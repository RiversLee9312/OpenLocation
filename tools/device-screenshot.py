"""Capture PNG pixels from ADB, tolerating Android 16 multi-display stdout diagnostics."""
import subprocess
import sys
from pathlib import Path

adb = Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
data = subprocess.check_output([str(adb), 'exec-out', 'screencap', '-p'])
start = data.find(b'\x89PNG\r\n\x1a\n')
if start < 0:
    raise SystemExit('ADB did not return PNG pixels')
path = Path(sys.argv[1]).resolve()
path.parent.mkdir(parents=True, exist_ok=True)
path.write_bytes(data[start:])
print(path)
