# Mapa · NUTRICIÓN-CUERPO (prefijo `nc`, pestaña «Comida y Cuerpo»)
Puntos del usuario: 20 (dictado de comida) y 21 (nueva medición + botón flotante).
Archivos: `src/scenes-nc.js`, `src/nc.css`. Compilar: `node build.cjs --out=shots/nc`.

## 1. Piezas reales de la app
- Dictado: `screens/nutrition/design/logger/LoggerDictation.kt` (`rememberLoggerDictation`, errores «No te entendí…», «Sin permiso…», «Este teléfono no tiene dictado por voz.»), `LoggerDescribeField.kt` (`DictateButton` círculo de 48 dp, `AnalyzePill`), `data/voice/VoiceNutritionRecognizer.kt` (`VoiceState.Partial/Final/Error/Unavailable`, 15 s, `EXTRA_PREFER_OFFLINE`).
- Cuerpo: `screens/nutrition/BodyProgressScreen.kt` (LazyColumn, `BodyIsland`, hoja), `body/design/BodyPageHeader.kt` («+» suelto), `BodyIsland.kt` («+» en círculo), `BodyRecordSheet.kt`, `BodyRecordDraft.kt`, `BodyRecordMeasures.kt`, `BodyWeightModule.kt`, `BodyHistorySection.kt`, `screens/onboarding/design/WizardWeightRule.kt` (la regla).
- Botón par: `nutrition/design/NutritionLogIsland.kt` (píldora vidrio 56 dp, «+» 20, 8 dp, padding 22, `padding(bottom=84.dp)` sobre la barra de gestos, se esconde con `KpknModalTracker.openCount>0`, entra 220 ms con 8 dp de subida).

## 2. Escenas → pieza
| Escena | Pieza |
|---|---|
| nc-d-vivo (interactiva) | LoggerDescribeField + LoggerDictation completo |
| nc-d-reposo | LoggerDescribeField (EDIT) + LoggerRepeat |
| nc-d-permiso / -sistema / -denegado | `permission` launcher de LoggerDictation + aviso |
| nc-d-escuchando-0 / -escuchando / -ruido | VoiceState.Partial, RMS |
| nc-d-leyendo / -resultado | LoggerWriteMode.READING/RESULT, LoggerAnalysisPanel, LoggerSummaryCard, LoggerFoodItem, LoggerSaveBar |
| nc-d-no-oi / -no-entendi / -sin-voz / -sin-conexion | `dictation.error` |
| nc-c-pantalla (interactiva) / -isla / -vacio / -guardado | BodyProgressScreen, BodyIsland, BodyWeightModule, BodyHistorySection |
| nc-s-primera / -historial / -atipico / -extras / -errores / -fecha / -editar / -eliminar / -hc | BodyRecordSheet (+ WizardWeightRule) |

## 3. Decisiones y propuestas
- **Micrófono**: de círculo suelto a acción «Dictar» (icono+palabra) dentro de la fila de acciones del campo; al escuchar se convierte en «parar» en el mismo sitio con anillo de 15 s. Razón: hoy parece relleno y no dice qué hace.
- **Avisos**: viven dentro del campo hasta resolverse, con dos salidas siempre (reintentar/abrir ajustes y «Escribirlo»). Ámbar para «atención», nunca rojo. `new`.
- **No te oí / No te entendí**: dos casos distintos; con texto parcial se conserva lo dictado. `new`.
- **Hoja previa al permiso** (copy aprobado por el orquestador). `new`/`ask`.
- **Regla sin caja**: se corta hoy porque el widget pinta su propio fondo negro de esquinas rectas dentro de una hoja gris redondeada. Se dibuja sobre la superficie de la hoja con máscara de desvanecido en los bordes (sin esquinas). ±0,1, inercia y ajuste a la décima. Cifra Syne 46–52.
- **Referencia**: «Último: x · hace n días» + diferencia en vivo (neutra; ámbar si es atípica).
- **FAB «Nueva medición»**: par exacto del de Nutrición. Se queda fijo (Nutrición NO se compacta con el scroll; solo se esconde bajo hojas). El «+» de la cabecera y el de la isla salen; el botón «Registrar pesaje» del vacío sale. `move`/`out`.
- **Valor atípico**: umbral propuesto >2,5 kg frente a un pesaje de ≤7 días; no bloquea. `new`/`ask`.
- Resultado del dictado: sin anillo; «652 kcal» + barra de macros + tres columnas (np-* de arreglos-v1).
- Fila nueva resaltada y «Medición guardada · Deshacer» (el diálogo de eliminar ya promete deshacer).

## 4. Motor / datos
- Dictado: exponer `onRmsChanged` (onda y ruido) y la confianza; distinguir `ERROR_SPEECH_TIMEOUT`/`NO_MATCH` (no te oí) de otros; conservar parcial al fallar; abrir ajustes de permiso y de voz; descarga de voz offline (ACTION_…) solo si hay forma estable.
- Cuerpo: mover «+» de `BodyPageHeader`/`BodyIsland` a un `BodyLogIsland` copia de `NutritionLogIsland`; hoja pasa a `WizardWeightRule` sin fondo propio (parámetro nuevo `transparent`/máscara), con ±0,1 y valor anterior; último pesaje ya existe en el resumen.
- Aviso atípico: cálculo en la hoja con `anchorKg` + fecha del último pesaje.

## 5. Dudas para el usuario
Resueltas: umbral atípico 2,5 kg/≤7 días aceptado; FAB sin compactar aceptado; copy del permiso fijado.
1. Unidad lb: la regla se vería igual con marcas de libra (no dibujada).
