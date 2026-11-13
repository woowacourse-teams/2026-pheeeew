// Usage: node scripts/extract-press-face-artwork.cjs source.svg output-directory [contact-sheet.png]
// Requires sharp and Python 3. The source hash locks the coordinates to the reviewed original.
const sharp = require('sharp');
const { createHash } = require('node:crypto');
const { execFileSync } = require('node:child_process');
const path = require('node:path');
const [source, output, contactSheet] = process.argv.slice(2);
if (!source || !output) throw new Error('Provide the original SVG and an output directory.');
const fs=require('fs');
const input = fs.readFileSync(source);
if (createHash('sha256').update(input).digest('hex') !== '37240ecc1f2dccecf9c752106604c868bc256985c5752353f291306a73897c07') {
 throw new Error('SVG changed; review extraction bounds before regenerating artwork.');
}
const out=path.resolve(output);fs.mkdirSync(out,{recursive:true});
const artwork=execFileSync('python3',['-c', `
import sys, xml.etree.ElementTree as E
r=E.parse(sys.argv[1]).getroot()
# Outlined column headings, not character paths; source hash guards these indices.
for e in list(r)[452:457]: r.remove(e)
r.set('width','900'); r.set('viewBox','0 0 900 876')
E.register_namespace('','http://www.w3.org/2000/svg')
sys.stdout.buffer.write(E.tostring(r))
`,source]);
const rows=[['angry',135,[60,205,350,495,640,800]],['blocked',310,[54,198,342,490,640,800]],['tired',485,[54,198,342,490,640,800]],['annoyed',640,[50,197,335,480,635,795]],['defeated',795,[50,195,330,478,635,790]]];
(async()=>{
 const full=await sharp(artwork,{density:216}).png().toBuffer();
 const sheet=[];const metadata=[];
 const rowBounds=[[55,210],[235,390],[400,575],[580,705],[705,876]];
 const xBounds=[0,130,275,420,565,715,895];
 for(let row=0;row<rows.length;row++){
 const [kind,y,xs]=rows[row];
 for(let stage=0;stage<6;stage++){
 const x=xs[stage];const left=Math.max(0,x-90,xBounds[stage]),right=Math.min(x+90,xBounds[stage+1]);
 const top=Math.max(y-90,rowBounds[row][0]),bottom=Math.min(y+90,rowBounds[row][1]);
 const fragment=await sharp(full).extract({left:left*3,top:top*3,width:(right-left)*3,height:(bottom-top)*3}).png().toBuffer();
 const asset=await sharp({create:{width:540,height:540,channels:4,background:'#00000000'}}).composite([{input:fragment,left:(left-(x-90))*3,top:(top-(y-90))*3}]).png().toBuffer();
 const name=`press_${kind}_stage_${stage}.png`;fs.writeFileSync(out+'/'+name,asset);
 const thumb=await sharp(asset).resize(180,180).toBuffer();sheet.push({input:thumb,left:stage*180,top:row*180});
 metadata.push({file:name,sourceCenter:[x,y],canvas:180,density:3});
 }
 }
 if(contactSheet) await sharp({create:{width:1080,height:900,channels:4,background:'#ffffff'}}).composite(sheet).png().toFile(contactSheet);
 // 180 logical pixels at 3x; centers and row bounds above are intentionally retained as provenance.
 console.log('30 transparent 540x540 PNGs; original paths/masks rendered at 3x.');
})();
