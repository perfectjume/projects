#!/usr/bin/env python3
import glob
import os
import sys
import zipfile

if len(sys.argv) != 5:
    raise SystemExit("usage: PackageV22.py <input-v21.jar> <FrozenRenderClock.class> <ClientLevelTimeStopMixin.class> <output-v22.jar>")

src, clock_cls, level_cls, out = sys.argv[1:]
replacements = {
    "com/misanthropy/hit_indicator/client/FrozenRenderClock.class": clock_cls,
    "com/misanthropy/hit_indicator/mixin/client/ClientLevelTimeStopMixin.class": level_cls,
}

clock_dir = os.path.dirname(clock_cls)
for nested in glob.glob(os.path.join(clock_dir, "FrozenRenderClock$*.class")):
    replacements[
        "com/misanthropy/hit_indicator/client/" + os.path.basename(nested)
    ] = nested

with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(out, "w") as zout:
    for info in zin.infolist():
        if info.filename in replacements:
            continue
        if info.filename.startswith("com/misanthropy/hit_indicator/client/FrozenRenderClock$"):
            continue
        zout.writestr(info, zin.read(info.filename))

    for path, disk in replacements.items():
        with open(disk, "rb") as f:
            zout.writestr(path, f.read())

print("PACKAGE_V22_CONTINUOUS_RESUME_OK")
