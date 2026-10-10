"""Prove a responsive age gate in the isolated package; reject dialogs, never erase data."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import threading
import time
import uuid
import xml.etree.ElementTree as ET
from emulator_food_tests import adb, LOCK, PACKAGE, COMPONENT

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--out', required=True)
args = parser.parse_args()
out = ROOT / 'artifacts/stability-verification' / args.out
assert not out.exists()
out.mkdir(parents=True)
ticket = str(uuid.uuid4())
LOCK.mkdir()
owner = LOCK / 'owner.txt'
owner.write_text(f'ticket={ticket} worktree={ROOT} purpose=interactive-launch\n')
done = threading.Event()
def heartbeat():
    while not done.wait(15): os.utime(LOCK, None)
beat = threading.Thread(target=heartbeat, daemon=True)
beat.start()
report = dict(state='RUNNING', package=PACKAGE, actions=[], limits='Synthetic debug age-gate interaction on API36; not full onboarding or device coverage.')
def snapshot(name):
    response = adb('exec-out', 'uiautomator', 'dump', '/dev/tty')
    assert response.returncode == 0
    xml = response.stdout
    (out / (name + '.xml')).write_bytes(xml)
    start, end = xml.find(b'<hierarchy'), xml.find(b'</hierarchy>')
    assert start >= 0 and end >= 0
    tree = ET.fromstring(xml[start:end + len(b'</hierarchy>')])
    shot = adb('exec-out', 'screencap', '-p')
    assert shot.returncode == 0 and shot.stdout.startswith(b'\x89PNG')
    (out / (name + '.png')).write_bytes(shot.stdout)
    state = adb('shell', 'dumpsys', 'activity', 'activities').stdout
    resumed = b'\n'.join(line for line in state.splitlines() if b'topResumedActivity' in line or b'mResumedActivity' in line)
    (out / (name + '-activity.txt')).write_bytes(resumed)
    assert COMPONENT.encode() in resumed
    assert not any(n.get('resource-id') in ('android:id/aerr_close','android:id/aerr_wait') or "isn't responding" in n.get('text','') for n in tree.iter('node')), 'Obstructing error/ANR dialog; evidence preserved'
    assert any(n.get('package') == PACKAGE for n in tree.iter('node'))
    return tree
def checkbox(tree):
    nodes = [n for n in tree.iter('node') if n.get('package') == PACKAGE and n.get('checkable') == 'true' and n.get('enabled') == 'true']
    assert len(nodes) == 1, 'Expected the single adult-confirmation checkbox on the age gate'
    assert any('18' in n.get('text','') for n in tree.iter('node'))
    return nodes[0]
def tap(node):
    bound = node.get('bounds')
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bound)
    assert match
    x1,y1,x2,y2 = map(int, match.groups())
    x,y = (x1+x2)//2, (y1+y2)//2
    assert adb('shell','input','tap',str(x),str(y)).returncode == 0
    report['actions'].append(dict(source='UI_TREE_BOUNDS', bounds=bound, x=x, y=y))
try:
    assert PACKAGE == 'com.example.kpkn.stability' and adb('get-state').stdout.strip() == b'device'
    report['api'] = adb('shell','getprop','ro.build.version.sdk').stdout.decode().strip()
    assert adb('shell','am','force-stop',PACKAGE).returncode == 0
    start = adb('shell','date','+%m-%d %H:%M:%S.000').stdout.decode().strip()
    launch = adb('shell','am','start','-W','-n',COMPONENT)
    (out / 'launch.txt').write_bytes(launch.stdout + launch.stderr)
    assert launch.returncode == 0
    time.sleep(12)
    before = snapshot('01-before')
    first = checkbox(before)
    assert first.get('checked') == 'false'
    tap(first)
    time.sleep(2)
    checked = snapshot('02-checked')
    second = checkbox(checked)
    assert second.get('checked') == 'true', 'The app did not respond to the first input'
    tap(second)
    time.sleep(2)
    unchecked = snapshot('03-unchecked')
    assert checkbox(unchecked).get('checked') == 'false', 'The app did not respond to the second input'
    pid = adb('shell','pidof','-s',PACKAGE).stdout.decode().strip()
    assert pid.isdecimal()
    logResponse = adb('logcat','-d','-v','threadtime','--pid',pid)
    assert logResponse.returncode == 0
    log = logResponse.stdout
    eventsResponse = adb('logcat','-d','-b','events','-s','am_anr','am_crash')
    assert eventsResponse.returncode == 0
    events = eventsResponse.stdout
    (out / 'logcat-app.txt').write_bytes(log)
    (out / 'system-events.txt').write_bytes(events)
    (out / 'gfxinfo.txt').write_bytes(adb('shell','dumpsys','gfxinfo',PACKAGE,'framestats').stdout)
    assert b'FATAL EXCEPTION' not in log and not any(PACKAGE.encode() in l for l in events.splitlines()), 'App crash/ANR in the bounded capture'
    report['logScope'] = 'Entire available log buffer filtered to the freshly launched PID; events cover current emulator boot.'
    report.update(state='PASS', mainVisible=True, interactionVerified=True, errorDialogPresent=False, appPid=int(pid))
except BaseException as error:
    report.update(state='FAIL_OR_INCOMPLETE', error=str(error))
    raise
finally:
    report['evidenceHashes'] = {p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.iterdir() if p.is_file() and p.name != 'result.json'}
    (out / 'result.json').write_text(json.dumps(report,indent=2)+'\n')
    done.set(); beat.join(timeout=2)
    if owner.exists() and f'ticket={ticket}' in owner.read_text(): owner.unlink(); LOCK.rmdir()
    print(json.dumps(dict(state=report['state'], actions=len(report['actions']))),flush=True)
