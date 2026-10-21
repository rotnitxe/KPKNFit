# B4 · Fatiga neuromuscular objetiva: VBT, readiness y lo que mide la técnica por vídeo de KPKN

Agente B4 (Opus 5.5) · 2026-10-21. Fuentes: repositorio `C:\kw\r4i` (solo lectura; rama `feat/rings-v4`, al día con `master` 8ac57c9d4) y literatura publicada. No hay APK en este encargo. Las citas exactas (archivo:línea, DOI, cálculos) están en `B4-vbt-evidencia.md`.

Etiquetas: **[OBSERVADO]** en código o documento del repo · **[LITERATURA]** fuente publicada · **[INFERIDO]** deducción mía, con su razonamiento · **[NO VERIFICABLE]**.

---

## 0. Resumen ejecutivo

**Qué mide hoy KPKN [OBSERVADO].** El análisis corre en el teléfono al terminar la serie.
- **Por repetición:** tipo (completa, parcial o fallida), fases con sus duraciones, amplitud, ROM en grados, profundidad, velocidad media concéntrica (MCV) y pico, trayectoria y tronco.
- **Por serie:** conteos, TUT, tempo, ROM, pérdida de velocidad (VL %) y MCV primera, mejor y última.
- **Unidades:** m/s solo con un disco **confirmado por el usuario**, de diámetro conocido y seguido sin cortes; si no, alturas corporales/s o °/s, mostradas en %.
- **Precisión:** la reserva **falló** (conteo y giros, ninguna confianza ALTA), los m/s no están validados y la VL salió en 2 de 59 series de ajuste.
- **Destino:** al `CompletedSet` solo llegan reps, parciales y fallo. **AUGE no consume nada del vídeo.**

**Ciencia [LITERATURA].**
- La VL intra-serie marca bien la fatiga aguda (r = 0,93–0,97 con lactato).
- La velocidad de una repetición estima el RIR con menos de 2 reps de error, **solo con relaciones individuales**.
- La velocidad a carga fija detecta fatiga a 24–48 h, pero el ruido día a día (4–6 % con transductor, 6–14 % con apps) lleva el cambio mínimo detectable al 9–18 %.

**Veredicto.**
- «−8 % en el calentamiento ⇒ SNC fatigado» no se sostiene: no hay umbral publicado, esa fatiga es sobre todo periférica y −8 % cae dentro del ruido (≈ 10 % de falsas alarmas por prueba).
- Realista: un RIR objetivo y una VL en series con barra y disco calibrado, como **evidencia con incertidumbre**.

**Propuesta.**
- **V0 (S):** fallo observado y RPE objetivo cuando falta el declarado.
- **V1 (M):** RIR por velocidad con calibración personal, fusionado con el RPE y como observación del aprendiz.
- **V2 (M–L):** readiness frente al perfil carga-velocidad personal.

**No pude ver** la validación en el teléfono ni los textos de pago; además se agotó el presupuesto de búsquedas web.

---

## 1. Identidad y arquitectura

| Pieza | Qué es | Dónde vive |
|---|---|---|
| Técnica por vídeo (ola VT) | Análisis **offline** al terminar la serie (D2), en WorkManager; el vídeo no sale del teléfono | `data/work/TechniqueAnalysisWorker.kt`, `data/media/technique/TechniqueAnalysisPipeline.kt` |
| Decodificación | GL a **720 px de lado largo** (`targetLongEdge = 720`), con PTS reales | `TechniqueAnalysisPipeline.kt:89-106` [OBSERVADO] |
| Pose | MediaPipe Pose Landmarker **full** (33 puntos, `numPoses = 2`), GPU con respaldo CPU; un fotograma de cada dos si la cadencia supera 35 fps **o** si el móvil está en modo degradado | `:349`; `GpuProbe.kt:51-52,120-132` [OBSERVADO] |
| Disco | Visión clásica sobre luma (Hough de gradiente, Kåsa, elipse, Kalman, NCC), **en todos los fotogramas** mientras se sigue | `TechniqueAnalysisPipeline.kt:765-773`; `domain/biomechanics/vision/` [OBSERVADO] |
| Motor | Kotlin puro (`domain/biomechanics`) → `SetTechniqueReport`; **`ENGINE_VERSION = 6`** en el código, aunque la especificación y el informe dorado hablan de «motor 1» | `TechniqueAnalysisEngine.kt:56`; `GOLDEN_REPORT.md:5` [OBSERVADO] |

**Consecuencia [INFERIDO].** Las cifras de precisión publicadas son del motor 1. Después entraron cambios en las fases y la velocidad del disco (`NativePlatePhases`, `NativePlateRelativeVelocity`, `PlatePublicationPolicy`). No sé si se volvió a medir: según la memoria del proyecto, el banco completo (VT11) se estaba corriendo el 20-oct en otra rama **[NO VERIFICABLE desde r4i]**.

**Fallo vivo en r4i [OBSERVADO].** La regla del salto de pose (`TechniqueAnalysisPipeline.kt:349`) aplica el paso 2 también a 25–30 fps en modo degradado (menos de 3 GB de RAM o estado térmico ≥ MODERATE). La pose queda así a 12,5–15 Hz, por debajo del umbral de 20 Hz (`AnalysisTuning.kt:15`), y la serie se queda sin fases ni velocidad. La memoria del proyecto lo registra como D39 y dice que M1 lo arregla en `feat/vt-ui`, que r4i todavía no tiene.

---

## 2. Entradas de datos: qué produce, en qué unidades, qué se guarda

### 2.1 Señales por repetición (`RepReport`, `SetTechniqueReport.kt:57-83`) [OBSERVADO]

| Campo | Contenido y unidad | Condición |
|---|---|---|
| `kind`, `partialKind`, `side` | FULL / PARTIAL / FAILED; lado en el conteo por lado | siempre que haya ciclos |
| `phases` (4 µs) + `eccentricMs`, `turnPauseMs`, `concentricMs`, `restPauseMs` | Ajuste de jerk mínimo por media repetición (Levenberg–Marquardt); pausas < 0,25 s cuentan como 0 | fs ≥ 20 |
| `amplitudeFraction` | Recorrido respecto de una completa (1,0) | — |
| `romDeg`, `depthClass/Deg`, `criteria` | Ángulo de la articulación del perfil; profundidad; bloqueos | vista en `metricViews` (casi siempre SIDE) |
| `mcv`, `peakVelocity` | MCV = desplazamiento de la concéntrica / su duración. **m/s** con disco calibrado; si no, **BH/s** (alturas corporales/s) o **°/s**. El pico solo con fs ≥ 50 | vista en `countViews`, fs ≥ 20 |
| `path*`, `trunk*` | Trayectoria (cm calibrado, % ROM sin calibrar) y tronco | SIDE |

### 2.2 Señales por serie (`SetTechniqueReport.kt:136-176`) [OBSERVADO]

`repCount`, `partialReps`, `failedReps`, `miniSets`, `tutMs`, `setDurationMs`, `restPositionMs`, `tempo`, `romMeanDeg/SdDeg/CvPct/TrendDeg`, `romSlopeDegPerRep`, `velocityUnit`, **`velocityLossPct`**, `mcvFirst/Best/Last`, `peakBest`, `scale` (fuente, diámetro, m/px H y V, a/b, error de paralaje), `trunkMeanDeg`, `barDeviation`, `observations` (como mucho 3), `available`, `flags` y `analyzedFps/sourceFps`.

**Lo que no se guarda [OBSERVADO]:** `VelocitySummary.durationError`, el error esperado de la duración que decide si se publica la VL, se calcula (`RelativeVelocity.kt:36-48`) pero no pasa al informe. Tampoco hay aceleración ni MPV: la especificación las reserva a «disco calibrado y fs ≥ 50», y en el código no se publican.

### 2.3 Velocidad, calibración y fps [OBSERVADO]

| Aspecto | Regla actual | Evidencia |
|---|---|---|
| **m/s** | Solo si `PlatePublicationPolicy.canUsePlate` (semilla **confirmada por el usuario** y seguimiento `UNINTERRUPTED`), `plateDiameterMm` conocido, escala válida y el disco es la señal de conteo. Con una semilla automática el diámetro es `null`, así que no hay m/s | `PlatePublicationPolicy.kt:11-12`; `TechniqueAnalysisEngine.kt:87-113`; `TechniqueAnalysisPipeline.kt:754-762` |
| Escala | m/px = D/(2·r̂_cal), con r̂ la mediana de los radios HOUGH con q ≥ 0,5 en fotogramas lentos (≥ 8 muestras); elipse: aviso con a/b < 0,85 y rechazo con a/b < 0,70; paralaje con el disco 0,70 m más cerca que el plano sagital; focal de `CameraCharacteristics` o 70° de HFOV supuesto; cordura con el muslo «world» (±25 %) | `PlateScale.kt`; `AnalysisTuning.kt:351-369` |
| Diámetro | 450 mm por defecto (ajuste) o chips 45/40/35/otro en «Calibrar disco» | `Settings.kt:470-475`; espec. §1.4 |
| Señal del disco para cinemática | Solo muestras HOUGH/NCC; huecos ≤ 0,1 s interpolados (con más, esa repetición se queda sin velocidad); paso bajo de fase cero a 8 Hz (≥ 50 fps) o 6 Hz | `CalibratedVelocity.kt:39-151` |
| Captura | 60 fps en perfiles con barra **si el móvil lo garantiza**; si no, 30. En el emulador se concedieron 30 frente a 60 pedidos (≈ 28 fps medidos) | espec. §1.1 y §9.6; `ESTADO.md:49,69` |
| Cámara | Fija, de lado, a 2–4 m y a media altura del recorrido; roll > 3° ⇒ «Endereza el móvil». **No hay compensación de movimiento de cámara** en el motor | espec. §1; búsqueda sin resultados en `domain/biomechanics` y `data/media` |
| Sin calibrar | VL y barras relativas en %; comparables en el historial solo con la misma vista y el mismo segmento de escala; **nunca** m/s | espec. §7 «Velocidad» |

**Guarda de la VL (D29) [OBSERVADO]** (`RelativeVelocity.kt:100-172`, `AnalysisTuning.kt:388-399`):
- **Modelo de error:** ε_T = κ·(σ/ΔL)/√(fs·T), con κ = 6,6, σ = el ruido que el ajuste de jerk mínimo deja sin explicar, ΔL = el recorrido y T = la duración.
- **VL publicada** si ε_T ≤ 2,5 %, hay al menos 3 FULL y el CV de las 3 primeras es ≤ 25 %.
- **Entre 2,5 % y 10 %:** solo barras relativas.
- **Más del 10 %:** nada.
- **Fórmula:** VL = (mejor − última)/mejor, medida en la ventana del 10 % al 90 % del desplazamiento.

### 2.4 Persistencia [OBSERVADO]

| Dónde | Qué | Evidencia |
|---|---|---|
| `<id>.analysis.json` (**fuente de verdad**, D9) | El `SetTechniqueReport` completo, con todas las repeticiones | espec. §3.8 |
| Room `video_analysis` (caché, CACHE en las copias) | Un resumen **por serie**: conteos, tempo, ROM, VL, MCV primera/mejor/última, pico, escala, desvío, flags; **nada por repetición** | `VideoAnalysisEntity.kt:41-83` |
| `CompletedSet` / `WorkoutLog` | **Ningún campo de vídeo.** «Usar» cambia solo reps y parciales del borrador; «Corregir» parchea reps y parciales de la serie registrada; «Fallo» marca el fallo. El ROM % se **muestra** (con ≥ 2 sesiones de referencia) pero **no se aplica** | `WorkoutLog.kt:137-186`; `SetExecutionCard.kt:2279-2297`; `WorkoutVideoAnalysisController.kt:134-175` |
| AUGE | Ninguna referencia a `VideoAnalysis*` ni a `SetTechniqueReport`. Tampoco usa `CompletedSet.rom` ni el TUT. Las «técnicas» de `AugeFatigueEngine` son dropsets y rest-pause | grep en `domain/auge` (apéndice) |

### 2.5 Precisión medida (banco dorado, motor 1) [OBSERVADO]

- **Conteo, reserva ciega de 37 clips: FALLÓ.** SQUAT 2/4 dentro de rango (requisito 95 %); ROW 3/5 (requisito 90 %; un clip con 7 de error). **Cero salidas ALTA**, así que la precisión de ALTA queda sin validar (`GOLDEN_REPORT.md:107-111`).
- **Giros:** MAE por clip de 29 a 789 ms, con giros sin pareja (`GOLDEN_TIMING_HOLDOUT.md:51-62`). Las etiquetas valen ~0,25 s, así que el objetivo de 50 ms y el ICC de las fases **no están validados**.
- **Velocidad:** VL publicada en **2/59** series de ajuste; `durationError` estimable en 19/59, con mediana **9,55 %**. «La pérdida de velocidad publicada no acredita fatiga real» (`ESTADO.md:128`; `GOLDEN_REPORT.md:105`).
- **m/s:** sin validar. Hace falta un desplazamiento conocido en el Z Flip5 (D5; `ESTADO.md:138`).

### 2.6 Limitaciones documentadas

- **Oclusión:** el disco tapa la mano o la cabeza, como en r08/r09.
- **Vista:** de espaldas no se cuenta en varios perfiles; en oblicuo no hay ROM.
- **Varias personas:** la segunda pose en más del 10 % de los fotogramas ⇒ MULTI_PERSON, y la confianza no pasa de MEDIA.
- **Captura:** cámara que se mueve; VFR; HDR; fs < 20 ⇒ LOW_FPS.
- **Disco:** la adquisición falla en clips reales (r02: disco PENDING, cero intentos).
- **Galería:** focal desconocida (se suponen 70° de HFOV).
- Fuentes: `ESTADO.md:89-100`; espec. §4.

---

## 3. Métricas y fórmulas

### 3.1 Velocidad y pérdida de velocidad

| Métrica | Definición | Constantes y umbrales | Etiqueta / referencia |
|---|---|---|---|
| MPV / MV | MPV: media de la fase propulsiva (a > −g); MV/MCV: media de toda la concéntrica | Cerca del 1RM, MPV ≈ MV (sentadilla: 0,32 ± 0,03 m/s ambas) | [LITERATURA] Sánchez-Medina 2017 (SMIO 1:E80) |
| Relación carga-velocidad | %1RM ↔ MPV casi lineal y estable por persona | Banca R² = 0,98; V1RM banca 0,16 ± 0,04 m/s | [LITERATURA] González-Badillo y Sánchez-Medina 2010 (IJSM 31:347) |
| | | Sentadilla MPV: 50 % = 1,14; 60 % = 1,00; 70 % = 0,84; 80 % = 0,68; 90 % = 0,51; 100 % = 0,32 m/s (SEE 4,0 %) | Sánchez-Medina 2017 |
| MVT (velocidad en el 1RM) | — | Powerlifters (ACV): sentadilla 0,23 ± 0,05; banca 0,10 ± 0,04; peso muerto 0,14 ± 0,05 | [LITERATURA] Helms 2017 (JSCR 31:292) |
| | | V1RM poco fiable: CV 22,5 %, ICC 0,42 (sentadilla) | Banyard 2017 (JSCR 31:1897) |
| | | KPKN: sentadilla 0,30, banca 0,17, peso muerto 0,15, press militar 0,19 y remo prono 0,50 | [OBSERVADO] `AnalysisProfileResolver.kt:205-214` |
| | | **No encontré fuente para 0,19 en press militar** | [NO VERIFICABLE] |
| VL % | (mejor − última)/mejor·100, o (primera − última)/primera | KPKN: ventana del 10 % al 90 %, ≥ 3 FULL, guarda D29 | [LITERATURA] Sánchez-Medina y González-Badillo 2011 (MSSE 43:1725) |
| VL → fatiga aguda | — | Con el lactato pico r = 0,93–0,97. La VL, la pérdida de velocidad con la carga de 1 m/s y la pérdida de CMJ se correlacionan entre sí (r = 0,91–0,97). El amonio sube solo pasada la mitad de las repeticiones posibles (+2) | Sánchez-Medina y González-Badillo 2011 |
| | | La fatiga tras la serie (velocidad con la carga de ~1 m/s) depende de la VL (r = 0,97 banca, 0,99 sentadilla), **no** del número de repeticiones | [LITERATURA] Rodríguez-Rosell 2020 (JSCR 34:2537) |
| VL → % de repeticiones hechas | — | Relación estrecha y casi independiente de la carga (R o R², según la fuente, de 0,93 a 0,97). Con la misma VL se hace menor % en banca que en sentadilla. Las ecuaciones exactas están en textos de pago | [LITERATURA] González-Badillo 2017 (IJSM 38:217); Rodríguez-Rosell 2020 [NO VERIFICABLE: tabla] |
| | | **En contra:** las relaciones VL–% de repeticiones, generales **o individuales**, predicen mal otra sesión (error > 10 %); en sentadilla al 70 %, la VL no predijo el total de repeticiones (R² = 0,011) | [LITERATURA] Jukic 2023 (tesis AUT); Haischer 2023 (JHK 87:167) |
| Velocidad → RIR | — | Las velocidades a las que se para con 2, 4, 6 u 8 RIR son casi iguales con cualquier carga (CV 4,4–8,0 %) | [LITERATURA] Morán-Navarro 2019 (JSCR 33:1496) |
| | | Relación RIR–velocidad individual: error < 2 reps en otra sesión; la general, no. Se puede promediar entre cargas | Jukic 2024 (Physiol Rep 12:e15955) |
| | | Ecuaciones de grupo: CV 15–29 % (inaceptable); individuales: r² mediano 0,984 | García-Ramos 2018 (IJSPP 13:353) |
| Precisión del RIR declarado | — | Se infravaloran las repeticiones hasta el fallo en 0,95 (IC 0,17–1,73); DE entre personas 1,45 reps | [LITERATURA] Halperin 2022 (Sports Med 52:377) |

### 3.2 VL robusta (propuesta) [INFERIDO]

**Problema.** «Mejor − última» sesga la VL hacia arriba, porque «mejor» es el máximo de n valores con ruido.

**Simulación** (Monte Carlo, apéndice §4), con una pérdida real de 0:

| Error por repetición ε | n | VL espuria media | P95 |
|---|---:|---:|---:|
| 2,5 % | 5 | 2,8 pp | 7,3 pp |
| 9,55 % | 8 | 11,8 pp | 27,1 pp |

**Alternativa.** VL_rob = 1 − mediana(v de las 2 últimas)/mediana(v de las 2 primeras). Media ≈ 0 y P95 a la mitad (4,0 pp con ε = 2,5 %).

**Alcance.** Es un cambio del motor VT, no de AUGE. Hay que proponérselo a esa ola.

### 3.3 Ruido de medida y cambio mínimo detectable

**Modelo [INFERIDO]:**
- error típico total: TE_total = √(TE_bio² + TE_disp²);
- cambio mínimo detectable al 95 %: MDC95 = 1,96·√2·TE si se compara con un día;
- y 1,96·TE·√(1 + 1/n) si se compara con una línea base de n sesiones.

| Medida (velocidad a carga fija) | TE técnico | TE total | MDC95 frente a base de 10 sesiones |
|---|---:|---:|---:|
| Transductor lineal en Smith (incluye la biología): MV a 45–75 % 1RM, CV 4,0–5,7 % [LITERATURA, Pérez-Castilla 2021, IJERPH 18:4626] | — | 4,5 % | **9,3 %** |
| Metric, sentadilla (CV del aparato 3,83 %, SDC 0,05 m/s) [LITERATURA, Puppyn 2025] | 3,8 % | 5,9 % | 12,1 % |
| Metric, banca (7,57 %) | 7,6 % | 8,8 % | 18,1 % |
| Metric, peso muerto (13,29 %) | 13,3 % | 14,0 % | 28,8 % |
| PowerLift v4 (sentadilla 7,6 %, banca 11,7 %) [LITERATURA, Courel-Ibáñez 2019 vía Weakley 2021] | 7,6–11,7 % | 8,8–12,5 % | 18–26 % |
| KPKN con disco calibrado (supuesto: 3–5 %) [INFERIDO] | 3–5 % | 5,4–6,7 % | 11–14 % |
| KPKN solo pose (ε_T mediano 9,55 %) [OBSERVADO + INFERIDO] | 9,6 % | 10,6 % | 21,7 % |

Promediar 3 repeticiones baja el ruido técnico (×1/√3), pero no el biológico. Así, la mejor opción realista del teléfono queda en un MDC95 de ~10 %.

### 3.4 Validez de apps frente a referencias [LITERATURA]

- **Renner 2024** (PLoS One 19:e0313919): 20 powerlifters, 589 repeticiones, Vicon a 200 Hz.
  - Qwik VBT (60 fps, 1080p) y un transductor: RMSE 0,01–0,04 m/s.
  - Metric y MyLift: RMSE 0,04–0,14. Metric perdió 52 repeticiones y MyLift 175.
  - **Corrección a la propia especificación de KPKN:** §0 atribuye a Metric «0,04–0,08»; el rango publicado es 0,04–0,14 (contando MyLift) y 0,04 solo en banca.
- **Trowell 2024** (PeerJ 12:e17789), Metric 0.6.0 a 720p/60 fps frente a Vicon:
  - sesgo +0,09 a +0,13 m/s en sentadilla;
  - límites de acuerdo de −0,05 a +0,28 m/s, con sesgo proporcional;
  - los autores lo consideran, por ahora, insuficiente para guiar el VBT.
  - Es lo más parecido a la situación de KPKN: cámara, 720 px y 60 fps.
- **Puppyn 2025** (IJSSC), Metric frente a GymAware:
  - detección del 94,3 % de las repeticiones;
  - **no** cumple el criterio de investigación (ICC ≥ 0,997; CV ≤ 3,5 %).
- **Otras apps:** PowerLift (240 fps, marcado manual) r = 0,94, SEE 0,028 m/s (Balsalobre-Fernández 2018, JSS 36:64) y CV 3,97 % frente a OptiTrack (Pérez-Castilla 2019, JSCR 33:1258); Iron Path no sustituye a un transductor (Kasovic 2021); My Jump Lab en banca r = 0,90–0,92, CV 8,2–13,6 % (Balsalobre-Fernández 2023, JSCR 37:e640).
- **Revisiones:** los transductores lineales son los más exactos (Weakley 2021, Sports Med 51:443); **ninguna** app de teléfono pasa los criterios de validez de Wannouch 2025 (PLoS One 20:e0324606).
- **Validación de pose sin barra:** **no encontré ninguna para velocidad de barra a partir de puntos de pose** (MediaPipe u OpenPose) [NO VERIFICABLE]. Las validaciones de MediaPipe miden ángulos de rodilla, con un sesgo de −5° a −14° [LITERATURA, búsqueda; resultados secundarios].
- **Qualisys:** no encontré comparativas; las que hay usan Vicon u OptiTrack [NO VERIFICABLE].

### 3.5 Límite por fps (cuantización) [INFERIDO, contrastado]

- **Modelo:** la DE de un evento es (1/fs)/√12, y la de una duración, √2 veces eso.
- **CMJ** (vuelo de 0,5 s): ±5,4 % a 30 fps, ±2,7 % a 60, ±1,4 % a 120 y ±0,7 % a 240 por salto.
  - El modelo **reproduce** el error técnico publicado para MyJump: 1,4 % a 120 Hz y 0,7 % a 240 Hz [LITERATURA, Pueo 2023, Biol Sport].
- **MCV con extremos por umbral:** ±2,3 % a 30 fps y ±1,1 % a 60, con una concéntrica de 0,6 s.
- **Ajuste del motor de KPKN:** el ajuste de jerk mínimo da resolución por debajo del fotograma. Su cota (§2.3) da:
  - con el disco (σ de 1–2 px sobre 200–300 px): 0,3–1,7 %;
  - con la pose (σ de 0,004–0,006 FH sobre 0,10–0,12 FH): 3–10 %.
- **Conclusión:** la cadencia no es el cuello de botella; lo es la señal. El disco es ~10 veces mejor que la pose.
- **Pico:** necesita ≥ 50 fps (regla de KPKN). Un fabricante dice que el suavizado a 30 fps recorta los picos hasta un 25 % [NO VERIFICABLE, blog de Metric].

### 3.6 Readiness neuromuscular con velocidad [LITERATURA]

- **Fatiga residual: Vernon 2020** (IJSSC). Tras 5×5 al 80 %, la velocidad con cargas ≥ 60 % sigue baja a 24 y 48 h (efecto grande) y se recupera a las 72 h. El 1RM y el CMJ solo cambian de forma trivial o pequeña: **la velocidad submáxima es más sensible**.
- **Tiempo de recuperación según el fallo:**
  - Morán-Navarro 2017 (EJAP 117:2387): con 3×10(10), al fallo, la velocidad con la carga de 1 m/s y al 75 % y el CMJ caen más y se recuperan más despacio que con 3×5(10) o 6×5(10); la diferencia aparece entre las 24 y las 48 h.
  - González-Badillo 2016 (IJSM 37:295): tras la mitad de las repeticiones posibles el CMJ se recupera en 6 h; tras el fallo sigue bajo a 48 h.
- **Sensibilidad aguda: Moura 2024** (IJSM 45:624). Justo después de la sesión la MPV cae entre −4,0 % y −12,5 % (ES 0,32–0,66) y el CMJ entre −7,5 % y −10,4 %. Solo la MPV al 40 % y al 80 % coincide bien con el CMJ.
- **El 1RM por velocidad sirve poco para prescribir:**
  - Hughes 2019 (JSCR 33:762 y 33:2409): no es lo bastante exacto para prescribir la carga a diario, aunque en el calentamiento sirve como indicio de fluctuaciones.
  - Banyard 2017: errores individuales de −5,5 % a +27,8 %.
  - Banyard 2018 (IJSPP 13:763): los perfiles con MV al 20–90 % son fiables, y los autores sugieren usarlos para ajustar la carga según la readiness del día.
- **Central o periférica: Thomas 2018** (MSSE 50:2526). Tras 10×5 al 80 %, la activación voluntaria sigue baja durante 48 h y la fuerza del twitch potenciado, también durante 48 h; todo se recupera en 72 h. Los autores concluyen que esa fatiga no se debe sobre todo al SNC.
- **CMJ: Claudino 2017** (JSAMS 20:397, metaanálisis de 151 artículos). La **altura media** del CMJ es más sensible que la máxima para detectar fatiga y supercompensación.
- **Sin verificar:**
  - qué dice exactamente Jovanović y Flanagan 2014 (JASC 22:58) sobre la readiness en el calentamiento;
  - la banda práctica de ±0,06 m/s para ajustar la carga del día (Dorrell 2020 y Moore y Dorrell 2020 solo en resumen) [NO VERIFICABLE].

### 3.7 Trabajo mecánico por serie

**Definición [LITERATURA + INFERIDO].** En una concéntrica de reposo a reposo, ΔKE ≈ 0, así que W ≈ m_sis·g·Δh. Por serie:

`W_serie = g · Σ_k [ m_ext · Δh_barra,k + f_seg · BM · c · Δh_barra,k ]` (J)

- **m_ext:** barra y discos.
- **f_seg** (de Leva 1996, varones):
  - sentadilla, zancada y step-up: 0,886 (masa corporal menos dos piernas y dos pies, 2·(4,33 + 1,37) %);
  - dominadas y fondos: 0,955 (menos dos antebrazos y dos manos, 2·(1,62 + 0,61) %);
  - flexiones: ≈ 0,65–0,75, por recuerdo de Suprak 2011 y Ebben 2011 [NO VERIFICABLE: el resumen no da la cifra];
  - banca, press, remo y curl: ≈ 0 (solo se mueven los brazos).
- **c ≈ 0,88:** la barra sube un 13,4 % más que el centro de masas (Lake 2012, JSCR 26:1302).
- **Δh:** en m con el disco; o en BH × la estatura del usuario sin disco (error ~10–15 %).

**Para qué sirve.** Mide la **carga mecánica externa** y corrige el volumen-carga (reps × kg) por el ROM. Dos ejemplos (apéndice §4): una media sentadilla de 100 kg × 5 son 2 788 J frente a 4 779 J en completa; una banca de 80 kg × 8, 2 511 J. **No mide la fatiga.** McBride 2009 (JSCR 23:106): el trabajo total no distinguió los protocolos de fuerza, hipertrofia y potencia, y el volumen-carga sí. Que esos protocolos fatiguen distinto es una inferencia mía [INFERIDO]: el estudio no midió la fatiga.

### 3.8 CMJ con el móvil [INFERIDO]

**Medida.** Altura = g·t²/8, con t el vuelo (despegue y aterrizaje por la pose de tobillos y pies, o la caída de la cadera).

**Error.** Con 3 saltos el error técnico es ±1,6 % a 60 fps y ±3,1 % a 30, frente a una variabilidad biológica de pocos % (no verificada en esta sesión). A 60 fps es una sonda viable. **KPKN no tiene perfil de salto:** `hip_hinge_explosive` está «sin soporte» (espec. §5).

---

## 4. Algoritmos y reglas de decisión

**Literatura [LITERATURA]:**
- **Umbrales de VL:**
  - Weakley 2020 (IJSPP 15:180), sentadilla 5 series al 10/20/30 %: MV 0,66/0,62/0,59 m/s; 4,2/6,4/7,8 reps por serie; la salida se mantiene estable entre series.
  - Pareja-Blanco 2017 (SJMSS 27:724): con VL20, la misma fuerza que con VL40 y ~40 % menos repeticiones, más CMJ (+9,5 % frente a +3,5 %) y se conservan las IIX. VL40 da más hipertrofia del vasto lateral e intermedio.
  - Pareja-Blanco 2020 (SJMSS 30:2154, banca 0/15/25/50 %): la misma fuerza; VL50 da más área del pectoral.
  - Jukic 2023 (Sports Med 53:177, metaanálisis): una VL mayor favorece la hipertrofia y empeora el CMJ, el sprint y la velocidad submáxima.
- **Readiness:** perfil individual + desviación frente a lo esperado. No hay umbral validado universal. La práctica es: detectar fuera del ruido y ajustar la carga del día.

**KPKN hoy [OBSERVADO]:**
- La VL se informa, no se prescribe.
- No hay contador en vivo; queda para la fase 2.
- El 1RM por velocidad sale solo en tendencias, como «orientativo · beta», y no alimenta la sugerencia de carga (D6). Exige ≥ 3 cargas, rango de carga ≥ 25 %, rango de MCV ≥ 0,30 m/s, el punto más pesado ≤ 0,60 m/s (banca ≤ 0,50), R² ≥ 0,95/0,90 e IP95 ≤ ±10 %.
- **Lista prohibida de la especificación:** «RIR o RPE por pérdida de velocidad (no en v1)» (`VIDEO_TECHNIQUE_V1.md:883`). **V0 y V1 chocan con esta regla:** es una decisión del usuario (§8).

**AUGE hoy [OBSERVADO]:**
- Sin RPE ni RIR declarados, `getEffectiveRPE` vale **7,0** por defecto (`AugeFatigueEngine.kt:249-259`); el multiplicador es 1 + (RPE/10)^4,2, más un bonus por encima de 10.
- El aprendiz de Energía usa un «ΔRPE emparejado»: la misma carga (±2,5 %) y las mismas reps (±2) que en el historial. La Energía implícita es la predicha − 8·ΔRPE; para músculos usa un cociente de e1RM, con implícita = 100 − 400·(1 − ratio) (`PerformanceTauLearner.kt:183-229,394-415`; `PerformanceImpliedBattery.kt:14-28`).
- El plan v4 rehace el aprendiz como Kalman, con σ de la observación de 5 pts (sentido), 12 (e1RM) y 10 (ΔRPE) (`PLAN.md:130-135`).

---

## 5. Conceptos de interfaz relevantes

**Hoy [OBSERVADO]:**
- barras de velocidad relativas a la mejor repetición;
- «≈» sin calibrar;
- m/s solo con disco y con «≈» hasta validarlos en el teléfono;
- «orientativo · beta» en el 1RM;
- como mucho 3 observaciones, sin «error» ni «riesgo»;
- nada se aplica solo: «Usar» o «Corregir» (D3).

**Propuesta [INFERIDO]:** los anillos **no cambian de forma**; la evidencia objetiva vive fuera de ellos.
1. **Tarjeta y descanso:** una línea «RIR ≈ 2 según el vídeo (±1,5)», solo cuando sea fiable.
2. **Hoja de AUGE:** una fila de evidencia, por ejemplo «Velocidad del calentamiento: −5 % frente a lo esperado · dentro del ruido (±10 %)».
3. **«Precisión del modelo»** (S3 del plan v4): el vídeo como una fuente más de observaciones.

Sin sliders nuevos (decisión cerrada).

---

## 6. Verificación del informe previo

| Afirmación | Veredicto | Evidencia |
|---|---|---|
| «Velocidad media concéntrica del calentamiento de hoy frente a su media de 4 semanas para el mismo peso» como fatiga neuromuscular objetiva | **CORREGIDA** | El concepto existe: la velocidad submáxima detecta fatiga residual (Vernon 2020, Moura 2024) y Banyard 2018 propone usar el perfil para la readiness. Pero: (1) la «media de 4 semanas al mismo peso» se contamina con la adaptación y con cargas distintas: hay que comparar contra el **perfil carga-velocidad personal** con pesos temporales; (2) el ruido da un MDC95 de 9–18 % (§3.3); (3) sin disco calibrado, KPKN no tiene m/s |
| Umbral «−8 % ⇒ SNC fatigado» | **CORREGIDA (no válida)** | No encontré ningún umbral publicado de −8 % [NO VERIFICABLE como origen]. La fatiga tras fuerza pesada es sobre todo periférica (Thomas 2018). Con TE = 6 %, la falsa alarma es del 10 % por prueba (una cada ~3 semanas con 3 sesiones por semana) y la sensibilidad a un −8 % real, del 50 % (apéndice §4). A cargas de calentamiento, −8 % equivale a +1,6–2,2 RPE (apéndice §4) |
| Pérdida de velocidad intra-serie | **CONFIRMADA (con matices)** | Sánchez-Medina 2011; Rodríguez-Rosell 2020; Pareja-Blanco 2017 y 2020. Exige intención máxima. Las ecuaciones de grupo predicen mal el RIR (Jukic, Haischer); KPKN solo la publica con la guarda (2/59 series) |
| «Trabajo mecánico = fuerza × ROM en metros» | **CORREGIDA** | Físicamente W ≈ m·g·Δh. Mide carga, **no fatiga** (McBride 2009). La barra no es el centro de masas del sistema (Lake 2012) |
| «Alimentar un modelo tipo Banister/ACWR con carga mecánica y neuromuscular real» | **CORREGIDA (parcial)** | El trabajo puede ser otra carga externa para el ACWR o el PMC (lo evalúa B3). La señal neuromuscular es escasa (solo las series filmadas) y ruidosa: encaja como **observación del aprendiz** con su σ, no como entrada de impulso |
| Kubios/Morpheus: «ligarlo al umbral de pérdida de velocidad del VBT» | **NO VERIFICABLE** | No encontré estudios que liguen la HRV matutina a umbrales de VL. Además, la HRV necesita Health Connect o un sensor, fuera de alcance: decisión del usuario (lo cubre B2) |
| Espec. VT §0: «Renner 2024: Qwik 0,01–0,03; Metric 0,04–0,08» | **CORREGIDA** | Publicado: un transductor y Qwik, RMSE 0,01–0,04; Metric y MyLift, 0,04–0,14. Metric solo es comparable en banca (0,04) |
| Espec. VT: MVT 0,30/0,17/0,15/0,19/0,50 | **Parcialmente CONFIRMADA** | Sentadilla 0,32 (Sánchez-Medina 2017; Helms 2017: 0,23 en powerlifters); banca 0,16–0,17 (González-Badillo 2010, Pérez-Castilla 2021); peso muerto 0,14 (Helms). **0,19 en press militar y 0,50 en remo prono: NO VERIFICABLE en esta sesión** |
| D6 «1RM por velocidad poco fiable en peso libre» | **CONFIRMADA** | Banyard 2017 (V1RM con CV del 22,5 %; errores individuales hasta +27,8 %); Hughes 2019 (desaconseja usarlo para prescribir la carga diaria) |

---

## 7. Aplicabilidad a AUGE

**Principio.** El vídeo da **evidencia con incertidumbre**, en pocas series (solo las filmadas) y tarde (20–60 s después, a veces tras cerrar la sesión). AUGE debe tratarla como una observación opcional: fusionarla, acotarla y guardarla con el log, nunca como una verdad que sustituya la serie.

**Capa nueva común [INFERIDO]:**
- **Qué es:** un `ObjectiveSetEffort` (en `domain/auge`, Kotlin puro) con `rirMean`, `rirSd`, `vlRob`, `vlSd`, `source` (V0_DURATION, V1_VELOCITY), `mediaId`, `engineVersion` y `flags`.
- **Cuándo se guarda:** se **congela** en el `CompletedSet`, en un campo nuevo opcional y nulo por defecto en el JSON del log, cuando la persona acepta el análisis; o al terminar, según la decisión §8-1. Así el historial no cambia si se borra el vídeo; `video_analysis` sigue siendo caché.
- **Cómo se une:** mismo `workoutLogId`/`sessionKey` + `exerciseId` + `setIndex` + `side`, como ya hace `WorkoutVideoAnalysisController`.

### 7.1 Fase V0 · Sin velocidad de barra (fases, ROM, fallidas)

**Variables:**
- por repetición: tipo, C_k (ms de concéntrica) y A_k (amplitud);
- por serie: VL publicada, ε_T (hay que **persistir `durationError`**), %1RM = carga/e1RM (`homologatedResultV3.estimatedRm`).

**Ecuaciones [INFERIDO]:**
1. **Fallo observado:** una repetición FAILED (el segmentador ya la detecta) ⇒ RIR = 0 al final. Hoy solo propone «Fallo» en la interfaz; si la persona lo acepta, AUGE ya lo usa (`isFailure` ⇒ RPE ≥ 11,2).
2. **VL robusta en relativo:** u_k = A_k/C_k; VL_rob = 1 − med(u de las 2 últimas)/med(u de las 2 primeras); σ_VL ≈ √2·ε_T.
   - **Puerta:** ε_T ≤ 2,5 % para mostrar (D29) y ≤ 5 % para el uso interno con σ, más un CV de la amplitud de las FULL ≤ 10 %, para que la «pérdida» no sea un cambio de ROM.
3. **RIR por caída lineal:**
   - modelo: v_k = v_1 − (v_1 − v_f)·(k−1)/(N−1), así que RIR = (n−1)·(VL_f/VL − 1), con VL_f = 1 − v_f/v_1(%1RM);
   - v_1(%1RM) sale del perfil genérico de la sentadilla (Sánchez-Medina 2017) o de la banca (González-Badillo 2010); v_f, de la MVT;
   - solo con VL ≥ 10 %; por debajo, solo la categoría «lejos del fallo»;
   - error: σ_RIR = (n−1)·VL_f/VL²·σ_VL. Con σ_VL = 3,5 pp sale 0,4–2,4 reps según la serie; con 13,5 pp (la pose típica), 1,4–9 reps (apéndice §4).
4. **Fusión** (con N(3 RIR, 2²) como a priori si no hay RIR declarado): RIR_f = (RIR_d/σ_d² + RIR_o/σ_o²)/(1/σ_d² + 1/σ_o²), con σ_d = 1,45 (Halperin). Sin RIR declarado, sustituye al **7,0 por defecto**: es donde más vale.
5. **Aviso de discrepancia:** |RIR_o − RIR_d| > 2·√(σ_o² + σ_d²) y σ_o ≤ 2 ⇒ «El vídeo sugiere RIR ≈ 1 (declaraste 3)». **Nunca cambia nada solo.**

**Parámetros y datos:**
- **Por defecto:** σ_d = 1,45; puertas de 2,5 % y 5 %.
- **Mínimos:** ≥ 3 FULL; perfil con señal de desplazamiento; e1RM conocido para el RIR.

**Cobertura esperada:** con solo la pose, ~3 % de las series (2/59). Con el disco seguido, aunque sin m/s, ε_T baja al 0,3–1,7 % (§3.5) y la VL relativa pasa a ser útil en las series con barra.

**Esfuerzo:** **S**:
- en el motor VT, persistir `durationError` y VL_rob al final del informe y subir la versión del motor;
- en `domain/auge`, el objeto, la fusión y sus pruebas;
- en la interfaz, una línea en el análisis.

**Riesgos:**
- ciencia débil en VL→RIR (Jukic, Haischer);
- intención submáxima (tempo lento prescrito ⇒ excluir);
- rest-pause y clusters (la VL se reinicia);
- error de amplitud en oblicuo.

**Validación con el banco VT:**
- **(a) Cobertura** por perfil y fuente de señal.
- **(b) Falsos «cerca del fallo»:** en series de ≤ 6 reps sin FAILED etiquetada se espera RIR_o ≥ 2 en ≥ 90 %.
- **(c) Fallo:** las series con FAILED etiquetada deben dar RIR_o ≤ 1.
- **(d) Diezmado** 60→30→15 fps en los clips de ≥ 50 fps, para medir ε_T frente a fps.
- **(e) Pose frente a disco** en los clips que tienen ambos.
- **(f) Sintéticos** (`SyntheticLifter` con VL programada de 0/10/20/33 %), con barrido de ruido.
- **Lo que el banco no puede dar:** el RIR real. Para eso hace falta el protocolo del teléfono de V1.

**Veredicto:** **adaptar** (cobertura baja, pero coste bajo y beneficio claro donde falta el RPE).

### 7.2 Fase V1 · m/s calibrados, RIR absoluto, corrección del drenaje y aprendiz

**Requisitos:**
- `VELOCITY_MPS` en `available`: disco confirmado, diámetro y seguimiento ininterrumpido;
- vista SIDE y fs ≥ 30;
- una configuración con MVT a priori (las 5 reglas actuales).

**Modelo RIR–velocidad individual por configuración c [LITERATURA + INFERIDO]:**
- **Modelo:** RIR_k = β_c·(v_k − v0_c), con v_k la MCV de la repetición k (m/s). Morán-Navarro 2019 y Jukic 2024 respaldan que no depende de la carga.
- **A prioris:**
  - v0_c ~ N(MVT_c, (0,2·MVT_c)²), porque el CV de la V1RM es del 22,5 % (Banyard 2017);
  - 1/β_c ~ 0,05 m/s por repetición ± 50 % [INFERIDO]: 1 RIR ≈ 2,5–3,3 % del 1RM ≈ 0,04–0,06 m/s según la pendiente del perfil de la sentadilla (apéndice §4).
- **Actualización:** regresión lineal bayesiana de 2 parámetros.
  - Cada serie al fallo (`isFailure` o FAILED observada) aporta n puntos exactos: RIR_k = n − k.
  - Los RIR declarados entran como puntos débiles (σ = 1,45).
- **Salida:** RIR_o = β̂·(v_last − v̂0), con σ por el método delta: 1,1–2,1 reps con σ_v de 0,03–0,05 y σ_v0 de 0,03–0,06 (apéndice §4).
  - Fusionado con el declarado sale σ ≈ 0,8–1,2 reps, con un peso del objetivo de 0,3–0,7.

**Corrección del drenaje** (sin un multiplicador nuevo: una sola fuente de verdad):
- RPE_usado = 10 − RIR_f;
- con RPE declarado, |RPE_usado − RPE_d| ≤ 1,5;
- el `rpeMult` de `calculateSetBatteryDrain` hace el resto;
- con una VL ≥ 40 % en una serie declarada lejos del fallo, el efecto llega al tope (+1,5).
- **Por qué no aparte para el SNC:** Rodríguez-Rosell 2020 liga la fatiga tras la serie a la VL. Esta corrección ya la recoge, porque la VL entra en el RIR.

**Observación para el aprendiz** (enganche en el paquete 3.2 o 3.3 de Rings v4):
- **Medida:** el «ΔRIR emparejado objetivo», con la misma carga (±2,5 %) y las mismas reps (±2) que la mediana histórica del RIR_o, el gemelo objetivo de `matchedRpeDelta`.
- **Energía implícita:** la predicha − 8·ΔRPE_o.
- **Datos:** σ ≈ 12 pts; fuente `video_v1`; id = `mediaId` (idempotente); solo en ejercicios de varios pilares o con cnc ≥ 3,5, como hoy.

**e1RM:**
- el `LoadVelocityProfile` (beta) ya existe y no se toca;
- usarlo en el `nearRm` de AUGE cuando falte `estimatedRm` es una **decisión** (§8-4).

**Información, no prescripción:** la VL va con zonas ≤ 10 / 10–20 / 20–30 / > 30 % y «≈».

**Mínimos:**
- 1 serie al fallo por configuración para tener un v0 personal;
- 3 para β;
- antes de eso solo se muestra la VL.

**Esfuerzo:** **M**:
- el modelo y la fusión;
- el campo congelado en `CompletedSet`;
- el enganche del aprendiz, después de 2.2 (`SessionImpact`) y 3.2;
- la línea en la interfaz;
- las pruebas.

**Prerrequisitos:**
- M1 (el paso de pose);
- m/s validados en el Z Flip5;
- adquisición del disco fiable (P1 de WL_ANALYSIS_RE).

**Riesgos:**
- **Calibración:** un diámetro mal puesto da un error sistemático de la escala, y PLATE_SCALE_MISMATCH tiene poco margen.
- **La cámara cambia entre sesiones.**
- **Intención y ROM:** sin intención máxima no vale; la profundidad debe ser constante (puerta por `depthClass` y CV del ROM).
- **Cumplimiento:** hay que filmar.
- **Confianza de la semilla:** **la confirmación de la semilla en cada vídeo** limita la cobertura (§8-3).

**Validación:**
- **(a) Sintéticos:** `SyntheticPlateFrames` + `CalibratedVelocity`, ya probados a ±0,02 m/s (60 fps) y ±0,03 (30 fps).
- **(b) Teléfono, desplazamiento conocido:** el disco subido a una altura medida, 3 sesiones; error de la escala ≤ 3 % y MCV frente a un cronometraje de 240 fps si el móvil lo graba.
- **(c) Teléfono, series al fallo** al 70–85 % (3 por ejercicio): el RIR exacto de cada repetición; aceptación con el error medio absoluto del RIR ≤ 1,5 en la reserva temporal (las últimas sesiones).
- **(d) Banco:** no sirve para los m/s (diámetros desconocidos, D5); sí para la cobertura y la continuidad del disco.

**Veredicto:** **adoptar** para barra con disco, **descartar** sin disco.

### 7.3 Fase V2 · Readiness diaria objetiva

**Señal:** v_probe = mediana de la MCV de las 2 primeras FULL de la primera serie de un ejercicio sonda: una configuración con barra y m/s, a ser posible la sentadilla o la banca, con carga ≤ 80 % del e1RM.

**Línea base:** un perfil carga-velocidad personal por configuración:
- **Ajuste:** robusto (Huber), v = a + b·L.
- **Datos:** las primeras repeticiones de las primeras series de las últimas 6 semanas.
- **Pesos:** w = 2^(−edad/21 d), para no confundir la adaptación con la fatiga.

**Residuo y ruido [INFERIDO]:**
- r = v_probe/v̂(L) − 1;
- TE_p = 1,4826·MAD de los últimos ≥ 8 residuos, con un suelo del 4 % (la biología con transductor);
- σ_r = TE_p·√(1 + h), con h la palanca de la carga de hoy;
- z = r/σ_r.

**Reglas:**

| Banda | Lo que se hace |
|---|---|
| \|z\| < 1 | «dentro del ruido» |
| 1 ≤ \|z\| < 1,65 | se informa sin efecto |
| z ≤ −1,65 **y** confirmación en la 2.ª serie o la 2.ª carga | «por debajo de lo esperado» |

**Efecto en AUGE, acotado:**
1. **Observación para el aprendiz** (Energía y los pilares primarios del ejercicio sonda):
   - Energía implícita = la predicha + 8·r/q(L), con q(L) = la fracción de la velocidad por unidad de RPE: 3,7–11,6 % según el %1RM, sacada del perfil (apéndice §4);
   - σ = 8·σ_r/q ≈ 10–14 pts;
   - |Δ| ≤ 20 pts;
   - las sorpresas positivas pesan ×0,5 (como el plan v4).
2. **«RPE objetivo del día»:** solo **sugiere bajar** (−0,5 a −1 RPE o −2,5 a −5 % de carga en las series pesadas), dentro del tope del 30 % de la readiness actual. Nunca sube sola.

**Mínimos:**
- ≥ 6 sesiones sonda;
- ≥ 3 cargas que cubran ≥ 20 % del e1RM;
- ≥ 3 semanas;
- si no, «calibrando 3/6».

**Esfuerzo:** **M–L**. Necesita:
- V1;
- el marcado de la serie sonda;
- un almacén del perfil por configuración, dentro del caché JSON, sin Room;
- la interfaz;
- las pruebas.

**Riesgos:**
- **Factores de confusión:** el calentamiento, la hora del día, la intención, el ROM y la cámara.
- **Deriva por adaptación.**
- **Uso:** pocas sondas filmadas.
- **Sobrerreacción al ruido:** el MDC95 queda en ~10–14 % incluso en el mejor caso.
- **Efecto «nocebo» de ver «baja readiness»** [INFERIDO].

**Validación:**
- **(a) Arnés de usuarios sintéticos** de Rings v4 (paquete 0.3): una readiness latente con observaciones de velocidad de TE 4–10 %. Se mide si el error del τ aprendido mejora o empeora al añadirlas, con la guardia de volver al a priori.
- **(b) Teléfono, 4–6 semanas** de sondas: el TE personal real.
- **(c) Teléfono, sensibilidad:** una sesión fatigante (5×5 al 80 %) y sondas a 24, 48 y 72 h; se espera una caída grande a 24–48 h y la vuelta a 72 h (Vernon 2020).
- **El banco VT no sirve:** no hay medidas repetidas de la misma persona.

**Veredicto:** **adaptar**: solo como observación del aprendiz y sugerencia que reduce, nunca como veredicto en el anillo.

### 7.4 Otras ideas

| Idea | Veredicto | Módulo | Datos | Esfuerzo | Riesgo | Prueba |
|---|---|---|---|---|---|---|
| Trabajo mecánico por serie (§3.7) | **Adaptar**: calcular y guardar; mostrarlo en las tendencias; como factor de ROM solo para Columna (axial), si el usuario lo decide | VT (cálculo); `AugeFatigueEngine` (Columna) | Δh (disco o pose × estatura), BM, f_seg | S | Ciencia débil como «fatiga»; error de BH ±10–15 % | Sintéticos con ROM conocido; coherencia completa/media en el banco |
| Usar `CompletedSet.rom` y el ROM del vídeo en el drenaje | **Adaptar con cautela** (solo axial) | `AugeFatigueEngine` | `rom` ya registrado | S | Las parciales en estiramiento estimulan; el ROM no escala lineal con el estímulo [INFERIDO] | Tabla antes/después |
| Sonda CMJ diaria (perfil JUMP) | **Decisión del usuario** | VT (perfil nuevo) + V2 | Vídeo de 3 saltos a ≥ 60 fps | M | Ritual diario; pose de los pies | Diezmado, test-retest y fatiga conocida |
| VL robusta en el motor VT | **Adoptar** (propuesta a la ola VT) | `RelativeVelocity`, `CalibratedVelocity` | — | S | Cambia las cifras publicadas (sube la versión del motor) | Monte Carlo (§3.2) y sintéticos |
| La literatura como diana del τ | **Adoptar** como prueba | Arnés de Rings v4 (0.3) | — | S | — | El fallo hace más lenta la recuperación neuromuscular, con efectos de hasta 24–48 h (Morán-Navarro 2017; González-Badillo 2016); todo se recupera a las 72 h (Thomas 2018) |
| «SNC fatigado» por velocidad | **Descartar** el nombre | Textos | — | — | Engañoso | — |

**Lista priorizada de entregables:**

| Prioridad | Entregable | Esfuerzo |
|---|---|---|
| P0 | M1 integrado (paso de pose) y m/s validados en el Z Flip5 con desplazamiento conocido (pendiente VT) | — |
| P1 | Persistir `durationError` + VL_rob en el informe; `ObjectiveSetEffort` V0 con fusión «solo si falta el RPE»; aviso de discrepancia | S |
| P2 | Modelo RIR–velocidad V1 con calibración por series al fallo; campo congelado en `CompletedSet`; línea en la tarjeta | M |
| P3 | Observación `video_v1` en el aprendiz de Kalman (con Rings v4 3.2–3.3) | M |
| P4 | Trabajo mecánico por serie (solo informativo) | S |
| P5 | V2 readiness, tras ≥ 6 semanas de datos V1 y el TE personal medido | M–L |
| P6 (opcional) | Sonda CMJ | M |

---

## 8. Preguntas abiertas y decisiones para el usuario

**Decisiones:**

**1. ¿Puede AUGE usar la evidencia del vídeo sin «Usar/Corregir»?**
- Pro: más cobertura; el análisis llega tarde y muchas veces no se toca.
- Contra: rompe la filosofía de D3 («nunca se aplica solo»).
- Recomendado: solo con la serie aceptada, o solo cuando falte el RPE, y con el tope ±1,5 RPE.

**2. ¿Levantar la regla VT «RIR o RPE por pérdida de velocidad (no en v1)»** (`VIDEO_TECHNIQUE_V1.md:883`)?
- Sin eso, V0 y V1 solo pueden ir **internas** a AUGE, sin mostrarse.
- Recomendado: mostrar «RIR ≈ x (±y)» solo en V1 (m/s), no en V0.

**3. Semilla del disco:** ¿confirmación una vez por ejercicio y sesión, o semilla automática con control de identidad, en lugar de por vídeo?
- Pro: más cobertura de m/s.
- Contra: riesgo de seguir otro objeto (la política actual lo prohíbe por eso).

**4. ¿Usar el e1RM por velocidad (beta) en el `nearRm` de AUGE** cuando falte el e1RM homologado, o mantener D6 en estricto?
- Recomendado: mantener D6.

**5. Sonda diaria: ¿calentamiento filmado (V2), CMJ o ninguna?**
- No es un cuestionario (la decisión cerrada de las 24 h sigue cerrada), pero añade un ritual.

**6. ¿Usar el trabajo mecánico o el ROM en el drenaje de Columna?**
- Cambia números visibles: va con tabla antes/después.

**7. HRV y readiness** (Kubios/Morpheus): requieren Health Connect o un sensor, cerrado. Reabrirlo es decisión del usuario; B2 da los pros y contras.

**8. Referencia física:** ¿conseguir prestado un transductor lineal para validar los m/s y el RIR?
- No propongo comprar nada.
- El desplazamiento conocido basta para la escala, no para el RIR.

**Preguntas abiertas:**
- fps reales sostenidos a 60 en el Z Flip5, y si la cámara deja grabar a 120/240 a través de CameraX [NO VERIFICABLE];
- qué fracción de series filmaría un usuario real;
- el ruido correlacionado de MediaPipe en el teléfono frente al sintético;
- el diámetro real de los discos de cada gimnasio;
- si el motor 6 mejora las cifras del motor 1 (VT11 en curso).
