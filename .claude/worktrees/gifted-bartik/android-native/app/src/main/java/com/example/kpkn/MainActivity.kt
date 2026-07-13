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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.ui.theme.KPKNTheme
import java.util.Calendar
import kotlin.math.pow
import kotlin.math.sqrt

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

// ─── MODELOS DE DATOS ───────────────────────
data class Program(val id: String, val name: String, val coverImage: Int? = null)
data class Session(val id: String, val name: String, val exercises: List<String>, val isCompleted: Boolean = false)

val samplePrograms = listOf(
    Program("1", "Hipertrofia Avanzada", null),
    Program("2", "Powerlifting Base", null),
    Program("3", "Acondicionamiento", null)
)

val todaySessions = listOf(
    Session("s1", "Pecho y Tríceps", listOf("Press Banca", "Aperturas", "Fondos"), false)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KPKNApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var userName by rememberSaveable { mutableStateOf("Usuario") }

    // Estados de los RINGS
    var muscularProgress by remember { mutableFloatStateOf(0.85f) }
    var sncProgress by remember { mutableFloatStateOf(0.70f) }
    var columnaProgress by remember { mutableFloatStateOf(0.90f) }
    var selectedRingIndex by remember { mutableIntStateOf(-1) }

    // Sin programa activo por ahora (sin Room/ViewModel)
    val hasActiveProgram = false

    // Estado del scroll para el header dinámico
    val listState = rememberLazyListState()
    val scrollProgress by remember {
        derivedStateOf {
            if (listState.layoutInfo.visibleItemsInfo.isEmpty()) 0f
            else {
                val firstItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                if (firstItem != null && firstItem.index == 0) {
                    (Math.abs(firstItem.offset) / 250f).coerceIn(0f, 1f)
                } else 1f
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.forEach { dest ->
                    item(
                        icon = { 
                            val tint = if (dest == currentDestination) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            when (dest) {
                                AppDestinations.HOME -> Icon(Icons.Default.Home, null)
                                AppDestinations.TRAINING -> DumbbellIcon(tint)
                                AppDestinations.NUTRITION -> NutritionIcon(tint)
                                AppDestinations.WIKILAB -> WikiIcon(tint)
                            }
                        },
                        label = { Text(dest.label) },
                        selected = dest == currentDestination,
                        onClick = { currentDestination = dest }
                    )
                }
            }
        ) {
            val greeting = remember {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                when {
                    hour < 12 -> "¡Buenos días"
                    hour < 19 -> "¡Buenas tardes"
                    else -> "¡Buenas noches"
                }
            }

            Scaffold(
                topBar = {
                    HomeTopBar(
                        onSettingsClick = { /* Settings */ },
                        scrollProgress = scrollProgress,
                        greeting = greeting,
                        userName = userName
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (currentDestination) {
                        AppDestinations.HOME -> HomeWithProgramScreen(muscularProgress, sncProgress, columnaProgress, { selectedRingIndex = it }, listState, scrollProgress, userName, greeting, hasActiveProgram)
                        else -> GenericScreen(currentDestination.label)
                    }
                }
            }
        }

        // Overlay Global
        AnimatedVisibility(visible = selectedRingIndex != -1, enter = fadeIn(), exit = fadeOut()) {
            CalibrationOverlay(
                index = selectedRingIndex,
                initialProgress = when(selectedRingIndex) { 0 -> muscularProgress; 1 -> sncProgress; else -> columnaProgress },
                onProgressChange = { if(selectedRingIndex == 1) sncProgress = it else if(selectedRingIndex == 2) columnaProgress = it },
                onDismiss = { selectedRingIndex = -1 }
            )
        }
    }
}

// ─── ICONOS CUSTOM ──────────────────────────
@Composable fun WikiIcon(color: Color) { Text("W", color = color, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, fontSize = 20.sp) }
@Composable fun DumbbellIcon(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val sw = 2.dp.toPx()
        drawLine(color, Offset(4.dp.toPx(), 12.dp.toPx()), Offset(20.dp.toPx(), 12.dp.toPx()), sw)
        drawRoundRect(color, Offset(0.dp.toPx(), 6.dp.toPx()), Size(4.dp.toPx(), 12.dp.toPx()), CornerRadius(1.dp.toPx()))
        drawRoundRect(color, Offset(20.dp.toPx(), 6.dp.toPx()), Size(4.dp.toPx(), 12.dp.toPx()), CornerRadius(1.dp.toPx()))
    }
}
@Composable fun NutritionIcon(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        drawLine(color, Offset(6.dp.toPx(), 4.dp.toPx()), Offset(6.dp.toPx(), 12.dp.toPx()), 1.5.dp.toPx())
        drawLine(color, Offset(9.dp.toPx(), 4.dp.toPx()), Offset(9.dp.toPx(), 12.dp.toPx()), 1.5.dp.toPx())
        drawLine(color, Offset(6.dp.toPx(), 12.dp.toPx()), Offset(9.dp.toPx(), 12.dp.toPx()), 1.5.dp.toPx())
        drawLine(color, Offset(7.5f.dp.toPx(), 12.dp.toPx()), Offset(7.5f.dp.toPx(), 20.dp.toPx()), 2.dp.toPx())
        drawOval(color, Offset(14.dp.toPx(), 4.dp.toPx()), Size(6.dp.toPx(), 10.dp.toPx()))
        drawLine(color, Offset(17.dp.toPx(), 14.dp.toPx()), Offset(17.dp.toPx(), 20.dp.toPx()), 2.dp.toPx())
    }
}

// ─── COMPONENTES ────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(onSettingsClick: () -> Unit, scrollProgress: Float, greeting: String, userName: String) {
    val horizontalBias by animateFloatAsState(if (scrollProgress > 0.5f) -1f else 0f)
    val headerHeight by animateDpAsState(if (scrollProgress > 0.5f) 70.dp else 100.dp)

    Surface(Modifier.fillMaxWidth().height(headerHeight), color = MaterialTheme.colorScheme.surface.copy(alpha = if (scrollProgress > 0.8f) 0.95f else 0f)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(BiasAlignment(horizontalBias, 0f))) {
                Image(painterResource(R.drawable.kpknicon), "Logo", Modifier.size(if(scrollProgress > 0.5f) 32.dp else 45.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
                AnimatedVisibility(visible = scrollProgress > 0.6f) { Text("$greeting, $userName!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) }
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.align(Alignment.CenterEnd)) { Icon(Icons.Default.Settings, null) }
        }
    }
}

@Composable
fun HomeWithProgramScreen(mp: Float, sp: Float, cp: Float, onSelect: (Int) -> Unit, listState: LazyListState, scrollProgress: Float, userName: String, greeting: String, hasActiveProgram: Boolean) {
    val mainAlpha = (1f - (scrollProgress * 2f)).coerceIn(0f, 1f)

    // Colores de rings: a color si hay programa, en grises si no
    val muscularColor = if (hasActiveProgram) Color(0xFFFF5252) else Color(0xFF666666)
    val sncColor      = if (hasActiveProgram) Color(0xFF448AFF) else Color(0xFF888888)
    val columnaColor  = if (hasActiveProgram) Color(0xFFFFD740) else Color(0xFFAAAAAA)

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Column(Modifier.padding(top = 16.dp).graphicsLayer { alpha = mainAlpha }) { Text("$greeting,\n$userName!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, lineHeight = 40.sp, letterSpacing = (-1).sp) } }
        item {
            SectionHeader("Tus RINGS")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.height(110.dp).fillMaxWidth()) {
                        AugeRings(mp, sp, cp, muscularColor, sncColor, columnaColor)
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) { detectTapGestures(onLongPress = { onSelect(0) }) })
                            Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) { detectTapGestures(onLongPress = { onSelect(1) }) })
                            Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) { detectTapGestures(onLongPress = { onSelect(2) }) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RingLabel("MUSCULAR", muscularColor, mp) { onSelect(0) }
                        RingLabel("SNC", sncColor, sp) { onSelect(1) }
                        RingLabel("COLUMNA", columnaColor, cp) { onSelect(2) }
                    }
                }
            }
        }
        item {
            SectionHeader("Sesión de hoy")
            if (hasActiveProgram) {
                todaySessions.forEach { TodaySessionCard(it) }
            } else {
                NoProgramCard()
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun CalibrationOverlay(index: Int, initialProgress: Float, onProgressChange: (Float) -> Unit, onDismiss: () -> Unit) {
    var localP by remember(index) { mutableFloatStateOf(initialProgress) }
    val color = when(index) { 0 -> Color(0xFFFF5252); 1 -> Color(0xFF448AFF); else -> Color(0xFFFFD740) }
    val name = when(index) { 0 -> "MUSCULAR"; 1 -> "SNC"; else -> "COLUMNA" }
    val msg = when(index) { 0 -> "Lectura automática."; 1 -> "¿Cómo te sientes mentalmente?"; else -> "¿Fatiga en espalda?" }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f)).pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
        .pointerInput(index) { if(index != 0) detectDragGestures { change, drag -> change.consume(); localP = (localP - drag.y/1000f).coerceIn(0f, 1f); onProgressChange(localP) } },
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(40.dp)) {
            Text("RECALIBRANDO $name", color = color, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(240.dp)) { drawCircle(color.copy(alpha = 0.1f), style = Stroke(20.dp.toPx())); drawArc(color, -90f, 360f * localP, false, style = Stroke(20.dp.toPx())) }
                Text("${(localP * 100).toInt()}%", style = MaterialTheme.typography.displayLarge, color = Color.White, fontWeight = FontWeight.Black)
            }
            Text(msg, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
            if(index == 0) Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = color)) { Text("CERRAR", color = Color.Black) }
        }
    }
}

@Composable fun AugeRings(mp: Float, sp: Float, cp: Float, muscularColor: Color = Color(0xFFFF5252), sncColor: Color = Color(0xFF448AFF), columnaColor: Color = Color(0xFFFFD740)) {
    Canvas(Modifier.fillMaxSize()) {
        val r = size.width / 5.8f
        val s = r * 1.9f
        val cy = size.height / 2f
        val cx = size.width / 2f
        val data = listOf(muscularColor to mp, sncColor to sp, columnaColor to cp)
        listOf(Offset(cx-s, cy), Offset(cx, cy), Offset(cx+s, cy)).forEachIndexed { i, c ->
            drawCircle(data[i].first.copy(alpha = 0.2f), r, c, style = Stroke(8.dp.toPx()))
            drawArc(data[i].first, -90f, 360f * data[i].second, false, Offset(c.x-r, c.y-r), Size(r*2, r*2), style = Stroke(8.dp.toPx()))
        }
    }
}

@Composable fun RingLabel(l: String, c: Color, p: Float, onLong: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLong() }) }) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(6.dp)); Text(l, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) }
        Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = c.copy(alpha = 0.8f))
    }
}

@Composable fun SectionHeader(t: String) { Text(t.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp)) }
@Composable fun TodaySessionCard(s: Session) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.name, fontWeight = FontWeight.Black, fontSize = 20.sp); Text("${s.exercises.size} ejercicios", style = MaterialTheme.typography.bodySmall) }; Button(onClick={}) { Icon(Icons.Default.PlayArrow, null); Text("START") } } } }

@Composable
fun NoProgramCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                "No tienes un programa activo",
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "Crea un programa de entrenamiento para ver tu sesión del día aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = {}) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Crear programa")
            }
        }
    }
}

@Composable fun GenericScreen(t: String) { Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Text(t, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Default.Home),
    TRAINING("Entreno", Icons.Default.Build),
    NUTRITION("Alimentación", Icons.Default.ShoppingCart),
    WIKILAB("WikiLab", Icons.Default.Info),
}
