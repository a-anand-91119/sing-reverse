# Sing Reverse

An ad-free take on the "sing, record & reverse" party game for Android (Kotlin + Jetpack Compose).

## How to play

1. **Sing** – Player 1 taps the mic, sings a short phrase (up to 10 seconds), and taps it again to stop. The phrase plays backwards automatically.
2. Tap **Pass phone**, hand it over, and Player 2 taps **I’m Player 2 · Play the sound**.
3. **Copy** – Player 2 hears the backwards sound, replays it with **Hear again** as often as they like, then taps the mic to record an imitation.
4. **Guess** – tap **Hear backwards**. Player 2’s imitation plays backwards; work out what Player 1 said. Not clear? **Copy again**.
5. **Lock in guess**, say it out loud, then everyone hears Player 1’s original. **New round** clears both recordings.

The bottom bar always keeps the same three spots: Redo or Copy again on the left, the main action in the middle, and the next step on the right. The app follows the device’s light or dark theme and supports larger text.

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
