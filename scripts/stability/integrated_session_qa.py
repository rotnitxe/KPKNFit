from pathlib import Path
import hashlib
import json
import os
import re
import subprocess
import sys
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/stability'))
from emulator_food_tests import ADB, LOCK, SERIAL, PACKAGE, COMPONENT, adb

out = ROOT / 'artifacts/stability-verification' / sys.argv[1]
assert out.resolve().is_relative_to(ROOT / 'artifacts/stability-verification') and not out.exists()
out.mkdir(parents=True)
ticket = str(uuid.uuid4())
report = dict(state='FAIL_OR_INCOMPLETE', runId=ticket, phases=[],
              limits='Real MainActivity/local maintenance and synthetic guest handover; no live Firebase, Play or interruption inside restore.')
try:
    LOCK.mkdir()
except FileExistsError:
    raise SystemExit('Another emulator owner is working; its lock was preserved')
owner = LOCK / 'owner.txt'
owner.write_text(f'ticket={ticket} worktree={ROOT}\n', encoding='utf-8')
done = threading.Event()
def heartbeat():
    while not done.wait(15):
        try: os.utime(LOCK, None)
        except OSError: return
beat = threading.Thread(target=heartbeat, daemon=True)
beat.start()
try:
    assert PACKAGE == 'com.example.kpkn.stability'
    assert adb('get-state').stdout.strip() == b'device'
    aapt = sorted(ADB.parents[1].glob('build-tools/*/aapt2.exe'))[-1]
    for name, apk, expected in [
        ('app', ROOT / 'android-native/app/build/outputs/apk/debug/app-debug.apk', PACKAGE),
        ('test', ROOT / 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk', PACKAGE + '.test')]:
        metadata = subprocess.run([str(aapt), 'dump', 'badging', str(apk)], capture_output=True, check=True).stdout.decode()
        assert f"name='{expected}'" in metadata
        installed = adb('install', '-r', '--no-incremental', str(apk), timeout=120)
        (out / f'install-{name}.txt').write_bytes(installed.stdout + installed.stderr)
        assert installed.returncode == 0 and b'Success' in installed.stdout
        report[name + 'ApkSha256'] = hashlib.file_digest(apk.open('rb'), 'sha256').hexdigest()
    runner = PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'
    assert f'instrumentation:{runner} (target={PACKAGE})' in adb('shell', 'pm', 'list', 'instrumentation').stdout.decode()
    start = adb('shell', 'date', '+%m-%d %H:%M:%S.000').stdout.decode().strip()
    assert adb('shell', 'am', 'force-stop', PACKAGE).returncode == 0
    registry = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
        'com.example.kpkn.data.account.ProfileRegistryPlatformRecoveryTest',
        '-e', 'kpkn_run_id', ticket, runner, timeout=180)
    (out / 'registry-platform.txt').write_bytes(registry.stdout + registry.stderr)
    registry_text = registry.stdout.decode(errors='replace')
    report['registryPlatformRecovery'] = dict(returnCode=registry.returncode,
        passed='OK (1 test)' in registry_text,
        committedOwnerRecovered='kpkn_registry_result=committed-owner-recovered' in registry_text)
    assert registry.returncode == 0 and report['registryPlatformRecovery']['passed'] and report['registryPlatformRecovery']['committedOwnerRecovered']
    for phase, method in enumerate([
        'phase1FinishDiscardAndRestoreThroughActualCoordinator',
        'phase2ColdRestoreThenExplicitErase',
        'phase3ColdEraseAndSyntheticGuestHandover'], 1):
        prior = adb('shell', 'pidof', '-s', PACKAGE).stdout.decode().strip()
        assert adb('shell', 'am', 'force-stop', PACKAGE).returncode == 0
        assert not adb('shell', 'pidof', '-s', PACKAGE).stdout.strip()
        cmd = [str(ADB), '-s', SERIAL, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
               'com.example.kpkn.data.backup.IntegratedDataSessionUiTest#' + method,
               '-e', 'kpkn_run_id', ticket, runner]
        with (out / f'phase-{phase}.txt').open('wb') as stream:
            process = subprocess.Popen(cmd, stdout=stream, stderr=subprocess.STDOUT,
                                       env=dict(os.environ, MSYS_NO_PATHCONV='1'))
            try: code = process.wait(timeout=360)
            except subprocess.TimeoutExpired:
                adb('shell', 'am', 'force-stop', PACKAGE)
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: process.terminate(); process.wait(timeout=10)
                code = 124
        result = (out / f'phase-{phase}.txt').read_text(encoding='utf-8', errors='replace')
        pid = re.search(r'INSTRUMENTATION_STATUS: kpkn_integrated_pid=(\d+)', result)
        record = dict(method=method, returnCode=code, passed=bool(re.search(r'OK \(1 test\)', result)),
                      priorPid=prior, coldPid=int(pid.group(1)) if pid else None)
        report['phases'].append(record)
        failure_image = adb('exec-out', 'run-as', PACKAGE, 'cat', f'cache/qa-integrated-{ticket}/erase-failure.png')
        if failure_image.returncode == 0 and failure_image.stdout.startswith(b'\x89PNG\r\n\x1a\n'):
            (out / f'phase-{phase}-failure.png').write_bytes(failure_image.stdout)
        ending_image = adb('exec-out', 'run-as', PACKAGE, 'cat', f'cache/qa-integrated-{ticket}/ending.png')
        if ending_image.returncode == 0 and ending_image.stdout.startswith(b'\x89PNG\r\n\x1a\n'):
            (out / f'phase-{phase}-ending.png').write_bytes(ending_image.stdout)
        ending_tree = adb('exec-out', 'run-as', PACKAGE, 'cat', f'cache/qa-integrated-{ticket}/ending-semantics.txt')
        if ending_tree.returncode == 0 and ending_tree.stdout.lstrip().startswith(b'Printing with useUnmergedTree'):
            (out / f'phase-{phase}-semantics.txt').write_bytes(ending_tree.stdout)
        activity = adb('shell', 'dumpsys', 'activity', 'activities').stdout
        (out / f'phase-{phase}-activity.txt').write_bytes(activity)
        target_visible = any(PACKAGE.encode() in line and (b'topResumedActivity' in line or b'mResumedActivity' in line) for line in activity.splitlines())
        if target_visible:
            (out / f'phase-{phase}-ui.xml').write_bytes(adb('exec-out', 'uiautomator', 'dump', '/dev/tty').stdout)
            (out / f'phase-{phase}-screen.png').write_bytes(adb('exec-out', 'screencap', '-p').stdout)
        if code or not record['passed'] or not pid: raise SystemExit('Native phase failed: ' + method)
    assert len({p['coldPid'] for p in report['phases']}) == 3
    launched = adb('shell', 'am', 'start', '-W', '-n', COMPONENT)
    (out / 'launch.txt').write_bytes(launched.stdout + launched.stderr)
    time.sleep(5)
    activity = adb('shell', 'dumpsys', 'activity', 'activities').stdout
    (out / 'activity.txt').write_bytes(activity)
    report['mainVisible'] = any(PACKAGE.encode() in line and b'com.example.kpkn.MainActivity' in line and
        (b'topResumedActivity' in line or b'mResumedActivity' in line) for line in activity.splitlines())
    (out / 'screen.png').write_bytes(adb('exec-out', 'screencap', '-p').stdout)
    assert report['mainVisible']
    tree = adb('exec-out', 'uiautomator', 'dump', '/dev/tty').stdout
    (out / 'ui.xml').write_bytes(tree)
    assert b'<hierarchy' in tree and b'android:id/aerr_close' not in tree and b'android:id/aerr_wait' not in tree and b"isn't responding" not in tree
    report.update(state='PASS', errorDialogPresent=False, testCount=3, api=adb('shell', 'getprop', 'ro.build.version.sdk').stdout.decode().strip())
finally:
    if 'start' in globals(): (out / 'logcat-available.txt').write_bytes(adb('logcat', '-d', '-v', 'threadtime').stdout)
    (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))
    done.set(); beat.join(timeout=2)
    if owner.exists() and f'ticket={ticket}' in owner.read_text(encoding='utf-8'): owner.unlink(); LOCK.rmdir()
