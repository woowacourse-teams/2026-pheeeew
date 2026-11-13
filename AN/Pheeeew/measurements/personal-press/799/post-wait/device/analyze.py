from pathlib import Path
from collections import defaultdict,deque
from decimal import Decimal
import re,csv,math,json,hashlib
root=Path(__file__).parent
# README and analysis code can evolve; comparison.csv is regenerated.
recorded=json.loads((root/'SHA256.json').read_text())
for name,digest in recorded.items():
 if name in ('comparison.csv','README.md','analyze.py'):continue
 path=root/name
 if not path.is_file():raise SystemExit(f'Missing original evidence: {name}')
 actual=hashlib.sha256(path.read_bytes()).hexdigest()
 if actual!=digest:raise SystemExit(f'Original evidence checksum mismatch: {name}: {actual} != {digest}')
rows=[]
for variant in ['before','after']:
 for rep in [1,2,3]:
  path=root/'logs'/f'postwait-{variant}_continuous_continuous_r{rep}.log'
  lines=path.read_text().splitlines()
  assert sum(f'probe_done variant=postwait-{variant} repetition={rep}' in l for l in lines)==1
  assert not any('probe_error ' in l for l in lines)
  active=None
  for line in lines:
   stamp=Decimal(line.split(' ',1)[0])*1000
   msg=line.split('PressMeasurement: ',1)[1]
   fields=dict(re.findall(r'(\w+)=([^ ]+)',msg))
   if msg.startswith('run_start '):
    assert active is None
    active=dict(variant=variant,repetition=rep,pattern=fields['pattern'],requested=int(fields['requested']))
    inputs=defaultdict(deque);pending={};confirm=[];sizes=[];http=defaultdict(int);peak=0;local=[];accepted=0;delta=None
   elif active:
    if msg.startswith('input '):
     inputs[fields['emotion']].append(stamp);accepted+=1
    elif msg.startswith('send '):
     sample=[]
     for item in fields['counts'].split(';'):
      emotion,count=item.split(':')
      sample.extend(inputs[emotion].popleft() for _ in range(int(count)))
     pending[fields['sequence']]=sample;sizes.append(len(sample))
    elif msg.startswith('result '):
     assert fields['outcome']=='Accepted',msg
     confirm.extend(stamp-t for t in pending.pop(fields['sequence']))
    elif msg.startswith('http_start '):http[fields['endpoint']]+=1
    elif msg.startswith('state '):peak=max(peak,int(fields['pending']))
    elif msg.startswith('local_state '):local.append(int(fields['elapsed_us']))
    elif msg.startswith('verification '):delta=int(fields['delta'])
    elif msg.startswith('run_end '):
     assert accepted==delta==active['requested']==len(confirm)==len(local)
     assert not pending and not any(inputs.values())
     confirm.sort();local.sort()
     def pct(xs,p):return float(xs[math.ceil(len(xs)*p)-1])
     rows.append(dict(**active,accepted=accepted,server_delta=delta,posts=http['emotion_press_write'],all_gets=http['emotion_press_all_daily'],personal_gets=http['emotion_press_me_daily'],http_total=sum(http.values()),http_endpoints=json.dumps(dict(http),sort_keys=True),batches=len(sizes),batch_sizes='/'.join(map(str,sizes)),confirmation_p50_ms=pct(confirm,.5),confirmation_p95_ms=pct(confirm,.95),peak_pending=peak,local_state_p95_us=pct(local,.95)))
     active=None
  assert active is None
with (root/'comparison.csv').open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
for r in rows:print(r)
