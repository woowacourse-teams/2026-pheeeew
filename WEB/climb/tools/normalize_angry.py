"""Normalize generated sprites and measure their actual alpha bounds.

Run with PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=tools/.image-work/deps.
Body bands are visually selected regions where hands/feet do not obscure
the widest body contour. All geometry transforms are uniform resize and
integer translation; facial features and pose are not repainted.
"""
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs/codex'
DEST = ROOT / 'assets_src/characters/angry'
CONFIG = DOCS / 'angry_normalization.json'


def cleaned(image):
    image = image.convert('RGBA')
    # Generated backgrounds carry alpha 1..7 dust, invisible at display size.
    # Keep genuine antialiasing at alpha >= 8; do not recolor the artwork.
    image.putalpha(image.getchannel('A').point(lambda a: a if a >= 8 else 0))
    return image


def run():
    config = json.loads(CONFIG.read_text())
    report = []
    for name, spec in config.items():
        source = cleaned(Image.open(spec['source']))
        band = spec['body_band']
        body_bbox = source.getchannel('A').crop(band).getbbox()
        assert body_bbox
        raw_body_width = body_bbox[2] - body_bbox[0]
        factor = spec['target_body_width'] / raw_body_width
        resized = cleaned(source.resize(
            (round(source.width * factor), round(source.height * factor)),
            Image.Resampling.LANCZOS))
        alpha = resized.getchannel('A')
        bbox = alpha.getbbox()
        crop = resized.crop(bbox)
        assert crop.width <= 1024 and crop.height <= 901, (name, crop.size)
        left = round(512 - (crop.width - 1) / 2)
        top = 901 - crop.height
        final = Image.new('RGBA', (1024, 1024), (0, 0, 0, 0))
        final.alpha_composite(crop, (left, top))
        # Map the body measurement band through the exact raster transform.
        mapped = [round(v * factor) for v in band]
        body_final_bbox = alpha.crop(mapped).getbbox()
        measured_body_width = body_final_bbox[2] - body_final_bbox[0]
        final.save(DEST / (name + '.png'))
        verify = Image.open(DEST / (name + '.png'))
        b = verify.getchannel('A').getbbox()
        corners = [verify.getpixel(p)[3] for p in [(0,0),(1023,0),(0,1023),(1023,1023)]]
        row = dict(file=name+'.png', size=list(verify.size), mode=verify.mode,
                   bottom_y=b[3]-1, center_x=(b[0]+b[2]-1)/2,
                   body_width=measured_body_width, alpha_bbox=list(b),
                   corner_alpha=corners, source_body_width=raw_body_width,
                   uniform_scale=factor, source_body_band=band,
                   body_measurement_note=spec.get('measurement_note', 'Visible torso contour in a band unobstructed by limbs.'))
        assert row['size']==[1024,1024] and row['mode']=='RGBA'
        assert row['bottom_y']==900 and abs(row['center_x']-512)<=0.5
        assert all(a==0 for a in corners)
        assert 762 <= measured_body_width <= 842, row
        report.append(row)

    original = json.loads((DOCS / 'angry_original_hashes.json').read_text())
    changed = [p for p, h in original.items()
               if not (ROOT/p).exists() or hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
    changed_pngs = [p for p in changed if p.endswith('.png')]
    assert not changed_pngs, changed_pngs
    (DOCS/'angry_measurements.json').write_text(json.dumps({
        'reference_body_width': 802, 'alpha_threshold': 8,
        'body_width_method': 'Torso ROI selected by visual inspection, excluding limbs; occluded contours estimated manually. Pixel width measured in that ROI after uniform resizing.',
        'original_files_unchanged': len(original) - len(changed),
        'concurrent_changes_not_written_by_this_task': changed,
        'files': report}, indent=2)+'\n')

    names = ['reference'] + list(config)
    sheet = Image.new('RGB', (512*len(names), 576), '#808080')
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', 19)
    for index, name in enumerate(names):
        path = ROOT/'refs/angry/front.png' if name=='reference' else DEST/(name+'.png')
        im = Image.open(path).convert('RGBA').resize((512,512), Image.Resampling.LANCZOS)
        sheet.paste(im,(512*index,0),im)
        draw.text((512*index+20,520), name, fill='white', font=font)
        if index:
            r=report[index-1]
            label=f"y={r['bottom_y']} x={r['center_x']} body={r['body_width']}"
        else:
            label='original body=802'
        draw.text((512*index+20,550),label,fill='white',font=font)
    sheet.save(DOCS/'check_angry.png')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    run()
