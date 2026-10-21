# Lenguaje visual KPKN v2 · pantallas de la app (2026-10-13)

Base para Home v1.1 y para el rediseño que viene de Program Detail, Nutrición y Cuerpo (todas sus vistas). Nace de la revisión
de Home v1 por el usuario (2026-10-13):
1. la portada de la sesión de hoy es excesiva;
2. faltan contenedores que ordenen;
3. no se ve el vidrio;
4. la píldora superior aparece sin animación;
5. falta pulido en iconos y en el reparto del texto.

Su listón: **nivel top mundial, por encima de Lyfta, MacroFactor Workouts y Hevy**.

El wizard conserva su propio lenguaje: página continua, símbolos sin caja y su vidrio. Este documento es para las pantallas de la
app.

## 0. Por qué así (frente a la competencia)
- **MacroFactor** ordena su panel en módulos con cabecera, iconografía coherente y datos honestos. Es lo mejor del sector en
  claridad, pero es sobrio y genérico.
- **Hevy** es sencillo y funcional: listas y tarjetas de rutina. Es rápido de usar, pero no tiene identidad.
- **Lyfta** apuesta por la biblioteca de ejercicios en vídeo. Su interfaz es correcta, sin carácter propio.
- **KPKN supera a los tres si junta:**
  - el orden en módulos de MacroFactor;
  - una identidad propia que ninguno tiene: negro puro, Syne, la torre de los RINGS, portadas con arte y símbolos animados;
  - vidrio real en lo que flota;
  - movimiento con intención y honestidad de datos (sin dato, «—»).
- **Reglas de fondo:**
  - densidad útil: cada píxel responde a «¿qué hago hoy?» o «¿cómo voy?»;
  - nada decorativo que empuje la acción principal por debajo del pliegue.

## 1. Superficies
| Token | Valor | Uso |
|---|---|---|
| `KpknSurface.canvas` | `#000000` | Fondo de toda pantalla |
| `KpknSurface.module` | `#111111` | Relleno de un módulo |
| `KpknSurface.modulePressed` | `#161616` | Módulo o fila pulsados |
| `KpknSurface.moduleBorder` | blanco al 7 % | Filete de 1 dp del módulo |
| `KpknSurface.inset` | `#1A1A1A` | Elementos tonales dentro de un módulo (chips, círculo de icono) |
| `KpknSurface.divider` | blanco al 6 % | Separador de 1 dp dentro de un módulo |
| `KpknShape.module` | radio 24 dp | Módulos |
| `KpknShape.island` | radio 32 dp | Islas de vidrio (barra superior, barra inferior, botones flotantes) |
| `KpknShape.sheet` | radio 28 dp (arriba) | Hojas |
| `KpknShape.pill` | redondeo completo | Botones e insignias |

**Qué lleva contenedor y qué no** (decisión: «continuo pero ordenado»):
- **Con módulo:** todo bloque de datos o de acción con vida propia. En Home: la semana con la sesión del día, RINGS, avisos,
  Nutrición de hoy, Cuerpo y Aprende.
- **Sin módulo, sobre el negro:**
  - lo editorial y de navegación: la cabecera de la página (fecha, saludo, programa) y las etiquetas de grupo;
  - los carruseles de pósteres, porque cada póster ya es su propio objeto.
- **Medidas del módulo:**
  - margen lateral de pantalla 16 dp;
  - relleno interior 20 dp a los lados, 18 dp arriba y 20 dp abajo;
  - 12 dp entre módulos seguidos, y 28 dp entre un grupo y el siguiente cuando hay etiqueta de grupo.
- **Cabecera del módulo:** dentro, el eyebrow a la izquierda y la acción a la derecha, centrados entre sí.
  - La acción es de texto o un botón de icono de 40 dp, con zona táctil de 48 dp.
- **Al pulsar** (si el módulo entero es tocable): relleno `modulePressed` y escala 0,985 con resorte (amortiguación 0,8, rigidez
  media), sin ripple de Material.
- **Avisos:** módulo teñido, con relleno = acento al 8 % sobre `module` y filete = acento al 24 %.
- **Dentro de un módulo no hay otra caja:** se separa con aire o con `divider`. Las celdas tonales `inset` solo van en chips e
  iconos.

## 2. Vidrio v2 (islas y hojas)
- **Diagnóstico:** el «DarkMica» de `KpknGlass` pinta un gris `#0E0E0E` al 94 % y encima el desenfoque al 42 % con un tinte del 58 %.
  - Por eso lo que pasa por debajo se ve NÍTIDO y apagado: es el 6 % que deja pasar el gris.
  - El desenfoque apenas aporta. Medido en las capturas del Flip: el texto de debajo sale con +8 niveles sobre el fondo y con los
    bordes nítidos.
- **Receta v2:** sin base opaca bajo el efecto; el desenfoque al 100 % de opacidad.

| Superficie | Desenfoque | Tinte | Fondo de la muestra | Ruido | Filete |
|---|---|---|---|---|---|
| Isla (barra superior, barra inferior, píldoras flotantes) | 36 dp | `#0A0A0A` al 55 % | negro | 0,05 | 1 dp, degradado vertical blanco 14 % → 4 % |
| Hoja y diálogo | 48 dp | `#0A0A0A` al 66 % | negro | 0,05 | 1 dp blanco 10 % solo en el borde superior |
| Sin fuente de desenfoque (API < 31 o sin `HazeState`) | — | — | `#0E0E0E` al 96 % | — | igual |

- **Criterio de aceptación en el emulador:** lo que pasa por debajo debe verse como manchas de color suaves, sin bordes ni letras
  legibles. El texto de la isla y de la hoja tiene contraste ≥ 4,5:1 sobre lo más claro que pase por debajo (cifras Syne blancas,
  anillos de color).
- **Respaldos:** `kpknCoverGlass` (cabecera del entreno sobre el arte) no cambia. Las demás funciones de `KpknGlass.kt` pasan a la
  v2 con la misma firma; nadie tiene que cambiar sus llamadas.

## 3. Islas
- **Barra superior contextual (Home y pantallas con cabecera grande):**
  - **Cuándo aparece:** cuando la cabecera de la página sale de la vista. Ya no por opacidad según el scroll, sino con
    `AnimatedVisibility`.
  - **Entrada:** fundido de 180 ms, bajada desde −24 dp con resorte (amortiguación 0,82, rigidez 380) y escala 0,92 → 1 desde el
    centro superior.
  - **Salida:** fundido de 140 ms, subida de 16 dp en 160 ms y escala → 0,96.
  - **Contenido:** entra escalonado: icono a 0 ms, títulos a 40 ms y acción a 80 ms (fundido y 6 dp en horizontal). Al cambiar de
    contexto (sesión → nutrición → cuerpo), `AnimatedContent`: el nuevo sube 8 dp con fundido cruzado de 220 ms.
  - **Con movimiento reducido:** solo fundidos de 120 ms.
  - **Medidas:** alto 56 dp, margen 12 dp, isla de vidrio v2.
  - **Disposición:**
    - icono de contexto de 20 dp en un círculo `inset` de 36 dp, teñido con el acento del contexto (sesión = músculo,
      nutrición = energía, cuerpo = columna);
    - título Inter 600 a 15 sp y subtítulo Inter 400 a 12 sp en `textMuted`, una línea cada uno;
    - acción contextual en un círculo de tinta de 36 dp con el icono en negro (play o «+»);
    - avatar con la inicial (círculo de 36 dp con filete) que abre Perfil.
  - Fuera el logo de la torre a 48 dp y el icono de persona de Material.
- **Barra inferior:**
  - **Medidas:** isla de vidrio v2 con margen de 12 dp a los lados y 8 dp sobre la barra de gestos, y 64 dp de alto (hoy 80).
  - **Iconos:** de línea KPKN (§4) a 24 dp y etiqueta Inter 500 a 11 sp.
  - **Pestaña activa:** icono y etiqueta en tinta, con un punto de 4 dp en color músculo bajo la etiqueta. El cambio de pestaña
    anima el punto de 0 a 1 en 160 ms.
  - **Pestaña inactiva:** en `textFaint`.
- **Velo de la barra de estado:** degradado fijo del negro al transparente, de alto = barra de estado + 24 dp, sin capturar toques.

## 4. Iconos KPKN Line
- **Construcción:** un solo set, en `ui/components/icons/KpknLineIcons.kt`, como `ImageVector`.
  - Retícula de 24 dp con zona viva de 2 a 22.
  - Trazo de 1,75 dp, con extremos y uniones redondeados.
  - Sin rellenos, salvo `PlayFilled`.
- **Lista:** Home, Training (barra con discos), Nutrition (cuenco con hoja), Body (báscula), Play, PlayFilled, Plus, Edit, Info,
  ChevronRight, ChevronLeft, ChevronDown, Close, Check, Clock, Calendar, Bell, Book, TrendUp, TrendDown, Repeat, More, Sliders,
  Person, Sunrise, Sun, Apple, Moon y Tower (marca KPKN de tres elipses).
- **Uso:** con `Icon(imageVector, tint)`. Fuera los iconos de Material en las pantallas rediseñadas.
- **Estados:** activo = tinta; inactivo = `textFaint`; destructivo = `danger`.
- **Tamaños:** 24 dp en navegación y 20 dp dentro de botones e islas.
- **Comidas** (Desayuno, Almuerzo, Merienda, Cena = Sunrise, Sun, Apple, Moon):
  - el icono va en un círculo `inset` de 44 dp;
  - con registro, el icono en tinta y una marca de check de 14 dp en `done` en la esquina;
  - sin registro, el icono en `textFaint` y «Añadir».

## 5. Tipografía (roles de pantalla)
| Rol | Fuente | Tamaño / interlínea | Uso |
|---|---|---|---|
| `display` | Syne ExtraBold | 32 / 36 | Saludo, título de pantalla (se encoge hasta 24 si no cabe) |
| `title` | Syne Bold | 24 / 28 | Título de módulo protagonista (nombre de la sesión) |
| `metricXL` | Syne ExtraBold | 40 / 44 | Peso, kcal del día |
| `metricL` | Syne Bold | 28 / 32 | Valores de RINGS |
| `metricM` | Syne Bold | 18 / 22 | Cifras pequeñas (grasa, IMC, FFMI) |
| `unit` | Inter SemiBold | 13 / 16 | Unidad junto a una cifra: misma línea base, 3 dp de separación, `textMuted` |
| `eyebrow` | Inter SemiBold | 12 / 16, espaciado 1,2 sp, MAYÚSCULAS | Cabeceras de módulo y kickers |
| `body` | Inter Regular | 15 / 21 | Texto de lectura |
| `bodyStrong` | Inter SemiBold | 15 / 21 | Nombres de ejercicio y filas |
| `caption` | Inter Regular | 12 / 16 | Notas y pies |
| `action` | Inter SemiBold | 14 / 20 | Acciones de texto (48 dp de zona táctil) |

**Reglas:**
- Las dosis y las cifras de tabla van con números tabulares (`fontFeatureSettings = "tnum"`).
- Una cifra y su unidad comparten la línea base (`Row` con `alignByBaseline()`). Nunca un «%» en superíndice ni separado por un
  hueco.
- Los títulos van en tipo frase: «Volumen de entrenamiento», no «Volumen de Entrenamiento». Máximo 2 líneas. Ninguna palabra se
  corta; si no cabe, elipsis.
- La etiqueta del módulo y su acción quedan centradas en la misma fila; la acción nunca es más grande que la etiqueta.
- Al 130 % de letra todo sigue legible. Una fila puede pasar a dos líneas, pero nada se solapa ni se corta a media palabra.

## 6. Botones
- **Principal:** píldora de 56 dp de alto y redondeo completo, fondo de tinta y texto negro Inter 600 a 16 sp.
- **Secundaria:** igual, con borde de 1 dp en tinta al 24 % y texto en tinta.
- **Botón de icono:** círculo `inset` de 40 dp con el icono a 20 dp y zona táctil de 48 dp.
- **Al pulsar:** escala 0,97 con resorte y la sombra interna desaparece. Nada de ripple de Material.
- Se sustituye el rectángulo de 9 dp de radio de `WizardShapes.cta` en las pantallas de la app. El wizard conserva el suyo.

## 7. Movimiento
- Se mantiene la entrada de los módulos una vez por proceso (`kpknEntrance`, antes `homeEntrance`): fundido y 12 dp, 420 ms,
  escalonado de 50 ms.
- Las cifras cuentan hacia arriba en 900 ms la primera vez que se ven.
- El cambio de contenido por selección (día de la semana, pestaña, rango) usa `AnimatedContent`: fundido cruzado de 220 ms y
  12 dp en la dirección del cambio.
- Con movimiento reducido: valores finales, solo fundidos y sin bucles.

## 8. Accesibilidad
- Todo lo tocable mide al menos 48 dp, con `Role` y un texto visible o una `contentDescription`.
- Las islas exponen su título. Los módulos tocables leen la cabecera y el valor principal.
- Contraste: tinta sobre `module` es 15,6:1; `textMuted` sobre `module`, 9:1; `textFaint` sobre `module`, 5,1:1 (solo para
  texto secundario).

## 9. Dónde vive el código
- **`ui/design/`** (nuevo): `KpknSurface`, `KpknShape`, `KpknType`, `KpknModule`, `KpknModuleHeader`, `KpknPrimaryPill`,
  `KpknSecondaryPill`, `KpknIconButton`, `KpknMetric` (cifra con unidad) y `Modifier.kpknEntrance`.
  - Usa `WizardColors` y `SymbolPalette` para los colores de marca.
  - Home pasa a usar estas piezas. `HomePrimaryPill` y las demás quedan como envoltorios finos para no tocar cada llamada.
- **`ui/components/KpknGlass.kt`:** receta v2.
- **`ui/components/icons/KpknLineIcons.kt`:** iconos.
