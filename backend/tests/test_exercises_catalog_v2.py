from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

from backend.exercises_catalog_v2 import (
    CatalogV2Error,
    ExerciseSelectionV2,
    catalog_hash,
    load_catalog,
    resolve_selection,
    verify_shared_catalog_artifacts,
)


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"


class ExerciseCatalogV2BackendTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.catalog = load_catalog(SOURCE, allow_draft=True)

    def test_hash_is_stable_and_selection_resolves_exact_configuration(self) -> None:
        payload = json.loads(SOURCE.read_text(encoding="utf-8"))
        self.assertEqual(catalog_hash(payload), catalog_hash(self.catalog))
        definition = self.catalog["families"][0]["definitions"][0]
        configuration = definition["configurations"][0]
        profile = resolve_selection(
            self.catalog,
            ExerciseSelectionV2(
                definitionId=definition["id"],
                configurationId=configuration["id"],
                catalogRevision=self.catalog["catalogRevision"],
            ),
        )
        self.assertEqual(profile["performanceProfileId"], configuration["profile"]["performanceProfileId"])

    def test_revision_mismatch_is_not_reinterpreted(self) -> None:
        definition = self.catalog["families"][0]["definitions"][0]
        configuration = definition["configurations"][0]
        with self.assertRaisesRegex(CatalogV2Error, "catalog_revision_mismatch"):
            resolve_selection(
                self.catalog,
                ExerciseSelectionV2(
                    definitionId=definition["id"],
                    configurationId=configuration["id"],
                    catalogRevision="other-revision",
                ),
            )

    def test_runtime_loader_accepts_only_the_approved_catalog_and_preserves_rich_metadata(self) -> None:
        runtime = load_catalog(SOURCE)
        self.assertEqual(runtime["catalogRevision"], "v2-approved-2026-08-12-a")
        definition = runtime["families"][0]["definitions"][0]
        configuration = definition["configurations"][0]
        self.assertTrue(configuration["profile"]["automationEligible"])
        self.assertEqual(
            configuration["profile"]["setupCues"],
            configuration["profile"]["richMetadata"]["coaching"]["setup"],
        )

    def test_editorial_android_ios_artifacts_have_one_hash_and_revision(self) -> None:
        self.assertEqual(
            verify_shared_catalog_artifacts(),
            "920eaa6e9b2da4abe91c7a7cc2e24894f1e37b68bf793ff753e7963f6afc8c67",
        )

    def test_reverse_fly_is_one_parent_with_explicit_machine_and_cable_configs(self) -> None:
        definition = next(
            definition
            for family in self.catalog["families"]
            for definition in family["definitions"]
            if definition["id"] == "reverse_pec_fly"
        )
        self.assertEqual(definition["canonicalName"], "Aperturas Inversas")
        self.assertEqual(definition["kind"], "PARENT")
        self.assertEqual(definition["optionAxes"], ["implement", "laterality"])
        self.assertEqual(
            {configuration["selectedOptions"]["implement"] for configuration in definition["configurations"]},
            {"machine", "cable", "dumbbells"},
        )
        self.assertIn("polea", definition["description"].lower())

    def test_descriptions_are_factual_and_configuration_specific(self) -> None:
        definitions = [
            definition
            for family in self.catalog["families"]
            for definition in family["definitions"]
        ]
        # v7.2: "se ejecuta" reflexivo/descriptivo permitido en descripciones de
        # definición (apertura editorial aprobada); imperativos siguen fuera.
        instructional = re.compile(r"(?i)\b(?:mantén|mantener|configura|adopta|controla|selecciona)\b")
        for definition in definitions:
            self.assertGreaterEqual(len(definition["description"].strip()), 40)
            self.assertIsNone(instructional.search(definition["description"]))
            descriptions = [configuration["profile"]["description"] for configuration in definition["configurations"]]
            for description in descriptions:
                self.assertGreaterEqual(len(description.strip()), 40)
                self.assertIsNone(instructional.search(description))
            if len(descriptions) > 1:
                self.assertEqual(len(descriptions), len(set(descriptions)), definition["id"])

    def test_grouping_audit_contract_is_materialized_in_source(self) -> None:
        definitions = {
            definition["id"]: definition
            for family in self.catalog["families"]
            for definition in family["definitions"]
        }
        self.assertEqual(definitions["romanian_deadlift"]["optionAxes"], ["implement", "stance"])
        self.assertEqual(definitions["good_morning"]["optionAxes"], ["implement", "laterality"])
        self.assertEqual(definitions["standing_lateral_raise"]["optionAxes"], ["implement"])
        self.assertEqual(definitions["seated_lateral_raise"]["optionAxes"], ["implement"])
        self.assertEqual(len(definitions["romanian_deadlift"]["configurations"]), 8)
        self.assertEqual(len(definitions["romanian_sumo_deadlift"]["configurations"]), 8)
        self.assertEqual(len(definitions["rear_delt_raise"]["configurations"]), 3)
        self.assertEqual(definitions["conventional_deadlift"]["optionAxes"], ["implement", "laterality"])
        self.assertEqual(definitions["sumo_deadlift"]["optionAxes"], ["implement"])
        self.assertEqual(definitions["stiff_leg_deadlift"]["optionAxes"], ["implement", "laterality"])
        self.assertEqual(definitions["seated_leg_curl"]["optionAxes"], ["implement", "laterality"])
        self.assertEqual(definitions["lying_leg_curl"]["optionAxes"], ["implement", "laterality"])
        self.assertEqual(definitions["standing_leg_curl"]["optionAxes"], ["implement", "laterality"])
        self.assertEqual(definitions["glute_ham_raise"]["optionAxes"], [])
        self.assertEqual(definitions["push_up"]["optionAxes"], ["support_angle"])
        self.assertEqual(definitions["lat_pulldown"]["optionAxes"], ["implement", "laterality"])
        self.assertEqual(definitions["pull_up"]["optionAxes"], ["grip_type", "grip_width"])
        self.assertEqual(definitions["crossbody_triceps_extension"]["optionAxes"], ["laterality"])
        self.assertEqual(definitions["calf_raise"]["optionAxes"], ["implement", "laterality"])
        self.assertEqual(definitions["chest_supported_row"]["optionAxes"], ["implement", "pulley_height", "grip_width"])
        self.assertEqual(len(definitions["chest_supported_row"]["configurations"]), 18)
        self.assertEqual(len(definitions["crossbody_triceps_extension"]["configurations"]), 2)
        self.assertEqual(definitions["tren_superior_cruce_poleas"]["optionAxes"], ["implement", "pulley_height"])
        self.assertEqual(definitions["romanian_sumo_deadlift"]["optionAxes"], ["implement", "stance"])
        self.assertNotIn("deadlift", definitions)
        self.assertNotIn("leg_curl", definitions)
        self.assertNotIn("lateral_raise", definitions)
        self.assertNotIn("biceps_curl", definitions)
        self.assertNotIn("hams_curl_femoral_sentado_unilateral_maquina", definitions)
        self.assertNotIn("tren_superior_flexiones_clasicas", definitions)
        self.assertNotIn("triceps_press_california_barra_recta", definitions)


if __name__ == "__main__":
    unittest.main()
