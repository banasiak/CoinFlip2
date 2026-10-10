#!/usr/bin/env bash
# Retakes the Play Store screenshots on a phone connected over adb, seven per language, framed the way
# Android Studio frames them, into store/screenshots/<language>/1.png to 7.png. store/README.md says
# what it needs and what it changes on the phone while it runs. Everything it changes is put back
# when it exits, including when it fails.
#
#   store/scripts/screenshots.sh                         every language in store_listing.xml
#   LANGS="en-US de-DE" store/scripts/screenshots.sh     only these
#   OUT=/tmp/shots store/scripts/screenshots.sh          somewhere other than store/screenshots
#   FORCE=1 store/scripts/screenshots.sh                 keep a language even if its status bars differ
#
# ANDROID_SERIAL picks the phone when more than one is connected. ANDROID_HOME (or ANDROID_SDK_ROOT)
# is the SDK, for zipalign and apksigner. ANDROID_STUDIO is the Android Studio install the frame is
# taken from (default /opt/android/studio), and DEVICE_FRAME is the device art inside it (default
# pixel_9a), whose screen size the phone has to match.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
out=${OUT:-$root/store/screenshots}
studio=${ANDROID_STUDIO:-/opt/android/studio}
frame=${DEVICE_FRAME:-pixel_9a}
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
pkg=com.banasiak.coinflip.screenshots
activity=$pkg/com.banasiak.coinflip.AppActivity
keystore=$HOME/.android/debug.keystore
statusbar=150

die() { echo "screenshots.sh: $*" >&2; exit 1; }
say() { echo "== $*"; }
ui() { python3 -I "$here/ui.py" "$@"; }
sh_adb() { adb shell "$@" | tr -d '\r'; }

# --- what this needs --------------------------------------------------------------------------

command -v adb >/dev/null || die "adb is not on the PATH"
command -v python3 >/dev/null || die "python3 is not on the PATH"
if command -v magick >/dev/null; then im=(magick); cmp=(magick compare); idf=(magick identify)
elif command -v convert >/dev/null; then im=(convert); cmp=(compare); idf=(identify)
else die "ImageMagick is not installed"; fi
"${im[@]}" -list format 2>/dev/null | grep -qE '^ *WEBP' || die "this ImageMagick cannot read WebP"
[ -n "$sdk" ] || die "set ANDROID_HOME to the Android SDK"
bt=$(ls -d "$sdk"/build-tools/* 2>/dev/null | sort -V | tail -1)
[ -x "$bt/apksigner" ] && [ -x "$bt/zipalign" ] || die "no build-tools with zipalign and apksigner under $sdk"
[ -f "$keystore" ] || die "no debug keystore at $keystore: build any debug app once to create it"
art=$(find "$studio" -type d -path "*/device-art-resources/$frame" -print -quit 2>/dev/null)
[ -n "$art" ] || die "no $frame device art under $studio: set ANDROID_STUDIO or DEVICE_FRAME"
adb get-state >/dev/null 2>&1 || die "no phone connected, or more than one: set ANDROID_SERIAL"

langs=${LANGS:-$(python3 -I -c 'import sys, xml.etree.ElementTree as ET; print(" ".join(e.tag for e in ET.parse(sys.argv[1]).getroot()))' "$root/store/store_listing.xml")}
for lang in $langs; do python3 -I "$here/labels.py" "$lang" >/dev/null || die "no labels for $lang"; done

# the frame: where the screen sits on it, how big the screen is, and the two images that make it
read -r sx sy < <(awk '$1 == "part2" {p = 1} p && $1 == "x" {x = $2} p && $1 == "y" {print x, $2; exit}' "$art/layout") || true
read -r sw sh < <(awk '$1 == "display" {d = 1} d && $1 == "width" {w = $2} d && $1 == "height" {print w, $2; exit}' "$art/layout") || true
back=$art/$(awk '$1 == "image" {print $2; exit}' "$art/layout")
mask=$art/$(awk '$1 == "mask" {print $2; exit}' "$art/layout")
[ -f "$back" ] && [ -f "$mask" ] && [ -n "${sx:-}" ] && [ -n "${sw:-}" ] || die "could not read $art/layout"

size=$(sh_adb wm size | awk '/Override size/ {o = $3} /Physical size/ {p = $3} END {print (o ? o : p)}')
[ "$size" = "${sw}x${sh}" ] || die "the phone's screen is $size, and the $frame frame needs ${sw}x${sh}"
case $(sh_adb settings get system font_scale) in
  1.0 | null) ;;
  *) echo "warning: the phone's font scale is not 1.0, so the text will not match earlier shots" >&2 ;;
esac

# --- put the phone back on the way out, however this ends --------------------------------------

work=$(mktemp -d)
night=$(sh_adb cmd uimode night | awk '{print $3}')
demo_allowed=$(sh_adb settings get global sysui_demo_allowed)
demo_on=$(sh_adb settings get global sysui_tuner_demo_on)

restore_setting() { # name, then the value read before: "null" means it was never set
  if [ "$2" = "null" ]; then adb shell settings delete global "$1" >/dev/null
  else adb shell settings put global "$1" "$2"; fi
}

restore() {
  set +e
  say "putting the phone back"
  adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1
  restore_setting sysui_tuner_demo_on "$demo_on"
  restore_setting sysui_demo_allowed "$demo_allowed"
  case $night in yes | no | auto) adb shell cmd uimode night "$night" >/dev/null ;; esac
  adb uninstall "$pkg" >/dev/null 2>&1
  rm -rf "$work"
}
trap restore EXIT

# --- build the side copy ------------------------------------------------------------------------

# a debug build so its preferences can be written through run-as, and a release build, signed with
# the same debug key so the two install over each other and keep them, for the Diagnostics shot:
# a debuggable app can run interpreted, which more than doubles the run time the shot shows
say "building the side copy"
(cd "$root" && ./gradlew -q -I "$here/side-copy.init.gradle" :coinflip:assembleDebug :coinflip:assembleRelease)
apks=$root/build/screenshots/coinflip/outputs/apk
debug_apk=$apks/debug/coinflip-debug.apk
"$bt/zipalign" -f -P 16 4 "$apks/release/coinflip-release-unsigned.apk" "$work/release-aligned.apk"
"$bt/apksigner" sign --ks "$keystore" --ks-pass pass:android --ks-key-alias androiddebugkey \
  --key-pass pass:android --out "$work/release.apk" "$work/release-aligned.apk" 2>/dev/null

# --- set the phone up ---------------------------------------------------------------------------

say "setting the phone up"
adb shell cmd uimode night yes >/dev/null
# demo mode, as Developer options' "System UI demo mode" sets it: a fixed clock, full signal and a
# full battery. The notification icons are hidden where SystemUI honours it; GrapheneOS's does not
adb shell settings put global sysui_demo_allowed 1
adb shell settings put global sysui_tuner_demo_on 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
# sent again before every shot: some builds, GrapheneOS's among them, let a real battery update
# replace the demo battery. A phone that is charging is drawn as charging regardless
demo_state() {
  demo clock -e hhmm 0500
  demo network -e wifi show -e level 4 -e fully true
  demo network -e mobile show -e datatype lte -e level 4 -e fully true
  demo battery -e level 100 -e plugged false
  demo notifications -e visible false
}
demo enter
demo_state
adb uninstall "$pkg" >/dev/null 2>&1 || true

# --- the screens --------------------------------------------------------------------------------

# Replaces the side copy's preferences with the state a shot needs: whether the streak shows, the
# heads and tails counts, the face the last flip landed on and the run it is part of. The keys are
# Setting's (coinflip/src/main/java/com/banasiak/coinflip/settings/Setting.kt), and the favorites are
# the coins the picker shot stars.
seed() {
  adb shell am force-stop "$pkg"
  adb shell run-as "$pkg" sh -c "'mkdir -p shared_prefs && cat > shared_prefs/${pkg}_preferences.xml'" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="schemaVersion" value="7" />
    <boolean name="streak" value="$1" />
    <long name="headsCount" value="$2" />
    <long name="tailsCount" value="$3" />
    <string name="streakValue">$4</string>
    <long name="streakCount" value="$5" />
    <long name="headsRecord" value="6" />
    <long name="tailsRecord" value="7" />
    <set name="favoriteCoins">
        <string>gw</string>
        <string>fl</string>
        <string>mn</string>
        <string>nm</string>
        <string>ny</string>
        <string>toonie</string>
        <string>netherlands</string>
        <string>claude</string>
        <string>random</string>
    </set>
</map>
XML
}

install() { timeout -k 5 120 adb install -r "$1" >/dev/null 2>&1 || die "could not install $1"; }
launch() { adb shell am start -W -n "$activity" >/dev/null; }

# waits for a screen, with the notification shade closed first: an open shade covers the app, and
# would otherwise end up in the shot
shown() { adb shell cmd statusbar collapse; ui wait "$@"; }

capture() {
  demo_state
  sleep 1 # the coin's artwork loads off the main thread, and can land after the text waited on
  adb exec-out screencap -p > "$work/raw-$1.png"
  [ "$("${idf[@]}" -format %wx%h "$work/raw-$1.png")" = "${sw}x${sh}" ] || die "shot $1 came out the wrong size"
}

# the status bar of a framed shot, which is what gets compared: a notification that arrives part-way
# through puts its icon on some shots and not others
bar() { "${im[@]}" "$1" -crop "${sw}x${statusbar}+${sx}+${sy}" +repage "$work/bar-$2.png"; }
same() { "${cmp[@]}" -metric AE "$work/bar-$1.png" "$work/bar-$2.png" null: 2>/dev/null; }

# Every language is compared with one reference: a set already in $out that this run is not
# retaking, English first, so a rerun of a few languages has to match the rest, or else the first
# language this run takes. The two sheets dim the status bar, so they have a reference of their own
ref=""
for candidate in en-US $(ls "$out" 2>/dev/null); do
  case " $langs " in *" $candidate "*) continue ;; esac
  if [ -f "$out/$candidate/1.png" ] && [ -f "$out/$candidate/5.png" ]; then
    ref=$candidate
    bar "$out/$ref/1.png" ref-screen
    bar "$out/$ref/5.png" ref-sheet
    break
  fi
done

done_langs=()
for lang in $langs; do
  say "taking $lang"
  declare -A L=()
  while IFS=$'\t' read -r name pattern; do L[$name]=$pattern; done < <(python3 -I "$here/labels.py" "$lang")

  # the debug build again after the last language's release build: only it can be seeded
  install "$debug_apk"
  adb shell cmd locale set-app-locales "$pkg" --locales "$lang" >/dev/null || die "could not set the side copy's language to $lang"

  seed false 0 0 UNKNOWN 0; launch; shown '^\?$' "${L[diagnostics]}"; capture 1
  seed false 4 6 HEADS 1; launch; shown "${L[heads]}" "${L[diagnostics]}"; capture 2
  ui tap "${L[settings]}"; shown "${L[coin_type]}"; capture 3
  ui tap "${L[coin_type]}"; shown "${L[favorites]}" '^Random Coin$'; capture 4
  seed true 17 20 TAILS 4; launch; shown "${L[tails]}" '^×4$'; capture 6
  ui tap "${L[about]}"; shown "${L[about_title]}"; capture 7
  # Diagnostics last: it needs the release build, which cannot be seeded once installed. The run is
  # over when TOTAL reads the run size the iterations button shows, in the locale's own format
  seed false 4 6 HEADS 1
  install "$work/release.apk"
  launch; shown "${L[diagnostics]}"; ui tap "${L[diagnostics]}"; shown "${L[diagnostics_title]}" "${L[iterations]}"
  total=$(ui exact "${L[iterations]}")
  shown "$total" "${L[seconds]}" -t 180; capture 5

  for n in 1 2 3 4 5 6 7; do
    "${im[@]}" "$back" "$work/raw-$n.png" -geometry "+$sx+$sy" -composite "$mask" -geometry "+$sx+$sy" -composite \
      -background black -alpha background -strip "$work/framed-$n.png"
  done
  if [ -z "$ref" ]; then
    ref=$lang
    bar "$work/framed-1.png" ref-screen
    bar "$work/framed-5.png" ref-sheet
  fi
  differ=()
  for n in 1 2 3 4 6; do bar "$work/framed-$n.png" "$n"; same ref-screen "$n" || differ+=("$n"); done
  for n in 5 7; do bar "$work/framed-$n.png" "$n"; same ref-sheet "$n" || differ+=("$n"); done
  if [ ${#differ[@]} -gt 0 ] && [ -z "${FORCE:-}" ]; then
    die "$lang: shots ${differ[*]} have a different status bar from $ref's, so $lang was not written. Done: ${done_langs[*]:-none}. Clear whatever posted the notification and rerun the rest with LANGS"
  fi

  mkdir -p "$out/$lang"
  cp "$work"/framed-[1-7].png "$out/$lang/"
  for n in 1 2 3 4 5 6 7; do mv "$out/$lang/framed-$n.png" "$out/$lang/$n.png"; done
  done_langs+=("$lang")
  echo "   wrote $out/$lang"
done
