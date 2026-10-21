Corrección de T2 (ronda 3 de 3, la última). Buen avance: con `catalog/` presente, el editor y el catálogo pasan (30 + 3 + 6 tests), lo que confirma que eran de entorno; `CatalogDetailContentTest.verTodosDespliegaLosMusculosQueFaltan` («Trapecio») lo trato como rojo previo ajeno y NO lo toques.

Quedan los 4 tuyos, todos con la MISMA aserción: `assertFalse(layouts.single().hasVisualOverflow)` en `ProgramVolumeModuleTest.assertPlannedAndRealUnits` (línea ≈ 174, 3 tests) y en `ProgramVolumeT2RenderSheetTest` (línea ≈ 115). Pistas del orquestador:
- Miré el PNG al 130 % de tu ronda 2: «6» arriba y «series/sem» debajo se ven COMPLETOS, sin recorte ni solape. El diseño está bien.
- La aserción falla incluso para el texto corto «series» (caso total), que cabe de sobra. Eso apunta a que `hasVisualOverflow` marca true por otra razón (p. ej. un `Text` con `softWrap = false` cuya caja es más estrecha que la anchura natural del texto bajo las restricciones del test, o un artefacto de métricas de fuente de Robolectric), no a un recorte real.

## Qué hacer (UNA sola corrida de Gradle para todo)
1. **Distingue real de artefacto** dentro de la propia aserción: para cada texto comprobado calcula y, si algo falla, incluye en el mensaje del `assertTrue` los datos que lo explican: `layout.lineCount`, `layout.size`, la anchura natural (`layout.multiParagraph.width`/`maxIntrinsicWidth`), `layout.didOverflowWidth`, y `getBoundsInRoot()` del nodo frente a los de su contenedor.
2. **Sustituye la medida por criterios que prueben de verdad «la unidad está completa y no se recorta»**, sin debilitar la intención ni usar `@Ignore`:
   - el texto se dispone en **una sola línea** (`lineCount == 1`) y **sin truncar** (`layout.getLineEnd(0, visibleEnd = true) == texto.length`, y sin puntos suspensivos);
   - los límites del nodo (`getBoundsInRoot()`) quedan **dentro** de los de la fila/ventana de prueba (no sale por la izquierda ni por la derecha);
   - se mantienen intactas las comprobaciones de contenido (`contentDescription` «6 series/sem», la unidad sin «/sem» en el total, «Real 4 …»).
   Si en el paso 1 descubres que SÍ hay un recorte real (la caja del texto es más estrecha que su anchura natural), arregla el diseño de la fila (p. ej. quita `softWrap = false` o deja que la columna tome su anchura intrínseca) en vez de relajar la medida.
3. Deja el render guardando sus PNG antes de las aserciones (ya lo hace); mira otra vez los dos PNG y confirma que no cambió nada visual.

## Corrida única de verificación (por `gradle_solo.sh`)
Filtros: `com.example.kpkn.screens.programdetail.*`, `com.example.kpkn.render.ProgramVolumeT2RenderSheetTest`, `com.example.kpkn.screens.home.VolumeConsumersTest`, `com.example.kpkn.domain.training.VolumeAccountingContractTest`, `com.example.kpkn.domain.training.TrainingWindowTest`, `com.example.kpkn.domain.training.NativeProfileRecipeAndFitterTest`. Esperado: TODO en verde. Cuenta los XML por suite.

## Reglas y entrega
Mismas de COMUN.md. Solo `ProgramVolumeRow.kt`/`ProgramVolumeModule.kt` (si hay recorte real) y los dos archivos de test mencionados. Commit aparte `test(programa): comprobar la unidad completa sin depender de hasVisualOverflow` (o `fix(programa): …` si cambiaste el diseño) con `Co-Authored-By: Codex <noreply@openai.com>`. Esta es la ronda 3 de 3: si no cierra, PARA y dime exactamente qué mide cada comprobación y qué valores obtuviste. Informe ≤ 200 palabras con la tabla de tests por suite y qué descubriste (¿recorte real o artefacto?).
