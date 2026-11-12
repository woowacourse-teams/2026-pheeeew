// Generated-art preparation only. Does not modify existing game code or source PNGs.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const sharp=require('./.image-work/node_modules/sharp');
const ROOT=path.resolve(__dirname,'..'),WORK=path.join(ROOT,'docs/codex/xmas_work'),OUT=path.join(ROOT,'assets_src/xmas'),DOC=path.join(ROOT,'docs/codex');
const sources=JSON.parse(fs.readFileSync(path.join(WORK,'sources.json')));
const clamp=(v,a=0,b=255)=>Math.max(a,Math.min(b,v));
const smooth=t=>{t=clamp(t,0,1);return t*t*(3-2*t)};
const raw=(d,w,h)=>sharp(d,{raw:{width:w,height:h,channels:4}});
async function pixels(p,w,h){let s=sharp(p);if(w)s=s.resize(w,h,{fit:'fill'});return s.ensureAlpha().raw().toBuffer({resolveWithObject:true})}
function bounds(d,w,h,t=32){let x0=w,y0=h,x1=-1,y1=-1;for(let y=0;y<h;y++)for(let x=0;x<w;x++)if(d[(y*w+x)*4+3]>t){x0=Math.min(x0,x);x1=Math.max(x1,x);y0=Math.min(y0,y);y1=Math.max(y1,y)}return {left:x0,top:y0,width:x1-x0+1,height:y1-y0+1}}
function clean(d){for(let i=0;i<d.length;i+=4){if(d[i+3]<28)d.fill(0,i,i+4);else d[i+3]=Math.round(255*smooth((d[i+3]-20)/235));}return d}
function edge(d,w,h,axis,band){const original=Buffer.from(d),len=axis==='x'?w:h,other=axis==='x'?h:w;for(let q=0;q<other;q++)for(let k=0;k<band;k++){let a=axis==='x'?(q*w+k)*4:(k*w+q)*4,b=axis==='x'?(q*w+len-1-k)*4:((len-1-k)*w+q)*4,first=axis==='x'?q*w*4:q*4,last=axis==='x'?(q*w+len-1)*4:((len-1)*w+q)*4;const f=.5*(1-smooth(k/band));for(let c=0;c<4;c++){const delta=original[last+c]-original[first+c];d[a+c]=clamp(Math.round(original[a+c]+delta*f));d[b+c]=clamp(Math.round(original[b+c]-delta*f));}}}
function tone(d,sat=.70,mix=.04){for(let i=0;i<d.length;i+=4){let l=d[i]*.2126+d[i+1]*.7152+d[i+2]*.0722;for(let c=0;c<3;c++)d[i+c]=clamp((l+(d[i+c]-l)*sat)*(1-mix)+[224,210,197][c]*mix,[74,47,36][c]);}return d}
async function save(name,d,w,h,opaque=false){let s=raw(d,w,h);if(opaque)s=s.removeAlpha();await s.png().toFile(path.join(OUT,name+'.png'))}
async function materials(){
 for(const name of ['rock','fabric','bg']){
  const w=name==='bg'?1024:512,h=name==='bg'?1536:512,{data:d}=await pixels(sources[name],w,h);
  if(name==='rock'||name==='bg'){
   const means=[0,0,0];for(let i=0;i<d.length;i+=4)for(let c=0;c<3;c++)means[c]+=d[i+c]/(w*h);
   const base=name==='rock'?[185,138,94]:[201,210,238],factor=name==='rock'?.65:.55;
   for(let i=0;i<d.length;i+=4)for(let c=0;c<3;c++)d[i+c]=clamp(base[c]+(d[i+c]-means[c])*factor);
  }else {
   tone(d,.46,.18);
   // Source candy stripe period measured at 128px. Blend overlapping samples
   // separated by THREE periods, so both color bands and gloss remain aligned.
   const cyc=Buffer.alloc(384*384*4);
   for(let y=0;y<384;y++)for(let x=0;x<384;x++){
    const wx=x<128?smooth(x/128):1,wy=y<128?smooth(y/128):1;
    for(let c=0;c<4;c++){let value=0;for(const [sx,fx]of [[x,wx],[x+384,1-wx]])for(const [sy,fy]of [[y,wy],[y+384,1-wy]])if(fx*fy>0)value+=d[(sy*512+sx)*4+c]*fx*fy;cyc[(y*384+x)*4+c]=Math.round(value)}
   }
   const periodic=await raw(cyc,384,384).resize(512,512).raw().toBuffer();periodic.copy(d);edge(d,512,512,'x',2);edge(d,512,512,'y',2);
  }
  if(name==='rock')edge(d,w,h,'x',36);if(name!=='fabric')edge(d,w,h,'y',name==='bg'?100:36);
  await save(name,d,w,h,true);
 }
 let {data:d}=await pixels(sources.trim,512,171);clean(d);
 const bb=bounds(d,512,171,80),art=await raw(d,512,171).extract({left:0,top:bb.top,width:512,height:bb.height}).resize(512,96,{fit:'fill'}).raw().toBuffer();
 let out=Buffer.alloc(512*128*4);
 for(let x=0;x<512;x++){
  let first=0;while(first<95&&art[(first*512+x)*4+3]<100)first++;
  const target=26+Math.round(Math.min(10,first*.50));
  for(let y=target;y<123;y++){const sy=y-target+first;if(sy>=96)continue;const a=(sy*512+x)*4,b=(y*512+x)*4;art.copy(out,b,a,a+4);out[b+3]=Math.round(out[b+3]*(1-smooth((y-68)/55)));}
 }
 edge(out,512,128,'x',26);for(let i=3;i<out.length;i+=4)if(out[i]<8)out[i]=0;
 await save('trim',out,512,128);
}
const specs=[
 ['top_1',210,36,.75],['top_2',140,65,.76],['top_3',170,50,.75],['top_4',130,65,.82],['top_5',140,56,.79],['top_6',130,68,.82],
 ['hang_1',170,140,.35],['hang_2',220,82,.42],['hang_3',150,142,.31],['hang_4',95,148,.39],['hang_5',110,148,.23],
 ['side_1',90,142,.27],['side_2',102,95,.27],['side_3',108,150,.22],['side_4',68,165,.30]
];
async function attached([name,span,rise,fraction],rockMean){
 const group=name.split('_')[0],orig=await pixels(sources[name]),ow=orig.info.width,oh=orig.info.height;
 clean(orig.data);let bb=bounds(orig.data,ow,oh),anchor=Math.round((group==='side'?ow:oh)*fraction),crop;
 if(group==='top')crop={left:bb.left,top:bb.top,width:bb.width,height:anchor-bb.top+1};
 if(group==='hang')crop={left:bb.left,top:anchor,width:bb.width,height:bb.top+bb.height-anchor};
 if(group==='side')crop={left:anchor,top:bb.top,width:bb.left+bb.width-anchor,height:bb.height};
 let tw=group==='side'?span:span,th=rise;
 // Preserve original object proportions; attachment footprint may be wider than object.
 if(group==='top'){const scale=Math.min(span/crop.width,rise/crop.height);tw=Math.round(crop.width*scale);th=Math.round(crop.height*scale)}
 const art=await raw(orig.data,ow,oh).extract(crop).resize(tw,th,{fit:'fill'}).raw().toBuffer();tone(art,.65,.06);
 const out=Buffer.alloc(256*256*4),left=group==='side'?56:Math.round(128-tw/2),top=group==='top'?200-th+1:group==='hang'?56:Math.round(128-th/2);
 // Reuse the generated snow-mound art as a low footprint without stretching the object.
 if(group==='top'&&tw<120){
  const {data:snow}=await pixels(path.join(OUT,'kit/top_1.png'));
  const base=await raw(snow,256,256).extract({left:25,top:164,width:210,height:37}).resize(span,9,{fit:'fill'}).raw().toBuffer();
  const sx=Math.round(128-span/2);
  for(let yy=0;yy<9;yy++)for(let xx=0;xx<span;xx++){const i=((192+yy)*256+sx+xx)*4,j=(yy*span+xx)*4;const f=smooth((yy-3)/5);for(let c=0;c<3;c++)out[i+c]=base[j+c]*(1-f)+rockMean[c]*f;out[i+3]=base[j+3];}
 }
 for(let y=0;y<th;y++)for(let x=0;x<tw;x++){
  const xx=x+left,yy=y+top;if(xx<0||xx>255||yy<0||yy>255)continue;const i=(yy*256+xx)*4,j=(y*tw+x)*4;
  const dist=group==='top'?200-yy:group==='hang'?yy-56:xx-56,blend=1-smooth(dist/10);
  const a=art[j+3]/255,old=out[i+3]/255,total=a+old*(1-a);
  for(let c=0;c<3;c++){const color=art[j+c]*(1-blend)+rockMean[c]*blend;out[i+c]=total?Math.round((color*a+out[i+c]*old*(1-a))/total):0;}
  out[i+3]=Math.round(total*255);
 }
 // Continue the exact attachment silhouette inside terrain, fading to alpha zero.
 for(let q=0;q<256;q++)for(let k=1;k<=55;k++){
  const ax=group==='side'?56:q,ay=group==='top'?200:group==='hang'?56:q;
  const x=group==='side'?56-k:q,y=group==='top'?200+k:group==='hang'?56-k:q;
  const i=(y*256+x)*4,j=(ay*256+ax)*4;
  const lateral=group==='side'?smooth(Math.min(q,255-q)/8):smooth(Math.min(q,255-q)/8);
  for(let c=0;c<3;c++)out[i+c]=rockMean[c];
  out[i+3]=Math.round(out[j+3]*(1-smooth(k/55))*lateral);
 }
 for(let i=3;i<out.length;i+=4)if(out[i]<8)out[i]=0;
 const b=bounds(out,256,256),shift=Math.round(128-(group==='side'?b.top+(b.height-1)/2:b.left+(b.width-1)/2));
 const centered=Buffer.alloc(out.length);for(let y=0;y<256;y++)for(let x=0;x<256;x++){const nx=x+(group==='side'?0:shift),ny=y+(group==='side'?shift:0);if(nx>=0&&nx<256&&ny>=0&&ny<256)out.copy(centered,(ny*256+nx)*4,(y*256+x)*4,(y*256+x)*4+4)}
 await save('kit/'+name,centered,256,256);return {name,sourceAnchor:anchor,sourceSize:[ow,oh],anchor:group==='top'?{y:200}:group==='hang'?{y:56}:{x:56},bounds:bounds(centered,256,256),artSize:[tw,th]};
}
async function centered(name,w,h,targetW,targetH,left,top){
 const src=await pixels(sources[name]);clean(src.data);const bb=bounds(src.data,src.info.width,src.info.height);
 const art=await raw(src.data,src.info.width,src.info.height).extract(bb).resize(targetW,targetH,{fit:name==='nod'?'fill':'inside'}).ensureAlpha().raw().toBuffer({resolveWithObject:true});
 tone(art.data,.70,.03);const aw=art.info.width,ah=art.info.height;
 const x=left??Math.round((w-aw)/2),y=top??Math.round((h-ah)/2),out=Buffer.alloc(w*h*4);
 for(let yy=0;yy<ah;yy++)for(let xx=0;xx<aw;xx++){const i=((yy+y)*w+xx+x)*4,j=(yy*aw+xx)*4;if(i>=0&&i<out.length)art.data.copy(out,i,j,j+4)}
 await save(name.startsWith('in_')?'kit/'+name:name,out,w,h);
}
async function distant(name){
 let {data:d}=await pixels(sources[name],768,768);clean(d);
 for(let y=0;y<768;y++)for(let x=0;x<768;x++){const i=(y*768+x)*4;for(let c=0;c<3;c++)d[i+c]=[185,195,218][c]+(d[i+c]-205)*.4;d[i+3]=Math.round(d[i+3]*.58*smooth(Math.min(x,y,767-x,767-y)/32));}
 d=await raw(d,768,768).blur(1.6).raw().toBuffer();for(let i=3;i<d.length;i+=4)if(d[i]<4)d[i]=0;await save('kit/'+name,d,768,768);
}
async function tile(name,nx,ny,outName){const p=path.join(OUT,name+'.png'),m=await sharp(p).metadata(),layers=[];for(let y=0;y<ny;y++)for(let x=0;x<nx;x++)layers.push({input:p,left:x*m.width,top:y*m.height});await sharp({create:{width:m.width*nx,height:m.height*ny,channels:4,background:{r:0,g:0,b:0,alpha:0}}}).composite(layers).png().toFile(path.join(DOC,outName));}
async function repeatBuffer(name,w,h,scale=.5){const p=path.join(OUT,name+'.png'),m=await sharp(p).metadata(),tw=Math.round(m.width*scale),th=Math.round(m.height*scale),{data:d}=await pixels(p,tw,th),out=Buffer.alloc(w*h*4);for(let y=0;y<h;y++)for(let x=0;x<w;x++){const i=(y*w+x)*4,j=((y%th)*tw+x%tw)*4;d.copy(out,i,j,j+4)}return raw(out,w,h).png().toBuffer()}
async function character(){
 // Extract the original blue character directly from lineup, flood-fill only exterior checkerboard.
 const m=await sharp(path.join(ROOT,'refs/lineup.png')).metadata(),rect={left:Math.round(m.width*.615),top:Math.round(m.height*.18),width:Math.round(m.width*.187),height:Math.round(m.height*.71)};
 const {data:d,info}=await sharp(path.join(ROOT,'refs/lineup.png')).extract(rect).ensureAlpha().raw().toBuffer({resolveWithObject:true}),w=info.width,h=info.height,seen=new Uint8Array(w*h),queue=[];
 function push(x,y){if(x<0||x>=w||y<0||y>=h)return;const k=y*w+x,i=k*4;if(seen[k])return;seen[k]=1;const mx=Math.max(d[i],d[i+1],d[i+2]),mn=Math.min(d[i],d[i+1],d[i+2]);if(mx-mn<24&&mn>160){d[i+3]=0;queue.push(k)}}
 for(let x=0;x<w;x++){push(x,0);push(x,h-1)}for(let y=0;y<h;y++){push(0,y);push(w-1,y)}for(let q=0;q<queue.length;q++){let k=queue[q],x=k%w,y=Math.floor(k/w);push(x-1,y);push(x+1,y);push(x,y-1);push(x,y+1)}
 return raw(d,w,h).extract(bounds(d,w,h)).resize({width:75}).png().toBuffer();
}
async function scene(){
 const W=1440,H=1000,bx=150,by=480,bw=900,bh=300,placements=[],layers=[];
 const bg=await repeatBuffer('bg',W,H,1);
 for(const [n,x,y,size]of [['far_1',145,110,384],['far_2',490,145,384],['far_3',890,30,384]])layers.push({input:await sharp(path.join(OUT,'kit/'+n+'.png')).resize(size,size).png().toBuffer(),left:x,top:y});
 layers.push({input:await repeatBuffer('rock',bw,bh),left:bx,top:by});
 const triangle=await pixels(await repeatBuffer('fabric',300,300));for(let y=0;y<300;y++)for(let x=0;x<300;x++)if(y<300-x)triangle.data[(y*300+x)*4+3]=0;
 layers.push({input:await raw(triangle.data,300,300).png().toBuffer(),left:bx+bw,top:by});
 // Thin theme contour: game normally draws this, preview reproduces it only here.
 const outline=Buffer.from(`<svg width="1440" height="1000"><path d="M150 480H1050V780H150Z M1050 780L1350 480V780Z" fill="none" stroke="#4a2f24" stroke-width="2" opacity=".8"/></svg>`);layers.push({input:outline,left:0,top:0});
 async function add(n,x,y,flip=false){let s=sharp(path.join(OUT,'kit/'+n+'.png')).resize(128,128);if(flip)s=s.flop();layers.push({input:await s.png().toBuffer(),left:x,top:y});placements.push({name:n,x,y,flip,scale:.5})}
 for(let i=1;i<=6;i++)await add('top_'+i,bx+75+(i-1)*150-64,by-100);
 for(let i=1;i<=5;i++)await add('hang_'+i,bx+90+(i-1)*180-64,by+bh-28);
 for(let i=1;i<=4;i++){await add('side_'+i,bx-100,by+50+(i-1)*65-64,true);await add('side_'+i,bx+bw-28,by+50+(i-1)*65-64)}
 for(let i=1;i<=6;i++)await add('in_'+i,bx+100+(i-1)*140-64,by+140-64);
 // Cover attachment seams with the continuous snow cap; anchors stay exactly at y=480.
 layers.push({input:await repeatBuffer('trim',900,64),left:bx,top:by-20});
 const ch=await character(),cm=await sharp(ch).metadata();layers.push({input:ch,left:bx+420,top:by-cm.height});
 const sign=await sharp(path.join(OUT,'arrow_sign.png')).resize(256,256).png().toBuffer();layers.push({input:sign,left:1120,top:700});
 layers.push({input:await sharp(path.join(OUT,'nod.png')).resize(240,100).png().toBuffer(),left:550,top:860});
 await sharp(bg).composite(layers).png().toFile(path.join(DOC,'check_xmas.png'));
 fs.writeFileSync(path.join(WORK,'placements.json'),JSON.stringify({canvas:[W,H],block:{x:bx,y:by,width:bw,height:bh},character:{source:'refs/lineup.png',width:75},placements},null,2));
}
async function contact(){
 const names=Object.keys(sources),cw=240,ch=235,layers=[];
 for(let i=0;i<names.length;i++){const n=names[i],kit=/^(top|hang|side|in|far)_/.test(n),p=path.join(OUT,(kit?'kit/':'')+n+'.png'),img=await sharp(p).resize(220,200,{fit:'inside'}).png().toBuffer(),m=await sharp(img).metadata(),x=(i%6)*cw,y=Math.floor(i/6)*ch;layers.push({input:img,left:x+Math.round((cw-m.width)/2),top:y+24+Math.round((200-m.height)/2)});layers.push({input:Buffer.from(`<svg width="240" height="24"><text x="12" y="18" font-size="14" fill="#4a2f24">${n}.png</text></svg>`),left:x,top:y})}
 await sharp({create:{width:cw*6,height:ch*5,channels:4,background:'#e0d9d4'}}).composite(layers).png().toFile(path.join(DOC,'xmas_contact_sheet.png'));
}
async function validate(anchors){
 const results=[];for(const n of Object.keys(sources)){const kit=/^(top|hang|side|in|far)_/.test(n),p=path.join(OUT,(kit?'kit/':'')+n+'.png'),{data:d,info}=await pixels(p),w=info.width,h=info.height;let min=255,max=0;for(let i=3;i<d.length;i+=4){min=Math.min(min,d[i]);max=Math.max(max,d[i])}const seam={};for(const axis of (n==='bg'?['y']:['rock','fabric'].includes(n)?['x','y']:n==='trim'?['x']:[])){let total=0,count=0;const len=axis==='x'?h:w;for(let q=0;q<len;q++)for(let c=0;c<4;c++){const a=axis==='x'?(q*w)*4+c:q*4+c,b=axis==='x'?(q*w+w-1)*4+c:((h-1)*w+q)*4+c;total+=Math.abs(d[a]-d[b]);count++}seam[axis]=total/count}results.push({name:n,size:[w,h],alpha:[min,max],bounds:bounds(d,w,h),seamEdgeMeanDifference:seam});}
 const before=JSON.parse(fs.readFileSync(path.join(WORK,'original_hashes.json'))),changed=Object.entries(before).filter(([p,h])=>crypto.createHash('sha256').update(fs.readFileSync(path.join(ROOT,p))).digest('hex')!==h).map(([p])=>p);
 const report={count:results.length,protectedFilesChecked:Object.keys(before).length,protectedFilesChanged:changed,externalChangeNote:'No public/*.js write operations exist in this preparation script. main.js and rig.js changed externally during generation; left untouched.',assets:results,anchors};fs.writeFileSync(path.join(WORK,'validation.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));if(changed.some(p=>p.endsWith('.png')))process.exitCode=1;
}
(async()=>{fs.mkdirSync(path.join(OUT,'kit'),{recursive:true});await materials();const stats=await sharp(path.join(OUT,'rock.png')).stats(),mean=stats.channels.slice(0,3).map(c=>Math.round(c.mean)),anchors=[];for(const s of specs)anchors.push(await attached(s,mean));for(let i=1;i<=6;i++)await centered('in_'+i,256,256,i===6?145:130,i===4?150:135);await centered('arrow_sign',512,512,220,330,undefined,121);await centered('nod',480,200,478,160,1,20);for(let i=1;i<=3;i++)await distant('far_'+i);await tile('rock',2,2,'xmas_tile_rock.png');await tile('fabric',2,2,'xmas_tile_fabric.png');await tile('trim',3,1,'xmas_tile_trim.png');await tile('bg',1,2,'xmas_tile_bg.png');await scene();await contact();await validate(anchors)})().catch(e=>{console.error(e);process.exitCode=1});
