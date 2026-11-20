#!/usr/bin/env bash
# Read-only inspection of the head unit over adb. Changes nothing on the screen.
#   tools/probe.sh            the connected device (adb devices)
#   tools/probe.sh 192.168.1.20        connects over Wi-Fi first (port 5555, «adb tcpip»)
#   tools/probe.sh 192.168.1.20:41235  the port «التصحيح اللاسلكي» shows (after adb pair)
# Writes:
#   device/raw/<time>/   everything as read (not committed: may hold serial numbers)
#   device/profile.json  the summary the preview and the app are built from (committed, no identifiers)
#   device/tw-components.txt  what the screen's own apps (com.tw.*) accept from outside (committed)
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
command -v adb >/dev/null || { echo "adb غير موجود. ثبّت Android platform-tools أولًا."; exit 1; }
if [ $# -ge 1 ]; then case "$1" in *:*) adb connect "$1" >/dev/null;; *) adb connect "$1:5555" >/dev/null;; esac; fi
[ "$(adb get-state 2>/dev/null)" = "device" ] || { echo "لا توجد شاشة متصلة. فعّل تصحيح USB ثم: tools/probe.sh <IP الشاشة>"; exit 2; }

TS=$(date +%Y%m%d-%H%M%S); RAW="$ROOT/device/raw/$TS"; mkdir -p "$RAW"
S(){ adb shell "$@" 2>/dev/null | tr -d '\r'; }
# identifiers never leave device/raw
SECRET='serial|imei|iccid|cpuid|meid|imsi|mac|bt.*addr|wifi.*addr|bdaddr|uuid|android_id'

S getprop | grep -viE "$SECRET" > "$RAW/getprop.txt"
S wm size > "$RAW/wm.txt"; S wm density >> "$RAW/wm.txt"
S cat /proc/meminfo | head -3 > "$RAW/meminfo.txt"
S cat /proc/cpuinfo | grep -viE "$SECRET" > "$RAW/cpuinfo.txt"
S pm list packages > "$RAW/packages.txt"
S pm list features > "$RAW/features.txt"
S cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$RAW/home.txt"
S settings get global enable_freeform_support > "$RAW/freeform.txt"
S settings get global force_resizable_activities >> "$RAW/freeform.txt"
S dumpsys media.camera > "$RAW/camera.txt"
S ls /dev/ | grep -E '^video' > "$RAW/video-devices.txt"
S dumpsys media_session > "$RAW/media_session.txt"
S dumpsys display | grep -m2 -E 'mBaseDisplayInfo|DisplayDeviceInfo' > "$RAW/display.txt"
S cmd notification allowed_listeners > "$RAW/listeners.txt"
adb exec-out screencap -p > "$RAW/screen.png" 2>/dev/null

# the screen's own apps: what each one accepts (actions of its activities, receivers, services)
: > "$ROOT/device/tw-components.txt"
for p in $(sed -n 's/^package://p' "$RAW/packages.txt" | grep -E '^com\.tw\.|radio|camera|avm|canbus|carinfo' | sort -u); do
  S dumpsys package "$p" | grep -viE "$SECRET" > "$RAW/pkg-$p.txt"
  {
    echo "== $p"
    S cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$p" | tail -1 | sed 's/^/launch: /'
    grep -E '^\s+Action: "' "$RAW/pkg-$p.txt" | sed 's/^ *//' | sort -u
    grep -E '^\s+[0-9a-f]+ [a-zA-Z0-9_.]+/[a-zA-Z0-9_.$]+' "$RAW/pkg-$p.txt" | awk '{print "component: "$2}' | sort -u
  } >> "$ROOT/device/tw-components.txt"
done

# ---- the summary ----
prop(){ grep -F "[$1]" "$RAW/getprop.txt" | head -1 | sed 's/.*\]: \[\(.*\)\]/\1/'; }
j(){ printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
has(){ grep -qx "package:$1" "$RAW/packages.txt" && echo true || echo false; }
SIZE=$(grep -m1 -oE '[0-9]+x[0-9]+' "$RAW/wm.txt" | tail -1)
OVR=$(grep -i 'override size' "$RAW/wm.txt" | grep -oE '[0-9]+x[0-9]+'); [ -n "$OVR" ] && SIZE=$OVR
DENS=$(grep -oE '[0-9]+' "$RAW/wm.txt" | tail -1)
RAM=$(awk '/MemTotal/{print int($2/1024)}' "$RAW/meminfo.txt")
FF_FEATURE=$(grep -q 'freeform_window_management' "$RAW/features.txt" && echo true || echo false)
FF_SETTING=$(head -1 "$RAW/freeform.txt")
CAMS=$(grep -m1 -oE 'Number of camera devices: [0-9]+' "$RAW/camera.txt" | grep -oE '[0-9]+$')
VIDEO=$(wc -l < "$RAW/video-devices.txt" | tr -d ' ')
HOME_PKG=$(tail -1 "$RAW/home.txt")
TW=$(sed -n 's/^package://p' "$RAW/packages.txt" | grep -E '^com\.tw\.' | sort | sed 's/.*/"&"/' | paste -sd, -)
NAV=""
for n in com.google.android.apps.maps com.waze com.here.app.maps com.sygic.aura net.osmand com.huawei.maps.app ru.yandex.yandexnavi; do
  [ "$(has $n)" = true ] && NAV="$NAV${NAV:+,}\"$n\""
done
MEDIA=$(grep -oE 'package=[a-zA-Z0-9_.]+' "$RAW/media_session.txt" | cut -d= -f2 | sort -u | sed 's/.*/"&"/' | paste -sd, -)

cat > "$ROOT/device/profile.json" <<JSON
{
 "probed": "$TS",
 "android_sdk": "$(j "$(prop ro.build.version.sdk)")",
 "android_release": "$(j "$(prop ro.build.version.release)")",
 "model": "$(j "$(prop ro.product.model)")",
 "board": "$(j "$(prop ro.board.platform)")",
 "hardware": "$(j "$(prop ro.hardware)")",
 "screen": "$SIZE",
 "density": "$DENS",
 "ram_mb": ${RAM:-0},
 "freeform": {"feature": $FF_FEATURE, "setting": "$(j "$FF_SETTING")"},
 "home": "$(j "$HOME_PKG")",
 "cameras_android": ${CAMS:-0},
 "video_devices": ${VIDEO:-0},
 "tw_packages": [${TW}],
 "nav_apps": [${NAV}],
 "media_sessions": [${MEDIA}],
 "google_play": $(has com.android.vending),
 "youtube": $(has com.google.android.youtube)
}
JSON
echo "تم. الملخص: device/profile.json  التفاصيل: device/raw/$TS/"
cat "$ROOT/device/profile.json"
