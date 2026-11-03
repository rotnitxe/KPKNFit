# Pulido v1 · informe de verificación y cierre del turno (1-nov-2026)

Worktree `C:\kw\pulido`, rama `feat/pulido-v1`. Punto de partida: HEAD `6dd49b361` + el WIP de correcciones
del turno anterior (sin commit). No se hizo reset, clean, stash, push ni merge; no se modificó Room ni se tocaron
datos reales. El detalle de la evidencia visual está en
`artifacts/pulido-v1-audit-evidence/README.md`.

## Hallazgos funcionales

| Hallazgo | Estado | Implementación / prueba |
|---|---|---|
| A · registro rápido desde Inicio y techo | **corregido** | `HomeScreen.kt` monta `SupplementQuickSheet`, detalle y aviso de techo; `HomeViewModel.takeSupplementFromHome` re-comprueba el techo con el total conocido (incluida cafeína de alimentos), informa fallos y solo confirma tras guardar; «Deshacer» borra el registro creado. **`HomeSupplementTakeTest` 3/3** (sin confirmación no guarda; con confirmación guarda y deshace; fallo de persistencia no anuncia éxito). |
| B · borrado seguro de una toma | **corregido** | `SupplementTake` lleva `logId` + `foodIndex` + `foodId`; `NutritionRepository.removeFoodFromLogAndAwait` quita solo ese alimento y conserva los demás; creatina histórica `gen106` incluida. **`SupplementEngineTest`** (dos tomas iguales, creatina legacy) y **`NutritionRepositoryRemoveFoodTest` 7/7** (comida compartida, índice cambiado, dosis cambiada). |
| C · edición de suplementos propios | **corregido** | `isCustom` se reconoce por `customName`, no solo por el modo; `supplementConfigItem` conserva id/nombre/unidad/notas al editar y solo genera id al crear. **`SupplementConfigIdentityTest` 4/4**. |
| D · recordatorios | **corregido y verificado** | `SupplementReminderManager` programa una alarma diaria por elemento con `AlarmManager` (exacta o ventana), canal propio, receptor y restauración en reinicio/actualización/zona; al desactivar/eliminar se cancela; si no se puede programar se retira el recordatorio y se avisa. **`SupplementReminderSchedulingTest` 5/5**. En el emulador, el recordatorio 09:00 de Cafeína quedó programado y `dumpsys alarm` mostró `RTC_WAKEUP … com.example.kpkn.SUPPLEMENT_REMINDER.cafeina` para `com.example.kpkn.dbg`. |
| E · fecha al cruzar medianoche | **corregido** | `SupplementEngine.whenOptions(now: LocalDateTime)` devuelve la fecha y hora reales; «Hace 30 min» a las 00:10 es ayer 23:40 y «Esta mañana» se etiqueta «Ayer por la mañana» si aún no ha llegado. **`SupplementEngineTest`** cubre medianoche y 00:40. |
| F · pendientes en Inicio | **corregido** | `SupplementEngine.isDone` usa `takes.size >= item.doses`; `HomeNutritionToday` y `SupplementQuickSheet` muestran `n/de doses` y no marcan «Todo tomado» tras la primera dosis. **`SupplementEngineTest`** cubre 1, 2 y 3 dosis. |
| G · acción de cafeína | **corregido** | `NutritionScreen` conecta `openCaffeine` al detalle; el módulo muestra el total también si solo hay cafeína de comidas/bebidas. **`NutritionSupplementsModuleTest` 2/2** y verificación en emulador (total → detalle → configuración). |
| H · «Crear una copia» | **corregido** | `applyProgramTemplate` pasa `forceCopy = !overwrite`; una copia crea un programa/borrador distinto sin tocar el original, incluso sin sesiones. **`ProgramTemplateEngineTest`** y **`ProgramDetailViewModelTest`** (copia sin sesiones). |
| I · carrera al sustituir | **corregido** | Se eliminó el retardo de 900 ms; la entrada confirmada se captura en el diálogo, `applying` bloquea dobles toques y el resultado solo se anuncia con el callback real. |
| J · entradas visibles no aplicables | **corregido** | `applicableIds` limita la hoja a plantillas/protocolos que el llamador resuelve; las entradas nativas sin aplicación no se anuncian. **`LibrarySectionCatalogTest` 1/1**. |
| K · «Deshacer» real | **corregido** | El resultado de la sustitución lleva el id de la copia previa; `undoProgramApply` solo restaura si el programa no cambió (contador de revisión de `updateProgram` + programa anterior), y no se ofrece deshacer tras una copia. **`ProgramDetailViewModelTest.undo_after_replacing…` 1/1**. |
| L · datos inventados del catálogo | **corregido** | Semanas, días, fases y descargas salen de `recipe`/catálogo; sin receta se muestra «—»/estado desconocido, sin 6 semanas ni fase fija. `ProgramTemplatesSheet` ya no fija «Intermedio», «Fuerza y músculo» ni «Gimnasio completo». **`CatalogDerivationTest` 3/3**. |
| M · ruta antigua del reparto | **corregido** | `SplitView` envía el resultado de `SplitApplySheet` y del reparto múltiple por `onApplied`, con resumen y deshacer del flujo aprobado, en vez de escribir por su cuenta. |
| N · pruebas del catálogo | **corregido** | `ProgramStructureSheetsTest` 8/8; `ProgramTemplatesContent` vuelve a mostrar las filas reales; se añadió `LibrarySectionCatalogTest`. |
| O · zona cardíaca falsa | **corregido** | `CardioNowContent` solo pinta check/«En tu zona» con `shownZone` calculada; sin FC máxima muestra «Zona sin calcular · falta tu FC máxima» y no inventa zona. **`CardioLiveCenterUiTest` 9/9**. |
| P · pulso manual y tiempo en zona | **corregido** | El tiempo en zona solo se acumula con pulso del reloj; la página Datos explica que una lectura manual es puntual y no mide tiempo en zona. **`CardioLiveCenterUiTest`**. |
| Q · aviso de cambio de peso | **corregido** | `bodyWeightReference` usa la medición más reciente anterior a la fecha y dentro de 7 días; una edición histórica no se compara con el peso actual; exactamente 2,5 kg no avisa. **`BodyRecordDraftTest` 15/15**. |
| R · microajuste en libras | **corregido** | `bodyFineAdjustedKg` avanza 0,1 de la unidad visible y convierte a kg con 4 decimales, con límites canónicos. **`BodyRecordDraftTest`** kg/lb/redondeo/límites. Emulador: 64,1 → 64,2 → 64,1 con capturas distintas. |
| S · anillo de dictado | **corregido** | `LoggerStopButton` hace una sola pasada de 15 s que crece (sin reinicio ni sentido inverso); el tope funcional de 25 s sigue en `LoggerDictation`. |
| T · certificación visual | **no alcanzada** | La compuerta estricta sigue fallando; ver la sección siguiente. Se corrigieron referencias falsas y se registraron DESV-1506…1509. |
| U · técnica | **corregido** | Bandas verticales con degradado, rayas diagonales en parciales, anillo de la elegida, cabezal de 2 dp con resplandor degradado y halo radial; altura uniforme de 18 dp (el dato de velocidad por repetición no existe). |
| V · capturas del teléfono | **parcial** | Las capturas `fine6_*`/`fine7_*` son distintas y muestran 64,0 → 64,1 → 64,2 → 64,1, pero son del APK anterior. El Z Flip no estaba conectado en este turno; la validación nueva del APK queda pendiente por falta de dispositivo. |

## Verificación ejecutada

- `:app:compileBaseDebugKotlin`: **BUILD SUCCESSFUL** (6 min 05 s).
- Tanda inicial de clases afectadas: **102/102** (`SupplementEngineTest` 20, `ProgramTemplateEngineTest` 15,
  `BodyRecordDraftTest` 15, `NutritionQuickSaveTest` 7, `NutritionRepositoryRemoveFoodTest` 7,
  `SupplementReminderSchedulingTest` 5, `ProgramStructureSheetsTest` 8, `CardioLiveCenterUiTest` 9,
  `CardioPulidoRenderSheetTest` 6, `SuplementosRenderSheetTest` 10).
- Tanda de deshacer/cafeína: **4/4**. Tanda final combinada con el APK: **10/10** y
  `:app:assembleBaseDebug` **BUILD SUCCESSFUL** (9 min 33 s).
- `node tools/paridad-check.cjs --all`: **825 filas con evidencia, 0 sin cerrar** (estructura; no es 1:1).
- `node --test tools/visual-gate.test.cjs`: 3/3 (prueba del comparador).

## APK y dispositivo

- APK: `com.example.kpkn.dbg` (KPKN Dev), versionCode 55, 529.933.249 bytes, SHA-256
  `50C984BC5F52B19D2E5862355F8D59CD232BF7A0F5325DACA8F748F1FDFAD811`, certificado
  `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`.
- Emulador `ZFlip5_QA` (720×1760 px = 360×880 dp, Android 16): instalado con `adb install -r` («Success»).
  Recorrido verificado: Inicio; Nutrición y pila; tomar/quitar una toma; hoja rápida de Inicio; Cuerpo y
  microajuste; detalle/configuración de Cafeína; recordatorio 09:00 con alarma real en `dumpsys alarm`.
  Capturas en `artifacts/pulido-v1-audit-evidence/emu-*.png`; el emulador se apagó al terminar.
- Z Flip real: no conectado por depuración inalámbrica en este turno. No se instaló ni se borraron datos; la
  validación del APK nuevo en el teléfono no se puede afirmar.

## Compuerta visual

| Escena | Paridad | Error medio | Resultado |
|---|---:|---:|---|
| cv-trabajo (1×) | 81,11 % | 28,79/255 | falla (98 % / 2,5) |
| cv-sobre-zona | 80,43 % | 29,21/255 | falla |
| cv-continuo | 84,12 % | 23,23/255 | falla |
| cv-listo | 83,76 % | 24,79/255 | falla |
| sp-ajustar (hoja sobre la página) | 88,22 % | 18,64/255 | falla |

Las capturas se recortaron 30 px arriba y 11 px abajo en ambos lados; no se estiró, enmascaró ni cambió el
umbral. La captura a densidad 1× descarta el reescalado; los TTF de `res/font` tienen el mismo SHA-256 que los
del HTML y un probe confirmó que Syne/Inter se rasterizan. Las diferencias restantes son de composición
(posición del reloj, ondas, sombras/gradientes y espaciado de hojas), no de fuentes. No se declara 1:1.

## Git y procesos

- WIP sin commit al escribir este informe; no se hizo push ni merge.
- No queda ninguna JVM con `kpkn.slot` de `C:\kw\pulido` ni ticket/candado propio; el emulador se detuvo.
  El slot Android que apareció vivo pertenece al árbol principal de otro agente y no se tocó.
