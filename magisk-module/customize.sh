#!/system/bin/sh
ui_print "OpenLocation native service backend"
MODEL=$(sed -n 's/^model=//p' "$MODPATH/device-profile.properties")
DEVICE=$(sed -n 's/^device=//p' "$MODPATH/device-profile.properties")
SDK=$(sed -n 's/^sdk=//p' "$MODPATH/device-profile.properties")
FINGERPRINT=$(sed -n 's/^fingerprint=//p' "$MODPATH/device-profile.properties")
INCREMENTAL=$(sed -n 's/^incremental=//p' "$MODPATH/device-profile.properties")
SENSOR_HASH=$(sed -n 's/^sensorServiceSha256=//p' "$MODPATH/device-profile.properties")
[ "$(getprop ro.product.model)" = "$MODEL" ] || abort "Device model mismatch"
[ "$(getprop ro.product.device)" = "$DEVICE" ] || abort "Device codename mismatch"
[ "$(getprop ro.build.version.sdk)" = "$SDK" ] || abort "Android API mismatch"
[ "$(getprop ro.build.fingerprint)" = "$FINGERPRINT" ] || abort "System fingerprint mismatch"
[ -z "$INCREMENTAL" ] || [ "$(getprop ro.build.version.incremental)" = "$INCREMENTAL" ] || abort "System incremental mismatch"
[ -n "$SENSOR_HASH" ] || abort "Missing SensorService compatibility hash"
ACTUAL_SENSOR_HASH=$(sha256sum /system/lib64/libsensorservice.so) || abort "Cannot read SensorService"
[ "${ACTUAL_SENSOR_HASH%% *}" = "$SENSOR_HASH" ] || abort "SensorService binary mismatch"
if [ "$KSU" = "true" ]; then
    [ -f /data/adb/metamodule/metamount.sh ] &&
        [ ! -f /data/adb/metamodule/disable ] &&
        [ ! -f /data/adb/metamodule/remove ] ||
        abort "KernelSU requires an active mounting metamodule; install it and restart first"
fi
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
ui_print "Use System Framework scope only in LSPosed/Vector. Restart required."
