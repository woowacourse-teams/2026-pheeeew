// Pixel-level delivery checks, independent of the export's requested dimensions.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const sharp=require('./.image-work/node_modules/sharp');
const root=path.resolve(__dirname,'..'),out=path.join(root,'public/assets/nyc'),work=path.join(root,'docs/codex/nyc_work');
const errors=[],assets={},check=(ok,label)=>{if(!ok)errors.push(label);};
function bb(data,w,h,condition=()=>true){let l=w,t=h,r=-1,b=-1;for(let y=0;y<h;y++)for(let x=0;x<w;x++)if(condition(x,y)&&data[(y*w+x)*4+3]>16){l=Math.min(l,x);t=Math.min(t,y);r=Math.max(r,x);b=Math.max(b,y);}return {left:l,top:t,right:r,bottom:b,width:r-l+1,height:b-t+1};}
async function main(){
const names=['rock','fabric','trim','bg','arrow_sign'];for(const [g,n] of [['top',6],['hang',5],['side',4],['in',6],['far',3]])for(let i=1;i<=n;i++)names.push('kit/'+g+'_'+i);
for(const name of names){
 const {data,info}=await sharp(path.join(out,name+'.png')).ensureAlpha().raw().toBuffer({resolveWithObject:true});const w=info.width,h=info.height;
 const dims=name==='bg'?[1024,1536]:name==='trim'?[512,128]:name.startsWith('kit/far')?[768,768]:name.startsWith('kit/')?[256,256]:[512,512];
 check(w===dims[0]&&h===dims[1],name+' dimensions');
 let min=255,max=0,zero=0,partial=0,edge=0,mean=[0,0,0];for(let y=0;y<h;y++)for(let x=0;x<w;x++){const i=(y*w+x)*4,a=data[i+3];min=Math.min(min,a);max=Math.max(max,a);zero+=a===0;partial+=a>0&&a<255;if(x===0||y===0||x===w-1||y===h-1)edge=Math.max(edge,a);for(let c=0;c<3;c++)mean[c]+=data[i+c]/(w*h);}
 const opaque=['rock','fabric','bg'].includes(name);check(opaque?min===255:zero>0&&partial>0,name+' alpha');if(!opaque&&name!=='trim')check(edge===0,name+' transparent outer edges');
 const result={size:[w,h],alpha:{min,max,zero,partial,edgeMax:edge},bounds:bb(data,w,h)};
 if(opaque)result.meanRGB=mean;
 if(['rock','fabric','trim','bg'].includes(name)){
  const axes=name==='trim'?['x']:name==='bg'?['y']:['x','y'];result.seams={};
  for(const axis of axes){let maxDiff=0,total=0,n=0;for(let k=0;k<(axis==='x'?h:w);k++)for(let c=0;c<4;c++){const a=axis==='x'?(k*w)*4+c:k*4+c,b=axis==='x'?(k*w+w-1)*4+c:((h-1)*w+k)*4+c;const d=Math.abs(data[a]-data[b]);maxDiff=Math.max(maxDiff,d);total+=d;n++;}result.seams[axis]={maxDifference:maxDiff,meanDifference:total/n};check(maxDiff===0,name+' '+axis+' seam');}
 }
 if(name.startsWith('kit/top')){const b=bb(data,w,h,(x,y)=>y<=200);result.exposed=b;result.rise=200-b.top;check(result.rise>=20&&result.rise<=70,name+' rise');check(b.width>=120&&b.width<=240,name+' width');check(Math.abs((b.left+b.right)/2-128)<=2,name+' center');}
 if(name.startsWith('kit/hang')){const b=bb(data,w,h,(x,y)=>y>=56);result.exposed=b;result.drop=b.bottom-56;check(result.drop>=40&&result.drop<=160,name+' drop');check(Math.abs((b.left+b.right)/2-128)<=2,name+' center');}
 if(name.startsWith('kit/side')){const b=bb(data,w,h,(x,y)=>x>=56);result.exposed=b;result.projection=b.right-56;check(result.projection>=30&&result.projection<=110,name+' projection');check(Math.abs((b.top+b.bottom)/2-128)<=2,name+' center');}
 if(/^kit\/(top|hang|side)/.test(name)){
  let monotonic=true,anchorCount=0;const group=name.split('/')[1].split('_')[0];
  for(let k=0;k<256;k++){let previous=255;for(let dist=0;dist<56;dist++){
   const x=group==='side'?56-dist:k,y=group==='top'?200+dist:group==='hang'?56-dist:k,a=data[(y*256+x)*4+3];if(dist===0&&a>16)anchorCount++;if(a>previous)monotonic=false;previous=a;
  }}check(monotonic,name+' alpha fade');check(anchorCount>0,name+' connected anchor');result.anchorConnectedPixels=anchorCount;result.monotonicAttachmentAlpha=monotonic;
 }
 if(name==='trim'){check(result.bounds.top>=20&&result.bounds.top<=40,'trim top plane');for(let x=0;x<512;x++)check(data[(40*512+x)*4+3]>200,'trim standing line '+x);}
 if(name.startsWith('kit/far'))check(max<=100,name+' distant opacity');
 if(name==='arrow_sign'){check(result.bounds.width>=216&&result.bounds.width<=222,'arrow width');check(result.bounds.bottom===450,'arrow bottom');let sx=0,n=0;for(let x=0;x<w;x++)if(data[(450*w+x)*4+3]>16){sx+=x;n++;}result.bottomCenterX=sx/n;check(Math.abs(sx/n-256)<=2,'arrow centered foot');}
 assets[name]=result;
}
// Master frame equality checks ignore the four glass interiors only.
const master=await sharp(path.join(out,'kit/in_1.png')).raw().toBuffer();let frameMaxDifference=0;
for(let n=2;n<=4;n++){const d=await sharp(path.join(out,'kit/in_'+n+'.png')).raw().toBuffer();for(let y=0;y<256;y++)for(let x=0;x<256;x++){const rx=x-53,ry=y-33,glass=((rx>=27&&rx<=72)||(rx>=77&&rx<=122))&&((ry>=24&&ry<=89)||(ry>=96&&ry<=165));if(!glass)for(let c=0;c<4;c++)frameMaxDifference=Math.max(frameMaxDifference,Math.abs(master[(y*256+x)*4+c]-d[(y*256+x)*4+c]));}const b=assets['kit/in_'+n].bounds;check(b.width>=148&&b.width<=150&&b.height>=188&&b.height<=190,'window '+n+' bounding size');}
check(frameMaxDifference===0,'identical window frame pixels');
const before=JSON.parse(fs.readFileSync(path.join(work,'original_hashes.json'))),changed=Object.entries(before).filter(([p,h])=>crypto.createHash('sha256').update(fs.readFileSync(path.join(root,p))).digest('hex')!==h).map(([p])=>p);check(changed.filter(p=>p.endsWith('.png')).length===0,'existing PNGs unchanged');
let pngCount=0;function count(p){for(const e of fs.readdirSync(p,{withFileTypes:true})){if(e.isDirectory())count(path.join(p,e.name));else if(e.name.endsWith('.png'))pngCount++;}}count(out);check(pngCount===29,'exactly 29 assets');
const report={passed:errors.length===0,errors,pngCount,protectedFilesChecked:Object.keys(before).length,protectedFilesChanged:changed,windowFrameMaxDifference:frameMaxDifference,assets};fs.writeFileSync(path.join(work,'validation.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({passed:report.passed,errors,pngCount,protectedFilesChecked:report.protectedFilesChecked,protectedFilesChanged:changed,windowFrameMaxDifference:frameMaxDifference,geometry:Object.fromEntries(Object.entries(assets).filter(([k])=>/^kit\/(top|hang|side)/.test(k)).map(([k,v])=>[k,{rise:v.rise,drop:v.drop,projection:v.projection}]))},null,2));if(errors.length)process.exitCode=1;
}
main().catch(e=>{console.error(e);process.exitCode=1});
