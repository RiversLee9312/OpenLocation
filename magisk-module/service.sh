#!/system/bin/sh
# Native library mount is provided by Magisk. No background injector or policy changes.
MODDIR=${0%/*}
umask 077
mkdir -p "$MODDIR/state"
{
    echo "api=$(getprop ro.build.version.sdk)"
    echo "abi=$(getprop ro.product.cpu.abi)"
    echo "selinux=$(getenforce)"
    echo "pagesize=$(getconf PAGESIZE)"
    echo "scope=system"
} > "$MODDIR/state/boot.txt"
