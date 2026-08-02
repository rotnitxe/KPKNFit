from __future__ import annotations

import json
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
        self.assertEqual(runtime["catalogRevision"], "v2-approved-2026-08-02")
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
            "58abd25a9a4e600d3c2e1347d91b43fb76144c3eac9d7411a415904a80b80a06",
        )


if __name__ == "__main__":
    unittest.main()
