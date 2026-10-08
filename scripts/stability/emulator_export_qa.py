"""One locked, grounded export flow. Writes one new local QA backup and preserves other apps/files."""
import argparse
import hashlib
import json
import re
import time
import uuid
from pathlib import Path
from emulator_smoke import ROOT, LOCK, PACKAGE, COMPONENT, adb
from emulator_review_qa import capture


def click(tree, labels, within=None):
    targets = [node for node in tree.iter('node') if node.attrib.get('text') in labels and node.attrib.get('enabled') == 'true']
    if within:
        headings = [node for node in tree.iter('node') if node.attrib.get('text') in within]
        if len(headings) != 1:
            raise RuntimeError('Expected one observed export row')
        parents = {child: parent for parent in tree.iter() for child in parent}
        ancestor = headings[0]
        while ancestor in parents:
            ancestor = parents[ancestor]
            scoped = [target for target in targets if target in set(ancestor.iter())]
            if scoped:
                targets = scoped
                break
    if len(targets) != 1:
        raise RuntimeError('Expected one observed action')
    node = targets[0]
    left, top, right, bottom = map(int, re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib['bounds']).groups())
    result = adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    if result.returncode:
        raise RuntimeError('Observed action failed')
    time.sleep(2)
    return node


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    out = Path(args.out).resolve()
    if not out.is_relative_to(ROOT):
        raise SystemExit('QA output must stay in this checkout')
    out.mkdir(parents=True, exist_ok=True)
    ticket = str(uuid.uuid4())
    LOCK.mkdir()  # Never reclaim or interrupt another owner.
    owner = LOCK / 'owner.txt'
    owner.write_text(f'ticket={ticket} worktree={ROOT}\n')
    result = {'status': 'INCOMPLETE', 'package': PACKAGE, 'scope': 'NEW_LOCAL_BACKUP_ONLY'}
    try:
        launch = adb('shell', 'am', 'start', '-W', '-n', COMPONENT)
        if launch.returncode:
            raise RuntimeError('The isolated app did not open')
        time.sleep(4)
        tree, visible = capture(out, 'initial')
        if any(item['text'] in {'Export or erase my data', 'Exportar o borrar mis datos'} for item in visible):
            click(tree, {'Export or erase my data', 'Exportar o borrar mis datos'})
            tree, visible = capture(out, 'controls')
        elif not any(item['text'] in {'Export a complete backup', 'Exportar copia completa'} for item in visible):
            launch = adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', 'kpkn://settings', '-n', COMPONENT)
            if launch.returncode:
                raise RuntimeError('Settings did not open')
            time.sleep(3)
            tree, visible = capture(out, 'controls')
        click(tree, {'Export', 'Exportar'}, {'Export a complete backup', 'Exportar copia completa'})
        tree, visible = capture(out, 'picker', allow_picker=True)
        if not any(item['text'] in {'Files in Downloads', 'Archivos en Descargas', 'Downloads', 'Descargas'} for item in visible):
            raise RuntimeError('The picker is not the local Downloads folder')
        fields = [node for node in tree.iter('node') if node.attrib.get('class') == 'android.widget.EditText' and node.attrib.get('enabled') == 'true']
        if len(fields) != 1 or len(fields[0].attrib.get('text', '')) > 120:
            raise RuntimeError('Expected one bounded filename field')
        old_name = fields[0].attrib.get('text', '')
        click(tree, {old_name})
        filename = f'kpkn-qa-export-{uuid.uuid4().hex[:12]}.kpkn.zip'
        adb('shell', 'input', 'keyevent', '123', *(['67'] * (len(old_name) + 1)))
        adb('shell', 'input', 'text', filename)
        adb('shell', 'input', 'keyevent', '4')
        tree, _ = capture(out, 'named', allow_picker=True)
        click(tree, {'SAVE', 'Save', 'GUARDAR', 'Guardar'})
        remote = f'/sdcard/Download/{filename}'
        deadline = time.monotonic() + 120
        size = 0
        while time.monotonic() < deadline:
            stat = adb('shell', 'stat', '-c', '%s', remote)
            current = int(stat.stdout.strip()) if stat.returncode == 0 and stat.stdout.strip().isdigit() else 0
            if current > 0 and current == size:
                time.sleep(3)
                confirmed = adb('shell', 'stat', '-c', '%s', remote)
                if confirmed.returncode == 0 and confirmed.stdout.strip() == str(current).encode():
                    break
            size = current
            time.sleep(2)
        else:
            raise RuntimeError('The new backup did not finish within the bounded wait')
        pulled = adb('pull', remote, str(out / 'qa-backup.kpkn.zip'), timeout=120)
        if pulled.returncode:
            raise RuntimeError('The new QA backup could not be inspected')
        import zipfile
        if not 0 < (out / 'qa-backup.kpkn.zip').stat().st_size <= 2 * 1024 * 1024 * 1024:
            raise RuntimeError('Unexpected QA backup size')
        with zipfile.ZipFile(out / 'qa-backup.kpkn.zip') as backup:
            if len(backup.infolist()) > 20_002 or sum(entry.file_size for entry in backup.infolist()) > 2 * 1024 * 1024 * 1024:
                raise RuntimeError('Unexpected unpacked QA backup size')
            if backup.testzip() is not None or 'database.db' not in backup.namelist() or 'manifest.json' not in backup.namelist():
                raise RuntimeError('The exported backup is incomplete')
            manifest = json.loads(backup.read('manifest.json'))
        capture(out, 'returned')
        voice = adb('shell', 'pidof', PACKAGE + ':voice').stdout.strip()
        if voice:
            raise RuntimeError('The voice process survived the writer barrier')
        log = adb('logcat', '-d', '-t', '1600', '-v', 'threadtime')
        (out / 'logcat.txt').write_bytes(log.stdout)
        with (out / 'qa-backup.kpkn.zip').open('rb') as backup_file:
            checksum = hashlib.file_digest(backup_file, 'sha256').hexdigest()
        result.update(status='PASS', backupBytes=(out / 'qa-backup.kpkn.zip').stat().st_size,
                      backupSha256=checksum,
                      manifestVersion=manifest.get('schemaVersion'), voiceProcessAfterExport='ABSENT',
                      limits='One export with a cold voice service; active recording, restore, identity switch and device erasure remain separate.')
    except Exception as error:
        result['errorType'] = type(error).__name__
        result['reason'] = str(error)
        raise
    finally:
        (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        if owner.exists() and f'ticket={ticket}' in owner.read_text():
            owner.unlink()
            LOCK.rmdir()
        print(json.dumps(result))


if __name__ == '__main__':
    main()
