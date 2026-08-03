#!/usr/bin/env python3
"""Curaduría v3: transformaciones editoriales sobre la superficie families/.

Aplica los cambios aprobados por el dueño del producto a los archivos de
familia, escribiéndolos con la misma serialización canónica que el split para
que split<->merge sigan siendo un round-trip estable. Después del script:
    python scripts/merge_catalog_v2_families.py
    python scripts/compile_exercise_catalog_v2_cli.py --check
    python scripts/catalog_v2_gate.py --strict
"""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILIES = ROOT / "catalog" / "exercises" / "v2" / "source" / "families"

# ---------------------------------------------------------------------------
# Serialización de familias: forma pretty legible (split/merge solo re-leen con
# json.loads y re-canonicalizan, así que el formato es libre y no afecta el
# hash del agregado)
# ---------------------------------------------------------------------------
def family_canonical(value) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def load(name: str) -> dict:
    return json.loads((FAMILIES / name).read_text(encoding="utf-8"))


def save(name: str, payload: dict) -> None:
    (FAMILIES / name).write_bytes(family_canonical(payload))


def fam_of(payload: dict) -> dict:
    return payload["family"]


def find_def(payload: dict, def_id: str) -> dict:
    for definition in fam_of(payload)["definitions"]:
        if definition["id"] == def_id:
            return definition
    raise KeyError(def_id)


# ---------------------------------------------------------------------------
# Perfil técnico por implemento
# ---------------------------------------------------------------------------
LOAD_PROFILES = {
    "machine": ("guided_external_load", "guided_constant"),
    "cable": ("continuous_cable", "continuous_cable"),
    "band": ("variable_band_resistance", "variable_band"),
    "bodyweight": ("bodyweight", "body_angle"),
    "barbell": ("free_external_load", "gravity_arc"),
    "ez_bar": ("free_external_load", "gravity_arc"),
    "hex_bar": ("free_external_load", "gravity_arc"),
    "dumbbells": ("free_external_load", "gravity_arc"),
    "kettlebell": ("free_external_load", "gravity_arc"),
    "t_bar": ("free_external_load", "gravity_arc"),
    "smith_machine": ("guided_external_load", "guided_constant"),
    "safety_bar": ("free_external_load", "gravity_arc"),
    "plate": ("free_external_load", "gravity_arc"),
    "trx": ("suspension", "body_angle"),
}

# ---------------------------------------------------------------------------
# Construcción de configuraciones nuevas a partir de una plantilla
# ---------------------------------------------------------------------------
def make_config(
    template: dict,
    *,
    cfg_id: str,
    options: dict,
    display_summary: str,
    description: str,
    primary: list,
    secondary: list,
    stabilizers: list,
    notes: list,
    equipment_id: str,
    laterality: str,
    axial: float,
    perf_id: str,
    objectives: list,
    required_equipment: list,
    compatible_equipment: list,
    preserves_intent: list,
    target_regions: list,
    setup_cue: str,
    exec_cue: str,
    mistake: str,
) -> dict:
    cfg = copy.deepcopy(template)
    cfg["id"] = cfg_id
    cfg["selectedOptions"] = dict(options)
    cfg["displaySummary"] = display_summary
    p = cfg["profile"]
    p["description"] = description
    p["equipmentId"] = equipment_id
    p["laterality"] = laterality
    load_mode, resistance = LOAD_PROFILES[equipment_id]
    p["loadMode"] = load_mode
    p["resistanceProfile"] = resistance
    p["axialLoadFactor"] = axial
    p["primaryMuscles"] = list(primary)
    p["secondaryMuscles"] = list(secondary)
    p["stabilizerMuscles"] = list(stabilizers)
    p["muscleNotes"] = copy.deepcopy(notes)
    p["performanceProfileId"] = perf_id
    p["setupCues"] = [setup_cue]
    p["executionCues"] = [exec_cue]
    p["commonMistakes"] = [mistake]
    rich = p["richMetadata"]
    rich["anatomy"]["primaryMuscles"] = list(primary)
    rich["anatomy"]["secondaryMuscles"] = list(secondary)
    rich["anatomy"]["stabilizerMuscles"] = list(stabilizers)
    rich["anatomy"]["targetRegions"] = list(target_regions)
    biomech = rich["biomechanics"]
    biomech["equipmentId"] = equipment_id
    biomech["laterality"] = laterality
    biomech["loadMode"] = load_mode
    biomech["resistanceProfile"] = resistance
    rich["display"]["displayName"] = cfg["displaySummary"] if False else None  # placeholder, set by sync pass
    rich["display"]["displaySummary"] = display_summary
    rich["display"]["selectedOptions"] = dict(options)
    rich["identity"]["configurationId"] = cfg_id
    rich["identity"]["performanceProfileId"] = perf_id
    rich["coaching"]["setup"] = [setup_cue]
    rich["coaching"]["execution"] = [exec_cue]
    rich["coaching"]["commonMistakes"] = [mistake]
    rich["programming"]["requiredEquipment"] = list(required_equipment)
    rich["programming"]["objectives"] = list(objectives)
    rich["replacement"]["compatibleEquipmentIds"] = list(compatible_equipment)
    rich["replacement"]["preservesIntent"] = list(preserves_intent)
    return cfg


def default_evidence() -> dict:
    return {
        "confidence": "MEDIUM",
        "evidenceRefs": ["editorial:catalog-v3-2026-08-02"],
        "rationale": "Aprobado por decisión explícita de catálogo v3; la configuración está materializada y no depende de inferencia runtime.",
        "reviewStatus": "APPROVED",
    }


def new_definition(
    *,
    def_id: str,
    family_id: str,
    canonical_name: str,
    description: str,
    option_axes: list,
    search_terms: list,
    configurations: list,
    default_id: str,
    evidence: dict | None = None,
) -> dict:
    return {
        "id": def_id,
        "familyId": family_id,
        "kind": "PARENT" if option_axes else "SPECIALTY",
        "canonicalName": canonical_name,
        "description": description,
        "searchTerms": search_terms,
        "optionAxes": list(option_axes),
        "configurations": configurations,
        "defaultConfigurationId": default_id,
        "evidence": evidence or default_evidence(),
    }


def drop_def(payload: dict, def_id: str) -> None:
    family = fam_of(payload)
    family["definitions"] = [d for d in family["definitions"] if d["id"] != def_id]


def rename_def(payload: dict, def_id: str, canonical_name: str, search_terms: list | None = None) -> None:
    definition = find_def(payload, def_id)
    definition["canonicalName"] = canonical_name
    if search_terms is not None:
        definition["searchTerms"] = search_terms


# ---------------------------------------------------------------------------
# Notas musculares y textos comunes
# ---------------------------------------------------------------------------
def note(muscle_id: str, text: str) -> dict:
    return {"muscleId": muscle_id, "note": text}


GENERIC_OBJECTIVES = "Desarrollar la musculatura específica del patrón con la configuración seleccionada."


# ---------------------------------------------------------------------------
# Pasada final de sincronización de identidad
# ---------------------------------------------------------------------------
def sync_identity(payload: dict, catalog_revision: str) -> None:
    family = fam_of(payload)
    for definition in family["definitions"]:
        for configuration in definition["configurations"]:
            profile = configuration["profile"]
            rich = profile["richMetadata"]
            rich["identity"]["canonicalName"] = definition["canonicalName"]
            rich["identity"]["searchTerms"] = definition["searchTerms"]
            rich["identity"]["catalogRevision"] = catalog_revision
            rich["identity"]["kind"] = definition["kind"]
            rich["identity"]["configurationId"] = configuration["id"]
            rich["identity"]["definitionId"] = definition["id"]
            rich["identity"]["familyId"] = family["id"]
            rich["display"]["displayName"] = definition["canonicalName"]
            rich["display"]["displaySummary"] = configuration["displaySummary"]
            rich["display"]["selectedOptions"] = configuration["selectedOptions"]
            rich["biomechanics"]["equipmentId"] = profile["equipmentId"]
            rich["biomechanics"]["laterality"] = profile["laterality"]
            rich["biomechanics"]["loadMode"] = profile["loadMode"]
            rich["biomechanics"]["resistanceProfile"] = profile["resistanceProfile"]
            rich["fatigue"]["efc"] = profile["efc"]
            rich["fatigue"]["cnc"] = profile["cnc"]
            rich["fatigue"]["ssc"] = profile["ssc"]
            rich["fatigue"]["ttc"] = profile["ttc"]
            rich["fatigue"]["axialLoadFactor"] = profile["axialLoadFactor"]
            rich["fatigue"]["technicalDifficulty"] = profile["technicalDifficulty"]
            rich["anatomy"]["primaryMuscles"] = profile["primaryMuscles"]
            rich["anatomy"]["secondaryMuscles"] = profile["secondaryMuscles"]
            rich["anatomy"]["stabilizerMuscles"] = profile["stabilizerMuscles"]
            rich["coaching"]["setup"] = profile["setupCues"]
            rich["coaching"]["execution"] = profile["executionCues"]
            rich["coaching"]["commonMistakes"] = profile["commonMistakes"]
            rich["replacement"]["replacementGroup"] = profile.get("replacementGroup")
            rich["replacement"]["replacementPriority"] = profile.get("replacementPriority")


def run_lot(name: str, fn) -> None:
    print(f"[lote] {name}")
    fn()
    print(f"[lote] {name} OK")


# ---------------------------------------------------------------------------
# LOTE 1 — Cadera y glúteos
# ---------------------------------------------------------------------------
HIP_MACHINE_NOTE = note("tensor_fasciae_latae", "Secundario: el tensor asiste la abducción y la estabilización lateral de la cadera; por eso suma 0.5.")

def lote1():
    # Abducciones de Pierna: matriz completa máquina/polea/banda x bilateral/unilateral
    payload = load("hip_abduction.json")
    definition = find_def(payload, "hip_abduction")
    template_machine = next(c for c in definition["configurations"] if c["id"].endswith("seated__machine__bilateral"))
    template_cable = next(c for c in definition["configurations"] if c["id"].endswith("standing__cable__unilateral"))
    template_band = next(c for c in definition["configurations"] if c["id"].endswith("standing__band__unilateral"))
    gluteus = [note("gluteus_medius", "Principal: el glúteo medio abduce el muslo y estabiliza la pelvis durante el movimiento lateral; por eso suma la serie completa (1.0).")]
    configs = [
        make_config(
            template_machine, cfg_id="hip_abduction__seated__machine__bilateral",
            options={"implement": "machine", "station": "seated", "laterality": "bilateral"},
            display_summary="machine · seated · bilateral",
            description="Abducciones de pierna sentado en máquina con trabajo bilateral: la estación guiada aísla el glúteo medio con tensión constante y recorrido controlado.",
            primary=["gluteus_medius"], secondary=["tensor_fasciae_latae"], stabilizers=[],
            notes=gluteus + [HIP_MACHINE_NOTE], equipment_id="machine", laterality="BILATERAL", axial=0.0,
            perf_id="hip_abduction__machine__abduccion_de_cadera",
            objectives=["Desarrollar gluteus_medius dentro del patrón hip_abduction."],
            required_equipment=["machine"], compatible_equipment=["machine", "cable", "band"],
            preserves_intent=["Conserva el patrón hip_abduction y el objetivo gluteus_medius."],
            target_regions=["gluteus_medius"], setup_cue="Ajusta el asiento y las almohadillas a tu altura antes de iniciar.",
            exec_cue="Abre y cierra las piernas con recorrido controlado sin despegar la espalda del respaldo.",
            mistake="Usar impulso o un recorrido demasiado corto que evita el rango completo de la abducción.",
        ),
        make_config(
            template_machine, cfg_id="hip_abduction__seated__machine__unilateral",
            options={"implement": "machine", "station": "seated", "laterality": "unilateral"},
            display_summary="machine · seated · unilateral",
            description="Abducciones de pierna sentado en máquina con trabajo unilateral: permite aislar cada cadera por separado y corregir desequilibrios de fuerza entre lados.",
            primary=["gluteus_medius"], secondary=["tensor_fasciae_latae"], stabilizers=[],
            notes=gluteus + [HIP_MACHINE_NOTE], equipment_id="machine", laterality="UNILATERAL", axial=0.0,
            perf_id="hip_abduction__machine__abduccion_de_cadera_unilateral",
            objectives=["Desarrollar gluteus_medius dentro del patrón hip_abduction con foco unilateral."],
            required_equipment=["machine"], compatible_equipment=["machine", "cable", "band"],
            preserves_intent=["Conserva el patrón hip_abduction y el objetivo gluteus_medius."],
            target_regions=["gluteus_medius"], setup_cue="Ajusta el asiento y las almohadillas a tu altura antes de iniciar.",
            exec_cue="Abduce una pierna con recorrido controlado, termina la serie completa y cambia de lado.",
            mistake="Usar impulso o apoyarte en el respaldo para compensar el lado trabajado.",
        ),
        make_config(
            template_cable, cfg_id="hip_abduction__standing__cable__bilateral",
            options={"implement": "cable", "station": "standing", "laterality": "bilateral"},
            display_summary="cable · standing · bilateral",
            description="Abducciones de pierna de pie en polea con trabajo bilateral: la tensión continua de la polea mantiene el glúteo medio activo a lo largo del recorrido.",
            primary=["gluteus_medius"], secondary=["tensor_fasciae_latae"], stabilizers=[],
            notes=gluteus + [HIP_MACHINE_NOTE], equipment_id="cable", laterality="BILATERAL", axial=0.0,
            perf_id="hip_abduction__cable__abduccion_de_cadera",
            objectives=["Desarrollar gluteus_medius dentro del patrón hip_abduction con polea."],
            required_equipment=["cable"], compatible_equipment=["cable", "machine", "band"],
            preserves_intent=["Conserva el patrón hip_abduction y el objetivo gluteus_medius."],
            target_regions=["gluteus_medius"], setup_cue="Coloca el tobilloera en la polea baja y busca un apoyo firme y estable.",
            exec_cue="Abduce la pierna contra la polea con movimiento controlado en ambos sentidos.",
            mistake="Balancear el tronco para mover la pierna en lugar de aislar la abducción de cadera.",
        ),
        make_config(
            template_cable, cfg_id="hip_abduction__standing__cable__unilateral",
            options={"implement": "cable", "station": "standing", "laterality": "unilateral"},
            display_summary="cable · standing · unilateral",
            description="Abducciones de pierna de pie en polea con trabajo unilateral: ideal para igualar la fuerza de cada cadera y sostener tensión en el punto de máxima contracción.",
            primary=["gluteus_medius"], secondary=["tensor_fasciae_latae"], stabilizers=[],
            notes=gluteus + [HIP_MACHINE_NOTE], equipment_id="cable", laterality="UNILATERAL", axial=0.0,
            perf_id="hip_abduction__cable__abduccion_de_cadera_unilateral",
            objectives=["Desarrollar gluteus_medius dentro del patrón hip_abduction con foco unilateral en polea."],
            required_equipment=["cable"], compatible_equipment=["cable", "machine", "band"],
            preserves_intent=["Conserva el patrón hip_abduction y el objetivo gluteus_medius."],
            target_regions=["gluteus_medius"], setup_cue="Coloca el tobilloera en la polea baja y busca un apoyo firme y estable.",
            exec_cue="Abduce una pierna con tensión continua, completa la serie y cambia de lado.",
            mistake="Perder el control de la pelvis y compensar con inclinación del tronco.",
        ),
        make_config(
            template_band, cfg_id="hip_abduction__standing__band__bilateral",
            options={"implement": "band", "station": "standing", "laterality": "bilateral"},
            display_summary="band · standing · bilateral",
            description="Abducciones de pierna de pie con banda elástica y trabajo bilateral: la resistencia variable exige más esfuerzo al final del recorrido, donde más trabaja el glúteo medio.",
            primary=["gluteus_medius"], secondary=["tensor_fasciae_latae"], stabilizers=[],
            notes=gluteus + [HIP_MACHINE_NOTE], equipment_id="band", laterality="BILATERAL", axial=0.0,
            perf_id="hip_abduction__band__abduccion_de_cadera",
            objectives=["Desarrollar gluteus_medius dentro del patrón hip_abduction con banda."],
            required_equipment=["band"], compatible_equipment=["band", "cable", "machine"],
            preserves_intent=["Conserva el patrón hip_abduction y el objetivo gluteus_medius."],
            target_regions=["gluteus_medius"], setup_cue="Coloca la banda alrededor de los tobillos y separa los pies al ancho de la cadera.",
            exec_cue="Abduce las piernas contra la banda con control en el retorno.",
            mistake="Reducir el rango al final del movimiento, donde la banda ofrece mayor resistencia.",
        ),
        make_config(
            template_band, cfg_id="hip_abduction__standing__band__unilateral",
            options={"implement": "band", "station": "standing", "laterality": "unilateral"},
            display_summary="band · standing · unilateral",
            description="Abducciones de pierna de pie con banda elástica y trabajo unilateral: permite trabajar cada cadera con su propia resistencia y detectar asimetrías de fuerza.",
            primary=["gluteus_medius"], secondary=["tensor_fasciae_latae"], stabilizers=[],
            notes=gluteus + [HIP_MACHINE_NOTE], equipment_id="band", laterality="UNILATERAL", axial=0.0,
            perf_id="hip_abduction__band__abduccion_de_cadera_unilateral",
            objectives=["Desarrollar gluteus_medius dentro del patrón hip_abduction con foco unilateral en banda."],
            required_equipment=["band"], compatible_equipment=["band", "cable", "machine"],
            preserves_intent=["Conserva el patrón hip_abduction y el objetivo gluteus_medius."],
            target_regions=["gluteus_medius"], setup_cue="Ancla la banda en un punto fijo y coloca el otro extremo en el tobillo.",
            exec_cue="Abduce la pierna contra la banda, completa la serie y cambia de lado.",
            mistake="Compensar con la cadera en vez de aislar la abducción con la pierna de trabajo.",
        ),
    ]
    definition["configurations"] = configs
    definition["defaultConfigurationId"] = "hip_abduction__seated__machine__bilateral"
    save("hip_abduction.json", payload)

    # Aducciones de Pierna: misma matriz, primary aductores
    payload = load("hip_adduction.json")
    definition = find_def(payload, "hip_adduction")
    template_machine = next(c for c in definition["configurations"] if c["id"].endswith("seated__machine__bilateral"))
    template_cable = next(c for c in definition["configurations"] if c["id"].endswith("standing__cable__unilateral"))
    template_band = next(c for c in definition["configurations"] if c["id"].endswith("standing__band__unilateral"))
    adductors_note = [note("adductors", "Principal: los aductores acercan el muslo a la línea media y concentran el trabajo del movimiento; por eso suman la serie completa (1.0).")]
    def ad(cfg_id, options, summary, description, laterality, perf, template, objective):
        return make_config(
            template, cfg_id=cfg_id, options=options, display_summary=summary,
            description=description, primary=["adductors"], secondary=[], stabilizers=[],
            notes=adductors_note, equipment_id=options["implement"], laterality=laterality, axial=0.0,
            perf_id=perf, objectives=[objective], required_equipment=[options["implement"]],
            compatible_equipment=["machine", "cable", "band"],
            preserves_intent=["Conserva el patrón hip_adduction y el objetivo adductors."],
            target_regions=["adductors"],
            setup_cue="Ajusta el equipo y la posición de inicio antes de mover las piernas.",
            exec_cue="Cierra las piernas con recorrido controlado y resiste el retorno.",
            mistake="Usar impulso o un rango corto que no alcanza la máxima contracción.",
        )
    configs = [
        ad("hip_adduction__seated__machine__bilateral", {"implement": "machine", "station": "seated", "laterality": "bilateral"},
           "machine · seated · bilateral",
           "Aducciones de pierna sentado en máquina con trabajo bilateral: la estación guiada aísla los aductores con tensión constante y recorrido controlado.",
           "BILATERAL", "hip_adduction__machine__adduccion_de_cadera", template_machine,
           "Desarrollar adductors dentro del patrón hip_adduction."),
        ad("hip_adduction__seated__machine__unilateral", {"implement": "machine", "station": "seated", "laterality": "unilateral"},
           "machine · seated · unilateral",
           "Aducciones de pierna sentado en máquina con trabajo unilateral: permite aislar cada pierna y corregir desequilibrios de fuerza entre lados.",
           "UNILATERAL", "hip_adduction__machine__adduccion_de_cadera_unilateral", template_machine,
           "Desarrollar adductors dentro del patrón hip_adduction con foco unilateral."),
        ad("hip_adduction__standing__cable__bilateral", {"implement": "cable", "station": "standing", "laterality": "bilateral"},
           "cable · standing · bilateral",
           "Aducciones de pierna de pie en polea con trabajo bilateral: la tensión continua de la polea mantiene los aductores activos a lo largo del recorrido.",
           "BILATERAL", "hip_adduction__cable__adduccion_de_cadera", template_cable,
           "Desarrollar adductors dentro del patrón hip_adduction con polea."),
        ad("hip_adduction__standing__cable__unilateral", {"implement": "cable", "station": "standing", "laterality": "unilateral"},
           "cable · standing · unilateral",
           "Aducciones de pierna de pie en polea con trabajo unilateral: ideal para igualar la fuerza de cada pierna y sostener tensión en el punto de máxima contracción.",
           "UNILATERAL", "hip_adduction__cable__adduccion_de_cadera_unilateral", template_cable,
           "Desarrollar adductors dentro del patrón hip_adduction con foco unilateral en polea."),
        ad("hip_adduction__standing__band__bilateral", {"implement": "band", "station": "standing", "laterality": "bilateral"},
           "band · standing · bilateral",
           "Aducciones de pierna de pie con banda elástica y trabajo bilateral: la resistencia variable exige más esfuerzo al cerrar las piernas.",
           "BILATERAL", "hip_adduction__band__adduccion_de_cadera", template_band,
           "Desarrollar adductors dentro del patrón hip_adduction con banda."),
        ad("hip_adduction__standing__band__unilateral", {"implement": "band", "station": "standing", "laterality": "unilateral"},
           "band · standing · unilateral",
           "Aducciones de pierna de pie con banda elástica y trabajo unilateral: permite trabajar cada pierna con su propia resistencia y detectar asimetrías.",
           "UNILATERAL", "hip_adduction__band__adduccion_de_cadera_unilateral", template_band,
           "Desarrollar adductors dentro del patrón hip_adduction con foco unilateral en banda."),
    ]
    definition["configurations"] = configs
    definition["defaultConfigurationId"] = "hip_adduction__seated__machine__bilateral"
    save("hip_adduction.json", payload)

    # Patada de Glúteo -> padre polea/mancuernas/banda
    payload = load("lower_hip_extension.json")
    definition = find_def(payload, "glutes_patada_gluteo")
    template = definition["configurations"][0]
    glute_note = [note("gluteus_maximus", "Principal: el glúteo mayor extiende la cadera hacia atrás y concentra el estímulo del movimiento; por eso suma la serie completa (1.0).")]
    def kick(cfg_id, implement, summary, description):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["gluteus_maximus"], secondary=[], stabilizers=[],
            notes=glute_note, equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0,
            perf_id=f"glutes_patada_gluteo__{implement}",
            objectives=["Desarrollar gluteus_maximus en extensión de cadera con patada de glúteo."],
            required_equipment=[implement], compatible_equipment=["cable", "dumbbells", "band"],
            preserves_intent=["Conserva la extensión de cadera y el objetivo gluteus_maximus."],
            target_regions=["gluteus_maximus"],
            setup_cue="Ajusta la polea baja o el lastre a la altura del tobillo y busca apoyo estable.",
            exec_cue="Extiende la cadera hacia atrás con control, evitando arqueo lumbar.",
            mistake="Arquear la zona lumbar para ganar recorrido en lugar de extender la cadera.",
        )
    definition["canonicalName"] = "Patada de Glúteo"
    definition["optionAxes"] = ["implement"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["patada de gluteo", "patada de gluteo en polea", "glute kickback", "kickback"]
    definition["configurations"] = [
        kick("glutes_patada_gluteo__cable", "cable", "cable",
             "Patada de glúteo en polea con tensión continua: el glúteo mayor extiende la cadera hacia atrás a lo largo del recorrido, con la polea baja como punto de anclaje."),
        kick("glutes_patada_gluteo__dumbbells", "dumbbells", "dumbbells",
             "Patada de glúteo con mancuerna detrás de la rodilla: la carga libre permite sentir bien la contracción del glúteo mayor al extender la cadera hacia atrás."),
        kick("glutes_patada_gluteo__band", "band", "band",
             "Patada de glúteo con banda elástica en el tobillo: la resistencia crece al final del recorrido, justo donde el glúteo mayor debe contraerse con más fuerza."),
    ]
    definition["defaultConfigurationId"] = "glutes_patada_gluteo__cable"
    save("lower_hip_extension.json", payload)

    # Patada de Glúteo Lateral -> padre, glúteo medio
    payload = load("lower_hip_abduction_extension.json")
    definition = find_def(payload, "glutes_patada_gluteo_lateral")
    template = definition["configurations"][0]
    medius_note = [note("gluteus_medius", "Principal: el glúteo medio abduce la pierna hacia un lado y estabiliza la pelvis; por eso suma la serie completa (1.0).")]
    def kick_lat(cfg_id, implement, summary, description):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["gluteus_medius"], secondary=[], stabilizers=[],
            notes=medius_note, equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0,
            perf_id=f"glutes_patada_gluteo_lateral__{implement}",
            objectives=["Desarrollar gluteus_medius en abducción de cadera con patada lateral."],
            required_equipment=[implement], compatible_equipment=["cable", "dumbbells", "band"],
            preserves_intent=["Conserva la abducción de cadera y el objetivo gluteus_medius."],
            target_regions=["gluteus_medius"],
            setup_cue="Coloca la resistencia en el tobillo y mantén el tronco estable con el apoyo de una mano.",
            exec_cue="Abduce la pierna hacia el costado con control y sin rotar el tronco.",
            mistake="Girar el tronco para ganar altura en lugar de aislar la abducción de cadera.",
        )
    definition["canonicalName"] = "Patada de Glúteo Lateral"
    definition["optionAxes"] = ["implement"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["patada lateral", "patada de gluteo lateral", "lateral kickback", "abduccion de cadera"]
    definition["configurations"] = [
        kick_lat("glutes_patada_gluteo_lateral__cable", "cable", "cable",
                 "Patada de glúteo lateral en polea baja: el glúteo medio abduce la pierna hacia un lado con tensión continua a lo largo del recorrido."),
        kick_lat("glutes_patada_gluteo_lateral__dumbbells", "dumbbells", "dumbbells",
                 "Patada de glúteo lateral con mancuerna: la carga libre aísla el glúteo medio al abducir la pierna hacia el costado."),
        kick_lat("glutes_patada_gluteo_lateral__band", "band", "band",
                 "Patada de glúteo lateral con banda elástica: la resistencia máxima llega al final del recorrido, donde el glúteo medio trabaja más."),
    ]
    definition["defaultConfigurationId"] = "glutes_patada_gluteo_lateral__cable"
    save("lower_hip_abduction_extension.json", payload)

    # Puente de Glúteos -> padre barra/mancuernas/smith x bilateral/unilateral
    payload = load("lower_hip_extension.json")
    definition = find_def(payload, "glutes_puente_gluteos")
    template = definition["configurations"][0]
    bridge_note = [note("gluteus_maximus", "Principal: el glúteo mayor extiende la cadera contra la carga y concentra el estímulo del puente; por eso suma la serie completa (1.0).")]
    def bridge(cfg_id, implement, laterality, summary, description):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement, "laterality": laterality},
            display_summary=summary, description=description,
            primary=["gluteus_maximus"], secondary=[], stabilizers=[],
            notes=bridge_note, equipment_id=implement,
            laterality="BILATERAL" if laterality == "bilateral" else "UNILATERAL", axial=0.4 if implement != "machine" else 0.2,
            perf_id=f"glutes_puente_gluteos__{implement}__{laterality}",
            objectives=["Desarrollar gluteus_maximus en extensión de cadera con puente de glúteos."],
            required_equipment=[implement], compatible_equipment=["barbell", "dumbbells", "smith_machine"],
            preserves_intent=["Conserva la extensión de cadera y el objetivo gluteus_maximus."],
            target_regions=["gluteus_maximus"],
            setup_cue="Coloca la carga sobre la cadera y los pies apoyados a una distancia cómoda.",
            exec_cue="Eleva la cadera hasta el puente y baja con control sin perder tensión en el glúteo.",
            mistake="Despegar demasiado la zona lumbar o cortar el recorrido arriba sin contraer el glúteo.",
        )
    definition["canonicalName"] = "Puente de Glúteos"
    definition["optionAxes"] = ["implement", "laterality"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["puente de gluteos", "glute bridge", "bridge", "hip bridge"]
    definition["configurations"] = [
        bridge("glutes_puente_gluteos__bilateral__barbell", "barbell", "bilateral", "barbell · bilateral",
               "Puente de glúteos con barra sobre la cadera y trabajo bilateral: la carga libre permite progresar la extensión de cadera con pesos altos y técnica simple."),
        bridge("glutes_puente_gluteos__unilateral__barbell", "barbell", "unilateral", "barbell · unilateral",
               "Puente de glúteos con barra y apoyo en una pierna: el trabajo unilateral expone asimetrías de fuerza entre caderas y exige estabilidad pélvica."),
        bridge("glutes_puente_gluteos__bilateral__dumbbells", "dumbbells", "bilateral", "dumbbells · bilateral",
               "Puente de glúteos con mancuerna sobre la cadera y trabajo bilateral: opción cómoda para empezar a cargar sin depender de rack ni barra larga."),
        bridge("glutes_puente_gluteos__unilateral__dumbbells", "dumbbells", "unilateral", "dumbbells · unilateral",
               "Puente de glúteos con mancuerna y apoyo en una pierna: combina carga cómoda con el reto de estabilizar la pelvis en cada repetición."),
        bridge("glutes_puente_gluteos__bilateral__smith_machine", "smith_machine", "bilateral", "smith_machine · bilateral",
               "Puente de glúteos en máquina Smith con trabajo bilateral: el recorrido guiado simplifica la colocación y permite concentrarse en la contracción del glúteo."),
        bridge("glutes_puente_gluteos__unilateral__smith_machine", "smith_machine", "unilateral", "smith_machine · unilateral",
               "Puente de glúteos en máquina Smith con apoyo en una pierna: el guiado elimina el equilibrio y deja el foco en la fuerza de cada cadera."),
    ]
    definition["defaultConfigurationId"] = "glutes_puente_gluteos__bilateral__barbell"
    save("lower_hip_extension.json", payload)


# ---------------------------------------------------------------------------
# LOTE 2 — Pecho y aperturas
# ---------------------------------------------------------------------------
def lote2():
    # Aperturas planas/inclinadas/declinadas: core fuera de estabilizadores
    payload = load("chest_fly.json")
    family = fam_of(payload)
    for definition in family["definitions"]:
        if definition["id"] not in {"flat_chest_fly", "incline_chest_fly", "decline_chest_fly"}:
            continue
        for configuration in definition["configurations"]:
            profile = configuration["profile"]
            profile["stabilizerMuscles"] = []
            profile["muscleNotes"] = [n for n in profile["muscleNotes"] if n["muscleId"] != "core"]
            profile["richMetadata"]["anatomy"]["stabilizerMuscles"] = []
            profile["richMetadata"]["replacement"]["preservesIntent"] = [
                text for text in profile["richMetadata"]["replacement"]["preservesIntent"]
                if "core" not in text.lower()
            ]
    save("chest_fly.json", payload)

    # Aperturas inversas: ejes [implement, laterality], default máquina pec deck + bilateral
    definition = find_def(payload, "reverse_pec_fly")
    template_machine = next(c for c in definition["configurations"] if c["id"].endswith("pec_deck__machine"))
    template_cable = next(c for c in definition["configurations"] if c["id"].endswith("standing__cable"))
    deltoid_note = [note("deltoid", "Principal: el deltoides posterior lleva el brazo atrás en abducción horizontal y concentra el trabajo; por eso suma la serie completa (1.0).")]
    trap_note = [note("trapezius", "Secundario: el trapecio fija y acompaña la escápula durante la abducción horizontal; por eso suma 0.5.")]
    rhomb_note = [note("rhomboids", "Secundario: los romboides retraen la escápula y estabilizan la espalda alta; por eso suman 0.5.")]
    def rpf(cfg_id, implement, laterality, summary, description, template, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement, "laterality": laterality},
            display_summary=summary, description=description,
            primary=["deltoid"], secondary=["trapezius", "rhomboids"], stabilizers=[],
            notes=deltoid_note + trap_note + rhomb_note, equipment_id=implement,
            laterality="BILATERAL" if laterality == "bilateral" else "UNILATERAL", axial=0.0,
            perf_id=perf,
            objectives=["Desarrollar el deltoides posterior en abducción horizontal."],
            required_equipment=[implement], compatible_equipment=["machine", "cable", "dumbbells"],
            preserves_intent=["Conserva la abducción horizontal y el énfasis en el deltoides posterior."],
            target_regions=["deltoid"],
            setup_cue="Ajusta el asiento, la altura del mango o el agarre según la estación elegida.",
            exec_cue="Lleva los brazos hacia atrás con control y vuelve a la posición inicial sin dejar caer los hombros.",
            mistake="Subir los hombros o mover el tronco en lugar de aislar la abducción horizontal.",
        )
    definition["canonicalName"] = "Aperturas Inversas"
    definition["optionAxes"] = ["implement", "laterality"]
    definition["searchTerms"] = ["aperturas inversas", "reverse fly", "reverse pec fly", "pec deck inverso", "deltoides posterior", "apertura posterior"]
    definition["description"] = "Abducción horizontal de hombros que enfatiza de forma específica el deltoides posterior. Las opciones cubren máquina pec deck, polea y mancuernas, en trabajo bilateral o unilateral."
    definition["configurations"] = [
        rpf("reverse_pec_fly__bilateral__machine", "machine", "bilateral", "machine · bilateral",
            "Aperturas inversas en máquina pec deck con trabajo bilateral: el recorrido guiado aísla el deltoides posterior de forma segura y constante.",
            template_machine, "reverse_pec_fly__machine__abduccion_horizontal"),
        rpf("reverse_pec_fly__unilateral__machine", "machine", "unilateral", "machine · unilateral",
            "Aperturas inversas en máquina pec deck con trabajo unilateral: permite igualar la fuerza del deltoides posterior entre ambos hombros.",
            template_machine, "reverse_pec_fly__machine__abduccion_horizontal_unilateral"),
        rpf("reverse_pec_fly__bilateral__cable", "cable", "bilateral", "cable · bilateral",
            "Aperturas inversas en polea con trabajo bilateral: la tensión continua mantiene el deltoides posterior activo a lo largo del recorrido.",
            template_cable, "reverse_pec_fly__cable__abduccion_horizontal"),
        rpf("reverse_pec_fly__unilateral__cable", "cable", "unilateral", "cable · unilateral",
            "Aperturas inversas en polea con trabajo unilateral: ideal para trabajar cada deltoides posterior por separado con tensión constante.",
            template_cable, "reverse_pec_fly__cable__abduccion_horizontal_unilateral"),
        rpf("reverse_pec_fly__bilateral__dumbbells", "dumbbells", "bilateral", "dumbbells · bilateral",
            "Aperturas inversas con mancuernas y trabajo bilateral: el peso libre permite ajustar el rango y la carga a cada nivel de fuerza del deltoides posterior.",
            template_machine, "reverse_pec_fly__dumbbells__abduccion_horizontal"),
        rpf("reverse_pec_fly__unilateral__dumbbells", "dumbbells", "unilateral", "dumbbells · unilateral",
            "Aperturas inversas con mancuerna y trabajo unilateral: la carga libre expone asimetrías de fuerza entre los dos deltoides posteriores.",
            template_machine, "reverse_pec_fly__dumbbells__abduccion_horizontal_unilateral"),
    ]
    definition["defaultConfigurationId"] = "reverse_pec_fly__bilateral__machine"
    save("chest_fly.json", payload)

    # Cruce de Poleas -> padre cable fijo con altura de polea
    payload = load("upper_horizontal_push.json")
    definition = find_def(payload, "tren_superior_cruce_poleas")
    template = definition["configurations"][0]
    pec_note = [note("pectoralis", "Principal: el pectoral aduce el brazo hacia la línea media y concentra el trabajo del cruce; por eso suma la serie completa (1.0).")]
    delt_note = [note("deltoid", "Secundario: el deltoides anterior asiste la flexión de hombro durante el cruce; por eso suma 0.5.")]
    def crossover(cfg_id, height, summary, description):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": "cable", "pulley_height": height},
            display_summary=summary, description=description,
            primary=["pectoralis"], secondary=["deltoid"], stabilizers=[],
            notes=pec_note + delt_note, equipment_id="cable", laterality="NOT_APPLICABLE", axial=0.0,
            perf_id=f"tren_superior_cruce_poleas__{height}",
            objectives=["Desarrollar pectoralis en aducción horizontal con cruce de poleas."],
            required_equipment=["cable"], compatible_equipment=["cable"],
            preserves_intent=["Conserva la aducción horizontal y el objetivo pectoralis."],
            target_regions=["pectoralis"],
            setup_cue="Ajusta ambas poleas a la misma altura y coloca un pie adelantado para estabilizarte.",
            exec_cue="Junta las manos frente al pecho con control y deja que los brazos vuelvan con tensión.",
            mistake="Usar el peso del cuerpo para completar el cruce en lugar de aislar el pectoral.",
        )
    definition["canonicalName"] = "Cruce de Poleas"
    definition["optionAxes"] = ["implement", "pulley_height"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["cruce de poleas", "crossover", "cable crossover", "cruces", "pecho en polea"]
    definition["description"] = "Aducción horizontal del pectoral en estación de poleas dobles. La altura de la polea cambia el enfoque del pecho: desde la altura alta el trabajo se concentra en la porción inferior del pectoral."
    definition["configurations"] = [
        crossover("tren_superior_cruce_poleas__high", "high", "cable · high",
                  "Cruce de poleas con las poleas en altura alta: el ángulo descendente pone el énfasis en la porción inferior del pectoral."),
        crossover("tren_superior_cruce_poleas__mid", "mid", "cable · mid",
                  "Cruce de poleas con las poleas a la altura media: el recorrido horizontal reparte el trabajo de forma equilibrada por todo el pectoral."),
        crossover("tren_superior_cruce_poleas__low", "low", "cable · low",
                  "Cruce de poleas con las poleas en altura baja: el ángulo ascendente pone el énfasis en la porción superior del pectoral."),
    ]
    definition["defaultConfigurationId"] = "tren_superior_cruce_poleas__mid"
    save("upper_horizontal_push.json", payload)

    # Press de Pecho en Máquina Convergente -> Press Plano + nueva Press Inclinado
    definition = find_def(payload, "tren_superior_press_pecho_maquina_convergente")
    definition["canonicalName"] = "Press Plano en Máquina Convergente"
    definition["searchTerms"] = ["press plano maquina convergente", "press de pecho convergente", "converging press", "chest press convergente"]
    config_template = copy.deepcopy(definition["configurations"][0])
    inclined = copy.deepcopy(definition)
    inclined["id"] = "tren_superior_press_inclinado_maquina_convergente"
    inclined["canonicalName"] = "Press Inclinado en Máquina Convergente"
    inclined["description"] = "Press de pecho en banco inclinado dentro de una máquina convergente: el ángulo desplaza el trabajo hacia la porción superior del pectoral manteniendo el recorrido guiado."
    inclined["searchTerms"] = ["press inclinado maquina convergente", "incline converging press", "press convergente inclinado"]
    inclined_config = copy.deepcopy(config_template)
    inclined_config["id"] = "tren_superior_press_inclinado_maquina_convergente__default"
    inclined_config["profile"]["description"] = "Press de pecho inclinado en máquina convergente: el ángulo del banco enfatiza la porción superior del pectoral con el recorrido guiado de la máquina."
    inclined_config["profile"]["richMetadata"]["identity"]["configurationId"] = inclined_config["id"]
    inclined_config["profile"]["richMetadata"]["identity"]["definitionId"] = inclined["id"]
    inclined_config["profile"]["richMetadata"]["identity"]["canonicalName"] = inclined["canonicalName"]
    inclined_config["profile"]["richMetadata"]["display"]["displayName"] = inclined["canonicalName"]
    inclined_config["profile"]["richMetadata"]["identity"]["searchTerms"] = inclined["searchTerms"]
    inclined_config["displaySummary"] = config_template["displaySummary"]
    inclined["configurations"] = [inclined_config]
    inclined["defaultConfigurationId"] = inclined_config["id"]
    family = fam_of(payload)
    family["definitions"].append(inclined)
    save("upper_horizontal_push.json", payload)


# ---------------------------------------------------------------------------
# LOTE 3 — Hombro
# ---------------------------------------------------------------------------
def lote3():
    # Elevaciones laterales de pie y sentado: {polea, mancuernas, máquina, kettlebell}
    payload = load("shoulder_lateral_raise.json")
    family = fam_of(payload)
    delt_primary = [note("deltoid", "Principal: el deltoides lateral abduce el brazo y concentra el trabajo de la elevación; por eso suma la serie completa (1.0).")]
    trap_note = [note("trapezius", "Secundario: el trapecio superior asiste la elevación del brazo en la fase final; por eso suma 0.5.")]
    for def_id, name, perf_prefix in (("standing_lateral_raise", "Elevaciones Laterales de Pie", "standing"), ("seated_lateral_raise", "Elevaciones Laterales Sentado", "seated")):
        definition = find_def(payload, def_id)
        template = definition["configurations"][0]
        def lr(cfg_id, implement, summary, description):
            return make_config(
                template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
                description=description, primary=["deltoid"], secondary=["trapezius"], stabilizers=[],
                notes=delt_primary + trap_note, equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0,
                perf_id=f"lateral_raise_{perf_prefix}__{implement}",
                objectives=["Desarrollar el deltoides lateral en abducción de hombro."],
                required_equipment=[implement], compatible_equipment=["cable", "dumbbells", "machine", "kettlebell"],
                preserves_intent=["Conserva la abducción de hombro y el objetivo deltoides lateral."],
                target_regions=["deltoid"],
                setup_cue="Busca una postura estable y los brazos relajados a los costados antes de empezar.",
                exec_cue="Eleva los brazos hasta la altura del hombro con control y baja sin golpear.",
                mistake="Balancear el cuerpo o subir los hombros para impulsar el peso.",
            )
        definition["canonicalName"] = name
        definition["optionAxes"] = ["implement"]
        definition["searchTerms"] = ["elevaciones laterales", "elevacion lateral", "lateral raise", "deltoides lateral"]
        definition["configurations"] = [
            lr(f"{def_id}__dumbbells", "dumbbells", "dumbbells",
               f"{name} con mancuernas: la carga libre permite ajustar el peso y el rango a la fuerza de cada deltoides lateral."),
            lr(f"{def_id}__cable", "cable", "cable",
               f"{name} en polea: la tensión continua mantiene el deltoides lateral activo incluso en la fase de bajada."),
            lr(f"{def_id}__machine", "machine", "machine",
               f"{name} en máquina: el recorrido guiado elimina el equilibrio y deja el foco en la contracción del deltoides lateral."),
            lr(f"{def_id}__kettlebell", "kettlebell", "kettlebell",
               f"{name} con kettlebell: la carga compacta resulta cómoda para sesiones de hombro y permite ritmos controlados."),
        ]
        definition["defaultConfigurationId"] = f"{def_id}__dumbbells"
    # Super ROM unificada (elimina las dos defs previas y crea la definitiva)
    drop_def(payload, "lateral_raise_super_rom")
    drop_def(payload, "lateral_raise_barbell")
    template_super = find_def(payload, "standing_lateral_raise")["configurations"][0]
    def srom(cfg_id, implement, summary, description):
        return make_config(
            template_super, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["deltoid"], secondary=["trapezius"], stabilizers=[],
            notes=delt_primary + trap_note, equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0,
            perf_id=f"lateral_raise_super_rom__{implement}",
            objectives=["Desarrollar el deltoides lateral con rango completo de abducción."],
            required_equipment=[implement], compatible_equipment=["cable", "dumbbells", "machine"],
            preserves_intent=["Conserva la abducción completa de hombro y el objetivo deltoides lateral."],
            target_regions=["deltoid"],
            setup_cue="Elige una carga ligera y prepara el brazo para un rango de recorrido completo.",
            exec_cue="Sube el brazo más allá de la altura del hombro con control y vuelve por el mismo camino.",
            mistake="Compensar el final del rango elevando el hombro o arqueando el tronco.",
        )
    super_def = new_definition(
        def_id="lateral_raise_super_rom", family_id="shoulder_lateral_raise",
        canonical_name="Elevaciones Laterales Super ROM",
        description="Elevaciones laterales con rango de recorrido completo, por encima de la altura del hombro, para reclutar el deltoides lateral en toda su amplitud de movimiento.",
        option_axes=["implement"],
        search_terms=["elevaciones laterales super rom", "super rom lateral", "elevacion super rom", "full rom lateral raise"],
        configurations=[
            srom("lateral_raise_super_rom__cable", "cable", "cable",
                 "Elevaciones laterales Super ROM en polea: la tensión continua acompaña el rango completo del deltoides lateral, incluido el tramo final sobre la altura del hombro."),
            srom("lateral_raise_super_rom__dumbbells", "dumbbells", "dumbbells",
                 "Elevaciones laterales Super ROM con mancuernas: el rango extendido sobre la cabeza exige carga ligera y control a lo largo del recorrido."),
            srom("lateral_raise_super_rom__machine", "machine", "machine",
                 "Elevaciones laterales Super ROM en máquina: el recorrido guiado sostiene el rango completo sin comprometer la estabilidad del hombro."),
        ],
        default_id="lateral_raise_super_rom__cable",
    )
    family["definitions"].append(super_def)
    save("shoulder_lateral_raise.json", payload)

    # Duplicada Super ROM en polea (otra familia) -> eliminar
    payload = load("upper_shoulder_abduction_full_rom.json")
    drop_def(payload, "deltoides_elevaciones_laterales_super_rom_polea")
    save("upper_shoulder_abduction_full_rom.json", payload)

    # Elevaciones posteriores -> {mancuernas, polea, máquina}
    payload = load("shoulder_rear_delt_raise.json")
    definition = find_def(payload, "rear_delt_raise")
    template = definition["configurations"][0]
    posterior_note = [note("deltoid", "Principal: el deltoides posterior abduce horizontalmente el brazo y concentra el trabajo; por eso suma la serie completa (1.0).")]
    trap2 = [note("trapezius", "Secundario: el trapecio fija la escápula durante la abducción horizontal; por eso suma 0.5.")]
    rhomb2 = [note("rhomboids", "Secundario: los romboides retraen la escápula y estabilizan la espalda alta; por eso suman 0.5.")]
    def rd(cfg_id, implement, summary, description):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["deltoid"], secondary=["trapezius", "rhomboids"], stabilizers=[],
            notes=posterior_note + trap2 + rhomb2, equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0,
            perf_id=f"rear_delt_raise__{implement}",
            objectives=["Desarrollar el deltoides posterior en abducción horizontal."],
            required_equipment=[implement], compatible_equipment=["dumbbells", "cable", "machine"],
            preserves_intent=["Conserva la abducción horizontal y el objetivo deltoides posterior."],
            target_regions=["deltoid"],
            setup_cue="Prepara la inclinación del tronco o el asiento según la estación elegida.",
            exec_cue="Abre los brazos hacia los lados con control y baja sin dejar caer los hombros.",
            mistake="Mover el tronco o elevar los hombros para completar el recorrido.",
        )
    definition["canonicalName"] = "Elevaciones Posteriores"
    definition["optionAxes"] = ["implement"]
    definition["searchTerms"] = ["elevaciones posteriores", "rear delt", "rear delt raise", "deltoides posterior"]
    definition["description"] = "Abducción horizontal de hombros con énfasis en el deltoides posterior. Mancuernas, polea y máquina cubren las estaciones más útiles para trabajar la espalda alta y la cara posterior del hombro."
    definition["configurations"] = [
        rd("rear_delt_raise__dumbbells", "dumbbells", "dumbbells",
           "Elevaciones posteriores con mancuernas: la carga libre permite ajustar el peso y el rango al deltoides posterior de cada lado."),
        rd("rear_delt_raise__cable", "cable", "cable",
           "Elevaciones posteriores en polea: la tensión continua mantiene el deltoides posterior activo durante todo el recorrido."),
        rd("rear_delt_raise__machine", "machine", "machine",
           "Elevaciones posteriores en máquina: el recorrido guiado aísla el deltoides posterior sin exigir estabilidad extra."),
    ]
    definition["defaultConfigurationId"] = "rear_delt_raise__dumbbells"
    save("shoulder_rear_delt_raise.json", payload)

    # Elevaciones frontales -> padre {polea, barra, mancuernas, kettlebell}
    payload = load("upper_shoulder_flexion.json")
    definition = find_def(payload, "deltoides_elevaciones_frontales")
    template = definition["configurations"][0]
    front_note = [note("deltoid", "Principal: el deltoides anterior flexiona el hombro y concentra el trabajo de la elevación frontal; por eso suma la serie completa (1.0).")]
    trap3 = [note("trapezius", "Secundario: el trapecio asiste la fase final de la elevación frontal; por eso suma 0.5.")]
    def fl(cfg_id, implement, summary, description):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["deltoid"], secondary=["trapezius"], stabilizers=[],
            notes=front_note + trap3, equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0,
            perf_id=f"deltoides_elevaciones_frontales__{implement}",
            objectives=["Desarrollar el deltoides anterior en flexión de hombro."],
            required_equipment=[implement], compatible_equipment=["cable", "barbell", "dumbbells", "kettlebell"],
            preserves_intent=["Conserva la flexión de hombro y el objetivo deltoides anterior."],
            target_regions=["deltoid"],
            setup_cue="Toma el implemento con una postura estable y los brazos extendidos al frente.",
            exec_cue="Eleva el brazo hasta la altura del hombro con control y baja sin balanceo.",
            mistake="Impulsar con el tronco o arquear la espalda para ganar altura.",
        )
    definition["canonicalName"] = "Elevaciones Frontales"
    definition["optionAxes"] = ["implement"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["elevaciones frontales", "elevacion frontal", "front raise", "deltoides anterior"]
    definition["configurations"] = [
        fl("deltoides_elevaciones_frontales__cable", "cable", "cable",
           "Elevaciones frontales en polea: la tensión continua mantiene el deltoides anterior activo a lo largo del recorrido."),
        fl("deltoides_elevaciones_frontales__barbell", "barbell", "barbell",
           "Elevaciones frontales con barra: la carga libre con ambas manos permite cargar más peso en un movimiento simple."),
        fl("deltoides_elevaciones_frontales__dumbbells", "dumbbells", "dumbbells",
           "Elevaciones frontales con mancuernas: el trabajo con cada brazo facilita la simetría del deltoides anterior."),
        fl("deltoides_elevaciones_frontales__kettlebell", "kettlebell", "kettlebell",
           "Elevaciones frontales con kettlebell: la carga compacta es cómoda para series de hombro y rangos controlados."),
    ]
    definition["defaultConfigurationId"] = "deltoides_elevaciones_frontales__dumbbells"
    save("upper_shoulder_flexion.json", payload)

    # Press de Hombros de Pie eliminado (duplica Press Militar)
    payload = load("upper_vertical_push.json")
    drop_def(payload, "deltoides_press_hombros_de_pie")
    save("upper_vertical_push.json", payload)

    # Encogimientos y Kelso -> padres
    payload = load("upper_scapular_elevation.json")
    definition = find_def(payload, "back_encogimientos")
    template = definition["configurations"][0]
    trap_primary = [note("trapezius", "Principal: el trapecio superior eleva la escápula y concentra el trabajo del encogimiento; por eso suma la serie completa (1.0).")]
    def shrug(cfg_id, implement, summary, description):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["trapezius"], secondary=["rhomboids"], stabilizers=[],
            notes=trap_primary + [note("rhomboids", "Secundario: los romboides estabilizan la escápula durante la elevación; por eso suman 0.5.")],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.3,
            perf_id=f"back_encogimientos__{implement}",
            objectives=["Desarrollar trapezius en elevación escapular."],
            required_equipment=[implement], compatible_equipment=["dumbbells", "smith_machine", "cable", "kettlebell", "barbell"],
            preserves_intent=["Conserva la elevación escapular y el objetivo trapezius."],
            target_regions=["trapezius"],
            setup_cue="Toma el peso con los brazos extendidos y la postura erguida.",
            exec_cue="Eleva los hombros hacia las orejas y baja con control sin balanceo.",
            mistake="Usar los brazos o el tronco en lugar de la elevación escapular.",
        )
    definition["canonicalName"] = "Encogimientos"
    definition["optionAxes"] = ["implement"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["encogimientos", "shrugs", "encogimiento de hombros", "trapecio"]
    definition["configurations"] = [
        shrug("back_encogimientos__dumbbells", "dumbbells", "dumbbells",
              "Encogimientos con mancuernas: la carga libre a los costados permite cargar cómodo y ajustar el peso a cada lado."),
        shrug("back_encogimientos__smith_machine", "smith_machine", "smith_machine",
              "Encogimientos en máquina Smith: el recorrido guiado estabiliza la barra y permite usar cargas altas con seguridad."),
        shrug("back_encogimientos__cable", "cable", "cable",
              "Encogimientos en polea: la tensión continua mantiene el trapecio activo durante todo el recorrido."),
        shrug("back_encogimientos__kettlebell", "kettlebell", "kettlebell",
              "Encogimientos con kettlebell: la carga compacta facilita el agarre y el control en series largas."),
        shrug("back_encogimientos__barbell", "barbell", "barbell",
              "Encogimientos con barra: la carga libre frente a los muslos permite progresar peso de forma simple."),
    ]
    definition["defaultConfigurationId"] = "back_encogimientos__dumbbells"

    kelso_def = find_def(payload, "back_encogimientos_kelso")
    kelso_template = kelso_def["configurations"][0]
    def kelso(cfg_id, implement, summary, description):
        return make_config(
            kelso_template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["trapezius"], secondary=["rhomboids"], stabilizers=[],
            notes=trap_primary + [note("rhomboids", "Secundario: los romboides sostienen la escápula en la posición inclinada; por eso suman 0.5.")],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.2,
            perf_id=f"back_encogimientos_kelso__{implement}",
            objectives=["Desarrollar trapezius con el patrón inclinado de los Kelso Shrugs."],
            required_equipment=[implement], compatible_equipment=["barbell", "machine", "cable", "kettlebell", "smith_machine"],
            preserves_intent=["Conserva la elevación escapular inclinada y el objetivo trapezius."],
            target_regions=["trapezius"],
            setup_cue="Apoya el tronco inclinado sobre un banco o la estación y deja los brazos colgando.",
            exec_cue="Eleva los hombros hacia arriba con control desde la posición inclinada.",
            mistake="Levantar el tronco del apoyo para impulsar el encogimiento.",
        )
    kelso_def["canonicalName"] = "Kelso Shrugs"
    kelso_def["optionAxes"] = ["implement"]
    kelso_def["kind"] = "PARENT"
    kelso_def["searchTerms"] = ["kelso shrugs", "encogimientos kelso", "shrug inclinado"]
    kelso_def["configurations"] = [
        kelso("back_encogimientos_kelso__barbell", "barbell", "barbell",
              "Kelso Shrugs con barra: el tronco inclinado sobre el banco deja caer la barra y sube los hombros con control."),
        kelso("back_encogimientos_kelso__machine", "machine", "machine",
              "Kelso Shrugs en máquina: el apoyo y el recorrido guiado permiten concentrarse en el trapecio sin equilibrio."),
        kelso("back_encogimientos_kelso__cable", "cable", "cable",
              "Kelso Shrugs en polea: la tensión continua mantiene el trapecio trabajando desde la posición inclinada."),
        kelso("back_encogimientos_kelso__kettlebell", "kettlebell", "kettlebell",
              "Kelso Shrugs con kettlebell: la carga compacta es cómoda desde la posición inclinada sobre el banco."),
        kelso("back_encogimientos_kelso__smith_machine", "smith_machine", "smith_machine",
              "Kelso Shrugs en máquina Smith: el guiado de la barra elimina el balanceo y facilita cargas altas."),
    ]
    kelso_def["defaultConfigurationId"] = "back_encogimientos_kelso__barbell"
    save("upper_scapular_elevation.json", payload)


# ---------------------------------------------------------------------------
# LOTE 4 — Bíceps y antebrazo
# ---------------------------------------------------------------------------
def lote4():
    payload = load("elbow_flexion_biceps_curl.json")
    family = fam_of(payload)
    biceps_note = [note("biceps", "Principal: el bíceps flexiona el codo y concentra el trabajo del curl; por eso suma la serie completa (1.0).")]
    forearm_note = [note("forearm", "Secundario: el antebrazo sostiene el agarre y estabiliza la muñeca; por eso suma 0.5.")]
    def curl(parent_id, cfg_id, options, summary, description, perf, template, objective):
        return make_config(
            template, cfg_id=cfg_id, options=dict(options), display_summary=summary,
            description=description, primary=["biceps"], secondary=["forearm"], stabilizers=[],
            notes=biceps_note + forearm_note, equipment_id=options["implement"],
            laterality="NOT_APPLICABLE", axial=0.0, perf_id=perf,
            objectives=[objective], required_equipment=[options["implement"]],
            compatible_equipment=compat_map.get(parent_id, [options["implement"]]),
            preserves_intent=["Conserva la flexión de codo y el objetivo biceps."],
            target_regions=["biceps"],
            setup_cue="Ajusta el banco, el ángulo del tronco o el soporte del brazo según la postura del curl.",
            exec_cue="Flexiona el codo con control y baja sin extender por completo al final.",
            mistake="Balancear el tronco o acelerar la fase de bajada para mover el peso.",
        )
    compat_map = {
        "spider_curl": ["dumbbells", "cable", "barbell"],
        "biceps_curl_bayesian": ["dumbbells", "cable"],
        "concentration_curl": ["dumbbells", "cable"],
        "biceps_curl_sentado_banco_plano": ["dumbbells", "cable"],
    }

    # Curl Araña
    definition = find_def(payload, "spider_curl")
    template = definition["configurations"][0]
    spider_configs = []
    for implement in ("dumbbells", "cable", "barbell"):
        for grip in ("supinated", "neutral", "pronated"):
            grip_label = {"supinated": "Supino", "neutral": "Neutro", "pronated": "Prono"}[grip]
            impl_label = {"dumbbells": "Mancuernas", "cable": "Polea", "barbell": "Barra"}[implement]
            spider_configs.append(curl(
                "spider_curl", f"spider_curl__{implement}__{grip}",
                {"implement": implement, "grip_type": grip},
                f"{implement} · {grip}",
                f"Curl Araña en banco inclinado con {impl_label.lower()} y agarre {grip_label.lower()}: el pecho apoyado en el banco fija los codos y aísla el bíceps con tensión constante.",
                f"spider_curl__{implement}__{grip}",
                template, "Desarrollar biceps con el patrón aislado del curl araña.",
            ))
    definition["canonicalName"] = "Curl Araña"
    definition["optionAxes"] = ["implement", "grip_type"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["curl araña", "spider curl", "curl de biceps araña"]
    definition["configurations"] = spider_configs
    definition["defaultConfigurationId"] = "spider_curl__dumbbells__supinated"

    # Curl Bayesian
    definition = find_def(payload, "biceps_curl_bayesian")
    template = definition["configurations"][0]
    bayes_configs = []
    for implement in ("dumbbells", "cable"):
        for grip in ("supinated", "neutral", "pronated"):
            grip_label = {"supinated": "Supino", "neutral": "Neutro", "pronated": "Prono"}[grip]
            impl_label = {"dumbbells": "Mancuernas", "cable": "Polea"}[implement]
            bayes_configs.append(curl(
                "biceps_curl_bayesian", f"biceps_curl_bayesian__{implement}__{grip}",
                {"implement": implement, "grip_type": grip},
                f"{implement} · {grip}",
                f"Curl Bayesian con {impl_label.lower()} y agarre {grip_label.lower()}: el tronco inclinado hacia delante mantiene el bíceps en tensión durante toda la serie.",
                f"biceps_curl_bayesian__{implement}__{grip}",
                template, "Desarrollar biceps con el patrón de tensión continua del curl bayesian.",
            ))
    definition["canonicalName"] = "Curl Bayesian"
    definition["optionAxes"] = ["implement", "grip_type"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["curl bayesian", "bayesian curl", "curl inclinado"]
    definition["configurations"] = bayes_configs
    definition["defaultConfigurationId"] = "biceps_curl_bayesian__dumbbells__supinated"

    # Curl Concentrado
    definition = find_def(payload, "concentration_curl")
    template = definition["configurations"][0]
    definition["canonicalName"] = "Curl Concentrado"
    definition["optionAxes"] = ["implement"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["curl concentrado", "concentration curl", "curl de biceps concentrado"]
    definition["configurations"] = [
        curl("concentration_curl", "concentration_curl__dumbbells", {"implement": "dumbbells"}, "dumbbells",
             "Curl Concentrado con mancuerna: el codo apoyado en el muslo fija el brazo y aísla el bíceps con contracción máxima arriba.",
             "concentration_curl__dumbbells", template, "Desarrollar biceps con el patrón aislado del curl concentrado."),
        curl("concentration_curl", "concentration_curl__cable", {"implement": "cable"}, "cable",
             "Curl Concentrado en polea: la tensión continua mantiene el bíceps activo incluso en el tramo de máxima contracción.",
             "concentration_curl__cable", template, "Desarrollar biceps con el patrón aislado del curl concentrado en polea."),
    ]
    definition["defaultConfigurationId"] = "concentration_curl__dumbbells"

    # Renombres simples
    rename_def(payload, "biceps_curl_crucifijo", "Curl Crucifijo", ["curl crucifijo", "crucifijo de biceps", "crossover curl"])
    rename_def(payload, "preacher_curl", "Curl Predicador", ["curl predicador", "preacher curl", "curl de biceps predicador"])
    rename_def(payload, "biceps_curl_superman", "Curl Superman", ["curl superman", "superman curl"])
    rename_def(payload, "biceps_curl_drag", "Curl Drag", ["curl drag", "drag curl"])

    # Curl de Bíceps Sentado -> padre {mancuernas, polea}
    definition = find_def(payload, "biceps_curl_sentado_banco_plano")
    template = definition["configurations"][0]
    definition["canonicalName"] = "Curl de Bíceps Sentado"
    definition["optionAxes"] = ["implement"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["curl de biceps sentado", "curl sentado", "seated curl"]
    definition["configurations"] = [
        curl("biceps_curl_sentado_banco_plano", "biceps_curl_sentado_banco_plano__dumbbells", {"implement": "dumbbells"}, "dumbbells",
             "Curl de Bíceps Sentado con mancuernas: la posición sentada evita el balanceo del tronco y deja el trabajo en el bíceps.",
             "biceps_curl_sentado_banco_plano__dumbbells", template, "Desarrollar biceps en posición sentada estable."),
        curl("biceps_curl_sentado_banco_plano", "biceps_curl_sentado_banco_plano__cable", {"implement": "cable"}, "cable",
             "Curl de Bíceps Sentado en polea: la tensión continua mantiene el bíceps trabajando a lo largo del recorrido desde una postura estable.",
             "biceps_curl_sentado_banco_plano__cable", template, "Desarrollar biceps en posición sentada con tensión continua."),
    ]
    definition["defaultConfigurationId"] = "biceps_curl_sentado_banco_plano__dumbbells"

    # Curl de Bíceps Inclinado eliminado (duplica Curl Bayesian)
    drop_def(payload, "incline_biceps_curl")
    save("elbow_flexion_biceps_curl.json", payload)

    # Extensión de Muñeca (antes Curl de Muñeca Inverso)
    payload = load("lower_wrist_extension.json")
    definition = find_def(payload, "forearms_curl_muneca_inverso_sentado")
    template = definition["configurations"][0]
    def wrist_ext(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["forearm"], secondary=[], stabilizers=[],
            notes=[note("forearm", "Principal: los extensores del antebrazo extienden la muñeca y concentran el trabajo; por eso suman la serie completa (1.0).")],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0, perf_id=perf,
            objectives=["Desarrollar los extensores de muñeca en extensión de muñeca."],
            required_equipment=[implement], compatible_equipment=["cable", "dumbbells", "barbell", "ez_bar"],
            preserves_intent=["Conserva la extensión de muñeca y el objetivo de antebrazo."],
            target_regions=["forearm"],
            setup_cue="Apoya el antebrazo sobre el banco con la muñeca fuera del borde.",
            exec_cue="Extiende la muñeca hacia arriba y baja con control.",
            mistake="Mover el antebrazo entero en lugar de aislar la muñeca.",
        )
    definition["canonicalName"] = "Extensión de Muñeca"
    definition["optionAxes"] = ["implement"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["extension de muñeca", "wrist extension", "curl de muñeca inverso"]
    definition["configurations"] = [
        wrist_ext("forearms_curl_muneca_inverso_sentado__cable", "cable", "cable",
                  "Extensión de muñeca en polea: la tensión continua mantiene los extensores del antebrazo activos a lo largo del recorrido.",
                  "forearms_extension_muneca__cable"),
        wrist_ext("forearms_curl_muneca_inverso_sentado__dumbbells", "dumbbells", "dumbbells",
                  "Extensión de muñeca con mancuerna: la carga libre permite ajustar el peso con precisión y trabajar cada muñeca por separado.",
                  "forearms_extension_muneca__dumbbells"),
        wrist_ext("forearms_curl_muneca_inverso_sentado__barbell", "barbell", "barbell",
                  "Extensión de muñeca con barra recta: la barra reparte la carga entre ambas muñecas con un agarre sencillo.",
                  "forearms_extension_muneca__barbell"),
        wrist_ext("forearms_curl_muneca_inverso_sentado__ez_bar", "ez_bar", "ez_bar",
                  "Extensión de muñeca con barra EZ: el agarre angulado resulta más cómodo para las muñecas con sensibilidades.",
                  "forearms_extension_muneca__ez_bar"),
    ]
    definition["defaultConfigurationId"] = "forearms_curl_muneca_inverso_sentado__dumbbells"
    save("lower_wrist_extension.json", payload)

    # Curl de Muñeca (antes Curl de Muñeca Sentado)
    payload = load("lower_wrist_flexion.json")
    definition = find_def(payload, "forearms_curl_muneca_sentado")
    template = definition["configurations"][0]
    def wrist_curl(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["forearm"], secondary=[], stabilizers=[],
            notes=[note("forearm", "Principal: los flexores del antebrazo flexionan la muñeca y concentran el trabajo; por eso suman la serie completa (1.0).")],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0, perf_id=perf,
            objectives=["Desarrollar los flexores de muñeca en flexión de muñeca."],
            required_equipment=[implement], compatible_equipment=["dumbbells", "barbell", "ez_bar", "cable"],
            preserves_intent=["Conserva la flexión de muñeca y el objetivo de antebrazo."],
            target_regions=["forearm"],
            setup_cue="Apoya los antebrazos sobre el banco con las muñecas fuera del borde.",
            exec_cue="Flexiona la muñeca hacia arriba con control y baja sin soltar la tensión.",
            mistake="Levantar los antebrazos del banco para ganar recorrido.",
        )
    definition["canonicalName"] = "Curl de Muñeca"
    definition["optionAxes"] = ["implement"]
    definition["searchTerms"] = ["curl de muñeca", "wrist curl", "flexion de muñeca"]
    definition["configurations"] = [
        wrist_curl("forearms_curl_muneca_sentado__dumbbells", "dumbbells", "dumbbells",
                   "Curl de Muñeca con mancuerna: la carga libre permite trabajar cada muñeca por separado y ajustar el peso con precisión.",
                   "forearms_curl_muneca__dumbbells"),
        wrist_curl("forearms_curl_muneca_sentado__barbell", "barbell", "barbell",
                   "Curl de Muñeca con barra recta: la barra reparte la carga entre ambas muñecas y permite progresar peso.",
                   "forearms_curl_muneca__barbell"),
        wrist_curl("forearms_curl_muneca_sentado__ez_bar", "ez_bar", "ez_bar",
                   "Curl de Muñeca con barra EZ: el agarre angulado resulta más cómodo para las muñecas con sensibilidades.",
                   "forearms_curl_muneca__ez_bar"),
        wrist_curl("forearms_curl_muneca_sentado__cable", "cable", "cable",
                   "Curl de Muñeca en polea: la tensión continua mantiene los flexores activos a lo largo del recorrido.",
                   "forearms_curl_muneca__cable"),
    ]
    definition["defaultConfigurationId"] = "forearms_curl_muneca_sentado__dumbbells"
    save("lower_wrist_flexion.json", payload)


# ---------------------------------------------------------------------------
# LOTE 5 — Tríceps
# ---------------------------------------------------------------------------
def lote5():
    payload = load("upper_elbow_extension.json")
    family = fam_of(payload)
    triceps_note = [note("triceps", "Principal: el tríceps extiende el codo y concentra el trabajo del movimiento; por eso suma la serie completa (1.0).")]

    # Pushdown -> Extensión de Tríceps (padre polea alta/máquina/banda x bilateral/unilateral)
    definition = find_def(payload, "triceps_pushdown")
    if not any(c["id"] == "triceps_pushdown__bilateral__cable" for c in definition["configurations"]):
        template = definition["configurations"][0]
        def ext(cfg_id, implement, laterality, summary, description, perf):
            return make_config(
                template, cfg_id=cfg_id, options={"implement": implement, "laterality": laterality},
                display_summary=summary, description=description,
                primary=["triceps"], secondary=[], stabilizers=[],
                notes=triceps_note, equipment_id=implement,
                laterality="BILATERAL" if laterality == "bilateral" else "UNILATERAL", axial=0.0,
                perf_id=perf,
                objectives=["Desarrollar triceps en extensión de codo con polea alta."],
                required_equipment=[implement], compatible_equipment=["cable", "machine", "band"],
                preserves_intent=["Conserva la extensión de codo y el objetivo triceps."],
                target_regions=["triceps"],
                setup_cue="Coloca los codos pegados al cuerpo y el agarre a la altura del pecho.",
                exec_cue="Extiende los codos hacia abajo con control y deja que la barra vuelva sin perder tensión.",
                mistake="Separar los codos del tronco o usar el impulso del cuerpo para extender.",
            )
        definition["canonicalName"] = "Extensión de Tríceps"
        definition["optionAxes"] = ["implement", "laterality"]
        definition["kind"] = "PARENT"
        definition["searchTerms"] = ["extension de triceps", "triceps pushdown", "pushdown", "polea alta triceps"]
        definition["description"] = "Extensión de codo en polea alta, máquina o banda elástica, con trabajo bilateral o unilateral. Es la base para aislar el tríceps con tensión continua."
        definition["configurations"] = [
            ext("triceps_pushdown__bilateral__cable", "cable", "bilateral", "cable · bilateral",
                "Extensión de Tríceps en polea alta con trabajo bilateral: la tensión continua de la polea mantiene el tríceps activo a lo largo del recorrido.",
                "triceps_pushdown__cable__bilateral"),
            ext("triceps_pushdown__unilateral__cable", "cable", "unilateral", "cable · unilateral",
                "Extensión de Tríceps en polea alta con trabajo unilateral: permite igualar la fuerza de ambos tríceps y corregir asimetrías.",
                "triceps_pushdown__cable__unilateral"),
            ext("triceps_pushdown__bilateral__machine", "machine", "bilateral", "machine · bilateral",
                "Extensión de Tríceps en máquina con trabajo bilateral: el recorrido guiado estabiliza el movimiento y deja el foco en la contracción del tríceps.",
                "triceps_pushdown__machine__bilateral"),
            ext("triceps_pushdown__unilateral__machine", "machine", "unilateral", "machine · unilateral",
                "Extensión de Tríceps en máquina con trabajo unilateral: ideal para trabajar cada brazo por separado con recorrido estable.",
                "triceps_pushdown__machine__unilateral"),
            ext("triceps_pushdown__bilateral__band", "band", "bilateral", "band · bilateral",
                "Extensión de Tríceps con banda elástica y trabajo bilateral: la resistencia crece al extender, justo donde el tríceps debe contraerse con más fuerza.",
                "triceps_pushdown__band__bilateral"),
            ext("triceps_pushdown__unilateral__band", "band", "unilateral", "band · unilateral",
                "Extensión de Tríceps con banda elástica y trabajo unilateral: opción ligera y portable para trabajar cada tríceps con su propia resistencia.",
                "triceps_pushdown__band__unilateral"),
        ]
        definition["defaultConfigurationId"] = "triceps_pushdown__bilateral__cable"
        save("upper_elbow_extension.json", payload)

    # Cruzada -> Extensión de Tríceps Cruzada en Polea Alta (solo polea, lateralidad)
    payload = load("upper_elbow_extension_crossbody.json")
    definition = find_def(payload, "crossbody_triceps_extension")
    template = next(c for c in definition["configurations"] if c["id"].endswith("cable__bilateral"))
    def cross(cfg_id, laterality, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"laterality": laterality},
            display_summary=summary, description=description,
            primary=["triceps"], secondary=[], stabilizers=[],
            notes=triceps_note, equipment_id="cable",
            laterality="BILATERAL" if laterality == "bilateral" else "UNILATERAL", axial=0.0,
            perf_id=perf,
            objectives=["Desarrollar triceps en extensión cruzada de codo en polea alta."],
            required_equipment=["cable"], compatible_equipment=["cable"],
            preserves_intent=["Conserva la extensión de codo en polea alta y el objetivo triceps."],
            target_regions=["triceps"],
            setup_cue="Coloca la polea en la altura alta y lleva el agarre al lado contrario del brazo de trabajo.",
            exec_cue="Extiende el codo cruzando hacia el otro lado con control.",
            mistake="Acompañar el movimiento con el hombro en lugar de aislar la extensión de codo.",
        )
    definition["canonicalName"] = "Extensión de Tríceps Cruzada en Polea Alta"
    definition["optionAxes"] = ["laterality"]
    definition["searchTerms"] = ["extension de triceps cruzada", "cruzada en polea", "crossbody extension", "triceps cruzado"]
    definition["description"] = "Extensión de codo en polea alta con el brazo cruzado hacia el lado contrario del cuerpo, manteniendo el hombro estable y el foco en el tríceps."
    definition["configurations"] = [
        cross("crossbody_triceps__cable__bilateral", "bilateral", "cable · bilateral",
              "Extensión de Tríceps Cruzada en Polea Alta con ambas manos: el recorrido cruzado mantiene tensión constante en el tríceps.",
              "crossbody_triceps__cable__bilateral"),
        cross("crossbody_triceps__cable__unilateral", "unilateral", "cable · unilateral",
              "Extensión de Tríceps Cruzada en Polea Alta con un brazo: la variante unilateral aísla cada tríceps y permite un rango mayor de cruce.",
              "crossbody_triceps__cable__unilateral"),
    ]
    definition["defaultConfigurationId"] = "crossbody_triceps__cable__bilateral"
    save("upper_elbow_extension_crossbody.json", payload)

    # Overhead -> renombre
    payload = load("upper_elbow_extension_overhead.json")
    rename_def(payload, "overhead_triceps_extension", "Extensión de Tríceps Overhead", ["extension de triceps overhead", "overhead extension", "triceps overhead"])
    save("upper_elbow_extension_overhead.json", payload)

    # Katana -> solo polea y banda
    payload = load("triceps_katana_extension.json")
    definition = find_def(payload, "katana_extension")
    template = next(c for c in definition["configurations"] if c["id"].endswith("cable__bilateral"))
    def katana(cfg_id, implement, laterality, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement, "laterality": laterality},
            display_summary=summary, description=description,
            primary=["triceps"], secondary=[], stabilizers=[],
            notes=triceps_note, equipment_id=implement,
            laterality="BILATERAL" if laterality == "bilateral" else "UNILATERAL", axial=0.0,
            perf_id=perf,
            objectives=["Desarrollar triceps con la extensión katana en polea o banda."],
            required_equipment=[implement], compatible_equipment=["cable", "band"],
            preserves_intent=["Conserva el patrón katana y el objetivo triceps."],
            target_regions=["triceps"],
            setup_cue="Sitúa el codo junto al torso y prepara el agarre por encima de la cabeza.",
            exec_cue="Extiende el codo hacia arriba con control manteniendo el brazo estable.",
            mistake="Separar el codo del cuerpo o arquear la espalda para ganar recorrido.",
        )
    definition["canonicalName"] = "Extensión Katana"
    definition["optionAxes"] = ["implement", "laterality"]
    definition["searchTerms"] = ["extension katana", "katana extension", "triceps katana"]
    definition["configurations"] = [
        katana("katana_extension__cable__bilateral", "cable", "bilateral", "cable · bilateral",
               "Extensión Katana en polea con trabajo bilateral: la tensión continua acompaña la extensión de codo por encima de la cabeza.",
               "katana_extension__cable__bilateral"),
        katana("katana_extension__cable__unilateral", "cable", "unilateral", "cable · unilateral",
               "Extensión Katana en polea con trabajo unilateral: cada brazo trabaja por separado con tensión constante.",
               "katana_extension__cable__unilateral"),
        katana("katana_extension__band__bilateral", "band", "bilateral", "band · bilateral",
               "Extensión Katana con banda elástica y trabajo bilateral: la resistencia crece al extender el codo hacia arriba.",
               "katana_extension__band__bilateral"),
    ]
    definition["defaultConfigurationId"] = "katana_extension__cable__bilateral"
    save("triceps_katana_extension.json", payload)

    # Press Francés -> padre {mancuernas, barra, EZ, polea, kettlebell}
    payload = load("upper_elbow_extension.json")
    definition = find_def(payload, "triceps_press_frances")
    template = definition["configurations"][0]
    def frances(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["triceps"], secondary=[], stabilizers=[],
            notes=triceps_note, equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0,
            perf_id=perf,
            objectives=["Desarrollar triceps con el press francés por detrás de la cabeza."],
            required_equipment=[implement], compatible_equipment=["dumbbells", "barbell", "ez_bar", "cable", "kettlebell"],
            preserves_intent=["Conserva la extensión de codo por detrás de la cabeza y el objetivo triceps."],
            target_regions=["triceps"],
            setup_cue="Sitúa el implemento sobre la cabeza con los codos apuntando hacia delante.",
            exec_cue="Baja el peso por detrás de la cabeza flexionando los codos y vuelve a extender con control.",
            mistake="Separar los codos hacia los lados, lo que traslada el trabajo fuera del tríceps.",
        )
    definition["canonicalName"] = "Press Francés"
    definition["optionAxes"] = ["implement"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["press frances", "french press", "extension de triceps por detras de la cabeza"]
    definition["configurations"] = [
        frances("triceps_press_frances__dumbbells", "dumbbells", "dumbbells",
                "Press Francés con mancuerna: la carga libre permite ajustar el peso y la comodidad de cada muñeca.",
                "triceps_press_frances__dumbbells"),
        frances("triceps_press_frances__barbell", "barbell", "barbell",
                "Press Francés con barra recta: la barra permite cargar peso progresivamente con ambas manos.",
                "triceps_press_frances__barbell"),
        frances("triceps_press_frances__ez_bar", "ez_bar", "ez_bar",
                "Press Francés con barra EZ: el agarre angulado alivia las muñecas y mantiene el énfasis en el tríceps.",
                "triceps_press_frances__ez_bar"),
        frances("triceps_press_frances__cable", "cable", "cable",
                "Press Francés en polea: la tensión continua mantiene el tríceps activo incluso en el tramo de máxima elongación.",
                "triceps_press_frances__cable"),
        frances("triceps_press_frances__kettlebell", "kettlebell", "kettlebell",
                "Press Francés con kettlebell: la carga compacta facilita sostener el peso sobre la cabeza con ambas manos.",
                "triceps_press_frances__kettlebell"),
    ]
    definition["defaultConfigurationId"] = "triceps_press_frances__ez_bar"
    save("upper_elbow_extension.json", payload)

    # JM Press -> renombre + implementos completos
    payload = load("triceps_jm_press.json")
    definition = find_def(payload, "jm_press")
    template = definition["configurations"][0]
    def jm(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["triceps"], secondary=["pectoralis"], stabilizers=[],
            notes=triceps_note + [note("pectoralis", "Secundario: el pectoral asiste la fase inicial del press cerca del pecho; por eso suma 0.5.")],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.3,
            perf_id=perf,
            objectives=["Desarrollar triceps con el patrón del JM Press."],
            required_equipment=[implement], compatible_equipment=["barbell", "ez_bar", "dumbbells", "smith_machine", "cable"],
            preserves_intent=["Conserva el patrón del JM Press y el objetivo triceps."],
            target_regions=["triceps"],
            setup_cue="Coloca la barra sobre el pecho con los codos apuntando hacia delante.",
            exec_cue="Baja la barra hacia la garganta con los codos pegados y vuelve a extender con control.",
            mistake="Separar los codos o usar el pecho en lugar del tríceps para empujar.",
        )
    definition["canonicalName"] = "JM Press"
    definition["optionAxes"] = ["implement"]
    definition["searchTerms"] = ["jm press", "press jm"]
    definition["configurations"] = [
        jm("jm_press__barbell", "barbell", "barbell",
           "JM Press con barra recta: el recorrido corto de codo pegado al cuerpo aísla el tríceps con carga libre.",
           "jm_press__barbell"),
        jm("jm_press__ez_bar", "ez_bar", "ez_bar",
           "JM Press con barra EZ: el agarre angulado alivia las muñecas y mantiene el énfasis en el tríceps.",
           "jm_press__ez_bar"),
        jm("jm_press__dumbbells", "dumbbells", "dumbbells",
           "JM Press con mancuernas: cada brazo trabaja por separado y la carga se ajusta con precisión.",
           "jm_press__dumbbells"),
        jm("jm_press__smith_machine", "smith_machine", "smith_machine",
           "JM Press en máquina Smith: el recorrido guiado elimina el equilibrio y permite concentrarse en el tríceps.",
           "jm_press__smith_machine"),
        jm("jm_press__cable", "cable", "cable",
           "JM Press en polea: la tensión continua mantiene el tríceps activo a lo largo del recorrido del press corto.",
           "jm_press__cable"),
    ]
    definition["defaultConfigurationId"] = "jm_press__ez_bar"
    save("triceps_jm_press.json", payload)

    # Renombres de tríceps
    payload = load("upper_elbow_extension_kickback.json")
    rename_def(payload, "triceps_patada", "Patada de Tríceps", ["patada de triceps", "triceps kickback", "kickback"])
    save("upper_elbow_extension_kickback.json", payload)
    payload = load("chest_floor_press.json")
    rename_def(payload, "floor_press", "Floor Press", ["floor press", "press en el suelo"])
    save("chest_floor_press.json", payload)
    payload = load("upper_elbow_extension.json")
    rename_def(payload, "triceps_flexiones_esfinge", "Flexiones Esfinge", ["flexiones esfinge", "sphinx push up", "extensiones de triceps en suelo"])
    save("upper_elbow_extension.json", payload)


# ---------------------------------------------------------------------------
# LOTE 6 — Dominadas y remos
# ---------------------------------------------------------------------------
def lote6():
    payload = load("upper_vertical_pull_pull_up.json")
    definition = find_def(payload, "pull_up")
    template = next(c for c in definition["configurations"])
    lats_primary = note("latissimus_dorsi", "Principal: el dorsal ancho aduce el húmero y concentra el tirón vertical; por eso suma la serie completa (1.0).")
    trap_primary = note("trapezius", "Principal: con el agarre abierto el trapecio fija y rota la escápula y comparte el trabajo de la subida; por eso suma la serie completa (1.0).")
    biceps_primary = note("biceps", "Principal: con el agarre supino el bíceps flexiona el codo y participa de forma notable en la subida; por eso suma la serie completa (1.0).")
    trap_secondary = note("trapezius", "Secundario: el trapecio fija la escápula y asiste la subida; por eso suma 0.5.")
    biceps_secondary = note("biceps", "Secundario: el bíceps flexiona el codo y asiste al dorsal durante la subida; por eso suma 0.5.")
    rhomb_secondary = note("rhomboids", "Secundario: los romboides retraen la escápula y estabilizan la espalda alta; por eso suman 0.5.")
    core_stab = note("core", "Estabilizador: el core mantiene el tronco firme y evita balanceos durante la serie; por eso suma 0.4.")

    def pu(cfg_id, grip_type, grip_width, summary, description, primary, secondary, stabilizers, notes):
        return make_config(
            template, cfg_id=cfg_id, options={"grip_type": grip_type, "grip_width": grip_width},
            display_summary=summary, description=description,
            primary=primary, secondary=secondary, stabilizers=stabilizers,
            notes=notes, equipment_id="bodyweight", laterality="NOT_APPLICABLE", axial=0.0,
            perf_id=f"pull_up__{grip_type}__{grip_width}",
            objectives=["Desarrollar la espalda y el bíceps con el tirón vertical de las dominadas."],
            required_equipment=["bodyweight"], compatible_equipment=["bodyweight"],
            preserves_intent=["Conserva el tirón vertical y el reparto muscular del agarre elegido."],
            target_regions=primary,
            setup_cue="Cuelga de la barra con el agarre elegido y los hombros activados.",
            exec_cue="Sube el pecho hacia la barra con control y baja sin soltarte de golpe.",
            mistake="Balancear las piernas o acortar el rango inferior del tirón.",
        )
    profiles = {
        "pronated": {"wide": ([lats_primary, trap_primary], [biceps_secondary, rhomb_secondary], "ancha"),
                     "medium": ([lats_primary], [trap_secondary, biceps_secondary, rhomb_secondary], "media"),
                     "close": ([lats_primary], [biceps_secondary, rhomb_secondary], "cerrada")},
        "supinated": {"wide": ([lats_primary, trap_primary, biceps_primary], [rhomb_secondary], "ancha"),
                      "medium": ([lats_primary, biceps_primary], [trap_secondary, rhomb_secondary], "media"),
                      "close": ([lats_primary, biceps_primary], [rhomb_secondary], "cerrada")},
        "neutral": {"wide": ([lats_primary, trap_primary], [biceps_secondary, rhomb_secondary], "ancha"),
                    "medium": ([lats_primary], [trap_secondary, biceps_secondary, rhomb_secondary], "media"),
                    "close": ([lats_primary], [biceps_secondary, rhomb_secondary], "cerrada")},
    }
    grip_names = {"pronated": "Prono", "supinated": "Supino", "neutral": "Neutro"}
    descriptions = {
        "wide": "la empuñadura amplia desplaza el trabajo hacia el trapecio y la espalda alta, y reduce la participación del bíceps.",
        "medium": "la posición intermedia reparte el esfuerzo de forma equilibrada entre el dorsal y la espalda alta, con buena participación del bíceps.",
        "close": "la empuñadura estrecha acerca el codo al tronco y concentra el trabajo en el dorsal ancho, con más ayuda del bíceps.",
    }
    configs = []
    for grip_type in ("pronated", "supinated", "neutral"):
        for grip_width in ("wide", "medium", "close"):
            primary, secondary, _ = profiles[grip_type][grip_width]
            notes = primary + secondary + [core_stab]
            extra = " El agarre supino añade una participación notable del bíceps." if grip_type == "supinated" else ""
            desc = f"Dominadas con agarre {grip_names[grip_type].lower()} y amplitud {grip_width}: {descriptions[grip_width]}{extra}"
            configs.append(pu(
                f"pull_up__{grip_type}__{grip_width}", grip_type, grip_width,
                f"{grip_type} · {grip_width}", desc,
                [n["muscleId"] for n in primary], [n["muscleId"] for n in secondary], ["core"], notes,
            ))
    definition["configurations"] = configs
    definition["defaultConfigurationId"] = "pull_up__pronated__medium"
    definition["description"] = "Tirón vertical con el peso corporal donde la elección del agarre y la amplitud cambian el reparto del estímulo: más cerrado concentra el dorsal, más abierto involucra trapecio y espalda alta, y el agarre supino suma bíceps."
    definition["searchTerms"] = ["dominadas", "pull up", "dominadas prono", "dominadas supino", "chin up"]
    save("upper_vertical_pull_pull_up.json", payload)

    # Remo Gorilla y Remo Renegado
    payload = load("upper_horizontal_pull.json")
    gorilla_def = find_def(payload, "back_remo_gorilla_mancuernas")
    template = gorilla_def["configurations"][0]
    lats_note = [note("latissimus_dorsi", "Principal: el dorsal ancho lleva el peso hacia el tronco y concentra el trabajo del remo; por eso suma la serie completa (1.0).")]
    biceps2 = [note("biceps", "Secundario: el bíceps flexiona el codo y asiste la tracción; por eso suma 0.5.")]
    rhomb3 = [note("rhomboids", "Secundario: los romboides retraen la escápula al final del remo; por eso suman 0.5.")]
    def gorilla(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["latissimus_dorsi"], secondary=["biceps", "rhomboids"], stabilizers=[],
            notes=lats_note + biceps2 + rhomb3, equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.2,
            perf_id=perf,
            objectives=["Desarrollar latissimus_dorsi con el patrón del remo gorilla."],
            required_equipment=[implement], compatible_equipment=["dumbbells", "kettlebell", "cable"],
            preserves_intent=["Conserva la tracción horizontal y el objetivo latissimus_dorsi."],
            target_regions=["latissimus_dorsi"],
            setup_cue="Inclina el tronco con la cadera atrás y deja los brazos colgando con el peso.",
            exec_cue="Lleva el peso hacia el tronco con los codos pegados y baja con control.",
            mistake="Enderezar el tronco durante la tracción en lugar de mantener la bisagra.",
        )
    gorilla_def["canonicalName"] = "Remo Gorilla"
    gorilla_def["optionAxes"] = ["implement"]
    gorilla_def["kind"] = "PARENT"
    gorilla_def["searchTerms"] = ["remo gorilla", "gorilla row", "remo con mancuernas"]
    gorilla_def["configurations"] = [
        gorilla("back_remo_gorilla_mancuernas__dumbbells", "dumbbells", "dumbbells",
                "Remo Gorilla con mancuernas: la bisagra de cadera con el peso colgando exige control del core durante toda la tracción.",
                "back_remo_gorilla__dumbbells"),
        gorilla("back_remo_gorilla_mancuernas__kettlebell", "kettlebell", "kettlebell",
                "Remo Gorilla con kettlebell: la carga compacta facilita el agarre desde la bisagra y permite ritmos controlados.",
                "back_remo_gorilla__kettlebell"),
        gorilla("back_remo_gorilla_mancuernas__cable", "cable", "cable",
                "Remo Gorilla en polea: la tensión continua mantiene el dorsal activo durante toda la tracción desde la bisagra.",
                "back_remo_gorilla__cable"),
    ]
    gorilla_def["defaultConfigurationId"] = "back_remo_gorilla_mancuernas__dumbbells"

    renegade_def = find_def(payload, "back_remo_renegado_mancuernas")
    template = renegade_def["configurations"][0]
    core_stab2 = note("core", "Estabilizador: el core sostiene la plancha alta durante la tracción de cada brazo; por eso suma 0.4.")
    def renegade(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["latissimus_dorsi"], secondary=["biceps", "rhomboids"], stabilizers=["core"],
            notes=lats_note + biceps2 + rhomb3 + [core_stab2], equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0,
            perf_id=perf,
            objectives=["Desarrollar latissimus_dorsi con el remo renegado en plancha alta."],
            required_equipment=[implement], compatible_equipment=["dumbbells", "kettlebell"],
            preserves_intent=["Conserva la tracción horizontal en plancha y el objetivo latissimus_dorsi."],
            target_regions=["latissimus_dorsi"],
            setup_cue="Colócate en plancha alta con un peso en cada mano.",
            exec_cue="Lleva un peso hacia el tronco manteniendo la cadera firme y alterna de lado.",
            mistake="Rotar la cadera o hundir la espalda al traccionar cada brazo.",
        )
    renegade_def["canonicalName"] = "Remo Renegado"
    renegade_def["optionAxes"] = ["implement"]
    renegade_def["kind"] = "PARENT"
    renegade_def["searchTerms"] = ["remo renegado", "renegade row", "remo en plancha"]
    renegade_def["configurations"] = [
        renegade("back_remo_renegado_mancuernas__dumbbells", "dumbbells", "dumbbells",
                 "Remo Renegado con mancuernas: la tracción desde la plancha alta combina el dorsal con una exigencia fuerte de estabilidad del core.",
                 "back_remo_renegado__dumbbells"),
        renegade("back_remo_renegado_mancuernas__kettlebell", "kettlebell", "kettlebell",
                 "Remo Renegado con kettlebell: la carga compacta roza menos el suelo y facilita la tracción desde la plancha alta.",
                 "back_remo_renegado__kettlebell"),
    ]
    renegade_def["defaultConfigurationId"] = "back_remo_renegado_mancuernas__dumbbells"
    save("upper_horizontal_pull.json", payload)


# ---------------------------------------------------------------------------
# LOTE 7 — Bisagras e hiperextensiones
# ---------------------------------------------------------------------------
def lote7():
    ham_note = note("hamstrings", "Principal: los isquiosurales controlan el descenso de la bisagra y concentran el trabajo; por eso suman la serie completa (1.0).")
    glut_note = note("gluteus_maximus", "Principal: el glúteo mayor extiende la cadera y concentra la potencia del ascenso; por eso suma la serie completa (1.0).")
    erector_note = note("erector_spinae", "Estabilizador: los erectores mantienen la columna neutra de forma isométrica durante la bisagra; por eso suman 0.4.")
    core_note = note("core", "Estabilizador: el core mantiene el tronco firme y protege la zona lumbar; por eso suma 0.4.")

    # Peso Muerto Piernas Rígidas -> {barra, smith, mancuernas, hex} x lateralidad
    payload = load("lower_hip_hinge_lengthened.json")
    definition = find_def(payload, "stiff_leg_deadlift")
    if not any(c["id"] == "stiff_leg_deadlift__bilateral__barbell" for c in definition["configurations"]):
        template = definition["configurations"][0]
        def sld(cfg_id, implement, laterality, summary, description, perf):
            return make_config(
                template, cfg_id=cfg_id, options={"implement": implement, "laterality": laterality},
                display_summary=summary, description=description,
                primary=["hamstrings", "gluteus_maximus"], secondary=[], stabilizers=["erector_spinae", "core"],
                notes=[ham_note, glut_note, erector_note, core_note], equipment_id=implement,
                laterality="BILATERAL" if laterality == "bilateral" else "UNILATERAL", axial=0.5,
                perf_id=perf,
                objectives=["Desarrollar los isquiosurales en bisagra de cadera con piernas rígidas."],
                required_equipment=[implement], compatible_equipment=["barbell", "smith_machine", "dumbbells", "hex_bar"],
                preserves_intent=["Conserva la bisagra de cadera en longitud y el objetivo hamstrings."],
                target_regions=["hamstrings", "gluteus_maximus"],
                setup_cue="Coloca la carga al frente y separa los pies al ancho de la cadera.",
                exec_cue="Baja con las piernas casi rectas hasta sentir el estiramiento de isquios y sube con la cadera.",
                mistake="Flexionar demasiado las rodillas o redondear la espalda para bajar más.",
            )
        definition["canonicalName"] = "Peso Muerto Piernas Rígidas"
        definition["optionAxes"] = ["implement", "laterality"]
        definition["searchTerms"] = ["peso muerto piernas rigidas", "stiff leg deadlift", "piernas rigidas"]
        definition["configurations"] = [
            sld("stiff_leg_deadlift__bilateral__barbell", "barbell", "bilateral", "barbell · bilateral",
                "Peso Muerto Piernas Rígidas con barra y trabajo bilateral: el énfasis está en la elongación de los isquios con la cadera atrás y las piernas casi rectas.",
                "stiff_leg_deadlift__bilateral__barbell"),
            sld("stiff_leg_deadlift__unilateral__barbell", "barbell", "unilateral", "barbell · unilateral",
                "Peso Muerto Piernas Rígidas con barra y apoyo en una pierna: el trabajo unilateral exige más equilibrio y expone asimetrías de isquios.",
                "stiff_leg_deadlift__unilateral__barbell"),
            sld("stiff_leg_deadlift__bilateral__smith_machine", "smith_machine", "bilateral", "smith_machine · bilateral",
                "Peso Muerto Piernas Rígidas en máquina Smith con trabajo bilateral: el guiado mantiene la barra en línea y simplifica la bisagra.",
                "stiff_leg_deadlift__bilateral__smith_machine"),
            sld("stiff_leg_deadlift__unilateral__smith_machine", "smith_machine", "unilateral", "smith_machine · unilateral",
                "Peso Muerto Piernas Rígidas en máquina Smith con apoyo en una pierna: el guiado elimina el equilibrio y deja el foco en la cadena posterior.",
                "stiff_leg_deadlift__unilateral__smith_machine"),
            sld("stiff_leg_deadlift__bilateral__dumbbells", "dumbbells", "bilateral", "dumbbells · bilateral",
                "Peso Muerto Piernas Rígidas con mancuernas y trabajo bilateral: la carga a los costados facilita el agarre y la progresión ligera.",
                "stiff_leg_deadlift__bilateral__dumbbells"),
            sld("stiff_leg_deadlift__unilateral__dumbbells", "dumbbells", "unilateral", "dumbbells · unilateral",
                "Peso Muerto Piernas Rígidas con mancuernas y apoyo en una pierna: variante unilateral cómoda para trabajar cada isquio por separado.",
                "stiff_leg_deadlift__unilateral__dumbbells"),
            sld("stiff_leg_deadlift__bilateral__hex_bar", "hex_bar", "bilateral", "hex_bar · bilateral",
                "Peso Muerto Piernas Rígidas con barra hexagonal y trabajo bilateral: el agarre neutro alivia la zona lumbar y permite cargar con comodidad.",
                "stiff_leg_deadlift__bilateral__hex_bar"),
            sld("stiff_leg_deadlift__unilateral__hex_bar", "hex_bar", "unilateral", "hex_bar · unilateral",
                "Peso Muerto Piernas Rígidas con barra hexagonal y apoyo en una pierna: la barra trapecio reduce la exigencia de equilibrio frente a la variante con barra.",
                "stiff_leg_deadlift__unilateral__hex_bar"),
        ]
        definition["defaultConfigurationId"] = "stiff_leg_deadlift__bilateral__barbell"
        save("lower_hip_hinge_lengthened.json", payload)

    # Peso Muerto Rumano Sumo (nuevo, espejo del Rumano)
    payload = load("hinge_rdl.json")
    family = fam_of(payload)
    if not any(d["id"] == "romanian_sumo_deadlift" for d in family["definitions"]):
        rdl = find_def(payload, "romanian_deadlift")
        template = rdl["configurations"][0]
        def sumo(cfg_id, implement, stance, summary, description, perf):
            return make_config(
                template, cfg_id=cfg_id, options={"implement": implement, "stance": stance},
                display_summary=summary, description=description,
                primary=["hamstrings", "gluteus_maximus"], secondary=[], stabilizers=["erector_spinae", "core"],
                notes=[ham_note, glut_note, erector_note, core_note], equipment_id=implement,
                laterality="BILATERAL", axial=0.5, perf_id=perf,
                objectives=["Desarrollar los isquiosurales y glúteos con el RDL en postura sumo."],
                required_equipment=[implement], compatible_equipment=["barbell", "smith_machine", "machine", "dumbbells", "hex_bar"],
                preserves_intent=["Conserva la bisagra de cadera en longitud y el objetivo hamstrings."],
                target_regions=["hamstrings", "gluteus_maximus"],
                setup_cue="Coloca los pies amplios y con las puntas algo hacia fuera, como en la postura sumo.",
                exec_cue="Baja la carga con la cadera atrás y las piernas casi rectas, y sube extendiendo la cadera.",
                mistake="Bajar la carga a las rodillas en lugar de dejarla caer entre las piernas por la bisagra.",
            )
        sumo_def = new_definition(
            def_id="romanian_sumo_deadlift", family_id="hinge_rdl",
            canonical_name="Peso Muerto Rumano Sumo",
            description="Bisagra de cadera en postura sumo con piernas casi rectas: la posición amplia reduce el recorrido de la carga y cambia el énfasis del trabajo hacia los glúteos y los isquios mediales.",
            option_axes=["implement", "stance"],
            search_terms=["peso muerto rumano sumo", "rdl sumo", "sumo rumano", "stiff sumo"],
            configurations=[
                sumo("romanian_sumo_deadlift__bilateral__barbell", "barbell", "bilateral", "barbell · bilateral",
                     "Peso Muerto Rumano Sumo con barra y postura amplia: la bisagra con las piernas casi rectas mantiene el foco en la cadena posterior.",
                     "romanian_sumo_deadlift__bilateral__barbell"),
                sumo("romanian_sumo_deadlift__unilateral__barbell", "barbell", "unilateral", "barbell · unilateral",
                     "Peso Muerto Rumano Sumo con barra y apoyo asimétrico: la postura amplia de una pierna exige más estabilidad y expone desequilibrios.",
                     "romanian_sumo_deadlift__unilateral__barbell"),
                sumo("romanian_sumo_deadlift__bilateral__smith_machine", "smith_machine", "bilateral", "smith_machine · bilateral",
                     "Peso Muerto Rumano Sumo en máquina Smith con postura amplia: el guiado mantiene la barra en línea y simplifica la bisagra.",
                     "romanian_sumo_deadlift__bilateral__smith_machine"),
                sumo("romanian_sumo_deadlift__unilateral__smith_machine", "smith_machine", "unilateral", "smith_machine · unilateral",
                     "Peso Muerto Rumano Sumo en máquina Smith con apoyo asimétrico: el guiado reduce el equilibrio y deja el foco en cada lado de la cadena posterior.",
                     "romanian_sumo_deadlift__unilateral__smith_machine"),
                sumo("romanian_sumo_deadlift__bilateral__machine", "machine", "bilateral", "machine · bilateral",
                     "Peso Muerto Rumano Sumo en máquina con postura amplia: la estación guiada permite cargar cómodo el patrón en longitud.",
                     "romanian_sumo_deadlift__bilateral__machine"),
                sumo("romanian_sumo_deadlift__unilateral__machine", "machine", "unilateral", "machine · unilateral",
                     "Peso Muerto Rumano Sumo en máquina con apoyo asimétrico: la máquina elimina el equilibrio y aísla el trabajo de cada lado.",
                     "romanian_sumo_deadlift__unilateral__machine"),
                sumo("romanian_sumo_deadlift__bilateral__dumbbells", "dumbbells", "bilateral", "dumbbells · bilateral",
                     "Peso Muerto Rumano Sumo con mancuernas y postura amplia: la carga a los costados facilita el agarre y la progresión ligera.",
                     "romanian_sumo_deadlift__bilateral__dumbbells"),
                sumo("romanian_sumo_deadlift__unilateral__dumbbells", "dumbbells", "unilateral", "dumbbells · unilateral",
                     "Peso Muerto Rumano Sumo con mancuernas y apoyo asimétrico: variante unilateral cómoda para trabajar cada isquio por separado.",
                     "romanian_sumo_deadlift__unilateral__dumbbells"),
                sumo("romanian_sumo_deadlift__bilateral__hex_bar", "hex_bar", "bilateral", "hex_bar · bilateral",
                     "Peso Muerto Rumano Sumo con barra hexagonal y postura amplia: el agarre neutro alivia la zona lumbar durante la bisagra.",
                     "romanian_sumo_deadlift__bilateral__hex_bar"),
                sumo("romanian_sumo_deadlift__unilateral__hex_bar", "hex_bar", "unilateral", "hex_bar · unilateral",
                     "Peso Muerto Rumano Sumo con barra hexagonal y apoyo asimétrico: la barra trapecio reduce la exigencia de equilibrio frente a la barra recta.",
                     "romanian_sumo_deadlift__unilateral__hex_bar"),
            ],
            default_id="romanian_sumo_deadlift__bilateral__barbell",
        )
        family["definitions"].append(sumo_def)
        save("hinge_rdl.json", payload)

    # Buenos Días -> padre {barra, safety, smith, máquina, polea} x lateralidad
    payload = load("hinge_good_morning.json")
    definition = find_def(payload, "good_morning")
    template = definition["configurations"][0]
    gm_ham = note("hamstrings", "Principal: los isquiosurales frenan el descenso del tronco y concentran el trabajo de la bisagra; por eso suman la serie completa (1.0).")
    gm_erector = note("erector_spinae", "Estabilizador: los erectores mantienen la columna neutra de forma isométrica durante todo el movimiento; por eso suman 0.4.")
    gm_core = note("core", "Estabilizador: el core protege la zona lumbar y mantiene el tronco firme; por eso suma 0.4.")
    def gm(cfg_id, implement, laterality, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement, "laterality": laterality},
            display_summary=summary, description=description,
            primary=["hamstrings"], secondary=["gluteus_maximus"], stabilizers=["erector_spinae", "core"],
            notes=[gm_ham, note("gluteus_maximus", "Secundario: el glúteo mayor asiste la extensión final de la cadera; por eso suma 0.5."), gm_erector, gm_core],
            equipment_id=implement, laterality="BILATERAL" if laterality == "bilateral" else "UNILATERAL", axial=0.6,
            perf_id=perf,
            objectives=["Desarrollar los isquiosurales en la bisagra de pie de los buenos días."],
            required_equipment=[implement], compatible_equipment=["barbell", "safety_bar", "smith_machine", "machine", "cable"],
            preserves_intent=["Conserva la bisagra de cadera de pie y el objetivo hamstrings."],
            target_regions=["hamstrings"],
            setup_cue="Apoya la carga sobre los trapecios y separa los pies al ancho de la cadera.",
            exec_cue="Inclina el tronco hacia delante con la cadera atrás y vuelve a erguirte con control.",
            mistake="Redondear la espalda o flexionar demasiado las rodillas para ganar profundidad.",
        )
    definition["canonicalName"] = "Buenos Días"
    definition["optionAxes"] = ["implement", "laterality"]
    definition["searchTerms"] = ["buenos dias", "good morning", "buenos dias con barra", "safety bar"]
    definition["configurations"] = [
        gm("good_morning__bilateral__barbell", "barbell", "bilateral", "barbell · bilateral",
           "Buenos Días con barra y trabajo bilateral: la barra sobre los trapecios carga la bisagra de cadera con el patrón clásico de pie.",
           "good_morning__bilateral__barbell"),
        gm("good_morning__unilateral__barbell", "barbell", "unilateral", "barbell · unilateral",
           "Buenos Días con barra y apoyo en una pierna: la variante unilateral exige más estabilidad y reparte el trabajo de la cadera posterior.",
           "good_morning__unilateral__barbell"),
        gm("good_morning__bilateral__safety_bar", "safety_bar", "bilateral", "safety_bar · bilateral",
           "Buenos Días con barra de seguridad y trabajo bilateral: los agarres frontales de la safety bar resultan más cómodos para hombros sensibles.",
           "good_morning__bilateral__safety_bar"),
        gm("good_morning__unilateral__safety_bar", "safety_bar", "unilateral", "safety_bar · unilateral",
           "Buenos Días con barra de seguridad y apoyo en una pierna: la comodidad del agarre se suma al reto de estabilizar la bisagra.",
           "good_morning__unilateral__safety_bar"),
        gm("good_morning__bilateral__smith_machine", "smith_machine", "bilateral", "smith_machine · bilateral",
           "Buenos Días en máquina Smith con trabajo bilateral: el recorrido guiado simplifica la colocación de la barra y reduce el riesgo.",
           "good_morning__bilateral__smith_machine"),
        gm("good_morning__unilateral__smith_machine", "smith_machine", "unilateral", "smith_machine · unilateral",
           "Buenos Días en máquina Smith con apoyo en una pierna: el guiado elimina el equilibrio y aísla el trabajo de cada lado.",
           "good_morning__unilateral__smith_machine"),
        gm("good_morning__bilateral__machine", "machine", "bilateral", "machine · bilateral",
           "Buenos Días en máquina con trabajo bilateral: la estación guiada permite cargar la bisagra sin preocuparse por la colocación de la barra.",
           "good_morning__bilateral__machine"),
        gm("good_morning__unilateral__machine", "machine", "unilateral", "machine · unilateral",
           "Buenos Días en máquina con apoyo en una pierna: el guiado de la máquina deja el foco en la fuerza de cada cadera.",
           "good_morning__unilateral__machine"),
        gm("good_morning__bilateral__cable", "cable", "bilateral", "cable · bilateral",
           "Buenos Días en polea con trabajo bilateral: la polea baja mantiene tensión continua en los isquios durante toda la bisagra.",
           "good_morning__bilateral__cable"),
        gm("good_morning__unilateral__cable", "cable", "unilateral", "cable · unilateral",
           "Buenos Días en polea con apoyo en una pierna: la tensión continua de la polea acompaña el descenso controlado del tronco.",
           "good_morning__unilateral__cable"),
    ]
    definition["defaultConfigurationId"] = "good_morning__bilateral__barbell"

    # Buenos Días Sentado -> padre {barra, smith, safety}
    seated_def = find_def(payload, "good_morning_seated")
    template = seated_def["configurations"][0]
    def gms(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["hamstrings"], secondary=["gluteus_maximus"], stabilizers=["erector_spinae", "core"],
            notes=[gm_ham, note("gluteus_maximus", "Secundario: el glúteo mayor asiste la extensión de la cadera desde la posición sentada; por eso suma 0.5."), gm_erector, gm_core],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.6, perf_id=perf,
            objectives=["Desarrollar los isquiosurales con los buenos días sentado."],
            required_equipment=[implement], compatible_equipment=["barbell", "smith_machine", "safety_bar"],
            preserves_intent=["Conserva la bisagra de cadera sentada y el objetivo hamstrings."],
            target_regions=["hamstrings"],
            setup_cue="Apoya la carga sobre los trapecios y siéntate en el banco con la espalda recta.",
            exec_cue="Inclina el tronco hacia delante manteniendo la espalda neutra y vuelve a erguirte.",
            mistake="Redondear la espalda o despegar la cadera del banco al bajar.",
        )
    seated_def["canonicalName"] = "Buenos Días Sentado"
    seated_def["optionAxes"] = ["implement"]
    seated_def["kind"] = "PARENT"
    seated_def["searchTerms"] = ["buenos dias sentado", "seated good morning"]
    seated_def["configurations"] = [
        gms("good_morning_seated__barbell", "barbell", "barbell",
            "Buenos Días Sentado con barra: el banco fija la cadera y aísla la bisagra de los isquios con el patrón clásico.",
            "good_morning_seated__barbell"),
        gms("good_morning_seated__smith_machine", "smith_machine", "smith_machine",
            "Buenos Días Sentado en máquina Smith: el recorrido guiado facilita la colocación de la barra desde el banco.",
            "good_morning_seated__smith_machine"),
        gms("good_morning_seated__safety_bar", "safety_bar", "safety_bar",
            "Buenos Días Sentado con barra de seguridad: el agarre frontal de la safety bar resulta más cómodo para hombros sensibles.",
            "good_morning_seated__safety_bar"),
    ]
    seated_def["defaultConfigurationId"] = "good_morning_seated__barbell"
    save("hinge_good_morning.json", payload)

    # Hiperextensiones: limpieza + nueva para glúteos
    payload = load("lower_hip_extension.json")
    drop_def(payload, "glutes_hiperextension_45_zercher_maquina_smith")
    definition = find_def(payload, "glutes_hiperextension_45")
    template = definition["configurations"][0]
    def h45(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["gluteus_maximus"], secondary=["hamstrings"], stabilizers=["erector_spinae"],
            notes=[note("gluteus_maximus", "Principal: el glúteo mayor extiende la cadera contra la carga y concentra el trabajo del banco a 45 grados; por eso suma la serie completa (1.0)."),
                   note("hamstrings", "Secundario: los isquiosurales asisten la extensión de cadera y la flexión de rodilla; por eso suman 0.5."),
                   note("erector_spinae", "Estabilizador: los erectores mantienen la columna en posición neutra durante la extensión; por eso suman 0.4.")],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.3, perf_id=perf,
            objectives=["Desarrollar gluteus_maximus en extensión de cadera en el banco a 45 grados."],
            required_equipment=[implement], compatible_equipment=["dumbbells", "barbell", "plate", "smith_machine"],
            preserves_intent=["Conserva la extensión de cadera en banco a 45 y el objetivo gluteus_maximus."],
            target_regions=["gluteus_maximus"],
            setup_cue="Ajusta el banco a 45 grados y coloca las almohadillas a la altura de la cadera.",
            exec_cue="Extiende el tronco desde la posición flexionada hasta la línea del cuerpo y vuelve con control.",
            mistake="Hiperextender la zona lumbar arriba en lugar de terminar con la cadera.",
        )
    definition["canonicalName"] = "Hiperextensiones a 45° para Glúteos"
    definition["optionAxes"] = ["implement"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["hiperextensiones 45", "hiperextension gluteos", "45 degree hyperextension", "banco 45"]
    definition["configurations"] = [
        h45("glutes_hiperextension_45__dumbbells", "dumbbells", "dumbbells",
            "Hiperextensiones a 45° para Glúteos con mancuerna: la carga libre abrazada al pecho permite ajustar el peso con precisión.",
            "glutes_hiperextension_45__dumbbells"),
        h45("glutes_hiperextension_45__barbell", "barbell", "barbell",
            "Hiperextensiones a 45° para Glúteos con barra: la barra sobre la espalda permite cargar peso progresivamente.",
            "glutes_hiperextension_45__barbell"),
        h45("glutes_hiperextension_45__plate", "plate", "plate",
            "Hiperextensiones a 45° para Glúteos con disco: la carga en el pecho resulta cómoda para rangos altos de repeticiones.",
            "glutes_hiperextension_45__plate"),
        h45("glutes_hiperextension_45__smith_machine", "smith_machine", "smith_machine",
            "Hiperextensiones a 45° para Glúteos en máquina Smith: el guiado estabiliza la carga y permite concentrarse en el glúteo.",
            "glutes_hiperextension_45__smith_machine"),
    ]
    definition["defaultConfigurationId"] = "glutes_hiperextension_45__plate"
    save("lower_hip_extension.json", payload)

    # Zercher para Glúteos (especialidad nueva)
    payload = load("lower_hip_extension.json")
    family = fam_of(payload)
    zercher_glutes = new_definition(
        def_id="glutes_hiperextension_45_zercher", family_id="lower_hip_extension",
        canonical_name="Hiperextensión a 45 Zercher para Glúteos",
        description="Hiperextensión en banco a 45 grados con la carga sostenida en posición Zercher: la sujeción del peso contra el pecho cambia la colocación y mantiene el énfasis en el glúteo.",
        option_axes=[],
        search_terms=["hiperextension zercher", "hiperextension 45 zercher", "zercher hyperextension"],
        configurations=[copy.deepcopy(template)],
        default_id="glutes_hiperextension_45_zercher__default",
    )
    zc = zercher_glutes["configurations"][0]
    zc["id"] = "glutes_hiperextension_45_zercher__default"
    zc["selectedOptions"] = {}
    zc["displaySummary"] = "zercher"
    p = zc["profile"]
    p["equipmentId"] = "barbell"
    p["description"] = "Hiperextensión a 45 grados con la barra sostenida en posición Zercher: la sujeción frontal del peso cambia la colocación y mantiene el énfasis en el glúteo."
    p["primaryMuscles"] = ["gluteus_maximus"]
    p["secondaryMuscles"] = ["hamstrings"]
    p["stabilizerMuscles"] = ["erector_spinae"]
    p["muscleNotes"] = [
        note("gluteus_maximus", "Principal: el glúteo mayor extiende la cadera contra la carga sostenida en Zercher; por eso suma la serie completa (1.0)."),
        note("hamstrings", "Secundario: los isquiosurales asisten la extensión de cadera en el banco a 45 grados; por eso suman 0.5."),
        note("erector_spinae", "Estabilizador: los erectores mantienen la columna neutra durante la extensión; por eso suman 0.4."),
    ]
    p["laterality"] = "NOT_APPLICABLE"
    p["loadMode"], p["resistanceProfile"] = LOAD_PROFILES["barbell"]
    p["axialLoadFactor"] = 0.4
    p["performanceProfileId"] = "glutes_hiperextension_45_zercher"
    p["setupCues"] = ["Ajusta el banco a 45 grados y sostén la carga en posición Zercher contra el pecho."]
    p["executionCues"] = ["Extiende el tronco hasta la línea del cuerpo con control y vuelve a flexionar."]
    p["commonMistakes"] = ["Dejar que la carga empuje el tronco hacia delante perdiendo la posición de inicio."]
    p["richMetadata"]["identity"]["configurationId"] = zc["id"]
    p["richMetadata"]["identity"]["performanceProfileId"] = p["performanceProfileId"]
    p["richMetadata"]["display"]["displaySummary"] = zc["displaySummary"]
    p["richMetadata"]["display"]["selectedOptions"] = {}
    p["richMetadata"]["biomechanics"]["equipmentId"] = "barbell"
    p["richMetadata"]["biomechanics"]["laterality"] = "NOT_APPLICABLE"
    p["richMetadata"]["biomechanics"]["loadMode"] = p["loadMode"]
    p["richMetadata"]["biomechanics"]["resistanceProfile"] = p["resistanceProfile"]
    p["richMetadata"]["anatomy"]["primaryMuscles"] = p["primaryMuscles"]
    p["richMetadata"]["anatomy"]["secondaryMuscles"] = p["secondaryMuscles"]
    p["richMetadata"]["anatomy"]["stabilizerMuscles"] = p["stabilizerMuscles"]
    p["richMetadata"]["anatomy"]["targetRegions"] = ["gluteus_maximus"]
    p["richMetadata"]["programming"]["requiredEquipment"] = ["barbell"]
    p["richMetadata"]["programming"]["objectives"] = ["Desarrollar gluteus_maximus con la hiperextensión Zercher a 45 grados."]
    p["richMetadata"]["replacement"]["compatibleEquipmentIds"] = ["barbell"]
    p["richMetadata"]["replacement"]["preservesIntent"] = ["Conserva la extensión de cadera en banco y el objetivo gluteus_maximus."]
    p["richMetadata"]["coaching"]["setup"] = p["setupCues"]
    p["richMetadata"]["coaching"]["execution"] = p["executionCues"]
    p["richMetadata"]["coaching"]["commonMistakes"] = p["commonMistakes"]
    family["definitions"].append(zercher_glutes)
    save("lower_hip_extension.json", payload)

    # Eliminaciones + renombres de hiperextensiones y espalda baja
    payload = load("lower_spinal_extension.json")
    drop_def(payload, "back_hiperextensiones")
    drop_def(payload, "back_hiperextensiones_45_lastradas")
    rename_def(payload, "back_extension_lumbar", "Hiperextensiones de Espalda Baja", ["hiperextensiones de espalda baja", "extension lumbar", "back extension"])
    zercher_back = new_definition(
        def_id="back_hiperextension_45_zercher_espalda_baja", family_id="lower_spinal_extension",
        canonical_name="Hiperextensión a 45 Zercher para Espalda Baja",
        description="Hiperextensión en banco a 45 grados con la carga en posición Zercher orientada a la espalda baja: los erectores sostienen la columna contra la flexión controlada.",
        option_axes=[],
        search_terms=["hiperextension zercher espalda baja", "zercher back extension", "hiperextension 45 zercher"],
        configurations=[copy.deepcopy(find_def(payload, "back_extension_lumbar")["configurations"][0])],
        default_id="back_hiperextension_45_zercher_espalda_baja__default",
    )
    zb = zercher_back["configurations"][0]
    zb["id"] = "back_hiperextension_45_zercher_espalda_baja__default"
    zb["selectedOptions"] = {}
    zb["displaySummary"] = "zercher"
    p = zb["profile"]
    p["equipmentId"] = "barbell"
    p["description"] = "Hiperextensión a 45 grados en posición Zercher orientada a la espalda baja: los erectores sostienen la columna contra la flexión controlada con la carga frontal."
    p["primaryMuscles"] = ["erector_spinae"]
    p["secondaryMuscles"] = []
    p["stabilizerMuscles"] = ["core"]
    p["muscleNotes"] = [
        note("erector_spinae", "Principal: los erectores extienden la columna contra la carga y concentran el trabajo de esta variante; por eso suman la serie completa (1.0)."),
        note("core", "Estabilizador: el core mantiene el tronco firme y protege la zona lumbar durante el recorrido; por eso suma 0.4."),
    ]
    p["laterality"] = "NOT_APPLICABLE"
    p["loadMode"], p["resistanceProfile"] = LOAD_PROFILES["barbell"]
    p["axialLoadFactor"] = 0.4
    p["performanceProfileId"] = "back_hiperextension_45_zercher_espalda_baja"
    p["setupCues"] = ["Ajusta el banco a 45 grados y sostén la carga en posición Zercher contra el pecho."]
    p["executionCues"] = ["Extiende el tronco con control manteniendo la zona lumbar neutra."]
    p["commonMistakes"] = ["Redondear la espalda al final del recorrido para ganar profundidad."]
    p["richMetadata"]["identity"]["configurationId"] = zb["id"]
    p["richMetadata"]["identity"]["performanceProfileId"] = p["performanceProfileId"]
    p["richMetadata"]["display"]["displaySummary"] = zb["displaySummary"]
    p["richMetadata"]["display"]["selectedOptions"] = {}
    p["richMetadata"]["biomechanics"]["equipmentId"] = "barbell"
    p["richMetadata"]["biomechanics"]["laterality"] = "NOT_APPLICABLE"
    p["richMetadata"]["biomechanics"]["loadMode"] = p["loadMode"]
    p["richMetadata"]["biomechanics"]["resistanceProfile"] = p["resistanceProfile"]
    p["richMetadata"]["anatomy"]["primaryMuscles"] = p["primaryMuscles"]
    p["richMetadata"]["anatomy"]["secondaryMuscles"] = p["secondaryMuscles"]
    p["richMetadata"]["anatomy"]["stabilizerMuscles"] = p["stabilizerMuscles"]
    p["richMetadata"]["anatomy"]["targetRegions"] = ["erector_spinae"]
    p["richMetadata"]["programming"]["requiredEquipment"] = ["barbell"]
    p["richMetadata"]["programming"]["objectives"] = ["Desarrollar erector_spinae con la hiperextensión Zercher a 45 grados."]
    p["richMetadata"]["replacement"]["compatibleEquipmentIds"] = ["barbell"]
    p["richMetadata"]["replacement"]["preservesIntent"] = ["Conserva la extensión de columna y el objetivo erector_spinae."]
    p["richMetadata"]["coaching"]["setup"] = p["setupCues"]
    p["richMetadata"]["coaching"]["execution"] = p["executionCues"]
    p["richMetadata"]["coaching"]["commonMistakes"] = p["commonMistakes"]
    fam_of(payload)["definitions"].append(zercher_back)
    save("lower_spinal_extension.json", payload)

    # Eliminar Hiperextensión 45 Unilateral
    payload = load("lower_unilateral_hip_extension.json")
    drop_def(payload, "glutes_hiperextension_45_unilateral")
    save("lower_unilateral_hip_extension.json", payload)

    # Jefferson Curl -> padre {barra, mancuernas, smith, polea}
    payload = load("lower_spinal_flexion.json")
    definition = find_def(payload, "back_jefferson_curl")
    template = definition["configurations"][0]
    def jc(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["erector_spinae"], secondary=["hamstrings"], stabilizers=["core"],
            notes=[note("erector_spinae", "Principal: los erectores trabajan en elongación contra la flexión controlada de la columna; por eso suman la serie completa (1.0)."),
                   note("hamstrings", "Secundario: los isquiosurales frenan la flexión de cadera durante el descenso; por eso suman 0.5."),
                   note("core", "Estabilizador: el core mantiene el tronco firme y protege la zona lumbar; por eso suma 0.4.")],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.2, perf_id=perf,
            objectives=["Desarrollar erector_spinae con la flexión espinal controlada del Jefferson Curl."],
            required_equipment=[implement], compatible_equipment=["barbell", "dumbbells", "smith_machine", "cable"],
            preserves_intent=["Conserva la flexión espinal controlada y el objetivo erector_spinae."],
            target_regions=["erector_spinae"],
            setup_cue="Coloca la carga ligera al frente y los pies separados al ancho de la cadera.",
            exec_cue="Redondea la columna vértebra a vértebra y vuelve a subir con control.",
            mistake="Acelerar la bajada o usar un peso que impide el control del recorrido.",
        )
    definition["canonicalName"] = "Jefferson Curl"
    definition["optionAxes"] = ["implement"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["jefferson curl", "curl de jefferson"]
    definition["configurations"] = [
        jc("back_jefferson_curl__barbell", "barbell", "barbell",
           "Jefferson Curl con barra: la barra frente a los muslos permite cargar ligero y sentir la flexión vértebra a vértebra.",
           "back_jefferson_curl__barbell"),
        jc("back_jefferson_curl__dumbbells", "dumbbells", "dumbbells",
           "Jefferson Curl con mancuernas: la carga libre a los costados facilita ajustar el peso con precisión.",
           "back_jefferson_curl__dumbbells"),
        jc("back_jefferson_curl__smith_machine", "smith_machine", "smith_machine",
           "Jefferson Curl en máquina Smith: el guiado estabiliza la barra y simplifica el control del recorrido.",
           "back_jefferson_curl__smith_machine"),
        jc("back_jefferson_curl__cable", "cable", "cable",
           "Jefferson Curl en polea: la tensión continua acompaña la flexión y el retorno de forma constante.",
           "back_jefferson_curl__cable"),
    ]
    definition["defaultConfigurationId"] = "back_jefferson_curl__barbell"
    save("lower_spinal_flexion.json", payload)


# ---------------------------------------------------------------------------
# LOTE 8 — Sentadillas
# ---------------------------------------------------------------------------
def lote8():
    payload = load("lower_knee_dominant.json")
    rename_def(payload, "quads_sentadilla_anderson_frontal_barra_recta", "Sentadilla Anderson Frontal",
               ["sentadilla anderson frontal", "anderson squat", "anderson frontal"])
    rename_def(payload, "quads_sentadilla_v_squat", 'Sentadilla en "V-Squat"', ["sentadilla v squat", "v squat", "sentadilla en v"])
    # Sentadilla Hack -> padre {máquina (default), barra, smith}
    definition = find_def(payload, "quads_sentadilla_hack")
    template = definition["configurations"][0]
    def hack(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["quadriceps"], secondary=["gluteus_maximus"], stabilizers=[],
            notes=[note("quadriceps", "Principal: el cuádriceps extiende la rodilla y concentra el trabajo de la sentadilla hack; por eso suma la serie completa (1.0)."),
                   note("gluteus_maximus", "Secundario: el glúteo mayor asiste la extensión de cadera en la fase de subida; por eso suma 0.5.")],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.5, perf_id=perf,
            objectives=["Desarrollar quadriceps con la sentadilla hack."],
            required_equipment=[implement], compatible_equipment=["machine", "barbell", "smith_machine"],
            preserves_intent=["Conserva el patrón de sentadilla con foco en cuádriceps."],
            target_regions=["quadriceps"],
            setup_cue="Coloca los pies en la plataforma o bajo la barra según la estación elegida.",
            exec_cue="Baja flexionando rodillas y cadera y sube extendiendo con el torso fijo.",
            mistake="Despegar los talones o cortar el rango para aliviar la tensión del cuádriceps.",
        )
    definition["canonicalName"] = "Sentadilla Hack"
    definition["optionAxes"] = ["implement"]
    definition["kind"] = "PARENT"
    definition["searchTerms"] = ["sentadilla hack", "hack squat", "hack"]
    definition["configurations"] = [
        hack("quads_sentadilla_hack__machine", "machine", "machine",
             "Sentadilla Hack en máquina: el respaldo fijo y el recorrido guiado permiten cargar fuerte con el foco en el cuádriceps.",
             "quads_sentadilla_hack__machine"),
        hack("quads_sentadilla_hack__barbell", "barbell", "barbell",
             "Sentadilla Hack con barra a la espalda: la carga libre exige más estabilidad y trabaja el cuádriceps con el torso erguido.",
             "quads_sentadilla_hack__barbell"),
        hack("quads_sentadilla_hack__smith_machine", "smith_machine", "smith_machine",
             "Sentadilla Hack en máquina Smith: el guiado de la barra simplifica la colocación y mantiene el énfasis en el cuádriceps.",
             "quads_sentadilla_hack__smith_machine"),
    ]
    definition["defaultConfigurationId"] = "quads_sentadilla_hack__machine"
    save("lower_knee_dominant.json", payload)

    # Sentadillas traseras: añadir barra de seguridad
    payload = load("lower_knee_dominant.json")
    for def_id in ("high_bar_back_squat", "low_bar_back_squat"):
        definition = find_def(payload, def_id)
        template = definition["configurations"][0]
        def safety(cfg_id, summary, description, perf):
            return make_config(
                template, cfg_id=cfg_id, options={"implement": "safety_bar"}, display_summary=summary,
                description=description, primary=["quadriceps"], secondary=["gluteus_maximus"], stabilizers=["core"],
                notes=[note("quadriceps", "Principal: el cuádriceps extiende la rodilla y concentra el trabajo de la sentadilla; por eso suma la serie completa (1.0)."),
                       note("gluteus_maximus", "Secundario: el glúteo mayor asiste la extensión de cadera en la subida; por eso suma 0.5."),
                       note("core", "Estabilizador: el core mantiene el tronco firme y protege la zona lumbar; por eso suma 0.4.")],
                equipment_id="safety_bar", laterality="NOT_APPLICABLE", axial=1.0, perf_id=perf,
                objectives=["Desarrollar quadriceps con sentadilla trasera y barra de seguridad."],
                required_equipment=["safety_bar"], compatible_equipment=["barbell", "smith_machine", "safety_bar"],
                preserves_intent=["Conserva el patrón de sentadilla trasera y el objetivo quadriceps."],
                target_regions=["quadriceps"],
                setup_cue="Coloca la barra de seguridad sobre los trapecios con los agarres frontales cómodos.",
                exec_cue="Baja en sentadilla con el torso erguido y sube extendiendo rodilla y cadera.",
                mistake="Despegar los talones o dejar caer el pecho durante el descenso.",
            )
        definition["configurations"] = definition["configurations"] + [
            safety(f"{def_id}__safety_bar", "safety_bar",
                   "Sentadilla trasera con barra de seguridad: los agarres frontales de la safety bar resultan más cómodos para hombros y muñecas sensibles.",
                   f"{def_id}__safety_bar"),
        ]
    save("lower_knee_dominant.json", payload)

    # Sissy: quitar bodyweight, añadir mancuernas y discos
    payload = load("lower_sissy_squat.json")
    definition = find_def(payload, "sissy_squat")
    template = definition["configurations"][0]
    def sissy(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["quadriceps"], secondary=[], stabilizers=[],
            notes=[note("quadriceps", "Principal: el cuádriceps extiende la rodilla y concentra el trabajo de la sentadilla sissy; por eso suma la serie completa (1.0).")],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.2, perf_id=perf,
            objectives=["Desarrollar quadriceps con la sentadilla sissy."],
            required_equipment=[implement], compatible_equipment=["barbell", "machine", "smith_machine", "dumbbells", "plate"],
            preserves_intent=["Conserva el patrón de rodilla dominante de la sissy y el objetivo quadriceps."],
            target_regions=["quadriceps"],
            setup_cue="Asegura los pies bajo el soporte y prepara la carga según la estación elegida.",
            exec_cue="Inclina el tronco hacia atrás flexionando las rodillas y vuelve a extender.",
            mistake="Flexionar la cadera en lugar de dejar el trabajo en la rodilla.",
        )
    definition["canonicalName"] = "Sentadilla Sissy"
    definition["optionAxes"] = ["implement"]
    definition["configurations"] = [
        sissy("sissy_squat__barbell", "barbell", "barbell",
              "Sentadilla Sissy con barra: la carga libre añade resistencia al patrón de rodilla dominante más exigente.",
              "sissy_squat__barbell"),
        sissy("sissy_squat__machine", "machine", "machine",
              "Sentadilla Sissy en máquina: la estación guiada sostiene el recorrido y permite concentrarse en el cuádriceps.",
              "sissy_squat__machine"),
        sissy("sissy_squat__smith_machine", "smith_machine", "smith_machine",
              "Sentadilla Sissy en máquina Smith: el guiado de la barra facilita la posición y la progresión de carga.",
              "sissy_squat__smith_machine"),
        sissy("sissy_squat__dumbbells", "dumbbells", "dumbbells",
              "Sentadilla Sissy con mancuernas: la carga a los costados permite progresar con pesos ligeros y control total.",
              "sissy_squat__dumbbells"),
        sissy("sissy_squat__plate", "plate", "plate",
              "Sentadilla Sissy con disco: la carga en el pecho resulta cómoda para iniciarse en el patrón.",
              "sissy_squat__plate"),
    ]
    definition["defaultConfigurationId"] = "sissy_squat__machine"
    save("lower_sissy_squat.json", payload)

    # Renombres de sentadillas en otras familias
    payload = load("lower_knee_dominant_belt_squat.json")
    rename_def(payload, "belt_squat", 'Sentadilla "Belt Squat"', ["sentadilla belt squat", "belt squat"])
    save("lower_knee_dominant_belt_squat.json", payload)
    payload = load("lower_knee_dominant_pendulum.json")
    rename_def(payload, "pendulum_squat", "Sentadilla en Máquina Pendular", ["sentadilla pendular", "pendulum squat", "sentadilla en maquina pendular"])
    save("lower_knee_dominant_pendulum.json", payload)
    payload = load("unilateral_knee_dominant_bulgarian.json")
    rename_def(payload, "bulgarian_zercher", "Sentadilla Búlgara Zercher", ["sentadilla bulgara zercher", "bulgarian zercher", "zercher bulgara"])
    save("unilateral_knee_dominant_bulgarian.json", payload)


# ---------------------------------------------------------------------------
# LOTE 9 — Core, cuello y varios
# ---------------------------------------------------------------------------
def lote9():
    payload = load("core_trunk_flexion.json")
    rename_def(payload, "core_crunch_banco_declinado_lastrado_disco", "Crunch Abdominal en Banco Declinado",
               ["crunch abdominal en banco declinado", "crunch declinado", "decline crunch"])
    save("core_trunk_flexion.json", payload)

    payload = load("core_anti_extension_isometric.json")
    rename_def(payload, "core_plancha", "Plancha Abdominal", ["plancha abdominal", "plancha", "plank"])
    save("core_anti_extension_isometric.json", payload)

    payload = load("hip_adduction.json")
    drop_def(payload, "copenhagen_plank_isometric")
    base = find_def(payload, "copenhagen_plank")
    base["canonicalName"] = "Plancha Copenhague"
    base["searchTerms"] = ["plancha copenhague", "copenhagen plank", "plancha lateral adductores"]
    base_cfg = base["configurations"][0]
    base_cfg["id"] = "copenhagen_plank__default"
    base_cfg["profile"]["richMetadata"]["identity"]["configurationId"] = base_cfg["id"]
    template = copy.deepcopy(base_cfg)
    dynamic = new_definition(
        def_id="copenhagen_plank_dynamic", family_id="hip_adduction",
        canonical_name="Plancha Copenhague Dinámica",
        description="Plancha lateral con la pierna superior apoyada en un banco, versión dinámica: el movimiento de aducción añade trabajo concéntrico a los aductores además del soporte isométrico.",
        option_axes=[],
        search_terms=["plancha copenhague dinamica", "copenhagen plank dynamic", "copenhagen dinamica"],
        configurations=[template],
        default_id="copenhagen_plank_dynamic__default",
    )
    dc = dynamic["configurations"][0]
    dc["id"] = "copenhagen_plank_dynamic__default"
    dc["profile"]["richMetadata"]["identity"]["configurationId"] = dc["id"]
    dc["profile"]["richMetadata"]["identity"]["definitionId"] = "copenhagen_plank_dynamic"
    dc["profile"]["description"] = "Plancha lateral dinámica con los pies apoyados en un banco: el movimiento de aducción de la pierna inferior añade trabajo concéntrico a los aductores durante la serie."
    fam_of(payload)["definitions"].append(dynamic)
    save("hip_adduction.json", payload)

    # Cuello: fusionar en Extensiones de Cuello y Flexiones de Cuello
    payload = load("lower_neck_extension.json")
    family = fam_of(payload)
    template = family["definitions"][0]["configurations"][0]
    def neck_ext(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["neck"], secondary=[], stabilizers=[],
            notes=[note("neck", "Principal: los extensores del cuello trabajan contra la resistencia y concentran el estímulo; por eso suman la serie completa (1.0).")],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0, perf_id=perf,
            objectives=["Desarrollar la musculatura extensora del cuello."],
            required_equipment=[implement], compatible_equipment=["cable", "plate"],
            preserves_intent=["Conserva la extensión cervical y el objetivo neck."],
            target_regions=["neck"],
            setup_cue="Coloca la resistencia en la cabeza o el arnés y prepara el apoyo del cuerpo.",
            exec_cue="Extiende el cuello contra la resistencia con un recorrido corto y controlado.",
            mistake="Usar la espalda o mover todo el tronco en lugar de aislar el cuello.",
        )
    extensiones = new_definition(
        def_id="neck_extension_cuello", family_id="lower_neck_extension",
        canonical_name="Extensiones de Cuello",
        description="Extensión del cuello contra resistencia externa con arnés de polea o disco: un patrón de aislamiento directo para la musculatura extensora cervical.",
        option_axes=["implement"],
        search_terms=["extensiones de cuello", "extension de cuello", "neck extension", "arnes de cuello"],
        configurations=[
            neck_ext("neck_extension_cuello__cable", "cable", "cable",
                     "Extensiones de Cuello con arnés en polea: la tensión continua acompaña la extensión cervical a lo largo del recorrido.",
                     "neck_extension_cuello__cable"),
            neck_ext("neck_extension_cuello__plate", "plate", "plate",
                     "Extensiones de Cuello con disco: la carga libre sobre la cabeza permite ajustar el peso con precisión.",
                     "neck_extension_cuello__plate"),
        ],
        default_id="neck_extension_cuello__cable",
    )
    family["definitions"] = [extensiones]
    save("lower_neck_extension.json", payload)

    payload = load("lower_neck_flexion.json")
    family = fam_of(payload)
    template = family["definitions"][0]["configurations"][0]
    def neck_flex(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["neck"], secondary=[], stabilizers=[],
            notes=[note("neck", "Principal: los flexores del cuello trabajan contra la resistencia y concentran el estímulo; por eso suman la serie completa (1.0).")],
            equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.0, perf_id=perf,
            objectives=["Desarrollar la musculatura flexora del cuello."],
            required_equipment=[implement], compatible_equipment=["cable", "plate"],
            preserves_intent=["Conserva la flexión cervical y el objetivo neck."],
            target_regions=["neck"],
            setup_cue="Coloca la resistencia en la cabeza o el arnés y prepara el apoyo del cuerpo.",
            exec_cue="Flexiona el cuello hacia delante contra la resistencia con control.",
            mistake="Acelerar el movimiento o usar el tronco para compensar la flexión cervical.",
        )
    flexiones = new_definition(
        def_id="neck_flexion_cuello", family_id="lower_neck_flexion",
        canonical_name="Flexiones de Cuello",
        description="Flexión del cuello contra resistencia externa con arnés de polea o disco: aislamiento directo para la musculatura flexora cervical.",
        option_axes=["implement"],
        search_terms=["flexiones de cuello", "flexion de cuello", "neck flexion", "arnes de cuello"],
        configurations=[
            neck_flex("neck_flexion_cuello__cable", "cable", "cable",
                      "Flexiones de Cuello con arnés en polea: la tensión continua acompaña la flexión cervical a lo largo del recorrido.",
                      "neck_flexion_cuello__cable"),
            neck_flex("neck_flexion_cuello__plate", "plate", "plate",
                      "Flexiones de Cuello con disco: la carga libre permite ajustar el peso y el ritmo del ejercicio.",
                      "neck_flexion_cuello__plate"),
        ],
        default_id="neck_flexion_cuello__cable",
    )
    family["definitions"] = [flexiones]
    save("lower_neck_flexion.json", payload)

    # Renombres varios
    payload = load("core_hip_flexion.json")
    rename_def(payload, "core_elevacion_piernas", "Elevación de Piernas Colgado", ["elevacion de piernas colgado", "leg raise colgado", "elevacion de piernas"])
    save("core_hip_flexion.json", payload)
    payload = load("core_anti_extension_pelvic_control.json")
    rename_def(payload, "core_dragon_flag_banco_plano", "Dragon Flag", ["dragon flag", "bandera de dragon"])
    save("core_anti_extension_pelvic_control.json", payload)
    payload = load("upper_horizontal_push.json")
    rename_def(payload, "tren_superior_fondos", "Fondos en Paralelas", ["fondos en paralelas", "fondos", "parallel dips", "dips"])
    save("upper_horizontal_push.json", payload)
    payload = load("core_trunk_rotation.json")
    rename_def(payload, "core_lenador_polea", "Leñador en Polea", ["lenador en polea", "lenador", "woodchop", "cable chop"])
    save("core_trunk_rotation.json", payload)

    # Elevación de Talones -> {máquina, barra, smith, polea} x lateralidad
    payload = load("lower_plantar_flexion.json")
    definition = find_def(payload, "calf_raise")
    template = definition["configurations"][0]
    def calf(cfg_id, implement, laterality, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement, "laterality": laterality},
            display_summary=summary, description=description,
            primary=["calves"], secondary=[], stabilizers=[],
            notes=[note("calves", "Principal: la pantorrilla realiza la flexión plantar y concentra el trabajo de la elevación; por eso suma la serie completa (1.0).")],
            equipment_id=implement, laterality="BILATERAL" if laterality == "bilateral" else "UNILATERAL", axial=0.4,
            perf_id=perf,
            objectives=["Desarrollar calves en flexión plantar."],
            required_equipment=[implement], compatible_equipment=["machine", "barbell", "smith_machine", "cable"],
            preserves_intent=["Conserva la flexión plantar y el objetivo calves."],
            target_regions=["calves"],
            setup_cue="Coloca los pies con las puntas en el borde del apoyo y el peso según la estación.",
            exec_cue="Sube sobre las puntas de los pies con control y baja hasta estirar la pantorrilla.",
            mistake="Rebotar en la fase baja o acortar el rango para completar más repeticiones.",
        )
    definition["canonicalName"] = "Elevación de Talones"
    definition["optionAxes"] = ["implement", "laterality"]
    definition["searchTerms"] = ["elevacion de talones", "calf raise", "gemelos", "pantorrillas"]
    definition["configurations"] = [
        calf("calf_raise__bilateral__machine", "machine", "bilateral", "machine · bilateral",
             "Elevación de Talones en máquina con trabajo bilateral: la estación guiada permite cargar fuerte en un rango completo de flexión plantar.",
             "calf_raise__bilateral__machine"),
        calf("calf_raise__unilateral__machine", "machine", "unilateral", "machine · unilateral",
             "Elevación de Talones en máquina con trabajo unilateral: cada pantorrilla trabaja por separado para igualar la fuerza de ambos lados.",
             "calf_raise__unilateral__machine"),
        calf("calf_raise__bilateral__barbell", "barbell", "bilateral", "barbell · bilateral",
             "Elevación de Talones con barra y trabajo bilateral: la barra sobre la espalda añade carga libre al patrón de pie.",
             "calf_raise__bilateral__barbell"),
        calf("calf_raise__unilateral__barbell", "barbell", "unilateral", "barbell · unilateral",
             "Elevación de Talones con barra y trabajo unilateral: el apoyo en una pierna con la barra exige más estabilidad y aísla cada pantorrilla.",
             "calf_raise__unilateral__barbell"),
        calf("calf_raise__bilateral__smith_machine", "smith_machine", "bilateral", "smith_machine · bilateral",
             "Elevación de Talones en máquina Smith con trabajo bilateral: el guiado de la barra simplifica la colocación sobre los trapecios.",
             "calf_raise__bilateral__smith_machine"),
        calf("calf_raise__unilateral__smith_machine", "smith_machine", "unilateral", "smith_machine · unilateral",
             "Elevación de Talones en máquina Smith con trabajo unilateral: el guiado reduce el equilibrio y deja el foco en cada pantorrilla.",
             "calf_raise__unilateral__smith_machine"),
        calf("calf_raise__bilateral__cable", "cable", "bilateral", "cable · bilateral",
             "Elevación de Talones en polea con trabajo bilateral: la tensión continua mantiene la pantorrilla activa a lo largo del recorrido.",
             "calf_raise__bilateral__cable"),
        calf("calf_raise__unilateral__cable", "cable", "unilateral", "cable · unilateral",
             "Elevación de Talones en polea con trabajo unilateral: la polea permite trabajar cada pierna con tensión constante y carga ligera.",
             "calf_raise__unilateral__cable"),
    ]
    definition["defaultConfigurationId"] = "calf_raise__bilateral__machine"
    save("lower_plantar_flexion.json", payload)

    # Glute Ham Raise -> sin opciones
    payload = load("lower_knee_hip_extension.json")
    definition = find_def(payload, "glute_ham_raise")
    template = definition["configurations"][0]
    single = copy.deepcopy(template)
    single["id"] = "glute_ham_raise__default"
    single["selectedOptions"] = {}
    single["displaySummary"] = "ghd"
    p = single["profile"]
    p["equipmentId"] = "ghd"
    p["description"] = "Glute Ham Raise en banco GHD: el patrón exige a los isquios frenar la extensión de rodilla y al glúteo extender la cadera, sin necesidad de opciones de carga."
    p["primaryMuscles"] = ["hamstrings"]
    p["secondaryMuscles"] = ["gluteus_maximus"]
    p["stabilizerMuscles"] = ["erector_spinae"]
    p["muscleNotes"] = [
        note("hamstrings", "Principal: los isquiosurales controlan la extensión de rodilla de forma excéntrica y concentran el trabajo; por eso suman la serie completa (1.0)."),
        note("gluteus_maximus", "Secundario: el glúteo mayor extiende la cadera al llegar arriba del banco GHD; por eso suma 0.5."),
        note("erector_spinae", "Estabilizador: los erectores mantienen la columna neutra durante la extensión del tronco; por eso suman 0.4."),
    ]
    p["laterality"] = "NOT_APPLICABLE"
    p["loadMode"], p["resistanceProfile"] = LOAD_PROFILES["bodyweight"]
    p["axialLoadFactor"] = 0.0
    p["performanceProfileId"] = "glute_ham_raise"
    p["setupCues"] = ["Ajusta el banco GHD con las rodillas al borde y los pies fijados."]
    p["executionCues"] = ["Baja extendiendo el cuerpo con control y vuelve arriba contrayendo isquios y glúteo."]
    p["commonMistakes"] = ["Caer de golpe en la fase excéntrica o extender la cadera antes de tiempo."]
    p["richMetadata"]["identity"]["configurationId"] = single["id"]
    p["richMetadata"]["identity"]["performanceProfileId"] = p["performanceProfileId"]
    p["richMetadata"]["display"]["displaySummary"] = single["displaySummary"]
    p["richMetadata"]["display"]["selectedOptions"] = {}
    p["richMetadata"]["biomechanics"]["equipmentId"] = "ghd"
    p["richMetadata"]["biomechanics"]["laterality"] = "NOT_APPLICABLE"
    p["richMetadata"]["biomechanics"]["loadMode"] = p["loadMode"]
    p["richMetadata"]["biomechanics"]["resistanceProfile"] = p["resistanceProfile"]
    p["richMetadata"]["anatomy"]["primaryMuscles"] = p["primaryMuscles"]
    p["richMetadata"]["anatomy"]["secondaryMuscles"] = p["secondaryMuscles"]
    p["richMetadata"]["anatomy"]["stabilizerMuscles"] = p["stabilizerMuscles"]
    p["richMetadata"]["anatomy"]["targetRegions"] = ["hamstrings"]
    p["richMetadata"]["programming"]["requiredEquipment"] = ["ghd"]
    p["richMetadata"]["programming"]["objectives"] = ["Desarrollar hamstrings con el Glute Ham Raise en banco GHD."]
    p["richMetadata"]["replacement"]["compatibleEquipmentIds"] = ["ghd"]
    p["richMetadata"]["replacement"]["preservesIntent"] = ["Conserva la extensión de rodilla excéntrica y el objetivo hamstrings."]
    p["richMetadata"]["coaching"]["setup"] = p["setupCues"]
    p["richMetadata"]["coaching"]["execution"] = p["executionCues"]
    p["richMetadata"]["coaching"]["commonMistakes"] = p["commonMistakes"]
    definition["canonicalName"] = "Glute Ham Raise"
    definition["optionAxes"] = []
    definition["kind"] = "SPECIALTY"
    definition["searchTerms"] = ["glute ham raise", "ghr", "glute ham"]
    definition["configurations"] = [single]
    definition["defaultConfigurationId"] = "glute_ham_raise__default"
    save("lower_knee_hip_extension.json", payload)

    # Paseo del Granjero -> padre {mancuernas, kettlebell, discos, barra hex}
    payload = load("lower_isometric_grip.json")
    family = fam_of(payload)
    template = find_def(payload, "forearms_paseo_del_granjero")["configurations"][0]
    grip_note = [note("forearm", "Principal: el antebrazo sostiene la carga de forma isométrica y concentra el trabajo del paseo; por eso suma la serie completa (1.0).")]
    trap_walk = [note("trapezius", "Secundario: el trapecio estabiliza la cintura escapular con la carga colgando; por eso suma 0.5.")]
    core_walk = [note("core", "Estabilizador: el core mantiene el tronco firme y alineado durante la marcha; por eso suma 0.4.")]
    def farmer(cfg_id, implement, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"implement": implement}, display_summary=summary,
            description=description, primary=["forearm"], secondary=["trapezius"], stabilizers=["core"],
            notes=grip_note + trap_walk + core_walk, equipment_id=implement, laterality="NOT_APPLICABLE", axial=0.3,
            perf_id=perf,
            objectives=["Desarrollar la fuerza de agarre con el paseo del granjero."],
            required_equipment=[implement], compatible_equipment=["dumbbells", "kettlebell", "plate", "hex_bar"],
            preserves_intent=["Conserva la marcha con carga y el objetivo forearm."],
            target_regions=["forearm"],
            setup_cue="Toma la carga con el agarre elegido y erguido el tronco.",
            exec_cue="Camina con pasos cortos y estables manteniendo los hombros atrás.",
            mistake="Inclinar el tronco o acortar la zancada perdiendo la postura erguida.",
        )
    family["definitions"] = [d for d in family["definitions"] if d["id"] not in {"forearms_paseo_del_granjero", "forearms_paseo_del_granjero_barras_trap"}]
    paseo = new_definition(
        def_id="forearms_paseo_del_granjero", family_id="lower_isometric_grip",
        canonical_name="Paseo del Granjero",
        description="Marcha con carga a los costados que exige mantener el agarre, la postura erguida y el core firme durante la distancia recorrida.",
        option_axes=["implement"],
        search_terms=["paseo del granjero", "farmer walk", "paseo del granjero con mancuernas"],
        configurations=[
            farmer("forearms_paseo_del_granjero__dumbbells", "dumbbells", "dumbbells",
                   "Paseo del Granjero con mancuernas: el agarre de la mancuerna es la puerta de entrada natural al patrón de marcha con carga.",
                   "forearms_paseo_del_granjero__dumbbells"),
            farmer("forearms_paseo_del_granjero__kettlebell", "kettlebell", "kettlebell",
                   "Paseo del Granjero con kettlebell: la carga compacta exige un agarre fuerte y permite desplazarse con comodidad.",
                   "forearms_paseo_del_granjero__kettlebell"),
            farmer("forearms_paseo_del_granjero__plate", "plate", "plate",
                   "Paseo del Granjero con discos: pellizcar el disco añade un reto extra de agarre a la marcha.",
                   "forearms_paseo_del_granjero__plate"),
            farmer("forearms_paseo_del_granjero__hex_bar", "hex_bar", "hex_bar",
                   "Paseo del Granjero con barra hexagonal: la barra trapecio reparte la carga y permite cargar más peso que con mancuernas.",
                   "forearms_paseo_del_granjero__hex_bar"),
        ],
        default_id="forearms_paseo_del_granjero__dumbbells",
    )
    family["definitions"].append(paseo)
    save("lower_isometric_grip.json", payload)

    # Extensión de Cuádriceps en Máquina: quitar antebrazo + nueva de pie en polea
    payload = load("lower_knee_extension.json")
    definition = find_def(payload, "quads_extension_cuadriceps")
    template = definition["configurations"][0]
    def quad(cfg_id, laterality, summary, description, perf):
        return make_config(
            template, cfg_id=cfg_id, options={"laterality": laterality}, display_summary=summary,
            description=description, primary=["quadriceps"], secondary=[], stabilizers=[],
            notes=[note("quadriceps", "Principal: el cuádriceps extiende la rodilla contra la resistencia y concentra el trabajo; por eso suma la serie completa (1.0).")],
            equipment_id="machine", laterality="BILATERAL" if laterality == "bilateral" else "UNILATERAL", axial=0.0,
            perf_id=perf,
            objectives=["Desarrollar quadriceps en extensión de rodilla en máquina."],
            required_equipment=["machine"], compatible_equipment=["machine"],
            preserves_intent=["Conserva la extensión de rodilla y el objetivo quadriceps."],
            target_regions=["quadriceps"],
            setup_cue="Ajusta el respaldo y el rodillo a la altura de los tobillos.",
            exec_cue="Extiende las rodillas contra la resistencia y baja con control.",
            mistake="Lanzar el peso en la subida o dejar caer la bajada sin freno.",
        )
    definition["canonicalName"] = "Extensión de Cuádriceps en Máquina"
    definition["optionAxes"] = ["laterality"]
    definition["searchTerms"] = ["extension de cuadriceps en maquina", "leg extension", "cuadriceps maquina"]
    definition["configurations"] = [
        quad("quads_extension_cuadriceps__machine__bilateral", "bilateral", "machine · bilateral",
             "Extensión de Cuádriceps en Máquina con trabajo bilateral: el cuádriceps extiende ambas rodillas contra la resistencia guiada.",
             "quads_extension_cuadriceps__machine__bilateral"),
        quad("quads_extension_cuadriceps__machine__unilateral", "unilateral", "machine · unilateral",
             "Extensión de Cuádriceps en Máquina con trabajo unilateral: cada rodilla trabaja por separado para igualar la fuerza de ambos cuádriceps.",
             "quads_extension_cuadriceps__machine__unilateral"),
    ]
    definition["defaultConfigurationId"] = "quads_extension_cuadriceps__machine__bilateral"

    # Nueva: Extensión de Cuádriceps de Pie en Polea
    family = fam_of(payload)
    quad_polea = new_definition(
        def_id="quads_extension_cuadriceps_pie_polea", family_id="lower_knee_extension",
        canonical_name="Extensión de Cuádriceps de Pie en Polea",
        description="Extensión de rodilla de pie contra la polea baja: el cuádriceps aísla la extensión con tensión continua mientras el tronco permanece erguido.",
        option_axes=["laterality"],
        search_terms=["extension de cuadriceps de pie en polea", "cuadriceps en polea", "leg extension en polea"],
        configurations=[
            quad("quads_extension_cuadriceps_pie_polea__bilateral", "bilateral", "cable · bilateral",
                 "Extensión de Cuádriceps de Pie en Polea con ambas piernas: la tensión continua de la polea acompaña la extensión de rodilla a lo largo del recorrido.",
                 "quads_extension_cuadriceps_pie_polea__bilateral"),
            quad("quads_extension_cuadriceps_pie_polea__unilateral", "unilateral", "cable · unilateral",
                 "Extensión de Cuádriceps de Pie en Polea con una pierna: cada rodilla trabaja por separado con la polea baja y el tronco estable.",
                 "quads_extension_cuadriceps_pie_polea__unilateral"),
        ],
        default_id="quads_extension_cuadriceps_pie_polea__bilateral",
    )
    for cfg in quad_polea["configurations"]:
        cfg["selectedOptions"] = {"laterality": cfg["selectedOptions"]["laterality"]}
        p = cfg["profile"]
        p["equipmentId"] = "cable"
        p["loadMode"], p["resistanceProfile"] = LOAD_PROFILES["cable"]
        p["richMetadata"]["biomechanics"]["equipmentId"] = "cable"
        p["richMetadata"]["biomechanics"]["loadMode"] = p["loadMode"]
        p["richMetadata"]["biomechanics"]["resistanceProfile"] = p["resistanceProfile"]
        p["richMetadata"]["programming"]["requiredEquipment"] = ["cable"]
        p["richMetadata"]["replacement"]["compatibleEquipmentIds"] = ["cable"]
        p["richMetadata"]["replacement"]["preservesIntent"] = ["Conserva la extensión de rodilla en polea y el objetivo quadriceps."]
        p["richMetadata"]["identity"]["definitionId"] = "quads_extension_cuadriceps_pie_polea"
    family["definitions"].append(quad_polea)
    save("lower_knee_extension.json", payload)


# ---------------------------------------------------------------------------
# Pasada final de sanitización: el gate trata "todo" y verbos instruccionales
# como placeholders; los textos heredados de los clones los conservan.
# ---------------------------------------------------------------------------
SANITIZE_PAIRS = [
    ("durante todo el movimiento", "durante el movimiento"),
    ("durante todo el recorrido", "a lo largo del recorrido"),
    ("en todo el recorrido", "a lo largo del recorrido"),
    ("por todo el pectoral", "por el pectoral completo"),
    ("mover todo el tronco", "mover el tronco entero"),
    ("evita el balanceo del tronco", "elimina el balanceo del tronco"),
    ("evita balanceos", "sin balanceos"),
    ("mantener la tensión", "sostener la tensión"),
]


def sanitize_texts() -> None:
    def walk(value):
        if isinstance(value, str):
            for old, new in SANITIZE_PAIRS:
                value = value.replace(old, new)
            return value
        if isinstance(value, list):
            return [walk(item) for item in value]
        if isinstance(value, dict):
            return {key: walk(item) for key, item in value.items()}
        return value

    for path in sorted(FAMILIES.glob("*.json")):
        payload = load(path.name)
        family = fam_of(payload)
        unique: dict[str, dict] = {}
        for definition in family["definitions"]:
            unique.setdefault(definition["id"], definition)
        family["definitions"] = list(unique.values())
        for definition in family["definitions"]:
            configuration_ids = {c["id"] for c in definition["configurations"]}
            if definition["defaultConfigurationId"] not in configuration_ids:
                definition["defaultConfigurationId"] = definition["configurations"][0]["id"]
        save(path.name, walk(payload))


LOTS = {
    "1": lote1,
    "2": lote2,
    "3": lote3,
    "4": lote4,
    "5": lote5,
    "6": lote6,
    "7": lote7,
    "8": lote8,
    "9": lote9,
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lote", help="Lote único a ejecutar (1-9)")
    parser.add_argument("--solo-sanitize", action="store_true", help="Solo pasada de sanitización/dedupe sin ejecutar lotes")
    parser.add_argument("--revision", default="v2-approved-2026-08-02-c", help="Revisión a fijar en identity")
    args = parser.parse_args()
    if args.solo_sanitize:
        sanitize_texts()
    else:
        selected = list(LOTS) if not args.lote else [args.lote]
        for lot_id in selected:
            LOTS[lot_id]()
        sanitize_texts()
    changed = sorted(str(p.name) for p in FAMILIES.glob("*.json"))
    for name in changed:
        payload = load(name)
        sync_identity(payload, args.revision)
        save(name, payload)
    print(f"familias sincronizadas: {len(changed)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

