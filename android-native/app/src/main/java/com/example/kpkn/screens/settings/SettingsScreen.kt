package com.example.kpkn.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.BuildConfig
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.export.CsvExportService
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.areMealRemindersAvailable
import com.example.kpkn.screens.settings.export.CsvExportSheet
import com.example.kpkn.screens.settings.export.CsvExportViewModel
import com.example.kpkn.services.diagnostics.KpknDiagnosticStorage
import com.example.kpkn.services.workout.PermissionGuideHelper
import com.example.kpkn.services.workout.WorkoutVoicePermissionHelper
import com.example.kpkn.ui.design.motion.KpknAnimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pantallas de Ajustes (paquete `aj`): la principal y cinco subpantallas. El atrás cierra primero hoja y aviso, luego sube un nivel. */
internal enum class AjRoute(val parent: AjRoute?) {
    Main(null), Reminders(Main), Permissions(Main), Copy(Main), Help(Main), Diagnostics(Help),
}

private enum class AjSheetKind { Units, Time, Save, Restore, Csv, Sources, Delete }

private class AjToastData(val id: Int, val message: String, val kind: AjToastKind)

private const val PermissionPrefs = "aj_permissions"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    healthConnectAvailable: Boolean = false,
    onOpenHealthConnect: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    LaunchedEffect(context) { viewModel.setContext(context) }

    // --- navegación y hojas ---
    var routeName by rememberSaveable { mutableStateOf(AjRoute.Main.name) }
    val route = AjRoute.valueOf(routeName)
    var lastNav by remember { mutableStateOf<KpknAnimation?>(null) }
    var sheetName by rememberSaveable { mutableStateOf<String?>(null) }
    val sheet = sheetName?.let { AjSheetKind.valueOf(it) }
    var timeKey by rememberSaveable { mutableStateOf("w") }
    fun openSheet(kind: AjSheetKind) { sheetName = kind.name }
    fun closeSheet() { sheetName = null }
    fun go(next: AjRoute) { lastNav = AjMotion.Push; routeName = next.name }
    fun goBack() {
        val parent = route.parent
        if (parent == null) onBack() else { lastNav = AjMotion.Pop; routeName = parent.name }
    }
    BackHandler(enabled = route != AjRoute.Main) { goBack() }

    // --- avisos (aj-J01: 2,9 s) ---
    var toast by remember { mutableStateOf<AjToastData?>(null) }
    var toastCount by remember { mutableIntStateOf(0) }
    fun flash(message: String, kind: AjToastKind = AjToastKind.Neutral) { toastCount++; toast = AjToastData(toastCount, message, kind) }
    LaunchedEffect(toast?.id) { if (toast != null) { delay(AjMotion.ToastMs); toast = null } }
    fun reason(error: String) = error.replaceFirstChar { it.lowercase() }.trimEnd('.') + "."

    // --- permisos ---
    var refreshToken by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshToken++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshToken++ }
    val permissionStates = remember(refreshToken, context) { AjPerm.entries.associateWith { permissionState(context, it) } }
    val pending = permissionStates.values.count { it != AjPermState.GRANTED }
    val exactAlarms = remember(refreshToken, context) { PermissionGuideHelper.isExactAlarmGranted(context) }
    fun onPerm(perm: AjPerm) {
        when {
            permissionStates[perm] == AjPermState.GRANTED -> Unit
            perm == AjPerm.Alarms ->
                if (!exactAlarms) PermissionGuideHelper.openExactAlarmSettings(context) else PermissionGuideHelper.openBatteryOptimizationSettings(context)
            permissionStates[perm] == AjPermState.BLOCKED -> PermissionGuideHelper.openAllPermissionSettings(context)
            else -> {
                markRequested(context, perm)
                permissionLauncher.launch(permissionNames(perm))
            }
        }
    }

    // --- CSV (conserva su modelo) ---
    val csvModel: CsvExportViewModel = viewModel(key = "csv-export") {
        CsvExportViewModel(CsvExportService(context.applicationContext, ProgramRepository.getInstance()) { KpknDatabase.getInstance(context.applicationContext) })
    }
    val csvState by csvModel.state.collectAsStateWithLifecycle()
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { csvModel.destinationChosen(it) }
    val csvZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { csvModel.destinationChosen(it) }
    LaunchedEffect(csvState.request?.token) {
        csvState.request?.let { request ->
            csvModel.acknowledge(request.token)
            if (request.mimeType == "application/zip") csvZipLauncher.launch(request.name) else csvLauncher.launch(request.name)
        }
    }
    LaunchedEffect(csvState.notice) {
        csvState.notice?.let { notice ->
            if (sheet == AjSheetKind.Csv && csvState.error == null) closeSheet()
            flash(notice, if (csvState.error != null) AjToastKind.Bad else AjToastKind.Ok)
            csvModel.consumeNotice()
        }
    }

    // --- guardar y restaurar copias ---
    var saveKind by rememberSaveable { mutableStateOf(AjBackupKind.Json.name) }
    var saveBusy by remember { mutableStateOf(false) }
    val jsonExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        saveBusy = true
        viewModel.exportData(
            context, uri,
            onSuccess = { saveBusy = false; closeSheet(); flash("Copia guardada.", AjToastKind.Ok) },
            onError = { saveBusy = false; closeSheet(); flash("No se pudo guardar: ${reason(it)}", AjToastKind.Bad) },
        )
    }
    val dbExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-sqlite3")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        saveBusy = true
        viewModel.exportDatabaseSnapshot(
            context, uri,
            onSuccess = { saveBusy = false; closeSheet(); flash("Copia completa guardada.", AjToastKind.Ok) },
            onError = { saveBusy = false; closeSheet(); flash("No se pudo guardar: ${reason(it)}", AjToastKind.Bad) },
        )
    }
    var restoreKind by rememberSaveable { mutableStateOf(AjBackupKind.Json.name) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreName by remember { mutableStateOf("") }
    var restoreBusy by remember { mutableStateOf(false) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        restoreUri = uri
        restoreName = displayName(context, uri)
    }
    fun restore() {
        val uri = restoreUri ?: return
        restoreBusy = true
        if (restoreKind == AjBackupKind.Json.name) {
            viewModel.importBackupJson(
                context, uri,
                onSuccess = { restoreBusy = false; restoreUri = null; closeSheet(); flash("Copia restaurada.", AjToastKind.Ok) },
                onError = { restoreBusy = false; restoreUri = null; closeSheet(); flash("No se pudo restaurar: ${reason(it)}", AjToastKind.Bad) },
            )
        } else {
            viewModel.importDatabaseSnapshot(
                context, uri,
                onSuccess = { restoreBusy = false; restoreUri = null; closeSheet(); flash("Base de datos restaurada.", AjToastKind.Ok) },
                onError = { restoreBusy = false; restoreUri = null; closeSheet(); flash("No se pudo restaurar: ${reason(it)}", AjToastKind.Bad) },
            )
        }
    }

    // --- álbumes, diagnóstico y borrado ---
    val mediaZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportWorkoutMediaZip(
            context, uri,
            onSuccess = { count ->
                if (count == 0) flash("No había medios para exportar.")
                else flash("ZIP exportado ($count ${if (count == 1) "archivo" else "archivos"}).", AjToastKind.Ok)
            },
            onError = { flash("No se pudo exportar: ${reason(it)}", AjToastKind.Bad) },
        )
    }
    var telemetryFolder by remember { mutableStateOf(KpknDiagnosticStorage.configuredLabel(context)) }
    val diagnosticsExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            KpknDiagnosticLogger.flushSync()
            val exported = KpknDiagnosticLogger.exportAllTo(context, uri)
            withContext(Dispatchers.Main) {
                if (exported) flash("ZIP de registros exportado.", AjToastKind.Ok) else flash("No se pudo exportar el registro.", AjToastKind.Bad)
            }
        }
    }
    val telemetryFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        KpknDiagnosticStorage.configure(context, uri)
            .onSuccess { label -> telemetryFolder = label; flash("Carpeta configurada: $label.", AjToastKind.Ok) }
            .onFailure { error -> flash("No se pudo configurar la carpeta: ${reason(error.message ?: "error desconocido")}", AjToastKind.Bad) }
    }
    var deleting by remember { mutableStateOf(false) }
    fun deleteAll() {
        deleting = true
        viewModel.clearAllAppData(
            context,
            onSuccess = { deleting = false; telemetryFolder = null; closeSheet(); lastNav = null; routeName = AjRoute.Main.name; flash("La app volvió a cero.", AjToastKind.Ok) },
            onError = { deleting = false; flash("No se pudo borrar todo: ${reason(it)}", AjToastKind.Bad) },
        )
    }

    // --- recordatorios ---
    fun toggle(which: AjReminder, on: Boolean) = viewModel.update {
        when (which) {
            AjReminder.Workout -> it.copy(workoutReminderEnabled = on)
            AjReminder.Meals -> it.copy(mealReminderEnabled = on)
            AjReminder.Sleep -> it.copy(sleepReminderEnabled = on)
        }
    }
    val showMeals = areMealRemindersAvailable(settings)
    val enter = lastNav

    Box(Modifier.fillMaxSize()) {
        when (route) {
            AjRoute.Main -> AjMainScreen(
                unit = settings.weightUnit,
                activeReminders = ajActiveReminders(settings.workoutReminderEnabled, settings.mealReminderEnabled, settings.sleepReminderEnabled, showMeals),
                pendingPermissions = pending,
                healthAvailable = healthConnectAvailable,
                onBack = ::goBack, onUnits = { openSheet(AjSheetKind.Units) }, onReminders = { go(AjRoute.Reminders) },
                onPermissions = { go(AjRoute.Permissions) }, onHealth = onOpenHealthConnect, onCopy = { go(AjRoute.Copy) },
                onHelp = { go(AjRoute.Help) }, onDelete = { openSheet(AjSheetKind.Delete) }, enter = enter,
            )
            AjRoute.Reminders -> AjRemindersScreen(
                notifications = permissionStates[AjPerm.Notifications] ?: AjPermState.DENIED,
                exactAlarmsGranted = exactAlarms,
                workoutOn = settings.workoutReminderEnabled, workoutTime = settings.workoutReminderTime,
                showMeals = showMeals, mealsOn = settings.mealReminderEnabled,
                breakfast = settings.mealReminderBreakfast, lunch = settings.mealReminderLunch, dinner = settings.mealReminderDinner,
                sleepOn = settings.sleepReminderEnabled, sleepTime = settings.sleepReminderTime,
                onBack = ::goBack, onToggle = ::toggle,
                onPickTime = { key -> timeKey = key; openSheet(AjSheetKind.Time) },
                onNotifications = { onPerm(AjPerm.Notifications) },
                onAlarmSettings = { PermissionGuideHelper.openExactAlarmSettings(context) },
                enter = enter,
            )
            AjRoute.Permissions -> AjPermissionsScreen(permissionStates, ::goBack, ::onPerm, enter)
            AjRoute.Copy -> AjCopyScreen(
                onBack = ::goBack,
                onSave = { openSheet(AjSheetKind.Save) },
                onRestore = { restoreUri = null; restoreBusy = false; openSheet(AjSheetKind.Restore) },
                onCsv = { openSheet(AjSheetKind.Csv) },
                onZip = { mediaZipLauncher.launch("kpkn-workout-media.zip") },
                enter = enter,
            )
            AjRoute.Help -> AjHelpScreen(
                versionLine = "${BuildConfig.VERSION_NAME} · versión ${BuildConfig.VERSION_CODE}",
                hasFolder = telemetryFolder != null,
                onBack = ::goBack,
                onTours = { viewModel.resetTours(); flash("Recorridos restablecidos.", AjToastKind.Ok) },
                onSources = { openSheet(AjSheetKind.Sources) },
                onDiagnostics = { go(AjRoute.Diagnostics) },
                enter = enter,
            )
            AjRoute.Diagnostics -> AjDiagnosticsScreen(
                folderLabel = telemetryFolder,
                onBack = ::goBack,
                onExportZip = { diagnosticsExportLauncher.launch(KpknDiagnosticLogger.suggestedFileName()) },
                onPickFolder = { telemetryFolderLauncher.launch(null) },
                onSync = {
                    scope.launch(Dispatchers.IO) { KpknDiagnosticLogger.flushSync(); KpknDiagnosticStorage.mirrorRecoveryFiles(context) }
                    flash("Sincronización iniciada.")
                },
                onUnlink = { KpknDiagnosticStorage.clear(context); telemetryFolder = null },
                enter = enter,
            )
        }

        when (sheet) {
            AjSheetKind.Units -> AjUnitsSheet(
                settings.weightUnit,
                // aj-J02: la hoja se cierra sola 280 ms después de elegir, para ver el valor marcado
                { unit -> viewModel.update { it.copy(weightUnit = unit) }; scope.launch { delay(280); closeSheet() } },
                ::closeSheet,
            )
            AjSheetKind.Time -> {
                val current = when (timeKey) {
                    "w" -> settings.workoutReminderTime
                    "s" -> settings.sleepReminderTime
                    "b" -> settings.mealReminderBreakfast
                    "l" -> settings.mealReminderLunch
                    else -> settings.mealReminderDinner
                }
                val (h, m) = ajParseTime(current)
                AjTimeSheet(
                    title = when (timeKey) { "w" -> "Entrenar"; "b" -> "Desayuno"; "l" -> "Almuerzo"; "d" -> "Cena"; else -> "Dormir" },
                    hour = h, minute = m,
                    onDone = { nh, nm ->
                        val value = ajFormatTime(nh, nm)
                        viewModel.update {
                            when (timeKey) {
                                "w" -> it.copy(workoutReminderTime = value)
                                "s" -> it.copy(sleepReminderTime = value)
                                "b" -> it.copy(mealReminderBreakfast = value)
                                "l" -> it.copy(mealReminderLunch = value)
                                else -> it.copy(mealReminderDinner = value)
                            }
                        }
                    },
                    onDismiss = ::closeSheet,
                )
            }
            AjSheetKind.Save -> AjSaveSheet(
                kind = AjBackupKind.valueOf(saveKind), busy = saveBusy,
                onKind = { saveKind = it.name },
                onSave = { if (saveKind == AjBackupKind.Json.name) jsonExportLauncher.launch("kpkn-backup.json") else dbExportLauncher.launch("kpkn-backup.db") },
                onDismiss = ::closeSheet,
            )
            AjSheetKind.Restore -> AjRestoreSheet(
                confirming = restoreUri != null, kind = AjBackupKind.valueOf(restoreKind), fileName = restoreName, busy = restoreBusy,
                onKind = { restoreKind = it.name },
                onPickFile = {
                    if (restoreKind == AjBackupKind.Json.name) restorePicker.launch(arrayOf("application/json", "text/plain"))
                    else restorePicker.launch(arrayOf("application/x-sqlite3", "application/octet-stream", "*/*"))
                },
                onRestore = ::restore,
                onDismiss = { restoreUri = null; closeSheet() },
            )
            AjSheetKind.Csv -> CsvExportSheet(csvState, csvModel::prepare, ::closeSheet)
            AjSheetKind.Sources -> NutritionSourcesSheet(::closeSheet)
            AjSheetKind.Delete -> AjDeleteSheet(
                deleting = deleting,
                onSaveFirst = { openSheet(AjSheetKind.Save) },
                onConfirm = ::deleteAll,
                onDismiss = ::closeSheet,
            )
            null -> Unit
        }

        toast?.let { t -> Box(Modifier.align(Alignment.BottomCenter)) { AjToast(t.message, t.kind) } }
    }
}

/* ------------------------------------------------------------------------------------------------------------------------------ */
/* Permisos del sistema                                                                                                            */
/* ------------------------------------------------------------------------------------------------------------------------------ */

private fun permissionNames(perm: AjPerm): Array<String> = when (perm) {
    AjPerm.Notifications -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    AjPerm.Microphone -> arrayOf(Manifest.permission.RECORD_AUDIO)
    AjPerm.Location -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    AjPerm.Bluetooth -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
    AjPerm.Camera -> arrayOf(Manifest.permission.CAMERA)
    AjPerm.Alarms -> emptyArray()
}

private fun has(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun isGranted(context: Context, perm: AjPerm): Boolean = when (perm) {
    AjPerm.Notifications -> Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || has(context, Manifest.permission.POST_NOTIFICATIONS)
    AjPerm.Microphone -> has(context, Manifest.permission.RECORD_AUDIO)
    AjPerm.Location -> has(context, Manifest.permission.ACCESS_FINE_LOCATION) || has(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    AjPerm.Bluetooth -> WorkoutVoicePermissionHelper.hasBluetoothConnectPermission(context)
    AjPerm.Camera -> has(context, Manifest.permission.CAMERA)
    AjPerm.Alarms -> PermissionGuideHelper.getPermissionState(context).allOk
}

/**
 * Concedido / activar / bloqueado. Bloqueado = ya se pidió alguna vez y el sistema ya no ofrece el motivo (`shouldShowRequestPermissionRationale`
 * es falso): a partir de ahí solo se recupera desde los ajustes del sistema.
 */
private fun permissionState(context: Context, perm: AjPerm): AjPermState {
    if (isGranted(context, perm)) return AjPermState.GRANTED
    val names = permissionNames(perm)
    val activity = context.findActivity()
    val requested = context.getSharedPreferences(PermissionPrefs, Context.MODE_PRIVATE).getStringSet("requested", emptySet()).orEmpty()
    val blocked = names.isNotEmpty() && activity != null && perm.name in requested &&
        names.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    return if (blocked) AjPermState.BLOCKED else AjPermState.DENIED
}

private fun markRequested(context: Context, perm: AjPerm) {
    val prefs = context.getSharedPreferences(PermissionPrefs, Context.MODE_PRIVATE)
    prefs.edit().putStringSet("requested", prefs.getStringSet("requested", emptySet()).orEmpty() + perm.name).apply()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: (uri.lastPathSegment ?: "copia")
