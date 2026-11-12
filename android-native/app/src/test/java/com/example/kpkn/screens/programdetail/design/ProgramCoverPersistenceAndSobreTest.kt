package com.example.kpkn.screens.programdetail.design

import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.screens.programdetail.buildProgramPlanDisplaySummary
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCover
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class ProgramCoverPersistenceAndSobreTest {

    @get:Rule
    val rule = createComposeRule()

    private fun tempDir(): File = Files.createTempDirectory("kpkn-cover-test").toFile()

    private fun sampleProgram(coverImage: String? = null) = Program(
        id = "prog-12",
        name = "Fuerza 12",
        coverImage = coverImage,
        macrocycles = listOf(
            Macrocycle(
                id = "m1",
                name = "M1",
                blocks = listOf(
                    Block(
                        id = "b1",
                        name = "Acumulación",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "me1",
                                name = "Base",
                                weeks = listOf(ProgramWeek(id = "w1", name = "S1")),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun permisoPersistenteValidoConservaUriSinCrearArchivoPrivado() = runBlocking {
        val dir = tempDir()
        try {
            val picked = Uri.parse("content://media/picker/123")
            val outcome = resolvePickedProgramCover(
                programId = "prog-12",
                uri = picked,
                filesDir = dir,
                takePersistableReadPermission = { _ -> },
                openInputStream = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
                isReadableImageStream = { true },
                dispatcher = Dispatchers.Unconfined,
            )
            assertEquals(ProgramCoverPickOutcome.Success("content://media/picker/123"), outcome)
            assertEquals(0, File(dir, "program_covers").listFiles().orEmpty().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sinPermisoPersistenteCopiaEnAlmacenamientoPrivadoYVerificadaSobreviveReapertura() = runBlocking {
        val dir = tempDir()
        try {
            val picked = Uri.parse("content://media/transient/456")
            val outcome = resolvePickedProgramCover(
                programId = "prog-12",
                uri = picked,
                filesDir = dir,
                takePersistableReadPermission = { throw SecurityException("no persistable grant") },
                openInputStream = { ByteArrayInputStream(byteArrayOf(9, 8, 7, 6)) },
                isReadableImageFile = { file -> file.exists() && file.readBytes().contentEquals(byteArrayOf(9, 8, 7, 6)) },
                nowMs = { 777L },
                dispatcher = Dispatchers.Unconfined,
            )
            assertTrue(outcome is ProgramCoverPickOutcome.Success)
            val saved = (outcome as ProgramCoverPickOutcome.Success).uriString
            assertTrue(saved.startsWith("file://"))
            val savedFile = File(Uri.parse(saved).path!!)
            assertTrue(savedFile.exists())
            assertTrue(savedFile.length() > 0L)

            // Al reconstruir desde el Program persistido ("tras matar proceso"), «Sobre el programa» conserva coverImage.
            val reloaded = sampleProgram(coverImage = saved)
            val ficha = programPlanFicha(
                summary = buildProgramPlanDisplaySummary(reloaded),
                program = reloaded,
                description = null,
                calendarRange = null,
                isActive = true,
                canOpenPlanInfo = false,
                today = LocalDate.of(2026, 11, 11),
                imageOf = { null },
            )
            assertEquals(saved, ficha.detail.card.coverImage)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun flujoNuloYFalloDuranteCopiaEImagenIlegibleConservanPortadaAnteriorSinArchivosHuerfanos() = runBlocking {
        val dir = tempDir()
        try {
            val picked = Uri.parse("content://media/broken/1")
            val nullOutcome = resolvePickedProgramCover(
                programId = "prog-12",
                uri = picked,
                filesDir = dir,
                takePersistableReadPermission = { throw SecurityException("no grant") },
                openInputStream = { null },
                dispatcher = Dispatchers.Unconfined,
            )
            assertTrue(nullOutcome is ProgramCoverPickOutcome.Failure)
            assertEquals(0, File(dir, "program_covers").listFiles().orEmpty().size)

            val brokenStream = object : InputStream() {
                private var reads = 0
                override fun read(): Int {
                    if (reads++ >= 2) throw IOException("stream interrupted")
                    return 42
                }
            }
            val copyFailure = resolvePickedProgramCover(
                programId = "prog-12",
                uri = picked,
                filesDir = dir,
                takePersistableReadPermission = { throw SecurityException("no grant") },
                openInputStream = { brokenStream },
                dispatcher = Dispatchers.Unconfined,
            )
            assertTrue(copyFailure is ProgramCoverPickOutcome.Failure)
            assertEquals(0, File(dir, "program_covers").listFiles().orEmpty().size)

            val corruptOutcome = resolvePickedProgramCover(
                programId = "prog-12",
                uri = picked,
                filesDir = dir,
                takePersistableReadPermission = { throw SecurityException("no grant") },
                openInputStream = { ByteArrayInputStream("not-an-image".toByteArray()) },
                isReadableImageFile = { false },
                dispatcher = Dispatchers.Unconfined,
            )
            assertTrue(corruptOutcome is ProgramCoverPickOutcome.Failure)
            assertEquals(0, File(dir, "program_covers").listFiles().orEmpty().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun cancelacionDuranteCopiaNoDejaPortadaParcial() = runBlocking {
        val dir = tempDir()
        try {
            val picked = Uri.parse("content://media/slow/1")
            val slowStream = object : InputStream() {
                override fun read(): Int = throw CancellationException("cancelled")
            }
            val job = async(Dispatchers.Unconfined) {
                resolvePickedProgramCover(
                    programId = "prog-12",
                    uri = picked,
                    filesDir = dir,
                    takePersistableReadPermission = { throw SecurityException("no grant") },
                    openInputStream = { slowStream },
                    dispatcher = Dispatchers.Unconfined,
                )
            }
            job.cancelAndJoin()
            assertEquals(0, File(dir, "program_covers").listFiles().orEmpty().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sobreElProgramaYPosterCompartenPortadaEstudioFotoYFalloDeCarga() {
        val studioCover = "kpkn-studio://PLATES/PAPEL"
        val studioProgram = sampleProgram(coverImage = studioCover)
        val fichaStudio = programPlanFicha(
            summary = buildProgramPlanDisplaySummary(studioProgram),
            program = studioProgram,
            description = null,
            calendarRange = null,
            isActive = false,
            canOpenPlanInfo = false,
            imageOf = { null },
        )
        assertEquals(studioCover, fichaStudio.detail.card.coverImage)

        val brokenPhoto = sampleProgram(coverImage = "file:///nonexistent/missing_cover.jpg")
        val fichaBroken = programPlanFicha(
            summary = buildProgramPlanDisplaySummary(brokenPhoto),
            program = brokenPhoto,
            description = null,
            calendarRange = null,
            isActive = false,
            canOpenPlanInfo = false,
            imageOf = { null },
        )

        rule.setContent {
            PlanCover(model = fichaBroken.detail.card, modifier = Modifier.size(220.dp, 290.dp), reduced = true)
            ProgramPoster(
                identity = coverIdentityForProgram(brokenPhoto),
                photoUrl = brokenPhoto.coverImage,
            )
        }
        rule.onNodeWithText("Fuerza 12").assertIsDisplayed()
        rule.onNodeWithContentDescription("Portada del programa").assertIsDisplayed()
    }
}
