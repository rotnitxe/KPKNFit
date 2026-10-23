LV-BF completado sobre LV-AF en `C:\kw\u11`, rama `feat/screens-v2`. **1.448 pruebas correctas, 0 fallos, 0 errores y 0 omitidas.** Producción e instrumentadas compilan y ambos APK `.dbg` están ensamblados y verificados.

Se leyeron el encargo, `CLAUDE.md`, QA_EMU_LVB, los briefs e informes de LV-B y LV-AF y las reglas del plan. Se contrastaron los tres registros del emulador y sus renders en lectura. La reproducción local ejecutó 62 pruebas: reprodujo los diez fallos de tarjeta, relator y descanso; pasaron las 33 pruebas de modos y la navegación de Fotos. Las comprobaciones separadas de ancho y alto detectaron además un recorte vertical del título de Distancia: la cabecera reserva ahora el alto real medido de la línea.

Los cuatro casos guiados fallaban en la primera comparación del feedback, antes de inyectar el error de persistencia. El origen es **CAL1, `89499d778`**: `amrapMinimumReps` pasa a ser el único mínimo efectivo y una serie ordinaria deja de heredar sus 8 reps como mínimo AMRAP. El formulario conserva explícitamente `null` mediante `amrapMinimumOverridden=true`. La comparación de `SetExecutionCard`, `WorkoutSetRecorder` y `WorkoutFeedbackModels` descarta AUD2, CAL2 y LV-A para esa diferencia. **LV-B, `9931c5a44`**, añade otra corrección semántica: las reps dejan de enviarse como segundos de cronómetro.

Se adaptó la expectativa a ese contrato aprobado. Se conserva la igualdad del feedback completo en ambos intentos, las filas y pausas originales, el commit vacío explícito al saltar y el borrador separado. Se añadieron comprobaciones de la captura principal —20 kg, 8 reps, RPE 8— y de que un segundo toque durante la persistencia no envíe otro registro. El registrador y su lógica de reintento se mantienen; sus pruebas con Room real siguen incluidas en la regresión.

| Siete casos heredados | Evidencia y decisión |
|---|---|
| `suggestedLoadUsesChip…` | LV3 y el snapshot PR16 ya mostraban «Sugerida» dentro del chip. Se conserva la ausencia de una tarjeta separada y se comprueba el chip de 82,5 kg y su selección efectiva en el borrador. |
| `scheduledVolumeReplacedKeepsCard…` | Las técnicas programadas conservan la tarjeta y no abren el panel guiado reservado a técnicas apiladas. Se actualizan los accesos a Avanzado y Modo visual y se conservan las aserciones de ausencia del panel. |
| `advancedOptionsCtaUsesNewCopy…` | Los rótulos aprobados de LV3 (`747350cd3`) son «Avanzado» y «Modo visual». Se prueban sus nombres accesibles, la apertura de ambas caras y la ausencia de los textos antiguos. |
| Relator: 41 → 39 kg y deshacer | El texto vigente incluye «↶ vuelve a 41» desde PR16 (`d5ffa3816`). Se corrige la frase esperada y se conservan el ajuste porcentual, el bloqueo de dobles acciones, el Ack, el pop y la restauración a 41. |
| Relator: carga desconocida | El texto vigente es «Carga ajustada. ↶ Deshacer.». Se conserva la restauración del borrador ausente y la prohibición de inventar kilos. |
| Descanso manual | LV6 (`97f14e0b6`) integra «manual» en el tiempo. Se exige «de 1:45 · manual» visible y se verifican los callbacks de −15 s, +15 s y Saltar. |
| `Revision14Photos#captureProductionFinish…` | El registro dice que falta el nodo «Siguiente», no que el emulador rechace los toques. El visor vigente usa «Foto siguiente». Se actualiza el selector y se exige la segunda imagen y «2/2». Este recorrido no está congelado: solo exige el destino `.dbg`. La prueba local abre mediante un toque real, avanza, retrocede y cierra el visor. Se conservan las comprobaciones instrumentadas de historias, exportaciones y notas. |

Los 22 contratos de tarjeta, relator y descanso comparten ahora el mismo código entre Robolectric y las instrumentadas, conservando sus clases y nombres de prueba en el APK. Se añadieron seis escenas visuales, cuatro casos de prioridad de reps en Solo RPE y una prueba de navegación de Fotos.

Los ajustes visuales conservan la tarjeta de **312 × 425 dp**, las acciones de **44 dp**, la isla de **112 dp** y sus **12 dp** de separación. Los títulos estáticos muestran elipsis cuando desbordan y mantienen el desplazamiento con movimiento habilitado. La cabecera compacta conserva los accesos táctiles de 44 dp; el relator adapta su texto entre 11 y 13 sp. RM a 740 dp se comprueba con el editor abierto a fuente normal y al 130 %. El deslizador usa energía **#F7CF73**. Solo RPE prioriza el resultado o borrador restaurado y centra las reps en objetivo, última serie o 8; la carga sigue sin fijarse y el esfuerzo empieza sin marcar. Distancia y Personalizado separan «Objetivo» de su etiqueta con **6 dp**.

La validación final comprende **1.422 pruebas de `screens.workout.*` y 26 renders de regresión de LV-A/LV-B**, en 184 suites. Pasan las 33 pruebas locales añadidas y se generaron **38 PNG**. A 740 dp, el relator de RM termina en y=154 dp con fuente normal y en y=156,33 dp al 130 %; la tarjeta empieza en y=159 dp. El título de Distancia conserva completos sus 65 px medidos.

Se verificaron los manifiestos empaquetados, los metadatos, la integridad ZIP y los SHA-256 de ambos APK:

- [APK de la app](C:/kw/u11/android-native/app/build/outputs/apk/base/debug/app-base-debug.apk): `com.example.kpkn.dbg`.
- [APK instrumentado](C:/kw/u11/android-native/app/build/outputs/apk/androidTest/base/debug/app-base-debug-androidTest.apk): `com.example.kpkn.dbg.test`, con destino `com.example.kpkn.dbg`.

Evidencia: [validación final](C:/kw/u11/android-native/app/build/lv-bf-validation.log), [resumen y SHA-256](C:/kw/u11/android-native/app/build/lv-bf-delivery-summary.json) y [reproducción inicial](C:/kw/u11/android-native/app/build/lv-bf-baseline.log). Los XML están preservados en `lv-bf-baseline-results/`, `lv-bf-title-height-results/` y `lv-bf-final-results/`; los diffs, en `lv-bf-origins/`, y los PNG, en `lv-bf-renders/`, todo bajo `android-native/app/build/`. Coinciden los hashes de los **48 registros y capturas de entrada**.

Código y pruebas: **`402039255` — «afina LV-B y valida los reintentos guiados»**. Este informe se registra en un commit documental separado. Ambos commits llevan `Co-Authored-By: Codex <noreply@openai.com>`.

Pendiente del orquestador: ejecutar `screens.workout` con ambos APK nuevos y revisar RM con el editor de porcentaje a 740/880 dp y fuente al 130 %, títulos largos, Solo RPE y separación de las cantidades. Revisar especialmente los cuatro reintentos guiados y el recorrido completo de Fotos. La ejecución instrumentada no se ha realizado en este trabajo.

Todos los comandos Gradle usaron `bash /c/kw/tools/gradle_slot.sh`, en primer plano y con `-Xmx8g`. No se utilizaron teléfono, adb, `--stop`, cierres de Java ni push. No se modificaron `artifacts/`, documentos de auditoría, editor ni calibrador. La actualización ajena de QA_EMU_LVB y el artefacto preexistente quedan fuera de los commits de esta entrega.
