package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import com.example.kpkn.screens.sessioneditor.dayInitial
import com.example.kpkn.screens.sessioneditor.sessionEditorDayLabel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.screens.sessioneditor.components.EdColors
import com.example.kpkn.screens.sessioneditor.components.edDisplay
import com.example.kpkn.screens.sessioneditor.components.edText
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknRing

/** Sin día pendiente de crear: el diálogo de «Crear sesión» está cerrado. */
private const val NO_DAY = -1

/**
 * La hoja «Semana» (ER1a §2.1): el navegador entero del original, en una hoja de vidrio. Se abre desde el texto de la píldora de la fila
 * superior. Lleva todo lo que el original tenía en su navegador: chips de bloque, la semana en programas simples con bucles, los
 * círculos de día (punto, competición y contador), las sesiones de un día con varias y su principal, el roadmap, las variantes (también
 * con pulsación larga en el día actual) y crear sesión en un día vacío.
 *
 * @param nextVariant variante libre a crear (B, C o D), o nulo si ya están todas.
 * @param defaultVariantName nombre que se propone al crear una variante, p. ej. «Pierna A – Rápida».
 * @param onDismiss cierra la hoja.
 */
@Composable
internal fun SessionEditorFrameWeekSheet(
    orderedDays: List<Int>,
    sessions: List<Session>,
    currentSessionId: String,
    currentDayOfWeek: Int?,
    competitionKeyDaysInWeek: Set<Int>,
    roadmapOptions: List<SessionRoadmapOption>,
    currentWeekId: String,
    isSimpleProgram: Boolean,
    hasActiveLoops: Boolean,
    activeVariant: WeekVariant,
    availableVariants: List<WeekVariant>,
    variantName: (WeekVariant) -> String,
    nextVariant: WeekVariant?,
    defaultVariantName: String,
    onSelectSession: (String) -> Unit,
    onSetMainSession: (String) -> Unit,
    onCreateSessionForDay: (Int) -> Unit,
    onSelectRoadmapOption: (SessionRoadmapOption) -> Unit,
    onSelectVariant: (WeekVariant) -> Unit,
    onCreateVariant: (WeekVariant, String) -> Unit,
    onDeleteActiveVariant: () -> Unit,
    onDismiss: () -> Unit,
) {
    // pulido:vidrio ed-S07 .ed-sheet #1B1B1D plano (sombra de hoja y entrada 0,34 s de la hoja común)
    KpknSheet(onDismissRequest = onDismiss, surfaceColor = com.example.kpkn.screens.sessioneditor.components.EdColors.sheet, maxHeightFraction = 0.88f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
        ) {
            SessionEditorFrameWeekContent(
                moduleInset = 0.dp,
                orderedDays = orderedDays,
                sessions = sessions,
                currentSessionId = currentSessionId,
                currentDayOfWeek = currentDayOfWeek,
                competitionKeyDaysInWeek = competitionKeyDaysInWeek,
                roadmapOptions = roadmapOptions,
                currentWeekId = currentWeekId,
                isSimpleProgram = isSimpleProgram,
                hasActiveLoops = hasActiveLoops,
                activeVariant = activeVariant,
                availableVariants = availableVariants,
                variantName = variantName,
                nextVariant = nextVariant,
                defaultVariantName = defaultVariantName,
                onSelectSession = onSelectSession,
                onSetMainSession = onSetMainSession,
                onCreateSessionForDay = onCreateSessionForDay,
                onSelectRoadmapOption = onSelectRoadmapOption,
                onSelectVariant = onSelectVariant,
                onCreateVariant = onCreateVariant,
                onDeleteActiveVariant = onDeleteActiveVariant,
                onDone = onDismiss,
            )
        }
    }
}

/** Contenido de la hoja «Semana», sin la hoja: los módulos y los diálogos de crear. [onDone] cierra la hoja tras una elección. */
@Composable
internal fun SessionEditorFrameWeekContent(
    orderedDays: List<Int>,
    sessions: List<Session>,
    currentSessionId: String,
    currentDayOfWeek: Int?,
    competitionKeyDaysInWeek: Set<Int>,
    roadmapOptions: List<SessionRoadmapOption>,
    currentWeekId: String,
    isSimpleProgram: Boolean,
    hasActiveLoops: Boolean,
    activeVariant: WeekVariant,
    availableVariants: List<WeekVariant>,
    variantName: (WeekVariant) -> String,
    nextVariant: WeekVariant?,
    defaultVariantName: String,
    onSelectSession: (String) -> Unit,
    onSetMainSession: (String) -> Unit,
    onCreateSessionForDay: (Int) -> Unit,
    onSelectRoadmapOption: (SessionRoadmapOption) -> Unit,
    onSelectVariant: (WeekVariant) -> Unit,
    onCreateVariant: (WeekVariant, String) -> Unit,
    onDeleteActiveVariant: () -> Unit,
    moduleInset: Dp = 0.dp,
    onDone: () -> Unit,
) {
    var pendingCreateDay by rememberSaveable { mutableStateOf(NO_DAY) }
    var showVariantDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateVariant by rememberSaveable { mutableStateOf(false) }
    var newVariantName by rememberSaveable { mutableStateOf("") }

    val blocks = remember(roadmapOptions) { roadmapOptions.map { it.blockIndex to it.blockName }.distinctBy { it.first } }
    val blockGrouping = blocks.size > 1
    val currentBlock = roadmapOptions.firstOrNull { it.weekId == currentWeekId }?.blockIndex
    val sessionsByDay = remember(sessions) { sessions.groupBy { it.dayOfWeek ?: 99 } }
    val currentDaySessions = sessions.filter { it.dayOfWeek == currentDayOfWeek }
    // ED-A · A5: el rótulo «Semana 3 · Acumulación» es el de la semana que está abierta (antes salía el de la primera de la lista).
    val weekCaption = roadmapOptions.firstOrNull { it.weekId.isNotBlank() && it.weekId == currentWeekId }
        ?: roadmapOptions.firstOrNull { it.weekId.isNotBlank() }

    // Pulido v1 · EDITOR · A2 (escena `ed-dia`): la hoja como `.ed-sheet` (14 dp entre bloques; título de Syne 20 y línea de 13,5) y cada bloque
    // como `.ed-box` (radio 22, 6 × 14 de aire, ceja de 11, filas de 52 y acciones de 44). Lo que el lienzo no tiene (la letra de la variante, el
    // «+» de «Crear sesión derivada») ya no está.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = moduleInset)) {
            Text(
                text = "Semana",
                style = edDisplay(20f, FontWeight.Bold, EdColors.ink, -0.4f).copy(lineHeight = 23.sp),
                modifier = Modifier.semantics { heading() },
            )
            if (isSimpleProgram && hasActiveLoops && weekCaption != null) {
                Text(
                    text = "Semana ${weekCaption.weekIndex + 1} · ${weekCaption.weekName}",
                    style = edText(13.5f, FontWeight.Normal, EdColors.muted, lineHeight = 19.6f),
                )
            }
        }

        // Días: chips de bloque en programas avanzados y los círculos de la semana.
        EdBox(padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
            if (!isSimpleProgram && blocks.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    blocks.forEach { (blockIndex, blockName) ->
                        KpknChip(
                            text = blockName,
                            selected = currentBlock == blockIndex,
                            onClick = {
                                // Se queda en la hoja: el bloque lleva a su primera semana y las semanas siguen a la vista.
                                roadmapOptions.firstOrNull { it.blockIndex == blockIndex }?.let(onSelectRoadmapOption)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            // A 360 dp, margen específico de 12 dp: los siete objetivos de 48 dp quedan a la vista.
            // Solo anchos menores de 336 dp de contenido necesitan desplazamiento.
            // `.ed-wd` mide 48 dp pero encoge (flex): con 294 dp de caja, los siete días caben a 42 dp cada uno y el círculo (40) no cambia.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                orderedDays.forEach { day ->
                    val daySessions = sessionsByDay[day].orEmpty()
                    val hasSession = daySessions.isNotEmpty()
                    FrameDayCircle(
                        day = day,
                        hasSession = hasSession,
                        isCurrentDay = day == currentDayOfWeek,
                        isCompetition = day in competitionKeyDaysInWeek,
                        sessionCount = daySessions.size,
                        modifier = Modifier.weight(1f),
                        onTap = {
                            val primary = primarySessionOnDay(sessions, day)
                            if (primary != null) {
                                onSelectSession(primary.id)
                                onDone()
                            } else {
                                pendingCreateDay = day
                            }
                        },
                        onLongPress = if (hasSession && day == currentDayOfWeek) {
                            { showVariantDialog = true }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        // Sesiones del día actual, si hay varias: la principal va marcada y las demás se pueden hacer principales.
        if (currentDaySessions.size > 1) {
            EdBox(modifier = Modifier.padding(horizontal = moduleInset)) {
                EdBoxEyebrow("Sesiones de ${sessionEditorDayLabel(currentDayOfWeek)}")
                currentDaySessions.forEach { session ->
                    EdRadioRow(
                        selected = session.id == currentSessionId,
                        title = session.name.ifBlank { "Sesión" },
                        subtitle = if (session.isMainSession) "Principal" else null,
                        onClick = {
                            onSelectSession(session.id)
                            onDone()
                        },
                    )
                    if (!session.isMainSession) {
                        EdBoxAction(
                            text = "Marcar como principal",
                            onClick = {
                                onSetMainSession(session.id)
                                onDone()
                            },
                        )
                    }
                }
            }
        }

        // Variantes de la sesión: también se abren con pulsación larga en el día actual.
        EdBox(modifier = Modifier.padding(horizontal = moduleInset)) {
            EdBoxEyebrow("Variantes de la sesión")
            FrameVariantRows(
                availableVariants = availableVariants,
                activeVariant = activeVariant,
                variantName = variantName,
                canCreate = nextVariant != null,
                onSelect = {
                    onSelectVariant(it)
                    onDone()
                },
                onCreate = {
                    newVariantName = defaultVariantName
                    showCreateVariant = true
                },
                onDelete = {
                    onDeleteActiveVariant()
                    onDone()
                },
            )
        }

        // Semanas del programa (el roadmap del original), agrupadas por bloque.
        if (roadmapOptions.size > 1) {
            EdBox(modifier = Modifier.padding(horizontal = moduleInset)) {
                EdBoxEyebrow("Semanas del programa")
                var lastBlock = -1
                roadmapOptions.forEach { option ->
                    if (blockGrouping && option.blockIndex != lastBlock) {
                        lastBlock = option.blockIndex
                        Text(
                            text = "Bloque ${option.blockIndex + 1} · ${option.blockName}",
                            style = edText(12f, FontWeight.Medium, EdColors.faint, lineHeight = 15.6f),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    EdRadioRow(
                        selected = option.weekId == currentWeekId,
                        title = "B${option.blockIndex + 1} · S${option.weekIndex + 1}",
                        subtitle = option.weekName,
                        onClick = {
                            onSelectRoadmapOption(option)
                            onDone()
                        },
                    )
                }
            }
        }
    }

    if (pendingCreateDay != NO_DAY) {
        val day = pendingCreateDay
        KpknAlertDialog(
            onDismissRequest = { pendingCreateDay = NO_DAY },
            title = {
                Text(
                    text = "¿Crear sesión para ${sessionEditorDayLabel(day)}?",
                    style = KpknType.dialogTitle,
                    color = WizardColors.text,
                )
            },
            text = {
                Text(
                    text = "Este día no tiene una sesión asignada. ¿Quieres crear una nueva sesión aquí?",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                KpknPrimaryPill(
                    text = "Crear sesión",
                    onClick = {
                        pendingCreateDay = NO_DAY
                        onCreateSessionForDay(day)
                        onDone()
                    },
                )
            },
            dismissButton = {
                KpknTextAction(text = "Cancelar", onClick = { pendingCreateDay = NO_DAY })
            },
        )
    }

    if (showVariantDialog) {
        KpknAlertDialog(
            onDismissRequest = { showVariantDialog = false },
            title = {
                Text(
                    text = "Variantes de la sesión",
                    style = KpknType.dialogTitle,
                    color = WizardColors.text,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FrameVariantRows(
                        availableVariants = availableVariants,
                        activeVariant = activeVariant,
                        variantName = variantName,
                        canCreate = nextVariant != null,
                        onSelect = {
                            showVariantDialog = false
                            onSelectVariant(it)
                            onDone()
                        },
                        onCreate = {
                            showVariantDialog = false
                            newVariantName = defaultVariantName
                            showCreateVariant = true
                        },
                        onDelete = {
                            showVariantDialog = false
                            onDeleteActiveVariant()
                            onDone()
                        },
                    )
                }
            },
            confirmButton = {
                KpknTextAction(text = "Cerrar", onClick = { showVariantDialog = false })
            },
        )
    }

    val variantTarget = nextVariant
    if (showCreateVariant && variantTarget != null) {
        KpknAlertDialog(
            onDismissRequest = { showCreateVariant = false },
            title = {
                Text(
                    text = "Nueva variante",
                    style = KpknType.dialogTitle,
                    color = WizardColors.text,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Crea una variante derivada de la sesión original. Tendrá sus propios ejercicios, series y descansos independientes.",
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                    )
                    KpknTextField(
                        value = newVariantName,
                        onValueChange = { newVariantName = it },
                        label = "Nombre de la variante",
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                KpknPrimaryPill(
                    text = "Crear variante",
                    enabled = newVariantName.isNotBlank(),
                    onClick = {
                        onCreateVariant(variantTarget, newVariantName.trim())
                        showCreateVariant = false
                        onDone()
                    },
                )
            },
            dismissButton = {
                KpknTextAction(text = "Cancelar", onClick = { showCreateVariant = false })
            },
        )
    }
}

/** Filas de las variantes: una por cada una disponible, «Crear sesión derivada» si queda una libre y «Eliminar variante activa» fuera de A. */
@Composable
private fun FrameVariantRows(
    availableVariants: List<WeekVariant>,
    activeVariant: WeekVariant,
    variantName: (WeekVariant) -> String,
    canCreate: Boolean,
    onSelect: (WeekVariant) -> Unit,
    onCreate: () -> Unit,
    onDelete: () -> Unit,
) {
    availableVariants.forEach { variant ->
        EdRadioRow(
            selected = variant == activeVariant,
            title = variantName(variant),
            modifier = Modifier.testTag("session_variant_${variant.name}"),
            onClick = { if (variant != activeVariant) onSelect(variant) },
        )
    }
    if (canCreate) {
        EdBoxAction(text = "Crear sesión derivada", onClick = onCreate)
    }
    if (activeVariant != WeekVariant.A) {
        EdBoxAction(text = "Eliminar variante activa", onClick = onDelete, color = EdColors.danger)
    }
}

/**
 * Círculo de un día con objetivo de 48 dp: elegido en tinta; con sesión, un punto en `textMuted`; competición con
 * borde en Energía y una «C»; con varias sesiones, un contador. La pulsación larga, solo en el día actual con sesión, abre las variantes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrameDayCircle(
    day: Int,
    hasSession: Boolean,
    isCurrentDay: Boolean,
    isCompetition: Boolean,
    sessionCount: Int,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val fill = if (isCurrentDay) WizardColors.text else KpknSurface.inset
    val ink = when {
        isCurrentDay -> WizardColors.ctaContent
        hasSession -> WizardColors.text
        else -> WizardColors.textFaint
    }
    // El círculo es una caja recortada; las marcas («C» y contador) van en la caja de fuera, que no recorta.
    Box(modifier = modifier.height(48.dp).testTag("session_week_day_$day").combinedClickable(
        role = Role.Button,
        onClick = onTap,
        onLongClick = onLongPress,
    )) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.Center)
                // pulido:sombra ed-S08 .ed-wd.comp i 0 0 0 1.5px --ene (preset anillo-energia-1p5): la «C» de competición rodea el día, no lo recorta
                .then(if (isCompetition) Modifier.kpknRing(KpknShadow.AnilloEnergia1p5, CircleShape) else Modifier)
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dayInitial(day),
                style = KpknType.bodyStrong,
                color = ink,
            )
            if (hasSession && !isCurrentDay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(WizardColors.textMuted),
                )
            }
        }
        if (isCompetition) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-1).dp, y = (-1).dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette.energia),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "C",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                )
            }
        }
        if (sessionCount > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(WizardColors.textMuted),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$sessionCount",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WizardColors.ctaContent,
                )
            }
        }
    }
}
