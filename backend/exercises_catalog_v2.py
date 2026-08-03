"""Strict backend boundary for the shared exercise catalog v2.

The backend never resolves an exercise by visible name.  It accepts a
definition/configuration selection only when the revision and exact
configuration exist in the approved artifact.
"""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_RUNTIME_ASSET = ROOT / "android-native" / "app" / "src" / "main" / "assets" / "exercise_catalog_v2.json"
IOS_RUNTIME_ASSET = ROOT / "ios-native" / "KPKNFit" / "KPKNFit" / "exercise_catalog_v2.json"
EDITORIAL_SOURCE_ASSET = ROOT / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"


class CatalogV2Error(ValueError):
    """Raised for an absent, draft, corrupt, or incompatible catalog."""


class ExerciseSelectionV2(BaseModel):
    model_config = ConfigDict(extra="forbid")

    definitionId: str
    configurationId: str
    catalogRevision: str


def canonical_json_bytes(catalog: dict[str, Any]) -> bytes:
    return (json.dumps(catalog, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def catalog_hash(catalog: dict[str, Any]) -> str:
    return hashlib.sha256(canonical_json_bytes(catalog)).hexdigest()


def load_catalog(path: Path | None = None, *, allow_draft: bool = False) -> dict[str, Any]:
    target = path or DEFAULT_RUNTIME_ASSET
    if not target.exists():
        raise CatalogV2Error(f"catalog_v2_asset_missing:{target}")
    try:
        catalog = json.loads(target.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise CatalogV2Error(f"catalog_v2_asset_invalid:{target}") from exc
    if not isinstance(catalog, dict):
        raise CatalogV2Error("catalog_root_invalid")
    if catalog.get("schemaVersion") != 2:
        raise CatalogV2Error("catalog_schema_version_must_be_2")
    if not catalog.get("catalogRevision") or not catalog.get("ontologyRevision"):
        raise CatalogV2Error("catalog_revision_missing")
    if not allow_draft:
        for family in catalog.get("families", []):
            if family.get("evidence", {}).get("reviewStatus") != "APPROVED":
                raise CatalogV2Error(f"family_not_approved:{family.get('id')}")
            for definition in family.get("definitions", []):
                if definition.get("evidence", {}).get("reviewStatus") != "APPROVED":
                    raise CatalogV2Error(f"definition_not_approved:{definition.get('id')}")
                for configuration in definition.get("configurations", []):
                    if configuration.get("evidence", {}).get("reviewStatus") != "APPROVED":
                        raise CatalogV2Error(f"configuration_not_approved:{configuration.get('id')}")
                    profile = configuration.get("profile", {})
                    if profile.get("automationEligible") is not True or not isinstance(profile.get("richMetadata"), dict):
                        raise CatalogV2Error(f"profile_not_runtime_eligible:{configuration.get('id')}")
        validate_runtime_catalog(catalog)
    return catalog


def _require_non_blank(value: Any, field: str) -> None:
    if not isinstance(value, str) or not value.strip():
        raise CatalogV2Error(f"{field}_blank")


def _require_text_list(value: Any, field: str, *, allow_empty: bool = False) -> None:
    if not isinstance(value, list):
        raise CatalogV2Error(f"{field}_not_list")
    if not allow_empty and not value:
        raise CatalogV2Error(f"{field}_empty")
    if any(not isinstance(item, str) or not item.strip() for item in value):
        raise CatalogV2Error(f"{field}_blank_item")


def _require_range(value: Any, field: str) -> None:
    if not isinstance(value, dict):
        raise CatalogV2Error(f"{field}_not_object")
    minimum = value.get("min")
    maximum = value.get("max")
    if not isinstance(minimum, int) or not isinstance(maximum, int) or minimum < 0 or maximum < minimum:
        raise CatalogV2Error(f"{field}_invalid")

def validate_runtime_catalog(catalog: dict[str, Any]) -> None:
    """Validate the same exact identities the Android loader accepts."""
    families = catalog.get("families")
    if not isinstance(families, list) or not families:
        raise CatalogV2Error("catalog_families_missing")
    definition_ids: set[str] = set()
    configuration_ids: set[str] = set()
    for family in families:
        family_id = family.get("id")
        if not isinstance(family_id, str) or not family_id:
            raise CatalogV2Error("family_id_missing")
        if family.get("evidence", {}).get("reviewStatus") != "APPROVED":
            raise CatalogV2Error(f"family_not_approved:{family_id}")
        definitions = family.get("definitions")
        if not isinstance(definitions, list) or not definitions:
            raise CatalogV2Error(f"family_definitions_missing:{family_id}")
        for definition in definitions:
            definition_id = definition.get("id")
            if not isinstance(definition_id, str) or not definition_id:
                raise CatalogV2Error("definition_id_missing")
            if definition_id in definition_ids:
                raise CatalogV2Error(f"duplicate_definition_id:{definition_id}")
            definition_ids.add(definition_id)
            if definition.get("familyId") != family_id:
                raise CatalogV2Error(f"definition_family_mismatch:{definition_id}")
            if definition.get("evidence", {}).get("reviewStatus") != "APPROVED":
                raise CatalogV2Error(f"definition_not_approved:{definition_id}")
            configurations = definition.get("configurations")
            if not isinstance(configurations, list) or not configurations:
                raise CatalogV2Error(f"definition_configurations_missing:{definition_id}")
            axes = definition.get("optionAxes", [])
            signatures: set[tuple[tuple[str, str], ...]] = set()
            for axis in axes:
                if axis == "pulley_height":
                    continue
                if axis == "implement" and "pulley_height" in axes:
                    # Cable-fixed definition: implement is implicitly cable.
                    continue
                values = {configuration.get("selectedOptions", {}).get(axis) for configuration in configurations}
                if len(values) <= 1:
                    raise CatalogV2Error(f"singleton_option_axis:{definition_id}:{axis}")
            if definition.get("defaultConfigurationId") not in {configuration.get("id") for configuration in configurations}:
                raise CatalogV2Error(f"default_configuration_missing:{definition_id}")
            for configuration in configurations:
                configuration_id = configuration.get("id")
                if not isinstance(configuration_id, str) or not configuration_id:
                    raise CatalogV2Error(f"configuration_id_missing:{definition_id}")
                if configuration_id in configuration_ids:
                    raise CatalogV2Error(f"duplicate_configuration_id:{configuration_id}")
                configuration_ids.add(configuration_id)
                selected_options = configuration.get("selectedOptions")
                if not isinstance(selected_options, dict):
                    raise CatalogV2Error(f"configuration_axes_mismatch:{configuration_id}")
                expected_options = set(axes)
                implement = selected_options.get("implement")
                if "pulley_height" in expected_options:
                    if implement == "cable":
                        if "pulley_height" not in selected_options:
                            raise CatalogV2Error(f"configuration_axes_mismatch:{configuration_id}")
                    else:
                        if "pulley_height" in selected_options:
                            raise CatalogV2Error(f"configuration_axes_mismatch:{configuration_id}")
                        expected_options = expected_options - {"pulley_height"}
                if set(selected_options) != expected_options:
                    raise CatalogV2Error(f"configuration_axes_mismatch:{configuration_id}")
                signature = tuple(sorted((str(key), str(value)) for key, value in selected_options.items()))
                if signature in signatures:
                    raise CatalogV2Error(f"duplicate_configuration_signature:{configuration_id}")
                signatures.add(signature)
                if configuration.get("evidence", {}).get("reviewStatus") != "APPROVED":
                    raise CatalogV2Error(f"configuration_not_approved:{configuration_id}")
                profile = configuration.get("profile")
                if not isinstance(profile, dict) or profile.get("automationEligible") is not True:
                    raise CatalogV2Error(f"profile_not_eligible:{configuration_id}")
                _require_non_blank(profile.get("description"), f"profile_description:{configuration_id}")
                if re.search(r"(?i)\b(?:ejecuta|mantén|mantener|configura|adopta|controla|asegura|evita|sigue|selecciona)\b", profile["description"]):
                    raise CatalogV2Error(f"profile_description_instructional:{configuration_id}")
                notes = profile.get("muscleNotes")
                if not isinstance(notes, list) or not notes:
                    raise CatalogV2Error(f"profile_muscle_notes_missing:{configuration_id}")
                listed = set(profile.get("primaryMuscles", [])) | set(profile.get("secondaryMuscles", [])) | set(profile.get("stabilizerMuscles", []))
                noted: set[str] = set()
                for note in notes:
                    if not isinstance(note, dict) or not isinstance(note.get("muscleId"), str) or not isinstance(note.get("note"), str):
                        raise CatalogV2Error(f"profile_muscle_note_invalid:{configuration_id}")
                    if note["muscleId"] not in listed:
                        raise CatalogV2Error(f"profile_muscle_note_orphan:{configuration_id}:{note['muscleId']}")
                    if note["muscleId"] in noted:
                        raise CatalogV2Error(f"profile_muscle_note_duplicate:{configuration_id}:{note['muscleId']}")
                    if len(note["note"].strip()) < 40:
                        raise CatalogV2Error(f"profile_muscle_note_short:{configuration_id}:{note['muscleId']}")
                    noted.add(note["muscleId"])
                if listed != noted:
                    raise CatalogV2Error(f"profile_muscle_notes_mismatch:{configuration_id}")
                rich = profile.get("richMetadata")
                if not isinstance(rich, dict):
                    raise CatalogV2Error(f"rich_metadata_missing:{configuration_id}")
                if rich.get("evidenceConfidence") not in {"MEDIUM", "HIGH"}:
                    raise CatalogV2Error(f"rich_metadata_confidence:{configuration_id}")
                identity = rich.get("identity")
                if not isinstance(identity, dict):
                    raise CatalogV2Error(f"rich_identity_missing:{configuration_id}")
                expected_identity = {
                    "catalogRevision": catalog["catalogRevision"],
                    "familyId": family_id,
                    "definitionId": definition_id,
                    "configurationId": configuration_id,
                    "canonicalName": definition.get("canonicalName"),
                    "searchTerms": definition.get("searchTerms", []),
                    "kind": definition.get("kind"),
                    "performanceProfileId": profile.get("performanceProfileId"),
                }
                if any(identity.get(key) != value for key, value in expected_identity.items()):
                    raise CatalogV2Error(f"rich_identity_mismatch:{configuration_id}")
                _require_text_list(identity.get("searchTerms"), f"rich_identity_search_terms:{configuration_id}", allow_empty=True)
                display = rich.get("display")
                if not isinstance(display, dict) or display.get("displayName") != definition.get("canonicalName") or display.get("displaySummary") != configuration.get("displaySummary") or display.get("selectedOptions") != selected_options:
                    raise CatalogV2Error(f"rich_display_mismatch:{configuration_id}")
                anatomy = rich.get("anatomy")
                if not isinstance(anatomy, dict) or any(anatomy.get(key) != profile.get(key) for key in ("primaryMuscles", "secondaryMuscles", "stabilizerMuscles")):
                    raise CatalogV2Error(f"rich_anatomy_mismatch:{configuration_id}")
                _require_text_list(anatomy.get("targetRegions"), f"rich_anatomy_target_regions:{configuration_id}")
                _require_text_list(anatomy.get("jointActions"), f"rich_anatomy_joint_actions:{configuration_id}")
                for key in ("muscleLengthBias", "volumeContribution", "stabilizationDemand"):
                    _require_non_blank(anatomy.get(key), f"rich_anatomy_{key}:{configuration_id}")
                biomechanics = rich.get("biomechanics")
                if not isinstance(biomechanics, dict) or any(biomechanics.get(key) != profile.get(key) for key in ("movementPatternId", "bodyRegion", "kineticChain", "laterality", "equipmentId", "loadMode", "resistanceProfile")):
                    raise CatalogV2Error(f"rich_biomechanics_mismatch:{configuration_id}")
                _require_non_blank(biomechanics.get("rangeOfMotion"), f"rich_biomechanics_rom:{configuration_id}")
                _require_non_blank(biomechanics.get("stability"), f"rich_biomechanics_stability:{configuration_id}")
                _require_text_list(biomechanics.get("relevantJoints"), f"rich_biomechanics_joints:{configuration_id}")
                _require_text_list(biomechanics.get("relevantTendons"), f"rich_biomechanics_tendons:{configuration_id}", allow_empty=True)
                fatigue = rich.get("fatigue")
                if not isinstance(fatigue, dict) or any(fatigue.get(key) != profile.get(key) for key in ("efc", "cnc", "ssc", "ttc", "axialLoadFactor", "technicalDifficulty")):
                    raise CatalogV2Error(f"rich_fatigue_mismatch:{configuration_id}")
                for key in ("efc", "cnc", "ssc", "ttc", "axialLoadFactor", "technicalDifficulty"):
                    value = profile.get(key)
                    if not isinstance(value, (int, float)) or value != value or value == float("inf") or value == float("-inf") or value < 0:
                        raise CatalogV2Error(f"profile_metric_invalid:{configuration_id}:{key}")
                if not 1 <= profile.get("technicalDifficulty", 0) <= 10:
                    raise CatalogV2Error(f"profile_technical_difficulty_invalid:{configuration_id}")
                coaching = rich.get("coaching")
                if not isinstance(coaching, dict) or coaching.get("setup") != profile.get("setupCues") or coaching.get("execution") != profile.get("executionCues") or coaching.get("commonMistakes") != profile.get("commonMistakes"):
                    raise CatalogV2Error(f"rich_coaching_mismatch:{configuration_id}")
                _require_text_list(coaching.get("cues"), f"rich_coaching_cues:{configuration_id}")
                _require_text_list(coaching.get("progressions"), f"rich_coaching_progressions:{configuration_id}")
                _require_text_list(coaching.get("regressions"), f"rich_coaching_regressions:{configuration_id}")
                _require_text_list(coaching.get("relevantMobility"), f"rich_coaching_mobility:{configuration_id}", allow_empty=True)
                programming = rich.get("programming")
                if not isinstance(programming, dict):
                    raise CatalogV2Error(f"rich_programming_missing:{configuration_id}")
                _require_non_blank(programming.get("role"), f"rich_programming_role:{configuration_id}")
                _require_text_list(programming.get("objectives"), f"rich_programming_objectives:{configuration_id}")
                _require_text_list(programming.get("suitableRepRanges"), f"rich_programming_rep_ranges:{configuration_id}")
                _require_range(programming.get("indicativeRestSeconds"), f"rich_programming_rest:{configuration_id}")
                _require_non_blank(programming.get("fatigueCost"), f"rich_programming_fatigue:{configuration_id}")
                _require_non_blank(programming.get("recoveryCost"), f"rich_programming_recovery:{configuration_id}")
                _require_text_list(programming.get("requiredEquipment"), f"rich_programming_equipment:{configuration_id}")
                _require_non_blank(programming.get("setupTransitionCost"), f"rich_programming_setup_cost:{configuration_id}")
                _require_text_list(programming.get("splitSuitability"), f"rich_programming_splits:{configuration_id}")
                replacement = rich.get("replacement")
                if not isinstance(replacement, dict):
                    raise CatalogV2Error(f"rich_replacement_missing:{configuration_id}")
                _require_text_list(replacement.get("compatibleEquipmentIds"), f"rich_replacement_equipment:{configuration_id}", allow_empty=True)
                _require_text_list(replacement.get("preservesIntent"), f"rich_replacement_intent:{configuration_id}")
                safety = rich.get("safety")
                if not isinstance(safety, dict):
                    raise CatalogV2Error(f"rich_safety_missing:{configuration_id}")
                _require_text_list(safety.get("risks"), f"rich_safety_risks:{configuration_id}", allow_empty=True)
                _require_text_list(safety.get("precautions"), f"rich_safety_precautions:{configuration_id}", allow_empty=True)
                if not isinstance(safety.get("medicalDisclaimerRequired"), bool):
                    raise CatalogV2Error(f"rich_safety_disclaimer_invalid:{configuration_id}")
def resolve_selection(catalog: dict[str, Any], selection: ExerciseSelectionV2) -> dict[str, Any]:
    revision = catalog.get("catalogRevision")
    if selection.catalogRevision != revision:
        raise CatalogV2Error(f"catalog_revision_mismatch:{selection.catalogRevision}:{revision}")
    for family in catalog.get("families", []):
        for definition in family.get("definitions", []):
            if definition.get("id") != selection.definitionId:
                continue
            for configuration in definition.get("configurations", []):
                if configuration.get("id") == selection.configurationId:
                    return configuration["profile"]
            raise CatalogV2Error(f"unknown_configuration:{selection.definitionId}:{selection.configurationId}")
    raise CatalogV2Error(f"unknown_definition:{selection.definitionId}")


def verify_shared_catalog_artifacts() -> str:
    """Fail closed when the generated Android/iOS artifacts diverge.

    The backend uses the Android runtime asset as its default boundary, but
    CI must prove that the editorial source and both platform artifacts are
    the same logical JSON document before a release can ship.
    """
    source = load_catalog(EDITORIAL_SOURCE_ASSET)
    android = load_catalog(DEFAULT_RUNTIME_ASSET)
    ios = load_catalog(IOS_RUNTIME_ASSET)
    hashes = {
        "source": catalog_hash(source),
        "android": catalog_hash(android),
        "ios": catalog_hash(ios),
    }
    if len(set(hashes.values())) != 1:
        raise CatalogV2Error(f"shared_catalog_hash_mismatch:{hashes}")
    revisions = {source["catalogRevision"], android["catalogRevision"], ios["catalogRevision"]}
    if len(revisions) != 1:
        raise CatalogV2Error(f"shared_catalog_revision_mismatch:{sorted(revisions)}")
    return next(iter(hashes.values()))
