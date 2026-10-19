"""Package the native service companion from the exact controller APK, with its profile."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
with zipfile.ZipFile(args.apk) as apk:
    profiles = json.loads(apk.read('assets/device_profiles.json'))
    assert len(profiles['profiles']) == 1, 'Select a single device profile before packaging'
    profile = profiles['profiles'][0]
    library = apk.read('lib/arm64-v8a/libopenlocation.so')
args.output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED) as output:
    for source in sorted((root/'magisk-module').glob('*')):
        if source.is_file(): output.writestr(source.name, source.read_bytes())
    output.writestr('system/lib64/libopenlocation.so', library)
    output.writestr('device_profiles.json', json.dumps(profiles, indent=2)+'\n')
    output.writestr('device-profile.properties', '\n'.join(f'{key}={profile[key]}' for key in ('model','device','sdk','fingerprint'))+'\n')
    output.writestr('native.sha256', hashlib.sha256(library).hexdigest()+'  system/lib64/libopenlocation.so\n')
print(f'Packaged {args.output} for {profile["model"]} / {profile["device"]}')
