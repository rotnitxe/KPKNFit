"""Host synthetic geo driver for the prepared-profile GPS test; no personal target."""
from pathlib import Path
import argparse,hashlib,json,os,re,subprocess,sys,threading,time,uuid
root=Path(__file__).resolve().parents[2];sys.path.insert(0,str(root/'scripts/stability'))
from emulator_food_tests import LOCK,ADB,PACKAGE,COMPONENT
parser=argparse.ArgumentParser()
parser.add_argument('--out',required=True)
parser.add_argument('--serial',default='emulator-5558')
parser.add_argument('--avd',default='Kpkn_Gps_QA')
parser.add_argument('--avd-config',type=Path,required=True)
parser.add_argument('--mock-provider',action='store_true',help='Explicitly authorize synthetic Android test-provider fixes on this own emulator')
args=parser.parse_args()
assert args.mock_provider, 'Explicit --mock-provider required; no GNSS approval implied'
assert re.fullmatch(r'[a-z0-9-]{1,95}',args.out)
assert re.fullmatch(r'emulator-\d+',args.serial)
assert re.fullmatch(r'[A-Za-z0-9_-]+',args.avd)
assert args.avd_config.resolve().is_relative_to(root)
SERIAL=args.serial
def adb(*args,timeout=60):
    return subprocess.run([str(ADB),'-s',SERIAL,*args],capture_output=True,timeout=timeout,
        env=dict(os.environ,MSYS_NO_PATHCONV='1'))
out=root/'artifacts/stability-service-followup'/args.out
assert not out.exists();out.mkdir()
LOCK.mkdir();ticket=uuid.uuid4().hex
owner=LOCK/'owner.txt';owner.write_text(ticket,encoding='utf-8')
done=threading.Event();geo=[];added=[];runner=None;restore_gms=False;mock_provider=False;mock_op_changed=False
def beat():
    while not done.wait(10):os.utime(LOCK,None)
heartbeat=threading.Thread(target=beat,daemon=True);heartbeat.start()
report=dict(state='RUNNING',scope='SYNTHETIC_GPS_FGS_FILE_RESTORE_ON_PREPARED_PROFILE',
    fixSource='ANDROID_TEST_GPS_PROVIDER_VIA_LOCATION_SERVICE',
    limits='Actual Android/FusedLocation/FGS/file restore with explicitly mocked GPS provider fixes, one API36 device. No GNSS hardware, console geo delivery, UI workout/editor, permission revocation, approximate-only location, process restart or physical route.')
try:
    assert PACKAGE=='com.example.kpkn.stability' and adb('get-state').stdout.strip()==b'device'
    assert args.avd in adb('emu','avd','name').stdout.decode()
    avd_config=args.avd_config.resolve()
    assert re.search(r'(?m)^hw.gps\s*=\s*yes\s*$',avd_config.read_text())
    report['avdConfigSha256']=hashlib.sha256(avd_config.read_bytes()).hexdigest()
    assert adb('shell','cmd','location','is-location-enabled').stdout.strip()==b'true'
    disabled=adb('shell','pm','list','packages','-d','--user','0','com.google.android.gms').stdout.decode().splitlines()
    report['gmsInitiallyDisabled']='package:com.google.android.gms' in [s.strip() for s in disabled]
    if report['gmsInitiallyDisabled']:
        raise RuntimeError('Fresh GPS AVD must have its normal GMS runtime enabled; preserve existing state')
    current_location=adb('shell','dumpsys','location').stdout
    assert b'mock provider' not in current_location.lower(), 'Preserve existing mock provider'
    before_op=adb('shell','cmd','appops','get','com.android.shell','android:mock_location').stdout
    assert b'No operations.' in before_op and b'Default mode: deny' in before_op
    (out/'mock-op-before.txt').write_bytes(before_op)
    assert adb('shell','cmd','appops','set','com.android.shell','android:mock_location','allow').returncode==0
    mock_op_changed=True
    assert adb('shell','cmd','location','providers','add-test-provider','gps','--requiresSatellite','--supportsAltitude','--supportsSpeed','--supportsBearing','--powerRequirement','3').returncode==0
    mock_provider=True
    assert adb('shell','cmd','location','providers','set-test-provider-enabled','gps','true').returncode==0
    app=root/'android-native/app/build/outputs/apk/debug/app-debug.apk'
    test=root/'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    assert hashlib.sha256(app.read_bytes()).hexdigest()=='f765a31a97dfa4214208cf859425544118c9eb79eaf3dfc7dae47cc5d73685fe'
    for label,apk in [('app',app),('test',test)]:
        target=PACKAGE if label=='app' else PACKAGE+'.test'
        paths=adb('shell','pm','path',target).stdout.decode().splitlines()
        assert len(paths)==1 and paths[0].startswith('package:')
        installed=paths[0].removeprefix('package:').strip()
        assert re.fullmatch(r'/data/app/[A-Za-z0-9_~/=+.-]+/base\.apk',installed)
        result=adb('shell','sha256sum',installed,timeout=120)
        (out/(label+'-installed-hash.txt')).write_bytes(result.stdout+result.stderr)
        assert result.returncode==0 and result.stdout.decode().split()[0]==hashlib.sha256(apk.read_bytes()).hexdigest()
        report['installedApksReusedWithExactHash']=True
    report.update(appApkSha256=hashlib.sha256(app.read_bytes()).hexdigest(),testApkSha256=hashlib.sha256(test.read_bytes()).hexdigest())
    initial=adb('shell','dumpsys','package',PACKAGE).stdout.decode()
    for permission in ['android.permission.ACCESS_COARSE_LOCATION','android.permission.ACCESS_FINE_LOCATION']:
        assert permission in initial, 'Expected requested permission absent from installed package'
        match=re.search(re.escape(permission)+r': granted=(true|false)',initial)
        already_granted=bool(match and match.group(1)=='true')
        report.setdefault('permissionBefore',{})[permission]=already_granted
        if not already_granted:
            assert adb('shell','pm','grant',PACKAGE,permission).returncode==0;added.append(permission)
    # ActivityScenario owns the first Activity launch. A pre-opened MainActivity
    # left Instrumentation.startActivitySync waiting before GPS in attempt06.
    assert adb('shell','am','force-stop',PACKAGE).returncode==0
    started=adb('shell','date','+%m-%d %H:%M:%S.000').stdout.decode().strip()
    def fixes():
        n=0
        while not done.is_set():
            lat=f'{-33.45+n*0.000020:.6f}'
            response=adb('shell','cmd','location','providers','set-test-provider-location','gps','--location',lat+',-70.650000','--accuracy','5',timeout=10)
            geo.append(dict(index=n,latitude=lat,longitude='-70.650000',returnCode=response.returncode,
                response=(response.stdout+response.stderr).decode(errors='replace').strip()))
            n+=1
            if done.wait(2):break
    feed=threading.Thread(target=fixes,daemon=True);feed.start()
    with (out/'instrumentation.txt').open('wb') as log:
        runner=subprocess.Popen([str(ADB),'-s',SERIAL,'shell','am','instrument','-w','-r','-e','class',
            'com.example.kpkn.services.cardio.StableProfileGpsLifecycleTest',PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner'],
            stdout=log,stderr=subprocess.STDOUT)
        code=runner.wait(timeout=180)
    done.set();feed.join(timeout=12)
    raw=(out/'instrumentation.txt').read_text(encoding='utf-8',errors='replace')
    assert code==0 and re.search(r'OK \(1 test\)',raw),raw[-4000:]
    matches=re.findall(r'^INSTRUMENTATION_STATUS: kpkn_stable_gps=(\{[^\r\n]+\})\r*$',raw,re.M)
    assert len(matches)==1
    native=json.loads(matches[0]);assert native['state']=='PASS'
    assert all(row['returnCode']==0 for row in geo)
    adb('shell','am','start','-W','-n',COMPONENT);time.sleep(4)
    ui=adb('exec-out','uiautomator','dump','/dev/tty').stdout
    (out/'ui.xml').write_bytes(ui);(out/'main.png').write_bytes(adb('exec-out','screencap','-p').stdout)
    resumed=adb('shell','dumpsys','activity','activities').stdout
    resumed=b'\n'.join(s for s in resumed.splitlines() if b'topResumedActivity' in s or b'mResumedActivity' in s)
    (out/'activity.txt').write_bytes(resumed)
    assert COMPONENT.encode() in resumed and b'<hierarchy' in ui
    assert b'android:id/aerr_close' not in ui and b'android:id/aerr_wait' not in ui
    services=adb('shell','dumpsys','activity','services',PACKAGE).stdout
    (out/'services.txt').write_bytes(services)
    assert b'CardioGpsForegroundService' not in services
    report.update(state='PASS',native=native,cases=1,api=adb('shell','getprop','ro.build.version.sdk').stdout.decode().strip(),
        mainTopResumed=True,syntheticFixes=len(geo))
except BaseException as error:
    report.update(state='FAIL_OR_INCOMPLETE',errorType=type(error).__name__,error=str(error));raise
finally:
    done.set();heartbeat.join(timeout=2)
    if 'feed' in locals():feed.join(timeout=12)
    if runner and runner.poll() is None:
        adb('shell','am','force-stop',PACKAGE)
        try:runner.wait(timeout=10)
        except subprocess.TimeoutExpired:runner.terminate();runner.wait(timeout=5)
    report['permissionRestoration']={}
    for permission in reversed(added):report['permissionRestoration'][permission]=adb('shell','pm','revoke',PACKAGE,permission).returncode==0
    if restore_gms:
        report['gmsRestoredDisabled']=adb('shell','pm','disable-user','--user','0','com.google.android.gms').returncode==0
    if mock_provider:report['mockProviderRemoved']=adb('shell','cmd','location','providers','remove-test-provider','gps').returncode==0
    if mock_op_changed:
        report['mockOpRestoredDefault']=adb('shell','cmd','appops','set','com.android.shell','android:mock_location','default').returncode==0
    adb('shell','am','start','-W','-n',COMPONENT)
    (out/'synthetic-fixes.json').write_text(json.dumps(geo,indent=2)+'\n',encoding='utf-8')
    report['evidenceHashes']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.iterdir() if p.is_file()}
    (out/'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    if owner.exists() and owner.read_text(encoding='utf-8')==ticket:owner.unlink();LOCK.rmdir()
    print(json.dumps(report),flush=True)
