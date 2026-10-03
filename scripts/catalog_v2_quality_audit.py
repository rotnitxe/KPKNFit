#!/usr/bin/env python3
"""Read-only editorial quality audit for the exercise catalog v2.

Why this exists
---------------
`catalog_v2_gate.py` measures *form* (coverage, mirrors, lengths, first
sentences) and reports READY on a catalogue whose prose is formulaic, recycled
between exercises or anatomically wrong. This audit measures *substance*:

* sentences and long phrases shared between different exercises;
* template skeletons (same sentence with only the muscle/implement swapped);
* sentences repeated between the fields of one configuration;
* banned clichés, a per-catalogue budget for filler words, grammar slips;
* description/cue shape (sentence and word limits);
* implements and muscles named in the text that the data does not declare;
* anatomical rules and parent/child inheritance (`curation/anatomy_rules.json`);
* internal exercise sheets (`curation/fichas/*.json`): shape, sources and the
  per-implement visual brief used to generate demonstration images.

It never writes catalogue files. Reports are written only to the explicit
`--json` / `--markdown` paths.

Corpus rules
------------
With no fichas on disk every definition is compared with every other one
(baseline mode). Once fichas exist, only CURATED definitions (plus the
definitions requested with `--definitions`) form the comparison corpus, so a
freshly curated text is never blamed for sentences that legacy text still
carries and that will be rewritten later.

Exit codes: 0 ok, 1 usage/data error, 2 blocking findings with `--strict`.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Iterator

try:
    from catalog_v2_ontology import JOINT_IDS, MUSCLE_IDS, MUSCLE_LISTS, ROLES
except ModuleNotFoundError:  # loaded by file path (tests) without scripts/ on sys.path
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from catalog_v2_ontology import JOINT_IDS, MUSCLE_IDS, MUSCLE_LISTS, ROLES

ROOT = Path(__file__).resolve().parents[1]
V2 = ROOT / "catalog" / "exercises" / "v2"
SOURCE = V2 / "source" / "catalog_v2.json"
CURATION = V2 / "curation"
RULES = CURATION / "anatomy_rules.json"
ALLOWLIST = CURATION / "quality_allowlist.json"
LEXICON = CURATION / "quality_lexicon.json"
FICHAS = CURATION / "fichas"

ERROR = "ERROR"
WARN = "WARN"
ROLE_RANK = {"NONE": 0, "STABILIZER": 1, "SECONDARY": 2, "PRIMARY": 3}

# ---------------------------------------------------------------------------
# Text kinds
# ---------------------------------------------------------------------------

K_DEF = "def_description"
K_CFG = "cfg_description"
K_SETUP = "cue_setup"
K_EXEC = "cue_execution"
K_FICHA_TECH = "ficha_technique"
K_FICHA_ANAT = "ficha_anatomy"
K_FICHA_VIS = "ficha_visual"

DESCRIPTION_KINDS = frozenset({K_DEF, K_CFG})
CUE_KINDS = frozenset({K_SETUP, K_EXEC})
PUBLIC_KINDS = DESCRIPTION_KINDS | CUE_KINDS
# Retired-in-F1 fields: still measured in baseline mode.
LEGACY_KINDS = frozenset(
    {
        "technique", "rationale", "benefit", "mistake", "muscle_note", "joint_note", "progression",
        "regression", "mobility", "objective", "intent", "risk", "precaution",
    }
)
INTRA_KINDS = DESCRIPTION_KINDS | CUE_KINDS | {"technique", "rationale", "benefit", "mistake"}
CROSS_EXEMPT = frozenset({K_FICHA_VIS})
CROSS_SOFT = frozenset({K_FICHA_ANAT})
SKELETON_IN_DEFINITION_KINDS = frozenset({K_CFG, "technique", "rationale", "benefit"})

PROFILE_FIELDS: tuple[tuple[str, str], ...] = (
    ("description", K_CFG),
    ("techniqueSummary", "technique"),
    ("variantRationale", "rationale"),
    ("benefits", "benefit"),
    ("setupCues", K_SETUP),
    ("executionCues", K_EXEC),
    ("commonMistakes", "mistake"),
)
RICH_FIELDS: tuple[tuple[str, str], ...] = (
    ("coaching.progressions", "progression"),
    ("coaching.regressions", "regression"),
    ("coaching.relevantMobility", "mobility"),
    ("programming.objectives", "objective"),
    ("replacement.preservesIntent", "intent"),
    ("safety.risks", "risk"),
    ("safety.precautions", "precaution"),
)

# ---------------------------------------------------------------------------
# Vocabulary (folded: lowercase, no accents)
# ---------------------------------------------------------------------------

STOPWORDS = frozenset(
    """a al algo algun alguna algunas alguno algunos ante antes aqui asi aun aunque bajo cada casi como con contra
    cual cuales cuando de del desde donde dos durante e el ella ellas ello ellos en entre era eran es esa esas ese
    eso esos esta estan estar este esto estos fue fueron ha han hasta hay la las le les lo los mas me mi mientras
    misma mismas mismo mismos muy ni no nos o os otra otras otro otros para pero poco por porque que quien se
    segun ser si sin sobre solo son su sus tambien tan te tiene tienen todo todos tras tu tus un una unas uno unos
    y ya""".split()
)

# muscle id -> aliases. An alias listed under several ids is ambiguous: any of them satisfies a claim.
MUSCLE_ALIASES: dict[str, tuple[str, ...]] = {
    "pectoralis": ("pectoral", "pectorales", "pectoral mayor", "pectoral menor"),
    "latissimus_dorsi": ("dorsal ancho", "dorsales anchos", "dorsal", "dorsales"),
    "trapezius": ("trapecio", "trapecios", "trapecio superior", "trapecio medio", "trapecio inferior"),
    "rhomboids": ("romboides",),
    "deltoid": (
        "deltoides", "deltoide", "deltoides anterior", "deltoides posterior", "deltoides lateral",
        "deltoides medio", "deltoides frontal",
    ),
    "biceps": ("biceps", "biceps braquial"),
    "triceps": ("triceps", "triceps braquial"),
    "forearm": ("antebrazo", "antebrazos"),
    "quadriceps": ("cuadriceps", "recto femoral", "vasto lateral", "vasto medial"),
    "hamstrings": ("isquiosurales", "isquiosural", "isquios", "isquio", "isquiotibiales", "isquiotibial", "femorales"),
    "gluteus_maximus": ("gluteo mayor", "gluteos mayores", "gluteo maximo", "gluteo", "gluteos"),
    "gluteus_medius": ("gluteo medio", "gluteos medios", "gluteo menor", "gluteo", "gluteos"),
    "adductors": ("aductores", "aductor"),
    "hip_flexors": ("flexores de la cadera", "flexores de cadera", "psoas", "iliopsoas"),
    "calves": ("gemelos", "gemelo", "pantorrillas", "pantorrilla", "soleo"),
    "tibialis_anterior": ("tibial anterior", "tibial"),
    "erector_spinae": ("erectores espinales", "erectores", "erector espinal", "espalda baja", "lumbares"),
    "abdominals": ("abdominales", "abdomen", "recto abdominal", "oblicuos", "oblicuo", "transverso del abdomen"),
    "core": ("core", "zona media", "faja abdominal", "espalda baja", "lumbares", "abdomen", "oblicuos"),
    "neck": ("cuello", "esternocleidomastoideo"),
    "tensor_fasciae_latae": ("tensor de la fascia lata", "tfl"),
}
# Aliases that name a body position in prose as often as a muscle: never an "undeclared muscle" claim.
POSITIONAL_ALIASES = frozenset({"antebrazo", "antebrazos", "cuello", "espalda baja", "lumbares", "tibial"})
# The internal technique text also says where the body rests ("pecho y abdomen apoyados", "glúteos sobre el banco"):
# there those words are positions, not muscle claims. Public copy keeps the stricter set.
TECHNIQUE_POSITIONAL_ALIASES = POSITIONAL_ALIASES | frozenset({"abdomen", "gluteo", "gluteos"})

EQUIPMENT_ALIASES = (
    "barra hexagonal", "barra de seguridad", "barra ez", "barra h", "barra t", "barra", "barras", "mancuerna",
    "mancuernas", "kettlebell", "kettlebells", "pesa rusa", "pesas rusas", "polea", "poleas", "cable", "cables",
    "cuerda", "maquina smith", "smith", "maquina", "maquinas", "banda", "bandas", "banda elastica",
    "bandas elasticas", "goma", "gomas", "disco", "discos", "placa", "placas", "trx", "sliders", "slider",
    "deslizadores", "balon", "rodillo",
)
JOINT_ALIASES = (
    "hombro", "hombros", "codo", "codos", "muneca", "munecas", "cadera", "caderas", "rodilla", "rodillas",
    "tobillo", "tobillos", "escapula", "escapulas", "omoplato", "omoplatos", "columna", "pelvis", "tronco",
    "torso",
)
NUMBER_WORDS = ("uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "doce")

# (equipment label, pattern, equipment ids where the word is legitimate)
IMPLEMENT_PATTERNS: tuple[tuple[str, re.Pattern[str], frozenset[str]], ...] = (
    ("mancuernas", re.compile(r"\bmancuernas?\b"), frozenset({"dumbbells"})),
    ("kettlebell", re.compile(r"\bkettlebells?\b|\bpesas? rusas?\b"), frozenset({"kettlebell"})),
    ("polea/cable", re.compile(r"\bpoleas?\b|\bcables?\b"), frozenset({"cable", "machine"})),
    ("smith", re.compile(r"\bsmith\b"), frozenset({"smith_machine"})),
    ("banda", re.compile(r"\bbandas?\b|\bgomas?\b"), frozenset({"band"})),
    ("trx", re.compile(r"\btrx\b"), frozenset({"trx"})),
    ("sliders", re.compile(r"\bsliders?\b|\bdeslizadores\b"), frozenset({"sliders"})),
    (
        "barra",
        re.compile(r"\bbarras?\b"),
        frozenset(
            {
                "barbell", "ez_bar", "hex_bar", "safety_bar", "t_bar", "h_bar", "smith_machine", "bodyweight",
                "plate", "trx", "ghd", "wrist_roller", "ab_wheel", "machine", "cable",
            }
        ),
    ),
    ("maquina", re.compile(r"\bmaquinas?\b"), frozenset({"machine", "smith_machine", "cable"})),
)

# ---------------------------------------------------------------------------
# Folding / tokenising
# ---------------------------------------------------------------------------

_WORD = re.compile(r"[a-z0-9]+")
_SENTENCE_SPLIT = re.compile(r"(?<=[.!?\u2026])\s+|\n+")
_LABEL_PREFIX = re.compile(
    r"^\s*(principal|secundari[oa]|estabilizador[a]?|preparaci[oó]n)\s*:\s*", re.IGNORECASE
)


def fold(text: str) -> str:
    decomposed = unicodedata.normalize("NFKD", text.casefold())
    return "".join(ch for ch in decomposed if not unicodedata.combining(ch))


def words(text: str) -> list[str]:
    return _WORD.findall(fold(text))


def split_sentences(text: str) -> list[str]:
    cleaned = _LABEL_PREFIX.sub("", text.strip())
    return [part.strip() for part in _SENTENCE_SPLIT.split(cleaned) if part.strip()]


def content_count(tokens: Iterable[str]) -> int:
    return sum(1 for token in tokens if token not in STOPWORDS and len(token) > 2 and not token.startswith("zz"))


def _alias_regex(aliases: Iterable[str]) -> re.Pattern[str]:
    ordered = sorted(set(aliases), key=lambda alias: (-len(alias), alias))
    return re.compile(r"\b(?:" + "|".join(re.escape(alias).replace(r"\ ", r"\s+") for alias in ordered) + r")\b")


_ALL_MUSCLE_ALIASES = tuple(alias for aliases in MUSCLE_ALIASES.values() for alias in aliases)
_MASKS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("zzm", _alias_regex(_ALL_MUSCLE_ALIASES)),
    ("zze", _alias_regex(EQUIPMENT_ALIASES)),
    ("zzj", _alias_regex(JOINT_ALIASES)),
    ("zzn", re.compile(r"\b(?:\d+|" + "|".join(NUMBER_WORDS) + r")\b")),
)
_ALIAS_TO_IDS: dict[str, frozenset[str]] = {}
for _muscle, _aliases in MUSCLE_ALIASES.items():
    for _alias in _aliases:
        _ALIAS_TO_IDS[_alias] = _ALIAS_TO_IDS.get(_alias, frozenset()) | {_muscle}
_MUSCLE_MENTION = _alias_regex(_ALIAS_TO_IDS)


def mask_tokens(folded_sentence: str) -> tuple[str, ...]:
    masked = folded_sentence
    for placeholder, pattern in _MASKS:
        masked = pattern.sub(f" {placeholder} ", masked)
    tokens = _WORD.findall(masked)
    collapsed: list[str] = []
    for token in tokens:
        if collapsed and collapsed[-1] == token and token.startswith("zz"):
            continue
        collapsed.append(token)
    return tuple(collapsed)


def muscle_mentions(folded_sentence: str) -> list[tuple[str, frozenset[str]]]:
    return [(match.group(0), _ALIAS_TO_IDS[re.sub(r"\s+", " ", match.group(0))]) for match in _MUSCLE_MENTION.finditer(folded_sentence)]


# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class Finding:
    check: str
    severity: str
    definition_id: str
    configuration_id: str | None
    field: str | None
    message: str
    detail: str = ""

    def as_dict(self) -> dict[str, Any]:
        return {
            "check": self.check,
            "severity": self.severity,
            "definitionId": self.definition_id,
            "configurationId": self.configuration_id,
            "field": self.field,
            "message": self.message,
            "detail": self.detail,
        }

    def sort_key(self) -> tuple[str, str, str, str, str, str]:
        return (
            self.check, self.definition_id, self.configuration_id or "", self.field or "", self.message,
            self.detail,
        )


@dataclass(frozen=True)
class Unit:
    definition_id: str
    configuration_id: str | None
    field: str
    kind: str
    text: str


@dataclass(frozen=True)
class Sentence:
    unit: Unit
    index: int
    text: str
    tokens: tuple[str, ...]
    folded: str


@dataclass
class Configuration:
    id: str
    definition_id: str
    equipment_id: str
    pattern: str
    options: dict[str, Any]
    profile: dict[str, Any]
    roles: dict[str, str]
    joints: dict[str, str]


@dataclass
class Definition:
    id: str
    family_id: str
    name: str
    description: str
    default_configuration_id: str | None
    configurations: list[Configuration]

    @property
    def equipment_ids(self) -> frozenset[str]:
        return frozenset(configuration.equipment_id for configuration in self.configurations)

    def default_configuration(self) -> Configuration:
        for configuration in self.configurations:
            if configuration.id == self.default_configuration_id:
                return configuration
        return self.configurations[0]


@dataclass
class Options:
    ngram: int = 8
    min_content: int = 4
    dense_ngram: int = 5
    dense_min_content: int = 4
    ngram_min_definitions: int = 2
    skeleton_min_definitions: int = 3
    opening_min_configurations: int = 6
    opening_min_definitions: int = 3
    min_sentence_tokens: int = 5
    warnings_as_errors: bool = False
    include_legacy: bool = False


@dataclass
class AuditResult:
    findings: list[Finding]
    allowlisted: list[Finding]
    metrics: dict[str, Any]
    report_ids: list[str]
    corpus_ids: list[str]
    legacy_ids: list[str]
    options: Options


# ---------------------------------------------------------------------------
# Loading
# ---------------------------------------------------------------------------


def role_map(profile: dict[str, Any]) -> dict[str, str]:
    roles: dict[str, str] = {}
    for key, role in MUSCLE_LISTS:
        for muscle in profile.get(key, []) or []:
            roles.setdefault(muscle, role)
    return roles


def joint_map(profile: dict[str, Any]) -> dict[str, str]:
    joints: dict[str, str] = {}
    for entry in profile.get("jointInvolvement", []) or []:
        joint_id = entry.get("jointId")
        if joint_id:
            joints.setdefault(joint_id, entry.get("role", ""))
    return joints


def load_definitions(source: dict[str, Any]) -> dict[str, Definition]:
    definitions: dict[str, Definition] = {}
    for family in source.get("families", []):
        for raw in family.get("definitions", []):
            configurations: list[Configuration] = []
            for config in raw.get("configurations", []):
                profile = config.get("profile", {}) or {}
                configurations.append(
                    Configuration(
                        id=config["id"],
                        definition_id=raw["id"],
                        equipment_id=profile.get("equipmentId", ""),
                        pattern=profile.get("movementPatternId", ""),
                        options=dict(config.get("selectedOptions", {}) or {}),
                        profile=profile,
                        roles=role_map(profile),
                        joints=joint_map(profile),
                    )
                )
            definitions[raw["id"]] = Definition(
                id=raw["id"],
                family_id=raw.get("familyId") or family.get("id", ""),
                name=raw.get("canonicalName", ""),
                description=raw.get("description", "") or "",
                default_configuration_id=raw.get("defaultConfigurationId"),
                configurations=configurations,
            )
    return definitions


def dig(obj: Any, dotted: str) -> Any:
    for part in dotted.split("."):
        if not isinstance(obj, dict):
            return None
        obj = obj.get(part)
    return obj


def iter_strings(value: Any, path: str = "") -> Iterator[tuple[str, str]]:
    if isinstance(value, str):
        if value.strip():
            yield path, value
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from iter_strings(item, f"{path}[{index}]")
    elif isinstance(value, dict):
        for key in sorted(value):
            yield from iter_strings(value[key], f"{path}.{key}" if path else key)


def _list_units(definition_id: str, configuration_id: str | None, field_name: str, kind: str, value: Any) -> Iterator[Unit]:
    if isinstance(value, str):
        if value.strip():
            yield Unit(definition_id, configuration_id, field_name, kind, value)
    elif isinstance(value, list):
        for index, item in enumerate(value):
            if isinstance(item, str) and item.strip():
                yield Unit(definition_id, configuration_id, f"{field_name}[{index}]", kind, item)


# Since F1 ``replacement.preservesIntent`` holds the derived key "<pattern>:<muscle>" (a machine token that the
# Android resolver compares, not prose). Pre-F1 snapshots still carry prose there, and that is still audited.
_MACHINE_INTENT = re.compile(r"^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$")


def source_units(definitions: dict[str, Definition]) -> Iterator[Unit]:
    for definition in definitions.values():
        if definition.description.strip():
            yield Unit(definition.id, None, "definition.description", K_DEF, definition.description)
        for configuration in definition.configurations:
            profile = configuration.profile
            for key, kind in PROFILE_FIELDS:
                yield from _list_units(definition.id, configuration.id, f"profile.{key}", kind, profile.get(key))
            rich = profile.get("richMetadata") or {}
            for key, kind in RICH_FIELDS:
                for unit in _list_units(
                    definition.id, configuration.id, f"richMetadata.{key}", kind, dig(rich, key)
                ):
                    if kind == "intent" and _MACHINE_INTENT.match(unit.text.strip()):
                        continue
                    yield unit
            for note in profile.get("muscleNotes", []) or []:
                if isinstance(note, dict) and str(note.get("note", "")).strip():
                    yield Unit(
                        definition.id, configuration.id, f"profile.muscleNotes[{note.get('muscleId')}]",
                        "muscle_note", note["note"],
                    )
            for entry in profile.get("jointInvolvement", []) or []:
                if isinstance(entry, dict) and str(entry.get("note", "")).strip():
                    yield Unit(
                        definition.id, configuration.id, f"profile.jointInvolvement[{entry.get('jointId')}].note",
                        "joint_note", entry["note"],
                    )


def ficha_units(fichas: dict[str, dict[str, Any]]) -> Iterator[Unit]:
    for definition_id, ficha in fichas.items():
        for path, text in iter_strings(ficha.get("technique")):
            yield Unit(definition_id, None, f"ficha.technique.{path}", K_FICHA_TECH, text)
        anatomy = ficha.get("anatomy") or {}
        for path, text in iter_strings(anatomy):
            if path.endswith(".why") or path == "why" or ".why" in path:
                yield Unit(definition_id, None, f"ficha.anatomy.{path}", K_FICHA_ANAT, text)
        for path, text in iter_strings(ficha.get("visual")):
            yield Unit(definition_id, None, f"ficha.visual.{path}", K_FICHA_VIS, text)


def load_fichas(directory: Path) -> dict[str, dict[str, Any]]:
    """definition id -> ficha body (with `status` and `familyId` filled in)."""
    fichas: dict[str, dict[str, Any]] = {}
    if not directory.is_dir():
        return fichas
    for path in sorted(directory.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        family_id = payload.get("familyId", path.stem)
        for definition_id, body in (payload.get("definitions") or {}).items():
            merged = dict(body)
            merged.setdefault("status", "LEGACY")
            merged["familyId"] = family_id
            fichas[definition_id] = merged
    return fichas


def build_sentences(units: Iterable[Unit]) -> list[Sentence]:
    result: list[Sentence] = []
    for unit in units:
        for index, text in enumerate(split_sentences(unit.text)):
            folded = fold(text)
            tokens = tuple(_WORD.findall(folded))
            if tokens:
                result.append(Sentence(unit, index, text, tokens, folded))
    return result


def excerpt(text: str, limit: int = 160) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    return text if len(text) <= limit else text[: limit - 1] + "\u2026"


# ---------------------------------------------------------------------------
# Context
# ---------------------------------------------------------------------------


@dataclass
class Context:
    definitions: dict[str, Definition]
    fichas: dict[str, dict[str, Any]]
    options: Options
    lexicon: dict[str, Any]
    report_ids: frozenset[str]
    corpus_ids: frozenset[str]

    def unit_in_report(self, unit: Unit) -> bool:
        return unit.definition_id in self.report_ids

    def configuration(self, unit: Unit) -> Configuration | None:
        if unit.configuration_id is None:
            return None
        definition = self.definitions.get(unit.definition_id)
        if definition is None:
            return None
        for configuration in definition.configurations:
            if configuration.id == unit.configuration_id:
                return configuration
        return None


def _finding(check: str, severity: str, unit: Unit, message: str, detail: str = "") -> Finding:
    return Finding(check, severity, unit.definition_id, unit.configuration_id, unit.field, message, excerpt(detail))


# ---------------------------------------------------------------------------
# Checks: repetition
# ---------------------------------------------------------------------------


def _jaccard(left: tuple[str, ...], right: tuple[str, ...]) -> float:
    a, b = set(left), set(right)
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def check_intra_duplicates(sentences: list[Sentence], ctx: Context) -> list[Finding]:
    """A sentence repeated between the fields of one configuration (or with its definition description)."""
    by_config: dict[tuple[str, str], list[Sentence]] = defaultdict(list)
    definition_sentences: dict[str, list[Sentence]] = defaultdict(list)
    for sentence in sentences:
        unit = sentence.unit
        if unit.kind not in INTRA_KINDS or unit.definition_id not in ctx.report_ids:
            continue
        if unit.configuration_id is None:
            definition_sentences[unit.definition_id].append(sentence)
        else:
            by_config[(unit.definition_id, unit.configuration_id)].append(sentence)
    findings: list[Finding] = []
    minimum = ctx.options.min_sentence_tokens
    for (definition_id, configuration_id), own in sorted(by_config.items()):
        bag = [s for s in own + definition_sentences.get(definition_id, []) if len(s.tokens) >= minimum]
        flagged: set[tuple[str, int]] = set()
        for position, first in enumerate(bag):
            for second in bag[position + 1 :]:
                if first.unit.field == second.unit.field and first.unit.configuration_id is None:
                    continue
                if first.unit.field == second.unit.field and first.index == second.index:
                    continue
                same = first.tokens == second.tokens
                near = (not same) and min(len(first.tokens), len(second.tokens)) >= 8 and _jaccard(first.tokens, second.tokens) >= 0.85
                if not (same or near):
                    continue
                target = second if second.unit.configuration_id is not None else first
                key = (target.unit.field, target.index)
                if key in flagged:
                    continue
                flagged.add(key)
                other = first if target is second else second
                findings.append(
                    _finding(
                        "intra_config_duplicate",
                        ERROR,
                        target.unit,
                        f"{'repite' if same else 'casi repite'} una frase de {other.unit.field}",
                        target.text,
                    )
                )
    return findings


def check_shared_sentences(sentences: list[Sentence], ctx: Context) -> list[Finding]:
    owners: dict[tuple[str, ...], set[str]] = defaultdict(set)
    minimum = ctx.options.min_sentence_tokens
    for sentence in sentences:
        if sentence.unit.kind in CROSS_EXEMPT or len(sentence.tokens) < minimum:
            continue
        owners[sentence.tokens].add(sentence.unit.definition_id)
    findings: list[Finding] = []
    for sentence in sentences:
        unit = sentence.unit
        if unit.kind in CROSS_EXEMPT or len(sentence.tokens) < minimum or unit.definition_id not in ctx.report_ids:
            continue
        others = sorted(owners[sentence.tokens] - {unit.definition_id})
        if others:
            severity = WARN if unit.kind in CROSS_SOFT else ERROR
            findings.append(
                _finding(
                    "shared_sentence",
                    severity,
                    unit,
                    f"frase idéntica en {len(others)} otra(s) definición(es): {', '.join(others[:4])}",
                    sentence.text,
                )
            )
    return findings


def _longest_run(flags: list[bool]) -> tuple[int, int] | None:
    best: tuple[int, int] | None = None
    run_start: int | None = None
    for index, flag in enumerate(flags + [False]):
        if flag and run_start is None:
            run_start = index
        elif not flag and run_start is not None:
            if best is None or index - run_start > best[1] - best[0]:
                best = (run_start, index)
            run_start = None
    return best


def check_shared_ngrams(sentences: list[Sentence], ctx: Context) -> list[Finding]:
    """Two blocking profiles: a long fragment (n words, few content words needed) and a short but dense one.

    A 5-word fragment with 4 content words («pierna delantera frena empuja peso») is recycled prose; a 5-word fragment
    with 3 («con los pies al ancho») is a natural collocation and is only reported as a metric.
    """
    options = ctx.options
    profiles = ((options.ngram, options.min_content), (options.dense_ngram, options.dense_min_content))
    whole: dict[tuple[str, ...], set[str]] = defaultdict(set)
    for sentence in sentences:
        if sentence.unit.kind not in CROSS_EXEMPT:
            whole[sentence.tokens].add(sentence.unit.definition_id)
    pool = [s for s in sentences if s.unit.kind not in CROSS_EXEMPT | CROSS_SOFT]
    best_by_position: dict[int, tuple[tuple[str, ...], list[str]]] = {}
    for n, min_content in profiles:
        owners: dict[tuple[str, ...], set[str]] = defaultdict(set)
        for sentence in pool:
            tokens = sentence.tokens
            for start in range(len(tokens) - n + 1):
                gram = tokens[start : start + n]
                if content_count(gram) >= min_content:
                    owners[gram].add(sentence.unit.definition_id)
        shared = {gram for gram, ids in owners.items() if len(ids) >= options.ngram_min_definitions}
        for position, sentence in enumerate(pool):
            if sentence.unit.definition_id not in ctx.report_ids or len(whole[sentence.tokens]) > 1:
                continue  # out of scope, or already reported as a shared sentence
            tokens = sentence.tokens
            covered = [False] * len(tokens)
            for start in range(len(tokens) - n + 1):
                if tokens[start : start + n] in shared:
                    for index in range(start, start + n):
                        covered[index] = True
            best = _longest_run(covered)
            if best is None:
                continue
            run = tokens[best[0] : best[1]]
            known = best_by_position.get(position)
            if known is None or len(run) > len(known[0]):
                others = sorted(owners[run[:n]] - {sentence.unit.definition_id})
                best_by_position[position] = (run, others)
    findings: list[Finding] = []
    for position in sorted(best_by_position):
        run, others = best_by_position[position]
        sentence = pool[position]
        findings.append(
            _finding(
                "shared_ngram",
                ERROR,
                sentence.unit,
                f"fragmento de {len(run)} palabras compartido con {', '.join(others[:4]) or 'otra definición'}: «{' '.join(run)}»",
                sentence.text,
            )
        )
    return findings


def _skeleton(sentence: Sentence) -> tuple[str, ...] | None:
    masked = mask_tokens(sentence.folded)
    if len(masked) < 6 or content_count(masked) < 3:
        return None
    return masked


def check_templates(sentences: list[Sentence], ctx: Context) -> list[Finding]:
    findings: list[Finding] = []
    skeleton_owners: dict[tuple[str, ...], set[str]] = defaultdict(set)
    skeletons: dict[int, tuple[str, ...]] = {}
    for position, sentence in enumerate(sentences):
        if sentence.unit.kind in CROSS_EXEMPT | CROSS_SOFT:
            continue
        masked = _skeleton(sentence)
        if masked is None:
            continue
        skeletons[position] = masked
        skeleton_owners[masked].add(sentence.unit.definition_id)
    for position, masked in skeletons.items():
        sentence = sentences[position]
        unit = sentence.unit
        if unit.definition_id not in ctx.report_ids:
            continue
        owners = skeleton_owners[masked]
        if len(owners) >= ctx.options.skeleton_min_definitions:
            findings.append(
                _finding(
                    "template_skeleton",
                    ERROR,
                    unit,
                    f"misma plantilla en {len(owners)} definiciones con solo músculo/implemento cambiados",
                    sentence.text,
                )
            )
    # Same skeleton between sibling configurations of one definition (the delta must say something different).
    grouped: dict[tuple[str, str, tuple[str, ...]], list[Sentence]] = defaultdict(list)
    for position, masked in skeletons.items():
        sentence = sentences[position]
        unit = sentence.unit
        if unit.kind in SKELETON_IN_DEFINITION_KINDS and unit.configuration_id and unit.definition_id in ctx.report_ids:
            grouped[(unit.definition_id, unit.kind, masked)].append(sentence)
    for (definition_id, kind, _masked), group in sorted(grouped.items(), key=lambda item: (item[0][0], item[0][1])):
        configuration_ids = {s.unit.configuration_id for s in group}
        if len(configuration_ids) < 2:
            continue
        for sentence in group[1:]:
            if sentence.unit.configuration_id == group[0].unit.configuration_id:
                continue
            findings.append(
                _finding(
                    "template_skeleton_in_definition",
                    ERROR,
                    sentence.unit,
                    f"misma frase que {group[0].unit.configuration_id} cambiando solo el implemento",
                    sentence.text,
                )
            )
    # Openings: the first masked words of configuration descriptions must not repeat across the catalogue.
    openings: dict[tuple[str, ...], set[str]] = defaultdict(set)
    opening_defs: dict[tuple[str, ...], set[str]] = defaultdict(set)
    first_sentences: list[tuple[Sentence, tuple[str, ...]]] = []
    for sentence in sentences:
        unit = sentence.unit
        if unit.kind != K_CFG or sentence.index != 0 or unit.configuration_id is None:
            continue
        masked = mask_tokens(sentence.folded)
        if len(masked) < 4:
            continue
        opening = masked[:4]
        if content_count(opening) < 2:
            continue
        openings[opening].add(unit.configuration_id)
        opening_defs[opening].add(unit.definition_id)
        first_sentences.append((sentence, opening))
    for sentence, opening in first_sentences:
        if sentence.unit.definition_id not in ctx.report_ids:
            continue
        if (
            len(openings[opening]) >= ctx.options.opening_min_configurations
            and len(opening_defs[opening]) >= ctx.options.opening_min_definitions
        ):
            findings.append(
                _finding(
                    "template_opening",
                    ERROR,
                    sentence.unit,
                    f"apertura «{' '.join(opening)}» usada en {len(openings[opening])} configuraciones de {len(opening_defs[opening])} definiciones",
                    sentence.text,
                )
            )
    return findings


# ---------------------------------------------------------------------------
# Checks: lexicon, grammar, shape
# ---------------------------------------------------------------------------


def check_lexicon(sentences: list[Sentence], ctx: Context) -> list[Finding]:
    findings: list[Finding] = []
    lexicon = ctx.lexicon
    for entry in lexicon.get("banned", []):
        pattern = re.compile(entry["pattern"])
        kinds = frozenset(entry.get("kinds") or PUBLIC_KINDS | {K_FICHA_TECH})
        for sentence in sentences:
            unit = sentence.unit
            if unit.kind in kinds and unit.definition_id in ctx.report_ids and pattern.search(sentence.folded):
                findings.append(_finding("banned_phrase", ERROR, unit, f"{entry['id']}: {entry['why']}", sentence.text))
    for entry in lexicon.get("budgets", []):
        pattern = re.compile(entry["pattern"])
        kinds = frozenset(entry.get("kinds") or DESCRIPTION_KINDS)
        users: set[str] = set()
        hits: list[Sentence] = []
        for sentence in sentences:
            if sentence.unit.kind in kinds and sentence.unit.definition_id in ctx.corpus_ids and pattern.search(sentence.folded):
                users.add(sentence.unit.definition_id)
                hits.append(sentence)
        if len(users) <= entry["maxDefinitions"]:
            continue
        for sentence in hits:
            if sentence.unit.definition_id in ctx.report_ids:
                findings.append(
                    _finding(
                        "word_budget",
                        ERROR,
                        sentence.unit,
                        f"{entry['id']}: usada en {len(users)} definiciones (máximo {entry['maxDefinitions']}); {entry['why']}",
                        sentence.text,
                    )
                )
    starts = lexicon.get("imperativeStarts", [])
    if starts:
        pattern = re.compile(r"^(?:" + "|".join(re.escape(verb) for verb in starts) + r")\b")
        for sentence in sentences:
            unit = sentence.unit
            if unit.kind in DESCRIPTION_KINDS and unit.definition_id in ctx.report_ids and pattern.search(sentence.folded):
                findings.append(
                    _finding(
                        "imperative_description", ERROR, unit,
                        "las descripciones no usan imperativo; las pautas directas van en los cues", sentence.text,
                    )
                )
    return findings


_GRAMMAR_PATTERNS: tuple[tuple[str, re.Pattern[str], str], ...] = (
    ("de_el", re.compile(r"\b(?:de|a) el\b"), "contracción faltante («de el» → «del», «a el» → «al»)"),
    ("snake_case", re.compile(r"\b[a-z]+_[a-z0-9_]+\b"), "identificador técnico crudo en prosa"),
    ("double_space", re.compile(r"[^\s]  +[^\s]"), "espacios dobles"),
    ("repeated_word", re.compile(r"\b([A-Za-zÁÉÍÓÚáéíóúñÑ]{3,}) \1\b", re.IGNORECASE), "palabra repetida"),
    ("punctuation", re.compile(r",,|\s,|\s\.(?!\.)|\.\.(?!\.)|\(\s|\s\)"), "puntuación defectuosa"),
    (
        "dangling_preposition",
        re.compile(r"\b(?:para|de|del|con|en|por|al|sin|que),\s"),
        "preposición seguida de coma (frase rota)",
    ),
)


def check_grammar(units: list[Unit], ctx: Context) -> list[Finding]:
    findings: list[Finding] = []
    for unit in units:
        if unit.definition_id not in ctx.report_ids or unit.kind in {K_FICHA_VIS}:
            continue
        text = unit.text
        for name, pattern, message in _GRAMMAR_PATTERNS:
            if pattern.search(text):
                findings.append(_finding("grammar", ERROR, unit, f"{name}: {message}", text))
        stripped = _LABEL_PREFIX.sub("", text.strip())
        labelled = stripped != text.strip()  # «Secundaria: la cadera…» is a label followed by a clause
        if stripped and not labelled and not (stripped[0].isupper() or stripped[0].isdigit() or stripped[0] in "¿¡\"“("):
            findings.append(_finding("grammar", ERROR, unit, "no comienza con mayúscula", text))
    return findings


def check_shape(ctx: Context) -> list[Finding]:
    findings: list[Finding] = []
    for definition in ctx.definitions.values():
        if definition.id not in ctx.report_ids:
            continue
        description_unit = Unit(definition.id, None, "definition.description", K_DEF, definition.description)
        sentences = split_sentences(definition.description)
        word_total = len(definition.description.split())
        if not 3 <= len(sentences) <= 6:
            findings.append(_finding("shape", ERROR, description_unit, f"descripción de definición con {len(sentences)} frases (3–6)"))
        if not 35 <= word_total <= 140:
            findings.append(_finding("shape", ERROR, description_unit, f"descripción de definición con {word_total} palabras (35–140)"))
        for configuration in definition.configurations:
            profile = configuration.profile
            unit = Unit(definition.id, configuration.id, "profile.description", K_CFG, profile.get("description", "") or "")
            parts = split_sentences(unit.text)
            count = len(unit.text.split())
            if not 1 <= len(parts) <= 2:
                findings.append(_finding("shape", ERROR, unit, f"descripción de configuración con {len(parts)} frases (1–2)", unit.text))
            if not 12 <= count <= 55:
                findings.append(_finding("shape", ERROR, unit, f"descripción de configuración con {count} palabras (12–55)", unit.text))
            for key, kind, low, high in (("setupCues", K_SETUP, 1, 3), ("executionCues", K_EXEC, 1, 4)):
                cues = profile.get(key, []) or []
                header = Unit(definition.id, configuration.id, f"profile.{key}", kind, "")
                if not low <= len(cues) <= high:
                    findings.append(_finding("shape", ERROR, header, f"{len(cues)} cues en {key} ({low}–{high})"))
                for index, cue in enumerate(cues):
                    cue_words = len(str(cue).split())
                    if cue_words > 30:
                        cue_unit = Unit(definition.id, configuration.id, f"profile.{key}[{index}]", kind, str(cue))
                        findings.append(_finding("shape", ERROR, cue_unit, f"cue de {cue_words} palabras (máximo 30)", str(cue)))
    return findings


# ---------------------------------------------------------------------------
# Checks: text vs data
# ---------------------------------------------------------------------------


_NEGATION_BEFORE = re.compile(r"\b(?:sin|ni|en lugar de|en vez de|no (?:necesita|requiere|usa|precisa))\b[^.;:()]*$")


def _negated(folded_sentence: str, start: int) -> bool:
    """True when the word at `start` sits inside a «sin …» / «ni …» clause."""
    return _NEGATION_BEFORE.search(folded_sentence[:start]) is not None


def check_implements(sentences: list[Sentence], ctx: Context) -> list[Finding]:
    findings: list[Finding] = []
    for sentence in sentences:
        unit = sentence.unit
        if unit.definition_id not in ctx.report_ids or unit.kind not in PUBLIC_KINDS | {K_FICHA_TECH}:
            continue
        definition = ctx.definitions.get(unit.definition_id)
        if definition is None:
            continue
        configuration = ctx.configuration(unit)
        for label, pattern, allowed in IMPLEMENT_PATTERNS:
            matches = list(pattern.finditer(sentence.folded))
            if not matches:
                continue
            if unit.kind not in CUE_KINDS and all(_negated(sentence.folded, match.start()) for match in matches):
                continue  # «sin barra ni mancuernas»: prose may say what an exercise does not use; cues may not.
            if unit.kind in CUE_KINDS and configuration is not None:
                if configuration.equipment_id not in allowed:
                    findings.append(
                        _finding(
                            "implement_mismatch", ERROR, unit,
                            f"el cue nombra «{label}» pero la configuración usa {configuration.equipment_id}", sentence.text,
                        )
                    )
            elif not (definition.equipment_ids & allowed):
                findings.append(
                    _finding(
                        "implement_mismatch", ERROR, unit,
                        f"nombra «{label}» y ninguna configuración de la definición usa ese implemento", sentence.text,
                    )
                )
    return findings


def _primary_roles(ctx: Context, definition: Definition) -> dict[str, str]:
    """Muscle roles of the default configuration, read from the curated ficha when there is one."""
    ficha = ctx.fichas.get(definition.id)
    configuration = definition.default_configuration()
    if isinstance(ficha, dict) and ficha.get("status") == "CURATED":
        judged = _ficha_anatomy(ficha, configuration.id)
        if judged is not None:
            return judged[0]
    return dict(configuration.roles)


def _declared_muscles(ctx: Context, definition: Definition, configuration_id: str | None) -> set[str]:
    """Muscles the text may name: the ficha's, once curated, otherwise the compiled profile's.

    A curated ficha is the authoring source and is checked before it is applied, so a muscle it declares counts
    even while the compiled profile still lists the old set.
    """
    ficha = ctx.fichas.get(definition.id)
    if isinstance(ficha, dict) and ficha.get("status") == "CURATED":
        if configuration_id is not None:
            judged = _ficha_anatomy(ficha, configuration_id)
            if judged is not None:
                return set(judged[0])
        declared: set[str] = set()
        for configuration in definition.configurations:
            judged = _ficha_anatomy(ficha, configuration.id)
            if judged is not None:
                declared |= set(judged[0])
        if declared:
            return declared
    if configuration_id is not None:
        configuration = next((item for item in definition.configurations if item.id == configuration_id), None)
        return set(configuration.roles) if configuration is not None else set()
    return {muscle for configuration in definition.configurations for muscle in configuration.roles}


def check_muscle_claims(sentences: list[Sentence], ctx: Context) -> list[Finding]:
    findings: list[Finding] = []
    union = {definition.id: _declared_muscles(ctx, definition, None) for definition in ctx.definitions.values()}
    for sentence in sentences:
        unit = sentence.unit
        if unit.definition_id not in ctx.report_ids or unit.kind not in DESCRIPTION_KINDS | {K_FICHA_TECH}:
            continue
        configuration = ctx.configuration(unit)
        definition = ctx.definitions.get(unit.definition_id)
        declared = (
            _declared_muscles(ctx, definition, configuration.id)
            if configuration is not None and definition is not None
            else union.get(unit.definition_id, set())
        )
        severity = WARN if configuration is not None else ERROR
        positional = TECHNIQUE_POSITIONAL_ALIASES if unit.kind == K_FICHA_TECH else POSITIONAL_ALIASES
        for alias, ids in muscle_mentions(sentence.folded):
            if re.sub(r"\s+", " ", alias) in positional:
                continue
            if ids & declared:
                continue
            findings.append(
                _finding(
                    "muscle_claim", severity, unit,
                    f"menciona «{alias}» pero no figura entre los músculos declarados ({', '.join(sorted(declared)) or 'ninguno'})",
                    sentence.text,
                )
            )
    for definition in ctx.definitions.values():
        if definition.id not in ctx.report_ids:
            continue
        primaries = {muscle for muscle, role in _primary_roles(ctx, definition).items() if role == "PRIMARY"}
        mentioned: set[str] = set()
        for _alias, ids in muscle_mentions(fold(definition.description)):
            mentioned |= ids
        if primaries and not (primaries & mentioned):
            findings.append(
                Finding(
                    "primary_unmentioned", ERROR, definition.id, None, "definition.description",
                    f"la descripción no nombra ningún músculo principal ({', '.join(sorted(primaries))})",
                    excerpt(definition.description),
                )
            )
    return findings


# ---------------------------------------------------------------------------
# Checks: anatomy rules
# ---------------------------------------------------------------------------


def _list_selector(value: Any) -> frozenset[str] | None:
    if value is None:
        return None
    return frozenset(value if isinstance(value, list) else [value])


def rule_matches(applies: dict[str, Any], definition: Definition, configuration: Configuration) -> bool:
    for key, actual in (
        ("definitionIds", definition.id),
        ("familyIds", definition.family_id),
        ("movementPatterns", configuration.pattern),
        ("equipmentIds", configuration.equipment_id),
    ):
        wanted = _list_selector(applies.get(key))
        if wanted is not None and actual not in wanted:
            return False
    excluded = _list_selector(applies.get("excludeDefinitionIds"))
    if excluded is not None and definition.id in excluded:
        return False
    for option, wanted_value in (applies.get("selectedOptions") or {}).items():
        wanted = _list_selector(wanted_value)
        if wanted is not None and configuration.options.get(option) not in wanted:
            return False
    return True


def _excepted(rule: dict[str, Any], definition: Definition, configuration: Configuration) -> bool:
    for exception in rule.get("exceptions", []) or []:
        definition_ids = _list_selector(exception.get("definitionIds"))
        configuration_ids = _list_selector(exception.get("configurationIds"))
        if definition_ids is not None and definition.id not in definition_ids:
            continue
        if configuration_ids is not None and configuration.id not in configuration_ids:
            continue
        if definition_ids is None and configuration_ids is None:
            continue
        return True
    return False


def _rule_finding(rule: dict[str, Any], definition: Definition, configuration: Configuration, message: str) -> Finding:
    return Finding("anatomy_rule", ERROR, definition.id, configuration.id, rule["id"], message, excerpt(rule.get("title", "")))


def _ficha_anatomy(ficha: dict[str, Any], configuration_id: str) -> tuple[dict[str, str], dict[str, str]] | None:
    """Roles the ficha declares for one configuration, overrides applied (``NONE`` removes an entry).

    Returns ``None`` when the ficha has no usable anatomy block, so the caller falls back to the compiled
    profile. A ficha is the authoring source and is judged before it is applied, so the rules have to read it
    directly: judging the compiled profile instead would pass a ficha that reintroduces a defect the rules
    already forbade, for as long as nobody recompiles.
    """
    anatomy = ficha.get("anatomy")
    if not isinstance(anatomy, dict):
        return None
    roles: dict[str, str] = {}
    for entry in anatomy.get("muscles") or []:
        if isinstance(entry, dict) and isinstance(entry.get("id"), str) and entry.get("role") in ROLES:
            roles[entry["id"]] = entry["role"]
    joints: dict[str, str] = {}
    for entry in anatomy.get("joints") or []:
        if isinstance(entry, dict) and isinstance(entry.get("id"), str) and entry.get("role") in ROLES:
            joints[entry["id"]] = entry["role"]
    if not roles and not joints:
        return None
    override = (anatomy.get("overrides") or {}).get(configuration_id) if isinstance(anatomy.get("overrides"), dict) else None
    if isinstance(override, dict):
        for entry in override.get("muscles") or []:
            if isinstance(entry, dict) and isinstance(entry.get("id"), str) and (entry.get("role") in ROLES or entry.get("role") == "NONE"):
                if entry["role"] == "NONE":
                    roles.pop(entry["id"], None)
                else:
                    roles[entry["id"]] = entry["role"]
        for entry in override.get("joints") or []:
            if isinstance(entry, dict) and isinstance(entry.get("id"), str) and (entry.get("role") in ROLES or entry.get("role") == "NONE"):
                if entry["role"] == "NONE":
                    joints.pop(entry["id"], None)
                else:
                    joints[entry["id"]] = entry["role"]
    return roles, joints


def check_anatomy_rules(rules: dict[str, Any] | None, ctx: Context) -> list[Finding]:
    if not rules:
        return []
    findings: list[Finding] = []
    for rule in rules.get("rules", []):
        applies = rule.get("applies", {}) or {}
        for definition in ctx.definitions.values():
            if definition.id not in ctx.report_ids:
                continue
            ficha = ctx.fichas.get(definition.id)
            for configuration in definition.configurations:
                if not rule_matches(applies, definition, configuration) or _excepted(rule, definition, configuration):
                    continue
                judged = _ficha_anatomy(ficha, configuration.id) if isinstance(ficha, dict) and ficha.get("status") == "CURATED" else None
                roles = judged[0] if judged is not None else configuration.roles
                joints = judged[1] if judged is not None else configuration.joints
                if configuration.pattern in set(rule.get("forbidPatterns", []) or []):
                    findings.append(
                        _rule_finding(rule, definition, configuration, f"el patrón de movimiento {configuration.pattern} no describe este ejercicio")
                    )
                required_patterns = rule.get("requirePatterns") or []
                if required_patterns and configuration.pattern not in set(required_patterns):
                    findings.append(
                        _rule_finding(rule, definition, configuration, f"el patrón debe ser uno de {', '.join(required_patterns)} (tiene {configuration.pattern})")
                    )
                for item in rule.get("require", []) or []:
                    have = ROLE_RANK[roles.get(item["muscle"], "NONE")]
                    if have < ROLE_RANK[item["atLeast"]]:
                        findings.append(
                            _rule_finding(rule, definition, configuration, f"{item['muscle']} debe ser al menos {item['atLeast']} (tiene {roles.get(item['muscle'], 'ninguno')})")
                        )
                for item in rule.get("exact", []) or []:
                    have = roles.get(item["muscle"], "NONE")
                    if have != item["role"]:
                        findings.append(
                            _rule_finding(rule, definition, configuration, f"{item['muscle']} debe ser {item['role']} (tiene {have if have != 'NONE' else 'ninguno'})")
                        )
                for item in rule.get("forbid", []) or []:
                    have = roles.get(item["muscle"], "NONE")
                    forbidden = set(item.get("roles") or ROLES)
                    if have != "NONE" and have in forbidden:
                        findings.append(
                            _rule_finding(rule, definition, configuration, f"{item['muscle']} no puede ser {have} aquí")
                        )
                for item in rule.get("requireJoints", []) or []:
                    have = ROLE_RANK[joints.get(item["joint"], "NONE")]
                    if have < ROLE_RANK[item["atLeast"]]:
                        findings.append(
                            _rule_finding(rule, definition, configuration, f"la articulación {item['joint']} debe ser al menos {item['atLeast']}")
                        )
                for item in rule.get("forbidJoints", []) or []:
                    have = joints.get(item["joint"], "NONE")
                    forbidden = set(item.get("roles") or ROLES)
                    if have != "NONE" and have in forbidden:
                        findings.append(
                            _rule_finding(rule, definition, configuration, f"la articulación {item['joint']} no puede ser {have} aquí")
                        )
    for item in rules.get("inheritance", []) or []:
        child = ctx.definitions.get(item["child"])
        parent = ctx.definitions.get(item["parent"])
        if child is None or parent is None or child.id not in ctx.report_ids:
            continue
        allowed = {entry["muscle"] for entry in item.get("allowedDifferences", []) or []}
        child_roles = child.default_configuration().roles
        parent_roles = parent.default_configuration().roles
        for muscle in sorted(set(child_roles) | set(parent_roles)):
            if muscle in allowed:
                continue
            if child_roles.get(muscle) != parent_roles.get(muscle):
                findings.append(
                    Finding(
                        "inheritance", ERROR, child.id, child.default_configuration().id, item["id"],
                        f"{muscle}: {child_roles.get(muscle, 'ninguno')} en la especialidad frente a {parent_roles.get(muscle, 'ninguno')} en {parent.id}",
                        "",
                    )
                )
    return findings


def check_rules_file(rules: dict[str, Any] | None, definitions: dict[str, Definition]) -> list[Finding]:
    """Rules must reference things that exist, otherwise they silently match nothing."""
    if rules is None:
        return []
    findings: list[Finding] = []

    def problem(rule_id: str, message: str) -> None:
        findings.append(Finding("rules_invalid", ERROR, "", None, rule_id, message))

    patterns = {c.pattern for d in definitions.values() for c in d.configurations}
    equipment = {c.equipment_id for d in definitions.values() for c in d.configurations}
    seen: set[str] = set()
    for rule in rules.get("rules", []):
        rule_id = rule.get("id", "?")
        if rule_id in seen:
            problem(rule_id, "id duplicado")
        seen.add(rule_id)
        for required in ("id", "title", "evidence", "applies"):
            if not rule.get(required):
                problem(rule_id, f"falta «{required}»")
        applies = rule.get("applies", {}) or {}
        for key in ("definitionIds", "excludeDefinitionIds"):
            for definition_id in applies.get(key, []) or []:
                if definition_id not in definitions:
                    problem(rule_id, f"{key}: definición inexistente {definition_id}")
        for pattern in applies.get("movementPatterns", []) or []:
            if pattern not in patterns:
                findings.append(Finding("rule_dead", WARN, "", None, rule_id, f"movementPatterns: patrón sin uso {pattern}"))
        for equipment_id in applies.get("equipmentIds", []) or []:
            if equipment_id not in equipment:
                findings.append(Finding("rule_dead", WARN, "", None, rule_id, f"equipmentIds: implemento sin uso {equipment_id}"))
        for key in ("require", "exact", "forbid"):
            for item in rule.get(key, []) or []:
                if item.get("muscle") not in MUSCLE_IDS:
                    problem(rule_id, f"{key}: músculo fuera de la ontología {item.get('muscle')}")
                for role in [item.get("atLeast"), item.get("role"), *(item.get("roles") or [])]:
                    if role is not None and role not in ROLE_RANK:
                        problem(rule_id, f"{key}: rol inválido {role}")
        for key in ("requireJoints", "forbidJoints"):
            for item in rule.get(key, []) or []:
                if item.get("joint") not in JOINT_IDS:
                    problem(rule_id, f"{key}: articulación fuera de la ontología {item.get('joint')}")
        for exception in rule.get("exceptions", []) or []:
            if not str(exception.get("why", "")).strip():
                problem(rule_id, "excepción sin «why»")
            for definition_id in exception.get("definitionIds", []) or []:
                if definition_id not in definitions:
                    problem(rule_id, f"excepción con definición inexistente {definition_id}")
        matched = any(
            rule_matches(applies, definition, configuration)
            for definition in definitions.values()
            for configuration in definition.configurations
        )
        if applies and not matched:
            findings.append(Finding("rule_dead", WARN, "", None, rule_id, "la regla no selecciona ninguna configuración"))
    for item in rules.get("inheritance", []) or []:
        item_id = item.get("id", "?")
        for key in ("child", "parent"):
            if item.get(key) not in definitions:
                problem(item_id, f"inheritance.{key}: definición inexistente {item.get(key)}")
        for entry in item.get("allowedDifferences", []) or []:
            if entry.get("muscle") not in MUSCLE_IDS or not str(entry.get("why", "")).strip():
                problem(item_id, "allowedDifferences requiere músculo válido y «why»")
    return findings


# ---------------------------------------------------------------------------
# Checks: fichas
# ---------------------------------------------------------------------------

VISUAL_BASE_FIELDS = ("camera", "phase", "orientation", "contacts", "posture", "load")


def _text(value: Any, minimum: int = 1) -> bool:
    return isinstance(value, str) and len(value.strip()) >= minimum


def _ficha_problem(definition_id: str, field_name: str, message: str, check: str = "ficha_shape") -> Finding:
    return Finding(check, ERROR, definition_id, None, field_name, message)


def check_fichas(ctx: Context) -> list[Finding]:
    findings: list[Finding] = []
    for definition in ctx.definitions.values():
        if definition.id not in ctx.report_ids:
            continue
        ficha = ctx.fichas.get(definition.id)
        if ficha is None:
            findings.append(
                Finding("visual_missing", WARN, definition.id, None, "ficha", "la definición no tiene ficha en curation/fichas")
            )
            continue
        if ficha.get("status") != "CURATED":
            findings.append(
                Finding("visual_missing", WARN, definition.id, None, "ficha.status", "ficha LEGACY: sin ficha visual ni técnica curada")
            )
            continue
        findings.extend(_check_curated_ficha(definition, ficha))
    return findings


def _check_curated_ficha(definition: Definition, ficha: dict[str, Any]) -> list[Finding]:
    did = definition.id
    findings: list[Finding] = []
    source_ids = set()
    sources = ficha.get("sources")
    if not isinstance(sources, list) or len(sources) < 2:
        findings.append(_ficha_problem(did, "sources", "se requieren al menos 2 fuentes"))
    else:
        for index, entry in enumerate(sources):
            if not isinstance(entry, dict) or not _text(entry.get("id")) or not _text(entry.get("url"), 8):
                findings.append(_ficha_problem(did, f"sources[{index}]", "cada fuente necesita id y url"))
                continue
            if not _text(entry.get("title"), 10) or not _text(entry.get("claim"), 20):
                findings.append(
                    _ficha_problem(did, f"sources[{index}]", "cada fuente necesita title (≥10) y claim (≥20): qué respalda aquí")
                )
            if entry["id"] in source_ids:
                findings.append(_ficha_problem(did, f"sources[{index}]", f"id de fuente duplicado {entry['id']}"))
            source_ids.add(entry["id"])
    technique = ficha.get("technique")
    if not isinstance(technique, dict):
        findings.append(_ficha_problem(did, "technique", "falta el bloque técnico"))
    else:
        if not _text(technique.get("identity"), 40):
            findings.append(_ficha_problem(did, "technique.identity", "identity requiere ≥40 caracteres"))
        for key, minimum in (("setup", 1), ("keyPositions", 1), ("mistakes", 2)):
            value = technique.get(key)
            if not isinstance(value, list) or len(value) < minimum or not all(_text(item, 8) for item in value):
                findings.append(_ficha_problem(did, f"technique.{key}", f"se requieren ≥{minimum} entradas de texto"))
        phases = technique.get("phases")
        if not isinstance(phases, list) or len(phases) < 2 or not all(
            isinstance(phase, dict) and _text(phase.get("name")) and _text(phase.get("description"), 20) for phase in phases
        ):
            findings.append(_ficha_problem(did, "technique.phases", "se requieren ≥2 fases con name y description"))
    anatomy = ficha.get("anatomy")
    if not isinstance(anatomy, dict):
        findings.append(_ficha_problem(did, "anatomy", "falta el bloque anatómico"))
    else:
        for key, ontology in (("muscles", MUSCLE_IDS), ("joints", JOINT_IDS)):
            entries = anatomy.get(key)
            if not isinstance(entries, list) or not entries:
                findings.append(_ficha_problem(did, f"anatomy.{key}", "lista vacía o ausente"))
                continue
            seen: set[str] = set()
            for index, entry in enumerate(entries):
                field_name = f"anatomy.{key}[{index}]"
                if not isinstance(entry, dict) or entry.get("id") not in ontology:
                    findings.append(_ficha_problem(did, field_name, f"id fuera de la ontología: {entry.get('id') if isinstance(entry, dict) else entry}"))
                    continue
                if entry["id"] in seen:
                    findings.append(_ficha_problem(did, field_name, f"id repetido {entry['id']}"))
                seen.add(entry["id"])
                if entry.get("role") not in ROLES:
                    findings.append(_ficha_problem(did, field_name, "rol inválido"))
                if not _text(entry.get("why"), 40):
                    findings.append(_ficha_problem(did, field_name, "why requiere ≥40 caracteres con la función concreta"))
                refs = entry.get("sources")
                if not isinstance(refs, list) or not refs or any(ref not in source_ids for ref in refs):
                    findings.append(_ficha_problem(did, field_name, "sources debe citar ids declarados en sources"))
                if key == "joints" and not (isinstance(entry.get("actions"), list) and entry["actions"]):
                    findings.append(_ficha_problem(did, field_name, "una articulación requiere actions"))
        overrides = anatomy.get("overrides")
        for configuration_id, override in (overrides.items() if isinstance(overrides, dict) else []):
            if not isinstance(override, dict):
                continue
            for key in ("muscles", "joints"):
                patches = override.get(key)
                for index, entry in enumerate(patches if isinstance(patches, list) else []):
                    if not isinstance(entry, dict):
                        continue
                    field_name = f"anatomy.overrides.{configuration_id}.{key}[{index}]"
                    if not _text(entry.get("why"), 40):
                        findings.append(_ficha_problem(did, field_name, "una excepción por configuración requiere why ≥40 caracteres que la justifique"))
                    refs = entry.get("sources")
                    if entry.get("role") != "NONE" and (
                        not isinstance(refs, list) or not refs or any(ref not in source_ids for ref in refs)
                    ):
                        findings.append(_ficha_problem(did, field_name, "sources debe citar ids declarados en sources"))
    visual = ficha.get("visual")
    if not isinstance(visual, dict):
        findings.append(_ficha_problem(did, "visual", "falta el bloque visual", "visual_incomplete"))
        return findings
    base = visual.get("base")
    for key in VISUAL_BASE_FIELDS:
        if not isinstance(base, dict) or not _text(base.get(key), 8):
            findings.append(_ficha_problem(did, f"visual.base.{key}", "campo visual obligatorio ausente o muy corto", "visual_incomplete"))
    for key, minimum in (("forbidden", 2), ("qa", 3)):
        value = visual.get(key)
        if not isinstance(value, list) or len(value) < minimum or not all(_text(item, 8) for item in value):
            findings.append(_ficha_problem(did, f"visual.{key}", f"se requieren ≥{minimum} entradas", "visual_incomplete"))
    expected = set(definition.equipment_ids)
    by_implement = visual.get("byImplement")
    prompts = visual.get("promptCore")
    for label, container in (("byImplement", by_implement), ("promptCore", prompts)):
        if not isinstance(container, dict):
            findings.append(_ficha_problem(did, f"visual.{label}", "falta el bloque por implemento", "visual_incomplete"))
            continue
        for equipment_id in sorted(expected - set(container)):
            findings.append(_ficha_problem(did, f"visual.{label}.{equipment_id}", "sin entrada para este implemento", "visual_incomplete"))
        for equipment_id in sorted(set(container) - expected):
            findings.append(_ficha_problem(did, f"visual.{label}.{equipment_id}", "implemento que la definición no tiene", "visual_incomplete"))
    if isinstance(by_implement, dict):
        for equipment_id, entry in by_implement.items():
            if not isinstance(entry, dict) or not _text(entry.get("geometry"), 20):
                findings.append(_ficha_problem(did, f"visual.byImplement.{equipment_id}.geometry", "falta la geometría del implemento", "visual_incomplete"))
    if isinstance(prompts, dict):
        for equipment_id, prompt in prompts.items():
            if not _text(prompt, 60) or len(prompt) > 700 or not all(ord(char) < 128 for char in prompt):
                findings.append(_ficha_problem(did, f"visual.promptCore.{equipment_id}", "promptCore debe ser inglés ASCII de 60–700 caracteres", "visual_incomplete"))
    by_variant = visual.get("byVariant")
    if by_variant is not None:
        configuration_ids = {configuration.id for configuration in definition.configurations}
        if not isinstance(by_variant, dict) or set(by_variant) - configuration_ids:
            findings.append(_ficha_problem(did, "visual.byVariant", "debe mapear configuraciones existentes", "visual_incomplete"))
        else:
            for configuration_id, entry in by_variant.items():
                if not isinstance(entry, dict) or not _text(entry.get("difference"), 20):
                    findings.append(
                        _ficha_problem(
                            did,
                            f"visual.byVariant.{configuration_id}.difference",
                            "una variante visual declara en 'difference' qué se ve distinto (≥20 caracteres)",
                            "visual_incomplete",
                        )
                    )
    return findings


# ---------------------------------------------------------------------------
# Metrics (informational)
# ---------------------------------------------------------------------------


def ngram_stats(sentences: list[Sentence], n: int, min_content: int) -> dict[str, int]:
    """How much text shares an n-gram (>= min_content content words) with another definition. Informational."""
    owners: dict[tuple[str, ...], set[str]] = defaultdict(set)
    for sentence in sentences:
        if sentence.unit.kind in CROSS_EXEMPT | CROSS_SOFT:
            continue
        tokens = sentence.tokens
        for start in range(len(tokens) - n + 1):
            gram = tokens[start : start + n]
            if content_count(gram) >= min_content:
                owners[gram].add(sentence.unit.definition_id)
    shared = {gram for gram, ids in owners.items() if len(ids) >= 2}
    affected_sentences = 0
    affected_definitions: set[str] = set()
    for sentence in sentences:
        if sentence.unit.kind in CROSS_EXEMPT | CROSS_SOFT:
            continue
        tokens = sentence.tokens
        if any(tokens[start : start + n] in shared for start in range(len(tokens) - n + 1)):
            affected_sentences += 1
            affected_definitions.add(sentence.unit.definition_id)
    return {
        "n": n,
        "minContent": min_content,
        "sharedNgrams": len(shared),
        "affectedSentences": affected_sentences,
        "affectedDefinitions": len(affected_definitions),
    }


def shared_sentence_stats(sentences: list[Sentence], minimum: int, top: int = 5) -> dict[str, Any]:
    """Informational: how many distinct sentences are reused by more than one definition, and the worst offenders."""
    owners: dict[tuple[str, ...], set[str]] = defaultdict(set)
    sample: dict[tuple[str, ...], str] = {}
    for sentence in sentences:
        if sentence.unit.kind in CROSS_EXEMPT or len(sentence.tokens) < minimum:
            continue
        owners[sentence.tokens].add(sentence.unit.definition_id)
        sample.setdefault(sentence.tokens, sentence.text)
    shared = {tokens: ids for tokens, ids in owners.items() if len(ids) >= 2}
    worst = sorted(shared.items(), key=lambda item: (-len(item[1]), item[0]))[:top]
    return {
        "distinctSharedSentences": len(shared),
        "maxDefinitionsSharingOne": max((len(ids) for ids in shared.values()), default=0),
        "top": [{"definitions": len(ids), "text": excerpt(sample[tokens], 140)} for tokens, ids in worst],
    }


def compute_metrics(definitions: dict[str, Definition], units: list[Unit], sentences: list[Sentence]) -> dict[str, Any]:
    by_kind: dict[str, list[str]] = defaultdict(list)
    for unit in units:
        by_kind[unit.kind].append(re.sub(r"\s+", " ", unit.text).strip())
    variety = {
        kind: {"total": len(values), "unique": len(set(values))} for kind, values in sorted(by_kind.items())
    }
    configurations = [c for d in definitions.values() for c in d.configurations]
    primary_distribution = Counter(sum(1 for role in c.roles.values() if role == "PRIMARY") for c in configurations)
    without_stabilizer = sum(1 for c in configurations if "STABILIZER" not in c.roles.values())
    pairs = {(d.id, equipment) for d in definitions.values() for equipment in d.equipment_ids}
    word_total = sum(len(unit.text.split()) for unit in units)
    return {
        "definitions": len(definitions),
        "families": len({d.family_id for d in definitions.values()}),
        "configurations": len(configurations),
        "definitionImplementPairs": len(pairs),
        "textUnits": len(units),
        "sentences": len(sentences),
        "words": word_total,
        "variety": variety,
        "primaryMuscleCountDistribution": dict(sorted(primary_distribution.items())),
        "configurationsWithoutStabilizers": without_stabilizer,
    }


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------


def validate_allowlist(allowlist: dict[str, Any] | None) -> list[Finding]:
    findings: list[Finding] = []
    for index, entry in enumerate((allowlist or {}).get("entries", []) or []):
        if not entry.get("check") or not str(entry.get("why", "")).strip():
            findings.append(Finding("allowlist_invalid", ERROR, "", None, f"entries[{index}]", "cada entrada necesita check y why"))
    return findings


def _allowed(entry: dict[str, Any], finding: Finding) -> bool:
    if entry.get("check") != finding.check:
        return False
    definition_ids = _list_selector(entry.get("definitionIds"))
    if definition_ids is not None and finding.definition_id not in definition_ids:
        return False
    configuration_ids = _list_selector(entry.get("configurationIds"))
    if configuration_ids is not None and finding.configuration_id not in configuration_ids:
        return False
    contains = entry.get("contains")
    if contains:
        haystack = fold(finding.message + " " + finding.detail)
        if fold(contains) not in haystack:
            return False
    return definition_ids is not None or configuration_ids is not None or bool(contains)


def run_audit(
    source: dict[str, Any],
    *,
    rules: dict[str, Any] | None = None,
    allowlist: dict[str, Any] | None = None,
    lexicon: dict[str, Any] | None = None,
    fichas: dict[str, dict[str, Any]] | None = None,
    scope: Iterable[str] | None = None,
    options: Options | None = None,
) -> AuditResult:
    options = options or Options()
    definitions = load_definitions(source)
    fichas = fichas or {}
    requested = frozenset(scope) if scope is not None else None
    if requested is not None:
        unknown = sorted(requested - set(definitions))
        if unknown:
            raise ValueError(f"definiciones desconocidas: {', '.join(unknown)}")
    if fichas and not options.include_legacy:
        curated = {definition_id for definition_id, ficha in fichas.items() if ficha.get("status") == "CURATED" and definition_id in definitions}
        corpus = frozenset(curated | (requested or frozenset()))
    else:
        corpus = frozenset(definitions)
    report = requested if requested is not None else corpus
    legacy = sorted(set(definitions) - corpus)

    all_units = list(source_units(definitions))
    all_units.extend(ficha_units({k: v for k, v in fichas.items() if k in definitions}))
    corpus_units = [unit for unit in all_units if unit.definition_id in corpus]
    sentences = build_sentences(corpus_units)
    ctx = Context(definitions, fichas, options, lexicon or {}, report, corpus)

    findings: list[Finding] = []
    findings += check_intra_duplicates(sentences, ctx)
    findings += check_shared_sentences(sentences, ctx)
    findings += check_shared_ngrams(sentences, ctx)
    findings += check_templates(sentences, ctx)
    findings += check_lexicon(sentences, ctx)
    findings += check_grammar(corpus_units, ctx)
    findings += check_shape(ctx)
    findings += check_implements(sentences, ctx)
    findings += check_muscle_claims(sentences, ctx)
    rule_problems = check_rules_file(rules, definitions)
    # A rules file with broken references cannot be trusted to judge the catalogue: report its problems only.
    usable_rules = None if any(item.check == "rules_invalid" for item in rule_problems) else rules
    findings += check_anatomy_rules(usable_rules, ctx)
    findings += rule_problems
    findings += check_fichas(ctx)
    findings += validate_allowlist(allowlist)

    entries = (allowlist or {}).get("entries", []) or []
    kept: list[Finding] = []
    allowlisted: list[Finding] = []
    for finding in findings:
        if any(_allowed(entry, finding) for entry in entries if entry.get("check") and str(entry.get("why", "")).strip()):
            allowlisted.append(finding)
        else:
            kept.append(finding)
    if options.warnings_as_errors:
        kept = [
            Finding(f.check, ERROR, f.definition_id, f.configuration_id, f.field, f.message, f.detail) if f.severity == WARN else f
            for f in kept
        ]
    kept.sort(key=Finding.sort_key)
    allowlisted.sort(key=Finding.sort_key)
    metrics = compute_metrics({d: definitions[d] for d in sorted(report) if d in definitions}, [u for u in all_units if u.definition_id in report], [s for s in sentences if s.unit.definition_id in report])
    metrics["sharedNgramProfile"] = [ngram_stats(sentences, n, c) for n, c in ((5, 3), (5, 4), (6, 4), (8, 4))]
    metrics["sharedSentenceProfile"] = shared_sentence_stats(sentences, options.min_sentence_tokens)
    return AuditResult(kept, allowlisted, metrics, sorted(report), sorted(corpus), legacy, options)


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------

# check -> (severity, what it detects). Rendered in the markdown report so the baseline explains itself.
CHECK_DOCS: dict[str, tuple[str, str]] = {
    "intra_config_duplicate": (
        "ERROR",
        "Una frase se repite entre campos de una misma configuración (o con la descripción de su definición); incluye casi-duplicados (Jaccard ≥ 0,85).",
    ),
    "shared_sentence": (
        "ERROR",
        "La misma frase aparece en otra definición. Cuenta texto visible, campos legacy y ficha técnica; la ficha anatómica solo avisa y la visual está exenta.",
    ),
    "shared_ngram": (
        "ERROR",
        "Fragmento compartido con otra definición aunque la frase completa sea distinta: ≥ 8 palabras con ≥ 4 de contenido, o 5 palabras con ≥ 4 de contenido (frase densa).",
    ),
    "template_skeleton": (
        "ERROR",
        "Misma plantilla en ≥ 3 definiciones con solo músculo, implemento, articulación o número cambiados.",
    ),
    "template_skeleton_in_definition": (
        "ERROR",
        "Una configuración repite la frase de su hermana cambiando solo el implemento: el delta por implemento tiene que decir algo distinto.",
    ),
    "template_opening": (
        "ERROR",
        "La misma apertura de 4 palabras en ≥ 6 configuraciones de ≥ 3 definiciones.",
    ),
    "banned_phrase": ("ERROR", "Cliché o plantilla del léxico prohibido (`curation/quality_lexicon.json`)."),
    "word_budget": (
        "ERROR",
        "Palabra comodín usada en más definiciones que su presupuesto por catálogo (`curation/quality_lexicon.json`).",
    ),
    "imperative_description": ("ERROR", "La descripción empieza con un imperativo; las pautas directas van en los cues."),
    "grammar": (
        "ERROR",
        "«de el»/«a el», identificadores snake_case en prosa, espacios dobles, palabra repetida, puntuación defectuosa, preposición seguida de coma o minúscula inicial.",
    ),
    "shape": (
        "ERROR",
        "Longitudes: definición 3–6 frases y 35–140 palabras; configuración 1–2 frases y 12–55 palabras; cues ≤ 30 palabras; setup 1–3 cues y execution 1–4.",
    ),
    "implement_mismatch": (
        "ERROR",
        "Un cue nombra un implemento distinto al de su configuración, o una descripción nombra uno que ninguna variante usa (las negaciones «sin barra» se toleran en prosa).",
    ),
    "muscle_claim": (
        "ERROR/WARN",
        "El texto menciona un músculo que los datos no declaran. ERROR en la descripción de la definición, WARN en la de una configuración.",
    ),
    "primary_unmentioned": ("ERROR", "La descripción de la definición no nombra ningún músculo principal."),
    "anatomy_rule": ("ERROR", "Incumple una regla de `curation/anatomy_rules.json` (músculos, articulaciones o patrón)."),
    "inheritance": (
        "ERROR",
        "Una especialidad difiere anatómicamente de su ejercicio base en la configuración por defecto sin una diferencia declarada.",
    ),
    "rules_invalid": ("ERROR", "El archivo de reglas referencia algo inexistente o incompleto; mientras exista, sus reglas no se aplican."),
    "rule_dead": ("WARN", "Una regla no selecciona ninguna configuración, o menciona un patrón o implemento sin uso."),
    "allowlist_invalid": ("ERROR", "Una entrada de `curation/quality_allowlist.json` no declara `check` y `why`."),
    "ficha_shape": ("ERROR", "Ficha CURATED incompleta (fuentes, bloque técnico o bloque anatómico)."),
    "visual_incomplete": ("ERROR", "Ficha CURATED sin ficha visual completa por implemento, o con `promptCore` inválido."),
    "visual_missing": ("WARN", "La definición todavía no tiene ficha curada (LEGACY o ausente)."),
}

_VISIBLE_FIELD_PREFIXES = ("definition.description", "profile.description", "profile.setupCues", "profile.executionCues")


def is_visible_field(field: str | None) -> bool:
    """True for the four text fields the app actually shows or speaks."""
    return bool(field) and str(field).startswith(_VISIBLE_FIELD_PREFIXES)


def summarize(result: AuditResult) -> list[dict[str, Any]]:
    grouped: dict[tuple[str, str], list[Finding]] = defaultdict(list)
    for finding in result.findings:
        grouped[(finding.check, finding.severity)].append(finding)
    rows = []
    for (check, severity), items in sorted(grouped.items()):
        rows.append(
            {
                "check": check,
                "severity": severity,
                "findings": len(items),
                "visible": sum(1 for f in items if is_visible_field(f.field)),
                "definitions": len({f.definition_id for f in items if f.definition_id}),
                "configurations": len({f.configuration_id for f in items if f.configuration_id}),
            }
        )
    return rows


def render_text(result: AuditResult, examples: int) -> str:
    lines = [
        f"definiciones auditadas: {len(result.report_ids)}  corpus: {len(result.corpus_ids)}  legacy fuera del corpus: {len(result.legacy_ids)}",
        f"configuraciones: {result.metrics['configurations']}  pares definición×implemento: {result.metrics['definitionImplementPairs']}  palabras: {result.metrics['words']}",
        "",
        f"{'chequeo':<34}{'sev':<7}{'hallazgos':>10}{'visible':>9}{'defs':>7}{'cfgs':>7}",
    ]
    for row in summarize(result):
        lines.append(
            f"{row['check']:<34}{row['severity']:<7}{row['findings']:>10}{row['visible']:>9}{row['definitions']:>7}{row['configurations']:>7}"
        )
    errors = sum(1 for f in result.findings if f.severity == ERROR)
    warnings = sum(1 for f in result.findings if f.severity == WARN)
    lines += ["", f"TOTAL errores={errors} avisos={warnings} permitidos={len(result.allowlisted)}"]
    if examples:
        shown: Counter[str] = Counter()
        lines.append("")
        for finding in result.findings:
            if shown[finding.check] >= examples:
                continue
            shown[finding.check] += 1
            target = finding.configuration_id or finding.definition_id or "-"
            lines.append(f"[{finding.check}] {target} {finding.field or ''}: {finding.message}")
            if finding.detail:
                lines.append(f"    {finding.detail}")
    return "\n".join(lines)


CALIBRATION_NOTES = (
    "**Alcance del corpus.** Sin fichas curadas (línea base) se compara todo el catálogo contra sí mismo, incluidos los campos legacy que la fase F1 retira. "
    "Con fichas, el corpus es solo texto `CURATED` más las definiciones pedidas con `--definitions`: así el texto nuevo no se culpa por texto legacy que se va a reescribir.",
    "**N-gramas.** Bloquea un fragmento compartido de 8 palabras con ≥ 4 de contenido, o de 5 palabras con ≥ 4 de contenido (frase densa en sustantivos y verbos). "
    "Los 5-gramas con 3 palabras de contenido y los 6-gramas se informan como métrica, no como hallazgo: "
    "son colocaciones naturales («la barra cerca del cuerpo», «con los pies al ancho de los hombros») y bloquearlas empujaría a escribir peor.",
    "**Plantillas.** Se enmascaran músculos, implementos, articulaciones y números antes de comparar; una frase que solo cambia esas palabras es la misma frase. "
    "Con ≥ 3 definiciones es plantilla; dentro de una definición, dos configuraciones hermanas no pueden diferir solo en el implemento.",
    "**Presupuesto léxico.** Verbos y adjetivos comodín («ejecuta», «tensión», «trayectoria», «honesto»…) se reparten por catálogo: "
    "una palabra puede usarse en un máximo de definiciones. Es reciclaje de palabras, no solo de frases. Los topes viven en `curation/quality_lexicon.json` y se ajustan con evidencia.",
    "**Implementos.** En descripciones se permite comparar con otro implemento de la misma definición y negar uno («sin barra»). "
    "En los cues, que se leen en voz alta durante la serie, nombrar un implemento distinto al de la configuración es error.",
    "**Músculos.** Una descripción solo nombra músculos que los datos declaran para esa definición o configuración. "
    "Referencias de posición (por ejemplo la barra «sobre los trapecios») se redactan sin nombre de músculo (parte alta de la espalda) o se justifican en `curation/quality_allowlist.json`.",
    "**Reglas anatómicas.** Se aplican sobre la configuración, no sobre el texto, y solo si el archivo de reglas es válido. Cada excepción exige un `why`.",
    "**Exenciones.** `quality_allowlist.json` solo acepta entradas con `check`, un selector y `why`; una entrada sin justificación se ignora y se informa.",
)


def render_markdown(result: AuditResult, title: str, revision: str) -> str:
    metrics = result.metrics
    sentence_profile = metrics.get("sharedSentenceProfile", {})
    rows = summarize(result)
    visible_errors = sum(row["visible"] for row in rows if row["severity"] == ERROR)
    all_errors = sum(row["findings"] for row in rows if row["severity"] == ERROR)
    lines = [
        f"# {title}",
        "",
        f"Generado por `scripts/catalog_v2_quality_audit.py` (solo lectura) sobre la revisión `{revision}`.",
        "Las cifras se vuelven a medir en cada ejecución; este documento solo congela una medición. "
        "Regenerar: `python scripts/catalog_v2_quality_audit.py --markdown catalog/exercises/v2/curation/QUALITY_BASELINE.md`.",
        "",
        "## Cobertura medida",
        "",
        f"- Definiciones auditadas: {metrics['definitions']} en {metrics['families']} familias (corpus: {len(result.corpus_ids)}; legacy fuera del corpus: {len(result.legacy_ids)}).",
        f"- Configuraciones: {metrics['configurations']}; pares definición × implemento: {metrics['definitionImplementPairs']}.",
        f"- Unidades de texto: {metrics['textUnits']}; frases: {metrics['sentences']}; palabras: {metrics['words']}.",
        f"- Configuraciones sin ningún estabilizador: {metrics['configurationsWithoutStabilizers']}.",
        f"- Distribución de músculos principales por configuración: {metrics['primaryMuscleCountDistribution']}.",
        f"- Hallazgos bloqueantes (ERROR): {all_errors}, de ellos {visible_errors} en los cuatro campos que la app muestra o dicta "
        "(`definition.description`, `profile.description`, `profile.setupCues`, `profile.executionCues`). El resto está en campos legacy que la fase F1 retira.",
        f"- Avisos (WARN): {sum(row['findings'] for row in rows if row['severity'] == WARN)}; exenciones aplicadas: {len(result.allowlisted)}.",
        "",
        "## Texto compartido entre definiciones distintas",
        "",
    ]
    if sentence_profile:
        lines.append(
            f"- Frases distintas reutilizadas por más de una definición: {sentence_profile['distinctSharedSentences']}; "
            f"la más repetida aparece en {sentence_profile['maxDefinitionsSharingOne']} definiciones."
        )
        for item in sentence_profile.get("top", []):
            lines.append(f"  - {item['definitions']} definiciones: «{item['text']}»")
        lines.append("")
    lines += [
        "| n-grama (palabras) | Mín. palabras de contenido | N-gramas compartidos | Frases afectadas | Definiciones afectadas |",
        "| ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in metrics.get("sharedNgramProfile", []):
        lines.append(
            f"| {row['n']} | {row['minContent']} | {row['sharedNgrams']} | {row['affectedSentences']} | {row['affectedDefinitions']} |"
        )
    lines += [
        "",
        "## Hallazgos por chequeo",
        "",
        "| Chequeo | Severidad | Hallazgos | En texto visible | Definiciones | Configuraciones |",
        "| --- | --- | ---: | ---: | ---: | ---: |",
    ]
    for row in rows:
        lines.append(
            f"| `{row['check']}` | {row['severity']} | {row['findings']} | {row['visible']} | {row['definitions']} | {row['configurations']} |"
        )
    lines += [
        "",
        "## Variedad por tipo de texto (total / únicos)",
        "",
        "| Tipo | Total | Únicos | Únicos / total |",
        "| --- | ---: | ---: | ---: |",
    ]
    for kind, row in metrics["variety"].items():
        ratio = row["unique"] / row["total"] if row["total"] else 0
        lines.append(f"| `{kind}` | {row['total']} | {row['unique']} | {ratio:.2f} |")
    lines += [
        "",
        "## Qué mide cada chequeo",
        "",
        "| Chequeo | Severidad | Qué detecta |",
        "| --- | --- | --- |",
    ]
    for check, (severity, description) in CHECK_DOCS.items():
        lines.append(f"| `{check}` | {severity} | {description} |")
    lines += ["", "## Decisiones de calibración", ""]
    lines += [f"- {note}" for note in CALIBRATION_NOTES]
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="catalog_v2_quality_audit.py",
        description="Read-only editorial quality audit for the exercise catalog v2.",
    )
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--rules", type=Path, default=RULES)
    parser.add_argument("--allowlist", type=Path, default=ALLOWLIST)
    parser.add_argument("--lexicon", type=Path, default=LEXICON)
    parser.add_argument("--fichas", type=Path, default=FICHAS)
    parser.add_argument("--definitions", metavar="ID[,ID...]", help="report only these definition ids (corpus still includes curated text)")
    parser.add_argument("--strict", action="store_true", help="exit 2 when blocking findings exist in scope")
    parser.add_argument("--warnings-as-errors", action="store_true", help="promote warnings to errors")
    parser.add_argument("--include-legacy", action="store_true", help="compare against legacy (uncurated) text too")
    parser.add_argument("--ngram", type=int, default=Options.ngram)
    parser.add_argument("--examples", type=int, default=0, help="print N example findings per check")
    parser.add_argument("--json", type=Path, help="write all findings to this path")
    parser.add_argument("--markdown", type=Path, help="write a markdown report to this path")
    parser.add_argument("--markdown-title", default="Línea base de calidad editorial del catálogo v2")
    return parser


def main(argv: list[str] | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    arguments = build_parser().parse_args(argv)
    try:
        source = read_json(arguments.source)
        rules = read_json(arguments.rules) if arguments.rules.exists() else None
        allowlist = read_json(arguments.allowlist) if arguments.allowlist.exists() else None
        lexicon = read_json(arguments.lexicon) if arguments.lexicon.exists() else None
        fichas = load_fichas(arguments.fichas)
    except (OSError, json.JSONDecodeError) as exc:
        print(f"error leyendo insumos: {exc}", file=sys.stderr)
        return 1
    scope = None
    if arguments.definitions is not None:
        scope = [item.strip() for item in arguments.definitions.split(",")]
        if not scope or any(not item for item in scope):
            print("--definitions requiere ids no vacíos", file=sys.stderr)
            return 1
    options = Options(
        ngram=arguments.ngram,
        warnings_as_errors=arguments.warnings_as_errors,
        include_legacy=arguments.include_legacy,
    )
    try:
        result = run_audit(source, rules=rules, allowlist=allowlist, lexicon=lexicon, fichas=fichas, scope=scope, options=options)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    except (KeyError, TypeError, AttributeError) as exc:
        print(f"datos con forma inesperada ({type(exc).__name__}: {exc}); valida con compile_exercise_catalog_v2.py --check", file=sys.stderr)
        return 1
    print(render_text(result, arguments.examples))
    if arguments.json:
        arguments.json.write_text(
            json.dumps([finding.as_dict() for finding in result.findings], ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    if arguments.markdown:
        arguments.markdown.write_text(
            render_markdown(result, arguments.markdown_title, source.get("catalogRevision", "?")), encoding="utf-8"
        )
    blocking = [finding for finding in result.findings if finding.severity == ERROR]
    if arguments.strict and blocking:
        print(f"STRICT: {len(blocking)} hallazgos bloqueantes", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
