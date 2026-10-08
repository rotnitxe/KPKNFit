"""Grounded, bounded interactions with the isolated stability app under the emulator lock."""
import argparse
import json
import re
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

from emulator_smoke import ROOT, LOCK, COMPONENT, PACKAGE, adb


def capture(out, name, allow_picker=False):
    activity = adb('shell', 'dumpsys', 'activity', 'activities').stdout.decode(errors='replace')
    resumed = [line for line in activity.splitlines() if 'topResumedActivity' in line or 'mResumedActivity' in line]
    picker = any(re.search(r'com\.(?:google\.android|android)\.documentsui/', line) for line in resumed)
    if not any(COMPONENT in line for line in resumed) and not (allow_picker and picker):
        raise SystemExit('The isolated app is not foreground; no interaction was performed')
    raw = adb('exec-out', 'uiautomator', 'dump', '/dev/tty').stdout
    start, end = raw.find(b'<?xml'), raw.find(b'</hierarchy>')
    if start < 0 or end < 0:
        raise SystemExit('UI hierarchy is unavailable')
    raw = raw[start:end + len(b'</hierarchy>')]
    (out / f'{name}.xml').write_bytes(raw)
    screenshot = adb('exec-out', 'screencap', '-p').stdout
    if not screenshot.startswith(b'\x89PNG'):
        raise SystemExit('Screenshot capture failed')
    (out / f'{name}.png').write_bytes(screenshot)
    (out / f'{name}-activity.txt').write_text(activity, encoding='utf-8')
    tree = ET.fromstring(raw)
    visible = [{key: node.attrib.get(key, '') for key in ('text', 'content-desc', 'bounds', 'clickable', 'enabled')}
               for node in tree.iter('node') if node.attrib.get('text') or node.attrib.get('content-desc')]
    (out / f'{name}-visible.json').write_text(json.dumps(visible, ensure_ascii=False, indent=2), encoding='utf-8')
    return tree, visible


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required=True)
    parser.add_argument('--within-text', help='Disambiguate a repeated action by its nearest shared row label')
    parser.add_argument('--allow-system-file-picker', action='store_true')
    parser.add_argument('--replace-with', help='Replace a tapped editable filename with bounded ASCII text')
    parser.add_argument('--press-ms', type=int, default=0, help='Hold a grounded control briefly when a short tap is missed')
    action = parser.add_mutually_exclusive_group()
    action.add_argument('--tap-text')
    action.add_argument('--tap-description')
    action.add_argument('--link')
    action.add_argument('--back', action='store_true')
    action.add_argument('--scroll', action='store_true')
    action.add_argument('--open-app', action='store_true')
    args = parser.parse_args()
    if args.press_ms not in range(0, 1001): raise SystemExit('Invalid press duration')
    out = Path(args.out).resolve()
    if not out.is_relative_to(ROOT):
        raise SystemExit('Evidence must stay in this worktree')
    out.mkdir(parents=True, exist_ok=True)
    ticket = str(uuid.uuid4())
    try:
        LOCK.mkdir()
    except FileExistsError:
        raise SystemExit('Another emulator owner is working; no lock was reclaimed')
    owner = LOCK / 'owner.txt'
    owner.write_text(f'ticket={ticket} worktree={ROOT}\n')
    try:
        if args.open_app:
            result = adb('shell', 'am', 'start', '-W', '-n', COMPONENT)
            if result.returncode:
                raise SystemExit('The isolated app did not open')
            time.sleep(3)
        tree, visible = capture(out, 'before', args.allow_system_file_picker)
        if args.link:
            if not args.link.startswith('kpkn://'):
                raise SystemExit('Only this app custom links are accepted')
            result = adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', args.link, '-n', COMPONENT)
        elif args.back:
            result = adb('shell', 'input', 'keyevent', '4')
        elif args.tap_text or args.tap_description:
            field = 'text' if args.tap_text else 'content-desc'
            label = args.tap_text or args.tap_description
            targets = [node for node in tree.iter('node') if node.attrib.get(field) == label and node.attrib.get('enabled') == 'true']
            if args.within_text:
                row_labels = [node for node in tree.iter('node') if node.attrib.get('text') == args.within_text]
                if len(row_labels) != 1:
                    raise SystemExit('Expected one row label')
                parents = {child: parent for parent in tree.iter() for child in parent}
                ancestor = row_labels[0]
                while ancestor in parents:
                    ancestor = parents[ancestor]
                    scoped = [target for target in targets if target in set(ancestor.iter())]
                    if scoped:
                        targets = scoped
                        break
            if len(targets) != 1:
                raise SystemExit(f'Expected one enabled target, found {len(targets)}')
            bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', targets[0].attrib['bounds'])
            if not bounds:
                raise SystemExit('Target bounds are unavailable')
            left, top, right, bottom = map(int, bounds.groups())
            x, y = str((left + right) // 2), str((top + bottom) // 2)
            result = adb('shell', 'input', 'swipe', x, y, x, y, str(args.press_ms)) if args.press_ms else adb('shell', 'input', 'tap', x, y)
            if args.replace_with:
                target = targets[0]
                if target.attrib.get('class') != 'android.widget.EditText' or not re.fullmatch(r'[A-Za-z0-9._-]{1,120}', args.replace_with):
                    raise SystemExit('Filename replacement requires one editable field and bounded ASCII text')
                current = target.attrib.get('text', '')
                if len(current) > 120:
                    raise SystemExit('Unexpected filename length')
                adb('shell', 'input', 'keyevent', '123', *(['67'] * (len(current) + 1)))
                result = adb('shell', 'input', 'text', args.replace_with)
                adb('shell', 'input', 'keyevent', '4')
        elif args.scroll:
            targets = [node for node in tree.iter('node') if node.attrib.get('scrollable') == 'true']
            if len(targets) != 1:
                raise SystemExit('Expected one scroll container')
            left, top, right, bottom = map(int, re.findall(r'\d+', targets[0].attrib['bounds']))
            x = (left + right) // 2
            height = bottom - top
            result = adb('shell', 'input', 'swipe', str(x), str(top + height * 3 // 4), str(x), str(top + height // 4), '500')
        else:
            result = None
        if result is not None:
            (out / 'action.txt').write_bytes(result.stdout + result.stderr)
            if result.returncode:
                raise SystemExit('UI action failed')
            time.sleep(3)
            _, visible = capture(out, 'after', args.allow_system_file_picker)
        print(json.dumps(visible, ensure_ascii=False))
    finally:
        if owner.exists() and f'ticket={ticket}' in owner.read_text():
            owner.unlink()
            LOCK.rmdir()


if __name__ == '__main__':
    main()
