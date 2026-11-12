package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class RecognitionTranscriptTest {
    private static final String GREETING =
            "{\"text\":\"Whisper ready\",\"recognizer\":\"whisper.cpp\",\"model\":\"large\"}";

    @Test
    public void buildsExpectedVoskConfiguration() throws Exception {
        JSONObject config = new JSONObject(VoskJson.config("hsb", 16000)).getJSONObject("config");

        assertEquals(16000, config.getInt("sample_rate"));
        assertEquals("hsb", config.getString("language"));
        assertEquals(48000, new JSONObject(VoskJson.config("hsb", 48000))
                .getJSONObject("config").getInt("sample_rate"));
        assertFalse(new JSONObject(VoskJson.audioLogConfig(false))
                .getJSONObject("config").getBoolean("audio_log"));
    }

    @Test
    public void parsesPartialFinalAndAlternativeResults() {
        RecognitionResult partial = VoskJson.parse("{\"partial\":\"dobry\"}");
        RecognitionResult result = VoskJson.parse("{\"text\":\"dobry dzen\"}");
        RecognitionResult alternative = VoskJson.parse("{\"alternatives\":[{\"text\":\"witaj\"}]}");

        assertTrue(partial.partial);
        assertEquals("dobry", partial.text);
        assertFalse(result.partial);
        assertEquals("dobry dzen", result.text);
        assertEquals("witaj", alternative.text);
        assertEquals("", VoskJson.parse("not-json").text);
        assertTrue(VoskJson.isNoSpeech("{\"text\":\"\",\"no_speech\":true}"));
        assertFalse(VoskJson.isNoSpeech("{\"text\":\"witaj\"}"));
        assertFalse(VoskJson.isEmptyFinal("{\"text\":null}"));
        assertFalse(VoskJson.isEmptyFinal("not json"));
    }

    @Test
    public void continuousTranscriptCombinesServerPhrasesUntilEof() {
        RecognitionTranscript transcript = new RecognitionTranscript(true);
        assertNull(transcript.accept(GREETING, false));
        assertNull(transcript.accept("{\"text\":\"\"}", false));
        RecognitionResult first = transcript.accept("{\"text\":\"dobry dzen\"}", false);
        assertTrue(first.partial);
        assertEquals("dobry dzen", first.text);
        assertEquals("dobry dzen to je", transcript.accept("{\"partial\":\"to je\"}", false).text);
        assertEquals("dobry dzen to je test", transcript.accept("{\"text\":\"to je test\"}", false).text);
        assertNull(transcript.accept(GREETING, true));
        RecognitionResult end = transcript.accept("{\"text\":\"\"}", true);
        assertFalse(end.partial);
        assertEquals("dobry dzen to je test", end.text);
    }

    @Test
    public void finalAfterEofIncludesPreviouslyCompletedPhrases() {
        RecognitionTranscript transcript = new RecognitionTranscript(true);
        transcript.accept("{\"text\":\"one\"}", false);
        RecognitionResult end = transcript.accept("{\"text\":\"two\"}", true);
        assertFalse(end.partial);
        assertEquals("one two", end.text);
    }

    @Test
    public void emptyEofDoesNotCommitUnconfirmedPartial() {
        RecognitionTranscript transcript = new RecognitionTranscript(true);
        transcript.accept("{\"partial\":\"unconfirmed\"}", false);
        RecognitionResult end = transcript.accept("{\"text\":\"\"}", true);
        assertFalse(end.partial);
        assertEquals("", end.text);
    }

    @Test
    public void oneShotFinishesOnFirstFinal() {
        RecognitionResult result = new RecognitionTranscript(false)
                .accept("{\"text\":\"finished\"}", false);
        assertFalse(result.partial);
        assertEquals("finished", result.text);
    }

    @Test
    public void greetingNeedsBothRecognizerAndModel() {
        String greeting = "{\"recognizer\":\"whisper.cpp X.Y.Z\",\"model\":\"whisper_large_turbo.ggml\",";
        for (boolean continuous : new boolean[]{false, true}) {
            RecognitionTranscript transcript = new RecognitionTranscript(continuous);
            assertNull(transcript.accept(greeting + "\"text\":\"whisper_large_turbo.ggml\"}", false));
            assertNull(transcript.accept(greeting + "\"text\":\"other model\"}", true));
            assertNull(transcript.accept(greeting + "\"text\":\"\",\"no_speech\":true}", true));
            assertEquals("witaj", transcript.accept("{\"text\":\"witaj\"}", true).text);
        }
        assertFalse(VoskJson.isNoSpeech(greeting + "\"no_speech\":true}"));
        assertEquals("witaj", VoskJson.parse("{\"text\":\"witaj\",\"recognizer\":\"vosk\"}").text);
        assertEquals("dobry dzen", VoskJson.parse("{\"text\":\"dobry dzen\",\"model\":\"whisper\"}").text);
    }
}
