package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.domain.account.AccountIdentityPolicy
import com.example.kpkn.domain.account.AccountState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [26, 34], shadows = [PosixAtomicFileRenameShadow::class])
class ProfileRegistryRecoveryTest {
    private val platform: Context = ApplicationProvider.getApplicationContext()
    private val registry get() = File(platform.filesDir, "profile-registry.json")
    private fun reload() = ProfileStorage.initialize(ContextWrapper(platform))
    private fun owner() {
        registry.parentFile!!.mkdirs()
        registry.writeText("""{"uid":"owner-a","guestId":"legacy-guest","epoch":2}""")
        reload()
        assertEquals("owner-a", ProfileStorage.current().uid)
    }
    @Test fun backupOnlyAfterInterruptedSignOutCannotExposeGuest() {
        owner()
        assertTrue(registry.renameTo(File(registry.path + ".bak")))
        reload()
        assertEquals("owner-a", ProfileStorage.current().uid)
        assertFalse(ProfileStorage.recoveryNeeded.value)
        assertTrue(identity(null) is AccountState.NeedsRecovery)
    }
    @Test fun interruptedAccountSwitchRequiresMatchingAuthenticatedOwner() {
        owner()
        assertTrue(registry.renameTo(File(registry.path + ".bak")))
        // A partial replacement cannot override the last committed owner.
        registry.writeText("{" )
        reload()
        assertEquals("owner-a", ProfileStorage.current().uid)
        assertTrue(identity("owner-b") is AccountState.NeedsRecovery)
        assertTrue(identity("owner-a") is AccountState.SignedIn)
    }
    @Test fun corruptRegistryFailsClosed() {
        owner()
        registry.writeText("corrupt")
        reload()
        assertTrue(ProfileStorage.recoveryNeeded.value)
        assertNotEquals("legacy-guest", ProfileStorage.current().key)
        assertTrue(identity(null) is AccountState.NeedsRecovery)
    }
    @Test fun unfinishedFirstWriteFailsClosed() {
        registry.parentFile!!.mkdirs()
        File(registry.path + ".new").writeText("{")
        reload()
        assertTrue(ProfileStorage.recoveryNeeded.value)
        assertNotEquals("legacy-guest", ProfileStorage.current().key)
    }
    @Test fun freshInstallRetainsLegacyMigration() {
        reload()
        assertFalse(ProfileStorage.recoveryNeeded.value)
        assertEquals("legacy-guest", ProfileStorage.current().key)
    }
    private fun identity(authenticatedUid: String?) = ProfileStorage.current().let {
        AccountIdentityPolicy.state(it.key, it.uid, authenticatedUid, true, ProfileStorage.recoveryNeeded.value)
    }
}
