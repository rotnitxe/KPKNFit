# Resumen ejecutivo — auditoría de músculos, volumen y AUGE

Fecha de corte: 2026-07-27.

## Cobertura

- Catálogo auditado: **1.020/1.020 ejercicios**.
- El alcance esperado indicaba 1.030: existe un desfase de **10 ejercicios** que debe resolverse contra la fuente de verdad.
- El archivo original no fue modificado. SHA-256 antes y después:
  `2655b89ad00d7a78618d887cacace2dbb65c88c0eef500053487eb2e03646822`.
- Muestra reproducible: **102 ejercicios**, semilla `20260727`.

## Totales

| Severidad | Flags | Ejercicios afectados |
|---|---:|---:|
| 🔴 Error biomecánico | 52 | 49 |
| 🟠 Inconsistencia de datos | 719 | 506 |
| 🟡 Bug de código/dato muerto | 67 | 43 |
| 🔵 Sospechoso a revisar | 1.072 | 642 |

Los totales por severidad no son mutuamente excluyentes: un ejercicio puede aparecer en varias categorías.

## Hallazgos dominantes

1. **Taxonomía muscular fragmentada.** `ExerciseAnatomy.kt` declara 17 músculos padre, mientras el JSON utiliza 30 strings musculares. Hay 367 usos de strings no canónicos.
2. **Emphasis sin contrato.** Se detectaron 625 valores libres o incompatibles con las cabezas declaradas.
3. **Activation fuera del rango del rol.** Hay 262 asignaciones que incumplen primary 0,8–1,0, secondary 0,3–0,7 o stabilizer ≤0,4.
4. **Pérdida silenciosa.** `subMuscleGroup` existe en el JSON pero no en `ExerciseMuscleInfo`.
5. **Normalización divergente.** Dos rutas activas aún usan `normalizeMuscleGroup` deprecated y pueden separar cabezas de deltoides.
6. **AUGE.** Se detectaron 426 outliers IQR, 21 bisagras con barra y `ssc<1`, y 37 ejercicios donde los estabilizadores pueden quedar anulados por el gate `ssc<1,5`.
7. **Escalas de volumen/fatiga.** La recuperación combina el multiplicador de fatiga con `volumeContribution` o su fallback de volumen; requiere contrato explícito para evitar doble ponderación accidental.
8. **Neutralizer.** El código sí soporta el rol, pero el catálogo tiene cero registros con `role=neutralizer`; varias rutas además lo presentan o convierten como stabilizer.

## Prioridad recomendada

1. Resolver los 49 ejercicios con flags rojos y confirmar el desfase 1.020/1.030.
2. Definir alias→músculo padre y vocabulario cerrado de emphasis/modifiers.
3. Preservar o retirar formalmente `subMuscleGroup`.
4. Reemplazar normalización deprecated y centralizar lookup músculo→baterías articulares.
5. Recalibrar AUGE después de estabilizar la anatomía.
6. Convertir estas reglas en validaciones de CI y exigir cero rojos/campos desconocidos.

El detalle, ejemplos antes/después y límites metodológicos están en `AUDIT_MUSCULOS_VOLUMEN.md`; el CSV contiene exactamente una fila por ejercicio y columnas filtrables por severidad, regla, variante y muestra.
