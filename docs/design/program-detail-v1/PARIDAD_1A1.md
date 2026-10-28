# Paridad 1:1 con los lienzos (orden del 28-oct-2026, ~16:00)

## Orden del usuario (literal, resumida sin cambiar el sentido)

> DEBES APLICAR LAS COSAS AL PIE DE LA LETRA. SI NO VA A QUEDAR 99,999 % IGUAL, NO ME INTERESA TU ENTREGA.
> Program Detail y Progreso (rediseño anterior de este chat) NO cumplen: siguen heredando elementos antiguos que no deberían estar.
> SI APARECEN GRÁFICOS E INFORMACIÓN EN LA ORIGINAL QUE EL NUEVO DISEÑO NO CONTEMPLA, SE ELIMINA. Si no queda claro qué eliminar, PREGUNTAR.
> Ejemplos que hay que eliminar: la tabla gigante de relación empuje/tirón («tabla de mierda fea»); los avisos de la vista de
> Semana sobre procedencia del plan, progreso del plan, temporada y próxima semana («puro relleno»).
> Nutrición: lo del gráfico que se mostrará en Nutrición (la huella del día) aplica SOLO a ese elemento individual; el resto de
> elementos aprobados (registro, etc.) mandan en cómo se ve esa pantalla.

Antes (mismo turno): «Ok» a implementar Registros (lienzo v4, https://claude.ai/artifact/AdMLuhhAnJPpt1iH4kK2xj).

## Lo que ya se sabe

- Los avisos «Temporada» (`ProgramSeasonModule`), «Próxima semana» (`ProgramPlannedLoadModule`), procedencia
  (`ProgramAboutModule`) y progreso del plan (`ProgramProgressModule`) estaban en la Semana del master ANTERIOR
  (164058d4f / de18c9f3c, el APK «KPKN Nutricion v2» que tiene el usuario). En la versión integrada hoy (df3a190b5) ya no se
  llaman; sus archivos siguen en `screens/programdetail/design/` → borrarlos si no los usa nadie.
- La tabla empuje/tirón es `VolumeAnalyticsCard` (`components/ProgramAnalyticsCards.kt`), que se pinta en
  `components/editor/MacrocycleEditorLegacy.kt:505` dentro de Estructura («Revisión del plan»). SE ELIMINA.
- La pantalla real sigue montada sobre piezas viejas: `DayView` (Semana), `MacrocycleEditor` + `MacrocycleEditorLegacy`
  (Estructura), `VolumeView` (volumen de Progreso). Cada una puede arrastrar elementos que el lienzo no tiene.

- OJO: el lienzo v6 SÍ tiene la escena `e-review` «Revisión del plan» (Estructura): avisos con semáforo + «Proporciones
  semanales» (Empuje/Tirón, Empuje H/Tirón H, Cuádriceps/Posterior, Tren sup./inf. como barras) + «Cobertura» (4 filas). La app
  pinta en su lugar la tabla vieja `VolumeAnalyticsCard`. DUDA para el usuario: ¿fuera todo, o la versión compacta del lienzo?
- Hojas de render obsoletas que usan los módulos viejos: `test/.../render/ProgramDetailClosureRenderSheetTest.kt`,
  `ProgramDetailHeaderRenderSheetTest.kt`; prueba `design/ProgramPlannedLoadModuleTest.kt`. Inventario en curso por un agente
  (Opus) → `INVENTARIO_PARIDAD.md`.

## Método

1. Inventario por pestaña (Semana, Progreso, Estructura, cabecera, hojas, Programas, historias, resumen del mes): lo que pinta
   la app frente a lo que tiene cada escena del lienzo (`docs/design/program-detail-v1/src/scenes*.js`).
2. Lo que la app pinta y el lienzo no tiene → se elimina (preguntar solo si no está claro).
3. Lo que el lienzo tiene distinto o falta → se rehace igual (textos, orden, tamaños, colores, iconos).
4. Verificación visual lado a lado: render de la app (Robolectric o emulador) junto a la captura de la escena del lienzo.
5. Después: Registros v4 1:1 (competencias, crear programa, registro de comida, buscar, vitaminas y minerales, historias).
6. Integrar en master y push al terminar.

## Estado

- [x] Inventario → `INVENTARIO_PARIDAD.md` (59 sobra, 101 falta/difiere, 24 dudosos). Dudosos de «quitar o no» preguntados al
  usuario el 28-oct ~16:30; el resto se resuelve con el patrón de la escena más cercana sin perder funciones.
  Master avanzó (35cc86392, Nutrición v2 QA): fusionar antes de integrar.
- [ ] Eliminaciones
- [ ] Correcciones
- [ ] Verificación lado a lado
- [ ] Registros v4

## Respuestas del usuario a los dudosos (28-oct ~16:40)

- E-D1 «Revisión del plan»: **QUITARLA ENTERA** (ni la tabla vieja ni la versión compacta del lienzo; Estructura sin revisión).
- Semana (W-D1…W-D4): «en la vista de semana quita todo lo que no salga en el prototipo, usaremos EL PROTOTIPO como fuente de
  verdad». → nombre/nota de semana bajo el navegador, casilla «Confirmar sesión opcional» en la agenda y días «Fuera de
  programa» salen de la agenda (las funciones que el lienzo ponga en un ⋯ se quedan ahí; lo demás se va).
- Progreso (G-D1, G-D3): «Ambos rediseñados (sin prototipo, para ahorrar tiempo) sin tirar hacia abajo la información
  importante». → los estados vacíos del volumen y la hoja «Calibrar» SE QUEDAN, rehechos con el lenguaje del lienzo.
- Estructura (E-D2…E-D5): «Nada de esto» → mesociclos, aviso «Revisa la estructura temporal» y hojas sin escena SE QUEDAN,
  rehechos con el patrón de e-block / e-dates.
- Resto de dudosos (C-D*, G-D2, H-D*, P-D*): patrón de la escena más cercana del lienzo, sin perder funciones; decisión anotada
  en el informe de cada paquete.

## Ejecución (paquetes en worktrees aislados, subagentes Opus/Sonnet effort high)

Ola 1 en paralelo, cada uno SOLO en su zona y, dentro de `ProgramDetailScreen.kt`, solo en su región:
- PA Cabecera/barra + Semana (región de cabecera ~350-760 y rama SEMANA; DayView.kt; design/ de cabecera y semana).
- PB Progreso (rama VOLUMEN; VolumeView.kt; design/progress/*; hoja Calibrar rediseñada).
- PC Estructura (rama MACROCICLO; MacrocycleEditor.kt, editor/*, design/structure/*; borrar ProgramAnalyticsCards.kt y la
  revisión del plan).
- PE Programas (screens/programs/*).
Ola 2: PD Hojas y menús, tras fusionar la ola 1. Después: borrar módulos muertos y hojas de render obsoletas, fusionar master,
verificación lado a lado, integrar y push. Luego Registros v4.
