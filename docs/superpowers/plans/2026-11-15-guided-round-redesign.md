# Guided Round Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the morphing action pad with the approved 7-screen guided round (fixed dock, sound cards, auto-play, countdown, Both).

**Architecture:** `Audio.kt` gains level/progress callbacks, a length cap and a pure `waveform()` helper. `MainActivity.kt` keeps one `GameScreen` state holder driven by a `Stage` enum (Sing, Handoff, Copy, Result) and small private composables (Dock, SoundCard, Handoff, ResultCard).

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AudioRecord/AudioTrack. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-15-guided-round-redesign-design.md`

## Global Constraints
- Android 8+ (minSdk 26), recordings in memory only, no INTERNET permission.
- Recording cap: 10 s = `44_100 * 2 * 10` bytes.
- Countdown: 3 s, one tick per second.
- Copy uses "backwards", "your copy", "original"; no "flip", "challenge", "reveal".
- Player 1 = primary blue, Player 2 = amber `#FFB547` (dark `#FFC56E`), on-amber ink `#2A1A00`.
- Keep: mic permission flow, snackbar errors, New round confirm dialog, dark theme, vertical scroll at 200 % text.

## Review Focus
- Mic permission denied while the countdown fires → no crash, snackbar, Mic stays available.
- Back pressed during auto-play or countdown → audio/countdown stops, previous stage shown.
- Stop tapped immediately (0 bytes recorded) → "No sound was recorded" snackbar, card stays empty.
- Recording reaches 10 s → stops by itself, auto-play backwards follows (Sing) or Next enables (Copy).
- Dark theme amber handoff and Player 2 waveform keep readable contrast.

---

### Task 1: Audio helpers

**Files:** Modify `app/src/main/java/dev/notyouraverage/singreverse/Audio.kt`; Test `app/src/test/java/dev/notyouraverage/singreverse/ReversePcmTest.kt`

**Interfaces – Produces:**
- `fun waveform(pcm: ByteArray, bars: Int): List<Float>` – peak |sample| / 32768 per equal bucket, values in 0..1, size `bars` (all 0f for empty input).
- `Audio.MAX_BYTES: Int`, `Audio.BYTES_PER_SECOND: Int`
- `suspend fun Audio.record(out: ByteArrayOutputStream, onLevel: (Float) -> Unit = {})` – stops at `MAX_BYTES`; `onLevel` gets the peak of each read chunk (0..1).
- `suspend fun Audio.play(pcm: ByteArray, onProgress: (Float) -> Unit = {})` – progress 0..1 from playback head.

- [ ] Tests: `waveformPeaksPerBucket` (samples `[0, 16384, -32768, 8192]` LE, 2 bars → `[0.5f, 1f]`), `waveformEmptyIsFlat` (`waveform(byteArrayOf(), 3) == listOf(0f,0f,0f)`).
- [ ] Run `./gradlew testDebugUnitTest` → FAIL (unresolved `waveform`).
- [ ] Implement the four interfaces above.
- [ ] Run `./gradlew testDebugUnitTest` → PASS.

### Task 2: Guided round UI

**Files:** Modify `app/src/main/java/dev/notyouraverage/singreverse/MainActivity.kt`, `app/src/main/res/values/strings.xml`

**Interfaces – Consumes:** Task 1.

- [ ] `Stage { Sing, Handoff, Copy, Result }`; `Clip { Mine, Copy }` slots 0/1; one `job` for whatever audio/countdown runs; `level` list for the live meter; `progress` for the playhead; `countdown: Int?`.
- [ ] Header row: player dot + "Player 1"/"Player 2"/"Both" and "1 of 3"/"2 of 3"/"3 of 3".
- [ ] `SoundCard(label, meta, bars, color, progress, buttons)` – dashed empty variant with hint.
- [ ] `Dock(left, center, right)` – left/right are 40 dp circle + label, disabled state shows reason label.
- [ ] Sing: stop recording → auto `play(reverse(clip0))`. Next "Pass phone" → Handoff.
- [ ] Handoff: amber full screen, button "I'm Player 2 · Play the sound" → Copy, play backwards, then countdown 3→1, then record (permission check). Mic spot = Cancel during countdown.
- [ ] Copy: cards "Sound to copy" (Hear again) and "Your copy"; Next "Hear result" → Result.
- [ ] Result: segmented Copy / Original / Both + one play/stop; Both auto-plays on entry (copy reversed, then original). Dock: Copy again (clears clip1, → Copy), disabled mic, New round (confirm dialog).
- [ ] Redo clears the current stage's clip. Back: stop job, go to previous stage.
- [ ] Run `./gradlew testDebugUnitTest lintDebug assembleDebug` → BUILD SUCCESSFUL.

### Task 3: Device check and docs

- [ ] Install on emulator; walk all 7 screens; screenshots to `app/build/ui-check/` (light, dark, 200 % font).
- [ ] `adb logcat -b crash` empty.
- [ ] Update `README.md` "How to play" and `DESIGN.md` components to the new flow.
