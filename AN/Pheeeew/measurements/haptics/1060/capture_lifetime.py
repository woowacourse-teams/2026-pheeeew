import subprocess,time,re
from pathlib import Path
root=Path('/private/tmp/haptic1060-comparison')
device='<DEVICE_IDENTIFIER>'
with (root/'lifetime-console.log').open('w') as log:
 proc=subprocess.Popen(['xcrun','devicectl','device','process','launch','--device',device,'--terminate-existing','--start-stopped','--environment-variables','{"PRESS_NATIVE_HAPTIC_EXPERIMENT":"1","PRESS_NATIVE_PREPARE":"1","PRESS_NATIVE_PAUSE":"1","PRESS_NATIVE_COUNT":"600","PRESS_NATIVE_INTERVAL_MS":"20","OS_ACTIVITY_DT_MODE":"YES"}','--console','com.itda.pheeeew'],stdout=log,stderr=subprocess.STDOUT)

 for _ in range(40):
  if 'Launched application' in (root/'lifetime-console.log').read_text(errors='replace'): break
  time.sleep(.25)
 for _ in range(12):
  listing=subprocess.check_output(['xcrun','devicectl','device','info','processes','--device',device],text=True)
  match=re.search(r'^(\d+)\s+.*Pheeeew.app/Pheeeew',listing,re.M)
  if match: break
  time.sleep(.5)
 if not match: raise RuntimeError('No app process found')
 pid=match.group(1)
 commands=Path(root/'capture-live.lldb').read_text()
 commands=commands.replace('device process attach -n Pheeeew','device process attach -p '+pid)
 commands=commands.replace('command script import', 'breakpoint set --func-regex "CHHapticEngine.*init" --one-shot true\ncontinue\ncommand script import',1)
 commands=commands.replace('thread backtrace all', 'thread backtrace\nbreakpoint set --name "-[CHHapticEngine(CHHapticEngineInternal) releaseChannel:]" --one-shot true\ncontinue\nregister read x2\nthread backtrace all')
 (root/'boot-capture.lldb').write_text(commands)
 try:
  with (root/'lifetime-stack.log').open('w') as out:
   p=subprocess.run(['/Applications/Xcode.app/Contents/Developer/usr/bin/lldb','--batch','-s',str(root/'boot-capture.lldb')],stdout=out,stderr=subprocess.STDOUT,timeout=70)
  print('LLDB exit',p.returncode)
 finally:
  proc.terminate()
  try:proc.wait(timeout=5)
  except subprocess.TimeoutExpired:proc.kill()
