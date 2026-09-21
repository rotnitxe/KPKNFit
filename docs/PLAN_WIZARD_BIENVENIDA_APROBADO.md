# Transformación del wizard de bienvenida de KPKN Fit

## 1. Resultado que debe entregar el agente

Transformar el wizard existente en una experiencia visual integrada, anterior a Home, que permita:

1. Conocer KPKN mediante un carrusel con capturas reales de sus funciones.
2. Completar un perfil compartido por entrenamiento, nutrición y recuperación.
3. Calibrar el volumen recomendado, incluso si no se crea un programa.
4. Crear un programa personalizado, elegir un protocolo o dejar la creación para después.
5. Configurar opcionalmente referencias nutricionales para registrar alimentos consumidos.
6. Terminar con la explicación, precalibración y reajuste de los rings de **Músculos, Energía y Columna**, incluyendo estimaciones iniciales por músculo.
7. Entrar a una Home coherente con las decisiones tomadas.

**Dirección visual aprobada:** KPKN editorial con momentos inmersivos. Fotografía cuidada, capturas reales, anatomía interactiva, calendarios y gráficos; formularios breves donde sean necesarios.

**Decisiones ya cerradas:**

- Crear un programa es opcional.
- La calibración de volumen forma parte del recorrido inicial aunque se omita el programa.
- Nutrición es opcional. Al omitirla, Home oculta metas y avisos alimentarios; su pestaña sigue disponible.
- Existen dos rutas distintas: **Programas personalizables** y **Protocolos**.
- Se ofrece todo el catálogo publicable existente, con recomendaciones y requisitos comprensibles.
- La progresión inteligente es intrínseca: no aparece como interruptor.
- Los rings constituyen el cierre del recorrido.
- No se preguntan horas de sueño. Energía recoge la percepción actual pertinente al motor.
- Las molestias proceden exclusivamente del catálogo existente.
- La selección de músculos entrenados alimenta una estimación inicial por músculo, identificada como aproximada y reajustable.
- No hay usuarios publicados: no hace falta una campaña de migración. Sí deben preservarse los datos locales de desarrollo.

Este plan se basa en revisión estática del checkout local `2d0cae9e`, cuyo remoto corresponde al repositorio indicado, y en las **48 capturas** proporcionadas. La exploración de código se delegó en subagentes Luna con esfuerzo máximo. No se modificó la aplicación ni se ejecutaron builds o pruebas durante esta planificación.

---

## 2. Punto de partida verificado y cambios necesarios

El repositorio **ya tiene un wizard integrado**. Hay que transformarlo y aprovechar su persistencia, no construir un segundo onboarding.

| Área | Estado actual verificado | Cambio necesario |
|---|---|---|
| Entrada | [MainActivity](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/MainActivity.kt:1048) inicia en Home; [HomeScreen](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/screens/home/HomeScreen.kt:192) navega después al wizard. | Resolver el destino antes de componer Home. |
| Wizard | [SetupWizardModels](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardModels.kt) y [SetupWizardViewModel](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardViewModel.kt) contienen modos, capítulos y borrador. | Convertir los capítulos actuales en un recorrido condicionado por respuestas, con pantallas pequeñas y visuales. |
| Orden | El recorrido completo coloca Rings antes de Nutrición. | Mover la precalibración y su resultado al cierre. |
| Persistencia | [SetupPersistence](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/data/onboarding/SetupPersistence.kt:104) ya tiene commit transaccional e idempotente. Room ya es **v27**. | Ampliar el contrato existente; conservar drafts, revisiones y receipts. No crear tablas duplicadas. |
| Volumen | [ProgramHeroWidgets](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/screens/programdetail/components/ProgramHeroWidgets.kt:485) contiene la sheet y su cálculo. | Extraer motor y contenido reutilizable; presentar pantallas del wizard y persistir un perfil global. |
| Personalización | [SimpleCyclePersonalizer](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/domain/training/SimpleCyclePersonalizer.kt:24) admite días, tiempo, equipo y un foco. | Incorporar split explícito, varios músculos prioritarios y músculos de menor énfasis. |
| Catálogo | [PersonalizedPlanCatalog](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/data/programs/PersonalizedPlanCatalog.kt:49) incluye familias KPKN y protocolos publicados. | Separar sus rutas de presentación y sus reglas de compatibilidad. |
| Progresión | Los planes nativos se crean con `PROPOSE`, pero la autorregulación semanal de [ProgramProgressEngine](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/domain/training/ProgramProgressEngine.kt:798) necesita `sourceRecipe`. | Dar a los programas personalizados una receta nativa persistible y verificar la progresión semanal real. |
| Nutrición opcional | Omitir el plan no basta para ocultar los objetivos predeterminados de Home. | Añadir una elección explícita del módulo y condicionar presentación, avisos y recordatorios. |
| Rings | [InitialRecoveryEvidence](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/data/models/InitialRecoveryEvidence.kt) ya representa una estimación inicial global. | Completar las entradas, corregir respuestas desconocidas y extenderla por músculo. |
| Error del cuestionario | [SetupWizardScreen](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardScreen.kt:379) escribe el tipo de actividad en `intensity` y no muestra su selección. | Corregir el enlace a `activityType` y añadir una prueba de regresión. |
| Molestias | [DiscomfortCatalog](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/data/models/DiscomfortCatalog.kt) y la UI de readiness ya existen. | Reutilizar sus IDs, etiquetas, agrupaciones y selección. |

### Inspiración que sí se toma de las referencias

- La progresión por etapas de `p1` y las pantallas equivalentes de alimentación.
- La selección visual de músculos de `p12–p13`.
- La explicación del split de `p17`.
- La vista previa concreta del programa de `p22`.
- La demostración del producto de `p26`.
- Los resultados explicados visualmente del cierre nutricional.

### Elementos que no se trasladan

- Identidad, tipografía distintiva, ilustraciones o capturas de MacroFactor.
- Opciones keto, low-fat o similares.
- Interruptores de progresión inteligente.
- Preguntas que no tengan un consumidor real en KPKN.
- Estimaciones de grasa corporal mediante una galería nueva de fotografías.
- Pantallas separadas para detalles secundarios como elegir el color del programa.
- Animaciones que finjan análisis científico o esperas artificiales.

---

## 3. Dirección visual y comportamiento común

### 3.1. Sistema visual

**Tesis visual:** una aplicación de entrenamiento cercana y precisa, con fondos oscuros, fotografía editorial y datos que se convierten en visualizaciones comprensibles.

Usar la identidad existente:

- Fondo base `#0F0F0F`.
- Superficies `#1A1A1A` y `#252525`.
- Amarillo del tema para la acción principal y selección.
- Músculos: coral `#C96B5C`.
- Energía: turquesa `#4FA3A5`.
- Columna: lavanda `#9A86C8`.
- Logo vectorial existente `ic_logo_kpkn.xml`.
- Familia tipográfica actual, ampliando su jerarquía dentro del onboarding.

El tema efectivo actual es oscuro. Esta tarea no incluye desarrollar un sistema de temas claro/oscuro.

**Composición:**

- Márgenes laterales de 24 dp, reducidos a 20 dp en anchuras pequeñas.
- Títulos de pantalla de aproximadamente 28–32 sp, con altura adaptable.
- Texto de apoyo corto, normalmente una o dos frases.
- Acción principal persistente al pie, respetando teclado e insets.
- Una decisión principal por pantalla.
- Tarjetas cuando representen una elección; listas y composiciones abiertas para información.
- Glass mediante `KpknGlass`, respetando su composición con `hazeSource`. No replicar manualmente sus parámetros.

### 3.2. Seis composiciones que deben alternarse

| Composición | Uso |
|---|---|
| Fotografía editorial con texto sobre zona despejada | Bienvenida y aperturas de entrenamiento, alimentación y recuperación. |
| Captura real del producto con explicación breve | Carrusel y demostraciones. |
| Pregunta con selector visual | Experiencia, objetivo, tiempo y energía. |
| Anatomía interactiva con alternativa en lista | Énfasis, músculos entrenados y reajuste muscular. |
| Calendario o semana visual | Días, split y vista previa del programa. |
| Resultado gráfico explicado | Volumen recomendado, referencias nutricionales y rings. |

No resolver todas las pantallas con una columna de tarjetas idénticas. Tampoco colocar una fotografía decorativa detrás de todos los formularios.

### 3.3. Movimiento

Implementar con Compose y las dependencias existentes:

- Transición de pasos: desplazamiento corto y fundido, aproximadamente 220–280 ms.
- Selecciones: respuesta visual de 150–200 ms.
- Anatomía: transición del resaltado al cambiar selección.
- Calendario: actualización animada al cambiar días o split.
- Rings: revelado inicial de aproximadamente 600–800 ms y reajustes fluidos.
- Carrusel: desplazamiento manual y movimiento de profundidad muy discreto en la imagen del slide activo.

El carrusel no avanza automáticamente. El usuario puede empezar sin recorrerlo entero.

Respetar la escala de animación del sistema. Con movimiento reducido, conservar toda la información y sustituir desplazamientos por cambios inmediatos o fundidos mínimos.

### 3.4. Navegación y accesibilidad

- Atrás conserva respuestas.
- El progreso muestra capítulos y el avance dentro del capítulo actual.
- Las ramas omitidas desaparecen del recorrido y del cálculo de progreso.
- No mostrar un contador rígido que incluya pantallas que el usuario nunca verá.
- Todos los controles tienen semántica accesible y objetivos táctiles de al menos 48 dp.
- La anatomía dispone de selección equivalente mediante lista.
- No usar exclusivamente color para indicar selección, intensidad o estado.
- Verificar 360 dp de ancho y texto al 200 %.
- El teclado no debe ocultar el campo activo ni impedir continuar.
- Las validaciones aparecen junto al dato y explican cómo corregirlo.

---

## 4. Recorrido completo, pantalla por pantalla

### 4.1. Bienvenida anterior al wizard

Crear un carrusel de cuatro slides. El logo y el botón **«Comenzar»** permanecen visibles.

| Slide | Mensaje principal | Visual obligatorio |
|---|---|---|
| B1 | **Tu entrenamiento, con contexto.** | Fotografía editorial P1 y captura real S1 del entrenamiento en vivo. |
| B2 | **Una semana que encaja contigo.** | Captura S2 del programa y calendario, mostrando sesiones reales. |
| B3 | **Entiende cómo llegas a entrenar.** | Captura S3 de los rings, con sus nombres legibles. |
| B4 | **También puedes registrar lo que comes.** | Captura S4 del registro alimentario; texto visible indicando que alimentación es opcional. |

No incluir datos personales reales en las capturas. No exigir permisos de notificaciones, ubicación, micrófono o Health Connect durante esta bienvenida.

Si existe un borrador, la entrada principal será **«Continuar configuración»**. El carrusel sigue accesible mediante una acción secundaria, sin reiniciar respuestas.

### 4.2. Capítulo «Sobre ti»

| ID | Pantalla | Contenido y comportamiento | Visual |
|---|---|---|---|
| P1 | **¿Qué quieres llevar con KPKN?** | Opciones: «Entrenamiento» y «Entrenamiento y alimentación». Aclarar que crear un programa ahora será opcional en ambos casos. | Dos composiciones seleccionables con fragmentos reales del producto. |
| P2 | **¿Cómo te llamamos?** | Nombre o apodo y unidades. No pedir fotografía de perfil ni permisos. Permitir continuar sin nombre usando el fallback de presentación, sin convertirlo en evidencia de onboarding completado. | Logo y resumen de perfil que se actualiza al escribir. |
| P3 | **Tu punto de partida** | Edad o fecha de nacimiento, nunca ambas; experiencia de entrenamiento una sola vez. Datos corporales disponibles en el perfil se muestran precargados y editables. | Composición tipográfica con métricas; sin siluetas que aparenten inferir el físico. |
| P4 | **¿Qué quieres priorizar?** | «Ganar músculo», «Mejorar fuerza» o «Combinar ambos». Esta elección determina el estilo inicial de calibración. | Ilustraciones existentes de ejercicios y una descripción breve de cada objetivo. |

**Datos corporales:**

- Peso y altura pertenecen al perfil compartido, aunque se editen desde Nutrición.
- Cuando se necesiten para calcular referencias nutricionales, exigir valores confirmados y válidos.
- Si se omite alimentación, no bloquear la calibración de volumen por datos que ese cálculo no utiliza.
- El parámetro sexual requerido por la ecuación nutricional se solicita únicamente si se necesita y falta; explicar su uso concreto.
- Los valores predeterminados de Settings no cuentan como respuestas confirmadas.

### 4.3. Capítulo «Tu volumen de referencia»: obligatorio

Debe existir también en la ruta que omite crear un programa.

| ID | Pantalla | Contenido y comportamiento | Visual |
|---|---|---|---|
| V1 | **Busquemos un punto de partida para tu volumen** | Explicación breve: las referencias servirán para interpretar las métricas y orientar los programas. El estilo se deriva del objetivo ya elegido. | Ilustración existente y una muestra de barras de volumen, sin cifras ficticias personalizadas. |
| V2 | **Cómo entrenas actualmente** | Técnica y consistencia, usando las opciones y escalas reales del calibrador actual. | Selectores amplios con ejemplos breves; dos preguntas relacionadas en una pantalla. |
| V3 | **Tu preparación actual** | Fuerza y movilidad, conservando las escalas y significado del calibrador. | Segunda composición de selectores, acompañada de ilustraciones pertinentes. |
| V4 | **Tu volumen de referencia** | Resultado por grupo muscular, explicación del rango y acceso a editar respuestas. | Anatomía y barras horizontales por músculo. |

Requisitos:

- Extraer el contenido de la sheet; no abrir `VolumeCalibrationSheet` dentro del wizard.
- Conservar la matemática actual.
- No añadir preguntas de sueño, estrés o nutrición a este cálculo.
- No incluir notas libres que ningún motor utiliza.
- No equiparar «fuerza + cardio» con `POWERBUILDER`: el estilo procede del objetivo de fuerza/músculo, no de la modalidad cardiovascular.
- Mostrar las recomendaciones completas, sin sumar aliases de un mismo grupo como músculos independientes.
- Guardar este resultado al completar el wizard, aunque no exista programa.

### 4.4. Decisión de programa

Pantalla: **«¿Cómo quieres preparar tu entrenamiento?»**

Tres acciones:

1. **Programas personalizables**  
   «Adaptamos una propuesta a tus días, equipo y prioridades».

2. **Protocolos**  
   «Elige una estructura definida y revisa sus requisitos».

3. **Lo crearé después**  
   Continúa sin crear un programa vacío.

La elección ocurre antes de pedir días, equipo o split. Quien omite el programa no debe completar preguntas de generación.

### 4.5. Ruta «Programas personalizables»

| ID | Pantalla | Datos y comportamiento | Visual |
|---|---|---|---|
| T1 | **¿Cuándo quieres entrenar?** | Seleccionar días concretos. La frecuencia se obtiene del número de días seleccionados; no se pregunta de nuevo. | Semana interactiva. |
| T2 | **¿Cuánto tiempo tienes por sesión?** | Opciones rápidas y edición dentro del rango actualmente soportado de 20–100 minutos. Diferenciar tiempo disponible de duración estimada. | Selector visual de tiempo y miniatura de una sesión. |
| T3 | **¿Con qué equipo cuentas?** | Presets de gimnasio, casa, máquinas o peso corporal, con equipos editables. Los presets representan IDs reales del catálogo. | Fotografía P2 y pictogramas existentes. |
| T4 | **¿Hay músculos que quieras priorizar?** | Entre cero y tres prioridades. «Desarrollo equilibrado» equivale a ninguna prioridad especial. | Anatomía frontal/posterior y lista accesible. |
| T5 | **¿Dónde prefieres poner menos énfasis?** | Selección opcional de grupos de menor prioridad. Un músculo no puede estar en ambas listas. | La misma anatomía, con una codificación diferente y explicación de mantenimiento. |
| T6 | **Cómo repartir tu semana** | Split recomendado, motivos concretos y acceso a todos los splits publicables. Mostrar el significado en lenguaje sencillo antes del nombre técnico. | Miniaturas de semanas: cuerpo completo, torso/pierna, empuje/tirón/pierna y demás opciones existentes. |
| T7 | **Elige una base para personalizar** | Recomendaciones iniciales y «Ver catálogo completo». Incluir las familias KPKN existentes y plantillas elegibles. | Previews de semanas y ejercicios reales. |
| T8 | **Así queda tu programa** | Sesiones, ejercicios, series, duración estimada, volumen frente a referencia y explicación de las decisiones. | Calendario desplegable, imágenes de ejercicios y barras de volumen. |

**Split personalizado:** permitir editar un patrón de siete posiciones usando etiquetas válidas del catálogo. Debe haber al menos un día de entrenamiento. El patrón debe concordar con los días elegidos.

**Vista previa:**

- Mostrar una sesión expandida inicialmente.
- Permitir revisar las restantes.
- Ofrecer alternativas compatibles por sesión o ejercicio mediante los mecanismos existentes.
- Permitir cambiar nombre dentro de esta pantalla.
- Mantener detalles secundarios de apariencia fuera del recorrido principal.
- No guardar ni activar el programa todavía.
- No presentar como cumplida una prioridad que el materializador no consiguió satisfacer.
- Mostrar problemas de compatibilidad con acciones concretas: modificar equipo, días, tiempo o base elegida.

Texto sobre progresión, dentro de la explicación del resultado:

> «KPKN utiliza tus registros para orientar las siguientes sesiones».

No mostrar un interruptor para activarla.

### 4.6. Ruta «Protocolos»

| ID | Pantalla | Comportamiento |
|---|---|---|
| Q1 | **Explorar protocolos** | Catálogo completo publicable, búsqueda y filtros por objetivo, nivel, frecuencia y duración. Separar recomendaciones de listado completo. |
| Q2 | **Qué exige este protocolo** | Mostrar estructura, frecuencia, semanas, equipo, requisitos y fuente. Explicar incompatibilidades antes de materializar. |
| Q3 | **Tus referencias de fuerza** | Solo aparece si la receta requiere porcentajes o Training Max. Pedir los levantamientos que realmente utiliza. |
| Q4 | **Inicio y distribución** | Asignar el calendario respetando el orden y descansos de la receta. |
| Q5 | **Revisar el protocolo** | Mostrar la estructura real y cómo encaja con las referencias de volumen del usuario. |

Reglas:

- No preguntar músculos prioritarios ni split: no modificarán la receta.
- No deshabilitar un protocolo por el foco muscular elegido en otra rama.
- Comprobar equipo real, no solo mostrarlo como descripción.
- No modificar series, repeticiones, porcentajes, AMRAP, RPE o fases para hacer que parezca compatible.
- Mantener los protocolos sin receta verificable fuera de las opciones seleccionables.
- Conservar atribución, fuente y revisión.
- La calibración de volumen permanece disponible para las métricas, sin escalar automáticamente la receta.

**Training Max pendiente:**

Se permite definirlo después, como admite el flujo actual, pero con un estado explícito de referencias pendientes. La preview conserva `%TM` sin inventar kilogramos. Antes de ejecutar una prescripción porcentual que necesite un dato ausente, el flujo de entrenamiento debe solicitarlo; nunca interpretar su ausencia como 0 kg válido.

### 4.7. Nutrición opcional

Este capítulo aparece únicamente si se eligió alimentación.

| ID | Pantalla | Contenido y comportamiento | Visual |
|---|---|---|---|
| N1 | **Registra lo que tú comes** | Explicar que se calculará una referencia para comparar con los registros reales. No hay elección de dieta ni menú. | Fotografía P3 y captura S4. |
| N2 | **Tu objetivo corporal** | Reutilizar los objetivos soportados por el wizard nutricional actual. Mostrar los datos corporales ya conocidos y pedir únicamente los que falten. | Trayectoria corporal o gráfico existente. |
| N3 | **Tu actividad habitual** | Preguntar la actividad cotidiana que no pueda deducirse. Diferenciar movimiento diario de entrenamientos planificados. | Opciones visuales breves. |
| N4 | **Tu referencia inicial** | Calorías y macros calculados por el motor existente, datos utilizados y explicación de su carácter estimado. Ajustes compatibles con el flujo actual. | Gráfico de macros y resumen visual. |

Condiciones:

- No repetir nombre, edad, altura, peso ni experiencia.
- No tratar una semana futura recién creada como actividad ya realizada.
- No inferir movimiento cotidiano a partir de ir al gimnasio tres días.
- No añadir preguntas de keto, alimentos permitidos, restricciones dietarias o menús.
- No pedir al usuario registrar una comida ficticia para terminar.
- No prometer fechas exactas de resultado.
- Mantener la calibración nutricional posterior existente; esta pantalla entrega la referencia inicial.

Si se vuelve atrás y se desactiva alimentación, sus respuestas pueden conservarse en el borrador, pero no se activa ni persiste un plan nutricional al finalizar.

### 4.8. Cierre: «Tu punto de partida»

Este es el último capítulo operativo. La pantalla final de revisión se integra en el resultado de los rings.

#### R1. Explicar los tres rings

Mostrar los componentes reales, inicialmente sin cifras inventadas:

- **Músculos:** estimación de recuperación muscular, con detalle por zonas.
- **Energía:** percepción y carga general relevantes para llegar a entrenar.
- **Columna:** estimación relacionada con la carga axial y el estado declarado.

Texto de apoyo:

> «Vamos a establecer una referencia inicial. Tus registros reales ayudarán a actualizarla».

No introducir un ring de sueño ni presentar Energía como una medición de horas dormidas.

#### R2. Entrenamiento reciente

Preguntar por lo ocurrido, no por lo planificado:

- Si entrenó durante la última semana.
- Tipo de actividad: fuerza, cardio o mixta.
- Número de sesiones, cuando lo recuerde.
- Intensidad: fácil, moderada, dura o muy exigente.
- Cuándo fue la última sesión.

Usar controles compactos y revelado condicionado. Si no entrenó, omitir las preguntas de carga que no correspondan.

Distinguir expresamente:

- No entrené.
- Entrené.
- No estoy seguro.
- Todavía no respondí.

No sustituir desconocimiento por fuerza, intensidad moderada o siete días de recencia.

Cuando exista historial real, presentar primero un resumen derivado de él y pedir solo información faltante o actual. No duplicar esas sesiones como evidencia declarada.

#### R3. Músculos más entrenados

- Usar anatomía y grupos canónicos de KPKN.
- Permitir selección múltiple, **«Todo el cuerpo»** y **«No estoy seguro»**.
- Explicar que se pregunta por la carga reciente, no por los músculos que desea desarrollar.
- «Todo el cuerpo» selecciona explícitamente todos los pilares.
- Una lista vacía nunca significa cuerpo completo.
- Preguntar la sensación muscular actual mediante la escala cualitativa existente.

La selección alimenta la nueva estimación inicial por músculo.

#### R4. Energía actual

Pregunta principal:

> «Hoy, ¿cómo sientes tu energía?»

Cinco estados, ordenados de sentirse muy bien a muy fatigado, y «No estoy seguro».

El descanso, los hábitos o el ánimo pueden mencionarse como ejemplos para interpretar la pregunta, pero no generan campos separados sin uso. No preguntar horas de sueño ni convertir una respuesta de energía en un registro de sueño.

#### R5. Columna y carga axial

Explicar brevemente la carga axial con ejemplos existentes de sentadilla y peso muerto.

Preguntar, dentro del periodo reciente:

- Si realizó ejercicios con carga axial.
- Con qué frecuencia.
- Qué tan exigentes fueron.
- Cómo siente actualmente la zona de la columna.

Mostrar los detalles solo cuando sean pertinentes. La exposición axial tiene sus propios datos; no hereda automáticamente la intensidad de una sesión que pudo haber sido principalmente de cardio o de tren superior.

No pedir kilos si la estimación existente no los necesita.

#### R6. Molestias actuales

Reutilizar `DiscomfortCatalog` y los patrones del readiness existente:

- Selección por regiones.
- Etiquetas oficiales.
- IDs canónicos.
- Acción «Ninguna».
- Posibilidad de revisar la selección.

«Ninguna» produce una lista vacía, no una molestia llamada `none`.

No generar un catálogo nuevo ni sustituir molestias por diagnósticos.

#### R7. Resultado, reajuste y entrada a Home

Composición principal:

1. Los tres rings reales.
2. Etiqueta **«Estimación inicial»** o **«Sin datos suficientes»**, según corresponda.
3. Resumen breve de los datos utilizados.
4. Acceso al detalle muscular.
5. Acción **«Reajustar»**.
6. Resumen compacto del programa, volumen y nutrición, con enlaces para editar.
7. Botón **«Entrar a KPKN»**.

Reajustes:

- Energía y Columna: controles existentes.
- Músculos: lista/anatomía con ajuste por músculo.
- Modificar un músculo no convierte todos los demás en ajustes manuales.
- Ofrecer «Restablecer estimación» para retirar los cambios manuales del borrador.
- No iniciar aprendizaje adaptativo a partir de este reajuste inicial.

No cerrar automáticamente esta pantalla después de unos segundos. El usuario confirma cuando termina de revisarla.

---

## 5. Contratos y lógica de implementación

Los nombres de tipos nuevos de esta sección son **propuestos**. Los componentes identificados como existentes deben reutilizarse.

### 5.1. Un borrador compartido y respuestas con procedencia

Ampliar `SetupWizardDraft`; no crear ViewModels independientes que mantengan versiones divergentes del perfil.

El borrador debe contener:

- Paso actual mediante identificador estable.
- Elección de alimentación.
- Ruta de programa.
- Perfil compartido y unidades.
- Respuestas de calibración de volumen.
- Preferencias de generación.
- Selección de protocolo y referencias de fuerza.
- Configuración nutricional.
- Respuestas de recuperación.
- Ajustes manuales.
- Revisiones del borrador, catálogo y generación.

Añadir procedencia y estado de confirmación a los datos que pueden venir precargados:

- Declarado.
- Importado.
- Derivado.
- Sugerido.
- Desconocido.

Un valor sugerido no debe presentarse como declarado. Los valores predeterminados de Settings requieren confirmación cuando influyan en un cálculo personalizado.

**Regla de actualización:** editar un dato invalida únicamente sus resultados dependientes.

| Dato modificado | Qué se actualiza |
|---|---|
| Peso, altura o edad | Cálculos que realmente los consuman y preview nutricional. |
| Respuestas de volumen | Perfil global, métricas y generación personalizable. |
| Días, tiempo o equipo | Split compatible y preview personalizable. |
| Prioridades musculares | Selección de plantillas/ejercicios y distribución del volumen. |
| Protocolo | Requisitos, referencias necesarias y preview fiel. |
| Respuestas recientes | Preview de recuperación. |
| Alimentación activada/desactivada | Ruta del wizard y estado final del módulo. |

Cambiar la calibración de volumen no debe reescribir la receta de un protocolo. Cambiar músculos prioritarios no debe alterar las respuestas sobre músculos entrenados recientemente.

### 5.2. Calibración de volumen global

Extraer `buildVolumeCalibration` a un motor puro, propuesto como `VolumeCalibrationEngine`.

La sheet existente y el wizard deben consumir el mismo cálculo.

Añadir a Settings un `VolumeCalibrationProfile?`, con:

- Estilo de entrenamiento.
- `AthleteProfileScore`.
- Respuestas necesarias para reeditarlo.
- Lista completa de `VolumeRecommendation`.
- Fecha de calibración.
- Revisión del cálculo.

Conservar los cálculos actuales: escalas 1–3, suma, umbral existente y límites por músculo. La extracción no debe introducir una fórmula distinta.

**Precedencia para las métricas:**

1. Calibración específica del programa.
2. Calibración global del usuario.
3. Referencias generales existentes, identificadas como generales.

La ausencia de programa no debe eliminar la posibilidad de consultar las referencias personales. Ajustar los lectores actuales que dependen exclusivamente de `Program.volumeRecommendations`.

Para un protocolo, materializar primero la receta fiel y adjuntar después la calibración como referencia de métricas. Añadir una protección explícita para que operaciones posteriores tampoco escalen accidentalmente la receta por encontrar esas recomendaciones.

### 5.3. Generación personalizable

Crear un coordinador puro `OnboardingPlanGenerator`, apoyado en:

- `SimpleCyclePersonalizer`.
- `SessionTemplateSuggestionEngine`.
- `SessionTemplateCatalogPolicy`.
- `SplitApplicationEngine`.
- `TemplateVolumeScaler`.
- Catálogo V2 y validadores existentes.

Su solicitud debe incluir:

- Familia o plantilla base.
- Objetivo y experiencia.
- Días concretos.
- Tiempo por sesión.
- Equipo real.
- Músculos prioritarios.
- Músculos de menor énfasis.
- Split o patrón personalizado.
- Calibración de volumen.
- Revisión del catálogo.

Su resultado debe contener el programa materializado, las estimaciones, las incompatibilidades y explicaciones de personalización.

**Secuencia obligatoria:**

1. Resolver la estructura semanal elegida.
2. Filtrar candidatos por requisitos duros: equipo, configuraciones aprobadas y estructura.
3. Puntuar mediante las políticas existentes, incorporando prioridades y tiempo.
4. Materializar ejercicios y sesiones.
5. Ajustar series con los mecanismos existentes dentro de sus límites.
6. Validar composición, volumen, duración y días.
7. Producir una explicación de lo conseguido y de cualquier objetivo no satisfecho.

**Énfasis y menor énfasis:**

- Normalizar con los identificadores canónicos actuales.
- Las dos listas son disjuntas.
- Menor énfasis reduce la preferencia por trabajo directo y accesorios, manteniendo las reglas de cobertura existentes.
- No eliminar un compuesto necesario únicamente porque también contribuya a un músculo de menor prioridad.
- No aumentar indiscriminadamente todas las series para satisfacer prioridades.
- Resolver empates de forma estable: mismas entradas y misma revisión producen la misma propuesta.

Para patrones personalizados, extender la sugerencia por arquetipos y etiquetas. El camino actual que devuelve vacío para `split.id == "custom"` debe tener una implementación real, no un fallback a otro split.

Las plantillas fuente permanecen intactas. El programa generado conserva su procedencia y se identifica como una personalización KPKN.

### 5.4. Progresión de programas personalizados

Mantener los mecanismos existentes de sugerencia durante el entrenamiento.

Para cerrar la carencia de autorregulación semanal:

- Generar una `TrainingPlanRecipe` nativa correspondiente al programa personalizado.
- Persistir `sourceRecipe` y su revisión.
- Reutilizar las políticas de progresión y autorregulación existentes.
- Mantener el comportamiento interno `PROPOSE` y su confirmación actual.
- No construir un segundo motor de progresión en paralelo.
- Verificar que completar una semana alcance efectivamente el motor y produzca el resultado esperado cuando se cumplan sus condiciones.

El hecho de que el campo diga `PROPOSE` no constituye por sí solo una prueba de funcionamiento.

### 5.5. Nutrición habilitada y fuentes de verdad

Añadir una elección persistida explícita, propuesta como:

`NutritionTrackingChoice = NOT_DECIDED | ENABLED | SKIPPED`

No reutilizar `algorithmSettings.augeEnableNutritionTracking`: ese ajuste tiene otra responsabilidad.

Cuando sea `SKIPPED`:

- No crear plan nutricional ni objetivos derivados del onboarding.
- No mostrar objetivos predeterminados como si fueran personales.
- Ocultar bloques alimentarios y sus minicards en Home.
- Suprimir avisos y recordatorios de comida.
- No considerar nutrición una tarea pendiente.
- Conservar acceso a la pestaña para activar el módulo posteriormente.

El efecto nutricional sobre AUGE debe seguir requiriendo su configuración correspondiente. La elección del módulo no activa silenciosamente todos los ajustes avanzados.

El motor actual ya trata la ausencia de comidas como neutral. Preservar ese comportamiento y cubrir además el caso de registros sin plan ni objetivo explícito: no compararlos con una referencia inventada de 2500 kcal.

### 5.6. Estimación inicial por músculo

Ampliar `InitialRecoveryEvidence` conservando compatibilidad con la versión global existente:

- `muscleScope`: desconocido, selección concreta o cuerpo completo.
- `perMuscleScores`.
- Revisión del contrato.
- Datos explícitos de exposición axial.
- Procedencia de reajustes.
- Ausencia explícita para respuestas desconocidas.

**No introducir una nueva tabla arbitraria de “carga semanal”.** Reutilizar la fórmula de `InitialRecoveryEvidenceFactory`, con sesiones, intensidad, recencia y sensaciones declaradas.

Para Columna, calcular su componente de exposición mediante el mismo criterio existente de carga/recencia, utilizando los datos axiales declarados; no aplicar la carga total de otra modalidad como si toda ella fuera axial.

Para el mapa muscular:

- Normalizar con `getAugeMusclePillarId`.
- `SELECTED`: incluir solo los pilares seleccionados.
- `FULL_BODY`: incluir todos los pilares canónicos.
- `UNKNOWN`: mapa vacío.
- Dar a cada músculo seleccionado la misma estimación muscular inicial derivada de las respuestas generales.
- No inventar diferencias locales de volumen, series o toneladas.
- Las diferencias adicionales se introducen mediante el reajuste explícito del usuario.
- Los músculos no seleccionados permanecen sin evidencia inicial local; no se presentan como recuperados al 100 % por haber sido omitidos.

**Combinación con historial:**

Reutilizar la política existente, que combina deuda inicial con estado real, y aplicarla una sola vez.

- Con cuerpo completo, el agregado puede obtenerse de los estados locales efectivos sin volver a restar el baseline global.
- Con selección parcial, conservar el resultado global general de la Factory y los resultados locales declarados; no fingir que el global es la media de una selección incompleta.
- Sin mapa, conservar el comportamiento global legacy.
- Mantener la expiración actual de 14 días y la política de solapamiento con registros reales.
- Un entrenamiento posterior no elimina automáticamente toda la carga previa declarada.

**Reajustes y molestias:**

- Usar `manualMuscleBatteries`/`manualMuscleOverridesV2` para las claves editadas.
- Corregir la condición actual que trata cualquier override muscular como bloqueo global.
- No escribir un override muscular global cuando solo se ajustó un músculo.
- Las molestias se aplican por el pipeline existente, una sola vez.
- No descontarlas también dentro de `perMuscleScores`.
- El origen onboarding nunca actualiza el cache adaptativo.

La preview debe ejecutar los mismos cálculos que Home sobre un contexto temporal con los datos del borrador. No crear una fórmula de preview independiente.

### 5.7. Paridad

La ampliación de recuperación afecta comportamiento compartido. Replicar el contrato y la política en Kotlin, Swift y Python, con vectores de prueba comunes.

La interfaz nueva se implementa en Android. No se incluye aquí un rediseño completo del onboarding iOS.

No dar por validado el build iOS desde Windows. Entregar por separado la evidencia de código/fixtures y la validación pendiente en un entorno Apple si no está disponible.

---

## 6. Entrada, guardado, reanudación y fallos

### 6.1. Gate anterior a Home

Resolver un estado raíz después de cargar los repositorios:

- Cargando → superficie de arranque.
- Nuevo perfil → bienvenida.
- Draft activo → reanudación.
- Onboarding completado → Home.
- Error de carga → reintento explícito.

No derivar «usuario nuevo» únicamente de que el nombre sea `Usuario`.

Mover las solicitudes de permisos a los momentos funcionales correspondientes. Retener los deep links y el contenido compartido mientras se completa el onboarding; resolverlos después sin saltarse la elección de módulos.

### 6.2. Reutilizar Room v27

Ya existen `setup_drafts` y `setup_commit_receipts`.

Los nuevos campos pueden incorporarse al JSON serializado de Settings, draft y evidencia. **No incrementar la versión de Room por añadir campos dentro de esos blobs.**

Conservar:

- Control de revisiones.
- Detección de conflictos.
- IDs estables.
- `catalogRevision`.
- `commitId`.
- Receipts idempotentes.
- Guardas frente a sesiones en curso.

Actualizar los formatos de backup y sus pruebas.

### 6.3. Commit único

El botón final debe persistir, en una misma transacción:

1. Perfil compartido.
2. Calibración global de volumen.
3. Elección de alimentación.
4. Programa y activación, si corresponde.
5. Plan nutricional y objetivos, si corresponde.
6. Evidencia inicial de recuperación.
7. Molestias y reajustes explícitos.
8. Estado de onboarding completado.
9. Receipt y eliminación del draft confirmado.

Añadir al contrato de commit un payload parcial de bienestar inicial.

No crear un registro de bienestar cuando no haya molestias ni reajustes que guardar. Cuando deba crearse, registrar su origen y los campos efectivamente capturados para que los defaults estructurales del modelo no se interpreten como respuestas de sueño, estrés o ánimo.

Publicar cambios a los repositorios y navegar a Home después de confirmar el éxito persistido.

### 6.4. Casos de recuperación

- Cierre del proceso: reanudar respuestas y paso.
- Doble pulsación final: un solo programa, plan y registro inicial.
- Error de escritura: conservar draft y permitir reintento.
- Cambio de catálogo: invalidar preview y reconstruirla conservando preferencias.
- Cambio de rama: conservar respuestas de la rama anterior en el borrador, sin materializarlas.
- Reanudación otro día: reconfirmar energía, molestias y estado actual; no desplazar silenciosamente la fecha del entrenamiento declarado.
- Reiniciar configuración: crear un nuevo intento sin borrar programas, comidas, historial o receipts previos.
- Datos existentes de desarrollo: no reemplazarlos automáticamente.

---

## 7. Producción de imágenes y capturas

### 7.1. Inventario obligatorio

| ID | Activo | Procedencia | Destino |
|---|---|---|---|
| S1 | Entrenamiento en vivo | Captura real del emulador | Slide B1 y explicación de seguimiento. |
| S2 | Programa y semana | Captura real | Slide B2. |
| S3 | Home con rings cargados | Captura real | Slide B3. |
| S4 | Registro alimentario y resultado | Captura real | Slide B4 y N1. |
| S5 | Métricas de volumen | Captura real tras la implementación | Apoyo visual de calibración. |
| P1 | Entrenamiento editorial | Generación original | Apertura de bienvenida. |
| P2 | Preparación y equipo | Generación original | Apertura de la ruta personalizable y T3. |
| P3 | Comida cotidiana | Generación original | Apertura de Nutrición. |
| P4 | Pausa después de entrenar | Generación original | Apertura del capítulo de recuperación. |
| G1 | Anatomía seleccionable | Recursos existentes y Compose | Prioridades, carga reciente y reajuste. |
| G2 | Rings y gráficos | Componentes reales | Resultados interactivos. |
| L1 | Logo KPKN | Vector existente | Bienvenida y cabecera. |

No generar capturas falsas de KPKN. No generar el logo, los números de los gráficos ni la anatomía funcional.

### 7.2. Prompt de trabajo para capturas

> Prepara un perfil sintético aislado en el emulador Android. Conserva los datos personales y de desarrollo existentes; usa un AVD o perfil de QA dedicado cuando necesites un primer inicio limpio. Compila e instala el APK del código actual y verifica que `com.example.kpkn/.MainActivity` esté visible.
>
> Crea mediante los flujos reales un programa demostrativo coherente y los registros necesarios para que las pantallas tengan contenido. Captura: una sesión en vivo con ilustración y registro de series; la semana del programa; Home con los tres rings completamente cargados; un registro de comida ya analizado y guardado; y las métricas de volumen con su referencia personal.
>
> Usa únicamente datos ficticios y nombres como «Programa de ejemplo». Espera a que terminen los estados de carga. Cierra teclado, diálogos y avisos accidentales. Evita notificaciones personales y elementos de depuración.
>
> Guarda los originales, los recortes utilizados y un manifiesto con pantalla, pasos para reproducirla, revisión del APK y carácter sintético de los datos. No retoques cifras o textos para mejorar la captura.
>
> Conserva suficiente contexto para reconocer la aplicación y realiza el encuadre mediante Compose. Si la interfaz cambia durante el trabajo, repite la captura antes de cerrar la entrega.

Los originales se conservarán como evidencia en una carpeta de artefactos del workspace. Solo las versiones optimizadas necesarias se incorporarán a los recursos de la app.

### 7.3. Prompts para fotografía generada

Usar la herramienta de generación integrada y la skill `imagegen`. Generar originales independientes. Las referencias de MacroFactor sirven para comprender la experiencia, no como material que deba reproducirse.

**P1 — Bienvenida**

> Fotografía editorial realista para la bienvenida de una aplicación de entrenamiento. Persona adulta de constitución atlética natural, preparando una sesión de fuerza en un gimnasio contemporáneo sobrio. Momento espontáneo de concentración, sin posar hacia cámara. Ropa deportiva lisa, sin marcas. Luz lateral suave, negros profundos, tonos de piel naturales y un leve acento cálido. Composición vertical, sujeto en la mitad inferior derecha y espacio oscuro tranquilo en la zona superior izquierda para añadir interfaz después. Equipamiento físicamente plausible, manos y anatomía correctas. Sensación cercana, competente y humana. Sin texto, logotipos, pantallas, métricas, marcos de teléfono ni estética de competición extrema.

**P2 — Entrenamiento y equipo**

> Fotografía editorial de detalle: una persona adulta prepara unas mancuernas junto a un banco de entrenamiento. Mostrar manos, parte del torso y material reconocible, con proporciones y agarres naturales. Gimnasio ordenado, superficies de metal y goma, iluminación lateral suave y profundidad de campo moderada. Composición adaptable a un recorte horizontal de cabecera y a un recorte vertical. Dejar una zona de baja complejidad para el título de la interfaz. Paleta de grafito, negro y reflejos cálidos, consistente con una aplicación de entrenamiento oscura. Sin marcas, texto, equipo imposible, montajes, cifras ni elementos de interfaz.

**P3 — Alimentación cotidiana**

> Fotografía editorial realista de una comida cotidiana servida en una mesa de casa, con ingredientes reconocibles y presentación sencilla. Una mano adulta acerca el plato a la mesa. Vajilla cerámica sin marcas, luz natural suave, textura real de alimentos y ambiente cercano. Evitar apariencia de anuncio de suplementos o dieta restrictiva. Encuadre principalmente cenital con algo de profundidad, composición limpia y una zona oscura o poco detallada para colocar texto fuera de la comida. La imagen representa registrar lo que alguien ya come, sin transmitir un menú obligatorio. Sin calorías escritas, etiquetas nutricionales, básculas, texto, logotipos ni teléfono con una interfaz inventada.

**P4 — Recuperación**

> Fotografía editorial realista de una persona adulta sentada durante una pausa después de entrenar, respiración tranquila y expresión natural. Entorno de entrenamiento sobrio, postura cómoda, cuerpo de apariencia real y ropa lisa sin marcas. La escena debe comunicar atención al propio estado y recuperación cotidiana. Luz lateral suave, fondo oscuro y detalles discretos en tonos fríos. Composición vertical con espacio despejado arriba para la interfaz. Sin cama, reloj de sueño, símbolos médicos, lesiones visibles, gestos dramáticos de dolor, texto, gráficos, anillos flotantes ni dispositivos con datos inventados.

### 7.4. Revisión y entrega de activos

- Revisar anatomía, manos, equipo, recortes y coherencia entre fotografías.
- Rechazar imágenes que compitan con el texto.
- Mantener texto y logo como componentes nativos.
- No incorporar capturas de las referencias a la aplicación.
- Empaquetar todos los activos necesarios para funcionar sin conexión.
- Optimizar fotografías y capturas por separado: preservar especialmente la legibilidad del texto de las capturas.
- Cargar al tamaño de presentación; evitar decodificar todos los originales del carrusel simultáneamente.
- Entregar un manifiesto con origen, prompt, uso, resolución, recortes y nombre del recurso.
- No dejar placeholders como resultado final.

---

## 8. Orden de ejecución para el agente implementador

| Fase | Trabajo | Condición de cierre |
|---|---|---|
| 1. Congelar contratos | Revalidar el checkout, documentar recorrido, estados y dependencias; preservar cambios ajenos. | Especificación técnica alineada con este plan y tests de comportamiento identificados. |
| 2. Fundamentos | Gate raíz, pasos condicionados, perfil compartido, elección nutricional y ampliación del draft. | Primer inicio sin composición de Home; reanudación funcional. |
| 3. Volumen | Extraer motor y contenido, añadir perfil global y actualizar lectores. | Calibración persistida y visible incluso sin programa. |
| 4. Programas | Separar rutas, ampliar personalización, mantener fidelidad de protocolos y conectar progresión nativa. | Previews materializadas y contratos de ejecución satisfechos. |
| 5. Nutrición | Integrar cálculos existentes, eliminar preguntas repetidas y aplicar modo solo entrenamiento. | Activación y omisión coherentes hasta Home. |
| 6. Rings | Corregir entradas, añadir evidencia por músculo, reajuste, molestias y paridad. | Preview y Home coinciden; ausencia y doble conteo cubiertos. |
| 7. Diseño y activos | Implementar composiciones, producir fotografías y capturas, añadir movimiento y accesibilidad. | Recorrido completo con activos definitivos y sin pantallas provisionales. |
| 8. Integración y QA | Commit completo, fallos, reanudación, pruebas de dominio, builds e interacción real. | Matriz aprobada y evidencias entregadas. |

La fotografía puede producirse mientras se implementan los fundamentos. Las capturas finales deben obtenerse después de estabilizar las pantallas que muestran.

Mantener Clean Architecture, dominio Kotlin puro, inyección manual y `StateFlow` de solo lectura. Los cálculos costosos y la carga de catálogos no deben ejecutarse en el hilo principal.

---

## 9. Pruebas y criterios de aceptación

### 9.1. Pruebas de dominio y persistencia

**Volumen**

- Paridad del motor extraído con el cálculo actual.
- Umbral de puntuación y límites por músculo.
- Normalización de aliases y deltoides.
- Persistencia sin programa.
- Backup y restauración.
- Precedencia de calibración específica sobre global.
- Recetas de protocolos intactas al adjuntar referencias.

**Generación**

- Determinismo.
- Frecuencias y familias soportadas.
- Equipo real y configuraciones aprobadas.
- Días concretos y patrón personalizado de siete posiciones.
- Tiempo disponible frente a duración estimada.
- Varias prioridades y menor énfasis.
- Límites de volumen y reglas de composición existentes.
- Errores explicables cuando no hay propuesta válida.
- Progresión semanal de un programa nativo con receta persistida.
- Fidelidad y atribución de todos los protocolos publicables.
- Referencias de fuerza requeridas por levantamiento.
- Ausencia de cargas inventadas cuando falta Training Max.

**Rings**

- Fuerza, cardio y mixta enlazados al campo correcto.
- Desconocido distinto de cero y de respuesta neutral.
- Cero sesiones sin deuda de entrenamiento inventada.
- Recencia declarada, sin defaults silenciosos.
- Carga axial independiente de otras modalidades.
- Músculos concretos, cuerpo completo y selección desconocida.
- Legacy sin mapa.
- Expiración y solapamiento con historial.
- Workout posterior sin doble aplicación del baseline.
- Reajuste de un músculo sin bloquear todos.
- Molestias aplicadas una sola vez.
- Ausencia de aprendizaje adaptativo desde onboarding.
- Paridad de vectores entre Kotlin, Swift y Python.
- Mismo resultado en preview y Home con un reloj de prueba fijo.

**Flujo y commit**

- Omitir programa permite completar el onboarding.
- Omitir nutrición no deja una tarea pendiente.
- Perfil compartido sin preguntas duplicadas.
- Rotación, cierre y reanudación.
- Cambio de rama y de catálogo.
- Doble pulsación final.
- Fallo transaccional y reintento.
- Ninguna entidad activa antes de confirmar.
- Deep links retenidos hasta terminar.
- Home sin metas predeterminadas en modo solo entrenamiento.

### 9.2. Matriz de QA real

| Escenario | Recorrido que debe verificarse |
|---|---|
| Primer usuario, solo entrenamiento | Bienvenida → perfil → volumen → programa personalizable → rings → Home sin alimentación. |
| Omite programa y nutrición | Volumen obligatorio → rings → Home sin programa vacío ni metas alimentarias. |
| Omite programa, activa nutrición | Perfil compartido → volumen → nutrición → rings → referencias activas y programa ausente. |
| Personalización completa | Días concretos, equipo limitado, dos prioridades, menor énfasis y split personalizado; comprobar las sesiones creadas. |
| Protocolo avanzado | Catálogo → requisitos → referencias necesarias → preview fiel → confirmación. |
| Training Max pendiente | Crear protocolo sin inventar cargas; completar la referencia antes de ejecutar la prescripción que la necesita. |
| Recuperación localizada | Seleccionar músculos, exposición axial y molestias del catálogo; reajustar uno y comprobar persistencia. |
| Respuestas inciertas | Elegir «No estoy seguro» en carga o energía; comprobar que no aparezcan datos asumidos como declarados. |
| Recuperación de proceso | Cerrar y abrir en perfil, generación, nutrición y resultado de rings. |
| Fallo al finalizar | Reintentar y verificar una única creación. |
| Uso posterior | Abrir el programa, comenzar una sesión, registrar series, finalizar y comprobar que la precalibración no se convierta en historial ficticio. |

### 9.3. Validación técnica

Desde `android-native/`:

- Ejecutar primero las pruebas dirigidas de onboarding, persistencia, volumen, catálogo, protocolos y recuperación.
- Ejecutar las regresiones relevantes de nutrición y AUGE.
- Compilar `assembleBaseDebug` con el wrapper del repositorio.
- Comprobar también la compilación de la variante Health cuando se afecte código compartido.
- Instalar el APK con conservación de datos y verificar la actividad visible.
- En un perfil poblado, obtener y verificar respaldo antes de reemplazarlo.
- No borrar datos del emulador de uso habitual para simular el primer inicio.

Evidencia mínima de emulador:

- Dispositivo y boot confirmados.
- `com.example.kpkn/.MainActivity` en primer plano.
- Capturas.
- UI dumps.
- Grabación del recorrido principal.
- Logcat sin fallos relevantes.
- Comprobación visual de teclado, tamaños pequeños, texto ampliado y movimiento reducido.

### 9.4. Definición de terminado

El trabajo solo está completo cuando:

- Home no aparece antes del wizard en una instalación nueva.
- La bienvenida muestra KPKN real, con logo y capturas definitivas.
- El recorrido tiene variedad visual y cada visual ayuda a comprender una decisión.
- El volumen permanece disponible al omitir el programa.
- La personalización modifica realmente las sesiones.
- Los protocolos conservan su receta.
- La progresión semanal nativa está conectada y probada.
- Omitir alimentación adapta Home y los avisos.
- Los rings cierran el recorrido, permiten reajuste por músculo y distinguen estimación de historial.
- Las decisiones sobreviven a reinicios y se guardan de forma atómica.
- Las pruebas y los flujos reales tienen evidencia.
- El informe final separa código, pruebas, build, instalación, QA Android y validación de paridad.
- No quedan fotografías provisionales, capturas de terceros, cálculos duplicados ni funciones anunciadas sin conexión real.

