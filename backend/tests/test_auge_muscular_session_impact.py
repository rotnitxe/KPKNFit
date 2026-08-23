from __future__ import annotations

import unittest

from engines.fatigue_engine import calculate_completed_muscular_impact_v2
from models.common import (
    CompletedExercise,
    CompletedSet,
    ExerciseMuscleInfo,
    InvolvedMuscle,
    MuscleRole,
)


class AugeMuscularImpactV2Test(unittest.TestCase):
    def setUp(self) -> None:
        self.exercise = ExerciseMuscleInfo(
            id="bench",
            name="Press banca",
            involvedMuscles=[
                InvolvedMuscle(muscle="Pectorales", role=MuscleRole.primary, activation=1.0),
                InvolvedMuscle(muscle="Tríceps", role=MuscleRole.secondary, activation=0.65),
            ],
            efc=3.8,
            ssc=0.3,
            cnc=3.8,
        )

    def _impact(self, rpe: float):
        return calculate_completed_muscular_impact_v2(
            [CompletedExercise(
                exerciseDbId="bench",
                exerciseName="Press banca",
                sets=[CompletedSet(completedReps=8, completedRPE=rpe, weight=80)],
            )],
            [self.exercise],
            "2026-08-23T17:25:00-04:00",
        )

    def test_direct_and_indirect_muscles_are_attributed_without_average(self) -> None:
        impact = self._impact(9.0)
        self.assertIn("Pectorales", impact.perMuscle)
        self.assertIn("Tríceps", impact.perMuscle)
        self.assertGreater(impact.perMuscle["Pectorales"].directStressUnits, 0)
        self.assertGreater(impact.perMuscle["Tríceps"].indirectStressUnits, 0)
        self.assertNotEqual(impact.perMuscle["Pectorales"].immediateDrainPct, impact.perMuscle["Tríceps"].immediateDrainPct)

    def test_harder_set_is_monotonic(self) -> None:
        self.assertGreaterEqual(
            self._impact(10.0).perMuscle["Pectorales"].immediateDrainPct,
            self._impact(8.0).perMuscle["Pectorales"].immediateDrainPct,
        )


if __name__ == "__main__":
    unittest.main()
