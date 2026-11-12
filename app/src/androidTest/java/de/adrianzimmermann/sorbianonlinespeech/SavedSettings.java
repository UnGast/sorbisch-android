package de.adrianzimmermann.sorbianonlinespeech;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.ExternalResource;

import java.util.HashMap;
import java.util.Map;

/** Puts the app's preferences back the way they were before the test. */
public class SavedSettings extends ExternalResource {
    private static final String[] FILES = {"online_speech", "dictation", "recognition_diagnostics"};

    private final Map<String, Map<String, ?>> saved = new HashMap<>();

    @Override
    protected void before() {
        for (String file : FILES) {
            saved.put(file, new HashMap<>(preferences(file).getAll()));
        }
    }

    @Override
    protected void after() {
        RecognitionDiagnostics.disarm();
        for (String file : FILES) {
            SharedPreferences.Editor editor = preferences(file).edit().clear();
            for (Map.Entry<String, ?> entry : saved.get(file).entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    editor.putString(entry.getKey(), (String) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(entry.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(entry.getKey(), (Integer) value);
                } else {
                    throw new IllegalStateException("Unexpected preference type: " + entry.getKey());
                }
            }
            editor.commit();
        }
    }

    private static SharedPreferences preferences(String file) {
        return InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences(file, Context.MODE_PRIVATE);
    }
}
