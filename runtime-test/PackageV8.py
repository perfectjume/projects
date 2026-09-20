import io, math, sys, zipfile
from pathlib import Path
from PIL import Image

if len(sys.argv) != 4:
    raise SystemExit('usage: PackageV8.py <v6.jar> <patched-renderer.class> <out.jar>')

base_jar = Path(sys.argv[1])
renderer_class = Path(sys.argv[2])
out_jar = Path(sys.argv[3])

N = 128
C = 63.5
GOLD = (225,173,83,255)
LIGHT = (255,235,177,255)
WHITE = (255,248,214,255)

def ring(radius, fixed=False):
    im = Image.new('RGBA', (N,N))
    for y in range(N):
        for x in range(N):
            dx=x-C
            dy=y-C
            r=math.hypot(dx,dy)
            a=(math.degrees(math.atan2(dy,dx))+360)%360
            color=None
            if fixed:
                if abs(r-radius)<.70: color=LIGHT
                elif radius+.7<=r<radius+1.6: color=GOLD
                elif abs(r-(radius-3))<.53: color=GOLD
                if abs(r-(radius+4))<.7 and (205<a<255 or 25<a<75) and int(a/7)%2==0: color=LIGHT
                if abs(dy)<.55 and abs(abs(dx)-radius)<2: color=WHITE
            else:
                if abs(r-radius)<.60: color=WHITE
                elif radius+.6<=r<radius+1.45: color=GOLD
            if color:
                im.putpixel((x,y), color)
    return im

def png_bytes(im):
    b=io.BytesIO()
    im.save(b, format='PNG')
    return b.getvalue()

renderer = renderer_class.read_bytes()
fixed = png_bytes(ring(30, True))
frames = [png_bytes(ring(56-26*i/31)) for i in range(32)]

with zipfile.ZipFile(base_jar, 'r') as zin, zipfile.ZipFile(out_jar, 'w') as zout:
    replace = {
        'com/misanthropy/hit_indicator/client/WindupIndicatorRenderer.class',
        'assets/hit_indicator/textures/indicator/ring.png',
    }
    replace.update({f'assets/hit_indicator/textures/indicator/timing/frame_{i}.png' for i in range(32)})
    for info in zin.infolist():
        if info.filename in replace:
            continue
        zout.writestr(info, zin.read(info.filename))

    zout.writestr('com/misanthropy/hit_indicator/client/WindupIndicatorRenderer.class', renderer)
    zout.writestr('assets/hit_indicator/textures/indicator/ring.png', fixed)
    for i, data in enumerate(frames):
        zout.writestr(f'assets/hit_indicator/textures/indicator/timing/frame_{i}.png', data)

print(out_jar)
