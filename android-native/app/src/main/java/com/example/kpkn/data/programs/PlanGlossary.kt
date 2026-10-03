package com.example.kpkn.data.programs

/**
 * Una entrada del glosario ligero de la hoja «Cómo funciona» (nivel A del diseño editorial §6).
 *
 * @property term término del plan que explica.
 * @property title cómo se rotula el término ante el usuario: la sigla y, cuando ayuda, su nombre completo.
 * @property definition una o dos frases en español llano, sin ids internos; cada sigla se explica en su entrada.
 * @property conceptId id del concepto de Conceptos Clave (`TRAINING_CONCEPTS_DATABASE`) donde ampliar la
 *   explicación, o null si el término solo tiene la definición corta.
 */
data class GlossaryEntry(
    val term: PlanTerm,
    val title: String,
    val definition: String,
    val conceptId: String? = null,
) {
    init {
        require(title.isNotBlank()) { "GlossaryEntry.title no puede estar vacío: $term" }
        require(definition.isNotBlank()) { "GlossaryEntry.definition no puede estar vacía: $term" }
        require(conceptId == null || conceptId.isNotBlank()) { "GlossaryEntry.conceptId no puede estar vacío: $term" }
    }
}

/**
 * Glosario de los términos técnicos de un plan, en dos niveles (diseño editorial §6, decisión D10).
 *
 * - Nivel A, esta tabla: una definición corta por cada [PlanTerm], para mostrarla en línea en la hoja del plan.
 * - Nivel B, Conceptos Clave: la explicación completa en `TRAINING_CONCEPTS_DATABASE`. [GlossaryEntry.conceptId]
 *   enlaza los dos niveles, y solo los términos con concepto ofrecen «Ver en Conceptos clave».
 *
 * Reglas del texto: español llano, una o dos frases por término, sin ids internos, y cada sigla explicada en su
 * propia entrada. Los niveles T1, T2 y T3 solo se nombran en la entrada de [PlanTerm.TIERS].
 *
 * Es una tabla de datos puros (sin Android): `PlanGlossaryTest` fija que cada [PlanTerm] tiene su entrada y que
 * cada `conceptId` existe en Conceptos Clave.
 */
object PlanGlossary {
    /** Una entrada por término, en el orden del enum [PlanTerm] (el mapa conserva el orden de inserción). */
    val entries: Map<PlanTerm, GlossaryEntry> = buildMap {
        fun add(entry: GlossaryEntry) {
            check(put(entry.term, entry) == null) { "Término duplicado en PlanGlossary: ${entry.term}" }
        }

        add(
            GlossaryEntry(
                term = PlanTerm.ONE_RM,
                title = "1RM",
                definition = "El máximo peso que puedes levantar una sola vez con buena técnica.",
                conceptId = "rm-tm",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.TM,
                title = "TM (máximo de entrenamiento)",
                definition = "Un peso de referencia, normalmente el 90 % de tu 1RM, sobre el que el plan calcula " +
                    "tus cargas. Es más bajo que tu máximo real para poder repetir series con buena técnica.",
                conceptId = "rm-tm",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.AMRAP,
                title = "AMRAP",
                definition = "Una serie en la que haces tantas repeticiones como puedas con una carga fija, sin " +
                    "perder la técnica. El plan usa ese resultado para ajustar tus cargas.",
                conceptId = "amrap",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.RPE,
                title = "RPE",
                definition = "Escala de 1 a 10 del esfuerzo de una serie. RPE 8 significa que te quedaban unas 2 " +
                    "repeticiones.",
                conceptId = "rpe",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.RIR,
                title = "RIR",
                definition = "Repeticiones en reserva: cuántas te quedaban al terminar la serie.",
                conceptId = "rir",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.DUP,
                title = "Ondulación diaria (DUP)",
                definition = "Cambiar el tipo de sesión de un mismo levantamiento a lo largo de la semana (un día " +
                    "pesado, otro ligero y otro de volumen) en vez de repetir siempre lo mismo.",
                conceptId = "ondulacion-diaria",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.MEV_MRV,
                title = "MEV y MRV",
                definition = "MEV es el volumen mínimo efectivo: las series semanales mínimas que producen mejora. " +
                    "MRV es el máximo recuperable: lo máximo que puedes recuperar de una semana a otra.",
                conceptId = "mev-mrv",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.SBD,
                title = "SBD",
                definition = "Sentadilla, banca y peso muerto (por sus siglas en inglés).",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.TIERS,
                title = "Niveles T1, T2 y T3 (GZCL)",
                definition = "T1 es el levantamiento principal con carga alta y pocas repeticiones, T2 uno " +
                    "complementario con más repeticiones y T3 accesorios de muchas repeticiones.",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.TOP_SET,
                title = "Serie top y back-off",
                definition = "La serie top es la más pesada de la sesión. Las back-off son series posteriores con " +
                    "menos peso.",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.SPEED,
                title = "Series rápidas",
                definition = "Series ligeras ejecutadas a máxima velocidad para entrenar potencia.",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.DELOAD,
                title = "Semana de descarga",
                definition = "Una semana más ligera, con menos series o menos peso, para recuperarte antes de " +
                    "seguir progresando.",
                conceptId = "deload",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.CYCLE,
                title = "Ciclo que se repite",
                definition = "Un ciclo es el conjunto de semanas del plan. Cuando termina, empieza otro igual con " +
                    "tus últimas cargas.",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.POWERLIFTING,
                title = "Powerlifting",
                definition = "Deporte de fuerza máxima en el que se compite por el mayor peso posible en " +
                    "sentadilla, banca y peso muerto. Los planes de powerlifting preparan esos tres levantamientos.",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.POWERBUILDING,
                title = "Powerbuilding",
                definition = "Estilo de entrenamiento que combina la fuerza del powerlifting, con levantamientos " +
                    "pesados de pocas repeticiones, y trabajo de más repeticiones para ganar músculo.",
            ),
        )
        add(
            GlossaryEntry(
                term = PlanTerm.UL_PPL,
                title = "Torso/pierna y empuje/tirón/pierna",
                definition = "Dos formas de repartir la semana: torso un día y pierna otro, o empuje, tirón y " +
                    "pierna en tres sesiones.",
            ),
        )

        val missing = PlanTerm.entries.filterNot { it in this }
        check(missing.isEmpty()) { "Faltan entradas de glosario para: $missing" }
    }

    /** La entrada de [term]; un término sin entrada es un error de datos, no un caso a tolerar. */
    fun entryFor(term: PlanTerm): GlossaryEntry =
        entries[term] ?: error("Falta la entrada de glosario de «$term» en PlanGlossary")

    /** Las entradas de [terms] en el orden estable del enum [PlanTerm], sin depender del orden del conjunto. */
    fun entriesFor(terms: Set<PlanTerm>): List<GlossaryEntry> =
        terms.sortedBy { it.ordinal }.map { entryFor(it) }
}
