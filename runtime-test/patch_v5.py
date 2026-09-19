from pathlib import Path
from zipfile import ZipFile
import struct, sys, hashlib

SRC = Path(sys.argv[1])
OUT = Path(sys.argv[2])
TARGET = "com/misanthropy/hit_indicator/server/AttackInterceptor.class"
OLD = "Lnet/minecraft/server/level/ServerGamePacketListenerImpl;"
NEW = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"

def u2(b,o): return struct.unpack_from(">H", b, o)[0]
def p2(n): return struct.pack(">H", n)

def patch_class(data):
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("bad class magic")
    cp_count = u2(data, 8)
    off = 10
    entries = []
    replaced = 0
    i = 1
    while i < cp_count:
        start = off
        tag = data[off]
        off += 1
        if tag == 1:
            n = u2(data, off); off += 2
            raw = data[off:off+n]; off += n
            text = raw.decode("utf-8")
            if text == OLD:
                enc = NEW.encode("utf-8")
                entries.append(bytes([1]) + p2(len(enc)) + enc)
                replaced += 1
            else:
                entries.append(data[start:off])
        elif tag in (3,4):
            off += 4; entries.append(data[start:off])
        elif tag in (5,6):
            off += 8; entries.append(data[start:off]); i += 1
        elif tag in (7,8,16,19,20):
            off += 2; entries.append(data[start:off])
        elif tag in (9,10,11,12,17,18):
            off += 4; entries.append(data[start:off])
        elif tag == 15:
            off += 3; entries.append(data[start:off])
        else:
            raise ValueError(f"unknown constant-pool tag {tag}")
        i += 1

    if replaced != 1:
        raise ValueError(f"expected exactly one bad connection descriptor, found {replaced}")
    return data[:10] + b"".join(entries) + data[off:]

with ZipFile(SRC, "r") as zin:
    infos = zin.infolist()
    patched = patch_class(zin.read(TARGET))
    with ZipFile(OUT, "w") as zout:
        for info in infos:
            zout.writestr(info, patched if info.filename == TARGET else zin.read(info.filename))

print(hashlib.sha256(OUT.read_bytes()).hexdigest())
