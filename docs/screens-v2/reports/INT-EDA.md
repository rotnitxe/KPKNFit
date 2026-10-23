# INT-EDA · Cierre acotado de omisiones A1/A5

Estado: integrado y verificado en u11. Compilación y 96 casos únicos verdes en corridas separadas; renders nativos revisados. Sin adb ni instalación en esta continuación. El worktree de preparación no ejecutó Gradle.

Base: `C:/kw/u26-int-eda`, rama `codex/sv2-int-eda`, creada desde integración `67ded417b`. El worktree C3 `u24-int-c3` permanece intocable y limpio.

## Qué se comprobó frente a los tableros

Fuentes leídas: `reports/ED-A.md`, decisiones del 20-oct, los tableros reales `Main.dc.html` (A1) y `A5-Semana.dc.html`, y los componentes FrameHeader/TopBar/BottomBar/WeekSheet existentes.

- La omisión de A5 es real: faltaban las letras A/B/C/D junto a los nombres de variantes. El tablero NO establece un color de acento por variante: sus badges tienen fondo `#1A1A1A`, 22 × 22 dp, radio 6 dp y letra Inter Bold 11 sp. Activa: `#F2EEE6`; inactiva: esa misma tinta al 62 %. Se implementa ese diseño, sin inventar una clasificación multicolor a partir de la frase antigua «insignias de color».
- La isla A1 ya conserva todas sus funciones y su altura de 64 dp. Su reparto sí deja «Añadir» con el sobrante después de «Asistente» y «Reglas», cuyos márgenes y texto crecen con la fuente; merecía comprobar el tamaño real de decisión al 130 %. Se usa el token `KpknType.action` (Inter SemiBold 14/20, coincidente con los 14 sp del tablero) y se reduce el margen interno lateral de las dos acciones de 10 a 6 dp. Se mantienen anillo, contador, «+ Añadir», «Asistente» y «Reglas», y sus callbacks distintos.
- La píldora día/semana estaba limitada a una línea. Se permite un máximo de dos líneas dentro de sus 48 dp, con Inter 13/16. «Jue · Semana 3» debe verse completa en las medidas aprobadas. Un nombre arbitrario muy largo puede seguir elidido: se conserva una etiqueta accesible completa «Abrir la semana: …» y su misma acción, y se cubre expresamente ese caso sin exigir que nunca haya elipsis.
- El nombre y la descripción del FrameHeader ya envuelven líneas; no se reimplementan ni se altera ese componente. Las elipsis informativas del protocolo o de nombres extraordinariamente largos no se consideran por sí mismas defectos.
- Las flechas de día eran de 44 dp y los días de la semana de 40 dp de toque. Pasan a objetivos de 48 dp. Los círculos visibles de los días siguen siendo de 40 dp. La hoja usa margen específico de 12 dp para los días: 7 × 48 = 336 dp, todos visibles a la vez en 360 dp. Los otros módulos/títulos conservan su margen previo de 16 dp mediante `moduleInset = 4.dp`. Solo una anchura disponible menor de 336 dp activa desplazamiento horizontal; no se oculta Domingo en el ancho aprobado.

## Funciones conservadas

Orden L/M/X/J/V/S/D; día actual; punto de sesión, competición y contador; pulsación larga de variantes; selección de sesión; cambiar principal; roadmap y semanas; crear sesión en día vacío con cancelar; variantes A–D; original A protegida; crear derivada con su nombre y cancelar; eliminar variante activa solo fuera de A. Los badges son decoración, la selección sigue en el `KpknRadioRow` original. Las variantes mantienen nombres que envuelven sin limitar líneas.

La barra conserva Atrás, anterior/siguiente, abrir semana, Transferir y Versiones; el pie conserva Tiempo, Añadir, Asistente y Reglas. No se añaden opciones ni se cambian motores, Room, datasets o scripts de build.

## Pruebas y renders preparados

Nueva clase `com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameIntegrationGeometryTest`, con gráficos nativos y once casos:

- Cuatro combinaciones A1: 360 × 740 y 360 × 880, letra 100 y 130 %. Comprueba límites de pantalla, objetivos de 48 dp, acciones que no se solapan y texto completo mediante `TextLayoutResult`; pulsa las diez acciones reales, incluidos Transferir y Versiones por el menú nativo.
- Cuatro combinaciones A5: abre el `KpknSheet` real con un click nativo desde la píldora, exige los siete días visibles simultáneamente y pulsa Domingo sin desplazamiento horizontal; cancelar no crea sesión. Comprueba badges de 22 dp, tinta activa/inactiva exacta y fondo `#1A1A1A` desde píxeles del render nativo. Nombres completos de variantes, objetivos de selección, B activa y callback de C.
- Original A protegida y activa no ejecuta selección redundante; crear derivada → cancelar conserva estado; confirmar llama al callback real con C y nombre.
- Marcar principal, seleccionar roadmap y eliminar B conservan callbacks mediante el overlay real.
- Nombre de cabecera extraordinariamente largo mantiene etiqueta accesible y acción, sin confundir elipsis informativa con pérdida de una decisión.

Con `KPKN_RENDER_DIR`, la misma clase escribe doce PNG `int_eda_a1_*`, `int_eda_a5_dias_*` y `int_eda_a5_variantes_*`, al ejecutar las cuatro combinaciones de tamaño/fuente. Sin esa variable se ejecutan las aserciones igualmente y solo se omite guardar PNG. Abrir los renders antes de afirmar validación visual.

Clases concretas para la corrida centralizada, además de `:app:compileBaseDebugKotlin`:

```text
com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameIntegrationGeometryTest
com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameA1ComposeTest
com.example.kpkn.screens.sessioneditor.frame.SessionEditorWeekSheetComposeTest
com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameComposeTest
com.example.kpkn.render.SessionEditorFrameRenderSheetTest
com.example.kpkn.render.SessionEditorEdaRenderSheetTest
```

Evidencia disponible ahora: revisión estática y `git diff --check` sin errores. Las pruebas/renders nuevos todavía no se han ejecutado; ninguna medida en este informe se atribuye a una corrida realizada.

## Riesgos limitados

La geometría y las capturas deben confirmarse en la corrida nativa centralizada, especialmente la píldora en dos líneas y las acciones del pie a 130 %. El parámetro opcional `moduleInset` conserva las llamadas existentes y deja `onDone` como último callback. Las hojas usan el portal existente; no se modifica su implementación. El `FrameHeader`, el orden de los contenidos del editor y los demás tableros ED-A no se reescriben.


## Verificación en integración · 23-oct

Merge ED-A tras G, con preflight limpio c922658c9cd398fddf8da689a2a528973a258c39. Primera corrida `int-eda-android-codex-20261023-1.log`: 14 clases, 96 pruebas, ocho fallos en la clase nueva; las otras 85 pasan y tres nuevas también. El render al130 muestra recorte real de Añadir. 8e4b54634 reduce márgenes internos, conserva altura64 y objetivos48, y reserva el ancho de la acción. Segunda: seis clases,33 casos,6 fallos; Añadir ya pasa en ambas alturas/fuentes.

La medida del texto centrado usa un párrafo de ancho110 dentro de un nodo95: línea7..103, fuera del ancho asignado. 2e14b0700 fuerza el ancho completo en AnimatedContent/Text, sin cambiar etiqueta o fuente. Los cuatro fallos de badges usan el árbol fusionado que oculta la decoración: se seleccionan en el árbol sin fusionar y se espera el cierre del diálogo; se conservan medidas22, colores exactos, selección, callbacks y píxeles. Se retiran impresiones diagnósticas.

Tercera `int-eda-android-codex-20261023-3.log`: BUILD SUCCESSFUL en8min10s, wrapper exit0; seis clases33/0, incluidos los11 nuevos. Con las63 pruebas de rutas no afectadas de la primera corrida, total96 únicos verdes. XML de las tres corridas archivados en C:/kw/logs/screens-v2-evidence/int-eda-android-codex-20261023-{1,2,3}/. Revisados Main740/130, Main880/100 y variantes740/130 en C:/kw/shots/int-eda: etiquetas completas y acciones dentro de pantalla. Se generan los12PNG nuevos previstos. QA de dispositivo sigue pendiente.
