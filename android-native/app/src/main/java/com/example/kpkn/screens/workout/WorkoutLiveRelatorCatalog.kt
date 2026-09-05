package com.example.kpkn.screens.workout

internal object WorkoutLiveRelatorCatalog {

    fun copyFor(bucket: RelatorSpeechBucket, snapshot: LiveRelatorSnapshot): String =
        variantsFor(bucket, snapshot).firstOrNull().orEmpty()

    fun variantsFor(bucket: RelatorSpeechBucket, snapshot: LiveRelatorSnapshot): List<String> = when (bucket) {
        RelatorSpeechBucket.HIDDEN -> listOf(fallback(snapshot))
        RelatorSpeechBucket.IDLE_MOBILITY -> idleMobilityVariants(snapshot)
        RelatorSpeechBucket.IDLE_WARMUP -> idleWarmupEarlyVariants(snapshot)
        RelatorSpeechBucket.IDLE_WARMUP_LAST -> idleWarmupLastVariants(snapshot)
        RelatorSpeechBucket.IDLE_REST -> idleRestVariants(snapshot)
        RelatorSpeechBucket.IDLE_FIRST_HIST,
        RelatorSpeechBucket.IDLE_FIRST_NEW,
        RelatorSpeechBucket.IDLE_MID,
        RelatorSpeechBucket.IDLE_LAST,
        RelatorSpeechBucket.IDLE_COMPOUND,
        -> situateWorkingVariants(snapshot)
        RelatorSpeechBucket.TISSUE_INTRA, RelatorSpeechBucket.TISSUE_DAY -> idleTissueVariants(snapshot)
        RelatorSpeechBucket.WARMUP_WEIGHT_BELOW -> warmupWeightVariants(snapshot, RelatorWeightBand.CONSERVATIVE)
        RelatorSpeechBucket.WARMUP_WEIGHT_ABOVE -> warmupWeightVariants(snapshot, RelatorWeightBand.AGGRESSIVE)
        RelatorSpeechBucket.WEIGHT_BELOW -> weightBelowVariants(snapshot)
        RelatorSpeechBucket.WEIGHT_ABOVE -> weightAboveVariants(snapshot)
        RelatorSpeechBucket.REPS_BELOW -> repsBelowVariants(snapshot)
        RelatorSpeechBucket.REPS_ABOVE -> repsAboveVariants(snapshot)
        RelatorSpeechBucket.TIME -> timeCopyVariants(snapshot)
        RelatorSpeechBucket.EFFORT_INEFFECTIVE -> effortIneffectiveVariants(snapshot)
        RelatorSpeechBucket.EFFORT_MEASURED -> effortMeasuredVariants(snapshot)
        RelatorSpeechBucket.EFFORT_HARD -> effortHardVariants(snapshot)
        RelatorSpeechBucket.EFFORT_FAILURE -> effortFailureVariants(snapshot)
        RelatorSpeechBucket.DROPSET_ONE -> dropsetOneVariants(snapshot)
        RelatorSpeechBucket.DROPSET_MANY -> dropsetManyVariants(snapshot)
        RelatorSpeechBucket.DROPSET_FOLLOWUP -> dropsetFollowUpVariants(snapshot)
        RelatorSpeechBucket.PR -> listOf(prCopy(snapshot, starred = false))
        RelatorSpeechBucket.PR_STAR -> listOf(prCopy(snapshot, starred = true))
        RelatorSpeechBucket.IDLE_DISCOMFORT -> discomfortVariants(snapshot)
        RelatorSpeechBucket.ASSIST_GAP_SET,
        RelatorSpeechBucket.ASSIST_GAP_UNI,
        RelatorSpeechBucket.ASSIST_GAP_SUPERSET,
        RelatorSpeechBucket.ASSIST_GAP_EXERCISE,
        RelatorSpeechBucket.ASSIST_TIME,
        RelatorSpeechBucket.ASSIST_MOBILITY,
        -> listOf(snapshot.assistOffer?.text ?: fallback(snapshot))
        RelatorSpeechBucket.ASSIST_CONFIRM -> assistConfirmVariants(snapshot)
        RelatorSpeechBucket.CAUTION_FAILED_SET -> cautionFailedSetVariants(snapshot)
        RelatorSpeechBucket.CONCEPT_CUE -> snapshot.conceptCueOrNull()?.lines.orEmpty()
            .ifEmpty { situateWorkingVariants(snapshot) }
    }

    fun idleFirstWithHistory(snapshot: LiveRelatorSnapshot): String = situateWorking(snapshot)

    fun idleFirstNoHistory(snapshot: LiveRelatorSnapshot): String = situateWorking(snapshot)

    fun idleMidWorking(snapshot: LiveRelatorSnapshot): String = situateWorking(snapshot)

    fun idleLastWorking(snapshot: LiveRelatorSnapshot): String = situateWorking(snapshot)

    fun idleCompound(snapshot: LiveRelatorSnapshot): String = situateWorking(snapshot)

    internal fun situateWorking(snapshot: LiveRelatorSnapshot): String =
        situateWorkingVariants(snapshot).first()

    internal fun situateShort(snapshot: LiveRelatorSnapshot): String {
        val i = snapshot.setIndex + 1
        val n = snapshot.setCount.coerceAtLeast(1)
        return "Serie $i de $n de {ex}."
    }

    internal fun situateWorkingVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val i = snapshot.setIndex + 1
        val n = snapshot.setCount.coerceAtLeast(1)
        val side = snapshot.activeSideLabel
        val heads = when {
            snapshot.isSuperset && side != null -> listOf(
                "Superserie, ronda $i de {ex}, lado $side",
                "Ronda $i de la superserie: {ex}, lado $side",
                "Te toca {ex} (superserie, ronda $i, lado $side)",
            )
            snapshot.isSuperset -> listOf(
                "Superserie, ronda $i: te toca {ex}",
                "Ronda $i de superserie. Ahora {ex}",
                "Sigue la superserie: ronda $i, {ex}",
            )
            side != null -> listOf(
                "Serie $i de $n de {ex}, lado $side",
                "Vas por la $i de $n de {ex}, lado $side",
                "{ex}, serie $i/$n, lado $side",
            )
            else -> listOf(
                "Serie $i de $n de {ex}",
                "Vas por la $i de $n de {ex}",
                "Siguiente: {ex}, serie $i/$n",
            )
        }
        val session = snapshot.sessionLastSet
        val history = snapshot.historyLastSet
        val tail = when {
            snapshot.setIndex > 0 && session != null ->
                ". En la ${session.setNumber} de hoy: ${formatRelatorSetMark(session)}."
            snapshot.setIndex <= 0 && history != null ->
                ". La última vez, primera serie: ${formatRelatorSetMark(history)}."
            snapshot.setIndex <= 0 && snapshot.lastLiftedWeight != null && snapshot.lastLiftedWeight > 0.0 ->
                ". La última vez: ${formatRelatorLoad(snapshot.lastLiftedWeight)} kg."
            snapshot.ultraFastApplied -> ". Vas en ultrarrápido; prioriza terminar limpio."
            else -> "."
        }
        return heads.map { it + tail }
    }

    private fun cautionFailedSetVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val caution = snapshot.failedSetCaution ?: return situateWorkingVariants(snapshot)
        return if (caution.sameExercise) {
            listOf(
                "La serie ${caution.sourceSetNumber} quedó marcada como fallida. Baja un poco la carga y cuida la articulación; no persigas el número de antes.",
                "Serie ${caution.sourceSetNumber} fallida. Entra más liviano y protege la articulación.",
            )
        } else {
            listOf(
                "El ejercicio anterior dejó una serie fallida. Entra suave a {ex} y no persigas el número.",
                "Vienes de un fallo. En {ex} empieza conservador.",
            )
        }
    }

    internal fun assistConfirmVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val ack = snapshot.assistAck ?: return situateWorkingVariants(snapshot)
        val target = ack.detail.trim().ifBlank { "{ex}" }
        val lines = if (!ack.applied) {
            when (ack.kind) {
                RelatorAssistActionKind.PREVIEW_ULTRAFAST -> listOf(
                    "El modo ultrarrápido ya está aplicado en esta sesión.",
                    "Ultrarrápido ya estaba puesto. No hay nada más que aplicar.",
                    "Ese modo ya corre en esta sesión.",
                )
                RelatorAssistActionKind.CONVERT_DROPSETS -> listOf(
                    "Eso ya está aplicado: las series que quedan ya van en dropset.",
                    "Las series que quedan ya van en dropset. Nada que cambiar.",
                    "Dropsets ya estaban. Seguimos.",
                )
                RelatorAssistActionKind.HALVE_SETS -> listOf(
                    "No había series suficientes para recortar a la mitad.",
                    "No alcanza para partir las series a la mitad.",
                    "Quedan pocas series; no pude recortar.",
                )
                RelatorAssistActionKind.ADD_MOBILITY -> listOf(
                    "No pude añadir esa movilidad ahora. Revisa el ejercicio y reintenta.",
                    "No pude sumar esa movilidad ahora.",
                    "Esa movilidad no entró. Revisa y reintenta.",
                )
                RelatorAssistActionKind.JUMP_TO_SIDE,
                RelatorAssistActionKind.JUMP_TO_SET,
                RelatorAssistActionKind.JUMP_TO_EXERCISE,
                -> listOf(
                    "No pude saltar ahí. El paso ya no está disponible.",
                    "Ese salto ya no está. El paso desapareció.",
                    "No hay a dónde saltar ahora.",
                )
                RelatorAssistActionKind.MOVE_EXERCISE_END -> listOf(
                    "No pude mandar $target al final ahora.",
                    "$target no se pudo mover al cierre.",
                    "Ese cambio de orden no aplicó. Seguimos aquí.",
                )
                RelatorAssistActionKind.OMIT_SET -> listOf(
                    "No pude omitir esa serie. Ya no está disponible.",
                    "Esa serie ya no se puede omitir.",
                    "Omitir no aplicó. Seguimos con lo que toca.",
                )
            }
        } else {
            when (ack.kind) {
                RelatorAssistActionKind.CONVERT_DROPSETS -> listOf(
                    "Ok, aplico dropsets a las series que quedan para que termines antes. Ojo a las articulaciones y recupera bien para la próxima.",
                    "Dropsets en lo que queda. Aceleramos; cuida las articulaciones.",
                    "Quedan en dropset. Terminas antes; no fuerces la articulación.",
                )
                RelatorAssistActionKind.PREVIEW_ULTRAFAST -> listOf(
                    "Ok, aplico modo ultrarrápido a lo que queda: menos series y menos descanso para terminar a tiempo. Ten ojo con las articulaciones.",
                    "Ultrarrápido activo: menos series y menos descanso. Cierra limpio.",
                    "Modo ultrarrápido puesto. Prioriza terminar; ojo a las articulaciones.",
                )
                RelatorAssistActionKind.HALVE_SETS -> listOf(
                    "Ok, recorto a la mitad las series que quedan. Terminas antes; no persigas volumen extra hoy.",
                    "Mitad de series en lo que queda. Cierra sin perseguir volumen.",
                    "Recorté las series a la mitad. Hoy no sumes extra.",
                )
                RelatorAssistActionKind.OMIT_SET -> listOf(
                    "Hecho: dejo esa serie omitida y seguimos.",
                    "Serie omitida. Seguimos con lo que toca.",
                    "La dejo de lado. Avanzamos.",
                )
                RelatorAssistActionKind.JUMP_TO_SET -> listOf(
                    "Vamos a esa serie ahora.",
                    "Saltamos a esa serie. Sigue desde aquí.",
                    "Esa serie, ahora. Retomamos.",
                )
                RelatorAssistActionKind.JUMP_TO_SIDE -> listOf(
                    "Cambio al otro lado.",
                    "Pasamos al otro lado. Sigue esa serie.",
                    "Otro lado. Mismo ejercicio, otra mitad.",
                )
                RelatorAssistActionKind.JUMP_TO_EXERCISE -> listOf(
                    "Volvemos a $target.",
                    "Regresamos a $target; retomamos desde aquí.",
                    "$target otra vez. Sigue esta serie.",
                )
                RelatorAssistActionKind.MOVE_EXERCISE_END -> listOf(
                    "Listo: $target queda al final. Seguimos con lo de ahora.",
                    "$target se va al cierre. Ahora toca lo que sigue.",
                    "Lo mandé al final. $target espera; seguimos aquí.",
                )
                RelatorAssistActionKind.ADD_MOBILITY -> listOf(
                    "Añado esa movilidad a {ex}. Un momento para la articulación y seguimos.",
                    "Movilidad añadida a {ex}. Un momento y seguimos.",
                    "Sumé esa movilidad. {ex} queda más protegido.",
                )
            }
        }
        return lines.map { it.replace("{target}", target) }
    }

    fun idleTissue(snapshot: LiveRelatorSnapshot): String = idleTissueVariants(snapshot).first()

    fun idleTissueVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val hint = snapshot.tissueHint ?: return listOf(fallback(snapshot))
        val source = shortSourceName(hint.sourceExerciseName)
        val muscle = hint.muscleLabel
        val joint = hint.jointCare
        return if (hint.window == RelatorTissueWindow.INTRA) {
            if (joint != null) {
                listOf(
                    "Los $muscle ya trabajaron en $source. Cuida los $joint.",
                    "$muscle vienen de $source. Ojo a los $joint.",
                    "Hoy $source ya cargó $muscle. En {ex} cuida los $joint.",
                )
            } else {
                listOf(
                    "Los $muscle ya trabajaron en $source en esta sesión.",
                    "$muscle ya se usaron en $source. No hace falta ir al límite.",
                    "Vienes de trabajar $muscle en $source.",
                )
            }
        } else {
            listOf(
                "Ayer $source trabajó $muscle. Hoy no hace falta ir tan pesado.",
                "$muscle llegan tocados de ayer ($source). Baja un punto la agresividad.",
                "Ayer ya hubo $muscle con $source. Hoy prioriza calidad.",
            )
        }
    }

    fun idleWarmupEarly(snapshot: LiveRelatorSnapshot): String = idleWarmupEarlyVariants(snapshot).first()

    fun idleWarmupEarlyVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val i = (snapshot.warmupIncompleteIndex ?: 0) + 1
        val n = snapshot.warmupCount.coerceAtLeast(1)
        return listOf(
            "Aproximación $i de $n de {ex}.",
            "Vas en la aproximación $i de $n de {ex}.",
            "{ex}: aproximación $i/$n.",
        )
    }

    fun idleWarmupLast(snapshot: LiveRelatorSnapshot): String = idleWarmupLastVariants(snapshot).first()

    fun idleWarmupLastVariants(snapshot: LiveRelatorSnapshot): List<String> = listOf(
        "Última aproximación de {ex}. Después vienen las efectivas.",
        "Cierra las aproximaciones de {ex}. Siguen las efectivas.",
        "Última aprox de {ex}. Ya casi las series de trabajo.",
    )

    fun idleMobility(snapshot: LiveRelatorSnapshot): String = idleMobilityVariants(snapshot).first()

    fun idleMobilityVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val zone = when (snapshot.family) {
            RelatorFamily.PRESS -> "hombro y manguito"
            RelatorFamily.PULL -> "escápula"
            RelatorFamily.SQUAT -> "cadera y tobillo"
            RelatorFamily.HINGE -> "cadera y lumbar"
            RelatorFamily.ISOLATION, RelatorFamily.OTHER -> null
        }
        val total = snapshot.mobilityTotal
        val done = snapshot.mobilityCompleted
        val head = if (zone != null) "Movilidad de $zone antes de {ex}" else "Movilidad antes de {ex}"
        val primary = if (total > 0) "$head: $done de $total." else "$head."
        val second = if (zone != null) {
            "Prepara $zone. Luego {ex}."
        } else {
            "Un momento de movilidad y seguimos con {ex}."
        }
        val third = if (total > 0) {
            "Movilidad $done/$total antes de {ex}."
        } else {
            "Movilidad breve; {ex} espera."
        }
        return listOf(primary, second, third)
    }

    fun idleRest(snapshot: LiveRelatorSnapshot): String = idleRestVariants(snapshot).first()

    fun idleRestVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val i = snapshot.setIndex + 1
        val n = snapshot.setCount.coerceAtLeast(1)
        val last = snapshot.sessionLastSet
        return if (last != null) {
            listOf(
                "Descanso. Serie ${last.setNumber}: ${formatRelatorSetMark(last)} de {ex}.",
                "Pausa. La ${last.setNumber} fue ${formatRelatorSetMark(last)} en {ex}.",
                "Respira. {ex} última marca: ${formatRelatorSetMark(last)}.",
            )
        } else {
            listOf(
                "Descanso. Vas en la serie $i de $n de {ex}.",
                "Pausa entre series de {ex} ($i de $n).",
                "Descansa; sigue {ex}, serie $i/$n.",
            )
        }
    }

    fun fallback(snapshot: LiveRelatorSnapshot): String = when (snapshot.phase) {
        RelatorPhase.MOBILITY -> idleMobility(snapshot)
        RelatorPhase.WARMUP -> idleWarmupEarly(snapshot)
        RelatorPhase.REST -> idleRest(snapshot)
        else -> situateWorking(snapshot)
    }

    fun warmupWeight(snapshot: LiveRelatorSnapshot, band: RelatorWeightBand): String =
        warmupWeightVariants(snapshot, band).first()

    fun warmupWeightVariants(snapshot: LiveRelatorSnapshot, band: RelatorWeightBand): List<String> {
        val entered = snapshot.enteredWeight?.let { formatRelatorLoad(it) }
            ?: return idleWarmupLastVariants(snapshot)
        val anchor = (snapshot.suggestedWeight ?: snapshot.referenceWeight)?.let { formatRelatorLoad(it) }
        return when {
            band == RelatorWeightBand.CONSERVATIVE && anchor != null -> listOf(
                "Aprox a $entered kg; el plan era $anchor kg. Más liviana, no debería fatigar.",
                "$entered kg de aprox, por debajo de $anchor. Bien para no gastar la serie.",
                "Aprox liviana: $entered kg (plan $anchor). Reserva para las efectivas.",
            )
            band == RelatorWeightBand.AGGRESSIVE && anchor != null -> listOf(
                "Aprox a $entered kg; el plan era $anchor kg. Ya parece serie efectiva.",
                "$entered kg en aprox supera los $anchor del plan. Cuidado: ya carga de verdad.",
                "Esa aprox ($entered kg) está alta frente a $anchor. No la trates como calentamiento.",
            )
            else -> listOf(
                "Aprox a $entered kg de {ex}.",
                "{ex}: aproximación a $entered kg.",
                "Vas a $entered kg en esta aprox.",
            )
        }
    }

    fun enumerateSnapshots(longExerciseName: String): List<LiveRelatorSnapshot> {
        val families = RelatorFamily.entries
        val genders = listOf(false, true)
        val phases = listOf(RelatorPhase.MOBILITY, RelatorPhase.WARMUP, RelatorPhase.WORKING, RelatorPhase.REST)
        val samples = mutableListOf<LiveRelatorSnapshot>()
        for (family in families) {
            for (feminine in genders) {
                for (phase in phases) {
                    val base = LiveRelatorSnapshot(
                        visible = true,
                        phase = phase,
                        family = family,
                        feminine = feminine,
                        exerciseDisplayName = longExerciseName,
                        setIndex = 0,
                        setCount = 3,
                        hasHistory = true,
                        warmupCount = 3,
                        warmupIncompleteIndex = 2,
                        warmupIsLastIncomplete = phase == RelatorPhase.WARMUP,
                        mobilityTotal = 3,
                        setKey = "enum_${family}_$feminine",
                        referenceWeight = 80.0,
                        suggestedWeight = 80.0,
                        lastLiftedWeight = 80.0,
                        plannedReps = 10.0,
                        plannedIntensity = 8.0,
                    )
                    samples += base
                    samples += base.copy(hasHistory = false, warmupIsLastIncomplete = false, warmupIncompleteIndex = 0)
                    samples += base.copy(setIndex = 1, warmupIsLastIncomplete = false)
                    samples += base.copy(setIndex = 2, warmupIsLastIncomplete = false)
                    samples += base.copy(compound = RelatorCompound.BENCH_BAR)
                    samples += base.copy(idleCycle = 3)
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.WEIGHT,
                        enteredWeight = 70.0,
                        enteredWeightRaw = "70",
                        lastLiftedWeight = 80.0,
                    )
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.WEIGHT,
                        enteredWeight = 82.5,
                        enteredWeightRaw = "82.5",
                        suggestedWeight = 80.0,
                    )
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.WEIGHT,
                        enteredWeight = 90.0,
                        enteredWeightRaw = "90",
                        suggestedWeight = 80.0,
                    )
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.REPS,
                        enteredReps = 6.0,
                    )
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.REPS,
                        enteredReps = 12.0,
                    )
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.REPS,
                        enteredReps = 15.0,
                    )
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.INTENSITY,
                        enteredIntensity = 5.0,
                    )
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.INTENSITY,
                        enteredIntensity = 6.5,
                    )
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.INTENSITY,
                        enteredIntensity = 8.0,
                    )
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.INTENSITY,
                        enteredIntensity = 9.5,
                    )
                    samples += base.copy(reachedFailure = true)
                    samples += base.copy(dropSetCount = 1, plannedDropCount = 0)
                    samples += base.copy(dropSetCount = 3, plannedDropCount = 0)
                    samples += base.copy(isDropsetFollowUp = true, setIndex = 1)
                    samples += base.copy(
                        tissueHint = RelatorTissueHint(
                            muscleLabel = "tríceps",
                            sourceExerciseName = "Press banca",
                            jointCare = "codos",
                            window = RelatorTissueWindow.INTRA,
                            drainScore = 2.0,
                        ),
                    )
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.WEIGHT,
                        enteredWeight = 110.0,
                        enteredWeightRaw = "110",
                        enteredReps = 3.0,
                        prHint = RelatorPrHint(estimatedRmKg = 120.0, isStar = false),
                    )
                    samples += base.copy(
                        lastChangedField = RelatorChangedField.REPS,
                        enteredWeight = 100.0,
                        enteredWeightRaw = "100",
                        enteredReps = 5.0,
                        prHint = RelatorPrHint(
                            estimatedRmKg = 116.0,
                            isStar = true,
                            goal1RmKg = 140.0,
                            goalPct = 83,
                        ),
                    )
                    samples += base.copy(
                        idleCycle = 1,
                        discomfortHint = RelatorDiscomfortHint(
                            label = "Hombro anterior",
                            fromThisSession = false,
                        ),
                    )
                    samples += base.copy(
                        sessionLastSet = RelatorSessionSetMemory(setNumber = 1, weightKg = 80.0, reps = 8),
                    )
                    samples += base.copy(
                        phase = RelatorPhase.WARMUP,
                        lastChangedField = RelatorChangedField.WARMUP_WEIGHT,
                        warmupIsLastIncomplete = true,
                        enteredWeight = 40.0,
                        enteredWeightRaw = "40",
                        referenceWeight = 50.0,
                        suggestedWeight = 50.0,
                    )
                    samples += base.copy(
                        phase = RelatorPhase.MOBILITY,
                        lastChangedField = RelatorChangedField.MOBILITY_CHECK,
                        mobilityCompleted = 1,
                    )
                    samples += base.copy(
                        phase = RelatorPhase.MOBILITY,
                        lastChangedField = RelatorChangedField.MOBILITY_TIMER,
                        mobilityTimerRunning = true,
                        mobilityRemainingSeconds = 90,
                    )
                    samples += base.copy(
                        loadKind = RelatorLoadKind.BODYWEIGHT,
                        lastChangedField = RelatorChangedField.WEIGHT,
                        enteredWeight = 0.0,
                        enteredWeightRaw = "0",
                    )
                    samples += base.copy(
                        unit = RelatorUnit.TIME,
                        lastChangedField = RelatorChangedField.REPS,
                        enteredReps = 30.0,
                        plannedReps = 20.0,
                    )
                }
            }
        }
        return samples
    }

    private fun weightBelow(snapshot: LiveRelatorSnapshot): String = weightBelowVariants(snapshot).first()

    private fun weightBelowVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val entered = snapshot.enteredWeight ?: return situateWorkingVariants(snapshot)
        val now = formatRelatorLoad(entered)
        val session = snapshot.sessionLastSet
        if (snapshot.setIndex > 0 && session != null) {
            val last = formatRelatorSetMark(session)
            val delta = formatRelatorDelta(entered - session.weightKg)
            val lastKg = formatRelatorLoad(session.weightKg)
            return listOf(
                "Ahora $now kg; en la ${session.setNumber} de hoy fue $last ($delta kg) en {ex}.",
                "Bajas a $now kg. Hace un rato esta serie iba a $lastKg.",
                "$now kg, más liviano que la marca de hoy. Si es a propósito, adelante.",
            )
        }
        val lastKg = snapshot.historyLastSet?.weightKg ?: snapshot.lastLiftedWeight
        val last = lastKg?.let { formatRelatorLoad(it) }
        val delta = lastKg?.let { formatRelatorDelta(entered - it) }
        return if (last != null && delta != null) {
            listOf(
                "Ahora $now kg; la última vez en este ejercicio fue $last kg ($delta kg).",
                "Bajas a $now kg. La última vez fueron $last kg.",
                "$now kg, más liviano que la última ($last kg). Si es a propósito, adelante.",
            )
        } else {
            listOf(
                "Ahora $now kg, por debajo de la última en {ex}.",
                "Bajas a $now kg en {ex}.",
                "$now kg, más liviano que lo habitual en {ex}.",
            )
        }
    }

    private fun weightAbove(snapshot: LiveRelatorSnapshot): String = weightAboveVariants(snapshot).first()

    private fun weightAboveVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val entered = snapshot.enteredWeight ?: return situateWorkingVariants(snapshot)
        val now = formatRelatorLoad(entered)
        val anchor = snapshot.suggestedWeight ?: snapshot.referenceWeight
        val plan = anchor?.let { formatRelatorLoad(it) }
        val delta = anchor?.let { formatRelatorDelta(entered - it) }
        return if (plan != null && delta != null) {
            listOf(
                "Ahora $now kg; lo sugerido era $plan kg ($delta kg) en {ex}.",
                "Subes a $now kg. El ancla era $plan kg ($delta kg).",
                "$now kg, por encima de lo sugerido ($plan). Si la técnica aguanta, ok.",
            )
        } else {
            listOf(
                "Ahora $now kg, por encima de lo sugerido en {ex}.",
                "Subes a $now kg en {ex}.",
                "$now kg, más pesado que lo previsto en {ex}.",
            )
        }
    }

    private fun repsBelow(snapshot: LiveRelatorSnapshot): String = repsBelowVariants(snapshot).first()

    private fun repsBelowVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val live = snapshot.enteredReps?.let { formatRelatorLoad(it) } ?: return situateWorkingVariants(snapshot)
        val plan = snapshot.plannedReps?.let { formatRelatorLoad(it) }
        return if (plan != null) {
            listOf(
                "Anotaste $live reps; el plan era $plan (por debajo) en {ex}.",
                "$live reps, menos que las $plan del plan en {ex}.",
                "Bajas a $live reps (plan $plan). Si la fatiga manda, está bien.",
            )
        } else {
            listOf(
                "Anotaste $live reps en {ex}, por debajo del plan.",
                "$live reps en {ex}, menos de lo previsto.",
                "Reps a $live en {ex}: por debajo del objetivo.",
            )
        }
    }

    private fun repsAbove(snapshot: LiveRelatorSnapshot): String = repsAboveVariants(snapshot).first()

    private fun repsAboveVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val live = snapshot.enteredReps?.let { formatRelatorLoad(it) } ?: return situateWorkingVariants(snapshot)
        val plan = snapshot.plannedReps?.let { formatRelatorLoad(it) }
        return if (plan != null) {
            listOf(
                "Anotaste $live reps; el plan era $plan (por encima) en {ex}.",
                "$live reps, más que las $plan del plan en {ex}.",
                "Subes a $live reps (plan $plan). Si siguen limpias, bien.",
            )
        } else {
            listOf(
                "Anotaste $live reps en {ex}, por encima del plan.",
                "$live reps en {ex}, más de lo previsto.",
                "Reps a $live en {ex}: por encima del objetivo.",
            )
        }
    }

    private fun timeCopy(snapshot: LiveRelatorSnapshot): String = timeCopyVariants(snapshot).first()

    private fun timeCopyVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val live = snapshot.enteredReps?.let { formatRelatorLoad(it) } ?: return situateWorkingVariants(snapshot)
        val plan = snapshot.plannedReps?.let { formatRelatorLoad(it) }
        return if (plan != null) {
            listOf(
                "Tiempo ${live}s; el plan era ${plan}s en {ex}.",
                "${live}s frente a ${plan}s de plan en {ex}.",
                "Cronómetro a ${live}s (plan ${plan}s) en {ex}.",
            )
        } else {
            listOf(
                "Tiempo anotado: ${live}s en {ex}.",
                "${live}s en {ex}.",
                "Marcas ${live}s de trabajo en {ex}.",
            )
        }
    }

    private fun effortIneffective(snapshot: LiveRelatorSnapshot): String =
        effortIneffectiveVariants(snapshot).first()

    private fun effortIneffectiveVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val range = if (snapshot.intensityMode == com.example.kpkn.data.models.IntensityMode.RIR) {
            "RIR 0–3"
        } else {
            "RPE 7–9"
        }
        return effortWithNumbersVariants(
            snapshot,
            extras = listOf(
                "Estímulo bajo para hipertrofia ($range).",
                "Queda holgado para $range; si buscas estímulo, aprieta un poco.",
                "Zona suave. $range suele ser el trabajo que cuenta.",
            ),
        )
    }

    private fun effortMeasured(snapshot: LiveRelatorSnapshot): String =
        effortMeasuredVariants(snapshot).first()

    private fun effortMeasuredVariants(snapshot: LiveRelatorSnapshot): List<String> =
        effortWithNumbersVariants(
            snapshot,
            extras = listOf(
                "Más cómodo que el plan: menos fatiga y menos estímulo.",
                "Entró más fácil que lo programado. Menos costo, menos empujón.",
                "Por debajo del esfuerzo del plan. Válido si hoy toca conservar.",
            ),
        )

    private fun effortHard(snapshot: LiveRelatorSnapshot): String = effortHardVariants(snapshot).first()

    private fun effortHardVariants(snapshot: LiveRelatorSnapshot): List<String> =
        effortWithNumbersVariants(
            snapshot,
            extras = listOf(
                "Más esfuerzo que el plan: más reclutamiento y más costo.",
                "Aprietas más de lo programado. Cuenta el extra de fatiga.",
                "Zona dura. Más estímulo, más precio para lo que queda.",
            ),
        )

    private fun effortFailure(snapshot: LiveRelatorSnapshot): String = effortFailureVariants(snapshot).first()

    private fun effortFailureVariants(snapshot: LiveRelatorSnapshot): List<String> = listOf(
        "Marcaste fallo en {ex}. Máximo reclutamiento; cobra fatiga y articulación.",
        "Fallo en {ex}. La serie ya no da más; no lo encadenes a lo loco.",
        "{ex} al fallo. Buen reclutamiento, mal sitio para insistir si no era el plan.",
    )

    private fun effortWithNumbers(snapshot: LiveRelatorSnapshot, extra: String): String =
        effortWithNumbersVariants(snapshot, listOf(extra)).first()

    private fun effortWithNumbersVariants(snapshot: LiveRelatorSnapshot, extras: List<String>): List<String> {
        val mode = if (snapshot.intensityMode == com.example.kpkn.data.models.IntensityMode.RIR) "RIR" else "RPE"
        val live = snapshot.enteredIntensity?.let { formatRelatorLoad(it) }
            ?: return situateWorkingVariants(snapshot)
        val plan = snapshot.plannedIntensity?.let { formatRelatorLoad(it) }
        val head = if (plan != null) "$mode $live; el plan era $plan" else "$mode $live"
        return extras.map { extra -> "$head en {ex}. $extra" }
    }

    private fun dropsetOne(snapshot: LiveRelatorSnapshot): String = dropsetOneVariants(snapshot).first()

    private fun dropsetOneVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val extra = snapshot.dropSetCount - snapshot.plannedDropCount
        return listOf(
            "Añadiste $extra drop extra a esta serie de {ex}.",
            "Un drop extra en {ex}. Más ardor local; no lo conviertas en hábito.",
            "Drop de más en {ex}. Termina limpio y listo.",
        )
    }

    private fun dropsetMany(snapshot: LiveRelatorSnapshot): String = dropsetManyVariants(snapshot).first()

    private fun dropsetManyVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val extra = snapshot.dropSetCount - snapshot.plannedDropCount
        val axial = snapshot.compound != RelatorCompound.NONE ||
            snapshot.family == RelatorFamily.SQUAT ||
            snapshot.family == RelatorFamily.HINGE
        return if (axial) {
            listOf(
                "Añadiste $extra drops extra en {ex}. En un básico el costo de SNC y articulación sube.",
                "$extra drops de más en {ex}. En un compuesto eso pesa más de lo que parece.",
                "Varios drops extra en {ex}. Cuida columna y sistema; no es un aislado.",
            )
        } else {
            listOf(
                "Añadiste $extra drops extra en {ex}. Más fatiga local; recupera después.",
                "$extra drops de más en {ex}. El músculo arde; la articulación también cuenta.",
                "Drops extra en {ex}. Válido puntualmente; no lo encadenes todo el día.",
            )
        }
    }

    private fun dropsetFollowUp(snapshot: LiveRelatorSnapshot): String = dropsetFollowUpVariants(snapshot).first()

    private fun dropsetFollowUpVariants(snapshot: LiveRelatorSnapshot): List<String> = listOf(
        "Sin descanso: baja 5 kg para esta serie de {ex}.",
        "Dropset seguido: recorta unos 5 kg en {ex} y sigue.",
        "No hay pausa. Quita 5 kg y cierra {ex} con forma.",
    )

    private fun prCopy(snapshot: LiveRelatorSnapshot, starred: Boolean): String {
        val hint = snapshot.prHint ?: return situateWorking(snapshot)
        val rm = formatRelatorLoad(hint.estimatedRmKg)
        val pct = hint.goalPct
        return when {
            starred && pct != null && pct >= 100 ->
                "Nuevo PR: RM ≈ $rm kg. Meta estrella completada."
            starred && pct != null ->
                "Nuevo PR: RM ≈ $rm kg. Llevas el $pct% de la meta."
            starred ->
                "Nuevo PR: RM ≈ $rm kg. Ejercicio estrella, sigue así."
            else ->
                "Nuevo PR: RM ≈ $rm kg. Sigue así."
        }
    }

    private fun discomfortCopy(snapshot: LiveRelatorSnapshot): String = discomfortVariants(snapshot).first()

    private fun discomfortVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val hint = snapshot.discomfortHint ?: return situateWorkingVariants(snapshot)
        val source = hint.sourceExerciseName?.let(::shortSourceName)
        return when {
            hint.fromThisSession && source != null -> listOf(
                "Hoy reportaste ${hint.label} en $source. Cuida eso en {ex}.",
                "${hint.label} hoy en $source. En {ex} no lo fuerces.",
            )
            hint.fromThisSession -> listOf(
                "Hoy reportaste ${hint.label} en {ex}.",
                "${hint.label} hoy en {ex}. Ajusta rango o carga si hace falta.",
            )
            else -> listOf(
                "La última vez en {ex} reportaste ${hint.label}.",
                "La vez pasada {ex} dejó ${hint.label}. Entra con margen.",
            )
        }
    }

    private fun shortSourceName(raw: String): String {
        val first = raw.split(" · ").first().trim()
        val words = first.split(Regex("\\s+")).filter { it.isNotBlank() }
        val compact = if (words.size <= 2) words.joinToString(" ") else words.take(2).joinToString(" ")
        return if (compact.length <= 16) compact else compact.take(15).trimEnd() + "…"
    }
}
