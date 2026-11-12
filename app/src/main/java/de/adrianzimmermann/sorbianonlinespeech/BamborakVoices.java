package de.adrianzimmermann.sorbianonlinespeech;

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class BamborakVoices {
    final String id;
    final String name;
    final String language;

    private BamborakVoices(String id, String name, String language) {
        this.id = id;
        this.name = name;
        this.language = language;
    }

    static HttpUrl endpoint(String synthesisUrl) {
        HttpUrl url = HttpUrl.get(synthesisUrl);
        String path = url.encodedPath().replaceAll("/+$", "");
        if (!path.endsWith("/tts")) {
            throw new IllegalArgumentException("Expected a Bamborak /tts/ endpoint");
        }
        return url.newBuilder().encodedPath(path.substring(0, path.length() - 3)
                + "fetch_speakers/").query(null).fragment(null).build();
    }

    static List<BamborakVoices> parse(String json, String requestedLanguage) throws JSONException {
        JSONArray entries = new JSONArray(json);
        List<BamborakVoices> voices = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String id = entry.optString("id").trim();
            String rawLanguage = entry.optString("language").trim();
            if (rawLanguage.isEmpty()) continue;
            String language = SpeechSettings.normalizeLanguage(rawLanguage);
            if (id.isEmpty() || !requestedLanguage.equals(language)) {
                continue;
            }
            String name = entry.optString("name", id).trim();
            voices.add(new BamborakVoices(id, name.isEmpty() ? id : name, language));
        }
        return voices;
    }
}
