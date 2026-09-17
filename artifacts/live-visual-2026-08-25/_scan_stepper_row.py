import re
from pathlib import Path

xml = Path("artifacts/live-visual-2026-08-25/_ui_tmp.xml").read_text(encoding="utf-8", errors="replace")
print("anadir in xml", "Añadir" in xml)
descs = sorted(set(re.findall(r'content-desc="([^"]*)"', xml)))
print("descs", [d for d in descs if d])
# nodes near y=511
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    t, x1, y1, x2, y2 = m.groups()
    cy = (int(y1) + int(y2)) // 2
    if 470 <= cy <= 560:
        print("text", t, "x", (int(x1)+int(x2))//2, "y", cy, "w", int(x2)-int(x1))
for m in re.finditer(r'content-desc="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    t, x1, y1, x2, y2 = m.groups()
    cy = (int(y1) + int(y2)) // 2
    if 470 <= cy <= 560 and t:
        print("desc", t, "x", (int(x1)+int(x2))//2, "y", cy)
