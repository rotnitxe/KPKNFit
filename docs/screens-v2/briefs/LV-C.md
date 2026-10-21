# LV-C · Aproximación y movilidad en vivo (H5, H6)

Worktree `C:\kw\u11`, rama `feat/screens-v2`, sobre CAL3. Lee primero:
- `C:\kw\u11\CLAUDE.md` y `docs/screens-v2/PLAN_IMPLEMENTACION.md` (las leyes);
- los informes `LV-A.md`, `LV-B.md`, `LV-AF.md`, `LV-BF.md`, `CAL3.md` y `QA_EMU_LVB.md` (su última sección);
- `docs/screens-v2/reports/PROTO_R5_aprox_movilidad.md`.

Tableros aprobados, en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\`: **H5-AproxVivo.dc.html** y **H6-MovilidadVivo.dc.html**. Ábrelos en un navegador local.

## 0. Regresión de la QA de LV-BF

`SessionRevisionFlowDeviceTest#realActivityReducedHeightKeepsCardAndLineComplete` falla en el emulador con «Voz conserva al menos 48dp táctiles» (`:841`). A 760 dp de alto, el botón de voz de la cabecera queda por debajo de 48 dp. Pasaba con LV-A.

Corrígelo sin perder la elipsis de los títulos largos. Añade una prueba de Robolectric que mida los objetivos táctiles de la cabecera a 760 dp, con fuente al 100 % y al 130 %.

## Qué construir

Lo que dice el usuario: «No pueden existir botones afuera de la tarjeta, ni mucho menos que la tarjeta de aprox/mov sea de distinto tamaño que la de series efectivas».

1. **Tarjeta de aproximación (H5)**, exactamente igual que la tarjeta de serie: 312 × 425, misma posición, radio y borde, en el marco de LV-A. **Todo dentro**:
   - los kg editables con coma decimal;
   - las marcas de hecha;
   - añadir aproximación;
   - el descanso corto;
   - saltar la fase;
   - los mensajes.

   Las listas largas se desplazan dentro de la tarjeta. Conserva los valores reales:
   - `PreparationCarouselCards.kt` ≈ :300/329/343: 60 s de descanso, de 10 a 95 % y al menos 1 rep;
   - `WarmupRules.kt` ≈ :6/14/23: aviso al 85 % y máximo recomendado de max(4, 2 × series efectivas);
   - `WarmupMobilityLiveCards.kt` ≈ :215/295/663/688/741/790/800/852.
2. **Tarjeta de movilidad (H6)**, con el mismo tamaño y todo dentro:
   - reloj por movimiento y reloj global independientes, y la lista, como páginas internas de la tarjeta;
   - dosis por tiempo o por reps;
   - marcar o avanzar;
   - +30 s;
   - saltar la fase.

   La movilidad avanza al marcar, no automáticamente al llegar a cero.
3. **Nada entre el relator y la tarjeta ni entre la tarjeta y la línea.** Aproximación y movilidad aparecen en la línea V1 como pasos con su icono; esto ya existe desde LV-A.
4. **Pantalla corta** (360 × 740): la tarjeta mide lo mismo y nada se desborda.

## Pruebas

- **Compose/Robolectric:** cada control de H5 y H6 dentro de los límites de la tarjeta, que mide 312 × 425 a 880 y a 740; añadir aproximación; editar kg; marcar; descanso corto; saltar fase; relojes (iniciar, pausar, reiniciar, finalizar, +30 s); lista larga con desplazamiento interno; dosis por reps.
- **androidTest:** adapta las pruebas de calentamiento y movilidad en vivo que dependan del diseño anterior, sin rebajar aserciones sin explicarlo. Añade renders de H5 y H6 a 880 y a 740.
- Al final:
  - `:app:compileBaseDebugKotlin`
  - `:app:compileBaseDebugAndroidTestKotlin`
  - `screens.workout.*` en verde
  - `:app:assembleBaseDebug :app:assembleBaseDebugAndroidTest -I C:/kw/tools/dbg-suffix.init.gradle`

## Reglas

Las de siempre. Informe en `docs/screens-v2/reports/LV-C.md`.
