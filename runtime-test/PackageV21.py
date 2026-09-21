#!/usr/bin/env python3
import json
import glob
import os
import sys
import zipfile

if len(sys.argv) != 7:
    raise SystemExit("usage: PackageV21.py <input-v20.jar> <FrozenEntityMaintenance.class> <FrozenRenderClock.class> <ClientLevelTimeStopMixin.class> <LevelRendererTimeStopMixin.class> <output-v21.jar>")

src, maintenance_cls, clock_cls, level_cls, renderer_cls, out = sys.argv[1:]
replacements = {
    "com/misanthropy/hit_indicator/client/FrozenEntityMaintenance.class": maintenance_cls,
    "com/misanthropy/hit_indicator/client/FrozenRenderClock.class": clock_cls,
    "com/misanthropy/hit_indicator/mixin/client/ClientLevelTimeStopMixin.class": level_cls,
    "com/misanthropy/hit_indicator/mixin/client/LevelRendererTimeStopMixin.class": renderer_cls,
}

clock_dir = os.path.dirname(clock_cls)
for nested in glob.glob(os.path.join(clock_dir, "FrozenRenderClock$*.class")):
    replacements[
        "com/misanthropy/hit_indicator/client/" + os.path.basename(nested)
    ] = nested

remove = {
    "com/misanthropy/hit_indicator/mixin/client/MobFreezeMixin.class",
    "com/misanthropy/hit_indicator/mixin/client/LivingEntityFreezeMixin.class",
    "com/misanthropy/hit_indicator/client/AnimationFreezeContext.class",
    "com/misanthropy/hit_indicator/mixin/client/LivingEntityRenderContextMixin.class",
    "com/misanthropy/hit_indicator/mixin/client/PlayerAnimatorInterpolationFreezeMixin.class",
}

with zipfile.ZipFile(src, "r") as zin:
    mixins = json.loads(zin.read("hit_indicator.mixins.json"))
    mixins["client"] = [
        "client.ClientLevelTimeStopMixin",
        "client.LevelRendererTimeStopMixin",
    ]
    mixins_data = json.dumps(mixins, indent=2).encode("utf-8") + b"\n"

    with zipfile.ZipFile(out, "w") as zout:
        for info in zin.infolist():
            if info.filename == "hit_indicator.mixins.json":
                continue
            if info.filename in remove or info.filename in replacements:
                continue
            zout.writestr(info, zin.read(info.filename))
        zout.writestr("hit_indicator.mixins.json", mixins_data)
        for path, disk in replacements.items():
            with open(disk, "rb") as f:
                zout.writestr(path, f.read())

print("PACKAGE_V21_STABLE_TIME_STOP_OK")
