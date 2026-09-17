from pathlib import Path
import sys

sys.path.insert(0, str(Path(".cursor/skills/kpkn-exercise-images/scripts")))
from cutout import cutout

assets = Path(r"C:\Users\valen\.cursor\projects\c-Users-valen-Documents-KPKNFit\assets")
draw = Path("android-native/app/src/main/res/drawable-nodpi")
pairs = [
    ("exercise_curl_invertido_banda_m.png", "exercise_curl_invertido_banda.png"),
    ("exercise_curl_superman_l.png", "exercise_curl_superman.png"),
    ("exercise_press_frances_polea_j.png", "exercise_press_frances_polea.png"),
    ("exercise_triceps_patada_polea_n.png", "exercise_triceps_patada_polea.png"),
    ("exercise_pinza_discos_m.png", "exercise_pinza_discos.png"),
]
for src_name, dst_name in pairs:
    src = assets / src_name
    if not src.exists():
        raise SystemExit(f"missing {src}")
    cutout(src, draw / dst_name)
    print("ok", dst_name, flush=True)
