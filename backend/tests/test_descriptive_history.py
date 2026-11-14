from datetime import datetime, timezone
from engines.analysis_engine import calculate_weekly_tonnage_comparison
from engines.volume_engine import calculate_fractional_volume, validate_session_volume
from models.common import WorkoutLog, Settings


def test_tonnage_uses_explicit_clock_preserves_units_and_deduplicates_activity():
    now = datetime(2026, 10, 24, 12, tzinfo=timezone.utc)
    def log(identity, date, sets):
        return WorkoutLog(id=identity, date=date, completedExercises=[{'exerciseId':'squat','useBodyweight': True,'sets':sets}])
    recorded = log('a','2026-10-22T10:00:00Z',[{'weight':20,'completedReps':5},{'weight':10,'completedDuration':30}])
    history = [recorded, recorded, log('future','2026-10-25T10:00:00Z',[{'weight':100,'completedReps':10}]),
               log('previous','2026-10-15T10:00:00Z',[{'weight':20,'completedReps':2}])]
    result = calculate_weekly_tonnage_comparison(history,Settings(userVitals={'weight':80}),now)
    assert result == {'current':100,'previous':40}


def test_unknown_muscle_roles_are_not_invented_and_counts_have_no_universal_ceiling():
    assert calculate_fractional_volume([{'muscleRole':'unknown','sets':100}]) == 0
    assert validate_session_volume(30,'Cuádriceps')['isValid'] is True
    assert validate_session_volume(-1,'Cuádriceps')['isValid'] is False


def test_conflicting_source_identity_is_excluded_instead_of_first_wins():
    now = datetime(2026, 10, 24, 12, tzinfo=timezone.utc)
    a=WorkoutLog(id='conflict',date='2026-10-22',completedExercises=[{'sets':[{'weight':20,'completedReps':5}]}])
    b=a.model_copy(update={'notes':'Different source content'})
    assert calculate_weekly_tonnage_comparison([a,b],Settings(),now) == {'current':0,'previous':0}


def test_legacy_aggregate_is_used_only_when_a_log_has_no_exercises():
    now = datetime(2026, 10, 24, 12, tzinfo=timezone.utc)
    legacy_without_sets = WorkoutLog(
        id='legacy-empty', date='2026-10-22T10:00:00Z', totalVolume=120,
    )
    detailed_log_with_stale_aggregate = WorkoutLog(
        id='detailed', date='2026-10-22T11:00:00Z', totalVolume=999,
        completedExercises=[{
            'exerciseId': 'bench',
            'sets': [{'weight': 10, 'reps': 5, 'loadMode': 'LOAD'}],
        }],
    )

    result = calculate_weekly_tonnage_comparison(
        [legacy_without_sets, detailed_log_with_stale_aggregate], Settings(), now,
    )

    assert result == {'current': 170, 'previous': 0}


def test_tonnage_respects_load_modes_and_excludes_synthetic_body_mass():
    now = datetime(2026, 10, 24, 12, tzinfo=timezone.utc)
    bw_with_synthetic = WorkoutLog(
        id='bw',
        date='2026-10-22T10:00:00Z',
        completedExercises=[{
            'exerciseId': 'pushup',
            'sets': [
                {
                    'weight': 80,
                    'reps': 12,
                    'loadMode': 'BODYWEIGHT',
                    'dropSets': [{'weight': 10, 'reps': 4}],
                    'restPauses': [{'restTime': 15, 'reps': 3}],
                },
                {
                    'weight': 35,
                    'reps': 8,
                    'loadMode': 'ASSISTED',
                    'recordedPayloadV3': {'loadInputMode': 'ASSISTED', 'assistedLoad': 35},
                },
            ],
        }],
    )
    lastre_recovered = WorkoutLog(
        id='lastre-ok',
        date='2026-10-22T11:00:00Z',
        completedExercises=[{
            'exerciseId': 'pullup',
            'sets': [
                {
                    'weight': 100,
                    'reps': 6,
                    'loadMode': 'LASTRE',
                    'recordedPayloadV3': {
                        'loadInputMode': 'LASTRE',
                        'externalLoad': 20,
                        'bodyWeightSnapshot': 80,
                    },
                    'dropSets': [{'weight': 10, 'reps': 4}],
                    'restPauses': [{'restTime': 15, 'reps': 2}],
                },
                {
                    'weight': 90,
                    'reps': 5,
                    'loadMode': 'LOAD',
                    'isWarmup': True,
                },
                {
                    'weight': 100,
                    'reps': 5,
                    'loadMode': 'LOAD',
                    'skipped': True,
                },
            ],
        }],
    )
    lastre_unrecoverable = WorkoutLog(
        id='lastre-no-detail',
        date='2026-10-15T10:00:00Z',
        completedExercises=[{
            'exerciseId': 'dips',
            'sets': [
                {
                    'weight': 95,
                    'reps': 8,
                    'loadMode': 'LASTRE',
                    'recordedPayloadV3': {'loadInputMode': 'LASTRE', 'bodyWeightSnapshot': 80},
                    'restPauses': [{'restTime': 15, 'reps': 3}],
                    'dropSets': [{'weight': 12, 'reps': 5}],
                },
            ],
        }],
    )

    result = calculate_weekly_tonnage_comparison(
        [bw_with_synthetic, lastre_recovered, lastre_unrecoverable],
        Settings(userVitals={'weight': 80}),
        now,
    )
    # Current week: lastre_recovered main (20*6=120) + drop (10*4=40) + rp (20*2=40) = 200; warmup/skipped/bw/assisted excluded
    # Previous week: unrecoverable lastre drops only explicit drop weight (12*5=60), excluding synthetic 95
    assert result == {'current': 200, 'previous': 60}
