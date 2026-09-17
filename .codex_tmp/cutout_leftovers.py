from pathlib import Path
import sys

sys.path.insert(0, str(Path(".cursor/skills/kpkn-exercise-images/scripts")))
from cutout import cutout

assets = Path(r"C:\Users\valen\.cursor\projects\c-Users-valen-Documents-KPKNFit\assets")
draw = Path("android-native/app/src/main/res/drawable-nodpi")
pairs = [
    ("exercise_press_california_ez_b.png", "exercise_press_california_ez.png"),
    ("exercise_press_california_mancuernas_b.png", "exercise_press_california_mancuernas.png"),
    ("exercise_triceps_press_maquina_b.png", "exercise_triceps_press_maquina.png"),
    ("exercise_curl_muneca_polea_b.png", "exercise_curl_muneca_polea.png"),
    ("exercise_pronacion_mancuernas_b.png", "exercise_pronacion_mancuernas.png"),
    ("exercise_pronacion_polea_c.png", "exercise_pronacion_polea.png"),
]
for src_name, dst_name in pairs:
    src = assets / src_name
    if not src.exists():
        raise SystemExit(f"missing {src}")
    cutout(src, draw / dst_name)
    print("ok", dst_name, flush=True)
