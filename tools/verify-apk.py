"""Check APK native libraries for 16 KiB ELF and ZIP alignment; never executes APK code."""
import argparse
from pathlib import Path
import struct
import subprocess
import zipfile

parser=argparse.ArgumentParser()
parser.add_argument('apk',type=Path)
parser.add_argument('--sdk',type=Path,default=Path.home()/'AppData/Local/Android/Sdk')
args=parser.parse_args()
checked=0
with zipfile.ZipFile(args.apk) as archive:
    for name in archive.namelist():
        if not name.startswith('lib/') or not name.endswith('.so'): continue
        data=archive.read(name)
        assert data[:6]==b'\x7fELF\x02\x01', f'{name}: expected little-endian ELF64'
        offset=struct.unpack_from('<Q',data,32)[0]
        stride,count=struct.unpack_from('<HH',data,54)
        for index in range(count):
            kind=struct.unpack_from('<I',data,offset+index*stride)[0]
            if kind!=1: continue
            alignment=struct.unpack_from('<Q',data,offset+index*stride+48)[0]
            assert alignment>=16384, f'{name}: LOAD alignment={alignment}'
        checked+=1
versions=sorted((args.sdk/'build-tools').iterdir(),key=lambda p:tuple(int(x) if x.isdigit() else 0 for x in p.name.split('.')),reverse=True)
zipalign=next(p/'zipalign.exe' for p in versions if (p/'zipalign.exe').exists())
subprocess.run([str(zipalign),'-c','-P','16','4',str(args.apk)],check=True,stdout=subprocess.DEVNULL)
print(f'{args.apk.name}: {checked} ELF64 libraries, >=16 KiB LOAD alignment and APK page alignment PASS')
