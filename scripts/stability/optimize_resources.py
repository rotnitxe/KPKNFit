"""Build a resource mirror with lossless WebP; original artwork and resource IDs remain intact."""
from pathlib import Path
from PIL import Image, features
import PIL
import argparse
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'android-native/app/src/main/res'

def checksum(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

def optimize(output, limit=None, previous=None):
    output = output.resolve()
    if not output.is_relative_to(ROOT) or output == SOURCE or output.is_relative_to(SOURCE) or SOURCE.is_relative_to(output):
        raise ValueError('Output must be a separate generated directory in this worktree')
    output.mkdir(parents=True, exist_ok=True)
    reusable = {}
    if previous and previous.get('originalPixelsVerified') is True and previous.get('pillowVersion') == PIL.__version__ and previous.get('webpVersion') == features.version_module('webp'):
        reusable = {entry['resource']: entry for entry in previous.get('entries', [])}
    expected = set()
    before = after = 0
    entries = []
    optimized_count = 0
    for source in sorted(SOURCE.rglob('*')):
        if not source.is_file():
            continue
        relative = source.relative_to(SOURCE)
        destination = output / relative
        if not destination.resolve().is_relative_to(output):
            raise ValueError('Generated resource points outside its mirror')
        destination.parent.mkdir(parents=True, exist_ok=True)
        old_size = source.stat().st_size
        prior = reusable.get(relative.with_suffix('').as_posix())
        cached = output / prior['output'] if prior else None
        can_reuse = prior and cached.resolve().is_relative_to(output) and cached.is_file() and prior['sourceSha256'] == checksum(source) and prior['outputSha256'] == checksum(cached)
        if can_reuse and (limit is None or optimized_count < limit):
            destination = cached
            if source.suffix == '.png' and destination.suffix == '.webp':
                optimized_count += 1
        elif source.suffix == '.png' and not source.name.endswith('.9.png') and (limit is None or optimized_count < limit):
            with Image.open(source) as image:
                if getattr(image, 'n_frames', 1) == 1:
                    candidate = destination.with_suffix('.webp')
                    rgba = image.convert('RGBA')
                    rgba.save(candidate, 'WEBP', lossless=True, exact=True, method=4, quality=100, icc_profile=image.info.get('icc_profile', b''))
                    with Image.open(candidate) as decoded:
                        if decoded.size != rgba.size or decoded.convert('RGBA').tobytes() != rgba.tobytes():
                            raise ValueError(f'Pixel validation failed: {relative}')
                    if candidate.stat().st_size < old_size:
                        destination = candidate
                        optimized_count += 1
                    else:
                        candidate.unlink()
                        shutil.copyfile(source, destination)
                else:
                    shutil.copyfile(source, destination)
        else:
            shutil.copyfile(source, destination)
        before += old_size; after += destination.stat().st_size
        expected.add(destination)
        if source.suffix.lower() in {'.png', '.webp', '.jpg'}:
            entries.append({'resource': relative.with_suffix('').as_posix(), 'sourceSha256': checksum(source),
                            'output': destination.relative_to(output).as_posix(), 'bytesBefore': old_size,
                            'bytesAfter': destination.stat().st_size, 'outputSha256': checksum(destination)})
    # Remove obsolete files only inside the resolved generated output (never original artwork).
    for obsolete in output.rglob('*'):
        if not obsolete.resolve().is_relative_to(output):
            raise ValueError('Unexpected linked resource in mirror')
        if obsolete.is_file() and obsolete not in expected:
            obsolete.unlink()
    report = {'schemaVersion': 1, 'pillowVersion': PIL.__version__, 'webpVersion': features.version_module('webp'),
              'lossless': True, 'originalPixelsVerified': True, 'optimizedImages': optimized_count,
              'bytesBefore': before, 'bytesAfter': after, 'entries': entries}
    return report

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--limit', type=int)
    args = parser.parse_args()
    previous = None
    if args.report.exists():
        try: previous = json.loads(args.report.read_text(encoding='utf-8'))
        except (ValueError, OSError): pass
    report = optimize(args.output, args.limit, previous)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({key: value for key, value in report.items() if key != 'entries'}))
