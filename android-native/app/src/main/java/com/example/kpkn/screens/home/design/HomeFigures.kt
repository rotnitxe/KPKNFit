package com.example.kpkn.screens.home.design

/*
 * Memoria de las cifras y de los trazos de Home: se animan solo la PRIMERA vez que se ven en el proceso. Es el mismo criterio que
 * la entrada de cada sección ([kpknEntrance]): al volver a Home, ya están en su sitio.
 */
internal object HomeFigureMemory {
    private val seen = HashSet<String>()

    /** true solo la primera vez que se pide [key] en el proceso. */
    @Synchronized
    fun firstTime(key: String): Boolean = seen.add(key)
}
