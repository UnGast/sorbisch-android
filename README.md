# Diktuj

Sorbian dictation and read-aloud for Android 7.0 and newer. Diktuj installs as

- a voice keyboard,
- a speech recognition service that other apps can use, and
- a text-to-speech engine for Android.

Recognition and synthesis run on a server, so the app needs a network
connection. The UI is available in German and English; the Upper Sorbian
translation is not complete yet.

## Install

Release builds are on
[Google Play](https://play.google.com/store/apps/details?id=de.serbskilud.diktuj).

Debug builds are attached to every run of the
[Debug APK workflow](.github/workflows/debug-apk.yml): open **Actions > Debug APK**,
pick a successful run, download the `diktuj-debug` artifact and install the
`app-debug.apk` inside it.

Every CI run signs with a fresh debug key. If Android refuses to update an
installed build, uninstall it first (this also deletes its settings). There is no
release signing key in this repository.

## Using the app

On first start a setup assistant asks whether you want dictation, read-aloud or
both, and walks through the steps for each. It can be reopened from the home
screen with **Diktuj-Einrichtung erneut starten**.

### Dictation

Enable the Diktuj keyboard in Android's keyboard settings and switch to it in any
text field. Tap the microphone, wait until it turns into a red stop square, speak,
and tap again to finish. While the app is connecting, the button shows an X that
cancels.

- The key next to the microphone sends the field's action (search, send, ...) or
  inserts a line break.
- The keyboard icon switches back to your normal keyboard.
- The last dictated text is kept on the device and can be copied or deleted
  under **Letztes Diktat**. Text from password fields is not kept.
- Hiding the keyboard stops the recording. Audio is not stored on the device, so
  anything the server had not transcribed by then is lost.

Some Google apps always use Google's own recognizer, whatever is selected as the
system recognition service.

### Reading aloud

Select Diktuj as the preferred engine in Android's text-to-speech settings. To
have text on the screen read out, also enable **Select to Speak** in the
accessibility settings; the app links to it and offers to install the Android
Accessibility Suite if it is missing. How the Select to Speak shortcut looks
depends on the device.

The voice is chosen on the home screen. The list comes from the TTS server.

### Privacy

Audio and text are sent to the configured servers. The default servers are run
by the Stiftung für das sorbische Volk; they do not keep audio or text, and
their logs are deleted after 24 hours. Whoever changes the server addresses
sends their data to a different operator. App backups are disabled.

**Datenschutz**, **Impressum** and **Open-Source-Lizenzen** on the home screen
open pages bundled with the app (sources in `app/src/main/assets/information/`).
The privacy page covers the app itself and links to the foundation's privacy
policy for everything else.

## Configuration

Server addresses and defaults are compiled in from
[server-config.properties](server-config.properties):

```properties
configVersion=2
recognizerUrl=wss://diktuj.serbskidom.de/
recognizerSampleRate=48000
continuousRecognition=true
recognitionLanguages=hsb
ttsUrl=https://bamborakapi.mudrowak.de/api/tts/
ttsProtocol=bamborak
ttsSpeakerId=weronika
```

Users can change these under **Erweiterte Einstellungen**. Only values that
differ from the defaults are stored, so users who never changed a setting get
new defaults with the next update. **Server-Standardwerte wiederherstellen**
drops all overrides.

### Changing a default

1. Edit `server-config.properties`.
2. Increase `configVersion`.
3. If installed versions stored the old default explicitly, add a step to
   [ConfigurationMigration](app/src/main/java/de/adrianzimmermann/sorbianonlinespeech/ConfigurationMigration.java)
   that removes it. Leave the old values in existing steps alone.

A user who typed in exactly the old default is migrated as well; the two cases
cannot be told apart.

### Lower Sorbian

Lower Sorbian has its own recognizer URL and TTS settings and stays hidden until
they are filled in:

```properties
recognitionLanguages=hsb,dsb
dsbRecognizerUrl=wss://dsb-recognizer.example/
ttsLanguages=hsb,dsb
dsbTtsUrl=https://bamborak.example/api/tts/
dsbTtsProtocol=bamborak
dsbTtsSpeakerId=some-dsb-speaker
```

The TTS URL may be the same Bamborak instance as for Upper Sorbian, as long as
`fetch_speakers/` labels the voices with `dsb`. Dictation language and read-aloud
language are selected separately. Sample rate and continuous mode apply to both
languages. The Lower Sorbian voice sample is a placeholder ("Diktuj.") until
someone supplies a proper sentence.

## Server protocols

### Recognition

A WebSocket in the style of the Vosk server. The app appends `?lang=` to the
URL and then sends

1. `{"config": {"sample_rate": 48000, "language": "hsb"}}`
2. `{"config": {"audio_log": false}}`
3. binary frames of 16-bit mono PCM
4. `{"eof": 1}` when the user stops

and reads `partial` and `text` results. Supported sample rates are 16 and
48 kHz; the Mudrowak server needs 48. A message that contains both `recognizer`
and `model` is treated as the server's greeting and ignored. With **Bis Stopp
aufnehmen** the app collects final results until the user stops; otherwise the
first final result ends the dictation.

`audio_log` is an extension of the [docker_vosk](https://github.com/ZalozbaDev/docker_vosk)
server. It goes into a message of its own because that server reads only one
setting per config message.

### Diagnostic recordings

Under **Erweiterte Einstellungen > Expertenoptionen: Diagnose** a user can agree
to have the next dictation recorded on the server (`audio_log: true`). The
consent is valid for one session and expires after ten minutes. The app keeps a
list of the last 50 such sessions (time, host, language, sample rate) which the
user can mail to whoever is debugging. The server does not confirm that it
stored anything, and clearing the list does not delete recordings there.

### Text-to-speech

`ttsProtocol=bamborak` posts `{"text", "speaker_id", "format": "wav"}` and
expects WAV data back. The voice list is loaded from `fetch_speakers/` next to
the TTS endpoint. `ttsProtocol=simple` posts `{"text", "language"}` instead.
There is no support for authentication.

Use `wss://` and `https://` outside of local development.

## Code

| | |
|---|---|
| [SorbianVoiceInputMethodService](app/src/main/java/de/adrianzimmermann/sorbianonlinespeech/SorbianVoiceInputMethodService.java) | the keyboard |
| [OnlineRecognitionService](app/src/main/java/de/adrianzimmermann/sorbianonlinespeech/OnlineRecognitionService.java) | Android `RecognitionService` for other apps |
| [SorbianTtsService](app/src/main/java/de/adrianzimmermann/sorbianonlinespeech/SorbianTtsService.java) | Android `TextToSpeechService` |
| [OnlineSpeechSession](app/src/main/java/de/adrianzimmermann/sorbianonlinespeech/OnlineSpeechSession.java) | microphone and WebSocket, shared by keyboard, service and app |
| [SpeechSettings](app/src/main/java/de/adrianzimmermann/sorbianonlinespeech/SpeechSettings.java) | preferences and packaged defaults |
| [ExternalTtsClient](app/src/main/java/de/adrianzimmermann/sorbianonlinespeech/ExternalTtsClient.java) | TTS requests |

[Kõnele](https://github.com/Kaljurand/K6nele) and
[vosk-android-service](https://github.com/alphacep/vosk-android-service) were
useful references for the Android side.

## Development

JDK 17, Android SDK platforms 34 and 36, build-tools 35.0.0 and `ANDROID_HOME`,
or simply Android Studio.

```sh
./gradlew :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

[scripts/android-dev.sh](scripts/android-dev.sh) wraps the usual steps on macOS
and Linux (run it without arguments for the full list):

```sh
./scripts/android-dev.sh avds           # list virtual devices
./scripts/android-dev.sh emulator NAME  # start one
./scripts/android-dev.sh run            # build, install, open
./scripts/android-dev.sh test           # tests and lint
./scripts/android-dev.sh test-tts       # Bamborak tests only
./scripts/android-dev.sh logs
```

Set `ANDROID_SERIAL` if more than one device is connected.

The tests are instrumentation tests and need a device or emulator
(`./gradlew :app:connectedDebugAndroidTest`). They run against scripted servers
and never contact the real services. CI builds and lints but does not run them.
The emulator's microphone forwarding is unreliable on macOS, so check real audio
on a phone.

[recognition-client](recognition-client/README.md) is a second small app for
testing the recognition service from outside.

## License

[MIT](LICENSE). Third-party components keep their own licenses, see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The license covers neither the
speech models nor access to the hosted services.
