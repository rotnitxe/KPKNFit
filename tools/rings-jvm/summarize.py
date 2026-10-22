from pathlib import Path
import xml.etree.ElementTree as ET
files = list((Path(__file__).parent / 'build/test-results/test').glob('TEST-*.xml'))
if not files:
    raise SystemExit('ERROR: no hay XML de pruebas; no declarar exito')
totals = {k: 0 for k in ['tests', 'failures', 'errors', 'skipped']}
for path in files:
    root = ET.parse(path).getroot()
    for key in totals:
        totals[key] += int(root.attrib.get(key, 0))
print('clases=' + str(len(files)), **{})
print(' '.join(str(k) + '=' + str(v) for k, v in totals.items()))
for path in files:
    root = ET.parse(path).getroot()
    for case in root.findall('testcase'):
        for failure in list(case.findall('failure')) + list(case.findall('error')):
            print(case.attrib.get('classname'), case.attrib.get('name'), failure.attrib.get('message'))
if totals['tests'] == 0 or totals['failures'] or totals['errors']:
    raise SystemExit(1)
