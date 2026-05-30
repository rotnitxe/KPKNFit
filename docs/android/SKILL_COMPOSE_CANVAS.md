# Skill: Jetpack Compose Canvas & Animaciones (Canvas Specialist)

Esta guía establece el estándar de desarrollo para crear, modificar y animar elementos gráficos dinámicos personalizados mediante la API `Canvas` de Jetpack Compose en KPKN Fit.

---

## 🎨 1. Principios de Renderizado Dinámico
En KPKN Fit, muchos elementos visuales (como los anillos AUGE o los iconos de la barra de navegación) se dibujan de forma matemática interactiva utilizando el canvas nativo de Jetpack Compose. Esto garantiza escalabilidad perfecta y rendimiento a 60/120 FPS sin cargar recursos de imagen (PNG/SVG).

### Reglas Críticas:
1. **Separación de Lógica y Dibujo**: El canvas no calcula la fatiga ni calcula porcentajes. Recibe estados ya normalizados (valores de `0.0f` a `1.0f`) a través de las propiedades de interfaz o de observables del ViewModel.
2. **Caché de Objetos de Dibujo**: Evitar instanciar objetos como `Path`, `Paint` o estructuras complejas dentro del scope de `onDraw` o del bloque del Canvas, ya que esto se ejecuta en cada frame y generará sobrecarga en el Garbage Collector (GC). Utilizar `remember` fuera del Canvas.
3. **Respeto a Material You (M3)**: Los colores de fondo y bordes deben derivarse dinámicamente de `MaterialTheme.colorScheme` para asegurar un comportamiento perfecto tanto en temas Light como Dark.

---

## 📐 2. Estructura de Dibujo de Arcos e Indicadores (Caso AUGE Rings)
Para dibujar círculos concéntricos o barras circulares con degradados y bordes redondeados, se utiliza la API `drawArc`.

### Ejemplo Práctico de Canvas Concéntrico:
```kotlin
@Composable
fun AugeRing(
    progress: Float, // 0.0f a 1.0f
    color: Color,
    strokeWidth: Float = 24f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val size = this.size
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)

        // 1. Dibujar el fondo del anillo (pista translúcida)
        drawArc(
            color = color.copy(alpha = 0.15f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 2. Dibujar el anillo de progreso activo
        drawArc(
            color = color,
            startAngle = -90f, // Empezar en la parte superior (12 en punto)
            sweepAngle = progress * 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
```

---

## 👆 3. Interacciones Táctiles y Gestos (Caso Calibración RINGS)
Para permitir al usuario calibrar y modificar valores arrastrando elementos en pantalla (como en `CalibrationOverlay` sobre los anillos AUGE), se implementan gestos combinando modificadores de `pointerInput` o `draggable`.

### Detección de Drag Vertical para Calibración:
```kotlin
@Composable
fun CalibrationOverlay(
    initialValue: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }
    val maxDragRange = 400f // Rango en píxeles

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { /* Inicializar */ },
                    onDragEnd = { onDismiss() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Limitar el arrastre
                        offsetY = (offsetY + dragAmount).coerceIn(-maxDragRange, maxDragRange)
                        
                        // Normalizar el valor a un rango de 0.0f a 1.0f
                        val normalized = 1.0f - ((offsetY + maxDragRange) / (maxDragRange * 2f))
                        onValueChange(normalized.coerceIn(0.0f, 1.0f))
                    }
                )
            }
    ) {
        // Renderizar anillos dinámicamente según el gesto Y
    }
}
```

---

## ⚡ 4. Animaciones de Transición
Para que los cambios de progreso o las aperturas de overlays se sientan premium, se debe utilizar `animateFloatAsState` o `Transition` de Compose.

### Ejemplo de Progreso Animado en Carga de Anillos:
```kotlin
@Composable
fun AnimatedAugeRings(
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float
) {
    // Animación suave de entrada/cambio del 0% al valor real
    val animatedMuscular by animateFloatAsState(
        targetValue = muscularProgress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "muscularArc"
    )
    
    val animatedSnc by animateFloatAsState(
        targetValue = sncProgress,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "sncArc"
    )

    // Canvas dibuja usando las variables animadas: animatedMuscular, animatedSnc...
}
```
