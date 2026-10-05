from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

from backend.exercises_catalog_v2 import (
    CatalogV2Error,
    DEFAULT_RUNTIME_ASSET,
    RETIRED_FIELD_PATHS,
    ExerciseSelectionV2,
    catalog_hash,
    find_retired_fields,
    load_catalog,
    resolve_selection,
    validate_runtime_catalog,
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
        self.assertEqual(runtime["catalogRevision"], "v2-approved-2026-09-29-a")
        definition = runtime["families"][0]["definitions"][0]
        configuration = definition["configurations"][0]
        self.assertTrue(configuration["profile"]["automationEligible"])
        rich = configuration["profile"]["richMetadata"]
        self.assertEqual(
            rich["replacement"]["preservesIntent"],
            [
                f"{configuration['profile']['movementPatternId']}:{muscle_id}"
                for muscle_id in configuration["profile"]["primaryMuscles"]
            ],
        )

    def test_runtime_payload_has_no_retired_fields_and_rejects_their_return(self) -> None:
        compiled = json.loads(DEFAULT_RUNTIME_ASSET.read_text(encoding="utf-8"))
        self.assertEqual(find_retired_fields(compiled), [])
        validate_runtime_catalog(compiled)

        def first_configuration(payload: dict) -> dict:
            return payload["families"][0]["definitions"][0]["configurations"][0]

        injections = {
            "family.description": lambda p: p["families"][0].__setitem__("description", "x"),
            "family.evidence.rationale": lambda p: p["families"][0]["evidence"].__setitem__("rationale", "x"),
            "definition.evidence.rationale": lambda p: p["families"][0]["definitions"][0]["evidence"].__setitem__("rationale", "x"),
            "configuration.evidence.rationale": lambda p: first_configuration(p)["evidence"].__setitem__("rationale", "x"),
            "profile.benefits": lambda p: first_configuration(p)["profile"].__setitem__("benefits", []),
            "profile.techniqueSummary": lambda p: first_configuration(p)["profile"].__setitem__("techniqueSummary", "x"),
            "profile.variantRationale": lambda p: first_configuration(p)["profile"].__setitem__("variantRationale", "x"),
            "profile.commonMistakes": lambda p: first_configuration(p)["profile"].__setitem__("commonMistakes", []),
            "profile.muscleNotes": lambda p: first_configuration(p)["profile"].__setitem__("muscleNotes", []),
            "profile.jointInvolvement[].note": lambda p: first_configuration(p)["profile"]["jointInvolvement"][0].__setitem__("note", "x"),
            "richMetadata.editorial": lambda p: first_configuration(p)["profile"]["richMetadata"].__setitem__("editorial", {}),
            "richMetadata.coaching": lambda p: first_configuration(p)["profile"]["richMetadata"].__setitem__("coaching", {}),
            "richMetadata.safety": lambda p: first_configuration(p)["profile"]["richMetadata"].__setitem__("safety", {}),
            "richMetadata.anatomy.targetRegions": lambda p: first_configuration(p)["profile"]["richMetadata"]["anatomy"].__setitem__("targetRegions", []),
            "richMetadata.anatomy.muscleLengthBias": lambda p: first_configuration(p)["profile"]["richMetadata"]["anatomy"].__setitem__("muscleLengthBias", "x"),
            "richMetadata.anatomy.stabilizationDemand": lambda p: first_configuration(p)["profile"]["richMetadata"]["anatomy"].__setitem__("stabilizationDemand", "x"),
            "richMetadata.anatomy.jointInvolvement[].note": lambda p: first_configuration(p)["profile"]["richMetadata"]["anatomy"]["jointInvolvement"][0].__setitem__("note", "x"),
            "richMetadata.biomechanics.rangeOfMotion": lambda p: first_configuration(p)["profile"]["richMetadata"]["biomechanics"].__setitem__("rangeOfMotion", "x"),
            "richMetadata.biomechanics.relevantTendons": lambda p: first_configuration(p)["profile"]["richMetadata"]["biomechanics"].__setitem__("relevantTendons", []),
            "richMetadata.programming.objectives": lambda p: first_configuration(p)["profile"]["richMetadata"]["programming"].__setitem__("objectives", []),
            "richMetadata.programming.suitableRepRanges": lambda p: first_configuration(p)["profile"]["richMetadata"]["programming"].__setitem__("suitableRepRanges", []),
            "richMetadata.programming.recoveryCost": lambda p: first_configuration(p)["profile"]["richMetadata"]["programming"].__setitem__("recoveryCost", "x"),
            "richMetadata.programming.setupTransitionCost": lambda p: first_configuration(p)["profile"]["richMetadata"]["programming"].__setitem__("setupTransitionCost", "x"),
            "richMetadata.programming.splitSuitability": lambda p: first_configuration(p)["profile"]["richMetadata"]["programming"].__setitem__("splitSuitability", []),
        }
        # Every retired path must be exercised: adding one without a test is a gap.
        self.assertEqual(set(injections), set(RETIRED_FIELD_PATHS))
        for path, inject in injections.items():
            with self.subTest(path=path):
                tampered = json.loads(json.dumps(compiled))
                inject(tampered)
                self.assertEqual(len(find_retired_fields(tampered)), 1)
                with self.assertRaisesRegex(CatalogV2Error, "retired_field_present:"):
                    validate_runtime_catalog(tampered)

    def test_derived_anatomy_mirrors_cannot_drift(self) -> None:
        compiled = json.loads(DEFAULT_RUNTIME_ASSET.read_text(encoding="utf-8"))

        stale_intent = json.loads(json.dumps(compiled))
        first = stale_intent["families"][0]["definitions"][0]["configurations"][0]["profile"]
        first["richMetadata"]["replacement"]["preservesIntent"] = ["Conserva el patrón y el objetivo equivocado."]
        with self.assertRaisesRegex(CatalogV2Error, "rich_replacement_intent_not_derived"):
            validate_runtime_catalog(stale_intent)

        stale_actions = json.loads(json.dumps(compiled))
        first = stale_actions["families"][0]["definitions"][0]["configurations"][0]["profile"]
        first["richMetadata"]["anatomy"]["jointActions"] = ["Acción que ninguna articulación declara"]
        with self.assertRaisesRegex(CatalogV2Error, "rich_anatomy_joint_actions_not_derived"):
            validate_runtime_catalog(stale_actions)

    def test_editorial_android_ios_artifacts_have_one_hash_and_revision(self) -> None:
        self.assertEqual(
            verify_shared_catalog_artifacts(),
            "c67eeb8ff6a8e68988fd3e0396fe8601920d3cdec7a05973fa95bf6085eb7566",
        )

    def test_retired_decline_variants_are_absent_from_the_shared_catalog(self) -> None:
        configuration_ids = {
            configuration["id"]
            for family in self.catalog["families"]
            for definition in family["definitions"]
            for configuration in definition["configurations"]
        }
        self.assertTrue(
            {
                "decline_bench_press__machine",
                "decline_bench_press__cable",
                "decline_chest_fly__machine",
                "decline_chest_fly__cable",
            }.isdisjoint(configuration_ids),
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
        # The definition text tells the shared exercise and never marries one implement
        # (AUTHORING_FICHA.md 3.1); the cable is named by its own configuration.
        cable = next(
            configuration
            for configuration in definition["configurations"]
            if configuration["selectedOptions"]["implement"] == "cable"
        )
        self.assertIn("polea", cable["profile"]["description"].lower())

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
        self.assertEqual(len(definitions["romanian_sumo_deadlift"]["configurations"]), 4)
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
        self.assertEqual(definitions["romanian_sumo_deadlift"]["optionAxes"], ["implement"])
        self.assertNotIn("deadlift", definitions)
        self.assertNotIn("leg_curl", definitions)
        self.assertNotIn("lateral_raise", definitions)
        self.assertNotIn("biceps_curl", definitions)
        self.assertNotIn("hams_curl_femoral_sentado_unilateral_maquina", definitions)
        self.assertNotIn("tren_superior_flexiones_clasicas", definitions)
        self.assertNotIn("triceps_press_california_barra_recta", definitions)

    def test_retired_sumo_rdl_has_only_implement_chips_and_bilateral_variants(self) -> None:
        sumo = next(
            definition
            for family in self.catalog["families"]
            for definition in family["definitions"]
            if definition["id"] == "romanian_sumo_deadlift"
        )
        implements = {"barbell", "smith_machine", "dumbbells", "hex_bar"}
        self.assertEqual(sumo["optionAxes"], ["implement"])
        self.assertEqual(sumo["defaultConfigurationId"], "romanian_sumo_deadlift__bilateral__barbell")
        self.assertEqual({configuration["id"] for configuration in sumo["configurations"]}, {
            f"romanian_sumo_deadlift__bilateral__{implement}" for implement in implements
        })
        for configuration in sumo["configurations"]:
            self.assertEqual(set(configuration["selectedOptions"]), {"implement"})
            self.assertEqual(configuration["profile"]["laterality"], "BILATERAL")
            self.assertEqual(set(configuration["profile"]["richMetadata"]["display"]["selectedOptions"]), {"implement"})


if __name__ == "__main__":
    unittest.main()
