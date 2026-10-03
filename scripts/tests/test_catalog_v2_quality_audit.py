#!/usr/bin/env python3
"""Tests for the read-only editorial quality audit (scripts/catalog_v2_quality_audit.py).

Every test builds a tiny synthetic catalogue and drives the production `run_audit`
(or `main`) entry point; assertions filter by check name so unrelated noise from the
minimal fixtures never hides or fakes a result. The default text of every fixture
definition embeds its own id, so nothing is shared between definitions unless a test
asks for it. A last group validates the shipped rules/lexicon/allowlist against the
real catalogue.
"""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any

SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY = SCRIPTS.parent
AUDIT = SCRIPTS / "catalog_v2_quality_audit.py"
CURATION = REPOSITORY / "catalog" / "exercises" / "v2" / "curation"
SOURCE = REPOSITORY / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"
TEMP_OVERRIDE = os.environ.get("KPKN_TEST_TEMP_DIR")


def load_audit():
    spec = importlib.util.spec_from_file_location("catalog_v2_quality_audit", AUDIT)
    if spec is None or spec.loader is None:  # pragma: no cover - defensive
        raise RuntimeError(f"cannot load {AUDIT}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["catalog_v2_quality_audit"] = module
    spec.loader.exec_module(module)
    return module


audit = load_audit()


def temporary_directory() -> tempfile.TemporaryDirectory:
    root = Path(TEMP_OVERRIDE) if TEMP_OVERRIDE and Path(TEMP_OVERRIDE).is_dir() else None
    return tempfile.TemporaryDirectory(dir=str(root) if root else None)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


def configuration(
    definition_id: str,
    suffix: str,
    *,
    equipment: str = "barbell",
    pattern: str = "horizontal_pull",
    description: str | None = None,
    setup: list[str] | None = None,
    execution: list[str] | None = None,
    primary: list[str] | None = None,
    secondary: list[str] | None = None,
    stabilizer: list[str] | None = None,
    joints: list[dict[str, Any]] | None = None,
    options: dict[str, Any] | None = None,
) -> dict[str, Any]:
    profile: dict[str, Any] = {
        "equipmentId": equipment,
        "movementPatternId": pattern,
        "description": description
        if description is not None
        else f"En el caso {suffix} de {definition_id}, esta ficha sirve solo para probar.",
        "setupCues": setup if setup is not None else [f"Prepara el agarre de {definition_id} con calma antes de empezar."],
        "executionCues": execution
        if execution is not None
        else [f"Tira del peso de {definition_id} hacia el torso sin balancear."],
        "primaryMuscles": primary if primary is not None else ["latissimus_dorsi"],
        "secondaryMuscles": secondary or [],
        "stabilizerMuscles": stabilizer or [],
        "jointInvolvement": joints
        if joints is not None
        else [{"jointId": "codo", "role": "SECONDARY", "actions": ["Flexión"]}],
    }
    return {"id": f"{definition_id}__{suffix}", "profile": profile, "selectedOptions": options or {"implement": equipment}}


def definition(
    definition_id: str,
    configurations: list[dict[str, Any]] | None = None,
    *,
    description: str | None = None,
    family: str = "family_a",
) -> dict[str, Any]:
    configs = configurations if configurations is not None else [configuration(definition_id, "default")]
    # Every 8-word window of every sentence contains the id, so no n-gram is shared between definitions.
    default_description = (
        f"El dorsal ancho de {definition_id} trabaja con una carga progresiva. "
        f"Con {definition_id} se mantiene el control del recorrido completo. "
        f"La variante de {definition_id} cambia la carga disponible en cada serie. "
        f"Resulta útil buscar con {definition_id} un tirón preciso y controlado."
    )
    return {
        "id": definition_id,
        "familyId": family,
        "canonicalName": definition_id.replace("_", " ").title(),
        "description": description if description is not None else default_description,
        "defaultConfigurationId": configs[0]["id"],
        "configurations": configs,
    }


def catalog(*definitions: dict[str, Any]) -> dict[str, Any]:
    families: dict[str, list[dict[str, Any]]] = {}
    for item in definitions:
        families.setdefault(item["familyId"], []).append(item)
    return {
        "catalogRevision": "test-revision",
        "families": [{"id": family_id, "definitions": defs} for family_id, defs in families.items()],
    }


def findings_of(result, check: str, *, definition_id: str | None = None) -> list:
    return [
        item
        for item in result.findings
        if item.check == check and (definition_id is None or item.definition_id == definition_id)
    ]


SHARED_CUE = "Aprieta el glúteo arriba y baja controlando la pelvis siempre."

LEXICON = {
    "banned": [{"id": "cliche.rey", "pattern": "\\b(?:el|la) (?:rey|reina)\\b", "why": "cliché"}],
    "budgets": [{"id": "budget.honesto", "pattern": "\\bhonest[oa]s?\\b", "maxDefinitions": 1, "why": "comodín"}],
    "imperativeStarts": ["manten", "configura"],
}


class FixtureSanityTest(unittest.TestCase):
    """The fixtures must be clean by default, otherwise the other tests would measure the fixtures."""

    def test_default_fixture_has_no_content_findings(self) -> None:
        result = audit.run_audit(catalog(definition("alfa"), definition("beta"), definition("gamma")))
        content_checks = {
            "intra_config_duplicate", "shared_sentence", "shared_ngram", "template_skeleton",
            "template_skeleton_in_definition", "template_opening", "grammar", "shape", "implement_mismatch",
            "muscle_claim", "primary_unmentioned", "banned_phrase", "word_budget",
        }
        self.assertEqual([], [(item.check, item.field, item.message) for item in result.findings if item.check in content_checks])


# ---------------------------------------------------------------------------
# Repetition
# ---------------------------------------------------------------------------


class RepetitionChecksTest(unittest.TestCase):
    def test_sentence_repeated_between_fields_of_one_configuration_is_flagged(self) -> None:
        repeated = "El codo se mantiene pegado al costado durante todo el recorrido."
        cfg = configuration("rem", "a", description=f"Apertura única de este caso concreto. {repeated}", execution=[repeated])
        flagged = findings_of(audit.run_audit(catalog(definition("rem", [cfg]))), "intra_config_duplicate")
        self.assertEqual(1, len(flagged))
        self.assertEqual("rem__a", flagged[0].configuration_id)
        self.assertTrue(flagged[0].field.startswith("profile."))

    def test_near_identical_sentences_are_flagged_and_distinct_ones_are_not(self) -> None:
        near_a = "Lleva el codo hacia atrás pegado al costado y pausa un instante arriba antes de volver."
        near_b = "Lleva el codo hacia atrás pegado al costado y pausa un instante arriba antes de volver ya."
        cfg = configuration("rem", "a", description=f"Idea distinta de este caso concreto. {near_a}", execution=[near_b])
        near = findings_of(audit.run_audit(catalog(definition("rem", [cfg]))), "intra_config_duplicate")
        self.assertEqual(1, len(near))
        self.assertIn("casi repite", near[0].message)
        distinct = configuration("rem", "b")
        self.assertEqual([], findings_of(audit.run_audit(catalog(definition("rem", [distinct]))), "intra_config_duplicate"))

    def test_sentence_copied_from_the_definition_description_is_flagged(self) -> None:
        shared = "La escápula se retrae antes de iniciar cualquier tirón del brazo."
        cfg = configuration("rem", "a", description=f"Matiz propio de esta configuración concreta. {shared}")
        item = definition("rem", [cfg], description=f"{shared} Otra frase cualquiera para la prueba. Y una tercera más. Y una cuarta final.")
        self.assertEqual(1, len(findings_of(audit.run_audit(catalog(item)), "intra_config_duplicate")))

    def test_identical_sentence_in_two_definitions_is_a_shared_sentence(self) -> None:
        a = definition("alfa", [configuration("alfa", "a", execution=[SHARED_CUE])])
        b = definition("beta", [configuration("beta", "a", execution=[SHARED_CUE])])
        c = definition("gamma")
        flagged = findings_of(audit.run_audit(catalog(a, b, c)), "shared_sentence")
        self.assertEqual({"alfa", "beta"}, {item.definition_id for item in flagged})

    def test_same_cue_in_sibling_configurations_of_one_definition_is_allowed(self) -> None:
        item = definition(
            "alfa",
            [
                configuration("alfa", "a", execution=[SHARED_CUE]),
                configuration("alfa", "b", equipment="dumbbells", execution=[SHARED_CUE]),
            ],
        )
        self.assertEqual([], findings_of(audit.run_audit(catalog(item)), "shared_sentence"))

    def test_scope_reports_only_requested_definitions_but_compares_with_the_whole_corpus(self) -> None:
        a = definition("alfa", [configuration("alfa", "a", execution=[SHARED_CUE])])
        b = definition("beta", [configuration("beta", "a", execution=[SHARED_CUE])])
        result = audit.run_audit(catalog(a, b), scope=["alfa"])
        self.assertEqual({"alfa"}, {item.definition_id for item in findings_of(result, "shared_sentence")})
        self.assertEqual(["alfa"], result.report_ids)
        with self.assertRaises(ValueError):
            audit.run_audit(catalog(a, b), scope=["fantasma"])

    def test_long_shared_phrase_is_flagged_but_short_collocations_are_not(self) -> None:
        a = definition("alfa", [configuration("alfa", "a", description="Al empezar, la barra sale despacio del suelo con el dorsal activo siempre antes.")])
        b = definition("beta", [configuration("beta", "a", description="Aquí la barra sale despacio del suelo con el dorsal activo siempre y termina arriba.")])
        c = definition("gamma", [configuration("gamma", "a", description="Los pies al ancho de los hombros y la mirada al frente.")])
        d = definition("delta", [configuration("delta", "a", description="Con los pies al ancho de los hombros se empieza muy bien.")])
        flagged = {item.definition_id for item in findings_of(audit.run_audit(catalog(a, b, c, d)), "shared_ngram")}
        self.assertEqual({"alfa", "beta"}, flagged)

    def test_dense_five_word_fragment_is_flagged_but_a_natural_collocation_is_not(self) -> None:
        a = definition("alfa", [configuration("alfa", "a", description="En alfa la pierna delantera frena empuja peso con calma total.")])
        b = definition("beta", [configuration("beta", "a", description="Con beta la pierna delantera frena empuja peso sin prisa nunca.")])
        flagged = findings_of(audit.run_audit(catalog(a, b)), "shared_ngram")
        self.assertEqual({"alfa", "beta"}, {item.definition_id for item in flagged})
        self.assertTrue(all(item.message.startswith("fragmento de 6 palabras") for item in flagged), [item.message for item in flagged])
        c = definition("gamma", [configuration("gamma", "a", description="En gamma hay que poner los pies al ancho de los hombros hoy.")])
        d = definition("delta", [configuration("delta", "a", description="Con delta los pies al ancho de los hombros se trabaja bien.")])
        self.assertEqual([], findings_of(audit.run_audit(catalog(c, d)), "shared_ngram"))

    def test_template_with_swapped_muscle_and_implement_is_flagged_from_three_definitions(self) -> None:
        texts = {
            "alfa": "El dorsal con la barra recibe la carga con tensión continua desde el arranque.",
            "beta": "El pectoral con la polea recibe la carga con tensión continua desde el arranque.",
            "gamma": "El cuádriceps con la banda recibe la carga con tensión continua desde el arranque.",
        }
        definitions = [definition(name, [configuration(name, "a", description=text)]) for name, text in texts.items()]
        flagged = findings_of(audit.run_audit(catalog(*definitions)), "template_skeleton")
        self.assertEqual({"alfa", "beta", "gamma"}, {item.definition_id for item in flagged})
        self.assertEqual([], findings_of(audit.run_audit(catalog(*definitions[:2])), "template_skeleton"))

    def test_same_description_between_implement_siblings_is_a_template_in_the_definition(self) -> None:
        item = definition(
            "alfa",
            [
                configuration("alfa", "barra", equipment="barbell", description="Con barra, el dorsal recibe la carga con tensión continua desde el arranque."),
                configuration("alfa", "mancuernas", equipment="dumbbells", description="Con mancuernas, el dorsal recibe la carga con tensión continua desde el arranque."),
            ],
        )
        flagged = findings_of(audit.run_audit(catalog(item)), "template_skeleton_in_definition")
        self.assertEqual(1, len(flagged))
        self.assertEqual("alfa__mancuernas", flagged[0].configuration_id)

    def test_repeated_opening_across_configurations_is_flagged(self) -> None:
        endings = ["inicial", "medio", "final", "lento", "rápido", "mixto"]
        definitions = [
            definition(
                f"ej{index}",
                [configuration(f"ej{index}", "a", description=f"El trabajo constante del dorsal cambia según el caso {ending} de la serie.")],
            )
            for index, ending in enumerate(endings)
        ]
        options = audit.Options(opening_min_configurations=6, opening_min_definitions=3)
        flagged = findings_of(audit.run_audit(catalog(*definitions), options=options), "template_opening")
        self.assertEqual(6, len(flagged))
        fewer = findings_of(audit.run_audit(catalog(*definitions[:5]), options=options), "template_opening")
        self.assertEqual([], fewer)


# ---------------------------------------------------------------------------
# Lexicon, grammar, shape
# ---------------------------------------------------------------------------


class LexiconGrammarShapeTest(unittest.TestCase):
    def test_banned_phrase_budget_and_imperative_start(self) -> None:
        a = definition(
            "alfa",
            description="El rey de los remos exige una técnica honesta. Mantén la espalda recta. Tercera frase. Cuarta frase para cumplir.",
        )
        b = definition("beta", [configuration("beta", "a", description="Es un remo honesto para quien quiere sentir la espalda trabajar bien.")])
        result = audit.run_audit(catalog(a, b), lexicon=LEXICON)
        self.assertEqual(1, len(findings_of(result, "banned_phrase", definition_id="alfa")))
        self.assertEqual([], findings_of(result, "banned_phrase", definition_id="beta"))
        self.assertEqual({"alfa", "beta"}, {item.definition_id for item in findings_of(result, "word_budget")})
        self.assertEqual(1, len(findings_of(result, "imperative_description", definition_id="alfa")))
        self.assertEqual([], findings_of(result, "imperative_description", definition_id="beta"))

    def test_a_word_inside_its_budget_is_not_flagged(self) -> None:
        a = definition("alfa", [configuration("alfa", "a", description="Un remo honesto para empezar a sentir la espalda con calma.")])
        b = definition("beta")
        self.assertEqual([], findings_of(audit.run_audit(catalog(a, b), lexicon=LEXICON), "word_budget"))

    def test_budget_counts_the_whole_corpus_even_when_the_scope_is_narrower(self) -> None:
        a = definition("alfa", [configuration("alfa", "a", description="Un remo honesto para empezar a sentir la espalda con calma.")])
        b = definition("beta", [configuration("beta", "a", description="Otro remo honesto para quien busca control en cada repetición.")])
        result = audit.run_audit(catalog(a, b), lexicon=LEXICON, scope=["beta"])
        self.assertEqual({"beta"}, {item.definition_id for item in findings_of(result, "word_budget")})

    def test_grammar_slips_are_reported_one_by_one(self) -> None:
        cases = {
            "de_el": "Aprieta el objetivo de el dorsal en cada repetición completa.",
            "snake_case": "Trabaja el patrón horizontal_pull durante todo el recorrido final.",
            "double_space": "Tira  del peso hacia el torso con calma durante la serie.",
            "repeated_word": "Baja la barra barra despacio hasta el borde inferior del banco.",
            "dangling_preposition": "El peso muerto más directo para, desde el suelo con las dos manos.",
            "punctuation": "Sube la barra , despacio y sin prisa hasta arriba del todo.",
        }
        for name, text in cases.items():
            with self.subTest(name=name):
                result = audit.run_audit(catalog(definition("alfa", [configuration("alfa", "a", description=text)])))
                messages = [item.message for item in findings_of(result, "grammar")]
                self.assertTrue(any(message.startswith(name) for message in messages), messages)

    def test_derived_preserves_intent_token_is_not_audited_as_prose_but_prose_still_is(self) -> None:
        def with_intent(value: str) -> dict[str, Any]:
            cfg = configuration("alfa", "a")
            cfg["profile"]["richMetadata"] = {"replacement": {"preservesIntent": [value]}}
            return catalog(definition("alfa", [cfg]))

        token = audit.run_audit(with_intent("horizontal_pull:latissimus_dorsi"))
        self.assertEqual([], findings_of(token, "grammar"), "the F1 machine token must not trip the grammar check")
        prose = audit.run_audit(with_intent("trabaja el patrón horizontal_pull sin cambiar el dorsal"))
        self.assertTrue(findings_of(prose, "grammar"), "pre-F1 prose in the same field keeps being audited")

    def test_lowercase_start_is_flagged_and_clean_text_is_not(self) -> None:
        bad = audit.run_audit(
            catalog(definition("alfa", [configuration("alfa", "a", description="empieza en minúscula y sigue con calma durante la serie completa.")]))
        )
        self.assertTrue(any("mayúscula" in item.message for item in findings_of(bad, "grammar")))
        good = audit.run_audit(
            catalog(definition("alfa", [configuration("alfa", "a", description="Empieza con mayúscula y sigue con calma durante la serie completa.")]))
        )
        self.assertEqual([], findings_of(good, "grammar"))

    def test_shape_limits_for_definitions_configurations_and_cues(self) -> None:
        long_cue = " ".join(["palabra"] * 31) + "."
        item = definition(
            "alfa",
            [configuration("alfa", "a", description="Una sola frase corta.", setup=["Uno.", "Dos.", "Tres.", "Cuatro."], execution=[long_cue])],
            description="Demasiado breve.",
        )
        joined = " | ".join(entry.message for entry in findings_of(audit.run_audit(catalog(item)), "shape"))
        for expected in (
            "descripción de definición con 1 frases",
            "palabras (35–140)",
            "palabras (12–55)",
            "4 cues en setupCues",
            "cue de 31 palabras",
        ):
            self.assertIn(expected, joined)


# ---------------------------------------------------------------------------
# Text versus data
# ---------------------------------------------------------------------------


class TextVersusDataTest(unittest.TestCase):
    def test_cue_naming_another_implement_and_definition_level_naming_are_errors(self) -> None:
        cfg = configuration("alfa", "a", equipment="dumbbells", setup=["Coloca la barra sobre el banco antes de empezar."])
        item = definition(
            "alfa",
            [cfg, configuration("alfa", "b", equipment="barbell")],
            description="El Alfa trabaja el dorsal ancho. Con mancuernas o con polea cambia el recorrido. Hay una tercera frase. Y una cuarta final.",
        )
        flagged = findings_of(audit.run_audit(catalog(item)), "implement_mismatch")
        self.assertEqual(2, len(flagged))
        by_field = {entry.field: entry for entry in flagged}
        self.assertIn("pero la configuración usa dumbbells", by_field["profile.setupCues[0]"].message)
        self.assertIn("polea/cable", by_field["definition.description"].message)

    def test_description_may_contrast_with_a_sibling_implement(self) -> None:
        item = definition(
            "alfa",
            [
                configuration("alfa", "a", equipment="barbell", description="Con barra se carga más que con mancuernas y el agarre es fijo."),
                configuration("alfa", "b", equipment="dumbbells", description="Con mancuernas cada brazo recorre su propia línea."),
            ],
        )
        self.assertEqual([], findings_of(audit.run_audit(catalog(item)), "implement_mismatch"))

    def test_negated_implements_are_tolerated_in_prose_but_not_in_cues(self) -> None:
        cfg = configuration(
            "alfa", "a", equipment="bodyweight",
            description="Se hace con el propio peso, sin mancuernas ni máquina, y exige mucho control del recorrido.",
            setup=["Quédate de pie sin mancuernas en las manos."],
        )
        flagged = findings_of(audit.run_audit(catalog(definition("alfa", [cfg]))), "implement_mismatch")
        self.assertEqual(["profile.setupCues[0]"], [entry.field for entry in flagged])

    def test_label_prefixed_notes_do_not_need_a_capital_but_plain_text_does(self) -> None:
        labelled = configuration(
            "alfa", "a",
            joints=[{"jointId": "cadera", "role": "SECONDARY", "actions": ["Flexión"], "note": "Secundaria: la cadera flexiona junto a la rodilla."}],
        )
        self.assertEqual([], findings_of(audit.run_audit(catalog(definition("alfa", [labelled]))), "grammar"))
        plain = configuration(
            "alfa", "a",
            joints=[{"jointId": "cadera", "role": "SECONDARY", "actions": ["Flexión"], "note": "la cadera flexiona junto a la rodilla."}],
        )
        flagged = findings_of(audit.run_audit(catalog(definition("alfa", [plain]))), "grammar")
        self.assertEqual(1, len(flagged))
        self.assertIn("mayúscula", flagged[0].message)

    def test_colloquial_singular_names_the_hamstrings(self) -> None:
        cfg = configuration("alfa", "a", primary=["hamstrings"], description="El isquio recibe el estiramiento mientras la cadera se mantiene fija en cada serie.")
        item = definition(
            "alfa", [cfg],
            description="El Alfa estira el isquio con carga. Segunda frase de relleno. Tercera frase de relleno. Cuarta frase de relleno.",
        )
        result = audit.run_audit(catalog(item))
        self.assertEqual([], findings_of(result, "primary_unmentioned"))
        self.assertEqual([], findings_of(result, "muscle_claim"))

    def test_undeclared_muscle_is_a_claim_error_and_the_primary_must_be_named(self) -> None:
        cfg = configuration(
            "alfa", "a", primary=["biceps"],
            description="Las palmas al frente dejan el pectoral al mando durante la serie completa.",
        )
        item = definition(
            "alfa", [cfg],
            description="El Alfa trabaja los isquiosurales con carga. Segunda frase de relleno. Tercera frase de relleno. Cuarta frase de relleno.",
        )
        result = audit.run_audit(catalog(item))
        severities = {entry.field: entry.severity for entry in findings_of(result, "muscle_claim")}
        self.assertEqual("WARN", severities["profile.description"])
        self.assertEqual("ERROR", severities["definition.description"])
        self.assertEqual(1, len(findings_of(result, "primary_unmentioned")))

    def test_declared_muscles_and_positional_words_are_not_claims(self) -> None:
        cfg = configuration(
            "alfa", "a", primary=["biceps"], stabilizer=["core"],
            description="El bíceps flexiona el codo mientras el core sostiene el tronco y los antebrazos quedan verticales.",
        )
        item = definition(
            "alfa", [cfg],
            description="El Alfa trabaja el bíceps con carga. Segunda frase de relleno. Tercera frase de relleno. Cuarta frase de relleno.",
        )
        result = audit.run_audit(catalog(item))
        self.assertEqual([], findings_of(result, "muscle_claim"))
        self.assertEqual([], findings_of(result, "primary_unmentioned"))


# ---------------------------------------------------------------------------
# Anatomy rules
# ---------------------------------------------------------------------------


def rules_document(**overrides: Any) -> dict[str, Any]:
    base: dict[str, Any] = {
        "schemaVersion": 1,
        "rules": [
            {
                "id": "rows.core",
                "title": "Remos",
                "evidence": "Anatomía básica del gesto.",
                "applies": {"definitionIds": ["remo"]},
                "require": [{"muscle": "rhomboids", "atLeast": "SECONDARY"}],
                "forbid": [{"muscle": "erector_spinae", "roles": ["PRIMARY", "SECONDARY"]}],
                "exact": [{"muscle": "latissimus_dorsi", "role": "PRIMARY"}],
                "forbidPatterns": ["wrist_flexion"],
            }
        ],
        "inheritance": [],
    }
    base.update(overrides)
    return base


class AnatomyRulesTest(unittest.TestCase):
    def test_require_forbid_exact_and_pattern_constraints(self) -> None:
        bad = configuration("remo", "bad", pattern="wrist_flexion", primary=["biceps"], secondary=["erector_spinae"])
        good = configuration("remo", "good", primary=["latissimus_dorsi"], secondary=["rhomboids"], stabilizer=["erector_spinae"])
        result = audit.run_audit(catalog(definition("remo", [bad, good])), rules=rules_document())
        flagged = findings_of(result, "anatomy_rule")
        messages = [entry.message for entry in flagged if entry.configuration_id == "remo__bad"]
        for expected in (
            "rhomboids debe ser al menos SECONDARY",
            "erector_spinae no puede ser SECONDARY",
            "latissimus_dorsi debe ser PRIMARY",
            "wrist_flexion",
        ):
            self.assertTrue(any(expected in message for message in messages), (expected, messages))
        self.assertEqual([], [entry for entry in flagged if entry.configuration_id == "remo__good"])
        self.assertTrue(all(entry.field == "rows.core" for entry in flagged))

    def test_a_curated_ficha_is_judged_on_its_own_anatomy_before_it_is_applied(self) -> None:
        # The compiled profile still lists the old, wrong muscles. The curated ficha corrects them and has not
        # been applied yet: the rules and the muscle claims must read the ficha, or a fix would stay invisible
        # until the next compile and a regression would pass for the same reason.
        compiled = configuration("remo", "default", primary=["biceps"], secondary=["erector_spinae"])
        source = catalog(definition("remo", [compiled]))
        ficha = {
            "status": "CURATED",
            "anatomy": {
                "muscles": [
                    {"id": "latissimus_dorsi", "role": "PRIMARY", "why": "x" * 40, "sources": ["s1"]},
                    {"id": "rhomboids", "role": "SECONDARY", "why": "x" * 40, "sources": ["s1"]},
                ],
                "joints": [],
            },
            "public": {"description": "El dorsal ancho de remo mueve la carga."},
        }
        result = audit.run_audit(source, rules=rules_document(), fichas={"remo": ficha})
        self.assertEqual([], findings_of(result, "anatomy_rule"))
        self.assertEqual([], findings_of(result, "muscle_claim"))
        self.assertEqual([], findings_of(result, "primary_unmentioned"))

        regressed = {
            "status": "CURATED",
            "anatomy": {
                "muscles": [
                    {"id": "biceps", "role": "PRIMARY", "why": "x" * 40, "sources": ["s1"]},
                    {"id": "erector_spinae", "role": "SECONDARY", "why": "x" * 40, "sources": ["s1"]},
                ],
                "joints": [],
            },
        }
        regressed_result = audit.run_audit(source, rules=rules_document(), fichas={"remo": regressed})
        messages = [entry.message for entry in findings_of(regressed_result, "anatomy_rule")]
        self.assertTrue(any("rhomboids debe ser al menos SECONDARY" in message for message in messages), messages)
        self.assertTrue(any("latissimus_dorsi debe ser PRIMARY" in message for message in messages), messages)

        # An override with role NONE drops a muscle for that configuration only.
        overridden = {
            "status": "CURATED",
            "anatomy": {
                "muscles": [{"id": "latissimus_dorsi", "role": "PRIMARY", "why": "x" * 40, "sources": ["s1"]}],
                "joints": [],
                "overrides": {"remo__default": {"muscles": [{"id": "latissimus_dorsi", "role": "NONE", "why": "x" * 40}]}},
            },
        }
        dropped = audit.run_audit(source, rules=rules_document(), fichas={"remo": overridden})
        self.assertTrue(
            any("latissimus_dorsi debe ser PRIMARY" in entry.message for entry in findings_of(dropped, "anatomy_rule"))
        )

    def test_required_pattern_constraint(self) -> None:
        rules = rules_document(
            rules=[
                {
                    "id": "pattern.only",
                    "title": "Patrón",
                    "evidence": "El gesto es un tirón horizontal.",
                    "applies": {"definitionIds": ["remo"]},
                    "requirePatterns": ["horizontal_pull"],
                }
            ]
        )
        wrong = configuration("remo", "wrong", pattern="vertical_pull")
        right = configuration("remo", "right")
        flagged = findings_of(audit.run_audit(catalog(definition("remo", [wrong, right])), rules=rules), "anatomy_rule")
        self.assertEqual({"remo__wrong"}, {entry.configuration_id for entry in flagged})

    def test_joint_constraints(self) -> None:
        rules = rules_document(
            rules=[
                {
                    "id": "joint.knee",
                    "title": "Rodilla",
                    "evidence": "El gesto mueve la rodilla.",
                    "applies": {"movementPatterns": ["knee_flexion"]},
                    "requireJoints": [{"joint": "rodilla", "atLeast": "PRIMARY"}],
                    "forbidJoints": [{"joint": "columna-lumbar", "roles": ["PRIMARY"]}],
                }
            ]
        )
        cfg = configuration(
            "curl", "a", pattern="knee_flexion",
            joints=[{"jointId": "columna-lumbar", "role": "PRIMARY", "actions": ["Estabilización"]}],
        )
        result = audit.run_audit(catalog(definition("curl", [cfg])), rules=rules)
        messages = " | ".join(entry.message for entry in findings_of(result, "anatomy_rule"))
        self.assertIn("rodilla debe ser al menos PRIMARY", messages)
        self.assertIn("columna-lumbar no puede ser PRIMARY", messages)

    def test_selectors_are_anded_and_exceptions_skip_a_configuration(self) -> None:
        rules = rules_document()
        rules["rules"][0]["applies"] = {
            "definitionIds": ["remo"], "equipmentIds": ["barbell"], "selectedOptions": {"grip": "wide"},
        }
        rules["rules"][0]["exceptions"] = [{"configurationIds": ["remo__wide_excepted"], "why": "Agarre amplio con apoyo."}]
        wide = configuration("remo", "wide", equipment="barbell", options={"grip": "wide"}, primary=["biceps"])
        narrow = configuration("remo", "narrow", equipment="barbell", options={"grip": "close"}, primary=["biceps"])
        excepted = configuration("remo", "wide_excepted", equipment="barbell", options={"grip": "wide"}, primary=["biceps"])
        result = audit.run_audit(catalog(definition("remo", [wide, narrow, excepted])), rules=rules)
        self.assertEqual({"remo__wide"}, {entry.configuration_id for entry in findings_of(result, "anatomy_rule")})

    def test_inheritance_reports_differences_unless_declared(self) -> None:
        parent = definition("padre", [configuration("padre", "a", primary=["gluteus_maximus", "hamstrings"], secondary=["quadriceps"])])
        child = definition("hijo", [configuration("hijo", "a", primary=["hamstrings"], secondary=["gluteus_maximus"])])
        strict = rules_document(rules=[], inheritance=[{"id": "inherit.hijo", "child": "hijo", "parent": "padre", "allowedDifferences": []}])
        text = " | ".join(entry.message for entry in findings_of(audit.run_audit(catalog(parent, child), rules=strict), "inheritance"))
        self.assertIn("gluteus_maximus: SECONDARY", text)
        self.assertIn("quadriceps: ninguno", text)
        self.assertNotIn("hamstrings", text)
        relaxed = rules_document(
            rules=[],
            inheritance=[
                {
                    "id": "inherit.hijo",
                    "child": "hijo",
                    "parent": "padre",
                    "allowedDifferences": [
                        {"muscle": "gluteus_maximus", "why": "El apoyo cambia el reparto."},
                        {"muscle": "quadriceps", "why": "No hay extensión de rodilla."},
                    ],
                }
            ],
        )
        self.assertEqual([], findings_of(audit.run_audit(catalog(parent, child), rules=relaxed), "inheritance"))

    def test_rules_file_validation_flags_unknown_references_and_skips_untrusted_rules(self) -> None:
        rules = rules_document(
            rules=[
                {
                    "id": "rows.bad",
                    "title": "Remos",
                    "evidence": "x",
                    "applies": {"definitionIds": ["fantasma"], "movementPatterns": ["sin_uso"]},
                    "require": [{"muscle": "musculo_inventado", "atLeast": "ENORME"}],
                    "requireJoints": [{"joint": "dedo", "atLeast": "PRIMARY"}],
                    "exceptions": [{"definitionIds": ["remo"], "why": ""}],
                },
                {"id": "rows.bad", "title": "", "evidence": "", "applies": {}, "require": [{"muscle": "biceps", "atLeast": "ENORME"}]},
            ],
            inheritance=[{"id": "inherit.bad", "child": "fantasma", "parent": "remo", "allowedDifferences": [{"muscle": "biceps", "why": ""}]}],
        )
        result = audit.run_audit(catalog(definition("remo")), rules=rules)
        messages = " | ".join(entry.message for entry in findings_of(result, "rules_invalid"))
        for expected in (
            "definitionIds: definición inexistente fantasma",
            "músculo fuera de la ontología musculo_inventado",
            "rol inválido ENORME",
            "articulación fuera de la ontología dedo",
            "excepción sin «why»",
            "id duplicado",
            "falta «title»",
            "inheritance.child: definición inexistente fantasma",
            "allowedDifferences requiere músculo válido y «why»",
        ):
            self.assertIn(expected, messages)
        self.assertIn("patrón sin uso sin_uso", " | ".join(entry.message for entry in findings_of(result, "rule_dead")))
        self.assertEqual([], findings_of(result, "anatomy_rule"))  # untrusted rules are not applied

    def test_a_rule_that_selects_nothing_is_reported_as_dead(self) -> None:
        rules = rules_document()
        rules["rules"][0]["applies"] = {"definitionIds": ["remo"], "equipmentIds": ["machine"]}
        dead = findings_of(audit.run_audit(catalog(definition("remo")), rules=rules), "rule_dead")
        self.assertTrue(any("no selecciona ninguna configuración" in entry.message for entry in dead))
        self.assertTrue(all(entry.severity == "WARN" for entry in dead))


# ---------------------------------------------------------------------------
# Allowlist, corpus, fichas
# ---------------------------------------------------------------------------


class AllowlistAndCorpusTest(unittest.TestCase):
    def sentence_catalog(self) -> dict[str, Any]:
        return catalog(
            definition("alfa", [configuration("alfa", "a", execution=[SHARED_CUE])]),
            definition("beta", [configuration("beta", "a", execution=[SHARED_CUE])]),
        )

    def test_allowlist_entry_with_why_suppresses_and_is_counted(self) -> None:
        allowlist = {"entries": [{"check": "shared_sentence", "definitionIds": ["alfa", "beta"], "why": "Pauta canónica compartida a propósito."}]}
        result = audit.run_audit(self.sentence_catalog(), allowlist=allowlist)
        self.assertEqual([], findings_of(result, "shared_sentence"))
        self.assertEqual(2, len([entry for entry in result.allowlisted if entry.check == "shared_sentence"]))

    def test_allowlist_without_why_is_ignored_and_reported(self) -> None:
        allowlist = {"entries": [{"check": "shared_sentence", "definitionIds": ["alfa", "beta"], "why": "  "}]}
        result = audit.run_audit(self.sentence_catalog(), allowlist=allowlist)
        self.assertEqual(2, len(findings_of(result, "shared_sentence")))
        self.assertEqual(1, len(findings_of(result, "allowlist_invalid")))

    def test_allowlist_entry_without_selector_matches_nothing(self) -> None:
        allowlist = {"entries": [{"check": "shared_sentence", "why": "Demasiado amplia para ser una excepción."}]}
        result = audit.run_audit(self.sentence_catalog(), allowlist=allowlist)
        self.assertEqual(2, len(findings_of(result, "shared_sentence")))
        self.assertEqual([], result.allowlisted)

    def test_contains_selector_matches_message_or_excerpt(self) -> None:
        allowlist = {"entries": [{"check": "shared_sentence", "contains": "glúteo arriba", "why": "Pauta canónica compartida."}]}
        result = audit.run_audit(self.sentence_catalog(), allowlist=allowlist)
        self.assertEqual([], findings_of(result, "shared_sentence"))

    def test_warnings_can_be_promoted_to_errors(self) -> None:
        plain = findings_of(audit.run_audit(catalog(definition("alfa"))), "visual_missing")
        self.assertEqual(1, len(plain))
        self.assertEqual("WARN", plain[0].severity)
        promoted = findings_of(
            audit.run_audit(catalog(definition("alfa")), options=audit.Options(warnings_as_errors=True)), "visual_missing"
        )
        self.assertEqual("ERROR", promoted[0].severity)

    def test_legacy_definitions_do_not_pollute_the_curated_corpus(self) -> None:
        catalog_data = self.sentence_catalog()
        fichas = {"alfa": {"status": "CURATED"}, "beta": {"status": "LEGACY"}}
        result = audit.run_audit(catalog_data, fichas=fichas)
        self.assertEqual(["alfa"], result.corpus_ids)
        self.assertEqual(["beta"], result.legacy_ids)
        self.assertEqual([], findings_of(result, "shared_sentence"))
        mixed = audit.run_audit(catalog_data, fichas=fichas, options=audit.Options(include_legacy=True))
        self.assertEqual({"alfa", "beta"}, {entry.definition_id for entry in findings_of(mixed, "shared_sentence")})
        scoped = audit.run_audit(catalog_data, fichas=fichas, scope=["beta"])
        self.assertEqual({"alfa", "beta"}, set(scoped.corpus_ids))
        self.assertEqual({"beta"}, {entry.definition_id for entry in findings_of(scoped, "shared_sentence")})


def complete_ficha(equipment: list[str]) -> dict[str, Any]:
    return {
        "status": "CURATED",
        "sources": [
            {
                "id": "S1", "url": "https://example.org/biomecanica-del-remo",
                "title": "Biomecánica del remo con el torso apoyado",
                "claim": "Describe la extensión del hombro durante el remo con el torso apoyado.",
            },
            {
                "id": "S2", "url": "https://example.org/emg-del-remo",
                "title": "Electromiografía del remo con el torso apoyado",
                "claim": "Registra la activación del dorsal ancho durante el remo apoyado.",
            },
        ],
        "technique": {
            "identity": "Remo horizontal con el torso apoyado en el banco y la barra bajo el banco alto.",
            "setup": ["Barra bajo el banco alto con agarre prono"],
            "keyPositions": ["Pecho pegado al banco durante toda la serie"],
            "mistakes": ["Despegar el pecho del banco", "Encoger los hombros al tirar"],
            "phases": [
                {"name": "Tirón", "description": "Los codos suben por detrás del torso hasta tocar el banco."},
                {"name": "Bajada", "description": "Los brazos se extienden hasta que la barra casi toca el suelo."},
            ],
        },
        "anatomy": {
            "muscles": [
                {
                    "id": "latissimus_dorsi", "role": "PRIMARY", "sources": ["S1"],
                    "why": "Extiende el hombro llevando el codo hacia atrás contra la carga.",
                }
            ],
            "joints": [
                {
                    "id": "glenohumeral", "role": "PRIMARY", "actions": ["Extensión"], "sources": ["S1", "S2"],
                    "why": "El húmero se desplaza hacia atrás del torso durante todo el tirón.",
                }
            ],
        },
        "visual": {
            "base": {
                "camera": "lateral izquierda a la altura del banco",
                "phase": "final del tirón con el pecho apoyado",
                "orientation": "cuerpo mirando a la derecha del encuadre",
                "contacts": "pecho y abdomen sobre el banco, puntas de los pies en el suelo",
                "posture": "columna neutra con la cabeza alineada",
                "load": "barra pasando bajo el banco con discos a ambos lados",
            },
            "byImplement": {name: {"geometry": f"Geometría del implemento {name} con la carga bajo el banco."} for name in equipment},
            "promptCore": {
                name: "Side view of a lifter lying chest down on a high bench pulling a loaded barbell toward the bench, neutral spine, white background"
                for name in equipment
            },
            "forbidden": ["Barra sobre el banco", "Pecho despegado del banco"],
            "qa": ["¿El pecho toca el banco?", "¿La barra pasa bajo el banco?", "¿La columna está neutra?"],
        },
    }


class FichaChecksTest(unittest.TestCase):
    def test_complete_curated_ficha_has_no_ficha_findings(self) -> None:
        item = definition("remo", [configuration("remo", "a", equipment="barbell")])
        result = audit.run_audit(catalog(item), fichas={"remo": complete_ficha(["barbell"])})
        for check in ("ficha_shape", "visual_incomplete", "visual_missing"):
            self.assertEqual([], findings_of(result, check), check)

    def test_missing_visual_fields_and_wrong_implements_are_reported(self) -> None:
        item = definition(
            "remo",
            [configuration("remo", "a", equipment="barbell"), configuration("remo", "b", equipment="dumbbells")],
        )
        ficha = complete_ficha(["barbell"])
        del ficha["visual"]["base"]["camera"]
        ficha["visual"]["promptCore"]["barbell"] = "Una descripción con acentos y ñ que no es ASCII ni inglés, demasiado."
        ficha["visual"]["byImplement"]["kettlebell"] = {"geometry": "Implemento que la definición no tiene en ninguna variante."}
        ficha["visual"]["qa"] = ["¿Solo una pregunta?"]
        result = audit.run_audit(catalog(item), fichas={"remo": ficha})
        fields = {entry.field for entry in findings_of(result, "visual_incomplete")}
        for expected in (
            "visual.base.camera",
            "visual.promptCore.barbell",
            "visual.promptCore.dumbbells",
            "visual.byImplement.dumbbells",
            "visual.byImplement.kettlebell",
            "visual.qa",
        ):
            self.assertIn(expected, fields)

    def test_missing_sources_and_undeclared_citations_are_reported(self) -> None:
        item = definition("remo", [configuration("remo", "a", equipment="barbell")])
        ficha = complete_ficha(["barbell"])
        ficha["sources"] = [{"id": "S1", "url": "https://example.org/uno-solo"}]
        ficha["anatomy"]["muscles"][0]["sources"] = ["S9"]
        ficha["anatomy"]["muscles"][0]["why"] = "Corto."
        ficha["technique"]["mistakes"] = ["Solo un error"]
        result = audit.run_audit(catalog(item), fichas={"remo": ficha})
        messages = " | ".join(f"{entry.field}: {entry.message}" for entry in findings_of(result, "ficha_shape"))
        for expected in (
            "sources: se requieren al menos 2 fuentes",
            "citar ids declarados",
            "why requiere ≥40 caracteres",
            "technique.mistakes",
        ):
            self.assertIn(expected, messages)

    def test_a_visual_variant_must_say_what_looks_different(self) -> None:
        item = definition("remo", [configuration("remo", "a", equipment="barbell")])
        ficha = complete_ficha(["barbell"])
        ficha["visual"]["byVariant"] = {"remo__a": {"difference": "Agarre supino con las palmas hacia el rostro del atleta."}}
        result = audit.run_audit(catalog(item), fichas={"remo": ficha})
        self.assertEqual([], findings_of(result, "visual_incomplete"))
        for broken in ({"remo__a": {"note": "sin difference"}}, {"remo__a": "texto suelto"}, {"remo__a": {"difference": "Corto"}}):
            ficha["visual"]["byVariant"] = broken
            fields = {entry.field for entry in findings_of(audit.run_audit(catalog(item), fichas={"remo": ficha}), "visual_incomplete")}
            self.assertEqual({"visual.byVariant.remo__a.difference"}, fields, broken)
        ficha["visual"]["byVariant"] = {"remo__ghost": {"difference": "Una configuración que la definición no tiene."}}
        fields = {entry.field for entry in findings_of(audit.run_audit(catalog(item), fichas={"remo": ficha}), "visual_incomplete")}
        self.assertEqual({"visual.byVariant"}, fields)

    def test_a_source_without_title_or_claim_is_reported(self) -> None:
        item = definition("remo", [configuration("remo", "a", equipment="barbell")])
        ficha = complete_ficha(["barbell"])
        del ficha["sources"][0]["claim"]
        ficha["sources"][1]["title"] = "Corto"
        messages = [entry.message for entry in findings_of(audit.run_audit(catalog(item), fichas={"remo": ficha}), "ficha_shape")]
        self.assertEqual(2, sum("title (≥10) y claim (≥20)" in message for message in messages), messages)

    def test_every_per_configuration_exception_must_be_justified_and_cited(self) -> None:
        item = definition("remo", [configuration("remo", "a", equipment="barbell")])
        ficha = complete_ficha(["barbell"])
        ficha["anatomy"]["overrides"] = {
            "remo__a": {
                "muscles": [
                    {"id": "biceps", "role": "SECONDARY", "why": "Corto.", "sources": ["S1"]},
                    {"id": "forearm", "role": "STABILIZER", "why": "Sostiene la barra con los flexores de los dedos durante toda la serie.", "sources": ["S9"]},
                    {"id": "latissimus_dorsi", "role": "NONE", "why": "Se retira porque esta configuración no lo recluta en absoluto."},
                ],
                "joints": [
                    {
                        "id": "codo", "role": "SECONDARY", "actions": ["Flexión del codo"], "sources": ["S2"],
                        "why": "El codo se flexiona más porque la barra recorre un rango mayor que en la configuración base.",
                    }
                ],
            }
        }
        fields = {entry.field: entry.message for entry in findings_of(audit.run_audit(catalog(item), fichas={"remo": ficha}), "ficha_shape")}
        self.assertIn("why ≥40", fields["anatomy.overrides.remo__a.muscles[0]"])
        self.assertIn("citar ids declarados", fields["anatomy.overrides.remo__a.muscles[1]"])
        self.assertNotIn("anatomy.overrides.remo__a.muscles[2]", fields, "a removal needs a why but no sources")
        self.assertNotIn("anatomy.overrides.remo__a.joints[0]", fields)

    def test_technique_text_may_name_where_the_body_rests_but_not_an_undeclared_muscle(self) -> None:
        item = definition("remo", [configuration("remo", "a", equipment="barbell")])
        ficha = complete_ficha(["barbell"])
        ficha["technique"]["setup"] = [
            "Pecho, abdomen y glúteos apoyados en el banco durante toda la serie.",
            "El pectoral queda relajado mientras los brazos cuelgan bajo el banco.",
        ]
        claims = findings_of(audit.run_audit(catalog(item), fichas={"remo": ficha}), "muscle_claim")
        self.assertEqual(["ficha.technique.setup[1]"], [entry.field for entry in claims])
        self.assertIn("pectoral", claims[0].message)

    def test_legacy_or_absent_ficha_is_a_warning_not_a_shape_error(self) -> None:
        item = definition("remo")
        absent = audit.run_audit(catalog(item), fichas={"otra": {"status": "CURATED"}}, scope=["remo"])
        self.assertEqual("WARN", findings_of(absent, "visual_missing")[0].severity)
        legacy = audit.run_audit(catalog(item), fichas={"remo": {"status": "LEGACY"}}, options=audit.Options(include_legacy=True))
        self.assertEqual("WARN", findings_of(legacy, "visual_missing")[0].severity)
        self.assertEqual([], findings_of(legacy, "ficha_shape"))

    def test_internal_ficha_text_takes_part_in_the_corpus_except_the_visual_block(self) -> None:
        sentence = "El húmero se desplaza hacia atrás del torso durante todo el tirón completo."
        first, second = complete_ficha(["barbell"]), complete_ficha(["barbell"])
        first["technique"]["phases"][0]["description"] = sentence
        second["technique"]["phases"][0]["description"] = sentence
        definitions = [definition("alfa", [configuration("alfa", "a")]), definition("beta", [configuration("beta", "a")])]
        flagged = findings_of(audit.run_audit(catalog(*definitions), fichas={"alfa": first, "beta": second}), "shared_sentence")
        technique = {(entry.definition_id, entry.field) for entry in flagged if entry.field.startswith("ficha.technique")}
        self.assertIn(("alfa", "ficha.technique.phases[0].description"), technique)
        self.assertIn(("beta", "ficha.technique.phases[0].description"), technique)
        self.assertFalse([entry for entry in flagged if entry.field.startswith("ficha.visual")])
        anatomy = [entry for entry in flagged if entry.field.startswith("ficha.anatomy")]
        self.assertTrue(anatomy)
        self.assertTrue(all(entry.severity == "WARN" for entry in anatomy))


# ---------------------------------------------------------------------------
# CLI and shipped data
# ---------------------------------------------------------------------------


class CliTest(unittest.TestCase):
    def run_main(self, argv: list[str]) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = audit.main(argv)
        return code, out.getvalue(), err.getvalue()

    def write_source(self, root: Path) -> Path:
        source = catalog(
            definition("alfa", [configuration("alfa", "a", execution=[SHARED_CUE])]),
            definition("beta", [configuration("beta", "a", execution=[SHARED_CUE])]),
        )
        path = root / "source.json"
        path.write_text(json.dumps(source, ensure_ascii=False), encoding="utf-8")
        return path

    def isolated_arguments(self, root: Path, source: Path) -> list[str]:
        missing = root / "missing.json"
        return [
            "--source", str(source), "--rules", str(missing), "--allowlist", str(missing),
            "--lexicon", str(missing), "--fichas", str(root / "no-fichas"),
        ]

    def test_report_mode_exits_zero_and_strict_mode_exits_two(self) -> None:
        with temporary_directory() as directory:
            root = Path(directory)
            source = self.write_source(root)
            common = self.isolated_arguments(root, source)
            code, stdout, _ = self.run_main(common + ["--json", str(root / "findings.json"), "--markdown", str(root / "report.md")])
            self.assertEqual(0, code)
            self.assertIn("shared_sentence", stdout)
            written = json.loads((root / "findings.json").read_text(encoding="utf-8"))
            self.assertTrue(any(item["check"] == "shared_sentence" for item in written))
            report = (root / "report.md").read_text(encoding="utf-8")
            self.assertIn("## Hallazgos por chequeo", report)
            self.assertIn("test-revision", report)
            code, _, stderr = self.run_main(common + ["--strict"])
            self.assertEqual(2, code)
            self.assertIn("STRICT", stderr)

    def test_strict_mode_exits_zero_when_the_scope_is_clean(self) -> None:
        with temporary_directory() as directory:
            root = Path(directory)
            path = root / "source.json"
            path.write_text(json.dumps(catalog(definition("alfa")), ensure_ascii=False), encoding="utf-8")
            code, _, _ = self.run_main(self.isolated_arguments(root, path) + ["--strict", "--definitions", "alfa"])
            self.assertEqual(0, code)

    def test_unknown_definition_and_missing_source_are_usage_errors(self) -> None:
        with temporary_directory() as directory:
            root = Path(directory)
            source = self.write_source(root)
            code, _, stderr = self.run_main(self.isolated_arguments(root, source) + ["--definitions", "fantasma"])
            self.assertEqual(1, code)
            self.assertIn("fantasma", stderr)
            code, _, stderr = self.run_main(self.isolated_arguments(root, root / "no-existe.json"))
            self.assertEqual(1, code)
            self.assertIn("error leyendo insumos", stderr)

    def test_malformed_catalogue_is_a_clean_error_not_a_traceback(self) -> None:
        with temporary_directory() as directory:
            root = Path(directory)
            path = root / "broken.json"
            path.write_text(json.dumps({"families": [{"id": "f", "definitions": [{"id": "x", "configurations": [{"profile": {}}]}]}]}), encoding="utf-8")
            code, _, stderr = self.run_main(self.isolated_arguments(root, path))
            self.assertEqual(1, code)
            self.assertIn("datos con forma inesperada", stderr)

    def test_help_documents_the_blocking_flag(self) -> None:
        self.assertIn("--strict", audit.build_parser().format_help())
        with self.assertRaises(SystemExit) as raised, contextlib.redirect_stdout(io.StringIO()):
            audit.main(["--help"])
        self.assertEqual(0, raised.exception.code)


class ShippedDataTest(unittest.TestCase):
    """The rules, lexicon and allowlist that ship in the repository must be valid against the real catalogue."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.source = json.loads(SOURCE.read_text(encoding="utf-8"))
        cls.definitions = audit.load_definitions(cls.source)
        cls.rules = json.loads((CURATION / "anatomy_rules.json").read_text(encoding="utf-8"))

    def test_anatomy_rules_reference_only_things_that_exist(self) -> None:
        problems = [f"{item.field}: {item.message}" for item in audit.check_rules_file(self.rules, self.definitions)]
        self.assertEqual([], problems)

    def test_rule_and_inheritance_ids_are_unique_and_every_exception_is_justified(self) -> None:
        identifiers = [rule["id"] for rule in self.rules["rules"]] + [item["id"] for item in self.rules["inheritance"]]
        self.assertEqual(len(identifiers), len(set(identifiers)))
        for rule in self.rules["rules"]:
            self.assertTrue(rule["evidence"].strip(), rule["id"])
            for exception in rule.get("exceptions", []):
                self.assertTrue(exception["why"].strip(), rule["id"])

    def test_lexicon_patterns_compile_and_are_documented(self) -> None:
        import re

        lexicon = json.loads((CURATION / "quality_lexicon.json").read_text(encoding="utf-8"))
        identifiers = []
        for entry in lexicon["banned"] + lexicon["budgets"]:
            re.compile(entry["pattern"])
            self.assertTrue(entry["why"].strip(), entry["id"])
            identifiers.append(entry["id"])
        for entry in lexicon["budgets"]:
            self.assertGreater(entry["maxDefinitions"], 0)
        self.assertEqual(len(identifiers), len(set(identifiers)))
        self.assertTrue(lexicon["imperativeStarts"])

    def test_allowlist_entries_are_well_formed(self) -> None:
        allowlist = json.loads((CURATION / "quality_allowlist.json").read_text(encoding="utf-8"))
        self.assertEqual([], audit.validate_allowlist(allowlist))

    def test_full_baseline_renders_and_documents_every_emitted_check(self) -> None:
        lexicon = json.loads((CURATION / "quality_lexicon.json").read_text(encoding="utf-8"))
        result = audit.run_audit(self.source, rules=self.rules, lexicon=lexicon)
        emitted = {item.check for item in result.findings}
        self.assertLessEqual(emitted, set(audit.CHECK_DOCS), emitted - set(audit.CHECK_DOCS))
        self.assertGreater(len(result.findings), 0)
        report = audit.render_markdown(result, "Línea base de prueba", self.source["catalogRevision"])
        for section in ("## Cobertura medida", "## Texto compartido entre definiciones distintas", "## Hallazgos por chequeo",
                        "## Variedad por tipo de texto", "## Qué mide cada chequeo", "## Decisiones de calibración"):
            self.assertIn(section, report)
        profile = result.metrics["sharedSentenceProfile"]
        self.assertGreater(profile["distinctSharedSentences"], 0)
        self.assertGreaterEqual(profile["maxDefinitionsSharingOne"], 2)
        self.assertEqual([(5, 3), (5, 4), (6, 4), (8, 4)], [(row["n"], row["minContent"]) for row in result.metrics["sharedNgramProfile"]])
        self.assertTrue(audit.is_visible_field("profile.executionCues[0]"))
        self.assertFalse(audit.is_visible_field("profile.muscleNotes[biceps]"))

    def test_real_catalogue_audit_is_read_only_and_respects_the_scope(self) -> None:
        watched = [SOURCE, CURATION / "anatomy_rules.json", CURATION / "quality_lexicon.json", CURATION / "quality_allowlist.json"]
        before = [path.stat().st_mtime_ns for path in watched]
        lexicon = json.loads((CURATION / "quality_lexicon.json").read_text(encoding="utf-8"))
        result = audit.run_audit(self.source, rules=self.rules, lexicon=lexicon, scope=["seal_row"])
        self.assertEqual(["seal_row"], result.report_ids)
        self.assertLessEqual({item.definition_id for item in result.findings if item.definition_id}, {"seal_row"})
        self.assertEqual(len(self.definitions), len(result.corpus_ids))
        self.assertEqual(before, [path.stat().st_mtime_ns for path in watched])


if __name__ == "__main__":
    unittest.main()
