package com.example.kpkn.screens.workout

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.toOngoingWorkoutState
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** Ejercicio de prueba: [sets] series de 8 repeticiones; [group] lo mete en una superserie. */
internal fun liveExercise(
    id: String,
    sets: Int = 2,
    group: String? = null,
    name: String = "Ejercicio ${id.uppercase()}",
): Exercise = Exercise(
    id = id,
    name = name,
    sets = (0 until sets).map { index -> ExerciseSet(id = "$id-set$index", targetReps = 8, weight = 40.0) },
    restTime = 60,
    supersetGroupRef = group,
    supersetId = group,
)

/** El orden de la sesión tal como lo lee la pantalla: todos los ejercicios de `allExercises()`. */
internal fun WorkoutUiState.liveOrder(): List<String> = session!!.allExercises().map { it.id }

/** Los ejercicios a la vista (sin los omitidos), en el orden del carrusel. */
internal fun WorkoutUiState.visibleOrder(): List<String> = liveOrder().filterNot { it in skippedExerciseIds }

/** El orden en que el navegador de pasos recorre los ejercicios (el primero en que aparece cada uno). */
internal fun WorkoutViewModel.stepExerciseOrder(): List<String> =
    workoutStepPositions(uiState.value).map { it.exerciseId }.distinct()

/**
 * Una sesión en vivo REAL para las pruebas del paquete LV4: un programa en Room en memoria, el [WorkoutViewModel] de verdad y su
 * hidratación. Lo que se comprueba es el efecto en el estado, en los pasos y en lo guardado, no una réplica de la lógica.
 */
internal class LiveSessionVmFixture(
    private val session: Session,
    private val simpleCyclic: Boolean = false,
) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val programId: String = if (simpleCyclic) "lv4-simple" else "lv4-complex"
    val sessionId: String = session.id
    private val weekId = "lv4-week"
    lateinit var repository: ProgramRepository
        private set
    private val opened = mutableListOf<WorkoutViewModel>()

    suspend fun prepare(): LiveSessionVmFixture {
        repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        val week = ProgramWeek(id = weekId, name = "Semana 1", sessions = listOf(session))
        val meso = Mesocycle(id = "lv4-meso", name = "Meso", weeks = listOf(week))
        val block = Block(id = "lv4-block", name = "Bloque", mesocycles = listOf(meso))
        val macro = Macrocycle(id = "lv4-macro", name = "Macro", blocks = listOf(block))
        val program = if (simpleCyclic) {
            Program(id = programId, name = "LV4 simple", macrocycles = listOf(macro))
        } else {
            Program(
                id = programId,
                name = "LV4 complejo",
                structure = ProgramStructure.COMPLEX,
                macrocycles = listOf(macro),
                runState = ProgramRunState(
                    runId = "lv4-run",
                    weekId = week.id,
                    weekInstanceId = week.id,
                    macrocycleId = macro.id,
                    blockId = block.id,
                    mesocycleId = meso.id,
                ),
            )
        }
        repository.addProgram(program)
        withTimeout(5_000) { repository.programs.first { list -> list.any { it.id == programId } } }
        repository.updateActiveProgramState(
            ActiveProgramState(
                programId = programId,
                status = ProgramStatus.ACTIVE,
                currentWeekId = week.id,
                currentWeekInstanceId = week.id,
                currentMacrocycleId = macro.id,
                currentBlockId = block.id,
                currentMesocycleId = meso.id,
            ),
        )
        repository.updateProgramNow(program)
        return this
    }

    /** Abre la sesión: un ViewModel nuevo que se hidrata desde el programa o, si ya hay una en curso, desde lo guardado en Room. */
    suspend fun open(): WorkoutViewModel {
        val vm = WorkoutViewModel(context, programId, sessionId, WorkoutRestAlertManager(context))
        opened += vm
        withTimeout(60_000) { vm.uiState.first { it.session != null && !it.isStartingWorkout } }
        withTimeout(30_000) { repository.ongoingWorkout.first { it != null } }
        return vm
    }

    /** «Recrear el proceso»: lo pendiente se guarda, el ViewModel muere y otro nuevo hidrata lo que quedó en Room. */
    suspend fun recreate(vm: WorkoutViewModel): WorkoutViewModel {
        vm.flushOngoingForBackgroundAndAwait()
        vm.viewModelScope.cancel()
        opened.remove(vm)
        return open()
    }

    /** La sesión en curso tal como está en Room (la que lee otro proceso al hidratar). */
    suspend fun storedOngoing(): OngoingWorkoutState =
        repository.databaseForTests().stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!

    suspend fun storedOrder(): List<String> = storedOngoing().session.allExercises().map { it.id }

    /** La sesión del programa (la que usará la próxima vez que se abra). */
    fun programSession(): Session? = repository.getProgramById(programId)
        ?.macrocycles?.flatMap { it.blocks }?.flatMap { it.mesocycles }?.flatMap { it.weeks }
        ?.flatMap { it.sessions }?.firstOrNull { it.id == sessionId }

    fun close() {
        opened.forEach { runCatching { it.viewModelScope.cancel() } }
        opened.clear()
    }

    companion object {
        /** El repositorio de Auge es un singleton: cada prueba lo suelta para no heredar el de la anterior. */
        fun resetAugeRepository() {
            AugeRepository::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, null)
        }
    }
}
