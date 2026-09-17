from pathlib import Path
import sys

sys.path.insert(0, str(Path(".cursor/skills/kpkn-exercise-images/scripts")))
from cutout import cutout

assets = Path(r"C:\Users\valen\.cursor\projects\c-Users-valen-Documents-KPKNFit\assets")
draw = Path("android-native/app/src/main/res/drawable-nodpi")
pairs = [
    ("exercise_curl_bayesian_mancuernas_g.png", "exercise_curl_bayesian_mancuernas.png"),
    ("exercise_curl_invertido_banda_g.png", "exercise_curl_invertido_banda.png"),
    ("exercise_curl_superman_g.png", "exercise_curl_superman.png"),
    ("exercise_triceps_pushdown_banda_h.png", "exercise_triceps_pushdown_banda.png"),
    ("exercise_triceps_overhead_barra_g.png", "exercise_triceps_overhead_barra.png"),
    ("exercise_triceps_overhead_maquina_g.png", "exercise_triceps_overhead_maquina.png"),
    ("exercise_triceps_overhead_polea_g.png", "exercise_triceps_overhead_polea.png"),
    ("exercise_press_frances_polea_g.png", "exercise_press_frances_polea.png"),
    ("exercise_press_frances_kettlebell_g.png", "exercise_press_frances_kettlebell.png"),
    ("exercise_press_california_barra_k.png", "exercise_press_california_barra.png"),
    ("exercise_press_california_ez_g.png", "exercise_press_california_ez.png"),
    ("exercise_tate_press_polea_k.png", "exercise_tate_press_polea.png"),
    ("exercise_triceps_patada_polea_g.png", "exercise_triceps_patada_polea.png"),
    ("exercise_triceps_katana_polea_g.png", "exercise_triceps_katana_polea.png"),
    ("exercise_triceps_press_maquina_g.png", "exercise_triceps_press_maquina.png"),
    ("exercise_pronacion_polea_g.png", "exercise_pronacion_polea.png"),
    ("exercise_pinza_discos_g.png", "exercise_pinza_discos.png"),
]
for src_name, dst_name in pairs:
    src = assets / src_name
    if not src.exists():
        raise SystemExit(f"missing {src}")
    cutout(src, draw / dst_name)
    print("ok", dst_name, flush=True)
