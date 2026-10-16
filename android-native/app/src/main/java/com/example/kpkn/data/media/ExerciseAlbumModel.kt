package com.example.kpkn.data.media

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.domain.training.NativeProgressionText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Filtro del álbum del ejercicio (A4): «Todo», «Fotos» o «Vídeos». */
enum class AlbumFilter(val label: String) {
    ALL("Todo"),
    PHOTOS("Fotos"),
    VIDEOS("Vídeos"),
}

/**
 * Un registro del álbum, ya con sus textos: [title] «Serie 3 · 95 kg × 6» y [detail] «RIR 2 · Vídeo · 18:42». [isMark] es una marca
 * (récord): la pantalla la destaca. [durationLabel] es la duración «0:14» de un vídeo.
 */
data class AlbumEntry(
    val media: WorkoutMedia,
    val title: String,
    val detail: String,
    val isMark: Boolean,
    val durationLabel: String?,
)

/** Un día del álbum: su cabecera («HOY · JUEVES 15 OCT», «LUNES 12 OCT») y sus registros, el más reciente primero. */
data class AlbumDay(val header: String, val entries: List<AlbumEntry>)

/**
 * El álbum de un ejercicio (LV8, A4): los medios de ese ejercicio entre sesiones, ordenados del más reciente al más antiguo, filtrados
 * por tipo y agrupados por día con los textos del prototipo. Puro: no sabe de pantallas ni de la base de datos.
 */
object ExerciseAlbumModel {
    private val WEEKDAYS = arrayOf("LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO", "DOMINGO")
    private val MONTHS = arrayOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")

    fun matches(media: WorkoutMedia, filter: AlbumFilter): Boolean = when (filter) {
        AlbumFilter.ALL -> true
        AlbumFilter.PHOTOS -> media.kind == WorkoutMediaKind.PHOTO
        AlbumFilter.VIDEOS -> media.kind == WorkoutMediaKind.VIDEO
    }

    /** Lo que se ve con [filter], sin lo borrado en espera ([hiddenIds]), del más reciente al más antiguo. */
    fun visible(items: List<WorkoutMedia>, filter: AlbumFilter, hiddenIds: Set<String> = emptySet()): List<WorkoutMedia> =
        items.filter { it.id !in hiddenIds && matches(it, filter) }.sortedByDescending { it.createdAtMs }

    /** «9 registros», «1 registro» o «Sin registros». */
    fun countLabel(count: Int): String = when (count) {
        0 -> "Sin registros"
        1 -> "1 registro"
        else -> "$count registros"
    }

    /** Subtítulo del álbum: «Sentadilla trasera · 9 registros». */
    fun heading(exerciseName: String, count: Int): String = "$exerciseName · ${countLabel(count)}"

    /** Cabecera de un día: «HOY · JUEVES 15 OCT»; con otro año, también el año. */
    fun dayHeader(date: LocalDate, today: LocalDate): String {
        val base = buildString {
            append(WEEKDAYS[date.dayOfWeek.value - 1])
            append(' ')
            append(date.dayOfMonth)
            append(' ')
            append(MONTHS[date.monthValue - 1])
            if (date.year != today.year) append(' ').append(date.year)
        }
        return if (date == today) "HOY · $base" else base
    }

    /**
     * Los registros de [items] agrupados por día, el día más reciente primero. [effortFor] da el esfuerzo de la serie («RIR 2») cuando
     * se sabe; si no, el registro no lo nombra.
     */
    fun days(
        items: List<WorkoutMedia>,
        filter: AlbumFilter,
        hiddenIds: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        effortFor: (WorkoutMedia) -> String? = { null },
    ): List<AlbumDay> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return visible(items, filter, hiddenIds)
            .groupBy { Instant.ofEpochMilli(it.createdAtMs).atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayItems) ->
                AlbumDay(
                    header = dayHeader(date, today),
                    entries = dayItems.map { entryOf(it, zone, effortFor(it)) },
                )
            }
    }

    fun entryOf(media: WorkoutMedia, zone: ZoneId = ZoneId.systemDefault(), effort: String? = null): AlbumEntry {
        val title = buildList {
            media.setIndex?.let { add("Serie ${it + 1}") }
            loadLabel(media.weightKg, media.reps)?.let { add(it) }
        }.joinToString(" · ").ifEmpty { media.exerciseName?.takeIf { it.isNotBlank() } ?: "Registro" }
        val time = Instant.ofEpochMilli(media.createdAtMs).atZone(zone).toLocalTime()
        val detail = listOfNotNull(
            effort,
            if (media.kind == WorkoutMediaKind.VIDEO) "Vídeo" else "Foto",
            String.format(java.util.Locale.ROOT, "%02d:%02d", time.hour, time.minute),
        ).joinToString(" · ")
        return AlbumEntry(
            media = media,
            title = title,
            detail = detail,
            isMark = media.isPr,
            durationLabel = if (media.kind == WorkoutMediaKind.VIDEO) WorkoutAlbumGrouping.formatDurationMs(media.durationMs) else null,
        )
    }

    /** «95 kg × 6» (con espacio inseparable antes de «kg»), «95 kg» o «6 reps»; nulo si no se sabe ninguna de las dos cosas. */
    fun loadLabel(weightKg: Double?, reps: Int?): String? {
        val weight = weightKg?.takeIf { it > 0.0 }?.let { NativeProgressionText.formatKg(it) + NBSP + "kg" }
        val repCount = reps?.takeIf { it > 0 }
        return when {
            weight != null && repCount != null -> "$weight × $repCount"
            weight != null -> weight
            repCount != null -> "$repCount reps"
            else -> null
        }
    }

    /** Clave con la que la sesión guarda la serie de un medio (`ejercicio_serie` y, si es de un lado, `_L` o `_R`), o nulo si no se sabe. */
    fun completedSetKey(media: WorkoutMedia): String? {
        val exerciseId = media.exerciseId?.takeIf { it.isNotBlank() } ?: return null
        val setIndex = media.setIndex ?: return null
        return WorkoutMediaPrFlags.completedSetKey(exerciseId, setIndex, media.side)
    }

    /** El esfuerzo histórico pertenece al log y al lado del medio; nunca a una serie parecida de hoy. */
    fun effortForLog(media: WorkoutMedia, log: WorkoutLog): String? {
        val key = completedSetKey(media) ?: return null
        val exercise = log.completedExercises.firstOrNull { it.exerciseId == media.exerciseId } ?: return null
        return exercise.sets.firstOrNull { it.id == key }?.let(::effortLabel)
    }

    /** El esfuerzo que registró una serie, como «RIR 2», «RPE 7,5» o «Fallo»; nulo si no registró ninguno. */
    fun effortLabel(set: CompletedSet): String? {
        if (set.isFailure || set.actualIntensityMode == IntensityMode.FAILURE) return "Fallo"
        val value = set.actualIntensityValue
        return when {
            set.actualIntensityMode == IntensityMode.RIR && value != null -> "RIR ${number(value)}"
            set.actualIntensityMode == IntensityMode.RPE && value != null -> "RPE ${number(value)}"
            set.rir != null -> "RIR ${set.rir}"
            set.rpe != null -> "RPE ${number(set.rpe)}"
            else -> null
        }
    }

    private fun number(value: Double): String =
        if (abs(value - value.roundToInt()) < 0.001) value.roundToInt().toString() else NativeProgressionText.formatKg(value)

    /** Espacio inseparable (U+00A0): «95 kg» no se parte entre la cifra y la unidad. */
    private const val NBSP = ' '
}
