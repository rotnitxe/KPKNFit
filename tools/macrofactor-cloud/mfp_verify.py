"""Verificación mecánica de un paquete de la ola MFP (la corre el orquestador; también la pueden correr los agentes).

Uso:
  python C:\\kw\\tools\\mfp_verify.py <worktree> [--since <ref>] [--baseline <archivo>] [--xml <carpeta>] [--strict]
  python C:\\kw\\tools\\mfp_verify.py <worktree> --make-baseline <salida.txt>     (vuelca los fallos del último test a un archivo)

Qué hace (solo lectura):
  1. Cuenta los XML de JUnit (suites, tests, fallos, errores, omitidos) del último `testBaseDebugUnitTest` del worktree.
  2. Con --baseline: separa los fallos HEREDADOS (están en el archivo, que sale de una corrida de master) de los NUEVOS.
  3. Con --since <ref>: sobre las líneas AÑADIDAS desde <ref> busca los coeficientes prohibidos de COMUN.md (clean-room),
     lista los archivos de producción EXISTENTES que se tocaron (con +/- líneas) y marca los de la zona de choque con screens-v2,
     avisa si se tocó el esquema de Room (entidades, versión, migraciones, schemas/: congelado) y comprueba que cada commit
     lleve «Co-Authored-By».
Sale con código 1 si hay fallos nuevos (o, con --strict, si hay constantes prohibidas o commits sin pie).
"""
import argparse
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

FORBIDDEN = [
    r"0[,.]965", r"0[,.]975", r"0[,.]582", r"0[,.]327", r"0[,.]5619", r"1[,.]718", r"\b48[,.]1\b", r"0[,.]783", r"\b598\b",
    r"0[,.]0008", r"0[,.]0025", r"\b4400\b", r"0[,.]33\b", r"0[,.]66\b", r"0[,.]85\b", r"\b750\b",
]
CONFLICT_ZONE = (
    "screens/workout/", "screens/sessioneditor/", "domain/calculations/Calculations.kt", "domain/workout/LoadSuggestionEngine.kt",
    "domain/training/NativeWorkoutProgressionRuntime.kt",
)
# Esquema Room congelado desde el 21-oct (la 30 y la 31 están publicadas en el APK de pruebas con datos reales): cualquier cambio
# de entidades, versión, migraciones o JSON de esquema exige permiso expreso (la próxima versión libre es la 32).
ROOM_SCHEMA_RX = re.compile(r"data/db/(Entities|KpknDatabase)\.kt|app/schemas/|data/db/\w*Migration\w*\.kt")


def git(wt, *args):
    r = subprocess.run(["git", "-C", wt, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    return r.stdout


def collect_xml(folder):
    suites = tests = fails = errs = skips = 0
    failing = []
    for f in sorted(glob.glob(os.path.join(folder, "TEST-*.xml"))):
        try:
            root = ET.parse(f).getroot()
        except ET.ParseError:
            print("  XML ilegible:", f)
            continue
        suites += 1
        tests += int(root.get("tests", 0))
        fails += int(root.get("failures", 0))
        errs += int(root.get("errors", 0))
        skips += int(root.get("skipped", 0))
        for case in root.findall("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                failing.append(f"{case.get('classname')}.{case.get('name')}")
    return suites, tests, fails, errs, skips, failing


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    p = argparse.ArgumentParser()
    p.add_argument("worktree")
    p.add_argument("--since")
    p.add_argument("--baseline")
    p.add_argument("--xml")
    p.add_argument("--make-baseline")
    p.add_argument("--strict", action="store_true")
    a = p.parse_args()
    wt = a.worktree.rstrip("/\\")
    xml = a.xml or os.path.join(wt, "android-native", "app", "build", "test-results", "testBaseDebugUnitTest")
    bad = False

    suites, tests, fails, errs, skips, failing = collect_xml(xml)
    print(f"[tests] {xml}\n        suites={suites} tests={tests} fallos={fails} errores={errs} omitidos={skips}")
    if a.make_baseline:
        with open(a.make_baseline, "w", encoding="utf-8", newline="\n") as fh:
            fh.write("\n".join(sorted(set(failing))) + "\n")
        print(f"        línea base escrita: {len(set(failing))} fallos únicos -> {a.make_baseline}")
        return 0
    if failing:
        base = set()
        if a.baseline and os.path.exists(a.baseline):
            base = {l.strip() for l in open(a.baseline, encoding="utf-8") if l.strip() and not l.startswith("#")}
        def is_base(fq):
            if fq in base:
                return True
            simple = fq.split(".")[-2] + "." + fq.split(".")[-1] if fq.count(".") >= 1 else fq
            return simple in base
        new = sorted(f for f in set(failing) if not is_base(f))
        inherited = sorted(f for f in set(failing) if is_base(f))
        print(f"        fallos heredados (ya rojos en master): {len(inherited)} · NUEVOS: {len(new)}")
        for n in new[:40]:
            print("          NUEVO", n)
        if len(new) > 40:
            print(f"          … y {len(new) - 40} más")
        if new:
            bad = True
    elif tests:
        print("        sin fallos")

    if a.since:
        print(f"[diff desde {a.since}]")
        added = git(wt, "diff", "-U0", f"{a.since}", "--", "android-native/app/src").splitlines()
        cur = None
        hits = []
        for line in added:
            if line.startswith("+++ b/"):
                cur = line[6:]
            elif line.startswith("+") and not line.startswith("+++"):
                for rx in FORBIDDEN:
                    if re.search(rx, line):
                        hits.append((cur, rx, line.strip()[:110]))
        if hits:
            print(f"  CONSTANTES PROHIBIDAS ({len(hits)}):")
            for h in hits[:30]:
                print("   ", h)
            if a.strict:
                bad = True
        else:
            print("  constantes prohibidas: ninguna")
        touched = git(wt, "diff", "--name-only", f"{a.since}", "--", "android-native/app/src/main", "android-native/app/schemas").splitlines()
        schema = [p for p in touched if ROOM_SCHEMA_RX.search(p)]
        if schema:
            print(f"  ESQUEMA ROOM TOCADO ({len(schema)}): congelado desde el 21-oct; solo con permiso expreso")
            for p in schema:
                print("   ", p)
            if a.strict:
                bad = True
        else:
            print("  esquema Room: intacto")
        stat = git(wt, "diff", "--numstat", "--diff-filter=M", f"{a.since}", "--", "android-native/app/src/main").splitlines()
        print(f"  archivos de producción EXISTENTES tocados: {len(stat)}")
        for line in stat:
            add, dele, path = line.split("\t")
            short = path.replace("android-native/app/src/main/java/com/example/kpkn/", "")
            zone = " «choque screens-v2»" if any(z in path for z in CONFLICT_ZONE) else ""
            big = " «>30 líneas»" if add.isdigit() and dele.isdigit() and int(add) + int(dele) > 30 else ""
            print(f"    +{add:>4} -{dele:>4}  {short}{zone}{big}")
        log = git(wt, "log", "--format=%h%x09%s%x09%b%x1e", f"{a.since}..HEAD").split("\x1e")
        nofoot = []
        for entry in log:
            entry = entry.strip()
            if not entry:
                continue
            head = entry.split("\t")[0]
            if "Co-Authored-By:" not in entry:
                nofoot.append(head)
        print(f"  commits sin «Co-Authored-By»: {len(nofoot)} {nofoot}")
        if nofoot and a.strict:
            bad = True
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
