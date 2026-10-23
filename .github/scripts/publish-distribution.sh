#!/usr/bin/env bash
# Publish a signed APK plus immutable versionCode and SHA-256 metadata.
set -euo pipefail

: "${GH_TOKEN:?ANDROID_DISTRIBUTION_TOKEN must be configured}"
: "${APP:?APP must be set}"
: "${VERSION:?VERSION must be set}"
: "${VERSION_CODE:?VERSION_CODE must be set}"
[[ "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]] || { echo "::error::Invalid APK versionCode"; exit 1; }

TARGET="N2K02/n2k02.github.io"
SLUG="shizucallrecorder"
APK="$APP-$VERSION.apk"
test -s "$APK"

CHANNEL="stable"
TAG="$SLUG-v$VERSION-build-$GITHUB_RUN_NUMBER"
MANIFEST="$SLUG/updates/stable.json"
RELEASE_FLAGS=(--latest=false)
DISPLAY_VERSION="$VERSION${VERSION_SUFFIX:-}"

if [ "$GITHUB_EVENT_NAME" = "pull_request" ]; then
  : "${PR_NUMBER:?Missing PR_NUMBER}"
  CHANNEL="experimental"
  TAG="$SLUG-experimental-pr-$PR_NUMBER-run-$GITHUB_RUN_NUMBER"
  MANIFEST="$SLUG/updates/experimental.json"
  RELEASE_FLAGS=(--prerelease --latest=false)
fi

SHA=$(sha256sum "$APK" | cut -d' ' -f1)
SIZE=$(stat -c %s "$APK")

if gh release view "$TAG" --repo "$TARGET" >/dev/null 2>&1; then
  gh release upload "$TAG" "$APK" --repo "$TARGET" --clobber
else
  gh release create "$TAG" "$APK" --repo "$TARGET" --target main \
    --title "$APP $DISPLAY_VERSION ($CHANNEL)" --notes-file release-notes.md "${RELEASE_FLAGS[@]}"
fi

URL="https://github.com/$TARGET/releases/download/$TAG/$APK"
NOTES=$( { grep '^- ' release-notes.md || true; } | head -10 | sed 's/^- //' |
  jq -R -s 'split("\n") | map(select(length > 0))')

jq -n \
  --arg channel "$CHANNEL" \
  --arg tag "$TAG" \
  --arg versionName "$DISPLAY_VERSION" \
  --argjson versionCode "$VERSION_CODE" \
  --arg apkName "$APK" \
  --arg apkUrl "$URL" \
  --argjson apkSize "$SIZE" \
  --arg sha256 "$SHA" \
  --arg pageUrl "https://github.com/$TARGET/releases/tag/$TAG" \
  --argjson notes "$NOTES" \
  '{channel:$channel,tag:$tag,versionName:$versionName,versionCode:$versionCode,apkName:$apkName,apkUrl:$apkUrl,apkSize:$apkSize,sha256:$sha256,pageUrl:$pageUrl,notes:$notes}' > manifest.json

CURRENT_JSON=$(gh api "repos/$TARGET/contents/$MANIFEST" --jq .content 2>/dev/null |
  tr -d '\n' | base64 -d 2>/dev/null || true)
if [ -n "$CURRENT_JSON" ]; then
  OLD=$(jq -r '.versionCode // 0' <<<"$CURRENT_JSON")
  if [[ "$OLD" =~ ^[0-9]+$ ]] && (( OLD >= VERSION_CODE )); then
    echo "Equal or newer $CHANNEL manifest already published; keeping it."
    exit 0
  fi
fi

ENC=$(base64 -w0 manifest.json)
for attempt in 1 2 3 4 5; do
  CURRENT_SHA=$(gh api "repos/$TARGET/contents/$MANIFEST" --jq .sha 2>/dev/null || true)
  ARGS=(--method PUT "repos/$TARGET/contents/$MANIFEST"
    -f "message=Publish $APP $DISPLAY_VERSION ($CHANNEL)"
    -f "content=$ENC" -f "branch=main")
  [ -z "$CURRENT_SHA" ] || ARGS+=(-f "sha=$CURRENT_SHA")
  if gh api "${ARGS[@]}" >/dev/null; then
    echo "Published $CHANNEL $APP $DISPLAY_VERSION versionCode=$VERSION_CODE"
    exit 0
  fi
  echo "::warning::Manifest update raced with another publication (attempt $attempt/5)"
  sleep $((attempt * 2))
done

echo "::error::Unable to publish update manifest"
exit 1
