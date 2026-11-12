package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.speech.tts.TextToSpeech;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

public class ManifestTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void keyboardIsProtectedAndRegistered() throws Exception {
        ComponentName component = new ComponentName(context, SorbianVoiceInputMethodService.class);
        ServiceInfo info = context.getPackageManager().getServiceInfo(component,
                PackageManager.GET_META_DATA);

        assertTrue(info.exported);
        assertEquals("android.permission.BIND_INPUT_METHOD", info.permission);
        assertEquals(R.xml.input_method, info.metaData.getInt("android.view.im"));
    }

    @Test
    public void ttsDataCheckActivityIsRegistered() {
        Intent check = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
                .setPackage(context.getPackageName());

        assertEquals(TtsDataActivity.class.getName(),
                context.getPackageManager().resolveActivity(check, 0).activityInfo.name);
    }

    @Test
    public void selectedTextIntentResolvesToMainActivity() {
        Intent intent = new Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "Dobry dźeń");
        boolean registered = false;
        for (ResolveInfo candidate : context.getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY)) {
            if (context.getPackageName().equals(candidate.activityInfo.packageName)
                    && MainActivity.class.getName().equals(candidate.activityInfo.name)) {
                registered = true;
                break;
            }
        }

        assertTrue(registered);
        assertTrue(MainActivity.isProcessTextIntent(intent));
        assertEquals("Dobry dźeń", MainActivity.selectedText(intent));
    }
}
