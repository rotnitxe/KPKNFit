package com.example.kpkn.data.food

import android.content.Context
import com.example.kpkn.data.models.CarbBreakdown
import com.example.kpkn.data.models.FoodItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Curated Hispanic-America snack catalog (branded + homemade typicals).
 * Loaded from assets JSON and merged with the programmatic core.
 */
object BrandedSnackCatalog {

    private const val ASSET_PATH = "food_data/branded_snack_catalog.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CatalogEntry(
        val id: String,
        val name: String,
        val brand: String? = null,
        val category: String = "snack",
        val servingSize: Double = 100.0,
        val servingUnit: String = "g",
        val unit: String = "g",
        val calories: Double = 0.0,
        val protein: Double = 0.0,
        val carbs: Double = 0.0,
        val fats: Double = 0.0,
        val sugar: Double = 0.0,
        val sodiumMg: Double = 0.0,
        val regions: List<String> = emptyList(),
        val searchAliases: List<String> = emptyList(),
        val nutritionBasis: String = "PER_100G",
    )

    @Serializable
    private data class CatalogFile(val items: List<CatalogEntry> = emptyList())

    fun load(context: Context? = null): List<FoodItem> {
        val fromAsset = context?.let { loadFromAsset(it) }.orEmpty()
        val programmatic = buildProgrammaticCatalog()
        return (fromAsset + programmatic).distinctBy { it.id }
    }

    private fun loadFromAsset(context: Context): List<FoodItem> = runCatching {
        context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
            val file = json.decodeFromString<CatalogFile>(reader.readText())
            file.items.map { it.toFoodItem() }
        }
    }.getOrElse { emptyList() }

    fun buildProgrammaticCatalog(): List<FoodItem> = buildList {
        addAll(mexico())
        addAll(centralNorth())
        addAll(caribbean())
        addAll(andino())
        addAll(conoSur())
        addAll(panLatamBrands())
        addAll(caseros())
        addAll(coverageFill())
    }

    private fun CatalogEntry.toFoodItem() = item(
        id = id,
        name = name,
        brand = brand,
        category = category,
        serving = servingSize,
        kcal = calories,
        protein = protein,
        carbs = carbs,
        fats = fats,
        sugar = sugar,
        regions = regions,
        aliases = searchAliases,
        basis = nutritionBasis,
        unit = unit,
    )

    private fun item(
        id: String,
        name: String,
        brand: String?,
        category: String,
        serving: Double,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
        sugar: Double = 0.0,
        regions: List<String>,
        aliases: List<String>,
        basis: String = "PER_100G",
        unit: String = "g",
    ) = FoodItem(
        id = id,
        name = name,
        brand = brand,
        category = category,
        servingSize = serving,
        servingUnit = unit,
        unit = unit,
        calories = kcal,
        protein = protein,
        carbs = carbs,
        fats = fats,
        carbBreakdown = CarbBreakdown(sugar = sugar),
        tags = listOf(category, "snack") + regions,
        searchAliases = (aliases + listOfNotNull(brand?.lowercase(), name.lowercase())).distinct(),
        source = "KPKN Curated",
        sourcePriority = 88,
        verifiedScore = 0.78,
        nutritionBasis = basis,
    )

    private fun cookie(
        id: String,
        name: String,
        brand: String,
        regions: List<String>,
        aliases: List<String> = emptyList(),
        kcal: Double = 490.0,
        p: Double = 6.0,
        c: Double = 68.0,
        f: Double = 22.0,
        sugar: Double = 28.0,
    ) = item(id, name, brand, "galletas", 100.0, kcal, p, c, f, sugar, regions, aliases)

    private fun chips(
        id: String,
        name: String,
        brand: String,
        regions: List<String>,
        aliases: List<String> = emptyList(),
        kcal: Double = 536.0,
        p: Double = 6.5,
        c: Double = 53.0,
        f: Double = 34.0,
        sugar: Double = 0.5,
    ) = item(id, name, brand, "chips", 100.0, kcal, p, c, f, sugar, regions, aliases)

    private fun candy(
        id: String,
        name: String,
        brand: String,
        regions: List<String>,
        aliases: List<String> = emptyList(),
        kcal: Double = 480.0,
        p: Double = 4.0,
        c: Double = 62.0,
        f: Double = 24.0,
        sugar: Double = 48.0,
    ) = item(id, name, brand, "dulce", 100.0, kcal, p, c, f, sugar, regions, aliases)

    private fun homemade(
        id: String,
        name: String,
        regions: List<String>,
        aliases: List<String>,
        kcal: Double,
        p: Double,
        c: Double,
        f: Double,
        sugar: Double = 10.0,
        category: String = "casero",
    ) = item(id, name, null, category, 100.0, kcal, p, c, f, sugar, regions, aliases)

    private fun mexico() = listOf(
        chips("sn_mx_sabritas_clasicas", "Sabritas Clásicas", "Sabritas", listOf("MX"), listOf("sabritas", "papas sabritas")),
        chips("sn_mx_doritos_nacho", "Doritos Nacho Cheese", "Doritos", listOf("MX", "LATAM"), listOf("doritos", "doritos nacho")),
        chips("sn_mx_takis_fuego", "Takis Fuego", "Takis", listOf("MX", "LATAM"), listOf("takis", "takis fuego")),
        chips("sn_mx_cheetos", "Cheetos Torciditos", "Cheetos", listOf("MX", "LATAM"), listOf("cheetos", "torciditos")),
        cookie("sn_mx_gamesa_emperador", "Emperador Chocolate", "Gamesa", listOf("MX"), listOf("emperador", "gamesa emperador")),
        cookie("sn_mx_chokis", "Chokis", "Gamesa", listOf("MX"), listOf("chokis", "galletas chokis")),
        cookie("sn_mx_marias", "Galletas Marías", "Gamesa", listOf("MX"), listOf("marias", "galletas marias", "marías"), kcal = 430.0, f = 10.0, sugar = 18.0),
        candy("sn_mx_gansito", "Gansito", "Marinela", listOf("MX"), listOf("gansito", "marinela gansito"), kcal = 420.0, c = 52.0, f = 20.0),
        candy("sn_mx_carlosv", "Carlos V", "Nestlé", listOf("MX"), listOf("carlos v", "carlos quinto")),
        candy("sn_mx_pulparindo", "Pulparindo", "De la Rosa", listOf("MX"), listOf("pulparindo"), kcal = 320.0, p = 1.0, c = 78.0, f = 1.0, sugar = 50.0),
        candy("sn_mx_pelon", "Pelon Pelo Rico", "Lorena", listOf("MX"), listOf("pelon", "pelon pelo rico"), kcal = 310.0, p = 0.5, c = 76.0, f = 0.5, sugar = 55.0),
        homemade("sn_mx_churro", "Churro mexicano", listOf("MX"), listOf("churro", "churros"), 410.0, 5.0, 48.0, 22.0, 12.0),
        homemade("sn_mx_elote", "Elote con chile y mayo", listOf("MX"), listOf("elote", "elote preparado", "esquite"), 180.0, 4.0, 22.0, 8.0, 6.0, "casero"),
        homemade("sn_mx_chicharron", "Chicharrón", listOf("MX"), listOf("chicharron", "chicharrón"), 570.0, 45.0, 0.0, 42.0, 0.0),
    )

    private fun centralNorth() = listOf(
        // GT, SV, HN, NI, CR, PA
        cookie("sn_gt_galleta_canela", "Galletas de canela", "Local", listOf("GT"), listOf("galleta de canela guatemala")),
        chips("sn_gt_senoritas", "Señorial Papas", "Señorial", listOf("GT"), listOf("señorial", "senorial")),
        candy("sn_sv_horchata_snack", "Semita salvadoreña", "Casero", listOf("SV"), listOf("semita"), kcal = 380.0, p = 7.0, c = 62.0, f = 10.0, sugar = 18.0),
        cookie("sn_hn_dixie", "Galletas Dixie", "Dixie", listOf("HN"), listOf("dixie")),
        candy("sn_ni_cajeta", "Cajeta nicaragüense", "Casero", listOf("NI"), listOf("cajeta"), kcal = 320.0, p = 5.0, c = 62.0, f = 6.0, sugar = 55.0),
        cookie("sn_cr_pozuelo_trelacreme", "Pozuelo Trelécreme", "Pozuelo", listOf("CR"), listOf("pozuelo", "trelecreme", "trelécreme")),
        chips("sn_cr_jacks", "Jack's Snacks", "Jack's", listOf("CR"), listOf("jacks", "jack's")),
        candy("sn_pa_flips", "Flips", "Productos Alimenticios", listOf("PA"), listOf("flips panama", "flips")),
        homemade("sn_sv_pupusa_snack", "Pupusa (porción snack)", listOf("SV"), listOf("pupusa"), 230.0, 8.0, 28.0, 9.0, 1.0),
        homemade("sn_cr_chilero_chips", "Platanitos fritos", listOf("CR", "PA", "NI"), listOf("platanitos", "chifles", "patacones snack"), 520.0, 3.0, 58.0, 32.0, 2.0),
        cookie("sn_gt_galleta_maria", "Galletas María", "Local", listOf("GT", "SV", "HN"), listOf("galletas maria centroamerica"), kcal = 430.0, f = 10.0, sugar = 18.0),
        candy("sn_hn_banano_chip", "Chips de banano", "Local", listOf("HN", "CR"), listOf("chips de banano", "banana chips"), kcal = 519.0, p = 2.3, c = 58.0, f = 34.0, sugar = 35.0),
    )

    private fun caribbean() = listOf(
        cookie("sn_cu_galleta_soda", "Galletas soda cubanas", "Local", listOf("CU"), listOf("galleta soda"), kcal = 420.0, f = 12.0, sugar = 8.0),
        candy("sn_cu_turron", "Turrón de maní cubano", "Casero", listOf("CU"), listOf("turron de mani", "turrón cubano"), kcal = 480.0, p = 12.0, c = 48.0, f = 26.0, sugar = 36.0),
        homemade("sn_cu_churros", "Churros cubanos", listOf("CU"), listOf("churros cuba"), 400.0, 5.0, 46.0, 22.0, 10.0),
        cookie("sn_do_guarina", "Galletas Guarina", "Guarina", listOf("DO"), listOf("guarina")),
        chips("sn_do_frito_lay", "Frito Lay Clásicas", "Frito-Lay", listOf("DO"), listOf("frito lay")),
        candy("sn_do_toasted", "Toasted ( helado paleta )", "Local", listOf("DO"), listOf("toasted dominicana"), kcal = 220.0, p = 3.0, c = 28.0, f = 10.0, sugar = 22.0),
        homemade("sn_do_yaniqueque", "Yaniqueque", listOf("DO"), listOf("yaniqueque", "janiqueque"), 430.0, 6.0, 52.0, 22.0, 4.0),
        homemade("sn_do_quipes", "Quipe frito", listOf("DO"), listOf("quipe", "kipes"), 280.0, 8.0, 24.0, 16.0, 1.0),
        candy("sn_cu_bocadito", "Bocadito de coco", "Casero", listOf("CU"), listOf("bocadito de coco"), 420.0, 3.0, 58.0, 20.0, 48.0),
    )

    private fun andino() = listOf(
        cookie("sn_co_saltin_noel", "Saltín Noel", "Noel", listOf("CO"), listOf("saltin", "saltín noel", "saltin noel"), kcal = 430.0, f = 11.0, sugar = 6.0),
        cookie("sn_co_ducales", "Ducales", "Noel", listOf("CO"), listOf("ducales"), kcal = 480.0, f = 22.0, sugar = 4.0),
        candy("sn_co_chocoramo", "Chocoramo", "Ramo", listOf("CO"), listOf("chocoramo", "ramo"), kcal = 430.0, p = 5.0, c = 55.0, f = 20.0, sugar = 28.0),
        chips("sn_co_detodito", "De Todito Mix", "Detodito", listOf("CO"), listOf("de todito", "detodito")),
        candy("sn_co_jet", "Chocolatina Jet", "Jet", listOf("CO"), listOf("jet", "chocolatina jet")),
        cookie("sn_ve_club_social", "Club Social", "Nabisco", listOf("VE", "LATAM"), listOf("club social"), kcal = 470.0, f = 20.0, sugar = 4.0),
        candy("sn_ve_samba", "Samba", "Nestlé", listOf("VE"), listOf("samba venezuela", "samba")),
        candy("sn_ve_susy", "Susy", "Nestlé", listOf("VE"), listOf("susy")),
        candy("sn_ve_pepito", "Pepito", "Nestlé", listOf("VE"), listOf("pepito galleta")),
        homemade("sn_ve_tequenos", "Tequeños", listOf("VE"), listOf("tequeños", "tequenos"), 360.0, 10.0, 32.0, 20.0, 2.0),
        cookie("sn_ec_galletas_amore", "Amor Galletas", "Nestlé", listOf("EC"), listOf("galletas amor ecuador")),
        chips("sn_ec_chifles", "Chifles", "Local", listOf("EC", "PE"), listOf("chifles", "chifles de platano")),
        cookie("sn_pe_field_casino", "Casino Field", "Field", listOf("PE"), listOf("casino", "galletas casino", "field casino")),
        cookie("sn_pe_morochas", "Morochas", "Nestlé", listOf("PE"), listOf("morochas")),
        cookie("sn_pe_glacitas", "Glacitas", "Field", listOf("PE"), listOf("glacitas")),
        candy("sn_pe_sublime", "Sublime", "Nestlé", listOf("PE"), listOf("sublime")),
        candy("sn_pe_princesa", "Princesa", "Nestlé", listOf("PE"), listOf("princesa chocolate")),
        chips("sn_pe_piqueo", "Piqueo Snax", "Frito-Lay", listOf("PE"), listOf("piqueo", "piqueo snax")),
        homemade("sn_pe_picarones", "Picarones", listOf("PE"), listOf("picarones"), 280.0, 4.0, 48.0, 8.0, 18.0),
        cookie("sn_bo_gallestinas", "Galletas María", "Local", listOf("BO"), listOf("galletas maria bolivia"), kcal = 430.0, f = 10.0, sugar = 18.0),
        homemade("sn_bo_tucumanas", "Salteñas (porción)", listOf("BO"), listOf("salteña", "saltenas"), 250.0, 10.0, 28.0, 10.0, 3.0),
        homemade("sn_co_bunuelos", "Buñuelos", listOf("CO"), listOf("buñuelos", "bunuelos"), 370.0, 8.0, 40.0, 18.0, 6.0),
        homemade("sn_ec_empanadas_verde", "Empanadas de verde", listOf("EC"), listOf("empanadas de verde"), 260.0, 6.0, 32.0, 12.0, 1.0),
    )

    private fun conoSur() = listOf(
        cookie("sn_cl_triton", "Tritón", "Costa", listOf("CL"), listOf("triton", "tritón", "costa triton", "galletas triton")),
        candy("sn_cl_super8", "Super 8", "Costa", listOf("CL"), listOf("super 8", "super8")),
        cookie("sn_cl_mckay_dona", "McKay Donas", "McKay", listOf("CL"), listOf("mckay", "donas mckay")),
        chips("sn_cl_ramitas", "Ramitas", "Evercrisp", listOf("CL"), listOf("ramitas", "evercrisp ramitas")),
        chips("sn_cl_kryspo", "Kryspo", "Evercrisp", listOf("CL"), listOf("kryspo")),
        candy("sn_cl_sahne_nuss", "Sahne Nuss", "Nestlé", listOf("CL"), listOf("sahne nuss", "sahnenuss")),
        candy("sn_cl_ambrosoli_frugele", "Frugelé", "Ambrosoli", listOf("CL"), listOf("frugele", "frugelé", "ambrosoli"), kcal = 350.0, p = 0.0, c = 85.0, f = 0.0, sugar = 70.0),
        cookie("sn_ar_pepitos", "Pepitos", "Bagley", listOf("AR"), listOf("pepitos", "galletas pepitos")),
        cookie("sn_ar_rumba", "Rumba", "Bagley", listOf("AR"), listOf("rumba galletas")),
        cookie("sn_ar_criollitas", "Criollitas", "Bagley", listOf("AR"), listOf("criollitas"), kcal = 440.0, f = 12.0, sugar = 4.0),
        candy("sn_ar_bonobon", "Bon o Bon", "Arcor", listOf("AR", "LATAM"), listOf("bon o bon", "bonobon")),
        candy("sn_ar_alfajor_havanna", "Alfajor Havanna", "Havanna", listOf("AR"), listOf("alfajor havanna", "havanna"), kcal = 410.0, p = 6.0, c = 52.0, f = 18.0, sugar = 32.0),
        candy("sn_ar_rhodesia", "Rhodesia", "Bagley", listOf("AR"), listOf("rhodesia")),
        cookie("sn_uy_alfajor_punta", "Alfajor Punta Ballena", "Local", listOf("UY"), listOf("alfajor uruguay"), kcal = 400.0, p = 6.0, c = 50.0, f = 18.0, sugar = 30.0),
        chips("sn_uy_lays", "Lays Clásicas", "Lays", listOf("UY", "AR", "CL"), listOf("lays", "lays clasicas")),
        cookie("sn_py_calza", "Galletas Calza", "Local", listOf("PY"), listOf("calza paraguay")),
        homemade("sn_py_chipa", "Chipa", listOf("PY"), listOf("chipa", "chipá"), 330.0, 10.0, 36.0, 14.0, 2.0),
        homemade("sn_ar_alfajor_casero", "Alfajor casero", listOf("AR", "UY"), listOf("alfajor casero", "alfajor"), 390.0, 6.0, 52.0, 16.0, 30.0),
        homemade("sn_cl_completo_snack", "Completo (mini)", listOf("CL"), listOf("completo chileno mini"), 260.0, 9.0, 24.0, 14.0, 3.0),
        homemade("sn_cl_berlin", "Berlín relleno", listOf("CL"), listOf("berlin", "berlín"), 380.0, 6.0, 48.0, 18.0, 16.0),
        homemade("sn_ar_facturas", "Facturas", listOf("AR", "UY"), listOf("facturas", "medialunas snack"), 400.0, 7.0, 48.0, 20.0, 12.0),
    )

    private fun panLatamBrands() = listOf(
        cookie("sn_oreo_original", "Galletas Oreo Original", "Oreo", listOf("LATAM"), listOf("oreo", "galletas oreo", "oreos")),
        chips("sn_lays_clasicas", "Lays Clásicas", "Lays", listOf("LATAM"), listOf("lays clasicas", "papas lays")),
        chips("sn_pringles", "Pringles Original", "Pringles", listOf("LATAM"), listOf("pringles")),
        candy("sn_mms", "M&M's", "Mars", listOf("LATAM"), listOf("m&ms", "emes", "m and ms")),
        candy("sn_snickers", "Snickers", "Mars", listOf("LATAM"), listOf("snickers")),
        cookie("sn_kitkat", "KitKat", "Nestlé", listOf("LATAM"), listOf("kitkat", "kit kat"), kcal = 518.0, p = 6.0, c = 65.0, f = 26.0, sugar = 50.0),
        candy("sn_ferrero", "Ferrero Rocher", "Ferrero", listOf("LATAM"), listOf("ferrero", "rocher"), kcal = 580.0, p = 8.0, c = 44.0, f = 42.0, sugar = 40.0),
    )

    private fun caseros() = listOf(
        homemade("sn_latam_churro", "Churro", listOf("LATAM"), listOf("churro", "churros"), 410.0, 5.0, 48.0, 22.0, 12.0),
        homemade("sn_latam_paleta_chile", "Paleta de chile", listOf("MX", "GT", "SV"), listOf("paleta de chile", "paleta pica"), 280.0, 0.0, 70.0, 0.0, 55.0),
        homemade("sn_latam_mazapan", "Mazapán", listOf("MX", "ES", "LATAM"), listOf("mazapan", "mazapán"), 450.0, 10.0, 52.0, 22.0, 40.0),
        homemade("sn_latam_cocada", "Cocada", listOf("LATAM"), listOf("cocada"), 420.0, 3.5, 50.0, 24.0, 42.0),
        homemade("sn_latam_rosquilla", "Rosquillas", listOf("NI", "SV", "HN"), listOf("rosquilla", "rosquillas"), 430.0, 6.0, 58.0, 18.0, 16.0),
        homemade("sn_latam_bunuelo", "Buñuelo", listOf("CO", "MX", "ES"), listOf("buñuelo", "bunuelo"), 370.0, 8.0, 40.0, 18.0, 6.0),
    )

    /** Typical homemade/branded fillers so each Hispanic country has 8–12 curated rows. */
    private fun coverageFill() = listOf(
        homemade("sn_gt_rellenito", "Rellenitos de plátano", listOf("GT"), listOf("rellenitos", "rellenitos de platano"), 280.0, 3.0, 48.0, 9.0, 18.0),
        homemade("sn_gt_chuchito", "Chuchitos", listOf("GT"), listOf("chuchito", "chuchitos"), 240.0, 8.0, 28.0, 10.0, 2.0),
        homemade("sn_gt_tostada", "Tostada chapina", listOf("GT"), listOf("tostada chapina", "tostadas chapinas"), 220.0, 6.0, 22.0, 12.0, 2.0),
        candy("sn_gt_canillitas", "Canillitas de leche", "Local", listOf("GT"), listOf("canillitas de leche", "canillitas"), kcal = 380.0, p = 6.0, c = 70.0, f = 8.0, sugar = 60.0),
        homemade("sn_sv_yuca", "Yuca frita", listOf("SV"), listOf("yuca frita salvadoreña"), 250.0, 2.0, 38.0, 10.0, 1.0),
        homemade("sn_sv_nuegados", "Nuegados", listOf("SV"), listOf("nuegados"), 360.0, 4.0, 55.0, 14.0, 28.0),
        homemade("sn_sv_empanada_platano", "Empanadas de plátano salvadoreñas", listOf("SV"), listOf("empanadas de platano el salvador"), 270.0, 5.0, 36.0, 12.0, 8.0),
        candy("sn_sv_maria_luisa", "María Luisa", "Casero", listOf("SV"), listOf("maria luisa salvadoreña"), kcal = 380.0, p = 5.0, c = 58.0, f = 14.0, sugar = 32.0),
        homemade("sn_hn_baleada", "Baleada (porción snack)", listOf("HN"), listOf("baleada", "baleadas"), 280.0, 10.0, 36.0, 10.0, 2.0),
        homemade("sn_hn_tajadas", "Tajadas de plátano hondureñas", listOf("HN"), listOf("tajadas de platano honduras"), 240.0, 1.5, 36.0, 11.0, 12.0),
        homemade("sn_hn_pastelito", "Pastelitos hondureños", listOf("HN"), listOf("pastelitos hondurenos"), 290.0, 7.0, 28.0, 16.0, 2.0),
        candy("sn_hn_pan_coco", "Pan de coco hondureño", "Local", listOf("HN"), listOf("pan de coco honduras"), kcal = 360.0, p = 6.0, c = 52.0, f = 14.0, sugar = 14.0),
        homemade("sn_ni_quesillo", "Quesillo nicaragüense", listOf("NI"), listOf("quesillo nicaragua"), 200.0, 12.0, 4.0, 16.0, 3.0),
        homemade("sn_ni_guirila", "Güirilas", listOf("NI"), listOf("guirilas", "güirilas"), 220.0, 5.0, 36.0, 7.0, 8.0),
        homemade("sn_ni_viejita", "Viejitas", listOf("NI"), listOf("viejitas nicaragua"), 380.0, 5.0, 48.0, 18.0, 22.0),
        candy("sn_ni_pioquinto", "Pío quinto", "Casero", listOf("NI"), listOf("pio quinto", "pío quinto"), kcal = 280.0, p = 5.0, c = 48.0, f = 8.0, sugar = 30.0),
        homemade("sn_ni_gofio", "Gofio nicaragüense", listOf("NI"), listOf("gofio nicaragua"), 380.0, 8.0, 62.0, 10.0, 8.0),
        candy("sn_pe_chocoteja", "Chocotejas", "Local", listOf("PE"), listOf("chocoteja", "chocotejas")),
        cookie("sn_cr_tanricas", "Galletas Tán Ricas", "Pozuelo", listOf("CR"), listOf("tan ricas", "tán ricas")),
        homemade("sn_cr_chifrijo", "Chifrijo (porción snack)", listOf("CR"), listOf("chifrijo"), 220.0, 12.0, 18.0, 10.0, 1.0),
        candy("sn_cr_tresleches", "Tres leches (porción)", "Casero", listOf("CR"), listOf("tres leches costa rica"), kcal = 280.0, p = 5.0, c = 38.0, f = 12.0, sugar = 28.0),
        homemade("sn_cr_tamal_asado", "Tamal asado", listOf("CR"), listOf("tamal asado"), 240.0, 6.0, 32.0, 9.0, 6.0),
        homemade("sn_pa_carimanola", "Carimañola", listOf("PA"), listOf("carimanola", "carimañola"), 250.0, 7.0, 28.0, 12.0, 2.0),
        homemade("sn_pa_hojaldre", "Hojaldres panameños", listOf("PA"), listOf("hojaldres panama"), 360.0, 6.0, 42.0, 18.0, 6.0),
        homemade("sn_pa_duros", "Duros de harina", listOf("PA"), listOf("duros de harina"), 420.0, 7.0, 58.0, 18.0, 2.0),
        homemade("sn_pa_empanada", "Empanadas panameñas", listOf("PA"), listOf("empanadas panama"), 270.0, 8.0, 28.0, 14.0, 1.0),
        candy("sn_pa_cocada", "Cocada panameña", "Casero", listOf("PA"), listOf("cocada panama"), kcal = 420.0, p = 3.0, c = 50.0, f = 24.0, sugar = 42.0),
        cookie("sn_pa_galletas_soda", "Galletas soda panameñas", "Local", listOf("PA"), listOf("galletas soda panama"), kcal = 420.0, f = 12.0, sugar = 6.0),
        homemade("sn_cu_croqueta", "Croquetas cubanas", listOf("CU"), listOf("croquetas cubanas"), 250.0, 9.0, 18.0, 14.0, 1.0),
        chips("sn_cu_mariquitas", "Mariquitas", "Local", listOf("CU"), listOf("mariquitas cubanas", "chicharritas cubanas"), kcal = 520.0, p = 3.0, c = 58.0, f = 32.0),
        homemade("sn_cu_tostones", "Tostones cubanos", listOf("CU"), listOf("tostones cuba"), 230.0, 1.5, 32.0, 12.0, 1.0),
        candy("sn_cu_merenguito", "Merenguitos", "Casero", listOf("CU"), listOf("merenguitos cubanos"), kcal = 390.0, p = 2.0, c = 90.0, f = 0.0, sugar = 85.0),
        homemade("sn_do_empanada", "Empanadas dominicanas", listOf("DO"), listOf("empanadas dominicanas"), 270.0, 8.0, 28.0, 14.0, 1.0),
        candy("sn_do_jalao", "Jalao", "Casero", listOf("DO"), listOf("jalao"), kcal = 400.0, p = 3.0, c = 62.0, f = 16.0, sugar = 48.0),
        candy("sn_do_coconete", "Coconete", "Casero", listOf("DO"), listOf("coconete"), kcal = 430.0, p = 4.0, c = 52.0, f = 22.0, sugar = 36.0),
        chips("sn_co_margarita", "Papas Margarita", "Margarita", listOf("CO"), listOf("papas margarita", "margarita papas")),
        candy("sn_co_bocadillo", "Bocadillo veleño", "Local", listOf("CO"), listOf("bocadillo veleno", "bocadillo veleño"), kcal = 320.0, p = 0.5, c = 80.0, f = 0.2, sugar = 70.0),
        candy("sn_ve_savoy", "Chocolate Savoy", "Savoy", listOf("VE"), listOf("savoy", "chocolate savoy")),
        candy("sn_ve_carmelita", "Carmelita", "Savoy", listOf("VE"), listOf("carmelita savoy")),
        homemade("sn_ve_arepita", "Arepitas dulces", listOf("VE"), listOf("arepitas dulces"), 280.0, 5.0, 42.0, 10.0, 14.0),
        homemade("sn_ec_viento", "Empanadas de viento", listOf("EC"), listOf("empanadas de viento"), 280.0, 7.0, 30.0, 14.0, 2.0),
        homemade("sn_ec_humita", "Humitas ecuatorianas", listOf("EC"), listOf("humitas ecuador"), 180.0, 5.0, 24.0, 6.0, 4.0),
        homemade("sn_ec_muchin", "Muchines de yuca", listOf("EC"), listOf("muchines", "muchin de yuca"), 240.0, 5.0, 36.0, 8.0, 2.0),
        cookie("sn_ec_galletas_dulce", "Galletas dulces ecuatorianas", "Local", listOf("EC"), listOf("galletas dulces ecuador")),
        homemade("sn_ec_churro", "Churros ecuatorianos", listOf("EC"), listOf("churros ecuador"), 410.0, 5.0, 48.0, 22.0, 12.0),
        homemade("sn_bo_cunape", "Cuñapé", listOf("BO"), listOf("cunape", "cuñape", "cuñapé"), 320.0, 9.0, 36.0, 14.0, 2.0),
        homemade("sn_bo_huminta", "Humintas bolivianas", listOf("BO"), listOf("humintas bolivia"), 190.0, 5.0, 26.0, 6.0, 5.0),
        homemade("sn_bo_sonso", "Sonso de yuca", listOf("BO"), listOf("sonso de yuca"), 230.0, 4.0, 38.0, 7.0, 8.0),
        homemade("sn_bo_tawa", "Tawa tawas", listOf("BO"), listOf("tawa tawa", "tawa tawas"), 360.0, 5.0, 42.0, 18.0, 12.0),
        homemade("sn_bo_empanada_queso", "Empanada de queso boliviana", listOf("BO"), listOf("empanada de queso bolivia"), 270.0, 8.0, 28.0, 14.0, 2.0),
        candy("sn_bo_rosquete", "Rosquetes bolivianos", "Casero", listOf("BO"), listOf("rosquetes bolivia"), kcal = 400.0, p = 6.0, c = 62.0, f = 14.0, sugar = 22.0),
        homemade("sn_uy_bizcochos", "Bizcochos uruguayos", listOf("UY"), listOf("bizcochos uruguayos"), 400.0, 7.0, 48.0, 20.0, 10.0),
        cookie("sn_uy_famosa", "Galletas Famosa", "Famosa", listOf("UY"), listOf("famosa galletas uruguay")),
        candy("sn_uy_conitos", "Conitos de dulce de leche", "Local", listOf("UY"), listOf("conitos uruguay"), kcal = 430.0, p = 5.0, c = 54.0, f = 22.0, sugar = 40.0),
        chips("sn_uy_papas_pay", "Papas pay", "Local", listOf("UY"), listOf("papas pay uruguay")),
        homemade("sn_py_mbeju", "Mbeju", listOf("PY"), listOf("mbeju", "mbeyu"), 340.0, 6.0, 40.0, 16.0, 2.0),
        homemade("sn_py_sopa", "Sopa paraguaya (porción)", listOf("PY"), listOf("sopa paraguaya"), 250.0, 8.0, 28.0, 12.0, 3.0),
        homemade("sn_py_chipa_soo", "Chipa so'o", listOf("PY"), listOf("chipa soo", "chipa so'o"), 300.0, 10.0, 32.0, 14.0, 2.0),
        homemade("sn_py_pajagua", "Pajagua mastín", listOf("PY"), listOf("pajagua"), 280.0, 12.0, 22.0, 16.0, 1.0),
        candy("sn_py_kai", "Ka'i ladrillo", "Casero", listOf("PY"), listOf("kai ladrillo", "ka'i ladrillo"), kcal = 380.0, p = 4.0, c = 70.0, f = 10.0, sugar = 55.0),
        homemade("sn_py_chipa_guasu", "Chipa guazú (porción)", listOf("PY"), listOf("chipa guasu", "chipa guazú"), 230.0, 8.0, 24.0, 12.0, 3.0),
        cookie("sn_cl_costa_fruta", "Galletas Costa Fruta", "Costa", listOf("CL"), listOf("costa fruta", "galletas costa fruta")),
        cookie("sn_ar_sonrisas", "Sonrisas", "Bagley", listOf("AR"), listOf("sonrisas", "galletas sonrisas")),
        cookie("sn_mx_canelitas", "Canelitas", "Gamesa", listOf("MX"), listOf("canelitas", "galletas canelitas")),
        candy("sn_cl_dos_en_uno", "Dos en Uno", "Costa", listOf("CL"), listOf("dos en uno", "2 en 1 costa"), kcal = 480.0, p = 5.0, c = 58.0, f = 24.0, sugar = 42.0),
        cookie("sn_pe_field_charada", "Charada Field", "Field", listOf("PE"), listOf("charada", "galletas charada")),
    )
}
