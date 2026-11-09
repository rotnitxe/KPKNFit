package com.example.kpkn.screens.workout.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraXProcessUseCaseOwnerTest {
    private class Provider {
        val bound = linkedSetOf<String>()
        val unbound = mutableListOf<List<String>>()
        fun bind(vararg cases: String) { bound.addAll(cases) }
        fun unbind(cases: List<String>) {
            unbound += cases
            bound.removeAll(cases.toSet())
        }
    }

    private fun coordinator() = ExclusiveUseCaseLeaseCoordinator<Provider, String> { provider, cases ->
        provider.unbind(cases)
    }

    @Test fun rebinding_suspended_owner_preempts_current_and_restores_it_on_release() {
        val provider = Provider()
        val ownerA = Any()
        val ownerB = Any()
        val coordinator = coordinator()
        var suspendedA = 0
        var suspendedB = 0
        var bindA = 0
        var bindB = 0

        coordinator.bind(provider, ownerA, listOf("a-preview", "a-video"), onSuspended = { suspendedA++ }) {
            bindA++
            provider.bind("a-preview", "a-video")
        }
        coordinator.bind(provider, ownerB, listOf("b-preview"), onSuspended = { suspendedB++ }) {
            bindB++
            provider.bind("b-preview")
        }
        coordinator.bind(provider, ownerA, listOf("a-preview", "a-video"), onSuspended = { suspendedA++ }) {
            bindA++
            provider.bind("a-preview", "a-video")
        }

        assertEquals(setOf("a-preview", "a-video"), provider.bound)
        assertEquals(listOf(listOf("a-preview", "a-video"), listOf("b-preview")), provider.unbound)
        assertEquals(2, bindA)
        assertEquals(1, bindB)
        assertEquals(1, suspendedA)
        assertEquals(1, suspendedB)

        coordinator.release(provider, ownerA)
        assertEquals(setOf("b-preview"), provider.bound)
        assertEquals(2, bindB)
        assertEquals(2, suspendedA)
    }

    @Test fun release_of_suspended_owner_does_not_unbind_current_owner() {
        val provider = Provider()
        val ownerA = Any()
        val ownerB = Any()
        val coordinator = coordinator()
        coordinator.bind(provider, ownerA, listOf("a")) { provider.bind("a") }
        coordinator.bind(provider, ownerB, listOf("b")) { provider.bind("b") }

        coordinator.release(provider, ownerA)

        assertEquals(setOf("b"), provider.bound)
        assertEquals(listOf(listOf("a")), provider.unbound)
    }

    @Test fun failed_bind_releases_only_its_cases_and_restores_latest_previous_owner() {
        val provider = Provider()
        val ownerA = Any()
        val ownerB = Any()
        val coordinator = coordinator()
        coordinator.bind(provider, ownerA, listOf("a")) { provider.bind("a") }

        runCatching {
            coordinator.bind(provider, ownerB, listOf("b")) {
                provider.bind("b")
                error("bind failed")
            }
        }

        assertEquals(setOf("a"), provider.bound)
        assertEquals(listOf(listOf("a"), listOf("b")), provider.unbound)
    }
}
