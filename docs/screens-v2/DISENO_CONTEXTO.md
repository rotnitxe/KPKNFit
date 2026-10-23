# Contexto de diseño para KPKN Fit

> Añade este texto al contexto del agente que implementa pantallas. Explica cómo diseñar en KPKN: el criterio, el sistema y el método de trabajo.

---

## 1. Postura

- Diseñas como un diseñador de producto senior que además programa. El listón son las mejores apps de fuerza y nutrición del mercado (Hevy, Lyfta, MacroFactor, Strong): **KPKN tiene que superarlas**, no parecerse a ellas.
- **El tablero aprobado es la especificación.** Tu trabajo es llevarlo a Compose con fidelidad y con el contenido real de la app. Donde el tablero calla, decides con el sistema de este documento, nunca con los valores por defecto de Material.
- **Refinar, no quitar.** Un rediseño parte de la pantalla original y aplica sus mejoras:
  - Ninguna función desaparece ni se esconde sin un sitio nuevo.
  - Si algo se mueve, se migra y se prueba.
  - El usuario rechazó dos veces rediseños que quitaban cosas.
- **Nada inventado.** Cada opción, número, texto y estado sale del código, de los datos o de una decisión anotada. No se añaden métricas, botones ni explicaciones «para que quede bonito». Si un dato no existe, la interfaz lo dice con honestidad («Sin estimación», «Falta el TM»); no lo rellena.
- Cada pantalla responde a **una pregunta principal** de la persona. Lo que ayuda a responderla va arriba y grande; lo demás, abajo o un toque más allá.

## 2. Sistema visual de KPKN

### Color (tokens; nunca valores sueltos)

| Rol | Valor | Uso |
|---|---|---|
| Fondo | `#000000` | Superficie continua de toda la app (negro puro, OLED) |
| Módulo | `#111111` | Contenedores y tarjetas, solo cuando agrupan algo con sentido |
| Módulo elevado | `#161616`–`#1C1C1C` | Hojas, menús y estados presionados |
| Tinta | `#F2EEE6` | Texto principal (blanco cálido, nunca `#FFFFFF`) |
| Tinta secundaria | tinta al 60–70 % | Subtítulos y metadatos |
| Tinta tenue | tinta al 35–45 % | Placeholders, separadores y deshabilitados |
| Músculo | `#F49A6E` | Fuerza, series y carga muscular |
| Energía | `#F7CF73` | Energía y tiempo; también el anillo de tiempo |
| Columna | `#8FB2FF` | Carga axial y cardio |
| Mente | `#C9B8FF` | Superseries, técnicas e intensidad percibida |
| OK | `#43D18C` | Hecho, confirmado y dentro del rango |
| Peligro | `#EF6F64` | Errores y borrar |

Reglas:
- **El color significa algo.** Un acento solo aparece si representa su dominio o un estado. Nunca decora.
- **Como mucho un acento dominante por pantalla**, más OK o Peligro cuando toca.
- **Sin degradados de fondo, sin resplandores** y sin bordes con brillo o neón.
- Contraste del texto sobre el fondo de al menos 4,5:1, y de 3:1 en los textos grandes y los iconos.

### Tipografía

- **Syne** para lo importante y lo de marca: títulos de pantalla y de sesión, cifras protagonistas (kg, reps, kcal) y rótulos de sección en versalitas con tracking amplio («SUPERSERIE», «CARDIO · AL FINAL»).
- **Inter** para leer y operar: cuerpo, etiquetas, botones y metadatos. Usa cifras tabulares en todo lo que cambia o se alinea (pesos, tiempos, contadores).
- Una escala corta y estable:
  - Título de pantalla: Syne 32–40.
  - Título de sección o tarjeta: Inter 17–20, seminegrita.
  - Cuerpo: Inter 15–16.
  - Metadatos: Inter 13–14.
  - Cifra protagonista: Syne 28–44.
- Nada por debajo de 12 sp.
- Los títulos largos se cortan con elipsis o se ajustan; **nunca empujan controles fuera de su sitio**.
- Los pares que no deben partirse («RIR 2», «4 × 6–8», «por lado», «60 kg») llevan espacio no separable.

### Espacio, forma y medidas

- Rejilla de 4 dp. Escala: 4, 8, 12, 16, 20, 24 y 32.
- Medianil lateral de 16 dp en el móvil y 24 dp en las pantallas de contenido largo.
- Radios:
  - fichas: 14 dp;
  - módulos y tarjetas: 20–28 dp;
  - píldoras y botones: totalmente redondeados;
  - hojas: 28 dp arriba.
- Bordes de 1 dp, con tinta al 8–12 %, solo para separar módulos sobre negro. Sin sombras de color.
- **Objetivo táctil de al menos 48 dp** en todo lo pulsable, aunque el dibujo sea más pequeño.
- **Medidas fijas de la sesión en vivo** (leyes, comprobadas en el Z Flip 5, 360 × 880 dp):
  - Tarjeta en vivo: **312 × 425 dp**, con **todo dentro** (acciones, listas y mensajes).
    - Las listas largas se desplazan dentro de la tarjeta.
    - Nada flota entre el relator y la tarjeta, ni entre la tarjeta y la línea de sesión.
  - Fichas de serie: **85 × 76 dp, radio 14**. Son iguales para efectivas, aproximación y movilidad, en vivo y en el editor.
  - Pantalla corta (360 × 740) y letra al 130 %: la tarjeta mide lo mismo y nada se desborda ni se solapa.

### Contenedores y vidrio

- **Contenedores con criterio.** Una caja solo aparece si agrupa algo que se entiende como unidad (un ejercicio, un grupo, una superserie, una hoja). Lo demás vive sobre la superficie negra continua, separado por espacio y jerarquía, no por cajas.
- **No hay tarjetas dentro de tarjetas** salvo que el anidamiento sea real: una superserie dentro de su grupo sí.
- **Vidrio** (desenfoque con haze) solo en lo que flota sobre el contenido: hojas, islas inferiores, cabeceras fijas y menús.
  - Tiene que verse: desenfoque real, relleno translúcido y borde de 1 dp.
  - Sin desenfoque disponible, el respaldo es opaco `#141414` al 94 %.
- Los diálogos son **hojas inferiores**, no `AlertDialog` de Material.

### Iconos, símbolos e ilustración

- Iconos lineales con un trazo coherente (1,5–2 dp a 24 dp) y del mismo grosor visual en toda la pantalla.
- Las opciones visuales importantes (patrones de movimiento, tipos de cardio, modos) se representan con **símbolos o ilustraciones animadas sobre la superficie, no con tarjetas con icono**.
- Los glifos de patrón de ejercicio ya existen en el código: reutilízalos.

### Movimiento

- Con propósito: confirmar una acción, mostrar de dónde viene o a dónde va algo, o dar continuidad entre estados.
- Duraciones:
  - entrada de elementos: 180–240 ms con `FastOutSlowIn`;
  - salida: 120–160 ms;
  - cambios de valor (cifras, anillos): 300–450 ms;
  - nunca más de 500 ms en algo que bloquea.
- **Retroalimentación inmediata:** pulsar responde en menos de 100 ms (estado presionado, háptica ligera en las confirmaciones).
- Con «reducir movimiento» activo, el cuadro final se muestra sin animación.
- Los bucles decorativos se paran fuera de pantalla o en segundo plano.

## 3. Interacción y estados

- **Diseña todos los estados**, no solo el feliz:
  - vacío: qué es, por qué está vacío y cómo empezar;
  - cargando, con esqueleto o un aviso discreto, nunca la pantalla en blanco;
  - error: qué pasó y cómo seguir;
  - deshabilitado: por qué;
  - guardado bloqueado;
  - contenido largo;
  - valores extremos (0, «<1», 999 kg o un nombre de 60 caracteres).
- **Un solo aviso por estado.** Nunca dos mensajes solapados o que digan lo mismo.
- Destructivo = deshacer antes que confirmar: borrar muestra «Deshacer» con la ventana de 5 s. Se confirma antes de aplicar solo lo que cambia mucho y no se deshace fácil (cambiar de modo, aplicar una plantilla o una sustitución).
- **Un control por función.** Si hay dos maneras de hacer lo mismo en la misma vista, sobra una (se conserva la del tablero).
- **Gestos con alternativa visible:** todo lo que se hace arrastrando o deslizando tiene también un botón o un menú. El arrastre muestra la señal de destino y nunca pierde el elemento si el destino desaparece.
- **Entrada de datos:**
  - teclado numérico con coma decimal para los kg;
  - −/+ de pulsación larga para ajustes finos;
  - deslizadores solo donde el rango importa más que el valor exacto, y siempre con la cifra editable al lado.

## 4. Textos (UX writing en español)

- Español neutro, en segunda persona del singular y sin género gramatical. Frases cortas, sin jerga técnica: no «política», «heurística» ni «e1RM» sin explicar.
- **Botones con verbo y resultado:** «Aplicar a S2–S4», «Añadir aproximación», «Guardar programa». Nunca «OK» ni «Aceptar».
- **Mensajes honestos y útiles:** qué pasó, por qué y qué hacer. Nada alarmista, sin afirmaciones médicas ni de lesiones.
- Longitudes: títulos de hasta 44 caracteres, subtítulos de hasta 100 y una línea de efecto en una sola línea a 360 dp.
- Los números con su unidad y el formato de la app: «62,5 kg», «1:30», «≈ 58 min».

## 5. Accesibilidad

- Cada control lleva rol y descripción (`contentDescription` o `semantics`) con su estado: «Serie 2, 62,5 kg, hecha».
- Orden de foco lógico. Las hojas son diálogos con título y el foco va al título al abrirse.
- Con la letra al 130 % todo sigue usable: se elide o se reorganiza, sin solapar ni salirse de su contenedor.
- No se transmite nada solo con color: hace falta también un icono, un texto o una forma.

## 6. Método de trabajo para cada tablero

1. **Lee el tablero entero:** su HTML (`*.dc.html`, con la lógica en `<script type="text/x-dc">`) o el lienzo. Apunta cada medida, texto, estado e interacción.
2. **Localiza la pantalla actual** en el código (archivo y línea) y haz el inventario de sus funciones. Esa lista no puede encoger.
3. **Mapea** cada elemento del tablero a un componente existente (`ui/components`, `ui/design`, tokens del tema) antes de crear uno nuevo. Reutiliza siempre que puedas y crea solo lo que falte, con tokens.
4. **Implementa** con composables sin estado y estado elevado, y con `testTag` en los elementos que las pruebas tengan que medir.
5. **Comprueba con renders** (`*RenderSheetTest`, `KPKN_RENDER_DIR`) a 360 × 880 y a 360 × 740, con la letra al 100 % y al 130 %. **Abre cada PNG y compáralo con el tablero.**
6. **Mide en las pruebas** lo que es ley:
   - límites de la tarjeta (312 × 425);
   - fichas (85 × 76);
   - objetivos táctiles (≥ 48 dp);
   - ningún control fuera de su contenedor;
   - un solo aviso por estado.
7. **Haz una autocrítica** antes de cerrar y repasa estas preguntas:
   - ¿Qué mira primero el ojo y es lo correcto?
   - ¿Hay un único acento dominante?
   - ¿Sobra alguna caja?
   - ¿Hay textos partidos o inventados?
   - ¿Quedan estados sin diseñar?
   - ¿Hay controles duplicados?
   - ¿Se ve como el tablero con datos reales?

   Corrige lo que falle y anota en el informe lo que se quede fuera y por qué.

## 7. Señales de que algo está mal (antipatrones)

- Componentes de Material por defecto a la vista: `AlertDialog`, `Card` con elevación, morado de Material, `Divider` gris o el botón sombreado.
- Varios acentos compitiendo, degradados o neón.
- Tarjetas dentro de tarjetas sin motivo, o cajas alrededor de todo.
- Botones fuera de la tarjeta en vivo, o una tarjeta de aproximación o movilidad con un tamaño distinto de la de series.
- Textos que se parten mal («RIR / 2»), que se cortan sin elipsis o que solapan un icono.
- Dos controles para la misma función, o un rótulo que repite el título.
- Explicaciones largas donde bastaba una línea, o párrafos dentro de controles.
- Un número sin fuente o una opción que la app no soporta.
- Objetivos táctiles de menos de 48 dp, o información transmitida solo con color.
