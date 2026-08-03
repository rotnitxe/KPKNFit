#!/usr/bin/env python3
"""Curaduría v4: ejercicios faltantes, involucramiento adaptativo por chips y
descripciones amigables para el usuario.

Pasos después del script:
    python scripts/merge_catalog_v2_families.py
    python scripts/compile_exercise_catalog_v2_cli.py --check
    python scripts/catalog_v2_gate.py --strict
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from curaduria_v3_transform import (  # noqa: E402
    FAMILIES,
    LOAD_PROFILES,
    fam_of,
    find_def,
    load,
    make_config,
    new_definition,
    note,
    save,
    sync_identity,
)

LOAD_PROFILES["h_bar"] = ("free_external_load", "gravity_arc")

REVISION = "v2-approved-2026-08-02-c"


# ---------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------
def set_muscles(cfg: dict, primary: list, secondary: list, stabilizers: list, notes: list) -> None:
    p = cfg["profile"]
    p["primaryMuscles"] = list(primary)
    p["secondaryMuscles"] = list(secondary)
    p["stabilizerMuscles"] = list(stabilizers)
    p["muscleNotes"] = copy.deepcopy(notes)
    rich = p["richMetadata"]
    rich["anatomy"]["primaryMuscles"] = list(primary)
    rich["anatomy"]["secondaryMuscles"] = list(secondary)
    rich["anatomy"]["stabilizerMuscles"] = list(stabilizers)
    if primary:
        rich["programming"]["objectives"] = [f"Desarrollar {primary[0]} en la configuración seleccionada."]
        rich["replacement"]["preservesIntent"] = [f"Conserva el patrón y el objetivo {primary[0]}."]


def rewrite_muscles(payload: dict, def_id: str, muscles_by_grip: dict, notes_by_grip: dict) -> None:
    definition = find_def(payload, def_id)
    for cfg in definition["configurations"]:
        grip = cfg["selectedOptions"].get("grip_width", "medium")
        set_muscles(cfg, *muscles_by_grip[grip], notes_by_grip[grip])


# ---------------------------------------------------------------------------
# P1 — Ejercicios faltantes
# ---------------------------------------------------------------------------
def p1_new_exercises() -> None:
    # --- Curl Martillo y Curl Invertido en la familia de bíceps -------------
    payload = load("elbow_flexion_biceps_curl.json")
    family = fam_of(payload)
    existing_ids = {d["id"] for d in family["definitions"]}
    template = next(
        c for d in family["definitions"] if d["id"] == "standing_biceps_curl"
        for c in d["configurations"] if c["id"].endswith("__dumbbells")
    )
    biceps_note = note(
        "biceps",
        "Principal: el bíceps flexiona el codo y concentra el trabajo del curl; por eso suma la serie completa (1.0).",
    )
    forearm_hammer = note(
        "forearm",
        "Secundario: el braquiorradial y el braquial ayudan con el agarre en martillo; por eso suman 0.5.",
    )

    hammer_configs = []
    for implement in ("h_bar", "dumbbells", "cable", "machine", "band"):
        hammer_configs.append(
            make_config(
                template,
                cfg_id=f"hammer_curl__{implement}",
                options={"implement": implement},
                display_summary=f"{implement} · bilateral",
                description=HAMMER_DESC[implement],
                primary=["biceps"], secondary=["forearm"], stabilizers=[],
                notes=[biceps_note, forearm_hammer],
                equipment_id=implement, laterality="BILATERAL", axial=0.6,
                perf_id=f"hammer_curl__{implement}",
                objectives=[f"Desarrollar biceps con el curl martillo en {implement}."],
                required_equipment=[implement], compatible_equipment=["h_bar", "dumbbells", "cable", "machine", "band"],
                preserves_intent=["Conserva el patrón de flexión de codo y el objetivo biceps."],
                target_regions=["biceps", "forearm"],
                setup_cue="Agarra el implemento con las palmas enfrentadas y los brazos a los costados.",
                exec_cue="Flexiona los codos llevando el peso hacia los hombros sin mover los brazos.",
                mistake="Balancear el torso o abrir los codos para levantar más peso.",
            )
        )
    if "hammer_curl" not in existing_ids:
        family["definitions"].append(
            new_definition(
                def_id="hammer_curl", family_id="elbow_flexion_biceps_curl",
                canonical_name="Curl Martillo", description=HAMMER_DEF_DESC,
                option_axes=["implement"],
                search_terms=["curl martillo", "hammer curl", "martillo", "curl agarre neutro"],
                configurations=hammer_configs, default_id="hammer_curl__dumbbells",
            )
        )

    forearm_reverse = note(
        "forearm",
        "Principal: el braquiorradial y el braquial concentran el trabajo con el agarre pronado; por eso suman la serie completa (1.0).",
    )
    biceps_reverse = note(
        "biceps",
        "Secundario: el bíceps asiste la flexión del codo en menor medida con las palmas hacia abajo; por eso suma 0.5.",
    )
    reverse_configs = []
    for implement in ("h_bar", "dumbbells", "cable", "machine", "band"):
        reverse_configs.append(
            make_config(
                template,
                cfg_id=f"reverse_curl__{implement}",
                options={"implement": implement},
                display_summary=f"{implement} · bilateral",
                description=REVERSE_DESC[implement],
                primary=["forearm"], secondary=["biceps"], stabilizers=[],
                notes=[forearm_reverse, biceps_reverse],
                equipment_id=implement, laterality="BILATERAL", axial=0.6,
                perf_id=f"reverse_curl__{implement}",
                objectives=[f"Desarrollar forearm con el curl invertido en {implement}."],
                required_equipment=[implement], compatible_equipment=["h_bar", "dumbbells", "cable", "machine", "band"],
                preserves_intent=["Conserva el patrón de flexión de codo y el objetivo forearm."],
                target_regions=["forearm", "biceps"],
                setup_cue="Agarra el implemento con las palmas hacia abajo y los brazos extendidos.",
                exec_cue="Flexiona los codos llevando el peso hacia el pecho manteniendo las palmas abajo.",
                mistake="Usar muñecas en flexión o impulsar el peso con el tronco.",
            )
        )
    if "reverse_curl" not in existing_ids:
        family["definitions"].append(
            new_definition(
                def_id="reverse_curl", family_id="elbow_flexion_biceps_curl",
                canonical_name="Curl Invertido", description=REVERSE_DEF_DESC,
                option_axes=["implement"],
                search_terms=["curl invertido", "reverse curl", "curl prono", "antebrazo"],
                configurations=reverse_configs, default_id="reverse_curl__dumbbells",
            )
        )
    sync_identity(payload, REVISION)
    save("elbow_flexion_biceps_curl.json", payload)

    # --- Nueva familia: rotaciones de antebrazo (supinaciones/pronaciones) --
    wrist_payload = load("lower_wrist_flexion.json")
    wrist_family = fam_of(wrist_payload)
    wrist_template = next(
        c for d in wrist_family["definitions"] if d["id"] == "forearms_curl_muneca_sentado"
        for c in d["configurations"] if c["id"].endswith("__dumbbells")
    )
    sup_note = note(
        "forearm",
        "Principal: los músculos supinadores giran el antebrazo contra la resistencia; por eso suman la serie completa (1.0).",
    )
    biceps_sup = note(
        "biceps",
        "Secundario: el bíceps asiste la rotación externa del antebrazo; por eso suma 0.5.",
    )
    sup_configs = []
    for implement, laterality in (("dumbbells", "BILATERAL"), ("cable", "UNILATERAL")):
        sup_configs.append(
            make_config(
                wrist_template,
                cfg_id=f"supination__{implement}",
                options={"implement": implement},
                display_summary=f"{implement} · {laterality.lower()}",
                description=SUP_DESC[implement],
                primary=["forearm"], secondary=["biceps"], stabilizers=[],
                notes=[sup_note, biceps_sup],
                equipment_id=implement, laterality=laterality, axial=0.0,
                perf_id=f"supination__{implement}",
                objectives=[f"Desarrollar forearm con supinaciones en {implement}."],
                required_equipment=[implement], compatible_equipment=["dumbbells", "cable"],
                preserves_intent=["Conserva la rotación externa del antebrazo y el objetivo forearm."],
                target_regions=["forearm"],
                setup_cue="Sujeta el peso con la palma hacia abajo y el codo apoyado y fijo.",
                exec_cue="Gira la palma hacia arriba rotando solo el antebrazo, con el codo quieto.",
                mistake="Mover el codo o el hombro para ayudar a girar el peso.",
            )
        )
    pron_note = note(
        "forearm",
        "Principal: los músculos pronadores rotan el antebrazo contra la resistencia; por eso suman la serie completa (1.0).",
    )
    pron_configs = []
    for implement, laterality in (("dumbbells", "BILATERAL"), ("cable", "UNILATERAL")):
        pron_configs.append(
            make_config(
                wrist_template,
                cfg_id=f"pronation__{implement}",
                options={"implement": implement},
                display_summary=f"{implement} · {laterality.lower()}",
                description=PRON_DESC[implement],
                primary=["forearm"], secondary=[], stabilizers=[],
                notes=[pron_note],
                equipment_id=implement, laterality=laterality, axial=0.0,
                perf_id=f"pronation__{implement}",
                objectives=[f"Desarrollar forearm con pronaciones en {implement}."],
                required_equipment=[implement], compatible_equipment=["dumbbells", "cable"],
                preserves_intent=["Conserva la rotación interna del antebrazo y el objetivo forearm."],
                target_regions=["forearm"],
                setup_cue="Sujeta el peso con la palma hacia arriba y el codo apoyado y fijo.",
                exec_cue="Gira la palma hacia abajo rotando solo el antebrazo, con el codo quieto.",
                mistake="Levantar el codo o usar el hombro para facilitar la rotación.",
            )
        )
    new_family = {
        "canonicalName": "Rotaciones de Antebrazo",
        "description": "Familia de rotación del antebrazo con implemento explícito: supinar y pronar trabajan los músculos que giran la palma y dan fuerza y salud a la muñeca y el codo.",
        "evidence": wrist_family["evidence"],
        "id": "lower_wrist_rotation",
        "taxonomy": ["lower", "wrist_rotation"],
        "definitions": [
            new_definition(
                def_id="supination", family_id="lower_wrist_rotation",
                canonical_name="Supinaciones", description=SUP_DEF_DESC,
                option_axes=["implement"],
                search_terms=["supinaciones", "supinación", "rotación externa antebrazo", "giro palma arriba"],
                configurations=sup_configs, default_id="supination__dumbbells",
            ),
            new_definition(
                def_id="pronation", family_id="lower_wrist_rotation",
                canonical_name="Pronaciones", description=PRON_DEF_DESC,
                option_axes=["implement"],
                search_terms=["pronaciones", "pronación", "rotación interna antebrazo", "giro palma abajo"],
                configurations=pron_configs, default_id="pronation__dumbbells",
            ),
        ],
    }
    new_payload = {
        "schemaVersion": wrist_payload["schemaVersion"],
        "catalogRevision": wrist_payload["catalogRevision"],
        "ontologyRevision": wrist_payload["ontologyRevision"],
        "family": new_family,
    }
    if not (FAMILIES / "lower_wrist_rotation.json").exists():
        sync_identity(new_payload, REVISION)
        save("lower_wrist_rotation.json", new_payload)


# ---------------------------------------------------------------------------
# P2 — Involucramiento muscular adaptativo por chips
# ---------------------------------------------------------------------------
ROW_MUSCLES = {
    "wide": (["latissimus_dorsi"], ["trapezius", "deltoid"], ["rhomboids", "biceps"]),
    "medium": (["latissimus_dorsi"], ["trapezius", "rhomboids", "biceps"], ["deltoid"]),
    "close": (["latissimus_dorsi"], ["rhomboids", "biceps"], ["deltoid"]),
}


def row_notes(grip: str) -> list:
    if grip == "wide":
        return [
            note("latissimus_dorsi", "Principal: el dorsal dirige el tirón y concentra el trabajo en todo el ancho de la espalda; por eso suma la serie completa (1.0)."),
            note("trapezius", "Secundario: con el agarre amplio, el trapecio y la espalda alta participan más al llevar los codos hacia atrás; por eso suman 0.5."),
            note("deltoid", "Secundario: el deltoides posterior asiste la abducción horizontal de los brazos en el agarre amplio; por eso suma 0.5."),
            note("rhomboids", "Estabilizador: los romboides juntan las escápulas al final del tirón; por eso suman 0.4."),
            note("biceps", "Estabilizador: el bíceps flexiona el codo con un brazo de palanca más corto en el agarre amplio; por eso suma 0.4."),
        ]
    if grip == "close":
        return [
            note("latissimus_dorsi", "Principal: el dorsal dirige el tirón y se estira más con los codos pegados al cuerpo; por eso suma la serie completa (1.0)."),
            note("rhomboids", "Secundario: con el agarre cerrado, los romboides retraen las escápulas con más recorrido; por eso suman 0.5."),
            note("biceps", "Secundario: el bíceps trabaja más con los codos cerca del cuerpo flexionando en posición de palanca larga; por eso suma 0.5."),
            note("deltoid", "Estabilizador: el deltoides posterior mantiene el hombro estable durante el tirón; por eso suma 0.4."),
        ]
    return [
        note("latissimus_dorsi", "Principal: el dorsal dirige el tirón y concentra el trabajo en todo el ancho de la espalda; por eso suma la serie completa (1.0)."),
        note("trapezius", "Secundario: el trapecio ayuda a retraer las escápulas al llevar los codos atrás; por eso suma 0.5."),
        note("rhomboids", "Secundario: los romboides juntan las escápulas al final del tirón; por eso suman 0.5."),
        note("biceps", "Secundario: el bíceps flexiona el codo con el agarre medio y reparte el trabajo del brazo; por eso suma 0.5."),
        note("deltoid", "Estabilizador: el deltoides posterior mantiene el hombro estable durante el tirón; por eso suma 0.4."),
    ]


def p2_adaptive_muscles() -> None:
    for fam_file, def_id in (
        ("back_chest_supported_row.json", "chest_supported_row"),
        ("back_t_bar_row.json", "t_bar_row"),
        ("back_gironda_row.json", "gironda_row"),
    ):
        payload = load(fam_file)
        rewrite_muscles(payload, def_id, ROW_MUSCLES, {g: row_notes(g) for g in ("wide", "medium", "close")})
        save(fam_file, payload)

    # Dominadas: supino enfatiza bíceps, pronado/neutro lo reduce a estabilizador
    payload = load("upper_vertical_pull_pull_up.json")
    definition = find_def(payload, "pull_up")
    lat_note = note(
        "latissimus_dorsi",
        "Principal: el dorsal extiende y aduce el hombro para subir el pecho a la barra; por eso suma la serie completa (1.0).",
    )
    core_note = note(
        "core",
        "Estabilizador: el core mantiene el cuerpo firme y evita el balanceo durante la dominada; por eso suma 0.4.",
    )
    rhomb_note = note(
        "rhomboids",
        "Estabilizador: los romboides retraen las escápulas al final del tirón; por eso suman 0.4.",
    )
    for cfg in definition["configurations"]:
        grip = cfg["selectedOptions"]["grip_type"]
        width = cfg["selectedOptions"]["grip_width"]
        wide = width == "wide"
        if grip == "supinated":
            primary, secondary, stabilizers = ["latissimus_dorsi"], ["biceps"], ["rhomboids", "core"]
            if wide:
                stabilizers.append("trapezius")
            notes = [
                lat_note,
                note("biceps", "Secundario: con el agarre supino, el bíceps trabaja mucho más porque parte más flexionado y tira en su postura más fuerte; por eso suma 0.5."),
                rhomb_note,
                core_note,
            ]
            if wide:
                notes.insert(3, note("trapezius", "Estabilizador: con el agarre amplio, la espalda alta ayuda a controlar el descenso; por eso suma 0.4."))
        elif grip == "neutral":
            primary, secondary, stabilizers = ["latissimus_dorsi"], [], ["biceps", "rhomboids", "core"]
            notes = [lat_note, rhomb_note, core_note]
            if wide:
                stabilizers.insert(0, "trapezius")
                notes.insert(2, note("trapezius", "Estabilizador: con el agarre amplio, la espalda alta ayuda a controlar el descenso; por eso suma 0.4."))
            notes.insert(2, note("biceps", "Estabilizador: el bíceps asiste la flexión del codo con agarre neutro sin protagonizar el tirón; por eso suma 0.4."))
        else:
            primary, secondary, stabilizers = ["latissimus_dorsi"], [], ["biceps", "rhomboids", "core"]
            if wide:
                secondary = ["trapezius"]
            notes = [lat_note, rhomb_note, core_note]
            if wide:
                notes.insert(1, note("trapezius", "Secundario: el agarre amplio reparte más trabajo a la espalda alta y el trapecio; por eso suma 0.5."))
            notes.insert(2, note("biceps", "Estabilizador: con el agarre pronado, el bíceps solo asiste la flexión del codo; por eso suma 0.4."))
        set_muscles(cfg, primary, secondary, stabilizers, notes)
    save("upper_vertical_pull_pull_up.json", payload)


# ---------------------------------------------------------------------------
# Descripciones (se completan por pasadas en este mismo archivo)
# ---------------------------------------------------------------------------
def p3_descriptions() -> None:
    from curaduria_v4_descripciones import DEF_DESC, CFG_DESC
    from curaduria_v3_transform import FAMILIES
    for path in sorted(FAMILIES.glob("*.json")):
        payload = load(path.name)
        for definition in fam_of(payload)["definitions"]:
            if definition["id"] in DEF_DESC:
                definition["description"] = DEF_DESC[definition["id"]]
            for cfg in definition["configurations"]:
                if cfg["id"] in CFG_DESC:
                    cfg["profile"]["description"] = CFG_DESC[cfg["id"]]
        save(path.name, payload)


# Descripciones nuevas (definiciones y configuraciones de los ejercicios nuevos)
HAMMER_DEF_DESC = ("El curl en versión martillo: agarras el peso con las palmas enfrentadas y subes y bajas con los codos fijos. "
                   "Siente el trabajo correr por el braquial y el antebrazo mientras le das un toque más de grosor a todo el brazo.")
HAMMER_DESC = {
    "h_bar": "Con la barra H, las palmas quedan enfrentadas de forma natural y el recorrido se siente muy estable. La versión perfecta para centrarte solo en la tensión del brazo sin distracciones.",
    "dumbbells": "Con mancuernas, cada brazo trabaja por su cuenta: notarás si un lado carga más que el otro y podrás igualarlos poco a poco. El clásico que nunca falla para dar grosor al brazo.",
    "cable": "En polea, la tensión no se suelta ni un segundo: el antebrazo y el braquial arden desde la primera repetición. Ideal para rematar el brazo con una sensación constante de trabajo.",
    "machine": "En máquina, el recorrido viene guiado y solo te queda empujar y sentir. La opción más cómoda para concentrarte en la contracción sin preocuparte por el equilibrio del peso.",
    "band": "Con banda elástica, la resistencia crece cuando más tenso estás arriba. Perfecta para hacer un buen trabajo de brazo en casa o como remate rápido al final de la sesión.",
}
REVERSE_DEF_DESC = ("El mismo curl, pero con las palmas hacia abajo: el trabajo se corre al braquiorradial y al antebrazo. "
                    "Es la herramienta perfecta para equilibrar la fuerza de agarre y darle vida a unos antebrazos que aguantan todo.")
REVERSE_DESC = {
    "h_bar": "Con la barra H y agarre prono, el antebrazo toma el protagonismo con un recorrido cómodo y estable para las muñecas. La opción más agradable para series largas.",
    "dumbbells": "Con mancuernas y palmas hacia abajo, sientes cómo el antebrazo hace el trabajo pesado. Además cada lado se entrena por separado para corregir desequilibrios.",
    "cable": "En polea, la tensión constante convierte cada repetición en un martilleo para el antebrazo. Perfecto para acabar con la fuerza de agarre en las últimas series.",
    "machine": "En máquina, el guiado te deja exprimir el antebrazo sin pensar en estabilizar el peso. La versión más limpia para centrarse en la contracción.",
    "band": "Con banda, la resistencia aumenta arriba y el antebrazo aguanta la tensión en todo el recorrido. Fácil de montar en cualquier lado y muy exigente.",
}
SUP_DEF_DESC = ("Gira la palma de la mano hacia arriba contra la resistencia: un movimiento pequeño que trabaja de verdad los músculos que "
                "supinan el antebrazo. Clave para la salud de la muñeca, el codo y un agarre más fuerte en todo lo que hagas.")
SUP_DESC = {
    "dumbbells": "Con mancuerna, el codo apoyado y la palma girando arriba: sientes el trabajo profundo en el antebrazo y el bíceps ayuda a girar.",
    "cable": "En polea, la tensión se mantiene todo el recorrido y el giro se siente lleno de principio a fin. Una forma muy pulida de fortalecer la muñeca.",
}
PRON_DEF_DESC = ("Gira la palma de la mano hacia abajo contra la resistencia: el gesto complementario a la supinación que fortalece "
                 "los pronadores del antebrazo. Pequeño en tamaño, grande en beneficios para el codo y el agarre.")
PRON_DESC = {
    "dumbbells": "Con mancuerna, el codo apoyado y la palma girando abajo: el antebrazo trabaja solo y el movimiento se siente muy controlado.",
    "cable": "En polea, la tensión constante hace que el giro hacia abajo cargue todo el recorrido. Ideal para igualar la fuerza de las rotaciones.",
}


if __name__ == "__main__":
    p1_new_exercises()
    p2_adaptive_muscles()
    p3_descriptions()
    print("curaduria v4 OK")
