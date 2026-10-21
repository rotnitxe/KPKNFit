# B3 · Modelado de carga: impulso–respuesta (Banister/PMC), ACWR con EWMA, monotonía/tensión y paneles de equipo

Fecha: 2026-10-21 · Alcance: literatura y documentación pública (sin APK) + lectura del motor AUGE (solo lectura, `C:\kw\r4i`) + simulaciones deterministas propias (`C:\kw\research\tools\b3\`).
Etiquetas: **[OBSERVADO]** código de AUGE · **[LITERATURA]** fuente publicada · **[INFERIDO]** deducción mía · **[NO VERIFICABLE]**.
Referencias `[Lx]` (literatura), `[Cx]` (código), `[Sx]` (simulaciones): lista completa con DOI/PMID y estado de lectura en `B3-carga-evidencia.md`.

## 0. Resumen ejecutivo

B3 pregunta cómo las herramientas profesionales convierten el historial de carga en «fitness/fatiga» y en «carga reciente frente a habitual», y qué cabe en AUGE sin tocar sus decisiones cerradas.

1. **PMC (CTL/ATL/TSB) = Banister con ganancias fijas.** CTL y ATL son medias exponenciales de la carga diaria (τ 42 y 7 d en TrainingPeaks) y TSB = CTL − ATL con los valores *de ayer* [L16,L17]. Equivale a Banister con k1 = α_c y k2 = α_a [S5]. Los anillos de AUGE son la capa rápida (horas–días); el PMC aporta lo que ellos no tienen: memoria de semanas.
2. **Banister no es identificable con datos de app.** En mi simulación, con el modelo bien especificado, τ1 acierta a ±30 % solo en el 12–35 % de los casos y los τ libres predicen peor que los fijos en 7 de 8 escenarios [S3]; Marchal 2025: la parte de fatiga no mejora la predicción (p > 0,40) [L9]. Decisión de diseño: τ fijos, sin aprendizaje por usuario.
3. **ACWR: descriptivo, no predictor de lesión.** Críticas serias (Impellizzeri 2020/2021, Lolli 2019, Wang 2020) y meta-análisis 2026 con g = 0,35 e I² = 96 % [L26–L29,L33]. Acoplado frente a desacoplado es un reescalado monótono (R_c = W·R_u/(R_u+W−1)); dentro de un mismo usuario la razón no separa un pico real mejor que la carga aguda sola (AUC 0,86–0,88 frente a 0,88) y su valor es la escala común entre usuarios (0,70 → 0,85–0,87) [S6].
4. **Brecha.** AUGE tiene ACWR acoplado 7/28 d, capacidad crónica por músculo (1,8 × media semanal de 35 d) y avisos con histéresis; no tiene serie diaria por canal, PMC, razón EWMA/desacoplada, monotonía ni la Energía de dos escalas (S10). Hallazgos de código: tres definiciones de «carga crónica», dos EMA de estrés divergentes, días en UTC y rótulos «Zona de Riesgo/Alto Riesgo» que prometen más de lo que la evidencia permite.
5. **Recomendación.** Módulo puro `domain/auge/load` (EWMA con pesos normalizados; PMC τ 7/28; razón S7/EWMA₂₈{S7} retrasada 7 d; monotonía opcional), panel «Carga» de solo lectura, mapa de calor desde las baterías, S10 con banda muerta; nada retroalimenta a los anillos hasta que pase el backtest.

**No pude ver:** el texto completo de varios artículos (403, PDF comprimido), documentación de BridgeAthletic/StrengthCoachPro/Smartabase, parámetros τ de levantadores (Busso 1990), capturas de interfaz, y no existe ACWR validado en fuerza (ver §3.4).

## 1. Identidad y arquitectura

No es una app: es un cuerpo de métodos (Banister 1975 → PMC de Coggan → ACWR de Gabbett → Foster) y su documentación. Todos son **funciones puras de una serie diaria de carga**; lo que hacen los servidores de TrainingPeaks, Polar o Garmin es ejecutarlas [INFERIDO]. Por eso encajan con el requisito de determinismo de AUGE.

Dónde vive hoy la lógica de carga en AUGE [OBSERVADO]:

| Pieza | Archivo:línea | Qué hace |
|---|---|---|
| Umbrales | `AugeClassifiers.kt:15-28` | zonas 0,8/1,3/1,5; avisos 1,2/1,4/1,6; histéresis 10 % (el propio comentario deja «alinearlos» como decisión pendiente) |
| ACWR | `AugeClassifiers.kt:81-103` | aguda 7 d ÷ (suma 28 d ÷ clamp(días_seguimiento/7, 2, 4)); **acoplada** |
| Factor muscular | `AugeClassifiers.kt:110-115`; `AugeRecoveryEngine.kt:351-373,1100-1106` | 1,0 → 1,12 sobre el déficit del anillo global (+0,06 a 1,3→1,5 y +0,06 a 1,5→1,7) |
| Monitores | `AxialLoadMonitor.kt:23-74`; `SystemicLoadMonitor.kt:15-57` | compuertas ≥ 4 sesiones y ≥ 14 d; carga axial = Σ axial × factor relativo; Energía = drenaje CNS |
| Avisos | `LoadAdvisoryEngine.kt:103-278` | WATCH (≥1,2), ADJUST (>1,4), UNLOAD (>1,6 con tendencia); baja un nivel por día |
| Acción en programa | `ProgramAutoregulationEngine.kt:932-962` | UNLOAD → volumen ×0,8 o descarga si readiness < 40 |
| Capacidad crónica | `AugeMuscleCapacityEngine.kt:14-51`; `AugeRecoveryEngine.kt:380-457` | capacidad = máx(base, 1,8 × media semanal) |
| Carga por sesión | `WorkoutFinishController.kt:310-348`; `MuscularSessionImpactEngine.kt:37-44` | `sessionStressScore`; `stressUnits` por músculo persistido |
| EMA de estrés | `AugeFatigueEngine.kt:887-971`; `ProgramRepository.kt:1084-1090` | α = 0,17 por sesión, dos versiones |

## 2. Entradas de datos

**2.1 Qué unidad de carga usar** (para alimentar PMC/ratio):

| Unidad | Definición | Canal | Pros | Contras | Veredicto |
|---|---|---|---|---|---|
| Volumen–carga | Σ kg×reps | ejercicio | objetiva | no comparable entre ejercicios ni mide intensidad relativa [L44] | solo descriptiva |
| Series duras efectivas | series RPE ≥ 8 / RIR ≤ 2 por músculo | músculo | alineada con hipertrofia | ciega a SNC y axial | complemento (B1) |
| sRPE × min (Foster) | RPE de sesión CR-10 × minutos | sistémico | válido en fuerza, pero depende del modo [L43] | pedirlo; `fatigueLevel` a veces es **inferido** (voz: `WorkoutViewModel.kt:6733`) | opcional |
| TRIMP / TSS | FC / potencia | cardio | estándar en resistencia | AUGE no tiene FC (Health Connect fuera) | no aplica |
| INOL | Σ reps/(100−%1RM) por serie [L57] | ejercicio | normaliza por %1RM | exige 1RM; inestable cerca de 100 % | descartar como unidad cruzada |
| `sessionStressScore` | (0,45 CNS + 0,25 músc + 0,30 espinal) × impacto × técnica (1–1,5) × claridad | escalar | ya existe | mezcla canales, factores subjetivos | solo resumen de sesión |
| **Drenaje AUGE por canal** | `stressUnits` por músculo, CNS, espinal | músculo, SNC, axial | por canal; ficha, RIR, descanso, densidad y series acumuladas ya dentro | circular (el modelo se mide a sí mismo) | **recomendada** |

**2.2 Qué hay en el log** [OBSERVADO]: `muscularImpactV2.perMuscle[m]` (stressUnits, direct/indirect, capacidad al cierre) persistido; CNS y espinal **se recalculan** en cada llamada (`SystemicLoadMonitor.kt:27-38`); `durationMinutes`, `fatigueLevel`, `ringStartSnapshot`, `postExerciseReports`, `actualDate`. El plan 6.2 (`sessionImpactV3`) persistirá `cnsLoad/spinalLoad`.

**2.3 Carga diaria por canal propuesta** (día **local** con `ZoneId` explícito; suma de sesiones del día; 0 = descanso; logs con fecha ≥ ahora se ignoran):

- Músculo m: L_m[d] = Σ stressUnits(m) de los logs del día (si falta V2, recalcular como `AugeMuscleCapacityEngine.kt:38-45`).
- Energía: L_E[d] = Σ drenaje CNS × (1,08 si > 75 min; 1,15 si > 90 min, como `AugeRecoveryEngine.kt:819-821`).
- Columna: L_S[d] = Σ drenaje espinal con compuerta axial posterior (`AugeRecoveryEngine.kt:1000`); **no** `axialUnits`, para usar la misma unidad que el anillo (hoy ACWR y anillo de Columna usan unidades distintas [C6]).
- Normalización entre sesiones distintas (piernas frente a brazos): las razones son adimensionales; para mostrar absolutos, «% de capacidad» = 7·CTL / capacidad del músculo.

## 3. Métricas y fórmulas

### 3.1 Banister (impulso–respuesta)

| Campo | Contenido |
|---|---|
| Definición | p(n) = p0 + k1·Σ_{s<n} w(s)·e^{−(n−s)/τ1} − k2·Σ_{s<n} w(s)·e^{−(n−s)/τ2} [L13] |
| Régimen con retorno positivo | 0 < k1 < k2 y 0 < τ2 < τ1 |
| Una carga aislada | cruce t_r = (τ1τ2/(τ1−τ2))·ln(k2/k1); pico t_p = (τ1τ2/(τ1−τ2))·ln(k2τ1/(k1τ2)) [L13; comprobado numéricamente S5] |
| Variantes | Busso 2003: k2 crece con la carga reciente (k3, τ3) [L6,L13]; sin fatiga (modelo «solo fitness») [L9]; multicanal, un par fitness/fatiga por sistema energético (preprint 2025, sin parámetros recuperados ni uso en fuerza) [L14] |
| Crítica de identificación | Hellard 2006 (9 nadadores): R² = 0,79 ± 0,13 pero IC95 % anchos (τ_fitness 38 [17–59], τ_fatiga 19 [6–32]) y algunos parámetros muy correlacionados [L7]; Marchal 2025: mal condicionado, sobreajuste, la fatiga no mejora la predicción [L9]; Vermeire 2022: no usar constantes generales, usarlo «informado por datos» [L8]; Imbach 2022: ENET generalizó mejor que el modelo dosis-respuesta (n = 7) [L10] |

Parámetros publicados (todos de resistencia salvo indicación; ninguno de fuerza recuperado [NO VERIFICABLE]):

| Fuente | Población | τ1 (fitness) | τ2 (fatiga) | k1 / k2 |
|---|---|---|---|---|
| Banister et al. 1975 (vía fuentes secundarias) [L1] | 1 nadador | 50 d | 15 d | — |
| Busso et al. 1991 (citado en [L13]) | 8 ciclistas | 38 d | 1,9 d | 0,048 / 0,117 |
| Busso 2003 [L6,L13] | 6 varones no entrenados | 30,8 d | 16,8 d (τ3 = 2,3) | k1 = 0,031 |
| Hellard 2006 [L7] (k vía [L12]) | 9 nadadores de élite | 38 (17–59) | 19 (6–32) | 0,036 / 0,050 |
| TrainingPeaks (Coggan) [L16] | valor por defecto | 42 | 7 (4–5 jóvenes o baja carga; 10–12 veteranos o alta carga) | ganancias eliminadas |
| Levantadores: Busso 1990/1992 [L3,L4] | 6 de élite | — | — | no leí los valores |

### 3.2 PMC de TrainingPeaks

| Campo | Contenido |
|---|---|
| CTL / ATL | EWMA de la carga diaria; TrainingPeaks documenta CTL_d = CTL_{d−1} + (L_d − CTL_{d−1})/τ, τ_c = 42, τ_a = 7 [L16,L17]. Con α = 1/τ el τ efectivo es −1/ln(1−1/τ) = **41,5 d y 6,5 d**; la forma exacta es α = 1 − e^{−1/τ} [S5] |
| TSB (forma) | CTL − ATL con **valores de ayer** [L17]; Intervals.icu muestra la forma como % del fitness [L59, foro no oficial]; otras convenciones («hoy») [NO VERIFICABLE] |
| Relación con Banister | (origen: Banister 1975, Morton 1990 [L1,L2]) quita las ganancias absolutas: TSB(t) = Σ w·(α_c·e^{−Δ/τ_c} − α_a·e^{−Δ/τ_a}), es decir k1 = α_c, k2 = α_a [INFERIDO, S5]. Una carga aislada deja TSB < 0 durante t_r = 14,6 d (42/7) y alcanza el máximo (≈ 1 % de la carga) hacia t_p = 29,6 d |
| Zonas orientativas | TSB < −10 «no fresco», > +10 «fresco», entre medias neutro [L16] |
| Rampa | CTL subiendo más de 5–7 TSS/día por semana durante ≥ 4 semanas se asocia a enfermedad o sobreentrenamiento; meseta de 4–6 semanas sugiere estancamiento [L16] |
| TSS | horas × IF² × 100; IF = NP/FTP; NP = (media de (media móvil de 30 s de la potencia)⁴)^{1/4} [L16,L17] |
| Arranque | CTL y ATL iniciales ≈ 50–75 TSS por hora de entreno típico y TSB = 0 [L16] |
| Límites declarados | descriptivo; sin techo de rendimiento; ajustarlo exigiría 20–200 mediciones; parámetros inestables; τ_a consistente entre estudios, τ_f variable [L16] |

### 3.3 Equivalencias de EWMA (trampa frecuente: N frente a τ)

| Definición | Constante | τ (e-folding) | Vida media | Edad media |
|---|---|---|---|---|
| TrainingPeaks CTL | 1/42 | 41,5 d | 28,8 d | 41,0 d |
| TrainingPeaks ATL | 1/7 | 6,5 d | 4,5 d | 6,0 d |
| Williams 2017, N = 28 (λ = 2/(N+1) = 0,069) [L21,L22,L58] | | **14,0 d** | 9,7 d | 13,5 d |
| Williams 2017, N = 7 (λ = 0,25) | | 3,5 d | 2,4 d | 3,0 d |
| Athlytics (R, rOpenSci): vidas medias 3,5 y 14 d [L56] | α = 1 − e^{−ln2/h} | 5,0 y 20,2 d | | |

Una «EWMA de 28 días» de Williams equivale a τ = 14 d, tres veces más corta que el CTL de 42 d. La recursión EWMA_hoy = carga_hoy·λ + (1−λ)·EWMA_ayer con λ = 2/(N+1) y N = 1 y 4 semanas (aguda y crónica) está impresa en Arazi 2020 [L58], que sigue a Williams y Murray; no pude leer la ecuación en la carta original (imagen embebida).

### 3.4 ACWR

| Variante | Fórmula | Notas |
|---|---|---|
| Rodante acoplada (Gabbett) | S7 / (S28/4) [L18,L19] | la semana aguda está dentro de la crónica; **es la de AUGE** |
| Desacoplada | S7 / (suma de los días 8–28 / 3) [L24,L25: definición estándar; los editoriales no traen resumen] | equivalencia exacta con la acoplada: R_c = W·R_u/(R_u+W−1) [INFERIDO]; con W = 4: 1,3 ↔ 1,44; 1,5 ↔ 1,80 |
| EWMA | EWMA_a / EWMA_c, λ = 2/(N+1), N = 7 y 28 [L21,L22,L58] | Murray 2017: 59 jugadores AFL, ACWR > 2,0 → RR 6,5–21,3 en distancia total; EWMA más sensible que la rodante. Arazi 2020 (soccer sub-18, preliminar): r con lesión sin contacto 0,53 (EWMA) frente a 0,31 (rodante) |
| Umbrales | 0,8–1,3 «punto dulce», ≥ 1,5 «peligro» (atribuidos a Gabbett 2016 y Blanch & Gabbett 2016, fuente secundaria [L18,L20]); Garmin: 0,8–1,4 óptimo, 1,5–1,9 alto, ≥ 2,0 muy alto, visible tras 2 semanas [L51] | Sedeaud (citado en el editorial de Zouhal 2021): sin evidencia del «punto dulce» [L37] |
| A favor | Hulin 2014: 28 lanzadores, balance negativo RR 2,2, > 200 % RR 4,5 [L19]; Blanch & Gabbett 2016: R² = 0,53 [L20]; Griffin 2020 (22 estudios): asociación con lesiones sin contacto, EWMA preferible [L35] | deportes de equipo y raqueta |
| Críticas | Impellizzeri 2020: sin evidencia para usarlo en gestión de carga ni para reducir lesiones; ruido y artefactos de ratio [L26]. Impellizzeri 2021: dividir por un crónico **inventado** (fijo o aleatorio) da OR 1,95 (fijo) y 1,16–2,07 (aleatorio) frente a 2,45 con el real; ni ACWR ni carga aguda mejoran un modelo nulo (c = 0,574/0,544) [L27]. Lolli 2019: acoplamiento matemático [L24]. Wang 2020: la EWMA tiene «problema de carga inicial» y no sirve con tapering [L29]. Kalkhoven 2021: la carga psicofisiológica queda lejos de la causa [L30]. West 2025: muchas guías de carga son arbitrarias [L32] |
| Síntesis reciente | Ding 2026 (meta-análisis, 16 estudios, 797 atletas): g = 0,35 (IC 0,16–0,54), I² = 95,8 %; no sirve como modelo causal o predictivo único, solo como indicador contextual [L33]. Andrade 2020 (20 estudios): 95 % usó la versión acoplada y hubo 14 formas distintas de categorizar [L36]. Zouhal 2021: «no abandonar» como herramienta simple, causalidad sin probar [L37]. Alternativa sin razón: «carga diferencial» (diferencia entre aguda y crónica; definición no releída) en cricket, RR 2,47 por +2 DE [L39] |
| Fuerza | **No encontré** estudios ACWR–lesión en entrenamiento de fuerza/hipertrofia (Europe PMC 2017–2026, primeros 13 de 120 resultados; ninguno lo era) [NO VERIFICABLE] |

### 3.5 Monotonía, tensión, sRPE, TRIMP

| Métrica | Definición | Constantes / umbrales | Aplica a fuerza |
|---|---|---|---|
| sRPE | RPE de sesión (CR-10, 30 min tras acabar) × minutos [L42,L43,L45] | — | sí, válido, aunque el modo (potencia, lento, tradicional) cambia el RPE a igual carga y puede subestimar la intensidad media por serie [L43] |
| Monotonía | media diaria / DE de los 7 días (descansos = 0) [L42] | > 2,0 «alta» (usada por TeamBuildr; origen en Foster 1998 [NO VERIFICABLE en esta sesión]) | poco: 3 días 0,80; 4 días 1,04; 5 iguales 1,46; **6 días 2,1–2,3** [S1,S7] |
| Tensión | carga semanal × monotonía [L42] | umbrales **individuales** en el original (25 atletas) | idem |
| TRIMP Banister | D · ΔFC · 0,64·e^{1,92·ΔFC} (varones), 0,86·e^{1,67·ΔFC} (mujeres) [constantes de memoria, no releídas] | | no (sin FC) |
| TRIMP Edwards | Σ min en zona × {1,2,3,4,5} para 50–60 … 90–100 % FCmáx [resumen de buscador] | | no |
| Lucia / Stagno / iTRIMP | pesos por zonas ligadas a umbrales ventilatorios o de lactato [resumen de buscador] | | no |
| INOL | Σ reps/(100−%1RM); 0,4–1,0 por sesión «óptimo» [L57, fuente secundaria] | | por ejercicio |

### 3.6 τ plausibles para fuerza e hipertrofia

| Proceso | Evidencia | Escala | τ sugerido |
|---|---|---|---|
| Fatiga neuromuscular | series sin fallo recuperan salto y velocidad en 24–48 h; el fallo alarga; seguimiento hasta 72 h [L46] | 1–3 d | fuera del PMC: lo cubren las baterías (e-folding de AUGE tras 24 h: 6–26 h [S4]) |
| Carga reciente (ventana de microciclo) | convención PMC 7 d; ciclo semanal de 3–5 sesiones | 7 d | τ_a = 7 d (Energía 10 d) |
| Adaptación (fitness) | la fuerza se pierde lentamente (efecto crece con la duración: SMD −0,46 en fuerza máxima, 103 estudios) [L47]; ciclos de 6 semanas de entreno + 3 de parada igualaron la ganancia total de 24 semanas continuas (n = 14, press banca) [L47]; τ1 de resistencia 31–50 d [L1,L6,L7,L16] | 4–8 semanas | τ_c = 28 d (hábito) y 42 d opcional (condición) [INFERIDO] |

AUGE usa «τ» como tiempo hasta 95 % de recuperación con alabeo (`AugeParameters.kt:119`), pero `InterferenceEngine.kt:18,26-38` lo trata como **vida media**: no mezclar al mapear a τ del PMC [C7].

## 4. Algoritmos y reglas de decisión

**Lo que hacen los profesionales:** TSB por bandas (−10/+10) [L16]; CTL con rampa máxima 5–7/semana [L16]; ACWR por bandas (Garmin 0,8/1,4/1,9/2,0 [L51]; Polar: «Strain» = media diaria de 7 d y «Tolerance» = media diaria de 28 d, estado según su cociente y, desde su actualización, también valores absolutos y 14 días de historial para no marcar «sobrecarga» a usuarios nuevos [L50]); salto semanal > 15 % y monotonía > 2 (caso Salisbury en TeamBuildr; vendor, no evaluación [L54]).

**Lo que la literatura permite hacer con ello:** tratar la razón como señal descriptiva de cambio de carga junto a otras (Ding 2026, Zouhal 2021; el consenso del COI pide vigilar los cambios rápidos de carga junto al bienestar [L38]) y no prescribir recortes automáticos basados solo en ella (Impellizzeri 2020, West 2025) [L26,L32,L33,L37]. Sin ensayos que muestren que *manipular* el ACWR reduce lesiones; las reglas simples de progresión tampoco están respaldadas: en 874 corredores noveles progresar > 30 %/semana no dio diferencia significativa (HR 1,59, IC 0,96–2,66) y un programa graduado «del 10 %» no redujo lesiones en un ensayo de 532 (20,8 % frente a 20,3 %) [L40].

**Lo que hace AUGE hoy** [OBSERVADO] y qué significa en saltos semanales (W = 4; tabla `b3_verify3`):

| Umbral AUGE | Uso | Salto de la semana aguda sobre la media de las 3 semanas previas |
|---|---|---|
| 0,8 | zona UNDER | −25 % |
| 1,2 | WATCH | +29 % |
| 1,3 | zona «Zona de Riesgo» | +44 % |
| 1,4 | ADJUST | +62 % |
| 1,5 | zona «Alto Riesgo» | +80 % |
| 1,6 | UNLOAD | +100 % |

Con menos historia la crónica divide entre menos semanas y las mismas cifras corresponden a saltos mayores (W = 2: 1,4 ↔ +133 %). En magnitud absoluta AUGE exige saltos grandes para avisar, pero eso no lo protege del ruido: con un usuario perfectamente estable de 4 sesiones/semana, adherencia 85 % y CV de carga 20 %, el ratio acoplado supera 1,2 el 16 % de los días, 1,3 el 7 % y 1,4 el 2,6 %; con 3 sesiones, 80 % y CV 25 % salen 25 %, 15 % y 8,6 % [S5]. Otras observaciones medidas:

- **Acantilado:** al salir una sesión pesada (250 frente a 100) de la ventana de 7 d, el ACWR cae de 1,333 a 0,889 en un día (−0,44) [S1,S5].
- **Vuelta tras 3 semanas sin entrenar** (una sesión de 100 con hábito de 3/semana): AUGE marca 1,333 «Zona de Riesgo»; con crónica EWMA de las sumas semanales sale 0,53 [S1,S6]. Cuál es «correcto» depende de la rapidez con que se pierde la adaptación en fuerza (lenta [L47]) [INFERIDO].
- **EMA de estrés con semilla 0** (`AugeFatigueEngine.kt:914-916`): con sesiones constantes de 60 puntos queda en 10,2 y 18,7 las dos primeras y por debajo de 50 las nueve primeras; `adjustPredictedDrainWithEMA` (umbrales 25/50, exige ≥ 2 sesiones) solo llega a sumar +2,5 % al drenaje previsto de la tercera sesión, pero retrasa la detección de estrés alto. `ProgramRepository.kt:1084-1090` usa semilla = primer valor: **dos EMA distintos para lo mismo** [C3,S5].
- Tres definiciones de «carga crónica semanal»: ACWR (28 d ÷ 2–4 semanas), capacidad de cierre (35 d ÷ 1–5 semanas × 1,8, `AugeMuscleCapacityEngine.kt:23,47-50`) y capacidad de respaldo (28 d con rampa a 35 d ÷ 4 × 1,8, `AugeRecoveryEngine.kt:402-405,454-456`) [C4].
- Días en **UTC**: `AxialLoadMonitor.kt:146`, `LoadAdvisoryEngine.kt:111,272-277`; una sesión a las 21:00 en UTC−3 cuenta como el día siguiente [C5].

## 5. Conceptos de interfaz y visualización

- **Gráfico PMC** (patrón de TrainingPeaks): área para la carga habitual, línea para la reciente, barra o banda con signo para el balance y marca de «hoy»; el balance oscila ±10 % del CTL aun con entrenamiento estable (−8,8 % a +12 % con 4 sesiones/semana, rango 20,7 puntos; solo 10,5 con 6 días) [S2]: hay que explicarlo o mostrar la media de 7 días.
- **Razón con tres o cuatro estados** descriptivos (Polar: sobrecarga/productivo/mantiene/desentrena; Garmin: bajo/óptimo/alto/muy alto) y espera visible («aparece tras 2 semanas»).
- **Barras semanales + línea de «tu semana habitual» + banda personal** (más natural que un número).
- **Mapa de calor corporal.** En plataformas de equipo es casi siempre un **informe subjetivo de molestias**: TeamBuildr (Body Heat Map: la persona marca zonas y severidad cada día), Training Feel (mapa de cuerpo), CoachMePlus (carta corporal), Smartabase (cuestionarios con diagrama) [L54,L55]. El mapa de **carga/recuperación** existe en apps de consumo con fórmulas no publicadas: Fitbod (0–100 % por músculo, recuperación completa a 6 días), Train Fitness (≥ 85 recuperado, 84–50 moderado, 49–0 muy fatigado), BodyAtlas (fatiga con curva de recuperación, tonos cálido-frío), MuscleMap (INOL) [L53]. WHOOP separa carga cardiovascular y **muscular** (volumen como «masa efectiva», intensidad como cercanía al máximo y velocidad) [L52].
- **Semáforo de bienestar** (TeamBuildr «listo para trabajar»: dolor, fatiga, sueño, estrés) y **comparación con línea base** (Polar escala cada sesión frente a los últimos 90 días) [L50,L54].
- Plataformas B2B: Catapult define aguda 3–7 d y crónica 3–6 semanas (21 d recomendado) [L55]; Kitman ofrece «Risk Advisor» (ML) y umbrales/alertas personalizados; Smartabase, métricas definidas por el staff; BridgeAthletic, carga/RPE/preparación dentro del constructor de programas; StrengthCoachPro **no encontré documentación** [NO VERIFICABLE]. No analicé capturas ni vídeos.

## 6. Verificación del informe previo

| Afirmación | Veredicto | Evidencia |
|---|---|---|
| WKO5/TrainingPeaks: CTL = media exponencial de 42 d, ATL = 7 d, TSB = CTL − ATL | **CONFIRMADA** (con matices) | Coggan [L16]; ayuda de TrainingPeaks vía buscador [L17]. Matices: TSB usa valores de **ayer**; la receta es α = 1/τ (τ efectivos 41,5 y 6,5 d); WKO5 en sí no lo leí |
| «Modelo de impulso–respuesta de Banister» es lo que ejecutan CTL/ATL | **CORREGIDA** | el PMC elimina las ganancias k1, k2 y no predice rendimiento; es un Banister con k1 = α_c y k2 = α_a [L16,S5] |
| Plataformas B2B (StrengthCoachPro, BridgeAthletic, Smartabase, Kitman) con paneles **ACWR con EWMA desacoplado** | **NO VERIFICABLE** | ninguna documentación pública dice EWMA ni desacoplado; Catapult describe ventanas 3–7 d / 3–6 semanas; TeamBuildr (semana frente a media de 4) [L54,L55] |
| «Riesgo de lesión al pasar de 1,3–1,5» | **CORREGIDA** | umbrales atribuidos a Gabbett 2016 / Blanch & Gabbett 2016; sin evidencia de «punto dulce» (Sedeaud), ACWR no mejora un modelo nulo y causalidad sin probar [L18,L20,L26,L27,L37] |
| «Mapas de calor anatómicos» en esas plataformas | **CORREGIDA** | los de equipo son informes subjetivos de molestias; los de carga/recuperación son de apps de consumo [L53,L54] |
| «Santo Grial»: velocidad de calentamiento frente a su media de 4 semanas = fatiga neuromuscular objetiva que alimenta un Banister/ACWR | **CORREGIDA** | en Banister esa velocidad es el **rendimiento p(t)** (salida), no la carga w(s) (entrada); sirve para calibrar o como observación del aprendiz (coherente con B4). La carga mecánica (fuerza × recorrido) sí puede ser una entrada de carga externa, sin validación en fuerza |
| «Athlytic/Bevel/WHOOP: dispersión Recuperación % × Strain con túnel óptimo» | **NO VERIFICABLE** aquí (A3) | solo confirmé que el Strain de WHOOP va de 0 a 21 con cuatro tramos y que incluye carga muscular [L52] |
| Resto (Evolve, RTS, RP, HRV4Training, Kubios) | fuera del alcance de B3 | — |

## 7. Aplicabilidad a AUGE

### 7.1 Brecha

| Concepto profesional | ¿AUGE? | Dónde | Qué faltaría |
|---|---|---|---|
| Carga por sesión y canal | parcial | `muscularImpactV2`; CNS/espinal recalculados | persistir `cnsLoad/spinalLoad` (plan 6.2); serie diaria local |
| Carga crónica normalizadora | **sí, 3 versiones** | ACWR; capacidad de cierre; capacidad de respaldo | una sola H(d) |
| CTL/ATL/TSB por canal | no | — | motor puro + gráfico |
| Impulso–respuesta con τ aprendidos | no (y no conviene) | `AugeAdaptiveEngine` aprende τ de *recuperación* (otro objeto) | — |
| ACWR rodante acoplado, umbrales, histéresis | sí | §1 | desacoplado/EWMA, umbrales reescalados, etiquetas descriptivas |
| Compuerta de datos mínimos | sí (4 sesiones/14 d; W ≥ 2) | monitores | 21 d y cobertura para la razón retrasada |
| Monotonía/tensión | no | `OvertrainingDetector` es otro heurístico | opcional |
| Mapa de calor corporal | parcial | baterías por músculo; `MuscleArt.kt` (regiones frente/espalda para 10 músculos); `ProgramAnalyticsEngine.kt:371-378` (tercera unidad: sets × efc × RPE/8, sin reloj ni decaimiento) | vista + unificar unidad |
| Semáforo subjetivo | sí | DOMS, molestias, `RecoveryBands` (85/70/50/35) | — |
| Rampa | no | — | Δ CTL semanal |
| Determinismo | casi | `nowMs` explícito en monitores; `System.currentTimeMillis()` en `AugeFatigueEngine.kt:910,940` | cuidar en el módulo nuevo |
| S10 (Energía de dos escalas) | no | Energía plana: 1 día 95,5 → 6 días seguidos 94,9 [S4] | §7.2-S10 |

### 7.2 Especificación implementable

**Contrato común.** Paquete `domain/auge/load` (sin `android.*`): `dailySeries(logs, zone: ZoneId, from: LocalDate, to: LocalDate): DoubleArray`; `ewma(series, tau)` con pesos normalizados (a[d] = (1−α)·a[d−1] + α·L[d]; w[d] = (1−α)·w[d−1] + α; valor = a/w; α = 1 − e^{−1/τ}); `rollingSum7`. Ningún reloj interno: `hoy` entra como parámetro. Pruebas doradas con los números de abajo (generados por `b3_verify*.py`).

**(a) PMC por canal.** Parámetros por defecto: τ_a = 7 d (músculo y Columna), 10 d (Energía); τ_c = 28 d (42 d opcional). Salidas: CTL[d], ATL[d], TSB_mañana[d] = CTL[d−1] − ATL[d−1], TSB% = TSB/CTL, ATL/CTL, rampa = (CTL[d] − CTL[d−7])/CTL[d−7]. Mínimos: 14 d y 4 sesiones para dibujar; etiqueta «calentando» hasta cobertura 1 − e^{−n/τ_c} ≥ 0,5 (≈ 20 d con τ_c = 28; n_eff = 13,7 con 14 d y 25,9 con 28 d [S2]). **Ejemplo** (Pectorales, lunes 1 200 u y jueves 1 000 u, 8 semanas; semanas 9–10 añaden miércoles 900 u; semana 11 al 50 %; lunes por la mañana con valores de ayer):

| Lunes de | CTL | ATL | TSB | TSB/CTL | ATL/CTL |
|---|---|---|---|---|---|
| régimen estable | 296,1 | 244,4 | 51,7 | 17,5 % | 0,825 |
| tras sem. 9 (+1 sesión) | 326,7 | 312,1 | 14,6 | 4,5 % | 0,955 |
| tras sem. 10 (+1 sesión) | 349,2 | 337,0 | 12,2 | 3,5 % | 0,965 |
| tras sem. 11 (descarga) | 301,6 | 201,2 | 100,4 | 33,3 % | 0,667 |
| tras sem. 12 (normal) | 300,3 | 228,5 | 71,8 | 23,9 % | 0,761 |

Datos nuevos: ninguno (derivado de los logs). Prueba: números dorados de la tabla, invariancia de escala, CTL ≥ 0 y mismo resultado con la misma semilla; sintéticos con la rejilla de §7.3. Esfuerzo: S (motor y pruebas) + M (gráfico). Riesgos: oscilación semanal del TSB; redundancia con los anillos (por eso TSB queda en «Avanzado» y la lectura principal es ATL/CTL); unidad circular; ciencia débil en fuerza. No alimenta a los anillos.

**(b) Razón «tu semana frente a tu semana habitual» (ACWR-EWMA desacoplado).** R(d) = S7(d) / H(d−7), con S7 = suma de los últimos 7 días y H = EWMA τ = 28 d **de la serie de sumas de 7 d**, evaluada 7 días antes (sin solape con la semana aguda; en régimen estable da 1,005 y no depende del día de la semana). Elegida porque en la comparación sintética con el mismo ruido iguala a la mejor variante (AUC mezclado 0,864 frente a 0,847 de la acoplada; detección al 5 % de falsas alarmas 68 % frente a 58 %) y evita la oscilación semanal de la EWMA/EWMA (rango 0,21) [S6,S2]. Estados descriptivos que conservan la sensibilidad actual (W = 4): **< 0,75 «menos de lo habitual», 0,75–1,29 «habitual», 1,29–1,62 «más», 1,62–2,0 «pico», > 2,0 «pico fuerte»** (0,8→0,75; 1,2→1,29; 1,4→1,62; 1,6→2,0), con la histéresis actual y persistencia de 2 días; esto además alinea zonas y avisos (decisión abierta del propio código). **Banda personal opcional:** umbral = exp(μ + 1,645σ) de ln R en las últimas 8 semanas; el umbral que da 5 % de falsas alarmas varía de 1,31 a 1,59 según adherencia y ruido, así que un umbral fijo sirve mal a todos. Mínimos: 21 d, 6 sesiones y H ≥ una sesión media/semana; si no, «calentando». **Reglas de presentación:** texto descriptivo («esta semana llevas 18 % más que tu semana habitual»), nunca «riesgo de lesión»; no recortar carga con esta señal sola.

| Escenario (4 sesiones/sem) | AUGE acoplada | Desacoplada | S7/EWMA₂₈{S7} |
|---|---|---|---|
| estable | 1,000 | 1,000 | 1,005 |
| pico +50 % (1 semana) | 1,333 | 1,500 | 1,506 |
| pico +50 % (2 semanas) | 1,200 | 1,286 | 1,386 |
| descarga 50 % | 0,571 | 0,500 | 0,502 |
| +25 % sostenido 3 semanas | 1,053 | 1,071 | 1,149 |
| vuelta tras 3 semanas sin entrenar | 1,333 | 1,500 | 0,531 |

Con los Pectorales del ejemplo (domingo de la semana 9): S7 = 3 100, H = 2 182, R = 1,42 (acoplada 1,278; desacoplada 1,409). Datos nuevos: ninguno. Prueba: la tabla anterior como pruebas doradas, más Monte Carlo de usuarios estables (tasa de días marcados) y de picos (AUC), ambos reproducibles con `b3_verify2.py`. Esfuerzo S. Riesgos: calibración (5–25 % de días fuera de rango en usuarios estables), acantilado de la caja de 7 d (se atenúa con histéresis), la señal dentro de un usuario no supera a la carga aguda sola.

**(c) Monotonía y tensión.** mon = media/DE muestral (n−1) de los 7 días con descansos a 0; tensión = ΣL × mon. Ejemplo con CNS: U/L 4 días (34, 26, 0, 31, 22, 0, 0): mon 1,04, tensión 117; PPL 6 días (34, 26, 31, 34, 22, 28, 0): mon 2,11, tensión 369; 6 días iguales: 2,27 [S7]. Veredicto: **adaptar y degradar**. El umbral 2,0 marcaría a todo programa de 6 días, que es el diseño buscado; ofrecer, si acaso, «variedad de la semana» por canal con mínimo de 5 días entrenados, sin alerta. Datos nuevos: ninguno (con RPE de sesión explícito sería sRPE×min). Prueba: patrones sintéticos de §3.5. Esfuerzo S.

**(d) Mapa de calor corporal.** Tres modos sobre la misma silueta frente/espalda (reutilizar regiones de `MuscleArt` y completar las que falten):
1. **Estado** (por defecto): color = batería del músculo (0–100) con las cinco bandas de `RecoveryBands` (≥ 85, ≥ 70, ≥ 50, ≥ 35, < 35) en rampa secuencial de luminosidad y siempre con el número; hoy `colorArgb` solo distingue tres colores.
2. **Carga relativa**: color = R del músculo (misma fórmula de (b) por músculo), divergente alrededor de 1,0 con los estados de arriba; gris si hay < 21 d de datos o H por debajo del mínimo.
3. **Sin trabajar**: gris creciente desde 10 días sin series efectivas (músculos olvidados).
Capa de marcas: DOMS ≥ 3 y dolor articular. Ejemplo: Pectorales con batería 62 y R = 1,42 → modo 1 «Moderada» (50–69), modo 2 «más de lo habitual» (1,29–1,62). Datos nuevos: ninguno. Prueba: fixtures de batería y razón por músculo + capturas de la vista en el teléfono (regla de 20 min del plan). Esfuerzo M. Riesgos: ratios extremos en músculos esporádicos (suavizar con un H mínimo), accesibilidad (no depender solo del color).

**(e) Panel «Carga» mínimo viable** (solo lectura, sin controles de parámetros — respeta «sin sliders»): (1) cabecera con la razón por Músculos, Energía y Columna y su estado; (2) 12 semanas de barras con la línea «habitual»; (3) CTL/ATL por canal, TSB colapsado en «Avanzado»; (4) mapa de calor; (5) insignia «calentando (n de 21 días)»; (6) pie descriptivo («no predice lesiones»). Bonus [INFERIDO]: «¿cuánto subiría mi semana si hago el plan?» usando `SessionDrainRecipe.forEachPlanned` (`SessionDrainRecipe.kt:115-141`) como carga prevista. Datos nuevos: ninguno. Prueba: instantáneas de estado (calentando / datos suficientes / picos) con relojes fijos. Esfuerzo L en total (a+b ≈ S-M, d ≈ M, UI ≈ M).

**S10 · Energía de dos escalas.** B_E = 100·exp(−(R_fast + R_slow)/75); R_fast es el actual; R_slow = g·100·máx(0, A − H·(1+δ))·τ_a / capacidad_CNS, con A = EWMA(τ_a = 10 d) y H = EWMA(τ_c = 42 d) de L_E, δ = 0,10 (banda muerta = oscilación semanal del hábito, A/H llega a 1,07 con 3 sesiones/semana). Ejemplo (ENTHUSIAST, capacidad 297,6; hábito Lu-Mi-Vi con CNS = 30; mañana siguiente a la racha) [S7]:

| Días seguidos | Actual | g = 0,5 | g = 0,75 | g = 1,0 |
|---|---|---|---|---|
| 1 | 95,5 | 95,5 | 95,5 | 95,5 |
| 3 | 94,9 | 92,7 | 91,7 | 90,6 |
| 6 | 94,9 | 88,3 | 85,3 | 82,3 |

Con 2 semanas de 4 sesiones en vez de 3 la penalización es nula (A/H = 1,04) y con 5 sesiones pequeña (A/H = 1,14; ≈ −2 puntos con g = 0,75). Sin banda muerta, 2 días seguidos ya restan 3–6 puntos por simple agrupamiento (g = 0,5–1,0, τ_a = 7–10 d). Datos nuevos: ninguno. Prueba: la tabla anterior, invariantes (monótona en la carga, nunca sube la batería) y backtest contra anclas de Energía (§7.3). Esfuerzo M. **Condición:** el plan (D4) pospone esto a la Fase 4; el modelo de dos compartimentos no es identificable por usuario, así que τ y δ son constantes del registro y solo g se calibra con el backtest.

### 7.3 Evaluación

- **Verdad terreno por canal:** anclas manuales («siento mi Energía en 60», por músculo en V2), DOMS 1–5 del día siguiente, `cnsRecovery`, residuo de e1RM frente a la mediana de las 3 sesiones anteriores, `fatigueLevel`/`clarityRating`, `ringStartSnapshot`, molestias nuevas. Lesiones: demasiado raras (con la regla heurística de 10 eventos por predictor, prevalencia de 2–5 % por sesión y 1–5 predictores hacen falta 200–2 500 sesiones [S4]) → solo con datos agregados y con consentimiento.
- **Diseño:** validación cruzada de serie temporal con origen móvil (el entrenamiento contiene solo el pasado) [L49], bootstrap por bloques de usuario para los IC; comparar contra: media móvil nula, batería de AUGE al inicio de la sesión, días desde la última sesión del músculo, S7 sola y cada variante. Métrica: mejora incremental (ΔRMSE, ΔAUC, ΔBrier), no el ajuste dentro de muestra (en mi simulación el FFM libre gana dentro y pierde fuera). Modelar la relación carga-resultado como no lineal (splines/polinomios fraccionarios) y no dicotomizar la razón [L41].
- **Potencia:** para detectar r = 0,2 hacen falta 194 pares; r = 0,3, 85; r = 0,4, 47 (Fisher z, α = 0,05, potencia 0,80). Con SD(TSB previo) = 8 % del CTL, un efecto de 0,10 % de e1RM por punto de TSB/CTL y ruido de e1RM de 2,5 % exige ≈ 83 sesiones; con 0,05 %, 313 [S4].
- **Usuarios sintéticos:** generador con verdad conocida (frecuencia, adherencia, CV de sesión, picos, descargas, vueltas, usuarios nuevos) que mida falsas alarmas, latencia, escalón máximo diario y recuperación de parámetros; invariantes (CTL ≥ 0, invariancia de escala k·L → misma razón, idempotencia, mismo hash con la misma semilla). Dos pruebas ya hechas: estimador de τ [S3] y comparación de razones [S6].
- **Puertas antes de cablear a los anillos:** no mejorar a la línea nula ⇒ no se cablea; S10 exige mejora en las anclas del día siguiente en ≥ 60 % de los usuarios con ≥ 100 observaciones.

## 8. Preguntas abiertas y decisiones para el usuario

1. **Lenguaje de riesgo.** `acwrZoneLabel` dice «Zona de Riesgo/Alto Riesgo». Renombrar a estados descriptivos (a favor: coherente con la evidencia; en contra: textos y pruebas fijados, la gente entiende «riesgo»).
2. **Factor muscular de ACWR (1,0→1,12).** Conservar, retirar o sustituir por la razón (b). A favor de retirarlo: la capacidad ya normaliza por carga crónica y el factor es una segunda normalización global; en contra: re-anclar el máster dorado.
3. **Acciones automáticas con UNLOAD** (volumen ×0,8, descarga en `ProgramAutoregulationEngine`). Mantener, exigir dos semanas de persistencia o pedir confirmación.
4. **S10 ahora o en Fase 4** (D4 recomienda posponer). Adelantarlo solo con g conservador (0,5) y δ = 0,10.
5. **Alcance del panel Pro.** Solo (b)+(d) o también PMC completo; dónde vive (detalle de AUGE) y que no haya controles de parámetros.
6. **Unidad de carga.** Mantener el drenaje de AUGE (circular) o añadir un RPE de sesión explícito en el cierre (hoy `fatigueLevel` puede ser inferido por voz).
7. **Backtest con tu historial real** (D6 del plan) y, más adelante, datos agregados con consentimiento para eventos raros.
8. **Día local frente a UTC** en monitores y avisos (cambia cuándo «se reinicia» un aviso).
9. **Monotonía:** ¿omitirla?
10. **Aviso WATCH:** con 16 % de días en usuarios estables, mostrarlo solo si persiste 2 días.

Preguntas de investigación abiertas: τ de fuerza (no existen estimaciones publicadas que pude recuperar); si la razón aporta algo sobre la carga aguda dentro de un usuario con datos reales; cobertura de `MuscleArt` para los 13 pilares.

## 9. Evidencia

Ver `B3-carga-evidencia.md` (fuentes con estado de lectura, líneas de código, salidas de las simulaciones, limitaciones de las herramientas).
