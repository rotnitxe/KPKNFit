"""Fetch the Apache-2.0 English mobile Vosk model from its official distribution.

Outputs stay in the requested worktree staging folder; packaging is a separate step.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MODEL = 'vosk-model-small-en-us-0.15'
URL = f'https://alphacephei.com/vosk/models/{MODEL}.zip'
EXPECTED_SHA256 = '30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498'

def fetch(destination):
    destination = destination.resolve()
    if not destination.is_relative_to(ROOT / 'artifacts/stability-launch'):
        raise ValueError('Model preparation must stay in this worktree staging directory')
    destination.mkdir(parents=True, exist_ok=True)
    archive = destination / f'{MODEL}.zip'
    if not archive.exists():
        temporary = archive.with_suffix('.download')
        with urllib.request.urlopen(URL, timeout=30) as response, temporary.open('wb') as output:
            total = 0
            while block := response.read(256 * 1024):
                total += len(block)
                if total > 80 * 1024 * 1024: raise ValueError('Model archive exceeded its bounded size')
                output.write(block)
            output.flush(); os.fsync(output.fileno())
        temporary.replace(archive)
    checksum = hashlib.file_digest(archive.open('rb'), 'sha256').hexdigest()
    if checksum != EXPECTED_SHA256: raise ValueError('Official model differs from the reviewed archive')
    with zipfile.ZipFile(archive) as package:
        if len(package.infolist()) > 2000 or sum(entry.file_size for entry in package.infolist()) > 256 * 1024 * 1024:
            raise ValueError('Unexpected model archive size')
        for entry in package.infolist():
            path = Path(entry.filename)
            if not entry.filename.startswith(MODEL + '/') or path.is_absolute() or '..' in path.parts or '\\' in entry.filename or (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError('Unexpected model archive path')
            target = (destination / path).resolve()
            if not target.is_relative_to(destination): raise ValueError('Unowned model output')
            if entry.is_dir(): target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with package.open(entry) as source, target.open('wb') as output:
                    total = 0
                    while block := source.read(256 * 1024):
                        total += len(block)
                        if total > entry.file_size: raise ValueError('Unexpected model entry size')
                        output.write(block)
                    output.flush(); os.fsync(output.fileno())
    report = {'model': MODEL, 'origin': URL, 'license': 'Apache-2.0', 'archiveSha256': checksum,
              'archiveBytes': archive.stat().st_size, 'packagedInApp': False, 'runtimeVerified': False}
    (destination / 'provenance.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))
    return destination / MODEL

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--install', action='store_true', help='Install the verified model into the app assets, outside an active build')
    args = parser.parse_args()
    staged = fetch(args.output)
    if args.install:
        target = (ROOT / 'android-native/app/src/main/assets/vosk' / MODEL).resolve()
        if not target.is_relative_to(ROOT / 'android-native/app/src/main/assets/vosk'):
            raise ValueError('Unowned asset target')
        shutil.copytree(staged, target, dirs_exist_ok=True)
        (target / 'uuid').write_text(EXPECTED_SHA256, encoding='utf-8')
