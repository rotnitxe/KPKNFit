"""Native media admission plus real process-death backup/erasure checks on the isolated app."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import threading
import time
import uuid
from emulator_food_tests import ADB, LOCK, ROOT, SERIAL, PACKAGE, COMPONENT, adb


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    out = Path(args.out).resolve()
    if not out.is_relative_to(ROOT): raise SystemExit('Evidence must remain in this checkout')
    out.mkdir(parents=True, exist_ok=False)
    ticket = str(uuid.uuid4())
    try: LOCK.mkdir()
    except FileExistsError: raise SystemExit('Another emulator owner is working; its lock was preserved')
    owner = LOCK / 'owner.txt'
    owner.write_text(f'ticket={ticket} worktree={ROOT}\n')
    done = threading.Event()
    def heartbeat():
        while not done.wait(15):
            try: os.utime(LOCK, None)
            except OSError: return
    beat = threading.Thread(target=heartbeat, daemon=True)
    beat.start()
    report = {'state': 'FAIL_OR_INCOMPLETE', 'runId': ticket, 'phases': [],
              'limits': 'Core backup/profile controls; not account UI, live Firebase, or real Play Billing.'}
    try:
        if adb('get-state').stdout.strip() != b'device': raise SystemExit('Agreed emulator unavailable')
        apk = ROOT / 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
        aapt = sorted(ADB.parents[1].glob('build-tools/*/aapt2.exe'))[-1]
        metadata = subprocess.run([str(aapt), 'dump', 'badging', str(apk)], capture_output=True, check=True).stdout.decode()
        if "name='com.example.kpkn.stability.test'" not in metadata:
            raise SystemExit('Only isolated instrumentation is permitted')
        installed = adb('install', '-r', '--no-incremental', str(apk), timeout=120)
        (out / 'install.txt').write_bytes(installed.stdout + installed.stderr)
        if installed.returncode or b'Success' not in installed.stdout: raise SystemExit('Install failed')
        runner = 'com.example.kpkn.stability.test/androidx.test.runner.AndroidJUnitRunner'
        if f'instrumentation:{runner} (target={PACKAGE})' not in adb('shell', 'pm', 'list', 'instrumentation').stdout.decode():
            raise SystemExit('Unexpected instrumentation target')
        start = adb('shell', 'date', '+%m-%d %H:%M:%S.000').stdout.decode().strip()
        methods = [
            ('com.example.kpkn.data.repository.WorkoutMediaUriDurabilityInstrumentedTest', 5, False),
            ('com.example.kpkn.screens.workout.WorkoutExternalCameraCaptureInstrumentedTest', 4, False),
            ('com.example.kpkn.data.repository.WorkoutMediaLateRetryInstrumentedTest', 4, False),
            *[('com.example.kpkn.data.repository.DataIsolationColdProcessTest#' + method, 1, True) for method in [
                'phase1SeedAndExport', 'phase2RestoreInNewProcess',
                'phase3VerifyRestoreExportAndErase', 'phase4VerifyErasureAndIsolationInNewProcess']],
        ]
        cold_pids = []
        for index, (method, expected, is_cold) in enumerate(methods):
            # Force-stop only this synthetic target; never clear data or stop the user's package.
            before = adb('shell', 'pidof', '-s', PACKAGE).stdout.decode().strip()
            stopped = adb('shell', 'am', 'force-stop', PACKAGE)
            absent = not adb('shell', 'pidof', '-s', PACKAGE).stdout.strip()
            if stopped.returncode or not absent: raise SystemExit('Isolated process did not stop')
            cmd = [str(ADB), '-s', SERIAL, 'shell', 'am', 'instrument', '-w', '-r',
                   '-e', 'class', method, '-e', 'kpkn_run_id', ticket, runner]
            with (out / f'phase-{index}.txt').open('wb') as log:
                process = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT,
                                           env=dict(os.environ, MSYS_NO_PATHCONV='1'))
                try: return_code = process.wait(timeout=300)
                except subprocess.TimeoutExpired:
                    adb('shell', 'am', 'force-stop', PACKAGE)
                    try: process.wait(timeout=15)
                    except subprocess.TimeoutExpired: process.terminate(); process.wait(timeout=10)
                    return_code = 124
            text = (out / f'phase-{index}.txt').read_text(encoding='utf-8', errors='replace')
            passed = re.search(r'OK \((\d+) tests?\)', text)
            success = bool(passed and int(passed.group(1)) == expected)
            cold = re.search(r'INSTRUMENTATION_STATUS: kpkn_cold_pid=(\d+)', text)
            record = dict(method=method, returnCode=return_code, passed=success, expectedCount=expected,
                          priorPid=before, absentAfterStop=absent, coldPid=int(cold.group(1)) if cold else None)
            report['phases'].append(record)
            if return_code or not success: raise SystemExit('Native phase failed: ' + method)
            if is_cold:
                if not cold: raise SystemExit('Missing phase process identity')
                cold_pids.append(int(cold.group(1)))
        if len(set(cold_pids)) != 4: raise SystemExit('Cold phases reused a process')
        launched = adb('shell', 'am', 'start', '-W', '-n', COMPONENT)
        (out / 'launch.txt').write_bytes(launched.stdout + launched.stderr)
        time.sleep(5)
        activity = adb('shell', 'dumpsys', 'activity', 'activities').stdout
        (out / 'activity.txt').write_bytes(activity)
        report['mainVisible'] = any(PACKAGE.encode() in line and b'com.example.kpkn.MainActivity' in line and
            (b'topResumedActivity' in line or b'mResumedActivity' in line) for line in activity.splitlines())
        (out / 'screen.png').write_bytes(adb('exec-out', 'screencap', '-p').stdout)
        (out / 'ui.xml').write_bytes(adb('exec-out', 'uiautomator', 'dump', '/dev/tty').stdout)
        (out / 'logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'threadtime', '-T', start).stdout)
        report.update(testCount=sum(expected for _, expected, _ in methods),
                      testApkSha256=hashlib.file_digest(apk.open('rb'), 'sha256').hexdigest(),
                      api=adb('shell', 'getprop', 'ro.build.version.sdk').stdout.decode().strip(),
                      coldPids=cold_pids)
        if not report['mainVisible']: raise SystemExit('MainActivity is not top resumed')
        report['state'] = 'PASS'
    finally:
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(report))
        done.set(); beat.join(timeout=2)
        if owner.exists() and f'ticket={ticket}' in owner.read_text(): owner.unlink(); LOCK.rmdir()


if __name__ == '__main__': main()
