package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class SorbianTtsServiceTest {
    @Test
    public void acceptsSorbianAndTheLocalesAccessibilityServicesAskFor() {
        boolean dsbConfigured = Arrays.asList(SpeechSettings.availableTtsLanguages()).contains("dsb");

        assertTrue(SorbianTtsService.isSystemLanguageSupported("hsb"));
        assertEquals(dsbConfigured, SorbianTtsService.isSystemLanguageSupported("dsb"));
        assertTrue(SorbianTtsService.isSystemLanguageSupported("deu"));
        assertTrue(SorbianTtsService.isSystemLanguageSupported("eng"));
        assertFalse(SorbianTtsService.isSystemLanguageSupported("fra"));
    }
}
