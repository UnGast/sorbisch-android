package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.ByteString;

public class MainActivityTest {
    @Rule
    public final SavedSettings savedSettings = new SavedSettings();

    private Instrumentation instrumentation;
    private MainActivity activity;

    @Before
    public void launchActivity() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        instrumentation.getUiAutomation().grantRuntimePermission(
                context.getPackageName(), Manifest.permission.RECORD_AUDIO);
        activity = TestUtil.launch(MainActivity.class);
    }

    @After
    public void finishActivity() {
        instrumentation.runOnMainSync(activity::finish);
    }

    private void restartActivity() {
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
        activity = TestUtil.launch(MainActivity.class);
    }

    private void click(int id) {
        instrumentation.runOnMainSync(() -> activity.findViewById(id).performClick());
    }

    private String text(int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    private boolean keepsScreenOn() {
        int flags = activity.getWindow().getAttributes().flags;
        return (flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
    }

    @Test
    public void intentExtrasCannotChangeTtsSettings() {
        String oldUrl = SpeechSettings.ttsUrl(activity);
        for (String action : new String[]{Intent.ACTION_MAIN, Intent.ACTION_PROCESS_TEXT}) {
            Intent intent = new Intent(action)
                    .putExtra("tts_url", "https://example.invalid/tts")
                    .putExtra("tts_text", "Unexpected speech")
                    .putExtra("tts_mode", "external");
            if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
                intent.putExtra(Intent.EXTRA_PROCESS_TEXT, "");
            }
            instrumentation.runOnMainSync(() -> activity.onNewIntent(intent));
            assertEquals(oldUrl, SpeechSettings.ttsUrl(activity));
            assertEquals(oldUrl, text(R.id.tts_url));
            assertEquals("", text(R.id.tts_text));
        }
    }

    @Test
    public void allSaveActionsRejectMissingBamborakSpeaker() {
        String oldSpeaker = SpeechSettings.ttsSpeakerId(activity);
        String oldUrl = SpeechSettings.ttsUrl(activity);
        instrumentation.runOnMainSync(() -> {
            ((Spinner) activity.findViewById(R.id.tts_protocol)).setSelection(1);
            ((EditText) activity.findViewById(R.id.tts_speaker_id)).setText(" ");
            ((EditText) activity.findViewById(R.id.tts_url)).setText("https://example.invalid/tts");
        });
        instrumentation.waitForIdleSync();
        for (int button : new int[]{R.id.save_settings, R.id.speak_android,
                R.id.speak_external, R.id.start_test}) {
            click(button);
            assertEquals(oldUrl, SpeechSettings.ttsUrl(activity));
            assertEquals(oldSpeaker, SpeechSettings.ttsSpeakerId(activity));
            assertNotNull(((EditText) activity.findViewById(R.id.tts_speaker_id)).getError());
            assertTrue(activity.findViewById(R.id.start_test).isEnabled());
        }
    }

    @Test
    public void recognitionControlsStartIdle() {
        Button start = activity.findViewById(R.id.start_test);
        Button stop = activity.findViewById(R.id.stop_test);

        assertTrue(start.isEnabled());
        assertFalse(stop.isEnabled());
    }

    @Test
    public void ttsInputStartsCompactAndSupportsMultipleLines() {
        EditText ttsText = activity.findViewById(R.id.tts_text);

        assertEquals(1, ttsText.getMinLines());
        assertEquals(3, ttsText.getMaxLines());
        assertTrue((ttsText.getInputType() & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0);
    }

    @Test
    public void advancedSettingsAreHiddenUntilRequested() {
        View advancedControls = activity.findViewById(R.id.advanced_controls);
        assertEquals(View.GONE, advancedControls.getVisibility());

        click(R.id.toggle_advanced);

        assertEquals(View.VISIBLE, advancedControls.getVisibility());
    }

    @Test
    public void bamborakSettingsSurviveActivityRestart() {
        click(R.id.toggle_advanced);
        instrumentation.runOnMainSync(() -> {
            ((Spinner) activity.findViewById(R.id.tts_protocol)).setSelection(1);
            ((EditText) activity.findViewById(R.id.tts_speaker_id)).setText(" test-voice ");
        });
        instrumentation.waitForIdleSync();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.tts_speaker_fields).getVisibility());
        click(R.id.save_settings);
        assertEquals("bamborak", SpeechSettings.ttsProtocol(activity));
        assertEquals("test-voice", SpeechSettings.ttsSpeakerId(activity));

        restartActivity();

        assertEquals(1, ((Spinner) activity.findViewById(R.id.tts_protocol)).getSelectedItemPosition());
        assertEquals("test-voice", text(R.id.tts_speaker_id));
    }

    @Test
    public void recognitionOptionsSurviveActivityRestart() {
        // Pick the opposite of the packaged defaults, otherwise nothing would be stored.
        int rateIndex = BuildConfig.DEFAULT_RECOGNIZER_SAMPLE_RATE == 48000 ? 0 : 1;
        boolean continuous = !BuildConfig.DEFAULT_CONTINUOUS_RECOGNITION;
        instrumentation.runOnMainSync(() -> {
            ((Spinner) activity.findViewById(R.id.recognition_sample_rate)).setSelection(rateIndex);
            ((CheckBox) activity.findViewById(R.id.continuous_recognition)).setChecked(continuous);
            activity.findViewById(R.id.save_settings).performClick();
        });
        assertEquals(rateIndex == 1 ? 48000 : 16000, SpeechSettings.sampleRate(activity));
        assertEquals(continuous, SpeechSettings.continuousRecognition(activity));

        restartActivity();

        assertEquals(rateIndex,
                ((Spinner) activity.findViewById(R.id.recognition_sample_rate)).getSelectedItemPosition());
        assertEquals(continuous, ((CheckBox) activity.findViewById(R.id.continuous_recognition)).isChecked());
    }

    @Test
    public void interruptedDictationIsKeptAsDraft() throws Exception {
        Context context = instrumentation.getTargetContext();
        CountDownLatch audio = new CountDownLatch(1);
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                private boolean sent;

                @Override
                public void onMessage(WebSocket socket, ByteString frame) {
                    if (!sent) {
                        sent = true;
                        socket.send("{\"text\":\"dobry dzen\"}");
                        audio.countDown();
                    }
                }

                @Override
                public void onMessage(WebSocket socket, String text) {
                    if (text.contains("eof")) {
                        socket.send("{\"text\":\"\"}");
                    }
                }

                @Override
                public void onClosing(WebSocket socket, int code, String reason) {
                    socket.close(code, reason);
                }
            }));
            server.start();
            String endpoint = server.url("/").toString().replace("http://", "ws://");
            instrumentation.runOnMainSync(() -> {
                ((EditText) activity.findViewById(R.id.server_url)).setText(endpoint);
                ((CheckBox) activity.findViewById(R.id.continuous_recognition)).setChecked(true);
                activity.findViewById(R.id.start_test).performClick();
                assertTrue(keepsScreenOn());
            });
            assertTrue(audio.await(10, TimeUnit.SECONDS));
            TestUtil.waitFor("the draft", 5000,
                    () -> "dobry dzen".equals(SpeechSettings.dictationDraft(context)));

            instrumentation.runOnMainSync(() -> {
                activity.onStop();
                assertFalse(keepsScreenOn());
            });
            restartActivity();

            assertEquals("dobry dzen", text(R.id.transcript));
        }
    }
}
