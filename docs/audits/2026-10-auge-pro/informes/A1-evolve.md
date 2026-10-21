# A1 · EVOLVE 1.1.1 (`com.richardoestreich.evolve`) — análisis estático

- Fecha: 2026-10-21. Analista: A1 (Sonnet 5.5). Entrada: `EVOLVE_1.1.1_APKPure.xapk` (sha256 `d236228a…36ff`).
- Herramientas: **REA 6.5.0** por `rea_run.sh` (`identify-flutter-build`, `inspect-android-package`, `inspect-dart-aot`, `search-android-classes`, `inspect-android-class/method`, `trace-android-references`) + scripts de Python propios (`python -I`) como complemento, donde REA no llega (ver §9). Solo análisis estático: nada se instaló, ejecutó ni consultó por red.
- Respaldo: `A1-evolve-evidencia.md` (referencias «E#»). Etiquetas: **[OBSERVADO]** **[INFERIDO]** **[LITERATURA]** **[NO VERIFICABLE]**.

## 0. Resumen ejecutivo

**Qué es.** EVOLVE, de `richardoestreich`, **no es un coach de fuerza ni de recuperación**: es un *rastreador de hábitos y metas gamificado* (XP, niveles, rachas, insignias, ranking, amigos) con una capa de IA —OpenAI `gpt-5-mini`, llamado **directamente desde el cliente**— detrás de la suscripción «EVOLVE+» (RevenueCat; €2,99/mes, €24,99/año, 7 días de prueba). Es una app Flutter (≈ 3.38) / Dart 3.10.4 **generada con FlutterFlow**, sin código nativo propio, con Firebase de backend.

**Relación con «Evolve AI / JuggernautAI» (Smith–Blevins): ninguna.** Ninguna coincidencia de «juggernaut», «blevins» o «chad wesley», y ninguna real de RPE, RIR, 1RM/e1RM (Epley, Brzycki, Lombardi), MEV/MAV/MRV, readiness, deload, fatiga, DOMS, HRV, pulso o VBT, ni en el pool completo de cadenas de Dart ni en un barrido de 397 archivos (los pocos aciertos brutos de 3 letras son ruido binario o códigos de idioma); sin ML, sin sensores, sin cámara de análisis (la cámara solo sube fotos de perfil/meta). El nombre «Evolve AI» existe *dentro* de esta app como etiqueta de su análisis de hábitos: probable origen de la confusión. **Es una pista falsa; no seguir por aquí.**

**Valor para AUGE.** Nada en métricas, fórmulas, umbrales ni datos fisiológicos. Solo tres ideas de *presentación* de bajo coste, realizables sin IA: la tarjeta de resumen en bloques «fortaleza / punto ciego / patrón / mensaje», la regla de redacción «una cifra, evidencia+impacto, acción aparte» y los chips de preguntas rápidas sobre los propios datos (§7). La gamificación por rachas choca con una lógica de recuperación.

**Hallazgo de seguridad.** Hay una **clave de API de OpenAI embebida** en `libapp.so` (no transcrita ni usada).

**No pude ver.** Las fórmulas numéricas de XP/nivel/racha (Dart AOT sin decompilador; el proveedor Ghidra de REA en Windows solo abre PE, no ELF ARM64), las reglas y funciones de nube de Firebase y la configuración de RevenueCat. Desbloqueo: *Blutter* (no compensa).

## 1. Identidad y arquitectura

### 1.1 Ficha

| Dato | Valor | Etiqueta | Respaldo |
|---|---|---|---|
| Paquete / etiqueta / versión | `com.richardoestreich.evolve` / «EVOLVE» / 1.1.1 (código 63), minSdk 24, targetSdk 36; instalación por splits (ABI + densidad) | OBSERVADO | REA `inspect-android-package` |
| Tecnología | Flutter; Dart **3.10.4** estable (9-dic-2025, `android_arm64`) ⇒ Flutter 3.38.x; instantánea Full AOT, hash `1ce86630…8e22`, **sin ofuscar** (nombres de clases y campos intactos) | OBSERVADO / INFERIDO (versión de Flutter) | REA `identify-flutter-build` (hash, clang 19.0.1), `inspect-dart-aot` (secciones, cabecera); la cadena de versión de Dart la leí con script propio en `libflutter.so` (REA devuelve `dart_version: null`) |
| Generador | **FlutterFlow**: carpeta `flutter_flow/` (`flutter_flow_theme`, `_util`, `_widgets`, `nav`, `revenue_cat_util`…), `FFUploadedFile`, `FlutterFlowDropDown`; **251 archivos Dart propios** (`package:evolve/…`), p. ej. `*_widget/_model` por pantalla y `backend/schema/*_record.dart` | OBSERVADO | REA `inspect-dart-aot` (165 de esos URIs en su muestra de 500) + pool completo propio (E7) |
| Código propio en el DEX | Solo `MainActivity` (una subclase vacía) y `R`; el resto es SDK/plugins, minificado con R8 (9 667 clases; 90 294 métodos) | OBSERVADO | REA `search-android-classes`, `inspect-android-class`; `apkanalyzer` |
| Plugins nativos | 21: Firebase (auth, firestore, functions, storage, messaging, performance), RevenueCat, Google/Apple sign-in, `image_picker`, `file_picker`, `share_plus`, `just_audio`, `video_player`, `sqflite`, `shared_preferences`, `url_launcher`… | OBSERVADO | REA `inspect-android-method … GeneratedPluginRegistrant registerWith` (E4) |
| Licencia | `com.pairip.*` (comprobación de licencia de Google Play) | OBSERVADO / INFERIDO | REA `search-android-classes pairip` |
| Analítica / config. remota | **Ninguna en Dart**: sin Firebase Analytics, Crashlytics, Remote Config (plugin) ni SDK de terceros. Por tanto **no hay eventos de embudo ni claves remotas que leer** | OBSERVADO (ausencia) | REA inventario de 9 667 clases; `NOTICES.Z` |
| Persistencia local | `shared_preferences`: solo 3 claves del ranking (`ff_leaderboardTopDays`, `ff_leaderboardTopStreak`, `ff_lastLeaderboardTopDate`); `sqflite`: solo la caché de imágenes (`cacheObject`, `user_version = 3`) | OBSERVADO | pool de cadenas |
| Idioma | UI **solo en inglés**; los 19 splits de idioma contienen únicamente cadenas de librerías (p. ej. 94 en `es`) | OBSERVADO | `aapt2 dump strings` |
| Cronología | Imágenes generadas con ChatGPT con fecha del 21-abr al 31-may-2026 ⇒ compilado después | INFERIDO | `flutter_assets` |

### 1.2 ¿Tiene relación con «Evolve AI / JuggernautAI»? **No**

1. El espacio de nombres `richardoestreich` es el de un desarrollador individual **[OBSERVADO]**; nada en el APK menciona Juggernaut Training Systems ni a sus fundadores.
2. 0 coincidencias de `juggernaut`, `blevins`, `chad wesley` en los 397 archivos escaneados (DEX, recursos, assets, libs) y ninguna coincidencia real de todo el vocabulario de fuerza/autorregulación (tabla en §3.1 y E13) **[OBSERVADO]**.
3. El producto no maneja ejercicios, series, cargas ni músculos. «reps», «workouts», «kg», «calories» solo aparecen como **unidades genéricas** de un hábito (la lista de 19 unidades de la plantilla de IA incluye también `km`, `pages`, `€`, `liters`…) y como ejemplo («Workout habit (value: 1) → adds 1 session»). De los 20 chips de intención del onboarding, 3 hablan de forma física/salud.
4. «Evolve AI» sí aparece **dentro** de la app: «Powered by Evolve AI» y la persona «EVOLVE AI» del prompt del LLM para analizar hábitos **[OBSERVADO]**. Es la marca de su función de IA; coincide en nombre por casualidad **[INFERIDO]**.

### 1.3 Dónde vive la lógica

- **Cliente (Dart/FlutterFlow)**: interfaz, reglas de producto y —casi seguro— el cálculo de XP, nivel y racha, que luego se escribe en Firestore (campos `current_xp`, `weekly_xp`, `current_streak`, `weekly_stats`…) **[INFERIDO por los nombres de campo; la aritmética no es visible]**.
- **IA**: el cliente compone el cuerpo JSON y llama a `https://api.openai.com/v1/chat/completions` con una cabecera `Bearer` embebida; lee la respuesta con la ruta JSONPath `$.choices[0].message.content` **[OBSERVADO]**. No hay servidor propio intermedio en esa ruta **[INFERIDO]**.
- **Servidor**: Firestore (datos y reglas), Storage, FCM, RevenueCat y *Cloud Functions* invocadas con `makeCloudCall` (región `us-central1`). Se desconocen los nombres y la lógica de las funciones (¿borrado de cuenta, push?) **[NO VERIFICABLE]**.
- Colecciones de Firestore deducidas de `backend/schema/*_record.dart`: `users`, `habits`, `habit_logs`, `goals`, `goal_logs`, `badges`, `user_badges`, `friends`, `friend_requests`, `weekly_stats`, `picture` **[OBSERVADO como archivos; el reparto de campos por colección es INFERIDO]**.

### 1.4 La cámara

Solo `image_picker` (`ImageSource.camera/gallery`, `pickImage/pickMultiImage`): foto de perfil (`photo_url`, «avatar») y foto de meta (`goal_photo_url`, `PictureRecord`, «Photo added. Swipe to view.», «Delete this photo?»), subidas a Firebase Storage **[OBSERVADO el plugin y los campos; INFERIDO el uso exacto]**. No hay plugin `camera`, ni ML Kit, TFLite, MediaPipe, CameraX ni modelos (el único `*.nnmodel` es un recurso de ejemplo de Apache Tika, 5 187 B). **No hay análisis de vídeo ni de técnica.**

### 1.5 Modelo de negocio

Freemium **[OBSERVADO como cadenas]**: plan gratuito con máximo 4 hábitos por día de la semana, 3 metas activas y usos de IA limitados (contadores `aiGoalGenerationUsed`, `aiHabitGenerationsUsed`). «EVOLVE+» (RevenueCat sobre Play Billing 8.0.0, con bibliotecas de Amazon IAP): €2,99/mes y €24,99/año («Save 30% • Only €0.07/day», 7 días de prueba; el reparto mensual/anual es **[INFERIDO]** por la aritmética: 24,99 € ≈ 0,07 €/día y −30 % frente a 12 × 2,99 €); incluye creación ilimitada con IA, analítica con IA, resumen semanal, insignias premium, sincronización en la nube, soporte prioritario y temas. Los IDs de producto y *entitlements* no están en el binario **[NO VERIFICABLE]**.

### 1.6 Seguridad y privacidad (para el orquestador y el usuario)

- Clave de API de OpenAI en claro en `libapp.so` (offset `0x4839a`) **[OBSERVADO]**. Es un secreto de un tercero: **no se ha usado ni se reproduce aquí**. Durante el análisis apareció una vez en la salida de una herramienta, y el arnés guardó esa salida en `C:\Users\valen\.claude\projects\C--Users-valen-Documents-KPKNFit\e5bf5841-3c4a-4327-a787-51d01257f542\tool-results\b51yfm2jy.txt` (no pude censurarla: el permiso para modificar el registro de sesión fue denegado); conviene que el usuario borre ese archivo si quiere. Todos mis archivos intermedios y los dos informes están censurados.
- La app envía a OpenAI el nombre de usuario y estadísticas de actividad agregadas (§4.2).

## 2. Entradas de datos

| Dato | Cómo entra | Evidencia |
|---|---|---|
| Hábitos: título, descripción, días de la semana, hora de recordatorio, categoría (8) y categoría propia, valor objetivo y unidad (19) | Formularios «Create Habit / Habit Configuration» o generación con IA | claves `selected_days`, `reminder_time`, `target_value`, `custom_category`, `custom_unit`, `suggested_by_ai`, `ai_generated`; plantilla A |
| Metas: título, descripción, categoría, fecha límite, tipo (`goal_type`: «Progress Goal» o logro puntual), valor objetivo/avance, foto | Formularios o IA | `goal_type`, `current_progress`, `goal_photo_url` |
| Cumplimientos | Toque «completado hoy» | `completed_today`, `count_value`, `progress_value`, `habit_logs`/`goal_logs` |
| Intenciones iniciales (20 chips) + texto libre | Onboarding («Describe in your own words…») | cadenas de UI |
| Perfil y social | Registro (correo, Google, Apple), nombre, foto, teléfono opcional, solicitudes y códigos de invitación | `display_name`, `photo_url`, `phone_number`, `friend_invite` |
| Preferencias de aviso | Interruptores por categoría (hábitos, metas, racha, motivación, resumen semanal, vencimiento de metas) y token FCM | `habit_notifications`, `goal_notifications`, `streak_notifications`, `motivation_notifications`, `fcm_token` |
| Estado de suscripción | RevenueCat | `is_premium`, `premiumExpiresAt` |
| **No captura** | sueño, FC, HRV, peso corporal, nutrición, pasos, ejercicios, cargas, RPE, ubicación, sensores | permisos y plugins (E3–E4) |

## 3. Métricas y fórmulas

### 3.1 Vocabulario de ciencia del entrenamiento: ausente

Barrido sobre el pool de 33 274 cadenas de `libapp.so`, sobre los bytes crudos de la sección de datos (ASCII y UTF-16) y sobre los 397 archivos del árbol (E13). Las cadenas vecinas no sirven de contexto: el orden en el *cluster* de cadenas no refleja su uso (E6); el contexto se obtiene de la agrupación léxica.

| Término buscado | Resultado | Apunta a… |
|---|---|---|
| RPE, RIR, 1RM/e1RM, Epley, Brzycki, Lombardi | 0 | — |
| Volumen, MEV/MAV/MRV, series, periodización, progresión doble, sobrecarga, deload, autorregulación | 0 (solo `setVolume` de audio; «sets» solo como setters de Dart) | — |
| Fatiga, readiness, agujetas/DOMS, bombeo, dolor articular, estrés | 0 | — |
| Sueño, HRV/rMSSD, frecuencia cardíaca, pulso | 0 (una cadena «Sleep» suelta, sin contexto asignable; «pulse» en una URL de animación) | texto de interfaz o identificador |
| Velocidad de barra/VBT, ACWR/CTL/ATL/TSB/Banister | 0 (solo `VelocityTracker` de gestos de Flutter) | framework |
| Ejercicio, músculos, nombres de ejercicios | 0; «workouts»/«reps»/«kg»/«calories» solo como unidades de hábito, ejemplo y frase motivacional («One more rep.») | **texto de interfaz y de prompt**, no cálculo |
| «strength» (4) | campo `strength` del resumen de IA (fortaleza conductual) y etiqueta «Your Strength» | prompt / interfaz |
| ML, pose, cámara de análisis | 0 | — |

**Ninguno de los términos apunta a un cálculo local.**

### 3.2 Lo que sí existe: métricas de hábitos y gamificación

Todas con fórmula **[NO VERIFICABLE]** (aritmética en el código AOT); se listan identificadores y lo que dice la interfaz.

| Métrica | Identificadores | Definición observada o inferida |
|---|---|---|
| XP | `xp_per_completion`, `xp_reward`, `current_xp`, `total_xp`, `weekly_xp`, `xp_progress`, `xp_peak`, `best_day_xp`, `daily_habit_xp_total`, `streak_xp_total`, `goal_xp_total` | «Earn XP points for completing this habit/goal»; cada hábito/meta lleva su propio valor (`xp_per_completion`/`xp_reward`) **[OBSERVADO]**. Etiquetas «+15 XP», «+50 XP», «+200 XP» sin asignación a eventos |
| Nivel | `level`, `level_xp`, `needed_xp`, `generateLevels`, `getLevelName`, `minLevel` | Nivel derivado de XP acumulada, barra «XP to next level», hitos «Level 5» y «Level 20» **[INFERIDO]** |
| Racha | `current_streak`, `longest_streak`, `updated_streak`, `last_completed_date`; «Streak Bonus», «streak protection» | Días consecutivos con actividad **[INFERIDO]** |
| Día perfecto | `perfect_day`, `perfect_days_count`, `last_perfect_day_date` | Probablemente todos los hábitos del día completados **[INFERIDO]** |
| Tasa de cumplimiento | `habitCompletionRate`; «% of your habits»; «Habit completion:» en el prompt | Completados / planificados en el periodo **[INFERIDO]** |
| Dificultad | `difficulty_level`, `difficulty_score`; etiquetas «Beginner», «Medium», «Advanced», «Elite» | Influye (¿en XP?) **[NO VERIFICABLE]** |
| Estadística semanal | colección `weekly_stats` (`week_id`, `habits_completed/total`, `goals_completed/total`, `xp_earned`, `active_days`, `best_day_xp`, `perfect_days_count`…) | Agregación por semana; el resumen semanal «se desbloquea el domingo» **[OBSERVADO el texto]** |
| Ranking | `leaderboard_top`, `_top_days`, `_top_streak`, `_wins`, `_repeat`, `last_leaderboard_check` | Posiciones y semanas en cabeza **[INFERIDO]** |
| Avance de meta | `current_progress`, `target_value`, `progress_value` | **Regla explícita**: cada cumplimiento del hábito vinculado suma el valor configurado en el hábito a una «Progress Goal» (ej.: hábito «workout» valor 1 → +1 sesión; «study» valor 2 → +2 horas) **[OBSERVADO]** |

## 4. Algoritmos y reglas de decisión

### 4.1 Reglas de producto observadas [OBSERVADO como texto salvo indicación]

- **Límites gratuitos**: 4 hábitos por cada día de la semana elegido; 3 metas activas; usos de IA contados.
- **Vinculación hábito→meta**: solo las «Progress Goals» admiten vínculo (§3.2).
- **Invitaciones**: insignias a los 3, 10 y 25 amigos que crean cuenta con tu código («Recruit», «Inner Circle», «Movement»).
- **Resumen semanal**: se genera/desbloquea el domingo; aviso semanal de «resumen listo»; se comparte por captura de pantalla.
- **Avisos**: hábitos, metas (antes del vencimiento), rachas, motivación, resumen semanal; hoja de ruta visible: «Smart reminders are coming in the next update».
- **Cuenta**: verificación de correo, cambio de nombre/correo/contraseña, borrado de cuenta, restaurar compras.

### 4.2 Plantillas de petición a un LLM (E10)

El cliente envía `{model: gpt-5-mini, response_format: json_object, messages: [system, user]}`. Tres cuerpos:

| Plantilla | Datos del usuario enviados | Respuesta esperada | Restricciones clave del *system prompt* |
|---|---|---|---|
| **A · Generar «mi sistema»** | Etiquetas de intención elegidas, descripción libre y —por las variables `recentHabitTitlesText/recentGoalTitlesText` y «Date of today»— títulos recientes y fecha **[INFERIDO]** | `{habits:[{title, description, days[], reminder_time, category, custom_category, targetValue, unit, customUnit}], goals:[{title, description, category, custom_category, deadline}]}` | Tamaño por complejidad (2+1, 4+2, 5+2; máx. 6 hábitos y 3 metas); 8 categorías (`health_fitness`, `mindset`, `productivity`, `learning`, `career_finance`, `relationships`, `personal_growth`, `custom`); 19 unidades; metas solo **logros puntuales** (nunca rachas, sprints, consistencia, rutinas); fechas ISO-UTC; todos los campos siempre, sin `null` |
| **B · Pregunta rápida** | Pregunta elegida + estadísticas del periodo | `{answer, tinyImprovement}` | `answer`: 2–3 frases que contestan la pregunta con los datos; sin lenguaje «robótico de productividad»; no atribuir causalidad hábito→meta sin respaldo; XP no es «gastable». `tinyImprovement`: una frase accionable distinta de la respuesta |
| **C · Análisis de progreso / resumen semanal** | Usuario (nombre), periodo, % de cumplimiento, hábitos completados, metas con avance, XP del periodo, racha, categoría principal, periodo más fuerte | `{headline, strength, blindSpot, growthPattern, message}` | Titular `<usuario>, [mini-insight]` (≤ 8 palabras); cada bloque ≤ 3 secciones cortas (fortaleza: «principal / evidencia / por qué destaca»; punto ciego: «debilidad / evidencia / impacto»; patrón: «detectado / qué sugiere / dirección»); **una sola cifra**; **sin consejos en el diagnóstico**; prohibido inventar estadísticas o patrones; `message` de 2 frases sin cifras |

Además hay dos textos con forma de instrucción para diálogos de cierre de sesión y borrado de cuenta; su uso es incierto **[NO VERIFICABLE]**.

### 4.3 Qué es local y qué remoto

Local (cliente): reglas de límites, vínculo hábito→meta, XP/nivel/racha **[INFERIDO]**. Remoto: IA (OpenAI), persistencia (Firestore), compras (RevenueCat), push (FCM). Ningún cálculo fisiológico en ningún lado.

## 5. Conceptos de interfaz y visualización

1. **Tarjeta de resumen en bloques** con emoji y rótulo —«💪 Your Strength», «🎯 Blind Spot», «📈 Growth Pattern», «🗣 Personal Message»— precedida de un titular personalizado. Separa *diagnóstico* de *acción*.
2. **Chips de «Quick Questions»** sobre los propios datos («Which category improved most?», «What repeatedly slowed me down?», «When was I the strongest?»), con respuesta corta + una micro-acción.
3. **Selector de periodo de análisis** («Last 3 Months», «recent weeks»).
4. **Carga escalonada de la IA** («Interpreting your request…», «Preparing your insights…», «Finalizing report structure») y *skeletons* en todas las listas.
5. **Gamificación visual**: ventana «LEVEL UP» con sonido (hay un MP3 de «level-up») y animaciones (paquetes `lottie` y `flutter_animate`), barras de XP, contador de insignias, ranking, contador de hábitos por día.
6. **Compartir** el resumen por captura; avisos configurables por categoría.
7. **Mapa de pantallas** (60 rutas de `go_router`, E7), útil como sustituto de los eventos de analítica que no existen: carga inicial → registro/verificación de correo → `onboardingflow` y páginas `ai1…ai8` (elección de intenciones y generación con IA) → `premiumAiLoadingPage` / `paywallpopup` → `home`, `habits`, `goals`, `weeklyRecapV1`, `friends`, `profile`, `accountPageV1` → suscripción (`subscriptionstatuspage`, `subscriptioncanclled`) y borrado de cuenta **[INFERIDO el orden por los nombres]**.

Relevancia para AUGE: solo 1–3 (§7).

## 6. Verificación del informe previo

| Afirmación del informe previo | Veredicto | Evidencia |
|---|---|---|
| «Autorregulación y fatiga neuromuscular (fuerza): **Evolve AI / JuggernautAI** (Garrett Blevins y Chad Wesley Smith)», tomando como tal el APK «EVOLVE 1.1.1» | **CORREGIDA** para este APK | El APK es un rastreador de hábitos de un desarrollador individual; 0 coincidencias de Juggernaut/Blevins/Wesley en 397 archivos (§1.2, E13). «Evolve AI» es el nombre de su función de IA de hábitos |
| «MEV/MAV/MRV por grupo muscular en tiempo real; readiness check previo con recorte automático de series o RPE objetivo» (atribuido a esa app) | **NO VERIFICABLE** con esta fuente (y **ausente** en este APK) | 0 términos de volumen, series, RPE, readiness en el binario. Verificarlo exige el producto correcto (JuggernautAI) o literatura (pista B1) |
| Resto del informe previo (Reactive Training Systems, RP Hypertrophy App, HRV4Training, Kubios, Athlytic/Bevel/WHOOP, WKO5, plataformas B2B, propuesta VBT) | No tocado por A1 | — |

## 7. Aplicabilidad a AUGE

Contexto leído: `PLAN.md` §8 (S1–S14) y el listado del motor `domain/auge/`. Criterio: ¿qué de EVOLVE sirve a una capa visual simple que descansa sobre métricas profesionales?

| # | Idea | Decisión | Módulo AUGE | Datos nuevos | Esf. | Riesgo | Cómo se probaría |
|---|---|---|---|---|---|---|---|
| 1 | **Tarjeta de resumen semanal en bloques** (titular + fortaleza + punto ciego + patrón + mensaje) generada **de forma determinista**: fortaleza = anillo/músculo con mejor media; punto ciego = el que más horas estuvo bajo un umbral; patrón = tendencia entre semanas | **Adaptar** (valor bajo–medio) | Función pura nueva en `domain/auge/` + tarjeta en la pantalla de anillos; se apoya en S2 (`SessionImpact`) y en el historial de observaciones (S11) | Ninguno | M | Textos pobres con pocos datos (exigir mínimo de sesiones); solapa con S2 | Semanas sintéticas (p. ej. 3 sesiones de espalda seguidas) → afirmaciones esperadas con pruebas *golden*; propiedad «toda cifra mostrada procede de un valor calculado» |
| 2 | **Regla de redacción**: una sola cifra por bloque, «evidencia → impacto», sin consejo en el diagnóstico, acción mínima aparte, nunca inventar cifras | **Adoptar** como guía de UX-copy | Textos de S1/S2 y del coach (`WorkoutCoachMessages`) | Ninguno | S | Nulo | Revisión de textos + test que rechace cifras sin origen |
| 3 | **Chips de preguntas rápidas** sobre los propios datos («¿por qué Pecho está al 71 %?», «¿cuándo estaré al 85 %?») respondidas por plantillas | **Adaptar** | UI de AUGE; reutiliza S1/S2 | Ninguno | M | Alcance; depende de S1/S2 | Tabla pregunta→función pura con casos sintéticos |
| 4 | Selector de periodo de análisis (semana/mes/3 meses) para tendencias de anillos | Adaptar (solo si hay pantalla de historial) | UI | Ninguno | S | Bajo | Prueba de UI con series sintéticas |
| 5 | Gamificación (XP, niveles, rachas, insignias, ranking, amigos, invitaciones) | **Descartar** para AUGE | — | — | — | La *racha* premia entrenar a diario y contradice anillos bajos que piden descanso **[INFERIDO]**; lo social exige backend (fuera de alcance) | — |
| 6 | LLM en el cliente con clave embebida | **Descartar** | — | — | — | Expone un secreto, envía nombre y datos a un tercero, contradice «sin backend»; los motores deterministas son el activo de AUGE | — |
| 7 | Vínculo «cada registro suma un valor configurable a una meta» | Descartar | — | — | — | Su analogía (series efectivas semanales vs rango MEV–MRV) la cubre la literatura (pista B1, landmarks MV/MEV/MAV/MRV) | — |
| 8 | Carga escalonada de IA, límites freemium | Descartar | — | — | — | No aplican | — |

**Ya cubierto por el motor actual**: nada de esto compite con `AugeRecoveryEngine`, `ExerciseReadinessEngine`, `LoadAdvisoryEngine`, `OvertrainingDetector` ni con las cargas aguda/crónica; EVOLVE no aporta ningún concepto de progresión, readiness ni carga.

**Conclusión crítica: no hay nada aprovechable en la ciencia, los datos ni los algoritmos.** Las ideas 1–4 son de presentación y su valor depende de S1/S2, que ya figuran en el plan.

## 8. Preguntas abiertas y decisiones para el usuario

1. **(Orquestador)** La pista «Evolve AI / JuggernautAI» no se resuelve con este APK. Si el usuario quiere analizar JuggernautAI, necesita *su* APK o documentación pública (literatura, pista B1). ¿Se reasigna?
2. **(Orquestador)** En Descargas hay APK con más probabilidad de contener lógica de fuerza/progresión, por nombre de archivo **[INFERIDO]**: «MacroFactor Workouts», «Sheiko Gold», «Metric – Barbell Tracker», «My Lift App», «WL Analysis». Ninguno es de este informe.
3. **Decisión del usuario — resumen semanal determinista (idea 1).** *Pros*: refuerza la explicabilidad (S2) sin IA, sin datos nuevos, con pruebas *golden*. *Contras*: M de esfuerzo, texto pobre con poca historia, duplica parte de S2. Recomendación: hacerlo después de S2, no antes.
4. **Decisión del usuario — gamificación (rachas, logros) en KPKN en general.** *Pros*: retención. *Contras*: choca con la lógica de recuperación, lo social necesita backend, riesgo de incentivar sobreentrenamiento. Recomendación: no en AUGE.
5. **Desbloquear el Dart AOT (Blutter)**: *no recomendado*; el resultado (fórmulas de XP de una app de hábitos) no cambiaría ninguna decisión de AUGE.
6. **Seguridad**: la clave de OpenAI es de un tercero; no usarla. Si el usuario quiere, puede avisar al desarrollador (una clave en un APK público está comprometida) y borrar el archivo de resultados de la sesión que la contiene (ruta en §1.6).

## 9. Apéndice y límites

Todo el detalle está en `A1-evolve-evidencia.md`: comandos de REA con resultados (E1), límites de REA (E2), manifiesto (E3), plugins (E4), DEX (E5), Flutter/Dart y formato de cadenas (E6), listados de archivos Dart, rutas y claves de datos (E7), plantillas de LLM (E10), textos de interfaz (E11), recursos (E12), barridos de vocabulario (E13), y lo que no se pudo ver con su desbloqueo (E14).

**Límites honestos.** (1) REA en este equipo **no puede analizar el ELF ARM64** `libapp.so` (el proveedor Ghidra solo admite PE en Windows; el perfil `pwntools-elf` solo Linux x64), y `inspect-dart-aot` devuelve una muestra acotada (500/207/2000 elementos) sin las cadenas de interfaz; por eso las cadenas, plantillas y claves salen de mi analizador propio (`dart_stringchain.py`), verificado contra bytes crudos. (2) Sin decompilador de Dart no se ven constantes ni fórmulas. (3) Nada del servidor es observable. (4) El análisis es estático: no se ejecutó la app ni se contactó ningún servidor.
