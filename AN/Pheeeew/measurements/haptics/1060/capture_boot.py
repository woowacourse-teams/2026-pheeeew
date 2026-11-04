import subprocess,time,re
from pathlib import Path
root=Path('/private/tmp/haptic1060-comparison')
device='<DEVICE_IDENTIFIER>'
with (root/'boot-console.log').open('w') as log:
 proc=subprocess.Popen(['xcrun','devicectl','device','process','launch','--device',device,'--terminate-existing','--start-stopped','--environment-variables','{"PRESS_HAPTIC_EXPERIMENT":"1","PRESS_HAPTIC_PREPARE":"1","PRESS_SOUND_DISABLED":"0","OS_ACTIVITY_DT_MODE":"YES"}','--console','com.itda.pheeeew'],stdout=log,stderr=subprocess.STDOUT)
 time.sleep(1)
 listing=subprocess.check_output(['xcrun','devicectl','device','info','processes','--device',device],text=True)
 pid=re.search(r'^(\d+)\s+.*Pheeeew.app/Pheeeew',listing,re.M).group(1)
 commands=Path(root/'capture-live.lldb').read_text()
 commands=commands.replace('device process attach -n Pheeeew','device process attach -p '+pid)
 commands=commands.replace('command script import', 'breakpoint set --func-regex "CHHapticEngine.*init" --one-shot true\ncontinue\ncommand script import',1)
 (root/'boot-capture.lldb').write_text(commands)
 try:
  with (root/'boot-stack.log').open('w') as out:
   p=subprocess.run(['/Applications/Xcode.app/Contents/Developer/usr/bin/lldb','--batch','-s',str(root/'boot-capture.lldb')],stdout=out,stderr=subprocess.STDOUT,timeout=70)
  print('LLDB exit',p.returncode)
 finally:
  proc.terminate()
  try:proc.wait(timeout=5)
  except subprocess.TimeoutExpired:proc.kill()
