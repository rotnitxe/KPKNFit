package com.example.kpkn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.screens.programs.ProgramsScreen
import com.example.kpkn.ui.components.icons.DumbbellIcon
import com.example.kpkn.ui.components.icons.NutritionIcon
import com.example.kpkn.ui.components.icons.WikiIcon
import com.example.kpkn.ui.theme.KPKNTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KPKNTheme {
                KPKNApp()
            }
        }
    }
}

// ─── App root with Navigation Compose ───────────────────────────────────────

@Composable
fun KPKNApp() {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val currentTab = when {
        currentRoute?.startsWith(KpknRoute.Training.route) == true  -> KpknRoute.Training.route
        currentRoute?.startsWith(KpknRoute.Nutrition.route) == true -> KpknRoute.Nutrition.route
        currentRoute?.startsWith(KpknRoute.WikiLab.route) == true   -> KpknRoute.WikiLab.route
        else -> KpknRoute.Home.route
    }

    var muscularProgress by remember { mutableFloatStateOf(0.85f) }
    var sncProgress     by remember { mutableFloatStateOf(0.70f) }
    var columnaProgress by remember { mutableFloatStateOf(0.90f) }
    var selectedRingIndex by remember { mutableIntStateOf(-1) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                item(
                    icon = { Icon(Icons.Default.Home, null, tint = navIconTint(currentTab == KpknRoute.Home.route)) },
                    label = { Text("Inicio") },
                    selected = currentTab == KpknRoute.Home.route,
                    onClick = { navController.navigate(KpknRoute.Home.route) { launchSingleTop = true } },
                )
                item(
                    icon = { DumbbellIcon(tint = navIconTint(currentTab == KpknRoute.Training.route)) },
                    label = { Text("Entreno") },
                    selected = currentTab == KpknRoute.Training.route,
                    onClick = { navController.navigate(KpknRoute.Training.route) { launchSingleTop = true } },
                )
                item(
                    icon = { NutritionIcon(tint = navIconTint(currentTab == KpknRoute.Nutrition.route)) },
                    label = { Text("Nutrición") },
                    selected = currentTab == KpknRoute.Nutrition.route,
                    onClick = { navController.navigate(KpknRoute.Nutrition.route) { launchSingleTop = true } },
                )
                item(
                    icon = { WikiIcon(tint = navIconTint(currentTab == KpknRoute.WikiLab.route)) },
                    label = { Text("WikiLab") },
                    selected = currentTab == KpknRoute.WikiLab.route,
                    onClick = { navController.navigate(KpknRoute.WikiLab.route) { launchSingleTop = true } },
                )
            },
        ) {
            NavHost(navController = navController, startDestination = KpknRoute.Home.route) {
                composable(KpknRoute.Home.route) {
                    HomeScreenPlaceholder(muscularProgress, sncProgress, columnaProgress) { selectedRingIndex = it }
                }
                composable(KpknRoute.Training.route) {
                    ProgramsScreen { programId ->
                        navController.navigate(KpknRoute.ProgramDetail.create(programId))
                    }
                }
                composable(KpknRoute.Nutrition.route) { GenericScreen("Nutrición") }
                composable(KpknRoute.WikiLab.route)   { GenericScreen("WikiLab") }
                composable(KpknRoute.ProgramDetail.route) { backStack ->
                    val id = backStack.arguments?.getString(KpknRoute.ProgramDetail.ARG_PROGRAM_ID)
                    GenericScreen("Programa: $id")
                }
            }
        }

        AnimatedVisibility(visible = selectedRingIndex != -1, enter = fadeIn(), exit = fadeOut()) {
            CalibrationOverlay(
                index = selectedRingIndex,
                initialProgress = when (selectedRingIndex) { 0 -> muscularProgress; 1 -> sncProgress; else -> columnaProgress },
                onProgressChange = { if (selectedRingIndex == 1) sncProgress = it else if (selectedRingIndex == 2) columnaProgress = it },
                onDismiss = { selectedRingIndex = -1 },
            )
        }
    }
}

@Composable
private fun navIconTint(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

// ─── Home placeholder (replaced by HomeScreen in Phase 2) ───────────────────

@Composable
fun HomeScreenPlaceholder(mp: Float, sp: Float, cp: Float, onRingSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    val scrollProgress by remember {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first != null && first.index == 0) (kotlin.math.abs(first.offset) / 250f).coerceIn(0f, 1f) else 1f
        }
    }
    val greeting = remember {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when { h < 12 -> "¡Buenos días"; h < 19 -> "¡Buenas tardes"; else -> "¡Buenas noches" }
    }
    Scaffold(
        topBar = { HomeTopBar({}, scrollProgress, greeting, "Usuario") },
    ) { inner ->
        HomeWithProgramScreen(mp, sp, cp, onRingSelect, listState, scrollProgress, "Usuario", greeting, Modifier.padding(inner))
    }
}

// ─── Components (unchanged from original implementation) ─────────────────────

data class SampleSession(val id: String, val name: String, val exercises: List<String>)
val todaySessions = listOf(SampleSession("s1", "Pecho y Tríceps", listOf("Press Banca", "Aperturas", "Fondos")))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(onSettingsClick: () -> Unit, scrollProgress: Float, greeting: String, userName: String) {
    val horizontalBias by animateFloatAsState(if (scrollProgress > 0.5f) -1f else 0f, label = "bias")
    val headerHeight by animateDpAsState(if (scrollProgress > 0.5f) 70.dp else 100.dp, label = "height")
    Surface(Modifier.fillMaxWidth().height(headerHeight), color = MaterialTheme.colorScheme.surface.copy(alpha = if (scrollProgress > 0.8f) 0.95f else 0f)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(BiasAlignment(horizontalBias, 0f))) {
                Image(painterResource(R.drawable.kpknicon), "Logo", Modifier.size(if (scrollProgress > 0.5f) 32.dp else 45.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
                AnimatedVisibility(visible = scrollProgress > 0.6f) { Text("$greeting, $userName!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) }
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.align(Alignment.CenterEnd)) { Icon(Icons.Default.Settings, null) }
        }
    }
}

@Composable
fun HomeWithProgramScreen(mp: Float, sp: Float, cp: Float, onSelect: (Int) -> Unit, listState: LazyListState, scrollProgress: Float, userName: String, greeting: String, modifier: Modifier = Modifier) {
    val mainAlpha = (1f - scrollProgress * 2f).coerceIn(0f, 1f)
    LazyColumn(state = listState, modifier = modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Column(Modifier.padding(top = 16.dp).graphicsLayer { alpha = mainAlpha }) { Text("$greeting,\n$userName!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, lineHeight = 40.sp, letterSpacing = (-1).sp) } }
        item {
            SectionHeader("Tus RINGS")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.height(110.dp).fillMaxWidth()) {
                        AugeRings(mp, sp, cp)
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) { detectTapGestures(onLongPress = { onSelect(0) }) })
                            Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) { detectTapGestures(onLongPress = { onSelect(1) }) })
                            Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) { detectTapGestures(onLongPress = { onSelect(2) }) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RingLabel("MUSCULAR", Color(0xFFFF5252), mp) { onSelect(0) }
                        RingLabel("SNC", Color(0xFF448AFF), sp) { onSelect(1) }
                        RingLabel("COLUMNA", Color(0xFFFFD740), cp) { onSelect(2) }
                    }
                }
            }
        }
        item { SectionHeader("Sesión de hoy"); todaySessions.forEach { TodaySessionCard(it) } }
    }
}

@Composable
fun CalibrationOverlay(index: Int, initialProgress: Float, onProgressChange: (Float) -> Unit, onDismiss: () -> Unit) {
    var localP by remember(index) { mutableFloatStateOf(initialProgress) }
    val color = when (index) { 0 -> Color(0xFFFF5252); 1 -> Color(0xFF448AFF); else -> Color(0xFFFFD740) }
    val name  = when (index) { 0 -> "MUSCULAR"; 1 -> "SNC"; else -> "COLUMNA" }
    val msg   = when (index) { 0 -> "Lectura automática."; 1 -> "¿Cómo te sientes mentalmente?"; else -> "¿Fatiga en espalda?" }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f)).pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }.pointerInput(index) { if (index != 0) detectDragGestures { change, drag -> change.consume(); localP = (localP - drag.y / 1000f).coerceIn(0f, 1f); onProgressChange(localP) } }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(40.dp)) {
            Text("RECALIBRANDO $name", color = color, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(240.dp)) { drawCircle(color.copy(alpha = 0.1f), style = Stroke(20.dp.toPx())); drawArc(color, -90f, 360f * localP, false, style = Stroke(20.dp.toPx())) }
                Text("${(localP * 100).toInt()}%", style = MaterialTheme.typography.displayLarge, color = Color.White, fontWeight = FontWeight.Black)
            }
            Text(msg, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
            if (index == 0) Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = color)) { Text("CERRAR", color = Color.Black) }
        }
    }
}

@Composable
fun AugeRings(mp: Float, sp: Float, cp: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val r = size.width / 5.8f; val s = r * 1.9f; val cy = size.height / 2f; val cx = size.width / 2f
        val data = listOf(Color(0xFFFF5252) to mp, Color(0xFF448AFF) to sp, Color(0xFFFFD740) to cp)
        listOf(Offset(cx - s, cy), Offset(cx, cy), Offset(cx + s, cy)).forEachIndexed { i, c ->
            drawCircle(data[i].first.copy(alpha = 0.2f), r, c, style = Stroke(8.dp.toPx()))
            drawArc(data[i].first, -90f, 360f * data[i].second, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(8.dp.toPx()))
        }
    }
}

@Composable
fun RingLabel(l: String, c: Color, p: Float, onLong: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLong() }) }) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(6.dp)); Text(l, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) }
        Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = c.copy(alpha = 0.8f))
    }
}

@Composable fun SectionHeader(t: String) { Text(t.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp)) }

@Composable fun TodaySessionCard(s: SampleSession) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.name, fontWeight = FontWeight.Black, fontSize = 20.sp); Text("${s.exercises.size} ejercicios", style = MaterialTheme.typography.bodySmall) }; Button(onClick = {}) { Icon(Icons.Default.PlayArrow, null); Text("START") } }
    }
}

@Composable fun GenericScreen(t: String) { Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Text(t, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }
