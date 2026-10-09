# C4c · Activación con competición: fecha clave, registro, recordatorios, permiso y sin test tras el taper (punto 13, activación)

Worktree indicado en tu encargo, rama `v3/c4c` (sale de `feat/wizard-v3` con C4a y C4b integrados). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md` y, del plan, «Punto 13» y «§3‑bis A» («Activación con fecha»).

## Contexto (verificado; confírmalo leyendo el código antes de escribir)
- Modelo: `ProgramKeyDate(id, title, type: KeyDateType, startDate, endDate?, eventDate?, notes?)` (`data/models/Program.kt:446-454`).
- Al guardar un programa con fecha clave de competición, la app ya crea el registro `CompetitionRecord` PLANNED y programa los recordatorios de
  7 días y 48 h: `domain/training/CompetitionKeyDateSync.kt:28-`, `services/competition/CompetitionReminderManager.kt:29-` y los repositorios
  (`ProgramRepository.kt` ~376, `CompetitionRepository.kt` ~93). Verifica que el guardado del alta pasa por ese mismo camino; si no, que lo haga.
- Inicio muestra la cuenta atrás con `domain/training/HomeCompetitionResolver.kt:32-` (no para programas SIMPLE).
- El permiso de notificaciones hoy solo se pide al empezar un entreno (`MainActivity.kt:429-437`, `requestWorkoutNotifications`).
- `BlockTransitionEngine` propone un test de 1RM tras el taper (`domain/training/BlockTransitionEngine.kt:163-171`): con competición caería el día
  de la competición.

## API real en tu rama (integrado: C4a y C4b)
- Lee la competición SOLO con `draft.activeCompetition()`, `draft.activeMeetDate()` (ISO) y `draft.activeMeetName()`
  (`screens/onboarding/SetupWizardModels.kt`). Las utilidades están en `CompetitionRules` (`domain/onboarding/EntrenoContracts.kt`):
  `parseMeetDate`, `dateLabel` y `weeksUntil`. La etiqueta de `NO_DATE` es «Sí, sin fecha».
- C4b ya entrega el programa que termina la semana de la competición: los propios con `Horizon.ToMeet`, y los de autor pasan por
  `MeetRecipeFitter` (`fittedToMeet(...)` en `SetupGeneratedPlans.kt`). Tú NO cambias su forma: solo le pones las fechas y la fecha clave al activar.
- El pago de activación: `screens/onboarding/SetupActivationPayload.kt:32` (`object SetupActivationPayload`).
- `BlockTransitionEngine` propone el test de 1RM en DOS sitios: tras el bloque de realización o pico (~145-151) y tras la descarga o el taper
  posterior al pico (~158-168). Con una fecha de competición futura o de esta semana, no se propone en NINGUNO de los dos: el pico y el
  taper llevan a la competición, no a un test.
- Permiso: `MainActivity.requestWorkoutNotifications()` (~430) es el patrón de hoy; en el alta va con `rememberLauncherForActivityResult`.

## Qué hay que hacer
1. **Activación con fecha** (donde el alta arma el programa a guardar; busca el armado del pago de activación, `SetupActivationPayload`):
   - `keyDates` + `ProgramKeyDate(type = COMPETITION, title = meetName o «Competición», startDate = eventDate = fecha)`.
   - Fechas del calendario del programa como las pone hoy la edición de fechas de la app (busca `timelineStartDate`, `schedulePlan` DATED con
     `targetEndDate` y `calendarization.manualEndDate`): inicio = hoy alineado al día de inicio; fin = la semana de la competición.
   - El registro PLANNED y los recordatorios salen del guardado de siempre (comprueba con una prueba que se crean).
2. **Permiso de notificaciones** al activar con fecha: pídelo desde la pantalla del alta en ese momento (Compose:
   `rememberLauncherForActivityResult(RequestPermission)` en el anfitrión del alta, solo Android 13+ y solo si no está concedido). Si se niega,
   el alta sigue; la tarjeta de Inicio ya avisa si no hay permiso (compruébalo; si no, no lo añadas aquí).
3. **Sin test tras el taper con competición**: en `BlockTransitionEngine`, si el programa tiene una `ProgramKeyDate` COMPETITION futura o de
   esta semana, no se propone el test de 1RM tras el taper. Prueba con y sin fecha.
4. **«Sí, sin fecha»**: guarda que compite (busca un campo existente en `Settings` o en el perfil del atleta; si no hay, añade
   `competesWithoutDate: Boolean = false` en `Settings` con valor por defecto) y en Inicio, donde va la cuenta atrás, una acción «Añadir la fecha
   de tu competición» que abre la edición de fechas clave del programa activo (busca esa pantalla y su ruta; no crees otra).

## Archivos permitidos
El armado de la activación del alta (`SetupActivationPayload.kt` u homólogo), `SetupWizardHost.kt` (solo el permiso), `BlockTransitionEngine.kt`
(solo la condición del test), `data/models/Settings.kt` (solo si hace falta el campo), el resolutor y la tarjeta de competición de Inicio (solo la
acción nueva), y pruebas. Si necesitas tocar otro archivo, descríbelo en el informe.

## Pruebas
- Nueva `CompetitionActivationTest` (Robolectric con base de datos en memoria, como las pruebas de activación del alta): activar con fecha guarda la
  fecha clave COMPETITION, el registro PLANNED y el programa termina en la semana de la competición; sin fecha no crea registro y guarda que
  compite.
- `BlockTransitionEngineTest` (amplía): con competición no hay test ni tras el pico ni tras el taper; sin ella, como hoy.
- Filtro: `*CompetitionActivation*`, `*BlockTransitionEngine*`, `*HomeCompetitionResolver*`, `*CompetitionKeyDateSync*`. Antes,
  `:app:compileDebugKotlin`.
