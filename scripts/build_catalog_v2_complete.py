#!/usr/bin/env python3
"""Build the approved v2 catalog from the audited legacy inventory.

The legacy JSON is used only as an evidence source.  Every resulting
configuration is materialised explicitly; no runtime resolver derives options
from names, aliases, or cartesian products.  The mapping tables in this file
are intentionally whitelist based and are included in the curation report.
"""

from __future__ import annotations

import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "catalog" / "exercises" / "v2" / "curation" / "evidence" / "legacy" / "exercise_database.json"
INVENTORY = ROOT / "catalog" / "exercises" / "v2" / "curation" / "candidate_inventory.json"
OUTPUT = ROOT / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"

REVISION = "v2-approved-2026-08-02-c"
ONTOLOGY = "wikilab-v2-2026-08-02"

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
        "implement",
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
    "decline_chest_fly": [
        "implement"
    ],
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
        "implement"
    ],
    "arnold_press": [
        "implement"
    ],
    "z_press": [
        "implement"
    ],
    "katana_extension": [
        "implement",
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
        "implement",
        "stance"
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



def ordered_options(definition_id: str, options: dict[str, str]) -> dict[str, str]:
    """Return a stable, general-to-particular option map for one definition."""
    order = AXIS_ORDER_OVERRIDES.get(definition_id, list(options))
    unknown = [axis for axis in options if axis not in order]
    return {axis: options[axis] for axis in [*order, *unknown] if axis in options}

MUSCLE_IDS = {
    "Abdomen": "abdominals",
    "Aductores": "adductors",
    "Antebrazo": "forearm",
    "Bíceps": "biceps",
    "Core": "core",
    "Cuello": "neck",
    "Cuádriceps": "quadriceps",
    "Deltoides": "deltoid",
    "Dorsales": "latissimus_dorsi",
    "Erectores Espinales": "erector_spinae",
    "Flexores Cadera": "hip_flexors",
    "Glúteos": "gluteus_maximus",
    "Isquiosurales": "hamstrings",
    "Pantorrillas": "calves",
    "Pectorales": "pectoralis",
    "Romboides": "rhomboids",
    "Tensor Fascia Lata": "tensor_fasciae_latae",
    "Tibial Anterior": "tibialis_anterior",
    "Trapecio": "trapezius",
    "Tríceps": "triceps",
}

# Explicit editorial corrections for records whose legacy role labels do not
# express the v2 ontology.  These are keyed by source ID, never inferred from
# the visible exercise name.  The two anti-motion-control examples below are
# intentionally counted primarily as Core (deep trunk stabilization) while
# retaining Abdomen as a secondary contributor.  The decision is auditable in
# the candidate register and keeps Core distinct from rectus/oblique work.
MUSCLE_ROLE_OVERRIDES = {
    "core_plancha": {
        "primary": ["Core"],
        "secondary": ["Abdomen"],
        "rationale": "La ficha legacy explicita énfasis Transverso Abdominal/Core y el patrón anti-extensión isométrica; v2 lo registra como Core primario y Abdomen secundario.",
    },
    "core_press_pallof": {
        "primary": ["Core"],
        "secondary": ["Abdomen"],
        "rationale": "La ficha legacy explicita énfasis Oblicuos/Transverso y el patrón anti-rotación; v2 lo registra como Core primario y Abdomen secundario.",
    },
}

# Search vocabulary that must survive canonical renaming.  These phrases are
# search terms only; they never create a second definition or runtime alias.
SEARCH_TERM_OVERRIDES = {
    "quads_sentadilla_bulgara_maquina": ["Bulgaria en Máquina", "Bulgaria en Maquina"],
    "deltoides_aperturas_inversas_maquina_pec_deck": [
        "Aperturas inversas",
        "Aperturas inversas en máquina pec deck",
        "Aperturas inversas en polea",
        "Reverse pec fly",
        "Reverse cable fly",
    ],
}

EQUIPMENT_IDS = {
    "Banda": "band",
    "Banda Elástica": "band",
    "Barra": "barbell",
    "Barra EZ": "ez_bar",
    "Barra Hexagonal": "hex_bar",
    "Barra T": "t_bar",
    "Barra de Seguridad": "safety_bar",
    "Disco": "plate",
    "Kettlebell": "kettlebell",
    "Mancuerna": "dumbbells",
    "Máquina": "machine",
    "Máquina GHD": "ghd",
    "Máquina Smith": "smith_machine",
    "Peso Corporal": "bodyweight",
    "Polea": "cable",
    "Rodillo de Muñeca": "wrist_roller",
    "Rueda Abdominal": "ab_wheel",
    "Sliders": "sliders",
    "TRX": "trx",
}

# These labels are only used in the user-facing descriptions emitted for a
# resolved configuration.  The catalog keeps the stable ids in
# ``selectedOptions``; descriptions must remain readable Spanish and must not
# expose those ids (``pec_deck_reverse``, ``barbell_back``, etc.).
DESCRIPTION_OPTION_LABELS = {
    "implement": {
        "band": "banda de resistencia",
        "barbell": "barra",
        "hex_bar": "barra hexagonal",
        "cable": "polea",
        "dumbbells": "mancuernas",
        "ez_bar": "barra EZ",
        "kettlebell": "kettlebell",
        "machine": "máquina",
        "smith_machine": "máquina Smith",
        "safety_bar": "barra de seguridad",
        "bodyweight": "peso corporal",
        "sliders": "sliders",
    },
    "laterality": {"bilateral": "bilateral", "unilateral": "unilateral"},
    "load_position": {
        "barbell_back": "barra sobre la espalda",
        "cable_front": "polea frontal",
        "front": "carga frontal",
        "guided": "recorrido guiado",
        "bodyweight": "sin carga externa",
        "hack": "máquina Hack",
        "smith": "recorrido Smith",
        "sides": "carga a los lados",
        "zercher": "posición Zercher",
    },
    "posture": {
        "seated": "sentado",
        "standing": "de pie",
        "lying_flat": "acostado en banco plano",
        "incline_supported": "inclinado con apoyo",
        "side_lying": "recostado de lado",
    },
    "setup": {
        "arana": "posición araña",
        "bayesian": "posición bayesiana",
        "concentrado": "posición concentrada",
        "crucifijo": "posición crucifijo",
        "declinado": "banco declinado",
        "inclinado": "banco inclinado",
        "preacher": "banco predicador",
        "sentado_banco_plano": "sentado en banco plano",
        "standing": "de pie",
        "superman": "posición Superman",
        "seated_bench": "sentado con banco plano",
        "chest_supported": "pecho apoyado",
        "lying_flat": "acostado en banco plano",
        "spider": "posición araña",
    },
    "stance": {
        "neutral": "postura neutra",
        "conventional": "postura convencional",
        "bilateral": "dos piernas",
        "sumo": "postura sumo",
        "b_stance": "apoyo asimétrico B-stance",
    },
    "load": {"bodyweight": "sin carga externa", "plate": "disco"},
    "station": {
        "bench": "banco",
        "floor": "suelo",
        "floor_sliders": "suelo con sliders",
        "standing_cable": "de pie en polea",
        "seated_machine": "máquina sentado",
        "lying_machine": "máquina tumbado",
        "standing": "estación de pie",
        "donkey": "estación donkey",
        "leg_press": "prensa de piernas",
        "seated": "estación sentada",
        "pec_deck": "pec deck",
        "pec_deck_reverse": "pec deck inverso",
    },
    "support_angle": {
        "decline": "ángulo declinado",
        "flat": "ángulo plano",
        "incline": "ángulo inclinado",
        "seated": "posición sentada",
        "standing": "posición de pie",
        "feet_elevated": "pies elevados",
    },
}

DESCRIPTION_INSTRUCTION_MARKERS = (
    "es un ejercicio dentro de un patrón",
    "se realiza con",
    "se ejecuta con",
    "ejecuta ",
    "mantén",
    "mantener ",
    "configura ",
    "adopta ",
    "controla ",
    "asegura ",
    "evita ",
    "sigue ",
    "selecciona ",
    "progresión de carga",
)

DESCRIPTION_EQUIPMENT_LABELS = {
    "band": "banda de resistencia",
    "barbell": "barra",
    "cable": "polea",
    "dumbbells": "mancuernas",
    "machine": "máquina",
    "ez_bar": "barra EZ",
    "hex_bar": "barra hexagonal",
    "t_bar": "barra T",
    "plate": "disco",
    "kettlebell": "kettlebell",
    "bodyweight": "peso corporal",
    "sliders": "sliders",
    "ghd": "máquina GHD",
    "trx": "TRX",
    "safety_bar": "barra de seguridad",
}

MOVEMENT_IDS = {
    "Abducción / Estabilización": "hip_abduction_stability",
    "Abducción / Rotación Externa Cadera": "hip_abduction_external_rotation",
    "Abducción Cadera": "hip_abduction",
    "Abducción Completa": "shoulder_abduction_full_rom",
    "Abducción Diagonal": "shoulder_abduction_diagonal",
    "Abducción Hombro": "shoulder_abduction",
    "Abducción Horizontal": "horizontal_abduction",
    "Abducción/Extensión Cadera": "hip_abduction_extension",
    "Aducción Cadera": "hip_adduction",
    "Aducción Dinámica": "hip_adduction_dynamic",
    "Aducción Isométrica": "hip_adduction_isometric",
    "Anti-Extensión / Control Pelvis": "anti_extension_pelvic_control",
    "Anti-Extensión Isométrica": "anti_extension_isometric",
    "Anti-Extensión Tronco": "anti_extension_trunk",
    "Anti-Rotación Tronco": "anti_rotation_trunk",
    "Bisagra": "hip_hinge",
    "Bisagra Cadera": "hip_hinge",
    "Bisagra Cadera / Déficit": "hip_hinge_deficit",
    "Bisagra Cadera / Estiramiento": "hip_hinge_lengthened",
    "Bisagra Cadera / RDL": "romanian_deadlift",
    "Bisagra Cadera / RDL Déficit": "romanian_deadlift_deficit",
    "Bisagra Cadera / Tracción": "deadlift",
    "Bisagra Cadera Explosiva": "hip_hinge_explosive",
    "Bisagra Cadera Unilateral": "unilateral_hip_hinge",
    "Depresión Escapular": "scapular_depression",
    "Dominante Cadera Unilateral": "unilateral_hip_dominant",
    "Dominante Rodilla / Estiramiento": "knee_dominant_lengthened",
    "Dominante de Rodilla": "knee_dominant",
    "Dominante de Rodilla / Cadera": "knee_hip_dominant",
    "Dominante Rodilla Asimétrico": "knee_dominant_asymmetric",
    "Dominante de Rodilla Asimétrico": "knee_dominant_asymmetric",
    "Dominante de Rodilla Lateral": "lateral_knee_dominant",
    "Dominante de Rodilla Unilateral": "unilateral_knee_dominant",
    "Dominante de Rodilla Unilateral Asimétrico": "unilateral_knee_dominant_asymmetric",
    "Elevación Escapular": "scapular_elevation",
    "Empuje Diagonal": "diagonal_push",
    "Empuje Horizontal": "horizontal_push",
    "Empuje Vertical": "vertical_push",
    "Estiramiento Biarticular": "biarticular_lengthened",
    "Extensión Cadera": "hip_extension",
    "Extensión Cadera / Rotación Externa": "hip_extension_external_rotation",
    "Extensión Cadera Inversa": "reverse_hip_extension",
    "Extensión Cadera Unilateral": "unilateral_hip_extension",
    "Extensión Codo": "elbow_extension",
    "Extensión Columna": "spinal_extension",
    "Extensión Cuello": "neck_extension",
    "Extensión Muñeca": "wrist_extension",
    "Extensión Rodilla": "knee_extension",
    "Extensión Rodilla / Cadera": "knee_hip_extension",
    "Extensión/Abducción Cadera": "hip_extension_abduction",
    "Flexión Codo": "elbow_flexion",
    "Flexión Columna": "spinal_flexion",
    "Flexión Cuello": "neck_flexion",
    "Flexión Dorsal": "ankle_dorsiflexion",
    "Flexión Hombro": "shoulder_flexion",
    "Flexión Lateral Cuello": "neck_lateral_flexion",
    "Flexión Lateral Tronco": "lateral_trunk_flexion",
    "Flexión Muñeca": "wrist_flexion",
    "Flexión Pelvis / Cadera": "hip_flexion",
    "Flexión Plantar": "plantar_flexion",
    "Flexión Plantar Sóleo": "plantar_flexion_seated",
    "Flexión Rodilla": "knee_flexion",
    "Flexión Rodilla / Cadera": "knee_hip_flexion",
    "Flexión Rodilla Eccéntrica": "eccentric_knee_flexion",
    "Flexión Tronco": "trunk_flexion",
    "Flexión/Extensión Muñeca": "wrist_flexion_extension",
    "Fuerza Agarre Isométrica": "isometric_grip",
    "Fuerza Agarre Pinza": "pinch_grip",
    "Rotación Tronco": "trunk_rotation",
    "Tirón Horizontal": "horizontal_pull",
    "Tirón Vertical": "vertical_pull",
    "Tirón Vertical / Abducción": "vertical_pull_abduction",
}

BODY_IDS = {"upper": "UPPER", "lower": "LOWER", "core": "CORE"}
CHAIN_IDS = {"anterior": "ANTERIOR", "posterior": "POSTERIOR", "full": "FULL"}

SPECIALTY_TOKENS = (
    "zottman", "drag", "waiter", "trx", "pendlay", "seal", "renegado", "gorilla",
    "spoto", "cadenas", "arnold", "push_press", "push press", "super_rom", "nordic",
    "dragon_flag", "dragon flag", "copenhagen", "jefferson", "sissy", "pistol",
    "somersault", "pallof", "dead_hang", "reverse_hyper", "ghd", "katana", "tate",
    "jm_press", "california", "pjr", "rolling_extension", "farmer", "paseo_del_granjero",
)

CANONICAL_OVERRIDES = {
    "quads_sentadilla_bulgara_maquina": ("bulgarian_split_squat", "bulgarian__machine__guided"),
    "hams_buenos_dias": ("good_morning", "good_morning__standing__barbell_back"),
    "back_buenos_dias": ("good_morning", "good_morning__standing__cable"),
    "hams_buenos_dias_sentado": ("good_morning", "good_morning__seated__barbell"),
    "hams_buenos_dias_zercher": ("good_morning", "good_morning__standing__zercher"),
    "back_buenos_dias_zercher_barra": ("good_morning", "good_morning__standing__zercher"),
    "glutes_abduccion_cadera": ("hip_abduction", "hip_abduction__standing__cable__unilateral"),
    "glutes_abduccion_cadera_sentado_maquina": ("hip_abduction", "hip_abduction__seated__machine__bilateral"),
    "adductors_aduccion_cadera": ("hip_adduction", "hip_adduction__standing__cable__unilateral"),
    "adductors_aduccion_cadera_sentado_maquina": ("hip_adduction", "hip_adduction__seated__machine__bilateral"),
    "adductors_plancha_copenhagen_dinamica": ("copenhagen_plank", "copenhagen_plank__dynamic"),
    "adductors_plancha_copenhagen_peso_corporal": ("copenhagen_plank_isometric", "copenhagen_plank_isometric__bodyweight"),
    "tren_superior_aperturas_planas_mancuernas": ("chest_fly", "chest_fly__bench__flat__dumbbells"),
    "tren_superior_aperturas_inclinadas_mancuernas": ("chest_fly", "chest_fly__bench__incline__dumbbells"),
    "tren_superior_aperturas_declinadas_mancuernas": ("chest_fly", "chest_fly__bench__decline__dumbbells"),
    "tren_superior_aperturas_suelo_mancuernas": ("chest_fly", "chest_fly__floor__dumbbells"),
    "tren_superior_aperturas_pec_deck": ("chest_fly", "chest_fly__pec_deck__machine__machine"),
    "tren_superior_aperturas_banda": ("chest_fly", "chest_fly__standing__band__band"),
    "deltoides_aperturas_inversas_maquina_pec_deck": ("reverse_pec_fly", "reverse_pec_fly__pec_deck__machine"),
    "quads_sentadilla_bulgara_zercher": ("bulgarian_zercher", "bulgarian_zercher__barbell__zercher"),
    "biceps_curl_zottman_mancuernas": ("biceps_curl_zottman", "biceps_curl_zottman__dumbbells"),
    "biceps_curl_drag": ("biceps_curl_drag", "biceps_curl_drag__barbell__supinated"),
    "biceps_curl_disco": ("biceps_curl_waiter", "biceps_curl_waiter__plate"),
    "biceps_curl_trx": ("biceps_curl_trx", "biceps_curl_trx__supinated"),
    "deltoides_elevaciones_laterales_super_rom_mancuernas": ("lateral_raise_super_rom", "lateral_raise_super_rom__dumbbells__bilateral"),
    "deltoides_elevaciones_laterales_super_rom_polea_unilateral": ("lateral_raise_super_rom", "lateral_raise_super_rom__cable__unilateral"),
}


def slug(value: str) -> str:
    value = value.lower()
    value = value.translate(str.maketrans("áéíóúñü", "aeiounu"))
    return re.sub(r"[^a-z0-9]+", "_", value).strip("_")


def canonical_name(record: dict[str, Any]) -> str:
    name = record["name"].strip()
    return name.replace("Bulgaria en Máquina", "Sentadilla búlgara")


def evidence(*refs: str, rationale: str | None = None) -> dict[str, Any]:
    out: dict[str, Any] = {
        "reviewStatus": "APPROVED",
        "confidence": "MEDIUM",
        "evidenceRefs": list(refs) or ["editorial:catalog-v2-full-2026-08-02"],
    }
    if rationale:
        out["rationale"] = rationale
    return out


def profile_muscles(record: dict[str, Any]) -> dict[str, list[str]]:
    buckets: dict[str, list[str]] = {"primary": [], "secondary": [], "stabilizer": []}
    for involvement in record.get("involvedMuscles") or []:
        source = involvement.get("muscle")
        if source not in MUSCLE_IDS:
            raise ValueError(f"Unmapped muscle {source!r} in {record['id']}")
        role = involvement.get("role")
        bucket = "primary" if role == "primary" else "stabilizer" if role == "stabilizer" else "secondary"
        value = MUSCLE_IDS[source]
        if value not in buckets[bucket]:
            buckets[bucket].append(value)
    override = MUSCLE_ROLE_OVERRIDES.get(record["id"])
    if override:
        buckets = {
            "primary": [MUSCLE_IDS[muscle] for muscle in override["primary"]],
            "secondary": [MUSCLE_IDS[muscle] for muscle in override.get("secondary", [])],
            "stabilizer": [MUSCLE_IDS[muscle] for muscle in override.get("stabilizer", [])],
        }
    if not buckets["primary"]:
        raise ValueError(f"No primary muscle for {record['id']}")
    return buckets


def default_load_mode(equipment_id: str) -> str:
    return {
        "barbell": "free_external_load", "ez_bar": "free_external_load", "hex_bar": "free_external_load",
        "t_bar": "free_external_load", "dumbbells": "free_external_load", "kettlebell": "free_external_load",
        "plate": "free_external_load", "cable": "continuous_cable", "machine": "guided_external_load",
        "smith_machine": "guided_external_load", "ghd": "guided_external_load", "band": "variable_band_resistance",
        "bodyweight": "bodyweight", "trx": "suspension", "sliders": "bodyweight_with_sliding_resistance",
        "wrist_roller": "free_external_load", "ab_wheel": "free_external_load",
    }.get(equipment_id, "external_load")


def pattern_family(pattern_id: str, body_region: str, primary: str) -> tuple[str, str, list[str]]:
    labels = {
        "UPPER": "Tren superior", "LOWER": "Tren inferior", "CORE": "Core",
    }
    family_id = f"{body_region.lower()}_{pattern_id}"
    canonical = f"{labels[body_region]} · {primary.replace('_', ' ')} · {pattern_id.replace('_', ' ')}"
    taxonomy = [body_region.lower(), pattern_id]
    return family_id, canonical, taxonomy


def is_specialty(record: dict[str, Any]) -> bool:
    haystack = f"{record['id']} {record['name']} {record.get('description', '')}".lower()
    return any(token in haystack for token in SPECIALTY_TOKENS)


def display_summary(record: dict[str, Any]) -> str:
    equipment = record.get("equipment", "")
    return f"{canonical_name(record)} · {equipment}" if equipment else canonical_name(record)


def cue_text(record: dict[str, Any], phase: str) -> str:
    name = canonical_name(record)
    equipment = record.get("equipment", "")
    movement = record.get("movementPattern", "")
    if phase == "setup":
        return f"Configura {equipment.lower()} y adopta la posición específica de «{name}» antes de iniciar; conserva una base estable."
    if phase == "execution":
        return f"Ejecuta «{name}» siguiendo el patrón {movement.lower()} con recorrido controlado y sin cambiar la trayectoria bajo carga."
    return f"Usar impulso, perder la posición de inicio o transformar el patrón {movement.lower()} para completar la repetición."


def description_is_instructional(value: str) -> bool:
    lowered = value.lower()
    return any(marker in lowered for marker in DESCRIPTION_INSTRUCTION_MARKERS)


def description_muscles(record: dict[str, Any]) -> tuple[list[str], list[str]]:
    involved = record.get("involvedMuscles") or []
    override = MUSCLE_ROLE_OVERRIDES.get(record["id"])
    if override:
        return override["primary"], override.get("secondary", [])
    return (
        [item["muscle"] for item in involved if item.get("role") == "primary"],
        [item["muscle"] for item in involved if item.get("role") == "secondary"],
    )


def factual_description(record: dict[str, Any], *, subject: str, kind: str) -> str:
    primary, secondary = description_muscles(record)
    primary_text = ", ".join(primary) or "la musculatura declarada"
    movement = record["movementPattern"].lower()
    text = f"{subject} trabaja principalmente {primary_text.lower()} mediante un patrón de {movement}."
    if secondary:
        text += f" La participación secundaria corresponde a {', '.join(secondary).lower()}."
    return text


def option_description(selected_options: dict[str, str]) -> str:
    labels: list[str] = []
    for axis, value in selected_options.items():
        label = DESCRIPTION_OPTION_LABELS.get(axis, {}).get(value, value.replace("_", " "))
        if label not in labels:
            labels.append(label)
    return " · ".join(labels)


def configuration_description(
    record: dict[str, Any],
    *,
    canonical: str,
    kind: str,
    selected_options: dict[str, str],
    equipment_override: str | None = None,
) -> str:
    """Return a dedicated, factual description for one materialized config.

    A configuration description is deliberately separate from setup/execution
    cues.  When legacy prose is usable, preserve its exercise-specific detail;
    otherwise rebuild a factual sentence from the audited movement and muscle
    fields.  In both cases append the explicit chip choices so two real
    configurations of one parent cannot collapse into the same description.
    """
    existing = (record.get("description") or "").strip()
    subject = canonical_name(record) or canonical
    if existing and len(existing) >= 40 and not description_is_instructional(existing):
        text = existing.rstrip(" .") + "."
    else:
        text = factual_description(record, subject=subject, kind=kind)

    equipment_id = equipment_override or EQUIPMENT_IDS.get(record.get("equipment", ""), "")
    equipment = DESCRIPTION_EQUIPMENT_LABELS.get(equipment_id, record.get("equipment", ""))
    if equipment and equipment.lower() not in text.lower():
        text += f" La configuración utiliza {equipment} como implemento."

    options = option_description(selected_options)
    if options:
        text += f" La variante se define por: {options}."
    return text


def profile(record: dict[str, Any], *, config_id: str, definition_id: str, family_id: str,
            selected_options: dict[str, str], kind: str, config_name: str | None = None,
            equipment_override: str | None = None, laterality: str | None = None,
            movement_override: str | None = None) -> dict[str, Any]:
    muscles = profile_muscles(record)
    equipment_id = equipment_override or EQUIPMENT_IDS[record["equipment"]]
    movement_id = movement_override or MOVEMENT_IDS[record["movementPattern"]]
    body_region = BODY_IDS[record["bodyPart"]]
    chain = CHAIN_IDS[record["chain"]]
    laterality = laterality or ("UNILATERAL" if any(word in record["name"].lower() for word in ("unilateral", "una mano", "unipodal")) else "BILATERAL")
    if record["bodyPart"] == "core":
        laterality = "NOT_APPLICABLE"
    metrics = {key: float(record.get(key) or 0.0) for key in ("efc", "cnc", "ssc", "ttc")}
    difficulty = max(1.0, min(10.0, round(2.0 + metrics["cnc"] + (1.0 if record.get("type") == "Básico" else 0.0), 1)))
    if is_specialty(record):
        difficulty = min(10.0, difficulty + 1.0)
    axial = 0.0
    if body_region == "LOWER" and equipment_id in {"barbell", "hex_bar", "smith_machine"}:
        axial = 0.7 if movement_id not in {"hip_hinge", "deadlift", "romanian_deadlift"} else 1.0
    resistance = "body_angle" if equipment_id == "trx" else "variable_band" if equipment_id == "band" else "continuous_cable" if equipment_id == "cable" else "guided_constant" if equipment_id in {"machine", "smith_machine", "ghd"} else "gravity_arc"
    primary = muscles["primary"][0]
    performance = f"{definition_id}__{equipment_id}__{slug(config_name or record['id'])}"
    setup = [cue_text(record, "setup")]
    execution = [cue_text(record, "execution")]
    mistakes = [cue_text(record, "mistake")]
    return {
        "movementPatternId": movement_id,
        "bodyRegion": body_region,
        "kineticChain": chain,
        "laterality": laterality,
        "equipmentId": equipment_id,
        "loadMode": default_load_mode(equipment_id),
        "primaryMuscles": muscles["primary"],
        "secondaryMuscles": muscles["secondary"],
        "stabilizerMuscles": muscles["stabilizer"],
        "efc": metrics["efc"], "cnc": metrics["cnc"], "ssc": metrics["ssc"], "ttc": metrics["ttc"],
        "axialLoadFactor": axial,
        "technicalDifficulty": difficulty,
        "resistanceProfile": resistance,
        "description": configuration_description(
            record,
            canonical=config_name or record["name"],
            kind=kind,
            selected_options=selected_options,
            equipment_override=equipment_override,
        ),
        "setupCues": setup, "executionCues": execution, "commonMistakes": mistakes,
        "performanceProfileId": performance,
        "replacementGroup": record.get("replacementGroup"),
        "replacementPriority": record.get("replacementPriority"),
        "automationEligible": True,
        "_source_record": record,
        "_kind": kind,
    }


def rich_metadata(profile_value: dict[str, Any], *, family: dict[str, Any], definition: dict[str, Any], configuration: dict[str, Any]) -> dict[str, Any]:
    record = profile_value.pop("_source_record")
    profile_value.pop("_kind", None)
    body = profile_value["bodyRegion"]
    joints = {"UPPER": ["shoulder", "elbow", "scapulothoracic"], "LOWER": ["hip", "knee", "ankle"], "CORE": ["spine", "hip"]}[body]
    tendons = [f"{joint}_tendon" for joint in joints if joint != "scapulothoracic"]
    target_regions = [profile_value["primaryMuscles"][0]]
    rest = int(record.get("averageRestSeconds") or 90)
    rest = max(30, min(300, rest))
    type_value = record.get("type")
    role = "primary_compound" if type_value == "Básico" else "accessory_isolation" if type_value == "Aislamiento" else "accessory_compound"
    reps = ["4-8", "6-12"] if type_value == "Básico" else ["8-15", "12-20"]
    intensity = "high" if profile_value["cnc"] >= 3.0 else "moderate" if profile_value["cnc"] >= 1.5 else "low"
    stability = "guided" if profile_value["equipmentId"] in {"machine", "smith_machine", "ghd"} else "supported" if "seated" in configuration["displaySummary"].lower() or "banco" in configuration["displaySummary"].lower() else "self_stabilized"
    range_of_motion = "limited_by_support" if "suelo" in record.get("description", "").lower() else "controlled_full_available"
    rich = {
        "identity": {
            "catalogRevision": REVISION, "familyId": family["id"], "definitionId": definition["id"],
            "configurationId": configuration["id"], "canonicalName": definition["canonicalName"],
            "searchTerms": definition.get("searchTerms", []), "kind": definition["kind"],
            "performanceProfileId": profile_value["performanceProfileId"],
        },
        "anatomy": {
            "primaryMuscles": profile_value["primaryMuscles"], "secondaryMuscles": profile_value["secondaryMuscles"],
            "stabilizerMuscles": profile_value["stabilizerMuscles"], "targetRegions": target_regions,
            "jointActions": [profile_value["movementPatternId"]], "muscleLengthBias": "mixed_controlled",
            "volumeContribution": "direct" if role != "accessory_compound" else "indirect",
            "stabilizationDemand": "high" if profile_value["stabilizerMuscles"] else "moderate",
        },
        "biomechanics": {
            key: profile_value[key] for key in ("movementPatternId", "bodyRegion", "kineticChain", "laterality", "equipmentId", "loadMode", "resistanceProfile")
        } | {"rangeOfMotion": range_of_motion, "stability": stability, "relevantJoints": joints, "relevantTendons": tendons},
        "programming": {
            "role": role, "objectives": [f"Desarrollar {profile_value['primaryMuscles'][0]} dentro del patrón {profile_value['movementPatternId']}.", "Mantener la técnica de la configuración seleccionada."],
            "suitableRepRanges": reps, "indicativeRestSeconds": {"min": max(30, rest - 30), "max": min(360, rest + 30)},
            "fatigueCost": intensity, "recoveryCost": intensity, "requiredEquipment": [profile_value["equipmentId"]],
            "setupTransitionCost": "high" if profile_value["equipmentId"] in {"machine", "ghd"} else "low",
            "splitSuitability": ["full_body", "upper_lower"] if body != "CORE" else ["full_body", "core_accessory"],
        },
        "fatigue": {key: profile_value[key] for key in ("efc", "cnc", "ssc", "ttc", "axialLoadFactor", "technicalDifficulty")},
        "replacement": {
            "replacementGroup": profile_value.get("replacementGroup"), "replacementPriority": profile_value.get("replacementPriority"),
            "compatibleEquipmentIds": [profile_value["equipmentId"]], "preservesIntent": [f"Conserva el patrón {profile_value['movementPatternId']} y el objetivo {profile_value['primaryMuscles'][0]}."],
        },
        "coaching": {
            "setup": profile_value["setupCues"], "execution": profile_value["executionCues"],
            "cues": [f"Mantén el equipo {profile_value['equipmentId']} alineado con el patrón y controla la fase excéntrica."],
            "commonMistakes": profile_value["commonMistakes"],
            "progressions": ["Aumentar gradualmente la carga solo si se conserva la configuración exacta."],
            "regressions": ["Reducir carga o rango manteniendo la misma configuración y el mismo patrón."],
            "relevantMobility": [f"Movilidad de {joint} según tolerancia y rango disponible." for joint in joints[:2]],
        },
        "safety": {
            "risks": [f"La pérdida de la posición puede trasladar carga fuera del patrón {profile_value['movementPatternId']}."] if profile_value["technicalDifficulty"] >= 5 else [],
            "precautions": ["Detener la serie si aparece dolor agudo o pérdida de control; no diagnostica lesiones."],
            "medicalDisclaimerRequired": False,
        },
        "display": {"displayName": definition["canonicalName"], "displaySummary": configuration["displaySummary"], "selectedOptions": configuration["selectedOptions"]},
        "evidenceConfidence": "MEDIUM",
    }
    profile_value["richMetadata"] = rich
    return profile_value


def make_config(record: dict[str, Any], *, family_id: str, definition_id: str, kind: str, config_id: str,
                selected_options: dict[str, str], canonical: str | None = None,
                display: str | None = None, equipment_override: str | None = None,
                laterality: str | None = None, movement_override: str | None = None) -> dict[str, Any]:
    config = {
        "id": config_id,
        "selectedOptions": selected_options,
        "displaySummary": display or display_summary(record),
        "profile": profile(record, config_id=config_id, definition_id=definition_id, family_id=family_id, selected_options=selected_options, kind=kind, config_name=canonical or record["name"], equipment_override=equipment_override, laterality=laterality, movement_override=movement_override),
        "evidence": evidence(f"legacy:exercise_database.json#{record['id']}", "editorial:catalog-v2-full-2026-08-02"),
    }
    config["profile"] = rich_metadata(config["profile"], family={"id": family_id}, definition={"id": definition_id, "canonicalName": canonical or record["name"], "kind": kind, "searchTerms": []}, configuration=config)
    return config


def make_description(record: dict[str, Any], *, canonical: str, kind: str) -> str:
    existing = (record.get("description") or "").strip()
    # A parent description must never inherit the first row's station or
    # implement: those details belong to its chips.  Specialty descriptions
    # can preserve audited prose when it is genuinely descriptive.
    if kind != "PARENT" and existing and len(existing) >= 40 and not description_is_instructional(existing):
        return existing
    return factual_description(record, subject=canonical, kind=kind)


def curation_rationale(source_id: str, base: str) -> str:
    override = MUSCLE_ROLE_OVERRIDES.get(source_id)
    if not override:
        return base
    return f"{base} {override['rationale']}"


def search_terms_for_record(record: dict[str, Any], canonical: str) -> list[str]:
    terms = [canonical.lower(), record["name"].lower()]
    terms.extend(x.strip().lower() for x in (record.get("alias") or "").split(",") if x.strip())
    terms.extend(x.lower() for x in SEARCH_TERM_OVERRIDES.get(record["id"], []))
    return sorted(set(terms))


def build() -> tuple[dict[str, Any], dict[str, Any]]:
    rows = {row["id"]: row for row in json.loads(ASSET.read_text(encoding="utf-8"))}
    families: dict[str, dict[str, Any]] = {}
    definitions: dict[str, dict[str, Any]] = {}
    decisions: dict[str, dict[str, Any]] = {}

    # Reuse the seven pilot definitions but rebuild their configurations from
    # source records so equipment and rich metadata are always coherent.
    pilot_rows: dict[str, tuple[str, str, dict[str, str], str, str | None]] = {
        "hams_buenos_dias": ("hinge_good_morning", "good_morning", {"implement": "barbell", "load_position": "barbell_back", "posture": "standing"}, "Buenos días", "good_morning__standing__barbell_back"),
        "back_buenos_dias": ("hinge_good_morning", "good_morning", {"implement": "cable", "load_position": "cable_front", "posture": "standing"}, "Buenos días", "good_morning__standing__cable"),
        "hams_buenos_dias_sentado": ("hinge_good_morning", "good_morning", {"implement": "barbell", "load_position": "barbell_back", "posture": "seated"}, "Buenos días", "good_morning__seated__barbell"),
        "hams_buenos_dias_zercher": ("hinge_good_morning", "good_morning", {"implement": "barbell", "load_position": "zercher", "posture": "standing"}, "Buenos días", "good_morning__standing__zercher"),
        "back_buenos_dias_zercher_barra": ("hinge_good_morning", "good_morning", {"implement": "barbell", "load_position": "zercher", "posture": "standing"}, "Buenos días", "good_morning__standing__zercher"),
        "glutes_abduccion_cadera_sentado_maquina": ("hip_abduction", "hip_abduction", {"station": "seated", "implement": "machine", "laterality": "bilateral"}, "Abducción de cadera", "hip_abduction__seated__machine__bilateral"),
        "glutes_abduccion_cadera": ("hip_abduction", "hip_abduction", {"station": "standing", "implement": "cable", "laterality": "unilateral"}, "Abducción de cadera", "hip_abduction__standing__cable__unilateral"),
        "adductors_aduccion_cadera_sentado_maquina": ("hip_adduction", "hip_adduction", {"station": "seated", "implement": "machine", "laterality": "bilateral"}, "Aducción de cadera", "hip_adduction__seated__machine__bilateral"),
        "adductors_aduccion_cadera": ("hip_adduction", "hip_adduction", {"station": "standing", "implement": "cable", "laterality": "unilateral"}, "Aducción de cadera", "hip_adduction__standing__cable__unilateral"),
        "adductors_plancha_copenhagen_dinamica": ("hip_adduction", "copenhagen_plank", {}, "Plancha Copenhagen", "copenhagen_plank__dynamic"),
        "tren_superior_aperturas_planas_mancuernas": ("chest_fly", "chest_fly", {"station": "bench", "support_angle": "flat", "implement": "dumbbells"}, "Aperturas de pecho", "chest_fly__bench__flat__dumbbells"),
        "tren_superior_aperturas_inclinadas_mancuernas": ("chest_fly", "chest_fly", {"station": "bench", "support_angle": "incline", "implement": "dumbbells"}, "Aperturas de pecho", "chest_fly__bench__incline__dumbbells"),
        "tren_superior_aperturas_declinadas_mancuernas": ("chest_fly", "chest_fly", {"station": "bench", "support_angle": "decline", "implement": "dumbbells"}, "Aperturas de pecho", "chest_fly__bench__decline__dumbbells"),
        "tren_superior_aperturas_suelo_mancuernas": ("chest_fly", "chest_fly", {"station": "floor", "support_angle": "flat", "implement": "dumbbells"}, "Aperturas de pecho", "chest_fly__floor__dumbbells"),
        "tren_superior_aperturas_pec_deck": ("chest_fly", "chest_fly", {"station": "pec_deck", "support_angle": "seated", "implement": "machine"}, "Aperturas de pecho", "chest_fly__pec_deck__machine__machine"),
        "tren_superior_aperturas_banda": ("chest_fly", "chest_fly", {"station": "standing", "support_angle": "standing", "implement": "band"}, "Aperturas de pecho", "chest_fly__standing__band__band"),
        "deltoides_aperturas_inversas_maquina_pec_deck": ("chest_fly", "reverse_pec_fly", {"station": "pec_deck_reverse", "implement": "machine"}, "Aperturas inversas", "reverse_pec_fly__pec_deck__machine"),
        "quads_sentadilla_bulgara_maquina": ("unilateral_knee_dominant_bulgarian", "bulgarian_split_squat", {"implement": "machine", "load_position": "guided"}, "Sentadilla búlgara", "bulgarian__machine__guided"),
        "quads_sentadilla_bulgara": ("unilateral_knee_dominant_bulgarian", "bulgarian_split_squat", {"implement": "dumbbells", "load_position": "sides"}, "Sentadilla búlgara", "bulgarian__dumbbells__sides"),
        "quads_sentadilla_bulgara_frontal": ("unilateral_knee_dominant_bulgarian", "bulgarian_split_squat", {"implement": "barbell", "load_position": "front"}, "Sentadilla búlgara", "bulgarian__front_barbell__front"),
        "quads_sentadilla_bulgara_zercher": ("unilateral_knee_dominant_bulgarian", "bulgarian_zercher", {}, "Sentadilla búlgara Zercher", "bulgarian_zercher__barbell__zercher"),
        "biceps_curl_de_pie": ("elbow_flexion_biceps_curl", "biceps_curl", {"setup": "standing", "implement": "barbell"}, "Curl de bíceps", "biceps_curl__standing__barbell"),
        "biceps_curl_predicador": ("elbow_flexion_biceps_curl", "biceps_curl", {"setup": "preacher", "implement": "barbell"}, "Curl de bíceps", "biceps_curl__preacher__barbell"),
        "biceps_curl_bayesian": ("elbow_flexion_biceps_curl", "biceps_curl", {"setup": "bayesian", "implement": "dumbbells"}, "Curl de bíceps", "biceps_curl__bayesian__dumbbells"),
        "biceps_curl_zottman_mancuernas": ("elbow_flexion_biceps_curl", "biceps_curl_zottman", {}, "Curl Zottman", "biceps_curl_zottman__dumbbells"),
        "biceps_curl_drag": ("elbow_flexion_biceps_curl", "biceps_curl_drag", {}, "Curl drag", "biceps_curl_drag__barbell__supinated"),
        "biceps_curl_disco": ("elbow_flexion_biceps_curl", "biceps_curl_waiter", {}, "Curl Waiter", "biceps_curl_waiter__plate"),
        "biceps_curl_trx": ("elbow_flexion_biceps_curl", "biceps_curl_trx", {}, "Curl de bíceps en TRX", "biceps_curl_trx__supinated"),
        "deltoides_elevaciones_laterales_super_rom_mancuernas": ("shoulder_lateral_raise", "lateral_raise_super_rom", {"implement": "dumbbells", "laterality": "bilateral"}, "Elevación lateral Super ROM", "lateral_raise_super_rom__dumbbells__bilateral"),
        "deltoides_elevaciones_laterales_super_rom_polea_unilateral": ("shoulder_lateral_raise", "lateral_raise_super_rom", {"implement": "cable", "laterality": "unilateral"}, "Elevación lateral Super ROM", "lateral_raise_super_rom__cable__unilateral"),
        "deltoides_elevaciones_laterales_de_pie": ("shoulder_lateral_raise", "lateral_raise", {"implement": "dumbbells", "posture": "standing", "laterality": "bilateral"}, "Elevación lateral", "lateral_raise__standing__dumbbells__bilateral"),
        "deltoides_elevaciones_laterales_sentado": ("shoulder_lateral_raise", "lateral_raise", {"implement": "machine", "posture": "seated", "laterality": "bilateral"}, "Elevación lateral", "lateral_raise__seated__machine__bilateral"),
        # These base rows are grouped only where the legacy technical-aspect
        # matrix and the system templates agree on a real implement choice.
        "back_pullover": ("upper_vertical_pull_pullover", "back_pullover", {"implement": "dumbbells"}, "Pullover", "back_pullover__dumbbells"),
        "triceps_patada": ("upper_elbow_extension_kickback", "triceps_patada", {"implement": "dumbbells", "laterality": "bilateral"}, "Patada de tríceps", "triceps_patada__dumbbells__bilateral"),
        "glutes_hip_thrust": ("lower_hip_extension_hip_thrust", "glutes_hip_thrust", {"implement": "barbell", "laterality": "bilateral"}, "Hip Thrust", "glutes_hip_thrust__barbell__bilateral"),
        "quads_extension_cuadriceps": ("lower_knee_extension", "quads_extension_cuadriceps", {"laterality": "bilateral"}, "Extensión de cuádriceps", "quads_extension_cuadriceps__machine__bilateral"),
        "deltoides_press_hombros_sentado": ("upper_vertical_push_seated_press", "deltoides_press_hombros_sentado", {"implement": "barbell"}, "Press de hombros sentado", "deltoides_press_hombros_sentado__barbell"),
        "forearms_curl_muneca_sentado": ("lower_wrist_flexion", "forearms_curl_muneca_sentado", {"implement": "barbell"}, "Curl de muñeca sentado", "forearms_curl_muneca_sentado__barbell"),
        # Romanian deadlift is a parent; the standard and sumo stances are
        # explicit, compatible configurations. Deficit and Zercher remain
        # independent specialties; B-stance is retained as an explicit stance
        # value because it is still the same RDL family and is fully profiled.
        "hams_peso_muerto_rumano": ("hinge_rdl", "romanian_deadlift", {"implement": "barbell", "stance": "bilateral"}, "Peso muerto rumano", "romanian_deadlift__bilateral__barbell"),
        "hams_peso_muerto_rumano_sumo": ("hinge_rdl", "romanian_deadlift", {"implement": "barbell", "stance": "sumo"}, "Peso muerto rumano", "romanian_deadlift__sumo__barbell"),
        "hams_peso_muerto_rumano_b_stance": ("hinge_rdl", "romanian_deadlift", {"implement": "dumbbells", "stance": "b_stance"}, "Peso muerto rumano", "romanian_deadlift__b_stance__dumbbells"),

        # The three standard deadlift records are one movement identity.  The
        # hex bar is neutral; the straight bar exposes the conventional/sumo
        # stance only after the implement choice. Deficit and other named
        # methods remain separate specialties.
        "hams_peso_muerto": ("hinge_deadlift", "deadlift", {"implement": "hex_bar", "stance": "neutral"}, "Peso muerto", "deadlift__neutral__hex_bar"),
        "hams_peso_muerto_convencional": ("hinge_deadlift", "deadlift", {"implement": "barbell", "stance": "conventional"}, "Peso muerto", "deadlift__conventional__barbell"),
        "hams_peso_muerto_sumo": ("hinge_deadlift", "deadlift", {"implement": "barbell", "stance": "sumo"}, "Peso muerto", "deadlift__sumo__barbell"),

        # Leg-curl stations are the same knee-flexion identity.  Only the
        # explicitly audited implement/station/laterality combinations exist;
        # no seated/lying or bilateral combinations are invented.
        "hams_curl_femoral": ("lower_knee_flexion", "leg_curl", {"implement": "sliders", "station": "floor_sliders", "laterality": "bilateral"}, "Curl femoral", "leg_curl__sliders__floor__bilateral"),
        "hams_curl_femoral_pie_polea": ("lower_knee_flexion", "leg_curl", {"implement": "cable", "station": "standing_cable", "laterality": "unilateral"}, "Curl femoral", "leg_curl__cable__standing__unilateral"),
        "hams_curl_femoral_sentado_unilateral_maquina": ("lower_knee_flexion", "leg_curl", {"implement": "machine", "station": "seated_machine", "laterality": "unilateral"}, "Curl femoral", "leg_curl__machine__seated__unilateral"),
        "hams_curl_femoral_tumbado_unilateral_maquina": ("lower_knee_flexion", "leg_curl", {"implement": "machine", "station": "lying_machine", "laterality": "unilateral"}, "Curl femoral", "leg_curl__machine__lying__unilateral"),

        # Weighted GHR is a load configuration of the same GHD movement, not
        # a second exercise card.
        "hams_glute_ham_raise": ("lower_knee_hip_extension", "glute_ham_raise", {"load": "bodyweight"}, "Glute-Ham Raise", "glute_ham_raise__bodyweight"),
        "hams_glute_ham_raise_lastrado_disco": ("lower_knee_hip_extension", "glute_ham_raise", {"load": "plate"}, "Glute-Ham Raise", "glute_ham_raise__plate"),

        # Bilateral/unilateral machine versions are a single squat identity;
        # the lateral choice is explicit and is the only real axis here.
        "quads_sentadilla_belt_squat_maquina": ("lower_knee_dominant_belt_squat", "belt_squat", {"laterality": "bilateral"}, "Sentadilla Belt Squat", "belt_squat__bilateral"),
        "quads_sentadilla_belt_squat_unilateral_maquina": ("lower_knee_dominant_belt_squat", "belt_squat", {"laterality": "unilateral"}, "Sentadilla Belt Squat", "belt_squat__unilateral"),
        "quads_sentadilla_pendulo_maquina": ("lower_knee_dominant_pendulum", "pendulum_squat", {"laterality": "bilateral"}, "Sentadilla péndulo", "pendulum_squat__bilateral"),
        "quads_sentadilla_pendulo_unilateral_maquina": ("lower_knee_dominant_pendulum", "pendulum_squat", {"laterality": "unilateral"}, "Sentadilla péndulo", "pendulum_squat__unilateral"),

        # Standard push-ups and feet-elevated push-ups share the same parent;
        # the sphinx and other named methods remain specialties.
        "tren_superior_flexiones": ("upper_horizontal_push", "push_up", {"support_angle": "flat"}, "Flexiones de brazos", "push_up__flat"),
        "tren_superior_flexiones_clasicas": ("upper_horizontal_push", "push_up", {"support_angle": "flat"}, "Flexiones de brazos", "push_up__flat"),
        "tren_superior_flexiones_pies_elevados": ("upper_horizontal_push", "push_up", {"support_angle": "feet_elevated"}, "Flexiones de brazos", "push_up__feet_elevated"),

        # Explicitly materialized implement/laterality variants of the same
        # cross-body elbow-extension identity.
        "triceps_crossbody_banda": ("upper_elbow_extension_crossbody", "crossbody_triceps_extension", {"implement": "band", "laterality": "bilateral"}, "Extensión de tríceps cruzada", "crossbody_triceps__band__bilateral"),
        "triceps_crossbody_kettlebell": ("upper_elbow_extension_crossbody", "crossbody_triceps_extension", {"implement": "kettlebell", "laterality": "bilateral"}, "Extensión de tríceps cruzada", "crossbody_triceps__kettlebell__bilateral"),
        "triceps_crossbody_mancuerna": ("upper_elbow_extension_crossbody", "crossbody_triceps_extension", {"implement": "dumbbells", "laterality": "bilateral"}, "Extensión de tríceps cruzada", "crossbody_triceps__dumbbells__bilateral"),
        "triceps_crossbody_polea": ("upper_elbow_extension_crossbody", "crossbody_triceps_extension", {"implement": "cable", "laterality": "bilateral"}, "Extensión de tríceps cruzada", "crossbody_triceps__cable__bilateral"),
        "triceps_crossbody_polea_unilateral": ("upper_elbow_extension_crossbody", "crossbody_triceps_extension", {"implement": "cable", "laterality": "unilateral"}, "Extensión de tríceps cruzada", "crossbody_triceps__cable__unilateral"),

        "triceps_overhead": ("upper_elbow_extension_overhead", "overhead_triceps_extension", {"implement": "barbell"}, "Extensión de tríceps overhead", "overhead_triceps__barbell"),
        "triceps_overhead_maquina": ("upper_elbow_extension_overhead", "overhead_triceps_extension", {"implement": "machine"}, "Extensión de tríceps overhead", "overhead_triceps__machine"),

        # The source explicitly materializes only these three pulldown
        # implements; grip aliases remain search vocabulary, not chips.
        "back_jalon_banda": ("upper_vertical_pull_lat_pulldown", "lat_pulldown", {"implement": "band"}, "Jalón al pecho", "lat_pulldown__band"),
        "back_jalon_pecho_maquina": ("upper_vertical_pull_lat_pulldown", "lat_pulldown", {"implement": "machine"}, "Jalón al pecho", "lat_pulldown__machine"),
        "back_jalon_pecho_polea": ("upper_vertical_pull_lat_pulldown", "lat_pulldown", {"implement": "cable"}, "Jalón al pecho", "lat_pulldown__cable"),

        # All four records are plantar-flexion variants; station is the only
        # materialized axis and seated remains distinct in its profile.
        "calves_talones_de_pie": ("lower_plantar_flexion", "calf_raise", {"station": "standing", "implement": "machine", "laterality": "bilateral"}, "Elevación de talones", "calf_raise__standing__machine__bilateral"),
        "calves_talones_donkey": ("lower_plantar_flexion", "calf_raise", {"station": "donkey", "implement": "machine", "laterality": "bilateral"}, "Elevación de talones", "calf_raise__donkey__machine__bilateral"),
        "calves_talones_prensa": ("lower_plantar_flexion", "calf_raise", {"station": "leg_press", "implement": "machine", "laterality": "bilateral"}, "Elevación de talones", "calf_raise__leg_press__machine__bilateral"),
        "calves_talones_sentado": ("lower_plantar_flexion", "calf_raise", {"station": "seated", "implement": "machine", "laterality": "bilateral"}, "Elevación de talones", "calf_raise__seated__machine__bilateral"),
        # The isometric Copenhagen plank is not interchangeable with the
        # dynamic eccentric version, so it has its own specialty definition.
        "adductors_plancha_copenhagen_peso_corporal": ("hip_adduction", "copenhagen_plank_isometric", {}, "Plancha Copenhagen isométrica", "copenhagen_plank_isometric__bodyweight"),

        # Safe equipment families: the movement identity and setup remain the
        # same; only an explicit implement (and, where needed, laterality or
        # support position) changes.  Paused, deficit, eccentric, asymmetrical
        # and named-method variants not listed here remain specialties.
        "tren_superior_press_banca_plano_barra": ("chest_press", "bench_press", {"implement": "barbell"}, "Press de banca", "bench_press__barbell"),
        "tren_superior_press_banca_plano_mancuernas": ("chest_press", "bench_press", {"implement": "dumbbells"}, "Press de banca", "bench_press__dumbbells"),
        "tren_superior_floor_press_barra": ("chest_floor_press", "floor_press", {"implement": "barbell"}, "Floor press", "floor_press__barbell"),
        "tren_superior_floor_press_mancuernas": ("chest_floor_press", "floor_press", {"implement": "dumbbells"}, "Floor press", "floor_press__dumbbells"),
        "triceps_jm_press_barra_ez": ("triceps_jm_press", "jm_press", {"implement": "ez_bar"}, "JM press", "jm_press__ez_bar"),
        "triceps_jm_press_mancuernas": ("triceps_jm_press", "jm_press", {"implement": "dumbbells"}, "JM press", "jm_press__dumbbells"),
        "triceps_press_california_barra_recta": ("triceps_california_press", "california_press", {"implement": "barbell"}, "Press California", "california_press__barbell"),
        "triceps_press_california_barra_ez": ("triceps_california_press", "california_press", {"implement": "ez_bar"}, "Press California", "california_press__ez_bar"),
        "triceps_press_california_mancuernas": ("triceps_california_press", "california_press", {"implement": "dumbbells"}, "Press California", "california_press__dumbbells"),
        "triceps_tate_press_mancuernas": ("triceps_tate_press", "tate_press", {"implement": "dumbbells"}, "Tate press", "tate_press__dumbbells"),
        "triceps_tate_press_polea": ("triceps_tate_press", "tate_press", {"implement": "cable"}, "Tate press", "tate_press__cable"),
        "deltoides_press_arnold_mancuernas": ("shoulder_arnold_press", "arnold_press", {"implement": "dumbbells"}, "Press Arnold", "arnold_press__dumbbells"),
        "deltoides_press_arnold_kettlebell": ("shoulder_arnold_press", "arnold_press", {"implement": "kettlebell"}, "Press Arnold", "arnold_press__kettlebell"),
        "deltoides_press_arnold_polea": ("shoulder_arnold_press", "arnold_press", {"implement": "cable"}, "Press Arnold", "arnold_press__cable"),
        "deltoides_press_z_barra_recta": ("shoulder_z_press", "z_press", {"implement": "barbell"}, "Press Z", "z_press__barbell"),
        "deltoides_press_z_barra_ez": ("shoulder_z_press", "z_press", {"implement": "ez_bar"}, "Press Z", "z_press__ez_bar"),
        "deltoides_press_z_mancuernas": ("shoulder_z_press", "z_press", {"implement": "dumbbells"}, "Press Z", "z_press__dumbbells"),
        "deltoides_press_z_kettlebell": ("shoulder_z_press", "z_press", {"implement": "kettlebell"}, "Press Z", "z_press__kettlebell"),
        "triceps_katana_polea": ("triceps_katana_extension", "katana_extension", {"implement": "cable", "laterality": "bilateral"}, "Extensión Katana", "katana_extension__cable__bilateral"),
        "triceps_katana_polea_unilateral": ("triceps_katana_extension", "katana_extension", {"implement": "cable", "laterality": "unilateral"}, "Extensión Katana", "katana_extension__cable__unilateral"),
        "triceps_katana_mancuerna": ("triceps_katana_extension", "katana_extension", {"implement": "dumbbells", "laterality": "bilateral"}, "Extensión Katana", "katana_extension__dumbbells__bilateral"),
        "triceps_katana_kettlebell": ("triceps_katana_extension", "katana_extension", {"implement": "kettlebell", "laterality": "bilateral"}, "Extensión Katana", "katana_extension__kettlebell__bilateral"),
        "triceps_katana_banda": ("triceps_katana_extension", "katana_extension", {"implement": "band", "laterality": "bilateral"}, "Extensión Katana", "katana_extension__band__bilateral"),
        "triceps_katana_barra_ez": ("triceps_katana_extension", "katana_extension", {"implement": "ez_bar", "laterality": "bilateral"}, "Extensión Katana", "katana_extension__ez_bar__bilateral"),
        "back_remo_pecho_apoyado_mancuernas": ("back_chest_supported_row", "chest_supported_row", {"implement": "dumbbells"}, "Remo con pecho apoyado", "chest_supported_row__dumbbells"),
        "back_remo_pecho_apoyado_polea": ("back_chest_supported_row", "chest_supported_row", {"implement": "cable"}, "Remo con pecho apoyado", "chest_supported_row__cable"),
        "back_remo_seal_barra_recta": ("back_seal_row", "seal_row", {"implement": "barbell"}, "Remo Seal", "seal_row__barbell"),
        "back_remo_seal_mancuernas": ("back_seal_row", "seal_row", {"implement": "dumbbells"}, "Remo Seal", "seal_row__dumbbells"),

        # Standard lateral/rear-delt and sissy-squat rows share one movement
        # identity; the setup/load position is exposed only after the broad
        # implement choice.  Super-ROM and named asymmetric methods remain
        # separate because they change the movement pattern itself.
        "deltoides_elevaciones_laterales": ("shoulder_lateral_raise", "lateral_raise", {"implement": "barbell", "posture": "standing", "laterality": "bilateral"}, "Elevación lateral", "lateral_raise__standing__barbell__bilateral"),
        "deltoides_elevaciones_laterales_acostado_banco_plano": ("shoulder_lateral_raise", "lateral_raise", {"implement": "dumbbells", "posture": "lying_flat", "laterality": "bilateral"}, "Elevación lateral", "lateral_raise__lying_flat__dumbbells__bilateral"),
        "deltoides_elevaciones_laterales_inclinadas": ("shoulder_lateral_raise", "lateral_raise", {"implement": "dumbbells", "posture": "incline_supported", "laterality": "bilateral"}, "Elevación lateral", "lateral_raise__incline_supported__dumbbells__bilateral"),
        "deltoides_elevaciones_laterales_recostado": ("shoulder_lateral_raise", "lateral_raise", {"implement": "dumbbells", "posture": "side_lying", "laterality": "bilateral"}, "Elevación lateral", "lateral_raise__side_lying__dumbbells__bilateral"),
        "deltoides_elevaciones_posteriores_de_pie": ("shoulder_rear_delt_raise", "rear_delt_raise", {"setup": "standing"}, "Elevaciones posteriores", "rear_delt_raise__standing"),
        "deltoides_elevaciones_posteriores_sentado_banco_plano": ("shoulder_rear_delt_raise", "rear_delt_raise", {"setup": "seated_bench"}, "Elevaciones posteriores", "rear_delt_raise__seated_bench"),
        "deltoides_elevaciones_posteriores_pecho_apoyado": ("shoulder_rear_delt_raise", "rear_delt_raise", {"setup": "chest_supported"}, "Elevaciones posteriores", "rear_delt_raise__chest_supported"),
        "deltoides_elevaciones_posteriores_acostado_banco_plano": ("shoulder_rear_delt_raise", "rear_delt_raise", {"setup": "lying_flat"}, "Elevaciones posteriores", "rear_delt_raise__lying_flat"),
        "deltoides_elevaciones_posteriores_arana": ("shoulder_rear_delt_raise", "rear_delt_raise", {"setup": "spider"}, "Elevaciones posteriores", "rear_delt_raise__spider"),
        "quads_sentadilla_sissy": ("lower_sissy_squat", "sissy_squat", {"implement": "bodyweight", "load_position": "bodyweight"}, "Sentadilla sissy", "sissy_squat__bodyweight"),
        "quads_sentadilla_sissy_maquina_hack": ("lower_sissy_squat", "sissy_squat", {"implement": "machine", "load_position": "hack"}, "Sentadilla sissy", "sissy_squat__hack_machine"),
        "quads_sentadilla_sissy_maquina_smith": ("lower_sissy_squat", "sissy_squat", {"implement": "smith_machine", "load_position": "smith"}, "Sentadilla sissy", "sissy_squat__smith_machine"),
        "quads_sentadilla_sissy_barra_recta": ("lower_sissy_squat", "sissy_squat", {"implement": "barbell", "load_position": "barbell_back"}, "Sentadilla sissy", "sissy_squat__barbell_back"),
        "quads_sentadilla_sissy_frontal_barra_recta": ("lower_sissy_squat", "sissy_squat", {"implement": "barbell", "load_position": "front"}, "Sentadilla sissy", "sissy_squat__barbell_front"),
        "quads_sentadilla_sissy_zercher_barra_recta": ("lower_sissy_squat", "sissy_squat", {"implement": "barbell", "load_position": "zercher"}, "Sentadilla sissy", "sissy_squat__barbell_zercher"),
    }

    def ensure_family(family_id: str, canonical: str, description: str, taxonomy: list[str]) -> None:
        families.setdefault(family_id, {"id": family_id, "canonicalName": canonical, "description": description, "taxonomy": taxonomy, "definitions": [], "evidence": evidence(f"editorial:family:{family_id}")})

    def ensure_definition(family_id: str, definition_id: str, canonical: str, kind: str, description: str, axes: list[str], searches: list[str]) -> dict[str, Any]:
        key = definition_id
        if key not in definitions:
            definitions[key] = {"id": definition_id, "familyId": family_id, "kind": kind, "canonicalName": canonical, "description": description, "searchTerms": sorted(set(searches)), "optionAxes": axes, "configurations": [], "defaultConfigurationId": "", "evidence": evidence(f"editorial:definition:{definition_id}")}
            families[family_id]["definitions"].append(definitions[key])
        return definitions[key]

    # Hard-coded pilot families and axes.
    pilot_specs = {
        "hinge_good_morning": ("Buenos días", "Familia de bisagra de cadera con postura y posición de carga materializadas.", ["lower", "posterior_chain", "hip_hinge"]),
        "hinge_rdl": ("Peso muerto rumano", "Familia de bisagra de cadera con postura de apoyo y posición de carga enumeradas; variantes excéntricas o asimétricas permanecen separadas.", ["lower", "posterior_chain", "romanian_deadlift"]),
        "hinge_deadlift": ("Peso muerto", "Familia de bisagra de cadera con implemento y postura materializados; déficits, rack pulls y métodos nombrados permanecen separados.", ["lower", "posterior_chain", "deadlift"]),
        "lower_knee_flexion": ("Curl femoral", "Familia de flexión de rodilla con implemento, estación y lateralidad enumerados; no se crean combinaciones no auditadas.", ["lower", "knee_flexion", "leg_curl"]),
        "lower_knee_hip_extension": ("Glute-Ham Raise", "Familia GHD con carga externa opcional materializada; la variante lastrada no genera una tarjeta independiente.", ["lower", "knee_hip_extension", "glute_ham_raise"]),
        "lower_knee_dominant_belt_squat": ("Sentadilla Belt Squat", "Familia de sentadilla con carga al cinturón; la lateralidad bilateral o unilateral es explícita.", ["lower", "knee_dominant", "belt_squat"]),
        "lower_knee_dominant_pendulum": ("Sentadilla péndulo", "Familia de sentadilla guiada en péndulo; la lateralidad bilateral o unilateral es explícita.", ["lower", "knee_dominant", "pendulum_squat"]),
        "hip_abduction": ("Abducción de cadera", "Familia de abducción con estación, implemento y lateralidad compatibles.", ["lower", "hip_abduction"]),
        "hip_adduction": ("Aducción de cadera", "Familia de aducción con estación, implemento y lateralidad compatibles.", ["lower", "hip_adduction"]),
        "chest_fly": ("Aperturas de pecho", "Familia de aducción horizontal; las aperturas inversas permanecen como especialidad distinta.", ["upper", "horizontal_abduction"]),
        "unilateral_knee_dominant_bulgarian": ("Sentadilla búlgara", "Familia unilateral con implementos y posiciones de carga explícitas.", ["lower", "unilateral", "knee_dominant"]),
        "elbow_flexion_biceps_curl": ("Curl de bíceps", "Familia de flexión de codo con setups explícitos; especialidades excéntricas o de suspensión quedan separadas.", ["upper", "elbow_flexion"]),
        "shoulder_lateral_raise": ("Elevación lateral", "Familia de abducción del hombro con postura, implemento y lateralidad explícitos.", ["upper", "shoulder_abduction"]),
        "chest_press": ("Press de banca", "Familia de empuje horizontal en banco; las configuraciones válidas cambian únicamente el implemento documentado.", ["upper", "horizontal_push", "bench_press"]),
        "chest_floor_press": ("Floor press", "Familia de empuje horizontal con los brazos limitados por el suelo; se conserva el implemento como elección explícita.", ["upper", "horizontal_push", "floor_press"]),
        "upper_horizontal_push": ("Empuje horizontal", "Familia de empuje horizontal; las flexiones de brazos usan un padre propio con el ángulo de apoyo materializado.", ["upper", "horizontal_push"]),
        "triceps_jm_press": ("JM press", "Familia de extensión de codo JM; las configuraciones válidas cambian el implemento sin crear combinaciones implícitas.", ["upper", "elbow_extension", "jm_press"]),
        "triceps_california_press": ("Press California", "Familia de extensión de codo California; la barra o mancuerna se elige como configuración materializada.", ["upper", "elbow_extension", "california_press"]),
        "triceps_tate_press": ("Tate press", "Familia de extensión de codo Tate; las configuraciones válidas cambian la resistencia documentada.", ["upper", "elbow_extension", "tate_press"]),
        "shoulder_arnold_press": ("Press Arnold", "Familia de empuje vertical con rotación del hombro; cada implemento queda materializado con su perfil propio.", ["upper", "vertical_push", "arnold_press"]),
        "shoulder_z_press": ("Press Z", "Familia de empuje vertical sentado en el suelo; cada implemento se conserva como configuración explícita.", ["upper", "vertical_push", "z_press"]),
        "triceps_katana_extension": ("Extensión Katana", "Familia de extensión de codo en posición Katana; implemento y lateralidad solo aparecen cuando cambian la configuración real.", ["upper", "elbow_extension", "katana"]),
        "upper_elbow_extension_crossbody": ("Extensión de tríceps cruzada", "Familia de extensión de codo cruzada con implemento y lateralidad materializados.", ["upper", "elbow_extension", "crossbody"]),
        "upper_elbow_extension_overhead": ("Extensión de tríceps overhead", "Familia de extensión de codo por encima de la cabeza con implementos explícitamente auditados.", ["upper", "elbow_extension", "overhead"]),
        "upper_vertical_pull_lat_pulldown": ("Jalón al pecho", "Familia de tirón vertical con los tres implementos documentados; agarres no materializados quedan fuera de los chips.", ["upper", "vertical_pull", "lat_pulldown"]),
        "upper_vertical_pull_pullover": ("Pullover", "Familia de tirón vertical con el implemento como decisión técnica; las configuraciones de mancuerna, polea y máquina se enumeran de forma explícita.", ["upper", "vertical_pull", "pullover"]),
        "upper_elbow_extension_kickback": ("Patada de tríceps", "Familia de extensión de codo con implemento y lateralidad materializados; no se generan combinaciones no auditadas.", ["upper", "elbow_extension", "triceps_kickback"]),
        "lower_hip_extension_hip_thrust": ("Hip Thrust", "Familia de extensión de cadera con implemento y lateralidad explícitos; la máquina y las mancuernas no se confunden con la barra.", ["lower", "hip_extension", "hip_thrust"]),
        "lower_knee_extension": ("Extensión de cuádriceps", "Familia de extensión de rodilla con implemento y lateralidad explícitos; la máquina bilateral y unilateral se mantienen dentro del mismo padre.", ["lower", "knee_extension", "quadriceps_extension"]),
        "upper_vertical_push_seated_press": ("Press de hombros sentado", "Familia de empuje vertical sentado con el implemento como decisión técnica; barra y máquina quedan materializadas.", ["upper", "vertical_push", "seated_shoulder_press"]),
        "lower_wrist_flexion": ("Curl de muñeca sentado", "Familia de flexión de muñeca con implemento explícito; barra y mancuernas conservan la misma identidad mecánica.", ["lower", "wrist_flexion"]),
        "back_chest_supported_row": ("Remo con pecho apoyado", "Familia de tirón horizontal con el tronco apoyado; la elección visible es el implemento materializado.", ["upper", "horizontal_pull", "chest_supported_row"]),
        "back_seal_row": ("Remo Seal", "Familia de tirón horizontal con apoyo de pecho tipo Seal; la elección visible es el implemento materializado.", ["upper", "horizontal_pull", "seal_row"]),
        "shoulder_rear_delt_raise": ("Elevaciones posteriores", "Familia de abducción horizontal de hombro con diferentes apoyos y posiciones; no se mezcla con el reverse pec fly guiado o en polea.", ["upper", "horizontal_abduction", "rear_delt_raise"]),
        "lower_sissy_squat": ("Sentadilla sissy", "Familia de dominante de rodilla en longitud; implemento y posición de carga son elecciones explícitas y no combinaciones libres.", ["lower", "knee_dominant_lengthened", "sissy_squat"]),
        "lower_plantar_flexion": ("Elevación de talones", "Familia de flexión plantar con estaciones explícitas; la estación sentada conserva su perfil de énfasis en sóleo.", ["lower", "plantar_flexion", "calf_raise"]),
    }
    for family_id, (name, description, taxonomy) in pilot_specs.items():
        ensure_family(family_id, name, description, taxonomy)

    for source_id, (family_id, definition_id, options, canonical, config_id) in pilot_rows.items():
        row = rows[source_id]
        kind = "SPECIALTY" if definition_id in {"copenhagen_plank", "copenhagen_plank_isometric", "bulgarian_zercher", "biceps_curl_zottman", "biceps_curl_drag", "biceps_curl_waiter", "biceps_curl_trx", "lateral_raise_super_rom"} else "PARENT"
        options = ordered_options(definition_id, options)
        axes = list(options)
        if definition_id in definitions:
            definition = definitions[definition_id]
            if options:
                definition["optionAxes"] = list(dict.fromkeys([*definition["optionAxes"], *options]))
        else:
            definition = ensure_definition(
                family_id,
                definition_id,
                canonical,
                kind,
                make_description(row, canonical=canonical, kind=kind),
                axes,
                search_terms_for_record(row, canonical),
            )
        definition["searchTerms"] = sorted(set(definition["searchTerms"] + search_terms_for_record(row, canonical)))
        equipment_override = {"band": "band", "cable": "cable", "machine": "machine", "dumbbells": "dumbbells", "barbell": "barbell", "hex_bar": "hex_bar", "sliders": "sliders", "plate": "plate", "trx": "trx"}.get(options.get("implement") or options.get("station"))
        config = make_config(row, family_id=family_id, definition_id=definition_id, kind=kind, config_id=config_id, selected_options=options, canonical=canonical, display=(" · ".join(options.values()) if options else canonical), equipment_override=equipment_override)
        # For a config originating from another record, align all identity rich metadata now.
        config["profile"]["richMetadata"]["identity"].update({"familyId": family_id, "definitionId": definition_id, "canonicalName": canonical, "kind": kind, "searchTerms": definition["searchTerms"]})
        config["profile"]["richMetadata"]["display"].update({"displayName": canonical, "displaySummary": config["displaySummary"], "selectedOptions": options})
        definition["configurations"].append(config)
        definition["defaultConfigurationId"] = definition["defaultConfigurationId"] or config_id
        decisions[source_id] = {
            "decision": "SPECIALTY" if kind == "SPECIALTY" else "CONFIGURATION",
            "rationale": curation_rationale(
                source_id,
                f"Mapeo explícito a {definition_id}/{config_id}; conserva la identidad técnica y no crea combinaciones implícitas.",
            ),
        }

    # Second-pass audit overlays.  These are deliberately enumerated instead
    # of generated as a Cartesian product: each row below corresponds to a
    # concrete implement/position combination present in the legacy technical
    # aspects or in a system-template prescription.  This closes the only
    # remaining source of cross-surface contradictions (for example, a
    # template saying "hip thrust en máquina" while the metadata still said
    # barra) without turning every possible option into a fake configuration.
    equipment_labels = {
        "band": "Banda",
        "barbell": "Barra",
        "cable": "Polea",
        "dumbbells": "Mancuerna",
        "machine": "Máquina",
    }

    def append_audited_overlay(
        source_id: str,
        family_id: str,
        definition_id: str,
        options: dict[str, str],
        canonical: str,
        config_id: str,
        equipment_id: str,
    ) -> None:
        row = dict(rows[source_id])
        synthetic_id = f"editorial:{config_id}"
        row["id"] = synthetic_id
        row["name"] = canonical
        row["equipment"] = equipment_labels.get(equipment_id, row.get("equipment", ""))
        options = ordered_options(definition_id, options)
        definition = definitions[definition_id]
        kind = definition["kind"]
        config = make_config(
            row,
            family_id=family_id,
            definition_id=definition_id,
            kind=kind,
            config_id=config_id,
            selected_options=options,
            canonical=canonical,
            display=" · ".join(options.values()),
            equipment_override=equipment_id,
            laterality="UNILATERAL" if options.get("laterality") == "unilateral" else None,
        )
        config["evidence"] = evidence(
            f"legacy:exercise_database.json#{source_id}",
            f"editorial:configuration:{config_id}",
            "editorial:catalog-v2-full-2026-08-02",
            rationale="Configuración explícita aprobada en la segunda auditoría; no se deriva por combinación runtime.",
        )
        config["profile"]["richMetadata"]["identity"].update({
            "familyId": family_id,
            "definitionId": definition_id,
            "canonicalName": canonical,
            "kind": kind,
            "searchTerms": definition["searchTerms"],
        })
        config["profile"]["richMetadata"]["display"].update({
            "displayName": canonical,
            "displaySummary": config["displaySummary"],
            "selectedOptions": options,
        })
        definition["configurations"].append(config)
        decisions[synthetic_id] = {
            "decision": "CONFIGURATION",
            "rationale": "Variante explícita materializada durante la segunda auditoría para mantener implemento y metadata sincronizados.",
        }

    for overlay in (
        ("biceps_curl_de_pie", "elbow_flexion_biceps_curl", "biceps_curl", {"setup": "standing", "implement": "dumbbells"}, "Curl de bíceps", "biceps_curl__standing__dumbbells", "dumbbells"),
        ("biceps_curl_predicador", "elbow_flexion_biceps_curl", "biceps_curl", {"setup": "preacher", "implement": "dumbbells"}, "Curl de bíceps", "biceps_curl__preacher__dumbbells", "dumbbells"),
        ("biceps_curl_predicador", "elbow_flexion_biceps_curl", "biceps_curl", {"setup": "preacher", "implement": "machine"}, "Curl de bíceps", "biceps_curl__preacher__machine", "machine"),
        ("biceps_curl_inclinado", "elbow_flexion_biceps_curl", "biceps_curl", {"setup": "inclinado", "implement": "dumbbells"}, "Curl de bíceps", "biceps_curl__inclinado__dumbbells", "dumbbells"),
        ("hams_peso_muerto_rumano_sumo", "hinge_rdl", "romanian_deadlift", {"implement": "dumbbells", "stance": "sumo"}, "Peso muerto rumano", "romanian_deadlift__sumo__dumbbells", "dumbbells"),
        ("triceps_overhead", "upper_elbow_extension_overhead", "overhead_triceps_extension", {"implement": "dumbbells"}, "Extensión de tríceps overhead", "overhead_triceps__dumbbells", "dumbbells"),
        ("triceps_overhead", "upper_elbow_extension_overhead", "overhead_triceps_extension", {"implement": "cable"}, "Extensión de tríceps overhead", "overhead_triceps__cable", "cable"),
        ("back_pullover", "upper_vertical_pull_pullover", "back_pullover", {"implement": "cable"}, "Pullover", "back_pullover__cable", "cable"),
        ("back_pullover", "upper_vertical_pull_pullover", "back_pullover", {"implement": "machine"}, "Pullover", "back_pullover__machine", "machine"),
        ("triceps_patada", "upper_elbow_extension_kickback", "triceps_patada", {"implement": "cable", "laterality": "bilateral"}, "Patada de tríceps", "triceps_patada__cable__bilateral", "cable"),
        ("triceps_patada", "upper_elbow_extension_kickback", "triceps_patada", {"implement": "dumbbells", "laterality": "unilateral"}, "Patada de tríceps", "triceps_patada__dumbbells__unilateral", "dumbbells"),
        ("glutes_hip_thrust", "lower_hip_extension_hip_thrust", "glutes_hip_thrust", {"implement": "dumbbells", "laterality": "bilateral"}, "Hip Thrust", "glutes_hip_thrust__dumbbells__bilateral", "dumbbells"),
        ("glutes_hip_thrust", "lower_hip_extension_hip_thrust", "glutes_hip_thrust", {"implement": "dumbbells", "laterality": "unilateral"}, "Hip Thrust", "glutes_hip_thrust__dumbbells__unilateral", "dumbbells"),
        ("glutes_hip_thrust", "lower_hip_extension_hip_thrust", "glutes_hip_thrust", {"implement": "machine", "laterality": "bilateral"}, "Hip Thrust", "glutes_hip_thrust__machine__bilateral", "machine"),
        ("deltoides_elevaciones_laterales_de_pie", "shoulder_lateral_raise", "lateral_raise", {"implement": "machine", "posture": "standing", "laterality": "bilateral"}, "Elevación lateral", "lateral_raise__standing__machine__bilateral", "machine"),
        ("deltoides_elevaciones_laterales_sentado", "shoulder_lateral_raise", "lateral_raise", {"implement": "dumbbells", "posture": "seated", "laterality": "bilateral"}, "Elevación lateral", "lateral_raise__seated__dumbbells__bilateral", "dumbbells"),
        ("quads_extension_cuadriceps", "lower_knee_extension", "quads_extension_cuadriceps", {"laterality": "unilateral"}, "Extensión de cuádriceps", "quads_extension_cuadriceps__machine__unilateral", "machine"),
        ("calves_talones_de_pie", "lower_plantar_flexion", "calf_raise", {"station": "standing", "implement": "dumbbells", "laterality": "bilateral"}, "Elevación de talones", "calf_raise__standing__dumbbells__bilateral", "dumbbells"),
        ("calves_talones_de_pie", "lower_plantar_flexion", "calf_raise", {"station": "standing", "implement": "machine", "laterality": "unilateral"}, "Elevación de talones", "calf_raise__standing__machine__unilateral", "machine"),
        ("deltoides_press_hombros_sentado", "upper_vertical_push_seated_press", "deltoides_press_hombros_sentado", {"implement": "machine"}, "Press de hombros sentado", "deltoides_press_hombros_sentado__machine", "machine"),
        ("forearms_curl_muneca_sentado", "lower_wrist_flexion", "forearms_curl_muneca_sentado", {"implement": "dumbbells"}, "Curl de muñeca sentado", "forearms_curl_muneca_sentado__dumbbells", "dumbbells"),
    ):
        append_audited_overlay(*overlay)

    # Reverse fly has two real, non-interchangeable stations. Keep one parent
    # card and materialize the machine and cable configurations explicitly.
    # The cable row is an editorial configuration overlay: it reuses the
    # audited anatomy but has its own implement, cues and resistance profile.
    reverse_definition = definitions["reverse_pec_fly"]
    # The parent card must describe the movement family, never one of its
    # implementations.  Machine and cable are separate, explicit
    # configurations below; the parent description stays equipment-neutral.
    reverse_definition["description"] = (
        "Abducción horizontal de hombros con énfasis en deltoides posteriores. "
        "Las configuraciones disponibles son pec deck inverso en máquina y "
        "aperturas inversas en polea."
    )
    reverse_source = dict(rows["deltoides_aperturas_inversas_maquina_pec_deck"])
    reverse_source.update({
        "id": "editorial_reverse_pec_fly_cable",
        "name": "Aperturas inversas en polea",
        "equipment": "Polea",
        "alias": "aperturas inversas en polea, reverse cable fly",
        "description": "Abducción horizontal de deltoides posterior en polea con tensión continua.",
    })
    reverse_config = make_config(
        reverse_source,
        family_id="chest_fly",
        definition_id="reverse_pec_fly",
        kind="PARENT",
        config_id="reverse_pec_fly__standing__cable",
        selected_options=ordered_options("reverse_pec_fly", {"station": "standing", "implement": "cable"}),
        canonical="Aperturas inversas",
        display="De pie · polea",
        equipment_override="cable",
    )
    reverse_config["evidence"] = evidence(
        "editorial:configuration:reverse_pec_fly__standing__cable",
        "editorial:catalog-v2-full-2026-08-02",
        rationale="Configuración explícita de polea; no se infiere desde la configuración de máquina.",
    )
    reverse_definition["searchTerms"] = sorted(set(reverse_definition["searchTerms"] + SEARCH_TERM_OVERRIDES["deltoides_aperturas_inversas_maquina_pec_deck"]))
    reverse_config["profile"]["richMetadata"]["identity"].update({
        "familyId": "chest_fly",
        "definitionId": "reverse_pec_fly",
        "canonicalName": "Aperturas inversas",
        "kind": "PARENT",
        "searchTerms": reverse_definition["searchTerms"],
    })
    reverse_config["profile"]["richMetadata"]["display"].update({
        "displayName": "Aperturas inversas",
        "displaySummary": reverse_config["displaySummary"],
        "selectedOptions": reverse_config["selectedOptions"],
    })
    reverse_definition["configurations"].append(reverse_config)
    decisions[reverse_source["id"]] = {
        "decision": "CONFIGURATION",
        "rationale": "Configuración editorial de polea para el mismo ejercicio padre; mantiene la estación y el implemento explícitos.",
    }

    # The legacy asset has a single cable row for unilateral hip isolation;
    # the band configuration is an explicitly audited equipment overlay, not a
    # runtime inference or a free combination.
    for source_id, family_id, definition_id, options, config_id, canonical, equipment in (
        ("glutes_abduccion_cadera", "hip_abduction", "hip_abduction", {"station": "standing", "implement": "band", "laterality": "unilateral"}, "hip_abduction__standing__band__unilateral", "Abducción de cadera", "band"),
        ("adductors_aduccion_cadera", "hip_adduction", "hip_adduction", {"station": "standing", "implement": "band", "laterality": "unilateral"}, "hip_adduction__standing__band__unilateral", "Aducción de cadera", "band"),
        ("deltoides_elevaciones_laterales_de_pie", "shoulder_lateral_raise", "lateral_raise", {"posture": "standing", "implement": "cable", "laterality": "unilateral"}, "lateral_raise__standing__cable__unilateral", "Elevación lateral", "cable"),
    ):
        row = rows[source_id]
        definition = definitions[definition_id]
        options = ordered_options(definition_id, options)
        config = make_config(row, family_id=family_id, definition_id=definition_id, kind="PARENT", config_id=config_id, selected_options=options, canonical=canonical, display=" · ".join(options.values()), equipment_override=equipment, laterality="UNILATERAL")
        config["profile"]["richMetadata"]["identity"].update({"familyId": family_id, "definitionId": definition_id, "canonicalName": canonical, "kind": "PARENT", "searchTerms": definition["searchTerms"]})
        config["profile"]["richMetadata"]["display"].update({"displayName": canonical, "displaySummary": config["displaySummary"], "selectedOptions": options})
        definition["configurations"].append(config)
        decisions.setdefault(source_id, {"decision": "CONFIGURATION", "rationale": f"La variante de implemento se materializa como {config_id}; no se crea una combinación implícita."})

    # Expand the biceps parent with the remaining setup variants from the source.
    for source_id in ("biceps_curl_sentado_banco_plano", "biceps_curl_inclinado", "biceps_curl_declinado", "biceps_curl_arana", "biceps_curl_concentrado", "biceps_curl_superman", "biceps_curl_crucifijo"):
        row = rows[source_id]
        setup = slug(row["name"].split("(")[-1].rstrip(")").strip()) if "(" in row["name"] else slug(row["name"])
        config_id = f"biceps_curl__{setup}__{EQUIPMENT_IDS[row['equipment']]}"
        definition = definitions["biceps_curl"]
        options = ordered_options("biceps_curl", {"setup": setup, "implement": EQUIPMENT_IDS[row["equipment"]]})
        config = make_config(row, family_id="elbow_flexion_biceps_curl", definition_id="biceps_curl", kind="PARENT", config_id=config_id, selected_options=options, canonical="Curl de bíceps", display=" · ".join(options.values()), equipment_override=EQUIPMENT_IDS[row["equipment"]])
        config["profile"]["richMetadata"]["identity"].update({"familyId": "elbow_flexion_biceps_curl", "definitionId": "biceps_curl", "canonicalName": "Curl de bíceps", "kind": "PARENT", "searchTerms": definition["searchTerms"]})
        config["profile"]["richMetadata"]["display"].update({"displayName": "Curl de bíceps", "displaySummary": config["displaySummary"], "selectedOptions": config["selectedOptions"]})
        definition["searchTerms"] = sorted(set(definition["searchTerms"] + search_terms_for_record(row, "Curl de bíceps")))
        definition["configurations"].append(config)
        decisions[source_id] = {"decision": "CONFIGURATION", "rationale": f"Setup de curl de bíceps representado en el eje setup/implement; no se crea una card independiente."}

    # Every remaining row is kept as a specialty unless a safe, explicit pilot
    # mapping exists.  This is conservative: uncertain biomechanics never get
    # fused merely to reduce cards.
    for source_id, row in rows.items():
        if source_id in decisions:
            continue
        movement_id = MOVEMENT_IDS[row["movementPattern"]]
        muscles = profile_muscles(row)
        family_id, family_name, taxonomy = pattern_family(movement_id, BODY_IDS[row["bodyPart"]], muscles["primary"][0])
        ensure_family(family_id, family_name, f"Familia controlada por patrón {movement_id}; cada especialidad conserva su perfil resuelto.", taxonomy)
        definition_id = slug(source_id)
        canonical = canonical_name(row)
        definition = ensure_definition(family_id, definition_id, canonical, "SPECIALTY", make_description(row, canonical=canonical, kind="SPECIALTY"), [], [canonical.lower(), *[x.strip().lower() for x in (row.get("alias") or "").split(",") if x.strip()]])
        config_id = f"{definition_id}__default"
        # A specialty has no chip axis: its canonical name is the technical
        # identity, while the compact summary only needs the effective
        # implement.  Never emit the old placeholder phrase
        # "configuración documentada"; it made cards look unresolved.
        config = make_config(row, family_id=family_id, definition_id=definition_id, kind="SPECIALTY", config_id=config_id, selected_options={}, canonical=canonical, display=str(row["equipment"]), equipment_override=EQUIPMENT_IDS[row["equipment"]])
        config["profile"]["richMetadata"]["identity"].update({"familyId": family_id, "definitionId": definition_id, "canonicalName": canonical, "kind": "SPECIALTY", "searchTerms": definition["searchTerms"]})
        config["profile"]["richMetadata"]["display"].update({"displayName": canonical, "displaySummary": config["displaySummary"], "selectedOptions": {}})
        definition["configurations"].append(config)
        definition["defaultConfigurationId"] = config_id
        decisions[source_id] = {
            "decision": "SPECIALTY",
            "rationale": curation_rationale(
                source_id,
                "Se conserva como especialidad porque no existe una matriz de compatibilidad editorial aprobada que permita agruparla sin inventar opciones.",
            ),
        }

    # Remove duplicate configurations created by multiple legacy rows mapping to
    # the same explicit pilot identity; the losing rows remain in the decision log.
    for definition in definitions.values():
        if definition["id"] in AXIS_ORDER_OVERRIDES:
            definition["optionAxes"] = AXIS_ORDER_OVERRIDES[definition["id"]]
        unique: dict[str, dict[str, Any]] = {}
        for config in definition["configurations"]:
            config["selectedOptions"] = ordered_options(definition["id"], config["selectedOptions"])
            unique.setdefault(config["id"], config)
        definition["configurations"] = list(unique.values())
        if not definition["defaultConfigurationId"]:
            definition["defaultConfigurationId"] = definition["configurations"][0]["id"]
        if definition["optionAxes"]:
            expected = set(definition["optionAxes"])
            for config in definition["configurations"]:
                if set(config["selectedOptions"]) != expected:
                    raise ValueError(
                        f"Axis mismatch for {definition['id']}: {config['id']} "
                        f"{sorted(config['selectedOptions'])} != {sorted(expected)}",
                    )
        for config in definition["configurations"]:
            # Every definition-level search term is copied into rich identity.
            config["profile"]["richMetadata"]["identity"]["searchTerms"] = definition["searchTerms"]
            config["profile"]["richMetadata"]["display"]["displayName"] = definition["canonicalName"]
            config["profile"]["richMetadata"]["display"]["displaySummary"] = config["displaySummary"]
            config["profile"]["richMetadata"]["display"]["selectedOptions"] = config["selectedOptions"]
            config["evidence"] = evidence(*config["evidence"]["evidenceRefs"], rationale="Aprobado por decisión explícita de catálogo v2; la configuración está materializada y no depende de inferencia runtime.")
            config["profile"]["richMetadata"]["evidenceConfidence"] = "MEDIUM"
        definition["evidence"] = evidence(f"editorial:definition:{definition['id']}", rationale="Revisada; las configuraciones válidas están enumeradas de forma explícita.")
    families_list = []
    for family in sorted(families.values(), key=lambda x: x["id"]):
        family["definitions"] = sorted(family["definitions"], key=lambda x: x["id"])
        family["evidence"] = evidence(f"editorial:family:{family['id']}", rationale="Aprobada para runtime tras validación de identidad, metadata y configuraciones.")
        families_list.append(family)
    source = {"schemaVersion": 2, "catalogRevision": REVISION, "ontologyRevision": ONTOLOGY, "families": families_list}
    inventory = json.loads(INVENTORY.read_text(encoding="utf-8"))
    for candidate in inventory["candidates"]:
        decision = decisions[candidate["sourceId"]]
        candidate["decision"] = decision["decision"]
        candidate["decisionRationale"] = decision["rationale"]
        candidate["decisionEvidenceRefs"] = [f"legacy:exercise_database.json#{candidate['sourceId']}", "editorial:catalog-v2-full-2026-08-02"]
    inventory["purpose"] = "Complete reviewed decision register; runtime source is generated from explicit mappings and resolved metadata."
    inventory["reviewRevision"] = REVISION
    return source, inventory


def canonical_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def main() -> int:
    source, inventory = build()
    OUTPUT.write_bytes(canonical_bytes(source))
    INVENTORY.write_bytes(canonical_bytes(inventory))
    digest = hashlib.sha256(canonical_bytes(source)).hexdigest()
    definitions = sum(len(f["definitions"]) for f in source["families"])
    configurations = sum(len(d["configurations"]) for f in source["families"] for d in f["definitions"])
    print(f"families={len(source['families'])} definitions={definitions} configurations={configurations}")
    print(f"candidates={len(inventory['candidates'])} decisions={sum(c['decision'] != 'UNREVIEWED' for c in inventory['candidates'])}")
    print(f"catalogRevision={REVISION}")
    print(f"canonicalSha256={digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
