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
JOINT_ROLES = {"PRIMARY", "SECONDARY", "STABILIZER"}


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


# Literal copy of ``scripts/catalog_v2_retired_fields.RETIRED_FIELD_PATHS``: the
# backend is deployed without ``scripts/``. ``scripts/tests/test_catalog_v2_retired_fields.py``
# fails when both copies (or both detectors) diverge.
RETIRED_FIELD_PATHS: tuple[str, ...] = (
    "family.description",
    "family.evidence.rationale",
    "definition.evidence.rationale",
    "configuration.evidence.rationale",
    "profile.benefits",
    "profile.techniqueSummary",
    "profile.variantRationale",
    "profile.commonMistakes",
    "profile.muscleNotes",
    "profile.jointInvolvement[].note",
    "richMetadata.editorial",
    "richMetadata.coaching",
    "richMetadata.safety",
    "richMetadata.anatomy.targetRegions",
    "richMetadata.anatomy.muscleLengthBias",
    "richMetadata.anatomy.stabilizationDemand",
    "richMetadata.anatomy.jointInvolvement[].note",
    "richMetadata.biomechanics.rangeOfMotion",
    "richMetadata.biomechanics.relevantTendons",
    "richMetadata.programming.objectives",
    "richMetadata.programming.suitableRepRanges",
    "richMetadata.programming.recoveryCost",
    "richMetadata.programming.setupTransitionCost",
    "richMetadata.programming.splitSuitability",
)
_RETIRED_PROFILE_KEYS = ("benefits", "techniqueSummary", "variantRationale", "commonMistakes", "muscleNotes")
_RETIRED_RICH_BLOCKS = ("editorial", "coaching", "safety")
_RETIRED_RICH_FIELDS = {
    "anatomy": ("targetRegions", "muscleLengthBias", "stabilizationDemand"),
    "biomechanics": ("rangeOfMotion", "relevantTendons"),
    "programming": ("objectives", "suitableRepRanges", "recoveryCost", "setupTransitionCost", "splitSuitability"),
}


def _joint_note_paths(joints: Any, prefix: str) -> list[str]:
    if not isinstance(joints, list):
        return []
    return [
        f"{prefix}[{index}].note"
        for index, joint in enumerate(joints)
        if isinstance(joint, dict) and "note" in joint
    ]


def find_retired_fields(catalog: dict[str, Any]) -> list[str]:
    """Every retired field still present, prefixed with the owning identifier."""
    found: list[str] = []
    for family in catalog.get("families", []) or []:
        if not isinstance(family, dict):
            continue
        family_id = family.get("id", "<family>")
        if "description" in family:
            found.append(f"{family_id}.description")
        if isinstance(family.get("evidence"), dict) and "rationale" in family["evidence"]:
            found.append(f"{family_id}.evidence.rationale")
        for definition in family.get("definitions", []) or []:
            if not isinstance(definition, dict):
                continue
            definition_id = definition.get("id", "<definition>")
            if isinstance(definition.get("evidence"), dict) and "rationale" in definition["evidence"]:
                found.append(f"{definition_id}.evidence.rationale")
            for configuration in definition.get("configurations", []) or []:
                if not isinstance(configuration, dict):
                    continue
                configuration_id = configuration.get("id", "<configuration>")
                if isinstance(configuration.get("evidence"), dict) and "rationale" in configuration["evidence"]:
                    found.append(f"{configuration_id}.evidence.rationale")
                profile = configuration.get("profile")
                if not isinstance(profile, dict):
                    continue
                found.extend(f"{configuration_id}.profile.{key}" for key in _RETIRED_PROFILE_KEYS if key in profile)
                found.extend(
                    f"{configuration_id}.profile{path}"
                    for path in _joint_note_paths(profile.get("jointInvolvement"), ".jointInvolvement")
                )
                rich = profile.get("richMetadata")
                if not isinstance(rich, dict):
                    continue
                found.extend(f"{configuration_id}.richMetadata.{block}" for block in _RETIRED_RICH_BLOCKS if block in rich)
                for section, keys in _RETIRED_RICH_FIELDS.items():
                    body = rich.get(section)
                    if isinstance(body, dict):
                        found.extend(f"{configuration_id}.richMetadata.{section}.{key}" for key in keys if key in body)
                anatomy = rich.get("anatomy")
                if isinstance(anatomy, dict):
                    found.extend(
                        f"{configuration_id}.richMetadata.anatomy{path}"
                        for path in _joint_note_paths(anatomy.get("jointInvolvement"), ".jointInvolvement")
                    )
    return found


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
    retired = find_retired_fields(catalog)
    if retired:
        raise CatalogV2Error(f"retired_field_present:{retired[0]}")
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
                joint_involvement = profile.get("jointInvolvement")
                if not isinstance(joint_involvement, list) or not joint_involvement:
                    raise CatalogV2Error(f"profile_joint_involvement_missing:{configuration_id}")
                joint_ids: list[str] = []
                for joint in joint_involvement:
                    if not isinstance(joint, dict) or not isinstance(joint.get("jointId"), str) or not joint["jointId"].strip():
                        raise CatalogV2Error(f"profile_joint_involvement_invalid:{configuration_id}")
                    if joint["jointId"] in joint_ids:
                        raise CatalogV2Error(f"profile_joint_involvement_duplicate:{configuration_id}:{joint['jointId']}")
                    if joint.get("role") not in JOINT_ROLES:
                        raise CatalogV2Error(f"profile_joint_role_invalid:{configuration_id}:{joint['jointId']}")
                    if not isinstance(joint.get("actions"), list) or not joint["actions"] or any(not isinstance(action, str) or not action.strip() for action in joint["actions"]):
                        raise CatalogV2Error(f"profile_joint_actions_invalid:{configuration_id}:{joint['jointId']}")
                    joint_ids.append(joint["jointId"])
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
                expected_joint_actions = list(dict.fromkeys(action for joint in joint_involvement for action in joint["actions"]))
                if anatomy.get("jointActions") != expected_joint_actions:
                    raise CatalogV2Error(f"rich_anatomy_joint_actions_not_derived:{configuration_id}")
                if anatomy.get("jointInvolvement") != joint_involvement:
                    raise CatalogV2Error(f"rich_anatomy_joint_involvement_mismatch:{configuration_id}")
                _require_non_blank(anatomy.get("volumeContribution"), f"rich_anatomy_volumeContribution:{configuration_id}")
                biomechanics = rich.get("biomechanics")
                if not isinstance(biomechanics, dict) or any(biomechanics.get(key) != profile.get(key) for key in ("movementPatternId", "bodyRegion", "kineticChain", "laterality", "equipmentId", "loadMode", "resistanceProfile")):
                    raise CatalogV2Error(f"rich_biomechanics_mismatch:{configuration_id}")
                _require_non_blank(biomechanics.get("stability"), f"rich_biomechanics_stability:{configuration_id}")
                _require_text_list(biomechanics.get("relevantJoints"), f"rich_biomechanics_joints:{configuration_id}")
                if set(biomechanics["relevantJoints"]) != set(joint_ids):
                    raise CatalogV2Error(f"rich_biomechanics_joint_involvement_mismatch:{configuration_id}")
                fatigue = rich.get("fatigue")
                if not isinstance(fatigue, dict) or any(fatigue.get(key) != profile.get(key) for key in ("efc", "cnc", "ssc", "ttc", "axialLoadFactor", "technicalDifficulty")):
                    raise CatalogV2Error(f"rich_fatigue_mismatch:{configuration_id}")
                for key in ("efc", "cnc", "ssc", "ttc", "axialLoadFactor", "technicalDifficulty"):
                    value = profile.get(key)
                    if not isinstance(value, (int, float)) or value != value or value == float("inf") or value == float("-inf") or value < 0:
                        raise CatalogV2Error(f"profile_metric_invalid:{configuration_id}:{key}")
                if not 1 <= profile.get("technicalDifficulty", 0) <= 10:
                    raise CatalogV2Error(f"profile_technical_difficulty_invalid:{configuration_id}")
                programming = rich.get("programming")
                if not isinstance(programming, dict):
                    raise CatalogV2Error(f"rich_programming_missing:{configuration_id}")
                _require_non_blank(programming.get("role"), f"rich_programming_role:{configuration_id}")
                _require_range(programming.get("indicativeRestSeconds"), f"rich_programming_rest:{configuration_id}")
                _require_non_blank(programming.get("fatigueCost"), f"rich_programming_fatigue:{configuration_id}")
                _require_text_list(programming.get("requiredEquipment"), f"rich_programming_equipment:{configuration_id}")
                replacement = rich.get("replacement")
                if not isinstance(replacement, dict):
                    raise CatalogV2Error(f"rich_replacement_missing:{configuration_id}")
                _require_text_list(replacement.get("compatibleEquipmentIds"), f"rich_replacement_equipment:{configuration_id}", allow_empty=True)
                expected_intent = [f"{profile.get('movementPatternId')}:{muscle_id}" for muscle_id in profile.get("primaryMuscles", [])]
                if replacement.get("preservesIntent") != expected_intent:
                    raise CatalogV2Error(f"rich_replacement_intent_not_derived:{configuration_id}")


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
