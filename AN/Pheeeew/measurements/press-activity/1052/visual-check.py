"""Optional, outside CPU runs: verify candidate halo changes while center dot remains."""
import argparse,json,os,subprocess,time
from pathlib import Path
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument('--device', required=True, help='Simulator UDID')
parser.add_argument('--bundle-id', default='com.itda.pheeeew')
args=parser.parse_args()
udid=args.device
env=dict(os.environ,DEVELOPER_DIR='/Applications/Xcode.app/Contents/Developer',SIMCTL_CHILD_ACTIVITY1052_MODE='candidate')
subprocess.run(['xcrun','simctl','launch','--terminate-running-process',udid,args.bundle_id],env=env,check=True)
time.sleep(5)
rows=[]
for i in range(4):
    path=root/f'candidate-visual-{i}.png'
    subprocess.run(['xcrun','simctl','io',udid,'screenshot',str(path)],env=env,check=True,capture_output=True)
    time.sleep(.23)
rows=json.loads(subprocess.check_output(['swift',str(root/'visual-check.swift'),str(root)],env=env,text=True))
(root/'visual-check.json').write_text(json.dumps(rows,indent=2))
subprocess.run(['xcrun','simctl','terminate',udid,args.bundle_id],env=env,check=True)
assert len({r['centralRegionRGBSum'] for r in rows})>1, 'No halo change observed'
print(json.dumps(rows))
