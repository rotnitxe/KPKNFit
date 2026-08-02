#!/usr/bin/env python3
"""Build the explicit v2 pilot source from reviewed candidate records.

This is intentionally a whitelist, not a name-based merger. Every source
record, option axis, configuration, specialty, and profile override is listed
below. Adding a candidate requires adding a row to this file and therefore
leaves a reviewable decision in the diff.
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "catalog" / "exercises" / "v2" / "curation" / "evidence" / "legacy" / "exercise_database.json"
OUTPUT = ROOT / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"

EVIDENCE = {
    "reviewStatus": "DRAFT",
    "confidence": "MEDIUM",
    "evidenceRefs": ["candidate:exercise_database.json", "editorial:pilot-2026-08-02"],
}

MUSCLE_IDS = {
    "Pectorales": "pectoralis",
    "Deltoides": "deltoid",
    "Tríceps": "triceps",
    "Bíceps": "biceps",
    "Antebrazo": "forearm",
    "Erectores Espinales": "erector_spinae",
    "Isquiosurales": "hamstrings",
    "Glúteos": "gluteus_maximus",
    "Cuádriceps": "quadriceps",
    "Aductores": "adductors",
    "Tensor Fascia Lata": "tensor_fasciae_latae",
    "Trapecio": "trapezius",
    "Romboides": "rhomboids",
    "Core": "abdominals",
}

MOVEMENT_IDS = {
    "Bisagra": "hip_hinge",
    "Bisagra Cadera": "hip_hinge",
    "Abducción Cadera": "hip_abduction",
    "Aducción Cadera": "hip_adduction",
    "Aducción Isométrica": "hip_adduction_isometric",
    "Aducción Dinámica": "hip_adduction_dynamic",
    "Empuje Horizontal": "horizontal_push",
    "Abducción Horizontal": "horizontal_abduction",
    "Dominante de Rodilla Unilateral": "unilateral_knee_dominant",
    "Dominante de Rodilla Unilateral Asimétrico": "unilateral_knee_dominant_asymmetric",
    "Flexión Codo": "elbow_flexion",
    "Abducción Hombro": "shoulder_abduction",
    "Abducción Completa": "shoulder_abduction_full_rom",
}

EQUIPMENT_IDS = {
    "Barra": "barbell",
    "Mancuerna": "dumbbells",
    "Máquina": "machine",
    "Polea": "cable",
    "Peso Corporal": "bodyweight",
    "Disco": "plate",
    "Banda": "band",
    "Banda Elástica": "band",
    "Kettlebell": "kettlebell",
    "Barra EZ": "ez_bar",
    "TRX": "trx",
    "Máquina Smith": "smith_machine",
}

CHAIN_IDS = {"anterior": "ANTERIOR", "posterior": "POSTERIOR", "full": "FULL"}
BODY_IDS = {"upper": "UPPER", "lower": "LOWER", "core": "CORE", "full": "FULL"}


def slug(value: str) -> str:
    value = value.lower().replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u").replace("ñ", "n")
    return re.sub(r"[^a-z0-9]+", "_", value).strip("_")


def explicit_config(
    config_id: str,
    source_id: str,
    selected_options: dict[str, str],
    display_summary: str = "",
    *,
    laterality: str = "BILATERAL",
    axial_load_factor: float = 0.0,
    technical_difficulty: float = 3.0,
    resistance_profile: str = "controlled_external_resistance",
    load_mode: str | None = None,
    performance_profile_id: str | None = None,
    replacement_group: str | None = None,
    replacement_priority: int | None = None,
    setup_cues: list[str],
    execution_cues: list[str],
    common_mistakes: list[str],
    search_terms: list[str] | None = None,
) -> dict[str, Any]:
    display_summary = display_summary or config_id.replace("__", " · ")
    return {
        "id": config_id,
        "sourceId": source_id,
        "selectedOptions": selected_options,
        "displaySummary": display_summary,
        "laterality": laterality,
        "axialLoadFactor": axial_load_factor,
        "technicalDifficulty": technical_difficulty,
        "resistanceProfile": resistance_profile,
        "loadMode": load_mode,
        "performanceProfileId": performance_profile_id,
        "replacementGroup": replacement_group,
        "replacementPriority": replacement_priority,
        "setupCues": setup_cues,
        "executionCues": execution_cues,
        "commonMistakes": common_mistakes,
        "searchTerms": search_terms or [],
    }


def profile(record: dict[str, Any], config: dict[str, Any]) -> dict[str, Any]:
    muscles: dict[str, list[str]] = {"primary": [], "secondary": [], "stabilizer": []}
    for involvement in record.get("involvedMuscles", []):
        muscle_id = MUSCLE_IDS.get(involvement.get("muscle", ""))
        if not muscle_id:
            raise ValueError(f"Unmapped controlled muscle: {involvement.get('muscle')} in {record['id']}")
        role = involvement.get("role", "secondary")
        bucket = "stabilizer" if role == "stabilizer" else "primary" if role == "primary" else "secondary"
        if muscle_id not in muscles[bucket]:
            muscles[bucket].append(muscle_id)
    if not muscles["primary"]:
        raise ValueError(f"No primary muscle in {record['id']}")

    equipment_id = EQUIPMENT_IDS.get(record.get("equipment", ""))
    if not equipment_id:
        raise ValueError(f"Unmapped controlled equipment: {record.get('equipment')} in {record['id']}")
    movement_pattern_id = MOVEMENT_IDS.get(record.get("movementPattern", ""))
    if not movement_pattern_id:
        raise ValueError(f"Unmapped controlled movement: {record.get('movementPattern')} in {record['id']}")
    body_region = BODY_IDS.get(record.get("bodyPart", ""))
    kinetic_chain = CHAIN_IDS.get(record.get("chain", ""))
    if not body_region or not kinetic_chain:
        raise ValueError(f"Unmapped region/chain in {record['id']}")

    default_load_mode = {
        "barbell": "free_external_load",
        "dumbbells": "free_external_load",
        "ez_bar": "free_external_load",
        "kettlebell": "free_external_load",
        "plate": "free_external_load",
        "cable": "continuous_cable",
        "machine": "guided_external_load",
        "smith_machine": "guided_external_load",
        "band": "variable_band_resistance",
        "bodyweight": "bodyweight",
        "trx": "suspension",
    }
    performance_profile_id = config["performanceProfileId"] or config["id"]
    return {
        "movementPatternId": movement_pattern_id,
        "bodyRegion": body_region,
        "kineticChain": kinetic_chain,
        "laterality": config["laterality"],
        "equipmentId": equipment_id,
        "loadMode": config["loadMode"] or default_load_mode[equipment_id],
        "primaryMuscles": muscles["primary"],
        "secondaryMuscles": muscles["secondary"],
        "stabilizerMuscles": muscles["stabilizer"],
        "efc": float(record["efc"]),
        "cnc": float(record["cnc"]),
        "ssc": float(record["ssc"]),
        "ttc": float(record["ttc"]),
        "axialLoadFactor": config["axialLoadFactor"],
        "technicalDifficulty": config["technicalDifficulty"],
        "resistanceProfile": config["resistanceProfile"],
        "setupCues": config["setupCues"],
        "executionCues": config["executionCues"],
        "commonMistakes": config["commonMistakes"],
        "performanceProfileId": performance_profile_id,
        "replacementGroup": config["replacementGroup"] or record.get("replacementGroup"),
        "replacementPriority": config["replacementPriority"] or record.get("replacementPriority"),
        "automationEligible": False,
    }


def build() -> dict[str, Any]:
    records = {row["id"]: row for row in json.loads(ASSET.read_text(encoding="utf-8"))}

    families: list[dict[str, Any]] = []

    def add_family(family_id: str, canonical: str, description: str, definitions: list[dict[str, Any]], taxonomy: list[str]) -> None:
        families.append({
            "id": family_id,
            "canonicalName": canonical,
            "description": description,
            "taxonomy": taxonomy,
            "definitions": definitions,
            "evidence": dict(EVIDENCE),
        })

    def definition(definition_id: str, family_id: str, canonical: str, description: str, axes: list[str], configs: list[dict[str, Any]], *, kind: str = "PARENT", searches: list[str] | None = None) -> dict[str, Any]:
        output_configs = []
        effective_axes = [
            axis for axis in axes
            if len({config["selectedOptions"].get(axis) for config in configs}) > 1
        ]
        for config in configs:
            source = records[config.pop("sourceId")]
            output_configs.append({
                "id": config["id"],
                "selectedOptions": {axis: config["selectedOptions"][axis] for axis in effective_axes},
                "displaySummary": config["displaySummary"],
                "profile": profile(source, config),
                "evidence": dict(EVIDENCE),
            })
        return {
            "id": definition_id,
            "familyId": family_id,
            "kind": kind,
            "canonicalName": canonical,
            "description": description,
            "searchTerms": searches or [canonical.lower()],
            "optionAxes": effective_axes,
            "configurations": output_configs,
            "defaultConfigurationId": output_configs[0]["id"],
            "evidence": dict(EVIDENCE),
        }

    add_family(
        "hinge_good_morning",
        "Buenos días",
        "Familia de bisagra de cadera en la que la postura y la posición de carga cambian la demanda sin crear tarjetas duplicadas para el mismo gesto.",
        [definition(
            "good_morning", "hinge_good_morning", "Buenos días",
            "Bisagra de cadera con inclinación del tronco controlada y extensión posterior. El usuario elige solo posiciones de carga materializadas y comparables.",
            ["posture", "load_position"],
            [
                explicit_config("good_morning__standing__barbell_back", "hams_buenos_dias", {"posture": "standing", "load_position": "barbell_back"}, "De pie · barra sobre la espalda", axial_load_factor=1.0, technical_difficulty=6.0, resistance_profile="lengthened_hip_extensor", performance_profile_id="good_morning__barbell_back", replacement_group="hip_hinge_accessory", replacement_priority=3, setup_cues=["Coloca la barra estable sobre la espalda y bloquea el tronco antes de iniciar."], execution_cues=["Lleva la cadera atrás manteniendo la columna estable y vuelve con extensión de cadera."], common_mistakes=["Convertir la inclinación en flexión lumbar o perder la tensión de la barra."]),
                explicit_config("good_morning__standing__zercher", "back_buenos_dias_zercher_barra", {"posture": "standing", "load_position": "zercher"}, "De pie · carga Zercher", axial_load_factor=0.7, technical_difficulty=7.0, resistance_profile="lengthened_hip_extensor", performance_profile_id="good_morning__zercher", replacement_group="hip_hinge_accessory", replacement_priority=4, setup_cues=["Asienta la barra en el pliegue de los codos y fija el agarre antes de inclinarte."], execution_cues=["Mantén la carga próxima al tronco mientras desplazas la cadera atrás."], common_mistakes=["Dejar que la barra se aleje y colapse el torso."]),
                explicit_config("good_morning__seated__barbell", "hams_buenos_dias_sentado", {"posture": "seated", "load_position": "barbell_back"}, "Sentado · barra sobre la espalda", axial_load_factor=0.6, technical_difficulty=6.0, resistance_profile="lengthened_hip_extensor", performance_profile_id="good_morning__seated", replacement_group="hip_hinge_accessory", replacement_priority=5, setup_cues=["Apoya ambos pies y coloca la barra sin desplazar la pelvis del banco."], execution_cues=["Inclina el tronco desde la cadera dentro del rango que mantenga el control lumbar."], common_mistakes=["Buscar profundidad redondeando la espalda o despegar la pelvis del apoyo."]),
                explicit_config("good_morning__standing__cable", "back_buenos_dias", {"posture": "standing", "load_position": "cable_front"}, "De pie · polea", axial_load_factor=0.3, technical_difficulty=5.0, resistance_profile="continuous_cable_lengthened", performance_profile_id="good_morning__cable", replacement_group="hip_hinge_accessory", replacement_priority=2, setup_cues=["Ajusta la polea y toma la distancia que mantenga una tensión continua sin tirar de los brazos."], execution_cues=["Desplaza la cadera atrás contra la línea de la polea y extiende sin hiperextender la espalda."], common_mistakes=["Usar los brazos para mover la polea o perder la tensión al final."]),
            ],
            searches=["buenos días", "buenos dias", "good morning", "buenos días isquiosurales", "buenos días zercher"],
        )],
        ["lower", "posterior_chain", "hip_hinge"],
    )

    add_family(
        "hip_abduction",
        "Abducción de cadera",
        "Familia de abducción de cadera con configuraciones separadas para máquina, polea y banda, respetando la postura y la lateralidad realmente disponibles.",
        [definition(
            "hip_abduction", "hip_abduction", "Abducción de cadera",
            "Abducción de cadera en la que estación, postura y lado de trabajo determinan la configuración exacta y el perfil de estabilidad.",
            ["station", "implement", "laterality"],
            [
                explicit_config("hip_abduction__seated__machine__bilateral", "glutes_abduccion_cadera_sentado_maquina", {"station": "seated", "implement": "machine", "laterality": "bilateral"}, "Sentado · máquina · bilateral", laterality="BILATERAL", technical_difficulty=2.0, resistance_profile="machine_midrange", performance_profile_id="hip_abduction__machine", replacement_group="hip_abduction_isolation", replacement_priority=1, setup_cues=["Ajusta el respaldo y el inicio del recorrido antes de separar las piernas."], execution_cues=["Abduce sin levantar la pelvis y regresa con control al punto inicial."], common_mistakes=["Balancear el tronco o convertir el movimiento en una apertura impulsiva."]),
                explicit_config("hip_abduction__standing__cable__unilateral", "glutes_abduccion_cadera", {"station": "standing", "implement": "cable", "laterality": "unilateral"}, "De pie · polea · unilateral", laterality="UNILATERAL", technical_difficulty=4.0, resistance_profile="continuous_cable", performance_profile_id="hip_abduction__cable_unilateral", replacement_group="hip_abduction_isolation", replacement_priority=2, setup_cues=["Sujétate a un apoyo y alinea la polea con el tobillo de trabajo."], execution_cues=["Separa la pierna lateralmente sin inclinar el tronco y vuelve sin perder tensión."], common_mistakes=["Girar la pelvis o usar una inclinación del tronco para ganar recorrido."]),
                explicit_config("hip_abduction__standing__band__unilateral", "glutes_abduccion_cadera", {"station": "standing", "implement": "band", "laterality": "unilateral"}, "De pie · banda · unilateral", laterality="UNILATERAL", technical_difficulty=3.0, resistance_profile="variable_band", performance_profile_id="hip_abduction__band_unilateral", replacement_group="hip_abduction_isolation", replacement_priority=3, setup_cues=["Coloca la banda sin que se deslice y fija el pie de apoyo."], execution_cues=["Abduce contra la resistencia manteniendo la pelvis neutra."], common_mistakes=["Mover la pierna con impulso o dejar que la rodilla rote hacia dentro."]),
            ],
            searches=["abducción de cadera", "abduccion cadera", "abductores"],
        )],
        ["lower", "hip_abduction", "gluteus"],
    )

    add_family(
        "hip_adduction",
        "Aducción de cadera",
        "Familia de aducción de cadera con estación, implemento y lateralidad explícitos. Las planchas Copenhagen se mantienen como especialidad independiente.",
        [
            definition(
                "hip_adduction", "hip_adduction", "Aducción de cadera",
                "Aducción de cadera para aproximar el miembro a la línea media con perfiles distintos para máquina y polea o banda unilateral.",
                ["station", "implement", "laterality"],
                [
                    explicit_config("hip_adduction__seated__machine__bilateral", "adductors_aduccion_cadera_sentado_maquina", {"station": "seated", "implement": "machine", "laterality": "bilateral"}, "Sentado · máquina · bilateral", technical_difficulty=2.0, resistance_profile="machine_midrange", performance_profile_id="hip_adduction__machine", replacement_group="hip_adduction_isolation", replacement_priority=1, setup_cues=["Ajusta el rango inicial de la máquina sin forzar la apertura."], execution_cues=["Cierra las piernas con control y evita despegar la pelvis del asiento."], common_mistakes=["Golpear los topes o impulsar el cierre con el tronco."]),
                    explicit_config("hip_adduction__standing__cable__unilateral", "adductors_aduccion_cadera", {"station": "standing", "implement": "cable", "laterality": "unilateral"}, "De pie · polea · unilateral", laterality="UNILATERAL", technical_difficulty=4.0, resistance_profile="continuous_cable", performance_profile_id="hip_adduction__cable_unilateral", replacement_group="hip_adduction_isolation", replacement_priority=2, setup_cues=["Fija el apoyo y alinea la polea con el tobillo de trabajo."], execution_cues=["Lleva la pierna hacia la línea media sin rotar la pelvis."], common_mistakes=["Cruzar la pierna por impulso o inclinar el tronco para compensar."]),
                    explicit_config("hip_adduction__standing__band__unilateral", "adductors_aduccion_cadera", {"station": "standing", "implement": "band", "laterality": "unilateral"}, "De pie · banda · unilateral", laterality="UNILATERAL", technical_difficulty=3.0, resistance_profile="variable_band", performance_profile_id="hip_adduction__band_unilateral", replacement_group="hip_adduction_isolation", replacement_priority=3, setup_cues=["Ancla la banda y mantén el pie de apoyo estable."], execution_cues=["Aduce la pierna sin perder el control de la pelvis y regresa lentamente."], common_mistakes=["Rotar la pelvis o dejar que la banda arrastre la pierna de vuelta."]),
                ],
                searches=["aducción de cadera", "aduccion cadera", "aductores"],
            ),
            definition(
                "copenhagen_plank", "hip_adduction", "Plancha Copenhagen",
                "Especialidad de aducción isométrica y control lateral del tronco. Su apoyo, palanca y tolerancia local no son equivalentes a una máquina aductora.",
                ["variant"],
                [
                    explicit_config("copenhagen_plank__dynamic", "adductors_plancha_copenhagen_dinamica", {"variant": "dynamic"}, "Dinámica", technical_difficulty=6.0, resistance_profile="isometric_bodyweight", load_mode="bodyweight", performance_profile_id="copenhagen_plank__dynamic", replacement_group="adductor_control", replacement_priority=5, setup_cues=["Apoya el lateral del tobillo o la rodilla superior y alinea hombros, pelvis y pies."], execution_cues=["Mantén el tronco en bloque mientras acercas y separas la pelvis con control."], common_mistakes=["Dejar caer la pelvis o colapsar el hombro de apoyo."]),
                ],
                kind="SPECIALTY",
                searches=["copenhagen", "plancha copenhagen"],
            ),
        ],
        ["lower", "hip_adduction", "adductors"],
    )

    add_family(
        "chest_fly",
        "Aperturas de pecho",
        "Familia de aducción horizontal del hombro con estación, soporte y ángulo materializados. Las aperturas inversas quedan fuera porque cambian la acción y el perfil anatómico.",
        [
            definition(
                "chest_fly", "chest_fly", "Aperturas de pecho",
                "Aducción horizontal para el pectoral con configuraciones de banco, máquina, banda o suelo que conservan la intención de acercar los brazos al frente.",
                ["station", "support_angle", "implement"],
                [
                    explicit_config("chest_fly__bench__flat__dumbbells", "tren_superior_aperturas_planas_mancuernas", {"station": "bench", "support_angle": "flat", "implement": "dumbbells"}, "Banco plano · mancuernas", technical_difficulty=4.0, resistance_profile="gravity_arc", performance_profile_id="chest_fly__bench_flat", replacement_group="horizontal_adduction", replacement_priority=2, setup_cues=["Apoya la espalda y coloca las mancuernas sobre el pecho con codos suavemente flexionados."], execution_cues=["Abre hasta un estiramiento tolerable y acerca las manos sin convertirlo en un press."], common_mistakes=["Bajar demasiado con el hombro anterior o cerrar golpeando las mancuernas."]),
                    explicit_config("chest_fly__bench__incline__dumbbells", "tren_superior_aperturas_inclinadas_mancuernas", {"station": "bench", "support_angle": "incline", "implement": "dumbbells"}, "Banco inclinado · mancuernas", technical_difficulty=4.0, resistance_profile="gravity_arc_clavicular", performance_profile_id="chest_fly__bench_incline", replacement_group="horizontal_adduction", replacement_priority=3, setup_cues=["Ajusta el banco inclinado y mantén las escápulas apoyadas."], execution_cues=["Acerca los brazos en el plano del banco sin perder la posición del hombro."], common_mistakes=["Convertir el movimiento en un press o perder el apoyo escapular."]),
                    explicit_config("chest_fly__bench__decline__dumbbells", "tren_superior_aperturas_declinadas_mancuernas", {"station": "bench", "support_angle": "decline", "implement": "dumbbells"}, "Banco declinado · mancuernas", technical_difficulty=4.0, resistance_profile="gravity_arc_sternal", performance_profile_id="chest_fly__bench_decline", replacement_group="horizontal_adduction", replacement_priority=4, setup_cues=["Asegura el apoyo del cuerpo en el banco declinado antes de descolgar las mancuernas."], execution_cues=["Controla la apertura y vuelve a juntar los brazos sin perder el arco del hombro."], common_mistakes=["Rebotar en la zona baja o perder la estabilidad del banco."]),
                    explicit_config("chest_fly__floor__dumbbells", "tren_superior_aperturas_suelo_mancuernas", {"station": "floor", "support_angle": "flat", "implement": "dumbbells"}, "Suelo · mancuernas", technical_difficulty=3.0, resistance_profile="gravity_arc_limited_rom", performance_profile_id="chest_fly__floor", replacement_group="horizontal_adduction", replacement_priority=3, setup_cues=["Túmbate con pies apoyados y coloca las mancuernas sobre el pecho."], execution_cues=["Deja que los brazos desciendan hasta que los tríceps contacten el suelo y vuelve con control."], common_mistakes=["Forzar el hombro más allá del límite que marca el suelo."]),
                    explicit_config("chest_fly__pec_deck__machine__machine", "tren_superior_aperturas_pec_deck", {"station": "pec_deck", "support_angle": "seated", "implement": "machine"}, "Pec deck · máquina", technical_difficulty=3.0, resistance_profile="machine_constant", performance_profile_id="chest_fly__pec_deck", replacement_group="horizontal_adduction", replacement_priority=1, setup_cues=["Ajusta el asiento para que los brazos queden a la altura del pecho."], execution_cues=["Cierra los brazos manteniendo el torso apoyado y regresa lentamente."], common_mistakes=["Despegar la espalda o usar un golpe de hombros para cerrar."]),
                    explicit_config("chest_fly__standing__band__band", "tren_superior_aperturas_banda", {"station": "standing", "support_angle": "standing", "implement": "band"}, "De pie · banda", technical_difficulty=3.0, resistance_profile="variable_band", performance_profile_id="chest_fly__band", replacement_group="horizontal_adduction", replacement_priority=4, setup_cues=["Ancla la banda a la altura adecuada y adopta una base estable."], execution_cues=["Acerca las manos siguiendo un arco estable y vuelve sin perder tensión."], common_mistakes=["Dejar que la banda tire de los hombros hacia delante o usar impulso."]),
                ],
                searches=["aperturas", "aperturas pecho", "fly", "pec deck"],
            ),
            definition(
                "reverse_pec_fly", "chest_fly", "Aperturas inversas",
                "Especialidad de abducción horizontal para deltoides posterior y musculatura escapular. No es una configuración de las aperturas de pecho porque cambia la acción y los músculos principales.",
                ["station", "implement"],
                [explicit_config("reverse_pec_fly__pec_deck__machine", "deltoides_aperturas_inversas_maquina_pec_deck", {"station": "pec_deck_reverse", "implement": "machine"}, "Pec deck inverso · máquina", technical_difficulty=4.0, resistance_profile="machine_constant", performance_profile_id="reverse_pec_fly__pec_deck", replacement_group="horizontal_abduction", replacement_priority=2, setup_cues=["Ajusta el asiento para que las manos queden alineadas con los hombros."], execution_cues=["Separa los brazos en el plano horizontal sin encoger los hombros."], common_mistakes=["Elevar los hombros hacia las orejas o convertirlo en un remo."])],
                kind="SPECIALTY",
                searches=["aperturas inversas", "reverse pec fly", "deltoides posterior"],
            ),
        ],
        ["upper", "horizontal_adduction", "pectoral"],
    )

    add_family(
        "unilateral_knee_dominant_bulgarian",
        "Sentadilla búlgara",
        "Familia de sentadilla unilateral con el pie trasero elevado. Máquina, mancuernas y barra son configuraciones explícitas; el nombre incorrecto Bulgaria en Máquina queda solo como búsqueda.",
        [
            definition(
                "bulgarian_split_squat", "unilateral_knee_dominant_bulgarian", "Sentadilla búlgara",
                "Sentadilla unilateral con apoyo posterior elevado. La configuración fija implemento y posición de carga sin mezclar variantes que cambian la secuencia técnica.",
                ["implement", "load_position"],
                [
                    explicit_config("bulgarian__dumbbells__sides", "quads_sentadilla_bulgara", {"implement": "dumbbells", "load_position": "sides"}, "Mancuernas · a los lados", laterality="UNILATERAL", axial_load_factor=0.4, technical_difficulty=5.0, resistance_profile="gravity_arc", performance_profile_id="bulgarian__dumbbells", replacement_group="unilateral_knee_dominant", replacement_priority=2, setup_cues=["Coloca el empeine trasero en un apoyo estable y encuentra la distancia que permita una bajada controlada."], execution_cues=["Desciende con la rodilla siguiendo el pie y sube empujando el suelo con la pierna delantera."], common_mistakes=["Impulsarse con la pierna trasera o perder la alineación de la rodilla."]),
                    explicit_config("bulgarian__machine__guided", "quads_sentadilla_bulgara_maquina", {"implement": "machine", "load_position": "guided"}, "Máquina · guiada", laterality="UNILATERAL", axial_load_factor=0.2, technical_difficulty=4.0, resistance_profile="guided_constant", performance_profile_id="bulgarian__machine", replacement_group="unilateral_knee_dominant", replacement_priority=1, setup_cues=["Ajusta la máquina para que el eje y el apoyo posterior no desplacen la pelvis."], execution_cues=["Baja y sube manteniendo el pie delantero completamente apoyado y el recorrido guiado."], common_mistakes=["Usar una profundidad que despegue el talón o girar la pelvis."]),
                    explicit_config("bulgarian__front_barbell__front", "quads_sentadilla_bulgara_frontal", {"implement": "barbell", "load_position": "front"}, "Barra frontal", laterality="UNILATERAL", axial_load_factor=0.8, technical_difficulty=7.0, resistance_profile="gravity_arc", performance_profile_id="bulgarian__front_barbell", replacement_group="unilateral_knee_dominant", replacement_priority=4, setup_cues=["Asegura la barra delante del tronco y el apoyo posterior antes de iniciar."], execution_cues=["Mantén el torso alto y carga la pierna delantera en todo el recorrido."], common_mistakes=["Dejar que la barra se aleje o descargar la repetición en la pierna trasera."]),
                ],
                searches=["sentadilla búlgara", "sentadilla bulgara", "bulgaria en máquina", "bulgaria maquina"],
            ),
            definition(
                "bulgarian_zercher", "unilateral_knee_dominant_bulgarian", "Sentadilla búlgara Zercher",
                "Especialidad con carga Zercher que modifica el brace y la posición del tronco. Se mantiene separada porque requiere cues y tolerancia técnica propios.",
                ["implement", "load_position"],
                [explicit_config("bulgarian_zercher__barbell__zercher", "quads_sentadilla_bulgara_zercher", {"implement": "barbell", "load_position": "zercher"}, "Barra · Zercher", laterality="UNILATERAL", axial_load_factor=0.7, technical_difficulty=7.0, resistance_profile="gravity_arc", performance_profile_id="bulgarian_zercher", replacement_group="unilateral_knee_dominant_specialty", replacement_priority=6, setup_cues=["Coloca la barra en el pliegue de los codos y fija el apoyo posterior antes de cargar la pierna delantera."], execution_cues=["Desciende con el tronco estable y sube sin dejar que la carga rote la pelvis."], common_mistakes=["Perder la barra del pliegue de los codos o usar la pierna trasera para despegar."])],
                kind="SPECIALTY",
                searches=["búlgara zercher", "bulgara zercher"],
            ),
        ],
        ["lower", "unilateral", "knee_dominant"],
    )

    add_family(
        "elbow_flexion_biceps_curl",
        "Curl de bíceps",
        "Familia de flexión de codo con setups de pie, predicador y bayesiano. Zottman, Drag, Waiter y TRX permanecen como especialidades porque cambian la secuencia o el agarre programable.",
        [
            definition(
                "biceps_curl", "elbow_flexion_biceps_curl", "Curl de bíceps",
                "Flexión de codo con el húmero controlado. La estación, el implemento y el agarre se eligen mediante configuraciones enumeradas, no mediante un producto cartesiano libre.",
                ["setup", "implement", "grip"],
                [
                    explicit_config("biceps_curl__standing__dumbbells__supinated", "biceps_curl_de_pie", {"setup": "standing", "implement": "dumbbells", "grip": "supinated"}, "De pie · mancuernas · supino", technical_difficulty=3.0, resistance_profile="gravity_arc", performance_profile_id="biceps_curl__standing_free", replacement_group="elbow_flexion_isolation", replacement_priority=2, setup_cues=["Coloca el torso estable y deja los brazos colgar sin adelantar los hombros."], execution_cues=["Flexiona los codos manteniendo el húmero quieto y desciende con control."], common_mistakes=["Balancear el tronco o adelantar el hombro para superar la zona difícil."]),
                    explicit_config("biceps_curl__preacher__ez_bar__supinated", "biceps_curl_predicador", {"setup": "preacher", "implement": "ez_bar", "grip": "supinated"}, "Predicador · barra EZ · supino", technical_difficulty=4.0, resistance_profile="lengthened_supported", performance_profile_id="biceps_curl__preacher", replacement_group="elbow_flexion_isolation", replacement_priority=4, setup_cues=["Ajusta el banco predicador para apoyar el brazo sin bloquear el codo."], execution_cues=["Extiende y flexiona el codo manteniendo el brazo apoyado en el pad."], common_mistakes=["Rebotar en la extensión o despegar el brazo del apoyo."]),
                    explicit_config("biceps_curl__bayesian__cable__supinated", "biceps_curl_bayesian", {"setup": "bayesian", "implement": "cable", "grip": "supinated"}, "Bayesiano · polea · supino", laterality="UNILATERAL", technical_difficulty=4.0, resistance_profile="lengthened_cable", performance_profile_id="biceps_curl__bayesian", replacement_group="elbow_flexion_isolation", replacement_priority=4, setup_cues=["Coloca el hombro ligeramente extendido y alinea la polea con la mano."], execution_cues=["Mantén el codo atrás mientras flexionas y extiendes con tensión continua."], common_mistakes=["Convertirlo en un balanceo del hombro o perder la posición del codo."]),
                ],
                searches=["curl de bíceps", "curl biceps", "curl bayesiano", "curl predicador", "curl de pie"],
            ),
            definition(
                "biceps_curl_zottman", "elbow_flexion_biceps_curl", "Curl Zottman",
                "Especialidad que combina una fase concéntrica supinada con una excéntrica pronada. La rotación de agarre cambia la secuencia y no es un chip genérico del curl.",
                ["implement"],
                [explicit_config("biceps_curl_zottman__dumbbells", "biceps_curl_zottman_mancuernas", {"implement": "dumbbells"}, technical_difficulty=5.0, resistance_profile="gravity_arc", performance_profile_id="biceps_curl_zottman", replacement_group="elbow_flexion_rotation", replacement_priority=6, setup_cues=["Inicia con mancuernas y palmas supinas, con el torso estable."], execution_cues=["Sube supinando y gira a pronación antes de bajar lentamente."], common_mistakes=["Girar la muñeca bajo carga o dejar caer la excéntrica sin control."])],
                kind="SPECIALTY",
                searches=["curl zottman", "zottman"],
            ),
            definition(
                "biceps_curl_drag", "elbow_flexion_biceps_curl", "Curl drag",
                "Especialidad de curl con el codo desplazándose hacia atrás y una trayectoria distinta al curl estándar. Se programa como ejercicio propio.",
                ["implement", "grip"],
                [explicit_config("biceps_curl_drag__barbell__supinated", "biceps_curl_drag", {"implement": "barbell", "grip": "supinated"}, technical_difficulty=5.0, resistance_profile="gravity_arc_shortened", performance_profile_id="biceps_curl_drag", replacement_group="elbow_flexion_specialty", replacement_priority=7, setup_cues=["Sujeta la barra con torso erguido y hombros colocados antes de iniciar."], execution_cues=["Arrastra los codos atrás mientras flexionas sin convertirlo en un remo."], common_mistakes=["Encoger los hombros o lanzar la barra con la espalda."])],
                kind="SPECIALTY",
                searches=["curl drag", "drag curl"],
            ),
            definition(
                "biceps_curl_waiter", "elbow_flexion_biceps_curl", "Curl Waiter",
                "Especialidad con un disco sostenido en copa. La carga compartida y el agarre cambian la ejecución respecto al curl de barra o mancuerna.",
                ["implement"],
                [explicit_config("biceps_curl_waiter__plate", "biceps_curl_disco", {"implement": "plate"}, technical_difficulty=4.0, resistance_profile="gravity_arc", performance_profile_id="biceps_curl_waiter", replacement_group="elbow_flexion_specialty", replacement_priority=6, setup_cues=["Sujeta el disco por los bordes con las palmas enfrentadas y el torso estable."], execution_cues=["Eleva el disco flexionando ambos codos sin perder la posición de las muñecas."], common_mistakes=["Doblar las muñecas o levantar el disco con los hombros."])],
                kind="SPECIALTY",
                searches=["waiter curl", "curl con disco", "curl copa"],
            ),
            definition(
                "biceps_curl_trx", "elbow_flexion_biceps_curl", "Curl de bíceps en TRX",
                "Especialidad en suspensión donde el ángulo corporal regula la carga y exige estabilización de tronco. No es equivalente a una polea o mancuerna.",
                ["grip"],
                [explicit_config("biceps_curl_trx__supinated", "biceps_curl_trx", {"grip": "supinated"}, technical_difficulty=5.0, resistance_profile="body_angle", load_mode="suspension", performance_profile_id="biceps_curl_trx", replacement_group="elbow_flexion_suspension", replacement_priority=8, setup_cues=["Ajusta la longitud del TRX y adopta una inclinación que puedas controlar."], execution_cues=["Flexiona los codos llevando las manos hacia la frente sin perder la línea del tronco."], common_mistakes=["Dejar caer la cadera o usar el cuello para buscar las asas."])],
                kind="SPECIALTY",
                searches=["curl trx", "curl en trx"],
            ),
        ],
        ["upper", "elbow_flexion", "biceps"],
    )

    add_family(
        "shoulder_lateral_raise",
        "Elevación lateral",
        "Familia de abducción del hombro con postura, implemento y lateralidad explícitos. Super ROM queda separado por su recorrido y secuencia continua por encima de la cabeza.",
        [
            definition(
                "lateral_raise", "shoulder_lateral_raise", "Elevación lateral",
                "Abducción del hombro para deltoides medio con configuraciones de pie o sentado y una selección explícita de implemento y lado.",
                ["posture", "implement", "laterality"],
                [
                    explicit_config("lateral_raise__standing__dumbbells__bilateral", "deltoides_elevaciones_laterales_de_pie", {"posture": "standing", "implement": "dumbbells", "laterality": "bilateral"}, technical_difficulty=3.0, resistance_profile="gravity_arc", performance_profile_id="lateral_raise__standing_free", replacement_group="shoulder_abduction_isolation", replacement_priority=1, setup_cues=["Adopta una base estable y deja los brazos a los lados con codos suavemente flexionados."], execution_cues=["Eleva los brazos en el plano escapular hasta el rango controlable y desciende lento."], common_mistakes=["Encoger los hombros o balancear el tronco para ganar altura."]),
                    explicit_config("lateral_raise__seated__machine__bilateral", "deltoides_elevaciones_laterales_sentado", {"posture": "seated", "implement": "machine", "laterality": "bilateral"}, technical_difficulty=2.0, resistance_profile="machine_constant", performance_profile_id="lateral_raise__seated_machine", replacement_group="shoulder_abduction_isolation", replacement_priority=2, setup_cues=["Ajusta el asiento y los apoyos para que el eje de la máquina coincida con el hombro."], execution_cues=["Separa los brazos sin despegar el tronco y regresa con control."], common_mistakes=["Despegar la espalda o impulsar los brazos desde el torso."]),
                    explicit_config("lateral_raise__standing__cable__unilateral", "deltoides_elevaciones_laterales_de_pie", {"posture": "standing", "implement": "cable", "laterality": "unilateral"}, laterality="UNILATERAL", technical_difficulty=4.0, resistance_profile="continuous_cable", performance_profile_id="lateral_raise__cable_unilateral", replacement_group="shoulder_abduction_isolation", replacement_priority=3, setup_cues=["Coloca la polea baja y cruza el cable por delante del cuerpo sin perder la base."], execution_cues=["Eleva el brazo lateralmente con tensión continua y baja sin soltar el hombro."], common_mistakes=["Girar el tronco o levantar la mano con una inclinación excesiva."]),
                ],
                searches=["elevaciones laterales", "elevacion lateral", "hombro lateral"],
            ),
            definition(
                "lateral_raise_super_rom", "shoulder_lateral_raise", "Elevación lateral Super ROM",
                "Especialidad de recorrido continuo por encima de la cabeza. El cambio de ROM y de intención mecánica impide tratarla como un chip del ejercicio lateral estándar.",
                ["implement", "laterality"],
                [
                    explicit_config("lateral_raise_super_rom__dumbbells__bilateral", "deltoides_elevaciones_laterales_super_rom_mancuernas", {"implement": "dumbbells", "laterality": "bilateral"}, technical_difficulty=6.0, resistance_profile="full_rom_gravity", performance_profile_id="lateral_raise_super_rom__dumbbells", replacement_group="shoulder_abduction_specialty", replacement_priority=7, setup_cues=["Usa una carga que permita controlar todo el arco sin perder el centrado del hombro."], execution_cues=["Continúa el arco por encima de la cabeza solo mientras mantengas control escapular y del tronco."], common_mistakes=["Forzar el final del recorrido o convertir la repetición en un encogimiento."]),
                    explicit_config("lateral_raise_super_rom__cable__unilateral", "deltoides_elevaciones_laterales_super_rom_polea_unilateral", {"implement": "cable", "laterality": "unilateral"}, laterality="UNILATERAL", technical_difficulty=6.0, resistance_profile="continuous_cable_full_rom", performance_profile_id="lateral_raise_super_rom__cable", replacement_group="shoulder_abduction_specialty", replacement_priority=8, setup_cues=["Ajusta la polea para que la tensión no se pierda al comienzo del arco."], execution_cues=["Sigue el arco completo con tensión continua y vuelve sin dejar caer el hombro."], common_mistakes=["Desplazar el tronco para completar el arco o perder la trayectoria del cable."]),
                ],
                kind="SPECIALTY",
                searches=["super rom", "elevaciones laterales super rom"],
            ),
        ],
        ["upper", "shoulder_abduction", "deltoid"],
    )

    return {
        "schemaVersion": 2,
        "catalogRevision": "v2-pilot-2026-08-02",
        "ontologyRevision": "wikilab-pilot-2026-08-02",
        "families": families,
    }


def main() -> int:
    source = build()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(source, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    definitions = sum(len(f["definitions"]) for f in source["families"])
    configurations = sum(len(d["configurations"]) for f in source["families"] for d in f["definitions"])
    print(f"wrote={OUTPUT}")
    print(f"families={len(source['families'])} definitions={definitions} configurations={configurations}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
