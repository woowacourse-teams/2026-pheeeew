const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const sharp=require('./.image-work/node_modules/sharp');
const ROOT=path.resolve(__dirname,'..'),OUT=path.join(ROOT,'public/assets/xmas'),WORK=path.join(ROOT,'docs/codex/xmas_work');
const report=JSON.parse(fs.readFileSync(path.join(WORK,'validation.json')));
const sizes={rock:[512,512],fabric:[512,512],trim:[512,128],bg:[1024,1536],arrow_sign:[512,512],nod:[480,200]};
let checks=0;function check(test,msg){assert(test,msg);checks++}
(async()=>{
 check(report.count===30,'exactly 30 deliverables');
 const rockStats=await sharp(path.join(OUT,'rock.png')).stats(),mean=rockStats.channels.slice(0,3).map(c=>Math.round(c.mean));
 for(const a of report.assets){
  const n=a.name,kit=/^(top|hang|side|in|far)_/.test(n),p=path.join(OUT,(kit?'kit/':'')+n+'.png');
  const {data:d,info:{width:w,height:h}}=await sharp(p).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const expected=sizes[n]??(n.startsWith('far_')?[768,768]:[256,256]);check(w===expected[0]&&h===expected[1],n+' dimensions');
  const alpha=(x,y)=>d[(y*w+x)*4+3];
  check(a.alpha[0]===(['rock','fabric','bg'].includes(n)?255:0),n+' alpha');
  if(kit||n==='nod'||n==='arrow_sign'){
   for(let x=0;x<w;x++)check(alpha(x,0)===0&&alpha(x,h-1)===0,n+' top/bottom transparent');
   for(let y=0;y<h;y++)check(alpha(0,y)===0&&alpha(w-1,y)===0,n+' left/right transparent');
  }
  for(const [axis,diff]of Object.entries(a.seamEdgeMeanDifference))check(diff===0,n+' '+axis+' edge continuity');
  if(n.startsWith('top_')){const b=a.bounds;check(200-b.top>=20&&200-b.top<=70,n+' rise');check(b.width>=120&&b.width<=240,n+' footprint width');check(Math.abs(b.left+(b.width-1)/2-128)<=.5,n+' horizontal center');}
  if(n.startsWith('hang_')){const b=a.bounds;check(b.top+b.height-1-56>=40&&b.top+b.height-1-56<=160,n+' length');check(Math.abs(b.left+(b.width-1)/2-128)<=.5,n+' horizontal center');}
  if(n.startsWith('side_')){const b=a.bounds;check(b.left+b.width-1-56>=30&&b.left+b.width-1-56<=110,n+' projection');check(Math.abs(b.top+(b.height-1)/2-128)<=.5,n+' vertical center');}
  if(/^(top|hang|side)_/.test(n)){
   let count=0;for(let q=0;q<256;q++){const x=n.startsWith('side')?56:q,y=n.startsWith('top')?200:n.startsWith('hang')?56:q,i=(y*w+x)*4;if(d[i+3]>32){count++;for(let c=0;c<3;c++)check(Math.abs(d[i+c]-mean[c])<=1,n+' contact color')}}check(count>0,n+' touches anchor');
  }
  if(n==='trim'){check(a.bounds.top>=20&&a.bounds.top<=40,'snow rise <=20px');for(let x=0;x<w;x++)check(alpha(x,40)>220,'continuous snow at y40');for(let x=0;x<w;x++)check(alpha(x,127)===0,'snow lower fade');}
  if(n==='nod'){check(a.bounds.top===20,'nod top y20');check(a.bounds.top+a.bounds.height<=181,'nod bottom <=180');check(a.bounds.width>=470,'nod fills width');}
  if(n==='arrow_sign'){check(a.bounds.top+a.bounds.height-1===450,'sign bottom y450');check(alpha(256,450)>0,'sign touches bottom center');check(a.bounds.width>=210&&a.bounds.width<=230,'sign width about220');}
 }
 check(!report.protectedFilesChanged.some(p=>p.endsWith('.png')),'all preexisting PNGs preserved');
 const bgStats=await sharp(path.join(OUT,'bg.png')).stats();
 const result={status:'passed',assertions:checks,assets:30,rockMean:mean,bgMean:bgStats.channels.slice(0,3).map(c=>+c.mean.toFixed(2)),existingPngsPreserved:true,externallyChangedJs:report.protectedFilesChanged.filter(p=>p.endsWith('.js')),visualReview:['xmas_tile_rock.png 2x2','xmas_tile_fabric.png 2x2','xmas_tile_trim.png 3x1','xmas_tile_bg.png 1x2','check_xmas.png','xmas_contact_sheet.png']};
 fs.writeFileSync(path.join(WORK,'checks.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
})().catch(e=>{console.error(e);process.exitCode=1});
