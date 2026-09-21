#!/usr/bin/env python3
import json
import sys
import zipfile

if len(sys.argv) != 4:
    raise SystemExit("usage: PackageV18.py <input-v17.jar> <MobFreezeMixin.class> <output-v18.jar>")

src, mob_mixin_class, out = sys.argv[1:]
mob_path = "com/misanthropy/hit_indicator/mixin/client/MobFreezeMixin.class"
mixins_json = "hit_indicator.mixins.json"

with open(mob_mixin_class, "rb") as f:
    mob_bytes = f.read()

with zipfile.ZipFile(src, "r") as zin:
    spec = json.loads(zin.read(mixins_json))
    client = list(spec.get("client") or [])
    if "client.LivingEntityFreezeMixin" not in client:
        raise SystemExit("v17 LivingEntityFreezeMixin missing")
    if "client.MobFreezeMixin" not in client:
        client.insert(0, "client.MobFreezeMixin")
    spec["client"] = client
    encoded = json.dumps(spec, indent=2).encode("utf-8") + b"\n"

    with zipfile.ZipFile(out, "w") as zout:
        for info in zin.infolist():
            if info.filename in (mob_path, mixins_json):
                continue
            zout.writestr(info, zin.read(info.filename))
        zout.writestr(mixins_json, encoded)
        zout.writestr(mob_path, mob_bytes)

print("PACKAGE_V18_MOB_HEAD_FREEZE_OK")
