// Normalize generated sprites to the user's anchors and build the inspection scene.
// Run: node tools/prepare_zone1_kit.cjs
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const sharp = require('./.image-work/node_modules/sharp');
const ROOT = path.resolve(__dirname, '..');
const WORK = path.join(ROOT, 'docs/codex/zone1_kit_work');
const OUT = path.join(ROOT, 'public/assets/zone1/kit');
const specs = JSON.parse(fs.readFileSync(path.join(WORK, 'specs.json')));
const sources = JSON.parse(fs.readFileSync(path.join(WORK, 'sources.json')));
const clamp = (v, a=0, b=255) => Math.max(a, Math.min(b, v));
const smooth = t => {t=clamp(t,0,1);return t*t*(3-2*t);};
const raw = (data,w,h) => sharp(data,{raw:{width:w,height:h,channels:4}});
async function rgba(p) { return sharp(p).ensureAlpha().raw().toBuffer({resolveWithObject:true}); }
function bounds(data,w,h,threshold=16) {
  let x0=w,y0=h,x1=-1,y1=-1;
  for(let y=0;y<h;y++)for(let x=0;x<w;x++)if(data[(y*w+x)*4+3]>threshold){x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);}
  return {x0,y0,x1,y1,width:x1-x0+1,height:y1-y0+1};
}
async function normalize(spec,rock,mean) {
  const [name,subject,span,height]=spec;
  const group=name.split('_')[0];
  if(group==='far') {
    const {data}=await sharp(sources[name]).resize(768,768).ensureAlpha().raw().toBuffer({resolveWithObject:true});
    let sum=0,weight=0;
    for(let i=0;i<data.length;i+=4){let a=data[i+3]/255;sum+=(data[i]*.2126+data[i+1]*.7152+data[i+2]*.0722)*a;weight+=a;}
    const avg=sum/weight;
    for(let y=0;y<768;y++)for(let x=0;x<768;x++){
      const i=(y*768+x)*4,lum=data[i]*.2126+data[i+1]*.7152+data[i+2]*.0722;
      const d=clamp((lum-avg)*.28,-15,15);
      [164,190,212].forEach((v,c)=>data[i+c]=clamp(v+d));
      data[i+3]=Math.round(data[i+3]*.52*smooth(Math.min(x,y,767-x,767-y)/28));
      if(data[i+3]<3)data[i+3]=0;
    }
    // Distant watercolor silhouettes need softer edges than the playable terrain.
    const softened=await raw(data,768,768).blur(2.6).raw().toBuffer();
    for(let i=3;i<softened.length;i+=4)if(softened[i]<2)softened[i]=0;
    await raw(softened,768,768).png().toFile(path.join(OUT,name+'.png'));
    let alphaMax=0;for(let i=3;i<data.length;i+=4)alphaMax=Math.max(alphaMax,data[i]);
    return {name,subject,size:[768,768],alphaMax,bounds:bounds(data,768,768)};
  }
  let pipeline=sharp(sources[name]).resize(256,256).ensureAlpha();
  // All types become top-oriented: air above, ground below.
  if(group==='hang')pipeline=pipeline.flip();
  if(group==='side')pipeline=pipeline.rotate(270);
  let {data}=await pipeline.raw().toBuffer({resolveWithObject:true});
  for(let i=3;i<data.length;i+=4)if(data[i]<12)data[i]=0;
  const bb=bounds(data,256,256,24);
  let base=200,best=0;
  for(let y=bb.y0+8;y<=Math.min(bb.y1,238);y++){
    let count=0;for(let x=0;x<256;x++)count+=data[(y*256+x)*4+3]>180?1:0;
    if(count>best){best=count;base=y;}
  }
  const targetWidth=group==='side'?height:span;
  const rise=group==='side'?span:height;
  const art=await raw(data,256,256).extract({left:bb.x0,top:bb.y0,width:bb.width,height:base-bb.y0+1}).resize(targetWidth,rise+1,{fit:'fill'}).raw().toBuffer();
  let lumSum=0,alphaSum=0;
  for(let i=0;i<art.length;i+=4){let a=art[i+3]/255;lumSum+=(art[i]*.2126+art[i+1]*.7152+art[i+2]*.0722)*a;alphaSum+=a;}
  const avg=lumSum/alphaSum,out=Buffer.alloc(256*256*4),left=Math.round(128-targetWidth/2),top=200-rise;
  for(let yy=0;yy<=rise;yy++)for(let xx=0;xx<targetWidth;xx++){
    const x=xx+left,y=yy+top,i=(y*256+x)*4,j=(yy*targetWidth+xx)*4;
    const lum=art[j]*.2126+art[j+1]*.7152+art[j+2]*.0722;
    const fade=smooth((y-184)/16);
    const ri=(((y*2)%512)*512+(x*2)%512)*4;
    for(let c=0;c<3;c++){
      const color=mean[c]+clamp((lum-avg)*.68,-35,29);
      const attachment=mean[c]+(rock[ri+c]-mean[c])*.35;
      out[i+c]=clamp(color*(1-fade)+attachment*fade);
    }
    out[i+3]=art[j+3];
  }
  // Carry the attachment footprint into the solid terrain, never a base outline.
  for(let y=201;y<256;y++)for(let x=0;x<256;x++){
    const i=(y*256+x)*4,j=(200*256+x)*4,ri=(((y*2)%512)*512+(x*2)%512)*4;
    for(let c=0;c<3;c++)out[i+c]=mean[c]+(rock[ri+c]-mean[c])*.35;
    out[i+3]=Math.round(out[j+3]*(1-smooth((y-200)/55)));
  }
  let final=raw(out,256,256);
  // Inverse transforms shift half-open raster coordinates to exact y/x=56.
  if(group==='hang')final=final.flip().affine([[1,0],[0,1]],{ody:1,background:'#00000000'});
  if(group==='side')final=final.rotate(90).affine([[1,0],[0,1]],{odx:1,background:'#00000000'});
  let finalData=await final.raw().toBuffer();
  const visible=bounds(finalData,256,256);
  const shift=Math.round(127.5-(group==='side'?(visible.y0+visible.y1):(visible.x0+visible.x1))/2);
  if(shift){
    const centered=Buffer.alloc(finalData.length);
    for(let y=0;y<256;y++)for(let x=0;x<256;x++){
      const nx=x+(group==='side'?0:shift),ny=y+(group==='side'?shift:0);
      if(nx>=0&&nx<256&&ny>=0&&ny<256)finalData.copy(centered,(ny*256+nx)*4,(y*256+x)*4,(y*256+x)*4+4);
    }
    finalData=centered;
  }
  await raw(finalData,256,256).png().toFile(path.join(OUT,name+'.png'));
  return {name,subject,size:[256,256],sourceAnchor:base,anchor:group==='top'?{y:200}:group==='hang'?{y:56}:{x:56},bounds:bounds(finalData,256,256),requestedRiseOrProjection:rise,requestedSpan:targetWidth};
}
async function scene() {
  const W=1280,H=960,bx=190,by=450,bw=900,bh=300;
  let bg=await sharp(path.join(ROOT,'public/assets/zone1/bg.png')).resize(W,H,{fit:'cover'}).png().toBuffer();
  let layers=[];
  for(const [name,left,top,size] of [['far_1',60,120,470],['far_2',370,55,580],['far_3',800,105,420]])if(sources[name])layers.push({input:await sharp(path.join(OUT,name+'.png')).resize(size,size).toBuffer(),left,top});
  let tileLayers=[];
  const tile=await sharp(path.join(ROOT,'public/assets/zone1/rock.png')).ensureAlpha().raw().toBuffer();
  const block=Buffer.alloc(bw*bh*4);
  for(let y=0;y<bh;y++)for(let x=0;x<bw;x++){let d=(y*bw+x)*4,s=((y%512)*512+x%512)*4;tile.copy(block,d,s,s+4);}
  layers.push({input:await raw(block,bw,bh).png().toBuffer(),left:bx,top:by});
  const placements=[];
  for(let i=1;i<=6;i++)if(sources['top_'+i]){let p={name:'top_'+i,left:bx+75+(i-1)*150-64,top:by-100};placements.push(p);layers.push({input:await sharp(path.join(OUT,p.name+'.png')).resize(128,128).toBuffer(),left:p.left,top:p.top});}
  for(let i=1;i<=5;i++)if(sources['hang_'+i]){let p={name:'hang_'+i,left:bx+90+(i-1)*180-64,top:by+bh-28};placements.push(p);layers.push({input:await sharp(path.join(OUT,p.name+'.png')).resize(128,128).toBuffer(),left:p.left,top:p.top});}
  for(let i=1;i<=4;i++)if(sources['side_'+i]){let p={name:'side_'+i,left:bx+bw-28,top:by+60+(i-1)*60-64,flipped:false};placements.push(p);layers.push({input:await sharp(path.join(OUT,p.name+'.png')).resize(128,128).toBuffer(),left:p.left,top:p.top});p={...p,left:bx-100,flipped:true};placements.push(p);layers.push({input:await sharp(path.join(OUT,p.name+'.png')).flop().resize(128,128).toBuffer(),left:p.left,top:p.top});}
  await sharp(bg).composite(layers).png().toFile(path.join(ROOT,'docs/codex/check_zone1_kit.png'));
  fs.writeFileSync(path.join(WORK,'placements.json'),JSON.stringify({canvas:[W,H],block:{x:bx,y:by,width:bw,height:bh},assetScale:.5,placements},null,2));
}
async function main(){
  fs.mkdirSync(OUT,{recursive:true});
  const rock=await sharp(path.join(ROOT,'public/assets/zone1/rock.png')).ensureAlpha().raw().toBuffer();
  const stats=await sharp(path.join(ROOT,'public/assets/zone1/rock.png')).stats(),mean=stats.channels.slice(0,3).map(c=>c.mean);
  let results=[];
  for(const spec of specs)if(sources[spec[0]])results.push(await normalize(spec,rock,mean));
  await scene();
  const before=JSON.parse(fs.readFileSync(path.join(WORK,'original_hashes.json')));
  const changed=Object.entries(before).filter(([p,h])=>crypto.createHash('sha256').update(fs.readFileSync(path.join(ROOT,p))).digest('hex')!==h).map(([p])=>p);
  fs.writeFileSync(path.join(WORK,'validation.json'),JSON.stringify({generatedCount:results.length,rockMean:mean,protectedFilesChecked:Object.keys(before).length,protectedFilesChanged:changed,concurrentChangeNote:'This asset script does not write public/*.js. JS hash differences were observed during generation and left untouched.',assets:results},null,2));
  console.log(JSON.stringify({generated:results.length,protectedFilesChanged:changed,assets:results},null,2));
  if(changed.some(p=>p.endsWith('.png')))process.exitCode=1;
}
main().catch(e=>{console.error(e);process.exitCode=1;});
