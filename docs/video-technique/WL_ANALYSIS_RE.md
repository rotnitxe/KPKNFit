# WLAnalysis 2.13.9 · evidencia estática y aplicación a KPKN

Fecha: 17 de octubre de 2026. Se inspeccionó el XAPK suministrado por el usuario, sus DEX y la biblioteca nativa; no se ejecutó WLAnalysis ni se usó el teléfono. Este documento resume hallazgos para diseñar una implementación propia. No acredita precisión, rendimiento ni autenticidad del editor, y no incorpora código, bibliotecas ni modelos del artefacto a KPKN.

## Procedencia y límites de la evidencia

La evidencia detallada se conserva como artefactos locales en la carpeta **`C:/Users/valen/Documents/KPKNFit/artifacts/wl-analysis-re-20261017/`**. Su informe es `INFORME.md`; contiene inventario, hashes y referencias a `rea-evidence.json`, `native-call-links.json`, `native-relocations.txt` y `native-jni-thumb.txt`. No se presenta un enlace relativo a archivos que no están en este directorio.

Artefacto: `WL+Analysis+-+bar+path+tracker_2.13.9_APKPure.xapk`, 102.246.282 B, SHA-256 `879bdf580f106965868547abc127488a1cde8752e29f1bd3ab28cffa924d4f1b`. Identidad declarada: `com.karolsmolak.wlanalysis`, versión 2.13.9, código 203. No se verificó la firma del editor.

La inspección usó REA/JADX y símbolos, relocaciones y desensamblado Thumb de LLVM. Ghidra en Windows no admitió el ELF ARM del paquete; no se obtuvo pseudocódigo nativo completo. El primer `native-jni.txt` usó modo ARM incorrecto y se descarta: la evidencia pertinente es `native-jni-thumb.txt`. Los nombres recuperados no acreditan por sí mismos el uso de todas las clases en ejecución.

## Detector, seguimiento y modos

El ensamblado Java recuperado separa tres etapas: localizar una región, seguirla por fotograma y convertir su trayectoria en métricas.

- `Yolo11Finder` inicializa un helper que crea `YoloDetector` y carga explícitamente `best.tflite` y `metadata.yaml`. El modelo ocupa 10.658.479 B. La metadata declara detección 640×640 y clase `bar`; no se inspeccionaron capas ni tensores y el nombre no demuestra la arquitectura interna.
- `SwitchingBarFinder` reúne el finder de disco y el de ArUco. `SwitchingBarTracker` selecciona disco o marcador al inicializar. **No se demostró un fallback automático entre modos.**
- El tracker de disco activo en el ensamblado recuperado es `BarTracker`. Su JNI `init0/update0` llama a PLT `0x97b90/0x97ba0`; las entradas GOT `0x9e924/0x9e928` resuelven a `KCFTracker::init/update`. Esta relación está confirmada por llamadas y relocaciones, no solo por símbolos presentes.
- La biblioteca incluye `TemplateTracker` y `Kalman`, pero eso no prueba que todas sus implementaciones participen en el flujo normal. No se recuperaron referencias entrantes al constructor de `TemplateBarTracker`; no se lo considera el tracker activo.
- El procesador inicializa una región y después llama al tracker por fotograma. No se observó una inferencia neuronal en cada actualización. La trayectoria filtrada se guarda en ObjectBox.

## Escala, señal y contador de barra

La cadena inspeccionada usa diámetro de disco; si el guardado vale cero, toma 0,45 m. El desplazamiento se convierte con dimensiones de imagen y radio vertical del disco, permitiendo componentes horizontal, vertical o norma. No se confirmó corrección de elipse o perspectiva en ese camino. El diámetro por defecto no verifica la escala física real.

El posprocesado usa el paso temporal mediano para cadencia y derivación, con combinaciones simétricas de vecinos. El cuerpo descompilado del suavizado contiene una reconstrucción de bucle dudosa: no se copia ni se interpreta como un fallo real de ejecución.

El contador inspeccionado segmenta a partir de la norma de velocidades de la barra y tramos de velocidad baja. Las constantes recuperadas están documentadas en el informe local como observaciones de ese artefacto; **no se adoptan como parámetros de KPKN**. No se probó su validez con repeticiones continuas, parciales, fallidas o cambios de encuadre. La presencia de fuerza/potencia tampoco demuestra exactitud física.

## Prioridades de una implementación propia

KPKN ya tiene Hough, refino de círculo/elipse, NCC, Kalman, seguimiento hacia atrás y filtrado de fase cero. No se propone añadirlos de nuevo ni sustituirlos por KCF sin comparación.

| Prioridad | Punto de KPKN | Candidato y prueba previa |
|---|---|---|
| P1 · adquisición | `data/media/technique/TechniqueAnalysisPipeline.kt`, `PlateSession.tryAcquire` | Separar la propuesta de disco de muñecas visibles, quietud y escala corporal. La selección manual ya existe en PlateCalibrationDialog. Una adquisición automática independiente, geométrica o aprendida con datos/pesos propios, debe probar identidad y coste con manos ocultas, inicio en movimiento, rack, varios discos y negativos. Detectar un objeto no habilita m/s. |
| P1 · identidad | `domain/biomechanics/vision/PlateTracker.kt`, `accept/track`; `Ncc.clear` | Estudiar la renovación de apariencia tras readquisición. La plantilla antigua es una hipótesis de riesgo de código, no una causa de fallo real demostrada. Probar oclusión, cambio de apariencia, distractores y readquisición de baja calidad; conservar movimiento rápido, elipse y seguimiento inverso. |
| P2 · discontinuidad | `domain/biomechanics/vbt/CalibratedVelocity.kt`, `PlateVerticalSeries.of` | Detectar saltos de identidad/centro/radio entre medidas aceptadas antes de publicar velocidades, conservando la traza original. Probar saltos artificiales frente a aceleraciones reales y asegurar que los huecos no se conviertan en interpolaciones inventadas. |
| P3 · marcador opcional | Modo nuevo, fuera del cierre actual | ArUco requeriría accesorio, tamaño conocido, calibración y pruebas de ángulo/oclusión. No se asume cambio automático de modo. |

Las rutas anteriores parten de `android-native/app/src/main/java/com/example/kpkn/`. Estas son propuestas para una ronda posterior; no se han implementado ni cambian el motor congelado.

## Relación con la reserva fallida

Los gates finales de conteo y tiempos **FALLARON**: SQUAT 2/4 dentro de rango, ROW 3/5 y ninguna salida ALTA. Se conserva esa única evaluación; no se retoca ni se repite para obtener otro veredicto.

- r02 tiene barra en la etiqueta, pero disco PENDING, cero intentos y cero muestras. Es un hueco de adquisición; no demuestra pérdida de un tracker inicializado. Sus 287/287 frames de pose contienen una persona, sin garantizar precisión por articulación. La condición que impidió intentar adquirir y la señal de conteo seleccionada siguen desconocidas.
- ymove_006 no usa disco: siete ciclos clasificados frente a cuatro repeticiones anotadas. Un tracker de barra no corrige directamente esa fragmentación de pose.
- ymove_007/011 tampoco usan disco. El replay final convierte una completa en parcial y las últimas llegadas de extracción exceden las ventanas anotadas. Es compatible con un efecto de recorte; no demuestra falta de pose ni justifica cambiar el umbral de completa.
- Hay controles correctos a unos 15 fps y sin disco adquirido. LOW_FPS no explica por sí solo todos los fallos.

La inspección de WLAnalysis no promete mayor exactitud. Las mejoras se desarrollarían con datos de ajuste y sintéticos, y necesitarían una evaluación independiente nueva. Rendimiento, comportamiento real, cámara/rotación, identidad ante distractores, arquitectura interna del modelo y exactitud física de WLAnalysis siguen sin medirse. El teléfono de KPKN permanece pospuesto; sus pruebas no se sustituyen con ingeniería inversa estática.
