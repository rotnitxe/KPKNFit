# Program Detail v1 · rediseño con el lenguaje KPKN v2 (2026-10-13)

Encargo del usuario: «ese mismo rediseño [de Home] pero para program detail, nutrición, cuerpo (para cada aspecto, no solo la cara
principal)». Más tarde añadió: «nivel top mundial, que supere en diseño a Lyfta, MacroFactor Workouts, Hevy». Lenguaje común en
`docs/design/KPKN_UI_V2.md`. Mapa del código actual: `C:\kw\analysis\maps\programdetail.md`.

Program Detail es la pestaña **Entreno** de quien tiene un programa activo: la segunda pantalla más vista después de Home.

## 1. Qué falla hoy
| Zona | Problema | Gravedad |
|---|---|---|
| Identidad | Unos 110 colores fijos, 233 tamaños de letra sueltos, 0 usos de Syne con intención e iconos de Material. Parece otra app | 🔴 |
| Cabecera | Un degradado de 4 colores fijos que ignora la portada del programa. El nombre se guarda en cada tecla (escritura por carácter). Las baterías AUGE se calculan y nadie las pinta | 🔴 |
| Jerarquía | Una columna sin lazy con todo compuesto. Pestañas de texto sin indicador. El cambio de pestaña no anima | 🟡 |
| Semana | Tarjetas `Card`/`ElevatedCard` anidadas. «~N min» = series × 3, cuando el resumen ya usa el estimador real | 🔴 |
| Estructura | Un editor de 3.000 líneas con `OutlinedButton`, fechas ISO escritas a mano, emojis y «Templates» o «break» en inglés | 🔴 |
| Volumen | 18 `Card`/`Surface`, barras sin animar y estados de color fijos | 🟡 |
| Vacíos | Sin bloques, la pantalla no tiene salida: el `return` impide componer el editor | 🔴 |
| Diálogos | `AlertDialog` de Material para el 1RM y para borrar, y `FilterChip` amarillos | 🟡 |
| Código muerto | ≈2.300 líneas: ProgressView, HistoryView, MetricsCard, widgets del héroe… | 🟢 |

## 2. La pantalla, de arriba abajo
1. **Fila superior**, sobre el negro: atrás (`KpknIconButton(ChevronLeft)`) y más opciones (`More`: editar programa, duplicar,
   historial, eliminar).
   - Al hacer scroll aparece la **isla superior**, con la animación de Home: atrás, nombre del programa, el punto de estado y
     `More`.
2. **Cabecera del programa**, sobre el negro:
   - **Póster** de 88 × 116 dp con radio de 16 dp y filete `moduleBorder`.
     - Muestra la identidad de portada del programa (`CoverStudioArt` con `coverIdentityFor(programId, templateId)`), o la foto si
       `coverImage` es una URL.
     - Respira solo si se ve y no hay movimiento reducido.
   - **A su derecha:**
     - el eyebrow con el estado: «ACTIVO» con un punto `done`, «PAUSADO» con `energia` o «BORRADOR» con `textFaint`;
     - el nombre en `display` (Syne ExtraBold 26, máximo 3 líneas);
     - la meta en `caption`: «12 semanas · 4 días por semana · Fuerza».
   - **Progreso:** regla de semanas segmentada a todo el ancho.
     - Las hechas en tinta, la actual en `musculo` y las futuras en blanco al 12 %.
     - Debajo, en `caption`: «Semana 3 de 12 · Acumulación».
     - En un programa simple: «Semana que se repite · ciclo 5».
   - **Acciones:**
     - principal contextual: «Empezar Torso A» si hoy hay sesión, «Continuar sesión» si hay una en curso y «Activar programa» si
       no está activo;
     - `KpknIconButton` de pausa o reanudar;
     - `KpknIconButton(Edit)`, que abre la hoja **Editar programa**.
3. **«Sobre este programa»:** módulo plegado con la procedencia en una línea y dos líneas de notas, y «Ver más», que abre una hoja.
   Conserva `plan_details_info_button`.
4. **Pestañas segmentadas:** «Semana · Estructura · Volumen».
   - Control de 44 dp con forma de píldora.
   - La pestaña elegida va con relleno de tinta y texto negro, y el indicador se desliza con resorte.
   - Al hacer scroll se queda pegado bajo la isla superior, como otra isla de vidrio.
   - El contenido cambia con `AnimatedContent`: 12 dp hacia el lado de la pestaña y 220 ms.

### Pestaña Semana
- **Módulo «Fases»** (solo en programas por bloques): la ruta del programa en horizontal.
  - Cada bloque lleva su eyebrow con el color de su fase: Acumulación = `musculo`, Intensificación = `energia`,
    Realización = `mente`, Descarga = `columna` y Competición = `danger`, con el icono Flag.
  - Debajo, las semanas como círculos de 40 dp: la actual con anillo de tinta y la elegida rellena de tinta.
  - Una pulsación larga abre el menú de vidrio con «Copiar semana» y «Pegar semana».
- **Avisos y propuestas:**
  - el aviso de transición (descarga, 1RM, autorregulación) es un módulo teñido con sus acciones en píldoras;
  - las propuestas de AUGE, TM y progresión van en un módulo con una fila por propuesta y «Aplicar» o «Descartar».
  - Conserva todas sus etiquetas (`program_proposals_card`, `native_progression_*`).
- **Cabecera de la semana:** «Semana 3 · Acumulación» en `title` y el nombre y la descripción de la semana, si los hay.
  - `KpknIconButton(Edit)` abre la hoja de la semana.
  - `KpknTextAction("Cambiar reparto")` abre SplitView como hoja.
- **Días:** un módulo por día, empezando por el día de inicio del programa.
  - **Fila del día** (64 dp):
    - a la izquierda, la fecha: «LUN» en eyebrow y «13» en Syne Bold 22;
    - en el centro, la sesión: nombre en `bodyStrong` y «6 ejercicios · 55 min» en `caption`. Los minutos salen de
      `SessionDurationEstimator`, nunca de series × 3;
    - a la derecha, el estado: check `done`, punto `musculo` si es hoy o nada.
  - **Al tocar**, se despliega (`AnimatedVisibility`, 220 ms):
    - los ejercicios con su dosis, en el formato de la sesión compacta de Home;
    - y las acciones: «Empezar» (principal de 44 dp), «Editar» (secundaria) y `More` (eliminar, restaurar desde el plan, confirmar
      la sesión opcional).
    - Conserva los textos y descripciones que buscan las pruebas instrumentadas.
  - **Descanso:** fila de 56 dp con «Descanso» en `textFaint`.
  - **Varias sesiones el mismo día:** apiladas dentro del módulo, con `divider`.
  - **Arrastrar para reordenar:** se mantiene. El módulo levantado gana una sombra suave y escala 1,02.
  - Conserva los anclas `training.semana`, `training.sesion` y `training.empezar`.

### Pestaña Estructura
- **Módulo «Herramientas»:** una rejilla de 3 × 2 con baldosas de icono y etiqueta:
  - Calendario;
  - Fechas clave;
  - Loops («Ciclos»);
  - Ruta;
  - Plantillas;
  - Historial (con su contador y el tag `macrocycle_history_button`).
  - Cada baldosa abre su hoja de vidrio.
- **Módulo «Calendario del programa»:** inicio, fin, competición (con cuenta atrás) y días de entreno. «Editar» abre la hoja del
  calendario.
  - Las fechas se eligen con el calendario de mes propio del wizard (C4a), nunca escribiendo fechas ISO.
- **Bloques:** un módulo por bloque.
  - Cabecera: nombre, insignia de fase y «N semanas», con `More` para renombrar, duplicar o eliminar.
  - Dentro: los mesociclos como filas y sus semanas como círculos.
  - Al final: `KpknTextAction("Añadir bloque")`.
- **Sin bloques:** la página muestra Estructura con «Añadir el primer bloque» como principal. Se acaba la pantalla sin salida.
- **Hojas** (todas de vidrio v2, con piezas KPKN):
  - calendario y calendarización simple: recuperar, empezar ciclo, pausa entre ciclos;
  - fecha clave;
  - ciclos (Loops);
  - ruta avanzada, con la línea de tiempo animada y el ajuste a la competición;
  - plantillas y protocolos: reemplazar o copiar;
  - historial de copias: restaurar;
  - diálogos de borrado y de conversión a avanzado.
- **Idioma:** español en todo («Plantillas», «Pausa»), sin emojis. Los emojis de `LoopEngine` pasan a iconos KPKN Line.

### Pestaña Volumen
- **Módulo «Volumen de la semana»:**
  - cabecera con el selector de semana (`ChevronLeft` y `ChevronRight`) y «Calibrar»;
  - una fila por músculo:
    - nombre;
    - barra de 8 dp con las zonas sombreadas (por debajo del mínimo efectivo, óptima y por encima del máximo recuperable);
    - la marca del valor actual, que se dibuja animada;
    - «14 series» tabular y el estado en su color (`textMuted`, `done` o `danger`).
  - Orden: primero las prioridades del usuario.
- **Módulo «Resumen»:** series totales, reparto empuje, tirón y pierna en una barra apilada, y las molestias.
- **Vacíos honestos:** «Activa tu programa para ver el volumen» y «Aún no hay sesiones en esta semana», cada uno con su acción.
- La invitación a calibrar es un módulo teñido de `energia`.

### SplitView («Cambiar reparto»)
- **Hoja a pantalla completa:**
  - catálogo de repartos en módulos, cada uno con su diagrama de la semana: 7 casillas con el patrón de cada día;
  - «Comparar» pone el actual y el elegido lado a lado;
  - «Aplicar» muestra antes el impacto («Se moverán 3 sesiones») con confirmación.

### Hojas y diálogos sueltos
- **1RM:** la hoja usa la regla de marcas del wizard (`LiftMarksPicker`/`MarkRuler`) para sentadilla, banca y peso muerto, en vez
  de tres campos de texto.
- **Calibrar volumen:** las opciones como segmentos KPKN, no como `FilterChip`.
- **Copiar semana:** filas de radio propias.
- **Borrar sesión:** diálogo KPKN con «Eliminar sesión» en `danger` y «Cancelar».
- **«Editar programa»** (sustituye a «Colores de portada»):
  - nombre y descripción con «Guardar»: se acaba la escritura por tecla;
  - el **estudio de portada**: una rejilla de los 10 estilos × 3 paletas de CoverStudio, con vista previa en vivo. Es una
    personalización que ningún competidor tiene.

## 3. Datos y honestidad
- Los minutos de una sesión salen siempre de `SessionDurationEstimator`.
- `ProgramMode` desconocido: sin etiqueta, en vez de caer a «Hipertrofia».
- El nombre se guarda al confirmar, no en cada tecla.
- Sin programa todavía cargado: la pantalla muestra un estado de carga (esqueletos de módulo), no «Programa no encontrado» a los
  1,5 s.

## 4. Paquetes (ola P, subagentes Haiku 5.5)
| Paquete | Qué | Depende de |
|---|---|---|
| P0 | Limpieza del código muerto (≈2.300 líneas, cada borrado confirmado con Grep) y de los parámetros sin uso del héroe | — |
| P1 | Fila superior e isla, cabecera con póster y regla de semanas, acciones, «Sobre este programa», pestañas segmentadas pegajosas y hoja «Editar programa» con el estudio de portada | Home v1.1 (I9) |
| P2 | Pestaña Semana: fases, avisos y propuestas, cabecera de la semana y días con sesiones desplegables | P1 |
| P3a | Estructura: herramientas, calendario del programa, hojas de calendario y fecha clave con el calendario de mes | P1 |
| P3b | Estructura: bloques, mesociclos y semanas, y diálogos de borrado y conversión | P3a |
| P3c | Estructura: hojas de ciclos, ruta avanzada, plantillas e historial | P3a |
| P4 | Pestaña Volumen | P1 |
| P5 | SplitView como hoja «Cambiar reparto» | P2 |
| P6 | 1RM con la regla de marcas, calibrar volumen, copiar semana y borrar sesión | P2 |
| P7 | Cierre: hoja de render de la pantalla completa (tres pestañas, 360 dp, 100 % y 130 %), accesibilidad y docs | todos |
