package com.example.kpkn.domain.competitions

import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** El resultado principal de una competencia según su forma: lo que va a la derecha de la fila y en la celebración. */
data class CompetitionHeadline(val value: String, val caption: String?)

/** Una fila de «Tu historial» (c-hub, `.meet`). [medal] null = disco sin podio con el icono [icon]. */
data class CompetitionHubRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val headline: CompetitionHeadline?,
    val medal: CompetitionMedalHonor?,
    val icon: String,
    val badge: String?,
    val discipline: CompetitionDiscipline,
    val completed: Boolean,
)

/** La tarjeta «Próxima» (c-hub). [daysUntil] 0 = hoy. [openers] son los primeros intentos del plan. */
data class CompetitionNextCard(
    val id: String,
    val title: String,
    val meta: String,
    val daysUntil: Long?,
    val openers: List<Pair<String, String>>,
    val discipline: CompetitionDiscipline,
)

data class CompetitionChartPoint(val date: LocalDate, val totalKg: Double)

data class CompetitionHubModel(
    val next: CompetitionNextCard?,
    val history: List<CompetitionHubRow>,
    val historyCount: String,
    val chart: List<CompetitionChartPoint>,
    val chartCaption: String?,
    val isEmpty: Boolean,
)

/** Fila de «Frente a la anterior» (c-detail) y de c-compare. */
data class CompetitionCompareRow(val label: String, val value: String, val delta: String?, val deltaSign: Int, val other: String? = null)

/**
 * Lo que se lee de las competencias para las pantallas del lienzo Registros v4: la página (c-hub), la ficha (c-detail), la
 * comparación (c-compare) y la celebración (c-cele). Todo puro: los registros entran como lista.
 */
object CompetitionInsights {
    fun dateOf(record: CompetitionRecord): LocalDate? = CompetitionFormat.parseDate(record.eventDate)

    fun visible(records: List<CompetitionRecord>): List<CompetitionRecord> = records.filter { it.status != CompetitionRecordStatus.ARCHIVED }

    /** La próxima: la planificada de fecha más cercana desde hoy (o sin fecha). */
    fun next(records: List<CompetitionRecord>, today: LocalDate): CompetitionRecord? =
        visible(records).filter { it.status == CompetitionRecordStatus.PLANNED }
            .filter { dateOf(it)?.let { d -> !d.isBefore(today) } ?: true }
            .minByOrNull { dateOf(it) ?: LocalDate.MAX }

    fun daysUntil(record: CompetitionRecord, today: LocalDate): Long? = dateOf(record)?.let { ChronoUnit.DAYS.between(today, it) }

    /** Las anteriores completadas de la misma disciplina, de la más reciente a la más antigua. */
    fun previousOf(records: List<CompetitionRecord>, record: CompetitionRecord, discipline: (CompetitionRecord) -> CompetitionDiscipline): List<CompetitionRecord> {
        val date = dateOf(record) ?: return emptyList()
        val own = discipline(record)
        return visible(records).filter {
            it.id != record.id && it.status == CompetitionRecordStatus.COMPLETED && discipline(it) == own &&
                (dateOf(it)?.isBefore(date) ?: false) && (own.form != CompetitionResultForm.CLOCK || sameDistance(it.distanceKm, record.distanceKm))
        }.sortedByDescending { dateOf(it) }
    }

    fun sameDistance(a: Double?, b: Double?): Boolean = a != null && b != null && a > 0 && abs(a - b) / b < 0.02

    /** Puntos de una competencia de carga: la fórmula de su federación en powerlifting, Sinclair en halterofilia. */
    fun points(record: CompetitionRecord, discipline: CompetitionDiscipline): Pair<String, Double>? = when (discipline) {
        CompetitionDiscipline.POWERLIFTING -> CompetitionScoring.displayedPoints(record)?.let { it.label to it.value }
        CompetitionDiscipline.WEIGHTLIFTING -> SinclairPoints.of(CompetitionAttempts.total(record, discipline), record.bodyweightKg,
            CompetitionScoring.scoringSex(record) == "female")?.let { "Sinclair" to it }
        else -> null
    }

    fun pointsLabel(record: CompetitionRecord, discipline: CompetitionDiscipline): String? = when (discipline) {
        CompetitionDiscipline.POWERLIFTING -> CompetitionScoring.formulaFor(record).label
        CompetitionDiscipline.WEIGHTLIFTING -> "Sinclair"
        else -> null
    }

    fun wins(record: CompetitionRecord): Int = record.bouts.count { it.won }
    fun losses(record: CompetitionRecord): Int = record.bouts.count { !it.won }

    /** La medalla que sale sola de los combates: final ganada oro, final perdida plata, semifinal perdida bronce. */
    fun boutMedal(record: CompetitionRecord): CompetitionMedalHonor? {
        val last = record.bouts.lastOrNull() ?: return null
        val round = last.round.lowercase()
        return when {
            round.startsWith("final") -> if (last.won) CompetitionMedalHonor.GOLD else CompetitionMedalHonor.SILVER
            round.startsWith("semi") && !last.won -> CompetitionMedalHonor.BRONZE
            else -> null
        }
    }

    fun paceSecondsPerKm(record: CompetitionRecord): Double? {
        val time = record.finishTimeSeconds ?: return null
        val km = record.distanceKm?.takeIf { it > 0 } ?: return null
        return time / km
    }

    /** El resultado principal de [record] según su forma. Null si aún no tiene resultado. */
    fun headline(record: CompetitionRecord, discipline: CompetitionDiscipline): CompetitionHeadline? = when (discipline.form) {
        CompetitionResultForm.LOAD -> CompetitionAttempts.total(record, discipline)?.let { total ->
            val pts = points(record, discipline)
            CompetitionHeadline(CompetitionFormat.kg(total), pts?.let { "${CompetitionFormat.points(it.second)} ${it.first}" } ?: "kg")
        }
        CompetitionResultForm.CLOCK -> when (discipline) {
            CompetitionDiscipline.JUMP -> record.markMeters?.let { CompetitionHeadline("${CompetitionFormat.kg(it)} m", null) }
            CompetitionDiscipline.SPRINT -> record.finishTimeSeconds?.let {
                CompetitionHeadline(String.format(java.util.Locale.ROOT, "%.2f", it).replace('.', ',') + " s", record.distanceKm?.let { km -> "${CompetitionFormat.kg(km * 1000)} m" })
            }
            else -> record.finishTimeSeconds?.let { time ->
                CompetitionHeadline(CompetitionFormat.duration(time), paceSecondsPerKm(record)?.let { "${CompetitionFormat.pace(it)} /km" })
            }
        }
        CompetitionResultForm.COMBAT -> record.bouts.takeIf { it.isNotEmpty() }?.let {
            CompetitionHeadline("${wins(record)}–${losses(record)}", if (it.size == 1) "1 combate" else "${it.size} combates")
        }
        CompetitionResultForm.MATCH -> if (discipline == CompetitionDiscipline.CLIMBING) {
            record.climbing?.takeIf { it.topsTotal > 0 }?.let { CompetitionHeadline("${it.topsDone} / ${it.topsTotal}", "tops") }
        } else record.bouts.takeIf { it.isNotEmpty() }?.let {
            CompetitionHeadline("${wins(record)}–${losses(record)}", if (it.size == 1) "1 partido" else "${it.size} partidos")
        }
        CompetitionResultForm.STAGE -> CompetitionPlaceHonors.parsePlace(record.placement)?.let {
            CompetitionHeadline(CompetitionFormat.ordinal(it), record.fieldSize?.let { n -> "de $n" })
        }
        CompetitionResultForm.MARKS -> record.finishTimeSeconds?.let { CompetitionHeadline(CompetitionFormat.duration(it), "${record.customMetrics.size} pruebas") }
            ?: record.customMetrics.count { it.value.isNotBlank() }.takeIf { it > 0 }?.let { CompetitionHeadline("$it", "pruebas") }
    }

    /** Récord («RÉCORD» en carga, «MP» contra el reloj): supera a todas las anteriores de la misma disciplina. */
    fun badge(records: List<CompetitionRecord>, record: CompetitionRecord, discipline: (CompetitionRecord) -> CompetitionDiscipline): String? {
        if (record.status != CompetitionRecordStatus.COMPLETED) return null
        val own = discipline(record)
        val previous = previousOf(records, record, discipline)
        if (previous.isEmpty()) return null
        return when (own.form) {
            CompetitionResultForm.LOAD -> {
                val total = CompetitionAttempts.total(record, own) ?: return null
                val best = previous.mapNotNull { CompetitionAttempts.total(it, own) }.maxOrNull() ?: return null
                "RÉCORD".takeIf { total > best }
            }
            CompetitionResultForm.CLOCK -> {
                if (own == CompetitionDiscipline.JUMP) {
                    val mark = record.markMeters ?: return null
                    val best = previous.mapNotNull { it.markMeters }.maxOrNull() ?: return null
                    "MP".takeIf { mark > best }
                } else {
                    val time = record.finishTimeSeconds ?: return null
                    val best = previous.mapNotNull { it.finishTimeSeconds }.minOrNull() ?: return null
                    "MP".takeIf { time < best }
                }
            }
            else -> null
        }
    }

    /** La línea de la tarjeta Próxima: «16 nov 2026 · Madrid · AEP · -83 kg». */
    fun nextMeta(record: CompetitionRecord): String {
        val fed = PowerliftingFederationCatalog.byId(record.federationId)?.shortName ?: record.federation?.takeIf { it.isNotBlank() }
        val weightClass = CompetitionFormat.weightClass(record.powerliftingDetails?.weightClass ?: record.targetWeightClass)?.let { "$it kg" }
        return listOfNotNull(
            CompetitionFormat.date(dateOf(record)),
            record.location?.takeIf { it.isNotBlank() },
            fed,
            weightClass ?: record.category?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    /** «fecha · ciudad» de la fila; contra el reloj, «fecha · distancia». */
    fun rowSubtitle(record: CompetitionRecord, discipline: CompetitionDiscipline): String {
        val second = if (discipline.form == CompetitionResultForm.CLOCK && record.distanceKm != null && discipline != CompetitionDiscipline.SPRINT && discipline != CompetitionDiscipline.JUMP) {
            CompetitionFormat.km(record.distanceKm)
        } else record.location?.takeIf { it.isNotBlank() }
        return listOfNotNull(CompetitionFormat.date(dateOf(record)), second).joinToString(" · ")
    }

    fun row(records: List<CompetitionRecord>, record: CompetitionRecord, discipline: (CompetitionRecord) -> CompetitionDiscipline): CompetitionHubRow {
        val own = discipline(record)
        val medal = CompetitionPlaceHonors.medalOf(record)
        return CompetitionHubRow(
            id = record.id,
            title = record.title.ifBlank { own.label },
            subtitle = rowSubtitle(record, own),
            headline = headline(record, own),
            medal = medal,
            icon = if (own.form == CompetitionResultForm.LOAD) "trophy" else own.icon,
            badge = badge(records, record, discipline),
            discipline = own,
            completed = record.status == CompetitionRecordStatus.COMPLETED,
        )
    }

    /** La página «Competencias» (c-hub). */
    fun hub(records: List<CompetitionRecord>, today: LocalDate, discipline: (CompetitionRecord) -> CompetitionDiscipline): CompetitionHubModel {
        val shown = visible(records)
        val next = next(shown, today)
        val history = shown.filter { it.id != next?.id }.sortedWith(compareByDescending<CompetitionRecord> { dateOf(it) ?: LocalDate.MIN }.thenByDescending { it.updatedAtMs })
        val disciplines = history.map(discipline).toSet().size
        val count = if (history.isEmpty()) "" else "${history.size} · $disciplines ${if (disciplines == 1) "disciplina" else "disciplinas"}"
        val chart = history.filter { it.status == CompetitionRecordStatus.COMPLETED && discipline(it) == CompetitionDiscipline.POWERLIFTING }
            .mapNotNull { r -> val d = dateOf(r) ?: return@mapNotNull null; CompetitionAttempts.total(r, CompetitionDiscipline.POWERLIFTING)?.let { CompetitionChartPoint(d, it) } }
            .sortedBy { it.date }
        val nextCard = next?.let { record ->
            val own = discipline(record)
            CompetitionNextCard(
                id = record.id,
                title = record.title.ifBlank { own.label },
                meta = nextMeta(record),
                daysUntil = daysUntil(record, today),
                openers = if (own.form == CompetitionResultForm.LOAD) CompetitionDraft.loadSeeds(own).mapNotNull { (movement, title) ->
                    record.plannedAttempts.firstOrNull { it.movementType == movement && it.attemptNumber == 1 }?.let { title.uppercase() to CompetitionFormat.kg(it.weightKg) }
                } else emptyList(),
                discipline = own,
            )
        }
        return CompetitionHubModel(
            next = nextCard,
            history = history.map { row(records, it, discipline) },
            historyCount = count,
            chart = chart.takeIf { it.size >= 2 }.orEmpty(),
            chartCaption = chartCaption(chart, history, discipline),
            isEmpty = shown.isEmpty(),
        )
    }

    /** «+47,5 kg en un año · mejor IPF GL 79,4»: lo que subió el total en el último año y los mejores puntos. */
    fun chartCaption(chart: List<CompetitionChartPoint>, history: List<CompetitionRecord>, discipline: (CompetitionRecord) -> CompetitionDiscipline): String? {
        if (chart.size < 2) return null
        val last = chart.last()
        val base = chart.first { !it.date.isBefore(last.date.minusDays(365)) }
        val delta = if (base === last) null else {
            val days = ChronoUnit.DAYS.between(base.date, last.date)
            val span = if (days >= 330) "en un año" else "desde ${CompetitionFormat.monthName(base.date)}"
            "${CompetitionFormat.signedKg(last.totalKg - base.totalKg)} kg $span"
        }
        val best = history.filter { it.status == CompetitionRecordStatus.COMPLETED && discipline(it) == CompetitionDiscipline.POWERLIFTING }
            .mapNotNull { r -> CompetitionScoring.displayedPoints(r) }.maxByOrNull { it.value }
            ?.let { "mejor ${it.label} ${CompetitionFormat.points(it.value)}" }
        return listOfNotNull(delta, best).joinToString(" · ").ifBlank { null }
    }

    /** «Frente a la anterior» (c-detail) en carga: cada fila y el total contra la competencia anterior de la misma disciplina. */
    fun versusPrevious(records: List<CompetitionRecord>, record: CompetitionRecord, discipline: (CompetitionRecord) -> CompetitionDiscipline): List<CompetitionCompareRow> {
        val own = discipline(record)
        val previous = previousOf(records, record, discipline).firstOrNull() ?: return emptyList()
        return when (own.form) {
            CompetitionResultForm.LOAD -> {
                val prevRows = CompetitionAttempts.rows(previous, own)
                val rows = CompetitionAttempts.rows(record, own).mapIndexedNotNull { index, block ->
                    val best = CompetitionAttempts.best(block) ?: return@mapIndexedNotNull null
                    val before = prevRows.getOrNull(index)?.let(CompetitionAttempts::best) ?: return@mapIndexedNotNull null
                    val tried = CompetitionAttempts.heaviestTried(block)
                    val value = if (tried != null && tried > best) "${CompetitionFormat.kg(tried)} → ${CompetitionFormat.kg(best)}" else CompetitionFormat.kg(best)
                    CompetitionCompareRow(block.movementType.liftLabel(block.title), value, CompetitionFormat.signedKg(best - before), sign(best - before))
                }
                val total = CompetitionAttempts.total(record, own)
                val prevTotal = CompetitionAttempts.total(previous, own)
                rows + listOfNotNull(if (total != null && prevTotal != null)
                    CompetitionCompareRow("Total", CompetitionFormat.kg(total), CompetitionFormat.signedKg(total - prevTotal), sign(total - prevTotal)) else null)
            }
            CompetitionResultForm.CLOCK -> {
                val time = record.finishTimeSeconds ?: return emptyList()
                val before = previous.finishTimeSeconds ?: return emptyList()
                listOf(CompetitionCompareRow("Tiempo", CompetitionFormat.duration(time), CompetitionFormat.signedDuration(time - before), sign(before - time)))
            }
            else -> emptyList()
        }
    }

    private fun sign(delta: Double): Int = when { delta > 0.005 -> 1; delta < -0.005 -> -1; else -> 0 }

    /** Las seis filas de c-compare para dos competencias de carga, o las de reloj. */
    fun compare(a: CompetitionRecord, b: CompetitionRecord, discipline: (CompetitionRecord) -> CompetitionDiscipline): List<CompetitionCompareRow> {
        val own = discipline(a)
        return when (own.form) {
            CompetitionResultForm.CLOCK -> listOf(
                CompetitionCompareRow("Tiempo", CompetitionFormat.duration(a.finishTimeSeconds), null, 0, CompetitionFormat.duration(b.finishTimeSeconds)),
                CompetitionCompareRow("Ritmo", CompetitionFormat.pace(paceSecondsPerKm(a)), null, 0, CompetitionFormat.pace(paceSecondsPerKm(b))),
                CompetitionCompareRow("Distancia", CompetitionFormat.km(a.distanceKm), null, 0, CompetitionFormat.km(b.distanceKm)),
            )
            else -> {
                val rowsA = CompetitionAttempts.rows(a, own)
                val rowsB = CompetitionAttempts.rows(b, own)
                val lifts = rowsA.mapIndexed { i, block ->
                    CompetitionCompareRow(block.movementType.liftLabel(block.title), CompetitionFormat.kg(CompetitionAttempts.best(block)), null, 0,
                        CompetitionFormat.kg(rowsB.getOrNull(i)?.let(CompetitionAttempts::best)))
                }
                lifts + listOfNotNull(
                    CompetitionCompareRow("Total", CompetitionFormat.kg(CompetitionAttempts.total(a, own)), null, 0, CompetitionFormat.kg(CompetitionAttempts.total(b, own))),
                    pointsLabel(a, own)?.let { label ->
                        CompetitionCompareRow(label, CompetitionFormat.points(points(a, own)?.second), null, 0, CompetitionFormat.points(points(b, own)?.second))
                    },
                    CompetitionCompareRow("Pesaje", CompetitionFormat.kg(a.bodyweightKg), null, 0, CompetitionFormat.kg(b.bodyweightKg)),
                )
            }
        }
    }

    /**
     * Las frases de la celebración (c-cele), como mucho dos: mejor total, primera vez que pasas una cifra redonda, récord de un
     * levantamiento, puntos o marca personal. Sin podio también hay frases (el foco pasa a tus marcas).
     */
    fun celebrationLines(records: List<CompetitionRecord>, record: CompetitionRecord, discipline: (CompetitionRecord) -> CompetitionDiscipline): List<String> {
        val own = discipline(record)
        val previous = previousOf(records, record, discipline)
        val lines = mutableListOf<String>()
        when (own.form) {
            CompetitionResultForm.LOAD -> {
                val total = CompetitionAttempts.total(record, own)
                val bestBefore = previous.mapNotNull { r -> CompetitionAttempts.total(r, own)?.let { r to it } }.maxByOrNull { it.second }
                if (previous.isEmpty()) lines += "Tu primera competencia registrada."
                if (total != null && bestBefore != null && total > bestBefore.second) {
                    val since = dateOf(bestBefore.first)?.let { " desde ${CompetitionFormat.monthName(it)}" }.orEmpty()
                    lines += "Tu mejor total: ${CompetitionFormat.signedKg(total - bestBefore.second)} kg$since."
                }
                val rows = CompetitionAttempts.rows(record, own)
                rows.forEachIndexed { index, block ->
                    if (lines.size >= 2) return@forEachIndexed
                    val best = CompetitionAttempts.best(block) ?: return@forEachIndexed
                    val before = previous.mapNotNull { CompetitionAttempts.rows(it, own).getOrNull(index)?.let(CompetitionAttempts::best) }.maxOrNull()
                    if (before == null || best <= before) return@forEachIndexed
                    val round = floor(best / 50.0) * 50.0
                    lines += if (round > before && round > 0) "${block.movementType.liftLabel(block.title)} de ${CompetitionFormat.kg(round)} kg por primera vez."
                    else "Récord en ${block.movementType.liftLabel(block.title).lowercase()}: ${CompetitionFormat.kg(best)} kg."
                }
                if (lines.size < 2) points(record, own)?.let { (label, value) ->
                    val bestPts = previous.mapNotNull { points(it, own)?.second }.maxOrNull()
                    if (bestPts == null || value > bestPts) lines += "Tus mejores puntos: $label ${CompetitionFormat.points(value)}."
                }
            }
            CompetitionResultForm.CLOCK -> {
                val time = record.finishTimeSeconds
                val best = previous.mapNotNull { it.finishTimeSeconds }.minOrNull()
                if (time != null && best != null && time < best) lines += "Marca personal en ${CompetitionFormat.km(record.distanceKm)}: ${CompetitionFormat.signedDuration(time - best)}."
                paceSecondsPerKm(record)?.let { lines += "Ritmo medio: ${CompetitionFormat.pace(it)} /km." }
                if (previous.isEmpty() && lines.size < 2) lines += "Tu primera competencia registrada."
            }
            CompetitionResultForm.COMBAT, CompetitionResultForm.MATCH -> {
                if (record.bouts.isNotEmpty()) lines += "Balance de ${wins(record)}–${losses(record)}."
                if (previous.isEmpty()) lines += "Tu primera competencia registrada."
            }
            CompetitionResultForm.STAGE, CompetitionResultForm.MARKS -> {
                headline(record, own)?.let { lines += listOfNotNull(it.value, it.caption).joinToString(" ") + "." }
                if (previous.isEmpty()) lines += "Tu primera competencia registrada."
            }
        }
        return lines.take(2)
    }

    // ─── Contra el reloj (c-run-next, c-run) ─────────────────────────────────────────────────────────────────────

    /** Nombre de una distancia en las frases («tu media maratón», «tu maratón», «tu 10 km»). */
    fun distanceName(km: Double): String = when {
        abs(km - 42.195) < 0.3 -> "maratón"
        abs(km - 21.0975) < 0.2 -> "media maratón"
        else -> CompetitionFormat.km(km)
    }

    /** Predicción de Riegel con exponentes 1,06–1,10: el rango de tiempo para [targetKm] desde una marca en [refKm]. */
    fun predictRange(refKm: Double, refSeconds: Double, targetKm: Double): Pair<Double, Double>? {
        if (refKm <= 0 || refSeconds <= 0 || targetKm <= 0) return null
        val ratio = targetKm / refKm
        return refSeconds * ratio.pow(1.06) to refSeconds * ratio.pow(1.10)
    }

    /** «Ritmo 4:57 /km · tu media maratón (1:38:12) predice 3:24–3:31». */
    fun targetCaption(records: List<CompetitionRecord>, record: CompetitionRecord, discipline: (CompetitionRecord) -> CompetitionDiscipline): String? {
        val km = record.distanceKm?.takeIf { it > 0 } ?: return null
        val pace = record.targetTimeSeconds?.let { "Ritmo ${CompetitionFormat.pace(it / km)} /km" }
        val own = discipline(record)
        val reference = visible(records).filter { it.id != record.id && it.status == CompetitionRecordStatus.COMPLETED && discipline(it) == own &&
            (it.distanceKm ?: 0.0) > 0 && (it.finishTimeSeconds ?: 0.0) > 0 }
            .minByOrNull { abs(kotlin.math.ln(it.distanceKm!! / km)) * 1000 - (dateOf(it)?.toEpochDay() ?: 0) / 1_000_000.0 }
        val prediction = reference?.let { ref ->
            predictRange(ref.distanceKm!!, ref.finishTimeSeconds!!, km)?.let { (lo, hi) ->
                "tu ${distanceName(ref.distanceKm)} (${CompetitionFormat.duration(ref.finishTimeSeconds)}) predice ${hoursMinutes(lo, false)}–${hoursMinutes(hi, true)}"
            }
        }
        return listOfNotNull(pace, prediction).joinToString(" · ").ifBlank { null }
    }

    private fun hoursMinutes(seconds: Double, up: Boolean): String {
        val minutes = if (up) ceil(seconds / 60).toLong() else floor(seconds / 60).toLong()
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}" else "$m min"
    }

    /** Un tramo del plan de ritmos. */
    data class PaceSegment(val label: String, val pace: String, val note: String)

    /** «Plan de ritmos» por tramos (c-run-next): empieza 5 s más lento, ritmo objetivo y cierra 5 s más rápido. */
    fun paceSegments(record: CompetitionRecord): List<PaceSegment> {
        val km = record.distanceKm?.takeIf { it >= 5 } ?: return emptyList()
        val target = record.targetTimeSeconds?.takeIf { it > 0 } ?: return emptyList()
        val pace = target / km
        val b1 = (km * 10 / 42.195).roundToInt().coerceAtLeast(1)
        val b2 = (km * 30 / 42.195).roundToInt().coerceAtLeast(b1 + 1)
        fun k(v: Double) = CompetitionFormat.km(v).removeSuffix(" km")
        return listOf(
            PaceSegment("0–$b1 km", "${CompetitionFormat.pace(pace + 5)} /km", "Empieza contenido"),
            PaceSegment("$b1–$b2 km", "${CompetitionFormat.pace(pace - 1)} /km", "Ritmo objetivo"),
            PaceSegment("$b2–${k(km)} km", "${CompetitionFormat.pace(pace - 5)} /km", "Si vas bien"),
        )
    }

    /** Diferencia con la marca personal anterior en la misma distancia; null si no hay anterior. */
    fun personalBestDelta(records: List<CompetitionRecord>, record: CompetitionRecord, discipline: (CompetitionRecord) -> CompetitionDiscipline): Double? {
        val time = record.finishTimeSeconds ?: return null
        val best = previousOf(records, record, discipline).mapNotNull { it.finishTimeSeconds }.minOrNull() ?: return null
        return time - best
    }

    /** «Último km el más rápido: 4:21» o «Km más rápido: el 7, 4:21». */
    fun fastestSplitCaption(splits: List<Double>): String? {
        if (splits.isEmpty()) return null
        val min = splits.min()
        val index = splits.indexOf(min)
        return if (index == splits.lastIndex) "Último km el más rápido: ${CompetitionFormat.pace(min)}"
        else "Km más rápido: el ${index + 1}, ${CompetitionFormat.pace(min)}"
    }

    // ─── Pesaje (c-plan) y tarima (c-bb) ──────────────────────────────────────────────────────────────────────────

    /** «Con tu tendencia de las últimas semanas llegas con 0,6 kg de margen.» a partir de la proyección de [WeighInPlanner]. */
    fun weighInCaption(forecast: WeighInForecast): String = when {
        !forecast.projectionAvailable -> "Sin tendencia suficiente para proyectar el pesaje."
        forecast.makesWeight -> "Con tu tendencia de las últimas semanas llegas con ${CompetitionFormat.kg(((forecast.targetKg - forecast.projectedKg) * 10).roundToInt() / 10.0)} kg de margen."
        else -> "Con tu tendencia de las últimas semanas llegarías ${CompetitionFormat.kg(((forecast.projectedKg - forecast.targetKg) * 10).roundToInt() / 10.0)} kg por encima."
    }

    /** Semanas que faltan (redondeo hacia arriba) hasta la tarima. */
    fun weeksUntil(record: CompetitionRecord, today: LocalDate): Long? = daysUntil(record, today)?.takeIf { it >= 0 }?.let { (it + 6) / 7 }

    /** Ritmo necesario hasta el peso de tarima en kg por semana (negativo = bajar). */
    fun stageRate(currentKg: Double?, stageKg: Double?, weeks: Long?): Double? {
        if (currentKg == null || stageKg == null || weeks == null || weeks <= 0) return null
        return (stageKg - currentKg) / weeks
    }

    /** Los primeros intentos del plan, por movimiento (para las aperturas). */
    fun openers(record: CompetitionRecord): Map<CompetitionMovementType, Double> =
        record.plannedAttempts.filter { it.attemptNumber == 1 }.associate { it.movementType to it.weightKg }
}
