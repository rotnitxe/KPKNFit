#!/usr/bin/env python3
"""Editorial axis order of every v2 exercise definition.

Single source for the compiler (`compile_exercise_catalog_v2.py`) and the
roster test.  The order is a product decision, not an implementation detail:
see the comment above `AXIS_ORDER_OVERRIDES`.  `catalog_v2_gate.py` keeps its
own independent copy (`EXPECTED_AXIS_ORDER`) on purpose, as a review contract.
"""

from __future__ import annotations

# The order is editorial, not alphabetical.  It is the contract used by the
# picker to reveal one decision level at a time: a broad choice first (usually
# the implement or setup), followed by only the technical choices that still
# matter for the configurations compatible with that choice.  Do not add an
# axis merely because the legacy row mentions it; every axis must have at least
# two real values and every combination must be materialised below.
AXIS_ORDER_OVERRIDES = {
    "good_morning": [
        "implement",
        "laterality"
    ],
    "romanian_deadlift": [
        "implement",
        "stance"
    ],
    "conventional_deadlift": [
        "implement",
        "laterality"
    ],
    "sumo_deadlift": [
        "implement"
    ],
    "stiff_leg_deadlift": [
        "implement",
        "laterality"
    ],
    "seated_leg_curl": [
        "implement",
        "laterality"
    ],
    "lying_leg_curl": [
        "implement",
        "laterality"
    ],
    "standing_leg_curl": [
        "implement",
        "laterality"
    ],
    "belt_squat": [
        "laterality"
    ],
    "pendulum_squat": [
        "laterality"
    ],
    "push_up": [
        "support_angle"
    ],
    "lat_pulldown": [
        "implement",
        "laterality"
    ],
    "pull_up": [
        "grip_type",
        "grip_width"
    ],
    "calf_raise": [
        "implement",
        "laterality"
    ],
    "overhead_triceps_extension": [
        "implement"
    ],
    "crossbody_triceps_extension": [
        "laterality"
    ],
    "pullover": [
        "laterality"
    ],
    "lying_pullover": [
        "implement"
    ],
    "hip_thrust": [
        "implement",
        "laterality"
    ],
    "triceps_patada": [
        "implement",
        "laterality"
    ],
    "quads_extension_cuadriceps": [
        "laterality"
    ],
    "military_press": [
        "implement"
    ],
    "seated_shoulder_press": [
        "implement"
    ],
    "forearms_curl_muneca_sentado": [
        "implement"
    ],
    "reverse_pec_fly": [
        "implement",
        "laterality"
    ],
    "flat_chest_fly": [
        "implement"
    ],
    "incline_chest_fly": [
        "implement"
    ],
    "decline_chest_fly": [],
    "hip_abduction": [
        "implement",
        "station",
        "laterality"
    ],
    "hip_adduction": [
        "implement",
        "station",
        "laterality"
    ],
    "bulgarian_split_squat": [
        "implement"
    ],
    "standing_biceps_curl": [
        "implement"
    ],
    "preacher_curl": [
        "implement"
    ],
    "standing_lateral_raise": [
        "implement"
    ],
    "seated_lateral_raise": [
        "implement"
    ],
    "rear_delt_raise": [
        "implement"
    ],
    "bench_press": [
        "implement"
    ],
    "incline_bench_press": [
        "implement"
    ],
    "decline_bench_press": [
        "implement"
    ],
    "floor_press": [
        "implement"
    ],
    "jm_press": [
        "implement"
    ],
    "california_press": [
        "implement"
    ],
    "tate_press": [
    ],
    "arnold_press": [
        "implement"
    ],
    "z_press": [
        "implement"
    ],
    "katana_extension": [
        "laterality"
    ],
    "chest_supported_row": [
        "implement",
        "pulley_height",
        "grip_width"
    ],
    "seal_row": [
        "implement"
    ],
    "conventional_row": [
        "implement"
    ],
    "pendlay_row": [
        "implement"
    ],
    "t_bar_row": [
        "implement",
        "grip_width"
    ],
    "gironda_row": [
        "grip_width"
    ],
    "sissy_squat": [
        "implement"
    ],
    "forward_lunge": [
        "implement"
    ],
    "reverse_lunge": [
        "implement"
    ],
    "walking_lunge": [
        "implement"
    ],
    "step_up": [
        "implement"
    ],
    "high_bar_back_squat": [
        "implement"
    ],
    "low_bar_back_squat": [
        "implement"
    ],
    "front_squat": [
        "implement"
    ],
    "sumo_squat": [
        "implement"
    ],
    "quads_prensa_piernas": [
        "laterality"
    ],
    "glutes_patada_gluteo": [
        "implement"
    ],
    "glutes_patada_gluteo_lateral": [
        "implement"
    ],
    "glutes_puente_gluteos": [
        "implement",
        "laterality"
    ],
    "tren_superior_cruce_poleas": [
        "implement",
        "pulley_height"
    ],
    "lateral_raise_super_rom": [
        "implement"
    ],
    "deltoides_elevaciones_frontales": [
        "implement"
    ],
    "back_encogimientos": [
        "implement"
    ],
    "back_encogimientos_kelso": [
        "implement"
    ],
    "spider_curl": [
        "implement",
        "grip_type"
    ],
    "biceps_curl_bayesian": [
        "implement",
        "grip_type"
    ],
    "concentration_curl": [
        "implement"
    ],
    "biceps_curl_sentado_banco_plano": [
        "implement"
    ],
    "forearms_curl_muneca_inverso_sentado": [
        "implement"
    ],
    "triceps_pushdown": [
        "implement",
        "laterality"
    ],
    "triceps_press_frances": [
        "implement"
    ],
    "back_remo_gorilla_mancuernas": [
        "implement"
    ],
    "back_remo_renegado_mancuernas": [
        "implement"
    ],
    "romanian_sumo_deadlift": [
        "implement"
    ],
    "good_morning_seated": [
        "implement"
    ],
    "glutes_hiperextension_45": [
        "implement"
    ],
    "back_jefferson_curl": [
        "implement"
    ],
    "quads_sentadilla_hack": [
        "implement"
    ],
    "neck_extension_cuello": [
        "implement"
    ],
    "neck_flexion_cuello": [
        "implement"
    ],
    "quads_extension_cuadriceps_pie_polea": [
        "laterality"
    ],
    "forearms_paseo_del_granjero": [
        "implement"
    ]
}
