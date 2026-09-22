#!/usr/bin/env python3
import glob
import json
import os
import sys
import zipfile

if len(sys.argv) != 5:
    raise SystemExit("usage: PackageV25.py <input-v23.jar> <EmfPoseFreeze.class> <EmfModelAnimationFreezeMixin.class> <output-v25.jar>")

src, helper_cls, mixin_cls, out = sys.argv[1:]
replacements = {
    "com/misanthropy/hit_indicator/client/EmfPoseFreeze.class": helper_cls,
    "com/misanthropy/hit_indicator/mixin/client/EmfModelAnimationFreezeMixin.class": mixin_cls,
}
helper_dir = os.path.dirname(helper_cls)
for nested in glob.glob(os.path.join(helper_dir, "EmfPoseFreeze$*.class")):
    replacements[
        "com/misanthropy/hit_indicator/client/" + os.path.basename(nested)
    ] = nested

with zipfile.ZipFile(src, "r") as zin:
    mixin_json = json.loads(zin.read("hit_indicator.mixins.json").decode("utf-8"))
    clients = list(mixin_json.get("client", []))
    name = "client.EmfModelAnimationFreezeMixin"
    if name not in clients:
        clients.append(name)
    mixin_json["client"] = clients
    mixin_bytes = (json.dumps(mixin_json, indent=2) + "\n").encode("utf-8")

    with zipfile.ZipFile(out, "w") as zout:
        for info in zin.infolist():
            if info.filename in replacements:
                continue
            if info.filename.startswith("com/misanthropy/hit_indicator/client/EmfPoseFreeze$"):
                continue
            if info.filename == "hit_indicator.mixins.json":
                continue
            if info.filename == "META-INF/hit-indicator-v25-marker.txt":
                continue
            zout.writestr(info, zin.read(info.filename))

        for path, disk in replacements.items():
            with open(disk, "rb") as f:
                zout.writestr(path, f.read())

        zout.writestr("hit_indicator.mixins.json", mixin_bytes)
        zout.writestr(
            "META-INF/hit-indicator-v25-marker.txt",
            b"v25 EMF evaluated-pose freeze; no EMF pause API\n"
        )

print("PACKAGE_V25_EMF_POSE_FREEZE_OK")
