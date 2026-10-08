"""Skipped real voice replay is a missing launch gate, never a passing corpus."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--reports', default='android-native/app/build/test-results/testDebugUnitTest')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    reports, output = Path(args.reports).resolve(), Path(args.output).resolve()
    if not reports.is_relative_to(ROOT) or not output.is_relative_to(ROOT):
        raise SystemExit('Replay gate paths must stay in this checkout')
    cases = []
    for path in reports.glob('TEST-*WorkoutVoiceReplayTest.xml'):
        cases.extend(ET.parse(path).getroot().findall('testcase'))
    result = {
        'status': 'PASS', 'tests': len(cases),
        'skipped': sum(case.find('skipped') is not None for case in cases),
        'failed': sum(case.find('failure') is not None or case.find('error') is not None for case in cases),
        'evidenceLevel': 'JUNIT_EXECUTION_ONLY',
        'limits': 'Execution gate only; corpus consent, provenance and coverage require separate evidence.',
    }
    if result['tests'] < 2 or result['skipped'] or result['failed']:
        result['status'] = 'INCOMPLETE'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result))
    raise SystemExit(0 if result['status'] == 'PASS' else 1)


if __name__ == '__main__':
    main()
