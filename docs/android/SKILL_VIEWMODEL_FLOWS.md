# Skill: Flujos Reactivos y Gestión de Estado (ViewModels & Flow Specialist)

Esta guía documenta el estándar para construir máquinas de estados interactivas y asíncronas en KPKN Fit usando la arquitectura nativa **MVVM (Model-View-ViewModel)** de Android, **Kotlin Coroutines** y flujos reactivos basados en **StateFlow** y **SharedFlow**.

---

## ⚡ 1. Arquitectura de Estado Unificado
Toda pantalla del sistema debe gobernarse por un único estado representativo expuesto por su respectivo `ViewModel`. Esto asegura consistencia perfecta (Unidirectional Data Flow) y evita discrepancias de UI.

### Reglas Críticas:
1. **Estado Inmutable de UI**: Exponer un `StateFlow` inmutable para lectura pública en Compose, y un `MutableStateFlow` privado dentro del ViewModel para escritura.
2. **Eventos de Disparo Único**: Para mensajes que deben ejecutarse una sola vez (ej. mostrar un toast, disparar vibración háptica o navegar usando el NavigationBus), utilizar `SharedFlow` o `Channel`, nunca variables de estado normales que puedan re-dispararse al rotar o redibujar la pantalla.
3. **Dispatchers Apropiados**:
   - `Dispatchers.Main`: Para cambios inmediatos en el hilo de UI.
   - `Dispatchers.Default`: Para cálculos pesados (AUGE Engine, volúmenes efectivos).
   - `Dispatchers.IO`: Para llamadas de base de datos Room, parsing de logs y red.

---

## 🏗️ 2. Patrón de Implementación del ViewModel (Estilo Workout)
El siguiente código ejemplifica el diseño reactivo que gobierna entrenamientos interactivos en vivo en KPKN Fit (ej. `WorkoutViewModel`):

```kotlin
// 1. Representación del estado completo de la UI
data class WorkoutUiState(
    val isLoading: Boolean = false,
    val activeSession: Session? = null,
    val currentExerciseIndex: Int = 0,
    val restRemainingSeconds: Int = 0,
    val muscularFatigueProgress: Float = 0.0f
)

// 2. Definición de acciones/eventos de UI admitidos
sealed interface WorkoutUiEvent {
    object CompleteActiveSet : WorkoutUiEvent
    data class AdjustRpe(val newRpe: Float) : WorkoutUiEvent
    data class SuggestWeight(val exerciseId: String) : WorkoutUiEvent
}

class WorkoutViewModel(
    private val workoutRepository: ProgramRepository,
    private val augeEngine: AugeAdaptiveEngine // Inyección de lógica de negocio
) : ViewModel() {

    // 3. Flujo interno mutable y público inmutable
    private val _uiState = MutableStateFlow(WorkoutUiState(isLoading = true))
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    // 4. Canal de eventos de un solo disparo (háptica, alarmas)
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    init {
        loadSessionData()
    }

    private fun loadSessionData() {
        viewModelScope.launch(Dispatchers.IO) {
            workoutRepository.activePrograms.collect { programs ->
                // Procesar cálculo matemático pesado en background thread
                withContext(Dispatchers.Default) {
                    val activeSession = programs.flatMap { it.weeks }
                        .flatMap { it.days }
                        .firstOrNull { !it.isCompleted }?.session
                    
                    val fatigue = augeEngine.calculateMuscularFatigue()

                    _uiState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            activeSession = activeSession,
                            muscularFatigueProgress = fatigue
                        )
                    }
                }
            }
        }
    }

    // 5. Método público único para recibir intenciones de la UI
    fun onEvent(event: WorkoutUiEvent) {
        viewModelScope.launch {
            when (event) {
                is WorkoutUiEvent.CompleteActiveSet -> {
                    // Acción lógica
                    _toastEvent.emit("¡Serie Guardada con Éxito!")
                }
                is WorkoutUiEvent.AdjustRpe -> { /* ... */ }
                is WorkoutUiEvent.SuggestWeight -> { /* ... */ }
            }
        }
    }
}
```

---

## 📱 3. Consumo de Flujos Seguro en Jetpack Compose
En la interfaz de Compose, consumir estados reactivos de forma que respeten el ciclo de vida de la pantalla (evitando consumos de recursos cuando la app esté minimizada).

```kotlin
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel = viewModel()
) {
    // 1. Recolección segura del estado según ciclo de vida de Android
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 2. Recolección de eventos de disparo único (Side Effects)
    LaunchedEffect(key1 = true) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 3. Renderizado de interfaz puramente declarativo basado en el estado
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(text = "Sesión Activa: ${state.activeSession?.name}")
            Text(text = "Fatiga Muscular: ${state.muscularFatigueProgress * 100}%")
            
            Button(onClick = { viewModel.onEvent(WorkoutUiEvent.CompleteActiveSet) }) {
                Text("Completar Serie")
            }
        }
    }
}
```

---

## 🔒 4. Prevención de Fugas y Cancelaciones
- El scope **`viewModelScope`** cancela automáticamente todas las tareas asíncronas activas (peticiones de red, suscripciones a Room, flujos continuos) cuando la pantalla se cierra y el ViewModel es destruido. **Nunca** uses `GlobalScope` o scopes personalizados persistentes que puedan fugar memoria.
- Utilizar `withContext(Dispatchers.Default)` al realizar operaciones que iteren colecciones de datos masivas (ej. calibrando bases de datos de alimentos con más de 10,000 entradas) para no congelar la tasa de frames de la interfaz de usuario.
