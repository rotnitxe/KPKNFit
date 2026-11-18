# wel-a · Bienvenida: marco 9:22 y página Progreso + Cuerpo (Sonnet high)

Worktree `C:\kw\gu-wel`, rama `guia/wel`. Plan: «Fase 3», punto 4–5. Los vídeos nuevos se graban después; tú preparas el código.

## Qué hacer (`screens/onboarding/SetupWelcomeScreen.kt`, `SetupEntryScreen.kt`, `screens/onboarding/welcome/**`, `res/raw/keep.xml`)
1. Proporción: la pantalla del teléfono toma la del vídeo, una única constante 720/1760 (9:22). El marco (`WelcomeShellPhone.kt`) deriva ancho de alto con ella; el radio de esquina y el bisel se mantienen proporcionales.
2. El `PlayerView` (`WelcomeVideoPlayer.kt`) se coloca al tamaño real de la pantalla del teléfono, fuera de `WelcomeScaledScene` (sin doble escalado); póster con la misma geometría. Si la proporción del vídeo no cuadra con la constante, que se vea entero (Fit) y no recortado.
3. Barra de estado dibujada en Compose sobre la franja superior (alto = 73/1760 del alto de pantalla): hora «9:41», señal, wifi y batería en tinta clara, sin texto de notificaciones; el agujero de la cámara centrado dentro de esa franja.
4. Reajusta `welcomePhoneHeight` para el teléfono más estrecho: que en el Flip (≈360×880 dp útiles) el teléfono sea lo más grande posible sin empujar texto, segmentos ni botón (comprueba con la prueba de layout a 360×780, 360×880 y 411×914).
5. Página 3: id `progreso`, etiqueta «PROGRESO», título ≤ 40 y mensaje ≤ 130 que digan que el progreso es de entreno Y físico (propón: «Tu progreso, dentro y fuera» / «Tus marcas en cada ejercicio y tu cuerpo semana a semana: peso, tendencia y objetivo.»), descripción TalkBack del vídeo, acento nuevo que no sea el `columna` de RINGS (usa uno de la paleta actual de la app; documenta cuál). Vídeo `welcome_progreso.mp4` + póster `welcome_progreso_poster.webp` (aún no existen: el código debe funcionar sin ellos).
6. TalkBack de Nutrición sin «anillos». Comentarios con «Recuperación» o RINGS fuera.
7. Borra las escenas Canvas del diseño viejo (`WelcomeEntreno*`, `WelcomeNutricion*`, `WelcomeResultsScene`, paleta de RINGS de `WelcomeScene` si queda sin uso) y sus pruebas. Si falta el par vídeo+póster de una página, se ve una pantalla neutra (fondo de la app con el logo tenue), no un hueco.
8. Botón «Continuar» en vez de «Comenzar» cuando hay un borrador recuperable (`SetupEntryScreen` ya los lee).

## Pruebas
- Nuevo `WelcomeVideoAssetsTest`: lee la caja `tkhd` de cada `res/raw/welcome_*.mp4` en Kotlin puro y exige 720×1760; y las dimensiones de cada póster WebP (cabecera VP8/VP8L/VP8X). Con los vídeos viejos (720×1488) FALLARÁ: es lo esperado; dilo en el informe.
- Actualiza `WelcomeShellLayoutTest`, `WelcomeShellCopyTest`, `WelcomeVideoPlayerTest`, `SetupWelcomeScreenTest`. Una sola corrida Android al final con estas clases.
