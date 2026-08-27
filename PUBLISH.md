# Publishing to GitHub

Done from your machine - this build environment has no GitHub credentials, so the repo and release
have to be created by you (or anyone with push rights).

## One-time: create the public repo and push
```
cd /Users/corbie/pm-odds-overlay

# with the gh CLI (brew install gh; gh auth login), simplest:
gh repo create bettor-odds/odds-overlay --public --source=. --remote=origin --push

# or by hand, after creating an empty public repo in the GitHub UI:
git remote add origin https://github.com/<you>/odds-overlay.git
git push -u origin main
```

## Cut a release with a downloadable APK
Tagging triggers .github/workflows/release.yml, which builds the APK on CI and attaches it:
```
git tag v0.1.0
git push origin v0.1.0
```
The download link is then:
`https://github.com/<you>/odds-overlay/releases/latest`

## Or attach the APK already built here, without waiting on CI
```
gh release create v0.1.0 dist/odds-overlay-v0.1.0.apk \
  --title "v0.1.0" \
  --notes "Sideload build. See INSTALL.md."
```

The APK in `dist/` is a debug build - fine for sideloading and sharing. For a Play-independent
"official" build you would sign a release APK with your own keystore; not required for sideload.
