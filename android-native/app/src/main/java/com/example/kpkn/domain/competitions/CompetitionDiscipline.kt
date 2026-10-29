package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionTemplateType
import com.example.kpkn.domain.multisport.SportId

/**
 * Las seis formas de resultado del lienzo Registros v4 (d-all): cada disciplina se registra con una de ellas, sin una pantalla por
 * deporte. LOAD = intentos de carga (c-log, c-lift) · MARKS = pruebas con marca (c-mixed) · CLOCK = contra el reloj (c-run) ·
 * STAGE = jueceo en tarima (c-bb) · COMBAT = combates (c-combat) · MATCH = partidos y tops (c-match).
 */
enum class CompetitionResultForm { LOAD, MARKS, CLOCK, STAGE, COMBAT, MATCH }

/** Los cuatro grupos de la hoja «Nueva competencia» (c-new), en su orden. */
enum class CompetitionDisciplineGroup(val label: String) {
    STRENGTH("Fuerza"),
    ENDURANCE("Resistencia"),
    COMBAT("Combate"),
    MORE("Más"),
}

/**
 * Las 28 disciplinas que dibuja c-new (C-D5: manda el dibujo). [icon] es la clave del icono de línea del lienzo (la capa de interfaz
 * la traduce); [legacy] es el `sportType` que se sigue escribiendo por compatibilidad; [sport] enlaza con el modelo de deportes para
 * poner arriba las de tus programas.
 */
enum class CompetitionDiscipline(
    val id: String,
    val label: String,
    val group: CompetitionDisciplineGroup,
    val form: CompetitionResultForm,
    val icon: String,
    val legacy: CompetitionTemplateType,
    val sport: SportId?,
) {
    POWERLIFTING("powerlifting", "Powerlifting", CompetitionDisciplineGroup.STRENGTH, CompetitionResultForm.LOAD, "dumb", CompetitionTemplateType.POWERLIFTING, SportId.POWERLIFTING),
    WEIGHTLIFTING("halterofilia", "Halterofilia", CompetitionDisciplineGroup.STRENGTH, CompetitionResultForm.LOAD, "bolt", CompetitionTemplateType.WEIGHTLIFTING, SportId.WEIGHTLIFTING),
    STRONGMAN("strongman", "Strongman", CompetitionDisciplineGroup.STRENGTH, CompetitionResultForm.MARKS, "tower", CompetitionTemplateType.STRONGMAN, SportId.STRONGMAN),
    CALISTHENICS("calistenia", "Calistenia", CompetitionDisciplineGroup.STRENGTH, CompetitionResultForm.LOAD, "flag", CompetitionTemplateType.CUSTOM, SportId.CALISTHENICS),
    ARMWRESTLING("armwrestling", "Armwrestling", CompetitionDisciplineGroup.STRENGTH, CompetitionResultForm.COMBAT, "grip", CompetitionTemplateType.MARTIAL_ARTS, SportId.ARMWRESTLING),
    BODYBUILDING("culturismo", "Culturismo", CompetitionDisciplineGroup.STRENGTH, CompetitionResultForm.STAGE, "eye", CompetitionTemplateType.BODYBUILDING, null),
    RUNNING("carrera", "Carrera", CompetitionDisciplineGroup.ENDURANCE, CompetitionResultForm.CLOCK, "heart", CompetitionTemplateType.RUNNING, SportId.RUNNING),
    TRAIL("trail", "Trail", CompetitionDisciplineGroup.ENDURANCE, CompetitionResultForm.CLOCK, "pin", CompetitionTemplateType.RUNNING, SportId.TRAIL),
    CYCLING("ciclismo", "Ciclismo", CompetitionDisciplineGroup.ENDURANCE, CompetitionResultForm.CLOCK, "repeat", CompetitionTemplateType.CUSTOM, SportId.CYCLING),
    SWIMMING("natacion", "Natación", CompetitionDisciplineGroup.ENDURANCE, CompetitionResultForm.CLOCK, "drop", CompetitionTemplateType.CUSTOM, SportId.SWIMMING),
    ROWING("remo", "Remo", CompetitionDisciplineGroup.ENDURANCE, CompetitionResultForm.CLOCK, "swap", CompetitionTemplateType.CUSTOM, SportId.ROWING),
    TRIATHLON("triatlon", "Triatlón", CompetitionDisciplineGroup.ENDURANCE, CompetitionResultForm.CLOCK, "target", CompetitionTemplateType.CUSTOM, SportId.TRIATHLON),
    HYROX("hyrox", "Hyrox", CompetitionDisciplineGroup.ENDURANCE, CompetitionResultForm.MARKS, "fire", CompetitionTemplateType.CROSSFIT, SportId.HYROX),
    CROSSFIT("crossfit", "CrossFit", CompetitionDisciplineGroup.ENDURANCE, CompetitionResultForm.MARKS, "bolt", CompetitionTemplateType.CROSSFIT, SportId.CROSSFIT),
    BOXING("boxeo", "Boxeo", CompetitionDisciplineGroup.COMBAT, CompetitionResultForm.COMBAT, "users", CompetitionTemplateType.MARTIAL_ARTS, SportId.BOXING),
    KICKBOXING("kickboxing", "Kickboxing", CompetitionDisciplineGroup.COMBAT, CompetitionResultForm.COMBAT, "users", CompetitionTemplateType.MARTIAL_ARTS, SportId.KICKBOXING),
    MUAY_THAI("muay-thai", "Muay thai", CompetitionDisciplineGroup.COMBAT, CompetitionResultForm.COMBAT, "users", CompetitionTemplateType.MARTIAL_ARTS, SportId.MUAY_THAI),
    BJJ("bjj", "BJJ", CompetitionDisciplineGroup.COMBAT, CompetitionResultForm.COMBAT, "users", CompetitionTemplateType.MARTIAL_ARTS, SportId.BJJ),
    WRESTLING("lucha", "Lucha", CompetitionDisciplineGroup.COMBAT, CompetitionResultForm.COMBAT, "users", CompetitionTemplateType.MARTIAL_ARTS, SportId.WRESTLING),
    JUDO("judo", "Judo", CompetitionDisciplineGroup.COMBAT, CompetitionResultForm.COMBAT, "users", CompetitionTemplateType.MARTIAL_ARTS, SportId.JUDO),
    MMA("mma", "MMA", CompetitionDisciplineGroup.COMBAT, CompetitionResultForm.COMBAT, "users", CompetitionTemplateType.MARTIAL_ARTS, SportId.MMA),
    CLIMBING("escalada", "Escalada", CompetitionDisciplineGroup.MORE, CompetitionResultForm.MATCH, "flag", CompetitionTemplateType.CUSTOM, SportId.CLIMBING),
    SPRINT("velocidad", "Velocidad", CompetitionDisciplineGroup.MORE, CompetitionResultForm.CLOCK, "bolt", CompetitionTemplateType.CUSTOM, SportId.SPRINT),
    JUMP("saltos", "Saltos", CompetitionDisciplineGroup.MORE, CompetitionResultForm.CLOCK, "up", CompetitionTemplateType.CUSTOM, SportId.JUMP),
    TENNIS("tenis", "Tenis", CompetitionDisciplineGroup.MORE, CompetitionResultForm.MATCH, "target", CompetitionTemplateType.CUSTOM, SportId.TENNIS),
    PADEL("padel", "Pádel", CompetitionDisciplineGroup.MORE, CompetitionResultForm.MATCH, "target", CompetitionTemplateType.CUSTOM, SportId.PADEL),
    TEAM("equipo", "Equipo", CompetitionDisciplineGroup.MORE, CompetitionResultForm.MATCH, "users", CompetitionTemplateType.CUSTOM, SportId.TEAM_SPORT),
    OTHER("otra", "Otra", CompetitionDisciplineGroup.MORE, CompetitionResultForm.MARKS, "plus", CompetitionTemplateType.CUSTOM, null),
    ;

    /** Se compite con categoría de peso y pesaje (powerlifting, halterofilia, calistenia lastrada, combates). */
    val hasWeighIn: Boolean
        get() = form == CompetitionResultForm.LOAD || form == CompetitionResultForm.COMBAT

    companion object {
        fun byId(id: String?): CompetitionDiscipline? = entries.firstOrNull { it.id == id?.trim() }

        /**
         * La disciplina de [record]: la guardada o, en los registros anteriores, la de su `sportType`. Un registro CUSTOM sin disciplina
         * (los que crea la fecha clave del programa) es powerlifting si trae la tabla SBD, intentos planificados o si [linkedToPowerlifting].
         */
        fun of(record: CompetitionRecord, linkedToPowerlifting: Boolean = false): CompetitionDiscipline {
            byId(record.discipline)?.let { return it }
            return when (record.sportType) {
                CompetitionTemplateType.POWERLIFTING -> POWERLIFTING
                CompetitionTemplateType.WEIGHTLIFTING -> WEIGHTLIFTING
                CompetitionTemplateType.BODYBUILDING -> BODYBUILDING
                CompetitionTemplateType.RUNNING -> RUNNING
                CompetitionTemplateType.STRONGMAN -> STRONGMAN
                CompetitionTemplateType.CROSSFIT -> CROSSFIT
                CompetitionTemplateType.MARTIAL_ARTS -> MMA
                CompetitionTemplateType.CUSTOM -> {
                    val sbd = record.technicalBlocks.any { it.movementType in AttemptPlanner.lifts } || record.plannedAttempts.isNotEmpty()
                    if (sbd || linkedToPowerlifting) POWERLIFTING else OTHER
                }
            }
        }

        /** Las disciplinas de c-new por grupo; dentro de cada grupo y entre grupos, primero las de tus programas ([fromPrograms]). */
        fun grouped(fromPrograms: Set<SportId>): List<Pair<CompetitionDisciplineGroup, List<CompetitionDiscipline>>> {
            fun mine(d: CompetitionDiscipline) = d.sport != null && d.sport in fromPrograms
            return CompetitionDisciplineGroup.entries
                .map { group -> group to entries.filter { it.group == group }.sortedBy { if (mine(it)) 0 else 1 } }
                .sortedBy { (_, list) -> if (list.any(::mine)) 0 else 1 }
        }

        /** La disciplina elegida al abrir c-new: la primera de tus programas o, si no hay, powerlifting. */
        fun preselected(fromPrograms: Set<SportId>): CompetitionDiscipline =
            grouped(fromPrograms).flatMap { it.second }.firstOrNull { it.sport != null && it.sport in fromPrograms } ?: POWERLIFTING
    }
}

/** Nombre del levantamiento en la tabla (c-log, c-lift). */
fun CompetitionMovementType.liftLabel(customName: String? = null): String = when (this) {
    CompetitionMovementType.SQUAT -> "Sentadilla"
    CompetitionMovementType.BENCH -> "Press banca"
    CompetitionMovementType.DEADLIFT -> "Peso muerto"
    CompetitionMovementType.SNATCH -> "Arrancada"
    CompetitionMovementType.CLEAN_AND_JERK -> "Dos tiempos"
    CompetitionMovementType.PRESS -> "Press"
    CompetitionMovementType.RUN -> "Carrera"
    CompetitionMovementType.CUSTOM -> customName?.takeIf { it.isNotBlank() } ?: "Dominada lastrada"
}

/** Nombre corto en las frases («Desde tus marcas: sentadilla 200 · banca 135 · peso muerto 217,5»). */
fun CompetitionMovementType.shortLabel(): String = when (this) {
    CompetitionMovementType.BENCH -> "banca"
    else -> liftLabel().lowercase()
}
