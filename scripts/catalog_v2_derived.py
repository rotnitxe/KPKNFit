"""Fields of the runtime catalog that are pure functions of the anatomy.

The ficha is the only place where a human writes muscles and joints. Everything
below is *derived* from them, so it can never drift:

* ``profile.primaryMuscles / secondaryMuscles / stabilizerMuscles``
* ``profile.jointInvolvement`` (``jointId``, ``role``, ``actions``)
* ``richMetadata.anatomy.*`` mirrors of the two previous fields
* ``richMetadata.anatomy.jointActions`` (de-duplicated, in joint order)
* ``richMetadata.biomechanics.relevantJoints``
* ``richMetadata.replacement.preservesIntent`` (``<movementPatternId>:<primaryMuscleId>``)

The applier writes them, the gate and the compiler check them with the very
same functions (so "apply changes nothing" is the invariant that is enforced).
"""

from __future__ import annotations

from typing import Any, Iterable

from catalog_v2_ontology import MUSCLE_LISTS


def derive_muscle_lists(muscles: Iterable[dict[str, Any]]) -> dict[str, list[str]]:
    """Group authored muscle entries by role, keeping the authored order."""
    entries = list(muscles)
    return {
        field_name: [entry["id"] for entry in entries if entry["role"] == role]
        for field_name, role in MUSCLE_LISTS
    }


def derive_joint_involvement(joints: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {"jointId": joint["id"], "role": joint["role"], "actions": list(joint["actions"])}
        for joint in joints
    ]


def derive_joint_actions(joint_involvement: Iterable[dict[str, Any]]) -> list[str]:
    flattened = [action for joint in joint_involvement for action in joint["actions"]]
    return list(dict.fromkeys(flattened))


def derive_relevant_joints(joint_involvement: Iterable[dict[str, Any]]) -> list[str]:
    return [joint["jointId"] for joint in joint_involvement]


def derive_preserves_intent(movement_pattern_id: str, primary_muscles: Iterable[str]) -> list[str]:
    """Machine-readable intent tokens: the substitution keeps the same pattern and target muscle."""
    return [f"{movement_pattern_id}:{muscle_id}" for muscle_id in primary_muscles]
