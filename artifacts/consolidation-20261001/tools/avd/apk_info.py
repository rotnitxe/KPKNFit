"""Host-side APK facts without aapt: a small binary-AndroidManifest parser, the
sibling ``output-metadata.json``, optional ``apksigner`` certificates and the
``dumpsys package`` parser used after install.  No adb, no Gradle.
"""

from __future__ import annotations

import json
import os
import re
import struct
import subprocess
import zipfile
from pathlib import Path
from typing import Any

from qa_paths import SDK_ROOT

_RES_NAMES = {
    0x0101021B: "versionCode", 0x0101021C: "versionName", 0x0101020C: "minSdkVersion",
    0x01010270: "targetSdkVersion", 0x0101000F: "debuggable", 0x01010003: "name",
    0x01010024: "value", 0x01010025: "resource", 0x010102B6: "targetPackage",
}
_TYPE_STRING, _TYPE_INT_DEC, _TYPE_INT_HEX, _TYPE_BOOL, _TYPE_REF = 0x03, 0x10, 0x11, 0x12, 0x01


class ApkInfoError(RuntimeError):
    pass


def _read_len8(data: bytes, pos: int) -> tuple[int, int]:
    first = data[pos]
    if first & 0x80:
        return ((first & 0x7F) << 8) | data[pos + 1], pos + 2
    return first, pos + 1


def _parse_string_pool(data: bytes, pos: int) -> list[str]:
    string_count, _style_count, flags, strings_start, _styles_start = struct.unpack_from("<IIIII", data, pos + 8)
    header_size = struct.unpack_from("<H", data, pos + 2)[0]
    offsets = struct.unpack_from(f"<{string_count}I", data, pos + header_size)
    base = pos + strings_start
    utf8 = bool(flags & 0x100)
    out: list[str] = []
    for offset in offsets:
        cursor = base + offset
        if utf8:
            _chars, cursor = _read_len8(data, cursor)
            byte_len, cursor = _read_len8(data, cursor)
            out.append(data[cursor:cursor + byte_len].decode("utf-8", errors="replace"))
        else:
            length = struct.unpack_from("<H", data, cursor)[0]
            cursor += 2
            if length & 0x8000:
                length = ((length & 0x7FFF) << 16) | struct.unpack_from("<H", data, cursor)[0]
                cursor += 2
            out.append(data[cursor:cursor + 2 * length].decode("utf-16-le", errors="replace"))
    return out


def parse_axml(data: bytes) -> list[dict[str, Any]]:
    """Return start-element records ``{"tag": str, "attrs": {name: value}}`` in document order."""
    if len(data) < 8 or struct.unpack_from("<H", data, 0)[0] != 0x0003:
        raise ApkInfoError("not a binary AndroidManifest (missing RES_XML header)")
    header_size = struct.unpack_from("<H", data, 2)[0]
    pos = header_size
    strings: list[str] = []
    resource_ids: list[int] = []
    elements: list[dict[str, Any]] = []
    while pos + 8 <= len(data):
        chunk_type, chunk_header, chunk_size = struct.unpack_from("<HHI", data, pos)
        if chunk_size < 8:
            break
        if chunk_type == 0x0001:
            strings = _parse_string_pool(data, pos)
        elif chunk_type == 0x0180:
            count = (chunk_size - chunk_header) // 4
            resource_ids = list(struct.unpack_from(f"<{count}I", data, pos + chunk_header))
        elif chunk_type == 0x0102:
            body = pos + 16
            _ns, name_idx, attr_start, attr_size, attr_count = struct.unpack_from("<IIHHH", data, body)
            attrs: dict[str, Any] = {}
            cursor = body + attr_start
            for _ in range(attr_count):
                _ans, aname, raw, _size, _res0, dtype, adata = struct.unpack_from("<IIIHBBI", data, cursor)
                cursor += attr_size or 20
                name = strings[aname] if aname < len(strings) and strings[aname] else ""
                if not name and aname < len(resource_ids):
                    name = _RES_NAMES.get(resource_ids[aname], f"res_{resource_ids[aname]:08x}")
                if dtype == _TYPE_STRING:
                    value: Any = strings[adata] if adata < len(strings) else ""
                elif dtype == _TYPE_BOOL:
                    value = adata != 0
                elif dtype == _TYPE_INT_DEC:
                    value = struct.unpack("<i", struct.pack("<I", adata))[0]
                elif dtype == _TYPE_INT_HEX:
                    value = adata
                elif dtype == _TYPE_REF:
                    value = f"@0x{adata:08x}"
                elif raw != 0xFFFFFFFF and raw < len(strings):
                    value = strings[raw]
                else:
                    value = adata
                attrs[name] = value
            elements.append({"tag": strings[name_idx] if name_idx < len(strings) else "", "attrs": attrs})
        pos += chunk_size
    return elements


def read_manifest(apk: Path) -> dict[str, Any]:
    """Package, versionCode/Name, sdk levels and debuggable flag from the APK itself."""
    try:
        with zipfile.ZipFile(apk) as archive:
            raw = archive.read("AndroidManifest.xml")
    except (OSError, KeyError, zipfile.BadZipFile) as error:
        raise ApkInfoError(f"cannot read AndroidManifest.xml from {apk}: {error}") from error
    elements = parse_axml(raw)
    manifest = next((e for e in elements if e["tag"] == "manifest"), None)
    if manifest is None:
        raise ApkInfoError("AndroidManifest has no <manifest> element")
    application = next((e for e in elements if e["tag"] == "application"), {"attrs": {}})
    uses_sdk = next((e for e in elements if e["tag"] == "uses-sdk"), {"attrs": {}})
    instrumentation = next((e for e in elements if e["tag"] == "instrumentation"), None)
    return {
        "package": manifest["attrs"].get("package"),
        "versionCode": manifest["attrs"].get("versionCode"),
        "versionName": manifest["attrs"].get("versionName"),
        "minSdk": uses_sdk["attrs"].get("minSdkVersion"),
        "targetSdk": uses_sdk["attrs"].get("targetSdkVersion"),
        "debuggable": bool(application["attrs"].get("debuggable", False)),
        "instrumentationTarget": instrumentation["attrs"].get("targetPackage") if instrumentation else None,
    }


def read_output_metadata(apk: Path) -> dict[str, Any] | None:
    """Gradle's ``output-metadata.json`` that sits next to a built APK (variant / versionCode)."""
    candidate = apk.parent / "output-metadata.json"
    if not candidate.is_file():
        return None
    try:
        data = json.loads(candidate.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    element = next((e for e in data.get("elements", []) if e.get("outputFile") == apk.name), None)
    return {
        "path": str(candidate),
        "applicationId": data.get("applicationId"),
        "variantName": data.get("variantName"),
        "versionCode": element.get("versionCode") if element else None,
        "versionName": element.get("versionName") if element else None,
    }


def find_apksigner() -> Path | None:
    root = SDK_ROOT / "build-tools"
    if not root.is_dir():
        return None

    def key(path: Path) -> tuple:
        numbers = re.findall(r"\d+", path.name)
        return (0 if "rc" in path.name else 1, tuple(int(n) for n in numbers))

    for directory in sorted((d for d in root.iterdir() if d.is_dir()), key=key, reverse=True):
        for name in ("apksigner.bat", "apksigner"):
            candidate = directory / name
            if candidate.is_file():
                return candidate
    return None


def parse_apksigner_output(text: str) -> dict[str, Any]:
    digests = re.findall(r"Signer #(\d+) certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", text)
    dns = re.findall(r"Signer #(\d+) certificate DN:\s*(.+)", text)
    return {
        "signerSha256": [digest.lower() for _, digest in digests],
        "signerDn": [dn.strip() for _, dn in dns],
    }


def signer_certs(apk: Path, *, timeout: float = 600) -> dict[str, Any] | None:
    """Best effort ``apksigner verify --print-certs``; ``None`` when unavailable."""
    tool = find_apksigner()
    if tool is None or not (os.environ.get("JAVA_HOME") or _which("java")):
        return None
    argv = ["cmd", "/c", str(tool)] if tool.suffix == ".bat" else [str(tool)]
    argv += ["verify", "--print-certs", str(apk)]
    try:
        result = subprocess.run(argv, capture_output=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        return None
    text = result.stdout.decode("utf-8", errors="replace")
    if result.returncode != 0:
        return {"error": (result.stderr.decode("utf-8", errors="replace") or text)[:500]}
    return parse_apksigner_output(text)


def _which(name: str) -> str | None:
    from shutil import which

    return which(name)


def parse_dumpsys_package(text: str) -> dict[str, Any]:
    """Installed facts from ``dumpsys package <pkg>`` (first Packages: record)."""
    def first(pattern: str) -> str | None:
        match = re.search(pattern, text)
        return match.group(1) if match else None

    code = first(r"\bversionCode=(\d+)")
    flags = first(r"\bflags=\[\s*([^\]]*)\]") or first(r"\bpkgFlags=\[\s*([^\]]*)\]") or ""
    return {
        "versionCode": int(code) if code else None,
        "versionName": first(r"\bversionName=([^\r\n]+)"),
        "minSdk": first(r"\bminSdk=(\d+)"),
        "targetSdk": first(r"\btargetSdk=(\d+)"),
        "firstInstallTime": first(r"\bfirstInstallTime=([^\r\n]+)"),
        "lastUpdateTime": first(r"\blastUpdateTime=([^\r\n]+)"),
        "debuggable": "DEBUGGABLE" in flags,
        "flags": flags.split(),
    }


def inspect_apk(apk: Path, *, with_signer: bool = False) -> dict[str, Any]:
    apk = Path(apk)
    if not apk.is_file() or apk.stat().st_size <= 0:
        raise ApkInfoError(f"APK missing or empty: {apk}")
    from evidence import sha256_file

    info: dict[str, Any] = {
        "path": str(apk), "bytes": apk.stat().st_size, "sha256": sha256_file(apk),
        "manifest": read_manifest(apk), "outputMetadata": read_output_metadata(apk),
    }
    if with_signer:
        info["signer"] = signer_certs(apk)
    return info


def newest_source_mtime(roots: list[Path]) -> tuple[float, str] | None:
    """Newest modification time (epoch seconds, path) below the given source roots."""
    newest: tuple[float, str] | None = None
    skip = {"build", ".gradle", ".git", "__pycache__", ".idea"}
    for root in roots:
        if root.is_file():
            candidates = [(root.stat().st_mtime, str(root))]
        else:
            candidates = []
            for current, dirs, files in os.walk(root):
                dirs[:] = [d for d in dirs if d not in skip]
                for name in files:
                    path = os.path.join(current, name)
                    try:
                        candidates.append((os.stat(path).st_mtime, path))
                    except OSError:
                        continue
        for item in candidates:
            if newest is None or item[0] > newest[0]:
                newest = item
    return newest


def staleness(apk: Path, source_roots: list[Path]) -> dict[str, Any]:
    """Compare an APK's mtime with the newest integrated source (informational / --allow-stale gate)."""
    from datetime import datetime, timezone

    apk_mtime = Path(apk).stat().st_mtime
    newest = newest_source_mtime(source_roots)
    iso = lambda value: datetime.fromtimestamp(value, timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    return {
        "apkMtimeUtc": iso(apk_mtime),
        "newestSourceMtimeUtc": iso(newest[0]) if newest else None,
        "newestSourceFile": newest[1] if newest else None,
        "apkOlderThanSources": bool(newest and apk_mtime < newest[0]),
    }
