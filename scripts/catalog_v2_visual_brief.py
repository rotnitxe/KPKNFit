#!/usr/bin/env python3
"""Assemble the image brief of a CURATED definition straight from its ficha (``visual`` block).

    python scripts/catalog_v2_visual_brief.py <definitionId> [--equipment <equipmentId>] [--json]
    python scripts/catalog_v2_visual_brief.py --list

One image per (definition x implement). For each pair this prints

* ``PROMPT``     the ASCII text to pass to ``GenerateImage`` (house style header + ``promptCore``);
* ``GEOMETRIA``  where the implement sits relative to the body, to check the picture against;
* the base frame (camera, phase, orientation, contacts, posture, load);
* ``RECHAZAR SI`` the ficha's ``forbidden`` list: a picture showing any of it is wrong;
* ``QA``         the ficha's yes/no questions for a clean inspector (a subagent that has not seen the
  history of the generation, only the picture and these questions).

Nothing here invents content: a LEGACY definition has no visual brief and is refused.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
FICHAS = ROOT / "catalog" / "exercises" / "v2" / "curation" / "fichas"

STYLE_HEADER = (
    "Semi-realistic 3D catalog illustration, not a comic, not a gym photo. "
    "No thick black outlines. No cel-shade. Isolated athlete and equipment on a black background. "
    "No logos, no text, blank plates, dark clothes. One scene only."
)
BASE_LABELS = (
    ("camera", "CAMARA"),
    ("phase", "FASE"),
    ("orientation", "ORIENTACION"),
    ("contacts", "CONTACTOS"),
    ("posture", "POSTURA"),
    ("load", "CARGA"),
)


class BriefError(ValueError):
    pass


def load_definitions(fichas_dir: Path) -> dict[str, dict[str, Any]]:
    definitions: dict[str, dict[str, Any]] = {}
    for path in sorted(fichas_dir.glob("*.json")):
        ficha = json.loads(path.read_text(encoding="utf-8"))
        for definition_id, body in (ficha.get("definitions") or {}).items():
            definitions[definition_id] = {"family": ficha.get("familyId"), **body}
    return definitions


def build_prompt(prompt_core: str) -> str:
    prompt = f"{STYLE_HEADER}\n\nEXERCISE: {prompt_core}"
    if not prompt.isascii():
        raise BriefError("the prompt must be ASCII")
    return prompt


def brief_for(definition_id: str, body: dict[str, Any], equipment_id: str) -> dict[str, Any]:
    visual = body.get("visual") or {}
    cores = visual.get("promptCore") or {}
    if equipment_id not in cores:
        raise BriefError(f"{definition_id} has no visual brief for equipment {equipment_id!r} (has: {', '.join(sorted(cores)) or 'none'})")
    base = visual.get("base") or {}
    return {
        "definition": definition_id,
        "equipment": equipment_id,
        "prompt": build_prompt(cores[equipment_id]),
        "geometry": ((visual.get("byImplement") or {}).get(equipment_id) or {}).get("geometry", ""),
        "base": {label: base.get(key, "") for key, label in BASE_LABELS},
        "rejectIf": list(visual.get("forbidden") or []),
        "qa": list(visual.get("qa") or []),
        "variants": {
            config: entry.get("difference", "")
            for config, entry in (visual.get("byVariant") or {}).items()
            if isinstance(entry, dict) and config.startswith(f"{definition_id}__")
        },
    }


def render(brief: dict[str, Any]) -> str:
    lines = [f"== {brief['definition']} x {brief['equipment']}", "PROMPT (GenerateImage, ASCII):", brief["prompt"], "", f"GEOMETRIA: {brief['geometry']}"]
    lines += [f"{label}: {value}" for label, value in brief["base"].items()]
    lines += ["", "RECHAZAR SI:"] + [f"  - {item}" for item in brief["rejectIf"]]
    lines += ["", "QA (si/no, inspector limpio):"] + [f"  {index}. {item}" for index, item in enumerate(brief["qa"], 1)]
    if brief["variants"]:
        lines += ["", "VARIANTES (otro fotograma si se pide por configuracion):"] + [f"  - {config}: {text}" for config, text in brief["variants"].items()]
    return "\n".join(lines)


def main(argv: list[str] | None = None, *, fichas_dir: Path | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("definition", nargs="?", help="definition id")
    parser.add_argument("--equipment", help="only this implement (default: every implement of the definition)")
    parser.add_argument("--list", action="store_true", help="list every CURATED (definition, implement) pair that has a brief")
    parser.add_argument("--json", action="store_true", help="machine-readable output")
    arguments = parser.parse_args(argv)
    definitions = load_definitions(fichas_dir or FICHAS)
    curated = {key: body for key, body in definitions.items() if body.get("status") == "CURATED"}

    if arguments.list:
        for definition_id, body in sorted(curated.items()):
            for equipment_id in sorted((body.get("visual") or {}).get("promptCore") or {}):
                print(f"{definition_id}\t{equipment_id}")
        return 0
    if not arguments.definition:
        parser.error("give a definition id or --list")
    body = definitions.get(arguments.definition)
    if body is None:
        print(f"unknown definition: {arguments.definition}", file=sys.stderr)
        return 2
    if body.get("status") != "CURATED":
        print(f"{arguments.definition} is {body.get('status', 'LEGACY')}: it has no visual brief yet", file=sys.stderr)
        return 2
    equipment_ids = [arguments.equipment] if arguments.equipment else sorted((body.get("visual") or {}).get("promptCore") or {})
    try:
        briefs = [brief_for(arguments.definition, body, equipment_id) for equipment_id in equipment_ids]
    except BriefError as error:
        print(str(error), file=sys.stderr)
        return 2
    print(json.dumps(briefs, ensure_ascii=False, indent=2) if arguments.json else "\n\n".join(render(brief) for brief in briefs))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
