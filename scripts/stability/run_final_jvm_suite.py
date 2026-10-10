"""Run remaining disjoint JVM partitions; retain every failure/skip and keep full coverage honest."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'artifacts/stability-verification'
inventory=OUT/'junit-final-inventory.json'
data=json.loads(inventory.read_text(encoding='utf-8'))
renderOutput=OUT/'final-wizard-render-images'
assert not renderOutput.exists()
renderOutput.mkdir()
environment=dict(os.environ,KPKN_RENDER_DIR=str(renderOutput))
steps=[]
for group in ['data','domain-nutrition','domain-other','domain-training','other','screens','services']:
    count=data['groups'][group]['classes']
    for chunk in range((count+79)//80):
        steps.append(dict(group=group,chunk=chunk,out=f'final-jvm-{group}-{chunk:02d}',method=None))
for method in [item['name'] for item in data['groups']['heavy-generation']['tests'][0]['inheritedMethods']]:
    steps.append(dict(group='heavy-generation',chunk=0,out='final-jvm-heavy-'+method,method=method))
statePath=OUT/'jvm-final-suite-sequence.json'
assert not statePath.exists()
report=dict(state='RUNNING_PARTIAL_SUITE',inventorySha256=hashlib.sha256(inventory.read_bytes()).hexdigest(),
    partitions=[],requiredPreexisting=[],
    limits='All partition XMLs including seven heavy methods and real voice corpus required for full-suite PASS. Skips or failures remain omissions/FAIL; old interrupted full run retained.')
def save(): statePath.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
save()
for step in steps:
    folder=OUT/step['out']
    assert not folder.exists(), 'Preserve existing attempt; use a fresh sequence/explicit retry'
    command=[sys.executable,str(ROOT/'scripts/stability/run_jvm_partition.py'),
        '--inventory',inventory.name,'--group',step['group'],'--chunk',str(step['chunk']),'--out',step['out']]
    if step['method']: command+=['--method',step['method']]
    started=time.time()
    code=subprocess.call(command,cwd=ROOT,env=environment)
    assert (folder/'result.json').exists(), 'Runner failed before result; do not silently skip the selection'
    result=json.loads((folder/'result.json').read_text(encoding='utf-8'))
    report['partitions'].append(dict(**step,exitCode=code,state=result['state'],totals=result['totals'],elapsedSeconds=time.time()-started))
    save()
report['state']='PARTITIONS_FINISHED_REQUIRES_COVERAGE_AUDIT'
save()
print(json.dumps(dict(state=report['state'],partitions=len(report['partitions']))),flush=True)
