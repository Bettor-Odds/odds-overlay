# Odds Overlay

Converts the implied-probability percentages that prediction market apps display back into American
odds, drawn over the original number in place. Built for Novig first; the recognition is
app-agnostic, so it works anywhere percentages are rendered as text.

## How it works

An **accessibility service** watches which app is in the foreground. The moment a target app (Novig
and other prediction markets, plus any the user adds) comes forward, it:

1. shows a "Show American odds?" prompt (or converts immediately if set to always-on for that app),
2. reads the screen with the accessibility screenshot API - no per-session capture consent,
3. runs on-device OCR, converts each percentage, and paints the American odds over it,
4. re-reads on the app's content-changed events, throttled to ~1/sec.

When the user leaves the app, an event fires and the overlay clears and goes idle - it does nothing
outside a target app. Nothing leaves the device; there is no network permission.

The user enables the service once in Settings. After that, opening a target app is the whole
interaction - no launching this app, no per-session taps.

## Design notes

**Why chips clear on scroll.** When the frame changes, the previous frame's boxes are stale.
Rather than drag them down the screen, the overlay clears and repaints once the screen settles. A
scroll briefly shows the app's own percentages, which is correct if unconverted.

**Why extremes are dropped.** One decimal of input is plenty of precision in the range people
actually trade: 43.4% is +130.4, and the rounding band on that decimal is worth about a third of a
point. At the extremes it collapses - 99.0% covers -9423 to -10426 - so prices outside 0.5%..99%
render nothing rather than a confidently wrong number.

**Why colors are sampled.** Each chip takes its background from the pixels it covers and its text
color from that background's luminance, so it sits in the host app's theme without knowing anything
about it.

## Build

Requires JDK 17+ and the Android SDK (compileSdk 35).

```
./gradlew :app:assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # conversion math
```

The Gradle wrapper JAR is not committed. Generate it once with `gradle wrapper --gradle-version 8.9`
or open the project in Android Studio, which writes it on first sync.

## Known constraints

- **FLAG_SECURE ends this.** If a host app marks its screens secure, capture returns black frames
  and the overlay has nothing to read. Novig does not currently set it. They can in any release.
- A persistent screen-capture notification is shown the entire session. Not removable.
- Android 14+ shows a per-app capture picker; some app transitions force re-consent.
- Layout changes in the host app do not break recognition, but heavy animation defeats the
  frame-settling heuristic and chips will flicker.
- `MIN_HITS_TO_DRAW` requires two percentages on screen before drawing anything, which keeps the
  overlay off unrelated apps that happen to show a percentage.

## Status

Verified end to end on an Android 14 emulator, fully automatic: enable the accessibility service ->
open the target app -> the "Show American odds?" prompt appears -> tap Turn on -> all six test
prices convert correctly and cleanly (56.6->-130, 43.4->+130, 40.0->+150, 60.0->-150, 72.5->-264,
27.5->+264). Leaving the app clears the overlay; returning restores it on its own. Conversion math
is covered by 9 unit tests.

A debug-only `DebugBoardActivity` (debug source set) stands in for a target app so the pipeline can
be verified without a live one.

### Still owed on a real device
- Confirm against the real Novig app - its odds may be canvas-drawn; the screenshot+OCR path handles
  that, but only a real device proves the layout.
- Filter transient misreads during heavy animation (read a value twice before committing).
- Measure battery over a real session.
