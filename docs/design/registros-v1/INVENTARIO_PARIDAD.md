# Inventario de paridad · Registros v1 (v4) frente a la app

Fecha: 28-oct-2026. Rama de trabajo `claude/program-detail-progress-redesign-b0a1e5`. Código leído de `origin/master` (`aaad22c84`), no del árbol de trabajo.
Regla (orden del usuario: «al pie de la letra, 99,999 % igual»): lo que la app pinta y el lienzo no contempla **se elimina**. Lo que el lienzo tiene y la app no tiene, o tiene distinto, **se rehace igual**. Lo que no se puede decidir leyendo el lienzo va a **Dudoso**, con una pregunta concreta.
Excepción del usuario sobre Nutrición: el gráfico del día (la «huella» de vitaminas y minerales) aplica SOLO a ese elemento. En el resto de la pantalla de Nutrición mandan los otros diseños aprobados (registro, búsqueda…). Por eso las escenas `n-day` y `n-day-sheet` solo mandan en su módulo de vitaminas y minerales (ver M-D1).

Rutas abreviadas (raíz `android-native/app/src/main/java/com/example/kpkn/`):
- `co/` = `screens/competitions/` · `cw/` = `screens/competitions/wizard/`
- `pg/` = `screens/programs/` · `ob/` = `screens/onboarding/` · `do/` = `domain/onboarding/`
- `nu/` = `screens/nutrition/` · `lg/` = `screens/nutrition/design/logger/` · `nc/` = `screens/nutrition/components/`
- `ho/` = `screens/home/` · `st/` = `screens/stories/` · `pd/` = `screens/programdetail/`
- Lienzo: `docs/design/registros-v1/src/scenes-*.js` (se cita el id de la escena). Piezas comunes en `base.js`, `app.js`, `data.js`, `extra.css`.

Los números de línea son los de `origin/master`. Si un elemento ocupa un bloque, se da el inicio.
Cruces con el otro inventario (Program Detail, `docs/design/program-detail-v1/INVENTARIO_PARIDAD.md`): el módulo de competición del programa (p-comp) y los iconos de la hoja «Nuevo programa» (P-F5…P-F7) se tratan allí; aquí solo se cruzan donde chocan (C-D3).

---

## 0. Decisiones del lienzo (escena `d-all`, `scenes-decisiones.js`)

### Decidido (tablas «Hoy → Propuesta»)

**1 · Competencias**
- Una sola ficha en tres momentos: **próxima**, **hoy** (modo competición que va guardando cada intento) y **resultado**. «Ya competí» registra el resultado directo en un minuto.
- Una página «Competencias» (próxima arriba, historial debajo), a la que se llega desde Perfil, Inicio y la portada del programa. Sustituye al archivo de Perfil, la ruta muerta, el aviso de Inicio y el módulo del programa.
- Todas las disciplinas de KPKN (se dice «las 29 del modelo de deportes»), con **seis formas de resultado**: intentos de carga · pruebas con marca · contra el reloj · jueceo en tarima · combates · partidos y tops. Room no cambia (la competencia ya se guarda como JSON completo).
- Pesos desde el plan o desde tus marcas (adiós al relleno de 100 kg); un toque alterna válido/nulo.
- «Planificar intentos» y «modo competición» **pasan a la competencia**; el programa enlaza con ella.
- Puesto opcional y exacto (no «4+» mostrado como «4.º»); trofeos como extras.
- Diario liviano: caras + «Lo mejor» + «Para la próxima»; nunca se borra al reabrir.
- ⋯ con **Archivar** y **Eliminar** reales (hoy «Archivar sesión y registro» borra para siempre). Eliminar borra también las fotos.
- El total se recalcula siempre (fallo: no baja si un válido pasa a nulo).
- Avisos como interruptores visibles y hora del pesaje; fotos quitadas se borran del almacenamiento.
- Compartir: historias con imagen o vídeo de 6 s.

**2 · Crear un programa**
- Inicio abre **la misma hoja «Nuevo» de cuatro caminos** (A tu medida · Del catálogo · Importar · Desde cero).
- «A tu medida» **reutiliza el asistente** con una instancia «Nuevo programa»: cabecera propia, lo conocido plegado arriba y cierre «Listo para empezar».
- Género y % de grasa no se piden en esta instancia si faltan.
- Al terminar vuelve a **Programas** con el programa nuevo abierto.
- Una sola pantalla «Listo para empezar» en los cuatro caminos: ahora, en cola o guardar. Nada se activa sin que lo decidas.
- «Configurar este plan» abre «A tu medida» con objetivo y días ya puestos.
- Se retira el código muerto de calibración y ficha de protocolo en Programas e Inicio («sale Calibrar»).
- Días 1–7 en todos los sitios.

**3 · Registro de comida**
- Motor local y sin conexión; no cambia.
- Un solo botón **«Analizar»** («Volver a analizar» si cambias el texto).
- Animación de plato de líneas con palabras que se subrayan; mensajes que dicen lo que pasa.
- Todas las dudas en su tarjeta, respuesta marcada, respuesta por defecto; **se guarda con la estimación por defecto** (≈ en la tarjeta).
- −/+ en la unidad natural, gramos o raciones a un toque; cifras de macros editables al tocarlas.
- «Repetir» y atajos pasan a Buscar; el campo vuelve a ser lo primero.
- Búsqueda al escribir, filas de carga, una línea por resultado, filtros; ficha de alimento; siempre «Añadir» (nunca «Agregar»).
- Huella de nutrientes en la ficha, el registro, la hoja de ajuste y el día; una fila por nutriente; hoja propia por nutriente; «sin dato» nunca es 0.
- «Olvidar lo aprendido» con confirmación, sin tocar el borrador.
- Hora con atajos, calendario y rueda. Avisos de error sin jerga técnica (qué se entendió y qué hacer).
- Registrador rápido en Inicio (barra «¿Qué comiste?» y media hoja) sin salir de Inicio.

**4 · Historias**
- Competencia: Total, Intentos, Foto y Próxima; imagen o vídeo de 6 s con la felicitación.
- Cuerpo: Cambio y Medidas, **sin peso absoluto por defecto**.
- Nutrición: Semana, Macros y Racha; **nunca qué comiste**.

### Abierto (sección «5 · Para decidir»)
- **Pregunta 1.** Fotos de progreso del cuerpo (la app no las guarda, solo fotos de entrenos). ¿Se añade esa función en otra ronda? Sin respuesta; mientras tanto no se dibuja nada de «antes y después» (la escena `c-bb` también lo deja abierto).
- **Pregunta 2.** Vídeo de la historia: se recomienda solo para la competencia. ¿Se amplía al resumen del mes? Sin respuesta; vale la recomendación (solo competencia).

### Contradicciones internas del lienzo (van a Dudoso)
- `c-hub` dice «Sale de Perfil › Archivo» pero la nota dice que se llega «desde Perfil» (C-D1).
- `d-all` habla de «las 29 del modelo de deportes» y `c-new` dibuja 28 botones (C-D5).
- `d-all` dice que «Planificar intentos» sale del programa; el lienzo de Program Detail (p-comp) lo conserva como píldora secundaria (C-D3).
- `f-error` dice en sus notas «icono de aviso (no de información)» pero dibuja `info` (F-D4).

---

## 1. Competencias (`scenes-competencias.js`: c-*)

La app tiene un asistente de cuatro pasos solo de powerlifting y un archivo dentro de Perfil. **Casi todo el lienzo es nuevo.** Resumen de lo que hay hoy: `cw/CompetitionWizardScreen.kt` (4 pasos EVENT, LIFTS, PLACE, ALBUM), `co/ProfileCompetitionsArchive.kt` (módulo dentro de Perfil), `data/models/CompetitionModels.kt`, `data/repository/CompetitionRepository.kt`.

### Sobra
- **C-S1.** Etiqueta fija «POWERLIFTING» arriba (`cw/CompetitionWizardScreen.kt:88-94`) y barra de progreso lineal de 3 dp (`:103-117`). En c-next, c-log, c-place y c-feel la cabecera es una fila `back(título)` con la flecha y tres segmentos `steps`; no hay etiqueta de disciplina.
- **C-S2.** Barra inferior de vidrio con «Atrás» textual + «Continuar» / «Guardar» (`cw/CompetitionWizardScreen.kt:145-184`). El lienzo pone «Continuar» a todo el ancho dentro del cuerpo (c-next, c-plan, c-log, c-place) y «Saltar» como texto (c-place). El cierre es «Guardar y celebrar» con icono confeti (c-feel).
- **C-S3.** Botón rojo cuadrado X «Cerrar» de 52 dp (`cw/CompetitionWizardScreen.kt:185-196`). Ninguna escena lo dibuja; la salida es la flecha atrás.
- **C-S4.** Panel de fecha de 108 dp con el día en 48 sp y el mes en dos líneas (`cw/CompetitionWizardEventStep.kt:82-107`). c-next: campo `field` «Fecha / dom 16 nov» en una rejilla de dos columnas con «Hora del pesaje».
- **C-S5.** Campos «Ciudad o recinto» y «Nombre del meet (opcional)» como píldoras sin etiqueta (`cw/CompetitionWizardEventStep.kt:109-118`). c-next: campos con rótulo pequeño «Nombre» y «Lugar», en ese orden (nombre primero).
- **C-S6.** Selector de federación a pantalla completa: continentes, «Buscar», rejilla de logos de 56 dp y «Otra» (`cw/CompetitionWizardEventStep.kt:236-302`). c-next solo dibuja el campo «Federación · puntos / AEP (IPF) · IPF GL». Ver C-D7.
- **C-S7.** Pesaje con botones ±0,1 kg que arrancan en 80 kg (`cw/CompetitionWizardEventStep.kt:156-161`, `cw/CompetitionWizardViewModel.kt:120`). c-next: «Hora del pesaje» (campo) y el peso se escribe con teclado numérico (nota de la escena).
- **C-S8.** Paso de intentos: panel `livePointsLabel` (`cw/CompetitionWizardLiftsStep.kt:53-61`), una tarjeta por levantamiento con título, campo «Nombre del movimiento» + sugerencias + «Usar “…”» (`:98-142`), círculos de 72 dp con aro de color (`:182-205`), stepper ±2,5 kg (`:159-165`), «Mejor válido N kg» (`:166-169`) y «Añadir otro movimiento» (`:73-75`). c-log: tres filas `att` (Sentadilla / Press banca / Peso muerto) con tres botones `ab` «peso + Válido/Nulo», barra de total debajo y nada más. Ver C-D6.
- **C-S9.** Ciclo de cuatro estados pendiente → válido → nulo → saltado (`cw/CompetitionWizardViewModel.kt:144-148, 275`) y relleno de 100 kg (`:152`). c-log: un toque alterna válido/nulo; mantener pulsado abre el peso; «Saltado» queda en ese menú.
- **C-S10.** Cuadrícula de siete trofeos «Fuera del podio» (`cw/CompetitionWizardPlaceStep.kt:50-67`; lista en `domain/competitions/CompetitionPlaceHonors.kt`: Primera competición, Levantador constante, 9/9, Superó su total, Mejor sentadilla/banca/peso muerto del día). c-place: campo «Puesto» + chip «Sin puesto» y, debajo, «Algo más (opcional)» con **tres** chips: Récord nacional, Mejor levantador, Clasificado.
- **C-S11.** Paso de puesto obligatorio (`cw/CompetitionWizardViewModel.kt:197-215`, `canContinue` exige honor válido) y «4+» (`CompetitionPlaceHonors.kt: placementString`). c-place: «Saltar» y puesto exacto.
- **C-S12.** Álbum: caja de 148 dp con el botón «Añadir foto o video» y el texto «Se copia al archivo de la app» (`cw/CompetitionWizardAlbumStep.kt:58-81`), rejilla fija de 3 columnas y 280 dp (`:83-90`), etiqueta «VIDEO» (`:101-113`) y «×» de texto (`:114-121`). c-feel: tira `photos` con celdas y una celda «+» con icono cámara; sin leyenda.
- **C-S13.** `saveCompleted()` apaga los tres avisos y borra el diario (`journal = null`) (`cw/CompetitionWizardViewModel.kt:241-253`). Decidido: el diario nunca se borra y los avisos son interruptores.
- **C-S14.** El módulo «Archivo de competiciones» dentro de Perfil (`co/ProfileCompetitionsArchive.kt:84`; montado en `screens/profile/ProfileScreen.kt:295-300` con el comentario «su rediseño interno es de la propuesta Registros v1»). c-hub: «Sale de Perfil › Archivo». Ver C-D1.
- **C-S15.** Dentro de ese archivo: «Álbum» global con miniaturas de 88 dp (`co/ProfileCompetitionsArchive.kt:120-131, 277-300`), «Comparativa» con sparkline y comparación embebida con barras (`:133-142, 302-376`), botón «Comparar» / «Elegido» en cada ficha (`:196-204`), logo de federación de 44 dp (`:179-184`), mini marcadores `LiftMini` S/B/D con puntos de intento de 7 dp (`:206-210, 246-275`), círculo de honor con letra de trofeo (`:218-244`) y texto de puntos suelto (`:211-214`). c-hub no tiene álbum global; la comparación vive en c-compare (acceso desde el ⋯); la fila `meet` es medalla 46 + nombre + «fecha · ciudad» + total y puntos a la derecha.
- **C-S16.** Estado vacío «Registrar powerlifting» y botón «Registrar otra» (`co/ProfileCompetitionsArchive.kt:95, 153`). c-hub: botón secundario «Registrar una competencia» con icono plus; no hay escena de vacío (ver C-D8).
- **C-S17.** Inicio: botón «Modo competición» (barra `ho/HomeScreen.kt:1198-1207`, héroe `ho/design/HomeSessionHero.kt:405-408`) y el cuerpo «Abre el modo competición desde tu programa.» (`ho/HomeSessionSection.kt:247`). El modo competición pasa a la propia competencia (c-today). Ver C-D2.
- **C-S18.** Ruta `competitions` que abre Perfil (`navigation/Navigation.kt:126`, `MainActivity.kt:1207-1222`, `navigation/DeepLinkRouter.kt:47`) y rutas del asistente de PL (`MainActivity.kt:1224-1231`, `co/CompetitionScreen.kt:1-15`). Sustituidas por la página y las fichas nuevas.
- **C-S19.** En el programa: píldoras «Planificar intentos» y «Modo competición», hoja `CompetitionPlanSheet` y hoja `MeetDaySheet` («Consulta y simulación local. Al cerrar no se guardan estos resultados…») (`pd/design/ProgramCompetitionModule.kt:53-55, 68, 141-146`; `pd/ProgramDetailScreen.kt:547, 641, 647`). Decidido que pasan a la competencia. Ver C-D3.
- **C-S20.** «Archivar sesión y registro» que en realidad borra para siempre (`pd/components/editor/MacrocycleEditorLegacy.kt:742`, diálogo «Eliminar competición» `:721-761`). Sustituido por ⋯ con Archivar / Eliminar reales (c-menu).

### Falta o difiere
- **C-F1.** c-hub «Competencias» no existe (la ruta abre Perfil). Hace falta: fila `back` con botón «+» (descripción «Nueva competencia»); título «Competencias»; tarjeta **Próxima** con portada «Órbitas / noche», etiqueta «Próxima» (color músculo), cuenta atrás «18 días» que cuenta al abrir, nombre en Syne 21 («Open regional»), «16 nov 2026 · Madrid · AEP · -83 kg», fila de tres aperturas («SENTADILLA 192,5 · PRESS BANCA 130 · PESO MUERTO 210», cada una con «1.er intento») y dos botones: «Ver plan del día» (primario) y «Pesaje: 82,4 kg» (secundario, icono balanza); `group-l` «Tu historial» con «4 · 2 disciplinas»; módulo «Total en competición» con la curva y «+47,5 kg en un año · mejor IPF GL 79,4»; módulo con filas (medalla 46, nombre + chip «RÉCORD», «fecha · ciudad», total y puntos «79,4 IPF GL»), una de ellas de otra disciplina («Media maratón de Valencia», chip «MP», «1:38:12 / 4:39 /km»); botón secundario «Registrar una competencia».
- **C-F2.** c-new «Nueva competencia» (hoja): control «Voy a competir / Ya competí» y botones de disciplina en cuatro grupos: **Fuerza** (Powerlifting, Halterofilia, Strongman, Calistenia, Armwrestling, Culturismo), **Resistencia** (Carrera, Trail, Ciclismo, Natación, Remo, Triatlón, Hyrox, CrossFit), **Combate** (Boxeo, Kickboxing, Muay thai, BJJ, Lucha, Judo, MMA), **Más** (Escalada, Velocidad, Saltos, Tenis, Pádel, Equipo, Otra); las de tus programas arriba; «Continuar». La app solo entiende powerlifting (`cw/CompetitionWizardScreen.kt:89`; `data/models/CompetitionModels.kt:13-22` tiene 8 plantillas).
- **C-F3.** c-next «¿Dónde compites?» (Voy a competir, paso 1 de 3). Título Syne 26. Campos: Nombre · Fecha («dom 16 nov») · Hora del pesaje («08:30», `startTime` no tiene UI) · Lugar · «Federación · puntos». Categoría en chips «-74, -83, -93, -105, -120, +120» (la app: «59, 66, 74, 83, 93, 105, 120, 120+», `cw/CompetitionWizardEventStep.kt:304-306`). División «Open / Junior / Máster 1» (la app: Open, Junior, Sub-junior, Master 1/2/3, `:180`). Equipo «Classic / Rodilleras / Equipado» (la app: «Classic / Wraps / Equipped», `:187-195`). «Te avisamos»: tres interruptores «Una semana antes / 48 horas antes / Al empezar» con sus subtítulos (la app no los muestra: `data/models/CompetitionModels.kt:92-94`, `cw/CompetitionWizardViewModel.kt:246-248`).
- **C-F4.** c-plan «Plan de intentos» (paso 2 de 3): título «Tus nueve intentos», «Desde tus marcas: sentadilla 200 · banca 135 · peso muerto 217,5», chips Estrategia (Conservadora / Objetivo / Ambiciosa), rejilla 3×3 pendiente, barra «Total si entran 562,5 kg / IPF GL 80,9», módulo «Pesaje · objetivo ≤ 83 kg» con «82,4 kg», chip «Dentro» y «Con tu tendencia… llegas con 0,6 kg de margen», y «Guardar plan». Hoy solo existe `CompetitionPlanSheet` dentro del programa (`pd/design/ProgramCompetitionModule.kt:68`). Los intentos cambiados a mano quedan marcados y cambiar de estrategia no los pisa sin preguntar.
- **C-F5.** c-today «Hoy compites»: no existe como pantalla de la competencia (solo la hoja del programa, C-S19). Hace falta: fila `back` con píldora «En vivo»; «Press banca · 2.º intento»; «132,5 kg» en 72 px; «Si entra: 3.º a 137,5 · si no: repites 132,5»; dos botones de 64 dp «Válido» (verde, check) y «Nulo» (rojo, cruz); `group-l` «La tabla» con la rejilla y el intento actual marcado «Ahora»; módulo «Calentamiento para el peso muerto» («60 × 5 · 100 × 3 · 140 × 2 · 170 × 1 · 190 × 1 (hasta el 92 % del primero)»). **Cada toque guarda el resultado.**
- **C-F6.** c-log «Registrar el resultado» (Ya competí, 1 de 3): título «¿Cómo te fue?», texto «Toca un intento para marcarlo válido o nulo. Mantén pulsado para cambiar el peso.», rejilla con pesos del plan o de tus marcas, barra de total «Total 552,5 kg / IPF GL 79,4 / chip +12,5 kg» que cuenta al cambiar y se compara con la anterior, «Continuar». Corrige además el fallo del total (`domain/competitions/CompetitionScoring.kt:106` reutiliza `details?.totalKg` guardado).
- **C-F7.** c-place «Tu puesto» (2 de 3): título «¿Subiste al podio?»; tres medallas de 64 que giran al elegirlas (Oro / Plata / Bronce; la app: `MedalChoice` sin giro, `cw/CompetitionWizardPlaceStep.kt:72-103`); grupo «Sin podio» con campo «Puesto» («5.º») y chip «Sin puesto»; «Algo más (opcional)» (ver C-S10); «Continuar» + «Saltar».
- **C-F8.** c-feel «Para recordarlo» (3 de 3): «Cómo te sentiste» con cinco caras (Mal, Regular, Bien, Muy bien, En llamas); «Fotos y vídeos»; «Una línea (opcional)» con campos «Lo mejor del día» y «Para la próxima»; botón «Guardar y celebrar». La app solo tiene el álbum (`cw/CompetitionWizardAlbumStep.kt`) y borra el diario al guardar (C-S13). Las fotos quitadas deben borrarse del almacenamiento (`cw/CompetitionWizardViewModel.kt:195` solo las quita de la lista; `data/competitions/CompetitionMediaStore.kt`).
- **C-F9.** c-cele «¡Celebración!»: no existe. Confeti (30), estallido de líneas finas desde el centro, medalla de 132 que entra girando, etiqueta «Oro · Copa de otoño», total que cuenta hasta «552,5 kg» en 70 px, frase («Tu mejor total: +12,5 kg desde abril. / Sentadilla de 200 kg por primera vez.») y pie con «Compartir» (primario) y «Ver ficha» (secundario). Con movimiento reducido todo aparece quieto. Sin podio también hay celebración (el foco pasa a tus marcas).
- **C-F10.** c-detail «Ficha de competencia»: no existe (la app abre el asistente en edición). Hace falta: portada de 230 dp con medalla encima y flecha/⋯ de vidrio; «21 sep 2026 · Toledo · AEP» y nombre Syne 26; tres KPI (Total 552,5 kg · IPF GL 79,4 puntos · Pesaje 82,6 kg · -83); «Intentos»; «Frente a la anterior» (Sentadilla +5, Press banca 135 → 132,5 +0, Peso muerto +5, Total +12,5); «Recuerdos»; tarjeta de diario («En llamas», cita); «Compartir en historias».
- **C-F11.** c-menu «⋯ de una competencia»: no existe ningún menú. Hoja con medalla 52, nombre, «21 sep 2026 · 552,5 kg» y seis filas con icono y subtítulo: «Editar resultado / Intentos, puesto, fotos y diario», «Compartir en historias», «Comparar con otra / Elige una de tu historial», «Actualizar tus marcas / Usa estos resultados en tu programa», «Archivar / Sale del historial; no se borra», «Eliminar / Borra la competencia y sus fotos» (en rojo, con confirmación). `CompetitionRecordStatus.ARCHIVED` ya existe (`data/models/CompetitionModels.kt:25-29`), pero nadie lo escribe y `CompetitionRepository.delete` (`:81`) no se ofrece en la interfaz.
- **C-F12.** c-marks «Actualizar tus marcas»: hoy solo hay una píldora «Revisar actualización de marcas» en el programa (`pd/design/ProgramCompetitionModule.kt:49`). Hoja del lienzo: eyebrow «Después de competir», título «Tus marcas suben», frase «Tu programa «Motor y fuerza» usa tus máximos… quedarían así:», tres filas «Sentadilla 195 → 200 / Press banca 132,5 → 132,5 / Peso muerto 212,5 → 217,5», «Usar en mi programa» y «Ahora no».
- **C-F13.** c-compare «Comparar dos competencias»: la comparación hoy va embebida en Perfil (C-S15). Hace falta una página con las dos fichas arriba (medalla 40, nombre, fecha), tabla alineada de seis filas (Sentadilla, Press banca, Peso muerto, Total, IPF GL, Pesaje) y «Toca una competencia de arriba para cambiarla.»
- **C-F14.** c-run-next «Carrera · próxima (maratón)»: tarjeta con portada «Ondas / noche», «Maratón de Valencia · 6 dic», «38 días»; módulo «Tu objetivo» (3:29:00, «Ritmo 4:57 /km · tu media maratón (1:38:12) predice 3:24–3:31», acción «Cambiar»); «Plan de ritmos» por tramos (0–10 km 5:02, 10–30 km 4:56, 30–42,2 km 4:52); fila «Avisos» con interruptor. No existe (`CompetitionTemplateType.RUNNING` sin UI).
- **C-F15.** c-run «Carrera · resultado»: «Tu tiempo» 1:38:12 (rueda), KPI Ritmo 4:39 /km · General 1.204 de 9.880 · Categoría 187 M35, módulo «Por kilómetro» (21 barras, «del reloj o a mano», «Último km el más rápido: 4:21»), barra «Marca personal −3:41 · Nueva MP». No existe.
- **C-F16.** c-bb «Culturismo · preparación y resultado»: «Faltan 9 semanas · peak week en la 9»; módulo «Hacia la tarima» (78,6 kg hoy · 75 kg en tarima · −0,4 kg/sem, «de tu pestaña Cuerpo»); resultado con Categoría / Rondas / Puesto («2.º de 14») y fotos de tarima. No existe (`bodybuildingDetails` sin UI).
- **C-F17.** c-combat «Combate · resultado»: lista de combates con insignia G/P, ronda, método (Puntos 6–2, Sumisión · mataleón · 3:12, Ventaja, Puntos 0–2), «Añadir combate», barra «Balance 3–1» con medalla de plata. No existe.
- **C-F18.** c-mixed «Hyrox · pruebas con marca»: «Tiempo total 1:12:48» y lista de nueve pruebas con tiempo; sirve a Strongman y CrossFit (`customMetrics` sin UI). No existe.
- **C-F19.** c-lift «Halterofilia y calistenia lastrada»: la tabla de intentos con filas «Arrancada / Dos tiempos» (cuatro columnas) y barra «Total 241 kg / Sinclair 318,6». No existe.
- **C-F20.** c-match «Partidos y escalada»: lista de partidos con marcador («6–3 6–4») y bloque «Escalada · bloque» (Tops 4 / 5, Zonas 5 / 5, Puesto 3.º de 22). No existe.
- **C-F21.** Cuenta atrás de Inicio y portada del programa enlazan con la página nueva: hoy `onRegisterCompetition` abre el asistente (`MainActivity.kt:1068-1072`) y la barra usa «Registrar» (`ho/HomeScreen.kt:1196, 1213`).
- **C-F22.** El «Pesaje» de la tarjeta Próxima y c-plan sale de `WeighInPlanner` (ya existe, `domain/competitions/WeighInPlanner.kt`; hoy solo alimenta al programa en `pd/ProgramDetailViewModel.kt:231`); no hay ninguna pantalla de competencia que lo muestre.

### Dudoso
- **C-D1.** c-hub dice «Sale de Perfil › Archivo» y la nota dice «Se llega desde Perfil». **¿Qué queda en Perfil: una fila «Competencias» (con la próxima y su cuenta atrás) que abre la página, o nada y la entrada es solo Inicio y el programa?**
- **C-D2.** Inicio: el héroe «Hoy compites» / «¿Cómo te fue en…?» (`ho/HomeSessionSection.kt:245-250`) y la barra con «Registrar» no están en el lienzo. **¿El héroe se queda con otro botón («Modo competición» → c-today, «Registrar» → c-log) o se retira?**
- **C-D3.** Choque con el lienzo de Program Detail: p-comp conserva «Planificar intentos» como píldora secundaria; Registros dice que el planificador pasa a la competencia. **¿La píldora del programa abre c-plan de la competencia enlazada, y «Modo competición» sale del programa?**
- **C-D4.** «Ya competí»: c-log muestra tres puntos de pasos (intentos, puesto, recuerdos) y no pasa por «¿Dónde compites?» (c-next). **¿Dónde se piden nombre, fecha, lugar, federación y categoría de una competencia ya hecha?**
- **C-D5.** c-new dibuja **28** botones y d-all dice «las 29 del modelo de deportes». **¿Cuál es la que falta o sobra, y cómo se guarda la disciplina sin tocar Room (`sportType` solo tiene 8 valores)?**
- **C-D6.** El typeahead de ejercicios por levantamiento (`co/` `cw/CompetitionWizardLiftsStep.kt:98-142`, `CompetitionRepository.kt:64-66` `activeCompetitionExerciseIds`) y «Añadir otro movimiento» no están en el lienzo. **¿Se quitan (los pesos vienen del plan) o se conserva un acceso a «Cambiar movimiento» desde el menú largo del intento?**
- **C-D7.** El selector de 40+ federaciones con logos (`cw/CompetitionWizardEventStep.kt:236-302`, `data/competitions/PowerliftingFederationCatalog.kt`) no tiene escena. **¿Tocar el campo «Federación · puntos» abre una hoja con la lista actual, o una lista sin logos?**
- **C-D8.** Hub sin ninguna competencia y hub sin «próxima» (solo historial): no hay escena. **¿Qué se dibuja en cada caso?**

---

## 2. Crear programa (`scenes-crear.js`: cr-*)

Casi todas las capturas del lienzo (cr-new, cr-cat*, cr-cero, cr-imp*) son **renders reales de la rama del 28-oct**: esas pantallas coinciden por construcción (su diferencia con la app está en el otro inventario, P-F*). Lo que cambia es el **flujo**, la **instancia «Nuevo programa»** del asistente y el cierre **«Listo para empezar»**, que no existen.

### Sobra
- **CR-S1.** Inicio abre la biblioteca vieja `CreateProgramTemplateSheet` en vez de la hoja «Nuevo programa»: `MainActivity.kt:1026-1032` (`openCreateProgram` → `homeViewModel.openCreateProgramSheet()`), `ho/HomeViewModel.kt:75-81`, `ho/HomeScreen.kt:414-448`. La hoja vieja trae título «Nuevo programa» a 20 sp, «Crear desde cero», «O elige un plan. Antes de activarlo confirmamos tu material, tus días y tu tiempo.», filtros Perfil / Días / Duración / Procedencia, tarjetas «Desde cero» e «Importar un programa» y «Cerrar» (`pg/CreateProgramTemplateSheet.kt:202-341`). cr-new: Inicio abre «Nuevo programa» con los cuatro caminos (`pg/NewProgramSheets.kt:100-146`).
- **CR-S2.** Código que solo alcanzaba esa hoja desde Inicio: `ProtocolDetailSheet` y `TrainingMaxWizard` en `ho/HomeScreen.kt:451, 461` (con `selectedProtocol` en `:173`). Al hacer CR-S1 quedan muertos y se retiran (decidido: «código muerto en Inicio»). En Programas siguen vivos (`pg/ProgramsScreen.kt:363, 375`) porque cr-cat4 usa los máximos.
- **CR-S3.** El cierre «Revisión y activación» del asistente (`ob/design/WizardVisualGateScreen.kt:193`, overlay `ob/design/ActivationOverlay.kt:141` «Activar y entrar a KPKN», `:390-391` «TODO LISTO» / «REVISA ANTES DE ACTIVAR», `:742-743` «Aún sin programa / Lo eliges cuando quieras desde Programas»). cr-listo lo sustituye en los cuatro caminos.
- **CR-S4.** Los pasos de Datos básicos (`NAME, AGE, HEIGHT, WEIGHT, EQUATION_SEX, BODY_FAT` + `MILESTONE_BASICS`) siguen en la ruta de solo entreno: `do/SetupStepGraph.kt:355-363` se añaden «siempre que haya entreno o nutrición». cr-hoy: en «Nuevo programa» este capítulo **no se vuelve a preguntar**; sexo y % de grasa no se piden si faltan.
- **CR-S5.** Volver a Inicio al terminar el asistente (`MainActivity.kt:1349-1362`, `onDone`). Decidido: vuelve a Programas con el programa nuevo abierto.
- **CR-S6.** Activación sin preguntar según el camino: Inicio `activate = pendingProgramName != null` (`ho/HomeViewModel.kt:84-95`, `ho/HomeScreen.kt:419, 435`), catálogo y desde cero van directos al detalle (`pg/ProgramsScreen.kt:323-332, 356-358`), TM (`:380-399`) e importar (`MainActivity.kt:1195-1204`).

### Falta o difiere
- **CR-F1.** cr-listo «Listo para empezar»: la cadena no existe en la app. Pantalla compuesta con piezas del asistente: cabecera, filas plegadas con check y lápiz y filas de opción con radio: **«Empezar ahora»** (pausa el actual), **«Ponerlo en cola»** (por defecto si hay uno en curso) y **«Guardarlo sin activar»**; sin programa en curso la opción por defecto es «Empezar ahora»; botón redondo de confirmar. Soporte de datos que ya existe: `repository.programQueue` (`pg/ProgramsViewModel.kt:138-144`), `repository.startProgram` (`ho/HomeViewModel.kt:92`).
- **CR-F2.** Instancia «Nuevo programa» del asistente (cr-wz01…cr-wz12): cabecera **«NUEVO PROGRAMA · n/m»** en vez de «ENTRENO» (el capítulo se rotula con `do/SetupStepDefinitions.kt:997-1003`, «Entreno») y X que sale y **guarda el borrador** (al volver a «A tu medida» retoma donde estaba). No hay ninguna instancia con nombre propio; el modo existente es `TRAINING_ONLY` (`navigation/PostDischargeRouting.kt:34-49`, `ob/SetupWizardModels.kt:78`).
- **CR-F3.** Fila plegada **«Lo que ya sabemos · Matías · 34 años · 1,78 m · 80,4 kg»** arriba, con el mismo check y lápiz que una respuesta confirmada (editable). La cadena no existe en la app. Solo hay un salto parcial: el paso de sexo se salta si ya se eligió (`ob/SetupWizardViewModel.kt:991-996`).
- **CR-F4.** cr-wz05 «¿compites?»: con «Sí, tengo fecha», además de armar el programa, **se crea la competencia como próxima** en Competencias. La app solo escribe la fecha clave del programa (`ob/SetupActivationPayload.kt:166-181`); no crea un `CompetitionRecord`.
- **CR-F5.** cr-cat3 «Configurar este plan» abre el asistente con el plan elegido y **solo pregunta lo que falta** (días, tiempo, material). Hoy lleva el `planId` al asistente de solo entreno (`MainActivity.kt:1853-1859`, `navigation/PostDischargeRouting.kt:46-49`) que sigue pidiendo Datos básicos y el resto de la ruta (CR-S4).
- **CR-F6.** cr-cat4 / cr-imp8 / cr-cero acaban en «Listo para empezar»: importar muestra «Listo: 1 semana · 2 días · 2 ejercicios» y pasa a ese cierre (hoy `onSaved` navega al detalle, `MainActivity.kt:1195-1203`); «Desde cero» → «Crear y abrir» pasa por él si hay uno en curso (hoy abre el detalle directo, `pg/ProgramsScreen.kt:352-358`); TM «Crear sin TM / Aplicar y crear» tampoco (`:375-399`).
- **CR-F7.** Días **1–7** en todos los sitios. Hoy el catálogo ofrece 1–6: `pg/CreateProgramTemplateSheet.kt:268` y `pg/ProgramCatalogPage.kt:227`; el asistente ya admite 1–7 (`do/EntrenoStepValues.kt:78`).
- **CR-F8.** cr-new: la hoja «Nuevo programa» se abre desde Inicio (CR-S1). La hoja en sí ya coincide con la captura (`pg/NewProgramSheets.kt:100-146`); los iconos de «A tu medida» e «Importar» se tratan en el otro inventario (P-F6, P-F7).

### Dudoso
- **CR-D1.** El contador «n/m» de la cabecera «NUEVO PROGRAMA · n/m». **¿Cuenta los pasos de la ruta efectiva (sin Datos básicos), o los de un capítulo?**
- **CR-D2.** Con el alta sin completar (`onboardingCompleted = false`) Inicio sigue abriendo el asistente completo (`MainActivity.kt:1026-1032`, `createProgramAndOpen`). **¿Se queda así o también abre «Nuevo programa»?**
- **CR-D3.** El lienzo cita «código muerto de calibración». En `ho/HomeScreen.kt` y `pg/ProgramsScreen.kt` no hay referencias a calibración (grep «alibr» sin resultados); lo único muerto que encuentro es CR-S2. **¿A qué código de calibración se refiere?**
- **CR-D4.** Enlace externo `kpkn://setup/wizard?mode=TRAINING_ONLY` va al editor directo (`navigation/DeepLinkRouter.kt:101`). **¿Se mantiene o pasa a «A tu medida» en la instancia nueva?**
- **CR-D5.** «Guardar el borrador al salir» del asistente (cr-wz01): el borrador de `TRAINING_ONLY` se guarda con `SetupDraftScope.TRAINING_ONLY` (`ob/SetupWizardViewModel.kt:3610`). **¿Se retoma siempre o se pregunta «¿Retomar?»?**

---

## 3. Registro de comida · Describir (`scenes-comida.js`: f-*)

La hoja del registro es `nc/FoodLoggerDrawer.kt` (1974 líneas) con piezas en `lg/`. Es una hoja de 0,92 de pantalla con `LazyColumn` y barra fija inferior (`nc/FoodLoggerDrawer.kt:1541-1545, 1838-1862`). El lienzo la dibuja como pantalla completa (`.lg`).

### Sobra
- **F-S1.** Icono (i) «Cómo funciona el registro de comidas» junto a la ayuda (`lg/LoggerDescribeField.kt:56-60`) y su diálogo «Registro privado, sin conexión» (`lg/LoggerPrivacyDialog.kt:29`, montado en `nc/FoodLoggerDrawer.kt:1396`). f-empty: solo la ayuda y el micrófono. Ver F-D3.
- **F-S2.** Acciones de texto «Plantillas», «Entrada rápida» (`nu/reuse/ReuseLoggerEntries.kt:29, 36`) y «Recetas» (`nc/FoodLoggerDrawer.kt:1572-1579`) bajo las pestañas, en las dos pestañas. Decidido: «Repetir» y atajos pasan a Buscar (s-start).
- **F-S3.** Bloque «Favoritos» apilado antes del campo, sin tope, con comidas favoritas y plantillas favoritas (`nc/FoodLoggerDrawer.kt:1581-1602`). f-empty: «Repetir» y «Favoritos» (máximo dos filas + «Ver todos») debajo del campo.
- **F-S4.** Tres puntos que respiran + mensajes rotatorios («Leyendo tu comida», «Estimando alimentos y cantidades», «Esto puede tardar un poco más la primera vez», «Preparando un resumen para que lo revises», «Convirtiendo la comida en un registro editable», «Comprobando porciones y cantidades», «Ajustando el resumen para que sea más consistente») y «Interpretando…» fijo con movimiento reducido (`lg/LoggerAnalysisPanel.kt:41-68, 85-100`). f-ana: plato de líneas con alimentos que caen y mensajes reales.
- **F-S5.** Textos del botón principal: «Interpretar comida» (`nc/FoodLoggerDrawer.kt:1421`), «Actualizar y buscar» (`domain/nutrition/FoodLoggerPrimaryAction.kt:19`, que no busca), «Interpretando…» / «Guardando…» (`nc/FoodLoggerDrawer.kt:1854-1855`). Decidido: un solo verbo «Analizar» / «Volver a analizar».
- **F-S6.** Módulo «Lo que comiste» con el subtítulo «Resumen de la comida» (`nc/FoodLoggerDrawer.kt:1811-1822`). f-result no tiene ese rótulo: va el anillo y luego las tarjetas.
- **F-S7.** Fila de acciones por alimento: «Corregir» (con icono de información si la coincidencia es dudosa), «Ajustar» / «Ocultar ajustes» y X «Quitar …» (`lg/LoggerFoodItem.kt:236-251`). f-result/f-edit: tocar la tarjeta abre su hoja con «Cambiar» y papelera.
- **F-S8.** Control Pequeño / Mediano / Grande / Extra dentro de cada fila (`lg/LoggerFoodItem.kt:166-173`, etiquetas `nc/FoodLoggerDrawer.kt:142-145`). Ver F-D1.
- **F-S9.** Deslizador de gramos + campo (`nc/GramsEditor.kt:126`) y bloque «Ajustar macros» con ±5 kcal y ±1 g (`lg/LoggerMacroSteppers.kt:36-37`). f-edit: −/+ en la unidad natural, «Huevos / Gramos / Raciones» y cuatro cifras tocables.
- **F-S10.** Líneas «Fibra / Azúcar / Sodio» (`lg/LoggerFoodItem.kt:215-222`), «Rango estimado: a–b kcal» (`:161`), «Excluido» (`:109`) y la línea de identidad «Arroz blanco cocido · aprox. 150 g» (`:111, 263-`). f-result: la tarjeta lleva solo «2 huevos · 110 g» (+ «· aprox.») y «≈ N kcal». Ver F-D2.
- **F-S11.** Guardar bloqueado por dudas: `saveBlocked` (`nc/FoodLoggerDrawer.kt:1401-1404`), «Resuelve la duda pendiente o acepta la estimación antes de guardar.» (`:1300`), «Revisa la comida antes de guardar: hay datos incompletos o fuera de rango.» (`:1648`) y «Usar lo estimado en todo (N)» (`:1655`). Decidido: se guarda con la estimación por defecto de cada duda.
- **F-S12.** Textos técnicos de error: «Dataset de porciones no cargado» (`nc/FoodLoggerDrawer.kt:444-445`), «Error inesperado durante el análisis» (`:466-467`), nombre de la excepción (`:153`), «No se pudo leer la comida completa» / «Preparamos un borrador manual» (`:677-694`) y «Ver detalle / Ocultar detalle» (`lg/LoggerAnalysisNotice.kt:53`). Decidido: sin jerga; el detalle técnico solo en registros.
- **F-S13.** «Olvidar lo aprendido» vacía la comida en curso (`tags = emptyList()`) y no pregunta (`nc/FoodLoggerDrawer.kt:1358-1365`), y deja el texto «Memoria de aprendizaje borrada» (`:1663-1668`). Ver S-F9.
- **F-S14.** Campo de texto de hora «HH:mm» y «Usar esta hora» (`lg/LoggerDateSheet.kt:54-57`); elegir un día cierra la hoja y reinicia la hora (`nc/FoodLoggerDrawer.kt:1461-1465`). s-date: atajos, calendario, rueda y «Listo».
- **F-S15.** Corrección inline que se abre sola para alimentos sin resolver (`nc/FoodLoggerDrawer.kt:1476`, `lg/LoggerFoodCorrection.kt:38` «Cambiar coincidencia»). f-identity: lista corta de candidatos en una hoja.
- **F-S16.** Detalle de coincidencia en texto: «Coincidencia aproximada: X. Cámbiala si no es lo que comiste.» (`nc/FoodClarificationPrompt.kt:44`). f-identity: candidatos con ración, kcal y «Muy probable» / «Posible».

### Falta o difiere
- **F-F1.** f-empty, cabecera `lg-head`: botón X a la izquierda (relleno), centro con **título «Registrar comida»** y debajo icono calendario + «Hoy · 08:40», botón de ajustes (sliders) a la derecha. La app: título a todo el ancho, debajo píldora de fecha («Hoy · lun 13 oct · 08:40») y los dos botones a la derecha (`lg/LoggerHeader.kt:58-67`, `nc/FoodLoggerDrawer.kt:1559`).
- **F-F2.** Comida con **chips** Desayuno / Almuerzo / Merienda / Cena (altura 34) bajo la cabecera; la app usa `KpknSegmentedControl` compacto (`lg/LoggerHeader.kt:70-76`).
- **F-F3.** Control «Describir / Buscar» con iconos lápiz y lupa (`seg2`); la app no lleva iconos (`lg/LoggerTabs.kt:19`).
- **F-F4.** Campo: recuadro grande `.write` con el cursor, placeholder **«¿Qué comiste? Escríbelo como lo contarías»**, pie «Cantidades, marcas y cómo lo cocinaste» y **botón de micrófono** «Dictar». La app: `KpknTextField` con etiqueta «¿Qué comiste?» y placeholder «Dos huevos con tostadas y un café» (`lg/LoggerDescribeField.kt:40-41`), ayuda «Escribe como hablas: cantidades, marcas y cómo lo cocinaste» (`:50`), sin micrófono. El reconocedor ya existe (`data/voice/VoiceNutritionRecognizer.kt`, usado solo en `screens/workout/WorkoutVoiceInput.kt:104`).
- **F-F5.** «Repetir» (f-empty): tres tarjetas horizontales (`hcard`) con lo que sueles registrar a esa hora y las comidas recientes («Avena con plátano · Desayuno de ayer · 412 kcal»…). No existe (la app solo tiene «Repetir ayer» en el día, `nu/reuse/ReuseDayActions.kt`).
- **F-F6.** «Favoritos» con tope de dos filas, estrella llena en color energía y «Ver todos». La app los apila sin tope (F-S3).
- **F-F7.** f-typed: pie «Enter para analizar» y botón «Analizar» con icono spark **dentro del campo**. La app tiene el botón en la barra inferior (`lg/LoggerSaveBar.kt:80`, `nc/FoodLoggerDrawer.kt:1412-1432`).
- **F-F8.** f-ana: el texto de la comida arriba con las palabras que se subrayan al reconocerse y pasan a verde («Dos huevos revueltos», «dos tostadas»…), plato de líneas con cuatro alimentos que flotan (huevo, pan, aguacate, taza) y mensaje «Encontré huevos y pan / Estimando cantidades…». Con movimiento reducido: texto subrayado y un solo mensaje.
- **F-F9.** f-result: texto de la comida en gris reducido; barra de aviso **«2 dudas: tócalas para afinar o guarda con la estimación»** (icono interrogación); **anillo de calorías** con los tres macros como arcos (`sumCard`: «Proteína 25 g · Carbos 46 g · Grasa 30 g») que cuenta al aparecer; módulo de vitaminas y minerales (M-F10); tarjeta por alimento con icono de línea (huevo, pan, aguacate, taza), nombre, «cantidad · gramos», «≈ N kcal» y barra de macros; «Añadir otro alimento». La app no tiene anillo; el total va en la barra inferior (`lg/LoggerSaveBar.kt:63-72`) y la fila no lleva icono ni barra (`lg/LoggerFoodItem.kt:105-123`).
- **F-F10.** Barra de guardado del lienzo (`savebar`): «548 kcal» + «4 alimentos · 25 g de proteína» (o «Resuelve 2 dudas o sigue con la estimación») y botón **«Guardar en desayuno»** (minúscula). La app: kcal en `metricL` + línea de macros + píldora a todo el ancho «Guardar en Desayuno» (`lg/LoggerSaveBar.kt:63-86`, `nc/FoodLoggerDrawer.kt:1422`).
- **F-F11.** f-edit «Ajustar un alimento»: hoja con icono 48, nombre, «Coincidencia alta · base KPKN», acción «Cambiar»; «Cantidad» con −/+ y la unidad natural («2 huevos»); chips de unidad «Huevos / Gramos / Raciones»; «Cómo lo cocinaste» con tres símbolos («Con aceite», «Sin grasa», «Con mantequilla»); «Aporta» en cuatro cifras (182 kcal · 13 g prot. · 2 g carb. · 14 g grasa) editables al tocarlas + «Toca una cifra para corregirla a mano.»; papelera roja + «Listo». La app ajusta **dentro de la fila** (`lg/LoggerFoodItem.kt:200-224`), sin hoja.
- **F-F12.** f-identity: hoja «Escribiste «tostadas» / ¿Cuál se parece más?» con cuatro candidatos (radio, nombre, «2 rebanadas · 158 kcal», chip «Muy probable» / «Posible»), «Buscar otro» y «Usar este». La app resuelve con chips en la tarjeta (`lg/LoggerFoodClarification.kt:63-80`, título «¿Es este el alimento?», `nc/FoodClarificationPrompt.kt:24-27`).
- **F-F13.** f-clar «Todas las dudas»: respuestas **marcadas** una vez elegidas y la tarjeta pierde el tono cálido. La app pinta siempre `selected = false` (`lg/LoggerFoodClarification.kt:63, 68, 85-91`). Textos que difieren:
  - aceite: lienzo «¿Las tostaste con aceite o mantequilla?» con «Nada / Un poco / Bastante» y «Seguir con «nada»»; verduras «¿Cuánto aceite tenía?» con **gotas** (Nada, Poco, Medio, Mucho). App: «¿Cuánto aceite tenía?» con chips de texto (`nc/FoodClarificationPrompt.kt:31`, `lg/LoggerFoodClarification.kt:96-103`).
  - leche: «¿Qué leche llevaba?» (Entera / Semidesnatada / Desnatada / Vegetal): sin pregunta equivalente en `nc/FoodClarificationPrompt.kt` (solo chips de variante N50, `lg/LoggerFoodItem.kt:132-150`).
  - tortilla: lienzo «De papas / De huevo / Trigo (wrap) / De maíz»; app «De maíz / De huevo / De papas» (`lg/LoggerFoodClarification.kt:21`), sin «Trigo (wrap)».
  - envase: lienzo «¿Comiste todo el envase?» con «Todo (180 g) / Una porción (30 g)»; app «¿Consumiste todo el envase o una porción?» (`nc/FoodClarificationPrompt.kt:33`).
  - corte: lienzo «¿Qué corte era?»; app «¿Qué corte comiste?» (`:23`).
  - crudo/cocido: lienzo «En crudo / Cocida»; app «Crudo / Cocido» o «Seco / Cocido» (`lg/LoggerFoodClarification.kt:85-91`).
  - «Seguir con esta estimación» (app, `nc/FoodClarificationPrompt.kt:59`) vs «Seguir con «nada»» (lienzo, respuesta por defecto concreta de cada duda).
  - «Cantidad declarada» (no pudimos leer tu medida) abre directo el ajuste de cantidad: la app muestra «Revisar cantidad» como chip (`lg/LoggerFoodClarification.kt:63`).
- **F-F14.** f-saved «Guardado»: check verde de 140 que se dibuja, **«Desayuno guardado»** y «548 kcal · te quedan 1.752 para hoy»; se cierra solo a los 0,8 s. La app convierte la píldora de guardar en una píldora con check y «Comida guardada» (`lg/LoggerSuccessCheck.kt:30-63`, `HoldMs = 600`, `lg/LoggerSaveBar.kt:78`); no dice cuánto queda.
- **F-F15.** f-error: módulo con borde energía, icono, título **«Lo leí a medias»**, «Reconocí las lentejas y el chorizo, pero no las cantidades. Las dejé en una ración normal: revísalas antes de guardar.» y dos píldoras «Volver a analizar» / «Buscar a mano»; debajo las tarjetas con «aprox.». La app: módulo `danger` con «Reintentar» / «Ver detalle» (`lg/LoggerAnalysisNotice.kt:38-57`) y errores de guardado con X (`lg/LoggerErrorNotice.kt:34-49`).
- **F-F16.** f-home «Inicio · escribir sin salir»: barra de vidrio flotante **«¿Qué comiste? Escríbelo aquí»** + micrófono encima de la barra de navegación (solo con plan de nutrición); la comida se elige por la hora. La app: botón «+» en la cabecera del módulo «Nutrición de hoy» (`ho/design/HomeNutritionToday.kt:124-130`) y píldora «Registrar comida» (`ho/HomeScreen.kt:1396`) que abren la hoja completa (`ho/HomeScreen.kt:402-413`, `HomeFoodLoggerHost` en `:512`).
- **F-F17.** f-home-type: el texto crece en la barra (hasta 3 líneas) y aparece «Analizar». f-home-ana: media hoja «Almuerzo · 14:10» con la misma animación. f-home-res: media hoja (82 %) con «3 alimentos · 1 duda», «Abrir completo ›», tarjetas compactas con su duda, «+» y «Guardar · 652 kcal». f-home-done: la hoja baja, el anillo de Inicio cuenta con rebote y aviso «Almuerzo guardado · 652 kcal» con «Deshacer» 4 s. Nada de esto existe.

### Dudoso
- **F-D1.** Pequeño / Mediano / Grande / Extra: en el lienzo existe el bloque `sizes` en `foodCard(open)` pero ninguna escena lo abre. **¿Desaparece el tamaño y queda solo el −/+ de la unidad natural (f-edit)?**
- **F-D2.** «Excluir» un alimento (`lg/LoggerFoodItem.kt:109`, `tag.isExcluded`) y las **chips de variante** N50 (`lg/LoggerFoodItem.kt:132-150`, ola Nutrición v2) no aparecen en el lienzo. **¿Se mantienen dentro de la hoja de ajuste o se eliminan?**
- **F-D3.** El diálogo de privacidad (F-S1) lleva la promesa «Todo se calcula en tu teléfono…». **¿Se conserva en algún sitio (p. ej. al pie de f-error o en Ajustes del registro) o se elimina?**
- **F-D4.** f-error: las notas piden «icono de aviso (no de información)» pero el lienzo dibuja `info`. **¿Qué icono?**
- **F-D5.** Registrador en Inicio: **¿el «+» del módulo y la píldora «Registrar comida» de Inicio se sustituyen por la barra «¿Qué comiste?», o conviven?** Y: sin plan de nutrición la barra no sale, **¿qué ve esa persona?**
- **F-D6.** Dictado: el micrófono necesita `RECORD_AUDIO` y el reconocedor es del sistema. **¿Se acepta el reconocimiento del sistema (no «local y sin conexión» garantizado) o solo on-device?**

---

## 4. Registro de comida · Buscar y menús (`scenes-busqueda.js`: s-*)

### Sobra
- **S-S1.** Botón «Buscar» de la barra inferior y del teclado: la búsqueda se lanza con la acción IME o el botón (`nc/FoodLoggerDrawer.kt:1265, 1438, 1685`; `lg/LoggerSearchField.kt:29`; etiqueta «Buscar» en `domain/nutrition/FoodLoggerPrimaryAction.kt:21`). s-loading: busca al escribir con 250 ms de espera.
- **S-S2.** «Crear alimento propio» como acción fija bajo el campo (`nc/FoodLoggerDrawer.kt:1695-1705`). En el lienzo solo aparece sin resultados (s-none, «Crear alimento»). Ver S-D2.
- **S-S3.** Línea extra bajo cada resultado con la estrella y «Editar alimento propio» / «Editar receta» (`nc/FoodLoggerDrawer.kt:1771-1790`, `nu/customfood/FoodFavoriteAction.kt`). s-results: la estrella va en la misma fila, sin acción de edición. Ver S-D1.
- **S-S4.** Texto técnico de coincidencia «Coincidencia media» / «Coincidencia baja» (`lg/LoggerSearchRow.kt:117-126`). Decidido: se quita.
- **S-S5.** «por 100 g» bajo las kcal (`lg/LoggerSearchRow.kt:97`). s-results: las kcal son las de la **ración natural** que se muestra («125 g · 1 envase» → 146).
- **S-S6.** «Favoritos» y las acciones «Plantillas / Entrada rápida / Recetas» siguen visibles también en la pestaña Buscar (`nc/FoodLoggerDrawer.kt:1572-1602`): están fuera del `if (activeTab…)`. s-start las convierte en **Atajos** (cuadrícula 2×2).
- **S-S7.** Avance «Preparando catálogo… N %» + «Los resultados salen de lo que ya hay.» (`lg/LoggerCatalogProgress.kt:38-43`). El lienzo no lo dibuja; usa filas de carga (s-loading). Ver S-D3.
- **S-S8.** Búsqueda sin resultados con una sola salida «Describirlo en lugar de buscar» (`lg/LoggerSearchEmpty.kt:33-39`). s-none: dos salidas, dibujo de líneas y título.
- **S-S9.** «Agregar» en todo el registro: `lg/LoggerSearchRow.kt:69`, `nu/reuse/QuickEntryScreen.kt:55, 152, 196`, `nu/scan/BarcodeScanScreens.kt:359`, `nu/recipes/RecipeLibraryOverlay.kt:149`, y el placeholder «Busca un alimento para agregarlo» (`lg/LoggerSearchField.kt:26`). Decidido: siempre «Añadir».
- **S-S10.** Diálogo «Configuración» como `KpknAlertDialog` con dos filas (`lg/LoggerSettingsDialog.kt:34-60`). s-settings: hoja «Ajustes del registro».
- **S-S11.** Entrada rápida a pantalla completa con módulos «Energía», «Macros (opcionales)», «Comida» y el texto «Para cuando ya sabes las kcal y no quieres buscar un alimento. Cuenta en los totales del día; sus micronutrientes quedan sin dato.» (`nu/reuse/QuickEntryScreen.kt:80-132`). s-quick: hoja corta.

### Falta o difiere
- **S-F1.** s-start «Buscar · al abrir»: campo de búsqueda con lupa y **código de barras dentro del campo** (la app: campo y `KpknIconButton` aparte, `nc/FoodLoggerDrawer.kt:1676-1692`); `group-l` **«Recientes»** (tres filas: «Avena en copos / 40 g · desayuno de ayer / 150», «Plátano», «Leche semidesnatada»; no existe, no hay recientes); `group-l` **«Atajos»** con cuatro botones de 92 dp: «Escanear · Código de barras», «Entrada rápida · Solo calorías y macros», «Recetas · Tus 4 recetas», «Plantillas · Comidas guardadas».
- **S-F2.** s-loading: cinco filas esqueleto mientras busca. La app no tiene estado de carga ni de error de red/lectura.
- **S-F3.** s-results: fila `sres` = **nombre · «125 g · 1 envase · KPKN»** (origen) · kcal en Syne 14 · estrella (llena en color energía si es favorito) · «+» (la app: dos líneas nombre/marca, kcal en `metricM` con «kcal» debajo, «+» y la estrella en otra línea; `lg/LoggerSearchRow.kt:81-104`). Filtros `Todo / Míos / Con marca / Sin marca` bajo el campo (no existen). «+» añade con la ración mostrada y avisa «Yogur griego natural añadido» con **«Deshacer»**; tocar la fila abre la **ficha** (hoy tocar la fila añade, `lg/LoggerSearchRow.kt:60-70`).
- **S-F4.** s-detail «Ficha de un alimento» (hoja): no existe (se añade directo). Hace falta: eyebrow «KPKN · verificado», nombre, «Cuánto» con −/+ y unidad («1 envase · 125 g»), chips «Envases / Gramos / Cucharadas», anillo de macros, huella (M-F2), favorito (estrella) y **«Añadir al desayuno»**.
- **S-F5.** s-none: dibujo de líneas, **«No encontré «arepa de choclo»»** (Syne 20), «Descríbela y la estimamos, o créala con sus datos.», píldoras «Describirla» (pasa el texto a Describir y lo analiza) y «Crear alimento» (con el nombre ya puesto). Hoy la entrada «Crear alimento propio» está duplicada en la búsqueda y en el escáner con textos distintos (`nc/FoodLoggerDrawer.kt:1697`, `nu/scan/BarcodeScanScreens.kt:266, 368`).
- **S-F6.** s-scan: el resultado **sube desde abajo sin salir de la cámara**: eyebrow «Encontrado · base global», nombre, «Nature Valley · 42 g · 191 kcal», botones «Escanear otro» / «Añadir»; arriba cerrar y linterna; «Centra el código en el recuadro» a 200 px. La app cambia de fase con módulos propios: «Base global» (`nu/scan/BarcodeScanScreens.kt:310`), «Código N» (`:325`), «Calidad del dato» con viñetas (`:330-337`), «Ración» (`:346-352`), «Agregar a la comida» (`:359`), «Probar otro» (`:271, 373`). «Escribe el código» queda en la hoja si no lee (`:157, 198, 224-237`).
- **S-F7.** s-quick «Entrada rápida» como hoja: título, «Para cuando solo sabes las calorías.», stepper **450 kcal** con −/+ de 10, «Macros (opcional)» en tres campos (Proteína 30 g, Carbos —, Grasa —), «Nombre (opcional) / Menú del día», **«Añadir a la comida»**.
- **S-F8.** s-settings «Ajustes del registro» (hoja): tres filas con icono y subtítulo: «Mis medidas de casa / Cuánto es tu taza, tu plato o tu cucharón», **«Favoritos y recetas / Ordenar y editar»**, «Olvidar lo aprendido / Las porciones y elecciones que KPKN recuerda de ti» (rojo). La app: «Configuración» con «Ajustar medidas / Personaliza el volumen en ml de tazas y platos» y «Olvidar lo aprendido / Borra las elecciones y porciones recordadas» (`lg/LoggerSettingsDialog.kt:44-52`); falta «Favoritos y recetas».
- **S-F9.** s-forget: confirmación «¿Olvidar lo aprendido?» con «KPKN volverá a preguntarte cómo cocinas, qué leche usas y tus porciones habituales. Tus comidas registradas no cambian.» y «Cancelar» / «Olvidar». No existe (`nc/FoodLoggerDrawer.kt:1358`).
- **S-F10.** s-measures «Mis medidas de casa»: título con «Mis»; subtítulo «Cuando escribas «una taza» o «un plato», usaremos estas cantidades.»; orden **Taza 240 ml, Vaso 250 ml, Plato 300 g, Plato hondo 350 ml, Bol 400 ml, Cucharada 15 ml, Cucharadita 5 ml, Cucharón 120 ml, Copa 150 ml**, cada una un chip que se toca para escribir; «Restablecer» y «Guardar» como píldoras en una fila. La app: «Medidas de casa» + «Cuánto cabe en cada utensilio de tu casa…» (`lg/LoggerMeasuresDialog.kt:72-74`), orden cucharadita, cucharada, cucharón, taza, vaso, plato, plato hondo, bol, copa (`:43-51`), todas en ml con ± (`:131`), «Restablecer» como texto y «Cancelar / Guardar» como diálogo (`:94-102`). Ver S-D4 (Plato en gramos).
- **S-F11.** s-date «¿Cuándo fue?»: chips «Ahora / Hace 1 h / Hoy al mediodía / Ayer»; calendario sin días futuros (la app ya limita con `maxDate`, `lg/LoggerDateSheet.kt`); **rueda** de hora «08 : 40»; «Listo». La app: título «Fecha de la comida», campo «Hora (HH:mm)», «Usar esta hora» (`lg/LoggerDateSheet.kt:47-57`).
- **S-F12.** s-rowmenu «⋯ de una comida registrada»: hoja con título «Desayuno», «08:40 · 548 kcal · 4 alimentos» y seis filas con icono y subtítulo: «Editar», «Duplicar / En la misma comida», «Copiar a… / Hoy, mañana o los próximos días», «Mover a… / Otra comida u otro día», «Guardar como plantilla / Para repetirla con un toque», «Eliminar / Se puede deshacer» (rojo). La app: menú contextual anclado a la pulsación larga, sin «Editar», sin iconos ni subtítulos, y «Mover a…» despliega las comidas en la misma lista (`nu/design/NutritionFoodMenu.kt:32-66`, uso en `nu/NutritionScreen.kt:389, 485`).
- **S-F13.** s-discard: el texto del lienzo es **«Tienes 4 alimentos sin guardar.»**; la app: «El borrador y sus correcciones no se guardarán ni se usarán para aprender hábitos.» (`lg/LoggerDiscardDialog.kt:39`). Títulos y botones coinciden («¿Descartar esta comida?», «Seguir editando», «Descartar»). El lienzo dice que hoy usa la piel de Material: la app ya usa `KpknAlertDialog` (`lg/LoggerDiscardDialog.kt`); solo cambia la frase.

### Dudoso
- **S-D1.** Editar un alimento propio o una receta (hoy «Editar alimento propio» / «Editar receta» en la fila de resultado, `nc/FoodLoggerDrawer.kt:1771-1790`): s-results, s-detail y s-settings no tienen la acción. **¿Dónde se edita (⋯ de la ficha, filtro «Míos» → fila tocable, «Favoritos y recetas» en Ajustes)?**
- **S-D2.** «Crear alimento propio» como acción permanente: **¿solo en s-none y en la ficha de código no encontrado, o también un atajo?** (los Atajos del lienzo son cuatro y no lo incluyen).
- **S-D3.** Importación del catálogo en curso: **¿se muestra el porcentaje o solo las filas esqueleto?** Hoy los resultados parciales llevan aviso (`lg/LoggerCatalogProgress.kt:38-43`).
- **S-D4.** s-measures pone «Plato 300 g» en gramos; la app mide platos en ml (`lg/LoggerMeasuresDialog.kt:131`, `UTENSIL_DEFAULTS`). **¿Es un error de cifra del lienzo o los platos pasan a gramos?**
- **S-D5.** Filtros «Míos / Con marca / Sin marca»: **¿filtran la lista que ya devuelve `searchLoggerCandidates` o la consulta cambia?** (`nc/FoodLoggerDrawer.kt:1265-1281`, límite 20).
- **S-D6.** El dato de origen de la fila («KPKN», «Hacendado», «Receta», «Oikos»): **¿de qué campo sale** (`food.source` o `brand`)?

---

## 5. Vitaminas y minerales (`scenes-micro.js`: n-*)

Hoy solo existe la hoja «Nutrientes del día» (`nu/nutrients/NutrientsSheet.kt`), abierta desde una acción de texto en Nutrición (`nu/NutritionScreen.kt:319, 468`), con un `KpknModule` de 6–9 líneas de texto por nutriente. **No hay huella en ningún sitio** (ni ficha de alimento, ni registro, ni hoja de ajuste, ni día).
Excepción del usuario: la huella del día vale solo para su elemento; el resto de `n-day` (anillo, filas de comida) no manda (M-D1).

### Sobra
- **M-S1.** Seis a nueve líneas de texto por nutriente (`nu/nutrients/NutrientsSheet.kt:86-139`): estado «Completo / Casi completo / Parcial / Sin datos» (`:79-84, 96`), «N de M alimentos con dato declarado» (`:98`), «N con datos estimados» (`:100`), «Cobertura: X % de las kcal / por alimentos (sin kcal)» (`:101`), «Sin dato en el catálogo; una ficha propia puede aportarlo.» (`:103`), «N sin dato comparable (puede ser 0)» (`:104`), línea de referencia y barra `LinearProgressIndicator` (`:105-112`), límite superior por alcance (`:114-124`), «Tope de la guía…» (`:127-137`). n-day-sheet: **una fila por nutriente** (nombre · cantidad · % · barra fina) y el detalle va a la hoja del nutriente.
- **M-S2.** Despliegue de contribuyentes dentro de la fila: «Ver contribuyentes / Ocultar contribuyentes» (`nu/nutrients/NutrientsSheet.kt:135`) y lista de texto «{alimento}: N mg · estimado». n-nutrient: hoja propia por nutriente.
- **M-S3.** Cada nutriente en su propio `KpknModule` de ancho completo, con 12 dp de separación (`nu/nutrients/NutrientsSheet.kt:92, 59`). n-ficha-vit: filas sin caja, separadas por filetes.
- **M-S4.** Texto introductorio «El total solo cuenta los alimentos con dato; puede ser mayor.» (`nu/nutrients/NutrientsSheet.kt:56`) y «Referencia adulta según la ecuación … declarada en el plan · N años.» (`:57-59`). n-day-sheet: subtítulo «Miércoles 28 · referencia de hombre, 30 años (DRI), según tu plan» y una sola línea de cobertura «86 % de las kcal con dato declarado · el total puede ser mayor».
- **M-S5.** «Calculando nutrientes…» (`nu/nutrients/NutrientsSheet.kt:60`) y botón X «Cerrar nutrientes» (`:50-51`). n-day-sheet: hoja con título y asa, sin X.
- **M-S6.** Acción de texto «Nutrientes del día» en Nutrición (`nu/NutritionScreen.kt:318-321`): la sustituye el módulo «Vitaminas y minerales» con «Ver todos ›» (n-day).
- **M-S7.** Pie de leyendas: «RDA: ingesta recomendada; AI: ingesta adecuada…» (`nu/nutrients/NutrientsSheet.kt:70-71`) y «Datos de alimentos: USDA… Open Food Facts (ODbL)…» (`:72-73`). n-day-sheet no los dibuja. Ver M-D3.

### Falta o difiere
- **M-F1.** La **huella** (componente nuevo, `huella()` en `scenes-micro.js`): barra fina por nutriente hasta el 100 % de la referencia (línea punteada con rótulo «100 %»); **24 nutrientes** en tres grupos (12 vitaminas: A, B1, B2, B3, B6, B9, B12, colina, C, D, E, K · 9 minerales: Ca, P, Mg, Fe, Zn, Se, I, Na, K · 3 «otros»: fibra, omega-3 ALA, cafeína); las **tres que más aportan en color músculo con su sigla**; el resto en tinta tenue; **topes** (sodio, cafeína) como contorno, en rojo si se pasan; **«sin dato»** como trazo punteado a ras de suelo, nunca una barra de 0; parte **estimada** en contorno discontinuo; punto sobre la línea si pasa del 100 %; leyenda «VITAMINAS · MINERALES · OTROS»; las barras crecen de izquierda a derecha al abrir (24 ms entre barras). Tamaños: 330×84 (ficha), 330×78 (comida), 330×74 (ajuste), huella del día con `day:true` y sin destacados. Hay `NutrientKey`/`NutrientGroup` en `domain/nutrition/NutrientGroups.kt` pero no gráfico.
- **M-F2.** n-ficha / s-detail: bajo las kcal y macros, «Vitaminas y minerales» + «% de tu día», huella, línea **«Destaca en: Vitamina B12 175 % · Selenio 112 % · Vitamina D 100 %»** (y «N % del máx.» para topes ≥ 25 %), y línea «**37 de 38 nutrientes con dato**» con «Ver todos ›». Se mueve con −/+. Origen (`USDA · verificado`) siempre a la vista. Sin perfil adulto compatible: cantidades relativas, sin inventar un 100 %.
- **M-F3.** n-ficha-vit / n-ficha-min / n-ficha-fat: lista completa de **una ración** (no del día): cabecera con flecha atrás, nombre, «1 filete · 150 g · % de tu día»; chips de grupo en una fila deslizable **«Vitaminas · Minerales · Grasas · Carbos · Otros»**; fila `nrow` = nombre, cantidad, %, barra fina con marca al 100 %; «sin dato» sin barra; con tope «% del máx.»; «≥» si es parcial; nota de la niacina, «La ficha no trae yodo. No cuenta como 0: tus totales lo dirán.», «Tope diario: 2.300 mg (CDRR)…», «Tope de la guía: 10 % de la energía (25,6 g con 2.300 kcal) · OMS»; sin referencia: solo el total, sin barra («Sin referencia de ingesta: se muestra el total.»). El orden de los grupos de la app es **Carbohidratos, Grasas, Vitaminas, Minerales, Otros** (`domain/nutrition/NutrientGroups.kt:5-19`), no el del lienzo; el lienzo dice que es «el mismo orden que la hoja de la app». Ver M-D4.
- **M-F4.** n-ficha-label «Solo etiqueta»: producto con 8 de 38 nutrientes; huella casi vacía (solo fibra y sodio con barra, el resto punteado), aviso con icono «La etiqueta declara 8 de 38 nutrientes… El resto queda **sin dato** y no cuenta como 0 en tu día.» y acción «Completar con una ficha propia».
- **M-F5.** n-reg: bajo el anillo de la comida, módulo «Vitaminas y minerales · % de tu día» con la huella de la comida entera, línea «Esta comida: Selenio · Riboflavina · B12 …», y cobertura «● ● ● ◌ 3 con ficha completa · el café con leche va estimado ›» (un punto por alimento, discontinuo si es estimado). Se recalcula al responder una duda. Tocar el módulo abre la lista de la comida. Hoy el registro no calcula nutrientes de la comida.
- **M-F6.** n-reg-list «La comida en detalle»: hoja «Vitaminas y minerales / Desayuno · 4 alimentos · % de tu día», puntos de cobertura, chips de grupo y filas; si falta dato, total con «≥» y «N alimentos sin dato: puede ser más»; parte estimada rayada dentro de la barra; tocar una fila abre de qué alimento sale cada cantidad.
- **M-F7.** n-edit: la hoja de ajuste suma la huella de esa cantidad bajo «Aporta» (`microHead` + huella 74 + «Destaca en»). La app no tiene hoja de ajuste (F-F11).
- **M-F8.** n-saved: bajo «548 kcal · te quedan 1.752 para hoy», una línea «Lo que más sumó: selenio, riboflavina y B12» (sin porcentajes). Ver F-F14.
- **M-F9.** n-home: en la media hoja de Inicio, línea **«Esta comida: Niacina 151 % · Vitamina B6 112 % · Selenio 89 %»** (sin huella). Ver F-F17.
- **M-F10.** n-day (solo el módulo): `mod` con cabecera «Vitaminas y minerales» y **«Ver todos ›»**, huella del día (`day:true`: lo que llega al 100 % en tinta plena, lo que falta en tenue, sodio pasado en rojo) y **cuatro cifras «al día · cerca (70–99 %) · por debajo · sobre el máx.»** y la línea «Cobertura: 86 % de las kcal con dato · 2 alimentos sin ficha». La app tiene la acción de texto (M-S6) y `NutrientCoverage` ya calcula cobertura (`domain/nutrition/NutrientCoverage.kt`).
- **M-F11.** n-day-sheet «Nutrientes del día»: subtítulo con fecha y referencia; **«A vigilar»** arriba (sodio con «Máximo recomendado: 2.300 mg (CDRR) · AI: 1.500 mg»); chips de grupo; filas; lo parcial con «≥» y «N alimentos sin dato». La app agrupa por secciones de texto sin chips ni «A vigilar» (`nu/nutrients/NutrientsSheet.kt:63-68`).
- **M-F12.** n-nutrient «Un nutriente · Hierro»: hoja con eyebrow «Mineral · hoy», nombre, cifra 44 px («11,4 mg de 8 mg · 142 %»), barra de 8 dp, «De dónde sale» (los cinco mayores con barras relativas + «Otros alimentos (3)»), aviso «El batido de proteína no trae dato de hierro: el total puede ser mayor.», «Referencia» (RDA 8 mg, Límite superior total 45 mg) y la nota «Academias Nacionales de EE. UU. No sustituye el consejo de un profesional.» No existe: los contribuyentes van desplegados en la fila (M-S2). Los datos salen de `NutrientCoverage` (no hay números nuevos).

### Dudoso
- **M-D1.** n-day dibuja también el anillo de calorías del día (`dayRing`) y la lista «Desayuno / Almuerzo»; por la excepción del usuario **solo manda el módulo «Vitaminas y minerales»**. **¿Dónde va el módulo en la pantalla de Nutrición actual: justo bajo `NutritionDayModule` (donde hoy está la acción de texto, `nu/NutritionScreen.kt:318-321`)?**
- **M-D2.** La huella dibuja 24 nutrientes; la app maneja más claves (azúcares totales y añadidos, grasas saturada/mono/poli/trans, EPA+DHA, colesterol, alcohol; `domain/nutrition/NutrientGroups.kt:9-18`). **¿La huella se queda con las 24 del lienzo y el resto solo en las listas?**
- **M-D3.** Fuentes y licencias (USDA, ANSES-Ciqual, OFF) y el aviso «No sustituye el consejo de un profesional»: la nota de n-day-sheet dice que se conservan, pero la escena no las dibuja (solo n-nutrient lleva el aviso). **¿Dónde se ponen en n-day-sheet?**
- **M-D4.** Orden de grupos de las listas: las notas dicen «mismo orden que la hoja de la app» pero el lienzo pone Vitaminas · Minerales · Grasas · Carbos · Otros y la app Carbohidratos · Grasas · Vitaminas · Minerales · Otros (`domain/nutrition/NutrientGroups.kt:5`). **¿Manda el orden del lienzo?**
- **M-D5.** Referencia «hombre, 30 años» del lienzo es de muestra; la app la toma del plan (`NutritionViewModel.kt:239`, `DriTargets.profileFor`). Sin plan adulto compatible la hoja dice «Sin referencia DRI…» (`nu/nutrients/NutrientsSheet.kt:57-59`); n-ficha pide huella en cantidades relativas. **¿Qué texto exacto lleva ese caso en la ficha?**

---

## 6. Historias (`scenes-historias.js`: h-*)

En la app solo existen historias de **programa** (`st/ProgramShareSheet.kt`, `st/ProgramStory.kt`, `st/ProgramStoryBuilder.kt`), **mes** (`st/MonthShareSheet.kt`, `st/MonthStory.kt`) y **entreno** (`st/WorkoutStory.kt`), con piezas comunes en `st/StoryCanvas.kt` (lienzo 1080×1920) y salidas en `st/StoryShare.kt`. **No hay historias de competencia, cuerpo ni nutrición, ni exportación de vídeo** (en `st/` no hay `MediaCodec`/`MediaMuxer`). El marco ya aprobado se reutiliza.

### Sobra
- Nada que eliminar en historias nuevas (no existen). Los cambios sobre la hoja de programa se tratan en H-D1.

### Falta o difiere
- **H-F1.** h-meet-sheet «Compartir tu competencia» (hoja): título; vista previa de **176 px**; control de plantillas **Total · Intentos · Foto · Próxima**; «Incluir» con chips marcados (**Medalla, Puntos** activos · Peso corporal apagado); botones **«Instagram»** (primario, icono compartir) + **«Vídeo»** (secundario) y **«Guardar · Más apps»**. Se abre desde la celebración, la ficha, el ⋯ y la tarjeta «Próxima».
- **H-F2.** h-meet-total «Total»: medalla con halo de rayos finos (24 rayos), eyebrow «Copa de otoño · 21 sep», título «552,5 kg», sub «Oro · -83 kg Open · 79,4 IPF GL», tres levantamientos (Sentadilla 200 · Press banca 132,5 · Peso muerto 217,5), insignia «Mejor total: +12,5 kg», pie «KPKN · Mi competencia»; portada «Relieve / carbón».
- **H-F3.** h-meet-score «Intentos»: «8 de 9», sub «Válidos · total 552,5 kg», marcador de nueve intentos (válidos en tinta llena, nulos tachados con ✗), portada «Ondas / carbón».
- **H-F4.** h-meet-photo «Foto»: tu foto de la competencia con curvas de nivel («Órbitas», `clear`), medalla pequeña a la derecha, título «Oro» y línea «552,5 kg · 79,4 pts · 8/9». Requiere elegir una de las fotos de la competencia.
- **H-F5.** h-meet-next «Próxima»: «Open regional · 16 nov», título «Faltan 18 días», sub «Madrid · -83 kg · AEP», tres aperturas («Sentadilla · abro con 192,5»…), pie «Mi próxima competencia»; portada «Órbitas / noche».
- **H-F6.** h-meet-video «Vídeo (6 s)»: confeti, estallido de líneas, medalla que entra girando y total que cuenta; **se graba a 30 fps en 1080 × 1920, MP4 de ~2 MB**, mismo dibujo que la imagen. No existe exportación de vídeo ni la animación de c-cele.
- **H-F7.** h-body-sheet «Compartir tu progreso» (Cuerpo): plantillas **Cambio · Medidas**; «Incluir»: Peso actual (apagado), Cambio en kg, Medidas, % de grasa (activos). Privado por defecto: nunca sale el peso absoluto. Se abre desde el botón compartir del módulo de peso (`screens/body/design/BodyWeightModule.kt:79`) y desde el ⋯ del historial (`BodyHistorySection.kt`). Hoy Cuerpo solo exporta CSV (`screens/settings/export/CsvExportSheet.kt`).
- **H-F8.** h-body-change «Cambio»: curva de 12 semanas con degradado y último punto en acento, «12 semanas», título «−4,2 kg», sub «De forma constante: 0,35 kg por semana», tres cambios (Cintura −6,1 cm · % grasa −3,4 puntos · Masa magra +0,6 kg); sin cifra de peso absoluto; pie «Mi progreso».
- **H-F9.** h-body-measures «Medidas»: fondo «Eco» que repite la cifra «6», eyebrow «Medidas · 12 semanas», título «−6,1 cm», sub «de cintura, con la misma fuerza», cuatro perímetros (Cintura, Cadera, Pecho, Brazo).
- **H-F10.** h-nutri-sheet «Compartir tu semana» (Nutrición): plantillas **Semana · Macros · Racha**; «Incluir»: Calorías, Objetivo. Se abre desde la tendencia semanal (`nu/design/NutritionWeekTrendModule.kt:143`). Nunca sale qué comiste ni tu peso.
- **H-F11.** h-nutri-week «Semana»: anillo de la media con los macros como arcos, eyebrow «Mi semana · nutrición», título «6 de 7 días», sub «En mi objetivo de 2.300 kcal» y siete barras (acento el día pasado).
- **H-F12.** h-nutri-macros «Macros»: portada «Bruma / carbón», eyebrow «Media diaria · 7 días», título «162 g de proteína», sub «2.240 kcal · objetivo cumplido» y tres barras (Proteína 162/180, Carbos 228/250, Grasa 71/75).
- **H-F13.** h-nutri-streak «Racha»: portada «Espiral», eyebrow «Racha», título «23 días», sub «Registrando todas mis comidas» e insignia «Mi mejor racha»; sale del hábito «Comidas» (`screens/habits/HabitsScreen.kt:81`).
- **H-F14.** Otras disciplinas: h-race «Carrera · tiempo» (título «1:38:12», sub «21,1 km · 4:39 /km · puesto 1.204», marca personal −3:41, último km 4:21, categoría M35 187.º), h-stage «Culturismo · tarima» (foto a pantalla completa, medalla de plata, «2.º puesto», «14 competidores · 9 semanas de prep»), h-combat «Combate · cuadro» (título «Plata · 3–1», camino ronda a ronda).
- **H-F15.** Hoja de compartir: el patrón del lienzo (`shareSheet`) lleva vista previa 176, **control de plantillas segmentado**, **«Incluir»** y botones «Instagram» / «Vídeo» / «Guardar · Más apps». La hoja actual del programa: título «Compartir en historias» (`st/ProgramShareSheet.kt:152`), vista previa de **190 dp** (`:157`), píldoras de plantilla (`:162-178`), **cinco muestras de fondo** de 36×45 (`:184-197`), «Compartir en Instagram» / «Compartir» (`:201`), «Guardar» (`:209`) y «Más apps» (`:216`). Ver H-D1.
- **H-F16.** Paleta/tipografía de las nuevas historias: reutilizan `st/StoryCanvas.kt` (columna 928,8 px, arte hasta el 58 %, Syne ExtraBold 113,4 px, Inter) y las portadas de líneas ya existentes (`LineCoverStyle`: Relieve, Perfil, Ondas, Órbitas, Campo, Espiral, Eco, Bruma, Sesión; `ob/design/entreno/plan/cover/LineCover.kt:4`); la medalla con halo, el anillo con arcos y el gráfico de 12 semanas son piezas nuevas.

### Dudoso
- **H-D1.** ¿El patrón `shareSheet` del lienzo (vista previa 176, «Incluir», «Instagram / Vídeo / Guardar · Más apps») reemplaza también a las hojas existentes de programa (`st/ProgramShareSheet.kt`), mes (`st/MonthShareSheet.kt:125, 150`) y entreno, o solo rige para las nuevas? Si rige, **¿qué pasa con las cinco muestras de fondo y con el nombre «Compartir en historias»?**
- **H-D2.** Pregunta 2 de `d-all` (vídeo del resumen del mes): sin respuesta. **¿Se implementa vídeo solo para competencia (recomendación) y se oculta «Vídeo» en las demás hojas?**
- **H-D3.** Pregunta 1 de `d-all` (fotos de progreso del cuerpo): sin respuesta. **¿Se descarta del alcance de esta entrega?** (h-body no la usa.)
- **H-D4.** h-meet-photo / h-stage usan una foto de la competencia pero la app guarda fotos copiadas a su almacenamiento (`data/competitions/CompetitionMediaStore.kt`) y vídeos. **¿Qué foto se usa (la primera, o se elige en la hoja) y qué pasa si solo hay vídeos o ninguna?**
- **H-D5.** «Mejor total: +12,5 kg» / «Racha 23 días» se calculan de datos; **¿qué texto sale cuando no hay competencia anterior o la racha es 1?**

---

## Recuento

| Grupo | Sobra | Falta o difiere | Dudoso |
|---|---:|---:|---:|
| 1. Competencias (C-) | 20 | 22 | 8 |
| 2. Crear programa (CR-) | 6 | 8 | 5 |
| 3. Registro de comida · Describir (F-) | 16 | 17 | 6 |
| 4. Buscar y menús (S-) | 11 | 13 | 6 |
| 5. Vitaminas y minerales (M-) | 7 | 12 | 5 |
| 6. Historias (H-) | 0 | 16 | 5 |
| **Total** | **60** | **88** | **35** |


---

## Archivos de la app que habría que tocar, por grupo (para repartir sin solapes)

Todos bajo `android-native/app/src/main/java/com/example/kpkn/`.

**Competencias (C-)** — solo este grupo toca:
- `screens/competitions/` completo: `CompetitionScreen.kt`, `ProfileCompetitionsArchive.kt` y los 7 archivos de `wizard/`; nuevas pantallas (hub, nueva, próxima, plan, hoy, resultado, puesto, recuerdos, celebración, ficha, menú, marcas, comparar y las plantillas de carrera, tarima, combate, pruebas, tabla, partidos).
- `data/models/CompetitionModels.kt`, `data/repository/CompetitionRepository.kt`, `data/competitions/CompetitionMediaStore.kt`, `data/competitions/PowerliftingFederationCatalog.kt`, `services/competition/CompetitionReminderManager.kt`.
- `domain/competitions/` (`AttemptPlanner`, `MeetDayPlan`, `WeighInPlanner`, `CompetitionScoring`, `CompetitionCompare`, `CompetitionPlaceHonors`, `PowerliftingWizardDraft`, `MeetResultToProfile`).
- Rutas: `navigation/Navigation.kt:126-129`, `navigation/DeepLinkRouter.kt:47-49`, `MainActivity.kt:554-567, 1068-1072, 1207-1231, 1442-1446, 1637`.
- Perfil: `screens/profile/ProfileScreen.kt:295-300`.
- Inicio (solo la parte de competición): `screens/home/HomeScreen.kt:1164-1213`, `screens/home/design/HomeSessionHero.kt:405-412`, `screens/home/HomeSessionSection.kt:244-250`.
- Programa (cruce con el otro inventario): `screens/programdetail/design/ProgramCompetitionModule.kt`, `screens/programdetail/ProgramDetailScreen.kt:180, 547, 641, 647`, `screens/programdetail/components/editor/MacrocycleEditorLegacy.kt:715-765`.

**Crear programa (CR-)**:
- `screens/programs/`: `NewProgramSheets.kt`, `CreateProgramTemplateSheet.kt` (retirar o reducir), `ProgramsScreen.kt` (`:323-332, 352-399`), `ProgramCatalogPage.kt` (`:227`), `ProgramsViewModel.kt`, `TrainingMaxWizard.kt`, `ProtocolDetailSheet.kt`, `import/ImportProgramScreen.kt` + `ImportProgramViewModel.kt`; nueva pantalla «Listo para empezar».
- `screens/onboarding/` (instancia «Nuevo programa»): `SetupWizardViewModel.kt`, `SetupWizardModels.kt`, `SetupWizardSteps.kt`, `SetupReviewStep.kt`, `SetupActivationPayload.kt`, `design/ActivationOverlay.kt`, `design/WizardVisualGateScreen.kt`.
- `domain/onboarding/SetupStepGraph.kt`, `SetupStepDefinitions.kt`, `EntrenoStepValues.kt`.
- `navigation/PostDischargeRouting.kt`, `navigation/DeepLinkRouter.kt:101`.
- `MainActivity.kt:1026-1032, 1195-1204, 1324-1365, 1853-1870` (**solapa con Competencias en `MainActivity.kt`**: repartir por rangos de líneas o hacer los cambios de `MainActivity` en un solo turno).
- Inicio: `screens/home/HomeScreen.kt:173, 414-480` y `HomeViewModel.kt:71-95`.

**Registro de comida y Buscar (F-, S-)** — comparten archivos, deben ir en un solo bloque:
- `screens/nutrition/components/FoodLoggerDrawer.kt` (1974 líneas), `FoodLoggerViewModel.kt`, `FoodClarificationPrompt.kt`, `GramsEditor.kt`.
- `screens/nutrition/design/logger/` (los 22 archivos: cabecera, pestañas, campo, panel de análisis, avisos, fila de alimento, clarificaciones, barra de guardado, filas de búsqueda, vacío, éxito, hoja de fecha, ajustes, medidas, descarte, privacidad, catálogo).
- `screens/nutrition/reuse/` (`ReuseLoggerEntries.kt`, `QuickEntryScreen.kt`, `TemplatesSheet.kt`, `CopyToSheet.kt`), `screens/nutrition/scan/BarcodeScanScreens.kt`, `screens/nutrition/recipes/RecipeLibraryOverlay.kt`, `screens/nutrition/customfood/FoodFavoriteAction.kt`, `screens/nutrition/design/NutritionFoodMenu.kt` + `NutritionFoodRow.kt`.
- `domain/nutrition/FoodLoggerPrimaryAction.kt`; `data/voice/VoiceNutritionRecognizer.kt` (dictado).
- Registrador en Inicio: `screens/home/design/HomeNutritionToday.kt`, `screens/home/HomeScreen.kt:402-413, 512-`, `HomeFoodLoggerHost`.
- Nueva pantalla de ficha de alimento (S-F4) y nuevos hojas (rowmenu, forget, ajustes del registro, measures, date).

**Vitaminas y minerales (M-)** — depende del bloque anterior para la ficha, el registro y el ajuste:
- `screens/nutrition/nutrients/NutrientsSheet.kt` (reescritura), `NutrientsSamples.kt`; nuevos: componente de huella, hoja de nutriente, lista de comida/ración.
- `screens/nutrition/NutritionScreen.kt:110, 318-321, 467-468`, `NutritionViewModel.kt:235-241`.
- `domain/nutrition/NutrientGroups.kt`, `NutrientCoverage.kt` (si se amplía el orden o los grupos).
- Puntos de integración con el bloque F/S: `FoodLoggerDrawer.kt` (módulo de comida), ficha (S-F4), hoja de ajuste (F-F11), éxito (F-F14), media hoja de Inicio (F-F17).

**Historias (H-)**:
- `screens/stories/`: nuevos `CompetitionStory*.kt`, `BodyStory*.kt`, `NutritionStory*.kt` + sus hojas; ajustes en `StoryCanvas.kt` (medalla, anillo, gráfico), `StoryShare.kt` (vídeo MP4) y, según H-D1, `ProgramShareSheet.kt` / `MonthShareSheet.kt`.
- Puntos de entrada: `screens/competitions/` (celebración, ficha, ⋯) → **depende del grupo Competencias**; `screens/body/design/BodyWeightModule.kt` y `BodyHistorySection.kt`; `screens/nutrition/design/NutritionWeekTrendModule.kt`.
- Fotos: `data/competitions/CompetitionMediaStore.kt` (lectura).

### Solapes a vigilar
- `MainActivity.kt`: Competencias (rutas) y Crear programa (Inicio, importar, asistente) lo tocan en rangos distintos.
- `screens/home/HomeScreen.kt`: Competencias (barra de competición), Crear programa (hoja «Nuevo programa» y código muerto) y Registro (registrador en Inicio) lo tocan en bloques distintos.
- `screens/nutrition/components/FoodLoggerDrawer.kt`: lo tocan F, S y M; asignarlo a una sola persona.
- `screens/competitions/`: lo reescribe Competencias; Historias solo añade puntos de entrada tras terminar.
- `screens/programdetail/design/ProgramCompetitionModule.kt`: lo toca Competencias (C-S19) y el inventario de Program Detail (p-comp): decidir C-D3 antes de tocar.
