# LV10b · Estado previo (G1) y el ajuste manual OFICIAL de los anillos (G5, G6)

Rama `screens/lv10b`, worktree y commit base: los del mensaje de lanzamiento. Lee antes `LV_COMUN.md`, `LV_COMUN2.md`, el mapa
`docs/screens-v1/maps/MAPA_anillos_estado_fichas.md` (A2, A3, B y riesgos) y los prototipos aprobados `G1-EstadoPrevio`,
`G5-AjusteEnergia`, `G6-AjusteMusculos` (mira sus `<script>`).

Decisiones del usuario (16-oct): el estado previo NO omite molestias ni los botones de voz; el ajuste manual animado y moderno de
G5/G6 (músculos especialmente creativo) es el ajuste OFICIAL en toda la app.

## 1. El ajuste oficial (componentes reutilizables)
Archivo nuevo `ui/components/rings/RingAdjust.kt` (o carpeta `rings/adjust/`), sin lógica de guardado dentro: reciben valores
visibles 0–100 y devuelven cambios por callback. La pantalla que los usa decide qué guarda (y la Columna se sigue invirtiendo SOLO
donde ya se invierte hoy).
- **`EnergyColumnAdjust` (G5):** anillo grande (≈ 280 dp, adaptado a 360 dp de ancho) con el arco luminoso del canal (degradado y
  halo), dentro un «líquido» del color del canal que sube hasta el valor con dos ondas que se desplazan; nombre del canal, «72 %»
  grande y «antes 88 %» (el valor automático o el de partida); una marca tenue en el anillo para «antes». **Tocar o arrastrar
  alrededor del anillo** fija el valor (ángulo → 0–100, con paso de 1 y háptica cada 5); −/+ de 5; la palabra («Con mucha energía»
  / «Bien, sin más» / «Algo cansado» / «Muy cansado»; para Columna «Espalda descansada» / «Algo cargada» / «Cargada» / «Muy
  cargada»); «Restablecer». Accesible como slider (`setProgress`).
- **`MuscleBodyAdjust` (G6):** cuerpo de frente y de espalda (segmentado «Frente · Espalda») dibujado en `Canvas` con una silueta
  limpia y proporcionada (mejor que el boceto del prototipo: cabeza, cuello, tronco, brazos y piernas con volumen), y las zonas de
  los **13 pilares** como formas: frente Deltoides, Pectorales, Bíceps, Abdomen, Core (oblicuos), Cuádriceps, Pantorrillas;
  espalda Trapecio, Dorsales, Tríceps, Erectores Espinales, Glúteos, Isquiosurales, Pantorrillas. Cada zona se pinta con su valor
  (≥ 80 verde `#43D18C`, ≥ 60 ámbar `#F7CF73`, si no `#F49A6E`), la elegida late suave y con borde de tinta. Debajo, la zona
  elegida con un mini-anillo, «Cuádriceps · 54 % · cargado» y −/+ de 10 (y arrastre horizontal sobre la fila para afinar).
  «Restablecer». **Sin los atajos «Todo fresco»/«Pierna cargada»** del prototipo: no existen en la app (ley 5). Zonas táctiles
  ≥ 48 dp (amplía el área si la forma es pequeña). TalkBack: cada zona es un botón «Cuádriceps 54 %».
- **`RingAdjustSheet`:** `KpknSheet` con pestañas «Energía · Columna · Músculos» que muestra el componente que toca, abierta en el
  canal pedido, con «Listo». Úsala donde haga falta una hoja.

## 2. El estado previo (G1)
`WorkoutReadinessSheet.kt` con el aspecto de G1 y TODO el contenido de hoy:
- título, la torre luminosa (tal como venga de `KpknTowerRings`), la leyenda de tres columnas y «Toca un anillo para ajustarlo»;
- **tocar un anillo abre `RingAdjustSheet`** en ese canal (sustituye el popup `ReadinessAdjustCard`); en Músculos se marcan
  primero los músculos de la sesión (los de hoy, `sessionMuscleStartingBatteries`), y se pueden tocar todos;
- lo que se ajusta va al MISMO estado de la hoja de hoy (`neural`, `spinal`, `muscleAdjustments`, `userEdited*`) y al guardar se
  sigue la MISMA ruta (`onSave` → `WorkoutSessionOverlaysHost` → `saveWellbeing` + `saveReadinessAdjustments`). Solo los canales
  editados se convierten en ajustes manuales; `manualMuscular` sigue en null;
- **molestias en línea** (no plegadas): «¿ALGUNA MOLESTIA HOY?», buscador y chips «Ninguna», las reportadas antes («Rodilla izq. ·
  lunes») y las sugeridas por `DiscomfortSuggestionEngine` (el contenido de `PreWorkoutDiscomforts` de hoy, con el aspecto de G1);
- **voz en una fila «VOZ»** con los controles reales de la app: una píldora «Comandos de voz» que enciende o apaga
  (`onVoiceToggle`) y, encendida, las dos píldoras de modo «Manos libres · Música» (`onVoiceCaptureModeChange`), más el (i) que
  abre la explicación de hoy;
- el consejo de patrón y el aviso AUGE se conservan, compactos;
- el ✓ de 64 dp abajo en el centro; el cierre con Atrás/fuera de LV4 (`onDismiss`) se conserva.

## 3. El ajuste oficial en el resto de la app
- **Inicio → `RingDetailSheet`**: la sección «¿TE SIENTES DISTINTO?» abre `RingAdjustSheet` en ese canal (o lo muestra en línea si
  cabe mejor) en lugar del slider; «Guardar ajuste» solo si cambió. Energía y Columna se guardan como hoy (`applyManualBatteries`).
  Músculos: si la persona ajusta músculos sueltos, guárdalos como anclas V2 por músculo con la misma ruta que usa el final del
  entreno (`applyManualBatteries(perMuscle…)`, claves de pilar); si no es posible sin tocar el motor, ajusta solo el global como
  hoy y anótalo.
- **Final del entreno**: NO lo toques (lo hace LV13 con tus componentes). Deja los componentes listos y documentados.
- **Wizard (`RingsResultStep.kt`)**: si encaja sin romper su contrato (página larga, sin tarjetas, `SetupWizardViewModel.
  setRingsManual`/`setMuscleManual`/`resetMuscleManual`), usa `EnergyColumnAdjust` y `MuscleBodyAdjust` en línea; si no encaja,
  anótalo y no lo fuerces.

## Lo que NO se puede romper
Columna invertida solo donde se invierte hoy; claves de pilar; V1/V2 y `mergeWellbeingForSave`; el estado previo vuelve a salir si
`readinessNeuralOverride` queda null; las pruebas de Inicio (`HomeRingsSectionTest`, `RingDetailSheetTest`: «Guardar» solo si
cambió, rejilla solo en Músculos) y `WorkoutReadinessSheetCompactTest`.

## Pruebas
- Nuevas: ángulo → valor y valor → ángulo (puro); −/+ con límites; zona del cuerpo → pilar; que el estado previo guarda solo lo
  editado y por la misma ruta; Inicio guarda Energía/Columna (y músculos si aplica); molestias y voz presentes en el estado previo.
- Hoja de render: G5 Energía y Columna (dos valores, con «antes»), G6 frente y espalda con una zona elegida, el estado previo
  entero (360 dp, 100 % y 130 %), y el detalle de Inicio con el ajuste.

## Permitido y prohibido
- **Permitido:** `ui/components/rings/RingAdjust*.kt` (nuevos), `WorkoutReadinessSheet.kt`, `WorkoutSessionOverlaysHost.kt` (solo
  el estado previo), `screens/home/RingDetailSheet.kt` y `HomeScreen.kt` (solo el ajuste), `screens/onboarding/rings/
  RingsResultStep.kt` (si encaja), `AugeViewModel` solo si hace falta una función de guardado por músculo ya soportada por el
  modelo, pruebas.
- **Prohibido:** `KpknTowerRings.kt` y `HomeRingsSection.kt` (LV10a), `WorkoutFinishHost.kt` (LV13), el descanso, la tarjeta, el
  relator, el motor AUGE.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lv10b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.screens.home.*" --tests "com.example.kpkn.screens.auge.*" --tests "com.example.kpkn.screens.onboarding.rings.*" --tests "*RingAdjust*" > /c/kw/logs/lv10b-<n>.log 2>&1`

Tope: 5 corridas.

## Commits
- `feat(rings): el ajuste manual oficial de Energía, Columna y Músculos (LV10b)`.
- `feat(workout): el estado previo con anillos luminosos, molestias y voz a la vista, y el ajuste oficial (LV10b)`.
- `feat(home): el detalle de un anillo se ajusta con el ajuste oficial (LV10b)`.
