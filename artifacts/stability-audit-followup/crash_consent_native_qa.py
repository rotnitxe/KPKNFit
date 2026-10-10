"""Public consent UI and process restart on the owned stability emulator only."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import sys
import threading
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path('C:/kw/msi')
sys.path.insert(0, str(ROOT / 'scripts/stability'))
from emulator_food_tests import adb, LOCK, PACKAGE, COMPONENT, SERIAL

parser = argparse.ArgumentParser()
parser.add_argument('--out', required=True)
args = parser.parse_args()
assert re.fullmatch(r'[a-z0-9-]{1,90}', args.out)
assert PACKAGE == 'com.example.kpkn.stability' and SERIAL == 'emulator-5554'
out = ROOT / 'artifacts/stability-audit-followup' / args.out
assert not out.exists()
out.mkdir()
ticket = uuid.uuid4().hex
LOCK.mkdir()
owner = LOCK / 'owner.txt'
owner.write_text(ticket, encoding='utf-8')
done = threading.Event()
def heartbeat():
    while not done.wait(15): os.utime(LOCK, None)
beat = threading.Thread(target=heartbeat, daemon=True)
beat.start()
report = dict(state='RUNNING', package=PACKAGE, serial=SERIAL, actions=[], coldPids=[],
    scope='PUBLIC_ACCOUNT_CONSENT_UI_AND_FRESH_PROCESS_READBACK',
    limits='One API36 emulator, isolated Debug package and synthetic guest data. Debug never uploads Crashlytics; no live SDK upload or minified Release runtime.')
start_time = adb('shell', 'date', '+%m-%d %H:%M:%S.000').stdout.decode().strip()

def capture(name):
    activity = adb('shell', 'dumpsys', 'activity', 'activities').stdout
    resumed = b'\n'.join(line for line in activity.splitlines() if b'topResumedActivity' in line or b'mResumedActivity' in line)
    (out / (name + '-activity.txt')).write_bytes(resumed)
    assert COMPONENT.encode() in resumed, 'The isolated MainActivity must be top-resumed'
    raw = adb('exec-out', 'uiautomator', 'dump', '/dev/tty').stdout
    (out / (name + '.xml')).write_bytes(raw)
    begin, end = raw.find(b'<hierarchy'), raw.find(b'</hierarchy>')
    assert begin >= 0 and end >= 0, 'No UI hierarchy'
    tree = ET.fromstring(raw[begin:end + len(b'</hierarchy>')])
    shot = adb('exec-out', 'screencap', '-p').stdout
    assert shot.startswith(b'\x89PNG')
    (out / (name + '.png')).write_bytes(shot)
    assert not any(node.get('resource-id') in ('android:id/aerr_close', 'android:id/aerr_wait') or
                   "isn't responding" in node.get('text', '') for node in tree.iter('node')), 'Obstructing error dialog'
    return tree

def consent_row(tree):
    labels = [n for n in tree.iter('node') if 'Share technical crash reports' in n.get('text', '')]
    if not labels: return None
    assert len(labels) == 1
    parents = {child: node for node in tree.iter() for child in node}
    row = labels[0]
    while row is not None and not (row.get('checkable') == 'true' and row.get('clickable') == 'true'):
        row = parents.get(row)
    assert row is not None and row.get('enabled') == 'true', 'Consent row unavailable'
    return row

def bounds(node):
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    assert match
    return tuple(map(int, match.groups()))

def find_row(name):
    for attempt in range(4):
        tree = capture(name + '-' + str(attempt))
        row = consent_row(tree)
        if row is not None: return row
        scrolling = [n for n in tree.iter('node') if n.get('scrollable') == 'true' and n.get('package') == PACKAGE]
        assert scrolling, 'Consent row is missing without a scrollable surface'
        x1, y1, x2, y2 = bounds(scrolling[0])
        assert adb('shell', 'input', 'swipe', str((x1 + x2) // 2), str(y1 + (y2-y1)*3//4),
                   str((x1 + x2) // 2), str(y1 + (y2-y1)//4), '400').returncode == 0
    raise AssertionError('Consent row unavailable after bounded scrolling')

def open_account(name):
    response = adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', 'kpkn://account', '-n', COMPONENT)
    (out / (name + '-launch.txt')).write_bytes(response.stdout + response.stderr)
    assert response.returncode == 0
    time.sleep(7)
    return find_row(name)

def desired(value, name):
    row = find_row(name + '-before')
    current = row.get('checked') == 'true'
    if current != value:
        x1, y1, x2, y2 = bounds(row)
        assert adb('shell', 'input', 'tap', str((x1 + x2)//2), str((y1 + y2)//2)).returncode == 0
        time.sleep(1)
    after = find_row(name + '-after')
    assert (after.get('checked') == 'true') == value
    report['actions'].append(dict(step=name, checked=value, changed=current != value))

def cold(value, name):
    prior = adb('shell', 'pidof', '-s', PACKAGE).stdout.decode().strip()
    assert prior.isdecimal()
    assert adb('shell', 'am', 'force-stop', PACKAGE).returncode == 0
    assert not adb('shell', 'pidof', '-s', PACKAGE).stdout.strip()
    row = open_account(name)
    pid = adb('shell', 'pidof', '-s', PACKAGE).stdout.decode().strip()
    assert pid.isdecimal() and pid != prior
    assert (row.get('checked') == 'true') == value
    report['coldPids'].append(int(pid))
    report['actions'].append(dict(step=name, checked=value, priorPid=int(prior), coldPid=int(pid)))

try:
    assert adb('get-state').stdout.strip() == b'device'
    apk = ROOT / 'android-native/app/build/outputs/apk/debug/app-debug.apk'
    report['appApkSha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
    paths = adb('shell', 'pm', 'path', PACKAGE).stdout.decode().splitlines()
    assert len(paths) == 1 and paths[0].startswith('package:')
    installed = paths[0].removeprefix('package:').strip()
    assert re.fullmatch(r'/data/app/[A-Za-z0-9_~/=+.-]+/base\.apk', installed)
    installed_hash = adb('shell', 'sha256sum', installed).stdout.decode().split()[0]
    assert installed_hash == report['appApkSha256']
    report['installedHashMatches'] = True
    open_account('01-account')
    desired(True, '02-enable')
    cold(True, '03-cold-enabled')
    desired(False, '04-disable')
    cold(False, '05-cold-disabled')
    assert len(set(report['coldPids'])) == 2
    pid = str(report['coldPids'][-1])
    log = adb('logcat', '-d', '-v', 'threadtime', '--pid', pid, '-T', start_time).stdout
    (out / 'logcat-final-pid.txt').write_bytes(log)
    assert b'FATAL EXCEPTION' not in log
    report.update(state='PASS', finalConsent=False, mainVisible=True,
                  api=adb('shell', 'getprop', 'ro.build.version.sdk').stdout.decode().strip())
except BaseException as error:
    report.update(state='FAIL_OR_INCOMPLETE', error=str(error))
    raise
finally:
    report['evidenceHashes'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in out.iterdir() if p.is_file()}
    (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    done.set(); beat.join(timeout=2)
    if owner.exists() and owner.read_text(encoding='utf-8') == ticket:
        owner.unlink(); LOCK.rmdir()
    print(json.dumps(report), flush=True)
