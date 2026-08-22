package com.example.kpkn.screens.wikilab

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.data.repository.WikiLabRepository
import com.example.kpkn.domain.exercises.calculateSearchScore
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.data.wikilab.searchConcepts
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════════════
// MUTED LENS PALETTE
// ═══════════════════════════════════════════════════════════════════════

private val LENS_EXERCISE = Color(0xFF9DB6C9)
private val LENS_MUSCLE = Color(0xFF86939B)
private val LENS_JOINT = Color(0xFF9AA5AC)
private val LENS_PATTERN = Color(0xFF7F8D96)
private val LENS_CHAIN = Color(0xFF8C979D)
private val LENS_CONCEPT = Color(0xFF9AA5AC)

// ═══════════════════════════════════════════════════════════════════════
// WIKI LENS TYPES
// ═══════════════════════════════════════════════════════════════════════

private data class WikiLensConcept(
    val key: String,
    val id: String,
    val label: String,
    val type: WikiLensConceptType,
    val color: Color,
)

private enum class WikiLensConceptType {
    EXERCISE,
    MUSCLE,
    JOINT,
    PATTERN,
    CHAIN,
    CONCEPT,
}

// ═══════════════════════════════════════════════════════════════════════
// MAIN ENCYCLOPEDIA SCREEN
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiLabHomeScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateToExercises: () -> Unit,
    onNavigateToMuscleAnatomy: () -> Unit,
    onNavigateToJoints: () -> Unit,
    onNavigateToMovementPatterns: () -> Unit,
    onNavigateToBiomechanics: () -> Unit,
    onNavigateToConcepts: () -> Unit,
    onNavigateToExercise: (String) -> Unit,
    onNavigateToMuscle: (String) -> Unit,
    onNavigateToChain: (String) -> Unit,
    onNavigateToConcept: (String) -> Unit,
    onNavigateToJoint: (String) -> Unit,
    onNavigateToPattern: (String) -> Unit,
) {
    val muscles by WikiLabRepository.muscles.collectAsState()
    val joints by WikiLabRepository.joints.collectAsState()
    val tendons by WikiLabRepository.tendons.collectAsState()
    val patterns by WikiLabRepository.patterns.collectAsState()
    val chains by WikiLabRepository.chains.collectAsState()
    val customExercises by CustomExerciseRepository.customExercises.collectAsState()
    val exerciseCatalog = remember(customExercises) {
        (exerciseCatalogSnapshot() + customExercises)
            .associateBy { it.id.lowercase() }
            .values
            .toList()
    }

    val isSearching = searchQuery.isNotBlank()
    val listState = rememberLazyListState()
    LaunchedEffect(searchQuery) {
        listState.scrollToItem(0)
    }

    // ─── Build canonicals for muscle navigation ──────────────────────
    val canonicalMuscles = remember(muscles) {
        muscles.mapNotNull { m ->
            val canonicalId = canonicalWikiLabMuscleIdFromEntityId(m.id)
            if (canonicalId != null) {
                val canonical = WikiLabRepository.getMuscleById(canonicalId)
                if (canonical != null) canonical to canonicalId else null
            } else null
        }.sortedBy { it.first.id }
    }

    // ─── Search results ──────────────────────────────────────────────
    val searchResults = remember(searchQuery, muscles, joints, patterns) {
        if (searchQuery.isBlank()) return@remember emptyList<SearchResult>()
        val q = searchQuery.lowercase()
        val results = mutableListOf<SearchResult>()

        exerciseCatalog
            .filter { calculateSearchScore(it, searchQuery) > 0 }
            .sortedWith(
                compareByDescending<ExerciseMuscleInfo> { calculateSearchScore(it, searchQuery) }
                    .thenBy { kotlin.math.abs(it.name.length - q.length) }
                    .thenBy { it.name }
            )
            .take(5).mapTo(results) {
                SearchResult(it.id, it.name, "Ejercicio", SearchResultType.EXERCISE, APRENDE_LINK_COLOR)
            }

        muscles.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
            .mapNotNull {
                val canonicalId = canonicalWikiLabMuscleIdFromEntityId(it.id) ?: return@mapNotNull null
                WikiLabRepository.getMuscleById(canonicalId)
            }.distinctBy { it.id }
            .sortedBy { it.id }
            .take(5)
            .mapTo(results) {
                SearchResult(it.id, it.name, "Músculo · ${WikiLabRepository.getBodyPartLabel(it.bodyPart)}", SearchResultType.MUSCLE, APRENDE_LINK_COLOR)
            }

        joints.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
            .sortedBy { it.id }
            .take(4).mapTo(results) {
                SearchResult(it.id, it.name, "Articulación · ${WikiLabRepository.getJointTypeLabel(it.type)}", SearchResultType.JOINT, APRENDE_LINK_COLOR)
            }

        patterns.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
            .sortedBy { it.id }
            .take(4).mapTo(results) {
                SearchResult(it.id, it.name, "Patrón de Movimiento", SearchResultType.PATTERN, APRENDE_LINK_COLOR)
            }

        searchConcepts(searchQuery).take(5).mapTo(results) {
            SearchResult(it.id, it.name, "Concepto · ${it.category.label}", SearchResultType.CONCEPT, APRENDE_LINK_COLOR)
        }

        results
    }

    // ─── Build concept pool for the lens ────────────────────────────
    val lensConcepts = remember(canonicalMuscles, joints, patterns, chains, exerciseCatalog) {
        val list = mutableListOf<WikiLensConcept>()
        var idx = 0

        for (ex in exerciseCatalog.sortedBy { it.id }.take(12)) {
            list.add(WikiLensConcept("ex$idx", ex.id, ex.name.take(12), WikiLensConceptType.EXERCISE, LENS_EXERCISE))
            idx++
        }
        for ((muscle, canonicalId) in canonicalMuscles.sortedBy { it.first.id }.take(10)) {
            list.add(WikiLensConcept("mu$idx", canonicalId, muscle.name.take(14), WikiLensConceptType.MUSCLE, LENS_MUSCLE))
            idx++
        }
        for (j in joints.sortedBy { it.id }.take(8)) {
            list.add(WikiLensConcept("jo$idx", j.id, j.name.take(12), WikiLensConceptType.JOINT, LENS_JOINT))
            idx++
        }
        for (p in patterns.sortedBy { it.id }.take(6)) {
            list.add(WikiLensConcept("pa$idx", p.id, p.name.take(12), WikiLensConceptType.PATTERN, LENS_PATTERN))
            idx++
        }
        for (ch in chains.sortedBy { it.id }) {
            list.add(WikiLensConcept("ch$idx", ch.id, ch.name.take(14), WikiLensConceptType.CHAIN, chainColor(ch.id)))
            idx++
        }
        val conceptShortNames = mapOf(
            "volumen-entrenamiento" to "Volumen",
            "intensidad" to "Intensidad",
            "rir" to "RIR",
            "fallo-muscular" to "Fallo",
            "tension-mecanica" to "Tensión",
            "sobrecarga-progresiva" to "Sobrecarga",
            "rom" to "ROM",
            "deload" to "Deload",
        )
        for (c in TRAINING_CONCEPTS_DATABASE.sortedBy { it.id }.take(8)) {
            val label = conceptShortNames[c.id] ?: c.name.take(10)
            list.add(WikiLensConcept("co$idx", c.id, label, WikiLensConceptType.CONCEPT, LENS_CONCEPT))
            idx++
        }
        list
    }

    // ─── Ring distribution coordinates ──────────────────────────────
    val nodeCoords = remember(lensConcepts) {
        val coords = mutableMapOf<String, Offset>()
        val count = lensConcepts.size
        if (count == 0) return@remember coords

        val ringDefs = listOf(
            0.18f to 4,
            0.30f to 6,
            0.42f to 10,
            0.54f to 14,
            0.66f to null,
        )

        var idx = 0
        val toRad = kotlin.math.PI / 180.0
        val centerDp = 225f
        val maxRadiusDp = 185f

        ringDefs.forEachIndexed { ringIndex, (radiusFrac, maxCount) ->
            val effectiveCount = if (maxCount != null) minOf(maxCount, count - idx) else count - idx
            if (effectiveCount <= 0) return@forEachIndexed
            val radius = maxRadiusDp * radiusFrac
            val angleStep = 360.0 / effectiveCount
            val angleOffset = ringIndex * 27.0
            for (i in 0 until effectiveCount) {
                val theta = (angleOffset + i * angleStep) * toRad
                coords[lensConcepts[idx].key] = Offset(
                    centerDp + radius * cos(theta).toFloat(),
                    centerDp + radius * sin(theta).toFloat(),
                )
                idx++
            }
        }
        coords
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(APRENDE_BACKGROUND),
        contentPadding = PaddingValues(bottom = 180.dp),
    ) {
        // ═══════════════════════════════════════════════════════════════
        // CONCEPT LENS (title is curved on the circle)
        // ═══════════════════════════════════════════════════════════════
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                WikiConceptLens(
                    concepts = lensConcepts,
                    coords = nodeCoords,
                    onNavigate = { concept ->
                        when (concept.type) {
                            WikiLensConceptType.EXERCISE -> onNavigateToExercise(concept.id)
                            WikiLensConceptType.MUSCLE -> onNavigateToMuscle(concept.id)
                            WikiLensConceptType.JOINT -> onNavigateToJoint(concept.id)
                            WikiLensConceptType.PATTERN -> onNavigateToPattern(concept.id)
                            WikiLensConceptType.CHAIN -> onNavigateToChain(concept.id)
                            WikiLensConceptType.CONCEPT -> onNavigateToConcept(concept.id)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // COMPACT STATS BADGES
        // ═══════════════════════════════════════════════════════════════
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactStat("${exerciseCatalog.size}", "Ejercicios")
                Spacer(Modifier.width(12.dp))
                Text("•", color = Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.width(12.dp))
                CompactStat("${muscles.size}", "Músculos")
                Spacer(Modifier.width(12.dp))
                Text("•", color = Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.width(12.dp))
                CompactStat("${TRAINING_CONCEPTS_DATABASE.size}", "Conceptos")
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // SEARCH RESULTS (when searching)
        // ═══════════════════════════════════════════════════════════════
        if (isSearching) {
            item {
                Text(
                    "${searchResults.size} resultados para \"$searchQuery\"",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            items(searchResults, key = { "${it.type}-${it.id}" }) { result ->
                SearchResultCard(
                    result = result,
                    onClick = {
                        when (result.type) {
                            SearchResultType.EXERCISE -> onNavigateToExercise(result.id)
                            SearchResultType.MUSCLE -> onNavigateToMuscle(result.id)
                            SearchResultType.JOINT -> onNavigateToJoint(result.id)
                            SearchResultType.PATTERN -> onNavigateToPattern(result.id)
                            SearchResultType.CONCEPT -> onNavigateToConcept(result.id)
                        }
                        onSearchQueryChange("")
                    },
                )
            }

            if (searchResults.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Sin resultados",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // MAIN CONTENT (when NOT searching)
        // ═══════════════════════════════════════════════════════════════
        if (!isSearching) {

            // ─── Section Grid ─────────────────────────────────────────
            item {
                SectionLabel("EXPLORAR", modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WikiSectionTile(
                            title = "Ejercicios",
                            subtitle = "${exerciseCatalog.size} catalogados",
                            onClick = onNavigateToExercises,
                            modifier = Modifier.weight(1f),
                        )
                        WikiSectionTile(
                            title = "Atlas Anatómico",
                            subtitle = "${muscles.size} músculos",
                            onClick = onNavigateToMuscleAnatomy,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WikiSectionTile(
                            title = "Articulaciones",
                            subtitle = "${joints.size} principales",
                            onClick = onNavigateToJoints,
                            modifier = Modifier.weight(1f),
                        )
                        WikiSectionTile(
                            title = "Patrones de Fuerza",
                            subtitle = "${patterns.size} patrones",
                            onClick = onNavigateToMovementPatterns,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WikiSectionTile(
                            title = "Biomecánica",
                            subtitle = "Análisis mecánico",
                            onClick = onNavigateToBiomechanics,
                            modifier = Modifier.weight(1f),
                        )
                        WikiSectionTile(
                            title = "Conceptos Clave",
                            subtitle = "${TRAINING_CONCEPTS_DATABASE.size} lecciones",
                            onClick = onNavigateToConcepts,
                            modifier = Modifier.weight(1f),
                        )
                    }


                }
            }

            // ─── Featured Concepts ────────────────────────────────────
            item {
                SectionLabel(
                    "CONCEPTOS DESTACADOS",
                    modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
                )
            }

            item {
                val featured = remember {
                    TRAINING_CONCEPTS_DATABASE.filter {
                        it.id in listOf("volumen-entrenamiento", "tension-mecanica", "rir", "sobrecarga-progresiva", "rom", "fallo-muscular")
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(featured) { concept ->
                        FeaturedConceptCard(
                            name = concept.name,
                            shortDesc = concept.shortDescription,
                            onClick = { onNavigateToConcept(concept.id) },
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// WIKI CONCEPT LENS — Concentric arc title OUTSIDE the circle
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun WikiConceptLens(
    concepts: List<WikiLensConcept>,
    coords: Map<String, Offset>,
    onNavigate: (WikiLensConcept) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val titleTopInset = 44.dp
    val arcMargin = 10.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val lensSize = minOf(maxWidth, 390.dp)
        val contentSize = lensSize * 1.25f
        val outerHeight = lensSize + titleTopInset
        val radiusPx = with(density) { lensSize.toPx() / 2f }
        val lensSizePx = with(density) { lensSize.toPx() }
        val titleTopInsetPx = with(density) { titleTopInset.toPx() }
        val arcMarginPx = with(density) { arcMargin.toPx() }

        val panLimit = ((scale - 1f).coerceAtLeast(0f) * lensSizePx * 0.40f) + with(density) { 40.dp.toPx() }
        val contentCenterDp = contentSize.value / 2f
        val contentMaxRadiusDp = contentCenterDp * 0.82f

        // Outer Box taller to fit title above the circle
        Box(
            modifier = Modifier
                .width(lensSize)
                .height(outerHeight)
                .align(Alignment.Center),
        ) {
            // Circle at bottom
            Box(
                modifier = Modifier
                    .size(lensSize)
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFF040404))
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.85f, 2.6f)
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-panLimit, panLimit),
                                (offset.y + pan.y).coerceIn(-panLimit, panLimit),
                            )
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offset.x, translationY = offset.y,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.requiredSize(contentSize).background(Color.Transparent)) {
                        concepts.forEach { concept ->
                            val coord = coords[concept.key] ?: return@forEach
                            Box(
                                modifier = Modifier
                                    .offset(x = coord.x.dp - 38.dp, y = coord.y.dp - 11.dp)
                                    .size(76.dp, 22.dp)
                                    .graphicsLayer {
                                        val dx = coord.x - contentCenterDp
                                        val dy = coord.y - contentCenterDp
                                        val rx = dx * scale + (offset.x / this.density)
                                        val ry = dy * scale + (offset.y / this.density)
                                        val dist = sqrt(rx * rx + ry * ry)
                                        val progress = (dist / contentMaxRadiusDp).coerceIn(0f, 1f)
                                        val lensEffect = 1.0f - progress
                                        scaleX = 0.55f + (lensEffect * 0.70f)
                                        scaleY = 0.55f + (lensEffect * 0.70f)
                                        alpha = 0.15f + (lensEffect * 0.85f)
                                    }
                                    .clickable { onNavigate(concept) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = concept.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Medium,
                                        color = concept.color,
                                        fontSize = 10.sp,
                                    ),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            0.0f to Color.Transparent,
                            0.40f to Color.Transparent,
                            0.70f to Color.Black.copy(alpha = 0.25f),
                            0.88f to Color.Black.copy(alpha = 0.70f),
                            1.0f to Color.Black,
                            center = Offset(radiusPx, radiusPx),
                            radius = radiusPx,
                        ),
                    ),
                )
            }

            // Curved title OUTSIDE the circle — CONCENTRIC arc
            // Same center as circle, radius = circleRadius + arcMargin
            val titlePaint = remember {
                Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.WHITE
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
            }

            Canvas(Modifier.fillMaxSize()) {
                val cvW = size.width
                val cvH = size.height
                val circleCx = cvW / 2f
                val circleCy = cvH - radiusPx
                val arcR = radiusPx + arcMarginPx

                titlePaint.textSize = circleCx * 0.18f

                val arcPath = Path().apply {
                    addArc(
                        RectF(circleCx - arcR, circleCy - arcR, circleCx + arcR, circleCy + arcR),
                        215f,
                        110f,
                    )
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawTextOnPath(
                        "Aprende",
                        arcPath,
                        0f,
                        -4.dp.toPx(),
                        titlePaint,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// COMPONENTS
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun CompactStat(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            ),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
            ),
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        ),
    )
}

@Composable
private fun WikiSectionTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(78.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = APRENDE_PANEL),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            AutoFitText(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.45f),
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FeaturedConceptCard(
    name: String,
    shortDesc: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = APRENDE_PANEL),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "CONCEPTO DESTACADO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 1.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            AutoFitText(
                text = name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                shortDesc,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    lineHeight = 15.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// SEARCH RESULT TYPES
// ═══════════════════════════════════════════════════════════════════════

private enum class SearchResultType { EXERCISE, MUSCLE, JOINT, PATTERN, CONCEPT }

private data class SearchResult(
    val id: String,
    val name: String,
    val subtitle: String,
    val type: SearchResultType,
    val color: Color,
)

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = APRENDE_PANEL_ELEVATED),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = result.color.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (result.type) {
                            SearchResultType.EXERCISE -> Icons.Default.FitnessCenter
                            SearchResultType.MUSCLE -> Icons.Default.Accessibility
                            SearchResultType.JOINT -> Icons.Default.Hub
                            SearchResultType.PATTERN -> Icons.Default.Sync
                            SearchResultType.CONCEPT -> Icons.Default.School
                        },
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = result.color,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    result.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = result.color,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                modifier = Modifier.size(18.dp),
                tint = result.color.copy(alpha = 0.5f),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════

private fun chainColor(id: String): Color = when (id) {
    "tren-superior", "tren-inferior", "core", "cadena-anterior", "cadena-posterior" -> APRENDE_LINK_COLOR
    else -> Color(0xFF7F8D96)
}

@Composable
private fun AutoFitText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
) {
    var sizeMultiplier by remember(text) { mutableStateOf(1f) }

    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontSize = style.fontSize * sizeMultiplier),
        maxLines = maxLines,
        textAlign = textAlign,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow && sizeMultiplier > 0.65f) {
                sizeMultiplier *= 0.85f
            }
        },
    )
}
