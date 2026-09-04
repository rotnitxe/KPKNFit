package com.example.kpkn.data.food

import android.content.Context
import com.example.kpkn.data.models.CarbBreakdown
import com.example.kpkn.data.models.FoodItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Curated catalog of branded energy drinks and kcal supplements (LATAM/EU/NA).
 * Loaded from assets JSON; falls back to programmatic templates if asset missing.
 */
object BrandedEnergyKcalCatalog {

    private const val ASSET_PATH = "food_data/branded_energy_kcal_catalog.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CatalogEntry(
        val id: String,
        val name: String,
        val brand: String? = null,
        val category: String = "bebida_energetica",
        val servingSize: Double = 250.0,
        val servingUnit: String = "ml",
        val unit: String = "ml",
        val calories: Double = 0.0,
        val protein: Double = 0.0,
        val carbs: Double = 0.0,
        val fats: Double = 0.0,
        val sugar: Double = 0.0,
        val sodiumMg: Double = 0.0,
        val caffeineMg: Double = 0.0,
        val creatineG: Double = 0.0,
        val regions: List<String> = emptyList(),
        val searchAliases: List<String> = emptyList(),
        val nutritionBasis: String = "PER_SERVING",
    )

    @Serializable
    private data class CatalogFile(val items: List<CatalogEntry> = emptyList())

    fun load(context: Context? = null): List<FoodItem> {
        val fromAsset = context?.let { loadFromAsset(it) }.orEmpty()
        val programmatic = buildProgrammaticCatalog()
        val merged = (fromAsset + programmatic).distinctBy { it.id }
        return merged
    }

    private fun loadFromAsset(context: Context): List<FoodItem> = runCatching {
        context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
            val file = json.decodeFromString<CatalogFile>(reader.readText())
            file.items.map { it.toFoodItem() }
        }
    }.getOrElse { emptyList() }

    fun buildProgrammaticCatalog(): List<FoodItem> = buildList {
        addAll(energyDrinkCatalog())
        addAll(gainerCatalog())
        addAll(rtdCatalog())
        addAll(mealReplacementCatalog())
        addAll(carbPowderCatalog())
        addAll(proteinPowderCatalog())
    }

    private fun CatalogEntry.toFoodItem() = FoodItem(
        id = id,
        name = name,
        brand = brand,
        category = category,
        servingSize = servingSize,
        servingUnit = servingUnit,
        unit = unit,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fats = fats,
        carbBreakdown = CarbBreakdown(sugar = sugar),
        micronutrients = if (sodiumMg > 0) listOf(
            com.example.kpkn.data.models.Micronutrient("Sodio", sodiumMg, "mg"),
        ) else emptyList(),
        caffeineMg = caffeineMg,
        creatineG = creatineG,
        tags = listOf("suplemento", category) + regions,
        searchAliases = searchAliases,
        source = "KPKN Curated",
        sourcePriority = 90,
        verifiedScore = 0.92,
        nutritionBasis = nutritionBasis,
    )

    private fun energy(
        id: String,
        name: String,
        brand: String,
        ml: Double,
        kcal: Double,
        caffeine: Double,
        sugar: Double = 11.0,
        regions: List<String> = listOf("LATAM", "EU", "NA"),
        aliases: List<String> = emptyList(),
    ) = FoodItem(
        id = id,
        name = name,
        brand = brand,
        category = "bebida_energetica",
        servingSize = ml,
        servingUnit = "ml",
        unit = "ml",
        calories = kcal,
        protein = 0.0,
        carbs = sugar * 4,
        fats = 0.0,
        carbBreakdown = CarbBreakdown(sugar = sugar),
        caffeineMg = caffeine,
        tags = listOf("bebida_energetica", "suplemento") + regions,
        searchAliases = aliases + listOf(brand.lowercase(), name.lowercase()),
        source = "KPKN Curated",
        sourcePriority = 90,
        verifiedScore = 0.92,
        nutritionBasis = "PER_SERVING",
    )

    private fun powder(
        id: String,
        name: String,
        brand: String,
        scoopG: Double,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
        category: String,
        aliases: List<String> = emptyList(),
        caffeine: Double = 0.0,
    ) = FoodItem(
        id = id,
        name = name,
        brand = brand,
        category = category,
        servingSize = scoopG,
        servingUnit = "g",
        unit = "g",
        calories = kcal,
        protein = protein,
        carbs = carbs,
        fats = fats,
        caffeineMg = caffeine,
        tags = listOf(category, "suplemento"),
        searchAliases = aliases,
        source = "KPKN Curated",
        sourcePriority = 90,
        verifiedScore = 0.9,
        nutritionBasis = "PER_SERVING",
    )

    private fun energyDrinkCatalog(): List<FoodItem> = buildList {
        // LATAM — Chile
        add(energy("enr_score_clasica", "Score Energy Drink", "Score", 250.0, 48.0, 80.0, regions = listOf("LATAM", "CL"), aliases = listOf("score", "score clasica")))
        add(energy("enr_score_sf", "Score Energy Sugar Free", "Score", 250.0, 5.0, 80.0, sugar = 0.0, regions = listOf("LATAM", "CL"), aliases = listOf("score zero", "score sin azucar")))
        add(energy("enr_score_gold", "Score Energy Gold", "Score", 250.0, 50.0, 80.0, regions = listOf("LATAM", "CL")))
        add(energy("enr_score_tropical", "Score Energy Tropical", "Score", 250.0, 48.0, 80.0, regions = listOf("LATAM", "CL")))
        add(energy("enr_score_citrus", "Score Energy Citrus", "Score", 250.0, 48.0, 80.0, regions = listOf("LATAM", "CL")))
        add(energy("enr_winkler_clasica", "Winkler Energy Drink", "Winkler", 250.0, 45.0, 80.0, regions = listOf("LATAM", "CL"), aliases = listOf("winkler")))
        add(energy("enr_winkler_sf", "Winkler Energy Sugar Free", "Winkler", 250.0, 4.0, 80.0, sugar = 0.0, regions = listOf("LATAM", "CL")))
        add(energy("enr_speed_ar", "Speed Unlimited", "Speed", 250.0, 45.0, 80.0, regions = listOf("LATAM", "AR")))
        add(energy("enr_220v_ar", "220V Energy", "220V", 250.0, 48.0, 80.0, regions = listOf("LATAM", "AR")))
        add(energy("enr_vive100_co", "Vive 100 Original", "Vive 100", 250.0, 45.0, 80.0, regions = listOf("LATAM", "CO", "MX")))
        add(energy("enr_vive100_ultra", "Vive 100 Ultra", "Vive 100", 250.0, 5.0, 80.0, sugar = 0.0, regions = listOf("LATAM", "CO", "MX")))
        add(energy("enr_boost_mx", "Boost Energy", "Boost", 250.0, 45.0, 80.0, regions = listOf("LATAM", "MX")))
        add(energy("enr_amp_mx", "AMP Energy", "AMP", 355.0, 110.0, 142.0, regions = listOf("LATAM", "MX")))
        add(energy("enr_volt_pe", "Volt Energy", "Volt", 250.0, 45.0, 80.0, regions = listOf("LATAM", "PE")))
        add(energy("enr_tnt_br", "TNT Energy Drink", "TNT", 269.0, 48.0, 80.0, regions = listOf("LATAM", "BR")))
        add(energy("enr_flyinghorse_br", "Flying Horse Energy", "Flying Horse", 250.0, 45.0, 80.0, regions = listOf("LATAM", "BR")))

        // Red Bull global variants
        val rbFlavors = listOf(
            "Original" to "enr_rb_original",
            "Sugarfree" to "enr_rb_sugarfree",
            "Zero" to "enr_rb_zero",
            "Tropical" to "enr_rb_tropical",
            "Watermelon" to "enr_rb_watermelon",
            "Blue Edition" to "enr_rb_blue",
            "Red Edition" to "enr_rb_red",
            "Yellow Edition" to "enr_rb_yellow",
            "Green Edition" to "enr_rb_green",
            "White Edition" to "enr_rb_white",
            "Coconut Edition" to "enr_rb_coconut",
        )
        rbFlavors.forEach { (flavor, id) ->
            val sugar = if (flavor.contains("Sugar") || flavor == "Zero") 0.0 else 11.0
            val kcal = if (sugar == 0.0) 5.0 else 112.0
            add(energy(id, "Red Bull $flavor", "Red Bull", 250.0, kcal, 80.0, sugar = sugar, aliases = listOf("red bull", "redbull", flavor.lowercase())))
        }
        add(energy("enr_rb_355", "Red Bull Original (lata grande)", "Red Bull", 355.0, 160.0, 114.0, aliases = listOf("red bull grande")))

        // Monster global
        val monsterVariants = listOf(
            Triple("Monster Energy", 473.0, 160.0) to "enr_monster_original",
            Triple("Monster Lo-Carb", 473.0, 20.0) to "enr_monster_locarb",
            Triple("Monster Ultra", 473.0, 10.0) to "enr_monster_ultra",
            Triple("Monster Ultra Paradise", 473.0, 10.0) to "enr_monster_ultra_paradise",
            Triple("Monster Ultra Rosa", 473.0, 10.0) to "enr_monster_ultra_rosa",
            Triple("Monster Ultra Fiesta", 473.0, 10.0) to "enr_monster_ultra_fiesta",
            Triple("Monster Ultra Violet", 473.0, 10.0) to "enr_monster_ultra_violet",
            Triple("Monster Mango Loco", 473.0, 160.0) to "enr_monster_mango",
            Triple("Monster Pipeline Punch", 473.0, 160.0) to "enr_monster_pipeline",
            Triple("Monster Pacific Punch", 473.0, 160.0) to "enr_monster_pacific",
            Triple("Monster Rehab Tea + Lemonade", 458.0, 25.0) to "enr_monster_rehab_lemon",
            Triple("Monster Rehab Peach Tea", 458.0, 25.0) to "enr_monster_rehab_peach",
            Triple("Monster Juice Khaotic", 473.0, 160.0) to "enr_monster_juice_khaotic",
            Triple("Monster Juice Mango Loco", 473.0, 160.0) to "enr_monster_juice_mango",
            Triple("Monster Java Mean Bean", 443.0, 188.0) to "enr_monster_java_bean",
            Triple("Monster Java Loca Moca", 443.0, 200.0) to "enr_monster_java_moca",
        )
        monsterVariants.forEach { (spec, id) ->
            val (name, ml, caffeine) = spec
            val kcal = if (name.contains("Ultra") || name.contains("Lo-Carb")) 10.0 else if (name.contains("Rehab")) 25.0 else if (name.contains("Java")) 190.0 else 210.0
            add(energy(id, name, "Monster", ml, kcal, caffeine, aliases = listOf("monster", name.lowercase())))
        }

        // Burn, Battery, Rockstar EU
        add(energy("enr_burn_original", "Burn Original", "Burn", 250.0, 48.0, 80.0, regions = listOf("EU", "LATAM")))
        add(energy("enr_burn_zero", "Burn Zero", "Burn", 250.0, 5.0, 80.0, sugar = 0.0, regions = listOf("EU", "LATAM")))
        add(energy("enr_battery_original", "Battery Original", "Battery", 330.0, 50.0, 80.0, regions = listOf("EU", "LATAM")))
        add(energy("enr_rockstar_original", "Rockstar Original", "Rockstar", 500.0, 220.0, 160.0, regions = listOf("EU", "NA")))
        add(energy("enr_rockstar_punched", "Rockstar Punched", "Rockstar", 500.0, 220.0, 160.0, regions = listOf("EU", "NA")))
        add(energy("enr_rockstar_zero", "Rockstar Zero", "Rockstar", 500.0, 10.0, 160.0, sugar = 0.0, regions = listOf("EU", "NA")))
        add(energy("enr_relentless_origin", "Relentless Origin", "Relentless", 500.0, 55.0, 80.0, regions = listOf("EU", "UK")))
        add(energy("enr_hell_classic", "Hell Classic", "Hell", 250.0, 48.0, 80.0, regions = listOf("EU")))
        add(energy("enr_tiger_classic", "Tiger Energy Classic", "Tiger", 250.0, 48.0, 80.0, regions = listOf("EU")))
        add(energy("enr_effect_classic", "Effect Energy Drink", "Effect", 250.0, 48.0, 80.0, regions = listOf("EU", "DE")))
        add(energy("enr_darkdog", "Dark Dog Original", "Dark Dog", 250.0, 48.0, 80.0, regions = listOf("EU", "FR")))
        add(energy("enr_carabao", "Carabao Green Apple", "Carabao", 330.0, 60.0, 80.0, regions = listOf("EU", "UK")))
        add(energy("enr_lucozade", "Lucozade Energy Original", "Lucozade", 380.0, 133.0, 46.0, regions = listOf("EU", "UK")))

        // North America
        add(energy("enr_celsius_sparkling", "Celsius Sparkling Orange", "Celsius", 355.0, 10.0, 200.0, sugar = 0.0, regions = listOf("NA"), aliases = listOf("celsius")))
        add(energy("enr_celsius_peach", "Celsius Peach Vibe", "Celsius", 355.0, 10.0, 200.0, sugar = 0.0, regions = listOf("NA")))
        add(energy("enr_prime_energy", "Prime Energy Tropical Punch", "Prime", 355.0, 10.0, 200.0, sugar = 0.0, regions = listOf("NA"), aliases = listOf("prime energy")))
        add(energy("enr_ghost_sour", "Ghost Energy Sour Patch", "Ghost", 473.0, 5.0, 200.0, sugar = 0.0, regions = listOf("NA"), aliases = listOf("ghost energy")))
        add(energy("enr_c4_original", "C4 Energy Original", "C4", 473.0, 0.0, 200.0, sugar = 0.0, regions = listOf("NA")))
        add(energy("enr_alani_cosmic", "Alani Nu Cosmic Stardust", "Alani Nu", 355.0, 15.0, 200.0, regions = listOf("NA"), aliases = listOf("alani nu")))
        add(energy("enr_bang_blue", "Bang Blue Razz", "Bang", 473.0, 0.0, 300.0, sugar = 0.0, regions = listOf("NA"), aliases = listOf("bang")))
        add(energy("enr_reign_melon", "Reign Melon Mania", "Reign", 473.0, 10.0, 300.0, sugar = 0.0, regions = listOf("NA"), aliases = listOf("reign")))
        add(energy("enr_nos", "NOS Energy Original", "NOS", 473.0, 210.0, 160.0, regions = listOf("NA")))
        add(energy("enr_fullthrottle", "Full Throttle Original", "Full Throttle", 473.0, 220.0, 160.0, regions = listOf("NA")))
        add(energy("enr_5hour", "5-hour Energy Berry", "5-hour Energy", 57.0, 4.0, 200.0, sugar = 0.0, regions = listOf("NA"), aliases = listOf("5 hour energy", "5-hour")))
        add(energy("enr_gfuel", "G Fuel Energy Blue Ice", "G Fuel", 473.0, 0.0, 140.0, sugar = 0.0, regions = listOf("NA"), aliases = listOf("gfuel", "g fuel")))
    }

    private fun gainerCatalog(): List<FoodItem> = listOf(
        powder("sup_on_serious_mass_choc", "Serious Mass Chocolate", "Optimum Nutrition", 334.0, 1250.0, 50.0, 254.0, 4.0, "gainer", listOf("serious mass", "on gainer")),
        powder("sup_on_serious_mass_van", "Serious Mass Vanilla", "Optimum Nutrition", 334.0, 1250.0, 50.0, 254.0, 4.0, "gainer"),
        powder("sup_mt_masstech", "Mass-Tech Extreme 2000", "MuscleTech", 306.0, 1200.0, 60.0, 220.0, 6.0, "gainer", listOf("mass tech")),
        powder("sup_dym_super_mass", "Super Mass Gainer Chocolate", "Dymatize", 325.0, 1280.0, 52.0, 246.0, 10.0, "gainer"),
        powder("sup_mutant_mass", "Mutant Mass Extreme", "Mutant", 285.0, 1100.0, 56.0, 192.0, 14.0, "gainer"),
        powder("sup_bsn_truemass", "True-Mass 1200", "BSN", 314.0, 1230.0, 54.0, 214.0, 14.0, "gainer"),
        powder("sup_applied_critical", "Critical Mass Chocolate", "Applied Nutrition", 235.0, 900.0, 42.0, 150.0, 10.0, "gainer"),
    )

    private fun rtdCatalog(): List<FoodItem> = listOf(
        powder("sup_premier_choc", "Premier Protein Chocolate Shake", "Premier Protein", 414.0, 160.0, 30.0, 5.0, 3.0, "rtd", listOf("premier protein")),
        powder("sup_corepower_choc", "Core Power Chocolate", "Fairlife", 414.0, 170.0, 26.0, 8.0, 4.5, "rtd", listOf("core power", "fairlife")),
        powder("sup_musclemilk_choc", "Muscle Milk Genuine Chocolate", "Muscle Milk", 414.0, 280.0, 32.0, 12.0, 9.0, "rtd"),
        powder("sup_ensure_plus", "Ensure Plus Vanilla", "Ensure", 237.0, 350.0, 16.0, 50.0, 11.0, "rtd"),
        powder("sup_boost_hp", "Boost High Protein Vanilla", "Boost", 237.0, 240.0, 15.0, 33.0, 6.0, "rtd"),
    )

    private fun mealReplacementCatalog(): List<FoodItem> = listOf(
        powder("sup_huel_rtd", "Huel Ready-to-drink Vanilla", "Huel", 500.0, 400.0, 20.0, 40.0, 13.0, "meal_replacement", listOf("huel")),
        powder("sup_herbalife_f1", "Formula 1 Nutritional Shake", "Herbalife", 26.0, 90.0, 9.0, 13.0, 1.0, "meal_replacement", listOf("herbalife", "formula 1")),
        powder("sup_slimfast", "SlimFast High Protein Shake", "SlimFast", 325.0, 180.0, 20.0, 23.0, 3.0, "meal_replacement"),
    )

    private fun carbPowderCatalog(): List<FoodItem> = listOf(
        powder("sup_maltodextrin", "Maltodextrina", "Genérico", 50.0, 190.0, 0.0, 47.0, 0.0, "carb_powder", listOf("maltodextrina", "maltodextrin")),
        powder("sup_dextrose", "Dextrosa", "Genérico", 50.0, 180.0, 0.0, 45.0, 0.0, "carb_powder", listOf("dextrosa", "dextrose")),
        powder("sup_cluster_dextrin", "Cluster Dextrin", "Genérico", 25.0, 95.0, 0.0, 24.0, 0.0, "carb_powder"),
        powder("sup_waxy_maize", "Waxy Maize", "Genérico", 50.0, 190.0, 0.0, 47.0, 0.0, "carb_powder"),
    )

    private fun proteinPowderCatalog(): List<FoodItem> = listOf(
        powder("sup_on_gold_choc", "Gold Standard 100% Whey Chocolate", "Optimum Nutrition", 30.0, 120.0, 24.0, 3.0, 1.5, "protein_powder", listOf("gold standard", "on whey")),
        powder("sup_dym_iso100", "ISO100 Hydrolyzed Chocolate", "Dymatize", 32.0, 120.0, 25.0, 2.0, 0.5, "protein_powder", listOf("iso100", "dymatize")),
        powder("sup_myprotein_impact", "Impact Whey Chocolate", "MyProtein", 25.0, 103.0, 21.0, 1.0, 1.9, "protein_powder", listOf("myprotein", "impact whey")),
        powder("sup_mt_nitrotech", "Nitro-Tech Whey Gold", "MuscleTech", 33.0, 130.0, 24.0, 3.0, 2.0, "protein_powder", listOf("nitro tech")),
        powder("sup_bsn_syntha6", "Syntha-6 Chocolate", "BSN", 47.0, 200.0, 22.0, 15.0, 6.0, "protein_powder"),
        powder("sup_isopure_zero", "Isopure Zero Carb", "Isopure", 31.0, 110.0, 25.0, 0.0, 0.5, "protein_powder"),
        powder("sup_ghost_whey", "Ghost Whey Cereal Milk", "Ghost", 32.0, 130.0, 25.0, 4.0, 1.5, "protein_powder", listOf("ghost whey")),
        powder("sup_proscience", "ProScience Whey Chocolate", "ProScience", 30.0, 120.0, 24.0, 3.0, 2.0, "protein_powder"),
        powder("sup_wild_whey", "Wild Foods Whey Protein", "Wild Foods", 30.0, 120.0, 24.0, 3.0, 2.0, "protein_powder", listOf("wild foods")),
        powder("sup_star_nutrition", "Star Nutrition Whey", "Star Nutrition", 30.0, 120.0, 24.0, 3.0, 2.0, "protein_powder"),
        powder("sup_integralmedica", "Integralmédica Whey Protein", "Integralmédica", 30.0, 120.0, 24.0, 3.0, 2.0, "protein_powder"),
    )
}
