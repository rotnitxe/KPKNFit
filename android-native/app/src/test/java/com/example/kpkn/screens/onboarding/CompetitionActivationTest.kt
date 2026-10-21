package com.example.kpkn.screens.onboarding

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupPatchField
import com.example.kpkn.data.onboarding.SetupSettingsPatch
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.services.competition.CompetitionReminderManager
import com.example.kpkn.testsupport.SyncRoomExecutor
import com.example.kpkn.testsupport.awaitRepositoryScope
import com.example.kpkn.testsupport.cerrarBaseDeRepositorioDePrueba
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * C4c · activar con fecha: el programa lleva la fecha clave COMPETITION y su calendario hasta la semana de la competición;
 * el registro PLANNED y los recordatorios de 7 días y de 48 h salen del guardado (el normal y el del alta).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class CompetitionActivationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Viernes 9 de octubre de 2026: la semana empieza el lunes 5 (con lunes como primer día). */
    private val today = LocalDate.of(2026, 10, 9)

    /** Viernes 20 de noviembre de 2026: su semana va del lunes 16 al domingo 22. */
    private val meet = LocalDate.of(2026, 11, 20)

    /** Repositorio de competición de la prueba en curso: sus escrituras son fire-and-forget y se esperan al cerrar. */
    private var competitionsDeLaPrueba: CompetitionRepository? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Room refresca sus tablas en el hilo que escribe: con el delegado síncrono ningún refresco queda en `arch_disk_io`.
        SyncRoomExecutor.install()
    }

    @After
    fun tearDown(): Unit = runBlocking {
        // Orden: primero los repositorios. La escritura de competición de `upsert` va en el scope del repositorio (fire-and-forget):
        // se espera mientras el delegado sigue síncrono; después se cierran las bases y, al final, el delegado y Main.
        ProgramRepository.closeInstance()
        competitionsDeLaPrueba?.let { awaitRepositoryScope(it) }
        competitionsDeLaPrueba?.let { cerrarBaseDeRepositorioDePrueba(it) }
        CompetitionRepository.closeInstance()
        KpknDatabase.closeInstance()
        SyncRoomExecutor.uninstall()
        Dispatchers.resetMain()
    }

    /** Quien compite (powerlifting, experiencia avanzada) con la fecha dada. */
    private fun draft(
        answer: CompetitionAnswer = CompetitionAnswer.WITH_DATE,
        meetDate: LocalDate = meet,
        name: String = "Open nacional",
    ) = SetupWizardDraft(
        goalProfile = TrainingGoalProfile.POWERLIFTING,
        experience = SetupExperience.ADVANCED,
        weekStartDay = 1,
        competition = answer,
        meetDateIso = meetDate.toString(),
        meetName = name,
    )

    /** Plan de bloques de siete semanas, como el que deja C4b para quien compite. */
    private fun plan(weeks: Int = 7): Program = Program(
        id = "plan-competicion",
        name = "Plan de competición",
        structure = ProgramStructure.COMPLEX,
        macrocycles = listOf(
            Macrocycle(
                id = "mac",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "b0",
                        name = "Acumulación",
                        goal = BlockGoal.ACCUMULATION,
                        progressionScheme = BlockProgressionScheme.PERCENT_RM,
                        mesocycles = listOf(
                            Mesocycle(
                                id = "m0",
                                name = "Meso",
                                goal = MesocycleGoal.ACCUMULATION,
                                weeks = (1..weeks).map { n ->
                                    ProgramWeek(id = "w$n", name = "Semana $n", progressionIndex = n)
                                },
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun con_fecha_el_programa_lleva_la_fecha_clave_y_termina_en_la_semana_de_la_competicion() {
        val saved = SetupActivationPayload.programForActivation(plan(), draft(), today)

        val keyDate = saved.keyDates.single { it.type == KeyDateType.COMPETITION }
        assertEquals("Open nacional", keyDate.title)
        assertEquals("2026-11-20", keyDate.eventDate)
        assertEquals("2026-11-16", keyDate.startDate)
        assertEquals("2026-11-22", keyDate.endDate)
        assertEquals("2026-10-05", saved.timelineStartDate)
        assertEquals(ScheduleMode.DATED, saved.schedulePlan?.mode)
        assertEquals("2026-11-22", saved.schedulePlan?.targetEndDate)
        assertEquals("2026-11-22", saved.calendarization?.manualEndDate)
    }

    @Test
    fun competesWithoutDate_se_escribe_en_cada_activacion() {
        // Solo «Sí, sin fecha» cuenta; con fecha, «No compito» y fuera de la ruta (sin objetivo de competición), false.
        assertTrue(SetupActivationPayload.competesWithoutDateOf(draft(CompetitionAnswer.NO_DATE)))
        assertFalse(SetupActivationPayload.competesWithoutDateOf(draft(CompetitionAnswer.WITH_DATE)))
        assertFalse(SetupActivationPayload.competesWithoutDateOf(draft(CompetitionAnswer.NO)))
        val fueraDeRuta = draft(CompetitionAnswer.NO_DATE).copy(goalProfile = null)
        assertFalse(SetupActivationPayload.competesWithoutDateOf(fueraDeRuta))
        // Se escribe siempre (Set): un true anterior se corrige al cambiar de objetivo.
        val patch = SetupSettingsPatch(
            competesWithoutDate = SetupPatchField.Set(SetupActivationPayload.competesWithoutDateOf(fueraDeRuta)),
        )
        assertFalse(patch.applyTo(Settings(competesWithoutDate = true)).competesWithoutDate)
    }

    @Test
    fun sin_nombre_del_evento_la_fecha_clave_se_llama_competicion() {
        val saved = SetupActivationPayload.programForActivation(plan(), draft(name = "  "), today)
        assertEquals("Competición", saved.keyDates.single { it.type == KeyDateType.COMPETITION }.title)
    }

    @Test
    fun sin_fecha_ni_con_no_compito_el_programa_no_cambia() {
        val program = plan()
        assertEquals(program, SetupActivationPayload.programForActivation(program, draft(CompetitionAnswer.NO_DATE), today))
        assertEquals(program, SetupActivationPayload.programForActivation(program, draft(CompetitionAnswer.NO), today))
    }

    @Test
    fun el_guardado_normal_crea_el_registro_planned_y_sus_recordatorios_de_7_dias_y_48_h() = runBlocking {
        val competitions = startRepositories()
        // Fecha relativa al reloj real: los recordatorios solo se programan si caen en el futuro.
        val program = SetupActivationPayload.programForActivation(
            plan(), draft(meetDate = LocalDate.now().plusDays(60)), LocalDate.now(),
        )

        ProgramRepository.getInstance().addProgramNow(program).getOrThrow()

        assertPlannedRecordWithReminders(competitions, program)
    }

    @Test
    fun el_alta_con_fecha_publica_el_registro_planned_y_sus_recordatorios() = runBlocking {
        val competitions = startRepositories()
        val program = SetupActivationPayload.programForActivation(
            plan(), draft(meetDate = LocalDate.now().plusDays(60)), LocalDate.now(),
        )

        ProgramRepository.getInstance().publishSetupCommit(Settings(), program, activateProgram = true)

        assertPlannedRecordWithReminders(competitions, program)
    }

    @Test
    fun el_alta_sin_fecha_no_crea_registro_de_competicion() = runBlocking {
        val competitions = startRepositories()

        ProgramRepository.getInstance().publishSetupCommit(Settings(), plan(), activateProgram = true)

        assertTrue(competitions.records.value.none { it.plannedProgramId == "plan-competicion" })
    }

    /** Repositorios de prueba en memoria, listos para escribir. */
    private suspend fun startRepositories(): CompetitionRepository {
        val competitions = CompetitionRepository.initForTests(context)
        competitionsDeLaPrueba = competitions
        withTimeout(10_000) { competitions.isReady.first { it } }
        val programs = ProgramRepository.initForTests(context)
        withTimeout(10_000) { programs.isReady.first { it } }
        programs.resetAllStateSync()
        return competitions
    }

    private fun assertPlannedRecordWithReminders(competitions: CompetitionRepository, program: Program) {
        val record = competitions.records.value.single { it.plannedProgramId == program.id }
        assertEquals(CompetitionRecordStatus.PLANNED, record.status)
        assertEquals(LocalDate.now().plusDays(60).toString(), record.eventDate)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        val reminderTypes = alarms.map { alarm ->
            shadowOf(alarm.operation).savedIntent.getStringExtra(CompetitionReminderManager.EXTRA_TYPE)
        }
        assertTrue(reminderTypes.contains(CompetitionReminderManager.TYPE_WEEK))
        assertTrue(reminderTypes.contains(CompetitionReminderManager.TYPE_48H))
    }
}
