#!/usr/bin/env python3
"""Builds the OFF search fixture used by SearchGoldenCorpusTest (WP-S2).

    python scripts/build_off_search_fixture.py

Reads android-native/app/src/main/assets/food_data/off_chile.csv (tab separated, NO header row, 200+ columns) and writes
android-native/app/src/test/resources/food_data/off_chile_search_fixture.tsv: for each head word of HEADS, up to
PER_HEAD lines that FoodImporter would import, plus the DECOYS (rows whose name only CONTAINS a word fragment, the kind
of noise a substring search returned: empanaditas for "pan") and the MUST_INCLUDE codes. Only the columns the importer
reads are kept, the other ones are left blank (the last one holds a '-' so no line ends in whitespace), so each line parses
with the same column indices as the real file:

    0 code | 10 product_name | 18 brands | 89 energy-kcal_100g | 92 fat_100g | 129 carbohydrates_100g
    130 sugars_100g | 146 fiber_100g | 150 proteins_100g | 156 sodium_100g

The output is deterministic (stdlib only, no randomness, stable orderings): running it twice yields the same bytes, and
the lines keep the order they have in off_chile.csv. Re-run it only when HEADS changes, and re-read the expectations of
SearchGoldenCorpusTest afterwards, because the corpus asserts rows of this file.
"""
import argparse
import os
import re
import sys
import unicodedata

PER_HEAD = 10
COLUMNS = 160
USED = {"code": 0, "name": 10, "brand": 18, "kcal": 89, "fat": 92, "carb": 129, "sugar": 130, "fiber": 146, "prot": 150, "sodium": 156}

# Head word (or phrase) -> word fragments that are NOT the head but contain it ("decoys", at most 2 lines each).
HEADS = [
    ("leche", ["lechuga", "lechera"]),
    ("dulce de leche", []),
    ("pan", ["empanad", "panch", "pancake", "biopan", "panqu"]),
    ("huevo", []),
    ("pollo", []),
    ("arroz", []),
    ("palta", []),
    ("aguacate", []),
    ("platano", []),
    ("banana", []),
    ("papa", ["papaya"]),
    ("tomate", []),
    ("yogurt", []),
    ("galleta", []),
    ("coca cola", []),
    ("red bull", []),
    ("nuggets", []),
    ("completo", []),
    ("empanada", []),
    ("sopaipilla", []),
    ("cazuela", []),
    ("whey", []),
    ("hallulla", []),
    ("marraqueta", []),
    ("queso", []),
    ("jamon", []),
    ("atun", []),
    ("salmon", []),
    ("lenteja", []),
    ("avena", []),
    ("aceite", []),
    ("fideos", []),
    ("chocolate", []),
    ("jugo", []),
    ("agua", []),
    ("cafe", []),
    ("helado", []),
    ("manzana", []),
    ("pizza", []),
    ("carne", []),
]

# Rows the corpus asserts by barcode that the head rules above would not necessarily pick.
MUST_INCLUDE = [
    "7802920007595",  # Leche Descremada, COLUN (a real Colun SKU for "leche colun")
    "7802920009315",  # Sin Lactosa mi LECHE NATURAL ENTERA, COLUN
    "0842626777542",  # mi Leche Entera, Colun
    "0721450761012",  # DULCE DE LECHE & CO. (brand repeats the product: the head-noun adversary of "leche")
    "7891962064055",  # Pan Integral, Bauducco
    "7891962056746",  # Pan Blanco, Bauducco
    "7804651843251",  # Hallulla, Tottus
    "7111610001196",  # Coca-Cola Sabor Original
    "0040469200030",  # Red Bull Energy Drink
    "7801930014081",  # NUGGETS DE POLLO, Receta del Abuelo
]


def normalize(text):
    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return re.sub(r"\s+", " ", re.sub(r"[^\w]+|_", " ", text.lower())).strip()


def number(value):
    try:
        return float(value)
    except ValueError:
        return None


def bounded(value, maximum):
    parsed = number(value)
    if parsed is None or parsed != parsed or parsed < 0 or parsed > maximum:
        return 0.0
    return parsed


def importable(parts):
    """The checks FoodImporter.parseOffLine applies to an OFF line before it keeps it (WP-S8: the fixture is parsed by it too)."""
    if len(parts) <= USED["sodium"] or not parts[USED["code"]].strip() or not parts[USED["name"]].strip():
        return False
    kcal = bounded(parts[USED["kcal"]], 1000.0)
    prot = bounded(parts[USED["prot"]], 100.0)
    fat = bounded(parts[USED["fat"]], 100.0)
    carb = bounded(parts[USED["carb"]], 100.0)
    sodium_raw = parts[USED["sodium"]]
    sodium = number(sodium_raw)
    if sodium is not None and not 0.0 <= sodium <= 5.0:
        sodium = None
    if sodium_raw.strip() and sodium is None:
        return False
    macro = prot * 4.0 + fat * 9.0 + carb * 4.0
    if not (kcal > 0.0 and macro > 0.0):
        return False
    return abs(kcal - macro) / macro <= 0.5


def variants(word):
    """A word and its plain Spanish plural/singular spellings."""
    forms = {word, word + "s", word + "es"}
    if word.endswith("s") and len(word) > 3:
        forms.add(word[:-1])
    return forms


def has_head(words, head):
    """True when the head's words appear in the name, in order and next to each other, modulo plural."""
    head_words = head.split()
    for start in range(0, len(words) - len(head_words) + 1):
        if all(words[start + i] in variants(head_words[i]) for i in range(len(head_words))):
            return True
    return False


def head_rank(name, head):
    if name in variants(head):
        return 0
    return 1 if name.startswith(head) else 2


def build(src):
    records = []
    with open(src, encoding="utf-8", newline="") as handle:
        for line_number, line in enumerate(handle):
            line = line.rstrip("\n").rstrip("\r")
            parts = line.split("\t")
            if not importable(parts):
                continue
            name = normalize(parts[USED["name"]])
            records.append({"line": line_number, "parts": parts, "name": name, "words": name.split(), "code": parts[0].strip()})

    chosen = {}
    for head, decoys in HEADS:
        pool = [r for r in records if has_head(r["words"], head)]
        pool.sort(key=lambda r: (head_rank(r["name"], head), len(r["name"]), r["code"]))
        for record in pool[:PER_HEAD]:
            chosen[record["line"]] = record
        for fragment in decoys:
            decoy_pool = [r for r in records if any(fragment in w and w not in variants(head) for w in r["words"])]
            decoy_pool.sort(key=lambda r: (len(r["name"]), r["code"]))
            for record in decoy_pool[:2]:
                chosen[record["line"]] = record
    by_code = {r["code"]: r for r in records}
    for code in MUST_INCLUDE:
        if code not in by_code:
            sys.exit("MUST_INCLUDE code %s is not an importable line of %s" % (code, src))
        chosen[by_code[code]["line"]] = by_code[code]

    lines = []
    for line_number in sorted(chosen):
        record = chosen[line_number]
        columns = [""] * COLUMNS
        for index in USED.values():
            columns[index] = record["parts"][index].strip()
        columns[-1] = "-"  # sentinel: no line ends in a tab (git flags trailing whitespace); the importer ignores columns > 156
        lines.append("\t".join(columns))
    return lines


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    default_src = os.path.join(root, "android-native", "app", "src", "main", "assets", "food_data", "off_chile.csv")
    default_out = os.path.join(root, "android-native", "app", "src", "test", "resources", "food_data", "off_chile_search_fixture.tsv")
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--src", default=default_src)
    parser.add_argument("--out", default=default_out)
    args = parser.parse_args()
    lines = build(args.src)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")
    print("wrote %d rows to %s" % (len(lines), args.out))


if __name__ == "__main__":
    main()
