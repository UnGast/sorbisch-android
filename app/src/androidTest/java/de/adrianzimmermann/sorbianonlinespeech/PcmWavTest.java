package de.adrianzimmermann.sorbianonlinespeech;

import static org.junit.Assert.assertEquals;

import android.media.AudioFormat;

import org.junit.Test;

public class PcmWavTest {
    @Test
    public void readsFormatAndSamples() throws Exception {
        byte[] pcm = {0, 0, 1, 0, -1, -1, 2, 0};
        PcmWav.Audio audio = PcmWav.parse(TestUtil.wav(pcm, 16000, 1));

        assertEquals(16000, audio.sampleRate);
        assertEquals(1, audio.channelCount);
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, audio.audioFormat);
        assertEquals(pcm.length, audio.pcm.length);
    }
}
