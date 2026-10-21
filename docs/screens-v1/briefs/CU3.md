# CU3 · Cuerpo: historial por meses y hoja de registro v2 (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con CU0, CU1, I9 y D1 integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/BODY_V1.md`, §2 puntos 7 y 8: TU especificación;
- el mapa `C:\kw\analysis\maps\body.md`;
- lo que dejó CU1 en `screens/body/design/`.

CU2 trabaja EN PARALELO en composición, objetivo y recordatorio de la misma pantalla. Toca solo tus secciones de
`BodyProgressScreen.kt` y AÑADE al final de `BodySamples.kt`.

## Qué haces
1. **`ui/design/KpknMonthCalendar.kt`**: calendario de mes propio y sin estado, para elegir una fecha.
   - Firma: `KpknMonthCalendar(selected: LocalDate?, onSelect: (LocalDate) -> Unit, modifier, minDate: LocalDate? = null, maxDate: LocalDate? = null, today: LocalDate)`.
   - Toma como modelo `CompetitionCalendar` (`screens/onboarding/entreno/EntrenoCompetitionStep.kt:232`): cabecera con el mes y
     `ChevronLeft`/`ChevronRight`, rejilla de 7 columnas que empieza en lunes, hoy con anillo, la fecha elegida rellena de tinta y los
     días fuera de rango en `textFaint`, sin toque.
   - El wizard NO se toca: conserva el suyo.
   - Lo usarán también Program Detail y Nutrición.
2. **Historial** (`screens/body/design/BodyHistorySection.kt`):
   - **Agrupado por meses:** la etiqueta de grupo («OCTUBRE DE 2026») sobre el negro y, debajo, un `KpknModule` por mes con una fila
     por medida y `divider` entre filas.
   - **Cada fila** (56 dp como mínimo):
     - la fecha y la hora («mar 13 oct · 07:40») en `bodyStrong`;
     - el peso tabular a la derecha;
     - el % de grasa y el de músculo en `caption` debajo, si los hay;
     - la insignia «Health Connect» (`caption` sobre `inset`) en las de origen externo, que son de solo lectura.
   - **Tocar una fila** abre la hoja de edición. Las de Health Connect abren la hoja en solo lectura.
   - **Eliminar:** desde la hoja, con `KpknAlertDialog` de confirmación y, al borrar, `KpknSnackbarBanner` con «Deshacer», que
     restaura la medida con su id y su hora.
   - **Vacío:** `KpknEmptyState(KpknLineIcons.Body, "Aún no hay medidas", …, «Registrar medidas»)`.
   - Fuera las tarjetas viejas de entrada (Screen:1799-1868) y el bloque de lista (355-423).
3. **Hoja de registro v2** (sustituye a Screen:1884-2023, en `KpknSheet`):
   - **Fecha y hora:** un chip con la fecha que despliega `KpknMonthCalendar` (hasta hoy) y otro con la hora que abre
     `KpknNativeTimePickerDialog`. Se guarda en la zona local con la regla de CU0; al editar, se conserva la hora original salvo que
     se cambie.
   - **Peso** con `WizardWeightRule`, en la unidad del usuario.
   - **% de grasa** con la regla de grasa del wizard (`WizardBodyFatRuler` o el de `WizardWeighIns`), opcional, con «Añadir % de
     grasa» como acción si está vacío.
   - **% de músculo** con su regla (rango de 20 a 70, como `MUSCLE_PERCENT_RANGE`), opcional igual.
   - **«Medidas»** plegable: cintura, cadera, pecho, brazo, muslo, pantorrilla y CUELLO, que vuelve. Cada una con `KpknTextField`
     numérico en cm, todas opcionales.
   - **Notas** con `KpknTextField` multilínea.
   - **«Guardar»** como principal, deshabilitado sin cambios o sin peso. Los valores van tal cual (precisión de CU0).
   - Las reglas del wizard son `internal`/`private`: si alguna es privada, hazla `internal` sin cambiar su comportamiento, y anótalo.
4. **Botón de alta:** el «+» de la cabecera (CU1) abre esta hoja con la fecha de hoy.

## Pruebas
- **Robolectric:**
  - el historial agrupa por mes y ordena del más reciente al más antiguo;
  - la fila de Health Connect es de solo lectura;
  - eliminar pide confirmación y «Deshacer» restaura;
  - la hoja no permite guardar sin peso;
  - al editar sin cambiar la hora, se conserva.
- **Unitarias** de `KpknMonthCalendar`: límites de rango, primer día lunes y meses de 28 a 31 días.
- **Siguen en verde:** `*Body*` y `*KpknControls*`.
- **Hoja de render:**
  - `render/BodyHistoryRenderSheetTest`: historial de 3 meses, vacío, la hoja de alta, la hoja de edición con medidas desplegadas,
    la de solo lectura, el diálogo de borrar y el calendario;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/cu3`.
  - Mira cada PNG con Read y critica con el listón del usuario. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/cu3 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*Body*" --tests "*KpknControls*" --tests "*KpknMonthCalendar*" > /c/kw/logs/screens-cu3-<n>.log 2>&1`

## Commit
- Por rutas: `feat(body): historial por meses con deshacer, hoja de registro con reglas, hora y medidas, y calendario de mes compartido (CU3)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Composición, objetivo y recordatorio (CU2).
- El wizard, salvo cambiar la visibilidad de una regla a `internal`.
- `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Firmas nuevas.
- Visibilidades cambiadas.
- Pruebas: número y resultado.
- PNG y crítica.
