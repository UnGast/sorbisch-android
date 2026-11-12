package de.adrianzimmermann.sorbianonlinespeech;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SetupActivity extends LocalizedActivity {
    private static final int REQ_AUDIO = 10;
    private static final int WELCOME = 0;
    private static final int MICROPHONE = 1;
    private static final int KEYBOARD = 2;
    private static final int OUTPUT = 3;
    private static final int TEST = 4;
    private static final int SUMMARY = 5;

    private int step;
    private CheckBox useInput;
    private CheckBox useOutput;
    private CheckBox dictationOk;
    private CheckBox speechOk;
    private EditText testText;
    private TextToSpeech speech;
    private int speechGeneration;
    private boolean sampleFinished;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable speechTimeout = () -> finishSample(false);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_setup);
        useInput = findViewById(R.id.setup_use_input);
        useOutput = findViewById(R.id.setup_use_output);
        dictationOk = findViewById(R.id.setup_dictation_ok);
        speechOk = findViewById(R.id.setup_speech_ok);
        testText = findViewById(R.id.setup_test_text);
        boolean inputOnly = getIntent().getIntExtra("start_step", 0) == MICROPHONE;
        useInput.setChecked(state == null || state.getBoolean("input", true));
        useOutput.setChecked(state == null ? !inputOnly : state.getBoolean("output", true));
        step = state == null ? (inputOnly ? MICROPHONE : WELCOME) : state.getInt("step", WELCOME);
        sampleFinished = state != null && state.getBoolean("sample_finished");
        useInput.setOnCheckedChangeListener((button, checked) -> render());
        useOutput.setOnCheckedChangeListener((button, checked) -> render());
        findViewById(R.id.setup_back).setOnClickListener(v -> move(-1));
        findViewById(R.id.setup_next).setOnClickListener(v -> move(1));
        findViewById(R.id.setup_finish_use).setOnClickListener(v -> {
            SpeechSettings.completeSetup(this);
            finishAffinity();
        });
        findViewById(R.id.setup_later).setOnClickListener(v -> {
            SpeechSettings.completeSetup(this);
            if (step == SUMMARY) {
                startActivity(new Intent(this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
            finish();
        });
        findViewById(R.id.setup_input_only).setOnClickListener(v -> {
            stopSample();
            step = TEST;
            useOutput.setChecked(false);
            findViewById(R.id.setup_scroll).scrollTo(0, 0);
        });
        findViewById(R.id.setup_test_keyboard).setOnClickListener(v ->
                getSystemService(InputMethodManager.class).showInputMethodPicker());
        findViewById(R.id.setup_test_speech).setOnClickListener(v -> playSample());
        findViewById(R.id.setup_stop_speech).setOnClickListener(v -> {
            stopSample();
            testStatus(R.string.status_speech_stopped);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && useInput != null) render();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("step", step);
        state.putBoolean("input", useInput.isChecked());
        state.putBoolean("output", useOutput.isChecked());
        state.putBoolean("sample_finished", sampleFinished);
        super.onSaveInstanceState(state);
    }

    private List<Integer> steps() {
        List<Integer> result = new ArrayList<>();
        result.add(WELCOME);
        if (useInput.isChecked()) {
            result.add(MICROPHONE);
            result.add(KEYBOARD);
        }
        if (useOutput.isChecked()) result.add(OUTPUT);
        result.add(TEST);
        result.add(SUMMARY);
        return result;
    }

    private void move(int direction) {
        List<Integer> pages = steps();
        int next = pages.indexOf(step) + direction;
        if (next < 0 || next >= pages.size()) return;
        stopSample();
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(testText.getWindowToken(), 0);
        testText.clearFocus();
        step = pages.get(next);
        render();
        findViewById(R.id.setup_scroll).post(() -> findViewById(R.id.setup_scroll).scrollTo(0, 0));
    }

    private void render() {
        int[] titles = {R.string.setup_welcome, R.string.setup_microphone, R.string.voice_input_section,
                R.string.voice_output_section, R.string.setup_try_title, R.string.setup_summary_title};
        int[] descriptions = {R.string.setup_online_notice, R.string.setup_microphone_help,
                R.string.setup_keyboard_help, R.string.setup_tts_help, R.string.setup_try_help,
                R.string.setup_summary_help};
        List<Integer> pages = steps();
        int position = pages.indexOf(step) + 1;
        ((TextView) findViewById(R.id.setup_step)).setText(
                getString(R.string.setup_step_number, position, pages.size()));
        ProgressBar progress = findViewById(R.id.setup_progress);
        progress.setMax(pages.size());
        progress.setProgress(position);
        ((TextView) findViewById(R.id.setup_heading)).setText(titles[step]);
        ((TextView) findViewById(R.id.setup_description)).setText(descriptions[step]);
        if (step == SUMMARY) {
            ((TextView) findViewById(R.id.setup_description)).setText(useInput.isChecked()
                    ? R.string.setup_use_keyboard_help : R.string.setup_use_output_help);
        }
        findViewById(R.id.setup_back).setEnabled(step != WELCOME);
        Button next = findViewById(R.id.setup_next);
        next.setText(R.string.setup_next);
        next.setVisibility(step == SUMMARY ? View.GONE : View.VISIBLE);
        show(R.id.setup_finish_use, step == SUMMARY);
        ((Button) findViewById(R.id.setup_later)).setText(step == SUMMARY
                ? R.string.setup_main_menu : R.string.setup_later);
        next.setEnabled(useInput.isChecked() || useOutput.isChecked());
        show(R.id.setup_functions, step == WELCOME);
        show(R.id.setup_input_only, (step == KEYBOARD || step == OUTPUT)
                && useInput.isChecked() && useOutput.isChecked());
        show(R.id.setup_tests, step == TEST);
        show(R.id.setup_input_test, useInput.isChecked());
        show(R.id.setup_output_test, useOutput.isChecked());
        speechOk.setEnabled(sampleFinished);
        configureLanguage();
        Button action = findViewById(R.id.setup_action);
        Button secondary = findViewById(R.id.setup_secondary);
        TextView check = findViewById(R.id.setup_check);
        action.setVisibility(View.GONE);
        secondary.setVisibility(View.GONE);
        action.setEnabled(true);
        check.setText("");
        if (step == WELCOME) {
            button(secondary, R.string.privacy_title, () -> startActivity(new Intent(this,
                    InformationActivity.class).putExtra("page", "privacy")));
        } else if (step == MICROPHONE) {
            check.setText(line(R.string.setup_microphone, microphoneAllowed()));
            button(action, R.string.setup_allow_microphone, () ->
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO));
            action.setEnabled(!microphoneAllowed());
            button(secondary, R.string.setup_app_permissions, () -> startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        } else if (step == KEYBOARD) {
            check.setText(inputStatus());
            button(action, R.string.open_ime_settings, () ->
                    HomeActivity.openSettings(this, Settings.ACTION_INPUT_METHOD_SETTINGS));
            button(secondary, R.string.show_ime_picker, () ->
                    getSystemService(InputMethodManager.class).showInputMethodPicker());
        } else if (step == OUTPUT) {
            check.setText(outputStatus());
            button(action, R.string.open_tts_settings, () ->
                    HomeActivity.openSettings(this, HomeActivity.ACTION_TTS_SETTINGS));
            button(secondary, R.string.setup_read_aloud, () -> ReadAloudSettings.open(this));
        } else if (step == TEST) {
            List<String> statuses = new ArrayList<>();
            if (useInput.isChecked()) statuses.add(inputStatus());
            if (useOutput.isChecked()) statuses.add(outputStatus());
            check.setText(TextUtils.join("\n", statuses));
        } else if (step == SUMMARY) {
            String result = "";
            if (useInput.isChecked()) result = inputStatus() + "\n" + line(R.string.setup_dictation_test,
                    dictationOk.isChecked() && testText.length() > 0);
            if (useOutput.isChecked()) result += (result.isEmpty() ? "" : "\n\n") + outputStatus()
                    + "\n" + line(R.string.setup_speech_test, sampleFinished && speechOk.isChecked());
            check.setText(result);
        }
        check.setVisibility(check.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private void configureLanguage() {
        boolean output = step == OUTPUT;
        String[] languages = output ? SpeechSettings.availableTtsLanguages() : SpeechSettings.availableLanguages();
        boolean visible = (step == KEYBOARD || output) && languages.length > 1;
        show(R.id.setup_language_label, visible);
        show(R.id.setup_language, visible);
        if (!visible) return;
        ((TextView) findViewById(R.id.setup_language_label)).setText(
                output ? R.string.tts_language : R.string.recognition_language);
        Spinner spinner = findViewById(R.id.setup_language);
        String selected = output ? SpeechSettings.ttsLanguage(this) : SpeechSettings.language(this);
        LanguageSpinner.bind(spinner, languages, selected, language -> {
            if (output) {
                SpeechSettings.saveTtsLanguage(this, language);
                sampleFinished = false;
                speechOk.setChecked(false);
            } else {
                SpeechSettings.saveLanguage(this, language);
                dictationOk.setChecked(false);
            }
        });
    }

    private boolean microphoneAllowed() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean keyboardSelected() {
        String selected = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName component = selected == null ? null : ComponentName.unflattenFromString(selected);
        return component != null && getPackageName().equals(component.getPackageName());
    }

    private String inputStatus() {
        return line(R.string.setup_microphone, microphoneAllowed()) + "\n"
                + line(R.string.setup_keyboard_enabled, HomeActivity.keyboardEnabled(this)) + "\n"
                + line(R.string.setup_keyboard_selected, keyboardSelected());
    }

    private String outputStatus() {
        String engine = Settings.Secure.getString(getContentResolver(), Settings.Secure.TTS_DEFAULT_SYNTH);
        boolean preferred = getPackageName().equals(engine);
        String accessibility = ReadAloudSettings.installedService(this) == null
                ? getString(R.string.setup_read_aloud_unknown)
                : line(R.string.setup_read_aloud_service, ReadAloudSettings.enabled(this));
        return line(R.string.setup_preferred_engine, preferred) + "\n" + accessibility;
    }

    private String line(int label, boolean ready) {
        return getString(label) + ": " + getString(ready ? R.string.setup_ready : R.string.setup_pending);
    }

    private void playSample() {
        stopSample();
        sampleFinished = false;
        speechOk.setChecked(false);
        speechOk.setEnabled(false);
        testStatus(R.string.tts_starting);
        int generation = speechGeneration;
        handler.postDelayed(speechTimeout, 65_000);
        speech = new TextToSpeech(this, status -> handler.post(() -> {
            if (generation != speechGeneration || speech == null) return;
            if (status != TextToSpeech.SUCCESS) {
                finishSample(false);
                return;
            }
            speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String id) {
                }

                @Override
                public void onDone(String id) {
                    report(true);
                }

                @Override
                public void onError(String id) {
                    report(false);
                }
                private void report(boolean success) {
                    handler.post(() -> { if (generation == speechGeneration) finishSample(success); });
                }
            });
            String language = SpeechSettings.ttsLanguage(this);
            if (speech.setLanguage(Locale.forLanguageTag(language)) < 0) {
                finishSample(false);
                return;
            }
            testStatus(R.string.setup_sample_playing);
            if (speech.speak(SpeechSettings.ttsSampleText(this, language), TextToSpeech.QUEUE_FLUSH,
                    null, "setup-sample") == TextToSpeech.ERROR) finishSample(false);
        }), getPackageName());
    }

    private void finishSample(boolean success) {
        stopSample();
        sampleFinished = success;
        speechOk.setEnabled(success);
        testStatus(success ? R.string.setup_sample_finished : R.string.setup_sample_failed);
    }

    private void stopSample() {
        speechGeneration++;
        handler.removeCallbacks(speechTimeout);
        if (speech != null) {
            speech.stop();
            speech.shutdown();
            speech = null;
        }
    }

    private void testStatus(int message) {
        ((TextView) findViewById(R.id.setup_test_status)).setText(message);
    }

    private void show(int id, boolean visible) {
        findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void button(Button button, int label, Runnable action) {
        button.setVisibility(View.VISIBLE);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
    }

    @Override
    protected void onStop() {
        if (speech != null) testStatus(R.string.status_speech_stopped);
        stopSample();
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        render();
    }
}
