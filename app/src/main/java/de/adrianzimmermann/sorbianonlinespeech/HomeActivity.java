package de.adrianzimmermann.sorbianonlinespeech;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HomeActivity extends LocalizedActivity {
    private final OkHttpClient http = new OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS).build();
    static final String ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

    private Call voiceRequest;
    private TtsPlayer player;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        player = new TtsPlayer(this, new TtsPlayer.Listener() {
            @Override
            public void onStatus(String message) {
                runOnUiThread(() -> status(message));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> status(message));
            }
        });
        findViewById(R.id.setup_input).setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                openSettings(this, Settings.ACTION_INPUT_METHOD_SETTINGS);
            } else {
                startActivity(new Intent(this, SetupActivity.class).putExtra("start_step", 1));
            }
        });
        findViewById(R.id.select_input).setOnClickListener(v ->
                getSystemService(InputMethodManager.class).showInputMethodPicker());
        findViewById(R.id.setup_read_aloud).setOnClickListener(v -> ReadAloudSettings.open(this));
        findViewById(R.id.select_engine).setOnClickListener(v -> openSettings(this, ACTION_TTS_SETTINGS));
        findViewById(R.id.choose_voice).setOnClickListener(v -> loadVoices());
        findViewById(R.id.preview_voice).setOnClickListener(v -> player.speakExternal(
                SpeechSettings.ttsSampleText(this, SpeechSettings.ttsLanguage(this)),
                SpeechSettings.ttsLanguage(this), SpeechSettings.ttsUrl(this)));
        findViewById(R.id.stop_preview).setOnClickListener(v -> {
            player.stop();
            status(getString(R.string.status_speech_stopped));
        });
        findViewById(R.id.show_draft).setOnClickListener(v -> showDraft());
        findViewById(R.id.reopen_setup).setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        findViewById(R.id.open_advanced).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class).putExtra("show_settings", true)));
        findViewById(R.id.open_privacy).setOnClickListener(v -> openInformation("privacy"));
        findViewById(R.id.open_imprint).setOnClickListener(v -> openInformation("imprint"));
        findViewById(R.id.open_licenses).setOnClickListener(v -> openInformation("licenses"));
        configureAppLanguage();
        if (state == null && !SpeechSettings.setupCompleted(this)) {
            startActivity(new Intent(this, SetupActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        configureVoiceLanguage();
        updateVoice();
        boolean microphone = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean keyboard = keyboardEnabled(this);
        ((TextView) findViewById(R.id.setup_status)).setText(getString(R.string.setup_input_status,
                getString(microphone ? R.string.setup_ready : R.string.setup_missing),
                getString(keyboard ? R.string.setup_ready : R.string.setup_missing)));
    }

    static boolean keyboardEnabled(Activity activity) {
        InputMethodManager manager = activity.getSystemService(InputMethodManager.class);
        for (InputMethodInfo info : manager.getEnabledInputMethodList()) {
            if (info.getPackageName().equals(activity.getPackageName())) return true;
        }
        return false;
    }

    private void configureAppLanguage() {
        Spinner spinner = findViewById(R.id.app_language);
        String[] labels = {getString(R.string.app_language_system), "Deutsch", "English", "Hornjoserbšćina"};
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        String selected = AppLanguage.selected(this);
        int index = Arrays.asList(AppLanguage.TAGS).indexOf(selected);
        spinner.setSelection(Math.max(index, 0));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String language = AppLanguage.TAGS[position];
                if (language.equals(AppLanguage.selected(HomeActivity.this))) return;
                AppLanguage.set(HomeActivity.this, language);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recreate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    static void openSettings(Activity activity, String action) {
        try {
            activity.startActivity(new Intent(action));
        } catch (ActivityNotFoundException unavailable) {
            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void loadVoices() {
        if (voiceRequest != null) voiceRequest.cancel();
        if (!SpeechSettings.TTS_BAMBORAK.equals(SpeechSettings.ttsProtocol(this))) {
            status(getString(R.string.voices_need_bamborak));
            return;
        }
        String endpoint = SpeechSettings.ttsUrl(this);
        String language = SpeechSettings.ttsLanguage(this);
        try {
            voiceRequest = http.newCall(new Request.Builder().url(BamborakVoices.endpoint(endpoint)).build());
        } catch (IllegalArgumentException e) {
            status(getString(R.string.voices_failed));
            return;
        }
        status(getString(R.string.voices_loading));
        voiceRequest.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                reportFailure(call);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response body = response) {
                    if (!body.isSuccessful() || body.body() == null) throw new IOException();
                    List<BamborakVoices> voices = BamborakVoices.parse(body.body().string(), language);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || call != voiceRequest
                                || !endpoint.equals(SpeechSettings.ttsUrl(HomeActivity.this))
                                || !language.equals(SpeechSettings.ttsLanguage(HomeActivity.this))) return;
                        String languageName = SpeechSettings.languageLabel(HomeActivity.this, language);
                        if (voices.isEmpty()) {
                            status(getString(R.string.voices_empty, languageName));
                            return;
                        }
                        status("");
                        String[] labels = new String[voices.size()];
                        int selected = -1;
                        for (int i = 0; i < labels.length; i++) {
                            labels[i] = voices.get(i).name + " · " + languageName;
                            if (voices.get(i).id.equals(SpeechSettings.ttsSpeakerId(HomeActivity.this))) selected = i;
                        }
                        new AlertDialog.Builder(HomeActivity.this).setTitle(R.string.choose_voice)
                                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                                    player.stop();
                                    SpeechSettings.saveTtsOptions(HomeActivity.this, language,
                                            SpeechSettings.TTS_BAMBORAK, voices.get(which).id);
                                    updateVoice();
                                    dialog.dismiss();
                                }).setNegativeButton(android.R.string.cancel, null).show();
                    });
                } catch (Exception error) {
                    reportFailure(call);
                }
            }
        });
    }

    private void reportFailure(Call call) {
        runOnUiThread(() -> {
            if (!isDestroyed() && !isFinishing() && call == voiceRequest && !call.isCanceled()) {
                status(getString(R.string.voices_failed));
            }
        });
    }

    private void updateVoice() {
        ((TextView) findViewById(R.id.current_voice)).setText(getString(R.string.selected_voice,
                SpeechSettings.languageLabel(this, SpeechSettings.ttsLanguage(this)),
                SpeechSettings.ttsSpeakerId(this)));
    }

    private void configureVoiceLanguage() {
        String[] languages = SpeechSettings.availableTtsLanguages();
        Spinner spinner = findViewById(R.id.home_tts_language);
        int visibility = languages.length > 1 ? View.VISIBLE : View.GONE;
        findViewById(R.id.home_tts_language_label).setVisibility(visibility);
        spinner.setVisibility(visibility);
        LanguageSpinner.bind(spinner, languages, SpeechSettings.ttsLanguage(this), language -> {
            player.stop();
            if (voiceRequest != null) voiceRequest.cancel();
            SpeechSettings.saveTtsLanguage(this, language);
            status("");
            updateVoice();
        });
    }

    private void showDraft() {
        String text = SpeechSettings.dictationDraft(this);
        TextView content = new TextView(this);
        content.setText(text.isEmpty() ? getString(R.string.no_draft) : text);
        content.setTextIsSelectable(true);
        content.setTextSize(18);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this).setTitle(R.string.last_dictation).setView(scroll)
                .setPositiveButton(android.R.string.copy, (dialog, which) -> {
                    if (text.isEmpty()) return;
                    getSystemService(ClipboardManager.class).setPrimaryClip(
                            ClipData.newPlainText(getString(R.string.last_dictation), text));
                })
                .setNeutralButton(R.string.delete_draft, (dialog, which) ->
                        SpeechSettings.saveDictationDraft(this, ""))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void status(String message) {
        TextView status = findViewById(R.id.home_status);
        status.setText(message);
        status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openInformation(String page) {
        startActivity(new Intent(this, InformationActivity.class).putExtra("page", page));
    }

    @Override
    protected void onStop() {
        player.stop();
        if (voiceRequest != null) voiceRequest.cancel();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        player.shutdown();
        super.onDestroy();
    }
}
