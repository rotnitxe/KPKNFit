from pathlib import Path
import sys

sys.path.insert(0, str(Path(".cursor/skills/kpkn-exercise-images/scripts")))
from cutout import cutout

assets = Path(r"C:\Users\valen\.cursor\projects\c-Users-valen-Documents-KPKNFit\assets")
draw = Path("android-native/app/src/main/res/drawable-nodpi")
pairs = [
    ("exercise_curl_biceps_pie_barra_a.png", "exercise_curl_biceps_pie_barra.png"),
    ("exercise_curl_biceps_pie_ez_a.png", "exercise_curl_biceps_pie_ez.png"),
    ("exercise_curl_biceps_pie_mancuernas_a.png", "exercise_curl_biceps_pie_mancuernas.png"),
    ("exercise_curl_biceps_pie_polea_a.png", "exercise_curl_biceps_pie_polea.png"),
    ("exercise_curl_biceps_sentado_mancuernas_a.png", "exercise_curl_biceps_sentado_mancuernas.png"),
    ("exercise_curl_biceps_sentado_polea_a.png", "exercise_curl_biceps_sentado_polea.png"),
    ("exercise_curl_predicador_barra_a.png", "exercise_curl_predicador_barra.png"),
    ("exercise_curl_predicador_ez_a.png", "exercise_curl_predicador_ez.png"),
    ("exercise_curl_predicador_mancuernas_a.png", "exercise_curl_predicador_mancuernas.png"),
    ("exercise_curl_predicador_maquina_a.png", "exercise_curl_predicador_maquina.png"),
    ("exercise_curl_arana_mancuernas_a.png", "exercise_curl_arana_mancuernas.png"),
    ("exercise_curl_arana_polea_a.png", "exercise_curl_arana_polea.png"),
    ("exercise_curl_arana_barra_a.png", "exercise_curl_arana_barra.png"),
    ("exercise_curl_concentrado_mancuernas_a.png", "exercise_curl_concentrado_mancuernas.png"),
    ("exercise_curl_concentrado_polea_a.png", "exercise_curl_concentrado_polea.png"),
    ("exercise_curl_bayesian_mancuernas_a.png", "exercise_curl_bayesian_mancuernas.png"),
    ("exercise_curl_bayesian_polea_a.png", "exercise_curl_bayesian_polea.png"),
    ("exercise_curl_drag_barra_b.png", "exercise_curl_drag_barra.png"),
    ("exercise_curl_waiter_disco_a.png", "exercise_curl_waiter_disco.png"),
    ("exercise_curl_martillo_h_bar_a.png", "exercise_curl_martillo_h_bar.png"),
    ("exercise_curl_martillo_mancuernas_a.png", "exercise_curl_martillo_mancuernas.png"),
    ("exercise_curl_martillo_polea_a.png", "exercise_curl_martillo_polea.png"),
    ("exercise_curl_martillo_banda_a.png", "exercise_curl_martillo_banda.png"),
    ("exercise_curl_martillo_kettlebell_a.png", "exercise_curl_martillo_kettlebell.png"),
    ("exercise_curl_invertido_h_bar_b.png", "exercise_curl_invertido_h_bar.png"),
    ("exercise_curl_invertido_mancuernas_b.png", "exercise_curl_invertido_mancuernas.png"),
    ("exercise_curl_invertido_polea_a.png", "exercise_curl_invertido_polea.png"),
    ("exercise_curl_invertido_banda_a.png", "exercise_curl_invertido_banda.png"),
    ("exercise_curl_invertido_kettlebell_a.png", "exercise_curl_invertido_kettlebell.png"),
    ("exercise_curl_crucifijo_a.png", "exercise_curl_crucifijo.png"),
    ("exercise_curl_superman_a.png", "exercise_curl_superman.png"),
    ("exercise_curl_biceps_trx_a.png", "exercise_curl_biceps_trx.png"),
]
for src_name, dst_name in pairs:
    src = assets / src_name
    if not src.exists():
        raise SystemExit(f"missing {src}")
    cutout(src, draw / dst_name)
    print("ok", dst_name, flush=True)
