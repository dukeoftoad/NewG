# Privacy and network audit

## Runtime privacy

The Android app does not integrate Firebase, Google Analytics, Firebase Crashlytics, AdMob, Google Sign-In, Google Advertising ID, or Google Play Services location. No analytics or advertising SDK is declared in the app dependency graph, and the app manifest does not request the advertising ID permission.

Face detection, face embeddings, and People grouping run on-device. Face metadata and embeddings are stored in the app's local Room database. Gallery photos and face embeddings are not uploaded by these features.

## Network access

The app requests Android's `INTERNET` permission for explicit model downloads:

- Ente FlutterFace model files are fetched from the pinned `raw.githubusercontent.com` source revision in `FaceModelManager`.
- The optional Depth Anything V2 model is fetched from Hugging Face in `DepthModelManager`.

A model host will necessarily receive ordinary connection metadata such as the device's IP address and the HTTP request headers when a download is requested. These downloads are not Google analytics or ad-tracking requests. The app does not send gallery content or face embeddings as part of either download.

## Build-only Google infrastructure retained

Gradle's `google()` repository is retained because Android Gradle Plugin and AndroidX artifacts are distributed through Google's Maven repository. The KSP plugin is retained because Room uses it for compile-time database code generation. These are build-time tools/repositories, not runtime Google tracking SDKs.

## Audit scope and limits

This audit covers the repository's Android manifest, Gradle plugin/dependency declarations, and application source references for Firebase, Google services, analytics, advertising, and network clients. It does not inspect behavior of the Android operating system, Google Play Store, a device manufacturer's services, or other apps installed on the device. It also cannot erase data already held by a third-party service from prior app versions or prior user activity.
