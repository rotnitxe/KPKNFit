package com.example.kpkn.screens.nutrition.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.localai.LocalAiManager
import com.example.kpkn.data.localai.ParseMode
import com.example.kpkn.data.localai.ParseOptions
import com.example.kpkn.data.localai.parseFreeFormNutrition
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.domain.nutrition.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// ─── Constants ───────────────────────────────────────────────────────────────

private const val PREF_FILE = "kpkn_nutrition_prefs"
private const val PREF_ANALYSIS_MODE = "analysis_mode"
private const val MODE_BASIC = "BASIC"
private const val MODE_PRO = "PRO"
private const val MODE_UNSET = "UNSET"

private val MEAL_OPTIONS = listOf(
    MealType.BREAKFAST to "Desayuno",
    MealType.LUNCH to "Almuerzo",
    MealType.DINNER to "Cena",
    MealType.SNACK to "Snack",
)

private val PROTEIN_COLOR = Color(0xFFB3261E)
private val CARBS_COLOR = Color(0xFF6750A4)
private val FATS_COLOR = Color(0xFF006A6A)
private val PRO_COLOR = Color(0xFF7C3AED)

private data class ResolvedTag(
    val id: String = UUID.randomUUID().toString(),
    val tag: String,
    val portion: PortionPreset = PortionPreset.MEDIUM,
    val quantity: Int = 1,
    val amountGrams: Double? = null,
    val cookingMethod: CookingMethod? = null,
    val foodItem: FoodItem? = null,
    val loggedFood: LoggedFood? = null,
    val isResolved: Boolean = false,
    val isFuzzyMatch: Boolean = false,
    val analysisSource: AnalysisSource = AnalysisSource.RULES,
    val statusText: String = "Pendiente",
    val isExpanded: Boolean = false,
)

// ─── Composable ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLoggerDrawer(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSave: (NutritionLog) -> Unit,
    foodDatabase: List<FoodItem>,
    initialDate: String = java.time.LocalDate.now().toString(),
    initialMealType: MealType = MealType.LUNCH,
) {
    if (!isOpen) return

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE) }
    val nutritionRepo = NutritionRepository.getInstance()

    var description by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(initialMealType) }
    var logDate by remember { mutableStateOf(initialDate) }
    var tags by remember { mutableStateOf(emptyList<ResolvedTag>()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<FoodItem>()) }
    var activeTab by remember { mutableIntStateOf(0) }
    var showSuccess by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var localAiStatus by remember { mutableStateOf(LocalAiManager.status()) }

    // Modo de análisis: UNSET → mostrar selector, BASIC / PRO → usar directo
    var analysisMode by remember {
        mutableStateOf(prefs.getString(PREF_ANALYSIS_MODE, MODE_UNSET) ?: MODE_UNSET)
    }
    var showModeDialog by remember { mutableStateOf(false) }

    // Progreso de copia del modelo (0.0–1.0)
    val copyProgress by LocalAiManager.copyProgress.collectAsState()

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            // Prevent accidental dismiss when there's content (description typed or foods added)
            if (value == SheetValue.Hidden) description.isBlank() && tags.isEmpty()
            else true
        },
    )
    val listState = rememberLazyListState()

    // Auto-scroll to show newly detected foods when analysis finishes
    LaunchedEffect(isAnalyzing) {
        if (!isAnalyzing && tags.isNotEmpty()) {
            val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            listState.animateScrollToItem(lastIndex)
        }
    }

    // Mostrar diálogo de selección si es la primera vez en tab Descripción
    LaunchedEffect(activeTab) {
        if (activeTab == 0 && analysisMode == MODE_UNSET) {
            showModeDialog = true
        }
    }

    // Pre-cargar modelo sólo si modo PRO ya fue seleccionado
    LaunchedEffect(analysisMode) {
        if (analysisMode == MODE_PRO) {
            localAiStatus = LocalAiManager.ensureReady(context, retries = 3)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    fun saveMode(mode: String) {
        analysisMode = mode
        prefs.edit().putString(PREF_ANALYSIS_MODE, mode).apply()
        if (mode == MODE_PRO) {
            scope.launch { localAiStatus = LocalAiManager.ensureReady(context, retries = 3) }
        }
    }

    fun resolveTags(parsed: ParsedMealDescription) {
        tags = parsed.items.map { item ->
            val food = findFoodByNormalized(item.tag)
            val source = item.analysisSource
            if (food != null) {
                val logged = scaleFoodByPortion(food, item.quantity, item.portion, item.amountGrams, item.cookingMethod)
                ResolvedTag(
                    tag = item.tag,
                    portion = item.portion,
                    quantity = item.quantity,
                    amountGrams = item.amountGrams,
                    cookingMethod = item.cookingMethod,
                    foodItem = food,
                    loggedFood = logged.copy(analysisSource = AnalysisSource.DATABASE),
                    isResolved = true,
                    isFuzzyMatch = false,
                    analysisSource = AnalysisSource.DATABASE,
                    statusText = "BD ✓",
                )
            } else if (item.amountGrams != null && item.amountGrams > 0) {
                val mac = item.macroOverrides
                val logged = createLoggedFood(
                    foodName = item.tag,
                    amount = item.amountGrams,
                    calories = mac?.calories ?: 0.0,
                    protein = mac?.protein ?: 0.0,
                    carbs = mac?.carbs ?: 0.0,
                    fats = mac?.fats ?: 0.0,
                    portion = item.portion,
                    cookingMethod = item.cookingMethod,
                )
                val trustLabel = when {
                    source == AnalysisSource.LOCAL_AI_ESTIMATE && mac != null -> "IA ~"
                    source == AnalysisSource.LOCAL_HEURISTIC && mac != null -> "Heur. ~"
                    mac != null -> "~ Estimado"
                    else -> "⚠ Sin ref."
                }
                ResolvedTag(
                    tag = item.tag,
                    portion = item.portion,
                    quantity = item.quantity,
                    amountGrams = item.amountGrams,
                    cookingMethod = item.cookingMethod,
                    foodItem = null,
                    loggedFood = logged.copy(analysisSource = source),
                    isResolved = mac != null,
                    isFuzzyMatch = true,
                    analysisSource = source,
                    statusText = trustLabel,
                )
            } else {
                ResolvedTag(
                    tag = item.tag,
                    portion = item.portion,
                    quantity = item.quantity,
                    foodItem = null,
                    loggedFood = null,
                    isResolved = false,
                    analysisSource = source,
                    statusText = "⚠ Sin ref.",
                )
            }
        }
    }

    fun analyzeDescription() {
        if (description.isBlank() || isAnalyzing) return

        nutritionRepo.findMealTemplateMatch(description)?.let { template ->
            tags = template.foods.map { food ->
                val foodItem = findFoodByNormalized(food.foodName)
                ResolvedTag(
                    tag = food.foodName,
                    portion = food.portionPreset ?: PortionPreset.MEDIUM,
                    quantity = food.quantity,
                    amountGrams = food.amount.takeIf { it > 0 },
                    cookingMethod = food.cookingMethod,
                    foodItem = foodItem,
                    loggedFood = food.copy(analysisSource = AnalysisSource.USER_MEMORY),
                    isResolved = true,
                    isFuzzyMatch = true,
                    analysisSource = AnalysisSource.USER_MEMORY,
                    statusText = "Memoria ✓",
                )
            }
            localAiStatus = LocalAiManager.status()
            return
        }

        isAnalyzing = true
        scope.launch {
            try {
                val userKnownFoods = nutritionRepo.mealTemplates.value.flatMap { template ->
                    buildList {
                        add(template.name)
                        if (template.description.isNotBlank()) add(template.description)
                        template.foods.forEach { food ->
                            add(food.foodName)
                        }
                    }
                }
                val parsed = withContext(Dispatchers.Default) {
                    when (analysisMode) {
                        MODE_PRO -> parseFreeFormNutrition(
                            description,
                            ParseOptions(
                                mode = ParseMode.LOCAL_AI,
                                knownFoods = userKnownFoods,
                            ),
                        )
                        else -> parseFreeFormNutrition(
                            description,
                            ParseOptions(
                                mode = ParseMode.RULES,
                                knownFoods = userKnownFoods,
                            ),
                        )
                    }
                }
                resolveTags(parsed)
            } catch (e: Exception) {
                android.util.Log.w("FoodLogger", "Parse failed, usando determinístico", e)
                resolveTags(parseMealDescription(description))
            } finally {
                isAnalyzing = false
                localAiStatus = LocalAiManager.status()
            }
        }
    }

    fun resolveFood(tagId: String, food: FoodItem) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val logged = scaleFoodByPortion(food, tag.quantity, tag.portion, tag.amountGrams, tag.cookingMethod)
                tag.copy(
                    foodItem = food,
                    loggedFood = logged.copy(analysisSource = AnalysisSource.DATABASE),
                    isResolved = true,
                    isFuzzyMatch = false,
                    analysisSource = AnalysisSource.DATABASE,
                    statusText = "BD ✓",
                )
            } else tag
        }
    }

    fun updateTagPortion(tagId: String, portion: PortionPreset) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val food = tag.foodItem
                if (food != null) {
                    val multiplier = PORTION_MULTIPLIERS[portion] ?: 1.0
                    val newGrams = food.servingSize * tag.quantity * multiplier
                    val logged = scaleFoodByPortion(food, tag.quantity, portion, newGrams, tag.cookingMethod)
                    tag.copy(portion = portion, amountGrams = newGrams, loggedFood = logged)
                } else {
                    tag.copy(portion = portion)
                }
            } else tag
        }
    }

    fun updateTagGrams(tagId: String, grams: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val food = tag.foodItem
                val logged = if (food != null) scaleFoodByPortion(food, tag.quantity, tag.portion, grams, tag.cookingMethod) else null
                tag.copy(amountGrams = grams, loggedFood = logged)
            } else tag
        }
    }

    fun updateTagCalories(tagId: String, calories: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) { val old = tag.loggedFood ?: return@map tag; tag.copy(loggedFood = old.copy(calories = calories)) } else tag
        }
    }

    fun updateTagProtein(tagId: String, protein: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) { val old = tag.loggedFood ?: return@map tag; tag.copy(loggedFood = old.copy(protein = protein)) } else tag
        }
    }

    fun updateTagCarbs(tagId: String, carbs: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) { val old = tag.loggedFood ?: return@map tag; tag.copy(loggedFood = old.copy(carbs = carbs)) } else tag
        }
    }

    fun updateTagFats(tagId: String, fats: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) { val old = tag.loggedFood ?: return@map tag; tag.copy(loggedFood = old.copy(fats = fats)) } else tag
        }
    }

    fun removeTag(tagId: String) { tags = tags.filter { it.id != tagId } }
    fun toggleTagExpanded(tagId: String) {
        tags = tags.map { if (it.id == tagId) it.copy(isExpanded = !it.isExpanded) else it }
    }

    fun performSearch() {
        if (searchQuery.isBlank()) return
        val normalized = searchQuery.trim().lowercase()
        searchResults = foodDatabase.filter { food ->
            food.name.lowercase().contains(normalized) ||
            food.searchAliases.any { it.lowercase().contains(normalized) }
        }.take(10)
    }

    fun saveLog() {
        val resolvedFoods = tags.mapNotNull { it.loggedFood }
        if (resolvedFoods.isEmpty()) return
        val log = NutritionLog(
            id = UUID.randomUUID().toString(),
            date = "${logDate}T12:00:00.000Z",
            mealType = mealType,
            foods = resolvedFoods,
            status = NutritionStatus.CONSUMED,
        )
        onSave(log)
        showSuccess = true
    }

    val tagTotals = remember(tags) {
        DailyMacroTotals(
            calories = tags.sumOf { it.loggedFood?.calories ?: 0.0 },
            protein = tags.sumOf { it.loggedFood?.protein ?: 0.0 },
            carbs = tags.sumOf { it.loggedFood?.carbs ?: 0.0 },
            fats = tags.sumOf { it.loggedFood?.fats ?: 0.0 },
        )
    }

    // ─── Diálogo Selector de Modo ────────────────────────────────────────────

    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { /* No cerrar sin seleccionar */ },
            title = {
                Text(
                    text = "¿Cómo analizar tu comida?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Elige cómo quieres que KPKN interprete tu descripción. Puedes cambiar esto después.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Opción BÁSICO
                    ModeOptionCard(
                        icon = Icons.Default.FlashOn,
                        iconTint = Color(0xFFF59E0B),
                        title = "Básico",
                        subtitle = "Rápido y siempre disponible. Usa la base de datos local para identificar alimentos.",
                        badge = null,
                        onClick = {
                            showModeDialog = false
                            saveMode(MODE_BASIC)
                        },
                    )

                    // Opción PRO
                    ModeOptionCard(
                        icon = Icons.Default.AutoAwesome,
                        iconTint = PRO_COLOR,
                        title = "Pro · IA Local",
                        subtitle = "Análisis con Gemma 4 en tu dispositivo. Más preciso para descripciones libres.",
                        badge = "GEMMA 4",
                        onClick = {
                            showModeDialog = false
                            saveMode(MODE_PRO)
                        },
                    )
                }
            },
            confirmButton = {},
            shape = RoundedCornerShape(24.dp),
        )
    }

    // ─── Sheet ───────────────────────────────────────────────────────────────

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Header ───────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Registrar Comida",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    // Badge de modo actual (toca para cambiar)
                    if (analysisMode != MODE_UNSET) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (analysisMode == MODE_PRO) PRO_COLOR.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.clickable { showModeDialog = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    if (analysisMode == MODE_PRO) Icons.Default.AutoAwesome else Icons.Default.FlashOn,
                                    null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (analysisMode == MODE_PRO) PRO_COLOR else Color(0xFFF59E0B),
                                )
                                Text(
                                    text = if (analysisMode == MODE_PRO) "Pro" else "Básico",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (analysisMode == MODE_PRO) PRO_COLOR
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ── Meal Type Selector ───────────────────────────────────────────
            item { MealTypeSelector(mealType = mealType, onSelect = { mealType = it }) }

            // ── Date ────────────────────────────────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = try {
                            java.time.LocalDate.parse(logDate)
                                .format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", java.util.Locale("es")))
                        } catch (e: Exception) { logDate },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // ── Tab Selector ────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TabChip("Describir", activeTab == 0) { activeTab = 0 }
                    TabChip("Buscar", activeTab == 1) { activeTab = 1 }
                }
            }

            // ── Description Tab ──────────────────────────────────────────────
            if (activeTab == 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    if (analysisMode == MODE_PRO)
                                        "Ej: almorcé pollo a la plancha con arroz y una palta"
                                    else
                                        "Ej: 200g pechuga de pollo, 150g arroz blanco cocido"
                                )
                            },
                            minLines = 2,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { analyzeDescription() }),
                        )

                        // Barra de progreso de copia del modelo (sólo modo PRO mientras copia)
                        AnimatedVisibility(
                            visible = analysisMode == MODE_PRO && copyProgress > 0f && copyProgress < 1f,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Preparando Gemma 4...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PRO_COLOR,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${(copyProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PRO_COLOR,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { copyProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = PRO_COLOR,
                                )
                            }
                        }

                        Button(
                            onClick = { analyzeDescription() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (analysisMode == MODE_PRO) PRO_COLOR
                                                 else MaterialTheme.colorScheme.primary,
                            ),
                            enabled = description.isNotBlank() && !isAnalyzing &&
                                      (analysisMode != MODE_PRO || copyProgress >= 1f || localAiStatus.ready || !LocalAiManager.status().ready.not()),
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = LocalContentColor.current,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ANALIZANDO...")
                            } else {
                                Icon(
                                    if (analysisMode == MODE_PRO) Icons.Default.AutoAwesome else Icons.Default.FlashOn,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (analysisMode == MODE_PRO) "ANALIZAR CON IA" else "ANALIZAR")
                            }
                        }

                        // Status line
                        when (analysisMode) {
                            MODE_PRO -> {
                                val statusText = when {
                                    copyProgress > 0f && copyProgress < 1f ->
                                        "Copiando modelo al dispositivo (primera vez)..."
                                    localAiStatus.ready ->
                                        "Gemma 4 activa · ${localAiStatus.modelVersion}"
                                    localAiStatus.error != null ->
                                        "IA no disponible · usando fallback inteligente"
                                    else -> "Cargando Gemma 4..."
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        localAiStatus.ready -> Color(0xFF4CAF50)
                                        localAiStatus.error != null -> MaterialTheme.colorScheme.error
                                        else -> PRO_COLOR
                                    },
                                )
                            }
                            MODE_BASIC -> Text(
                                text = "Modo Básico · base de datos local (${foodDatabase.size} alimentos)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── Search Tab ──────────────────────────────────────────────────
            if (activeTab == 1) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar alimento...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                    )
                }
                items(searchResults) { food ->
                    FoodSearchResultCard(food = food, onClick = {
                        val logged = scaleFoodByPortion(food)
                        val tag = ResolvedTag(
                            tag = food.name,
                            portion = PortionPreset.MEDIUM,
                            quantity = 1,
                            amountGrams = food.servingSize,
                            foodItem = food,
                            loggedFood = logged,
                            isResolved = true,
                            statusText = "✓ Listo",
                        )
                        tags = tags + tag
                        searchQuery = ""
                        searchResults = emptyList()
                        activeTab = 0
                    })
                }
            }

            // ── Tag List ────────────────────────────────────────────────────
            if (tags.isNotEmpty()) {
                item {
                    Text(
                        text = "ALIMENTOS DETECTADOS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (0.1f).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(tags, key = { it.id }) { tag ->
                    Box(modifier = Modifier.animateItem()) {
                        TagCard(
                            tag = tag,
                            onToggleExpanded = { toggleTagExpanded(tag.id) },
                            onPortionChange = { updateTagPortion(tag.id, it) },
                            onGramsChange = { updateTagGrams(tag.id, it) },
                            onCaloriesChange = { updateTagCalories(tag.id, it) },
                            onProteinChange = { updateTagProtein(tag.id, it) },
                            onCarbsChange = { updateTagCarbs(tag.id, it) },
                            onFatsChange = { updateTagFats(tag.id, it) },
                            onRemove = { removeTag(tag.id) },
                            foodDatabase = foodDatabase,
                            onResolve = { food -> resolveFood(tag.id, food) },
                        )
                    }
                }
            }

            // ── Totals ──────────────────────────────────────────────────────
            if (tags.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("TOTAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1f.sp)
                                Text("${kotlin.math.round(tagTotals.calories)} kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MacroBadge("P", "${kotlin.math.round(tagTotals.protein)}g", PROTEIN_COLOR)
                                MacroBadge("C", "${kotlin.math.round(tagTotals.carbs)}g", CARBS_COLOR)
                                MacroBadge("G", "${kotlin.math.round(tagTotals.fats)}g", FATS_COLOR)
                            }
                        }
                    }
                }
            }

            // ── Save ────────────────────────────────────────────────────────
            item {
                Button(
                    onClick = { saveLog() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = tags.any { it.loggedFood != null },
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GUARDAR REGISTRO", fontWeight = FontWeight.Black)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // ── Success Snackbar ─────────────────────────────────────────────────────
    if (showSuccess) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            showSuccess = false
            onDismiss()
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("¡Comida registrada!", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Mode Option Card ────────────────────────────────────────────────────────

@Composable
private fun ModeOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badge: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PRO_COLOR,
                        ) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Sub-composables ─────────────────────────────────────────────────────────

@Composable
private fun MealTypeSelector(mealType: MealType, onSelect: (MealType) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MEAL_OPTIONS) { (type, label) ->
            val selected = type == mealType
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.clickable { onSelect(type) },
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TabChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MacroBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), fontWeight = FontWeight.ExtraBold, color = color)
        Text(text = value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FoodSearchResultCard(food: FoodItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${kotlin.math.round(food.calories)} kcal · P ${food.protein}g · C ${food.carbs}g · G ${food.fats}g / ${food.servingSize}${food.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TagCard(
    tag: ResolvedTag,
    onToggleExpanded: () -> Unit,
    onPortionChange: (PortionPreset) -> Unit,
    onGramsChange: (Double) -> Unit,
    onCaloriesChange: (Double) -> Unit,
    onProteinChange: (Double) -> Unit,
    onCarbsChange: (Double) -> Unit,
    onFatsChange: (Double) -> Unit,
    onRemove: () -> Unit,
    foodDatabase: List<FoodItem>,
    onResolve: (FoodItem) -> Unit,
) {
    val logged = tag.loggedFood

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tag.isResolved) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = tag.tag, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tag.statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (tag.analysisSource) {
                                AnalysisSource.DATABASE -> Color(0xFF4CAF50)
                                AnalysisSource.LOCAL_AI_ESTIMATE -> PRO_COLOR
                                AnalysisSource.LOCAL_HEURISTIC -> Color(0xFFFF9800)
                                else -> if (tag.isResolved) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    if (logged != null) {
                        Text(
                            text = "${kotlin.math.round(logged.calories)} kcal · P ${kotlin.math.round(logged.protein)}g · C ${kotlin.math.round(logged.carbs)}g · G ${kotlin.math.round(logged.fats)}g",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(32.dp)) {
                        Icon(if (tag.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            AnimatedVisibility(visible = tag.isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Porción", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PortionPreset.entries.forEach { preset ->
                            val label = when (preset) {
                                PortionPreset.SMALL -> "Pequeño"
                                PortionPreset.MEDIUM -> "Mediano"
                                PortionPreset.LARGE -> "Grande"
                                PortionPreset.EXTRA -> "Extra"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (tag.portion == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.clickable { onPortionChange(preset) },
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (tag.portion == preset) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (tag.amountGrams != null || tag.loggedFood != null) {
                        Text("Gramos (${kotlin.math.round(tag.amountGrams ?: tag.loggedFood?.amount ?: 100.0)}g)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        Slider(
                            value = ((tag.amountGrams ?: tag.loggedFood?.amount ?: 100.0) / 500.0).toFloat().coerceIn(0f, 1f),
                            onValueChange = { onGramsChange(kotlin.math.round(it * 500.0)) },
                            valueRange = 0f..1f,
                        )
                    }

                    Text("Ajustar macros", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                    MacroOverrideRow("Calorías", logged?.calories ?: 0.0, onCaloriesChange)
                    MacroOverrideRow("Proteína", logged?.protein ?: 0.0, onProteinChange)
                    MacroOverrideRow("Carbohidratos", logged?.carbs ?: 0.0, onCarbsChange)
                    MacroOverrideRow("Grasas", logged?.fats ?: 0.0, onFatsChange)

                    if (!tag.isResolved) {
                        Text("Resoluciones sugeridas:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        val suggestions = foodDatabase.filter { food ->
                            val n = tag.tag.lowercase()
                            food.name.lowercase().contains(n) || n.contains(food.name.lowercase())
                        }.take(5)
                        if (suggestions.isEmpty()) {
                            Text("No se encontraron coincidencias.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            suggestions.forEach { food ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth().clickable { onResolve(food) },
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(food.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("${kotlin.math.round(food.calories)} kcal / ${food.servingSize}${food.unit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroOverrideRow(label: String, value: Double, onChange: (Double) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - 5).coerceAtLeast(0.0)) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = "${kotlin.math.round(value)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onChange((value + 5).coerceAtMost(9999.0)) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
            }
        }
    }
}
