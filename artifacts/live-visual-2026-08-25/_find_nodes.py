import re
from pathlib import Path
import sys

xml = Path(sys.argv[1]).read_text(encoding="utf-8", errors="ignore")
for m in re.finditer(r"<node[^>]+>", xml):
    n = m.group(0)
    cd = re.search(r'content-desc="([^"]*)"', n)
    t = re.search(r'text="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    label = (cd.group(1) if cd else "") or (t.group(1) if t else "")
    if not label or not b:
        continue
    low = label.lower()
    if any(k in low for k in ["iniciar", "dod", "sesi", "play", "editar", "live", "variante"]):
        x1, y1, x2, y2 = map(int, b.groups())
        clickable = 'clickable="true"' in n
        print(f"{label!r:40} {(x1+x2)//2},{(y1+y2)//2} {b.group(0)} clickable={clickable}")
