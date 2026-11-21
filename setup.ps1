# Install or set up TV Unsplash wallpapers on an Android TV that runs the cleanyourtv launcher (com.gathin.tvhome).
# Build first: .\gradlew.bat assembleRelease   (JDK 17, Android SDK 34)
# Then, with the TV's network debugging on:
#   powershell -ExecutionPolicy Bypass -File setup.ps1 -TvHost <tv ip> -Key <Unsplash access key> [options]
# Options (each can also be sent later on its own, with -SkipInstall):
#   -PerDay 4                   photos a day, 1 to 48 (4, the default, follows the light: sunrise, day, sunset, night)
#   -Topics 'architecture,street,wildlife'   your own searches, taken in turn; 'none' = the built-in nature ones
#   -Collections '1234,5678'    Unsplash collection ids instead of searches; 'none' turns them off
#   -PlaceOnly                  only photos with a place name (by default they are preferred, not required)
#   -NoCredit                   leave "/ Unsplash" out of the caption
#   -Theme <cleanyourtv folder> push the starter look in theme\ with cleanyourtv's tools\theme.ps1
#   -SkipInstall                only send the options (the app is already installed)
# The key goes straight to the TV and is kept only in this app's private storage there.
param([Parameter(Mandatory = $true)][string]$TvHost, [string]$Key = '', [int]$PerDay = 0, [string]$Topics = '',
      [string]$Collections = '', [switch]$PlaceOnly, [switch]$NoCredit, [string]$Theme = '', [switch]$SkipInstall,
      [string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
$s = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }
& $Adb connect $s | Out-Null
if (-not $SkipInstall) {
    $apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path $apk)) { throw "Build it first: .\gradlew.bat assembleRelease (no $apk)" }
    if ($Theme) {
        $env:TV_ADB_BINARY = (Get-Command $Adb).Source
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Theme 'tools\theme.ps1') -TvHost $s (Join-Path $PSScriptRoot 'theme')
    }
    & $Adb -s $s install -r $apk
    # The launcher's ThemeProvider only accepts writers that hold WRITE_SECURE_SETTINGS.
    & $Adb -s $s shell pm grant app.tvunsplash android.permission.WRITE_SECURE_SETTINGS
    # An app with no screen is never "used", so app standby would run its checks once a day at most.
    & $Adb -s $s shell dumpsys deviceidle whitelist +app.tvunsplash
}
# One shell line for the TV, each text value in single quotes so spaces and commas arrive as typed.
function Quote([string]$v) { "'" + ($v -replace "'", '') + "'" }
$line = 'am broadcast -n app.tvunsplash/.Setup -a app.tvunsplash.SETUP'
if ($Key) { $line += ' --es key ' + (Quote $Key) }
if ($PerDay -gt 0) { $line += " --ei per_day $PerDay" }
if ($Topics) { $line += ' --es topics ' + (Quote $Topics) }
if ($Collections) { $line += ' --es collections ' + (Quote $Collections) }
if ($PlaceOnly) { $line += ' --ez place_only true' }
if ($NoCredit) { $line += ' --ez credit false' }
& $Adb -s $s shell $line
Write-Host "Done. In a minute: $Adb -s $s logcat -d -s UnsplashWall:*"
