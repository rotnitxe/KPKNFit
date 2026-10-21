# A2 · Kubios HRV 1.8.14 (Android) — qué mide, dónde lo calcula y qué sirve para AUGE

Análisis estático del `.xapk` `Kubios+HRV_1.8.14_APKPure.xapk` (SHA-256 `de1c7a13…9a6a`). Sin ejecutar nada. Apéndice con offsets, comandos e identificadores de evidencia: `A2-kubios-evidencia.md`.

Notación: **[R-n]** = comando de REA (tabla en el apéndice); **[H fn N]** = función N del bytecode Hermes, leída con mi desensamblador validado; **[S n]** = cadena n de la tabla Hermes; **[E]/[D]** = mis scripts ELF/DEX. Etiquetas: [OBSERVADO], [LITERATURA], [INFERIDO], [NO VERIFICABLE].

---

## 0. Resumen ejecutivo

**Qué es.** Kubios HRV 1.8.14 (`com.kubioshrvapp`) es un **cliente fino** en React Native (bytecode Hermes v96) de Kubios (Kuopio, Finlandia). Mide la VFC en reposo con banda BLE (intervalos RR), sensor óptico Polar (PPI) o, en beta, con el **acelerómetro del teléfono apoyado en el pecho** (no hay PPG por cámara). Sube los datos **crudos** por trozos a Kubios Cloud y **todo el análisis se hace en el servidor**: corrección de artefactos, índices temporales/frecuenciales/no lineales, PNS/SNS, *Stress index*, readiness y edad fisiológica [OBSERVADO: en 33 546 funciones JS y en el DEX no hay ninguna función de cálculo HRV; la finalización pide `analysis: readiness` y el servidor devuelve los índices].

**Lo valioso para AUGE.** (1) El **contrato de readiness**: 0–100 % con cortes 25/50/75 % que, según su propia ayuda, abarcan ≈2–3 %/14 %/68 %/16 % de las mediciones de la persona ⇒ bandas de la **distribución personal** cerca de z=−2/−1/+1 [INFERIDO]; usa hasta 90 días de historia y exige **mínimo 6 mediciones**. (2) **Protocolo**: matinal, reclinado, 180 s + 15 s de relajación (ajustables 30–300 s y 0–60 s), una medición diaria cuenta (repetirla la sustituye), autoevaluación 1–5 y notas. (3) **Calidad**: % de latidos corregidos con umbrales de aviso; aviso «sin datos» a los 9 s. (4) Lectura BLE estándar 0x2A37 (RR en unidades de 1/1024 s). (5) El SDK de Polar incluido modela la línea base como **media y desviación típica** (RMSSD, RRI, respiración) y una taxonomía de consejo «listo para fuerza/velocidad / solo cardio suave».

**Qué no pude ver.** Las fórmulas del servidor: cómo interpola readiness entre cortes, qué índice usa, la corrección de artefactos, la normalización PNS/SNS, la edad fisiológica y la detección de latidos por acelerómetro. No pude confirmar por web (cupo de búsqueda agotado en el turno).

**Recomendación.** Adaptar **lnRMSSD matinal con línea base personal** como evidencia opcional de Energía (ancla y confianza, nunca sustituto del modelo), primero por entrada manual y, si el usuario reabre sensores BLE —no Health Connect—, con banda de pecho. Descartar LF/HF, edad fisiológica, PNS/SNS poblacional y la medición por acelerómetro en el pecho.

---

## 1. Identidad y arquitectura

| Campo | Valor | Evidencia |
|---|---|---|
| Paquete / versión | `com.kubioshrvapp` 1.8.14 (código 2000122), minSdk 26, targetSdk 36; `MainActivity` única; servicios `CustomMeasurementService` y `CustomMeasurementEventService` (primer plano, tipo `connectedDevice`); esquema `kubioshrv://` | [R-1] [OBSERVADO] |
| Editor | Enlaces legales y de ayuda a `kubios.com`; certificado de firma de una empresa de **Kuopio (FI)**; **ninguna** cadena «Morpheus»/«Jamieson» | [S 3169, 3225], cert. `BNDLTOOL.RSA` [OBSERVADO] |
| Tecnología | React Native (arquitectura nueva, TurboModules); **Hermes bytecode v96**: 5 491 824 B, 33 546 funciones, 32 336 cadenas; info de depuración recortada (48 B) pero **nombres de función conservados** | cabecera Hermes [OBSERVADO] |
| Clases DEX | 9 872; **11 propias** (`com.kubioshrvapp.*`); el resto: SDK Polar BLE (`com.polar` 285 + `fi.polar` 40), RxAndroidBle (270), `react-native-ble-plx` (50), `react-native-sensors`, AWS Amplify, MMKV, almacenamiento cifrado, `react-native-config`, Skia, Reanimated, SVG | [R-2] [OBSERVADO] |
| Nativo (split armeabi-v7a, 27 entradas) | 23 `.so`, **todas runtime genérico** (Hermes, RN, Skia, Reanimated/Worklets, MMKV, uCrop, codegen de módulos); `libappmodules.so` = 4 016 símbolos de descriptores de componentes RN, **ninguno de HRV** | [E], [R-7] (REA no abre ELF ARM en este host, §9) [OBSERVADO] |

**Dónde vive la lógica.**

| Capa | Qué hace | Evidencia |
|---|---|---|
| Java propio (11 clases) | Puente Polar: streams HR+RR, PPI, ECG, ACC, PPG; grabación interna/offline; servicio en primer plano + tarea JS sin cabeza para registros largos. **Solo reenvía** muestras (HR, RR ms, contacto, `rrAvailable`, marca de tiempo del teléfono; PPI ms, marca, `errorEstimate`) sin procesarlas | [R-3] [R-4] [R-5] [OBSERVADO] |
| JS (Hermes) | Flujo de medición, BLE genérico, acelerómetro, troceo y subida, petición de análisis, pantallas, **umbrales de calidad y de categoría que se muestran**, gráficos de tendencia | [H] [OBSERVADO] |
| Servidor (Kubios Cloud, host `analysis.kubioscloud.com`) | **Todo el análisis HRV** y la línea base personal | finalización con `analysis:[{type:'readiness'}]` [H fn 20494]; respuesta → `fromApi` [H fn 20501]; códigos `AccPoorDataQuality`/`AccSampleRateLow` del servidor [H fn 30804]; «guardar sube a Kubios Cloud» [S 6211]; contenido del cálculo [NO VERIFICABLE] |

**Flujo de API** (rutas relativas a una `baseUrl` de configuración) [H fn 20476, 26544, 20475, 17113, 20494] [OBSERVADO]: abrir sesión de licencia → crear medición `POST /measure/self/session` con metadatos y definición de canales (`index`, `type`, `data_enc`, `unit`, `sample_rate`) → subir trozos `…/chunk` → `…/finalize` (`skip_missing_data:true`) → historial `GET /result/self`. Modo demo sin licencia: `POST /analytics/analyze`, no almacena. La autenticación de usuario usa el proveedor gestionado de AWS (Cognito/Amplify). **Hay credenciales embebidas en `BuildConfig`** [R-3]; no se transcriben. Lección para KPKN: el cálculo local evita exponer secretos de cliente.

---

## 2. Entradas de datos

| Modo | Señal | Frecuencia | Controles en el cliente | Evidencia |
|---|---|---|---|---|
| Readiness con banda BLE | RR (canal `rri`); Polar por SDK, resto por servicio estándar 0x180D / car. 0x2A37 | latido a latido | exige RR («sensor con datos latido a latido»); estado de contacto (Polar); aviso **«sin datos» a los 9 s** (consejo: humedecer electrodos); reconexión; compensación de desfase de marcas | [H fn 19879, 32429, 32419], [S 3538] [OBSERVADO] |
| Readiness con óptico Polar | PPI (canal `ppi`) | latido a latido | el `errorEstimate` de cada PPI **se descarta** al subir (solo `[valor, ts]`) | [R-5], [H fn 20531, 32446] [OBSERVADO] |
| Readiness con teléfono (beta) | acelerómetro; teléfono plano sobre el pecho, tumbado, sin hablar | **pide 1 000 Hz** (intervalo 1 ms) | canal `Acc` con marcas; el servidor rechaza por **calidad** o por **frecuencia insuficiente** (HTTP 400) | [H fn 30770, 30797, 30778, 30804], [S 724, 12773, 13011] [OBSERVADO] |
| Medición personalizada (investigación) | RR, PPI, ECG, PPG, ACC, marcadores; online u offline | la del sensor | requiere licencia para guardar; si no, modo demo | [S 619, 7336, 12769] [OBSERVADO] |
| Subjetivo | estado 1–5 (`user_happiness`): 1 «sin estrés, readiness alta» … 4 «estrés alto, readiness muy baja», 5 «no me encuentro bien, debo descansar»; notas | por medición | opcional | [H fn 32520] [OBSERVADO] |
| Perfil (en servidor) | sexo, nacimiento, altura, peso, FC reposo, FC máx., VO₂máx | — | — | [H fn 30335] [OBSERVADO]; su uso en las normas [NO VERIFICABLE] |

**No hay PPG por cámara.** El permiso de cámara es solo para la foto de perfil (`ProfileScreen → openCamera`, selector de imágenes) [H fn 30722–30763]; no existe biblioteca de cámara ni flujo de fotogramas [R-2, D] [OBSERVADO]. La hipótesis del brief (cadencia de fotogramas, ROI, canal de color, paso banda, detección de picos) **no aplica** a esta app.

**Lectura de la trama 0x2A37** (sensores no Polar) [H fn 19908, 19909] [OBSERVADO]: bit 0 de las banderas = FC de 16 bits (la app no decodifica ese caso: devuelve 255); bit 4 = hay RR; desplazamiento según banderas; cada RR = `round(uint16_LE / 1,024)` ms (unidades de 1/1024 s); marca de tiempo = reloj del teléfono. Es la especificación Bluetooth estándar [LITERATURA: Bluetooth SIG, Heart Rate Service].

**Protocolo y ajustes** [OBSERVADO]: duración por defecto **180 s** (30–300 s, paso 30) y relajación por defecto **15 s** (0–60 s, paso 5) [H fn 19654, 19662, 32661, 32662]; un equipo puede imponerlas vía `readiness_measurement_length_s` y `relaxation_time_s` [H fn 19354, 30826]; se recomienda posición constante (reclinado/semirreclinado), a la misma hora, idealmente al despertar, varias veces por semana [S 7561, 6116]; mediciones de 1–5 min [S 12774]. Si la relajación se excluye del análisis: [NO VERIFICABLE].

---

## 3. Métricas y fórmulas

Todas llegan **calculadas por el servidor** dentro del objeto de resultado; la app solo las pinta [H fn 32549 `RecoveryResult`] [OBSERVADO]. Las definiciones estándar son [LITERATURA]; los rangos «típicos» son los textos de ayuda de la propia app [S 12745] [OBSERVADO] y concuerdan con valores normales publicados (Nunan et al. 2010; Task Force 1996).

**3.1 Dominio temporal y Poincaré** (campos observados en `RecoveryResult`)

| Métrica (campo) | Definición | Rango mostrado por la app | Etiqueta |
|---|---|---|---|
| RR medio (`mean_rr_ms`), FC media (`mean_hr_bpm`) | media de NN; FC = 60000/RR̄ | FC típica **51–77 lpm**; atletas 40–60 | def. [LITERATURA]; rango [OBSERVADO] |
| RMSSD (`rmssd_ms`) | √(media(ΔRR²)) | típico **19–75 ms**; mayor = más vagal | def. [LITERATURA]; rango [OBSERVADO] |
| SDNN (`sdnn_ms`) | DE de los NN | «mayor = mejor salud cardiovascular» | def. [LITERATURA]; texto [OBSERVADO] |
| Poincaré SD1, SD2 (`sd1_ms`, `sd2_ms`) | SD1 = dispersión corto plazo (aprox. RMSSD/raíz2); SD2 = largo plazo; cociente SD2/SD1 = balance simpático-vagal | — | def. [LITERATURA]; campos [OBSERVADO] |

**3.2 Dominio frecuencial** (campos bajo `freq_domain`)

| Métrica | Definición | Nota de la app | Etiqueta |
|---|---|---|---|
| LF, HF (`LF_power`, `HF_power`, ms²) y en u.n. (`LF_power_nu`, `HF_power_nu`) | potencia 0,04–0,15 Hz y 0,15–0,40 Hz | HF = parasimpático; LF «sobre todo simpático» (simplificación de marketing) | def. [LITERATURA]; bandas estándar [LITERATURA]; textos [OBSERVADO] |
| LF/HF (`LF_HF_power`) | cociente | «balance simpático-vagal» | [OBSERVADO] |
| Frecuencia respiratoria (`respiratory_rate`) | derivada del HRV | típico **9–24 resp/min (0,15–0,40 Hz)** | [OBSERVADO] |

No hay VLF ni pNN50 (0 apariciones en cadenas) [OBSERVADO]. El método espectral exacto (FFT/Welch vs Lomb-Scargle/AR) ocurre en el servidor [NO VERIFICABLE]. [LITERATURA: el escritorio Kubios usa FFT-Welch y AR, con detrending por *smoothness priors* lambda; Tarvainen et al. 2014.]

**3.3 Índices autonómicos y de estrés** (texto de ayuda [S 12745]; valores [OBSERVADO]; fórmula [NO VERIFICABLE])

| Métrica (campo) | Qué dice la app | Escala declarada |
|---|---|---|
| PNS index (`pns_index`) | sube con HRV en reposo alta; «mejor salud y recuperación» | **−2…+2** en el 95 % de adultos; −1…+1 en el 68 % |
| SNS index (`sns_index`) | inverso al PNS; alto = HRV baja y más estrés | **−2…+2** en el 95 % |
| Stress index (`stress_index`) | «agudeza (peakedness) del HRV; mayor = más estrés» | — |

[LITERATURA] El escritorio Kubios define PNS/SNS como **z-scores** frente a valores de referencia poblacionales: PNS a partir de RR̄, RMSSD y SD1; SNS a partir de FC, el *Baevsky Stress Index* y SD2; el Stress Index de Baevsky se calcula sobre el histograma de RR (agudeza de la distribución). La app expone los tres campos, así que el servidor los calcula así [INFERIDO por coincidencia de nombres, escalas y origen]. **Importante para AUGE:** PNS/SNS son relativos a **población**, no a la persona, y dependen del perfil (edad/sexo) que vive en el servidor.

**3.4 Readiness y edad fisiológica**

| Métrica | Definición observada | Constantes | Etiqueta |
|---|---|---|---|
| **Readiness (0–100 %)** | «estado fisiológico comparando tu HRV en reposo actual con tus niveles habituales» | cortes de categoría **25 / 50 / 75** [H fn 26454]; reparto declarado **muy baja 2–3 % · baja 14 % · normal 68 % · alta 16 %**; usa hasta **90 días**; **mínimo 6 mediciones** [S 1949, 12745] | valor y cortes [OBSERVADO]; mapeo a la persona [INFERIDO] |
| Edad fisiológica (`physiological_age`) | compara tu HRV con normas por edad | barra de UI = (edadFisiol − edadActual + 28)/56·100, solo escala visual [H fn 27696] | fórmula del servidor [NO VERIFICABLE]; barra [OBSERVADO] |

El reparto 2–3/14/68/16 % es casi el de una normal en z<−2 / −2…−1 / −1…+1 / >+1, así que readiness mapea el HRV de hoy en la **distribución personal** de las mediciones recientes [INFERIDO fuerte]. Los cortes 25/50/75 son los de la **barra de color**, no z-scores; la conversión de z a 0–100 ocurre en el servidor [NO VERIFICABLE].

**3.5 Calidad de la medición** [OBSERVADO]

| Señal | Regla | Evidencia |
|---|---|---|
| Artefactos (`artefact`, %) | el resultado trae un % de latidos corregidos; si **> 10** la app avisa «calidad muy baja» y ofrece «Repetir» / «Continuar igualmente» | [H fn 32466] (umbral literal artefact>10) |
| Calidad mostrada | etiqueta GOOD / LOW / VERY LOW por franjas (menor o igual a 5 / a 10 / resto), con color | [H fn 32530] |
| Sin datos | si el sensor no entrega datos en **9 s**, diálogo «problemas obteniendo datos» | [H fn 32429] (setTimeout 9000) |

El % de artefactos y el método de corrección (umbral automático/medio, interpolación) se calculan en el servidor; la app solo recibe el número [NO VERIFICABLE]. [LITERATURA: el escritorio Kubios corrige con umbrales 0,05–0,45 s y niveles muy bajo/bajo/medio/fuerte/personalizado; Lipponen & Tarvainen 2019.]

**3.6 Límites de subida** (acotan el muestreo) [H fn 20540, 20541] [OBSERVADO]: por petición, máx. **100 000** muestras o un tope de segundos por canal (p. ej. RRI 3600 s, PPI 2400 s), con troceo por tipo.

---

## 4. Algoritmos y reglas de decisión (lo que ocurre en el cliente)

Casi toda la decisión es del servidor. En el cliente [OBSERVADO]:

1. **Mapa valor→categoría→color de readiness** [H fn 26454]: mayor/igual 75 Alta, 50 Normal, 25 Baja, resto Muy baja; mismo patrón en las etiquetas de estrés del selector de estado [H fn 32520].
2. **Compuerta de 6 mediciones** [H fn 26665]: si faltan, muestra «6 readiness measurements required» en vez del anillo; la línea base personal necesita historia.
3. **Compuerta de calidad** [H fn 32466]: artefact>10 provoca repetir/continuar.
4. **Agregación de tendencias** en el cliente [H fn 32731, 32732, 32734]: ventanas `month` / `three_months`; serie por día (92 iteraciones) y **promedio por semana** (suma/numero de puntos, pintando el domingo). Es suavizado visual, no un modelo.
5. **Ejes** [H fn 32728–32730]: PNS/SNS fijan rango simétrico redondeado; RMSSD/FC/readiness, min–max redondeado. Solo presentación.
6. **Una medición diaria** sustituye a la anterior; «mostrar solo las diarias usadas para readiness y tendencias» vs historial completo [S 6278, 7107].
7. **`hide_results`**: un equipo/licencia puede ocultar los resultados al sujeto [H fn 19356].

No hay zonas de carga, ACWR, CTL/ATL/TSB, recomendación de entrenamiento ni VBT en el cliente (0 coincidencias) [OBSERVADO].

---

## 5. Conceptos de interfaz y visualización

- **Anillo de readiness segmentado** animado (SVG/Skia): arco 0–100 con `strokeDasharray = 66/100·valor` y color por categoría; carrusel de métricas [H fn 26645, 26662, 26663, S 10806] [OBSERVADO]. Buena referencia del patrón «anillo simple arriba, números debajo», alineado con AUGE.
- **Resultado** [H fn 32549]: readiness grande; sección «HRV PARAMETERS» con FC media, RR medio, RMSSD, SDNN, SD1/SD2, stress index, respiración, LF/HF; PNS/SNS y edad fisiológica; «MEASUREMENT QUALITY»; estado 1–5 y notas.
- **Línea base como lenguaje**: todo se expresa como «frente a tus niveles habituales»; la app nunca enseña fórmulas, solo rangos de referencia. Es el tono que AUGE ya usa con la «batería».
- **SDK de Polar incluido** (no es lógica de Kubios, pero es material reutilizable) [R-6, D] [OBSERVADO]:
  - `PolarNightlyRechargeData` modela la línea base con **media y DE**: `meanBaselineRMSSD/RRI/RespirationInterval`, `sdBaseline…`, recuperación nocturna `meanNightlyRecovery…`, más `ansRate`/`ansStatus` y `recoveryIndicator`. Es el patrón «media ± DE personal» que conviene a AUGE [LITERATURA: Polar Nightly Recharge].
  - `PolarReadinessForSpeedAndStrengthTraining` es un **enum de consejo** con estados como `RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING`, `NOT_RECOVERED_NO_LEG_TRAINING_OR_INTENSIVE_CARDIO`, `…INJURY_AND_ILLNESS_RISK_CAUSED_BY_CARDIO_TRAINING`, con modificadores por mala recarga nocturna. Traduce recuperación a recomendación de **tipo** de entrenamiento; es un modelo de mensajes, no una fórmula, y una buena plantilla para la capa de consejo de AUGE.

---

## 6. Verificación del informe previo

| Afirmación del informe previo | Veredicto | Evidencia |
|---|---|---|
| «Kubios HRV / Morpheus (de Joel Jamieson)» | **CORREGIDA** | Son productos distintos. Kubios HRV es de **Kubios** (Kuopio, Finlandia): todos los enlaces van a `kubios.com`, el certificado de firma es de una empresa de Kuopio y no hay ninguna cadena «Morpheus» ni «Jamieson» [S 3169, 3225; cert. BNDLTOOL.RSA] [OBSERVADO]. Morpheus (Joel Jamieson / 8weeksout) es otra app no presente aquí [LITERATURA; no verificable por web este turno]. |
| «el estado matutino recalcula zonas del día» | **CORREGIDA / NO VERIFICABLE** | Kubios calcula un **readiness** matinal 0–100 y recomienda medir al despertar [S 7561] [OBSERVADO], pero **no define zonas de entrenamiento del día** ni las recalcula; no hay zonas en el cliente [OBSERVADO]. La taxonomía «listo para fuerza/velocidad» es del **SDK de Polar** incluido, no de Kubios [OBSERVADO]. «Ligarlo al umbral de pérdida de velocidad de VBT» es una propuesta del autor del informe, no algo de la app. |
| Catálogo HRV (RMSSD/lnRMSSD, SDNN, SD1/SD2, HF/LF, Stress Index Baevsky, PNS/SNS) | **CONFIRMADA (como campos)** | Todos los campos aparecen en el resultado [OBSERVADO §3]; las fórmulas no están en el cliente (servidor) [NO VERIFICABLE]. lnRMSSD como tal no aparece: la app muestra RMSSD en ms. |
| «PNS/SNS como z-scores respecto a referencia poblacional» | **CONFIRMADA parcialmente** | La ayuda declara escalas −2…+2 para el 95 % de adultos y −1…+1 para el 68 %, coherente con z-scores poblacionales [S 12745] [OBSERVADO]; la referencia exacta está en el servidor [NO VERIFICABLE / LITERATURA]. |
| «Readiness: qué entra, cómo se normaliza, cuántos días» | **CONFIRMADA (contrato) / NO VERIFICABLE (fórmula)** | Compara el HRV en reposo actual con los niveles habituales; hasta **90 días**, **mínimo 6 mediciones**, cortes 25/50/75, reparto 2–3/14/68/16 % [S 1949, 12745; H fn 26454, 26665] [OBSERVADO]. El cálculo numérico es del servidor [NO VERIFICABLE]. |
| «medición por cámara (PPG): fps, ROI, canal, paso banda, picos, ventana» | **CORREGIDA** | Esta app **no** mide por cámara. El modo sin sensor usa el **acelerómetro** del teléfono sobre el pecho [H fn 30770, 30778] [OBSERVADO]. La cámara es solo para la foto de perfil [OBSERVADO]. |

---

## 7. Aplicabilidad a AUGE

Contexto: AUGE no tiene FC/HRV; Health Connect está fuera; el sueño no se registra; Energía hace de proxy de SNC/estrés. Lo de Kubios encaja como **entrada opcional de confianza y estado inicial de Energía**, nunca como sustituto del modelo exp/τ.

| # | Idea de Kubios | Decisión | Módulo de AUGE | Datos nuevos | Esf. | Riesgo | Prueba |
|---|---|---|---|---|---|---|---|
| K1 | **lnRMSSD matinal con línea base personal** (media móvil 7 d vs base 60 d) como ancla/confianza de Energía | **Adaptar** | `AugeRecoveryEngine` (ancla de Energía, como `manualBatteryAnchorMs`), `WellbeingSaveMerge`, `DailyWellbeingLog` | un número HRV diario (manual o BLE) | M | ruido si pocas medidas; exigir base mínima | sintético: 60 usuarios, base ±DE, comprobar que una caída >1 DE baja la confianza, no τ |
| K2 | **Readiness 0–100 sobre distribución personal** (z→percentil con cortes 25/50/75) | **Adaptar** | nuevo `EnergyReadinessMapper` que alimenta el chip/anillo de Energía, no la batería | historia de HRV propia | M | necesita 6+ días; no confundir con la batería exp | reproducir reparto 2–3/14/68/16 % con normal simulada |
| K3 | **Umbral de mínimo de datos** (6 medidas, hasta 90 d) y mensaje «faltan N» | **Adoptar** | capa de confianza/UI de Energía | contador de medidas | S | — | sin historia ⇒ sin readiness, como ya hace AUGE con «sin datos» |
| K4 | **Control de calidad de la medición** (% artefactos; umbrales de aviso; «sin datos» a los 9 s) | **Adaptar** si se añade captura propia | pipeline de ingesta HRV (nuevo) | — | M | detección de latidos fiable es difícil en el móvil | banco de RR con ruido conocido; medir % corregido |
| K5 | **Patrón «media ± DE» del SDK de Polar** (Nightly Recharge) para definir «lo normal» | **Adoptar como diseño** | la futura línea base de Energía y, por analogía, las bandas SWC por músculo | — | S | — | comparar banda ±0,5·DE con la SWC del plan |
| K6 | **Enum de consejo por estado** (Polar «listo para fuerza/velocidad») | **Adaptar** | `LoadAdvisoryEngine` / `DiscomfortSuggestionEngine` (capa de mensajes) | — | S | no prometer lo que el dato no sostiene | tabla estado→mensaje revisada |
| K7 | **Protocolo guiado** (matinal, reclinado, 180 s, misma hora, repetible) | **Adoptar** si hay captura propia | onboarding/here de Energía | — | S | fricción diaria | — |
| K8 | Vía de hardware: **banda BLE 0x180D/0x2A37** (RR en 1/1024 s) para HRV sin Health Connect | **Adaptar (decisión del usuario)** | nuevo servicio BLE (como el puente Polar de Kubios) | permiso BLE | L | permisos, batería, soporte de dispositivos | leer RR de una banda real (fuera de este encargo) |
| K9 | LF/HF, VLF, frecuencia respiratoria espectral | **Descartar** | — | — | — | ciencia débil para autorregular fuerza; exige señal larga y limpia | — |
| K10 | **Edad fisiológica**, PNS/SNS **poblacional**, Stress Index Baevsky | **Descartar** (o solo informativo) | — | perfil edad/sexo | — | normas poblacionales, no personales; marketing | — |
| K11 | Medición por **acelerómetro en el pecho** | **Descartar** | — | — | — | pide 1000 Hz, calidad frágil, el propio Kubios lo marca beta y lo rechaza a menudo | — |

**Cómo mediría HRV AUGE sin Health Connect** (orden de preferencia): (a) **entrada manual** de lnRMSSD/HRV desde otra app del usuario (coste S, sin hardware); (b) **banda BLE** propia leyendo RR estándar (coste L, patrón ya visto aquí); **no** cámara PPG (esta app no la usa y la fiabilidad es baja) ni acelerómetro.

**Normalización de línea base a adoptar** [LITERATURA, confirmado por el patrón de Polar y el reparto de Kubios]: media móvil corta (7 días) de **lnRMSSD** frente a base larga (≈60 días) con su DE; marcar «cambio significativo» cuando la media de 7 d sale de la banda **±0,5–0,75·DE** (SWC); readiness = percentil del valor de hoy en esa distribución personal. Encaja con S3/S10 del plan de Rings v4 (perfil de recuperación y Energía de dos escalas).

**Qué pondría en la interfaz**: anillo de Energía igual que hoy; detrás, un panel «Pro» opcional con lnRMSSD de hoy vs su banda personal, una etiqueta de confianza (Alta/Media/Baja como ya hace `AugeRecoveryEngine.confidenceLabel`) y, si hay pocas medidas, el mensaje de «faltan N». La HRV **ajusta la confianza y el estado inicial de Energía**, no reescribe τ ni la batería.

---

## 8. Preguntas abiertas y decisiones para el usuario

1. **¿Se reabre la captura de HRV?** Hoy está fuera (Health Connect fuera). Opciones: manual (S), banda BLE propia (L). Pro: evidencia objetiva de SNC/estrés que hoy falta. Contra: hardware, permisos, fricción diaria. **Recomendado por defecto:** empezar por entrada **manual** opcional de lnRMSSD, sin hardware.
2. **¿HRV como ancla/confianza o como señal de estado?** Recomendado: **ancla + confianza** de Energía (coherente con las anclas manuales que ya decaen con el modelo), nunca sustituir la batería.
3. **¿Readiness personal (K2) en la UI?** Útil pero necesita 6+ días; decidir si se muestra como percentil o solo como color.
4. **PNS/SNS/edad fisiológica**: descartados por ser poblacionales; confirmar que no se quieren ni como dato informativo.
5. **Fórmulas del servidor**: para igualar la calidad de Kubios haría falta implementar corrección de artefactos (umbral/interpolación) y espectro desde la literatura (Tarvainen/Lipponen), no desde esta app. ¿Merece la pena, o basta con lnRMSSD temporal (robusto y barato)? **Recomendado:** lnRMSSD temporal.

---

## 9. Qué no se pudo ver y qué herramienta lo desbloquearía

- **El cálculo HRV completo está en Kubios Cloud**, no en el APK: fórmulas de readiness, normalización PNS/SNS, corrección de artefactos, edad fisiológica y detección de latidos por acelerómetro son **[NO VERIFICABLE]** por análisis estático. Ninguna herramienta local lo desbloquea (sería tráfico de red contra sus servidores, prohibido).
- **Verificación web** (autoría Kubios vs Morpheus, método espectral del escritorio): el cupo de WebSearch del turno se agotó; la autoría quedó resuelta por evidencia del propio APK. Si se quiere confirmar la ciencia del escritorio, basta una búsqueda web posterior (Tarvainen 2014; Lipponen & Tarvainen 2019).
- **REA y el nativo/Hermes en este host**: REA (JADX) resolvió perfectamente DEX/Java (manifiesto, 9 872 clases, métodos decompilados del puente Polar). Pero sus proveedores nativos **no funcionan en este Windows**: Ghidra P0 solo acepta PE nativo (rechaza ELF ARM), Hopper es de macOS e IDA no está configurado; `inspect-binary-layout` es «solo Linux x64»; y Hermes no tiene decompilador en REA. Por eso el bytecode Hermes y las `.so` los resolví con **mis scripts de Python** (parser de cabecera/cadenas/funciones y **desensamblador de opcodes v96 validado**: las 33 546 funciones decodifican con tamaño exacto, 0 fallos), más lector ELF y DEX.
  - **Lo que desbloquearía más:** no hace falta más para esta app, porque la lógica HRV no está en el cliente. Si en el futuro se quisiera leer bytecode Hermes con herramienta dedicada: **`hbctool`** (GitHub, Python, ~0,5 MB; pero su soporte llega hasta HBC v96 de forma parcial) o **`hermes-dec`** (GitHub, Python, ~1 MB), que da un desensamblado/decompilado más legible que mi script. Para las `.so` ARM haría falta **Ghidra** en un host Linux/con el proveedor adecuado, pero aquí no aportaría (las `.so` son runtime genérico). Ninguna de estas dos me excede en capacidad; mi desensamblador ya cubre lo necesario.

---

## Procedencia

`.xapk` de `C:\Users\valen\Downloads\` (solo lectura). Extracción verificada por CRC contra el zip; scripts en `C:\kw\research\tools\a2\` (Python `-I`). Análisis de APK apoyado en REA por `rea_run.sh` (una ejecución a la vez). Detalle de comandos, offsets, identificadores de evidencia y huellas en `A2-kubios-evidencia.md`.
