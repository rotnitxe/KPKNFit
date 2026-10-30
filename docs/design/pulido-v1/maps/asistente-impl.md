# ASISTENTE · informe de implementación, pasada A (30-oct-2026)

Worktree `C:\kw\pu-as` (rama `pulido/as`), sin commit. Estructura, estados y lógica; sin sombras, vidrio, resplandores ni movimiento (cada sitio lleva su marca `// pulido:<ID>` con el ID de fila de `paridad/as.md`).

## Qué hay
- UI nueva en `screens/sessioneditor/components/assistant/` (estilo `AssistantStyle`, piezas `AssistantPieces`, marco y pestañas `AssistantFrame`, Métricas, Sugerencias, Plantillas, Reglas, `AssistantSheetContent`, mapeadores `AssistantModelBuilders`). La hoja es `KpknSheet` plana (62 % / 93 %).
- Dominio puro nuevo (`domain/sessionassistant/`): `AssistantMuscleBreakdown` (aporte de cada ejercicio a un músculo, semana), `AssistantSuggestionPreview` (caja de efecto en vivo), `AssistantLimitStatus` (estado de cada límite), `TemplatePreviewBuilder` (rellena `TemplatePreview`, Reemplazar/Añadir).
- Estado y VM: `AssistantUiState.kt`, `SessionEditorViewModelAssistant.kt` (Deshacer/Descartar de sugerencias, Deshacer de plantilla, «aplicando/aplicada» dentro de la hoja). `openRulesSheet` abre Asistente › Reglas (Valores / Tiempo). Preset «Tiempo corto»: RIR 2 (y se corrige lo ya guardado).
- Eliminado: `AssistantContentsTab`, `AssistantSuggestionsTab`, `RulesSheet`, `RulesTimeTab`, `SessionEditorSheet.RULES`, `SheetLoadingSkeleton`, los parámetros de reglas de `SessionEditorSheets`. Las etiquetas de las casillas ya no llevan «· ahorra N min» y el motivo de «Ajustar al tiempo» ya no trae la cola de efecto (la cuenta la caja «Duración»).

## Comparación con el lienzo (`tools/compare.cjs`; entre paréntesis, error medio)
Columnas: % de píxeles distintos de la imagen completa; y «estructura» = % distinto dentro de la hoja con ambas imágenes desenfocadas 2 px (bruta/suave no se usa aquí: solo suave).

| escena | 360×880 | 360×740 | 130 % | estr. 880 | estr. 740 | estr. 130 % |
|---|---|---|---|---|---|---|
| as-entrada | 7.81 % (err 9.87) | 6.91 % (err 8.23) | 11.18 % (err 15.12) | 9.3 /  7.7 | 8.7 /  6.6 | 14.0 / 16.4 |
| as-met-media | 7.81 % (err 9.87) | 6.91 % (err 8.23) | 11.18 % (err 15.12) | 9.3 /  7.7 | 8.7 /  6.6 | 14.0 / 16.4 |
| as-met-completa | 10.04 % (err 14.09) | 9.5 % (err 11.71) | 12.98 % (err 18.71) | 10.7 / 10.3 | 10.1 /  9.1 | 13.7 / 18.6 |
| as-met-todos | 9.65 % (err 14.5) | 9.16 % (err 14.17) | 12.92 % (err 19.41) | 10.4 / 12.7 | 9.7 / 12.0 | 13.6 / 19.2 |
| as-met-cardio | 10.32 % (err 15.91) | 10.51 % (err 15.05) | 12.51 % (err 18.96) | 11.1 / 10.9 | 11.2 / 10.0 | 13.5 / 18.7 |
| as-met-vacia | 6.3 % (err 6.52) | 5.83 % (err 6.03) | 10.2 % (err 13.13) | 7.3 /  3.4 | 5.9 /  2.3 | 11.9 / 16.2 |
| as-sug-lista | 9.42 % (err 10.92) | 8.83 % (err 9.57) | 11.95 % (err 14.81) | 9.5 /  4.0 | 8.4 /  2.1 | 12.5 / 11.0 |
| as-sug-aplicada | 6.36 % (err 6.73) | 7.73 % (err 8.16) | 10.19 % (err 12.14) | 7.0 /  1.3 | 8.4 /  1.8 | 11.3 /  8.7 |
| as-sug-vacio | 5.97 % (err 5.68) | 5.55 % (err 5.29) | 9.31 % (err 10.57) | 7.2 /  1.7 | 6.3 /  1.2 | 11.2 / 10.7 |
| as-sug-nota | 5.68 % (err 5.33) | 6.3 % (err 6.06) | 7.71 % (err 7.54) | 6.6 /  2.8 | 8.1 /  3.6 | 9.3 /  5.4 |
| as-sug-carga | 3.39 % (err 3.78) | 3.53 % (err 4.03) | 4.46 % (err 4.88) | 2.5 /  0.5 | 2.4 /  0.9 | 3.4 /  1.3 |
| as-pla-catalogo | 11.18 % (err 12.27) | 10.89 % (err 11.63) | 12.06 % (err 14.07) | 11.9 /  9.5 | 11.5 /  9.3 | 12.8 /  8.9 |
| as-pla-filtros | 8.39 % (err 9.07) | 7.48 % (err 8.1) | 7.74 % (err 8.14) | 8.6 /  5.2 | 6.8 /  3.7 | 8.1 /  2.4 |
| as-pla-vacio | 4.58 % (err 4.79) | 5.79 % (err 6.2) | 6.44 % (err 7.28) | 4.9 /  2.0 | 6.2 /  3.5 | 7.0 /  4.3 |
| as-pla-vista | 10.28 % (err 12.12) | 9.5 % (err 10.6) | 11.19 % (err 13.52) | 11.4 /  8.5 | 10.4 /  6.3 | 12.5 /  7.4 |
| as-pla-aplicar | 8.06 % (err 8.52) | 8.45 % (err 9.02) | 9.72 % (err 11.46) | 9.1 /  2.5 | 9.4 /  3.0 | 10.9 /  5.2 |
| as-pla-aplicando | 4.2 % (err 5.51) | 5.03 % (err 6.39) | 4.74 % (err 6.01) | 4.4 /  5.0 | 5.2 /  5.9 | 5.0 /  3.4 |
| as-pla-aplicada | 2.77 % (err 2.69) | 3.35 % (err 3.16) | 4.27 % (err 5.13) | 2.8 /  0.5 | 3.3 /  0.6 | 4.5 /  1.9 |
| as-pla-mias | 6.27 % (err 7.15) | 7.78 % (err 8.77) | 8.89 % (err 9.2) | 6.9 /  3.1 | 8.5 /  4.7 | 10.1 /  4.8 |
| as-pla-guardar | 5.56 % (err 5.94) | 6.83 % (err 7.16) | 6.88 % (err 7.73) | 6.0 /  2.6 | 7.4 /  3.7 | 7.7 /  3.7 |
| as-reg-valores | 4.96 % (err 5.53) | 5.93 % (err 6.28) | 6.56 % (err 7.46) | 5.4 /  2.0 | 6.4 /  2.2 | 7.2 /  3.1 |
| as-reg-compuestos | 5.01 % (err 5.61) | 5.94 % (err 6.32) | 6.53 % (err 7.39) | 5.5 /  2.1 | 6.4 /  2.4 | 7.2 /  3.0 |
| as-reg-limites | 9.38 % (err 11.49) | 9.86 % (err 12.02) | 10.04 % (err 13.06) | 10.2 /  8.4 | 10.2 /  9.2 | 10.6 /  7.4 |
| as-reg-limites-vacio | 7.17 % (err 8.7) | 7.02 % (err 8.31) | 8.88 % (err 11.13) | 9.1 /  9.3 | 8.6 /  8.8 | 10.5 / 12.1 |
| as-reg-tiempo | 7.68 % (err 8.53) | 7.61 % (err 8.28) | 9.14 % (err 10.81) | 7.9 /  3.9 | 7.6 /  3.1 | 10.0 /  5.8 |

Lectura: la diferencia por **marco** (la barra de estado y el contorno del teléfono del lienzo, el fondo del editor de muestra) y por **tipografía** (Inter y Syne de Chrome ocupan algo más que las de la app: cambian los saltos de línea, p. ej. «Aún no hay nada que medir») explica casi todo el residuo de las imágenes completas; no hay sombras ni vidrio en ninguna de las dos pasadas todavía. Estructura (hoja desenfocada): ≤ 5 % en 14 de 25 escenas a 880; el resto (Métricas 7–11 %, catálogo 9,5 %, límites 8 %, vista de plantilla 8,5 %) viene de 2–5 px de deriva vertical acumulada (cifras Syne de una línea, saltos de línea distintos) y de las portadas de líneas (distinta semilla).
La deriva vertical con la letra al 130 % sigue siendo la mayor (hasta 19 % en Métricas): la escala de letra de Chrome (`calc(Npx*min(fs,tope))`) y la de Compose no recortan igual.

## Decisiones (marcadas como DECISIÓN, el lienzo no las contempla)
1. «Dentro del límite» en la cifra de Duración va en verde (el lienzo lo pinta en ámbar siempre); sin límite no hay línea.
2. «Mis plantillas»: tocar una fila abre su ficha (con Aplicar plantilla); el lienzo no trae «Aplicar» en la fila.
3. «Editar» (menú ⋯): formulario de nombre, descripción y nivel. Salen del diálogo viejo: foco, duración, ids de split, etiquetas de día y «autogeneración» (los datos se conservan sin tocar).
4. Plantillas de reglas propias: mantener pulsado = Renombrar / Eliminar (el lienzo solo dice que se pueden).
5. Portada de plantilla: estilo y semilla por hash del id, fondo por grupo (el catálogo no trae portada); la línea de la tarjeta repetida en varias plantillas se sustituye por su dato de equipo.
6. Altura inicial: media; completa si se abre en Reglas. Una fila de cardio por cada cardio.
7. Textos propios para «Añadir» en aplicando/aplicada.
8. Objetivos táctiles de 44 dp como en el lienzo (la regla común pide 48): se respetó el lienzo.
9. Cajas de efecto solo para `time_overage` y `user_limits` (las dos reglas que existen).

## Abierto / riesgos
- Filas S, V, G, A, T, X, J sin cerrar (marcadas). Las «(base)» (`.pl-glass`, `.tok.*`, `.split7`, `.story .*`, `.draw`, `.plate`, `.wr-bars`, `as-reopen`) no pertenecen al código del asistente: son del editor de fondo o del propio prototipo.
- No se ha podido comprobar en el teléfono ni la animación de entrada; el mapa de tamaños del editor de fondo se dibujó a mano.
- Reglas › límites en pantallas con el teclado y el selector de descanso/tiempo (diálogos de la app) no se dibujaron: solo se prueban abiertos por código.
- `git merge feat/pulido-v1` NO hecho (había corrida en marcha); conflictos probables en `SessionEditorScreen.kt`, `SessionEditorModels.kt` y `SessionEditorViewModel.kt`.
