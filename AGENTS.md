# AGENTS.md

Instructions for agents and for anyone editing this repository. Every version and constraint below was checked against `build.gradle`, `app/build.gradle`, `gradle.properties` and `app/src/main/AndroidManifest.xml`. When the build changes, this file and `README.md` are updated in the same batch.

## What this is

**RoboCam** (`ru.proghouse.robocam`) is an Android app that turns a phone or tablet into a camera and control server for a robot (LEGO Mindstorms EV3 or a custom build). Inside there is an HTTP + WebSocket server on Webbit that serves the HTML5 client from `app/src/main/assets/web/` and streams camera frames to browsers.

- One module, `:app`, Java only (the project has no Kotlin).
- The web client lives in a separate repository, RoboCam-HTML5-Client; `assets/web/` holds a built copy of it (`def` is the default, `ru`/`kk` override it).
- No product flavors: one APK.

## Stack and versions

| Item | Value |
| --- | --- |
| Gradle | 9.6.0 (wrapper) |
| Android Gradle Plugin | 9.4.0 |
| JDK | 21, path set in `gradle.properties` (`org.gradle.java.home`) |
| compileSdk / targetSdk | 36 / 36 |
| minSdk | 19 (Android 4.4) |
| Version | 1.5.1, versionCode 43 |
| Dependencies | AndroidX AppCompat 1.6.1, AndroidX Multidex 2.0.1, Google Mobile Ads 22.6.0, Webbit 0.4.15 |

Settings in `gradle.properties` that cannot be dropped without breaking something:

- `android.useAndroidX=true` — neither AppCompat nor the ads SDK builds without AndroidX.
- `android.nonFinalResIds=false` — the code has `switch` statements over `R.id`, which need compile-time constants.
- `android.enableR8.fullMode=false` — 2016-era ProGuard rules and old libraries; full mode strips too much.
- `org.gradle.jvmargs=… -Dfile.encoding=UTF-8` — sources and comments are in Cyrillic.

## Build

```bat
gradlew.bat assembleDebug
gradlew.bat assembleRelease
gradlew.bat testDebugUnitTest
gradlew.bat lint
```

- If the user's home directory contains non-ASCII characters (Cyrillic), point `GRADLE_USER_HOME` at an ASCII path: the AIDL generator writes that directory name into the header of the generated file and `javac` fails on the encoding.
- `buildFeatures { aidl true }` is required by `app/src/main/aidl/com/android/vending/billing/IInAppBillingService.aidl` (legacy in-app billing v3).
- Release is minified by R8 with `proguard-android-optimize.txt`, but `app/proguard-rules.pro` contains `-dontoptimize`: historical releases were built with `proguard-android.txt`, which does not optimize, and that behavior is kept. The release APK is **unsigned** — signing is a separate step.
- `lint { abortOnError false; checkReleaseBuilds false }` — lint does not fail the build, so watch `NewApi` by hand: minSdk 19 with targetSdk 36 means many APIs need `SDK_INT` guards.

## Code map

- `MainActivity` — camera preview (`SurfaceHolder.Callback`, `TextureView`), starts the server, initializes ads.
- `HttpServer` (`IntentService`) — HTTP and WebSocket on Webbit: serves `assets/web/<locale>`, returns settings, accepts commands, caches content for several clients.
- `CameraManager` — camera access (Camera API, Camera2 on newer versions) and frame preparation.
- `RoboCamBroker` — shared state: settings, the current driver, the latest frame.
- `PreviewSize`, `CompareSizesByArea`, `RgbData`, `StreamHelper` — preview size selection and frame processing.
- `drivers/` — `RoboCamDriver` / `RoboCamKeyGroup` / `RoboCamJoystick` as the base, `drivers/EV3/` — EV3 byte code and Bluetooth, `drivers/Custom/` — the "custom robot" driver.
- `*Activity` — settings screens (`ServerSettings`, `RobotSettingsList`, `EV3Settings`, `CustomSettings`, `GlobalSettings`, `LocalControllers`, `SelectKey`, `Import`).
- `util/IabHelper` and neighbors — legacy in-app billing v3.
- `app/src/main/rs2/` — RenderScript sources (`yuv420888.rs`, `RSHelper.java`). **Not a source set, not part of the build**; kept as history. Do not wire it back in: Play already rejected APK 40 over the 32-bit `.so` produced by RenderScript.

## Hard constraints

- **minSdk 19 is a floor, not a choice.** Google Play requires an ads SDK of at least 22.0.0, and `play-services-ads:22.6.0` declares `minSdkVersion 19`. It cannot go lower, and going higher loses devices (Android 4.4–6.0 and newer).
- **Multidex must stay legacy.** API 19–20 have no native multidex: `multiDexEnabled true` plus `androidx.multidex.MultiDexApplication` in the manifest. Remove either and the app will not start on 4.4–5.1 (the 64K method limit is exceeded by ads + AndroidX + Webbit).
- **Ads API is GMA 22.x.** `MobileAds.initialize(context)`, app ID in `<meta-data com.google.android.gms.ads.APPLICATION_ID>`, banner listener is `onAdFailedToLoad(LoadAdError)`. Do not bring back the old `AdRequest.Builder` from GMA 10.
- **AndroidX is mandatory.** There is no `android.support.*` left in the code — do not reintroduce it.
- **Do not remove the path check in `HttpServer`.** The request path arrives raw from the request line; any `..` is a path traversal (incident 2026-09-08, fixed and released). Any new file-serving logic must go through the same check.
- **Do not touch `uses-sdk`, permissions or `uses-feature` in the manifest without a reason** — they directly affect device coverage in Play Console.

## Repository rules

- Commits and tags are made by the repository owner; commit only on an explicit request, and never push without a separate request.
- Follow the style of the neighboring files: Java without lambdas or `var` where they are absent; comments only where the reason is not obvious.
- Build and dependency changes are accompanied by a check of `README.md` and this file.
- New UI strings go into `res/values` (English is the default), translations into `values-ru` and `values-kk`.

## Publishing to Google Play

- targetSdk 36 is required for new versions from 2026-08-31 (extension to 2026-11-01 on request). Google Play does not restrict minSdk.
- App Signing is enabled: the build on disk is signed with the local key (CN=Alexey Valuev, O=ProgHouse), so an APK from disk will not install over the Play copy — `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Testing on a device that has the Play build requires a separate `applicationId`.
- The advertising ID declaration (App content → Advertising ID) is mandatory: the APK declares `com.google.android.gms.permission.AD_ID`.
