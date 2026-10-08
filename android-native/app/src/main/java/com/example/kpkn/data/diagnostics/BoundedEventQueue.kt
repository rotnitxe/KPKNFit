package com.example.kpkn.data.diagnostics

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/** Producers never wait for disk. Drop newest normal events; reserve capacity for critical events. */
internal class BoundedEventQueue<T>(capacity: Int, private val critical: (T) -> Boolean) {
    private val values = LinkedBlockingQueue<T>(capacity)
    val dropped = AtomicLong()
    val size: Int get() = values.size
    fun offer(value: T): Boolean = synchronized(this) {
        if (values.offer(value)) return true
        if (critical(value)) {
            val victim = values.firstOrNull { !critical(it) }
            if (victim != null && values.remove(victim)) {
                dropped.incrementAndGet()
                if (values.offer(value)) return true
            }
        }
        dropped.incrementAndGet()
        false
    }
    fun peek(): T? = values.peek()
    fun poll(): T? = values.poll()
    fun isEmpty(): Boolean = values.isEmpty()
    fun isNotEmpty(): Boolean = values.isNotEmpty()
    fun any(predicate: (T) -> Boolean): Boolean = values.any(predicate)
    fun clear() = synchronized(this) { dropped.addAndGet(values.size.toLong()); values.clear() }
}
