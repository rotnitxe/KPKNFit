import re
from pathlib import Path

xml = Path("artifacts/live-visual-2026-08-25/_ui_tmp.xml").read_text(encoding="utf-8", errors="replace")
for m in re.finditer(r"<node [^>]*clickable=\"true\"[^>]*>", xml):
    n = m.group(0)
    t = re.search(r'text="([^"]*)"', n)
    d = re.search(r'content-desc="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    print(repr(t.group(1) if t else ""), "|", repr(d.group(1) if d else ""), (x1 + x2) // 2, (y1 + y2) // 2)
