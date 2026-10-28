# Inventario de paridad · Programas + Program Detail frente al lienzo v6

Fecha: 28-oct-2026. Rama `claude/program-detail-progress-redesign-b0a1e5`.
Regla: lo que la app pinta y el lienzo no contempla **se elimina**. Lo que el lienzo tiene y la app no tiene, o tiene distinto, **se rehace igual**. Lo que no se puede decidir leyendo el lienzo va a **Dudoso**, con una pregunta.

Rutas abreviadas:
- `pd/` = `android-native/app/src/main/java/com/example/kpkn/screens/programdetail/`
- `pr/` = `android-native/app/src/main/java/com/example/kpkn/screens/programs/`
- Lienzo: `docs/design/program-detail-v1/src/scenes*.js` (se cita el id de la escena).

Los números de línea son los del árbol actual (28-oct). Si un elemento ocupa un bloque, se da el inicio.

---

## 0. Ya decidido (solo se lista)

- **D-1.** Se elimina `VolumeAnalyticsCard` («Balance y cobertura», empuje/tirón) y su etiqueta «REVISIÓN DEL PLAN»: `pd/components/editor/MacrocycleEditorLegacy.kt:498-506`.
- **D-2.** `ProgramSeasonModule`, `ProgramPlannedLoadModule`, `ProgramAboutModule` y `ProgramProgressModule` (`pd/design/ProgramProgressModule.kt`, con sus «Decisiones» en las líneas 42 y 53) ya no se llaman. Se borran.

---

## 1. Cabecera / hero y barra superior

### Sobra
- **C-S1.** La barra gris de segmentos `ProgramWeekRuler` (`pd/design/ProgramHeader.kt:149-161`, `pd/design/ProgramWeekRuler.kt:25`) sale en la rutina o cuando la cinta viene vacía. `ProgramDetailScreen.kt:340` deja la cinta vacía si `repeatingWeek`. Decisiones del lienzo: «Barra gris de segmentos… → Cinta de fases». Se va a la cinta de fases. Lo que lleva una rutina está en C-D1.
- **C-S2.** En el módulo de competición, la píldora final «Ver resultado» / «Registrar resultado» / «Ficha de competición» (`pd/design/ProgramCompetitionModule.kt:58-62`). Su función pasa a la acción «Ficha ›» de la cabecera del módulo (p-comp).
- **C-S3.** En el módulo de competición, «Resultado aplicado a las referencias del programa.» (`ProgramCompetitionModule.kt:51`). p-comp no lo tiene.
- **C-S4.** En el módulo de competición, la línea del pronóstico `forecast.message` (`ProgramCompetitionModule.kt:41-42`). p-comp no tiene ninguna frase de pronóstico: solo la cifra y la línea de pesaje.
- **C-S5.** En el módulo de competición, el `resultSummary` en texto (`ProgramCompetitionModule.kt:47-48`). Fuera de «Revisar actualización de marcas», p-comp no contempla el estado terminado.
- **C-S6.** Ítem «Historial» del menú ⋯ (`pd/ProgramDetailScreen.kt:765`). Solo cambia a la pestaña Estructura. Lo sustituye «Versiones guardadas», que abre la hoja (C-F9).

### Falta o difiere
- **C-F1.** p-today. Lienzo: «Empezar · Tirón» (con el icono play). App: `"Empezar ${sessionName}"`, sin punto medio (`pd/design/ProgramHeaderModel.kt:68`).
- **C-F2.** p-paused. Lienzo: primario «Reanudar» con el icono play. App: «Activar programa» sin icono (`ProgramHeaderModel.kt:70`; `ProgramHeader.kt:187` no pone icono a `Activate`). El borrador (programa no activo) sigue diciendo «Activar programa».
- **C-F3.** p-rest. Lienzo: «Siguiente: viernes · Pierna B», con el icono de calendario. App: «Viernes · Pierna B», sin «Siguiente:» y en mayúscula (`ProgramHeaderModel.kt:181`).
- **C-F4.** m-program, cabecera del menú ⋯. Lienzo: hoja con la portada, el nombre y «Activo · semana 4 de 6». App: `ProgramMoreMenu` es un panel de texto sin cabecera (`ProgramDetailScreen.kt:746-768`).
- **C-F5.** m-program, filas con icono y subtítulo:
  - Pendiente: icono bell, «Propuesta de sentadilla, TM y check-in».
  - Sobre el programa: icono info, «Descripción, material, sesión más larga y cambios».
  - Pausar programa: icono pause, «Se guarda en la semana 4».
  - Compartir en historias: icono share, «Portada, nombre y reparto o bloques».
  - Decisiones del sistema: icono spark, «Qué cambió KPKN y por qué · 12».
  - Versiones guardadas: icono history, «Restaura el plan a un punto anterior».

  App: solo etiquetas, sin icono ni subtítulo (`ProgramDetailScreen.kt:749-765`).
- **C-F6.** m-program, grupos. Lienzo: mgroup «Programa» antes de Sobre y mgroup «Registro» antes de Decisiones. App: no tiene grupos.
- **C-F7.** m-program, etiqueta. Lienzo: «Editar nombre, nota y portada». App: «Editar programa» (`ProgramDetailScreen.kt:751`).
- **C-F8.** m-program, orden. Lienzo: Sobre, Editar, **Pausar**, **Compartir**. App: Compartir (752) antes de Pausar (754-762).
- **C-F9.** m-program / e-history. Lienzo: «Versiones guardadas», que abre la hoja de versiones. App: «Historial» solo cambia de pestaña (`ProgramDetailScreen.kt:765`).
- **C-F10.** p-comp, cabecera del módulo. Lienzo: eyebrow «Competición» con la acción «Ficha ›». App: `"Competición · ${title}"` en `bodyStrong` y sin acción (`ProgramCompetitionModule.kt:33`).
- **C-F11.** p-comp, cuenta atrás. Lienzo: la cifra grande «18 días» y debajo «Open regional · 16 nov». App: «Faltan N días» en `body` y sin nombre ni fecha de la competición (`ProgramCompetitionModule.kt:37`).
- **C-F12.** p-comp, pesaje. Lienzo: «Pesaje 82,5 kg · −0,2 kg/sem». App: «Peso declarado en el perfil; tendencia observada en 28 días: X kg/semana.» / «…sin tendencia corporal observada suficiente.» (`ProgramCompetitionModule.kt:43-44`).
- **C-F13.** p-comp, intentos. Lienzo: a la derecha, «Intentos» y «185 · 120 · 210». App: no los pinta.
- **C-F14.** p-comp, botón. Lienzo: «Planificar intentos» como píldora **secundaria**. App: `KpknCompactPill` relleno (`ProgramCompetitionModule.kt:53`).

### Dudoso
- **C-D1.** Portada de una rutina (s-simple / p-*). Sin bloques no hay cinta de fases, y la barra gris sale (C-S1). **¿Qué pinta la portada de una rutina bajo el título: nada, «Semana 7» solo, o la mini-cinta de 12 semanas de s-simple?**
- **C-D2.** m-program solo dibuja el programa activo («Pausar programa»). La app cambia la etiqueta a «Reanudar programa» o «Activar programa» según el estado (`ProgramDetailScreen.kt:756-760`). **Con el programa pausado o en borrador, ¿la fila dice «Reanudar programa» / «Activar programa», y con qué subtítulo?**
- **C-D3.** Estados de competición que p-comp no dibuja: «Hoy compites», «Competición pasada», «Sin fecha de competición válida» (`ProgramCompetitionModule.kt:35-39`) y «Consulta o registra esta competición en su ficha.» (57), para competiciones que no son SBD. **¿Qué muestra la cifra grande el día de la competición y después de ella, y qué lleva el módulo en una competición sin planificación SBD?**
- **C-D4.** La hoja «Modo competición» (`ProgramCompetitionModule.kt:146`, con el subtítulo «Consulta y simulación local…») no tiene escena. El botón se mantiene. **¿La hoja se deja como está?**
- **C-D5.** «Continuar sesión» con una sesión en curso: el lienzo no la dibuja en el hero. **¿Cómo dice el primario si hay una sesión empezada sin terminar?**

---

## 2. Semana

### Sobra
- **W-S1.** El nombre y la descripción de la semana en el navegador (`pd/design/ProgramWeekHeader.kt:118-139`, alimentado desde `ProgramDetailScreen.kt:1121-1122`). El navegador del lienzo no lleva ninguno de los dos. Dónde se ven, en W-D1.
- **W-S2.** «+ Sesión» en cada descanso suelto de un programa por bloques (`pd/design/ProgramDayModule.kt:570`). El lienzo solo lo pone en la rutina. La función sigue en «Añadir sesión», bajo la agenda, y en la pulsación larga del día (m-day).
- **W-S3.** Al tocar un tramo de descansos plegado, se despliega día a día (`ProgramRestRunRow`, `ProgramDayModule.kt:580`). El lienzo no lo contempla.
- **W-S4.** El rótulo «Suelta la sesión aquí» (`ProgramDayModule.kt:547`). En s-drag el destino es solo la línea `.drop`; «Suelta para moverla…» es una nota del lienzo, no texto de la app.
- **W-S5.** «Sin ejercicios todavía» dentro de la sesión desplegada (`pd/design/ProgramSessionBlock.kt:84`). s-open no tiene ese estado.
- **W-S6.** La marca de estado arriba a la derecha de la sesión desplegada. En s-open ese sitio es del ⋯ (W-F9).
- **W-S7.** El módulo «Fases» que envuelve la hoja «Semanas» (`pd/design/ProgramPhasesModule.kt:51`, `KpknModule` + `KpknModuleHeader`). s-weeks pone las filas directamente en la hoja, sin caja ni «Fases».
- **W-S8.** El aviso «Ciclo activo: … Ya puedes programar sus sesiones.» en snackbar y en notificación (`ProgramDetailScreen.kt:812-813`). s-simple dice que este aviso sale.
- **W-S9.** El carácter «⇄» como botón de cambiar ejercicio (`ProgramSessionBlock.kt:95`). Lo sustituye el icono swap (W-F10).

### Falta o difiere
- **W-F1.** s-week, navegador. Lienzo: va dentro de un módulo `.mod`. App: `ProgramWeekHeader` va suelto, sin superficie.
- **W-F2.** s-week, tiempo de la semana. Lienzo: «≈ 4 h 36». App: «≈ 4 h 36 min» (`dayTotalLabel`, `pd/design/ProgramWeekModel.kt:24`).
- **W-F3.** s-simple, navegación. Lienzo: la rutina navega con flechas («Semana 7») y abre la hoja. App: apaga las flechas y la hoja en la rutina (`ProgramDetailScreen.kt:1028-1030` y `1119`).
- **W-F4.** s-simple, mini-cinta. Lienzo: dentro del navegador, una mini-cinta de 12 semanas con la descarga en verde. App: no la tiene.
- **W-F5.** s-simple, «+ Sesión». Lienzo: en cada descanso de la rutina, también en la fila de descansos plegados. App: `ProgramRestRunRow` (`ProgramDayModule.kt:580`) no recibe `onAddSession`.
- **W-F6.** s-drag, tarjeta arrastrada. Lienzo: lleva el icono grip y va inclinada. App: sin grip ni rotación (`ProgramDayModule.kt`, la tarjeta de arrastre).
- **W-F7.** s-week, sesión opcional. Lienzo: borde **discontinuo** `#ffffff2a` (`.sess.optn`) y el chip «Opcional». App: borde sólido blanco al 16 % (`ProgramDayModule.kt:246` y `382`).
- **W-F8.** s-week, cápsula de propuesta. Lienzo: «+2,5 kg» con el icono trendUp. App: `proposalLabel` existe (`ProgramDayModule.kt:91` y `405`), pero `sessionUiOf` no lo rellena nunca (`pd/components/DayView.kt:249`). Además, `ProposalChip` (`ProgramDayModule.kt:424`) no lleva icono.
- **W-F9.** s-open, cabecera de la sesión desplegada. Lienzo: ⋯ arriba a la derecha, el nombre a 19 px y debajo «5 ejercicios · ≈ 48 min · 17 series». App: el ⋯ va en la fila de acciones (`ProgramSessionBlock.kt:149`) y falta «· N series».
- **W-F10.** s-open, ejercicios. Lienzo: filas con separadores (`.ex`) y el icono swap. App: «⇄» en texto (W-S9).
- **W-F11.** s-open, botones. Lienzo: «Empezar» con el icono play y «Editar» con el icono lápiz. App: píldoras compactas sin icono.
- **W-F12.** s-empty. Lienzo: «Copiar la semana N» siempre. App: solo sale si la semana anterior tiene sesiones (`ProgramDetailScreen.kt:1151`). Si el lienzo lo quiere siempre visible, falta el caso sin sesiones; si no, coincide. Se deja anotado aquí por el condicional.

### Dudoso
- **W-D1.** El nombre y la nota de la semana (W-S1). El ⋯ de semana del lienzo (m-week) tiene «Nombre y nota de la semana», pero ninguna escena enseña dónde se **leen**. **¿Se dejan de mostrar en la pestaña Semana (solo se editan) o van a otro sitio, por ejemplo como subtítulo de la hoja m-week?**
- **W-D2.** La casilla «Confirmar sesión opcional» / «Retirar confirmación de sesión opcional» (`ProgramSessionBlock.kt:127-129`) no sale en s-open. **¿Se elimina, o pasa al ⋯ de la sesión (m-session)?**
- **W-D3.** «Fuera de programa» con el día tachado (`ProgramDayModule.kt:562`): los días antes del inicio o después del fin con fechas. Ninguna escena lo dibuja. **¿Se eliminan esos días de la agenda o se dejan con este rótulo?**
- **W-D4.** Un programa simple de varias semanas, calendarizado pero que no es rutina, no tiene escena propia en Semana. **¿Se trata como un programa por bloques de un solo bloque (flechas, hoja «Semanas» con un grupo) o como la rutina?**

---

## 3. Progreso

### Sobra
- **G-S1.** El pie «kg × reps» bajo el tonelaje del resumen (`pd/design/progress/ProgramProgressPieces.kt:180`). En g-summary ahí va la comparación (G-F5).
- **G-S2.** El segmentado de tipo Programa / Bloque / Semana dentro de Volumen, y el selector `ProgramVolumeScopeSelector` (‹ › con «Promedio semanal de N semanas. fecha – fecha.», `pd/components/VolumeView.kt:368` y `382`). El lienzo usa los chips de alcance de arriba de la pestaña.
- **G-S3.** «N registros sin fecha no se cuentan» (`pd/design/ProgramVolumeModule.kt:87`).
- **G-S4.** La línea «Sin series esta semana: …» / «Sin series en total: …» (`ProgramVolumeModule.kt:116`). El lienzo muestra la fila del músculo con «No hay trabajo directo en el plan».
- **G-S5.** En cada fila de volumen, la etiqueta de estado («Dentro de la referencia del programa», etc., `pd/design/ProgramVolumeStates.kt:24`), la línea «Plan / Real N series/sem» y la unidad «series/sem» / «series» (`pd/design/ProgramVolumeRow.kt:68`).
- **G-S6.** Los controles «Alcance del volumen» Directo / +Secundario / +Estabilizador (`pd/design/ProgramVolumeCountControls.kt:35`).
- **G-S7.** El módulo «Resumen» de volumen (`pd/design/ProgramVolumeSummaryModule.kt`): series/sem, «Músculos activos» (48), «Más cargado» (53) y «Reparto de empuje, tirón y pierna» (`pd/design/ProgramVolumeSplitBar.kt:46`). Las molestias que lleva dentro pasan a su propio módulo (G-F14).
- **G-S8.** «Molestias recientes» / «Molestias por ejercicio» con «N veces», dentro del resumen (`pd/design/ProgramVolumeDiscomfortRows.kt:37`). Se sustituye por el módulo «Molestias» de g-adh.
- **G-S9.** En la hoja de rangos, las líneas de respuestas «rasgo: etiqueta» (`pd/design/progress/ProgramRangesSheet.kt`, bajo «Perfil del asistente»).
- **G-S10.** En la hoja de rangos, el texto vacío «Al cerrar un ciclo con check-ins semanales, KPKN propondrá ajustes con tus datos.» (`ProgramRangesSheet.kt:172`).
- **G-S11.** El desglose «N series/sem» de `ProgramVolumeBreakdownList.kt:129`, si se sigue pintando bajo la fila. El lienzo no tiene desglose por ejercicio en Volumen.

### Falta o difiere
- **G-F1.** g-summary, titular. Lienzo: «Subes en X de N **básicos**». App: «Subes en X de N ejercicios.» (`pd/design/progress/ProgramProgressModel.kt:218`).
- **G-F2.** g-empty, titular vacío. Lienzo: «Aún no hay tendencia. Con 2 sesiones de cada ejercicio empezamos a comparar.». App: «Aún no hay datos para ver tendencias.» (`ProgramProgressModel.kt:216`).
- **G-F3.** g-summary / g-empty, eyebrow del alcance. Lienzo: «Semana 1». App: «En 1 semana» / «En N semanas» (`ProgramProgressModel.kt:204`).
- **G-F4.** g-empty, KPI vacíos. Lienzo: «2/5 sesiones», «de 64», «esta semana». App: no tiene esos pies en vacío.
- **G-F5.** g-summary, pie del tonelaje. Lienzo: «+9 % vs bloque 1». App: siempre «kg × reps» (G-S1).
- **G-F6.** g-strength, estado de cada ejercicio. Lienzo: el icono y el texto del estado en la misma línea, seguidos de «6 sesiones». App: el estado va en una cápsula en su propia línea y el pie debajo (`ProgramProgressPieces.kt:263-271`).
- **G-F7.** g-strength, sparkline. Lienzo: con relleno de área y punto final. App: solo la línea.
- **G-F8.** g-strength, «Todos». Lienzo: «Todos ›», que abre la lista completa con buscador. App: «Todos», sin chevron, solo si `allCount` es mayor que las filas, y despliega en el sitio (`ProgramProgressPieces.kt:272`).
- **G-F9.** g-lift. Lienzo: tocar una fila abre la hoja de detalle del ejercicio. App: ninguna fila es clicable y la hoja no existe.
- **G-F10.** g-strength, bajada. Lienzo: «Baja 2 semanas». App: «N sesiones».
- **G-F11.** g-empty, ejercicio con una sesión. Lienzo: «1 sesión». App: «1 sesión: aún sin tendencia» (`ProgramProgressModel.kt:245`).
- **G-F12.** g-summary, series duras. Lienzo: «212 / 264» en `cap` y la nota con «(faltó la sesión del sábado)». App: la cifra en `metricM` y la nota sin el motivo.
- **G-F13.** g-summary, módulo «Trabajo acumulado». Lienzo: Tonelaje (kg × reps), Repeticiones efectivas, Tiempo con N sesiones y la mini-gráfica «Tonelaje por semana». App: no existe.
- **G-F14.** g-adh, módulos. App: no existe ninguno de estos:
  - «Cumplimiento»: rejilla por semana y «Más omitidos: …».
  - «Esfuerzo registrado»: RIR medio frente al plan, con su frase.
  - «Molestias»: cabecera «4 semanas» y «Coincide con …».
  - «Decisiones del sistema»: línea de tiempo de 3 entradas y «Todas ›».
- **G-F15.** g-volume, cabecera. Lienzo: «Series por músculo». App: «Volumen de la semana» / «Volumen total» (`ProgramVolumeModule.kt:55`).
- **G-F16.** g-volume, fila. Lienzo: «14 / 16» sin unidad y, si falta, la caption roja «Por debajo del mínimo útil (8)». App: G-S5.
- **G-F17.** g-volume, leyenda. Lienzo: «Rango útil · Plan · Indirectas: ½». App: no la tiene.
- **G-F18.** g-volume, «Ver lo que has hecho en 4 semanas». Lienzo: un `.mod.tight` a todo el ancho con chevron. App: acción de texto fuera de módulo (`VolumeView.kt:293`).
- **G-F19.** g-ranges, «Revisar mis respuestas». Lienzo: reutiliza el paso del asistente, con el detalle «Abre las mismas preguntas del asistente; cambia el perfil de todos tus programas». App: abre `VolumeCalibrationSheet` («Calibrar»), con otro detalle (`ProgramRangesSheet.kt:153`; `VolumeView.kt:167`).
- **G-F20.** g-ranges, icono de «Perfil del asistente». Lienzo: spark. App: Info (`ProgramRangesSheet.kt:57`).
- **G-F21.** g-ranges, detalle de las propuestas. Lienzo: termina en «tras 5 semanas respondidas». App: no lo dice.
- **G-F22.** g-ranges. Lienzo: la acción «Usar la referencia general». App: no la tiene.

### Dudoso
- **G-D1.** Los estados vacíos de Volumen: «Activa tu programa para ver el volumen» / «Activar» y «Aún no hay sesiones en esta semana» / «Añadir sesión» (`pd/design/ProgramVolumeEmpty.kt:17` y `36`). g-empty no los dibuja. **¿Se eliminan (y la pestaña muestra filas vacías) o se mantienen?**
- **G-D2.** El orden vertical de los módulos de Progreso está repartido en varias escenas: g-summary, g-strength, g-volume, g-adh. **¿Cuál es el orden de arriba abajo: Resumen, Trabajo acumulado, Fuerza, Series por músculo, Cumplimiento, Esfuerzo, Molestias, Decisiones?**
- **G-D3.** `VolumeCalibrationSheet` sigue en `ProgramDetailScreen` (`showVolumeSheet`, ya inalcanzable) y en `VolumeView`. **Si «Revisar mis respuestas» pasa al paso del asistente (G-F19), ¿se borra la hoja «Calibrar» entera?**

---

## 4. Estructura

### Sobra
- **E-S1.** El módulo `ProgramCalendarModule` entero: «Calendario» con «Editar», Inicio / Fin / Competición / Duración, «Días de entreno», «Calendarizar» y los mensajes del motor (`pd/design/structure/ProgramCalendarModule.kt:58`). Su función pasa a la acción «Fechas» del mapa, que abre la hoja Fechas (e-dates).
- **E-S2.** Las filas «Ruta y semanas por bloque» y «Fechas clave» de «Del programa» (`pd/components/editor/MacrocycleEditorLegacy.kt:456-483`, títulos en 459 y 469). Su función pasa al mapa y a la hoja Fechas.
- **E-S3.** La hoja «Ruta del programa» entera (`pd/components/editor/RouteSection.kt`, `pd/design/structure/ProgramRouteSheet.kt:64`): «Línea de tiempo» (82), «Bloques» con +/− y «Competición». Ninguna escena la tiene.
- **E-S4.** `KeyDatesSection` como entrada separada (`pd/components/editor/KeyDatesSection.kt`). La recoge la hoja Fechas.
- **E-S5.** En la tarjeta de bloque, el eyebrow de fase coloreado y la cuenta «· 12 sesiones». Lienzo: franja de color + nombre + «Intensificación · 2 semanas · Top set + back-off».
- **E-S6.** Las filas de mesociclo dentro de la tarjeta de bloque (`pd/design/structure/ProgramBlockModule.kt:48-53`). El lienzo las mete en el ⋯ del bloque.
- **E-S7.** El menú propio de la pulsación larga en una semana de Estructura: «Editar semana», «Copiar semana», «Pegar aquí», «Eliminar semana» (`MacrocycleEditorLegacy.kt:619-648`). El lienzo pide el mismo menú que el ⋯ de semana de la pestaña Semana (m-week).
- **E-S8.** En la hoja de bloque, «Al superar el bloque» como etiqueta de sección (`pd/design/structure/ProgramBlockSheet.kt:108`). En e-block es una caption sin etiqueta.
- **E-S9.** En la hoja Fechas de la app (`pd/design/structure/ProgramCalendarSheet.kt`), los textos que e-dates no tiene: «La competición marca su semana entera en el programa.», «Vista previa», «Otras fechas clave», «Exámenes, vacaciones o viajes que cambian tu semana.» y la píldora «Guardar calendario» (218).
- **E-S10.** En la hoja de versiones, el estado vacío «Aún no hay copias» (`pd/design/structure/ProgramHistorySheet.kt:98`) y el módulo que envuelve las filas.
- **E-S11.** En la hoja de plantillas, «Filtrar» y la hoja de confirmación aparte (Reemplazar / Crear copia). La sustituye el interruptor de e-library (E-F18).
- **E-S12.** El estado «deshabilitado» de la fila Versiones cuando no hay copias (`pd/design/structure/ProgramFromProgramModule.kt`, `alpha 0.45`, 32-35). En e-history la fila siempre abre la hoja.

### Falta o difiere
- **E-F1.** e-map / e-blocks, etiquetas de grupo. Lienzo: «Bloques», «Del programa» y «Revisión del plan» como `group-l`. App: «Del programa» es la cabecera de un módulo y no hay «Bloques». «Revisión del plan» se trata en E-D1.
- **E-F2.** e-blocks, tarjeta de bloque. Lienzo: franja de color de 3 px a la izquierda (`.blk:before`), el nombre y «Intensificación · 2 semanas · Top set + back-off». App: no hay franja y falta el esquema de progresión en la línea.
- **E-F3.** e-blocks, añadir semana. Lienzo: un círculo «+» al final de las semanas de **cada** bloque. App: «Añadir semana» es una acción de texto y solo sale en el último bloque (`ProgramBlockModule.kt:116`).
- **E-F4.** e-blocks, sección desplegada. Lienzo: «Progresión de este bloque». App: no la tiene.
- **E-F5.** e-blocks, «Añadir bloque». Lienzo: con el icono plus. App: sin icono (`ProgramBlockModule.kt:183`).
- **E-F6.** e-blocks, ⋯ del bloque. Lienzo: «+ / − semanas». App: «Añadir semana», «Añadir mesociclo», «Editar bloque», «Eliminar bloque» (`MacrocycleEditorLegacy.kt:581-590`). Falta quitar una semana desde aquí.
- **E-F7.** e-map, Del programa, Versiones. Lienzo: «Última: hoy 08:12, antes de aplicar la propuesta». App: «N versiones · restaurar una copia» (`MacrocycleEditorLegacy.kt:444-445`).
- **E-F8.** e-map, Del programa, TM. App: si no hay resumen, dice «Máximos de entrenamiento de los básicos» (`MacrocycleEditorLegacy.kt:423`); con datos, «N ejercicios con TM · faltan M» (`pd/ExerciseTrainingMaxPanel.kt:42-48`). Hay que igualarlo al texto de la escena.
- **E-F9.** e-map, Del programa, icono de plantillas. Lienzo: book. App: Layers.
- **E-F10.** e-cycles (rutina), módulo «Semana base». Lienzo: la acción «Editar», el titular «4 sesiones · ≈ 3 h 10» y las letras L–D. App: no existe. Muestra el Calendario con «Semana que se repite» / «Semana N» (`MacrocycleEditorLegacy.kt:377`).
- **E-F11.** e-cycles, grupo «Ciclos» en la página. Lienzo: filas «Descarga cada 4 semanas» / «Próxima: semana 8», «Test de 1RM cada 8 semanas» / «Próximo: semana 8» y «Añadir ciclo». App: una sola fila «Ciclos» en Del programa que abre la hoja `CyclesSection`.
- **E-F12.** e-cycles, pie. Lienzo: «Pasar a programa por bloques». App: «Convertir en programa por bloques» (`ProgramBlockModule.kt:178`).
- **E-F13.** e-block, cabecera de la hoja. Lienzo: título = el nombre del bloque y sub «Semanas 4 y 5 · 29 oct – 9 nov». App: «Nuevo bloque» / «Editar bloque», sin subtítulo (`ProgramBlockSheet.kt:59`).
- **E-F14.** e-block, campos. Lienzo: el campo se llama «Nota», hay 6 chips de objetivo (incluida «Competición») y la progresión es un desplegable. App: «Descripción», las 9 chips de `BlockGoal` y la progresión en filas radio.
- **E-F15.** e-block, borrar. Lienzo: «Eliminar bloque» dentro de la hoja. App: solo está en el ⋯ del bloque.
- **E-F16.** e-dates, hoja Fechas:
  - Título «Fechas».
  - Sub «Con fechas, cada día de la Semana lleva su número y Inicio sabe qué toca hoy.».
  - Empieza; Termina «· calculado».
  - Grupo Fechas clave (Competición, «Añadir fecha clave» con «Test, viaje, vacaciones»).
  - Días de entreno.
  - Pausas: «Añadir una pausa» / «Las semanas siguientes se corren».

  App: «Calendario del programa», «Inicio del programa», «Día de la competición», «Fin del programa», «Añadir fecha clave» (`ProgramCalendarSheet.kt`). No tiene «Días de entreno» ni «Pausas» en la hoja.
- **E-F17.** e-history, cabecera. Lienzo: título «Versiones guardadas» y sub «KPKN guarda una copia antes de cada cambio grande. Restaurar no borra tus registros.», con la píldora «Guardar una copia como programa nuevo». App: «Historial del programa» (`ProgramHistorySheet.kt:89`), «N copias guardadas. Restaurar sustituye el programa actual.» y no tiene la píldora.
- **E-F18.** e-library. Lienzo: título «Aplicar plantilla o método», sub «Rellena las semanas vacías. Si ya tienen sesiones, eliges si sustituirlas.», grupos «Plantillas» / «Métodos de progresión» y el interruptor «Sustituir semanas con sesiones». App: «Plantillas», «Aplican una estructura completa al programa.» (`pd/design/structure/ProgramTemplatesSheet.kt:72`) y los grupos «Plantillas» / «Protocolos».

### Dudoso
- **E-D1.** «Revisión del plan». El lienzo tiene avisos con semáforo, «Ver revisión completa» y la hoja e-review, con «Proporciones semanales» Empuje/Tirón y «Cobertura». Choca con lo ya decidido (D-1: se elimina la tabla empuje/tirón). **¿Se construye la versión del lienzo (avisos + hoja e-review) o se quita toda la «Revisión del plan»?**
- **E-D2.** La rutina en Estructura (e-cycles) solo muestra Semana base, Ciclos y Pasar a bloques. La app pinta además el Mapa, el Calendario, la lista de bloques y «Del programa» (TM, plantillas, versiones). **¿La rutina lleva Mapa y «Del programa», o solo Semana base / Ciclos / Pasar a bloques? Y si no lleva «Del programa», ¿dónde quedan TM, plantillas y versiones?**
- **E-D3.** «Añadir mesociclo» y «Ciclo N» (`cycleLabel`). El ⋯ del bloque del lienzo no nombra los mesociclos. En un programa simple, la app pone «Añadir mesociclo» en el pie. **¿Los mesociclos siguen existiendo en la interfaz? Si siguen, ¿solo en el ⋯ del bloque?**
- **E-D4.** Las hojas de mesociclo, de ciclos (`CyclesSection`) y del calendario de un programa simple no tienen escena. **¿Se dejan como están, o se rehacen con el patrón de e-block / e-dates?**
- **E-D5.** El aviso «Revisa la estructura temporal» (`pd/design/structure/ProgramStructureNotice.kt:24`). El lienzo no lo dibuja explícitamente, pero lo deja en la lista de lo que se conserva. **¿Confirmas que se queda, y en qué posición: encima del mapa o debajo?**

---

## 5. Hojas y menús

### Sobra
- **H-S1.** En la hoja Pendiente, los eyebrows «ESTA SEMANA» y «SEMANA PASADA» (`pd/design/ProgramPendingModel.kt:10-16`). El lienzo solo agrupa «Más adelante».
- **H-S2.** La hoja intermedia de detalle de Pendiente (`ProgramDetailScreen.kt:672-733`), que mete los módulos viejos:
  - `ProgramScheduleModule` (678): «N sesiones requeridas sin registro · semana», «Revisar salto de sesiones», «Las haré esta semana», «Revisar vuelta suave».
  - `ProgramTransitionModule` (705 y 725): «Revisar inicio suave» / «Revisar bloque», «Conservar plan», «Ver decisiones», «Programa terminado. Elige cómo seguir.» + «Repetir programa» / «Elegir catálogo» / «Semana de mantenimiento».
  - `AutoregulationProposalsCard` (712).

  El lienzo abre directamente la hoja final (m-block, m-onerm, m-continue).
- **H-S3.** El diálogo de test de 1RM con «Registrar resultado», «Omitir», «Más tarde: el programa espera tu decisión» y «Entendido» (`ProgramDetailScreen.kt:1695-1716`). Lo sustituye m-onerm.
- **H-S4.** En la hoja Continuación, la segunda hoja «Repetir el programa» / «Preparar mantenimiento» con «Crear y activar» / «Volver». m-continue es la hoja de elección.
- **H-S5.** En la hoja de Revisión de bloque:
  - «N semanas con respuestas originales atribuibles al bloque.»
  - «Músculo: X series previstas y Y registradas por semana.»
  - «Volver».

  m-block los cambia por grupos y por «Conservar el plan» (H-F7).
- **H-S6.** En el check-in, el `Switch` «No volver a preguntar» (`WeeklyCheckinSheet.kt`). Pasa a una acción con confirmación (H-F6).
- **H-S7.** En «Sobre el programa» (`pd/design/ProgramPlanSheet.kt`), las secciones «Qué haces», «Notas del plan» y «Volumen real».
- **H-S8.** En «Sobre el programa», la procedencia como subtítulo arriba (`ProgramPlanSheet.kt:43`). El lienzo la pone al final como «Origen», en letra pequeña.
- **H-S9.** En «Editar programa» (`pd/design/ProgramEditSheet.kt`), el texto «Orienta el volumen y las cargas…», «Tu foto manda…» y «Usar portada KPKN».
- **H-S10.** En «Cambiar ejercicio» (`ProgramSwapSheet.kt`), la píldora «Volver».
- **H-S11.** En «Planificar intentos» (`CompetitionPlanSheet`), el texto de validación, «Volver» y «Sin referencia comparable…».
- **H-S12.** En «Pegar semana» (`pd/design/ProgramCopyWeekSheet.kt`):
  - La sección «Semana origen» con filas radio.
  - Las casillas de destino con los atajos «Bloque actual» / «Una semana».
  - «Conflictos por semana» y «Cancelar».
- **H-S13.** En el diálogo de inicio de semana (`ProgramDayDialogs.kt:12-66`), la caption dinámica bajo las opciones y «Cancelar».
- **H-S14.** La hoja «Calibrar» (`VolumeCalibrationSheet`), si se cumple G-F19 (ver G-D3).

### Falta o difiere
- **H-F1.** m-pending, títulos y subtítulos de cada aviso. App:
  - «Sesiones pendientes» / «Elige cómo retomar el plan».
  - «Propuesta de ajuste» / «Test de 1RM pendiente» / «Cambio de bloque».
  - «Propuestas de progresión» / «Revisa y aplica o mantén la carga».
  - «Revisión de bloque».
  - «Check-in de la semana» / «¿Cómo fue la semana pasada?».
  - «Programa terminado» / «Elige cómo seguir».

  Lienzo: «Check-in de la semana 3» / «Responder u omitir», y el resto con los textos de m-pending.
- **H-F2.** m-pending, TM que falta. Lienzo: «Falta el TM de press militar» / «Sin él, sus cargas no se calculan». App: el tipo `MISSING_TM` está definido (`ProgramPendingModel.kt:12`; icono en `ProgramPendingSheet.kt:248`), pero nunca se añade.
- **H-F3.** m-pending, progresión destacada. Lienzo: las acciones «Aplicar» / «Mantener carga». App: no las lleva.
- **H-F4.** m-pending, aviso futuro. Lienzo: «Fin del programa en 2 semanas» / «Te preguntaremos cómo seguir». App: no lo tiene.
- **H-F5.** m-onerm. Lienzo: el eyebrow «Test de fin de bloque» y «Omitir el test» dentro de la hoja. App: no tiene ninguno de los dos.
- **H-F6.** m-checkin:
  - Eyebrow «Semana 3».
  - Subtítulo «Después revisas una propuesta para los accesorios.».
  - Etiquetas «Pecho · rendimiento» / «Pecho · agujetas». La app pone el grupo y luego «Rendimiento» / «Agujetas».
  - «No igualé la anterior». La app dice «No igualé la semana anterior».
  - El plegado «Espalda · Hombros · Pierna: 3 grupos más».
  - «Omitir» como acción de texto. La app usa una píldora secundaria.
  - «No volver a preguntar» como acción con confirmación.
  - Chips KPKN. La app usa `FilterChip` de Material.
- **H-F7.** m-block. Lienzo: grupos «Series por semana» y «Cambios propuestos · 4 sesiones», con filas, y «Conservar el plan». App: eyebrow «Bloque terminado», «Revisión de bloque», textos sueltos, «Confirmar» / «Volver».
- **H-F8.** m-continue. Lienzo: la hoja «¿Cómo sigues?» con un resumen y tres filas con subtítulo. App: va por la hoja intermedia (H-S2) y luego a la hoja de confirmación (H-S4).
- **H-F9.** m-decisions. Lienzo: chips de filtro «Todas», «Progresión», «Check-in», «Bloque», «Manual» y la línea de tiempo. App (`ProgramDecisionsSheet.kt`): no tiene chips; pone «dd MMM · fuente» encima del título y el detalle debajo.
- **H-F10.** m-about. Lienzo:
  - «Material».
  - «Calendario» con el rango de fechas.
  - «≈ 61 min».
  - «1 por resolver».
  - «Origen» al final.

  App:
  - «Material requerido».
  - «Calendario» con los días por semana (`ProgramPlanSheet.kt:65`).
  - «~61 min (estimación)».
  - «N pendientes de resolver».
  - «Ver cómo funciona» como píldora compacta, solo si el programa viene del catálogo.
- **H-F11.** m-edit. Lienzo: la paleta en 5 círculos y «Usar una foto» con el icono de ojo. App: un segmentado con la etiqueta «Paleta» y no tiene «Usar una foto».
- **H-F12.** m-swap. Lienzo:
  - Subtítulo «Conserva repeticiones, RIR y descansos. La carga nueva la eliges al entrenar.».
  - Candidatos en filas radio.
  - Etiqueta «Dónde se cambia».
  - Segmentado con «Todo».

  App (`ProgramSwapSheet.kt`): otro subtítulo, `FilterChip`, no tiene la etiqueta y pone «Todo el programa».
- **H-F13.** m-attempts. Lienzo:
  - Título «Planificar intentos».
  - Eyebrow «Open regional · 16 nov».
  - Segmentado.
  - Tres intentos por fila.

  App: «Planificar competición», un subtítulo largo, `FilterChip`, los campos en vertical y «Peso objetivo para el pesaje, kg (opcional)».
- **H-F14.** m-week. Lienzo: hoja con el título «Semana 4», sub «Intensificación · 29 oct – 2 nov», filas con subtítulo y grupos «Copiar» / «Semanas». App: menú anclado solo con texto (`ProgramItemMenu`).
- **H-F15.** m-day. Lienzo: menú de vidrio con iconos y un separador. App: menú anclado solo con texto.
- **H-F16.** m-session. Lienzo: hoja con título y sub «Miércoles 29 · 5 ejercicios · ≈ 48 min», con filas con subtítulo. App: menú anclado solo con texto.
- **H-F17.** m-startday. Lienzo:
  - Hoja «La semana empezará el miércoles».
  - Sub «Cambia el orden en que ves los días.».
  - «Qué semanas»: Todas / «Desde esta semana».
  - «Las sesiones»: «Se quedan en su día» / «Conservan su orden del reparto», cada una con subtítulo.
  - «Confirmar».

  App: `AlertDialog` «Cambiar inicio de semana»:
  - «X será el nuevo primer día visual del programa.»
  - «Alcance de sesiones»: Todas / Desde esta.
  - «Qué hacer con las sesiones»: «Mantener días actuales» / «Mantener orden del split».
  - Confirmar / Cancelar.
- **H-F18.** m-outofrange. Lienzo: una hoja con el texto. App: un `AlertDialog` con el mismo texto.
- **H-F19.** m-paste. Lienzo:
  - «Pegar la semana 3».
  - Sub «5 sesiones · Acumulación».
  - «Dónde» con chips.
  - «Ya tienen sesiones» con fila y el chip «Reemplazar».
  - «Pegar».

  App: «Copiar sesiones de semana», «Destino», «Ya tiene sesiones en: …», «Reemplazar en esta semana», «Copiar».
- **H-F20.** m-week, edición de la semana. Lienzo: la fila «Nombre y nota de la semana». App: la hoja «Editar semana» con el campo «Descripción» (`pd/design/ProgramWeekEditSheet.kt`).

### Dudoso
- **H-D1.** El diálogo de confirmación de «Restaurar esta sesión desde el plan» no tiene escena. **¿Restaurar pide confirmación? Si la pide, ¿hoja o diálogo, y con qué texto?**
- **H-D2.** El reparto (`SplitView`) no se ha comparado con m-split en este inventario. **¿Entra en esta ronda de paridad? Si entra, hay que auditarlo aparte.**
- **H-D3.** La hoja m-pending dibuja una semana con avisos de varios tipos. **Con más de un aviso del mismo tipo (por ejemplo dos propuestas de progresión), ¿se agrupan en una fila o salen por separado?**

---

## 6. Programas

### Sobra
- **P-S1.** Las flechas Subir / Bajar de cada programa en cola (`pr/ProgramsScreen.kt:650-661`, `QueueMoveButton` 671-702). En pr-list la cola se reordena **arrastrando** por el asa. Hoy el asa es decorativa (619-625). La función pasa al arrastre (P-F3).
- **P-S2.** `CreateTailoredProgramCard` («Crear mi programa a medida» / «KPKN arma un programa con tu material, tus días y tu objetivo», `pr/ProgramsScreen.kt:893-902`). Nadie lo llama. Es código muerto y pr-list dice que sale; su función ya es «A tu medida» en Nuevo.
- **P-S3.** En el paso de TM, el botón «Calcular desde mi historial» (`pr/TrainingMaxWizard.kt:136`). pr-tm viene precargado y lo dice con una caption (P-F12).
- **P-S4.** En el paso de TM, la vista previa aparte con una línea por levantamiento, «Sentadilla · TM …» etc. (`TrainingMaxWizard.kt:155-166` y `216-219`). pr-tm pone el TM en la misma fila que el 1RM («sin vista previa aparte»).
- **P-S5.** En el catálogo, el filtro de perfil «Otros» (`pr/CreateProgramTemplateSheet.kt:50`). pr-catalog tiene Todos / Fuerza / Músculo / Fuerza y músculo / Atleta.
- **P-S6.** Importar como pantalla completa con la fila Atrás + «Importar programa» (`pr/import/ImportProgramScreen.kt:106-128`) y la rejilla ilustrada `StartingPointGrid` (`pr/import/ImportSourceChoices.kt:186-192`). pr-import es una hoja con filas `.mi`. La lectura y la revisión posteriores no cambian.
- **P-S7.** En Importar, las acciones desplegables «Hacer una foto» / «Elegir de la galería» bajo «Foto de tu rutina» (`ImportSourceChoices.kt:91-100`). En pr-import, «Foto de tu rutina» es una fila con el sub «Hacer una foto o elegirla de la galería». La elección se haría al tocar la fila.

### Falta o difiere
- **P-F1.** pr-list, cabecera. Lienzo: la píldora primaria pequeña con el icono plus y «Nuevo». App: `KpknCompactPill` con el texto «+ Nuevo» (`pr/ProgramsScreen.kt:434`).
- **P-F2.** pr-list, En curso. Lienzo: «Hoy: Tirón» con el icono play. App: sin icono (`ProgramsScreen.kt:551-556`).
- **P-F3.** pr-list, En cola. Lienzo: se reordena arrastrando por el asa (grip). App: el asa no hace nada; se reordena con las flechas (P-S1).
- **P-F4.** pr-list, Tus programas, línea de meta. Lienzo: «16 semanas · 4 días» y, en la rutina, «Rutina · 4 días». App: «N semanas · M sesiones» (`ProgramsScreen.kt:736-737`). La cola del lienzo sí usa «8 semanas · 5 sesiones», así que ahí coincide.
- **P-F5.** pr-menu, icono de «Archivar». Lienzo: upload. App: Layers (`pr/ProgramListSheets.kt:90`).
- **P-F6.** pr-new, icono de «A tu medida». Lienzo: spark. App: Bulb (`pr/NewProgramSheets.kt:106`).
- **P-F7.** pr-new, icono de «Importar». Lienzo: upload. App: Layers (`NewProgramSheets.kt:129`).
- **P-F8.** pr-catalog, botón de filtros arriba a la derecha. Lienzo: icono filter. App: icono More (`pr/ProgramCatalogPage.kt:147`).
- **P-F9.** pr-catalog, chip de perfil. Lienzo: «Atleta». App: «Atleta completo» (`CreateProgramTemplateSheet.kt:50`).
- **P-F10.** pr-catalog, chips de días y duración. Lienzo: llevan icono (calendario y reloj) y muestran el valor («4 días», «8–12 semanas»). App: sin icono y, sin elegir, «Días: Todos» / «Duración: Todas» (`ProgramCatalogPage.kt:181` y `189`).
- **P-F11.** pr-tm, subtítulo. Lienzo: «Escribe tu 1RM. El TM es el 90 % y con él se calculan las cargas.». App: «Introduce 1RM de competición. El TM se calcula al 90 % de tu 1RM.» (`TrainingMaxWizard.kt:196`).
- **P-F12.** pr-tm, filas. Lienzo: una fila por levantamiento con el nombre, el campo de kg y «TM 144» a la derecha; «Press militar · opcional»; y la caption «Precargado con tu historial.». App: `LiftMarksPicker` (con su variante «opcional»), la vista previa aparte y no tiene la caption.
- **P-F13.** pr-import, cabecera. Lienzo: hoja «Importar un programa» con el sub «KPKN lo lee y te deja revisarlo antes de guardarlo.». App: pantalla «Importar programa», sin subtítulo.
- **P-F14.** pr-import, filas. Lienzo:
  - Icono upload, «Elegir archivo» / «Excel, CSV, Word, texto o PDF».
  - Icono paste, «Pegar texto» / «Ej.: Día 1 — Pierna / Sentadilla 4x6 @120kg».
  - Icono eye, «Foto de tu rutina» / «Hacer una foto o elegirla de la galería».

  App: ilustraciones sin subtítulo visible; el texto solo va en `contentDescription`.
- **P-F15.** pr-empty, botón. Lienzo: «Crear a mi medida» con el icono spark. App: sin icono (`ProgramsScreen.kt:457`).

### Dudoso
- **P-D1.** La tarjeta En curso con el programa **pausado**: chip «Pausado» con el punto en energía (`ProgramsScreen.kt:516-519`), y «Hoy: …» que reanuda (551-554). pr-list solo dibuja «Activo». **¿Se mantiene «Pausado» en el chip, y el primario sigue diciendo «Hoy: Tirón» aunque reanude?**
- **P-D2.** La tarjeta En curso sin sesión hoy: solo «Ver programa» a todo el ancho (`ProgramsScreen.kt:561-567`). **¿Qué lleva el primario un día de descanso: «Siguiente: viernes · Pierna B», como el hero de p-rest, o solo «Ver programa»?**
- **P-D3.** La hoja Archivados con la lista vacía: «No tienes programas archivados.» (`pr/ProgramListSheets.kt:148`). Solo se ve al restaurar o borrar el último con la hoja abierta. **¿La hoja se cierra sola al quedar vacía, o se deja ese texto?**
- **P-D4.** La nota de Texas Method / Madcow en el paso de TM, «Texas Method y Madcow usan el 87 % de tu 1RM como TM (parecido a un 5RM).» (`TrainingMaxWizard.kt:204`). pr-tm no la tiene. **¿Se elimina, o el subtítulo cambia de porcentaje y de frase en esos planes?**

---

## Recuento

| Zona | Sobra | Falta o difiere | Dudoso |
|---|---:|---:|---:|
| 1. Cabecera / barra | 6 | 14 | 5 |
| 2. Semana | 9 | 12 | 4 |
| 3. Progreso | 11 | 22 | 3 |
| 4. Estructura | 12 | 18 | 5 |
| 5. Hojas y menús | 14 | 20 | 3 |
| 6. Programas | 7 | 15 | 4 |
| **Total** | **59** | **101** | **24** |

Hay 2 ítems más de «Ya decidido»: D-1 y D-2.
