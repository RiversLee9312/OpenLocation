#!/system/bin/sh
ui_print "OpenLocation native service backend"
MODEL=$(sed -n 's/^model=//p' "$MODPATH/device-profile.properties")
DEVICE=$(sed -n 's/^device=//p' "$MODPATH/device-profile.properties")
FINGERPRINT=$(sed -n 's/^fingerprint=//p' "$MODPATH/device-profile.properties")
[ "$(getprop ro.product.model)" = "$MODEL" ] || abort "Device model mismatch"
[ "$(getprop ro.product.device)" = "$DEVICE" ] || abort "Device codename mismatch"
[ "$(getprop ro.build.fingerprint)" = "$FINGERPRINT" ] || abort "System fingerprint mismatch"
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
ui_print "Use LSPosed System Framework scope only. Restart required."
