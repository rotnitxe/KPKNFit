package com.example.kpkn.data.account

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.domain.account.AccountIdentityPolicy
import com.example.kpkn.domain.account.AccountState
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises actual Android AtomicFile with a QA-only registry, never the installed profile. */
@RunWith(AndroidJUnit4::class)
class ProfileRegistryPlatformRecoveryTest {
    @Test fun partialReplacementRecoversLastCommittedOwner(): Unit {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val platform = instrumentation.targetContext
        check(platform.packageName == "com.example.kpkn.stability")
        val runId = requireNotNull(InstrumentationRegistry.getArguments().getString("kpkn_run_id"))
        require(runId.matches(Regex("[a-f0-9-]{36}")))
        val root = File(platform.cacheDir, "qa-registry-$runId")
        check(root.mkdirs() || root.isDirectory)
        val supplied = object : ContextWrapper(platform) {
            override fun getFilesDir(): File = root
            override fun getApplicationContext(): Context = this
        }
        val registry = File(root, "profile-registry.json")
        val backup = File(registry.path + ".bak")
        try {
            registry.writeText("""{"uid":"qa-owner-a","guestId":"legacy-guest","epoch":2}""")
            ProfileStorage.initialize(supplied)
            assertEquals("qa-owner-a", ProfileStorage.current().uid)
            assertTrue(registry.renameTo(backup))
            registry.writeText("{")
            ProfileStorage.initialize(ContextWrapper(supplied))
            assertEquals("qa-owner-a", ProfileStorage.current().uid)
            assertFalse(ProfileStorage.recoveryNeeded.value)
            assertFalse("Backup must be atomically recovered", backup.exists())
            val profile = ProfileStorage.current()
            assertTrue(AccountIdentityPolicy.state(profile.key, profile.uid, "qa-owner-b", true, false) is AccountState.NeedsRecovery)
            assertTrue(AccountIdentityPolicy.state(profile.key, profile.uid, "qa-owner-a", true, false) is AccountState.SignedIn)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("kpkn_registry_pid", "${Process.myPid()}")
                putString("kpkn_registry_result", "committed-owner-recovered")
            })
        } finally {
            // This directory is uniquely named for the harness and contains only its registry.
            registry.delete(); backup.delete(); File(registry.path + ".new").delete(); root.delete()
            ProfileStorage.initialize(platform)
        }
    }
}
