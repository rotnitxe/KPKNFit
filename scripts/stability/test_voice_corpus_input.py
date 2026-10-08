"""Synthetic validation fixtures; these are NOT field replay evidence."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from prepare_voice_corpus import prepare


class CorpusInputTest(unittest.TestCase):
    def setUp(self):
        workspace = Path(__file__).resolve().parents[2] / '.ci-private' / 'test-tmp'
        workspace.mkdir(parents=True, exist_ok=True)
        self.directory = tempfile.TemporaryDirectory(dir=workspace)
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        data = b'{"event":"synthetic_validation_fixture"}\n'
        (self.source / 'sample.jsonl').write_bytes(data)
        self.manifest = {'schemaVersion': 1, 'kind': 'REAL_FIELD_SANITIZED', 'consentForCi': True,
            'files': [{'path': 'sample.jsonl', 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}]}

    def run_prepare(self):
        (self.source / 'manifest.json').write_text(json.dumps(self.manifest), encoding='utf-8')
        return prepare(self.source, self.root / 'output')

    def test_verified_input_is_private_and_prints_metadata_only(self):
        result = self.run_prepare()
        self.assertEqual('INPUT_VALIDATION_ONLY', result['evidenceLevel'])
        self.assertEqual(1, result['files'])
        self.assertNotIn('event', result)

    def test_missing_consent_rejected(self):
        self.manifest['consentForCi'] = False
        with self.assertRaises(ValueError): self.run_prepare()

    def test_modified_file_rejected(self):
        (self.source / 'sample.jsonl').write_bytes(b'changed')
        with self.assertRaises(ValueError): self.run_prepare()

    def test_traversal_and_duplicates_rejected(self):
        self.manifest['files'][0]['path'] = '../sample.jsonl'
        with self.assertRaises(ValueError): self.run_prepare()
        self.manifest['files'][0]['path'] = 'sample.jsonl'
        self.manifest['files'].append(self.manifest['files'][0].copy())
        with self.assertRaises(ValueError): self.run_prepare()

    def test_synthetic_provenance_cannot_satisfy_field_input(self):
        self.manifest['kind'] = 'SYNTHETIC'
        with self.assertRaises(ValueError): self.run_prepare()


if __name__ == '__main__': unittest.main()
