"""Pure UiAutomator-XML helpers plus the "fresh dump" protocol.

Nothing here talks to a device by itself.  The fresh-dump functions take an
injected ``run_adb`` callable (``run_adb(*args, timeout=...) -> CompletedProcess``)
so they can be unit-tested offline and so that the allowlisted ``avd.Avd``
wrapper stays the only code path that ever invokes adb.

Reused/ported from the OLDROOT tools (measure_catalog_editor.py selector
resolution, uia_dump_freshness.py, ime_visibility.py) without their path pins.
"""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from typing import Any, Callable

# ---------------------------------------------------------------------------
# Selectors
# ---------------------------------------------------------------------------

BOUNDS_RE = re.compile(r"^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$")
TOP_RESUMED_RE = re.compile(
    r"topResumedActivity=ActivityRecord\{[^}]*?\s(?P<component>[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)(?=\s|})"
)
RESUMED_FALLBACK_RE = re.compile(
    r"(?:mResumedActivity|ResumedActivity):\s*ActivityRecord\{[^}]*?\s(?P<component>[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)(?=\s|})"
)
_SELECTOR_ATTRS = {"text", "content-desc", "hint", "resource-id", "class"}
_SELECTOR_ALIASES = {"desc": "content-desc", "id": "resource-id"}


class TargetResolutionError(ValueError):
    """The selector matched zero or several enabled clickable controls."""


def is_ambiguous_target_error(error: TargetResolutionError) -> bool:
    message = str(error)
    selector_match = re.search(r"selector .* matched (\d+) controls:", message)
    scope_match = re.search(r"matches=(\d+)", message)
    return bool(
        (selector_match and int(selector_match.group(1)) > 1)
        or (scope_match and int(scope_match.group(1)) > 1)
    )


def parse_bounds(raw: str | None) -> tuple[int, int, int, int] | None:
    match = BOUNDS_RE.fullmatch(raw or "")
    if not match:
        return None
    left, top, right, bottom = (int(part) for part in match.groups())
    if right <= left or bottom <= top:
        return None
    return left, top, right, bottom


def selector_parts(selector: str) -> tuple[str | None, str, bool]:
    """Return (attribute|None, expected-casefolded, contains?)."""
    value = selector.strip()
    contains = value.startswith("contains:")
    if contains:
        value = value[len("contains:"):]
    if ":" in value:
        key, expected = value.split(":", 1)
        key = _SELECTOR_ALIASES.get(key, key)
        if key not in _SELECTOR_ATTRS:
            raise TargetResolutionError(f"unsupported selector attribute: {key}")
        return key, expected.strip().casefold(), contains
    return None, value.casefold(), contains


def _parse(xml_text: str | bytes) -> ET.Element:
    try:
        return ET.fromstring(xml_text)
    except ET.ParseError as error:
        raise TargetResolutionError(f"invalid UiAutomator XML: {error}") from error


def _node_matches(node: ET.Element, attribute: str | None, expected: str, contains: bool) -> str | None:
    attrs = (attribute,) if attribute else ("text", "content-desc", "hint", "resource-id", "class")
    for key in attrs:
        value = node.attrib.get(key, "")
        if contains:
            if expected in value.casefold():
                return key
        elif value.strip().casefold() == expected:
            return key
    return None


def selector_present(xml_text: str, selector: str) -> bool:
    """True when any node (clickable or not) carries the selector's value."""
    attribute, expected, contains = selector_parts(selector)
    if not expected:
        return False
    root = _parse(xml_text)
    return any(_node_matches(node, attribute, expected, contains) for node in root.iter())


def selector_count(xml_text: str, selector: str) -> int:
    attribute, expected, contains = selector_parts(selector)
    root = _parse(xml_text)
    return sum(1 for node in root.iter() if _node_matches(node, attribute, expected, contains))


def resolve_clickable_target(xml_text: str, selector: str) -> dict[str, Any]:
    """Resolve exactly one enabled clickable node (or its clickable ancestor)."""
    root = _parse(xml_text)
    attribute, expected, contains = selector_parts(selector)
    if not expected:
        raise TargetResolutionError("selector text must not be empty")
    parent_by_node = {child: parent for parent in root.iter() for child in parent}
    candidates: dict[tuple[int, int, int, int], dict[str, Any]] = {}
    for node in root.iter():
        matched = _node_matches(node, attribute, expected, contains)
        if matched is None:
            continue
        target: ET.Element | None = node
        while target is not None:
            bounds = parse_bounds(target.attrib.get("bounds"))
            if (
                bounds is not None
                and target.attrib.get("clickable", "").casefold() == "true"
                and target.attrib.get("enabled", "true").casefold() == "true"
            ):
                break
            target = parent_by_node.get(target)
        if target is None:
            continue
        bounds = parse_bounds(target.attrib.get("bounds"))
        assert bounds is not None
        left, top, right, bottom = bounds
        candidates[bounds] = {
            "selector": selector,
            "matchedAttribute": matched,
            "matchedValue": node.attrib.get(matched, ""),
            "bounds": list(bounds),
            "center": [(left + right) // 2, (top + bottom) // 2],
            "class": target.attrib.get("class", ""),
            "resourceId": target.attrib.get("resource-id", ""),
            "clickable": True,
            "enabled": True,
        }
    if not candidates:
        raise TargetResolutionError(f"no enabled clickable node matched selector {selector!r}")
    if len(candidates) != 1:
        rendered = ", ".join(str(bounds) for bounds in sorted(candidates))
        raise TargetResolutionError(f"selector {selector!r} matched {len(candidates)} controls: {rendered}")
    target_info = next(iter(candidates.values()))
    display = display_bounds(root)
    if display is not None:
        left, top, right, bottom = display
        x, y = target_info["center"]
        if not (left <= x < right and top <= y < bottom):
            raise TargetResolutionError("resolved tap center falls outside current display bounds")
        target_info["displayBounds"] = list(display)
    return target_info


def clickable_candidates(xml_text: str, selector: str) -> list[dict[str, Any]]:
    """Every enabled clickable control (or clickable ancestor) matching ``selector``; no uniqueness requirement.

    Added in the base AVD validation: a pager next to the active card keeps the neighbouring card's chips in the
    tree (a few pixels wide at the screen edge), so "exactly one match" is too strict for the media face trigger;
    the caller picks the on-screen, widest candidate and records the choice.
    """
    root = _parse(xml_text)
    attribute, expected, contains = selector_parts(selector)
    if not expected:
        raise TargetResolutionError("selector text must not be empty")
    parent_by_node = {child: parent for parent in root.iter() for child in parent}
    display = display_bounds(root)
    found: dict[tuple[int, int, int, int], dict[str, Any]] = {}
    for node in root.iter():
        matched = _node_matches(node, attribute, expected, contains)
        if matched is None:
            continue
        target: ET.Element | None = node
        while target is not None:
            bounds = parse_bounds(target.attrib.get("bounds"))
            if (bounds is not None and target.attrib.get("clickable", "").casefold() == "true"
                    and target.attrib.get("enabled", "true").casefold() == "true"):
                break
            target = parent_by_node.get(target)
        if target is None:
            continue
        bounds = parse_bounds(target.attrib.get("bounds"))
        assert bounds is not None
        left, top, right, bottom = bounds
        center = [(left + right) // 2, (top + bottom) // 2]
        if display is not None and not (display[0] <= center[0] < display[2] and display[1] <= center[1] < display[3]):
            continue
        found[bounds] = {"selector": selector, "matchedAttribute": matched, "matchedValue": node.attrib.get(matched, ""),
                         "bounds": list(bounds), "center": center, "class": target.attrib.get("class", ""),
                         "resourceId": target.attrib.get("resource-id", ""), "clickable": True, "enabled": True,
                         "width": right - left}
    return [found[key] for key in sorted(found)]


def resolve_scoped_clickable_target(xml_text: str, selector: str, within_text: str) -> dict[str, Any]:
    """Resolve the nearest control container for a visible label."""
    root = _parse(xml_text)
    parents = {child: parent for parent in root.iter() for child in parent}
    attribute, expected, contains = selector_parts(selector)
    targets: dict[tuple[int, int], dict[str, Any]] = {}
    for anchor in root.iter():
        if anchor.attrib.get("text") != within_text:
            continue
        ancestor: ET.Element | None = anchor
        while ancestor is not None:
            if any(_node_matches(node, attribute, expected, contains) for node in ancestor.iter()):
                target = resolve_clickable_target(ET.tostring(ancestor, encoding="unicode"), selector)
                target["withinText"] = within_text
                targets[tuple(target["center"])] = target
                break
            ancestor = parents.get(ancestor)
    if len(targets) != 1:
        raise TargetResolutionError(
            f"label scope did not identify one control: {within_text!r}; matches={len(targets)}"
        )
    return next(iter(targets.values()))


def display_bounds(root: ET.Element) -> tuple[int, int, int, int] | None:
    bounds = parse_bounds(root.attrib.get("bounds"))
    if bounds is None:
        first = next(iter(root), None)
        bounds = parse_bounds(first.attrib.get("bounds")) if first is not None else None
    return bounds


# ---------------------------------------------------------------------------
# Token / label helpers
# ---------------------------------------------------------------------------

def ui_tokens(xml_text: str) -> list[str]:
    """Every non-empty text / content-desc / hint in document order."""
    root = _parse(xml_text)
    tokens: list[str] = []
    for node in root.iter():
        for key in ("text", "content-desc", "hint"):
            value = node.attrib.get(key, "").strip()
            if value:
                tokens.append(value)
    return tokens


def has_exact_label(xml_text: str, label: str) -> bool:
    root = _parse(xml_text)
    return any(
        node.attrib.get(key, "").strip() == label for node in root.iter() for key in ("text", "content-desc")
    )


def has_label_containing(xml_text: str, fragment: str) -> bool:
    needle = fragment.casefold()
    return any(needle in token.casefold() for token in ui_tokens(xml_text))


def progress_tokens(xml_text: str) -> list[str]:
    """Roadmap progress badges such as ``1/2`` or the all-done mark."""
    return [token for token in ui_tokens(xml_text) if re.fullmatch(r"\d+/\d+|✓", token)]


def package_visible(xml_text: str, package: str) -> bool:
    root = _parse(xml_text)
    return any(node.attrib.get("package") == package for node in root.iter())


def visible_summary(xml_text: str, limit: int = 60) -> list[dict[str, str]]:
    """Compact list of nodes with text/desc for diagnostics in failure details."""
    root = _parse(xml_text)
    out: list[dict[str, str]] = []
    for node in root.iter():
        text, desc = node.attrib.get("text", ""), node.attrib.get("content-desc", "")
        if text or desc or node.attrib.get("class", "").endswith("EditText"):
            out.append({
                "text": text, "desc": desc, "class": node.attrib.get("class", ""),
                "clickable": node.attrib.get("clickable", ""), "enabled": node.attrib.get("enabled", ""),
                "bounds": node.attrib.get("bounds", ""),
            })
            if len(out) >= limit:
                break
    return out


# ---------------------------------------------------------------------------
# Edit fields, scrolling
# ---------------------------------------------------------------------------

def _is_visible_enabled_edit(node: ET.Element) -> bool:
    return (
        node.attrib.get("class", "").endswith("EditText")
        and node.attrib.get("enabled", "").casefold() == "true"
        and node.attrib.get("clickable", "").casefold() == "true"
        and node.attrib.get("visible-to-user", "true").casefold() == "true"
        and node.attrib.get("focusable", "true").casefold() == "true"
        and parse_bounds(node.attrib.get("bounds")) is not None
    )


def edit_candidates(xml_text: str) -> list[dict[str, Any]]:
    root = _parse(xml_text)
    out = []
    for node in root.iter():
        if not _is_visible_enabled_edit(node):
            continue
        bounds = parse_bounds(node.attrib.get("bounds"))
        assert bounds is not None
        left, top, right, bottom = bounds
        out.append({
            "bounds": list(bounds), "area": (right - left) * (bottom - top),
            "center": [(left + right) // 2, (top + bottom) // 2],
            "text": node.attrib.get("text", ""), "hint": node.attrib.get("hint", ""),
            "resourceId": node.attrib.get("resource-id", ""), "focused": node.attrib.get("focused", ""),
        })
    return out


def resolve_primary_input(xml_text: str, dominance: float = 3.0) -> dict[str, Any]:
    """Unique dominant EditText (area >= ``dominance`` x the next one)."""
    candidates = sorted(edit_candidates(xml_text), key=lambda item: item["area"], reverse=True)
    if not candidates:
        raise TargetResolutionError("no visible enabled EditText for the primary input")
    if len(candidates) > 1 and candidates[0]["area"] < candidates[1]["area"] * dominance:
        raise TargetResolutionError(
            f"primary input is ambiguous: area {candidates[0]['area']} < {dominance}x next {candidates[1]['area']}"
        )
    return candidates[0]


def scroll_container_swipe(xml_text: str) -> tuple[int, int, int, int]:
    """Swipe (x1, y1, x2, y2) moving content up inside the only scrollable container."""
    root = _parse(xml_text)
    nodes = [node for node in root.iter() if node.attrib.get("scrollable") == "true"]
    if len(nodes) != 1:
        raise TargetResolutionError(f"expected one scroll container, found {len(nodes)}")
    bounds = parse_bounds(nodes[0].attrib.get("bounds"))
    if bounds is None:
        raise TargetResolutionError("scroll container has no usable bounds")
    left, top, right, bottom = bounds
    x = (left + right) // 2
    return x, top + (bottom - top) * 3 // 4, x, top + (bottom - top) // 4


def screen_swipe_up(xml_text: str) -> tuple[int, int, int, int]:
    """Swipe over the whole display (used when the screen has several scrollables)."""
    root = _parse(xml_text)
    bounds = display_bounds(root)
    if bounds is None:
        raise TargetResolutionError("no display bounds in hierarchy")
    left, top, right, bottom = bounds
    height = bottom - top
    x = left + (right - left) // 2
    return x, top + int(height * 0.78), x, top + int(height * 0.38)


# ---------------------------------------------------------------------------
# Activity / IME parsing
# ---------------------------------------------------------------------------

def parse_top_resumed(dumpsys_text: str) -> str | None:
    match = TOP_RESUMED_RE.search(dumpsys_text)
    if match:
        return match.group("component")
    fallback = RESUMED_FALLBACK_RE.search(dumpsys_text)
    return fallback.group("component") if fallback else None


def component_is(component: str | None, package: str, activity: str = ".MainActivity") -> bool:
    if not component or "/" not in component:
        return False
    pkg, act = component.split("/", 1)
    if pkg != package:
        return False
    if act.startswith("."):
        return act == activity or package + act == package + activity
    return act == package + activity or act == activity


_IME_FIELDS = (
    ("mInputShown", re.compile(r"^\s*mInputShown\s*[:=]\s*(true|false)\b", re.IGNORECASE)),
    ("isInputViewShown", re.compile(r"^\s*m?(?:[Ii]s)?InputViewShown\s*(?:\(\s*\))?\s*[:=]\s*(true|false)\b")),
)


@dataclass(frozen=True)
class ImeObservation:
    visibility: str  # "shown" | "hidden" | "unknown"
    evidence: tuple[tuple[str, bool], ...]
    raw_sha256: str


def parse_ime_visibility(dumpsys_text: str) -> ImeObservation:
    """Explicit shown-state fields only; focus / show-request flags do not count."""
    evidence: list[tuple[str, bool]] = []
    for line in dumpsys_text.splitlines():
        for name, pattern in _IME_FIELDS:
            match = pattern.match(line)
            if match:
                evidence.append((name, match.group(1).casefold() == "true"))
    states = {value for _, value in evidence}
    if len(states) == 1:
        visibility = "shown" if True in states else "hidden"
    else:
        visibility = "unknown"
    return ImeObservation(
        visibility=visibility,
        evidence=tuple(evidence),
        raw_sha256=hashlib.sha256(dumpsys_text.encode("utf-8", errors="replace")).hexdigest(),
    )


# ---------------------------------------------------------------------------
# Fresh dump protocol
# ---------------------------------------------------------------------------

_REMOTE_XML_RE = re.compile(r"/sdcard/kpkn-qa-[A-Za-z0-9._-]+\.xml\Z")
_SUCCESS_RE = re.compile(r"\bUI hier(?:archy|chary) dumped to:\s*(?P<path>/[^\s]+)", re.IGNORECASE)


class UiDumpFreshnessError(RuntimeError):
    """The current UI hierarchy could not be proven fresh and usable."""


def _output_text(result: Any) -> str:
    stdout = result.stdout or b""
    stderr = result.stderr or b""
    if isinstance(stdout, bytes):
        stdout = stdout.decode("utf-8", errors="replace")
    if isinstance(stderr, bytes):
        stderr = stderr.decode("utf-8", errors="replace")
    return (str(stdout) + str(stderr)).strip()


def dump_fresh_xml(
    remote_path: str,
    run_adb: Callable[..., Any],
    *,
    timeout: float = 90.0,
) -> tuple[bytes, dict[str, Any]]:
    """Delete the fixed remote file, require a confirmed dump, then read it back."""
    if not _REMOTE_XML_RE.fullmatch(remote_path) or ".." in remote_path.split("/"):
        raise ValueError(f"UiAutomator XML destination is not an owned /sdcard/kpkn-qa-*.xml path: {remote_path!r}")
    if timeout <= 0:
        raise ValueError("timeout must be positive")
    deadline = time.monotonic() + timeout

    def run(*args: str) -> Any:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise UiDumpFreshnessError("deadline expired before " + " ".join(args[:3]))
        try:
            return run_adb(*args, timeout=remaining)
        except (subprocess.TimeoutExpired, OSError) as error:
            raise UiDumpFreshnessError(f"adb {' '.join(args[:3])} failed: {error}") from error

    removed = run("shell", "rm", "-f", remote_path)
    if removed.returncode != 0:
        raise UiDumpFreshnessError(f"could not remove prior UiAutomator XML: {_output_text(removed)[:500]}")
    dumped = run("shell", "uiautomator", "dump", remote_path)
    dump_output = _output_text(dumped)
    match = _SUCCESS_RE.search(dump_output)
    if dumped.returncode != 0 or match is None:
        raise UiDumpFreshnessError(
            f"UiAutomator did not confirm a fresh hierarchy (exit={dumped.returncode}): {dump_output[:500]}"
        )
    reported = match.group("path").rstrip(".,;")
    if reported != remote_path:
        raise UiDumpFreshnessError(f"UiAutomator reported {reported!r}; expected {remote_path!r}")
    pulled = run("exec-out", "cat", remote_path)
    payload = pulled.stdout or b""
    if pulled.returncode != 0:
        raise UiDumpFreshnessError(f"could not read fresh UiAutomator XML: {_output_text(pulled)[:500]}")
    try:
        root = ET.fromstring(payload)
    except (ET.ParseError, TypeError, ValueError) as error:
        raise UiDumpFreshnessError(f"fresh UiAutomator output was not valid XML: {error}") from error
    if root.tag != "hierarchy":
        raise UiDumpFreshnessError(f"fresh UiAutomator XML had unexpected root {root.tag!r}")
    return payload, {
        "remotePath": remote_path,
        "dumpOutput": dump_output,
        "bytes": len(payload),
        "sha256": hashlib.sha256(payload).hexdigest(),
        "fresh": True,
    }


def _failure_phase(error: UiDumpFreshnessError) -> str:
    message = str(error).casefold()
    if "remove prior" in message:
        return "remove_previous_xml"
    if "not valid xml" in message or "unexpected root" in message:
        return "parse_hierarchy"
    if "read fresh" in message:
        return "read_fresh_xml"
    if "uiautomator" in message:
        return "dump_hierarchy"
    if "deadline" in message:
        return "deadline"
    return "freshness_check"


def dump_fresh_xml_until_ready(
    remote_path: str,
    run_adb: Callable[..., Any],
    *,
    timeout: float = 90.0,
    retry_interval: float = 1.0,
    sleep: Callable[[float], None] = time.sleep,
) -> tuple[bytes, dict[str, Any]]:
    """Retry transient UiAutomator bridge failures inside one overall deadline."""
    if timeout <= 0 or retry_interval <= 0:
        raise ValueError("timeout and retry_interval must be positive")
    retry_interval = min(float(retry_interval), 1.0)
    started = time.monotonic()
    deadline = started + timeout
    attempts: list[dict[str, Any]] = []
    number = 0
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        number += 1
        try:
            payload, metadata = dump_fresh_xml(remote_path, run_adb, timeout=max(0.001, remaining))
        except UiDumpFreshnessError as error:
            attempts.append({"attempt": number, "phase": _failure_phase(error), "fresh": False, "error": str(error)})
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                break
            sleep(min(retry_interval, remaining))
        else:
            attempts.append({"attempt": number, "phase": "complete", "fresh": True,
                             "bytes": metadata["bytes"], "sha256": metadata["sha256"]})
            metadata["retry"] = {
                "attemptCount": number, "failedAttemptCount": number - 1, "deadlineSeconds": timeout,
                "elapsedSeconds": time.monotonic() - started, "attempts": attempts,
            }
            return payload, metadata
    error = UiDumpFreshnessError(
        "fresh UiAutomator capture did not become ready before the caller deadline; attempts="
        + json.dumps(attempts, ensure_ascii=False, separators=(",", ":"))
    )
    error.attempts = attempts  # type: ignore[attr-defined]
    raise error
