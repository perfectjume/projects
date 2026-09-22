#!/usr/bin/env python3
import sys
import zipfile

if len(sys.argv) != 5:
    raise SystemExit("usage: PackageV24.py <input-v23.jar> <EmfStasisCompat.class> <StasisDesaturationClientEvents.class> <output-v24.jar>")

src, emf_cls, events_cls, out = sys.argv[1:]
replacements = {
    "com/misanthropy/hit_indicator/client/EmfStasisCompat.class": emf_cls,
    "com/misanthropy/hit_indicator/client/StasisDesaturationClientEvents.class": events_cls,
}

with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(out, "w") as zout:
    for info in zin.infolist():
        if info.filename in replacements:
            continue
        if info.filename == "META-INF/hit-indicator-v24-marker.txt":
            continue
        zout.writestr(info, zin.read(info.filename))

    for path, disk in replacements.items():
        with open(disk, "rb") as f:
            zout.writestr(path, f.read())

    zout.writestr(
        "META-INF/hit-indicator-v24-marker.txt",
        b"v24 optional EMF stasis animation pause compat\n"
    )

print("PACKAGE_V24_EMF_STASIS_FREEZE_OK")
