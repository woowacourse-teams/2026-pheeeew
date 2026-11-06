"""Physical iPhone only. No screenshots or profiler during measured intervals."""
import argparse,json,os,re,subprocess,time
from pathlib import Path
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument('--device',required=True)
parser.add_argument('--bundle-id',default='com.itda.pheeeew')
parser.add_argument('--phase',choices=['isolated','full'],required=True)
args=parser.parse_args()
env=dict(os.environ,DEVELOPER_DIR='/Applications/Xcode.app/Contents/Developer')
results=[]
for index,mode in enumerate(['baseline','candidate','candidate','baseline','baseline','candidate']):
    counter='1' if args.phase=='isolated' else '0'
    variables={'ACTIVITY1052_MODE':mode,'ACTIVITY1052_SCENE':args.phase,'ACTIVITY1052_COUNTER':counter,'OS_ACTIVITY_DT_MODE':'YES'}
    log=root/f'{args.phase}-{index+1}-{mode}.log'
    with log.open('w') as output:
        process=subprocess.Popen(['xcrun','devicectl','device','process','launch','--device',args.device,'--terminate-existing','--environment-variables',json.dumps(variables),'--console',args.bundle_id],env=env,stdout=output,stderr=subprocess.STDOUT)
        deadline=time.monotonic()+70
        while time.monotonic()<deadline:
            text=log.read_text(errors='replace')
            match=re.search(r'ACTIVITY1052_DEVICE result=(\{[^\n]+\})',text)
            if match:
                result=json.loads(match.group(1));result['run']=index+1;result['log']=log.name
                assert result['mode']==mode and result['scene']==args.phase
                assert result['applicationState']==0, 'App not active'
                assert 29<=result['wallSeconds']<=35
                result['peakCpuPercentOneCore']=max(s['cpuPercentOneCore'] for s in result['samples'] if s['intervalSeconds']>=.1)
                results.append(result)
                (root/f'{args.phase}-results.json').write_text(json.dumps(results,indent=2))
                print(json.dumps({k:v for k,v in result.items() if k!='samples'}),flush=True)
                break
            if process.poll() is not None: raise RuntimeError(f'App launch/console exited: {log}')
            time.sleep(.1)
        else: raise RuntimeError(f'Measurement timed out: {log}')
        process.terminate()
        try: process.wait(timeout=5)
        except subprocess.TimeoutExpired: process.kill();process.wait()
    # Terminate by PID from device listing is unnecessary: next launch uses terminate-existing.
    time.sleep(3)
