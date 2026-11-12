"""Normalize new generated zone1 art and build the requested visual checks.

No source PNG or JavaScript is changed. Run with existing project Pillow:
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=tools/.image-work/deps python3 tools/prepare_zone1.py
"""
import hashlib
import json
import math
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageStat, ImageOps

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs/codex'
DEST = ROOT / 'assets_src/zone1'
SOURCES = json.loads((DOCS / 'zone1_sources.json').read_text())
LANCZOS = Image.Resampling.LANCZOS


def clean(im):
    im = im.convert('RGBA')
    im.putalpha(im.getchannel('A').point(lambda a: a if a >= 8 else 0))
    return im


def wrap_axis(im, axis, margin, pre_extended=False):
    """Overlap opposite margins using a cosine feather; retain artwork geometry."""
    if axis == 'y':
        return wrap_axis(im.transpose(Image.Transpose.TRANSPOSE), 'x', margin, pre_extended).transpose(Image.Transpose.TRANSPOSE)
    width, height = im.size
    if pre_extended:
        expanded = im
        width -= 2 * margin
    else:
        expanded = im.resize((width + 2 * margin, height), LANCZOS)
    result = expanded.crop((margin, 0, width + margin, height))
    for x in range(margin):
        weight = .5 * (1 + math.cos(math.pi * x / margin)) / 2
        # Opposite strips run in the same direction; no mirroring of the art.
        left = expanded.crop((margin + x, 0, margin + x + 1, height))
        right = expanded.crop((width + margin + x, 0, width + margin + x + 1, height))
        result.paste(Image.blend(left, right, weight), (x, 0))
        x2 = width - 1 - x
        a = expanded.crop((margin + x2, 0, margin + x2 + 1, height))
        b = expanded.crop((margin - 1 - x, 0, margin - x, height))
        result.paste(Image.blend(a, b, weight), (x2, 0))
    # Exact endpoint equality also protects against sampler rounding.
    edge = Image.blend(result.crop((0, 0, 1, height)), result.crop((width - 1, 0, width, height)), .5)
    result.paste(edge, (0, 0))
    result.paste(edge, (width - 1, 0))
    return result


def periodic(im, axes, margin):
    for axis in axes:
        im = wrap_axis(im, axis, margin)
    return im


def checker(size):
    im = Image.new('RGB', size, '#f1f4f6')
    d = ImageDraw.Draw(im)
    for y in range(0, size[1], 16):
        for x in range(0, size[0], 16):
            if (x // 16 + y // 16) % 2:
                d.rectangle((x, y, x + 15, y + 15), fill='#e1e8ed')
    return im


def tile(im, nx, ny):
    out = Image.new(im.mode, (im.width * nx, im.height * ny))
    for y in range(ny):
        for x in range(nx):
            out.paste(im, (im.width * x, im.height * y))
    return out


def build():
    for name in ('rock',):
        im = Image.open(SOURCES[name]).convert('RGB').resize((512, 512), LANCZOS)
        im = periodic(im, 'xy', 36)
        im.save(DEST / f'{name}.png')
    # Align the generated diagonal stitch period before wrapping. The original
    # has x + 1.16*y = constant seams about 172px apart at 512px source scale.
    # Two complete periods make both boundary continuations agree geometrically.
    fabric = Image.open(SOURCES['fabric']).convert('RGB')
    scale = fabric.width / 512
    span_x, span_y = 344, 344 / 1.16
    margin = 32
    step_x, step_y = span_x / 512 * scale, span_y / 512 * scale
    fabric = fabric.transform((576, 576), Image.Transform.AFFINE,
        (step_x, 0, 80 * scale - margin * step_x,
         0, step_y, 100 * scale - margin * step_y), Image.Resampling.BICUBIC)
    fabric = wrap_axis(fabric, 'x', margin, True)
    fabric = wrap_axis(fabric, 'y', margin, True)
    fabric.save(DEST / 'fabric.png')
    bg = Image.open(SOURCES['bg']).convert('RGB').resize((1024, 1536), LANCZOS)
    periodic(bg, 'y', 96).save(DEST / 'bg.png')

    raw = clean(Image.open(SOURCES['trim']))
    raw = raw.resize((512, round(raw.height * 512 / raw.width)), LANCZOS)
    # Generated rock lip is at source y=275, so move it to the y=32 baseline.
    top = round(275 * 512 / 2172) - 32
    im = raw.crop((0, top, 512, top + 128))
    fade = Image.new('L', im.size)
    fade.putdata([round(255 * (1 if y <= 32 else max(0, (127 - y) / 95) ** 3)) for y in range(128) for x in range(512)])
    im.putalpha(ImageChops.multiply(im.getchannel('A'), fade))
    periodic(im, 'x', 32).save(DEST / 'trim.png')

    for name, width in [('puddle', 300), ('tissue_pile', 260), ('paper_boat', 220), ('arrow_sign', 220)]:
        raw = clean(Image.open(SOURCES[name]))
        raw = raw.crop(raw.getchannel('A').getbbox())
        raw = raw.resize((width, round(raw.height * width / raw.width)), LANCZOS)
        raw = clean(raw)
        raw = raw.crop(raw.getchannel('A').getbbox())
        canvas = Image.new('RGBA', (512, 512))
        canvas.paste(raw, (256 - raw.width // 2, 451 - raw.height))
        canvas.save(DEST / f'{name}.png')

    raw = clean(Image.open(SOURCES['drips']))
    raw = raw.crop(raw.getchannel('A').getbbox())
    raw = clean(raw.resize((round(raw.width * .4), round(raw.height * .4)), LANCZOS))
    raw = raw.crop(raw.getchannel('A').getbbox())
    top_bounds = raw.getchannel('A').crop((0, 0, raw.width, 1)).getbbox()
    tip = (top_bounds[0] + top_bounds[2] - 1) // 2
    canvas = Image.new('RGBA', (512, 512))
    canvas.paste(raw, (256 - tip, 60))
    canvas.save(DEST / 'drips.png')
    checks()


def checks():
    assets = {p.stem: Image.open(p) for p in DEST.glob('*.png')}
    for name, nx, ny in [('rock', 2, 2), ('fabric', 2, 2), ('trim', 3, 1), ('bg', 1, 2)]:
        tile(assets[name], nx, ny).save(DOCS / f'zone1_tile_{name}.png')

    sheet = Image.new('RGB', (1440, 1280), '#f9fbfc')
    d = ImageDraw.Draw(sheet)
    font_path = '/System/Library/Fonts/Supplemental/Arial.ttf'
    title = ImageFont.truetype(font_path, 30)
    label = ImageFont.truetype(font_path, 19)
    small = ImageFont.truetype(font_path, 15)
    d.text((32, 22), 'ZONE 1 / Rainy room - asset & scale check', fill='#263f55', font=title)
    names = ['rock', 'fabric', 'bg', 'trim', 'puddle', 'drips', 'tissue_pile', 'paper_boat', 'arrow_sign']
    for i, name in enumerate(names):
        x = 32 + (i % 5) * 280
        y = 85 + (i // 5) * 335
        im = assets[name].copy()
        thumb = im.convert('RGBA')
        thumb.thumbnail((256, 256), LANCZOS)
        panel = checker((256, 256)).convert('RGBA')
        panel.alpha_composite(thumb, ((256 - thumb.width) // 2, (256 - thumb.height) // 2))
        sheet.paste(panel.convert('RGB'), (x, y))
        d.text((x, y + 264), name + '.png', fill='#263f55', font=label)
        d.text((x, y + 290), f'{im.width} x {im.height} / {im.mode}', fill='#61758a', font=small)
    lineup = Image.open(ROOT / 'refs/lineup.png')
    ref = lineup.crop((round(lineup.width * .615), round(lineup.height * .18), round(lineup.width * .802), round(lineup.height * .89))).convert('RGB')
    ref.thumbnail((256, 256), LANCZOS)
    sheet.paste(ref, (1152, 435))
    d.text((1152, 684), 'lineup.png / defeated', fill='#263f55', font=label)

    # Visual scale sample: materials at 50%, blue character at 75px actual width.
    scene = ImageOps.fit(assets['bg'], (1376, 480), LANCZOS).convert('RGBA')
    rock = assets['rock'].resize((256, 256), LANCZOS)
    for x in range(0, 1376, 256):
        scene.paste(rock, (x, 290))
    trim = assets['trim'].resize((256, 64), LANCZOS)
    for x in range(0, 1376, 256):
        scene.alpha_composite(trim, (x, 274))
    fabric = assets['fabric'].resize((256, 256), LANCZOS)
    ramp = Image.new('RGBA', scene.size)
    for y in range(0, 480, 256):
        for x in range(850, 1376, 256):
            ramp.paste(fabric, (x, y))
    mask = Image.new('L', scene.size)
    ImageDraw.Draw(mask).polygon([(850, 290), (1376, 140), (1376, 480), (850, 480)], fill=255)
    ramp.putalpha(mask)
    scene.alpha_composite(ramp)
    sd = ImageDraw.Draw(scene)
    sd.line([(0, 290), (850, 290), (1376, 140)], fill='#263340', width=4)
    character = clean(Image.open(ROOT / 'refs/defeated/front.png'))
    character = character.crop(character.getchannel('A').getbbox())
    character = character.resize((75, round(character.height * 75 / character.width)), LANCZOS)
    scene.alpha_composite(character, (300, 290 - character.height))
    for name, x, bottom in [('puddle', 430, 290), ('tissue_pile', 100, 290), ('paper_boat', 575, 290), ('arrow_sign', 735, 290)]:
        prop = assets[name].resize((256, 256), LANCZOS)
        scene.alpha_composite(prop, (x - 128, bottom - 225))
    drop = assets['drips'].resize((256, 256), LANCZOS)
    scene.alpha_composite(drop, (480, 20))
    sheet.paste(scene.convert('RGB'), (32, 765))
    d.text((48, 778), 'Display scale: tile 256px / character 75px', fill='#263f55', font=label)
    sheet.save(DOCS / 'check_zone1.png')

    report = {}
    for name, im in assets.items():
        expected = (1024, 1536) if name == 'bg' else (512, 128) if name == 'trim' else (512, 512)
        assert im.size == expected, name
        opaque = name in ('rock', 'fabric', 'bg')
        assert im.mode == ('RGB' if opaque else 'RGBA'), name
        record = {'size': im.size, 'mode': im.mode, 'mean_rgb': [round(v, 2) for v in ImageStat.Stat(im.convert('RGB')).mean]}
        if not opaque:
            a = im.getchannel('A')
            assert a.getextrema() == (0, 255), name
            record['alpha_bbox'] = a.getbbox()
            record['alpha_extrema'] = a.getextrema()
            if name not in ('trim', 'drips'):
                assert record['alpha_bbox'][3] == 451, name
            if name == 'drips':
                assert record['alpha_bbox'][1] == 60
        for axis in ('xy' if name in ('rock', 'fabric') else 'x' if name == 'trim' else 'y' if name == 'bg' else ''):
            if axis == 'x':
                a, b = im.crop((0, 0, 1, im.height)), im.crop((im.width - 1, 0, im.width, im.height))
            else:
                a, b = im.crop((0, 0, im.width, 1)), im.crop((0, im.height - 1, im.width, im.height))
            assert a.tobytes() == b.tobytes(), (name, axis)
            record[f'{axis}_edge_max_difference'] = 0
        report[name] = record
    original = json.loads((DOCS / 'zone1_original_hashes.json').read_text())
    changed = [p for p, digest in original.items() if hashlib.sha256((ROOT / p).read_bytes()).hexdigest() != digest]
    assert not changed, changed
    report['existing_png_and_js_unchanged'] = len(original)
    (DOCS / 'zone1_measurements.json').write_text(json.dumps(report, indent=2))
    print(f'Validated {len(assets)} assets, 4 repeat proofs, contact sheet; {len(original)} existing PNG/JS files unchanged.')


if __name__ == '__main__':
    build()
