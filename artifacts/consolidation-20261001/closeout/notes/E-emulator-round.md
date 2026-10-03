# E-emulator-round: ronda de emulador del cierre (2026-10-02, solo flavor base)

Dispositivo: `emulator-5582` (AVD `KPKNWizchatQA`, usuario QA10 id 10, modo avion = 1). Horas: arranque 17:59 local, apagado 19:59 (no se ejecuto nada entre las 20:55 y las 21:05). El reloj del emulador es UTC.
APK instalados con `install_apks.py --flavor base --serial emulator-5582 --user 10` SIN `--allow-stale` (guardia de frescura OK): APK `afafdf962713` (17:58 local) y androidTest `b46cf50728fb`, SHA-256 origen == instalado (recibo `device-evidence/installs/base/20261002T210133357953Z-f2ba2a`). Mitigacion s20 aplicada DESPUES de instalar (`cmd package compile -m verify -f` de ambos paquetes + `am kill-all`, carga < 4).
Evidencia: `device-evidence/closeout/` (key-frames con `manifest.json`, capturas manuales en `manual/`, room-check, logcat-scan, `offline-tests.txt`) y `device-evidence/instrumentation/base/...` (indicadas abajo).

## Veredicto

**PASS con 1 defecto de producto (medio) y varios hallazgos menores.** Todo lo que se pidio verificar en dispositivo se ejecuto; lo que no se pudo se marca NOT_RUN. Ninguna falla de pruebas instrumentadas.

| # | Item | Resultado |
|---|---|---|
| 1 | Arranque AVD, usuarios 0 y QA10, airplane_mode_on=1, instalacion sin `--allow-stale` | PASS |
| 2 | Suite `wizard-ui` completa (40 pruebas) | PASS 40/40, 270 s (corrida `...211110821920Z-c0b916-closeout-wizard-ui-a2`). Un primer intento (`...210431624920Z-799a89`) quedo ABORTADO por entorno (ver «Entorno») |
| 2a | `SetupWizardFullJourneyUiTest` 7 recorridos + `assertCommittedRoom` (primera ejecucion real) | PASS 7/7 dos veces (a2 y la repeticion `...215631591234Z-e68a9b`, 254,7 s, 127 fotogramas sin el dialogo ANR). lo previsualizado == lo guardado: nombre del programa, sesiones/ejercicios, kcal, cache de metas diarias, meta del Home |
| 2b | Paso de grasa corporal (Continuar bloqueado hasta accion; Omitir limpia) | PASS (aserciones de FullJourney + fotograma `key-frames/WIZ-01.png`: estado «Omitido», Continuar habilitado) |
| 2c | Textos en singular | PASS (`key-frames/SING-01.png`: «1 día», «2 días»; «Elige 5 días · 0 de 5 elegidos») |
| 2d | `WizardGateComponentsUiTest` | PASS 17/17 |
| 2e | Resto de la suite | `SetupWizardJourneyUiTest` 7/7, `PostSetupCalendarUiTest` 2/2, `PostSetupEditorsUiTest` 2/2, `SetupWelcomeScreenUiTest` 2/2, `SetupRingsPreviewIntegrationTest` 1/1, `WizardControlSemanticsUiTest` 2/2 |
| 3a | `warmup-qa10` (`WorkoutWarmupInventoryIntegrationTest`) | PASS 3/3 EJECUTADAS (no SKIPPED), 14,2 s (`...212311109496Z-37c494`) |
| 3b | `SetExecutionCardManagedLoadUiTest` | PASS 1/1 (`...212352549025Z-9ae62f`) |
| 3c | `PostSetupEditorsUiTest` | PASS 2/2 (dentro de la suite) |
| 3d | Suites + tests offline de `tools/avd` | `SetExecutionCardManagedLoadUiTest` anadida a `all-android-tests.txt`, `-base-r2`, `-base-r3` y `workout-editor-ui.txt`; `tests.test_suite_plan` 10/10 OK; tests offline completos 189 OK + `check_names` ok (`offline-tests.txt`) |
| 4a | D2.2 boton «Registrar serie» sobre el teclado | PASS (ver abajo) |
| 4b | D2.1 aviso ambar y «Seguir con ellas» | PASS (caso «nada pendiente» y «ejercicio saltado» NOT_RUN en dispositivo; cubiertos por JVM) |
| 4c | H-UI / H-VERIF progresion | PASS parcial (ver abajo) |
| 4d | C2 meta diaria | PASS |
| 4e | Aviso/nota de revision sin jerga; tiempos de `SetupPlanSweep` | PASS con observaciones |
| 5 | `room-check` y `logcat-scan` | PASS (ver abajo) |
| 6 | Restauracion y apagado | `wm size/density reset` (1080x2400 / 420), usuario 0 actual, AVD apagado con `avd.py stop --yes`; QA10 y modo avion intactos |

## Detalle por prueba

### 4a. D2.2 (fixture `strength-empty-v27`, usuario 10)
- Teclado numerico abierto (campo «Carga (kg)»): el boton redondo queda en `[886,1449][1038,1601]`, por ENCIMA del teclado (que empieza en y~1632); animado, entero y tocable. `key-frames/D2.2-01.png`.
- Un toque con el teclado abierto registra la serie (20 kg x 6, aparece el descanso); no cae en una tecla (`D2.2-02.png`). Back con teclado abierto solo cierra el teclado, no abre «¿Qué deseas hacer?» (`D2.2-03.png`).
- Pantalla pequena `wm size 720x1280` + `wm density 240`: el boton sigue encima del teclado y no tapa el campo de carga; registra la serie 2 (22,5 kg x 6) (`D2.2-04/05.png`). Hallazgo menor: sobre esa pantalla el boton solapa parte del ultimo valor de la rueda RIR («0»). `wm` restaurado al final.

### 4b. D2.1 (fixture `cardio-two-series-ui-v27`, usuario 10)
Cardio primero con fuerza pendiente: se registraron las dos series de cardio (GPS iniciado/pausado/finalizado, sin alimentador de coordenadas, 0,00 km) y la tarjeta de sensaciones; la hoja «RESUMEN DE ENTRENAMIENTO» muestra la tarjeta ambar «Te quedan 5 series sin hacer: Floor Press · Barra (2), Remo Convencional · Barra (2), Crunch Abdominal en Suelo (1).» con «Seguir con ellas» (`D2.1-01.png`). Al tocarlo, la hoja se cierra y el cursor queda en «Serie 1 de 2 de Floor Press» (el primer paso pendiente) (`D2.1-02.png`). Variante «Terminar hasta acá» en otra sesion: el aviso cuenta los ejercicios ANTERIORES sin hacer («Te quedan 9 series sin hacer: Press de Banca (3), Remo (3), Press Militar (3).», `D2.1-03.png`). NOT_RUN en dispositivo: sesion completa sin aviso y ejercicio saltado a proposito.

### 4c. H-VERIF (programa `Fuerza y músculo KPKN`, creado por el asistente en QA10; ejercicio Curl de Bíceps de Pie, 2 series, 10-15 reps, en Día 1 y Día 4)
- Contraprueba sin mover la reserva (Día 4 sem. 1, ambas series con el selector sin tocar): NO genera propuesta. Una serie con RIR 2 < 3 del plan (Día 1 sem. 1) tampoco cuenta.
- Mover el selector de reserva y volver al valor del plan (Día 1 sem. 2: serie 1 verificada con el selector de vuelta en 3, serie 2 no verificada; Día 1 sem. 3: plan 2, movido a 2 y a 3, verificado) cuenta como ajustado; la inferencia de que RIR menor que el plan y el selector sin tocar no cuentan sale de que no aparecio propuesta antes de la segunda sesion valida (no se inspecciono Room): tras la segunda exposicion valida aparece en el detalle del programa (pestana Semana) la cabecera «1 progresión por revisar», la tarjeta con el NOMBRE «Curl de Bíceps de Pie», texto «Lo hiciste dos veces con 15 reps en todas las series. Propuesta: en el próximo entrenamiento elige una carga un poco mayor y deja las mismas repeticiones en reserva.» y los botones APLICAR / RECHAZAR (`H-VERIF-01/02.png`). Despues de la sesion con una sola exposicion valida no habia propuesta.
- APLICAR: la propuesta desaparece. La insignia «N progresiones por revisar» en las pestanas Estructura/Volumen NO se comprobo; el snackbar «Listo: ...» y la confirmacion al aplicar no se alcanzaron a capturar (NOT_RUN); RECHAZAR NOT_RUN.
- Sesion siguiente (Día 4 sem. 3): pide «Elige una carga para 10–15 reps dejando 2 en reserva y registra la serie.» con «Carga a elegir» (antes de aplicar, Día 1 sem. 3 venia con la carga anterior 10 prerellenada) (`H-VERIF-03.png`). La nota P2 esperaba «subir de X a Y kg»; el producto no muestra un numero de kg (la tarjeta de referencia dice «Cargas de referencia: 5 pendientes de resolver»). Se reporta como diferencia con la nota, no como falla.
- El programa esta en estado «Borrador» (los programas del asistente en QA10 no se activaron con «Activar programa») y aun asi la progresion funciona.

### 4d. C2 meta diaria
- En QA10 se activaron cuatro planes nutricionales el mismo dia (2100, 2100, 2859, 2859 kcal); Room: `daily_goal_snapshots[2026-10-02]` pertenece al ultimo plan activado (2859 kcal, `planId` 298a7935...), y el Home muestra «2859 kcal meta» (`C2-01.png`). Las aserciones estrictas de `assertCommittedRoom` (meta de hoy del plan nuevo, cache republicada) pasaron en los 7 recorridos.
- Editor del mismo plan: «La meta de hoy ya quedó fijada en 2859 kcal. Lo que cambies en este plan vale desde mañana.» (`C2-02.png`).

### 4e. Revision del asistente y SetupPlanSweep
Textos de revision sin jerga en las capturas (`WIZ-02/03.png`). El aviso de «volumen alto en glúteos» NO se alcanzo a ver (ningun recorrido genero informe con exceso): NOT_RUN en dispositivo.
Tiempos (`adb logcat -s SetupPlanSweep`, ms; 1 barrido por recorrido; el primero incluye la carga fria del catalogo):

| Recorrido | Corrida a2 total/barrido | Repeticion total/barrido | publicados/viables |
|---|---|---|---|
| T1 Powerbuilding solo registro (catalogo frio) | 11 879 / 2 214 (catalogo 9 665) | 13 691 / 2 598 (catalogo 11 093) | 4/1 |
| Unificado manual | 951 / 951 | 1 550 / 1 550 | 2/2 |
| Unificado solo registro | 1 226 / 1 226 | 2 136 / 2 136 | 2/2 |
| Powerbuilding manual | 2 100 / 2 100 | 2 335 / 2 336 | 4/1 |
| E0 peso corporal | 2 706 / 2 706 | 4 049 / 4 049 | 8/6 |
| Powerbuilding automatico | 862 / 862 | 3 279 / 3 279 | 4/1 |
| Unificado automatico | 186 + 933 | 1 401 | 2/2 |
Maximo de un barrido sin catalogo: 4,0 s (E0, emulador 2 GB y 2 nucleos con otra compilacion corriendo en el host); con catalogo frio 13,7 s. `adaptado=false`, `pases=1` en todos.

### 5. Room y logcat
- `wizard_evidence.py room-check --since-utc 2026-10-02T21:12:00Z --expect-receipts 7 --ignore-commit-prefix m9-psu- --ignore-commit-prefix 7faa0950`: PASS (7 programas con semanas/sesiones/ejercicios: 6 sem./30 ses./108 ej. x3, 6/18/90, 10/10/60 x3; `active_program` apunta a uno activado). Repeticion: `--since-utc 2026-10-02T21:56:28Z --expect-receipts 7`: PASS. Hizo falta anadir `--ignore-commit-prefix` a la herramienta (ver «Defectos de herramienta»).
- `logcat-scan` de cada corrida: wizard-ui a2 0 FATAL / 0 ANR / 0 SQLite / 0 IllegalState (56 StrictMode informativos); warmup 0/0/0/0 (0 StrictMode); managed-load 0/0/0/0; FullJourney repeticion 0/0/0/0 (9 StrictMode). Logcat de la sesion manual completa (`logcat-manual-round.txt`): 0 FATAL, 0 ANR de com.example.kpkn, 0 SQLiteException.

## Defectos

### Producto
1. **(medio) Mensaje tecnico en ingles y terminar entreno imposible hasta reiniciar la app.** Al terminar un entreno aparece el dialogo «The coroutine scope left the composition» con «Cerrar» (`key-frames/DEF-01.png`, `DEF-02.png`). Reproducido 4 veces en la sesion: (a) al salir de la pantalla (deeplink a otra pantalla) con el guardado en curso; y (b) tras eso, cada nuevo «Terminar hasta acá» en el mismo proceso falla al instante con el mismo mensaje (3 veces, incluso en entrenos nuevos); no se pierde nada: la sesion queda en curso, y tras `force-stop` y «Reanudar sesión» la hoja se abre y guarda bien. Causa raiz probable [C por lectura]: `screens/workout/WorkoutFinishController.kt:540` captura `Exception` (incluye `CancellationException`, cuyo mensaje es el de Compose «The coroutine scope left the composition») y `:593` lo muestra tal cual en `finishWarning`; el guardado vive en un scope ligado a la composicion, que queda cancelado para los siguientes intentos. Arreglo propuesto: relanzar `CancellationException` antes de poner `finishWarning`, ejecutar el guardado en un scope que sobreviva a la pantalla (el del ViewModel) y usar un texto en espanol («No se pudo guardar la sesión. Inténtalo de nuevo.») en lugar de `error.message`.
2. (bajo) Pantalla pequena (720x1280, d240) con teclado: el boton de registrar solapa parcialmente el ultimo valor de la rueda RIR.
3. (bajo) Paso de grasa corporal: tras «Omitido» sigue visible la linea «≈ 25 % de grasa corporal (estimación visual)» de la figura, junto al aviso «No se guardará tu grasa corporal».
4. (bajo) Fecha de Nutricion mezcla idiomas con el telefono en ingles: «Friday, 2 de October».
5. (info) Aviso rojo escueto en la revision del catalogo autorado: «La receta fija programa Domingo.».
6. (info) Una sesion con cardio muestra en cabecera el nombre del ultimo ejercicio de fuerza visitado y no el de cardio; el mensaje de ayuda del primer paso decia «serie 1/2, 2 hechas» (no verificado si es texto correcto). Sin investigar.

### Prueba
Ninguno. Los 7 recorridos de FullJourney y `assertCommittedRoom` pasan en 2 de 2 ejecuciones; no hace falta cambiar ninguna prueba.

### Entorno / herramienta
- Entorno: con la compilacion Gradle de otro proceso corriendo, el host (1,4 GB libres, 74 % CPU) llevo la carga del AVD a 30 y SystemUI mostro «System UI isn't responding» en casi todos los fotogramas de la corrida a2 (no afecto a las aserciones de Compose; `logcat-scan` no cuenta ANR de `com.android.systemui`). En el arranque en frio del usuario 10, un `adb` se desconecto unos segundos.
- Herramienta (corregido): `run_instrumentation.py` revienta con `RuntimeError` en `avd.logcat_dump` si `adb` esta caido al terminar (primer intento, evidencia sin `summary.json`) y deja la instrumentacion huerfana corriendo en el dispositivo (hubo que `am force-stop --user 10` de ambos paquetes) y el usuario 10 en primer plano (no restauro el 0). No se toco; se reintento tras estabilizar la carga.
- Herramienta (corregido): `wizard_evidence.py room-check` juzgaba recibos de `PostSetup*` dentro de la misma ventana (recibos `m9-psu-*` y 1 de editor cuyos programas se borran). Se anadio la opcion repetible `--ignore-commit-prefix` (cambio minimo en `tools/avd/wizard_evidence.py`; probado: 189 tests offline OK).
- Mia: dos repeticiones de FullJourney (`...214848...`, `...215428...`) fallaron porque yo habia dejado un entreno en curso (fixture cardio / `normal-v27`, que trae «Sesión en curso»): la puerta del producto respondio correctamente «Termina la sesión en curso antes de activar otro plan.». Se resolvio sembrando una copia de `normal-v27` sin `ongoing_workout` (solo en QA10) y repitiendo: PASS 7/7. No es defecto del producto (mensaje claro).

## Notas
- Todos los datos tocados son de QA10 (id 10); el usuario 0 no se limpio. QA10 quedo con datos de prueba (programas del asistente, entrenos de Curl).
- No se edito ningun fuente bajo `android-native\app\src`; Gradle no se ejecuto; git solo lectura. Cambios en `tools/avd`: `suites/{all-android-tests,all-android-tests-base-r2,all-android-tests-base-r3,workout-editor-ui}.txt` (+ `SetExecutionCardManagedLoadUiTest`) y `wizard_evidence.py` (`--ignore-commit-prefix`).
