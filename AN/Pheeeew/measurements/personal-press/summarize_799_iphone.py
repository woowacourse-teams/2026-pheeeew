"""Analyze the paired iPhone benchmark's allowlisted measurement logs."""
import csv
from collections import defaultdict, deque
from decimal import Decimal
import io
from pathlib import Path
import re
import subprocess
import sys
import tempfile

source=Path(sys.argv[1])
parser=Path(sys.argv[2])
out=Path(sys.argv[3]);out.mkdir(parents=True,exist_ok=True)
rows=[]
for variant in ('before','after'):
    merged = source / f'799-iphone-{variant}.log'
    if merged.exists():
        all_lines = merged.read_text().splitlines()
    else:
        all_lines = []
        for repetition in (1, 2, 3):
            all_lines.extend((source / f'{variant}-{repetition}.log').read_text().splitlines())
    for repetition in (1, 2, 3):
        marker = f'probe_done variant={variant} repetition={repetition}'
        assert sum(marker in line for line in all_lines) == 1, marker
    assert not any('probe_error' in line for line in all_lines)
    # Preserve source numeric notation; normalize only a temporary parser input.
    (out/f'799-iphone-{variant}.log').write_text('\n'.join(all_lines)+'\n')
    normalized=[]
    for line in all_lines:
        stamp,rest=line.split(' ',1)
        normalized.append(f'{Decimal(stamp):.3f} {rest}')
    with tempfile.NamedTemporaryFile('w',suffix='.log') as f:
        f.write('\n'.join(normalized)+'\n');f.flush()
        result=subprocess.run(['python3',str(parser),f.name],capture_output=True,text=True,check=True)
    singleton_waits = {}
    queues = defaultdict(deque)
    active = None
    for line in all_lines:
        timestamp = Decimal(line.split(' ', 1)[0]) * 1000
        message = line.split('PressMeasurement: ', 1)[1]
        fields = dict(re.findall(r'(\w+)=([^ ]+)', message))
        if message.startswith('run_start '):
            active = (fields['pattern'], fields['repetition'])
            singleton_waits[active] = []
        elif message.startswith('run_end '):
            active = None
        elif message.startswith('input '):
            assert active is not None, 'Unexpected input outside a measured run'
            queues[fields['emotion']].append(timestamp)
        elif message.startswith('send '):
            inputs = []
            for item in fields['counts'].split(';'):
                emotion, count = item.split(':')
                inputs.extend(queues[emotion].popleft() for _ in range(int(count)))
            if len(inputs) == 1:
                singleton_waits[active].append(round(timestamp - inputs[0]))
    assert not any(queues.values())
    values=list(csv.DictReader(io.StringIO(result.stdout)))
    assert len(values)==9
    for row in values:
        sizes=list(map(int,row['batch_sizes'].split('/')))
        rows.append({'variant':variant,**row,'logical_batches':len(sizes),'single_input_batches':sizes.count(1),'mean_batch_size':round(sum(sizes)/len(sizes),3),'singleton_wait_ms':'/'.join(map(str,singleton_waits[(row['pattern'], row['repetition'])]))})
    (out/f'799-iphone-{variant}.csv').write_text(result.stdout)
with (out/'799-iphone-comparison.csv').open('w') as f:
    w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
for pattern in ('single','same','mixed'):
    for variant in ('before','after'):
        group=[r for r in rows if r['pattern']==pattern and r['variant']==variant]
        print(pattern,variant,'posts',[r['http_post_attempts'] for r in group], 'p95',[r['confirmation_p95_ms'] for r in group], 'singletons',[r['single_input_batches'] for r in group])
