package com.example.kpkn.screens.workout

internal object WorkoutLiveRelatorCatalog {

    fun copyFor(bucket: RelatorSpeechBucket, snapshot: LiveRelatorSnapshot): String = when (bucket) {
        RelatorSpeechBucket.HIDDEN -> fallback(snapshot)
        RelatorSpeechBucket.IDLE_MOBILITY -> idleMobility(snapshot)
        RelatorSpeechBucket.IDLE_WARMUP -> idleWarmupEarly(snapshot)
        RelatorSpeechBucket.IDLE_WARMUP_LAST -> idleWarmupLast(snapshot)
        RelatorSpeechBucket.IDLE_REST -> idleRest(snapshot)
        RelatorSpeechBucket.IDLE_FIRST_HIST -> idleFirstWithHistory(snapshot)
        RelatorSpeechBucket.IDLE_FIRST_NEW -> idleFirstNoHistory(snapshot)
        RelatorSpeechBucket.IDLE_MID -> idleMidWorking(snapshot)
        RelatorSpeechBucket.IDLE_LAST -> idleLastWorking(snapshot)
        RelatorSpeechBucket.IDLE_COMPOUND -> idleCompound(snapshot)
        RelatorSpeechBucket.TISSUE_INTRA, RelatorSpeechBucket.TISSUE_DAY -> idleTissue(snapshot)
        RelatorSpeechBucket.WARMUP_WEIGHT_BELOW -> warmupWeight(snapshot, RelatorWeightBand.CONSERVATIVE)
        RelatorSpeechBucket.WARMUP_WEIGHT_ABOVE -> warmupWeight(snapshot, RelatorWeightBand.AGGRESSIVE)
        RelatorSpeechBucket.WEIGHT_BELOW -> weightBelow(snapshot)
        RelatorSpeechBucket.WEIGHT_ABOVE -> weightAbove(snapshot)
        RelatorSpeechBucket.REPS_BELOW -> repsBelow(snapshot)
        RelatorSpeechBucket.REPS_ABOVE -> repsAbove(snapshot)
        RelatorSpeechBucket.TIME -> timeCopy(snapshot)
        RelatorSpeechBucket.EFFORT_INEFFECTIVE -> effortIneffective(snapshot)
        RelatorSpeechBucket.EFFORT_MEASURED -> effortMeasured(snapshot)
        RelatorSpeechBucket.EFFORT_HARD -> effortHard(snapshot)
        RelatorSpeechBucket.EFFORT_FAILURE -> effortFailure(snapshot)
        RelatorSpeechBucket.DROPSET_ONE -> dropsetOne(snapshot)
        RelatorSpeechBucket.DROPSET_MANY -> dropsetMany(snapshot)
        RelatorSpeechBucket.PR -> prCopy(snapshot, starred = false)
        RelatorSpeechBucket.PR_STAR -> prCopy(snapshot, starred = true)
        RelatorSpeechBucket.IDLE_DISCOMFORT -> discomfortCopy(snapshot)
        RelatorSpeechBucket.ASSIST_GAP_SET,
        RelatorSpeechBucket.ASSIST_GAP_UNI,
        RelatorSpeechBucket.ASSIST_GAP_SUPERSET,
        RelatorSpeechBucket.ASSIST_GAP_EXERCISE,
        RelatorSpeechBucket.ASSIST_TIME,
        RelatorSpeechBucket.ASSIST_MOBILITY,
        -> snapshot.assistOffer?.text ?: fallback(snapshot)
    }

    fun idleFirstWithHistory(snapshot: LiveRelatorSnapshot): String = situateWorking(snapshot)

    fun idleFirstNoHistory(snapshot: LiveRelatorSnapshot): String = situateWorking(snapshot)

    fun idleMidWorking(snapshot: LiveRelatorSnapshot): String = situateWorking(snapshot)

    fun idleLastWorking(snapshot: LiveRelatorSnapshot): String = situateWorking(snapshot)

    fun idleCompound(snapshot: LiveRelatorSnapshot): String = situateWorking(snapshot)

    internal fun situateWorking(snapshot: LiveRelatorSnapshot): String {
        val i = snapshot.setIndex + 1
        val n = snapshot.setCount.coerceAtLeast(1)
        val side = snapshot.activeSideLabel
        val head = when {
            snapshot.isSuperset && side != null -> "Superserie, ronda $i de {ex}, lado $side"
            snapshot.isSuperset -> "Superserie, ronda $i: te toca {ex}"
            side != null -> "Serie $i de $n de {ex}, lado $side"
            else -> "Serie $i de $n de {ex}"
        }
        val session = snapshot.sessionLastSet
        val history = snapshot.historyLastSet
        val kg = snapshot.lastLiftedWeight?.takeIf { it > 0.0 }?.let { formatRelatorLoad(it) }
        val tail = when {
            session != null && session.setNumber < i ->
                ". En la ${session.setNumber}: ${formatRelatorSetMark(session)}."
            history != null && snapshot.setIndex <= 0 ->
                ". La última vez: ${formatRelatorSetMark(history)}."
            kg != null && snapshot.setIndex <= 0 -> ". La última vez: $kg kg."
            kg != null -> ". Antes moviste $kg kg."
            else -> "."
        }
        return head + tail
    }

    fun idleTissue(snapshot: LiveRelatorSnapshot): String {
        val hint = snapshot.tissueHint ?: return fallback(snapshot)
        val source = shortSourceName(hint.sourceExerciseName)
        val muscle = hint.muscleLabel
        val joint = hint.jointCare
        return if (hint.window == RelatorTissueWindow.INTRA) {
            if (joint != null) {
                "Los $muscle ya trabajaron en $source. Cuida los $joint."
            } else {
                "Los $muscle ya trabajaron en $source en esta sesión."
            }
        } else {
            "Ayer $source trabajó $muscle. Hoy no hace falta ir tan pesado."
        }
    }

    fun idleWarmupEarly(snapshot: LiveRelatorSnapshot): String {
        val i = (snapshot.warmupIncompleteIndex ?: 0) + 1
        val n = snapshot.warmupCount.coerceAtLeast(1)
        return "Aproximación $i de $n de {ex}."
    }

    fun idleWarmupLast(snapshot: LiveRelatorSnapshot): String =
        "Última aproximación de {ex}. Después vienen las efectivas."

    fun idleMobility(snapshot: LiveRelatorSnapshot): String {
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
        return if (total > 0) "$head: $done de $total." else "$head."
    }

    fun idleRest(snapshot: LiveRelatorSnapshot): String {
        val i = snapshot.setIndex + 1
        val n = snapshot.setCount.coerceAtLeast(1)
        val last = snapshot.sessionLastSet
        return if (last != null) {
            "Descanso. Serie ${last.setNumber}: ${formatRelatorSetMark(last)} de {ex}."
        } else {
            "Descanso. Vas en la serie $i de $n de {ex}."
        }
    }

    fun fallback(snapshot: LiveRelatorSnapshot): String = when (snapshot.phase) {
        RelatorPhase.MOBILITY -> idleMobility(snapshot)
        RelatorPhase.WARMUP -> idleWarmupEarly(snapshot)
        RelatorPhase.REST -> idleRest(snapshot)
        else -> situateWorking(snapshot)
    }

    fun warmupWeight(snapshot: LiveRelatorSnapshot, band: RelatorWeightBand): String {
        val entered = snapshot.enteredWeight?.let { formatRelatorLoad(it) }
        val anchor = (snapshot.suggestedWeight ?: snapshot.referenceWeight)?.let { formatRelatorLoad(it) }
        return when {
            entered == null -> idleWarmupLast(snapshot)
            band == RelatorWeightBand.CONSERVATIVE && anchor != null ->
                "Aprox a $entered kg; el plan era $anchor kg. Más liviana, no debería fatigar."
            band == RelatorWeightBand.AGGRESSIVE && anchor != null ->
                "Aprox a $entered kg; el plan era $anchor kg. Ya parece serie efectiva."
            else -> "Aprox a $entered kg de {ex}."
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

    private fun weightBelow(snapshot: LiveRelatorSnapshot): String {
        val entered = snapshot.enteredWeight ?: return situateWorking(snapshot)
        val now = formatRelatorLoad(entered)
        val lastKg = snapshot.lastLiftedWeight
        val last = lastKg?.let { formatRelatorLoad(it) }
        val delta = lastKg?.let { formatRelatorDelta(entered - it) }
        return if (last != null && delta != null) {
            "Ahora $now kg; la última fue $last kg ($delta kg) en {ex}."
        } else {
            "Ahora $now kg, por debajo de la última en {ex}."
        }
    }

    private fun weightAbove(snapshot: LiveRelatorSnapshot): String {
        val entered = snapshot.enteredWeight ?: return situateWorking(snapshot)
        val now = formatRelatorLoad(entered)
        val anchor = snapshot.suggestedWeight ?: snapshot.referenceWeight
        val plan = anchor?.let { formatRelatorLoad(it) }
        val delta = anchor?.let { formatRelatorDelta(entered - it) }
        return if (plan != null && delta != null) {
            "Ahora $now kg; lo sugerido era $plan kg ($delta kg) en {ex}."
        } else {
            "Ahora $now kg, por encima de lo sugerido en {ex}."
        }
    }

    private fun repsBelow(snapshot: LiveRelatorSnapshot): String {
        val live = snapshot.enteredReps?.let { formatRelatorLoad(it) } ?: return situateWorking(snapshot)
        val plan = snapshot.plannedReps?.let { formatRelatorLoad(it) }
        return if (plan != null) {
            "Anotaste $live reps; el plan era $plan (por debajo) en {ex}."
        } else {
            "Anotaste $live reps en {ex}, por debajo del plan."
        }
    }

    private fun repsAbove(snapshot: LiveRelatorSnapshot): String {
        val live = snapshot.enteredReps?.let { formatRelatorLoad(it) } ?: return situateWorking(snapshot)
        val plan = snapshot.plannedReps?.let { formatRelatorLoad(it) }
        return if (plan != null) {
            "Anotaste $live reps; el plan era $plan (por encima) en {ex}."
        } else {
            "Anotaste $live reps en {ex}, por encima del plan."
        }
    }

    private fun timeCopy(snapshot: LiveRelatorSnapshot): String {
        val live = snapshot.enteredReps?.let { formatRelatorLoad(it) } ?: return situateWorking(snapshot)
        val plan = snapshot.plannedReps?.let { formatRelatorLoad(it) }
        return if (plan != null) {
            "Tiempo ${live}s; el plan era ${plan}s en {ex}."
        } else {
            "Tiempo anotado: ${live}s en {ex}."
        }
    }

    private fun effortIneffective(snapshot: LiveRelatorSnapshot): String {
        val range = if (snapshot.intensityMode == com.example.kpkn.data.models.IntensityMode.RIR) {
            "RIR 0–3"
        } else {
            "RPE 7–9"
        }
        return effortWithNumbers(
            snapshot,
            extra = "Estímulo bajo para hipertrofia ($range).",
        )
    }

    private fun effortMeasured(snapshot: LiveRelatorSnapshot): String =
        effortWithNumbers(
            snapshot,
            extra = "Más cómodo que el plan: menos fatiga y menos estímulo.",
        )

    private fun effortHard(snapshot: LiveRelatorSnapshot): String =
        effortWithNumbers(
            snapshot,
            extra = "Más esfuerzo que el plan: más reclutamiento y más costo.",
        )

    private fun effortFailure(snapshot: LiveRelatorSnapshot): String =
        "Marcaste fallo en {ex}. Máximo reclutamiento; cobra fatiga y articulación."

    private fun effortWithNumbers(snapshot: LiveRelatorSnapshot, extra: String): String {
        val mode = if (snapshot.intensityMode == com.example.kpkn.data.models.IntensityMode.RIR) "RIR" else "RPE"
        val live = snapshot.enteredIntensity?.let { formatRelatorLoad(it) } ?: return situateWorking(snapshot)
        val plan = snapshot.plannedIntensity?.let { formatRelatorLoad(it) }
        val head = if (plan != null) "$mode $live; el plan era $plan" else "$mode $live"
        return "$head en {ex}. $extra"
    }

    private fun dropsetOne(snapshot: LiveRelatorSnapshot): String {
        val extra = snapshot.dropSetCount - snapshot.plannedDropCount
        return "Añadiste $extra drop extra a esta serie de {ex}."
    }

    private fun dropsetMany(snapshot: LiveRelatorSnapshot): String {
        val extra = snapshot.dropSetCount - snapshot.plannedDropCount
        val axial = snapshot.compound != RelatorCompound.NONE ||
            snapshot.family == RelatorFamily.SQUAT ||
            snapshot.family == RelatorFamily.HINGE
        return if (axial) {
            "Añadiste $extra drops extra en {ex}. En un básico el costo de SNC y articulación sube."
        } else {
            "Añadiste $extra drops extra en {ex}. Más fatiga local; recupera después."
        }
    }

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

    private fun discomfortCopy(snapshot: LiveRelatorSnapshot): String {
        val hint = snapshot.discomfortHint ?: return situateWorking(snapshot)
        val source = hint.sourceExerciseName?.let(::shortSourceName)
        return when {
            hint.fromThisSession && source != null ->
                "Hoy reportaste ${hint.label} en $source. Cuida eso en {ex}."
            hint.fromThisSession ->
                "Hoy reportaste ${hint.label} en {ex}."
            else ->
                "La última vez en {ex} reportaste ${hint.label}."
        }
    }

    private fun shortSourceName(raw: String): String {
        val first = raw.split(" · ").first().trim()
        val words = first.split(Regex("\\s+")).filter { it.isNotBlank() }
        val compact = if (words.size <= 2) words.joinToString(" ") else words.take(2).joinToString(" ")
        return if (compact.length <= 16) compact else compact.take(15).trimEnd() + "…"
    }
}
