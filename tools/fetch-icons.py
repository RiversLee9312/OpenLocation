"""Fetch Google Fonts Material Symbols Rounded and preserve their official vector paths."""
from pathlib import Path
import argparse
from urllib.request import urlopen
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
NAMES = 'route play_arrow pause stop add tune sensors settings directions_walk directions_run directions_bike directions_car check_circle open_in_new download upload delete arrow_back undo language dark_mode info close'.split()
parser=argparse.ArgumentParser()
parser.add_argument('names',nargs='*')
NAMES=parser.parse_args().names or NAMES
for name in NAMES:
    svg = urlopen(f'https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{name}/materialsymbolsrounded/{name}_24px.svg', timeout=30).read()
    (ROOT/'artwork'/f'{name}.svg').write_bytes(svg)
    node = ET.fromstring(svg)
    x,y,w,h = map(float,node.attrib['viewBox'].split())
    paths = ''.join(f'<path android:fillColor="#FF000000" android:pathData="{p.attrib["d"]}"/>' for p in node.iter() if p.tag.endswith('path'))
    vector = f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="{w:g}" android:viewportHeight="{h:g}"><group android:translateX="{-x:g}" android:translateY="{-y:g}">{paths}</group></vector>\n'
    if name in ('arrow_back','chevron_right','undo'):
        vector=vector.replace(' android:width=', ' android:autoMirrored="true" android:width=',1)
    (ROOT/'app/src/main/res/drawable'/f'ic_{name}.xml').write_text(vector, encoding='utf-8')
print(f'{len(NAMES)} official icons fetched')
