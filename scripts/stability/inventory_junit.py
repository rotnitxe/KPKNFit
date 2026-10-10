"""Inventory compiled JUnit4 annotations without loading tests or changing assertions."""
from pathlib import Path
from collections import defaultdict
import hashlib
import json
import struct

root=Path(__file__).resolve().parents[2]
out=root/'artifacts/stability-verification'
compiled=[root/'android-native/app/build/intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes',
          root/'android-native/app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes']
class Binary:
 def __init__(self,data): self.data=data; self.i=0
 def read(self,n): result=self.data[self.i:self.i+n]; assert len(result)==n; self.i+=n; return result
 def u1(self): return self.read(1)[0]
 def u2(self): return struct.unpack('>H',self.read(2))[0]
 def u4(self): return struct.unpack('>I',self.read(4))[0]
def parse(path):
 b=Binary(path.read_bytes()); assert b.u4()==0xcafebabe
 b.read(4); count=b.u2(); cp=[None]*count; index=1
 while index<count:
  tag=b.u1()
  if tag==1: cp[index]=b.read(b.u2()).decode('utf-8',errors='replace')
  elif tag in (7,8,16,19,20): cp[index]=(tag,b.u2())
  elif tag in (3,4,9,10,11,12,17,18): b.read(4)
  elif tag in (5,6): b.read(8); index+=1
  elif tag==15: b.read(3)
  else: raise ValueError((path,tag))
  index+=1
 flags=b.u2(); this=b.u2(); parent=b.u2()
 name=cp[cp[this][1]].replace('/','.')
 supername=cp[cp[parent][1]].replace('/','.') if parent else None
 b.read(b.u2()*2)
 def attributes():
  values=[]
  for _ in range(b.u2()): values.append((cp[b.u2()],b.read(b.u4())))
  return values
 def annotation_names(content):
  r=Binary(content); names=[]
  def value():
   tag=chr(r.u1())
   if tag in 'BCDFIJSZsc': r.read(2)
   elif tag=='e': r.read(4)
   elif tag=='@': annotation()
   elif tag=='[':
    for _ in range(r.u2()): value()
   else: raise ValueError(tag)
  def annotation():
   names.append(cp[r.u2()])
   for _ in range(r.u2()): r.read(2); value()
  for _ in range(r.u2()): annotation()
  assert r.i==len(content)
  return names
 for _ in range(b.u2()): b.read(6); attributes()
 methods=[]
 for _ in range(b.u2()):
  access=b.u2(); method=cp[b.u2()]; descriptor=cp[b.u2()]
  annotations=[]
  for attr,content in attributes():
   if attr in ('RuntimeVisibleAnnotations','RuntimeInvisibleAnnotations'): annotations+=annotation_names(content)
  if 'Lorg/junit/Test;' in annotations:
   methods.append(dict(name=method,descriptor=descriptor,ignored='Lorg/junit/Ignore;' in annotations))
 classAnnotations=[]
 for attr,content in attributes():
  if attr in ('RuntimeVisibleAnnotations','RuntimeInvisibleAnnotations'): classAnnotations+=annotation_names(content)
 assert b.i==len(b.data)
 return dict(name=name,superclass=supername,abstract=bool(flags&0x400),methods=methods,
   ignored='Lorg/junit/Ignore;' in classAnnotations,bytecodeSha256=hashlib.sha256(path.read_bytes()).hexdigest())

allclasses={}
for directory in compiled:
 if not directory.exists(): continue
 for path in directory.rglob('*.class'):
  item=parse(path)
  assert item['name'] not in allclasses or allclasses[item['name']]['bytecodeSha256']==item['bytecodeSha256']
  allclasses[item['name']]=item
def inherited(item,seen=None):
 seen=set() if seen is None else seen
 assert item['name'] not in seen
 seen.add(item['name'])
 own=item['methods']
 parent=allclasses.get(item['superclass'])
 return own+(inherited(parent,seen) if parent else [])
tests=[]
for item in allclasses.values():
 methods=inherited(item)
 if not item['abstract'] and methods:
  item['inheritedMethods']=methods
  tests.append(item)
groups=defaultdict(list)
for item in sorted(tests,key=lambda item:item['name']):
 name=item['name']
 if name.endswith('.PlanGenerationCoverageT006Test'): group='heavy-generation'
 elif '.domain.training.' in name: group='domain-training'
 elif '.domain.nutrition.' in name: group='domain-nutrition'
 elif '.domain.' in name: group='domain-other'
 elif '.data.' in name: group='data'
 elif '.screens.' in name: group='screens'
 elif '.services.' in name: group='services'
 else: group='other'
 groups[group].append(item)
manifest=dict(state='INVENTORY_ONLY_NOT_EXECUTION',baseline='bf007cf165f86399bdefa06513cc4744a52100bd',
 classes=len(tests),compiledClassesScanned=len(allclasses),annotatedMethods=sum(len(t['inheritedMethods']) for t in tests),
 groups={name:dict(classes=len(items),annotatedMethods=sum(len(t['inheritedMethods']) for t in items),tests=items) for name,items in sorted(groups.items())},
 limits='JUnit method inventory is not runtime testcase count: Robolectric SDK variants and parameterized runners multiply cases. Every group must emit XML; skipped/failed cases remain visible. Heavy method requires a separate complete execution, never counted as skipped/pass.')
target=out/'junit-final-inventory.json'
assert not target.exists()
target.write_text(json.dumps(manifest,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps(dict(classes=manifest['classes'],annotatedMethods=manifest['annotatedMethods'],groups={name:value['classes'] for name,value in manifest['groups'].items()})))
