# Push Log

Nothing pushed. Local only.

## 2026-08-27 - initial scaffold
Android overlay app: MediaProjection capture, ML Kit OCR, percentage-to-American conversion drawn
as in-place chips. Not compiled - no Android SDK on the build machine at time of writing.

## 2026-08-27 - v0.2.0 accessibility rewrite (local)
Replaced MediaProjection capture with an AccessibilityService: auto-detects the foreground app,
shows a "Show American odds?" prompt inside target apps, reads the screen via the accessibility
screenshot API (no per-session consent), converts, and clears on leaving. Verified on emulator.
NOT pushed - awaiting go to cut the v0.2.0 release.

## 2026-08-27 - v0.4.4 PUBLIC LAUNCH READY
Live: https://bettor-odds.github.io (Download + full setup walkthrough on every device).
Latest release v0.4.4. Verified end-to-end on emulator:
- Conversion math correct (16 authoritative values).
- Reads only leaf price nodes (converts the price, not the whole bet button).
- Off-screen pruning + 55ms scroll correction for speed/smoothness.
- Near-auto update: background download + one-tap system install (tested working).
Real-device confirmation on live Novig still recommended before wide push.
