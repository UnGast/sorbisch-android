# Recognition test client

A second, minimal app that calls Diktuj's recognition service through Android's
`SpeechRecognizer`, the way any other app would. It is only for testing.

```bash
./gradlew :recognition-client:assembleDebug
adb install -r recognition-client/build/outputs/apk/debug/recognition-client-debug.apk
adb shell am start -n de.adrianzimmermann.speechclient/.MainActivity
```

Diktuj has to be installed and set up as well. **Use Diktuj service** addresses
the service directly, **Use Android default** uses whatever recognizer the
system has selected.

To make Diktuj the system default on an emulator (note the old value first with
`settings get` and put it back afterwards):

```bash
adb shell settings put secure voice_recognition_service \
  de.serbskilud.diktuj/de.adrianzimmermann.sorbianonlinespeech.OnlineRecognitionService
```
