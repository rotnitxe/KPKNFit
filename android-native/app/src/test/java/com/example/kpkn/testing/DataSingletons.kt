package com.example.kpkn.testing

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain

/**
 * Drena y cierra los singletons de datos al final de una clase de prueba que puso un despachador de prueba en Main.
 *
 * Una continuación de `ProgramRepository` que quedó en la cola de ese despachador (p. ej. su `loadFromDb`, que salta a Main)
 * nadie la vuelve a avanzar, y el siguiente `closeInstance()` espera para siempre en `cancelAndJoin`. Por eso primero se drena
 * el planificador de la prueba, después Main pasa a un despachador sin cola (las continuaciones corren al instante) y solo
 * entonces se cierran los repositorios. Sin instancia, cada `closeInstance()` no hace nada. La clase sigue con su
 * `Dispatchers.resetMain()`.
 */
fun drainAndCloseSingletons(scheduler: TestCoroutineScheduler) {
    scheduler.advanceUntilIdle()
    Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    ProgramRepository.closeInstance()
    NutritionRepository.closeInstance()
    KpknDatabase.closeInstance()
}
