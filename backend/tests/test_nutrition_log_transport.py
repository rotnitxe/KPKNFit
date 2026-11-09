from models.common import NutritionLog


def test_legacy_nutrition_log_food_defaults_preparation_fields_to_none():
    legacy = NutritionLog.model_validate({
        "id": "legacy",
        "date": "2026-11-06T12:00:00.000Z",
        "foods": [{"id": "food", "foodName": "Papas cocidas", "calories": 174, "fats": 0.2}],
    })

    assert legacy.foods[0].preparation is None
    assert legacy.foods[0].preparationOilLevel is None


def test_nutrition_log_transport_round_trips_preparation_without_calculating_nutrients():
    payload = {
        "id": "prepared",
        "date": "2026-11-06T12:00:00.000Z",
        "foods": [{
            "id": "food",
            "foodName": "Papas cocidas",
            "calories": 260,
            "protein": 3.8,
            "carbs": 40,
            "fats": 10,
            "preparation": "SAUTEED",
            "preparationOilLevel": "abundante",
        }],
    }

    food = NutritionLog.model_validate(payload).model_dump()["foods"][0]

    assert food["preparation"] == "SAUTEED"
    assert food["preparationOilLevel"] == "abundante"
    assert food["calories"] == 260
    assert food["fats"] == 10
