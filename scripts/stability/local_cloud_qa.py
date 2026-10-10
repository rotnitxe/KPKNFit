"""Run demo Firebase emulators on loopback, never a live/authenticated project."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import socket
import subprocess
import threading
import time
import uuid

ROOT=Path(__file__).resolve().parents[2]
CLOUD=ROOT/'cloud-sync'
parser=argparse.ArgumentParser()
parser.add_argument('--out',required=True)
parser.add_argument('--mode',choices=['integration','native-account','native-instrumented'],default='integration')
args=parser.parse_args()
assert re.fullmatch(r'[a-z0-9-]{1,95}',args.out)
OUT=ROOT/'artifacts/stability-verification'/args.out
assert OUT.resolve().is_relative_to(ROOT/'artifacts/stability-verification')
NODE22=CLOUD/'.toolchain/verification-node22/node-v22.23.3-win-x64/node.exe'
NODE24=Path(r'C:\Users\valen\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe')
JAVA=Path(r'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot')
assert subprocess.check_output([str(NODE22),'--version'],text=True).strip()=='v22.23.3'
assert subprocess.check_output([str(NODE24),'--version'],text=True).strip()=='v24.19.0'
assert not OUT.exists()
OUT.mkdir()
LOCK=CLOUD/'.toolchain/local-emulator.lock'
LOCK.mkdir()
ticket=str(uuid.uuid4())
(LOCK/'owner').write_text(ticket,encoding='utf-8')
done=threading.Event()
def heartbeat():
    while not done.wait(10): os.utime(LOCK,None)
thread=threading.Thread(target=heartbeat,daemon=True); thread.start()
report=dict(state='RUNNING',project='demo-kpkn-stability',functionsRuntime='v22.23.3',rulesClientRuntime='v24.19.0',
    limits='Local emulators/rules/client fixtures only; no live Play purchases, remote project, RTDN/PubSub delivery, production App Check or multi-device native accounts.')
try:
    ports=[19099,18080,19199,15001,15002,14400,14500]
    for port in ports:
        with socket.socket() as probe:
            assert probe.connect_ex(('127.0.0.1',port))!=0, f'Port {port} already owned; preserve existing service'
    config=json.loads((CLOUD/'firebase.json').read_text(encoding='utf-8'))
    assert all(item.get('host')=='127.0.0.1' for name,item in config['emulators'].items() if isinstance(item,dict) and 'port' in item)
    environment=dict(os.environ,PATH=str(NODE22.parent)+os.pathsep+str(JAVA/'bin')+os.pathsep+os.environ.get('PATH',''),
        JAVA_HOME=str(JAVA),FUNCTIONS_DISCOVERY_TIMEOUT='60',FIREBASE_CLI_DISABLE_USAGE='true',CI='true')
    # Deliberately use a demo project. Remove credential environment hooks from this subprocess.
    for key in ['GOOGLE_APPLICATION_CREDENTIALS','FIREBASE_TOKEN','GOOGLE_CLOUD_PROJECT','GCLOUD_PROJECT']:
        environment.pop(key,None)
    tests=sorted((CLOUD/'functions/integration').glob('*.test.mjs'))
    # The Firebase tool expects a shell command; every path below is fixed/local and quoted.
    assert all('"' not in str(p) and '$' not in str(p) and '`' not in str(p) for p in [NODE24,*tests])
    inner='cd functions && "'+str(NODE24)+'" --test --test-concurrency=1 '+' '.join('"integration/'+p.name+'"' for p in tests)
    if args.mode=='native-account':
        python=Path(r'C:\Users\valen\AppData\Local\Programs\Python\Python313\python.exe')
        inner='"'+str(python)+'" "'+str(ROOT/'scripts/stability/native_account_ui_qa.py')+'" --out '+args.out+'-ui'
    if args.mode=='native-instrumented':
        python=Path(r'C:\Users\valen\AppData\Local\Programs\Python\Python313\python.exe')
        inner='"'+str(python)+'" "'+str(ROOT/'scripts/stability/native_account_instrumented_qa.py')+'" --out '+args.out+'-native'
    report['testMode']=args.mode
    command=[str(NODE22),str(CLOUD/'node_modules/firebase-tools/lib/bin/firebase.js'),'emulators:exec',
        '--project','demo-kpkn-stability','--only','firestore,auth,storage,functions',inner]
    started=time.time()
    with (OUT/'firebase.log').open('wb') as log:
        process=subprocess.Popen(command,cwd=CLOUD,env=environment,stdout=log,stderr=subprocess.STDOUT)
        code=process.wait()
    raw=(OUT/'firebase.log').read_text(encoding='utf-8',errors='replace')
    assert 'Using node@22 from host' in raw, 'Functions runtime Node22 not confirmed'
    report.update(state='PASS' if code==0 else 'FAIL',exitCode=code,elapsedSeconds=time.time()-started,
        sourceHashes={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [CLOUD/'firebase.json',CLOUD/'package-lock.json',CLOUD/'functions/package-lock.json',*(tests if args.mode=='integration' else [ROOT/'scripts/stability'/('native_account_instrumented_qa.py' if args.mode=='native-instrumented' else 'native_account_ui_qa.py')])]})
except BaseException as error:
    report.update(state='FAIL_OR_INCOMPLETE',error=str(error))
    raise
finally:
    done.set(); thread.join(timeout=2)
    (OUT/'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    if (LOCK/'owner').exists() and (LOCK/'owner').read_text(encoding='utf-8')==ticket:
        (LOCK/'owner').unlink(); LOCK.rmdir()
    print(json.dumps(report),flush=True)
raise SystemExit(0 if report['state']=='PASS' else 1)
