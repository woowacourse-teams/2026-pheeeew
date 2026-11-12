// Technical export of image_gen artwork: dimensions, anchors, tile wraps and proof sheets.
// Reads original art; writes only the new NYC assets and NYC verification files.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const sharp=require('./.image-work/node_modules/sharp');
const ROOT=path.resolve(__dirname,'..'),WORK=path.join(ROOT,'docs/codex/nyc_work'),OUT=path.join(ROOT,'assets_src/nyc');
const src=JSON.parse(fs.readFileSync(path.join(WORK,'sources.json')));
const clamp=(v,a=0,b=255)=>Math.max(a,Math.min(b,v));
const smooth=t=>{t=clamp(t,0,1);return t*t*(3-2*t)};
const raw=(data,w,h)=>sharp(data,{raw:{width:w,height:h,channels:4}});
const read=async p=>sharp(p).ensureAlpha().raw().toBuffer({resolveWithObject:true});
function clean(data){for(let i=0;i<data.length;i+=4){if(data[i+3]<16 || (Math.max(...data.subarray(i,i+3))-Math.min(...data.subarray(i,i+3))>150 && data[i+3]<245))data[i+3]=0;}return data;}
function bounds(data,w,h,region=[0,0,w,h],threshold=20){let l=w,t=h,r=-1,b=-1;for(let y=region[1];y<region[3];y++)for(let x=region[0];x<region[2];x++)if(data[(y*w+x)*4+3]>threshold){l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);}return {left:l,top:t,width:r-l+1,height:b-t+1};}
function mean(data){let s=[0,0,0],n=0;for(let i=0;i<data.length;i+=4){let a=data[i+3]/255;n+=a;for(let c=0;c<3;c++)s[c]+=data[i+c]*a;}return s.map(x=>x/n);}
function colorMean(data,target,contrast){const m=mean(data);for(let i=0;i<data.length;i+=4)for(let c=0;c<3;c++)data[i+c]=clamp(target[c]+(data[i+c]-m[c])*contrast);return data;}
function wrap(data,w,h,axis,margin){const old=Buffer.from(data),n=axis==='x'?w:h,cross=axis==='x'?h:w;for(let k=0;k<cross;k++)for(let d=0;d<margin;d++){const weight=.5*(1-smooth(d/margin));const a=axis==='x'?(k*w+d)*4:(d*w+k)*4,b=axis==='x'?(k*w+n-1-d)*4:((n-1-d)*w+k)*4;for(let c=0;c<4;c++){data[a+c]=old[a+c]*(1-weight)+old[b+c]*weight;data[b+c]=old[b+c]*(1-weight)+old[a+c]*weight;}}return data;}
async function save(name,data,w,h){fs.mkdirSync(path.dirname(path.join(OUT,name+'.png')),{recursive:true});await raw(data,w,h).png().toFile(path.join(OUT,name+'.png'));}
function copyPixels(out,W,H,art,w,h,l,t){for(let y=0;y<h;y++)for(let x=0;x<w;x++){if(x+l<0||y+t<0||x+l>=W||y+t>=H)continue;const a=(y*w+x)*4,b=((y+t)*W+x+l)*4;art.copy(out,b,a,a+4);}}
async function cropResize(data,w,h,bb,tw,th){return raw(data,w,h).extract(bb).resize(tw,th,{fit:'fill'}).raw().toBuffer();}
function periodicCrop(input,span){
  const n=512,m=32,size=n+2*m,extended=Buffer.alloc(size*size*4),step=span/n;
  for(let y=0;y<size;y++)for(let x=0;x<size;x++){
    const sx=40+(x-m)*step,sy=40+(y-m)*step,x0=Math.floor(sx),y0=Math.floor(sy),fx=sx-x0,fy=sy-y0,i=(y*size+x)*4;
    for(let c=0;c<4;c++){const sample=(xx,yy)=>input[(clamp(yy,0,511)*512+clamp(xx,0,511))*4+c];extended[i+c]=sample(x0,y0)*(1-fx)*(1-fy)+sample(x0+1,y0)*fx*(1-fy)+sample(x0,y0+1)*(1-fx)*fy+sample(x0+1,y0+1)*fx*fy;}
  }
  const horizontal=Buffer.alloc(n*size*4),out=Buffer.alloc(n*n*4);
  for(let y=0;y<size;y++)for(let x=0;x<n;x++){
    const i=(y*n+x)*4,j=(y*size+x+m)*4,k=(y*size+x+m+(x<m?n:-n))*4;
    const f=x<m?.5*(1-smooth(x/m)):x>=n-m?.5*(1-smooth((n-1-x)/m)):0;
    for(let c=0;c<4;c++)horizontal[i+c]=extended[j+c]*(1-f)+(f?extended[k+c]*f:0);
  }
  for(let y=0;y<n;y++)for(let x=0;x<n;x++){
    const i=(y*n+x)*4,j=((y+m)*n+x)*4,k=((y+m+(y<m?n:-n))*n+x)*4;
    const f=y<m?.5*(1-smooth(y/m)):y>=n-m?.5*(1-smooth((n-1-y)/m)):0;
    for(let c=0;c<4;c++)out[i+c]=horizontal[j+c]*(1-f)+(f?horizontal[k+c]*f:0);
  }
  // Exact edge pairs after forward overlap; never mirror diagonal stripe geometry.
  wrap(out,n,n,'x',1);wrap(out,n,n,'y',1);return out;
}
let rockMean,rockData;
async function materials(){
  let d=await sharp(src.rock).resize(512,512).ensureAlpha().raw().toBuffer();
  d=await cropResize(d,512,512,{left:0,top:4,width:512,height:380},512,512);
  colorMean(d,[166,120,112],.50);wrap(d,512,512,'x',22);wrap(d,512,512,'y',16);await save('rock',d,512,512);rockData=d;rockMean=mean(d);
  d=await sharp(src.fabric).resize(512,512).ensureAlpha().raw().toBuffer();
  // Find the actual stripe period in the generated material before cropping.
  let period=190,best=Infinity;for(let p=160;p<=225;p++){let e=0,n=0;for(let y=32;y<480;y+=7)for(let x=16;x<512-p-16;x+=3){const i=(y*512+x)*4,j=i+p*4;e+=Math.abs((d[i]-d[i+1])-(d[j]-d[j+1]));n++;}if(e/n<best){best=e/n;period=p;}}
  const span=period*2;d=periodicCrop(d,span);await save('fabric',d,512,512);
  d=await sharp(src.bg).resize(1024,1536,{fit:'fill'}).ensureAlpha().raw().toBuffer();colorMean(d,[246,217,200],.66);wrap(d,1024,1536,'y',110);for(let i=3;i<d.length;i+=4)d[i]=255;await save('bg',d,1024,1536);
  const tr=await sharp(src.trim).resize({width:512}).ensureAlpha().raw().toBuffer({resolveWithObject:true});clean(tr.data);
  const td=Buffer.alloc(512*128*4);for(let y=0;y<128;y++)for(let x=0;x<512;x++){const i=(y*512+x)*4,j=((y+25)*512+x)*4;if(j>=tr.data.length)continue;for(let c=0;c<4;c++)td[i+c]=tr.data[j+c];const f=smooth((y-70)/40);for(let c=0;c<3;c++)td[i+c]=td[i+c]*(1-f)+rockMean[c]*f;td[i+3]*=1-smooth((y-72)/(42+9*Math.sin(6*Math.PI*x/512)+5*Math.sin(10*Math.PI*x/512)));if(y<20||td[i+3]<3)td[i+3]=0;}
  wrap(td,512,128,'x',20);await save('trim',td,512,128);
  fs.writeFileSync(path.join(WORK,'material_processing.json'),JSON.stringify({rockMean,stripePeriodAt512:period,stripeCropSpan:span,bgTargetMean:[246,217,200]},null,2));
}
async function single(name,tw,th,canvas=256,bottom=null){const r=await read(src[name]);clean(r.data);const bb=bounds(r.data,r.info.width,r.info.height);if(!th)th=Math.round(bb.height*tw/bb.width);const a=await cropResize(r.data,r.info.width,r.info.height,bb,tw,th),d=Buffer.alloc(canvas*canvas*4);copyPixels(d,canvas,canvas,a,tw,th,Math.round((canvas-tw)/2),bottom===null?Math.round((canvas-th)/2):bottom-th+1);await save(name.startsWith('in_')?'kit/'+name:name,d,canvas,canvas);}
// Original source anchors measured visually; crop below/left of these planes only.
const tops=[['top_1',213,200,68],['top_2',198,140,38],['top_3',203,140,39],['top_4',218,216,58],['top_5',196,204,44],['top_6',201,208,42]];
const hangs=[['hang_1',74,110,153],['hang_2',90,220,108],['hang_3',83,160,139],['hang_4',90,220,88],['hang_5',75,86,151]];
const sides=[['side_1',56,78,112],['side_2',61,104,114],['side_3',0,78,108],['side_4',58,96,74]];
function attachment(d,kind){
  // Matching color at the exact plane, smooth alpha falloff only inside terrain.
  if(kind==='top'||kind==='hang'){
    const anchor=kind==='top'?200:56,dir=kind==='top'?1:-1;
    for(let x=0;x<256;x++){
      const j=(anchor*256+x)*4;let a=d[j+3];
      for(let dist=0;dist<=55;dist++){const y=anchor+dir*dist,i=(y*256+x)*4;for(let c=0;c<3;c++)d[i+c]=rockMean[c];d[i+3]=Math.round(a*(1-smooth(dist/55)));}
    }
  }else for(let y=0;y<256;y++){let a=d[(y*256+56)*4+3];for(let x=0;x<=56;x++){const i=(y*256+x)*4;for(let c=0;c<3;c++)d[i+c]=rockMean[c];d[i+3]=Math.round(a*smooth(x/56));}}
  for(let i=3;i<d.length;i+=4)if(d[i]<3)d[i]=0;
}
async function edges(){
 for(const [name,base,tw,th] of [...tops,...hangs,...sides]){
  const kind=name.split('_')[0],file=name==='side_3'?src.side_3_cleanup:src[name];
  let d=await sharp(file).resize(256,256,{fit:'fill'}).ensureAlpha().raw().toBuffer();clean(d);
  let bb;
  if(kind==='top'){bb=bounds(d,256,256,[0,0,256,base+1]);bb.height=base-bb.top+1;}
  if(kind==='hang'){bb=bounds(d,256,256,[0,base,256,256]);bb.top=base;bb.height=bounds(d,256,256,[0,base,256,256]).top+bounds(d,256,256,[0,base,256,256]).height-base;}
  if(kind==='side'){bb=bounds(d,256,256,[base+ (base?4:0),0,256,256]);if(base){bb.width=bb.left+bb.width-base;bb.left=base;}}
  let art=await cropResize(d,256,256,bb,tw,th),out=Buffer.alloc(256*256*4);
  // Preserve object color, lightly compress contrast toward warm midtone.
  for(let i=0;i<art.length;i+=4){const lum=.2126*art[i]+.7152*art[i+1]+.0722*art[i+2];for(let c=0;c<3;c++)art[i+c]=clamp(art[i+c]*.84+lum*.08+[168,137,126][c]*.08);}
  const left=kind==='side'?56:Math.round(128-tw/2),top=kind==='top'?201-th:kind==='hang'?56:Math.round(128-th/2);
  copyPixels(out,256,256,art,tw,th,left,top);
  for(let y=0;y<256;y++)for(let x=0;x<256;x++){
    const distance=kind==='top'?200-y:kind==='hang'?y-56:x-56;
    if(distance>=0&&distance<=5){const i=(y*256+x)*4,f=1-smooth(distance/5);for(let c=0;c<3;c++)out[i+c]=out[i+c]*(1-f)+rockMean[c]*f;}
  }
  attachment(out,kind);await save('kit/'+name,out,256,256);
 }
}
async function windows(){
  for(let n=1;n<=4;n++)await single('in_'+n,150,190);
  // Use the very same master frame pixels, including lintel/sill/mullions.
  const master=(await read(path.join(OUT,'kit/in_1.png'))).data;
  for(let n=2;n<=4;n++){
    const r=await read(path.join(OUT,'kit/in_'+n+'.png'));for(let y=0;y<256;y++)for(let x=0;x<256;x++){
      const rx=x-53,ry=y-33;
      const glass=((rx>=27&&rx<=72)||(rx>=77&&rx<=122))&&((ry>=24&&ry<=89)||(ry>=96&&ry<=165));
      if(!glass){const i=(y*256+x)*4;master.copy(r.data,i,i,i+4);}
    }await save('kit/in_'+n,r.data,256,256);
  }
  await single('in_5',126,174);await single('in_6',182,132);
  const r=await read(path.join(OUT,'kit/in_6.png'));
  // Material-match the outer chipped-brick contact band while retaining the hole.
  for(let y=0;y<256;y++)for(let x=0;x<256;x++){const i=(y*256+x)*4,q=Math.sqrt(((x-128)/91)**2+((y-128)/66)**2),f=smooth((q-.52)/.4);for(let c=0;c<3;c++)r.data[i+c]=r.data[i+c]*(1-f)+rockMean[c]*f;r.data[i+3]*=1-smooth((q-.77)/.25);}await save('kit/in_6',r.data,256,256);
}
async function distant(){for(let n=1;n<=3;n++){
  const r=await read(n===2?src.far_2_cleanup:src['far_'+n]);clean(r.data);const bb=bounds(r.data,r.info.width,r.info.height),tw=n===1?270:n===2?700:440,th=n===1?690:n===2?125:620;
  const art=await cropResize(r.data,r.info.width,r.info.height,bb,tw,th),m=mean(art),lumMean=.2126*m[0]+.7152*m[1]+.0722*m[2];
  for(let y=0;y<th;y++)for(let x=0;x<tw;x++){const i=(y*tw+x)*4,lum=.2126*art[i]+.7152*art[i+1]+.0722*art[i+2],v=clamp((lum-lumMean)*.3,-14,14);[185,153,166].forEach((c,k)=>art[i+k]=c+v);art[i+3]*=.32*(n===2?1:1-smooth((y/th-.75)/.25));}
  const d=Buffer.alloc(768*768*4);copyPixels(d,768,768,art,tw,th,Math.round((768-tw)/2),Math.round((768-th)/2));const soft=await raw(d,768,768).blur(1.5).raw().toBuffer();for(let i=3;i<soft.length;i+=4)if(soft[i]<3)soft[i]=0;await save('kit/far_'+n,soft,768,768);
}}
async function tile(name,nx,ny){const r=await read(path.join(OUT,name+'.png')),w=r.info.width,h=r.info.height,d=Buffer.alloc(w*nx*h*ny*4);for(let y=0;y<h*ny;y++)for(let x=0;x<w*nx;x++){const i=(y*w*nx+x)*4,j=((y%h)*w+x%w)*4;r.data.copy(d,i,j,j+4);}await raw(d,w*nx,h*ny).png().toFile(path.join(ROOT,'docs/codex/nyc_tile_'+name+'.png'));}
async function repeated(name,w,h,scale=.5){const r=await sharp(path.join(OUT,name+'.png')).resize({width:Math.round(512*scale)}).ensureAlpha().raw().toBuffer({resolveWithObject:true}),d=Buffer.alloc(w*h*4);for(let y=0;y<h;y++)for(let x=0;x<w;x++){const i=(y*w+x)*4,j=((y%r.info.height)*r.info.width+x%r.info.width)*4;r.data.copy(d,i,j,j+4);}return raw(d,w,h).png().toBuffer();}
async function scene(){
 const W=1440,H=1000,bx=170,by=440,bw=900,bh=300,layers=[],placements=[];
 const bg=await sharp(path.join(OUT,'bg.png')).resize(W,H,{fit:'cover'}).toBuffer();
 for(const [name,left,top] of [['far_1',180,105],['far_2',550,170],['far_3',860,120]]){layers.push({input:await sharp(path.join(OUT,'kit',name+'.png')).resize(384,384).toBuffer(),left,top});placements.push({name,left,top,scale:.5});}
 layers.push({input:await repeated('rock',bw,bh),left:bx,top:by});
 // A right-attached ascending awning triangle, with material at half scale.
 const tw=230,th=200,tri=await sharp(await repeated('fabric',tw,th)).ensureAlpha().raw().toBuffer();for(let y=0;y<th;y++)for(let x=0;x<tw;x++)if(y<th-1-x*(th-1)/(tw-1))tri[(y*tw+x)*4+3]=0;
 layers.push({input:await raw(tri,tw,th).png().toBuffer(),left:bx+bw,top:by+bh-th});
 // Terrain contour is drawn only in the proof, never baked into material files.
 const contour=Buffer.from(`<svg width="${W}" height="${H}"><path d="M${bx},${by}H${bx+bw}V${by+bh}H${bx}Z M${bx+bw},${by+bh}L${bx+bw+tw},${by+bh-th}V${by+bh}Z" fill="none" stroke="#3b2a2e" stroke-width="2" stroke-linejoin="round"/></svg>`);layers.push({input:contour,left:0,top:0});
 layers.push({input:await repeated('trim',bw,64),left:bx,top:by-20});
 const add=async(name,left,top,flip=false)=>{let p=sharp(path.join(OUT,'kit',name+'.png'));if(flip)p=p.flop();layers.push({input:await p.resize(128,128).toBuffer(),left,top});placements.push({name,left,top,flip,scale:.5});};
 for(let i=1;i<=6;i++)await add('top_'+i,bx+75+(i-1)*150-64,by-100);
 for(let i=1;i<=5;i++)await add('hang_'+i,bx+90+(i-1)*180-64,by+bh-28);
 for(let i=1;i<=4;i++)await add('side_'+i,i<=2?bx-100:bx+bw-28,by+(i%2?90:220)-64,i<=2);
 for(let i=1;i<=4;i++)await add('in_'+i,bx+170+(i-1)*185-64,by+125-64);
 await add('in_5',bx+330-64,by+243-64);await add('in_6',bx+600-64,by+243-64);
 layers.push({input:await sharp(path.join(OUT,'arrow_sign.png')).resize(256,256).toBuffer(),left:bx+672,top:by-225});
 // Lineup's tired sprite uses this exact transparent source, with no pose changes.
 const c=await read(path.join(ROOT,'refs/tired/front.png')),bb=bounds(c.data,c.info.width,c.info.height,[0,0,1024,1024],16),ch=Math.round(bb.height*75/bb.width);
 const character=await raw(c.data,1024,1024).extract(bb).resize(75,ch).png().toBuffer();layers.push({input:character,left:bx+263,top:by-ch});
 await sharp(bg).composite(layers).png().toFile(path.join(ROOT,'docs/codex/check_nyc.png'));
 fs.writeFileSync(path.join(WORK,'placements.json'),JSON.stringify({canvas:[W,H],block:{x:bx,y:by,width:bw,height:bh},assetScale:.5,character:{source:'refs/tired/front.png (same character as refs/lineup.png)',width:75,left:bx+263,top:by-ch},placements},null,2));
}
async function contact(){
 const groups=[['top',6],['hang',5],['side',4],['in',6],['far',3]],W=1440,H=1240,layer=[];let row=0;
 for(const [g,count] of groups){for(let i=1;i<=count;i++){const x=20+(i-1)*235,y=25+row*245;layer.push({input:await sharp(path.join(OUT,'kit',g+'_'+i+'.png')).resize(220,220).toBuffer(),left:x,top:y});const label=Buffer.from(`<svg width="220" height="24"><text x="110" y="18" text-anchor="middle" font-family="sans-serif" font-size="15" fill="#49323c">${g}_${i}</text></svg>`);layer.push({input:label,left:x,top:y+218});}row++;}
 const checker=Buffer.alloc(W*H*4);for(let y=0;y<H;y++)for(let x=0;x<W;x++){const i=(y*W+x)*4,v=((x>>4)+(y>>4))%2?236:249;checker[i]=v;checker[i+1]=v-3;checker[i+2]=v-6;checker[i+3]=255;}
 await raw(checker,W,H).composite(layer).png().toFile(path.join(ROOT,'docs/codex/nyc_contact_sheet.png'));
}
async function main(){await materials();await single('arrow_sign',220,null,512,450);await edges();await windows();await distant();for(const a of [['rock',2,2],['fabric',2,2],['trim',3,1],['bg',1,2]])await tile(...a);await scene();await contact();console.log('29 NYC assets and proof images exported.');}
main().catch(e=>{console.error(e);process.exitCode=1;});
