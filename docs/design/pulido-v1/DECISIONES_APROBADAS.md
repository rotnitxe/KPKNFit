# Decisiones aprobadas para implementar (29-oct-2026)

Fuente: el usuario revisó el lienzo «Pulido KPKN v1» y respondió: «Me gustaron todos excepto el roadmap… Lo demás aprobado», y después eligió **B + luz verde a Gradle y al teléfono**.
Lo que el usuario no cambió se implementa con las recomendaciones de la pestaña «Resumen» del lienzo (decisiones que tomé por él y respuestas por defecto a lo que le tocaba confirmar). Si algo de aquí choca con el lienzo, **manda el lienzo**; si el lienzo no lo contempla, se elimina de la app.

## Reglas del usuario que mandan sobre todo lo demás
- **1:1 al pie de la letra** («99,999 % igual»): incluye sombras, vidrio, resplandores, animaciones y transiciones. `PROTOCOLO_1A1.md` dice cómo se comprueba. Nada de montar el diseño nuevo encima de la pantalla vieja: lo que la pantalla vieja tiene y el lienzo no, se elimina.
- **Las mejoras son el sistema**: sin interruptores «beta» ni «modo sombra» en Ajustes. **Refinar, no quitar** funciones alcanzables (salvo lo que el usuario nombró para eliminar).
- **Room congelado en v31**: nada de migraciones. Lo nuevo va en JSON del plan o en Ajustes.
- Gradle SOLO por la cola (`/c/kw/tools/gradle_slot.sh`, carril Android; carril JVM para dominio): `GRADLE_GUIA.md`. Una compilación a la vez. Teléfono solo con la regla de 20 minutos (`phone_run.py`, solo `com.example.kpkn.dbg`).
- Textos de la app en español (tú, tono cercano). Solo flavor `base` (`assembleBaseDebug`, `compileBaseDebugKotlin`).

## Roadmap (punto 14)
**B en pantallas de 820 dp de alto o más y A en las demás.** B = 132 dp con la tira de segmentos que se desliza (arrastre con vibración de 10 ms por segmento, segmento provisional de 18 dp); A = 112 dp, dos bandas. La «Ruta» (hoja «Recorrido de la sesión») es común y conserva todo lo que ofrece hoy el despliegue de la barra. Las escenas `lv-road-*` (pestaña «Roadmap») son el contrato.

## Decisiones tomadas por el usuario o por defecto
| Área | Decisión |
|---|---|
| Editor | Barra inferior: anillo de minutos + «Añadir» partido (toque = catálogo, flecha = ejercicio / cardio / grupo) + Asistente como icono con insignia; «Reglas» pasa a ser la 4.ª pestaña del Asistente y tocar los minutos abre Asistente › Reglas › Tiempo. «Eliminar serie» gana «Deshacer». RIR de 0 a 5. Los kg de corporal, lastre y asistido siguen estimándose con el récord. Atrás y ⋯ son círculos de vidrio de 48 dp. |
| Asistente | Pestañas en línea fina con indicador. «Aplicar» solo en Valores. Sugerencias gana «Deshacer» y «Descartar». Volumen por músculo: la cifra de siempre. Equilibrio, frecuencia y fatiga no se construyen. Los dos motores de tiempo se unen más adelante. |
| Catálogo | Texto honesto: «Crear una copia» (marcada, la segura) o «Sustituir mi programa». Coan–Philippi, Smolov y Smolov Jr con aviso. Nombres editoriales. Sin volumen por músculo en la ficha. «Deshacer» solo tras sustituir. |
| Cardio · editor | El panel «Avanzado» desaparece entero (sin migrar datos). Avisos Beeps / Voz / Vibra se guardan por cardio también en Intervalos. Ritmo solo donde es min/km. «Al inicio / Al final» → rótulo «CARDIO · AL FINAL» con flecha. «+» del rótulo y «Cardio» en «Añadir» crean otro cardio. |
| En vivo · tarjeta | ROM: vale para esta sesión con interruptor «Guardar también en el programa»; **solo se anota y se muestra** (no excluye de PR ni de progresión). Rango 10–100 % con zonas en 65 y 95. Sin entrada directa de repeticiones. El destello del esfuerzo solo en la tarjeta. |
| Cardio en vivo | Tercera página «Datos» (antes «Resumen»). Sin «Conectar reloj». Parciales por km no se muestran. Terminar cardio sigue por pausa → Terminar. |
| Técnica | El detalle completo tiene scroll interno. «Cancelar análisis» descarta. Sin detección de vídeo oscuro. Lectura de velocidad siempre con los datos. «Reanalizar» visible sin repeticiones. |
| Comida · Cuerpo | Valor atípico: más de 2,5 kg en 7 días o menos. El botón de Cuerpo no se compacta al hacer scroll. Permiso del micrófono: «no guarda el audio». Tope de dictado 25 s (el prototipo dibuja 15 s: **se corrige el prototipo, no el código**). |
| Suplementos | **Catálogo de 11** (sin ashwagandha, hierro y melatonina). Aviso de techo con «Registrar igual». Etiquetas de evidencia sí. Proteína en polvo cuenta como suplemento y suma a macros. La pila vive en Ajustes. Inicio: máximo 3 botones y «+N más». |
| Ajustes | Sale «Descanso por defecto» (queda 90 s fijo) y «Iniciar descanso al registrar una serie» (siempre activo). «Mi material» sale de Ajustes (el dato queda en el asistente). «Unidades» y «Recordatorios» entran como pantallas nuevas. Voz con valores fijos. La copia .db pide confirmación y borrar todo exige mantener pulsado. |
| Reparto | Se conecta el motor existente (`SplitRedistributor`) a Program Detail: catálogo con descripciones, vista previa Antes/Después con arrastre, revisión por sesión y por músculo, Deshacer. **Por fases**: primero esto; las piezas de motor que faltan (bloqueo de sesiones hechas, término de estabilidad, día preferido, decisiones, recorte por tiempo) van en la fase siguiente. Las 35 descripciones nuevas se usan tal cual salvo que el usuario las cambie. |
| En vivo · más | Cronómetro y temporizador dentro de la hoja del tiempo de sesión (Sesión · Cronómetro · Temporizador). «Guardar imagen» solo con icono. Cápsula de vidrio solo en Lista. Anillo de Duración sin objetivo = vuelta de reloj de 60 min. Sesión completada sin scroll en niveles A/B/C. |

## Valores que difieren entre el código y el lienzo (se alinean al lienzo)
Roadmap 112/132 dp (no 138); banda de ROM de 36 dp (hoy 26 dp: la tarjeta no cambia de tamaño porque sustituye a la fila «Parciales, ayuda y ROM»); tope de dictado 25 s (se corrige el prototipo).

## Arreglos directos ya escritos en la rama (reconciliar con el lienzo)
Carga de sesión sin pantalla intermedia; Lista con desvanecido de 28 dp y cápsula de vidrio; ROM de punta a punta y «Expandir» del modo visual; dictado de comida; píldora del editor en vidrio y botón flotante de Cuerpo. Son punto de partida: cada paquete los iguala al lienzo.

## Decisiones de implementación (30-oct-2026, tomadas por el orquestador porque el usuario no está; revisables)
| Tema | Decisión | Por qué |
|---|---|---|
| Pares «casi iguales» del catálogo (20, `paridad/CIMIENTOS_GRUPOS.md`) | **No se unifican.** Cada preset conserva su valor; ninguna escena del lienzo aprobado cambia. | El usuario aprobó el lienzo tal cual y pide 1:1; unificar `menu`/`aviso`/`popover` o `flotante`/`caida-8-28` movería sombras aprobadas. Si más adelante quiere coherencia, es un cambio de lienzo y de código a la vez. |
| Valor exacto frente a preset canónico | El preset **nombra y calibra**; lo que se dibuja es **el valor exacto de la fila** de la matriz. La API de profundidad y de movimiento admite el canónico y un valor exacto distinto (`copy`/parámetros). El canónico se usa tal cual solo si la fila coincide. | La tolerancia del catálogo llega a ±2 dp, ±4 dp de desenfoque y ±0,067 de alfa: sirve para agrupar, no para redondear en la app. |
| Vidrio en Android < 12 | Sin desenfoque de fondo (velo de respaldo, como hoy en `KpknGlass`); la calibración del vidrio es para Android 12+ (el Flip5 lo es). | Haze 1.7.2 solo desenfoca desde API 31. |
| Hojas de calibración de caída (8 pantallas) | Se dejan así. | Compactarlas solaparía halos y estropearía la medida. |
| Bucles de 6 s y 15 s | Solo se calibra su primera vuelta y el periodo exacto. | Son decorativos. |
| Curvas por tramo | En CSS la curva de `animation-timing-function` rige **cada intervalo** entre fotogramas clave; en `keyframes {}` de Compose `using` rige el tramo que empieza en ese fotograma. El motor de movimiento debe reproducir la semántica de CSS y verificarlo con fotogramas. | Trampa clásica que hace que `lv-pop`, `cv-pop` o `ed-delta` se vean distintos. |
