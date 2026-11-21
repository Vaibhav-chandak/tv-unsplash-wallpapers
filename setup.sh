#!/bin/sh
# Install or set up TV Unsplash wallpapers on an Android TV that runs the cleanyourtv launcher (com.gathin.tvhome).
# Build first: ./gradlew assembleRelease   (JDK 17, Android SDK 34)
# Then, with the TV's network debugging on:
#   ./setup.sh <tv ip> [access key] [cleanyourtv folder, to push the starter look]
# Options, as environment variables (each can also be sent later on its own with SKIP_INSTALL=1):
#   PER_DAY=4                         photos a day, 1 to 48 (4, the default, follows the light)
#   TOPICS='architecture,street'      your own searches, taken in turn; 'none' = the built-in nature ones
#   COLLECTIONS='1234,5678'           Unsplash collection ids instead of searches; 'none' turns them off
#   PLACE_ONLY=1                      only photos with a place name (by default preferred, not required)
#   NO_CREDIT=1                       leave "/ Unsplash" out of the caption
#   SKIP_INSTALL=1                    only send the options
# Example: PER_DAY=6 TOPICS='india landscape,temples,himalaya' ./setup.sh 192.168.1.50 <key>
# The key goes straight to the TV and is kept only in this app's private storage there.
set -e
[ -n "$1" ] || { echo "usage: $0 <tv ip> [access key] [cleanyourtv folder]" >&2; exit 2; }
case "$1" in *:*) S="$1" ;; *) S="$1:5555" ;; esac
ADB="${ADB:-adb}"
"$ADB" connect "$S" >/dev/null
if [ -z "$SKIP_INSTALL" ]; then
  APK="$(dirname "$0")/app/build/outputs/apk/release/app-release.apk"
  [ -f "$APK" ] || { echo "build it first: ./gradlew assembleRelease" >&2; exit 2; }
  if [ -n "$3" ]; then TV_HOST="$S" "$3/tools/theme.sh" "$(dirname "$0")/theme"; fi
  "$ADB" -s "$S" install -r "$APK"
  # The launcher's ThemeProvider only accepts writers that hold WRITE_SECURE_SETTINGS.
  "$ADB" -s "$S" shell pm grant app.tvunsplash android.permission.WRITE_SECURE_SETTINGS
  # An app with no screen is never "used", so app standby would run its checks once a day at most.
  "$ADB" -s "$S" shell dumpsys deviceidle whitelist +app.tvunsplash
fi
# One shell line for the TV, each text value in single quotes so spaces and commas arrive as typed.
q() { printf "'%s'" "$(printf '%s' "$1" | tr -d "'")"; }
LINE="am broadcast -n app.tvunsplash/.Setup -a app.tvunsplash.SETUP"
[ -n "$2" ] && LINE="$LINE --es key $(q "$2")"
case "$PER_DAY" in *[!0-9]*) echo "PER_DAY must be a number from 1 to 48" >&2; exit 2 ;; esac
[ -n "$PER_DAY" ] && LINE="$LINE --ei per_day $PER_DAY"
[ -n "$TOPICS" ] && LINE="$LINE --es topics $(q "$TOPICS")"
[ -n "$COLLECTIONS" ] && LINE="$LINE --es collections $(q "$COLLECTIONS")"
[ -n "$PLACE_ONLY" ] && LINE="$LINE --ez place_only true"
[ -n "$NO_CREDIT" ] && LINE="$LINE --ez credit false"
"$ADB" -s "$S" shell "$LINE"
echo "Done. In a minute: $ADB -s $S logcat -d -s UnsplashWall:*"
