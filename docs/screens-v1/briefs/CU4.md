# CU4 · Cuerpo: cierre (cableado, unidad única, pulido de CU3, código muerto y hoja de la página) (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con CU0, CU1, CU2 y CU3 integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/BODY_V1.md` entero (es la especificación de toda la página);
- lo que dejaron CU1, CU2 y CU3 en `screens/body/design/` y en `screens/nutrition/BodyProgressScreen.kt`.

Cuerpo ya tiene sus módulos nuevos. Este paquete la deja TERMINADA: nada de lo de abajo cambia la lógica de datos (CU0) ni el
guardado.

## Qué haces
1. **Cableado en `MainActivity.kt`** (SOLO el bloque `composable` de `BodyProgressScreen`, aprox. línea 1340):
   - `onAddHeight` navega a `KpknRoute.Profile.route` (la edición del perfil, donde está la altura);
   - `onEditNutritionPlan = { planId -> navController.navigate(KpknRoute.NutritionPlanEditor.create(planId)) }`;
   - `onCreatePlan` ya no lo usa la pantalla: quita el parámetro y su paso en `MainActivity`.
2. **Una sola unidad de peso en toda la página:** la de Ajustes (`settings.weightUnit`, kg o lb).
   - La usan el módulo «Peso» y su gráfica (CU1), la isla, el objetivo (CU2), las filas del historial y la hoja de registro (CU3).
     Hoy la hoja va en la unidad de Ajustes y lo demás en kg.
   - Se guarda siempre en kg, como hoy: solo cambia lo que se muestra. Una función pura de formato (`bodyWeightText(kg, unit)`,
     con «64,5 kg» o «142,2 lb»), con prueba unitaria, y úsala en todas partes.
3. **Pulido de CU3** (crítica del orquestador sobre sus PNG):
   - **«Añadir % de grasa» y «Añadir % de músculo» se parten en dos líneas a 360 dp y al 100 %.** Pasan a dos `KpknChip` con
     `leadingIcon = KpknLineIcons.Plus`: «Grasa» y «Músculo», en un `FlowRow` (al 130 % caben o bajan a otra fila, nunca se parten).
   - **«% DE MÚSCULO» se corta en «% DE» al 130 %.** Los eyebrows pasan a «GRASA» y «MÚSCULO»: el «%» ya va con la cifra.
   - **El peso sale en gris en la hoja** porque `WizardWeightRule` pinta así el valor sin confirmar del wizard. Añade a
     `WizardWeightRule` un parámetro al final, `emphasized: Boolean = false`, que con `true` pinta la cifra en `WizardColors.text`.
     Por defecto se queda como hoy, para no cambiar el wizard. La hoja de Cuerpo lo pasa a `true`.
   - **`WizardBodyFatRuler` anuncia «Porcentaje de grasa corporal» también para el músculo.** Añade un parámetro al final,
     `accessibilityLabel: String = "Porcentaje de grasa corporal"`, y pasa «Porcentaje de músculo» en la regla de músculo.
4. **Código muerto** (confirma cada uno con Grep en `src/main`, `src/test` y `src/androidTest` antes de borrarlo):
   - en `BodyProgressScreen.kt`: `ProgressHero`, `HeroMetric`, `BodyTrendSparkline`, `BodyMetricTrendChartCard`,
     `MeasurementSectionLabel`, `legacyEntries` y los ayudantes que solo usen estos;
   - los formatos de fecha con `Locale.getDefault()` (aprox. 1163 y 1172): si quedan vivos, `Locale.forLanguageTag("es")`;
   - cualquier `Card`, `MaterialTheme.colorScheme`, `Color(0x…)` o texto en mayúsculas («GUARDAR MEDICIÓN») que siga vivo en la página.
   - Anota en el informe cuántas líneas quitas.
5. **Hoja de render de la página entera** (`render/BodyPageRenderSheetTest`):
   - la página completa de arriba abajo (cabecera, peso con gráfica, grasa, composición, objetivo, recordatorio e historial), con
     datos;
   - una persona nueva: sin pesajes, sin altura y sin objetivo;
   - en libras;
   - con mediciones de Health Connect;
   - la isla con la página desplazada;
   - la hoja de registro con las correcciones del punto 3;
   - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/cu4`.
   - Si la página es muy larga para un PNG, pártela en dos o tres capturas por estado.
   - Mira cada PNG con Read y critica con el listón del usuario: Cuerpo tiene que verse mejor que la sección de peso de MacroFactor
     (la referencia del sector en tendencia de peso). Máximo 3 rondas.
6. **Documentación:** al final de `docs/screens-v1/BODY_V1.md`, una sección «Estado (CU4)» con:
   - lo terminado por punto de la especificación;
   - lo que no se hizo y por qué: las notas no se guardan en ningún sitio, así que no se muestran; la pantorrilla no existe en
     `BodyMetric`.

## Pruebas
- **Siguen en verde:** `*Body*`, `*KpknMonthCalendar*` y lo que pruebe `WizardWeightRule` y `WizardBodyFatRuler` (Grep).
- **Unitarias:** `bodyWeightText` en kg y en lb, con redondeo y coma decimal.
- **Robolectric** (en tu prueba de la hoja o en una nueva): los dos chips añaden grasa y músculo, y la regla de músculo anuncia
  «Porcentaje de músculo».

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/cu4 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "*Body*" --tests "*KpknMonthCalendar*" --tests "*WizardWeightRule*" --tests "*BodyFat*" > /c/kw/logs/screens-cu4-<n>.log 2>&1`

## Commit
- Por rutas: `feat(body): cierre de Cuerpo con cableado, una sola unidad de peso, pulido de la hoja y hoja de render de la página (CU4)`.
- Si borras mucho código, un commit aparte: `refactor(body): quita el código muerto de la pantalla de Cuerpo (CU4)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lógica de datos de Cuerpo (`domain/body/`, el repositorio) salvo la función de formato nueva.
- `MainActivity.kt` fuera del bloque de `BodyProgressScreen`.
- Nutrición, Program Detail y Home.
- Push.

## Informe
- Qué hiciste, por punto.
- Líneas de código muerto quitadas.
- Pruebas: número y resultado.
- PNG y crítica.
