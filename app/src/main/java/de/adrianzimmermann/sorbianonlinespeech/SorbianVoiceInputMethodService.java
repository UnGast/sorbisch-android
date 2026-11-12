package de.adrianzimmermann.sorbianonlinespeech;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.text.InputType;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

public class SorbianVoiceInputMethodService extends InputMethodService {
    enum Phase { IDLE, CONNECTING, RECORDING, FINISHING }

    private Context uiContext;
    private String uiLanguage;
    private Spinner languageSpinner;
    private TextView preview;
    private ScrollView previewScroll;
    private ImageButton startButton;
    private ImageButton actionButton;
    private View microphoneProgress;

    private Phase phase = Phase.IDLE;
    private OnlineSpeechSession session;
    private int sessionGeneration;
    private boolean dictationActive;
    private boolean awaitingFinal;
    private boolean privateInput;
    private String recognizedText = "";

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        privateInput = isPrivateInput(info);
        if (!restarting) recognizedText = "";
    }

    /** Password fields and fields that opt out of learning: their text is not kept as a draft. */
    static boolean isPrivateInput(EditorInfo info) {
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                return true;
            }
        } else if (inputClass == InputType.TYPE_CLASS_NUMBER
                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
    }

    @Override
    public View onCreateInputView() {
        uiContext = AppLanguage.wrap(this);
        uiLanguage = AppLanguage.selected(this);
        View view = getLayoutInflater().cloneInContext(uiContext).inflate(R.layout.input_method_voice, null);
        languageSpinner = view.findViewById(R.id.ime_language);
        preview = view.findViewById(R.id.ime_preview);
        previewScroll = view.findViewById(R.id.ime_preview_scroll);
        startButton = view.findViewById(R.id.ime_start);
        microphoneProgress = view.findViewById(R.id.ime_progress);
        actionButton = view.findViewById(R.id.ime_action);
        applyNavigationInsets(view);
        matchNavigationBar();
        actionButton.setOnClickListener(v -> {
            if (session != null || awaitingFinal) return;
            performEditorAction(getCurrentInputConnection(), getCurrentInputEditorInfo());
        });
        updateEditorAction();

        configureLanguageSpinner();
        startButton.setOnClickListener(v -> {
            if (awaitingFinal) return;
            if (session == null) startRecognition();
            else if (phase == Phase.CONNECTING) {
                cancelRecognition();
                showStatus(uiContext.getString(R.string.ime_stopped));
            }
            else stopRecognition();
        });
        View chooseKeyboard = view.findViewById(R.id.ime_choose_keyboard);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            chooseKeyboard.setTooltipText(uiContext.getString(R.string.ime_choose_keyboard));
        }
        chooseKeyboard.setOnClickListener(v -> {
            cancelRecognition();
            InputMethodManager manager = getSystemService(InputMethodManager.class);
            if (manager != null) {
                manager.showInputMethodPicker();
            }
        });
        view.findViewById(R.id.ime_open_settings).setOnClickListener(v -> openAppSettings());
        view.findViewById(R.id.ime_hide).setOnClickListener(v -> requestHideSelf(0));
        setPhase(Phase.IDLE);
        return view;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (uiContext != null && !AppLanguage.selected(this).equals(uiLanguage)) {
            cancelRecognition();
            setInputView(onCreateInputView());
        }
        if (preview != null && !dictationActive && !awaitingFinal) {
            showPreview(recognizedText.isEmpty() ? uiContext.getString(R.string.ime_tap_microphone) : recognizedText);
        }
        updateEditorAction();
    }

    static void applyNavigationInsets(View view) {
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            // The IME insets would be the keyboard itself, we want the bars around it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // InputMethodService has its own Insets class.
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                v.setPadding(left + bars.left, top, right + bars.right, bottom + bars.bottom);
            } else {
                v.setPadding(left + insets.getSystemWindowInsetLeft(), top,
                        right + insets.getSystemWindowInsetRight(), bottom + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        view.requestApplyInsets();
    }

    // No effect on Android 15+, where edge-to-edge is enforced.
    @SuppressWarnings("deprecation")
    private void matchNavigationBar() {
        Window window = getWindow().getWindow();
        if (window == null) return;
        window.setNavigationBarColor(getColor(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? R.color.keyboard_background : R.color.navigation_bar));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    static int editorAction(EditorInfo info) {
        if (info == null || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return EditorInfo.IME_ACTION_NONE;
        }
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        return action == EditorInfo.IME_ACTION_UNSPECIFIED ? EditorInfo.IME_ACTION_NONE : action;
    }

    static void performEditorAction(InputConnection connection, EditorInfo info) {
        if (connection == null || info == null) return;
        int action = editorAction(info);
        if (hasCustomAction(info)) {
            connection.performEditorAction(info.actionId);
        } else if (action != EditorInfo.IME_ACTION_NONE) {
            connection.performEditorAction(action);
        } else {
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
    }

    private static boolean hasCustomAction(EditorInfo info) {
        return info != null && info.actionLabel != null
                && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0;
    }

    private void updateEditorAction() {
        if (actionButton == null) return;
        EditorInfo info = getCurrentInputEditorInfo();
        int action = editorAction(info);
        CharSequence label = hasCustomAction(info) ? info.actionLabel : action == EditorInfo.IME_ACTION_NONE
                ? uiContext.getString(R.string.ime_enter)
                : getTextForImeAction(action);
        if (label == null) label = uiContext.getString(R.string.ime_enter);
        actionButton.setContentDescription(label);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) actionButton.setTooltipText(label);
        // Enabled state first, otherwise the new icon gets the old tint.
        actionButton.setEnabled(info != null && session == null && !dictationActive && !awaitingFinal);
        actionButton.setImageResource(action == EditorInfo.IME_ACTION_SEARCH
                ? R.drawable.ic_search : R.drawable.ic_keyboard_enter);
    }

    @Override
    public void onFinishInput() {
        cancelRecognition();
        super.onFinishInput();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        // A late result must not end up in the next text field.
        cancelRecognition();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onDestroy() {
        cancelRecognition();
        recognizedText = "";
        super.onDestroy();
    }

    private void configureLanguageSpinner() {
        String[] languages = SpeechSettings.availableLanguages();
        // The items are language codes, only the views show the names.
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(uiContext, R.layout.spinner_chip_item, languages) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return label(super.getView(position, convertView, parent), position);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return label(super.getDropDownView(position, convertView, parent), position);
            }
            private View label(View view, int position) {
                ((TextView) view).setText(SpeechSettings.languageLabel(uiContext, getItem(position)));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);
        String savedLanguage = SpeechSettings.language(this);
        for (int i = 0; i < languageSpinner.getCount(); i++) {
            if (savedLanguage.equals(languageSpinner.getItemAtPosition(i))) {
                languageSpinner.setSelection(i);
                break;
            }
        }
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SpeechSettings.saveLanguage(SorbianVoiceInputMethodService.this, selectedLanguage());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void startRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            preview.setText(R.string.ime_microphone_permission_missing);
            return;
        }
        cancelRecognition();
        dictationActive = true;
        recognizedText = "";
        preview.setText(R.string.ime_connecting);
        setPhase(Phase.CONNECTING);
        int generation = ++sessionGeneration;
        session = new OnlineSpeechSession(uiContext,
                SpeechSettings.serverUrl(this, selectedLanguage()), selectedLanguage(), new RecognitionListener() {
                    @Override
                    public void onReady() {
                        // Not recording yet, wait for onSpeechStart().
                    }

                    @Override
                    public void onSpeechStart() {
                        ifCurrent(generation, () -> {
                            if (!dictationActive) return;
                            setPhase(Phase.RECORDING);
                            preview.setText(R.string.ime_listening);
                            startButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        });
                    }

                    @Override
                    public void onResult(RecognitionResult result) {
                        ifCurrent(generation, () -> {
                            recognizedText = result.text;
                            if (!privateInput) {
                                SpeechSettings.saveDictationDraft(SorbianVoiceInputMethodService.this, result.text);
                            }
                            showPreview(result.text);
                            if (!result.partial) {
                                awaitingFinal = false;
                                commitRecognizedText(result.text);
                            }
                        });
                    }

                    @Override
                    public void onNoSpeech() {
                        ifCurrent(generation, () -> {
                            session = null;
                            dictationActive = false;
                            showStatus(uiContext.getString(R.string.status_no_speech));
                            setPhase(Phase.IDLE);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        ifCurrent(generation, () -> {
                            session = null;
                            dictationActive = false;
                            showStatus(uiContext.getString(R.string.status_error, message));
                            setPhase(Phase.IDLE);
                        });
                    }

                    @Override
                    public void onComplete() {
                        ifCurrent(generation, () -> {
                            session = null;
                            dictationActive = false;
                            if (awaitingFinal) {
                                showStatus(uiContext.getString(R.string.ime_stopped));
                            }
                            setPhase(Phase.IDLE);
                        });
                    }
                });
        session.start();
    }

    private void stopRecognition() {
        dictationActive = false;
        OnlineSpeechSession localSession = session;
        if (localSession != null) {
            setPhase(Phase.FINISHING);
            showStatus(uiContext.getString(R.string.ime_finishing));
            preview.setKeepScreenOn(false);
            localSession.stop();
        } else {
            preview.setText(R.string.ime_stopped);
            setPhase(Phase.IDLE);
        }
    }

    private void cancelRecognition() {
        dictationActive = false;
        sessionGeneration++;
        OnlineSpeechSession localSession = session;
        session = null;
        if (localSession != null) {
            localSession.cancel();
        }
        if (startButton != null) {
            setPhase(Phase.IDLE);
        }
    }

    private void commitRecognizedText(String result) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            showStatus(uiContext.getString(R.string.ime_no_input_field));
            return;
        }
        String insertion = insertionText(connection.getTextBeforeCursor(1, 0), result);
        if (!insertion.isEmpty()) {
            if (connection.commitText(insertion, 1)) {
                showPreview(uiContext.getString(R.string.ime_inserted, result.trim()));
            } else {
                showStatus(uiContext.getString(R.string.ime_insert_failed));
            }
        }
    }

    /** Pads the text with spaces so that it does not stick to the surrounding words. */
    static String insertionText(CharSequence beforeCursor, String recognized) {
        String text = recognized == null ? "" : recognized.trim();
        if (text.isEmpty()) {
            return "";
        }
        boolean needsLeadingSpace = beforeCursor != null && beforeCursor.length() > 0
                && !Character.isWhitespace(beforeCursor.charAt(beforeCursor.length() - 1));
        return (needsLeadingSpace ? " " : "") + text + " ";
    }

    private void setPhase(Phase phase) {
        this.phase = phase;
        preview.setKeepScreenOn(phase == Phase.CONNECTING || phase == Phase.RECORDING);
        awaitingFinal = phase == Phase.FINISHING;
        renderMicrophone(startButton, microphoneProgress, phase);
        languageSpinner.setEnabled(phase == Phase.IDLE);
        languageSpinner.setAlpha(phase == Phase.IDLE ? 1f : 0.5f);
        updateEditorAction();
    }

    static void renderMicrophone(ImageButton button, View progress, Phase phase) {
        boolean recording = phase == Phase.RECORDING;
        boolean waiting = phase == Phase.CONNECTING || phase == Phase.FINISHING;
        // bg_microphone and microphone_icon_tint pick the colors from these view states.
        button.setEnabled(phase != Phase.FINISHING);
        button.setActivated(recording);
        button.setSelected(phase == Phase.CONNECTING);
        button.setImageResource(recording ? R.drawable.ic_stop : phase == Phase.CONNECTING
                ? R.drawable.ic_close : R.drawable.ic_mic);
        int label = phase == Phase.IDLE ? R.string.ime_start_recognition
                : phase == Phase.CONNECTING ? R.string.ime_cancel_connecting
                : phase == Phase.RECORDING ? R.string.ime_stop_recognition : R.string.ime_finishing;
        button.setContentDescription(button.getContext().getString(label));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) button.setTooltipText(button.getContentDescription());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) button.setStateDescription(button.getContext().getString(
                recording ? R.string.ime_listening : phase == Phase.CONNECTING ? R.string.ime_connecting
                        : phase == Phase.FINISHING ? R.string.ime_finishing : R.string.ime_tap_microphone));
        progress.setVisibility(waiting ? View.VISIBLE : View.GONE);
    }

    private void showStatus(String message) {
        showPreview(recognizedText.isEmpty() ? message : recognizedText + "\n" + message);
    }

    private void showPreview(String text) {
        preview.setText(text);
        previewScroll.post(() -> previewScroll.fullScroll(View.FOCUS_DOWN));
    }

    private String selectedLanguage() {
        Object selected = languageSpinner == null ? null : languageSpinner.getSelectedItem();
        return selected == null ? SpeechSettings.DEFAULT_LANGUAGE : selected.toString();
    }

    // Callbacks of a cancelled session can still be queued on the main thread.
    private void ifCurrent(int generation, Runnable action) {
        if (generation == sessionGeneration) {
            action.run();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
