package com.example.kpkn.domain.biomechanics.metrics

/**
 * TODOS los umbrales del motor de técnica en vídeo (§4 a §8), con los valores de la especificación como defecto. VT10 solo
 * ajusta aquí, con el reparto de ajuste y de reserva del banco dorado. Los campos nuevos de las olas siguientes (VT3a, VT3b)
 * se añaden AL FINAL.
 *
 * Los comentarios dicen de dónde sale cada cifra: «§6 p5» es el paso 5 del pseudocódigo del segmentador.
 */
data class AnalysisTuning(
    // ---------------------------------------------------------------------------------------------------------
    // §4 · preparación de la traza
    // ---------------------------------------------------------------------------------------------------------
    /** fs por debajo de esto (Hz): LOW_FPS, sin fases ni velocidad y confianza BAJA (§4.1 y §6). */
    val lowFpsBelow: Double = 20.0,
    /** MULTI_PERSON si la 2.ª pose sale en más de esta fracción de los fotogramas con pose (§4.2). */
    val multiPersonFraction: Double = 0.10,
    /** Se corrige la inclinación del móvil si |roll| supera esto, en grados (§4.3). */
    val rollCorrectionDeg: Double = 2.0,
    /** Un punto es válido con visibilidad y presencia de al menos esto (§4.4). */
    val minVisibility: Float = 0.5f,
    val minPresence: Float = 0.5f,
    /** S provisional del reparador de cruces izquierda/derecha = mediana(|S_mid − A_mid|) / esto (§4.5). */
    val provisionalScaleRatio: Double = 0.80,

    // ---------------------------------------------------------------------------------------------------------
    // §4.6–§4.8 · atípicos y huecos de la señal de conteo
    // ---------------------------------------------------------------------------------------------------------
    /** Hampel: media ventana k = max([hampelMinHalfWindow], round([hampelWindowFraction]·fs)) (§4.6). */
    val hampelWindowFraction: Double = 0.10,
    val hampelMinHalfWindow: Int = 2,
    /** Hampel: umbral de 3·1,4826·MAD. */
    val hampelSigmas: Double = 3.0,
    /** Salto: |Δr| > 4,5 BH/s · Δt deja la muestra como falta (§4.6). */
    val jumpBodyHeightsPerS: Double = 4.5,
    /**
     * Huesos (§4.6, solo en SIDE): un segmento 2D fuera de [0,65; 1,25]·L̂ deja la articulación distal como falta. La serie de
     * conteo no lo aplica (Hampel y salto bastan para contar); lo usa la serie de ángulos de las métricas (VT3a).
     */
    val boneMinRatio: Double = 0.65,
    val boneMaxRatio: Double = 1.25,
    /** Huecos de hasta 0,25 s se rellenan y se marcan (§4.8); de 0,25 a 0,6 s también, solo para contar. */
    val fillShortGapS: Double = 0.25,
    val fillCountGapS: Double = 0.60,
    /** Fracción mínima de fotogramas con pose para dar por buena la serie con un lado en un perfil por lado (§6 p9). */
    val perSideMinValid: Double = 0.70,
    /** Segundos mínimos de señal observada para que haya serie. */
    val minObservedS: Double = 2.0,

    // ---------------------------------------------------------------------------------------------------------
    // §5 · disco como señal principal y caminar
    // ---------------------------------------------------------------------------------------------------------
    /** El disco es la señal si HOUGH + NCC cubren al menos esta fracción de los fotogramas con pose (§5). */
    val plateMinCoverage: Double = 0.85,
    /**
     * Desfase pose↔disco que se busca por correlación, en fotogramas (±) (§5). Hacen falta [plateLagMinPairs] pares de datos
     * y que el desfase reduzca la decorrelación (1 − ρ) del desfase 0 en al menos la fracción [plateLagMinGain]. Se mide la
     * reducción relativa y no la diferencia de ρ porque la mano y el disco siguen una señal lenta: tres fotogramas de
     * desfase cambian ρ en menos de 0,01 aunque dupliquen el error.
     */
    val plateMaxLagFrames: Int = 3,
    val plateLagMinPairs: Int = 30,
    val plateLagMinGain: Double = 0.30,
    /** Visibilidad mínima de la muñeca para usar la mano como punto de barra del peso muerto (§5: «HC (vis ≥ 0,6)»). */
    val deadliftHandVisibility: Float = 0.6f,
    /** Caminar: |dX tobillos| > 0,12 BH/s durante ≥ 0,3 s, dilatado ±0,3 s (§6 p5). */
    val walkSpeedBhPerS: Double = 0.12,
    val walkMinS: Double = 0.3,
    val walkDilateS: Double = 0.3,
    /** Paso bajo con el que se suaviza la X de los tobillos y las caderas antes de derivarla. */
    val walkLowPassHz: Double = 1.0,

    // ---------------------------------------------------------------------------------------------------------
    // §6 · segmentador
    // ---------------------------------------------------------------------------------------------------------
    /** Menos de estos segundos de señal válida: «too_short». */
    val minSeriesS: Double = 3.0,
    /** p1: σn = 1,4826·MAD(r − LP(r, 5 Hz)). */
    val noiseLowPassHz: Double = 5.0,
    /** p2: rd = LP(r, 2,5 Hz) hasta conocer T0. */
    val acfLowPassHz: Double = 2.5,
    /** p2: T0 = primera cresta de la ACF normalizada en [0,6; 10] s con ρ ≥ 0,3. */
    val periodMinS: Double = 0.6,
    val periodMaxS: Double = 10.0,
    val periodMinRho: Double = 0.3,
    /** p2: con T0, rd = LP(r, clamp(4/T0, 1,5, 4)). */
    val countLowPassCycles: Double = 4.0,
    val countLowPassMinHz: Double = 1.5,
    val countLowPassMaxHz: Double = 4.0,
    /** p2: dmin = clamp(0,45·T0, 0,35, 3,0) s, o 0,6 s sin T0; wlen = max(3·T0, 12 s). */
    val minDistanceFactor: Double = 0.45,
    val minDistanceMinS: Double = 0.35,
    val minDistanceMaxS: Double = 3.0,
    val minDistanceDefaultS: Double = 0.6,
    /**
     * No está en el §6: en la segunda pasada, dos picos a menos de dmin se conservan los dos si el más alto NO es de tamaño
     * completo (prominencia < [closePeakRelaxFraction]·Aref) y el otro tiene al menos [closePeakProminenceRatio] veces su
     * prominencia. Así los rebotes de las parciales, al doble de cadencia que las completas, no se pierden; los rizos del
     * ruido junto a una cima y los bordes de una meseta o de un hundimiento (picos de tamaño completo) siguen eliminándose.
     */
    val closePeakProminenceRatio: Double = 0.60,
    val closePeakRelaxFraction: Double = 0.80,
    val windowPeriods: Double = 3.0,
    val windowMinS: Double = 12.0,
    /**
     * No está en el §6: si la serie acaba en la mitad alta de su recorrido (mediana de los últimos [endLevelWindowS] s por
     * encima del [endHighFraction] del recorrido), se le pega una bajada virtual de [endDescentS] s para que el último pico,
     * cortado por el final, tenga prominencia y el clasificador lo juzgue (bloqueo de un peso muerto, barra dejada abajo).
     */
    val endLevelWindowS: Double = 0.5,
    val endHighFraction: Double = 0.5,
    val endDescentS: Double = 1.0,
    /** p3: span = P99 − P1; primera pasada con prominencia ≥ 0,25·span. */
    val spanLowPercentile: Double = 1.0,
    val spanHighPercentile: Double = 99.0,
    val firstPassProminence: Double = 0.25,
    /** p3: Aref = mediana de las 3 mayores prominencias; segunda pasada con prominencia ≥ 0,30·Aref. */
    val refPeakCount: Int = 3,
    val secondPassProminence: Double = 0.30,
    /** p4: el primer y el último valle se buscan a 1,5·T. */
    val edgeSearchPeriods: Double = 1.5,
    /** p4: «top» = ciclos con prominencia ≥ 0,7·Aref; de ahí salen Lrest y Afull. */
    val topProminence: Double = 0.70,
    /** p4: sin repeticiones si Afull < 6σn (y si Afull < minAmplitude del perfil). */
    val minAmplitudeSigmas: Double = 6.0,
    /** p5: FULL si a ≥ 0,70; PARTIAL si 0,30 ≤ a < 0,70 (FAR_END si e ≥ 0,85); menos, ruido. */
    val fullFraction: Double = 0.70,
    val partialFraction: Double = 0.30,
    val farEndFraction: Double = 0.85,
    /** p5: NONREP si caminar solapa más del 20 % del ciclo. */
    val walkOverlapFraction: Double = 0.20,
    /** p5: NONREP si, en un perfil vertical, ΔX del punto de barra > 1,5·ΔY. */
    val horizontalPathRatio: Double = 1.5,
    /** p5, FAILED con arranque C: sube entre el 30 % y el 70 %, > 1,5× la subida de una FULL, parada ≥ 0,3 s. */
    val failedConcentricFactor: Double = 1.5,
    val failedStallS: Double = 0.3,
    /** p5, último ciclo con arranque C: FULL si sube ≥ 0,70 y se sostiene ≥ 0,3 s en ≥ Lrest + 0,7·A. */
    val lastSustainedLevel: Double = 0.70,
    val lastSustainedS: Double = 0.3,
    /**
     * p5, arranque E: el ciclo llegó al extremo lejano y no vuelve. Con aDn < [failedReturnBelow] (el §6 dice 0,5; sube a 0,70
     * porque el intento del generador llega justo al 50 % y el ruido lo pone a cada lado), un intento ≥ [failedAttemptFraction]·A
     * parado ≥ [failedValleyStallS] es FAILED; si es el último ciclo y vuelve menos de [setdownReturnBelow], se ignora (SETDOWN).
     */
    val failedReturnBelow: Double = 0.70,
    val failedAttemptFraction: Double = 0.15,
    val failedValleyStallS: Double = 0.5,
    val setdownReturnBelow: Double = 0.5,
    /**
     * No está en el §6: una «parada» (de un intento fallido) es un tramo quieto; si la señal LP de 5 Hz varía más de esta
     * fracción de Afull entre el fin de un tramo y el inicio del siguiente, hay movimiento (un rebote) y no cuenta.
     */
    val stillBandFraction: Double = 0.20,
    /**
     * p6: la primera o la última FULL con más de 1,35× el recorrido de una completa (recoger la barra del suelo y dejarla) es
     * preparación o dejada, si hay al menos [edgeMinFull] FULL. No está en el §6.
     */
    val edgeOversizeFactor: Double = 1.35,
    val edgeMinFull: Int = 3,
    /** p6: un parcial se conserva si el reposo que lo separa es ≤ 1,5·Tmed (≤ [miniSetGapS] con REST_PAUSE). */
    val partialGapPeriods: Double = 1.5,
    val miniSetGapS: Double = 30.0,
    /** p8: mini-serie nueva donde el reposo dura ≥ max(8 s, 3× la mediana). */
    val miniSetRestMinS: Double = 8.0,
    val miniSetRestFactor: Double = 3.0,
    /**
     * Banda sobre el nivel bajo en la que se mide el reposo: max(10 % de Afull, 2,5σn), como mucho el 25 % de Afull; el nivel
     * bajo es el percentil [restReferencePercentile] de la señal entre dos picos.
     */
    val restBandFraction: Double = 0.10,
    val restBandSigmas: Double = 2.5,
    val restBandMaxFraction: Double = 0.25,
    val restReferencePercentile: Double = 10.0,
    /** Las muestras de reposo (de las que sale σ_v) son las de rd ≤ Lrest + 15 % de Afull. */
    val restSampleFraction: Double = 0.15,
    /** Periodo de referencia (s) para buscar el primer y el último valle y medir descansos cuando no hay T0 ni picos de los que sacarlo. */
    val referencePeriodS: Double = 4.0,
    /** p7: quieto = |v| ≤ max(0,10·|v|máx, 3σ_v); se da la vuelta si quieto arriba > 2× abajo y ≥ 2 s. */
    val speedThresholdFraction: Double = 0.10,
    val speedThresholdSigmas: Double = 3.0,
    val polarityStillRatio: Double = 2.0,
    val polarityMinStillS: Double = 2.0,
    /**
     * No está en el §6: si el primer y el último segundo de la serie caen en la mitad baja (la persona empieza y acaba en
     * reposo), la señal está bien aunque haya más quieto arriba (sentadilla con pausas largas abajo): no se da la vuelta.
     */
    val polarityEdgeS: Double = 1.0,
    /** p9: ÚNICO si la amplitud de un lado es < 0,3× la del otro. */
    val singleSideAmplitudeRatio: Double = 0.3,
    /**
     * p9: ALTERNO si corr(rL, rR) < −0,3 o ≥ 80 % de los ciclos alternan con fase ≥ 0,25 del periodo propio. La fase se mide
     * entre los CENTROS de los ciclos (la mitad del recorrido), no entre los máximos de rd, que en una cima plana y con
     * ruido caen donde sea.
     */
    val alternatingCorrelation: Double = -0.3,
    val alternatingPeaksFraction: Double = 0.80,
    val alternatingMinPhase: Double = 0.25,
    /** Un giro a ±esto de un hueco rellenado cuenta como `turnInGap`. */
    val turnGapWindowS: Double = 0.15,

    // ---------------------------------------------------------------------------------------------------------
    // §6 · confianza del conteo
    // ---------------------------------------------------------------------------------------------------------
    /** c_track = clamp((fracción válida − 0,70)/0,25, 0, 1). */
    val trackBase: Double = 0.70,
    val trackSpan: Double = 0.25,
    /** c_snr = clamp(log10(Afull/σn/5)/log10(6), 0, 1). */
    val snrFloor: Double = 5.0,
    val snrSpanRatio: Double = 6.0,
    /** c_amp = clamp(1 − (CV_a − 0,10)/0,30, 0, 1). */
    val ampCvBase: Double = 0.10,
    val ampCvSpan: Double = 0.30,
    /** c_rhy = clamp(1 − (CV_duración − 0,30)/0,50, 0, 1). */
    val rhythmCvBase: Double = 0.30,
    val rhythmCvSpan: Double = 0.50,
    /** Con menos FULL que esto el CV no es de fiar y c_amp y c_rhy valen [neutralScore]. */
    val minCvReps: Int = 3,
    val neutralScore: Double = 0.5,
    /** c_view: 1 si la vista cuenta; 0,7 si es OBLIQUE; 0,4 en otro caso. */
    val viewOblique: Double = 0.7,
    val viewOther: Double = 0.4,
    /** n_amb: franja alrededor de 0,30 y 0,70 (±0,08 → [0,22; 0,38] y [0,62; 0,78]) y ±15 % de prominencia del umbral. */
    val ambiguityMargin: Double = 0.08,
    val prominenceBorderline: Double = 0.15,
    /** Extremo bajo de los picos «por poco» (1 − 0,15). */
    val borderlineLow: Double = 0.85,
    /** score = √c_track · c_view · (0,45·c_snr + 0,30·c_amp + 0,25·c_rhy) · 0,8^n_amb. */
    val snrWeight: Double = 0.45,
    val ampWeight: Double = 0.30,
    val rhythmWeight: Double = 0.25,
    val ambiguityPenalty: Double = 0.8,
    /** ALTA con score ≥ 0,75 (y n_amb = 0…); MEDIA con score ≥ 0,45; BAJA forzada con fracción válida < 0,6. */
    val altaScore: Double = 0.75,
    val mediaScore: Double = 0.45,
    val minValidFraction: Double = 0.60,

    // ---------------------------------------------------------------------------------------------------------
    // §6 · fases, TUT y tempo
    // ---------------------------------------------------------------------------------------------------------
    /** r_m = LP(r, 5 Hz), o el disco a 8 Hz si fs ≥ 50. */
    val phaseLowPassHz: Double = 5.0,
    val plateLowPassHzFast: Double = 8.0,
    val plateFastMinFps: Double = 50.0,
    /** Una pausa de giro o de reposo de menos de 0,25 s vale 0. */
    val turnPauseMinS: Double = 0.25,
    /** Ventana en la que se buscan los niveles de reposo y de giro de un tramo de movimiento. */
    val pieceLevelWindowS: Double = 0.3,

    // ---------------------------------------------------------------------------------------------------------
    // §7 · métricas por repetición y por serie (VT3a). Campo añadido al final.
    // ---------------------------------------------------------------------------------------------------------
    /**
     * Los umbrales de las métricas de VT3a (ROM, profundidad, bloqueos, tronco, trayectoria y velocidad), agrupados. Un constructor
     * de la JVM y de ART admite 255 palabras de parámetros y un `Double` ocupa dos: con este campo AnalysisTuning está en el límite
     * (un campo más suelto da `ClassFormatError`). Los umbrales que añadan VT3b y VT10 deben ir en un grupo propio como este.
     */
    val metrics: MetricsTuning = MetricsTuning(),

    // ---------------------------------------------------------------------------------------------------------
    // §8 punto 6 y §7 «Velocidad» y «Perfil carga-velocidad» (VT3b). Campo añadido al final, en un grupo propio.
    // ---------------------------------------------------------------------------------------------------------
    /** Umbrales de la escala por disco, la velocidad en m/s, la pérdida de velocidad y el perfil carga-velocidad. */
    val vbt: VbtTuning = VbtTuning(),

    // ---------------------------------------------------------------------------------------------------------
    // §7 «Observaciones» (VT3b). Campo añadido al final, en un grupo propio.
    // ---------------------------------------------------------------------------------------------------------
    /** Umbrales de las reglas de observación. */
    val observations: ObservationsTuning = ObservationsTuning(),
) {
    companion object {
        val DEFAULT: AnalysisTuning = AnalysisTuning()
    }
}

/**
 * Umbrales de las métricas por repetición y por serie (§7), con los valores de la especificación como defecto: viven en
 * `AnalysisTuning.metrics`. VT10 los ajusta aquí, igual que el resto.
 */
data class MetricsTuning(
    /** Paso bajo de las series de las métricas (ángulos, tronco, trayectoria): 5 Hz, o 4 Hz con fs < [metricSlowFpsBelow] (§4.9). */
    val metricLowPassHz: Double = 5.0,
    val metricLowPassSlowHz: Double = 4.0,
    val metricSlowFpsBelow: Double = 25.0,
    /** Ventana (s) de la media móvil con la que se buscan los extremos de una repetición: el ruido no empuja el pico. */
    val extremumWindowS: Double = 0.30,
    /**
     * Margen (s) a cada lado de [salida, llegada] en el que se buscan los extremos (ROM, profundidad, tronco…). Cubre los
     * ±0,5 s con los que las fases de la sentadilla sin disco (señal de cadera, no lineal) sitúan la salida y la llegada.
     */
    val repWindowMarginS: Double = 0.50,
    /** Semiancho (s) de la ventana alrededor del giro de la que sale «el valor en el giro». */
    val turnWindowHalfS: Double = 0.15,
    /** Semiancho (s) de la ventana alrededor de un instante (inicio o fin de la CON…) de la que sale su valor. */
    val instantWindowHalfS: Double = 0.10,
    /** Fracción mínima de muestras válidas de una ventana para dar su valor. */
    val minWindowValidFraction: Double = 0.60,

    // ROM (§7)
    /** Con al menos estas FULL hay tendencia (media de las 2 últimas − media de las 2 primeras) y pendiente OLS. */
    val romTrendMinFull: Int = 4,

    // Profundidad y criterios (§7)
    /** Sentadilla: PARALLEL si |α| < 5°; BELOW si α ≥ +5° (cadera bajo la rodilla); ABOVE si α ≤ −5°. */
    val depthParallelDeg: Double = 5.0,
    /** Sentadilla de lado: si α (con la longitud del muslo) y atan2(ΔY, |ΔX|) difieren más que esto, la repetición queda sin clase. */
    val depthCrossCheckDeg: Double = 10.0,
    /** Dominada: mentón ≈ boca + [chinBelowMouthFactor]·(boca − nariz); sobre la barra si Y_mentón ≤ Y_barra − [chinClearanceTrunk]·L̂_tronco. */
    val chinBelowMouthFactor: Double = 1.5,
    val chinClearanceTrunk: Double = 0.02,
    /** Dominada: colgado del todo si θE ≥ 155°. */
    val fullHangElbowDeg: Double = 155.0,
    /** Flexiones: completa si θE,min ≤ 90° (entre 90° y 110° es «≈» y por encima, corta; lo usa VT3b). */
    val pushUpFullElbowDeg: Double = 90.0,
    val pushUpShortElbowDeg: Double = 110.0,
    /** Banca: «más corta» si su fondo queda más de un 10 % de ROM_v por encima de la mediana, con al menos [benchMinFull] FULL. */
    val benchShallowerFraction: Double = 0.10,
    val benchMinFull: Int = 3,
    /** Bloqueos (θ incluido al final de la CON): peso muerto θH ≥ 165° y θK ≥ 160°; hip thrust θH ≥ 165°; press militar θE ≥ 160° y muñeca sobre la nariz. */
    val lockoutDeadliftHipDeg: Double = 165.0,
    val lockoutDeadliftKneeDeg: Double = 160.0,
    val lockoutHipThrustHipDeg: Double = 165.0,
    val lockoutOhpElbowDeg: Double = 160.0,

    // Tronco (§7)
    /** Δτ temprano: del inicio de la CON a este porcentaje de su desplazamiento. */
    val earlyConFraction: Double = 0.30,

    // Trayectoria (§7)
    /** x_ref = mediana de X_MF con visibilidad ≥ 0,6 y pies quietos (DE robusta < 0,02 BH). */
    val pathFootVisibility: Float = 0.6f,
    val pathFeetStillBh: Double = 0.02,
    /** Banca: las trayectorias se remuestrean en este número de niveles de progreso vertical. */
    val pathLevels: Int = 21,
    /** Banca: una mediana de trayectoria necesita al menos estas repeticiones con el nivel medido. */
    val pathMinReps: Int = 3,

    // Velocidad (§7)
    /** Pérdida de velocidad: con al menos estas FULL. */
    val vlMinFull: Int = 3,
    /** Pico de velocidad: solo con fs ≥ 50 Hz. */
    val peakMinFps: Double = 50.0,
    /** Una concéntrica más corta que esto (s) no da MCV. */
    val minConcentricS: Double = 0.10,
)

/**
 * Umbrales de la escala por disco, la velocidad en m/s, la pérdida de velocidad y el perfil carga-velocidad (§8 punto 6 y §7):
 * viven en `AnalysisTuning.vbt`. VT10 los ajusta aquí, igual que el resto.
 */
data class VbtTuning(
    // ---- Escala por disco (§8 p6) ----
    /** Los radios de calibración son los de las muestras HOUGH con q ≥ esto. */
    val calibrationMinQuality: Double = 0.5,
    /** Fotograma lento = velocidad del disco < esto × su radio por segundo (el desenfoque infla los bordes verticales). */
    val calibrationSlowRadiiPerS: Double = 0.4,
    /** Muestras lentas mínimas para fiarse del radio de calibración. */
    val calibrationMinSamples: Int = 8,
    /** a/b por debajo de esto: PLATE_ELLIPSE (cámara no perpendicular a la barra); por debajo del segundo: PLATE_REJECTED y sin escala. */
    val ellipseWarnBelow: Double = 0.85,
    val ellipseRejectBelow: Double = 0.70,
    /** Campo de visión horizontal que se supone cuando la traza no trae la focal (galería). */
    val defaultHfovDeg: Double = 70.0,
    /** El plano del disco está esto más cerca de la cámara que el plano sagital (m). */
    val plateToSagittalM: Double = 0.70,
    /** Sin focal conocida, un error de paralaje del desvío absoluto mayor que esto (cm) lo oculta (PARALLAX_SUPPRESSED). */
    val parallaxMaxErrorCm: Double = 2.0,
    /** Con focal conocida el paralaje se corrige; queda esta parte del error por la incertidumbre de los 0,70 m. */
    val parallaxResidualFraction: Double = 0.15,

    // ---- Velocidad con disco (§8 p5 y §4.9) ----
    /** Hueco máximo (s) que se interpola en la trayectoria del disco para la cinemática; con más, esa repetición no tiene velocidad. */
    val plateMaxGapS: Double = 0.1,
    /** Paso bajo de la posición del disco a menos de `plateFastMinFps`; a más, `plateLowPassHzFast` (8 Hz). */
    val plateLowPassSlowHz: Double = 6.0,

    // ---- Pérdida de velocidad (§7) ----
    /** La velocidad de cada repetición se mide entre el 10 % y el 90 % del desplazamiento de su concéntrica. */
    val vlWindowLow: Double = 0.10,
    val vlWindowHigh: Double = 0.90,
    /**
     * Error relativo esperado de la duración de una concéntrica por el ruido de la señal: κ·(σ/ΔL)/√(fs·T), con σ el ruido de la
     * señal en la propia concéntrica (lo que el ajuste de jerk mínimo deja sin explicar, ver [vlNoiseMarginS]), ΔL el recorrido, fs los
     * fotogramas por segundo y T la duración. La cota de Cramér–Rao de un tramo de jerk mínimo da κ = 5,55; el ajuste real
     * (Levenberg–Marquardt, 300 simulaciones) se queda un 20 % por encima: κ = 6,6. Por encima de [vlMaxDurationError] (0,025 = ±2,5 %,
     * con lo que una serie sin pérdida real marca como mucho en torno a ±5 %) la pérdida de velocidad no se da.
     */
    val vlDurationErrorFactor: Double = 6.6,
    val vlMaxDurationError: Double = 0.025,
    /**
     * Con un error esperado de la duración por encima de [vlMaxDurationError] la pérdida no se da, pero cada velocidad relativa sigue
     * valiendo para ver cuánto más lenta es una repetición; por encima de este (0,10 = ±10 %) tampoco: no hay velocidad relativa.
     */
    val vlMaxBarDurationError: Double = 0.10,
    /** Las [vlFirstReps] primeras repeticiones no deben diferir más de este CV: si no, alguna se midió mal (un hueco, una oclusión). */
    val vlFirstReps: Int = 3,
    val vlMaxFirstCv: Double = 0.25,
    /** El ruido se mide en la concéntrica y en estos segundos a cada lado (los reposos que la rodean fijan los dos niveles). */
    val vlNoiseMarginS: Double = 0.20,

    // ---- Perfil carga-velocidad (§7) ----
    /** Solo valen los puntos de los últimos 14 días respecto del más reciente. */
    val lvMaxAgeDays: Int = 14,
    /** Dos cargas a menos de esto (kg) cuentan como la misma. */
    val lvDistinctLoadKg: Double = 0.5,
    val lvMinLoads: Int = 3,
    /** (carga mayor − carga menor) / carga mayor ≥ 25 %. */
    val lvMinLoadRangeFraction: Double = 0.25,
    /** MCV máxima − MCV mínima ≥ 0,30 m/s. */
    val lvMinVelocityRangeMps: Double = 0.30,
    /** MCV del punto más pesado ≤ 0,60 m/s (banca: 0,50). */
    val lvMaxHeaviestMps: Double = 0.60,
    val lvMaxHeaviestBenchMps: Double = 0.50,
    /** R² ≥ 0,95 con 3 puntos; ≥ 0,90 con 4 o más. */
    val lvMinR2ThreePoints: Double = 0.95,
    val lvMinR2FourPlusPoints: Double = 0.90,
    /** El 1RM se oculta si supera 1,6× la carga mayor o si el intervalo de predicción del 95 % pasa de ±10 %. */
    val lvMaxOverMaxLoad: Double = 1.6,
    val lvMaxIntervalFraction: Double = 0.10,
    /** El 1RM se redondea a este paso (kg). */
    val lvRoundingKg: Double = 2.5,
)

/**
 * Umbrales de las reglas de observación (§7, «Observaciones»): viven en `AnalysisTuning.observations`. Cada regla comprueba
 * sus requisitos (vista, visibilidad, disco, fps) y calla si le falta alguno. Los que ya están en `MetricsTuning` (bloqueos,
 * flexiones cortas…) se leen de allí.
 */
data class ObservationsTuning(
    /** Como mucho este número de observaciones por serie. */
    val maxObservations: Int = 3,
    /** Peso de la confianza del conteo en el orden de las observaciones (BAJA: sin observaciones). */
    val confidenceAlta: Double = 1.0,
    val confidenceMedia: Double = 0.75,
    /** Las reglas que solo describen (pausa arriba, estilo del peso muerto) pesan menos que las que señalan una desviación. */
    val descriptiveWeight: Double = 0.4,
    /** Visibilidad mínima de cadera y rodilla (o del pie) para las reglas que miden en el fondo. */
    val minLandmarkVisibility: Float = 0.7f,
    /** Margen (s) alrededor de una repetición en el que se buscan sus extremos. */
    val repMarginS: Double = 0.5,

    // ---- Comunes ----
    /** Tendencia del ROM: |Δ| ≥ 6° y |Δ|/media ≥ 10 %. */
    val romTrendMinDeg: Double = 6.0,
    val romTrendMinFraction: Double = 0.10,
    /** Variación del ROM entre repeticiones (cadera en rumano e hip thrust): CV > 10 %. */
    val romCvPct: Double = 10.0,
    /** Tronco: τ en el giro de la última FULL frente a la primera, con al menos [trunkChangeMinFull] FULL. */
    val trunkChangeDeg: Double = 8.0,
    val trunkChangeMinFull: Int = 3,

    // ---- Sentadilla ----
    /** Profundidad irregular: DE(α) > 5° (con al menos 3 FULL) o una repetición ≥ 8° más alta que la mediana. */
    val squatDepthSdDeg: Double = 5.0,
    val squatShallowFromMedianDeg: Double = 8.0,
    /** Cadera adelantada: Δτ temprano ≥ 8° y subida de cadera / subida de hombro ≥ 1,5 en el primer 30 % de la CON. */
    val squatHipsEarlyDeltaDeg: Double = 8.0,
    val squatHipsRiseRatio: Double = 1.5,
    val earlyConTimeFraction: Double = 0.30,
    /** Deriva de la barra: |drift_con| ≥ 5 cm (calibrado) o ≥ 10 % de ROM_v (≈). */
    val squatDriftCm: Double = 5.0,
    val squatDriftPctRom: Double = 10.0,
    /** Talón que se levanta: ≥ 6 % del largo del pie con la punta quieta (la punta no se mueve más del 3 % del largo del pie). */
    val heelLiftFootFraction: Double = 0.06,
    val toeStillFootFraction: Double = 0.03,

    // ---- Peso muerto ----
    /** Cadera antes que la barra: ΔY_cadera/ΔY_barra ≥ 2 en el primer 15 % del recorrido de la barra y Δτ ≥ 5°. */
    val deadliftHipsToBarRatio: Double = 2.0,
    val deadliftBarRomFraction: Double = 0.15,
    val deadliftHipsDeltaTauDeg: Double = 5.0,
    /** Inclinación hacia atrás arriba: τ ≤ −10° (aquí en positivo: grados hacia atrás). */
    val deadliftLeanBackDeg: Double = 10.0,
    /** Estilo: «parado» si estuvo ≥ 0,25 s en el suelo (la pausa mínima de las fases); «rebote» si < 0,1 s, a ≥ 50 fps y con disco. */
    val deadliftBounceMaxS: Double = 0.1,
    val deadliftBounceMinFps: Double = 50.0,

    // ---- Rumano y buenos días ----
    /** La flexión de rodilla crece ≥ 25° en la EXC; la barra queda ≥ 8 cm por delante del medio pie en el fondo. */
    val hingeKneeGrowthDeg: Double = 25.0,
    val hingeBarAheadCm: Double = 8.0,

    // ---- Banca ----
    /** Repetición atípica: PD ≥ max(2× la mediana, 3 cm o 6 % de ROM_v). Punto de contacto: DE ≥ 3 cm o 6 % de ROM_v. */
    val benchAtypicalFactor: Double = 2.0,
    val benchDeviationMinCm: Double = 3.0,
    val benchDeviationMinPctRom: Double = 6.0,
    /** En banca con pausa, la pausa en el pecho debe durar ≥ 0,5 s. */
    val benchPauseMinS: Double = 0.5,

    // ---- Flexiones ----
    /** Línea del cuerpo θ(S,H,A) < 165°. */
    val pushUpBodyLineDeg: Double = 165.0,

    // ---- Press militar ----
    /** Inclinación hacia atrás en el pico de velocidad: τ ≤ −12° (aquí en positivo). En press estricto, flexión de rodilla ≥ 15° antes de la CON. */
    val ohpLeanBackDeg: Double = 12.0,
    val ohpKneeDriveDeg: Double = 15.0,

    // ---- Dominada y jalón ----
    /** Balanceo: X de cadera ≥ 0,10 BH o flexión de rodilla ≥ 30°. Jalón: Δτ hacia atrás ≥ 15°. */
    val pullSwingHipBh: Double = 0.10,
    val pullSwingKneeDeg: Double = 30.0,
    val pulldownLeanBackDeg: Double = 15.0,
    /** Para distinguir la dominada del jalón: en la dominada los hombros suben al menos esto (BH) en la concéntrica. */
    val pullUpShoulderRiseBh: Double = 0.10,

    // ---- Remo ----
    /** El torso sube ≥ 10° en la CON; remo en polea: balanceo ≥ 15°. */
    val rowTorsoRiseDeg: Double = 10.0,
    val rowCableSwingDeg: Double = 15.0,

    // ---- Curl y tríceps ----
    /** Balanceo del tronco ≥ 10°; deriva del codo θ(H,S,E): curl ≥ 25°, tríceps ≥ 20°; curl: θE máx. abajo < 150°. */
    val armTrunkSwingDeg: Double = 10.0,
    val curlElbowDriftDeg: Double = 25.0,
    val tricepsElbowDriftDeg: Double = 20.0,
    val curlMinExtensionDeg: Double = 150.0,

    // ---- Elevaciones ----
    /** Impulso: rango de τ ≥ 8° o flexión de rodilla ≥ 15°. */
    val raiseMomentumTrunkDeg: Double = 8.0,
    val raiseMomentumKneeDeg: Double = 15.0,
)
