from engines.volume_engine import (
    MUSCLE_ROLE_MULTIPLIERS,
    calculate_unified_muscle_volume,
    normalize_muscle_group,
)
from models.common import (
    Exercise,
    ExerciseMuscleInfo,
    ExerciseSet,
    InvolvedMuscle,
    MuscleRole,
    Session,
    SessionPart,
)


def test_normalize_muscle_group_pectoral_upper_adductors_neck_and_tibial():
    assert normalize_muscle_group("Pectoral porción alta") == "Pectorales"
    assert normalize_muscle_group("Pectoral porción baja") == "Pectorales"
    assert normalize_muscle_group("Pecho superior") == "Pectorales"
    assert normalize_muscle_group("Aductor mayor") == "Aductores"
    assert normalize_muscle_group("Pectíneo") == "Aductores"
    assert normalize_muscle_group("Cuello") == "Cuello"
    assert normalize_muscle_group("Flexores cervicales") == "Cuello"
    assert normalize_muscle_group("Tibial anterior") == "Pantorrillas"


def test_role_multipliers_zero_out_postural_stabilizer_and_neutralizer():
    assert MUSCLE_ROLE_MULTIPLIERS == {
        "primary": 1.0,
        "secondary": 0.5,
        "stabilizer": 0.0,
        "neutralizer": 0.0,
    }


def test_unified_volume_combines_loose_and_part_exercises_and_two_tier_stabilizers():
    db = [
        ExerciseMuscleInfo(
            id="squat",
            name="Sentadilla",
            involvedMuscles=[
                InvolvedMuscle(muscle="Cuádriceps", role=MuscleRole.primary),
                InvolvedMuscle(muscle="Glúteos", role=MuscleRole.secondary),
                InvolvedMuscle(
                    muscle="Erectores Espinales",
                    role=MuscleRole.stabilizer,
                    volumeContribution=0.3,
                ),
                InvolvedMuscle(
                    muscle="Core",
                    role=MuscleRole.stabilizer,
                    volumeContribution=0.0,
                ),
                InvolvedMuscle(muscle="Romboides", role=MuscleRole.neutralizer),
            ],
        ),
        ExerciseMuscleInfo(
            id="curl",
            name="Curl bíceps",
            involvedMuscles=[
                InvolvedMuscle(muscle="Bíceps", role=MuscleRole.primary),
                InvolvedMuscle(muscle="Antebrazo", role=MuscleRole.stabilizer),
            ],
        ),
    ]
    session = Session(
        id="s1",
        name="Mixta",
        exercises=[
            Exercise(
                id="ex-partless",
                name="Curl bíceps",
                exerciseDbId="curl",
                sets=[
                    ExerciseSet(id="c1", completedReps=10),
                    ExerciseSet(id="c2", completedReps=10, isEmptySlot=True),
                ],
            )
        ],
        parts=[
            SessionPart(
                id="p1",
                name="Principal",
                exercises=[
                    Exercise(
                        id="ex-part",
                        name="Sentadilla",
                        exerciseDbId="squat",
                        sets=[
                            ExerciseSet(id="q1", completedReps=8),
                            ExerciseSet(id="q2", completedReps=8),
                            ExerciseSet(id="q3", completedReps=8, isIneffective=True),
                        ],
                    )
                ],
            )
        ],
    )

    by_group = {
        entry["muscleGroup"]: entry["displayVolume"]
        for entry in calculate_unified_muscle_volume([session], db)
    }

    assert by_group["Cuádriceps"] == 2.0
    assert by_group["Glúteos"] == 1.0
    assert by_group["Erectores Espinales"] == 0.6
    assert by_group["Bíceps"] == 1.0
    assert "Core" not in by_group
    assert "Romboides" not in by_group
    assert "Antebrazo" not in by_group
