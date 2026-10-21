"""Verifica el máster del bloque 1 y conserva la evidencia del remapeo provisional.

Desde la raíz: python -I docs/rings-v4/tools/check_2_2b1.py
Lee los XML de la corrida diagnóstica; no re-ancla pruebas ni copia el máster.
"""
import json
import re
import subprocess
import sys
import textwrap
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
BASE = "ffd6a1648"
GOLDEN = "android-native/app/src/test/resources/auge/golden/expected.txt"
CHARACTERIZATION = (
    "android-native/app/src/test/java/com/example/kpkn/domain/auge/"
    "SessionDrainRecipeCharacterizationTest.kt"
)


def original(path):
    return subprocess.check_output(
        ["git", "show", f"{BASE}:{path}"], cwd=ROOT
    ).decode("utf-8").replace("\r\n", "\n")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    generated = ROOT / "android-native/app/build/auge-golden/expected.txt"
    golden_before = original(GOLDEN)
    golden_after = generated.read_text(encoding="utf-8")
    assert golden_before == golden_after, "Cambios inesperados en el máster: detener"
    assert (ROOT / GOLDEN).read_text(encoding="utf-8") == golden_before
    expected = {
        case: textwrap.dedent(body)
        for case, body in re.findall(
            r'            "([^"]+)" to """\n(.*?)\n            """\.trimIndent\(\)',
            original(CHARACTERIZATION), re.S,
        )
    }
    assert len(expected) == 7
    current_expected = {
        case: textwrap.dedent(body)
        for case, body in re.findall(
            r'            "([^"]+)" to """\n(.*?)\n            """\.trimIndent\(\)',
            (ROOT / CHARACTERIZATION).read_text(encoding="utf-8"), re.S,
        )
    }
    assert current_expected == expected, "Se re-anclaron caracterizaciones del bloque 1"
    frozen_tests = [
        "domain/auge/AugeRingCharacterizationTest.kt",
        "domain/auge/AugeRingDrainRealismTest.kt",
        "domain/auge/SessionDrainBoundsTest.kt",
        "domain/auge/AugeEditorCompletedParityTest.kt",
        "domain/auge/AugeLearnerBiasCharacterizationTest.kt",
        "screens/sessioneditor/SessionEditorAugeCharacterizationTest.kt",
    ]
    for test in frozen_tests:
        path = "android-native/app/src/test/java/com/example/kpkn/" + test
        assert (ROOT / path).read_text(encoding="utf-8") == original(path), f"Prueba re-anclada: {test}"
    xml = ROOT / (
        "android-native/app/build/test-results/testBaseDebugUnitTest/"
        "TEST-com.example.kpkn.domain.auge.SessionDrainRecipeCharacterizationTest.xml"
    )
    suite = ET.parse(xml).getroot()
    output = "\n".join(node.text or "" for node in suite.iter("system-out"))
    captures = re.findall(r"\[2\.2b1\]\[([^]]+)\]\n(.*?)\n\[/2\.2b1\]", output, re.S)
    assert captures, "Faltan capturas de la corrida diagnóstica"
    changes = []
    point_deltas = []
    hour_deltas = []
    for case, actual in captures:
        before_lines = expected[case].splitlines()
        after_lines = actual.splitlines()
        assert len(before_lines) == len(after_lines)
        for before, after in zip(before_lines, after_lines):
            if before == after:
                continue
            # Solo B/D/E cambian al remapear: A/P/C ya usaban pilares.
            assert before.startswith("history[") and after.startswith("history["), (case, before, after)
            key, old_value = before.split("=", 1)
            new_key, new_value = after.split("=", 1)
            assert key == new_key
            old_numbers = [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?", old_value)]
            new_numbers = [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?", new_value)]
            assert len(old_numbers) == len(new_numbers)
            deltas = [abs(a - b) for a, b in zip(old_numbers, new_numbers) if a != b]
            if "][" in key:
                # Batería, horas de recuperación, horas desde la sesión y series.
                assert len(old_numbers) == 4
                assert old_numbers[2:] == new_numbers[2:]
                point_deltas.extend([abs(old_numbers[0] - new_numbers[0])] if old_numbers[0] != new_numbers[0] else [])
                hour_deltas.extend([abs(old_numbers[1] - new_numbers[1])] if old_numbers[1] != new_numbers[1] else [])
            else:
                point_deltas.extend(deltas)
            changes.append(dict(case=case, field=key, before=old_value, after=new_value, absolute_deltas=deltas))
    deltas = [delta for row in changes for delta in row["absolute_deltas"]]
    assert deltas, "La hipótesis del impedimento no produjo diferencias"
    result = dict(
        base=BASE,
        diagnostic_only=True,
        golden_changed_lines=0,
        golden_maximum_delta=0,
        golden_mean_delta=0,
        cases_changed=len(captures),
        fields_changed=len(changes),
        maximum_delta=max(deltas),
        mean_delta=sum(deltas) / len(deltas),
        delta_definition="Campos numéricos distintos de las caracterizaciones; incluye puntos y horas.",
        points=dict(count=len(point_deltas), maximum=max(point_deltas, default=0), mean=sum(point_deltas) / max(1, len(point_deltas))),
        recovery_hours=dict(count=len(hour_deltas), maximum=max(hour_deltas, default=0), mean=sum(hour_deltas) / max(1, len(hour_deltas))),
        changes=changes,
    )
    destination = ROOT / "docs/rings-v4/reports/2.2b1-evidencia.json"
    destination.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"máster: diferencias=0; caracterizaciones: casos={len(captures)} campos={len(changes)}")
    print(f"puntos: máximo={result['points']['maximum']:g} media={result['points']['mean']:.6f}; horas: máximo={result['recovery_hours']['maximum']:g} media={result['recovery_hours']['mean']:.6f}")


if __name__ == "__main__":
    main()
