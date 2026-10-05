"""Behavior and stale-plan tests for the retirement proposal; no live writes."""
import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('retire_walking', HERE.parent / 'catalog_v2_retire_walking_guided.py')
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)

def family():
    ids = sorted(r.SURVIVORS | set(r.REPLACEMENTS))
    configs = [{'id': key, 'selectedOptions': {'implement': key.rsplit('__', 1)[1]},
                'profile': {'equipmentId': key.rsplit('__', 1)[1], 'laterality': 'UNILATERAL', 'keep': [1, 2, 3]}} for key in ids]
    return {'family': {'definitions': [{'id': 'walking_lunge', 'optionAxes': ['implement'],
               'defaultConfigurationId': 'walking_lunge__dumbbells', 'configurations': configs, 'keep': 'name/revision/search terms'},
               {'id': 'unrelated', 'keep': True}]}, 'catalogRevision': 'fixed'}

def targets():
    return {'family': {'definitions': [{'id': 'forward_lunge', 'configurations': [
        {'id': key, 'profile': {'equipmentId': key.rsplit('__', 1)[1], 'laterality': 'UNILATERAL', 'performanceProfileId': key + '__zancada'}}
        for key in r.REPLACEMENTS.values()]}]}}

def ficha():
    entries = {key: {'keep': key} for key in r.SURVIVORS | set(r.REPLACEMENTS)}
    return {'definitions': {'walking_lunge': {'status': 'CURATED', 'public': {'description': 'Keep prose', 'configurations': copy.deepcopy(entries)},
        'anatomy': {'muscles': [{'keep': 'real anatomy'}], 'overrides': copy.deepcopy(entries)},
        'visual': {'byVariant': copy.deepcopy(entries), 'byImplement': {key: 'geometry' for key in ['barbell', 'dumbbells', 'kettlebell', 'smith_machine', 'cable']},
                   'promptCore': {key: 'prompt' for key in ['barbell', 'dumbbells', 'kettlebell', 'smith_machine', 'cable']}}},
        'unrelated': {'keep': True}}}

class WalkingRetirementTests(unittest.TestCase):
    def test_only_two_configurations_removed_survivors_unchanged_and_default_retained(self):
        original = family()
        updated = r.transform_family(original)
        before, after = original['family']['definitions'][0], updated['family']['definitions'][0]
        self.assertEqual([item for item in before['configurations'] if item['id'] in r.SURVIVORS], after['configurations'])
        self.assertEqual(before['defaultConfigurationId'], after['defaultConfigurationId'])
        self.assertEqual(original['family']['definitions'][1], updated['family']['definitions'][1])
        self.assertEqual(5, len(before['configurations']))
        self.assertEqual(updated, r.transform_family(updated))

    def test_ficha_removes_visual_implement_and_configuration_references(self):
        original = ficha()
        result = r.transform_ficha(original)
        body = result['definitions']['walking_lunge']
        self.assertEqual(r.SURVIVORS, set(body['public']['configurations']))
        for path in [body['anatomy']['overrides'], body['visual']['byVariant']]:
            self.assertEqual(r.SURVIVORS, set(path))
        for key in ['byImplement', 'promptCore']:
            self.assertEqual({'barbell', 'dumbbells', 'kettlebell'}, set(body['visual'][key]))
        self.assertEqual(original['definitions']['walking_lunge']['anatomy']['muscles'], body['anatomy']['muscles'])
        self.assertEqual(original['definitions']['unrelated'], result['definitions']['unrelated'])
        self.assertEqual(result, r.transform_ficha(result))

    def test_partial_retirement_or_retired_default_rejected(self):
        original = family()
        original['family']['definitions'][0]['configurations'].pop()
        with self.assertRaisesRegex(r.RetirementError, 'both or neither'):
            r.transform_family(original)
        original = family()
        original['family']['definitions'][0]['defaultConfigurationId'] = 'walking_lunge__cable'
        with self.assertRaisesRegex(r.RetirementError, 'default'):
            r.transform_family(original)

    def test_targets_exist_same_implement_and_unilateral(self):
        for field, value in [('equipmentId', 'dumbbells'), ('laterality', 'BILATERAL')]:
            with self.subTest(field=field):
                original = targets()
                original['family']['definitions'][0]['configurations'][0]['profile'][field] = value
                with self.assertRaisesRegex(r.RetirementError, 'same implement'):
                    r.validate_targets(original)

    def fixture(self, directory):
        directory = Path(directory)
        families, fichas = directory / 'families', directory / 'fichas'
        families.mkdir()
        fichas.mkdir()
        for path, value in [(families / r.FAMILY_FILE, family()), (families / r.TARGET_FILE, targets()), (fichas / r.FAMILY_FILE, ficha())]:
            path.write_text(json.dumps(value), encoding='utf-8')
        return families, fichas

    def test_dry_plan_write_and_idempotent_reapply(self):
        with tempfile.TemporaryDirectory() as directory:
            families, fichas = self.fixture(directory)
            before = (families / r.FAMILY_FILE).read_bytes()
            plan, outputs = r.prepare(families, [fichas])
            self.assertEqual(before, (families / r.FAMILY_FILE).read_bytes())
            self.assertEqual(2, r.apply_checked(plan))
            self.assertEqual(outputs[(families / r.FAMILY_FILE).resolve()], (families / r.FAMILY_FILE).read_bytes())
            self.assertEqual(0, r.apply_checked(plan))

    def test_ficha_drift_aborts_before_any_source_write(self):
        with tempfile.TemporaryDirectory() as directory:
            families, fichas = self.fixture(directory)
            plan, _ = r.prepare(families, [fichas])
            source_before = (families / r.FAMILY_FILE).read_bytes()
            with (fichas / r.FAMILY_FILE).open('a', encoding='utf-8') as stream:
                stream.write('\n')
            with self.assertRaisesRegex(r.RetirementError, 'input hash'):
                r.apply_checked(plan)
            self.assertEqual(source_before, (families / r.FAMILY_FILE).read_bytes())

    def test_target_drift_aborts_before_source_write(self):
        with tempfile.TemporaryDirectory() as directory:
            families, fichas = self.fixture(directory)
            plan, _ = r.prepare(families, [fichas])
            source_before = (families / r.FAMILY_FILE).read_bytes()
            with (families / r.TARGET_FILE).open('a', encoding='utf-8') as stream:
                stream.write('\n')
            with self.assertRaisesRegex(r.RetirementError, 'replacement source changed'):
                r.apply_checked(plan)
            self.assertEqual(source_before, (families / r.FAMILY_FILE).read_bytes())

    def test_tampered_output_aborts_before_write(self):
        with tempfile.TemporaryDirectory() as directory:
            families, fichas = self.fixture(directory)
            plan, _ = r.prepare(families, [fichas])
            source_before = (families / r.FAMILY_FILE).read_bytes()
            plan['files'][1]['afterSha256'] = '0' * 64
            with self.assertRaisesRegex(r.RetirementError, 'output hash'):
                r.apply_checked(plan)
            self.assertEqual(source_before, (families / r.FAMILY_FILE).read_bytes())

if __name__ == '__main__':
    unittest.main(verbosity=2)
