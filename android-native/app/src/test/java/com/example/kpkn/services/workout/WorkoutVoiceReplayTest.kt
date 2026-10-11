package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.parseWorkoutVoiceTranscript
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Replay del campo: re-corre dictados reales (asr_final) que en producción
 * llegaron a "command_parsed" contra el parser actual, para detectar regresiones
 * de vocabulario/gramática sin necesidad de un teléfono.
 *
 * Corpus: `src/test/resources/voice-replay/` (archivos jsonl) o carpeta pasada
 * por `-Dvoice.replay.dir=path`. Si no hay corpus, el test se salta (assumeTrue).
 */
class WorkoutVoiceReplayTest {

    private fun resourceCorpus(): List<File> {
        val explicitDir = System.getProperty("voice.replay.dir")?.let(::File)
        val dir = explicitDir
            ?: File("src/test/resources/voice-replay")
        return if (dir.isDirectory) dir.listFiles { f -> f.extension == "jsonl" }?.toList().orEmpty()
        else emptyList()
    }

    @Test
    fun fieldTranscriptsThatWorkedStillParseAsCommands() {
        val files = resourceCorpus()
        assumeTrue("No hay corpus de replay de voz configurado.", files.isNotEmpty())

        var commandParsed = 0
        var replayed = 0
        var unexpectedUnknown = 0
        val samples = mutableListOf<String>()

        files.forEach { file ->
            val events = file.readLines()
                .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
            // eventos command_parsed con su asr_final previo como "dictados que sí
            // funcionaron en el campo". Se usa el unitMode logueado para no aplanar
            // el contexto (regresión de modo tiempo/unilateral).
            events
                .filter { it.optString("event") == "command_parsed" }
                .forEach { parsed ->
                    commandParsed++
                    val isTimeMode = parsed.optString("unitMode", "REPS") == "TIME"
                    val isUnilateral = parsed.optBoolean("isUnilateral", false)
                    val transcript = parsed.optString("transcript").takeIf { it.isNotBlank() }
                        ?: parsed.optString("sanitized").takeIf { it.isNotBlank() }
                        ?: events.asReversed()
                            .firstOrNull { it.optString("event") == "asr_final" }
                            ?.optString("sanitized")
                            ?.takeIf { it.isNotBlank() }
                            ?: return@forEach
                    replayed++

                    val command = WorkoutVoiceCommandParser.parseCommand(
                        transcript = transcript,
                        isTimeMode = isTimeMode,
                        isUnilateral = isUnilateral,
                        hasPendingConfirmation = false,
                        isRestTimerActive = false,
                    )
                    val interpretation = parseWorkoutVoiceTranscript(
                        transcript = transcript,
                        isTimeMode = isTimeMode,
                        isUnilateral = isUnilateral,
                    )
                    if (command is VoiceSessionCommand.Unknown && interpretation == null) {
                        unexpectedUnknown++
                        if (samples.size < 25) samples.add(transcript)
                    }
                }
        }

        assertTrue("No hubo command_parsed en el corpus para replay.", commandParsed > 0)
        assertTrue(
            "$unexpectedUnknown de $replayed dictado(s) de campo ya no parsean. " +
                "Muestras: ${samples.joinToString(" | ")}",
            unexpectedUnknown == 0,
        )
    }

    @Test
    fun replayTranscriptParserNeverThrows() {
        val files = resourceCorpus()
        assumeTrue("No hay corpus de replay de voz configurado.", files.isNotEmpty())
        files.forEach { file ->
            file.readLines().forEach { line ->
                val obj = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
                if (obj.optString("event") != "asr_final") return@forEach
                val text = obj.optString("sanitized").takeIf { it.isNotBlank() } ?: return@forEach
                WorkoutVoiceCommandParser.parseCommand(
                    transcript = text,
                    isTimeMode = false,
                    isUnilateral = false,
                    hasPendingConfirmation = false,
                    isRestTimerActive = false,
                )
                parseWorkoutVoiceTranscript(text, isTimeMode = false, isUnilateral = false)
            }
        }
        // Llegar aquí sin excepción es el contrato.
        assertTrue(true)
    }
}
