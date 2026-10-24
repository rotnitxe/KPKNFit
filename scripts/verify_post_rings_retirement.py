#!/usr/bin/env python3
"""Product gate. Legacy import boundaries are explicit; exercise rings/anillas are not matched."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ['android-native/app/src/main', 'ios-native/KPKNFit/KPKNFit', 'backend']
ALLOW = {
    'android-native/app/src/main/java/com/example/kpkn/data/compatibility/LegacyTrainingImport.kt',
    'android-native/app/src/main/java/com/example/kpkn/data/compatibility/LegacyTrainingBackup.kt',
    'backend/routers/retired.py',
}
PATTERN = re.compile(r'\b(?:Auge(?:Recovery|Fatigue|Ttc|Adaptive|MuscleCapacity)Engine|MuscularSessionImpactEngine|PerformanceTauLearner|PerformanceImpliedBattery|SystemicLoadMonitor|AxialLoadMonitor|LoadAdvisoryEngine|RINGS_ONLY|WelcomeRingsScene|calculate_acwr|calculate_global_batteries)\b')

def scan():
    failures=[]
    for source in SOURCES:
        for path in sorted((ROOT/source).rglob('*')):
            if path.suffix not in {'.kt','.swift','.py'} or '__pycache__' in path.parts: continue
            relative=path.relative_to(ROOT).as_posix()
            if relative in ALLOW or 'tests' in path.parts: continue
            for number,line in enumerate(path.read_text().splitlines(),1):
                if PATTERN.search(line) and not line.lstrip().startswith(('//','*','#')):
                    failures.append(f'{relative}:{number}: {line.strip()}')
    return failures

if __name__ == '__main__':
    failures=scan()
    for failure in failures: print(failure)
    print(f'{len(failures)} active retirement violations')
    sys.exit(bool(failures))
