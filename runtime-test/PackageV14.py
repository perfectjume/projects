import json, sys, zipfile
from pathlib import Path

if len(sys.argv) != 5:
    raise SystemExit("usage: PackageV14.py <base.jar> <AttackInterceptor.class> <helper-class-dir> <out.jar>")

base = Path(sys.argv[1])
attack = Path(sys.argv[2])
helper_dir = Path(sys.argv[3])
out = Path(sys.argv[4])

helper_files = sorted(helper_dir.glob("LearnedAttackPredictor*.class"))
if not helper_files:
    raise SystemExit("no LearnedAttackPredictor classes found")

with zipfile.ZipFile(base, "r") as zin:
    mix = json.loads(zin.read("hit_indicator.mixins.json").decode("utf-8"))
    mix["client"] = [x for x in mix.get("client", []) if x != "client.LivingEntityFreezeMixin"]
    mix_bytes = (json.dumps(mix, indent=2) + "\n").encode("utf-8")

    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as zout:
        seen = set()
        for info in zin.infolist():
            if info.filename in seen:
                continue
            seen.add(info.filename)
            if info.filename == "hit_indicator.mixins.json":
                zout.writestr(info, mix_bytes)
            elif info.filename == "com/misanthropy/hit_indicator/server/AttackInterceptor.class":
                zout.writestr(info, attack.read_bytes())
            elif info.filename == "com/misanthropy/hit_indicator/mixin/client/LivingEntityFreezeMixin.class":
                continue
            elif info.filename.startswith("com/misanthropy/hit_indicator/server/LearnedAttackPredictor"):
                continue
            else:
                zout.writestr(info, zin.read(info.filename))

        for helper in helper_files:
            arc = "com/misanthropy/hit_indicator/server/" + helper.name
            zout.writestr(arc, helper.read_bytes())

print(out)
print("V14_PACKAGE_PASS helpers=" + str(len(helper_files)))
