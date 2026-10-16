"""Summarize one complete failure/UI device experiment, with draw sample coverage."""
import collections
import csv
import math
import re
import sys
from pathlib import Path

source = Path(sys.argv[1])
events = []
for line in source.read_text().splitlines():
    m = re.match(r"\s*(\d+\.\d+).*PressMeasurement: (.*)", line)
    if m:
        events.append((float(m[1]) * 1000, m[2]))
start = max(i for i, (_, m) in enumerate(events) if m.startswith('failure_start mode=400 '))
events = events[start:]

def fields(message):
    return dict(re.findall(r'(\w+)=([^ ]+)', message))

def segments(prefix):
    for i, (timestamp, message) in enumerate(events):
        if message.startswith(prefix + '_start '):
            end = next(j for j in range(i+1,len(events)) if events[j][1].startswith(prefix + '_end '))
            yield timestamp, fields(message), events[i+1:end+1]

def write(name, header, rows):
    path = source.with_name(name)
    with path.open('w') as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(rows)
    print(path.name)

assert len(list(segments("failure"))) == 6, "Expected six complete fault scenarios"
assert len(list(segments("animation"))) == 3, "Expected three complete animation scenarios"
failures=[]
for timestamp, info, segment in segments('failure'):
    posts=[]; results=[]; pending=[]; unknown=False; last=timestamp; delta=None; offline_end=None; reconnect=None
    for t,m in segment:
        d=fields(m)
        if m.startswith('http_end ') and d['endpoint']=='emotion_press_write':
            posts.append(d['status'] if d['status']!='null' else d['outcome'])
        if m.startswith('result '): results.append(d['outcome'])
        if m.startswith('state '):
            pending.append(int(d['pending']))
            unknown |= d['unknown']=='true'
            if d['pending']=='0': last=t
        if m.startswith('offline_inputs_end '): offline_end=t
        if m.startswith('connectivity_restored '): reconnect=t
        if m.startswith('failure_end '): delta=int(d['delta'])
    assert pending and pending[-1]==0
    # Last POST success is the confirmation point; final GET may follow.
    successes=[t for t,m in segment if m.startswith('result ') and fields(m)['outcome']=='Accepted']
    settled=successes[-1] if successes else last
    failures.append([info['mode'],int(info['baseline']),delta,len(posts),'/'.join(posts),'/'.join(results),max(pending),unknown,pending[-1],round(settled-timestamp),round(settled-reconnect) if reconnect is not None else ''])
write('2026-10-14-failure.csv',['mode','baseline','personal_delta','post_attempts','http_outcomes','repository_outcomes','max_pending','observed_unknown','remaining_pending','settled_from_start_ms','recovery_after_restore_ms'],failures)

mapping={'FRUSTRATED':'Blocked','IRRITATED':'Annoyed','EXHAUSTED':'Tired','DISCOURAGED':'Defeated','ANGRY':'Angry'}
ui=[]
for _,info,segment in segments('animation'):
    accepted=collections.defaultdict(collections.deque); last_input={}; face=[]; count=[]; reaction=[]; n=0; downs=0
    for t,m in segment:
        d=fields(m)
        if m.startswith('ui_down '): downs+=1
        if m.startswith('input '):
            kind=mapping[d['emotion']];accepted[kind].append(t);last_input[kind]=t;n+=1
        if m.startswith('ui_face_draw '): face.append(float(d['delay_ms']))
        if m.startswith('ui_reaction_draw '):
            kind=d['kind']
            if accepted[kind]: reaction.append(t-accepted[kind].popleft())
        if m.startswith('ui_count_draw ') and d['kind'] in last_input:
            count.append(t-last_input[d['kind']])
    assert n==int(info['requested'])
    for metric,samples in [('down_to_face_draw',face),('accept_to_count_draw',count),('accept_to_reaction_draw',reaction)]:
        assert samples, metric+' has no samples'
        values=sorted(samples)
        pct=lambda p: round(values[math.ceil(len(values)*p)-1],2)
        ui.append([info['pattern'],n,downs,metric,len(values),pct(.5),pct(.95),round(max(values),2)])
write('2026-10-14-ui.csv',['pattern','accepted','touch_downs','metric','draw_samples','p50_ms','p95_ms','max_ms'],ui)
