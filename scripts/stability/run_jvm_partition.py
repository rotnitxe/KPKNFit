"""Run exact compiled-class groups and preserve every XML/failure/skip; no source/test mutation."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'artifacts/stability-verification'
parser=argparse.ArgumentParser()
parser.add_argument('--inventory',default='junit-partition-inventory.json')
parser.add_argument('--group',required=True)
parser.add_argument('--class-name',help='One exact inventoried class for a separately recorded retry')
parser.add_argument('--chunk',type=int,default=0)
parser.add_argument('--chunk-size',type=int,default=80)
parser.add_argument('--out',required=True)
parser.add_argument('--method',help='One exact inventoried method; complete class requires every disjoint method')
args=parser.parse_args()
assert 1<=args.chunk_size<=80 and args.chunk>=0
inventoryPath=OUT/args.inventory
assert inventoryPath.resolve().is_relative_to(OUT)
inventory=json.loads(inventoryPath.read_text(encoding='utf-8'))
tests=inventory['groups'][args.group]['tests']
start=args.chunk*args.chunk_size
selected=tests[start:start+args.chunk_size]
assert selected
if args.class_name:
    selected=[item for item in tests if item['name']==args.class_name]
    assert len(selected)==1
names=[item['name'] for item in selected]
patterns=names
if args.method:
    assert args.group=='heavy-generation' and len(selected)==1
    assert re.fullmatch(r'[A-Za-z0-9_]+',args.method)
    assert args.method in {method['name'] for method in selected[0]['inheritedMethods']}
    patterns=[names[0]+'.'+args.method]
assert all(re.fullmatch(r'com\.example\.kpkn\.[A-Za-z0-9_.$]+',name) for name in names)
out=OUT/args.out
assert out.resolve().is_relative_to(OUT) and not out.exists()
out.mkdir(parents=True)
manifest=dict(state='RUNNING',group=args.group,chunk=args.chunk,chunkSize=args.chunk_size,
    singleClassRetry=args.class_name,selectedClasses=names,selectedPatterns=patterns,selectedMethod=args.method,inventorySha256=hashlib.sha256(inventoryPath.read_bytes()).hexdigest(),
    limits='One partition only. Full suite needs every disjoint group/chunk including heavy-generation; runtime annotations/SDK variants may multiply testcase counts. Omissions/failures never promoted to PASS.')
(out/'selection.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
command=[r'C:\Program Files\Git\bin\bash.exe','/c/kw/tools/gradle_slot.sh',
         '/c/kw/msi/android-native','--console=plain','--max-workers=1',
         '-Dorg.gradle.jvmargs=-Xmx4g','-Pkotlin.compiler.execution.strategy=in-process','testDebugUnitTest']
for pattern in patterns: command+=['--tests',pattern]
started=time.time()
with (out/'gradle.log').open('wb') as log:
    process=subprocess.Popen(command,cwd=ROOT/'android-native',stdout=log,stderr=subprocess.STDOUT)
    code=process.wait()
totals=dict(tests=0,failures=0,errors=0,skipped=0)
files={}; emitted=[]
results=ROOT/'android-native/app/build/test-results/testDebugUnitTest'
for path in sorted(results.glob('TEST-*.xml')):
    # Old XML from an unstarted/resource-failed attempt must never stand in for this partition.
    if path.stat().st_mtime<started-1: continue
    element=ET.parse(path).getroot()
    for key in totals: totals[key]+=int(element.get(key,0))
    if args.method:
        assert all(case.get('name','').split('[')[0]==args.method for case in element.findall('testcase')), 'Unexpected method emitted'
    emitted.append(element.get('name'))
    target=out/path.name
    target.write_bytes(path.read_bytes())
    files[path.name]=hashlib.sha256(path.read_bytes()).hexdigest()
missing=sorted(set(names)-set(emitted))
manifest.update(state='PASS' if code==0 and totals['tests']>0 and not missing and totals['failures']==totals['errors']==0 else 'FAIL_OR_INCOMPLETE',
    exitCode=code,elapsedSeconds=time.time()-started,totals=totals,emittedClasses=emitted,
    missingClasses=missing,xmlHashes=files)
if totals['skipped']: manifest['state']='COMPLETED_WITH_OMISSIONS' if manifest['state']=='PASS' else manifest['state']
(out/'result.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(state=manifest['state'],group=args.group,chunk=args.chunk,**totals,missingClasses=missing)),flush=True)
sys.exit(0 if manifest['state']=='PASS' else 1)
