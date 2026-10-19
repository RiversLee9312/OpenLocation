"""Small ADB UI inspection helper for the connected development device."""
import subprocess
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

adb = str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe')
subprocess.run([adb,'shell','rm','-f','/sdcard/openlocation-ui.xml'],check=True)
dump = subprocess.run([adb,'shell','uiautomator','dump','/sdcard/openlocation-ui.xml'],check=True,capture_output=True,text=True)
if 'UI hierchary dumped to:' not in dump.stdout:
    raise SystemExit('Fresh UI dump unavailable: '+dump.stdout+dump.stderr)
xml = subprocess.check_output([adb,'exec-out','cat','/sdcard/openlocation-ui.xml'])
tree = ET.fromstring(xml)
parents = {child: parent for parent in tree.iter() for child in parent}
if len(sys.argv)>1:
    label = sys.argv[1]
    matches = [node for node in tree.iter('node') if node.get('text')==label or node.get('content-desc')==label]
    if len(matches)!=1:
        raise SystemExit(f'Expected one match for {label!r}, got {len(matches)}')
    node=matches[0]
    while node.get('clickable')!='true' and node in parents:
        node=parents[node]
    if node.get('clickable')!='true':
        raise SystemExit(f'No clickable ancestor for {label!r}')
    import re
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
    subprocess.run([adb,'shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
else:
    for node in tree.iter('node'):
        label=node.get('text') or node.get('content-desc')
        if not label and node.get('checkable')=='true':
            label=node.get('class')
        if label:
            print(f'{label} | {node.get("bounds")} | checked={node.get("checked")}')
