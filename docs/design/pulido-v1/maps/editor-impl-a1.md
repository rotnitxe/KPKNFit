# EDITOR · tanda A1 de la pasada A · informe (30-oct-2026)

Worktree `C:\kw\pu-ed` (rama `pulido/ed`). Alcance: configuración de las series del ejercicio abierto (18 escenas `ed-serie…`, `ed-modo`, `ed-preparacion`, `ed-unilateral`, `ed-opciones`, `ed-descanso-ejercicio`). Sin sombras, vidrio ni animaciones (pasadas B/C); cada sitio lleva su marca `// pulido:ed-…`.

## Archivos (rutas relativas a `android-native/app/src/`)
Nuevos: `main/…/sessioneditor/components/EditorSeriesPrimitives.kt` (campo −/+, menú anclado, hoja, chips, fila flexible, deslizador, iconos), `…/EditorSeriesSections.kt` (carga, repeticiones, intensidad, descanso), `test/…/render/EditorSeriesRenderSheetTest.kt` (+ clase de 360 × 740), `test/…/components/EditorSeriesLogicTest.kt`.
Modificados (main): `SessionEditorControls.kt` (RestSheet), `components/{AmrapSetFocus, ExerciseEditorExpanded, ExerciseOptionsBar, ExercisePreparationSection, ExerciseSetList, InlineSetRow, InlineSetRowTechniqueChips, PreparationCarouselCards, PreparationChips, RmSetFocus, SessionEditorSeriesChip, SetFocusControls, SetModeGoals}.kt`.
Modificados (test): `EdbFocusControlsTest, ExerciseEditorExpandedComposeTest, ExerciseOptionsBarComposeTest, ExerciseSetListComposeTest, LoadTypeGridComposeTest (reescrito), PreparationChipsComposeTest, SupersetRoundsFocusIntegrationTest, WorkoutEditSeriesSheetBoardTest`.

## Verificación
`:app:compileBaseDebugKotlin` verde. Por la cola: 14 clases (las 8 de arriba + `InlineSetRowStateTest`, `SupersetRoundsC3ComposeTest`, `WorkoutEditSeriesAuditTest`, `EditorSeriesLogicTest` y las 2 hojas de render) → 189 pruebas, 0 fallos.

## Comparación con el lienzo (`tools/compare.cjs`, % de píxeles distintos / error medio de 255)
Lienzo SIN isla ni barra (son de A2) y recortado bajo la fila del ejercicio (A2); la app con una franja vacía de 68 dp en lugar de la fila. A 130 % se alinea por la primera ficha (la fila de A2 crece); en las hojas (modo, opciones, descanso del ejercicio, aplicar) no se alinea porque van pegadas al borde de abajo.
| escena | 100 % | 130 % | 360 × 740 |
|---|---|---|---|
| ed-serie | 5,3 / 6,7 | 10,3 / 20,2 | 5,2 / 6,9 |
| ed-serie-carga | 7,3 / 8,5 | 4,5 / 5,0 | 7,7 / 9,1 |
| ed-serie-rango | 5,6 / 7,1 | 11,1 / 21,4 | 5,6 / 7,3 |
| ed-serie-intensidad | 6,0 / 7,9 | 10,1 / 17,4 | 6,0 / 8,3 |
| ed-serie-fallo | 7,7 / 11,9 | 13,5 / 24,1 | 7,5 / 11,0 |
| ed-serie-descanso | 8,4 / 17,1 | 11,8 / 24,3 | 7,9 / 15,0 |
| ed-serie-acciones | 5,6 / 10,0 | 9,4 / 20,7 | 5,9 / 11,8 |
| ed-serie-aplicar | 6,3 / 14,8 | 12,2 / 20,2 | 6,0 / 15,0 |
| ed-modo | 8,1 / 11,0 | 13,0 / 17,1 | 7,9 / 9,3 |
| ed-serie-amrap | 5,8 / 9,4 | 6,7 / 9,2 | 5,6 / 7,3 |
| ed-serie-rm | 5,7 / 8,1 | 9,9 / 14,8 | 5,6 / 7,3 |
| ed-serie-solorpe | 6,3 / 10,7 | 7,2 / 10,2 | 5,8 / 7,6 |
| ed-serie-tiempo | 5,2 / 6,5 | 6,3 / 8,5 | 5,4 / 7,1 |
| ed-serie-distancia | 5,1 / 6,2 | 6,0 / 7,6 | 5,5 / 6,6 |
| ed-preparacion | 8,2 / 13,6 | 10,8 / 20,3 | 7,9 / 12,2 |
| ed-unilateral | 5,7 / 8,4 | 7,0 / 10,7 | 6,4 / 9,4 |
| ed-opciones | 6,0 / 10,1 | 8,2 / 13,5 | 5,5 / 7,9 |
| ed-descanso-ejercicio | 6,6 / 13,7 | 9,9 / 16,0 | 7,0 / 12,9 |

Estructura: las posiciones verticales de los campos quedan a ±1 dp del lienzo a 100 % (se midió borde a borde con ed-serie: carga, repeticiones, descanso). Lo que queda de diferencia a 100 % (≈ 5–8 %) es la composición del texto (Chrome frente a Skia), el filete de 1 px de la ficha y el icono de «inicio» del móvil del lienzo; el vidrio y las sombras (menús, hojas, pulgar) están pendientes por diseño. A 130 % hay saltos de línea distintos en la barra de opciones (el lienzo pasa «⋯» a la 2.ª línea) y en el texto de las fichas de técnica.

## Decisiones
1. La serie elegida siempre tiene foco (la primera por defecto); tocar una ficha ya no la cierra.
2. RM: se quitan «Reps estimadas» y «Peso (kg)» editables (el lienzo solo tiene «% del RM» + deslizador y la cifra de carga). La referencia 1RM/PR queda en «Carga inteligente»; solo con % del TM se muestra el campo del TM.
3. AMRAP: se quita «Sin mínimo» (bajar de 1 deja «—»), la hoja «¿Qué hace cada uno?» y el segmentado; el calibrador conserva alcance, vista previa y requisitos.
4. La cifra de carga abre Carga inteligente (`onOpenSmartLoad`); se eliminan `SmartLoadModule` y `CustomUnitModule` (la unidad sigue en «⋯»).
5. «Eliminar serie» gana «Deshacer» (aviso de 5 s bajo las fichas; solo cuando la pantalla da `onTransformExercise`).
6. Descanso entre lados avanza de 5 en 5 y su botón dice «−5/+5» (el lienzo dice −15/+15 con paso de 5).
7. Aproximación: se quitan Kg, deslizador, descanso propio y «Cerrar»; los dos campos miden 25 dp como el lienzo (efecto de su flex); las fichas dicen «A1 · 40 % · 5 reps»; los bloques ya no se pliegan; la ficha de movilidad es texto (sin miniatura) y la dosis en segundos no pasa a m:ss.
8. Sin carga estimable se lee «Sin estimar»; en «Personalizado» sin unidad el rótulo dice «Unidad» / «unidad por configurar».
9. Rango: mín ≤ máx, se guarda `targetReps = máx` y `targetRepsRange` (puede quedar mín = máx dentro de «Rango»).
10. Tiempo: las fichas dicen «0:40» (no «40 s»), como el lienzo.

## Impacto fuera del editor (revisar)
`InlineSetRow` (piel Lista), `RmSetFocus`, `AmrapSetFocus`, `SetRestControl`, `RestSheet`, `ExerciseModeSheet` y `EditorConfirmationSheet` son compartidos: el cajón «Editar series» de la sesión en vivo y las rondas de superserie heredan la misma piel (sus acciones propias —Subir/Bajar/Quitar del plan— no cambian). Sus pruebas se actualizaron; no se probó en teléfono.

## Abierto
- Filas de la matriz `ed`: ninguna se cierra en A1 (son de B/C). Marcas puestas: S05, S06, S07, G01, A06, A08, A10–A15, T01, T08, T09, T19, T20, T23, T26–T28, T30–T34, X07, X09, J01, J05, J08.
- Sin tocar (no están en las escenas y son de la A2 o de otro paquete): lista, isla, barra, «Añadir», borrar/ordenar; cardio; panel «Superserie activa» y «Relación» (siguen con la piel antigua); foco de dosis de movilidad (H4) con la piel antigua; en `InlineSetRow` queda la piel «Tarjeta» antigua (sin llamadas desde la lista).
- Posible desviación: A08/A15 (rebote del valor) y el filete punteado (4 dp + 3 dp) se calibran en B/C.
