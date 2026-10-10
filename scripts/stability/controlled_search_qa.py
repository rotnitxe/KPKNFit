"""Run unchanged search budget and recalculate metrics from120 raw samples; record host coordination."""
from pathlib import Path
import argparse
import hashlib
import json
import math
import os
import re
import threading
import time
import uuid
import xml.etree.ElementTree as ET
from emulator_food_tests import adb, ADB, LOCK, PACKAGE, COMPONENT
import subprocess

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--out', required=True)
args = parser.parse_args()
out = ROOT / 'artifacts/stability-verification' / args.out
assert out.resolve().is_relative_to(ROOT / 'artifacts/stability-verification') and not out.exists()
out.mkdir(parents=True)
def active_builds():
    result = []
    for slot in [Path(r'C:\kw\slots\1'),Path(r'C:\kw\slots\2')]:
        owner = slot / 'owner'
        beat = slot / 'beat'
        if owner.is_file() and beat.exists() and time.time()-beat.stat().st_mtime < 150:
            result.append(dict(slot=slot.name,owner=owner.read_text(encoding='utf-8',errors='replace').strip()))
    return result
builds = active_builds()
(out / 'builds-before.json').write_text(json.dumps(builds,indent=2)+'\n',encoding='utf-8')
assert not builds, 'Concurrent Gradle lease; measurement not started, lease preserved'
ticket = str(uuid.uuid4())
LOCK.mkdir()
owner = LOCK / 'owner.txt'
owner.write_text(f'ticket={ticket} worktree={ROOT} purpose=controlled-search\n',encoding='utf-8')
done = threading.Event()
samples = []
def watch():
    while not done.is_set():
        samples.append(dict(epoch=time.time(),builds=active_builds()))
        os.utime(LOCK,None)
        done.wait(10)
watcher = threading.Thread(target=watch,daemon=True)
watcher.start()
report = dict(state='RUNNING', limits='Controlled shared-host Gradle coordination and measured API36 emulator; not physical hardware/cold search/general startup performance.')
try:
    assert PACKAGE == 'com.example.kpkn.stability' and adb('get-state').stdout.strip()==b'device'
    apk = ROOT / 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    installed = adb('install','-r','--no-incremental',str(apk),timeout=120)
    (out/'install.txt').write_bytes(installed.stdout+installed.stderr)
    assert installed.returncode==0 and b'Success' in installed.stdout
    assert adb('shell','am','force-stop',PACKAGE).returncode==0
    cls='com.example.kpkn.data.repository.NutritionSearchBenchmarkTest'
    runner=PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner'
    response=adb('shell','am','instrument','-w','-r','-e','class',cls,runner,timeout=300)
    raw=(response.stdout+response.stderr).decode('utf-8',errors='replace')
    (out/'instrumentation.txt').write_text(raw,encoding='utf-8')
    assert response.returncode==0 and re.search(r'OK \(1 test\)',raw), raw[-1800:]
    evidence=re.findall(r'^INSTRUMENTATION_STATUS: kpkn_search_result=(\{[^\r\n]+\})\r*$',raw,re.M)
    assert len(evidence)==1
    metrics=json.loads(evidence[0])
    timings=metrics['latenciesMs']
    assert len(timings)==metrics['samples']==120 and all(isinstance(x,(float,int)) and x>=0 for x in timings)
    assert metrics['queryCount']==12 and metrics['passes']==10 and metrics['warmupPasses']==3
    ordered=sorted(timings)
    calculated=dict(p95Ms=ordered[math.ceil(len(ordered)*.95)-1],medianMs=ordered[len(ordered)//2],maxMs=ordered[-1])
    assert all(abs(calculated[key]-metrics[key])<1e-8 for key in calculated)
    assert calculated['p95Ms']<metrics['budgetMs']==150 and metrics['storage']=='PROFILE_ROOM_FILE'
    (out/'samples.json').write_text(json.dumps(metrics,indent=2)+'\n',encoding='utf-8')
    assert not any(sample['builds'] for sample in samples), 'Concurrent build appeared; result retained but environment not controlled'
    launch=adb('shell','am','start','-W','-n',COMPONENT)
    (out/'launch.txt').write_bytes(launch.stdout+launch.stderr)
    assert launch.returncode==0
    time.sleep(5)
    tree=adb('exec-out','uiautomator','dump','/dev/tty').stdout
    (out/'ui.xml').write_bytes(tree)
    assert b'android:id/aerr_close' not in tree and b'android:id/aerr_wait' not in tree and b"isn't responding" not in tree and b'<hierarchy' in tree
    shot=adb('exec-out','screencap','-p').stdout
    assert shot.startswith(b'\x89PNG')
    (out/'main.png').write_bytes(shot)
    state=adb('shell','dumpsys','activity','activities').stdout
    resumed=b'\n'.join(line for line in state.splitlines() if b'topResumedActivity' in line or b'mResumedActivity' in line)
    (out/'activity.txt').write_bytes(resumed)
    assert COMPONENT.encode() in resumed
    pid=adb('shell','pidof','-s',PACKAGE).stdout.decode().strip()
    assert pid.isdecimal()
    (out/'logcat-app.txt').write_bytes(adb('logcat','-d','-v','threadtime','--pid',pid).stdout)
    report.update(state='PASS',metrics=metrics,recalculated=calculated,controlledGradleLoad=True,
        api=adb('shell','getprop','ro.build.version.sdk').stdout.decode().strip(),mainVisible=True,errorDialogPresent=False,
        testApkSha256=hashlib.sha256(apk.read_bytes()).hexdigest())
except BaseException as error:
    report.update(state='FAIL_OR_INCOMPLETE',error=str(error))
    raise
finally:
    done.set(); watcher.join(timeout=2)
    (out/'host-build-samples.json').write_text(json.dumps(samples,indent=2)+'\n',encoding='utf-8')
    (out/'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    if owner.exists() and f'ticket={ticket}' in owner.read_text(encoding='utf-8'): owner.unlink(); LOCK.rmdir()
    print(json.dumps(dict(state=report['state'],p95Ms=report.get('recalculated',{}).get('p95Ms'))),flush=True)
