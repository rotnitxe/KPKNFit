package com.example.kpkn.domain.performance

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthModelArchitectureTest {
    @Test
    fun `produccion no llama a las formulas heredadas fuera del adaptador`() {
        val main = listOf(File("src/main"), File("app/src/main"), File("android-native/app/src/main"))
            .firstOrNull { it.isDirectory }
        checkNotNull(main) { "No se encontró src/main para comprobar la arquitectura" }
        val forbidden = Regex("\\bcalculate(?:Hybrid1RM|Epley1RM|Brzycki1RM|Lander1RM|WeightFrom1RM\\w*)\\b")
        val allowed = setOf(
            "java/com/example/kpkn/domain/calculations/Calculations.kt",
            "java/com/example/kpkn/domain/performance/StrengthModel.kt",
        )
        val violations = main.walkTopDown().filter { it.extension == "kt" }
            .filter { it.relativeTo(main).invariantSeparatorsPath !in allowed }
            .flatMap { file -> file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.containsMatchIn(line)) "${file.relativeTo(main)}:${index + 1}" else null
            } }.toList()
        assertTrue("Fórmulas heredadas fuera del adaptador: $violations", violations.isEmpty())
    }
}
