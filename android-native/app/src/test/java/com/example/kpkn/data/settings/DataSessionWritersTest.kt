package com.example.kpkn.data.backup

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DataSessionWritersTest {
    @Test fun acceptedDurableCallerSurvivesVmCancellationAndOldOwnerCannotWriteAfterReopen() = runBlocking {
        val writer = DataSessionWriter("durable-caller-test")
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var durableValue = 0
        val caller = launch(Dispatchers.Default) {
            writer.durableWrite {
                entered.complete(Unit)
                finish.await()
                DataSessionWriter("nested-dependency").write { durableValue = 41 }
            }
        }
        try {
            withTimeout(5_000) { entered.await() }
            DataSessionWriters.closeAdmission()
            caller.cancel()
            val drain = async { DataSessionWriters.awaitStopped() }
            yield()
            assertFalse(drain.isCompleted)
            assertTrue(runCatching { DataSessionWriter("late-factory") }.isFailure)
            finish.complete(Unit)
            withTimeout(5_000) { drain.await(); caller.join() }
            assertEquals(41, durableValue)
            DataSessionWriters.reopenAdmission()
            assertTrue(runCatching { writer.write { durableValue = 99 } }.isFailure)
            DataSessionWriter("rebuilt-test").write { durableValue = 42 }
            assertEquals(42, durableValue)
        } finally {
            finish.complete(Unit)
            caller.cancelAndJoin()
            DataSessionWriters.closeAdmission()
            DataSessionWriters.awaitStopped()
            DataSessionWriters.reopenAdmission()
        }
    }

    @Test fun repositoryCollectorKeepsItsParentAndCancellingParentReleasesItsPermit() = runBlocking {
        val writer = DataSessionWriter("collector-test")
        val parent = SupervisorJob()
        val scope = DataSessionWriterScope(CoroutineScope(parent + Dispatchers.Default), writer)
        val entered = CompletableDeferred<Unit>()
        val child = scope.launch { entered.complete(Unit); awaitCancellation() }
        try {
            withTimeout(5_000) { entered.await() }
            DataSessionWriters.closeAdmission()
            val drain = async { DataSessionWriters.awaitStopped() }
            yield()
            assertFalse(drain.isCompleted)
            parent.cancelAndJoin()
            withTimeout(5_000) { drain.await() }
            assertTrue(child.isCancelled)
            DataSessionWriters.reopenAdmission()
            assertTrue(scope.async { error("Retired scope ran") }.isCompleted)
        } finally {
            parent.cancelAndJoin()
            DataSessionWriters.closeAdmission()
            DataSessionWriters.awaitStopped()
            DataSessionWriters.reopenAdmission()
        }
    }

    @Test fun newSiblingOperationCannotBorrowRetiredOwnerButOriginalChainCanFinish() = runBlocking {
        val retired = DataSessionWriter("retiring-owner")
        val sibling = DataSessionWriter("sibling")
        var acceptedValue = 0
        try {
            retired.write {
                retired.closeAdmission()
                sibling.write { retired.write { acceptedValue = 7 } }
            }
            assertEquals(7, acceptedValue)
            var attempted = false
            assertTrue(runCatching {
                sibling.write { retired.write { attempted = true } }
            }.isFailure)
            assertFalse(attempted)
        } finally {
            DataSessionWriters.closeAdmission()
            DataSessionWriters.awaitStopped()
            DataSessionWriters.reopenAdmission()
        }
    }
}
