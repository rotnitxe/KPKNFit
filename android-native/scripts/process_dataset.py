"""Compile the 19,405 nutrition examples into a compact offline search asset."""

from __future__ import annotations

import gzip
import hashlib
import json
import math
import re
import struct
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path
from statistics import median

ROOT = Path.cwd()
DATASET_PATH = ROOT / "DATASET_KPKN_TRINIDAD_MASTER.json"
OUTPUT_DIR = ROOT / "app/src/main/assets/food_data"
OUTPUT_PATH = OUTPUT_DIR / "dataset_knowledge.bin"
REPORT_PATH = OUTPUT_DIR / "dataset_knowledge_report.json"
CHECK_ONLY = "--check" in sys.argv[1:]
FORMAT_VERSION = 2
MAGIC = b"KPKNDS02"
MAX_TOKEN_POSTINGS = 64
MAX_TRIGRAM_POSTINGS = 32
MAX_TOKEN_DF_RATIO = 0.35
MAX_TRIGRAM_DF = 600

STOPWORDS = {
    "de", "la", "el", "con", "sin", "a", "al", "en", "por", "y", "o", "un",
    "una", "unos", "unas", "del", "las", "los", "lo", "para", "que", "es", "su",
    "se", "no", "más", "mas", "como", "le", "me", "te", "mi", "tu", "muy", "ya",
    "si", "pero", "porque", "cuando", "donde", "cual", "quien", "este", "esta",
    "ese", "esa", "todos", "todas", "todo", "toda", "otro", "otra", "otros",
    "otras", "mismo", "misma", "cada", "sobre", "entre", "hasta", "desde", "hacia",
    "hay", "son", "fue", "era", "tiene", "puede", "debe", "calcula", "cuáles",
    "cuales", "oficiales", "gramos", "calorías", "calorias", "macros",
}

CONTEXT_KEYWORDS = {
    "CASINO": ("casino", "cafeteria", "comedor", "buffet", "menu del dia"),
    "POST_ENTRENO": ("post entreno", "post entrenamiento", "recuperacion", "post workout"),
    "POWERBUILDER": ("powerbuilder", "power builder", "volumen extremo", "masa extrema", "volumen sucio"),
    "ABUELA_CHILENA": ("abuela chilena", "abuela", "contundente", "plato hondo", "plato rebosante"),
    "OFICINA": ("oficina", "escritorio", "trabajo", "reunion"),
    "ESTUDIANTE": ("estudiante", "universidad", "facultad", "campus", "corto de lucas"),
    "CONDOMINIO": ("condominio", "edificio", "departamento"),
    "SNACK": ("snack", "colacion", "merienda", "tentempie", "refrigerio"),
    "DESAYUNO": ("desayuno", "desayunar", "manana"),
    "ALMUERZO": ("almuerzo", "almorzar", "mediodia"),
    "CENA": ("cena", "cenar", "noche", "nocturno", "antes de dormir"),
}

COOKING_KEYWORDS = (
    "plancha", "horno", "frito", "frita", "cocido", "cocida", "hervido", "hervida",
    "crudo", "cruda", "vapor", "parrilla", "asado", "asada", "guisado", "guisada",
    "ahumado", "ahumada", "salteado", "salteada",
)

GRAM_FOOD_PATTERN = re.compile(
    r"(\d+(?:[.,]\d+)?)\s*g\s+(?:de\s+)?([a-záéíóúñü][a-záéíóúñü\s,'\"-]*?)"
    r"(?=\s*,|\s+y\s+|\s+con\s+|\s*\(|\.|$)",
    re.IGNORECASE,
)
PAREN_GRAM_PATTERN = re.compile(
    r"([a-záéíóúñü][a-záéíóúñü\s,'\"-]*?)\s*\((\d+(?:[.,]\d+)?)\s*g\)",
    re.IGNORECASE,
)


def normalize(text: object) -> str:
    decomposed = unicodedata.normalize("NFD", str(text or "").lower())
    without_marks = "".join(char for char in decomposed if unicodedata.category(char) != "Mn")
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9\s]", " ", without_marks)).strip()


def tokenize(text: object) -> list[str]:
    return [token for token in normalize(text).split() if len(token) >= 2 and token not in STOPWORDS]


def trigrams(token: str) -> set[str]:
    if len(token) < 3:
        return {token}
    padded = f"${token}$"
    return {padded[index:index + 3] for index in range(len(padded) - 2)}


def classify(instruction: str) -> str:
    value = normalize(instruction)
    if any(term in value for term in ("base de datos", "valores nutricionales oficiales", "macros oficiales")):
        return "DATABASE_LOOKUP"
    if any(term in value for term in ("calcula macros", "calcula las calorias", "dime los macronutrientes", "datos nutricionales")):
        return "MACRO_CALC"
    if "describe" in value:
        return "DESCRIBE"
    if "?" in instruction:
        return "QUESTION"
    return "GENERAL"


def detect_contexts(text: str) -> list[str]:
    value = normalize(text)
    return [
        context
        for context, keywords in CONTEXT_KEYWORDS.items()
        if any(keyword in value for keyword in keywords)
    ]


def number(value: object, maximum: float) -> float | None:
    try:
        parsed = float(str(value).replace(",", "."))
    except (TypeError, ValueError):
        return None
    return parsed if math.isfinite(parsed) and 0 <= parsed <= maximum else None


def first_number(text: str, patterns: tuple[str, ...], maximum: float) -> float | None:
    for pattern in patterns:
        match = re.search(pattern, text, re.IGNORECASE)
        if match:
            parsed = number(match.group(1), maximum)
            if parsed is not None:
                return parsed
    return None


def parse_output(output: str) -> dict[str, object] | None:
    value = output.strip()
    if not value:
        return None
    if value.startswith(("[", "{")):
        try:
            parsed = json.loads(value)
            items = parsed if isinstance(parsed, list) else [parsed]
            nutrition = []
            for item in items:
                if not isinstance(item, dict):
                    continue
                row = {
                    "food": str(item.get("alimento") or item.get("food") or "").strip(),
                    "kcal": number(item.get("kcal", item.get("calorias", item.get("calories"))), 5000),
                    "protein": number(item.get("P", item.get("proteina", item.get("protein"))), 1000),
                    "fats": number(item.get("G", item.get("grasas", item.get("fats", item.get("fat")))), 1000),
                    "carbs": number(item.get("C", item.get("carbohidratos", item.get("carbs"))), 1500),
                }
                if row["kcal"] is not None or row["protein"] is not None:
                    nutrition.append(row)
            if nutrition:
                return {
                    "kcal": sum(float(row["kcal"] or 0) for row in nutrition),
                    "protein": sum(float(row["protein"] or 0) for row in nutrition),
                    "fats": sum(float(row["fats"] or 0) for row in nutrition),
                    "carbs": sum(float(row["carbs"] or 0) for row in nutrition),
                    "foods": [row["food"] for row in nutrition if row["food"]],
                    "item_count": len(nutrition),
                }
        except (json.JSONDecodeError, TypeError):
            pass

    kcal = first_number(value, (r"calor[ií]as?\s*:\s*(\d+(?:[.,]\d+)?)", r"(\d+(?:[.,]\d+)?)\s*kcal"), 5000)
    protein = first_number(value, (r"prote[ií]nas?\s*:\s*(\d+(?:[.,]\d+)?)", r"(\d+(?:[.,]\d+)?)\s*g?\s*P(?:\s|/|\||$)"), 1000)
    fats = first_number(value, (r"grasas?\s*:\s*(\d+(?:[.,]\d+)?)", r"(\d+(?:[.,]\d+)?)\s*g?\s*G(?:\s|/|\||$)"), 1000)
    carbs = first_number(value, (r"carbohidratos?\s*:\s*(\d+(?:[.,]\d+)?)", r"(\d+(?:[.,]\d+)?)\s*g?\s*C(?:\s|/|\||$)"), 1500)
    if all(item is None for item in (kcal, protein, fats, carbs)):
        return None
    return {
        "kcal": kcal or 0.0,
        "protein": protein or 0.0,
        "fats": fats or 0.0,
        "carbs": carbs or 0.0,
        "foods": [],
        "item_count": 0,
    }


def clean_food(value: str) -> str:
    return re.sub(
        r"\s+",
        " ",
        re.sub(r"\b(?:cocid[oa]|frit[oa]|asad[oa]|hornead[oa]|plancha|horno)\b", " ", normalize(value)),
    ).strip()


def extract_portions(instruction: str) -> list[dict[str, object]]:
    portions: dict[tuple[str, float], dict[str, object]] = {}
    for match in GRAM_FOOD_PATTERN.finditer(instruction):
        grams = number(match.group(1), 2000)
        food = clean_food(match.group(2))
        if grams and len(food) >= 2:
            portions[(food, grams)] = {"food": food, "grams": grams}
    for match in PAREN_GRAM_PATTERN.finditer(instruction):
        grams = number(match.group(2), 2000)
        food = clean_food(match.group(1))
        if grams and len(food) >= 2:
            portions[(food, grams)] = {"food": food, "grams": grams}
    return list(portions.values())


def derive_macro_basis(
    instruction: str,
    macros: dict[str, object] | None,
    portions: list[dict[str, object]],
) -> tuple[int, float, dict[str, object] | None]:
    if macros is None:
        return 0, 0.0, None
    total_grams = sum(float(item["grams"]) for item in portions)
    single_food = len(portions) == 1 or macros["item_count"] == 1
    if re.search(r"\b100\s*(?:g|gramos?)\b", normalize(instruction)) and single_food:
        return 1, 100.0, macros
    if len(portions) == 1 and total_grams > 0:
        factor = 100.0 / total_grams
        normalized_macros = dict(macros)
        for key in ("kcal", "protein", "fats", "carbs"):
            normalized_macros[key] = float(macros[key]) * factor
        return 1, 100.0, normalized_macros
    return 2, total_grams, macros


class Writer:
    def __init__(self) -> None:
        self.parts: list[bytes] = []

    def raw(self, value: bytes) -> None:
        self.parts.append(value)

    def u8(self, value: int) -> None:
        self.parts.append(struct.pack(">B", value))

    def u16(self, value: int) -> None:
        self.parts.append(struct.pack(">H", value))

    def i32(self, value: int) -> None:
        self.parts.append(struct.pack(">i", value))

    def f32(self, value: float) -> None:
        self.parts.append(struct.pack(">f", value))

    def f64(self, value: float) -> None:
        self.parts.append(struct.pack(">d", value))

    def string(self, value: object) -> None:
        encoded = str(value).encode("utf-8")
        self.i32(len(encoded))
        self.parts.append(encoded)

    def build(self) -> bytes:
        return b"".join(self.parts)


raw_dataset = DATASET_PATH.read_bytes()
checksum = hashlib.sha256(raw_dataset).hexdigest()
source = json.loads(raw_dataset)
if not isinstance(source, list):
    raise ValueError("Dataset root must be an array")

documents: list[dict[str, object]] = []
for doc_id, entry in enumerate(source):
    instruction = str(entry.get("instruction") or "").strip()
    output = str(entry.get("output") or "").strip()
    doc_tokens = tokenize(instruction)
    token_counts = Counter(doc_tokens)
    doc_trigrams = set().union(*(trigrams(token) for token in doc_tokens)) if doc_tokens else set()
    portions = extract_portions(instruction)
    macro_basis, basis_grams, macros = derive_macro_basis(instruction, parse_output(output), portions)
    normalized_instruction = normalize(instruction)
    documents.append({
        "id": doc_id,
        "instruction": instruction[:500],
        "type": classify(instruction),
        "contexts": detect_contexts(instruction),
        "cooking": [keyword for keyword in COOKING_KEYWORDS if keyword in normalized_instruction],
        "tokens": doc_tokens,
        "token_counts": token_counts,
        "trigrams": doc_trigrams,
        "portions": portions,
        "macro_basis": macro_basis,
        "basis_grams": basis_grams,
        "macros": macros,
        "norm": 0.0,
    })

token_documents: dict[str, list[int]] = defaultdict(list)
trigram_documents: dict[str, list[int]] = defaultdict(list)
for document in documents:
    for token in document["token_counts"]:
        token_documents[token].append(int(document["id"]))
    for trigram in document["trigrams"]:
        trigram_documents[trigram].append(int(document["id"]))

max_token_df = int(len(documents) * MAX_TOKEN_DF_RATIO)
token_entries = []
for token, doc_ids in token_documents.items():
    if len(doc_ids) <= max_token_df:
        idf = math.log((len(documents) + 1) / (len(doc_ids) + 1)) + 1
        token_entries.append({"token": token, "doc_ids": doc_ids, "idf": idf})
token_entries.sort(key=lambda item: (-float(item["idf"]) * math.log1p(len(item["doc_ids"])), str(item["token"])))

selected_tokens = {str(item["token"]) for item in token_entries}
for document in documents:
    squared = 0.0
    token_total = max(1, len(document["tokens"]))
    for token, count in document["token_counts"].items():
        if token not in selected_tokens:
            continue
        idf = math.log((len(documents) + 1) / (len(token_documents[token]) + 1)) + 1
        weight = count / token_total * idf
        squared += weight * weight
    document["norm"] = math.sqrt(squared)

compiled_tokens = []
for entry in token_entries:
    postings = []
    for doc_id in entry["doc_ids"]:
        document = documents[doc_id]
        count = document["token_counts"].get(entry["token"], 0)
        weight = count / max(1, len(document["tokens"])) * float(entry["idf"])
        postings.append((doc_id, weight))
    postings.sort(key=lambda item: (-item[1], item[0]))
    compiled_tokens.append({
        "token": entry["token"],
        "idf": entry["idf"],
        "postings": postings[:MAX_TOKEN_POSTINGS],
    })

compiled_trigrams = [
    {"trigram": trigram, "doc_ids": doc_ids[:MAX_TRIGRAM_POSTINGS]}
    for trigram, doc_ids in sorted(trigram_documents.items())
    if 2 <= len(doc_ids) <= MAX_TRIGRAM_DF
]

global_portions: dict[str, list[float]] = defaultdict(list)
for document in documents:
    for portion in document["portions"]:
        global_portions[str(portion["food"])].append(float(portion["grams"]))
portion_priors = sorted(
    (
        {"food": food, "grams": median(grams), "frequency": len(grams)}
        for food, grams in global_portions.items()
    ),
    key=lambda item: (-int(item["frequency"]), str(item["food"])),
)

context_profiles = []
for context in CONTEXT_KEYWORDS:
    matching = [document for document in documents if context in document["contexts"]]
    macro_rows = [document["macros"] for document in matching if document["macros"] is not None]
    portion_grams = [
        float(portion["grams"])
        for document in matching
        for portion in document["portions"]
    ]
    context_profiles.append({
        "context": context,
        "count": len(matching),
        "grams": median(portion_grams) if portion_grams else 0.0,
        "kcal": median([float(row["kcal"]) for row in macro_rows]) if macro_rows else 0.0,
        "protein": median([float(row["protein"]) for row in macro_rows]) if macro_rows else 0.0,
        "fats": median([float(row["fats"]) for row in macro_rows]) if macro_rows else 0.0,
        "carbs": median([float(row["carbs"]) for row in macro_rows]) if macro_rows else 0.0,
    })

writer = Writer()
writer.raw(MAGIC)
writer.i32(FORMAT_VERSION)
writer.string(checksum)
writer.i32(len(documents))
for document in documents:
    writer.i32(int(document["id"]))
    writer.string(document["instruction"])
    writer.string(document["type"])
    writer.u8(len(document["contexts"]))
    for context in document["contexts"]:
        writer.string(context)
    writer.u8(len(document["cooking"]))
    for cooking in document["cooking"]:
        writer.string(cooking)
    writer.u8(int(document["macro_basis"]))
    writer.f64(float(document["basis_grams"]))
    macros = document["macros"]
    writer.u8(1 if macros else 0)
    if macros:
        writer.f64(float(macros["kcal"]))
        writer.f64(float(macros["protein"]))
        writer.f64(float(macros["fats"]))
        writer.f64(float(macros["carbs"]))
    writer.u16(len(document["portions"]))
    for portion in document["portions"]:
        writer.string(portion["food"])
        writer.f64(float(portion["grams"]))
    writer.f32(float(document["norm"]))
    writer.u16(min(len(document["trigrams"]), 65535))

writer.i32(len(compiled_tokens))
for entry in compiled_tokens:
    writer.string(entry["token"])
    writer.f32(float(entry["idf"]))
    writer.u16(len(entry["postings"]))
    for doc_id, weight in entry["postings"]:
        writer.i32(doc_id)
        writer.f32(weight)

writer.i32(len(compiled_trigrams))
for entry in compiled_trigrams:
    writer.string(entry["trigram"])
    writer.u16(len(entry["doc_ids"]))
    for doc_id in entry["doc_ids"]:
        writer.i32(doc_id)

writer.i32(len(portion_priors))
for prior in portion_priors:
    writer.string(prior["food"])
    writer.f64(float(prior["grams"]))
    writer.i32(int(prior["frequency"]))

writer.i32(len(context_profiles))
for profile in context_profiles:
    writer.string(profile["context"])
    writer.i32(int(profile["count"]))
    for key in ("grams", "kcal", "protein", "fats", "carbs"):
        writer.f64(float(profile[key]))

binary = writer.build()
compressed = gzip.compress(binary, compresslevel=9, mtime=0)

invalid_postings = sum(
    1
    for entry in compiled_tokens
    for doc_id, _ in entry["postings"]
    if not 0 <= doc_id < len(documents)
)
report = {
    "formatVersion": FORMAT_VERSION,
    "checksum": checksum,
    "sourceEntries": len(source),
    "runtimeDocuments": len(documents),
    "documentsWithMacros": sum(document["macros"] is not None for document in documents),
    "documentsWithPer100gMacros": sum(document["macro_basis"] == 1 for document in documents),
    "documentsWithTotalMacros": sum(document["macro_basis"] == 2 for document in documents),
    "documentsWithPortions": sum(bool(document["portions"]) for document in documents),
    "tokenCount": len(compiled_tokens),
    "trigramCount": len(compiled_trigrams),
    "portionPriorCount": len(portion_priors),
    "contextProfileCount": len(context_profiles),
    "invalidPostingCount": invalid_postings,
    "rawBytes": len(binary),
    "compressedBytes": len(compressed),
    "typeCounts": dict(sorted(Counter(str(document["type"]) for document in documents).items())),
}
report_text = json.dumps(report, indent=2, ensure_ascii=False) + "\n"
if invalid_postings:
    raise ValueError(f"Generated {invalid_postings} invalid postings")

if CHECK_ONLY:
    if not OUTPUT_PATH.exists() or OUTPUT_PATH.read_bytes() != compressed:
        raise SystemExit("dataset_knowledge.bin is stale; run python3 scripts/process_dataset.py")
    if not REPORT_PATH.exists() or REPORT_PATH.read_text(encoding="utf-8") != report_text:
        raise SystemExit("dataset_knowledge_report.json is stale; run python3 scripts/process_dataset.py")
else:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_bytes(compressed)
    REPORT_PATH.write_text(report_text, encoding="utf-8")
print(json.dumps(report, indent=2, ensure_ascii=False))
