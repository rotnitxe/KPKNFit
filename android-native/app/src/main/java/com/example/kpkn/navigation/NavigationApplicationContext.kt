package com.example.kpkn.navigation

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

/** Only Navigation's creation extras need Application. Personal contexts remain captured. */
internal class NavigationApplicationContext(base: Context, private val platformApplication: Application) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = platformApplication
}

private class NavigationHolder { var controller: NavHostController? = null }

@Composable
internal fun rememberProfileNavController(): NavHostController {
    val personalContext = LocalContext.current
    val application = com.example.kpkn.data.account.ProfileStorage.platformContext() as Application
    val navigationContext = remember(personalContext, application) { NavigationApplicationContext(personalContext, application) }
    val holder = remember { NavigationHolder() }
    // The holder survives skipped recompositions. Saved navigation state is still
    // managed by the official rememberNavController implementation.
    CompositionLocalProvider(LocalContext provides navigationContext) {
        holder.controller = rememberNavController()
    }
    return requireNotNull(holder.controller)
}
