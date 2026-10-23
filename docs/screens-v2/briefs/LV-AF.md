# LV-AF · Arreglos de la QA de LV-A (paquete corto, después de LV-B)

Lee `docs/screens-v2/reports/QA_EMU_LVA.md`. Las capturas y fotogramas están en `C:\kw\shots\emu-lva\`; mira `story\f095–f103.png` y `files\qa16-live-controls\`.

1. **«Historia» desde la cabecera no muestra la historia.** `SessionRevisionFlowDeviceTest#storyFromTheHeaderOpensTheExistingStoryWithoutSavingTheWorkout` falla siempre: existe `story-sheet`, pero `story-templates` no se ve y en pantalla no aparece ninguna hoja.
   - Diagnostica: puede ser una hoja de alto cero, un anfitrión de overlay equivocado o algo fuera de pantalla.
   - Corrige la app para que la historia se abra visible y completa, con la invariante de la prueba: no guarda ni termina la sesión.
2. **Hoja «Ultrarrápido».** Tras pulsar Registrar se queda en «Registrando…» mientras el descanso ya está abierto detrás.
   - Al guardar, la hoja se cierra en cuanto se confirma el registro.
   - Si el guardado falla, muestra el error en la hoja.
   - Reproduce con una prueba la intermitencia de `exerciseMenuOpensRmAndQuickRecords…`.
3. **Línea tras el registro rápido.** Comprueba que, tras registrar desde el formulario rápido, la línea V1 marca la serie hecha y avanza (1/4, ✓S1, S2 activa), igual que en el registro canónico. En la prueba de Historia se veía «0/4 · S1».
4. **Decimales con coma** en la calculadora RM (`e1RM` y la tabla 1–10 RM), y en cualquier otra hoja migrada que use `toString()` de un Double.
5. **Panel «Drenaje de …» tras registrar.** No debe tapar el ✓ ni la línea de forma persistente. Comprueba su duración y su posición; si tapa controles, que se cierre al tocar o que se coloque sin solaparlos (ley 1).

**Pruebas:** reproducción de cada punto, `screens.workout.*` en verde, `androidTest` compilado y APK `.dbg` ensamblado.

**Reglas:** las de siempre. Informe en `docs/screens-v2/reports/LV-AF.md`.
