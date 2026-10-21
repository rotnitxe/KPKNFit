# B2 · HRV, estado autonómico y biometría de recuperación

Agente B2 (Opus 5.5) · 2026-10-21 · investigación web y literatura; motor AUGE leído en `C:\kw\r4i\…\domain\auge\` (solo lectura). Evidencia detallada, URL y salidas de la simulación: `B2-hrv-evidencia.md`.

Etiquetas: **[LITERATURA]** artículo o documentación pública (si llegó por cita de otra fuente: «vía …»); **[LITERATURA·NR]** conocimiento previo **no reverificado** en esta sesión (se agotó el cupo de búsquedas); **[OBSERVADO]** visto en el código de KPKN o en la documentación/API oficial; **[INFERIDO]** deducción mía con razonamiento; **[NO VERIFICABLE]**. Ciencia: **A** = convergencia de revisiones/ensayos o validaciones independientes; **B** = algunos estudios controlados o evidencia observacional consistente; **C** = casos, propietario sin validar o contradictorio.

---

## 0. Resumen ejecutivo

**Qué es.** Revisión de cómo miden y usan la variabilidad de la frecuencia cardíaca (HRV) HRV4Training, Kubios, Morpheus, WHOOP, Oura, Garmin/Firstbeat, Athlytic y Bevel, y de la ciencia (Plews, Buchheit, Kiviniemi, Vesterinen, Javaloyes, metaanálisis 2020–2021, perspectiva 2026), con una propuesta de integración en AUGE.

**Hallazgos.** (1) El estándar práctico es **ln rMSSD matinal** (≥ 60 s tras ~1 min de estabilización), **media de 7 días** (≥ 3 mediciones/semana) comparada con un **rango personal** (HRV4Training y Athlytic 60 días, Oura 2 meses, Kubios hasta 90 días) con banda de ±0,5 a ±1 DE; HRV4Training usó 1,5 → 0,75 → 1 DE según la versión. (2) Las puntuaciones comerciales (Recovery, Strain, Body Battery, PNS/SNS, Readiness) son **propietarias**: se conocen entradas, escalas y umbrales de color, no las fórmulas. (3) El entrenamiento guiado por HRV da efectos **pequeños** en VO2max y rendimiento de resistencia, no significativos en los dos metaanálisis de 2021 (g ≈ 0,08–0,17; SMD ≈ 0,13–0,20) y **no hay evidencia de beneficio en fuerza o hipertrofia**; tras una sesión de fuerza la HRV cae y suele volver en ~24 h, pero **no sigue** la fuerza isométrica ni la CK. (4) La cámara del móvil sirve en reposo con buena técnica, pero su error es varias veces el de una banda de pecho (MAPE 17,5 % frente a 2,2 % en un estudio independiente de 2026).

**Para AUGE.** HRV como **modulador acotado y asimétrico del estado matinal de Energía** (−10/+2 puntos; umbral 0,75 DE; 6 puntos por DE; ponderado por calidad de la fuente, adherencia y calibración), aplicado como **pseudo-sesión que decae con la propia dinámica de Energía** y subordinado a las anclas manuales. Primero en **modo informativo**; el ajuste numérico solo tras 8 semanas en modo sombra con criterios. Simulación propia (2 000 usuarios sintéticos): con banda de pecho, ≤ 1 % de días con un ajuste ≤ −3 puntos por azar; con cámara, la sensibilidad cae casi a cero.

**No pude ver.** Las páginas de WHOOP (403), las guías PDF de Kubios y Morpheus (binarias, ilegibles para la herramienta), parte de PubMed/MDPI/Medium; el cupo de búsquedas web del turno se agotó, así que algunos datos quedan marcados como no reverificados.

---

## 1. Identidad y arquitectura de las fuentes

| Fuente | Qué es | Dónde vive la lógica | Evidencia |
|---|---|---|---|
| HRV4Training (Marco Altini, A.S.M.A. B.V.) | App de medición matinal (cámara 60 s, banda BLE, Apple Watch, Oura, sensor PPG de algunos Samsung) + plataforma web Pro | PPG y RR en el teléfono (Android: 30 fps, canal rojo, Butterworth de 4.º orden, spline a 180 Hz); tendencias Pro en la web | [LITERATURA] blog 2017, QuickStart |
| Kubios (Kubios Oy, Finlandia) | Software científico (Kubios HRV Scientific), app «Kubios HRV – Daily Readiness» y servicios en la nube (Kubios Cloud, Readiness Analytics) | Métricas estándar documentadas; PNS/SNS/Readiness con ponderación propietaria | [LITERATURA] kubios.com; Tarvainen 2014 |
| Morpheus (Joel Jamieson) | Banda M7 + app; producto **distinto** de Kubios | Fórmula del recovery score no publicada | [LITERATURA] soporte de Morpheus |
| WHOOP | Pulsera + app + API; Recovery, Strain, Sleep | Puntuaciones servidas por la API (calculadas fuera del cliente) | [LITERATURA] developer.whoop.com; [INFERIDO] servidor |
| Oura | Anillo; Readiness con «contribuyentes» | Nube de Oura (HRV4Training lee los datos nocturnos de la nube de Oura) | [LITERATURA] Oura; QuickStart HRV4Training |
| Garmin / Firstbeat | HRV Status, Body Battery, Training Readiness | Algoritmos de Firstbeat; ubicación (reloj o nube) [NO VERIFICABLE] | [LITERATURA] manual Garmin |
| Athlytic | App iOS sobre Apple Health (Apple Watch) | Lee HRV y FC en reposo nocturnas de Apple Health | [LITERATURA] ayuda de Athlytic; el APK lo analiza A3 |
| Bevel | App iOS sobre Apple Health | Fórmulas no públicas | [LITERATURA] vía reseña 2026 |

---

## 2. Entradas de datos

| Fuente | Señal y momento | Duración | Otras entradas | Base personal | Datos mínimos |
|---|---|---|---|---|---|
| HRV4Training | ln rMSSD y FC al despertar, misma rutina, respiración libre | Cámara 60 s (más tiempo = más movimiento del dedo); banda 60 s/3 min/5 min | Cuestionario: calidad de sueño, motivación, rendimiento percibido, dolor muscular; carga de entrenamiento (Pro) | Base 7 días; rango normal 60 días | Base ≥ 4 días; insights 2–6 semanas; tendencias ≥ 40 días |
| Kubios Readiness | PNS index matinal, reclinado, sin hablar | 1–5 min (> 3 min recomendado), 15–30 s de relajación previa | — | Hasta 90 días | 1–2 semanas imprescindibles |
| Morpheus | HRV con M7, 10–30 min tras despertar, tumbado (sentado si la FC en reposo ronda 40 o menos y la HRV pasa de 90) | Arranca tras 10–20 s de estabilidad; prueba de 2:30 | Sueño (otro dispositivo o manual), cuestionario (sueño, dolor, bienestar), carga reciente y «carga vital» | No publicada | ≥ 4 pruebas/semana |
| WHOOP | HRV (RMSSD: la API expone `hrv_rmssd_milli`) y FC en reposo durante el sueño | Noche entera (ponderación hacia sueño profundo según WHOOP; no verificada en texto) | Frecuencia respiratoria, sueño; temperatura de piel y SpO2 (sus documentos se contradicen sobre si entran en el score) | No publicada | Hay fase de calibración (`user_calibrating`); duración no publicada |
| Oura | HRV y FC nocturnas, temperatura | Noche | Sueño, regularidad, actividad | Recientes 14 días ponderados (2–5 días con algo más de peso) frente a media de 2 meses | ~2 semanas de aprendizaje |
| Garmin HRV Status | HRV nocturna | Periodo de sueño | — | Rango basal personal dinámico | 3 semanas de sueño con el dispositivo |
| Athlytic | Media nocturna de HRV y FC en reposo de Apple Health | Noche | Sueño, FC de entrenamiento | 60 días móviles | No publicado |
| Bevel | HRV (SDNN por defecto de Apple; Bevel recomienda RMSSD y ventana de sueño completa) | Noche | Sueño, strain, estrés | No publicada | No publicado |

Nota [LITERATURA]: Apple Health expone la HRV del Apple Watch como **SDNN**, no rMSSD (reseña de Bevel 2026); las apps sobre Apple Health no son comparables numéricamente con las basadas en rMSSD.

---

## 3. Métricas y fórmulas

### 3.1 Índices de HRV (definiciones de Kubios y del estándar del Task Force)

| Métrica | Definición | Ventana | Comentario | Evidencia |
|---|---|---|---|---|
| RMSSD | √[ Σ (RRₙ₊₁ − RRₙ)² / (N−1) ] | 5 min estándar; 60 s aceptable en reposo | Índice vagal de elección en deporte; poco afectado por la respiración | [LITERATURA] Kubios; Esco & Flatt 2014; Buchheit 2014 — A |
| ln rMSSD | ln(RMSSD en ms) | ídem | Normaliza la distribución; base de todas las bandas | [LITERATURA] Plews 2012–2014; Buchheit 2014 — A |
| SDNN | √[ Σ (RRₙ − RR̄)² / (N−1) ] | 5 min | Mezcla variabilidad corta y larga; es lo que da Apple Health | [LITERATURA] Kubios — A |
| pNN50 | NN50 / (N−1) × 100 % | ≥ 5 min | Inestable en 60 s (no se pudo calcular su CV) | [LITERATURA] Johansson 2026 — B |
| SD1, SD2 (Poincaré) | SD1² = ½·SDSD²; SD2² = 2·SDNN² − ½·SDSD² | — | SD1 ≈ RMSSD/√2 | [LITERATURA] Kubios — A |
| LF, HF, LF/HF | 0,04–0,15 Hz y 0,15–0,40 Hz; Welch, AR o Lomb-Scargle; spline previo | ≥ 2–5 min | LF/HF **no** mide el «balance simpático-vagal» (Billman 2013); la respiración lenta lo invalida | [LITERATURA] Kubios; Shaffer & Ginsberg 2017 — C para decidir |
| Índice de estrés de Baevsky | SI = AMo·100 % / (2·Mo·MxDMn); Kubios muestra √SI; AMo = altura del histograma normalizado con clases de 50 ms; MxDMn = RR máx − RR mín; Kubios toma Mo como **mediana** de RR y elimina antes la tendencia de muy baja frecuencia | Histograma: minutos | Muy sensible a artefactos y a la duración (MxDMn) | [LITERATURA] Kubios — C |
| PNS index | Combinación ponderada (propietaria) de RR medio, RMSSD y SD1 normalizados contra normas de Nunan 2010 | — | 0 = media poblacional; en reposo suele estar en ±2 | [LITERATURA] Kubios — C (pesos no públicos) |
| SNS index | Ídem con FC media, √SI de Baevsky y SD2 | — | En ejercicio intenso o estrés: 5–35 | [LITERATURA] Kubios — C |
| Normas de Nunan 2010 (44 estudios, 21 438 personas, 5 min) | RR 926 ± 90 ms; SDNN 50 ± 16; RMSSD 42 ± 15; LF 519 ± 291 ms²; HF 657 ± 777 ms²; LF/HF 2,8 ± 2,6 | 5 min | **No** sirven para juzgar a un individuo (gran variación entre personas) | [LITERATURA] vía Shaffer & Ginsberg 2017 — A como norma, C como umbral individual |

**Corrección de artefactos (Kubios)** [LITERATURA]: (a) automática (Lipponen & Tarvainen 2019): umbral variable sobre la serie dRR = 5,2 × desviación cuartílica de los 90 latidos vecinos; patrones NPN/PNP = ectópico, PN/NP = largo/corto; latidos perdidos o extra contra la mediana de 10 RR; ectópicos y largos/cortos se interpolan. (b) por umbral: compara cada RR con una media local filtrada por mediana; niveles 0,45/0,35/0,25/0,15/0,05 s (referidos a 60 lpm y escalados con la FC); sustitución por spline cúbico; se recomienda el nivel más bajo que corrija y no más de ~5 % de latidos interpolados (guía de usuario, vía secundaria). (c) Eliminación de tendencia por *smoothness priors* (Tarvainen 2002).

### 3.2 HRV4Training

| Elemento | Definición | Ventana / umbral | Evidencia |
|---|---|---|---|
| Puntuación diaria («Recovery Points») | Transformación de ln rMSSD ajustada por antropometría | Fórmula no pública | [LITERATURA] blog; fórmula [NO VERIFICABLE] |
| Línea base | Media móvil de 7 días | Se muestra con ≥ 4 días | [LITERATURA] QuickStart |
| Rango normal | Media ± k·DE de los **últimos 60 días** (ventana móvil) | k = **1,5** (consejo diario, v6.3.6, mayo 2016); **0,75** (Pro, enero 2019); **1** (página Overview de Pro, marzo 2019); valor actual no publicado; según Altini, 0,5, 0,75 y 1 son aceptables y no hay una regla perfecta | [LITERATURA] blog 2016/2019; comentario de Altini |
| Por qué 60 días | Más corto: una enfermedad larga se vuelve «normal»; más largo: arrastra estacionalidad | — | [LITERATURA] Altini 2022/2023 |
| CV de la HRV | Variabilidad día a día; Pro la calcula sobre ~2 semanas; Plews usó el CV del ln rMSSD sobre 7 días | Lectura relativa a la propia historia; solo se colorean cambios «significativos» (el resto en gris) | [LITERATURA] Pro guide; blog CV — C |
| Tendencias (Pro) | Cambios de la base de HRV, FC y CV en las últimas 2 semanas frente a los 60 días previos | Categorías en §4 | [LITERATURA] Altini 2023 |

Ojo con dos «CV» distintos [INFERIDO]: el de HRV4Training/Plews es DE(ln)/media(ln) × 100 (típicamente 3–10 %); el «CV» de error típico de Buchheit/Hopkins se expresa sobre el valor bruto, ≈ 100·(e^DE(ln) − 1) (10–20 % para rMSSD). No deben mezclarse.

### 3.3 Puntuaciones comerciales

| Puntuación | Entradas y escala | Umbrales | Lo no publicado | Evidencia |
|---|---|---|---|---|
| Kubios Readiness | PNS index de hoy frente a la historia personal (hasta 90 días) | 4 categorías: muy baja (~2–3 % de los días), baja (~14 %), normal (~68 %), alta (~16 %) ⇒ cortes ≈ −2, −1 y +1 DE de la propia distribución [INFERIDO por los porcentajes de una normal]; zonas del PNS: < −2, −2…−1, −1…1, … | Pesos del PNS | [LITERATURA] blog Kubios — C |
| Morpheus recovery score | HRV, FC en reposo, sueño, entrenamiento reciente, carga vital, cuestionario | Porcentaje; 3 zonas de FC del día (azul recuperación, verde acondicionamiento, rojo sobrecarga) | Fórmula, pesos y cuánto se mueven las zonas | [LITERATURA] soporte Morpheus — C |
| WHOOP Recovery | HRV (RMSSD), FC en reposo, frecuencia respiratoria, sueño (± temperatura, SpO2) | 0–100 %: rojo 0–33, amarillo 34–66, verde 67–100 | Pesos (según su pódcast de 2019, la HRV pesa más) | [LITERATURA] developer.whoop.com — C |
| WHOOP Strain | 0–21 inspirado en Borg, no lineal («logarítmico»: cuesta más subir de 20 a 21 que de 10 a 11); carga cardiovascular por tiempo en zonas de FC (zonas por FC máx estimada por edad, sexo y peso, actualizada si se supera) + carga muscular (Strength Trainer) | Ligero 0–9, moderado 10–13, alto 14–17, máximo 18–21 | Fórmula; la comunidad la supone tipo TRIMP con compresión [NO VERIFICABLE] | [LITERATURA] WHOOP vía resultados de búsqueda — C |
| WHOOP Strain Target | Objetivo de strain según Recovery, strain acumulado del día y fase menstrual (si está activa) | Rango del día | Mapeo recuperación → objetivo | [LITERATURA] soporte WHOOP vía búsqueda — C |
| Oura Readiness | FC en reposo, HRV balance, temperatura, recovery index (cuánto tarda la FC en estabilizarse de noche), sueño, sleep balance, regularidad, actividad previa, activity balance | ≥ 85 óptimo; 70–84 bueno; < 70 atención | Pesos | [LITERATURA] Oura — C |
| Garmin HRV Status | Media de 7 días de HRV nocturna frente al rango basal | Balanced (dentro), Unbalanced (encima o debajo), Low (muy por debajo), Poor (por debajo del rango normal para la edad), No status | Cómo se calcula el rango | [LITERATURA] manual Garmin — B |
| Garmin Body Battery (Firstbeat) | Se carga con sueño y descanso; se drena con actividad y estrés (estrés derivado de la HRV) | 5–100 (Garmin también dice 0–100) | Algoritmo | [LITERATURA] Garmin — C |
| Athlytic Recovery / Exertion | Recovery 0–100 % = HRV y FC en reposo nocturnas frente a base de 60 días (la HRV pesa algo más; fija para el día). Exertion 0–10 = tiempo sobre un umbral personal con FC máx (media 30 días) y FC en reposo (media 60 días) | Target Exertion según Recovery y objetivo (línea verde en la gráfica) | Pesos, umbral | [LITERATURA] ayuda Athlytic — C |
| Bevel | Recovery, Strain personalizado según sueño y recuperación, Energy Bank (recuperación + sueño + strain + estrés) | — | Todo | [LITERATURA] vía reseña 2026 — C |

### 3.4 «Cambio mínimo» y bandas: qué se usa realmente

| Fuente | Referencia | Banda |
|---|---|---|
| Hopkins (SWC clásico) | Entre sujetos | 0,2 × DE **entre sujetos** (efecto pequeño de Cohen): pensado para tests de rendimiento y grupos, **no** para seguir la HRV de una persona [LITERATURA·NR] |
| Buchheit 2014 | El propio deportista | 0,5 × CV o 1 × CV; su tabla: SWC de ln rMSSD ≈ +3 %, FC en reposo ≈ −2 %; error día a día de ln rMSSD ≈ 10–20 % (≈ 12 % en otro estudio) [LITERATURA] |
| Kiviniemi 2007 | Media de 10 días | Media − 1 DE, o tendencia descendente 2 días [LITERATURA] |
| Javaloyes 2019; ensayos de «referencia fija» (Manresa-Rocamora 2021) | 4 semanas basales, ln rMSSD de 7 días | Media ± 0,5 DE [LITERATURA] |
| HRV4Training | 60 días | 1,5 → 0,75 → 1 DE según versión [LITERATURA] |
| Kubios Readiness | Hasta 90 días | ≈ ±1 y −2 DE [INFERIDO] |

---

## 4. Algoritmos y reglas de decisión

**4.1 Ensayos de entrenamiento guiado por HRV (resistencia).**
- Kiviniemi 2007 (n = 26, 4 semanas): HRV diaria (potencia HF); igual o mayor → sesión intensa; por debajo de (media de 10 días − DE) o 2 días bajando → suave o descanso. Mayor mejora de la carga máxima que el plan fijo. [LITERATURA]
- Vesterinen 2016 (n = 40 corredores, 4 + 8 semanas): sesiones moderadas o intensas solo con la HRV dentro del SWC individual; si no, suave. Menos sesiones duras (13,2 vs 17,7) y 3000 m +2,1 % vs +1,1 % (ES entre grupos 0,42). [LITERATURA]
- Javaloyes 2019 (n = 17 ciclistas): ln rMSSD de 7 días fuera de media ± 0,5 DE → bajar intensidad; potencia pico +5,1 % solo en el grupo HRV, sin diferencia significativa entre grupos. [LITERATURA]
- La regla moderna es **bidireccional**: salir del rango por arriba también es motivo de prudencia (Vesterinen, Javaloyes, Morpheus, Garmin; Le Meur 2013 describió hiperactividad parasimpática en sobrecarga funcional). [LITERATURA, Le Meur vía Altini 2025]

**4.2 Lo que dice el conjunto (A/B).** Granero-Gallegos 2020: VO2max, ventaja pequeña del guiado por HRV (ES ≈ 0,19 frente a control). Düking 2021 (8 estudios, 198 personas): umbrales submáximos g = 0,296 (significativo); rendimiento g = 0,079 y VO2pico g = 0,171 (no significativos); menos no respondedores. Manresa-Rocamora 2021 (8 estudios, 199 personas): HRV vagal (RMSSD/SD1) SMD 0,50 [0,09; 0,91]; VO2max 0,13 [−0,12; 0,39]; rendimiento 0,20 [−0,09; 0,48]; riesgo de sesgo de selección y detección poco claro o alto. Schaffarczyk & Sperlich 2026 (perspectiva): la HRV es un marcador **global y no específico**; faltan marcos multimodales; las puntuaciones comerciales son opacas; el límite ya no es medir, sino interpretar. [LITERATURA]

**4.3 Fuerza (evidencia más débil, C para decidir).**
- No hay ensayos de entrenamiento guiado por HRV en personas entrenadas en fuerza; en no entrenados, De Oliveira 2019 no encontró diferencias de hipertrofia ni de fuerza frente a programación fija. [LITERATURA vía Addleman 2024]
- Schneider 2019 (n = 37): tras una sobrecarga de fuerza de 6 días, FC supina ↑ y ln rMSSD supino ↓ (efectos pequeños), de pie sin cambios; la relación con el rendimiento fue «débil o inconsistente». [LITERATURA]
- Thamm 2019 (n = 10, prensa 5×10 al 70 % o 15×1 al 100 %): RMSSD bajó tras ambos (p. ej. 65,7 → 23,9 ms); la fuerza isométrica y la CK volvieron a la base a 24 h; **sin asociación** entre los cambios de HRV y los de fuerza o CK. [LITERATURA]
- Chen 2011 (halterófilos, n = 7): HF suprimida a 3 h; rendimiento y HF de vuelta a 24 h y por encima de la base a 48–72 h. [LITERATURA]
- Marasingha-Arachchige 2022 (metaanálisis): retirada vagal y activación simpática medidas 8–30 min después, mayores con más volumen. Addleman 2024: con más volumen o intensidad la caída se prolonga y puede requerir varios días. [LITERATURA]
- Holmes 2020: la PPG del móvil (HRV4Training, iPhone 6) **sobreestima** ln rMSSD 5 min después de la sesión (ES 1,14; r = 0,41): no medir justo después de entrenar. [LITERATURA]

**4.4 Reglas de las apps.**
- HRV4Training, consejo diario (desde v6.3.6, 2016, con Plews y Laursen): semáforo verde/amarillo/rojo a partir de la puntuación frente al rango normal, más el componente subjetivo y la tendencia; un icono amarillo avisa si se está cerca del límite inferior aunque el consejo sea verde. Pro: «estable», «afrontando bien» (HRV estable o ↑, FC estable o ↓, CV ↓), «mala adaptación» (FC y CV ↑, HRV ↓), «fatiga acumulada» (HRV ↓ y FC ↑, con CV ↓ o HRV bajo el rango), «saturación» (HRV ↓ y FC ↓, típica de FC en reposo muy baja; función experimental, sin regla numérica). [LITERATURA]
- Saturación parasimpática: en deportistas con historia larga los índices vagales pueden bajar aunque la actividad vagal suba; se distingue mirando la FC y el cociente ln rMSSD/RR; una subida grande de ese cociente fuera de un afinamiento puede indicar fatiga. [LITERATURA] Buchheit 2014
- Morpheus: con recuperación baja se ensancha la zona azul y bajan los umbrales verde y rojo; con recuperación alta se amplían verde y rojo; una HRV muy por encima o por debajo de la norma cuenta como recuperación suprimida. [LITERATURA]
- WHOOP: Strain Target por Recovery; «zonas de entrenamiento» óptima / sobrecarga / restauración, definidas con casi un millón de días de Strain y Recovery y su efecto sobre la HRV del día siguiente (más, igual o menos preparado mañana). [LITERATURA vía búsqueda]
- Athlytic: Target Exertion según Recovery y objetivo; una reseña señala que no tiene en cuenta la fatiga muscular. [LITERATURA]

---

## 5. Conceptos de interfaz (descritos)

1. **Banda personal sombreada** + línea de base de 7 días + puntos diarios; color solo si se sale de la banda, gris si no (HRV4Training).
2. **Etiquetas discretas** en vez de números crudos: Balanced/Unbalanced/Low/Poor (Garmin), 4 niveles (Kubios), óptimo/bueno/atención (Oura).
3. **Contribuyentes** con su estado individual (Oura): explica la puntuación; encaja con la sugerencia S2 de explicabilidad del PLAN.
4. **Estado de calibración** explícito: «calibrando» (WHOOP `user_calibrating`), «sin estado» (Garmin), 2 semanas (Oura).
5. **Objetivo de carga del día según la recuperación** (WHOOP Strain Target, Athlytic Target Exertion) y **diagrama de dispersión carga × recuperación** con zonas (WHOOP, Weekly Performance Assessment; la versión mensual lo convierte en serie temporal de «strain óptimo − real»).
6. **Zonas de FC dinámicas** recalculadas cada mañana (Morpheus).

---

## 6. Verificación del informe previo

| Afirmación | Veredicto | Evidencia |
|---|---|---|
| HRV4Training usa rMSSD | **CONFIRMADA** | ln rMSSD, mostrado como «Recovery Points» (transformación no pública) |
| «Media móvil de 7 días frente a línea base de 60 días» | **CONFIRMADA en lo sustancial; CORREGIDA en la terminología** | En HRV4Training la *baseline* es la media de 7 días; los 60 días definen el **rango normal**, no la línea base |
| «Banda de cambio mínimo significativo ±0,75 DE» | **CORREGIDA** | 0,75 DE es la cifra de Pro en enero de 2019; también usaron 1,5 DE (2016) y 1 DE (marzo de 2019); el valor actual no se publica. No es el SWC de Hopkins (0,2 × DE entre sujetos); los ensayos usan 0,5 DE intra-sujeto |
| HRV4Training detecta saturación parasimpática | **CONFIRMADA (con matiz)** | Función experimental de Pro: patrón HRV ↓ + FC ↓, sin regla numérica pública |
| «Kubios HRV / Morpheus: el estado matutino recalcula las zonas del día» | **CORREGIDA** | Son empresas y productos distintos. Quien recalcula a diario 3 zonas de FC es **Morpheus** (regla no publicada). Kubios da una readiness por categorías (PNS index frente a hasta 90 días) y consejos genéricos |
| «Ligarlo al umbral de pérdida de velocidad del VBT» | **NO VERIFICABLE** | No encontré estudios ni productos que fijen el umbral de VBT con la HRV matinal (búsqueda acotada). Como en fuerza la HRV no sigue la fuerza (Thamm 2019; Schneider 2019), no debería fijar umbrales de VBT sin validación propia |
| «Athlytic / Bevel / WHOOP: dispersión Recuperación % (X) × Strain 0–21 (Y) con túnel óptimo diagonal» | **PARCIALMENTE CONFIRMADA / CORREGIDA** | WHOOP sí tiene una dispersión strain × recovery en el Weekly Performance Assessment, con zonas óptima/sobrecarga/restauración calibradas con ~1 M de días y la HRV del día siguiente; **no** pude verificar la orientación de los ejes ni la forma «diagonal». Strain 0–21 es de WHOOP; Athlytic usa Exertion 0–10 con un objetivo dibujado como línea verde; Bevel tiene un objetivo de strain según sueño y recuperación. A3 puede confirmarlo en el APK de Athlytic |
| Implícito: la HRV mide el «estado autonómico» y sirve como fatiga neuromuscular o del SNC | **CORREGIDA** | La HRV refleja la modulación **vagal cardíaca**; en fuerza se disocia de la fuerza y la CK (Thamm 2019). No es un medidor del SNC |

---

## 7. Aplicabilidad a AUGE

### 7.1 Lo que encontré en el motor (para no romperlo)

- Energía: batería = 100·exp(−R/75), con R = carga acumulada / capacidad × 100; cada sesión aporta carga que decae con `recoveryDecay(h, τ) = exp(−ln 20 · h/τ)`. **El «τ = 36 h» de Energía es el tiempo al 95 % de recuperación, no la constante de tiempo** (≈ 12 h); en Energía el decaimiento usa horas reales, sin el alabeo de 24 h. [OBSERVADO] `AugeUtils.kt:30-44`, `AugeRecoveryEngine.kt:786-897`
- El ancla manual de Energía se convierte en carga implícita (−75·ln(1 − penalización/100)) y solo cuentan las sesiones posteriores al ancla. [OBSERVADO] `AugeRecoveryEngine.kt:798-805`
- Ya existen un desplazamiento aditivo aprendido `cnsLearningDelta` (acotado ±15), suelos fisiológicos y un hueco `lifeLoad` devuelto siempre en 0. [OBSERVADO] `AugeRecoveryEngine.kt:897, 1214-1233`
- Bandas: ≥ 85 alta, ≥ 70 normal, ≥ 50 moderada, ≥ 35 baja. [OBSERVADO] `RecoveryBands.kt`
- El PLAN prevé anclas como «innovación» del estado y evidencia como pseudo-sesiones (6.5), aprendiz de Kalman (6.6) y «sin migración Room» (principio 5). [OBSERVADO] `PLAN.md`
- Coherencia temporal [INFERIDO]: con τ95 = 36 h, a las 24 h se ha disipado ~86 % de la carga de una sesión; la HRV tras una sesión de fuerza típica vuelve en ~24 h (Chen 2011, Thamm 2019) y tarda más con mucho volumen (Addleman 2024). La escala temporal de Energía es compatible con la de la HRV: la HRV matinal reflejaría sobre todo carga residual + estrés vital, que es lo que Energía pretende aproximar.

### 7.2 Catálogo (ordenado por valor/esfuerzo)

| # | Métrica | Fórmula | Datos nuevos | Ciencia | Valor | Esfuerzo | Decisión · módulo |
|---|---|---|---|---|---|---|---|
| 1 | ln rMSSD matinal válido | ln(RMSSD) de ≥ 60 s analizados tras ≥ 60 s de estabilización; ≤ 5 % de latidos corregidos | Fuente HRV (§8) | A (medida) | Alto | S el cálculo; fuente S/M/L | **Adoptar** · nuevo `domain/auge/hrv/` |
| 2 | Base de 7 días con adherencia | x̄₇ = media de válidos en [t−6, t], exige n₇ ≥ 3 | ídem | B (Plews 2014) | Alto | S | **Adoptar** |
| 3 | Rango personal | μ, σ de válidos en [t−67, t−8] (60 días con una semana de desfase), por fuente y postura | ídem | B | Alto | S | **Adoptar (adaptado)**: el desfase evita que la caída actual contamine la referencia [INFERIDO] |
| 4 | FC en reposo del mismo registro y patrón conjunto | z de FC de 7 días; categorías tipo HRV4Training | ídem | B/C | Medio | S | **Adaptar** · solo información y signo del ajuste positivo |
| 5 | Indicador de calidad y confianza | c = c_ref · c_src · c_n7 (§7.3) | Metadatos de medición | B | Alto (honestidad) | S | **Adoptar** · enlaza con `sourceConfidence` |
| 6 | Regla de prudencia bidireccional | Fuera de rango (abajo o arriba) → no empujar intensidad | ídem | B (resistencia), C (fuerza) | Medio | S | **Adaptar** · solo para el texto y la sugerencia de readiness (solo reduce) |
| 7 | CV de ln rMSSD | DE/media sobre 7–14 días | ídem | C (dirección ambigua) | Bajo | S | **Descartar como señal**; a lo sumo, información |
| 8 | Cociente ln rMSSD/RR (saturación) | ln(RMSSD)/RR medio | ídem | C | Bajo | S | **Descartar** detección automática; sí frenar el ajuste positivo |
| 9 | PNS/SNS, Baevsky, LF/HF | §3.1 | Registros de 3–5 min | C | Bajo | M | **Descartar** (pesos propietarios, normas poblacionales, sensibles a respiración y artefactos) |
| 10 | HRV nocturna de un wearable | Media de RMSSD de la noche | Health Connect | B | Alto si se reabre | L + decisión | **Decisión del usuario** (§8) |
| 11 | Puntuación compuesta tipo Recovery / Strain / Body Battery | Propietarias | Wearable continuo | C | Bajo | L | **Descartar copiar**; adaptar solo la idea de «objetivo del día según recuperación», que AUGE ya cubre con readiness y reducción de carga |
| 12 | Dispersión carga × Energía con zonas | Días como puntos; zonas aprendidas con datos propios | Historial AUGE | C | Medio | M | **Adaptar** más tarde, como visualización de solo lectura; las zonas tendrían que salir del backtest (D6), no copiarse |

### 7.3 Diseño recomendado de la integración (sin Kotlin)

**Principios.** (i) La HRV **no sustituye** a Energía ni a las anclas: es una observación ruidosa del estado sistémico. (ii) Solo toca **Energía**; nunca Músculos ni Columna. (iii) **Asimétrica**: penaliza más de lo que premia, porque una HRV alta es ambigua. (iv) Pasa por la **misma dinámica** del motor (pseudo-sesión), no por un parche visual. (v) **Sin datos válidos no hay efecto** ni etiqueta de «normal».

**Paso 1 · Validez de cada medición (todas obligatorias).** Duración analizada ≥ 60 s tras ≥ 60 s de estabilización (Esco & Flatt 2014; Flatt & Esco 2016); latidos corregidos ≤ 5 % (Kubios); misma postura que la referencia; antes de entrenar y sin sesión en las 6 h previas (Holmes 2020; las 6 h son mías); hora dentro de ±90 min de la mediana personal [INFERIDO]; plausibilidad 5 ≤ RMSSD ≤ 250 ms y 35 ≤ FC ≤ 110 lpm [INFERIDO]. Una medición por día (si hay varias válidas, la primera).

**Paso 2 · Referencia personal (por fuente y postura).** R_t = valores válidos en los días [t−67, t−8]; μ_t = media(R_t), σ_t = máx(DE(R_t), 0,05). Alternativa equivalente: media y varianza exponenciales con α = 1 − 2^(−1/21) ≈ 0,032 (semivida de 21 mediciones). Calibración: |R_t| < 14 → sin efecto («calibrando n/14»); c_ref = 0,5 con 14–29 y 1 con ≥ 30. Un cambio de fuente o de postura abre una base nueva. Días etiquetados por la persona como enfermedad o alcohol se excluyen de R (etiqueta opcional).

**Paso 3 · Señales.** x̄₇ = media de válidos en [t−6, t], n₇ ≥ 3; z₇ = (x̄₇ − μ_t)/σ_t; z₁ = (x_t − μ_t)/σ_t; zFC₇ análogo con la FC (FC alta = positivo).

**Paso 4 · Categoría informativa** (con umbral que crece si hay pocos datos): k(n₇) = máx(0,75; 1,645/√n₇) ⇒ 0,95 con 3 días, 0,82 con 4, 0,75 con ≥ 5 [INFERIDO: prueba unilateral del 5 % sobre la media]. «Dentro de tu rango» si |z₇| ≤ k; «por debajo» si z₇ < −k (y «estrés acumulado probable» si además zFC₇ > +k); «por encima» si z₇ > +k (con zFC₇ < −k: «patrón ambiguo: adaptación o saturación», sin premio); «caída aguda» si z₁ < −1,5 con x̄₇ en rango → aviso suave sin efecto; dos días seguidos pasan a contar como «por debajo» (regla de Kiviniemi).

**Paso 5 · Ajuste de Energía (fase 2, acotado).**
ΔE_bruto = −mín(10; 6·máx(0; −z₇ − 0,75)) + [zFC₇ ≤ 0]·mín(2; 2·máx(0; z₇ − 0,75))
c = c_ref · c_src · c_n7, con c_src = 1 (banda BLE), 0,5 (cámara, SCG o valor copiado de otra app) y c_n7 = mín(1; n₇/5)
ΔE = c · ΔE_bruto (rango final −10…+2 puntos).

**Paso 6 · Cómo entra en el motor.** Como pseudo-sesión en el instante de medición t_m: con E_m = Energía del modelo en t_m, ΔR = −75·ln((E_m + ΔE)/E_m) y carga L = ΔR·capacidad/100, que decae con `recoveryDecay(h, τ_E)` igual que cualquier sesión (con τ95 = 36 h, a las 24 h queda ~14 %). Así se compone con sesiones y anclas, respeta suelos y desaceleración, y se desvanece antes de la medición siguiente. Precedencia: un ancla manual posterior a t_m la anula sola (el motor solo suma lo posterior al ancla); nunca baja Energía más de un escalón de banda por sí sola; el ajuste positivo no puede dejar R < 0.

**Paso 7 · Aprendizaje.** El aprendiz de τ (PLAN 6.6) debe evaluar h(θ) **sin** la pseudo-sesión de HRV para no confundir τ con la ganancia de la HRV. La ganancia (6 puntos/DE) solo se personalizaría tras el modo sombra, regresando (ancla manual − E_modelo) sobre z₇ con *shrinkage* hacia la ganancia por defecto. Más adelante, si se aprueba S10 (Energía de dos escalas), una desviación sostenida de z₇ (≥ 2 semanas) es la candidata natural para informar el compartimento lento.

**Sin datos.** Sin medición válida en 3 días → sin efecto y chip «sin medición» (nunca «100 %» ni «normal»). Más de 14 días sin medir → base «caducada»: hacen falta 7 mediciones nuevas antes de usar z₇.

**Fase 1 (recomendada para empezar): solo información.** ΔE = 0 siempre. Se muestran la categoría, la banda de 60 días y la calibración, y la HRV solo (a) baja la etiqueta de confianza cuando contradice al modelo en más de 1 DE y (b) refuerza, nunca dispara por sí sola, la sugerencia de reducción de carga ya existente (que solo reduce, con tope del 30 %).

### 7.4 Evidencia sintética de las constantes

Script propio `C:\kw\research\tools\B2\hrv_band_sim.py` (biblioteca estándar; 2 000 usuarios × 150 días; ruido biológico AR(1) φ = 0,3 con DE 0,10–0,18 en ln, coherente con el 10–20 % de Buchheit 2014 y el ~14 % intra-sesión de Johansson 2026; error de medida 0,03 banda, 0,06 cámara optimista (Plews 2017) y 0,15 cámara pesimista (Johansson 2026); g = 6, tope −10; solo la rama negativa, con c_ref = 1 y c_src de cámara = 0,6, algo menos prudente que el 0,5 del diseño). Detalle en el anexo.

| Fuente · adherencia | k₀ | Días nulos con ajuste ≤ −3 | Aviso «por debajo» en días nulos | Caída real de 1 DE, día 7: ajuste ≤ −3 / medio | Caída de 1,5 DE, día 7: ajuste ≤ −3 / medio |
|---|---|---|---|---|---|
| Banda · 7/7 | 0,5 | 3,0 % | 16,9 % | 48 % / −3,2 | 81 % / −5,8 |
| Banda · 7/7 | **0,75** | **1,0 %** | **7,8 %** | 31 % / −2,1 | **66 % / −4,4** |
| Banda · 7/7 | 1,0 | 0,3 % | 3,0 % | 17 % / −1,3 | 49 % / −3,2 |
| Banda · 4/7 | 0,75 | 1,4 % | 10,9 % | 25 % / −1,8 | 51 % / −3,4 |
| Cámara optimista · 7/7 | 0,75 | 0,1 % | 7,2 % | 10 % / −1,1 | 36 % / −2,4 |
| Cámara pesimista · 7/7 | 0,75 | 0,1 % | 5,3 % | 3,5 % / −0,6 | 12,5 % / −1,3 |

Lectura [INFERIDO]: con k₀ = 0,75 la HRV casi nunca mueve Energía ≥ 3 puntos por azar y, ante una caída real sostenida de 1,5 DE, la baja ~4 puntos de media con banda; con cámara pesimista el efecto es casi nulo, así que la cámara **solo debería informar** (fase 1), salvo que una prueba de repetibilidad propia (dos mediciones seguidas de 60 s con diferencia < 0,1 en ln) demuestre poco ruido. Con 3–4 mediciones por semana los avisos falsos suben (tabla analítica: 9,7 % unilateral con n = 3 y k = 0,75), de ahí el umbral k(n₇).

### 7.5 Cómo se evaluaría

1. **Sintético**: llevar este modelo al arnés de usuarios sintéticos del PLAN (0.3) generando la HRV desde la Energía latente del propio motor con un acoplamiento supuesto; probar enfermedad (−0,3 en ln durante 3–5 días), alcohol (días sueltos), saturación (HRV y FC bajan en usuarios muy entrenados), cambio de fuente, adherencia 3/7 y viajes. Invariantes: sin NaN; |ΔE| ≤ 10; nunca toca Músculos ni Columna; sin efecto sin datos; la misma semilla da el mismo resultado.
2. **Real, modo sombra ≥ 8 semanas** (encaja con D6): se calcula ΔE sin aplicarlo y se registra (z₇, zFC₇, ΔE, E_modelo, anclas manuales, residuo de e1RM y RPE a carga fija de la sesión siguiente, estrés declarado) en el anillo de observaciones del PLAN 6.6.
3. **Criterios para activar la fase 2**: ≥ 30 días emparejados; ρ de Spearman entre z₇ y (ancla − E_modelo) ≥ 0,2 con IC 95 % que excluya 0; MAE frente a las anclas al menos 1 punto mejor con ΔE; no más de un titular extra de «Prioriza recuperación» al mes atribuible a la HRV; la misma guardia del PLAN (si tras ≥ 8 observaciones el error empeora > 15 %, se congela).

### 7.6 Lo que NO hay que prometer al usuario

1. «Tu SNC está fatigado» o «tu sistema nervioso está listo»: la HRV mide modulación vagal cardíaca, no el SNC ni la fatiga neuromuscular.
2. «Detecta sobreentrenamiento»: un valor aislado no lo hace, y la HRV alta también aparece en sobrecarga funcional.
3. «Más HRV = más recuperado».
4. «La cámara equivale a un ECG»: error típico varias veces mayor que con banda (MAPE 17,5 % frente a 2,2 %; límites −30…+24 ms frente a −3…+4 ms).
5. «El entrenamiento guiado por HRV te hará más fuerte o más grande»: no hay evidencia en fuerza; en resistencia los efectos son pequeños.
6. Comparaciones con la población («tu HRV es baja para tu edad»): las normas no sirven para individuos.
7. Diagnóstico de arritmias o enfermedad: fuera de alcance; es una función de bienestar.
8. Que mide o sustituye el sueño (decisión cerrada: el sueño no se registra).
9. Que funciona en cualquier Android o que se puede medir justo después de entrenar.
10. Precisión de un número exacto de «preparación %» del día.

### 7.7 Riesgos

Calibración sin verdad de referencia (solo anclas y rendimiento); ruido de la cámara y fragmentación de Android; ciencia débil en fuerza; confusores (enfermedad, alcohol, ciclo menstrual, sueño, calor, viajes; Schaffarczyk & Sperlich 2026); adherencia (≥ 3 mediciones/semana); ansiedad por el número; datos de salud (§8); doble conteo con el estrés declarado (`stressLevel`), que obliga a decidir si la HRV ocupa el hueco `lifeLoad`.

---

## 8. Decisiones para el usuario y preguntas abiertas

**D-HRV1 · Fuente de la HRV** (de menor a mayor esfuerzo):

| Opción | Pros | Contras | Esfuerzo |
|---|---|---|---|
| a) Entrada manual (rMSSD y FC de otra app) | Inmediata; útil para quien ya usa HRV4Training, Kubios, Elite HRV u Oura | Métricas no comparables entre apps (Recovery Points, SDNN, nocturna); errores de tecleo; sin control de calidad; hay que pedir unidad y fuente y separar bases | S |
| b) **Banda BLE estándar** (servicio de FC 0x180D, característica 0x2A37 con RR en unidades de 1/1024 s [LITERATURA·NR: detalle de campos]) | Casi ECG en reposo (Polar H10: MAPE 2,2 %; límites −3,3…+4,0 ms); sin SDK de terceros; sirve también para la FC en sesión (S8 cardio) | Hay que comprarla y ponérsela; permisos BLE (Android 12+: escanear y conectar; ≤ 11: ubicación) [LITERATURA·NR]; algunos sensores ópticos no emiten RR | M · **recomendada si la HRV ha de mover números** |
| c) Cámara del móvil | Sin hardware; validada en reposo en iPhone (Plews 2017: TEE ≈ 6 %, diferencias triviales); basta 60 s | Fragmentación Android (frecuencia de fotogramas, distancia flash–lente en móviles recientes); perfusión baja; presión del dedo; error mayor en estudios independientes (Johansson 2026); probar en el Flip5 no generaliza | L · **solo informativa** |
| d) Acelerómetro con el móvil en el pecho (SCG, enfoque de Kubios) | Sin hardware; el proveedor declara MAE de RMSSD 2,0 ms (57 adultos, libro blanco de 2026) | Evidencia solo del proveedor; exige estar tumbado e inmóvil; acelerómetros ruidosos en Android de gama baja | L |
| e) **Reabrir Health Connect** (decisión **cerrada**; solo se plantea) | HRV y FC nocturnas pasivas sin ritual (más adherencia); Health Connect define un registro de RMSSD [LITERATURA·NR]; el proyecto ya tiene un flavor `health` con Health Connect | Reabre una decisión tomada; datos heterogéneos (nocturna vs matinal, algoritmos por dispositivo, no todas las apps escriben HRV); declaraciones y revisión de permisos de salud en Play; más superficie de privacidad; fragmenta flavors (el `base`, minSdk 24, no lo tiene) | L |

**D-HRV2 · ¿Solo informa o también mueve Energía?** Recomendado: fase 1 (informativa) ahora; fase 2 (ajuste acotado) solo si se cumplen los criterios de §7.5.
**D-HRV3 · Ritual matinal.** Medir es un hábito diario opcional de 2–3 min (≥ 3 veces por semana), en tensión con la decisión «sin cuestionario de 24 h». Pro: sin medición no hay efecto ni penalización. Contra: con poca adherencia la señal es casi inútil (§7.4). Las etiquetas opcionales de enfermedad o alcohol son, en la práctica, un mini cuestionario.
**D-HRV4 · Almacenamiento.** Una entidad nueva de mediciones (fecha, hora, fuente, postura, duración, RMSSD, ln, FC, % corregido, calidad, RR opcional) choca con «sin migración Room» del PLAN (Room v29 compartida): migración coordinada o almacén aparte.
**D-HRV5 · Privacidad.** Es un dato de salud: categoría especial en el RGPD (art. 9) y dato sensible en la ley chilena (Ley 19.628, «estados de salud») [LITERATURA·NR]. Recomendado: procesado solo en el dispositivo; fotogramas de cámara solo en memoria y nunca guardados; RR crudos opcionales con retención limitada; borrado sencillo; consentimiento explícito. Para Google Play, lo que nunca sale del dispositivo no cuenta como «recogido» en la sección de seguridad de datos [LITERATURA]; queda por comprobar cómo afectan la copia de seguridad automática y la exportación (D6). Afirmaciones solo de bienestar, sin diagnóstico.
**Preguntas abiertas.** ¿Cuánto se acopla de verdad la HRV a la Energía en usuarios de fuerza (solo lo dirá el modo sombra)? ¿Mostrar milisegundos o solo categorías? ¿Debe la HRV rellenar `lifeLoad` o sustituir parte del peso del estrés declarado? ¿Se acepta el diagrama carga × Energía como vista de solo lectura?
