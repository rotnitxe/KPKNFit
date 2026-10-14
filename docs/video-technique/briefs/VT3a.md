# VT3a · Métricas por repetición y por serie, informe y motor (dominio puro)

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt3a`, desde `feat/video-technique` con VT2b integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3.5, §3.6 y §7, salvo las partes «Perfil carga-velocidad» y «Observaciones», que son de VT3b;
  - el código de `reps/`, `metrics/` (lo que dejó VT2b), `kinematics/` y `profiles/`.
- **Logs:** `C:\kw\logs\vt-3a-<n>.log`.

El motor deja de ser solo un contador: mide cada repetición y arma el `SetTechniqueReport`. **Aún sin m/s y sin observaciones** (de eso se encarga VT3b): `observations = emptyList()`, `scale = ScaleInfo(NONE, …)` y velocidades relativas.

## Archivos permitidos
- **`domain/biomechanics/metrics/` (nuevos):**
  - `SetTechniqueReport.kt`: los tipos de §3.5 que aún no existen (`DepthClass`, `VelocityUnit`, `ScaleSource`, `MetricKey`, `RepPhaseTimes`, `RepReport`, `ScaleInfo`, `TechniqueObservation`, `SetTechniqueReport`), todos `@Serializable`;
  - `JointAngleSeries.kt`, `RomMetrics.kt`, `DepthRules.kt` (profundidad, bloqueos y criterios), `TrunkMetrics.kt`, `PathMetrics.kt`, `RelativeVelocity.kt`;
  - `TechniqueAnalysisEngine.kt`, con `TechniqueAnalysisInput`.
- **`AnalysisTuning.kt`:** solo AÑADIR campos al final, con sus valores por defecto de §7.
- **Pruebas:** en `src/test/java/com/example/kpkn/domain/biomechanics/metrics/`.

## Qué haces
1. **`JointAngleSeries`.** Construye la serie, a 5 Hz de corte, del ángulo `romAngle` del perfil:
   - del lado cercano, o por lado en los perfiles `perSide`;
   - sobre la geometría corregida de `PreparedTrack`.

   **Ángulos:**
   - KNEE = 180 − θ(H,K,A);
   - HIP = 180 − θ(S,H,K);
   - ELBOW = 180 − θ(S,E,W);
   - ARM_ELEVATION = β;
   - TRUNK = τ.
2. **`RomMetrics`.**
   - Por repetición: |ángulo(giro) − ángulo(salida)|.
   - Por serie, sobre las FULL: media, DE, CV y tendencia (media de las 2 últimas − de las 2 primeras, con ≥ 4 FULL) más la pendiente OLS.
   - **Disponibilidad:** solo si la vista ∈ `metricViews`; RAISE, en cualquier vista. En otra vista no hay ROM (`MetricKey.ROM` no se marca).
3. **`DepthRules`** (§7, «Profundidad y criterios»): SQUAT_PARALLEL, CHIN_OVER_BAR, PUSH_UP_ELBOW, DIP_SHOULDER y BENCH_RELATIVE.
   - Rellenan `depthClass` y `depthDeg` por repetición, y `criteria`.
   - **Claves de `criteria`:** `chinOverBar`, `fullHang`, `lockout`, `pushUpFull`, `dipParallel` y `shallower`.
   - **Bloqueos:** DEADLIFT, HIP_THRUST y OHP, en el fotograma de fin de la CON.
4. **`TrunkMetrics`.**
   - Por repetición:
     - τ en el giro;
     - τ máx.;
     - rango;
     - Δτ_temprano: del inicio de la CON al 30 % de su desplazamiento.
   - Por serie: media.
   - Solo en SIDE (y LYING en los perfiles tumbados).
5. **`PathMetrics`.** El punto de barra es el disco si `source == "plate"`; si no, el sustituto de `BarPointRule`. Va en BH, porque sin calibrar no hay cm.
   - **MIDFOOT_VERTICAL:**
     - x_ref = mediana de X_MF del lado cercano;
     - por repetición: `pathOffsetMean`, `pathDrift` (deriva en la CON) y `pathExcursionFraction` (excursión/ROM_v);
     - en el informe, `barDeviation` = mediana de offMean en % de ROM_v, con `barDeviationUnit = "%ROM"`.
   - **CONSISTENCY** (banca): trayectorias remuestreadas en 21 niveles de progreso vertical → PD por repetición (`pathDeviationRms`, en % de ROM_v); DE del punto de contacto.
   - Las cifras con unidades absolutas (cm) las pone VT3b cuando haya disco calibrado.
6. **`RelativeVelocity`.**
   - **Unidad:** con señal de desplazamiento, la velocidad vertical del punto de barra en BH/s (`BODY_HEIGHTS_PER_S`); con señal de ángulo, la angular (`DEG_PER_S`).
   - **MCV por repetición:** desplazamiento entre el inicio y el fin de la CON / su duración.
   - **Pico:** solo con fs ≥ 50.
   - **Por serie:** `mcvFirst`, `mcvBest`, `mcvLast` y `velocityLossPct` = (mejor − última)/mejor·100, con ≥ 3 FULL.
     - Se excluyen las parciales y las fallidas.
     - Si el perfil es solo excéntrico, no hay VL.
7. **`TechniqueAnalysisEngine.analyze`.**
   - **Secuencia:**
     1. `TrackPreparation`;
     2. `CountSignalBuilder`;
     3. `RepSegmenter`;
     4. `RepConfidence`;
     5. `RepPhases`;
     6. las métricas.
   - **Resultado:** el `SetTechniqueReport` completo, con `engineVersion = ENGINE_VERSION` (1), `available` según lo que se pudo medir y las flags de la preparación y del segmentador.
   - **Con `NoSignal`:** informe con 0 reps, confianza BAJA, `available` vacío y la razón en flags o en un campo.
     - Si necesitas un campo nuevo para la razón, añádelo AL FINAL de `SetTechniqueReport` con valor por defecto, y anótalo.

## Pruebas (JVM, `SyntheticLifter` + `BarTrack` sintético)
- **ROM:**
  - sentadilla de lado con ROM de rodilla de 100° → 100 ± 3°;
  - con la vista FRONT, no hay ROM;
  - la tendencia detecta −10° en las 2 últimas.
- **Profundidad:**
  - sentadillas con la cadera 10° por debajo, a la par y 10° por encima → BELOW, PARALLEL y ABOVE;
  - dominada con el mentón sobre la barra en 4 de 5 → `chinOverBar` correcto por repetición.
- **Bloqueo:** peso muerto con la cadera a 150° → `lockout = false`.
- **Tronco:** τ en el giro dentro de ±2° de la verdad.
- **Trayectoria:**
  - deriva lateral inyectada → `pathDrift` con signo;
  - en banca, una repetición desviada → la de mayor PD.
- **Velocidad:**
  - CON de 1,0 s con un desplazamiento conocido → MCV a ±3 %;
  - pérdida: una serie con la CON cada vez más lenta (1,0 → 1,5 s) → VL ≈ 33 % ± 3.
- **Motor de punta a punta:** 5 sentadillas limpias → 5 reps, ALTA, tempo con 4 cifras, TUT > 0, `available` ⊇ {REPS, PHASES, TUT, TEMPO, ROM, DEPTH, TRUNK, PATH, VELOCITY_RELATIVE}.
- **Ida y vuelta** del `SetTechniqueReport` con `kotlinx.serialization`.

**Comandos:**
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.biomechanics.metrics.*" --tests "com.example.kpkn.domain.biomechanics.reps.*"`

## Commits
- `feat(technique): métricas por repetición (ROM, profundidad, bloqueo, tronco, trayectoria, velocidad relativa)`
- `feat(technique): informe de la serie y motor del analizador`
