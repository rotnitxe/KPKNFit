"""Captura y contrasta los tres bloques numéricos de 2.2b2 sin invocar Gradle.

Ejecutar --stage base antes de editar producción. Tras cada bloque:
--stage canales|axial|volumen --reanchor, con AUGE_GOLDEN_UPDATE=1 ya ejecutado.
--verify comprueba el recurso, los snapshots y la evidencia de la última corrida.
Las medias excluyen deltas cero; los bits de Double no son magnitudes.
"""
import argparse
import hashlib
import json
import math
import re
import subprocess
import sys
import textwrap
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASE = "b8c2b91e46830cdba79f925eef8134c18920565f"
REPORT = ROOT / "docs/rings-v4/reports/2.2b2-evidencia.json"
TABLES = ROOT / "docs/rings-v4/reports/2.2b2-tablas.md"
GOLDEN = Path("android-native/app/src/test/resources/auge/golden/expected.txt")
RESULTS = ROOT / "android-native/app/build/test-results/testBaseDebugUnitTest"
TEST = Path("android-native/app/src/test/java/com/example/kpkn")
SNAPSHOTS = {
    "domain": TEST / "domain/auge/SessionDrainRecipeCharacterizationTest.kt",
    "editor": TEST / "screens/sessioneditor/SessionEditorAugeCharacterizationTest.kt",
}
LITERAL = re.compile(r'            "([^"]+)" to """\n(.*?)\n            """\.trimIndent\(\)', re.S)
NUMBER = re.compile(r"-?\d+(?:\.\d+)?")


def read(path):
    return path.read_text(encoding="utf-8").replace("\r\n", "\n")


def original(path):
    return subprocess.check_output(["git", "show", f"{BASE}:{path.as_posix()}"], cwd=ROOT).decode("utf-8").replace("\r\n", "\n")


def stdout(name):
    roots = [ET.parse(p).getroot() for p in sorted(RESULTS.glob(f"TEST-*{name}*.xml"))]
    assert roots, f"Falta la clase {name}"
    return "\n".join(n.text or "" for root in roots for n in root.iter("system-out"))


def literals(source):
    return {case: textwrap.dedent(body) for case, body in LITERAL.findall(source)}


def stats(deltas):
    values = [abs(v) for v in deltas if v != 0]
    return dict(numeros_distintos=len(values), maximo=max(values, default=0), media=sum(values) / max(1, len(values)))


def deltas(a, b):
    numbers = lambda s: list(map(float, NUMBER.findall(re.sub(r"\[-?\d+\]", "", s))))
    old, new = numbers(a), numbers(b)
    assert len(old) == len(new), (a, b)
    return [abs(x-y) for x, y in zip(old, new) if x != y]


def golden_changes(before, after):
    old, new = before.splitlines(), after.splitlines()
    assert len(old) == len(new), "Cambió la estructura del corpus"
    changes = []
    for a, b in zip(old, new):
        if a == b:
            continue
        x, y = a.split(" | "), b.split(" | ")
        assert len(x) == len(y) and x[:2] == y[:2], (a, b)
        for av, bv in zip(x[2:], y[2:]):
            if av != bv:
                field = av.split("=", 1)[0]
                assert field == bv.split("=", 1)[0]
                changes.append(dict(caso=x[0], instante=x[1], campo=field, antes=av, despues=bv, deltas=deltas(av, bv)))
    return changes


def by_channel(rows):
    grouped = defaultdict(list)
    fields = defaultdict(int)
    for row in rows:
        sizes = {key: len(value) for key, value in grouped.items()}
        a, b, field = row["antes"], row["despues"], row["campo"]
        if field == "rings":
            for channel, av, bv in zip(("Músculos", "Energía", "Columna"), a.split("=")[1].split(","), b.split("=")[1].split(",")):
                grouped[channel] += deltas(av, bv)
        elif field in {"drain", "drains", "tomorrow", "pre", "neural"}:
            pattern = r"(cns|spinal|muscular|neural)[=:](-?\d+)"
            old, new = re.findall(pattern, a), re.findall(pattern, b)
            assert [k for k, _ in old] == [k for k, _ in new]
            for (key, av), (_, bv) in zip(old, new):
                grouped[{"cns": "Energía", "neural": "Energía", "spinal": "Columna", "muscular": "Músculos"}[key]] += deltas(av, bv)
        elif field in {"pm", "v2global", "v2", "muscles"}:
            grouped["Músculos"] += row["deltas"]
        elif field == "energy":
            grouped["Energía"] += row["deltas"]
        elif field in {"spinal", "raw"}:
            grouped["Columna"] += row["deltas"]
        elif field == "dash":
            old, new = a.split(";"), b.split(";")
            grouped["Panel/mixto"] += deltas(old[0], new[0])
            for av, bv in zip(old[2:], new[2:]):
                channel = {"MUSCULAR": "Músculos", "SYSTEM": "Energía", "STRUCTURE": "Columna"}[av.split(":")[0]]
                grouped[channel] += deltas(av, bv)
        else:
            grouped["Panel/mixto"] += row["deltas"]
        for key, value in grouped.items():
            if len(value) > sizes.get(key, 0):
                fields[key] += 1
    return {channel: dict(campos_distintos=fields[channel], **stats(grouped[channel]))
            for channel in ("Músculos", "Energía", "Columna", "Panel/mixto")}


def summarize(rows):
    result = {"Total": dict(campos_distintos=len(rows), **stats([d for row in rows for d in row["deltas"]]))}
    for category in "ABCDE":
        selected = [r for r in rows if r["caso"].startswith(category)]
        result[category] = dict(campos_distintos=len(selected), **stats([d for r in selected for d in r["deltas"]]))
    result.update(by_channel(rows))
    return result


def independent(trace, per_channel):
    """Recurrencia Python independiente: ningún import del motor ni del verificador anterior."""
    sessions = defaultdict(list)
    for line in trace:
        cells = line.split("|")
        assert len(cells) == 14, line
        sessions[cells[1]].append(cells)
    assert set(sessions) == {"axial", "unilateral", "superserie"}
    result = {}
    for case, rows in sessions.items():
        acc, shared, counts, max_error = [0.0]*3, 0.0, defaultdict(float), 0.0
        for index, cells in enumerate(rows):
            _, _, sequence, pillar, side, count, gate, bonus, *values = cells
            assert int(sequence) == index
            counts[pillar] += 1.0 if side == "bilateral" else 0.5
            assert int(count) == math.floor(counts[pillar] + 0.5), (case, index, counts)
            raw, actual = list(map(float, values[:3])), list(map(float, values[3:]))
            assert all(math.isfinite(value) and 0.0 <= value <= cap for value, cap in zip(raw, (32.0, 32.0, 35.0)))
            assert all(math.isfinite(value) for value in actual)
            expected = [raw[i] * 0.85 / (1.0 + 0.65 * ((acc[i] if per_channel else shared) / 100.0)) for i in range(3)]
            expected[2] *= float(gate)
            if per_channel:
                acc = [x+y for x, y in zip(acc, expected)]
            else:
                shared += sum(expected) / 3.0
            expected[1] += expected[1] * (float(bonus)-1.0)
            error = max(abs(x-y) for x, y in zip(expected, actual))
            max_error = max(max_error, error)
            assert error < 1e-12, (case, index, expected, actual, error)
        result[case] = dict(series=len(rows), comparaciones=3*len(rows), error_maximo=max_error,
                            acumulados=acc if per_channel else shared)
    return result


def capture(stage):
    roots = [ET.parse(p).getroot() for p in sorted(RESULTS.glob("TEST-*.xml"))]
    assert len(roots) >= 15, len(roots)
    counts = {key: sum(int(r.get(key, 0)) for r in roots) for key in ("tests", "failures", "errors", "skipped")}
    counts["classes"] = len(roots)
    counts["seconds"] = round(sum(float(r.get("time", 0)) for r in roots), 1)
    out = stdout("SessionDrainRecipeTest")
    impact = [line for line in out.splitlines() if line.startswith("[2.2b2-impact]|")]
    trace = [line for line in out.splitlines() if line.startswith("[2.2b2-set]|")]
    assert len(impact) == 24, len(impact)
    snapshots = {}
    for kind, path in SNAPSHOTS.items():
        output = stdout(path.stem)
        pattern = rf"\[2\.2b1-{kind}\]\[([^\]]+)\]\n(.*?)\n\[/2\.2b1-{kind}\]"
        snapshots[kind] = dict(re.findall(pattern, output, re.S))
        assert len(snapshots[kind]) == (7 if kind == "domain" else 6)
    remapped = dict(re.findall(r"\[2\.2b2-remap\]\[([^\]]+)\]\n(.*?)\n\[/2\.2b2-remap\]", stdout("SessionDrainRecipeCharacterizationTest"), re.S))
    counterfactual = [dict(caso=case, **row) for case, value in remapped.items()
                      for row in changed_lines(snapshots["domain"][case].splitlines(), value.splitlines())]
    learner = [line for line in stdout("AugeLearnerBiasCharacterizationTest").splitlines()
               if line.startswith(("[SIM-INV-RATIO]", "[SIM-BUCLE] escenario=", "[SIM-BUCLE-LARGO] escenario=", "[SIM-F11]"))]
    pins = [line for line in stdout("AugeRingCharacterizationTest").splitlines() if line.startswith("[PIN]")]
    realism = [line for line in stdout("AugeRingDrainRealismTest").splitlines() if line.startswith("[PIN]")]
    invariants = [line for line in stdout("AugeSyntheticUserInvariantsTest").splitlines()
                  if line.startswith("[SIM-INV]") and any(key in line for key in ("violaciones=", "divergencia", "máximo", "discrepancias=", "comparaciones="))]
    parity = [re.sub(r" tiempo=\d+ ms", "", line) for line in stdout("AugeSyntheticUserInvariantsTest").splitlines()
              if line.startswith("[SIM-PARIDAD] RESUMEN")]
    assert len(parity) == 4
    assert int(re.search(r"máx\|Δ\|=(\d+)", parity[-1])[1]) <= 8, parity
    failures = [dict(prueba=case.get("classname", "")+"."+case.get("name", ""),
                     mensaje=node.get("message", "").splitlines()[0])
                for root in roots for case in root.iter("testcase") for node in case if node.tag in {"failure", "error"}]
    return dict(stage=stage, tests=counts, fallos=failures, snapshots=snapshots, impacto=impact, series=trace,
                python=independent(trace, stage != "base"), aprendiz=learner, pines=pins, realismo=realism,
                invariantes=invariants, paridad=parity, contrafactual_zombi=counterfactual)


def changed_lines(before, after):
    assert len(before) == len(after), "Cambió la estructura de la captura"
    return [dict(antes=a, despues=b) for a, b in zip(before, after) if a != b]


def characterization_statistics(row):
    before, after = row["antes"], row["despues"]
    if "MuscleRecoveryStatus(" in before:
        unit = {"recoveryScore": "puntos", "hoursToRecovery": "horas", "hoursSinceLastSession": "horas", "effectiveSets": "series"}
        a = dict(re.findall(r"(recoveryScore|hoursToRecovery|hoursSinceLastSession|effectiveSets)=(-?\d+)", before))
        b = dict(re.findall(r"(recoveryScore|hoursToRecovery|hoursSinceLastSession|effectiveSets)=(-?\d+)", after))
        groups = defaultdict(list)
        for key in a:
            groups[unit[key]] += deltas(a[key], b[key])
    else:
        unit = "unidades de estrés" if before.startswith("stress[") else "puntos"
        groups = {unit: deltas(before, after)}
    parts = [f"{unit}: {stats(values)['maximo']}/{stats(values)['media']:.6f}" for unit, values in groups.items() if values]
    return "; ".join(parts) or "0/0 (solo bits/etiquetas)"


def reanchor_pins(data):
    """Los nuevos pines proceden exclusivamente de la salida ya calculada por JUnit."""
    changes = []
    pending = []
    path = ROOT/TEST/"domain/auge/AugeRingCharacterizationTest.kt"
    source = read(path)
    values = {}
    for line in data["pines"]:
        hour = re.search(r"t=(\d+)h", line)
        if hour:
            values.update({f"{key}@{hour[1]}": value for key, value in re.findall(r"(energyHard|spinalHard|chestHard|energyLight|chestLight)=(\d+)", line)})
        else:
            values.update({f"energy{key}x": value for key, value in re.findall(r"([135])x=(\d+)", line)})
    def pin(match):
        label = match[1]
        assert label in values, label
        after = match[0][:-len(match[3])-1] + values[label] + ")"
        if after != match[0]:
            changes.append(dict(archivo=str(path.relative_to(ROOT)), antes=match[0], despues=after))
        return after
    updated = re.sub(r'pin\("([^"]+)", (.*), (\d+)\)', pin, source)
    if updated != source:
        pending.append((path, updated))

    path = ROOT/TEST/"domain/auge/AugeLearnerBiasCharacterizationTest.kt"
    source = read(path)
    summaries = []
    for line in data["aprendiz"]:
        match = re.search(r'^\[SIM-BUCLE\] escenario="([^"]+)" parámetro=(\S+) n=30 .*MAE=(-?[\d.]+) h .*sesgo=(-?[\d.]+) h', line)
        if match:
            summaries.append(match.groups())
    assert len(summaries) == 12, summaries
    start, end = source.index("        val expected: Map<String, Map<String, Measured>>"), source.index("        expected.forEach")
    measured = iter(summaries)
    def pair(match):
        _, param, mae, bias = next(measured)
        assert param == {"muscleParam": "pectorales", "cns": "cns", "spinalParam": "spinal"}[match[1]]
        return f"{match[1]} to Measured({mae}, {bias})"
    updated = source[:start] + re.sub(r"(muscleParam|cns|spinalParam) to Measured\([-\d., ]+\)", pair, source[start:end]) + source[end:]
    assert next(measured, None) is None
    # Los diagnósticos conservan sus márgenes; se actualiza solo el centro medido.
    away = [re.search(r"alejan\[todos los parámetros\]: ([\d.]+)", line)[1]
            for line in data["aprendiz"] if "alejan[todos los parámetros]" in line]
    assert len(away) == 4
    updated = re.sub(r"listOf\([\d., ]+\)\.forEachIndexed \{ i, want ->", "listOf(" + ", ".join(away) + ").forEachIndexed { i, want ->", updated)
    guards = [line for line in data["aprendiz"] if "guarda de planta" in line]
    assert len(guards) == 4
    for variable, line in (("plantaSigma4", guards[0]), ("plantaSigma7", guards[3])):
        measurements = re.findall(r"(MUSCLE|ENERGY|SPINAL): planta \d+ de \d+ \(([\d.]+) %\)", line)
        assert len(measurements) == 3
        replacement = "val " + variable + " = mapOf(" + ", ".join(f"SimChannel.{key} to {value}" for key, value in measurements) + ")"
        updated = re.sub(r"val " + variable + r" = mapOf\([^\n]+\)", replacement, updated)
    # Seis pines de la corrida larga: misma semilla, usuarios y número de observaciones.
    scenarios = list(dict.fromkeys(row[0] for row in summaries))
    long_rows = {}
    for line in data["aprendiz"]:
        match = re.search(r'^\[SIM-BUCLE-LARGO\] escenario="([^"]+)" parámetro=(\S+) tras (\d+) (observaciones propias|gestos) .*MAE=(-?[\d.]+) h sesgo=(-?[\d.]+) h', line)
        if match:
            scenario, param, count, unit, mae, bias = match.groups()
            long_rows[(scenario, param, count, unit)] = (mae, bias)
    targets = {
        "pectorales obs30 MAE": (0, "pectorales", "30", "observaciones propias", 0),
        "pectorales obs30 sesgo": (0, "pectorales", "30", "observaciones propias", 1),
        "columna obs30 sesgo": (0, "spinal", "30", "observaciones propias", 1),
        "columna g100 sesgo": (0, "spinal", "100", "gestos", 1),
        "pectorales ×0,75 obs30 sesgo": (2, "pectorales", "30", "observaciones propias", 1),
        "energía ×1,25 obs30 sesgo": (1, "cns", "30", "observaciones propias", 1),
    }
    for label, (scenario, param, count, unit, metric) in targets.items():
        value = long_rows[(scenarios[scenario], param, count, unit)][metric]
        pattern = r'(assertNear\("' + re.escape(label) + r'", .*?\.(?:mae|bias), )-?[\d.]+'
        updated, n = re.subn(pattern, lambda m: m[1]+value, updated)
        assert n == 1, label
    changes += [dict(archivo=str(path.relative_to(ROOT)), **row) for row in changed_lines(source.splitlines(), updated.splitlines())]
    if updated != source:
        pending.append((path, updated))
    for path, updated in pending:
        path.write_text(updated, encoding="utf-8")
    data["reanclajes"] = changes


def markdown(evidence):
    lines = ["# Tablas completas de 2.2b2", "", f"Base: `{BASE}`. Generado por `check_2_2b2.py` desde JUnit y el máster.", "",
             "Máximo/media: deltas absolutos sobre números distintos, excluyendo ceros y bits de Double. El máster incluye porcentajes, horas y confianza; los campos completos conservan su unidad. Un campo multicanal cuenta en cada canal que cambia; panel/mixto recoge el global del dashboard.", ""]
    escape = lambda value: str(value).replace("|", "&#124;").replace("\n", "<br>")
    def table(headers, rows):
        lines.append("| " + " | ".join(headers) + " |")
        lines.append("|" + "|".join("---" for _ in headers) + "|")
        lines.extend("| " + " | ".join(escape(v) for v in row) + " |" for row in rows)
        lines.append("")
    if "total" in evidence:
        lines += ["## Comparación acumulada: base → resultado actual", ""]
        table(["Agrupación", "Campos distintos", "Números distintos", "Máximo", "Media"],
              [[key, value["campos_distintos"], value["numeros_distintos"], value["maximo"], f"{value['media']:.6f}"]
               for key, value in evidence["total"]["resumen"].items()])
        table(["Caso", "Instante", "Campo", "Antes", "Después"],
              [[r["caso"], r["instante"], r["campo"], r["antes"], r["despues"]] for r in evidence["total"]["golden"]])
    for stage, data in evidence["stages"].items():
        lines += [f"## {stage}", "", f"Pruebas: `{data['tests']}`. Recálculo independiente: `{data['python']}`.", ""]
        if stage == "base":
            continue
        lines += ["### Resumen del máster", ""]
        table(["Agrupación", "Campos distintos", "Números distintos", "Máximo", "Media"],
              [[key, value.get("campos_distintos", "—"), value["numeros_distintos"], value["maximo"], f"{value['media']:.6f}"]
               for key, value in data["resumen"].items()])
        lines += ["### Cada campo cambiado del máster", ""]
        table(["Caso", "Instante", "Campo", "Antes", "Después", "Máximo/media"],
              [[r["caso"], r["instante"], r["campo"], r["antes"], r["despues"],
                f"{stats(r['deltas'])['maximo']}/{stats(r['deltas'])['media']:.6f}"] for r in data["golden"]])
        lines += ["### Caracterizaciones: todas las líneas distintas", ""]
        table(["Consumidor/caso", "Antes", "Después", "Máximo/media"],
              [[r["caso"], r["antes"], r["despues"], characterization_statistics(r)]
               for r in data["caracterizaciones"]])
        for key, title in (("pines", "Pines F0"), ("realismo", "Realismo muscular"), ("aprendiz", "Aprendiz: inversión, 30 gestos, observaciones propias y diagnósticos")):
            lines += [f"### {title}", ""]
            table(["Antes", "Después"], [[r["antes"], r["despues"]] for r in data["cambios_"+key]])
        lines += ["### Literales re-anclados desde la medición", ""]
        table(["Archivo", "Antes", "Después"], [[r["archivo"], r["antes"], r["despues"]] for r in data.get("reanclajes", [])])
        lines += ["### Contrafactual del caché zombi, sin aplicar el remapeo", "", "Se conserva el estado y las tres variantes hasta 3.1. Esta tabla compara la lectura actual con el remapeo hipotético, no dos cambios aplicados.", ""]
        table(["Caso", "Lectura conservada", "Remapeo hipotético"], [[r["caso"], r["antes"], r["despues"]] for r in data.get("contrafactual_zombi", [])])
    base, final = evidence["stages"]["base"], next(reversed(evidence["stages"].values()))
    lines += ["## Impacto acumulado para el usuario", "", "M/E/C = Músculos/Energía/Columna; relojes UTC. Pecho usa A02 (empuje superior), pierna A01, cuerpo completo/un día C01, cinco días C04 (pierna/empuje/tirón alternados); cardio es remo 120 min, RPE 7. Todos con Settings por defecto y sin bienestar manual.", ""]
    table(["Sesión", "Horas", "Antes M/E/C", "Después M/E/C"],
          [[a.split("|")[1], a.split("|")[2], "/".join(a.split("|")[3:]), "/".join(b.split("|")[3:])]
           for a, b in zip(base["impacto"], final["impacto"])])
    lines += ["## F0 y realismo: comparación completa con la base", ""]
    table(["Antes", "Después"], [[a, b] for key in ("pines", "realismo") for a, b in zip(base[key], final[key])])
    lines += ["## Aprendiz: línea base completa que debe batir 3.2", "", "Horas de MAE y sesgo, cocientes y porcentajes identificados en cada fila; mismos usuarios, semillas y observaciones. Incluye valores que permanecen iguales.", ""]
    table(["Antes", "Después"], [[a, b] for a, b in zip(base["aprendiz"], final["aprendiz"])])
    lines += ["## Monotonía y decisiones de alcance", "",
              "Para x′ = x + a/(1 + 0,0065x), las derivadas son ∂x′/∂x = 1 − 0,0065a/(1 + 0,0065x)² y ∂x′/∂a = 1/(1 + 0,0065x). Ambas son positivas con x≥0 y 0,0065a<1. Músculos/Energía tienen a≤0,85·32=27,2 (producto ≤0,1768); Columna conserva su tope real 35: a≤29,75 (≤0,193375). La puerta axial [0,1] no aumenta ese límite. La inducción sobre las series elimina la realimentación cruzada de F-60. Los bonos históricos siguen fuera de la recurrencia y preceden a los topes; no se recalibra ninguna constante.", "",
              "SessionDrainBoundsTest conserva 32/32/35 y sus cinco pruebas. No se cambian Room, JSON, capacidad, estrés local V2, progresión muscular/neural, SPF, fallback ni el algoritmo de aprendizaje. La unificación del conteo solo modifica el argumento accumulatedSets por pilar. El planificador no materializa side: el editor adopta el mismo contador, sin inventar lados ni cambiar su volumen de UI.", "",
              "El contrafactual zombi gana diferencias de redondeo tras F-60; se re-ancla su prueba sin aplicar remapeos. La tabla de impacto usa las sesiones existentes C01/C04, con cargas distintas: no aísla causalmente la frecuencia (F-32 sigue para 2.6).", ""]
    if "volumen" in evidence["stages"]:
        lines += ["## Opciones restantes y retirada de opciones constantes", ""]
        table(["Decisión", "Antes", "Después", "Motivo"], [
            ["axialGatePosition", "Antes / después / no aplicada", "Sin opción; puerta antes del acumulado", "Columna acumula lo que aplica"],
            ["volumeCounting", "Mitades o enteros por consumidor", "Sin opción; mitades unilaterales redondeadas", "Una progresión por pilar en A–F"],
            ["cardioHandling", "Gancho o fuerza legacy", "Igual", "C conserva el respaldo de estrés del aprendiz sin V2; los otros consumidores mantienen reparto y conservación de cardio"],
            ["softCapUnits", "Canal / estrés / log / ninguno", "Igual", "A/P/F: porcentaje; B: unidades de estrés; C: estrés sin tope; D/E: porcentaje por log"],
            ["muscleMultiplierSource", "Remapeado / directo / ninguno", "Igual", "Estado zombi reservado a 3.1; no se modifica"],
        ])
        if "verificacion_final" in evidence:
            lines += ["Limpieza final de comentarios/opciones constantes: 0 campos, máximo 0, media 0. La corrida 5 comprueba sin regenerar que máster, caracterizaciones, pines, aprendiz y series siguen exactos tras esa limpieza.", ""]
    lines += ["## Archivos y firmas", "",
              "Ninguna firma pública nueva o cambiada. Todo tipo nuevo de la receta es interno: `ChannelDrainAccumulation(muscular, cns, spinal)`, `AdjustedSetDrain.accumulatedDrain: ChannelDrainAccumulation`, `SessionDrainRecipe.adjustSet(SetDrain, ChannelDrainAccumulation, Double)` y `accumulateCardio(Double, Double, Double)`. `RecipeOptions` conserva únicamente las dos variantes legítimas y el campo zombi de 3.1.", ""]
    paths = subprocess.check_output(["git", "diff", "--name-only", BASE, "--"], cwd=ROOT).decode("utf-8").splitlines()
    paths = sorted(set(paths + ["docs/rings-v4/reports/2.2b2.md"]))
    lines += [f"- [{Path(path).name}](../../../{path})" for path in paths]
    lines += [""]
    lines += ["## Ejecuciones y reproducción", "", "Base antes de editar producción; tres bloques numéricos con regeneración; verificación final sin regeneración. Los fallos de captura son snapshots anteriores, detallados en el JSON.", ""]
    rows = []
    for n, (stage, data) in enumerate(evidence["stages"].items(), 1):
        t = data["tests"]
        rows.append([f"C:\\kw\\logs\\r4-2-2b2-{n}.log", stage, t["classes"], t["tests"], t["failures"], t["errors"], t["skipped"]])
    if "verificacion_final" in evidence:
        t = evidence["verificacion_final"]
        rows.append(["C:\\kw\\logs\\r4-2-2b2-5.log", "Verificación final", t["classes"], t["tests"], t["failures"], t["errors"], t["skipped"]])
    table(["Log", "Propósito", "Clases", "Pruebas", "Fallos", "Errores", "Omitidas"], rows)
    classes = [
        "domain.auge.SessionDrainRecipeCharacterizationTest", "screens.sessioneditor.SessionEditorAugeCharacterizationTest",
        "domain.auge.SessionDrainRecipeTest", "domain.auge.AugeGoldenMasterTest", "domain.auge.AugeRingCharacterizationTest",
        "domain.auge.AugeRingDrainRealismTest", "domain.auge.SessionDrainBoundsTest", "domain.auge.AugeEditorCompletedParityTest",
        "domain.auge.AugeLearnerBiasCharacterizationTest", "domain.auge.AugeSyntheticUserInvariantsTest", "domain.auge.RingsIntroSimulatorTest",
        "screens.sessioneditor.SessionEditorAugeAxialGateTest", "domain.auge.AugePostSessionPreviewImmediateTest",
        "domain.auge.AugeUnitsConsistencyTest", "domain.auge.BatteryRoundingTest",
    ]
    filters = " ".join("--tests 'com.example.kpkn."+name+"'" for name in classes)
    lines += ["Desde C:\\kw\\v3c; `<n>`=1 base, 2 canales, 3 axial, 4 volumen, 5 verificación. En 2/3/4 se antepone `AUGE_GOLDEN_UPDATE=1` a `bash` dentro de `-c`.", "", "```powershell",
              "& 'C:\\Program Files\\Git\\bin\\bash.exe' -c \"bash /c/kw/tools/gradle_solo.sh /c/kw/v3c/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest "+filters+" > /c/kw/logs/r4-2-2b2-<n>.log 2>&1\"",
              "python -I docs/rings-v4/tools/count_tests.py --clases",
              "python -I docs/rings-v4/tools/check_2_2b2.py --stage base",
              "python -I docs/rings-v4/tools/check_2_2b2.py --stage canales --reanchor",
              "python -I docs/rings-v4/tools/check_2_2b2.py --stage axial --reanchor",
              "python -I docs/rings-v4/tools/check_2_2b2.py --stage volumen --reanchor",
              "python -I docs/rings-v4/tools/check_2_2b2.py --verify", "```", ""]
    TABLES.write_text("\n".join(lines), encoding="utf-8")


def save(evidence):
    rows = golden_changes(original(GOLDEN), read(ROOT/GOLDEN))
    evidence["total"] = dict(golden=rows, resumen=summarize(rows))
    REPORT.write_text(json.dumps(evidence, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    markdown(evidence)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", choices=("base", "canales", "axial", "volumen"))
    parser.add_argument("--reanchor", action="store_true")
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--refresh", action="store_true", help="Actualiza solo diagnósticos adicionales desde los mismos XML")
    args = parser.parse_args()
    evidence = json.loads(read(REPORT)) if REPORT.exists() else dict(base=BASE, stages={})
    for recorded in evidence["stages"].values():
        if "golden" in recorded:
            recorded["resumen"].update(by_channel(recorded["golden"]))
    if args.refresh:
        latest = next(reversed(evidence["stages"].values()))
        actual = capture(latest["stage"])
        assert latest["snapshots"] == actual["snapshots"]
        latest["contrafactual_zombi"] = actual["contrafactual_zombi"]
        if "golden" in latest:
            latest["resumen"].update(by_channel(latest["golden"]))
        save(evidence)
        print("Diagnósticos actualizados; se conservan las comparaciones antes→después.")
        return
    if args.verify:
        latest = next(reversed(evidence["stages"].values()))
        actual = capture(latest["stage"])
        assert actual["tests"]["failures"] == actual["tests"]["errors"] == 0
        for kind, path in SNAPSHOTS.items():
            assert literals(read(ROOT/path)) == actual["snapshots"][kind]
        assert hashlib.sha256(read(ROOT/GOLDEN).encode()).hexdigest() == latest["golden_sha256"]
        for key in ("impacto", "series", "aprendiz", "pines", "realismo", "paridad"):
            assert latest[key] == actual[key], key
        evidence["verificacion_final"] = actual["tests"]
        save(evidence)
        print("Verificación final: recurso, capturas, aprendiz, pines y recálculo Python coinciden; 0 fallos.")
        print(json.dumps(actual["python"], ensure_ascii=False))
        return
    assert args.stage
    data = capture(args.stage)
    if args.stage == "base":
        assert data["tests"]["failures"] == data["tests"]["errors"] == 0
        assert read(ROOT/GOLDEN) == original(GOLDEN)
        candidate = read(ROOT/GOLDEN)
        assert not args.reanchor
    else:
        assert args.reanchor
        previous = next(reversed(evidence["stages"].values()))
        candidate = read(ROOT/"android-native/app/build/auge-golden/expected.txt")
        data["golden"] = golden_changes(read(ROOT/GOLDEN), candidate)
        data["resumen"] = summarize(data["golden"])
        assert all(row["campo"] != "v2" for row in data["golden"]), "El estrés local V2 queda fuera de 2.2b2"
        if args.stage == "axial":
            assert data["resumen"]["Músculos"]["numeros_distintos"] == 0, "La puerta axial contaminó Músculos"
            assert data["resumen"]["Energía"]["numeros_distintos"] == 0, "La puerta axial contaminó Energía"
        if args.stage == "volumen":
            assert all(row["caso"] == "A09" for row in data["golden"]), "El conteo movió un caso bilateral"
        data["caracterizaciones"] = []
        for kind, path in SNAPSHOTS.items():
            current = literals(read(ROOT/path))
            assert current == previous["snapshots"][kind], f"La base de {kind} ya fue alterada"
            for case, before in current.items():
                data["caracterizaciones"] += [dict(caso=f"{kind}/{case}", **row) for row in changed_lines(before.splitlines(), data["snapshots"][kind][case].splitlines())]
        for key in ("pines", "realismo", "aprendiz"):
            data["cambios_"+key] = changed_lines(previous[key], data[key])
        reanchor_pins(data)
        for kind, path in SNAPSHOTS.items():
            updated = LITERAL.sub(lambda m: f'            "{m[1]}" to """\n' + textwrap.indent(data["snapshots"][kind][m[1]], "                ") + '\n            """.trimIndent()', read(ROOT/path))
            (ROOT/path).write_text(updated, encoding="utf-8")
        (ROOT/GOLDEN).write_text(candidate, encoding="utf-8")
    data["golden_sha256"] = hashlib.sha256(candidate.encode()).hexdigest()
    evidence["stages"][args.stage] = data
    save(evidence)
    print(json.dumps(dict(stage=args.stage, tests=data["tests"], resumen=data.get("resumen"), python=data["python"]), ensure_ascii=False))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
