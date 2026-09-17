import re
from pathlib import Path

xml = Path(r"C:\Users\valen\Documents\KPKNFit\artifacts\live-visual-2026-08-25\ui-home4.xml").read_text(
    encoding="utf-8", errors="ignore"
)
print("file len", len(xml))
print("texts", re.findall(r'text="([^"]+)"', xml)[:40])
print("descs", [d for d in re.findall(r'content-desc="([^"]*)"', xml) if d][:40])
print("--- clickables y<1200 ---")
for m in re.finditer(r"<node[^>]+>", xml):
    n = m.group(0)
    if 'clickable="true"' not in n:
        continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    if y1 > 1200:
        continue
    t = re.search(r'text="([^"]*)"', n)
    cd = re.search(r'content-desc="([^"]*)"', n)
    print(f"{(x1+x2)//2:4},{(y1+y2)//2:4} {(t.group(1) if t else '')!r} {(cd.group(1) if cd else '')!r} {b.group(0)}")
