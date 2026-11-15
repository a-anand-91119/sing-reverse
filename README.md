# Sing Reverse

An ad-free take on the "sing, record & reverse" party game for Android (Kotlin + Jetpack Compose).

## How to play

1. **Sing** – Player 1 taps **Record**, sings or says a short phrase, then taps **Stop recording**.
2. Tap **Listen backwards**, then **Pass to Player 2**.
3. **Copy** – Player 2 taps **Listen backwards**, then **Record** to imitate the sound.
4. Tap **Reveal the result** to hear the imitation backwards. Compare it using **Play the original**.
5. Try the imitation again, or choose **New round** to clear both recordings and start over.

The screen guides one player at a time. Replay and re-record controls appear after recording; the app follows the device’s light or dark theme and supports larger text.

## Privacy

- No ads, no analytics, no accounts.
- The app does not request the `INTERNET` permission, so it cannot send anything anywhere.
- Recordings stay in memory only and disappear when the app closes.

## Build

```sh
./gradlew testDebugUnitTest assembleDebug   # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                      # install on a connected device / emulator
```

Requires JDK 17+ and the Android SDK (compileSdk 36).
