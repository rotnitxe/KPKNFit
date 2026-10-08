"""Fail CI on unclassified Room tables or direct private storage roots.

This is a source gate, not proof that every writer has been stopped. Dynamic
bindings are explicit reviewed call sites; new bindings fail until classified.
"""
from pathlib import Path
import argparse
import json
import re

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'android-native/app'
SOURCE = APP / 'src/main/java/com/example/kpkn'
STORAGE = SOURCE / 'data/backup/PersonalStorageManifest.kt'
BACKUP = SOURCE / 'data/backup/PortableBackup.kt'
TABLES = SOURCE / 'data/db/BackupTableManifest.kt'

# These names are resolved by the named caller's checked root/relative-path list.
# This list must not grow automatically from a scan result.
DYNAMIC_BINDINGS = {
    ('data/backup/PersonalStorageManifest.kt', 'filesDir', 'name'): 'personalFileRoots',
    ('data/backup/PersonalDataErasure.kt', 'filesDir', 'it'): 'personalFileRoots',
    ('data/backup/PortableBackup.kt', 'filesDir', 'root'): 'portableRoots',
    ('data/backup/PortableBackup.kt', 'filesDir', 'name'): 'portableRoots',
    ('data/backup/PortableBackup.kt', 'filesDir', 'relative'): 'owned portable relative path',
    ('data/sync/GuestImportCoordinator.kt', 'filesDir', 'name'): 'portableRoots',
    ('data/sync/GuestImportCoordinator.kt', 'filesDir', 'file.relativeTo'): 'owned portable relative path',
    ('data/diagnostics/KpknDiagnosticLogger.kt', 'filesDir', 'legacyName'): 'legacy diagnostic roots',
    ('data/diagnostics/KpknDiagnosticLogger.kt', 'filesDir', 'rootName'): 'legacy diagnostic roots',
    ('services/diagnostics/KpknDiagnosticStorage.kt', 'filesDir', 'name'): 'diagnostic root list',
    ('services/diagnostics/KpknDiagnosticStorage.kt', 'filesDir', 'KpknDiagnosticLogger.LOG_ROOT'): 'kpkn_logs',
}

FILE_CALL = re.compile(
    r'\bFile\s*\(\s*[\w.]*\b(filesDir|cacheDir|noBackupFilesDir)\s*,\s*'
    r'("(?:\\.|[^"\n])*"|[\w.]+)')
OPERATIONAL_NO_BACKUP = {
    'guest-import', 'maintenance', 'portable-stage', 'sync-media', 'sync-transfer',
    'pending-guest-import.json', 'owned-guest-sources.json', 'pending-profile-erasure.json',
}


def declared_set(text, name):
    match = re.search(r'\b' + re.escape(name) + r'\s*=\s*setOf\((.*?)\)', text, re.S)
    if not match:
        raise ValueError(f'Missing storage declaration: {name}')
    return set(re.findall(r'"([^"\n]+)"', match[1]))


def verify(source=SOURCE, schema=None):
    storage = STORAGE.read_text(encoding='utf-8')
    personal = declared_set(storage, 'personalFileRoots')
    regenerable = declared_set(storage, 'regenerableRoots')
    portable = declared_set(BACKUP.read_text(encoding='utf-8'), 'portableRoots')
    errors = []
    if personal & regenerable:
        errors.append('A root is both personal and regenerable')
    if portable - personal:
        errors.append(f'Portable roots absent from deletion: {sorted(portable - personal)}')

    database_text = (SOURCE / 'data/db/KpknDatabase.kt').read_text(encoding='utf-8')
    version = re.search(r'DATABASE_VERSION\s*=\s*(\d+)', database_text)[1]
    schema = schema or APP / f'schemas/com.example.kpkn.data.db.KpknDatabase/{version}.json'
    tables = {entity['tableName'] for entity in json.loads(schema.read_text(encoding='utf-8'))['database']['entities']}
    manifest = dict(re.findall(r'"([^"\n]+)"\s+to\s+Category\.(\w+)', TABLES.read_text(encoding='utf-8')))
    if tables != set(manifest):
        errors.append(f'Room classification mismatch: missing={sorted(tables - set(manifest))}, obsolete={sorted(set(manifest) - tables)}')

    roots = []
    for file in sorted(source.rglob('*.kt')):
        text = file.read_text(encoding='utf-8')
        constants = dict(re.findall(r'\bconst\s+val\s+(\w+)\s*=\s*"([^"\n]+)"', text))
        relative = file.relative_to(source).as_posix()
        for call in FILE_CALL.finditer(text):
            scope, expression = call.groups()
            value = expression[1:-1] if expression.startswith('"') else constants.get(expression)
            category = None
            root = None
            if scope == 'cacheDir':
                category = 'CACHE'
            elif scope == 'noBackupFilesDir':
                # The entire captured profile noBackup directory is erased. Individual
                # operational roots must never be mistaken for portable personal records.
                root = value.split('/')[0] if value else None
                if root in regenerable:
                    category = 'REGENERABLE'
                elif root in OPERATIONAL_NO_BACKUP:
                    category = 'PROFILE_OPERATIONAL'
                elif root == 'legacy-body-recovery.json':
                    category = 'PERSONAL_PORTABLE'
                elif relative == 'data/account/ProfileStorage.kt' and root == 'profiles':
                    category = 'PROFILE_REGISTRY'
            else:
                if value:
                    for key, replacement in constants.items():
                        value = value.replace('${' + key + '}', replacement).replace('$' + key, replacement)
                    root = value.split('/')[0]
                    if root in personal:
                        category = 'PERSONAL_PORTABLE' if root in portable else 'PERSONAL_OPERATIONAL'
                    elif root in regenerable:
                        category = 'REGENERABLE'
                    elif relative == 'data/account/ProfileStorage.kt' and root in {'profiles', 'profile-registry.json'}:
                        category = 'PROFILE_REGISTRY'
                else:
                    binding = DYNAMIC_BINDINGS.get((relative, scope, expression))
                    if binding:
                        category = 'REVIEWED_DYNAMIC_PERSONAL'
                        root = binding
            line = text.count('\n', 0, call.start()) + 1
            if category is None:
                errors.append(f'Unclassified private root: {relative}:{line} {scope}, {expression}')
            roots.append({'source': relative, 'line': line, 'scope': scope, 'expression': expression,
                          'root': root, 'category': category})
    return {'status': 'FAIL' if errors else 'PASS', 'evidenceLevel': 'SOURCE_CLASSIFICATION',
            'databaseVersion': int(version), 'tables': manifest, 'portableRoots': sorted(portable),
            'storageCalls': roots, 'errors': errors,
            'limits': 'Direct private-root constructors and schema tables; device writer/barrier and arbitrary file alias analysis remain separate.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path)
    parser.add_argument('--source-root', type=Path)
    parser.add_argument('--schema', type=Path)
    args = parser.parse_args()
    result = verify(args.source_root or SOURCE, args.schema)
    if args.output:
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: value for key, value in result.items() if key not in {'tables', 'storageCalls'}}, ensure_ascii=False))
    raise SystemExit(0 if result['status'] == 'PASS' else 1)
