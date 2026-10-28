"""Genera EncyclopediaConceptsData.kt a partir de docs/conceptos-clave/contenido/*.json.

Uso (desde la raíz del repo): python -I docs/conceptos-clave/generar.py
Antes valida todos los grupos presentes con validar.py; si hay errores, no escribe nada.
"""
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = ROOT / "android-native/app/src/main/java/com/example/kpkn/data/wikilab/EncyclopediaConceptsData.kt"
PARAGRAPH = chr(10) * 2
CATALOG = json.loads((HERE / "catalogo.json").read_text(encoding="utf-8"))


def kt(text):
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", "\\n") + '"'


def kt_list(items):
    return "listOf(" + ", ".join(kt(i) for i in items) + ")" if items else "emptyList()"


def main():
    present = [] if "--vacio" in sys.argv else sorted(p.stem for p in (HERE / "contenido").glob("A*.json"))
    if present:
        check = subprocess.run([sys.executable, "-I", "-X", "utf8", str(HERE / "validar.py"), "todos"], capture_output=True, text=True, encoding="utf-8")
        if check.returncode != 0:
            print(check.stdout)
            sys.exit("No se genera: el contenido no valida.")
    entries = {}
    for g in present:
        for e in json.loads((HERE / "contenido" / f"{g}.json").read_text(encoding="utf-8")):
            entries[e["id"]] = e
    ordered = [entries[c["id"]] for c in CATALOG["new"] if c["id"] in entries]
    lines = [
        "package com.example.kpkn.data.wikilab",
        "",
        "// GENERADO por docs/conceptos-clave/generar.py a partir de docs/conceptos-clave/contenido/*.json. No editar a mano:",
        "// corrige el JSON, valida con validar.py y vuelve a generar.",
        "",
        "/** Entradas de la enciclopedia añadidas a los conceptos de entrenamiento originales. */",
        "internal val ENCYCLOPEDIA_CONCEPTS: List<TrainingConcept> get() = listOf(",
    ]
    for e in ordered:
        lines += [
            "    TrainingConcept(",
            f"        id = {kt(e['id'])},",
            f"        name = {kt(e['name'])},",
            f"        category = ConceptCategory.{e['category']},",
            f"        description = {kt(PARAGRAPH.join(e['body']))},",
            f"        shortDescription = {kt(e['short'])},",
            f"        practice = {kt(e['practice'])},",
            f"        aliases = {kt_list(e.get('aliases', []))},",
            f"        related = {kt_list(e['related'])},",
            "    ),",
        ]
    lines += [")", "", "/** Fuentes verificadas en PubMed de cada entrada de [ENCYCLOPEDIA_CONCEPTS]. */",
              "internal val ENCYCLOPEDIA_CONCEPT_SOURCES: Map<String, List<ConceptSourceRef>> get() = mapOf("]
    for e in ordered:
        refs = ", ".join(
            f"ConceptSourceRef(title = {kt(s['title'])}, publisher = {kt(s['publisher'])}, url = {kt(s['url'])})" for s in e["sources"]
        )
        lines.append(f"    {kt(e['id'])} to listOf({refs}),")
    lines += [")", ""]
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"{OUT.name}: {len(ordered)} entradas de {len(present)} grupos ({', '.join(present) or 'ninguno'})")


if __name__ == "__main__":
    main()
