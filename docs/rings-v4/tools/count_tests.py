"""Cuenta las pruebas de la ÚLTIMA ejecución de Gradle (XML de JUnit) y lista los fallos.

Uso, desde la raíz del worktree (solo lectura):
    python -I docs/rings-v4/tools/count_tests.py            # totales + fallos
    python -I docs/rings-v4/tools/count_tests.py --clases   # además, una línea por clase

Lee android-native/app/build/test-results/testBaseDebugUnitTest/*.xml. Gradle borra esos XML al
empezar cada ejecución de la tarea de pruebas, así que los totales son los de la última invocación.
Salida con código 0 aunque haya fallos (el que decide es quien lee el informe).
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

RUTA = os.path.join("android-native", "app", "build", "test-results", "testBaseDebugUnitTest", "*.xml")


def main() -> int:
    # La consola de Windows usa cp1252 y un fallo con «τ» o «→» tiraba UnicodeEncodeError.
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass
    archivos = sorted(glob.glob(RUTA))
    if not archivos:
        print(f"SIN RESULTADOS: no hay XML en {RUTA}. ¿Corrió la tarea de pruebas en este worktree?")
        return 1
    total = fallos = errores = omitidas = 0
    tiempo = 0.0
    clases = []
    detalle = []
    for ruta in archivos:
        raiz = ET.parse(ruta).getroot()
        t = int(raiz.get("tests", 0))
        f = int(raiz.get("failures", 0))
        e = int(raiz.get("errors", 0))
        s = int(raiz.get("skipped", 0))
        seg = float(raiz.get("time", 0.0))
        total += t
        fallos += f
        errores += e
        omitidas += s
        tiempo += seg
        clases.append((raiz.get("name", os.path.basename(ruta)), t, f + e, s, seg))
        for caso in raiz.iter("testcase"):
            for tag in ("failure", "error"):
                nodo = caso.find(tag)
                if nodo is not None:
                    msg = (nodo.get("message") or (nodo.text or "")).strip().splitlines()
                    detalle.append((caso.get("classname", "?") + "." + caso.get("name", "?"), msg[0][:200] if msg else ""))
    print(f"clases={len(clases)} pruebas={total} fallos={fallos} errores={errores} omitidas={omitidas} tiempo_suma={tiempo:.1f}s")
    if "--clases" in sys.argv:
        for nombre, t, mal, s, seg in clases:
            print(f"  {nombre}: pruebas={t} mal={mal} omitidas={s} {seg:.1f}s")
    for nombre, msg in detalle:
        print(f"FALLO {nombre}: {msg}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
