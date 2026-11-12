package de.adrianzimmermann.sorbianonlinespeech;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

public abstract class LocalizedActivity extends Activity {
    private String language;

    @Override
    protected void attachBaseContext(Context base) {
        Context localized = AppLanguage.wrap(base);
        language = AppLanguage.selected(base);
        super.attachBaseContext(localized);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Before Android 13 nothing recreates the activities further down the back stack.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !language.equals(AppLanguage.selected(this))) {
            recreate();
        }
    }
}
