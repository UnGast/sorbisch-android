package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Instrumentation;
import android.app.LocaleManager;
import android.content.Context;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

public class AppLanguageTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void legacyContextsResolveResourcesWithoutChangingSystemLanguage() {
        String system = context.getResources().getConfiguration().getLocales().toLanguageTags();
        assertEquals("App-Sprache", AppLanguage.localizedContext(context, "de").getString(R.string.app_language));
        assertEquals("App language", AppLanguage.localizedContext(context, "en").getString(R.string.app_language));
        Context sorbian = AppLanguage.localizedContext(context, "hsb");
        assertEquals("Rěč aplikacije", sorbian.getString(R.string.app_language));
        assertSame(context, AppLanguage.localizedContext(context, ""));
        assertEquals(system, context.getResources().getConfiguration().getLocales().toLanguageTags());
    }

    @Test
    public void selectionPersistsWithoutChangingSpeechSettings() {
        String previous = AppLanguage.selected(context);
        String recognition = SpeechSettings.language(context);
        String tts = SpeechSettings.ttsLanguage(context);
        String voice = SpeechSettings.ttsSpeakerId(context);
        try {
            for (String language : AppLanguage.TAGS) {
                setLanguage(language);
                assertEquals(language, AppLanguage.selected(context));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    assertEquals(language, context.getSystemService(LocaleManager.class)
                            .getApplicationLocales().toLanguageTags());
                }
                assertEquals(recognition, SpeechSettings.language(context));
                assertEquals(tts, SpeechSettings.ttsLanguage(context));
                assertEquals(voice, SpeechSettings.ttsSpeakerId(context));
            }
        } finally {
            setLanguage(previous);
        }
    }

    private void setLanguage(String language) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> AppLanguage.set(context, language));
        instrumentation.waitForIdleSync();
    }
}
