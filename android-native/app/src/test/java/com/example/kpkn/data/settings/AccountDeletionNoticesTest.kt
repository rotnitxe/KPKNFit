package com.example.kpkn.data.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.*
import com.example.kpkn.data.backup.PersonalStorageManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountDeletionNoticesTest {
    @Test fun aPendingNoticeSurvivesProfileErasureAndCanBeAcknowledged() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val base = ApplicationProvider.getApplicationContext<Application>()
        ProfileStorage.initialize(base)
        val profile = ProfileStorage.context(base, LocalProfile(uid = "synthetic-deleted-user")) as ProfileContext
        try {
            AccountDeletionNotices.save(remotePending = true, localPending = true)
            assertFalse(AccountDeletionNotices.PREFERENCES in profile.personalPreferenceNames())
            PersonalStorageManifest.clearFilesAndPreferences(profile)
            AccountDeletionNotices.load()
            assertTrue(AccountDeletionNotices.notice.value!!.remotePending)
            AccountDeletionNotices.clearLocalPending()
            assertFalse(AccountDeletionNotices.notice.value!!.localPending)
            AccountDeletionNotices.dismiss()
            AccountDeletionNotices.load()
            assertNull(AccountDeletionNotices.notice.value)
        } finally { AccountDeletionNotices.dismiss(); Dispatchers.resetMain() }
    }
    @Test fun corruptNoticeCannotExposeArbitraryStoredText() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val base = ApplicationProvider.getApplicationContext<Application>()
        ProfileStorage.initialize(base)
        try {
            base.getSharedPreferences(AccountDeletionNotices.PREFERENCES, 0).edit()
                .putString("account_deletion", "not a valid notice").commit()
            AccountDeletionNotices.load()
            assertNull(AccountDeletionNotices.notice.value)
        } finally { AccountDeletionNotices.dismiss(); Dispatchers.resetMain() }
    }
}
