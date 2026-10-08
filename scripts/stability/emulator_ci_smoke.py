"""Launch the isolated debug identity on a fresh CI emulator and preserve evidence.

This proves startup/page size only. Voice, camera, GPS and account flows require
their own functional tests. Local shared-emulator QA uses emulator_smoke.py.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def command(argv, timeout=30):
    return subprocess.run(argv, check=True, capture_output=True, timeout=timeout).stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--page-size', type=int, required=True)
    parser.add_argument('--api', type=int, required=True)
    args = parser.parse_args()
    if os.environ.get('CI') != 'true':
        raise RuntimeError('This helper operates only on a CI-owned emulator')
    args.output.mkdir(parents=True, exist_ok=True)
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    suffix = '.exe' if os.name == 'nt' else ''
    tools = sorted((sdk / 'build-tools').glob(f'*/aapt2{suffix}'))
    if not tools:
        raise RuntimeError('Android build tools are unavailable')
    badging = command([str(tools[-1]), 'dump', 'badging', str(args.apk)]).decode()
    package = re.search(r"package: name='([^']+)'", badging)[1]
    if package != 'com.example.kpkn.stability':
        raise RuntimeError('The APK is not the isolated review identity')
    serials = re.findall(r'^(emulator-\d+)\s+device$', command(['adb', 'devices']).decode(), re.M)
    if len(serials) != 1:
        raise RuntimeError('Exactly one CI emulator is required')
    serial = serials[0]
    adb = ['adb', '-s', serial]
    if command(adb + ['shell', 'getprop', 'ro.kernel.qemu']).decode().strip() != '1':
        raise RuntimeError('The target is not an emulator')
    api = int(command(adb + ['shell', 'getprop', 'ro.build.version.sdk']).decode().strip())
    page_query = subprocess.run(adb + ['shell', 'getconf', 'PAGE_SIZE'], capture_output=True, timeout=30).stdout.decode().strip()
    if page_query.isdigit():
        pages = int(page_query)
        page_method = 'getconf PAGE_SIZE'
    elif api < 29:
        mappings = command(adb + ['shell', 'cat', '/proc/self/smaps']).decode()
        pages = int(re.findall(r'^KernelPageSize:\s+(\d+) kB\s*$', mappings, re.M)[0]) * 1024
        page_method = '/proc/self/smaps KernelPageSize'
    else:
        raise RuntimeError('The runtime page-size query failed')
    if api != args.api or pages != args.page_size:
        raise RuntimeError(f'Unexpected emulator API/page size: {api}/{pages}')
    command(adb + ['install', '-r', str(args.apk)], timeout=180)
    command(adb + ['logcat', '-c'])
    component = package + '/com.example.kpkn.MainActivity'
    command(adb + ['shell', 'am', 'start', '-W', '-n', component], timeout=90)
    deadline = time.monotonic() + 120
    activity = ''
    visible = []
    while time.monotonic() < deadline:
        activity = command(adb + ['shell', 'dumpsys', 'activity', 'activities']).decode()
        foreground = re.search(r'(?:topResumedActivity|mResumedActivity):.*' + re.escape(component), activity)
        if foreground:
            command(adb + ['shell', 'uiautomator', 'dump', '/sdcard/kpkn-ci-window.xml'])
            tree = command(adb + ['shell', 'cat', '/sdcard/kpkn-ci-window.xml'])
            args.output.joinpath('window.xml').write_bytes(tree)
            visible = [node.get('text') or node.get('content-desc') for node in ET.fromstring(tree).iter('node')
                       if node.get('package') == package and (node.get('text') or node.get('content-desc'))]
            if visible:
                break
        time.sleep(2)
    else:
        raise RuntimeError('MainActivity did not render visible controls')
    log = command(adb + ['logcat', '-d', '-v', 'threadtime']).decode(errors='replace')
    args.output.joinpath('logcat.txt').write_text(log, encoding='utf-8')
    args.output.joinpath('activity.txt').write_text(activity, encoding='utf-8')
    args.output.joinpath('screen.png').write_bytes(command(adb + ['exec-out', 'screencap', '-p']))
    fatal = re.search(r'FATAL EXCEPTION|UnsatisfiedLinkError|dlopen failed|Fatal signal', log)
    report = {'status': 'FAIL' if fatal else 'PASS', 'evidenceLevel': 'EMULATOR_STARTUP_ONLY',
              'api': api, 'pageBytes': pages, 'pageSizeMethod': page_method, 'component': component, 'topResumed': True,
              'sha256': hashlib.file_digest(args.apk.open('rb'), 'sha256').hexdigest(),
              'visibleControls': visible, 'fullFeatureFlows': 'NOT_RUN'}
    args.output.joinpath('result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))
    if fatal:
        raise RuntimeError('A native or Android runtime failure appeared during startup')


if __name__ == '__main__':
    main()
