package de.adrianzimmermann.sorbianonlinespeech;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

final class ReadAloudSettings {
    private static final String SUITE = "com.google.android.marvin.talkback";
    private static final String SERVICE = "com.google.android.accessibility.selecttospeak.SelectToSpeakService";

    private ReadAloudSettings() {
    }

    static ComponentName installedService(Context context) {
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        for (AccessibilityServiceInfo info : manager.getInstalledAccessibilityServiceList()) {
            ServiceInfo service = info.getResolveInfo().serviceInfo;
            if (SUITE.equals(service.packageName) && SERVICE.equals(service.name)) {
                return new ComponentName(service.packageName, service.name);
            }
        }
        return null;
    }

    static boolean enabled(Context context) {
        ComponentName component = installedService(context);
        if (component == null) return false;
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (component.equals(ComponentName.unflattenFromString(info.getId()))) return true;
        }
        return false;
    }

    static void open(Activity activity) {
        ComponentName component = installedService(activity);
        if (component != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // Stock Android only lets system apps open this page.
                    activity.startActivity(new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                            .putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString()));
                    return;
                } catch (ActivityNotFoundException | SecurityException unavailable) {
                    // fall through to the dialog
                }
            }
            new AlertDialog.Builder(activity).setTitle(R.string.setup_read_aloud)
                    .setMessage(R.string.setup_read_aloud_steps)
                    .setPositiveButton(R.string.setup_accessibility_settings, (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        // Undocumented: makes the Settings app scroll to and highlight the entry.
                        String key = ":settings:fragment_args_key";
                        Bundle arguments = new Bundle();
                        arguments.putString(key, component.flattenToString());
                        intent.putExtra(key, component.flattenToString());
                        intent.putExtra(":settings:show_fragment_args", arguments);
                        try {
                            activity.startActivity(intent);
                        } catch (ActivityNotFoundException missing) {
                            HomeActivity.openSettings(activity, Settings.ACTION_SETTINGS);
                        }
                    }).setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        new AlertDialog.Builder(activity).setTitle(R.string.setup_read_aloud)
                .setMessage(R.string.setup_read_aloud_missing)
                .setPositiveButton(R.string.setup_install_suite, (dialog, which) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=" + SUITE)));
                    } catch (ActivityNotFoundException unavailable) {
                        HomeActivity.openSettings(activity, Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    }
                })
                .setNeutralButton(R.string.setup_accessibility_settings, (dialog, which) ->
                        HomeActivity.openSettings(activity, Settings.ACTION_ACCESSIBILITY_SETTINGS))
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
