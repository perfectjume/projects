from pathlib import Path
from zipfile import ZipFile
import struct, sys, hashlib

SRC = Path(sys.argv[1])
OUT = Path(sys.argv[2])
TARGET = "com/misanthropy/hit_indicator/client/WindupTracker.class"
SOUND_EVENTS = "net/minecraft/sounds/SoundEvents"
OLD_DESC = "Lnet/minecraft/core/Holder;"
NEW_DESC = "Lnet/minecraft/core/Holder$Reference;"
FIELDS = {
    "NOTE_BLOCK_BASS", "NOTE_BLOCK_CHIME", "NOTE_BLOCK_BASEDRUM",
    "NOTE_BLOCK_HAT", "NOTE_BLOCK_BIT", "NOTE_BLOCK_BELL"
}

def u2(b,o): return struct.unpack_from(">H",b,o)[0]
def p2(n): return struct.pack(">H",n)

def patch_class(data):
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("bad class magic")
    cp_count = u2(data,8)
    off = 10
    entries = [None] * cp_count
    utf = {}
    i = 1
    while i < cp_count:
        start = off
        tag = data[off]
        off += 1
        if tag == 1:
            n = u2(data,off); off += 2
            raw = data[off:off+n]; off += n
            utf[i] = raw.decode("utf-8")
        elif tag in (3,4):
            off += 4
        elif tag in (5,6):
            off += 8
            entries[i] = data[start:off]
            if i + 1 < cp_count:
                entries[i+1] = b""
            i += 2
            continue
        elif tag in (7,8,16,19,20):
            off += 2
        elif tag in (9,10,11,12,17,18):
            off += 4
        elif tag == 15:
            off += 3
        else:
            raise ValueError(f"unknown constant-pool tag {tag}")
        entries[i] = data[start:off]
        i += 1
    cp_end = off

    def class_name(idx):
        e = entries[idx]
        return utf.get(u2(e,1)) if e and e[0] == 7 else None

    def nat(idx):
        e = entries[idx]
        if not e or e[0] != 12:
            return None, None
        return utf.get(u2(e,1)), utf.get(u2(e,3))

    matches = []
    for idx in range(1, cp_count):
        e = entries[idx]
        if not e or e[0] != 9:
            continue
        ci, ni = u2(e,1), u2(e,3)
        name, desc = nat(ni)
        if class_name(ci) == SOUND_EVENTS and name in FIELDS:
            matches.append((idx, ci, name, desc))

    if {x[2] for x in matches} != FIELDS:
        raise ValueError(f"expected six note-block fields, got {matches}")
    if any(x[3] != OLD_DESC for x in matches):
        raise ValueError(f"unexpected source descriptors: {matches}")

    enc = NEW_DESC.encode()
    added = [bytes([1]) + p2(len(enc)) + enc]
    desc_idx = cp_count
    new_nat = {}
    for name in sorted(FIELDS):
        name_idx = next(k for k,v in utf.items() if v == name)
        nat_idx = cp_count + len(added)
        added.append(bytes([12]) + p2(name_idx) + p2(desc_idx))
        new_nat[name] = nat_idx

    for idx, ci, name, _ in matches:
        entries[idx] = bytes([9]) + p2(ci) + p2(new_nat[name])

    old_pool = b"".join(e for e in entries[1:] if e not in (None,b""))
    return data[:8] + p2(cp_count + len(added)) + old_pool + b"".join(added) + data[cp_end:]

with ZipFile(SRC, "r") as zin:
    info_list = zin.infolist()
    patched = patch_class(zin.read(TARGET))
    with ZipFile(OUT, "w") as zout:
        for info in info_list:
            zout.writestr(info, patched if info.filename == TARGET else zin.read(info.filename))

print(hashlib.sha256(OUT.read_bytes()).hexdigest())
