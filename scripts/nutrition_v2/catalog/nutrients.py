"""Tabla NUTRIENTS (ids estables) y mapeos por fuente del paquete de catalogo v0.

Fuente de verdad: docs/nutrition-v2/F1A_PAQUETE.md seccion 3. Aqui solo hay datos y ayudas
puras (sin lectura de archivos): los lectores viven en build_pack.py y off_chile.py.
"""
from collections import namedtuple
import math
import re

Nutrient = namedtuple("Nutrient", "id key unit max_value")

# max_value: techo fisico generoso por 100 g/ml EN LA UNIDAD DEL NUTRIENTE. Un valor mayor se
# trata como dato corrupto: queda ausente (nunca se recorta ni se inventa) y se cuenta en el informe.
NUTRIENTS = (
    Nutrient(1, "energy", "kcal", 910.0),
    Nutrient(2, "protein", "g", 100.0),
    Nutrient(3, "fat", "g", 100.0),
    Nutrient(4, "carbs", "g", 100.0),
    Nutrient(5, "fiber", "g", 100.0),
    Nutrient(6, "sugars", "g", 100.0),
    Nutrient(7, "added_sugars", "g", 100.0),
    Nutrient(8, "sat_fat", "g", 100.0),
    Nutrient(9, "mono_fat", "g", 100.0),
    Nutrient(10, "poly_fat", "g", 100.0),
    Nutrient(11, "trans_fat", "g", 100.0),
    Nutrient(12, "cholesterol", "mg", 5000.0),
    Nutrient(13, "sodium", "mg", 40000.0),
    Nutrient(14, "potassium", "mg", 20000.0),
    Nutrient(15, "calcium", "mg", 40000.0),
    Nutrient(16, "iron", "mg", 500.0),
    Nutrient(17, "magnesium", "mg", 3000.0),
    Nutrient(18, "phosphorus", "mg", 20000.0),
    Nutrient(19, "zinc", "mg", 500.0),
    Nutrient(20, "selenium", "µg", 10000.0),
    Nutrient(21, "iodine", "µg", 500000.0),
    Nutrient(22, "vitamin_a_rae", "µg", 100000.0),
    Nutrient(23, "vitamin_c", "mg", 5000.0),
    Nutrient(24, "vitamin_d", "µg", 2000.0),
    Nutrient(25, "vitamin_e", "mg", 1000.0),
    Nutrient(26, "vitamin_k", "µg", 10000.0),
    Nutrient(27, "thiamin", "mg", 500.0),
    Nutrient(28, "riboflavin", "mg", 500.0),
    Nutrient(29, "niacin", "mg", 1000.0),
    Nutrient(30, "vitamin_b6", "mg", 500.0),
    Nutrient(31, "folate_dfe", "µg", 10000.0),
    Nutrient(32, "vitamin_b12", "µg", 5000.0),
    Nutrient(33, "choline", "mg", 5000.0),
    Nutrient(34, "epa_dha", "g", 100.0),
    Nutrient(35, "ala", "g", 100.0),
    Nutrient(36, "caffeine", "mg", 6000.0),
    Nutrient(37, "alcohol", "g", 100.0),
    Nutrient(38, "water", "g", 100.0),
)
BY_ID = {n.id: n for n in NUTRIENTS}
BY_KEY = {n.key: n for n in NUTRIENTS}
REQUIRED_IDS = (BY_KEY["energy"].id, BY_KEY["protein"].id, BY_KEY["fat"].id, BY_KEY["carbs"].id)

# Calidad: 0 medido/declarado, 1 calculado por la fuente, 2 derivado por nosotros, 3 traza/bajo limite.
Q_DECLARED, Q_SOURCE_CALC, Q_DERIVED, Q_TRACE = 0, 1, 2, 3

UG = "µg"


def key_id(key):
    return BY_KEY[key].id


def normalize_unit(raw):
    """Unidad de la fuente en forma canonica: kcal | g | mg | µg (acepta KCAL, UG, '�g', 'μg')."""
    text = str(raw or "").strip()
    low = text.lower()
    if low in ("kcal", "g", "mg"):
        return low
    if low == "ug" or (len(text) == 2 and text[1].lower() == "g" and not text[0].isascii()):
        return UG
    return low


# --------------------------------------------------------------------------------------------
# USDA (FNDDS, Foundation, SR Legacy): id de nutriente -> (nombres admitidos en minusculas, unidad)
# --------------------------------------------------------------------------------------------
def _u(names, unit):
    return (frozenset(n.lower() for n in names), unit)


USDA_EXPECTED = {
    1008: _u(["Energy"], "kcal"),
    2047: _u(["Energy (Atwater General Factors)"], "kcal"),
    2048: _u(["Energy (Atwater Specific Factors)"], "kcal"),
    1003: _u(["Protein"], "g"),
    1004: _u(["Total lipid (fat)"], "g"),
    1085: _u(["Total fat (NLEA)"], "g"),
    1005: _u(["Carbohydrate, by difference"], "g"),
    1050: _u(["Carbohydrate, by summation"], "g"),
    1079: _u(["Fiber, total dietary"], "g"),
    2000: _u(["Total Sugars", "Sugars, Total"], "g"),
    1063: _u(["Sugars, Total", "Sugars, Total NLEA"], "g"),
    1235: _u(["Sugars, added", "Added sugars"], "g"),
    1258: _u(["Fatty acids, total saturated"], "g"),
    1292: _u(["Fatty acids, total monounsaturated"], "g"),
    1293: _u(["Fatty acids, total polyunsaturated"], "g"),
    1257: _u(["Fatty acids, total trans"], "g"),
    1253: _u(["Cholesterol"], "mg"),
    1093: _u(["Sodium, Na"], "mg"),
    1092: _u(["Potassium, K"], "mg"),
    1087: _u(["Calcium, Ca"], "mg"),
    1089: _u(["Iron, Fe"], "mg"),
    1090: _u(["Magnesium, Mg"], "mg"),
    1091: _u(["Phosphorus, P"], "mg"),
    1095: _u(["Zinc, Zn"], "mg"),
    1103: _u(["Selenium, Se"], UG),
    1100: _u(["Iodine, I"], UG),
    1106: _u(["Vitamin A, RAE"], UG),
    1162: _u(["Vitamin C, total ascorbic acid"], "mg"),
    1114: _u(["Vitamin D (D2 + D3)"], UG),
    1109: _u(["Vitamin E (alpha-tocopherol)"], "mg"),
    1185: _u(["Vitamin K (phylloquinone)"], UG),
    1165: _u(["Thiamin"], "mg"),
    1166: _u(["Riboflavin"], "mg"),
    1167: _u(["Niacin"], "mg"),
    1175: _u(["Vitamin B-6"], "mg"),
    1190: _u(["Folate, DFE"], UG),
    1177: _u(["Folate, total"], UG),
    1178: _u(["Vitamin B-12"], UG),
    1180: _u(["Choline, total"], "mg"),
    1278: _u(["PUFA 20:5 n-3 (EPA)"], "g"),
    1272: _u(["PUFA 22:6 n-3 (DHA)"], "g"),
    1404: _u(["PUFA 18:3 n-3 c,c,c (ALA)"], "g"),
    1270: _u(["PUFA 18:3"], "g"),
    1057: _u(["Caffeine"], "mg"),
    1018: _u(["Alcohol, ethyl"], "g"),
    1051: _u(["Water"], "g"),
}

# nutriente KPKN -> ids USDA por prioridad (primero que exista gana): (usda_id, calidad)
USDA_RULES = {
    1: ((1008, Q_DECLARED), (2047, Q_SOURCE_CALC), (2048, Q_SOURCE_CALC)),
    2: ((1003, Q_DECLARED),),
    3: ((1004, Q_DECLARED), (1085, Q_DECLARED)),
    4: ((1005, Q_DECLARED), (1050, Q_DECLARED)),
    5: ((1079, Q_DECLARED),),
    6: ((2000, Q_DECLARED), (1063, Q_DECLARED)),
    7: ((1235, Q_DECLARED),),
    8: ((1258, Q_DECLARED),),
    9: ((1292, Q_DECLARED),),
    10: ((1293, Q_DECLARED),),
    11: ((1257, Q_DECLARED),),
    12: ((1253, Q_DECLARED),),
    13: ((1093, Q_DECLARED),),
    14: ((1092, Q_DECLARED),),
    15: ((1087, Q_DECLARED),),
    16: ((1089, Q_DECLARED),),
    17: ((1090, Q_DECLARED),),
    18: ((1091, Q_DECLARED),),
    19: ((1095, Q_DECLARED),),
    20: ((1103, Q_DECLARED),),
    21: ((1100, Q_DECLARED),),
    22: ((1106, Q_DECLARED),),
    23: ((1162, Q_DECLARED),),
    24: ((1114, Q_DECLARED),),
    25: ((1109, Q_DECLARED),),
    26: ((1185, Q_DECLARED),),
    27: ((1165, Q_DECLARED),),
    28: ((1166, Q_DECLARED),),
    29: ((1167, Q_DECLARED),),
    30: ((1175, Q_DECLARED),),
    31: ((1190, Q_DECLARED), (1177, Q_SOURCE_CALC)),
    32: ((1178, Q_DECLARED),),
    33: ((1180, Q_DECLARED),),
    35: ((1404, Q_DECLARED), (1270, Q_DECLARED)),
    36: ((1057, Q_DECLARED),),
    37: ((1018, Q_DECLARED),),
    38: ((1051, Q_DECLARED),),
}
# Sumas: nutriente -> componentes USDA obligatorios (todos deben estar); calidad 2
USDA_SUMS = {34: (1278, 1272)}
USDA_TARGET_IDS = frozenset(uid for rules in USDA_RULES.values() for uid, _ in rules) | {
    uid for ids in USDA_SUMS.values() for uid in ids}
assert USDA_TARGET_IDS <= set(USDA_EXPECTED)

# --------------------------------------------------------------------------------------------
# CIQUAL: const_code -> unidad esperada (se comprueba contra const_nom_eng del archivo const)
# --------------------------------------------------------------------------------------------
CIQUAL_UNITS = {
    328: "kcal", 25000: "g", 40000: "g", 31000: "g", 34100: "g", 32000: "g",
    40302: "g", 40303: "g", 40304: "g", 75100: "mg", 10110: "mg", 10190: "mg", 10200: "mg",
    10260: "mg", 10120: "mg", 10150: "mg", 10300: "mg", 10340: UG, 10530: UG,
    51200: UG, 51330: UG, 55100: "mg", 52100: UG, 53100: "mg", 71010: "mg", 54101: UG, 54104: UG,
    56100: "mg", 56200: "mg", 56310: "mg", 56500: "mg", 56702: UG, 56700: UG, 56600: UG,
    42053: "g", 42263: "g", 41833: "g", 60000: "g", 400: "g",
}
CIQUAL_RULES = {
    1: ((328, Q_SOURCE_CALC),),
    2: ((25000, Q_DECLARED),),
    3: ((40000, Q_DECLARED),),
    4: ((31000, Q_DECLARED),),
    5: ((34100, Q_DECLARED),),
    6: ((32000, Q_DECLARED),),
    8: ((40302, Q_DECLARED),),
    9: ((40303, Q_DECLARED),),
    10: ((40304, Q_DECLARED),),
    12: ((75100, Q_DECLARED),),
    13: ((10110, Q_DECLARED),),
    14: ((10190, Q_DECLARED),),
    15: ((10200, Q_DECLARED),),
    16: ((10260, Q_DECLARED),),
    17: ((10120, Q_DECLARED),),
    18: ((10150, Q_DECLARED),),
    19: ((10300, Q_DECLARED),),
    20: ((10340, Q_DECLARED),),
    21: ((10530, Q_DECLARED),),
    23: ((55100, Q_DECLARED),),
    24: ((52100, Q_DECLARED),),
    25: ((53100, Q_DECLARED), (71010, Q_DECLARED)),
    27: ((56100, Q_DECLARED),),
    28: ((56200, Q_DECLARED),),
    29: ((56310, Q_DECLARED),),
    30: ((56500, Q_DECLARED),),
    31: ((56702, Q_DECLARED), (56700, Q_SOURCE_CALC)),
    32: ((56600, Q_DECLARED),),
    35: ((41833, Q_DECLARED),),
    37: ((60000, Q_DECLARED),),
    38: ((400, Q_DECLARED),),
}
# Sumas CIQUAL: nutriente -> (obligatorios [(const, divisor)], opcionales [(const, divisor)])
CIQUAL_SUMS = {
    22: (((51200, 1.0), (51330, 12.0)), ()),     # vitamina A = retinol + beta-caroteno / 12
    26: (((54101, 1.0),), ((54104, 1.0),)),      # K1 (+ K2 si existe)
    34: (((42053, 1.0), (42263, 1.0)), ()),      # EPA + DHA
}
CIQUAL_TARGET_CODES = frozenset(
    [c for rules in CIQUAL_RULES.values() for c, _ in rules]
    + [c for req, opt in CIQUAL_SUMS.values() for c, _ in tuple(req) + tuple(opt)])
assert CIQUAL_TARGET_CODES <= set(CIQUAL_UNITS)

# --------------------------------------------------------------------------------------------
# Formas fisicas, preparacion, volumen (ayudas puras sobre el nombre)
# --------------------------------------------------------------------------------------------
ML_PER_UNIT = {"cup": 236.588, "tablespoon": 14.787, "tbsp": 14.787, "teaspoon": 4.929, "tsp": 4.929,
               "fl oz": 29.574}
_QTY = r"(\d+\s+\d+/\d+|\d+/\d+|\d*\.?\d+)"
_VOLUME_LABEL = re.compile(
    r"^\s*" + _QTY + r"\s*(cups?|tablespoons?|tbsp|teaspoons?|tsp|fl\.? ?oz)\b", re.IGNORECASE)


def parse_quantity(text):
    text = str(text).strip()
    try:
        if " " in text and "/" in text:
            whole, frac = text.split(None, 1)
            num, den = frac.split("/")
            return float(whole) + float(num) / float(den)
        if "/" in text:
            num, den = text.split("/")
            return float(num) / float(den)
        return float(text)
    except (ValueError, ZeroDivisionError):
        return None


def _unit_alias(text):
    low = re.sub(r"[.\s]+", " ", str(text or "").strip().lower()).strip()
    return {"cups": "cup", "tablespoons": "tablespoon", "teaspoons": "teaspoon", "floz": "fl oz"}.get(low, low)


def volume_unit_key(name):
    low = _unit_alias(name)
    return low if low in ML_PER_UNIT else None


def portion_ml(label, amount, unit_name):
    """ml de una porcion en volumen (cup, tbsp, tsp, fl oz) o None. Usa la unidad estructurada si
    la fuente la trae; si no, el comienzo de la etiqueta ('1 cup', '1/2 fl oz')."""
    key = volume_unit_key(unit_name)
    if key is not None and amount is not None and amount > 0:
        return round(amount * ML_PER_UNIT[key], 3)
    match = _VOLUME_LABEL.match(str(label or ""))
    if match:
        qty = parse_quantity(match.group(1))
        unit = volume_unit_key(match.group(2))
        if qty and qty > 0 and unit:
            return round(qty * ML_PER_UNIT[unit], 3)
    return None


def density_from_portions(portions):
    """Densidad g/ml de la PRIMERA porcion con volumen; solo si cae en [0.2, 2.0]."""
    for _label, grams, ml in portions:
        if ml:
            density = grams / ml
            return round(density, 4) if 0.2 <= density <= 2.0 else None
    return None


def words(text):
    return re.findall(r"[a-z]+", str(text or "").lower())


_EXTRA_COOKED = {"broiled", "grilled", "steamed", "stewed", "braised", "simmered", "microwaved", "poached",
                 "scrambled", "sauteed", "sauted"}
_LIQUID_WORDS = {"fluid", "beverage", "beverages", "juice", "juices", "drink", "drinks", "soda", "beer", "wine",
                 "liqueur", "cola", "smoothie", "broth", "brewed", "lemonade", "nectar", "kefir", "boisson", "jus"}
_POWDER_FIRST = {"milk", "buttermilk", "cream", "whey", "egg", "eggs", "yeast", "gravy", "soup", "sauce",
                 "seasoning", "beverage", "beverages"}
# Categorias WWEIA (FNDDS) cuyas fichas son bebidas o liquidos; el resto no se marca LIQUID por categoria.
FNDDS_LIQUID_CATEGORIES = frozenset([
    "Apple juice", "Baby juice", "Baby water", "Beer", "Bottled water", "Citrus juice", "Coffee",
    "Diet soft drinks", "Diet sport and energy drinks", "Enhanced water", "Flavored milk, lowfat",
    "Flavored milk, nonfat", "Flavored milk, reduced fat", "Flavored milk, whole",
    "Flavored or carbonated water", "Formula, prepared from powder", "Formula, ready-to-feed",
    "Fruit drinks", "Human milk", "Liquor and cocktails", "Milk shakes and other dairy drinks",
    "Milk, lowfat", "Milk, nonfat", "Milk, reduced fat", "Milk, whole", "Nutritional beverages",
    "Other diet drinks", "Other fruit juice", "Plant-based milk", "Smoothies and grain drinks",
    "Soft drinks", "Sport and energy drinks", "Tap water", "Tea", "Vegetable juice", "Wine",
    "Soups, broth-based", "Soups, cream-based", "Ramen and Asian broth-based soups"])
_PREPARED_PHRASE = re.compile(r"prepared (from|with|using)|reconstituted|ready[- ]to[- ](drink|feed|serve)|made from|brewed")


def extra_cooked(name, preparation):
    """canonical() del adaptador solo conoce cooked/boiled/roasted/fried/baked; amplia con los verbos
    de cocinado de FNDDS cuando canonical devolvio AS_SOLD."""
    if preparation == "AS_SOLD" and set(words(name)) & _EXTRA_COOKED:
        return "COOKED"
    return preparation


def is_powder(name, category=None):
    low = str(name or "").lower()
    if _PREPARED_PHRASE.search(low.replace("not reconstituted", "")):
        return False    # "prepared from powder" ya es liquido/preparado
    toks = words(low)
    if set(toks) & {"powder", "powdered", "poudre"}:
        return True
    if re.search(r"dry (powdered )?mix|dried mix", low):
        return True
    if toks and toks[0] in _POWDER_FIRST and ("dry" in toks or "dried" in toks)             and not re.search(r"dry (roasted|heat)", low):
        return True
    return bool(category and re.search(r"powders?", str(category), re.IGNORECASE))


def physical_form(name, category=None, liquid_hint=False):
    """POWDER | LIQUID | NA a partir del nombre; `liquid_hint` = evidencia de la ficha (categoria de bebida,
    grupo CIQUAL de bebidas/leche, porcion en fl oz)."""
    if is_powder(name, category):
        return "POWDER"
    toks = words(name)
    if set(toks) & _LIQUID_WORDS:
        return "LIQUID"
    if toks and toks[0] in ("milk", "water") and not (set(toks) & {"dry", "dried", "malted"}):
        return "LIQUID"
    return "LIQUID" if liquid_hint else "NA"


def alias_norm(text):
    """minusculas, sin tildes, solo [a-z0-9 ] con espacios simples (la app normaliza la consulta igual)."""
    import unicodedata
    folded = "".join(c for c in unicodedata.normalize("NFKD", str(text or "").lower())
                     if not unicodedata.combining(c))
    return " ".join(re.sub(r"[^a-z0-9]+", " ", folded).split())


def clean_value(value, nutrient_id):
    """Valor dentro de [0, techo] o None. Nunca recorta."""
    if value is None or isinstance(value, bool):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if not math.isfinite(number) or number < 0 or number > BY_ID[nutrient_id].max_value:
        return None
    return number


# Claves INFOODS de CIQUAL (const_2025_11_03.xml, code_INFOODS) para comprobar cada const_code mapeado.
CIQUAL_TAGS = {
    328: "ENERC", 25000: "PROCNT", 40000: "FAT", 31000: "CHOAVL", 34100: "FIB-", 32000: "SUGAR",
    40302: "FASAT", 40303: "FAMS", 40304: "FAPU", 75100: "CHOL-", 10110: "NA", 10190: "K", 10200: "CA",
    10260: "FE", 10120: "MG", 10150: "P", 10300: "ZN", 10340: "SE", 10530: "ID", 51200: "RETOL",
    51330: "CARTB", 55100: "VITC", 52100: "VITD-", 53100: "VITE-", 71010: "TOCPHA", 54101: "VITK1",
    54104: "VITK2", 56100: "THIA", 56200: "RIBF", 56310: "NIA", 56500: "VITB6-", 56702: "FOLDFE",
    56700: "FOL", 56600: "VITB12", 42053: "F20D5N3", 42263: "F22D6N3", 41833: "F18D3N3", 60000: "ALC",
    400: "WATER",
}
assert set(CIQUAL_TAGS) == set(CIQUAL_UNITS)

# Coherencia interna de las tablas: la unidad de la fuente coincide con la del nutriente destino.
for _nid, _rules in USDA_RULES.items():
    for _uid, _q in _rules:
        assert USDA_EXPECTED[_uid][1] == BY_ID[_nid].unit, (_nid, _uid)
for _nid, _ids in USDA_SUMS.items():
    for _uid in _ids:
        assert USDA_EXPECTED[_uid][1] == BY_ID[_nid].unit, (_nid, _uid)
for _nid, _rules in CIQUAL_RULES.items():
    for _code, _q in _rules:
        assert CIQUAL_UNITS[_code] == BY_ID[_nid].unit, (_nid, _code)
for _nid, (_req, _opt) in CIQUAL_SUMS.items():
    for _code, _div in tuple(_req) + tuple(_opt):
        assert CIQUAL_UNITS[_code] == BY_ID[_nid].unit, (_nid, _code)
