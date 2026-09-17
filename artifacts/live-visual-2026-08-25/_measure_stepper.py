import re
from pathlib import Path

xml = Path("artifacts/live-visual-2026-08-25/_ui_tmp.xml").read_text(encoding="utf-8", errors="replace")
for label in ["0/2", "R1", "S1", "S2"]:
    for m in re.finditer(
        rf'text="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    ):
        x1, y1, x2, y2 = map(int, m.groups())
        print(f"{label} bounds=({x1},{y1})-({x2},{y2}) cx={(x1+x2)//2} w={x2-x1}")
for m in re.finditer(
    r'content-desc="([^"]*[Aa].adir serie)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
    xml,
):
    d, x1, y1, x2, y2 = m.group(1), *map(int, m.groups()[1:])
    print(f"ADD {d!r} bounds=({x1},{y1})-({x2},{y2}) cx={(x1+x2)//2} w={x2-x1}")
