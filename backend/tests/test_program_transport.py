from __future__ import annotations

from engines.analysis_engine import calculate_average_volume_for_weeks
from models.common import ExerciseMuscleInfo, MuscleHierarchy, Program


def transport_fixture() -> dict:
    return {
        "id": "plan-transport-1",
        "name": "Atleta completo",
        "mode": "POWERBUILDING",
        "sourceProtocolId": "phat-verified",
        "planWarmupConfig": [{"targetReps": 8, "loadFraction": 0.4}],
        "sourceRecipe": {
            "id": "native:complete-athlete-v2",
            "contentVersion": 2,
            "provenance": {
                "planId": "native:complete-athlete-v2",
                "category": "KPKN",
                "sourceTitle": "Plan KPKN",
            },
            "weeks": [{"weekNumber": 1, "days": [{"id": "day-a", "slots": []}]}],
        },
        "planProvenance": {
            "planId": "native:complete-athlete-v2",
            "category": "KPKN",
            "sourceEdition": "r1",
            "slotChanges": [],
        },
        "exerciseLoadReferences": [{"exerciseId": "ex-1", "references": [{"state": "PENDING"}]}],
        "effectiveWeekRecipes": [{"weekOccurrence": 1, "cycleNumber": 1, "changes": []}],
        "manualSessionOverrides": [{"sessionId": "session-1", "weekId": "week-1", "scope": "SESSION"}],
        "runState": {"runId": "run-1", "pendingAction": {"type": "CONFIRM_AUTOREGULATION"}},
        "macrocycles": [
            {
                "id": "macro-1",
                "name": "Macrociclo",
                "blocks": [
                    {
                        "id": "block-1",
                        "name": "Bloque",
                        "goal": "ACCUMULATION",
                        "progressionScheme": "LINEAR_LOAD",
                        "materializationPending": True,
                        "materializationStatus": "USER_MODIFIED",
                        "sourceDefinitionId": "native:complete-athlete-v2",
                        "mesocycles": [
                            {
                                "id": "meso-1",
                                "name": "Mesociclo",
                                "customGoal": "Base",
                                "weeks": [
                                    {
                                        "id": "week-1",
                                        "name": "Semana 1",
                                        "executionKind": "TRAINING",
                                        "sessions": [
                                            {
                                                "id": "session-1",
                                                "name": "Día A",
                                                "dayOfWeek": 1,
                                                "requirement": "REQUIRED",
                                                "origin": "GENERATED_PLACEHOLDER",
                                                "cardioFirst": False,
                                                "persistedRuleDefaults": {"setCount": 3, "reps": 10},
                                                "parts": [
                                                    {
                                                        "id": "strength-part",
                                                        "name": "Fuerza",
                                                        "isCardioGroup": False,
                                                        "exercises": [
                                                            {
                                                                "id": "ex-1",
                                                                "name": "Press banca",
                                                                "exerciseDbId": "bench-press",
                                                                "catalogRevision": "v2-approved-2026-09-29-a",
                                                                "catalogConfigurationId": "bench_press__barbell",
                                                                "occurrenceId": "occ-1",
                                                                "recipeDayId": "day-a",
                                                                "recipeSlotId": "slot-bench",
                                                                "slotRole": "T1_MAIN",
                                                                "loadQuantityConvention": "TOTAL_EXTERNAL",
                                                                "loadReference": {"state": "PENDING", "kind": "EXERCISE_TM"},
                                                                "sets": [
                                                                    {
                                                                        "id": "set-1",
                                                                        "targetReps": 5,
                                                                        "targetRepsRange": {"min": 3, "max": 5},
                                                                        "targetRIR": 2,
                                                                        "restAfterSeconds": 180,
                                                                        "isTopSet": True,
                                                                        "loadBasis": "PERCENT_TM",
                                                                        "loadQuantityConvention": "TOTAL_EXTERNAL",
                                                                    }
                                                                ],
                                                            }
                                                        ],
                                                    },
                                                    {
                                                        "id": "cardio-part",
                                                        "name": "Cardio",
                                                        "isCardioGroup": True,
                                                        "targetDurationMinutes": 20,
                                                        "exercises": [
                                                            {
                                                                "id": "cardio-1",
                                                                "name": "Caminata",
                                                                "trainingMode": "TIME",
                                                                "cardioDetails": {"type": "WALK", "targetDurationSeconds": 1200},
                                                                "sets": [],
                                                            }
                                                        ],
                                                    },
                                                ],
                                                "exercises": [],
                                            }
                                        ],
                                    }
                                ],
                            }
                        ],
                    }
                ],
            }
        ],
    }


def test_program_decode_edit_encode_preserves_plan_recipe_provenance_and_overrides() -> None:
    program = Program.model_validate(transport_fixture())
    encoded = program.model_copy(update={"name": "Programa local editado"}).model_dump(mode="json")

    assert encoded["name"] == "Programa local editado"
    assert encoded["sourceRecipe"]["id"] == "native:complete-athlete-v2"
    assert encoded["sourceProtocolId"] == "phat-verified"
    assert encoded["planWarmupConfig"][0]["targetReps"] == 8
    assert encoded["runState"]["pendingAction"]["type"] == "CONFIRM_AUTOREGULATION"
    assert encoded["planProvenance"]["planId"] == "native:complete-athlete-v2"
    assert encoded["manualSessionOverrides"][0]["sessionId"] == "session-1"
    assert encoded["exerciseLoadReferences"][0]["references"][0]["state"] == "PENDING"
    assert encoded["effectiveWeekRecipes"][0]["weekOccurrence"] == 1

    block = encoded["macrocycles"][0]["blocks"][0]
    assert block["materializationPending"] is True
    assert block["sourceDefinitionId"] == "native:complete-athlete-v2"
    session = block["mesocycles"][0]["weeks"][0]["sessions"][0]
    assert session["requirement"] == "REQUIRED"
    assert session["persistedRuleDefaults"]["setCount"] == 3
    assert session["parts"][1]["isCardioGroup"] is True
    exercise = session["parts"][0]["exercises"][0]
    assert exercise["recipeSlotId"] == "slot-bench"
    assert exercise["loadQuantityConvention"] == "TOTAL_EXTERNAL"
    exercise_set = exercise["sets"][0]
    assert exercise_set["targetRepsRange"] == {"min": 3, "max": 5}
    assert exercise_set["restAfterSeconds"] == 180
    assert exercise_set["loadBasis"] == "PERCENT_TM"


def test_analytics_count_materialized_sessions_once_not_recipe_snapshots() -> None:
    program = Program.model_validate(transport_fixture())
    weeks = [week for macro in program.macrocycles for block in macro.blocks for meso in block.mesocycles for week in meso.weeks]
    exercise_list = [
        ExerciseMuscleInfo(
            id="bench-press",
            name="Press banca",
            involvedMuscles=[{"muscle": "pectorals", "role": "primary"}],
        )
    ]

    result = calculate_average_volume_for_weeks(weeks, exercise_list, MuscleHierarchy())

    assert len(weeks[0].sessions) == 1
    assert len(result) == 1
    assert result[0]["totalSets"] == 1
