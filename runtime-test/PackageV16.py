#!/usr/bin/env python3
import sys
import zipfile

if len(sys.argv) != 4:
    raise SystemExit("usage: PackageV16.py <input-v15.jar> <patched-AttackInterceptor.class> <output-v16.jar>")

src, attack_class, out = sys.argv[1:]
attack_path = "com/misanthropy/hit_indicator/server/AttackInterceptor.class"
predictor_prefix = "com/misanthropy/hit_indicator/server/LearnedAttackPredictor"

with open(attack_class, "rb") as f:
    patched = f.read()

with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(out, "w") as zout:
    for info in zin.infolist():
        name = info.filename
        if name == attack_path:
            continue
        if name.startswith(predictor_prefix):
            continue
        zout.writestr(info, zin.read(name))
    zout.writestr(attack_path, patched)

print("PACKAGE_V16_UNIVERSAL_OK")
