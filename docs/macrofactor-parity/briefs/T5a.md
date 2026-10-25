# T5a · Panel de series por músculo y ventana, con el cuerpo coloreado por volumen

**Prioridad P1 · esfuerzo M.** Informe §4.6 (V2, V4). Depende de **T2** (ventana y contabilidad de volumen, ya integrada: `domain/training/TrainingWindow.kt` y el cálculo canónico con contribuyentes).

## Por qué
Hoy el volumen solo se ve **dentro de un programa** (pestaña Volumen) y es *plan* frente a *real*. No hay una vista del trabajo **realizado** por músculo en una ventana de tiempo común («¿cuántas series por semana hice en pecho las últimas 4 semanas, y con qué ejercicios?»). La silueta del cuerpo (`ui/components/rings/RingAdjustBody.kt`) solo pinta la recuperación de AUGE, en dos tonos y sin explicar nada. Hay que dar **un panel** que se pueda abrir desde Perfil y desde el programa, con una unidad, una ventana y un desglose que reconcilien, y la silueta como segunda vista de los MISMOS números.

## Reglas de significado (no negociables)
- **Misma ventana, misma contabilidad que T2:** usa `TrainingWindow` (calendario, zona horaria explícita) y el cálculo canónico por ocurrencia/rol; no reimplementes el conteo. Ventanas: **1 semana** (últimos 7 días), **4 semanas** (últimos 28 días) y **12 semanas** (últimos 84 días), con el divisor de T2 (promedio semanal) y un conmutador «promedio por semana / total».
- Modos de rol: Directo · +Secundario · +Estabilizador (los mismos que `VolumeView`), con ajuste por intensidad opcional como allí.
- **La suma del desglose por ejercicio = el valor mostrado**, en cada ventana y modo.
- **Cero ≠ sin datos:** un músculo con 0 series en una ventana con registros se muestra 0; una ventana sin ningún registro (o con todos sin fecha asignable) se muestra «sin datos» con su recuento de registros sin asignar. Nada se colorea «perfecto» por ausencia.
- No es recuperación ni carga: es **series registradas**. Rotúlalo así (unidad «series/sem»).

## Cambio exacto
1. **Dominio (`domain/training/MuscleVolumePanel.kt`, puro):** dado el historial de registros, la ventana, el modo y el ajuste, devuelve filas por grupo muscular con: series (promedio o total), contribuyentes (ejercicio/configuración, series aportadas, nº de sesiones, última fecha), porciones (cabezas) si existen, `unassignedCount`, y un indicador `hasData`. Se apoya en lo de T2; puedes LEER como referencia `C:\kw\ref\mf-wip\untracked\android-native\app\src\main\java\com\example\kpkn\domain\training\PerformanceMetricProjection.kt` y `…\AugeRecoveryMapProjection.kt` (mismo problema), sin aplicarlos como parche.
2. **Pantalla `screens/progress/MuscleVolumePanelScreen` + ViewModel** (`StateFlow` de solo lectura, cálculo fuera del hilo principal):
   - Cabecera con selector de ventana (1 / 4 / 12 semanas), conmutador promedio/total y modo de roles.
   - **Vista «Lista»:** una fila por grupo (nombre, series, barra relativa al máximo de la ventana y, si el programa tiene `volumeRecommendations`, sus zonas); tocar la fila la despliega con los ejercicios que la explican y cuánto aporta cada uno. Esta lista es también la **alternativa accesible** al mapa.
   - **Vista «Cuerpo»:** frente y espalda con la geometría de regiones que ya existe (`MuscleArtPaths`/`RingAdjustBody`), coloreando cada región con una escala de intensidad clara (leyenda con unidades). Tocar una región abre su desglose (el mismo de la lista). Las regiones sin datos usan un trazo neutro distinto del «0».
   - Un conmutador **Volumen | Recuperación AUGE** en la silueta: «Recuperación» reutiliza lo que ya pinta hoy (no lo quites ni lo cambies); «Volumen» es lo nuevo. Cada modo con su leyenda y su unidad; no se mezclan escalas.
   - Estados: cargando, sin datos, con datos, con registros sin asignar (línea discreta con el recuento).
3. **Rutas y entradas** (bloques nuevos en `navigation/Navigation.kt` y `MainActivity.kt`; eres dueño de las rutas en este paquete): `KpknRoute.MuscleVolumePanel`. Entradas: (a) un bloque «Progreso» en `screens/profile/ProfileScreen.kt` con una tarjeta «Volumen por músculo» (la de T5b se añade después en el mismo bloque); (b) un enlace discreto en la pestaña Volumen del detalle del programa («Ver lo que has hecho en 4 semanas»). No rediseñes Perfil ni la pestaña: añade una pieza.
4. **Diseño:** KPKN v2 (`docs/design/KPKN_UI_V2.md`, `S_COMUN.md`, `docs/android/SKILL_COMPOSE_CANVAS.md` para el lienzo), contenedores con criterio, español, objetivos ≥ 48 dp, TalkBack: el mapa expone una descripción resumen y la lista hace de alternativa. Respeta `reducedMotion`. Hoja de render (PNG a 360 dp, 100 % y 130 %): lista con datos, cuerpo con datos, sin datos, con registros sin asignar, regiones a 0 frente a sin datos. Máx. 3 rondas.
5. **Vista previa en dispositivo (COMUN.md):** `MuscleVolumePanelHarnessActivity` con `state` = `data` | `empty` | `sparse` | `unassigned` | `body` | `recovery` y datos en memoria.

## Archivos permitidos
- Nuevos: `domain/training/MuscleVolumePanel.kt`, `screens/progress/*`, `app/src/debug/.../MuscleVolumePanelHarnessActivity.kt` + bloque de manifiesto; tests y hoja de render.
- `ui/components/rings/RingAdjustBody.kt` (solo para exponer la geometría/regiones reutilizables SIN cambiar su aspecto actual), `screens/profile/ProfileScreen.kt` (el bloque «Progreso»), `screens/programdetail/components/VolumeView.kt` (solo el enlace), `navigation/Navigation.kt`, `MainActivity.kt` (rutas nuevas).
- `docs/macrofactor-parity/CAMBIOS_VISIBLES.md`.

## Pruebas obligatorias
- Para 1, 4 y 12 semanas, en los tres modos de rol y con/sin ajuste por intensidad: la suma del desglose por ejercicio = el total de la fila.
- Ventana sin registros → `hasData = false` (no ceros); ventana con registros y un músculo sin series → 0; registros sin fecha → `unassignedCount` y no suman.
- Promedio vs total: 6 sesiones de 2 series en 1 semana = 12/sem; la misma carga repartida en 3 semanas = 4/sem (coherente con T2).
- Zona horaria: un registro a las 23:30 locales cuenta en su día local.
- ViewModel: cambiar ventana/modo recalcula; selección de región ↔ fila desplegada.
- Mapa: cada región pinta el color de su intensidad; «sin datos» ≠ «0» (test del modelo de colores, no de píxeles).
- Regresión: `VolumeView` y la pestaña Volumen del programa no cambian de cifras.

## Comandos
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.training.*" --tests "com.example.kpkn.screens.progress.*" --tests "com.example.kpkn.screens.programdetail.*" --tests "com.example.kpkn.screens.profile.*"`
3. La hoja: `KPKN_RENDER_DIR=C:/kw/shots/mfp/T5a` con `--tests "com.example.kpkn.render.MuscleVolumePanelRenderSheetTest"`.

## Fuera de alcance
La vista de progreso por ejercicio y los récords (T5b); cambiar la ponderación de roles; tocar AUGE; mapa de planificado (el plan sigue en la pestaña Volumen del programa).
