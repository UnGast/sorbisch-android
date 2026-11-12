package de.adrianzimmermann.sorbianonlinespeech;

import android.content.Context;
import android.content.ContextParams;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

public class OnlineRecognitionService extends RecognitionService {
    private Callback activeCallback;
    private OnlineSpeechSession session;
    private boolean speechEnded;

    @Override
    protected void onStartListening(Intent intent, Callback callback) {
        cancelSession();
        activeCallback = callback;
        speechEnded = false;
        Context recordingContext = this;
        // Android 12+: record on behalf of the calling app, so the microphone use is attributed to it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            recordingContext = createContext(new ContextParams.Builder()
                    .setNextAttributionSource(callback.getCallingAttributionSource())
                    .build());
        }
        String language = SpeechSettings.languageFromIntent(this, intent);
        // Never continuous: callers expect a result without having to call stopListening().
        session = new OnlineSpeechSession(recordingContext, SpeechSettings.serverUrl(this, language),
                language, false, new RecognitionListener() {
                    @Override
                    public void onReady() {
                        send(callback, () -> callback.readyForSpeech(new Bundle()));
                    }

                    @Override
                    public void onSpeechStart() {
                        send(callback, callback::beginningOfSpeech);
                    }

                    @Override
                    public void onResult(RecognitionResult result) {
                        if (result.partial) {
                            send(callback, () -> callback.partialResults(resultBundle(result.text)));
                        } else {
                            endSpeech(callback);
                            send(callback, () -> callback.results(resultBundle(result.text)));
                            clearSession(callback);
                        }
                    }

                    @Override
                    public void onNoSpeech() {
                        fail(callback, SpeechRecognizer.ERROR_NO_MATCH);
                    }

                    @Override
                    public void onError(String message) {
                        fail(callback, SpeechRecognizer.ERROR_NETWORK);
                    }

                    @Override
                    public void onComplete() {
                        // Closed without a final result.
                        fail(callback, SpeechRecognizer.ERROR_NO_MATCH);
                    }
                });
        session.start();
    }

    @Override
    protected void onCancel(Callback callback) {
        if (callback == activeCallback) {
            cancelSession();
        }
    }

    @Override
    protected void onStopListening(Callback callback) {
        if (callback == activeCallback && session != null) {
            session.stop();
            endSpeech(callback);
        }
    }

    @Override
    public void onDestroy() {
        cancelSession();
        super.onDestroy();
    }

    private void endSpeech(Callback callback) {
        if (callback == activeCallback && !speechEnded) {
            speechEnded = true;
            send(callback, callback::endOfSpeech);
        }
    }

    private void fail(Callback callback, int error) {
        send(callback, () -> callback.error(error));
        clearSession(callback);
    }

    private void clearSession(Callback callback) {
        if (callback == activeCallback) {
            activeCallback = null;
            session = null;
        }
    }

    private void cancelSession() {
        activeCallback = null;
        if (session != null) {
            session.cancel();
            session = null;
        }
    }

    // Ignores callbacks of earlier clients. A RemoteException means the client is gone.
    private void send(Callback callback, CallbackAction action) {
        if (callback != activeCallback) {
            return;
        }
        try {
            action.run();
        } catch (RemoteException e) {
            cancelSession();
        }
    }

    private interface CallbackAction {
        void run() throws RemoteException;
    }

    private static Bundle resultBundle(String text) {
        ArrayList<String> results = new ArrayList<>();
        results.add(text);
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, results);
        return bundle;
    }
}
