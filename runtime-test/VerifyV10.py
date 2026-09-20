import io, sys, zipfile
from PIL import Image

if len(sys.argv) != 2:
    raise SystemExit("usage: VerifyV10.py <jar>")

jar = sys.argv[1]
with zipfile.ZipFile(jar) as z:
    ring = Image.open(io.BytesIO(z.read('assets/hit_indicator/textures/indicator/ring.png'))).convert('RGBA')
    assert ring.size == (128, 128)
    assert sum(1 for p in ring.getdata() if p[3]) > 0

    for i in range(32):
        im = Image.open(io.BytesIO(z.read(f'assets/hit_indicator/textures/indicator/timing/frame_{i}.png'))).convert('RGBA')
        assert im.size == (128, 128)
        assert sum(1 for p in im.getdata() if p[3]) > 0

    for n in ['icon.png', 'lunge.png', 'slam.png', 'sure.png', 'shadow.png']:
        im = Image.open(io.BytesIO(z.read('assets/hit_indicator/textures/indicator/' + n))).convert('RGBA')
        assert im.size == (16, 16)
        assert all(p[3] == 0 for p in im.getdata())

print('V10_ASSET_ASSERTIONS_PASS')
