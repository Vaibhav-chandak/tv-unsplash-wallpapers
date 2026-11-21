# Install TV Unsplash wallpapers on an Android TV that runs the cleanyourtv launcher (com.gathin.tvhome).
# Build first: .\gradlew.bat assembleRelease   (JDK 17, Android SDK 34)
# Then, with the TV's network debugging on:
#   powershell -ExecutionPolicy Bypass -File setup.ps1 -TvHost <tv ip> -Key <Unsplash access key> [-Theme <cleanyourtv folder>] [-NoCredit]
# -Theme pushes the starter look in theme\ with cleanyourtv's tools\theme.ps1 (needs a launcher with wallpaper_watch).
# The key goes straight to the TV and is kept only in this app's private storage there.
param([Parameter(Mandatory = $true)][string]$TvHost, [string]$Key = '', [string]$Theme = '', [switch]$NoCredit,
      [string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
$s = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "Build it first: .\gradlew.bat assembleRelease (no $apk)" }
& $Adb connect $s | Out-Null
if ($Theme) {
    $env:TV_ADB_BINARY = (Get-Command $Adb).Source
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Theme 'tools\theme.ps1') -TvHost $s (Join-Path $PSScriptRoot 'theme')
}
& $Adb -s $s install -r $apk
# The launcher's ThemeProvider only accepts writers that hold WRITE_SECURE_SETTINGS.
& $Adb -s $s shell pm grant app.tvunsplash android.permission.WRITE_SECURE_SETTINGS
# An app with no screen is never "used", so app standby would run its checks once a day at most.
& $Adb -s $s shell dumpsys deviceidle whitelist +app.tvunsplash
$a = @('shell', 'am', 'broadcast', '-n', 'app.tvunsplash/.Setup', '-a', 'app.tvunsplash.SETUP')
if ($Key) { $a += @('--es', 'key', $Key) }
if ($NoCredit) { $a += @('--ez', 'credit', 'false') }
& $Adb -s $s @a
Write-Host "Done. In a minute: $Adb -s $s logcat -d -s UnsplashWall:*"
