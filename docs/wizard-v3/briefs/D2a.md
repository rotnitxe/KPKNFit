# D2a · Periodizador: programas en bloques y periodizados a partir de la semana del generador (puntos 12 y 13, motor)

Worktree indicado en tu encargo, rama `v3/d2a`. Lee antes `docs/wizard-v3/BRIEF_COMUN.md` y, del plan, «§3‑bis A» (diseño validado) y
«Punto 12». Paquete de dominio puro (sin `android.*`, sin UI, sin catálogo de programas: eso es D2b/D2c).

## Contexto (verificado)
- `RoutineGenerator.generate(request)` (`domain/training/generator/RoutineGenerator.kt`) devuelve un programa SIMPLE de una sola semana
  («Semana repetible», bloque «Semana cíclica», ~102-140) y hay pruebas que fijan esa forma: **no lo cambies**.
- Un programa con varios bloques debe ser **COMPLEX** con un único macrociclo (`domain/training/ProgramHierarchyIndex.kt:122-125`); el orden
  de fases dentro del macrociclo es monótono (Acumulación < Intensificación < Especificidad < Realización/Pico; Descarga y Taper exentos,
  `:160-190`) y toda semana no REST necesita una sesión ejecutable (`:196-208`). `Block.goal` usa `BlockGoal` (`data/models/Program.kt:373-384`).
- **No** dejes las semanas futuras para `BlockTransitionEngine.applyProgression`: reescribe en RPE y deja RIR y kilos desfasados
  (`BlockTransitionEngine.kt:199-206`, `BlockProgressionEngine.kt:286-300`). Materializa TODAS las semanas de entrada.
- Ids: `SessionAssembler.kt:152` hace `"${programId}-s$i"`, que se repetiría entre semanas; `ProgramStructureContract` rechaza ids duplicados.
- Utilidades a reutilizar: `Prescriber.percentOfOneRm(repMax, rir)` (`generator/Prescriptions.kt:107-115`), `TimeModel.sessionSeconds`
  (`generator/TimeModel.kt:104`) / `SessionDurationEstimator`, `GenContext.windowMinutes` (`GenContext.kt:29-33`), `VolumeBudgets`
  (`VolumeBudgets.kt:48`), `BlockProgressionEngine.intensityRangeFor` (`BlockProgressionEngine.kt:37-50`), constantes de
  `PeriodizationEngine.kt:22-50`.

## Qué hay que hacer (paquete nuevo `domain/training/periodization/`)
```kotlin
enum class ProgramVariant { SIMPLE, BLOCKS, PERIODIZED }
enum class PhaseKind { ACCUMULATION, INTENSIFICATION, REALIZATION, PEAK, DELOAD, TAPER, TEST }
data class PhaseSpan(val kind: PhaseKind, val weeks: Int)
sealed interface Horizon {
    data object Simple : Horizon
    data class Weeks(val weeks: Int) : Horizon
    data class ToMeet(val anchor: java.time.LocalDate, val meetDate: java.time.LocalDate, val weekStartDay: Int) : Horizon
}
object PeriodLayout { fun phases(variant: ProgramVariant, horizon: Horizon, heavy: Boolean): List<PhaseSpan> }
data class PeriodizedRoutine(val program: Program, val phases: List<PhaseSpan>, val notes: List<String>)
object RoutinePeriodizer { fun build(request: RoutineRequest, base: Program, variant: ProgramVariant, horizon: Horizon): PeriodizedRoutine }
```
(ajusta nombres de tipos a los reales: `base` es el `Program` que devuelve el generador; mira su tipo de retorno).
1. **Fases** (`PeriodLayout`):
   - BLOCKS sin fecha (6 sem): Acumulación 3 · Intensificación 2 · Descarga 1 (en general para W de 4 a 8: Acum ⌈(W−1)/2⌉ · Int ⌊(W−1)/2⌋ · Desc 1).
   - PERIODIZED sin fecha: 10 sem = Acum 4 · Int 2 · Real 2 · Desc 1 · Test 1; **12 sem = Acum 5 · Int 3 · Real 2 · Desc 1 · Test 1**;
     16 sem = Acum 7 · Int 4 · Real 3 · Desc 1 · Test 1.
   - Con fecha (W = semanas desde la semana del ancla hasta la semana de la competición, alineando con `weekStartDay`): BLOCKS (W 4–8) =
     Acum ⌈(W−1)/2⌉ · Int ⌊(W−1)/2⌋ (la última de Int es PEAK) · Taper 1; PERIODIZED (W 8–16) = cabeza W − 2 − t (Int = máx(2, round(cabeza/3)),
     Acum = resto) · Pico 2 · Taper t (t = 2 si `heavy` y W ≥ 10, si no 1); W = 2 → Pico 1 · Taper 1; W = 3 → Int 1 · Pico 1 · Taper 1;
     W ≤ 1 → lista vacía (el llamador muestra el aviso; nota «Tu competición está demasiado cerca para un programa por bloques.»).
   - Si la Acumulación pasa de 6 semanas, añade una Descarga en medio.
2. **Reglas por semana** sobre cada ejercicio de la semana base (series S, repeticiones R, RIR r):
   - Series: `clamp(round(S·m), 1, S+1)` con m = Acum 1,15 · Int 1,0 · Real/Pico 0,8 · Desc/Taper 0,55 · Test: solo los principales a 1 serie.
   - Repeticiones: Acum +2 · Int 0 · Real/Pico −2 (mín. 1) · Desc/Taper −1.
   - RIR: Acum +1 (tope 3) · Int 0 · Real/Pico 1 · Desc/Taper +2 (tope 4); mínimo por nivel: principiante y quien vuelve ≥ 2 en Real/Pico,
     intermedio ≥ 1, avanzado ≥ 0.
   - Carga: si el ejercicio tiene `reference1RM`, `pct = Prescriber.percentOfOneRm(reps', rir')` y `weight = redondeo(ref·pct/100)` con el
     redondeo que ya usa el generador; si no, sin kilos (modo RIR).
   - Por grupo de disciplina (`request.mode`): pesadas (powerlifting, halterofilia, strongman) quitan accesorios en Real/Pico y dejan solo los
     principales en Taper; culturismo sube repeticiones con tope 15 y no tiene Real; calistenia y armwrestling solo cambian series (nunca el
     peldaño ni las repeticiones); funcional no tiene Real (una semana más de Int con RIR 1); híbrido reduce el cardio a la mitad en
     Descarga y Taper.
3. **Minutos:** mide cada sesión con el estimador común y, si pasa de la ventana del pedido, quita una serie al último accesorio o
   aislamiento que esté por encima de su mínimo; si queda corta, añade una serie a un principal hasta su máximo. Máximo 20 correcciones por
   sesión. Nunca añadas ejercicios.
4. **Volumen:** comprueba cada semana contra el techo de `VolumeBudgets`; si lo pasa, recorta series de accesorios de ese músculo.
5. **Estructura del programa:** COMPLEX, un macrociclo, un bloque por fase con `Block.goal` mapeado (Acum→ACCUMULATION, Int→INTENSIFICATION,
   Real→REALIZATION, Pico→PEAK, Desc→DELOAD, Taper→TAPER, Test→REALIZATION con el test), ids deterministas `"<programId>-w<n>"` para semanas y
   el sufijo `-w<n>` en sesiones, ejercicios y series; el id del programa lleva el sufijo de la variante (`-blocks`, `-periodized`).
   `ProgramVariant.SIMPLE` devuelve la base sin tocar.
6. Determinista: misma entrada → mismo programa. La fecha de anclaje llega en `Horizon` (nunca `LocalDate.now()`).

## Archivos permitidos
Nuevos archivos en `domain/training/periodization/` y sus pruebas. Si necesitas exponer algo `private` del generador (p. ej. el redondeo de
carga), hazlo `internal` sin cambiar su comportamiento y dilo en el informe.

## Pruebas (JVM, rápidas)
Nueva `RoutinePeriodizerTest` con semanas base reales del generador (reutiliza cómo construyen el pedido y el catálogo
`RoutineGeneratorBehaviorTest`/`RoutineGeneratorLowMinutesTest`; búscalo con Grep) para 3 modos (fuerza y masa, culturismo, powerlifting):
1. SIMPLE devuelve la base idéntica.
2. BLOCKS y PERIODIZED son COMPLEX, con un macrociclo, y pasan el contrato de ejecución del programa (`ProgramExecutionContract` o
   `ProgramStructureContract`; búscalo).
3. Fases monótonas (Descarga/Taper exentas).
4. Semanas totales = las de las fases; con fecha, la competición cae en la última semana.
5. Ids únicos en semanas, sesiones, ejercicios y series; misma entrada → programa igual.
6. Las fases no dependen de las marcas.
7. Cada serie lleva kilos solo si hay `reference1RM`, y RIR siempre.
8. Las sesiones caben en la ventana de minutos o quedan anotadas.
9. Con fecha a 12, 8, 5, 3 y 1 semanas: fases esperadas (la de 1 semana, vacía con nota).
Filtro: `*RoutinePeriodizer*` y `*PeriodLayout*`. Antes, `:app:compileBaseDebugKotlin`.
