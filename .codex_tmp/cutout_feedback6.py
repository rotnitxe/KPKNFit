from pathlib import Path
import sys
sys.path.insert(0, str(Path(".cursor/skills/kpkn-exercise-images/scripts")))
from cutout import cutout
assets = Path(r"C:\Users\valen\.cursor\projects\c-Users-valen-Documents-KPKNFit\assets")
draw = Path("android-native/app/src/main/res/drawable-nodpi")
pairs = [
    ("exercise_curl_bayesian_mancuernas_r.png", "exercise_curl_bayesian_mancuernas.png"),
    ("exercise_curl_invertido_banda_p.png", "exercise_curl_invertido_banda.png"),
    ("exercise_curl_superman_o.png", "exercise_curl_superman.png"),
    ("exercise_tate_press_polea_u.png", "exercise_tate_press_polea.png"),
    ("exercise_triceps_patada_polea_p.png", "exercise_triceps_patada_polea.png"),
    ("exercise_pinza_discos_p.png", "exercise_pinza_discos.png"),
]
for src_name, dst_name in pairs:
    src = assets / src_name
    if not src.exists():
        raise SystemExit(f"missing {src}")
    cutout(src, draw / dst_name)
    print("ok", dst_name, flush=True)
