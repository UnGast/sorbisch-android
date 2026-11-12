package de.adrianzimmermann.sorbianonlinespeech;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

final class OnlineSpeechSession {
    private static final int AUDIO_FRAME_DURATION_MS = 40;
    private static final long MAX_WEBSOCKET_QUEUE_BYTES = 256 * 1024;
    private static final long FINAL_RESULT_TIMEOUT_MS = 120_000;
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    private final Context context;
    private final String serverUrl;
    private final String language;
    private final int sampleRate;
    private final RecognitionTranscript transcript;
    private final RecognitionListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicBoolean eofSent = new AtomicBoolean();
    private final AtomicBoolean terminalCallbackSent = new AtomicBoolean();
    private volatile boolean connected;
    private volatile WebSocket webSocket;
    private volatile AudioRecord recorder;
    private boolean audioLog;
    private final Runnable finalResultTimeout = () -> {
        if (running.get() && stopping.get()) {
            finishWithError(text(R.string.recognizer_no_final_result));
        }
    };

    OnlineSpeechSession(Context context, String serverUrl, String language, RecognitionListener listener) {
        this(context, serverUrl, language, SpeechSettings.continuousRecognition(context), listener);
    }

    OnlineSpeechSession(Context context, String serverUrl, String language, boolean continuous,
                        RecognitionListener listener) {
        // RecognitionService passes a context that carries the calling app's microphone attribution.
        this.context = context;
        this.language = SpeechSettings.normalizeLanguage(language);
        this.serverUrl = SpeechSettings.sanitizeServerUrl(serverUrl, this.language);
        this.sampleRate = SpeechSettings.sampleRate(context);
        this.transcript = new RecognitionTranscript(continuous);
        this.listener = listener;
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            finishWithError(text(R.string.recognizer_microphone_missing));
            return;
        }
        Request request;
        try {
            request = new Request.Builder().url(urlWithLanguage()).build();
        } catch (IllegalArgumentException e) {
            finishWithError(text(R.string.recognizer_url_invalid));
            return;
        }
        webSocket = HTTP_CLIENT.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket socket, Response response) {
                if (!running.get()) {
                    socket.close(1000, "stopped");
                    return;
                }
                if (stopping.get()) {
                    running.set(false);
                    socket.close(1000, "stopped before recording");
                    finishComplete();
                    return;
                }
                connected = true;
                audioLog = RecognitionDiagnostics.consumeRequest();
                // Two messages because the C++ server stops reading a config message
                // once it has seen sample_rate.
                if (!socket.send(VoskJson.config(language, sampleRate))
                        || !socket.send(VoskJson.audioLogConfig(audioLog))) {
                    finishWithError(text(R.string.recognizer_config_failed));
                    return;
                }
                post(listener::onReady);
                startAudioLoop(socket);
            }

            @Override
            public void onMessage(WebSocket socket, String text) {
                if (!running.get()) {
                    return;
                }
                if (VoskJson.isNoSpeech(text) && !eofSent.get()) {
                    finishNoSpeech(socket);
                    return;
                }
                RecognitionResult result = transcript.accept(text, eofSent.get());
                if (result == null) {
                    return;
                }
                if (!result.partial && result.text.isEmpty()) {
                    finishNoSpeech(socket);
                    return;
                }
                if (result.partial && !result.text.isEmpty()) {
                    post(() -> listener.onResult(result));
                }
                if (!result.partial && !result.text.isEmpty()) {
                    release();
                    post(() -> listener.onResult(result));
                    socket.close(1000, "final result received");
                    finishComplete();
                }
            }

            @Override
            public void onClosing(WebSocket socket, int code, String reason) {
                // Without this the socket stays half open and onClosed() never arrives.
                socket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket socket, int code, String reason) {
                finishComplete();
            }

            @Override
            public void onFailure(WebSocket socket, Throwable t, Response response) {
                connected = false;
                if (running.getAndSet(false)) {
                    finishWithError(text(connectionError(t, response == null ? 0 : response.code())));
                }
            }
        });
    }

    /** Stops recording and waits for the server's final result. */
    void stop() {
        if (!running.get() || !stopping.compareAndSet(false, true)) {
            return;
        }
        AudioRecord localRecorder = recorder;
        if (localRecorder != null) {
            try {
                localRecorder.stop();
            } catch (IllegalStateException ignored) {
            }
            return;
        }
        if (connected) {
            sendEofAndAwaitFinal();
            return;
        }
        running.set(false);
        WebSocket socket = webSocket;
        if (socket != null) {
            socket.cancel();
        }
        finishComplete();
    }

    /**
     * Aborts without a final result or terminal callback. Listener calls that are
     * already queued on the main thread still run, so owners have to ignore them.
     */
    void cancel() {
        terminalCallbackSent.set(true);
        release();
        WebSocket socket = webSocket;
        if (socket != null) {
            socket.cancel();
        }
    }

    // Only the audio thread releases the recorder. Other threads just stop it,
    // which unblocks the pending read().
    private void startAudioLoop(WebSocket socket) {
        try {
            audioExecutor.execute(() -> {
                if (!running.get() || stopping.get()) {
                    if (running.get() && stopping.get()) {
                        sendEofAndAwaitFinal();
                    }
                    return;
                }
                int minBuffer = AudioRecord.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                if (minBuffer <= 0) {
                    finishWithError(text(R.string.recognizer_audio_unsupported));
                    return;
                }
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    finishWithError(text(R.string.recognizer_microphone_revoked));
                    return;
                }
                int bufferSize = Math.max(minBuffer, sampleRate);
                AudioRecord localRecorder;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        localRecorder = new AudioRecord.Builder()
                                .setContext(context)
                                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                                .setAudioFormat(new AudioFormat.Builder()
                                        .setSampleRate(sampleRate)
                                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .build())
                                .setBufferSizeInBytes(bufferSize)
                                .build();
                    } else {
                        localRecorder = new AudioRecord(
                                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                                sampleRate,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize);
                    }
                } catch (SecurityException e) {
                    finishWithError(text(R.string.recognizer_microphone_revoked));
                    return;
                } catch (RuntimeException e) {
                    finishWithError(text(R.string.recognizer_recorder_failed));
                    return;
                }
                recorder = localRecorder;
                int frameBytes = sampleRate * 2 * AUDIO_FRAME_DURATION_MS / 1000;
                byte[] buffer = new byte[Math.min(bufferSize, frameBytes)];
                try {
                    if (!running.get() || stopping.get()) {
                        return;
                    }
                    if (localRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        finishWithError(text(R.string.recognizer_recorder_failed));
                        return;
                    }
                    localRecorder.startRecording();
                    if (localRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                        finishWithError(text(R.string.recognizer_capture_failed));
                        return;
                    }
                    post(listener::onSpeechStart);
                    boolean logged = false;
                    while (running.get() && connected && !stopping.get()) {
                        int read = localRecorder.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            if (socket.queueSize() > MAX_WEBSOCKET_QUEUE_BYTES) {
                                finishWithError(text(R.string.recognizer_connection_slow));
                                socket.cancel();
                                break;
                            }
                            if (!socket.send(ByteString.of(buffer, 0, read))) {
                                finishWithError(text(R.string.recognizer_audio_closed));
                                break;
                            }
                            if (audioLog && !logged) {
                                RecognitionDiagnostics.record(context, serverUrl, language, sampleRate);
                                logged = true;
                            }
                        } else if (read < 0 && running.get() && !stopping.get()) {
                            finishWithError(text(R.string.recognizer_capture_failed));
                            break;
                        }
                    }
                } catch (RuntimeException e) {
                    if (running.get()) {
                        finishWithError(text(R.string.recognizer_capture_failed));
                    }
                } finally {
                    try {
                        localRecorder.stop();
                    } catch (IllegalStateException ignored) {
                    }
                    localRecorder.release();
                    if (recorder == localRecorder) {
                        recorder = null;
                    }
                    if (running.get() && stopping.get()) {
                        sendEofAndAwaitFinal();
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Stopped while the socket was still opening.
        }
    }

    private String urlWithLanguage() {
        if (serverUrl.contains("?")) {
            return serverUrl + "&lang=" + language;
        }
        return serverUrl + "?lang=" + language;
    }

    private void post(Runnable runnable) {
        mainHandler.post(runnable);
    }

    private void release() {
        running.set(false);
        connected = false;
        stopRecorder();
        mainHandler.removeCallbacks(finalResultTimeout);
        audioExecutor.shutdownNow();
    }

    private void finishWithError(String message) {
        release();
        WebSocket socket = webSocket;
        webSocket = null;
        if (socket != null) {
            socket.cancel();
        }
        if (terminalCallbackSent.compareAndSet(false, true)) {
            post(() -> listener.onError(message));
        }
    }

    private void finishComplete() {
        release();
        if (terminalCallbackSent.compareAndSet(false, true)) {
            post(listener::onComplete);
        }
    }

    private void finishNoSpeech(WebSocket socket) {
        release();
        socket.close(1000, "no speech detected");
        if (terminalCallbackSent.compareAndSet(false, true)) {
            post(listener::onNoSpeech);
        }
    }

    private void sendEofAndAwaitFinal() {
        if (!eofSent.compareAndSet(false, true)) {
            return;
        }
        WebSocket socket = webSocket;
        mainHandler.postDelayed(finalResultTimeout, FINAL_RESULT_TIMEOUT_MS);
        if (socket == null || !socket.send("{\"eof\": 1}")) {
            finishWithError(text(R.string.recognizer_finish_failed));
        }
    }

    static int connectionError(Throwable error, int status) {
        if (error instanceof SSLException) return R.string.recognizer_secure_connection_failed;
        if (status == 401 || status == 403) return R.string.recognizer_access_denied;
        if (status == 429 || status >= 500) return R.string.recognizer_server_unavailable;
        return R.string.recognizer_connection_failed;
    }

    private void stopRecorder() {
        AudioRecord localRecorder = recorder;
        if (localRecorder == null) {
            return;
        }
        try {
            localRecorder.stop();
        } catch (IllegalStateException ignored) {
        }
    }

    private String text(int resourceId) {
        return context.getString(resourceId);
    }
}
