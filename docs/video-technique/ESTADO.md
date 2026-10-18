# Técnica en vídeo · estado de la entrega

Fecha de la continuación: 17–18 de octubre de 2026. Rama de trabajo `vt/vt9a`, integrada localmente por avance lineal en `feat/video-technique`. Se retomó el plan aprobado y el historial local de Claude Code de «Analizador de técnica y conteo de repeticiones». El relevo a agentes Codex está autorizado en U10.

Delegación aplicada por complejidad: `gpt-6-luna` con esfuerzo medio para inventario e integridad de solo lectura; `gpt-6.1-sol` con esfuerzo alto para estado/Room y cámara/Compose. No se usó Sol Max. Se mantuvo un solo escritor de producto a la vez por el espacio libre, con listas de archivos, contratos y pruebas concretas; el orquestador revisa los diffs, ejecuta la validación y decide la integración.

## Alcance implementado

- Motor local de análisis al terminar el vídeo: pose MediaPipe full, segmentación de completas/parciales/fallidas, ROM, fases, trayectoria y velocidad cuando sus condiciones permiten publicarla.
- Pantalla de análisis con reproducción, observaciones y calibración del disco; hub, importación de galería, ajustes persistentes, tendencias y comparación.
- Propuestas en la tarjeta activa y en el descanso completo/compacto. «Usar» modifica el borrador local; «Corregir» persiste la clave exacta con la puerta compartida de voz. Ninguna cifra se aplica automáticamente.
- Correcciones de cero completas con parciales, unidades históricas y series omitidas. Se conserva el temporizador, el token de deshacer y los RM/PR calculados al registrar.
- Instantánea de sesiones integrada: `cc75ae87c`. No se sigue automáticamente el avance concurrente de esa rama.

## Evidencia y aceptación

La aceptación de precisión **FALLÓ** en la única evaluación final de reserva. No se redujeron umbrales ni se repitió esa reserva. La rama funcional ya está integrada y su QA sigue en validación; el código implementado no constituye por sí solo aceptación de precisión ni aceptación de dispositivo.

| Comprobación | Resultado y alcance |
|---|---|
| Código | Alcance funcional implementado e integrado localmente en `feat/video-technique`. Commits `adb95c1f0` (producto y regresiones) y `15c6aac72` (banco derivado). Se comprobaron 53 fuentes, 64 archivos del motor y 225 fixtures: contenido idéntico entre worktrees, salvo conversión CRLF de Git en algunos textos. Pendiente de cerrar QA conjunta; sin push. |
| Compilación, ronda 1 | Compilaron Kotlin de app, androidTest y unit tests. Esto acredita compilación de esos destinos, no pruebas Android ejecutadas ni APK instalado. |
| Pruebas funcionales, ronda 1 | La tarea falló: 307 pruebas, 304 correctas y 3 fallidas. |
| Correcciones tras ronda 1 | «Usar» reinicia solo el selector de reps para que el scroll anterior no sobrescriba el valor; se conservan parciales restauradas. Añadidas regresión de edición manual posterior y pruebas de propiedad de cámara. |
| Build conjunto Android, ronda 2 | **BUILD FAILED en 24 min 51 s**, con causa explícita «Java heap space». Fallo de recursos antes de nuevas pruebas o APK; no se diagnosticó un error de compilación Kotlin. |
| Pruebas funcionales Android, ronda 2 | Sin nuevas pruebas ejecutadas antes del fallo de memoria; no corresponde declarar test FAIL ni PASS de esta ronda. |
| Build conjunto Android, ronda 3 | Kotlin de app, androidTest y unit tests compiló y se generó el APK. La tarea terminó **BUILD FAILED en 27 min 33 s** por dos pruebas heredadas; sin error de compilación. `build-8b-3.log`, heap de 4 GB y un worker. |
| Pruebas funcionales Android, ronda 3 | 2.405 ejecutadas: 2.397 correctas, 2 fallidas, 6 omitidas, 0 errores. XML preservado en `results-8b-3/`. Los fallos eran un selector de texto obsoleto de Ultrarrápido y medición de fuente con el renderer legacy de Robolectric. |
| Pruebas heredadas, ronda 4 | Tras corregir solo el selector y usar gráficos nativos para el test de desbordamiento, **32/32 correctas** (20 LiveLayerDismiss y 12 SetCardTechniqueBack). Se conservan expectativas de cierre, píxeles, abreviación y accesibilidad. `build-8b-4-baseline-tests.log`. |
| Revisión independiente posterior | Detectó y se corrigieron: registro real de 0+parciales, actualización de controles del mismo CompletedSet, restauración de cero sin draft y muestras fuera del intervalo REC. El parche invalida atómicamente el borrador objetivo y su alias bilateral; preserva el lado hermano y otras series. |
| Pruebas / APK tras revisión, ronda 5 | Interrumpida por el orquestador durante Kotlin tras advertencia de heap; GC observada con old-gen >99 %. No llegó a nuevas pruebas ni APK. Se verificó PID/cliente antes de terminar solo esa JVM. `build-8b-5-review-fixes.log` y `heap-8b-5-gc.txt`. |
| Pruebas / APK tras revisión, ronda 6 | **BUILD SUCCESSFUL en 16 min 43 s. 145/145 correctas**, 12 clases, cero errores u omisiones. Incluye diez regresiones nuevas y las rutas de tarjeta, sensores, registrador, Room y cámara. Mismas fuentes congeladas (50 hashes). Heap local 6 GB, guarda elevada a 9 GB libres, un worker y nuestro AVD apagado durante el build; sin cambios de Gradle en el repositorio. `build-8b-6-review-fixes.log`, XML y resumen en `results-8b-6/`. |
| Revisión de metadatos tras QA | Se detectó un seed inicial con lado crudo y configuración ausente. `WorkoutMediaCaptureRequestPolicy` normaliza/enriquece antes del callback visual y del diario inicial, y reutiliza la política en Finalize. La revisión independiente no detectó defectos materiales. Las siete regresiones nuevas de request/obturador y una de asociación bilateral antigua pasaron en la ronda 7. |
| Pruebas / APK, ronda 7 | **BUILD SUCCESSFUL en 31 min 10 s; 170/170 pruebas funcionales correctas**, 16 clases, sin errores u omisiones. Los 16 XML son posteriores a la congelación; copia y resumen en `results-8b-7/`. Compilaron app, unit tests y androidTest y se generó el APK debug. Coinciden las 53 fuentes congeladas y los 64 archivos del motor siguen idénticos. No se repite la reserva final. `build-8b-7-capture-seed.log`. |
| Identidad del APK, ronda 7 | El build anterior usa el paquete normal `com.example.kpkn`: la guarda lo detectó y bloqueó antes de instalar. Se conserva `r7-normal-build-proof.json`. Se genera el paquete aislado `.dbg` con el init script existente `C:/kw/tools/dbg-suffix.init.gradle`, sin cambiar fuentes. Ese empaquetado espera la guarda de 9 GB de RAM; otros builds están activos. |
| Renders | 36 artefactos de render en rondas 1 y 3; revisión visual de tarjeta, grabador y descansos al 130 %. No sustituyen la navegación real. |
| Banco de ajuste | Cuatro pruebas correctas; diagnósticos de modelos/velocidad correctos como ejecución, sin acreditar exactitud física. |
| Reserva final | **Ambos gates FALLARON:** conteo y giros a 250 ms, en una única evaluación final. Log `golden-final-1.log`. No se retoca ni se repite la reserva. |
| Diagnóstico externo | 416/416 reproducciones, tarea ejecutada correctamente. Sus porcentajes son diagnósticos separados, no aceptación. |
| APK previo a revisión | Generado en ronda 3: 677.176.161 B, SHA-256 `d8aad7e5bd13701e62d39b4136969cb0322d49631e2593badeddf7c54f1a43c8`, paquete `com.example.kpkn.dbg`. Las correcciones posteriores requieren un APK nuevo. |
| APK debug posterior a revisión | 680.619.023 B, SHA-256 `75220bdae29e0113694da49a724ffa6e84121066b2902e07861ade8aca156838`, generado el 18-oct a las 03:09:36 UTC. Paquete y fuentes se vuelven a verificar antes de instalar. |
| Instalación previa a revisión | APK de ronda 3 instalado con Success y MainActivity top-resumed; datos preservados en el AVD original. También instalado en el AVD aislado con cámara sintética. No acredita las correcciones posteriores. |
| Emulador / navegación, APK ronda 6 | Instalado con Success en `KPKN_VT_QA_20261017`, MainActivity top-resumed. Con fixture ficticio de onboarding completado: Perfil → hub → galería → catálogo → análisis → reproductor; x14 muestra «≈ 2», confianza baja y vídeo no concluyente. La cámara virtual se abrió desde la sesión real; tras reintentar el guardado, el álbum confirmó la asociación 20 kg × 8 y su acción abrió el análisis. Sin atleta, el resultado y la tarjeta mostraron no concluyente, sin cambiar las 8 reps manuales. Se registró la primera serie con RPE 5 y se observó el descanso. Quedan nueva captura con el arreglo de seed y fin completo de la sesión. |
| Cámara del emulador | Perfil concedido 30 fps frente a 60 solicitados. Archivo H.264 válido: 1280×720, giro −90°, 514 fotogramas y 52,337 s; promedio ≈9,82 fps bajo carga. No acredita 30 fps reales. El análisis mostró «No vimos a nadie» y «Graba a 30 fps o más», sin repeticiones o velocidad. |
| R8 | Pendiente. Intento 1 interrumpido por arreglos de revisión. Intento 2 falló con IOException de espacio insuficiente. Intento 3 comenzó tras esperar RAM y se interrumpió para incluir el arreglo de seed; la señal TERM del wrapper no terminó su bucle, por lo que se identificó y detuvo solo su JVM. No se atribuye ese último fallo a R8. Se mantiene init script local con firma debug, sufijo `.dbg` y ABI x86_64; minificación activa y release no debuggable. |
| Teléfono físico | Pospuesto expresamente; sin validación de esta entrega en Z Flip5. |
| Push / publicación / release | Sin push ni publicación; sin aceptación de release. |

La ronda 3 es un reintento por recursos: no se rebajó la memoria mínima ni se retocaron el código o los gates por el fallo de heap. Tras verificar PID, padre y creación, el orquestador terminó únicamente la JVM huérfana de la ronda 2.

La verificación posterior a los arreglos confirmó por SHA-256 los 64 archivos del motor sin cambios frente a la congelación. El revisor independiente cerró los cuatro hallazgos funcionales en lectura y las nuevas regresiones se ejecutaron en ronda 6.

La QA usa un AVD propio con cámara sintética. Tres intentos con QEMU headless sufrieron excepción Windows `0xc0000005`; el cuarto usa el backend gráfico con ventana oculta. Se preservan los eventos Windows y no se atribuyen esos fallos nativos del emulador a la app. El fixture cambia únicamente `onboardingCompleted` y añade un programa ficticio mínimo; esto no valida el onboarding. El primer guardado de cámara quedó pendiente y se recuperó con «Reintentar», sin causa inicial demostrada. El arreglo de seed conserva metadatos en esa recuperación, sin presentarse como explicación del primer fallo.

Ante el agotamiento del disco, el usuario autorizó retirar salidas obsoletas de KPKN. Se eliminaron **32.456.194.803 B**: intermediarios de nueve builds del 12–16 de octubre sin procesos activos y snapshots/comandos temporales de un AVD apagado. La segunda limpieza se hizo después de que los builds concurrentes volvieran a dejar solo 6,8 GB libres. Se conservaron fuentes, APKs finales de esos builds, informes de pruebas, vídeos y datos persistentes del AVD; los AVD de QA activos o en uso se excluyeron de la limpieza. Manifiestos: `disk-cleanup-authorized-summary.json`, `old-build-intermediates-cleanup.json`, `inactive-builds-round2-cleanup.json` y `unused-avd-cache-cleanup.json`.

Las rondas funcionales Android 2 y 3 excluyen mediante un init script local únicamente los dos controles de reserva ya evaluados. No modifican las pruebas del repositorio: su ejecución normal conserva los controles fallidos. Un resultado verde de regresiones funcionales no convierte el banco completo en aprobado.

## Banco congelado

Los 97 clips aptos de fuentes compatibles se separaron antes de extraer: Pexels/Mixkit/wger para ajuste (59), Pixabay/Commons/YMove para reserva (38). El humo histórico `x14-commons-squat` queda fuera de la aceptación ciega: 37 clips de reserva ciega. La partición no se cambia para mejorar porcentajes. SHA-256 del manifiesto de entrada: `fd4b2f72d43bd59ada8b8b0af7d0ebbab129629c17e089e75aa46eec33b86c3b`.

El diagnóstico externo completó 416/416 extracciones y reproducciones del dominio: Kaggle 214 repeticiones aisladas y UCF101 202 clips con 116 grupos conocidos. Cada grupo describe procedencia, sin garantizar personas independientes. Los 15 rangos que empiezan en cero no se convierten en aciertos por falta de salida. Los datos externos y el informe por clip permanecen fuera del repositorio; solo se publican estadísticas agregadas.

El reparto de ajuste ya muestra errores que impiden afirmar precisión global: extensión de rodilla `g04`, hack `r29`, flexiones `r11` y fondos `wger_0064`. No hay evidencia que justifique cambiar un umbral numérico sin convertir ruido o huecos en movimiento inventado. Se conservaron los parámetros y la guarda LOW_FPS.

La reserva se evaluó una sola vez con 37 clips. Para perfiles con n ≥ 3: nivel A, SQUAT 2/4 dentro de rango (50 %); nivel B, BENCH 4/4, CURL 4/4 y ROW 3/5, agregado 11/13 (84,6 %). La cobertura de esos perfiles fue del 100 %. Ninguna salida fue ALTA: su precisión no queda validada y su cobertura fue cero. ROW `r02` tiene error de 7, por encima del máximo permitido de 1. Los perfiles con n < 3 se informan sin conclusión. Cinco negativos de espalda no produjeron ALTA; dos conteos inciertos quedan separados.

El control de tiempos también falló: r02 y r03 tienen giros sin pareja; r08 y r17 difieren en número de giros; r09 supera el máximo en 266,7 ms y r18 en 446,8 ms. El MAE de pares disponibles no mide los giros ausentes. Se conservan los informes completos de conteo y tiempos.

## Diagnóstico de los fallos y arreglo propuesto

- **ROW / r02:** etiqueta 12 completas (rango 12–13), resultado 5 completas + 1 parcial; vista etiquetada SIDE y motor OBLIQUE. La extracción tiene 287/287 fotogramas de pose con una persona, pero disco PENDING, cero intentos y cero muestras. Hay un hueco de adquisición; no es evidencia de un tracker que perdió un disco ya adquirido. Todavía se desconoce qué condición impidió intentar adquirir y qué señal eligió el contador. Instrumentar razones de espera y selección de señal; estudiar adquisición independiente de muñecas/quietud en una ronda nueva.
- **ROW / ymove_006:** remo unilateral sin disco, 4 completas anotadas frente a 3 completas + 3 parciales + 1 fallida. El motor clasifica LYING frente a OBLIQUE en la etiqueta. La fragmentación de ciclos necesita diagnóstico de señal/polaridad y del perfil; el seguimiento del disco no la resuelve directamente.
- **SQUAT / ymove_007 y 011:** sin disco, cinco ciclos presentes pero el replay final devuelve 4 completas + 1 parcial. Los wrappers de extracción conservan 5/0 y sus últimas llegadas (17,44 y 17,12 s) exceden el final de las ventanas anotadas (17,0 y 16,6 s). Es compatible con clasificación de borde al recortar; no demuestra falta de pose. Revisar recorte/clasificación con sintéticos de borde y datos de ajuste, manteniendo esta reserva como resultado histórico.
- **Resto:** `g04` incluye una discrepancia de clasificación de vista y exceso de ciclos parciales; `r11` tiene un hueco de pose cerca de un giro y un final fuera de ventana; `wger_0064` mezcla huecos y movimiento de cámara. `r29` conserva pose en todos los fotogramas y su causa exacta no quedó demostrada. No se atribuyen todos a una única causa ni se modifica un umbral para forzar el porcentaje.

Controles `x06` ROW y `g26` SQUAT cuentan correctamente a unos 15 fps y sin disco adquirido. Por eso LOW_FPS no explica por sí solo los fallos. Una persona detectada en cada fotograma tampoco garantiza precisión o visibilidad de todas las articulaciones.

La ingeniería inversa estática de WLAnalysis 2.13.9 añadió una comparación específica: detector TFLite de disco separado de KCF, modo ArUco, escala por diámetro y segmentación por velocidad de barra. KPKN ya tiene Hough/NCC/Kalman y filtrado bidireccional. Las mejoras candidatas son adquisición con manos ocultas, reinicio de apariencia al readquirir y rechazo de saltos de identidad. No se copiaron modelos/código ni se retocó el motor congelado. Véase `WL_ANALYSIS_RE.md`.

El diagnóstico externo, separado de la aceptación, obtuvo rango 46/56 en A (82,1 %, cobertura 56/90) y 6/8 en B (75 %, cobertura 8/9), para los perfiles con n suficiente. Kaggle aislado: 122/214 salidas y rango 105/122 (86,1 %). No hay ALTA; no se suman estos resultados a la reserva compatible.

## Velocidad y modelos

Diagnóstico del reparto de ajuste, separado de la reserva. Error de duración estimado, sin verdad física independiente. Ningún umbral cambió.

| Perfil | n | REPS | 3+ FULL motor | 3+ FULL etiqueta | VL publicada/n | n durationError / mediana % | Mediana error relativo % | Mediana error disco % |
|---|---:|---:|---:|---:|---|---|---|---|
| BACK_EXTENSION | 1 | 1 | 1 | 1 | 0/1 | 0/— | — (n=0) | — (n=0) |
| CURL | 8 | 8 | 7 | 8 | 0/8 | 5/9.55 % | 9.55 % (n=5) | — (n=0) |
| DEADLIFT | 2 | 2 | 1 | 1 | 0/2 | 0/— | — (n=0) | — (n=0) |
| DIP | 4 | 4 | 2 | 3 | 0/4 | 0/— | — (n=0) | — (n=0) |
| HINGE | 2 | 2 | 2 | 2 | 0/2 | 1/3.06 % | 3.06 % (n=1) | — (n=0) |
| HIP_THRUST | 1 | 1 | 1 | 1 | 0/1 | 0/— | — (n=0) | — (n=0) |
| LEG_CURL | 1 | 1 | 1 | 1 | 0/1 | 1/15.86 % | 15.86 % (n=1) | — (n=0) |
| LEG_EXTENSION | 2 | 2 | 2 | 2 | 0/2 | 0/— | — (n=0) | — (n=0) |
| LUNGE | 4 | 4 | 3 | 3 | 0/4 | 1/4.01 % | 4.01 % (n=1) | — (n=0) |
| MACHINE_SQUAT | 4 | 4 | 3 | 4 | 0/4 | 1/8.39 % | 8.39 % (n=1) | — (n=0) |
| OHP | 2 | 2 | 1 | 2 | 0/2 | 1/8.30 % | 8.30 % (n=1) | — (n=0) |
| PULL_VERTICAL | 4 | 4 | 2 | 3 | 0/4 | 1/22.15 % | 22.15 % (n=1) | — (n=0) |
| PUSH_UP | 4 | 4 | 3 | 4 | 0/4 | 0/— | — (n=0) | — (n=0) |
| RAISE | 5 | 4 | 4 | 5 | 0/5 | 2/9.48 % | 9.48 % (n=2) | — (n=0) |
| SHRUG | 2 | 2 | 2 | 2 | 0/2 | 2/19.77 % | 19.77 % (n=2) | — (n=0) |
| SQUAT | 6 | 6 | 4 | 4 | 2/6 | 4/2.69 % | 2.69 % (n=4) | — (n=0) |
| STEP_UP | 2 | 2 | 1 | 1 | 0/2 | 0/— | — (n=0) | — (n=0) |
| TRICEPS_EXTENDED_REST | 3 | 2 | 2 | 2 | 0/3 | 0/— | — (n=0) | — (n=0) |
| TRICEPS_FLEXED_REST | 2 | 2 | 2 | 2 | 0/2 | 0/— | — (n=0) | — (n=0) |

En ajuste se publica pérdida de velocidad en 2/59 series; `durationError` se puede estimar en 19/59, con mediana 9,55 %. Es una estimación del modelo de ruido, sin verdad física independiente. Se mantienen κ=6,6, guarda de duración 2,5 %, guarda de barra 10 % y CV inicial máximo 25 %. No se ha aprobado ni aplicado ningún cambio a estos parámetros; la ingeniería inversa no valida un umbral de producto ni garantiza publicar más series.

Los clips de 3–6 repeticiones no están etiquetados como «sin fatiga»: su VL no mide una tasa de falsas alarmas. Tampoco se conoce el diámetro físico real de todos los discos; asumir 450 mm no permite medir falsos avisos de escala. Los m/s conservan «≈» hasta probar un desplazamiento conocido en el teléfono.

La comparación histórica usa tres clips emparejados, sin nueve muestras independientes: full, heavy y ML Kit acertaron los tres conteos. Todos ejecutaron CPU; la GPU solicitada hizo fallback. P50 históricos: full 51,68 ms, heavy 99,45 ms, ML Kit 35,38 ms. Se mantiene full en producción y ML Kit solo en debug; n=3 no justifica cambiar de estimador.

## Validación posterior en el teléfono

El usuario pidió aprovechar después todos los vídeos recopilados y pospuso expresamente esa corrida. No se usa el teléfono en esta entrega. El futuro banco debe inventariar los 1.000 vídeos del corpus, conservar hashes, fuentes y grupos, ejecutarse por lotes reanudables y separar clips aptos para exactitud de los no soportados o no concluyentes usados como negativos. Los 76 clips públicos adicionales del banco se identifican por separado.

Pendientes de Z Flip5: 30/60 fps reales y PTS, GPU/fallback, latencia por fotograma y duración total, memoria/temperatura, HDR/rotación y precisión métrica de la escala con desplazamiento conocido. La cámara sintética del emulador no acredita estas propiedades.

## Límites de entrega

Sin contador en vivo ni voz de conteo (fase 2). Sin push, publicación ni aceptación de release. Se preserva `master` y su trabajo concurrente.

## Fuentes de este estado

Evidencia local: `C:/Users/valen/Documents/KPKNFit/artifacts/video-technique-continuation-20261016/` (logs, XML, hashes y capturas). Informes finales completos: [conteo](GOLDEN_REPORT.md) y [giros](GOLDEN_TIMING_HOLDOUT.md). La comparación estática se resume en [WLAnalysis](WL_ANALYSIS_RE.md); su evidencia completa permanece en la carpeta local indicada allí.
