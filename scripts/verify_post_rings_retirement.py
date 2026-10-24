#!/usr/bin/env python3
"""Mechanical product gate, supplemented by semantic review; physical rings/anillas are preserved."""
from pathlib import Path
import hashlib
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ['android-native/app/src/main', 'android-native/app/src/debug', 'ios-native/KPKNFit/KPKNFit', 'backend']
# Isolated raw compatibility boundaries and 410 routes only. No directory-wide exclusions.
ALLOW = {
    'android-native/app/src/main/java/com/example/kpkn/data/compatibility/LegacyTrainingImport.kt',
    'android-native/app/src/main/java/com/example/kpkn/data/compatibility/LegacyTrainingBackup.kt',
    'backend/routers/retired.py',
}
PATTERN = re.compile(r'\b(?:Auge[A-Z]\w*|MuscularSessionImpactEngine|PerformanceTauLearner|PerformanceImpliedBattery|SystemicLoadMonitor|AxialLoadMonitor|LoadAdvisoryEngine|RINGS_ONLY|WelcomeRingsScene|WorkoutReadinessSheet|WorkoutReadinessBridge|ReadinessObserver|SetAdjustmentSuggestion|PostSessionPreview|PredictedDrain|RecoveryDashboard|GlobalBatteries|PredictionBiasProfile|calculate_acwr|calculate_global_batteries|exerciseReadinessMap|dailyReadiness|readinessScore|spinalScore|neuralBattery|spinalBattery|voiceFinalNeural|voiceFinalSpinal|finalNeuralBattery|finalSpinalBattery|finalMuscularBattery)\b|\b(?:WizardBlock|WizChatStage)\.RINGS\b|Tus RINGS|KPKN mide tu fatiga|Baterías de recuperación')
# Literal, reviewed legacy-key purge statements in existing iOS persistence files.
# Their SHA-256 identities prevent a whole-file compatibility exception.
COMPAT_LINES = {'ios-native/KPKNFit/KPKNFit/Data/Db/Entities.swift': {'e1efe73f3413684d101606a0914cdb70caddeb566b20a98a7e7a2cf05133c70d', '1b11196843fab7c2ca40257ba83bb4e02f6b38c6f0e8c049a780d6131ffebb05'}, 'ios-native/KPKNFit/KPKNFit/Data/Db/KpknDatabase.swift': {'2a743bf786f94a1fa1d00882fd242137ac0b2f94ef771a5432f17cdeb07f9598', '7496d3358046f1dd0c6a8e9f48ff1db122b19a0104c8b5ad50cb35ec2b1adab5'}}

def scan():
    failures=[]
    for source in SOURCES:
        for path in sorted((ROOT/source).rglob('*')):
            if path.suffix not in {'.kt','.swift','.py','.xml','.json','.txt','.md'} or '__pycache__' in path.parts: continue
            relative=path.relative_to(ROOT).as_posix()
            if relative in ALLOW or 'tests' in path.parts: continue
            for number,line in enumerate(path.read_text().splitlines(),1):
                stripped=line.strip()
                if stripped.startswith(('//','/*','*','#','<!--')): continue
                if PATTERN.search(line):
                    identity=hashlib.sha256(stripped.encode()).hexdigest()
                    if identity in COMPAT_LINES.get(relative,set()): continue
                    failures.append(f'{relative}:{number}: {stripped[:240]}')
    return failures

def self_test():
    for retired in ['AugeRecoveryEngine.compute()', 'WorkoutReadinessBridge.consume()', 'WizardBlock.RINGS', 'RINGS_ONLY', 'Tus RINGS', 'SetAdjustmentSuggestion', 'calculate_acwr']:
        assert PATTERN.search(retired), retired
    for independent in ['EquipmentTier.RINGS','rings_kit','Entrenamiento con anillas','hamstrings','SharedStrings','RingsArt','RINGS("Anillas o TRX")']:
        assert not PATTERN.search(independent), independent

if __name__ == '__main__':
    self_test()
    failures=scan()
    for failure in failures: print(failure)
    print(f'{len(failures)} active retirement violations; positive/physical-rings control checks passed')
    sys.exit(bool(failures))
