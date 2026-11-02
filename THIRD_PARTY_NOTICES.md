# Third-party software

MyTube uses these libraries through Gradle. Original copyright notices remain in the upstream projects and dependency artifacts.

| Component | Version | License / upstream |
| --- | --- | --- |
| NewPipeExtractor | v0.26.5 | GPL-3.0-or-later — https://github.com/TeamNewPipe/NewPipeExtractor |
| AndroidX Activity, Compose, Lifecycle, TV Material | Pinned in app/build.gradle.kts | Apache-2.0 — https://android.googlesource.com/platform/frameworks/support |
| AndroidX Media3 | 1.8.0 | Apache-2.0 — https://github.com/androidx/media |
| Kotlin / kotlinx.coroutines | 2.2.21 / 1.10.2 | Apache-2.0 — https://github.com/JetBrains/kotlin |
| OkHttp / Okio | 4.12.0 / transitive | Apache-2.0 — https://github.com/square/okhttp |
| Coil | 2.7.0 | Apache-2.0 — https://github.com/coil-kt/coil |

Extractor transitive libraries include NewPipe nanojson (MIT), jsoup (MIT), protobuf-javalite (BSD-3-Clause), Rhino (MPL-2.0), and jsr305 (Apache-2.0). The resolved dependency report and Gradle metadata record exact versions. No official YouTube executable, library, image, logo or other copied APK content is used in the application.
