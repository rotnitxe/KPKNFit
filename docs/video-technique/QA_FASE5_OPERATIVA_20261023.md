# Operativa de QA físico VT · Fase 5 · 23-oct-2026

Estado: preparación estática. Ningún comando de esta guía se ha ejecutado en el teléfono; no acredita una fila de QA. Aplicar después de que MM-Fit termine o quede detenido de forma comprobada, y de confirmar libre C:\kw\phone.lock. No instalar ni lanzar comandos al teléfono mientras el conductor siga activo. El trabajo de PC puede continuar con una única Gradle propia por la cola compartida; no ejecutar pruebas del anexo antes de entregar.

Usar únicamente el APK .dbg de QA y el flujo de producto:

- APK: C:\kw\vt-mmfit\apks\r8-v51-20261023\app-base-release-r8-dbg.apk
- SHA-256 esperado: 4f26e454ab482d901e73beb33798a899845c479d0a38f305c58d1dc5041be250
- Paquete: com.example.kpkn.dbg
- Actividad: com.example.kpkn.MainActivity

No limpiar datos ni desinstalar. phone_run.py no desbloquea el teléfono; si la captura queda negra o muestra la pantalla bloqueada, detener el intento y registrarlo. --supervised solo sirve con el teléfono abierto, desbloqueado y cedido para esa sesión. No navegar a aplicaciones personales ni capturar sus pantallas.

## 1. Abrir el flujo real y guardar capturas

Comprobar el hash local y, cuando MM-Fit haya liberado el dispositivo, instalar/abrir con phone_run.py:

    Get-FileHash C:\kw\vt-mmfit\apks\r8-v51-20261023\app-base-release-r8-dbg.apk -Algorithm SHA256

    python -I -X utf8 C:\kw\tools\phone_run.py --apk C:\kw\vt-mmfit\apks\r8-v51-20261023\app-base-release-r8-dbg.apk --component com.example.kpkn.dbg/com.example.kpkn.MainActivity --out C:\kw\shots\vt\qa\20261023 --tag fase5 --actions "wait:2;shot:00-inicio"

Si la app ya está abierta y KPKN permanece en primer plano, las capturas siguientes pueden hacerse sin reiniciar la actividad:

    python -I -X utf8 C:\kw\tools\phone_run.py --no-install --no-start --keep-open --out C:\kw\shots\vt\qa\20261023 --tag fase5 --actions "shot:01-estado"

tap:x,y y swipe:... usan píxeles de la pantalla principal 1080×2640. Leer primero la captura y usar solo coordenadas observadas; no reutilizar coordenadas supuestas. phone_run comprueba el foco antes de pulsar, pero shot por sí solo no lo comprueba: mantener KPKN visible y detenerse si cambia el foco. El rec opcional del script graba la pantalla, no el vídeo de cámara; no sirve para demostrar FPS y no debe dejarse grabando si puede cambiar la app enfocada.

Recorrido real, con capturas nombradas por estado:

1. Sesión en vivo → tarjeta de serie → Modo visual → Vídeo. Capturar vista previa, REC, finalización y registro de la serie; no usar TechniqueRecorderScreen ni TechniqueBenchService como entrada.
2. Repetir con una serie de un perfil compatible con velocidad/disco. En Perfil → Técnica, activar o desactivar «Grabar a 60 fps cuando se pueda»: apagado solicita 30; encendido solicita 60 solo para SQUAT, DEADLIFT, BENCH, OHP, HIP_THRUST y ROW. La cámara puede conceder 30 como respaldo.
3. Abrir el análisis desde la tarjeta y capturar estado previo, progreso en curso y resultado. Comprobar que grabar pausa el análisis y que al terminar la grabación se reanuda.
4. Probar Usar/Corregir, confianza ALTA/MEDIA/BAJA, parciales y descanso al 130%; guardar evidencia del cambio aplicado y de los avisos.
5. En el álbum de ese medio, comprobar capa, 0,25×, paso por fotograma y bucle; luego calibración 45/40/35/Otro y recálculo PLATE_ONLY.
6. En Perfil → Técnica en vídeo, comprobar tendencia, comparación, galería y reanálisis con el mismo registro, sin importar archivos ajenos ni duplicar la entrada.

Usar una captura separada por cada estado. Registrar fecha, SHA del APK, perfil, preferencia FPS, medio de prueba, acción y resultado. Los renders de Modo visual al 100/130 ya pasaron; esta ronda comprueba el flujo físico y sus acciones.

## 2. FPS efectivos por PTS

El ajuste «Grabar a 60 fps» y capture.json acreditan solicitud/concesión, no la cadencia del archivo. El control válido es la secuencia de PTS del MP4 real. Cuando un único clip de QA generado o seleccionado para estas pruebas esté disponible en una ruta local mediante la exportación individual del flujo de producto, extraer y resumir sus PTS:

    $vtVideo = 'C:\kw\shots\vt\qa\20261023\qa-30.mp4'
    $vtPts = 'C:\kw\shots\vt\qa\20261023\qa-30-pts.json'
    ffprobe -v error -select_streams v:0 -show_entries frame=pts_time -of json $vtVideo | Set-Content -LiteralPath $vtPts -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'ffprobe no pudo leer el clip de QA' }
    @'
    import json, math, statistics, sys
    from pathlib import Path

    frames = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8-sig")).get("frames", [])
    pts = []
    for frame in frames:
        try:
            value = float(frame["pts_time"])
            if math.isfinite(value):
                pts.append(value)
        except (KeyError, ValueError, TypeError):
            pass
    if len(pts) != len(frames) or len(pts) < 2:
        raise SystemExit("INCOMPLETO: faltan PTS reales; no sustituir por timestamps estimados")
    delta = [b - a for a, b in zip(pts, pts[1:])]
    positive = sorted(value for value in delta if value > 0)
    median = statistics.median(positive) if positive else 0
    duration = pts[-1] - pts[0]
    print(json.dumps({"frames": len(pts), "duration_s": duration,
        "mean_fps": (len(pts) - 1) / duration if duration > 0 else None,
        "median_fps": 1 / median if median else None,
        "dt_p95_s": positive[int(.95 * (len(positive) - 1))] if positive else None,
        "gaps_gt_1_5x": sum(value > 1.5 * median for value in positive) if median else None,
        "nonpositive_pts": sum(value <= 0 for value in delta)}))
    '@ | python -I -X utf8 - $vtPts

Repetir con $vtVideo y $vtPts terminados en qa-60. Informar mediana de ΔPTS, FPS efectivos medios y medianos, percentil 95 de ΔPTS, huecos y cualquier PTS no creciente; no sustituir por FPS nominal, timestamps best-effort, insignia, grantedFps o screenrecord. Un PTS no creciente o una discontinuidad exige revisión; el resumen por sí solo no declara el gate aprobado.

Límite de extracción actual: WorkoutMediaStore guarda vídeo y sidecars bajo filesDir/workout_media/yyyy-MM/, que es privado. phone_run --pull-dbg solo trae archivos de /sdcard/Android/data/com.example.kpkn.dbg/files/; no puede extraer esos MP4. La exportación ZIP existente puede incluir medios previos y no se debe usar para trasladar todo el álbum con el fin de medir un clip. Hasta comprobar una exportación individual o una ruta ya permitida para ese clip, marcar PTS 30/60 como pendiente. La lectura/escritura local necesaria para el QA ya está autorizada; una duda operativa no exige esperar otra respuesta del usuario. No usar adb pull fuera de phone_run ni leer el almacenamiento privado por otra vía.

## 3. Delegado, temperatura y batería

El flujo real deja un resumen PipelineRunStats en el log del proceso KPKN, tag KpknTechnique, con delegado, cuadros y tiempos por pasada. phone_run.py no ofrece lectura de logcat en su CLI, pero su API Python sí tiene find_serial, acquire/release, free_to_use y adb. El patrón siguiente está limitado a batería, servicio térmico, PID del paquete .dbg y logcat de ese PID/tag; imprime solo la línea resumen y no guarda el log bruto. Ejecutarlo después de analizar, con el teléfono libre:

    @'
    import importlib.util, re

    spec = importlib.util.spec_from_file_location("phone_run_readonly", r"C:\kw\tools\phone_run.py")
    phone = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(phone)
    serial = phone.find_serial()
    if not serial:
        raise SystemExit("No hay dispositivo ya conectado")
    phone.SERIAL = serial
    phone.acquire(timeout_s=0)
    try:
        ok, why = phone.free_to_use()
        if not ok:
            raise SystemExit(why)
        model = phone.adb("shell", "getprop", "ro.product.model").stdout.decode(errors="replace").strip()
        if "SM-F731B" not in model:
            raise SystemExit("Dispositivo distinto del Z Flip5 esperado")
        battery = phone.adb("shell", "dumpsys", "battery").stdout.decode(errors="replace")
        thermal = phone.adb("shell", "dumpsys", "thermalservice").stdout.decode(errors="replace")
        level = re.search(r"^\s*level:\s*(\d+)\s*$", battery, re.M)
        scale = re.search(r"^\s*scale:\s*(\d+)\s*$", battery, re.M)
        charging = bool(re.search(r"^\s*(?:AC|USB|Wireless|Dock) powered:\s*true\s*$", battery, re.M | re.I))
        status = re.search(r"^\s*status:\s*(\d+)\s*$", battery, re.M)
        charging = charging or bool(status and status.group(1) == "2")
        percent = round(100 * int(level.group(1)) / int(scale.group(1))) if level and scale and int(scale.group(1)) else None
        t = re.search(r"^\s*Thermal\s+Status\s*:\s*([0-6])\b", thermal, re.M | re.I)
        if not t:
            t = re.search(r"^\s*mStatus\s*=\s*([0-6])\s*$", thermal, re.M | re.I)
        print("battery_percent=%s charging=%s" % (percent if percent is not None else "unavailable", charging))
        print("thermal_status=%s" % (t.group(1) if t else "unavailable"))
        pid = phone.adb("shell", "pidof", "com.example.kpkn.dbg").stdout.decode(errors="replace").split()
        if pid:
            raw = phone.adb("shell", "logcat", "-d", "-v", "time", "--pid=" + pid[0], "-s", "KpknTechnique:I").stdout.decode(errors="replace")
            rows = [line for line in raw.splitlines() if "Pasada de " in line]
            for line in rows:
                print(re.sub(r"(Pasada de )[^:]+:", r"\1[id]:", line, count=1))
            if not rows:
                print("delegate=unavailable; no inferir GPU/CPU")
        else:
            print("delegate=unavailable; proceso .dbg no activo")
    finally:
        phone.release()
    '@ | python -I -X utf8 -

Tomar muestra antes, durante y después del análisis si el foco sigue siendo KPKN; si falta estado térmico o la línea del análisis, anotarlo como no disponible. El estado térmico 3 o mayor implica pausa de 10 minutos y nueva lectura; batería menor al 30% sin carga implica esperar hasta que cargue o supere 40%. No inferir GPU por el modelo del teléfono ni por el manifiesto.

## 4. Privacidad dinámica por UID

Hacer la observación alrededor del mismo flujo real de captura y análisis, con una ventana base sin tocar KPKN y una ventana de uso controlado. El único dato que se guarda es el contador del UID de com.example.kpkn.dbg antes/después y la duración de cada ventana. No usar contadores de interfaz globales ni presentar tráfico de todo el sistema como tráfico VT.

Al quedar libre el teléfono, consultar la ayuda real con phone_run.adb("shell", "cmd", "netstats", "help") y confirmar en ese dispositivo si admite una consulta filtrada por UID. Obtener el UID solo de dumpsys package com.example.kpkn.dbg. No asumir flags ni ejecutar una consulta global sin filtro. Si el dispositivo solo ofrece un volcado global, filtrarlo en memoria al UID objetivo y descartar el contenido bruto: nunca guardar ni imprimir filas de otras apps. Si no se puede aislar el UID de forma verificable, dejar el gate de privacidad pendiente. El contador del UID cubre todo el paquete durante la ventana; describirlo así y controlar que no haya otros flujos KPKN simultáneos. El análisis de fuente y el manifiesto no acreditan tráfico dinámico. El arnés telemetry_check es un banco, no sustituye esta prueba de producto.

## Evidencia que no se debe inferir

La app procesa vídeo localmente y existe una exportación ZIP iniciada por la persona; no afirmar que un vídeo no puede salir del dispositivo. La medición de PTS, el delegado observado, el estado térmico y el contador por UID son gates distintos. Cualquier dato no extraíble con las rutas permitidas queda pendiente y se registra como tal.
