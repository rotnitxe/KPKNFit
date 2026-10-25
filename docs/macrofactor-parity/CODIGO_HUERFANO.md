# Código sin consumidor en la ola · 24-oct-2026

Revisión de solo lectura de las áreas tocadas. No se elimina ni conecta código. Las líneas son del checkout indicado; se actualizarán al consolidar la integración.

| Nombre | Ruta y línea | Qué hace | Consumidores comprobados | Recomendación pendiente de decisión |
|---|---|---|---|---|
| `diffSessionPlan` | `mfp: android-native/app/src/main/java/com/example/kpkn/screens/workout/SessionPlanAspectDiff.kt:37` | Compara la sesión base con la sesión actual y produce aspectos reversibles | En producción solo lo llama `WorkoutViewModel.revertPlanAspect`; también tiene pruebas `SessionPlanAspectDiffTest` | Conservar hasta decidir si se añade revisión de cambios en UI o se retira esta ruta |
| `revertPlanAspect` | `mfp: android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt:4406` | Revierte un aspecto y actualiza sugerencias/estado persistido | Sin llamadores en `app/src/main` Kotlin; no existe una acción visible conectada por nombre | Decidir un flujo y alcance de reversión antes de conectar; no se considera funcionalidad entregada de esta ola |
| `applySessionPlanAspectRevert` | `mfp: android-native/app/src/main/java/com/example/kpkn/screens/workout/SessionPlanAspectDiff.kt:162` | Materializa la reversión de un aspecto | Único consumidor de producción: la ruta anterior, sin entrada de UI | Evaluar junto al flujo anterior; conservar sus pruebas mientras exista |
| `readinessByMuscle` | `mfp: android-native/app/src/main/java/com/example/kpkn/domain/training/ProgramAnalyticsEngine.kt:60,363` | Deriva una preparación por músculo del estrés acumulado, sin reloj de recuperación | La propiedad se declara y construye; no se encontró lectura de producción. `ProgramDetailScreen.kt:952` construye el informe y `VolumeView.kt:299` sí consume otras secciones | Evaluar una ventana temporal y validación antes de darle un uso visible. No retirar el informe completo, porque tiene otros lectores |
| `recoveryDebtByMuscle` | `mfp: android-native/app/src/main/java/com/example/kpkn/domain/training/ProgramAnalyticsEngine.kt:59,361` | Deriva deuda por músculo de la misma agregación | Sin lecturas de esta propiedad fuera de declaración/construcción en producción | Evaluar junto a la preparación anterior; no modificar AUGE ni sus consumidores activos |
| `MacroGoals.fiberGoal` | `mfpn: android-native/app/src/main/java/com/example/kpkn/domain/nutrition/MacroCalculator.kt:483,567` | Transporta el objetivo heredado de fibra de Ajustes | Declaración y asignación; sin lectura de la propiedad en producción | Conservar compatibilidad; decidir si el usuario necesita un objetivo propio separado de la referencia DRI |
| `MacroGoals.sugarLimit` | Misma ruta: `484,568` | Transporta límite heredado de azúcar | Declaración y asignación; sin lector de producción | Conservar. No crear una referencia DRI de azúcar fuera del CSV aprobado |
| `MacroGoals.sodiumLimitMg` | Misma ruta: `485,569` | Transporta límite heredado de sodio | Declaración y asignación; sin lector de producción | Conservar. La hoja P2-Nb muestra CDRR/AI identificados, sin confundir un default heredado con una elección expresa |
| `MacroGoals.potassiumGoalMg` | Misma ruta: `486,570` | Transporta objetivo heredado de potasio | Declaración y asignación; sin lector de producción | Conservar; la nueva referencia depende de edad y perfil, no del default heredado |
| `MacroGoals.hydrationGoalMl` | Misma ruta: `487,571` | Transporta objetivo heredado de hidratación | Declaración y asignación; sin lector de producción | Conservar; agua queda fuera del CSV DRI de esta ola |

## Método y límites

Se buscaron nombres completos y accesos a propiedades en `android-native/app/src/main/**/*.kt`, y se separaron las referencias de pruebas. El informe analítico sí se consume: el hallazgo se limita a las dos propiedades citadas. Tampoco se equipara esta preparación sin reloj con la preparación activa que consume la sesión en vivo.

P2-Nb documenta en `P2-Nb_FUENTES.md` por qué las referencias generales no consumen los defaults de `MacroGoals`. Los objetos de muestras, hashes del CSV y datos de los harness tienen consumidores deliberados de depuración/pruebas; su presencia no acredita una entrada de producción y tampoco implica que deban eliminarse.

No se hizo una búsqueda exhaustiva de funciones huérfanas en todo el repositorio. No se cambian rutas de entrenamiento, preferencias, importador, catálogo ni datos del usuario con esta documentación.
