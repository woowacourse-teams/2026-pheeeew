import subprocess,time,json,re,statistics
from pathlib import Path
root=Path('/private/tmp/haptic1060-comparison');results=[]
for name,core,pause,count in [('core-reuse',1,0,2000),('uikit-idle-recovery',0,1,600)]:
 path=root/(name+'.log')
 env=json.dumps({'PRESS_NATIVE_HAPTIC_EXPERIMENT':'1','PRESS_NATIVE_CORE_HAPTICS':str(core),'PRESS_NATIVE_PREPARE':str(1-core),'PRESS_NATIVE_PAUSE':str(pause),'PRESS_NATIVE_COUNT':str(count),'PRESS_NATIVE_INTERVAL_MS':'20','OS_ACTIVITY_DT_MODE':'YES'})
 with path.open('w') as out:
  proc=subprocess.Popen(['xcrun','devicectl','device','process','launch','--device','<DEVICE_IDENTIFIER>','--terminate-existing','--environment-variables',env,'--console','com.itda.pheeeew'],stdout=out,stderr=subprocess.STDOUT)
  status='timeout';started=time.monotonic()
  while time.monotonic()-started<70:
   s=path.read_text(errors='replace')
   if '[NativeHaptic] event=complete' in s:status='complete';break
   if '[NativeHaptic] event=setupError' in s:status='setupError';break
   if proc.poll() is not None:status='process_exit';break
   time.sleep(.5)
  time.sleep(3);proc.terminate()
  try:proc.wait(timeout=8)
  except subprocess.TimeoutExpired:proc.kill();proc.wait()
 s=path.read_text(errors='replace');idx=0;errors=[]
 for line in s.splitlines():
  m=re.search(r'event=impact index=(\d+)',line)
  if m:idx=int(m.group(1))
  if 'Unable to add an additional player channel' in line:errors.append(idx)
 r={'name':name,'status':status,'lastIndex':idx,'channelErrorLogCount':len(errors),'publicPlayerErrors':s.count('event=playerError'),'engineResets':s.count('event=engineReset'),'firstErrorAfterLoggedIndex':errors[0] if errors else None,'errorsBeforePause':[i for i in errors if i<=300] if pause else None,'errorsAfterPause':[i for i in errors if i>300] if pause else None,'log':str(path)}
 results.append(r);(root/'candidate-results.json').write_text(json.dumps(results,indent=2));print(json.dumps(r),flush=True)
 if status!='complete':break
 time.sleep(2)
