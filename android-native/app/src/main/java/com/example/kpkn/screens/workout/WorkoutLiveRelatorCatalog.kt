package com.example.kpkn.screens.workout

import com.example.kpkn.ui.design.KpknSetFormat

/**
 * Frases del relator de la sesión en vivo (LR1g). Reglas de redacción:
 * - español de producto, sin rótulos al principio y como mucho un «: » por frase;
 * - una idea por frase y, con el nombre del ejercicio incluido, dentro de [RELATOR_MAX_LINE_CHARS];
 * - series como «peso kg × reps», coma decimal, espacio inseparable antes de las unidades y tiempos con [KpknSetFormat.doseTime].
 */
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
        RelatorSpeechBucket.TAG_BEST_PROGRESS -> tagBestProgressVariants(snapshot)
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
        // Ni las cabezas ni las colas llevan «: », así que la frase entera nunca pasa de uno.
        val heads = when {
            snapshot.isSuperset && side != null -> listOf(
                "Superserie, {ex}, lado $side",
                "Ronda $i, {ex}, lado $side",
                "{ex}, lado $side, ronda $i",
            )
            snapshot.isSuperset -> listOf(
                "Superserie, ronda $i. Te toca {ex}",
                "Ronda $i de superserie, ahora {ex}",
                "Sigue la superserie, ronda $i, {ex}",
            )
            side != null -> listOf(
                "Serie $i de $n de {ex}, lado $side",
                "Lado $side, serie $i de $n de {ex}",
                "{ex}, serie $i de $n, lado $side",
            )
            else -> listOf(
                "Serie $i de $n de {ex}",
                "Vas por la serie $i de $n en {ex}",
                "{ex}, serie $i de $n",
            )
        }
        val session = snapshot.sessionLastSet
        val history = snapshot.historyLastSet
        val lastLifted = snapshot.lastLiftedWeight
        val tail = when {
            snapshot.setIndex > 0 && session != null ->
                ". En la ${session.setNumber} de hoy, ${formatRelatorSetMark(session)}."
            snapshot.setIndex <= 0 && history != null ->
                ". La última vez, ${formatRelatorSetMark(history)}."
            snapshot.setIndex <= 0 && lastLifted != null && lastLifted > 0.0 ->
                ". La última vez, ${formatRelatorKgText(lastLifted)}."
            snapshot.ultraFastApplied -> ". Modo ultrarrápido activo."
            else -> "."
        }
        return heads.map { it + tail }
    }

    private fun cautionFailedSetVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val caution = snapshot.failedSetCaution ?: return situateWorkingVariants(snapshot)
        return if (caution.sameExercise) {
            listOf(
                "La serie ${caution.sourceSetNumber} quedó fallida. Baja un poco la carga y cuida la articulación.",
                "Serie ${caution.sourceSetNumber} fallida. Entra con menos carga y protege la articulación.",
            )
        } else {
            listOf(
                "El ejercicio anterior dejó una serie fallida. Entra suave a {ex}.",
                "Vienes de un fallo. En {ex} empieza con margen.",
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
                    "Eso ya está aplicado. Las series que quedan ya van en dropset.",
                    "Las series que quedan ya van en dropset. Nada que cambiar.",
                    "Los dropsets ya estaban. Seguimos.",
                )
                RelatorAssistActionKind.HALVE_SETS -> listOf(
                    "No había series suficientes para recortar a la mitad.",
                    "No alcanza para partir las series a la mitad.",
                    "Quedan pocas series. No pude recortar.",
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
                    "$target no se pudo mover al final.",
                    "Ese cambio de orden no aplicó. Seguimos aquí.",
                )
                RelatorAssistActionKind.OMIT_SET -> listOf(
                    "No pude omitir esa serie. Ya no está disponible.",
                    "Esa serie ya no se puede omitir.",
                    "Omitir no aplicó. Seguimos con lo que toca.",
                )
                RelatorAssistActionKind.APPLY_SUGGESTED_LOAD,
                RelatorAssistActionKind.ADJUST_LOAD,
                -> listOf(
                    "No pude cambiar la carga ahora.",
                    "Ese ajuste de kilos no aplicó.",
                    "La carga se quedó como estaba.",
                )
                RelatorAssistActionKind.START_REST,
                RelatorAssistActionKind.EXTEND_REST,
                -> listOf(
                    "No pude tocar el descanso ahora.",
                    "El descanso no aceptó ese cambio.",
                    "El descanso sigue igual. Seguimos.",
                )
                RelatorAssistActionKind.SKIP_REMAINING_WARMUPS -> listOf(
                    "No pude saltar las aproximaciones ahora.",
                    "El calentamiento no se pudo recortar.",
                    "Las aproximaciones siguen igual. Sigue la rampa.",
                )
                RelatorAssistActionKind.OPEN_REPLACE,
                RelatorAssistActionKind.OPEN_READINESS,
                RelatorAssistActionKind.OPEN_TECHNIQUE,
                RelatorAssistActionKind.OPEN_HISTORY,
                RelatorAssistActionKind.CAPTURE_MEDIA,
                RelatorAssistActionKind.OPEN_ALBUM,
                -> listOf(
                    "Esa pantalla no se abrió ahora.",
                    "No pude abrir esa vista. Reintenta.",
                    "El atajo no aplicó. Seguimos aquí.",
                )
            }
        } else {
            when (ack.kind) {
                RelatorAssistActionKind.CONVERT_DROPSETS -> listOf(
                    "Aplico dropsets a lo que queda para terminar antes. Cuida las articulaciones.",
                    "Dropsets en lo que queda. Aceleramos, pero cuida las articulaciones.",
                    "Las series que quedan van en dropset. Terminas antes, sin forzar la articulación.",
                )
                RelatorAssistActionKind.PREVIEW_ULTRAFAST -> listOf(
                    "Aplico el modo ultrarrápido: menos series y menos descanso. Cuida las articulaciones.",
                    "Modo ultrarrápido activo, con menos series y menos descanso.",
                    "Modo ultrarrápido puesto. Prioriza terminar y cuida las articulaciones.",
                )
                RelatorAssistActionKind.HALVE_SETS -> listOf(
                    "Recorto a la mitad las series que quedan. Hoy no persigas volumen extra.",
                    "Dejo la mitad de las series que quedan. Cierra sin perseguir volumen.",
                    "Recorté las series a la mitad. Hoy no sumes extra.",
                )
                RelatorAssistActionKind.OMIT_SET -> listOf(
                    "Dejo esa serie omitida y seguimos.",
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
                    "Regresamos a $target, retomamos desde aquí.",
                    "$target otra vez. Sigue esta serie.",
                )
                RelatorAssistActionKind.MOVE_EXERCISE_END -> listOf(
                    "Listo, $target queda al final. Seguimos con lo de ahora.",
                    "$target se va al final. Ahora toca lo que sigue.",
                    "Lo mandé al final. $target espera, seguimos aquí.",
                )
                RelatorAssistActionKind.ADD_MOBILITY -> listOf(
                    "Añado esa movilidad a {ex}.",
                    "Movilidad añadida. Un momento para la articulación y seguimos.",
                    "Sumé esa movilidad. Así llegas mejor a {ex}.",
                )
                RelatorAssistActionKind.APPLY_SUGGESTED_LOAD -> listOf(
                    "Carga sugerida puesta en {ex}.",
                    "Aplico esos kilos en {ex}.",
                    "Dejo la sugerencia en {ex}.",
                )
                RelatorAssistActionKind.ADJUST_LOAD -> listOf(
                    "Ajusto la carga de {ex}.",
                    "Ajusto los kilos de {ex} según tu estado de hoy.",
                    "Cambio el peso de {ex}.",
                )
                RelatorAssistActionKind.START_REST -> listOf(
                    "Arranco el descanso.",
                    "El descanso ya está en marcha.",
                    "Respira. Deja que corra el descanso.",
                )
                RelatorAssistActionKind.EXTEND_REST -> listOf(
                    "Alargo el descanso un poco.",
                    "Sumo tiempo al descanso.",
                    "Pausa extra puesta.",
                )
                RelatorAssistActionKind.SKIP_REMAINING_WARMUPS -> listOf(
                    "Salto las aproximaciones que quedan.",
                    "Calentamiento recortado. Pasamos a las series efectivas.",
                    "Sin aproximaciones. Entras a {ex}.",
                )
                RelatorAssistActionKind.OPEN_REPLACE -> listOf(
                    "Abro la sustitución de {ex}.",
                    "Cambio de ejercicio a la vista.",
                    "Sustituye {ex} si el cuerpo pide otra cosa.",
                )
                RelatorAssistActionKind.OPEN_READINESS -> listOf(
                    "Abro tu estado de hoy.",
                    "Mira el veredicto de hoy.",
                    "Tu estado de hoy, a la vista.",
                )
                RelatorAssistActionKind.OPEN_TECHNIQUE -> listOf(
                    "Abro la técnica de {ex}.",
                    "Las indicaciones de {ex}, a la vista.",
                    "Revisa el gesto de {ex}.",
                )
                RelatorAssistActionKind.OPEN_HISTORY -> listOf(
                    "Abro el historial de {ex}.",
                    "Las marcas previas de {ex}.",
                    "Historial a la vista.",
                )
                RelatorAssistActionKind.CAPTURE_MEDIA,
                RelatorAssistActionKind.OPEN_ALBUM,
                -> listOf(
                    "Abro tus fotos y vídeos.",
                    "Fotos y vídeos de la sesión, a la vista.",
                    "Ahí tienes tus fotos y vídeos.",
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
                val care = withArticle(joint)
                listOf(
                    "Ya trabajaste $muscle en $source. Cuida $care.",
                    "$source ya cargó $muscle. En {ex} cuida $care.",
                    "Vienes de $source, donde trabajaste $muscle. Cuida $care.",
                )
            } else {
                listOf(
                    "En esta sesión ya trabajaste $muscle en $source.",
                    "$source ya cargó $muscle. No hace falta ir al límite.",
                    "Vienes de trabajar $muscle en $source.",
                )
            }
        } else {
            listOf(
                "Ayer trabajaste $muscle en $source. Hoy no hace falta ir tan pesado.",
                "Ayer cargaste $muscle con $source. Hoy baja un punto la intensidad.",
                "Ayer ya hubo $muscle con $source. Hoy prioriza la calidad.",
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
            "{ex}, aproximación $i de $n.",
        )
    }

    fun idleWarmupLast(snapshot: LiveRelatorSnapshot): String = idleWarmupLastVariants(snapshot).first()

    fun idleWarmupLastVariants(snapshot: LiveRelatorSnapshot): List<String> = listOf(
        "Última aproximación de {ex}. Después vienen las efectivas.",
        "Cierra las aproximaciones de {ex}. Siguen las efectivas.",
        "Última aproximación de {ex}. Ya casi toca trabajar.",
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
        val primary = if (total > 0) "$head, $done de $total." else "$head."
        val second = if (zone != null) {
            "Prepara $zone. Luego {ex}."
        } else {
            "Un momento de movilidad y seguimos con {ex}."
        }
        val third = if (total > 0) {
            "Movilidad $done de $total antes de {ex}."
        } else {
            "Movilidad breve, luego {ex}."
        }
        return listOf(primary, second, third)
    }

    fun idleRest(snapshot: LiveRelatorSnapshot): String = idleRestVariants(snapshot).first()

    fun idleRestVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val i = snapshot.setIndex + 1
        val n = snapshot.setCount.coerceAtLeast(1)
        val last = snapshot.sessionLastSet
        return if (last != null) {
            val mark = formatRelatorSetMark(last)
            listOf(
                "Serie ${last.setNumber} hecha: $mark en {ex}.",
                "La serie ${last.setNumber} fue $mark en {ex}.",
                "Tu última marca en {ex} fue $mark.",
            )
        } else {
            listOf(
                "Descansa, sigue la serie $i de $n de {ex}.",
                "Pausa entre series de {ex}, vas por la $i de $n.",
                "Respira y vuelve a {ex}, serie $i de $n.",
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
        val entered = snapshot.enteredWeight ?: return idleWarmupLastVariants(snapshot)
        val enteredKg = formatRelatorKgText(entered)
        val anchor = snapshot.suggestedWeight ?: snapshot.referenceWeight
        val anchorKg = anchor?.let { formatRelatorKgText(it) }
        val gapKg = anchor?.let { formatRelatorKgText(kotlin.math.abs(entered - it)) }
        return when {
            band == RelatorWeightBand.CONSERVATIVE && anchorKg != null && gapKg != null -> listOf(
                "$enteredKg de aproximación, $gapKg menos que el plan.",
                "Aproximación de $enteredKg, por debajo de $anchorKg. Bien para no gastar fuerzas.",
                "Aproximación liviana, $enteredKg frente a $anchorKg del plan. Reserva fuerzas.",
            )
            band == RelatorWeightBand.AGGRESSIVE && anchorKg != null && gapKg != null -> listOf(
                "$enteredKg de aproximación, $gapKg más que el plan. Ya parece una serie efectiva.",
                "Aproximación de $enteredKg, por encima de los $anchorKg del plan. Ya carga de verdad.",
                "Esa aproximación de $enteredKg está alta frente a $anchorKg. No es calentamiento.",
            )
            else -> listOf(
                "Aproximación de $enteredKg en {ex}.",
                "{ex}, aproximación de $enteredKg.",
                "Vas a $enteredKg en esta aproximación.",
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
        val now = formatRelatorKgText(entered)
        val session = snapshot.sessionLastSet
        if (snapshot.setIndex > 0 && session != null) {
            val last = formatRelatorSetMark(session)
            val change = kgMoreOrLess(entered - session.weightKg)
            val lastKg = formatRelatorKgText(session.weightKg)
            return listOf(
                "$now: $change que en la serie ${session.setNumber} de hoy ($last).",
                "Bajas a $now. En la serie ${session.setNumber} de hoy fueron $lastKg.",
                "$now es menos que tu marca de hoy. Si es a propósito, adelante.",
            )
        }
        val lastValue = snapshot.historyLastSet?.weightKg ?: snapshot.lastLiftedWeight
        return if (lastValue != null) {
            val lastKg = formatRelatorKgText(lastValue)
            listOf(
                "$now: ${kgMoreOrLess(entered - lastValue)} que la última vez.",
                "Bajas a $now. La última vez fueron $lastKg.",
                "$now es menos que la última vez ($lastKg). Si es a propósito, adelante.",
            )
        } else {
            listOf(
                "$now, por debajo de la última vez en {ex}.",
                "Bajas a $now en {ex}.",
                "$now es menos de lo habitual en {ex}.",
            )
        }
    }

    private fun weightAbove(snapshot: LiveRelatorSnapshot): String = weightAboveVariants(snapshot).first()

    private fun weightAboveVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val entered = snapshot.enteredWeight ?: return situateWorkingVariants(snapshot)
        val now = formatRelatorKgText(entered)
        val anchor = snapshot.suggestedWeight ?: snapshot.referenceWeight
        return if (anchor != null) {
            val planKg = formatRelatorKgText(anchor)
            listOf(
                "$now: ${kgMoreOrLess(entered - anchor)} que lo sugerido.",
                "Subes a $now. Lo sugerido era $planKg.",
                "$now es más de lo sugerido ($planKg). Si la técnica aguanta, adelante.",
            )
        } else {
            listOf(
                "$now, por encima de lo sugerido en {ex}.",
                "Subes a $now en {ex}.",
                "$now es más de lo previsto en {ex}.",
            )
        }
    }

    private fun repsBelow(snapshot: LiveRelatorSnapshot): String = repsBelowVariants(snapshot).first()

    private fun repsBelowVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val live = snapshot.enteredReps ?: return situateWorkingVariants(snapshot)
        val plan = snapshot.plannedReps
        return if (plan != null) {
            val gap = formatRelatorNumber(kotlin.math.abs(plan - live))
            val planText = formatRelatorNumber(plan)
            listOf(
                "${repsText(live)}: $gap menos que el plan ($planText).",
                "${repsText(live)} en {ex}, menos que el plan ($planText).",
                "Bajas a ${repsText(live)} (plan $planText). Si la fatiga manda, está bien.",
            )
        } else {
            listOf(
                "Anotaste ${repsText(live)} en {ex}, por debajo del plan.",
                "${repsText(live)} en {ex}, menos de lo previsto.",
                "Bajas a ${repsText(live)} en {ex}, por debajo del objetivo.",
            )
        }
    }

    private fun repsAbove(snapshot: LiveRelatorSnapshot): String = repsAboveVariants(snapshot).first()

    private fun repsAboveVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val live = snapshot.enteredReps ?: return situateWorkingVariants(snapshot)
        val plan = snapshot.plannedReps
        return if (plan != null) {
            val gap = formatRelatorNumber(kotlin.math.abs(live - plan))
            val planText = formatRelatorNumber(plan)
            listOf(
                "${repsText(live)}: $gap más que el plan ($planText).",
                "${repsText(live)} en {ex}, más que el plan ($planText).",
                "Subes a ${repsText(live)} (plan $planText). Si siguen limpias, bien.",
            )
        } else {
            listOf(
                "Anotaste ${repsText(live)} en {ex}, por encima del plan.",
                "${repsText(live)} en {ex}, más de lo previsto.",
                "Subes a ${repsText(live)} en {ex}, por encima del objetivo.",
            )
        }
    }

    private fun timeCopy(snapshot: LiveRelatorSnapshot): String = timeCopyVariants(snapshot).first()

    private fun timeCopyVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val live = snapshot.enteredReps?.let { timeText(it) } ?: return situateWorkingVariants(snapshot)
        val plan = snapshot.plannedReps?.let { timeText(it) }
        return if (plan != null) {
            listOf(
                "Tiempo de $live, el plan era $plan.",
                "$live frente a $plan de plan en {ex}.",
                "Cronómetro a $live (plan $plan) en {ex}.",
            )
        } else {
            listOf(
                "Anotaste $live de tiempo en {ex}.",
                "$live en {ex}.",
                "Marcas $live de trabajo en {ex}.",
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
                "Queda holgado para $range. Si buscas estímulo, aprieta más.",
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
                "Más cómodo, con menos fatiga y menos estímulo.",
                "Más fácil que lo programado, con menos costo.",
                "Por debajo del plan. Válido si hoy toca conservar.",
            ),
        )

    private fun effortHard(snapshot: LiveRelatorSnapshot): String = effortHardVariants(snapshot).first()

    private fun effortHardVariants(snapshot: LiveRelatorSnapshot): List<String> =
        effortWithNumbersVariants(
            snapshot,
            extras = listOf(
                "Más esfuerzo, con más reclutamiento y más costo.",
                "Aprietas más de lo programado. Cuenta el extra de fatiga.",
                "Zona dura. Más estímulo, más precio para lo que queda.",
            ),
        )

    private fun effortFailure(snapshot: LiveRelatorSnapshot): String = effortFailureVariants(snapshot).first()

    private fun effortFailureVariants(snapshot: LiveRelatorSnapshot): List<String> = listOf(
        "Marcaste fallo. Máximo reclutamiento, pero cobra fatiga y articulación.",
        "Fallo en {ex}. Recupera bien antes de insistir.",
        "Al fallo en {ex}. No insistas si no estaba en el plan.",
    )

    private fun effortWithNumbers(snapshot: LiveRelatorSnapshot, extra: String): String =
        effortWithNumbersVariants(snapshot, listOf(extra)).first()

    private fun effortWithNumbersVariants(snapshot: LiveRelatorSnapshot, extras: List<String>): List<String> {
        val mode = if (snapshot.intensityMode == com.example.kpkn.data.models.IntensityMode.RIR) "RIR" else "RPE"
        val live = snapshot.enteredIntensity?.let { formatRelatorNumber(it) }
            ?: return situateWorkingVariants(snapshot)
        val plan = snapshot.plannedIntensity?.let { formatRelatorNumber(it) }
        val head = if (plan != null) "$mode $live frente a $plan del plan." else "$mode $live."
        return extras.map { extra -> "$head $extra" }
    }

    private fun dropsetOne(snapshot: LiveRelatorSnapshot): String = dropsetOneVariants(snapshot).first()

    private fun dropsetOneVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val extra = snapshot.dropSetCount - snapshot.plannedDropCount
        return listOf(
            "Añadiste $extra drop extra a esta serie de {ex}.",
            "Un drop extra en {ex}. Más ardor, pero no lo hagas hábito.",
            "Drop de más en {ex}. Termina el drop con técnica limpia.",
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
                "Añadiste $extra drops. En un básico sube el costo de SNC y articulación.",
                "$extra drops de más. En un compuesto pesan más de lo que parece.",
                "Varios drops extra. Cuida columna y sistema, no es un aislado.",
            )
        } else {
            listOf(
                "Añadiste $extra drops extra. Más fatiga local, recupera después.",
                "$extra drops de más. El músculo arde, pero la articulación también cuenta.",
                "Drops extra. Válido de vez en cuando, no todo el día.",
            )
        }
    }

    private fun dropsetFollowUp(snapshot: LiveRelatorSnapshot): String = dropsetFollowUpVariants(snapshot).first()

    private fun dropsetFollowUpVariants(snapshot: LiveRelatorSnapshot): List<String> = listOf(
        "Sin descanso, baja 5\u00A0kg para esta serie de {ex}.",
        "Dropset seguido, recorta unos 5\u00A0kg en {ex} y sigue.",
        "No hay pausa. Quita 5\u00A0kg y cierra {ex} con técnica limpia.",
    )

    private fun tagBestProgressVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val rawName = snapshot.tagProgressHint?.tagName?.trim().orEmpty()
        if (rawName.isEmpty()) return situateWorkingVariants(snapshot)
        val tag = if (rawName.length > 24) rawName.take(23) + "…" else rawName
        return listOf(
            "En «$tag» es donde mejor progresas, trata de hacerla más seguido.",
            "Tu mejor curva está en «$tag». Priorízala cuando puedas.",
            "«$tag» te está rindiendo más. Vale la pena repetirla.",
        )
    }

    private fun prCopy(snapshot: LiveRelatorSnapshot, starred: Boolean): String {
        val hint = snapshot.prHint ?: return situateWorking(snapshot)
        val rm = "RM estimado de ${formatRelatorKgText(hint.estimatedRmKg)}"
        val mark = prSetMark(snapshot)
        // Con la serie a la vista, el récord es la serie y el RM va aparte; sin ella, el récord es el RM.
        val lead = if (mark != null) "Récord: $mark." else "Récord: $rm."
        val rmNote = if (mark != null) rm else null
        val pct = hint.goalPct
        return when {
            starred && pct != null && pct >= 100 ->
                "$lead Meta de tu ejercicio estrella completada."
            starred && pct != null ->
                if (rmNote != null) {
                    "$lead $rmNote, el ${pct}\u00A0% de tu meta."
                } else {
                    "$lead Llevas el ${pct}\u00A0% de la meta."
                }
            starred ->
                if (rmNote != null) "$lead $rmNote, en tu ejercicio estrella." else "$lead Es tu ejercicio estrella."
            else ->
                if (rmNote != null) "$lead $rmNote." else lead
        }
    }

    /** La serie que dio el récord: la última hecha en el descanso, o la que está en pantalla si aún se edita. */
    private fun prSetMark(snapshot: LiveRelatorSnapshot): String? {
        if (snapshot.phase == RelatorPhase.REST) {
            return snapshot.sessionLastSet?.let { formatRelatorSetMark(it) }
        }
        val weight = snapshot.enteredWeight?.takeIf { it > 0.0 } ?: return null
        val reps = snapshot.enteredReps?.takeIf { it > 0.0 } ?: return formatRelatorKgText(weight)
        return "${formatRelatorKgText(weight)} × ${formatRelatorNumber(reps)}"
    }

    private fun discomfortCopy(snapshot: LiveRelatorSnapshot): String = discomfortVariants(snapshot).first()

    private fun discomfortVariants(snapshot: LiveRelatorSnapshot): List<String> {
        val hint = snapshot.discomfortHint ?: return situateWorkingVariants(snapshot)
        val source = hint.sourceExerciseName?.let(::shortSourceName)
        val label = hint.label
        // Dentro de la frase el nombre de la molestia va en minúscula («hombro anterior»).
        val inline = label.replaceFirstChar { it.lowercase() }
        return when {
            hint.fromThisSession && source != null -> listOf(
                "Hoy reportaste $inline en $source. Entra con cuidado.",
                "$label hoy en $source. No lo fuerces aquí.",
            )
            hint.fromThisSession -> listOf(
                "Hoy reportaste $inline en {ex}.",
                "$label hoy en {ex}. Ajusta rango o carga.",
            )
            else -> listOf(
                "La última vez en {ex} reportaste $inline.",
                "Tuviste molestia en $inline la vez pasada. Entra con margen.",
            )
        }
    }

    /** Los kilos de diferencia con «más» o «menos» según el signo; sin diferencia, «lo mismo» (para «… que la última vez»). */
    private fun kgMoreOrLess(delta: Double): String = when {
        delta > 0.05 -> "${formatRelatorKgText(delta)} más"
        delta < -0.05 -> "${formatRelatorKgText(-delta)} menos"
        else -> "lo mismo"
    }

    /** Las repeticiones con su unidad pegada a la cifra: «6 reps», y «1 rep» cuando es una sola. */
    private fun repsText(value: Double): String =
        "${formatRelatorNumber(value)}\u00A0${if (value == 1.0) "rep" else "reps"}"

    /** Segundos de una serie por tiempo, como en el resto de la app: «45 s» o «3:15». */
    private fun timeText(seconds: Double): String =
        KpknSetFormat.doseTime(kotlin.math.round(seconds).toInt())

    /** La zona con su artículo: «los codos», «la zona lumbar». */
    private fun withArticle(zone: String): String = when (zone) {
        "lumbar" -> "la zona lumbar"
        "pecho" -> "el pecho"
        else -> "los $zone"
    }

    private fun shortSourceName(raw: String): String {
        val first = raw.split(" · ").first().trim()
        val words = first.split(Regex("\\s+")).filter { it.isNotBlank() }
        val compact = if (words.size <= 2) words.joinToString(" ") else words.take(2).joinToString(" ")
        return if (compact.length <= 16) compact else compact.take(15).trimEnd() + "…"
    }
}
