#!/usr/bin/env python3
"""Opt-in Home metadata/subscription walkthrough on the paired Chromecast."""
import importlib.util,json,pathlib,re,subprocess,time
spec=importlib.util.spec_from_file_location('tv',pathlib.Path(__file__).with_name('verify-tv.py'))
tv=importlib.util.module_from_spec(spec);spec.loader.exec_module(tv)
folder=tv.ROOT/'artifacts/screenshots/0.8-optimized';folder.mkdir(parents=True,exist_ok=True)
def shot(name):
    p=subprocess.run([tv.ADB,'exec-out','screencap','-p'],capture_output=True,check=True,timeout=20)
    (folder/name).write_bytes(p.stdout)
def photos(root):return [n for n in root.iter('node') if (n.get('content-desc') or '').endswith(' channel photo')]
def focused(root):return next((n for n in tv.buttons(root) if n.get('focused')=='true'),None)
root=tv.home();shot('home-relative-dates.png')
relative=re.compile(r'^(Today|Upcoming|Date unavailable|\d+ (day|week|month|year)s? ago)$')
assert any(relative.fullmatch(label) for label in tv.labels(root)),'Relative publication metadata is missing'
# Menu focus activates Subscriptions immediately after Home.
tv.key(20)
root=tv.wait(lambda r:'Select a channel to see its videos' in tv.labels(r) and len(photos(r))>=2)
tv.key(22)
root=tv.ui();selected=focused(root)
assert selected is not None and any(s.endswith(' channel photo') for s in tv.labels(selected))
name=next(s for s in tv.labels(selected) if s.endswith(' channel photo')).removesuffix(' channel photo')
for photo in photos(root):
    a,b,c,d=tv.bounds(photo);assert abs((c-a)-(d-b))<=2,'Channel image should have square bounds before circular clipping'
tv.key(20);tv.key(19)
root=tv.ui();assert name+' channel photo' in tv.labels(focused(root))
shot('subscription-channel-grid.png')
tv.key(23)
root=tv.wait(lambda r:'Select a channel to see its videos' not in tv.labels(r) and name in tv.labels(r) and tv.cards(r))
shot('selected-channel-feed.png')
tv.key('KEYCODE_BACK');tv.wait(lambda r:'Select a channel to see its videos' in tv.labels(r))
tv.key('KEYCODE_BACK');tv.wait(lambda r:tv.cards(r) and 'Home' in tv.labels(r) and not photos(r))
checks={'relative_publication_metadata':True,'subscription_photo_grid':True,'square_photo_bounds':True,
    'remote_grid_navigation':True,'channel_feed_selection':True,'back_to_home':True}
(tv.ROOT/'artifacts/home-0.8-optimized-checks.json').write_text(json.dumps(checks,indent=2)+'\n')
print(json.dumps(checks),flush=True)
