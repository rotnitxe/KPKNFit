#!/usr/bin/env python3
"""Build an immutable, indexed OFF catalog generation. Never truncates to fit.

The manifest pins input bytes and records coverage separately from row count.
USDA/CIQUAL adapters must supply the same canonical record contract; this OFF
adapter must not label their absence as completed coverage.
"""
import argparse
import collections
from contextlib import closing
import hashlib
import gzip
import itertools
import json
import math
import os
from pathlib import Path
import sqlite3
import tempfile
import unicodedata

FORMAT_VERSION = 2
REQUIRED = {"energy": ("energy-kcal_100g", 1000), "protein": ("proteins_100g", 100),
            "carbs": ("carbohydrates_100g", 100), "fat": ("fat_100g", 100)}
OPTIONAL = {"fiber": ("fiber_100g", 100), "sugar": ("sugars_100g", 100),
            "sodium": ("sodium_100g", 5)}


def digest(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def normalize(value):
    return " ".join("".join(c for c in unicodedata.normalize("NFKD", value.lower())
                            if not unicodedata.combining(c)).split())


def number(raw, maximum):
    # bool is a Python integer, but it is not a nutrient declaration.
    if raw is None or isinstance(raw, bool):
        return None
    try:
        n = float(raw)
    except (TypeError, ValueError):
        return None
    return n if math.isfinite(n) and 0 <= n <= maximum else None


def off_record(product):
    code = str(product.get("code") or "").strip()
    name = str(product.get("product_name_es") or product.get("product_name") or "").strip()
    if not code or not name:
        return None, "missing_identity"
    raw = product.get("nutriments") or {}
    structured = (product.get("nutrition") or {}).get("aggregated_set") or {}
    basis = structured.get("per")
    # Legacy _100g keys mean 100g OR 100ml according to OFF's schema. The
    # stripped repository snapshot cannot establish which denominator applies.
    # Retain those identities, but never call them calculable mass-based foods.
    unit = "g" if basis == "100g" else "ml" if basis == "100ml" else "g_or_ml"
    preparation = str(structured.get("preparation") or "as_sold").upper()
    if structured:
        standard = {}
        for column, _ in (REQUIRED | OPTIONAL).values():
            key = column.removesuffix("_100g")
            value = (structured.get("nutrients") or {}).get(key) or {}
            standard[column] = value.get("value") if value.get("value") is not None else value.get("value_computed")
        energy_kj = (structured.get("nutrients") or {}).get("energy") or (structured.get("nutrients") or {}).get("energy-kj") or {}
        standard["energy_100g"] = energy_kj.get("value") if energy_kj.get("value") is not None else energy_kj.get("value_computed")
        raw = standard
    nutrients = {key: {"value": number(raw.get(column), maximum), "kind": "DECLARED", "unit": "kcal" if key == "energy" else "g"}
                 for key, (column, maximum) in (REQUIRED | OPTIONAL).items()}
    # Only explicit per-100g fields are used. Unqualified OFF nutrient keys do
    # not establish a denominator; serving/prepared data cannot replace them.
    if nutrients["energy"]["value"] is None and raw.get("energy-kcal_100g") is None:
        kj = number(raw.get("energy_100g"), 4184)
        if kj is not None:
            nutrients["energy"] = {"value": kj / 4.184, "kind": "DERIVED", "unit": "kcal", "method": "kJ/4.184"}
    for datum in nutrients.values():
        if datum["value"] is None:
            datum["kind"] = "MISSING"
    if structured:
        for key, (column, _) in (REQUIRED | OPTIONAL).items():
            evidence = (structured.get("nutrients") or {}).get(column.removesuffix("_100g")) or {}
            if evidence.get("modifier") in ("<", "<=", ">", ">=", "~"):
                old_value = nutrients[key]["value"]
                nutrients[key].update(value=None, kind="MISSING", qualifier=evidence["modifier"])
                if evidence["modifier"] in ("<", "<="):
                    nutrients[key]["upperBound"] = old_value
            elif evidence.get("value") is None and evidence.get("value_computed") is not None and nutrients[key]["value"] is not None:
                nutrients[key].update(kind="DERIVED", method="OFF_AGGREGATED_COMPUTATION")
    aliases = sorted({str(product[k]).strip() for k in ("product_name", "product_name_es", "product_name_en", "product_name_fr")
                      if product.get(k) and str(product[k]).strip()})
    return {"id": "off_" + code, "source": "OPEN_FOOD_FACTS", "sourceRecordId": code,
            "name": name, "brand": product.get("brands"), "aliases": aliases,
            "spanishName": bool(product.get("product_name_es")),
            "categories": product.get("categories_tags") or [],
            "regions": product.get("countries_tags") or [],
            "languages": sorted((product.get("languages_codes") or {}).keys()),
            "denominator": {"amount": 100, "unit": unit, "preparation": preparation},
            "carbohydrateConvention": "AVAILABLE_EXCLUDES_FIBER",
            "qualityFlags": [] if unit in ("g", "ml") else ["UNVERIFIED_NUTRIENT_BASIS"],
            "physicalForm": "UNKNOWN", "reconstitution": None, "portions": [],
            "nutrients": nutrients,
            "calculable": unit in ("g", "ml") and all(nutrients[k]["value"] is not None for k in REQUIRED)}, None


def products(path):
    # Existing repository snapshots are JSON arrays; full OFF dumps are JSONL
    # and stream without retaining the source in memory.
    with Path(path).open(encoding="utf-8-sig") as stream:
        prefix = stream.read(1)
        stream.seek(0)
        if prefix == "[":
            yield from json.load(stream)
        else:
            for line in stream:
                if line.strip():
                    yield json.loads(line)


def canonical_jsonl(path):
    opener = gzip.open if str(path).endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as stream:
        for line in stream:
            if line.strip():
                yield json.loads(line)


def build(source, destination, release, minimum=60000, max_bytes=300_000_000, reserved_bytes=0,
          extra_records=(), extra_sources=()):
    source, destination = Path(source), Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    source_hash = digest(source)
    builder_sources = {"build_nutrition_catalog_pack.py": digest(Path(__file__))}
    usda_adapter = Path(__file__).with_name("nutrition_catalog_usda.py")
    if usda_adapter.is_file():
        builder_sources[usda_adapter.name] = digest(usda_adapter)
    ciqual_adapter = Path(__file__).with_name("nutrition_catalog_ciqual.py")
    if ciqual_adapter.is_file():
        builder_sources[ciqual_adapter.name] = digest(ciqual_adapter)
    # The release label is included in the generation identity, not just the bytes.
    generation = hashlib.sha256(json.dumps([FORMAT_VERSION, release, source_hash, list(extra_sources), builder_sources], sort_keys=True).encode()).hexdigest()
    target = destination / (generation + ".sqlite")
    fd, temporary = tempfile.mkstemp(prefix="catalog-", suffix=".pending", dir=destination)
    os.close(fd)
    counts, rejected = collections.Counter(), collections.Counter()
    by_source = {}
    categories, regions, languages = set(), set(), set()
    try:
        with closing(sqlite3.connect(temporary)) as db:
            db.executescript("""
                PRAGMA journal_mode=DELETE;
                CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL);
                CREATE TABLE foods(id TEXT PRIMARY KEY, name TEXT NOT NULL, brand TEXT,
                    calculable INTEGER NOT NULL, data TEXT NOT NULL);
                CREATE VIRTUAL TABLE search USING fts4(id, text);
            """)
            canonical_records = itertools.chain((off_record(product) for product in products(source)),
                                                ((record, "invalid_source_record" if record is None else None) for record in extra_records))
            for record, reason in canonical_records:
                counts["inputRows"] += 1
                if record is None:
                    rejected[reason] += 1
                    continue
                encoded = json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
                # Duplicate identities are an input error, never silently overwritten.
                db.execute("INSERT INTO foods VALUES(?,?,?,?,?)", (record["id"], record["name"], record["brand"], int(record["calculable"]), encoded))
                db.execute("INSERT INTO search VALUES(?,?)", (record["id"], normalize(" ".join(record["aliases"] + [record["brand"] or ""]))))
                counts["identities"] += 1
                counts["calculableIdentities"] += int(record["calculable"])
                counts["numericallyCompleteIdentities"] += int(all(record["nutrients"][key]["value"] is not None for key in REQUIRED))
                counts["unverifiedNutrientBasis"] += int(record["denominator"]["unit"] not in ("g", "ml", "serving"))
                counts["withSpanishName"] += int(record["spanishName"])
                counts["missingNutrients"] += sum(d["kind"] == "MISSING" for d in record["nutrients"].values())
                counts["authoritativePortions"] += len(record.get("portions", []))
                counts["withCategory"] += bool(record["categories"])
                counts["withRegion"] += bool(record["regions"])
                counts["withLanguage"] += bool(record["languages"])
                source_counts = by_source.setdefault(record["source"], collections.Counter())
                source_counts["identities"] += 1
                source_counts["calculableIdentities"] += int(record["calculable"])
                source_counts["authoritativePortions"] += len(record.get("portions", []))
                categories.update(record["categories"])
                regions.update(record["regions"])
                languages.update(record["languages"])
            db.execute("INSERT INTO metadata VALUES('generation',?)", (generation,))
            db.execute("INSERT INTO metadata VALUES('formatVersion',?)", (str(FORMAT_VERSION),))
            db.execute("INSERT INTO search(search) VALUES('optimize')")
            db.commit()
        size = os.path.getsize(temporary)
        report = {"formatVersion": FORMAT_VERSION, "generation": generation, "builderSources": builder_sources, "source": {
            "name": "OPEN_FOOD_FACTS", "release": release, "inputSha256": source_hash,
            "license": "ODbL-1.0", "url": "https://world.openfoodfacts.org/data"},
            "additionalSources": list(extra_sources), "bySource": {key: dict(value) for key, value in sorted(by_source.items())},
            "counts": dict(counts), "rejected": dict(rejected), "categories": sorted(categories),
            "regions": sorted(regions), "languages": sorted(languages),
            "catalogBytes": size, "reservedModelAndRuntimeBytes": reserved_bytes,
            "budgetKind": "uncompressed_payload_preflight_only", "apkGrowthAccepted": False,
            "sha256": digest(temporary), "minimumCalculableIdentities": minimum,
            "coveragePassed": counts["calculableIdentities"] >= minimum,
            "budgetPassed": size + reserved_bytes <= max_bytes,
            "releaseAccepted": False,
            "pending": ["Spanish concept mapping", "derived database redistribution bundle",
                        "independent accuracy and Android performance evaluation"] +
                       (["CIQUAL release and license pin"] if "CIQUAL" not in by_source else []) +
                       (["OFF explicit 100g/100ml basis metadata"] if counts["unverifiedNutrientBasis"] else []) +
                       (["USDA source coverage"] if not {"USDA_FOUNDATION", "USDA_FNDDS", "USDA_SR_LEGACY"}.issubset(by_source) else [])}
        # An over-budget generation leaves a report, never a silently cut catalog.
        report_path = destination / (generation + (".json" if report["budgetPassed"] else ".rejected.json"))
        report_path.write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        if not report["budgetPassed"]:
            raise ValueError("Catalog plus reserved model/runtime exceeds size budget; see " + str(report_path))
        os.replace(temporary, target)
        return report
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--release", required=True, help="Exact source release or explicit local snapshot label")
    parser.add_argument("--minimum", type=int, default=60000)
    parser.add_argument("--max-bytes", type=int, default=300_000_000)
    parser.add_argument("--reserved-bytes", type=int, default=0)
    parser.add_argument("--usda-directory", type=Path)
    parser.add_argument("--usda-lock", type=Path)
    parser.add_argument("--usda-aliases", type=Path)
    parser.add_argument("--ciqual-directory", type=Path)
    parser.add_argument("--ciqual-lock", type=Path)
    parser.add_argument("--canonical-jsonl", type=Path)
    parser.add_argument("--canonical-audit", type=Path)
    args = parser.parse_args()
    if min(args.minimum, args.max_bytes, args.reserved_bytes) < 0:
        parser.error("budgets and minimum must be nonnegative")
    extra_records, extra_sources = (), []
    if args.usda_directory or args.usda_lock:
        if not (args.usda_directory and args.usda_lock):
            parser.error("--usda-directory and --usda-lock must be supplied together")
        from nutrition_catalog_usda import locked_sources, records
        lock = locked_sources(args.usda_directory, args.usda_lock)
        extra_records = records(args.usda_directory, lock, args.usda_aliases)
        extra_sources = [{"name": name, **metadata} for name, metadata in sorted(lock.items())]
        if args.usda_aliases:
            extra_sources.append({"name": "USDA_SPANISH_ALIASES", "sha256": digest(args.usda_aliases)})
    if args.ciqual_directory or args.ciqual_lock:
        if not (args.ciqual_directory and args.ciqual_lock):
            parser.error("--ciqual-directory and --ciqual-lock must be supplied together")
        from nutrition_catalog_ciqual import locked_source, records as ciqual_records
        lock = locked_source(args.ciqual_directory, args.ciqual_lock)
        extra_records = itertools.chain(extra_records, ciqual_records(args.ciqual_directory, lock))
        extra_sources.append({"name": "CIQUAL", **lock})
    if args.canonical_jsonl or args.canonical_audit:
        if not (args.canonical_jsonl and args.canonical_audit):
            parser.error("--canonical-jsonl and --canonical-audit must be supplied together")
        audit = json.loads(args.canonical_audit.read_text(encoding="utf-8"))
        if digest(args.canonical_jsonl) != audit["outputSha256"]:
            parser.error("Canonical input bytes differ from their audit manifest")
        extra_sources.append({"name": "USDA_BRANDED_LABELS", **audit})
        extra_records = itertools.chain(extra_records, canonical_jsonl(args.canonical_jsonl))
    report = build(args.source, args.destination, args.release, args.minimum, args.max_bytes, args.reserved_bytes,
                   extra_records, extra_sources)
    print(json.dumps(report, ensure_ascii=True, sort_keys=True))


if __name__ == "__main__":
    main()
