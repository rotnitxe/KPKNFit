# Auditoría de músculos, volumen y AUGE

Fecha de corte: 2026-07-27. Fuente: `app/src/main/assets/exercise_database.json`.

## Resumen ejecutivo

- Cobertura real: **1020/1020 ejercicios**. El alcance solicitado decía 1030; faltan **10** respecto de ese contrato.
- Flags individuales: **1910**; ejercicios con al menos un flag: **812**.
- 🔴: **52 flags / 49 ejercicios**.
- 🟠: **719 flags / 506 ejercicios**.
- 🟡 de datos/runtime detectables por catálogo: **67 flags / 43 ejercicios**.
- 🔵: **1072 flags / 642 ejercicios**.
- Muestra reproducible: **102 ejercicios (seed 20260727)**, equivalente al 10% del catálogo; 22 sin flags y 80 con señales no rojas.
- Integridad: SHA-256 antes/después `2655b89ad00d7a78618d887cacace2dbb65c88c0eef500053487eb2e03646822` / `2655b89ad00d7a78618d887cacace2dbb65c88c0eef500053487eb2e03646822`; original **sin cambios**.

### Decisiones recomendadas

1. Congelar un contrato único de músculos padre. Hoy `ExerciseAnatomy.kt` contiene 17, mientras el catálogo usa 30 strings distintos.
2. Migrar `subMuscleGroup` al modelo o retirarlo del JSON; hoy se descarta al deserializar.
3. Separar `parentMuscle` de `emphasis` y aplicar una única normalización en volumen, fatiga, TTC y modifiers.
4. Tratar los flags rojos como cola experta, no como cambios automáticos: las reglas detectan contradicciones, pero no sustituyen la revisión del gesto concreto.

## Metodología y límites

- El script operó sobre `exercise_database.copy.json` en Temp; no escribió el JSON fuente.
- Los rangos de activation se aplicaron literalmente como fueron solicitados.
- Los outliers AUGE usan IQR 1,5× dentro de grupos tipo+implemento con al menos 8 observaciones.
- La revisión «experta» aquí es una revisión estática de metadatos y reglas. No se observó vídeo ni ejecución humana; por eso las señales dependientes de técnica quedan como sospechosas salvo contradicción directa.
- La muestra del 10% es reproducible y está marcada en la columna `sample10Percent` del CSV.

## Hallazgo de contrato: 1.020, no 1.030

El array contiene 1020 objetos. No se crearon diez filas ficticias. Antes de corregir datos debe identificarse si faltan ejercicios, si el número esperado quedó obsoleto o si existe otra fuente aún no fusionada.

## Hallazgos por regla

### 🔵 sospechoso a revisar — `1.5_FREE_EMPHASIS` (625)

- `tren_superior_press_inclinado_maquina_convergente` — Press Inclinado en Máquina Convergente: Emphasis libre "clavicular" en Pectorales. Corrección: Usar solo el vocabulario de Pectorales: inferior, superior.
- `tren_superior_press_inclinado_smith` — Press Inclinado en Smith: Emphasis libre "clavicular" en Pectorales. Corrección: Usar solo el vocabulario de Pectorales: inferior, superior.
- `nuevo_press_pecho_pie_polea_doble` — Press de Pecho de Pie en Polea Doble: Emphasis libre "medio" en Pectorales. Corrección: Usar solo el vocabulario de Pectorales: inferior, superior.
- `nuevo_flexiones_deslizadores` — Flexiones con Deslizadores (Slide Fly Push-ups): Emphasis libre "medio" en Pectorales. Corrección: Usar solo el vocabulario de Pectorales: inferior, superior.
- `triceps_press_maquina` — Press de Tríceps en Máquina: Emphasis libre "Pectorales" en Tríceps. Corrección: Usar solo el vocabulario de Tríceps: larga, lateral, medial.

### 🟠 inconsistencia de datos — `1.4_NONCANONICAL_MUSCLE` (367)

- `ultimo_press_banca_suelo_puente` — Press de Banca en Suelo (Floor Press): "Glúteo Mayor" no pertenece a los 17 padres de ExerciseAnatomy.kt. Corrección: Antes: {"muscle":"Glúteo Mayor"}; después: {"muscle":"Glúteos","emphasis":"gluteo mayor"}.
- `back_hiperextensiones_45` — Hiperextensiones a 45º: "Glúteo Mayor" no pertenece a los 17 padres de ExerciseAnatomy.kt. Corrección: Antes: {"muscle":"Glúteo Mayor"}; después: {"muscle":"Glúteos","emphasis":"gluteo mayor"}.
- `back_hiperextensiones_45_lastradas` — Hiperextensiones a 45º Lastradas: "Glúteo Mayor" no pertenece a los 17 padres de ExerciseAnatomy.kt. Corrección: Antes: {"muscle":"Glúteo Mayor"}; después: {"muscle":"Glúteos","emphasis":"gluteo mayor"}.
- `back_hiperextensiones_horizontales` — Hiperextensiones Horizontales: "Glúteo Mayor" no pertenece a los 17 padres de ExerciseAnatomy.kt. Corrección: Antes: {"muscle":"Glúteo Mayor"}; después: {"muscle":"Glúteos","emphasis":"gluteo mayor"}.
- `back_reverse_hyper` — Reverse Hyper: "Glúteo Mayor" no pertenece a los 17 padres de ExerciseAnatomy.kt. Corrección: Antes: {"muscle":"Glúteo Mayor"}; después: {"muscle":"Glúteos","emphasis":"gluteo mayor"}.

### 🟠 inconsistencia de datos — `1.1_ACTIVATION_ROLE` (262)

- `nuevo_press_pecho_pie_polea_doble` — Press de Pecho de Pie en Polea Doble: Core/stabilizer usa activation=0.5. Corrección: Ajustar a primary 0.8–1.0, secondary 0.3–0.7 o stabilizer ≤0.4.
- `nuevo_fondos_barra_recta` — Fondos de Pecho en Barra Recta: Core/stabilizer usa activation=0.5. Corrección: Ajustar a primary 0.8–1.0, secondary 0.3–0.7 o stabilizer ≤0.4.
- `nuevo_flexiones_deslizadores` — Flexiones con Deslizadores (Slide Fly Push-ups): Core/stabilizer usa activation=0.5. Corrección: Ajustar a primary 0.8–1.0, secondary 0.3–0.7 o stabilizer ≤0.4.
- `back_remo_mancuerna` — Remo con Mancuerna: Erectores Espinales/secondary usa activation=0.2. Corrección: Ajustar a primary 0.8–1.0, secondary 0.3–0.7 o stabilizer ≤0.4.
- `back_remo_kettlebell` — Remo con Kettlebell: Erectores Espinales/secondary usa activation=0.2. Corrección: Ajustar a primary 0.8–1.0, secondary 0.3–0.7 o stabilizer ≤0.4.

### 🔵 sospechoso a revisar — `3.1_IQR_TTC` (128)

- `tren_superior_press_unilateral_polea` — Press Unilateral en Polea: ttc=2.0 es outlier IQR para tipo=Accesorio, equipo=Polea (1.80–1.80). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_press_spoto_barra` — Press Spoto con Barra: ttc=2.2 es outlier IQR para tipo=Accesorio, equipo=Barra (2.00–2.00). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_press_banca_cadenas` — Press de Banca con Cadenas: ttc=2.2 es outlier IQR para tipo=Accesorio, equipo=Barra (2.00–2.00). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `ultimo_press_banca_agarre_inverso` — Press de Banca con Agarre Inverso: ttc=2.4 es outlier IQR para tipo=Accesorio, equipo=Barra (2.00–2.00). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `ultimo_hex_press_mancuernas` — Hex Press con Mancuernas: ttc=1.8 es outlier IQR para tipo=Aislamiento, equipo=Mancuerna (1.35–1.75). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.

### 🔵 sospechoso a revisar — `3.1_IQR_SSC` (115)

- `tren_superior_press_pecho_maquina_convergente` — Press de Pecho en Máquina Convergente: ssc=0.8 es outlier IQR para tipo=Accesorio, equipo=Máquina (0.25–0.65). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_floor_press_barra` — Floor Press con Barra: ssc=1.0 es outlier IQR para tipo=Accesorio, equipo=Barra (0.50–0.50). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_floor_press_mancuernas` — Floor Press con Mancuernas: ssc=0.9 es outlier IQR para tipo=Accesorio, equipo=Mancuerna (0.50–0.50). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_flexiones_pies_elevados` — Flexiones con Pies Elevados: ssc=0.8 es outlier IQR para tipo=Accesorio, equipo=Peso Corporal (0.50–0.50). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_press_unilateral_polea` — Press Unilateral en Polea: ssc=0.7 es outlier IQR para tipo=Accesorio, equipo=Polea (0.29–0.59). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.

### 🔵 sospechoso a revisar — `3.1_IQR_CNC` (104)

- `tren_superior_press_pecho_maquina_convergente` — Press de Pecho en Máquina Convergente: cnc=2.8 es outlier IQR para tipo=Accesorio, equipo=Máquina (1.70–2.50). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_floor_press_barra` — Floor Press con Barra: cnc=3.2 es outlier IQR para tipo=Accesorio, equipo=Barra (2.20–2.20). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_floor_press_mancuernas` — Floor Press con Mancuernas: cnc=2.8 es outlier IQR para tipo=Accesorio, equipo=Mancuerna (2.08–2.27). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_flexiones_pies_elevados` — Flexiones con Pies Elevados: cnc=2.8 es outlier IQR para tipo=Accesorio, equipo=Peso Corporal (1.98–2.57). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_press_unilateral_polea` — Press Unilateral en Polea: cnc=2.5 es outlier IQR para tipo=Accesorio, equipo=Polea (2.00–2.00). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.

### 🔵 sospechoso a revisar — `3.1_IQR_EFC` (79)

- `tren_superior_floor_press_barra` — Floor Press con Barra: efc=3.5 es outlier IQR para tipo=Accesorio, equipo=Barra (2.80–2.80). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_flexiones_pies_elevados` — Flexiones con Pies Elevados: efc=3.2 es outlier IQR para tipo=Accesorio, equipo=Peso Corporal (2.80–2.80). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_press_unilateral_polea` — Press Unilateral en Polea: efc=2.8 es outlier IQR para tipo=Accesorio, equipo=Polea (2.50–2.50). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_press_spoto_barra` — Press Spoto con Barra: efc=4.0 es outlier IQR para tipo=Accesorio, equipo=Barra (2.80–2.80). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.
- `tren_superior_press_banca_cadenas` — Press de Banca con Cadenas: efc=4.0 es outlier IQR para tipo=Accesorio, equipo=Barra (2.80–2.80). Corrección: Revisar contra variantes equivalentes; no corregir automáticamente.

### 🟠 inconsistencia de datos — `1.7_PARENT_SUM_GT_ONE` (67)

- `glutes_patada_gluteo_polea_diagonal` — Patada de Glúteos Diagonal en Polea: Suma por padre Glúteos=1.50; el runtime usa MAX y oculta el exceso. Corrección: Consolidar entradas por padre o documentar explícitamente que son alternativas, no sumables.
- `glutes_frog_pumps_peso_corporal` — Frog Pumps (Puente Rana) con Peso Corporal: Suma por padre Glúteos=1.40; el runtime usa MAX y oculta el exceso. Corrección: Consolidar entradas por padre o documentar explícitamente que son alternativas, no sumables.
- `glutes_frog_pumps_disco` — Frog Pumps (Puente Rana) con Disco: Suma por padre Glúteos=1.40; el runtime usa MAX y oculta el exceso. Corrección: Consolidar entradas por padre o documentar explícitamente que son alternativas, no sumables.
- `glutes_abduccion_cadera_sentado_maquina` — Abducción de Cadera Sentado en Máquina: Suma por padre Glúteos=1.60; el runtime usa MAX y oculta el exceso. Corrección: Consolidar entradas por padre o documentar explícitamente que son alternativas, no sumables.
- `glutes_abduccion_cadera_polea` — Abducción de Cadera de Pie en Polea: Suma por padre Glúteos=1.60; el runtime usa MAX y oculta el exceso. Corrección: Consolidar entradas por padre o documentar explícitamente que son alternativas, no sumables.

### 🟡 bug de código — `3.2_DEAD_STABILIZER_GATE` (37)

- `tren_superior_press_banca_plano_barra` — Press de Banca Plano con Barra: Tiene stabilizers pero ssc=1.2<1.5; el gate puede volverlos dato muerto. Corrección: Separar estabilidad local del gate axial o recalibrar ssc/gate.
- `tren_superior_press_banca_plano_mancuernas` — Press de Banca Plano con Mancuernas: Tiene stabilizers pero ssc=1.0<1.5; el gate puede volverlos dato muerto. Corrección: Separar estabilidad local del gate axial o recalibrar ssc/gate.
- `tren_superior_flexiones_clasicas` — Flexiones de Brazos Clásicas: Tiene stabilizers pero ssc=0.8<1.5; el gate puede volverlos dato muerto. Corrección: Separar estabilidad local del gate axial o recalibrar ssc/gate.
- `tren_superior_flexiones_lastradas` — Flexiones de Brazos Lastradas: Tiene stabilizers pero ssc=0.9<1.5; el gate puede volverlos dato muerto. Corrección: Separar estabilidad local del gate axial o recalibrar ssc/gate.
- `tren_superior_flexiones_pies_elevados` — Flexiones con Pies Elevados: Tiene stabilizers pero ssc=0.8<1.5; el gate puede volverlos dato muerto. Corrección: Separar estabilidad local del gate axial o recalibrar ssc/gate.

### 🔴 error biomecánico — `2.1_PATTERN_PRIMARY` (31)

- `tren_superior_press_inclinado_maquina_convergente` — Press Inclinado en Máquina Convergente: Empuje Vertical espera ['Deltoides'], pero primary=['Pectorales']. Corrección: Cambiar/añadir primary coherente con empuje vertical o corregir movementPattern.
- `tren_superior_press_inclinado_smith` — Press Inclinado en Smith: Empuje Vertical espera ['Deltoides'], pero primary=['Pectorales']. Corrección: Cambiar/añadir primary coherente con empuje vertical o corregir movementPattern.
- `back_dominadas_escapulares` — Dominadas Escapulares: Elevación Escapular espera ['Trapecio'], pero primary=['Dorsales']. Corrección: Cambiar/añadir primary coherente con elevacion escapular o corregir movementPattern.
- `back_hiperextensiones_45` — Hiperextensiones a 45º: Bisagra espera ['Glúteos', 'Isquiosurales'], pero primary=['Erectores Espinales']. Corrección: Cambiar/añadir primary coherente con bisagra o corregir movementPattern.
- `back_hiperextensiones_45_lastradas` — Hiperextensiones a 45º Lastradas: Bisagra espera ['Glúteos', 'Isquiosurales'], pero primary=['Erectores Espinales']. Corrección: Cambiar/añadir primary coherente con bisagra o corregir movementPattern.

### 🟡 bug de código — `4.3_MODIFIER_MUSCLE_NAME` (30)

- `tren_superior_press_banca_plano_barra` — Press de Banca Plano con Barra: Modifier usa "Pectoral Mayor", fuera del catálogo/anatomía. Corrección: Normalizar modifier.muscle a un padre canónico; sugerido "Pectoral Mayor".
- `tren_superior_press_banca_plano_mancuernas` — Press de Banca Plano con Mancuernas: Modifier usa "Pectoral Mayor", fuera del catálogo/anatomía. Corrección: Normalizar modifier.muscle a un padre canónico; sugerido "Pectoral Mayor".
- `tren_superior_press_banca_inclinado_barra` — Press de Banca Inclinado con Barra: Modifier usa "Pectoral Mayor", fuera del catálogo/anatomía. Corrección: Normalizar modifier.muscle a un padre canónico; sugerido "Pectoral Mayor".
- `tren_superior_press_banca_inclinado_mancuernas` — Press de Banca Inclinado con Mancuernas: Modifier usa "Pectoral Mayor", fuera del catálogo/anatomía. Corrección: Normalizar modifier.muscle a un padre canónico; sugerido "Pectoral Mayor".
- `tren_superior_press_banca_declinado_barra` — Press de Banca Declinado con Barra: Modifier usa "Pectoral Mayor", fuera del catálogo/anatomía. Corrección: Normalizar modifier.muscle a un padre canónico; sugerido "Pectoral Mayor".

### 🔵 sospechoso a revisar — `1.2_MULTIPLE_PRIMARY` (21)

- `ultimo_press_banca_agarre_inverso` — Press de Banca con Agarre Inverso: Múltiples primarios: Pectorales, Tríceps. Corrección: Confirmar sinergia real; conservar solo si ambos son motores principales.
- `ultimo_press_banca_tabla` — Press de Banca con Tabla (Board Press): Múltiples primarios: Tríceps, Pectorales. Corrección: Confirmar sinergia real; conservar solo si ambos son motores principales.
- `ultimo_flexiones_diamante_pared` — Flexiones Diamante en Pared: Múltiples primarios: Tríceps, Pectorales. Corrección: Confirmar sinergia real; conservar solo si ambos son motores principales.
- `back_remo_barra_recta_ancho` — Remo con Barra Recta (Agarre Ancho): Múltiples primarios: Trapecio, Dorsales. Corrección: Confirmar sinergia real; conservar solo si ambos son motores principales.
- `back_remo_banda_ancho` — Remo en Banda Elástica (Agarre Ancho): Múltiples primarios: Trapecio, Dorsales. Corrección: Confirmar sinergia real; conservar solo si ambos son motores principales.

### 🔴 error biomecánico — `3.1_HINGE_LOW_SSC` (21)

- `back_jefferson_curl_barra_recta` — Jefferson Curl con Barra Recta: Bisagra con barra usa ssc=0.4. Corrección: Elevar ssc a ≥1.0 salvo variante descargada documentada.
- `back_buenos_dias_zercher_barra` — Buenos Días Zercher con Barra: Bisagra con barra usa ssc=0.4. Corrección: Elevar ssc a ≥1.0 salvo variante descargada documentada.
- `back_hiperextensiones_45_barra` — Hiperextensiones a 45º con Barra: Bisagra con barra usa ssc=0.4. Corrección: Elevar ssc a ≥1.0 salvo variante descargada documentada.
- `hams_peso_muerto_rumano_barra_recta` — Peso Muerto Rumano con Barra Recta: Bisagra con barra usa ssc=0.5. Corrección: Elevar ssc a ≥1.0 salvo variante descargada documentada.
- `hams_peso_muerto_rumano_unilateral_barra_recta` — Peso Muerto Rumano Unilateral con Barra Recta: Bisagra con barra usa ssc=0.5. Corrección: Elevar ssc a ≥1.0 salvo variante descargada documentada.

### 🟠 inconsistencia de datos — `2.4_VARIANT_PRIMARY_DIVERGENCE` (15)

- `tren_superior_press_banca_plano_barra` — Press de Banca Plano con Barra: vg_bench_press contiene 2 conjuntos primary distintos. Corrección: Alinear primaries entre implementos o dividir variantGroupId por biomecánica.
- `tren_superior_press_banca_plano_mancuernas` — Press de Banca Plano con Mancuernas: vg_bench_press contiene 2 conjuntos primary distintos. Corrección: Alinear primaries entre implementos o dividir variantGroupId por biomecánica.
- `tren_superior_press_banca_inclinado_barra` — Press de Banca Inclinado con Barra: vg_bench_press contiene 2 conjuntos primary distintos. Corrección: Alinear primaries entre implementos o dividir variantGroupId por biomecánica.
- `tren_superior_press_banca_inclinado_mancuernas` — Press de Banca Inclinado con Mancuernas: vg_bench_press contiene 2 conjuntos primary distintos. Corrección: Alinear primaries entre implementos o dividir variantGroupId por biomecánica.
- `tren_superior_press_banca_declinado_barra` — Press de Banca Declinado con Barra: vg_bench_press contiene 2 conjuntos primary distintos. Corrección: Alinear primaries entre implementos o dividir variantGroupId por biomecánica.

### 🟠 inconsistencia de datos — `1.3_DUPLICATE_MUSCLE` (8)

- `glutes_zancada_cruzada_mancuernas` — Zancada Cruzada (Curtsy Lunge) con Mancuernas: Músculo repetido: Glúteo Medio. Corrección: Consolidar en una entrada y expresar la cabeza mediante emphasis.
- `glutes_zancada_cruzada_kettlebell` — Zancada Cruzada (Curtsy Lunge) con Kettlebells: Músculo repetido: Glúteo Medio. Corrección: Consolidar en una entrada y expresar la cabeza mediante emphasis.
- `glutes_zancada_cruzada_barra_recta` — Zancada Cruzada (Curtsy Lunge) con Barra Recta: Músculo repetido: Glúteo Medio. Corrección: Consolidar en una entrada y expresar la cabeza mediante emphasis.
- `glutes_zancada_cruzada_polea` — Zancada Cruzada (Curtsy Lunge) en Polea: Músculo repetido: Glúteo Medio. Corrección: Consolidar en una entrada y expresar la cabeza mediante emphasis.
- `glutes_zancada_cruzada_maquina_smith` — Zancada Cruzada (Curtsy Lunge) en Máquina Smith: Músculo repetido: Glúteo Medio. Corrección: Consolidar en una entrada y expresar la cabeza mediante emphasis.

## Auditoría de código y sistema

### 🟡 `subMuscleGroup` se pierde silenciosamente

El JSON declara `subMuscleGroup`, pero `ExerciseMuscleInfo` no tiene esa propiedad. Kotlin Serialization ignora la clave desconocida cuando el loader usa `ignoreUnknownKeys`, por lo que el dato no llega al dominio.

Corrección propuesta:

```kotlin
// Antes: no existe el campo
// Después:
val subMuscleGroup: String? = null,
```

Después se debe decidir si este campo es un padre redundante o una taxonomía distinta; añadirlo sin consumidores solo preserva datos, no corrige semántica.

### 🟡 Normalización deprecated usada en dos rutas

`SplitApplicationEngine.kt:500` y `ProgramDetailViewModel.kt:1360` llaman `VolumeCalculator.normalizeMuscleGroup`, pese a que el propio archivo indica que no se deben renormalizar claves canónicas. Esto puede separar cabezas y padres de manera distinta entre planificación y visualización.

Corrección propuesta: usar una API explícita `toParentMuscleKey(muscle, emphasis)` en la frontera de ingestión y transportar esa clave canónica sin volver a normalizarla.

### 🟡 Modifiers y catálogo usan vocabularios distintos

Los modifiers contienen 4 strings musculares distintos. Los no canónicos están marcados por `4.3_MODIFIER_MUSCLE_NAME`. Un modifier puede crear una clave que no coincide con el músculo base.

Corrección propuesta: deserializar modifier.muscle mediante el mismo alias→padre usado por involvedMuscles y rechazar en validación cualquier clave desconocida.

### 🟡 Volumen y fatiga tienen escalas distintas, pero se mezclan en la recuperación

`HYPERTROPHY_ROLE_MULTIPLIERS`/`VOLUME_CONTRIBUTION_FALLBACKS` usan secondary=0.5 y stabilizer=0.4; `FATIGUE_ROLE_MULTIPLIERS` usa 0.2 y 0.05. La diferencia es intencional, pero `AugeRecoveryEngine` multiplica el peso de fatiga por `volumeContribution` o por el fallback de volumen, introduciendo una doble ponderación de rol (por ejemplo secondary: 0.2×0.5).

Corrección propuesta: renombrar `activation` a una magnitud neutral documentada o separar `volumeContribution` de `fatigueActivation`; añadir tests de contrato que prohíban pasar multiplicadores de volumen a fórmulas de fatiga sin una conversión explícita.

### 🔵 `NEUTRALIZER`: soportado por código, ausente del catálogo

El enum sí está consumido por varias rutas; por tanto no es código muerto. Lo que tiene cero usos es el **dato JSON**: ningún involvement usa role=neutralizer. Además, algunas pantallas lo convierten o presentan como stabilizer.

Corrección propuesta: definir criterios anatómicos de neutralización y recategorizar únicamente casos revisados; si el producto no distinguirá el concepto, eliminarlo del contrato completo en vez de mantener semánticas divergentes.

### 🟡 TTC y nombres no canónicos

`MUSCLE_TO_ARTICULAR` intenta lookup directo y, en algunas rutas, fallback a pillar; otras rutas solo hacen lookup directo. Los strings no canónicos pueden quedar sin batería articular según el consumidor.

Corrección propuesta: centralizar `articularBatteriesFor(muscle, emphasis)` y prohibir accesos directos al mapa.

## Muestra del 10% y falsos negativos

Se seleccionaron 102 ejercicios sin flags rojos con seed fija. La heurística encontró señales adicionales no rojas en 80 y dejó 22 completamente limpios. Este resultado **no permite estimar clínicamente falsos negativos** sin vídeo/fuente biomecánica independiente; sí permite repetir exactamente la cola manual usando el CSV.

## Uso del CSV

Filtrar por `highestSeverity`, `flagCodes`, `variantGroupId` y `sample10Percent`. Cada fila representa exactamente un objeto del array y conserva músculos con rol/activation/emphasis en `muscles`.

## Criterio de cierre

1. Resolver primero errores rojos y duplicados/strings que rompen claves.
2. Congelar tests de esquema y vocabulario.
3. Recalibrar AUGE solo después de estabilizar anatomía, para no calibrar sobre músculos perdidos o duplicados.
4. Reejecutar este script y exigir 0 rojos, 0 claves desconocidas y 100% del conteo contractual.
