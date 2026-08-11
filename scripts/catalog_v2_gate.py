#!/usr/bin/env python3
"""Single report for the v2 cutover gates.

Diagnostic mode never mutates the tree. --strict is the CI/cutover gate:
unresolved editorial or legacy conditions fail the command instead of being
hidden behind warnings.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"
BRIEFS = ROOT / "catalog" / "exercises" / "v2" / "curation" / "editorial_briefs.json"
INVENTORY = ROOT / "catalog" / "exercises" / "v2" / "curation" / "candidate_inventory.json"
ANDROID = ROOT / "android-native" / "app" / "src" / "main"
JOINT_ROLES = {"PRIMARY", "SECONDARY", "STABILIZER"}

# Editorial hierarchy is deliberately duplicated in the gate as a review
# contract.  If a generator change silently alphabetizes or flattens these
# axes, the catalog is blocked before it can reach Android/iOS/backend.
EXPECTED_AXIS_ORDER = {
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


def _normalized_first_sentence(value: str) -> str:
    first = re.split(r"(?<=[.!?])\s+", value.strip())[0]
    return re.sub(r"\s+", " ", re.sub(r"[^a-záéíóúüñ0-9 ]", " ", first.casefold())).strip()


def editorial_brief_gate(source: dict, definitions: list[dict], configurations: list[dict]) -> list[str]:
    failures: list[str] = []
    if not BRIEFS.exists():
        return ["editorial_briefs_missing"]
    try:
        brief_source = json.loads(BRIEFS.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [f"editorial_briefs_invalid_json:{exc.lineno}:{exc.colno}"]
    if brief_source.get("schemaVersion") != 1:
        failures.append("editorial_briefs_schema_version")
    if brief_source.get("catalogRevision") != source.get("catalogRevision"):
        failures.append("editorial_briefs_revision_mismatch")
    brief_definitions = brief_source.get("definitions")
    if not isinstance(brief_definitions, dict):
        return failures + ["editorial_briefs_definitions_invalid"]
    definition_ids = {definition["id"] for definition in definitions}
    configuration_ids = {configuration["id"] for configuration in configurations}
    if set(brief_definitions) != definition_ids:
        missing = sorted(definition_ids - set(brief_definitions))
        extra = sorted(set(brief_definitions) - definition_ids)
        if missing:
            failures.append(f"editorial_brief_definitions_missing:{','.join(missing)}")
        if extra:
            failures.append(f"editorial_brief_definitions_extra:{','.join(extra)}")
    profile_fields = ("description", "benefits", "techniqueSummary", "variantRationale", "setupCues", "executionCues")
    for definition in definitions:
        brief = brief_definitions.get(definition["id"])
        if not isinstance(brief, dict):
            continue
        if brief.get("description") != definition.get("description"):
            failures.append(f"editorial_definition_mismatch:{definition['id']}")
        configurations_brief = brief.get("configurations")
        if not isinstance(configurations_brief, dict):
            failures.append(f"editorial_configurations_invalid:{definition['id']}")
            continue
        actual_ids = {configuration["id"] for configuration in definition["configurations"]}
        if set(configurations_brief) != actual_ids:
            failures.append(f"editorial_configuration_inventory_mismatch:{definition['id']}")
        for configuration in definition["configurations"]:
            copy = configurations_brief.get(configuration["id"])
            if not isinstance(copy, dict):
                continue
            profile = configuration.get("profile", {})
            for field in profile_fields:
                if copy.get(field) != profile.get(field):
                    failures.append(f"editorial_field_mismatch:{configuration['id']}:{field}")
    if set(configuration_ids) != {configuration_id for brief in brief_definitions.values() for configuration_id in (brief.get("configurations", {}) if isinstance(brief, dict) else {})}:
        failures.append("editorial_configuration_inventory_global_mismatch")
    forbidden = (
        "llevas las manos hacia el cuerpo",
        "esta configuración aporta",
        "puedes elegir entre",
        "es un ejercicio dentro de un patrón",
    )
    for definition in definitions:
        if any(marker in definition.get("description", "").casefold() for marker in forbidden):
            failures.append(f"editorial_boilerplate_definition:{definition['id']}")
        for configuration in definition["configurations"]:
            description = configuration.get("profile", {}).get("description", "")
            if any(marker in description.casefold() for marker in forbidden):
                failures.append(f"editorial_boilerplate_configuration:{configuration['id']}")
    for label, entries in (
        ("definition", [(definition["id"], definition.get("description", "")) for definition in definitions]),
        ("configuration", [(configuration["id"], configuration.get("profile", {}).get("description", "")) for configuration in configurations]),
    ):
        first_sentences: dict[str, list[str]] = {}
        for identifier, description in entries:
            first_sentences.setdefault(_normalized_first_sentence(description), []).append(identifier)
        for sentence, identifiers in first_sentences.items():
            if sentence and len(identifiers) > 1:
                failures.append(f"duplicate_editorial_opening:{label}:{'|'.join(identifiers)}")
    return failures



def source_gate() -> list[str]:
    failures: list[str] = []
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    definitions = [d for family in source["families"] for d in family["definitions"]]
    configurations = [c for definition in definitions for c in definition["configurations"]]
    failures.extend(editorial_brief_gate(source, definitions, configurations))
    generic_markers = (
        "configuración specialty",
        "configuración parent",
        "identidad técnica",
        "es un ejercicio dentro de un patrón",
        "configuración documentada",
        "el perfil fija la ejecución",
        "no se combinan opciones",
    )
    placeholder_pattern = re.compile(r"(?i)(?<![a-záéíóúüñ])(?:unknown|pendiente|placeholder|n/a)(?![a-záéíóúüñ])")
    if len({d["id"] for d in definitions}) != len(definitions):
        failures.append("duplicate_definition_id")
    if len({c["id"] for c in configurations}) != len(configurations):
        failures.append("duplicate_configuration_id")
    for family in source["families"]:
        if family["evidence"]["reviewStatus"] != "APPROVED":
            failures.append(f"family_not_approved:{family['id']}")
        for definition in family["definitions"]:
            if definition["evidence"]["reviewStatus"] != "APPROVED":
                failures.append(f"definition_not_approved:{definition['id']}")
            if any(marker in definition["description"].lower() for marker in generic_markers):
                failures.append(f"generic_description:{definition['id']}")
            if placeholder_pattern.search(definition["description"]):
                failures.append(f"placeholder_description:{definition['id']}")
            # v7.2: la descripción de definición abre con el nombre del ejercicio
            # a propósito (introducción editorial aprobada). El nombre sigue
            # prohibido en las descripciones de configuración (ver abajo).
            if definition["description"].strip() and not definition["description"].strip()[0].isupper():
                failures.append(f"definition_description_not_capitalized:{definition['id']}")
            axes = definition["optionAxes"]
            if len(axes) != len(dict.fromkeys(axes)):
                failures.append(f"duplicate_axis:{definition['id']}")
            expected_axes = EXPECTED_AXIS_ORDER.get(definition["id"])
            if expected_axes is not None and axes != expected_axes:
                failures.append(f"hierarchy_order:{definition['id']}:{axes}")
            configuration_signatures: set[tuple[tuple[str, str], ...]] = set()
            configuration_ids = {configuration["id"] for configuration in definition["configurations"]}
            configuration_descriptions: set[str] = set()
            if definition["defaultConfigurationId"] not in configuration_ids:
                failures.append(f"invalid_default:{definition['id']}")
            for axis in axes:
                if axis == "pulley_height":
                    continue
                if axis == "implement" and "pulley_height" in axes:
                    # Cable-fixed definition: implement is implicitly cable.
                    continue
                values = {c["selectedOptions"][axis] for c in definition["configurations"]}
                if len(values) == 1:
                    failures.append(f"singleton_axis:{definition['id']}:{axis}")
            for configuration in definition["configurations"]:
                signature = tuple(sorted(configuration["selectedOptions"].items()))
                if signature in configuration_signatures:
                    failures.append(f"duplicate_configuration_options:{configuration['id']}")
                configuration_signatures.add(signature)
                if "pulley_height" in axes:
                    if "implement" in axes:
                        if configuration["selectedOptions"].get("implement") == "cable":
                            if "pulley_height" not in configuration["selectedOptions"]:
                                failures.append(f"missing_pulley_height:{configuration['id']}")
                        elif "pulley_height" in configuration["selectedOptions"]:
                            failures.append(f"forbidden_pulley_height:{configuration['id']}")
                    elif "pulley_height" not in configuration["selectedOptions"]:
                        failures.append(f"missing_pulley_height:{configuration['id']}")
                if any(marker in configuration["displaySummary"].lower() for marker in generic_markers):
                    failures.append(f"generic_display_summary:{configuration['id']}")
                if configuration["evidence"]["reviewStatus"] != "APPROVED":
                    failures.append(f"configuration_not_approved:{configuration['id']}")
                profile = configuration["profile"]
                profile_description = str(profile.get("description") or "")
                configuration_descriptions.add(profile_description.strip())
                if len(profile_description.strip()) < 40:
                    failures.append(f"configuration_description_too_short:{configuration['id']}")
                if re.search(rf"(?i)(?<!\w){re.escape(definition['canonicalName'])}(?!\w)", profile_description):
                    failures.append(f"canonical_name_repeated_in_configuration_description:{configuration['id']}")
                if profile_description.strip() and not profile_description.strip()[0].isupper():
                    failures.append(f"configuration_description_not_capitalized:{configuration['id']}")
                if re.search(r"(?i)\b(?:ejecuta|mantén|mantener|configura|adopta|controla|asegura|evita|sigue|selecciona)\b", profile_description):
                    failures.append(f"instructional_configuration_description:{configuration['id']}")
                if profile.get("catalogRevision") != source.get("catalogRevision"):
                    failures.append(f"profile_revision_mismatch:{configuration['id']}")
                listed = set(profile.get("primaryMuscles", [])) | set(profile.get("secondaryMuscles", [])) | set(profile.get("stabilizerMuscles", []))
                notes = profile.get("muscleNotes") or []
                note_ids = {note.get("muscleId") for note in notes if isinstance(note, dict)}
                if not notes:
                    failures.append(f"missing_muscle_note:{configuration['id']}")
                if listed != note_ids:
                    failures.append(f"muscle_notes_mismatch:{configuration['id']}")
                if len(note_ids) != len(notes):
                    failures.append(f"duplicate_muscle_note:{configuration['id']}")
                for note in notes:
                    if isinstance(note, dict):
                        note_text = str(note.get("note") or "").strip()
                        if len(note_text) < 40:
                            failures.append(f"short_muscle_note:{configuration['id']}:{note.get('muscleId')}")
                        if note_text and not note_text[0].isupper():
                            failures.append(f"muscle_note_not_capitalized:{configuration['id']}:{note.get('muscleId')}")
                benefits = profile.get("benefits")
                if not isinstance(benefits, list) or len(benefits) < 2:
                    failures.append(f"missing_configuration_benefits:{configuration['id']}")
                elif any(not isinstance(benefit, str) or len(benefit.strip()) < 40 for benefit in benefits):
                    failures.append(f"short_configuration_benefit:{configuration['id']}")
                elif any(benefit.strip() and not benefit.strip()[0].isupper() for benefit in benefits):
                    failures.append(f"configuration_benefit_not_capitalized:{configuration['id']}")
                for field in ("techniqueSummary", "variantRationale"):
                    if not isinstance(profile.get(field), str) or len(profile[field].strip()) < 40:
                        failures.append(f"short_configuration_{field}:{configuration['id']}")
                    elif profile[field].strip() and not profile[field].strip()[0].isupper():
                        failures.append(f"configuration_{field}_not_capitalized:{configuration['id']}")
                joints = profile.get("jointInvolvement")
                if not isinstance(joints, list) or not joints:
                    failures.append(f"missing_joint_involvement:{configuration['id']}")
                    joints = []
                joint_ids: list[str] = []
                for joint in joints:
                    if not isinstance(joint, dict):
                        failures.append(f"invalid_joint_involvement:{configuration['id']}")
                        continue
                    joint_id = joint.get("jointId")
                    joint_ids.append(str(joint_id))
                    if not isinstance(joint_id, str) or not joint_id.strip():
                        failures.append(f"blank_joint_id:{configuration['id']}")
                    if joint.get("role") not in JOINT_ROLES:
                        failures.append(f"invalid_joint_role:{configuration['id']}:{joint_id}")
                    actions = joint.get("actions")
                    if not isinstance(actions, list) or not actions or any(not isinstance(action, str) or not action.strip() for action in actions):
                        failures.append(f"invalid_joint_actions:{configuration['id']}:{joint_id}")
                    elif any(action.strip() and not action.strip()[0].isupper() for action in actions):
                        failures.append(f"joint_action_not_capitalized:{configuration['id']}:{joint_id}")
                    if not isinstance(joint.get("note"), str) or len(joint["note"].strip()) < 40:
                        failures.append(f"short_joint_note:{configuration['id']}:{joint_id}")
                    elif joint["note"].strip() and not joint["note"].strip()[0].isupper():
                        failures.append(f"joint_note_not_capitalized:{configuration['id']}:{joint_id}")
                if len(joint_ids) != len(set(joint_ids)):
                    failures.append(f"duplicate_joint_involvement:{configuration['id']}")
                serialized = json.dumps(configuration, ensure_ascii=False)
                if placeholder_pattern.search(serialized):
                    failures.append(f"placeholder_metadata:{configuration['id']}")
                if profile.get("automationEligible") is not True:
                    failures.append(f"automation_ineligible:{configuration['id']}")
                rich = profile.get("richMetadata")
                if not isinstance(rich, dict):
                    failures.append(f"rich_metadata_missing:{configuration['id']}")
                elif rich.get("evidenceConfidence") not in {"MEDIUM", "HIGH"}:
                    failures.append(f"rich_metadata_low_confidence:{configuration['id']}")
                elif not isinstance(rich.get("editorial"), dict):
                    failures.append(f"rich_editorial_missing:{configuration['id']}")
                else:
                    editorial = rich["editorial"]
                    if editorial.get("description") != profile_description:
                        failures.append(f"rich_editorial_description_mismatch:{configuration['id']}")
                    if editorial.get("benefits") != benefits:
                        failures.append(f"rich_editorial_benefits_mismatch:{configuration['id']}")
                    if editorial.get("technique") != profile.get("techniqueSummary"):
                        failures.append(f"rich_editorial_technique_mismatch:{configuration['id']}")
                    if editorial.get("variantRationale") != profile.get("variantRationale"):
                        failures.append(f"rich_editorial_variant_mismatch:{configuration['id']}")
                    anatomy = rich.get("anatomy")
                    if not isinstance(anatomy, dict) or anatomy.get("jointInvolvement") != joints:
                        failures.append(f"rich_anatomy_joint_mismatch:{configuration['id']}")
                    biomechanics = rich.get("biomechanics")
                    relevant_joints = biomechanics.get("relevantJoints") if isinstance(biomechanics, dict) else None
                    if not isinstance(relevant_joints, list) or set(relevant_joints) != set(joint_ids):
                        failures.append(f"rich_biomechanics_joint_mismatch:{configuration['id']}")
            if len(definition["configurations"]) > 1 and len(configuration_descriptions) != len(definition["configurations"]):
                failures.append(f"non_distinct_configuration_descriptions:{definition['id']}")
    return failures


def inventory_gate() -> list[str]:
    if not INVENTORY.exists():
        return ["candidate_inventory_missing"]
    inventory = json.loads(INVENTORY.read_text(encoding="utf-8"))
    failures: list[str] = []
    candidates = inventory.get("candidates")
    if not isinstance(candidates, list) or not candidates:
        return ["candidate_inventory_empty"]
    allowed = {"PARENT", "CONFIGURATION", "SPECIALTY", "DISCARD"}
    for candidate in candidates:
        decision = candidate.get("decision")
        if decision not in allowed:
            failures.append(f"candidate_unresolved:{candidate.get('sourceId', '<missing>')}")
        if decision in allowed and not str(candidate.get("decisionRationale") or "").strip():
            failures.append(f"candidate_missing_rationale:{candidate.get('sourceId', '<missing>')}")
    return failures


def legacy_consumer_gate() -> list[str]:
    failures: list[str] = []
    patterns = {
        "runtime_alias_asset": re.compile(r"exercise_id_aliases\.json|\bEXERCISE_ID_ALIASES\b"),
        "legacy_variant_flow": re.compile(r"VariantFlowResultCache|VariantFlowSheet"),
        "legacy_technical_aspects": re.compile(r"technicalAspects"),
        "direct_global_exercise_map": re.compile(r"\bEXERCISE_DATABASE(?:_BY_ID)?\b"),
        "name_based_catalog_resolution": re.compile(r"\.name\.equals\(exerciseName"),
        # Only the catalog detail surface is prohibited from falling back to
        # placeholder values; unrelated diagnostics/protocol screens may use
        # their own domain-specific N/A notation.
        "placeholder_ui_fallback": re.compile(r"\?:\s*[\"'](?:N/A|unknown|pendiente|TODO)[\"']", re.IGNORECASE),
    }
    for path in ANDROID.rglob("*.kt"):
        if any(part in {"build", ".gradle"} for part in path.parts):
            continue
        text = path.read_text(encoding="utf-8")
        for label, pattern in patterns.items():
            if label == "placeholder_ui_fallback" and path.name != "ExerciseDetailScreen.kt":
                continue
            if pattern.search(text):
                failures.append(f"{label}:{path.relative_to(ROOT)}")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    failures = source_gate() + inventory_gate() + legacy_consumer_gate()
    if failures:
        print(f"status=BLOCKED failures={len(failures)}")
        for failure in failures:
            print(f"- {failure}")
        return 2 if args.strict else 0
    print("status=READY")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
