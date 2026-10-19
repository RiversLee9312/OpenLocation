"""Build the application license catalog from checked-in primary license texts."""
import json
from pathlib import Path

root = Path(__file__).resolve().parent.parent
def read(path): return (root/path).read_text(encoding='utf-8')
entries = [
    dict(title='OpenLocation', license='Apache-2.0', text='Copyright 2026 OpenLocation contributors.\n\n'+read('LICENSE')),
    dict(title='AndroidX, Jetpack Compose and Material Symbols', license='Apache-2.0', text='Copyright The Android Open Source Project and Google LLC.\n\n'+read('third-party/material-symbols/LICENSE')),
    dict(title='Kotlin, kotlinx.coroutines and kotlinx.serialization', license='Apache-2.0', text='Copyright JetBrains s.r.o. and respective contributors.\n\n'+read('LICENSE')),
    dict(title='MapLibre Native Android 11.11.0', license='BSD-2-Clause and bundled component licenses', text=read('third-party/maplibre/LICENSE')+'\n\n'+read('third-party/maplibre/ANDROID-LICENSES.md')),
    dict(title='XZ for Java 1.10', license='0BSD', text=read('third-party/xz-java/LICENSE')),
    dict(title='SGP4 (aholinch/sgp4)', license='Unlicense', text=read('third-party/sgp4/LICENSE')),
    dict(title='GNSS orbital data', license='CelesTrak source attribution', text='GNSS general-perturbations OMM snapshot retrieved from CelesTrak on 2026-10-19.\nhttps://celestrak.org/NORAD/elements/gp.php?GROUP=gnss&FORMAT=json\n\nThe orbital propagation and measurement noise are software models; CelesTrak does not endorse this application.'),
    dict(title='OpenStreetMap', license='Open Database License (ODbL)', text='Map data © OpenStreetMap contributors.\nhttps://www.openstreetmap.org/copyright\nhttps://opendatacommons.org/licenses/odbl/1-0/\n\nRaster tiles are requested on demand; no offline tile distribution is included.'),
]
for component in ('libcxx','libcxxabi','libunwind','compiler-rt'):
    entries.append(dict(title=component+' (Android NDK runtime)',license='Apache-2.0 WITH LLVM-exception; retained legacy notices',text=read('third-party/llvm/'+component+'-LICENSE.TXT')))
destination = root/'app/src/main/assets/licenses.json'
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(entries,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
probe=root/'probe-app/src/main/assets/OPEN_SOURCE_NOTICES.txt'
probe.parent.mkdir(parents=True,exist_ok=True)
probe.write_text('OpenLocation project notices; components differ between packages.\n\n'+'\n\n'.join(entry['title']+'\n'+entry['text'] for entry in entries),encoding='utf-8')
(root/'magisk-module/LICENSE').write_text(read('LICENSE'),encoding='utf-8')
(root/'magisk-module/RUNTIME-NOTICES.txt').write_text('\n\n'.join(entry['title']+'\n'+entry['text'] for entry in entries if 'NDK runtime' in entry['title']),encoding='utf-8')
print(f'Built {len(entries)} license entries')
