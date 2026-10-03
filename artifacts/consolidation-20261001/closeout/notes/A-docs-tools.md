# Paquete A-docs-tools — nota de cierre (2026-10-02)

Estado del paquete: **DONE** (los 6 ítems cerrados; D1.6 queda con la corrida manual del dueño en el iMac, y D1.3 con la corrida en `emulator-5582` a cargo de quien coordina, ambas fuera de lo que se puede ejecutar aquí). `SessionTemplateRepositoryTest` **pasó 2 de 2 veces: ya se puede quitar de `-Exclude` / `KPKN_EXCLUDE`** (ver sección 4).

## 1. Ítems

| Ítem | Estado | Resumen |
|---|---|---|
| B-01 | **Cerrado** | `docs/WIZARD_PLAN_DEVIATIONS.md`: registro de estado; DEV-r2-01 **aceptada** con la cita del dueño (2026-10-02); tabla de series literal frente a aceptado; variantes descartadas (un reemplazo 17–17,5; literal +3,5; B-06; B-07); la nota al usuario «casi no se ve»; hallazgo MRV 16 < piso 18; DEV-r2-02 (tolerancia 17,5, **en curso**: «implementada en el cierre; ver nota B-02») con anexo fechado a r2 §14.3; DEV-r2-03 (R13 diferido, R14 absorbida); DEV-r2-04 (R16 parcial: PHUL sí, PHAT no); DEV-r2-05 (H-CICLO, añadida por mí: ver sección 3); 32/2.304 corregido a 36 filas afectadas / 24 viables. Dos comentarios de `NativeProfileSpec.kt` actualizados (solo comentarios). |
| D1.5 | **Cerrado** | `docs/REPAIR_BENCHMARK_HOOKS.md` (nuevo): qué son, dónde están (21 llamadas en 6 archivos, verificado), cómo reactivarlos (`create("repairBenchmark")` + `assembleBaseRepairBenchmark`), cómo retirarlos. Sin tocar código de producción. |
| D1.4 | **Cerrado** | `SetupInventoryTypingUiTest.kt` movida a `closeout/deleted/…`; quitada de las 4 suites que la nombraban; README con marcas y §26; pruebas offline de `tools/avd` en verde. |
| D1.3 | **Cerrado (sin emulador)** | `tools/avd/suites/warmup-qa10.txt` + README §26 (cómo correrla en `emulator-5582` con `--user 10 --switch-user`; en el 5580 quedan SKIPPED esperadas). No se ejecutó ningún emulador. |
| D1.6 | **Cerrado en lo que se puede hacer aquí** | `KPKNFitTests.swift:13-14` corregido (`Set(x.map(\.id)).count`); `TESTING.md` con los pasos exactos; `closeout/IOS_RUN_GUIDE.md` (10 pasos para el iMac). **No se afirma que compile ni que pase** (no hay macOS). La corrida real es manual del dueño. |
| D1.2 | **Cerrado** (plan A cumplido, no hizo falta el plan B `@Ignore`) | `SessionTemplateRepositoryTest` reescrita con base Room en memoria; sin tocar producción; **3/3 en verde dos veces** (sección 4). |

## 2. Archivos tocados (ruta relativa al repo)

Creados:
- `docs/REPAIR_BENCHMARK_HOOKS.md`
- `artifacts/consolidation-20261001/tools/avd/suites/warmup-qa10.txt`
- `artifacts/consolidation-20261001/closeout/IOS_RUN_GUIDE.md`
- `artifacts/consolidation-20261001/closeout/notes/A-docs-tools.md` (esta nota)

Modificados:
- `docs/WIZARD_PLAN_DEVIATIONS.md` (archivo sin rastrear en git; edición de documentación)
- `android-native/app/src/main/java/com/example/kpkn/data/protocols/definitions/NativeProfileSpec.kt` (**solo 2 comentarios**, ~l.396 y ~l.501; comportamiento idéntico; el archivo también está sin rastrear)
- `android-native/app/src/test/java/com/example/kpkn/data/sessions/SessionTemplateRepositoryTest.kt` (reescrita)
- `ios-native/KPKNFit/KPKNFitTests/KPKNFitTests.swift` (solo líneas 13-14; archivo CRLF, CRLF conservado: 576 CRLF antes y después)
- `ios-native/KPKNFit/TESTING.md` (reescrito; sin rastrear en git)
- `artifacts/consolidation-20261001/tools/avd/README.md` (fila de la tabla de archivos, 3 marcas en menciones históricas de la clase eliminada, sección nueva §26 al final)
- `artifacts/consolidation-20261001/tools/avd/suites/all-android-tests.txt`, `all-android-tests-base-r2.txt` (se quita la clase), `all-android-tests-base-r3.txt` y `wizard-ui.txt` (solo comentarios de cabecera)

Movidos:
- `android-native/app/src/androidTest/java/com/example/kpkn/screens/onboarding/SetupInventoryTypingUiTest.kt` → `artifacts/consolidation-20261001/closeout/deleted/android-native/app/src/androidTest/java/com/example/kpkn/screens/onboarding/SetupInventoryTypingUiTest.kt`

Generados por las herramientas (evidencia): `logs/A-sessiontemplate-*.log`, `receipts/A-sessiontemplate-*.receipt.json`, `test-evidence/A-sessiontemplate-*/`.

## 3. Verificaciones y decisiones de B-01 (cifras comprobadas, no inventadas)

- **36 / 24**: la matriz Q2 son 4 perfiles × 4 experiencias × 6 días × 8 fixtures × 3 tiempos (20/60/100) = 2.304; las recetas de DEV-r2-01 solo se usan en E0: Músculo 5 y 6 días (2×4×3 = 24) + Atleta 6 días (1×4×3 = 12) = **36** (1,6 %); viables 24 (60 y 100 min); las 12 de 20 min son `TIME_BUDGET` legítimo [I]. Evidencia g24: `rejectedByReason={APPARATUS_ABSENT=360, TIME_BUDGET=504, PROFILE_MISMATCH=216}`, sin COMPOSITION. La cifra «32 (1,4 %)» salía de la hipótesis F11 del informe Q2.
- **Tabla de series**: salida del modelo estático `research/q2/q2sim.js` **reejecutado hoy con el catálogo actual** (SHA 6bdb9e59…): idéntica byte a byte a `q2sim-output.txt`. Literal: Músculo 5d 19,0 / 6d 19,5 (principiante), 29/30 (inter/avanz., antes de ajustar), suelo 18; Atleta 6d 20 (suelo 18) y 27 (suelo 19). Aceptado: 15,0 / 15,5 / 16 y Atleta 16. Solo 18 (Músculo) y 20 con suelo 18 (Atleta) están fijadas por pruebas (verdes en g24).
- **Un solo reemplazo (17–17,5)**: reproducido con una copia de trabajo de `q2sim.js` ampliada con 4 calendarios (no guardada en el repositorio): `["BL_lit","BU","BL_lit","BU","BL_MRV"]`, `["BU","BL_lit","BU","BL_lit","BU","BL_MRV"]`, `["XA","CARDIO","XH6","XB","CARDIO","D6"]`, `["XA","CARDIO","XH","XB","CARDIO","D6b"]` → Músculo 5d principiante 17,0; 6d 17,5; inter/avanz. 16; Atleta 6d principiante 16, inter/avanz. 17 (semanas 3–5).
- **MRV 16 < piso 18**: `VolumeLandmarks.kt:39` (GLUTES 0/8/16) frente a `VolumeCalibrationEngine.kt:93` (`Bounds(9, 14, 18)`); `budgets()` toma `min(personal, global)`; `SessionAssistantEngine.kt:644-651` bloquea con `weeklySets >= weeklyMrv`.
- **«Casi no se ve»**: `description = (entry.description + "\n" + notes…)` en `SimpleCyclePersonalizer.kt` y `CompactHeroBanner.kt` (`InlineHeroTextField(... singleLine = false)`, `maxLines = 2`, ~l.320-335 y 488).
- **Plan externo (solo lectura)**: `user_approval: PENDING` l.16, `approved_revision: null` l.17, definición de terminado l.813, §12.4 l.433/435, PHUL l.262, §12.1 l.399, §14.3 l.597/601. No se editó.
- **Decisión mía, fuera de la lista literal**: añadí **DEV-r2-05** (H-CICLO: el fin de bloque continúa solo con un aviso, el plan §12.1 dice «ofrecer continuar») porque es la misma laguna de gobierno (plan incumplido en silencio) y es decisión ya resuelta del dueño (n.º 10). Estado: aviso pendiente de implementar. Si no se quiere, se borra la fila y la sección sin efectos colaterales.
- **Cardio**: dejé anotado en DEV-r2-03 que el botón de progresión del editor (`CardioProgressionEngine.suggest`, +10 %: 10→11 min) no es un respaldo limpio de R13 (verificado en código).

## 4. D1.2 — SessionTemplateRepositoryTest

**Qué se hizo** (solo el archivo de pruebas; producción intacta): cada prueba crea su propia base Room en memoria (`KpknDatabase.createInMemory(context)`), la enlaza por reflexión como `KpknDatabase.INSTANCE` (mismo helper que `SessionEditorViewModelRulesTest`), y `@After` hace `SessionTemplateRepository.resetForTests()`, cierra la base y desenlaza el singleton. `@Before` hace antes `resetForTests()` y `KpknDatabase.closeInstance()`. `@Config(manifest = Config.NONE, sdk = [34], application = Application::class)` evita instalar `NutritionCrashHook` (la `KpknApplication` real). Además se quitó el `try/finally` de limpieza (la base es nueva en cada prueba), se usan ids fijos, se esperan con tope de 30 s los recálculos asíncronos de `allTemplates` (antes la espera de «desaparece al archivar» podía cumplirse con un valor aún sin recalcular, p. ej. el inicial vacío; ahora primero se espera a verla y después a que desaparezca), y las lecturas de vuelta usan la base de la propia prueba. Las 3 pruebas conservan nombre y contrato.

**Causa del cuelgue (sigue [I], con soporte)**: el análisis estático `research/sessiontemplate-hang/informe.md` y los XML antiguos (`g02`/`g04`: la 1.ª prueba pasa en 5–17 s y la 2.ª queda «en vuelo» hasta el fin de la tarea) apuntan a que `KpknDatabase.INSTANCE` de la 1.ª prueba sigue enlazado con conexiones SQLite de Robolectric ya reiniciadas, así que la 2.ª nunca ve `isReady`. No existe volcado de hilos de un worker colgado y **no hizo falta capturarlo**: con la base en memoria ya no se reproduce, ni la 2.ª ni la 3.ª prueba (`generationCatalog…`, que nunca había llegado a ejecutarse) fallan.

| LogName | Tareas | Resultado |
|---|---|---|
| `A-sessiontemplate-1.log` | `testBaseDebugUnitTest --tests *.SessionTemplateRepositoryTest`, `-TaskTimeoutMin 12` | exit 1 en 7 m 49 s: **errores ajenos** de compilación, 32 líneas `e:` todas en `src/test/.../screens/workout/WorkoutPendingSeriesTest.kt` (otro paquete; su código de producción `WorkoutPendingSeries.kt` aún no existía), ninguna en mi archivo. Reintento tras esperar, como manda la regla. |
| `A-sessiontemplate-1b.log` | idem | **exit 0**, `BUILD SUCCESSFUL in 8m 39s`; 3 pruebas, 0 fallos, 0 omitidas, 4,97 s (`durableCrud…` 4,722 s, `invalidSystemWrite…` 0,127 s, `generationCatalog…` 0,117 s). Evidencia: `test-evidence/A-sessiontemplate-1b/`. |
| `A-sessiontemplate-2.log` | `cleanTestBaseDebugUnitTest testBaseDebugUnitTest --tests *.SessionTemplateRepositoryTest` (el `cleanTest` fuerza la ejecución real; si no, Gradle da la tarea por al día) | **exit 0**, `BUILD SUCCESSFUL in 6m 9s` (más 1.016 s esperando el turno); 3 pruebas, 0 fallos, 0 omitidas, 5,04 s. Evidencia: `test-evidence/A-sessiontemplate-2/`. |

**Para quien coordina**: pasó **2 de 2 veces** y la tarea terminó sola (sin llegar al tope de 12 min): se puede **quitar `*.SessionTemplateRepositoryTest` de `-Exclude`/`KPKN_EXCLUDE`** en la corrida final (X-FINAL). Reserva: las dos corridas fueron aisladas; no se corrió junto a otras clases Robolectric en el mismo JVM (el informe recomendaba una vez más sin `forkEvery`). Si en la suite completa volviera a colgarse, se vuelve a excluir y se captura el volcado con `tools/dump-test-workers.ps1`; el plan B (`@Ignore` con motivo) no se aplicó.

## 5. Pruebas offline de las herramientas (D1.4 / D1.3)

- `python -X utf8 -m unittest discover -s tests -p "test_*.py"` (en `tools/avd`): **189 pruebas, OK** (corrida final 17:13 hora local, 89 s). `python -X utf8 tests\check_names.py`: **ok**.
- La primera corrida (16:48) dio 187 OK + 2 FAIL (`test_driver_e2e_transfer` REPLACE y CREATE: «app-base-debug.apk is older than …HomeViewModel.kt»), una carrera con la edición de `HomeViewModel.kt` (16:48:15) del paquete de nutrición durante la prueba. Repetidas, `tests.test_driver_e2e_transfer` + `tests.test_suite_plan` (13 pruebas) y la suite completa pasan. Esas dos pruebas pueden volver a fallar mientras otros paquetes editen fuentes de `src/main` a mitad de la corrida.
- `test_suite_plan` valida que toda entrada de toda suite exista, incluida `warmup-qa10.txt`, y que `all-android-tests.txt` + las 2 clases especiales cubran exactamente el árbol androidTest (hoy 38 clases). **Si otro paquete añade una clase androidTest nueva, hay que añadirla a `suites/all-android-tests.txt` o esa prueba fallará.**

## 6. Clases de prueba para verificar el paquete

- JVM: `com.example.kpkn.data.sessions.SessionTemplateRepositoryTest` (3 pruebas).
- Herramientas offline: el comando de la sección 5.
- No hay cambios que afecten a otras clases JVM (NativeProfileSpec.kt solo cambió comentarios). Como la clase eliminada era androidTest, conviene que la próxima compilación de androidTest (`compileBaseDebugAndroidTestKotlin`, ya prevista por quien coordina) confirme que nada la referenciaba (por búsqueda no queda ninguna referencia en `src/`).

## 7. Riesgos abiertos

- **iOS**: el arreglo de `KPKNFitTests.swift` es a ojo y sin compilar; los otros 10 archivos Swift de la app y el catálogo modificados sin commit en `ios-native` nunca se han compilado, hasta donde se sabe. Faltan el iMac y la decisión del dueño (¿cuándo publicar iOS?).
- **D1.3**: la corrida real del calentamiento en `emulator-5582` queda para quien coordina (comprobar `users` y modo avión antes; incógnita del motor TTS).
- **DEV-r2-02**: estado «en curso» hasta que cierre B-02 (la pasada de Q2 calibrada sigue pendiente; sin ella «0 filas cambian» solo vale sin calibración). Quien cierre B-02 debe actualizar la fila del registro y la sección DEV-r2-02.
- `TODO.md` §3 sigue diciendo que `SetupInventoryTypingUiTest` es un `@Ignore` «no borrado» y que `SessionTemplateRepositoryTest` está excluida: lo actualiza X-FINAL.
- Las líneas citadas en los documentos (`~l.`) se moverán con las ediciones de los otros paquetes; los documentos indican cómo localizarlas con `grep`.
