#!/usr/bin/env python3
"""Deterministic validator/compiler for the KPKN exercise catalog v2.

This tool deliberately has no heuristic merge behavior. It validates the
editorial source and only emits a runtime artifact when every definition and
configuration is explicitly approved.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"
OUTPUT = ROOT / "android-native" / "app" / "src" / "main" / "assets" / "exercise_catalog_v2.json"


def fail(message: str) -> None:
    raise ValueError(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_source() -> dict[str, Any]:
    require(SOURCE.exists(), f"Missing source: {SOURCE}")
    try:
        value = json.loads(SOURCE.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"Invalid JSON at {SOURCE}:{exc.lineno}:{exc.colno}: {exc.msg}")
    require(isinstance(value, dict), "Catalog root must be an object")
    require(value.get("schemaVersion") == 2, "schemaVersion must be exactly 2")
    require(isinstance(value.get("catalogRevision"), str) and value["catalogRevision"], "catalogRevision is required")
    require(isinstance(value.get("ontologyRevision"), str) and value["ontologyRevision"], "ontologyRevision is required")
    require(isinstance(value.get("families"), list), "families must be a list")
    return value


def validate_evidence(value: Any, path: str) -> None:
    require(isinstance(value, dict), f"{path}.evidence must be an object")
    require(value.get("reviewStatus") in {"DRAFT", "REVIEWED", "APPROVED"}, f"{path}.evidence.reviewStatus invalid")
    require(value.get("confidence") in {"LOW", "MEDIUM", "HIGH"}, f"{path}.evidence.confidence invalid")
    refs = value.get("evidenceRefs")
    require(isinstance(refs, list) and all(isinstance(ref, str) and ref.strip() for ref in refs), f"{path}.evidence.evidenceRefs required")


def validate_profile(value: Any, path: str, allow_draft: bool) -> None:
    require(isinstance(value, dict), f"{path}.profile must be an object")
    required = {
        "movementPatternId", "bodyRegion", "kineticChain", "laterality", "equipmentId",
        "loadMode", "primaryMuscles", "secondaryMuscles", "stabilizerMuscles", "efc",
        "cnc", "ssc", "ttc", "axialLoadFactor", "technicalDifficulty", "resistanceProfile",
        "setupCues", "executionCues", "commonMistakes", "performanceProfileId",
    }
    missing = sorted(key for key in required if key not in value)
    require(not missing, f"{path}.profile missing required fields: {', '.join(missing)}")
    for key in ("primaryMuscles", "secondaryMuscles", "stabilizerMuscles", "setupCues", "executionCues", "commonMistakes"):
        require(isinstance(value[key], list), f"{path}.profile.{key} must be a list")
    require(value["primaryMuscles"], f"{path}.profile.primaryMuscles cannot be empty")
    for key in ("efc", "cnc", "ssc", "ttc", "axialLoadFactor"):
        require(isinstance(value[key], (int, float)) and value[key] >= 0, f"{path}.profile.{key} must be a non-negative number")
    require(1 <= value["technicalDifficulty"] <= 10, f"{path}.profile.technicalDifficulty must be 1..10")
    require(all(isinstance(item, str) and item.strip() for item in value["setupCues"]), f"{path}.profile.setupCues contains empty text")
    require(all(isinstance(item, str) and item.strip() for item in value["executionCues"]), f"{path}.profile.executionCues contains empty text")
    require(all(isinstance(item, str) and item.strip() for item in value["commonMistakes"]), f"{path}.profile.commonMistakes contains empty text")
    require(isinstance(value["performanceProfileId"], str) and value["performanceProfileId"], f"{path}.profile.performanceProfileId required")
    if not allow_draft:
        require(value.get("automationEligible") is True, f"{path}.profile.automationEligible must be true for runtime")
        rich = value.get("richMetadata")
        require(isinstance(rich, dict), f"{path}.profile.richMetadata is required for runtime")
        required_sections = {"identity", "anatomy", "biomechanics", "programming", "fatigue", "replacement", "coaching", "safety", "display", "evidenceConfidence"}
        require(required_sections.issubset(rich), f"{path}.profile.richMetadata missing required sections")
        require(rich.get("evidenceConfidence") in {"MEDIUM", "HIGH"}, f"{path}.profile.richMetadata confidence must be MEDIUM or HIGH for runtime")

def validate_rich_metadata(
    profile: dict[str, Any],
    path: str,
    *,
    catalog_revision: str,
    family_id: str,
    definition: dict[str, Any],
    configuration: dict[str, Any],
) -> None:
    def require_text(value: Any, field: str) -> None:
        require(isinstance(value, str) and value.strip(), f"{path}.richMetadata.{field} must be non-empty text")

    def require_text_list(value: Any, field: str, *, allow_empty: bool = False) -> None:
        require(isinstance(value, list), f"{path}.richMetadata.{field} must be a list")
        if not allow_empty:
            require(value, f"{path}.richMetadata.{field} cannot be empty")
        require(all(isinstance(item, str) and item.strip() for item in value), f"{path}.richMetadata.{field} contains empty text")

    def require_range(value: Any, field: str) -> None:
        require(isinstance(value, dict), f"{path}.richMetadata.{field} must be an object")
        require(isinstance(value.get("min"), int) and isinstance(value.get("max"), int), f"{path}.richMetadata.{field} requires integer min/max")
        require(0 <= value["min"] <= value["max"], f"{path}.richMetadata.{field} range is invalid")

    rich = profile.get("richMetadata")
    require(isinstance(rich, dict), f"{path}.profile.richMetadata is required for runtime")
    identity = rich.get("identity")
    require(isinstance(identity, dict), f"{path}.profile.richMetadata.identity must be an object")
    require(identity.get("catalogRevision") == catalog_revision, f"{path}.richMetadata.identity.catalogRevision mismatch")
    require(identity.get("familyId") == family_id, f"{path}.richMetadata.identity.familyId mismatch")
    require(identity.get("definitionId") == definition["id"], f"{path}.richMetadata.identity.definitionId mismatch")
    require(identity.get("configurationId") == configuration["id"], f"{path}.richMetadata.identity.configurationId mismatch")
    require(identity.get("canonicalName") == definition["canonicalName"], f"{path}.richMetadata.identity.canonicalName mismatch")
    require(identity.get("performanceProfileId") == profile["performanceProfileId"], f"{path}.richMetadata.identity.performanceProfileId mismatch")
    require_text_list(identity.get("searchTerms"), "identity.searchTerms", allow_empty=True)
    display = rich.get("display")
    require(isinstance(display, dict), f"{path}.profile.richMetadata.display must be an object")
    require(display.get("displayName") == definition["canonicalName"], f"{path}.richMetadata.display.displayName mismatch")
    require(display.get("displaySummary") == configuration["displaySummary"], f"{path}.richMetadata.display.displaySummary mismatch")
    require(display.get("selectedOptions") == configuration["selectedOptions"], f"{path}.richMetadata.display.selectedOptions mismatch")
    anatomy = rich.get("anatomy")
    require(isinstance(anatomy, dict), f"{path}.profile.richMetadata.anatomy must be an object")
    require_text_list(anatomy.get("targetRegions"), "anatomy.targetRegions")
    require_text_list(anatomy.get("jointActions"), "anatomy.jointActions")
    for key in ("muscleLengthBias", "volumeContribution", "stabilizationDemand"):
        require_text(anatomy.get(key), f"anatomy.{key}")
    require(anatomy.get("primaryMuscles") == profile["primaryMuscles"], f"{path}.richMetadata.anatomy.primaryMuscles mismatch")
    require(anatomy.get("secondaryMuscles") == profile["secondaryMuscles"], f"{path}.richMetadata.anatomy.secondaryMuscles mismatch")
    require(anatomy.get("stabilizerMuscles") == profile["stabilizerMuscles"], f"{path}.richMetadata.anatomy.stabilizerMuscles mismatch")
    biomechanics = rich.get("biomechanics")
    require(isinstance(biomechanics, dict), f"{path}.profile.richMetadata.biomechanics must be an object")
    for key in ("movementPatternId", "bodyRegion", "kineticChain", "laterality", "equipmentId", "loadMode", "resistanceProfile"):
        require(biomechanics.get(key) == profile[key], f"{path}.richMetadata.biomechanics.{key} mismatch")
    require_text(biomechanics.get("rangeOfMotion"), "biomechanics.rangeOfMotion")
    require_text(biomechanics.get("stability"), "biomechanics.stability")
    require_text_list(biomechanics.get("relevantJoints"), "biomechanics.relevantJoints")
    require_text_list(biomechanics.get("relevantTendons"), "biomechanics.relevantTendons", allow_empty=True)
    fatigue = rich.get("fatigue")
    require(isinstance(fatigue, dict), f"{path}.profile.richMetadata.fatigue must be an object")
    for key in ("efc", "cnc", "ssc", "ttc", "axialLoadFactor", "technicalDifficulty"):
        require(fatigue.get(key) == profile[key], f"{path}.richMetadata.fatigue.{key} mismatch")
    coaching = rich.get("coaching")
    require(isinstance(coaching, dict), f"{path}.profile.richMetadata.coaching must be an object")
    require(coaching.get("setup") == profile["setupCues"], f"{path}.richMetadata.coaching.setup mismatch")
    require(coaching.get("execution") == profile["executionCues"], f"{path}.richMetadata.coaching.execution mismatch")
    require_text_list(coaching.get("cues"), "coaching.cues")
    require_text_list(coaching.get("progressions"), "coaching.progressions")
    require_text_list(coaching.get("regressions"), "coaching.regressions")
    require_text_list(coaching.get("relevantMobility"), "coaching.relevantMobility", allow_empty=True)
    require(coaching.get("commonMistakes") == profile["commonMistakes"], f"{path}.richMetadata.coaching.commonMistakes mismatch")
    require(identity.get("searchTerms") == definition.get("searchTerms", []), f"{path}.richMetadata.identity.searchTerms mismatch")
    require(identity.get("kind") == definition.get("kind"), f"{path}.richMetadata.identity.kind mismatch")
    programming = rich.get("programming")
    require(isinstance(programming, dict), f"{path}.profile.richMetadata.programming must be an object")
    require_text(programming.get("role"), "programming.role")
    require_text_list(programming.get("objectives"), "programming.objectives")
    require_text_list(programming.get("suitableRepRanges"), "programming.suitableRepRanges")
    require_range(programming.get("indicativeRestSeconds"), "programming.indicativeRestSeconds")
    require_text(programming.get("fatigueCost"), "programming.fatigueCost")
    require_text(programming.get("recoveryCost"), "programming.recoveryCost")
    require_text_list(programming.get("requiredEquipment"), "programming.requiredEquipment")
    require_text(programming.get("setupTransitionCost"), "programming.setupTransitionCost")
    require_text_list(programming.get("splitSuitability"), "programming.splitSuitability")
    replacement = rich.get("replacement")
    require(isinstance(replacement, dict), f"{path}.profile.richMetadata.replacement must be an object")
    require(replacement.get("replacementGroup") == profile.get("replacementGroup"), f"{path}.richMetadata.replacement.replacementGroup mismatch")
    require(replacement.get("replacementPriority") == profile.get("replacementPriority"), f"{path}.richMetadata.replacement.replacementPriority mismatch")
    require_text_list(replacement.get("compatibleEquipmentIds"), "replacement.compatibleEquipmentIds", allow_empty=True)
    require_text_list(replacement.get("preservesIntent"), "replacement.preservesIntent")
    safety = rich.get("safety")
    require(isinstance(safety, dict), f"{path}.profile.richMetadata.safety must be an object")
    require_text_list(safety.get("risks"), "safety.risks", allow_empty=True)
    require_text_list(safety.get("precautions"), "safety.precautions", allow_empty=True)
    require(isinstance(safety.get("medicalDisclaimerRequired"), bool), f"{path}.richMetadata.safety.medicalDisclaimerRequired must be boolean")
    require(rich.get("evidenceConfidence") in {"MEDIUM", "HIGH"}, f"{path}.profile.richMetadata confidence must be MEDIUM or HIGH for runtime")
def validate_family_manifest(source: dict[str, Any]) -> None:
    manifest_path = ROOT / "catalog" / "exercises" / "v2" / "source" / "manifest.json"
    family_dir = ROOT / "catalog" / "exercises" / "v2" / "source" / "families"
    require(manifest_path.exists(), f"Missing family manifest: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    require(manifest.get("schemaVersion") == source["schemaVersion"], "family manifest schemaVersion mismatch")
    require(manifest.get("catalogRevision") == source["catalogRevision"], "family manifest catalogRevision mismatch")
    names = manifest.get("families")
    require(isinstance(names, list) and names, "family manifest must list family files")
    materialized: list[dict[str, Any]] = []
    for name in names:
        path = family_dir / name
        require(path.exists(), f"Missing family source: {path}")
        payload = json.loads(path.read_text(encoding="utf-8"))
        require(payload.get("catalogRevision") == source["catalogRevision"], f"family source revision mismatch: {name}")
        family = payload.get("family")
        require(isinstance(family, dict), f"family source missing family object: {name}")
        materialized.append(family)
    expected = canonical_bytes({"schemaVersion": source["schemaVersion"], "catalogRevision": source["catalogRevision"], "ontologyRevision": source["ontologyRevision"], "families": sorted(source["families"], key=lambda item: item["id"])})
    actual = canonical_bytes({"schemaVersion": source["schemaVersion"], "catalogRevision": source["catalogRevision"], "ontologyRevision": source["ontologyRevision"], "families": sorted(materialized, key=lambda item: item["id"])})
    require(expected == actual, "family files drift from aggregated source")

def validate(source: dict[str, Any], allow_draft: bool) -> tuple[int, int]:
    family_ids: set[str] = set()
    definition_ids: set[str] = set()
    configuration_ids: set[str] = set()
    definition_count = 0
    configuration_count = 0

    for family_index, family in enumerate(source["families"]):
        family_path = f"families[{family_index}]"
        require(isinstance(family, dict), f"{family_path} must be an object")
        family_id = family.get("id")
        require(isinstance(family_id, str) and family_id, f"{family_path}.id required")
        require(family_id not in family_ids, f"Duplicate family id: {family_id}")
        family_ids.add(family_id)
        require(isinstance(family.get("definitions"), list) and family["definitions"], f"{family_path}.definitions cannot be empty")
        validate_evidence(family.get("evidence"), family_path)
        if not allow_draft:
            require(family["evidence"]["reviewStatus"] == "APPROVED", f"{family_path} is not APPROVED")

        for definition_index, definition in enumerate(family["definitions"]):
            definition_path = f"{family_path}.definitions[{definition_index}]"
            require(isinstance(definition, dict), f"{definition_path} must be an object")
            definition_id = definition.get("id")
            require(isinstance(definition_id, str) and definition_id, f"{definition_path}.id required")
            require(definition_id not in definition_ids, f"Duplicate definition id: {definition_id}")
            definition_ids.add(definition_id)
            require(definition.get("familyId") == family_id, f"{definition_path}.familyId must equal {family_id}")
            require(isinstance(definition.get("optionAxes"), list), f"{definition_path}.optionAxes must be a list")
            require(len(definition.get("description", "").strip()) >= 40, f"{definition_path}.description is too short")
            validate_evidence(definition.get("evidence"), definition_path)
            if not allow_draft:
                require(definition["evidence"]["reviewStatus"] == "APPROVED", f"{definition_path} is not APPROVED")
            configurations = definition.get("configurations")
            require(isinstance(configurations, list) and configurations, f"{definition_path}.configurations cannot be empty")
            for axis in definition["optionAxes"]:
                axis_values = {configuration.get("selectedOptions", {}).get(axis) for configuration in configurations}
                require(len(axis_values) > 1, f"{definition_path}.optionAxes contains a singleton axis: {axis}")
            configuration_ids_for_definition: set[str] = set()
            option_signatures: set[tuple[tuple[str, str], ...]] = set()
            for configuration_index, configuration in enumerate(configurations):
                configuration_path = f"{definition_path}.configurations[{configuration_index}]"
                require(isinstance(configuration, dict), f"{configuration_path} must be an object")
                configuration_id = configuration.get("id")
                require(isinstance(configuration_id, str) and configuration_id, f"{configuration_path}.id required")
                require(configuration_id not in configuration_ids, f"Duplicate configuration id: {configuration_id}")
                require(configuration_id not in configuration_ids_for_definition, f"Duplicate configuration id inside {definition_id}: {configuration_id}")
                configuration_ids.add(configuration_id)
                configuration_ids_for_definition.add(configuration_id)
                selected_options = configuration.get("selectedOptions")
                require(isinstance(selected_options, dict), f"{configuration_path}.selectedOptions must be an object")
                require(all(isinstance(key, str) and isinstance(value, str) and value.strip() for key, value in selected_options.items()), f"{configuration_path}.selectedOptions must contain non-empty strings")
                require(set(selected_options) == set(definition["optionAxes"]), f"{configuration_path}.selectedOptions must cover optionAxes exactly")
                option_signature = tuple(sorted(selected_options.items()))
                require(option_signature not in option_signatures, f"{configuration_path} duplicates another configuration selectedOptions")
                option_signatures.add(option_signature)
                validate_profile(configuration.get("profile"), configuration_path, allow_draft=allow_draft)
                if not allow_draft:
                    validate_rich_metadata(
                        configuration["profile"],
                        configuration_path,
                        catalog_revision=source["catalogRevision"],
                        family_id=family_id,
                        definition=definition,
                        configuration=configuration,
                    )
                validate_evidence(configuration.get("evidence"), configuration_path)
                if not allow_draft:
                    require(configuration["evidence"]["reviewStatus"] == "APPROVED", f"{configuration_path} is not APPROVED")
                configuration_count += 1
            default_id = definition.get("defaultConfigurationId")
            require(default_id in configuration_ids_for_definition, f"{definition_path}.defaultConfigurationId does not exist")
            definition_count += 1

    require(family_ids, "At least one family is required")
    return definition_count, configuration_count


def canonical_bytes(source: dict[str, Any]) -> bytes:
    return (json.dumps(source, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def write_atomic(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="validate without writing")
    parser.add_argument("--write", action="store_true", help="write the approved runtime asset")
    parser.add_argument("--allow-draft", action="store_true", help="validate draft source for editorial work; never use with --write")
    args = parser.parse_args()
    if args.write and args.allow_draft:
        parser.error("--write cannot be combined with --allow-draft")
    if not args.check and not args.write:
        parser.error("choose --check or --write")
    source = load_source()
    validate_family_manifest(source)
    definitions, configurations = validate(source, allow_draft=args.allow_draft)
    payload = canonical_bytes(source)
    digest = hashlib.sha256(payload).hexdigest()
    print(f"catalogRevision={source['catalogRevision']}")
    print(f"definitions={definitions} configurations={configurations}")
    print(f"canonicalSha256={digest}")
    if args.write:
        write_atomic(OUTPUT, payload)
        print(f"wrote={OUTPUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
