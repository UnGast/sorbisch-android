package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.adrianzimmermann.sorbianonlinespeech.SorbianVoiceInputMethodService.Phase;

public class KeyboardTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static void onMainThread(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static void measure(View keyboard, int width) {
        keyboard.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    }

    private static Rect bounds(ViewGroup root, View child) {
        Rect rect = new Rect();
        child.getDrawingRect(rect);
        root.offsetDescendantRectToMyCoords(child, rect);
        return rect;
    }

    @Test
    public void progressIndicatorSitsBelowThePreviewNextToTheMicrophone() {
        float density = context.getResources().getDisplayMetrics().density;
        onMainThread(() -> {
            for (int widthDp : new int[]{320, 640}) {
                int width = (int) (widthDp * density);
                ViewGroup keyboard = (ViewGroup) LayoutInflater.from(context)
                        .inflate(R.layout.input_method_voice, null);
                ImageButton mic = keyboard.findViewById(R.id.ime_start);
                View progress = keyboard.findViewById(R.id.ime_progress);

                SorbianVoiceInputMethodService.renderMicrophone(mic, progress, Phase.CONNECTING);
                measure(keyboard, width);
                keyboard.layout(0, 0, keyboard.getMeasuredWidth(), keyboard.getMeasuredHeight());
                Rect indicator = bounds(keyboard, progress);
                Rect button = bounds(keyboard, mic);
                Rect preview = bounds(keyboard, keyboard.findViewById(R.id.ime_preview_scroll));
                assertTrue(indicator.top > preview.bottom);
                assertFalse(Rect.intersects(indicator, button));
                assertEquals(button.centerY(), indicator.centerY(), 1);

                // Hiding the indicator must not change the keyboard height.
                int height = keyboard.getMeasuredHeight();
                SorbianVoiceInputMethodService.renderMicrophone(mic, progress, Phase.IDLE);
                measure(keyboard, width);
                assertEquals(height, keyboard.getMeasuredHeight());
            }
        });
    }

    @Test
    public void microphoneOnlyShowsRecordingAfterCaptureStarts() {
        onMainThread(() -> {
            View keyboard = LayoutInflater.from(context).inflate(R.layout.input_method_voice, null);
            ImageButton mic = keyboard.findViewById(R.id.ime_start);
            View progress = keyboard.findViewById(R.id.ime_progress);
            for (Phase phase : Phase.values()) {
                SorbianVoiceInputMethodService.renderMicrophone(mic, progress, phase);
                boolean waiting = phase == Phase.CONNECTING || phase == Phase.FINISHING;
                assertEquals(phase == Phase.RECORDING, mic.isActivated());
                assertEquals(phase != Phase.FINISHING, mic.isEnabled());
                assertEquals(waiting ? View.VISIBLE : View.GONE, progress.getVisibility());
            }
            SorbianVoiceInputMethodService.renderMicrophone(mic, progress, Phase.IDLE);
            assertTrue(mic.isEnabled());
            assertFalse(mic.isActivated());
            assertEquals(context.getString(R.string.ime_start_recognition), mic.getContentDescription());
        });
    }

    @Test
    public void sendsSearchAndCustomActionsButEnterForMultiline() {
        List<Integer> actions = new ArrayList<>();
        List<Integer> keys = new ArrayList<>();
        BaseInputConnection connection = new BaseInputConnection(new View(context), true) {
            @Override
            public boolean performEditorAction(int action) {
                actions.add(action);
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                assertEquals(KeyEvent.KEYCODE_ENTER, event.getKeyCode());
                keys.add(event.getAction());
                return true;
            }
        };
        EditorInfo info = new EditorInfo();

        info.imeOptions = EditorInfo.IME_ACTION_SEARCH;
        SorbianVoiceInputMethodService.performEditorAction(connection, info);
        assertEquals(Integer.valueOf(EditorInfo.IME_ACTION_SEARCH), actions.get(0));

        info.actionLabel = "Custom";
        info.actionId = 1;
        SorbianVoiceInputMethodService.performEditorAction(connection, info);
        assertEquals(Integer.valueOf(1), actions.get(1));

        info.imeOptions |= EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        SorbianVoiceInputMethodService.performEditorAction(connection, info);
        assertEquals(2, actions.size());
        assertEquals(Arrays.asList(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), keys);
    }

    @Test
    public void insertedTextIsSeparatedFromSurroundingWords() {
        assertEquals("dobry dzen ", SorbianVoiceInputMethodService.insertionText(null, " dobry dzen "));
        assertEquals(" dobry dzen ", SorbianVoiceInputMethodService.insertionText("A", "dobry dzen"));
        assertEquals("dobry dzen ", SorbianVoiceInputMethodService.insertionText(" ", "dobry dzen"));
        assertEquals("", SorbianVoiceInputMethodService.insertionText("A", "  "));
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    public void navigationInsetsDoNotAccumulateOrIncludeKeyboardHeight() {
        onMainThread(() -> {
            View view = new View(context);
            view.setPadding(8, 8, 8, 8);
            SorbianVoiceInputMethodService.applyNavigationInsets(view);
            WindowInsets insets = new WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                    .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 300)).build();
            view.dispatchApplyWindowInsets(insets);
            view.dispatchApplyWindowInsets(insets);
            assertEquals(56, view.getPaddingBottom());

            view.dispatchApplyWindowInsets(new WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 24, 0)).build());
            assertEquals(8, view.getPaddingBottom());
            assertEquals(32, view.getPaddingRight());
        });
    }
}
