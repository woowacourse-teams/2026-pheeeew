// Pixel-level acceptance checks and transparent contact sheet, no game-code writes.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const sharp=require('./.image-work/node_modules/sharp');
const ROOT=path.resolve(__dirname,'..'),DIR=path.join(ROOT,'assets_src/zone1/kit'),WORK=path.join(ROOT,'docs/codex/zone1_kit_work');
const specs=JSON.parse(fs.readFileSync(path.join(WORK,'specs.json')));
async function main(){
 let records=[];
 for(const[name]of specs){
  const group=name.split('_')[0],size=group==='far'?768:256;
  const {data,info}=await sharp(path.join(DIR,name+'.png')).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  assert.equal(info.width,size,name+' width');assert.equal(info.height,size,name+' height');
  let transparent=0,partial=0,opaque=0,edgeMax=0,airMinX=size,airMaxX=-1,airMinY=size,airMaxY=-1;
  for(let y=0;y<size;y++)for(let x=0;x<size;x++){
   const a=data[(y*size+x)*4+3];if(a===0)transparent++;else if(a===255)opaque++;else partial++;
   if(x===0||y===0||x===size-1||y===size-1)edgeMax=Math.max(edgeMax,a);
   const air=group==='top'?y<200:group==='hang'?y>56:group==='side'?x>56:true;
   if(air&&a>16){airMinX=Math.min(airMinX,x);airMaxX=Math.max(airMaxX,x);airMinY=Math.min(airMinY,y);airMaxY=Math.max(airMaxY,y);}
  }
  assert(transparent>size*size*.15,name+' transparent area');assert(partial>0,name+' soft alpha');assert.equal(edgeMax,0,name+' fully transparent outer border');
  const extension=group==='top'?200-airMinY:group==='hang'?airMaxY-56:group==='side'?airMaxX-56:null;
  if(group==='top'){assert(extension>=20&&extension<=70,name+' height');assert(airMaxX-airMinX+1>=120&&airMaxX-airMinX+1<=240,name+' width');}
  if(group==='hang')assert(extension>=40&&extension<=160,name+' hanging length');
  if(group==='side')assert(extension>=30&&extension<=110,name+' wall projection');
  let fadeViolations=0,attachmentSamples=0,colorError=0;
  if(group!=='far')for(let t=0;t<256;t++){
   const ax=group==='side'?56:t,ay=group==='top'?200:group==='hang'?56:t;
   const anchor=(ay*256+ax)*4;
   if(data[anchor+3]>180){attachmentSamples++;for(let c=0;c<3;c++)colorError+=Math.abs(data[anchor+c]-[118.8375,141.09,165.3184][c]);}
   let previous=data[anchor+3];
   for(let d=1;d<=55;d++){
    const x=group==='side'?56-d:t,y=group==='top'?200+d:group==='hang'?56-d:t;
    const alpha=data[(y*256+x)*4+3];if(alpha>previous+1)fadeViolations++;previous=alpha;
   }
  }
  assert.equal(fadeViolations,0,name+' monotonic buried fade');
  if(group!=='far'){assert(attachmentSamples>0,name+' attached at anchor');assert(colorError/(attachmentSamples*3)<5,name+' rock-colored attachment');}
  records.push({name,size:[size,size],transparentPixels:transparent,softAlphaPixels:partial,opaquePixels:opaque,outerBorderAlphaMax:edgeMax,airBounds:[airMinX,airMinY,airMaxX,airMaxY],extension,fadeViolations,attachmentMeanChannelDifference:attachmentSamples?colorError/(attachmentSamples*3):null});
 }
 assert.equal(fs.readdirSync(DIR).filter(f=>f.endsWith('.png')).length,18);
 const W=1500,H=1300,cell=300,buf=Buffer.alloc(W*H*4),layers=[];
 for(let y=0;y<H;y++)for(let x=0;x<W;x++){const i=(y*W+x)*4,v=(Math.floor(x/12)+Math.floor(y/12))%2?225:240;buf[i]=v;buf[i+1]=v+1;buf[i+2]=Math.min(255,v+3);buf[i+3]=255;}
 for(let k=0;k<15;k++){
  const name=specs[k][0],left=(k%5)*cell+22,top=Math.floor(k/5)*cell+25;
  layers.push({input:path.join(DIR,name+'.png'),left,top});
  layers.push({input:Buffer.from(`<svg width="280" height="24"><text x="12" y="18" font-family="sans-serif" font-size="15" fill="#2c3850">${name}.png · 256 × 256</text></svg>`),left:(k%5)*cell,top:Math.floor(k/5)*cell});
 }
 for(let k=0;k<3;k++){
  const name='far_'+(k+1);layers.push({input:await sharp(path.join(DIR,name+'.png')).resize(380,380).toBuffer(),left:k*500+60,top:920});
  layers.push({input:Buffer.from(`<svg width="400" height="24"><text x="12" y="18" font-family="sans-serif" font-size="15" fill="#2c3850">${name}.png · 768 × 768</text></svg>`),left:k*500+50,top:900});
 }
 await sharp(buf,{raw:{width:W,height:H,channels:4}}).composite(layers).png().toFile(path.join(ROOT,'docs/codex/zone1_kit_contact_sheet.png'));
 fs.writeFileSync(path.join(WORK,'acceptance.json'),JSON.stringify({status:'passed',assets:records},null,2));
 console.log(JSON.stringify({status:'passed',assets:records.length,dimensions:'15 × 256×256; 3 × 768×768',transparentBorders:true,allFadesMonotonic:true,extensions:records.filter(r=>r.extension!==null).map(r=>[r.name,r.extension])},null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
