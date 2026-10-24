package com.example.kpkn.screens.sessioneditor

/** Discards stale descriptive analysis after editing a different snapshot. */
internal class EditorAnalysisRequestGate {
    private val counter = java.util.concurrent.atomic.AtomicLong()
    fun next(): Long = counter.incrementAndGet()
    fun isCurrent(ticket: Long): Boolean = counter.get() == ticket
}
