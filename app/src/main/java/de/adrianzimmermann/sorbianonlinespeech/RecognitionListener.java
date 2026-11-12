package de.adrianzimmermann.sorbianonlinespeech;

interface RecognitionListener {
    /** Connected to the server. */
    void onReady();

    /** Recording has started. */
    void onSpeechStart();

    /** A partial or final transcript, see {@link RecognitionResult#partial}. */
    void onResult(RecognitionResult result);

    void onNoSpeech();

    void onError(String message);

    /** The session ended without an error. */
    void onComplete();
}
