"""Normalize generated zone2 art; never modify original PNGs or game code.

Run: PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=tools/.image-work/deps python3 tools/prepare_zone2.py
"""
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageOps, ImageStat
from prepare_zone1 import clean, periodic, wrap_axis, checker, tile, LANCZOS

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs/codex'
DEST = ROOT / 'public/assets/zone2'
SOURCES = json.loads((DOCS / 'zone2_sources.json').read_text())


def build():
    DEST.mkdir(exist_ok=True)
    im = Image.open(SOURCES['rock']).convert('RGB').resize((512, 512), LANCZOS)
    periodic(im, 'xy', 40).save(DEST / 'rock.png')

    # Measured diagonal period: 332 pixels on the 512px source preview.
    # Take a complete period with overlap, then repeat twice per final tile.
    raw = Image.open(SOURCES['fabric']).convert('RGB')
    scale = raw.width / 512
    step = 332 / 256 * scale
    margin = 20
    im = raw.transform((296, 296), Image.Transform.AFFINE,
        (step, 0, 80 * scale - margin * step,
         0, step, 80 * scale - margin * step), Image.Resampling.BICUBIC)
    im = wrap_axis(im, 'x', margin, True)
    im = wrap_axis(im, 'y', margin, True)
    tile(im, 2, 2).save(DEST / 'fabric.png')

    bg = Image.open(SOURCES['bg']).convert('RGB').resize((1024, 1536), LANCZOS)
    periodic(bg, 'y', 80).save(DEST / 'bg.png')

    raw = clean(Image.open(SOURCES['trim']))
    raw = raw.resize((512, round(raw.height * 512 / raw.width)), LANCZOS)
    # Puffy upper piping starts near original y=245; map that to platform y=32.
    origin = round(245 * 512 / 2172) - 32
    im = raw.crop((0, origin, 512, origin + 128))
    fade = Image.new('L', im.size)
    fade.putdata([round(255 * (1 if y <= 32 else max(0, (127-y)/95) ** 2))
                  for y in range(128) for x in range(512)])
    im.putalpha(ImageChops.multiply(im.getchannel('A'), fade))
    periodic(im, 'x', 24).save(DEST / 'trim.png')

    for name, width in [('book_stack',260), ('coffee_cup',200), ('alarm_clock',200), ('arrow_sign',220)]:
        raw = clean(Image.open(SOURCES[name]))
        raw = raw.crop(raw.getchannel('A').getbbox())
        raw = clean(raw.resize((width, round(raw.height * width / raw.width)), LANCZOS))
        raw = raw.crop(raw.getchannel('A').getbbox())
        canvas = Image.new('RGBA', (512,512))
        canvas.paste(raw, (256-raw.width//2, 451-raw.height))
        canvas.save(DEST / f'{name}.png')

    raw = clean(Image.open(SOURCES['moon_mobile']))
    raw = raw.crop(raw.getchannel('A').getbbox())
    raw = clean(raw.resize((round(raw.width*370/raw.height),370),LANCZOS))
    raw = raw.crop(raw.getchannel('A').getbbox())
    bounds = raw.getchannel('A').crop((0,0,raw.width,1)).getbbox()
    tip = (bounds[0]+bounds[2]-1)//2
    canvas = Image.new('RGBA',(512,512))
    canvas.paste(raw,(256-tip,60))
    canvas.save(DEST/'moon_mobile.png')

    raw = clean(Image.open(SOURCES['nod']))
    raw = raw.crop(raw.getchannel('A').getbbox())
    raw = clean(raw.resize((480,round(raw.height*480/raw.width)),LANCZOS))
    # Align the long standing surface to y=20. Corner pinches remain within the
    # allowed body region; preserve generated pigment and outline in each column.
    canvas = Image.new('RGBA',(480,200))
    for x in range(480):
        column = raw.crop((x,0,x+1,raw.height))
        box = column.getchannel('A').getbbox()
        if box:
            canvas.paste(column,(x,20-box[1]))
    canvas.save(DEST/'nod.png')
    checks()


def checks():
    assets={p.stem:Image.open(p) for p in DEST.glob('*.png')}
    assert len(assets)==10
    for name,nx,ny in [('rock',2,2),('fabric',2,2),('trim',3,1),('bg',1,2)]:
        tile(assets[name],nx,ny).save(DOCS/f'zone2_tile_{name}.png')

    sheet=Image.new('RGB',(1680,1280),'#faf8fd')
    d=ImageDraw.Draw(sheet)
    fp='/System/Library/Fonts/Supplemental/Arial.ttf'
    title=ImageFont.truetype(fp,30);label=ImageFont.truetype(fp,19);small=ImageFont.truetype(fp,15)
    d.text((24,22),'ZONE 2 / Sleepless bedroom - asset & scale check',font=title,fill='#49385f')
    names=['rock','fabric','bg','trim','nod','book_stack','coffee_cup','moon_mobile','alarm_clock','arrow_sign']
    for i,name in enumerate(names):
        x=24+(i%6)*274;y=85+(i//6)*335
        im=assets[name];thumb=im.convert('RGBA');thumb.thumbnail((256,256),LANCZOS)
        panel=checker((256,256)).convert('RGBA')
        panel.alpha_composite(thumb,((256-thumb.width)//2,(256-thumb.height)//2))
        sheet.paste(panel.convert('RGB'),(x,y))
        d.text((x,y+264),name+'.png',font=label,fill='#49385f')
        d.text((x,y+290),f'{im.width} x {im.height} / {im.mode}',font=small,fill='#7d728e')
    lineup=Image.open(ROOT/'refs/lineup.png')
    ref=lineup.crop((round(lineup.width*.013),round(lineup.height*.18),round(lineup.width*.188),round(lineup.height*.89)))
    ref.thumbnail((256,256),LANCZOS)
    sheet.paste(ref.convert('RGB'),(1120,435))
    d.text((1120,684),'lineup.png / tired',font=label,fill='#49385f')

    scene=ImageOps.fit(assets['bg'],(1632,480),LANCZOS).convert('RGBA')
    rock=assets['rock'].resize((256,256),LANCZOS)
    for x in range(0,1632,256):scene.paste(rock,(x,290))
    trim=assets['trim'].resize((256,64),LANCZOS)
    for x in range(0,1632,256):scene.alpha_composite(trim,(x,274))
    fabric=assets['fabric'].resize((256,256),LANCZOS)
    ramp=Image.new('RGBA',scene.size)
    for y in range(0,480,256):
        for x in range(1150,1632,256):ramp.paste(fabric,(x,y))
    mask=Image.new('L',scene.size)
    ImageDraw.Draw(mask).polygon([(1150,290),(1632,120),(1632,480),(1150,480)],fill=255)
    ramp.putalpha(mask);scene.alpha_composite(ramp)
    ImageDraw.Draw(scene).line([(0,290),(1150,290),(1632,120)],fill='#292133',width=4)
    character=clean(Image.open(ROOT/'refs/tired/front.png'))
    character=character.crop(character.getchannel('A').getbbox())
    character=character.resize((75,round(character.height*75/character.width)),LANCZOS)
    scene.alpha_composite(character,(350,290-character.height))
    for name,x in [('book_stack',150),('coffee_cup',500),('alarm_clock',660),('arrow_sign',1050)]:
        prop=assets[name].resize((256,256),LANCZOS)
        scene.alpha_composite(prop,(x-128,290-225))
    scene.alpha_composite(assets['moon_mobile'].resize((256,256),LANCZOS),(690,15))
    scene.alpha_composite(assets['nod'].resize((240,100),LANCZOS),(870,90))
    sd=ImageDraw.Draw(scene)
    sd.text((16,12),'Display scale: tile 256px / character 75px (composite, not gameplay)',font=label,fill='#49385f')
    sheet.paste(scene.convert('RGB'),(24,765))
    sheet.save(DOCS/'check_zone2.png')

    report={}
    for name,im in assets.items():
        expected=(1024,1536) if name=='bg' else (512,128) if name=='trim' else (480,200) if name=='nod' else (512,512)
        assert im.size==expected,name
        opaque=name in ('rock','fabric','bg')
        assert im.mode==('RGB' if opaque else 'RGBA'),name
        rec={'size':im.size,'mode':im.mode,'mean_rgb':[round(v,2) for v in ImageStat.Stat(im.convert('RGB')).mean]}
        if not opaque:
            a=im.getchannel('A');rec['alpha_extrema']=a.getextrema();rec['alpha_bbox']=a.getbbox()
            assert a.getextrema()==(0,255),name
            if name in ('book_stack','coffee_cup','alarm_clock','arrow_sign'):assert a.getbbox()[3]==451,name
            if name=='moon_mobile':assert a.getbbox()[1]==60
            if name=='nod':
                assert a.getbbox()[0]==0 and a.getbbox()[2]==480
                assert a.getbbox()[1]==20 and a.getbbox()[3]<=181
                tops=[a.crop((x,0,x+1,200)).getbbox()[1] for x in range(480)]
                assert min(tops)==max(tops)==20
                rec['standing_surface_y_range']=[min(tops),max(tops)]
        for axis in ('xy' if name in ('rock','fabric') else 'x' if name=='trim' else 'y' if name=='bg' else ''):
            a,b=(im.crop((0,0,1,im.height)),im.crop((im.width-1,0,im.width,im.height))) if axis=='x' else (im.crop((0,0,im.width,1)),im.crop((0,im.height-1,im.width,im.height)))
            assert a.tobytes()==b.tobytes(),(name,axis)
            rec[f'{axis}_edge_max_difference']=0
        report[name]=rec
    original=json.loads((DOCS/'zone2_original_hashes.json').read_text())
    changed=[p for p,h in original.items() if hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
    changed_png=[p for p in changed if p.endswith('.png')]
    assert not changed_png,changed_png
    report['existing_png_unchanged']=sum(p.endswith('.png') for p in original)
    report['existing_files_unchanged']=len(original)-len(changed)
    report['external_changes_observed']=changed
    report['game_js_written_by_this_task']=False
    (DOCS/'zone2_measurements.json').write_text(json.dumps(report,indent=2))
    print(f'Validated 10 assets and 5 check images; all original PNGs unchanged. External changes: {changed}')


if __name__=='__main__':build()
