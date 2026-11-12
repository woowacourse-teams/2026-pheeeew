// Technical export and requested proofs. Artwork is generated with built-in image_gen.
// Never writes other themes, existing non-target PNGs, or public JavaScript.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const sharp = require('./.image-work/node_modules/sharp');
const root = path.resolve(__dirname, '..');
const work = path.join(root, 'docs/codex/nyc_night_work');
const src = JSON.parse(fs.readFileSync(path.join(work, 'sources.json')));
const out = path.join(root, 'assets_src/nyc');
const raw = (data, w, h) => sharp(data, {raw:{width:w,height:h,channels:4}});
const smooth = t => { t=Math.max(0,Math.min(1,t)); return t*t*(3-2*t); };
function bounds(data,w,h,threshold=12) {
  let l=w,t=h,r=0,b=0;
  for(let y=0;y<h;y++)for(let x=0;x<w;x++)if(data[(y*w+x)*4+3]>threshold){l=Math.min(l,x);t=Math.min(t,y);r=Math.max(r,x);b=Math.max(b,y);}
  return {left:l,top:t,width:r-l+1,height:b-t+1};
}
async function artwork() {
  const w=1024,h=1536;
  const d=await sharp(src.bg).resize(w,h).ensureAlpha().raw().toBuffer(), original=Buffer.from(d);
  // Only sky/haze enters this 100px seam overlap. Architecture stays untouched.
  for(let y=0;y<100;y++)for(let x=0;x<w;x++)for(let c=0;c<3;c++){
    const a=(y*w+x)*4+c,b=((h-1-y)*w+x)*4+c,f=.5*(1-smooth(y/99));
    d[a]=Math.round(original[a]*(1-f)+original[b]*f);
    d[b]=Math.round(original[b]*(1-f)+original[a]*f);
  }
  await raw(d,w,h).removeAlpha().png().toFile(path.join(out,'bg.png'));
  const exportInfo={};
  for(let n=1;n<=3;n++){
    const r=await sharp(src['far_'+n]).ensureAlpha().raw().toBuffer({resolveWithObject:true});
    const bb=bounds(r.data,r.info.width,r.info.height);
    const maxSize=n===1?[290,660]:n===2?[700,280]:[600,570];
    const sprite=await raw(r.data,r.info.width,r.info.height).extract(bb).resize(...maxSize,{fit:'inside'}).raw().toBuffer({resolveWithObject:true});
    const sw=sprite.info.width,sh=sprite.info.height;
    // Preserve generated alpha; taper the base to avoid an opaque cutout edge.
    for(let y=0;y<sh;y++)for(let x=0;x<sw;x++){
      const i=(y*sw+x)*4;
      const base=n===2?1:1-smooth((y/sh-.82)/.18);
      sprite.data[i+3]=Math.round(sprite.data[i+3]*base*.82);
      if(sprite.data[i+3]<2)sprite.data[i+3]=0;
    }
    const left=Math.round((768-sw)/2),top=Math.round((768-sh)/2);
    await sharp({create:{width:768,height:768,channels:4,background:'#00000000'}}).composite([{input:await raw(sprite.data,sw,sh).png().toBuffer(),left,top}]).png().toFile(path.join(out,'kit/far_'+n+'.png'));
    exportInfo['far_'+n]={sourceBounds:bb,exportedBounds:{left,top,width:sw,height:sh},opacityScale:.82};
  }
  fs.writeFileSync(path.join(work,'export.json'),JSON.stringify({seamBlend:100,assets:exportInfo},null,2));
}
async function lineupCharacters(){
  // Cut the actual supplied lineup; remove only neutral checkerboard connected
  // to the exterior. Closed black outlines protect original character pixels.
  const r=await sharp(path.join(root,'refs/lineup.png')).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const W=r.info.width,H=r.info.height,scale=W/2048;
  const sprites=[];
  for(let n=0;n<5;n++){
    const left=Math.round(n*W/5),right=Math.round((n+1)*W/5);
    const top=Math.round(72*scale),bottom=Math.round(359.2*scale),w=right-left,h=bottom-top;
    const d=await raw(r.data,W,H).extract({left,top,width:w,height:h}).raw().toBuffer();
    const seen=new Uint8Array(w*h),queue=[];
    const add=(x,y)=>{if(x<0||x>=w||y<0||y>=h)return;const q=y*w+x,i=q*4;if(seen[q])return;const max=Math.max(d[i],d[i+1],d[i+2]),min=Math.min(d[i],d[i+1],d[i+2]);if(max-min<=12&&min>135){seen[q]=1;queue.push(q);}};
    for(let x=0;x<w;x++){add(x,0);add(x,h-1);}for(let y=0;y<h;y++){add(0,y);add(w-1,y);}
    for(let k=0;k<queue.length;k++){const q=queue[k],x=q%w,y=Math.floor(q/w);d[q*4+3]=0;add(x-1,y);add(x+1,y);add(x,y-1);add(x,y+1);}
    const bb=bounds(d,w,h);
    const height=Math.round(bb.height*75/bb.width);
    sprites.push({input:await raw(d,w,h).extract(bb).resize(75,height).png().toBuffer(),height,sourceBounds:{left:left+bb.left,top:top+bb.top,width:bb.width,height:bb.height}});
  }
  return sprites;
}
async function proofs(){
  const bg=path.join(out,'bg.png'),old=path.join(root,'docs/codex/nyc_dusk_backup/bg.png');
  await sharp({create:{width:1024,height:3072,channels:3,background:'#000'}}).composite([{input:bg,left:0,top:0},{input:bg,left:0,top:1536}]).png().toFile(path.join(root,'docs/codex/nyc_night_tile_bg.png'));
  const chars=await lineupCharacters(),rock=await sharp(path.join(out,'rock.png')).resize(256,256).png().toBuffer(),trim=await sharp(path.join(out,'trim.png')).resize(256,64).png().toBuffer();
  const blockLayers=[];
  for(let y=0;y<300;y+=256)for(let x=0;x<900;x+=256){const w=Math.min(256,900-x),h=Math.min(256,300-y);blockLayers.push({input:await sharp(rock).extract({left:0,top:0,width:w,height:h}).toBuffer(),left:x,top:y});}
  const block=await sharp({create:{width:900,height:300,channels:4,background:'#00000000'}}).composite(blockLayers).png().toBuffer();
  const farPositions=[[36,230],[320,270],[627,275]];
  const panels=[];
  for(const background of [bg,old]){
    const layers=[];
    for(let n=1;n<=3;n++)layers.push({input:await sharp(path.join(out,'kit/far_'+n+'.png')).resize(384,384).png().toBuffer(),left:farPositions[n-1][0],top:farPositions[n-1][1]});
    layers.push({input:block,left:62,top:590});
    for(let x=0;x<900;x+=256)layers.push({input:await sharp(trim).extract({left:0,top:0,width:Math.min(256,900-x),height:64}).png().toBuffer(),left:62+x,top:570});
    for(let n=0;n<5;n++)layers.push({input:chars[n].input,left:160+160*n,top:590-chars[n].height});
    panels.push(await sharp(background).extract({left:0,top:200,width:1024,height:1000}).composite(layers).png().toBuffer());
  }
  const label=Buffer.from('<svg width="2072" height="64"><rect width="2072" height="64" fill="#242735"/><g fill="#f3e6d4" font-family="sans-serif" font-size="23"><text x="28" y="41">NEW · BLUE HOUR</text><text x="1076" y="41">BEFORE · DUSK (same foreground)</text></g></svg>');
  await sharp({create:{width:2072,height:1064,channels:3,background:'#242735'}}).composite([{input:panels[0],left:0,top:64},{input:panels[1],left:1048,top:64},{input:label,left:0,top:0}]).png().toFile(path.join(root,'docs/codex/check_nyc_night.png'));
  fs.writeFileSync(path.join(work,'proof_layout.json'),JSON.stringify({panels:['new night','old dusk'],panelSize:[1024,1000],backgroundCropTop:200,block:{left:62,top:590,width:900,height:300},textureScale:.5,trimStandingLine:590,farSize:[384,384],farPositions,characterSource:'refs/lineup.png',characterWidth:75,characterBounds:chars.map(c=>c.sourceBounds)},null,2));
}
async function validate(){
 const assets={},errors=[];
 for(const name of ['bg','kit/far_1','kit/far_2','kit/far_3']){
  const {data,info}=await sharp(path.join(out,name+'.png')).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const w=info.width,h=info.height;let min=255,max=0,zero=0,partial=0,edge=0,brightness=0,white=0;
  for(let y=0;y<h;y++)for(let x=0;x<w;x++){const i=(y*w+x)*4,a=data[i+3];min=Math.min(min,a);max=Math.max(max,a);zero+=a===0;partial+=a>0&&a<255;brightness+=.2126*data[i]+.7152*data[i+1]+.0722*data[i+2];white+=Math.min(data[i],data[i+1],data[i+2])>245;if(x===0||x===w-1||y===0||y===h-1)edge=Math.max(edge,a);}
  const isBg=name==='bg',expected=isBg?[1024,1536]:[768,768];
  if(w!==expected[0]||h!==expected[1])errors.push(name+' size');
  if(isBg?min!==255:(!zero||!partial||edge!==0))errors.push(name+' alpha');
  const metric=brightness/(w*h*255);
  assets[name]={size:[w,h],alpha:{min,max,zero,partial,outerEdgeMax:edge},meanSrgbLumaPercent:metric*100,nearWhitePixels:white};
  if(isBg){let seamMax=0,seamTotal=0;for(let x=0;x<w;x++)for(let c=0;c<3;c++){const v=Math.abs(data[x*4+c]-data[((h-1)*w+x)*4+c]);seamMax=Math.max(seamMax,v);seamTotal+=v;}assets[name].verticalSeam={maxDifference:seamMax,meanDifference:seamTotal/(w*3)};if(seamMax)errors.push('bg seam');if(metric<.25||metric>.4)errors.push('bg brightness');}
 }
 const before=JSON.parse(fs.readFileSync(path.join(work,'before_hashes.json')));
 const changed=Object.entries(before).filter(([p,h])=>crypto.createHash('sha256').update(fs.readFileSync(path.join(root,p))).digest('hex')!==h).map(([p])=>p);
 const allowed=['assets_src/nyc/bg.png',...Array.from({length:3},(_,i)=>'assets_src/nyc/kit/far_'+(i+1)+'.png')];
 if(changed.some(p=>!allowed.includes(p)))errors.push('protected file changed');
 const report={passed:errors.length===0,errors,changedExistingFiles:changed,protectedFilesUnchanged:Object.keys(before).length-changed.length,assets};
 fs.writeFileSync(path.join(work,'validation.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));if(errors.length)process.exitCode=1;
}
(async()=>{await artwork();await proofs();await validate();})().catch(e=>{console.error(e);process.exitCode=1;});
