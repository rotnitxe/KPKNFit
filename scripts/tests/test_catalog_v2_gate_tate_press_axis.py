#!/usr/bin/env python3
"""Regression tests for the `tate_press` axis roster and the ficha/source sync.

Every test drives the production gate (`scripts/catalog_v2_gate.py`) through its
real `source_gate()` entry point, so the assertions observe actual validation
results and never a stubbed return value. Negative cases substitute temporary
`SOURCE` / `FICHAS` paths, and the temporary fixtures are derived from the real
repository source and fichas (current truth) with one targeted mutation each,
instead of hard-coding production answers.

Scope notes that keep these tests durable:

* No test reads orchestration/execution artifacts under `.opencode`.
* No test asserts an absolute global failure count, so repairing unrelated
  families later cannot break this file.
* The historical `tate_press__cable` configuration is NOT part of the accepted
  identity. The generic validators must keep rejecting both a filler singleton
  axis and a reintroduced ficha entry for it.
* The ficha is the only authoring surface: a hand edit of the compiled source
  (without the matching ficha edit) must be caught as drift.
"""

from __future__ import annotations

import copy
import importlib.util
import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any, Callable


SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = SCRIPTS.parent
GATE = SCRIPTS / "catalog_v2_gate.py"
# Fixtures never touch the repository: temporary files can be redirected with
# KPKN_TEST_TEMP_DIR to keep runs inside an approved agent workspace on Windows.
TEMP_OVERRIDE = os.environ.get("KPKN_TEST_TEMP_DIR")

TATE_PRESS = "tate_press"
TATE_PRESS_FAMILY = "triceps_tate_press"
TATE_PRESS_DUMBBELLS = "tate_press__dumbbells"
TATE_PRESS_CABLE = "tate_press__cable"
PAUSED_BENCH_PRESS = "paused_bench_press"
PAUSED_BENCH_PRESS_FAMILY = "chest_press"
PAUSED_BENCH_PRESS_BARBELL = "paused_bench_press__barbell"
REVERSE_CURL = "reverse_curl"
KATANA_EXTENSION = "katana_extension"

# Failure prefixes that belong to the axis and ficha/source checks of this file.
WATCHED_PREFIXES = ("ficha_", "editorial_", "hierarchy_order", "singleton_axis")


def temporary_directory() -> tempfile.TemporaryDirectory:
    root = Path(TEMP_OVERRIDE) if TEMP_OVERRIDE and Path(TEMP_OVERRIDE).is_dir() else None
    return tempfile.TemporaryDirectory(dir=str(root) if root else None)


def load_gate():
    spec = importlib.util.spec_from_file_location("catalog_v2_gate_under_test", GATE)
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {GATE}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gate = load_gate()


def load_compiler_roster() -> dict[str, list[str]]:
    """The authoritative axis roster the compiler itself applies."""
    if str(SCRIPTS) not in sys.path:
        sys.path.insert(0, str(SCRIPTS))
    import catalog_v2_axis_order  # noqa: WPS433 - resolved from SCRIPTS

    return catalog_v2_axis_order.AXIS_ORDER_OVERRIDES


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def find_definition(source: dict[str, Any], family_id: str, definition_id: str) -> dict[str, Any]:
    for family in source["families"]:
        if family["id"] != family_id:
            continue
        for definition in family["definitions"]:
            if definition["id"] == definition_id:
                return definition
    raise AssertionError(f"{definition_id} not found in {family_id}")


class TatePressAxisGateTest(unittest.TestCase):
    """`tate_press` has no option axes and exactly one configuration."""

    def setUp(self) -> None:
        self._source_path = gate.SOURCE
        self._fichas_path = gate.FICHAS
        self.addCleanup(self._restore_paths)

    def _restore_paths(self) -> None:
        gate.SOURCE = self._source_path
        gate.FICHAS = self._fichas_path

    def _ficha(self, family_id: str) -> dict[str, Any]:
        return read_json(self._fichas_path / f"{family_id}.json")

    def _run_gate_against(
        self,
        mutate_source: Callable[[dict[str, Any]], None] | None = None,
        ficha_edits: dict[str, Callable[[dict[str, Any]], None]] | None = None,
        remove_fichas: tuple[str, ...] = (),
        add_files: dict[str, str] | None = None,
    ) -> list[str]:
        """Run the real `source_gate()` over temporary copies of the real source and fichas."""
        source = copy.deepcopy(read_json(self._source_path))
        if mutate_source is not None:
            mutate_source(source)
        with temporary_directory() as raw:
            workspace = Path(raw)
            source_file = workspace / "catalog_v2.json"
            fichas_dir = workspace / "fichas"
            source_file.write_text(json.dumps(source, ensure_ascii=False), encoding="utf-8")
            shutil.copytree(self._fichas_path, fichas_dir)
            for family_id, mutate in (ficha_edits or {}).items():
                path = fichas_dir / f"{family_id}.json"
                ficha = read_json(path)
                mutate(ficha)
                path.write_text(json.dumps(ficha, ensure_ascii=False), encoding="utf-8")
            for family_id in remove_fichas:
                (fichas_dir / f"{family_id}.json").unlink()
            for name, content in (add_files or {}).items():
                (fichas_dir / name).write_text(content, encoding="utf-8")
            gate.SOURCE = source_file
            gate.FICHAS = fichas_dir
            return gate.source_gate()

    # --- positive: the roster encodes the accepted identity -----------------

    def test_roster_value_matches_current_tate_press_axes(self) -> None:
        self.assertIn(TATE_PRESS, gate.EXPECTED_AXIS_ORDER)
        source_axes = find_definition(read_json(self._source_path), TATE_PRESS_FAMILY, TATE_PRESS)["optionAxes"]
        self.assertEqual(gate.EXPECTED_AXIS_ORDER[TATE_PRESS], source_axes)
        self.assertEqual(gate.EXPECTED_AXIS_ORDER[TATE_PRESS], [])

    def test_roster_agrees_with_compiler_overrides(self) -> None:
        overrides = load_compiler_roster()
        self.assertIn(TATE_PRESS, overrides)
        self.assertEqual(gate.EXPECTED_AXIS_ORDER[TATE_PRESS], overrides[TATE_PRESS])

    def test_current_tate_press_record_is_accepted(self) -> None:
        """Real files: `tate_press` must not fail any ficha or axis check."""
        related = [
            failure
            for failure in gate.source_gate()
            if failure.startswith(WATCHED_PREFIXES) and TATE_PRESS in failure
        ]
        self.assertEqual(related, [])

    def test_current_ficha_inventory_matches_source_for_tate_press(self) -> None:
        source_ids = [
            configuration["id"]
            for configuration in find_definition(read_json(self._source_path), TATE_PRESS_FAMILY, TATE_PRESS)["configurations"]
        ]
        ficha_ids = sorted(self._ficha(TATE_PRESS_FAMILY)["definitions"][TATE_PRESS]["public"]["configurations"])
        self.assertEqual(sorted(source_ids), ficha_ids)
        self.assertNotIn(TATE_PRESS_CABLE, ficha_ids)

    def test_ficha_public_copy_equals_current_source_for_the_synced_definitions(self) -> None:
        """The ficha is the authoring surface; the compiled source must carry its copy verbatim."""
        source = read_json(self._source_path)
        for family_id, definition_id in (
            (PAUSED_BENCH_PRESS_FAMILY, PAUSED_BENCH_PRESS),
            ("elbow_flexion_biceps_curl", REVERSE_CURL),
            ("triceps_katana_extension", KATANA_EXTENSION),
        ):
            with self.subTest(definition=definition_id):
                definition = find_definition(source, family_id, definition_id)
                public = self._ficha(family_id)["definitions"][definition_id]["public"]
                self.assertEqual(public["description"], definition["description"])
                for configuration in definition["configurations"]:
                    entry = public["configurations"][configuration["id"]]
                    for field in ("description", "setupCues", "executionCues"):
                        self.assertEqual(entry[field], configuration["profile"][field], f"{configuration['id']}.{field}")

    # --- negative: the generic validators must stay active ------------------

    def test_gate_still_rejects_singleton_axis_on_tate_press(self) -> None:
        def introduce_singleton(source: dict[str, Any]) -> None:
            definition = find_definition(source, TATE_PRESS_FAMILY, TATE_PRESS)
            definition["optionAxes"] = ["implement"]
            for configuration in definition["configurations"]:
                configuration["selectedOptions"] = {"implement": "dumbbells"}

        failures = self._run_gate_against(mutate_source=introduce_singleton)
        self.assertIn(f"singleton_axis:{TATE_PRESS}:implement", failures)

    def test_gate_still_rejects_hierarchy_drift_on_tate_press(self) -> None:
        def introduce_two_implements(source: dict[str, Any]) -> None:
            definition = find_definition(source, TATE_PRESS_FAMILY, TATE_PRESS)
            definition["optionAxes"] = ["implement"]
            original = definition["configurations"][0]
            original["selectedOptions"] = {"implement": "dumbbells"}
            second = copy.deepcopy(original)
            second["id"] = TATE_PRESS_CABLE
            second["displaySummary"] = "cable"
            second["selectedOptions"] = {"implement": "cable"}
            definition["configurations"] = [original, second]

        failures = self._run_gate_against(mutate_source=introduce_two_implements)
        self.assertIn(f"hierarchy_order:{TATE_PRESS}:['implement']", failures)
        self.assertNotIn(f"singleton_axis:{TATE_PRESS}:implement", failures)

    def test_gate_still_rejects_a_reintroduced_cable_ficha_entry(self) -> None:
        def reintroduce_cable(ficha: dict[str, Any]) -> None:
            configurations = ficha["definitions"][TATE_PRESS]["public"]["configurations"]
            stale = copy.deepcopy(configurations[TATE_PRESS_DUMBBELLS])
            stale["description"] = (
                "Con polea, con las palmas al frente y los codos en abanico, la carga "
                "empuja desde el pecho mezclando press y extensión."
            )
            configurations[TATE_PRESS_CABLE] = stale

        failures = self._run_gate_against(ficha_edits={TATE_PRESS_FAMILY: reintroduce_cable})
        expected = f"ficha_invalid:{TATE_PRESS}: configuration inventory mismatch (missing [], extra ['{TATE_PRESS_CABLE}'])"
        self.assertIn(expected, failures)

    def test_gate_rejects_a_ficha_that_drifted_from_the_source(self) -> None:
        """A ficha edit that was never applied must keep failing the byte comparison."""

        def drift_description(ficha: dict[str, Any]) -> None:
            ficha["definitions"][PAUSED_BENCH_PRESS]["public"]["description"] += " "

        failures = self._run_gate_against(ficha_edits={PAUSED_BENCH_PRESS_FAMILY: drift_description})
        self.assertIn(f"ficha_drift:{PAUSED_BENCH_PRESS_FAMILY}:family.definitions[{PAUSED_BENCH_PRESS}].description", failures)

    def test_gate_rejects_a_hand_edited_source(self) -> None:
        """Editing the compiled source without the ficha must not survive: the ficha is the truth."""

        def hand_edit(source: dict[str, Any]) -> None:
            find_definition(source, PAUSED_BENCH_PRESS_FAMILY, PAUSED_BENCH_PRESS)["description"] += " Edición manual."

        failures = self._run_gate_against(mutate_source=hand_edit)
        self.assertIn(f"ficha_drift:{PAUSED_BENCH_PRESS_FAMILY}:family.definitions[{PAUSED_BENCH_PRESS}].description", failures)

    def test_gate_rejects_a_missing_orphan_and_malformed_ficha(self) -> None:
        failures = self._run_gate_against(
            remove_fichas=(TATE_PRESS_FAMILY,),
            add_files={"ghost_family.json": "{}", f"{PAUSED_BENCH_PRESS_FAMILY}.json": "{ not json"},
        )
        self.assertIn(f"ficha_missing:{TATE_PRESS_FAMILY}", failures)
        self.assertIn("ficha_orphan:ghost_family.json", failures)
        self.assertTrue(any(failure.startswith(f"ficha_invalid_json:{PAUSED_BENCH_PRESS_FAMILY}:") for failure in failures), failures)

    # --- scoped: later repairs of other families must not break this file ----

    def test_no_ficha_or_axis_failure_for_the_synced_definitions(self) -> None:
        """Scoped by failure prefix and id on purpose: it must not depend on the unrelated global failure count."""
        watched = (PAUSED_BENCH_PRESS, PAUSED_BENCH_PRESS_BARBELL, REVERSE_CURL, KATANA_EXTENSION, TATE_PRESS, TATE_PRESS_CABLE)
        offending = sorted(
            failure
            for failure in set(gate.source_gate())
            if failure.startswith(WATCHED_PREFIXES) and any(token in failure for token in watched)
        )
        self.assertEqual(offending, [])


if __name__ == "__main__":  # pragma: no cover
    unittest.main()
