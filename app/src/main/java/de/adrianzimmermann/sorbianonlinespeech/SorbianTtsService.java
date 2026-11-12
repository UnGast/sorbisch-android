package de.adrianzimmermann.sorbianonlinespeech;

import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SorbianTtsService extends TextToSpeechService {
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();
    private volatile Call activeCall;
    private volatile boolean stopped;
    private volatile String[] currentLanguage = {"hsb", "DEU", ""};

    @Override
    protected int onIsLanguageAvailable(String language, String country, String variant) {
        if (!isSystemLanguageSupported(language)) {
            return TextToSpeech.LANG_NOT_SUPPORTED;
        }
        if (variant != null && !variant.isEmpty()) {
            return TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
        }
        if (country != null && !country.isEmpty()) {
            return TextToSpeech.LANG_COUNTRY_AVAILABLE;
        }
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    protected int onLoadLanguage(String language, String country, String variant) {
        int availability = onIsLanguageAvailable(language, country, variant);
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            currentLanguage = new String[]{language, country == null ? "" : country,
                    variant == null ? "" : variant};
        }
        return availability;
    }

    @Override
    protected String[] onGetLanguage() {
        return currentLanguage.clone();
    }

    // The server has no rate or pitch parameters, so those request settings are ignored.
    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        stopped = false;
        String endpoint = SpeechSettings.ttsUrl(this);
        String text = request.getText() == null ? "" : request.getText().toString().trim();
        if (endpoint.isEmpty() || text.isEmpty()) {
            finishError(callback, TextToSpeech.ERROR_INVALID_REQUEST);
            return;
        }
        Request networkRequest;
        try {
            // Select to Speak and similar services ask for the device locale (e.g. de-DE),
            // so the request language is ignored and the configured one is used.
            networkRequest = ExternalTtsClient.request(
                    this, endpoint, text, SpeechSettings.ttsLanguage(this));
        } catch (IllegalArgumentException e) {
            finishError(callback, TextToSpeech.ERROR_INVALID_REQUEST);
            return;
        }
        Call call = httpClient.newCall(networkRequest);
        activeCall = call;
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                finishError(callback, TextToSpeech.ERROR_NETWORK);
                return;
            }
            stream(PcmWav.parse(ExternalTtsClient.readAudio(response)), callback);
        } catch (IOException e) {
            if (stopped) {
                callback.done();
            } else {
                finishError(callback, TextToSpeech.ERROR_NETWORK);
            }
        } finally {
            if (activeCall == call) {
                activeCall = null;
            }
        }
    }

    @Override
    protected void onStop() {
        stopped = true;
        Call call = activeCall;
        if (call != null) {
            call.cancel();
        }
    }

    private void stream(PcmWav.Audio audio, SynthesisCallback callback) {
        if (callback.start(audio.sampleRate, audio.audioFormat, audio.channelCount) != TextToSpeech.SUCCESS) {
            finishError(callback, TextToSpeech.ERROR_OUTPUT);
            return;
        }
        int max = Math.max(1, callback.getMaxBufferSize());
        for (int offset = 0; offset < audio.pcm.length && !stopped; offset += max) {
            int length = Math.min(max, audio.pcm.length - offset);
            if (callback.audioAvailable(audio.pcm, offset, length) != TextToSpeech.SUCCESS) {
                callback.done();
                return;
            }
        }
        callback.done();
    }

    private static void finishError(SynthesisCallback callback, int errorCode) {
        if (!callback.hasFinished()) {
            callback.error(errorCode);
            callback.done();
        }
    }

    static boolean isSystemLanguageSupported(String language) {
        for (String available : SpeechSettings.availableTtsLanguages()) {
            if (available.equals(language)) return true;
        }
        return "deu".equals(language) || "de".equals(language)
                || "eng".equals(language) || "en".equals(language);
    }
}
