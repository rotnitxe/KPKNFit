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
