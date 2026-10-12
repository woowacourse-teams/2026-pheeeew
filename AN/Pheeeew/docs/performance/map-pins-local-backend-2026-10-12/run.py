import json,subprocess,time
from pathlib import Path
P=Path(__file__).resolve().parent
ADB=['adb','-s','R5CX11AKCYJ']
REMOTE='/sdcard/Android/data/com.pheeeew.mappinprofile/files/'
def adb(*args):
    return subprocess.check_output(ADB+list(args),stderr=subprocess.STDOUT).decode()
def seed(count):
    subprocess.run(['docker','exec','-i','pheeeew-pin-perf-606-db-1','psql','-v','ON_ERROR_STOP=1','-v',f'pin_count={count}','-U','pinperf','-d','pinperf'],input=(P/'seed.sql').read_bytes(),check=True,stdout=subprocess.DEVNULL)
last=None
last_progress=time.monotonic()
started=time.monotonic()
while time.monotonic()-started<1800:
    try: data=adb('shell','cat',REMOTE+'map-pin-profile.jsonl')
    except subprocess.CalledProcessError: time.sleep(1); continue
    rows=[]
    for line in data.splitlines():
        try: rows.append(json.loads(line))
        except json.JSONDecodeError: pass
    if not rows or rows[0].get('target_count') != -1:
        time.sleep(.4); continue
    prepares=[r for r in rows if r['kind']=='prepare']
    if prepares and prepares[-1]['ticket']!=last:
        r=prepares[-1]
        seed(r['target_count'])
        (P/'ready.txt').write_text(r['ticket'])
        adb('push',str(P/'ready.txt'),REMOTE+'backend-ready.next')
        adb('shell','mv',REMOTE+'backend-ready.next',REMOTE+'backend-ready.txt')
        last=r['ticket']
        last_progress=time.monotonic()
    runs=[r for r in rows if r['kind']=='run' and not r['warmup']]
    if any(r['outcome']!='success' for r in rows if r['kind']=='run'):
        (P/'failed.jsonl').write_text(data)
        if globals().get('failure_reported') is not True:
            print('A failed API trial was recorded; continuing the planned cases without retrying it',flush=True)
            failure_reported=True
    if runs and len(runs)%30==0 and len(runs)!=globals().get('printed'):
        printed=len(runs); print(f'{printed}/270 completed trials',flush=True)
    if any(r['kind']=='complete' for r in rows):
        (P/'measurements.jsonl').write_text(data)
        assert len(runs)==270
        print(f'Complete: {len(runs)} trials; {sum(r["outcome"]=="success" for r in runs)} successful',flush=True)
        break
    if time.monotonic()-last_progress>300:
        (P/'stalled.jsonl').write_text(data)
        raise TimeoutError('No new trial preparation for 300 seconds')
    time.sleep(.4)
else: raise TimeoutError('Measurement did not finish')
