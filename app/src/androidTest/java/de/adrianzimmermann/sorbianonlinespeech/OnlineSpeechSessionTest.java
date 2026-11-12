package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Instrumentation;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLHandshakeException;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.ByteString;

public class OnlineSpeechSessionTest {
    @Rule
    public final SavedSettings savedSettings = new SavedSettings();

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test
    public void connectionErrorsMapToMessages() {
        assertEquals(R.string.recognizer_secure_connection_failed,
                OnlineSpeechSession.connectionError(new SSLHandshakeException("secret"), 0));
        assertEquals(R.string.recognizer_access_denied,
                OnlineSpeechSession.connectionError(new IOException("secret"), 403));
        assertEquals(R.string.recognizer_server_unavailable,
                OnlineSpeechSession.connectionError(new IOException(), 503));
        assertEquals(R.string.recognizer_server_unavailable,
                OnlineSpeechSession.connectionError(new IOException(), 429));
        assertEquals(R.string.recognizer_connection_failed,
                OnlineSpeechSession.connectionError(new UnknownHostException("private-host"), 0));
    }

    @Test
    public void repeatedRecordingsCombinePhrasesAndFinishOnEmptyEof() throws Exception {
        instrumentation.getUiAutomation().grantRuntimePermission(
                instrumentation.getTargetContext().getPackageName(), Manifest.permission.RECORD_AUDIO);
        MainActivity activity = TestUtil.launch(MainActivity.class);
        try {
            SpeechSettings.saveRecognitionOptions(activity, 48000, true);
            RecognitionDiagnostics.clear(activity);
            RecognitionDiagnostics.arm();
            // Only the first session may use up the diagnostics consent.
            runSession(activity, true);
            runSession(activity, false);
            assertEquals(1, RecognitionDiagnostics.entries(activity).trim().split("\n").length);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private void runSession(MainActivity activity, boolean audioLog) throws Exception {
        CountDownLatch preview = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<String> finalText = new AtomicReference<>();
        AtomicInteger sampleRate = new AtomicInteger();
        AtomicInteger terminalCount = new AtomicInteger();
        AtomicInteger audioFrames = new AtomicInteger();
        AtomicInteger configMessages = new AtomicInteger();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket socket, Response response) {
                    socket.send("{\"partial\":\"\",\"listen\":\"false\"}");
                    socket.send("{\"text\":\"whisper_large_turbo.ggml\","
                            + "\"recognizer\":\"whisper.cpp X.Y.Z\",\"model\":\"whisper_large_turbo.ggml\"}");
                }

                @Override
                public void onMessage(WebSocket socket, String text) {
                    try {
                        JSONObject message = new JSONObject(text);
                        if (message.has("config")) {
                            JSONObject config = message.getJSONObject("config");
                            int order = configMessages.incrementAndGet();
                            // Like the real server, look at one setting per message.
                            if (config.has("sample_rate")) {
                                sampleRate.set(config.getInt("sample_rate"));
                                if (order != 1 || config.has("audio_log")) {
                                    error.set("Audio logging must be a separate command");
                                }
                            } else if (order != 2 || config.length() != 1
                                    || audioLog != config.getBoolean("audio_log")) {
                                error.set("Missing or unexpected session audio_log setting");
                            }
                        } else if (message.has("eof")) {
                            socket.send("{\"text\":\"\"}");
                        }
                    } catch (Exception e) {
                        error.set(e.toString());
                    }
                }

                @Override
                public void onMessage(WebSocket socket, ByteString bytes) {
                    if (configMessages.get() != 2) {
                        error.set("Audio arrived before both config commands");
                    }
                    if (audioFrames.incrementAndGet() == 1) {
                        socket.send("{\"text\":\"dobry dzen\"}");
                        socket.send("{\"partial\":\"to je\"}");
                        socket.send("{\"text\":\"to je test\"}");
                    }
                }

                @Override
                public void onClosing(WebSocket socket, int code, String reason) {
                    socket.close(code, reason);
                }
            }));
            server.start();
            String url = server.url("/").toString().replace("http://", "ws://");
            OnlineSpeechSession session = new OnlineSpeechSession(activity, url, "hsb", new RecognitionListener() {
                @Override
                public void onReady() {
                }

                @Override
                public void onSpeechStart() {
                }

                @Override
                public void onResult(RecognitionResult result) {
                    if (result.partial && "dobry dzen to je test".equals(result.text)) {
                        preview.countDown();
                    } else if (!result.partial) {
                        finalText.set(result.text);
                    }
                }

                @Override
                public void onNoSpeech() {
                    error.set("No speech");
                    done.countDown();
                }

                @Override
                public void onError(String message) {
                    error.set(message);
                    done.countDown();
                }

                @Override
                public void onComplete() {
                    terminalCount.incrementAndGet();
                    done.countDown();
                }
            });
            try {
                instrumentation.runOnMainSync(session::start);
                assertTrue("Missing combined preview: " + error.get(), preview.await(10, TimeUnit.SECONDS));
                assertEquals(48000, sampleRate.get());
                assertFalse("Session ended before Stop", done.await(250, TimeUnit.MILLISECONDS));
                assertTrue(audioFrames.get() > 1);
                instrumentation.runOnMainSync(session::stop);
                assertTrue("EOF did not finish", done.await(10, TimeUnit.SECONDS));
                assertNull(error.get());
                assertEquals("dobry dzen to je test", finalText.get());
                assertEquals(1, terminalCount.get());
            } finally {
                instrumentation.runOnMainSync(session::cancel);
            }
        }
    }
}
