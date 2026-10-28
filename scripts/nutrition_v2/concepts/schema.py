"""Esquema y validacion de conceptos F1b (docs/nutrition-v2/F1B_CONCEPTOS.md). Solo biblioteca estandar."""
import json
import re
import unicodedata

KINDS = ("FOOD", "DISH", "DRINK", "SUPPLEMENT", "CONDIMENT")
CATEGORIES = (
    "ACEITES_GRASAS", "ALCOHOL", "AZUCARES_DULCES", "BEBIDAS", "CARNES", "CEREALES_PANES",
    "FRUTAS", "FRUTOS_SECOS", "HUEVOS", "LACTEOS", "LEGUMBRES", "OTROS", "PASTAS_ARROZ",
    "PESCADOS_MARISCOS", "PLATOS", "SALSAS_CONDIMENTOS", "SNACKS", "SUPLEMENTOS",
    "TUBERCULOS", "VERDURAS",
)
STATES = ("NA", "RAW", "COOKED", "FRIED", "GRILLED", "BAKED", "STEAMED")
FORMS = ("NA", "POWDER_DRY", "RECONSTITUTED", "CONCENTRATE", "LIQUID", "DRIED",
         "CANNED_DRAINED", "CANNED_UNDRAINED", "LIGHT", "SUGAR_FREE")
PORTION_LABELS = (
    "taza", "cucharada", "cucharadita", "vaso", "plato", "rebanada", "lamina", "punado",
    "scoop", "lata", "porcion", "unidad", "pocillo", "botella", "paquete", "trozo", "pieza",
)
ALIAS_FIELDS = (("aliasesEs", "es"), ("aliasesEn", "en"), ("typos", "typos"))
ID_RE = re.compile(r"^[a-z0-9]+(_[a-z0-9]+)*$")


def normalize(text):
    """Minusculas, sin tildes, todo lo que no sea [a-z0-9] -> espacio, espacios colapsados."""
    decomposed = unicodedata.normalize("NFD", str(text).lower())
    plain = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    return re.sub(r"[^a-z0-9]+", " ", plain).strip()


def _is_num(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def _str_list(obj, key, errors, min_len=0):
    value = obj.get(key)
    if not isinstance(value, list):
        errors.append("%s debe ser una lista" % key)
        return []
    ok = True
    for item in value:
        if not isinstance(item, str) or not item.strip():
            errors.append("%s contiene un valor vacio o no texto" % key)
            ok = False
            break
    if len(value) < min_len:
        errors.append("%s necesita al menos %d" % (key, min_len))
    return value if ok else []


def validate_concept(obj, stage):
    """Devuelve la lista de errores legibles (vacia si es valido). stage 1: sin ref; stage 3: con ref."""
    errors = []
    if not isinstance(obj, dict):
        return ["el concepto no es un objeto"]
    cid = obj.get("id")
    if not isinstance(cid, str) or not ID_RE.match(cid):
        errors.append("id debe ser snake_case ASCII")
    name = obj.get("name")
    if not isinstance(name, str) or not name.strip():
        errors.append("name vacio")
    kind = obj.get("kind")
    if kind not in KINDS:
        errors.append("kind invalido: %r" % (kind,))
    if obj.get("category") not in CATEGORIES:
        errors.append("category invalida: %r" % (obj.get("category"),))
    _str_list(obj, "aliasesEs", errors, min_len=1)
    for key in ("aliasesEn", "typos"):
        if obj.get(key) is not None:
            _str_list(obj, key, errors)
    ds, df = obj.get("defaultState"), obj.get("defaultForm")
    if ds not in STATES:
        errors.append("defaultState invalido: %r" % (ds,))
    if df not in FORMS:
        errors.append("defaultForm invalido: %r" % (df,))

    variants = obj.get("variants") or []
    components = obj.get("components") or []
    if not isinstance(variants, list):
        errors.append("variants debe ser lista")
        variants = []
    if not isinstance(components, list):
        errors.append("components debe ser lista")
        components = []
    is_dish_components = kind == "DISH" and not variants

    if is_dish_components:
        if not components:
            errors.append("DISH sin variantes necesita components no vacio")
    elif not variants:
        errors.append("variants vacio")

    seen_v = []
    has_powder = False
    for i, v in enumerate(variants):
        tag = "variants[%d]" % i
        if not isinstance(v, dict):
            errors.append("%s no es objeto" % tag)
            continue
        if v.get("state") not in STATES:
            errors.append("%s.state invalido: %r" % (tag, v.get("state")))
        if v.get("form") not in FORMS:
            errors.append("%s.form invalido: %r" % (tag, v.get("form")))
        if not isinstance(v.get("query"), str) or not v["query"].strip():
            errors.append("%s.query vacia" % tag)
        if v.get("form") in ("POWDER_DRY", "CONCENTRATE"):
            has_powder = True
        seen_v.append((v.get("state"), v.get("form")))
        if stage >= 3 and v.get("noMatch") is not True:
            ref = v.get("ref")
            if not isinstance(ref, str) or not ref.strip():
                errors.append("%s sin ref ni noMatch" % tag)
    if variants and not is_dish_components and (ds, df) not in seen_v:
        errors.append("ninguna variante cubre defaultState/defaultForm (%s/%s)" % (ds, df))

    for i, c in enumerate(components):
        tag = "components[%d]" % i
        if not isinstance(c, dict):
            errors.append("%s no es objeto" % tag)
            continue
        if not isinstance(c.get("query"), str) or not c["query"].strip():
            errors.append("%s.query vacia" % tag)
        if not _is_num(c.get("grams")) or c["grams"] <= 0:
            errors.append("%s.grams debe ser > 0" % tag)
        if stage >= 3 and (not isinstance(c.get("ref"), str) or not c["ref"].strip()):
            errors.append("%s sin ref" % tag)

    if obj.get("countable"):
        unit = obj.get("unit")
        g = unit.get("grams") if isinstance(unit, dict) else None
        if not isinstance(g, dict) or not all(_is_num(g.get(k)) and g[k] > 0 for k in "SML"):
            errors.append("countable requiere unit.grams con S/M/L > 0")
        elif not (g["S"] <= g["M"] <= g["L"]):
            errors.append("unit.grams debe cumplir S <= M <= L")

    portions = obj.get("portions") or []
    if not isinstance(portions, list):
        errors.append("portions debe ser lista")
        portions = []
    for i, p in enumerate(portions):
        tag = "portions[%d]" % i
        if not isinstance(p, dict):
            errors.append("%s no es objeto" % tag)
            continue
        if not isinstance(p.get("label"), str) or not p["label"].strip():
            errors.append("%s.label vacia" % tag)
        if not _is_num(p.get("grams")) or p["grams"] <= 0:
            errors.append("%s.grams debe ser > 0" % tag)
        if p.get("state") not in STATES:
            errors.append("%s.state invalido: %r" % (tag, p.get("state")))

    dg = obj.get("defaultGrams")
    if not _is_num(dg) or dg <= 0:
        errors.append("defaultGrams debe ser > 0")

    rec = obj.get("reconstitution")
    if rec is not None:
        if not has_powder:
            errors.append("reconstitution solo con variante POWDER_DRY o CONCENTRATE")
        if not isinstance(rec, dict) or not _is_num(rec.get("gramsPer100ml")) or rec["gramsPer100ml"] <= 0:
            errors.append("reconstitution.gramsPer100ml debe ser > 0")
    return errors


def alias_entries(obj):
    """[(alias_norm, tipo)] de un concepto (tipo es/en/typos), sin vacios."""
    out = []
    for key, kind in ALIAS_FIELDS:
        for a in obj.get(key) or []:
            if isinstance(a, str):
                n = normalize(a)
                if n:
                    out.append((n, kind))
    return out


def find_collisions(concepts):
    """{alias_norm: [ids ordenados]} para alias compartidos por conceptos distintos."""
    owners = {}
    for c in concepts:
        for n, _ in alias_entries(c):
            owners.setdefault(n, set()).add(c.get("id"))
    return {a: sorted(ids, key=str) for a, ids in owners.items() if len(ids) > 1}


def read_jsonl(path):
    """Itera (numero_linea, objeto|None, error|None)."""
    with open(path, encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                yield n, json.loads(line), None
            except ValueError as e:
                yield n, None, "JSON invalido: %s" % e
