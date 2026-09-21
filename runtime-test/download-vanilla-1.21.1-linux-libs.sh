#!/usr/bin/env bash
set -euo pipefail
mkdir -p /tmp/vanilla-libs/libraries
curl -fsSL https://piston-meta.mojang.com/mc/game/version_manifest_v2.json -o /tmp/manifest.json
python - <<'PY'
import json, pathlib, urllib.request, hashlib
manifest=json.load(open('/tmp/manifest.json'))
url=next(v['url'] for v in manifest['versions'] if v['id']=='1.21.1')
urllib.request.urlretrieve(url,'/tmp/1.21.1.json')
j=json.load(open('/tmp/1.21.1.json'))
base=pathlib.Path('/tmp/vanilla-libs/libraries')
def allowed(lib):
    rules=lib.get('rules')
    if not rules: return True
    allow=False
    for r in rules:
        match=True
        o=r.get('os')
        if o and o.get('name') and o['name']!='linux': match=False
        if match: allow=(r.get('action')=='allow')
    return allow
items=[]
for lib in j['libraries']:
    if not allowed(lib): continue
    d=lib.get('downloads',{}).get('artifact')
    if d and d.get('path') and d.get('url'): items.append(d)
    cls=lib.get('downloads',{}).get('classifiers',{})
    for k in ('natives-linux','natives-linux-64'):
        d=cls.get(k)
        if d and d.get('path') and d.get('url'): items.append(d)
seen=set()
for d in items:
    if d['path'] in seen: continue
    seen.add(d['path'])
    p=base/d['path']; p.parent.mkdir(parents=True,exist_ok=True)
    urllib.request.urlretrieve(d['url'],p)
    data=p.read_bytes()
    got=hashlib.sha1(data).hexdigest()
    if d.get('sha1') and got!=d['sha1']:
        raise SystemExit(f"SHA1 mismatch {d['path']} {got} != {d['sha1']}")
    print(d['path'], len(data), flush=True)
print('FILES',len(seen))
print('BYTES',sum((base/p).stat().st_size for p in seen))
PY
tar -C /tmp/vanilla-libs -czf /tmp/vanilla-1.21.1-linux-libs.tar.gz libraries
sha256sum /tmp/vanilla-1.21.1-linux-libs.tar.gz
