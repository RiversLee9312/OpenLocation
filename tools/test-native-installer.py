"""Exercise the real companion installer guards on an authorized device, without installing.

Only temporary test inputs are pushed. Permission-setting functions are replaced with
markers; no module files, mounts, service state or SELinux policy are changed.
"""
import argparse
import json
from pathlib import Path
import shlex
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default=str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'))
parser.add_argument('--serial', required=True)
parser.add_argument('--profile', required=True)
parser.add_argument('--output', type=Path, default=Path('.build-tmp/installer-guards.txt'))
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
adb = [args.adb, '-s', args.serial]
remote = '/data/local/tmp/openlocation-installer-tests'
profile = next(p for p in json.loads((root / 'hook-runtime/src/main/assets/device_profiles.json').read_text())['profiles'] if p['id'] == args.profile)
subprocess.run(adb + ['shell', 'mkdir', '-p', remote], check=True)
subprocess.run(adb + ['push', str(root / 'magisk-module/customize.sh'), remote + '/customize.sh'], check=True)
with tempfile.TemporaryDirectory() as temp:
    props = Path(temp) / 'device-profile.properties'
    keys = ('model', 'device', 'sdk', 'fingerprint', 'sensorServiceSha256', 'incremental')
    props.write_text(''.join(f'{key}={profile[key]}\n' for key in keys if key in profile), encoding='utf-8', newline='\n')
    subprocess.run(adb + ['push', str(props), remote + '/device-profile.properties'], check=True)

harness = r'''
KSU=false
ui_print() { printf '%s\n' "$*"; }
abort() { printf 'ABORT: %s\n' "$*"; exit 42; }
set_perm_recursive() { echo PERMISSIONS_REACHED; }
set_perm() { :; }
getprop() {
    case "$MODE:$1" in
        model:ro.product.model|device:ro.product.device|sdk:ro.build.version.sdk|fingerprint:ro.build.fingerprint|incremental:ro.build.version.incremental)
            echo deliberately-mismatched-test-value ;;
        *) /system/bin/getprop "$@" ;;
    esac
}
sha256sum() {
    if [ "$MODE" = sensor ]; then
        echo '0000000000000000000000000000000000000000000000000000000000000000  /system/lib64/libsensorservice.so'
    else /system/bin/sha256sum "$@"; fi
}
sed() {
    if [ "$MODE" = missing_hash ] && [ "$2" = 's/^sensorServiceSha256=//p' ]; then
        return 0
    fi
    /system/bin/sed "$@"
}
. "$MODPATH/customize.sh"
'''
cases = {
    'valid': None,
    'model': 'Device model mismatch',
    'device': 'Device codename mismatch',
    'sdk': 'Android API mismatch',
    'fingerprint': 'System fingerprint mismatch',
    'sensor': 'SensorService binary mismatch',
    'missing_hash': 'Missing SensorService compatibility hash',
}
reports = []
if profile.get('incremental'):
    cases['incremental'] = 'System incremental mismatch'
for mode, expected in cases.items():
    script = f'MODE={shlex.quote(mode)}\nMODPATH={shlex.quote(remote)}\n' + harness
    result = subprocess.run(adb + ['shell', 'sh', '-s'], input=script.encode(),
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=20)
    text = result.stdout.decode('utf-8', errors='replace')
    if expected is None:
        passed = result.returncode == 0 and 'PERMISSIONS_REACHED' in text and 'ABORT:' not in text
    else:
        passed = result.returncode == 42 and f'ABORT: {expected}' in text and 'PERMISSIONS_REACHED' not in text
    reports.append(f'{mode}: {"PASS" if passed else "FAIL"} (exit {result.returncode})\n{text}')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text('\n'.join(reports), encoding='utf-8')
    print(reports[-1], flush=True)
    if not passed:
        raise SystemExit(1)
