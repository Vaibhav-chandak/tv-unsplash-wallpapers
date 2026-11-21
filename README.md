# TV Unsplash wallpapers

A fresh landscape photo on your Android TV's Home screen four times a day, matched to the time of day, with
the place and the photographer under the buttons. Made for the [cleanyourtv](https://github.com/Gathin23/cleanyourtv)
launcher (`com.gathin.tvhome`): it writes each photo into the launcher's wallpaper folder and deletes the one
before. No app tile, nothing on screen of its own.

<img src="screenshot.jpg" width="640" alt="A night skyline as the Home wallpaper, with its place and photographer under the buttons">

| Time | Photos |
|---|---|
| 05:00 - 11:00 | sunrise, misty mornings |
| 11:00 - 16:00 | mountains, lakes, coastlines, the Himalaya |
| 16:00 - 19:00 | sunset, golden hour |
| 19:00 - 05:00 | night sky, the Milky Way, city lights |

## How it behaves

- Every 30 minutes, only while the TV is online, it checks whether a new time slot has started. If so it asks
  Unsplash for ten matching landscape photos, picks one with a place name that it has not shown in its last 60,
  downloads it at 1920x1080 in sRGB (about 0.5 MB), writes it and its caption into the launcher's folder, then
  deletes the previous photo. Nothing is deleted until the new photo is fully written.
- Offline, or when Unsplash is down, the current photo simply stays.
- About 8 Unsplash API calls a day (one search, one download count per photo), far below the free limit.
- It runs for a few seconds per check (about 8 MB of memory), then Android ends it. Nothing runs between checks.

## Requirements

- An Android TV (Android 8 or later) with the **cleanyourtv launcher** as Home.
- A launcher build with the **`wallpaper_watch`** key, so a swapped photo shows without restarting Home.
  It is proposed upstream in [cleanyourtv discussion #4319](https://github.com/Gathin23/cleanyourtv/discussions/4319);
  until it is in a release, a new photo shows the next time Home starts.
- Optional, and recommended: **`wallpaper_scrim`** ([PR #4318](https://github.com/Gathin23/cleanyourtv/pull/4318)),
  which darkens each photo behind the text only as much as that photo needs. The starter look in `theme/` turns
  both on.
- Unsplash photos often carry an Adobe RGB colour profile, which made Home stutter on some TVs; the helper asks
  Unsplash for sRGB, and the launcher fix is [PR #4317](https://github.com/Gathin23/cleanyourtv/pull/4317).
- A free Unsplash **access key**: sign in at <https://unsplash.com/oauth/applications>, choose **New Application**,
  accept the guidelines and copy the **Access Key** (not the Secret key). Each person uses their own key.

## Install

Build it (JDK 17 and Android SDK 34), then run setup with the TV's network debugging on:

```bash
./gradlew assembleRelease
./setup.sh <tv ip> <access key> <path to your cleanyourtv checkout>
```

On Windows: `gradlew.bat assembleRelease`, then
`powershell -ExecutionPolicy Bypass -File setup.ps1 -TvHost <tv ip> -Key <access key> -Theme <path to cleanyourtv>`.

The third argument pushes the starter look in `theme/` (glass dock, captions, `wallpaper_watch` and
`wallpaper_scrim` on). Leave it out to keep your own theme; your theme then needs a `wallpapers/` collection
with one picture named `00-start.jpg` (the first photo replaces it), `wallpaper_rotation_minutes` above 0,
`wallpaper_captions` and `wallpaper_watch`.

Setup installs the app, grants it `WRITE_SECURE_SETTINGS` (the launcher's theme folder accepts writes only from
holders of it), exempts it from app standby so its checks keep running, sends it your key and fetches the first
photo. Check it with `adb logcat -d -s UnsplashWall:*`.

## Options

Sent over ADB, as setup does:

```bash
adb shell am broadcast -n app.tvunsplash/.Setup -a app.tvunsplash.SETUP --ez force true    # a new photo now
adb shell am broadcast -n app.tvunsplash/.Setup -a app.tvunsplash.SETUP --ez credit false  # caption without "/ Unsplash"
```

The caption credits the photographer and Unsplash by default, as Unsplash's API guidelines ask of apps that show
their photos. If you turn the Unsplash credit off, that is your call under their terms.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | To ask Unsplash for photos and download them (`api.unsplash.com`, `images.unsplash.com`). Nothing else is contacted. |
| `ACCESS_NETWORK_STATE` | To run its check only when the TV is online. |
| `RECEIVE_BOOT_COMPLETED` | To schedule its checks again after the TV restarts. |
| `WRITE_SECURE_SETTINGS` | Granted by setup over ADB. The launcher's ThemeProvider only takes files from apps that hold it. The app does not change any setting with it. |

No analytics, no ads, no account. Your Unsplash key is stored only on the TV, in this app's private storage.
Unsplash sees the TV's IP address when it serves a photo.

## Remove

```bash
adb shell pm uninstall app.tvunsplash
```

The last photo stays as the wallpaper until you push another theme.

## Credits

Photos are by their photographers on [Unsplash](https://unsplash.com), under the
[Unsplash License](https://unsplash.com/license). This project is not affiliated with Unsplash or with cleanyourtv.
The code is MIT-licensed (see `LICENSE`).
