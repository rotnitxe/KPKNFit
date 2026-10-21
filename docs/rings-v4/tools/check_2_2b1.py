"""Verifica los cambios declarados de 2.2b1 frente al bloque 1 sin ejecutar Gradle.

--candidate valida el máster generado antes de copiarlo al recurso.
--reanchor pega las capturas verificadas en los mapas de caracterización.
La ejecución normal exige que capturas, pines y recurso coincidan.
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import textwrap
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASE = "fd4940a13"
TEST = "android-native/app/src/test/java/com/example/kpkn/"
GOLDEN = "android-native/app/src/test/resources/auge/golden/expected.txt"
SNAPSHOTS = {
    "domain": ("domain/auge/SessionDrainRecipeCharacterizationTest.kt", 7),
    "editor": ("screens/sessioneditor/SessionEditorAugeCharacterizationTest.kt", 6),
}
LITERAL = re.compile(r'            "([^"]+)" to """\n(.*?)\n            """\.trimIndent\(\)', re.S)
DRAIN = re.compile(r"PredictedDrain\(cns=(\d+), muscular=(\d+), spinal=(\d+)\)")


def original(path):
    return subprocess.check_output(["git", "show", f"{BASE}:{path}"], cwd=ROOT).decode("utf-8").replace("\r\n", "\n")


def literals(text):
    return {case: textwrap.dedent(body) for case, body in LITERAL.findall(text)}


def stdout(class_name):
    path = ROOT / "android-native/app/build/test-results/testBaseDebugUnitTest" / f"TEST-{class_name}.xml"
    return "\n".join(node.text or "" for node in ET.parse(path).getroot().iter("system-out"))


def stats(deltas):
    return dict(changed_numbers=len(deltas), maximum=max(deltas, default=0), mean=sum(deltas) / max(1, len(deltas)))


def numeric_deltas(before, after):
    # Los bits de Double son identidad binaria, no puntos de batería.
    strip_bits = lambda s: re.sub(r"\[-?\d+\]", "", s)
    a = re.findall(r"-?\d+(?:\.\d+)?", strip_bits(before))
    b = re.findall(r"-?\d+(?:\.\d+)?", strip_bits(after))
    assert len(a) == len(b), (before, after)
    return [abs(float(x) - float(y)) for x, y in zip(a, b) if x != y]


def field_statistics(row):
    before, after, field = row["before"], row["after"], row["field"]
    added = {}
    if field == "insights":
        pattern = r"exerciseId=([^,]+), name=.*?, muscular=(\d+), cns=(\d+), spinal=(\d+), total=(\d+)"
        a = {m[0]: list(map(int, m[1:])) for m in re.findall(pattern, before)}
        b = {m[0]: list(map(int, m[1:])) for m in re.findall(pattern, after)}
        assert a.keys() <= b.keys(), "Desapareció un insight"
        deltas = [abs(x-y) for key in a for x, y in zip(a[key], b[key]) if x != y]
        added = {key: b[key] for key in sorted(b.keys() - a.keys())}
    else:
        deltas = numeric_deltas(before, after)
    return dict(consumer=row["consumer"], case=row["case"], field=field,
                unit="series" if field == "sets" else "puntos", deltas=deltas,
                added_insights=added, **stats(deltas))


def verify_golden(candidate):
    before = original(GOLDEN).splitlines()
    after = candidate.splitlines()
    assert len(before) == len(after), "Cambió la estructura del corpus"
    changes = []
    for old, new in zip(before, after):
        if old == new:
            continue
        a, b = old.split(" | "), new.split(" | ")
        assert len(a) == len(b) and a[:2] == b[:2], (old, new)
        case, instant = a[:2]
        assert instant.startswith("session#") and case in {"A08", "A11"}, (case, instant)
        allowed = {"drain", "pm", "v2global"} if case == "A08" else {"drain"}
        for old_field, new_field in zip(a[2:], b[2:]):
            if old_field == new_field:
                continue
            key = old_field.split("=", 1)[0]
            assert key == new_field.split("=", 1)[0] and key in allowed, (case, key)
            if case == "A11":
                assert old_field.split(",")[1:] == new_field.split(",")[1:], "Single: solo cambia Energía"
            changes.append(dict(case=case, instant=instant, field=key, before=old_field, after=new_field,
                                deltas=numeric_deltas(old_field, new_field)))
    assert changes and any(row["case"] == "A08" for row in changes), "Máster sin regenerar"
    return changes


def verify_snapshots(kind, before, after):
    editor_allowed = {
        "unilateral": {"drain"}, "cardio": {"drain"}, "filtrado-rir": {"drain"},
        "superserie": {"drain", "muscular", "energy", "spinal", "insights"},
        "axial": {"drain", "energy"},
        "sin-ficha": {"drain", "muscular", "energy", "spinal", "insights", "sets", "volume", "roles", "recommendations"},
    }
    changes, drains = [], []
    for case in before:
        old_lines, new_lines = before[case].splitlines(), after[case].splitlines()
        assert len(old_lines) == len(new_lines), (kind, case)
        for old, new in zip(old_lines, new_lines):
            if old == new:
                continue
            key = old.split("=", 1)[0]
            assert key == new.split("=", 1)[0], (kind, case, key)
            if kind == "domain":
                assert key in {"global", "predicted"}, (kind, case, key)
                old_drain, new_drain = DRAIN.search(old), DRAIN.search(new)
                assert old_drain and new_drain
                # Superseries: el descanso cambia los canales y su reparto por el acumulado.
                # Duración: Energía en los logs (80/96 min), Columna solo en axial (96 min).
                if key == "global" and case != "superserie":
                    assert old_drain[2] == new_drain[2] and old.split(";muscles=")[1] == new.split(";muscles=")[1]
                    if case != "axial":
                        assert old_drain[3] == new_drain[3]
                if key == "predicted":
                    assert case in {"axial", "filtrado-rir", "superserie"}, (case, old, new)
            else:
                assert key in editor_allowed[case], (kind, case, key)
                if key == "sets":
                    assert old.split(";", 1)[1] == new.split(";", 1)[1], "Solo cambia el conteo de series"
            changes.append(dict(consumer=kind, case=case, field=key, before=old, after=new))
            a, b = DRAIN.search(old), DRAIN.search(new)
            if a and b and key in {"global", "predicted", "drain"}:
                deltas = [abs(int(x) - int(y)) for x, y in zip(a.groups(), b.groups()) if x != y]
                drains.append(dict(consumer=kind, case=case, field=key, before=list(map(int, a.groups())),
                                   after=list(map(int, b.groups())), deltas=deltas, **stats(deltas)))
    return changes, drains


def learner_lines(output):
    return [line for line in output.splitlines() if line.startswith("[SIM-") and
            ("RESUMEN" in line or "MAE=" in line and ("n=30 " in line or "tras 30 " in line or "tras 100 gestos" in line))]


def learner_key(line):
    if "RESUMEN" in line:
        return line.split("  ", 1)[0]
    return line.split(" tauVerdadero=", 1)[0]


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", action="store_true")
    parser.add_argument("--reanchor", action="store_true")
    args = parser.parse_args()
    candidate = (ROOT / "android-native/app/build/auge-golden/expected.txt").read_text(encoding="utf-8")
    golden = verify_golden(candidate)
    if not args.candidate:
        assert (ROOT / GOLDEN).read_text(encoding="utf-8") == candidate, "Falta copiar el máster verificado al recurso"
    frozen = ["domain/auge/AugeRingCharacterizationTest.kt", "domain/auge/AugeRingDrainRealismTest.kt",
              "domain/auge/SessionDrainBoundsTest.kt", "domain/auge/RingsIntroSimulatorTest.kt",
              "screens/sessioneditor/SessionEditorAugeAxialGateTest.kt"]
    for path in frozen:
        assert (ROOT / (TEST + path)).read_text(encoding="utf-8") == original(TEST + path), f"Pin inesperado: {path}"
    changes, drains, reanchors = [], [], []
    for kind, (relative, count) in SNAPSHOTS.items():
        path = ROOT / (TEST + relative)
        before = literals(original(TEST + relative))
        package = "domain.auge" if kind == "domain" else "screens.sessioneditor"
        class_name = "SessionDrainRecipeCharacterizationTest" if kind == "domain" else "SessionEditorAugeCharacterizationTest"
        output = stdout(f"com.example.kpkn.{package}.{class_name}")
        captures = dict(re.findall(rf"\[2\.2b1-{kind}\]\[([^]]+)\]\n(.*?)\n\[/2\.2b1-{kind}\]", output, re.S))
        assert len(before) == count and set(captures) == set(before), (kind, list(captures))
        delta, metrics = verify_snapshots(kind, before, captures)
        changes.extend(delta)
        drains.extend(metrics)
        current = path.read_text(encoding="utf-8")
        if args.reanchor:
            assert literals(current) in (before, captures), f"Pines ajenos en {relative}"
            updated = LITERAL.sub(lambda m: f'            "{m[1]}" to """\n' +
                                  textwrap.indent(captures[m[1]], "                ") +
                                  '\n            """.trimIndent()', current)
            reanchors.append((path, updated))
        elif not args.candidate:
            assert literals(current) == captures, f"Falta re-anclar {relative}"
    baseline = json.loads((ROOT / "docs/rings-v4/reports/2.2b1-aprendiz-antes.json").read_text(encoding="utf-8"))
    after_learner = learner_lines(stdout("com.example.kpkn.domain.auge.AugeLearnerBiasCharacterizationTest"))
    old_by_key = {learner_key(line): line for line in baseline["lines"]}
    new_by_key = {learner_key(line): line for line in after_learner}
    assert old_by_key.keys() == new_by_key.keys(), "Faltan métricas del aprendiz"
    learner = [dict(metric=key, before=old_by_key[key], after=new_by_key[key]) for key in old_by_key]
    result = dict(base=BASE, regenerated_in="r4-2-2b1-4.log", golden=golden,
                  golden_statistics=stats([d for row in golden for d in row["deltas"]]),
                  golden_sha256=hashlib.sha256(candidate.encode("utf-8")).hexdigest(),
                  golden_categories={
                      "supersets": dict(covered=["A08"], changed=sorted({r["case"] for r in golden if r["case"] == "A08"})),
                      "singles": dict(covered=["A11"], changed=sorted({r["case"] for r in golden if r["case"] == "A11"})),
                      "cardio": dict(covered=[], changed=[]),
                      "missing_catalog": dict(covered=[], changed=[]),
                      "long_sessions": dict(covered=[], changed=[]),
                  },
                  frozen_tests=frozen, characterization_changes=changes, global_drains=drains,
                  drain_statistics=stats([d for row in drains for d in row["deltas"]]), learner=learner)
    learner_output = stdout("com.example.kpkn.domain.auge.AugeLearnerBiasCharacterizationTest")
    diagnostics = [line for line in learner_output.splitlines() if line.startswith("[SIM-BUCLE]") and
                   "escenario=" in line and any(key in line for key in
                   ["alejan[", "guarda de planta", "cociente implícito", "actualizaciones de"])]
    assert len(diagnostics) == len(baseline["diagnostics"]) == 40
    result["learner_diagnostics"] = [dict(before=a, after=b) for a, b in zip(baseline["diagnostics"], diagnostics)]
    result["characterization_field_statistics"] = [field_statistics(row) for row in changes]
    for path, updated in reanchors:
        path.write_text(updated, encoding="utf-8")
    destination = ROOT / "docs/rings-v4/reports/2.2b1-cambios.json"
    destination.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Máster: {len(golden)} campos esperables; {result['golden_statistics']}")
    print(f"Caracterizaciones: {len(changes)} líneas; drenajes globales: {result['drain_statistics']}")
    print(f"Aprendiz: {len(learner)} métricas; cambian {sum(row['before'] != row['after'] for row in learner)}")
    print("Verificación completa; evidencia en docs/rings-v4/reports/2.2b1-cambios.json")


if __name__ == "__main__":
    main()
