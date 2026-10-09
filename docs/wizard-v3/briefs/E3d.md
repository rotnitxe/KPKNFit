# E3d · Pantallas de las preguntas de RINGS: calendario, ejercicios, series por grupo, intensidad y sensaciones (punto 21)

Worktree indicado en tu encargo, rama `v3/e3d` (sale de `feat/wizard-v3` con E3b y C1b integrados). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md`, del plan «Punto 21» y el brief `E3b.md` (datos, reductores y setters ya existen; aquí solo la UI).
Regla del usuario: **símbolos, nunca tarjetas** (hoy estas preguntas usan `SetupBodyChoiceCards`: se sustituyen).

## Lo que ya existe (léelo antes)
- Enganche: `screens/onboarding/SetupRingsSteps.kt:52-74` (`SetupRingsStepContent`); E3b dejó ramas vacías para los ids nuevos.
- Kit de símbolos: `design/entreno/EntrenoSymbolKit.kt` y `EntrenoSymbolRuntime.kt` (`SymbolCell`, `rememberSymbolClock`), cuadrícula de
  referencia `design/entreno/EquipmentSymbols.kt:59-`; pictogramas `design/entreno/plan/ExercisePatterns.kt` (`patternArt`); arte de carrera e
  intervalos de C1c en `design/entreno/StartingPointArts.kt`; fila de días `design/entreno/WeekDayRow.kt` / `WeekCalendar.kt` y glifos
  `WeekGlyphs.kt`; músculos `design/entreno/MuscleArt.kt`; regla de enteros `design/entreno/CountRuler.kt`; torre `ui/components/rings/`.
- Setters de E3b (busca los nombres exactos): días, «No entrené», «No lo recuerdo», ejercicios, series por grupo, sensaciones.

## Pantallas (un archivo por paso en `screens/onboarding/rings/`, firma `(state, vm)`)
1. `RINGS_SESSION_DAYS` · `RingsSessionDaysStep.kt`
   - Fila de los 7 días previos, de hace 6 días a hoy (izquierda → derecha), con el lenguaje de la fila de días del Entreno: letra del día
     arriba y debajo «Hoy», «Ayer» o el número («7»); selección múltiple con el relleno y el check de esa fila. `Role.Checkbox`,
     descripción «Martes 7, entrenado».
   - Debajo, dos símbolos exclusivos: «No entrené» (figura sentada descansando) y «No lo recuerdo» (calendario con un signo de interrogación
     trazado). Elegir uno vacía los días; tocar un día los apaga.
   - Nota: «Cuentan los últimos 7 días: lo anterior ya casi no pesa en tus RINGS.»
2. `RINGS_MAIN_LIFTS` · `RingsMainLiftsStep.kt`
   - Cuadrícula de símbolos (selección múltiple): Sentadilla (SQUAT), Peso muerto (HINGE), Rumano o remo (ROW), Press banca (PRESS_HORIZONTAL),
     Press militar (PRESS_VERTICAL), Dominadas (PULL_VERTICAL), Zancadas (LUNGE), Cardio largo (correr de C1c), Intervalos (series rápidas de
     C1c), y dos exclusivos: «Ninguno de estos» y «No lo sé». Los pictogramas estáticos se iluminan con su acento al elegirlos.
3. `RINGS_GROUP_SETS` · `RingsGroupSetsStep.kt`
   - Encima: «Desde el <día>» con el día más antiguo entrenado de las últimas 72 h («Desde ayer» si es ayer, «Desde hoy» si solo hoy).
   - Seis filas (Pecho, Espalda, Hombros, Brazos, Piernas, Core), separadas por el filete de 1 dp: a la izquierda el músculo de `MuscleArt`
     (Pecho → pecho, Espalda → espalda, Hombros → hombros, Brazos → bíceps, Piernas → cuádriceps, Core → abdomen; 56 dp, estático), el nombre
     y una `CountRuler(0..20, «series»)`; al lado, «No lo sé» como interruptor de texto (escribe `null`). Solo una regla desplegada a la vez
     (como las marcas); las plegadas muestran «12 series» o «No lo sé».
4. `RINGS_INTENSITY` (sustituye a la tarjeta de hoy, misma rama)
   - Escala de 4 símbolos de elección única: Suave, Moderada, Dura, Muy dura. Cada símbolo: una barra con discos que se dobla más según la
     intensidad (0°, 4°, 9°, 15° de flexión) y, en «Muy dura», unas gotas de esfuerzo; acento `SymbolPalette.energia`.
5. `RINGS_FEELINGS` · `RingsFeelingsStep.kt`
   - Tres filas (Músculos, Energía, Columna) en ese orden, cada una con una escala de 5 símbolos de elección única: el anillo de ese canal de la
     torre (su color de marca) lleno al 100, 75, 50, 30 y 10 %. Etiquetas bajo el elegido (una sola visible por fila, `WizardTypography.note`):
     - Músculos: «Como nuevos» · «Bien» · «Normales» · «Cargados» · «Muy cargados».
     - Energía: «De sobra» · «Bien» · «Normal» · «Baja» · «Agotada».
     - Columna: «Perfecta» · «Bien» · «Normal» · «Cargada» · «Con molestias».
     (1 = el mejor estado, como el dato de hoy: `sensationPenalty(1) = 0`.)
   - Cada fila tiene «Prefiero no decirlo» (texto, deja ese canal sin responder).
- Accesibilidad: cada escala como grupo con `selectableGroup`; cada opción `Role.RadioButton` con «Músculos: Cargados»; objetivos ≥ 48 dp.
- Movimiento: solo el símbolo elegido se anima; reloj compartido; con movimiento reducido, cuadro final.

## Archivos permitidos
Nuevos en `screens/onboarding/rings/` y su arte en `design/entreno/RingsQuestionArts.kt`; `SetupRingsSteps.kt` (solo las ramas de estos
pasos y borrar los `Rings*Content` que queden sin uso); y pruebas. Nada de dominio ni del ViewModel.

## Pruebas
- Nueva `RingsQuestionStepsTest` (Compose): los días y los dos exclusivos; la cuadrícula de ejercicios con sus exclusivos; la regla de series de
  Piernas escribe el setter; «No lo sé» escribe `null`; la escala de Energía es de elección única; «Prefiero no decirlo» deja el canal vacío.
- Hoja de render (`KPKN_RENDER_DIR=C:/kw/shots/v3/e3d`): las cinco pantallas con algo elegido.
- Filtro: `*RingsQuestionSteps*`, `*SetupRings*`, `*EntrenoAccessibility*`. Antes, `:app:compileDebugKotlin`.
