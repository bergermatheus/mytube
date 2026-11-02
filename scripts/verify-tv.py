#!/usr/bin/env python3
"""Opt-in checks for an authorized TV. Uses ADB, never the debug test runner."""
import hashlib,json,pathlib,re,subprocess,sys,tempfile,time,sqlite3
ROOT=pathlib.Path(__file__).resolve().parent.parent
ADB=str(pathlib.Path.home()/'Library/Android/sdk/platform-tools/adb')
REPORTS=ROOT/'artifacts/performance'
REPORTS.mkdir(parents=True,exist_ok=True)
def adb(*args):
 p=subprocess.run([ADB,*map(str,args)],capture_output=True,timeout=180 if tuple(args[:3]) == ("shell","am","instrument") else 55)
 if p.returncode: raise RuntimeError(p.stderr.decode(errors='replace'))
 return p.stdout.decode(errors='replace')
def key(code): adb('shell','input','keyevent',code);time.sleep(.22)
def ui():
 import xml.etree.ElementTree as ET
 adb('shell','uiautomator','dump','/sdcard/mytube-ui.xml')
 return ET.fromstring(adb('exec-out','cat','/sdcard/mytube-ui.xml'))
def labels(node): return [x.get('text') or x.get('content-desc') for x in node.iter('node') if x.get('text') or x.get('content-desc')]
def bounds(node): return list(map(int,re.findall(r'\d+',node.get('bounds',''))))
def buttons(root): return [n for n in root.iter('node') if n.get('clickable')=='true']
def cards(root):
 screen=bounds(next(root.iter('node')))
 width=screen[2]-screen[0];height=screen[3]-screen[1]
 return [n for n in buttons(root) if len(bounds(n))==4 and bounds(n)[2]-bounds(n)[0]>width*.15 and bounds(n)[3]-bounds(n)[1]>height*.2]
def wait(predicate,seconds=85):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  root=ui()
  result=predicate(root)
  if result:return root
  time.sleep(.3)
 raise AssertionError('Timed out waiting for the expected TV UI')
def home():
 key('KEYCODE_WAKEUP');adb('shell','am','force-stop','app.mytube.tv');adb('shell','am','start','-n','app.mytube.tv/.MainActivity')
 return wait(lambda r:cards(r))
def install(apk,downgrade=False):
 args=['install','-r']+(['-d'] if downgrade else [])+[ROOT/apk]
 print(adb(*args).strip(),flush=True)
def prefs(label):
 # Freeze writes, inspect copies, and leave the installed database untouched.
 adb('shell','am','force-stop','app.mytube.tv')
 with tempfile.TemporaryDirectory(prefix='mytube-prefs-') as directory:
  for filename in ['mytube.db','mytube.db-wal']:
   p=subprocess.run([ADB,'exec-out','run-as','app.mytube.tv','cat','databases/'+filename],capture_output=True,timeout=15)
   if p.returncode==0:pathlib.Path(directory,filename).write_bytes(p.stdout)
  connection=sqlite3.connect('file:'+directory+'/mytube.db?mode=ro',uri=True)
  facts={}
  for table in ['subscriptions','interests']:
   rows=connection.execute('SELECT * FROM '+table+' ORDER BY 1').fetchall()
   facts[table]={'count':len(rows),'sha256':hashlib.sha256(json.dumps(rows,ensure_ascii=False).encode()).hexdigest()}
  connection.close()
 (REPORTS/(label+'-preferences.json')).write_text(json.dumps(facts,indent=2)+'\n')
 return facts
def profile(label,direct=True):
 root=home()
 # One complete trip starts/ends on Home in the rail; it traverses two grid rows.
 sequence=([22,20,19,21,19,19,19,19,19] if direct else [22,20,21,21,20,19,21,19,19,19,19,19])
 for _ in range(2):
  for code in sequence:key(code)
 root=ui();assert cards(root),'Warm navigation must leave visible Home cards'
 pid=adb('shell','pidof','app.mytube.tv').strip();assert pid.isdigit()
 hz=int(adb('shell','getconf','CLK_TCK').strip())
 def ticks():
  fields=adb('shell','cat','/proc/'+pid+'/stat').rsplit(')',1)[1].split()
  return int(fields[11])+int(fields[12]),fields[19]
 adb('shell','dumpsys','gfxinfo','app.mytube.tv','reset')
 cpu,identity=ticks();start=time.monotonic()
 for _ in range(6):
  for code in sequence:key(code)
 elapsed=(time.monotonic()-start)*1000;after,end_identity=ticks();assert identity==end_identity
 report=adb('shell','dumpsys','gfxinfo','app.mytube.tv','framestats')
 memory=adb('shell','cat','/proc/'+pid+'/status')
 data={'label':label,'script_sha256':hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest(),'warmup_cycles':2,'measured_cycles':6,'keycodes_per_cycle':sequence,'pacing_ms_per_key':220,
       'elapsed_ms':round(elapsed),'app_cpu_ms':round((after-cpu)*1000/hz),'clock_ticks_per_second':hz,
       'memory':{m.group(1):int(m.group(2)) for m in re.finditer(r'^(VmHWM|VmRSS):\s+(\d+) kB',memory,re.M)},
       'cold_start':'not measured','agent_tokens':'not measured','raw_report':label+'-gfxinfo.txt'}
 (REPORTS/(label+'-gfxinfo.txt')).write_text(report)
 (REPORTS/(label+'.json')).write_text(json.dumps(data,indent=2)+'\n')
 print(json.dumps(data),flush=True)
 return data
if __name__=='__main__':
 if len(sys.argv)!=2:raise SystemExit('Usage: python3 scripts/verify-tv.py PROFILE_LABEL')
 profile(sys.argv[1])
