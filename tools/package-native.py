"""Package the native service companion from the exact controller APK, with its profile."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--profile', help='Exact profile ID; required when the APK contains multiple profiles')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
with zipfile.ZipFile(args.apk) as apk:
    profiles = json.loads(apk.read('assets/device_profiles.json'))
    candidates = [p for p in profiles['profiles'] if not args.profile or p['id'] == args.profile]
    if len(candidates) != 1:
        parser.error('Select exactly one profile with --profile: ' + ', '.join(p['id'] for p in profiles['profiles']))
    profile = candidates[0]
    library = apk.read('lib/arm64-v8a/libopenlocation.so')
args.output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED) as output:
    for source in sorted((root/'magisk-module').glob('*')):
        if source.is_file(): output.writestr(source.name, source.read_bytes())
    output.writestr('system/lib64/libopenlocation.so', library)
    output.writestr('device_profiles.json', json.dumps({'schema': profiles['schema'], 'profiles': [profile]}, indent=2)+'\n')
    keys = ('model','device','sdk','fingerprint','sensorServiceSha256','incremental')
    output.writestr('device-profile.properties', '\n'.join(f'{key}={profile[key]}' for key in keys if key in profile)+'\n')
    output.writestr('native.sha256', hashlib.sha256(library).hexdigest()+'  system/lib64/libopenlocation.so\n')
print(f'Packaged {args.output} for {profile["model"]} / {profile["device"]}')
