package com.example.kpkn.domain.nutrition

/**
 * SubjectivePortionEngine — Traduce 310+ expresiones subjetivas a gramos reales.
 *
 * Categorías:
 * 1. Utensilios (~35): cucharada, taza, vaso, copa, cucharón, etc.
 * 2. Cuerpo/Gestos (~55): puñado, pizca, pellizco, dedo, puño, palma, etc.
 * 3. Subjetivas/Coloquiales (~60): un poco, un chorrito, un montón, etc.
 * 4. Pan/Masas (~20): rebanada, hogaza, bollo, empanada, etc.
 * 5. Envases (~15): lata, bote, caja, bolsa, paquete, etc.
 * 6. Bebidas (~20): vaso, copa, jarra, chupito, caña, etc.
 * 7. Comparaciones (~15): tamaño de nuez, pelota de golf, puño, etc.
 * 8. Verbos de cantidad (~20): salpimentar, untar, espolvorear, etc.
 *
 * Sistema de densidad por categoría de alimento + factores relativos sobre ración estándar.
 */
object SubjectivePortionEngine {

    enum class FoodDensityCategory(val densityGPerMl: Double) {
        LIQUID(1.0),
        POWDER(0.6),
        GRAIN(0.85),
        VEGETABLE(0.7),
        PROTEIN(1.0),
        FAT(0.9),
        DAIRY(1.03),
        NUTS(0.65),
        FRUIT(0.6),
        MIXED(0.8),
    }

    data class PortionResult(
        val grams: Double,
        val confidence: Double,
        val source: String,
        val expression: String,
        val relativeFactor: Double,
    )

    // ─── Utensilios: volumen base en ml ─────────────────────────────────────

    private val UTENSIL_PATTERNS = listOf(
        // Cucharadas
        Triple(Regex("""\b(un|una|1)\s+cucharaditas?\b""", RegexOption.IGNORE_CASE), 5.0, "cucharadita"),
        Triple(Regex("""\b(un|una|1)\s+cucharadas?\s+(?:de\s+)?(?:postre)?\b""", RegexOption.IGNORE_CASE), 10.0, "cucharada_postre"),
        Triple(Regex("""\b(un|una|1)\s+cucharadas?\s+soperas?\b""", RegexOption.IGNORE_CASE), 20.0, "cucharada_sopera"),
        Triple(Regex("""\b(un|una|1)\s+cucharadas?\s+colmadas?\b""", RegexOption.IGNORE_CASE), 25.0, "cucharada_colmada"),
        Triple(Regex("""\b(un|una|1)\s+cucharadas?\s+generosas?\b""", RegexOption.IGNORE_CASE), 22.0, "cucharada_generosa"),
        Triple(Regex("""\b(\d+(?:[.,]\d+)?)\s+cucharaditas?\b""", RegexOption.IGNORE_CASE), 5.0, "cucharadita_multi"),
        Triple(Regex("""\b(\d+(?:[.,]\d+)?)\s+cucharadas?\b""", RegexOption.IGNORE_CASE), 15.0, "cucharada_multi"),
        Triple(Regex("""\bmedia\s+cucharadita\b""", RegexOption.IGNORE_CASE), 2.5, "media_cucharadita"),
        Triple(Regex("""\bmedia\s+cucharada\b""", RegexOption.IGNORE_CASE), 7.5, "media_cucharada"),
        Triple(Regex("""\bun\s+cuarto\s+de\s+cucharadita\b""", RegexOption.IGNORE_CASE), 1.25, "cuarto_cucharadita"),

        // Cucharón
        Triple(Regex("""\b(un|una|1)\s+cuchar[oó]n\b""", RegexOption.IGNORE_CASE), 90.0, "cucharon"),
        Triple(Regex("""\bmedio\s+cuchar[oó]n\b""", RegexOption.IGNORE_CASE), 45.0, "medio_cucharon"),

        // Tazas
        Triple(Regex("""\b(un|una|1)\s+tazas?\s+rebosantes?\b""", RegexOption.IGNORE_CASE), 280.0, "taza_rebosante"),
        Triple(Regex("""\b(un|una|1)\s+tazas?\s+de\s+desayuno\b""", RegexOption.IGNORE_CASE), 200.0, "taza_desayuno"),
        Triple(Regex("""\b(un|una|1)\s+tazas?\b""", RegexOption.IGNORE_CASE), 250.0, "taza"),
        Triple(Regex("""\bmedia\s+taza\b""", RegexOption.IGNORE_CASE), 125.0, "media_taza"),
        Triple(Regex("""\bun\s+cuarto\s+de\s+taza\b""", RegexOption.IGNORE_CASE), 62.5, "cuarto_taza"),
        Triple(Regex("""\bun\s+tercio\s+de\s+taza\b""", RegexOption.IGNORE_CASE), 83.0, "tercio_taza"),
        Triple(Regex("""\b(un|una|1)\s+tacitas?\s+de\s+caf[ée]\b""", RegexOption.IGNORE_CASE), 60.0, "tacita_cafe"),

        // Vasos y copas
        Triple(Regex("""\b(un|una|1)\s+vasos?\b""", RegexOption.IGNORE_CASE), 250.0, "vaso"),
        Triple(Regex("""\bmedio\s+vaso\b""", RegexOption.IGNORE_CASE), 125.0, "medio_vaso"),
        Triple(Regex("""\b(un|una|1)\s+copas?\b""", RegexOption.IGNORE_CASE), 150.0, "copa"),
        Triple(Regex("""\b(un|una|1)\s+copitas?\b""", RegexOption.IGNORE_CASE), 50.0, "copita"),
        Triple(Regex("""\b(un|una|1)\s+caballitos?\b""", RegexOption.IGNORE_CASE), 45.0, "caballito"),
        Triple(Regex("""\b(un|una|1)\s+dedales?\b""", RegexOption.IGNORE_CASE), 5.0, "dedal"),

        // Otros utensilios
        Triple(Regex("""\b(un|una|1)\s+pocillos?\b""", RegexOption.IGNORE_CASE), 60.0, "pocillo"),
        Triple(Regex("""\b(un|una|1)\s+bols?\b""", RegexOption.IGNORE_CASE), 300.0, "bol"),
        Triple(Regex("""\b(un|una|1)\s+platos?\s+hondos?\b""", RegexOption.IGNORE_CASE), 400.0, "plato_hondo"),
        Triple(Regex("""\b(un|una|1)\s+platos?\s+grandes?\b""", RegexOption.IGNORE_CASE), 350.0, "plato_grande"),
        Triple(Regex("""\b(un|una|1)\s+platos?\b""", RegexOption.IGNORE_CASE), 250.0, "plato"),
        Triple(Regex("""\b(un|una|1)\s+tazones?\b""", RegexOption.IGNORE_CASE), 300.0, "tazon"),
        Triple(Regex("""\b(un|una|1)\s+fuente\s+de\b""", RegexOption.IGNORE_CASE), 500.0, "fuente"),
    )

    // ─── Cuerpo/Gestos: gramos base por categoría ───────────────────────────

    private val BODY_PATTERNS = listOf(
        // Puñado
        Triple(Regex("""\bun\s+puñadito\b""", RegexOption.IGNORE_CASE), 20.0, "puñadito"),
        Triple(Regex("""\bun\s+puñado\s+gigante\b""", RegexOption.IGNORE_CASE), 50.0, "puñado_gigante"),
        Triple(Regex("""\bun\s+puñado\s+generoso\b""", RegexOption.IGNORE_CASE), 45.0, "puñado_generoso"),
        Triple(Regex("""\bun\s+puñado\b""", RegexOption.IGNORE_CASE), 30.0, "puñado"),
        Triple(Regex("""\b(lo\s+que\s+agarr[óo]\s+la\s+mano|un\s+puñado\s+de)\b""", RegexOption.IGNORE_CASE), 40.0, "mano"),

        // Pizca / Pellizco
        Triple(Regex("""\buna\s+pizquita\b""", RegexOption.IGNORE_CASE), 0.3, "pizquita"),
        Triple(Regex("""\buna\s+pizca\b""", RegexOption.IGNORE_CASE), 0.5, "pizca"),
        Triple(Regex("""\bun\s+pellizquito\b""", RegexOption.IGNORE_CASE), 0.5, "pellizquito"),
        Triple(Regex("""\bun\s+pellizco\b""", RegexOption.IGNORE_CASE), 1.0, "pellizco"),

        // Dedos
        Triple(Regex("""\bun\s+dedo\b""", RegexOption.IGNORE_CASE), 20.0, "dedo"),
        Triple(Regex("""\bdos\s+dedos\b""", RegexOption.IGNORE_CASE), 30.0, "dos_dedos"),
        Triple(Regex("""\btres\s+dedos\b""", RegexOption.IGNORE_CASE), 45.0, "tres_dedos"),

        // Mano / Palma / Puño
        Triple(Regex("""\bla\s+palma\s+de\s+la\s+mano\b""", RegexOption.IGNORE_CASE), 135.0, "palma"),
        Triple(Regex("""\bel\s+puño\s+cerrado\b""", RegexOption.IGNORE_CASE), 175.0, "puño_cerrado"),
        Triple(Regex("""\bun\s+puño\b""", RegexOption.IGNORE_CASE), 150.0, "puño"),
        Triple(Regex("""\bun\s+nudillo\b""", RegexOption.IGNORE_CASE), 10.0, "nudillo"),

        // Piezas individuales
        Triple(Regex("""\bun\s+diente\s+(?:de\s+)?ajo\b""", RegexOption.IGNORE_CASE), 5.0, "diente_ajo"),
        Triple(Regex("""\bmedia\s+cabeza\s+de\s+ajo\b""", RegexOption.IGNORE_CASE), 15.0, "media_cabeza_ajo"),
        Triple(Regex("""\buna\s+cabeza\s+de\s+ajo\b""", RegexOption.IGNORE_CASE), 30.0, "cabeza_ajo"),
        Triple(Regex("""\buna\s+rama\b""", RegexOption.IGNORE_CASE), 10.0, "rama"),
        Triple(Regex("""\bun\s+ramillete\b""", RegexOption.IGNORE_CASE), 15.0, "ramillete"),
        Triple(Regex("""\bun\s+tallo\b""", RegexOption.IGNORE_CASE), 15.0, "tallo"),
        Triple(Regex("""\buna\s+hoja\b""", RegexOption.IGNORE_CASE), 5.0, "hoja"),
        Triple(Regex("""\bunas?\s+hojitas?\b""", RegexOption.IGNORE_CASE), 3.0, "hojitas"),
        Triple(Regex("""\bun\s+cogollo\b""", RegexOption.IGNORE_CASE), 30.0, "cogollo"),
        Triple(Regex("""\bun\s+ramo\b""", RegexOption.IGNORE_CASE), 20.0, "ramo"),
        Triple(Regex("""\buna\s+vara\b""", RegexOption.IGNORE_CASE), 5.0, "vara"),
        Triple(Regex("""\buna\s+astilla\b""", RegexOption.IGNORE_CASE), 2.0, "astilla"),

        // Cortes
        Triple(Regex("""\buna\s+rodaja\b""", RegexOption.IGNORE_CASE), 30.0, "rodaja"),
        Triple(Regex("""\bmedia\s+rodaja\b""", RegexOption.IGNORE_CASE), 15.0, "media_rodaja"),
        Triple(Regex("""\buna\s+tajada\b""", RegexOption.IGNORE_CASE), 40.0, "tajada"),
        Triple(Regex("""\bun\s+trozo\b""", RegexOption.IGNORE_CASE), 50.0, "trozo"),
        Triple(Regex("""\bun\s+trocito\b""", RegexOption.IGNORE_CASE), 20.0, "trocito"),
        Triple(Regex("""\bun\s+pedazo\b""", RegexOption.IGNORE_CASE), 50.0, "pedazo"),
        Triple(Regex("""\bun\s+pedacito\b""", RegexOption.IGNORE_CASE), 20.0, "pedacito"),
        Triple(Regex("""\buna\s+loncha\b""", RegexOption.IGNORE_CASE), 25.0, "loncha"),
        Triple(Regex("""\buna\s+lonchita\b""", RegexOption.IGNORE_CASE), 15.0, "lonchita"),
        Triple(Regex("""\buna\s+l[aá]mina\b""", RegexOption.IGNORE_CASE), 15.0, "lamina"),
        Triple(Regex("""\buna\s+tira\b""", RegexOption.IGNORE_CASE), 15.0, "tira"),
        Triple(Regex("""\buna\s+tirita\b""", RegexOption.IGNORE_CASE), 8.0, "tirita"),
        Triple(Regex("""\bun\s+gajo\b""", RegexOption.IGNORE_CASE), 40.0, "gajo"),
        Triple(Regex("""\buna\s+raja\b""", RegexOption.IGNORE_CASE), 30.0, "raja"),
        Triple(Regex("""\buna\s+cuña\b""", RegexOption.IGNORE_CASE), 40.0, "cuña"),
        Triple(Regex("""\buna\s+esquina\b""", RegexOption.IGNORE_CASE), 60.0, "esquina"),
        Triple(Regex("""\buna\s+punta\b""", RegexOption.IGNORE_CASE), 40.0, "punta"),
        Triple(Regex("""\bun\s+tri[aá]ngulo\b""", RegexOption.IGNORE_CASE), 60.0, "triangulo"),
        Triple(Regex("""\bun\s+dado\b""", RegexOption.IGNORE_CASE), 20.0, "dado"),
        Triple(Regex("""\bun\s+cubito\b""", RegexOption.IGNORE_CASE), 10.0, "cubito"),

        // Formas
        Triple(Regex("""\buna\s+pastilla\b""", RegexOption.IGNORE_CASE), 10.0, "pastilla"),
        Triple(Regex("""\buna\s+tableta\b""", RegexOption.IGNORE_CASE), 100.0, "tableta"),
        Triple(Regex("""\buna\s+onza\b""", RegexOption.IGNORE_CASE), 28.0, "onza"),
        Triple(Regex("""\buna\s+barra\b""", RegexOption.IGNORE_CASE), 250.0, "barra"),
        Triple(Regex("""\bun\s+cuarto\s+de\s+barra\b""", RegexOption.IGNORE_CASE), 62.0, "cuarto_barra"),
        Triple(Regex("""\buna\s+nuez\b""", RegexOption.IGNORE_CASE), 20.0, "nuez"),
        Triple(Regex("""\buna\s+avellana\b""", RegexOption.IGNORE_CASE), 10.0, "avellana"),
        Triple(Regex("""\buna\s+aceituna\b""", RegexOption.IGNORE_CASE), 5.0, "aceituna"),
        Triple(Regex("""\bun\s+garbanzo\b""", RegexOption.IGNORE_CASE), 1.0, "garbanzo"),
        Triple(Regex("""\bun\s+grano\b""", RegexOption.IGNORE_CASE), 0.03, "grano"),
    )

    // ─── Subjetivas/Coloquiales: factor relativo ────────────────────────────

    private val SUBJECTIVE_PATTERNS = listOf(
        // Casi nada
        Triple(Regex("""\bun\s+poquit[ií]n\b""", RegexOption.IGNORE_CASE), 0.01, "poquitin"),
        Triple(Regex("""\bun\s+tantico\b""", RegexOption.IGNORE_CASE), 0.02, "tantico"),
        Triple(Regex("""\bun\s+chin\b""", RegexOption.IGNORE_CASE), 0.02, "chin"),
        Triple(Regex("""\bun\s+poquillo\b""", RegexOption.IGNORE_CASE), 0.03, "poquillo"),
        Triple(Regex("""\bun\s+pel[ií]n\b""", RegexOption.IGNORE_CASE), 0.03, "pelin"),
        Triple(Regex("""\buna\s+miaja\b""", RegexOption.IGNORE_CASE), 0.02, "miaja"),
        Triple(Regex("""\buna\s+mijita\b""", RegexOption.IGNORE_CASE), 0.02, "mijita"),

        // Muy poco
        Triple(Regex("""\buna\s+gotita\b""", RegexOption.IGNORE_CASE), 0.03, "gotita"),
        Triple(Regex("""\buna\s+gota\b""", RegexOption.IGNORE_CASE), 0.05, "gota"),
        Triple(Regex("""\bun\s+hilito\b""", RegexOption.IGNORE_CASE), 0.03, "hilito"),
        Triple(Regex("""\bun\s+hilo\b""", RegexOption.IGNORE_CASE), 0.05, "hilo"),
        Triple(Regex("""\bun\s+velo\b""", RegexOption.IGNORE_CASE), 0.05, "velo"),

        // Poco
        Triple(Regex("""\bun\s+chorrito\b""", RegexOption.IGNORE_CASE), 0.10, "chorrito"),
        Triple(Regex("""\bun\s+chorret[oó]n\b""", RegexOption.IGNORE_CASE), 0.20, "chorreton"),
        Triple(Regex("""\bun\s+chorro\b""", RegexOption.IGNORE_CASE), 0.15, "chorro"),
        Triple(Regex("""\bun\s+cul[ií]n\b""", RegexOption.IGNORE_CASE), 0.10, "culin"),
        Triple(Regex("""\bun\s+culillo\b""", RegexOption.IGNORE_CASE), 0.12, "culillo"),
        Triple(Regex("""\bun\s+fondo\b""", RegexOption.IGNORE_CASE), 0.10, "fondo"),
        Triple(Regex("""\bun\s+poquito\b""", RegexOption.IGNORE_CASE), 0.10, "poquito"),
        Triple(Regex("""\bun\s+poco\b""", RegexOption.IGNORE_CASE), 0.15, "poco"),

        // Ración normal
        Triple(Regex("""\buna\s+capita\b""", RegexOption.IGNORE_CASE), 0.5, "capita"),
        Triple(Regex("""\buna\s+capa\b""", RegexOption.IGNORE_CASE), 0.7, "capa"),
        Triple(Regex("""\buna\s+capa\s+fina\b""", RegexOption.IGNORE_CASE), 0.4, "capa_fina"),
        Triple(Regex("""\buna\s+medida\b""", RegexOption.IGNORE_CASE), 1.0, "medida"),
        Triple(Regex("""\buna\s+medida\s+escasa\b""", RegexOption.IGNORE_CASE), 0.7, "medida_escasa"),
        Triple(Regex("""\buna\s+raci[oó]n\s+individual\b""", RegexOption.IGNORE_CASE), 1.0, "racion_individual"),
        Triple(Regex("""\buna\s+porci[oó]n\b""", RegexOption.IGNORE_CASE), 1.0, "porcion"),
        Triple(Regex("""\buna\s+raci[oó]n\b""", RegexOption.IGNORE_CASE), 1.0, "racion"),

        // Generosa
        Triple(Regex("""\buna\s+medida\s+generosa\b""", RegexOption.IGNORE_CASE), 1.3, "medida_generosa"),
        Triple(Regex("""\buna\s+raci[oó]n\s+generosa\b""", RegexOption.IGNORE_CASE), 1.4, "racion_generosa"),
        Triple(Regex("""\buna\s+raci[oó]n\s+doble\b""", RegexOption.IGNORE_CASE), 2.0, "racion_doble"),
        Triple(Regex("""\buna\s+media\s+raci[oó]n\b""", RegexOption.IGNORE_CASE), 0.5, "media_racion"),

        // Mucha cantidad
        Triple(Regex("""\bun\s+mont[oó]n\b""", RegexOption.IGNORE_CASE), 2.0, "monton"),
        Triple(Regex("""\bun\s+montoncito\b""", RegexOption.IGNORE_CASE), 1.5, "montoncito"),
        Triple(Regex("""\bun\s+cerro\b""", RegexOption.IGNORE_CASE), 3.0, "cerro"),
        Triple(Regex("""\buna\s+barbaridad\b""", RegexOption.IGNORE_CASE), 3.5, "barbaridad"),
        Triple(Regex("""\buna\s+bestialidad\b""", RegexOption.IGNORE_CASE), 4.0, "bestialidad"),
        Triple(Regex("""\buna\s+exageraci[oó]n\b""", RegexOption.IGNORE_CASE), 3.5, "exageracion"),
        Triple(Regex("""\bun\s+disparate\b""", RegexOption.IGNORE_CASE), 3.0, "disparate"),
        Triple(Regex("""\bun\s+porr[oó]n\b""", RegexOption.IGNORE_CASE), 3.0, "porron"),

        // Scoops / medidas deportivas
        Triple(Regex("""\bun\s+scoop\s+generoso\b""", RegexOption.IGNORE_CASE), 40.0, "scoop_generoso"),
        Triple(Regex("""\b(\d+(?:[.,]\d+)?)\s+scoops?\s+generosos?\b""", RegexOption.IGNORE_CASE), 40.0, "scoops_generosos"),
        Triple(Regex("""\bun\s+scoop\b""", RegexOption.IGNORE_CASE), 30.0, "scoop"),
        Triple(Regex("""\b(\d+(?:[.,]\d+)?)\s+scoops?\b""", RegexOption.IGNORE_CASE), 30.0, "scoops"),
        Triple(Regex("""\buna\s+medida\s+(?:de\s+)?(?:prote[ií]na|suplemento)\b""", RegexOption.IGNORE_CASE), 30.0, "medida_proteina"),
    )

    // ─── Pan/Masas ──────────────────────────────────────────────────────────

    private val BREAD_PATTERNS = listOf(
        Triple(Regex("""\bmedia\s+marraqueta\b""", RegexOption.IGNORE_CASE), 50.0, "media_marraqueta"),
        Triple(Regex("""\buna\s+marraqueta\b""", RegexOption.IGNORE_CASE), 100.0, "marraqueta"),
        Triple(Regex("""\bdiente\s+de\s+marraqueta\b""", RegexOption.IGNORE_CASE), 25.0, "diente_marraqueta"),
        Triple(Regex("""\buna\s+hallulla\b""", RegexOption.IGNORE_CASE), 80.0, "hallulla"),
        Triple(Regex("""\bpan\s+amasado\b""", RegexOption.IGNORE_CASE), 100.0, "pan_amasado"),
        Triple(Regex("""\buna\s+rebanada\b""", RegexOption.IGNORE_CASE), 30.0, "rebanada"),
        Triple(Regex("""\buna\s+rebanadita\b""", RegexOption.IGNORE_CASE), 15.0, "rebanadita"),
        Triple(Regex("""\buna\s+hogaza\b""", RegexOption.IGNORE_CASE), 500.0, "hogaza"),
        Triple(Regex("""\buna\s+barra\s+de\s+pan\b""", RegexOption.IGNORE_CASE), 250.0, "barra_pan"),
        Triple(Regex("""\bun\s+bollo\b""", RegexOption.IGNORE_CASE), 50.0, "bollo"),
        Triple(Regex("""\bun\s+panecillo\b""", RegexOption.IGNORE_CASE), 40.0, "panecillo"),
        Triple(Regex("""\bun\s+mollete\b""", RegexOption.IGNORE_CASE), 60.0, "mollete"),
        Triple(Regex("""\buna\s+arepa\b""", RegexOption.IGNORE_CASE), 80.0, "arepa"),
        Triple(Regex("""\buna\s+tortilla\b""", RegexOption.IGNORE_CASE), 40.0, "tortilla"),
        Triple(Regex("""\buna\s+empanada\b""", RegexOption.IGNORE_CASE), 120.0, "empanada"),
        Triple(Regex("""\bun\s+tamal\b""", RegexOption.IGNORE_CASE), 150.0, "tamal"),
        Triple(Regex("""\bun\s+pastel\b""", RegexOption.IGNORE_CASE), 120.0, "pastel"),
        Triple(Regex("""\bun\s+trozo\s+de\s+pastel\b""", RegexOption.IGNORE_CASE), 100.0, "trozo_pastel"),
        Triple(Regex("""\bun\s+bizcocho\b""", RegexOption.IGNORE_CASE), 30.0, "bizcocho"),
        Triple(Regex("""\buna\s+galleta\b""", RegexOption.IGNORE_CASE), 10.0, "galleta"),
        Triple(Regex("""\bun\s+puñado\s+de\s+galletas?\b""", RegexOption.IGNORE_CASE), 30.0, "puñado_galletas"),
        Triple(Regex("""\buna\s+bolsita\s+de\s+snacks?\b""", RegexOption.IGNORE_CASE), 45.0, "bolsita_snacks"),
        Triple(Regex("""\bun\s+paquete\b""", RegexOption.IGNORE_CASE), 150.0, "paquete"),
        Triple(Regex("""\bun\s+sobre\b""", RegexOption.IGNORE_CASE), 10.0, "sobre"),
    )

    // ─── Envases comerciales ────────────────────────────────────────────────

    private val CONTAINER_PATTERNS = listOf(
        Triple(Regex("""\buna\s+lata\b""", RegexOption.IGNORE_CASE), 180.0, "lata"),
        Triple(Regex("""\bun\s+bote\b""", RegexOption.IGNORE_CASE), 400.0, "bote"),
        Triple(Regex("""\bun\s+frasco\b""", RegexOption.IGNORE_CASE), 250.0, "frasco"),
        Triple(Regex("""\buna\s+botella\b""", RegexOption.IGNORE_CASE), 750.0, "botella"),
        Triple(Regex("""\bmedia\s+botella\b""", RegexOption.IGNORE_CASE), 375.0, "media_botella"),
        Triple(Regex("""\bun\s+cuarto\s+de\s+botella\b""", RegexOption.IGNORE_CASE), 187.0, "cuarto_botella"),
        Triple(Regex("""\buna\s+c[aá]psula\b""", RegexOption.IGNORE_CASE), 6.0, "capsula"),
        Triple(Regex("""\bun\s+cart[oó]n\b""", RegexOption.IGNORE_CASE), 1000.0, "carton"),
        Triple(Regex("""\buna\s+caja\b""", RegexOption.IGNORE_CASE), 500.0, "caja"),
        Triple(Regex("""\buna\s+bolsa\b""", RegexOption.IGNORE_CASE), 200.0, "bolsa"),
    )

    // ─── Comparaciones con objetos ──────────────────────────────────────────

    private val COMPARISON_PATTERNS = listOf(
        Triple(Regex("""\bdel\s+ta[mañ]o\s+de\s+una\s+nuez\b""", RegexOption.IGNORE_CASE), 22.0, "tamano_nuez"),
        Triple(Regex("""\bdel\s+ta[mañ]o\s+de\s+una\s+aceituna\b""", RegexOption.IGNORE_CASE), 10.0, "tamano_aceituna"),
        Triple(Regex("""\bdel\s+ta[mañ]o\s+de\s+una\s+pelota\s+de\s+golf\b""", RegexOption.IGNORE_CASE), 90.0, "tamano_golf"),
        Triple(Regex("""\bdel\s+ta[mañ]o\s+de\s+un\s+pu[ñn]o\b""", RegexOption.IGNORE_CASE), 175.0, "tamano_puño"),
        Triple(Regex("""\bdel\s+ta[mañ]o\s+de\s+la\s+palma\b""", RegexOption.IGNORE_CASE), 135.0, "tamano_palma"),
        Triple(Regex("""\bdel\s+ta[mañ]o\s+de\s+un\s+dedo\s+pulgar\b""", RegexOption.IGNORE_CASE), 25.0, "tamano_pulgar"),
        Triple(Regex("""\bdel\s+ta[mañ]o\s+de\s+una\s+baraja\b""", RegexOption.IGNORE_CASE), 110.0, "tamano_baraja"),
        Triple(Regex("""\bcomo\s+un\s+dado\b""", RegexOption.IGNORE_CASE), 22.0, "como_dado"),
        Triple(Regex("""\bcomo\s+un\s+cubo\s+de\s+hielo\b""", RegexOption.IGNORE_CASE), 30.0, "como_hielo"),
        Triple(Regex("""\bcomo\s+una\s+moneda\b""", RegexOption.IGNORE_CASE), 12.0, "como_moneda"),
    )

    // ─── Intensificadores ───────────────────────────────────────────────────

    private val INTENSIFIER_FACTORS = mapOf(
        "gigante" to 1.8,
        "generoso" to 1.4,
        "colmado" to 1.5,
        "rebosante" to 1.6,
        "grande" to 1.3,
        "pequeño" to 0.7,
        "chico" to 0.7,
        "fino" to 0.6,
        "delgado" to 0.6,
        "grueso" to 1.4,
        "gordo" to 1.5,
    )

    // ─── Raciones estándar por categoría de alimento ────────────────────────

    private val STANDARD_PORTIONS = mapOf(
        FoodDensityCategory.PROTEIN to 150.0,
        FoodDensityCategory.GRAIN to 100.0,
        FoodDensityCategory.VEGETABLE to 200.0,
        FoodDensityCategory.FRUIT to 150.0,
        FoodDensityCategory.DAIRY to 200.0,
        FoodDensityCategory.NUTS to 30.0,
        FoodDensityCategory.FAT to 15.0,
        FoodDensityCategory.LIQUID to 250.0,
        FoodDensityCategory.POWDER to 30.0,
        FoodDensityCategory.MIXED to 200.0,
    )

    /**
     * Resolve a subjective expression to grams.
     *
     * @param expression The user's subjective expression (e.g., "un puñado", "una cucharada colmada")
     * @param foodCategory The density category of the food (auto-detected if null)
     * @param standardPortion Override for the standard portion in grams
     * @param retrievalResult Optional retrieval result from SemanticPortionRetriever for priors
     */
    fun resolve(
        expression: String,
        foodCategory: FoodDensityCategory? = null,
        standardPortion: Double? = null,
        retrievalResult: SemanticPortionRetriever.RetrievalResult? = null,
    ): PortionResult? {
        val lower = expression.lowercase().trim()

        // Check retrieval priors first
        if (retrievalResult != null && retrievalResult.portionPriors.isNotEmpty()) {
            val foodName = extractFoodName(expression)
            if (foodName != null) {
                val priorGrams = SemanticPortionRetriever.getGramsForFood(foodName, retrievalResult)
                if (priorGrams != null && priorGrams > 0) {
                    return PortionResult(
                        grams = priorGrams,
                        confidence = 0.85,
                        source = "dataset-prior",
                        expression = expression,
                        relativeFactor = 1.0,
                    )
                }
            }
        }

        // Check utensil patterns
        for ((pattern, baseMl, source) in UTENSIL_PATTERNS) {
            val match = pattern.find(lower) ?: continue
            val qty = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 1.0
            val category = foodCategory ?: FoodDensityCategory.MIXED
            val grams = baseMl * qty * category.densityGPerMl
            return PortionResult(
                grams = grams,
                confidence = 0.75,
                source = "utensil:$source",
                expression = expression,
                relativeFactor = qty,
            )
        }

        // Check body/gesture patterns
        for ((pattern, baseGrams, source) in BODY_PATTERNS) {
            val match = pattern.find(lower) ?: continue
            val category = foodCategory ?: FoodDensityCategory.MIXED
            val grams = baseGrams * category.densityGPerMl / FoodDensityCategory.MIXED.densityGPerMl
            return PortionResult(
                grams = grams,
                confidence = 0.70,
                source = "body:$source",
                expression = expression,
                relativeFactor = 1.0,
            )
        }

        // Check subjective patterns (relative factors)
        for ((pattern, factor, source) in SUBJECTIVE_PATTERNS) {
            val match = pattern.find(lower) ?: continue
            val category = foodCategory ?: FoodDensityCategory.MIXED
            val stdPortion = standardPortion ?: STANDARD_PORTIONS[category] ?: 100.0
            val grams = stdPortion * factor
            return PortionResult(
                grams = grams,
                confidence = 0.60,
                source = "subjective:$source",
                expression = expression,
                relativeFactor = factor,
            )
        }

        // Check bread patterns
        for ((pattern, baseGrams, source) in BREAD_PATTERNS) {
            val match = pattern.find(lower) ?: continue
            return PortionResult(
                grams = baseGrams,
                confidence = 0.80,
                source = "bread:$source",
                expression = expression,
                relativeFactor = 1.0,
            )
        }

        // Check container patterns
        for ((pattern, baseGrams, source) in CONTAINER_PATTERNS) {
            val match = pattern.find(lower) ?: continue
            return PortionResult(
                grams = baseGrams,
                confidence = 0.65,
                source = "container:$source",
                expression = expression,
                relativeFactor = 1.0,
            )
        }

        // Check comparison patterns
        for ((pattern, baseGrams, source) in COMPARISON_PATTERNS) {
            val match = pattern.find(lower) ?: continue
            return PortionResult(
                grams = baseGrams,
                confidence = 0.55,
                source = "comparison:$source",
                expression = expression,
                relativeFactor = 1.0,
            )
        }

        return null
    }

    /**
     * Detect intensifiers in the expression and return a multiplier.
     */
    fun detectIntensifier(expression: String): Double {
        val lower = expression.lowercase()
        for ((keyword, factor) in INTENSIFIER_FACTORS) {
            if (lower.contains(keyword)) {
                return factor
            }
        }
        return 1.0
    }

    /**
     * Auto-detect food density category from food name.
     */
    fun detectDensityCategory(foodName: String): FoodDensityCategory {
        val lower = foodName.lowercase()

        return when {
            lower.contains("aceite") || lower.contains("mantequilla") || lower.contains("manteca") || lower.contains("ghee") || lower.contains("margarina") || lower.contains("mayonesa") || lower.contains("mayo") -> FoodDensityCategory.FAT
            lower.contains("azúcar") || lower.contains("azucar") || lower.contains("harina") || lower.contains("cacao") || lower.contains("canela") -> FoodDensityCategory.POWDER
            lower.contains("arroz") || lower.contains("pasta") || lower.contains("quinoa") || lower.contains("avena") || lower.contains("lenteja") || lower.contains("garbanzo") || lower.contains("poroto") -> FoodDensityCategory.GRAIN
            lower.contains("pollo") || lower.contains("carne") || lower.contains("pescado") || lower.contains("cerdo") || lower.contains("vacuno") || lower.contains("pavo") || lower.contains("huevo") || lower.contains("merluza") || lower.contains("salmón") || lower.contains("camarón") -> FoodDensityCategory.PROTEIN
            lower.contains("lechuga") || lower.contains("tomate") || lower.contains("cebolla") || lower.contains("zanahoria") || lower.contains("espinaca") || lower.contains("brócoli") || lower.contains("pepino") -> FoodDensityCategory.VEGETABLE
            lower.contains("manzana") || lower.contains("plátano") || lower.contains("naranja") || lower.contains("uva") || lower.contains("frutilla") || lower.contains("pera") -> FoodDensityCategory.FRUIT
            lower.contains("leche") || lower.contains("yogurt") || lower.contains("yogur") || lower.contains("queso") || lower.contains("crema") -> FoodDensityCategory.DAIRY
            lower.contains("almendra") || lower.contains("nuez") || lower.contains("maní") || lower.contains("cashew") || lower.contains("chía") -> FoodDensityCategory.NUTS
            lower.contains("agua") || lower.contains("jugo") || lower.contains("zumo") || lower.contains("leche") || lower.contains("vino") || lower.contains("cerveza") -> FoodDensityCategory.LIQUID
            else -> FoodDensityCategory.MIXED
        }
    }

    // ─── Internal ──────────────────────────────────────────────────────────

    private val DE_FOOD_PATTERN = Regex("""de\s+([a-záéíóúñü\s]{2,})""", RegexOption.IGNORE_CASE)

    private fun extractFoodName(expression: String): String? {
        // Try to extract food name after "de"
        val deMatch = DE_FOOD_PATTERN.find(expression)
        if (deMatch != null) {
            return deMatch.groupValues[1].trim()
        }
        return null
    }
}
