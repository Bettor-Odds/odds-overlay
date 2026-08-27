# Odds Overlay

Converts the implied-probability percentages that prediction market apps display back into American
odds, drawn over the original number in place. Built for Novig first; the recognition is
app-agnostic, so it works anywhere percentages are rendered as text.

## How it works

1. `MediaProjection` mirrors the display into an `ImageReader`.
2. Frames are sampled at 4/sec and fingerprinted. Identical frames are dropped before OCR.
3. Once a frame has been stable for two samples, ML Kit's on-device recognizer returns text with
   bounding boxes.
4. Percentage tokens are converted to American odds and painted over their own bounding boxes by a
   non-touchable `TYPE_APPLICATION_OVERLAY` window.

Nothing leaves the device. There is no network permission in the manifest.

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

Verified end to end on an Android 14 emulator: screen capture -> on-device OCR -> conversion ->
in-place overlay. All six test prices converted correctly (56.6% -> -130, 43.4% -> +130, 40.0% ->
+150, 60.0% -> -150, 72.5% -> -264, 27.5% -> +264) and drew on the correct rows. Conversion math is
also covered by 9 unit tests.

A debug-only `DebugBoardActivity` (debug source set, never in release) shows a static percentage
board for verifying the overlay without a live app:
`adb shell am start -n com.bettorodds.oddsoverlay/.DebugBoardActivity`.

### Known items to tune on a real device
These surfaced during emulator testing and are best finished against the real Novig app, whose OCR
boxes are exact (the emulator's scaled test host is not):

- **Chip masking.** The converted chip should fully cover the original percentage. Bleed is set
  generously; confirm it covers cleanly at Novig's real font size and adjust `HORIZONTAL_BLEED` /
  `VERTICAL_BLEED` in `OverlayView`.
- **Transient misreads.** During screen animations OCR can briefly read a fragment and draw a wrong
  value that sticks once the screen goes static. A real static board avoids this, but a robust
  build should require a value to be read twice before committing it. Not yet implemented.
- **Battery/heat.** Measure over 20-30 minutes on a real phone; the emulator gives no useful number.
