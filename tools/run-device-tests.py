"""Run actual consumer instrumentation and save complete ADB output without shell redirection."""
import argparse
from pathlib import Path
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default=str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'))
parser.add_argument('--classes', default='dev.openlocation.probe.HookIntegrationTest,dev.openlocation.probe.ExtendedChannelsTest')
parser.add_argument('--output', type=Path, default=Path('.build-tmp/device-tests.txt'))
parser.add_argument('--component', default='dev.openlocation.probe.test/androidx.test.runner.AndroidJUnitRunner')
args = parser.parse_args()
command = [args.adb, 'shell', 'am', 'instrument', '-w', '-e', 'class', args.classes, args.component]
result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=150)
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_bytes(result.stdout)
text = result.stdout.decode('utf-8',errors='replace')
print(text)
sys.exit(0 if result.returncode == 0 and 'OK (' in text and 'FAILURES' not in text else 1)
