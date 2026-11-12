package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

public class SetupActivityTest {
    @Rule
    public final SavedSettings savedSettings = new SavedSettings();

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private SetupActivity activity;

    private void open() {
        finish();
        activity = TestUtil.launch(SetupActivity.class);
    }

    @After
    public void finish() {
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
            activity = null;
        }
    }

    private void click(int id) {
        activity.findViewById(id).performClick();
    }

    private void next(int times) {
        for (int i = 0; i < times; i++) {
            click(R.id.setup_next);
        }
    }

    private void use(int checkbox, boolean checked) {
        ((CheckBox) activity.findViewById(checkbox)).setChecked(checked);
    }

    private String text(int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    private int visibility(int id) {
        return activity.findViewById(id).getVisibility();
    }

    private String line(int label, int state) {
        return activity.getString(label) + ": " + activity.getString(state);
    }

    @Test
    public void summaryOffersClosingOrReturningToHomeEvenWithSkippedSteps() {
        for (boolean close : new boolean[]{true, false}) {
            open();
            Instrumentation.ActivityMonitor home =
                    instrumentation.addMonitor(HomeActivity.class.getName(), null, false);
            try {
                instrumentation.runOnMainSync(() -> {
                    use(R.id.setup_use_output, false);
                    next(4);
                    assertEquals(View.GONE, visibility(R.id.setup_next));
                    assertEquals(View.VISIBLE, visibility(R.id.setup_finish_use));
                    assertEquals(activity.getString(R.string.setup_main_menu), text(R.id.setup_later));
                    click(close ? R.id.setup_finish_use : R.id.setup_later);
                    assertTrue(SpeechSettings.setupCompleted(context));
                    assertTrue(activity.isFinishing());
                });
                if (close) {
                    assertEquals(0, home.getHits());
                } else {
                    Activity homeActivity = instrumentation.waitForMonitorWithTimeout(home, 3000);
                    assertNotNull("Main menu did not open", homeActivity);
                    instrumentation.runOnMainSync(homeActivity::finish);
                }
            } finally {
                instrumentation.removeMonitor(home);
            }
        }
    }

    @Test
    public void readAloudOnlySkipsMicrophoneAndKeyboardAndDoesNotClaimSuccess() {
        open();
        instrumentation.runOnMainSync(() -> {
            use(R.id.setup_use_input, false);
            next(1);
            assertEquals(activity.getString(R.string.voice_output_section), text(R.id.setup_heading));
            next(1);
            assertEquals(View.GONE, visibility(R.id.setup_input_test));
            assertFalse(activity.findViewById(R.id.setup_speech_ok).isEnabled());
            next(1);
            String summary = text(R.id.setup_check);
            assertTrue(summary.contains(line(R.string.setup_speech_test, R.string.setup_pending)));
            assertFalse(summary.contains(activity.getString(R.string.setup_keyboard_enabled)));
            click(R.id.setup_back);
            click(R.id.setup_back);
            assertEquals(activity.getString(R.string.voice_output_section), text(R.id.setup_heading));
        });
    }

    @Test
    public void inputOnlySkipsOutputAndEmptySelectionCannotContinue() {
        open();
        instrumentation.runOnMainSync(() -> {
            use(R.id.setup_use_input, false);
            use(R.id.setup_use_output, false);
            assertFalse(activity.findViewById(R.id.setup_next).isEnabled());
            use(R.id.setup_use_input, true);
            next(1);
            assertEquals(activity.getString(R.string.setup_microphone), text(R.id.setup_heading));
            next(2);
            assertEquals(View.GONE, visibility(R.id.setup_output_test));
            assertEquals(View.VISIBLE, visibility(R.id.setup_input_test));
        });
    }

    @Test
    public void outputCanBeDroppedOnKeyboardOrOutputPage() {
        for (int page : new int[]{2, 3}) {
            open();
            instrumentation.runOnMainSync(() -> {
                next(page);
                assertEquals(View.VISIBLE, visibility(R.id.setup_input_only));
                click(R.id.setup_input_only);
                assertEquals(activity.getString(R.string.setup_try_title), text(R.id.setup_heading));
                assertEquals(View.GONE, visibility(R.id.setup_output_test));
                assertEquals(View.VISIBLE, visibility(R.id.setup_input_test));
                click(R.id.setup_back);
                assertEquals(activity.getString(R.string.voice_input_section), text(R.id.setup_heading));
            });
        }
    }

    @Test
    public void failedSampleCanBeRetriedAndHasToBeConfirmed() throws Exception {
        open();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503));
            server.enqueue(new MockResponse().setHeader("Content-Type", "audio/wav")
                    .setBody(new Buffer().write(TestUtil.silentWav())));
            SpeechSettings.save(context, SpeechSettings.serverUrl(context), SpeechSettings.language(context),
                    server.url("/api/tts/").toString());
            SpeechSettings.saveTtsOptions(context, "bamborak", "setup-test-voice");
            instrumentation.runOnMainSync(() -> {
                use(R.id.setup_use_input, false);
                next(2);
                click(R.id.setup_test_speech);
            });
            assertNotNull(server.takeRequest(15, TimeUnit.SECONDS));
            awaitSampleStatus(R.string.setup_sample_failed);

            instrumentation.runOnMainSync(() -> {
                assertFalse(activity.findViewById(R.id.setup_speech_ok).isEnabled());
                click(R.id.setup_test_speech);
            });
            RecordedRequest request = server.takeRequest(15, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("setup-test-voice",
                    new JSONObject(request.getBody().readUtf8()).getString("speaker_id"));
            awaitSampleStatus(R.string.setup_sample_finished);

            instrumentation.runOnMainSync(() -> {
                CheckBox heard = activity.findViewById(R.id.setup_speech_ok);
                assertTrue(heard.isEnabled());
                assertFalse(heard.isChecked());
                heard.setChecked(true);
                next(1);
                assertTrue(text(R.id.setup_check)
                        .contains(line(R.string.setup_speech_test, R.string.setup_ready)));
            });
        }
    }

    private void awaitSampleStatus(int resource) throws Exception {
        String expected = activity.getString(resource);
        TestUtil.waitFor("sample status \"" + expected + "\"", 15000,
                () -> expected.equals(TestUtil.onMainThread(() -> text(R.id.setup_test_status))));
    }
}
