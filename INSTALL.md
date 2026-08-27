# Install (Android)

This app is not on the Play Store. It reads the screen and draws over other apps, which Play does
not allow, so it installs by sideloading the APK.

1. Download `odds-overlay-vX.Y.Z.apk` from the Releases page onto your Android phone.
2. Open it. Android will ask to allow installs from your browser or files app - allow it.
3. Open **Odds Overlay** and tap **Start overlay**. Grant, in order:
   - Notifications
   - Display over other apps
   - Screen capture ("Start now")
4. Open Novig. Percentages are replaced with American odds in place.
5. Stop any time from the app or the notification's **Stop** action.

Nothing leaves your phone. The app has no internet permission - capture and conversion are entirely
on-device.

## If nothing appears
- The board needs at least two percentages visible before the overlay draws.
- Give it a second after the screen settles - it waits for a still frame before converting.
- If a screen shows nothing at all, that app may be blocking capture (see README).

## Requirements
Android 10 (API 29) or newer.
