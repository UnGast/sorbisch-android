package de.adrianzimmermann.sorbianonlinespeech;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

final class LanguageSpinner {
    interface Listener {
        void onLanguageChanged(String language);
    }

    private LanguageSpinner() {
    }

    /** Shows language names for the given codes and reports when the user picks another one. */
    static void bind(Spinner spinner, String[] languages, String selected, Listener listener) {
        String[] labels = new String[languages.length];
        int selection = 0;
        for (int i = 0; i < languages.length; i++) {
            labels[i] = SpeechSettings.languageLabel(spinner.getContext(), languages[i]);
            if (languages[i].equals(selected)) selection = i;
        }
        String initial = languages[selection];
        spinner.setOnItemSelectedListener(null);
        spinner.setAdapter(new ArrayAdapter<>(spinner.getContext(),
                android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(selection);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private String current = initial;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Spinners also fire this for the initial selection.
                if (languages[position].equals(current)) return;
                current = languages[position];
                listener.onLanguageChanged(current);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
}
