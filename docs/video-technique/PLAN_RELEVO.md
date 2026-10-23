# Plan de relevo · técnica por vídeo y contador de repeticiones al 100 %

Escrito el 22-oct-2026. Es la continuación de la ola VT en la app Android KPKN Fit (Kotlin + Compose). Responde siempre en
español neutro, sin voseo.

**Prioridad vigente del usuario · 23-oct-2026:** hacer primero la entrega pre-Anexo con los gates del plan original.
La aceptación competitiva queda como **anexo posterior a la entrega**, para afinar y demostrar paridad/superioridad;
no añade bloqueos a Pre Release 18. Su protocolo y herramientas se conservan sin iniciar capturas competitivas antes de
entregar. El usuario autorizó subagentes Sol 6 High o Luna 6 Max en lugar de Haiku y trabajo paralelo. La revisión REA del
XAPK Metric aportado busca mejoras concretas; una inferencia estática no prueba precisión ni reemplaza QA. No demorar la
entrega por una reconstrucción de Metric ni por implementar contador/voz en vivo. Se mantienen datos inmutables, ciegos,
cola Gradle, protección del teléfono y ausencia de push. El permiso MiMo conserva su alcance UI/medios/navegación.

## 0. Contexto mínimo

**Worktree:** `C:\Users\valen\Documents\KPKNFit\.claude\worktrees\video-technique-rep-counter-ee5b0d`, rama `feat/vt-ui`, punta
`3596ae3a2`. `master` está en `8ac57c9d4`.

**Lee primero:**
- `docs/video-technique/RELEVO.md`: estado exacto y rutas;
- `docs/video-technique/BANCO_COMPLETO.md`;
- `docs/video-technique/briefs/M4.md`;
- `docs/video-technique/UI_PLAN.md`;
- `docs/video-technique/DECISIONES.md`;
- `/c/kw/tools/GRADLE_GUIA.md`, la cola de Gradle v10;
- `/c/kw/tools/HAIKU_GUIA.md`;
- en la memoria `C:\Users\valen\.claude\projects\C--Users-valen-Documents-KPKNFit\memory\`: `MEMORY.md`,
  `project_video_technique_wave.md`, `reference_apk_delivery_drive.md`, `feedback_release_signing.md` y
  `reference_phone_wireless_debug.md`.

**Qué ya existe en `feat/vt-ui`:**
- grabador con comprobación de encuadre;
- análisis en segundo plano (WorkManager y MediaPipe);
- pantalla «Análisis de técnica»;
- hub en Perfil, con tendencias, comparar y análisis de vídeos de la galería;
- sugerencia «Usar / Corregir» en la sesión en vivo y en el Modo visual;
- motor 9, con parciales, zona, confianza propia y referencia de recorrido;
- base de datos v29, sin migraciones nuevas.

**Cifras actuales:**
- Examen del banco anterior con el motor 9: ALTA acertó exacto 8 de 10 y MEDIA 22 de 22. Solo da número en el 77 % de los clips
  útiles. No llega al nivel del mercado.
- MM-Fit con pose OpenPose, 494 series:
  - exacto el 80,5 % y ±1 el 92,9 %;
  - ALTA exacta el 85 %;
  - tríceps 0/64 (siempre una de menos);
  - zancada 44/62, con 16 ALTA equivocadas.

## Reglas fijas
- **Escritura del código:** el código del repo lo escriben subagentes Haiku 5.5 con effort max. Sonnet u Opus, en effort medium,
  solo si Haiku falla dos veces en la misma tarea por juicio.
- **Gradle:** solo por `/c/kw/tools/gradle_slot.sh`, `gradle_solo.sh` o `kpkn_jvm.sh`.
  - Las pruebas de `domain/**` van por el carril JVM.
  - El carril Android, solo con clases concretas.
  - Nunca `./gradlew` directo, `--stop`, matar java ni dos corridas a la vez.
- **Teléfono (Z Flip5):** solo a través de `/c/kw/tools/phone_run.py`.
  - Nunca `pm clear`, `uninstall` ni tocar `com.example.kpkn.completo` (es la app personal del usuario).
  - Las pruebas de QA van en `.dbg`.
- **Repo y ramas:**
  - No toques otros worktrees ni el checkout principal.
  - Sin push.
  - Commits por rutas, en español, con su línea Co-Authored-By.
- **Firma:** solo con `C:\kw\tools\signed_release.py`. Nunca pidas credenciales.
- **Datos de los bancos:**
  - Los vídeos nunca entran al repo.
  - Las etiquetas y los corpus son de solo lectura.
  - Quien ajusta el motor nunca ve los datos ciegos (fold 4 de MM-Fit, `C:\kw\vt-bench\dev-check-20261021\`); solo recibe
    agregados.
  - El código de producto no depende de clips, fuentes ni etiquetas.
- **Bloqueos:** si una acción queda bloqueada por permisos o por el clasificador, para y avisa. No la intentes por otra vía.
- **Cuota:** al llegar al 94 % de la cuota semanal, para y deja un relevo.

---

## Fase 1 · Cerrar M4b (las parciales en datos e interfaz)
**Estado:** 28 archivos modificados sin commit en `C:\kw\vt-a`, rama `vt/m4b`. Compila (log `C:\kw\logs\vt-m4b-1.log`). Su
corrida 2 de pruebas quedó en la cola; el log es `C:\kw\logs\vt-m4b-2.log`.

1. Mira la corrida 2 con `bash /c/kw/tools/gradle_cola.sh` y el log. No la relances si sigue en cola.
2. Revisa el diff (`git -C /c/kw/vt-a diff`) contra el encargo de `briefs/M4.md` (sección M4b):
   - **Referencia PERSONAL:**
     - se toma de los análisis previos DONE de la misma configuración o del mismo ejercicio;
     - motor ≥ 9, la misma `countSignal` y confianza ALTA o MEDIA;
     - al menos 2 análisis, sin contar el propio vídeo;
     - es la mediana de `fullAmplitude`;
     - también vale en RECOMPUTE.
   - **Textos:**
     - zona STRETCHED → «en estiramiento»; SHORTENED → «acortada»;
     - con ALTA: «+2 parciales (en estiramiento)»;
     - con MEDIA: «+≈ 2 parciales»;
     - con BAJA no se mencionan;
     - chip «8 ·2p» y, con MEDIA, «8 ·≈2p».
   - **«Usar» y «Corregir»:** solo rellenan `partialReps` con ALTA o MEDIA.
   - **Pantalla de análisis:** las parciales marcadas en las barras, con su zona y su %, y una línea en el resumen.
   - **Archivos:** solo los permitidos. No toca `domain/biomechanics` ni Room.
3. Encarga a Haiku que arregle lo que falle y haga los commits (`feat(technique)` / `test(technique)`).
4. Hojas de render al 100 % y al 130 % (`KPKN_RENDER_DIR=C:/kw/shots/vt/ui/m4b`): la sugerencia, el descanso, el chip, el Modo
   visual y la pantalla de análisis. Míralas: nada cortado al 130 % y «≈ 2» con espacio inseparable.
5. Fusiona en `feat/vt-ui` con `merge --no-ff`.

**Hecho cuando:** M4b está en `feat/vt-ui`, con las pruebas verdes y las hojas revisadas.

## Fase 2 · Medición real con MediaPipe sobre MM-Fit
**Estado:** el conductor `C:\kw\vt-mmfit\phone\run_mmfit_phone.py --background` sigue extrayendo en el Z Flip. Había 36 de 616
trazas. Se pausa solo si la batería baja o el teléfono se calienta.

1. **Vigílalo:**
   - `Get-Content C:\kw\vt-mmfit\phone\runall.log -Tail 5`;
   - cuenta `C:\kw\vt-mmfit\phone\traces\*.pose2.json.gz`.
   - Si muere, relánzalo desde `C:\kw\vt-mmfit\phone\` con la orden de `RELEVO.md`, después de comprobar que no sigue vivo.
2. Al terminar, monta `C:\kw\vt-mmfit\bank-mp\`: es `C:\kw\vt-mmfit\bank\`, con las mismas etiquetas, pero apuntando a las
   trazas nuevas y con hashes reales.
3. Corre el evaluador por el carril JVM con `-PappRoot=...\android-native\app` (orden exacta en `RELEVO.md`). Fallan 2 pruebas por
   diseño. Luego `exam_report.py C:/kw/vt-mmfit/report-mp-1 development-clips`.
4. Tabla por ejercicio, OpenPose (`report-1`) frente a MediaPipe (`report-mp-1`), más el tiempo por clip y los estados térmicos.
   Anótala en `BANCO_COMPLETO.md`.

**Hecho cuando:** hay cifras con MediaPipe para las 494 series G1 de MM-Fit.

## Fase 3 · Ronda M5 del motor (fiabilidad «como el mercado»)
**Objetivo:**
- ALTA ≥ 98 % exacta y MEDIA ≥ 90 %;
- más series con número sin perder precisión;
- en los básicos, ≥ 95 % de series exactas.

**Datos:**
- **Ajuste:** las sesiones de los folds 0–3 de MM-Fit (fold = sesión mod 5) más `C:\kw\vt-tune` (folds 0–3), con validación cruzada
  dejando un fold fuera.
- **Ciegos:** el fold 4 de MM-Fit y `dev-check-20261021`. Solo los corre quien no ajusta.

**Pasos:**
1. Diagnóstico con cifras:
   - **Tríceps:** pierde la primera y la última repetición en los bordes de la serie (el primer giro se detecta 2,1 s tarde).
   - **Zancada:** 16 ALTA falsas. Hay que ver si es alternancia de lados, conteo por pierna o cambio de vista.
   - **Banco anterior:** clips útiles sin número.
2. Cambios en `domain/biomechanics`, uno por encargo:
   - los bordes de la serie en la segmentación;
   - el conteo de la zancada, alterno o por pierna;
   - la confianza: que no dé ALTA cuando el periodo o la amplitud son irregulares;
   - la cobertura de los clips sin número.
   - Regla de estabilidad: en cada parámetro, el valor más conservador entre las rotaciones.
3. Sube la versión del motor a 10. Los análisis viejos se recalculan con RECOMPUTE.
4. Mide en ajuste, después en los ciegos, y anota en `BANCO_COMPLETO.md`.
   - Si los ciegos empeoran respecto al motor 9, no se fusiona.
5. Fusiona en `feat/vt-ui`.

**Hecho cuando:** el motor 10 mejora los ciegos, o queda documentado por qué no se alcanza el listón y qué hace falta.
Ese cierre no demuestra paridad competitiva: sus afirmaciones se reservan al anexo posterior.

## Fase 4 · Verificación completa de `feat/vt-ui`
1. `:app:compileBaseDebugKotlin`.
2. **Carril JVM:** todas las pruebas de `domain/biomechanics/**`.
3. **Carril Android, por tandas con clases concretas:**
   - `data.media`, `data.work` y `data.repository.*VideoAnalysis*`;
   - `screens.technique.*`, `screens.profile.*` y `screens.workout.*`, incluidos el Video y el VisualMode.
   - Fallos que ya existían, para no confundirlos: `WorkoutReadinessSheetAdjustTest` y `TechniqueCompareViewModelTest`. Arréglalos
     si son del VT.
4. Pruebas de migración y de Room: no debe haber cambios en v29.
5. Humo con R8: `assembleBaseRelease` con el init de QA (`C:\kw\tools\dbg-r8.init.gradle`). Comprueba las reglas de MediaPipe.

**Hecho cuando:** todo está verde o los fallos están explicados uno por uno.

## Fase 5 · QA de punta a punta en el Z Flip (APK `.dbg`)
Cuando el conductor de MM-Fit no esté usando el teléfono, o entre sus lotes, respetando `C:/kw/phone.lock`:

1. **Grabar una serie desde la sesión en vivo:**
   - el encuadre;
   - REC y el límite de tiempo;
   - 30 y 60 fps reales.
2. **El análisis en segundo plano:**
   - el progreso en la tarjeta;
   - el tiempo por fotograma (objetivo: ≤ 1× la duración del vídeo);
   - el calor;
   - GPU o CPU.
3. **La sugerencia:**
   - «Usar» y «Corregir»;
   - las parciales;
   - la línea del descanso;
   - el chip;
   - el Modo visual.
4. **La pantalla de análisis:**
   - reproductor con capa;
   - 0,25×;
   - paso a paso;
   - bucle por repetición;
   - observaciones;
   - calibrar el disco.
5. **Perfil → «Técnica en vídeo»:**
   - tendencias;
   - comparar;
   - analizar un vídeo de la galería;
   - analizar vídeos anteriores.
6. **Los m/s con disco:** valídalos con un desplazamiento conocido. Si el error es ≤ 0,05 m/s, quita el «≈»; si no, se queda.
   Sin referencia física disponible, conservar «≈» y declarar esa validación pendiente; no fabricar una medición.
   La validación competitiva completa queda en el anexo posterior y no bloquea la entrega pre-Anexo.
7. Capturas con `screencap -d`, a través de `phone_run`, en `C:\kw\shots\vt\qa\`. Registra los fallos y arréglalos con Haiku, en
   3 rondas como máximo.

**Hecho cuando:** todos los flujos funcionan en el teléfono real, con sus capturas.

## Fase 6 · Documentación
- **Actualizar:**
  - `docs/ANDROID_ARCHITECTURE_MAP.md` y `docs/ANDROID_UI_SCREENS_MAP.md`: rutas, pantallas, worker y sidecars;
  - `docs/REPO_STRUCTURE.md`;
  - `docs/video-technique/DECISIONES.md`;
  - `BANCO_COMPLETO.md`, con las cifras finales.
- **Limpiar:** las referencias a `BiomechanicsEngine`, que ya no existe.
- **Privacidad:** comprobar que el análisis es local y medir tráfico atribuible al flujo, incluida MediaPipe. Explicar la
  exportación ZIP manual; no prometer que un vídeo nunca puede salir del teléfono. La inspección del manifiesto no
  sustituye la medición dinámica del APK exacto.

## Fase 7 · Entrega
**Prioridad vigente:** entrega pre-Anexo según los gates originales de Fases 2–6. Las pruebas competitivas posteriores
no la bloquean. Informar los límites reales y conservar≈/beta cuando falte validación; no afirmar paridad/superioridad
antes del estudio del anexo. La firma se mantiene exclusivamente por signed_release.py.

1. Fast-forward local de `master` a `feat/vt-ui`, sin push.
2. **APK firmado «KPKN Pre Release 18»** con `C:\kw\tools\signed_release.py`:
   - cópialo a `G:\Mi unidad\`;
   - con un `KPKN Pre Release 18 LEEME.txt` que diga qué instalar y qué trae;
   - la base sigue en v29, así que los datos del usuario están a salvo.
   - Anota el SHA-256.
3. Informe al usuario: qué hay, las cifras de fiabilidad por ejercicio y lo que queda con «≈» o en beta.

## Fase 8 · Después de la entrega (solo cuando el usuario lo pida o lo avise)
- **APK COMPLETO** (`.completo`, de uso personal) con las otras olas:
  - MFP con T3;
  - rings cuando avisen de 3.3 y 3.4;
  - screens-v2.
  - Cadena de Room acordada: 29→30 calibración, 30→31 nutrición; la siguiente libre es la 32.
  - Nunca `fallbackToDestructiveMigration`.
  - Init `C:\kw\tools\completo-r8.init.gradle`.
- **Prueba física del usuario** con sus series del gimnasio. Sus vídeos se convierten en banco de validación personal.
- **Fase 2 del plan original:** el contador en vivo con voz durante la grabación; continúa después de la entrega.
- **CameraX 1.6.x:** evaluarlo en el teléfono.


## Anexo A · Aceptación competitiva posterior a la entrega

Orden expresa más reciente del usuario: entregar primero y ejecutar después estas pruebas para afinar al nivel de la
competencia. Protocolo en `ACEPTACION_COMPETITIVA.md`; herramientas de evaluación preparadas en paralelo. No convertir
sus FAIL/INCOMPLETE en un nuevo bloqueo de Pre Release 18. Sí impiden afirmar que se ha demostrado igualdad o superioridad.

- Congelar versiones, ejercicios, métricas, verdad, muestras independientes y márgenes antes del ensayo.
- Conteo completo y eventos uno a uno, confianza/cobertura, parciales, velocidad/ROM, pérdida de velocidad, fases/TUT:
  aplicar los criterios de la sección6 de `MERCADO_FIABILIDAD.md`, separados por ejercicio y condición.
- Comparación directa con las apps y versiones realmente disponibles, usando referencia independiente y diseño pareado;
  sin prueba directa, no inferir equivalencia a partir de marketing o de estudios de otra versión.
- Z Flip es el único teléfono confirmado; no hay transductor ni otra referencia disponible. El usuario indicó que puede
  hacer series y acceder a una app comparativa mañana; reconfirmar después de la entrega. No programar capturas ni
  solicitar más equipo ahora como condición de la entrega.
- Muestra insuficiente o referencia ausente: INCOMPLETE. Mantener separados sintéticos, ajuste, controles y prueba física.
- Contador/voz en vivo y mejoras adicionales se evalúan en su fase posterior, sin introducirlos para retrasar esta entrega.
- Usar REA sobre el XAPK aportado como fuente de hipótesis trazables de diseño/algoritmo; no ejecutar el paquete durante
  MM-Fit, no atribuirle precisión por lectura estática ni copiar código/modelos como atajo de integración.
