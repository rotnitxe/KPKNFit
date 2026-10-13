# CU1 · Cuerpo: cabecera, isla, módulo «Peso» con gráfica interactiva y «Grasa corporal» (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con I9, CU0 y D1 integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/BODY_V1.md`, §2, puntos 1 a 3: TU especificación;
- el mapa `C:\kw\analysis\maps\body.md`.
- Usa lo que dejaron:
  - I9 en `ui/design/`: módulo, tipos, botones, `KpknMetric` y `kpknEntrance`;
  - D1: `KpknTopIsland`, `KpknSegmentedControl`, `KpknProgressBar`, `KpknSkeletonModule` y `KpknEmptyState`;
  - I11a: `KpknLineIcons`;
  - CU0: `BodyWeightSummary`, `ResolvedBodyGoal` y las series del VM.

## Qué haces
1. **Piezas nuevas** en `screens/body/design/` (paquete nuevo `com.example.kpkn.screens.body.design`), un archivo por pieza y sin
   estado:
   - **`BodyTrendChart`.** Parámetros: `points: List<BodyChartPoint>` (fecha, valor del día o null y tendencia), `goal: Double?`,
     `unit: String`, `modifier`, `height = 200.dp`.
     - Dibujo: puntos diarios de 3 dp en `textFaint`, la tendencia en tinta de 2,5 dp con extremos redondos y la meta como línea
       discontinua `SymbolPalette.ok` al 60 %, solo si cae dentro de la escala.
     - Ejes: 3 o 4 fechas en `caption` `textFaint`, y el máximo y el mínimo a la derecha en `caption` tabular.
     - Al arrastrar o pulsar: una guía vertical de 1 dp en blanco al 30 % y una burbuja `KpknSurface.inset` con filete
       `moduleBorder`, radio de 12 dp, con la fecha («mar 13 oct»), el pesaje del día y «Tendencia 64,2». Vibración
       `TextHandleMove` al cambiar de día.
     - Entrada: la tendencia se dibuja de izquierda a derecha en 700 ms la primera vez; con movimiento reducido, sin entrada.
     - Rendimiento: las posiciones se calculan FUERA del dibujo (`remember` por puntos y tamaño). Nada de `LocalDate.parse` en el
       Canvas.
     - Semántica: «Tendencia de peso: de 66,4 a 64,0 kg en 3 meses».
   - **`BodyWeightModule`**, con:
     - la cabecera «Peso» y el rango como `KpknSegmentedControl(Compact)`: 1M, 3M, 6M, 1A y Todo;
     - `KpknMetric(current, "kg", metricXL)` y debajo «hoy» o «hace 3 días» en `caption`;
     - el cambio: `TrendDown` o `TrendUp` de 16 dp y «−2,4 kg desde el 12 de agosto» en `caption`. En `done` si va hacia la meta,
       en `textMuted` sin meta y en `danger` si se aleja;
     - el ritmo: «−0,4 kg por semana», o «Sin ritmo medido aún» con la nota «Hacen falta 2 semanas de pesajes para medir tu ritmo»;
     - `BodyTrendChart`;
     - con objetivo:
       - `KpknProgressBar` desde la base hasta la meta;
       - «Te faltan 4,5 kg» y la fecha «≈ marzo de 2027», o «Sin fecha estimada aún»;
       - si el objetivo es de % de grasa, el texto lo dice («Meta: 15 % de grasa») y la barra va en el módulo de grasa.
     - Sin pesajes: `KpknEmptyState(KpknLineIcons.Body, "Registra tu primer pesaje", …, acción «Registrar pesaje»)`.
   - **`BodyFatModule`:** el mismo gráfico con «%», la cifra y su cambio. Solo si hay datos de grasa.
2. **`BodyProgressScreen`:** la parte de arriba de la página pasa a ser:
   - **Cabecera** sobre el negro: «Cuerpo» en `KpknType.display` y `KpknIconButton(KpknLineIcons.Plus, "Registrar medidas")`,
     que abre la hoja de alta de hoy.
     - El ancla `TourAnchors.BODY_PESAJES` pasa a este botón.
     - Quita la píldora flotante «Registrar medidas» (488-529) y el botón del medio de la lista (334-353): «Registrar medidas» queda
       una vez, en la cabecera y en la isla.
   - **`KpknTopIsland`** cuando la cabecera sale de la vista:
     - título «64,0 kg» y subtítulo «−0,6 kg en 30 días»;
     - acción `Plus` en un círculo de tinta;
     - icono `Body` en `SymbolPalette.columna`.
   - **Módulos** `BodyWeightModule` y `BodyFatModule`, con `kpknEntrance("body", i)`.
     - El ancla `TourAnchors.BODY_TENDENCIA` pasa a la gráfica de peso.
   - **Carga:** mientras `loaded` (de CU0) sea falso, `KpknSkeletonModule` en lugar de los módulos.
   - **Lo que va debajo** (composición, objetivo, recordatorio e historial) se queda como está: es de CU2 y CU3. Solo sus fondos
     pasan a `WizardColors.background` si hoy usan otro.
   - El fondo de la pantalla es `KpknSurface.canvas`. El `HazeState` propio de la pantalla deja de hacer falta si solo servía para la
     píldora quitada (Grep).
3. **Medidas antiguas a medianoche UTC** (aviso de CU0): en `bodyLocalDate`, una observación con `timestampEpochMs % 86_400_000 == 0`,
   `source == MANUAL` y `method == MANUAL` se lee con su fecha UTC. No se cambia nada guardado.
   - Prueba unitaria: en `America/Santiago`, una medida antigua del «13 oct» UTC se lee como del 13 de octubre.

4. **Nuevo icono de Cuerpo.** La báscula de `KpknLineIcons.Body` se lee como un medidor (dos agentes lo señalaron). Sustituye sus
   trazados por una cinta métrica, con la misma regla de trazo:
   - `M9.5 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12z`
   - `M9.5 13.75a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5z`
   - `M9.5 18h11v-2.5`
   - `M13.5 18v-1.75`
   - `M16.75 18v-1.75`
   Vuelve a sacar `render/KpknLineIconsRenderSheetTest` (PNG en tu carpeta) y míralo: tiene que leerse como cinta métrica a 20 y 24
   dp. Ajusta si hace falta y anota los cambios.

## Pruebas
- **Robolectric** `BodyWeightModuleTest`:
  - el rango llama al VM;
  - sin pesajes se ve el vacío con su acción;
  - con objetivo se ve «Te faltan …»;
  - sin ritmo medido se ve «Sin ritmo medido aún» y no hay fecha.
- **Robolectric** `BodyTrendChartTest`: la semántica resume la tendencia; con `pointerInput` simulado, la burbuja muestra la fecha
  del punto pulsado.
- **Unitaria** de la regla de UTC.
- **Hoja de render** `render/BodyPageRenderSheetTest` (360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/cu1`):
  - página arriba con objetivo de peso y 3 meses de datos;
  - sin objetivo;
  - sin pesajes;
  - con % de grasa;
  - la burbuja del gráfico pulsada;
  - la isla visible.
  - Mira cada PNG con Read y critica con el listón del usuario (por encima del gráfico de tendencia de MacroFactor). Máximo 3
    rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/cu1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*Body*" --tests "*NutritionActionRouting*" --tests "*Tour*" --tests "*KpknLineIcons*" > /c/kw/logs/screens-cu1-<n>.log 2>&1`

## Commit
- Por rutas: `feat(body): cabecera, isla, módulo de peso con gráfica interactiva y objetivo, y grasa corporal (CU1)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Composición, objetivo (hoja), recordatorio, historial y hoja de registro: son de CU2 y CU3.
- `MainActivity.kt`.
- Las piezas de `ui/design/`: si te falta algo, añade AL FINAL y anótalo.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas con su firma.
- Pruebas: número y resultado.
- Rutas de los PNG y tu crítica.
