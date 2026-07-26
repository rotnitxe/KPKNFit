package com.example.kpkn.domain.training

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

fun interface IdProvider {
    fun newId(): String
}

interface AppClock {
    fun now(): Instant
    fun today(zoneId: ZoneId): LocalDate
}

object UuidIdProvider : IdProvider {
    override fun newId(): String = UUID.randomUUID().toString()
}

object SystemAppClock : AppClock {
    override fun now(): Instant = Instant.now()
    override fun today(zoneId: ZoneId): LocalDate = LocalDate.now(zoneId)
}
