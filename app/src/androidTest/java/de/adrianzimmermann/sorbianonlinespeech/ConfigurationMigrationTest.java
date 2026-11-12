package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConfigurationMigrationTest {
    private SharedPreferences preferences;
    private Context context;

    @Before
    public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = target.getSharedPreferences("configuration_migration_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        context = new ContextWrapper(target) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return preferences;
            }
        };
    }

    @After
    public void tearDown() {
        preferences.edit().clear().commit();
    }

    @Test
    public void oldDefaultsAreRemovedEvenWhenVersionsAreSkipped() {
        preferences.edit()
                .putString("server_url", "wss://webcaptioner-spoz.mudrowak.de/")
                .putString("tts_url", "https://bamborakapi.mudrowak.de/api/tts/")
                .putString("tts_protocol", "bamborak")
                .putString("tts_speaker_id", "weronika")
                .putInt("recognizer_sample_rate", 48000)
                .putBoolean("continuous_recognition", true)
                .putBoolean("setup_completed", true).commit();

        ConfigurationMigration.migrate(preferences, 3);

        assertEquals(3, preferences.getInt("config_version", 0));
        assertEquals(2, preferences.getAll().size());
        assertTrue(preferences.getBoolean("setup_completed", false));
        assertEquals("wss://new.example/", preferences.getString("server_url", "wss://new.example/"));
        assertEquals(BuildConfig.DEFAULT_TTS_URL, SpeechSettings.ttsUrl(context));
    }

    @Test
    public void customValuesAndLanguageProfilesSurvive() {
        preferences.edit().putString("server_url", "wss://custom.example/")
                .putString("tts_url", "")
                .putString("tts_protocol", "simple")
                .putString("tts_speaker_id", "korla")
                .putInt("recognizer_sample_rate", 16000)
                .putBoolean("continuous_recognition", false)
                .putString("server_url_dsb", "wss://dsb.example/")
                .putString("tts_speaker_id_dsb", "custom-dsb").commit();
        assertEquals("wss://custom.example/", SpeechSettings.serverUrl(context, "hsb"));
        assertEquals("", SpeechSettings.ttsUrl(context, "hsb"));
        assertEquals("simple", SpeechSettings.ttsProtocol(context));
        assertEquals("korla", SpeechSettings.ttsSpeakerId(context));
        assertEquals(16000, SpeechSettings.sampleRate(context));
        assertFalse(SpeechSettings.continuousRecognition(context));
        assertEquals("wss://dsb.example/", SpeechSettings.serverUrl(context, "dsb"));
        assertEquals("custom-dsb", SpeechSettings.ttsSpeakerId(context, "dsb"));
    }

    @Test
    public void migrationDoesNotRepeatOrDowngradeTheVersion() {
        ConfigurationMigration.migrate(preferences, 2);
        preferences.edit().putString("server_url", "wss://webcaptioner-spoz.mudrowak.de/").commit();
        ConfigurationMigration.migrate(preferences, 1);
        ConfigurationMigration.migrate(preferences, 3);
        ConfigurationMigration.migrate(preferences, 2);
        assertTrue(preferences.contains("server_url"));
        assertEquals(3, preferences.getInt("config_version", 0));
    }

    @Test
    public void versionTwoReplacesOldEndpointButKeepsCustomServers() {
        for (String oldUrl : new String[]{"wss://webcaptioner-spoz.mudrowak.de/",
                "wss://webcaptioner-spoz.mudrowak.de"}) {
            preferences.edit().putInt("config_version", 1).putString("server_url", oldUrl).commit();
            ConfigurationMigration.migrate(preferences, 2);
            assertFalse(preferences.contains("server_url"));
        }
        preferences.edit().putInt("config_version", 1)
                .putString("server_url", "wss://custom.example/").commit();
        ConfigurationMigration.migrate(preferences, 2);
        assertEquals("wss://custom.example/", preferences.getString("server_url", ""));
    }

    @Test
    public void savingDefaultsClearsOverridesAndResetPreservesSetup() {
        assertEquals(BuildConfig.DEFAULT_RECOGNIZER_URL, SpeechSettings.serverUrl(context));
        assertEquals(BuildConfig.CONFIG_VERSION, preferences.getInt("config_version", 0));
        SpeechSettings.save(context, "wss://custom.example/", "hsb", "https://custom.example/tts");
        assertTrue(preferences.contains("server_url"));
        SpeechSettings.save(context, BuildConfig.DEFAULT_RECOGNIZER_URL, "hsb", BuildConfig.DEFAULT_TTS_URL);
        SpeechSettings.saveTtsOptions(context, BuildConfig.DEFAULT_TTS_PROTOCOL, BuildConfig.DEFAULT_TTS_SPEAKER_ID);
        SpeechSettings.saveRecognitionOptions(context, BuildConfig.DEFAULT_RECOGNIZER_SAMPLE_RATE,
                BuildConfig.DEFAULT_CONTINUOUS_RECOGNITION);
        for (String key : new String[]{"server_url", "tts_url", "tts_protocol", "tts_speaker_id",
                "recognizer_sample_rate", "continuous_recognition"}) {
            assertFalse(key, preferences.contains(key));
        }
        SpeechSettings.completeSetup(context);
        SpeechSettings.save(context, "wss://custom.example/", "hsb", "https://custom.example/tts");
        SpeechSettings.resetServerSettings(context);
        assertEquals(BuildConfig.DEFAULT_RECOGNIZER_URL, SpeechSettings.serverUrl(context));
        assertTrue(SpeechSettings.setupCompleted(context));
        assertEquals(BuildConfig.CONFIG_VERSION, preferences.getInt("config_version", 0));
    }
}
