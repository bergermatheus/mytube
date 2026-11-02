#!/usr/bin/env python3
"""Explicit ADB smoke: searches/plays a public video on the authorized TV."""
import importlib.util,pathlib,re,json,time,subprocess
spec=importlib.util.spec_from_file_location('tv',pathlib.Path(__file__).with_name('verify-tv.py'));tv=importlib.util.module_from_spec(spec);spec.loader.exec_module(tv)
SCREENSHOTS=tv.ROOT/'artifacts/screenshots/0.8-optimized';SCREENSHOTS.mkdir(parents=True,exist_ok=True)
def screenshot(name):
 p=subprocess.run([tv.ADB,'exec-out','screencap','-p'],capture_output=True,check=True,timeout=20)
 (SCREENSHOTS/name).write_bytes(p.stdout)
def button(root,label): return next((n for n in tv.buttons(root) if label in tv.labels(n)),None)
def text(root,label): return next((n for n in root.iter('node') if n.get('text')==label),None)
def tap(node):
 a,b,c,d=tv.bounds(node);tv.adb('shell','input','tap',(a+c)//2,(b+d)//2);time.sleep(.4)
def activate(label,condition,direction):
 for _ in range(10):
  root=tv.ui();node=button(root,label)
  if node is not None and node.get('focused')=='true':
   tv.key(23);return tv.wait(condition)
  tv.key(direction)
 raise AssertionError('Remote focus did not reach '+label)
def player_active():
 return re.search(r'^\s*topResumedActivity=.*app\.mytube\.tv/\.PlayerActivity\b',tv.adb('shell','dumpsys','activity','activities'),re.M) is not None
def main_active():
 return re.search(r'^\s*topResumedActivity=.*app\.mytube\.tv/\.MainActivity\b',tv.adb('shell','dumpsys','activity','activities'),re.M) is not None
def own_session():
 data=tv.adb('shell','dumpsys','media_session')
 # Retain only this app's session block, not other installed apps' state.
 parts=data.split('package=app.mytube.tv')
 return re.split(r'\n    \S',parts[1],maxsplit=1)[0] if len(parts)>1 else ''
if __name__=='__main__':
 root=tv.home()
 tv.key('KEYCODE_DPAD_RIGHT');tv.key('KEYCODE_DPAD_UP')
 for _ in range(3):
  root=tv.ui();candidates=[n for n in tv.buttons(root) if 'Search' in tv.labels(n)]
  field=max(candidates,key=lambda n:tv.bounds(n)[2]-tv.bounds(n)[0]) if candidates else None
  if field is not None and field.get('focused')=='true':break
  tv.key('KEYCODE_DPAD_RIGHT')
 else:raise AssertionError('Could not focus the Home header search pill')
 tv.key(23)
 root=tv.wait(lambda r:button(r,'SPACE') is not None)
 assert button(root,'Home') is None,'Sidebar should be hidden on the search page'
 tv.adb('shell','input','text','https://www.youtube.com/watch?v=jNQXAC9IVRw')
 tv.key('KEYCODE_SEARCH')
 def video(r):return next((n for n in tv.buttons(r) if any('zoo' in s.lower() for s in tv.labels(n))),None)
 root=tv.wait(lambda r:video(r));card=video(root)
 channel=[s for s in tv.labels(card) if not s.startswith('Watched')][-1]
 for _ in range(16):
  root=tv.ui();candidate=video(root)
  if candidate is not None and candidate.get('focused')=='true':break
  tv.key('KEYCODE_TAB')
 else:raise AssertionError('Could not focus the search result')
 tv.key(23)
 assert player_active(),'Selecting the result must open playback directly'
 root=tv.wait(lambda r:'Loading video' not in tv.labels(r))
 time.sleep(2)
 state=own_session()
 assert 'state=PLAYING(3)' in state,'Real MediaSession did not enter PLAYING'
 tv.key('KEYCODE_MEDIA_PAUSE');tv.key(20)
 root=tv.wait(lambda r:button(r,'Settings') is not None)
 screenshot('player-controls.png')
 root=activate('Settings',lambda r:text(r,'Settings') is not None,22)
 assert tv.bounds(text(root,'Settings'))[0]>1000,'Settings must open on the right'
 assert text(root,'Close') is None,'Settings must not have a Close button'
 screenshot('settings-drawer.png');tv.key('KEYCODE_BACK')
 root=tv.wait(lambda r:button(r,'Comments') is not None)
 root=activate('Comments',lambda r:any((n.get('text') or '').startswith('Comments') for n in r.iter('node')),22)
 root=tv.wait(lambda r:'Loading comments' not in tv.labels(r))
 assert len(tv.labels(root))>6,'Expected actual comment content'
 assert text(root,'Close') is None,'Comments must not have a Close button'
 screenshot('comments-drawer.png');tv.key('KEYCODE_BACK')
 root=tv.wait(lambda r:button(r,'Open channel') is not None)
 root=activate('Open channel',lambda r:main_active(),21)
 root=tv.wait(lambda r:"Loading videos" not in tv.labels(r))
 tv.key(22) # reveal the channel heading by collapsing the rail
 root=tv.wait(lambda r:any(n.get('text')==channel and tv.bounds(n)[1]<300 for n in r.iter('node') if n.get('text')))
 screenshot('channel-feed.png')
 result={'search_public_url':True,'direct_playback':True,'media_session_playing':True,'right_settings_drawer':True,'physical_back':True,'actual_comments':True,'channel_avatar_navigation':True}
 (tv.ROOT/'artifacts/optimized-playback-results.json').write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps(result),flush=True)
