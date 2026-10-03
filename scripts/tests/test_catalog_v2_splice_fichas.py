#!/usr/bin/env python3
"""Private ficha copies for parallel authors and the guarded splice back (scripts/catalog_v2_splice_fichas.py)."""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]


def load(name: str):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / f"{name}.py")
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {name}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


splice = load("catalog_v2_splice_fichas")

FICHA = {
    "schemaVersion": 1,
    "familyId": "fam",
    "definitions": {
        "alpha": {"status": "LEGACY", "public": {"description": "Alpha heredada.", "configurations": {}}},
        "beta": {"status": "LEGACY", "public": {"description": "Beta heredada.", "configurations": {}}},
    },
}


def quiet(function, *args, **kwargs):
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        return function(*args, **kwargs)


class SpliceTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory(prefix="kpkn-splice-")
        root = Path(self._tmp.name)
        self.shared, self.private = root / "shared", root / "private"
        self.shared.mkdir()
        # Compact on purpose: a splice rewrites the touched file as canonical JSON.
        (self.shared / "fam.json").write_text(json.dumps(FICHA, ensure_ascii=False), encoding="utf-8")
        splice.snapshot(self.shared, self.private)

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def edit_private(self, definition_id: str, description: str) -> None:
        path = self.private / "fam.json"
        ficha = json.loads(path.read_text(encoding="utf-8"))
        ficha["definitions"][definition_id]["public"]["description"] = description
        path.write_text(json.dumps(ficha, ensure_ascii=False), encoding="utf-8")

    def shared_ficha(self) -> dict:
        return json.loads((self.shared / "fam.json").read_text(encoding="utf-8"))

    def test_snapshot_copies_every_ficha_and_records_the_base(self) -> None:
        self.assertTrue((self.private / "fam.json").is_file())
        base = json.loads((self.private / splice.BASE_NAME).read_text(encoding="utf-8"))["definitions"]
        self.assertEqual({"alpha", "beta"}, set(base))
        self.assertEqual(splice.body_hash(FICHA["definitions"]["alpha"]), base["alpha"]["sha256"])

    def test_splice_copies_only_the_requested_bodies_as_canonical_json(self) -> None:
        self.edit_private("alpha", "Alpha curada.")
        self.edit_private("beta", "Beta en curso.")
        self.assertEqual(["alpha"], splice.splice(self.shared, self.private, ["alpha"]))
        text = (self.shared / "fam.json").read_text(encoding="utf-8")
        self.assertEqual(splice.canonical_text(json.loads(text)), text)
        self.assertEqual("Alpha curada.", self.shared_ficha()["definitions"]["alpha"]["public"]["description"])
        self.assertEqual("Beta heredada.", self.shared_ficha()["definitions"]["beta"]["public"]["description"])

    def test_a_shared_body_edited_after_the_snapshot_blocks_the_splice(self) -> None:
        self.edit_private("alpha", "Alpha curada.")
        concurrent = self.shared_ficha()
        concurrent["definitions"]["alpha"]["public"]["description"] = "Editada por otra persona."
        (self.shared / "fam.json").write_text(json.dumps(concurrent, ensure_ascii=False), encoding="utf-8")
        before = (self.shared / "fam.json").read_text(encoding="utf-8")
        with self.assertRaises(splice.SpliceError):
            splice.splice(self.shared, self.private, ["alpha"])
        self.assertEqual(before, (self.shared / "fam.json").read_text(encoding="utf-8"))
        self.assertEqual(["alpha"], splice.splice(self.shared, self.private, ["alpha"], force=True))

    def test_check_mode_writes_nothing(self) -> None:
        self.edit_private("alpha", "Alpha curada.")
        before = (self.shared / "fam.json").read_text(encoding="utf-8")
        self.assertEqual(["alpha"], splice.splice(self.shared, self.private, ["alpha"], check=True))
        self.assertEqual(before, (self.shared / "fam.json").read_text(encoding="utf-8"))

    def test_unknown_definition_and_reused_snapshot_directory_are_refused(self) -> None:
        with self.assertRaises(splice.SpliceError):
            splice.splice(self.shared, self.private, ["gamma"])
        with self.assertRaises(splice.SpliceError):
            splice.snapshot(self.shared, self.private)
        self.assertEqual(1, splice.snapshot(self.shared, self.private, force=True))

    def test_cli_exit_codes(self) -> None:
        self.edit_private("alpha", "Alpha curada.")
        code = quiet(splice.main, ["splice", "--from", str(self.private), "--definitions", "gamma"], fichas_dir=self.shared)
        self.assertEqual(2, code)
        code = quiet(splice.main, ["splice", "--from", str(self.private), "--definitions", "alpha"], fichas_dir=self.shared)
        self.assertEqual(0, code)

    def test_into_gathers_a_private_copy_into_an_integration_copy(self) -> None:
        integration = Path(self._tmp.name) / "integration"
        self.assertEqual(0, quiet(splice.main, ["snapshot", "--to", str(integration), "--into", str(self.shared)]))
        self.edit_private("alpha", "Alpha curada.")
        code = quiet(splice.main, ["splice", "--from", str(self.private), "--definitions", "alpha", "--into", str(integration)])
        self.assertEqual(0, code)
        merged = json.loads((integration / "fam.json").read_text(encoding="utf-8"))
        self.assertEqual("Alpha curada.", merged["definitions"]["alpha"]["public"]["description"])
        self.assertEqual("Alpha heredada.", self.shared_ficha()["definitions"]["alpha"]["public"]["description"])


class LoteReportSmokeTest(unittest.TestCase):
    """The lote report reads the live catalogue: a landed CURATED definition shows no delta."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.report_module = load("catalog_v2_lote_report")

    def test_a_landed_definition_has_no_anatomy_delta(self) -> None:
        module = self.report_module
        report = module.build_report(["seal_row"], module.apply_fichas.FICHAS)
        self.assertEqual({"seal_row__barbell", "seal_row__dumbbells"}, {row["configuration"] for row in report["rows"]})
        for row in report["rows"]:
            self.assertFalse(row["dominant"])
            self.assertEqual({}, row["muscles"])
            self.assertEqual({}, row["joints"])
        lexicon = json.loads(module.LEXICON.read_text(encoding="utf-8"))
        self.assertEqual([entry["id"] for entry in lexicon["budgets"]], [entry["id"] for entry in report["budgets"]])
        self.assertIn("# Informe de lote", module.render_markdown(report))

    def test_protected_configurations_come_from_android_code(self) -> None:
        literals = self.report_module.configuration_literals(self.report_module.ANDROID_MAIN)
        self.assertIn("bench_press__barbell", literals)

    def test_delta_flags_a_new_dominant_muscle(self) -> None:
        old = {"primaryMuscles": ["quadriceps", "gluteus_maximus"], "secondaryMuscles": [], "stabilizerMuscles": ["core"],
               "jointInvolvement": [{"jointId": "rodilla", "role": "PRIMARY", "actions": ["Extension"]}], "movementPatternId": "knee_dominant"}
        new = {"primaryMuscles": ["gluteus_maximus"], "secondaryMuscles": ["quadriceps"], "stabilizerMuscles": [],
               "jointInvolvement": [{"jointId": "cadera", "role": "PRIMARY", "actions": ["Extension"]}], "movementPatternId": "knee_dominant"}
        delta = self.report_module.configuration_delta(old, new)
        self.assertTrue(delta["dominant"])
        self.assertTrue(delta["primarySet"])
        self.assertEqual(("PRIMARY", "SECONDARY"), delta["muscles"]["quadriceps"])
        self.assertEqual(("STABILIZER", "-"), delta["muscles"]["core"])
        self.assertEqual({"cadera": ("-", "PRIMARY"), "rodilla": ("PRIMARY", "-")}, delta["joints"])


if __name__ == "__main__":
    unittest.main()
