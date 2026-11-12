package de.adrianzimmermann.sorbianonlinespeech;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class VoskJson {
    private VoskJson() {
    }

    static String config(String language, int sampleRate) {
        JSONObject config = new JSONObject();
        JSONObject wrapper = new JSONObject();
        try {
            config.put("sample_rate", sampleRate);
            config.put("language", language);
            wrapper.put("config", config);
        } catch (JSONException ignored) {
        }
        return wrapper.toString();
    }

    static String audioLogConfig(boolean enabled) {
        return "{\"config\":{\"audio_log\":" + enabled + "}}";
    }

    /** The server answers EOF with an empty final result. */
    static boolean isEmptyFinal(String message) {
        try {
            JSONObject json = new JSONObject(message);
            return !isMetadata(json) && json.has("text") && json.get("text") instanceof String
                    && json.getString("text").trim().isEmpty();
        } catch (JSONException ignored) {
            return false;
        }
    }

    static RecognitionResult parse(String message) {
        try {
            JSONObject json = new JSONObject(message);
            if (isMetadata(json)) {
                return new RecognitionResult("", false);
            }
            if (json.has("partial")) {
                return new RecognitionResult(json.optString("partial"), true);
            }
            if (json.has("text")) {
                return new RecognitionResult(json.optString("text"), false);
            }
            if (json.has("alternatives")) {
                JSONArray alternatives = json.optJSONArray("alternatives");
                if (alternatives != null && alternatives.length() > 0) {
                    return new RecognitionResult(alternatives.getJSONObject(0).optString("text"), false);
                }
            }
        } catch (JSONException ignored) {
        }
        return new RecognitionResult("", false);
    }

    private static boolean isMetadata(JSONObject json) {
        // The Mudrowak server greets with a message that has a "text" field too.
        return json.has("recognizer") && json.has("model");
    }

    // "no_speech" is an extension of the docker_vosk server, plain Vosk does not send it.
    static boolean isNoSpeech(String message) {
        try {
            JSONObject json = new JSONObject(message);
            return !isMetadata(json) && json.optBoolean("no_speech", false);
        } catch (JSONException ignored) {
            return false;
        }
    }
}
