from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import catalog_v2_retire_romanian_sumo_unilateral as retirement


def family_fixture() -> dict:
    def definition(definition_id: str) -> dict:
        return {
            "id": definition_id,
            "defaultConfigurationId": f"{definition_id}__bilateral__barbell",
            "optionAxes": ["implement", "stance"],
            "other": {"anatomy": ["hamstrings", "gluteus_maximus"]},
            "configurations": [
                {
                    "id": f"{definition_id}__{stance}__{implement}",
                    "selectedOptions": {"implement": implement, "stance": stance},
                    "profile": {
                        "equipmentId": implement,
                        "laterality": stance.upper(),
                        "primaryMuscles": ["hamstrings", "gluteus_maximus"],
                        "richMetadata": {"display": {"selectedOptions": {"implement": implement, "stance": stance}}},
                    },
                }
                for implement in retirement.IMPLEMENTS
                for stance in ("bilateral", "unilateral")
            ],
        }
    return {"family": {"id": "hinge_rdl", "definitions": [definition(retirement.REPLACEMENT_DEFINITION), definition(retirement.DEFINITION)]}}


def ficha_fixture() -> dict:
    entries = {configuration_id: {"text": configuration_id} for configuration_id in retirement.SURVIVORS | set(retirement.REPLACEMENTS)}
    body = {
        "status": "CURATED",
        "public": {"configurations": copy.deepcopy(entries), "description": "Keep approved bilateral prose"},
        "anatomy": {"base": {"muscles": ["hamstrings"]}, "overrides": copy.deepcopy(entries)},
        "visual": {
            "byVariant": copy.deepcopy(entries),
            "byImplement": {implement: {"keep": True} for implement in retirement.IMPLEMENTS},
            "promptCore": {implement: "Keep prompt" for implement in retirement.IMPLEMENTS},
        },
    }
    return {"definitions": {retirement.DEFINITION: body, retirement.REPLACEMENT_DEFINITION: {"status": "CURATED", "keep": True}}}


class RetirementTest(unittest.TestCase):
    def test_only_rejected_variants_and_singleton_stance_are_removed(self) -> None:
        original = family_fixture()
        updated = retirement.transform_family(original)
        self.assertEqual(original["family"]["definitions"][0], updated["family"]["definitions"][0])
        before, after = original["family"]["definitions"][1], updated["family"]["definitions"][1]
        self.assertEqual(before["defaultConfigurationId"], after["defaultConfigurationId"])
        self.assertEqual(before["other"], after["other"])
        self.assertEqual(["implement"], after["optionAxes"])
        self.assertEqual(retirement.SURVIVORS, {configuration["id"] for configuration in after["configurations"]})
        for configuration in after["configurations"]:
            expected = copy.deepcopy(next(item for item in before["configurations"] if item["id"] == configuration["id"]))
            expected["selectedOptions"].pop("stance")
            expected["profile"]["richMetadata"]["display"]["selectedOptions"].pop("stance")
            self.assertEqual(expected, configuration)
        self.assertEqual(8, len(before["configurations"]))  # pure transform
        self.assertEqual(updated, retirement.transform_family(updated))

    def test_ficha_removes_all_variant_references_and_keeps_implement_content(self) -> None:
        original = ficha_fixture()
        updated = retirement.transform_ficha(original)
        before, after = (value["definitions"][retirement.DEFINITION] for value in (original, updated))
        for section in (after["public"]["configurations"], after["anatomy"]["overrides"], after["visual"]["byVariant"]):
            self.assertEqual(retirement.SURVIVORS, set(section))
        for key in ("byImplement", "promptCore"):
            self.assertEqual(before["visual"][key], after["visual"][key])
        self.assertEqual(before["anatomy"]["base"], after["anatomy"]["base"])
        self.assertEqual(original["definitions"][retirement.REPLACEMENT_DEFINITION], updated["definitions"][retirement.REPLACEMENT_DEFINITION])
        self.assertEqual(updated, retirement.transform_ficha(updated))

    def test_default_retirement_is_rejected(self) -> None:
        original = family_fixture()
        original["family"]["definitions"][1]["defaultConfigurationId"] = next(iter(retirement.REPLACEMENTS))
        with self.assertRaisesRegex(retirement.RetirementError, "bilateral default"):
            retirement.transform_family(original)

    def test_partial_or_unexpected_retirements_are_rejected(self) -> None:
        original = family_fixture()
        original["family"]["definitions"][1]["configurations"].pop()
        with self.assertRaisesRegex(retirement.RetirementError, "exactly the four"):
            retirement.transform_family(original)

    def test_replacement_must_exist_with_same_implement_and_unilateral_support(self) -> None:
        for field, value in (("equipmentId", "cable"), ("laterality", "BILATERAL")):
            with self.subTest(field=field):
                original = family_fixture()
                original["family"]["definitions"][0]["configurations"][1]["profile"][field] = value
                with self.assertRaisesRegex(retirement.RetirementError, "preserve implement"):
                    retirement.transform_family(original)

    def test_hash_plan_drycheck_apply_and_reapply(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            families, fichas = Path(temporary) / "families", Path(temporary) / "fichas"
            families.mkdir()
            fichas.mkdir()
            source, ficha = families / "hinge_rdl.json", fichas / "hinge_rdl.json"
            source.write_text(json.dumps(family_fixture()), encoding="utf-8")
            ficha.write_text(json.dumps(ficha_fixture()), encoding="utf-8")
            original = source.read_bytes(), ficha.read_bytes()
            plan, outputs = retirement.prepare(families, [fichas])
            self.assertEqual(original, (source.read_bytes(), ficha.read_bytes()))
            self.assertEqual(2, retirement.apply_checked(plan))
            self.assertEqual(outputs[source.resolve()], source.read_bytes())
            self.assertEqual(outputs[ficha.resolve()], ficha.read_bytes())
            self.assertEqual(0, retirement.apply_checked(plan))
            second, _ = retirement.prepare(families, [fichas])
            self.assertTrue(all(not record["changed"] for record in second["files"]))

    def test_any_input_drift_blocks_the_whole_batch_before_source_write(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            families, fichas = Path(temporary) / "families", Path(temporary) / "fichas"
            families.mkdir()
            fichas.mkdir()
            source, ficha = families / "hinge_rdl.json", fichas / "hinge_rdl.json"
            source.write_text(json.dumps(family_fixture()), encoding="utf-8")
            ficha.write_text(json.dumps(ficha_fixture()), encoding="utf-8")
            original_source = source.read_bytes()
            plan, _ = retirement.prepare(families, [fichas])
            ficha.write_text(ficha.read_text(encoding="utf-8") + "\n", encoding="utf-8")
            with self.assertRaisesRegex(retirement.RetirementError, "hash changed"):
                retirement.apply_checked(plan)
            self.assertEqual(original_source, source.read_bytes())

    def test_altered_output_hash_blocks_before_writing(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            families, fichas = Path(temporary) / "families", Path(temporary) / "fichas"
            families.mkdir()
            fichas.mkdir()
            source, ficha = families / "hinge_rdl.json", fichas / "hinge_rdl.json"
            source.write_text(json.dumps(family_fixture()), encoding="utf-8")
            ficha.write_text(json.dumps(ficha_fixture()), encoding="utf-8")
            original_source = source.read_bytes()
            plan, _ = retirement.prepare(families, [fichas])
            plan["files"][0]["afterSha256"] = "0" * 64
            with self.assertRaisesRegex(retirement.RetirementError, "output hash mismatch"):
                retirement.apply_checked(plan)
            self.assertEqual(original_source, source.read_bytes())


if __name__ == "__main__":
    unittest.main()
