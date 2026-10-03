package com.example.kpkn.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián de arquitectura (CLAUDE.md): `domain/` es Kotlin puro. Falla si algún
 * archivo de `domain/` importa `android.*` (D2.4 sacó de ahí las dos últimas
 * dependencias, que ahora viven como adaptadores en `data/preferences`).
 */
class DomainHasNoAndroidImportsTest {

    private fun domainDirectory(): File {
        val relative = "src/main/java/com/example/kpkn/domain"
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            listOf(File(dir, relative), File(dir, "app/$relative")).firstOrNull { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        error("No encuentro $relative subiendo desde ${File("").absolutePath}")
    }

    @Test
    fun noFileUnderDomainImportsAndroid() {
        val offenders = domainDirectory().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .withIndex()
                    .filter { (_, line) -> line.trimStart().startsWith("import android.") }
                    .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }
            .toList()
        assertTrue("domain/ no puede importar android.*: $offenders", offenders.isEmpty())
    }

    @Test
    fun theGuardActuallyFindsTheDomainSources() {
        val files = domainDirectory().walkTopDown().count { it.isFile && it.extension == "kt" }
        assertTrue("el guardián no encontró fuentes de domain/ ($files)", files > 100)
    }
}
