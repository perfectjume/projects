#!/usr/bin/env python3
import glob
import json
import os
import sys
import zipfile

if len(sys.argv) != 7:
    raise SystemExit(
        "usage: PackageV25.py <input-v23.jar> <EntityRenderContext.class> "
        "<EmfAnimationClockCompat.class> <EmfAnimationClockMixin.class> "
        "<LevelRendererTimeStopMixin.class> <output-v25.jar>"
    )

src, context_cls, compat_cls, emf_mixin_cls, level_cls, out = sys.argv[1:]
replacements = {
    "com/misanthropy/hit_indicator/client/EntityRenderContext.class": context_cls,
    "com/misanthropy/hit_indicator/client/EmfAnimationClockCompat.class": compat_cls,
    "com/misanthropy/hit_indicator/mixin/client/EmfAnimationClockMixin.class": emf_mixin_cls,
    "com/misanthropy/hit_indicator/mixin/client/LevelRendererTimeStopMixin.class": level_cls,
}

compat_dir = os.path.dirname(compat_cls)
for nested in glob.glob(os.path.join(compat_dir, "EmfAnimationClockCompat$*.class")):
    replacements[
        "com/misanthropy/hit_indicator/client/" + os.path.basename(nested)
    ] = nested

with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(out, "w") as zout:
    for info in zin.infolist():
        if info.filename in replacements:
            continue
        if info.filename in ("hit_indicator.mixins.json", "META-INF/hit-indicator-v25-marker.txt"):
            continue
        zout.writestr(info, zin.read(info.filename))

    mixins = json.loads(zin.read("hit_indicator.mixins.json"))
    clients = list(mixins.get("client", []))
    name = "client.EmfAnimationClockMixin"
    if name not in clients:
        clients.append(name)
    mixins["client"] = clients
    zout.writestr("hit_indicator.mixins.json", json.dumps(mixins, indent=2).encode("utf-8"))

    for path, disk in replacements.items():
        with open(disk, "rb") as f:
            zout.writestr(path, f.read())

    zout.writestr(
        "META-INF/hit-indicator-v25-marker.txt",
        b"v25 optional EMF clock freeze; animation evaluation remains active\n"
    )

print("PACKAGE_V25_EMF_CLOCK_FREEZE_OK")
