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
