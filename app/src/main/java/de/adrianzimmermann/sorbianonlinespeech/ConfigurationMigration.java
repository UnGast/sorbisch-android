package de.adrianzimmermann.sorbianonlinespeech;

import android.content.SharedPreferences;

import java.util.Map;

final class ConfigurationMigration {
    private static final String VERSION = "config_version";

    private ConfigurationMigration() {
    }

    static synchronized void migrate(SharedPreferences preferences, int targetVersion) {
        int previousVersion = preferences.getInt(VERSION, 0);
        if (previousVersion >= targetVersion) return;

        SharedPreferences.Editor editor = preferences.edit();
        if (previousVersion < 1) {
            // Defaults of the first release, which had no config version. Do not update these.
            Map<String, ?> saved = preferences.getAll();
            removeDefault(editor, saved, "server_url", "wss://webcaptioner-spoz.mudrowak.de/");
            removeDefault(editor, saved, "tts_url", "https://bamborakapi.mudrowak.de/api/tts/");
            removeDefault(editor, saved, "tts_protocol", "bamborak");
            removeDefault(editor, saved, "tts_speaker_id", "weronika");
            removeDefault(editor, saved, "recognizer_sample_rate", 48000);
            removeDefault(editor, saved, "continuous_recognition", true);
        }
        if (previousVersion < 2 && targetVersion >= 2) {
            Map<String, ?> saved = preferences.getAll();
            removeDefault(editor, saved, "server_url", "wss://webcaptioner-spoz.mudrowak.de/");
            removeDefault(editor, saved, "server_url", "wss://webcaptioner-spoz.mudrowak.de");
        }
        editor.putInt(VERSION, targetVersion).apply();
    }

    private static void removeDefault(SharedPreferences.Editor editor, Map<String, ?> saved,
                                      String key, Object oldDefault) {
        if (oldDefault.equals(saved.get(key))) editor.remove(key);
    }
}
