"""Run after installing experiment app on simulator. CPU = process user+system / wall / one core."""
import argparse, ctypes, json, os, re, subprocess, time
from pathlib import Path
root=Path(__file__).resolve().parent
env=dict(os.environ,DEVELOPER_DIR='/Applications/Xcode.app/Contents/Developer')
lib=ctypes.CDLL('/usr/lib/libproc.dylib')
def cpu(pid):
    data=ctypes.create_string_buffer(4096)
    n=lib.proc_pidinfo(pid,4,0,data,len(data))
    if n<48: raise RuntimeError(f'proc_pidinfo failed: {n}')
    values=ctypes.cast(data,ctypes.POINTER(ctypes.c_uint64))
    return (values[2]+values[3])/1e9
parser=argparse.ArgumentParser()
parser.add_argument('--device', required=True, help='Simulator UDID')
parser.add_argument('--bundle-id', default='com.itda.pheeeew')
parser.add_argument('--prefix',default='')
parser.add_argument('--modes',nargs='+',default=['baseline','candidate','candidate','baseline','baseline','candidate'])
args=parser.parse_args()
udid=args.device
results=[]
for index,mode in enumerate(args.modes):
    log=root/f'{args.prefix}{index+1}-{mode}.log'
    log.write_text('')
    launch=subprocess.run(['xcrun','simctl','launch','--terminate-running-process','--stdout='+str(log),'--stderr='+str(log),udid,args.bundle_id],env=dict(env,SIMCTL_CHILD_ACTIVITY1052_MODE=mode),capture_output=True,text=True,check=True)
    pid=int(launch.stdout.strip().split(':')[-1]); start=None; deadline=time.monotonic()+55
    while time.monotonic()<deadline:
        text=log.read_text(errors='replace')
        if start is None and 'ACTIVITY1052 start' in text: start=(time.monotonic(),cpu(pid))
        if start and 'ACTIVITY1052 end' in text:
            elapsed=time.monotonic()-start[0]; consumed=cpu(pid)-start[1]
            count=int(re.search(r'compositions=(\d+)',text).group(1))
            result=dict(run=index+1,mode=mode,pid=pid,wallSeconds=elapsed,cpuSeconds=consumed,cpuPercentOneCore=100*consumed/elapsed,compositions=count)
            results.append(result); print(json.dumps(result),flush=True)
            (root/f'{args.prefix}results.json').write_text(json.dumps(results,indent=2));break
        time.sleep(.05)
    else: raise RuntimeError(f'timeout: {log}')
    subprocess.run(['xcrun','simctl','terminate',udid,args.bundle_id],env=env,check=True)
    time.sleep(2)
