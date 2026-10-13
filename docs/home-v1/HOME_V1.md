# Home v1 · rediseño de Inicio con la identidad del wizard (2026-10-13)

Encargo del usuario: Home y sus tarjetas, «más visual, más interactivo, más animado y bonito», replicando la identidad que ya dejó el
wizard. El mapa del Home de hoy está en `C:\kw\analysis\home\home_map.md` (rutas y líneas). Las decisiones que tomé sin preguntar
(el usuario dormía) están en `C:\kw\analysis\decisiones_2026-10-13.md`.

## 1. Qué falla hoy (crítica)
| Zona | Problema | Gravedad |
|---|---|---|
| Identidad | Mezcla dos marcas: el amarillo de Material (enlaces, barra, avisos) y una fuente ancha, frente a la tinta, los acentos y Syne del wizard | 🔴 |
| Sesión de hoy | Una tarjeta con degradado y dos botones solo de icono (lápiz y «repetir» verde), ambiguos. El arte del programa no aparece | 🔴 |
| RINGS | La torre ocupa media pantalla, pero arriba hay una línea técnica («Cobertura de datos: Media · Historial real · …»). El detalle es una hoja de Material sin el ajuste que promete «¿Qué es esto?» | 🟡 |
| Macros y biometría | Cajas `Card` de color fijo, barras lineales y círculos de 104 dp. Inventa la altura (170 cm) para el IMC y el FFMI | 🔴 |
| Programas | Tarjetas de 176 × 112 dp con un degradado propio, sin la portada del programa | 🟡 |
| Avisos | `Surface` de `errorContainer` de Material | 🟢 |
| Honestidad | Un músculo sin dato sale al 100 % (`?: 100`) | 🔴 |

## 2. Principios (los del wizard)
- **Superficie:** negra y continua. Nada de tarjetas; las secciones se separan por aire (40 dp) y, si hace falta, por una línea fina
  (`WizardColors.divider`). El vidrio va solo en lo que flota: barra superior, barra inferior y hojas.
- **Letra:** Syne en títulos y cifras grandes; Inter en lectura. Solo roles de `WizardTypography` y tokens de `WizardColors`,
  `SymbolPalette` y `WizardSpacing`.
- **Movimiento:**
  - Entrada de cada sección una vez por proceso: fundido y subida de 12 dp, 420 ms `FastOutSlowIn`, escalonada 50 ms.
  - Las cifras cuentan hacia arriba y los anillos se dibujan.
  - El arte del programa respira en un bucle lento, solo si se ve.
  - Con movimiento reducido, cuadro final.
- **Honestidad:** sin dato no hay cifra. «—», «Sin calibrar» y «Sin objetivo», nunca un valor por defecto.

## 3. La página, de arriba abajo
1. **Cabecera de la página (masthead):**
   - Fecha en versalitas («MARTES 13 DE OCTUBRE»).
   - Saludo en Syne («Buenos días, Alex»), que se encoge si no cabe.
   - Botón de perfil con la inicial.
   - Debajo: el programa activo («Camino al pico · Semana 3 de 12»), una regla de semanas segmentada y la fase si existe.
   - La barra superior de vidrio solo aparece cuando la cabecera sale de la pantalla.
2. **Tu semana:**
   - 7 días desde el día de inicio del programa.
   - Hoy va relleno de tinta. Hecho = check verde, entreno pendiente = punto músculo, descanso = guion tenue, entreno pasado sin
     hacer = punto hueco.
   - Tocar un día muestra su sesión en el bloque siguiente.
3. **La sesión del día (héroe a sangre):**
   - **Fondo:** el arte de la portada del programa (motor de portadas de K3a), o el fondo propio de la sesión, con velo negro abajo.
   - **Texto:** kicker («HOY · DÍA 2 DE 4»), título grande, los 3 primeros ejercicios con su dosis, duración y número de ejercicios.
   - **Acciones:** «Empezar sesión» (píldora de tinta) y «Editar», con texto.
   - **Variantes:** descanso (arte apagado y «Siguiente: jueves · Torso B»), hecha, en curso, otro día elegido («Ver en el
     programa»), sin programa, registrar competición y cuenta atrás.
4. **Cómo llegan tus músculos:** anillos de 44 dp con el % de cada músculo de la sesión. Sin dato = «—». Al tocar, el detalle de
   Músculos.
5. **Tus RINGS:**
   - Titular del motor en Syne.
   - La torre a la izquierda y tres filas a la derecha (Energía, Músculos y Columna, en el orden de la torre): cifra en Syne, banda o
     «Estimación inicial», o «Sin calibrar».
   - (i) para entender los RINGS (la introducción animada de K6, en modo repaso).
   - **El detalle** es una hoja de vidrio con:
     - el anillo grande;
     - tu rango habitual;
     - por qué;
     - qué hacer hoy;
     - horas a la normalidad;
     - músculos uno a uno;
     - últimas sesiones;
     - y el ajuste con deslizador («Tu ajuste manda sobre la estimación de hoy»), que llama a `AugeViewModel.applyManualBatteries`.
6. **Avisos** (si hay): sin caja, con un icono teñido, el texto y una acción con texto.
7. **Nutrición de hoy:**
   - Los anillos de macros del wizard (`WizardMacroRings`) con las kcal contando, «de 2.300 kcal · te quedan 1.060» y la leyenda.
   - Cuatro comidas como símbolos (Desayuno, Almuerzo, Merienda y Cena), con sus kcal o «Añadir». Al tocar una, se abre el registro
     ya en esa comida.
8. **Cuerpo:**
   - Peso en Syne y el cambio de 30 días.
   - Línea de tendencia que se dibuja, y la línea del objetivo si lo hay.
   - % de grasa y % de músculo. IMC y FFMI solo con la altura real.
   - Sin pesajes: invitación a registrar.
9. **Tus programas:** pósteres 3:4 con el arte de cada programa; el activo con «ACTIVO» y su progreso. Al final, «Nuevo» («Crear o
   importar»).
10. **Aprende:** el concepto del día con un símbolo, el título en Syne, dos líneas y «Leer».
11. **Barras:** la inferior con la activa en tinta y un punto músculo, sin amarillo. La de permisos recomendados pasa a la estética
    KPKN.

## 4. Paquetes (ola I, subagentes Haiku 5.5)
| Paquete | Qué | Depende de |
|---|---|---|
| I1 | Base: orden y espaciado de la página, cabecera, «Tu semana», barra superior según scroll y hoja de render de Home | — |
| I2 | Héroe de la sesión del día y anillos de músculos | I1, K3a |
| I3 | Sección RINGS y su detalle con ajuste | I1, K6 |
| I4 | Nutrición de hoy y Cuerpo (tendencia de peso; fuera los 170 cm inventados) | I1 |
| I5 | Pósteres de programas, Aprende, avisos y barra superior | I1, K3a |
| I7 | Barra inferior y «Permisos recomendados» (único paquete que toca `MainActivity.kt`) | — |
| I6 | Limpieza de código muerto, overlay de comidas, hoja de la página completa y documentación | I2 a I5, I7 |

## 5. Fuera de este encargo
- El tema global de Material: Home deja de usarlo, el resto de la app no cambia aún.
- Program Detail y las demás pantallas: el usuario dará indicaciones.
- Mover los tokens del wizard a `ui/`: queda anotado como deuda.

## Estado (cierre de la ola Home v1, 2026-10-13)

### Paquetes

| Paquete | Qué | Commits (merge) | Estado |
|---|---|---|---|
| I1 | Cabecera de la página, «Tu semana», barra superior según scroll, base de `design/` y la hoja de Home | `ddb12052d` (`46281070c`) | Integrado |
| I2 | Sesión del día como héroe con el arte del programa y los anillos de músculos (sin dato, «—») | `90a8f4b96` (`58d3bc657`) | Integrado |
| I3 | RINGS con la torre, cifras y bandas; detalle con ajuste; intro en repaso desde Home | `5fe8c86e3`, `a0ebe7e54` (`71cc9e2a9`) | Integrado |
| I4 | Nutrición de hoy con anillos y comidas; cuerpo con tendencia real; fuera la altura inventada | `a0c1bc10b`, `e7b7ed13f` (`087ff1116`) | Integrado |
| I5 | Pósteres de programas, «Aprende», avisos y barra superior con la identidad KPKN | `e458e1391` (`8071e040e`) | Integrado |
| I7 | Barra inferior y permisos recomendados con la identidad KPKN (único que toca `MainActivity`) | `8dc96c342` (`45575480f`) | Integrado |
| I6 | Cierre: código muerto, overlay de comidas, página completa, pendientes de I2 a I5 y documentación | esta rama (`home/i6`) | Ver abajo |

### Hecho en I6

- **Compilación conjunta.** El código principal compila con todos los paquetes juntos. La compilación de pruebas falló por un bloque roto en `HomeSamples.kt`, fruto de la fusión de I2 e I5 (se perdieron el cierre de `heroSheetCases()` y la apertura del KDoc de `programOf`). Reparado.
- **Código muerto borrado** (cada uno confirmado con Grep):
  - `HomeHeaderSection.kt`, entero.
  - `augeRingsLayout`, `augeRingsClusterFits`, `AugeRingsStaggerDx`, `AugeRingsLayout` y `AugeRingsLayoutTest` (esta última solo probaba el código muerto).
  - `batteryColor`.
  - `RingsBarbellArt` y las cuatro barras de `RingsQuestionArts.kt`, con sus constantes.
  - En `HomeViewModel`: estrellas, historial, 1RM, IPF y fuerza relativa (`cardsState` y sus clases), IMC y FFMI sin llamadores, `lastWeight`, `lastBodyFat`, `lastMusclePct`, `ongoingWorkout` y cinco métodos sin llamadores (`consumeVolumeCalibrationRequest`, `updateDisplayName`, `createOnboardingProgram`, `markProgramDone`, `completeOnboarding`), con sus imports.
  - Parámetros sin uso: `HomeWithProgram` (`userName`, `greeting`, `autoDeloadMessage`) y `HomeSessionSection.currentDayOfWeek`, con sus dos llamadas.
- **Dejado porque tiene uso:**
  - `SingleRingCanvas` y `drawAugeRing*`: cuatro archivos de sessioneditor lo importan (sin llamarlo). Hay que quitar esos imports antes; no se tocan desde Home.
  - `augeRingsHostHeightDp`: lo usan `WorkoutReadinessSheet` y `WorkoutFinishHost`.
  - Parámetros de `HomeScreen` que pasa `MainActivity` (`themeMode`, `onThemeChange`, `nutritionViewModel`, `onNavigateToNutritionEditor`, `onOnboardingOverlayChange`): no se tocan.
- **HomeUiState:** ningún campo sin lector; todos se usan.
- **Overlay «Comidas de hoy»:** se mantienen el vidrio y la lógica; colores y textos pasan a `WizardColors` y `WizardTypography`; cada comida es una fila sin caja (tipo, nombre y kcal, con filete entre filas); «Registrar comida» es la píldora principal. La comida «Colación» pasa a «Merienda», como en la tarjeta de nutrición.
- **Pósteres:** ningún título se parte dentro de una palabra. Si una palabra no cabe ni a 14 sp, el título va en una línea con elipsis (igual que el héroe de I2).
- **Prueba de «Aprende»:** `AprendeCatalogAuditTest` busca «APRENDE» (la etiqueta de I5); la comprobación no cambia.
- **Fondo raíz de Home:** `WizardColors.background` en vez de `MaterialTheme.colorScheme.background` (en el tema es el mismo negro).
- **Imports sin uso de `HomeScreen.kt`:** quitados (`Canvas`, `clickable`, `Offset`, `Size`, `Stroke`, `RingChannel` ya no se usaban en HEAD; `Color` lo dejó sin uso el overlay nuevo).
- **Hoja de la página completa:** `HomeFullRenderSheetTest`, cuatro casos (entreno, descanso, sin programa y recién activado), a 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/home/i6`. Los datos están en `HomeSamples.pageCases()`.
- **Documentación:** `docs/ANDROID_UI_SCREENS_MAP.md` (sección de Home reescrita con las piezas y su orden) y este apartado.

### Pruebas

- Compilación del código principal (`:app:compileBaseDebugKotlin`) y de las pruebas: sin errores. Corrida final `home-i6-4`: `BUILD SUCCESSFUL in 7m 33s`.
- Pruebas de Home (`*Home*`, `*RingDetailSheet*`, `*RingsQuestion*`, `*AprendeCatalogAudit*`, `*RecommendedPermissions*`, `*TourHomeLaunch*`): 28 clases, 120 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Hojas de render regeneradas en `C:/kw/shots/home/i6`. La página completa son ocho PNG (`home_pagina_<caso>_<s100|s130>.png`), a 360 dp.
- Crítica de la página: dos rondas (de tres posibles). Se corrigieron tres cosas: el marcador del último punto de la tendencia de peso quedaba cortado por el borde (margen de 8 dp); el «+» de «Nutrición de hoy» quedaba por encima de su etiqueta (ahora va centrado con ella, con un parámetro opcional en `HomeSectionLabel`); y en el caso recién activado, la muestra mostraba un titular «Buen estado» y «Confianza Media» sin datos (ahora sin titular y con confianza «Baja»).

### Lo que no se hizo y deuda

- **Material en Home:** `KpknAlertDialog` de la recomendación de recuperación (en `HomeScreen.kt`) y las pantallas de «Conceptos clave» (`ConceptosClaveScreen.kt`, `ConceptoClaveAccordion.kt`), que `MainActivity` sigue abriendo. Fuera del encargo de I6.
- **`SingleRingCanvas`:** quitar los cuatro imports de sessioneditor (`GroupEditorCard.kt`, `SessionContextNavigator.kt`, `sheets/SessionEditorSheets.kt`, `SessionEditorAnalytics.kt`) y luego el composable y sus helpers.
- **Cobertura de `augeRingsHostHeightDp`:** sigue viva en workout, pero su prueba de capa desapareció con `AugeRingsLayoutTest`; conviene una prueba directa.
- **Rendimiento (del mapa, §7):** lecturas amplias en el cuerpo de `HomeScreen`; anclas reescritas en cada cuadro de scroll; la torre se pinta dos veces y repite su entrada; desenfoque de 100 dp en la cabecera y en la barra. No se abordó en esta ola.
- **Duplicados de `gradient://lagoon`:** ya no hay ninguno dentro de Home; quedan 7 archivos de otras pantallas (`MainActivity`, `programs`, `programdetail`, `sessioneditor`).
- **Resuelto respecto al mapa:** colores fijos de Material en Home, `?: 100` para músculos sin dato y la altura de 170 cm (solo queda en un comentario).
- **Cosmética a 130 %:** la leyenda de nutrición reparte «sin meta» en tres líneas y, en Cuerpo, «% músculo» se parte entre el «%» y la palabra. No se corta ninguna palabra; se deja tal cual.
