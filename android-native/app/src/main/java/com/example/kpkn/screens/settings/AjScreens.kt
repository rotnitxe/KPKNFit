package com.example.kpkn.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.kpknTween
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Los seis accesos de la pantalla de permisos (las mismas filas de hoy menos Health Connect, que es una conexión). */
internal enum class AjPerm(val title: String, val description: String, val question: String, val systemOnly: Boolean = false) {
    Notifications("Notificaciones", "Recordatorios, avisos de descanso y estado de la sesión.", "te envíe notificaciones"),
    Microphone("Micrófono", "Control por voz durante el entrenamiento y dictado de comidas.", "grabe audio"),
    Location("Ubicación", "Rastreo GPS para sesiones de cardio.", "acceda a la ubicación de este dispositivo"),
    Bluetooth("Bluetooth", "Conexión con audífonos y accesorios compatibles.", "encuentre dispositivos cercanos y se conecte a ellos"),
    Camera("Cámara", "Fotos de ejercicios, técnica en vídeo y escáner de códigos.", "haga fotos y grabe vídeo"),
    Alarms("Alarmas y batería", "Permite alarmas precisas y evita que el sistema detenga los avisos.", "", systemOnly = true),
    ;

    val icon get() = when (this) {
        Notifications -> AjIcons.Bell
        Microphone -> AjIcons.Mic
        Location -> AjIcons.Pin
        Bluetooth -> AjIcons.Bluetooth
        Camera -> AjIcons.Camera
        Alarms -> AjIcons.Clock
    }
}

internal enum class AjBackupKind { Json, Db }
internal enum class AjReminder { Workout, Meals, Sleep }

/** Cuántos recordatorios hay activos (la cuenta de la fila de Ajustes): «Comidas» no cuenta si se saltó Nutrición. */
internal fun ajActiveReminders(workout: Boolean, meals: Boolean, sleep: Boolean, nutrition: Boolean): Int =
    listOf(workout, meals && nutrition, sleep).count { it }

/** «HH:mm» → (hora, minuto) con los límites del reloj; un texto raro cae a las 00:00. */
internal fun ajParseTime(value: String): Pair<Int, Int> {
    val parts = value.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return h to m
}

internal fun ajFormatTime(h: Int, m: Int): String = "%02d:%02d".format(((h % 24) + 24) % 24, ((m % 60) + 60) % 60)

private fun unitLabel(unit: WeightUnit) = if (unit == WeightUnit.KG) "kg" else "lb"

/* ------------------------------------------------------------------------------------------------------------------------------ */
/* Pantallas                                                                                                                       */
/* ------------------------------------------------------------------------------------------------------------------------------ */

@Composable
internal fun AjMainScreen(
    unit: WeightUnit,
    activeReminders: Int,
    pendingPermissions: Int,
    healthAvailable: Boolean,
    onBack: () -> Unit,
    onUnits: () -> Unit,
    onReminders: () -> Unit,
    onPermissions: () -> Unit,
    onHealth: () -> Unit,
    onCopy: () -> Unit,
    onHelp: () -> Unit,
    onDelete: () -> Unit,
    enter: com.example.kpkn.ui.design.motion.KpknAnimation? = null,
) {
    AjPage("Ajustes", onBack, enter = enter) {
        AjGroup("Preferencias")
        AjRow(AjIcons.Ruler, "Unidades", onUnits, value = unitLabel(unit), modifier = Modifier.testTag("aj-row-units"))
        AjRow(
            AjIcons.Bell, "Recordatorios", onReminders,
            value = if (activeReminders > 0) "$activeReminders ${if (activeReminders == 1) "activo" else "activos"}" else "Sin activar",
            divider = true, modifier = Modifier.testTag("aj-row-reminders"),
        )
        AjGroup("Sistema")
        AjRow(
            AjIcons.Shield, "Permisos", onPermissions,
            value = if (pendingPermissions > 0) "$pendingPermissions por activar" else "Todo concedido",
            valueColor = if (pendingPermissions > 0) AjColors.Ene else AjColors.Done, dot = pendingPermissions > 0,
            modifier = Modifier.testTag("aj-row-permissions"),
        )
        if (healthAvailable) AjRow(AjIcons.Heart, "Health Connect", onHealth, divider = true, modifier = Modifier.testTag("aj-row-health"))
        AjGroup("Datos")
        AjRow(AjIcons.Folder, "Copia y exportación", onCopy, modifier = Modifier.testTag("aj-row-copy"))
        AjGroup("KPKN")
        AjRow(AjIcons.Lifebuoy, "Ayuda y acerca de", onHelp, modifier = Modifier.testTag("aj-row-help"))
        Column(
            Modifier.padding(top = 34.dp).fillMaxWidth()
                .drawBehind { drawLine(Color(0x0FFFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(top = 6.dp),
        ) {
            AjRow(
                AjIcons.Trash, "Borrar todos los datos", onDelete,
                sub = "Borra tu progreso, rutinas, nutrición, foto y configuraciones de la app.",
                bad = true, chevron = false, modifier = Modifier.testTag("aj-row-delete"),
            )
        }
    }
}

@Composable
internal fun AjRemindersScreen(
    notifications: AjPermState,
    exactAlarmsGranted: Boolean,
    workoutOn: Boolean,
    workoutTime: String,
    showMeals: Boolean,
    mealsOn: Boolean,
    breakfast: String,
    lunch: String,
    dinner: String,
    sleepOn: Boolean,
    sleepTime: String,
    onBack: () -> Unit,
    onToggle: (AjReminder, Boolean) -> Unit,
    onPickTime: (String) -> Unit,
    onNotifications: () -> Unit,
    onAlarmSettings: () -> Unit,
    enter: com.example.kpkn.ui.design.motion.KpknAnimation? = null,
) {
    val active = ajActiveReminders(workoutOn, mealsOn, sleepOn, showMeals)
    AjPage("Recordatorios", onBack, enter = enter) {
        if (notifications != AjPermState.GRANTED) {
            val blocked = notifications == AjPermState.BLOCKED
            AjWarn(
                AjIcons.Warn, "Las notificaciones están desactivadas.",
                if (blocked) "El sistema ya no pregunta: actívalas en los ajustes del sistema." else "Sin ellas, los recordatorios no llegan.",
                blocked, if (blocked) "Abrir ajustes del sistema" else "Activar notificaciones", blocked, onNotifications,
            )
        } else if (!exactAlarmsGranted && active > 0) {
            AjWarn(
                AjIcons.Clock, "Pueden llegar con retraso.",
                "Sin el permiso de alarmas precisas, el sistema decide la hora exacta del aviso.",
                false, "Abrir ajustes del sistema", true, onAlarmSettings,
            )
        }
        AjModule {
            AjModuleHeader(AjIcons.Gym, "Entrenar", "Un aviso al día", workoutOn, { onToggle(AjReminder.Workout, it) }, "aj-rem-workout")
            if (workoutOn) {
                AjTimeLine("Hora", workoutTime, { onPickTime("w") }, "aj-time-w")
                AjReceives("¿Ya está todo listo para entrenar?")
            }
        }
        if (showMeals) AjModule {
            AjModuleHeader(AjIcons.Fork, "Comidas", "Para registrar lo que comes", mealsOn, { onToggle(AjReminder.Meals, it) }, "aj-rem-meals")
            if (mealsOn) {
                AjTimeLine("Desayuno", breakfast, { onPickTime("b") }, "aj-time-b")
                AjTimeLine("Almuerzo", lunch, { onPickTime("l") }, "aj-time-l")
                AjTimeLine("Cena", dinner, { onPickTime("d") }, "aj-time-d")
            }
        }
        AjModule {
            AjModuleHeader(AjIcons.Moon, "Dormir", "Un aviso para prepararte", sleepOn, { onToggle(AjReminder.Sleep, it) }, "aj-rem-sleep")
            if (sleepOn) {
                AjTimeLine("Hora", sleepTime, { onPickTime("s") }, "aj-time-s")
                AjReceives("Es hora de prepararse para descansar")
            }
        }
    }
}

@Composable
internal fun AjPermissionsScreen(
    states: Map<AjPerm, AjPermState>,
    onBack: () -> Unit,
    onPerm: (AjPerm) -> Unit,
    enter: com.example.kpkn.ui.design.motion.KpknAnimation? = null,
) {
    val anyBlocked = states.values.any { it == AjPermState.BLOCKED }
    AjPage("Permisos", onBack, lead = "KPKN te los pide también cuando hacen falta. Aquí puedes revisarlos o recuperarlos.", enter = enter) {
        Column(Modifier.padding(top = 14.dp)) {
            AjPerm.entries.forEachIndexed { i, perm ->
                AjPermRow(
                    perm.icon, perm.title, perm.description, states[perm] ?: AjPermState.DENIED, perm.systemOnly,
                    { onPerm(perm) }, divider = i > 0, modifier = Modifier.testTag("aj-perm-${perm.name}"),
                )
            }
        }
        if (anyBlocked) {
            Text(
                "Si el sistema deja de preguntar, se activa desde Ajustes del sistema › Aplicaciones › KPKN Fit › Permisos. Al volver, KPKN lo comprueba solo.",
                style = AjType.note, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp),
            )
        }
    }
}

@Composable
internal fun AjCopyScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    onCsv: () -> Unit,
    onZip: () -> Unit,
    enter: com.example.kpkn.ui.design.motion.KpknAnimation? = null,
) {
    AjPage("Copia y exportación", onBack, enter = enter) {
        AjGroup("Copia de seguridad")
        AjRow(AjIcons.Download, "Guardar copia", onSave, sub = "Rutinas, historial, nutrición y todo lo tuyo, en un archivo que eliges.", modifier = Modifier.testTag("aj-row-save"))
        AjRow(AjIcons.History, "Restaurar copia", onRestore, sub = "Recupera una copia guardada antes.", divider = true, modifier = Modifier.testTag("aj-row-restore"))
        AjGroup("Exportar")
        AjRow(AjIcons.Table, "Hoja de cálculo (CSV)", onCsv, sub = "Comidas, cuerpo, entrenos o tu biblioteca.", modifier = Modifier.testTag("aj-row-csv"))
        AjRow(AjIcons.Image, "Fotos y vídeos (ZIP)", onZip, sub = "Los álbumes privados de entrenamiento.", divider = true, modifier = Modifier.testTag("aj-row-zip"))
    }
}

@Composable
internal fun AjHelpScreen(
    versionLine: String,
    hasFolder: Boolean,
    onBack: () -> Unit,
    onTours: () -> Unit,
    onSources: () -> Unit,
    onDiagnostics: () -> Unit,
    enter: com.example.kpkn.ui.design.motion.KpknAnimation? = null,
) {
    AjPage("Ayuda y acerca de", onBack, lead = versionLine, enter = enter) {
        Column(Modifier.padding(top = 18.dp)) {
            AjRow(AjIcons.Repeat, "Volver a ver los recorridos", onTours, sub = "Las explicaciones de cada pantalla vuelven la próxima vez que entres.", chevron = false, modifier = Modifier.testTag("aj-row-tours"))
            AjRow(AjIcons.Book, "Fuentes y licencias", onSources, sub = "De dónde salen los datos de alimentos y bajo qué licencias se usan.", divider = true, modifier = Modifier.testTag("aj-row-sources"))
            AjRow(AjIcons.Pulse, "Diagnóstico", onDiagnostics, sub = "Registros técnicos para investigar un fallo.", value = if (hasFolder) "Con carpeta" else null, divider = true, modifier = Modifier.testTag("aj-row-diagnostics"))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AjDiagnosticsScreen(
    folderLabel: String?,
    onBack: () -> Unit,
    onExportZip: () -> Unit,
    onPickFolder: () -> Unit,
    onSync: () -> Unit,
    onUnlink: () -> Unit,
    enter: com.example.kpkn.ui.design.motion.KpknAnimation? = null,
) {
    AjPage(
        "Diagnóstico", onBack, enter = enter,
        lead = "KPKN guarda en tu móvil un registro técnico de cada área (entreno, voz, nutrición y app), un archivo por día. Exportarlo sirve para investigar un fallo.",
    ) {
        Column(Modifier.padding(top = 14.dp)) {
            AjRow(AjIcons.Upload, "Exportar registros (ZIP)", onExportZip, sub = "Todo el registro, en un archivo que eliges.", chevron = false, modifier = Modifier.testTag("aj-row-diag-zip"))
        }
        AjGroup("Copia en una carpeta")
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier.padding(top = 6.dp).fillMaxWidth().background(AjColors.Module, shape).border(1.dp, Color(0x12FFFFFF), shape).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AjIconCircle(AjIcons.Folder)
                Column(Modifier.weight(1f)) {
                    Text(folderLabel ?: "Sin carpeta externa", style = AjType.rowTitle.copy(fontSize = 15.sp))
                    Text(
                        if (folderLabel != null) "Cada registro se copia también aquí." else "Opcional: guarda una segunda copia donde tú quieras.",
                        style = AjType.rowSub, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (folderLabel != null) {
                    AjButton("Sincronizar ahora", onSync, tone = AjButtonTone.Ghost, small = true, tag = "aj-mirror-sync")
                    AjButton("Desvincular", onUnlink, tone = AjButtonTone.Line, small = true, tag = "aj-mirror-off")
                } else {
                    AjButton("Elegir carpeta", onPickFolder, small = true, tag = "aj-mirror-on")
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------------------------------------ */
/* Hojas                                                                                                                           */
/* ------------------------------------------------------------------------------------------------------------------------------ */

@Composable
internal fun AjUnitsSheet(unit: WeightUnit, onPick: (WeightUnit) -> Unit, onDismiss: () -> Unit) {
    AjSheet(onDismiss) { AjUnitsContent(unit, onPick) }
}

@Composable
internal fun ColumnScope.AjUnitsContent(unit: WeightUnit, onPick: (WeightUnit) -> Unit) {
    run {
        AjSheetTitle("Unidades de peso")
        AjSheetSub("Cómo ves tu peso corporal, tus marcas y lo que exportas. Tus datos siguen guardados en kg.")
        Column {
            AjOption("Kilogramos", unit == WeightUnit.KG, { onPick(WeightUnit.KG) }, sub = "kg", divider = false, tag = "aj-unit-kg")
            AjOption("Libras", unit == WeightUnit.LBS, { onPick(WeightUnit.LBS) }, sub = "lb", tag = "aj-unit-lb")
        }
    }
}

@Composable
internal fun AjTimeSheet(title: String, hour: Int, minute: Int, onDone: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    AjSheet(onDismiss) { AjTimeContent(title, hour, minute, onDone) }
}

@Composable
internal fun ColumnScope.AjTimeContent(title: String, hour: Int, minute: Int, onDone: (Int, Int) -> Unit) {
    var h by rememberSaveable { mutableIntStateOf(hour) }
    var m by rememberSaveable { mutableIntStateOf(minute) }
    run {
        val close = LocalAjSheetClose.current
        AjSheetTitle(title)
        AjSheetSub("Hora del aviso, cada día.")
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AjDrumColumn("hora", { ajFormatTime(h + it, 0).substringBefore(":") }, { h = (h + 23) % 24 }, { h = (h + 1) % 24 }, "aj-drum-h")
            Text(":", style = AjType.drumSep, modifier = Modifier.padding(bottom = 3.dp))
            AjDrumColumn("minutos", { ajFormatTime(0, m + it * 5).substringAfter(":") }, { m = (m + 55) % 60 }, { m = (m + 5) % 60 }, "aj-drum-m")
        }
        AjButton("Listo", { onDone(h, m); close() }, fill = true, tag = "aj-time-ok")
    }
}

@Composable
private fun AjDrumColumn(label: String, value: (Int) -> String, onLess: () -> Unit, onMore: () -> Unit, tag: String) {
    Column(Modifier.width(96.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(96.dp).height(40.dp).semantics { contentDescription = "Menos $label"; role = Role.Button }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onLess),
            contentAlignment = Alignment.Center,
        ) { Icon(AjIcons.Up, null, tint = AjColors.Faint, modifier = Modifier.size(20.dp)) }
        Box(Modifier.width(96.dp).height(44.dp).graphicsLayer { alpha = 0.55f }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onLess), contentAlignment = Alignment.Center) {
            Text(value(-1), style = AjType.drumFar)
        }
        Box(Modifier.width(96.dp).height(60.dp).background(AjColors.Inset, RoundedCornerShape(18.dp)).semantics { contentDescription = "$label ${value(0)}" }, contentAlignment = Alignment.Center) {
            Text(value(0), style = AjType.drumCur)
        }
        Box(Modifier.width(96.dp).height(44.dp).graphicsLayer { alpha = 0.55f }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onMore), contentAlignment = Alignment.Center) {
            Text(value(1), style = AjType.drumFar)
        }
        Box(
            Modifier.width(96.dp).height(40.dp).semantics { contentDescription = "Más $label"; role = Role.Button }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onMore),
            contentAlignment = Alignment.Center,
        ) { Icon(AjIcons.Down, null, tint = AjColors.Faint, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
internal fun AjSaveSheet(kind: AjBackupKind, busy: Boolean, onKind: (AjBackupKind) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    AjSheet(onDismiss, dismissible = !busy) { AjSaveContent(kind, busy, onKind, onSave) }
}

@Composable
internal fun ColumnScope.AjSaveContent(kind: AjBackupKind, busy: Boolean, onKind: (AjBackupKind) -> Unit, onSave: () -> Unit) {
    run {
        AjSheetTitle("Guardar copia")
        AjSheetSub("Después eliges dónde guardar el archivo.")
        Column {
            AjOption(
                "Copia estándar", kind == AjBackupKind.Json, { onKind(AjBackupKind.Json) }, enabled = !busy, divider = false, tag = "aj-save-json",
                sub = "Rutinas, historial, nutrición, competiciones y metadatos de medios. No incluye el catálogo de alimentos ni WikiLab: se regeneran.",
            )
            AjOption(
                "Copia completa", kind == AjBackupKind.Db, { onKind(AjBackupKind.Db) }, enabled = !busy, tag = "aj-save-db",
                sub = "Toda la base de datos, con el catálogo de alimentos y WikiLab. Recomendada para cambiar de móvil.",
            )
        }
        AjButton(if (busy) "Guardando…" else "Guardar", onSave, fill = true, busy = busy, enabled = !busy, tag = "aj-save-go")
    }
}

@Composable
internal fun AjRestoreSheet(
    confirming: Boolean,
    kind: AjBackupKind,
    fileName: String,
    busy: Boolean,
    onKind: (AjBackupKind) -> Unit,
    onPickFile: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    AjSheet(onDismiss, dismissible = !busy) { AjRestoreContent(confirming, kind, fileName, busy, onKind, onPickFile, onRestore) }
}

@Composable
internal fun ColumnScope.AjRestoreContent(
    confirming: Boolean,
    kind: AjBackupKind,
    fileName: String,
    busy: Boolean,
    onKind: (AjBackupKind) -> Unit,
    onPickFile: () -> Unit,
    onRestore: () -> Unit,
) {
    run {
        val close = LocalAjSheetClose.current
        if (!confirming) {
            AjSheetTitle("Restaurar copia")
            AjSheetSub("¿Qué tipo de archivo vas a restaurar?")
            Column {
                AjOption("Copia estándar", kind == AjBackupKind.Json, { onKind(AjBackupKind.Json) }, sub = "Archivo .json guardado con «Copia estándar».", divider = false, tag = "aj-restore-json")
                AjOption("Copia completa", kind == AjBackupKind.Db, { onKind(AjBackupKind.Db) }, sub = "Archivo .db guardado con «Copia completa».", tag = "aj-restore-db")
            }
            AjButton("Elegir archivo", onPickFile, fill = true, tag = "aj-restore-pick")
        } else {
            val json = kind == AjBackupKind.Json
            AjSheetTitle("¿Restaurar esta copia?")
            Row(
                Modifier.fillMaxWidth().background(AjColors.Inset, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AjIconCircle(if (json) AjIcons.Folder else AjIcons.Db)
                Column {
                    Text(fileName, style = AjType.fileName)
                    Text(if (json) "Copia estándar" else "Copia completa", style = AjType.fileKind)
                }
            }
            AjSheetSub(
                (if (json) "Se restaurarán rutinas, historial, nutrición y observaciones originales según el archivo. Si es antiguo, competiciones y datos de contexto no listados se mantienen."
                else "Se reemplazan todos los datos de este móvil por los del archivo.") + " Antes se guarda una copia de rescate por si algo falla.",
            )
            AjButton(if (busy) "Restaurando…" else "Restaurar", onRestore, fill = true, busy = busy, enabled = !busy, tag = "aj-restore-go")
            AjTextButton("Cancelar", { close() }, enabled = !busy)
        }
    }
}

@Composable
internal fun AjDeleteSheet(deleting: Boolean, onSaveFirst: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AjSheet(onDismiss, dismissible = !deleting) { AjDeleteContent(deleting, onSaveFirst, onConfirm) }
}

@Composable
internal fun ColumnScope.AjDeleteContent(deleting: Boolean, onSaveFirst: () -> Unit, onConfirm: () -> Unit) {
    run {
        val close = LocalAjSheetClose.current
        if (deleting) {
            AjSheetTitle("Borrando…")
            AjSheetSub("No cierres KPKN.")
            Box(Modifier.fillMaxWidth().padding(vertical = 26.dp), contentAlignment = Alignment.Center) { AjSpinner(size = 30.dp) }
        } else {
            AjSheetTitle("¿Borrar todos los datos?")
            AjSheetSub("Es irreversible. KPKN queda como recién instalada.")
            AjLines(
                listOf("Rutinas, programas e historial", "Nutrición y planes", "Progreso, mediciones y foto", "Configuración y recordatorios"),
                AjIcons.XMark, AjColors.Danger,
            )
            AjLines(listOf("No se toca nada de lo que guardaste fuera de KPKN, como tus copias."), AjIcons.Check, AjColors.Done)
            AjButton("Guardar una copia antes", onSaveFirst, tone = AjButtonTone.Ghost, small = true, icon = AjIcons.Download, tag = "aj-delete-save")
            AjHoldButton("Mantén pulsado para borrar", "Sigue pulsando…", onConfirm)
            AjTextButton("Cancelar", { close() })
        }
    }
}

/** `.aj-lines`: lista con filete entre líneas y un icono de color por línea. */
@Composable
private fun AjLines(lines: List<String>, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Column {
        lines.forEachIndexed { i, line ->
            Row(
                Modifier.fillMaxWidth().then(if (i > 0) Modifier.drawBehind { drawLine(Color(0x0FFFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(18.dp))
                Text(line, style = AjType.line)
            }
        }
    }
}

/**
 * `.aj-hold` (aj-T01, aj-T02, aj-X01, aj-X02, aj-J03): botón rojo que se llena de izquierda a derecha en 1,2 s lineal mientras se mantiene
 * pulsado; soltar antes lo vacía en 0,2 s `ease-out` y no hace nada. Con movimiento reducido el relleno es instantáneo pero el tiempo se
 * mantiene. Con TalkBack se activa con doble toque.
 */
@Composable
internal fun AjHoldButton(label: String, holdingLabel: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reduced = wizardReducedMotion()
    var holding by remember { mutableStateOf(false) }
    val confirm by rememberUpdatedState(onConfirm)
    val fs = LocalDensity.current.fontScale
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp * fs)
            .testTag("aj-hold")
            .clip(shape)
            .background(Color(0x14FF9B92), shape)
            .border(1.dp, Color(0x33FF9B92), shape)
            .semantics {
                contentDescription = "Mantén pulsado para borrar todo"
                role = Role.Button
                onClick(label = "Borrar todos los datos") { confirm(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    holding = true
                    val job: Job = scope.launch {
                        if (reduced) { progress.snapTo(1f); delay(AjMotion.HoldMs) }
                        else progress.animateTo(1f, kpknTween<Float>(AjMotion.HoldFill, KpknMotion.Lineal))
                        confirm()
                    }
                    tryAwaitRelease()
                    holding = false
                    if (job.isActive) {
                        job.cancel()
                        scope.launch {
                            if (reduced) progress.snapTo(0f) else progress.animateTo(0f, kpknTween<Float>(AjMotion.HoldRelease, KpknMotion.Salida))
                        }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize().graphicsLayer { scaleX = progress.value; transformOrigin = TransformOrigin(0f, 0.5f) }
                .background(Color(0x40FF9B92)),
        )
        Text(
            if (holding) holdingLabel else label,
            style = AjType.button, color = AjColors.Danger, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
}
