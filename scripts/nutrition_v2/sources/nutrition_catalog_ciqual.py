"""CIQUAL2025 XML adapter. Values below a limit and traces are never declared zeros."""
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from build_nutrition_catalog_pack import digest, number, normalize

TARGETS = {
    "328": ("energy", "kcal", 1000), "333": ("energy_jones", "kcal", 1000),
    "25003": ("protein", "g", 100), "25000": ("protein_jones", "g", 100),
    "31000": ("carbs", "g", 100), "40000": ("fat", "g", 100),
    "34100": ("fiber", "g", 100), "32000": ("sugar", "g", 100),
    "34000": ("polyols", "g", 100), "10110": ("sodium", "mg", 100000),
    "10190": ("potassium", "mg", 100000), "400": ("water", "g", 100),
    "60000": ("alcohol", "g", 100),
}


def rows(path, tag):
    for _, item in ET.iterparse(path, events=("end",)):
        if item.tag == tag:
            yield {child.tag: (child.text or "").strip() for child in item}
            item.clear()


def datum(text, unit, maximum):
    text = str(text or "").strip()
    value = number(text.replace(",", "."), maximum)
    result = {"value": value, "kind": "MISSING" if value is None else "DECLARED", "unit": unit}
    if value is None and text:
        result["sourceText"] = text
        below = re.fullmatch(r"<\s*(\d+(?:[.,]\d+)?)", text)
        if below:
            result["qualifier"] = "BELOW_LIMIT"
            result["upperBound"] = number(below.group(1).replace(",", "."), maximum)
        elif text.lower() in ("traces", "trace"):
            result["qualifier"] = "TRACE"
    return result


def locked_source(directory, lock_path):
    lock = json.loads(Path(lock_path).read_text(encoding="utf-8"))
    if lock.get("datasetVersion") != "1.0" or lock.get("release") != "2025-11-03" or lock.get("license") != "Etalab-2.0":
        raise ValueError("Unexpected CIQUAL release or license")
    for name, info in lock["files"].items():
        if Path(name).name != name or digest(Path(directory) / name) != info["sha256"]:
            raise ValueError("CIQUAL source hash or filename mismatch: " + name)
    return lock


def records(directory, lock):
    root = Path(directory)
    nutrients = {}
    for row in rows(root / "compo_2025_11_03.xml", "COMPO"):
        target = TARGETS.get(row["const_code"])
        if target is None:
            continue
        key, unit, maximum = target
        value = datum(row.get("teneur"), unit, maximum)
        value["sourceConstituentId"] = row["const_code"]
        if key in ("energy", "energy_jones") and value["value"] is not None:
            value.update(kind="DERIVED", method="CIQUAL_EU1169_2011" if key == "energy" else "CIQUAL_JONES_WITH_FIBER")
        nutrients.setdefault(row["alim_code"], {})[key] = value
    groups = {}
    for row in rows(root / "alim_grp_2025_11_03.xml", "ALIM_GRP"):
        for code, name in (("alim_grp_code", "alim_grp_nom_fr"), ("alim_ssgrp_code", "alim_ssgrp_nom_fr"), ("alim_ssssgrp_code", "alim_ssssgrp_nom_fr")):
            if row.get(code) and row.get(name) not in (None, "-", ""):
                groups[row[code]] = row[name]
    for row in rows(root / "alim_2025_11_03.xml", "ALIM"):
        record_id = row["alim_code"]
        values = nutrients.get(record_id, {}).copy()
        for key, unit, _ in TARGETS.values():
            values.setdefault(key, {"value": None, "kind": "MISSING", "unit": unit})
        english, french = row.get("alim_nom_eng", ""), row["alim_nom_fr"]
        words = set(re.findall(r"[a-z]+", normalize(english)))
        preparation = "PREPARED" if words & {"prepared", "reconstituted"} else "COOKED" if words & {"cooked", "boiled", "fried", "baked", "roasted"} else "RAW" if "raw" in words else "AS_SOLD"
        yield {"id": "ciqual_" + record_id, "source": "CIQUAL", "sourceRecordId": record_id,
               "sourceRelease": lock["release"], "name": french, "brand": None,
               "aliases": sorted(set(x for x in (english, french) if x)), "spanishName": False,
               "categories": sorted(set(groups[row[k]] for k in ("alim_grp_code", "alim_ssgrp_code", "alim_ssssgrp_code") if row.get(k) in groups)),
               "regions": ["FR"], "languages": ["fr", "en"],
               "denominator": {"amount": 100, "unit": "g", "preparation": preparation},
               "carbohydrateConvention": "AVAILABLE_EXCLUDES_FIBER",
               "physicalForm": "POWDER" if words & {"powder", "powdered"} else "UNKNOWN", "reconstitution": None,
               "portions": [], "nutrients": values,
               "calculable": all(values[key]["value"] is not None for key in ("energy", "protein", "carbs", "fat"))}
