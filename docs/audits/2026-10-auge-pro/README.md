# AUGE Pro · síntesis de la investigación (21 de octubre de 2026)

**Qué se pidió.** Que AUGE se nutra de las capacidades profesionales de las mejores aplicaciones de autorregulación, HRV y gestión de carga, manteniendo los anillos como capa visual y amable, y que se analicen mediante ingeniería inversa los APK aportados (EVOLVE, Kubios HRV y Athlytic).
**Qué se hizo.** Siete agentes (Sonnet 5.5 Max y Opus 5.5 Max): tres análisis estáticos de APK con **REA** (`rea-agents`, con JADX y Ghidra) y cuatro investigaciones sobre la literatura y el código de KPKN (fuerza, HRV, carga y velocidad de barra). Los informes completos están en `docs/audits/2026-10-auge-pro/informes/` y cada uno trae su propio resumen ejecutivo y sus fuentes.

## 1. Veredicto en diez líneas
1. **Los APK dieron poco, y eso ya es un resultado.** Ninguno de los tres contiene la lógica profesional que buscabas: EVOLVE es un rastreador de hábitos gamificado (no es JuggernautAI); Athlytic de Android es una app de fútbol (la de Recuperación/Esfuerzo es solo de iOS); Kubios calcula todo en su servidor. Lo valioso está en la **literatura** y en un par de patrones de Kubios.
2. **El informe previo acierta en la dirección y falla en los detalles** (sección 3): varias cifras, atribuciones y fórmulas no se sostienen.
3. **AUGE ya tiene la mitad del camino:** drenaje por serie, recuperación exponencial, ACWR con histéresis, readiness por ejercicio y un aprendiz de τ. Le falta la capa de **diagnóstico profesional** sobre su propio historial.
4. **Lo más rentable no necesita datos nuevos:** series duras por músculo frente a zonas de volumen, tendencia del 1RM estimado, índice de bienestar personal, caída de fatiga dentro de la sesión, calibración del RIR, carga crónica/aguda (CTL/ATL) y mapa de calor. Todo sale de lo que la app ya registra.
5. **La HRV es útil pero modesta:** solo debe tocar Energía, como modulador acotado, y primero en modo informativo. La cámara del teléfono no sirve para mover números.
6. **La «ventaja injusta» del vídeo es realista solo a medias:** la pérdida de velocidad dentro de la serie y un RIR objetivo de ±1–2 repeticiones, sí; «−8 % en el calentamiento ⇒ SNC fatigado», no.
7. **El «SNC» como entidad separada no está demostrado** tras sesiones de fuerza. Conviene presentar Energía como *fatiga sistémica percibida y de rendimiento*.
8. **El ACWR es descriptivo, no predictivo de lesión** (críticas serias, 2019–2021). AUGE debe dejar de decir «Zona de Riesgo».
9. **Banister no es identificable con datos de aplicación:** si se adopta CTL/ATL/TSB, con constantes fijas, sin aprenderlas por usuario.
10. **Nada de esto retroalimenta a los anillos hasta superar una prueba retrospectiva** con anclas reales y usuarios sintéticos.

## 2. Qué dijo cada APK
| App | Qué es en realidad | Lo aprovechable | Lo que no se pudo ver |
|---|---|---|---|
| **EVOLVE 1.1.1** | Rastreador de hábitos y metas gamificado (FlutterFlow, IA de OpenAI desde el cliente, suscripción RevenueCat). Sin RPE, 1RM, MEV/MRV, HRV ni VBT. | Nada en métricas. Tres ideas de redacción: resumen semanal en bloques, «una cifra, evidencia→impacto, acción aparte», chips de preguntas rápidas. XP y rachas chocan con la lógica de recuperación. | Código Dart compilado (REA solo extrae las cadenas). Hay una clave de API de OpenAI embebida de su desarrollador: no se usó ni se transcribió. |
| **Kubios HRV 1.8.14** | Cliente fino React Native/Hermes de Kubios Oy (no es Morpheus). Mide con banda BLE o sensor óptico Polar; no usa cámara. **Todo el análisis HRV ocurre en Kubios Cloud.** | Readiness 0–100 sobre la distribución *personal* (cortes 25/50/75 %, mínimo 6 mediciones, hasta 90 días); línea base = media ± desviación típica personal; protocolo matinal de 180 s; mapa estado → tipo de entrenamiento. | Fórmulas del servidor (se cubren con la literatura: Tarvainen 2014, Lipponen 2019). Se desensamblaron las 33.546 funciones Hermes con un desensamblador propio. |
| **Athlytic 1.0.8** | «Athlytic – Train Like a Pro»: rastreador gamificado de entrenamiento de fútbol (XP, rachas, rankings, planes). **No** es la app de Recuperación/Esfuerzo de Apple Watch. | Patrones del servidor, sin valores: carga por ítem con cinco demandas y coste de recuperación, bandas mín/objetivo/máx por sesión, reglas duras/blandas. «Work Rate» es un contraejemplo: premia hacer más sin mirar la recuperación. | Servidor y valores del motor de carga; la app de iOS (no hay APK). |

## 3. Verificación del informe previo
| Afirmación | Veredicto |
|---|---|
| «Evolve AI / JuggernautAI» | **Corregida.** «Evolve AI» existe (Evolve Training App), pero no es JuggernautAI ni el APK EVOLVE. JuggernautAI publica MEV/MRV individualizados (sin cifras) y recorta series accesorias si el readiness es bajo. |
| «Stress Index de RTS = repeticiones × %1RM × RPE» | **No verificable.** El Stress Index existe (RPE, intensidad, esfuerzo; central/periférico), pero esa fórmula no consta. |
| HRV4Training: base de 60 días y banda ±0,75 DE | **Corregida.** Los 60 días son el rango normal; la base es de 7 días. ±0,75 DE fue solo una versión Pro (2019) y no es el cambio mínimo significativo de Hopkins. |
| «Kubios HRV / Morpheus (Joel Jamieson)» | **Corregida.** Productos distintos; quien recalcula las zonas del día es Morpheus. |
| «Strain 0–21» de Athlytic / dispersión con «túnel óptimo» | **Corregida / no verificable.** El 0–21 es de WHOOP; en Athlytic iOS el esfuerzo es 0–10. La dispersión de strain por recuperación de WHOOP existe; el «túnel diagonal» no se pudo verificar. |
| CTL 42 d, ATL 7 d, TSB = CTL − ATL | **Confirmada** (con los valores de ayer). Pero Banister no es identificable por usuario. |
| ACWR > 1,3–1,5 ⇒ riesgo de lesión | **Corregida.** Descriptivo; las críticas y el metaanálisis de 2026 (g = 0,35, I² = 96 %) impiden usarlo como predictor. |
| HRV ajusta el umbral de pérdida de velocidad del VBT | **Sin evidencia.** |
| «−8 % de velocidad en el calentamiento ⇒ SNC fatigado» | **No se sostiene.** Sin umbral publicado, fatiga sobre todo periférica y −8 % cae dentro del ruido (cambio mínimo detectable del 10–18 % con teléfono). |
| Calcular la fatiga neuromuscular objetiva con VBT y ROM | **Parcialmente realista:** pérdida de velocidad y RIR objetivo, sí; «SNC», no. Hoy AUGE no consume nada del vídeo. |

## 4. Arquitectura propuesta: tres capas
**Capa 1 — Anillos (sin cambios de concepto).** Músculos, Energía, Columna: visuales, amigables, con «sin datos» honesto (paquetes 1.9v2/1.10v2 en curso).
**Capa 2 — Panel «Pro» (solo lectura, dentro del detalle de AUGE, sin controles de parámetros).** Diagnóstico sobre el historial:

| # | Métrica | Qué muestra | Datos nuevos | Ciencia | Esfuerzo |
|---|---|---|---|---|---|
| P1 | **Carga aguda/crónica por canal** (CTL/ATL con τ 7 d y 28 d; Energía 10 d) y **razón de tu semana frente a la habitual** (desacoplada) | «Esta semana llevas 18 % más que tu semana habitual» (nunca «riesgo de lesión») | ninguno | B / C | S–M |
| P2 | **Series duras por músculo y semana** frente a zonas MEV–MRV versionadas (directas y fraccionales) | «Cuádriceps: 13 series duras/semana (72–93 % del MRV según la fuente)» | ninguno | B / C | S |
| P3 | **Tendencia del 1RM estimado** por ejercicio (aviso con −2,5 % frente al mejor de las 3 previas; regresión si se repite) y **deriva de RPE** a carga constante | «Press banca: −3,3 % esta sesión» | ninguno | B | S |
| P4 | **Índice de bienestar personal** (4 ítems: energía, agujetas, estrés, ganas; sin sueño) con z-score de 28 días | Semáforo propio de la persona | ninguno (campos ya existentes) | B | S |
| P5 | **Caída de fatiga dentro de la sesión** (el «fatigue percent» de RTS) | 3/5/7 % = baja/media/alta | ninguno | C | S |
| P6 | **Calibración personal del RIR** con series al fallo/AMRAP | «Tu RIR declarado 3 equivale a ~3,8» | ninguno | B | M |
| P7 | **Deload reactivo multi-señal** (≥ 2 señales: regresión de e1RM, deriva de RPE, bienestar bajo, volumen sobre MRV, razón alta) | Sustituye al detector de sobrecarga actual (2 de 5 factores muertos) | ninguno | C | M |
| P8 | **Mapa de calor corporal** (estado, carga relativa, sin trabajar) | Silueta frente/espalda con las bandas de `RecoveryBands` y siempre el número | ninguno | — | M |
| P9 | Monotonía/tensión (opcional, degradada) | «Variedad de la semana» sin alerta | ninguno | B / C | S |

**Capa 3 — Entradas opcionales nuevas (por fases, cada una con su decisión).**
- **HRV** (solo Energía): fase 1, *solo informativa* (ln rMSSD matinal de entrada manual, z de 7 días frente a base de 60; mínimo 14 valores; confianza y refuerzo de la reducción ya existente); fase 2, tras 8 semanas en sombra, ajuste acotado (−10/+2 puntos) como pseudo-sesión que decae con la τ de Energía y subordinada al ancla manual. Con banda BLE el peso es 1; con cámara, 0,5 e informativa.
- **Vídeo** (V0/V1/V2): V0 interno (fallo observado y RPE objetivo solo si falta el declarado); V1 con m/s calibrados (RIR por velocidad individual, corrección del RPE acotada a ±1,5 y observación para el aprendiz); V2 readiness diaria frente al perfil carga–velocidad personal, que solo sugiere bajar.

## 5. Cómo encaja con la ola «Rings v4» (hoja de ruta)
Los paquetes puros no esperan decisiones de producto y pueden construirse en cuanto el motor esté unificado:
1. **Ahora, en curso:** 2.2b1 y 2.2b2 (editor = cierre = Home y corrección del bug F-60), luego 2.3 (instantánea única de AUGE), 2.4, 2.5, 2.6 y la fase 3 (aprendiz con filtro de Kalman). Las métricas de AUGE Pro consumen la carga por sesión y canal que deja 2.2/2.3.
2. **AUGE Pro-1 (módulo puro `domain/auge/load` y `domain/auge/pro`):** EWMA/PMC/razón desacoplada (P1), P2, P3, P5, P6, P9; pruebas doradas con los números de los informes y usuarios sintéticos. Sin interfaz.
3. **AUGE Pro-2:** índice de bienestar (P4), deload reactivo (P7) y su integración como observaciones del aprendiz (rendimiento σ≈12, ΔRPE σ≈10, ya previstas en el plan 3.4).
4. **AUGE Pro-3:** panel «Pro» y mapa de calor (P8) con las decisiones de abajo, más textos descriptivos en lugar de «riesgo».
5. **AUGE Pro-4:** HRV manual (informativa) y observaciones de vídeo V0.
6. **Más tarde, con backtest:** S10 (Energía de dos escalas), HRV fase 2, vídeo V1/V2.

## 6. Decisiones que necesito de ti
Recomendado entre paréntesis; mientras no respondas, avanzo solo con lo que no depende de ellas (los módulos puros).
1. **Panel «Pro»:** ¿solo lectura dentro del detalle de AUGE y sin controles de parámetros? (sí).
2. **Zonas MEV–MRV:** mostrarlas como *rango con fuente y versión* (RP-2017 frente a RP-2023–24 discrepan mucho) y como diagnóstico, no como prescripción (sí).
3. **¿Puede AUGE sugerir subir series?** (no; sigue «solo reduce»).
4. **Índice de bienestar de 4 ítems:** reutilizar los campos que ya se capturan antes de entrenar, sin pantalla nueva ni cuestionario de 24 h (sí). El sueño sigue fuera. *Aviso:* el modelo `DailyWellbeingLog` conserva `sleepQuality` y `sleepHours` pese a esa decisión; conviene limpiarlos.
5. **Terminología:** presentar Energía como *fatiga sistémica* en los textos, sin renombrar el anillo (sí).
6. **Lenguaje de riesgo:** sustituir «Zona de Riesgo/Alto Riesgo» por estados descriptivos (sí).
7. **Factor muscular del ACWR (1,0→1,12):** retirarlo o sustituirlo por la razón desacoplada tras la prueba retrospectiva (dejarlo hasta entonces).
8. **HRV:** ¿reabrir la captura? Solo entrada manual de ln rMSSD y modo informativo (sí). Banda BLE y Health Connect quedan fuera salvo que tú los reabras.
9. **Vídeo en AUGE:** ¿se levanta la regla de la ola VT «no dar RIR por pérdida de velocidad» solo para V1? (sí, y V0 interno). ¿La evidencia del vídeo cuenta solo con la serie aceptada por el usuario? (sí).
10. **RPE de sesión (sRPE):** derivarlo de las series, con opción de pedirlo al cerrar (derivar).
11. **S10 (Energía de dos escalas):** posponer hasta tener la prueba retrospectiva (sí).
12. **Más APK:** si quieres que analice HRV4Training, RP Hypertrophy, Bevel o WHOOP, tendrás que descargarlos tú y dejarlos en Descargas; no puedo bajarlos de espejos no confiables. Esperanza limitada: lo propietario suele estar en el servidor. La literatura aportó más que los tres APK.
13. **Validación física:** pedir prestado un transductor lineal de velocidad para validar los m/s del vídeo (opcional).

## 7. Lo que NO se hará
Presentar la «fatiga del SNC» como medida; XP, rachas o rankings; ACWR como predictor de lesión; landmarks de volumen como números exactos o límites duros; clonar fórmulas propietarias (se implementa desde la literatura y de forma independiente); medir HRV con acelerómetro en el pecho; prometer que la cámara sustituye a un electrocardiograma.

## 8. Límites de esta investigación
Se agotó el cupo de búsquedas web de la sesión varias veces (WHOOP devolvió 403): algunas cifras vienen de resúmenes y están marcadas como no reverificadas. REA no abre binarios ARM en este Windows ni trae decompilador de Hermes ni de Dart; no hizo falta porque la lógica relevante está en servidores. No hay estimaciones publicadas de τ específicas de fuerza; eso lo aprenderá el filtro de Kalman o el backtest.
