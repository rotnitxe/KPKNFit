"""Dependency-free probing of captured media bytes (JPEG / MP4) and camera-service dumps.

Used by ``camerax_capture.py`` to prove a file is a real encoder output (valid JPEG with a start-of-frame
marker; MP4 with ftyp + moov/mvhd + a video track) and to find the app in ``dumpsys media.camera``.
"""

from __future__ import annotations

import re
import struct
from typing import Any


def jpeg_info(data: bytes) -> dict[str, Any]:
    """Walk JPEG markers: dimensions from SOF, EXIF presence, EOI presence."""
    if not data.startswith(b"\xff\xd8"):
        raise ValueError("not a JPEG (missing SOI)")
    pos, length = 2, len(data)
    info: dict[str, Any] = {"width": None, "height": None, "exif": False, "eoi": data.rstrip(b"\x00").endswith(b"\xff\xd9"),
                            "progressive": False, "bytes": length}
    while pos + 4 <= length:
        if data[pos] != 0xFF:
            pos += 1
            continue
        marker = data[pos + 1]
        if marker in (0xD8, 0x01) or 0xD0 <= marker <= 0xD7:
            pos += 2
            continue
        if marker == 0xFF:
            pos += 1
            continue
        if marker == 0xD9:
            break
        seg_len = struct.unpack_from(">H", data, pos + 2)[0]
        if marker == 0xE1 and data[pos + 4:pos + 10] == b"Exif\x00\x00":
            info["exif"] = True
        if marker in (0xC0, 0xC1, 0xC2) and pos + 9 <= length:
            info["progressive"] = marker == 0xC2
            info["height"], info["width"] = struct.unpack_from(">HH", data, pos + 5)
            break
        if marker == 0xDA:  # start of scan before any SOF: malformed
            break
        pos += 2 + seg_len
    if not info["width"]:
        raise ValueError("JPEG has no SOF marker")
    return info


def _boxes(data: bytes, start: int, end: int):
    pos = start
    while pos + 8 <= end:
        size, kind = struct.unpack_from(">I4s", data, pos)
        header = 8
        if size == 1 and pos + 16 <= end:
            size = struct.unpack_from(">Q", data, pos + 8)[0]
            header = 16
        elif size == 0:
            size = end - pos
        if size < header or pos + size > end:
            return
        yield kind.decode("latin-1"), pos + header, pos + size
        pos += size


def mp4_info(data: bytes) -> dict[str, Any]:
    """ftyp brand, mvhd duration, and per-track handler/dimensions from moov (no external parser)."""
    boxes = {kind: (a, b) for kind, a, b in _boxes(data, 0, len(data))}
    if "ftyp" not in boxes:
        raise ValueError("not an MP4 (no ftyp box)")
    if "moov" not in boxes:
        raise ValueError("MP4 has no moov box (recording not finalized?)")
    ftyp_a, ftyp_b = boxes["ftyp"]
    info: dict[str, Any] = {"brand": data[ftyp_a:ftyp_a + 4].decode("latin-1"), "bytes": len(data), "tracks": [],
                            "durationMs": None, "hasMdat": "mdat" in boxes}
    moov_a, moov_b = boxes["moov"]
    for kind, a, b in _boxes(data, moov_a, moov_b):
        if kind == "mvhd":
            version = data[a]
            if version == 1:
                timescale, duration = struct.unpack_from(">IQ", data, a + 20)
            else:
                timescale, duration = struct.unpack_from(">II", data, a + 12)
            info["durationMs"] = round(duration * 1000 / timescale) if timescale else None
        elif kind == "trak":
            track: dict[str, Any] = {"handler": None, "width": None, "height": None}
            for tkind, ta, tb in _boxes(data, a, b):
                if tkind == "tkhd":
                    version = data[ta]
                    offset = ta + (88 if version == 1 else 76)
                    if offset + 8 <= tb:
                        w, h = struct.unpack_from(">II", data, offset)
                        track["width"], track["height"] = w >> 16, h >> 16
                elif tkind == "mdia":
                    for mkind, ma, mb in _boxes(data, ta, tb):
                        if mkind == "hdlr" and ma + 12 <= mb:
                            track["handler"] = data[ma + 8:ma + 12].decode("latin-1")
            info["tracks"].append(track)
    info["hasVideoTrack"] = any(t["handler"] == "vide" for t in info["tracks"])
    info["hasAudioTrack"] = any(t["handler"] == "soun" for t in info["tracks"])
    return info


def camera_device_count(dumpsys_media_camera: str) -> int | None:
    match = re.search(r"Number of camera devices:\s*(\d+)", dumpsys_media_camera)
    return int(match.group(1)) if match else None


def camera_service_evidence(dumpsys_media_camera: str, package: str, limit: int = 12) -> dict[str, Any]:
    """Lines of ``dumpsys media.camera`` that mention ``package`` together with a connect/client marker."""
    lines = [line.strip() for line in dumpsys_media_camera.splitlines()
             if package in line and re.search(r"CONNECT|DISCONNECT|[Cc]lient|OPEN|ACTIVE|active", line)]
    return {"found": bool(lines), "lines": lines[:limit], "deviceCount": camera_device_count(dumpsys_media_camera)}


def logcat_camera_lines(logcat_text: str, package: str, limit: int = 20) -> list[str]:
    """CameraService / Camera2 lines mentioning the app (INFO-level service logs)."""
    pattern = re.compile(r"CameraService|Camera2CameraImpl|CameraManager|cameraserver|CameraDeviceClient")
    return [line for line in logcat_text.splitlines() if package in line and pattern.search(line)][:limit]
