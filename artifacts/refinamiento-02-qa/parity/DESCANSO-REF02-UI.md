# Descanso Ref02 · lo que pinta la interfaz nueva (para tests)

Código: `android-native/app/src/main/java/com/example/kpkn/screens/workout/components/RestRef02.kt` (piezas), `RestLiveCard.kt` (tarjeta), `RestFeedbackCard.kt` (feedback en tarjeta + hoja de molestias `RestRef02DiscomfortsSheet`), `WorkoutRestOverlay.kt` (`RestTimerOverlay`, pantalla completa), `RestOverrunLayer.kt` (tiempo extra), `RestArcade.kt` (juegos), `../WorkoutPostExerciseFeedbackHost.kt` (`PostExerciseFeedbackPanel`).

## Tarjeta de descanso (`RestLiveCard`)
- Contenedor tag `rest-card`. Kicker (tag `rest-kicker`): «DESCANSO» (o «DESCANSO · SUPERSERIE», «DESCANSO · RONDA», «DESCANSO · APROX.», «DESCANSO · LADOS»). Expandir: contentDescription «Expandir descanso», tag `rest-expand`.
- Eclipse tag `rest-eclipse`; tiempo tag `rest-eclipse-clock` con texto «m:ss»; debajo «Descansando» o «En pausa». Semántica fusionada del eclipse: contentDescription «<Descansando|En pausa>, m:ss».
- Controles: tags `rest-minus` («Restar 15 segundos»), `rest-pause` («Pausar descanso» / «Continuar descanso»), `rest-plus` («Sumar 15 segundos»); textos «−15 s» y «+15 s».
- SIGUIENTE (tag `rest-next`): «SIGUIENTE», «<ejercicio> · serie N» y la dosis («95 kg × 4–6»); sin siguiente: «Fin de las series».
- Fila del sugerido (tag `rest-suggestion-row`): «Descanso sugerido» + «m:ss» + › (abre la hoja «Descanso sugerido», tag `rest-suggestion-sheet`, botón «Aplicar m:ss» tag `rest-suggestion-apply` que llama a onUseAdaptive, y «Mantener descanso actual»); si ya está aplicado: «Sugerido aplicado»; sin sugerencia: «Descanso del plan» + el plan (o el total).
- Pie: «Jugar mientras descansas» (tag `rest-play-games`). Al tocarlo: tag `rest-card-games` con la cabecera de juegos.
- YA NO EXISTEN: «de 4:09», «· manual», «· sugerido», «Plan de sesión · …», forma de RINGS («Reloj de descanso: Torre/Pista/Espiral»), fila de vídeo (`rest_video_line`), «Saltar descanso» dentro de la tarjeta.

## Juegos (`RestArcade`)
- Cabecera: botón atrás (contentDescription «Volver al descanso», tag `arcade-back`), título «Jugar mientras descansas» o el juego, reloj tag `arcade-rest-clock`.
- Selector: filas tags `rest-game-runner`, `rest-game-flappy`, `rest-game-shooter`. En tarjeta, pie «Volver al descanso» (tag `arcade-return-rest`). En pantalla completa no hay ese pie: el pie de la pantalla es «Terminar descanso» (tag `rest-end`).
- Juego: «Reiniciar juego» (tag `rest-game-restart`), acción (tag `rest-game-input`: «Saltar»/«Volar»; en el escuadrón `rest-game-left`/`rest-game-right`), «Otro juego» (tag `rest-game-other`). YA NO EXISTEN: «El descanso sigue corriendo», «Saltar descanso», «Volver».

## Pantalla completa (`RestTimerOverlay`)
- Tag `rest-fullscreen`. Arriba: eyebrow (tag `rest-full-eyebrow`) «PAUSA ENTRE SERIES» («PAUSA ENTRE APROXIMACIONES» en aproximación; «DESCANSO + FEEDBACK» con feedback y descanso; «FEEDBACK DEL EJERCICIO» con feedback sin descanso); título (tag `rest-full-title`) = ejercicio; minimizar contentDescription «Minimizar a tarjeta», tag `rest-minimize`.
- Reloj: «Una pausa antes de la siguiente.», eclipse (tag `rest-eclipse`, además `lr1g-aro`), controles (mismas tags), fila del sugerido, bloque ÚLTIMA | SIGUIENTE (tag `rest-full-context`: «ÚLTIMA» + «100 × 8 · RIR 2»; «SIGUIENTE» + «<ejercicio> · N» + dosis), «Jugar mientras descansas» (tag `rest-play-games`), pie «Terminar descanso» (tag `rest-end`, llama a onSkip).
- Sin scroll. YA NO EXISTEN: «DESCANSO» como rótulo, «Ajustar tiempo», «PLAN DE SESIÓN», módulo «Última serie» con insignias, «Saltar series restantes…» (onSkipExercise ya no se pinta aquí), experiencia de primera vez, relator, vídeo.
- Con feedback: reloj pequeño (tag `rest-feedback-clock`: «m:ss», «de m:ss · el reloj sigue»), el panel de feedback y «Terminar descanso».

## Tiempo extra
- Pantalla completa (`RestOverrunOverlay`, tag `rest-overrun`): eyebrow «PAUSA ENTRE SERIES», «Tu descanso se ha cumplido.», eclipse lleno con «+m:ss» y «Tiempo extra», controles (−15 y pausa desactivados; +15 llama a onExtendSeconds(15) o, si es null, a onExtend), «Necesito +30 s» (tag `rest-overrun-extend`, onExtend) y «Continuar entrenando» (tag `rest-overrun-start`, onStart).
- Tarjeta (`RestOverrunCard`, tag `rest-overrun-card`): kicker «DESCANSO · TIEMPO EXTRA», eclipse «+m:ss» «Tiempo extra», pie «Necesito +30 s» (tag `rest-overrun-extend`) y «Continuar» (tag `rest-overrun-start`). YA NO EXISTEN: «TIEMPO EXTRA» como rótulo grande, «DESCANSO CUMPLIDO», «Empezar la serie N», «sobre 4:09».

## Feedback (tarjeta `RestFeedbackCard`, completa `PostExerciseFeedbackPanel`)
- Tag del cuerpo `rest-feedback-body`. «EJERCICIO TERMINADO» (o «SUPERSERIE TERMINADA») + nombre del ejercicio (o «Superserie»).
- Por ejercicio: «Calidad técnica» (con varios: «Calidad técnica · <ejercicio>»), valor tag `rest-feedback-quality-<exerciseId>` cuyo texto es «8/10»; rango tag `rest-feedback-slider-<exerciseId>` con semántica de progreso 1..10 (ajústalo con `performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }`), contentDescription «Calidad técnica», stateDescription «8 de 10»; «Por mejorar» / «Muy buena».
- Fila «Molestias» (tag `rest-feedback-discomforts`) con «Sin seleccionar» o los títulos elegidos: al tocarla se abre la hoja «Molestias del ejercicio» (tag `rest-discomforts-sheet`) que contiene el `DiscomfortPicker` de siempre (buscador, chips destacados, avisos del motor, fichas). Todo lo del buscador exige abrir antes esta hoja.
- Molestias previas: «<etiqueta> · reportada antes» con «Sigue» y «Se resolvió» (antes «Resuelta»).
- Acciones: «Ahora no» (tag `feedback-defer`) y «Guardar feedback» (tag `feedback-save`). Sin defer disponible solo «Guardar feedback».
- La tarjeta: tag `rest-feedback-card`, kicker «m:ss · DESCANSO» + expandir.
- YA NO EXISTEN: «¿Cómo te salió?», «/ 10 · limpia», «/ 10 · irregular» (la palabra), módulo «Molestias previas relacionadas», buscador visible sin abrir la hoja, «Ejercicio terminado · Descanso 0:42».
