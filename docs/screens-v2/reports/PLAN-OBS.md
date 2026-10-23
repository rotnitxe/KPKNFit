# PLAN-OBS · notas del relator para las reglas de progresión

## Cambios

- Juggernaut: PlanObserver usa el mismo resultado de AmrapProgramPreview. La nota informa el TM que deja la ola (si hay TM real) o el incremento de la regla, y conserva el cierre de ola; el AMRAP corto no anuncia subida.
- GZCLP: T1 y T2 muestran etapa y volumen base. T2 no depende de que la última serie sea AMRAP. El cambio de etapa queda condicional hasta recibir un TierCheck agregado de todas las series; la nota distingue aprobar, avanzar con la misma carga y reiniciar.
- APRE: la serie 3 conserva el cálculo de carga para la serie 4; la serie 4 expresa el XRM físico de la semana siguiente. OFF no lo aplica, PROPONE requiere confirmación y AUTO queda para la semana siguiente. No se deriva TM ni 1RM del XRM. La identidad reutiliza `resolveLiveApreBlock`: el marcador válido prevalece; el respaldo exige `sourceProtocolId=apre-mann`, regla APRE real y cuatro series válidas con mínimos y propósitos de la misma variante. Un AMRAP ordinario no es APRE.
- CycleIncrement: informa el incremento upper/lower de AuthoredProgressionEngine y si se aplica al cerrar ciclo o bloque. Un AMRAP corto congela el TM al cierre. Sin una racha de congelación verificada no se anuncia reinicio.
- Las reglas anteriores y la recomendación genérica para AMRAP sin una de estas reglas siguen usando sus rutas existentes.

El contexto vivo transporta la receta completa y el perfil real opcional. Para el hit, toma setJustLoggedKey, lo resuelve contra CompletedSet y ExerciseSet, y por eso no confunde el cursor avanzado con la serie observada. Incluye peso y reps registrados, RIR/fallo/reps parciales observados, porcentaje y relación de carga respecto de la prescrita, mínimo de reps, propósito, rol y volumen. No completa RIR/RPE con objetivos prescritos.

## Pruebas añadidas

- AmrapProgramPreviewTest: casos numéricos de Juggernaut, APRE S3 150×12 → 155 kg, APRE S4 155×17 → XRM 162,5 kg, modos OFF/PROPONE/AUTO sin conversión a TM, ausencia de perfil, GZCLP sin cierre agregado y racha congelada desconocida. El helper de pruebas deja el streak desconocido por defecto.
- RelatorPlanAwareTest: líneas iguales al preview para Juggernaut y APRE, GZCLP T1/T2 con notas de hasta 90 caracteres, CycleIncrement upper +2,5 kg/lower +5 kg y alcance de bloque, AMRAP genérico y ausencia de reinicio inventado.
- RelatorPlanProgressionContextTest: prueba JVM en screens/workout del set recién registrado tras avanzar el cursor, campos reales del hit, detección del marcador y respaldo legado APRE, y rechazo de AMRAP normal o explícitamente desactivado. No es una prueba instrumentada de Android.

## Validación y límites

La base preparatoria de u22 incorpora APRE/CAL5b desde `u20@992a9bbee`. El conflicto add/add de `LiveApreBlock.kt` se resolvió con la implementación de u20: un marcador presente e inválido no cae al respaldo legado; este exige `sourceProtocolId=apre-mann`, regla APRE real y cuatro series válidas, y se conservan las comprobaciones de variante, mínimo y configuración. Los consumidores del relator pasan el protocolo y la regla reales. La nota del set observado sigue usando `setJustLoggedKey` → `parseCompletedSetKey` → `CompletedSet`/`ExerciseSet`; no usa el cursor ya avanzado.

La base preparatoria sigue pendiente de validación integrada. `git diff --check` se revisa en este cambio; no se ejecutaron Gradle, JVM, adb ni pruebas, según la coordinación centralizada de root. No se modificó Room ni su esquema.

El flujo vivo aún no aporta el cierre agregado GZCLP ni una racha 5/3/1 persistida y verificada. En esos casos las notas quedan condicionales o informativas. La base ya contiene el contrato tipado `ADJUST_XRM` y la aplicación XRM de APRE; su convivencia con PLAN-OBS sigue sin validar. El relator conserva el valor como XRM físico y no lo presenta como TM.
