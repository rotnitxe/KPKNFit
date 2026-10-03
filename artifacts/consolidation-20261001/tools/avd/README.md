# Herramientas AVD de consolidación (KPKN Fit, 2026-10-01)

Juego **nuevo y autocontenido** para validar en emulador el árbol integrado
(`C:\Users\valen\Documents\KPKNFit\android-native`) sin depender de la copia aislada de la sesión anterior
(`artifacts\session-repair\2026-09-30-01a0eede`, "OLDROOT", que no se modifica). Todo lo que sale de OLDROOT se
**portó** (lógica) o se **copió** (fixtures sintéticos, con hash registrado); ningún script lee ni importa nada de allí
en tiempo de ejecución, salvo la búsqueda *opcional* del APK baseline v27 (ver «Migración»).

> Estado al entregar: **nada se ejecutó contra un dispositivo** (no había emulador ni APK fresco). Lo verificado es
> sintaxis, 153 tests offline y simulaciones de extremo a extremo contra un adb simulado (ver «Tests offline»).
> Lo que solo se puede afirmar con un dispositivo está en «Supuestos no verificados».

## 1. Prerrequisitos

| Qué | Valor |
|---|---|
| Python | `C:\Users\valen\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe` (3.12), siempre con `-X utf8` |
| adb | `C:\Users\valen\AppData\Local\Android\Sdk\platform-tools\adb.exe` (override: `KPKN_QA_ADB`) |
| AVD de auditoría | `KPKNFitSessionAudit20260929` en `emulator-5580`, `ANDROID_AVD_HOME=C:\Users\valen\AppData\Local\Temp\KPKNFitSessionAuditAVDs` |
| AVD del wizard | `KPKNWizchatQA` en el AVD home por defecto, puerto 5582 → `emulator-5582` (override: `KPKN_QA_WIZARD_PORT`) |
| APK integrados | `android-native\app\build\outputs\apk\{base,health}\debug\app-{base,health}-debug.apk` y `...\apk\androidTest\{base,health}\debug\app-{base,health}-debug-androidTest.apk` |
| APK baseline v27 (solo migración) | descubierto en este orden: `--baseline-apk`; `OLDROOT\baseline-workspace\...\apk\base\debug\app-base-debug.apk` (hoy **no existe**: solo hay `repairBenchmark`); `artifacts\session-audit\2026-09-29\kpkn-base-debug.apk` (BaseDebug histórico, versionCode 34, el que creó la BD v27 de la auditoría) |
| Fixtures | `fixtures\` (5 BD Room v27 sintéticas, PNG/MP4 sintéticos, `audit_shared_prefs`, golden opcional) + `fixtures\fixtures.lock.json` |

Los APK deben estar **compilados después del último cambio de fuentes**: `install_apks.py` y cada driver rechazan (exit 4)
un APK más viejo que `android-native\app\src\main` (`--allow-stale` lo acepta y lo deja anotado). No se ejecuta Gradle ni se
arranca nada desde aquí: compilar es responsabilidad de quien es dueño del build.

## 2. Modelo de seguridad

* **Lista blanca** (`qa_paths.OWNED_DEVICES`): solo `emulator-5580` y el serial del wizard. `emulator-5554` y `emulator-5556`
  están además en una denylist independiente. Cualquier otro serial se rechaza **antes** de lanzar un proceso adb.
* Cada llamada lleva `-s <serial>`; `kill-server`, `connect`, `reboot`, `root`, `forward`, `-s/-d/-e`, `emu` salvo
  `avd name | geo fix | kill`, y `pm clear/uninstall/disable` de paquetes ajenos a `com.example.kpkn[.test]` están bloqueados
  (`avd.guard_adb_args`).
* El AVD detrás del serial debe contestar **exactamente** su nombre a `adb -s <serial> emu avd name`; además
  `ro.kernel.qemu=1` y `sys.boot_completed=1` (`Avd.verify`). `adb devices -l` (única llamada sin `-s`) puede listar otros
  emuladores: se ignoran.
* `KPKN_QA_OFFLINE=1` (o `--validate-only`) hace que `Avd` rechace cualquier acceso a adb: ejecución solo de host.
* Los datos tocados son los de `com.example.kpkn` en el AVD propio (`pm clear` + fixture sintético); antes se copia a
  `room\00-preseed` lo que hubiera. Nada se envía fuera del equipo.

## 3. Resultado, PASS / FAIL / NOT_RUN y evidencias

Cada driver imprime **un JSON** en stdout (progreso en stderr) con un resultado explícito por paso y lo guarda como
`result.json`.

| Estado | Significado |
|---|---|
| `PASS` | el paso se ejecutó y su aserción se cumplió |
| `FAIL` | el paso se ejecutó y la aserción falló (o el código lanzó): detiene la corrida; los pasos restantes quedan `NOT_RUN` («blocked by failed step X») |
| `NOT_RUN` | no se ejecutó (bloqueado, evidencia no disponible, insumo faltante). **Nunca cuenta como éxito** |

`overall`: `FAIL` si algún paso falló; `PASS` solo si **todos** los pasos requeridos pasaron; si no, `NOT_RUN`.
Los pasos marcados `optional` (evidencia de corroboración) pueden quedar `NOT_RUN` sin impedir el `PASS`, pero un `FAIL`
sigue fallando la corrida. Códigos de salida: **0** PASS · **1** FAIL · **2** error de argumentos · **3** NOT_RUN ·
**4** insumo faltante / dispositivo rechazado (no se tocó el dispositivo).

Estructura de archivo (nunca se reutiliza ni se sobrescribe un directorio):

```
consolidation-20261001\device-evidence\
  installs\<flavor>\<UTC>-<id>\install-receipt.json      + installs\<flavor>\latest.json
  instrumentation\<flavor>\<UTC>-<id>-<label>\{declared,metadata,summary}.json, stdout.txt, stderr.txt, logcat.txt
  <driver>\<flavor>\<UTC>-<id>[-label]\
      result.json        el JSON de stdout (pasos, notas, inputs: hashes de APK/fixtures/fuentes/herramientas)
      files.json         SHA-256 y tamaño de TODO lo archivado
      events.jsonl       cada captura, toque, tecla, lanzamiento, siembra
      seed.json          fixture + prefs empujados con hash de dispositivo, permisos concedidos
      ui\NNN-*.xml|png   UiAutomator fresco antes de cada acción (PNG en hitos o con --png-all)
      room\NN-*\         app-data.tar, app-data\databases\kpkn.db(+wal+shm), shared_prefs\, manifest.json (hashes), summary.json
      logcat.txt
  suites\<flavor>\<UTC>-<role>\suite.json
  avd-logs\              logs + .launch.json de start_avd.ps1
```

`<driver>` ∈ `strength-s1-s2`, `cancel-strength`, `cancel-cardio`, `editor-drafts-{restore,save,discard}`,
`editor-transfer-{append,replace,create}`, `cardio-two-series`, `media-import`, `camerax-capture`, `migration-27-28`.
Cada captura Room **detiene la app** (`force-stop`) para que BD y WAL sean consistentes (`consistent: true` en el manifiesto);
el driver relanza explícitamente cuando sigue.

## 4. Orden exacto de comandos (por flavor)

```powershell
$py  = 'C:\Users\valen\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
Set-Location 'C:\Users\valen\Documents\KPKNFit\artifacts\consolidation-20261001\tools\avd'
```

**0. Sin dispositivo** (siempre primero):

```powershell
& $py -X utf8 -m unittest discover -s tests -p "test_*.py"     # 153 tests (~2 min)
& $py -X utf8 tests\check_names.py                               # nombres indefinidos (no hay linters instalados)
& $py -X utf8 fixtures.py verify                                 # fixtures y prefs contra fixtures.lock.json
& $py -X utf8 install_apks.py --flavor base --dry-run            # APK: manifiesto, hash, variante, frescura (sin adb)
& $py -X utf8 migrate_27_28.py --flavor base --validate-only     # fixture v27 + baseline + schema 28 (sin adb)
```

**1. Arrancar el AVD propio** (ventana, GPU host, `-no-snapshot-load/-save`, 2048 MB, 2 núcleos, `-feature -Vulkan`):

```powershell
.\start_avd.ps1 -DryRun            # muestra el comando y la config de cámara, no arranca nada
.\start_avd.ps1 -WaitBoot
& $py -X utf8 avd.py status
```

**2. Por flavor — primero `base`, luego `health`** (el mismo AVD; `install_apks.py --flavor health` reemplaza in-place):

```powershell
$f = 'base'      # luego 'health'
& $py -X utf8 install_apks.py --flavor $f
& $py -X utf8 run_instrumentation.py --flavor $f --classes-file suites\workout-avd.txt --label workout-avd
& $py -X utf8 run_instrumentation.py --flavor $f --classes-file suites\gps-lifecycle.txt --prepare-gps --geo-feed --label gps-lifecycle
& $py -X utf8 ui_strength_s1_s2.py --flavor $f
& $py -X utf8 ui_cancel_workout.py --flavor $f --mode strength
& $py -X utf8 ui_cancel_workout.py --flavor $f --mode cardio
foreach ($s in 'RESTORE','SAVE','DISCARD') { & $py -X utf8 ui_editor_drafts.py --flavor $f --scenario $s }
foreach ($s in 'APPEND','REPLACE','CREATE')  { & $py -X utf8 ui_editor_transfer.py --flavor $f --scenario $s }
& $py -X utf8 ui_cardio_two_series.py --flavor $f
& $py -X utf8 ui_media_import.py --flavor $f
& $py -X utf8 camerax_capture.py --flavor $f --finish
& $py -X utf8 migrate_27_28.py --flavor $f          # SIEMPRE el último: instala el baseline v27 y deja el APK integrado
# opcional, ~1 h: las 38 clases androidTest sin argumentos especiales
& $py -X utf8 run_instrumentation.py --flavor $f --classes-file suites\all-android-tests.txt --timeout 3600 --label all-android-tests
```

Equivalente automatizado (un subproceso por etapa, resumen en `suites\...\suite.json`):
`& $py -X utf8 run_suite.py --flavor $f` (`--plan` imprime el plan, `--only a,b`, `--skip a,b`, `--continue-on-fail`,
`--with-full-instrumentation`).

**3. Wizard** (otro AVD; ejecutar por separado para no tener dos emuladores a la vez):

```powershell
.\start_avd.ps1 -Avd Wizard -WaitBoot
& $py -X utf8 install_apks.py --flavor base --serial emulator-5582
& $py -X utf8 run_instrumentation.py --flavor base --serial emulator-5582 --classes-file suites\wizard-ui.txt --label wizard-ui
& $py -X utf8 avd.py --serial emulator-5582 stop --yes
```

**4. Cerrar**: `& $py -X utf8 avd.py stop --yes` (solo `emu kill` del emulador de la lista blanca).

Opciones comunes de los drivers: `--flavor`, `--serial`, `--apk` (APK instalado esperado), `--label`, `--home-timeout 180`,
`--allow-stale`, `--png-all`.

## 5. Archivos

| Archivo | Función |
|---|---|
| `avd.py` | wrapper adb con lista blanca; UI XML fresco, screenshot, `dumpsys` top-resumed, logcat, `run-as` (cat/sha256/tar), copia de **Room db+WAL+shm con hashes**, `geo fix`; CLI `status / ui-dump / screenshot / top-resumed / logcat / room-pull / stop` |
| `start_avd.ps1` | arranque con los flags de `start-owned-avd.ps1`; `-Avd Audit\|Wizard`, `-Renderer`, `-Headless`, `-CameraBack/-CameraFront`, `-WaitBoot`, `-DryRun` |
| `install_apks.py` | instala debug + androidTest; SHA-256 origen vs instalado (`sha256sum` del único `base.apk`), `versionCode` del manifiesto vs `dumpsys`, instrumentación registrada; recibo JSON; `--dry-run` |
| `run_instrumentation.py` | `am instrument -w -r` por clases/métodos; declarados (parser Kotlin) vs reportados; `-e scenario APPEND\|REPLACE\|CREATE`; `--timeout`; `--prepare-gps --geo-feed`; `--list` |
| `ui_strength_s1_s2.py` | S1→S2 con **un click** por serie, 1/2 y 2/2 (UI), reapertura y Room con exactamente 20×6 y 22.5×6 |
| `ui_cancel_workout.py` | cancelación `strength` / `cardio`: ongoing borrado, sin log parcial, sin snapshot GPS, servicio parado |
| `ui_editor_drafts.py` | borradores RESTORE / SAVE / DISCARD + ciclo de vida (HOME+retorno, muerte de proceso) con `room_verify` |
| `ui_editor_transfer.py` | transferencia APPEND / REPLACE / CREATE → Room → rematerialización real (androidTest) → verificador |
| `ui_cardio_two_series.py` | cardio de dos series / GPS (coordenadas sintéticas) con checkpoints Room y claves de snapshot GPS |
| `ui_media_import.py` | fotos/vídeo **importados** por el selector del sistema, foto **tardía** (después de la serie, antes de Finish) y Finish |
| `camerax_capture.py` | captura **real** con CameraX (ver §6) |
| `migrate_27_28.py` | migración real 27→28 in-place (ver §7) |
| `run_suite.py` | secuencia las etapas de arriba |
| `qa_common.py`, `evidence.py`, `uia.py`, `fixtures.py`, `room_inspect.py`, `room_verify.py`, `media_*.py`, `cardio_profiles.py`, `editor_flow.py`, `gps_tools.py`, `kotlin_tests.py`, `apk_info.py`, `qa_paths.py` | núcleo (selectores, recorder PASS/FAIL/NOT_RUN, registro de hashes, inspección Room, verificador de editor/migración ported verbatim, etc.) |
| `suites\*.txt` | listas de clases androidTest (workout-avd, gps-lifecycle, wizard-ui, warmup-qa10, all-android-tests) |
| `tests\` | tests offline y simulador de adb (`fake_device.py`) |

## 6. `camerax_capture.py`: captura real vs importación

Navegación (textos reales de `SetCardExerciseMediaBack.kt`, `WorkoutSessionAlbumSheet.kt`, `WorkoutMediaThumb.kt`):
`Ver ejercicio/Fotos` → chips **`Foto`**, **`Vídeo` → `Parar`**, `Galería` (importación: **este script nunca la toca**),
`desc:Álbum`, `desc:Volver a la serie`; thumbs con `contentDescription` = nombre del ejercicio y overlay `Vídeo`;
sheet «Álbum de esta sesión».

Permisos (contrato del código): `CAMERA` se pide al abrir la cara de medios; `RECORD_AUDIO` al pulsar `Vídeo`.
`--permissions pm` (defecto) concede con `pm grant`; `--permissions dialog` responde el diálogo del sistema
(`permissioncontroller`, ids `permission_allow_foreground_only_button` / `permission_deny_button` + textos EN/ES).
`--audio grant|deny`: con `deny` el vídeo se graba sin audio (la app lo soporta) y se exige que el MP4 no tenga pista de audio.

Qué prueba (todos requeridos para `captureClaim: REAL_CAMERAX_CAPTURE`):

1. el preview CameraX está ligado (mensaje «Activa la cámara…» ausente, chips habilitados);
2. **una** pulsación del obturador (`Foto`; `Vídeo`…`Parar` con `--video-seconds`) y una miniatura nueva en la UI
   (dos heurísticas independientes: `contentDescription` del ejercicio / overlay `Vídeo`, y nodos clicables cuadrados de 40–80 dp);
3. fila `workout_media` con UUID canónico, `kind`, `sessionKey/programId/sessionId` del entreno en curso, `exerciseId` de la sentadilla,
   sin vínculo de log, `createdAtMs` dentro de la ventana de captura (reloj del dispositivo), ruta
   `filesDir/workout_media/yyyy-MM/<uuid>.jpg|mp4` y nombre = `<uuid>.<ext>`;
4. el archivo se **lee del dispositivo** y es salida real de encoder: JPEG con SOF/EOI y dimensiones, o MP4 con `ftyp`+`moov`+pista de vídeo y duración
   ≈ lo grabado; miniatura de vídeo existente; **hash distinto** de los fixtures PNG/MP4 de importación;
5. `media_checks.classify_origin` → `CAMERAX_CAPTURE`. La misma función clasifica `IMPORT` si los bytes son un fixture,
   si se usó `Galería` o si el selector estuvo en primer plano (`origin_classifier_self_check` y los tests lo demuestran);
6. corroboración **opcional** (no bloquea): `dumpsys media.camera` lista `com.example.kpkn` como cliente.

`--finish` termina el entreno y verifica la asociación durable sesión→log de las filas capturadas.

**Cámara del AVD** (leído de los `config.ini`, sin modificarlos):

| AVD | `hw.camera.back` | `hw.camera.front` | otros |
|---|---|---|---|
| `KPKNFitSessionAudit20260929` | `emulated` | `emulated` | `hw.camera.back.orientation=90`, `hw.audioInput=yes`; `AVD.conf`: `mic\available=false` |
| `KPKNWizchatQA` | `emulated` | `none` | |

`emulated` ya es una cámara de dispositivo que entrega fotogramas reales a Camera2/CameraX: **no hace falta cambiar nada** para
capturar. Para la escena virtual 3D **sin tocar el AVD**: `.\start_avd.ps1 -CameraBack virtualscene -WaitBoot`
(`-camera-back virtualscene`, requiere GPU host). Si se prefiriera persistirlo habría que cambiar `hw.camera.back=virtualscene`
en `...\KPKNFitSessionAudit20260929.avd\config.ini` (no se hizo). Con `mic\available=false` la grabación con audio podría no
entregar muestras: usar `--audio deny` para aislar CameraX de la entrada de audio.

Distinción con importación en el repo: `ui_media_import.py` (selector, PNG/MP4 sintéticos, foto tardía) y `camerax_capture.py` (obturador)
escriben evidencia con `origin` distinto y nunca comparten fixture de captura.

## 7. `migrate_27_28.py`

Baseline: `artifacts\session-audit\2026-09-29\kpkn-base-debug.apk` salvo que exista el debug de `baseline-workspace` o se pase
`--baseline-apk` (ruta, hash y manifiesto quedan en el recibo). `--probe-baseline` lanza ese APK una vez sobre el fixture y exige que
siga en v27 (convierte esa captura en el «antes»). Flujo: instala baseline (`-r -d`) → `pm clear` → fixture
`migration-retention-v27` (1 programa, 2 logs, 4 medios, 1 ongoing) + 4 PNG privados + prefs de auditoría + archivo marcador →
captura «antes» (= fixture byte a byte, sin WAL) → `adb install -r` del APK integrado **sin desinstalar ni `pm clear`** → marcador y
`kpkn.db` intactos justo después de actualizar → primer arranque (migra) → Home → captura «después» y comparación:
identity hash de `room_master_table` = `schemas\...\28.json`; `programs` y `workout_media` fila a fila; `workout_logs`
**semánticamente** (la normalización tipada puede reescribir el JSON, nunca perder una serie: id, peso, reps, sesión, volumen);
asociaciones exactas (`qa-migration-valid → repair-history-0`, la clave ambigua no crea vínculo); DB/WAL antes y después con
hashes; PNG y marcador intactos; APK instalado = build. Falla claro (exit 4) si falta APK integrado, baseline, fixture, schema 28,
o si el baseline tiene versionCode mayor / misma firma incompatible (`--check-signers`, requiere Java).

## 8. Registro en lugar de pins

* `fixtures\fixtures.lock.json`: hashes **esperados de los insumos sintéticos**; una deriva = archivo corrupto/editado, no una aserción contra el producto
  (`fixtures.py init-lock --force` para refrescarlo a propósito).
* Cada corrida guarda en `inputs`: SHA-256 del APK (y su `versionCode`, variante, frescura frente a las fuentes), de los fixtures y prefs, de
  las fuentes del producto que usa el driver (`fixtures.source_registry()`, **solo informativo**) y huella de estas herramientas.
* Se descartaron los pins sobre `workspace\...` (WorkoutV2Body, CardioLiveCard, WorkoutViewModel…), sobre scripts de OLDROOT y sobre recibos de
  staging: la verificación ahora es contra el comportamiento (UI/Room), no contra hashes de fuentes antiguas.

## 9. Tests offline

```powershell
& $py -X utf8 -m unittest discover -s tests -p "test_*.py"
```

153 tests: lista blanca y guardas de comandos, selectores y volcado «fresco» con reintentos, parseo de `am instrument`
(stacks multilínea, skip ≠ pass, crash, parametrizados), descubrimiento Kotlin, parser binario de `AndroidManifest`, `Recorder`
(PASS/FAIL/NOT_RUN y códigos de salida), Room/WAL de solo lectura, el verificador de editor/migración sobre el fixture real v27
(migración simulada correcta, asociación equivocada, medio perdido, serie perdida), clasificación CameraX vs importación, perfiles de
cardio, listas de suites contra las clases reales, y **simulaciones de extremo a extremo** de los drivers (strength, cancel strength/cardio,
borradores x3, transferencia x3, cardio, importación + Finish, CameraX, migración, `run_instrumentation.run`, `install_verified`) contra `tests\fake_device.py` (simulador de adb a nivel de comando que
ejecuta el wrapper real y una BD SQLite real). El simulador **prueba la coherencia interna de los drivers, no que la UI del producto sea como la simulada.**

## 10. Supuestos no verificados sin dispositivo

1. Que la UI del build integrado coincida con la de OLDROOT para los flujos portados (los fuentes de `SetExecutionCard`, `SetCardExerciseMediaBack`,
   `WorkoutFinishHost`, `CardioLiveCard`… son idénticos a la copia aislada; `WorkoutViewModel.kt` difiere): textos/`content-desc` usados
   (`S1`, `Registrar serie`, `Saltar descanso`, `Registrar feedback`, badges `n/m` o `✓`, `Abandonar sin guardar`, `Terminar hasta acá`,
   `Guardar y terminar entrenamiento`, `Más`/`Transferir`/`Reemplazar`/`Preparar transferencia`, perfiles de cardio…).
2. Que con `pm clear` + fixture v27 + prefs de auditoría + permisos concedidos el build integrado llegue a Home (`onboardingCompleted` viene de la tabla
   `settings` del fixture) y que la importación del catálogo de alimentos no exceda `--home-timeout`.
3. Navegación del selector del sistema (DocumentsUI): búsqueda/ids/carpetas varían por imagen; se prueba visibilidad directa → búsqueda → carpetas → raíces y se archiva cada intento.
4. `uiautomator dump` con el preview CameraX activo; ids del diálogo de permisos en API 36; que CameraX ligue sobre la cámara `emulated`; grabación con `mic\available=false`.
5. Formato de `dumpsys media.camera` y de `dumpsys activity services` (pasos de corroboración / parada del servicio GPS).
6. Orden «borrado antes de salir» en la cancelación: se acota por la latencia de UiAutomator (Room tras la primera captura sin la pantalla de entreno);
   la prueba estricta queda en tests unitarios/instrumentados y se reporta `NOT_RUN` (opcional).
7. Que `kpkn-base-debug.apk` (BaseDebug del 2026-09-29) sea realmente un build Room v27 (`--probe-baseline` lo comprueba) y comparta firma (debug) con los APK integrados.
8. Que los `workout_logs` migrados cumplan el golden JUnit de OLDROOT (paso opcional; la comprobación semántica es la autoritativa).
9. Serial del AVD wizard (`emulator-5582`, configurable): solo se confía tras `emu avd name = KPKNWizchatQA`.
10. «Tardío»: la UI solo permite una importación después de la serie y antes de Finish; la ingesta que sobrevive al observador **después** de Finish se cubre con
    `WorkoutMediaLateRetryInstrumentedTest` / `WorkoutMediaUriDurabilityInstrumentedTest` (`suites\workout-avd.txt`) y el paso queda `NOT_RUN` opcional.

## 11. Correspondencia con OLDROOT

`avd_owned.py`→`avd.py` · `start-owned-avd.ps1`→`start_avd.ps1` · `uia_dump_freshness.py`+`ime_visibility.py`+selectores de `measure_catalog_editor.py`→`uia.py` ·
`evidence_collector.py`→`kotlin_tests.py` · `run-final-instrumentation-owned.py`+`run_instrumentation_owned.py`→`install_apks.py`+`run_instrumentation.py` ·
`seed_owned.py`→`QaSession.seed`/`install_verified` · `capture_room_owned.py`→`Avd.room_pull`+`room_inspect.py` · `repeat-strength-final-owned.py`→`ui_strength_s1_s2.py` ·
`editor_drafts_avd.py`+`editor-drafts-final-owned.py`→`ui_editor_drafts.py`+`editor_flow.py` · `editor-transfer-final-owned.py`→`ui_editor_transfer.py` ·
`run_cardio_two_series_ui.py`→`ui_cardio_two_series.py`+`cardio_profiles.py` · controlador de medios V4+`verify_workout_media_finish.py`→`ui_media_import.py`+`media_flow.py`+`media_checks.py` ·
`migrate_27_to_28_owned.py`+wrapper normalizado+`make_migration_fixture.py`+`validate-migration-inputs-root.py`→`migrate_27_28.py`+`fixtures.py` ·
`verify_editor_db.py`→`room_verify.py` (verbatim) · `run-frozen-functional-owned.py`→`run_suite.py`.

## 12. Ajustes de herramientas hechos durante la validación del flavor base (2026-10-02)

Descubiertos al ejecutar contra el AVD (la UI del build integrado difiere de OLDROOT); cada uno quedó anotado en el `result.json` correspondiente.

| Archivo | Cambio | Motivo |
|---|---|---|
| `ui_strength_s1_s2.register_once` | siempre lee `dumpsys input_method` y cierra el IME antes de tocar «Registrar serie» | el FAB sigue en el árbol UiAutomator aunque el teclado numérico lo tape: el toque caía en la tecla «-» (campo `20-`) |
| `gps_tools.app_workout_services` + `ui_cancel_workout` | tras cancelar exige que no quede ningún servicio voz/descanso/cardio GPS vivo (antes de la captura Room, que hace force-stop) | contrato «servicios/voz detenidos» |
| `cardio_nav.py` (nuevo), `cardio_profiles.py`, `ui_cardio_two_series.py`, `ui_cancel_workout.py` | el build ordena las partes de cardio **después** de las de fuerza y el nombre mostrado es el resuelto por catálogo; se llega a la tarjeta cardio por el carril («— CARDIO —»), se desplaza la tarjeta para alcanzar «Finalizar y registrar», `confirm-cardio` es un diálogo (ventana propia), se acepta la tarjeta de feedback post-ejercicio y se cierra la hoja «Resumen de entrenamiento» con «Volver al entrenamiento» | orden/UX nuevos |
| `ui_editor_transfer.py` | si el androidTest de rematerialización falla con «No hay ExerciseCompositionMetadataProvider» reintenta ejecutando antes `CardioGpsAvdLifecycleInstrumentedTest` en el mismo proceso (lanza `MainActivity`, que llena `CompositionMetadataHolder`) | el test no inicializa el catálogo y `am instrument` mata la app |
| `room_verify.py` | `canonical()` ignora `nativeProgressionManaged=false` y `manualLoadRequiredSides=[]` (campos nuevos con valor por defecto); `verify(allow_derived_native_metadata=True)` revierte **solo** `nativeProgressionManaged` (false→true) y `loadQuantityConvention` (UNSPECIFIED→derivado) tras rematerializar y los lista en el check visible `derived_native_metadata_upgrade` | `PlanMaterializer` deriva esos campos (§12.4) en días no editados; el contenido no cambia |
| `uia.clickable_candidates`, `media_flow.tap_widest/tap_target`, `camerax_capture.py` | «Ver ejercicio/Fotos» elige el candidato más ancho (la tarjeta vecina asoma en el borde); el buscador del selector (`AutoCompleteTextView`) ya no cuenta como archivo; `--finish` registra S1 antes (el producto bloquea terminar con 0 series: evento `finish_blocked_empty_session`) | UI real |
| `suites/workout-editor-ui.txt` (nuevo) | clases UI/instrumentadas de workout/editor que no estaban en `workout-avd.txt` | cobertura pedida |

`--allow-stale` fue necesario en `camerax_capture.py` y `migrate_27_28.py`: `NutritionCrashHook.kt` (rama que solo se ejecuta en la JVM de tests unitarios) se modificó a las 03:55 hora local, después de compilar los APK (01:27/01:28).

## 13. Ajustes de herramientas hechos durante la validación del flavor health (2026-10-02)

El flavor health se validó con las herramientas de §12 **sin cambios de comportamiento** salvo uno:

| Archivo | Cambio | Motivo |
|---|---|---|
| `camerax_capture.py` (`video_stop_recording`, `room_rows_and_private_files`) | registra la ventana de reloj del dispositivo entre el toque «Vídeo» y el toque «Parar» (`recordedWindowSeconds`), la muestra en el detalle del paso y exige además `MP4.durationMs <= ventana + 3 s` (antes solo existía la cota inferior del 40 %) | `videoRecordingSince` se toma *después* de la espera UiAutomator que confirma el chip «Parar» (~10-20 s en este AVD): el detalle «stopped after ~4 s» subestimaba la grabación real (MP4 de 14,3 s en la primera corrida health, 24,7 s en la segunda) |

Todas las corridas de health usaron `--allow-stale`: los APK se compilaron a la 01:34 (hora local) y las fuentes cambiaron **después** (primero `NutritionCrashHook.kt` 03:55, luego, durante esta validación y por otros agentes, `LocaleManager.kt`, `ProgramSnapshotStore.kt`, `ProgramDetailViewModel.kt`, `SetExecutionCard.kt`, `WorkoutRestForegroundService.kt`, `WorkoutFinishHost.kt`, `LocalMediaImage.kt`, … y varios `androidTest`, 04:27-04:43). Lo validado en el dispositivo es el APK `637c6b11c7c9` (health) y `42951a739ed2` (androidTest) **tal como estaban compilados**; las correcciones posteriores requieren recompilar y repetir.

## 14. Segunda vuelta, flavor base, APK finales (2026-10-02)

APK `b60dd4b146af` (base, versionCode 34) y androidTest `9e90530f6e07` instalados con `install_apks.py --flavor base` **sin `--allow-stale`** (frescura: APK 08:59/09:00 UTC, fuente más nueva 08:10 UTC; SHA-256 origen == instalado). Ningún driver necesitó `--allow-stale`.

| Archivo | Cambio | Motivo |
|---|---|---|
| `suites/workout-avd.txt`, `suites/all-android-tests.txt` | + `WorkoutRestForegroundServiceStopRaceInstrumentedTest` | clase nueva (carrera start→stop del servicio de descanso); workout-avd pasa de 30 a 31 pruebas |
| `suites/all-android-tests-base-r2.txt` (nuevo) | `all-android-tests` menos `PostSetupCalendarUiTest`, `PostSetupEditorsUiTest`, `SetupWizardFullJourneyUiTest`, `SetupWizardJourneyUiTest` y `WorkoutWarmupInventoryIntegrationTest` | clases que exigen el usuario QA 10 (se cubren en la fase Wizard / ya reportadas SKIPPED) |
| `ui_strength_s1_s2.py` | paso nuevo `rom_null_for_squat_in_room` | lee el JSON crudo de `ongoing_workout` y exige `rom == null` en las series de la sentadilla (la vuelta 1 persistía `rom = 100`) |
| `ui_cardio_two_series.py` | paso nuevo `km_tile_formatted_not_raw_double` | recorre todos los XML UiAutomator de la corrida: exige textos `N.NN km` y ningún double crudo (`0.10811738104249106`) |
| `ui_finish_empty_session.py` (nuevo, driver `finish-empty-session`) | terminar con 0 series | Back → «Terminar hasta acá» → hoja de finalización: banner `Registra al menos una serie para terminar o abandona sin guardar.` en XML y PNG; confirmar no guarda (hoja y banner siguen) y Room: `workout_logs = 0`, sin series de trabajo |
| `strictmode_report.py` (nuevo) | informe de StrictMode por **bloques de violación** (cabecera `StrictMode policy violation` + pila) con origen (primer frame `com.example.kpkn`) y punto de entrada (Application / Activity.attach / layout-draw) | las cifras por líneas `StrictMode` de la vuelta 1 contaban frames, no violaciones |

Nota StrictMode: `KpknApplication.onCreate` instala la política **después** de `KpknDiagnosticLogger.initialize`, `WorkoutVoiceDiagnosticLogger.initialize` y de `attachBaseContext`; las lecturas de disco de esa fase inicial por proceso no se detectan nunca, así que «una por proceso en Application» no aparece en ningún logcat.

Resultados de la segunda vuelta base (evidencia bajo `device-evidence\`; `*-r2` / `*all-android-tests*` / `*stress-rest-cancel-NN*` / `*isolated-*`; informes de StrictMode en `device-evidence\strictmode-report\`):

* workout-avd 31/31 PASS (WorkoutV2UiTest 7/7, StopRace 1/1) · workout-editor-ui 36 PASS + 3 SKIPPED (`WorkoutWarmupInventoryIntegrationTest`: usuario QA 10 → NOT_RUN) · gps-lifecycle 2/2 · estrés `manual_cancel_clears_active_rest_overlay_before_terminal_ack` 20/20 sin `Bringing down service...` ni `ForegroundServiceDidNotStartInTimeException`.
* Drivers PASS: strength-s1-s2 (con `rom` null), finish-empty-session (banner), cancel strength/cardio, cardio-two-series (tile Km `N.NN km`), media-import, camerax-capture `--mode both --permissions pm --finish`, migration-27-28 (APK final reinstalado después), editor-drafts RESTORE/SAVE/DISCARD.
* `suites/all-android-tests-base-r2.txt` (105 pruebas) dos veces: 100/105 y 101/105 PASS. Fallos deterministas (de pruebas, no de producto): `SetupInventoryTypingUiTest` x3 (clase eliminada del repositorio en el cierre del 2026-10-02, ver §26; el paso `INVENTORY_BARBELL` ya no existe en la ruta: `SetupWizardModels.kt:403` devuelve `inventoryGroups() = emptySet()`, y `SetupStepProgress.at()` ignora pasos fuera de ruta) y `TelemetryIntegrationTest.app_should_initialize_telemetry_on_startup` (marcador de posición con `createComposeRule()` sin `setContent`). Intermitente (1 de 2 corridas completas; 0 de 5 aisladas): `WorkoutMediaUriDurabilityInstrumentedTest.selected_uri_finishes_after_vm_scope_cancellation_...` (la aserción final `journal.readAll().isEmpty()` compite con el borrado del marcador: `readAll()` lista y luego lee, y un `FileNotFoundException` se envuelve en `IOException`).

## 15. Segunda vuelta, flavor health, APK finales (2026-10-02)

APK health `65e4a88b4199` (versionCode 34) y androidTest `b84b86ef3e40` instalados con `install_apks.py --flavor health` **sin `--allow-stale`** (SHA-256 origen == instalado; recibo `installs\health\20261002T102553222023Z-99314d`). **No hizo falta ningún cambio de herramientas** (los drivers y `strictmode_report.py` de §14 se usaron tal cual).

* workout-avd 31/31 PASS (WorkoutV2UiTest 7/7, `WorkoutRestForegroundServiceStopRaceInstrumentedTest` 1/1) · workout-editor-ui 36 PASS + 3 SKIPPED (`WorkoutWarmupInventoryIntegrationTest`: usuario QA 10 -> NOT_RUN) · gps-lifecycle 2/2 · estres `manual_cancel_clears_active_rest_overlay_before_terminal_ack` 20/20 (cada iteracion muestra `am_foreground_service_start` y `_stop` de `WorkoutRestForegroundService` y 0 `Bringing down service...` / `ForegroundServiceDidNotStartInTimeException`).
* Drivers PASS: strength-s1-s2 (con `rom` null), finish-empty-session (banner en XML y PNG), cancel strength/cardio, cardio-two-series (tile Km `0.06 km`).
* `media-import` y `camerax-capture` quedaron **NOT_RUN (exit 4, MISSING_INPUT)**: durante la corrida otro proceso modifico `src\main\...\data\media\WorkoutMediaCaptureJournal.kt` (07:49:51 hora local) y los APK (06:05) quedaron mas viejos que las fuentes. No se uso `--allow-stale`; hay que recompilar los 4 APK y repetir `install_apks.py --flavor health` + estos dos drivers (`--label r2`). `migrate_27_28.py` (opcional) tampoco se ejecuto por la misma razon.
* StrictMode (health): `strictmode-report\health-round1-vs-round2.json` (round1 = 23 logcats anteriores a 10:25Z, round2 = 28 logcats de la vuelta final).

## 16. Fase Wizard, segunda vuelta (2026-10-02): soporte de usuario Android secundario (QA10)

`SetupWizardFullJourneyUiTest`, `SetupWizardJourneyUiTest`, `PostSetupCalendarUiTest`, `PostSetupEditorsUiTest` (y `WorkoutWarmupInventoryIntegrationTest`) fallan a propósito si `Process.myUid() / 100000 != 10`. El AVD `KPKNWizchatQA` (`emulator-5582`) ya trae `UserInfo{10:QA10}`; el usuario 0 (`Owner`) conserva su app y sus datos y **nunca** se limpia ni se desinstala desde estas herramientas.

| Archivo | Cambio | Motivo |
|---|---|---|
| `avd.py` | `Avd(..., user=N)` y CLI global `--user N`: `pm path --user N`, `run-as com.example.kpkn --user N`, `am force-stop --user N`, `am start --user N`; `list_users()`, `user_states()`, `current_user()`, `switch_user(N)` (espera `RUNNING_UNLOCKED`), `wake_and_unlock()`; subcomandos `users` y `switch-user N` | los tests del wizard exigen el usuario 10; una actividad de un usuario en segundo plano nunca llega a RESUMED y un usuario recién cambiado arranca con la pantalla dormida tras el keyguard |
| `install_apks.py` | `--user N` (`adb install -r [-t] --user N`); el recibo guarda `androidUser` y `users`; exit 4 si el usuario no existe | instalar el APK y el androidTest para el usuario 10 sin tocar los datos del 0 |
| `run_instrumentation.py` | `--user N` (`am instrument -w -r --user N ...`), `--switch-user` (pone a N en primer plano, despierta/desbloquea y **restaura** el usuario anterior al terminar), `pm list instrumentation --user N`; `summary.json` guarda `androidUser`, `userSwitch`, `userRestore` | idem |
| `wizard_evidence.py` (nuevo) | `pull-captures` (PNG de `captureQ6Screenshot` en `/storage/emulated/10/Android/data/com.example.kpkn/files/q6-ui-captures/<escenario>-<uuid>/`, validación PNG/hash/nombres esperados), `room-check` (`run-as --user 10`: programas activados con semanas/sesiones/ejercicios, recibos, `active_program`), `logcat-scan` (FATAL/ANR/StrictMode/SQLite/IllegalState) | pasos 3 y 4 de la fase Wizard |
| `tests/test_user_support.py` (nuevo) | 11 pruebas offline | cobertura de lo anterior (153 -> 164 pruebas) |

Orden de la fase Wizard con el APK Base final:

```powershell
.\start_avd.ps1 -Avd Wizard -WaitBoot
& $py -X utf8 install_apks.py --flavor base --serial emulator-5582 --user 10
& $py -X utf8 run_instrumentation.py --flavor base --serial emulator-5582 --user 10 --switch-user --classes-file suites\wizard-ui.txt --label wizard-ui --timeout 3600
& $py -X utf8 wizard_evidence.py pull-captures --instrumentation-dir <dir de la corrida>
& $py -X utf8 wizard_evidence.py room-check
& $py -X utf8 wizard_evidence.py logcat-scan --logcat <dir de la corrida>\logcat.txt
& $py -X utf8 avd.py --serial emulator-5582 stop --yes
```

## 17. Tercera vuelta, flavor base, APK finales 12:12 UTC (2026-10-02)

APK base `0b424bc6c8ae` (versionCode 34) y androidTest `7bc7d3a65b7c` instalados con `install_apks.py --flavor base` sin `--allow-stale` (recibo `installs\base\20261002T122556309618Z-b2a1ee`).

| Archivo | Cambio | Motivo |
|---|---|---|
| `qa_common.QaSession.dismiss_ime_if_shown` | tras el `KEYCODE_BACK` que cierra el IME captura un volcado fresco; si aparece el diálogo de salida del entreno («Continuar entrenando» + «Abandonar sin guardar») lo cierra con «Continuar entrenando» y lo registra (`exitDialogOpenedByBack`, `exitDialogRecovered`) | con el host al 100 % de CPU `dumpsys input_method` dijo `shown` para un IME que ya se ocultaba; el Back llegó a la app y abrió «¿Qué deseas hacer?» (`ui_strength_s1_s2`, intento 1). No se pudo reintentar en dispositivo (ver bloqueo de frescura). 32 pruebas offline `test_driver_e2e*.py` siguen OK |
| `suites/all-android-tests-base-r3.txt` (nuevo) | `all-android-tests-base-r2` menos `SetupInventoryTypingUiTest` (la clase se eliminó del repositorio y de todas las listas en el cierre del 2026-10-02, §26: r2 y r3 ya son idénticas) | la clase `@Ignore` se reporta como un único caso `SKIPPED` con `test=null` y sus 3 métodos declarados nunca se reportan: el contrato «declarados == reportados» no se puede cumplir (r2 pasada: 102 PASS + 1 SKIPPED, `FAILED_OR_INCOMPLETE`; r3: 102/102 PASS) |
| `strictmode_pass3.py` (nuevo) | reagrupa los bloques StrictMode por origen: LocaleManager, ProgramSnapshotStore, Coil/FileKeyer, WorkoutMedia*, otros kpkn, terceros; filtra por prefijo de directorio UTC | informe C de la pasada (`device-evidence\strictmode-report\pass3-base.json`) |

Bloqueo de frescura (sin `--allow-stale`): a las 12:56:08 UTC un proceso ajeno (verificación de la re-curación del catálogo) reescribió `src\main\assets\exercise_catalog_v2.json` y `src\main\resources\exercise_catalog_v2.json` (2 182 631 B; los APK contienen 2 180 570 B, es decir, difieren de verdad). Desde entonces `install_apks.py` y cada driver devuelven exit 4 para base y health; la fase health y los reintentos de `ui_strength_s1_s2` / `camerax_capture` quedaron `NOT_RUN` hasta recompilar los 4 APK.

## 18. Fase Wizard, tercera vuelta (2026-10-02, 13:1x-13:4x UTC): AVD reparado, instalación bloqueada por frescura

Resultado en `device-evidence\wizard\wizard-phase-r3-result.json`: `overall NOT_RUN` (los 4 APK son de 12:12-12:23 UTC; `src\main\assets|resources\exercise_catalog_v2.json` se reescribieron a las 12:56:08 UTC; `install_apks.py --dry-run` → exit 4; sin `--allow-stale`; ~20 min de espera sin recompilación).

Cambios de herramientas / entorno:

| Qué | Cambio | Motivo |
|---|---|---|
| `suites/wizard-ui.txt` | se quita `SetupInventoryTypingUiTest` (+ línea de comentario; la clase se eliminó del repositorio en el cierre del 2026-10-02, §26) | la clase es `@Ignore`: se reporta un único `SKIPPED` con `test=null` y el contrato «declarados == reportados» no se puede cumplir |
| AVD `KPKNWizchatQA` (`...\Temp\opencode\KPKNWizchatQA.avd`) | `config.ini` reconstruido a partir de `hardware-qemu.ini` (cámara back=emulated/front=none, 1080x2400 d420, 2048 MB, 2 núcleos, `google_apis_playstore` android-36 x86_64, `userdata.useQcow2=yes`) | entre las 08:39 y las 10:13 (hora local) un proceso externo borró `config.ini`, `cache.img`, `encryptionkey.img`, `userdata-qemu.img` (backing file del qcow2), `netsim.ini` y `version_num.cache`; `emulator.exe` salía con código 1 (`Failed to process .ini file ...config.ini`) |
| mismo AVD | usuario `QA10` recreado con `pm create-user QA10` (id 10) y red apagada (`svc wifi/data disable` + modo avión) | sin el backing file el emulador creó un `userdata` nuevo: el usuario 10 y la instalación anterior se perdieron; ahora el AVD queda **apagado** con QA10 (id 10), modo avión = 1 y sin paquetes `com.example.kpkn` |

Antes de reintentar: recompilar los 4 APK (después de que el catálogo esté definitivo) y seguir §16 (`start_avd.ps1 -Avd Wizard -WaitBoot`; comprobar `pm list users` → `UserInfo{10:QA10...}` y `airplane_mode_on=1`).

## 19. Pasada final de humo, flavor base, APK 13:56 UTC (2026-10-02, emulator-5580)

APK base `6143f92af774` (versionCode 34, 13:56:26Z; contiene el catálogo definitivo `6bdb9e59…`, verificado byte a byte dentro del APK) instalado con `install_apks.py --flavor base --no-test-apk` (SHA-256 origen == instalado, recibo `installs\base\20261002T140528016775Z-a51ba6`). El androidTest APK **no se reconstruyó** (sigue en 12:15:05Z, anterior a `src\main\{assets,resources}\exercise_catalog_v2.json` de 12:56:08Z): `install_apks.py --flavor base` completo devolvió exit 4 (`installs\base\20261002T135711063112Z-4e8e5d`); no se usó `--allow-stale` y el androidTest no se instaló (los drivers de esta pasada no lo usan).

| Archivo | Cambio | Motivo |
|---|---|---|
| `ui_strength_s1_s2.py` (`reopen_progress_2_of_2`, constante `SQUAT_RAIL_CARD`) | si tras reabrir no se ve 2/2 / check, se toca una vez la tarjeta del carril de la sentadilla con `contains:text:Sentadilla Trasera con Barra Baja` (única, clicable) y la aserción 2/2 sigue igual de estricta; el toque queda en `evidence.squatRailNavigation` | con la sentadilla completa el build reabre el entreno en el **siguiente ejercicio incompleto** (Floor Press, `0/2`); la sentadilla queda como tarjeta recortada en el borde izquierdo con la etiqueta truncada (`Sentadilla Trasera con Barra Baja`, sin ` · Barra`), que el selector exacto no encontraba (intento 1 FAIL, `strength-s1-s2\base\20261002T140558580006Z-b046e5-final-a1`; intento 2 PASS, `...T141647228640Z-b1355f-final-a2`) |

`ui_media_import.py` quedó NOT_RUN (exit 4, `...media-import\base\20261002T142800607030Z-175bf9-final-a1`): `src\main\java\com\example\kpkn\domain\training\SimpleCyclePersonalizer.kt` se modificó a las 14:23:25Z, después del APK (13:56:26Z). Sin `--allow-stale`.

## 20. Fase Wizard, pasada final (2026-10-02, 15:1x-15:4x UTC, `emulator-5582`, usuario 10): primera ejecución real de la suite

APK base `b0019a4ba15c` (versionCode 34, 15:04:41Z) y androidTest `36c927a50d5d` instalados para el usuario 10 con `install_apks.py --flavor base --serial emulator-5582 --user 10` (SHA-256 origen == instalado, sin `--allow-stale`). Primera vez que `suites\wizard-ui.txt` corre en un dispositivo (r2, r3 y r4 quedaron NOT_RUN por frescura).

| Archivo | Cambio | Motivo |
|---|---|---|
| `run_instrumentation.py` | `pm list instrumentation` ya no lleva `--user N`; para un usuario secundario se exige además `pm path --user N` de `com.example.kpkn` y `com.example.kpkn.test` (`installedForUser` en `metadata.json`) | en Android 16 `pm list instrumentation --user 10` devuelve `Error: Unknown option: --user`; el ejecutor concluía «runner not registered» (exit 4, intento 1) |
| `run_instrumentation.py` (`AwakeKeeper`, `--keep-awake`, implícito con `--switch-user`; `summary.json.keepAwake`) | hilo que envía `KEYCODE_WAKEUP` cada 15 s durante la corrida | el usuario 10 conserva el `screen_off_timeout` por defecto de 60 s (el usuario 0 lo tiene en 2147483647): ~60 s después de empezar el sistema entró en suspensión, volvió el keyguard y 37 de 40 pruebas fallaron con «No compose hierarchies found» (intento 3). `KEYCODE_WAKEUP` lo consume el window manager (no llega a la app) y no cambia ningún ajuste persistente |
| `wizard_evidence.py pull-captures` | distingue «Permission denied» de «vacío», registra siempre los marcadores `Q6_UI_*` (`listingPermissionDenied`, `stdoutMarkers`) antes de fallar | ni el shell ni `run-as` pueden entrar en `/storage/emulated/10` (aislamiento entre usuarios, imagen `user` sin root, no hay `/mnt/user/10`, `/mnt/pass_through/10` accesibles): los PNG de `captureQ6Screenshot` **no se pueden extraer** con adb en esta configuración aunque existan |
| `tests/test_awake_keeper.py` (nuevo) | 2 pruebas offline | cobertura de `AwakeKeeper` |

Preparación de entorno (no es cambio de herramienta, queda en `device-evidence`): primer arranque en frío del usuario 10 con 2 GB de RAM -> el proceso de instrumentación tardó 31 s en completar el arranque y `ActivityManager` lo mató por `bg anr` («failed to complete startup», intento 2, `...wizard-ui-final-a2`). Se resolvió esperando a que la carga del AVD bajara (< 4), `cmd package compile -m verify -f com.example.kpkn` y `com.example.kpkn.test` (ambos estaban en `run-from-apk`; el hash de `base.apk` no cambia) y `am kill-all` antes de lanzar (swap libre 180 MB -> 934 MB).

Resultado (evidencia `instrumentation\base\20261002T153300571577Z-82a909-wizard-ui-final-a4`): 40 declaradas = 40 reportadas, **30 PASS / 10 FAIL**.

* PASS: `WizardGateComponentsUiTest` 19/19, `WizardControlSemanticsUiTest` 2/2, `SetupWelcomeScreenUiTest` 2/2, `SetupRingsPreviewIntegrationTest` 1/1, `PostSetupEditorsUiTest` 2/2, `SetupWizardJourneyUiTest` 6/7.
* FAIL (7) `SetupWizardFullJourneyUiTest` (todas): `performScrollToIndex(75)` en `setHeightCm` (`SetupWizardFullJourneyUiTest.kt:1263`); la prueba busca el `LazyColumn` bajo `contentDescription = "Rueda de altura en centímetros"`, pero `SetupWizardSteps` elige `WizardAnthropometryLayout.Combined` cuando `WizardAnthropometryFit.fits(viewport, fontScale)` (>= ~673 dp de alto útil a fontScale 1.0) y entonces pinta altura+peso con **reglas** (`Regla de altura en …`, `SetupAnthropometryPair`), no la rueda (solo en `Separate`). El AVD 1080x2400 a 420 dpi (~914 dp) cae en Combined. Deriva prueba-producto (la prueba no maneja Combined), no defecto de producto. Como el recorrido nunca pasa de HEIGHT: 0 PNG (los marcadores muestran 2 directorios `Q6_UI_ARTIFACT_DIR` y 0 `Q6_UI_SCREENSHOT`) y ningún programa activado en Room del usuario 10.
* FAIL (1) `SetupWizardJourneyUiTest.priorityChipsWrapInANarrowViewportAtDoubleFontAndStayOperable`: tras el CTA de PRIORITIES espera `SPLIT` pero el cursor queda en `TRAINING_MAX` (`canConfirmStep=false`), el orden real de la ruta (el propio FullJourney recorre PRIORITIES -> TRAINING_MAX -> SPLIT). Expectativa obsoleta de la prueba.
* FAIL (2) `PostSetupCalendarUiTest` (ambas): `performScrollToNode` exige un nodo con `ScrollToIndex`+`ScrollBy`; el host de la prueba es `Column(verticalScroll)` (solo `ScrollBy`) y `DayView.kt` no contiene ningún `Lazy*`.

`logcat-scan` de la corrida: 0 FATAL, 0 ANR, 0 SQLiteException, 0 IllegalStateException, 168 líneas StrictMode (informativas). Android user final: 0 (restaurado por `--switch-user`); el AVD 5582 quedó apagado.

## 21. Fotogramas del display durante `am instrument` (`--screencap-every`)

| Archivo | Cambio | Motivo |
|---|---|---|
| `run_instrumentation.py` (`ScreencapRecorder`, `--screencap-every SEGUNDOS`, `--screencap-dir DIR`; `summary.json.screencap`, `metadata.json.screencapEverySeconds`) | hilo daemon en el host que, mientras corre `am instrument`, ejecuta `adb -s <serial> exec-out screencap -p` (`Avd.screenshot_bytes`, ya en la lista blanca) cada N s y guarda `device-evidence\wizard\frames\<corrida>\frame-NNNN-<UTC>.png` + `manifest.json` (tamaño y SHA-256); primer fotograma inmediato, tope **400** por corrida (`limitReached`), un fallo se cuenta (`failed`/`error`) y nunca cambia el veredicto ni bloquea la instrumentación | los PNG de `captureQ6Screenshot` quedan en el almacenamiento del usuario 10 y no se pueden extraer con adb sin root (sección 20); así hay imágenes del recorrido sin tocar el dispositivo más allá de `screencap` |
| `tests/test_screencap.py` (nuevo) | 15 pruebas offline contra `fake_device` (numeración/marca de tiempo, solo `exec-out screencap -p`, tope, fallos contados, modo offline, hilo no bloqueante, integración con `run()`, directorio por defecto y explícito) | cobertura de lo anterior (166 -> 181 pruebas con toda la suite) |

```powershell
& $py -X utf8 run_instrumentation.py --flavor base --serial emulator-5582 --user 10 --switch-user --classes-file suites\wizard-ui.txt --label wizard-ui --timeout 3600 --screencap-every 3
```

## 22. Fase Wizard, ejecución con APK 15:04Z / androidTest 16:04Z (2026-10-02, `emulator-5582`, usuario 10)

APK base `b0019a4ba15c` y androidTest `1133d6881b7f` (recompilado 13:04 hora local) instalados con `install_apks.py --flavor base --serial emulator-5582 --user 10` sin `--allow-stale` (recibo `installs\base\20261002T160629821777Z-454cde`, SHA-256 origen == instalado). Preparación de §20 aplicada (carga < 4, `cmd package compile -m verify -f` de ambos paquetes, `am kill-all`): sin ANR de arranque.

| Archivo | Cambio | Motivo |
|---|---|---|
| `avd.py` (`Avd.poke_user_activity`: `input trackball roll 0 0`) y `run_instrumentation.py` (`AwakeKeeper` ahora envía además ese evento; `summary.json.keepAwake.userActivityPokes`) | el `AwakeKeeper` de §20 **no mantenía la pantalla encendida**: `KEYCODE_WAKEUP` no restablece el temporizador de actividad de usuario de un display ya despierto (medido con `dumpsys power`: `lastUserActivityTime` seguía creciendo, 317 s; `input keyevent 0`, `trackball roll 0 0` y `KEYCODE_SYSRQ` lo dejan en ~0,9 s). En la corrida `wizard-ui-fixed` el sistema entró en `Going to sleep due to timeout (screenOffTimeout=60000)` a las 16:10:42, 16:11:47, 16:12:49 y 16:13:50 UTC (2-5 s de sueño cada vez, despertado por `WAKE_KEY`). El de 16:10:42 cayó dentro de la prueba 2 de FullJourney: la actividad quedó en STOP y `performTextInput` en NAME falló con «No compose hierarchies found» | el evento de trackball de delta cero no lleva código de tecla, ni posición, ni movimiento; no se cambia ningún ajuste. Reintento de FullJourney con el arreglo: 0 eventos `Going to sleep` / `WAKE_KEY` en 116 s |
| `tests/test_awake_keeper.py` | + comprobación de `poke_user_activity` en cada ciclo | cobertura |

Resultado: ver `device-evidence\instrumentation\base\20261002T160930029740Z-c62c7d-wizard-ui-fixed` (40 declaradas = 40 reportadas, 33 PASS / 7 FAIL) y `...20261002T162052312218Z-2f8b1e-wizard-fulljourney-retry-awakefix` (FullJourney 0/7). Los 7 fallos de FullJourney son el mismo: `ensureVisible` busca «No lo sé» en BODY_FAT (`SetupWizardFullJourneyUiTest.kt:497/1230/1625`) y el producto ya pinta `WizardPhysiqueSelector` (figura + slider + «Tengo una medición exacta») con «Continuar» habilitado, sin esa opción (`SetupBodyFatControl`, `SetupBasicSteps.kt:285`). Fotogramas clave en `device-evidence\wizard\key-frames\`.

## 23. Fase Wizard, FullJourney con la prueba corregida (2026-10-02, `emulator-5582`, usuario 10)

APK base `b0019a4ba15c` (15:04:41Z) y androidTest `c93c5602f105` (16:30:34Z = 13:30 local, recompilado tras corregir BODY_FAT) instalados con `install_apks.py --flavor base --serial emulator-5582 --user 10` sin `--allow-stale` (recibo `installs\base\20261002T163324563871Z-04d340`, SHA-256 origen == instalado, el androidTest anterior era `1133d6881b7f`). Preparación de §20 aplicada después de instalar (carga < 4, `cmd package compile -m verify -f` de ambos paquetes, `am kill-all`): sin ANR de arranque.

| Archivo | Cambio | Motivo |
|---|---|---|
| `wizard_evidence.py` (`room-check --since-utc ISO [--expect-receipts N]`, `parse_since_utc`, `select_run_receipts`) | los recibos anteriores a `--since-utc` se listan (`receiptsOlderThanRun`, con `programStillInRoom`) pero no se juzgan; con `--expect-receipts` exige el número exacto de activaciones de la corrida; sin las opciones el comportamiento es el de §16 | la BD del usuario 10 la comparten todas las clases del wizard: `PostSetup*` / `WorkoutWarmup*` dejan 8 recibos `m9-psu-*` cuyos programas borran después, y `room-check` culpaba a FullJourney («receipt m9-psu-22f20a5f-... points at a program that is not in Room») |
| `tests/test_room_check_receipts.py` (nuevo) | 5 pruebas offline | cobertura de lo anterior (181 -> 186 con toda la suite; las 18 de `test_room_check_receipts`, `test_user_support` y `test_awake_keeper` pasan, `check_names` ok) |

Resultado (evidencia `instrumentation\base\20261002T163448551976Z-32ae48-wizard-fulljourney-bodyfat-skip`, fotogramas `wizard\frames\<corrida>`, clave en `wizard\key-frames-fulljourney`): 7 declaradas = 7 reportadas, **0 PASS / 7 FAIL**, pero el recorrido ya pasa BODY_FAT y llega hasta la activación en 5 de 7 pruebas. Los fallos son ahora otros dos, deterministas (también en el reintento de E0 con `--screencap-every 1`, `...20261002T164419234102Z-f099be-wizard-fulljourney-e0-dense-frames`):

* `performScrollTo()` sobre «Tus Programas» (`SetupWizardFullJourneyUiTest.kt:938`, 5 pruebas: TrackingOnly x2, E0, Automatic x2): `HomeScreen` es un `LazyColumn` y «Tus Programas» está en `item(key = "programs")`, el cuarto ítem, debajo de sesión, rings y tarjetas; fuera de la ventana no se compone, así que no hay nodo al que desplazar. La activación sí ocurrió antes (`Committed`, una sola navegación, 0 cancelaciones). Deriva prueba-producto: debe usar `performScrollToNode`/`performScrollToKey("programs")` sobre la lista.
* `assertInTree("150 g proteína")` sin `substring = true` (`:749`, 2 pruebas SelfDefined): el resultado de nutrición pinta un solo `Text` «Referencia diaria: 2100 kcal · 150 g proteína · … g hidratos · … g grasas» (`SetupNutritionSteps.kt:690`); la coincidencia exacta nunca casa. Mismo defecto probable en `:878` (revisión), no alcanzado.

`room-check --since-utc 2026-10-02T16:34:48Z --expect-receipts 5` (y `6` tras el reintento): PASS; 5 programas activados con semanas/sesiones/ejercicios y procedencia (`structureTemplateId` `native:powerbuilding-foundation-v2` x2, `native:muscle-foundation-v2`, `coan-phillipi-dl` x2 con `sourceProtocolId`), 2 planes nutricionales `calculationOrigin=PLAN` (EER-2023) solo en las pruebas automáticas, `active_program` apunta al último. `logcat-scan`: 0 FATAL, 0 ANR, 0 SQLiteException, 0 IllegalStateException (StrictMode informativo). Los PNG de `captureQ6Screenshot` (16 anunciados) siguen sin poder extraerse (§20); los fotogramas de pantalla los sustituyen.

## 24. Fase Wizard, FullJourney final (2026-10-02, 17:0x-17:2x UTC, `emulator-5582`, usuario 10): sin cambios de herramientas

APK base `b0019a4ba15c` (sin cambios en el dispositivo) y androidTest `0c4b20d8e3b5` (13:52 local = 16:52:46Z; el anterior era `c93c5602f105`) instalados con `install_apks.py --flavor base --serial emulator-5582 --user 10` sin `--allow-stale` (recibo `installs\base\20261002T170104008789Z-b2c149`, SHA-256 origen == instalado). Preparación de §20 aplicada después de instalar. **No hizo falta ningún cambio de herramientas.**

Resultado (`wizard\wizard-phase-fulljourney-final-result.json`): `instrumentation\base\20261002T170408814983Z-58886f-wizard-fulljourney-final` = 7 declaradas = 7 reportadas, **0 PASS / 7 FAIL**, todas en el mismo punto: `SetupWizardFullJourneyUiTest.kt:943 -> scrollHomeTo (:1667)`: `performScrollTo()` sobre «Tus Programas» (`ignoreCase: false`). Los dos defectos de la pasada anterior quedaron corregidos en dispositivo (los 2 SelfDefined pasan `:751`/`:881` y llegan al Home; los otros 5 siguen llegando a `Committed`).

Causa raíz (defecto de PRUEBA, corrige el diagnóstico de §23): `HomeProgramsSection.kt:36` llama `SectionHeader("Tus Programas")` y `SectionHeader` (`ui/components/SharedComponents.kt:44`) pinta `title.uppercase()`; el nodo semántico es «TUS PROGRAMAS» y `hasText("Tus Programas")` distingue mayúsculas, así que el desplazamiento llega a la fila de programas (fotogramas) pero la coincidencia nunca ocurre. Además el bucle recorre también el `LazyRow` de programas y deja cortado a la izquierda el primer card (el activo), por lo que `:945 assertIsDisplayed("ACTIVO")` probablemente fallará después; el encabezado además es negro sobre negro en el arnés (sin `Surface`). Tras corregirlo, `:1188-1192` probablemente falle por el snapshot inmutable de hoy (`daily_goal_snapshots[2026-10-02]`, `planId = m9-psu-183f3941-plan`, 1800 kcal) que dejaron las clases `PostSetup*` en la BD compartida del usuario 10.

Un reintento dirigido (`...20261002T171346379925Z-2b6341-wizard-fulljourney-final-selfdefined-den`, solo `fiveDayPowerbuildingSelfDefinedActivatesNativeProgramAndManualTargets`, `--screencap-every 1`) reprodujo el mismo fallo. `wizard_evidence.py room-check --since-utc 2026-10-02T17:04:08Z --expect-receipts 7` (8 tras el reintento): PASS; 7 programas activados con semanas/sesiones/ejercicios y procedencia (`native:powerbuilding-foundation-v2` x3, `native:muscle-foundation-v2`, `coan-phillipi-dl` x3 con `sourceProtocolId`), 2 `nutrition_plans` `calculationOrigin=PLAN` (2859 kcal) y 2 `MANUAL` (2100 kcal, SelfDefined), 3 solo registro sin plan. `logcat-scan`: 0 FATAL / 0 ANR / 0 SQLiteException / 0 IllegalStateException. Esto **no sustituye** a `assertCommittedRoom` (no se ejecutó). Fotogramas nuevos 29-39 en `wizard\key-frames-fulljourney`.

## 25. Fase Wizard, FullJourney última tanda (2026-10-02, 17:3x-17:4x UTC, `emulator-5582`, usuario 10)

APK base `b0019a4ba15c` (15:04:41Z, sin cambios en el dispositivo) y androidTest `03b329c84811` (17:25:06Z = 14:25 local; el anterior era `0c4b20d8e3b5`) instalados con `install_apks.py --flavor base --serial emulator-5582 --user 10` sin `--allow-stale` (recibo `installs\base\20261002T173252070341Z-f143fe`, SHA-256 origen == instalado). Preparación de §20 aplicada después de instalar (carga 0,49, `cmd package compile -m verify -f` de ambos paquetes, `am kill-all`): sin ANR de arranque.

| Archivo | Cambio | Motivo |
|---|---|---|
| `start_avd.ps1` (bucle `-WaitBoot`) | la llamada `adb shell getprop sys.boot_completed` va dentro de `try`/`catch`; un fallo de adb se trata como «aún no arrancó» | con `$ErrorActionPreference = 'Stop'` el stderr de adb durante el arranque (`error: device offline`) es un `NativeCommandError` terminante: el script murió a mitad del arranque (el emulador siguió vivo), no escribió el `.launch.json` de ese arranque y no imprimió nada (los logs `avd-wizard-host-windowed-20261002-142548.*.log` existen, el `.launch.json` no). Reproducido con un adb falso en Windows PowerShell 5.1 (patrón viejo: lanza; nuevo: «not yet») |
| `tests/test_start_avd_boot_wait.py` (nuevo) | 3 pruebas offline (poll dentro de `try`/`catch`, `Stop` y CRLF conservados, patrón contra adb falso `device offline`) | cobertura de lo anterior (`check_names` ok) |

Resultado (`wizard\wizard-phase-fulljourney-last-result.json`; evidencia `instrumentation\base\20261002T173343768462Z-5301a9-wizard-fulljourney-last`, 78 fotogramas en `wizard\frames\<corrida>`): 7 declaradas = 7 reportadas, **0 PASS / 7 FAIL**, las 7 en `SetupWizardFullJourneyUiTest.kt:948` (`onNodeWithText("ACTIVO").assertIsDisplayed()`). Por primera vez se ejecutaron `:944 scrollHomeTo("Tus Programas")` y `:945 assertIsDisplayed("Tus Programas")` (PASS, con `ignoreCase`) y `:947` (envuelto en `runCatching`). `:950-:954` (nombre del programa en el Home, captura `home-active-program`, `assertCommittedRoom`) siguen sin ejecutarse.

Causa raíz probable (defecto de PRUEBA): en `androidx.compose.ui:ui-test` 1.10.0 `checkIsDisplayed` devuelve `false` cuando el selector no casa con ningún nodo (bytecode de `AndroidAssertions_androidKt.checkIsDisplayed`: `selectedNodes.isEmpty() -> return false`), de modo que el mensaje «is not displayed!» también aparece si la tarjeta no está compuesta. La fila de programas del Home es un `LazyRow` ordenado por nombre al cargar y con los programas nuevos **añadidos al final** (`ProgramRepository.kt:276-283`, `list + program`); la BD compartida del usuario 10 ya tenía 14 programas, así que el programa recién activado es la tarjeta 15..21 (índice 14..20, ~188 dp por tarjeta) y no está compuesta; `performScrollTo()` no puede desplazar un nodo inexistente y `runCatching` (`:947`) oculta el error. Pendiente de confirmar con un fotograma de la fila (los 3 s de intervalo solo capturaron el Home una vez, en su primera composición).

`wizard_evidence.py room-check --since-utc 2026-10-02T17:33:43Z --expect-receipts 7`: PASS (7 recibos de la corrida, programas con semanas/sesiones/ejercicios, `active_program` = último). `logcat-scan`: 0 FATAL / 0 ANR / 0 SQLiteException / 0 IllegalStateException (168 líneas StrictMode informativas). Esto **no sustituye** a `assertCommittedRoom` (no se ejecutó).

## 26. Cierre 2026-10-02 (paquete A): D1.4 prueba de inventario eliminada y D1.3 calentamiento en QA10

### D1.4 — `SetupInventoryTypingUiTest` eliminada

La clase probaba el tecleo («20.» perdía el punto) en el subeditor de inventario con pesos (barra, kettlebells), una pantalla que ya no forma parte del asistente (`SetupWizardModels.kt` devuelve `inventoryGroups() = emptySet()` y ninguna pantalla se asocia a `INVENTORY_PICKER`). Estaba en `@Ignore` y su propio aviso pedía retirarla en una limpieza posterior.

| Archivo | Cambio | Motivo |
|---|---|---|
| `android-native/app/src/androidTest/java/com/example/kpkn/screens/onboarding/SetupInventoryTypingUiTest.kt` | **movido** (no borrado) a `artifacts/consolidation-20261001/closeout/deleted/android-native/app/src/androidTest/java/com/example/kpkn/screens/onboarding/SetupInventoryTypingUiTest.kt` | prueba obsoleta; git además conserva la versión confirmada |
| `suites/all-android-tests.txt`, `suites/all-android-tests-base-r2.txt` | se quita la clase (`all-android-tests.txt` queda en 38 clases; r2, en 33) | `tests/test_suite_plan.py` exige que toda entrada de toda suite exista y que `all-android-tests.txt` + las dos clases especiales cubran exactamente el árbol androidTest |
| `suites/all-android-tests-base-r3.txt`, `suites/wizard-ui.txt` | solo comentarios de cabecera: dejan de nombrar la clase (r2 y r3 listan ahora las mismas 33 clases) | la clase ya no existe |

Las menciones históricas de las secciones 14, 17 y 18 se conservan (son el registro de lo que pasó entonces) y llevan una marca que remite a esta sección. Las listas de `suites\` ya no contienen la clase en ningún sitio.

### D1.3 — `suites/warmup-qa10.txt`: `WorkoutWarmupInventoryIntegrationTest` (3 pruebas)

Suite nueva con una sola clase, `com.example.kpkn.screens.workout.WorkoutWarmupInventoryIntegrationTest`. Sus tres pruebas son `declared_bar_inventory_is_consumed_and_resolves_real_40_60_80`, `explicit_inventory_without_bar_returns_pending_not_a_phantom_20` y `dumbbell_kind_from_catalog_drives_the_declared_pair_stock`: comprueban que, con barra o mancuernas declaradas en `Settings.equipmentInventory`, el peso de calentamiento sale con números reales (y que sin barra declarada queda «pendiente», no un 20 inventado).

Por qué se omiten hoy: la clase tiene dos guardas `Assume` en `setUp` (`WorkoutWarmupInventoryIntegrationTest.kt`, ~l.134-155): (1) `Process.myUid() / 100000 == 10`, es decir, solo corre como usuario QA 10; (2) que QA no tenga un entrenamiento en curso en Room. Si falla cualquiera, las pruebas quedan `SKIPPED`.

* **Dónde se ejecuta: `emulator-5582`** (AVD `KPKNWizchatQA`, que ya trae el usuario `QA10`, id 10; ver §16 y §18), con `--user 10 --switch-user`, **en el mismo arranque del emulador que las pruebas largas del asistente** (`suites\wizard-ui.txt`, ítem D1.1), para no arrancarlo dos veces.
* **En `emulator-5580` (AVD de auditoría) las 3 quedan `SKIPPED`: es lo esperado, no un fallo.** Así las reportan `workout-editor-ui` (36 PASS + 3 SKIPPED, §14 y §15) y `all-android-tests`. **No se crea QA10 en el 5580.**
* Antes de empezar, con el directorio y `$py` de la sección 4: `& $py -X utf8 avd.py --serial emulator-5582 users` debe incluir el usuario `{"id": 10, "name": "QA10", ...}`, y el modo avión debe seguir activo (`airplane_mode_on=1`). El AVD 5582 vive en una carpeta temporal y ya se vació una vez (§18): si falta el usuario, no se ejecuta nada y se avisa.

```powershell
.\start_avd.ps1 -Avd Wizard -WaitBoot                  # solo si está apagado
& $py -X utf8 avd.py --serial emulator-5582 users
& $py -X utf8 install_apks.py --flavor base --serial emulator-5582 --user 10      # solo si los APK cambiaron
& $py -X utf8 run_instrumentation.py --flavor base --serial emulator-5582 --user 10 --switch-user --classes-file suites\warmup-qa10.txt --label warmup-qa10
& $py -X utf8 avd.py --serial emulator-5582 stop --yes                            # al terminar todo lo del 5582
```

Resultado esperado: 3 declaradas = 3 reportadas, 3 PASS. Si salen `SKIPPED`, el proceso no corrió como usuario 10 (falta `--user 10 --switch-user`) o QA10 tiene un entrenamiento en curso. Incógnita del plan de cierre: si la imagen del AVD del asistente tiene motor TTS (la prueba construye un `WorkoutViewModel` real); si falla por eso, se anota el mensaje y se trata como hallazgo del entorno, no de la suite.

Estado de esta entrega: la suite y esta sección son lo único que se hizo en el paquete A; **no se ejecutó ningún emulador** (lo hace quien coordina las pruebas del 5582). `tests/test_suite_plan.py` valida que la clase existe y declara `@Test`.
