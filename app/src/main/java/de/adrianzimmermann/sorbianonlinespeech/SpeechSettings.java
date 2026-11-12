package de.adrianzimmermann.sorbianonlinespeech;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.speech.RecognizerIntent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class SpeechSettings {
    static final String TTS_SIMPLE = "simple";
    static final String TTS_BAMBORAK = "bamborak";

    private static final String PREFS = "online_speech";
    private static final String DRAFT_PREFS = "dictation";
    private static final String KEY_DRAFT = "text";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_TTS_LANGUAGE = "tts_language";
    private static final String KEY_SAMPLE_RATE = "recognizer_sample_rate";
    private static final String KEY_CONTINUOUS = "continuous_recognition";
    private static final String KEY_SETUP_COMPLETED = "setup_completed";
    // Stored once per language, see profileKey().
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_TTS_URL = "tts_url";
    private static final String KEY_TTS_PROTOCOL = "tts_protocol";
    private static final String KEY_TTS_SPEAKER_ID = "tts_speaker_id";
    private static final String[] PROFILE_KEYS = {
            KEY_SERVER_URL, KEY_TTS_URL, KEY_TTS_PROTOCOL, KEY_TTS_SPEAKER_ID};

    private static final String HSB = "hsb";
    private static final String DSB = "dsb";
    private static final String[] RECOGNITION_LANGUAGES = parseLanguages(
            BuildConfig.SUPPORTED_RECOGNITION_LANGUAGES);
    static final String DEFAULT_LANGUAGE = availableLanguages()[0];

    private SpeechSettings() {
    }

    static String language(Context context) {
        String saved = normalizeLanguage(prefs(context).getString(KEY_LANGUAGE, DEFAULT_LANGUAGE));
        return contains(availableLanguages(), saved) ? saved : DEFAULT_LANGUAGE;
    }

    static void saveLanguage(Context context, String language) {
        prefs(context).edit().putString(KEY_LANGUAGE, language).apply();
    }

    /**
     * Other apps usually ask for the phone's language. A language we do not offer
     * is replaced by the one selected in the app.
     */
    static String languageFromIntent(Context context, Intent intent) {
        String requested = intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
        if (requested != null && !requested.trim().isEmpty()) {
            String language = normalizeLanguage(requested);
            if (contains(availableLanguages(), language)) {
                return language;
            }
        }
        return language(context);
    }

    static String serverUrl(Context context) {
        return serverUrl(context, language(context));
    }

    static String serverUrl(Context context, String language) {
        return prefs(context).getString(profileKey(KEY_SERVER_URL, language), defaultServerUrl(language));
    }

    static int sampleRate(Context context) {
        int rate = prefs(context).getInt(KEY_SAMPLE_RATE, BuildConfig.DEFAULT_RECOGNIZER_SAMPLE_RATE);
        return rate == 48000 ? 48000 : 16000;
    }

    static boolean continuousRecognition(Context context) {
        return prefs(context).getBoolean(KEY_CONTINUOUS, BuildConfig.DEFAULT_CONTINUOUS_RECOGNITION);
    }

    static void saveRecognitionOptions(Context context, int sampleRate, boolean continuous) {
        if (sampleRate != 16000 && sampleRate != 48000) {
            throw new IllegalArgumentException("Unsupported recognition sample rate");
        }
        SharedPreferences.Editor editor = prefs(context).edit();
        if (sampleRate == BuildConfig.DEFAULT_RECOGNIZER_SAMPLE_RATE) {
            editor.remove(KEY_SAMPLE_RATE);
        } else {
            editor.putInt(KEY_SAMPLE_RATE, sampleRate);
        }
        if (continuous == BuildConfig.DEFAULT_CONTINUOUS_RECOGNITION) {
            editor.remove(KEY_CONTINUOUS);
        } else {
            editor.putBoolean(KEY_CONTINUOUS, continuous);
        }
        editor.apply();
    }

    static String ttsLanguage(Context context) {
        String saved = normalizeLanguage(prefs(context).getString(KEY_TTS_LANGUAGE, HSB));
        String[] available = availableTtsLanguages();
        return contains(available, saved) ? saved : available[0];
    }

    static void saveTtsLanguage(Context context, String language) {
        String normalized = normalizeLanguage(language);
        if (!contains(availableTtsLanguages(), normalized)) {
            throw new IllegalArgumentException("TTS language is not configured");
        }
        prefs(context).edit().putString(KEY_TTS_LANGUAGE, normalized).apply();
    }

    static String ttsUrl(Context context) {
        return ttsUrl(context, ttsLanguage(context));
    }

    static String ttsUrl(Context context, String language) {
        return prefs(context).getString(profileKey(KEY_TTS_URL, language), defaultTtsUrl(language));
    }

    static String ttsProtocol(Context context) {
        return ttsProtocol(context, ttsLanguage(context));
    }

    static String ttsProtocol(Context context, String language) {
        return prefs(context).getString(profileKey(KEY_TTS_PROTOCOL, language), defaultTtsProtocol(language));
    }

    static String ttsSpeakerId(Context context) {
        return ttsSpeakerId(context, ttsLanguage(context));
    }

    static String ttsSpeakerId(Context context, String language) {
        return prefs(context).getString(profileKey(KEY_TTS_SPEAKER_ID, language), defaultTtsSpeakerId(language));
    }

    static void saveTtsOptions(Context context, String protocol, String speakerId) {
        saveTtsOptions(context, ttsLanguage(context), protocol, speakerId);
    }

    static void saveTtsOptions(Context context, String language, String protocol, String speakerId) {
        SharedPreferences.Editor editor = prefs(context).edit();
        saveOverride(editor, profileKey(KEY_TTS_PROTOCOL, language), protocol, defaultTtsProtocol(language));
        saveOverride(editor, profileKey(KEY_TTS_SPEAKER_ID, language), speakerId.trim(),
                defaultTtsSpeakerId(language));
        editor.apply();
    }

    static void save(Context context, String serverUrl, String language, String ttsUrl) {
        SharedPreferences.Editor editor = prefs(context).edit();
        saveOverride(editor, profileKey(KEY_SERVER_URL, language), sanitizeServerUrl(serverUrl, language),
                defaultServerUrl(language));
        editor.putString(KEY_LANGUAGE, language);
        String outputLanguage = ttsLanguage(context);
        saveOverride(editor, profileKey(KEY_TTS_URL, outputLanguage), sanitizeOptionalUrl(ttsUrl),
                defaultTtsUrl(outputLanguage));
        editor.apply();
    }

    static void resetServerSettings(Context context) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (String key : PROFILE_KEYS) {
            editor.remove(profileKey(key, HSB)).remove(profileKey(key, DSB));
        }
        editor.remove(KEY_LANGUAGE).remove(KEY_TTS_LANGUAGE)
                .remove(KEY_SAMPLE_RATE).remove(KEY_CONTINUOUS).apply();
    }

    static boolean setupCompleted(Context context) {
        return prefs(context).getBoolean(KEY_SETUP_COMPLETED, false);
    }

    static void completeSetup(Context context) {
        prefs(context).edit().putBoolean(KEY_SETUP_COMPLETED, true).apply();
    }

    static String dictationDraft(Context context) {
        return context.getSharedPreferences(DRAFT_PREFS, Context.MODE_PRIVATE).getString(KEY_DRAFT, "");
    }

    static void saveDictationDraft(Context context, String text) {
        context.getSharedPreferences(DRAFT_PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_DRAFT, text).apply();
    }

    static String languageLabel(Context context, String language) {
        if (HSB.equals(language)) return context.getString(R.string.upper_sorbian);
        if (DSB.equals(language)) return context.getString(R.string.lower_sorbian);
        return language;
    }

    static String ttsSampleText(Context context, String language) {
        // TODO: Lower Sorbian sample sentence.
        return isDsb(language) ? "Diktuj." : context.getString(R.string.tts_sample_text);
    }

    /** An empty field means "use the packaged default". */
    static String sanitizeServerUrl(String raw, String language) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultServerUrl(language);
        }
        return raw.trim();
    }

    static String sanitizeOptionalUrl(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /** Strips the region, so "hsb-DE" becomes "hsb". */
    static String normalizeLanguage(String language) {
        String normalized = language == null ? DEFAULT_LANGUAGE : language.trim();
        if (normalized.contains("-")) {
            normalized = normalized.substring(0, normalized.indexOf('-'));
        }
        return normalized.isEmpty() ? DEFAULT_LANGUAGE : normalized.toLowerCase(Locale.ROOT);
    }

    /** DSB is only offered once a recognizer URL is configured for it. */
    static String[] availableLanguages() {
        Set<String> enabled = new LinkedHashSet<>();
        for (String language : RECOGNITION_LANGUAGES) {
            if (!isDsb(language) || !BuildConfig.DSB_RECOGNIZER_URL.isEmpty()) {
                enabled.add(language);
            }
        }
        if (enabled.isEmpty()) enabled.add(HSB);
        return enabled.toArray(new String[0]);
    }

    static String[] availableTtsLanguages() {
        Set<String> enabled = new LinkedHashSet<>();
        for (String language : parseLanguages(BuildConfig.SUPPORTED_TTS_LANGUAGES)) {
            if (HSB.equals(language) || (DSB.equals(language) && dsbTtsConfigured())) {
                enabled.add(language);
            }
        }
        if (enabled.isEmpty()) enabled.add(HSB);
        return enabled.toArray(new String[0]);
    }

    /** Parses a comma-separated list from server-config.properties. */
    static String[] parseLanguages(String configured) {
        Set<String> languages = new LinkedHashSet<>();
        if (configured != null) {
            for (String candidate : configured.split(",")) {
                String language = candidate.trim().toLowerCase(Locale.ROOT);
                int regionSeparator = language.indexOf('-');
                if (regionSeparator >= 0) {
                    language = language.substring(0, regionSeparator);
                }
                if (language.matches("[a-z]{2,8}")) {
                    languages.add(language);
                }
            }
        }
        if (languages.isEmpty()) {
            languages.add(HSB);
        }
        return languages.toArray(new String[0]);
    }

    private static boolean dsbTtsConfigured() {
        if (BuildConfig.DSB_TTS_URL.isEmpty()) return false;
        if (TTS_SIMPLE.equals(BuildConfig.DSB_TTS_PROTOCOL)) return true;
        return TTS_BAMBORAK.equals(BuildConfig.DSB_TTS_PROTOCOL) && !BuildConfig.DSB_TTS_SPEAKER_ID.isEmpty();
    }

    private static void saveOverride(SharedPreferences.Editor editor, String key, String value, String defaultValue) {
        if (value.equals(defaultValue)) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
    }

    // HSB keeps the unsuffixed keys from before DSB existed.
    private static String profileKey(String key, String language) {
        return isDsb(language) ? key + "_dsb" : key;
    }

    private static boolean isDsb(String language) {
        return DSB.equals(normalizeLanguage(language));
    }

    private static String defaultServerUrl(String language) {
        return isDsb(language) ? BuildConfig.DSB_RECOGNIZER_URL : BuildConfig.DEFAULT_RECOGNIZER_URL;
    }

    private static String defaultTtsUrl(String language) {
        return isDsb(language) ? BuildConfig.DSB_TTS_URL : BuildConfig.DEFAULT_TTS_URL;
    }

    private static String defaultTtsProtocol(String language) {
        return isDsb(language) ? BuildConfig.DSB_TTS_PROTOCOL : BuildConfig.DEFAULT_TTS_PROTOCOL;
    }

    private static String defaultTtsSpeakerId(String language) {
        return isDsb(language) ? BuildConfig.DSB_TTS_SPEAKER_ID : BuildConfig.DEFAULT_TTS_SPEAKER_ID;
    }

    private static boolean contains(String[] values, String value) {
        for (String candidate : values) {
            if (candidate.equals(value)) return true;
        }
        return false;
    }

    private static SharedPreferences prefs(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ConfigurationMigration.migrate(preferences, BuildConfig.CONFIG_VERSION);
        return preferences;
    }
}
