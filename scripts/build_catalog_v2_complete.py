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

REVISION = "v2-approved-2026-08-02"
ONTOLOGY = "wikilab-v2-2026-08-02"

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
}

EQUIPMENT_IDS = {
    "Banda": "band",
    "Banda Elástica": "band",
    "Barra": "barbell",
    "Barra EZ": "ez_bar",
    "Barra Hexagonal": "hex_bar",
    "Barra T": "t_bar",
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
    if existing and "es un ejercicio dentro de un patrón" not in existing.lower() and len(existing) >= 40:
        return existing
    involved = record.get("involvedMuscles") or []
    override = MUSCLE_ROLE_OVERRIDES.get(record["id"])
    primary = override["primary"] if override else [item["muscle"] for item in involved if item.get("role") == "primary"]
    secondary = override.get("secondary", []) if override else [item["muscle"] for item in involved if item.get("role") == "secondary"]
    primary_text = ", ".join(primary) or "la musculatura declarada"
    secondary_text = ", ".join(secondary)
    secondary_clause = f" También exige coordinar {secondary_text} como apoyo." if secondary_text else ""
    chain = record.get("chain", "")
    chain_clause = f" La cadena {chain.lower()} mantiene la demanda estable durante el recorrido." if chain else ""
    kind_label = "ejercicio padre" if kind == "PARENT" else "especialidad"
    return (
        f"{canonical}: {kind_label} centrado en {primary_text}, con patrón "
        f"{record['movementPattern'].lower()} y {record['equipment'].lower()} como implemento."
        f" La ejecución aprobada prioriza control del recorrido y una trayectoria coherente con esa mecánica."
        f"{secondary_clause}{chain_clause}"
    )


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
        "hams_buenos_dias": ("hinge_good_morning", "good_morning", {"posture": "standing", "load_position": "barbell_back"}, "Buenos días", "good_morning__standing__barbell_back"),
        "back_buenos_dias": ("hinge_good_morning", "good_morning", {"posture": "standing", "load_position": "cable_front"}, "Buenos días", "good_morning__standing__cable"),
        "hams_buenos_dias_sentado": ("hinge_good_morning", "good_morning", {"posture": "seated", "load_position": "barbell_back"}, "Buenos días", "good_morning__seated__barbell"),
        "hams_buenos_dias_zercher": ("hinge_good_morning", "good_morning", {"posture": "standing", "load_position": "zercher"}, "Buenos días", "good_morning__standing__zercher"),
        "back_buenos_dias_zercher_barra": ("hinge_good_morning", "good_morning", {"posture": "standing", "load_position": "zercher"}, "Buenos días", "good_morning__standing__zercher"),
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
        "deltoides_aperturas_inversas_maquina_pec_deck": ("chest_fly", "reverse_pec_fly", {}, "Aperturas inversas", "reverse_pec_fly__pec_deck__machine"),
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
        "deltoides_elevaciones_laterales_de_pie": ("shoulder_lateral_raise", "lateral_raise", {"posture": "standing", "implement": "dumbbells", "laterality": "bilateral"}, "Elevación lateral", "lateral_raise__standing__dumbbells__bilateral"),
        "deltoides_elevaciones_laterales_sentado": ("shoulder_lateral_raise", "lateral_raise", {"posture": "seated", "implement": "machine", "laterality": "bilateral"}, "Elevación lateral", "lateral_raise__seated__machine__bilateral"),
        # Romanian deadlift is a parent; the standard and sumo stances are
        # explicit, compatible configurations. Deficit, Zercher, B-stance and
        # other technically distinct variants remain independent specialties.
        "hams_peso_muerto_rumano": ("hinge_rdl", "romanian_deadlift", {"stance": "bilateral"}, "Peso muerto rumano", "romanian_deadlift__bilateral__barbell"),
        "hams_peso_muerto_rumano_sumo": ("hinge_rdl", "romanian_deadlift", {"stance": "sumo"}, "Peso muerto rumano", "romanian_deadlift__sumo__barbell"),
        # The isometric Copenhagen plank is not interchangeable with the
        # dynamic eccentric version, so it has its own specialty definition.
        "adductors_plancha_copenhagen_peso_corporal": ("hip_adduction", "copenhagen_plank_isometric", {}, "Plancha Copenhagen isométrica", "copenhagen_plank_isometric__bodyweight"),
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
        "hip_abduction": ("Abducción de cadera", "Familia de abducción con estación, implemento y lateralidad compatibles.", ["lower", "hip_abduction"]),
        "hip_adduction": ("Aducción de cadera", "Familia de aducción con estación, implemento y lateralidad compatibles.", ["lower", "hip_adduction"]),
        "chest_fly": ("Aperturas de pecho", "Familia de aducción horizontal; las aperturas inversas permanecen como especialidad distinta.", ["upper", "horizontal_abduction"]),
        "unilateral_knee_dominant_bulgarian": ("Sentadilla búlgara", "Familia unilateral con implementos y posiciones de carga explícitas.", ["lower", "unilateral", "knee_dominant"]),
        "elbow_flexion_biceps_curl": ("Curl de bíceps", "Familia de flexión de codo con setups explícitos; especialidades excéntricas o de suspensión quedan separadas.", ["upper", "elbow_flexion"]),
        "shoulder_lateral_raise": ("Elevación lateral", "Familia de abducción del hombro con postura, implemento y lateralidad explícitos.", ["upper", "shoulder_abduction"]),
    }
    for family_id, (name, description, taxonomy) in pilot_specs.items():
        ensure_family(family_id, name, description, taxonomy)

    for source_id, (family_id, definition_id, options, canonical, config_id) in pilot_rows.items():
        row = rows[source_id]
        kind = "SPECIALTY" if definition_id in {"copenhagen_plank", "copenhagen_plank_isometric", "reverse_pec_fly", "bulgarian_zercher", "biceps_curl_zottman", "biceps_curl_drag", "biceps_curl_waiter", "biceps_curl_trx", "lateral_raise_super_rom"} else "PARENT"
        axes = sorted(options)
        if definition_id in definitions:
            definition = definitions[definition_id]
            if options:
                definition["optionAxes"] = sorted(set(definition["optionAxes"]) | set(options))
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
        equipment_override = {"band": "band", "cable": "cable", "machine": "machine", "dumbbells": "dumbbells", "barbell": "barbell", "plate": "plate", "trx": "trx"}.get(options.get("implement") or options.get("station"))
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
        config = make_config(row, family_id="elbow_flexion_biceps_curl", definition_id="biceps_curl", kind="PARENT", config_id=config_id, selected_options={"setup": setup, "implement": EQUIPMENT_IDS[row["equipment"]]}, canonical="Curl de bíceps", display=f"{row['name'].split('(')[-1].rstrip(')')} · {row['equipment']}", equipment_override=EQUIPMENT_IDS[row["equipment"]])
        config["profile"]["richMetadata"]["identity"].update({"familyId": "elbow_flexion_biceps_curl", "definitionId": "biceps_curl", "canonicalName": "Curl de bíceps", "kind": "PARENT", "searchTerms": definition["searchTerms"]})
        config["profile"]["richMetadata"]["display"].update({"displayName": "Curl de bíceps", "displaySummary": config["displaySummary"], "selectedOptions": config["selectedOptions"]})
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
        unique: dict[str, dict[str, Any]] = {}
        for config in definition["configurations"]:
            unique.setdefault(config["id"], config)
        definition["configurations"] = list(unique.values())
        if not definition["defaultConfigurationId"]:
            definition["defaultConfigurationId"] = definition["configurations"][0]["id"]
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
