# C4a · «¿Compites?»: paso, calendario propio y línea de bloques (punto 13, primera parte)

Worktree indicado en tu encargo, rama `v3/c4a` (sale de `feat/wizard-v3` con C1 y D2a integrados). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md` y, del plan, «Punto 13» y «§3‑bis A» (fases con fecha). El ajuste del programa a la fecha y la activación
son de C4b y C4c: aquí, datos, ruta, paso y UI.

## 1. Datos y ruta
- `enum class CompetitionAnswer { WITH_DATE, NO_DATE, NO }` (`@Serializable`, en `domain/onboarding/EntrenoContracts.kt`) y en el borrador:
  `competition: CompetitionAnswer? = null`, `meetDateIso: String? = null` (fecha local `yyyy-MM-dd`), `meetName: String = ""`.
- Paso nuevo `SetupStepId.COMPETITION` (bloque Entreno) **justo después de `GOAL`**, solo si el objetivo es Powerlifting, Halterofilia, Strongman,
  Culturismo, Armwrestling o Calistenia (Powerbuilding no tiene competiciones) y la experiencia es «Ya entreno con constancia» o «Tengo
  experiencia». `SetupStepContext.asksCompetition`.
- No subas `REVISION` (C1 ya la dejó en 5 sin versión publicada entre medias).
- Huella y vista previa: los tres campos entran en `SetupInputFootprint` (dependencia: cambian PLAN y la semana) y en `trainingKey`.
- Validación: con WITH_DATE, la fecha es obligatoria y válida (de hoy + 14 días a hoy + 18 meses); el nombre es opcional (≤ 40 caracteres).
- Resumen: «Sí · 14 de marzo de 2027 · Nacional Open», «Sí, sin fecha», «No compito».

## 2. Textos
- Título dinámico (donde se resuelven los títulos por perfil, `screens/onboarding/SetupWizardSteps.kt` `wizardPageCopy`): «¿Compites en
  powerlifting?» / «… en halterofilia?» / «… en strongman?» / «… en culturismo?» / «… en armwrestling?» / «… en calistenia?».
- Subtítulo: «Si tienes fecha, armamos tu programa para llegar en tu mejor momento.»
- Opciones: «Sí, tengo fecha» · «Compito, pero sin fecha» · «No compito».

## 3. UI (`entreno/EntrenoCompetitionStep.kt` + rama en `SetupTrainingSteps.kt`; arte en `design/entreno/CompetitionArts.kt`)
- Tres símbolos de elección única (patrón de `EquipmentSymbols.kt`, `SymbolCell`, `Role.RadioButton`). El símbolo de «Sí» y el de «Sin
  fecha» cambian según la disciplina (lienzo 64 × 64, acento `SymbolPalette.musculo`):
  - Powerlifting: tres luces de jueces que se encienden de una en una (blancas) sobre una barra cargada.
  - Halterofilia: plataforma con la barra en el suelo y la figura que la sube por encima de la cabeza.
  - Strongman: piedra atlas (círculo) que sube a una plataforma.
  - Culturismo: escenario con un foco que se abre sobre una figura de frente.
  - Armwrestling: mesa con dos antebrazos que se agarran.
  - Calistenia: barra alta con un foco.
  - «Sin fecha» usa el mismo escenario sin el destello final; «No compito», la figura entrenando sola (sin escenario), tenue.
- Con «Sí, tengo fecha», debajo (expandVertically 260 ms):
  - **Calendario de mes propio** (sin `DatePicker` de Material): cabecera «marzo de 2027» con flechas (48 dp), letras de la semana empezando por
    el día de inicio del alta (`effectiveWeekStart()`), días como números (`WizardTypography.controlValue` pequeño); fuera de rango, tenues y no
    tocables; el elegido con un círculo de tinta y el número en negro; hoy con un punto. Accesible: cada día `Role.RadioButton` con «14 de
    marzo de 2027».
  - Campo opcional «Nombre del evento (opcional)» con el estilo de los campos de texto del wizard (búscalo en Datos básicos).
  - **Línea de bloques animada**: «Faltan 14 semanas» (`WizardTypography.heroTitle` pequeño) y una barra horizontal partida en tramos según
    `PeriodLayout.phases(PERIODIZED, Horizon.ToMeet(hoy, fecha, inicio de semana), heavy)` (de D2a), con su nombre debajo de cada tramo
    («Acumulación», «Intensificación», «Pico», «Puesta a punto») y una bandera al final. Los tramos se dibujan de izquierda a derecha (900 ms,
    escalonados 120 ms); con movimiento reducido, ya dibujados. Con 1 semana o menos: «Tu competición está muy cerca: no da tiempo a un programa
    por bloques. Te propondremos una semana de puesta a punto.» (el texto del motor).
- Escribe SOLO con setters nuevos al FINAL del ViewModel: `setCompetition(answer)`, `setMeetDate(iso)`, `setMeetName(name)`.

## Archivos permitidos
`domain/onboarding/{EntrenoContracts, SetupStepGraph, SetupStepDefinitions}.kt`, `screens/onboarding/{SetupWizardModels, SetupStepAnswers,
SetupStepSummaries, SetupWizardSteps, SetupTrainingSteps}.kt`, `SetupWizardViewModel.kt` (setters al final y `trainingKey`), nuevos
`entreno/EntrenoCompetitionStep.kt` y `design/entreno/CompetitionArts.kt`, y pruebas.

## Pruebas
- `SetupStepGraphTest`: el paso aparece tras GOAL solo en las 6 disciplinas con intermedio o avanzado.
- Nueva `CompetitionStepTest` (Compose + JVM): elegir «Sí» muestra el calendario; los días fuera de rango no se pueden tocar; elegir el 14 de marzo
  llama a `setMeetDate("2027-03-14")`; la línea de bloques muestra los tramos de `PeriodLayout` para 14 semanas; la validación exige fecha con
  «Sí»; la huella cambia con la fecha.
- Hoja de render (`KPKN_RENDER_DIR=C:/kw/shots/v3/c4a`): los símbolos de las 6 disciplinas y el calendario con la línea de bloques.
- Filtro: `*SetupStepGraph*`, `*CompetitionStep*`, `*SetupStepDefinitions*`, `*SetupStepSummaries*`, `*SetupStepCopyRules*`. Antes,
  `:app:compileDebugKotlin`.
