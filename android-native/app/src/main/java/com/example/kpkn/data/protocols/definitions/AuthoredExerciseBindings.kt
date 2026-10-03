package com.example.kpkn.data.protocols.definitions

import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.TrainingPlanRecipe

/** Tablas fuente de los originales autorados (§10.2/§10.3). */
enum class AuthoredSourceTable {
    /** PHUL · Muscle & Strength, edición actualizada 2021-05-26 (§10.2). */
    PHUL_MS_2021,
    /** PHAT · Biolayne, publicado el 2016-05-30 (§10.3). */
    PHAT_BIOLAYNE_2016,
}

/**
 * Binding de UN slot autorado a su configuración canónica del catálogo
 * (§13.5): ejercicio/variante/aparato de la tabla → id verificado + razón.
 *
 * Un binding sin coincidencia verificada es error de publicación y origina una
 * alta curada explícita; nunca un fallback por nombre ni un id «parecido».
 */
data class AuthoredBinding(
    val table: AuthoredSourceTable,
    /** `DayRecipe.id` del día autorado. */
    val dayId: String,
    /** `SlotRecipe.id` del slot. */
    val slotId: String,
    /** Ejercicio tal y como aparece en la tabla fuente. */
    val exercise: String,
    /** Variante/aparato citado por la fuente; null si la tabla no lo especifica. */
    val variant: String? = null,
    val configurationId: String,
    val reason: String,
)

/** Slot de una receta autorada sin binding: error de publicación (§13.5). */
data class AuthoredBindingGap(
    val dayId: String?,
    val slotId: String,
    val configurationId: String,
    val detail: String,
)

/**
 * Tabla estática de bindings de §10 (§13.5): centraliza el id canónico y la
 * razón de CADA slot de las cuatro recetas. Los originales y sus adaptaciones
 * comparten la tabla fuente ([AuthoredPhulPhatRecipes.recipeTables]), porque
 * la adaptación conserva la tabla y solo cambia la procedencia.
 */
object AuthoredExerciseBindings {
    /** recipeId → tabla fuente §10 cotejada. */
    val recipeTables: Map<String, AuthoredSourceTable> = mapOf(
        AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID to AuthoredSourceTable.PHUL_MS_2021,
        AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID to AuthoredSourceTable.PHUL_MS_2021,
        AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID to AuthoredSourceTable.PHAT_BIOLAYNE_2016,
        AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID to AuthoredSourceTable.PHAT_BIOLAYNE_2016,
    )

    private fun b(
        table: AuthoredSourceTable,
        dayId: String,
        slotId: String,
        exercise: String,
        configurationId: String,
        reason: String,
        variant: String? = null,
    ) = AuthoredBinding(table, dayId, slotId, exercise, variant, configurationId, reason)

    val all: List<AuthoredBinding> = buildList {
        // ── PHUL §10.2 ───────────────────────────────────────────────────────
        val phul = AuthoredSourceTable.PHUL_MS_2021
        add(b(phul, "phul-upper-power", "bp", "Banca barra 3–4×3–5", CatalogIds.BP, "Empuje horizontal con barra; requiere banca y rack (§13.3)."))
        add(b(phul, "phul-upper-power", "inc-db", "Banca inclinada mancuernas 3–4×6–10", CatalogIds.BP_INC_DB, "Banca inclinada con mancuernas: mismo patrón con otra implementación."))
        add(b(phul, "phul-upper-power", "row", "Remo inclinado barra 3–4×3–5", CatalogIds.ROW, "Remo inclinado con barra: la tabla PHUL no cita Pendlay, se usa el remo convencional de barra."))
        add(b(phul, "phul-upper-power", "lat", "Jalón 3–4×6–10", CatalogIds.LAT, "Jalón en polea alta bilateral; se acredita con la estación de polea, nunca con `machine` genérico."))
        add(b(phul, "phul-upper-power", "ohp", "Press sobre cabeza 2–3×5–8", CatalogIds.OHP, "Press militar de pie con barra (§13.3, fila O)."))
        add(b(phul, "phul-upper-power", "curl", "Curl barra 2–3×6–10", CatalogIds.CURL, "Curl de bíceps con barra recta."))
        add(
            b(phul, "phul-upper-power", "skull", "Extensión de tríceps tumbado/skullcrusher 2–3×6–10", CatalogIds.SKULLCRUSHER,
                "Alta curada §13.5: la extensión de tríceps tumbada no existía en el catálogo; se publicó la configuración canónica en lugar de resolver a un «parecido».", variant = "barra recta, tumbado en banco"),
        )
        add(b(phul, "phul-lower-power", "sq", "Sentadilla barra 3–4×3–5", CatalogIds.SQ_HIGH, "Sentadilla con barra alta y rack (§13.3, fila S)."))
        add(b(phul, "phul-lower-power", "dl", "Peso muerto 3–4×3–5", CatalogIds.DL, "Peso muerto convencional con barra."))
        add(b(phul, "phul-lower-power", "press", "Prensa 3–5×10–15", CatalogIds.PRESS_LEG, "Prensa de piernas curada; exige la configuración de prensa confirmada."))
        add(
            b(phul, "phul-lower-power", "curl", "Curl femoral 3–4×6–10", CatalogIds.CURL_L,
                "§10.2 admite la variante descrita genéricamente con aparato real: curl femoral tumbado en máquina.", variant = "tumbado"),
        )
        add(
            b(phul, "phul-lower-power", "calf", "Gemelo 4×6–10", CatalogIds.CALF_STANDING,
                "Gemelo de pie en máquina; la clave calf_standing acredita el aparato (§13.2).", variant = "de pie"),
        )
        add(b(phul, "phul-upper-hypertrophy", "bp-inc", "Banca inclinada barra 3–4×8–12", CatalogIds.BP_INC, "Banca inclinada con barra."))
        add(b(phul, "phul-upper-hypertrophy", "fly", "Aperturas planas mancuernas 3–4×8–12", CatalogIds.FLY, "Aperturas en banco plano con mancuernas."))
        add(b(phul, "phul-upper-hypertrophy", "row-cable", "Remo sentado polea 3–4×8–12", CatalogIds.ROW_CABLE, "Remo sentado en polea baja: la estación de polea es el aparato de la tabla."))
        add(
            b(phul, "phul-upper-hypertrophy", "row-db", "Remo unilateral mancuerna 3–4×8–12 por lado", CatalogIds.ROW_DB,
                "La definición de remo con mancuernas no publica eje de lateralidad; el «por lado» de la fuente viaja en `isUnilateral` del slot.",
            variant = "por lado",
            ),
        )
        add(b(phul, "phul-upper-hypertrophy", "lat-raise", "Elevación lateral mancuernas 3–4×8–12", CatalogIds.LATERAL, "Elevación lateral de pie con mancuernas."))
        add(
            b(phul, "phul-upper-hypertrophy", "curl-inc", "Curl inclinado sentado mancuernas 3–4×8–12", CatalogIds.CURL_SEATED_DB,
                "Correspondencia canónica: curl SENTADO con mancuernas, la única configuración sentada de la definición; el ángulo inclinado del respaldo de la fuente no es un eje publicado y queda registrado en esta razón.",
            variant = "respaldo inclinado",
            ),
        )
        add(b(phul, "phul-upper-hypertrophy", "tri-polea", "Extensión tríceps polea 3–4×8–12", CatalogIds.PUSHDOWN, "Extensión de tríceps en polea alta bilateral."))
        add(b(phul, "phul-lower-hypertrophy", "sq-front", "Sentadilla frontal 3–4×8–12", CatalogIds.SQ_FRONT, "Sentadilla frontal con barra (§10.2: principal sin sustitución automática)."))
        add(
            b(phul, "phul-lower-hypertrophy", "lunge", "Zancada barra 3–4×8–12 por lado", CatalogIds.LUNGE_W_BARBELL,
                "Zancada caminante con barra del catálogo; el «por lado» de la fuente viaja en `isUnilateral`.", variant = "barra, por lado",
            ),
        )
        add(b(phul, "phul-lower-hypertrophy", "ext", "Extensión de cuádriceps 3–4×10–15", CatalogIds.LEG_EXT, "Extensión de cuádriceps en máquina bilateral."))
        add(
            b(phul, "phul-lower-hypertrophy", "curl", "Curl femoral 3–4×10–15", CatalogIds.CURL_H,
                "§10.2 admite la variante genérica con aparato real: curl femoral sentado en máquina.", variant = "sentado",
            ),
        )
        add(b(phul, "phul-lower-hypertrophy", "calf-sit", "Gemelo sentado 3–4×8–12", CatalogIds.CALF_SEATED, "Máquina de gemelos sentado (clave calf_seated, §13.2)."))
        add(b(phul, "phul-lower-hypertrophy", "calf-press", "Gemelo en prensa 3–4×8–12", CatalogIds.CALF_LEG_PRESS, "Gemelo en la prensa de piernas: configuración exacta añadida en §13.5 y habilitada por la clave leg_press."))

        // ── PHAT §10.3 ───────────────────────────────────────────────────────
        val phat = AuthoredSourceTable.PHAT_BIOLAYNE_2016
        add(
            b(phat, "phat-power-upper", "row-pendlay", "Remo inclinado/Pendlay barra 3×3–5", CatalogIds.PENDLAY,
                "§10.3 pide la primera alternativa explícita de cada «o»: remo Pendlay con barra.", variant = "Pendlay",
            ),
        )
        add(
            b(phat, "phat-power-upper", "pullup", "Dominada lastrada 2×6–10", CatalogIds.PULLUP,
            "Dominada con agarre prono medio; el lastre es carga externa que el usuario elige en entrenamiento (la receta nunca inventa kg, §14.2).",
            variant = "lastrada",
            ),
        )
        add(b(phat, "phat-power-upper", "rack-chin", "Rack chin 2×6–10", CatalogIds.RACK_CHIN, "Alta curada §13.5: tracción vertical con barra a la altura del pecho exigida por §10.3; no se sustituye por dominada libre."))
        add(b(phat, "phat-power-upper", "bench-db", "Press plano mancuernas 3×3–5", CatalogIds.BP_DB, "Press de banca plano con mancuernas."))
        add(
            b(phat, "phat-power-upper", "dips", "Fondos lastrados 2×6–10", CatalogIds.DIPS,
            "Fondos en paralelas con sus apoyos reales; el lastre se elige en entrenamiento.", variant = "lastrados",
            ),
        )
        add(b(phat, "phat-power-upper", "press-db", "Press sentado mancuernas 3×6–10", CatalogIds.SEATED_PRESS_DB, "Press de hombros sentado con mancuernas."))
        add(b(phat, "phat-power-upper", "curl-ez", "Curl barra EZ 3×6–10", CatalogIds.CURL_EZ, "Curl de bíceps con barra EZ; la clave ez_bar acredita el implemento (§13.2)."))
        add(b(phat, "phat-power-upper", "skull", "Skullcrusher 3×6–10", CatalogIds.SKULLCRUSHER, "Alta curada §13.5; la tabla no especifica implemento y el catálogo publica la variante de barra recta."))
        add(b(phat, "phat-power-lower", "sq", "Sentadilla 3×3–5", CatalogIds.SQ_HIGH, "Sentadilla con barra alta y rack."))
        add(b(phat, "phat-power-lower", "hack", "Hack squat 2×6–10", CatalogIds.SQ_HACK, "Hack squat en su máquina curada; una máquina genérica no la prueba (§13.2)."))
        add(b(phat, "phat-power-lower", "ext", "Extensión cuádriceps 2×6–10", CatalogIds.LEG_EXT, "Extensión de cuádriceps en máquina bilateral."))
        add(b(phat, "phat-power-lower", "sldl", "Peso muerto piernas rígidas 3×5–8", CatalogIds.SLDL, "Peso muerto de piernas rígidas con barra."))
        add(b(phat, "phat-power-lower", "curl-lying", "Curl femoral tumbado 2×6–10", CatalogIds.CURL_L, "Curl femoral tumbado en máquina (primera alternativa literal de la tabla)."))
        add(b(phat, "phat-power-lower", "calf-stand", "Gemelo de pie 3×6–10", CatalogIds.CALF_STANDING, "Gemelo de pie en máquina (clave calf_standing)."))
        add(b(phat, "phat-power-lower", "calf-sit", "Gemelo sentado 2×6–10", CatalogIds.CALF_SEATED, "Máquina de gemelos sentado (clave calf_seated)."))
        add(
            b(phat, "phat-back-shoulder-hypertrophy", "speed-row", "Remo del día 1 rápido 6×3 al 65–70 % de su carga habitual de 3–5 reps",
                CatalogIds.PENDLAY, "El SPEED usa la MISMA configuración técnica del pesado enlazado (`sourceSlotId`), con referencia OBSERVED_WORKING_SET (§14.2).",
                variant = "65 % del trabajo 3–5 reps",
            ),
        )
        add(b(phat, "phat-back-shoulder-hypertrophy", "rack-chin", "Rack chin 3×8–12", CatalogIds.RACK_CHIN, "Misma configuración exacta del día de fuerza; no se sustituye por dominada libre."))
        add(b(phat, "phat-back-shoulder-hypertrophy", "row-cable", "Remo polea sentado 3×8–12", CatalogIds.ROW_CABLE, "Remo sentado en polea."))
        add(
            b(phat, "phat-back-shoulder-hypertrophy", "row-cs", "Remo mancuernas pecho apoyado banco inclinado 2×12–15", CatalogIds.CSR,
                "§10.3 elige la primera alternativa «remo mancuernas con pecho apoyado»; agarre medio de la definición.", variant = "pecho apoyado, agarre medio",
            ),
        )
        add(
            b(phat, "phat-back-shoulder-hypertrophy", "lat-close", "Jalón agarre cerrado 2×15–20", CatalogIds.LAT,
            "La definición de jalón no publica eje de agarre: el agarre cerrado viaja en `TechniqueModifier.CLOSE_GRIP` sobre la configuración canónica de polea.",
            variant = "agarre cerrado",
            ),
        )
        add(b(phat, "phat-back-shoulder-hypertrophy", "press-db", "Press mancuernas sentado 3×8–12", CatalogIds.SEATED_PRESS_DB, "Press sentado con mancuernas."))
        add(b(phat, "phat-back-shoulder-hypertrophy", "upright", "Remo al mentón 2×12–15", CatalogIds.UPRIGHT_ROW, "Remo al mentón con barra."))
        add(b(phat, "phat-back-shoulder-hypertrophy", "lat-raise", "Lateral mancuernas 3×12–20", CatalogIds.LATERAL, "Elevación lateral de pie con mancuernas."))
        add(
            b(phat, "phat-lower-hypertrophy", "speed-sq", "Sentadilla del día 2 rápida 6×3 al 65–70 % de su carga habitual de 3–5 reps",
                CatalogIds.SQ_HIGH, "MISMA configuración que el pesado del día 2, con referencia OBSERVED_WORKING_SET (§14.2).",
                variant = "65 % del trabajo 3–5 reps",
            ),
        )
        add(b(phat, "phat-lower-hypertrophy", "hack", "Hack 3×8–12", CatalogIds.SQ_HACK, "Hack squat en su máquina curada."))
        add(b(phat, "phat-lower-hypertrophy", "press", "Prensa 2×12–15", CatalogIds.PRESS_LEG, "Prensa de piernas curada."))
        add(b(phat, "phat-lower-hypertrophy", "ext", "Extensión cuádriceps 3×15–20", CatalogIds.LEG_EXT, "Extensión de cuádriceps en máquina bilateral."))
        add(b(phat, "phat-lower-hypertrophy", "rdl", "Rumano 3×8–12", CatalogIds.RDL, "Peso muerto rumano con barra."))
        add(b(phat, "phat-lower-hypertrophy", "curl-lying", "Curl femoral tumbado 2×12–15", CatalogIds.CURL_L, "Curl femoral tumbado en máquina."))
        add(b(phat, "phat-lower-hypertrophy", "curl-seated", "Curl femoral sentado 2×15–20", CatalogIds.CURL_H, "Curl femoral sentado en máquina."))
        add(b(phat, "phat-lower-hypertrophy", "calf-donkey", "Gemelo donkey 4×10–15", CatalogIds.CALF_DONKEY, "Alta curada §13.5: máquina de gemelo donkey; no se sustituye por gemelo de pie (§13.2: sin equivalencia automática)."))
        add(b(phat, "phat-lower-hypertrophy", "calf-sit", "Gemelo sentado 3×15–20", CatalogIds.CALF_SEATED, "Máquina de gemelos sentado."))
        add(
            b(phat, "phat-chest-arms-hypertrophy", "speed-bp", "Press plano mancuernas del día 1 rápido 6×3 al 65–70 % de su carga habitual de 3–5 reps",
                CatalogIds.BP_DB, "MISMA configuración que el pesado del día 1, con referencia OBSERVED_WORKING_SET (§14.2).",
                variant = "65 % del trabajo 3–5 reps",
            ),
        )
        add(b(phat, "phat-chest-arms-hypertrophy", "inc-db", "Press inclinado mancuernas 3×8–12", CatalogIds.BP_INC_DB, "Press inclinado con mancuernas."))
        add(
            b(phat, "phat-chest-arms-hypertrophy", "hammer", "Press pecho Hammer Strength 3×12–15", CatalogIds.HAMMER_CHEST_PRESS,
                "Configuración convergente curada del catálogo; un `machine` genérico no acredita el aparato convergente (§13.2/§10.3).",
                variant = "convergente",
            ),
        )
        add(b(phat, "phat-chest-arms-hypertrophy", "fly-inc", "Aperturas inclinadas polea 2×15–20", CatalogIds.FLY_INC_CABLE, "Aperturas inclinadas en polea."))
        add(b(phat, "phat-chest-arms-hypertrophy", "preacher", "Predicador EZ 3×8–12", CatalogIds.PREACHER_EZ, "Curl predicador con barra EZ; la clave preacher_bench/ez_bar acredita banco e implemento."))
        add(b(phat, "phat-chest-arms-hypertrophy", "conc", "Curl concentración mancuerna 2×12–15", CatalogIds.CONCENTRATION_DB, "Curl de concentración con mancuerna."))
        add(
            b(phat, "phat-chest-arms-hypertrophy", "spider", "Spider curl pecho apoyado banco inclinado 2×15–20", CatalogIds.SPIDER_DB,
                "Spider curl con mancuernas; primera variante de agarre publicada (supino) de la definición.", variant = "agarre supino",
            ),
        )
        add(b(phat, "phat-chest-arms-hypertrophy", "overhead-ez", "Extensión tríceps sentado EZ sobre cabeza 3×8–12", CatalogIds.OVERHEAD_EZ, "Extensión overhead con barra EZ (configuración exacta §13.5)."))
        add(
            b(phat, "phat-chest-arms-hypertrophy", "pushdown", "Pushdown cuerda 2×12–15", CatalogIds.PUSHDOWN,
                "La configuración canónica publicada describe el empuje en polea alta con las manos empujando la cuerda: la correspondencia técnica es la estación de polea (nunca `machine` genérico) y el accesorio forma parte de esa copia canónica.",
                variant = "cuerda",
            ),
        )
        add(b(phat, "phat-chest-arms-hypertrophy", "kickback", "Patada tríceps polea 2×15–20", CatalogIds.KICKBACK_CABLE, "Alta curada §13.5: patada de tríceps en polea unilateral."))
    }

    fun of(table: AuthoredSourceTable): List<AuthoredBinding> = all.filter { it.table == table }

    fun bindingFor(table: AuthoredSourceTable, dayId: String, slotId: String): AuthoredBinding? =
        all.firstOrNull { it.table == table && it.dayId == dayId && it.slotId == slotId }

    fun configurationFor(table: AuthoredSourceTable, dayId: String, slotId: String): String? =
        bindingFor(table, dayId, slotId)?.configurationId

    /**
     * Slots de [recipe] sin binding o con un binding que apunta a otra
     * configuración: **error de publicación** de §13.5. Una lista vacía demuestra
     * que cada slot autorado está ligado a su id canónico verificado.
     */
    fun coverageGapsOf(recipe: TrainingPlanRecipe): List<AuthoredBindingGap> {
        val table = recipeTables[recipe.id]
            ?: return listOf(AuthoredBindingGap(null, recipe.id, "", "La receta '${recipe.id}' no declara tabla fuente autorada."))
        val gaps = mutableListOf<AuthoredBindingGap>()
        recipe.weeks.forEach { week ->
            week.days.forEach { day ->
                day.slots.forEach { slot ->
                    val binding = bindingFor(table, day.id.orEmpty(), slot.id)
                    when {
                        binding == null -> gaps += AuthoredBindingGap(
                            day.id,
                            slot.id,
                            slot.lift.configurationId,
                            "Sin binding autorado para ${day.id}/${slot.id} en ${table.name}.",
                        )
                        binding.configurationId != slot.lift.configurationId -> gaps += AuthoredBindingGap(
                            day.id,
                            slot.id,
                            slot.lift.configurationId,
                            "El binding liga ${binding.configurationId} pero la receta publica ${slot.lift.configurationId}.",
                        )
                    }
                }
            }
        }
        return gaps
    }
}
