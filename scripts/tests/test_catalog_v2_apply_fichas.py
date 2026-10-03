#!/usr/bin/env python3
"""Tests for the ficha applier (`scripts/catalog_v2_apply_fichas.py`).

Every test drives the production applier. Fixtures are temporary copies of a few
*real* family files and their real fichas (never hard-coded production answers),
mutated one targeted way per test, so the repository is never touched.

What is pinned here:

* the invariant of the whole pipeline: applying the shipped fichas changes nothing;
* strict preflight: one invalid ficha blocks every write, including valid families;
* idempotence and no-rewrite of unchanged files;
* scoped mode (`--only-definitions`) writes only the requested definitions;
* a CURATED ficha copies public copy + anatomy, re-derives every mirror with
  `catalog_v2_derived`, and touches nothing else (ids, evidence, fatigue, options...);
* the validation that keeps a malformed ficha from ever reaching the catalog.
"""

from __future__ import annotations

import contextlib
import copy
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any, Callable

SCRIPTS = Path(__file__).resolve().parents[1]
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

import catalog_v2_apply_fichas as apply_fichas  # noqa: E402
import catalog_v2_derived as derived  # noqa: E402
from catalog_v2_ontology import MOVEMENT_PATTERN_IDS  # noqa: E402

# Temporary fixtures can be redirected with KPKN_TEST_TEMP_DIR (approved agent workspace on Windows).
TEMP_OVERRIDE = os.environ.get("KPKN_TEST_TEMP_DIR")

# Everything an applied ficha is allowed to change, as paths of `changed_paths`.
ALLOWED_PATH = re.compile(
    r"^family\.definitions\[[^\]]+\]"
    r"(?:\.description$"
    r"|\.configurations\[[^\]]+\]\.profile\."
    r"(?:description|setupCues|executionCues|primaryMuscles|secondaryMuscles|stabilizerMuscles"
    r"|jointInvolvement|movementPatternId"
    r"|richMetadata\.anatomy\.[A-Za-z]+"
    r"|richMetadata\.biomechanics\.(?:relevantJoints|movementPatternId)"
    r"|richMetadata\.replacement\.preservesIntent)(?:[\[.].*)?$)"
)


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def pick_families() -> tuple[str, str]:
    """A family with a multi-configuration definition (for overrides) and any other family."""
    multi: str | None = None
    other: str | None = None
    for path in sorted(apply_fichas.FAMILIES.glob("*.json")):
        payload = read_json(path)
        family_id = payload["family"]["id"]
        has_multi = any(len(definition["configurations"]) >= 2 for definition in payload["family"]["definitions"])
        if multi is None and has_multi:
            multi = family_id
        elif other is None:
            other = family_id
        if multi and other:
            return multi, other
    raise AssertionError("the catalog needs a multi-configuration family and another family")


MULTI_FAMILY, OTHER_FAMILY = pick_families()


class Workspace:
    """Copies of some real family files and their fichas inside a temporary directory."""

    def __init__(self, test: unittest.TestCase, family_ids: list[str]) -> None:
        raw = tempfile.TemporaryDirectory(dir=TEMP_OVERRIDE if TEMP_OVERRIDE and Path(TEMP_OVERRIDE).is_dir() else None)
        test.addCleanup(raw.cleanup)
        root = Path(raw.name)
        self.families = root / "families"
        self.fichas = root / "fichas"
        self.families.mkdir()
        self.fichas.mkdir()
        for family_id in family_ids:
            shutil.copyfile(apply_fichas.FAMILIES / f"{family_id}.json", self.families / f"{family_id}.json")
            shutil.copyfile(apply_fichas.FICHAS / f"{family_id}.json", self.fichas / f"{family_id}.json")

    def family(self, family_id: str) -> dict[str, Any]:
        return read_json(self.families / f"{family_id}.json")

    def ficha(self, family_id: str) -> dict[str, Any]:
        return read_json(self.fichas / f"{family_id}.json")

    def write_ficha(self, family_id: str, ficha: dict[str, Any]) -> None:
        (self.fichas / f"{family_id}.json").write_bytes(apply_fichas.canonical_json(ficha))

    def family_bytes(self) -> dict[str, bytes]:
        return {path.name: path.read_bytes() for path in sorted(self.families.glob("*.json"))}

    def run(self, *argv: str) -> tuple[int, str]:
        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            code = apply_fichas.main(list(argv), families_dir=self.families, fichas_dir=self.fichas)
        return code, buffer.getvalue()


def curated_body(payload: dict[str, Any], definition_id: str, ficha: dict[str, Any]) -> dict[str, Any]:
    """A complete, valid CURATED body for one definition, with a synthetic anatomy and one override."""
    definition = next(item for item in payload["family"]["definitions"] if item["id"] == definition_id)
    first, *_ = definition["configurations"]
    anatomy = {
        "muscles": [
            {"id": "pectoralis", "role": "PRIMARY"},
            {"id": "triceps", "role": "SECONDARY"},
            {"id": "deltoid", "role": "SECONDARY"},
            {"id": "core", "role": "STABILIZER"},
        ],
        "joints": [
            {"id": "glenohumeral", "role": "PRIMARY", "actions": ["Aducción horizontal", "Rotación interna"]},
            {"id": "codo", "role": "SECONDARY", "actions": ["Extensión", "Rotación interna"]},
        ],
        "overrides": {
            first["id"]: {
                "muscles": [
                    {"id": "triceps", "role": "NONE"},
                    {"id": "deltoid", "role": "PRIMARY"},
                    {"id": "latissimus_dorsi", "role": "STABILIZER"},
                ],
                "joints": [
                    {"id": "codo", "role": "NONE"},
                    {"id": "muñeca", "role": "STABILIZER", "actions": ["Estabilización"]},
                ],
            }
        },
    }
    public = ficha["definitions"][definition_id]["public"]
    return {
        "status": apply_fichas.STATUS_CURATED,
        "public": public,
        "technique": {"note": "fixture"},
        "anatomy": anatomy,
        "visual": {"note": "fixture"},
        "sources": ["fixture"],
    }


def first_definition_id(payload: dict[str, Any]) -> str:
    return next(definition["id"] for definition in payload["family"]["definitions"] if len(definition["configurations"]) >= 2)


def run_cli(*argv: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(SCRIPTS / "catalog_v2_apply_fichas.py"), *argv],
        cwd=SCRIPTS.parent,
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=300,
    )


class RealCatalogInvariantTest(unittest.TestCase):
    def test_shipped_fichas_are_valid_and_the_families_are_in_sync(self) -> None:
        """The pipeline invariant, through the real command line: apply changes nothing on the committed catalog."""
        result = run_cli("--check")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("mode=check", result.stdout)
        self.assertIn("changed=0", result.stdout)

    def test_help_lists_the_options_without_writing_anything(self) -> None:
        before = {path.name: path.stat().st_mtime_ns for path in apply_fichas.FAMILIES.glob("*.json")}
        result = run_cli("--help")
        self.assertEqual(result.returncode, 0)
        self.assertIn("--only-definitions", result.stdout)
        self.assertIn("--check", result.stdout)
        self.assertEqual(before, {path.name: path.stat().st_mtime_ns for path in apply_fichas.FAMILIES.glob("*.json")})

    def test_every_family_has_exactly_one_ficha(self) -> None:
        family_ids = sorted(read_json(path)["family"]["id"] for path in apply_fichas.FAMILIES.glob("*.json"))
        ficha_ids = sorted(path.stem for path in apply_fichas.FICHAS.glob("*.json"))
        self.assertEqual(family_ids, ficha_ids)


class ApplyBehaviourTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workspace = Workspace(self, [MULTI_FAMILY, OTHER_FAMILY])
        self.payload = self.workspace.family(MULTI_FAMILY)
        self.definition_id = first_definition_id(self.payload)

    def _edit_description(self, family_id: str, definition_id: str, text: str) -> None:
        ficha = self.workspace.ficha(family_id)
        ficha["definitions"][definition_id]["public"]["description"] = text
        self.workspace.write_ficha(family_id, ficha)

    def test_apply_copies_the_public_copy_and_is_idempotent(self) -> None:
        self._edit_description(MULTI_FAMILY, self.definition_id, "Texto nuevo y distinto de la definición.")
        code, output = self.workspace.run()
        self.assertEqual(code, 0)
        self.assertIn("changed=1", output)
        definition = next(item for item in self.workspace.family(MULTI_FAMILY)["family"]["definitions"] if item["id"] == self.definition_id)
        self.assertEqual(definition["description"], "Texto nuevo y distinto de la definición.")
        # A public-only apply must leave revisions, evidence, ids and anatomy exactly as they were.
        stray = [path for path in apply_fichas.changed_paths(self.payload, self.workspace.family(MULTI_FAMILY)) if not ALLOWED_PATH.match(path)]
        self.assertEqual(stray, [])

        snapshot = self.workspace.family_bytes()
        stamps = {path.name: path.stat().st_mtime_ns for path in self.workspace.families.glob("*.json")}
        code, output = self.workspace.run()
        self.assertEqual(code, 0)
        self.assertIn("changed=0", output)
        self.assertEqual(snapshot, self.workspace.family_bytes())
        self.assertEqual(stamps, {path.name: path.stat().st_mtime_ns for path in self.workspace.families.glob("*.json")}, "unchanged files must not be rewritten")

    def test_public_cues_and_description_are_copied_per_configuration(self) -> None:
        ficha = self.workspace.ficha(MULTI_FAMILY)
        configurations = ficha["definitions"][self.definition_id]["public"]["configurations"]
        target_id = sorted(configurations)[0]
        configurations[target_id]["description"] = "Descripción propia de esta variante."
        configurations[target_id]["setupCues"] = ["Primera señal de preparación.", "Segunda señal de preparación."]
        configurations[target_id]["executionCues"] = ["Señal de ejecución única."]
        self.workspace.write_ficha(MULTI_FAMILY, ficha)
        self.assertEqual(self.workspace.run()[0], 0)
        definition = next(item for item in self.workspace.family(MULTI_FAMILY)["family"]["definitions"] if item["id"] == self.definition_id)
        profile = next(item for item in definition["configurations"] if item["id"] == target_id)["profile"]
        self.assertEqual(profile["description"], "Descripción propia de esta variante.")
        self.assertEqual(profile["setupCues"], ["Primera señal de preparación.", "Segunda señal de preparación."])
        self.assertEqual(profile["executionCues"], ["Señal de ejecución única."])

    def test_check_reports_drift_and_never_writes(self) -> None:
        self._edit_description(MULTI_FAMILY, self.definition_id, "Texto que todavía no se aplicó.")
        before = self.workspace.family_bytes()
        code, output = self.workspace.run("--check")
        self.assertEqual(code, 1)
        self.assertIn("differs", output)
        self.assertIn(f"definitions[{self.definition_id}].description", output)
        self.assertEqual(before, self.workspace.family_bytes())

    def test_scoped_mode_writes_only_the_requested_definition(self) -> None:
        other_payload = self.workspace.family(OTHER_FAMILY)
        other_definition = other_payload["family"]["definitions"][0]["id"]
        siblings = [item["id"] for item in self.payload["family"]["definitions"] if item["id"] != self.definition_id]
        self._edit_description(MULTI_FAMILY, self.definition_id, "Solo esta definición debe cambiar.")
        self._edit_description(OTHER_FAMILY, other_definition, "Esta otra familia no se pidió.")
        if siblings:
            self._edit_description(MULTI_FAMILY, siblings[0], "Una hermana tampoco se pidió.")
        before = self.workspace.family_bytes()
        code, output = self.workspace.run(f"--only-definitions={self.definition_id}")
        self.assertEqual(code, 0, output)
        self.assertIn("mode=scoped", output)
        after = self.workspace.family_bytes()
        self.assertEqual(before[f"{OTHER_FAMILY}.json"], after[f"{OTHER_FAMILY}.json"], "a family that owns no requested definition must stay untouched")
        family = self.workspace.family(MULTI_FAMILY)["family"]
        by_id = {item["id"]: item for item in family["definitions"]}
        self.assertEqual(by_id[self.definition_id]["description"], "Solo esta definición debe cambiar.")
        if siblings:
            original = {item["id"]: item for item in self.payload["family"]["definitions"]}
            self.assertEqual(by_id[siblings[0]]["description"], original[siblings[0]]["description"])

    def test_preflight_failure_blocks_every_write(self) -> None:
        self._edit_description(MULTI_FAMILY, self.definition_id, "Texto válido que no debe escribirse.")
        broken = self.workspace.ficha(OTHER_FAMILY)
        first_definition = next(iter(broken["definitions"].values()))
        first_definition["public"]["configurations"].popitem()
        self.workspace.write_ficha(OTHER_FAMILY, broken)
        before = self.workspace.family_bytes()
        with self.assertRaises(SystemExit) as raised:
            self.workspace.run()
        self.assertIn("preflight failed", str(raised.exception))
        self.assertIn("nothing was written", str(raised.exception))
        self.assertEqual(before, self.workspace.family_bytes())

    def test_a_missing_ficha_blocks_the_run(self) -> None:
        (self.workspace.fichas / f"{OTHER_FAMILY}.json").unlink()
        with self.assertRaises(SystemExit) as raised:
            self.workspace.run()
        self.assertIn(f"{OTHER_FAMILY}: no ficha", str(raised.exception))

    def _assert_rejected_without_writing(self, expected: str, *argv: str) -> None:
        before = self.workspace.family_bytes()
        with self.assertRaises(SystemExit) as raised:
            self.workspace.run(*argv)
        self.assertIn(expected, str(raised.exception))
        self.assertEqual(before, self.workspace.family_bytes(), "a rejected run must not write anything")

    def test_unknown_duplicated_and_malformed_selections_fail_before_any_write(self) -> None:
        # A pending, valid edit proves the rejection also protects work that would otherwise be applied.
        self._edit_description(MULTI_FAMILY, self.definition_id, "Edición válida pendiente de aplicar.")
        self._assert_rejected_without_writing("unknown definition id", "--only-definitions=does_not_exist")
        self._assert_rejected_without_writing("unknown definition id", f"--only-definitions={self.definition_id},does_not_exist")
        self._assert_rejected_without_writing("duplicate definition ids", f"--only-definitions={self.definition_id},{self.definition_id}")
        self._assert_rejected_without_writing("non-empty", f"--only-definitions={self.definition_id},")
        self._assert_rejected_without_writing("non-empty", "--only-definitions=")
        self._assert_rejected_without_writing("non-empty", "--only-definitions=,,")

    def test_scoped_mode_tolerates_spaces_around_ids(self) -> None:
        self._edit_description(MULTI_FAMILY, self.definition_id, "Edición con ids rodeados de espacios.")
        code, output = self.workspace.run(f"--only-definitions= {self.definition_id} ")
        self.assertEqual(code, 0, output)
        self.assertIn(f"definition={self.definition_id}", output)
        definition = next(item for item in self.workspace.family(MULTI_FAMILY)["family"]["definitions"] if item["id"] == self.definition_id)
        self.assertEqual(definition["description"], "Edición con ids rodeados de espacios.")

    def test_scoped_mode_can_span_two_families(self) -> None:
        other_definition = self.workspace.family(OTHER_FAMILY)["family"]["definitions"][0]["id"]
        siblings = [item["id"] for item in self.payload["family"]["definitions"] if item["id"] != self.definition_id]
        self._edit_description(MULTI_FAMILY, self.definition_id, "Primera familia aplicada.")
        self._edit_description(OTHER_FAMILY, other_definition, "Segunda familia aplicada.")
        if siblings:
            self._edit_description(MULTI_FAMILY, siblings[0], "Hermana no pedida: no debe aplicarse.")
        code, output = self.workspace.run(f"--only-definitions={self.definition_id},{other_definition}")
        self.assertEqual(code, 0, output)
        self.assertIn("changed=2", output)
        first = {item["id"]: item for item in self.workspace.family(MULTI_FAMILY)["family"]["definitions"]}
        second = {item["id"]: item for item in self.workspace.family(OTHER_FAMILY)["family"]["definitions"]}
        self.assertEqual(first[self.definition_id]["description"], "Primera familia aplicada.")
        self.assertEqual(second[other_definition]["description"], "Segunda familia aplicada.")
        if siblings:
            original = {item["id"]: item for item in self.payload["family"]["definitions"]}
            self.assertEqual(first[siblings[0]]["description"], original[siblings[0]]["description"])

    def test_an_invalid_json_ficha_fails_before_any_write(self) -> None:
        self._edit_description(MULTI_FAMILY, self.definition_id, "Edición válida pendiente de aplicar.")
        (self.workspace.fichas / f"{OTHER_FAMILY}.json").write_text("{ no es json", encoding="utf-8")
        self._assert_rejected_without_writing("invalid JSON", "--check")
        self._assert_rejected_without_writing("invalid JSON")

    def test_a_definition_id_present_in_two_families_cannot_be_selected(self) -> None:
        clone_id = "clone_family"
        clone = copy.deepcopy(self.payload)
        clone["family"]["id"] = clone_id
        (self.workspace.families / f"{clone_id}.json").write_bytes(apply_fichas.canonical_json(clone))
        clone_ficha = self.workspace.ficha(MULTI_FAMILY)
        clone_ficha["familyId"] = clone_id
        self.workspace.write_ficha(clone_id, clone_ficha)
        self._assert_rejected_without_writing("not unique across families", f"--only-definitions={self.definition_id}")
        # Selecting a definition that is unique still works.
        unique = self.workspace.family(OTHER_FAMILY)["family"]["definitions"][0]["id"]
        self.assertEqual(self.workspace.run(f"--only-definitions={unique}")[0], 0)


class CuratedAnatomyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workspace = Workspace(self, [MULTI_FAMILY])
        self.original = self.workspace.family(MULTI_FAMILY)
        self.definition_id = first_definition_id(self.original)
        self.definition = next(item for item in self.original["family"]["definitions"] if item["id"] == self.definition_id)
        ficha = self.workspace.ficha(MULTI_FAMILY)
        ficha["definitions"][self.definition_id] = curated_body(self.original, self.definition_id, ficha)
        self.ficha = ficha
        self.override_id = self.definition["configurations"][0]["id"]
        self.plain_id = self.definition["configurations"][1]["id"]

    def _apply(self) -> dict[str, Any]:
        self.workspace.write_ficha(MULTI_FAMILY, self.ficha)
        code, output = self.workspace.run()
        self.assertEqual(code, 0, output)
        family = self.workspace.family(MULTI_FAMILY)["family"]
        definition = next(item for item in family["definitions"] if item["id"] == self.definition_id)
        return {item["id"]: item["profile"] for item in definition["configurations"]}

    def test_base_anatomy_reaches_every_configuration_without_an_override(self) -> None:
        profile = self._apply()[self.plain_id]
        self.assertEqual(profile["primaryMuscles"], ["pectoralis"])
        self.assertEqual(profile["secondaryMuscles"], ["triceps", "deltoid"])
        self.assertEqual(profile["stabilizerMuscles"], ["core"])
        self.assertEqual([item["jointId"] for item in profile["jointInvolvement"]], ["glenohumeral", "codo"])

    def test_override_replaces_adds_and_removes_entries(self) -> None:
        profile = self._apply()[self.override_id]
        # triceps removed, deltoid promoted in place, latissimus appended as stabilizer.
        self.assertEqual(profile["primaryMuscles"], ["pectoralis", "deltoid"])
        self.assertEqual(profile["secondaryMuscles"], [])
        self.assertEqual(profile["stabilizerMuscles"], ["core", "latissimus_dorsi"])
        self.assertEqual([item["jointId"] for item in profile["jointInvolvement"]], ["glenohumeral", "muñeca"])

    def test_every_mirror_is_derived_with_the_shared_functions(self) -> None:
        for configuration_id, profile in self._apply().items():
            with self.subTest(configuration=configuration_id):
                rich = profile["richMetadata"]
                for field_name in ("primaryMuscles", "secondaryMuscles", "stabilizerMuscles"):
                    self.assertEqual(rich["anatomy"][field_name], profile[field_name])
                self.assertEqual(rich["anatomy"]["jointInvolvement"], profile["jointInvolvement"])
                self.assertEqual(rich["anatomy"]["jointActions"], derived.derive_joint_actions(profile["jointInvolvement"]))
                self.assertEqual(rich["biomechanics"]["relevantJoints"], derived.derive_relevant_joints(profile["jointInvolvement"]))
                self.assertEqual(
                    rich["replacement"]["preservesIntent"],
                    derived.derive_preserves_intent(profile["movementPatternId"], profile["primaryMuscles"]),
                )
                self.assertEqual(rich["biomechanics"]["movementPatternId"], profile["movementPatternId"])

    def test_joint_actions_are_deduplicated_in_joint_order(self) -> None:
        profile = self._apply()[self.plain_id]
        # "Rotación interna" is declared by glenohumeral and codo but must appear once.
        self.assertEqual(profile["richMetadata"]["anatomy"]["jointActions"], ["Aducción horizontal", "Rotación interna", "Extensión"])

    def test_a_pattern_declaration_moves_both_mirrors_and_the_intent_tokens(self) -> None:
        current = self.definition["configurations"][0]["profile"]["movementPatternId"]
        target = sorted(MOVEMENT_PATTERN_IDS - {current})[0]
        self.ficha["definitions"][self.definition_id]["pattern"] = {"movementPatternId": target, "why": "fixture"}
        for configuration_id, profile in self._apply().items():
            with self.subTest(configuration=configuration_id):
                self.assertEqual(profile["movementPatternId"], target)
                self.assertEqual(profile["richMetadata"]["biomechanics"]["movementPatternId"], target)
                self.assertEqual(profile["richMetadata"]["replacement"]["preservesIntent"], [f"{target}:{muscle}" for muscle in profile["primaryMuscles"]])

    def test_an_applied_ficha_touches_nothing_outside_the_authored_surface(self) -> None:
        self.ficha["definitions"][self.definition_id]["pattern"] = {"movementPatternId": sorted(MOVEMENT_PATTERN_IDS)[0]}
        self._apply()
        updated = self.workspace.family(MULTI_FAMILY)
        paths = apply_fichas.changed_paths(self.original, updated)
        self.assertTrue(paths, "the curated ficha must change something")
        stray = [path for path in paths if not ALLOWED_PATH.match(path)]
        self.assertEqual(stray, [], "ids, options, evidence, fatigue and revisions must be untouched")

    def test_curated_apply_is_idempotent(self) -> None:
        self._apply()
        snapshot = self.workspace.family_bytes()
        code, output = self.workspace.run()
        self.assertEqual(code, 0)
        self.assertIn("changed=0", output)
        self.assertEqual(snapshot, self.workspace.family_bytes())
        self.assertEqual(self.workspace.run("--check")[0], 0)

    def test_a_legacy_definition_never_receives_anatomy(self) -> None:
        sibling_ids = [item["id"] for item in self.original["family"]["definitions"] if item["id"] != self.definition_id]
        self._apply()
        updated = {item["id"]: item for item in self.workspace.family(MULTI_FAMILY)["family"]["definitions"]}
        original = {item["id"]: item for item in self.original["family"]["definitions"]}
        for sibling_id in sibling_ids:
            self.assertEqual(updated[sibling_id], original[sibling_id])


class ValidationTest(unittest.TestCase):
    """Each malformed ficha is rejected before anything could be written."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.payload = read_json(apply_fichas.FAMILIES / f"{MULTI_FAMILY}.json")
        cls.definition_id = first_definition_id(cls.payload)
        cls.definition = next(item for item in cls.payload["family"]["definitions"] if item["id"] == cls.definition_id)
        cls.ficha = read_json(apply_fichas.FICHAS / f"{MULTI_FAMILY}.json")
        cls.base_ficha = copy.deepcopy(cls.ficha)
        cls.base_ficha["definitions"][cls.definition_id] = curated_body(cls.payload, cls.definition_id, cls.ficha)

    def _errors(self, mutate: Callable[[dict[str, Any]], None]) -> list[str]:
        ficha = copy.deepcopy(self.base_ficha)
        mutate(ficha["definitions"][self.definition_id])
        return apply_fichas.validate_ficha(self.payload, ficha)

    def test_the_unmodified_fixture_is_valid(self) -> None:
        self.assertEqual(apply_fichas.validate_ficha(self.payload, self.base_ficha), [])

    def test_each_defect_is_reported(self) -> None:
        first_config = self.definition["configurations"][0]["id"]
        first_public = lambda body: body["public"]["configurations"][first_config]  # noqa: E731
        cases: dict[str, tuple[Callable[[dict[str, Any]], None], str]] = {
            "unknown muscle": (lambda b: b["anatomy"]["muscles"].append({"id": "biceps_femoris", "role": "PRIMARY"}), "outside the ontology"),
            "unknown joint": (lambda b: b["anatomy"]["joints"].append({"id": "ankle", "role": "PRIMARY", "actions": ["x"]}), "outside the ontology"),
            "invalid role": (lambda b: b["anatomy"]["muscles"][0].update(role="LEADER"), "invalid role"),
            "repeated muscle": (lambda b: b["anatomy"]["muscles"].append(dict(b["anatomy"]["muscles"][0])), "repeated id"),
            "joint without actions": (lambda b: b["anatomy"]["joints"][0].update(actions=[]), "non-empty actions"),
            "no primary muscle": (lambda b: [m.update(role="SECONDARY") for m in b["anatomy"]["muscles"]], "no PRIMARY muscle"),
            "no joint": (lambda b: b["anatomy"].update(joints=[]), "anatomy.joints must be a non-empty list"),
            "override on unknown configuration": (lambda b: b["anatomy"]["overrides"].update({"nope__x": {"muscles": []}}), "unknown configuration"),
            "override removes an id the base lacks": (
                lambda b: b["anatomy"]["overrides"][first_config]["muscles"].append({"id": "calves", "role": "NONE"}),
                "does not list",
            ),
            "override with a stray key": (lambda b: b["anatomy"]["overrides"][first_config].update(notes=[]), "only accepts muscles and joints"),
            "curated without visual": (lambda b: b.pop("visual"), "requires visual"),
            "curated without sources": (lambda b: b.pop("sources"), "requires sources"),
            "curated without technique": (lambda b: b.pop("technique"), "requires technique"),
            "unknown pattern": (lambda b: b.update(pattern={"movementPatternId": "teleport"}), "outside the runtime patterns"),
            "blank definition description": (lambda b: b["public"].update(description="   "), "public.description"),
            "blank configuration description": (lambda b: first_public(b).update(description=""), "description must be non-empty"),
            "empty setup cues": (lambda b: first_public(b).update(setupCues=[]), "setupCues"),
            "blank execution cue": (lambda b: first_public(b).update(executionCues=["ok", " "]), "executionCues"),
            "unknown public key": (lambda b: first_public(b).update(benefits=["x"]), "unknown public keys"),
            "configuration missing from public": (lambda b: b["public"]["configurations"].pop(first_config), "inventory mismatch"),
            "configuration invented in public": (lambda b: b["public"]["configurations"].update({"ghost__x": dict(first_public(b))}), "inventory mismatch"),
            "invalid status": (lambda b: b.update(status="DONE"), "status must be one of"),
        }
        for label, (mutate, expected) in cases.items():
            with self.subTest(defect=label):
                errors = self._errors(mutate)
                self.assertTrue(any(expected in error for error in errors), f"{label}: {expected!r} not in {errors}")

    def test_a_legacy_ficha_rejects_curated_blocks(self) -> None:
        ficha = copy.deepcopy(self.base_ficha)
        ficha["definitions"][self.definition_id]["status"] = apply_fichas.STATUS_LEGACY
        errors = apply_fichas.validate_ficha(self.payload, ficha)
        self.assertTrue(any("LEGACY ficha does not accept" in error for error in errors), errors)

    def test_ficha_level_defects_are_reported(self) -> None:
        for label, mutate, expected in (
            ("schema", lambda f: f.update(schemaVersion=2), "schemaVersion"),
            ("family id", lambda f: f.update(familyId="other_family"), "familyId"),
            ("missing definition", lambda f: f["definitions"].pop(self.definition_id), "missing definitions"),
            ("extra definition", lambda f: f["definitions"].update(ghost=copy.deepcopy(f["definitions"][self.definition_id])), "unknown definitions"),
            ("stray key", lambda f: f.update(notes="x"), "unknown ficha keys"),
        ):
            with self.subTest(defect=label):
                ficha = copy.deepcopy(self.base_ficha)
                mutate(ficha)
                errors = apply_fichas.validate_ficha(self.payload, ficha)
                self.assertTrue(any(expected in error for error in errors), f"{label}: {errors}")

    def test_non_object_ficha_is_reported(self) -> None:
        self.assertTrue(apply_fichas.validate_ficha(self.payload, []))


class ChangedPathsTest(unittest.TestCase):
    def test_identical_values_report_nothing(self) -> None:
        value = {"a": [{"id": "x", "n": 1}], "b": "t"}
        self.assertEqual(apply_fichas.changed_paths(value, copy.deepcopy(value)), [])

    def test_leaf_paths_are_labelled_by_id(self) -> None:
        old = {"items": [{"id": "x", "n": 1}, {"id": "y", "n": 2}]}
        new = {"items": [{"id": "x", "n": 1}, {"id": "y", "n": 3}]}
        self.assertEqual(apply_fichas.changed_paths(old, new), ["items[y].n"])

    def test_length_and_key_differences_are_reported_at_the_container(self) -> None:
        self.assertEqual(apply_fichas.changed_paths({"a": [1]}, {"a": [1, 2]}), ["a"])
        self.assertEqual(apply_fichas.changed_paths({"a": 1}, {"b": 1}), ["a", "b"])


if __name__ == "__main__":  # pragma: no cover
    unittest.main()
