#!/usr/bin/env python3
import json
import sys
import zipfile

if len(sys.argv) != 4:
    raise SystemExit("usage: PackageV17.py <input-v16.jar> <LivingEntityFreezeMixin.class> <output-v17.jar>")

src, mixin_class, out = sys.argv[1:]
mixin_path = "com/misanthropy/hit_indicator/mixin/client/LivingEntityFreezeMixin.class"
mixins_json = "hit_indicator.mixins.json"

with open(mixin_class, "rb") as f:
    mixin_bytes = f.read()

with zipfile.ZipFile(src, "r") as zin:
    spec = json.loads(zin.read(mixins_json))
    spec["client"] = ["client.LivingEntityFreezeMixin"]
    encoded_spec = json.dumps(spec, indent=2).encode("utf-8") + b"\n"

    with zipfile.ZipFile(out, "w") as zout:
        for info in zin.infolist():
            if info.filename in (mixin_path, mixins_json):
                continue
            zout.writestr(info, zin.read(info.filename))
        zout.writestr(mixins_json, encoded_spec)
        zout.writestr(mixin_path, mixin_bytes)

print("PACKAGE_V17_REFERENCE_FREEZE_OK")
