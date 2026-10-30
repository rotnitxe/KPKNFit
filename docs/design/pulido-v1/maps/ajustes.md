# Ajustes · mapa del paquete `aj` (punto 22)
Archivos: `src/scenes-aj.js`, `src/aj.css`. Inventario completo con archivo:línea: `maps/ajustes-inventario.md`. Todas las escenas son interactivas (un solo motor
de pantalla: cada escena es un estado inicial; el atrás cierra primero diálogo/hoja y luego la subpantalla). Última escena = documento «Veredictos».

## 1. Piezas reales de la app que se sustituyen
Rutas = `android-native/app/src/main/java/com/example/kpkn/`.
- `screens/settings/SettingsScreen.kt` (LazyColumn de 10 bloques con `Card` de Material, `Scaffold` + `TopAppBar`) → nueva pantalla principal + 5 subpantallas.
- `TrainingSettingsBlock.kt` (descanso por defecto + iniciar descanso) → se elimina.
- `equipment/*` (4 archivos) + ruta `KpknRoute.EquipmentInventory` (Navigation.kt:164, MainActivity.kt:1491) → se eliminan; el dato lo sigue escribiendo el asistente.
- `NutritionSourcesDialog.kt` → hoja «Fuentes y licencias» (misma lista `nutritionNotices`).
- `export/CsvExportSheet.kt` + `CsvExportViewModel.kt` → se conservan; solo cambia la presentación (módulo → filas con filete; botón desactivado sin selección).
- `components/SettingsListItems.kt` (462 líneas, sin usos) → se borra.
- `SettingsViewModel.kt`: se conservan `exportData`, `importBackupJson`, `exportDatabaseSnapshot`, `importDatabaseSnapshot`, `exportWorkoutMediaZip`, `clearAllAppData`, `resetTours` y la lógica de reprogramar recordatorios (`handleReminderChanges`, ya escrita).

## 2. Escena → pieza de la app
| Escena | Estado / pieza |
|---|---|
| `aj-main` | Pantalla principal (nueva). Filas: Unidades, Recordatorios, Permisos, Health Connect (solo flavor `health`), Copia y exportación, Ayuda y acerca de, Borrar todos los datos |
| `aj-unidades` | Hoja de elección de `Settings.weightUnit` (campo existente) |
| `aj-recordatorios` / `-vacio` / `-alarmas` / `-permiso` | Subpantalla sobre `workoutReminder*`, `mealReminder*`, `sleepReminder*` (campos y programadores existentes). Avisos de permiso: `POST_NOTIFICATIONS` y `PermissionGuideHelper.isExactAlarmGranted` |
| `aj-hora` | Selector de hora «HH:mm» (formato que ya guardan los campos) |
| `aj-permisos` / `aj-permiso-dialogo` | `permissionRows()` + `PermissionGuideHelper` (6 filas, con estados concedido / activar / abrir ajustes) |
| `aj-copia` | 4 filas que sustituyen a «Datos» y «Backup» |
| `aj-guardar` / `-error` | `exportData` (JSON) y `exportDatabaseSnapshot` (.db) detrás de una hoja de elección |
| `aj-restaurar` | `importBackupJson` / `importDatabaseSnapshot`; confirmación nueva también para .db |
| `aj-csv` / `-error` | `CsvExportSheet` (dominios `CsvExportDomain`) |
| `aj-ayuda` | Recorridos (`resetTours`), Fuentes, Diagnóstico y versión (`versionName`) |
| `aj-fuentes` | `NutritionSourcesDialog` |
| `aj-diagnostico` | Tarjeta «Telemetría JSONL»: exportar ZIP (`KpknDiagnosticLogger.exportAllTo`), carpeta (`KpknDiagnosticStorage.configure/mirrorRecoveryFiles/clear`) |
| `aj-borrar` | `clearAllAppData` con confirmación nueva |
| `aj-veredictos` | Documento: tabla de veredictos y preguntas |

## 3. Decisiones (con razón)
1. **Solo se quedan los ajustes que son globales**: unidades, recordatorios, permisos, conexiones, datos, ayuda, borrar. El resto sale o ya vive en su pantalla (descanso → editor; vista de sesión → sesión; técnica → su hub; modo de voz → la sesión).
2. **Descanso por defecto e «Iniciar descanso al registrar»: eliminados** (orden del usuario). El 90 s pasa a constante; el arranque del descanso queda siempre activo. Sin migración de Room.
3. **Mi material: sale de Ajustes** (orden del usuario) pero el dato se queda: el motor lo usa en 12+ sitios (progresión redondeada a discos, calculadora de discos, máquinas, sustituciones). Ya lo pregunta el asistente (pasos EQUIPMENT / INVENTORY_*), así que no hay hueco funcional salvo corregir un disco sin reabrir el asistente (pregunta 1).
4. **`ask` — Unidades y Recordatorios son `new`**: existen en el motor y no tienen pantalla. Unidades es el ejemplo más claro de ajuste global (14 carpetas lo leen; el CSV ya habla de «unidades de Ajustes»). Recordatorios: motor completo y nadie puede activarlo.
5. **Sin interruptores de sonido/vibración ni de idioma**: ya obedecen el timbre del sistema; y no hay ni un `stringResource` en `screens/`, así que un selector de idioma no cambiaría casi nada.
6. **JSON y .db se funden en «Guardar copia» / «Restaurar copia»** con hoja de elección (cuatro filas casi iguales → dos). Los cuatro caminos de código se conservan.
7. **La copia .db pide confirmación** (hoy reemplaza la base al tocar). Es la única acción destructiva de Ajustes sin aviso.
8. **Borrar todo: mantener pulsado 1,2 s**, lista de lo que se borra y lo que no, y «Guardar una copia antes». Alternativa accesible: doble toque con TalkBack / Enter con teclado; con «reducir movimiento» el relleno es instantáneo pero el tiempo se mantiene.
9. **Permisos**: Health Connect sale de la lista (es una conexión; hoy siempre dice «Activar»). Estados: concedido / activar / bloqueado → «Abrir ajustes». Los textos del micrófono y la cámara se amplían a sus usos reales (dictado de comidas; escáner y técnica).
10. **Diseño**: negro continuo, sin tarjetas en la navegación; módulos `#111` solo donde hay controles de datos (recordatorios, carpeta de diagnóstico); hojas gris plano `#1B1B1D`; interruptor 52 × 32 de la app; títulos Syne 700; cifras Inter tabulares; ámbar = falta algo, rojo = bloqueado/destructivo, verde = concedido.
11. Copia de texto corregida: «Exportación completada (1 archivos)» → «1 archivo»; avisos de copia más cortos («Copia guardada.»).

## 4. Qué exige implementarlo
- **Sin Room, sin migración**: todos los campos ya existen en `Settings` (JSON).
- Quitar: `TrainingSettingsBlock`, `equipment/*` y su ruta, `SettingsListItems`, tarjeta de bienvenida; `restTimerDefaultSeconds` y `autoStartRestAfterRecording` dejan de leerse (constantes en `WorkoutSetRecorder`, `Calculations`, `SessionAdvisor`, `SessionEditorViewModel`).
- Nuevos composables: filas de ajustes, subpantallas, selector de hora (par de `LazyColumn` o `TimePicker` en hoja), hoja de confirmación de restaurar .db, botón «mantener pulsado».
- Permisos: estado «bloqueado» = `shouldShowRequestPermissionRationale` + «ya se pidió» (hay un precedente: `locationPermissionRequestedOnce`). Recordatorios reutilizan `handleReminderChanges` tal cual.
- Sin motor nuevo. No se toca Kotlin en esta ola (solo prototipo).

## 5. Dudas para el usuario (también en la escena Veredictos)
1. **Mi material**: ¿solo en el asistente, o además un acceso desde el Perfil (nunca desde Ajustes)? Recomiendo solo el asistente.
2. **Recordatorios**: ¿pantalla nueva o que no existan? (motor completo y sin pantalla hoy).
3. **Diagnóstico**: ¿se mantiene la carpeta externa (solo se configura aquí) o basta el ZIP?
4. **Voz** (verbosidad, ruido, velocidad): ¿valores fijos (recomendado) o un bloque de tres filas?
5. **Descanso por defecto**: ¿se pierde el valor de quien lo cambió (recomendado) o se escribe una vez en los ejercicios sin descanso?

## 6. Pendiente / flojo
- Los textos de los 4 avisos de licencia que no son `NOTICE.txt` se muestran como líneas de lectura (el texto real lo lee la app del APK).
- Health Connect: solo la fila; su pantalla ya existe y no se redibuja aquí.
- Con texto al 130 % y 360 × 740 la pantalla principal pasa a desplazarse (Borrar queda debajo): aceptado.
