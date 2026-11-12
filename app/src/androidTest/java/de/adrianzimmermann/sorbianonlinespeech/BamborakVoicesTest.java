package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class BamborakVoicesTest {
    @Rule
    public final SavedSettings savedSettings = new SavedSettings();

    @Test
    public void keepsServerIdsAndSkipsOtherLanguages() throws Exception {
        List<BamborakVoices> voices = BamborakVoices.parse("["
                + "{\"id\":\"weronika\",\"name\":\"Weronika\",\"language\":\"hsb\"},"
                + "{\"id\":\"thorsten\",\"language\":\"de\"},"
                + "{\"id\":\"\",\"language\":\"hsb\"}]", "hsb");

        assertEquals(1, voices.size());
        assertEquals("weronika", voices.get(0).id);
        assertEquals("Weronika", voices.get(0).name);
    }

    @Test
    public void filtersByRequestedLanguage() throws Exception {
        String json = "[{\"id\":\"hsb-voice\",\"language\":\"hsb\"},"
                + "{\"id\":\"dsb-voice\",\"name\":\"DSB\",\"language\":\"dsb-DE\"},"
                + "{\"id\":\"unlabelled\"}]";
        List<BamborakVoices> voices = BamborakVoices.parse(json, "dsb");

        assertEquals(1, voices.size());
        assertEquals("dsb-voice", voices.get(0).id);
        assertEquals("dsb", voices.get(0).language);
        assertEquals("hsb-voice", BamborakVoices.parse(json, "hsb").get(0).id);
    }

    @Test
    public void speakerListIsNextToTheTtsEndpoint() {
        assertEquals("https://example.org/prefix/api/fetch_speakers/",
                BamborakVoices.endpoint("https://example.org/prefix/api/tts/?ignored=true").toString());
        try {
            BamborakVoices.endpoint("https://example.org/unrelated");
            fail("Expected the URL to be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void failedRefreshKeepsSavedVoice() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SpeechSettings.completeSetup(context);
        HomeActivity home = TestUtil.launch(HomeActivity.class);
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503));
            server.start();
            SpeechSettings.save(context, SpeechSettings.serverUrl(context), SpeechSettings.language(context),
                    server.url("/api/tts/").toString());
            SpeechSettings.saveTtsOptions(context, SpeechSettings.TTS_BAMBORAK, "saved-voice");

            instrumentation.runOnMainSync(() -> home.findViewById(R.id.choose_voice).performClick());

            assertEquals("/api/fetch_speakers/", server.takeRequest(5, TimeUnit.SECONDS).getPath());
            String failed = home.getString(R.string.voices_failed);
            TextView status = home.findViewById(R.id.home_status);
            TestUtil.waitFor("the error message", 5000,
                    () -> failed.equals(TestUtil.onMainThread(() -> status.getText().toString())));
            assertEquals("saved-voice", SpeechSettings.ttsSpeakerId(context));
        } finally {
            instrumentation.runOnMainSync(home::finish);
        }
    }
}
