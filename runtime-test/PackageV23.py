#!/usr/bin/env python3
import os
import sys
import zipfile

if len(sys.argv) != 10:
    raise SystemExit(
        "usage: PackageV23.py <input-v22.jar> <StasisDesaturationClientEvents.class> "
        "<StasisDesaturationRenderer.class> <LevelRendererTimeStopMixin.class> "
        "<shader.json> <shader.vsh> <shader.fsh> <output-v23.jar> <marker>"
    )

(src, events_cls, renderer_cls, level_cls,
 shader_json, shader_vsh, shader_fsh, out, marker) = sys.argv[1:]

replacements = {
    "com/misanthropy/hit_indicator/client/StasisDesaturationClientEvents.class": events_cls,
    "com/misanthropy/hit_indicator/client/StasisDesaturationRenderer.class": renderer_cls,
    "com/misanthropy/hit_indicator/mixin/client/LevelRendererTimeStopMixin.class": level_cls,
    "assets/hit_indicator/shaders/core/stasis_desaturate.json": shader_json,
    "assets/hit_indicator/shaders/core/stasis_desaturate.vsh": shader_vsh,
    "assets/hit_indicator/shaders/core/stasis_desaturate.fsh": shader_fsh,
}

with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(out, "w") as zout:
    for info in zin.infolist():
        if info.filename in replacements:
            continue
        zout.writestr(info, zin.read(info.filename))

    for path, disk in replacements.items():
        with open(disk, "rb") as f:
            zout.writestr(path, f.read())

    zout.writestr("META-INF/hit-indicator-v23-marker.txt", marker.encode("utf-8"))

print("PACKAGE_V23_UNIVERSAL_STASIS_GRAYSCALE_OK")
