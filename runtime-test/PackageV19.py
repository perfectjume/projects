#!/usr/bin/env python3
import json
import sys
import zipfile

if len(sys.argv) != 6:
    raise SystemExit("usage: PackageV19.py <input-v18.jar> <AnimationFreezeContext.class> <LivingEntityRenderContextMixin.class> <PlayerAnimatorInterpolationFreezeMixin.class> <output-v19.jar>")

src, context_cls, render_cls, pal_cls, out = sys.argv[1:]
files = {
    "com/misanthropy/hit_indicator/client/AnimationFreezeContext.class": context_cls,
    "com/misanthropy/hit_indicator/mixin/client/LivingEntityRenderContextMixin.class": render_cls,
    "com/misanthropy/hit_indicator/mixin/client/PlayerAnimatorInterpolationFreezeMixin.class": pal_cls,
}
mixins_json = "hit_indicator.mixins.json"

payloads = {}
for path, disk in files.items():
    with open(disk, "rb") as f:
        payloads[path] = f.read()

with zipfile.ZipFile(src, "r") as zin:
    spec = json.loads(zin.read(mixins_json))
    client = list(spec.get("client") or [])
    for name in [
        "client.LivingEntityRenderContextMixin",
        "client.PlayerAnimatorInterpolationFreezeMixin",
    ]:
        if name not in client:
            client.append(name)
    spec["client"] = client
    encoded = json.dumps(spec, indent=2).encode("utf-8") + b"\n"

    with zipfile.ZipFile(out, "w") as zout:
        for info in zin.infolist():
            if info.filename == mixins_json or info.filename in payloads:
                continue
            zout.writestr(info, zin.read(info.filename))
        zout.writestr(mixins_json, encoded)
        for path, data in payloads.items():
            zout.writestr(path, data)

print("PACKAGE_V19_PLAYERANIM_RENDER_FREEZE_OK")
