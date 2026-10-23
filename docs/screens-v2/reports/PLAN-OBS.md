# PLAN-OBS · notas del relator para las reglas de progresión

## Cambios

- Juggernaut: PlanObserver usa el mismo resultado de AmrapProgramPreview. La nota informa el TM que deja la ola (si hay TM real) o el incremento de la regla, y conserva el cierre de ola; el AMRAP corto no anuncia subida.
- GZCLP: T1 y T2 muestran etapa y volumen base. T2 no depende de que la última serie sea AMRAP. El cambio de etapa queda condicional hasta recibir un TierCheck agregado de todas las series; la nota distingue aprobar, avanzar con la misma carga y reiniciar.
- APRE: la serie 3 conserva el cálculo de carga para la serie 4; la serie 4 expresa el XRM físico de la semana siguiente. OFF no lo aplica, PROPONE requiere confirmación y AUTO queda para la semana siguiente. No se deriva TM ni 1RM del XRM. Si existe una captura S4 vigente, se reutiliza su incremento material y su XRM; sólo sin captura se usa la regla de receta. Para S3, el relator consume la decisión contextual vigente y reutiliza su carga de S4; 155×17 con salto material de 5 kg produce 160 kg, mientras que el fallback de receta a 2,5 kg produce 162,5 kg. Las capturas stale se descartan; la nota distingue S4 prevista de una carga realmente ajustada. La identidad reutiliza `resolveLiveApreBlock`: el marcador válido prevalece; el respaldo exige `sourceProtocolId=apre-mann`, regla APRE real y cuatro series válidas con mínimos y propósitos de la misma variante. Un AMRAP ordinario no es APRE.
- CycleIncrement: informa el incremento upper/lower de AuthoredProgressionEngine y si se aplica al cerrar ciclo o bloque. Un AMRAP corto congela el TM al cierre. Sin una racha de congelación verificada no se anuncia reinicio.
- Las reglas anteriores y la recomendación genérica para AMRAP sin una de estas reglas siguen usando sus rutas existentes.

El contexto vivo transporta la receta completa y el perfil real opcional. Para el hit, toma setJustLoggedKey, lo resuelve contra CompletedSet y ExerciseSet, y por eso no confunde el cursor avanzado con la serie observada. Incluye peso y reps registrados, RIR/fallo/reps parciales observados, porcentaje y relación de carga respecto de la prescrita, mínimo de reps, propósito, rol y volumen. No completa RIR/RPE con objetivos prescritos.

## Pruebas añadidas

- AmrapProgramPreviewTest (domain JVM): casos numéricos de Juggernaut, APRE S3 150×12 → 155 kg, S4 155×17 con fallback de receta → XRM 162,5 kg y captura material de 5 kg → 160 kg, modos OFF/PROPONE/AUTO sin conversión a TM, ausencia de perfil, GZCLP sin cierre agregado y racha congelada desconocida.
- RelatorPlanAwareTest: líneas iguales al preview para Juggernaut y APRE, GZCLP T1/T2 con notas de hasta 90 caracteres, CycleIncrement upper +2,5 kg/lower +5 kg y alcance de bloque, AMRAP genérico y ausencia de reinicio inventado.
- ProgramAutoregulationApreXrmChannelTest (domain JVM): el colector conserva el XRM del snapshot material válido (5 kg → 160 kg), demuestra el fallback (2,5 kg → 162,5 kg) y rechaza una captura con reps stale. RelatorPlanProgressionContextTest (screens/workout JVM): set recién registrado tras avanzar el cursor, captura S4 exacta, decisión S3 contextual (5 kg → 160 kg), texto previsto/aplicado y rechazo de AMRAP normal o desactivado. Ninguna es instrumentada de Android.

## Validación y límites

La base preparatoria de u22 incorpora APRE/CAL5b desde `u20@992a9bbee`. El conflicto add/add de `LiveApreBlock.kt` se resolvió con la implementación de u20: un marcador presente e inválido no cae al respaldo legado; este exige `sourceProtocolId=apre-mann`, regla APRE real y cuatro series válidas, y se conservan las comprobaciones de variante, mínimo y configuración. Los consumidores del relator pasan el protocolo y la regla reales. La nota del set observado sigue usando `setJustLoggedKey` → `parseCompletedSetKey` → `CompletedSet`/`ExerciseSet`; no usa el cursor ya avanzado.

El cambio material APRE quedó en `0def2640d`; u22 incorpora `6eb44c9d3` mediante el merge `d1f4a0655` desde `992a9bbee`. Los arreglos centrales de imports/tipo y las correcciones de formato/fixtures están en `ba6bf5bc9`, `98ce181f6` y `3ef97c39a`. La convivencia integrada sigue pendiente de validación. `git diff --check` pasó; este trabajo no ejecutó Gradle, JVM, adb ni pruebas. No se modificó Room ni su esquema.

El flujo vivo aún no aporta el cierre agregado GZCLP ni una racha 5/3/1 persistida y verificada. En esos casos las notas quedan condicionales o informativas. La base ya contiene el contrato tipado `ADJUST_XRM` y la aplicación XRM de APRE; su convivencia con PLAN-OBS sigue sin validar. El relator conserva el valor como XRM físico y no lo presenta como TM.


## Primera compilación central

`int-phase4-final-jvm-codex-20261023-1.log` se detuvo en compileKotlin: la expresión común de etapas T1/T2 infería un tipo sin propiedades number/label. Se despacha el texto por el enum real de cada etapa, conservando número, etiqueta y reglas. No se ejecutaron tests ni se archivaron XML antiguos como evidencia de esta compilación fallida. Pendiente repetir el barrido.

La segunda central (`int-phase4-final-jvm-codex-20261023-2.log`) compila producción y falla únicamente en compileTestKotlin por imports ausentes de assertEquals/AmrapEffectTiming en RelatorPlanAwareTest. Se añaden ambos sin cambiar aserciones ni expectativas. Tampoco ejecutó casos ni se reutilizaron XML.

La tercera central (`int-phase4-final-jvm-codex-20261023-3.log`) informó tres fallos durante su ejecución: la nota condicional de S4 no tenía `plannedMinimumReps` en la fixture; el fixture de captura S4 omitía las reps de S1/S2 que exige el bloque APRE10; y el titular XRM añadía un punto a una nota que ya estaba puntuado. Se completan las fixtures y se elimina la puntuación duplicada, sin cambiar las cifras ni las reglas. La corrida cerró exit1 con207 clases/2062 casos, tres fallos y cero errores/omitidas; XML nuevos archivados. No se declara verde. Se repetirán completas las dos clases afectadas: producción sólo cambia la puntuación del titular y los otros dos ajustes completan fixtures sin cambiar reglas ni expectativas.


La repetición pura4 cerró exit0 con2clases/27casos, sin fallos/errores/omitidas; XML archivados. Junto a la completa3 verifica2062 casos únicos de207clases; no se presenta como una única corrida completa verde. La Android int-phase4-tm-relator-android-codex-20261023-1 falló compileBaseDebugKotlin en2min20s por import LiveApreBlock ausente (errores derivados en sus propiedades). Se añade el import del dominio, sin cambiar lógica ni repetir el barrido puro. Cero tests Android ejecutados en este intento y sin XML viejos archivados. Pendiente repetición.


Android2 TM+Relator cerró: RelatorPlanProgressionContextTest4/4correctos. La corrida conjunta17casos tuvo4fallos del selector visual TM, ajenos a Relator; XML nuevos archivados en int-phase4-tm-relator-android-codex-20261023-2. El selector se corrige sin cambios de producto y se repetirá el conjunto junto a CAL7. La verificación dirigida de PLAN-OBS comprende2062casos puros únicos mediante completa3+focused4 y4casos Android, no QA de dispositivo ni una sola completa verde.
