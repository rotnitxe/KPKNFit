from pathlib import Path
import sys

sys.path.insert(0, str(Path(".cursor/skills/kpkn-exercise-images/scripts")))
from cutout import cutout

assets = Path(r"C:\Users\valen\.cursor\projects\c-Users-valen-Documents-KPKNFit\assets")
draw = Path("android-native/app/src/main/res/drawable-nodpi")
pairs = [
    ("exercise_curl_muneca_mancuernas_a.png", "exercise_curl_muneca_mancuernas.png"),
    ("exercise_curl_muneca_barra_a.png", "exercise_curl_muneca_barra.png"),
    ("exercise_curl_muneca_ez_a.png", "exercise_curl_muneca_ez.png"),
    ("exercise_extension_muneca_polea_a.png", "exercise_extension_muneca_polea.png"),
    ("exercise_extension_muneca_mancuernas_a.png", "exercise_extension_muneca_mancuernas.png"),
    ("exercise_extension_muneca_barra_a.png", "exercise_extension_muneca_barra.png"),
    ("exercise_extension_muneca_ez_a.png", "exercise_extension_muneca_ez.png"),
    ("exercise_curl_muneca_tras_espalda_a.png", "exercise_curl_muneca_tras_espalda.png"),
    ("exercise_enrollamiento_muneca_rodillo_a.png", "exercise_enrollamiento_muneca_rodillo.png"),
    ("exercise_supinacion_mancuernas_a.png", "exercise_supinacion_mancuernas.png"),
    ("exercise_supinacion_polea_a.png", "exercise_supinacion_polea.png"),
    ("exercise_paseo_granjero_mancuernas_a.png", "exercise_paseo_granjero_mancuernas.png"),
    ("exercise_paseo_granjero_kettlebell_a.png", "exercise_paseo_granjero_kettlebell.png"),
    ("exercise_paseo_granjero_discos_a.png", "exercise_paseo_granjero_discos.png"),
    ("exercise_paseo_granjero_hex_a.png", "exercise_paseo_granjero_hex.png"),
    ("exercise_dead_hang_a.png", "exercise_dead_hang.png"),
    ("exercise_pinza_discos_a.png", "exercise_pinza_discos.png"),
]
for src_name, dst_name in pairs:
    src = assets / src_name
    if not src.exists():
        raise SystemExit(f"missing {src}")
    cutout(src, draw / dst_name)
    print("ok", dst_name, flush=True)
