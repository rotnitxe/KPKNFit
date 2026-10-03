package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * WP-N12 grep gate: no assertion of `src/test/.../domain/nutrition` accepts two outcomes.
 *
 * `assertTrue(a || b)`, `size == 1 || size >= 2` or `isResolved || hasMaterialQuestion()` pass whichever way the code
 * behaves, so they fix a bug exactly as readily as a feature. The scan blanks comments and literals, finds every `assertTrue(`
 * and `assert(` call and flags a `||` of its argument that is not inside a `{ }` lambda: inside `none { a || b }` the `||`
 * joins two conditions on one element, it is not a choice between outcomes. `assertFalse(a || b)` is two `assertFalse`
 * calls and is not flagged either. Not scanned, on purpose: a predicate inside a lambda (`any { a || b }` picks elements, not outcomes),
 * `x in setOf(a, b)` and `anyOf(...)`; those stay a matter for review.
 *
 * A `||` that is legitimate goes in [ALLOWED] with the file, a distinctive fragment of the assertion and the reason.
 * An allow-list entry that no longer matches anything is reported, not failed: the `||` is gone, delete the entry.
 */
class NoDoubleOutcomeAssertionsTest {

    private data class Allowed(val file: String, val fragment: String, val reason: String)

    private data class Finding(val file: String, val line: Int, val statement: String)

    private companion object {
        val SOURCE_DIR = File("src/test/java/com/example/kpkn/domain/nutrition")

        val ALLOWED: List<Allowed> = listOf(
            Allowed(
                file = "SearchGoldenCorpusTest.kt",
                fragment = "genericAt == -1",
                reason = "a conditional, not two outcomes: a generic row that is not in the result cannot outrank the OFF rows; if it is there it ranks after them",
            ),
            Allowed(
                file = "SearchGoldenCorpusTest.kt",
                fragment = "rawAt == -1",
                reason = "a conditional, not two outcomes: the raw row may be absent from the result; if it is there it ranks after the cooked one",
            ),
        )

        val CALL = Regex("""\b(?:assertTrue|assert)\s*\(""")
    }

    // --- Blanking comments and literals (newlines are kept, so line numbers survive) ----------------------------------

    /** Index just after the string literal that starts at `start` (a quote); template expressions are skipped as code. */
    private fun skipString(src: String, start: Int, out: StringBuilder): Int {
        val raw = src.startsWith("\"\"\"", start)
        val quote = if (raw) 3 else 1
        repeat(quote) { out.append(' ') }
        var i = start + quote
        while (i < src.length) {
            val c = src[i]
            when {
                raw && src.startsWith("\"\"\"", i) -> {
                    var end = i + 3
                    while (end < src.length && src[end] == '"') end++ // a raw string may end in extra quotes
                    repeat(end - i) { out.append(' ') }
                    return end
                }
                !raw && c == '"' -> {
                    out.append(' ')
                    return i + 1
                }
                !raw && c == '\u005C' && i + 1 < src.length -> {
                    out.append(' ').append(if (src[i + 1] == '\n') '\n' else ' ')
                    i += 2
                }
                c == '$' && i + 1 < src.length && src[i + 1] == '{' -> {
                    // Template expression: code inside a string. Blank it too (a `||` there is not an assertion choice).
                    var depth = 0
                    var j = i
                    while (j < src.length) {
                        if (src[j] == '{') depth++
                        if (src[j] == '}') {
                            depth--
                            if (depth == 0) break
                        }
                        out.append(if (src[j] == '\n') '\n' else ' ')
                        j++
                    }
                    out.append(' ')
                    i = j + 1
                }
                else -> {
                    out.append(if (c == '\n') '\n' else ' ')
                    i++
                }
            }
        }
        return i
    }

    /** The source with comments, string literals and char literals replaced by spaces. */
    private fun blank(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                src.startsWith("//", i) -> {
                    while (i < src.length && src[i] != '\n') {
                        out.append(' ')
                        i++
                    }
                }
                src.startsWith("/*", i) -> {
                    var depth = 0
                    while (i < src.length) {
                        if (src.startsWith("/*", i)) {
                            depth++
                            out.append("  ")
                            i += 2
                        } else if (src.startsWith("*/", i)) {
                            depth--
                            out.append("  ")
                            i += 2
                            if (depth == 0) break
                        } else {
                            out.append(if (src[i] == '\n') '\n' else ' ')
                            i++
                        }
                    }
                }
                c == '"' -> i = skipString(src, i, out)
                c == '\'' -> {
                    // Char literal: 'x', '\n', '\'' or '\u0041'.
                    var j = i + 1
                    if (j < src.length && src[j] == '\u005C') j++
                    j++
                    while (j < src.length && src[j] != '\'' && src[j] != '\n') j++
                    repeat(minOf(j + 1, src.length) - i) { out.append(' ') }
                    i = minOf(j + 1, src.length)
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    /** The text between the parenthesis that opens at `open` and its match, or null if the call never closes. */
    private fun argumentsOf(code: String, open: Int): String? {
        var depth = 0
        for (i in open until code.length) {
            if (code[i] == '(') depth++
            if (code[i] == ')') {
                depth--
                if (depth == 0) return code.substring(open + 1, i)
            }
        }
        return null
    }

    /** True if `arguments` holds a `||` outside every `{ }` block. */
    private fun hasChoiceOutsideLambda(arguments: String): Boolean {
        var braces = 0
        for (i in arguments.indices) {
            when {
                arguments[i] == '{' -> braces++
                arguments[i] == '}' -> braces--
                braces == 0 && arguments.startsWith("||", i) -> return true
            }
        }
        return false
    }

    private fun scan(fileName: String, source: String): List<Finding> {
        val code = blank(source)
        val findings = mutableListOf<Finding>()
        for (call in CALL.findAll(code)) {
            val open = call.range.last
            val arguments = argumentsOf(code, open) ?: continue
            if (!hasChoiceOutsideLambda(arguments)) continue
            val line = source.substring(0, call.range.first).count { it == '\n' } + 1
            // The statement as written (literals included), whitespace collapsed, for the report and for the allow-list match.
            val written = source.substring(call.range.first, open + 1 + arguments.length + 1).replace(Regex("""\s+"""), " ")
            findings += Finding(fileName, line, written)
        }
        return findings
    }

    private fun isAllowed(f: Finding): Boolean = ALLOWED.any { it.file == f.file && f.statement.contains(it.fragment) }

    // --- Tests -------------------------------------------------------------------------------------------------------

    @Test
    fun `no assertion of domain nutrition accepts two outcomes`() {
        assertTrue(
            "run from the module directory: ${SOURCE_DIR.absolutePath} is not a directory",
            SOURCE_DIR.isDirectory,
        )
        val sources = SOURCE_DIR.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.name }.toList()
        assertTrue("no test sources found under ${SOURCE_DIR.absolutePath}", sources.size > 50)
        val findings = sources.flatMap { scan(it.name, it.readText(Charsets.UTF_8)) }
        val offending = findings.filterNot { isAllowed(it) }
        val stale = ALLOWED.filter { a -> findings.none { it.file == a.file && it.statement.contains(a.fragment) } }
        println("assertions scanned in ${sources.size} files; '||' choices found: ${findings.size}, allowed: ${findings.size - offending.size}")
        stale.forEach { println("allow-list entry no longer matches (delete it): ${it.file} [${it.fragment}]") }
        assertTrue(
            "assertTrue / assert with a || between outcomes (tighten the expectation, or allow-list it with a reason):\n" +
                offending.joinToString("\n") { "  ${it.file}:${it.line}: ${it.statement.take(220)}" },
            offending.isEmpty(),
        )
    }

    @Test
    fun `the scanner flags the patterns it exists to forbid and spares the rest`() {
        fun flagged(code: String): Int = scan("Snippet.kt", code).size
        // Forbidden: a choice between outcomes.
        assertEquals(1, flagged("assertTrue(a || b)"))
        assertEquals(1, flagged("assertTrue(\"size\", result.items.size == 1 || result.items.size >= 2)"))
        assertEquals(1, flagged("Assert.assertTrue(\n    tag.isResolved ||\n        tag.hasMaterialQuestion(),\n)"))
        assertEquals(1, flagged("assertTrue(foo(a || b))"))
        assertEquals(1, flagged("assert(x == 1 || y)"))
        // Allowed: not a choice between the outcomes of one assertion.
        assertEquals(0, flagged("assertFalse(a || b)"))
        assertEquals(0, flagged("assertTrue(list.none { it.a || it.b })"))
        assertEquals(0, flagged("assertTrue(list.any { x -> x.a || x.b })"))
        assertEquals(0, flagged("// assertTrue(a || b)"))
        assertEquals(0, flagged("/* assertTrue(a || b) */ assertTrue(c)"))
        assertEquals(0, flagged("assertEquals(\"assertTrue(a || b)\", text)"))
        assertEquals(0, flagged("val s = \"\"\"assertTrue(a || b)\"\"\""))
        assertEquals(0, flagged("assertTrue(\"x ${'$'}{a || b}\", c)"))
        assertEquals(0, flagged("assertTrue(c == '|' && d)"))
        // The line number points at the call, in the original text.
        val finding = scan("Snippet.kt", "line1\n// a comment\nassertTrue(a || b)\n").single()
        assertEquals(3, finding.line)
    }
}
