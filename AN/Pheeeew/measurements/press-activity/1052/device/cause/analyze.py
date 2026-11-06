"""Summarize xctrace time-profile XML; inclusive categories overlap, never sum them."""
import collections,json,sys,xml.etree.ElementTree as E
from pathlib import Path
source=Path(sys.argv[1]);output=Path(sys.argv[2]);tree=E.parse(source)
refs={e.get('id'):e for e in tree.iter() if e.get('id')}
def resolve(e): return refs.get(e.get('ref'),e) if e is not None else None
categories={
 'checkerBacktrace':lambda n,b:any('generateCulledBacktrace' in s for s in n),
 'runtimeCheckerBinary':lambda n,b:'libRPAC.dylib' in b,
 'metalValidation':lambda n,b:any('MTLDebug' in s for s in n),
 'gpuCapture':lambda n,b:'GPUToolsCapture' in b,
 'composeRender':lambda n,b:any('SurfaceMetalRedrawer.draw#' in s for s in n),
 'mapLibre':lambda n,b:'MapLibre' in b,
 'indicator':lambda n,b:any('PressActivityLiveIndicator' in s for s in n),
 'footer':lambda n,b:any('PressActivityFooter' in s for s in n),
 'emotionPadButton':lambda n,b:any('EmotionPadButton' in s for s in n),
 'hapticBackend':lambda n,b:any('IosCoreHaptic' in s or 'CHHaptic' in s for s in n),
 'gc':lambda n,b:any('gc::' in s for s in n),
}
inc=collections.Counter();leaf=collections.Counter();threads=collections.Counter();cat=collections.Counter();buckets={};skips=0;total=0;count=0;stacks={k:collections.Counter() for k in categories}
for row in tree.findall('.//row'):
 cs=[resolve(e) for e in row];w=int(cs[5].text)/1e6;stack=resolve(cs[6].find('backtrace'))
 if stack is None:skips+=1;continue
 fs=[resolve(f) for f in stack.findall('frame')];names=[f.get('name') for f in fs];bins=[resolve(f.find('binary')).get('name') if resolve(f.find('binary')) is not None else '?' for f in fs]
 t=int(cs[0].text)/1e9;bucket=buckets.setdefault(str(int(t//5)*5),collections.Counter());bucket['all']+=w;total+=w;count+=1;threads[cs[1].get('fmt')]+=w
 for name in set(names):inc[name]+=w
 if names:leaf[names[0]]+=w
 for k,fn in categories.items():
  if fn(names,bins):cat[k]+=w;bucket[k]+=w;stacks[k][tuple(zip(names,bins))]+=w
summary={'source':str(source),'resolvedSamples':count,'skippedStackSamples':skips,'totalSampleWeightMs':total,'categories':{k:{'weightMs':cat[k],'percentOfResolvedWeight':100*cat[k]/total} for k in categories},'inclusiveTop':inc.most_common(60),'selfTop':leaf.most_common(30),'threads':threads.most_common(),'fiveSecondBuckets':buckets,'representativeStacks':{k:[{'weightMs':weight,'frames':[{'name':n,'binary':b} for n,b in frames]} for frames,weight in c.most_common(2)] for k,c in stacks.items()},'warning':'Inclusive categories overlap. Sample weights are statistical CPU estimates, not wall duration or recomposition counts.'}
output.write_text(json.dumps(summary,indent=2,ensure_ascii=False));print(json.dumps({k:v for k,v in summary.items() if k in ['resolvedSamples','skippedStackSamples','totalSampleWeightMs','categories']},indent=2))
