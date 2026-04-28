@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.kpkn.screens.myrings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.ExerciseDrainRanking
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.PersonalRecoveryStats
import com.example.kpkn.data.models.RecoveryDashboard
import com.example.kpkn.data.models.SessionDrainRanking
import com.example.kpkn.data.models.SessionInterference
import com.example.kpkn.data.models.SleepLogExtended
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.screens.home.batteryColor
import com.example.kpkn.ui.components.SectionHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private enum class MyRingsTab(val label: String) {
    RESUMEN("Resumen"),
    RANKINGS("Rankings"),
    MUSCULOS("Músculos"),
    INTERFERENCIA("Interferencia"),
    SUEÑO("Sueño"),
}

private val HeroGradient = listOf(
    Color(0xFF13212E),
    Color(0xFF183C55),
    Color(0xFF1B5B73),
)

@Composable
fun MyRingsScreen(
    augeViewModel: AugeViewModel,
    modifier: Modifier = Modifier,
) {
    val myRingsViewModel: MyRingsViewModel = viewModel()

    val snapshot by augeViewModel.snapshot.collectAsState()
    val isAugeLoading by augeViewModel.isLoading.collectAsState()
    val isDataLoading by myRingsViewModel.isLoading.collectAsState()

    val sessionRankings by myRingsViewModel.filteredSessionRankings.collectAsState()
    val exerciseRankings by myRingsViewModel.filteredExerciseRankings.collectAsState()
    val recentSessions by myRingsViewModel.recentSessions.collectAsState()
    val sessionsThisWeekCount by myRingsViewModel.sessionsThisWeekCount.collectAsState()
    val recoveryStats by myRingsViewModel.recoveryStats.collectAsState()
    val plannedInterferences by myRingsViewModel.plannedInterferences.collectAsState()
    val historicalInterferences by myRingsViewModel.historicalInterferences.collectAsState()
    val sleepLogs by myRingsViewModel.sleepLogs.collectAsState()
    val rankingsTab by myRingsViewModel.rankingsTab.collectAsState()
    val drainFilter by myRingsViewModel.drainFilter.collectAsState()
    val interferenceTab by myRingsViewModel.interferenceTab.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(MyRingsTab.RESUMEN) }
    var showSleepSheet by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 32.dp),
    ) {
        item {
            CompactRingsHeroCard(
                snapshot = snapshot.dashboard,
                batteries = snapshot.batteries,
                sessionsThisWeekCount = sessionsThisWeekCount,
                latestSleep = sleepLogs.maxByOrNull { it.date },
                isLoading = isAugeLoading,
                onOpenSleep = { showSleepSheet = true },
            )
        }

        item {
            RingsTabSelector(
                selected = selectedTab,
                onSelected = { selectedTab = it },
            )
        }

        item {
            when (selectedTab) {
                MyRingsTab.RESUMEN -> ResumenContent(
                    batteries = snapshot.batteries,
                    dashboard = snapshot.dashboard,
                    stats = recoveryStats,
                    sessionRankings = sessionRankings,
                    exerciseRankings = exerciseRankings,
                    recentSessions = recentSessions,
                )
                MyRingsTab.RANKINGS -> RankingsPager(
                    sessionRankings = sessionRankings,
                    exerciseRankings = exerciseRankings,
                    rankingsTab = rankingsTab,
                    onRankingsTabSelected = { myRingsViewModel.setRankingsTab(it) },
                    drainFilter = drainFilter,
                    onDrainFilterSelected = { myRingsViewModel.setDrainFilter(it) },
                    isLoading = isDataLoading,
                )
                MyRingsTab.MUSCULOS -> MuscleBreakdownSection(
                    perMuscle = snapshot.perMuscle,
                )
                MyRingsTab.INTERFERENCIA -> InterferenceSection(
                    plannedInterferences = plannedInterferences,
                    historicalInterferences = historicalInterferences,
                    selectedTab = interferenceTab,
                    onTabSelected = { myRingsViewModel.setInterferenceTab(it) },
                )
                MyRingsTab.SUEÑO -> SleepTrackingSection(
                    sleepLogs = sleepLogs,
                    onSaveSleep = { myRingsViewModel.saveSleepLog(it) },
                    onDeleteSleep = { myRingsViewModel.deleteSleepLog(it) },
                    buildSleepLog = { bedTime, wakeTime, quality, awakenings, notes ->
                        myRingsViewModel.buildNewSleepLog(bedTime, wakeTime, quality, awakenings, notes)
                    },
                )
            }
        }
    }

    if (showSleepSheet) {
        SleepTrackingSheet(
            sleepLogs = sleepLogs,
            onDismiss = { showSleepSheet = false },
            onSaveSleep = { myRingsViewModel.saveSleepLog(it) },
            onDeleteSleep = { myRingsViewModel.deleteSleepLog(it) },
            buildSleepLog = { bedTime, wakeTime, quality, awakenings, notes ->
                myRingsViewModel.buildNewSleepLog(bedTime, wakeTime, quality, awakenings, notes)
            },
        )
    }
}

@Composable
private fun CompactRingsHeroCard(
    snapshot: RecoveryDashboard,
    batteries: GlobalBatteries,
    sessionsThisWeekCount: Int,
    latestSleep: SleepLogExtended?,
    isLoading: Boolean,
    onOpenSleep: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(HeroGradient))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "RINGS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                    Text(
                        snapshot.headline.ifBlank { "Tu estado de recuperación" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FloatingActionButton(
                    onClick = onOpenSleep,
                    modifier = Modifier.size(40.dp),
                    containerColor = Color.White.copy(alpha = 0.14f),
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Bedtime, contentDescription = "Abrir sueño", modifier = Modifier.size(18.dp))
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                CompactBatteryStrip(batteries = batteries)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroStatPill(
                    title = "Sesiones 7d",
                    value = sessionsThisWeekCount.toString(),
                    icon = Icons.Default.Timeline,
                    modifier = Modifier.weight(1f),
                )
                HeroStatPill(
                    title = "Confianza",
                    value = snapshot.confidenceLabel.ifBlank { "Media" },
                    icon = Icons.Default.Insights,
                    modifier = Modifier.weight(1f),
                )
                HeroStatPill(
                    title = "Sueño",
                    value = latestSleep?.let { "${"%.1f".format(it.duration)}h" } ?: "Sin log",
                    icon = Icons.Default.Hotel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CompactBatteryStrip(
    batteries: GlobalBatteries,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BatteryPill(
            label = "Músculos",
            value = batteries.muscular,
            color = batteryColor(batteries.muscular),
            modifier = Modifier.weight(1f),
        )
        BatteryPill(
            label = "Energía",
            value = batteries.cnc,
            color = batteryColor(batteries.cnc),
            modifier = Modifier.weight(1f),
        )
        BatteryPill(
            label = "Columna",
            value = batteries.spinal,
            color = batteryColor(batteries.spinal),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BatteryPill(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.12f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$value%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = color,
                    maxLines = 1,
                )
            }
            LinearProgressIndicator(
                progress = { value.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = color,
                trackColor = Color.White.copy(alpha = 0.14f),
            )
        }
    }
}

@Composable
private fun HeroStatPill(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RingsTabSelector(
    selected: MyRingsTab,
    onSelected: (MyRingsTab) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(MyRingsTab.entries) { tab ->
            val active = tab == selected
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelected(tab) },
            ) {
                Text(
                    tab.label,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ResumenContent(
    batteries: GlobalBatteries,
    dashboard: RecoveryDashboard,
    stats: PersonalRecoveryStats?,
    sessionRankings: List<SessionDrainRanking>,
    exerciseRankings: List<ExerciseDrainRanking>,
    recentSessions: List<SessionDrainRanking>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RecoverySnapshotCard(
            batteries = batteries,
            dashboard = dashboard,
            stats = stats,
        )
        RingsInsightCard(
            sessionRankings = sessionRankings,
            exerciseRankings = exerciseRankings,
            recentSessions = recentSessions,
        )
    }
}

@Composable
private fun RecoverySnapshotCard(
    batteries: GlobalBatteries,
    dashboard: RecoveryDashboard,
    stats: PersonalRecoveryStats?,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader("Resumen")
            Text(
                dashboard.recommendation.ifBlank { "Tu readiness combina músculos, energía y columna." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 2,
            ) {
                RecoveryMetricTile("Músculos", batteries.muscular, "Promedio actual")
                RecoveryMetricTile("Energía", batteries.cnc, "Carga neural")
                RecoveryMetricTile("Columna", batteries.spinal, "Tolerancia axial")
                RecoveryMetricTile(
                    "Recup. media",
                    stats?.avgRecoveryHoursOverall?.roundToInt() ?: 0,
                    if (stats == null) "Sin historial" else "horas por sesión",
                    valueSuffix = if (stats == null) "" else "h",
                )
            }
        }
    }
}

@Composable
private fun RecoveryMetricTile(
    label: String,
    value: Int,
    subtitle: String,
    valueSuffix: String = "%",
) {
    Surface(
        modifier = Modifier.widthIn(min = 140.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            Text(
                "$value$valueSuffix",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = batteryColor(value.coerceIn(0, 100)),
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RingsInsightCard(
    sessionRankings: List<SessionDrainRanking>,
    exerciseRankings: List<ExerciseDrainRanking>,
    recentSessions: List<SessionDrainRanking>,
) {
    val hardestSession = sessionRankings.firstOrNull()
    val hardestExercise = exerciseRankings.firstOrNull()
    val recentAverage = recentSessions.takeIf { it.isNotEmpty() }
        ?.map { it.totalDrain * 100.0 }
        ?.average()
        ?.roundToInt()
        ?: 0

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Lectura")
            InsightLine(
                icon = Icons.Default.Bolt,
                title = hardestSession?.sessionName ?: "Sin sesiones",
                subtitle = hardestSession?.let { "Mayor drenaje reciente · ${(it.totalDrain * 100).roundToInt()}%" } ?: "Registra sesiones para ver patrones.",
            )
            InsightLine(
                icon = Icons.Default.Insights,
                title = hardestExercise?.exerciseName ?: "Sin ejercicios",
                subtitle = hardestExercise?.let { "Mayor costo promedio · ${(it.overallDrain * 100).roundToInt()}%" } ?: "Aun no hay ranking de ejercicios.",
            )
            InsightLine(
                icon = Icons.Default.Timeline,
                title = "$recentAverage% de drenaje medio",
                subtitle = if (recentSessions.isEmpty()) {
                    "Aun no hay sesiones recientes para promediar."
                } else {
                    "Promedio de tus ultimas ${recentSessions.size} sesiones."
                },
            )
        }
    }
}

@Composable
private fun InsightLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RankingsPager(
    sessionRankings: List<SessionDrainRanking>,
    exerciseRankings: List<ExerciseDrainRanking>,
    rankingsTab: RankingsTab,
    onRankingsTabSelected: (RankingsTab) -> Unit,
    drainFilter: DrainFilter,
    onDrainFilterSelected: (DrainFilter) -> Unit,
    isLoading: Boolean,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Rankings de drenaje",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PagerDot(active = pagerState.currentPage == 0)
                PagerDot(active = pagerState.currentPage == 1)
            }
        }

        if (isLoading) {
            SectionLoadingPlaceholder(title = "RANKINGS")
            return@Column
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(390.dp),
        ) { page ->
            when (page) {
                0 -> SessionRankingsSection(
                    rankings = sessionRankings.take(4),
                    selectedTab = rankingsTab,
                    onTabSelected = onRankingsTabSelected,
                )
                else -> ExerciseDrainSection(
                    rankings = exerciseRankings.take(4),
                    selectedFilter = drainFilter,
                    onFilterSelected = onDrainFilterSelected,
                )
            }
        }
    }
}

@Composable
private fun PagerDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(if (active) 16.dp else 8.dp, 8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTrackingSheet(
    sleepLogs: List<SleepLogExtended>,
    onDismiss: () -> Unit,
    onSaveSleep: (SleepLogExtended) -> Unit,
    onDeleteSleep: (String) -> Unit,
    buildSleepLog: (bedTime: String, wakeTime: String, quality: Int, awakenings: Int, notes: String?) -> SleepLogExtended,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader("Registro de sueño")
            Text(
                "Registra tus horas, calidad y despertares para mejorar la lectura de recuperación.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EmbeddedSleepSection(
                sleepLogs = sleepLogs,
                onSaveSleep = onSaveSleep,
                onDeleteSleep = onDeleteSleep,
                buildSleepLog = buildSleepLog,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmbeddedSleepSection(
    sleepLogs: List<SleepLogExtended>,
    onSaveSleep: (SleepLogExtended) -> Unit,
    onDeleteSleep: (String) -> Unit,
    buildSleepLog: (bedTime: String, wakeTime: String, quality: Int, awakenings: Int, notes: String?) -> SleepLogExtended,
    modifier: Modifier = Modifier,
) {
    var showForm by rememberSaveable { mutableStateOf(sleepLogs.isEmpty()) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Últimos registros", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                    FilterChip(selected = showForm, onClick = { showForm = !showForm }, label = { Text(if (showForm) "Ocultar formulario" else "Agregar") })
                }
                sleepLogs.take(7).forEach { log ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(formatSessionDate(log.date), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("${log.bedTime} - ${log.wakeTime} · ${"%.1f".format(log.duration)}h · calidad ${log.quality}/5", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "Eliminar",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { onDeleteSleep(log.id) },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                }
                if (sleepLogs.isEmpty()) {
                    Text("Sin registros todavía.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showForm) {
                    SleepEntryForm(onSaveSleep = onSaveSleep, buildSleepLog = buildSleepLog)
                }
            }
        }
    }
}

@Composable
private fun SleepEntryForm(
    onSaveSleep: (SleepLogExtended) -> Unit,
    buildSleepLog: (bedTime: String, wakeTime: String, quality: Int, awakenings: Int, notes: String?) -> SleepLogExtended,
) {
    var bedTime by rememberSaveable { mutableStateOf("23:00") }
    var wakeTime by rememberSaveable { mutableStateOf("07:00") }
    var quality by rememberSaveable { mutableIntStateOf(3) }
    var awakenings by rememberSaveable { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickInput(label = "Dormir", value = bedTime, onValueChange = { bedTime = it }, modifier = Modifier.weight(1f))
            QuickInput(label = "Despertar", value = wakeTime, onValueChange = { wakeTime = it }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { value ->
                FilterChip(selected = quality == value, onClick = { quality = value }, label = { Text(value.toString()) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Despertares", style = MaterialTheme.typography.bodySmall)
            FilterChip(selected = false, onClick = { awakenings = (awakenings - 1).coerceAtLeast(0) }, label = { Text("-") })
            Text(awakenings.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            FilterChip(selected = false, onClick = { awakenings += 1 }, label = { Text("+") })
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier.clickable {
                onSaveSleep(buildSleepLog(bedTime, wakeTime, quality, awakenings, null))
            },
        ) {
            Text(
                "Guardar sueño",
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun QuickInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun EmptySectionCard(message: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLoadingPlaceholder(title: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

private fun formatSessionDate(raw: String): String = runCatching {
    val date = LocalDate.parse(raw.take(10))
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es"))
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale("es"))
    "${weekday.replaceFirstChar { it.uppercase() }} ${date.format(formatter)}"
}.getOrDefault(raw)
