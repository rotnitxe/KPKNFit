"""Run all exported-version SQLite migrations on the isolated Android app and retain receipts."""
from pathlib import Path
import hashlib
import json
import os
import re
import threading
import time
import uuid
from emulator_food_tests import adb, LOCK, PACKAGE, COMPONENT

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'artifacts/stability-verification/native-migration-33'
assert PACKAGE=='com.example.kpkn.stability' and not OUT.exists()
OUT.mkdir()
LOCK.mkdir()
ticket=uuid.uuid4().hex
owner=LOCK/'owner.txt'; owner.write_text(ticket,encoding='utf-8')
done=threading.Event()
def heartbeat():
    while not done.wait(10): os.utime(LOCK,None)
thread=threading.Thread(target=heartbeat,daemon=True); thread.start()
report=dict(state='RUNNING',limits='One instrumented API36 test exercises eleven exported schemas20..30 with raw personal-column markers; not domain decoding, real old-install files, schemas1..19, all devices or filesystem-full fidelity.')
try:
    assert adb('get-state').stdout.strip()==b'device'
    apk=ROOT/'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    install=adb('install','-r','--no-incremental',str(apk),timeout=120)
    (OUT/'install.txt').write_bytes(install.stdout+install.stderr)
    assert install.returncode==0 and b'Success' in install.stdout
    assert adb('shell','am','force-stop',PACKAGE).returncode==0
    cls='com.example.kpkn.data.db.SupportedSchemaMigrationMatrixTest'
    runner=PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner'
    result=adb('shell','am','instrument','-w','-r','-e','class',cls,runner,timeout=300)
    raw=(result.stdout+result.stderr).decode('utf-8',errors='replace')
    (OUT/'instrumentation.txt').write_text(raw,encoding='utf-8')
    assert result.returncode==0 and re.search(r'OK \(1 test\)',raw),raw[-2000:]
    matches=re.findall(r'^INSTRUMENTATION_STATUS: kpkn_migration_matrix=(\[[^\r\n]+\])\r*$',raw,re.M)
    assert len(matches)==1
    receipts=json.loads(matches[0])
    assert [r['fromVersion'] for r in receipts]==list(range(20,31))
    for receipt in receipts:
        assert receipt['toVersion']==30 and receipt['rawColumnsPreserved'] is True
        assert receipt['personalTables']>=26 and receipt['originalColumns']>100
        schema=ROOT/f"android-native/app/schemas/com.example.kpkn.data.db.KpknDatabase/{receipt['fromVersion']}.json"
        assert receipt['schemaSha256']==hashlib.sha256(schema.read_bytes()).hexdigest()
    assert adb('shell','am','start','-W','-n',COMPONENT).returncode==0
    time.sleep(5)
    ui=adb('exec-out','uiautomator','dump','/dev/tty').stdout
    (OUT/'ui.xml').write_bytes(ui)
    assert b'<hierarchy' in ui and b'android:id/aerr_close' not in ui and b'android:id/aerr_wait' not in ui and b"isn't responding" not in ui
    (OUT/'main.png').write_bytes(adb('exec-out','screencap','-p').stdout)
    activity=adb('shell','dumpsys','activity','activities').stdout
    resumed=b'\n'.join(l for l in activity.splitlines() if b'topResumedActivity' in l or b'mResumedActivity' in l)
    (OUT/'activity.txt').write_bytes(resumed)
    assert COMPONENT.encode() in resumed
    report.update(state='PASS',versions=receipts,instrumentedTests=1,versionChains=11,
        testApkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),api=adb('shell','getprop','ro.build.version.sdk').stdout.decode().strip(),mainVisible=True,errorDialogPresent=False)
except BaseException as error:
    report.update(state='FAIL_OR_INCOMPLETE',errorType=type(error).__name__,error=str(error))
    raise
finally:
    done.set();thread.join(timeout=2)
    (OUT/'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    if owner.exists() and owner.read_text(encoding='utf-8')==ticket: owner.unlink(); LOCK.rmdir()
    print(json.dumps({k:v for k,v in report.items() if k!='versions'}),flush=True)
