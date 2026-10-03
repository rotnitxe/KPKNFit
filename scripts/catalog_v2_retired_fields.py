#!/usr/bin/env python3
"""Single definition of the exercise catalog v2 fields retired in F1.

Retirement means the field is neither authored, stored in the editorial
source, compiled into the runtime asset nor read by Android, iOS or the
backend.  The compiler, the gate and the backend validator all reject a
payload that still carries one of these keys, so a retired payload can never
silently come back.

Fields are retired only when no production code reads them (proof recorded in
``catalog/exercises/v2/curation/STATUS.md``).  Fields that are still read are
deliberately absent from this list: ``programming.indicativeRestSeconds``
(iOS session assistant and analytics), ``programming.requiredEquipment``
(Android plan compatibility), ``programming.role`` / ``biomechanics.stability``
(iOS projection) and ``replacement.preservesIntent`` (Android similarity).

The backend keeps a literal copy of ``RETIRED_FIELD_PATHS`` because it is
deployed without ``scripts/``; ``scripts/tests/test_catalog_v2_retired_fields.py``
fails when both copies diverge.
"""

from __future__ import annotations

import copy
from typing import Any, Mapping

# Every retired field as a stable, human readable path.  ``[]`` marks a list
# whose items carry the field.  The same strings are used in error messages.
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

_PROFILE_KEYS = ("benefits", "techniqueSummary", "variantRationale", "commonMistakes", "muscleNotes")
_RICH_BLOCKS = ("editorial", "coaching", "safety")
_RICH_FIELDS = {
    "anatomy": ("targetRegions", "muscleLengthBias", "stabilizationDemand"),
    "biomechanics": ("rangeOfMotion", "relevantTendons"),
    "programming": (
        "objectives",
        "suitableRepRanges",
        "recoveryCost",
        "setupTransitionCost",
        "splitSuitability",
    ),
}


def _families(payload: Mapping[str, Any]) -> list[Mapping[str, Any]]:
    """Accept an aggregated catalog, a per-family file payload or a family."""
    if isinstance(payload.get("families"), list):
        return [item for item in payload["families"] if isinstance(item, Mapping)]
    if isinstance(payload.get("family"), Mapping):
        return [payload["family"]]
    if "definitions" in payload:
        return [payload]
    return []


def _joint_note_paths(joints: Any, prefix: str) -> list[str]:
    if not isinstance(joints, list):
        return []
    return [
        f"{prefix}[{index}].note"
        for index, joint in enumerate(joints)
        if isinstance(joint, Mapping) and "note" in joint
    ]


def find_retired(payload: Mapping[str, Any]) -> list[str]:
    """Return every retired field still present, with the owning identifier."""
    found: list[str] = []
    for family in _families(payload):
        family_id = family.get("id", "<family>")
        if "description" in family:
            found.append(f"{family_id}.description")
        if isinstance(family.get("evidence"), Mapping) and "rationale" in family["evidence"]:
            found.append(f"{family_id}.evidence.rationale")
        for definition in family.get("definitions", []) or []:
            if not isinstance(definition, Mapping):
                continue
            definition_id = definition.get("id", "<definition>")
            evidence = definition.get("evidence")
            if isinstance(evidence, Mapping) and "rationale" in evidence:
                found.append(f"{definition_id}.evidence.rationale")
            for configuration in definition.get("configurations", []) or []:
                if not isinstance(configuration, Mapping):
                    continue
                configuration_id = configuration.get("id", "<configuration>")
                evidence = configuration.get("evidence")
                if isinstance(evidence, Mapping) and "rationale" in evidence:
                    found.append(f"{configuration_id}.evidence.rationale")
                profile = configuration.get("profile")
                if not isinstance(profile, Mapping):
                    continue
                for key in _PROFILE_KEYS:
                    if key in profile:
                        found.append(f"{configuration_id}.profile.{key}")
                found.extend(
                    f"{configuration_id}.profile{path}"
                    for path in _joint_note_paths(profile.get("jointInvolvement"), ".jointInvolvement")
                )
                rich = profile.get("richMetadata")
                if not isinstance(rich, Mapping):
                    continue
                for block in _RICH_BLOCKS:
                    if block in rich:
                        found.append(f"{configuration_id}.richMetadata.{block}")
                for section, keys in _RICH_FIELDS.items():
                    body = rich.get(section)
                    if not isinstance(body, Mapping):
                        continue
                    for key in keys:
                        if key in body:
                            found.append(f"{configuration_id}.richMetadata.{section}.{key}")
                anatomy = rich.get("anatomy")
                if isinstance(anatomy, Mapping):
                    found.extend(
                        f"{configuration_id}.richMetadata.anatomy{path}"
                        for path in _joint_note_paths(anatomy.get("jointInvolvement"), ".jointInvolvement")
                    )
    return found


def strip_retired(payload: Any) -> Any:
    """Return a deep copy of ``payload`` without any retired field.

    Used once by the F1 migration; the compiler never calls it because the
    source must already be clean (``find_retired`` is a hard gate).
    """
    cleaned = copy.deepcopy(payload)
    if not isinstance(cleaned, dict):
        return cleaned
    for family in _families(cleaned):
        family.pop("description", None)
        _pop_rationale(family)
        for definition in family.get("definitions", []) or []:
            _pop_rationale(definition)
            for configuration in definition.get("configurations", []) or []:
                _pop_rationale(configuration)
                profile = configuration.get("profile")
                if not isinstance(profile, dict):
                    continue
                for key in _PROFILE_KEYS:
                    profile.pop(key, None)
                _drop_joint_notes(profile.get("jointInvolvement"))
                rich = profile.get("richMetadata")
                if not isinstance(rich, dict):
                    continue
                for block in _RICH_BLOCKS:
                    rich.pop(block, None)
                for section, keys in _RICH_FIELDS.items():
                    body = rich.get(section)
                    if isinstance(body, dict):
                        for key in keys:
                            body.pop(key, None)
                anatomy = rich.get("anatomy")
                if isinstance(anatomy, dict):
                    _drop_joint_notes(anatomy.get("jointInvolvement"))
    return cleaned


def _pop_rationale(owner: Any) -> None:
    evidence = owner.get("evidence") if isinstance(owner, dict) else None
    if isinstance(evidence, dict):
        evidence.pop("rationale", None)


def _drop_joint_notes(joints: Any) -> None:
    if isinstance(joints, list):
        for joint in joints:
            if isinstance(joint, dict):
                joint.pop("note", None)
