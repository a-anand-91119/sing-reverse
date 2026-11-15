---
name: Sing Reverse
description: A bold native Android controller for a two-person singing game.
colors:
  cobalt: "#284DDB"
  white: "#FFFFFF"
  ice: "#F4F6FF"
  ink: "#17264B"
  muted-ink: "#526080"
  pale-blue: "#E0E7FF"
  deep-blue: "#1A348B"
  periwinkle: "#B8C5FF"
  pad-ink: "#142978"
  navy: "#101727"
  moon-white: "#E6EBFC"
  muted-moon: "#BAC4DF"
  blue-container: "#273C78"
  pale-moon: "#DEE5FF"
typography:
  headline:
    fontFamily: sans-serif
    fontWeight: 800
  action:
    fontFamily: sans-serif
    fontWeight: 700
  body:
    fontFamily: sans-serif
    fontWeight: 400
rounded:
  progress: 2dp
  control: 20dp
  pad: 32dp
spacing:
  small: 8dp
  medium: 12dp
  control-gap: 16dp
  gutter: 24dp
  section: 32dp
components:
  action-pad:
    backgroundColor: "{colors.cobalt}"
    textColor: "{colors.white}"
    rounded: "{rounded.pad}"
    padding: 24dp
  action-pad-dark:
    backgroundColor: "{colors.periwinkle}"
    textColor: "{colors.pad-ink}"
    rounded: "{rounded.pad}"
    padding: 24dp
  button-tonal:
    backgroundColor: "{colors.pale-blue}"
    textColor: "{colors.deep-blue}"
    rounded: "{rounded.control}"
    padding: 20dp
  button-tonal-dark:
    backgroundColor: "{colors.blue-container}"
    textColor: "{colors.pale-moon}"
    rounded: "{rounded.control}"
    padding: 20dp
---

# Design System: Sing Reverse

## Overview

**Creative North Star: "The Party-Game Controller"**

A television party-game controller translated into native Android Material 3: confident type, a generous action pad, and a calm cool field. Friendly instruction copy and a clear current-player label make the phone easy to pass between players.

The system uses native controls, typography, feedback, and system insets. Brand expression comes from the blue palette, bold hierarchy, and rounded pads. This record describes `MainActivity.kt` and the rendered light and dark screens in `app/build/ui-check`.

**Key Characteristics:**
- One visually dominant next action.
- Cobalt and ice in light mode; periwinkle and navy in dark mode.
- Bold Material type and large, rounded controls.
- A fixed bottom dock: Redo, mic, next.
- Player 1 is blue; Player 2 is amber.

## Colors

Cool blue controls sit on a quiet, nearly neutral field. The frontmatter records the custom colors actually used; Android Material roles select the light or dark scheme.

### Primary

- **Cobalt / Periwinkle:** `primary`, for the filled next-action control, completed progress bars, and the active step label.
- **White / Pad Ink:** `onPrimary`, for content inside the filled control.
- **Pale Blue / Blue Container:** `primaryContainer`, for tonal controls and future progress bars.
- **Deep Blue / Pale Moon:** `onPrimaryContainer`, for tonal-control content.

### Neutral

- **Ice / Navy:** shared `background` and `surface` field.
- **Ink / Moon White:** shared `onBackground` and `onSurface` foreground.
- **Muted Ink / Muted Moon:** `onSurfaceVariant`, for hints, inactive step labels, and player context.

Recording uses the inherited Material `error`/`onError` pair. Disabled controls, snackbars, and dialogs retain Material defaults rather than acquiring a separate custom palette.

**The Semantic Pair Rule.** Apply each container with its corresponding on-color through `MaterialTheme.colorScheme`, including the dark scheme.

## Typography

**Display and Body Font:** Android's native sans-serif through the default Material 3 type scale. Sizes and line heights remain Material role defaults; font scaling remains enabled. The frontmatter records explicit weight overrides without inventing fixed text sizes.

### Hierarchy

- **Instruction:** `headlineLarge`, extra bold; centered, with the current task expressed as a short sentence.
- **Action-pad label:** `headlineSmall`, bold; centered below a drawn icon.
- **App title:** `titleLarge`, bold. The smaller filled reveal control also uses `titleLarge` at its default weight.
- **Tonal controls:** `titleMedium`, bold.
- **Instruction hint:** `bodyLarge`; pad status uses `bodyMedium`.
- **Step and player context:** `labelLarge`; only the current step gets an explicit bold override.

**The Native Scale Rule.** Use Material typography roles and sp-based font scaling; retain wrapping and vertical scrolling as text grows.

## Layout

A single centered column fills available width up to 480dp. Scaffold insets protect system bars. Content uses 24dp horizontal gutters and 12dp vertical padding, with vertical scrolling for short windows or large text.

The header leads into three equal-width progress segments, separated by 12dp. Centered instruction and hint precede the dominant action pad. Supporting actions follow it; player context sits below. Spacing follows the small recurring steps recorded above, with a larger 40dp pause after progress.

**The Next Action Rule.** Keep the current task visually dominant. Show replay, re-record, and continuation controls when the recorded state makes them useful.

## Elevation & Depth

The game surface is flat at rest. Solid color and tonal contrast separate controls from the background; there are no custom shadows or decorative cards. Standard Material interaction indication, snackbar, and dialog presentation remain intact. No custom animation or motion tokens are defined.

## Shapes

Broad rounded rectangles carry the controller metaphor. The mic and play controls are circles; cards use a 16dp radius and buttons the Material defaults. Icons are Android vector drawables.

## Components

### Dock
A fixed bottom bar on every turn screen: Redo or Copy again (left), a 76dp round main action (center: mic, Stop, or play/stop) and the next step (right). Positions never change. A disabled next step names what is missing ("Record first"). While recording, the mic becomes a red Stop.

### Sound cards
Each recording is a bordered card with a title, duration, level bars and its own play buttons. Empty cards are dashed. A playing card gets a 2dp primary border and a red playhead; a recording card gets an error border and a live timer.

### Handoff
A full-screen amber screen (Player 2's color) with one dark button that starts Player 2's turn and plays the sound.

### Guess and reveal
Guess shows Player 2's copy played backwards, an amber hint panel, and a locked "Player 1's original" card. "Lock in guess" opens a confirm dialog; only then does the Reveal screen play the original. On both screens the center dock button plays or stops that screen's main sound.

### Feedback and confirmation

Short audio or permission failures appear in a Material snackbar. Clearing both clips requires a Material alert dialog with a keep-playing alternative. The instruction heading uses a polite live region when the task changes.

## Do's and Don'ts

### Do:

- **Do** pair Material color roles in both light and dark themes.
- **Do** keep one dominant next action and expose supporting controls as the turn develops.
- **Do** preserve native typography, semantic state, system insets, and scrolling at large text sizes.

### Don't:

- **Don't** replace native interaction feedback with custom decorative effects.
- **Don't** use color alone to communicate recording or playback; retain status and Stop labels.
- **Don't** move or repurpose dock positions between screens; change the label instead.
