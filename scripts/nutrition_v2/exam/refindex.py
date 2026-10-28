"""Indice de fichas nutricionales publicas (USDA FNDDS / SR Legacy / Foundation y CIQUAL 2025).

Sirve a los etiquetadores del examen de nutricion v2 (docs/nutrition-v2/EXAMEN.md, seccion 9):
busqueda BM25 por nombre, modo lote y consulta por id. Solo biblioteca estandar.

    python refindex.py search "rice white cooked" -k 8 [--sources USDA_FNDDS,CIQUAL]
    python refindex.py batch --in q.jsonl --out c.jsonl -k 6
    python refindex.py get usda_171283
"""
import argparse
import hashlib
import json
import math
import os
import pickle
import re
import sys
import time
import unicodedata
from dataclasses import asdict, dataclass, field
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCES_DIR = HERE.parent / "sources"
DEFAULT_LOCKS_DIR = SOURCES_DIR / "locks"
DEFAULT_INPUTS_DIR = Path(r"C:\Users\valen\Documents\KPKNFit\artifacts\nutrition-local-ai\inputs")
DEFAULT_CACHE_DIR = Path(r"C:\kw\nutri-examen\cache")
CACHE_VERSION = 1

STOPWORDS = frozenset(
    "of and with the or ns nfs from de la le les du des et au aux en a l d".split()
)
# Menor numero = mayor prioridad en los empates.
SOURCE_PRIORITY = {"USDA_FNDDS": 0, "USDA_SR_LEGACY": 1, "USDA_FOUNDATION": 2, "CIQUAL": 3}
K1 = 1.2
B = 0.75
PER100G_KEYS = ("kcal", "protein", "fat", "carbs", "fiber", "sugar", "sodium_mg")
NUTRIENT_FIELDS = {
    "kcal": "energy", "protein": "protein", "fat": "fat", "carbs": "carbs",
    "fiber": "fiber", "sugar": "sugar", "sodium_mg": "sodium",
}


@dataclass
class Ref:
    id: str
    source: str
    code: str
    name: str
    names: list
    category: str
    preparation: str
    form: str
    per100g: dict
    portions: list = field(default_factory=list)  # [(label, grams)]


# --------------------------------------------------------------------------- texto

def tokens(text):
    """Minusculas, sin tildes, [a-z0-9]+, plural simple y sin stopwords."""
    if not text:
        return []
    decomposed = unicodedata.normalize("NFKD", str(text).lower())
    plain = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    result = []
    for tok in re.findall(r"[a-z0-9]+", plain):
        if len(tok) > 3 and tok.endswith("s"):
            tok = tok[:-1]
        if tok in STOPWORDS:
            continue
        result.append(tok)
    return result


# --------------------------------------------------------------------------- carga

def _num(value):
    if value is None:
        return None
    return float(value)


def _portion_label(portion):
    label = (portion.get("label") or "").strip()
    unit = (portion.get("unit") or "").strip()
    amount = portion.get("amount")
    if not label:
        label = unit if unit and unit.lower() != "undetermined" else "porcion"
    if amount and not re.match(r"^\s*[\d./]+\s", label) and float(amount) != 1.0:
        label = "%g %s" % (float(amount), label)
    return label


def _to_ref(rec):
    nutrients = rec.get("nutrients") or {}
    per100g = {}
    for out_key, src_key in NUTRIENT_FIELDS.items():
        datum = nutrients.get(src_key) or {}
        per100g[out_key] = _num(datum.get("value"))
    names = []
    for text in [rec.get("name")] + list(rec.get("aliases") or []):
        if text and text not in names:
            names.append(text)
    categories = rec.get("categories") or []
    denominator = rec.get("denominator") or {}
    portions = []
    for p in rec.get("portions") or []:
        grams = p.get("grams")
        if grams is not None and grams > 0:
            portions.append((_portion_label(p), float(grams)))
    return Ref(
        id=rec["id"], source=rec["source"], code=str(rec["sourceRecordId"]),
        name=rec.get("name") or "", names=names,
        category=categories[0] if categories else "",
        preparation=denominator.get("preparation") or "",
        form=rec.get("physicalForm") or "",
        per100g=per100g, portions=portions,
    )


def load_refs(inputs_dir, locks_dir):
    """Lee las fuentes reales (verificando SHA-256) y devuelve solo registros calculables."""
    if str(SOURCES_DIR) not in sys.path:
        sys.path.insert(0, str(SOURCES_DIR))
    import nutrition_catalog_usda as usda
    import nutrition_catalog_ciqual as ciqual

    inputs_dir = Path(inputs_dir)
    locks_dir = Path(locks_dir)
    refs = []
    usda_lock = usda.locked_sources(inputs_dir, locks_dir / "usda-sources.lock.json")
    for rec in usda.records(inputs_dir, usda_lock):
        if rec and rec.get("calculable"):
            refs.append(_to_ref(rec))
    ciqual_dir = inputs_dir / "ciqual"
    ciqual_lock = ciqual.locked_source(ciqual_dir, locks_dir / "ciqual-sources.lock.json")
    for rec in ciqual.records(ciqual_dir, ciqual_lock):
        if rec and rec.get("calculable"):
            refs.append(_to_ref(rec))
    return refs


# --------------------------------------------------------------------------- indice

class RefIndex:
    def __init__(self, refs):
        self.refs = list(refs)
        self.by_id = {r.id: i for i, r in enumerate(self.refs)}
        self._doc_tokens = [tokens(" ".join(r.names)) for r in self.refs]
        self._build()

    def _build(self):
        self.doc_len = [len(t) for t in self._doc_tokens]
        n = len(self.refs)
        self.avgdl = (sum(self.doc_len) / n) if n else 0.0
        self.postings = {}
        for doc, toks in enumerate(self._doc_tokens):
            counts = {}
            for tok in toks:
                counts[tok] = counts.get(tok, 0) + 1
            for tok, tf in counts.items():
                self.postings.setdefault(tok, []).append((doc, tf))

    def get(self, ref_id):
        i = self.by_id.get(ref_id)
        return self.refs[i] if i is not None else None

    def search(self, query, k=8, sources=None):
        q_tokens = list(dict.fromkeys(tokens(query)))
        if not q_tokens or not self.refs:
            return []
        allowed = set(sources) if sources else None
        n = len(self.refs)
        scores = {}
        for tok in q_tokens:
            plist = self.postings.get(tok)
            if not plist:
                continue
            df = len(plist)
            idf = math.log(1.0 + (n - df + 0.5) / (df + 0.5))
            for doc, tf in plist:
                if allowed is not None and self.refs[doc].source not in allowed:
                    continue
                norm = K1 * (1.0 - B + B * self.doc_len[doc] / self.avgdl) if self.avgdl else K1
                scores[doc] = scores.get(doc, 0.0) + idf * tf * (K1 + 1.0) / (tf + norm)
        ranked = sorted(
            scores.items(),
            key=lambda kv: (-round(kv[1], 9), SOURCE_PRIORITY.get(self.refs[kv[0]].source, 99), self.refs[kv[0]].id),
        )
        return [(score, self.refs[doc]) for doc, score in ranked[:k]]

    # Serializacion sin clases propias (el pickle no depende de que modulo lo cargo).
    def to_payload(self):
        return {"version": CACHE_VERSION, "refs": [asdict(r) for r in self.refs]}

    @classmethod
    def from_payload(cls, payload):
        refs = []
        for d in payload["refs"]:
            d = dict(d)
            d["portions"] = [(label, grams) for label, grams in d["portions"]]
            refs.append(Ref(**d))
        return cls(refs)


def eaten(ref, grams):
    """kcal/protein/fat/carbs consumidos para `grams` de la ficha; None se conserva."""
    out = {}
    for key in ("kcal", "protein", "fat", "carbs"):
        value = ref.per100g.get(key)
        out[key] = None if value is None else round(value * grams / 100.0, 1)
    return out


# --------------------------------------------------------------------------- cache

def lock_digest(locks_dir):
    h = hashlib.sha256()
    for name in ("usda-sources.lock.json", "ciqual-sources.lock.json"):
        h.update((Path(locks_dir) / name).read_bytes())
    return h.hexdigest()[:12]


def cache_path(locks_dir, cache_dir=DEFAULT_CACHE_DIR):
    return Path(cache_dir) / ("refindex-%s.pkl" % lock_digest(locks_dir))


def load_index(inputs_dir=DEFAULT_INPUTS_DIR, locks_dir=DEFAULT_LOCKS_DIR,
               cache_dir=DEFAULT_CACHE_DIR, rebuild=False):
    path = cache_path(locks_dir, cache_dir)
    if not rebuild and path.is_file():
        try:
            with path.open("rb") as stream:
                payload = pickle.load(stream)
            if payload.get("version") == CACHE_VERSION:
                return RefIndex.from_payload(payload)
        except Exception:
            pass  # cache danada: se reconstruye
    index = RefIndex(load_refs(inputs_dir, locks_dir))
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    with tmp.open("wb") as stream:
        pickle.dump(index.to_payload(), stream, protocol=pickle.HIGHEST_PROTOCOL)
    os.replace(tmp, path)
    return index


# --------------------------------------------------------------------------- CLI

def _fmt(value):
    return "-" if value is None else ("%g" % value)


def format_line(ref):
    p = ref.per100g
    macros = "/".join(_fmt(p[k]) for k in ("kcal", "protein", "fat", "carbs"))
    portions = " ".join("%s=%g" % (label.replace(" ", "_"), grams) for label, grams in ref.portions[:4])
    return " | ".join([
        ref.id, ref.source, ref.name, macros + " kcal/P/G/C", ref.preparation, ref.form, portions,
    ])


def candidate_dict(ref):
    return {
        "id": ref.id, "source": ref.source, "code": ref.code, "name": ref.name,
        "per100g": dict(ref.per100g), "preparation": ref.preparation, "form": ref.form,
        "portions": [{"label": label, "grams": grams} for label, grams in ref.portions[:6]],
    }


def _parse_sources(value):
    if not value:
        return None
    if isinstance(value, str):
        value = value.split(",")
    sources = [s.strip() for s in value if s and s.strip()]
    return sources or None


def run_batch(index, in_path, out_path, k):
    count = 0
    with open(in_path, encoding="utf-8") as src, open(out_path, "w", encoding="utf-8", newline="\n") as dst:
        for line in src:
            line = line.strip()
            if not line:
                continue
            item = json.loads(line)
            hits = index.search(item["query"], k=k, sources=_parse_sources(item.get("sources")))
            row = {"qid": item["qid"], "query": item["query"],
                   "candidates": [candidate_dict(ref) for _, ref in hits]}
            dst.write(json.dumps(row, ensure_ascii=False) + "\n")
            count += 1
    return count


def build_parser():
    parser = argparse.ArgumentParser(description="Indice de fichas nutricionales publicas")
    parser.add_argument("--rebuild", action="store_true", help="fuerza reconstruir la cache")
    parser.add_argument("--inputs-dir", default=str(DEFAULT_INPUTS_DIR))
    parser.add_argument("--locks-dir", default=str(DEFAULT_LOCKS_DIR))
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE_DIR))
    sub = parser.add_subparsers(dest="command", required=True)
    s = sub.add_parser("search")
    s.add_argument("query")
    s.add_argument("-k", type=int, default=8)
    s.add_argument("--sources", default=None)
    b = sub.add_parser("batch")
    b.add_argument("--in", dest="in_path", required=True)
    b.add_argument("--out", dest="out_path", required=True)
    b.add_argument("-k", type=int, default=6)
    g = sub.add_parser("get")
    g.add_argument("ref_id")
    return parser


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    args = build_parser().parse_args(argv)
    t0 = time.time()
    index = load_index(args.inputs_dir, args.locks_dir, args.cache_dir, rebuild=args.rebuild)
    print("# indice: %d fichas en %.1f s" % (len(index.refs), time.time() - t0), file=sys.stderr)
    if args.command == "search":
        for _, ref in index.search(args.query, k=args.k, sources=_parse_sources(args.sources)):
            print(format_line(ref))
    elif args.command == "batch":
        n = run_batch(index, args.in_path, args.out_path, args.k)
        print("# %d consultas -> %s" % (n, args.out_path), file=sys.stderr)
    else:
        ref = index.get(args.ref_id)
        if ref is None:
            print("no existe: " + args.ref_id, file=sys.stderr)
            return 1
        print(json.dumps(asdict(ref), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
