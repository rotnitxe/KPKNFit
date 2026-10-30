# Arreglo K1 · pantalla de la sesión en vivo: carga intermedia (1) y modo Lista (19)
Lee antes `briefs/impl-COMUN.md`.

## 1. Quitar la pantalla intermedia «Cargando sesión de entrenamiento…»
Palabras del usuario: «1. Cuando inicio cualquier sesión de entreno hay una pantalla intermedia SIN NINGUN PUTO SENTIDO que dice "cargando sesión de entreno", quitala.»
Captura de hoy: `docs/design/pulido-v1/hoy/h01-cargando.jpg` (spinner amarillo, «Cargando sesión de entrenamiento…», botón «Cancelar carga»).
Código: `screens/workout/WorkoutScreen.kt` ~línea 398: `if (session == null) { BackHandler… Box(fondo 0xFF0D1117) { CircularProgressIndicator + Text + OutlinedButton("Cancelar carga") } return }`
con `val session = uiState.session` (línea ~315). Investiga el flujo REAL: quién navega a la sesión en vivo (Inicio «Empezar», Program Detail, widgets, deep links,
`DeepLinkRouter`), qué hace `WorkoutViewModel` para cargar la sesión (DB, plan, readiness…) y cuánto tarda cada tramo. Objetivo: **que el usuario nunca vea esa pantalla**.
Opciones (elige la más robusta y que no parpadee): (a) cargar/crear la sesión ANTES de navegar y navegar cuando esté lista, manteniendo en la pantalla de origen un estado
de pulsación breve (sin texto «Cargando…»); (b) si hay que navegar ya, pintar desde el primer fotograma el armazón de la sesión en vivo (fondo negro de la app, cápsula del
header y esqueleto de la tarjeta, sin spinner ni texto ni «Cancelar carga»); (c) combinación. Si la carga puede FALLAR (sesión inexistente, error de DB), conserva un estado de
error honesto con salida (volver) — eso no es una pantalla intermedia, es un error. El color `0xFF0D1117` no es de KPKN (la app es negro puro): no lo dejes visible.
Revisa `WorkoutBenchmark`/`RepairBenchmarkTrace` y tests (`src/test`, `src/androidTest`) que dependan de ese texto o de ese estado y actualízalos a mano.

## 19. Modo Lista: desvanecido en los extremos y píldora superior flotante en vidrio
Palabras del usuario: «19. En la sesión en vivo modo lista, aplicaste un efecto desvanecido arriba y abajo, sin embargo, en la parte inferior el efecto está demasiado arriba, cortando mucho antes del tamaño de la pantalla y además la pildora superior con el nombre de la sesión no está funcionando como flotante, debería permitir que los elementos pasen por abajo y tener un efecto blur, el desvanecido es a los extremos, no antes, es para refinar ese término.»
Captura de hoy: `h18-lista-vivo.jpg` (el usuario rodeó en azul la cápsula superior y el borde inferior donde el desvanecido empieza muy arriba).
Código: `screens/workout/LiveSessionListView.kt` (modo Lista), `WorkoutHeaderBar.kt` (la cápsula «Legs II · Semana 1 · 0:21 · ⋯»), `components/FeedbackScrollFade.kt`,
`WorkoutV2Body.kt`, `components/WorkoutUiTokens.kt` y cualquier `drawWithContent`/`verticalGradient`/`graphicsLayer(compositingStrategy)` que haga el desvanecido.
Hecho esperado: (1) el desvanecido inferior mide pocos dp (~24–32 dp) y vive pegado al borde inferior REAL de la pantalla (por encima de la barra de gestos/navegación según
los insets), no a media altura ni delante de un hueco negro; el superior igual, pegado al borde superior bajo la barra de estado/cápsula; (2) la cápsula superior (nombre de
sesión · progreso · cronómetro · ⋯) es una isla **flotante en vidrio con desenfoque real** (Haze, como `KpknTopIsland`/`ProgramFloatingBar`): el contenido de la lista pasa por
debajo y se ve borroso detrás, sin que la lista empiece debajo de ella con un corte (la lista usa `contentPadding` superior para que al reposo no quede tapado el primer elemento);
(3) comprueba que la vista Tarjetas NO cambia de aspecto (solo toca lo del modo Lista y lo compartido que sea necesario). Mantén el comportamiento de autoscroll y de
descanso minimizado de la Lista (decisiones del Refinamiento 02 en `C:\Users\valen\Documents\KPKNFit\artifacts\refinamiento-02-qa\parity\DECISIONES.md`: léelas antes de tocar).
Ojo: la cápsula puede ser compartida con la vista Tarjetas; si ya es vidrio allí y solo falta registrar la lista como fuente (`hazeSource`/`Modifier.haze`), basta con eso.

Archivos permitidos: `screens/workout/WorkoutScreen.kt`, `WorkoutViewModel.kt` (solo si hace falta para la carga), `WorkoutHeaderBar.kt`, `LiveSessionListView.kt`,
`components/FeedbackScrollFade.kt`, `components/WorkoutUiTokens.kt`, navegación (`navigation/Navigation.kt`) si el punto 1 lo exige, y los tests que cubran esto.
NO toques `components/SetExecutionCard.kt` ni `LiveSetDetailSheet.kt` (los edita otro agente a la vez).
