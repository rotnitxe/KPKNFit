---
flags: [nutrition, room]
---

# Fiabilidad del registro natural de comidas

Fecha: 2026-09-12. Plan aprobado explícitamente por el usuario en Codex para implementación. El plan completo y la auditoría de partida están en la conversación; este documento fija el contrato de construcción.

Objetivo: inferir porciones habituales y preguntar únicamente ante dudas importantes, sin exigir gramos. Mantener todos y únicamente los alimentos descritos, sus atributos y cantidades; estimar con fundamento y conservar la incertidumbre relevante.

## Rutas

- Android: `domain/nutrition/`, `data/food/`, modelos/adaptadores y repositorios nutricionales, `screens/nutrition/` y sus componentes.
- Pruebas JVM e instrumentadas de estas áreas; contratos compartidos en `docs/contracts/` y evidencia de auditoría en `docs/audits/2026-09-nutrition-reliability/`.
- Sin migración Room, sin reconstruir los ports incompletos de iOS/backend, sin modificaciones de AUGE ni servicios de voz.

## Impacto

1. Reconocer decimales, fracciones y números escritos antes de segmentar. Conservar menciones, medidas, estado, atributos y exclusiones; conservar también fragmentos sin cantidad. Fusionar solo menciones compatibles después de resolver sus cantidades. Distinguir exclusiones de ingredientes de atributos como sin lactosa/sin azúcar/sin gluten.
2. Eliminar recuperación de comidas completas por coincidencias parciales de plantillas. Analizar primero la descripción; ninguna plantilla puede introducir alimentos omitidos/negados ni pisar cantidades explícitas.
3. Separar identidad, familia y procedencia. Mantener especie, composición, marca y preparación durante recuperación/ranking; no eliminar coincidencias compatibles globales antes de compararlas. No convertir cifras plausibles en certeza de identidad ni almacenamiento local en procedencia curada.
4. Separar base de nutrientes y porción consumida. Normalizar mediante adaptadores verificables, respetando fichas por ración y por 100 g; resolver duplicados de catálogo de forma determinista. Prioridad: cantidad/medida explícita > habitual confirmado compatible > porción contextual de hogar. Aplicar fracciones y conversiones una sola vez; contexto modifica cantidad, nunca densidad. No anunciar un estado distinto del usado por la ficha/cálculo.
5. Reutilizar FoodInterpretationV2 por mención ya resuelta, sin reparsear ni descartar alimentos posteriores. Conservar identidad, fuente, estado, cantidad, intervalos y dudas en el mismo resultado que gobierna UI, edición y guardado. Recalcular tamaños desde una base inmutable.
6. Porción habitual omitida no exige pregunta. Conflicto de identidad/atributo es material; alternativas de preparación se comparan sobre igual cantidad con umbrales existentes de 50 kcal o 5 g proteína/grasa. Una pregunta a la vez visible fuera de detalles plegados. No estoy seguro conserva estimación/intervalos y no entrena hábitos.
7. Aprender solo dimensiones confirmadas explícitamente tras guardar con éxito. Borrar conjuntamente asociaciones/hábitos relacionados; serializar actualizaciones. Propagar éxito/error de Room a UI, conservar borrador ante fallo e impedir omisiones silenciosas de alimentos activos.

## Pruebas

Baseline: 482 tests dirigidos, 481 correctos y un alias exacto `papa fritas` fallido; algunas expectativas antiguas codifican bugs (torta como pan, sin lactosa como ingrediente excluido).

- Regresiones: media/una/dos tazas de leche; rodajas/rebanadas frente a piezas; 0,5 kg y números compuestos; pollo frito y cocido separados; cantidades omitidas y orden; atributos sin azúcar/lactosa/gluten; pavo/pollo y pasta de maní/pasta; marca explícita; base por 100 g/ración; estado real/asumido; plantillas parciales/negaciones/cambios de gramos.
- Integración: recorrido parser -> resolución -> resultado/UI -> persistencia con identidad/atributos/cantidad/ficha/nutrientes finales, preguntas críticas y estimación aceptada; cambios de tamaño idempotentes; aprendizaje después de save, olvidar, descarte/reinicio, fallo escritura y reapertura.
- Corpus independiente del dataset del motor, con lenguaje natural Chile/LATAM, platos compuestos, toppings, typos, narración, desconocidos y ausencia de porciones. Publicar resultados separados de identidad, omisiones/adiciones, cantidades/nutrientes y aclaraciones, sin usar porcentaje de tests como precisión poblacional.
- Linux disponible: JAVA_HOME=/home/rotnitxe/.local/share/kpkn-android/jdk; ANDROID_HOME=/home/rotnitxe/.local/share/kpkn-android/sdk; GRADLE_USER_HOME=/tmp/kpkn-gradle. Usar ./gradlew --no-daemon --console=plain --warning-mode=summary --offline desde android-native, primero tests dirigidos, después suite nutricional/build y QA emulador disponible.
- Cierre: defectos reproducidos corregidos, invariantes verdes, sin aceptación silenciosa de otra identidad/cantidad mal interpretada/alimento perdido en corpus crítico.

## Riesgos

- Preservar WIP inicial: app/build.gradle.kts, NutritionWizardScreen.kt, NutritionWizardPhysiqueVisibilityTest.kt, plan/release Beta 14.8 y APK/verificación preexistentes.
- No recalcular registros históricos ni regenerar manualmente datasets grandes. Corpus lingüístico no certifica nutrientes. Fuentes nutricionales: USDA Foundation documentación por 100 g y FAO/INFOODS matching/recetas.
- Preservar protección regex contra backtracking/StackOverflowError y límites de entrada. Domain puro sin android.*, StateFlow readonly, IO para persistencia y concurrencia estructurada.
- No existe .opencode/pipeline.json ni herramienta pipeline expuesta: kpkn-gate.ts permite explícitamente flujo ad-hoc sin pipeline. Se ejecuta autorización de usuario con construcción, validación y revisión delegada, sin inventar estado/acciones de aprobación.
