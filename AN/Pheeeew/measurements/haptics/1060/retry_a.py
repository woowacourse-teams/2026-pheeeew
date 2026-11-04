import subprocess,time,json,re
from pathlib import Path
root=Path('/private/tmp/haptic1060-comparison')
results=json.loads((root/'results.json').read_text())
for number,mode in enumerate([0],5):
 path=root/f'{number}-{"B-prepare" if mode else "A-impact-only"}.log'
 env=json.dumps({'PRESS_HAPTIC_EXPERIMENT':'1','PRESS_HAPTIC_PREPARE':str(mode),'PRESS_SOUND_DISABLED':'0','OS_ACTIVITY_DT_MODE':'YES'})
 with path.open('w') as out:
  proc=subprocess.Popen(['xcrun','devicectl','device','process','launch','--device','<DEVICE_IDENTIFIER>','--terminate-existing','--environment-variables',env,'--console','com.itda.pheeeew'],stdout=out,stderr=subprocess.STDOUT)
  started=time.monotonic(); status='timeout'
  while time.monotonic()-started<70:
   text=path.read_text(errors='replace')
   if 'event=complete' in text:status='complete';break
   if 'event=invalid' in text:status='invalid';break
   if proc.poll() is not None:status='process_exit';break
   time.sleep(1)
  time.sleep(3)
  proc.terminate()
  try:proc.wait(timeout=10)
  except subprocess.TimeoutExpired:proc.kill();proc.wait()
 text=path.read_text(errors='replace')
 hits=list(re.finditer(r'event=impact index=(\d+).*?elapsedMs=(\d+) latenessMs=(\d+)',text))
 first=text.find('Unable to add an additional player channel')
 before=[m for m in hits if m.start()<first] if first>=0 else []
 result={'trial':number,'prepare':bool(mode),'status':status,'impactsLogged':len(hits),'lastIndex':int(hits[-1].group(1)) if hits else None,'channelErrors':text.count('Unable to add an additional player channel'),'coreHaptics10851':text.count('Code=-10851'),'rateLimitWarnings':text.count('rate-limit threshold'),'firstErrorAfterLoggedIndex':int(before[-1].group(1)) if before else None,'maxLatenessMs':max([int(m.group(3)) for m in hits],default=None),'log':str(path)}
 results.append(result);(root/'results.json').write_text(json.dumps(results,indent=2))
 print(json.dumps(result),flush=True)
 time.sleep(2)
