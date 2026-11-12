package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;

public class RecognitionDiagnosticsTest {
    @Rule
    public final SavedSettings savedSettings = new SavedSettings();

    @Test
    public void recordingRequiresSingleUseConsentAndCanBeCancelled() {
        RecognitionDiagnostics.disarm();
        assertFalse(RecognitionDiagnostics.consumeRequest());

        RecognitionDiagnostics.arm();
        assertTrue(RecognitionDiagnostics.consumeRequest());
        assertFalse(RecognitionDiagnostics.consumeRequest());

        RecognitionDiagnostics.arm();
        RecognitionDiagnostics.disarm();
        assertFalse(RecognitionDiagnostics.consumeRequest());
    }

    @Test
    public void listIsBoundedAndDoesNotIncludeUrlSecrets() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String url = "wss://user:secret@example.org/private?token=secret";
        RecognitionDiagnostics.clear(context);
        for (int i = 0; i < 55; i++) {
            RecognitionDiagnostics.record(context, url, "hsb", 48000);
        }
        String entries = RecognitionDiagnostics.entries(context);
        assertEquals(50, entries.trim().split("\n").length);
        assertTrue(entries.contains("Z | example.org | hsb | 48000 Hz"));
        assertFalse(entries.contains("secret"));
        assertFalse(entries.contains("private"));

        RecognitionDiagnostics.clear(context);
        assertTrue(RecognitionDiagnostics.entries(context).isEmpty());
    }
}
