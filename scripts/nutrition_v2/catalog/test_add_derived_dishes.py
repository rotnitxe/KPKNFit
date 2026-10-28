"""Pruebas de add_derived_dishes: paquete minimo con el esquema real + mini exportacion."""
import json
import sqlite3
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

import add_derived_dishes as A  # noqa: E402
import build_pack as B  # noqa: E402
import nutrients as N  # noqa: E402


def make_pack(path):
    con = sqlite3.connect(str(path))
    con.executescript(B.SCHEMA)
    con.execute("INSERT INTO metadata VALUES ('formatVersion','1'),('generation','abc123')")
    con.execute("INSERT INTO record VALUES ('usda_1','USDA_FNDDS','1','Leche',NULL,NULL,NULL,NULL,NULL,'RAW','NA',100.0,'g',NULL)")
    con.execute("INSERT INTO nutrient_value VALUES ('usda_1',?,61.0,0)", (N.key_id("energy"),))
    con.commit()
    con.close()


def make_export(path):
    records = {
        "kpkn_cafe": {"name": "Café con leche", "densityGPerMl": 1.02,
                      "per100g": {"energy": 40.123456789, "calcium": 90.0, "protein": None,
                                  "fat": 1e9, "clave_rara": 1.0}},
        "kpkn_arroz": {"name": "Arroz", "densityGPerMl": None, "per100g": {"energy": 130.0}},
        "otro_x": {"name": "No", "per100g": {"energy": 1.0}},
    }
    path.write_text(json.dumps({"conceptsSha256": "deadbeef", "records": records,
                                "concepts": [{"id": "cafe", "category": "bebidas"}]}), encoding="utf-8")


def test_adds_dishes(tmp_path):
    pack, exp, out = tmp_path / "in.sqlite", tmp_path / "e.json", tmp_path / "out.sqlite"
    make_pack(pack)
    make_export(exp)
    before = A.file_sha256(pack)
    s = A.run(pack, exp, out)
    assert A.file_sha256(pack) == before
    assert s["dishes"] == 2 and s["values"] == 3
    assert s["skipped"] == {"clave_sin_id": 1, "nulo": 1, "fuera_de_rango": 1}
    assert s["oldGeneration"] == "abc123" and s["newGeneration"] != "abc123"
    con = sqlite3.connect(str(out))
    assert con.execute("SELECT source, source_record_id, name, name_es, category, preparation, form, basis_amount, "
                       "basis_unit, density_g_per_ml FROM record WHERE id='kpkn_cafe'").fetchone() == \
        ("KPKN_DERIVED", "cafe", "Café con leche", "Café con leche", "bebidas", "PREPARED", "NA", 100.0, "g", 1.02)
    assert con.execute("SELECT COUNT(*) FROM record WHERE id LIKE 'otro%'").fetchone()[0] == 0
    rows = {n: (a, q) for n, a, q in con.execute(
        "SELECT nutrient_id, amount, quality FROM nutrient_value WHERE record_id='kpkn_cafe'")}
    assert rows == {N.key_id("energy"): (40.123457, N.Q_DERIVED), N.key_id("calcium"): (90.0, N.Q_DERIVED)}
    meta = dict(con.execute("SELECT key, value FROM metadata"))
    assert meta["derivedDishes"] == "2" and meta["derivedFrom"] == "deadbeef" and meta["formatVersion"] == "1"
    assert con.execute("SELECT COUNT(*) FROM portion").fetchone()[0] == 0
    con.close()
    assert (tmp_path / "out.sqlite.sha256").read_text().startswith(s["sha256"])


def test_deterministic(tmp_path):
    pack, exp = tmp_path / "in.sqlite", tmp_path / "e.json"
    make_pack(pack)
    make_export(exp)
    a = A.run(pack, exp, tmp_path / "a.sqlite")
    b = A.run(pack, exp, tmp_path / "b.sqlite")
    assert a["newGeneration"] == b["newGeneration"] and a["sha256"] == b["sha256"]


def test_existing_id_fails(tmp_path):
    pack, exp, out = tmp_path / "in.sqlite", tmp_path / "e.json", tmp_path / "out.sqlite"
    make_pack(pack)
    make_export(exp)
    con = sqlite3.connect(str(pack))
    con.execute("INSERT INTO record VALUES ('kpkn_arroz','X','1','a',NULL,NULL,NULL,NULL,NULL,'RAW','NA',100.0,'g',NULL)")
    con.commit()
    con.close()
    assert A.main(["--pack", str(pack), "--export", str(exp), "--out", str(out)]) == 1
    with pytest.raises(A.DerivedError):
        A.run(pack, exp, out)
