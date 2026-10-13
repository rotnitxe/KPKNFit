# Cuerpo v1 · rediseño con el lenguaje KPKN v2 (2026-10-13)

Encargo y listón: los de `PROGRAM_DETAIL_V1.md`. Lenguaje común en `docs/design/KPKN_UI_V2.md`. Mapa del código actual:
`C:\kw\analysis\maps\body.md`.

Cuerpo es donde se ve si el plan funciona. Para superar a la competencia, en especial al gráfico de tendencia de MacroFactor, hacen
falta:
- cifras correctas (hoy hay errores de cálculo);
- una tendencia que se pueda recorrer con el dedo;
- un objetivo con fecha honesta;
- registrar un pesaje en dos toques.

## 1. Qué falla hoy
| Zona | Problema | Gravedad |
|---|---|---|
| Datos | El «peso actual» es el de la última sesión con % de grasa, no el último pesaje. El cambio y el progreso salen del primer punto del RANGO elegido (cambian al tocar 1M o 3M) | 🔴 |
| Datos | El ritmo es el previsto por el plan, no el medido (el VM ya calcula `weeklyRate` y nadie lo usa). Sin plan se lee «0.0 kg/sem» | 🔴 |
| Datos | El IMC tiene 5 categorías con huecos (24,995 → «Obesidad II+»). Las medidas se guardan a medianoche UTC y al oeste de UTC caen el día anterior. Al editar se pierde precisión. `load()` corre sin mutex | 🔴 |
| Inventos | Barra al 45 % sin dato. «Sin fecha» y «Calibrando» fijos | 🔴 |
| Identidad | Diez `Card` de Material, la paleta de Material en FFMI e IMC, un emoji y `Switch` teal | 🔴 |
| Producto | No hay editor de objetivo. La medida no lleva hora. El cuello no se puede registrar. La hora del recordatorio no se edita. «Registrar medidas» aparece dos veces | 🟡 |
| Estados | Sin carga: «Sin mediciones» parpadea. Los errores no se muestran | 🟡 |

## 2. La página, de arriba abajo
1. **Cabecera**, sobre el negro: «Cuerpo» en `display` y `KpknIconButton(Plus)`, que abre el registro. Al hacer scroll, la isla
   superior: «64,0 kg · −0,6 kg en 30 días».
2. **Módulo «Peso»:**
   - **Cabecera:** el eyebrow y el rango como segmentos compactos (1M, 3M, 6M, 1A, Todo).
   - **El peso actual** = el ÚLTIMO pesaje, en `metricXL` con «kg» en la misma línea base, y debajo «hoy» o «hace 3 días».
   - **El cambio:** «−2,4 kg desde el 12 de agosto» (el primer pesaje del rango, con su fecha escrita) con TrendUp o TrendDown.
   - **El ritmo MEDIDO:** «−0,4 kg por semana» de `weeklyRate`. Si no hay datos suficientes: «Hacen falta 2 semanas de pesajes
     para medir tu ritmo», sin cifra.
   - **Gráfica interactiva** de 200 dp:
     - los puntos diarios tenues, la tendencia (media móvil exponencial de 7 días) en tinta y la línea del objetivo discontinua;
     - el eje del tiempo con 3 o 4 marcas en `caption`;
     - al arrastrar el dedo, una guía vertical y una burbuja de vidrio con la fecha, el pesaje y la tendencia de ese día, con
       vibración háptica suave al cambiar de día;
     - se dibuja de izquierda a derecha la primera vez;
     - el cálculo de fechas va fuera del dibujo: nada de `LocalDate.parse` en el Canvas.
   - **Objetivo, si lo hay:**
     - una barra fina de progreso desde el peso de partida hasta la meta;
     - «Te faltan 4,5 kg»;
     - la fecha estimada SOLO si hay ritmo medido y vas hacia la meta: «≈ marzo de 2027». Si no, «Sin fecha estimada aún».
3. **Módulo «Grasa corporal»** (si hay datos): la misma gráfica y la cifra en %, con su cambio.
4. **Módulo «Composición»:**
   - la masa magra y la grasa en una barra apilada animada, con sus kg;
   - el FFMI en `metricM`, con su categoría;
   - el IMC en `metricM`, con las 4 categorías CONTINUAS del dominio (`BodyProgressCalculations`) y la cifra redondeada antes de
     clasificar;
   - **sin altura:** «Añade tu altura para ver tu IMC y FFMI» y la acción «Añadir altura», que abre la edición del perfil. Nunca se
     inventan 170 cm.
5. **Módulo «Objetivo»** (nuevo):
   - el objetivo de peso o de % de grasa, la fecha de inicio y «Editar»;
   - con un plan de nutrición activo, «Editar» abre la sección «Dirección y meta» del editor del plan: hay una sola fuente de verdad;
   - sin plan, abre una hoja con `WizardWeightRule` y guarda un objetivo propio con `upsertGoal`.
6. **Módulo «Recordatorio de pesaje»:**
   - interruptor propio;
   - la frecuencia en segmentos (cada día, cada semana, cada 2 semanas, cada mes) y «Otra», con un selector de 1 a 365 días;
   - la HORA, que ahora se puede editar;
   - y la próxima fecha.
7. **Historial:** los meses como etiquetas de grupo y un módulo por mes con una fila por medida.
   - **Cada fila:** fecha y hora, peso tabular, % de grasa y de músculo en `caption`, y la insignia «Health Connect» (solo lectura)
     si viene de ahí.
   - **Tocar** edita. **Eliminar** pide confirmación y deja «Deshacer».
8. **Hoja de registro** (vidrio v2):
   - fecha y hora con el calendario de mes y el reloj, guardadas en la zona local (se acaba el error de UTC);
   - el peso con `WizardWeightRule`;
   - el % de grasa con `WizardBodyFatRuler`;
   - el % de músculo con su regla;
   - «Medidas» plegable: cintura, cadera, pecho, brazo, muslo, pantorrilla y CUELLO, que vuelve;
   - notas;
   - «Guardar» como principal, sin perder precisión: los valores se guardan tal cual.

## 3. Fuera de la página
- **«Consejos»:** el módulo de consejos de salud genéricos se quita. Cada explicación útil va como (i) junto a su cifra (FFMI o
  IMC), con su fuente.
- **Health Connect** (flavor `health`): una línea en el historial («Sincronizado con Health Connect») y nada más.

## 4. Paquetes (ola CU, subagentes Haiku 5.5)
| Paquete | Qué | Depende de |
|---|---|---|
| CU0 | Datos y honestidad, con su prueba unitaria cada uno:<br>- peso actual = último pesaje;<br>- cambio con la fecha de su punto de partida;<br>- ritmo medido;<br>- IMC continuo y redondeado;<br>- fecha y hora locales;<br>- precisión al editar;<br>- `load()` con mutex;<br>- sin barra inventada;<br>- un solo cálculo de series (fuera el duplicado de la pantalla) | — |
| CU1 | Cabecera, isla, módulo «Peso» con la gráfica interactiva y el objetivo, y módulo «Grasa corporal» | CU0, Home v1.1 |
| CU2 | Composición, «Añadir altura», módulo «Objetivo» con su hoja y recordatorio | CU1 |
| CU3 | Historial por meses, editar, eliminar con «Deshacer» y hoja de registro v2 con reglas, hora y medidas | CU1 |
| CU4 | Cierre: código muerto, hojas de render de la página y de la hoja (360 dp, 100 % y 130 %) y docs | todos |
