from pathlib import Path
import sys

sys.path.insert(0, str(Path(".cursor/skills/kpkn-exercise-images/scripts")))
from cutout import cutout

assets = Path(r"C:\Users\valen\.cursor\projects\c-Users-valen-Documents-KPKNFit\assets")
draw = Path("android-native/app/src/main/res/drawable-nodpi")
pairs = [
    ("exercise_curl_concentrado_polea_f.png", "exercise_curl_concentrado_polea.png"),
    ("exercise_curl_bayesian_mancuernas_f.png", "exercise_curl_bayesian_mancuernas.png"),
    ("exercise_curl_bayesian_polea_f.png", "exercise_curl_bayesian_polea.png"),
    ("exercise_curl_martillo_h_bar_f.png", "exercise_curl_martillo_h_bar.png"),
    ("exercise_curl_martillo_polea_f.png", "exercise_curl_martillo_polea.png"),
    ("exercise_curl_martillo_kettlebell_f.png", "exercise_curl_martillo_kettlebell.png"),
    ("exercise_curl_invertido_banda_f.png", "exercise_curl_invertido_banda.png"),
    ("exercise_curl_zottman_mancuernas_f.png", "exercise_curl_zottman_mancuernas.png"),
    ("exercise_curl_superman_f.png", "exercise_curl_superman.png"),
    ("exercise_curl_biceps_trx_f.png", "exercise_curl_biceps_trx.png"),
    ("exercise_triceps_pushdown_maquina_f.png", "exercise_triceps_pushdown_maquina.png"),
    ("exercise_triceps_pushdown_banda_f.png", "exercise_triceps_pushdown_banda.png"),
    ("exercise_triceps_overhead_barra_f.png", "exercise_triceps_overhead_barra.png"),
    ("exercise_triceps_overhead_maquina_f.png", "exercise_triceps_overhead_maquina.png"),
    ("exercise_triceps_overhead_polea_f.png", "exercise_triceps_overhead_polea.png"),
    ("exercise_press_frances_polea_f.png", "exercise_press_frances_polea.png"),
    ("exercise_press_frances_kettlebell_f.png", "exercise_press_frances_kettlebell.png"),
    ("exercise_jm_press_smith_f.png", "exercise_jm_press_smith.png"),
    ("exercise_jm_press_polea_f.png", "exercise_jm_press_polea.png"),
    ("exercise_press_california_barra_f.png", "exercise_press_california_barra.png"),
    ("exercise_press_california_ez_f.png", "exercise_press_california_ez.png"),
    ("exercise_press_california_mancuernas_f.png", "exercise_press_california_mancuernas.png"),
    ("exercise_tate_press_polea_f.png", "exercise_tate_press_polea.png"),
    ("exercise_triceps_patada_polea_f.png", "exercise_triceps_patada_polea.png"),
    ("exercise_triceps_katana_polea_f.png", "exercise_triceps_katana_polea.png"),
    ("exercise_triceps_extension_trx_f.png", "exercise_triceps_extension_trx.png"),
    ("exercise_triceps_press_maquina_f.png", "exercise_triceps_press_maquina.png"),
    ("exercise_supinacion_polea_f.png", "exercise_supinacion_polea.png"),
    ("exercise_pronacion_mancuernas_f.png", "exercise_pronacion_mancuernas.png"),
    ("exercise_pronacion_polea_f.png", "exercise_pronacion_polea.png"),
    ("exercise_pinza_discos_f.png", "exercise_pinza_discos.png"),
]
for src_name, dst_name in pairs:
    src = assets / src_name
    if not src.exists():
        raise SystemExit(f"missing {src}")
    cutout(src, draw / dst_name)
    print("ok", dst_name, flush=True)
