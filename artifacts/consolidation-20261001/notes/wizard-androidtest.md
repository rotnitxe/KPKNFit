# wizard-androidtest — deriva prueba-producto de la primera ejecucion real (AVD KPKNWizchatQA, usuario 10)

Fecha: 2026-10-02. Solo edicion de androidTest y de herramientas de QA; no se ejecuto Gradle, adb ni emulador, y no se toco
`android-native/app/src/main`. Compilacion Kotlin hecha mentalmente (Grep de cada simbolo, lectura del producto); las herramientas
Python SI se validaron (py_compile + 181 pruebas offline OK, `check_names: ok`).

Evidencia de partida: `device-evidence/instrumentation/base/20261002T153300571577Z-82a909-wizard-ui-final-a4/` (40 declaradas, 30 PASS / 10 FAIL).

## Archivos cambiados

| Archivo | Cambio |
|---|---|
| `android-native/app/src/androidTest/java/com/example/kpkn/screens/onboarding/SetupWizardFullJourneyUiTest.kt` (CRLF conservado) | (1) soporte de los dos layouts de altura/peso |
| `android-native/app/src/androidTest/java/com/example/kpkn/screens/onboarding/SetupWizardJourneyUiTest.kt` (LF) | (2) destino del CTA de PRIORITIES = TRAINING_MAX |
| `android-native/app/src/androidTest/java/com/example/kpkn/screens/onboarding/PostSetupCalendarUiTest.kt` (LF) | (3) scroll valido para `Column(verticalScroll)` |
| `artifacts/consolidation-20261001/tools/avd/run_instrumentation.py` | (4) `ScreencapRecorder`, `--screencap-every`, `--screencap-dir` |
| `artifacts/consolidation-20261001/tools/avd/tests/test_screencap.py` (nuevo) | 15 pruebas offline con `fake_device` |
| `artifacts/consolidation-20261001/tools/avd/README.md` | seccion 21 (uso de `--screencap-every`) |

## (1) SetupWizardFullJourneyUiTest: paso HEIGHT en SEPARATE y COMBINED

Causa (confirmada leyendo el producto): `SetupWizardSteps.kt` l.94-119 + `WizardScaffold.currentAnthropometryLayout()` eligen
`WizardAnthropometryLayout.Combined` si `WizardAnthropometryFit.fits(viewport, fontScale)` (>= 673 dp de hueco a fontScale 1.0, el AVD
de ~914 dp lo cumple). En Combined `SetupBasicSteps.SetupAnthropometryPair` pinta `WizardHeightRule` ("Regla de altura en centimetros",
tag `setup-height-rule-value`) y `WizardWeightRule` ("Regla de peso en kilogramos", tag `setup-weight-value`) en el mismo paso HEIGHT; no
existe "Rueda de altura...". El CTA unico llama a `vm.submitAnthropometryPair()` (solo habilitado con `draft.weightKg != null`): confirma
HEIGHT y WEIGHT a la vez y el cursor salta HEIGHT -> EQUATION_SEX (nunca se muestra el paso WEIGHT).

Cambios:
- `walkBasics`: tras AGE -> HEIGHT, `awaitAnthropometryLayout()` detecta el layout por lo que HAY en pantalla (solo rueda => SEPARATE, solo regla =>
  COMBINED; ambas o ninguna = no estable => timeout con diagnostico). Exige que el resultado lleve estable 750 ms (`LAYOUT_SETTLE_MS`): el hueco depende de
  insets/IME y puede asentarse tras componer el paso (defensivo, no es un fallo observado). Escribe `WIZARD_ANTHROPOMETRY_LAYOUT=<layout>` en stdout y logcat
  (tag `KPKN_Q6_UI`) para que la evidencia diga que layout recorrio cada prueba.
  - SEPARATE: igual que antes (`HEIGHT -> WEIGHT` con la rueda, `WEIGHT -> EQUATION_SEX` con la regla de peso).
  - COMBINED: `answerAndContinue(HEIGHT, EQUATION_SEX)` con `setHeightCm(..., COMBINED)` (regla de altura) y `setWeightKg` (misma regla de peso que en SEPARATE),
    el CTA unico lo pulsa `continueTo` (que ya exige estado habilitado + CTA habilitado) y se anaden aserciones: `stepProgress.answers[HEIGHT]` y `[WEIGHT]` ==
    `USER_DECLARED` tras el CTA combinado. Todas las aserciones posteriores (EQUATION_SEX, BODY_FAT, hitos, Entreno..., capturas) quedan intactas.
- `setHeightCm(target, layout)`: contentDescription y tag segun layout (`WHEEL_CONTENT_DESCRIPTION`/`HEIGHT_VALUE_TAG` vs `HEIGHT_RULE_CONTENT_DESCRIPTION`/
  `HEIGHT_RULE_VALUE_TAG`, constantes nuevas). El `performScrollToIndex` sigue siendo la accion del `LazyList` del propio control (indice `cm - MIN_CM` en la rueda
  y en la regla). El fallback "tocar el rotulo literal" solo se conserva en SEPARATE; en COMBINED, si la regla no queda centrada, falla con el candidato real.
- `centerControlOn(...)` (nuevo, usado por altura y peso): relee el candidato del control (stateDescription) tras el scroll y corrige por la diferencia (maximo 3 intentos).
  Motivo: `scrollToItem(i)` deja el item pegado al centro (su borde izquierdo en la linea central) y el control elige el item mas cercano; en un empate de media casilla
  gana el vecino (`minByOrNull` conserva el primero). Con 420 dpi (densidad 2,625) la casilla de peso (10,5 dp) mide 28 px: mitad 14 = distancia 14 a ambos lados, empate; la de altura (14 dp) mide 37 px:
  mitad 18 < 19, sin empate. Es un calculo por lectura, NO observado en dispositivo: si el centrado es exacto el bucle no hace nada. `weightTickIndex` pasa a recibir `Double`.
- No se fija el viewport ni se usa `LocalWizardControlViewport` (la prueba soporta ambos layouts tal cual los elige el producto).

## (2) SetupWizardJourneyUiTest.priorityChipsWrapInANarrowViewportAtDoubleFontAndStayOperable

Veredicto: expectativa obsoleta de la PRUEBA, no defecto de producto. `SetupStepGraph.nodes` (SetupStepGraph.kt l.357-368) anade `TRAINING_MAX` sin condicion
justo despues de `PRIORITIES` y antes de `SPLIT` (`TRAINING_MARKS` solo si `hasTrainingMarks`). Con el borrador sembrado (`seedPrioritiesStep`) el siguiente paso es
TRAINING_MAX y su CTA esta deshabilitado hasta responder (`SetupWizardModels.kt` l.1092 `absent("trainingMax", ...)`), exactamente lo que dumpeo el fallo
(`currentStep=TRAINING_MAX canConfirmStep=false`). `SetupWizardFullJourneyUiTest.walkTraining` ya recorre PRIORITIES -> TRAINING_MAX -> SPLIT.
Cambio: antes de pulsar el CTA se afirma `SetupStepGraph.next(PRIORITIES, draft.stepContext()) == TRAINING_MAX` y se espera `TRAINING_MAX` (no se avanza mas: la prueba solo
valida que el CTA de PRIORITIES es operable); la asercion final de la bolsa (`GLUTE_BAG`) sigue igual. Import nuevo: `SetupStepGraph`.

## (3) PostSetupCalendarUiTest x2 (scheduledDateEdit... y oneOptionalConfirmation...)

`hasScrollToNodeAction()` exige `ScrollToIndex` ademas de `ScrollBy`; el host `Column(verticalScroll(rememberScrollState()))` (l.~728) solo expone `ScrollBy` y `DayView`
no contiene `Lazy*`, por lo que no existe ese contenedor. Nuevo helper `scrollDayHeaderIntoView(header)` = `onNodeWithText(header).performScrollTo()` +
`assertIsDisplayed()` (la asercion ademas lo exige visible; no se debilita nada), usado en `scheduledDateEdit...` y en `expandDay`. Imports: `hasScrollToNodeAction` y
`performScrollToNode` fuera, `performScrollTo` dentro.
Endurecimiento anticipado (SIN evidencia, el paso nunca se alcanzo): en `scheduledDateEdit...` el clic sobre la fecha ahora usa `onNodeWithText(header, useUnmergedTree = true)`,
porque el nodo fusionado es la fila `combinedClickable` entera (su centro puede caer fuera del rotulo con `clickable` propio que abre "Fecha de entrenamiento"); es la misma
tecnica que `expandDay` usa con el circulo del dia. Si en dispositivo se comportara distinto, es un cambio de una linea.

## (4) --screencap-every N (run_instrumentation.py)

- `--screencap-every SEGUNDOS` (float > 0, validado por `positive_seconds`) y `--screencap-dir DIR` opcional. Hilo daemon `ScreencapRecorder`: mientras corre `am instrument`
  llama a `Avd.screenshot_bytes()` (= `adb -s <serial> exec-out screencap -p` por el wrapper de lista blanca; no se toco `avd.py`) y guarda
  `device-evidence/wizard/frames/<nombre de la corrida>/frame-NNNN-<UTC>.png` + `manifest.json` (tamano y SHA-256). Primer fotograma inmediato (aunque la corrida acabe ya),
  cadencia anclada al reloj (sin rafagas tras un adb lento), tope duro de 400 por corrida (`limitReached`, el hilo termina solo), fallos contados (`failed`/`error`) y
  nunca elevados: el veredicto de la corrida no cambia ni se bloquea (arranca tras `popen`, se detiene en el `finally`, `join` <= 10 s).
- `summary.json.screencap` ({everySeconds, directory, saved, failed, maxFrames, limitReached, error}) y `metadata.json.screencapEverySeconds`; `None`/ausente si no se usa.
- Coste: hasta 400 PNG de 1080x2400 por corrida (cientos de KB cada uno, hasta ~0,2-0,4 GB en el peor caso).
- `wizard_evidence.py` no cambia (no hace falta; el runner ya deja los fotogramas). `pull-captures` sigue siendo el camino para los PNG de `captureQ6Screenshot` si algun dia
  son extraibles.
- Validacion: `py_compile` de `run_instrumentation.py`, `wizard_evidence.py`, `avd.py`, `tests/test_screencap.py` OK; `tests/test_screencap.py` 15/15; suite offline completa
  `unittest discover -s tests -p "test_*.py"` = 181 pruebas OK en 45 s; `tests/check_names.py` = ok. Nada en `device-evidence/wizard/frames` (las pruebas usan directorio temporal).

## Sin resolver / riesgos

1. Nada de lo Kotlin esta compilado ni ejecutado (restriccion de la tarea). Primero hace falta recompilar el androidTest y reinstalarlo con `install_apks.py` (los hashes
   del androidTest instalado ya no coincidiran).
2. COMBINED nunca se ha ejecutado en dispositivo: es posible un segundo fallo en pasos posteriores de FullJourney que hasta ahora jamas se alcanzaban (weight, EQUATION_SEX,
   Entreno, Nutricion, Rings, Revision y activacion). Los 7 recorridos pasaban antes por HEIGHT.
3. Hipotesis de producto (NO verificada, no tocada): `WizardWeightRule.jumpTo(index)` y el toque en un rotulo entero usan `animateScrollToItem(index)`, que deja el item pegado
   al centro sin la compensacion de media casilla que SI aplica el centrado inicial (`delta`); con casilla de 28 px (empate) el candidato podria quedar en `index-1`
   (p. ej. 76,9 al tocar "77"). La prueba lo tolera (corrige con `centerControlOn`) pero un usuario real podria verlo. Conviene una prueba de componente
   (no existe ninguna de `WizardHeightRule` en `WizardGateComponentsUiTest`) o mirarlo en el AVD.
4. Q6: `captureQ6Screenshot` sigue escribiendo en el almacenamiento del usuario 10 (no extraible sin root); los fotogramas de `--screencap-every` son un sustituto visual,
   no los PNG del propio test (ni los nombres esperados por `wizard_evidence.py pull-captures`).

## Comandos de verificacion

```powershell
# 0) compilar el androidTest (lo ejecuta el orquestador; no se hizo aqui)
cd android-native; ./gradlew compileBaseDebugAndroidTestKotlin     # o assembleBaseDebug assembleBaseDebugAndroidTest
# 1) reinstalar APK + androidTest para el usuario 10
$py = 'C:\Users\valen\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
cd C:\Users\valen\Documents\KPKNFit\artifacts\consolidation-20261001\tools\avd
& $py -X utf8 install_apks.py --flavor base --serial emulator-5582 --user 10
# 2) las clases tocadas (10 FAIL previos), con fotogramas cada 3 s
& $py -X utf8 run_instrumentation.py --flavor base --serial emulator-5582 --user 10 --switch-user --screencap-every 3 --timeout 3600 --label wizard-ui-fix `
  --classes com.example.kpkn.screens.onboarding.SetupWizardFullJourneyUiTest,com.example.kpkn.screens.onboarding.SetupWizardJourneyUiTest,com.example.kpkn.screens.onboarding.PostSetupCalendarUiTest
# 3) suite completa del wizard (40 pruebas)
& $py -X utf8 run_instrumentation.py --flavor base --serial emulator-5582 --user 10 --switch-user --classes-file suites\wizard-ui.txt --screencap-every 3 --timeout 3600 --label wizard-ui-final
# 4) offline (ya ejecutado aqui: 181 OK)
& $py -X utf8 -m py_compile run_instrumentation.py tests\test_screencap.py
& $py -X utf8 -m unittest discover -s tests -p "test_*.py"
& $py -X utf8 tests\check_names.py
```
Comprobar en `stdout.txt`/`logcat.txt` la linea `WIZARD_ANTHROPOMETRY_LAYOUT=COMBINED|SEPARATE` de cada recorrido de `SetupWizardFullJourneyUiTest`.
