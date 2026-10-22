> ENTREGA PRIVADA · Este relevo llega como adjunto `Rings-v4-cloud-private.zip`, no mediante una rama publicada. GitHub es público y la revisión automática rechazó publicar el WIP; no se publicó nada. El archivo `rings-v4.bundle` transporta el historial local y este paquete, con prerrequisito público 381123d0a59ed8b3afdafe3f6353f1f9b26a1424. Desde el clon cloud de rotnitxe/KPKNFit, verifica `git bundle verify /ruta/rings-v4.bundle`, luego `git fetch /ruta/rings-v4.bundle refs/heads/codex/rings-v4-cloud-20261022:refs/heads/codex/rings-v4-cloud-20261022` y trabaja en esa rama local. Esto restaura exactamente la integración ebf870f92 más los archivos de relevo, WIP y arnés. No hay que subir esta rama al repositorio público ni obtenerla desde GitHub. La indicación previa de rama de traslado en el texto se refiere a la rama dentro del bundle privado. No publiques código/WIP en GitHub sin consentimiento específico; deja el resultado en el chat cloud y sus artefactos/diff privados.
# Rings v4 · continuidad autónoma en Codex Cloud

Autorización humana vigente, 22-oct-2026: «Sigue este trabajo en la nube de Codex. TE AUTORIZO a que el proceso lo lleves a cabo tu de forma independiente y autónoma, tomando las decisiones que más recomiendes por mi».

Objetivo: completar el alcance aprobado de Rings/AUGE, con evidencia separada de código, pruebas JVM, pruebas Android, QA físico y autorización de merge. Responder en español. El trabajo y las compilaciones locales están detenidos para liberar RAM; ejecutar en el entorno cloud, sin reactivar procesos del PC.

## Entrada y preservación del trabajo

Esta rama de traslado parte de `feat/rings-v4` en `ebf870f9248cca7544fed1ff66452c44a61f2b70`. Contiene las integraciones reales de 3.3 y 3.4, sus correcciones de compilación y los informes parciales. Los tres paquetes aún sin integrar viajan como parches completos, incluidos sus archivos nuevos, en `docs/rings-v4/cloud-transfer/`; sus SHA-256, bases y listas exactas están en `manifest.json`.

Los parches son WIP, no código aprobado ni validado. Aplicarlos en ramas por paquete con `git apply --3way <ruta>`, revisar conflictos y completar lo existente. Integrar con `--no-ff` después de las compuertas pertinentes. El parche 3.2c-2 sustituye `SystemTauRoundTripTest`: conservar su motor real al resolver el puente legado de una línea de 3.3. Los otros dos parches pueden coincidir en `AugeModels.kt` y `AugeViewModel.kt`: preservar la procedencia y el mutex de 3.3/3.4, la proyección y la tarjeta.

No tocar `master`, ni subir una versión firmada, ni afirmar QA del teléfono sin evidencia. La autorización para autonomía no revoca la compuerta previa de consentimiento explícito para merge a master. No introducir Room/migraciones (v29), iOS, backend, Health Connect, cuestionario 24 h, sliders de parámetros en Ajustes ni medición de sueño. Siguen aplazados BT1, AUGE Pro, 3.6, 4.1, 4.2 y S1–S14 salvo la tarjeta de 3.5. Domain puro, sin nuevos imports Android.

## Lectura autoritativa

1. `docs/rings-v4/cloud-transfer/SOLICITUD_ORIGINAL.txt`: solicitud íntegra de relevo y orden de fases.
2. `docs/rings-v4/RELEVO.md`, `ESTADO.md`, `DECISIONES_AUTORRESPONDIDAS.md`, `BRIEF_COMUN.md`, `BRIEF_PARALELO.md` y los briefs 2.4, 3.2, 3.3, 3.4, 3.5.
3. `docs/audits/2026-10-rings-v4/PLAN.md`, informes 2.6 y sus tablas, y `reports/M0-fallos-previos.txt`.

Los avisos históricos de cuota, ejecutores gratuitos y «orquestador no escribe código» proceden de la sesión anterior y no deben bloquear la autorización actual: ejecutar con el Codex del entorno, tomando decisiones recomendadas y registrándolas. Las rutas Windows/cola compartida son referencias del equipo local; no existen necesariamente en cloud. `ESTADO_LOCAL_AL_PAUSAR.md` es una copia de evidencia histórica, no una orden de mantener pausada esta nueva tarea cloud. Mantener el alcance y las compuertas numéricas.

## Hechos de la continuación local

- Base del relevo: 215c74d04, master 8ac57c9d4. Integraciones posteriores: c51fc4615 (3.3), b02467685 (3.4), ebf870f92 (informes/estado).
- 3.3: 8a0846a0b explicita `Unit` en `saveWellbeingAndCache` y en su `withTransaction`; se revisaron funciones completas del VM, cierre, puente de voz y pruebas Room. Se conserva F-06: log y anillos del cierre persisten en dos fases; readiness invierte Columna con `_snapshot`; sin reintento automático.
- 3.4: bd8331746 corrige imports Application/LocalDate, contexto opcional, ViewModelStore y espera inicial de la prueba de rendimiento. Revisadas firmas reales de ProgramRepository, enumerados de descarga y bloqueo compartido. No se han ejecutado con éxito las nuevas pruebas Android. No confundir el EMA de suavizado de fatiga con los actualizadores de τ retirados.
- Se cancelaron por orden humana cinco corridas: 3.2c-2, 3.5, 2.4, contraste master y Android 3.3/3.4. No hay XML final ni éxito nuevo que atribuirles. No quedaron colas propias activas.
- Antes de cancelar 3.2c-2 se observó un fallo REAL: `criterioB_convergencia_200UsuariosPorEscenario`, factor 0,75, mediana EKF **0,2382 > 0,15**. El resultado no cierra el criterio. No cambiar LEARNER_* ni umbrales para hacerlo pasar.
- La revisión detectó que algunos casos WIP de 3.2 son insuficientes: apilado con τ verdadero igual al prior, hitos que pueden quedar sin muestras y cobertura de las ocho observaciones informativas que debe verificarse. Completar los oráculos; no reducir tamaños ni ocultar censuras. Mantener comparación EMA contemporáneo y escalas explícitas.
- 3.5 WIP: métricas EWMA por parámetro/escala y fuente, datos persistidos de freeze, medición común en `AugeLearner.update`, prior completo en `SnapshotObservationModel`, publicación en snapshot, tarjeta Compose de lectura, tests de fronteras/dedupe/JSON/motor adverso y pruebas Compose. Todo requiere revisión y ejecución. Asegurar normalización de métricas, metadatos completos de diagnóstico, persistencia/atomicidad en las dos fuentes y que la guardia no enmascare los siete criterios de 3.2 (`trackSkill=false` cuando se mide el EKF aislado).
- 2.4 WIP: proyector 336 h, barrido 0,25 h y bisección 0,125 h, caché compartida por publicación y cortes de eventos, tipos nullable explícitos, retirada de fórmulas de horas, prueba TTC de barrido. Falta verificarlo, cubrir toda la matriz, endurecer cruces positivos por target, completar causalidad de eventos/anclas y regenerar el golden SOLO en horas. No aceptar una tabla llena de cruces triviales a 0 ni una tabla de horas todas ausentes como validación numérica.

## Ejecución cloud sin depender del PC

Se incluye `tools/rings-jvm/`, exportación de configuración, exclusiones, shims que FALLAN si se ejecutan y guardián de escritura del arnés local validado. No contiene caches, JDK, credenciales, APK ni datasets generados. Su portabilidad aún no se ha ejecutado: preparar JDK 17 y dependencias estándar en el entorno cloud, revisar las rutas, y usar:

```sh
bash tools/rings-jvm/run.sh --tests com.example.kpkn.domain.auge.AugeLearnerEkfTest
python3 tools/rings-jvm/summarize.py
# Selección por clases durante iteración; completa sin --tests al cierre.
bash tools/rings-jvm/run.sh -Pr4SimFull=true
```

El arnés reutiliza el wrapper versionado de `android-native/`, no necesita PowerShell, `/c/kw` ni la cola compartida. Conserva las mismas exclusiones de cobertura: no compila UI, Room, repositorios, Robolectric ni tres clases de frontera documentadas. Revisar sus listas; no declarar cobertura Android por pasar aquí. Se puede adaptar la infraestructura al Linux cloud sin cambiar las fórmulas ni los oráculos del producto. Toda prueba debe producir recuento >0.

Para Android preparar el SDK/JDK que requiera el proyecto; desde `android-native/`, `bash gradlew --no-daemon --console=plain :app:testBaseDebugUnitTest --tests <clase>`, solo Base. En este entorno cloud aislado no aplica la cola local de Windows. Limitar concurrencia/heap a los recursos reales y evitar pipelines de test duplicados. No ejecutar builds en el PC. Si faltan dependencias o acceso a repositorios, tratarlo como limitación de entorno y seguir todo lo independiente, dejando pasos reproducibles.

## Alcance restante y aceptación

1. Cerrar 3.2c-2: criterios (a)/(b)/(g), ida y vuelta real, Columna mostrada bloqueada, tablas EMA/EKF Pectorales/Energía/Columna cruda en cuatro escenarios, observaciones presentadas/admitidas/censuradas, ligero y full. No recalibrar constantes para aprobar.
2. Verificar Android 3.3/3.4 y reanclajes (`AugeWriteMutexTest`, `SessionEditorAugeCharacterizationTest`, `RingsUiCopyConsistencyTest`, `RingAdjustTest`, FinishRingEdits y cobertura de snapshot), corrigiendo todos los defectos propios. Corregir filtros por nombre de clase real si el paquete histórico no coincide.
3. Completar/verificar 3.5: β=0,20, pares previos a aprender, freeze por parámetro tras ≥8 si Eadapt >1,15·Eprior, JSON/reinicio/desborde de anillo, ambas fuentes, evento posterior a commit y tarjeta sin controles/efectos. Versiones y escalas separadas, cero falsa precisión.
4. Completar/verificar 2.4: mismo motor mostrado, umbrales 70/85/90, músculos/globales/articular, apilado, curvas mixtas y anclas/evidencia; oráculo directo 5 min, error ≤0,5 h en cada caso y cruces positivos por target; actualización golden una vez con diff exclusivamente de horas.
5. Regresión JVM completa/full y tanda Android integrada. Contrastar fallos ajenos con el master exacto 8ac57c9d4 en checkout limpio; no atribuirles origen previo por intuición. Lista completa en solicitud original. Revisar el diff global, retirar código muerto residual F-62, τ duplicados, relojes implícitos y truncamientos.
6. Actualizar informes ≤400 palabras y tablas separadas, ESTADO, decisiones, índice de auditoría, arquitectura y tabla de los 32 paquetes (hecho/aplazado). Registrar cada decisión recomendada sin preguntas bloqueantes.
7. El QA físico del Flip5, su regla de 20 min, coordinación y aviso a VT requieren el equipo/teléfono local. No simularlos ni declarar «100 %» si faltan. Dejar un protocolo exacto y artefactos listos; no intentar acceder al pipe Windows desde cloud ni publicar avisos de éxito no verificado.

Continuar de manera autónoma hasta completar todo lo ejecutable en cloud, sin terminar tras un mero plan o una primera compilación. Corregir y repetir comprobaciones justificadas. Si una compuerta numérica no se puede satisfacer sin cambiar la especificación, conservarla fallando, investigar la causa raíz y documentar los resultados y límites; nunca debilitarla ni esconderlo con rollback. Entregar cambios/commits revisables y evidencia, con lo físico pendiente claramente separado. No fusionar master.
