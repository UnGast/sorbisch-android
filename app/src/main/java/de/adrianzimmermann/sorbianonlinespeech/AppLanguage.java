package de.adrianzimmermann.sorbianonlinespeech;

import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Arrays;

final class AppLanguage {
    static final String[] TAGS = {"", "de", "en", "hsb"};
    private static final String PREFERENCES = "app_language";
    private static final String KEY = "language";

    private AppLanguage() {
    }

    static String selected(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleList locales = context.getSystemService(LocaleManager.class).getApplicationLocales();
            return locales.isEmpty() ? "" : locales.get(0).getLanguage();
        }
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, "");
    }

    static void set(Context context, String language) {
        if (!Arrays.asList(TAGS).contains(language)) {
            throw new IllegalArgumentException("Unsupported app language");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager.class)
                    .setApplicationLocales(LocaleList.forLanguageTags(language));
        } else {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .edit().putString(KEY, language).apply();
        }
    }

    static Context wrap(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 stores the app language itself. Hand over what we saved before the upgrade.
            SharedPreferences old = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            if (old.contains(KEY)) {
                if (selected(context).isEmpty()) set(context, old.getString(KEY, ""));
                old.edit().remove(KEY).apply();
            }
            return context;
        }
        return localizedContext(context, selected(context));
    }

    static Context localizedContext(Context context, String language) {
        if (language.isEmpty()) return context;
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocales(LocaleList.forLanguageTags(language));
        return context.createConfigurationContext(configuration);
    }
}
