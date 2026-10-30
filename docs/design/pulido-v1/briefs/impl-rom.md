# Arreglo K2 · ROM conectado de punta a punta (15) y «Expandir» en el modo visual de la tarjeta (17)
Lee antes `briefs/impl-COMUN.md`.

## 15. ROM
Palabras del usuario: «15. En todos los ejercicios, en el sheet de "detalles del registro" sale la opción para habilitar registrar rom, sin embargo temo que no esté bien conectado con la logica de la app, por tanto quiero que lo conectes bien, que esté conectado al editor de entreno y que al habilitarlo, la tarjeta muestre ese slider de ROM, y que efectivamente el ROM cuente dentro de la serie.»
Captura de hoy: `docs/design/pulido-v1/hoy/h17-rom.jpg` (hoja «Detalle del registro»: Repeticiones completas/parciales/con ayuda, interruptor «Registrar ROM», «ROM · % 100», «Nota de serie», «Listo»).
Código a leer (archivos que mencionan ROM): `screens/workout/components/LiveSetDetailSheet.kt`, `SetExecutionCard.kt`, `screens/workout/WorkoutViewModel.kt`,
`data/models/Session.kt`, `screens/sessioneditor/components/ExerciseOptionsBar.kt`, `screens/sessioneditor/SessionEditorUiUtils.kt`, `WorkoutVideoAnalysisController.kt`
(el análisis de vídeo también mide ROM), `services/workout/WorkoutVoice*` (comandos de voz con ROM) y busca `rom`, `Rom`, `ROM`, `rangeOfMotion` en `domain/` y `data/`.
Haz primero el MAPA: dónde nace el dato (plan del ejercicio en el editor / interruptor de la hoja), dónde se guarda por serie (`SetRecord`/`LogSet`…), qué lo lee (volumen,
progreso, análisis, resumen, exportación, historial) y qué NO lo lee aunque debería. Luego deja el flujo coherente:
1. **Editor de entreno**: una opción por ejercicio «Registrar ROM» (en la barra de opciones/configuración del ejercicio, donde ya vive su vecina) que se guarda en el plan
   (modelo + persistencia; si cambia el esquema de Room, **NO migres**: Room está CONGELADO en v31; guarda el flag dentro de un campo JSON/lista ya existente de la serie o
   del ejercicio, o en el modelo serializado del plan — documenta dónde y por qué en el informe).
2. **Tarjeta en vivo**: si el ejercicio tiene ROM activo, la tarjeta muestra el **slider de ROM** (0–100 %, 100 por defecto) en el hueco de «Parciales, ayuda y ROM» SIN cambiar
   el tamaño de la tarjeta (ley: la tarjeta de la serie nunca cambia de tamaño); si no está activo, no hay slider. Estilo del slider vecino de «Esfuerzo».
3. **Hoja «Detalle del registro»**: el interruptor «Registrar ROM» y el campo reflejan el MISMO estado (activarlo desde la hoja lo activa también para esa serie/ejercicio como
   hoy decida la lógica; define la regla: ¿interruptor por serie o por ejercicio?, elige lo coherente con el editor y anótalo).
4. **El ROM cuenta dentro de la serie**: queda guardado en el registro de la serie y se usa donde corresponde (resumen de la serie «4 reps · ROM 80 %», historial/Registros, y el
   cálculo que ya use parciales/ayuda para el volumen efectivo o la calidad: no inventes una fórmula nueva si ya hay una; si no la hay, deja el dato registrado y mostrado y
   documenta qué métrica debería usarlo). Voz y análisis de vídeo que ya escriban ROM deben seguir escribiéndolo en el mismo campo.
5. Tests JVM del área (ViewModel/modelos) actualizados a mano.

## 17. «Expandir» en el modo visual de la tarjeta
Palabras del usuario: «17. En el modo visual de las tarjetas no aparece la opción para expandir la vista.»
Ley aprobada del 16-oct: «Modo visual» con «Expandir» encima de todo. En `LiveSessionListView.kt` (línea ~1184) ya hay un comentario: «Modo visual se abre en su vista expandida,
como en la tarjeta; cada "Ampliar" la vuelve a abrir (pedido del usuario, 29-oct)». Busca la cara «Modo visual» de la TARJETA (`SetExecutionCard.kt`, `SetCardTechniqueBack.kt`,
`screens/workout/technique/`, `WorkoutMediaThumb`…) y por qué la acción de expandir no aparece (condición oculta, capa tapada, tamaño, estado sin vídeo): arréglalo para que la opción sea
visible y funcione en todos los estados de la cara (sin vídeo, grabando, con análisis…) sin cambiar el tamaño de la tarjeta, y deja claro cómo se vuelve.

Archivos permitidos: los de ROM listados arriba, `SetExecutionCard.kt`, `LiveSetDetailSheet.kt`, `SetCardTechniqueBack.kt`, `screens/workout/technique/**`, modelos y tests asociados.
NO toques `WorkoutScreen.kt`, `LiveSessionListView.kt` ni `WorkoutHeaderBar.kt` (los edita otro agente a la vez).
