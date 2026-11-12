"""Normalize generated sprites, measure visible torso, and build inspection sheet.

Run with PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=tools/.image-work/deps python3 tools/normalize_frustrated.py
Only this task's new files are written. Source/reference images remain untouched.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json
import hashlib

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs/codex'
OUT = ROOT / 'assets_src/characters/frustrated'
SOURCES = json.loads((DOCS / 'frustrated_sources.json').read_text())
# Visible torso-only bands, chosen by inspecting each source (exclude limbs).
SPECS = {
    'jump_charge': (650, 705, 825),
    'jump_rise': (460, 650, 775),
    'jump_apex': (500, 640, 795),
    'jump_fall': (540, 740, 775),
    'jump_land': (620, 711, 800),
    'jump_wall': (610, 645, 780),
    'jump_splat': (600, 720, 825),
}

def clean_alpha(im):
    im = im.convert('RGBA')
    im.putalpha(im.getchannel('A').point(lambda v: 0 if v < 8 else v))
    return im

reference = Image.open(ROOT / 'refs/frustrated/front.png').convert('RGBA')
ref_box = clean_alpha(reference).getchannel('A').crop((0,500,1024,650)).getbbox()
reference_width = ref_box[2] - ref_box[0]
rows = []
for name, (band_top, band_bottom, target_width) in SPECS.items():
    source = clean_alpha(Image.open(SOURCES[name]))
    torso = source.getchannel('A').crop((0,band_top,source.width,band_bottom)).getbbox()
    source_width = torso[2] - torso[0]
    scale = target_width / source_width
    scaled = clean_alpha(source.resize((round(source.width*scale),round(source.height*scale)),Image.Resampling.LANCZOS))
    bbox = scaled.getchannel('A').getbbox()
    cropped = scaled.crop(bbox)
    left = round(512 - (cropped.width-1)/2)
    top = 901 - cropped.height
    assert left >= 0 and top >= 0 and left+cropped.width <= 1024, (name,cropped.size,left,top)
    final = Image.new('RGBA',(1024,1024),(0,0,0,0))
    final.paste(cropped,(left,top))
    final.save(OUT / (name+'.png'))
    # Remeasure pixels in the final file, including alpha > 0 edge pixels.
    final = Image.open(OUT / (name+'.png'))
    a = final.getchannel('A')
    b = a.getbbox()
    final_band = [round(band_top*scale)-bbox[1]+top, round(band_bottom*scale)-bbox[1]+top]
    fb = a.crop((0,final_band[0],1024,final_band[1])).getbbox()
    body_width = fb[2]-fb[0]
    corners = [a.getpixel(p) for p in [(0,0),(1023,0),(0,1023),(1023,1023)]]
    result = {'file':name+'.png','size':list(final.size),'mode':final.mode,'bottom_y':b[3]-1,'center_x':(b[0]+b[2]-1)/2,'body_width':body_width,'reference_body_width':reference_width,'body_difference_percent':round((body_width/reference_width-1)*100,2),'alpha_bbox':list(b),'corner_alpha':corners,'source_torso_band_y':[band_top,band_bottom],'source_body_width':source_width,'scale':scale,'final_torso_band_y':final_band}
    assert final.size==(1024,1024) and final.mode=='RGBA' and corners==[0,0,0,0]
    assert abs(result['bottom_y']-900)<=4 and abs(result['center_x']-512)<=12
    assert abs(body_width/reference_width-1)<=.05, result
    rows.append(result)

# All eight canvases use the same 1:1 display scale and y origin.
sheet = Image.new('RGB',(8192,1104),(150,150,150))
draw = ImageDraw.Draw(sheet)
font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc',28)
items = [('reference_front',reference)] + [(r['file'],Image.open(OUT/r['file']).convert('RGBA')) for r in rows]
for i,(name,im) in enumerate(items):
    sheet.paste(im,(i*1024,60),im)
    draw.text((i*1024+30,16),name,fill=(25,25,25),font=font)
sheet.save(DOCS/'check_frustrated.png')
# Larger inspection previews retain enough detail for direct visual review.
for i in range(2):
    detail = sheet.crop((i*4096,0,(i+1)*4096,1104))
    detail.resize((2048,552),Image.Resampling.LANCZOS).save(DOCS/f'frustrated_check_detail_{i+1}.png')
(DOCS/'frustrated_measurements.json').write_text(json.dumps(rows,indent=2)+'\n')
originals = json.loads((DOCS/'frustrated_original_hashes.json').read_text())
changed = [p for p,h in originals.items() if hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
changed_pngs = [p for p in changed if p.endswith('.png')]
assert not changed_pngs, changed_pngs
print(json.dumps({'measurements':rows,'unchanged_original_pngs':sum(p.endswith('.png') for p in originals),'changed_files_outside_this_script':changed},indent=2))
