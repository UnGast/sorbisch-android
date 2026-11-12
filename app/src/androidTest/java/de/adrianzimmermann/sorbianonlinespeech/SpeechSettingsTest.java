package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;

import okhttp3.Request;
import okio.Buffer;

public class SpeechSettingsTest {
    @Rule
    public final SavedSettings savedSettings = new SavedSettings();

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void normalizesLanguages() {
        assertEquals("hsb", SpeechSettings.normalizeLanguage("hsb-DE"));
        assertEquals("dsb", SpeechSettings.normalizeLanguage(" dsb "));
        assertEquals(SpeechSettings.DEFAULT_LANGUAGE, SpeechSettings.normalizeLanguage(""));
    }

    @Test
    public void parsesConfiguredLanguages() {
        assertEquals(2, SpeechSettings.parseLanguages("hsb, dsb,hsb").length);
        assertEquals("hsb", SpeechSettings.parseLanguages("hsb, dsb")[0]);
        assertEquals("dsb", SpeechSettings.parseLanguages("hsb, dsb")[1]);
        assertEquals("hsb", SpeechSettings.parseLanguages(" , ")[0]);
    }

    @Test
    public void keepsServerOverrides() {
        SpeechSettings.save(context, "ws://10.0.2.2:2700", "hsb", "http://10.0.2.2:2780/tts");

        assertEquals("ws://10.0.2.2:2700", SpeechSettings.serverUrl(context));
        assertEquals("http://10.0.2.2:2780/tts", SpeechSettings.ttsUrl(context));
    }

    @Test
    public void languagesKeepSeparateServersAndVoices() throws Exception {
        String ttsUrl = "https://tts.example.org/api/tts/";
        SpeechSettings.saveTtsLanguage(context, "hsb");
        SpeechSettings.save(context, "wss://hsb.example.org/", "hsb", ttsUrl);
        SpeechSettings.saveTtsOptions(context, "hsb", "bamborak", "hsb-voice");
        SpeechSettings.save(context, "wss://dsb.example.org/", "dsb", ttsUrl);
        SpeechSettings.saveTtsOptions(context, "dsb", "bamborak", "dsb-voice");

        assertEquals("wss://hsb.example.org/", SpeechSettings.serverUrl(context, "hsb"));
        assertEquals("wss://dsb.example.org/", SpeechSettings.serverUrl(context, "dsb-DE"));
        assertEquals("hsb", SpeechSettings.ttsLanguage(context));
        assertEquals("hsb-voice", SpeechSettings.ttsSpeakerId(context, "hsb"));

        Request request = ExternalTtsClient.request(context, ttsUrl, "Diktuj.", "dsb");
        Buffer body = new Buffer();
        request.body().writeTo(body);
        assertEquals("dsb-voice", new JSONObject(body.readUtf8()).getString("speaker_id"));
    }

    @Test
    public void requestedLanguageIsOnlyUsedIfOffered() {
        String selected = SpeechSettings.language(context);

        assertEquals("hsb", SpeechSettings.languageFromIntent(context, recognizeIntent("hsb-DE")));
        assertEquals(selected, SpeechSettings.languageFromIntent(context, recognizeIntent("de-DE")));
        assertEquals(selected, SpeechSettings.languageFromIntent(context, recognizeIntent("hsb&x=1")));
        assertEquals(selected, SpeechSettings.languageFromIntent(context, recognizeIntent(" ")));
        assertEquals(selected, SpeechSettings.languageFromIntent(context,
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)));
    }

    private static Intent recognizeIntent(String language) {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
    }

    @Test
    public void unconfiguredDsbNeverFallsBackToHsb() {
        assertEquals(BuildConfig.DSB_RECOGNIZER_URL, SpeechSettings.sanitizeServerUrl("", "dsb"));
        if (BuildConfig.DSB_RECOGNIZER_URL.isEmpty()) {
            assertFalse(Arrays.asList(SpeechSettings.availableLanguages()).contains("dsb"));
        }
        if (BuildConfig.DSB_TTS_URL.isEmpty() || ("bamborak".equals(BuildConfig.DSB_TTS_PROTOCOL)
                && BuildConfig.DSB_TTS_SPEAKER_ID.isEmpty())) {
            assertFalse(Arrays.asList(SpeechSettings.availableTtsLanguages()).contains("dsb"));
        }
    }
}
