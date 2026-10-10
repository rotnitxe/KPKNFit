"""Actual local Firebase native client/Room with Compose assertions, not an ADB form journey."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import threading
import time
import uuid
from emulator_food_tests import adb, LOCK, PACKAGE, COMPONENT

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--out',required=True);args=parser.parse_args()
OUT=ROOT/'artifacts/stability-verification'/args.out
assert OUT.resolve().is_relative_to(ROOT/'artifacts/stability-verification') and not OUT.exists()
OUT.mkdir()
LOCK.mkdir();ticket=uuid.uuid4().hex
owner=LOCK/'owner.txt';owner.write_text(ticket,encoding='utf-8')
done=threading.Event()
def beat():
    while not done.wait(10):os.utime(LOCK,None)
thread=threading.Thread(target=beat,daemon=True);thread.start()
report=dict(state='RUNNING',limits='One native device/local Firebase, actual AccountRepository/AppDataSessions/Auth/Room, MainActivity and Compose email checks. Identity commands call actual API; not every form button, native two-device sync, all entities, Google/Play or remote project.')
try:
    assert PACKAGE=='com.example.kpkn.stability'
    for label,apk in [('app',ROOT/'android-native/app/build/outputs/apk/debug/app-debug.apk'),
                      ('test',ROOT/'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]:
        result=adb('install','-r','--no-incremental',str(apk),timeout=120)
        (OUT/('install-'+label+'.txt')).write_bytes(result.stdout+result.stderr)
        assert result.returncode==0 and b'Success' in result.stdout
        report[label+'ApkSha256']=hashlib.sha256(apk.read_bytes()).hexdigest()
    assert adb('shell','am','force-stop',PACKAGE).returncode==0
    start=adb('shell','date','+%m-%d %H:%M:%S.000').stdout.decode().strip()
    result=adb('shell','am','instrument','-w','-r','-e','class',
        'com.example.kpkn.data.account.LocalFirebaseAccountHandoverTest',
        PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner',timeout=600)
    raw=(result.stdout+result.stderr).decode('utf-8',errors='replace')
    (OUT/'instrumentation.txt').write_text(raw,encoding='utf-8')
    assert result.returncode==0 and re.search(r'OK \(1 test\)',raw),raw[-3000:]
    matches=re.findall(r'^INSTRUMENTATION_STATUS: kpkn_account_handover=(\{[^\r\n]+\})\r*$',raw,re.M)
    assert len(matches)==1
    receipt=json.loads(matches[0])
    assert receipt['state']=='PASS' and all(receipt[key] is True for key in ['distinctUids','accountARetained','accountBHiddenFromA','staleWriterRejected','finalGuest'])
    assert adb('shell','am','start','-W','-n',COMPONENT).returncode==0
    time.sleep(5)
    ui=adb('exec-out','uiautomator','dump','/dev/tty').stdout
    (OUT/'ui.xml').write_bytes(ui)
    (OUT/'main.png').write_bytes(adb('exec-out','screencap','-p').stdout)
    assert b'<hierarchy' in ui and b'android:id/aerr_close' not in ui and b'android:id/aerr_wait' not in ui
    activity=adb('shell','dumpsys','activity','activities').stdout
    resumed=b'\n'.join(line for line in activity.splitlines() if b'topResumedActivity' in line or b'mResumedActivity' in line)
    (OUT/'activity.txt').write_bytes(resumed)
    assert COMPONENT.encode() in resumed
    pid=adb('shell','pidof','-s',PACKAGE).stdout.decode().strip()
    assert pid.isdecimal()
    log=adb('logcat','-d','-v','threadtime','--pid',pid).stdout
    events=adb('logcat','-d','-b','events','-T',start,'-s','am_anr','am_crash').stdout
    (OUT/'logcat-final-pid.txt').write_bytes(log)
    (OUT/'events-bounded.txt').write_bytes(events)
    assert b'FATAL EXCEPTION' not in log and not any(PACKAGE.encode() in line for line in events.splitlines())
    report.update(state='PASS',receipt=receipt,api=adb('shell','getprop','ro.build.version.sdk').stdout.decode().strip(),
        mainVisible=True,appPid=int(pid),errorDialogPresent=False,logScope='Final PID available buffer and events bounded from this instrumentation start')
except BaseException as error:
    report.update(state='FAIL_OR_INCOMPLETE',errorType=type(error).__name__,error=str(error));raise
finally:
    if report['state']!='PASS':
        for name,command in [('failure-activity.txt',('shell','dumpsys','activity','activities')),
                             ('failure-logcat.txt',('logcat','-d','-v','threadtime')),
                             ('failure-screen.png',('exec-out','screencap','-p'))]:
            try: (OUT/name).write_bytes(adb(*command).stdout)
            except Exception as captureError: report.setdefault('captureErrors',[]).append(type(captureError).__name__)
    report['evidenceHashes']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in OUT.iterdir() if p.is_file() and p.name!='result.json'}
    done.set();thread.join(timeout=2)
    (OUT/'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    if owner.exists() and owner.read_text(encoding='utf-8')==ticket:owner.unlink();LOCK.rmdir()
    print(json.dumps(report),flush=True)
