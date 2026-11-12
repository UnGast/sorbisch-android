package de.adrianzimmermann.sorbianonlinespeech;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.Arrays;

public class TtsDataActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String action = getIntent().getAction();
        Intent result = new Intent();
        if (TextToSpeech.Engine.ACTION_CHECK_TTS_DATA.equals(action)) {
            ArrayList<String> available = new ArrayList<>(Arrays.asList("deu-DEU", "eng-USA"));
            for (String language : SpeechSettings.availableTtsLanguages()) available.add(language + "-DEU");
            result.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available);
            result.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES,
                    new ArrayList<>());
            setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result);
        } else if (TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT.equals(action)) {
            result.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT,
                    SpeechSettings.ttsSampleText(this, SpeechSettings.ttsLanguage(this)));
            setResult(TextToSpeech.LANG_AVAILABLE, result);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }
}
