#!/usr/bin/env python3
import re, sys
from pathlib import Path
xml = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
for m in re.finditer(r"<node [^>]*>", xml):
    s = m.group(0)
    def g(k):
        m2 = re.search(rf'{k}="([^"]*)"', s)
        return m2.group(1) if m2 else ""
    t, d, c, b, cls = g("text"), g("content-desc"), g("clickable"), g("bounds"), g("class")
    if t or d or c == "true" or "Seek" in cls or "Slider" in cls:
        safe = lambda x: x.encode("ascii", "replace").decode("ascii")
        print(f"{c:5} {b:24} {cls.split('.')[-1]:20} t={safe(t)!r} d={safe(d)!r}")
