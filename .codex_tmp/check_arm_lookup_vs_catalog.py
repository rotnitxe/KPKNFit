import json
import re
from pathlib import Path

src = json.loads(Path("android-native/app/src/main/assets/exercise_catalog_v2.json").read_text(encoding="utf-8"))
kt = Path("android-native/app/src/main/java/com/example/kpkn/data/exercises/ExerciseTechniqueImageLookup.kt").read_text(encoding="utf-8")
ids = [
    "standing_biceps_curl", "biceps_curl_sentado_banco_plano", "preacher_curl", "spider_curl",
    "concentration_curl", "biceps_curl_bayesian", "hammer_curl", "reverse_curl", "biceps_curl_drag",
    "biceps_curl_zottman", "biceps_curl_waiter", "biceps_curl_crucifijo", "biceps_curl_superman",
    "biceps_curl_trx", "triceps_pushdown", "overhead_triceps_extension", "triceps_press_frances",
    "jm_press", "california_press", "tate_press", "triceps_patada", "katana_extension",
    "crossbody_triceps_extension", "triceps_extension", "triceps_extension_pjr_mancuerna",
    "triceps_flexiones_esfinge", "triceps_fondos_entre_bancos", "triceps_press_maquina",
    "triceps_rolling_extension", "forearms_curl_muneca_sentado", "forearms_curl_muneca_inverso_sentado",
    "forearms_curl_muneca_de_pie_tras_espalda_barra", "forearms_enrollamiento_muneca_rodillo",
    "supination", "pronation", "forearms_paseo_del_granjero",
    "forearms_suspension_isometrica_barra_fija", "forearms_pinza_de_discos",
]
non_impl = {"bilateral", "unilateral", "left", "right", "supinated", "neutral", "pronated"}
found = {}
for family in src["families"]:
    for definition in family["definitions"]:
        if definition["id"] in ids:
            found[definition["id"]] = definition

blocks = re.findall(r'const val (\w+_DEFINITION_ID) = "([^"]+)"', kt)
id_by_const = {const: value for const, value in blocks}
var_blocks = re.findall(r"(\w+_DEFINITION_ID) -> listOf\((.*?)\)\n", kt, re.S)
lookup = {}
for const, body in var_blocks:
    def_id = id_by_const.get(const)
    if not def_id:
        continue
    impls = re.findall(r'ExerciseTechniqueImageVariant\("([^"]+)"', body)
    lookup[def_id] = impls

gaps = []
for def_id in ids:
    definition = found[def_id]
    catalog_impls = sorted({
        options.get("implement")
        for configuration in definition["configurations"]
        for options in [configuration.get("selectedOptions", {})]
        if options.get("implement")
    })
    mapped = lookup.get(def_id, [])
    if catalog_impls:
        missing = [item for item in catalog_impls if item not in mapped]
        extra = [item for item in mapped if item not in catalog_impls]
        if missing or extra:
            gaps.append((def_id, "catalog", catalog_impls, "lookup", mapped, "missing", missing, "extra", extra))
    else:
        # no implement axis: expect a single token that appears in config ids or default
        cfg_tokens = []
        for configuration in definition["configurations"]:
            parts = configuration["id"].split("__")[1:]
            usable = [part for part in parts if part not in non_impl]
            cfg_tokens.extend(usable or ["default"])
        unique = sorted(set(cfg_tokens))
        if set(mapped) != set(unique) and not (mapped == ["default"] and unique == ["default"]):
            # drag: barbell in id; trx: only supinated -> default
            if mapped == ["cable"] and unique == ["cable"]:
                continue
            if mapped == ["barbell"] and unique == ["barbell"]:
                continue
            if mapped == ["dumbbells"] and unique == ["dumbbells"]:
                continue
            if mapped == ["plate"] and unique == ["plate"]:
                continue
            if mapped == ["default"] and unique == ["default"]:
                continue
            if mapped == ["default"] and unique == []:
                continue
            if mapped == ["default"] and set(unique) <= {"default"}:
                continue
            if mapped == ["default"] and unique == ["supinated"]:
                # trx grip-only config falls back to default drawable
                continue
            gaps.append((def_id, "no-axis", unique, "lookup", mapped))

print("arm defs in lookup", sum(1 for item in ids if item in lookup))
print("GAPS")
for gap in gaps:
    print(gap)
