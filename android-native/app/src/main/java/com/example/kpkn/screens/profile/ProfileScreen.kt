package com.example.kpkn.screens.profile

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.adapt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.data.profile.ProfilePhotoStore
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.competitions.ProfileCompetitionsArchive
import com.example.kpkn.domain.training.NutritionGoalProgress
import com.example.kpkn.domain.training.StarredExerciseProgress
import com.example.kpkn.domain.training.buildNutritionGoalProgress
import com.example.kpkn.domain.training.buildStarredExerciseProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val settings: Settings = Settings(),
    val starredExercises: List<StarredExerciseProgress> = emptyList(),
    val nutritionGoal: NutritionGoalProgress? = null,
)

class ProfileViewModel : ViewModel() {
    private val programRepository = ProgramRepository.getInstance()
    private val nutritionRepository = NutritionRepository.getInstance()

    private val activeNutritionPlan = nutritionRepository.nutritionPlans
        .combine(nutritionRepository.activeNutritionPlanId) { plans, activeId ->
            activeId?.let { id -> plans.firstOrNull { it.id == id } }
        }

    val uiState: StateFlow<ProfileUiState> = combine(
        programRepository.settings,
        programRepository.programs,
        programRepository.history,
        activeNutritionPlan,
        nutritionRepository.bodyProgressRepository.observations,
    ) { settings, programs, history, plan, observations ->
        ProfileUiState(
            settings = settings,
            starredExercises = buildStarredExerciseProgress(programs, history),
            nutritionGoal = buildNutritionGoalProgress(plan, observations),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun saveProfile(
        name: String,
        gender: Gender?,
        heightText: String,
        weightText: String,
    ): String? {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return "Escribe un nombre para continuar."
        val current = programRepository.settings.value
        val height = parseOptionalPositive(heightText)
            ?: if (heightText.isBlank()) null else return "La estatura debe ser mayor que cero."
        val displayedWeight = parseOptionalPositive(weightText)
            ?: if (weightText.isBlank()) null else return "El peso debe ser mayor que cero."
        val weightKg = displayedWeight?.let { value ->
            if (current.weightUnit == WeightUnit.LBS) value / LB_PER_KG else value
        }
        programRepository.updateSettings { settings ->
            settings.copy(
                username = normalizedName,
                onboardingNameDone = true,
                userVitals = settings.userVitals.copy(
                    gender = gender,
                    height = height,
                    weight = weightKg,
                ),
            )
        }
        return null
    }

    fun savePhoto(context: android.content.Context, uri: android.net.Uri, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ProfilePhotoStore.saveFromUri(context, uri) }
                .onSuccess { token ->
                    programRepository.updateSettings { it.copy(profilePicture = token) }
                }
                .onFailure { error ->
                    withContext(Dispatchers.Main) { onError(error.message ?: "No se pudo guardar la foto") }
                }
        }
    }

    fun removePhoto(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            ProfilePhotoStore.delete(context)
            programRepository.updateSettings { it.copy(profilePicture = null) }
        }
    }

    private fun parseOptionalPositive(value: String): Double? {
        if (value.isBlank()) return null
        return value.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    companion object {
        private const val LB_PER_KG = 2.2046226218
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onOpenCompetition: (String) -> Unit = {},
    onCreateCompetition: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel { ProfileViewModel() },
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var editMode by remember { mutableStateOf(false) }
    var name by remember(state.settings.username) { mutableStateOf(state.settings.username.takeUnless { it == "Usuario" }.orEmpty()) }
    var gender by remember(state.settings.userVitals.gender) { mutableStateOf(state.settings.userVitals.gender) }
    var heightText by remember(state.settings.userVitals.height) {
        mutableStateOf(state.settings.userVitals.height?.formatInput().orEmpty())
    }
    var weightText by remember(state.settings.userVitals.weight, state.settings.weightUnit) {
        mutableStateOf(state.settings.userVitals.weight?.let { displayWeight(it, state.settings.weightUnit).formatInput() }.orEmpty())
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    fun resetDraftFromSettings() {
        name = state.settings.username.takeUnless { it == "Usuario" }.orEmpty()
        gender = state.settings.userVitals.gender
        heightText = state.settings.userVitals.height?.formatInput().orEmpty()
        weightText = state.settings.userVitals.weight?.let { displayWeight(it, state.settings.weightUnit).formatInput() }.orEmpty()
    }

    val avatar: Bitmap? by produceState<Bitmap?>(initialValue = null, state.settings.profilePicture) {
        value = withContext(Dispatchers.IO) {
            ProfilePhotoStore.loadBitmap(context, state.settings.profilePicture)
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.savePhoto(context, uri) { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
    ) { padding ->
        val profileAdapt = LocalViewportAdapt.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp.adapt(profileAdapt)),
            verticalArrangement = Arrangement.spacedBy(16.dp.adapt(profileAdapt)),
        ) {
            Spacer(Modifier.height(2.dp))
            ProfileIdentityCard(
                settings = state.settings,
                avatar = avatar,
                editMode = editMode,
                onChoosePhoto = { photoPicker.launch(arrayOf("image/*")) },
                onRemovePhoto = { viewModel.removePhoto(context) },
                onEdit = {
                    resetDraftFromSettings()
                    validationError = null
                    editMode = true
                },
            )

            if (editMode) {
                ProfileEditor(
                    name = name,
                    onNameChange = { name = it },
                    gender = gender,
                    onGenderChange = { gender = it },
                    heightText = heightText,
                    onHeightChange = { heightText = it },
                    weightText = weightText,
                    onWeightChange = { weightText = it },
                    weightUnit = state.settings.weightUnit,
                    validationError = validationError,
                    onCancel = {
                        resetDraftFromSettings()
                        editMode = false
                        validationError = null
                    },
                    onSave = {
                        validationError = viewModel.saveProfile(name, gender, heightText, weightText)
                        if (validationError == null) editMode = false
                    },
                )
            } else {
                BodyDataCard(settings = state.settings)
            }

            StarredExercisesCard(state.starredExercises)
            ProfileCompetitionsArchive(
                onOpenCompetition = onOpenCompetition,
                onCreateCompetition = onCreateCompetition,
            )
            NutritionProgressCard(state.nutritionGoal)
            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun ProfileIdentityCard(
    settings: Settings,
    avatar: Bitmap?,
    editMode: Boolean,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onEdit: () -> Unit,
) {
    val initials = settings.username.trim().split(Regex("\\s+")).take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "U" }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF102A35), Color(0xFF263E61), Color(0xFF181B2B))))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (avatar != null) {
                        Image(
                            bitmap = avatar.asImageBitmap(),
                            contentDescription = "Foto de perfil",
                            modifier = Modifier.size(88.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(88.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(initials, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp).clickable(onClick = onChoosePhoto)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Cambiar foto", tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        settings.username.takeUnless { it.isBlank() || it == "Usuario" } ?: "Añade tu nombre",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Text(if (avatar == null) "Personaliza tu foto y tus datos" else "Tu perfil personal", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
                    if (avatar != null) {
                        TextButton(onClick = onRemovePhoto, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("Quitar foto", color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
                        }
                    }
                }
                if (!editMode) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar perfil", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditor(
    name: String,
    onNameChange: (String) -> Unit,
    gender: Gender?,
    onGenderChange: (Gender?) -> Unit,
    heightText: String,
    onHeightChange: (String) -> Unit,
    weightText: String,
    onWeightChange: (String) -> Unit,
    weightUnit: WeightUnit,
    validationError: String?,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var genderMenuExpanded by remember { mutableStateOf(false) }
    ProfileCard(title = "Editar datos") {
        OutlinedTextField(value = name, onValueChange = onNameChange, modifier = Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true)
        Box {
            OutlinedButton(onClick = { genderMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Sexo: ${gender?.label() ?: "No especificar"}", modifier = Modifier.fillMaxWidth())
            }
            DropdownMenu(expanded = genderMenuExpanded, onDismissRequest = { genderMenuExpanded = false }) {
                DropdownMenuItem(text = { Text("No especificar") }, onClick = { onGenderChange(null); genderMenuExpanded = false })
                Gender.values().forEach { option ->
                    DropdownMenuItem(text = { Text(option.label()) }, onClick = { onGenderChange(option); genderMenuExpanded = false })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = heightText, onValueChange = onHeightChange, modifier = Modifier.weight(1f), label = { Text("Estatura (cm)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = weightText, onValueChange = onWeightChange, modifier = Modifier.weight(1f), label = { Text("Peso (${weightUnit.label()})") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Cancelar")
            }
            Button(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun BodyDataCard(settings: Settings) {
    ProfileCard(title = "Datos corporales") {
        ProfileDataRow("Sexo", settings.userVitals.gender?.label() ?: "No especificado")
        ProfileDataRow("Estatura", settings.userVitals.height?.let { "${it.formatInput()} cm" } ?: "Pendiente")
        ProfileDataRow("Peso", settings.userVitals.weight?.let { "${displayWeight(it, settings.weightUnit).formatInput()} ${settings.weightUnit.label()}" } ?: "Pendiente")
    }
}

@Composable
private fun StarredExercisesCard(exercises: List<StarredExerciseProgress>) {
    ProfileCard(title = "Ejercicios estrella", icon = Icons.Default.FitnessCenter) {
        if (exercises.isEmpty()) {
            EmptyProfileHint("Marca ejercicios con estrella desde tus sesiones para seguir aquí su RM y objetivo.")
        } else {
            exercises.forEachIndexed { index, exercise ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(exercise.name, fontWeight = FontWeight.Bold)
                        val current = exercise.bestEstimated1RM?.let { "${it.formatInput()} kg eRM" } ?: "Sin registros todavía"
                        val goal = exercise.goal1RM?.let { "Meta ${it.formatInput()} kg" } ?: "Sin meta RM"
                        Text("$current · $goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val progressFraction = exercise.progressFraction
                        if (progressFraction != null) {
                            LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)))
                            Text("${(progressFraction * 100).toInt()} % hacia la meta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Registra una marca y define una meta para ver el avance.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (index < exercises.lastIndex) Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun NutritionProgressCard(progress: NutritionGoalProgress?) {
    ProfileCard(title = "Progreso nutricional", icon = Icons.Default.Restaurant) {
        when {
            progress == null -> EmptyProfileHint("Crea una meta corporal para empezar a seguir tu avance.")
            progress.currentValue == null -> EmptyProfileHint("Aún no hay una medición reciente para esta meta.")
            progress.targetValue == null -> EmptyProfileHint("Tu plan todavía no tiene un objetivo corporal definido.")
            else -> {
                Text(progress.metric.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ProgressValue("Inicio", progress.startValue, progress.metric)
                    ProgressValue("Ahora", progress.currentValue, progress.metric)
                    ProgressValue("Meta", progress.targetValue, progress.metric)
                }
                Spacer(Modifier.height(10.dp))
                if (progress.percent != null) {
                    LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)))
                    Text("${progress.percent} % completado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Registra un valor inicial para calcular el porcentaje.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProgressValue(label: String, value: Double?, metric: GoalMetric) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.let { "${it.formatInput()} ${metric.unitLabel()}" } ?: "—", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun ProfileDataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyProfileHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun Gender.label(): String = when (this) {
    Gender.MALE -> "Masculino"
    Gender.FEMALE -> "Femenino"
    Gender.OTHER -> "Otro"
}

private fun WeightUnit.label(): String = when (this) {
    WeightUnit.KG -> "kg"
    WeightUnit.LBS -> "lb"
}

private fun GoalMetric.label(): String = when (this) {
    GoalMetric.WEIGHT -> "Meta de peso"
    GoalMetric.BODY_FAT -> "Meta de grasa corporal"
    GoalMetric.MUSCLE_MASS -> "Meta de masa muscular"
}

private fun GoalMetric.unitLabel(): String = when (this) {
    GoalMetric.WEIGHT -> "kg"
    GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> "%"
}

private fun displayWeight(weightKg: Double, unit: WeightUnit): Double = if (unit == WeightUnit.LBS) weightKg * 2.2046226218 else weightKg

private fun Double.formatInput(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(java.util.Locale.US, "%.1f", this)
