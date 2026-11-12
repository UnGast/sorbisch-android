package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

final class TestUtil {
    private TestUtil() {
    }

    static <T extends Activity> T launch(Class<T> activity) {
        return launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), activity));
    }

    @SuppressWarnings("unchecked")
    static <T extends Activity> T launch(Intent intent) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        T activity = (T) instrumentation.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        return activity;
    }

    static void waitFor(String what, long timeoutMillis, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!condition.call()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for " + what);
            }
            Thread.sleep(50);
        }
    }

    /** Runs the supplier on the main thread and returns its value. */
    static <T> T onMainThread(Callable<T> supplier) {
        Object[] result = new Object[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                result[0] = supplier.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        @SuppressWarnings("unchecked")
        T value = (T) result[0];
        return value;
    }

    static byte[] wav(byte[] pcm, int sampleRate, int channels) {
        ByteBuffer wav = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length);
        wav.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        wav.putShort((short) 1).putShort((short) channels);
        wav.putInt(sampleRate).putInt(sampleRate * channels * 2);
        wav.putShort((short) (channels * 2)).putShort((short) 16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length);
        wav.put(pcm);
        return wav.array();
    }

    static byte[] silentWav() {
        return wav(new byte[9600], 48000, 1);
    }
}
