from pathlib import Path
from zipfile import ZipFile
import struct, sys, hashlib

SRC = Path(sys.argv[1])
HELPER = Path(sys.argv[2])
OUT = Path(sys.argv[3])
TARGET = 'com/misanthropy/hit_indicator/server/AttackInterceptor.class'
HELPER_ENTRY = 'com/misanthropy/hit_indicator/server/ParticleCompat.class'

HELPER_OWNER = 'com/misanthropy/hit_indicator/server/ParticleCompat'
HELPER_NAME = 'groundParticleSpec'
HELPER_DESC = '(Ljava/lang/String;Lnet/minecraft/world/level/block/state/BlockState;)Ljava/lang/String;'
OLD_OWNER = 'net/minecraft/commands/arguments/blocks/BlockStateParser'
OLD_NAME = 'serialize'
OLD_DESC = '(Lnet/minecraft/world/level/block/state/BlockState;)Ljava/lang/String;'
REPLACE_OWNER = 'java/lang/String'
REPLACE_NAME = 'replace'
REPLACE_DESC = '(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;'
STATE_TOKEN = '{state}'

def u2(b,o): return struct.unpack_from('>H',b,o)[0]
def p2(n): return struct.pack('>H',n)

def patch_class(data):
    if data[:4] != b'\xca\xfe\xba\xbe': raise ValueError('bad class magic')
    cp_count = u2(data,8)
    off = 10
    entries = [None] * cp_count
    utf = {}
    i = 1
    while i < cp_count:
        start = off; tag = data[off]; off += 1
        if tag == 1:
            n=u2(data,off); off+=2; raw=data[off:off+n]; off+=n; utf[i]=raw.decode('utf-8')
        elif tag in (3,4): off += 4
        elif tag in (5,6):
            off += 8; entries[i]=data[start:off]
            if i+1 < cp_count: entries[i+1]=b''
            i += 2; continue
        elif tag in (7,8,16,19,20): off += 2
        elif tag in (9,10,11,12,17,18): off += 4
        elif tag == 15: off += 3
        else: raise ValueError(f'unknown cp tag {tag}')
        entries[i]=data[start:off]; i += 1
    cp_end=off

    def cls(idx):
        e=entries[idx]
        return utf.get(u2(e,1)) if e and e[0]==7 else None
    def nat(idx):
        e=entries[idx]
        if not e or e[0]!=12: return None,None
        return utf.get(u2(e,1)), utf.get(u2(e,3))
    def methodref(owner,name,desc):
        for idx in range(1,cp_count):
            e=entries[idx]
            if e and e[0] in (10,11):
                n,d=nat(u2(e,3))
                if cls(u2(e,1))==owner and n==name and d==desc: return idx
        raise ValueError(f'methodref not found {owner}.{name}{desc}')
    def string_const(value):
        for idx in range(1,cp_count):
            e=entries[idx]
            if e and e[0]==8 and utf.get(u2(e,1))==value: return idx
        raise ValueError(f'string const not found {value}')

    old_ser=methodref(OLD_OWNER,OLD_NAME,OLD_DESC)
    replace=methodref(REPLACE_OWNER,REPLACE_NAME,REPLACE_DESC)
    token=string_const(STATE_TOKEN)

    additions=[]
    def add_utf(s):
        idx=cp_count+len(additions); b=s.encode(); additions.append(bytes([1])+p2(len(b))+b); return idx
    owner_u=add_utf(HELPER_OWNER)
    owner_c=cp_count+len(additions); additions.append(bytes([7])+p2(owner_u))
    name_u=add_utf(HELPER_NAME)
    desc_u=add_utf(HELPER_DESC)
    nat_i=cp_count+len(additions); additions.append(bytes([12])+p2(name_u)+p2(desc_u))
    helper_ref=cp_count+len(additions); additions.append(bytes([10])+p2(owner_c)+p2(nat_i))

    rest=bytearray(data[cp_end:])
    pattern = bytes([0x13]) + p2(token) + bytes([0x19,0x0f,0xb8]) + p2(old_ser) + bytes([0xb6]) + p2(replace)
    pos=rest.find(pattern)
    if pos < 0: raise ValueError('ground particle callsite pattern not found')
    if rest.find(pattern,pos+1) >= 0: raise ValueError('ground particle callsite pattern found more than once')
    rest[pos:pos+3] = b'\x00\x00\x00'
    inv = pos + 5
    rest[inv:inv+3] = bytes([0xb8]) + p2(helper_ref)
    repl = inv + 3
    rest[repl:repl+3] = b'\x00\x00\x00'

    old_pool=b''.join(e for e in entries[1:] if e not in (None,b''))
    return data[:8]+p2(cp_count+len(additions))+old_pool+b''.join(additions)+bytes(rest)

with ZipFile(SRC,'r') as zin:
    infos=zin.infolist()
    patched=patch_class(zin.read(TARGET))
    helper_bytes=HELPER.read_bytes()
    with ZipFile(OUT,'w') as zout:
        for info in infos:
            if info.filename == HELPER_ENTRY: continue
            zout.writestr(info, patched if info.filename==TARGET else zin.read(info.filename))
        zout.writestr(HELPER_ENTRY, helper_bytes)

print(hashlib.sha256(OUT.read_bytes()).hexdigest())
