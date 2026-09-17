import re
from pathlib import Path

xml = Path("artifacts/live-visual-2026-08-25/_ui_tmp.xml").read_text(encoding="utf-8", errors="replace")
# all clickable nodes near y=507
nodes = []
for m in re.finditer(r"<node [^>]*>", xml):
    n = m.group(0)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    cy = (y1 + y2) // 2
    if not (480 <= cy <= 540):
        continue
    t = re.search(r'text="([^"]*)"', n)
    d = re.search(r'content-desc="([^"]*)"', n)
    nodes.append(((x1 + x2) // 2, x1, x2, t.group(1) if t else "", d.group(1) if d else ""))

nodes.sort()
for cx, x1, x2, t, d in nodes:
    print(f"cx={cx} [{x1},{x2}] w={x2-x1} text={t!r} desc={d!r}")

# find badge-ish and add
badge = next((n for n in nodes if n[3] == "0/2" or "0/2" in n[3]), None)
# parent of 0/2 may be wider - look for widest left node
leftish = [n for n in nodes if n[1] < 200]
rightish = [n for n in nodes if n[2] > 1200]
print("--- leftish ---")
for n in leftish:
    print(n)
print("--- rightish ---")
for n in rightish:
    print(n)

r1 = next(n for n in nodes if n[3] == "R1")
s2 = next(n for n in nodes if n[3] == "S2")
add = next(n for n in nodes if "adir serie" in n[4])
# Use surface/clickable parents: often empty text with larger bounds containing the label
# Approximate: for R1, find node that contains R1 text center
r1_cx = r1[0]
r1_parent = max((n for n in nodes if n[1] <= r1_cx <= n[2] and n[2] - n[1] >= r1[2] - r1[1]), key=lambda n: n[2] - n[1])
s2_cx = s2[0]
s2_parent = max((n for n in nodes if n[1] <= s2_cx <= n[2] and n[2] - n[1] >= s2[2] - s2[1]), key=lambda n: n[2] - n[1])
badge_parent = max((n for n in nodes if n[1] <= 107 <= n[2]), key=lambda n: n[2] - n[1])
print("badge_parent", badge_parent)
print("r1_parent", r1_parent)
print("s2_parent", s2_parent)
print("add", add)
print("LEFT gap (badge_right -> first_left)", r1_parent[1] - badge_parent[2])
print("RIGHT gap (last_right -> add_left)", add[1] - s2_parent[2])
