# B1 · Autorregulación y gestión de la fatiga en fuerza (investigación pública)

Fecha: 2026-10-21 · Autor: investigador B1 (Sonnet 5.5) · Alcance: web y literatura; sin APK, sin descargas; del repositorio de KPKN solo se leyó (motor AUGE, `VolumeLandmarks.kt`, `Calculations.kt`, `VolumeCalculator.kt`, modelos de bienestar).
Etiquetas: **[LITERATURA]** (con subtipo: proveedor, secundaria, preprint), **[INFERIDO]**, **[NO VERIFICABLE]**, **[OBSERVADO·KPKN]** (visto en código del repositorio). Referencias `[R#]` al final; evidencia ampliada en `B1-fuerza-evidencia.md`.
Límite honesto: el presupuesto de búsquedas web (200 por turno, compartido con los demás investigadores) se agotó a mitad de la tarea. Desde entonces solo pude abrir páginas por URL directa; lo que sigue apoyado únicamente en un resumen de búsqueda va marcado «(resumen)». Varias páginas de editoriales (Springer, Taylor & Francis, PubMed, ayuda de RP) devolvieron 403 o redirección a cookies.

---

## 0. Resumen ejecutivo

B1 contrasta con fuentes públicas cómo JuggernautAI, Evolve, RTS y RP convierten series (peso, repeticiones, RPE/RIR) y percepciones subjetivas en decisiones de volumen y carga, y qué respalda la literatura.

1. **Ninguna de las cuatro publica sus fórmulas internas.** Lo público son reglas cualitativas y pocos umbrales: RP (matriz 1–4 de agujetas × rendimiento → +2–3 / +1 / mantener / descarga; puntuación de MEV por sesión 0–6), RTS (tabla RPE→%1RM; «fatigue percent» 3/5/7 %), JuggernautAI (readiness < 3 recorta series accesorias; MEV/MRV «individualizados» sin cifras). El **Stress Index** de RTS existe, pero su fórmula está tras un curso de pago.
2. **Los landmarks de RP se revisaron en 2023–24**: el MEV baja a ~la mitad en pecho, espalda, cuádriceps, isquios, tríceps y pantorrillas, y los glúteos suben. KPKN usa hoy valores de tipo 2017.
3. **La ciencia sostiene mejor** el volumen (dosis-respuesta con rendimientos decrecientes), la monitorización subjetiva y la RPE (subestimación media ≈ 1 repetición) que los landmarks exactos o la «fatiga del SNC»: tras sesiones de fuerza lo demostrado es una pérdida de función neuromuscular que dura 24–48 h, más tras series al fallo.
4. **Valor para AUGE:** una capa «Pro» con datos que ya registra: series duras por músculo frente a zonas MEV–MRV versionadas, tendencia de e1RM y deriva de RPE, caída de fatiga intrasesión, índice de bienestar de 4 ítems con z personal, calibración del RIR y deload reactivo multi-señal (§7, con ejemplo numérico).
5. **Correcciones al informe previo (§6):** «Evolve AI» existe pero es otro producto que JuggernautAI y que el APK EVOLVE; «Stress Index = reps × %1RM × RPE» no es verificable; RP usa bombeo, agujetas y carga de trabajo.

No pude ver: fórmulas propietarias (Stress Index, motores de Juggernaut y Evolve), el artículo original de «exertion load», las cifras de espalda de RP (imagen) ni artículos de pago.

---

## 1. Identidad y arquitectura

| Fuente | Qué es | Autoría | Dónde vive la lógica | Qué se ve |
|---|---|---|---|---|
| **JuggernautAI** | App de programación de powerlifting/powerbuilding con «Readiness Rating System» (pre-sesión, intra-sesión, sesión→sesión, semana, bloque, programa) [R1,R2] | Juggernaut Apps LLC; diseño de Chad Wesley Smith, tecnología del CTO Tim Arnold [R1,R8]. Garrett Blevins figura como cocreador en una fuente secundaria [R9] | No documentado; reseñas la describen como motor de reglas [NO VERIFICABLE] | Textos de marketing y ayuda; sin fórmulas |
| **Evolve Training App** (antes «EvolveAI»; Play: `com.skynetcoaching.expertsystem`) | App de powerlifting con motor «Genesis» (v2.0, jun-2025) y «Realtime Training» (oct-2025) [R10,R12] | Skynet Coaching Inc.; CEO Garrett Blevins; lógica de Zac Robinson (PhD) y Josh Pelland (PhD-C) según notas de versión [R12] | Reglas/heurísticas «en nube y en dispositivo» (relato de un foro, 2022) [R13]; no hallé patente (solo la marca SKYNET [R15]) | Analíticas con «stress index» y «session RPE»; «volume landmarks y agujetas en tiempo real» [R10] |
| **RTS** (Reactive Training Systems) | Método + software (RTS Lab, logs, TRAC) de Mike Tuchscherer | Tuchscherer | Propietario; Stress Index solo en Lab/Classroom de pago [R17,R19] | Tabla RPE→%1RM pública; porcentajes de fatiga públicos |
| **RP** (Renaissance Periodization) | Método (MV/MEV/MAV/MRV) + RP Hypertrophy App | Mike Israetel y equipo | Reglas manuales publicadas; app propietaria | Landmarks y matriz de progresión públicos |

**Atribución del informe previo.** «Evolve AI» **existe como producto**: Evolve Training App, de Skynet Coaching Inc. (dominio de soporte `evolveai.app`) [R12,R13]. **No es el APK «EVOLVE»** (`com.richardoestreich.evolve`, desarrollador independiente, A1): el paquete de Play es distinto [INFERIDO por nombres de paquete; el contenido del APK es trabajo de A1]. Otras «Evolve» (plataforma de formación corporativa, EvolveYou) no tienen relación. La conexión con JuggernautAI es de **persona** (Blevins), no de producto [R9,R10]. Un competidor (Dr. Muscle) afirma que Smith cofundó Evolve [R14]; no lo pude confirmar [NO VERIFICABLE].

---

## 2. Entradas de datos y mapeo a KPKN

| Sistema | Entradas | Escala | KPKN ya registra |
|---|---|---|---|
| JuggernautAI | Cuestionario inicial (sexo, edad, talla, peso, experiencia, fuerza, puntos débiles, estilo de vida, días/semana, fecha de competición); readiness previo (sueño, ánimo, energía, agujetas); RPE de serie superior y de back-off; check-ins de sesión/semana/bloque/programa [R2,R3]; v3.0: puntuación 0–100 con zonas, tendencias de sueño y peso [R6]; v2.0: cuestionario precargable desde Apple Health/Health Connect [R7] | Escalas no publicadas; solo «readiness < 3» (v2.5) [R5] | peso, reps, RPE/RIR, DOMS 1–5, estrés 1–5, motivación 1–5 [OBSERVADO·KPKN] |
| Evolve | Check-in previo («cómo te sientes») que ajusta la sesión y se puede anular; check-ins de bloque; sueño, agujetas y estrés como ejemplos [R10,R11] | no publicadas | idem |
| RTS | RPE por serie, e1RM, porcentaje de fatiga; TRAC (matutino): FC ortostática, test de golpeteo, tiempo de reacción [R24] | RPE 1–10 | RPE sí; TRAC no |
| RP | Por sesión: bombeo 0–2, reto muscular 0–2, agujetas 0–2. Por semana: recuperación de agujetas 1–4 y rendimiento 1–4 [R27]. App: bombeo, agujetas, carga de trabajo [R30] | ver §3.4 | DOMS 1–5, `strengthCapacity` 1–10 y `jointPain` por músculo tras la sesión [OBSERVADO·KPKN] |
| Ciencia | RPE/RIR por serie; e1RM; carga; duración y RPE de sesión (sRPE); bienestar 4–5 ítems | ver §3 | duración: derivable del log |

---

## 3. Métricas y fórmulas

### 3.1 Escala RPE–RIR y tabla RPE–%1RM [LITERATURA]
Escala de Zourdos (2016) popularizada por Tuchscherer/Helms: RPE 10 = 0 RIR; 9,5 = 0 RIR pero podría subirse carga; 9 = 1; 8,5 = 1–2; 8 = 2; 7,5 = 2–3; 7 = 3; 6 = 4; ≤5 = 5+ [R32,R33,R23]. La velocidad de barra cae al subir el RPE (r = −0,88 en experimentados) [R32]. Tabla de Tuchscherer (adaptada de la escala de Borg), % del 1RM:

| Reps | RPE 8 | RPE 9 | RPE 10 |
|---|---|---|---|
| 1 | 92,2 | 95,5 | 100 |
| 3 | 86,3 | 89,2 | 92,2 |
| 5 | 81,1 | 83,7 | 86,3 |
| 8 | 73,9 | 76,2 | 78,6 |
| 10 | 68,0 | 70,7 | 73,9 |

Cada paso de RPE o de repetición cambia el porcentaje ≈ 2–3 puntos. La tabla viene de datos de powerlifting (S/B/D) [R23]; RTS advierte de no usarla para estimar el 1RM [R20].

### 3.2 e1RM: fórmulas y desviaciones
| Fórmula | Expresión | % del 1RM implícito a 5 / 10 / 15 reps |
|---|---|---|
| Epley | w(1 + r/30) | 85,7 / 75,0 / 66,7 |
| Brzycki | 36w/(37 − r) | 88,9 / 75,0 / 61,1 |
| Lombardi | w·r^0,10 | 85,1 / 79,4 / 76,3 |
| Wathan | 100w/(48,8 + 53,8·e^(−0,075r)) | 85,8 / 74,2 / 66,3 |
| Mayhew | 100w/(52,2 + 41,9·e^(−0,055r)) | 84,0 / 76,4 / 70,6 |
| Lander | 100w/(101,3 − 2,67123r) | 87,9 / 74,6 / 61,2 |
| **KPKN híbrido** | Brzycki ≤10, Epley 11–20 [OBSERVADO·KPKN] | 88,9 / 75,0 / 66,7 |

Cálculo propio [INFERIDO a partir de las fórmulas]. Referencia empírica (Nuzzo 2024: 269 estudios, 7 289 personas): ~5 reps a 90 % (banca ~4; prensa ~9), banca 8,8 reps a 80 % (IC 7,7–10,1) y 14,1 a 70 %; prensa 13,1 y 19,0; sexo, edad y nivel influyen poco, **el ejercicio sí** [R48]. Conclusión [INFERIDO]: las fórmulas coinciden ±3 puntos hasta ~8 reps y divergen más allá de 10; en prensa se hacen ~19 reps a 70 % frente a ~13 que implica Epley, de modo que el e1RM de series largas de tren inferior se sobreestima. Regla propuesta: **calcular e1RM solo con reps + RIR ≤ 10**. Un preprint de 2026 (un solo autor, 303 494 series de una app) propone un factor que depende del peso, validado solo por consistencia interna, sin 1RM medidos [R49] [LITERATURA · preprint; no adoptar].

### 3.3 RTS: porcentaje de fatiga, Stress Index, «exertion load»
| Métrica | Definición | Constantes y umbrales | Evidencia |
|---|---|---|---|
| **Porcentaje de fatiga** (RTS) | Tras una serie inicial a RPE 9–10, se baja la carga un X % y se repite (mismo RPE) hasta que el RPE vuelve a ser el de la inicial [R20,R21,R22] | 0 % nada; 2 % mínima; 3 % ≈ RPE 8→8,5; 5 % ≈ 8→9 (media); 7 % ≈ 8→10 (alta). Ej.: 300×3@9 → 285×3@9 = 5 % | [LITERATURA · proveedor/divulgación]; heurística de entrenador |
| **Stress Index** (RTS) | Según RTS Lab, se calcula a partir de RPE, intensidad de carga y esfuerzo total; considera el estrés de **cada repetición**; se ajusta a la tabla RPE personal; se parte en **Estrés Periférico** (≈ reps efectivas, estímulo de hipertrofia), **Central** (≈ intensidad, carga sistémica) y **CS Balance** [R16,R17,R18,R19] | **Fórmula no pública** (módulos de pago de RTS Classroom/Lab) | [LITERATURA · proveedor] cualitativa; fórmula [NO VERIFICABLE] |
| **Exertion load** (Frederick, STRONGUR) | Σ por repetición de masa × ROM × peso por proximidad al fallo; una versión daría e^(−0,215·(RIR + n − i)) por repetición i de n [R25] | 0,215: no confirmada | [NO VERIFICABLE] contra el original (certificado caducado; solo resumen de una copia) |
| **HNFM** (foro Exodus) | «Costo de fatiga» de la sesión normalizado por repeticiones, útil ~65–90 % del e1RM | H≈200 ligera, ≈400 media, ≈600 >2 días de recuperación | [LITERATURA · foro]; sin validación [R26] |

### 3.4 RP: landmarks, matriz de progresión y puntuación de MEV
**Definiciones RP** [R27]: serie de trabajo = 30–85 % 1RM, 5–30 reps, 0–4 RIR; **solo cuentan series directas** (principal motor o aislamiento; el aporte indirecto ya está en las cifras); tope por sesión 8–12 series por músculo [R28]; MV aplica si se entrena ≥ 2 veces/semana; \*P = especialización con el resto reducido. MRV = la semana en que el rendimiento ya no se iguala (puntuación 4), a confirmar repitiendo la semana si hubo mal sueño o estrés.

**Tabla completa de landmarks** (series/semana). RP 2017 [LITERATURA · secundaria: el artículo original no estuvo accesible; MEV/MRV coinciden en 3 fuentes, MV/MAV en 1–2] [R31]. RP 2023–24: guías por músculo, «intermedios serios con 3–7 años» [R28]; MEV/MRV cruzados con Lift Vault [R31].

| Músculo (pilar AUGE) | RP 2017: MV / MEV / MAV / MRV | RP 2023–24: MV / MEV / MAV / MRV | KPKN hoy: MEV / MAV / MRV [OBSERVADO·KPKN] |
|---|---|---|---|
| Pectorales | 8 / 10 / 12–20 / 22+ | 2–4 / 4–6 / 6–16 / 16–24 | 8 / 14 / 22 |
| Dorsales (espalda) | 8 / 10 / 14–22 / 25+ | n/d / 6–8 / n/d / 20–26 † | 10 / 16 / 25 (dorsal); 6 / 12 / 20 (alta) |
| Deltoides lateral | 6 (0–6) / 8 / 16–22 / 26+ | 2–6 / 6–8 / 8–24 / 24–30 | 8 / 16 / 26 |
| Deltoides posterior | (= lateral en 2017) | 0–4 / 0–4 / 4–12 / 12–20 | 6 / 12 / 20 |
| Deltoides anterior | 0 / 0 / 6–8 / 12 | 0–2 / 0–2 / 4–8 / 8–12 | 0 / 8 / 16 |
| Bíceps | 4 / 8 / 14–20 / 26+ | 6–8 / 8–10 / 14–20 / 20–26 | 8 / 14 / 26 |
| Tríceps | 4 / 6 / 10–14 / 18+ | 0–4 / 4–6 / 6–16 / 16–20 | 6 / 12 / 18 |
| Cuádriceps | 6 / 8 / 12–18 / 20+ | 2–4 / 4–6 / 6–14 / 14–18 | 8 / 14 / 20 |
| Isquiosurales | 4 / 6 / 10–16 / 20+ | 0–2 / 2–4 / 2–8 / 8–14 | 6 / 12 / 20 |
| Glúteos | 0 / 0 / 4–12 / 16+ | 2–6 / 6–8 / 8–24 / 24–30 | 0 / 8 / 16 |
| Pantorrillas | 6 / 8 / 12–16 / 20+ | 2–4 / 4–6 / 6–16 / 16–24 | 8 / 12 / 20 |
| Abdomen / Core | 0 / 0 / 16–20 / 25+ | 0–4 / 0–4 / 4–12 / 12–20 | 0 / 8 / 25 |
| Trapecio | 0 / 0 / 12–20 / 26+ | 0–4 / 0–4 / 4–12 / 12–20 | 6 / 12 / 20 (espalda alta) |
| Antebrazo | no cubierto | 0–4 / 0–8 / 8–24 / 24–30 | 0 / 8 / 16 |
| Erectores espinales | sin landmark | sin landmark | 0 / 8 / 16 (propio de KPKN) [NO VERIFICABLE] |

† La tabla de espalda de RP es una imagen; MEV/MRV proceden de Lift Vault (secundaria); un texto de ayuda de RP habla de «al menos 10 series» para crecer [R30, resumen de búsqueda] — contradicción no resuelta. Las variantes \*P y otras notas están en el apéndice. Hallazgo clave [OBSERVADO·KPKN + LITERATURA]: con los puntos medios de RP-2024, el MRV de KPKN es **+82 % en isquiosurales** (20 frente a 8–14), **+25 % en cuádriceps** (20 frente a 14–18) y **−41 % en glúteos** (16 frente a 24–30); el MEV de pantorrillas es +60 % (8 frente a 4–6). Pectoral, bíceps, tríceps y deltoides lateral difieren ≤ 13 % en el MRV.

**Matriz de progresión semanal de RP** (agujetas S × rendimiento P) [R27]. Agujetas: 1 ninguna, 2 curadas antes de la siguiente sesión, 3 curadas justo a tiempo, 4 aún con agujetas. Rendimiento: 1 supera objetivos con facilidad, 2 cumple, 3 con dificultad, 4 no iguala la semana previa.

| S \ P | 1 | 2 | 3 | 4 |
|---|---|---|---|---|
| 1 | **+2–3 series** | **+1** | mantener ‡ | recuperación o descarga |
| 2 | **+1** | **+1** | mantener ‡ | recuperación o descarga |
| 3 | mantener | mantener | mantener | recuperación o descarga |
| 4 | mantener ‡ | mantener ‡ | mantener | recuperación o descarga |

‡ Celdas que RP no define explícitamente; las relleno por la regla «algún 3 con agujetas 3–4 → mantener» [INFERIDO]. **MEV por sesión:** bombeo + reto muscular + agujetas, cada uno 0–2; total 0–1 = bajo MEV; 2–4 = en MEV; 5–6 = sobre MEV. **Ejemplo de mesociclo:** 12→14→16→18→20 series y descarga a 6 series (mantenimiento) [R27]; RIR 4→3→2→1 en 4 semanas de acumulación [R29]; el MRV roto termina la acumulación [R29]. Tres criterios de autorregulación: recuperación de rendimiento, recuperación percibida y proxy de estímulo (bombeo) [R29].

### 3.5 Evidencia científica compacta
| Tema | Hallazgo (cifras) | Fuente | Nivel |
|---|---|---|---|
| Precisión del RIR | Subestimación media 0,95 reps (IC 0,17–1,73; 12 estudios, 414 personas), mejor cerca del fallo y en series cortas [R37]. 141 usuarios de gimnasio: error medio 0,95–3,9 reps según ejercicio; expertos 0,2–1,9, grupo menos experimentado hasta 8,5 [R36]. ~0,5 reps a 1 RIR y ~1,1–1,2 a 4–5 RIR [R38] | Halperin 2022; Steele 2017; Remmert (vía SBS) | B |
| RPE vs % fijo | 8 semanas, n = 21: sentadilla +17,1 vs +13,9 kg (p = 0,32; ES 0,50); regla: ±2 % de carga por cada 0,5 RPE fuera de rango [R34]. Metaanálisis: diferencia 2,07 kg (IC −0,32 a 4,46); RPE/RIR 3,15 kg (p = 0,06; 3 estudios) [R42]. RIR > carga fija en 12 semanas (n = 31) [R40]. APRE > periodización lineal en 6 semanas (n = 23) [R39] | Helms 2018; Hickmott 2022; Graham & Cleather 2021; Mann 2010 | B–C |
| Volumen→hipertrofia | Más volumen = más masa con rendimientos decrecientes (35 estudios, 1 032 personas); fuerza: meseta ~5 series/semana (66 estudios, 2 020) [R45,R46]. Nuckols: ≈10 series «buen punto», 15–25 máximo, techo prudente ≈25; conteo 1 principal + 0,5 sinérgico; ~11 series fraccionales/sesión es el punto de no superioridad (preprint) [R45,R46]. ≥10 vs <5 series/semana favorece lo alto [R55 de memoria] | Pelland 2025; Remmert 2025; Schoenfeld 2017 | B |
| Proximidad al fallo | Hipertrofia mejora al acercarse al fallo (≈10 % a 0 RIR vs ≈5 % a 10 RIR según SBS); fuerza ≈ plana [R44,R46]. Fallo vs no fallo: ES 0,19 (IC 0,00–0,37; 15 estudios); fallo momentáneo ES 0,12 (n.s.) [R43] | Robinson 2024; Refalo 2023 | B |
| Deload | Una semana sin entrenar (n = 39, 9 semanas): sin diferencias en hipertrofia/potencia; la fuerza favoreció seguir entrenando (prob. posterior 0,85–0,92); financiado por RP [R47] | Coleman 2024 | B |
| Monitorización | Subjetivo más sensible que objetivo a cambios de carga (56 estudios) [R51]; CMJ: la altura media detecta mejor fatiga que el mejor salto (151 artículos) [R52] | Saw 2016; Claudino 2017 | B |
| Recuperación neuromuscular | Tras sesión pesada (10 atletas), caída 12–14 % vs 6–7 % moderada; recuperación hasta 33 h [R54]. Al fallo, la recuperación a 24–48 h es más lenta y la caída aguda mayor (n = 10) [R54]. Tesis 2017 (n = 11): fuerza isométrica −19 a −22 %, recuperada a 24 h [R54] | Raastad 2000; Morán-Navarro 2017 | B–C |

### 3.6 Carga interna
**sRPE** (Foster 2001): carga = RPE de sesión (CR-10, retrospectivo) × minutos; **monotonía** = media diaria/DE diaria semanal; **tensión** = carga semanal × monotonía [R50]. Válido y fiable en deporte (36 estudios de validez entre 950 que lo citan); en fuerza, Day 2004, McGuigan & Foster 2004 (ICC 0,95) y Egan 2006, que halló que el sRPE depende del **modo** de ejercicio y no del porcentaje relativo [R50]. No hay umbrales de lesión validados en fuerza [INFERIDO]. **Volumen-carga** (Σ peso×reps) distorsiona entre intensidades (argumento de Tuchscherer para el Stress Index) [R16]. **Series duras** = RP; **series fraccionales** = Pelland [R45].

### 3.7 Bienestar
Índice de Hooper (nadadores de élite): sueño, fatiga, estrés y agujetas, 1–7 [R51]. Los cuestionarios de «wellness» tipo McLean añaden ánimo en 5 niveles (confirmación parcial). JuggernautAI pregunta sueño, ánimo, energía y agujetas [R3].

---

## 4. Algoritmos y reglas de decisión

1. **RP:** matriz S×P (§3.4); empezar el mesociclo cerca del MEV, subir series de forma **autorregulada** (ejemplos: 2→4 sin agujetas ni bombeo; 5→5–6 con buenos bombeos y recuperación temprana; más allá, «sin necesidad») y terminar la acumulación al romper el MRV; cada ~4–6 semanas, descarga [R29]. La app ajusta series según bombeo, agujetas y carga de trabajo (poco bombeo/fácil → más; buena respuesta → igual; mucho/sin recuperar/exceso → menos) y sube el peso «unos pocos puntos» por semana (si el salto es grande, añade una repetición) [R30]. Regla de RP en su ayuda: si no igualas repeticiones en 2 sesiones seguidas, probable MRV local → sesión de recuperación a la mitad de volumen y descarga (resumen).
2. **RTS:** elegir el X % de fatiga, bajar carga o repetir hasta que se alcance el RPE objetivo; el volumen del día **emerge** del rendimiento; el reporte posterior compara estrés central y periférico con la línea base propia (ej.: bloque 1 central bajo base y periférico sobre base; bloque 2 al revés) [R18,R21].
3. **Helms 2018:** rango de RPE por semana (5–7, 6–8, 7–9, 8–10); fuera de rango, ±2 % de carga por cada 0,5 RPE; las repeticiones falladas suman RPE [R34].
4. **APRE (Mann 2010):** las repeticiones logradas hasta el fallo en la serie 3 deciden el peso de la serie 4 y de la sesión siguiente; la tabla exacta de ajustes no se pudo verificar [NO VERIFICABLE]; resultados en §3.5.
5. **JuggernautAI / Evolve:** el RPE de la serie superior y de back-off ajusta las siguientes; el readiness previo modifica volumen o peso; el check-in semanal baja series con puntuación < 3 (v2.5, accesorios) [R3,R5]; ante duda se aconseja el RPE mayor [R7]. Evolve añade o quita series según la fatiga y ofrece deload reactivo bajo demanda [R10].

---

## 5. Conceptos de interfaz relevantes (descritos)
- **Puntuación única 0–100 con zonas** (JuggernautAI v3.0) y tendencia de peso/sueño en el mismo panel [R6].
- **Gráfico estrés central vs periférico frente a línea base propia** por ejercicio/bloque (RTS Lab) [R17,R18]; informes que relacionan pares repetición–RPE con los mejores resultados y con los picos, y muestran cuánto y qué tipo de trabajo tolera el atleta [R17].
- **Cambios reversibles:** el usuario puede anular los ajustes del readiness (Evolve) [R10] y RP permite editar series/peso a mano [R30].
- **Tres preguntas por sesión** (bombeo, reto, agujetas) y **dos por semana** (agujetas, rendimiento) en RP [R27]: carga de entrada baja, útil como patrón.

---

## 6. Verificación del informe previo

| Afirmación | Veredicto | Evidencia |
|---|---|---|
| «Evolve AI / JuggernautAI: MEV/MAV/MRV por grupo muscular en tiempo real» | **CORREGIDA** | JuggernautAI publica «volume landmarks individualizados» y puntos de partida **MEV/MRV** (no MAV) ajustados por sexo, talla y peso, reconfigurables a mitad de programa [R3,R5]; sin cifras ni «por grupo muscular en tiempo real». Evolve afirma seguir «landmarks y agujetas en tiempo real» [R10] sin detalle |
| «Readiness previo con recorte automático de series o RPE objetivo» | **CONFIRMADA** (cualitativa) | Readiness previo modifica volumen o peso; check-in semanal baja series si <3; el RPE de serie ajusta las siguientes [R3,R5]; Evolve ajusta y permite anular [R10]. Magnitudes y escalas no públicas |
| «Evolve AI / JuggernautAI» como un mismo sistema | **CORREGIDA** | Dos productos; vínculo por Blevins (§1). EVOLVE (APK de A1) es un tercero |
| RTS: RPE/RIR | **CONFIRMADA** | §3.1 |
| RTS: «Stress Index a partir de reps × %1RM × RPE» | **NO VERIFICABLE** (parcial) | Insumos publicados: RPE, intensidad de carga y esfuerzo total, por repetición, con tabla RPE personal [R17]; la forma funcional es de pago; no es un simple producto |
| RTS: estrés central vs periférico | **CONFIRMADA** | central ≈ intensidad; periférico ≈ efecto local/reps efectivas [R16,R17,R18] |
| RP App: «bombeo, agujetas, dolor articular, rendimiento → añadir/quitar series» | **CORREGIDA** | Oficial: bombeo, agujetas y carga de trabajo [R30]; la regla manual usa agujetas y rendimiento (1–4) [R27]; dolor articular no confirmado (una reseña dice que no interviene) |
| VBT: velocidad de calentamiento vs media de 4 semanas al mismo peso; pérdida de velocidad | Fuera de B1 (→ B4) | Lo visto: umbral de pérdida ≤25 % favorece fuerza y >25 % hipertrofia (3 estudios) [R42]; las series al fallo (máxima pérdida de velocidad) alargan la recuperación [R54] |
| «Trabajo mecánico = fuerza × ROM» como carga | **Matiz** | Correcto físicamente; ninguna fuente validó su uso como carga de fuerza; solo el «exertion load» de Frederick lo usa [NO VERIFICABLE] |
| «Fatiga neuromuscular objetiva» (el «santo grial») | **CORREGIDA** | Velocidad/CMJ miden función neuromuscular (periférica y central), no «fatiga del SNC»; ver §7.4 |

---

## 7. Aplicabilidad a AUGE

### 7.1 Modelo actual frente a la literatura [OBSERVADO·KPKN + LITERATURA]
| Rasgo de AUGE | Observación | Veredicto |
|---|---|---|
| Serie «efectiva» si RPE efectivo ≥ 6 (RIR ≤ 4) | Coincide con la definición de serie de trabajo de RP (0–4 RIR) [R27] | Mantener |
| Multiplicador de RPE 1 + (RPE/10)^4,2 (RPE 6 = 1,12; 8 = 1,39; 10 = 2,0; fallo hasta 2,3) | Convexo. Frente a la «exertion load» (constante 0,215 no verificada), AUGE es más plano lejos del fallo y similar en el último tramo: con 5 reps, RIR 4→2→0 da 0,80 : 1 : 1,44 en AUGE y 0,65 : 1 : 1,54 en exertion load [INFERIDO, cálculo propio] | Razonable para **coste de recuperación**; no para «estímulo» |
| reps^0,65 | Sublineal; mismo orden que exertion load (10 vs 5 reps a RIR 2: 1,57 vs 1,34); coherente con que el sRPE no escale con el porcentaje relativo [R50] | Mantener |
| Multiplicador muscular por serie acumulada: sube hasta 1,10 en la serie 3 y cae a ≤0,3 pasadas ~10 | Se parece a rendimientos decrecientes del **estímulo** (~11 series fraccionales/sesión) [R45]; el coste de recuperación al fallo no decrece (Morán-Navarro) [R54] | Pregunta abierta: ¿la batería mide estímulo o recuperación? [INFERIDO] |
| Descanso, densidad, técnicas | Sin evidencia verificada en esta ronda | [NO VERIFICABLE] |
| `OvertrainingDetector` (≥3 de 5: volumen>MRV, dolor, fatiga ≥8, DOMS ≥3,5 o fuerza ≤5, peso en descenso) | Es un esbozo de las reglas multi-señal de RP | Refinar con líneas base personales |
| Volumen contado con aportes 1,0 / 0,5 / 0,4 / 0,1 por rol | Cercano a Pelland (1 y 0,5); pero los landmarks de RP cuentan **solo directas** | Riesgo de **mezclar** criterios (§7.5) |

### 7.2 Panel «Pro»: métricas con fórmula, umbral y ejemplo
**Semana inventada** (script `C:\kw\research\tools\b1\ejemplo_semana.py`): Superior A, Inferior A, Superior B, Inferior B; 74 series, todas duras, RPE medio 8,8, 26 % con RPE ≥ 9,5. Banca de referencia: 77,5×6@8 → 80×6@8 → 82,5×6@8,5 → 82,5×6@9,5; sentadilla: 105×5@8 → 107,5×5@8 → 110×5@8,5 → 110×5@9. Rol 1,0 principal, 0,5 secundario.

| # | Métrica | Fórmula | Umbral | Resultado del ejemplo |
|---|---|---|---|---|
| M1 | **Series duras semanales** (directas D, fraccionales F) | D = Σ series con RPE ≥ 6 donde el músculo es principal (unilateral 0,5 por lado); F = D + 0,5·Σ donde es secundario; ventana móvil de 7 d | Zona: < MEV / MEV–MAV / MAV–MRV / > MRV; «alto» ≥ 85 % del MRV | Cuádriceps D = 13 (F = 14,5): 65 % del MRV KPKN (20) pero 72–93 % del MRV RP-24 (14–18). Isquios D = 6: 30 % vs 43–75 %. Pectoral D = 7: «bajo MEV» con KPKN (8), «MEV–MAV» con RP-24 |
| M2 | **e1RM por ejercicio y tendencia** | e1RM = híbrido(w, reps + (10 − RPE)), solo si reps + RIR ≤ 10; referencia = primera serie de trabajo; Δ = e1RM hoy / mejor e1RM de las 3 exposiciones previas − 1; «caída» si Δ ≤ −2,5 %; «regresión» si hay caída en **2 exposiciones seguidas** (1 caída = aviso) | −2,5 % ≈ salto mínimo de carga y ruido típico [INFERIDO]; calibrar con la DE propia (cambio mínimo ≈ 0,2·DE) | Banca 96,2 → 99,3 → 100,7 → **97,4 (Δ = −3,3 %)**; sentadilla 126,0 → 129,0 → 129,8 → 127,7 (−1,6 %). Banca: aviso (1.ª caída) |
| M3 | **Deriva de RPE a carga constante** | ΔRPE = RPE hoy − mediana del RPE de las 3 últimas exposiciones con la misma carga (±2,5 %) y las mismas reps (±1) | ≥ +1,0 «peor que lo normal»; el error de RPE es ≈ 1 rep [R36,R37] | Banca 82,5×6: de @8,5 a @9,5 = **+1,0** |
| M4 | **Caída de fatiga intrasesión** (RTS «fatigue percent») | FP = 1 − e1RM(última serie)/e1RM(primera), mismo ejercicio | RTS: 3 % baja, 5 % media, ≥ 7 % alta [R21,R22] | Banca −7,6 % (híbrido) / −7,4 % (tabla RTS): alta; sentadilla −6,1 %: media-alta |
| M5 | **Carga sRPE, monotonía y tensión** | UA = RPE de sesión × minutos; monotonía = media/DE diaria (DE muestral); tensión = total × monotonía; aguda:crónica con 4 semanas | Sin umbrales validados en fuerza; mostrar tendencia | 525+640+487,5+637,5 = 2 290 UA; monotonía 1,05; tensión 2 409; aguda:crónica 1,11 (previas 1 900/2 050/2 200/2 100) |
| M6 | **Índice de bienestar** (§7.3) | I = 25·(media(E, 6−A, 6−S, M) − 1) → 0–100; z = (I − μ28)/σ28 | z ≤ −1 «bajo lo normal», ≤ −2 «muy bajo»; absoluto I < 50 (= 3,0 de 5; paralelo al «<3» de JuggernautAI si su escala fuera 1–5, no publicada) [INFERIDO] | I = 43,8; base 63,2 ± 9,1 → **z = −2,14** |
| M7 | **Calibración del RIR** | b = reps reales al fallo − (reps hechas + RIR declarado) en series al fallo/AMRAP; RIR_corr = RIR + b̂(RIR) | Prior: b ≈ 0,46 a RIR 1 hasta 1,2 a RIR 5 (interpolación lineal de dos puntos) [INFERIDO sobre R38] | RIR declarado 3 → real ≈ 3,8 |
| M8 | **Deload reactivo multi-señal** | señales: s1 ≥2 ejercicios con regresión (M2); s2 ΔRPE ≥ +1 en ≥2; s3 z ≤ −1 en ≥3 de los últimos 5; s4 músculo sobre MRV 2 semanas; s5 aguda:crónica > 1,3 dos semanas | 2 señales: recortar 20–30 % de series, mantener cargas; ≥3: sugerir descarga (≈ 50 % series, −10 % carga, RPE ≤ 7), con histéresis y confirmación | Ejemplo: s2 + s3 + banca en regresión → amarillo |
| M9 | **Balance central/periférico** (RTS-like) | P = F semanal; C = reps con ≥ 85 % del e1RM (o RPE ≥ 9 con ≤ 5 reps); razón C/P frente a base propia | Heurística; ±1 DE de la base | Opcional |

**Lectura integrada del ejemplo:** M6 (z = −2,1), M3 (+1,0), M2 (−3,3 % en banca) y M4 (alta) señalan fatiga; sin embargo, el volumen local de pectoral es **bajo** (7 series), de modo que la causa probable es **sistémica** (atribuir a Energía, no a Músculos). Acción: «recorta ~20 % de series de accesorios y mantén las cargas principales una semana», no descarga completa. Los cuádriceps (13 series, hasta 93 % del MRV de RP-24) quedan en «vigilar».

Estas métricas **alimentan además al aprendiz del plan** (PLAN 3.4: observaciones de rendimiento σ≈12 y ΔRPE σ≈10) y coinciden con S4, S5, S6 y S14 del PLAN.

### 7.3 Entrada subjetiva (readiness de 4 preguntas) y salidas
| Ítem | Pregunta | Escala 1→5 | Polaridad | Campo existente |
|---|---|---|---|---|
| E | «¿Con cuánta energía llegas hoy?» | agotado · bajo · normal · bien · a tope | + | ancla de Energía |
| A | «¿Cómo están tus músculos (agujetas, rigidez)?» | nada · leve · moderada · alta · dolorosa | − | `doms` |
| S | «¿Cuánto estrés o carga mental llevas?» | ninguno … muchísimo | − | `stressLevel` |
| M | «¿Qué ganas tienes de entrenar?» | ninguna … muchas | + | `motivation` |
| (opc.) | «¿Alguna articulación o tendón molesta?» sí/no + zona | — | no puntúa | `preWorkoutDiscomforts` |

Es un índice tipo Hooper [R51] **sin sueño** (decisión cerrada); el modelo de datos ya tiene tres de los cuatro campos [OBSERVADO·KPKN]. **Salida** (siempre «solo reduce», tope 30 %, como el ajuste actual):
- Verde (z > −1 y sin señales): sin cambio.
- Amarillo (−2 < z ≤ −1 o 1 señal de rendimiento): «−1 serie en accesorios; mantén cargas; RPE objetivo −0,5».
- Rojo (z ≤ −2, I < 50 o ≥ 2 señales): «−20 a −30 % de series o −5 a −10 % de carga».
- Agujetas ≥ 4 en un músculo que toca hoy: sugerir orden o ejercicio alternativo (ya existe el readiness por ejercicio). Dolor articular: sustitución (el Stronger By Science lo trata como señal que **no** es normal [R46]).

### 7.4 «Fatiga del SNC» y el anillo Energía
- **Definición y medición** [LITERATURA]: la fatiga central es una reducción de la activación voluntaria (Gandevia 2001, no reabierto en esta sesión); se mide con estimulación (twitch, TMS) en laboratorio [R53]. Es un fenómeno real, pero: la mayoría de la evidencia es de contracciones isométricas de una sola extremidad; en contracciones máximas breves explica ≈ ¼ de la pérdida de fuerza y en submáximas largas ½–⅔; la validez de la interpolación en músculo fatigado es objeto de debate (De Haan–Taylor 2009; revisión de 2021, solo resumen) [R53]. Tras ejercicio breve e intenso, la activación voluntaria se recupera en minutos (resumen de Carroll 2017) [R53].
- **Lo que dura días** [LITERATURA]: la función neuromuscular (CMJ, velocidad, fuerza) tarda 24–48 h tras series sin fallo y más tras fallo; se atribuye a acoplamiento excitación-contracción y proteínas contráctiles (periférico) [R54]. No hay evidencia que separe una «fatiga del SNC» de días tras sesiones de fuerza [INFERIDO].
- **Marcadores prácticos:** bienestar subjetivo (mejor sensibilidad a carga: Saw 2016) → **usar** (ya existe); ΔRPE y regresión de e1RM → **usar con cautela** [INFERIDO, sin validación directa]; CMJ (la altura media es más sensible) y velocidad de barra → fuera de AUGE hoy (B4); HRV → B2; activación voluntaria, test de golpeteo/reacción/ortostática de TRAC → **no adoptar** (laboratorio / sin validación pública).
- **Implicación de diseño:** reencuadrar Energía como **fatiga sistémica percibida y de rendimiento**, no como «estado del SNC»; entradas de más valor: bienestar, ΔRPE/e1RM, series al fallo y de alta intensidad (AUGE ya las penaliza vía `nearRm` y RPE > 10), sRPE. Dos escalas (aguda de 1–3 días y acumulada de semanas) son coherentes con la literatura de recuperación aguda frente a la de sobrecarga acumulada [INFERIDO] (S10 del plan). No hay datos para validar τ = 36 h; lo hará el aprendiz.

### 7.5 Matrices MEV/MAV/MRV como capa de diagnóstico
**Algoritmo** (sin programar entrenamientos): para cada músculo con series en 28 d, calcular D y F en 7 d; tabla de referencia **versionada** `{id, año, fuente}` con rangos; zona y texto «Cuádriceps: 13 series duras/semana (alto: 72–93 % del MRV según RP-2024)». Tope por sesión: señalar >12 series directas de un músculo en una sesión [R28]. MRV personal (opcional): cuando regresión de e1RM y exceso de volumen coincidan, estrechar el prior (no hay sliders: aprendizaje silencioso con histéresis).

**Riesgos de calibración**
1. **Heurísticos e inestables:** RP revisó MEV a ~la mitad en 2023–24 [R31]; los rangos se publican para intermedios y dependen de sueño/estrés/frecuencia. → mostrar **rango**, fuente y confianza (baja/media), no un porcentaje exacto.
2. **Criterio de conteo:** RP cuenta solo series directas; con fraccionales, el tríceps pasa de 6 (33 % del MRV KPKN) a 11 (61 %) y los glúteos de 6 (38 %) a 12,5 (78 %) en el ejemplo. → comparar **D con RP** y **F con Pelland**; nunca mezclar.
3. **Calidad del RIR:** subestimación ≈ 1 rep infla las «series duras» de RIR 4 declarado (M7).
4. **Objetivo:** la fuerza apenas depende de la proximidad al fallo y se satura en pocas series [R44,R45]; los landmarks de hipertrofia no deben penalizar a quien entrena fuerza.
5. **Lectura como prescripción:** AUGE es un monitor; el «+1 serie» de RP sería prescribir (decisión §8).

### 7.6 Qué evitar (ciencia débil)
- Presentar la «fatiga del SNC» como medida o dar tiempos de recuperación del SNC como hechos.
- Landmarks como números exactos o MRV como límite duro; usar solo una versión sin etiquetarla.
- Tonelaje como único proxy de estímulo o fatiga entre rangos de repeticiones; e1RM de series con reps + RIR > 10.
- Subir series por «bombeo» o por agujetas (proxy de estímulo débil); decirle al usuario que las agujetas indican crecimiento [de memoria: Schoenfeld & Contreras 2013].
- Descargas calendarizadas fijas (Coleman 2024); ACWR como predictor de lesión (B3).
- Clonar el Stress Index o la «exertion load» sin fórmula verificable; copiar tests de TRAC.

### 7.7 Catálogo ordenado (valor/esfuerzo)
Ciencia: **A** sólida y replicada; **B** controlada o validada parcialmente; **C** heurística o extrapolación.

| Orden | Métrica | Fórmula (resumen) | Datos KPKN | Ciencia | Valor | Esfuerzo |
|---|---|---|---|---|---|---|
| 1 | Series duras por músculo vs zona (M1) | D, F con landmarks versionados | series, RPE, roles (existe `calculateCompletedWeeklyMuscleVolume`) | B (volumen) / C (landmarks) | Alto | S |
| 2 | Tendencia e1RM y regresión (M2) | híbrido(w, reps+RIR); Δ ≤ −2,5 % vs mejor de 3, dos veces | peso, reps, RPE | B | Alto | S |
| 3 | Índice de bienestar con z (M6) | 4 ítems, z 28 d | `doms`, `stressLevel`, `motivation` + energía | B | Alto | S |
| 4 | Caída de fatiga intrasesión (M4) | 1 − e1RM_últ/e1RM_prim | series de una sesión | C | Medio-alto | S |
| 5 | Calibración de RIR (M7) | sesgo en series al fallo | `IntensityMode.FAILURE`/AMRAP (calibrador AMRAP de la ola screens-v2) | B | Alto (mejora a todas) | M |
| 6 | Deriva de RPE (M3) | RPE hoy − mediana | historial por ejercicio | C | Medio | S |
| 7 | Deload reactivo multi-señal (M8) | ≥2 señales | M2–M6 + ACWR | C | Alto | M |
| 8 | sRPE, monotonía, tensión (M5) | RPE×min | duración del log; RPE de sesión (nuevo) | B/C | Medio | S |
| 9 | Matriz S×P de RP (§3.4) | 16 celdas → acción | `doms` por músculo + M2 | C | Medio (didáctico) | S |
| 10 | Series ponderadas por RIR | w = 1 − 0,05·RIR (aprox. lineal de Robinson) o exertion load | series | C | Medio | M |
| 11 | Balance central/periférico (M9) | C/P | e1RM + reps | C | Bajo-medio | S |

---

## 8. Preguntas abiertas y decisiones para el usuario
1. **Mostrar zonas MEV–MRV** y con qué tabla por defecto: RP-2017 (como hoy en KPKN) o RP-2023–24 (más conservadora), o ambas con rango. Pro: honestidad sobre la incertidumbre; contra: dos números confunden.
2. **¿Puede AUGE sugerir subir series** (+1 / +2–3 de RP) o se mantiene «solo reduce»? Subir lo convierte en entrenador.
3. **Check-in previo de 4 ítems:** ¿cuenta como «cuestionario de 24 h» (decisión cerrada)? Alternativa: reutilizar `DailyWellbeingLog` sin pantalla nueva. Sueño queda fuera.
4. **RPE de sesión (sRPE):** ¿pedirlo al cerrar o derivarlo de las series?
5. **Reencuadrar «SNC»** en textos y en el prior de τ como «fatiga sistémica» (la evidencia no sostiene «SNC»).
6. **Calibración del RIR:** ¿pedir una serie al fallo cada N semanas (UX) o inferir del e1RM?
7. **MRV personal aprendido** (sin sliders): ¿permitido y con qué histéresis?
8. **Músculo prioritario (\*P):** ¿existe en perfil/wizard para usar MAV\*P/MRV\*P?
9. Referencia (cerrada): JuggernautAI precarga el cuestionario desde Health Connect [R7]; no se propone nada.
10. Contradicción detectada en el repositorio, solo para el orquestador: `DailyWellbeingLog` conserva `sleepQuality` y `sleepHours` pese a la decisión de no registrar sueño [OBSERVADO·KPKN].

---

## 9. Apéndice de evidencia y fuentes
El apéndice completo (estado de lectura de cada fuente, tablas de datos, lecturas del código de KPKN, salida íntegra del ejemplo y limitaciones) está en `B1-fuerza-evidencia.md` (secciones E1–E6). Las cifras de estudios abiertos vienen de un lector web que resume el texto; las marcadas «resumen» provienen solo del buscador.

**Proveedores y práctica**
- [R1] Juggernaut Training Systems, «The JuggernautAI App Has Arrived», 2021 — https://www.jtsstrength.com/the-juggernautai-app-has-arrived/
- [R2] C. W. Smith, «How JuggernautAI Works», 2021 — https://www.jtsstrength.com/how-juggernautai-works/
- [R3] JTS Help, «How JuggernautAI is individualized to you» — https://help.jtsstrength.com/en/articles/3-how-juggernautai-is-individualized-to-you
- [R4] JTS Help, «All about RPE and RIR» — https://help.jtsstrength.com/en/articles/2-all-about-rpe-and-rir
- [R5] JuggernautAI, «v2.5 is out now» — https://www.juggernautai.app/blog/juggernautai-25
- [R6] JuggernautAI, «V3.0 Is Here» — https://www.juggernautai.app/blog/juggernautai-v3-0-is-here
- [R7] JuggernautAI, «v2.0 is out now» y «5 Tips…» — https://www.juggernautai.app/blog/juggernautai-v2-0-is-out-now · https://www.juggernautai.app/blog/5-tips-to-get-the-most-out-of-the-juggernautai-app
- [R8] App Store, JuggernautAI (Juggernaut Apps LLC) — https://apps.apple.com/us/app/juggernautai/id1515756471
- [R9] Lift Vault, programas BLEST (perfil de G. Blevins) — https://liftvault.com/programs/powerlifting/blest-beginner-intermediate-powerlifting-programs/
- [R10] Evolve Training App, web y press kit — https://www.evolvetrainingapp.com/ · https://www.evolvetrainingapp.com/press-kit
- [R11] Evolve, «How the Evolve App Creates Your Personalized Training Program» — https://www.evolvetrainingapp.com/post/how-does-evolve-decide-your-training
- [R12] App Store, Evolve Training App (Skynet Coaching Inc.) — https://apps.apple.com/us/app/evolve-training-app/id1631174176
- [R13] Exodus Strength, hilo «Evolve AI», nov-2022 — https://www.exodus-strength.com/forum/viewtopic.php?t=4506
- [R14] Dr. Muscle, «Evolve AI Workout App—Independent Review» (competidor) — https://dr-muscle.com/evolve-ai-app-review/
- [R15] USPTO.report, marca SKYNET (G. J. Blevins), resumen — https://uspto.report/TM/87808092
- [R16] RTS, «Powerlifting: Emerging Strategies, an Interview with Mike Tuchscherer» — https://store.reactivetrainingsystems.com/blogs/default-blog-1/powerlifting-emerging-strategies-an-interview-with-mike-tuchscherer
- [R17] RTS Lab — https://rtslab.reactivetrainingsystems.com/
- [R18] RTS, «How I added 129lbs to my total in 2 blocks» — https://store.reactivetrainingsystems.com/blogs/default-blog-1/how-i-added-129lbs-to-my-total-in-2-blocks
- [R19] RTS Classroom, «Programming with Emerging Strategies» — https://store.reactivetrainingsystems.com/products/classroom-emerging-strategies-series-bundle-1
- [R20] RTS, «Beginning RTS» — https://store.reactivetrainingsystems.com/blogs/rts-basics/beginning-rts
- [R21] All Things Gym, «Mike Tuchscherer on Auto-Regulation Fundamentals & Fatigue Percentages» — https://www.allthingsgym.com/mike-tuchscherer-auto-regulation-fundamentals-fatigue-percentages/
- [R22] CriticalBench, «An RTS overview» — https://www.criticalbench.com/RTS-overview.htm
- [R23] fitnessvolt, «Tuchscherer RPE Chart Explained»; Stronger, «RPE chart» — https://fitnessvolt.com/rpe-training/guides/tuchscherer-chart-explained/ · https://www.strongermobileapp.com/blog/rpe-chart
- [R24] RTS, «Understanding Your TRAC Score» — https://store.reactivetrainingsystems.com/blogs/application-articles/understanding-your-trac-score
- [R25] R. Frederick, «Monitoring training stress with exertion load» (STRONGUR; no abierto: certificado caducado; resumen) — http://www.strongur.io/monitoring-training-stress-with-exertion-load/
- [R26] Exodus Strength, hilo sobre HNFM — https://exodus-strength.com/forum/viewtopic.php?t=3409&start=20
- [R27] Renaissance Periodization, «Training Volume Landmarks for Muscle Growth» — https://rpstrength.com/blogs/articles/training-volume-landmarks-muscle-growth
- [R28] RP, guías por músculo 2023–24 (p. ej.) — https://rpstrength.com/blogs/articles/chest-hypertrophy-training-tips · …/quad-hypertrophy-training-tips · …/hamstring-hypertrophy-training-tips · …/glute-hypertrophy-training-tips · …/triceps-hypertrophy-training-tips · …/back-hypertrophy-training-tips (lista completa en la evidencia)
- [R29] RP, «Progressing for Hypertrophy»; «In defense of set increases within the hypertrophy mesocycle» — https://rpstrength.com/blogs/articles/progressing-for-hypertrophy · https://rpstrength.com/blogs/articles/in-defense-of-set-increases-within-the-hypertrophy-mesocycle
- [R30] RP Hypertrophy App (página); Centro de ayuda «Hypertrophy App Progressions» (403; resumen) — https://rpstrength.com/pages/hypertrophy-app · https://help.rpstrength.com/hc/en-us/articles/32600173777815-Hypertrophy-App-Progressions
- [R31] Lift Vault (2017 vs 2024); fitguide.blog; drmikeisraetel.com — https://liftvault.com/programs/bodybuilding/mike-israetel-5-week-hypertrophy-workout-routine-spreadsheet/ · https://fitguide.blog/training/volume/ · https://drmikeisraetel.com/dr-mike-israetel-mv-mev-mav-mrv-explained/

**Literatura científica**
- [R32] Zourdos et al., 2016, «Novel Resistance Training-Specific RPE Scale Measuring Repetitions in Reserve», J Strength Cond Res 30:267 — https://openrepository.aut.ac.nz/items/efef3b25-6701-4fb5-bb82-55fcd2a26027/full
- [R33] Helms et al., 2016, «Application of the RIR-Based RPE Scale for Resistance Training», Strength Cond J 38(4):42 — https://openrepository.aut.ac.nz/items/92cefeac-7c32-44f1-bfbc-f48f4a5dd8cf/full
- [R34] Helms et al., 2018, «RPE vs. Percentage 1RM Loading in Periodized Programs Matched for Sets and Repetitions», Front Physiol 9:247 — https://pmc.ncbi.nlm.nih.gov/articles/PMC5877330
- [R35] Hackett et al., 2012, «A novel scale to assess resistance-exercise effort», J Sports Sci 30:1405 (resumen)
- [R36] Steele et al., 2017, «Ability to predict repetitions to momentary failure is not perfectly accurate…», PeerJ 5:e4105 — https://pmc.ncbi.nlm.nih.gov/articles/PMC5712461/
- [R37] Halperin et al., 2022, «Accuracy in Predicting Repetitions to Task Failure in Resistance Exercise», Sports Med 52:377 — https://pubmed.ncbi.nlm.nih.gov/34542869/ (resumen)
- [R38] Stronger By Science, «Reps in Reserve» — https://www.strongerbyscience.com/reps-in-reserve
- [R39] Mann et al., 2010, «The effect of autoregulatory progressive resistance exercise vs. linear periodization…», J Strength Cond Res 24:1718 (resumen)
- [R40] Graham y Cleather, 2021, «Autoregulation by repetitions in reserve…», J Strength Cond Res 35:2451 — https://research.stmarys.ac.uk/3067/3/Graham-Cleather-Autoregulation-Repetitions-in-Reserve.pdf (resumen)
- [R41] Greig et al., 2020, «Autoregulation in Resistance Training: Addressing the Inconsistencies», Sports Med 50:1873 — https://pmc.ncbi.nlm.nih.gov/articles/PMC7575491/ (resumen)
- [R42] Hickmott et al., 2022, «The Effect of Load and Volume Autoregulation on Muscular Strength and Hypertrophy», Sports Med Open 8:9 — https://pmc.ncbi.nlm.nih.gov/articles/PMC8762534
- [R43] Refalo et al., 2023, «Influence of Resistance Training Proximity-to-Failure on Skeletal Muscle Hypertrophy», Sports Med 53:649 — https://pmc.ncbi.nlm.nih.gov/articles/PMC9935748/
- [R44] Robinson et al., 2024, «Exploring the Dose-Response Relationship Between Estimated Resistance Training Proximity to Failure, Strength Gain, and Muscle Hypertrophy», Sports Med 54:2209 — https://pure.solent.ac.uk/en/publications/exploring-the-dose-response-relationship-between-estimated-resist/
- [R45] Pelland et al., 2025, «The Resistance Training Dose Response…», Sports Med, doi 10.1007/s40279-025-02344-w (Springer bloqueado; cifras vía R46); Remmert et al., 2025, «Is There Too Much of a Good Thing?» (preprint) — https://sportrxiv.org/index.php/server/preprint/view/537
- [R46] Stronger By Science, «More Training, More Gaining» — https://www.strongerbyscience.com/volume/
- [R47] Coleman et al., 2024, «Gaining more from doing less? The effects of a one-week deload…», PeerJ — https://pmc.ncbi.nlm.nih.gov/articles/PMC10809978/
- [R48] Nuzzo et al., 2024, «Maximal Number of Repetitions at Percentages of the One Repetition Maximum», Sports Med 54:303 — https://pmc.ncbi.nlm.nih.gov/articles/PMC10933212
- [R49] Preprint arXiv 2603.17495, «A Weight-Dependent 1RM Prediction Equation…», 2026 — https://arxiv.org/abs/2603.17495
- [R50] Haddad et al., 2017, «Session-RPE method for training load monitoring», Front Neurosci 11:612 — https://www.frontiersin.org/articles/10.3389/fnins.2017.00612/full ; Foster et al., 2001; Day et al., 2004; McGuigan y Foster, 2004; Egan et al., 2006 (https://jssm.org/jssm-05-289.xml-Fulltext)
- [R51] Hooper et al., 1995, MSSE 27:106 (resumen); Saw et al., 2016, «Monitoring the athlete training response: subjective self-reported measures trump commonly used objective measures», Br J Sports Med 50:281 — https://bjsm.bmj.com/content/50/5/281
- [R52] Claudino et al., 2017, «The countermovement jump to monitor neuromuscular status: A meta-analysis», J Sci Med Sport 20:397 — https://ro.ecu.edu.au/ecuworkspost2013/2538 (resumen)
- [R53] Gandevia, 2001, «Spinal and supraspinal factors in human muscle fatigue», Physiol Rev 81:1725 (no abierto, 403); Taylor et al., 2016, «Neural contributions to muscle fatigue», Med Sci Sports Exerc 48:2294 — https://pmc.ncbi.nlm.nih.gov/articles/PMC5033663/ ; Carroll, Taylor y Gandevia, 2017, J Appl Physiol 122:1068 (resumen); revisión de 2021 sobre la cuantificación de la fatiga central (resumen)
- [R54] Morán-Navarro et al., 2017, «Time course of recovery following resistance training leading or not to failure», Eur J Appl Physiol 117:2387 (resumen); Raastad y Hallén, 2000, Eur J Appl Physiol 82:206 (resumen); tesis, Univ. de Jyväskylä, 2017 — https://jyx.jyu.fi/handle/123456789/53644
- [R55] Schoenfeld, Ogborn y Krieger, 2017, «Dose-response relationship between weekly resistance training volume and increases in muscle mass», J Sports Sci 35:1073 (de memoria; no reabierto)
