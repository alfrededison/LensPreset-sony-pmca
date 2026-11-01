#!/usr/bin/env bash
# One pass over everything that needs a real body, driven over adb (OpenMemories-Tweak's adb, over
# Wi-Fi or USB). No keys are pressed blind.
#
#   tools/camera-test.sh [--photos | --face] [--skip-install] [serial]
#
# serial: e.g. 192.168.1.68:5555; default $CAMERA, else the only device. --skip-install tests the app
# already on the camera.
#
# Quick (about two minutes): every setting is read back from the camera after it is written, so the
# report says whether the camera really holds it, not only that the call went through.
# --photos (about three minutes): also takes one picture per preset and checks its EXIF and Sony
# MakerNotes — what the camera really shot with — against what the app set (needs exiftool; point the
# camera at something it can focus on). The pictures stay on the card: delete them on the camera,
# since deleting over adb can confuse its picture database.
# --face (about a minute, run it from a terminal): Soft Skin only, which the body applies to detected
# faces alone. Applies portrait-bokeh, waits for Enter while you hold a face in the frame (a person,
# or a portrait on a phone or screen), shoots and checks the photo; up to three tries.
#
# What it does:
#   1. installs out/LensPreset.apk (build it first) and puts on the card, for the run only:
#      LENSPRST/DEBUG.TXT with "adb-control" (the app then takes commands from adb) and the test
#      presets in tools/camera-test/ (shutter priority 1/60, manual f/4 1/200);
#   2. starts the app, keeps the camera from sleeping, applies every preset by id and waits for each
#      apply report — shutter is stepped both ways (1/500 -> 1/60 -> 1/500) and manual sets both;
#   3. runs the camera probe and copies PROBE.TXT;
#   4. puts everything back: auto power-off, DEBUG.TXT as it was, test presets removed, app closed.
#
# Results land in out/camera-test/<time>/: summary.txt, logcat.txt, PROBE.TXT (and, with --photos, the
# first 256 KB of each picture, which hold its EXIF).
# Environment: ADB (default: the SDK's adb), CARD (memory card path on the device; /mnt/sdcard on Sony
# bodies, /sdcard on an emulator).
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_SDK:=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}}"
ADB=${ADB:-$ANDROID_SDK/platform-tools/adb}
[ -x "$ADB" ] || ADB=$(command -v adb)
CARD=${CARD:-/mnt/sdcard}
PHOTOS=0
FACE=0
INSTALL=1
while [ $# -gt 0 ]; do
  case "$1" in
    --photos) PHOTOS=1 ;;
    --face) PHOTOS=1; FACE=1 ;;
    --skip-install) INSTALL=0 ;;
    -*) echo "unknown option $1" >&2; exit 1 ;;
    *) break ;;
  esac
  shift
done
SERIAL=${1:-${CAMERA:-}}
APK=out/LensPreset.apk
APP=com.lenspreset/.app.MainActivity
DIR="$CARD/LENSPRST"
OUT="out/camera-test/$(date +%Y%m%d-%H%M%S)"

a() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }
say() { printf '%s\n' "$*" | tee -a "$OUT/summary.txt"; }
# The app's log; the camera's adb (Android 2.3) ends every line with \r\n, which "$" patterns trip on.
applog() { a logcat -d -s LensPreset:I 2>/dev/null | tr -d '\r'; }
# The app's log lines matching $1, without logcat's prefix, into the summary; nothing matching is fine.
show() { { applog | grep -E "$1" | sed 's/^.*LensPreset[^:]*: /  /'; } | tee -a "$OUT/summary.txt" || true; }

[ "$INSTALL" = 0 ] || [ -f "$APK" ] || { echo "no $APK: run ./build.sh first" >&2; exit 1; }
a get-state >/dev/null 2>&1 || { echo "no device (serial: ${SERIAL:-any})" >&2; exit 1; }
mkdir -p "$OUT"
if [ "$FACE" = 1 ] && { ! command -v exiftool >/dev/null || [ ! -t 0 ]; }; then
  echo "--face needs exiftool (brew install exiftool) and a terminal to press Enter in" >&2
  exit 1
fi
if [ "$PHOTOS" = 1 ] && ! command -v exiftool >/dev/null; then
  echo "exiftool not found (brew install exiftool): photo checks skipped"
  PHOTOS=0
fi

# Waits until the app's log (logcat tag LensPreset) has a line matching $1; $2 seconds at most.
wait_log() {
  local i
  for i in $(seq 1 "$2"); do
    if applog | grep -q -E "$1"; then return 0; fi
    sleep 1
  done
  return 1
}
# The camera's adb shell (Android 2.3) prints errors on stdout and always exits 0, so a file's
# presence is asked for in a form that prints only "yes".
exists() { a shell "ls '$1' >/dev/null 2>&1 && echo yes" 2>/dev/null | tr -d '\r' | grep -q '^yes$'; }
# Every JPEG on the card, as full paths; nothing (not an error) before the first picture, when the
# card has no DCIM yet.
jpegs() {
  local d
  exists "$CARD/DCIM" || return 0
  { for d in $(a shell ls "$CARD/DCIM" 2>/dev/null | tr -d '\r'); do
      a shell ls "$CARD/DCIM/$d" 2>/dev/null | tr -d '\r' | grep -i '\.JPG$' | sed "s|^|$CARD/DCIM/$d/|" || true
    done; } | sort || true
}

# Shoots, finds the new picture, copies the part holding its EXIF and compares it with preset $1's report.
# The picture and its EXIF are saved as $2 (default $1).
shoot_and_check() {
  local p=$1 name=${2:-$1} before after new
  before=$(jpegs)
  a logcat -c
  cmd shoot
  if ! wait_log '^.*shot ' 15; then say "  PHOTO FAIL: no shot reported"; return; fi
  show 'shot '
  for _ in 1 2 3 4 5; do
    sleep 1
    after=$(jpegs)
    new=$(comm -13 <(echo "$before") <(echo "$after") | tail -1)
    [ -n "$new" ] && break
  done
  if [ -z "$new" ]; then say "  PHOTO FAIL: no new JPEG on the card (RAW only?)"; return; fi
  a shell dd if="$new" of=/data/local/tmp/lenspreset-exif.jpg bs=65536 count=4 >/dev/null 2>&1
  a pull /data/local/tmp/lenspreset-exif.jpg "$OUT/$name.jpg" >/dev/null 2>&1
  a shell rm /data/local/tmp/lenspreset-exif.jpg >/dev/null 2>&1
  say "  photo $new"
  exiftool -j -q -q -ExposureTime# -FNumber# -ExposureCompensation# -ISO -ExposureProgram -MeteringMode \
    -FocusMode -AFAreaMode -CreativeStyle -SoftSkinEffect# -FacesDetected# -ReleaseMode "$OUT/$name.jpg" > "$OUT/$name.exif.json" 2>/dev/null || true
  python3 tools/camera-test/exif_check.py "$OUT/$name.exif.json" "$OUT/$p.report.txt" | tee -a "$OUT/summary.txt" || true
}

# Soft Skin needs a face: asks for one before each shot, and shoots again while the photo has none.
face_shots() {
  local p=$1 try faces
  for try in 1 2 3; do
    read -r -p "  Hold a face in the frame until the camera shows a face box, then press Enter (try $try/3) " _
    shoot_and_check "$p" "$p-try$try"
    faces=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[0].get("FacesDetected", 0))' \
      "$OUT/$p-try$try.exif.json" 2>/dev/null || echo 0)
    if [ "${faces:-0}" -gt 0 ]; then return; fi
    say "  no face in the photo"
  done
}

cmd() { a shell am broadcast -a com.lenspreset.debug --es cmd "$1" ${2:+--es arg "$2"} >/dev/null; }

DEBUG_BACKUP=""
restore() {
  set +e
  cmd test-look restore 2>/dev/null
  cmd awake off 2>/dev/null
  cmd exit 2>/dev/null
  a shell rm "$DIR/PRESETS/T60.JSN" "$DIR/PRESETS/TMAN.JSN" >/dev/null 2>&1
  if [ -n "$DEBUG_BACKUP" ]; then a push "$DEBUG_BACKUP" "$DIR/DEBUG.TXT" >/dev/null 2>&1
  else a shell rm "$DIR/DEBUG.TXT" >/dev/null 2>&1; fi
  a logcat -d -v time > "$OUT/logcat.txt" 2>/dev/null
}
trap restore EXIT

say "camera test $(date '+%Y-%m-%d %H:%M'), device ${SERIAL:-default}, $(grep -o 'versionName="[^"]*"' app/AndroidManifest.xml)"
say "== install"
if [ "$INSTALL" = 1 ]; then
  a install -r "$APK" 2>&1 | tail -1 | tee -a "$OUT/summary.txt"
else
  say "  skipped: testing the app already on the camera"
fi

a shell mkdir "$DIR" "$DIR/PRESETS" >/dev/null 2>&1 || true
if exists "$DIR/DEBUG.TXT" && a pull "$DIR/DEBUG.TXT" "$OUT/DEBUG.TXT.before" >/dev/null 2>&1; then
  DEBUG_BACKUP="$OUT/DEBUG.TXT.before"
  { cat "$DEBUG_BACKUP"; echo; echo adb-control; } > "$OUT/DEBUG.TXT"
else
  echo adb-control > "$OUT/DEBUG.TXT"
fi
a push "$OUT/DEBUG.TXT" "$DIR/DEBUG.TXT" >/dev/null 2>&1
a push tools/camera-test/T60.JSN "$DIR/PRESETS/T60.JSN" >/dev/null 2>&1
a push tools/camera-test/TMAN.JSN "$DIR/PRESETS/TMAN.JSN" >/dev/null 2>&1

say "== start"
a logcat -c
a shell am start -n "$APP" >/dev/null
sleep 2
# While Sony's Application List is in front it refuses activity starts from adb ("Activity start
# request from 2000 stopped"); then the app has to be opened by hand. DEBUG.TXT is already on the
# card, so adb control comes on all the same.
if a logcat -d -s ActivityManager 2>/dev/null | tr -d '\r' | grep -q 'start request from 2000 stopped'; then
  say "  The camera refused to start the app from adb: open LensPreset on the camera now (waiting 60 s)"
  wait_log ': ready$' 60 || { say "FAIL: the app was not opened"; exit 1; }
else
  wait_log ': ready$' 30 || { say "FAIL: the app did not get to ready in 30 s"; exit 1; }
fi
wait_log 'adb control on' 5 || { say "FAIL: adb control is off (DEBUG.TXT not read?)"; exit 1; }
show 'create|loaded:|camera open|open:|ready'
{ a logcat -d -s ActivityManager:I | tr -d '\r' | grep 'Displayed com.lenspreset' | sed 's/^.*Displayed /  displayed /'; } 2>/dev/null | tee -a "$OUT/summary.txt" || true
cmd awake on
cmd test-look managed
wait_log 'test look managed' 5 || { say "FAIL: this app cannot enable look writes for the test"; exit 1; }
show 'test look managed'

# Every bundled preset once; then shutter both ways and manual mode.
PRESETS="group portrait-bokeh portrait-environment silhouette high-key kids-action indoor-low-light \
zz-test-shutter-60 kids-action zz-test-manual"
if [ "$FACE" = 1 ]; then PRESETS=portrait-bokeh; fi
for p in $PRESETS; do
  a logcat -c
  cmd apply "$p"
  # The report, or the app saying at once that it cannot apply (no camera, unknown preset).
  wait_log "apply-end $p|adb command: (no camera|no preset)" 25 || true
  if applog | grep -q "apply-end $p"; then
    say "== $p"
    show 'apply |OK |FAILED|PARTIAL|UNSUPPORTED|MISMATCH|status'
    applog | sed -n "/apply $p /,/apply-end $p/p" > "$OUT/$p.report.txt" || true
    if [ "$FACE" = 1 ] && ! grep -q -E 'OK soft_skin low(,|$)' "$OUT/$p.report.txt"; then
      say "  FAIL: Soft Skin Low was not applied; face photo checks would be inconclusive"
      exit 1
    fi
    if [ "$FACE" = 1 ]; then face_shots "$p"
    elif [ "$PHOTOS" = 1 ]; then shoot_and_check "$p"; fi
  else
    say "== $p: FAIL, no apply report"
    show 'adb command'
  fi
done

if [ "$FACE" = 0 ]; then
  say "== probe"
  a logcat -c
  cmd probe
  if wait_log 'probe (saved|failed)' 20; then
    show 'probe (saved|failed)'
    a pull "$DIR/PROBE.TXT" "$OUT/PROBE.TXT" >/dev/null 2>&1 && say "  copied to $OUT/PROBE.TXT"
  else
    say "  FAIL: no probe result in 20 s"
  fi
fi

say "== result"
fails=$(grep -c -E 'FAILED|PARTIAL|FAIL|MISMATCH' "$OUT/summary.txt" || true)
say "  ${fails} problem line(s); details above and in $OUT/logcat.txt"
