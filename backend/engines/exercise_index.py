"""O(1) lookup index for the exercise database."""
from __future__ import annotations
from models.common import ExerciseMuscleInfo


class ExerciseIndex:
    __slots__ = ("by_id", "by_name")

    def __init__(self, exercise_list: list[ExerciseMuscleInfo]):
        self.by_id: dict[str, ExerciseMuscleInfo] = {}
        self.by_name: dict[str, ExerciseMuscleInfo] = {}
        for ex in exercise_list:
            self.by_id[ex.id] = ex
            self.by_name[ex.name.lower()] = ex

    def find(
        self,
        id_or_db_id: str | None = None,
        name: str | None = None,
    ) -> ExerciseMuscleInfo | None:
        if id_or_db_id:
            hit = self.by_id.get(id_or_db_id)
            if hit:
                return hit
        if name:
            return self.by_name.get(name.lower())
        return None
