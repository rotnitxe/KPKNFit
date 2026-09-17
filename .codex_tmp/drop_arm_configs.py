import json
from pathlib import Path

ROOT = Path(".")


def walk_drop_token(obj, token: str, keys=("compatibleEquipmentIds",)):
    if isinstance(obj, dict):
        for key, value in obj.items():
            if key in keys and isinstance(value, list):
                obj[key] = [item for item in value if item != token]
            else:
                walk_drop_token(value, token, keys)
    elif isinstance(obj, list):
        for item in obj:
            walk_drop_token(item, token, keys)


def main() -> None:
    biceps_path = ROOT / "catalog/exercises/v2/source/families/elbow_flexion_biceps_curl.json"
    katana_path = ROOT / "catalog/exercises/v2/source/families/triceps_katana_extension.json"
    briefs_path = ROOT / "catalog/exercises/v2/curation/editorial_briefs.json"

    biceps = json.loads(biceps_path.read_text(encoding="utf-8"))
    reverse = next(d for d in biceps["family"]["definitions"] if d["id"] == "reverse_curl")
    before = len(reverse["configurations"])
    reverse["configurations"] = [
        cfg for cfg in reverse["configurations"] if cfg["id"] != "reverse_curl__h_bar"
    ]
    assert len(reverse["configurations"]) == before - 1, (before, len(reverse["configurations"]))
    walk_drop_token(reverse, "h_bar")
    reverse["description"] = (
        "El Curl Invertido pone las palmas a mirar al suelo: el protagonista pasa a ser el antebrazo "
        "y el braquial, y el bíceps trabaja en desventaja mecánica. Se ejecuta como un curl normal "
        "pero con el dorso de la mano liderando la subida, con cargas más modestas de lo habitual. "
        "La banda o la polea permiten la misma idea con otro tacto. El equilibrador del brazo: "
        "antebrazos que combinan con unos buenos bíceps."
    )
    biceps_path.write_text(json.dumps(biceps, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    katana = json.loads(katana_path.read_text(encoding="utf-8"))
    definition = next(d for d in katana["family"]["definitions"] if d["id"] == "katana_extension")
    before_k = len(definition["configurations"])
    definition["configurations"] = [
        cfg for cfg in definition["configurations"] if cfg["id"] != "katana_extension__band__bilateral"
    ]
    assert len(definition["configurations"]) == before_k - 1
    walk_drop_token(definition, "band")
    definition["optionAxes"] = ["laterality"]
    for cfg in definition["configurations"]:
        options = cfg.get("selectedOptions", {})
        options.pop("implement", None)
        cfg["selectedOptions"] = options
    definition["description"] = (
        "La Extensión Katana desenfunda el brazo como una espada: de pie ante la polea, el codo se "
        "extiende llevando las manos arriba con los cables cruzados. Se ejecuta con dos poleas "
        "independientes a altura media, torso estable y trayectoria limpia de principio a fin. "
        "Elegante y distinto, con una congestión final sorprendente."
    )
    katana_path.write_text(json.dumps(katana, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    briefs = json.loads(briefs_path.read_text(encoding="utf-8"))
    if "reverse_curl" in briefs and "configurations" in briefs["reverse_curl"]:
        briefs["reverse_curl"]["configurations"].pop("reverse_curl__h_bar", None)
    if "katana_extension" in briefs and "configurations" in briefs["katana_extension"]:
        briefs["katana_extension"]["configurations"].pop("katana_extension__band__bilateral", None)
    briefs_path.write_text(json.dumps(briefs, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("ok reverse", [c["id"] for c in reverse["configurations"]])
    print("ok katana", [c["id"] for c in definition["configurations"]], "axes", definition["optionAxes"])


if __name__ == "__main__":
    main()
