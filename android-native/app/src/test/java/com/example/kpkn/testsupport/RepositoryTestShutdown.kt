package com.example.kpkn.testsupport

import com.example.kpkn.data.db.KpknDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Cancela y espera el trabajo que un repositorio lanzó en su `scope` privado. Las escrituras de `CompetitionRepository` son
 * fire-and-forget y su `closeInstance()` no las espera: sin esta espera, una escritura en vuelo llega después de cerrar la base o
 * después de retirar el ejecutor de Room. Usa reflexión porque el repositorio no expone su scope; solo lo llaman las pruebas.
 */
internal suspend fun awaitRepositoryScope(repositorio: Any, limite: Duration = 5.seconds) {
    val campo = repositorio.javaClass.getDeclaredField("scope").apply { isAccessible = true }
    val scope = campo.get(repositorio) as CoroutineScope
    withTimeoutOrNull(limite) { scope.coroutineContext[Job]?.cancelAndJoin() }
}

/**
 * Cierra la base que un repositorio de prueba creó para sí (`CompetitionRepository.initForTests` la abre en memoria y su
 * `closeInstance()` solo suelta el singleton). Solo para repositorios que poseen su base: una base compartida no se cierra aquí.
 */
internal fun cerrarBaseDeRepositorioDePrueba(repositorio: Any) {
    val campo = repositorio.javaClass.getDeclaredField("db").apply { isAccessible = true }
    (campo.get(repositorio) as? KpknDatabase)?.close()
}
