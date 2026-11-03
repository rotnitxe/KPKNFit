# ASISTENTE · informe de la pasada B/C (30-oct-2026)

Worktree `C:\kw\pu-as` (rama `pulido/as`, con `feat/pulido-v1` fusionado sin conflictos). Sin commit.

## Hecho
- Filas S, V, G, A, T, X, J de `paridad/as.md`: 73 `[x]` y 1 `[~]` (X05, DESV-300: el botón «Abrir asistente» es del prototipo). Las filas «(base)» (S08-S10, V01, G03-G05, A20-A23, X11) no son de este código y apuntan al preset o a la familia de los cimientos.
- Profundidad con `ui/design/depth` (presets y `copyLayer` para el valor exacto); movimiento con `ui/design/motion` (`AsAnim` en `AssistantMotion.kt`, duraciones EXACTAS de cada fila, curvas `suave`/`estandar`).
- `KpknSheet` gana el parámetro opcional `lienzo: KpknSheetSpec` (sombra de la hoja, velo, entrada, salida al 102 %, altura animada); los demás llamadores no cambian.
- Objetivo táctil de 48 dp (`asTouch48`, dentro de `asClick`): el área pulsable mide 48 en los ejes fijos menores y el dibujo ocupa lo mismo (prueba `AssistantTouchTargetTest`: columna de 212 dp, botones ≥ 48).
- Arreglos de pasada A vistos al comparar: arco del giro (95,5° desde las 3, radio 36 del lienzo) y posición del menú de «Mis plantillas».

## Pruebas
`AsistenteRenderSheetTest` (25 escenas × 3 tamaños + 7 de fotogramas), `AssistantTouchTargetTest`, `AssistantRulesPaneTest`, `AssistantSheetComposeTest`, `EditorSheetsEdeTest`: verdes. `SessionAssistantRedesignReproTest`: 7 de 7 (carril JVM).

## Comparación (error medio /255 a 360×880, 740, 130 %)
El residuo lo domina el texto y la deriva de unos px (Chrome frente a Skia) y el marco del teléfono; no baja de 2,5 en las escenas con texto (igual que en la pasada A). Detalle en el informe del mensaje final.
