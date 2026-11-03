# CARDIO DEL EDITOR · informe de implementación (30-oct-2026)

Worktree `C:\kw\pu-ce` (rama `pulido/ce`, con `feat/pulido-v1` 816f14484 fusionada: profundidad y movimiento). Fase 1 y fase 2 juntas. Paquete `ce`, puntos 5, 6, 7, 8, 9, 11 y 26. Sin commit.

## Qué se hizo
- **5 · miniaturas**: las 12 ilustraciones del lienzo (`CE_ART`) como dos trazados por modalidad (`design/CardioArt.kt`, generado desde el JS: círculos y rectángulos pasados a arcos; grosor `min(2,6; 1,75 + (s − 56)·0,01)`); usadas en la hoja (84 dp), la fila (56), el menú (60) y la ficha (168). Sin bitmaps.
- **6 y 11 · tarjeta**: `CardioEditorCard` reescrita: Forma → magnitudes → esfuerzo (10 barras con las zonas reales) → objetivos → accesos. Fuera: la dosis repetida, el chip «Al final», el carrusel de plantillas, «Añadir series», «Añadir cardio» dentro. Hojas nuevas: Plantillas (7 + 7 patrones), Protocolo, Avisos y pantalla, Zona de pulso, menú de bloque.
- **7 · «Avanzado» eliminado entero** (`maps/cardio-avanzado.md`): ritmo solo donde es min/km (`CardioCatalogItem.supportsPace`), zona de pulso = punto medio de la banda (`CardioZones`), objetivo kcal/metros en Protocolo, avisos en la hoja de avisos. Los campos del modelo no se migran ni se borran. **Avisos por cardio también en Intervalos**: `CardioDetails.beepsEnabled/voiceCuesEnabled/vibrationEnabled` (nulos = como antes) y `CardioCueRules.transitionCue/countdownCue(details)`; el cardio en vivo los lee (`WorkoutViewModel`, 3 líneas).
- **8 · selector único** (`CardioValueSheet.kt` + `domain/cardio/CardioValueScale.kt`): cifra tocable (teclado del sistema), regla con arrastre y ajuste a la marca, −/+, atajos, modos Con duración / Libre / Omitir, estados de error y apagado. 16 escalas (duración, esfuerzo, pausa, calentamiento, enfriamiento, entre series, bloque, distancia, velocidad, inclinación, cadencia, vatios, ritmo, RPE de bloque, kcal, metros). `CardioMagnitudePickers.kt` conserva solo las dos ruedas que usa la tarjeta del cardio en vivo.
- **9 · menú y ficha**: `CardioQuickActionsSheet` (Ficha, Cambiar máquina, Duplicar, Pasar antes/después de la fuerza, Eliminar; sin aproximación, movilidad ni superserie) y `CardioFicha` (textos de las 12 en `domain/cardio/CardioFichaGuide.kt`, generados desde el documento del lienzo). «Ver progreso» abre el progreso del ejercicio.
- **26 · posición**: rótulo «CARDIO · AL FINAL» con flecha y «+» (`CardioSpaceViews.kt`); desaparecen el selector grande (`CardioPlacement` ya no se emite) y el pie «Añadir cardio» (solo queda el estado vacío «Todavía sin cardio»). Avisos de la maqueta («El cardio se hace antes de la fuerza.», «Cardio duplicado.»…) por la snackbar del editor; destello `ce-fresh` del cardio nuevo.

## Archivos
Nuevos (rutas bajo `android-native/app/src/`): `main/…/domain/cardio/{CardioValueScale,CardioZones,CardioFichaGuide}.kt`, `main/…/screens/sessioneditor/components/{CardioEditorPrimitives,CardioEditorSheets,CardioValueSheet,CardioFicha,CardioQuickActionsSheet,CardioSpaceViews}.kt`, `main/…/screens/sessioneditor/design/CardioArt.kt`, `test/…/domain/cardio/{CardioValueScaleTest,CardioCueFlagsTest}.kt`, `test/…/render/CardioEditorRenderSheetTest.kt` (+ clase 360 × 740).
Reescritos: `CardioEditorCard`, `CardioIntervalsEditor`, `CardioCatalogSheet`. Tocados: `CardioDoseLine` (series en «m:ss»: «8 × 0:20 / 0:10»), `CardioMagnitudeCell`, `CardioMagnitudePickers`, `data/models/{Session,CardioCatalog}.kt`, `domain/cardio/{CardioCueRules,CardioRepeatGrammar}.kt`, `screens/workout/WorkoutViewModel.kt`, y los compartidos con la tanda A2 del editor, con cambios mínimos: `ExerciseEditorExpanded` (el cardio sale pronto con su marco de 4 · 16 · 20), `ExerciseEditorCard` (miniatura de 56, destello, gráfica solo cerrada), `SessionEditorScrollRenderer` (rótulo, estado vacío), `SessionListItems` (sin `CardioPlacement` ni pie con cardios; divisor de cardio siempre), `SessionEditorSheets` (catálogo y menú con hoja propia), `SessionEditorScreen` (3 líneas: ficha, avisos, posición), `SessionEditorViewModelCardio/QuickActions` (avisos y destello), `ExerciseCatalogV2Info` (ficha del cardio).
Eliminados: `HiitTemplatePickerDialog.kt`, `SessionCardioEditorRenderSheetTest.kt`. Tests actualizados: `CardioEditorEddComposeTest` (reescrito), `SessionCardioEditorDoseLineTest`, `SessionEditorEmptySpacesTest`, `SessionListItemsTest`, `SessionListEdgesTest`, `SessionEditorEddRenderSheetTest`.

## Verificación
- JVM: `CardioValueScaleTest`, `CardioCueFlagsTest`, `CardioCueRulesTest`, `CardioRepeatGrammarTest`, `CardioHiitProgramBuilderTest`: 26 pruebas, 0 fallos.
- Android (cola): compila; hoja de render de 25 escenas × 100 % / 130 % / 360 × 740 (100 PNG en `C:\kw\pu-ce-render`); los tests de lista, dosis, formas, hojas y espacio de cardio en verde. `CardioEditorEddComposeTest` (21 pruebas): en verde en la última corrida (BUILD SUCCESSFUL) con las hojas pintadas en línea. Las hojas de render se regeneraron en la corrida anterior (todas pasan).
- Comparación (`tools/compare.cjs`, lienzo SIN isla ni barra: `shots/ce-nf`; app con hueco en blanco para isla, barra y filas de FUERZA, y andamio mínimo de fila): error medio / % de píxeles > 28:

| escena | 100 % | 360 × 740 | 130 % |
|---|---|---|---|
| ce-anadir | 12.2 / 8.88 % | 11.98 / 8.55 % | 16.27 / 11.02 % |
| ce-cambiar | 13.36 / 9.35 % | 13.22 / 9.11 % | 23.38 / 13.75 % |
| ce-ilustraciones-ref | 13.82 / 8.56 % | 16.41 / 10.18 % | 15.4 / 9.44 % |
| ce-lista | 9.11 / 5.15 % | 10.81 / 6.13 % | 14.31 / 6.36 % |
| ce-solo | 8.2 / 4.26 % | 9.72 / 5.06 % | 11.15 / 5.77 % |
| ce-continuo | 8.6 / 6.13 % | 7.49 / 5.18 % | 22.72 / 11.71 % |
| ce-continuo-gps | 9.31 / 6.71 % | 9.13 / 6.77 % | 21.93 / 12.12 % |
| ce-series | 11.24 / 7.15 % | 9.76 / 6.31 % | 27.46 / 15.01 % |
| ce-intervalos | 12.25 / 7.94 % | 11.35 / 7.58 % | 29.29 / 16.53 % |
| ce-duracion | 4.96 / 4.06 % | 5.86 / 4.83 % | 7.18 / 5.92 % |
| ce-duracion-teclado | 18.17 / 18.52 % | 23.37 / 35.31 % | 20.1 / 20.5 % |
| ce-duracion-error | 17.63 / 18.9 % | 22.83 / 35.53 % | 20.04 / 21.04 % |
| ce-esfuerzo | 5.42 / 4.13 % | 6.42 / 4.92 % | 10.06 / 7.01 % |
| ce-calentamiento | 6.83 / 5.29 % | 8.09 / 6.29 % | 9.43 / 7.21 % |
| ce-calentamiento-libre | 3.93 / 3.57 % | 4.64 / 4.25 % | 5.08 / 4.85 % |
| ce-distancia | 5.13 / 3.98 % | 6.07 / 4.74 % | 7.62 / 5.85 % |
| ce-zona | 5.24 / 4.38 % | 6.2 / 5.2 % | 7.47 / 6.16 % |
| ce-protocolo | 9.22 / 9.29 % | 10.93 / 11.05 % | 11.92 / 10.04 % |
| ce-avisos | 11.23 / 10.69 % | 13.33 / 12.71 % | 14.23 / 12.26 % |
| ce-avisos-continuo | 8.08 / 10.18 % | 9.54 / 12.11 % | 10.52 / 11.9 % |
| ce-plantillas | 13.31 / 8.74 % | 11.5 / 7.98 % | 11.29 / 8.33 % |
| ce-menu | 6.22 / 4.99 % | 7.36 / 5.92 % | 12.39 / 9.79 % |
| ce-menu-eliminado | 8.19 / 9.89 % | 8.58 / 10.31 % | 14.12 / 11.25 % |
| ce-ficha | 10.85 / 7.15 % | 9.87 / 6.6 % | 13.23 / 8.25 % |
| ce-ficha-exterior | 12.22 / 7.88 % | 10.03 / 6.7 % | 12.65 / 7.94 % |

Objetivo del encargo (≤ 2,5/255): NO se alcanza en ninguna escena. Lo que queda es (a) la composición del texto (Chrome frente a Skia, 3–6/255 de suelo), (b) las filas, la isla y la barra, que son de A2 y aquí son andamio, (c) al 130 % el lienzo hace más scroll que la hoja (el desfase vertical infla el error), (d) `ce-duracion-teclado/-error`: el lienzo dibuja un teclado (DESV-506). Las escenas de hoja puras quedan en 4–8/255 (duración, esfuerzo, calentamiento, distancia, zona, menú); las de tarjeta en 8–12.

## Decisiones (revisables)
1. Pulsar «RPE» del inspector abre el selector (el lienzo hace un ciclo 1→10 al tocar).
2. «Ritmo» en series/intervalos desaparece de la interfaz (el motor lo pisaba); el dato no se toca.
3. El selector de tiempo teclea «al estilo microondas» con el teclado numérico del sistema.
4. `CardioPlacement` deja de emitirse; el tipo queda en la clase sellada (A2 puede quitarlo).
5. Las hojas del cardio van en `CeSheet` (KpknSheet plana); las pruebas las pintan en línea (`LocalCardioSheetInline`) porque en Robolectric los toques no llegan dentro de `KpknSheet`.

## Abierto
Filas sin cerrar (8), todas del marco del editor (isla superior, barra inferior, pestañas de la semana), que dibuja la tanda A2 del paquete `ed`: ce-S01, S02, S03, S09, V01, G03, G04, A05. Cerrarlas es apuntarlas a `EditorGlass.kt` y a la barra de A2 cuando se fusionen. DESV-500…506 sin aprobar. Por comprobar en el Flip5: vidrio de la ficha (DESV-500), teclado del sistema en el selector, arrastre de la regla con el dedo, fotogramas de `ce-in`, `ce-fresh` y el cursor.

## Riesgos
`ExerciseEditorCard`, `SessionEditorScrollRenderer`, `SessionListItems` y `SessionEditorSheets` los toca también A2: conflictos esperados al fusionar (los resuelvo yo en mi parte). El cardio en vivo (`CardioLiveCard`) sigue usando `CardioDistanceWheelDialog` y `CardioIntWheelDialog`; cuando el paquete `cv` lo rediseñe se pueden borrar. Dos hojas del cardio en pantalla (valor y zona) abren desde la propia tarjeta: en el cajón «Editar series» de la sesión en vivo la tarjeta es la misma, sin avisos de snackbar.

## Fusión con `feat/pulido-v1` (bfff45d70)
Hecha entre corridas, sin cola después: `ExerciseEditorCard` (la fila nueva de A2 conserva mi miniatura de 56 dp, el destello y la gráfica solo con la tarjeta cerrada) y `SessionEditorSheets` (mi hoja de catálogo y de menú de cardio delante del `KpknSheet` común) tenían conflictos, resueltos a mano; `SessionEditorScreen`, `SessionEditorScrollRenderer` y `WorkoutViewModel` entraron limpios. **El árbol fusionado NO se ha compilado ni probado** (la cola ya no admitía otra corrida): lo comprobé leyendo (nadie más usa los símbolos que borré; `WorkoutStructureSheetsHost` llama a `CardioEditorCard` con mi firma). Riesgo: un error de compilación menor en esas dos zonas.
