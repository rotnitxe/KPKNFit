package com.example.kpkn.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.R
import com.example.kpkn.screens.settings.components.SettingsCategoryRow
import com.example.kpkn.screens.settings.components.SettingsProfileHeader
import com.example.kpkn.screens.settings.components.SettingsSectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToGeneral: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToNutrition: () -> Unit,
    onNavigateToTraining: () -> Unit,
    onNavigateToAuge: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToData: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val versionLabel = rememberAppVersionLabel(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_settings_title), fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item {
                SettingsProfileHeader(
                    username = settings.username,
                    athleteType = settings.athleteType,
                    onClick = onNavigateToProfile,
                )
            }
            item {
                SettingsSectionCard {
                    SettingsCategoryRow(
                        icon = Icons.Default.Settings,
                        title = stringResource(R.string.screen_settings_cat_general),
                        subtitle = stringResource(R.string.screen_settings_cat_general_subtitle),
                        onClick = onNavigateToGeneral,
                    )
                    SettingsCategoryRow(
                        icon = Icons.Default.Person,
                        title = stringResource(R.string.screen_settings_cat_profile),
                        subtitle = stringResource(R.string.screen_settings_cat_profile_subtitle),
                        onClick = onNavigateToProfile,
                    )
                    SettingsCategoryRow(
                        icon = Icons.Default.Restaurant,
                        title = stringResource(R.string.screen_settings_cat_nutrition),
                        subtitle = stringResource(R.string.screen_settings_cat_nutrition_subtitle),
                        onClick = onNavigateToNutrition,
                    )
                    SettingsCategoryRow(
                        icon = Icons.Default.FitnessCenter,
                        title = stringResource(R.string.screen_settings_cat_training),
                        subtitle = stringResource(R.string.screen_settings_cat_training_subtitle),
                        onClick = onNavigateToTraining,
                    )
                    SettingsCategoryRow(
                        icon = Icons.Default.Psychology,
                        title = stringResource(R.string.screen_settings_cat_rings),
                        subtitle = stringResource(R.string.screen_settings_cat_rings_subtitle),
                        onClick = onNavigateToAuge,
                    )
                    SettingsCategoryRow(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.screen_settings_cat_notifications),
                        subtitle = stringResource(R.string.screen_settings_cat_notifications_subtitle),
                        onClick = onNavigateToNotifications,
                    )
                    SettingsCategoryRow(
                        icon = Icons.Default.Storage,
                        title = stringResource(R.string.screen_settings_cat_data),
                        subtitle = stringResource(R.string.screen_settings_cat_data_subtitle),
                        onClick = onNavigateToData,
                    )
                }
            }
            item {
                Text(
                    text = versionLabel,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun rememberAppVersionLabel(context: android.content.Context): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return "KPKN Fit v${packageInfo.versionName} (${packageInfo.longVersionCode})"
}
