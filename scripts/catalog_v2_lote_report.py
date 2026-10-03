#!/usr/bin/env python3
"""What a lote changes before it lands: anatomy delta, protected configurations and word budgets.

    python scripts/catalog_v2_lote_report.py --definitions a,b [--fichas-dir <dir>] [--markdown <file>]

The configurations of the requested definitions, as the fichas would render them, are compared with
the compiled catalogue (``source/catalog_v2.json``, what the app ships today):

* the first PRIMARY muscle: the dominant muscle that ``SessionCompositionPolicy`` and
  ``CompositionTaxonomy`` read with ``primaryMuscles.first()``; the PRIMARY set (each PRIMARY counts a
  full weekly set); every other role change; the joints that appear, disappear or change role;
* whether the configuration id is referenced from Android production code (plans, templates,
  protocols) or from an Android test. A dominant or PRIMARY change on such a configuration is
  escalated to the product owner before landing;
* the per-catalogue word budgets of ``quality_lexicon.json``: how many CURATED definitions use each
  budgeted word, the lote included, against its maximum.

Read-only: nothing is written except the optional markdown file.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

try:
    import catalog_v2_apply_fichas as apply_fichas
    import catalog_v2_quality_audit as audit
except ModuleNotFoundError:  # loaded by file path (tests) without scripts/ on sys.path
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import catalog_v2_apply_fichas as apply_fichas
    import catalog_v2_quality_audit as audit

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "catalog" / "exercises" / "v2" / "source" / "catalog_v2.json"
LEXICON = ROOT / "catalog" / "exercises" / "v2" / "curation" / "quality_lexicon.json"
ANDROID_MAIN = ROOT / "android-native" / "app" / "src" / "main" / "java"
ANDROID_TEST = ROOT / "android-native" / "app" / "src" / "test" / "java"
ROLE_FIELDS = (("primaryMuscles", "PRIMARY"), ("secondaryMuscles", "SECONDARY"), ("stabilizerMuscles", "STABILIZER"))
_CONFIG_LITERAL = re.compile(r'"([a-z0-9_]+__[a-z0-9_]+)"')
TICK = chr(96)


def code(text: str) -> str:
    return f"{TICK}{text}{TICK}"


def configuration_literals(root: Path) -> set[str]:
    found: set[str] = set()
    if root.is_dir():
        for path in root.rglob("*.kt"):
            found.update(_CONFIG_LITERAL.findall(path.read_text(encoding="utf-8", errors="replace")))
    return found


def muscle_roles(profile: dict[str, Any]) -> dict[str, str]:
    return {muscle: role for field, role in ROLE_FIELDS for muscle in profile.get(field) or []}


def joint_roles(profile: dict[str, Any]) -> dict[str, str]:
    return {joint["jointId"]: joint["role"] for joint in profile.get("jointInvolvement") or []}


def configuration_delta(old: dict[str, Any], new: dict[str, Any]) -> dict[str, Any]:
    old_primary, new_primary = list(old.get("primaryMuscles") or []), list(new.get("primaryMuscles") or [])
    old_roles, new_roles = muscle_roles(old), muscle_roles(new)
    old_joints, new_joints = joint_roles(old), joint_roles(new)
    return {
        "dominant": (old_primary[:1] or [None])[0] != (new_primary[:1] or [None])[0],
        "oldPrimary": old_primary,
        "newPrimary": new_primary,
        "primarySet": set(old_primary) != set(new_primary),
        "muscles": {
            muscle: (old_roles.get(muscle, "-"), new_roles.get(muscle, "-"))
            for muscle in sorted(set(old_roles) | set(new_roles))
            if old_roles.get(muscle) != new_roles.get(muscle)
        },
        "joints": {
            joint: (old_joints.get(joint, "-"), new_joints.get(joint, "-"))
            for joint in sorted(set(old_joints) | set(new_joints))
            if old_joints.get(joint) != new_joints.get(joint)
        },
        "pattern": (old.get("movementPatternId"), new.get("movementPatternId")),
    }


def budget_ledger(fichas_dir: Path, lexicon: dict[str, Any], lote: set[str]) -> list[dict[str, Any]]:
    texts: dict[str, list[str]] = {}
    for path in sorted(fichas_dir.glob("*.json")):
        ficha = json.loads(path.read_text(encoding="utf-8"))
        for definition_id, body in (ficha.get("definitions") or {}).items():
            if not isinstance(body, dict) or body.get("status") != apply_fichas.STATUS_CURATED:
                continue
            public = body.get("public") or {}
            items = [public.get("description") or ""]
            items += [entry.get("description") or "" for entry in (public.get("configurations") or {}).values()]
            texts[definition_id] = [audit.fold(text) for text in items]
    ledger = []
    for entry in lexicon.get("budgets", []):
        pattern = re.compile(entry["pattern"])
        users = sorted(definition_id for definition_id, folded in texts.items() if any(pattern.search(text) for text in folded))
        ledger.append({
            "id": entry["id"],
            "max": entry["maxDefinitions"],
            "used": len(users),
            "lote": [definition_id for definition_id in users if definition_id in lote],
        })
    return ledger


def build_report(definition_ids: list[str], fichas_dir: Path, source_path: Path = SOURCE, lexicon_path: Path = LEXICON) -> dict[str, Any]:
    plan, _ = apply_fichas.build_plan(apply_fichas.FAMILIES, fichas_dir, definition_ids)
    rendered = {
        configuration["id"]: (definition["id"], configuration["profile"])
        for item in plan
        for definition in item.rendered["family"]["definitions"]
        if definition["id"] in definition_ids
        for configuration in definition["configurations"]
    }
    source = json.loads(source_path.read_text(encoding="utf-8"))
    current = {
        configuration["id"]: configuration["profile"]
        for family in source["families"]
        for definition in family["definitions"]
        for configuration in definition["configurations"]
    }
    production = configuration_literals(ANDROID_MAIN)
    tests = configuration_literals(ANDROID_TEST)
    rows = []
    for configuration_id, (definition_id, profile) in rendered.items():
        delta = configuration_delta(current[configuration_id], profile)
        delta.update({
            "configuration": configuration_id,
            "definition": definition_id,
            "production": configuration_id in production,
            "tests": configuration_id in tests,
        })
        rows.append(delta)
    lexicon = json.loads(lexicon_path.read_text(encoding="utf-8"))
    return {"rows": rows, "budgets": budget_ledger(fichas_dir, lexicon, set(definition_ids))}


def yes(flag: bool) -> str:
    return "sí" if flag else ""


def render_markdown(report: dict[str, Any]) -> str:
    rows = report["rows"]
    lines = ["# Informe de lote: delta anatómico", ""]
    escalate = [row for row in rows if (row["dominant"] or row["primarySet"]) and (row["production"] or row["tests"])]
    lines += ["## Escalar antes de aplicar (principal cambiado en configuraciones protegidas)", ""]
    if escalate:
        lines += ["| configuración | antes | después | código | tests |", "|---|---|---|---|---|"]
        for row in escalate:
            before, after = ", ".join(row["oldPrimary"]), ", ".join(row["newPrimary"])
            lines.append(f"| {code(row['configuration'])} | {before} | {after} | {yes(row['production'])} | {yes(row['tests'])} |")
    else:
        lines.append("Ninguna.")
    lines += ["", "## Cambios de rol muscular", ""]
    changed = [row for row in rows if row["muscles"]]
    if changed:
        lines += ["| configuración | músculo | antes → después | dominante cambia | protegida |", "|---|---|---|---|---|"]
        for row in changed:
            protected = "código" if row["production"] else ("tests" if row["tests"] else "")
            for muscle, (before, after) in row["muscles"].items():
                lines.append(f"| {code(row['configuration'])} | {muscle} | {before} → {after} | {yes(row['dominant'])} | {protected} |")
    else:
        lines.append("Sin cambios.")
    lines += ["", "## Cambios articulares", ""]
    joints = [row for row in rows if row["joints"]]
    if joints:
        lines += ["| configuración | articulación | antes → después |", "|---|---|---|"]
        for row in joints:
            for joint, (before, after) in row["joints"].items():
                lines.append(f"| {code(row['configuration'])} | {joint} | {before} → {after} |")
    else:
        lines.append("Sin cambios.")
    patterns = [row for row in rows if row["pattern"][0] != row["pattern"][1]]
    if patterns:
        lines += ["", "## Cambios de patrón", ""]
        lines += [f"- {code(row['configuration'])}: {row['pattern'][0]} → {row['pattern'][1]}" for row in patterns]
    lines += ["", "## Topes de palabras (léxico): definiciones CURATED que usan cada palabra", ""]
    lines += ["| tope | usadas | máximo | en este lote |", "|---|---|---|---|"]
    for entry in report["budgets"]:
        if entry["used"] or entry["lote"]:
            flag = " (EXCEDIDO)" if entry["used"] > entry["max"] else ""
            lines.append(f"| {entry['id']} | {entry['used']}{flag} | {entry['max']} | {', '.join(entry['lote'])} |")
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None, *, fichas_dir: Path | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(prog="catalog_v2_lote_report.py", description=__doc__.split("\n\n")[0])
    parser.add_argument("--definitions", required=True, help="comma-separated definition ids of the lote")
    parser.add_argument("--fichas-dir", type=Path, help="read the fichas of this directory (a private copy) instead of the shared one")
    parser.add_argument("--markdown", type=Path, help="also write the report to this file")
    parser.add_argument("--json", action="store_true", help="machine-readable output")
    arguments = parser.parse_args(argv)
    requested = [item.strip() for item in arguments.definitions.split(",") if item.strip()]
    directory = Path(fichas_dir) if fichas_dir is not None else (arguments.fichas_dir or apply_fichas.FICHAS)
    try:
        report = build_report(requested, directory)
    except apply_fichas.FichaError as error:
        print(str(error), file=sys.stderr)
        return 1
    if arguments.json:
        print(json.dumps(report, ensure_ascii=False, indent=2, default=list))
        return 0
    text = render_markdown(report)
    if arguments.markdown:
        arguments.markdown.write_text(text, encoding="utf-8", newline="\n")
    print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())