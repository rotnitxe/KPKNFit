package com.example.kpkn.services.workout

/**
 * Joins Vosk endpoint fragments that belong to one spoken workout command.
 * Vosk may finalize after a natural pause between reps, load and intensity.
 */
internal class VoskUtteranceAccumulator {
    private val fragments = mutableListOf<String>()

    fun append(text: String): String {
        val clean = text.trim()
        if (clean.isNotEmpty() && fragments.lastOrNull() != clean) fragments += clean
        return fragments.joinToString(" ")
    }

    fun consume(): String = fragments.joinToString(" ").also { fragments.clear() }

    fun clear() = fragments.clear()
}
