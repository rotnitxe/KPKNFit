# A3 · Athlytic 1.0.8 (Android, `com.getathlytic.app`) · análisis estático con REA

Etiquetas: **[OBSERVADO]** visto en el APK; **[LITERATURA]** fuente publicada/web; **[INFERIDO]** deducción mía (con razonamiento); **[NO VERIFICABLE]**. Los comandos de REA (R1…R63) y las posiciones L:C del JavaScript original están en `A3-athlytic-evidencia.md` (E1, E4, E8). Todo es análisis estático; no se instaló ni ejecutó nada y no se usó red contra los servidores de la app.

## 0. Resumen ejecutivo

**Identidad (lo primero).** El APK **no es la app de Recuperación/Esfuerzo/Sueño** del informe previo: es «Athlytic – Train Like a Pro», un rastreador **gamificado de entrenamiento de fútbol** (drills, XP, rachas, «Work Rate» semanal, rankings, equipos, planes Bronze/Silver/Gold) de otro desarrollador. La app iOS homónima (MyndArc, Apple Watch) es otro producto; no hallé versión Android de ella. Sin Health Connect, sensores, FC, HRV ni sueño: 0 clases de salud en el DEX (REA R52–R57), ningún permiso de salud, 0 apariciones en el JS.

**Arquitectura.** Capacitor + SPA React de 1,9 MB (legible tras desminificar) + servidor remoto. Solo 3 clases Java propias. El esquema de la BD (22 tablas) viaja en el cliente; XP, niveles, racha, ranking, IA y el motor de carga «training_*» viven en el servidor y **no están en el APK**.

**Valor para AUGE: bajo en métricas, medio como patrones.**
- Work Rate semanal = 0,40·días + 0,35·minutos + 0,25·volumen (tramos, tope 100): **contraejemplo** («más es mejor», sin recuperación).
- Objetivo adaptativo +15 %/+25 % con topes.
- Esquema (sin valores) de carga por ítem, bandas mín/objetivo/máx por sesión, reglas duras/blandas con acción de fallo, y de progresión con confianza, evidencia válida, versión y código de motivo: adaptables al motor de drenaje y al aprendiz de τ.
- XP, rachas, rankings: **descartar**.

**Informe previo.** «Athlytic» como app de Recuperación/Esfuerzo existe solo en iOS; su Esfuerzo es 0–10 (el 0–21 es de WHOOP); la dispersión con «túnel óptimo» no se confirma; «Bevel antes Superset» tiene una sola fuente.

**No pude ver:** el servidor y los valores del motor de carga; el IPA de Athlytic iOS; con REA, el grafo semántico completo y `recover-javascript-sources` (falta Wakaru).

---

## 1. Identidad y arquitectura

### 1.1 Ficha

| Campo | Valor | Evidencia |
|---|---|---|
| Entrada | `Athlytic_1.0.8_APKPure.xapk` → APK base (SHA-256 `9ffbefc7…d203`) | hash propio = hash de REA R1 |
| Versión / SDK | 1.0.8 (código 11); minSdk 24; targetSdk 36 | R1 [OBSERVADO] |
| Tecnología | híbrida **Capacitor** (WebView); SPA Vite/React; 2 DEX (7 822 clases, solo 3 propias); sin `.so` propias (el split ARM solo trae 2 JNI de CameraX) | R5, R51; listado de splits |
| Plugins | `keep-awake` (comunidad), `firebase/analytics` (capawesome), `@capacitor/camera` (+ biblioteca Ionic), propio `AthlyticSubscriptions` (**Google Play Billing 8.0.0**) | `capacitor.plugins.json`; R58–R62, R8, R22 |
| Protección | `com.pairip.*` (9 clases): verificación de licencia de Google Play [INFERIDO por nombres de clase y permiso `CHECK_LICENSE`]; sin clase de VM ni `libpairipcore` en lo que trae el `.xapk` (solo el split ARMv7) | R1, R50 |
| Dominio | producto público (`getathlytic.com`): URL base de la API en nativo, `sitemap.xml`, `robots.txt` (`Disallow: /api/`) | JS 8:108037; sitemap |

### 1.2 Pruebas de identidad

| # | Prueba | Etiqueta |
|---|---|---|
| 1 | `index.html`: título «Athlytic – Train Like a Pro»; descripción «rastreador gamificado de entrenamiento de fútbol: registra drills, sigue tu work rate, compite en rankings, sube de nivel» | [OBSERVADO] |
| 2 | Deporte único «Soccer» («disponible en esta primera prueba»); 4 posiciones; 24 entradas posición-arquetipo (15 vigentes, 13 nombres: Finisher, Maestro, Sentinel, Guardian…; 8 «futuras»; 1 alias); 6 categorías de drill (Control, Dribbling, Passing, Shooting, Agility, Conditioning); grupos de edad U4…Adult | [OBSERVADO] |
| 3 | Aviso de privacidad propio: «app de entrenamiento de fútbol y rendición de cuentas de equipo»; datos: perfil, posición, equipo, registros de drills, rachas, Work Rate, insignias, suscripción | [OBSERVADO] |
| 4 | Esquema de BD: `work_rate_score, xp, level, level_tier, current_streak, drill_logs, drills, teams, badges…` (E7) | [OBSERVADO] |
| 5 | Imágenes promocionales: «Work Rate Score 35 · LEVEL 2/10», drills por posición, «Top 100» de clasificación | [OBSERVADO] |
| 6 | Ausencia de todo dato fisiológico: 0 clases de salud/sensores (R52–R57; la única con «Health» en el nombre es `ClientHealthMetricsStore` de Google Transport, R63), permisos sin Health Connect/sensores/actividad (R1), 0 apariciones de HRV, FC, reposo, readiness, strain, exertion, HealthKit, Health Connect, Bluetooth, wearable en el código de la app. Las 10 apariciones de «recovery» son columnas de planificación de sesión (rol «recuperación» de un drill), «account recovery» y «pasos de recuperación» de portero | [OBSERVADO] |
| 7 | Contraste externo: el espejo APKPure lo describe como app de entrenamiento y desarrollo de futbolistas (v1.0.8); «Athlytic: AI Fitness Coach» (iOS, MyndArc LLC) depende del Apple Watch y no tiene versión Android en lo que encontré | [LITERATURA] |

### 1.3 Dónde vive la lógica

| Capa | Contenido | ¿Visible? |
|---|---|---|
| Cliente (SPA) | 33 rutas; formularios; Work Rate semanal para la serie de 6 semanas y su explicación; objetivo adaptativo; selección diaria de drills; insights de respaldo; derechos por nivel; gráficos | Sí |
| Nativo (Java) | actividad que registra el plugin; plugin de facturación de Play; Capacitor, cámara, KeepAwake, Firebase Analytics, licencia | Sí (R6–R36) |
| Servidor | XP, niveles, racha, bonus por récord, Work Rate «oficial», clasificaciones, equipos, IA «Coach», motor de carga y de progresión, verificación de compras | **No**: solo contrato (rutas, campos) y esquema |

### 1.4 Red, estado y datos

- API REST sobre el dominio del producto (≈40 rutas; E6). Auth: cookie (web) o token `Bearer` guardado en `localStorage` (nativo). Cabeceras propias con origen, zona horaria, posición, vía de desarrollo, atleta activo y **nivel de cuenta declarado por el cliente** (`X-Athlytic-Account-Tier`) [OBSERVADO]; si el servidor lo ignora o lo valida contra la compra no se puede ver.
- Estado: TanStack Query con `staleTime` infinito, sin reintentos ni refetch al enfocar; `localStorage` solo para sesión, onboarding y cachés (E4).
- Analítica: Firebase Analytics nativo, 8 eventos (`drill_logged`, `checkout_started`…) con `user_id` entre los parámetros permitidos [OBSERVADO].
- Zona horaria: Insights, racha y desglose diario usan la del usuario (por defecto `America/Los_Angeles`); las cifras «esta semana / este mes» del perfil y el rating calculado en el cliente usan la hora local del dispositivo.

---

## 2. Entradas de datos

| Entrada | Cómo | Campos / límites | Etiqueta |
|---|---|---|---|
| Registro de drill | **manual**: repeticiones/toques + duración (cronómetro o cuenta atrás de 30 s por defecto, o minutos escritos) + fecha (no futura; se guarda a las 12:00 locales) | `drillId, reps, durationSeconds, completedAt`; puede registrarse para varios atletas a la vez | [OBSERVADO] |
| Perfil y origen | formularios de alta: nombre, posición, grupo de edad, año de nacimiento, género, club, país/región/ciudad (globo 3D), arquetipo elegido | `users`/`athletes` | [OBSERVADO] |
| Cuentas de padres | varios atletas (hijos) por cuenta | `athletes` | [OBSERVADO] |
| Notas de equipo (entrenador) | tipo práctica/partido, fecha, momento, duración 15–480 min, asistencia, **4 valoraciones 1–5** (energía, foco, calidad de ejecución, intensidad), resultado, texto libre | `team_notes` | [OBSERVADO] |
| Foto de perfil | cámara o galería (`CAMERA`; PNG/JPG/WEBP ≤ 10 MB) | — | [OBSERVADO] |
| **Ausentes** | FC, HRV, sueño, pasos, GPS, acelerómetro, Health Connect, wearables, importaciones | el manifiesto no declara nada de esto; el único uso de cámara es la foto | [OBSERVADO] |

---

## 3. Métricas y fórmulas

### 3.1 Work Rate semanal («Rating», 0–100)

| Campo | Valor |
|---|---|
| Definición | Con los registros de la semana: `días` = fechas distintas; `min` = Σ ceil(segundos/60) (30 s por defecto si falta); `vol` = nº de registros. `Rating = round(0,40·S_días + 0,35·S_min + 0,25·S_vol)` |
| Tramos | `S_días`: 1→25, 2→50, 3→70, 4→85, ≥5→100 (0 si ninguno). `S_min`: <15→0; 15–29→20; 30–44→40; 45–59→60; 60–74→80; ≥75→100. `S_vol`: <3→0; 3–5→25; 6–8→50; 9–11→75; ≥12→100 |
| Ventana | semana en curso desde el **lunes**, hora local del dispositivo en el cliente (el servidor: [NO VERIFICABLE]); la serie del perfil usa 6 semanas |
| Normalización | **ninguna**: no depende de edad, nivel, posición ni plan. Valores absolutos |
| Propiedades | no decreciente en días, minutos y volumen; satura con 5 días + 75 min + 12 drills; no penaliza el exceso; no tiene noción de recuperación |
| Pantallas | tarjeta de jugador («Rating»), anillo de Home («of 100», «Weekly progress»), clasificaciones, gráfico de 6 semanas |
| Evidencia | [OBSERVADO] `sEe` 132:7311 y textos de la propia app («Rating = consistency score x 40% + minutes score x 35% + drill volume score x 25%»; «scores are capped at 100»). El servidor devuelve el mismo desglose (`/api/work-rate-breakdown`) y dice usar la misma fórmula para los rankings; su cálculo real es [NO VERIFICABLE] |

La propia empresa aísla esta métrica del XP («entender el esfuerzo de entrenamiento sin mezclarlo con la lógica de XP») [OBSERVADO, texto de la landing].

### 3.2 Objetivo adaptativo por drill (progresión)

| Campo | Valor |
|---|---|
| Definición | `base = objetivo[banda de edad][nivel]` del drill (13 drills con tabla; resto: perfil de la categoría). Si `mejor > 0` y `mejor < base`: `objetivo = mejor × (mejor < mínimoRazonable ? 1,25 : 1,15)`; si no, si `media > 0` y `media < base`: `objetivo = media × 1,15`; si no, `base`. Después se redondea (múltiplos de 5 para toques/pases por minuto; entero ≥ 1 en el resto) y se **recorta** a [`mínimoRazonable`, `máximoRazonable`] del drill |
| Normalización | rendimiento = `reps / max(1, minutos)` si la métrica es «por minuto»; `reps` si no. `mejor` = máximo y `media` = promedio de **todo** el historial de ese drill |
| Bandas | 7 de edad (U6–U8 … U17–U18, Adult; U19–U23 → Adult) × 3 niveles (principiante/intermedio/avanzado; Elite→avanzado). Nivel de habilidad desde el nivel de jugador: Gold/Platinum/Diamond→Avanzado; Silver→Intermedio; resto→Principiante |
| Progreso | `clamp(round(mejor/objetivo·100), 0, 100)`; fuente mostrada: «drill_age_band», «personal_best_scaled», «recent_average_scaled» o «safe_fallback» |
| Evidencia | [OBSERVADO] `j5` 121:173383, `R1e` 121:174930. Los valores de las tablas no se reproducen aquí (son datos de la app); las constantes 1,15/1,25 son heurísticas sin fuente [INFERIDO: no citan literatura] |

### 3.3 Otras reglas cuantitativas visibles

| Regla | Definición y constantes | Etiqueta · ref. |
|---|---|---|
| Serie de rating | 6 cubos lunes–domingo, de hace 5 semanas a la actual; `Rating` de cada cubo; etiqueta = fecha del lunes | [OBSERVADO] `oEe` 132:7712 |
| Consistencia de «Insights» (respaldo cliente) | `round(días activos en 7 días / 7 · 100)` | [OBSERVADO] `v1e` 121:142206 |
| Tendencia semanal | minutos de los últimos 4 días vs los 3 primeros de la ventana de 7: «sube / baja / estable» (ventanas desiguales: sesgo a «sube» [INFERIDO]) | [OBSERVADO] ídem |
| «Más trabajada / más ligera / más mejorada» | categoría con más minutos; con menos minutos entre las activas; mayor aumento de nº de drills esta semana vs la anterior | [OBSERVADO] ídem; `cEe` 132:8215 |
| Etiqueta de intensidad del día | minutos ≥ 30 «alto volumen»; ≥ 12 «moderada»; si no «ligera»; ≥ 3 categorías = «equilibrada» | [OBSERVADO] `dOe` 4257:7513 |
| Progreso de la ruta del día | XP completado / XP total de los drills de la ruta, tope 100 % | [OBSERVADO] `fW` 121:193085 |
| Color del jugador por nivel | niveles ≤2, ≤4, ≤6, ≤8, ≥9 → 5 familias de color; imagen promocional «LEVEL 2/10» ⇒ 10 niveles | [OBSERVADO] `YTe` 132:629; [INFERIDO] lo de 10 niveles |
| Racha | texto de la app: días locales consecutivos con ≥ 1 drill; se reinicia al saltar un día. El cálculo es del servidor | texto [OBSERVADO]; cálculo [NO VERIFICABLE] |

### 3.4 Lo que existe solo en el servidor

- **XP y niveles:** el cliente solo manda `xpEarned = xpReward del drill (10 por defecto)` y `personalBest:false`; el servidor responde con el XP real (mensajes de «récord personal» con bonus). Umbrales de nivel, bonus y rangos: [NO VERIFICABLE]. Un resto sin uso (`xp % 100`, `nivel × 100`) sugiere 100 XP por nivel [INFERIDO, débil].
- **Motor de carga de sesión (esquema [OBSERVADO], valores [NO VERIFICABLE], uso [INFERIDO]):** por ítem, 5 demandas (física, cognitiva, técnica, coordinación, impacto), coste de recuperación, demanda ponderada, carga base, modificadores de edad y de nivel, carga final, marca de alta intensidad y elegibilidad por rol (calentamiento, principal, apoyo, recuperación); por banda de edad × nivel × intensidad pedida, `mín/objetivo/máx` de nº de ítems, de minutos y de **carga**, minutos por rol, máximo de ítems de alta intensidad y consecutivos, «descanso obligatorio tras alta intensidad» y tope de complejidad; reglas de armado con prioridad, **dura/blanda** y **acción de fallo**; pesos de puntuación con mín/máx y ajustables por arquetipo/edad/nivel/posición. La landing lo anuncia como futuro («planificación de carga adaptada a la edad»), de modo que puede estar sin exponer.
- **Progresión por etapas ([OBSERVADO] esquema):** reglas por drill (objetivos «base / reto / maestría», ventana de evaluación, intentos, umbral de calidad, prerrequisitos, siguiente drill), etapas con requisito de desbloqueo/promoción y disparador de regresión; estado por jugador y subhabilidad (etapa demostrada, **confianza**, **nº de evidencias válidas**, `rules_version`); observaciones con valor, calidad, `valid` y **`reason_code`**.
- **IA «Coach» (Gold):** `POST /api/coach/query` con pregunta (≤1 000 car.), última respuesta y plan actual; devuelve respuesta, plan de 3 días (categoría y propósito por día), próximo objetivo, pista técnica, lectura rápida y `safetyFlag` (si está activa, el plan no se actualiza). Modelo y prompts: [NO VERIFICABLE].

**Lo que no hay en ninguna parte del APK:** medias móviles o EWMA, líneas base personales, desviaciones o z-scores, zonas de color por umbral, readiness, recuperación, esfuerzo/strain cardiovascular, ACWR, CTL/ATL. Las únicas «ventanas» son semanas de calendario (lunes a domingo) y los 7/14 días de las vistas.

---

## 4. Algoritmos y reglas de decisión

| Regla | Descripción | Etiqueta |
|---|---|---|
| Ruta del día (8 drills) | parte de los `suggestedDrills` del servidor; si faltan, completa con hasta 3 rondas de «mejor drill aún no usado de cada categoría» en el orden propio de la posición (`(6 − índice)·10` + bono 30 por palabras clave de la posición, 80 para portero) y, si aún falta, por puntuación. **Estable por día**: se guarda por usuario + fecha + posición + vía y se reutiliza si sigue válida | [OBSERVADO] `O5`, `Y1e` |
| Sugerencias por nivel | 4 (basic) u 8 drills (bronze/silver/gold) | [OBSERVADO] `H1e` |
| Derechos por nivel | historial 7/30/90 d/ilimitado; detalle de Work Rate básico→avanzado; insights y análisis del día desde Silver; plan semanal y Coach desde Gold; «predictivo» marcado como «coming soon» (ver E10) | [OBSERVADO] |
| Filtros de clasificación | si el nivel no permite un filtro, se sustituye por el valor por defecto; alcance `regional` si el jugador tiene ciudad/región propias, si no `global`; si el nivel no permite el alcance, `team`. Periodo: solo Gold puede elegir (esta semana / este mes / histórico); el resto ve «esta semana». Puntuación del ranking = Work Rate semanal | [OBSERVADO] `G5`, `cp` |
| Compra | JS pide el SKU al plugin nativo (`startSubscriptionCheckout`), que valida contra 6 SKU admitidos, abre la hoja de Google Play (prueba de 1 mes por plan base `*-free-trial` si la cuenta es elegible) y devuelve `purchaseToken`; el JS lo manda al servidor (`/api/billing/google-play/verify`) y solo da el plan por activo si responde `active`/`trialing` | [OBSERVADO] R12, R16–R18, `Xq` |
| Entradas inválidas | fecha futura bloqueada; duración 0 en el registro manual rechazada | [OBSERVADO] |

### 4.1 Modelo de negocio y de datos (resumen; detalle en E7 y E10)

| Aspecto | Contenido | Etiqueta |
|---|---|---|
| Niveles de cuenta | Bronze **gratis** (en el código «Basic» y «Bronze» se normalizan a Bronze); Silver 7,99 USD/mes o 69 USD/año; Gold 19,99 USD/mes o 159,99 USD/año; prueba de 1 mes; tarifa familiar (7,99 + 4,99 por atleta extra en Silver; 19,99 + 14,99 en Gold) | [OBSERVADO] 132:5786 |
| SKU de Play admitidos | `athlytic_{bronze,silver,gold}_{monthly,annual}` (los de Bronze: resto de un plan de pago antiguo [INFERIDO]); base plans de prueba `{silver,gold}-{monthly,annual}-free-trial` | [OBSERVADO] R35, 4259:10680 |
| Funciones bloqueadas | historial (7/30/90 d/ilimitado), insights y análisis del día (Silver+), plan semanal y Coach IA (Gold), clasificaciones regional/global y filtros (según nivel), detalle del Work Rate | [OBSERVADO] `H1e` |
| Equipos | Free, Starter 9, Growth 19, Club Pro 39 USD/mes (15 jugadores); el plan Pro convierte notas de partido en planes | [OBSERVADO] `Uo` |
| BD remota | PostgreSQL vía Drizzle; 22 tablas: 11 usadas por el cliente (usuarios, atletas, drills, registros, equipos, notas, insignias…) y 11 solo declaradas (motor de carga, progresión, suscripciones) | [OBSERVADO] E7 |
| Verificación de compra | en el servidor (token de Play → `/api/billing/google-play/verify`); el cliente solo acepta `active`/`trialing` | [OBSERVADO]; lógica del servidor [NO VERIFICABLE] |

---

## 5. Conceptos de interfaz y visualización (descritos, sin copiar)

- **Anillo del Work Rate**: SVG (radio 38, trazo 7) con gradiente verde→cian **fijo** (sin zonas de color por umbral), cifra central «x %» sobre «of 100», pie «Weekly progress», flecha ▲▼ verde/roja para el cambio semanal (en Home siempre 0, no se muestra); toca el anillo y abre el desglose (tres sub-puntuaciones con su peso y la final). Es lo más parecido a un anillo de AUGE, pero mide **adherencia**, no recuperación.
- **Tarjeta de jugador**: avatar, chip del plan («Bronze Account»), «Rating», código de arquetipo de 2–3 letras y tres cifras (drills totales, racha, minutos semanales); color según nivel.
- **Tres tarjetas rápidas** (drills de la semana, racha, minutos de hoy) con diálogos: línea de 7 días entrenado/no (eje 0–1) y tendencia diaria de minutos (gráfico de 7 días + lista de 14).
- **Insights** con candado y vista previa borrosa («Insights unlock at Silver»), plan de 3 días, caja de pregunta al Coach.
- **Ruta del día**: línea de tiempo vertical con casillas, barra de XP y confeti al completarla.
- **Clasificación**: filas con medalla oro/plata/bronce, avatar, chip de arquetipo, puntuación grande y delta; el cliente contempla filas marcadas `isFake` (posible relleno sembrado) [OBSERVADO el flag; el relleno es INFERIDO].
- **Radar de equipo** (SVG propio): 6 ejes = las 6 categorías, escala 0–100, anillos de rejilla en 35/70/100, empieza arriba en sentido horario.
- **Gráficos Recharts**: 3 líneas monótonas (rating 6 semanas con eje fijo 0–100; días entrenados; minutos diarios). Temporizador anillo con pitidos 3-2-1.
- **No existe ninguna dispersión Recuperación × Esfuerzo ni «túnel óptimo»** en el APK (E11).

---

## 6. Verificación del informe previo (solo lo que toca este APK / «Athlytic»)

| Afirmación | Veredicto | Evidencia |
|---|---|---|
| «Athlytic / Bevel / WHOOP» como apps de estado fisiológico (Recuperación y Carga del día) | **CORREGIDA** para este APK: el Android `com.getathlytic.app` es un tracker de fútbol sin datos fisiológicos. **CONFIRMADA** solo para la app iOS homónima (Athlytic: AI Fitness Coach, MyndArc LLC) | §1.2; [LITERATURA] ficha App Store y centro de ayuda |
| Athlytic: Recuperación a partir de HRV y FC en reposo | **CONFIRMADA** (iOS, [LITERATURA]): HRV + FC en reposo frente a línea base móvil de **60 días**, HRV con peso ligeramente mayor, se fija una vez al día, 50 % ≈ en línea base | centro de ayuda «Understanding Recovery» |
| «Carga del día — Strain 0–21 — en Y» (aplicada a Athlytic) | **CORREGIDA**: Athlytic usa **Exertion 0–10** (FC máx. de 30 d, FC reposo media de 60 d, tiempo sobre un umbral personal de reserva de FC); el 0–21 es la escala de **WHOOP** | centro de ayuda «Exertion»; WHOOP 101 |
| WHOOP: Recuperación 0–100 % y Strain 0–21 | **CONFIRMADA** [LITERATURA] (Strain logarítmico; Recovery rojo 0–33, amarillo 34–66, verde 67–100) | guías de WHOOP vía buscador (no pude abrir la página: HTTP 403) |
| Athlytic y Bevel/WHOOP muestran un **gráfico de dispersión** Recuperación (X) vs Carga (Y) con **«túnel óptimo» diagonal** | **NO VERIFICABLE**: ni el APK analizado ni los documentos consultados describen ninguna dispersión. Lo documentado es una **zona objetivo de esfuerzo** derivada de la Recuperación (en Athlytic iOS, además del «Training Goal» que sube/baja el límite inferior; en WHOOP, del Recovery y del Strain ya acumulado) **sin límites numéricos publicados** | centro de ayuda «Exertion»; WHOOP |
| «Bevel (antes Superset)» | **NO VERIFICABLE**: una sola fuente dice que Bevel nació como Superset; «Superset» aparece también como plataforma de coaching no relacionada. Bevel publica Recovery, Sleep, Strain y «Energy Bank» | ficha App Store; prensa; E12 |
| (Resto del informe previo: Evolve, RP, HRV4Training, Kubios, Banister, ACWR…) | no toca este APK: **sin juicio** aquí | — |

Nota de método: la búsqueda web se agotó al final y las guías de WHOOP bloquearon la lectura directa; los rangos numéricos de su «Strain Target» siguen sin obtenerse.

---

## 7. Aplicabilidad a AUGE

Producto de gamificación, no de recuperación. Criterio de diseño de KPKN: los anillos son una batería de recuperabilidad (0–100 %), no un marcador; ninguna idea de abajo debe convertirlos en puntos.

| Idea | Decisión | Módulo AUGE | Datos nuevos | Esf. | Riesgo | Prueba (sintética) |
|---|---|---|---|---|---|---|
| Puntuación semanal 0,40 días + 0,35 minutos + 0,25 volumen con tramos | **Descartar** como anillo/puntuación. **Adaptar** solo un indicador de *adherencia al plan* (hechas/planificadas, tope 100 %) en la semana de Home | `domain/training/HomeWeekResolver`, `ProgramCalendarEngine` | ninguno | S | premia «más» (opuesto a recuperar); tramos arbitrarios sin fuente (ciencia débil) | semana planificada de 4 días y 7 hechos ⇒ no pasa de 100 % ni sugiere más |
| Objetivo adaptativo (+15 %/+25 % desde mejor/media; recorte; redondeo) | **Adaptar** como «siguiente objetivo» de progresión **fuera de AUGE** (AUGE solo reduce) | `domain/workout` (progresión), sugerencias | e1RM/mejor serie ya existen | M | +15 % es agresivo en fuerza; sin literatura; la «media» usa todo el historial | históricos con estancamiento y con mejora; comprobar topes |
| Esquema de carga: demandas múltiples + coste de recuperación + modificadores; **bandas mín/objetivo/máx de sesión**; reglas duras/blandas con **acción de fallo**; máx. de ítems de alta intensidad seguidos; descanso tras alta intensidad | **Adaptar el patrón** (no los números): bandas de carga por canal y registro versionado de reglas (id, grupo, dura/blanda, acción, prioridad, versión) sobre el tope blando actual | `domain/auge` (drenaje, tope por canal), `domain/templates/SessionTemplateEngine` | tabla de reglas en asset JSON versionado | M | calibración propia obligatoria; complejidad; no hay valores de referencia | sesiones sintéticas que cruzan bandas ⇒ aviso/ajuste; reglas duras vs blandas |
| Progresión con «evidencia válida»: confianza, nº de observaciones válidas, `rules_version`, `reason_code` por observación | **Adaptar** al aprendiz de τ por usuario (filtro de Kalman): guardar confianza, nº de observaciones válidas, versión y código de motivo de cada actualización (explicabilidad) | `domain/auge` (aprendiz), Room (columnas nuevas; v30) | campos nuevos locales | S–M | migración de BD; sin riesgo de privacidad (local) | usuarios simulados: la confianza crece con evidencia y se reinicia al cambiar de versión |
| Selección diaria **estable** (clave usuario+fecha+perfil) | **Adaptar** a «sugerencias del día estables» hasta que cambien los datos | `domain/training/HomeSessionResolver` | ninguno | S | sugerencias obsoletas: invalidar al completar series o corregir un anillo | dos llamadas el mismo día ⇒ igual; tras completar serie ⇒ recalcula |
| Insights descriptivos (tendencia semanal, categoría más/menos trabajada, «más mejorada») | **Adaptar** el «patrón/músculo con mayor aumento de carga semanal» como línea de texto del resumen semanal | `HomeWeekResolver` | ninguno | S | ruido con pocos datos: exigir mínimo de sesiones | semanas sintéticas con y sin cambio |
| Anillo con delta semanal ▲▼ | **Adaptar** el delta «vs. hace 24 h» en los anillos (con semántica de color propia: en batería, subir es bueno) | UI de anillos | ninguno | S | colores verde/rojo ambiguos si se reutilizan con carga | captura de referencia |
| Valoración 1–5 de energía/foco/calidad/intensidad tras la sesión | **Descartar** (KPKN ya recoge RPE/RIR, DOMS y estrés; cuatro escalas añaden fricción) | — | — | — | redundancia | — |
| Radar de 6 ejes 0–100 | **Descartar** para los 13 pilares (ilegible); evaluar solo un radar de 6 patrones de movimiento como vista secundaria | UI | — | M | legibilidad | — |
| Catálogo de drills con rutas por perfil (posición, edad, arquetipo) y «rol en la sesión» (calentamiento / principal / apoyo / recuperación) con elegibilidad por rol | **Descartar** el catálogo y las rutas (KPKN ya tiene programas y plantillas); **adaptar** solo la idea de etiquetar cada ejercicio con rol y elegibilidad para validar plantillas | `SessionTemplateEngine`, catálogo v2 | etiqueta por ejercicio | S–M | trabajo de curaduría; solapa con el catálogo ya curado | plantillas sintéticas sin calentamiento ⇒ aviso |
| Racha de días consecutivos | **Descartar**: en fuerza el descanso es parte del plan | — | — | — | presión a entrenar sin recuperar | — |
| XP, niveles, insignias, rankings equipo/regional/global, filtros por edad, «jugadores similares» | **Descartar**: juego de puntos; datos de menores; exige backend (fuera de alcance) | — | — | — | privacidad, incentivos perversos | — |
| Derechos por suscripción (historial 7/30/90 d, IA solo Gold) | **Descartar** (otro modelo de negocio); la IA conversacional requeriría backend | — | — | — | — | — |

Lo que este APK **no** aporta: ninguna métrica fisiológica (HRV, FC, esfuerzo cardiovascular), ninguna carga aguda/crónica ni ACWR, ninguna noción de fatiga o recuperación. Para eso hay que mirar la literatura y los otros agentes (A1/A2/A4…).

---

## 8. Preguntas abiertas y decisiones para el usuario

1. **¿Se investiga la app iOS «Athlytic: AI Fitness Coach»?** Es la que el informe previo describe. Solo se puede por literatura (ya recopilada arriba): requeriría el IPA (iOS está fuera de alcance) y, aun así, el mapeo Recuperación→zona objetivo no está publicado [NO VERIFICABLE]. Recomendación: no, salvo que se reabra Health Connect.
2. **Health Connect (decisión cerrada, la reabres tú):** el modelo «Recuperación = HRV y FC en reposo vs línea base de 60 días» y «Exertion 0–10 desde FC de reserva» es la referencia de este tipo de producto. **Pros:** dato objetivo de recuperación. **Contras:** exige dispositivo, permisos y privacidad; contradice «sueño no se registra». Sin cambio de decisión, nada de esto es aplicable.
3. **¿Quieres un indicador de adherencia semanal?** Es la única pieza reutilizable de la «puntuación de esfuerzo» de este APK, pero como estadística de plan, nunca como anillo.
4. **¿Se incorpora un registro versionado de reglas duras/blandas con acción de fallo al motor de drenaje?** Mejora la explicabilidad y la calibración por usuario; coste M y una migración de Room.
5. **¿Se aceptan los límites de REA?** Para este APK basta con lo hecho; si se quisiera el grafo semántico completo habría que subir los topes (RAM) o instalar Wakaru (permiso del usuario).

---

## 9. Límites y apéndice

| Límite | Qué lo desbloquearía |
|---|---|
| Todo el servidor (XP, niveles, racha, ranking, IA, valores del motor de carga) | solo respuestas reales de sus servidores (**prohibido**; lo decide el usuario) |
| REA `recover-javascript-sources` no funciona | instalar Wakaru (≥1.13, Linux x64) y fijar `REA_WAKARU_COMMAND`; ganancia pequeña (ya tengo desminificador verificado) |
| Grafo semántico de REA parcial (tope de 20 000 nodos por archivo) | relanzar con topes mayores (cientos de MB y más RAM; máquina justa) |
| Cuerpo de `queryProductAndLaunchCheckout` (JADX sin cuerpo) | `dexdump.exe` del SDK; irrelevante para AUGE |
| App iOS «Athlytic: AI Fitness Coach» | su IPA (iOS fuera de alcance); solo literatura |
| Rangos numéricos del «Strain Target» de WHOOP | límite de búsquedas agotado y HTTP 403: fuente primaria del fabricante |

`A3-athlytic-evidencia.md`: procedencia y hashes (E0), registro de comandos REA R1–R63 (E1), manifiesto (E2), censo de clases (E3), rutas y claves de `localStorage` de REA (E4), scripts propios (E5), API (E6), esquema de BD (E7), pseudocódigo con posiciones (E8), constantes (E9), modelo de negocio (E10), visualizaciones (E11), fuentes web (E12), límites y qué desbloquearía cada uno (E13).
