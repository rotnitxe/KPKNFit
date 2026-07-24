package com.example.kpkn.domain.nutrition

/**
 * CookingMethodParser — Legacy rich pattern catalog (440+ expressions).
 *
 * NOT wired into the live FoodLoggerDrawer pipeline. Production uses
 * FoodParser.COOKING_PATTERNS + COOKING_FACTORS + oil grams in FoodLoggerDrawer.
 * Aggressive fritura multipliers here (2–3.5×) would exaggerate macros — do not cable blindly.
 */
object CookingMethodParser {

    enum class CookingCategory {
        LOW_IMPACT, NEUTRAL, MODERATE, HIGH, VERY_HIGH,
    }

    data class CookingMethodResult(
        val method: String,
        val category: CookingCategory,
        val kcalFactor: Double,
        val proteinFactor: Double,
        val carbsFactor: Double,
        val fatsFactor: Double,
        val waterChange: Double,
        val confidence: Double,
    )

    data class MethodPattern(val regex: Regex, val method: String, val kcal: Double, val protein: Double, val carbs: Double, val fats: Double, val water: Double)
    data class DonenessPattern(val regex: Regex, val method: String, val kcal: Double, val protein: Double, val carbs: Double, val fats: Double)
    data class ColloquialPattern(val regex: Regex, val factor: Double)
    data class ModernMethod(val regex: Regex, val method: String, val factor: Double)

    private val METHOD_PATTERNS = listOf(
        MethodPattern(Regex("""\b(crudo|natural|al\s+natural|sin\s+cocinar|vivo)\b""", RegexOption.IGNORE_CASE), "crudo", 0.90, 1.0, 1.0, 1.0, 0.0),
        MethodPattern(Regex("""\b(hervido|hervidito|cocido|cocidito|en\s+agua|en\s+agua\s+con\s+sal|sancochado|sancochadito|sancochar|medio\s+sancochado)\b""", RegexOption.IGNORE_CASE), "hervido", 0.90, 0.95, 1.0, 1.0, 0.15),
        MethodPattern(Regex("""\b(al\s+vapor|al\s+vaporcito|vaporizado|en\s+vaporera|en\s+olla\s+vaporera|en\s+canasta\s+de\s+bambu|en\s+tamalera|al\s+vapor\s+suave|al\s+vapor\s+fuerte|coccion\s+al\s+vapor\s+express|en\s+papillote\s+al\s+vapor|en\s+olla\s+de\s+presion\s+con\s+rejilla|envuelto\s+en\s+hoja|en\s+hoja\s+de\s+platano|en\s+hoja\s+de\s+maiz|envuelto)\b""", RegexOption.IGNORE_CASE), "vapor", 0.95, 1.0, 1.0, 1.0, 0.08),
        MethodPattern(Regex("""\b(olla\s+de\s+presion|olla\s+expres|en\s+olla\s+de\s+presion|en\s+olla\s+expres)\b""", RegexOption.IGNORE_CASE), "olla_presion", 0.90, 0.95, 1.0, 1.0, 0.12),
        MethodPattern(Regex("""\b(escalfado|escalfadito|pochado|poche|huevo\s+poche|en\s+agua\s+con\s+vinagre|en\s+caldo\s+suave|a\s+fuego\s+muy\s+lento|apenas\s+un\s+temblor|sin\s+que\s+hierva|a\s+punto\s+de\s+ebullicion|con\s+agua\s+temblando|con\s+aguita\s+temblorosa)\b""", RegexOption.IGNORE_CASE), "escalfado", 0.95, 1.0, 1.0, 1.0, 0.05),
        MethodPattern(Regex("""\b(blanqueado|blanqueadito|dar\s+un\s+hervor|hervir\s+un\s+minuto|pasado\s+por\s+agua|huevo\s+tibio|huevo\s+mollet)\b""", RegexOption.IGNORE_CASE), "blanqueado", 0.98, 1.0, 1.0, 1.0, 0.03),
        MethodPattern(Regex("""\b(microondas|microondeado|al\s+micro)\b""", RegexOption.IGNORE_CASE), "microondas", 0.98, 1.0, 1.0, 1.0, -0.05),
        MethodPattern(Regex("""\b(sous\s+vide|al\s+vacio|envasado\s+y\s+cocido|en\s+bolsa\s+sellada|a\s+baja\s+temperatura|coccion\s+controlada|en\s+termocirculador|en\s+olla\s+con\s+temperatura\s+exacta|coccion\s+precisa|coccion\s+lenta\s+al\s+vacio)\b""", RegexOption.IGNORE_CASE), "sous_vide", 1.02, 1.0, 1.0, 1.0, 0.0),
        MethodPattern(Regex("""\b(ceviche|leche\s+de\s+tigre|aguachile|tiradito|carpaccio|coccion\s+acida|marinado\s+acido|macerado\s+en\s+acido)\b""", RegexOption.IGNORE_CASE), "ceviche", 0.98, 1.0, 1.0, 1.0, 0.05),
        MethodPattern(Regex("""\b(fermentado|fermentacion)\b""", RegexOption.IGNORE_CASE), "fermentado", 0.93, 1.05, 0.90, 1.0, 0.0),
        MethodPattern(Regex("""\b(encurtido|en\s+vinagreta|en\s+salmuera|salado\s+y\s+secado|en\s+salazon)\b""", RegexOption.IGNORE_CASE), "encurtido", 0.95, 1.0, 1.0, 1.0, 0.10),
        MethodPattern(Regex("""\b(a\s+la\s+plancha|a\s+la\s+planchita|plancheado|bien\s+plancheado|a\s+la\s+chapa|a\s+la\s+chapita|a\s+la\s+chapa\s+volcanica|al\s+fierro|a\s+la\s+bifera|sellado|marcado\s+a\s+la\s+plancha)\b""", RegexOption.IGNORE_CASE), "plancha_sin_aceite", 1.0, 1.05, 1.0, 0.95, -0.15),
        MethodPattern(Regex("""\b(a\s+la\s+parrilla|a\s+la\s+parrillita|parrillado|asado\s+a\s+la\s+parrilla|a\s+la\s+brasa|al\s+carbon|a\s+la\s+lena|al\s+asador|al\s+espeto|al\s+espeton|en\s+varal|en\s+cruz|al\s+espiedo|al\s+pincho|ahumado\s+en\s+parrilla|ahumadito|barbacoa\s+de\s+tambor)\b""", RegexOption.IGNORE_CASE), "parrilla", 1.05, 1.10, 1.0, 0.90, -0.20),
        MethodPattern(Regex("""\b(ahumado|ahumadito|en\s+frio|en\s+caliente|con\s+lena\s+aromatica|al\s+humo|humeado|con\s+humito|en\s+ahumador|en\s+tambor\s+ahumador|en\s+caja\s+de\s+ahumar|con\s+virutas\s+de\s+madera)\b""", RegexOption.IGNORE_CASE), "ahumado", 1.10, 1.05, 1.0, 0.95, -0.15),
        MethodPattern(Regex("""\b(curado|en\s+salazon|salmuera|en\s+salmuera|salado\s+y\s+secado)\b""", RegexOption.IGNORE_CASE), "curado", 1.30, 1.10, 1.0, 1.05, -0.30),
        MethodPattern(Regex("""\b(asado\s+al\s+horno|al\s+horno|horneado|en\s+horno\s+de\s+lena|a\s+la\s+piedra|al\s+rescoldo|a\s+las\s+brasas|en\s+cazuela\s+de\s+barro\s+al\s+horno|dorado\s+al\s+horno|a\s+la\s+sal|envuelto\s+en\s+papillote|empapelado|en\s+olla\s+de\s+hierro\s+tapada|en\s+horno\s+holandes|gratinado|gratinadito|al\s+graten|con\s+costra|con\s+cubierta\s+crocante|al\s+golpe\s+de\s+horno|un\s+golpe\s+de\s+horno\s+fuerte\s+al\s+principio|horno\s+fuerte|horno\s+suave|horno\s+medio|a\s+fuego\s+de\s+horno)\b""", RegexOption.IGNORE_CASE), "horno_sin_grasa", 1.15, 1.10, 1.05, 0.95, -0.25),
        MethodPattern(Regex("""\b(salteado|salteadito|salteado\s+al\s+wok|wok-wok|al\s+wok|revuelto\s+en\s+el\s+wok|vuelta\s+y\s+vuelta\s+en\s+la\s+sarten|rehogado|rehogadito|sofrito|sofritito|pochado\s+en\s+aceite|dorado\s+en\s+sarten|bien\s+doradito|tostado\s+en\s+sarten|refrito|refritito)\b""", RegexOption.IGNORE_CASE), "frito", 1.10, 1.10, 1.0, 1.00, -0.20),
        MethodPattern(Regex("""\b(confitado|confitadito|en\s+su\s+propia\s+grasa|en\s+manteca|en\s+aceite\s+templado|confitar|en\s+aceite\s+a\s+baja\s+temperatura|sumergido\s+en\s+aceite\s+tibio|a\s+80\s+grados|textura\s+melosa\s+por\s+confitado|desmigable|confitura\s+de\s+carne)\b""", RegexOption.IGNORE_CASE), "confitado", 2.50, 1.0, 1.0, 3.0, -0.20),
        MethodPattern(Regex("""\b(estofado|estofadito|guisado|guisadito|guiso|guisote|en\s+salsa|a\s+la\s+cacerola|en\s+cazuela|cazuelita|en\s+olla\s+de\s+barro|a\s+fuego\s+lento\s+tapado|coccion\s+prolongada|a\s+la\s+antigua|de\s+toda\s+la\s+vida|como\s+el\s+de\s+la\s+abuela|casero|guiso\s+de\s+puchero|cocido|puchero|olla\s+podrida|cocido\s+madrileno|sancochado|sudado|sudadito|al\s+vapor\s+de\s+su\s+propio\s+jugo|en\s+su\s+jugo|en\s+juguito|a\s+la\s+olla|en\s+paila|en\s+caldero|en\s+fondo|con\s+fondo\s+oscuro|con\s+fondo\s+claro|desglasado|reducido|concentrado|espesadito|trabado|ligado|meloso|con\s+textura\s+de\s+terciopelo)\b""", RegexOption.IGNORE_CASE), "estofado", 1.30, 1.0, 1.0, 1.20, 0.05),
        MethodPattern(Regex("""\b(braseado|al\s+brasier|en\s+cocotte|en\s+horno\s+holandes\s+tapado|primera\s+sellada\s+luego\s+liquido|un\s+cuarto\s+de\s+liquido|coccion\s+mixta|primero\s+dorado\s+luego\s+guisado|fondo\s+corto)\b""", RegexOption.IGNORE_CASE), "braseado", 1.25, 1.05, 1.0, 1.15, 0.0),
        MethodPattern(Regex("""\b(mantecado|enmantecado|en\s+mantequilla\s+clarificada|en\s+ghee|en\s+aceite\s+de\s+oliva\s+suave)\b""", RegexOption.IGNORE_CASE), "mantecado", 1.30, 1.0, 1.0, 1.40, -0.05),
        MethodPattern(Regex("""\b(glaseado|con\s+brillo|lacado|con\s+espejo|caramelizado|acaramelado)\b""", RegexOption.IGNORE_CASE), "glaseado", 1.30, 1.0, 1.15, 1.0, -0.10),
        MethodPattern(Regex("""\b(frito|fritito|fritanga|fritura|freir\s+en\s+abundante\s+aceite|freir\s+en\s+aceite\s+profundo|sumergido\s+en\s+aceite\s+caliente|por\s+inmersion|frito\s+en\s+sarten|fritura\s+superficial|fritura\s+ligera|en\s+freidora\s+de\s+aire|air\s+fryer|sin\s+aceite\s+\(air\s+fryer\)|sin\s+aceite\s+en\s+freidora)\b""", RegexOption.IGNORE_CASE), "fritura_superficial", 2.0, 1.0, 1.0, 2.5, -0.15),
        MethodPattern(Regex("""\b(gratinado\s+con\s+queso|gratinado\s+con\s+bechamel|gratinado\s+burbujeante|con\s+burbujitas)\b""", RegexOption.IGNORE_CASE), "gratinado", 1.80, 1.20, 1.15, 1.60, -0.15),
        MethodPattern(Regex("""\b(rebozado|empanizado|empanado|enharinado\s+y\s+frito|en\s+tempura|tempurizado|rebozado\s+en\s+panko|capeado|capeadito|milanesa|milanesita|escalope|escalopita|a\s+la\s+romana|en\s+gabardina|bunuelo|bunuelito|croqueta|croquetita|fritura\s+de\s+calle|fritura\s+casera)\b""", RegexOption.IGNORE_CASE), "rebozado_frito", 3.5, 1.15, 1.30, 3.5, 0.10),
        MethodPattern(Regex("""\b(freir\s+en\s+aceite\s+profundo|fritura\s+profunda|fritura\s+en\s+inmersion)\b""", RegexOption.IGNORE_CASE), "fritura_profunda", 3.0, 1.0, 1.0, 3.5, -0.20),
        MethodPattern(Regex("""\b(deshidratado|seco\s+al\s+sol|al\s+oreo|orear|en\s+deshidratador)\b""", RegexOption.IGNORE_CASE), "deshidratado", 4.0, 3.0, 3.0, 3.0, -0.85),
    )

    private val DONENESS_PATTERNS = listOf(
        DonenessPattern(Regex("""\b(al\s+dente|aldentito)\b""", RegexOption.IGNORE_CASE), "al_dente", 1.0, 1.0, 1.0, 1.0),
        DonenessPattern(Regex("""\b(termino\s+medio|a\s+punto|en\s+su\s+punto\s+exacto|jugoso|rosadito)\b""", RegexOption.IGNORE_CASE), "termino_medio", 1.0, 1.0, 1.0, 1.0),
        DonenessPattern(Regex("""\b(tres\s+cuartos|bien\s+cocido|bien\s+pasado)\b""", RegexOption.IGNORE_CASE), "bien_cocido", 0.95, 1.0, 1.0, 0.95),
        DonenessPattern(Regex("""\b(seco|reseco|como\s+suela\s+de\s+zapato|como\s+carton)\b""", RegexOption.IGNORE_CASE), "reseco", 0.90, 1.05, 1.05, 0.90),
        DonenessPattern(Regex("""\b(chicloso|gomoso|crudo\s+por\s+dentro|vivo|que\s+muge|sangrante|que\s+suelte\s+sangre)\b""", RegexOption.IGNORE_CASE), "crudo_interior", 1.05, 1.0, 1.0, 1.05),
        DonenessPattern(Regex("""\b(apenas\s+cocido|pasado\s+de\s+coccion|recocido|deshecho|que\s+se\s+deshaga|a\s+punto\s+de\s+deshacerse)\b""", RegexOption.IGNORE_CASE), "pasado", 0.90, 0.95, 1.0, 0.95),
        DonenessPattern(Regex("""\b(blandito|suavecito|firme\s+pero\s+tierno|que\s+se\s+deshaga\s+en\s+la\s+boca|butter-soft|mantequilloso|derretido|fundente|que\s+se\s+corre)\b""", RegexOption.IGNORE_CASE), "tierno", 1.0, 1.0, 1.0, 1.0),
        DonenessPattern(Regex("""\b(ingles|muy\s+poco\s+hecho|bleu|casi\s+crudo)\b""", RegexOption.IGNORE_CASE), "bleu", 1.02, 1.0, 1.0, 1.02),
        DonenessPattern(Regex("""\b(chamuscado|tatemado|tatemar|quemadito|con\s+puntitos\s+negros)\b""", RegexOption.IGNORE_CASE), "chamuscado", 1.05, 1.05, 0.95, 1.0),
    )

    private val COLLOQUIAL_DONENESS = listOf(
        ColloquialPattern(Regex("""\b(vuelta\s+y\s+vuelta)\b""", RegexOption.IGNORE_CASE), 1.02),
        ColloquialPattern(Regex("""\b(bien\s+doradito|crocante|crujiente|con\s+corteza|con\s+costra)\b""", RegexOption.IGNORE_CASE), 1.10),
        ColloquialPattern(Regex("""\b(tostado|tostadito|bien\s+tostado|ligeramente\s+tostado|torrado|torradito|torrefacto|dorado|doradito|al\s+sarten\s+seco|a\s+fuego\s+seco|en\s+comal|comaleado|en\s+budare|en\s+callana|tatemado\s+en\s+comal)\b""", RegexOption.IGNORE_CASE), 1.08),
    )

    private val MODERN_METHODS = listOf(
        ModernMethod(Regex("""\b(en\s+olla\s+de\s+coccion\s+lenta|en\s+slow\s+cooker|en\s+crockpot)\b""", RegexOption.IGNORE_CASE), "slow_cooker", 0.95),
        ModernMethod(Regex("""\b(en\s+Instant\s+Pot|olla\s+multifuncion)\b""", RegexOption.IGNORE_CASE), "multifuncion", 0.95),
        ModernMethod(Regex("""\b(en\s+arrocera|arrocero)\b""", RegexOption.IGNORE_CASE), "arrocera", 0.95),
        ModernMethod(Regex("""\b(en\s+termomix|robot|robotizado)\b""", RegexOption.IGNORE_CASE), "robot", 0.98),
        ModernMethod(Regex("""\b(en\s+horno\s+de\s+conveccion)\b""", RegexOption.IGNORE_CASE), "conveccion", 1.10),
        ModernMethod(Regex("""\b(en\s+grill\s+electrico)\b""", RegexOption.IGNORE_CASE), "grill_electrico", 1.05),
        ModernMethod(Regex("""\b(en\s+fondue)\b""", RegexOption.IGNORE_CASE), "fondue", 1.40),
        ModernMethod(Regex("""\b(en\s+raclette)\b""", RegexOption.IGNORE_CASE), "raclette", 1.50),
        ModernMethod(Regex("""\b(en\s+tagine|en\s+tajin)\b""", RegexOption.IGNORE_CASE), "tagine", 1.20),
        ModernMethod(Regex("""\b(en\s+paellera|paellero)\b""", RegexOption.IGNORE_CASE), "paellera", 1.15),
        ModernMethod(Regex("""\b(en\s+cazuela\s+de\s+hierro|en\s+sarten\s+de\s+hierro\s+fundido|en\s+wok\s+de\s+acero|en\s+olla\s+de\s+cobre)\b""", RegexOption.IGNORE_CASE), "hierro", 1.05),
        ModernMethod(Regex("""\b(bajo\s+tierra|pachamanca|curanto|en\s+hoyo)\b""", RegexOption.IGNORE_CASE), "bajo_tierra", 1.10),
        ModernMethod(Regex("""\b(en\s+espeto|al\s+asador|en\s+cruz)\b""", RegexOption.IGNORE_CASE), "asador", 1.05),
        ModernMethod(Regex("""\b(al\s+disco|de\s+arado)\b""", RegexOption.IGNORE_CASE), "disco", 1.25),
    )

    fun parse(text: String): CookingMethodResult? {
        val lower = text.lowercase()

        for (entry in METHOD_PATTERNS) {
            if (entry.regex.containsMatchIn(lower)) {
                val category = when {
                    entry.kcal < 1.1 -> CookingCategory.NEUTRAL
                    entry.kcal < 1.5 -> CookingCategory.MODERATE
                    entry.kcal < 2.5 -> CookingCategory.HIGH
                    else -> CookingCategory.VERY_HIGH
                }
                return CookingMethodResult(
                    method = entry.method,
                    category = category,
                    kcalFactor = entry.kcal,
                    proteinFactor = entry.protein,
                    carbsFactor = entry.carbs,
                    fatsFactor = entry.fats,
                    waterChange = entry.water,
                    confidence = 0.85,
                )
            }
        }

        for (entry in DONENESS_PATTERNS) {
            if (entry.regex.containsMatchIn(lower)) {
                return CookingMethodResult(
                    method = entry.method,
                    category = CookingCategory.NEUTRAL,
                    kcalFactor = entry.kcal,
                    proteinFactor = entry.protein,
                    carbsFactor = entry.carbs,
                    fatsFactor = entry.fats,
                    waterChange = 0.0,
                    confidence = 0.70,
                )
            }
        }

        for (entry in COLLOQUIAL_DONENESS) {
            if (entry.regex.containsMatchIn(lower)) {
                return CookingMethodResult(
                    method = "coloquial",
                    category = CookingCategory.NEUTRAL,
                    kcalFactor = entry.factor,
                    proteinFactor = 1.0,
                    carbsFactor = 1.0,
                    fatsFactor = 1.0,
                    waterChange = -0.10,
                    confidence = 0.60,
                )
            }
        }

        for (entry in MODERN_METHODS) {
            if (entry.regex.containsMatchIn(lower)) {
                return CookingMethodResult(
                    method = entry.method,
                    category = if (entry.factor < 1.1) CookingCategory.NEUTRAL else CookingCategory.MODERATE,
                    kcalFactor = entry.factor,
                    proteinFactor = 1.0,
                    carbsFactor = 1.0,
                    fatsFactor = 1.0,
                    waterChange = -0.10,
                    confidence = 0.75,
                )
            }
        }

        return null
    }

    fun applyOilFactor(
        baseKcal: Double,
        baseProtein: Double,
        baseCarbs: Double,
        baseFats: Double,
        method: CookingMethodResult?,
        tablespoonsOil: Double = 0.0,
    ): QuadMacro {
        val methodKcal = method?.kcalFactor ?: 1.0
        val methodProtein = method?.proteinFactor ?: 1.0
        val methodCarbs = method?.carbsFactor ?: 1.0
        val methodFats = method?.fatsFactor ?: 1.0

        var kcal = baseKcal * methodKcal
        var protein = baseProtein * methodProtein
        var carbs = baseCarbs * methodCarbs
        var fats = baseFats * methodFats

        if (tablespoonsOil > 0) {
            kcal += tablespoonsOil * 120.0
            fats += tablespoonsOil * 13.5
        }

        return QuadMacro(
            calories = round1(kcal),
            protein = round1(protein),
            carbs = round1(carbs),
            fats = round1(fats),
        )
    }

    data class QuadMacro(
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fats: Double,
    )

    private fun round1(v: Double) = kotlin.math.round(v * 10.0) / 10.0
}
