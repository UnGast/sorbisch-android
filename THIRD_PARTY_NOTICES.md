# Third-Party Notices

The MIT license covers original project code, not the components below.
Their original licenses and copyright notices remain applicable.

| Component | Use | License |
| --- | --- | --- |
| [docker_vosk](https://github.com/ZalozbaDev/docker_vosk) | Remote recognition service; not bundled in the app | [MIT](third_party/docker_vosk/LICENSE) |
| [bamborak](https://github.com/Korla-tech/bamborak) | Remote speech synthesis service; not bundled in the app | [AGPL-3.0](third_party/bamborak/LICENSE) |
| [OkHttp 4.12.0](https://github.com/square/okhttp/tree/parent-4.12.0), Copyright Square, Inc. | HTTP and WebSocket client | [Apache-2.0](third_party/okhttp/LICENSE) |
| [Okio 3.6.0](https://github.com/square/okio/tree/parent-3.6.0), Copyright Square, Inc. | Transitive I/O dependency | [Apache-2.0](third_party/okio/LICENSE) |
| [Kotlin runtime 1.9.10](https://github.com/JetBrains/kotlin/tree/v1.9.10), Copyright JetBrains and contributors | Transitive runtime dependency | [Apache-2.0](third_party/kotlin/LICENSE), [upstream notice](third_party/kotlin/NOTICE) |
| [JetBrains annotations 13.0](https://github.com/JetBrains/java-annotations), Copyright JetBrains | Transitive annotations dependency | [Apache-2.0](third_party/jetbrains-annotations/LICENSE) |
| [Material Icons](https://github.com/google/material-design-icons), Copyright Google | Keyboard, keyboard-return, mic, search, settings, close, expand-more, arrow-back, chevron-right and arrow-drop-down icons, converted to Android VectorDrawables and tinted | [Apache-2.0](third_party/material-icons/LICENSE) |
| [Inter](https://github.com/google/fonts/tree/main/ofl/inter), Copyright The Inter Project Authors | Outlined Diktuj wordmark; no font download at runtime | [SIL OFL-1.1](third_party/inter/OFL.txt) |
| [Gradle 8.14](https://github.com/gradle/gradle/tree/v8.14.0), Copyright original authors | Wrapper JAR and generated launch scripts | [Apache-2.0](third_party/gradle/LICENSE) |

The APK includes these notices and license texts in `assets/licenses/`.
Build tools and test-only dependencies (Android Gradle Plugin, AndroidX Test,
JUnit and MockWebServer) are not included in the end-user APK. They retain their
upstream licenses. Referenced recognition/TTS projects and remotely hosted
models are not bundled or relicensed by this project.
