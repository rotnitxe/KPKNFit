# demo · Datos de demostración y guion de grabación (Sonnet medium)

Worktree `C:\kw\gu-demo`, rama `guia/demo`. Plan: «Fase 3», puntos 1–3.

## 1. Siembra (solo `src/debug/java/com/example/kpkn/debug/`)
Amplía `DemoDataPlan.kt` (puro) y `DemoDataSeeder.kt` para que el vídeo «Progreso + Cuerpo» tenga algo que enseñar:
- 10 semanas de entrenos terminados con progresión visible en 3 básicos del programa activo (p. ej. +2,5 kg cada 1–2 semanas) y un récord en la última semana; ids fijos `demo-…` (relanzar sustituye, no acumula). Respeta los 8 entrenos actuales o intégralos.
- 8–10 semanas de pesajes hacia un objetivo de peso (reutiliza la forma de `descent` de `BodyTrendHarnessActivity.kt`) y el objetivo corporal si el modelo lo permite sin Room nuevo.
- Mira cómo leen los datos la pantalla de progreso de ejercicio (`screens/progress/`) y Cuerpo (`screens/nutrition/BodyProgressScreen.kt`) para que se vean bien: récord marcado, curva subiendo, tendencia y objetivo.
- Pruebas JVM puras de `DemoDataPlan` (si ya hay `DemoDataPlanTest` en `src/test`, amplíala).

## 2. Guion reproducible (`scripts/welcome/`)
- `README.md`: cómo regrabar (instalar `.dbg`, `DemoSeedActivity --ez pro true`, orden de tomas, montaje, comprobación).
- `takes.md`: para cada vídeo (entreno, nutricion, progreso), la secuencia de acciones de `C:\kw\tools\phone_run.py` (lee su `main()` para ver el formato de acciones: `tap`, `text`, `rec:<nombre>:<s>[:<WxH>]`, `recwait:`, `pull:`…) a 720x1760, con coordenadas del Flip (1080×2640) que tendrás que dejar marcadas como «a confirmar en la sesión».
- `build_welcome_videos.py`: adapta `C:\kw\tools\build_welcome_videos.py` a 720×1760 SIN recorte ni relleno; tapa en negro la franja de la barra de estado (alto como constante, ~73 px a 720 de ancho); CRF 20, `faststart`, sin audio; pósteres WebP ≤ 45 KB; al final `ffprobe` (720×1760) y `cropdetect` (sin barras) con fallo si no cuadra. Rutas de entrada/salida por argumento.
  Guiones: Entreno = Inicio → Empezar → registrar una serie → descanso. Nutrición = isla «Registrar comida» → escribir «dos huevos con tostadas y un jugo de naranja» → Analizar → guardar → sube el día. Progreso = progreso de un ejercicio con récord → Cuerpo con la tendencia hacia el objetivo.
- Comprueba el script con un vídeo sintético hecho con ffmpeg (`testsrc` 720x1760) en el scratch, no en el repo.

## Pruebas
`kpkn_jvm.sh` no cubre `src/debug`: usa `gradle_slot.sh ... :app:testBaseDebugUnitTest --tests "<DemoDataPlanTest>"` y además `:app:assembleBaseDebug` en la misma corrida (lo necesito para el teléfono). Indica la ruta del APK.
