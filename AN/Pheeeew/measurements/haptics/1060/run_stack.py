import subprocess,time
from pathlib import Path
root=Path('/private/tmp/haptic1060-comparison')
with (root/'stack-sync-console.log').open('w') as log:
 p=subprocess.Popen(['xcrun','devicectl','device','process','launch','--device','<DEVICE_IDENTIFIER>','--terminate-existing','--environment-variables','{"PRESS_HAPTIC_EXPERIMENT":"1","PRESS_HAPTIC_PREPARE":"1","PRESS_SOUND_DISABLED":"0","OS_ACTIVITY_DT_MODE":"YES"}','--console','com.itda.pheeeew'],stdout=log,stderr=subprocess.STDOUT)
 for i in range(12):
  if 'Launched application' in (root/'stack-sync-console.log').read_text(errors='replace'):break
  time.sleep(.5)
 try:
  with (root/'stack-sync-lldb.log').open('w') as out:
   result=subprocess.run(['/Applications/Xcode.app/Contents/Developer/usr/bin/lldb','--batch','-s',str(root/'capture-live.lldb')],stdout=out,stderr=subprocess.STDOUT,timeout=85)
  print('lldb exit:',result.returncode)
 finally:
  p.terminate()
  try:p.wait(timeout=10)
  except subprocess.TimeoutExpired:p.kill()
