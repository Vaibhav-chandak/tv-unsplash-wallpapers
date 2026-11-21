#!/bin/sh
# Install TV Unsplash wallpapers on an Android TV that runs the cleanyourtv launcher (com.gathin.tvhome).
# Build first: ./gradlew assembleRelease   (JDK 17, Android SDK 34)
# Then, with the TV's network debugging on:
#   ./setup.sh <tv ip> <Unsplash access key> [cleanyourtv folder, to push the starter look]
#   NO_CREDIT=1 ./setup.sh ...   leaves "/ Unsplash" out of the caption
# The key goes straight to the TV and is kept only in this app's private storage there.
set -e
[ -n "$1" ] || { echo "usage: $0 <tv ip> [access key] [cleanyourtv folder]" >&2; exit 2; }
case "$1" in *:*) S="$1" ;; *) S="$1:5555" ;; esac
ADB="${ADB:-adb}"
APK="$(dirname "$0")/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "build it first: ./gradlew assembleRelease" >&2; exit 2; }
"$ADB" connect "$S" >/dev/null
if [ -n "$3" ]; then TV_HOST="$S" "$3/tools/theme.sh" "$(dirname "$0")/theme"; fi
"$ADB" -s "$S" install -r "$APK"
# The launcher's ThemeProvider only accepts writers that hold WRITE_SECURE_SETTINGS.
"$ADB" -s "$S" shell pm grant app.tvunsplash android.permission.WRITE_SECURE_SETTINGS
# An app with no screen is never "used", so app standby would run its checks once a day at most.
"$ADB" -s "$S" shell dumpsys deviceidle whitelist +app.tvunsplash
set -- "$ADB" -s "$S" shell am broadcast -n app.tvunsplash/.Setup -a app.tvunsplash.SETUP ${2:+--es key "$2"}
[ -n "$NO_CREDIT" ] && set -- "$@" --ez credit false
"$@"
echo "Done. In a minute: $ADB -s $S logcat -d -s UnsplashWall:*"
