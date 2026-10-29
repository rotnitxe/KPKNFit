package com.example.kpkn.screens.stories

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionMediaKind
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import com.example.kpkn.domain.competitions.CompetitionPlaceHonors
import com.example.kpkn.domain.competitions.CompetitionScoring
import com.example.kpkn.screens.onboarding.design.WIZARD_MINUS
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

// ----------------------------------------------------------------------------------------------------------- modelo

/**
 * Plantillas de la historia de competencia (escenas h-meet-* y, por disciplina, h-race, h-stage y h-combat del lienzo Registros v4).
 * Las de carga usan Total, Intentos y Foto; la carrera, Tiempo; el culturismo, Tarima (siempre con foto); el combate, Cuadro.
 */
internal enum class CompetitionStoryTemplate(val label: String) {
    TOTAL("Total"),
    INTENTOS("Intentos"),
    TIEMPO("Tiempo"),
    TARIMA("Tarima"),
    CUADRO("Cuadro"),
    FOTO("Foto"),
    PROXIMA("Próxima"),
}

/** Lo que el usuario incluye en la historia («Incluir»): la medalla, los puntos y el peso corporal (apagado por defecto). */
internal data class CompetitionStoryOptions(val medal: Boolean = true, val points: Boolean = true, val bodyweight: Boolean = false)

/** Una historia de competencia ya en texto: lo que pinta una plantilla, sin nada inventado. */
internal data class CompetitionStoryPage(
    val eyebrow: String,
    val title: String,
    val sub: String?,
    val foot: String,
    val rows: List<StoryPair> = emptyList(),
    val badge: String? = null,
    val score: List<StoryAttemptRow> = emptyList(),
    val inline: List<StoryInlineItem> = emptyList(),
    val medal: StoryMedal? = null,
)

/** Qué hay en la competencia para compartir: las plantillas que se pueden ofrecer, las fotos y lo que se puede incluir. */
internal data class CompetitionStoryAvailability(
    val templates: List<CompetitionStoryTemplate>,
    val photos: List<String>,
    val hasMedal: Boolean,
    val hasPoints: Boolean,
    val hasBodyweight: Boolean,
)

/** Pie de las historias de resultado y de la próxima. */
private const val FOOT_MEET = "Mi competencia"
private const val FOOT_NEXT = "Mi próxima competencia"
private const val FOOT_RACE = "Mi carrera"

private val liftingSports = setOf(
    CompetitionTemplateType.POWERLIFTING,
    CompetitionTemplateType.WEIGHTLIFTING,
    CompetitionTemplateType.STRONGMAN,
    CompetitionTemplateType.CROSSFIT,
    CompetitionTemplateType.CUSTOM,
)

private val monthsEs = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

private fun CompetitionRecord.isDone(): Boolean = status != CompetitionRecordStatus.PLANNED

private fun CompetitionRecord.date(): LocalDate? = eventDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

/** «21 sep». */
internal fun storyDayMonth(date: LocalDate): String = "${date.dayOfMonth} ${monthsEs[date.monthValue - 1]}"

/** Kilos con coma y sin «,0»: 187.5 → «187,5», 200 → «200». */
internal fun storyKg(kg: Double): String {
    val tenths = (abs(kg) * 10).roundToLong()
    val whole = tenths / 10
    val tenth = tenths % 10
    val sign = if (kg < 0 && tenths > 0) "-" else ""
    return if (tenth == 0L) "$sign${thousands(whole)}" else "$sign${thousands(whole)},$tenth"
}

/** Entero con puntos de miles: 1204 → «1.204». */
internal fun thousands(value: Long): String = abs(value).toString().reversed().chunked(3).joinToString(".").reversed().let {
    if (value < 0) "-$it" else it
}

/** Una cifra con un decimal y coma: 79.43 → «79,4». */
private fun oneDecimal(value: Double): String {
    val tenths = (abs(value) * 10).roundToLong()
    return "${tenths / 10},${tenths % 10}"
}

/** Clase de peso del formulario («83», «120+», «-83 kg») como en el lienzo: «-83 kg», «120+ kg». */
internal fun storyWeightClass(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    if (text.contains("kg", ignoreCase = true)) return text
    if (text.endsWith("+") || text.startsWith("-") || text.startsWith("+")) return "$text kg"
    return if (text.first().isDigit()) "-$text kg" else text
}

/** Tiempo en segundos como «1:38:12» o «4:21». */
internal fun storyClock(seconds: Double): String {
    val total = seconds.roundToLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

private fun medalOf(record: CompetitionRecord): StoryMedal? {
    when (record.medal?.lowercase()) {
        "gold", "oro" -> return StoryMedal.ORO
        "silver", "plata" -> return StoryMedal.PLATA
        "bronze", "bronce" -> return StoryMedal.BRONCE
    }
    return when (CompetitionPlaceHonors.parsePlace(record.placement)) {
        1 -> StoryMedal.ORO
        2 -> StoryMedal.PLATA
        3 -> StoryMedal.BRONCE
        else -> null
    }
}

/** El puesto exacto («2.º puesto»), solo si está guardado como número. */
private fun placeText(record: CompetitionRecord): String? =
    record.placement?.trim()?.toIntOrNull()?.takeIf { it >= 1 }?.let { "${thousands(it.toLong())}.º puesto" }

private fun liftName(block: CompetitionTechnicalBlock): String = when (block.movementType) {
    CompetitionMovementType.SQUAT -> "Sentadilla"
    CompetitionMovementType.BENCH -> "Press banca"
    CompetitionMovementType.DEADLIFT -> "Peso muerto"
    CompetitionMovementType.SNATCH -> "Arrancada"
    CompetitionMovementType.CLEAN_AND_JERK -> "Dos tiempos"
    else -> block.exerciseName?.takeIf { it.isNotBlank() } ?: block.title
}

private fun liftShort(block: CompetitionTechnicalBlock): String = when (block.movementType) {
    CompetitionMovementType.SQUAT -> "Sent."
    CompetitionMovementType.BENCH -> "Banca"
    CompetitionMovementType.DEADLIFT -> "P. muerto"
    CompetitionMovementType.SNATCH -> "Arranc."
    CompetitionMovementType.CLEAN_AND_JERK -> "Dos t."
    else -> block.exerciseName?.takeIf { it.isNotBlank() } ?: block.title
}

private fun liftName(type: CompetitionMovementType): String = when (type) {
    CompetitionMovementType.SQUAT -> "Sentadilla"
    CompetitionMovementType.BENCH -> "Press banca"
    CompetitionMovementType.DEADLIFT -> "Peso muerto"
    CompetitionMovementType.SNATCH -> "Arrancada"
    CompetitionMovementType.CLEAN_AND_JERK -> "Dos tiempos"
    CompetitionMovementType.PRESS -> "Press"
    else -> "Levantamiento"
}

/** Total del día: el de powerlifting (recalculado) o la suma de los mejores válidos de cada bloque con carga. */
internal fun competitionTotalKg(record: CompetitionRecord): Double? {
    val scored = CompetitionScoring.recalculate(record)
    if (scored.sportType == CompetitionTemplateType.POWERLIFTING) {
        return scored.powerliftingDetails?.totalKg ?: CompetitionScoring.totalKg(scored.technicalBlocks)
    }
    val bests = scored.technicalBlocks.mapNotNull { CompetitionScoring.bestValidKg(it) }
    return bests.takeIf { it.isNotEmpty() }?.sum()
}

private fun judged(record: CompetitionRecord) = record.technicalBlocks.flatMap { it.attempts }.filter {
    it.resultType == CompetitionAttemptResult.GOOD_LIFT || it.resultType == CompetitionAttemptResult.NO_LIFT
}

private fun photosOf(record: CompetitionRecord): List<String> =
    record.photos.filter { it.kind == CompetitionMediaKind.PHOTO && it.uri.isNotBlank() }.map { it.uri }

/** La próxima competencia: esta si está planificada, o la primera planificada de [history] desde [today]. */
internal fun nextCompetition(record: CompetitionRecord, history: List<CompetitionRecord>, today: LocalDate): CompetitionRecord? {
    if (!record.isDone()) return record.takeIf { (it.date() ?: return null) >= today }
    return history
        .filter { !it.isDone() && it.id != record.id }
        .mapNotNull { other -> other.date()?.takeIf { it >= today }?.let { other to it } }
        .minByOrNull { it.second }
        ?.first
}

/** El mejor tiempo del día en una carrera y su distancia. */
private fun raceBest(record: CompetitionRecord): Pair<Double, Double?>? = record.technicalBlocks
    .flatMap { block -> block.attempts.map { it to block } }
    .filter { (attempt, _) -> attempt.timeSeconds != null && attempt.resultType != CompetitionAttemptResult.NO_LIFT }
    .minByOrNull { it.first.timeSeconds ?: Double.MAX_VALUE }
    ?.let { (attempt, _) -> (attempt.timeSeconds ?: 0.0) to attempt.distance }

/** Lo que hay para compartir en [record] ([history] da la próxima competencia y las comparaciones). */
internal fun competitionStoryAvailability(
    record: CompetitionRecord,
    history: List<CompetitionRecord>,
    today: LocalDate,
): CompetitionStoryAvailability {
    val photos = photosOf(record)
    val templates = buildList {
        if (record.isDone()) {
            when (record.sportType) {
                CompetitionTemplateType.RUNNING -> if (raceBest(record) != null || !record.resultSummary.isNullOrBlank()) add(CompetitionStoryTemplate.TIEMPO)
                CompetitionTemplateType.BODYBUILDING -> if (photos.isNotEmpty()) add(CompetitionStoryTemplate.TARIMA)
                CompetitionTemplateType.MARTIAL_ARTS -> if (record.technicalBlocks.isNotEmpty() || medalOf(record) != null || placeText(record) != null) {
                    add(CompetitionStoryTemplate.CUADRO)
                }
                else -> {
                    if (competitionTotalKg(record) != null) add(CompetitionStoryTemplate.TOTAL)
                    if (judged(record).isNotEmpty()) add(CompetitionStoryTemplate.INTENTOS)
                }
            }
            if (photos.isNotEmpty() && record.sportType != CompetitionTemplateType.BODYBUILDING) add(CompetitionStoryTemplate.FOTO)
        }
        if (nextCompetition(record, history, today) != null) add(CompetitionStoryTemplate.PROXIMA)
    }
    val bodyweight = record.bodybuildingDetails?.stageWeightKg ?: record.bodyweightKg
    return CompetitionStoryAvailability(
        templates = templates,
        photos = photos,
        hasMedal = medalOf(record) != null,
        hasPoints = record.sportType == CompetitionTemplateType.POWERLIFTING && CompetitionScoring.displayedPoints(CompetitionScoring.recalculate(record)) != null,
        hasBodyweight = bodyweight != null && bodyweight > 0.0,
    )
}

/** La página de la plantilla [template] para [record], con las [options] de «Incluir». */
internal fun competitionStoryPage(
    template: CompetitionStoryTemplate,
    record: CompetitionRecord,
    history: List<CompetitionRecord>,
    today: LocalDate,
    options: CompetitionStoryOptions = CompetitionStoryOptions(),
): CompetitionStoryPage {
    val scored = CompetitionScoring.recalculate(record)
    val medal = medalOf(record)
    val shownMedal = medal?.takeIf { options.medal }
    val date = record.date()
    val total = competitionTotalKg(record)
    val points = CompetitionScoring.displayedPoints(scored)?.takeIf { options.points && record.sportType == CompetitionTemplateType.POWERLIFTING }
    val bodyweight = (record.bodybuildingDetails?.stageWeightKg ?: record.bodyweightKg)?.takeIf { options.bodyweight && it > 0.0 }
    val weightClass = storyWeightClass(record.powerliftingDetails?.weightClass ?: record.targetWeightClass)
    return when (template) {
        CompetitionStoryTemplate.TOTAL -> {
            val category = listOfNotNull(weightClass, record.powerliftingDetails?.division?.takeIf { it.isNotBlank() })
                .joinToString(" ").ifBlank { record.category?.takeIf { it.isNotBlank() } }
            CompetitionStoryPage(
                eyebrow = listOfNotNull(record.title, date?.let(::storyDayMonth)).joinToString(" · "),
                title = total?.let { "${storyKg(it)} kg" } ?: record.resultSummary.orEmpty(),
                sub = listOfNotNull(
                    shownMedal?.label,
                    category,
                    points?.let { "${oneDecimal(it.value)} ${it.label}" },
                    bodyweight?.let { "${storyKg(it)} kg corporal" },
                ).joinToString(" · ").ifBlank { null },
                foot = FOOT_MEET,
                rows = scored.technicalBlocks.mapNotNull { block -> CompetitionScoring.bestValidKg(block)?.let { StoryPair(liftName(block), storyKg(it)) } },
                badge = bestTotalBadge(record, total, history),
                medal = shownMedal,
            )
        }
        CompetitionStoryTemplate.INTENTOS -> {
            val all = judged(record)
            val good = all.count { it.resultType == CompetitionAttemptResult.GOOD_LIFT }
            CompetitionStoryPage(
                eyebrow = "${record.title} · intentos",
                title = "$good de ${all.size}",
                sub = listOfNotNull("Válidos", total?.let { "total ${storyKg(it)} kg" }, bodyweight?.let { "${storyKg(it)} kg corporal" })
                    .joinToString(" · "),
                foot = FOOT_MEET,
                score = scored.technicalBlocks.mapNotNull { block ->
                    val cells = block.attempts.sortedBy { it.attemptNumber }.mapNotNull { attempt ->
                        attempt.weightKg?.let { kg ->
                            StoryAttemptCell(
                                storyKg(kg),
                                when (attempt.resultType) {
                                    CompetitionAttemptResult.GOOD_LIFT -> true
                                    CompetitionAttemptResult.NO_LIFT -> false
                                    else -> null
                                },
                            )
                        }
                    }
                    cells.takeIf { it.isNotEmpty() }?.let { StoryAttemptRow(liftShort(block), it) }
                },
            )
        }
        CompetitionStoryTemplate.FOTO -> {
            val all = judged(record)
            val inline = when (record.sportType) {
                CompetitionTemplateType.RUNNING -> listOfNotNull(raceBest(record)?.let { StoryInlineItem(storyClock(it.first), null) })
                else -> listOfNotNull(
                    total?.let { StoryInlineItem(storyKg(it), "kg") },
                    points?.let { StoryInlineItem(oneDecimal(it.value), "pts") },
                    all.takeIf { it.isNotEmpty() }?.let { list -> StoryInlineItem("${list.count { it.resultType == CompetitionAttemptResult.GOOD_LIFT }}/${list.size}", null) },
                )
            } + listOfNotNull(bodyweight?.let { StoryInlineItem(storyKg(it), "kg corporal") })
            CompetitionStoryPage(
                eyebrow = record.title,
                title = medal?.label ?: placeText(record) ?: total?.let { "${storyKg(it)} kg" } ?: record.resultSummary.orEmpty(),
                sub = null,
                foot = FOOT_MEET,
                inline = inline,
                medal = shownMedal,
            )
        }
        CompetitionStoryTemplate.TIEMPO -> raceStoryPage(record, history, options)
        CompetitionStoryTemplate.TARIMA -> CompetitionStoryPage(
            eyebrow = listOfNotNull(record.title, (record.bodybuildingDetails?.division ?: record.category)?.takeIf { it.isNotBlank() }).joinToString(" · "),
            title = placeText(record) ?: medal?.label ?: record.resultSummary.orEmpty(),
            sub = null,
            foot = FOOT_MEET,
            inline = record.customMetrics
                .filter { it.value.trim().replace(',', '.').toDoubleOrNull() != null }
                .take(2)
                .map { StoryInlineItem(it.value.trim(), listOfNotNull(it.unit, it.label).joinToString(" ").lowercase()) } +
                listOfNotNull(bodyweight?.let { StoryInlineItem(storyKg(it), "kg") }),
            medal = shownMedal,
        )
        CompetitionStoryTemplate.CUADRO -> {
            val bouts = record.technicalBlocks.map { block ->
                val won = block.attempts.any { it.resultType == CompetitionAttemptResult.GOOD_LIFT }
                val lost = !won && block.attempts.any { it.resultType == CompetitionAttemptResult.NO_LIFT }
                val mark = block.bestValidMark?.takeIf { it.isNotBlank() }
                    ?: block.attempts.firstNotNullOfOrNull { it.mark?.takeIf { m -> m.isNotBlank() } }
                    ?: when {
                        won -> "Victoria"
                        lost -> "Derrota"
                        else -> "—"
                    }
                Triple(block.title, mark, if (won) 1 else if (lost) -1 else 0)
            }
            val balance = bouts.takeIf { list -> list.any { it.third != 0 } }
                ?.let { list -> "${list.count { it.third > 0 }}–${list.count { it.third < 0 }}" }
            CompetitionStoryPage(
                eyebrow = listOfNotNull(record.title, weightClass).joinToString(" · "),
                title = listOfNotNull(medal?.label ?: placeText(record), balance).joinToString(" · ")
                    .ifBlank { record.resultSummary.orEmpty() },
                sub = record.category?.takeIf { it.isNotBlank() },
                foot = FOOT_MEET,
                rows = bouts.map { StoryPair(it.first, it.second) },
            )
        }
        CompetitionStoryTemplate.PROXIMA -> nextStoryPage(nextCompetition(record, history, today) ?: record, today)
    }
}

/**
 * «Mejor total: +12,5 kg» si [total] supera el mejor total de las competencias anteriores terminadas del mismo deporte. Sin
 * competencia anterior con total (o sin mejora), no hay insignia.
 */
internal fun bestTotalBadge(record: CompetitionRecord, total: Double?, history: List<CompetitionRecord>): String? {
    if (total == null) return null
    val date = record.date() ?: return null
    val previous = history
        .filter { it.id != record.id && it.isDone() && it.sportType == record.sportType && it.status != CompetitionRecordStatus.ARCHIVED }
        .filter { other -> other.date()?.let { it < date } == true }
        .mapNotNull { competitionTotalKg(it) }
        .maxOrNull() ?: return null
    val gain = total - previous
    return if ((gain * 10).roundToLong() > 0) "Mejor total: +${storyKg(gain)} kg" else null
}

/** Tiempo: el tiempo como título, distancia, ritmo y puesto; la marca personal frente a carreras anteriores de la misma distancia. */
private fun raceStoryPage(record: CompetitionRecord, history: List<CompetitionRecord>, options: CompetitionStoryOptions): CompetitionStoryPage {
    val best = raceBest(record)
    val unit = record.technicalBlocks.firstNotNullOfOrNull { it.resultUnit?.takeIf { u -> u.isNotBlank() } } ?: "km"
    val distance = best?.second?.takeIf { it > 0.0 }
    val pace = if (best != null && distance != null && unit.equals("km", ignoreCase = true)) "${storyClock(best.first / distance)} /km" else null
    val place = record.placement?.trim()?.toIntOrNull()?.takeIf { it >= 1 }?.let { "puesto ${thousands(it.toLong())}" }
    val date = record.date()
    val personal = if (best != null && distance != null && date != null) {
        history
            .filter { it.id != record.id && it.isDone() && it.sportType == CompetitionTemplateType.RUNNING }
            .filter { other -> other.date()?.let { it < date } == true }
            .mapNotNull { raceBest(it) }
            .filter { (_, d) -> d != null && abs(d - distance) < 0.05 }
            .minOfOrNull { it.first }
            ?.takeIf { it - best.first >= 1.0 }
            ?.let { StoryPair("Marca personal", WIZARD_MINUS + storyClock(it - best.first)) }
    } else {
        null
    }
    val metrics = record.customMetrics.map { StoryPair(it.label, listOfNotNull(it.value, it.unit).joinToString(" ")) }
    return CompetitionStoryPage(
        eyebrow = record.title,
        title = best?.let { storyClock(it.first) } ?: record.resultSummary.orEmpty(),
        sub = listOfNotNull(
            distance?.let { "${storyKg(it)} $unit" },
            pace,
            place,
            record.bodyweightKg?.takeIf { options.bodyweight && it > 0.0 }?.let { "${storyKg(it)} kg corporal" },
        ).joinToString(" · ").ifBlank { null },
        foot = FOOT_RACE,
        rows = (listOfNotNull(personal) + metrics).take(4),
    )
}

/** Próxima: cuenta atrás, sede, clase y federación, y con qué abre cada levantamiento. */
private fun nextStoryPage(next: CompetitionRecord, today: LocalDate): CompetitionStoryPage {
    val date = next.date()
    val days = date?.let { ChronoUnit.DAYS.between(today, it) }
    val openers = next.plannedAttempts.filter { it.attemptNumber == 1 && it.weightKg > 0.0 }
        .sortedBy { it.movementType.ordinal }
        .map { StoryPair("${liftName(it.movementType)} · abro con", storyKg(it.weightKg)) }
        .ifEmpty {
            next.technicalBlocks.mapNotNull { block ->
                block.attempts.minByOrNull { it.attemptNumber }?.weightKg?.takeIf { it > 0.0 }
                    ?.let { StoryPair("${liftName(block)} · abro con", storyKg(it)) }
            }
        }
    val federation = PowerliftingFederationCatalog.byId(next.federationId)?.shortName ?: next.federation
    return CompetitionStoryPage(
        eyebrow = listOfNotNull(next.title, date?.let(::storyDayMonth)).joinToString(" · "),
        title = storyCountdown(days),
        sub = listOfNotNull(
            next.location?.takeIf { it.isNotBlank() },
            storyWeightClass(next.powerliftingDetails?.weightClass ?: next.targetWeightClass),
            federation?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { null },
        foot = FOOT_NEXT,
        rows = openers,
    )
}

/** «Faltan 18 días», «Falta 1 día», «Es hoy». */
internal fun storyCountdown(days: Long?): String = when {
    days == null -> "Próxima competencia"
    days <= 0L -> "Es hoy"
    days == 1L -> "Falta 1 día"
    else -> "Faltan $days días"
}

// ------------------------------------------------------------------------------------------------------------- dibujo

/**
 * Pinta [page] con la plantilla [template] en el lienzo de 1080 × 1920 (posiciones, portadas y medallas del lienzo). [photo] es la
 * foto elegida (Foto y Tarima); [progress] es el avance del dibujo de las líneas.
 */
internal fun DrawScope.drawCompetitionStory(
    template: CompetitionStoryTemplate,
    page: CompetitionStoryPage,
    measurer: TextMeasurer,
    photo: ImageBitmap? = null,
    progress: Float = 1f,
) {
    when (template) {
        CompetitionStoryTemplate.TOTAL -> {
            drawStoryBackdrop(LineCoverSpec(LineCoverStyle.RELIEVE, LineCoverBackground.CARBON, seed = 6), measurer, progress, artHeight(48f))
            page.medal?.let { drawStoryMedal(it, Offset(540f, topAt(22f)), 0.46f * StoryCanvas.WIDTH, measurer, progress) }
            drawShell(
                topAt(44f),
                end = true,
                blocks = buildList {
                    addAll(measurer.shellHeader(page.eyebrow, page.title, page.sub))
                    measurer.shellRows(page.rows, SHELL_ROWS_GAP_END, auto = false)?.let(::add)
                    page.badge?.let { add(measurer.shellBadge(it, ShellIcon.SPARK)) }
                    add(measurer.shellFoot(page.foot, end = true))
                },
            )
        }
        CompetitionStoryTemplate.INTENTOS -> {
            drawStoryBackdrop(LineCoverSpec(LineCoverStyle.ONDAS, LineCoverBackground.CARBON, seed = 3), measurer, progress, artHeight(55f))
            drawShell(
                topAt(40f),
                end = true,
                blocks = buildList {
                    addAll(measurer.shellHeader(page.eyebrow, page.title, page.sub))
                    measurer.shellScore(page.score)?.let(::add)
                    add(measurer.shellFoot(page.foot, end = true))
                },
            )
        }
        CompetitionStoryTemplate.FOTO, CompetitionStoryTemplate.TARIMA -> {
            val stage = template == CompetitionStoryTemplate.TARIMA
            drawStoryPlain()
            photo?.let { drawStoryPhoto(it) }
            val cover = if (stage) {
                LineCoverSpec(LineCoverStyle.BRUMA, LineCoverBackground.CARBON, seed = 4)
            } else {
                LineCoverSpec(LineCoverStyle.ORBITAS, LineCoverBackground.CARBON, seed = 4)
            }
            drawCoverOverPhoto(cover, measurer, artHeight(52f), progress)
            page.medal?.let {
                val width = (if (stage) 0.28f else 0.30f) * StoryCanvas.WIDTH
                drawStoryMedal(it, Offset(0.8f * StoryCanvas.WIDTH, topAt(if (stage) 48f else 46f)), width, measurer, progress)
            }
            drawShell(
                topAt(60f),
                end = false,
                blocks = buildList {
                    addAll(measurer.shellHeader(page.eyebrow, page.title, page.sub))
                    measurer.shellInline(page.inline)?.let(::add)
                    add(measurer.shellFoot(page.foot, end = false))
                },
            )
        }
        CompetitionStoryTemplate.PROXIMA, CompetitionStoryTemplate.TIEMPO, CompetitionStoryTemplate.CUADRO -> {
            val (cover, art) = when (template) {
                CompetitionStoryTemplate.PROXIMA -> LineCoverSpec(LineCoverStyle.ORBITAS, LineCoverBackground.NOCHE, seed = 4) to 45f
                CompetitionStoryTemplate.TIEMPO -> LineCoverSpec(LineCoverStyle.ONDAS, LineCoverBackground.NOCHE, seed = 3) to 50f
                else -> LineCoverSpec(LineCoverStyle.CAMPO, LineCoverBackground.CARBON, seed = 7) to 50f
            }
            drawStoryBackdrop(cover, measurer, progress, artHeight(art))
            val valueSize = if (template == CompetitionStoryTemplate.CUADRO) 4.6f * CQW else 64.8f
            drawShell(
                topAt(42f),
                end = true,
                blocks = buildList {
                    addAll(measurer.shellHeader(page.eyebrow, page.title, page.sub))
                    measurer.shellRows(page.rows, SHELL_ROWS_GAP_END, auto = false, valueSize = valueSize)?.let(::add)
                    add(measurer.shellFoot(page.foot, end = true))
                },
            )
        }
    }
}

/** Pinta la historia en un bitmap de 1080 × 1920, para el PNG. */
internal fun renderCompetitionStory(
    context: Context,
    template: CompetitionStoryTemplate,
    page: CompetitionStoryPage,
    photo: ImageBitmap?,
): android.graphics.Bitmap = renderStory(context) { drawCompetitionStory(template, page, it, photo) }
