# Plan · «Bienvenida y guía v1» (30-oct-2026)

## Contexto

Tres encargos del usuario sobre la primera experiencia de KPKN:

1. **Bienvenida** (primer arranque). Ya no hay RINGS y la app cambió mucho. Los teléfonos tienen que mostrar la app actual **respetando su relación de aspecto**.
2. **Auditoría del wizard**. ¿Cada pregunta está bien cableada con la app? ¿Hay inconsistencias graves entre pasos?
3. **Guía de recorrido**. Los mensajes del primer paso por cada pantalla «funcionan pésimo». Hay que rehacerla funcional, bien diseñada, interactiva, animada y sin bugs.

### Qué encontré (exploración y verificación a mano)

**Bienvenida**
- Las páginas Entreno y Nutrición son vídeos grabados el 11 y el 12 de octubre con el diseño viejo: la sesión en vivo previa a Ref02 y los cuatro anillos de macros previos a «Nutrición plana».
- El marco del teléfono es 15:31 (`WelcomeSceneSize` 300×620). El Flip es 9:22.
- Para encajar, `C:\kw\tools\build_welcome_videos.py` recorta las barras del sistema y rellena con negro: quedan 22 px negros a cada lado (y 26 px arriba en Nutrición).
- El agujero de la cámara se dibuja encima del título de la app.
- La 3.ª página («Resultados») son cuatro `Text` de relleno; su vídeo de RINGS se borró.
- Hay unas 4.900 líneas de escenas Canvas que imitan el diseño viejo. Hoy no se ven: son el respaldo si falta el vídeo.

**Wizard**
- Hay respuestas que no llegan a la app. La más grave es el **género**: el parche de Ajustes solo lo escribe si se respondió el paso GENDER, que ya está retirado (`SetupWizardViewModel.kt:3577`). Lo verifiqué.
- Hay validaciones que solo saltan en la revisión final.
- La calibración de volumen no llega a los programas generados. Lo verifiqué.
- Quedan restos de RINGS/AUGE.
- Hay androidTest que no compilan.

**Recorrido** (`ui/tour/`) — fallos de raíz:
- **Marca «visto» sin haberse mostrado.** Si falta el ancla, el paso se salta a los 1,5 s, y al saltarse todos el recorrido se da por terminado.
- **Arranca antes de que carguen los datos.** Lo hace con `LaunchedEffect(Unit)`.
- **Anclas que no existen.** `workout.siguiente` y `home.rings`, y los textos hablan de «Músculos involucrados» y de RINGS.
- **Un «Pruébalo» puede empezar un entreno.** El ancla del hero de Inicio envuelve el botón «Empezar».
- **Sin auto-scroll.** No sigue las animaciones de entrada (desfase de hasta 12 dp) y usa `boundsInRoot` recortado.
- **Queda debajo de las hojas.** Su zIndex es 0, frente a 350/360 de las hojas.
- **Empieza de cero al plegar o girar.**
- **TalkBack sigue leyendo lo de debajo.**
- **Apenas tiene animación.**

### Decisiones del usuario (AskUserQuestion, 30-oct)

- Teléfonos: **regrabar en el Flip a pantalla completa 9:22**, con un guion reproducible.
- 3.ª página: **Progreso + Cuerpo**. Que se entienda que el progreso es de entreno y también físico.
- Recorrido: **lienzo primero**, sobre capturas reales, y después implementación 1:1.
- **Gradle por la cola y el Flip autorizados** en esta ola (regla de 20 min; grabación en sesión supervisada).

---

## Dónde y quién

- **Rama de la ola:** `feat/guia-v1` desde master `c9d3b2801`, en el worktree disperso `C:\kw\guia` (`bash /c/kw/tools/mkwt.sh guia master feat/guia-v1`).
- **Paquetes paralelos:** cada uno en su worktree, `C:\kw\gu-wiz`, `C:\kw\gu-tour` y `C:\kw\gu-wel` (ramas `guia/wiz`, `guia/tour`, `guia/wel`). Tocan archivos disjuntos. Solo `gu-tour` toca `MainActivity.kt`.
- **Escriben subagentes; yo oriento y reviso los diffs** (regla vigente):
  - Opus 5.5 high: motor del recorrido y lienzo.
  - Sonnet 5.5 high: arreglos del wizard, capa visual del recorrido y código de la bienvenida.
  - Sonnet medium: guiones, anclas y sembrador.
  - Haiku 5.5 max: limpieza mecánica, siguiendo la guía `kpkn-haiku-brief`.
  - Nunca Fable.
- **Docs de la ola:** `docs/design/guia-v1/` (lienzo, `DECISIONES.md`, `PARIDAD_1A1.md`, `ESTADO.md`) y `docs/audits/WIZARD_AUDIT_2026-10-30.md`.
- **Sin cambios de Room.** Todos los campos que se escriben ya existen.
- **Única pausa prevista:** la aprobación del lienzo. Fuera de eso, nada de «¿sigo?». Para la sesión supervisada del Flip te aviso una sola vez.

---

## Fase 1 · Auditoría del wizard y arreglos (`gu-wiz`, en paralelo con las fases 2 y 3)

Entregable: `docs/audits/WIZARD_AUDIT_2026-10-30.md`. Cada hallazgo lleva la ruta y la línea donde está, la gravedad y el arreglo, con su prueba.

**Se arreglan en la ola** (bugs; no se quita ninguna función):

| # | Hallazgo | Arreglo |
|---|---|---|
| W1 | El paso «¿Cuál es tu género?» no llega a `userVitals.gender`. Consecuencias: la categoría de competición cae a «male» (`CompetitionFlowViewModel.kt:95`), la voz en femenino no se activa (`RelatorContextBuilder.kt:404`), el BMR del check-in se omite y la ficha del Perfil queda vacía. | Mapear `EQUATION_SEX` a `SetupUserVitalsPatch.gender` (VM ~3577; `data/onboarding/SetupSettingsPatch.kt`). |
| W2 | Embarazo/lactancia (ELIGIBILITY) no llega a `userVitals.pregnancyLactation`. Lo leen `DriTargets` y `CaffeineLimitEngine`. | Escribirlo en el parche. |
| W3 | La grasa corporal no llega a `userVitals.bodyFatPercentage`, que lee `NutritionCheckinRuntime.kt:54`. | Escribirla en el parche. |
| W4 | Cuatro validaciones de nutrición fallan solo al final, en `reviewErrors`: objetivo contra dirección, kcal y macros manuales vacíos, edad < 19 con plan automático y elegibilidad «No lo sé». | Validar en el propio paso, con error en línea y el check apagado (`SetupWizardModels.kt` ~1717-1791). |
| W5 | Los programas generados, de autor y NATIVE no copian `volumeRecommendations` ni `athleteProfileScore`. Por eso Program Detail muestra «Referencia general» (`VolumeCalibrationGate`). | Antes, confirmar que la calibración sigue viva tras el retiro de RINGS (`VolumeCalibrationProvenanceTest`). Si lo está, copiar en `SetupGeneratedPlans.presentGenerated` y en las ramas de autor y NATIVE. Si no, va a decisiones. |
| W6 | El enlace profundo `FULL`/`RESUME` no tiene guardia cuando el alta ya está hecha: sustituye el plan de nutrición y crea otro programa. | Redirigir a `NEW_PROGRAM` (`DeepLinkRouter.kt:96-110`). |
| W7 | Hay dos ids de borrador para el mismo alcance: `setup-wizard:full:<uuid>` y `setup-wizard:full`. El aviso de Inicio abre otro borrador. | Unificarlos (`SetupEntryScreen.kt:55`, `HomeScreen.kt:258`). |
| W8 | Un pesaje con fecha de hoy duplica la observación WEIGHT del mismo día. | Deduplicar (`SetupActivationPayload.kt:55,105`). |
| W9 | La pantalla pide la fecha de nacimiento, pero solo se guardan los años y la edad envejece. | Escribir también `birthDateIso`. |
| W10 | Los androidTest con restos de RINGS no compilan: `OfficialRingAdjustDeviceTest` y otros de `screens/workout`. | Retirarlos y anotarlos en `docs/post-rings/RETIRED_TESTS.md`. |
| W11 | Restos de RINGS/AUGE en el código y los textos: `SetupControlKind.RINGS_*`, `KpknModule.RINGS`, `ringLineText`, `summaryFeeling`, `initialWellbeing` / `auge_wellbeing`, la escritura sin lector de `athleteType`, la carpeta vacía `rings/`, «tus anillos» en `PostDischargeRouting`, comentarios y `docs/wizard-vm-api.md` desfasado. | Limpieza con Haiku max. Sin cambio de comportamiento. |

**Decisiones de producto.** Van a la pestaña «Auditoría» del lienzo con mi recomendación. No se quita nada sin tu sí:
- **Q-a.** NUTRITION_HISTORY_CONTEXT (tendencia y máximo previo) se pregunta y no se usa. Opciones: usarlo para ajustar el ritmo propuesto, o retirarlo.
- **Q-b.** «Estoy empezando» responde igual tres calibraciones redundantes con la experiencia. Propuesta: derivarlas, como ya se hace con la técnica.
- **Q-c.** Tras el retiro del motor viejo, los programas nacen con `autoregulationMode = PROPOSE`. ¿Ese es el valor por defecto correcto?
- **Q-d.** Una competición con fecha no condiciona ni la nutrición ni la variante simple, aunque el subtítulo promete «llegar en tu mejor momento». Opciones: cambiar el texto o el comportamiento.
- **Q-e.** Límites de tiempo de sesión dispares: el dial va de 30 a 180, los planes propios llegan a 100 y hay tolerancias distintas.
- **Q-f.** No hay entrada desde Ajustes ni Perfil para rehacer el asistente.
- **Q-g.** Código muerto de `FROM_SCRATCH`, `focus` y `trainingIntent`: se puede retirar.

**Pruebas nuevas o ajustadas:** `SetupSettingsPatchTest` (género, embarazo, grasa y fecha), `SetupWizardValidationTest` (validaciones en el paso), `DeepLinkRouterTest` (guardia), `SetupActivationContractTest` (pesaje deduplicado) y `VolumeCalibrationProvenanceTest`.

---

## Fase 2 · Motor del recorrido v2 (`gu-tour`, independiente del diseño)

Rehacer `ui/tour/` como una máquina de estados:
`Inactivo → Esperando → Preparando paso → Mostrando → En pausa → Terminado`.

1. **Arranque con puertas.** La pantalla declara el recorrido con un composable `TourRequest(id, steps, ready, route, prepare)` en lugar de `LaunchedEffect(Unit)`. Solo arranca cuando se cumplen todas estas condiciones:
   - `ready` (datos cargados);
   - la ruta actual coincide;
   - no hay ninguna hoja abierta (`KpknModalTracker.openCount == 0`);
   - no hay diálogo de permisos ni overlays del alta (`homeOnboardingOverlay`, activación);
   - el ancla del primer paso está medida y estable.
2. **«Visto» solo si de verdad se vio.** Se marca al terminar o al saltar, y solo si al menos un paso se mostró. Los pasos sin contenido se filtran al arrancar con el contexto de la pantalla (por ejemplo, sin plan no hay paso «Tu plan»), sin temporizadores.
3. **Si el ancla desaparece a mitad, pausa en vez de saltar.** Espera hasta 4 s. Si no vuelve, salta solo ese paso. Si cambia la ruta, el recorrido se suspende sin marcarse y se reanuda en la siguiente visita.
4. **Anclas vivas.** `TourAnchorRegistry` guarda las `LayoutCoordinates` vivas y no una foto del `Rect`. El overlay calcula `localToWindow` sin recorte en cada cuadro mientras el paso se asienta, así sigue las entradas animadas (`kpknEntrance`, `homeEntrance`). Se convierte de ventana a coordenadas locales del overlay.
5. **Llevar el ancla a la vista.** Cada ancla trae su `BringIntoViewRequester`, con margen para la burbuja. Para listas perezosas o acciones previas (expandir una sesión, cambiar de pestaña), la pantalla da un `prepare` por ancla.
6. **Capa propia por encima de las hojas.** El recorrido se monta en `MainActivity` con zIndex 450, fuera del mapa `entries` de `KpknOverlayHost`. Mientras haya una hoja abierta se oculta y se pausa. Un «Pruébalo» que abre una hoja continúa al cerrarla.
7. **Sobrevive al plegado y a la rotación.** El estado vive en un ViewModel de la Activity con `SavedStateHandle`: id y paso.
8. **Cola.** Si se piden dos recorridos a la vez, el segundo espera en lugar de perderse.
9. **Accesibilidad.** Mientras hay recorrido, lo de debajo queda oculto a TalkBack. El paso se anuncia con `liveRegion` y el foco de accesibilidad va a la burbuja.
10. **Ids versionados** (`tour-home-v2`…), para que quien vio los viejos rotos vea la guía nueva una vez. Se retiran `HOME_RINGS`, `conTorre` y `Settings.hasSeenHomeTour`, que es código muerto.
11. **«Repetir la guía» en Ajustes.** Hoy solo muestra un Toast. Pasará a reiniciar, llevar a Inicio y arrancar. Es un cambio mínimo, porque Pulido v1 rediseñará Ajustes.

**Pruebas:** `TourControllerTest` reescrito (puertas, pausas, reanudar, cola, nunca «visto» sin mostrarse), pruebas de colocación de la burbuja (ancla enorme, insets, IME, letra al 130 y al 200 %) y `TourAnchorCoverageTest`, que escanea el código fuente. Esta última comprueba que cada ancla de los guiones existe en exactamente un `tourAnchor(TourAnchors.X)`, y así detecta si otra ola la borra.

La capa visual se deja provisional hasta que se apruebe el lienzo.

---

## Fase 3 · Bienvenida: datos de demo, grabación y marco 9:22 (`gu-wel`)

1. **Datos de demo** (solo depuración: `src/debug/.../DemoDataPlan.kt` y `DemoDataSeeder.kt`):
   - 10 semanas de historial con progresión en 3 básicos y un récord en la última semana;
   - 8-10 semanas de pesajes hacia un objetivo, reutilizando la lógica `descent` de `BodyTrendHarnessActivity`.
   - Ids fijos `demo-…`.
   - `markToursSeen` se mantiene para las grabaciones.
2. **Guion reproducible en el repo** (`scripts/welcome/`):
   - Las acciones de `phone_run.py` (`rec:` / `recwait:` / `pull:`) por toma.
   - El montaje adaptado de `build_welcome_videos.py`:
     - 720×1760 (9:22) **sin recorte ni relleno**;
     - franja de la barra de estado tapada en negro, por privacidad;
     - CRF 20 y `faststart`;
     - pósteres WebP de 45 KB como mucho.
   - La comprobación con `ffprobe` + `cropdetect`: sin barras negras.
   - Sirve para regrabar en minutos cuando aterrice Pulido v1.
3. **Guion de las tomas** (se aprueba en el lienzo):
   - **Entreno:** Inicio → Empezar → registrar una serie → arranca el descanso.
   - **Nutrición:** isla «Registrar comida» → escribir la comida → Analizar → guardar → sube el día (anillo de kcal y macros planos).
   - **Progreso + Cuerpo:** progreso de un ejercicio con su récord → Cuerpo con la tendencia de peso hacia el objetivo.
4. **Código** (`screens/onboarding/welcome/`):
   - **Proporción.** La pantalla del teléfono toma exactamente la proporción del vídeo: una constante 720/1760, comprobada por una prueba.
   - **Vídeo a tamaño real.** El `PlayerView` se coloca al tamaño real de la pantalla, no dentro del lienzo escalado, para evitar el doble escalado.
   - **Barra de estado dibujada.** Hora, señal y batería en la franja superior, con el agujero de la cámara dentro de esa franja y no sobre la app.
   - **Altura del teléfono.** Se reajusta `welcomePhoneHeight` para el teléfono más estrecho.
   - **Página 3.** Vídeo `welcome_progreso.mp4`, textos nuevos y acento sin el color heredado de RINGS.
   - **TalkBack.** Descripciones sin «anillos».
   - **Botón.** «Continuar» si existe un borrador recuperable.
   - **Limpieza.** Se borran las escenas Canvas del diseño viejo (`WelcomeEntreno*`, `WelcomeNutricion*`, `WelcomeResultsScene` y la paleta de RINGS de `WelcomeScene`) con sus pruebas. Si falta el vídeo, se muestra el póster.
   - **Recursos.** `res/raw/keep.xml` al día.
5. **Pruebas:**
   - `WelcomeVideoAssetsTest` lee la caja `tkhd` del MP4 en Kotlin puro y exige 720×1760 en los tres vídeos y en los pósteres.
   - Se actualizan `WelcomeShellLayoutTest` (9:22) y `WelcomeShellCopyTest`.

**Sesión supervisada 1 del Flip** (unos 20 min; te aviso). Se hace con la app `.dbg` (`--clear-dbg`, `DemoSeedActivity --ez pro true`):
- capturas a pantalla completa de las 6 pantallas con recorrido, para el lienzo;
- 2 tomas en bruto de cada vídeo.

El montaje final se hace después de aprobar el lienzo.

---

## Fase 4 · Lienzo «Guía v1» (la única aprobación)

- **Dónde:** `docs/design/guia-v1/`. Reutiliza el motor de escenas de `docs/design/registros-v1/src`, los estilos de `program-detail-v1/src` y las fuentes reales (`res/font`).
- **Cómo se ve:** a tamaño Flip (360×880 dp) y sobre las capturas reales de la sesión 1.
- **Publicación:** como artifact privado. Antes de escribirlo cargo el skill `artifact-design`.

**Pestaña «Bienvenida»**
- Las 3 páginas con el marco 9:22, la barra de estado dibujada, los textos y la inclinación.
- El guion de cuadros de cada vídeo.

**Pestaña «Guía»**
- Prototipo interactivo de cada recorrido: Inicio, Programa, Primera sesión, Nutrición, Cuerpo y Perfil.
- Visual:
  - velo que entra y sale con fundido;
  - foco que se transforma de un ancla a otra;
  - burbuja con la flecha en un solo trazo, que entra desde el lado del ancla;
  - fundido cruzado entre pasos;
  - segmentos de progreso animados.
- «Pruébalo»:
  - anillo que respira y el gesto de un dedo;
  - ancla precisa: el chip, no todo el hero;
  - siempre hay salida («Siguiente»).
- Casos límite:
  - burbuja anclada abajo si el ancla es enorme;
  - pausa con una hoja abierta;
  - letra al 130 %;
  - movimiento reducido;
  - salida en la que el foco se abre a pantalla completa;
  - vibración.
- Cada animación lleva su curva y su duración exactas, para la comprobación cuadro a cuadro.
- Guiones propuestos (como mucho 5 pasos y 90 caracteres por texto), solo sobre anclas reales de hoy:
  - Inicio: sesión, músculos, semana, nutrición de hoy, programas, barra.
  - Programa: semana, sesión (expandir), empezar, pestañas.
  - Primera sesión: serie, registrar, siguiente ejercicio (ancla nueva).
  - Nutrición: registrar, día, comidas, tendencia, plan.
  - Cuerpo: pesaje, tendencia y objetivo.
  - Perfil: ajustes (repetir la guía), competiciones.

**Pestaña «Auditoría»**
- W1–W11: qué se arregla.
- Q-a…Q-g, con el valor recomendado para aprobarlas de una vez.

---

## Fase 5 · Implementación 1:1 tras la aprobación

- **Capa visual del recorrido** (`KpknTourOverlay.kt` reescrito), igual al lienzo. Inventario elemento a elemento en `PARIDAD_1A1.md`.
- **Guiones y anclas** (`TourScripts.kt` y pantallas):
  - Partir el ancla del hero de Inicio para que «Empezar» quede fuera de un «Pruébalo».
  - Añadir `workout.siguiente`.
  - Renombrar `nutrition.anillos` a `nutrition.dia`.
  - Dar `prepare` a «Empezar sesión», que pide la sesión expandida.
  - Arreglar el ancla de ajustes con alfa 0 en Perfil.
- **Wizard:** aplicar las decisiones Q aprobadas.
- **Bienvenida:** montaje final de los 3 vídeos y pósteres. Si el guion cambió en la aprobación, una sesión supervisada 2, corta.
- **Antes de cerrar:** integrar `guia/wiz`, `guia/tour` y `guia/wel` en `feat/guia-v1`. Solo `gu-tour` toca `MainActivity`, así que no se esperan conflictos.

---

## Fase 6 · Verificación e integración

1. **Carril JVM.** `bash /c/kw/tools/kpkn_jvm.sh guia ...` para el controlador del recorrido y el dominio del wizard (`domain/onboarding`, `domain/nutrition`).
2. **Carril Android.** `bash /c/kw/tools/gradle_slot.sh /c/kw/guia/android-native :app:testBaseDebugUnitTest --tests ...` con clases concretas: `KpknTourOverlayTest`, `TourAnchorCoverageTest`, `KpknTourRenderSheetTest`, `Welcome*Test`, `SetupSettingsPatchTest`, `SetupWizardValidationTest`, `DeepLinkRouterTest`, `SetupActivationContractTest`. Además, `:app:compileBaseDebugKotlin`.
3. **Puertas de paridad.** Render del recorrido al lado de la escena del lienzo, paso a paso, más fotogramas de las animaciones. No se declara «1:1» sin los números.
4. **Flip** (regla de 20 min; lo de `MainActivity` en sesión supervisada). Primer arranque con `--clear-dbg`:
   - **Arranque y bienvenida:** splash → bienvenida (3 vídeos, 9:22, sin barras negras ni notificaciones).
   - **Alta:** wizard → activación → el recorrido de Inicio arranca solo cuando se cierran los overlays.
   - **Recorridos:** cada uno en su pantalla.
   - **Navegar a mitad:** se reanuda y no queda marcado.
   - **Hoja abierta:** pausa.
   - **«Repetir la guía»:** funciona.
   - **Datos tras el alta:** género, grasa, embarazo y fecha guardados en el Perfil.
   - **Humo de R8:** con `dbg-r8.init.gradle`, para que los vídeos sobrevivan al reductor.
5. **Integración.** `turno.sh master` según `C:\kw\tools\INTEGRACION_GUIA.md`. Después:
   - borrar la rama y los worktrees `gu-*` y `guia`;
   - borrar las compilaciones sobrantes (regla «nada residual»);
   - poner al día la memoria.

---

## Riesgos y coordinación

- **Pulido v1 ya está rediseñando la sesión en vivo (`lv`) y Nutrición (`nc`).**
  - Los vídeos de Entreno y Nutrición y las anclas de `WorkoutV2Body` y `screens/nutrition/design` pueden quedar viejos o chocar.
  - Mitigación: `TourAnchorCoverageTest` falla si una ola borra un ancla, y el guion de grabación permite regrabar en unos 20 min cuando Pulido aterrice.
  - No edito los documentos de Pulido, porque me pediste no coordinar otras sesiones. Lo aviso en el informe final.
- **Ajustes también lo rediseña Pulido (`aj`).** Mi cambio ahí es un solo manejador. Lo resuelve quien integre en segundo lugar.
- **Cuota.** Si el uso semanal llega al 94 %, paro y dejo el resumen y el prompt de relevo.

## Archivos críticos

- **Recorrido:**
  - `ui/tour/TourController.kt`, `TourAnchorRegistry.kt`, `TourModels.kt`, `TourScripts.kt`, `TourSeenStore.kt`, `KpknTourOverlay.kt`;
  - `ui/components/KpknOverlayHost.kt` y `KpknModalTracker.kt`;
  - `MainActivity.kt` (~644, 932, 973-995).
- **Anclas en pantallas:** `screens/home/HomeScreen.kt:199`, `screens/home/design/HomeSessionHero.kt:111`, `screens/programdetail/ProgramDetailScreen.kt:302`, `screens/workout/WorkoutScreen.kt:434` y `WorkoutV2Body.kt:2342`, `screens/nutrition/NutritionScreen.kt:109`, `BodyProgressScreen.kt:77`, `screens/profile/ProfileScreen.kt:245`, `SettingsScreen.kt:296` y `SettingsViewModel.kt:147`.
- **Bienvenida:**
  - `screens/onboarding/SetupWelcomeScreen.kt`, `SetupEntryScreen.kt`;
  - `welcome/WelcomeShell.kt`, `WelcomeShellPhone.kt`, `WelcomeShellCopy.kt`, `WelcomeVideoPlayer.kt`, `WelcomeScene.kt`;
  - `res/raw/welcome_*.mp4`, `res/drawable-nodpi/welcome_*_poster.webp`, `res/raw/keep.xml`;
  - `src/debug/.../DemoDataPlan.kt`, `DemoDataSeeder.kt`.
- **Wizard:**
  - `screens/onboarding/SetupWizardViewModel.kt` (3491-3623 parche, 3419-3488 revisión), `SetupWizardModels.kt`, `SetupActivationPayload.kt`, `SetupGeneratedPlans.kt`;
  - `data/onboarding/SetupSettingsPatch.kt`;
  - `domain/onboarding/SetupStepGraph.kt`, `SetupStepDefinitions.kt`;
  - `navigation/DeepLinkRouter.kt`.
