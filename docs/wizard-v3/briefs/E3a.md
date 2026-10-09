# E3a · Evidencia v2 de los RINGS del alta (motor puro; puntos 20–22, §3‑bis C)

Worktree indicado en tu encargo, rama `v3/e3a`. Lee antes `docs/wizard-v3/BRIEF_COMUN.md` y, del plan, «Punto 21» y «§3‑bis C».
Solo `data/models` y `domain/auge`: nada de UI, ruta ni ViewModel (eso es E3b). Kotlin puro y determinista (la hora entra como
parámetro; nada de `System.currentTimeMillis()` dentro de las funciones nuevas).

## Por qué (defectos verificados)
- `InitialRecoveryEvidenceFactory.fromInputs` (`data/models/InitialRecoveryEvidence.kt:86-171`) aplica UNA carga a todos los músculos
  y la columna sale 100 si no se declara carga axial (dice «todo bien» cuando no se sabe nada).
- `InitialRecoveryEvidencePolicy.resolve` (`domain/auge/InitialRecoveryEvidencePolicy.kt:36-89`) recupera con τ fijo de 72 h para los
  tres canales; el motor real usa curvas distintas por canal (`AugeUtils.recoveryDecay` ~39, `getSigmoidalHours` ~136,
  `getSpinalRecoveryHours` ~149; constantes de canal C = 90 músculos, 75 energía, 70 columna: `AugeRecoveryEngine.kt:523, 653, 794,
  885, 948, 1095`). Ejemplo: una deuda de 40 puntos de energía, 36 h después, da 75,7 con la ley vieja frente a 97,5 con la del motor.
- `perMuscleScores` se escribe pero ningún cálculo lo lee (`getPerMuscleBatteries`, `AugeRecoveryEngine.kt:1260-`).

## 1. Datos nuevos (en `data/models/InitialRecoveryEvidence.kt`, `@Serializable`, todo con valor por defecto)
- `enum class RingsLiftPattern { SQUAT, DEADLIFT, HINGE_ROW, BENCH, OVERHEAD, PULL_UP, LUNGE, CARDIO_LONG, INTERVALS }`
  (sentadilla, peso muerto, rumano o remo, press banca, press militar, dominadas, zancadas, cardio largo, intervalos).
- `enum class RingsMuscleGroup(val label) { CHEST «Pecho», BACK «Espalda», SHOULDERS «Hombros», ARMS «Brazos», LEGS «Piernas», CORE «Core» }`.
- `data class InitialRecoveryDayLoad(val dayStartMs: Long, val muscular: Map<String, Double>, val energy: Double, val spinal: Double)`:
  la carga de un día entrenado (unidades del motor), por pilar muscular.
- En `InitialRecoveryEvidence`: `dayLoads: List<InitialRecoveryDayLoad> = emptyList()`, `structureKnown: Boolean = true` y
  `muscleGeneric: Boolean = false`. `estimatorVersion` sigue en 1 por defecto: los borradores y ajustes guardados se leen igual.

## 2. `InitialRecoveryEvidenceFactory.fromInputsV2(...)` (nuevo; `fromInputs` no cambia)
Entradas: `capturedAtMs`, `zone` (`ZoneId`), `trainedDays: Set<Int>` (días hacia atrás: 0 = hoy … 6), `intensity`, `lifts: Set<RingsLiftPattern>`,
`groupSets: Map<RingsMuscleGroup, Int>` (solo los grupos con número; «No lo sé» no viene), `sensations`, `sourceId`.
- Cada día entrenado se fecha a las **12:00** de ese día en `zone` (supuesto rotulado en KDoc).
- Músculos: con series por grupo, carga del pilar = series × peso del grupo en el pilar × carga por serie según intensidad; reparto
  grupo → pilares (los 13 de `INITIAL_RECOVERY_MUSCLE_PILLARS`): Pecho → Pectorales 0,8 · Deltoides 0,1 · Tríceps 0,1; Espalda →
  Dorsales 0,5 · Trapecio 0,3 · Erectores Espinales 0,2; Hombros → Deltoides 0,8 · Trapecio 0,2; Brazos → Bíceps 0,5 · Tríceps 0,5;
  Piernas → Cuádriceps 0,4 · Isquiosurales 0,25 · Glúteos 0,25 · Pantorrillas 0,1; Core → Abdomen 0,6 · Core 0,4. Las series de un día
  sin dato por grupo se reparten entre los días declarados de las últimas 72 h por igual.
  Sin series por grupo: un único valor global (`muscleGeneric = true`) a partir de la intensidad y de los patrones elegidos.
- Energía: la tabla de intensidad de `fromInputs` (3 / 8 / 16 / 24) por día entrenado, ×1,3 si hay INTERVALS y ×0,8 si solo hay fuerza.
- Columna: solo con patrones axiales declarados (SQUAT y DEADLIFT alto; HINGE_ROW moderado; LUNGE bajo). Sin patrones axiales y sin
  sensación de columna: `structureKnown = false`.
- Constantes por serie: elige valores que, para una sesión de referencia (5 series de sentadilla + 4 de rumano a intensidad HARD, ayer),
  den un descenso de Cuádriceps comparable al que calcula el motor con `AugeFatigueEngine.calculateSetBatteryDrain` (~412) para esas
  series. Documenta en KDoc de dónde sale cada número. Si no consigues comparar con el motor sin montar media app, fija los valores y
  anótalo en el informe (no inventes una calibración que no hiciste).
- Puntuaciones del alta (`muscularScore`, `systemScore`, `structureScore`, `perMuscleScores`) = estado A LA HORA de captura, calculado
  con las mismas curvas que el §3. `estimatorVersion = 2`, `contractRevision = "initial-recovery-v3"`.
- Sin días entrenados («No entrené»): cargas vacías y puntuaciones 100 (eso sí es un dato). «No lo recuerdo» no es tarea de esta
  función: quien llama no fabrica evidencia (como hoy en `SetupRingsMapper`).

## 3. Ley v2 en `InitialRecoveryEvidencePolicy.resolve`
- Con `estimatorVersion >= 2` y `dayLoads` no vacío, cada canal se calcula en `nowMs` desde las cargas, con las curvas del motor:
  `B = 100 · exp(−Σ carga_i · d(now − t_i) / C)`, donde `d` es la fracción que queda (`AugeUtils.recoveryDecay` con las horas efectivas
  del canal: músculos `getSigmoidalHours` y el τ de su perfil, energía τ = 36 h, columna `getSpinalRecoveryHours` y τ = 52 h) y C es
  la constante del canal (90 / 75 / 70). Busca en el motor los τ reales antes de escribirlos.
- Con un ajuste manual (`overrides`), el canal sigue la ley de ancla del motor: `B(t) = 100 · (B₀ / 100)^d(Δt)`.
- `structureKnown == false` y sin ajuste de columna → `structure = null` (la UI dirá «Sin calibrar»).
- `estimatorVersion == 1`: exactamente la ley de hoy (τ = 72 h). No cambies su resultado.

## 4. Músculos en `AugeRecoveryEngine.getPerMuscleBatteries` (~1260)
- Solo con evidencia v2 no caducada: cada pilar con carga v2 resta su parte (la misma combinación que `getGlobalBatteries` usa para el
  global, ~1239-1240: `real − (100 − inicial)`, con su suelo).
- Los días que ya tienen registros reales de entrenamiento se descartan de la evidencia, comparando **fechas** (día local), no instantes.
- Sin evidencia v2, el resultado no cambia en nada.

## Archivos permitidos
`data/models/InitialRecoveryEvidence.kt`, `domain/auge/InitialRecoveryEvidencePolicy.kt`, `domain/auge/AugeRecoveryEngine.kt` (solo
`getPerMuscleBatteries` y, si hace falta, un ayudante privado nuevo), `domain/auge/AugeUtils.kt` (solo si necesitas exponer una constante),
y pruebas.

## Pruebas (JVM)
- Nueva `InitialRecoveryEvidenceV2Test`: un JSON de evidencia v1 se decodifica igual y la política da lo de hoy; con v2, más horas →
  más batería en los tres canales (monótono); 40 de deuda de energía a las 36 h ≈ 97,5 (±1); sin patrones axiales → columna `null`;
  series de Piernas bajan Cuádriceps y no Pectorales; «No entrené» → 100; determinismo (dos llamadas iguales, mismo resultado).
- Nueva `AugePerMuscleInitialEvidenceTest`: sin evidencia v2 los músculos no cambian; con un registro real el mismo día, ese día se
  descarta.
- Pruebas que deben seguir pasando: `InitialRecoveryEvidence*`, `AugeRecoveryEngine*`, `SetupRingsMapper*`.
- Filtro: `*InitialRecoveryEvidence*`, `*AugePerMuscleInitialEvidence*`, `*AugeRecoveryEngine*`, `*SetupRingsMapper*`. Antes,
  `:app:compileDebugKotlin`.
