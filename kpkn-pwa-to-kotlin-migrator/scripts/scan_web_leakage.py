#!/usr/bin/env python3
"""Scan Kotlin files for suspicious web/PWA carryover tokens."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


SUSPICIOUS = {
    r"\bwindow\b": "Browser global leaked into Android code",
    r"\bdocument\b": "DOM reference leaked into Android code",
    r"\blocalStorage\b": "Web persistence API leaked into Android code",
    r"\bsessionStorage\b": "Web persistence API leaked into Android code",
    r"\buseEffect\b": "React hook concept leaked into Kotlin",
    r"\buseMemo\b": "React hook concept leaked into Kotlin",
    r"\buseRef\b": "React hook concept leaked into Kotlin",
    r"onClick=": "JSX-like token suggests copied UI markup",
    r"<div\b": "HTML-like structure suggests copied markup",
    r"framer-motion": "Web animation library reference leaked",
    r"(?<![A-Za-z])\d+(?:\.\d+)?vh\b": "Viewport CSS unit leaked into Android code",
    r"(?<![A-Za-z])\d+(?:\.\d+)?vw\b": "Viewport CSS unit leaked into Android code",
    r"(?<![A-Za-z])\d+(?:\.\d+)?rem\b": "CSS unit leaked into Android code",
    r"overflow-x": "CSS/layout token leaked into Android code",
    r"position:\s*sticky": "Web layout token leaked into Android code",
}


def scan_file(path: Path) -> list[tuple[int, str, str]]:
    findings: list[tuple[int, str, str]] = []
    text = path.read_text(encoding="utf-8", errors="ignore")
    for line_no, line in enumerate(text.splitlines(), start=1):
        for pattern, reason in SUSPICIOUS.items():
            if re.search(pattern, line, flags=re.IGNORECASE):
                findings.append((line_no, pattern, reason))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", help="Kotlin files to scan")
    args = parser.parse_args()

    any_findings = False
    for raw_path in args.paths:
        path = Path(raw_path)
        if not path.exists():
            print(f"[missing] {path}")
            continue
        findings = scan_file(path)
        if not findings:
            print(f"[clean] {path}")
            continue

        any_findings = True
        print(f"[review] {path}")
        for line_no, token, reason in findings:
            print(f"  line {line_no}: '{token}' -> {reason}")

    if any_findings:
        print()
        print("Review flagged lines and decide whether they are intentional or evidence of literal web carryover.")
        return 1

    print()
    print("No obvious web leakage tokens found.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
