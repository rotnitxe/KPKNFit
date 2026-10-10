"""Empty synthetic A/B account UI on local Firebase; never production/authenticated Google."""
from pathlib import Path
import argparse
import json
import os
import re
import threading
import time
import uuid
import xml.etree.ElementTree as ET
from emulator_food_tests import adb, LOCK, PACKAGE, COMPONENT
from emulator_review_qa import capture

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('--out',required=True)
args=parser.parse_args()
assert re.fullmatch(r'[a-z0-9-]{1,100}',args.out)
OUT=ROOT/'artifacts/stability-verification'/args.out
assert PACKAGE=='com.example.kpkn.stability' and not OUT.exists()
OUT.mkdir()
ticket=uuid.uuid4().hex
LOCK.mkdir()
owner=LOCK/'owner.txt'; owner.write_text('ticket='+ticket+' native-account-demo-only',encoding='utf-8')
done=threading.Event()
def heartbeat():
    while not done.wait(10): os.utime(LOCK,None)
worker=threading.Thread(target=heartbeat,daemon=True); worker.start()
report=dict(state='RUNNING',steps=[],
    limits='One API36 Android device, empty synthetic account A/B lifecycle. No sync mutations. No all-entity guest incorporation, two native devices, real Google/Play, production App Check or full deletion proof.')
counter=0
def screen(label):
    global counter
    counter+=1
    tree,visible=capture(OUT,f'{counter:02d}-{label}')
    assert not any(n.get('resource-id') in ['android:id/aerr_close','android:id/aerr_wait'] for n in tree.iter('node'))
    assert not any("isn't responding" in n.get('text','') for n in tree.iter('node'))
    return tree
def tree_only():
    state=adb('shell','dumpsys','activity','activities').stdout
    resumed=[line for line in state.splitlines() if b'topResumedActivity' in line or b'mResumedActivity' in line]
    if any(b'GrantPermissionsActivity' in line for line in resumed):
        text=state.decode('utf-8',errors='replace')
        assert any('GrantPermissionsActivity' in block and 'launchedFromPackage='+PACKAGE in block for block in re.split(r'\* Hist #',text))
        rawPermission=adb('exec-out','uiautomator','dump','/dev/tty').stdout
        a,b=rawPermission.find(b'<?xml'),rawPermission.find(b'</hierarchy>')
        permission=ET.fromstring(rawPermission[a:b+len(b'</hierarchy>')])
        assert any('notifications?' in n.get('text','') and 'KPKN' in n.get('text','') for n in permission.iter('node'))
        deny=[n for n in permission.iter('node') if n.get('resource-id')=='com.android.permissioncontroller:id/permission_deny_button']
        assert len(deny)==1
        tap(deny[0]); report['steps'].append(dict(operation='DENY_OWNED_NOTIFICATION_PERMISSION'))
        time.sleep(2)
        state=adb('shell','dumpsys','activity','activities').stdout
    assert any(COMPONENT.encode() in line and (b'topResumedActivity' in line or b'mResumedActivity' in line) for line in state.splitlines())
    for attempt in range(3):
        dumped=adb('exec-out','uiautomator','dump','/dev/tty')
        raw=dumped.stdout
        start,end=raw.find(b'<?xml'),raw.find(b'</hierarchy>')
        if dumped.returncode==0 and start>=0 and end>=0:
            return ET.fromstring(raw[start:end+len(b'</hierarchy>')])
        # Profile recreation can briefly leave no accessibility root. Preserve that evidence.
        (OUT/f'tree-unavailable-{time.time_ns()}.txt').write_bytes(raw+dumped.stderr)
        time.sleep(2)
        state=adb('shell','dumpsys','activity','activities').stdout
        assert any(COMPONENT.encode() in line and (b'topResumedActivity' in line or b'mResumedActivity' in line) for line in state.splitlines()), 'Owned app left foreground during retry'
    raise AssertionError('No accessibility root after three bounded reads')
def tap(node):
    left,top,right,bottom=map(int,re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.get('bounds')).groups())
    assert right>left and bottom>top
    assert adb('shell','input','tap',str((left+right)//2),str((top+bottom)//2)).returncode==0
    time.sleep(1)
def label_node(tree,label):
    matches=[n for n in tree.iter('node') if n.get('text')==label and n.get('enabled')=='true']
    assert len(matches)==1, 'Expected one enabled observed label: '+label
    return matches[0]
def click(label): tap(label_node(tree_only(),label))
def open_account():
    assert adb('shell','am','start','-W','-a','android.intent.action.VIEW','-d','kpkn://account','-n',COMPONENT).returncode==0
    time.sleep(3)
def identity():
    response=adb('exec-out','run-as',PACKAGE,'cat','files/profile-registry.json')
    assert response.returncode==0 and len(response.stdout)<=2048
    return json.loads(response.stdout)
def gate_if_needed():
    tree=tree_only()
    target=[n for n in tree.iter('node') if n.get('content-desc')=='I confirm that I am 18 or older.' and n.get('checkable')=='true']
    if target:
        assert len(target)==1
        if target[0].get('checked')!='true': tap(target[0])
        click('Continue'); time.sleep(3); open_account()
def fill(email,password):
    tree=tree_only()
    fields=[n for n in tree.iter('node') if n.get('class')=='android.widget.EditText' and n.get('enabled')=='true']
    assert len(fields)==2 and fields[0].get('password')=='false' and fields[1].get('password')=='true'
    assert re.fullmatch(r'[A-Za-z0-9@._-]{1,120}',email) and re.fullmatch(r'[A-Za-z0-9]{8,80}',password)
    tap(fields[0]); assert adb('shell','input','text',email).returncode==0
    adb('shell','input','keyevent','4')
    tree=tree_only(); field=next(n for n in tree.iter('node') if n.get('class')=='android.widget.EditText' and n.get('password')=='true')
    tap(field); assert adb('shell','input','text',password).returncode==0
    adb('shell','input','keyevent','4')
def await_signed(email):
    deadline=time.monotonic()+90
    while time.monotonic()<deadline:
        gate_if_needed()
        tree=tree_only()
        if any(n.get('text')==email and n.get('class')!='android.widget.EditText' for n in tree.iter('node')) and any(n.get('text')=='Current plan: FREE' for n in tree.iter('node')):
            uid=identity().get('uid')
            assert isinstance(uid,str) and uid
            return uid
        if any(n.get('text')=='The operation could not be completed. Retry safely.' for n in tree.iter('node')):
            screen('operation-failed'); raise AssertionError('Account action error')
        time.sleep(2)
    screen('sign-in-timeout'); raise AssertionError('Account action did not reach signed-in identity')
def find_scroll(label):
    for attempt in range(8):
        tree=tree_only()
        matches=[n for n in tree.iter('node') if n.get('text')==label and n.get('enabled')=='true']
        if len(matches)==1: return matches[0]
        containers=[n for n in tree.iter('node') if n.get('scrollable')=='true']
        assert len(containers)==1
        left,top,right,bottom=map(int,re.findall(r'\d+',containers[0].get('bounds')))
        x=(left+right)//2; height=bottom-top
        adb('shell','input','swipe',str(x),str(top+height*3//4),str(x),str(top+height//4),'450'); time.sleep(1)
    raise AssertionError('Observed action not found: '+label)
def signout():
    tap(find_scroll('Sign out'))
    deadline=time.monotonic()+90
    while time.monotonic()<deadline:
        if identity().get('uid') is None:
            open_account(); gate_if_needed()
            tree=tree_only()
            if any(n.get('text')=='Create account' for n in tree.iter('node')):
                screen('signed-out'); return
        time.sleep(2)
    screen('signout-timeout')
    raise AssertionError('Guest account screen did not become ready after signed-out registry')
try:
    open_account(); gate_if_needed()
    assert identity().get('uid') is None, 'Existing signed-in identity must be preserved'
    screen('initial-guest')
    accounts={}
    for role in ['a','b']:
        email='qa-native-'+role+'-'+ticket[:12]+'@example.test'
        password='FixtureQA123'+ticket[:12]
        fill(email,password); click('I confirm that I am 18 or older'); click('Create account')
        uid=await_signed(email); accounts[role]=dict(email=email,uid=uid)
        screen('signed-in-'+role)
        report['steps'].append(dict(role=role,operation='SIGN_UP',uid=uid,selectedUidMatches=True))
        signout()
    assert accounts['a']['uid']!=accounts['b']['uid']
    fill(accounts['a']['email'],'FixtureQA123'+ticket[:12]); click('Sign in')
    assert await_signed(accounts['a']['email'])==accounts['a']['uid']
    screen('return-to-a')
    report['steps'].append(dict(role='a',operation='SIGN_IN_AGAIN',sameUid=True))
    signout()
    report.update(state='PASS',accounts=accounts,api=adb('shell','getprop','ro.build.version.sdk').stdout.decode().strip(),finalState='FRESH_GUEST')
except BaseException as error:
    report.update(state='FAIL_OR_INCOMPLETE',errorType=type(error).__name__,error=str(error))
    raise
finally:
    (OUT/'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    done.set(); worker.join(timeout=2)
    if owner.exists() and ticket in owner.read_text(encoding='utf-8'): owner.unlink(); LOCK.rmdir()
    print(json.dumps({k:v for k,v in report.items() if k!='accounts'}),flush=True)
