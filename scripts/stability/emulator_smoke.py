"""Smoke QA for this worktree's debug identity, under the team's emulator lock.

Never touches the personal phone, com.example.kpkn, or com.example.kpkn.dbg.
No data clearing, uninstallation, or lock reclamation is implemented.
"""
from pathlib import Path
import argparse
import hashlib
import os
import re
import subprocess
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
SDK = Path(r'C:\Users\valen\AppData\Local\Android\Sdk')
ADB = SDK / 'platform-tools/adb.exe'
LOCK = Path(r'C:\kw\emu.lock')
SERIAL = 'emulator-5554'
PACKAGE = 'com.example.kpkn.stability'
COMPONENT = f'{PACKAGE}/com.example.kpkn.MainActivity'

def adb(*args, timeout=40):
    return subprocess.run([str(ADB), '-s', SERIAL, *args], capture_output=True,
                          timeout=timeout, env=dict(os.environ, MSYS_NO_PATHCONV='1'))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', required=True)
    parser.add_argument('--out', required=True)
    parser.add_argument('--start-readonly-emulator', action='store_true')
    parser.add_argument('--avd', default='Pixel_9_Pro_XL')
    parser.add_argument('--gpu', choices=['swiftshader', 'auto', 'host'], default='swiftshader')
    parser.add_argument('--qa-sdk', type=Path)
    parser.add_argument('--expected-page-size', type=int)
    args = parser.parse_args()
    apk, out = Path(args.apk).resolve(), Path(args.out).resolve()
    if not apk.is_relative_to(ROOT) or not out.is_relative_to(ROOT):
        raise SystemExit('APK and evidence must stay within this worktree')
    if args.qa_sdk and not args.qa_sdk.resolve().is_relative_to(ROOT):
        raise SystemExit('The isolated QA SDK must stay within this worktree')
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', args.avd):
        raise SystemExit('Invalid AVD identity')
    aapt = sorted(SDK.glob('build-tools/*/aapt2.exe'))[-1]
    result = subprocess.run([str(aapt), 'dump', 'packagename', str(apk)], capture_output=True, check=True)
    if result.stdout.decode().strip() != PACKAGE:
        raise SystemExit('Refusing to install any package other than the isolated stability identity')
    out.mkdir(parents=True, exist_ok=True)
    ticket = str(uuid.uuid4())
    acquired = False
    done = threading.Event()
    start_wait = time.monotonic()
    while not acquired:
        try:
            LOCK.mkdir()
            (LOCK / 'owner.txt').write_text(f'pid={os.getpid()} ticket={ticket} worktree={ROOT}\n')
            acquired = True
        except FileExistsError:
            if time.monotonic() - start_wait > 900:
                raise SystemExit('Emulator lock is occupied; no other owner was interrupted')
            time.sleep(3)

    def heartbeat():
        while not done.wait(15):
            try: os.utime(LOCK, None)
            except OSError: return
    beat = threading.Thread(target=heartbeat, daemon=True)
    beat.start()
    emulator_output = None
    try:
        if adb('get-state').stdout.strip() != b'device' and args.start_readonly_emulator:
            # A temporary, headless instance does not save the existing AVD's data or snapshots.
            memory = subprocess.run(['powershell', '-NoProfile', '-Command',
                '[math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1MB,1).ToString([System.Globalization.CultureInfo]::InvariantCulture)'],
                capture_output=True, text=True, check=True)
            if float(memory.stdout.strip()) < 4:
                raise SystemExit('Not enough free RAM for temporary emulator QA; other work was left running')
            emulator_output = (out / 'emulator-startup.txt').open('wb')
            emulator_env = dict(os.environ)
            image_options = []
            if args.qa_sdk:
                qa_sdk = args.qa_sdk.resolve()
                # The emulator binary still belongs to the installed SDK. Point its
                # system image explicitly at the isolated downloaded image.
                emulator_env.update(ANDROID_HOME=str(SDK), ANDROID_SDK_ROOT=str(SDK),
                                    ANDROID_AVD_HOME=str(qa_sdk / 'avds'), ANDROID_USER_HOME=str(qa_sdk / 'user'))
                config = (qa_sdk / 'avds' / (args.avd + '.avd') / 'config.ini').read_text()
                image_binding = re.search(r'^\s*image\.sysdir\.1\s*=\s*(.+)$', config, re.M)
                if image_binding is None:
                    raise SystemExit('The AVD has no declared system image')
                relative_image = image_binding[1].strip().replace('\\', '/')
                system_image = (qa_sdk / relative_image).resolve()
                if not system_image.is_relative_to(qa_sdk) or not system_image.is_dir():
                    raise SystemExit('The AVD image must belong to the isolated QA SDK')
                image_options = ['-sysdir', str(system_image)]
            process = subprocess.Popen([str(SDK / 'emulator/emulator.exe'), '-avd', args.avd,
                '-port', '5554', '-read-only', '-no-snapshot', '-no-window', '-no-audio', '-memory', '2048',
                '-gpu', args.gpu, *image_options], stdout=emulator_output, stderr=subprocess.STDOUT,
                env=emulator_env, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
            (out / 'emulator-instance.txt').write_text(f'launcherPid={process.pid}\nserial={SERIAL}\navd={args.avd}\nreadOnly=true\n')
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                if adb('get-state').stdout.strip() == b'device' and adb('shell', 'getprop', 'sys.boot_completed').stdout.strip() == b'1':
                    break
                time.sleep(2)
        if adb('get-state').stdout.strip() != b'device':
            raise SystemExit('The agreed emulator is not available')
        page_response = adb('shell', 'getconf', 'PAGE_SIZE').stdout.strip()
        if page_response.isdigit():
            page_bytes = int(page_response)
        else:
            # Android 8's toybox has no getconf. Report the kernel mapping page
            # size only for this old platform, rather than assuming a value.
            api_level = int(adb('shell', 'getprop', 'ro.build.version.sdk').stdout.strip())
            if api_level >= 29:
                raise SystemExit('The runtime page-size query failed')
            mappings = adb('shell', 'cat', '/proc/self/smaps').stdout.decode(errors='replace')
            sizes = re.findall(r'^KernelPageSize:\s+(\d+) kB\s*$', mappings, re.M)
            if not sizes:
                raise SystemExit('No kernel page-size evidence is available')
            page_bytes = int(sizes[0]) * 1024
            (out / 'page-size-method.txt').write_text('API<29: /proc/self/smaps KernelPageSize\n')
        (out / 'page-size.txt').write_text(str(page_bytes) + '\n')
        if args.expected_page_size and page_bytes != args.expected_page_size:
            raise SystemExit(f'Unexpected page size {page_bytes}')
        (out / 'system-events-before-app.txt').write_bytes(adb('logcat', '-d', '-b', 'events',
            '-s', 'am_anr', 'am_crash').stdout)
        (out / 'device-uptime-before-app.txt').write_bytes(adb('shell', 'cat', '/proc/uptime').stdout)
        start_time = adb('shell', 'date', '+%m-%d %H:%M:%S.000').stdout.decode().strip()
        install = adb('install', '-r', '--no-incremental', str(apk), timeout=120)
        (out / 'install.txt').write_bytes(install.stdout + install.stderr)
        if install.returncode or b'Success' not in install.stdout:
            raise SystemExit('Isolated APK installation failed')
        adb('shell', 'am', 'force-stop', PACKAGE)
        launch = adb('shell', 'am', 'start', '-W', '-n', COMPONENT)
        (out / 'launch.txt').write_bytes(launch.stdout + launch.stderr)
        if launch.returncode: raise SystemExit('MainActivity launch failed')
        time.sleep(12)
        activity = adb('shell', 'dumpsys', 'activity', 'activities').stdout
        (out / 'activity.txt').write_bytes(activity)
        resumed = [line for line in activity.decode(errors='replace').splitlines() if 'topResumedActivity' in line or 'mResumedActivity' in line]
        if not any(COMPONENT in line for line in resumed):
            raise SystemExit('The correct MainActivity is not top-resumed; no successful launch is claimed')
        screenshot = adb('exec-out', 'screencap', '-p').stdout
        if not screenshot.startswith(b'\x89PNG'): raise SystemExit('Screenshot capture failed')
        (out / 'main.png').write_bytes(screenshot)
        hierarchy = adb('exec-out', 'uiautomator', 'dump', '/dev/tty').stdout
        end = hierarchy.find(b'</hierarchy>')
        if end < 0:
            raise SystemExit('UI tree capture failed')
        (out / 'ui.xml').write_bytes(hierarchy[:end + len(b'</hierarchy>')])
        app_pid = adb('shell', 'pidof', '-s', PACKAGE).stdout.decode().strip()
        if app_pid:
            (out / 'logcat.txt').write_bytes(adb('logcat', '-d', '--pid', app_pid, '-T', start_time).stdout)
        checksum = hashlib.sha256()
        with apk.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                checksum.update(chunk)
        (out / 'apk-sha256.txt').write_text(checksum.hexdigest() + '\n')
        (out / 'system-events-after-app.txt').write_bytes(adb('logcat', '-d', '-b', 'events',
            '-s', 'am_anr', 'am_crash', '-T', start_time).stdout)
        # Preserve every captured artifact before rejecting an obstructed foreground.
        # Top-resumed alone does not prove that the application's controls are available.
        if b'android:id/aerr_close' in hierarchy or b'android:id/aerr_wait' in hierarchy or b"isn't responding" in hierarchy:
            raise SystemExit('Foreground blocked by an error/ANR dialog; original evidence retained, launch QA not approved')
        print('INSTALL_SUCCESS; MAIN_ACTIVITY_TOP_RESUMED; fresh evidence:', out, flush=True)
    finally:
        if emulator_output is not None:
            emulator_output.close()
        done.set(); beat.join(timeout=2)
        # Only our exact lock ownership can be released. Never recursively delete a shared lock.
        owner = LOCK / 'owner.txt'
        if acquired and owner.exists() and f'ticket={ticket}' in owner.read_text():
            owner.unlink()
            LOCK.rmdir()

if __name__ == '__main__':
    main()
