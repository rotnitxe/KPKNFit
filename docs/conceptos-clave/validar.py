"""Valida el contenido de la enciclopedia de Conceptos clave.

Uso:
  python -I docs/conceptos-clave/validar.py A3            # valida contenido/A3.json
  python -I docs/conceptos-clave/validar.py todos         # valida todos los grupos presentes
  python -I docs/conceptos-clave/validar.py todos --red   # además abre cada URL de fuente y compara el título

Sale con código 0 y la línea «VALIDACIÓN OK» si todo cumple; si no, lista cada error.
"""
import json
import re
import sys
import unicodedata
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
CATALOG = json.loads((HERE / "catalogo.json").read_text(encoding="utf-8"))
NEW = {c["id"]: c for c in CATALOG["new"]}
ALL_IDS = set(CATALOG["existing"]) | set(NEW)
LINK = re.compile(r"\[\[([a-z0-9-]+)(?:\|([^\]]+))?\]\]")
URL_OK = re.compile(r"^https://pubmed\.ncbi\.nlm\.nih\.gov/(\d+)/$")


def plain(text):
    return LINK.sub(lambda m: m.group(2) or m.group(1), text)


def words(text):
    return len([w for w in plain(text).split() if w.strip()])


def norm(s):
    s = unicodedata.normalize("NFKD", s.lower())
    return re.sub(r"[^a-z0-9 ]+", " ", "".join(c for c in s if not unicodedata.combining(c))).split()


def check_group(group, net):
    errors = []
    path = HERE / "contenido" / f"{group}.json"
    if not path.exists():
        return [f"{group}: falta {path}"]
    try:
        entries = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        return [f"{group}: JSON inválido: {e}"]
    expected = {i for i, c in NEW.items() if c["group"] == group}
    got = [e.get("id") for e in entries]
    if set(got) != expected or len(got) != len(set(got)):
        errors.append(f"{group}: ids distintos al catálogo. Faltan {sorted(expected - set(got))}; sobran {sorted(set(got) - expected)}")
    for e in entries:
        cid = e.get("id", "?")
        cat = NEW.get(cid)
        p = f"{group}/{cid}"
        if not cat:
            continue
        if e.get("category") != cat["category"]:
            errors.append(f"{p}: category debe ser {cat['category']}")
        if e.get("name") != cat["name"] and cat["category"] != "STUDIES":
            errors.append(f"{p}: name debe ser exactamente «{cat['name']}»")
        short = e.get("short", "")
        if not (20 <= len(short) <= 130) or "\n" in short or short.endswith(".") or "[[" in short:
            errors.append(f"{p}: short debe tener 20-130 caracteres, una línea, sin punto final ni enlaces (tiene {len(short)})")
        aliases = e.get("aliases", [])
        if not isinstance(aliases, list) or len(aliases) > 6 or any(not isinstance(a, str) or not a.strip() for a in aliases):
            errors.append(f"{p}: aliases debe ser una lista de 0-6 textos")
        body = e.get("body", [])
        practice = e.get("practice", "")
        if not isinstance(body, list) or not (2 <= len(body) <= 3) or any(not isinstance(b, str) or not b.strip() for b in body):
            errors.append(f"{p}: body debe ser una lista de 2 o 3 párrafos")
            continue
        if not isinstance(practice, str) or words(practice) < 30:
            errors.append(f"{p}: practice debe ser un párrafo de al menos 30 palabras")
        total = sum(words(b) for b in body) + words(practice)
        if not (180 <= total <= 240):
            errors.append(f"{p}: body + practice deben sumar 180-240 palabras (suma {total})")
        text = "\n".join(body + [practice])
        if "$" in text or '"""' in text or "\n" in "".join(body + [practice]):
            errors.append(f"{p}: no uses «$», comillas triples ni saltos de línea dentro de un párrafo")
        links = LINK.findall(text)
        if not links:
            errors.append(f"{p}: el texto necesita al menos un enlace [[id|texto]]")
        for target, _ in links:
            if target not in ALL_IDS:
                errors.append(f"{p}: enlace a id inexistente «{target}»")
            if target == cid:
                errors.append(f"{p}: no te enlaces a ti mismo")
        if re.search(r"\[\[(?![a-z0-9-]+(\|[^\]]+)?\]\])", text):
            errors.append(f"{p}: enlace mal formado; usa [[id|texto visible]]")
        related = e.get("related", [])
        if not isinstance(related, list) or not (2 <= len(related) <= 4) or cid in related or len(set(related)) != len(related):
            errors.append(f"{p}: related debe tener 2-4 ids distintos y no incluirse a sí mismo")
        for r in related if isinstance(related, list) else []:
            if r not in ALL_IDS:
                errors.append(f"{p}: related con id inexistente «{r}»")
        sources = e.get("sources", [])
        if not isinstance(sources, list) or not (1 <= len(sources) <= 3):
            errors.append(f"{p}: sources debe tener 1-3 fuentes")
            sources = []
        for s in sources:
            if not all(isinstance(s.get(k), str) and s.get(k).strip() for k in ("title", "publisher", "url")):
                errors.append(f"{p}: cada fuente necesita title, publisher y url")
                continue
            if not URL_OK.match(s["url"]):
                errors.append(f"{p}: url no admitida «{s['url']}» (solo https://pubmed.ncbi.nlm.nih.gov/<PMID>/)")
            if net:
                err = check_url(s)
                if err:
                    errors.append(f"{p}: {err}")
    return errors


def check_url(s):
    """Comprueba el PMID con la API E-utilities de NCBI (la web de PubMed pide reCAPTCHA)."""
    import time
    m = URL_OK.match(s["url"])
    if not m:
        return None
    pmid = m.group(1)
    api = f"https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&id={pmid}&retmode=json"
    page, last = None, None
    # NCBI limita a ~3 peticiones por segundo por IP: con varios redactores a la vez hay 429/500 pasajeros.
    for attempt in range(6):
        time.sleep(0.4 + attempt * attempt * 1.5)
        try:
            data = json.loads(urllib.request.urlopen(api, timeout=25).read().decode("utf-8"))
            page = data["result"][pmid].get("title", "")
            break
        except Exception as e:  # noqa: BLE001
            last = e
    if page is None:
        return f"no se pudo comprobar el PMID {pmid}: {last}"
    want, have = set(norm(s["title"])), set(norm(page))
    if not want or len(want & have) / len(want) < 0.7:
        return f"el título no coincide con el PMID {pmid}: PubMed dice «{page.strip()[:140]}»"
    return None


def main():
    arg = sys.argv[1] if len(sys.argv) > 1 else "todos"
    net = "--red" in sys.argv
    groups = sorted({c["group"] for c in CATALOG["new"]}) if arg == "todos" else [arg]
    if arg == "todos":
        groups = [g for g in groups if (HERE / "contenido" / f"{g}.json").exists()]
    errors = [err for g in groups for err in check_group(g, net)]
    for err in errors:
        print("ERROR", err)
    if errors:
        print(f"VALIDACIÓN FALLIDA: {len(errors)} errores en {', '.join(groups)}")
        sys.exit(1)
    print(f"VALIDACIÓN OK: {', '.join(groups)}")


if __name__ == "__main__":
    main()
