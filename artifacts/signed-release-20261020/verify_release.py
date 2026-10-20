"""Inspect public APK contents/signatures only; never reads private signing configuration."""
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone
from pathlib import Path

root = Path(__file__).resolve().parent
apk = root / "build/app/outputs/apk/base/release/app-base-release.apk"
sdk = Path(r"C:\Users\valen\AppData\Local\Android\Sdk\build-tools\37.0.0")
baseline = json.loads((root / "source-before-04.json").read_text(encoding="utf-8-sig"))
after = json.loads((root / "source-after.json").read_text(encoding="utf-8-sig"))
assert after["source_unchanged"] and after["source_sha256"] == baseline["source_sha256"]
build_log_bytes = (root / "final-tests-and-release.log").read_bytes()
build_log = build_log_bytes.decode("utf-16" if build_log_bytes.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig", errors="replace")
assert "BUILD SUCCESSFUL" in build_log


def run(name, arguments):
    result = subprocess.run([str(sdk / name), *map(str, arguments)], capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    (root / (name.split(".")[0] + "-" + arguments[0] + ".txt")).write_text(
        result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"{name} failed: {result.returncode}: {result.stderr[:500]}")
    return result.stdout


signature = run("apksigner.bat", ["verify", "--verbose", "--print-certs", apk])
assert "Verified using v2 scheme (APK Signature Scheme v2): true" in signature
historical_bytes = (root / "historical-release-public-certificate.txt").read_bytes()
historical = historical_bytes.decode("utf-16" if historical_bytes.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig")
certificate_pattern = r"certificate SHA-256 digest: ([a-fA-F0-9]+)"
certificate = re.findall(certificate_pattern, signature)
historical_certificate = re.findall(certificate_pattern, historical)
assert certificate
run("zipalign.exe", ["-c", "-P", "16", "4", apk])
badging = run("aapt.exe", ["dump", "badging", apk])
assert "package: name='com.example.kpkn'" in badging
assert "versionCode='50'" in badging
assert "application-debuggable" not in badging
native_code = re.search(r"^native-code: (.+)$", badging, flags=re.MULTILINE)
abis = re.findall(r"'([^']+)'", native_code.group(1)) if native_code else []
assert "arm64-v8a" in abis
manifest = run("aapt2.exe", ["dump", "xmltree", "--file", "AndroidManifest.xml", apk])
assert "com.example.kpkn.debug." not in manifest
assert not re.search(r"(?:debuggable|testOnly).*?0xffffffff", manifest)
assert "com.example.kpkn.MainActivity" in manifest
with zipfile.ZipFile(apk) as archive:
    embedded = json.loads(archive.read("assets/release-build.json").decode("utf-8-sig"))
    assert embedded["source_sha256"] == baseline["source_sha256"]
    assert embedded["head"] == baseline["head"]
    assert archive.testzip() is None
metadata = json.loads((apk.parent / "output-metadata.json").read_text())
assert metadata["variantName"] == "baseRelease"
assert metadata["applicationId"] == "com.example.kpkn"
tests = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
suites = list((root / "build/app/test-results/testBaseDebugUnitTest").glob("TEST-*.xml"))
for path in suites:
    suite = ET.parse(path).getroot()
    for key in tests:
        tests[key] += int(suite.attrib.get(key, 0))
assert suites and tests["tests"] > 0 and tests["failures"] == tests["errors"] == 0
with apk.open("rb") as stream:
    apk_sha = hashlib.file_digest(stream, "sha256").hexdigest()
mapping = root / "build/app/outputs/mapping/baseRelease/mapping.txt"
debug_classes = []
with mapping.open(encoding="utf-8") as stream:
    for line in stream:
        if line.startswith("com.example.kpkn.debug."):
            debug_classes.append(line.strip())
assert not debug_classes, debug_classes
proof = {
    "verified_utc": datetime.now(timezone.utc).isoformat(), "apk": str(apk),
    "apk_bytes": apk.stat().st_size, "apk_sha256": apk_sha, "variant": "baseRelease",
    "application_id": "com.example.kpkn", "version_code": 50,
    "abis": abis,
    "version_name": metadata["elements"][0]["versionName"],
    "source_head": baseline["head"], "source_sha256": baseline["source_sha256"],
    "source_unchanged_during_build": True, "includes_pending_local_sources": True,
    "gradle_build_successful": True, "build_log_sha256": hashlib.sha256(build_log_bytes).hexdigest(),
    "signed_and_verified": True, "signature": signature,
    "signer_certificate_sha256": certificate,
    "matches_historical_release_certificate": set(certificate) == set(historical_certificate),
    "zipalign_16k": True, "debuggable": False, "debug_activities": [], "debug_classes_in_mapping": [],
    "embedded_provenance": embedded, "unit_test_suites": len(suites), "unit_tests": tests,
    "controlled_build_inputs": {str(path.name): hashlib.sha256(path.read_bytes()).hexdigest() for path in (
        root / "fresh-build.init.gradle", root / "provenance-assets/release-build.json")},
    "device_installation_performed": False,
}
(root / "release-verification.json").write_text(json.dumps(proof, indent=2), encoding="utf-8")
print(json.dumps({key: value for key, value in proof.items() if key not in ("signature", "embedded_provenance")}))
