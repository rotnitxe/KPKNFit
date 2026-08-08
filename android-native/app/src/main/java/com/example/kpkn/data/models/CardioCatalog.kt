package com.example.kpkn.data.models

/** Curated Beta 10 cardio entry points; each item creates an embedded [CardioDetails]. */
data class CardioCatalogItem(
    val id: String,
    val name: String,
    val type: CardioType,
    val description: String,
    val requiresGps: Boolean = false,
    val supportsDistance: Boolean = true,
)

object CardioCatalog {
    val items: List<CardioCatalogItem> = listOf(
        CardioCatalogItem("cardio_treadmill", "Cinta de correr", CardioType.TREADMILL, "Ritmo y distancia en cinta."),
        CardioCatalogItem("cardio_elliptical", "Elíptica", CardioType.ELLIPTICAL, "Trabajo continuo de bajo impacto."),
        CardioCatalogItem("cardio_row", "Remo ergómetro", CardioType.ROW_MACHINE, "Intervalos o trabajo continuo en remo."),
        CardioCatalogItem("cardio_bike", "Bicicleta estática", CardioType.BIKE_STATIONARY, "Cardio indoor con distancia estimada."),
        CardioCatalogItem("cardio_run_outdoor", "Carrera exterior", CardioType.RUN_OUTDOOR, "Carrera con registro manual; GPS en vivo próximamente.", requiresGps = true),
        CardioCatalogItem("cardio_bike_outdoor", "Bicicleta exterior", CardioType.BIKE_OUTDOOR, "Ciclismo con registro manual; GPS en vivo próximamente.", requiresGps = true),
        CardioCatalogItem("cardio_walk", "Caminata", CardioType.WALK, "Caminata continua o por intervalos; GPS en vivo próximamente.", requiresGps = true),
        CardioCatalogItem("cardio_stairs", "Escaladora", CardioType.STAIR_CLIMBER, "Trabajo continuo en escaladora."),
    )
}
