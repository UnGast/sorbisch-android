package de.adrianzimmermann.sorbianonlinespeech;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends LocalizedActivity {
    private static final int REQ_AUDIO = 10;
    // Index in R.array.tts_protocol_options.
    private static final int PROTOCOL_BAMBORAK = 1;
    private EditText serverUrl;
    private EditText ttsText;
    private EditText ttsUrl;
    private Spinner ttsProtocol;
    private EditText ttsSpeakerId;
    private Spinner languageSpinner;
    private Spinner recognitionSampleRate;
    private CheckBox continuousRecognition;
    private TextView status;
    private TextView transcript;
    private Button startRecognitionButton;
    private Button stopRecognitionButton;
    private OnlineSpeechSession session;
    private TtsPlayer ttsPlayer;
    private int sessionGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ttsPlayer = new TtsPlayer(this, new TtsPlayer.Listener() {
            @Override
            public void onStatus(String message) {
                runOnUiThread(() -> setStatus(message));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> setStatus(getString(R.string.tts_error, message)));
            }
        });
        setContentView(R.layout.activity_main);
        findViewById(R.id.advanced_back).setOnClickListener(v -> finish());
        bindViews();
        configureControls();
        loadSettings();
        transcript.setText(SpeechSettings.dictationDraft(this));
        if (getIntent().getBooleanExtra("show_settings", false) || isProcessTextIntent(getIntent())) {
            findViewById(R.id.toggle_advanced).performClick();
        }
        handleTtsIntent(getIntent());
    }

    @Override
    protected void onStop() {
        stopSession();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        sessionGeneration++;
        if (session != null) session.cancel();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (ttsPlayer != null) {
            ttsPlayer.shutdown();
            ttsPlayer = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleTtsIntent(intent);
    }

    private void bindViews() {
        serverUrl = findViewById(R.id.server_url);
        ttsText = findViewById(R.id.tts_text);
        ttsUrl = findViewById(R.id.tts_url);
        ttsProtocol = findViewById(R.id.tts_protocol);
        ttsSpeakerId = findViewById(R.id.tts_speaker_id);
        languageSpinner = findViewById(R.id.language_spinner);
        recognitionSampleRate = findViewById(R.id.recognition_sample_rate);
        continuousRecognition = findViewById(R.id.continuous_recognition);
        status = findViewById(R.id.status);
        transcript = findViewById(R.id.transcript);
    }

    private void configureControls() {
        findViewById(R.id.expert_diagnostics).setOnClickListener(v -> RecognitionDiagnostics.show(this));
        recognitionSampleRate.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"16 kHz", "48 kHz"}));
        ttsProtocol.setAdapter(ArrayAdapter.createFromResource(this,
                R.array.tts_protocol_options, android.R.layout.simple_spinner_dropdown_item));
        ttsProtocol.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                findViewById(R.id.tts_speaker_fields).setVisibility(
                        position == PROTOCOL_BAMBORAK ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        findViewById(R.id.toggle_advanced).setOnClickListener(v ->
                showAdvanced(findViewById(R.id.advanced_controls).getVisibility() != View.VISIBLE));
        String[] languages = SpeechSettings.availableLanguages();
        languageSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languages));
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (selectedLanguage().equals(SpeechSettings.language(MainActivity.this))) return;
                SpeechSettings.saveLanguage(MainActivity.this, selectedLanguage());
                serverUrl.setText(SpeechSettings.serverUrl(MainActivity.this));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        configureTtsLanguage();
        Button save = findViewById(R.id.save_settings);
        save.setOnClickListener(v -> {
            if (saveSettings()) {
                setStatus(getString(R.string.status_settings_saved));
            }
        });
        startRecognitionButton = findViewById(R.id.start_test);
        startRecognitionButton.setOnClickListener(v -> startSession());
        stopRecognitionButton = findViewById(R.id.stop_test);
        stopRecognitionButton.setOnClickListener(v -> stopSession());
        setRecognitionControls(false);
        Button serviceTest = findViewById(R.id.open_service_test);
        serviceTest.setOnClickListener(v -> startActivity(new Intent(this, ServiceTestActivity.class)));
        findViewById(R.id.reset_settings).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.reset_settings_confirm)
                .setPositiveButton(R.string.reset_settings, (dialog, which) -> {
                    SpeechSettings.resetServerSettings(this);
                    configureTtsLanguage();
                    loadSettings();
                }).setNegativeButton(android.R.string.cancel, null).show());
        Button speakWithEngine = findViewById(R.id.speak_android);
        speakWithEngine.setOnClickListener(v -> {
            if (saveSettings()) {
                ttsPlayer.speakWithEngine(ttsText.getText().toString(), SpeechSettings.ttsLanguage(this));
            }
        });
        Button speakExternal = findViewById(R.id.speak_external);
        speakExternal.setOnClickListener(v -> {
            if (saveSettings()) {
                ttsPlayer.speakExternal(ttsText.getText().toString(), SpeechSettings.ttsLanguage(this),
                        ttsUrl.getText().toString());
            }
        });
        Button stopSpeech = findViewById(R.id.stop_speech);
        stopSpeech.setOnClickListener(v -> {
            ttsPlayer.stop();
            setStatus(getString(R.string.status_speech_stopped));
        });
        findViewById(R.id.open_tts_settings).setOnClickListener(v ->
                HomeActivity.openSettings(this, HomeActivity.ACTION_TTS_SETTINGS));
    }

    private void startSession() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (session != null) {
            return;
        }
        if (!saveSettings()) {
            return;
        }
        int generation = ++sessionGeneration;
        setRecognitionControls(true);
        OnlineSpeechSession newSession = new OnlineSpeechSession(
                this, serverUrl.getText().toString(), selectedLanguage(),
                new RecognitionListener() {
                    @Override
                    public void onReady() {
                        ifCurrent(generation, () -> status.setText(R.string.status_connected));
                    }

                    @Override
                    public void onSpeechStart() {
                        ifCurrent(generation, () -> status.setText(R.string.status_recording));
                    }

                    @Override
                    public void onResult(RecognitionResult result) {
                        ifCurrent(generation, () -> {
                            transcript.setText(result.text);
                            SpeechSettings.saveDictationDraft(MainActivity.this, result.text);
                            if (result.partial) {
                                status.setText(getString(R.string.status_partial, result.text));
                            } else {
                                status.setText(R.string.status_result_received);
                            }
                        });
                    }

                    @Override
                    public void onNoSpeech() {
                        ifCurrent(generation, () -> finishSession(getString(R.string.status_no_speech)));
                    }

                    @Override
                    public void onError(String message) {
                        ifCurrent(generation, () -> finishSession(getString(R.string.status_error, message)));
                    }

                    @Override
                    public void onComplete() {
                        ifCurrent(generation, () -> finishSession(getString(R.string.status_stopped)));
                    }
                });
        session = newSession;
        newSession.start();
    }

    // Drops callbacks of a session that has been cancelled in the meantime.
    private void ifCurrent(int generation, Runnable action) {
        if (generation == sessionGeneration) {
            action.run();
        }
    }

    private void stopSession() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        OnlineSpeechSession localSession = session;
        if (localSession != null) {
            stopRecognitionButton.setEnabled(false);
            status.setText(R.string.status_stopping);
            localSession.stop();
        }
    }

    private void finishSession(String message) {
        status.setText(message);
        session = null;
        setRecognitionControls(false);
    }

    private void setRecognitionControls(boolean recording) {
        if (recording) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        startRecognitionButton.setEnabled(!recording);
        stopRecognitionButton.setEnabled(recording);
    }

    private void loadSettings() {
        recognitionSampleRate.setSelection(SpeechSettings.sampleRate(this) == 48000 ? 1 : 0);
        continuousRecognition.setChecked(SpeechSettings.continuousRecognition(this));
        serverUrl.setText(SpeechSettings.serverUrl(this));
        loadTtsSettings();
        String language = SpeechSettings.language(this);
        for (int i = 0; i < languageSpinner.getCount(); i++) {
            if (languageSpinner.getItemAtPosition(i).equals(language)) {
                languageSpinner.setSelection(i);
                break;
            }
        }
    }

    private void loadTtsSettings() {
        ttsUrl.setText(SpeechSettings.ttsUrl(this));
        ttsProtocol.setSelection(SpeechSettings.TTS_BAMBORAK.equals(SpeechSettings.ttsProtocol(this))
                ? PROTOCOL_BAMBORAK : 0);
        ttsSpeakerId.setText(SpeechSettings.ttsSpeakerId(this));
    }

    private void configureTtsLanguage() {
        String[] languages = SpeechSettings.availableTtsLanguages();
        Spinner spinner = findViewById(R.id.tts_language);
        int visibility = languages.length > 1 ? View.VISIBLE : View.GONE;
        spinner.setVisibility(visibility);
        findViewById(R.id.tts_language_label).setVisibility(visibility);
        LanguageSpinner.bind(spinner, languages, SpeechSettings.ttsLanguage(this), language -> {
            ttsPlayer.stop();
            SpeechSettings.saveTtsLanguage(this, language);
            loadTtsSettings();
        });
    }

    private void showAdvanced(boolean show) {
        findViewById(R.id.advanced_controls).setVisibility(show ? View.VISIBLE : View.GONE);
        ((Button) findViewById(R.id.toggle_advanced)).setText(
                show ? R.string.hide_advanced_settings : R.string.show_advanced_settings);
    }

    private boolean bamborakSelected() {
        return ttsProtocol.getSelectedItemPosition() == PROTOCOL_BAMBORAK;
    }

    private boolean saveSettings() {
        if (bamborakSelected() && ttsSpeakerId.getText().toString().trim().isEmpty()) {
            showAdvanced(true);
            ttsSpeakerId.setError(getString(R.string.tts_speaker_required));
            ttsSpeakerId.requestFocus();
            return false;
        }
        ttsSpeakerId.setError(null);
        SpeechSettings.saveRecognitionOptions(this,
                recognitionSampleRate.getSelectedItemPosition() == 1 ? 48000 : 16000,
                continuousRecognition.isChecked());
        SpeechSettings.save(this, serverUrl.getText().toString(), selectedLanguage(), ttsUrl.getText().toString());
        SpeechSettings.saveTtsOptions(this,
                bamborakSelected() ? SpeechSettings.TTS_BAMBORAK : SpeechSettings.TTS_SIMPLE,
                ttsSpeakerId.getText().toString());
        return true;
    }

    /** Reads out text another app sent through the "process text" selection menu. */
    private void handleTtsIntent(Intent intent) {
        if (!isProcessTextIntent(intent)) {
            return;
        }
        String text = selectedText(intent);
        if (text == null) {
            return;
        }
        ttsText.setText(text);
        showAdvanced(true);
        ttsPlayer.speakWithEngine(text, SpeechSettings.ttsLanguage(this));
    }

    static boolean isProcessTextIntent(Intent intent) {
        return intent != null && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction());
    }

    static String selectedText(Intent intent) {
        CharSequence text = intent == null ? null : intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        return text == null ? null : text.toString();
    }

    private String selectedLanguage() {
        Object selected = languageSpinner.getSelectedItem();
        return selected == null ? SpeechSettings.DEFAULT_LANGUAGE : selected.toString();
    }

    private void setStatus(String message) {
        status.setText(message);
    }
}
