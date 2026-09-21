#!/usr/bin/env python3
import sys
import zipfile

if len(sys.argv) != 4:
    raise SystemExit("usage: PackageV20.py <input-v19.jar> <patched-WindupTracker.class> <output-v20.jar>")

src, tracker_class, out = sys.argv[1:]
tracker_path = "com/misanthropy/hit_indicator/client/WindupTracker.class"

with open(tracker_class, "rb") as f:
    patched = f.read()

with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(out, "w") as zout:
    for info in zin.infolist():
        if info.filename == tracker_path:
            continue
        zout.writestr(info, zin.read(info.filename))
    zout.writestr(tracker_path, patched)

print("PACKAGE_V20_IMMEDIATE_BETTERCOMBAT_FREEZE_OK")
