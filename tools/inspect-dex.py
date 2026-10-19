"""Read DEX type/method tables for compatibility diagnostics; does not execute APK code."""
import zipfile,struct,sys,re

def uleb(data,offset):
    value=0; shift=0
    while True:
        b=data[offset]; offset+=1; value|=(b&127)<<shift
        if b<128: return value,offset
        shift+=7

pattern=re.compile(sys.argv[2] if len(sys.argv)>2 else 'com/google/android/gms/location/|com/amap/api/location')
for name,data in ((n,z.read(n)) for z in [zipfile.ZipFile(sys.argv[1])] for n in z.namelist() if n.endswith('.dex')):
    string_count,string_offset=struct.unpack_from('<II',data,56)
    strings=[]
    for i in range(string_count):
        pos=struct.unpack_from('<I',data,string_offset+i*4)[0]; _,pos=uleb(data,pos)
        strings.append(data[pos:data.index(0,pos)].decode('utf-8','replace'))
    type_count,type_offset=struct.unpack_from('<II',data,64)
    types=[strings[struct.unpack_from('<I',data,type_offset+i*4)[0]] for i in range(type_count)]
    method_count,method_offset=struct.unpack_from('<II',data,88)
    proto_count,proto_offset=struct.unpack_from('<II',data,72)
    found={}
    for i in range(method_count):
        cls,proto,method_name=struct.unpack_from('<HHI',data,method_offset+i*8)
        if pattern.search(types[cls]) or sys.argv[2]=='task-factory':
            _,returned,parameters=struct.unpack_from('<III',data,proto_offset+proto*12)
            args=[]
            if parameters:
                count=struct.unpack_from('<I',data,parameters)[0]
                args=[types[struct.unpack_from('<H',data,parameters+4+j*2)[0]] for j in range(count)]
            if sys.argv[2]!='task-factory' or types[returned]=='Lbfsc;' and args==['Ljava/lang/Object;']:
                found.setdefault(types[cls],set()).add(strings[method_name]+'('+','.join(args)+')->'+types[returned])
    for cls,methods in found.items():
        print(name,cls,','.join(sorted(methods)))
