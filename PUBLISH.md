# Publishing the download page

The build machine has no GitHub login, so this last step runs from your terminal.

## One command
```
cd /Users/corbie/pm-odds-overlay
brew install gh && gh auth login      # first time only
./publish.sh bettor-odds v0.1.0       # <your-github-org> <version>
```

That creates the public repo, turns on the download page, and publishes the APK. It prints:

- Download page: `https://bettor-odds.github.io/odds-overlay/`  <- send this to users
- Direct APK:    `https://github.com/bettor-odds/odds-overlay/releases/latest/download/odds-overlay.apk`

The page auto-detects Android, shows one big Download button, and walks users through the three
permission prompts. iPhone visitors see a short "Android only" note instead.

## Shipping a new version later
Bump `versionCode`/`versionName` in `app/build.gradle.kts`, rebuild, then:
```
./gradlew :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk dist/odds-overlay-v0.2.0.apk
./publish.sh bettor-odds v0.2.0
```
The download page link never changes - it always points at the latest release.
