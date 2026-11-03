import subprocess,time,json,re,statistics
from pathlib import Path
root=Path('/private/tmp/haptic1060-comparison');results=[]
for prepare,bound in [(0,0),(1,0)]:
 path=root/f'native-slow-prepare{prepare}-bound{bound}.log'
 env=json.dumps({'PRESS_NATIVE_HAPTIC_EXPERIMENT':'1','PRESS_NATIVE_PREPARE':str(prepare),'PRESS_NATIVE_BOUND':str(bound),'PRESS_NATIVE_COUNT':'600','PRESS_NATIVE_INTERVAL_MS':'100','OS_ACTIVITY_DT_MODE':'YES'})
 with path.open('w') as out:
  proc=subprocess.Popen(['xcrun','devicectl','device','process','launch','--device','<DEVICE_IDENTIFIER>','--terminate-existing','--environment-variables',env,'--console','com.itda.pheeeew'],stdout=out,stderr=subprocess.STDOUT)
  started=time.monotonic();status='timeout'
  while time.monotonic()-started<85:
   text=path.read_text(errors='replace')
   if '[NativeHaptic] event=complete' in text:status='complete';break
   if proc.poll() is not None:status='process_exit';break
   time.sleep(.5)
  time.sleep(2);proc.terminate()
  try:proc.wait(timeout=8)
  except subprocess.TimeoutExpired:proc.kill();proc.wait()
 text=path.read_text(errors='replace');hits=list(re.finditer(r'event=impact index=(\d+).*?elapsedMs=(\d+) intervalMs=(\d+)',text))
 first=text.find('Unable to add an additional player channel');prior=[m for m in hits if m.start()<first] if first>=0 else []
 result={'prepare':bool(prepare),'boundToView':bool(bound),'status':status,'impacts':len(hits),'channelErrorLogCount':text.count('Unable to add an additional player channel'),'firstErrorAfterLoggedIndex':int(prior[-1].group(1)) if prior else None,'medianIntervalMs':statistics.median(int(m.group(3)) for m in hits[1:]) if len(hits)>1 else None,'log':str(path)}
 results.append(result);(root/'native-slow-results.json').write_text(json.dumps(results,indent=2));print(json.dumps(result),flush=True)
 if status!='complete':break
 time.sleep(2)
