"""Verify source, merged manifests and packaged classes separately for the launch scope."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'android-native/app'
NS = '{http://schemas.android.com/apk/res/android}'
FORBIDDEN = {
    'android.permission.ACCESS_BACKGROUND_LOCATION', 'android.permission.MANAGE_EXTERNAL_STORAGE',
    'android.permission.READ_MEDIA_IMAGES', 'android.permission.READ_MEDIA_VIDEO',
    'android.permission.QUERY_ALL_PACKAGES', 'android.permission.REQUEST_INSTALL_PACKAGES',
    'android.permission.FOREGROUND_SERVICE_DATA_SYNC',
}
HEALTH_CLASSES = ('Landroidx/health/connect/', 'Lcom/example/kpkn/features/healthconnect/')


def check_permissions(permissions):
    assert not (permissions & FORBIDDEN), sorted(permissions & FORBIDDEN)
    assert not any(p.startswith('android.permission.health.') for p in permissions)
    assert {'android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION',
            'android.permission.RECORD_AUDIO', 'android.permission.CAMERA'} <= permissions


def check_manifest(path, merged=False):
    manifest = ET.parse(path).getroot()
    permissions = {item.get(NS + 'name') for item in manifest.findall('uses-permission')}
    check_permissions(permissions)
    services = {s.get(NS + 'name'): s.get(NS + 'foregroundServiceType')
                for s in manifest.findall('application/service') if s.get(NS + 'foregroundServiceType')}
    for suffix, service_type in [('CardioGpsForegroundService', 'location'),
                                 ('WorkoutVoiceForegroundService', 'microphone')]:
        assert any(name.endswith('.' + suffix) and value == service_type for name, value in services.items())
    assert all('dataSync' not in value.split('|') for value in services.values())
    assert 'com.google.android.apps.healthdata' not in Path(path).read_text(encoding='utf-8')
    if merged:
        sdk = manifest.find('uses-sdk')
        assert sdk is not None and sdk.get(NS + 'minSdkVersion') == '26' and sdk.get(NS + 'targetSdkVersion') == '36'
        if manifest.get('package') == 'com.example.kpkn':
            assert 'android.permission.READ_EXTERNAL_STORAGE' not in permissions
    return {'path': str(path), 'permissions': sorted(permissions), 'foregroundServices': services}


def dex_class_names(data):
    assert data[:4] == b'dex\n', 'Unsupported dex container'
    string_count, string_offset = struct.unpack_from('<II', data, 56)
    type_count, type_offset = struct.unpack_from('<II', data, 64)
    class_count, class_offset = struct.unpack_from('<II', data, 96)
    for index in range(class_count):
        type_index = struct.unpack_from('<I', data, class_offset + index * 32)[0]
        assert type_index < type_count
        string_index = struct.unpack_from('<I', data, type_offset + type_index * 4)[0]
        assert string_index < string_count
        start = struct.unpack_from('<I', data, string_offset + string_index * 4)[0]
        while data[start] & 0x80:
            start += 1
        start += 1
        end = data.index(b'\0', start)
        yield data[start:end].decode('ascii')


def check_apk(path):
    sdk = Path(os.environ.get('ANDROID_HOME', r'C:\Users\valen\AppData\Local\Android\Sdk'))
    aapt = sdk / 'build-tools/36.0.0/aapt2.exe'
    badging = subprocess.run([str(aapt), 'dump', 'badging', str(path)], check=True, capture_output=True, text=True).stdout
    permissions = set(re.findall(r"uses-permission[^:]*: name='([^']+)'", badging))
    check_permissions(permissions)
    assert re.search(r"(?:minSdkVersion|sdkVersion):'26'", badging) and "targetSdkVersion:'36'" in badging
    package = re.search(r"package: name='([^']+)'", badging).group(1)
    assert package in {'com.example.kpkn', 'com.example.kpkn.stability'}
    if package == 'com.example.kpkn':
        assert 'android.permission.READ_EXTERNAL_STORAGE' not in permissions
    class_count = 0
    with zipfile.ZipFile(path) as archive:
        dex_files = [name for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name)]
        assert dex_files, 'APK contains no dex files'
        for name in dex_files:
            for descriptor in dex_class_names(archive.read(name)):
                class_count += 1
                assert not descriptor.startswith(HEALTH_CLASSES), descriptor
    checksum = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            checksum.update(chunk)
    return {'path': str(path), 'package': package, 'sha256': checksum.hexdigest(),
            'permissions': sorted(permissions), 'dexClassesChecked': class_count,
            'healthProviderClassesFound': 0}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--manifest', action='append', default=[])
    parser.add_argument('--apk')
    parser.add_argument('--dependencies')
    parser.add_argument('--output')
    args = parser.parse_args()
    gradle = (APP / 'build.gradle.kts').read_text(encoding='utf-8')
    versions = (ROOT / 'android-native/gradle/libs.versions.toml').read_text(encoding='utf-8')
    assert 'health.connect' not in gradle and 'health.connect' not in versions
    assert 'src/health/' not in gradle and 'productFlavors' not in gradle
    assert not list((APP / 'src/health').rglob('*.*'))
    assert re.search(r'minSdk\s*=\s*26\b', gradle) and re.search(r'targetSdk\s*=\s*36\b', gradle)
    for path in (APP / 'src/main/res').rglob('*.xml'):
        assert 'Health Connect' not in path.read_text(encoding='utf-8'), str(path)
    result = {'status': 'PASS', 'evidenceLevel': 'SOURCE_ONLY',
              'source': check_manifest(APP / 'src/main/AndroidManifest.xml'),
              'mergedManifests': [check_manifest(path, merged=True) for path in args.manifest]}
    if args.manifest:
        result['evidenceLevel'] = 'MERGED_MANIFEST'
    if args.dependencies:
        dependencies = Path(args.dependencies).read_text(encoding='utf-8')
        assert 'androidx.health.connect' not in dependencies
        result['dependencies'] = {'path': args.dependencies, 'healthSdkFound': False}
    if args.apk:
        result['apk'] = check_apk(args.apk)
        result['evidenceLevel'] = 'APK'
    rendered = json.dumps(result, indent=2)
    if args.output:
        Path(args.output).write_text(rendered + '\n', encoding='utf-8')
    print(rendered)


if __name__ == '__main__':
    main()
