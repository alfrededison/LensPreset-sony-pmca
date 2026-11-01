#!/usr/bin/env bash
# One pass over everything that needs a real body, driven over adb (OpenMemories-Tweak's adb, over
# Wi-Fi or USB). No keys are pressed blind.
#
#   tools/camera-test.sh [--photos] [serial]     serial: e.g. 192.168.1.68:5555; default $CAMERA,
#                                                else the only device
#
# Quick (about two minutes): every setting is read back from the camera after it is written, so the
# report says whether the camera really holds it, not only that the call went through.
# --photos (about three minutes): also takes one picture per preset and checks its EXIF and Sony
# MakerNotes — what the camera really shot with — against what the app set (needs exiftool; point the
# camera at something it can focus on). The pictures stay on the card: delete them on the camera,
# since deleting over adb can confuse its picture database.
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
if [ "${1:-}" = "--photos" ]; then PHOTOS=1; shift; fi
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

[ -f "$APK" ] || { echo "no $APK: run ./build.sh first" >&2; exit 1; }
a get-state >/dev/null 2>&1 || { echo "no device (serial: ${SERIAL:-any})" >&2; exit 1; }
mkdir -p "$OUT"
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
shoot_and_check() {
  local p=$1 before after new
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
  a pull /data/local/tmp/lenspreset-exif.jpg "$OUT/$p.jpg" >/dev/null 2>&1
  a shell rm /data/local/tmp/lenspreset-exif.jpg >/dev/null 2>&1
  say "  photo $new"
  exiftool -j -q -q -ExposureTime# -FNumber# -ExposureCompensation# -ISO -ExposureProgram -MeteringMode \
    -FocusMode -AFAreaMode -CreativeStyle -SoftSkinEffect -FacesDetected# -ReleaseMode "$OUT/$p.jpg" > "$OUT/$p.exif.json" 2>/dev/null || true
  python3 tools/camera-test/exif_check.py "$OUT/$p.exif.json" "$OUT/$p.report.txt" | tee -a "$OUT/summary.txt" || true
}

cmd() { a shell am broadcast -a com.lenspreset.debug --es cmd "$1" ${2:+--es arg "$2"} >/dev/null; }

DEBUG_BACKUP=""
restore() {
  set +e
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
a install -r "$APK" 2>&1 | tail -1 | tee -a "$OUT/summary.txt"

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

# Every bundled preset once; then shutter both ways and manual mode.
PRESETS="group portrait-bokeh portrait-environment silhouette high-key kids-action indoor-low-light \
zz-test-shutter-60 kids-action zz-test-manual"
for p in $PRESETS; do
  a logcat -c
  cmd apply "$p"
  # The report, or the app saying at once that it cannot apply (no camera, unknown preset).
  wait_log "apply-end $p|adb command: (no camera|no preset)" 25 || true
  if applog | grep -q "apply-end $p"; then
    say "== $p"
    show 'apply |OK |FAILED|PARTIAL|UNSUPPORTED|MISMATCH|status'
    applog | sed -n "/apply $p /,/apply-end $p/p" > "$OUT/$p.report.txt" || true
    if [ "$PHOTOS" = 1 ]; then shoot_and_check "$p"; fi
  else
    say "== $p: FAIL, no apply report"
    show 'adb command'
  fi
done

say "== probe"
a logcat -c
cmd probe
if wait_log 'probe (saved|failed)' 20; then
  show 'probe (saved|failed)'
  a pull "$DIR/PROBE.TXT" "$OUT/PROBE.TXT" >/dev/null 2>&1 && say "  copied to $OUT/PROBE.TXT"
else
  say "  FAIL: no probe result in 20 s"
fi

say "== result"
fails=$(grep -c -E 'FAILED|PARTIAL|FAIL|MISMATCH' "$OUT/summary.txt" || true)
say "  ${fails} problem line(s); details above and in $OUT/logcat.txt"
