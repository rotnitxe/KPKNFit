package com.example.kpkn.domain.relator

import com.example.kpkn.data.models.AutoregulationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que el relator dice en el descanso y al registrar la serie (LR1h), en una sesión de un básico: serie 1 hecha,
 * serie 2 de 4 por venir, con 2:00 de descanso. Una prueba por momento y una prueba de frases sobre todas las plantillas.
 */
class RelatorRestSpeechTest {

    private val nombre = "Sentadilla trasera con barra alta"

    /** Serie 1 registrada con lo previsto (95 kg × 6 a RIR 2); la serie 2 es la que toca. */
    private fun registrada(
        reps: Int? = 6,
        rir: Int? = 2,
        plannedReps: Int? = 6,
        plannedRepsMax: Int? = 6,
        plannedRir: Int? = 2,
        nextSetNumber: Int? = 2,
    ) = RelatorLoggedSetSlice(
        setNumber = 1,
        setTotal = 4,
        weightKg = 95.0,
        reps = reps,
        rir = rir,
        plannedWeightKg = 95.0,
        plannedReps = plannedReps,
        plannedRepsMax = plannedRepsMax,
        plannedRir = plannedRir,
        nextSetNumber = nextSetNumber,
        nextSetTotal = 4,
        nextSetWeightKg = 95.0,
        nextSetReps = 6,
    )

    /** Contexto del básico con el cursor en la serie 2 (durante el descanso). */
    private fun contexto(
        moment: RelatorMoment?,
        logged: RelatorLoggedSetSlice? = registrada(),
        history: List<RelatorHistorySet> = listOf(
            RelatorHistorySet(90.0, 6),
            RelatorHistorySet(92.5, 6),
            RelatorHistorySet(92.5, 5),
            RelatorHistorySet(92.5, 4),
        ),
        remaining: Int = 120,
        resting: Boolean = true,
    ): RelatorContext = RelatorContext(
        sessionId = "s1",
        setKey = "ex|1|",
        moment = moment,
        phase = if (resting) RelatorSessionPhase.REST else RelatorSessionPhase.WORKING,
        exerciseId = "ex",
        exerciseName = nombre,
        setIndex = 1,
        setCount = 4,
        targetReps = 6,
        targetRir = 2,
        prescribedWeightKg = 95.0,
        suggestedWeightKg = 95.0,
        plan = RelatorPlanSlice(autoregulationMode = AutoregulationMode.OFF),
        history = RelatorHistorySlice(lastSessionSets = history, trendThreeSessionsKg = history.map { it.weightKg }),
        progress = RelatorProgressSlice(
            completedWorkingSets = 1,
            totalWorkingSets = 4,
            nextSetNumber = 2,
            nextSetTotal = 4,
            nextSetWeightKg = 95.0,
            nextSetReps = 6,
        ),
        rest = RelatorRestSlice(
            active = resting,
            remainingSeconds = if (resting) remaining else 0,
            plannedSeconds = 195,
            adaptiveSeconds = 0,
        ),
        loggedSet = logged ?: RelatorLoggedSetSlice(),
    )

    private fun texto(context: RelatorContext): String? = RelatorEngine.resolve(context).line.text

    // Serie registrada: cómo fue frente a lo previsto.

    @Test
    fun registrada_en_lo_previsto_dice_la_serie_y_que_va_como_toca() {
        assertEquals("95\u00A0kg × 6 a RIR 2: lo previsto.", texto(contexto(RelatorMoment.SET_LOGGED)))
    }

    @Test
    fun registrada_dentro_del_rango_de_reps_sigue_siendo_lo_previsto() {
        val logged = registrada(reps = 7, plannedReps = 6, plannedRepsMax = 8)
        assertEquals("95\u00A0kg × 7 a RIR 2: lo previsto.", texto(contexto(RelatorMoment.SET_LOGGED, logged)))
    }

    @Test
    fun registrada_por_encima_de_las_reps_dice_que_sobro_margen() {
        val logged = registrada(reps = 8)
        assertEquals(
            "Te sobró margen: la próxima puede subir 2,5\u00A0kg.",
            texto(contexto(RelatorMoment.SET_LOGGED, logged)),
        )
    }

    @Test
    fun registrada_con_mas_rir_que_el_previsto_dice_que_sobro_margen() {
        val logged = registrada(rir = 3)
        assertEquals(
            "Te sobró margen: la próxima puede subir 2,5\u00A0kg.",
            texto(contexto(RelatorMoment.SET_LOGGED, logged)),
        )
    }

    @Test
    fun registrada_por_encima_en_la_ultima_serie_habla_de_la_proxima_vez() {
        val logged = registrada(reps = 8, nextSetNumber = null)
        assertEquals(
            "Te sobró margen: la próxima vez puede subir 2,5\u00A0kg.",
            texto(contexto(RelatorMoment.SET_LOGGED, logged)),
        )
    }

    @Test
    fun registrada_por_debajo_dice_una_rep_menos_y_mantiene_la_carga() {
        val logged = registrada(reps = 5)
        assertEquals("Una rep menos de lo previsto: mantén la carga.", texto(contexto(RelatorMoment.SET_LOGGED, logged)))
    }

    @Test
    fun registrada_por_debajo_con_mas_de_una_rep_dice_cuantas() {
        val logged = registrada(reps = 4)
        assertEquals("2 reps menos de lo previsto: mantén la carga.", texto(contexto(RelatorMoment.SET_LOGGED, logged)))
    }

    @Test
    fun registrada_sin_reps_dice_que_toca_despues() {
        val logged = registrada(reps = null)
        assertEquals("Prepárate: serie 2 de 4, 95\u00A0kg × 6.", texto(contexto(RelatorMoment.SET_LOGGED, logged)))
    }

    @Test
    fun registrada_sin_reps_en_la_ultima_serie_dice_que_era_la_ultima() {
        val logged = registrada(reps = null, nextSetNumber = null)
        assertEquals("Esta era la última serie del ejercicio.", texto(contexto(RelatorMoment.SET_LOGGED, logged)))
    }

    @Test
    fun registrada_sin_reps_previstas_solo_dice_la_serie() {
        val logged = registrada(plannedReps = null, plannedRepsMax = null, plannedRir = null)
        assertEquals("Serie 1 hecha: 95\u00A0kg × 6 a RIR 2.", texto(contexto(RelatorMoment.SET_LOGGED, logged)))
    }

    @Test
    fun la_marca_personal_manda_sobre_la_frase_de_la_serie() {
        val ctx = contexto(RelatorMoment.SET_LOGGED).copy(
            milestone = RelatorMilestoneSlice(prJustNow = true, estimatedRmKg = 120.0),
        )
        val line = texto(ctx).orEmpty()
        assertTrue(line, line.contains(nombre))
        assertFalse(line, line.contains("lo previsto"))
    }

    // Mitad del descanso: cómo fue la última vez, o cuánto queda y qué sigue.

    @Test
    fun mitad_con_historial_dice_como_fue_la_ultima_vez() {
        assertEquals(
            "La última vez, en la serie 2: 92,5\u00A0kg × 6.",
            texto(contexto(RelatorMoment.REST_MID)),
        )
    }

    @Test
    fun mitad_sin_historial_dice_cuanto_queda_y_que_sigue() {
        assertEquals(
            "Quedan 2:00. Después, serie 2 de 4: 95\u00A0kg × 6.",
            texto(contexto(RelatorMoment.REST_MID, history = emptyList())),
        )
    }

    @Test
    fun mitad_en_la_ultima_serie_sin_historial_dice_cuanto_queda_y_que_no_sigue_serie() {
        val ctx = contexto(RelatorMoment.REST_MID, history = emptyList()).copy(
            progress = RelatorProgressSlice(completedWorkingSets = 4, totalWorkingSets = 4),
        )
        assertEquals("Quedan 2:00. Después cierras la sesión.", texto(ctx))
    }

    // Últimos 15 s: como antes, con lo que toca después (tres frases; la elegida depende de la semilla).

    @Test
    fun ultimos_15_s_dicen_que_toca_despues() {
        val permitidas = setOf(
            "Prepárate: serie 2 de 4, 95\u00A0kg × 6.",
            "Después del descanso, serie 2 de 4, 95\u00A0kg × 6.",
            "El plan marca 3:15 de descanso en $nombre.",
        )
        val line = texto(contexto(RelatorMoment.REST_PREP, remaining = 12))
        assertTrue("«$line» no es una de las de los últimos 15 s", line in permitidas)
    }

    // Serie lista: la serie y la carga, sin frase de técnica pegada al nombre.

    @Test
    fun serie_lista_dice_la_serie_y_la_carga() {
        val ctx = contexto(RelatorMoment.SET_READY, resting = false, history = emptyList())
        assertEquals("Serie 2 de 4: 95\u00A0kg × 6 a RIR 2.", texto(ctx))
    }

    // Prueba de frases: todas las plantillas que puede decir el relator en la sesión de un básico.

    private val relleno = listOf(
        "va como está escrito en la receta",
        "No adelantes el plan de",
        "Si dudas, baja 2,5",
        "sigue en el programa",
        "El estímulo de",
        "Respeta series y descanso",
        "Sigue la receta de",
        "entra como va escrito",
        "Esta pieza de",
        "Cumple el esquema de",
        "hoy no improvisamos",
        "cuenta con esta serie",
        "marca esta serie de",
    )
    private val jerga = listOf("timer", "recobro", "recetario", "tal cual", "overlay", "tarjeta")
    private val rotulos = listOf("Ahora", "Ojo", "Dato", "Plan", "Siguiente", "Hoy", "Carga", "Recuerda")
    private val cifraPegadaS = Regex("\\d+s\\b")
    private val xSinEspacios = Regex("\\d×|×\\d|\\dx\\d")
    private val kgSinEspacioDuro = Regex("\\d kg")
    private val serieCargaRepsTiempo = Regex("(?i)serie|kg|reps?\\b|\\d:\\d{2}|\\d s\\b")

    /**
     * Las reglas de frase de una plantilla (LR1g y LR1h). Lista vacía si la frase está bien. La longitud (90 caracteres)
     * se mide en la frase que sale y en las plantillas propias del descanso; las de HistoryObserver (LR1g) no se miden.
     */
    private fun fallos(frase: String, conLongitud: Boolean): List<String> = buildList {
        if (relleno.any { frase.contains(it) }) add("relleno")
        if (jerga.any { frase.contains(it, ignoreCase = true) }) add("jerga")
        if (rotulos.any { frase.startsWith("$it:") }) add("rótulo")
        if (cifraPegadaS.containsMatchIn(frase)) add("cifra pegada a la s")
        if (xSinEspacios.containsMatchIn(frase) || kgSinEspacioDuro.containsMatchIn(frase)) add("serie o kilos mal escritos")
        if (frase.split(": ").size - 1 > 1) add("dos «: »")
        if (conLongitud && frase.length > RELATOR_LINE_MAX_CHARS) add("más de 90 caracteres")
        val segmentos = frase.split(Regex("(?<=[.!?])\\s+")).map { it.trim().trimEnd('.') }
        if (segmentos.any { it == nombre }) add("el nombre del ejercicio como frase suelta")
    }

    /** Los momentos de la sesión: serie registrada en lo previsto, por encima, por debajo, sin datos; mitad; últimos 15 s. */
    private fun contextosDeLaSesion(): List<RelatorContext> = listOf(
        contexto(RelatorMoment.SET_LOGGED),
        contexto(RelatorMoment.SET_LOGGED, registrada(reps = 8)),
        contexto(RelatorMoment.SET_LOGGED, registrada(rir = 3)),
        contexto(RelatorMoment.SET_LOGGED, registrada(reps = 5)),
        contexto(RelatorMoment.SET_LOGGED, registrada(reps = 4)),
        contexto(RelatorMoment.SET_LOGGED, registrada(reps = null)),
        contexto(RelatorMoment.SET_LOGGED, registrada(reps = null, nextSetNumber = null)),
        contexto(RelatorMoment.SET_LOGGED, registrada(plannedReps = null, plannedRepsMax = null, plannedRir = null)),
        contexto(RelatorMoment.REST_MID),
        contexto(RelatorMoment.REST_MID, history = emptyList()),
        contexto(RelatorMoment.REST_PREP, remaining = 12),
        contexto(RelatorMoment.SET_READY, resting = false, history = emptyList()),
    )

    @Test
    fun la_prueba_de_frases_de_una_sesion_de_un_basico() {
        val errores = mutableListOf<String>()
        for (context in contextosDeLaSesion()) {
            val momento = context.moment?.name ?: "SIN_MOMENTO"
            val restMoment = context.moment == RelatorMoment.SET_LOGGED ||
                context.moment == RelatorMoment.REST_MID ||
                context.moment == RelatorMoment.REST_PREP
            // Todas las plantillas de todos los observadores, no solo la que sale.
            RelatorEngine.defaultObservers().forEach { observer ->
                // Las plantillas del descanso y de la serie registrada son de este paquete: también miden la longitud.
                val delDescanso = observer is RestObserver || observer is SetOutcomeObserver
                observer.observe(context).forEach { candidate ->
                    candidate.lines.forEach { plantilla ->
                        val frase = plantilla.replace("{ex}", nombre)
                        fallos(frase, conLongitud = delDescanso).forEach { motivo -> errores += "$momento: $motivo en «$frase»" }
                        // En el descanso, cada frase de descanso y de la serie registrada dice serie, carga, reps o tiempo.
                        if (restMoment && delDescanso && !serieCargaRepsTiempo.containsMatchIn(frase)) {
                            errores += "$momento: no dice serie, carga, reps ni tiempo: «$frase»"
                        }
                    }
                }
            }
            // La frase que sale de verdad, con su variante elegida: aquí la longitud sí cuenta.
            texto(context)?.let { frase ->
                fallos(frase, conLongitud = true).forEach { motivo -> errores += "$momento (sale): $motivo en «$frase»" }
            }
        }
        assertTrue("Frases que fallan:\n" + errores.joinToString("\n"), errores.isEmpty())
    }
}
