# Guided round redesign (Direction A mix)

Approved mockups: "Mix · A4 + A1 dock" for Player 1 and the handoff, then "Option 1 · Player 2 locks in, then hears the original" (revised after phone testing).

## Why
Players found the current flow confusing: the big pad changed meaning (Record → Listen backwards → Reveal), extra controls appeared as small links, labels used game jargon, and nothing showed that a sound existed.

## Goals
- Every control keeps one job and one place. A bottom dock holds Redo (left), Mic (center), Next (right) on every screen.
- Each recording is a sound card with a waveform and its own play buttons.
- The app plays the obvious next sound on its own: backwards after Player 1 stops, the sound when Player 2 starts, Player 2's copy backwards on the Guess screen, and the original on the Reveal screen. It never starts recording on its own.
- The game is a guessing game: Player 2 decodes their own copy played backwards and guesses Player 1's phrase. The original stays hidden until Player 2 locks in.
- Plain words: "backwards", "your copy", "original".

## Non-goals
Scoring, voting, more than two players, saving recordings, new dependencies.

## Approaches considered
- B (voice-note thread) and C (game-show screens) were rejected in favour of A.
- Within A: A1 thumb dock, A2 checklist, A3 single card with switch, A4 auto-play. Chosen: A4 + A1 dock.
- Ending: "Guess out loud" (Option 1) over "Type the answer" (Option 2, typing slows a passed phone). Player 1 judging and Swap roles were dropped. The Copy / Original / Both switch and the countdown were removed after phone testing.

## Screens
1. **Sing, empty** – Player 1 dot, "1 of 3", empty dashed card. Only Mic is enabled; Next reads "Record first".
2. **Recording** – timer `0:02.7 / 0:10`, live level bars, Mic becomes Stop. Stops by itself at 10 s.
3. **Sing, recorded** – backwards playback starts automatically with a playhead; card has Normal and Backwards buttons. Next = "Pass phone".
4. **Handoff** – full-screen amber "Hand the phone to Player 2"; button "I'm Player 2 · Play the sound".
5. **Copy** – the backwards sound plays once; Player 2 replays it with "Hear again" as often as they like and taps the mic when ready (no countdown).
6. **Copy recorded** – sound-to-copy and "Your copy" cards, each playable. Next = "Hear backwards".
7. **Guess** – "What did Player 1 say?" Player 2's copy plays backwards automatically; the center button replays/stops it. Player 1's sound stays replayable backwards ("Hear again"); its normal version is locked until Player 2 locks in. Dock: Copy again (back to 5 with the copy cleared), play/stop, Lock in guess.
8. **Lock-in confirm** – dialog "Lock in your guess?" with "Lock in & hear the original" / "Not yet".
9. **Reveal** – "Player 1 said…" The original plays forwards automatically; the center button replays/stops it; "Your copy, backwards" card stays playable. Dock: New round with confirm dialog. No judging, no scoring.

## Calls made by the builder (not discussed)
- Redo discards the current turn's take and returns to the empty state; Mic records over it directly.
- Copy again clears Player 2's take and returns to the Copy screen without replaying.
- On Guess and Reveal the center dock button plays/stops that screen's main sound.
- System Back goes to the previous screen and stops any audio.
- Constraints kept: Kotlin + Compose, Android 8+, recordings in memory, no internet, mic permission flow, dark theme, large text scrolling.
