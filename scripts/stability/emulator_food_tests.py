"""Run only the selected food tests on the isolated stability application."""
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

ROOT = Path(__file__).resolve().parents[2]
ADB = Path(r'C:\Users\valen\AppData\Local\Android\Sdk\platform-tools\adb.exe')
LOCK = Path(r'C:\kw\emu.lock')
SERIAL = 'emulator-5554'
PACKAGE = 'com.example.kpkn.stability'
COMPONENT = PACKAGE + '/com.example.kpkn.MainActivity'
TESTS = ['com.example.kpkn.domain.nutrition.FoodParserAndroidTest',
         'com.example.kpkn.data.db.NutritionMigrationTest',
         'com.example.kpkn.screens.nutrition.components.FoodLoggerDrawerDraftUiTest']


def adb(*args, timeout=60):
    return subprocess.run([str(ADB), '-s', SERIAL, *args], capture_output=True, timeout=timeout,
                          env=dict(os.environ, MSYS_NO_PATHCONV='1'))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--suite', choices=['food', 'performance', 'media'], default='food')
    args = parser.parse_args()
    suites = {'food': (TESTS, 8), 'performance': (['com.example.kpkn.data.repository.NutritionSearchBenchmarkTest'], 1),
              'media': (['com.example.kpkn.data.media.MediaContainerGalleryInstrumentedTest'], 3)}
    tests, expected_count = suites[args.suite]
    out = Path(args.out).resolve()
    if not out.is_relative_to(ROOT): raise SystemExit('Evidence must stay in this checkout')
    out.mkdir(parents=True, exist_ok=True)
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
    try:
        if adb('get-state').stdout.strip() != b'device': raise SystemExit('The agreed emulator is unavailable')
        apk = ROOT / 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
        aapt = sorted(ADB.parents[1].glob('build-tools/*/aapt2.exe'))[-1]
        metadata = subprocess.run([str(aapt), 'dump', 'badging', str(apk)], capture_output=True, check=True).stdout.decode()
        if "name='com.example.kpkn.stability.test'" not in metadata:
            raise SystemExit('Only the isolated test APK is permitted')
        installed = adb('install', '-r', '--no-incremental', str(apk), timeout=120)
        (out / 'install.txt').write_bytes(installed.stdout + installed.stderr)
        if installed.returncode or b'Success' not in installed.stdout: raise SystemExit('Test APK installation failed')
        instrumentation = adb('shell', 'pm', 'list', 'instrumentation').stdout.decode()
        runner = 'com.example.kpkn.stability.test/androidx.test.runner.AndroidJUnitRunner'
        if f'instrumentation:{runner} (target={PACKAGE})' not in instrumentation:
            raise SystemExit('Instrumentation must target only the isolated stability app')
        start = adb('shell', 'date', '+%m-%d %H:%M:%S.000').stdout.decode().strip()
        with (out / 'instrumentation.txt').open('wb') as log:
            process = subprocess.Popen([str(ADB), '-s', SERIAL, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', ','.join(tests), runner],
                stdout=log, stderr=subprocess.STDOUT, env=dict(os.environ, MSYS_NO_PATHCONV='1'))
            try:
                return_code = process.wait(timeout=900)
            except subprocess.TimeoutExpired:
                # This harness owns only this synthetic target. End its runner
                # before releasing the shared device lock; never stop another app.
                adb('shell', 'am', 'force-stop', PACKAGE)
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.terminate(); process.wait(timeout=10)
                return_code = 124
        text = (out / 'instrumentation.txt').read_text(encoding='utf-8', errors='replace')
        passed = re.search(r'OK \((\d+) tests?\)', text)
        report = {'state': 'PASS' if return_code == 0 and passed and int(passed.group(1)) == expected_count else 'FAIL_OR_INCOMPLETE',
                  'testCount': int(passed.group(1)) if passed else None, 'classes': tests,
                  'testApkSha256': hashlib.file_digest(apk.open('rb'), 'sha256').hexdigest(),
                  'api': adb('shell', 'getprop', 'ro.build.version.sdk').stdout.decode().strip()}
        metrics = re.findall(r'^INSTRUMENTATION_STATUS: kpkn_search_result=(\{[^\r\n]+\})$', text, re.MULTILINE)
        if args.suite == 'performance':
            if len(metrics) != 1:
                report['state'] = 'FAIL_OR_INCOMPLETE'
            else:
                report['search'] = json.loads(metrics[0])
                if report['search'].get('samples') != 120 or report['search'].get('storage') != 'PROFILE_ROOM_FILE':
                    report['state'] = 'FAIL_OR_INCOMPLETE'
        adb('shell', 'am', 'start', '-W', '-n', COMPONENT)
        time.sleep(5)
        activity = adb('shell', 'dumpsys', 'activity', 'activities').stdout
        (out / 'activity.txt').write_bytes(activity)
        report['mainVisible'] = any(PACKAGE.encode() in line and b'com.example.kpkn.MainActivity' in line and
            (b'topResumedActivity' in line or b'mResumedActivity' in line) for line in activity.splitlines())
        (out / 'logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'threadtime', '-T', start).stdout)
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(report))
        if report['state'] != 'PASS' or not report['mainVisible']: raise SystemExit(1)
    finally:
        done.set(); beat.join(timeout=2)
        if owner.exists() and f'ticket={ticket}' in owner.read_text():
            owner.unlink(); LOCK.rmdir()


if __name__ == '__main__': main()
