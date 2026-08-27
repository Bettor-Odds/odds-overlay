#!/usr/bin/env bash
# One-shot publish: creates the public repo, enables the download page, and cuts a release
# with the APK attached. Re-run for later versions - it will just add a new release.
#
#   ./publish.sh <github-owner> [version]
#   ./publish.sh bettor-odds v0.1.0
set -euo pipefail

OWNER="${1:?usage: ./publish.sh <github-owner> [version]}"
VERSION="${2:-v0.1.0}"
REPO="odds-overlay"
SLUG="$OWNER/$REPO"

command -v gh >/dev/null || { echo "Install gh first: brew install gh && gh auth login"; exit 1; }

# Bake the real repo into the landing page and release notes.
sed -i '' "s#REPO_SLUG#$SLUG#g; s#REPO_OWNER#$OWNER#g" docs/index.html
sed -i '' "s#REPO_OWNER#$OWNER#g" .github/workflows/release.yml || true

git add -A
git commit -m "Wire download page to $SLUG" >/dev/null 2>&1 || true

# Create the repo if it does not exist yet, then push.
if ! gh repo view "$SLUG" >/dev/null 2>&1; then
  gh repo create "$SLUG" --public --source=. --remote=origin --push
else
  git push -u origin main
fi

# Turn on GitHub Pages from /docs so the download page goes live.
gh api -X POST "repos/$SLUG/pages" -f "source[branch]=main" -f "source[path]=/docs" >/dev/null 2>&1 \
  || echo "(Pages may already be enabled - check repo Settings > Pages)"

# Cut the release with the already-built APK under the stable name the page links to.
cp dist/odds-overlay-*.apk "dist/odds-overlay.apk" 2>/dev/null || true
gh release create "$VERSION" \
  "dist/odds-overlay.apk" \
  --title "$VERSION" \
  --notes "Install page: https://$OWNER.github.io/$REPO/"

echo
echo "Done."
echo "  Download page:  https://$OWNER.github.io/$REPO/"
echo "  Direct APK:     https://github.com/$SLUG/releases/latest/download/odds-overlay.apk"
echo "(Pages can take ~1 minute to go live the first time.)"
