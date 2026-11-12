package de.adrianzimmermann.sorbianonlinespeech;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class RecognitionDiagnostics {
    private static long armedUntil;
    private static final String PREFS = "recognition_diagnostics";

    private RecognitionDiagnostics() {
    }

    // Consent covers one session and lapses after ten minutes. It is kept in memory only.
    static synchronized void arm() {
        armedUntil = SystemClock.elapsedRealtime() + 10 * 60 * 1000;
    }

    static synchronized void disarm() {
        armedUntil = 0;
    }

    static synchronized boolean isArmed() {
        return armedUntil > SystemClock.elapsedRealtime();
    }

    static synchronized boolean consumeRequest() {
        boolean requested = isArmed();
        disarm();
        return requested;
    }

    static synchronized void record(Context context, String serverUrl, String language, int sampleRate) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        // Host only: the URL may contain credentials or tokens.
        String host = Uri.parse(serverUrl).getHost();
        String entry = format.format(new Date()) + " | " + host + " | " + language + " | " + sampleRate + " Hz\n";
        String[] previous = entries(context).split("\n");
        StringBuilder list = new StringBuilder(entry);
        for (int i = 0; i < Math.min(previous.length, 49); i++) {
            if (!previous[i].isEmpty()) list.append(previous[i]).append('\n');
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("sessions", list.toString()).apply();
    }

    static String entries(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sessions", "");
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("sessions").apply();
    }

    static void show(Activity activity) {
        String[] actions = {
                activity.getString(isArmed() ? R.string.diagnostics_disarm : R.string.diagnostics_arm),
                activity.getString(R.string.diagnostics_email),
                activity.getString(R.string.diagnostics_clear)
        };
        new AlertDialog.Builder(activity).setTitle(R.string.diagnostics_title)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        if (isArmed()) {
                            disarm();
                            show(activity);
                        } else {
                            new AlertDialog.Builder(activity).setTitle(R.string.diagnostics_arm)
                                    .setMessage(R.string.diagnostics_consent)
                                    .setPositiveButton(R.string.diagnostics_enable, (confirmation, button) -> {
                                        arm();
                                        Toast.makeText(activity, R.string.diagnostics_armed, Toast.LENGTH_LONG).show();
                                    }).setNegativeButton(android.R.string.cancel, null).show();
                        }
                    } else if (which == 1) {
                        showReport(activity);
                    } else {
                        new AlertDialog.Builder(activity).setMessage(R.string.diagnostics_clear_confirm)
                                .setPositiveButton(R.string.diagnostics_clear,
                                        (confirmation, button) -> clear(activity))
                                .setNegativeButton(android.R.string.cancel, null).show();
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void showReport(Activity activity) {
        String entries = entries(activity);
        if (entries.isEmpty()) {
            Toast.makeText(activity, R.string.diagnostics_empty, Toast.LENGTH_LONG).show();
            return;
        }
        String report = activity.getString(R.string.diagnostics_report, BuildConfig.VERSION_NAME) + "\n\n" + entries;
        new AlertDialog.Builder(activity).setTitle(R.string.diagnostics_title).setMessage(report)
                .setPositiveButton(R.string.diagnostics_email, (dialog, which) -> {
                    Uri uri = Uri.parse("mailto:?subject=" + Uri.encode("Diktuj diagnostics")
                            + "&body=" + Uri.encode(report));
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_SENDTO, uri));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(activity, R.string.diagnostics_no_mail, Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
