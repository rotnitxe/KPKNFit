package com.example.kpkn.data.models

/** Curated Beta 10 cardio entry points; each item creates an embedded [CardioDetails]. */
data class CardioCatalogItem(
    val id: String,
    val name: String,
    val type: CardioType,
    val description: String,
    val requiresGps: Boolean = false,
    val supportsDistance: Boolean = true,
    val supportsSpeed: Boolean = true,
    val supportsIncline: Boolean = false,
    val supportsRpm: Boolean = false,
    val supportsWatts: Boolean = false,
)

object CardioCatalog {
    val items: List<CardioCatalogItem> = listOf(
        CardioCatalogItem("cardio_treadmill", "Cinta de correr", CardioType.TREADMILL, "Ritmo y distancia en cinta.", supportsSpeed = true, supportsIncline = true),
        CardioCatalogItem("cardio_elliptical", "Elíptica", CardioType.ELLIPTICAL, "Trabajo continuo de bajo impacto.", supportsSpeed = false, supportsRpm = true),
        CardioCatalogItem("cardio_row", "Remo ergómetro", CardioType.ROW_MACHINE, "Intervalos o trabajo continuo en remo.", supportsSpeed = false, supportsRpm = true, supportsWatts = true),
        CardioCatalogItem("cardio_bike", "Bicicleta estática", CardioType.BIKE_STATIONARY, "Cardio indoor con distancia estimada.", supportsSpeed = false, supportsRpm = true, supportsWatts = true),
        CardioCatalogItem("cardio_run_outdoor", "Carrera exterior", CardioType.RUN_OUTDOOR, "Carrera con distancia y ritmo registrados por GPS.", requiresGps = true, supportsSpeed = true),
        CardioCatalogItem("cardio_bike_outdoor", "Bicicleta exterior", CardioType.BIKE_OUTDOOR, "Ciclismo con distancia y ritmo registrados por GPS.", requiresGps = true, supportsSpeed = true),
        CardioCatalogItem("cardio_walk", "Caminata", CardioType.WALK, "Caminata continua o por intervalos con GPS.", requiresGps = true, supportsSpeed = true),
        CardioCatalogItem("cardio_stairs", "Escaladora", CardioType.STAIR_CLIMBER, "Trabajo continuo en escaladora.", supportsSpeed = false),
        CardioCatalogItem("cardio_air_bike", "Air Bike", CardioType.AIR_BIKE, "Bicicleta de brazos y piernas, ideal para sprints.", supportsDistance = false, supportsSpeed = false, supportsRpm = true, supportsWatts = true),
        CardioCatalogItem("cardio_ski_erg", "SkiErg", CardioType.SKI_ERG, "Ergómetro de esquí con metros y vatios.", supportsDistance = true, supportsSpeed = false, supportsWatts = true),
        CardioCatalogItem("cardio_curved_treadmill", "Cinta curva", CardioType.CURVED_TREADMILL, "Cinta autopropulsada para sprints y ritmo manual.", supportsSpeed = true, supportsDistance = true),
        CardioCatalogItem("cardio_sled", "Trineo (sled)", CardioType.SLED, "Empuje o arrastre de trineo por distancia corta.", supportsSpeed = false, supportsDistance = true),
    )

    fun findByType(type: CardioType): CardioCatalogItem? = items.firstOrNull { it.type == type }
}
