import json, sys, zipfile
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit("usage: PackageV13NoFreeze.py <in.jar> <out.jar>")

src = Path(sys.argv[1])
out = Path(sys.argv[2])

with zipfile.ZipFile(src, "r") as zin:
    mix = json.loads(zin.read("hit_indicator.mixins.json").decode("utf-8"))
    mix["client"] = [x for x in mix.get("client", []) if x != "client.LivingEntityFreezeMixin"]
    if mix["client"]:
        raise SystemExit(f"unexpected remaining client mixins: {mix['client']}")
    mix_bytes = (json.dumps(mix, indent=2) + "\n").encode("utf-8")

    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as zout:
        seen=set()
        for info in zin.infolist():
            if info.filename in seen:
                continue
            seen.add(info.filename)
            if info.filename == "hit_indicator.mixins.json":
                zout.writestr(info, mix_bytes)
            else:
                zout.writestr(info, zin.read(info.filename))

print(out)
print("V13_NO_FREEZE_MIXIN_PASS")
