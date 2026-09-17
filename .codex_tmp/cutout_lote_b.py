from pathlib import Path
import sys

sys.path.insert(0, str(Path(".cursor/skills/kpkn-exercise-images/scripts")))
from cutout import cutout

assets = Path(r"C:\Users\valen\.cursor\projects\c-Users-valen-Documents-KPKNFit\assets")
draw = Path("android-native/app/src/main/res/drawable-nodpi")
pairs = [
    ("exercise_triceps_pushdown_polea_a.png", "exercise_triceps_pushdown_polea.png"),
    ("exercise_triceps_pushdown_maquina_a.png", "exercise_triceps_pushdown_maquina.png"),
    ("exercise_triceps_pushdown_banda_a.png", "exercise_triceps_pushdown_banda.png"),
    ("exercise_triceps_overhead_barra_a.png", "exercise_triceps_overhead_barra.png"),
    ("exercise_triceps_overhead_maquina_a.png", "exercise_triceps_overhead_maquina.png"),
    ("exercise_triceps_overhead_mancuernas_a.png", "exercise_triceps_overhead_mancuernas.png"),
    ("exercise_triceps_overhead_polea_a.png", "exercise_triceps_overhead_polea.png"),
    ("exercise_press_frances_mancuernas_a.png", "exercise_press_frances_mancuernas.png"),
    ("exercise_press_frances_barra_a.png", "exercise_press_frances_barra.png"),
    ("exercise_press_frances_ez_a.png", "exercise_press_frances_ez.png"),
    ("exercise_press_frances_polea_a.png", "exercise_press_frances_polea.png"),
    ("exercise_press_frances_kettlebell_a.png", "exercise_press_frances_kettlebell.png"),
    ("exercise_jm_press_barra_a.png", "exercise_jm_press_barra.png"),
    ("exercise_jm_press_ez_a.png", "exercise_jm_press_ez.png"),
    ("exercise_jm_press_mancuernas_a.png", "exercise_jm_press_mancuernas.png"),
    ("exercise_jm_press_smith_a.png", "exercise_jm_press_smith.png"),
    ("exercise_jm_press_polea_a.png", "exercise_jm_press_polea.png"),
    ("exercise_press_california_barra_a.png", "exercise_press_california_barra.png"),
    ("exercise_tate_press_mancuernas_b.png", "exercise_tate_press_mancuernas.png"),
    ("exercise_tate_press_polea_b.png", "exercise_tate_press_polea.png"),
    ("exercise_triceps_patada_mancuernas_a.png", "exercise_triceps_patada_mancuernas.png"),
    ("exercise_triceps_patada_polea_a.png", "exercise_triceps_patada_polea.png"),
    ("exercise_triceps_katana_polea_a.png", "exercise_triceps_katana_polea.png"),
    ("exercise_triceps_katana_banda_a.png", "exercise_triceps_katana_banda.png"),
    ("exercise_triceps_cruzada_polea_a.png", "exercise_triceps_cruzada_polea.png"),
    ("exercise_triceps_extension_trx_a.png", "exercise_triceps_extension_trx.png"),
    ("exercise_triceps_pjr_mancuerna_a.png", "exercise_triceps_pjr_mancuerna.png"),
    ("exercise_triceps_flexion_esfinge_a.png", "exercise_triceps_flexion_esfinge.png"),
    ("exercise_triceps_fondos_bancos_a.png", "exercise_triceps_fondos_bancos.png"),
    ("exercise_triceps_rolling_extension_a.png", "exercise_triceps_rolling_extension.png"),
]
for src_name, dst_name in pairs:
    src = assets / src_name
    if not src.exists():
        raise SystemExit(f"missing {src}")
    cutout(src, draw / dst_name)
    print("ok", dst_name, flush=True)
