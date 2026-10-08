"""Negative probes for the release storage classification gate."""
import json
from pathlib import Path
import tempfile
import unittest
from contextlib import contextmanager
from verify_storage_inventory import APP, ROOT, verify


@contextmanager
def owned_temporary_directory():
    root = (ROOT / 'artifacts/stability-launch/.test-tmp').resolve()
    assert root.is_relative_to(ROOT.resolve())
    root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='kpkn-storage-gate-', dir=root) as temporary:
        assert Path(temporary).resolve().parent == root
        yield temporary


class StorageInventoryGateTest(unittest.TestCase):
    def test_current_schema_and_private_roots_are_classified(self):
        result = verify()
        self.assertEqual('PASS', result['status'], result['errors'])
        self.assertTrue(result['tables'])
        self.assertTrue(result['storageCalls'])

    def test_new_table_blocks_validation(self):
        with owned_temporary_directory() as temporary:
            schema = json.loads((APP / 'schemas/com.example.kpkn.data.db.KpknDatabase/30.json').read_text(encoding='utf-8'))
            schema['database']['entities'].append({'tableName': 'unclassified_personal_records'})
            fixture = Path(temporary) / 'schema.json'
            fixture.write_text(json.dumps(schema), encoding='utf-8')
            result = verify(schema=fixture)
            self.assertEqual('FAIL', result['status'])
            self.assertTrue(any('unclassified_personal_records' in error for error in result['errors']))

    def test_new_file_root_or_dynamic_binding_blocks_validation(self):
        for expression in ('filesDir, "unclassified-personal-files"',
                           'noBackupFilesDir, "unclassified-personal-files"',
                           'filesDir, runtimePath'):
            with self.subTest(expression=expression), owned_temporary_directory() as temporary:
                source = Path(temporary)
                (source / 'NewWriter.kt').write_text(f'val output = File(context.{expression})', encoding='utf-8')
                result = verify(source=source)
                self.assertEqual('FAIL', result['status'])
                self.assertTrue(any('Unclassified private root: NewWriter.kt' in error for error in result['errors']))


if __name__ == '__main__':
    unittest.main()
