"""Valida el inventario de etapa 1: python check_stage1.py <archivos.jsonl...>"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import schema  # noqa: E402


def check_files(paths):
    """Devuelve dict con errors, warnings, stats por archivo y collisions."""
    errors, warnings, stats = [], [], {}
    ids = {}
    concepts = []
    for path in paths:
        st = {"concepts": 0, "es": 0, "en": 0, "typos": 0, "variants": 0, "dishes_components": 0}
        stats[path] = st
        for n, obj, err in schema.read_jsonl(path):
            loc = "%s:%d" % (os.path.basename(path), n)
            if err:
                errors.append("%s: %s" % (loc, err))
                continue
            for e in schema.validate_concept(obj, 1):
                errors.append("%s [%s]: %s" % (loc, obj.get("id") if isinstance(obj, dict) else "?", e))
            if not isinstance(obj, dict):
                continue
            cid = obj.get("id")
            if cid in ids:
                errors.append("%s: id repetido %r (ya en %s)" % (loc, cid, ids[cid]))
            else:
                ids[cid] = loc
            st["concepts"] += 1
            st["es"] += len(obj.get("aliasesEs") or [])
            st["en"] += len(obj.get("aliasesEn") or [])
            st["typos"] += len(obj.get("typos") or [])
            st["variants"] += len(obj.get("variants") or [])
            if obj.get("components"):
                st["dishes_components"] += 1
            seen = {}
            for a, _ in schema.alias_entries(obj):
                seen[a] = seen.get(a, 0) + 1
            for a, c in seen.items():
                if c > 1:
                    warnings.append("%s [%s]: alias repetido en el mismo concepto: %r" % (loc, cid, a))
            concepts.append(obj)
    collisions = schema.find_collisions(concepts)
    for a, owners in sorted(collisions.items()):
        warnings.append("colision de alias %r entre %s" % (a, ", ".join(map(str, owners))))
    return {"errors": errors, "warnings": warnings, "stats": stats, "collisions": collisions}


def main(argv=None):
    paths = list(sys.argv[1:] if argv is None else argv)
    if not paths:
        print("uso: check_stage1.py <archivos.jsonl...>", file=sys.stderr)
        return 2
    r = check_files(paths)
    for w in r["warnings"]:
        print("AVISO", w)
    for e in r["errors"]:
        print("ERROR", e)
    for p, st in r["stats"].items():
        print("%s: %d conceptos, alias es=%d en=%d faltas=%d, variantes=%d, platos con componentes=%d" % (
            os.path.basename(p), st["concepts"], st["es"], st["en"], st["typos"],
            st["variants"], st["dishes_components"]))
    print("%d errores, %d avisos" % (len(r["errors"]), len(r["warnings"])))
    return 1 if r["errors"] else 0


if __name__ == "__main__":
    for s in (sys.stdout, sys.stderr):
        if hasattr(s, "reconfigure"):
            s.reconfigure(encoding="utf-8")
    sys.exit(main())
