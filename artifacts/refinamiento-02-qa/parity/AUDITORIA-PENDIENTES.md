# Auditoría de pendientes · Refinamiento 02 + iteración 03 (28-oct-2026)

Pedida por el usuario tras detectar que el descanso no se había implementado como el artefacto. Regla: **si la app se ve distinta al artefacto, se cambia por el artefacto; los diseños antiguos que difieran se eliminan.**

Método: cinco auditores independientes (solo lectura) compararon escena por escena el marcado y el CSS de los prototipos (`Desktop/KPKN-sesion-iteracion-02/` y `docs/design/live-session-iteration-03/`) con el código nativo, con archivo:línea. Veredictos: IGUAL / DISTINTO / FALTA. Ninguna fila es PASS hasta tener captura nativa.

## Resumen por área

**Estado 28-oct (noche, tras compilar):** el flavor base compila (`:app:compileBaseDebugKotlin` BUILD SUCCESSFUL, tras corregir 14 errores de imports y ámbito). Tests unitarios de `screens.workout.*`, `render.*`, `screens.sessioneditor.*`, `screens.technique.*`, `data.workout.*` y `services.workout.*`: 1.697 tests, 774 omitidos (hojas de render, que piden `KPKN_RENDER_DIR`). De los fallos, 6 eran de este trabajo (análisis: «Analizando…», «8 + 1 · completas + parciales», «Sentadilla trasera · serie 3», «estimado · vista lateral», «≈ 19 %»): actualizados y en verde. Los demás no son de este trabajo: hojas de render sin `KPKN_RENDER_DIR` (NPE al guardar el PNG) y tres del editor de sesión (`AssistantSheetComposeTest`, `CardioEditorEddComposeTest` con `analysisGate` nulo, `SessionEditorCatalogLaunchAuditTest` por tiempo), que no usan nada de la sesión en vivo. Tests de dispositivo (`androidTest`, la cola no los compila) adaptados a mano al diseño nuevo: `RecoveryRevisionDeviceTest` (juegos), `Revision14/15VisualDeviceTest`, `SessionRevisionFlowDeviceTest` (juego), `VoiceFirstUseDeviceTest`, `RealMusicVoiceDeviceTest`, `LiveHeaderSheetsDeviceTest` (voz y «Contexto del ejercicio»), `LiveHeaderSheetsRenderSheetTest`. Ninguna fila es PASS hasta la captura nativa en el Flip5.

| # | Área | Estado real | Falta |
|---|---|---|---|
| 1 | Descanso, sugerido, tiempo extra y feedback | Hecho, compila, tests verdes | Captura nativa. |
| 2 | Modos de la tarjeta | Hecho, compila, tests verdes | Captura nativa. |
| 3 | Lista | Compila, tests verdes | Captura nativa. Aproximado: el blur de la isla. |
| 4 | Etiquetas, configurar serie, reemplazo, avisos | Compila, tests verdes | Captura nativa; test de dispositivo del gestor de etiquetas sin correr. Aproximado: el blur del velo. |
| 5 | Análisis, encuadre, selector de voz, asistencia | Compila, tests verdes (6 actualizados) | Captura nativa; tests de dispositivo de voz sin correr. «Reanudar comandos» no aplica (§5). |
| 6 | Modo Voz como cápsula | Compila, tests verdes | Captura nativa. No hecho: valores que vuelan a la tarjeta; registros «Comando». |
| 7 | Juegos | Compila, tests verdes | Captura nativa; tests de dispositivo de juegos sin correr. |
| 8 | Cardio | Compila, tests verdes | Captura nativa. No hecho: «Ahora vamos con fuerza». |

## 1. Descanso (rehecho)

Archivos: `components/RestRef02.kt` (nuevo: eclipse, controles, sugerido, hoja «Descanso sugerido», pantalla completa, tarjeta, feedback), `RestLiveCard.kt`, `RestFeedbackCard.kt`, `WorkoutRestOverlay.kt` (`RestTimerOverlay`), `RestOverrunLayer.kt`, `RestArcade.kt` (marco del selector), `WorkoutPostExerciseFeedbackHost.kt` (`PostExerciseFeedbackPanel`).

- Tarjeta (`restCard`): «DESCANSO» + expandir; eclipse 174/153; −15 s · pausa · +15 s; SIGUIENTE; «Descanso sugerido 2:30 ›» al fondo; pie «Jugar mientras descansas».
- Pantalla completa (`.rest-fullscreen`): «PAUSA ENTRE SERIES» + ejercicio + minimizar; «Una pausa antes de la siguiente.»; eclipse 266/205; controles; sugerido; ÚLTIMA | SIGUIENTE; «Jugar mientras descansas»; pie «Terminar descanso». **Sin scroll.**
- Tiempo extra: la misma pantalla con «Tu descanso se ha cumplido.», «+0:12 · Tiempo extra», «Necesito +30 s» y «Continuar entrenando»; +15 s suma 15 s reales.
- Feedback (tarjeta y completa): «EJERCICIO TERMINADO», llama, «Calidad técnica 7/10» con deslizador, «Molestias › Sin seleccionar» (hoja con el buscador y el motor), «Rodilla · reportada antes · Sigue / Se resolvió», «Ahora no / Guardar feedback»; en completa, el reloj pequeño «de 2:00 · el reloj sigue».
- Hoja «Descanso sugerido»: PLANIFICADO → SUGERIDO, datos, «Aplicar m:ss», «Mantener descanso actual».
- **Eliminado del descanso** (diseño antiguo distinto al artefacto): forma de RINGS y su selector, experiencia de primera vez, franja del vídeo de la serie, módulos «Plan de sesión» / «Última serie» con insignias, «Saltar series restantes» (sigue en Opciones del ejercicio), relator, velo con haze.
- Radio de las tarjetas: 25 dp (`.v2-card`), también en la tarjeta de serie.

## 2. Modos de la tarjeta (escenas del selector)

**Estado 28-oct (tarde):** rehechos tal cual el artefacto y con tests en verde (1.869): frente común de todos los modos (kicker con lado / ronda / AMRAP / calibrador, CARGA con referencia «100 × 8 · RIR 2» + icono, 5 botones de repeticiones o campo ±1 para tiempo/distancia/unidad, rango de esfuerzo con «RIR 2 · plan» y «Fallo», detalle de 27, pie de 58), RM por la hoja «Referencia de RM», cara Avanzado (Normal · Drop · Rest-pause · Myo, cadena, conmutador, campos, nota/tramo/error en hojas, «Volver al registro»), serie con incidencia, resultado de calibración y «Técnica en curso». Ficha: `TARJETA-REF02-UI.md`. **28-oct (noche), SIN COMPILAR** (el usuario pidió no lanzar Gradle hasta que lo pida; revisado a mano):
- Movilidad (`mobilityCard`): «MOVILIDAD 2 / 3», «Prepara el movimiento.», pestañas Checklist (por defecto) · Movimiento · Articulaciones (zonas del bloque, cada una abre sus movimientos), guía del movimiento en hoja con su reloj y «Marcar completado», pie «Omitir» / «Continuar».
- Aproximaciones (`warmupCard`): «APROXIMACIONES 1 / 2», «Acércate a tu carga.», «Trabajo · 100 kg» + «Ajustar» (hoja con los kg de cada aproximación), filas «A1 · 20 % · 20 × 10» con ✓/+, tres por página, «+ Aproximación», pie «Omitir» / «Ir a efectivas».
- Descanso corto de la preparación (`warmup-rest`): la tarjeta del descanso «DESCANSO · APROX.» con el eclipse, la siguiente aproximación y «Terminar descanso» (sin pausa ni ajuste: atenuados).
- Modo visual en la tarjeta (`mediaCard`): «‹ Serie 3» · «Álbum», vista con «Vídeo»/«Foto» y «Ficha», disparador de 49 centrado, insignia «Cámara»/«Grabando», resumen del análisis con «Abrir análisis ›», pie «Revisar trayectoria y métricas». Quitados de la tarjeta: «Expandir», galería y miniaturas (siguen en el álbum y en la vista expandida).
- **Pendiente:** compilar y actualizar los tests de estas tres tarjetas (cambian textos y etiquetas), la vista expandida del modo visual y la captura en dispositivo.

Lo que la auditoría encontró antes del cambio:

Con criterio estricto **ninguna escena sale IGUAL**. Marco común que difiere en todas las de repeticiones (y que la captura 01 muestra distinto):
- Referencia «100 × 8 · RIR 2» **sin «Anterior»** y con **icono** de historial (hoy «Anterior …» + «Historial» subrayado).
- «REPETICIONES» con **5 botones** alrededor del plan (hoy rueda de 0 a máx.).
- «ESFUERZO · Sin marcar ⌄» con el **rango** del prototipo (RIR 10 · RIR 2 · plan · Fallo); hoy deslizador de fuego y el valor añade una palabra.
- Alturas: kicker 29 (hoy 44), detalle 27 (hoy 44), pie 58 con acciones de 40 (hoy 44).

Por escena:
- unilateral: «SERIE 3L / 8» y botón de lado con borde (radio 7) a la derecha; sin el bloque «Izquierda (L) / Derecha (R)» en modos no-REPS.
- superset: kicker «RONDA 2 · A1 / 3» (FALTA).
- amrap: kicker «AMRAP / 4»; «Mínimo 6» atenuado junto a «REPETICIONES» (hoy insignia naranja en el kicker).
- calibrator: kicker «Calibrador / 4»; «0 reps permitido» junto a «REPETICIONES».
- calibration-result: «CALIBRACIÓN · Registrada», «RESULTADO 8 reps · 100 kg · RIR 2», propuesta «102,5 kg», «Mantener el plan», «Corregir registro», «Aplicar propuesta».
- rm: misma tarjeta base con «CARGA» ±2,5 y referencia «80% · RM 125 kg» + icono (hoy «CARGA UTILIZADA», botón de %, «Calculado…», «REPS», icono «+»).
- solo-rpe: orden carga → reps → esfuerzo; «CARGA» con «—»; rango RPE; sin «Sin carga fija…».
- time: «TIEMPO» con play/pausa y «0:00» en su cabecera; campo «45 s» ±1; orden carga → cantidad → esfuerzo.
- distance: «DISTANCIA», «40 m» con ±1 juntos tras la unidad; sin objetivo ni barra.
- custom: «TRAMOS», «4 tramos» con ±1 juntos.
- bodyweight / assisted: «AYUDA EXTERNA» (asistida); resto del marco común.
- partials / rom: sin «· Nota»; sin deslizador de ROM en la tarjeta (ROM solo en «Detalle del registro»).
- drops / restpause / myo (cara Avanzado): pestañas «Normal · Drop · Rest-pause · Myo»; «CADENA DE LA SERIE · 3 tramos ›»; conmutador «PRINCIPAL 100 × 8» con «Carga, repeticiones y esfuerzo de la serie principal se conservan.»; «Añadir nota ›», «+ Añadir tramo», «Error de ejecución» (hoja); campos «Pausa · s» antes de «Repeticiones»; pie «Volver al registro»; sin textos didácticos extra.
- guided: hoja «Técnica en curso» con «Rest-pause · tramo 1 de 2», reloj, «Principal: 100 kg × 8 · RIR 2»; salida como hoja.
- failed: tarjeta «Serie con incidencia»: «SERIE 3», «!» 49, «Error de ejecución» Syne 23, «100 kg × 8», «El fallo muscular se registra en esfuerzo.», «Revisar motivo» (hoja con 3 motivos) y «Recuperar serie» (hoy velo rojo con campo y «Revertir»).
- visual / visual-expanded: «Álbum» con texto, conmutador Vídeo, «Ficha», captura redonda 49, resumen «8 completas · 2 parciales» + «Abrir análisis →», pie «Revisar trayectoria y métricas»; expandida con «Serie 3 · 100 kg · 8 reps», «Ver análisis» y pie «Volver al registro».

## 3. Lista (01, 06–08)

**Escrito el 28-oct (noche), sin compilar** (`LiveSessionListView.kt`, `WorkoutV2Body.kt`, `WorkoutHeaderBar.kt`): columnas 25/·/91/47/24 (18/·/87/43/30 en superserie); fondo activo con −5; tipo sin negrita; ✓ en omitidas e incidencias; el esfuerzo de la fila abre «Esfuerzo realizado»; ✓ con esfuerzo registra de un toque y abre la siguiente pendiente; editor con padding 14, −1,5, cantidad 48, referencia del historial o «Sin historial», error #ff9b92, validación de carga, «Fallo» con peso 1; hoja de esfuerzo completa (RIR/RPE, 0–10, «Otro valor…» + «Aplicar valor», «Llegué al fallo», «Dejar sin informar», no se cierra al elegir); «Avanzado» y «Modo visual» en línea en el editor; «Opciones de la serie» con la rejilla Configurar · Avanzado · Parciales y ROM · Nota y las filas Añadir · Omitir · Quitar; «SUPERSERIE A», filete crema continuo, sangría 10, ronda con 8/15, padding solo en el 2.º; sin chip rojo; metadatos 10; preparación solo en su fase; aproximaciones A1/A2 como filas (47; 24/46/·/36; Syne 16); movimiento → hoja; «Articulaciones» → hoja; «Referencia · kg» → hoja de cargas; el plegado cuenta aproximaciones; ⋯ de 18; «AHORA · Descanso» con su tarjeta y «Terminar descanso»; isla flotante (la lista pasa por debajo; Compose no desenfoca lo de detrás, así que es vidrio translúcido sin blur); «Ir a efectivas» conserva la serie elegida; bloque de cardio propio.

Lo que la auditoría encontró antes del cambio:

FALTA:
- Aproximaciones A1/A2 como filas dentro del bloque del ejercicio (hoy solo viven en la preparación y desaparecen al completarla).
- Descanso dentro de la lista: bloque «AHORA · Descanso» con su tarjeta (`warmup-rest`) y botón «Terminar descanso» abajo en lista.
- Tocar el esfuerzo de una fila abre «Esfuerzo realizado» de esa serie.
- Hoja «Esfuerzo realizado» completa: conmutador RIR/RPE, RPE 0–10, «Otro valor…» + «Aplicar valor», «Dejar sin informar», no cerrar al elegir.
- «Modo visual» en las acciones del editor; «Avanzado» como panel en línea (Normal/Drop/Rest-pause/Myo, tramos, nota, error).
- Movilidad: tocar el movimiento abre su hoja; pie «Articulaciones» abre la hoja de articulación.
- Aproximaciones: «Referencia · 100 kg» abre «Carga de referencia de trabajo».
- ✓ de una serie hecha abre «Opciones de la serie» completas (Configurar, Avanzado, Parciales y ROM, Nota, Añadir/Omitir/Quitar, Marcar error).

DISTINTO:
- Un toque en ✓ con esfuerzo marcado debe registrar (hoy abre y hace falta otro toque); tras registrar, abrir la serie siguiente (hoy cierra el editor).
- Fila activa: fondo con margen −5 (contenido alineado con las demás). Columnas 25/·/91/47/24. Tipo de la activa sin negrita.
- Omitida/incidencia muestran ✓ con controles en línea.
- Editor: padding lateral 14 y margen −5; letter-spacing −1,5; cantidad en ancho fijo 48; referencia = historial anterior o «Sin historial»; color de error #ff9b92; validar carga; «Fallo» con peso 1.
- Superserie: «SUPERSERIE A»; filete crema 2 dp continuo; filas con sangría 10 y columnas 18/·/87/43/30; ronda en x=15 con márgenes 8/15; padding-top 9 solo en el 2.º ejercicio; sin chip rojo «Superserie».
- Preparación solo en fases de movilidad/aproximación; aproximación: alto 47, columnas 24/46/·/36, cifra Syne 16, % 11 muted; índice de movilidad 15.
- Isla flotante con blur sobre la lista y 112 de margen superior; margen inferior 28 (no 44); metadatos 10 sp; «Ir a efectivas» conserva la selección; plegado cuenta aproximaciones; icono ⋯ 18.

## 4. Contexto y avisos (09–12)

**Escrito el 28-oct (noche), sin compilar.** Etiquetas (`WorkoutExerciseSheets.kt`): el ejercicio en Syne 22; sin educación, vacío ni subetiquetas en la hoja principal; «Nueva etiqueta» con «Guardar etiqueta» en el pie; «Contexto del ejercicio» (dos columnas, REFERENCIA DE ESTA ETIQUETA, «Reiniciar referencia de historial» en rojo → «¿Reiniciar esta referencia?», «Guardar etiqueta»); «Ya existe una etiqueta con ese nombre.». Se conservan al final, como acciones de texto, «Subetiquetas» (hoja propia) y «Eliminar etiqueta» (refinar, no quitar). Configurar serie (`LiveSetConfigSheet.kt`): Formato (7), Intensidad + Valor en una fila, Propósito como selector, «Parciales, ayuda y ROM», «El detalle de ejecución conserva su propio borrador.», «Guardar objetivos», alerta «Tienes cambios sin guardar»; RM / TM pide «% de RM» y Solo RPE quita la carga fija. Reemplazo (`LiveReplaceSheet.kt`, `WorkoutStructureSheetsHost.kt`): hoja con búsqueda, candidatos «revisar antes de aplicar» (sin búsqueda, los que comparten músculos principales), «YA REALIZADO», paso «Revisar reemplazo» y «Abrir el catálogo completo». Avisos: «No se pudo guardar» con reintento real del mismo registro, conflicto en dos pasos, molestia con «Ver opciones del ejercicio», icono info. Velo #0008 sin desenfoque (Compose no lo tiene).


Etiquetas: nombre del ejercicio en Syne 22; quitar aviso educativo, estado vacío y bloque de subetiquetas de la hoja principal; «Nueva etiqueta» con «Guardar etiqueta» en el pie y sin «Volver a las etiquetas»; **tag-setup**: título «Contexto del ejercicio» + subtítulo con el ejercicio; Equipo / marca y Carga base · kg en dos columnas siempre visibles + Notas de setup; sin «Guardar set-up» (guarda el pie «Guardar etiqueta»); «REFERENCIA DE ESTA ETIQUETA» con una referencia; «Reiniciar referencia de historial» en rojo → alerta «¿Reiniciar esta referencia?»; texto de duplicado «Ya existe una etiqueta con ese nombre.».

Configurar serie: selector «Formato» (7 opciones) en Objetivo; Intensidad (selector RIR/RPE) y Valor objetivo en la misma fila; Propósito como selector; fila «Parciales, ayuda y ROM» en Detalle; nota «El detalle de ejecución conserva su propio borrador.»; botón «Guardar objetivos»; cambios sin guardar como alerta centrada («Tienes cambios sin guardar» · «Seguir editando» primario · «Descartar cambios» texto).

Reemplazo: hoja «Reemplazar ejercicio» (no página de catálogo) con «Reemplazar el trabajo pendiente de X.», «Buscar ejercicio» / «Nombre del ejercicio», resultados con «revisar antes de aplicar», «YA REALIZADO · N series se conservan»; paso **«Revisar reemplazo»**: «REEMPLAZO PARA LO PENDIENTE», nuevo ejercicio, «Desde X», «Se conserva del plan» (con descanso), «Contexto propio del reemplazo», nota, «Aplicar reemplazo», «Elegir otro ejercicio».

Avisos: esfuerzo «Marca tu esfuerzo antes de registrar.» en línea #ffb5ad 10 sp; velo de alertas con blur 7; molestia: cuerpo y «Ver opciones del ejercicio»; **«No se pudo guardar»** (Reintentar / Volver al registro); conflicto: «Ya hay una sesión en curso», «Puedes reanudar … Empezar otra descartaría la sesión pendiente.», «Reanudar sesión», «Revisar descarte» → segundo paso «¿Descartar la sesión pendiente?» (Conservar sesión / Descartar); icono info.

## 5. Análisis, encuadre y voz (13–20 + voz de la iteración 03)

**Escrito el 28-oct (noche), sin compilar.** Análisis: «ANÁLISIS» + «Sentadilla · serie 3» + cierre X (⋯ se conserva); «i» junto al vídeo con «Cómo leer este análisis»; pestañas subrayadas; «8 + 2 · completas + parciales»; «Tiempo bajo tensión»; ROM «estimado · vista lateral/oblicua»; pérdida de velocidad «≈ · comparación relativa»; «Repeticiones» abre por ROM; «Analizando…» / «Analizando repeticiones y amplitud.»; en la tarjeta «Analizando…», «Requiere revisión», «8 completas · 2 parciales». Selector de voz: «Elige cómo entrenar con voz.», Música → Manos libres con icono y radio, «Entrada de audio», «Probar este modo», onda de 25 barras, «Di «Listo»» / «Te escuchamos.», «Activar modo de voz», «Ahora no». Encuadre: marco de cuatro esquinas, «✓ Cuerpo y barra visibles» y frases completas, «Vista lateral · cómo encuadrar» + «Preparar el encuadre» (4 pasos), «PREPARAR CAPTURA» / «● GRABANDO · m:ss», REC crema, píldora de comando pulsable. «Reanudar comandos»: en la app pausar la voz es desactivarla, así que dice «Activar Modo Voz». Asistencia: «Ayuda y explicaciones» → «Asistencia de sesión» con sus tres filas.

### 5b. Modo Voz como cápsula (iteración 03)
**Escrito sin compilar** (`VoiceCapsule.kt`): cápsula sobre la sesión (sobre el roadmap; en Lista, abajo) con «Te escucho», orbe, serie y cómo decirlo, contador, pausa y ⌃; recibo «Serie N registrada» con «Deshacer» (el mismo token del comando de voz) y «Editar»; captura ambigua «Por revisar» con lo dudoso discontinuo, chips y «Descartar» / «Registrar»; registros a 2/3 de pantalla con «Todo» / «Por revisar», Registrada / Por revisar / Corregida, editar con chips, «Escuchar frase de ejemplo» y «Desactivar Modo Voz»; «Escucha en pausa»; comandos del descanso y de la captura; «KPKN habla» con «Detener». **No hecho:** valores que vuelan a la tarjeta y registros «Comando» (el historial solo guarda series).


Análisis: eyebrow «ANÁLISIS»; «Sentadilla · serie 3» en una línea; cierre con X; botón info junto al vídeo + hoja «Cómo leer este análisis» (Qué se puede comprobar · Unidades y confianza); pestañas subrayadas; métrica «Repeticiones 8 + 2 · completas + parciales»; «Tiempo bajo tensión» como título; ROM «estimado · vista lateral»; pérdida de velocidad «comparación relativa»; selector por defecto «ROM»; detalle «Repetición 9 · Parcial · ≈58°»; estados «Analizando…» con «Analizando repeticiones y amplitud.» y «Resultado no concluyente.» con su explicación; entrada «Revisar trayectoria y métricas». Extras nativos (⋯, módulos de calibración, observaciones) a revisar con el criterio del artefacto.

Selector de voz: subtítulo «Elige cómo entrenar con voz.»; orden Música → Manos libres con sus subtítulos e icono + radio; fila «Entrada de audio»; «Probar este modo»; «Ahora no» bajo el cuerpo; «Comprueba el modo antes de entrenar.»; onda de 25 barras; «Te escuchamos.» + «Activar modo de voz».

Encuadre: marco de cuatro esquinas (verde al estar listo); «✓ Cuerpo y barra visibles»; enlace «Vista lateral · cómo encuadrar» + hoja **«Preparar el encuadre»** (4 pasos); texto de guía visible; «PREPARAR CAPTURA» / «GRABANDO»; frases completas en el chip; textos de pies y ángulo del prototipo; REC crema con icono; píldora de comando pulsable; «Reanudar comandos».

Modo Voz (iteración 03, aprobada): **cápsula flotante sobre la sesión** («Te escucho», orbe, contador, «Ver registros de voz»); valores que vuelan a la tarjeta con recibo «Deshacer / Editar»; captura ambigua con «Descartar / Revisar»; registros expandidos «Todo / Por revisar» con estado «Comando»; editar con chips; pausa atenuada; cápsula sobre Lista; «KPKN habla» con «Detener»; estados de vídeo visibles. Hoy es una pantalla completa.

Relator: hoja «Asistencia de sesión» con sus tres filas (FALTA).

## 6. Iteración 03 · Cardio y juegos (aprobada)

**Juegos, escrito sin compilar** (`RestArcadeGames.kt`, `RestArcade.kt`, récord por juego en `RestGameRecordStore`): física y reglas del prototipo en los tres juegos; selector con la cabecera del descanso (anillo, siguiente, −15 / pausa / +15), vistas previas animadas, descripciones, «Récord N · 3 vidas» y «El descanso manda…»; HUD; pausa con velo; «Otra partida / Cerrar juego»; «Volver a la serie / Seguir jugando · cuenta como extra»; a pantalla completa solo la cabecera y el lienzo.

**Cardio, escrito sin compilar** (`CardioLiveSpace.kt`, `CardioLiveCard.kt`): el descanso anuncia el cardio; «Ahora vamos con cardio» con deslizar en sesión mixta; «Antes de empezar»; 3-2-1; Ahora · Plan · Resumen; anillo con la corona de la zona; pulso y objetivo (lpm con la edad, si no % de FC máx); cinta con aguja; «En tu zona» / «Baja el ritmo» / «Sube el ritmo» con histéresis; guía RPE sin pulso; caída de pulso en la recuperación; exterior por distancia y ritmo; chips; siguiente paso; perfil; dock Bloquear · Pausa · Saltar paso; pausa con velo; bloqueo manteniendo 1,2 s; cierre con lo medido, cumplimiento por paso y RPE real obligatorio (se guarda en la serie); bloque en Lista. **No hecho:** «Ahora vamos con fuerza».


Cardio — FALTA casi todo: descanso que anuncia cardio; transición «Ahora vamos con cardio» con deslizar; cuenta 3-2-1; espacio propio con cinta de zonas, objetivo de zona, «En tu zona», chips de velocidad/inclinación/fuente; aviso por encima de zona con histéresis; guía RPE sin pulso; recuperación con caída de pulso; exterior por distancia con ritmo objetivo y GPS; páginas Plan y Resumen; pausa con velo; bloqueo con mantener; cierre con RPE real obligatorio y cumplimiento por paso; transición «Ahora vamos con fuerza»; sesión solo cardio con comprobaciones; bloque de cardio en Lista.

Juegos — DISTINTO/FALTA: selector con cabecera de descanso (anillo, siguiente, −15/pausa/+15), vistas previas animadas, descripciones propias, «Récord N · 3 vidas», pie «El descanso manda…»; **Salta las mancuernas**: salto variable, gracia, doble salto con tiza, «¡Justo!», racha, kettlebell y pila, línea de récord, fin con «Otra partida / Cerrar juego»; **Entre las pesas**: física del prototipo, huecos que se mueven, «Perfecto» y racha, INHALA/EXHALA, récord; **Escuadrón retro**: control relativo, oleadas con patrones, jefe cada cuatro, potenciadores, «Roce +5», disparo apuntado, récord, sin botones de mover; pausa con velo y aviso; fin de descanso con «Volver a la serie / Seguir jugando»; colores crema (melocotón solo jugador, golpes y potenciadores).

## 7. Orden de trabajo

1. Descanso (hecho; falta verificación en dispositivo).
2. Tarjeta base y modos (§2): es lo que más se usa; el marco común arregla de una vez todas las escenas de repeticiones.
3. Lista (§3).
4. Contexto y avisos (§4).
5. Análisis, encuadre y selector de voz (§5).
6. Modo Voz como cápsula (iteración 03).
7. Juegos (iteración 03).
8. Cardio (iteración 03): el más grande; parte del código v2 está en `C:/kw/u5`.

Cada área termina con compilación, tests y capturas nativas; ninguna se declara terminada sin captura.

**28-oct (noche):** pasos 1–8 escritos en el código. Lo siguiente, cuando el usuario lo pida: compilar, arreglar lo que salga, actualizar los tests de la lista de arriba, capturas en el teléfono y, con su visto bueno, commit y APK.
