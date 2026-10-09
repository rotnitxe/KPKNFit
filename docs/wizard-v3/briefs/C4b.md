# C4b · El programa llega a la fecha de la competición (punto 13, motor; §3‑bis A «Competición en planes de autor»)

Worktree indicado en tu encargo, rama `v3/c4b` (sale de `feat/wizard-v3` con C4a, D2a y D2b integrados). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md`, del plan «Punto 13» y «§3‑bis A», y los briefs `D2a.md`, `D2b.md` y `C4a.md`.

## API real (integrado: C4a, D2a, D2b)
- Lee la competición SOLO con los helpers de C4a (`screens/onboarding/SetupWizardModels.kt`): `draft.activeCompetition(): CompetitionAnswer?`,
  `draft.activeMeetDate(): String?` (ISO `yyyy-MM-dd`) y `draft.activeMeetName()`. Los campos crudos (`competition`, `meetDateIso`) pueden
  quedar en el borrador aunque el paso haya salido de la ruta. Utilidades en `domain/onboarding/EntrenoContracts.kt`: `CompetitionRules.parseMeetDate(iso)`,
  `CompetitionRules.heavy`, `CompetitionRules.weeksUntil(today, meet, weekStartDay)` y `CompetitionRules.dateLabel(date)` («14 de marzo de 2027»).
- `trainingKey` ya incluye la competición y la fecha: la vista previa se recalcula sola al cambiarlas.
- Variante propia: `generatedVariantOf(routine, entry, draft, request)` en `screens/onboarding/SetupGeneratedPlans.kt:126`. Hoy hace
  `Horizon.Weeks(entry.durationWeeks)` (~134) y `RoutinePeriodizer.build(request, base, variant, horizon)`. `Horizon.ToMeet(anchor, meetDate,
  weekStartDay)` está en `domain/training/periodization/PeriodizationTypes.kt:31`.
- «Hoy»: el ViewModel ya usa `LocalDate.now()` (~1250, ~3395). Pásalo como parámetro (`today: LocalDate`) hasta `generatedVariantOf`, para
  que las pruebas lo fijen. Nada de `LocalDate.now()` en `domain/`.

## 0. Arreglos heredados de C4a (primero; son de una línea)
- `app/src/debug/.../debug/WizardHarnessDrafts.kt`, en `answeredAs`: `SetupStepId.COMPETITION -> withCompetition(CompetitionAnswer.NO)`. La
  persona de powerlifting intermedio ahora pasa por el paso y, sin esto, se queda sin responder.
- `EntrenoContracts.kt`: la etiqueta de `CompetitionAnswer.NO_DATE` pasa a «Sí, sin fecha» (hoy «Compito, pero sin fecha» se corta a 320 dp
  con la letra al 130 %, y el resumen ya dice «Sí, sin fecha»).
- Añade al filtro `*WizardHarnessDraftsTest*` y `*CompetitionStep*`. Si en `WizardHarnessDraftsTest` solo falla la expectativa de marcas de la
  persona QA (falta `OVERHEAD_PRESS`), es conocido: lo arregla otro paquete (E4). Anótalo y no lo toques.

## 1. Programas propios (generador + periodizador)
- **Quien compite con fecha no ve la versión simple** (no tiene pico; plan §3‑bis A): con `activeCompetition() == WITH_DATE`, la oferta de PLAN
  de los programas propios es solo `:blocks` y `:periodized`, ajustados a la fecha. Busca dónde D2c arma la lista de ids por perfil y filtra ahí.
- Con `draft.activeCompetition() == WITH_DATE` y una fecha válida (`activeMeetDate()`), materializar `generated:<perfil>:blocks` o `:periodized` usa
  `Horizon.ToMeet(ancla, fecha, effectiveWeekStart())` en lugar de `Horizon.Weeks(6|12)` (en `generatedVariantProgram` de D2b). El ancla es la
  fecha de hoy del reloj de la app (busca `AppClock` o el equivalente que ya use el ViewModel; nunca `LocalDate.now()` dentro del dominio).
- Si `PeriodLayout` devuelve vacío (competición a ≤ 1 semana), el programa es la semana simple con la nota «Tu competición está muy cerca: no da
  tiempo a un programa por bloques.»

## 2. Planes de autor · `domain/training/periodization/MeetRecipeFitter.kt` (nuevo, puro)
- `fit(program: Program, meet: LocalDate, anchor: LocalDate, weekStartDay: Int): MeetFit` con `MeetFit.Fitted(program, notes)` o
  `MeetFit.NotFittable(reason)`.
- Aptos (búscalos por su id de catálogo y su receta): `kpkn-native-sbd-4`, `candito-6`, `cube-method`, `calgary-16`; comprueba la cola de `tsa-9` y
  `gzcl-uhf-9` (si acaban en pico o taper, aptos; si no, no) y anótalo en el informe.
- Regla: la cola (las semanas desde la primera de realización, pico, taper o descarga final) queda fija y termina la semana de la competición.
  - Falta tiempo: se quitan semanas de la cabeza, primero de acumulación, dejando al menos una de intensificación; si ni así cabe →
    `NotFittable("Este programa necesita al menos N semanas hasta tu competición.")`.
  - Sobra tiempo: se repite la última semana de acumulación (con su propio escalón de carga, sin copiar ids: sufijo `-r<n>`) y se añade una
    descarga cada 6 semanas.
  - Valida el resultado con el contrato de ejecución del programa (`hardFindings`/`requireExecutable`; búscalos) y marca el nombre o la ficha
    como «adaptado a tu competición» (usa el campo de procedencia que exista; dilo en el informe).
- No aptos (ciclos que se repiten: Madcow, nSuns, GZCLP, Westside, Texas, PHUL, PHAT; sin pico: Smolov Jr, PPL, estilo RP): `NotFittable` con
  «Este programa se repite en ciclos y no tiene pico: no se ajusta a tu fecha.»
- Donde el ViewModel materializa los planes de autor (busca dónde se construye el programa de una receta o protocolo; hoy fija `startDay` hacia
  `SetupWizardViewModel.kt:3115` y `:3195`), con competición y fecha se pasa el programa por el ajustador; si no es apto, el plan se ofrece igual con
  su nota (no se descarta).

## 3. Lo que ve la persona en PLAN
- La portada o el detalle del programa ajustado dice «Termina el 14 de marzo, el día de tu competición» (o la nota de no apto). Busca dónde se
  pintan las notas del programa en el detalle (`PlanDetailOverlay`) y en la portada (`PlanCover`/`SetupPlanReveals`), y añade la línea por el
  camino de notas que ya exista.

## Archivos permitidos
Nuevo `domain/training/periodization/MeetRecipeFitter.kt`; `screens/onboarding/SetupGeneratedPlans.kt`; `SetupWizardViewModel.kt` (solo la
materialización de planes de autor, la llamada al ajustador, el paso de `today` y el filtro de la versión simple); `screens/onboarding/SetupPlanReveals.kt` y
`design/entreno/plan/PlanDetailOverlay.kt` (solo la línea de la fecha); los dos arreglos heredados (`WizardHarnessDrafts.kt`, la etiqueta en
`EntrenoContracts.kt`); y pruebas.

## Pruebas (JVM)
- Nueva `MeetRecipeFitterTest`: con un plan apto de 9 semanas y la competición a 12, 9, 6 y 3 semanas: la última semana es la de la competición,
  la cola es la original, ids únicos, contrato de ejecución sin hallazgos duros; a 3 semanas de un plan que necesita 5, `NotFittable` con el
  texto; un plan que se repite da `NotFittable` con su texto.
- `GeneratedVariantProgramTest` (de D2b): con fecha a 10 semanas, el programa periodizado tiene 10 semanas y termina en la de la competición.
- Filtro: `*MeetRecipeFitter*`, `*GeneratedVariantProgram*`, `*RoutinePeriodizer*`, `*SetupPlanReveals*`. Antes, `:app:compileDebugKotlin`.
