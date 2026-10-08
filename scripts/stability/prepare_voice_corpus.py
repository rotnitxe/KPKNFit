"""Validate an explicitly supplied private field corpus without printing its text."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil

MAX_BYTES = 64 * 1024 * 1024


def prepare(source: Path, output: Path):
    source, output = source.resolve(), output.resolve()
    manifest_path = source / 'manifest.json'
    if manifest_path.stat().st_size > 1024 * 1024:
        raise ValueError('Corpus manifest exceeds limit')
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    if manifest.get('schemaVersion') != 1 or manifest.get('kind') != 'REAL_FIELD_SANITIZED' or manifest.get('consentForCi') is not True:
        raise ValueError('Explicit field provenance and CI consent are required')
    entries = manifest.get('files', [])
    if not 1 <= len(entries) <= 100:
        raise ValueError('Missing or excessive corpus files')
    verified = []
    total = 0
    names = set()
    for entry in entries:
        name = entry['path']
        if not isinstance(name, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,120}\.jsonl', name) or Path(name).name != name or name in names:
            raise ValueError('Invalid corpus file identity')
        names.add(name)
        file = source / name
        if file.is_symlink() or file.resolve().parent != source:
            raise ValueError('Linked corpus files are not accepted')
        size = file.stat().st_size
        total += size
        if size <= 0 or size != entry['bytes'] or total > MAX_BYTES:
            raise ValueError('Corpus size mismatch or limit exceeded')
        digest = hashlib.sha256()
        with file.open('rb') as stream:
            for block in iter(lambda: stream.read(65536), b''):
                digest.update(block)
        if digest.hexdigest() != entry['sha256']:
            raise ValueError('Corpus integrity check failed')
        verified.append(file)
    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        raise ValueError('Corpus destination must be empty')
    for file in verified:
        destination = output / file.name
        shutil.copyfile(file, destination)
        if hashlib.sha256(destination.read_bytes()).hexdigest() != next(entry['sha256'] for entry in entries if entry['path'] == file.name):
            raise ValueError('Corpus changed during staging')
    return {'files': len(verified), 'bytes': total,
            'manifestSha256': hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
            'evidenceLevel': 'INPUT_VALIDATION_ONLY'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    destination = Path(args.output).resolve()
    if not destination.is_relative_to(root / '.ci-private'):
        raise SystemExit('Private corpus output must stay under .ci-private in this checkout')
    try:
        print(json.dumps(prepare(Path(args.source), destination)))
    except (ValueError, OSError, KeyError, TypeError):
        raise SystemExit('Authorized corpus unavailable or invalid; no transcripts were logged') from None


if __name__ == '__main__':
    main()
