import json, sys, zipfile

if len(sys.argv) != 2:
    raise SystemExit("usage: VerifyV13.py <jar>")

with zipfile.ZipFile(sys.argv[1]) as z:
    mix = json.loads(z.read("hit_indicator.mixins.json"))

assert mix.get("client") == [], mix
print("V13_CLIENT_FREEZE_DISABLED_PASS")
