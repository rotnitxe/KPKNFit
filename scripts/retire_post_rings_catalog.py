#!/usr/bin/env python3
"""Idempotent, path-specific editorial migration; never edits generated assets.

Run from repository root, then compile_exercise_catalog_v2.py --write/--check.
The shared strip_retired preserves every other field, including exercise IDs,
technicalDifficulty, curation, selections and ring apparatus exercises.
"""
import json
from pathlib import Path
from catalog_v2_retired_fields import strip_retired

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'catalog/exercises/v2/source'

def main():
    manifest = json.loads((SOURCE / 'manifest.json').read_text())
    paths = [SOURCE / 'catalog_v2.json'] + [SOURCE / 'families' / name for name in manifest['families']]
    changed = 0
    for path in paths:
        original = json.loads(path.read_text())
        cleaned = strip_retired(original)
        if cleaned != original:
            path.write_text(json.dumps(cleaned, ensure_ascii=False, indent=2) + '\n')
            changed += 1
    print(f'Editorial files migrated: {changed}')

if __name__ == '__main__':
    main()
