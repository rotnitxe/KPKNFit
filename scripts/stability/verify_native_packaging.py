"""Inspect ELF LOAD/RELRO segments and APK ZIP boundaries, separately from runtime."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

PAGE = 16384


def relro_padding_conflicts(load_segments, relro_segments):
    """Bionic protects whole touched pages; padding must contain no writable bytes.

    A nonaligned RELRO end is safe when a separate RW LOAD starts on a later
    page. Checking only the end modulus would reject valid segment padding.
    """
    conflicts = []
    for relro in relro_segments:
        start = relro['virtualAddress']
        end = start + relro['memoryBytes']
        rounded_start = start // PAGE * PAGE
        rounded_end = (end + PAGE - 1) // PAGE * PAGE
        for load in load_segments:
            if not load['writable']:
                continue
            load_start, load_end = load['virtualAddress'], load['virtualAddress'] + load['memoryBytes']
            for extra_start, extra_end in ((rounded_start, start), (end, rounded_end)):
                overlap_start, overlap_end = max(load_start, extra_start), min(load_end, extra_end)
                if overlap_start < overlap_end:
                    conflicts.append({'start': overlap_start, 'end': overlap_end})
    return conflicts

def inspect(archive):
    rows = []
    with zipfile.ZipFile(archive) as bundle, archive.open('rb') as raw:
        for entry in bundle.infolist():
            if not entry.filename.startswith('lib/') or not entry.filename.endswith('.so'):
                continue
            data = bundle.read(entry)
            if data[:4] != b'\x7fELF' or data[5] != 1:
                raise ValueError('Expected a little-endian ELF library')
            is64 = data[4] == 2
            offset, stride, count = (struct.unpack_from('<Q', data, 32)[0], *struct.unpack_from('<HH', data, 54)) if is64 else (
                struct.unpack_from('<I', data, 28)[0], *struct.unpack_from('<HH', data, 42))
            loads = []
            load_segments = []
            relro = []
            for index in range(count):
                start = offset + stride * index
                kind = struct.unpack_from('<I', data, start)[0]
                if kind == 1:
                    alignment = struct.unpack_from('<Q' if is64 else '<I', data, start + (48 if is64 else 28))[0]
                    loads.append(alignment)
                    flags = struct.unpack_from('<I', data, start + (4 if is64 else 24))[0]
                    address = struct.unpack_from('<Q' if is64 else '<I', data, start + (16 if is64 else 8))[0]
                    memory = struct.unpack_from('<Q' if is64 else '<I', data, start + (40 if is64 else 20))[0]
                    load_segments.append({'virtualAddress': address, 'memoryBytes': memory, 'writable': bool(flags & 2)})
                elif kind == 0x6474E552:  # PT_GNU_RELRO
                    address = struct.unpack_from('<Q' if is64 else '<I', data, start + (16 if is64 else 8))[0]
                    memory = struct.unpack_from('<Q' if is64 else '<I', data, start + (40 if is64 else 20))[0]
                    relro.append({'virtualAddress': address, 'memoryBytes': memory,
                                  'endModulo16Kb': (address + memory) % 16384})
            raw.seek(entry.header_offset + 26)
            name_size, extra_size = struct.unpack('<HH', raw.read(4))
            zip_offset = entry.header_offset + 30 + name_size + extra_size
            relro_aligned = all(segment['endModulo16Kb'] == 0 for segment in relro)
            overlaps = relro_padding_conflicts(load_segments, relro)
            supported = bool(loads) and min(loads) >= PAGE and not overlaps
            zip_aligned = entry.compress_type != zipfile.ZIP_STORED or zip_offset % 16384 == 0
            rows.append({'path': entry.filename, 'elfBits': 64 if is64 else 32, 'loadAlignments': loads,
                         'relroSegments': relro, 'relroEnd16KbAligned': relro_aligned,
                         'relroWritablePaddingOverlaps': overlaps, 'relroProtection16Kb': not overlaps,
                         'elf16Kb': supported, 'zip16Kb': zip_aligned, 'compressed': entry.compress_type != zipfile.ZIP_STORED})
    assert rows, 'No native libraries were inspected'
    required = [row for row in rows if row['elfBits'] == 64]
    return {'status': 'PASS' if all(row['elf16Kb'] and row['zip16Kb'] for row in required) else 'FAIL',
            'evidenceLevel': 'ELF_AND_APK_PACKAGING', 'runtime16Kb': 'NOT_RUN', 'libraries': rows,
            'apkBytes': archive.stat().st_size, 'sha256': hashlib.file_digest(archive.open('rb'), 'sha256').hexdigest()}

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = inspect(args.apk)
    args.output.write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps({key: value for key, value in result.items() if key != 'libraries'}))
    raise SystemExit(0 if result['status'] == 'PASS' else 1)
