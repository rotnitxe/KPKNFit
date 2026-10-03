"""Static discovery of Kotlin ``@Test`` methods (for declared-vs-reported checks).

Ported from OLDROOT ``evidence_collector.py``: comments and string literals are
blanked (offsets preserved) before scanning so commented-out tests do not count,
and methods are attributed to the *top-level* class that really owns them (a
Kotlin file may declare several classes).
"""

from __future__ import annotations

import re
from pathlib import Path

_TOP_LEVEL_CLASS = re.compile(r"\b(?:class|object)\s+([A-Za-z_]\w*)")
_PACKAGE = re.compile(r"(?m)^\s*package\s+([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)")
_TEST_FUNCTION = re.compile(
    r"""
    @(?:[A-Za-z_]\w*\.)*Test\b
    (?:\s*\([^)]*\))?
    (?:\s*@(?:[A-Za-z_]\w*\.)*[A-Za-z_]\w*(?:\s*\([^)]*\))?)*
    \s*
    (?:(?:public|protected|private|internal|open|override|final|suspend|inline|infix|operator|tailrec|external|actual|expect)\s+)*
    fun\s+(?:\x60([^\x60\r\n]+)\x60|([A-Za-z_]\w*))\s*\(
    """,
    re.VERBOSE,
)


def strip_kotlin_comments_and_literals(source: str) -> str:
    """Blank comments and literals while preserving offsets and newlines."""
    chars = list(source)
    i, length = 0, len(source)

    def blank(start: int, end: int) -> None:
        for index in range(start, min(end, length)):
            if chars[index] not in "\r\n":
                chars[index] = " "

    while i < length:
        if source.startswith("//", i):
            end = source.find("\n", i)
            end = length if end < 0 else end
            blank(i, end)
            i = end
        elif source.startswith("/*", i):
            start, i, nesting = i, i + 2, 1
            while i < length and nesting:
                if source.startswith("/*", i):
                    nesting, i = nesting + 1, i + 2
                elif source.startswith("*/", i):
                    nesting, i = nesting - 1, i + 2
                else:
                    i += 1
            blank(start, i)
        elif source.startswith('"""', i):
            start = i
            end = source.find('"""', i + 3)
            i = length if end < 0 else end + 3
            blank(start, i)
        elif source[i] in ('"', "'"):
            quote, start = source[i], i
            i += 1
            while i < length:
                if source[i] == "\\":
                    i = min(length, i + 2)
                elif source[i] == quote:
                    i += 1
                    break
                else:
                    i += 1
            blank(start, i)
        else:
            i += 1
    return "".join(chars)


def _brace_depths_and_pairs(code: str) -> tuple[list[int], dict[int, int]]:
    depths = [0] * len(code)
    stack: list[int] = []
    pairs: dict[int, int] = {}
    depth = 0
    for index, char in enumerate(code):
        depths[index] = depth
        if char == "{":
            stack.append(index)
            depth += 1
        elif char == "}" and stack:
            pairs[stack.pop()] = index
            depth -= 1
    return depths, pairs


def parse_kotlin_test_classes(source: str) -> list[dict]:
    """Each top-level class with its directly owned ``@Test`` methods."""
    code = strip_kotlin_comments_and_literals(source)
    package_match = _PACKAGE.search(code)
    package_name = package_match.group(1) if package_match else ""
    depths, brace_pairs = _brace_depths_and_pairs(code)
    declarations = [m for m in _TOP_LEVEL_CLASS.finditer(code) if depths[m.start()] == 0]
    parsed = []
    for index, declaration in enumerate(declarations):
        next_start = declarations[index + 1].start() if index + 1 < len(declarations) else len(code)
        opening = code.find("{", declaration.end(), next_start)
        if opening < 0 or depths[opening] != 0 or opening not in brace_pairs:
            continue
        closing = brace_pairs[opening]
        class_name = declaration.group(1)
        fqcn = f"{package_name}.{class_name}" if package_name else class_name
        methods = [t.group(1) or t.group(2) for t in _TEST_FUNCTION.finditer(code, opening + 1, closing)
                   if depths[t.start()] == 1]
        parsed.append({"className": class_name, "fqcn": fqcn, "methods": methods})
    return parsed


def index_test_sources(source_root: Path) -> dict[str, list[dict]]:
    """Index Kotlin test classes by fully-qualified name."""
    index: dict[str, list[dict]] = {}
    if not source_root.exists():
        return index
    for kotlin in sorted(source_root.rglob("*.kt")):
        source = kotlin.read_text(encoding="utf-8-sig")
        for declaration in parse_kotlin_test_classes(source):
            index.setdefault(declaration["fqcn"], []).append({**declaration, "source": str(kotlin.resolve())})
    return index


def resolve_declared(selections: list[str], index: dict[str, list[dict]]) -> list[tuple[str, str]]:
    """Expand ``Class`` / ``Class#method`` selections into declared (class, method) pairs."""
    declared: list[tuple[str, str]] = []
    for selection in selections:
        name, _, method = selection.partition("#")
        if not re.fullmatch(r"com\.example\.kpkn\.[A-Za-z0-9_.]+", name) or (
                method and not re.fullmatch(r"[A-Za-z0-9_]+", method)):
            raise ValueError(f"exact class (optionally #method) required, got {selection!r}")
        candidates = index.get(name, [])
        if len(candidates) != 1:
            raise LookupError(("missing" if not candidates else "ambiguous") + f" androidTest source for {name}")
        methods = candidates[0]["methods"]
        if method:
            if method not in methods:
                raise LookupError(f"unknown @Test method: {selection}")
            declared.append((name, method))
        else:
            if not methods:
                raise LookupError(f"{name} declares no @Test methods (parser found none)")
            declared.extend((name, item) for item in methods)
    return declared
