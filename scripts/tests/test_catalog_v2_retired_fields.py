#!/usr/bin/env python3
"""Retired catalog fields: shared definition, backend copy and the shipped payloads must agree."""

from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = SCRIPTS.parent
for entry in (str(SCRIPTS), str(REPOSITORY)):
    if entry not in sys.path:
        sys.path.insert(0, entry)

import catalog_v2_retired_fields as retired  # noqa: E402
from backend import exercises_catalog_v2 as backend  # noqa: E402

SOURCE = REPOSITORY / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"
ANDROID_ASSET = REPOSITORY / "android-native" / "app" / "src" / "main" / "assets" / "exercise_catalog_v2.json"
IOS_ASSET = REPOSITORY / "ios-native" / "KPKNFit" / "KPKNFit" / "exercise_catalog_v2.json"


def fully_loaded_catalog() -> dict:
    """A minimal catalogue that carries every retired field exactly once (joint notes: profile and rich)."""
    joint = {"jointId": "codo", "role": "PRIMARY", "actions": ["Flexión"], "note": "retired"}
    return {
        "families": [
            {
                "id": "family_a",
                "description": "retired",
                "evidence": {"rationale": "retired"},
                "definitions": [
                    {
                        "id": "definition_a",
                        "evidence": {"rationale": "retired"},
                        "configurations": [
                            {
                                "id": "configuration_a",
                                "evidence": {"rationale": "retired"},
                                "profile": {
                                    "description": "kept",
                                    "benefits": ["retired"],
                                    "techniqueSummary": "retired",
                                    "variantRationale": "retired",
                                    "commonMistakes": ["retired"],
                                    "muscleNotes": [{"muscleId": "biceps", "note": "retired"}],
                                    "jointInvolvement": [dict(joint)],
                                    "richMetadata": {
                                        "editorial": {},
                                        "coaching": {},
                                        "safety": {},
                                        "anatomy": {
                                            "targetRegions": ["retired"],
                                            "muscleLengthBias": "retired",
                                            "stabilizationDemand": "retired",
                                            "jointActions": ["kept"],
                                            "volumeContribution": "kept",
                                            "jointInvolvement": [dict(joint)],
                                        },
                                        "biomechanics": {
                                            "rangeOfMotion": "retired",
                                            "relevantTendons": [],
                                            "stability": "kept",
                                        },
                                        "programming": {
                                            "objectives": ["retired"],
                                            "suitableRepRanges": ["retired"],
                                            "recoveryCost": "retired",
                                            "setupTransitionCost": "retired",
                                            "splitSuitability": ["retired"],
                                            "role": "kept",
                                            "indicativeRestSeconds": {"min": 60, "max": 90},
                                        },
                                        "replacement": {"preservesIntent": ["kept"]},
                                    },
                                },
                            }
                        ],
                    }
                ],
            }
        ]
    }


class RetiredFieldsTest(unittest.TestCase):
    def test_backend_keeps_a_literal_copy_of_the_retired_paths(self) -> None:
        self.assertEqual(backend.RETIRED_FIELD_PATHS, retired.RETIRED_FIELD_PATHS)

    def test_shared_and_backend_detectors_report_the_same_fields(self) -> None:
        payload = fully_loaded_catalog()
        shared = retired.find_retired(payload)
        self.assertEqual(shared, backend.find_retired_fields(payload))
        # 24 paths; the joint note exists on profile and richMetadata.anatomy, each counted once.
        self.assertEqual(len(shared), len(retired.RETIRED_FIELD_PATHS))

    def test_strip_removes_every_retired_field_and_keeps_the_rest(self) -> None:
        payload = fully_loaded_catalog()
        cleaned = retired.strip_retired(payload)
        self.assertEqual(retired.find_retired(cleaned), [])
        self.assertEqual(backend.find_retired_fields(cleaned), [])
        profile = cleaned["families"][0]["definitions"][0]["configurations"][0]["profile"]
        self.assertEqual(profile["description"], "kept")
        self.assertEqual(profile["jointInvolvement"], [{"jointId": "codo", "role": "PRIMARY", "actions": ["Flexión"]}])
        rich = profile["richMetadata"]
        self.assertEqual(rich["anatomy"]["jointActions"], ["kept"])
        self.assertEqual(rich["anatomy"]["volumeContribution"], "kept")
        self.assertEqual(rich["biomechanics"], {"stability": "kept"})
        self.assertEqual(rich["programming"]["role"], "kept")
        self.assertEqual(rich["replacement"]["preservesIntent"], ["kept"])
        # The input is never mutated.
        self.assertNotEqual(retired.find_retired(payload), [])

    def test_strip_is_idempotent(self) -> None:
        once = retired.strip_retired(fully_loaded_catalog())
        self.assertEqual(retired.strip_retired(once), once)

    def test_each_retired_path_is_detected_on_its_own(self) -> None:
        loaded = fully_loaded_catalog()
        clean = retired.strip_retired(loaded)
        families = lambda payload: payload["families"][0]
        definition = lambda payload: families(payload)["definitions"][0]
        configuration = lambda payload: definition(payload)["configurations"][0]
        profile = lambda payload: configuration(payload)["profile"]
        rich = lambda payload: profile(payload)["richMetadata"]
        injections = {
            "family.description": lambda p: families(p).__setitem__("description", "x"),
            "family.evidence.rationale": lambda p: families(p)["evidence"].__setitem__("rationale", "x"),
            "definition.evidence.rationale": lambda p: definition(p)["evidence"].__setitem__("rationale", "x"),
            "configuration.evidence.rationale": lambda p: configuration(p)["evidence"].__setitem__("rationale", "x"),
            "profile.benefits": lambda p: profile(p).__setitem__("benefits", []),
            "profile.techniqueSummary": lambda p: profile(p).__setitem__("techniqueSummary", "x"),
            "profile.variantRationale": lambda p: profile(p).__setitem__("variantRationale", "x"),
            "profile.commonMistakes": lambda p: profile(p).__setitem__("commonMistakes", []),
            "profile.muscleNotes": lambda p: profile(p).__setitem__("muscleNotes", []),
            "profile.jointInvolvement[].note": lambda p: profile(p)["jointInvolvement"][0].__setitem__("note", "x"),
            "richMetadata.editorial": lambda p: rich(p).__setitem__("editorial", {}),
            "richMetadata.coaching": lambda p: rich(p).__setitem__("coaching", {}),
            "richMetadata.safety": lambda p: rich(p).__setitem__("safety", {}),
            "richMetadata.anatomy.targetRegions": lambda p: rich(p)["anatomy"].__setitem__("targetRegions", []),
            "richMetadata.anatomy.muscleLengthBias": lambda p: rich(p)["anatomy"].__setitem__("muscleLengthBias", "x"),
            "richMetadata.anatomy.stabilizationDemand": lambda p: rich(p)["anatomy"].__setitem__("stabilizationDemand", "x"),
            "richMetadata.anatomy.jointInvolvement[].note": lambda p: rich(p)["anatomy"]["jointInvolvement"][0].__setitem__("note", "x"),
            "richMetadata.biomechanics.rangeOfMotion": lambda p: rich(p)["biomechanics"].__setitem__("rangeOfMotion", "x"),
            "richMetadata.biomechanics.relevantTendons": lambda p: rich(p)["biomechanics"].__setitem__("relevantTendons", []),
            "richMetadata.programming.objectives": lambda p: rich(p)["programming"].__setitem__("objectives", []),
            "richMetadata.programming.suitableRepRanges": lambda p: rich(p)["programming"].__setitem__("suitableRepRanges", []),
            "richMetadata.programming.recoveryCost": lambda p: rich(p)["programming"].__setitem__("recoveryCost", "x"),
            "richMetadata.programming.setupTransitionCost": lambda p: rich(p)["programming"].__setitem__("setupTransitionCost", "x"),
            "richMetadata.programming.splitSuitability": lambda p: rich(p)["programming"].__setitem__("splitSuitability", []),
        }
        self.assertEqual(set(injections), set(retired.RETIRED_FIELD_PATHS))
        for path, inject in injections.items():
            with self.subTest(path=path):
                tampered = copy.deepcopy(clean)
                inject(tampered)
                self.assertEqual(len(retired.find_retired(tampered)), 1)
                self.assertEqual(retired.find_retired(tampered), backend.find_retired_fields(tampered))

    def test_shipped_payloads_carry_no_retired_field(self) -> None:
        for label, path in (("source", SOURCE), ("android", ANDROID_ASSET), ("ios", IOS_ASSET)):
            with self.subTest(payload=label):
                payload = json.loads(path.read_text(encoding="utf-8"))
                self.assertEqual(retired.find_retired(payload), [])
                self.assertEqual(backend.find_retired_fields(payload), [])

    def test_family_payload_shapes_are_accepted(self) -> None:
        family_payload = {"family": fully_loaded_catalog()["families"][0]}
        self.assertEqual(len(retired.find_retired(family_payload)), len(retired.RETIRED_FIELD_PATHS))


if __name__ == "__main__":
    unittest.main()
