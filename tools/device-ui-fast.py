"""Snapshot, click a unique observed label, or set its editable text (optional second argument)."""
from pathlib import Path
import subprocess
import sys

adb=str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe')
cmd=[adb,'shell','am','instrument','-w','-e','class','dev.openlocation.probe.UiInspectionTest','-e','ui_action','snapshot']
if len(sys.argv)>1:
    # adb shell transports a command string: quote the single label for the device shell.
    label="'"+sys.argv[1].replace("'", "'\\''")+"'"
    cmd+=['-e','ui_label',label]
if len(sys.argv)>2:
    value="'"+sys.argv[2].replace("'", "'\\''")+"'"
    cmd+=['-e','ui_text',value]
cmd+=['dev.openlocation.probe.test/androidx.test.runner.AndroidJUnitRunner']
out=subprocess.check_output(cmd,stderr=subprocess.STDOUT,timeout=25).decode('utf-8',errors='replace')
for line in out.splitlines():
    if line.startswith('INSTRUMENTATION_STATUS: stream='):
        print(line.split('stream=',1)[1])
    elif ' | Rect(' in line:
        print(line)
    elif 'Error in ' in line or line.startswith('java.lang.'):
        print(line)
if 'FAILURES' in out: raise SystemExit(1)
