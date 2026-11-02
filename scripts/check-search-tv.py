#!/usr/bin/env python3
"""Explicit, bounded search/playback walkthrough on the already paired Chromecast."""
import importlib.util,json,pathlib,subprocess,time
spec=importlib.util.spec_from_file_location('player',pathlib.Path(__file__).with_name('check-player-tv.py'))
player=importlib.util.module_from_spec(spec);spec.loader.exec_module(player)
tv=player.tv
folder=tv.ROOT/'artifacts/screenshots/0.8-optimized';folder.mkdir(parents=True,exist_ok=True)
def shot(name):
    p=subprocess.run([tv.ADB,'exec-out','screencap','-p'],capture_output=True,check=True,timeout=20)
    (folder/name).write_bytes(p.stdout)
root=tv.home();shot('home-search-pill.png')
field=player.button(root,'Search');assert field is not None
screen=tv.bounds(next(root.iter('node')));pill=tv.bounds(field)
assert pill[2]-pill[0] < (screen[2]-screen[0])*.45
tv.key('KEYCODE_DPAD_RIGHT');tv.key('KEYCODE_DPAD_UP')
for _ in range(3):
    root=tv.ui()
    candidates=[n for n in tv.buttons(root) if 'Search' in tv.labels(n)]
    field=max(candidates,key=lambda n:tv.bounds(n)[2]-tv.bounds(n)[0]) if candidates else None
    if field is not None and field.get('focused')=='true':break
    tv.key('KEYCODE_DPAD_RIGHT')
else:raise AssertionError('Remote focus did not reach the Home header search pill')
tv.key(23)
root=tv.wait(lambda r:player.button(r,'SPACE') is not None)
assert player.button(root,'Home') is None
assert player.button(root,'Back to browsing') is not None
shot('search-history.png')
tv.adb('shell','input','text','https://www.youtube.com/watch?v=jNQXAC9IVRw')
root=tv.ui();assert any('jNQXAC9IVRw' in s for s in tv.labels(root))
tv.key('KEYCODE_SEARCH')
def result(r):return next((n for n in tv.buttons(r) if any('Me at the zoo' in s for s in tv.labels(n))),None)
root=tv.wait(lambda r:result(r) is not None and 'Loading search results' not in tv.labels(r))
assert player.button(root,'SPACE') is not None
shot('search-direct-video.png')
assert result(root).get('focused')=='true','The first result should receive remote focus'
tv.key(23);assert player.player_active(),'Select must play directly'
tv.wait(lambda r:'Loading video' not in tv.labels(r))
end=time.monotonic()+20
while time.monotonic()<end:
    if 'state=PLAYING(3)' in player.own_session():break
    time.sleep(.5)
else:raise AssertionError('The public video never entered PLAYING')
shot('direct-playback.png')
tv.key('KEYCODE_BACK')
root=tv.wait(lambda r:player.main_active() and player.button(r,'SPACE') is not None)
assert result(root) is not None
# Search remains the previous route after playback; a second Back restores Home.
tv.key('KEYCODE_BACK')
root=tv.wait(lambda r:player.button(r,'Home') is not None)
shot('home-return.png')
checks={'compact_home_pill':True,'sidebar_hidden_in_search':True,'embedded_keyboard':True,'back_arrow':True,
        'hardware_typing':True,'direct_url_result':True,'result_focus':True,'direct_playback':True,
        'media_session_playing':True,'return_to_search':True,'back_to_home':True}
(tv.ROOT/'artifacts/search-0.8-optimized-checks.json').write_text(json.dumps(checks,indent=2)+'\n')
print(json.dumps(checks),flush=True)
