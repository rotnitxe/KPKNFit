#!/usr/bin/env python3
"""Retire the two walking-lunge setups explicitly rejected by the product owner.

Dry-check defaults to live inputs and never writes them. --write requires a saved
hash plan. Compile, pin, native changes and validation are separate steps.
Ordinary forward/reverse lunges remain available with Smith and cable.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / 'scripts/catalog_v2_apply_fichas.py').is_file())
sys.path.insert(0, str(ROOT / 'scripts'))
import catalog_v2_apply_fichas as applier

DEFINITION = 'walking_lunge'
FAMILY_FILE = 'lower_walking_lunge.json'
TARGET_FILE = 'lower_forward_lunge.json'
REPLACEMENTS = {'walking_lunge__smith_machine': 'forward_lunge__smith_machine', 'walking_lunge__cable': 'forward_lunge__cable'}
RETIRED_IMPLEMENTS = {'smith_machine', 'cable'}
SURVIVORS = {'walking_lunge__barbell', 'walking_lunge__dumbbells', 'walking_lunge__kettlebell'}

class RetirementError(ValueError):
    pass

def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def transform_family(payload: dict[str, Any]) -> dict[str, Any]:
    result = copy.deepcopy(payload)
    definitions = [entry for entry in result['family']['definitions'] if entry['id'] == DEFINITION]
    if len(definitions) != 1:
        raise RetirementError('exactly one walking_lunge definition required')
    definition = definitions[0]
    ids = [entry['id'] for entry in definition['configurations']]
    if len(ids) != len(set(ids)) or set(ids) not in (SURVIVORS, SURVIVORS | set(REPLACEMENTS)):
        raise RetirementError('expected three survivors and either both or neither retired configurations')
    if definition['defaultConfigurationId'] not in SURVIVORS:
        raise RetirementError('retirement must preserve the existing surviving default')
    if definition['optionAxes'] != ['implement']:
        raise RetirementError('unexpected walking_lunge axes')
    for config in definition['configurations']:
        if config['selectedOptions'] != {'implement': config['profile']['equipmentId']}:
            raise RetirementError('configuration implement mismatch')
    definition['configurations'] = [entry for entry in definition['configurations'] if entry['id'] in SURVIVORS]
    return result

def transform_ficha(payload: dict[str, Any]) -> dict[str, Any]:
    result = copy.deepcopy(payload)
    body = result['definitions'][DEFINITION]
    configurations = body['public']['configurations']
    if set(configurations) not in (SURVIVORS, SURVIVORS | set(REPLACEMENTS)):
        raise RetirementError('ficha configurations must be complete before or after retirement')
    for key in REPLACEMENTS:
        configurations.pop(key, None)
        body.get('anatomy', {}).get('overrides', {}).pop(key, None)
        body.get('visual', {}).get('byVariant', {}).pop(key, None)
    for implement in RETIRED_IMPLEMENTS:
        body.get('visual', {}).get('byImplement', {}).pop(implement, None)
        body.get('visual', {}).get('promptCore', {}).pop(implement, None)
    return result

def validate_targets(payload: dict[str, Any]) -> dict[str, str]:
    definitions = [entry for entry in payload['family']['definitions'] if entry['id'] == 'forward_lunge']
    if len(definitions) != 1:
        raise RetirementError('forward_lunge replacement definition missing')
    configurations = {entry['id']: entry for entry in definitions[0]['configurations']}
    profiles = {}
    for retired, replacement in REPLACEMENTS.items():
        target = configurations.get(replacement)
        implement = retired.rsplit('__', 1)[1]
        if target is None or target['profile']['equipmentId'] != implement or target['profile']['laterality'] != 'UNILATERAL':
            raise RetirementError('replacement must exist with the same implement and unilateral support')
        profiles[retired] = target['profile']['performanceProfileId']
    return profiles

def prepare(families_dir: Path, fichas_dirs: list[Path]) -> tuple[dict[str, Any], dict[Path, bytes]]:
    target_path = (families_dir / TARGET_FILE).resolve()
    target_bytes = target_path.read_bytes()
    profiles = validate_targets(json.loads(target_bytes.decode('utf-8')))
    paths = [(families_dir / FAMILY_FILE, transform_family)] + [(directory / FAMILY_FILE, transform_ficha) for directory in fichas_dirs]
    if len({path.resolve() for path, _ in paths}) != len(paths):
        raise RetirementError('duplicate input path')
    records, outputs = [], {}
    for path, transform in paths:
        path = path.resolve()
        original = path.read_bytes()
        parsed = json.loads(original.decode('utf-8'))
        transformed = transform(parsed)
        output = original if parsed == transformed else applier.canonical_json(transformed)
        outputs[path] = output
        records.append({'path': str(path), 'beforeSha256': digest(original), 'afterSha256': digest(output), 'changed': original != output})
    return {'planVersion': 1, 'definitionId': DEFINITION, 'replacements': REPLACEMENTS, 'replacementProfiles': profiles,
            'replacementInput': {'path': str(target_path), 'sha256': digest(target_bytes)},
            'survivingIds': sorted(SURVIVORS), 'files': records}, outputs

def apply_checked(plan: dict[str, Any]) -> int:
    if plan.get('planVersion') != 1 or plan.get('definitionId') != DEFINITION or plan.get('replacements') != REPLACEMENTS or plan.get('survivingIds') != sorted(SURVIVORS):
        raise RetirementError('altered or unsupported retirement plan')
    guard = plan['replacementInput']
    target_path = Path(guard['path'])
    if target_path.name != TARGET_FILE or digest(target_path.read_bytes()) != guard['sha256']:
        raise RetirementError('replacement source changed; repeat dry check')
    if validate_targets(json.loads(target_path.read_text(encoding='utf-8'))) != plan['replacementProfiles']:
        raise RetirementError('replacement profiles changed')
    records = plan['files']
    if not records or len({entry['path'] for entry in records}) != len(records):
        raise RetirementError('empty or duplicate file plan')
    pending = []
    for index, record in enumerate(records):
        path = Path(record['path'])
        if path.name != FAMILY_FILE:
            raise RetirementError('unexpected target file')
        original = path.read_bytes()
        if digest(original) == record['afterSha256']:
            continue
        if digest(original) != record['beforeSha256']:
            raise RetirementError('input hash changed; repeat dry check')
        result = (transform_family if index == 0 else transform_ficha)(json.loads(original.decode('utf-8')))
        content = applier.canonical_json(result)
        if digest(content) != record['afterSha256']:
            raise RetirementError('output hash changed')
        pending.append((path, content))
    for path, content in pending:
        temporary = path.with_name('.' + path.name + '.retire-walking-tmp')
        temporary.write_bytes(content)
        temporary.replace(path)
    return len(pending)

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--families-dir', type=Path, default=applier.FAMILIES)
    parser.add_argument('--fichas-dir', type=Path, action='append')
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args(argv)
    try:
        if args.write:
            if args.fichas_dir or args.families_dir != applier.FAMILIES:
                raise RetirementError('--write uses only the paths in its checked plan')
            changed = apply_checked(json.loads(args.plan.read_text(encoding='utf-8')))
            print('Applied checked retirement: %s files, two retired mappings' % changed)
        else:
            plan, _ = prepare(args.families_dir, args.fichas_dir or [applier.FICHAS])
            if args.plan.resolve() in {Path(entry['path']).resolve() for entry in plan['files']}:
                raise RetirementError('plan must not overwrite an input')
            args.plan.parent.mkdir(parents=True, exist_ok=True)
            args.plan.write_bytes(applier.canonical_json(plan))
            print(json.dumps(plan, ensure_ascii=False, indent=2))
    except (RetirementError, OSError, KeyError, json.JSONDecodeError) as error:
        print(str(error), file=sys.stderr)
        return 2
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
