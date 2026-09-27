#!/usr/bin/env python3
"""Focused tests for the scoped editorial application mode.

Every test drives the production applier (`scripts/curaduria_v6_catalogo_editorial.py`)
through its real `main()` entry point against temporary fixture families and
briefs. The fixtures are written with the production `canonical_json` so any byte
difference after a run corresponds to a real content change, and the assertions
compare whole sub-trees instead of imitating the transformation.
"""

from __future__ import annotations

import contextlib
import copy
import importlib.util
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any


SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = SCRIPTS.parent
APPLIER = SCRIPTS / "curaduria_v6_catalogo_editorial.py"
# Fixtures never touch the repository: they live in a temporary directory, which
# can be redirected with KPKN_TEST_TEMP_DIR (used to keep the run inside the
# approved agent workspace on Windows).
TEMP_OVERRIDE = os.environ.get("KPKN_TEST_TEMP_DIR")


def temporary_directory() -> tempfile.TemporaryDirectory:
    root = Path(TEMP_OVERRIDE) if TEMP_OVERRIDE and Path(TEMP_OVERRIDE).is_dir() else None
    return tempfile.TemporaryDirectory(dir=str(root) if root else None)


def load_applier():
    spec = importlib.util.spec_from_file_location("curaduria_v6_catalogo_editorial", APPLIER)
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {APPLIER}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


applier = load_applier()

CURRENT_REVISION = "v2-approved-2026-08-12-a"


def profile(definition_id: str, configuration_id: str, marker: str) -> dict[str, Any]:
    """A configuration profile with every field the applier reads or mirrors."""
    return {
        "axialLoadFactor": 0.0,
        "benefits": [f"beneficio previo {marker}"],
        "catalogRevision": CURRENT_REVISION,
        "commonMistakes": [f"error previo {marker}"],
        "description": f"descripción previa {marker}",
        "equipmentId": f"equipo_{marker}",
        "executionCues": [f"ejecución previa {marker}"],
        "loadMode": "bodyweight",
        "movementPatternId": "patron_fijo",
        "primaryMuscles": ["pectoralis"],
        "richMetadata": {
            "anatomy": {"primaryMuscles": ["pectoralis"], "jointInvolvement": []},
            "biomechanics": {"relevantJoints": []},
            "coaching": {
                "commonMistakes": [f"error desactualizado {marker}"],
                "cues": [f"técnica previa {marker}"],
                "execution": [f"ejecución previa {marker}"],
                "progressions": [f"progresión {marker}"],
                "regressions": [f"regresión {marker}"],
                "setup": [f"preparación previa {marker}"],
            },
            "display": {"summary": f"resumen {marker}"},
            "editorial": {
                "benefits": [f"beneficio previo {marker}"],
                "description": f"descripción previa {marker}",
                "technique": f"técnica previa {marker}",
                "variantRationale": f"justificación previa {marker}",
            },
            "evidenceConfidence": "MEDIUM",
            "fatigue": {"tier": "MODERATE"},
            "identity": {
                "canonicalName": f"Nombre {marker}",
                "catalogRevision": CURRENT_REVISION,
                "configurationId": configuration_id,
                "definitionId": definition_id,
                "familyId": "family",
                "kind": "PARENT",
                "searchTerms": [f"término {marker}"],
            },
            "programming": {"minSlots": 2},
            "replacement": {"group": None},
            "safety": {"notes": [f"seguridad {marker}"]},
        },
        "secondaryMuscles": ["triceps"],
        "setupCues": [f"preparación previa {marker}"],
        "technicalDifficulty": 4.0,
        "techniqueSummary": f"técnica previa {marker}",
        "variantRationale": f"justificación previa {marker}",
    }


def definition(definition_id: str, configuration_id: str, marker: str) -> dict[str, Any]:
    return {
        "canonicalName": f"Nombre {marker}",
        "configurations": [
            {
                "displaySummary": f"Peso Corporal {marker}",
                "evidence": {
                    "confidence": "MEDIUM",
                    "evidenceRefs": [f"legacy:exercise_database.json#{marker}"],
                    "rationale": f"evidencia previa {marker}",
                    "reviewStatus": "APPROVED",
                },
                "id": configuration_id,
                "profile": profile(definition_id, configuration_id, marker),
                "selectedOptions": {"implement": "bodyweight"},
            }
        ],
        "defaultConfigurationId": configuration_id,
        "description": f"descripción de definición previa {marker}",
        "evidence": {
            "confidence": "MEDIUM",
            "evidenceRefs": [f"legacy:exercise_database.json#definicion_{marker}"],
            "rationale": f"evidencia de definición previa {marker}",
            "reviewStatus": "APPROVED",
        },
        "familyId": "family",
        "id": definition_id,
        "kind": "PARENT",
        "optionAxes": [],
        "searchTerms": [f"término {marker}"],
    }


def family_payload(family_id: str, definitions: list[dict[str, Any]]) -> dict[str, Any]:
    for item in definitions:
        item["familyId"] = family_id
    return {
        "catalogRevision": CURRENT_REVISION,
        "family": {
            "canonicalName": f"Familia {family_id}",
            "definitions": definitions,
            "description": f"descripción de familia {family_id}",
            "evidence": {
                "confidence": "MEDIUM",
                "evidenceRefs": [f"legacy:family_{family_id}"],
                "rationale": f"evidencia de familia {family_id}",
                "reviewStatus": "APPROVED",
            },
            "id": family_id,
            "taxonomy": {"domain": "upper", "region": "torso"},
        },
        "ontologyRevision": "wikilab-v3-2026-08-08",
        "schemaVersion": 1,
    }


def build_definition(item: tuple[str, str, str]) -> dict[str, Any]:
    definition_id, configuration_id, marker = item
    return definition(definition_id, configuration_id, marker)


def configuration_brief(marker: str) -> dict[str, Any]:
    return {
        "benefits": [f"beneficio editorial {marker} uno", f"beneficio editorial {marker} dos"],
        "description": f"línea de matiz editorial {marker} con suficiente longitud.",
        "executionCues": [f"ejecución editorial {marker}"],
        "setupCues": [f"preparación editorial {marker}"],
        "techniqueSummary": f"técnica editorial {marker}",
        "variantRationale": f"justificación editorial {marker}",
    }


def definition_brief(definition_id: str, configuration_id: str, marker: str) -> dict[str, Any]:
    return {
        "configurations": {configuration_id: configuration_brief(marker)},
        "description": f"descripción editorial de {definition_id} con longitud suficiente.",
    }


def changed_paths(before: Any, after: Any, prefix: str = "") -> set[str]:
    """Every dotted path whose value differs between two parsed payloads."""
    if isinstance(before, dict) and isinstance(after, dict):
        paths: set[str] = set()
        for key in set(before) | set(after):
            child = f"{prefix}.{key}" if prefix else key
            if key not in before or key not in after:
                paths.add(child)
            else:
                paths |= changed_paths(before[key], after[key], child)
        return paths
    if isinstance(before, list) and isinstance(after, list):
        if len(before) != len(after) or (before and not isinstance(before[0], dict)):
            return set() if before == after else {prefix}
        paths = set()
        for index, (old, new) in enumerate(zip(before, after)):
            paths |= changed_paths(old, new, f"{prefix}.{index}")
        return paths
    return set() if before == after else {prefix}


class FixtureWorkspace:
    """Three independent family files plus the briefs, all inside a temp dir."""

    LAYOUT = (
        ("family_a.json", "family_a", (("alpha", "alpha__default", "alfa"), ("beta", "beta__default", "beta"))),
        ("family_b.json", "family_b", (("gamma", "gamma__default", "gamma"), ("delta", "delta__default", "delta"))),
        ("family_c.json", "family_c", (("epsilon", "epsilon__default", "epsilon"),)),
    )
    BRIEFS = {
        "alpha": ("alpha__default", "alfa"),
        "beta": ("beta__default", "beta"),
        "gamma": ("gamma__default", "gamma"),
        "delta": ("delta__default", "delta"),
        "epsilon": ("epsilon__default", "epsilon"),
    }

    def __init__(self, root: Path) -> None:
        self.root = root
        self.families = root / "families"
        self.families.mkdir(parents=True)
        self.briefs_path = root / "editorial_briefs.json"
        self.paths = {}
        for file_name, family_id, definitions in self.LAYOUT:
            payload = family_payload(family_id, [build_definition(item) for item in definitions])
            path = self.families / file_name
            self.write(path, payload)
            self.paths[family_id] = path
        self.write_briefs(self.briefs(CURRENT_REVISION))

    @staticmethod
    def write(path: Path, payload: Any) -> None:
        path.write_bytes(applier.canonical_json(payload))

    @staticmethod
    def briefs(revision: str) -> dict[str, Any]:
        return {
            "catalogRevision": revision,
            "definitions": {
                definition_id: definition_brief(definition_id, configuration_id, marker)
                for definition_id, (configuration_id, marker) in FixtureWorkspace.BRIEFS.items()
            },
            "schemaVersion": 1,
            "source": "curaduria_v7.2_human_editorial_2026-08-10",
        }

    def write_briefs(self, payload: dict[str, Any]) -> None:
        self.write(self.briefs_path, payload)

    def family(self, family_id: str) -> dict[str, Any]:
        return json.loads(self.paths[family_id].read_text(encoding="utf-8"))

    def definition(self, family_id: str, definition_id: str) -> dict[str, Any]:
        for item in self.family(family_id)["family"]["definitions"]:
            if item["id"] == definition_id:
                return item
        raise AssertionError(f"{definition_id} not present in {family_id}")

    def snapshot(self) -> dict[str, bytes]:
        return {path.name: path.read_bytes() for path in sorted(self.families.glob("*.json"))}

    def run(self, argv: list[str]) -> str:
        stream = io.StringIO()
        with contextlib.redirect_stdout(stream):
            code = applier.main(argv, families_dir=self.families, briefs_path=self.briefs_path)
        if code != 0:
            raise AssertionError(f"expected exit 0, got {code}")
        return stream.getvalue()


class ScopedEditorialApplicationTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temporary = temporary_directory()
        self.addCleanup(self._temporary.cleanup)
        self.workspace = FixtureWorkspace(Path(self._temporary.name))

    def assertNoWrites(self, before: dict[str, bytes]) -> None:
        self.assertEqual(before, self.workspace.snapshot())
        self.assertEqual([], list(self.workspace.families.glob(".*.codex-editorial-tmp")))

    def test_scoped_mode_updates_only_requested_definitions_in_separate_families(self) -> None:
        before = self.workspace.snapshot()
        untouched = {name: payload for name, payload in self.workspace.family("family_c").items()}

        output = self.workspace.run(["--only-definitions", "alpha,gamma"])

        self.assertIn("mode=scoped definitions=2 families=2", output)
        self.assertIn("definition=alpha family=family_a.json", output)
        self.assertIn("definition=gamma family=family_b.json", output)
        self.assertIn("family_file=family_a.json", output)
        self.assertIn("family_file=family_b.json", output)
        self.assertNotIn("family_c.json", output)

        expected = {
            "family.definitions.0.description",
            "family.definitions.0.configurations.0.profile.description",
            "family.definitions.0.configurations.0.profile.benefits",
            "family.definitions.0.configurations.0.profile.techniqueSummary",
            "family.definitions.0.configurations.0.profile.variantRationale",
            "family.definitions.0.configurations.0.profile.setupCues",
            "family.definitions.0.configurations.0.profile.executionCues",
            "family.definitions.0.configurations.0.profile.richMetadata.editorial.description",
            "family.definitions.0.configurations.0.profile.richMetadata.editorial.benefits",
            "family.definitions.0.configurations.0.profile.richMetadata.editorial.technique",
            "family.definitions.0.configurations.0.profile.richMetadata.editorial.variantRationale",
            "family.definitions.0.configurations.0.profile.richMetadata.coaching.setup",
            "family.definitions.0.configurations.0.profile.richMetadata.coaching.execution",
            "family.definitions.0.configurations.0.profile.richMetadata.coaching.cues",
            "family.definitions.0.configurations.0.profile.richMetadata.coaching.commonMistakes",
        }
        for family_id in ("family_a", "family_b"):
            original = json.loads(before[f"{family_id}.json"].decode("utf-8"))
            written = self.workspace.family(family_id)
            self.assertEqual(expected, changed_paths(original, written))

        self.assertNotEqual(before["family_a.json"], self.workspace.paths["family_a"].read_bytes())
        self.assertNotEqual(before["family_b.json"], self.workspace.paths["family_b"].read_bytes())

        # The untouched family is byte identical and structurally identical.
        self.assertEqual(before["family_c.json"], self.workspace.paths["family_c"].read_bytes())
        self.assertEqual(untouched, self.workspace.family("family_c"))

        # Sibling definitions in the touched families are structurally identical.
        for family_id, sibling in (("family_a", "beta"), ("family_b", "delta")):
            original = json.loads(before[f"{family_id}.json"].decode("utf-8"))
            sibling_before = next(
                item for item in original["family"]["definitions"] if item["id"] == sibling
            )
            self.assertEqual(sibling_before, self.workspace.definition(family_id, sibling))
            self.assertEqual(original["family"]["evidence"], self.workspace.family(family_id)["family"]["evidence"])
            self.assertEqual(
                original["family"]["canonicalName"], self.workspace.family(family_id)["family"]["canonicalName"]
            )
            self.assertEqual(original["family"]["taxonomy"], self.workspace.family(family_id)["family"]["taxonomy"])
            self.assertEqual(original["schemaVersion"], self.workspace.family(family_id)["schemaVersion"])
            self.assertEqual(original["ontologyRevision"], self.workspace.family(family_id)["ontologyRevision"])

    def test_scoped_mode_mirrors_the_exact_brief_copy(self) -> None:
        self.workspace.run(["--only-definitions", "alpha"])
        brief = self.workspace.briefs(CURRENT_REVISION)["definitions"]["alpha"]
        configuration_brief = brief["configurations"]["alpha__default"]
        definition_payload = self.workspace.definition("family_a", "alpha")
        configuration = definition_payload["configurations"][0]
        mirror = configuration["profile"]
        rich = mirror["richMetadata"]

        self.assertEqual(brief["description"], definition_payload["description"])
        for key in ("description", "benefits", "techniqueSummary", "variantRationale", "setupCues", "executionCues"):
            self.assertEqual(configuration_brief[key], mirror[key])
        self.assertEqual(mirror["description"], rich["editorial"]["description"])
        self.assertEqual(mirror["benefits"], rich["editorial"]["benefits"])
        self.assertEqual(mirror["techniqueSummary"], rich["editorial"]["technique"])
        self.assertEqual(mirror["variantRationale"], rich["editorial"]["variantRationale"])
        self.assertEqual(mirror["setupCues"], rich["coaching"]["setup"])
        self.assertEqual(mirror["executionCues"], rich["coaching"]["execution"])
        self.assertEqual([mirror["techniqueSummary"]], rich["coaching"]["cues"])
        self.assertEqual(mirror["commonMistakes"], rich["coaching"]["commonMistakes"])
        self.assertEqual(["pectoralis"], rich["anatomy"]["primaryMuscles"])
        self.assertEqual([], rich["anatomy"]["jointInvolvement"])
        self.assertEqual([], rich["biomechanics"]["relevantJoints"])
        self.assertEqual({"implement": "bodyweight"}, configuration["selectedOptions"])
        self.assertEqual(f"Peso Corporal alfa", configuration["displaySummary"])
        self.assertEqual("alpha__default", definition_payload["defaultConfigurationId"])
        self.assertEqual([], definition_payload["optionAxes"])
        self.assertEqual("PARENT", definition_payload["kind"])
        self.assertEqual([f"término alfa"], definition_payload["searchTerms"])
        self.assertEqual("Nombre alfa", definition_payload["canonicalName"])
        self.assertEqual("patron_fijo", mirror["movementPatternId"])
        self.assertEqual(4.0, mirror["technicalDifficulty"])
        self.assertEqual("wikilab-v3-2026-08-08", self.workspace.family("family_a")["ontologyRevision"])

    def test_scoped_mode_preserves_revision_evidence_and_review_status(self) -> None:
        self.workspace.run(["--only-definitions", "alpha,gamma"])
        for family_id, definition_id, configuration_id, marker in (
            ("family_a", "alpha", "alpha__default", "alfa"),
            ("family_b", "gamma", "gamma__default", "gamma"),
        ):
            payload = self.workspace.family(family_id)
            self.assertEqual(CURRENT_REVISION, payload["catalogRevision"])
            self.assertEqual("wikilab-v3-2026-08-08", payload["ontologyRevision"])
            definition_payload = self.workspace.definition(family_id, definition_id)
            configuration = definition_payload["configurations"][0]
            self.assertEqual(CURRENT_REVISION, configuration["profile"]["catalogRevision"])
            self.assertEqual(
                CURRENT_REVISION, configuration["profile"]["richMetadata"]["identity"]["catalogRevision"]
            )
            self.assertEqual([f"legacy:family_{family_id}"], payload["family"]["evidence"]["evidenceRefs"])
            self.assertEqual(f"evidencia de familia {family_id}", payload["family"]["evidence"]["rationale"])
            self.assertEqual("APPROVED", payload["family"]["evidence"]["reviewStatus"])
            self.assertEqual(
                [f"legacy:exercise_database.json#definicion_{marker}"],
                definition_payload["evidence"]["evidenceRefs"],
            )
            self.assertEqual(f"evidencia de definición previa {marker}", definition_payload["evidence"]["rationale"])
            self.assertEqual("APPROVED", definition_payload["evidence"]["reviewStatus"])
            self.assertEqual([f"legacy:exercise_database.json#{marker}"], configuration["evidence"]["evidenceRefs"])
            self.assertEqual(f"evidencia previa {marker}", configuration["evidence"]["rationale"])
            self.assertEqual("APPROVED", configuration["evidence"]["reviewStatus"])
            self.assertEqual(f"equipo_{marker}", configuration["profile"]["equipmentId"])
            self.assertEqual(f"resumen {marker}", configuration["profile"]["richMetadata"]["display"]["summary"])
            self.assertEqual(
                [f"progresión {marker}"], configuration["profile"]["richMetadata"]["coaching"]["progressions"]
            )

    def test_scoped_mode_never_inserts_the_historical_human_editorial_reference(self) -> None:
        self.workspace.run(["--only-definitions", "alpha,gamma,epsilon"])
        for family_id, definition_id, configuration_id, marker in (
            ("family_a", "alpha", "alpha__default", "alfa"),
            ("family_b", "gamma", "gamma__default", "gamma"),
            ("family_c", "epsilon", "epsilon__default", "epsilon"),
        ):
            payload = self.workspace.family(family_id)
            references = list(payload["family"]["evidence"]["evidenceRefs"])
            references += list(self.workspace.definition(family_id, definition_id)["evidence"]["evidenceRefs"])
            references += list(
                self.workspace.definition(family_id, definition_id)["configurations"][0]["evidence"]["evidenceRefs"]
            )
            self.assertNotIn(applier.EVIDENCE_REF, references)
            self.assertFalse([ref for ref in references if "catalog-v" in ref], references)
            self.assertTrue([ref for ref in references if ref.startswith("legacy:")], references)
            self.assertEqual(configuration_id, self.workspace.definition(family_id, definition_id)["defaultConfigurationId"])

    def test_scoped_mode_writes_only_families_that_own_a_requested_id(self) -> None:
        before = self.workspace.snapshot()
        self.workspace.run(["--only-definitions", "delta"])
        self.assertEqual(before["family_a.json"], self.workspace.paths["family_a"].read_bytes())
        self.assertEqual(before["family_c.json"], self.workspace.paths["family_c"].read_bytes())
        self.assertNotEqual(before["family_b.json"], self.workspace.paths["family_b"].read_bytes())

    def test_scoped_mode_accepts_the_current_revision_while_unscoped_still_rejects_it(self) -> None:
        self.assertNotEqual(CURRENT_REVISION, applier.REVISION)
        self.workspace.run(["--only-definitions", "alpha"])
        with self.assertRaises(SystemExit) as failure:
            self.workspace.run([])
        self.assertEqual(f"brief revision mismatch: {CURRENT_REVISION}", str(failure.exception))

    def test_scoped_mode_is_idempotent(self) -> None:
        self.workspace.run(["--only-definitions", "alpha,gamma"])
        first = self.workspace.snapshot()
        self.workspace.run(["--only-definitions", "alpha,gamma"])
        self.assertEqual(first, self.workspace.snapshot())

    def test_scoped_mode_tolerates_spaces_and_a_single_id(self) -> None:
        output = self.workspace.run(["--only-definitions", " gamma "])
        self.assertIn("mode=scoped definitions=1 families=1", output)
        self.assertIn("definition=gamma family=family_b.json", output)

    def test_unknown_definition_id_fails_before_any_write(self) -> None:
        before = self.workspace.snapshot()
        with self.assertRaises(SystemExit) as failure:
            self.workspace.run(["--only-definitions", "alpha,ghost"])
        self.assertEqual("unknown definition id: ghost", str(failure.exception))
        self.assertNoWrites(before)

    def test_empty_and_malformed_selections_fail_before_any_write(self) -> None:
        before = self.workspace.snapshot()
        for selection, message in (
            ("", "--only-definitions requires at least one non-empty definition id"),
            (",", "--only-definitions requires at least one non-empty definition id"),
            ("   ", "--only-definitions requires at least one non-empty definition id"),
            ("alpha,,gamma", "--only-definitions requires at least one non-empty definition id"),
        ):
            with self.subTest(selection=selection):
                with self.assertRaises(SystemExit) as failure:
                    self.workspace.run(["--only-definitions", selection])
                self.assertEqual(message, str(failure.exception))
                self.assertNoWrites(before)

    def test_duplicate_definition_ids_fail_before_any_write(self) -> None:
        before = self.workspace.snapshot()
        with self.assertRaises(SystemExit) as failure:
            self.workspace.run(["--only-definitions", "alpha,gamma,alpha"])
        self.assertEqual("duplicate definition ids requested: alpha", str(failure.exception))
        self.assertNoWrites(before)

    def test_revision_mismatch_fails_before_any_write(self) -> None:
        before = self.workspace.snapshot()
        payload = self.workspace.family("family_c")
        payload["catalogRevision"] = "v2-approved-2026-08-11-b"
        self.workspace.write(self.workspace.paths["family_c"], payload)
        drifted = self.workspace.snapshot()
        with self.assertRaises(SystemExit) as failure:
            self.workspace.run(["--only-definitions", "alpha"])
        self.assertEqual(
            f"family revision mismatch: v2-approved-2026-08-11-b in family_c.json != briefs {CURRENT_REVISION}",
            str(failure.exception),
        )
        self.assertEqual(drifted, self.workspace.snapshot())
        self.assertNotEqual(before["family_c.json"], drifted["family_c.json"])

    def test_brief_revision_must_be_a_non_empty_string(self) -> None:
        before = self.workspace.snapshot()
        briefs = self.workspace.briefs(CURRENT_REVISION)
        briefs["catalogRevision"] = ""
        self.workspace.write_briefs(briefs)
        with self.assertRaises(SystemExit) as failure:
            self.workspace.run(["--only-definitions", "alpha"])
        self.assertEqual("editorial briefs catalogRevision must be a non-empty string", str(failure.exception))
        self.assertNoWrites(before)

    def test_missing_or_invalid_briefs_fail_before_any_write(self) -> None:
        before = self.workspace.snapshot()

        briefs = self.workspace.briefs(CURRENT_REVISION)
        del briefs["definitions"]["alpha"]
        self.workspace.write_briefs(briefs)
        with self.assertRaises(SystemExit) as failure:
            self.workspace.run(["--only-definitions", "alpha,gamma"])
        self.assertEqual("missing definition brief: alpha", str(failure.exception))
        self.assertNoWrites(before)

        briefs = self.workspace.briefs(CURRENT_REVISION)
        del briefs["definitions"]["alpha"]["configurations"]["alpha__default"]["variantRationale"]
        self.workspace.write_briefs(briefs)
        with self.assertRaises(SystemExit) as failure:
            self.workspace.run(["--only-definitions", "alpha,gamma"])
        self.assertEqual(
            "missing configuration brief field variantRationale: alpha__default", str(failure.exception)
        )
        self.assertNoWrites(before)

        briefs = self.workspace.briefs(CURRENT_REVISION)
        briefs["definitions"]["alpha"]["configurations"] = {}
        self.workspace.write_briefs(briefs)
        with self.assertRaises(SystemExit) as failure:
            self.workspace.run(["--only-definitions", "alpha,gamma"])
        self.assertEqual("missing configuration brief: alpha__default", str(failure.exception))
        self.assertNoWrites(before)

    def test_definition_id_duplicated_across_families_fails_before_any_write(self) -> None:
        payload = self.workspace.family("family_c")
        clone = copy.deepcopy(self.workspace.definition("family_a", "alpha"))
        clone["id"] = "gamma"
        clone["familyId"] = "family_c"
        payload["family"]["definitions"].append(clone)
        self.workspace.write(self.workspace.paths["family_c"], payload)
        before = self.workspace.snapshot()
        with self.assertRaises(SystemExit) as failure:
            self.workspace.run(["--only-definitions", "alpha,gamma"])
        self.assertEqual(
            "definition id is not unique across families: gamma (family_b.json, family_c.json)",
            str(failure.exception),
        )
        self.assertNoWrites(before)


class UnscopedBehaviourPreservedTest(unittest.TestCase):
    def setUp(self) -> None:
        self._temporary = temporary_directory()
        self.addCleanup(self._temporary.cleanup)
        self.root = Path(self._temporary.name)
        self.families = self.root / "families"
        self.families.mkdir()
        self.briefs_path = self.root / "editorial_briefs.json"

    def seed(self, brief_revision: str, *, ontology: str | None = "wikilab-v3-2026-08-08") -> None:
        payloads = {}
        for file_name, family_id, definitions in FixtureWorkspace.LAYOUT:
            payload = family_payload(family_id, [build_definition(item) for item in definitions])
            if ontology is None:
                del payload["ontologyRevision"]
            FixtureWorkspace.write(self.families / file_name, payload)
            payloads[file_name] = payload
        self.originals = payloads
        FixtureWorkspace.write(self.briefs_path, FixtureWorkspace.briefs(brief_revision))

    def run_main(self, argv: list[str]) -> str:
        stream = io.StringIO()
        with contextlib.redirect_stdout(stream):
            code = applier.main(argv, families_dir=self.families, briefs_path=self.briefs_path)
        self.assertEqual(0, code)
        return stream.getvalue()

    def test_unscoped_run_keeps_the_historical_transformation(self) -> None:
        self.seed(applier.REVISION)
        output = self.run_main([])
        self.assertIn(f"revision={applier.REVISION}", output)
        self.assertIn("families=3 configurations=5", output)
        self.assertNotIn("mode=scoped", output)

        for file_name, original in self.originals.items():
            written = json.loads((self.families / file_name).read_text(encoding="utf-8"))
            self.assertEqual(applier.REVISION, written["catalogRevision"])
            self.assertEqual("wikilab-v3-2026-08-08", written["ontologyRevision"])
            self.assertEqual([applier.EVIDENCE_REF], written["family"]["evidence"]["evidenceRefs"])
            self.assertEqual(applier.FAMILY_RATIONALE, written["family"]["evidence"]["rationale"])
            for before, after in zip(original["family"]["definitions"], written["family"]["definitions"]):
                definition_id = before["id"]
                configuration_id = before["configurations"][0]["id"]
                marker = FixtureWorkspace.BRIEFS[definition_id][1]
                brief = FixtureWorkspace.briefs(applier.REVISION)["definitions"][definition_id]["configurations"][
                    configuration_id
                ]
                self.assertEqual([applier.EVIDENCE_REF], after["evidence"]["evidenceRefs"])
                self.assertEqual(applier.DEFINITION_RATIONALE, after["evidence"]["rationale"])
                self.assertEqual(brief["description"], after["configurations"][0]["profile"]["description"])
                self.assertEqual(
                    [f"legacy:exercise_database.json#{marker}", applier.EVIDENCE_REF],
                    after["configurations"][0]["evidence"]["evidenceRefs"],
                )
                self.assertEqual(applier.CONFIGURATION_RATIONALE, after["configurations"][0]["evidence"]["rationale"])
                self.assertEqual(applier.REVISION, after["configurations"][0]["profile"]["catalogRevision"])
                self.assertEqual(
                    applier.REVISION, after["configurations"][0]["profile"]["richMetadata"]["identity"]["catalogRevision"]
                )

    def test_unscoped_run_supplies_the_default_ontology_revision(self) -> None:
        self.seed(applier.REVISION, ontology=None)
        self.run_main([])
        for file_name in self.originals:
            written = json.loads((self.families / file_name).read_text(encoding="utf-8"))
            self.assertEqual("wikilab-v3-2026-08-08", written["ontologyRevision"])

    def test_unscoped_run_rejects_a_brief_revision_that_differs(self) -> None:
        self.seed(CURRENT_REVISION)
        with self.assertRaises(SystemExit) as failure:
            self.run_main([])
        self.assertEqual(f"brief revision mismatch: {CURRENT_REVISION}", str(failure.exception))
        for file_name, original in self.originals.items():
            self.assertEqual(applier.canonical_json(original), (self.families / file_name).read_bytes())

    def test_unscoped_run_requires_an_object_of_definitions(self) -> None:
        self.seed(applier.REVISION)
        FixtureWorkspace.write(self.briefs_path, {"catalogRevision": applier.REVISION, "definitions": []})
        with self.assertRaises(SystemExit) as failure:
            self.run_main([])
        self.assertEqual("editorial briefs definitions must be an object", str(failure.exception))

    def test_unscoped_run_fails_when_a_definition_brief_is_absent(self) -> None:
        self.seed(applier.REVISION)
        briefs = FixtureWorkspace.briefs(applier.REVISION)
        del briefs["definitions"]["beta"]
        FixtureWorkspace.write(self.briefs_path, briefs)
        with self.assertRaises(SystemExit) as failure:
            self.run_main([])
        self.assertEqual("missing definition brief: beta", str(failure.exception))


class CliWiringTest(unittest.TestCase):
    def test_help_exposes_only_definitions_without_writing_anything(self) -> None:
        completed = subprocess.run(
            [sys.executable, str(APPLIER), "--help"],
            capture_output=True,
            text=True,
            cwd=str(REPOSITORY),
            timeout=60,
        )
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("--only-definitions", completed.stdout)
        self.assertIn("Scoped mode", completed.stdout)
        self.assertEqual("", completed.stderr)

    def test_default_invocation_keeps_working_without_new_arguments(self) -> None:
        parser_actions = {tuple(action.option_strings) for action in applier.build_parser()._actions}
        self.assertIn(("--only-definitions",), parser_actions)
        self.assertIsNone(applier.parse_only_definitions(None))
        self.assertEqual(["alpha", "beta"], applier.parse_only_definitions("alpha, beta"))


if __name__ == "__main__":
    unittest.main()

