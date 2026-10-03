#!/usr/bin/env python3
"""The Python ontology is a mirror of the Kotlin one; these tests keep both honest.

`scripts/catalog_v2_ontology.py` is what the applier, the gate and the quality audit
validate a ficha against. `AprendeOntology.kt` is what the app resolves at runtime.
If they drift, a ficha could pass the tooling and still reference an id the app
does not know (or the tooling would block an id the app already supports).
"""

from __future__ import annotations

import json
import re
import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = SCRIPTS.parent
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from catalog_v2_ontology import JOINT_IDS, MOVEMENT_PATTERN_IDS, MUSCLE_IDS, ROLES  # noqa: E402

KOTLIN = (
    REPOSITORY
    / "android-native" / "app" / "src" / "main" / "java" / "com" / "example" / "kpkn"
    / "domain" / "exercises" / "catalogv2" / "AprendeOntology.kt"
)
SOURCE = REPOSITORY / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"


def kotlin_block(text: str, declaration: str) -> str:
    """Text between the parentheses of ``declaration = xxxOf(`` ... ``)`` (comments removed)."""
    start = text.index(declaration)
    open_at = text.index("(", text.index("=", start))
    depth = 0
    for position in range(open_at, len(text)):
        if text[position] == "(":
            depth += 1
        elif text[position] == ")":
            depth -= 1
            if depth == 0:
                block = text[open_at + 1 : position]
                return re.sub(r"//[^\n]*", "", block)
    raise AssertionError(f"unbalanced block for {declaration}")


class OntologyMirrorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.kotlin = KOTLIN.read_text(encoding="utf-8")

    def test_joint_ids_match_the_kotlin_ontology(self) -> None:
        block = kotlin_block(self.kotlin, "val wikiLabJointIds")
        self.assertEqual(set(re.findall(r'"([^"]+)"', block)), set(JOINT_IDS))

    def test_muscle_ids_match_the_kotlin_ontology(self) -> None:
        block = kotlin_block(self.kotlin, "val catalogMuscleToWikiLab")
        self.assertEqual(set(re.findall(r'"([^"]+)"\s+to\s+', block)), set(MUSCLE_IDS))

    def test_movement_pattern_ids_match_the_kotlin_ontology(self) -> None:
        block = kotlin_block(self.kotlin, "val catalogPatternToWikiLab")
        patterns = set(re.findall(r'"([^"]+)"\s+to\s+', block))
        self.assertEqual(patterns, set(MOVEMENT_PATTERN_IDS))
        self.assertEqual(len(MOVEMENT_PATTERN_IDS), 64)

    def test_roles_are_the_three_runtime_roles(self) -> None:
        self.assertEqual(ROLES, ("PRIMARY", "SECONDARY", "STABILIZER"))


class CatalogUsesOnlyTheOntologyTest(unittest.TestCase):
    """The shipped source must already live inside the ontology the tooling enforces."""

    @classmethod
    def setUpClass(cls) -> None:
        source = json.loads(SOURCE.read_text(encoding="utf-8"))
        cls.profiles = [
            configuration["profile"]
            for family in source["families"]
            for definition in family["definitions"]
            for configuration in definition["configurations"]
        ]

    def test_every_pattern_is_a_known_runtime_pattern(self) -> None:
        used = {profile["movementPatternId"] for profile in self.profiles}
        self.assertEqual(sorted(used - set(MOVEMENT_PATTERN_IDS)), [])

    def test_every_muscle_is_a_known_muscle(self) -> None:
        used = {muscle for profile in self.profiles for field in ("primaryMuscles", "secondaryMuscles", "stabilizerMuscles") for muscle in profile[field]}
        self.assertEqual(sorted(used - set(MUSCLE_IDS)), [])

    def test_every_joint_is_a_known_joint(self) -> None:
        used = {joint["jointId"] for profile in self.profiles for joint in profile["jointInvolvement"]}
        self.assertEqual(sorted(used - set(JOINT_IDS)), [])


if __name__ == "__main__":  # pragma: no cover
    unittest.main()
