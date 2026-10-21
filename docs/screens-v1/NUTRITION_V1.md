# Nutrición v1 · rediseño con el lenguaje KPKN v2 (2026-10-13)

Encargo y listón: los de `PROGRAM_DETAIL_V1.md`. Lenguaje común en `docs/design/KPKN_UI_V2.md`. Mapa del código actual:
`C:\kw\analysis\maps\nutrition.md`.

MacroFactor es el referente del sector en nutrición. Para superarlo hacen falta:
- registrar en segundos, ya sea describiendo o buscando;
- un día legible de un vistazo, con anillos y comidas;
- honestidad en las cifras: estimaciones con rango y nada inventado;
- la identidad KPKN: anillos del wizard, iconos propios, vidrio y movimiento.

## 1. Qué falla hoy
| Zona | Problema | Gravedad |
|---|---|---|
| Identidad | Teal de Material (`0xFF009688`), unos 40 colores fijos, `primary` amarillo en chips y botones y cero tokens del wizard | 🔴 |
| Coherencia | Los colores de P, C y G del registro no son los de los anillos. Cada pantalla tiene su «Snack», «Merienda» o «Colación» y sus iconos | 🔴 |
| Raíz | Tres accesos para registrar. El balance repite la misma cifra dos veces. Hay `Card` en todo | 🟡 |
| Registro | Una hoja de 2.755 líneas con `TextField` de Material, el botón «BUSCAR» en mayúsculas, un verde fijo y voseo en la privacidad | 🔴 |
| Editor del plan | `Card`, `RadioButton`, `Switch` y `FilterChip` de Material, y «Guardar» dos veces | 🟡 |
| Historial y calibración | `Scaffold` y `TopAppBar` de Material. La calibración no tiene estilo | 🟡 |
| Carga | Sin estado de carga: «Sin objetivos» parpadea antes de leer el plan | 🟡 |
| Movimiento | No respeta el movimiento reducido. Las cifras no cuentan | 🟢 |

## 2. Raíz de Nutrición, de arriba abajo
1. **Cabecera**, sobre el negro: «Nutrición» en `display` y, a la derecha, `KpknIconButton(Sliders)`, que abre el editor del plan.
   Al hacer scroll, la isla superior: «Hoy · 1.320 kcal restantes» con `Plus`.
2. **Módulo «Tu día»:**
   - **Tira de 7 días** (el mismo patrón que «Esta semana» de Home): cada día con un mini anillo de kcal de 28 dp. Tocar un día
     cambia todo lo de abajo con `AnimatedContent`.
   - **Anillos de macros del wizard** (`WizardMacroRings`, 128 dp):
     - las kcal en `metricXL` contando y «de 2.300 kcal · te quedan 1.320»;
     - la leyenda en tres columnas, como en Home v1.1.
   - **Sin plan:** la cifra y «Aún no tienes objetivos.» con «Definir objetivos». Mientras se carga el plan, esqueleto, no «Sin
     objetivos».
   - **Solo seguimiento:** la cifra y la leyenda, sin anillos y sin ofrecer metas.
3. **Avisos:** la pauta a medias y lo que diga el motor, como módulos teñidos.
4. **Módulos de comida:** Desayuno, Almuerzo, Merienda y Cena. Los nombres y los iconos salen de una sola fuente, `MealSlots`,
   compartida con Home y el registro.
   - **Cabecera:** el icono de la comida en un círculo `inset`, el nombre, las kcal y `KpknIconButton(Plus)`, que abre el registro
     en esa comida.
   - **Una fila por alimento:**
     - nombre en `bodyStrong`;
     - porción en `caption`;
     - kcal tabulares a la derecha;
     - debajo, una micro barra de 3 dp con el reparto de P, C y G en los colores de los anillos;
     - las estimaciones llevan «≈» y su rango al tocar.
   - **Gestos:** tocar la fila edita. Una pulsación larga abre el menú de vidrio con «Duplicar», «Mover a…» y «Eliminar».
     Eliminar deja «Deshacer» en la barra de avisos.
   - **Vacío:** una fila «Añadir» en `textFaint`.
5. ~~**Módulo «Balance de hoy»**~~ (quitado tras revisar N1, 2026-10-13): el motor ya no resta el entreno de lo comido, así que el
   módulo solo repetía las cifras de «Tu día». El gasto del entreno, si lo hay, es una línea de «Tu día» («Entreno · 320 kcal»).
6. **Módulo «Suplementos»:**
   - la cafeína (mg y límite) con un medidor de arco pequeño;
   - la creatina, con un check del día y su racha.
7. **Módulo «Últimos 7 días»:**
   - barras diarias con la línea de meta discontinua; la de hoy en tinta;
   - tocar una barra muestra su día;
   - debajo, «Ver historial».
8. **Isla flotante «Registrar comida»:** sobre la barra inferior, píldora de vidrio con `Plus`. Es el único acceso primario: fuera
   los accesos rápidos duplicados.

## 3. Registro de comida (hoja)
- **Hoja de vidrio v2** a pantalla casi completa.
- **Cabecera:**
  - la comida elegida como selector (las cuatro de `MealSlots` en segmentos con su icono);
  - la fecha y la hora (chip que abre el calendario de mes);
  - y `Close`.
- **Segmentos** «Describir · Buscar».
  - Describir:
    - campo de texto KPKN: superficie `inset`, radio de 16 dp, texto `body` y marcador «Dos huevos con tostadas y un café»;
    - con el botón principal «Interpretar».
  - Buscar: un campo con `Search` y los resultados en filas con nombre, marca, kcal por porción y «+».
- **Análisis:**
  - tres puntos que respiran y los mensajes que rotan, como hoy;
  - con movimiento reducido, un texto fijo;
  - sin detalles técnicos en monoespaciado: un mensaje claro y «Reintentar».
- **Alimentos interpretados:** una fila por alimento con:
  - nombre;
  - selector de porción en segmentos;
  - gramos con `GramsEditor` en estilo KPKN;
  - macros en una línea tabular;
  - la aclaración pendiente como pregunta con dos o tres respuestas en píldoras;
  - «Corregir» abre la búsqueda para cambiar el alimento.
- **Barra inferior fija:** el total (kcal y P, C y G) y «Guardar en Almuerzo» como principal. El éxito cierra con un check animado
  de 600 ms.
- **Diálogos KPKN** (fuera el `AlertDialog` de Material):
  - descartar;
  - medidas, con nueve controles;
  - olvidar lo aprendido;
  - privacidad, en español neutro y sin voseo.
- **Textos:**
  - «Buscar» y «Actualizar y buscar» en tipo frase;
  - «Interpretando…» con «…»;
  - «Hidratos» en todas partes;
  - «Merienda» en lugar de «Snack».

## 4. Editor del plan
- **Una página de módulos:** Dirección y meta, Datos de cálculo, Calorías y macros, Reparto semanal, Procedencia y Estado.
- **Controles del wizard:**
  - dirección con los tres símbolos de Definir, Mantener y Volumen;
  - meta con `WizardWeightRule`;
  - ritmo con `WizardPaceGauge`;
  - macros con `WizardMacroSlider`;
  - reparto semanal con `WizardNutritionDayStrip`.
- **Interruptores propios** (no `Switch` de Material) para las condiciones médicas.
- **«Guardar»:** uno solo, en una isla inferior. Se habilita cuando hay cambios y antes de guardar muestra lo que cambia («2.300 →
  2.150 kcal»).
- **Errores:** en lenguaje llano, nunca `e.message`.

## 5. Historial y calibración
- **Historial:** una página propia, con isla superior y atrás.
  - Meses como etiquetas de grupo y días como módulos compactos: fecha, mini anillo de kcal frente a la meta, kcal y macros en una
    línea.
  - Tocar un día abre su detalle (las comidas en solo lectura y «Ir a este día»).
  - Se pagina por meses: se acabó cargar todo el historial en la composición.
- **Calibración:**
  - un módulo de estado: «Ajuste semanal», última y próxima fecha, y el cambio propuesto en kcal con su porqué;
  - pasos numerados claros y «Restablecer» como acción de texto `danger`;
  - las constantes (14 días, ±150 kcal) salen del motor, no van repetidas en la pantalla.

## 6. Widget
- **Colores:** los de KPKN (tinta sobre negro y acento energía), fuera el teal.
- **Contenido:** «Registrar» y «Buscar» con iconos KPKN Line.

## 7. Paquetes (ola N, subagentes Haiku 5.5)
| Paquete | Qué | Depende de |
|---|---|---|
| N0 | `MealSlots` (nombres, orden e iconos únicos, también en Home), colores de macros únicos (`MacroColors`) y código muerto | Home v1.1 |
| N1 | Raíz: cabecera, isla, módulo «Tu día» (tira de días y anillos), avisos, balance y suplementos, con estado de carga | N0 |
| N2 | Raíz: módulos de comida con filas, menú y deshacer, «Últimos 7 días» e isla «Registrar comida» | N1 |
| N3a | Registro: hoja v2, cabecera, segmentos, Describir y Buscar, análisis | N0 |
| N3b | Registro: alimentos interpretados, barra de total y guardar, diálogos y textos | N3a |
| N4 | Editor del plan con controles del wizard y «Guardar» único | N0 |
| N5 | Historial (paginado) y calibración | N1 |
| N6 | Widget y cierre: hojas de render de la raíz, el registro, el editor y el historial a 360 dp, al 100 % y al 130 %, y docs | todos |

## Estado (N6)

Cierre de Nutrición (2026-10-13). Por punto de la especificación:

- **§1 Qué falla hoy:** lo cierran N0–N5. N6 deja cerrado lo que quedaba: textos («Hidratos», «Merienda», voseo y mayúsculas), el widget, el código muerto y el módulo «Distribución calórica», que sale de la raíz. Lo que queda abierto está al final de esta sección.
- **§2 Raíz:** lo hecho en N1 y N2. N6 quita el módulo «Distribución calórica» y su barra de macros (repetían «Tu día» y las micro barras de cada comida). Cada fila lleva su micro barra 6 dp bajo la porción, dentro de la columna de texto. La columna de kcal mide al menos 112 dp, así que todas las barras miden igual, y el nombre puede ocupar dos líneas. La fila sigue midiendo 56 dp como mínimo. Hojas de render: la raíz con datos en tres tramos, sin objetivos, con solo seguimiento (plan activo sin meta: sin anillos ni «Definir objetivos») y cargando (esqueletos en «Tu día» y en suplementos).
- **§3 Registro:** lo hecho en N3a y N3b. N6 añade dos cosas. La aclaración de la tortilla ya no repite la respuesta corta: si un candidato es la misma tortilla que un chip «De …», se oculta y queda la respuesta corta («De maíz»); lo que pasa al elegir no cambia. Y el aviso «Tocá la tarjeta para editar» pasa a «Toca la tarjeta para editar». En producción la tortilla solo ofrece «Trigo (wrap)» como candidato; el caso repetido venía de las muestras de la hoja. Hojas de render: describir vacío, alimentos con aclaración y guardando.
- **§4 Editor:** lo hecho en N4. N6 quita del estado `savedPlanId` y `distributionStatus`, que nadie leía. `isDirty` sí lo lee el editor y se queda. Hojas de render arriba y abajo.
- **§5 Historial y calibración:** lo hecho en N5. N6 saca el tope del ajuste a `NutritionCalibrationEngine.MAX_ADJUSTMENT_KCAL` (150 kcal, sin cambiar la lógica). Las cifras con signo ya usan el menos tipográfico «−» en todos los formateadores de Nutrición (`formatKcalEs`, `formatDecimalEs`, `formatSignedDecimalEs` y `signedKcal`), así que no hubo que cambiar ninguno. Hojas de render: historial de tres meses y detalle de un día.
- **§6 Widget:** hecho. Fondo negro KPKN `#0A0A0A` igual de día y de noche, con el radio de esquina del sistema (Android 12 y posteriores; 16 dp antes). El símbolo y «Nutrición» van en tinta `#F2EEE6`. Dos píldoras: «Registrar», en tinta con texto negro, y «Buscar», en blanco al 12 % con texto tinta. Los iconos `Plus` y `Search` están exportados a `res/drawable/ic_kpkn_plus.xml` y `ic_kpkn_search.xml`, con el mismo trazo de 1,75 y extremos redondos. Los enlaces (`nutrition/action/openFoodLog` y `nutrition/action/openSearch`) no cambian. Glance no se pinta en Robolectric: la hoja muestra una réplica en Compose de la misma composición.
- **§7 Paquetes:** N6 cerrado dentro de su alcance. N0 a N5 integrados en la rama.

**Código muerto quitado (N6):** 94 líneas de producción. La raíz pierde 54 (el módulo de «Distribución calórica», su barra y los colores privados), el ViewModel 33 (`macroRingPct` y `createPlan`) y el editor 7 (`savedPlanId` y `distributionStatus`). `createPlan` sigue como ayuda de las pruebas de planes, que ahora llaman a `addNutritionPlan` y `activatePlan`, igual que hacía él. Ya no existían `nutrientProgress`, `progressPct` ni `onNavigateToBodyProgress`.

**Lo que no se hizo, y por qué:**
- **Código muerto del dominio (análisis):** quedan funciones y constantes sin lectores en `domain/nutrition`, como `resolveBatch`, `fixedTargetsFor`, `catalogFoodFor`, `methodSearchSuffix` o `DATASET_MIN_CONFIDENCE`. Quitarlas toca el análisis, que este paquete no debe cambiar. Van en una limpieza aparte.
- **Repositorio:** `NutritionRepository.mealTemplates` se actualiza, pero nadie lo lee. `deleteNutritionPlan` y `clearNutritionLogs` los usan pruebas como limpieza. Ninguno entra en la lista de N6.
- **Cuerpo:** `BodyProgressViewModel.selectMetric` y `replaceLegacyMeasurement` no tienen lectores. Son de Cuerpo y quedan anotados para su cierre.
- **Pruebas de los avisos de macros:** `MacroValidator` no tiene prueba propia, así que los textos nuevos («Hidratos …») no tienen aserción.
- **Inglés:** `values-en` conserva «Carbs», que es el término correcto en inglés.
- **Widget en dispositivo:** pendiente de probarlo en el teléfono. La hoja solo muestra la réplica.
- **Vidrio de las islas (§2 y §4):** en las hojas de render, la isla de «Guardar» del editor y la superior dejan leer por debajo las etiquetas de los días y los campos. La especificación pide manchas de color sin letras legibles. Es el vidrio compartido (`KpknGlass`), no esta pantalla; queda para una revisión del vidrio.
- **Carga de las comidas (§2):** mientras no llegan los registros, cada comida muestra «Añadir» en lugar de un esqueleto. Hace falta un estado de carga propio para las comidas; queda pendiente.
- **Pie de la tendencia (§2):** «Media de los días registrados: … · …» parte la línea justo tras el punto medio. Es cosmético y queda pendiente.
