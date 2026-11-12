package de.adrianzimmermann.sorbianonlinespeech;

import android.content.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONException;
import org.json.JSONObject;

final class ExternalTtsClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private ExternalTtsClient() {
    }

    /** Builds the request from the saved settings for this language. */
    static Request request(Context context, String endpointUrl, String text, String language) {
        if (SpeechSettings.TTS_BAMBORAK.equals(SpeechSettings.ttsProtocol(context, language))
                && SpeechSettings.ttsSpeakerId(context, language).trim().isEmpty()) {
            throw new IllegalArgumentException(context.getString(R.string.tts_speaker_required));
        }
        return request(endpointUrl, text, language, SpeechSettings.ttsProtocol(context, language),
                SpeechSettings.ttsSpeakerId(context, language));
    }

    // Bamborak picks the language through the speaker, so it gets no language field.
    static Request request(String endpointUrl, String text, String language,
                           String protocol, String speakerId) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("text", text.trim());
            if (SpeechSettings.TTS_BAMBORAK.equals(protocol)) {
                if (speakerId == null || speakerId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Bamborak requires a speaker ID");
                }
                payload.put("speaker_id", speakerId.trim());
                payload.put("format", "wav");
            } else if (SpeechSettings.TTS_SIMPLE.equals(protocol)) {
                payload.put("language", SpeechSettings.normalizeLanguage(language));
            } else {
                throw new IllegalArgumentException("Unknown TTS protocol: " + protocol);
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException("Could not encode TTS request", e);
        }
        return new Request.Builder()
                .url(endpointUrl.trim())
                .post(RequestBody.create(payload.toString(), JSON))
                .build();
    }

    /** Returns the response body, or throws if the server reported an error. */
    static byte[] readAudio(Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            throw new IOException("Empty TTS response");
        }
        byte[] audio = body.bytes();
        String contentType = response.header("Content-Type", "");
        int first = 0;
        while (first < audio.length && audio[first] <= 32 && audio[first] >= 0) {
            first++;
        }
        // Bamborak can return a JSON error with HTTP 200.
        if (contentType.contains("json") || (first < audio.length && audio[first] == '{')) {
            String message = "Invalid TTS response";
            try {
                JSONObject error = new JSONObject(new String(audio, StandardCharsets.UTF_8));
                message = error.optString("errmsg", message);
            } catch (JSONException ignored) {
            }
            throw new IOException(message);
        }
        if (!response.isSuccessful()) {
            throw new IOException("TTS HTTP " + response.code());
        }
        if (audio.length == 0) {
            throw new IOException("Empty TTS response");
        }
        return audio;
    }
}
