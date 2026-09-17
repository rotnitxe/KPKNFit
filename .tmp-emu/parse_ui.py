import re
from pathlib import Path

xml = Path(".tmp-emu/wizard-ui.xml").read_text(encoding="utf-8", errors="replace")
out = []
texts = re.findall(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
for t, x1, y1, x2, y2 in texts:
    if t.strip():
        out.append(f"{t}  y={y1}-{y2} h={int(y2)-int(y1)}")
Path(".tmp-emu/wizard-ui-texts.txt").write_text("\n".join(out), encoding="utf-8")
print("wrote", len(out), "lines")
print("POWERLIFTING y", [l for l in out if "POWER" in l])
print("Continuar", [l for l in out if "Continuar" in l or "Cerrar" in l])
print("nav", any(k in xml for k in ["Home", "Training", "Nutrition"]))
