import json
from pathlib import Path

src = json.loads(
    Path("android-native/app/src/main/assets/exercise_catalog_v2.json").read_text(encoding="utf-8")
)
ids = [
    "standing_biceps_curl",
    "biceps_curl_sentado_banco_plano",
    "preacher_curl",
    "spider_curl",
    "concentration_curl",
    "biceps_curl_bayesian",
    "hammer_curl",
    "reverse_curl",
    "biceps_curl_drag",
    "biceps_curl_zottman",
    "biceps_curl_waiter",
    "biceps_curl_crucifijo",
    "biceps_curl_superman",
    "biceps_curl_trx",
    "triceps_pushdown",
    "overhead_triceps_extension",
    "triceps_press_frances",
    "jm_press",
    "california_press",
    "tate_press",
    "triceps_patada",
    "katana_extension",
    "crossbody_triceps_extension",
    "triceps_extension",
    "triceps_extension_pjr_mancuerna",
    "triceps_flexiones_esfinge",
    "triceps_fondos_entre_bancos",
    "triceps_press_maquina",
    "triceps_rolling_extension",
    "forearms_curl_muneca_sentado",
    "forearms_curl_muneca_inverso_sentado",
    "forearms_curl_muneca_de_pie_tras_espalda_barra",
    "forearms_enrollamiento_muneca_rodillo",
    "supination",
    "pronation",
    "forearms_paseo_del_granjero",
    "forearms_suspension_isometrica_barra_fija",
    "forearms_pinza_de_discos",
]
found = {}
for family in src["families"]:
    for definition in family["definitions"]:
        if definition["id"] in ids:
            found[definition["id"]] = {
                "family": family["id"],
                "name": definition["canonicalName"],
                "axes": definition.get("optionAxes", []),
                "default": definition.get("defaultConfigurationId"),
                "cfgs": [
                    (configuration["id"], configuration.get("selectedOptions", {}))
                    for configuration in definition["configurations"]
                ],
            }

print("found", len(found), "of", len(ids))
print("MISSING", [item for item in ids if item not in found])
print("--- FAMILIES ---")
for family in src["families"]:
    fid = family["id"]
    if any(token in fid for token in ("biceps", "triceps", "forearm", "elbow", "curl", "grip")):
        print(fid, [definition["id"] for definition in family["definitions"]])
print("--- DETAIL ---")
for item in ids:
    if item not in found:
        continue
    data = found[item]
    implements = sorted({options.get("implement", "") for _, options in data["cfgs"]})
    print(item, "|", data["name"], "| axes=", data["axes"], "| impls=", implements, "| n=", len(data["cfgs"]))
    for configuration_id, options in data["cfgs"]:
        print("   ", configuration_id, options)
