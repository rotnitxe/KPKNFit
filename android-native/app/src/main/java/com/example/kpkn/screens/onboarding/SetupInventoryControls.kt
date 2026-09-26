package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.catalogConfigurationDisplayName
import com.example.kpkn.data.models.DumbbellPairStock
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.KettlebellStock
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.onboarding.SetupInventoryGroup
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * Inventario principal del bloque Entreno + controles compartidos del bloque.
 *
 * Cada paso INVENTORY_* edita su grupo dentro de `draft.trainingOptions.inventory`
 * mediante `SetupWizardViewModel.updateStep`: el inventario nace **nullable**
 * (desconocido) y solo toma forma cuando el usuario declara material real. Aquí
 * jamás se inventa una barra de 20 kg, un disco ni un incremento: los campos
 * nuevos empiezan vacíos y una fila solo se guarda con cantidades explícitas y
 * finitas (`countPerSide` nunca queda null en una declaración nueva).
 *
 * La edición del subeditor **vive en el borrador** (`stepEditors[step]`, DTO
 * genérico de M1: fila, fase, crudos), nunca en `remember` local: salir con
 * Atrás o «Guardar y salir» y reanudar restaura la edición exacta, y el paso
 * queda `editing` mientras haya cambios sin guardar (M1 bloquea el CTA con ese
 * estado). Guardar escribe el tipado y cierra el editor en un `updateStep`
 * atómico; Cancelar solo cierra el editor sin tocar el inventario.
 *
 * Cada grupo ofrece además «No tengo este material»: token explícito `none`
 * en `stepSelections[step]` que crea el inventario vacío si hace falta y
 * retira **solo** ese grupo. Ausencia declarada así nunca se interpreta como
 * material ilimitado, y agregar una fila retira el token.
 *
 * Un solo inventario (sin selector de gimnasios) y filas con add/edit/remove.
 * Cada subeditor muestra como máximo dos campos relacionados a la vez (las
 * máquinas reparten sus cinco datos en tres subfases: nombre, mínimo/máximo,
 * incremento/base).
 *
 * Los controles compartidos (`TrainingNotice`, `TrainingNumberField`,
 * `InventoryRowCard`, …) viven aquí para que `SetupTrainingSteps.kt` los reuse
 * sin duplicar estilos.
 */

// ─── Controles compartidos del bloque Entreno ────────────────────────────────

/** Tono de los avisos en línea del contenido (informativo o bloqueante). */
internal enum class TrainingNoticeTone { INFO, ERROR }

/** Formatea una carga real sin artefactos: 40.0 → "40", 12.5 → "12.5". */
internal fun formatTrainingNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/** Campo numérico/texto de un solo línea con teclado localizado. */
@Composable
internal fun TrainingNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = setupBodyFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Campo del subeditor con buffer inmediato.
 *
 * Bug real que evita: un valor controlado que sólo se compone tras el eco de
 * Room deja al IME componiendo sobre texto viejo y pierde teclas (`adb shell
 * input text20.` en la barra llegó a dejar `0.`). Aquí la cadena de cada
 * `onValueChange` actualiza primero el buffer local de forma síncrona
 * (`rememberSaveable`, identidad `draftId|paso|fila|fase|clave`, que sólo se
 * siembra al abrir/cambiar de alcance, nunca con el eco persistido) y en el
 * mismo evento escribe el crudo en el borrador vía `updateStep` — jamás un
 * `remember` sin persistencia. La edición durable vive en `stepEditors`; si
 * el proceso muere, `rememberSaveable` restaura el mismo texto y al reabrir
 * tras cancelar/guardar el cambio de alcance siembra desde el borrador.
 */
@Composable
internal fun InventoryEditField(
    label: String,
    draftId: String,
    step: SetupStepId,
    itemIndex: Int?,
    phase: Int,
    fieldKey: String,
    initialValue: String,
    onPersist: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    isError: Boolean = false,
) {
    val identity = "$draftId|${step.name}|${itemIndex ?: "slot"}|$phase|$fieldKey"
    var buffer by rememberSaveable(identity) { mutableStateOf(initialValue) }
    TrainingNumberField(
        label = label,
        value = buffer,
        onValueChange = { text ->
            buffer = text
            onPersist(text)
        },
        modifier = Modifier.testTag("inventory-field-$fieldKey"),
        keyboardType = keyboardType,
        isError = isError,
    )
}

/** Aviso en línea del contenido: estado de carga, error con reintento o restricción. */
@Composable
internal fun TrainingNotice(
    text: String,
    tone: TrainingNoticeTone = TrainingNoticeTone.INFO,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardColors.cardFill, WizardShapes.card)
            .border(
                width = WizardColors.unselectedBorderWidth,
                color = if (tone == TrainingNoticeTone.ERROR) WizardColors.danger else WizardColors.cardBorder,
                shape = WizardShapes.card,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = WizardTypography.bodySmall,
            color = if (tone == TrainingNoticeTone.ERROR) WizardColors.danger else WizardColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = WizardColors.text) }
        }
    }
}

/** Carga en curso del contenido: solo visible mientras la bandera real está activa. */
@Composable
internal fun TrainingLoading(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardColors.cardFill, WizardShapes.card)
            .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = WizardColors.text,
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = WizardTypography.bodySmall, color = WizardColors.textMuted)
    }
}

/** Fila de resumen no interactiva (revisión e hito). */
@Composable
internal fun TrainingSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardColors.cardFill, WizardShapes.card)
            .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = WizardTypography.cardTitle, color = WizardColors.text)
    }
}

/**
 * Fila de inventario con editar y quitar. El toque en la fila abre el
 * subeditor; nunca avanza el paso (solo el CTA del scaffold confirma).
 */
@Composable
internal fun InventoryRowCard(
    title: String,
    subtitle: String?,
    editLabel: String = "Editar",
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WizardSpacing.touchTarget)
            .background(WizardColors.cardFill, WizardShapes.card)
            .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = WizardTypography.cardTitle, color = WizardColors.text)
            if (subtitle != null) {
                Text(subtitle, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
            }
        }
        if (onRemove != null) {
            TextButton(onClick = onRemove) { Text("Quitar", color = WizardColors.danger) }
        }
        TextButton(onClick = onClick) { Text(editLabel, color = WizardColors.text) }
    }
}

// ─── Editor de filas persistente (stepEditors) ──────────────────────────────

/** Token explícito «No tengo este material» dentro de `stepSelections[step]`. */
internal const val INVENTORY_NONE = "none"

// Claves de los campos crudos dentro de `SetupStepEditorState.values`.
private const val KEY_WEIGHT = "weight"
private const val KEY_COUNT = "count"
private const val KEY_PAIR = "pair"
private const val KEY_NAME = "name"
private const val KEY_MIN = "min"
private const val KEY_MAX = "max"
private const val KEY_INC = "inc"
private const val KEY_BASE = "base"
private const val KEY_SUPPORTS = "supports"
private const val KEY_KIND = "kind"
private const val KEY_CONFIG = "config"

/** Número de subfases del editor de máquinas (nombre+tipo · mín/máx · inc/base). */
internal const val MACHINE_EDITOR_PHASES = 3

/** Subfases del editor de la barra: peso de la barra · ¿Qué soportes tienes? */
internal const val BARBELL_EDITOR_PHASES = 2

/**
 * Soportes con ids reales del vocabulario del catálogo. Rack y banco no tienen
 * kind propio: se acreditan como `support`, que es lo que valida
 * `PersonalizedPlanCatalog.requiredEquipment` (nunca «ítem no soportado»).
 */
internal val SUPPORT_EQUIPMENT_CHOICES: List<Pair<String, String>> = listOf(
    "support" to "Soportes, rack o banco",
    "pull_up_bar" to "Barra de dominadas",
    "band" to "Bandas",
    "ball" to "Balón",
    "cardio" to "Cardio (cinta o bici)",
)

/** Tipos declarables de una máquina; el nombre de la fila nunca los deduce. */
internal val MACHINE_KIND_CHOICES: List<Pair<String, String>> = listOf(
    "machine" to "Máquina con peso guiado",
    "cable" to "Polea",
    "smith_machine" to "Máquina Smith",
)

/** Máquina genérica: exige configuración real del catálogo (no basta el nombre). */
internal const val MACHINE_KIND_GENERIC = "machine"

/** Alias granulares → id canónico: rack y banco se acreditan como `support`. */
private val SUPPORT_EQUIPMENT_ALIASES = mapOf("rack" to "support", "bench" to "support")

/** Soportes crudos (coma) → ids canónicos; lo desconocido se descarta. */
internal fun supportEquipmentFromRaw(raw: String): Set<String> {
    val known = SUPPORT_EQUIPMENT_CHOICES.map { (id, _) -> id }.toSet()
    return raw.split(',')
        .map(String::trim)
        .filter { it.isNotBlank() }
        .map { SUPPORT_EQUIPMENT_ALIASES[it] ?: it }
        .filter { it in known }
        .toSet()
}

/** Ids canónicos → crudo estable para `SetupStepEditorState.values`. */
internal fun rawFromSupportEquipment(supports: Set<String>): String =
    SUPPORT_EQUIPMENT_CHOICES.map { (id, _) -> id }.filter { it in supports }.joinToString(",")

/** Tipo explícito de máquina; null = sin declarar (nunca inferido del nombre). */
internal fun machineKindOrNull(raw: String): String? =
    MACHINE_KIND_CHOICES.firstOrNull { (id, _) -> id == raw }?.first

/** Editor abierto en la fila [itemIndex] (null = ranura única o fila nueva). */
internal fun openStepEditor(
    itemIndex: Int?,
    values: Map<String, String> = emptyMap(),
    phase: Int = 0,
): SetupStepEditorState =
    SetupStepEditorState(editing = true, itemIndex = itemIndex, phase = phase.coerceAtLeast(0), values = values)

/** Escribe un campo crudo conservando el resto de la edición. */
internal fun withEditorValue(editor: SetupStepEditorState, key: String, raw: String): SetupStepEditorState =
    editor.copy(values = editor.values + (key to raw))

/** Cambia de subfase sin tocar los crudos. */
internal fun withEditorPhase(editor: SetupStepEditorState, phase: Int): SetupStepEditorState =
    editor.copy(phase = phase.coerceAtLeast(0))

/**
 * Claves visibles del editor de máquinas por subfase: nombre (con el tipo de
 * máquina declarado al lado, elección explícita) → mínimo/máximo →
 * incremento/base. Nunca más de dos campos relacionados en pantalla.
 */
internal fun machineEditorKeys(phase: Int): List<String> = when (phase) {
    0 -> listOf(KEY_NAME)
    1 -> listOf(KEY_MIN, KEY_MAX)
    else -> listOf(KEY_INC, KEY_BASE)
}

/**
 * Retira SOLO el grupo indicado del inventario, creándolo vacío si todavía no
 * existe; los demás grupos quedan intactos. El grupo queda vacío, nunca
 * «ilimitado»: ausencia declarada ≠ material sin declarar.
 *
 * En la barra, el token `none` retira únicamente el peso de la barra:
 * [EquipmentInventory.supportEquipment] declarado a mano se conserva siempre.
 */
internal fun clearInventoryGroup(inventory: EquipmentInventory?, group: SetupInventoryGroup): EquipmentInventory {
    val base = inventory ?: EquipmentInventory()
    return when (group) {
        SetupInventoryGroup.BARBELL -> base.copy(barbellWeightKg = null)
        SetupInventoryGroup.PLATES -> base.copy(plates = emptyList())
        SetupInventoryGroup.DUMBBELLS -> base.copy(dumbbells = emptyList())
        SetupInventoryGroup.KETTLEBELLS -> base.copy(kettlebells = emptyList())
        SetupInventoryGroup.MACHINES -> base.copy(machines = emptyList())
    }
}

/** Retira el token `none` de la selección del paso; si queda vacía, la clave sale. */
internal fun withoutNoneToken(
    selections: Map<SetupStepId, List<String>>,
    step: SetupStepId,
): Map<SetupStepId, List<String>> {
    val current = selections[step] ?: return selections
    val kept = current.filterNot { it == INVENTORY_NONE }
    return when {
        kept.size == current.size -> selections
        kept.isEmpty() -> selections - step
        else -> selections + (step to kept)
    }
}

/** Peso positivo y finito (barra, disco, mancuerna, kettlebell). */
internal fun positiveWeightValid(raw: String): Boolean {
    val parsed = parseLocalizedNumber(raw) ?: return false
    return parsed.isFinite() && parsed > 0.0
}

/** Discos por lado: entero ≥ 0 explícito (nunca null «ilimitado»). */
internal fun plateCountValid(raw: String): Boolean = raw.trim().toIntOrNull()?.let { it >= 0 } == true

/**
 * Validez del editor de máquinas sobre TODOS sus campos crudos:
 * - el tope máximo es obligatorio: una máquina nueva nunca se declara sin
 *   límite superior (el null del modelo sólo sobrevive a lecturas legadas);
 * - el tipo es obligatorio en toda declaración nueva (nunca se deduce del
 *   nombre);
 * - la máquina genérica exige una configuración real del catálogo; las
 *   estaciones multi (cable/Smith) sólo exigen tipo + rango válido.
 */
internal fun machineValuesValid(values: Map<String, String>): Boolean {
    val name = values[KEY_NAME]?.trim().orEmpty()
    val min = parseLocalizedNumber(values[KEY_MIN].orEmpty())
    val maxRaw = values[KEY_MAX]?.trim().orEmpty()
    val max = maxRaw.takeIf { it.isNotBlank() }?.let(::parseLocalizedNumber)
    val increment = parseLocalizedNumber(values[KEY_INC].orEmpty())
    val base = parseLocalizedNumber(values[KEY_BASE].orEmpty())
    val kind = machineKindOrNull(values[KEY_KIND].orEmpty())
    val configurationOk = kind != MACHINE_KIND_GENERIC || values[KEY_CONFIG].orEmpty().trim().isNotBlank()
    return name.isNotEmpty() &&
        min != null && min.isFinite() && min >= 0.0 &&
        maxRaw.isNotBlank() && max != null && max.isFinite() && max >= min &&
        increment != null && increment.isFinite() && increment > 0.0 &&
        base != null && base.isFinite() && base >= 0.0 &&
        kind != null && configurationOk
}

/** Guarda un cambio puntual sobre un grupo, sin tocar editor ni selección. */
private fun updateInventory(
    vm: SetupWizardViewModel,
    step: SetupStepId,
    transform: (EquipmentInventory) -> EquipmentInventory,
) {
    vm.updateStep(step) { draft ->
        val current = draft.trainingOptions.inventory ?: EquipmentInventory()
        draft.copy(trainingOptions = draft.trainingOptions.copy(inventory = transform(current)))
    }
}

/** Abre el subeditor: fila, fase y crudos quedan en el borrador (reanudable). */
private fun openInventoryEditor(
    vm: SetupWizardViewModel,
    step: SetupStepId,
    itemIndex: Int?,
    values: Map<String, String>,
    phase: Int = 0,
) {
    vm.updateStep(step) { draft ->
        draft.copy(stepEditors = draft.stepEditors + (step to openStepEditor(itemIndex, values, phase)))
    }
}

/** Escribe crudos o fase en el editor persistido del paso. */
private fun updateInventoryEditor(
    vm: SetupWizardViewModel,
    step: SetupStepId,
    change: (SetupStepEditorState) -> SetupStepEditorState,
) {
    vm.updateStep(step) { draft ->
        val current = draft.stepEditors[step] ?: SetupStepEditorState()
        draft.copy(stepEditors = draft.stepEditors + (step to change(current)))
    }
}

/**
 * Guardar: inventario tipado + retira el token `none` del grupo + cierra el
 * editor, todo en un único `updateStep` atómico.
 *
 * El transform recibe el editor ACTUAL de `draft.stepEditors[step]`, leído
 * dentro de la cola bajo el mutex —nunca el capturado en pantalla—: si la
 * última tecla y «Guardar» se apilan antes del eco de Room, se guarda el
 * valor más reciente. Si ese crudo es inválido en ese instante, el transform
 * devuelve null: no se escribe nada y el editor queda abierto con la fila
 * actual (`itemIndex` también se lee del draft).
 */
private fun saveInventoryEditor(
    vm: SetupWizardViewModel,
    step: SetupStepId,
    transform: (editor: SetupStepEditorState, inventory: EquipmentInventory) -> EquipmentInventory?,
) {
    vm.updateStep(step) { draft ->
        val editor = draft.stepEditors[step] ?: SetupStepEditorState()
        val updated = transform(editor, draft.trainingOptions.inventory ?: EquipmentInventory())
            ?: return@updateStep draft
        draft.copy(
            trainingOptions = draft.trainingOptions.copy(inventory = updated),
            stepSelections = withoutNoneToken(draft.stepSelections, step),
            stepEditors = draft.stepEditors - step,
        )
    }
}

/** Cancelar: cierra SOLO el editor; el inventario declarado no cambia. */
private fun cancelInventoryEditor(vm: SetupWizardViewModel, step: SetupStepId) {
    vm.updateStep(step) { draft -> draft.copy(stepEditors = draft.stepEditors - step) }
}

/**
 * «No tengo este material»: token `none` explícito en la selección del grupo,
 * inventario vacío si no existía y retiro de SOLO ese grupo, atómico.
 */
private fun declareNoInventory(
    vm: SetupWizardViewModel,
    step: SetupStepId,
    group: SetupInventoryGroup,
) {
    vm.updateStep(step) { draft ->
        draft.copy(
            stepSelections = draft.stepSelections + (step to listOf(INVENTORY_NONE)),
            trainingOptions = draft.trainingOptions.copy(
                inventory = clearInventoryGroup(draft.trainingOptions.inventory, group),
            ),
            stepEditors = draft.stepEditors - step,
        )
    }
}

/** Tarjeta «No tengo este material» del grupo, presente en la lista de filas. */
@Composable
private fun InventoryNoneCard(
    step: SetupStepId,
    group: SetupInventoryGroup,
    state: SetupWizardState,
    vm: SetupWizardViewModel,
) {
    val selected = state.draft.selectedValues(step)
    WizardChoiceCard(
        title = "No tengo este material",
        subtitle = "Declaración explícita del grupo: queda vacío, nunca como material ilimitado.",
        selected = INVENTORY_NONE in selected,
        onClick = {
            if (INVENTORY_NONE !in selected) declareNoInventory(vm, step, group)
        },
    )
}

// ─── INVENTORY_BARBELL ──────────────────────────────────────────────────────

/**
 * Peso real de la barra más la subfase «¿Qué soportes tienes?» (elección
 * explícita con ids canónicos del catálogo). Sin declaración el peso sigue
 * `null` (desconocido): no se rellena 20 kg por defecto ni se asume material;
 * el token `none` retira sólo el peso y conserva los soportes declarados.
 */
@Composable
internal fun InventoryBarbellStep(step: SetupStepId, state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val current = draft.trainingOptions.inventory?.barbellWeightKg
    val supports = draft.trainingOptions.inventory?.supportEquipment.orEmpty()
    val editor = draft.stepEditors[step] ?: SetupStepEditorState()
    val prefill = mapOf(
        KEY_WEIGHT to (current?.let(::formatTrainingNumber) ?: ""),
        KEY_SUPPORTS to rawFromSupportEquipment(supports),
    )

    if (!editor.editing) {
        Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
            InventoryRowCard(
                title = "Barra",
                subtitle = if (current != null) {
                    "${formatTrainingNumber(current)} kg"
                } else {
                    "Sin declarar: el peso de la barra sigue desconocido."
                },
                onClick = {
                    openInventoryEditor(vm = vm, step = step, itemIndex = null, values = prefill, phase = 0)
                },
                onRemove = if (current == null) null else {
                    { updateInventory(vm, step) { it.copy(barbellWeightKg = null) } }
                },
            )
            InventoryRowCard(
                title = "Soportes",
                subtitle = if (supports.isEmpty()) {
                    "Sin soportes declarados."
                } else {
                    SUPPORT_EQUIPMENT_CHOICES.filter { (id, _) -> id in supports }
                        .joinToString(", ") { (_, label) -> label }
                },
                onClick = {
                    openInventoryEditor(vm = vm, step = step, itemIndex = null, values = prefill, phase = 1)
                },
            )
            InventoryNoneCard(step = step, group = SetupInventoryGroup.BARBELL, state = state, vm = vm)
        }
        return
    }

    val phase = editor.phase.coerceIn(0, BARBELL_EDITOR_PHASES - 1)
    val raw = editor.values[KEY_WEIGHT].orEmpty()
    val weightOk = raw.isBlank() || positiveWeightValid(raw)
    val selectedSupports = supportEquipmentFromRaw(editor.values[KEY_SUPPORTS].orEmpty())

    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        Text(
            text = "Datos ${phase + 1} de $BARBELL_EDITOR_PHASES",
            style = WizardTypography.caption,
            color = WizardColors.textFaint,
        )
        if (phase == 0) {
            InventoryEditField(
                label = "Peso de la barra (kg)",
                draftId = draft.draftId,
                step = step,
                itemIndex = editor.itemIndex,
                phase = phase,
                fieldKey = KEY_WEIGHT,
                initialValue = raw,
                onPersist = { text ->
                    updateInventoryEditor(vm, step) { withEditorValue(it, KEY_WEIGHT, text) }
                },
            )
            if (raw.isNotBlank() && !positiveWeightValid(raw)) {
                TrainingNotice(
                    text = "El peso de la barra debe ser un número positivo.",
                    tone = TrainingNoticeTone.ERROR,
                )
            }
        } else {
            Text(
                text = "¿Qué soportes tienes?",
                style = WizardTypography.cardTitle,
                color = WizardColors.text,
            )
            SUPPORT_EQUIPMENT_CHOICES.forEach { (id, label) ->
                WizardChoiceCard(
                    title = label,
                    subtitle = if (id == "support") {
                        "Rack y banco se acreditan como apoyo estable («support»), el id que valida el catálogo."
                    } else {
                        null
                    },
                    selected = id in selectedSupports,
                    onClick = {
                        updateInventoryEditor(vm, step) { current ->
                            // Alterno contra el editor ACTUAL de la cola, no
                            // contra el capturado en pantalla.
                            val selected = supportEquipmentFromRaw(
                                current.values[KEY_SUPPORTS].orEmpty(),
                            )
                            val next = if (id in selected) selected - id else selected + id
                            withEditorValue(current, KEY_SUPPORTS, rawFromSupportEquipment(next))
                        }
                    },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { cancelInventoryEditor(vm, step) }) {
                Text("Cancelar", color = WizardColors.textMuted)
            }
            TextButton(
                enabled = phase > 0,
                onClick = { updateInventoryEditor(vm, step) { withEditorPhase(it, phase - 1) } },
            ) { Text("Atrás", color = WizardColors.text) }
            if (phase < BARBELL_EDITOR_PHASES - 1) {
                TextButton(onClick = { updateInventoryEditor(vm, step) { withEditorPhase(it, phase + 1) } }) {
                    Text("Siguiente", color = WizardColors.text)
                }
            } else {
                TextButton(
                    enabled = weightOk,
                    onClick = {
                        saveInventoryEditor(vm, step) { editor, inventory ->
                            val weightRaw = editor.values[KEY_WEIGHT].orEmpty()
                            if (weightRaw.isNotBlank() && !positiveWeightValid(weightRaw)) {
                                null
                            } else {
                                inventory.copy(
                                    barbellWeightKg = parseLocalizedNumber(weightRaw)
                                        ?.takeIf { it.isFinite() && it > 0.0 },
                                    supportEquipment = supportEquipmentFromRaw(
                                        editor.values[KEY_SUPPORTS].orEmpty(),
                                    ),
                                )
                            }
                        }
                    },
                ) { Text("Guardar", color = WizardColors.text) }
            }
            if (phase == 0 && current != null) {
                TextButton(onClick = {
                    saveInventoryEditor(vm, step) { _, inventory ->
                        inventory.copy(barbellWeightKg = null)
                    }
                }) { Text("Quitar", color = WizardColors.danger) }
            }
        }
    }
}

// ─── INVENTORY_PLATES ───────────────────────────────────────────────────────

/**
 * Discos: peso + cantidad por lado. Una declaración nueva exige `countPerSide`
 * explícito (nunca null, que en el modelo legacy significaba «ilimitado»).
 */
@Composable
internal fun InventoryPlatesStep(step: SetupStepId, state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val plates = draft.trainingOptions.inventory?.plates.orEmpty()
    val editor = draft.stepEditors[step] ?: SetupStepEditorState()

    if (!editor.editing) {
        Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
            plates.forEachIndexed { index, stock ->
                InventoryRowCard(
                    title = "${formatTrainingNumber(stock.weightKg)} kg",
                    subtitle = stock.countPerSide?.let { "$it por lado" } ?: "Cantidad por lado sin declarar",
                    onClick = {
                        openInventoryEditor(
                            vm = vm,
                            step = step,
                            itemIndex = index,
                            values = mapOf(
                                KEY_WEIGHT to formatTrainingNumber(stock.weightKg),
                                KEY_COUNT to (stock.countPerSide?.toString() ?: ""),
                            ),
                        )
                    },
                    onRemove = {
                        updateInventory(vm, step) { inventory ->
                            inventory.copy(plates = inventory.plates.filterIndexed { i, _ -> i != index })
                        }
                    },
                )
            }
            InventoryRowCard(
                title = "Añadir disco",
                subtitle = "Peso del disco y cuántos tienes por lado (simétrico).",
                editLabel = "Añadir",
                onClick = { openInventoryEditor(vm, step, itemIndex = plates.size, values = emptyMap()) },
            )
            InventoryNoneCard(step = step, group = SetupInventoryGroup.PLATES, state = state, vm = vm)
        }
        return
    }

    val weightRaw = editor.values[KEY_WEIGHT].orEmpty()
    val countRaw = editor.values[KEY_COUNT].orEmpty()
    val weightOk = positiveWeightValid(weightRaw)
    val countOk = plateCountValid(countRaw)

    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        InventoryEditField(
            label = "Peso del disco (kg)",
            draftId = draft.draftId,
            step = step,
            itemIndex = editor.itemIndex,
            phase = editor.phase,
            fieldKey = KEY_WEIGHT,
            initialValue = weightRaw,
            onPersist = { text ->
                updateInventoryEditor(vm, step) { withEditorValue(it, KEY_WEIGHT, text) }
            },
            isError = weightRaw.isNotBlank() && !weightOk,
        )
        InventoryEditField(
            label = "Discos por lado",
            draftId = draft.draftId,
            step = step,
            itemIndex = editor.itemIndex,
            phase = editor.phase,
            fieldKey = KEY_COUNT,
            initialValue = countRaw,
            onPersist = { text ->
                updateInventoryEditor(vm, step) { withEditorValue(it, KEY_COUNT, text) }
            },
            keyboardType = KeyboardType.Number,
            isError = countRaw.isNotBlank() && !countOk,
        )
        if (weightRaw.isNotBlank() && !weightOk) {
            TrainingNotice("Un disco necesita un peso positivo y finito.", TrainingNoticeTone.ERROR)
        }
        if (countRaw.isNotBlank() && !countOk) {
            TrainingNotice("Declara cuántos discos tienes por lado (0 o más).", TrainingNoticeTone.ERROR)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = weightOk && countOk,
                onClick = {
                    saveInventoryEditor(vm, step) { editor, inventory ->
                        val weight = parseLocalizedNumber(editor.values[KEY_WEIGHT].orEmpty())
                        val count = editor.values[KEY_COUNT].orEmpty().trim().toIntOrNull()
                        if (weight == null || !weight.isFinite() || weight <= 0.0 || count == null || count < 0) {
                            null
                        } else {
                            val stock = PlateStock(weightKg = weight, countPerSide = count)
                            val rows = inventory.plates.toMutableList()
                            val index = editor.itemIndex
                            if (index != null && index in rows.indices) rows[index] = stock else rows.add(stock)
                            inventory.copy(plates = rows)
                        }
                    }
                },
            ) { Text("Guardar", color = WizardColors.text) }
            TextButton(onClick = { cancelInventoryEditor(vm, step) }) {
                Text("Cancelar", color = WizardColors.textMuted)
            }
        }
    }
}

// ─── INVENTORY_DUMBBELLS ────────────────────────────────────────────────────

/** Mancuernas: peso por unidad + si existe la pareja completa. */
@Composable
internal fun InventoryDumbbellsStep(step: SetupStepId, state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val dumbbells = draft.trainingOptions.inventory?.dumbbells.orEmpty()
    val editor = draft.stepEditors[step] ?: SetupStepEditorState()

    if (!editor.editing) {
        Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
            dumbbells.forEachIndexed { index, stock ->
                InventoryRowCard(
                    title = "${formatTrainingNumber(stock.weightPerUnitKg)} kg por unidad",
                    subtitle = if (stock.pairAvailable) "Pareja completa" else "Sin pareja (solo una unidad)",
                    onClick = {
                        openInventoryEditor(
                            vm = vm,
                            step = step,
                            itemIndex = index,
                            values = mapOf(
                                KEY_WEIGHT to formatTrainingNumber(stock.weightPerUnitKg),
                                KEY_PAIR to stock.pairAvailable.toString(),
                            ),
                        )
                    },
                    onRemove = {
                        updateInventory(vm, step) { inventory ->
                            inventory.copy(dumbbells = inventory.dumbbells.filterIndexed { i, _ -> i != index })
                        }
                    },
                )
            }
            InventoryRowCard(
                title = "Añadir mancuerna",
                subtitle = "Peso de cada unidad; las fijas suelen venir por parejas.",
                editLabel = "Añadir",
                onClick = { openInventoryEditor(vm, step, itemIndex = dumbbells.size, values = emptyMap()) },
            )
            InventoryNoneCard(step = step, group = SetupInventoryGroup.DUMBBELLS, state = state, vm = vm)
        }
        return
    }

    val weightRaw = editor.values[KEY_WEIGHT].orEmpty()
    // Sin valor persistido rige el default del modelo (pareja completa).
    val pairAvailable = editor.values[KEY_PAIR]?.toBoolean() ?: true
    val weightOk = positiveWeightValid(weightRaw)

    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        InventoryEditField(
            label = "Peso por unidad (kg)",
            draftId = draft.draftId,
            step = step,
            itemIndex = editor.itemIndex,
            phase = editor.phase,
            fieldKey = KEY_WEIGHT,
            initialValue = weightRaw,
            onPersist = { text ->
                updateInventoryEditor(vm, step) { withEditorValue(it, KEY_WEIGHT, text) }
            },
            isError = weightRaw.isNotBlank() && !weightOk,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tengo la pareja completa",
                style = WizardTypography.cardSubtitle,
                color = WizardColors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = pairAvailable,
                onCheckedChange = { checked ->
                    updateInventoryEditor(vm, step) { withEditorValue(it, KEY_PAIR, checked.toString()) }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = WizardColors.ruleCursor,
                    checkedThumbColor = WizardColors.text,
                    uncheckedTrackColor = WizardColors.cardBorder,
                    uncheckedThumbColor = WizardColors.textMuted,
                ),
            )
        }
        if (weightRaw.isNotBlank() && !weightOk) {
            TrainingNotice("El peso por unidad debe ser positivo y finito.", TrainingNoticeTone.ERROR)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = weightOk,
                onClick = {
                    saveInventoryEditor(vm, step) { editor, inventory ->
                        val weight = parseLocalizedNumber(editor.values[KEY_WEIGHT].orEmpty())
                        if (weight == null || !weight.isFinite() || weight <= 0.0) {
                            null
                        } else {
                            val pairAvailable = editor.values[KEY_PAIR]?.toBoolean() ?: true
                            val stock = DumbbellPairStock(weightPerUnitKg = weight, pairAvailable = pairAvailable)
                            val rows = inventory.dumbbells.toMutableList()
                            val index = editor.itemIndex
                            if (index != null && index in rows.indices) rows[index] = stock else rows.add(stock)
                            inventory.copy(dumbbells = rows)
                        }
                    }
                },
            ) { Text("Guardar", color = WizardColors.text) }
            TextButton(onClick = { cancelInventoryEditor(vm, step) }) {
                Text("Cancelar", color = WizardColors.textMuted)
            }
        }
    }
}

// ─── INVENTORY_KETTLEBELLS ──────────────────────────────────────────────────

/** Kettlebells: lista de pesos reales; una fila por peso declarado. */
@Composable
internal fun InventoryKettlebellsStep(step: SetupStepId, state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val kettlebells = draft.trainingOptions.inventory?.kettlebells.orEmpty()
    val editor = draft.stepEditors[step] ?: SetupStepEditorState()

    if (!editor.editing) {
        Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
            kettlebells.forEachIndexed { index, stock ->
                InventoryRowCard(
                    title = "Kettlebell ${formatTrainingNumber(stock.weightKg)} kg",
                    subtitle = null,
                    onClick = {
                        openInventoryEditor(
                            vm = vm,
                            step = step,
                            itemIndex = index,
                            values = mapOf(KEY_WEIGHT to formatTrainingNumber(stock.weightKg)),
                        )
                    },
                    onRemove = {
                        updateInventory(vm, step) { inventory ->
                            inventory.copy(kettlebells = inventory.kettlebells.filterIndexed { i, _ -> i != index })
                        }
                    },
                )
            }
            InventoryRowCard(
                title = "Añadir kettlebell",
                subtitle = "Peso real de cada una; normalmente basta una de cada peso.",
                editLabel = "Añadir",
                onClick = { openInventoryEditor(vm, step, itemIndex = kettlebells.size, values = emptyMap()) },
            )
            InventoryNoneCard(step = step, group = SetupInventoryGroup.KETTLEBELLS, state = state, vm = vm)
        }
        return
    }

    val weightRaw = editor.values[KEY_WEIGHT].orEmpty()
    val weightOk = positiveWeightValid(weightRaw)

    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        InventoryEditField(
            label = "Peso de la kettlebell (kg)",
            draftId = draft.draftId,
            step = step,
            itemIndex = editor.itemIndex,
            phase = editor.phase,
            fieldKey = KEY_WEIGHT,
            initialValue = weightRaw,
            onPersist = { text ->
                updateInventoryEditor(vm, step) { withEditorValue(it, KEY_WEIGHT, text) }
            },
            isError = weightRaw.isNotBlank() && !weightOk,
        )
        if (weightRaw.isNotBlank() && !weightOk) {
            TrainingNotice("El peso debe ser positivo y finito.", TrainingNoticeTone.ERROR)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = weightOk,
                onClick = {
                    saveInventoryEditor(vm, step) { editor, inventory ->
                        val weight = parseLocalizedNumber(editor.values[KEY_WEIGHT].orEmpty())
                        if (weight == null || !weight.isFinite() || weight <= 0.0) {
                            null
                        } else {
                            val stock = KettlebellStock(weightKg = weight)
                            val rows = inventory.kettlebells.toMutableList()
                            val index = editor.itemIndex
                            if (index != null && index in rows.indices) rows[index] = stock else rows.add(stock)
                            inventory.copy(kettlebells = rows)
                        }
                    }
                },
            ) { Text("Guardar", color = WizardColors.text) }
            TextButton(onClick = { cancelInventoryEditor(vm, step) }) {
                Text("Cancelar", color = WizardColors.textMuted)
            }
        }
    }
}

// ─── INVENTORY_MACHINES ─────────────────────────────────────────────────────

/**
 * Máquinas: tipo explícito (máquina/polea/Smith, nunca deducido del nombre) +
 * rango mínimo/máximo, incremento y carga base reales. La máquina genérica
 * exige además una configuración real del catálogo (id + nombre canónico);
 * cable y Smith son estación multi: tipo + rango bastan. El subeditor reparte
 * los datos en tres subfases —nombre y tipo, mínimo/máximo, incremento/base—
 * de máximo dos campos relacionados, y el tope máximo es obligatorio: una
 * máquina nueva no se declara ilimitada.
 */
@Composable
internal fun InventoryMachinesStep(step: SetupStepId, state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val machines = draft.trainingOptions.inventory?.machines.orEmpty()
    val editor = draft.stepEditors[step] ?: SetupStepEditorState()

    if (!editor.editing) {
        Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
            machines.forEachIndexed { index, machine ->
                InventoryRowCard(
                    title = machine.name,
                    subtitle = machineSummary(machine),
                    onClick = {
                        openInventoryEditor(
                            vm = vm,
                            step = step,
                            itemIndex = index,
                            values = mapOf(
                                KEY_NAME to machine.name,
                                KEY_MIN to formatTrainingNumber(machine.minLoadKg),
                                KEY_MAX to machine.maxLoadKg?.let(::formatTrainingNumber).orEmpty(),
                                KEY_INC to formatTrainingNumber(machine.incrementKg),
                                KEY_BASE to formatTrainingNumber(machine.baseLoadKg),
                                KEY_KIND to machine.equipmentKind.orEmpty(),
                                KEY_CONFIG to machine.configurationId.orEmpty(),
                            ),
                        )
                    },
                    onRemove = {
                        updateInventory(vm, step) { inventory ->
                            inventory.copy(machines = inventory.machines.filterIndexed { i, _ -> i != index })
                        }
                    },
                )
            }
            InventoryRowCard(
                title = "Añadir máquina o polea",
                subtitle = "Nombre aproximado y rango real de carga; nada se asume.",
                editLabel = "Añadir",
                onClick = { openInventoryEditor(vm, step, itemIndex = machines.size, values = emptyMap()) },
            )
            InventoryNoneCard(step = step, group = SetupInventoryGroup.MACHINES, state = state, vm = vm)
        }
        return
    }

    val phase = editor.phase.coerceIn(0, MACHINE_EDITOR_PHASES - 1)
    val keys = machineEditorKeys(phase)
    val valid = machineValuesValid(editor.values)
    val currentKind = editor.values[KEY_KIND].orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
        Text(
            text = "Datos ${phase + 1} de $MACHINE_EDITOR_PHASES",
            style = WizardTypography.caption,
            color = WizardColors.textFaint,
        )
        if (phase == 0) {
            // Nombre libre sólo para estación multi o tipo aún sin declarar; la
            // máquina genérica toma el nombre canónico de su configuración.
            if (currentKind != MACHINE_KIND_GENERIC) {
                InventoryEditField(
                    label = machineFieldLabel(KEY_NAME),
                    draftId = draft.draftId,
                    step = step,
                    itemIndex = editor.itemIndex,
                    phase = phase,
                    fieldKey = KEY_NAME,
                    initialValue = editor.values[KEY_NAME].orEmpty(),
                    onPersist = { text ->
                        updateInventoryEditor(vm, step) { withEditorValue(it, KEY_NAME, text) }
                    },
                    keyboardType = KeyboardType.Text,
                )
            }
            Text(
                text = "¿Qué tipo de máquina es?",
                style = WizardTypography.cardTitle,
                color = WizardColors.text,
            )
            Text(
                text = "Se declara aquí: el nombre de la fila nunca deduce el tipo.",
                style = WizardTypography.cardSubtitle,
                color = WizardColors.textMuted,
            )
            MACHINE_KIND_CHOICES.forEach { (id, label) ->
                WizardChoiceCard(
                    title = label,
                    selected = id == currentKind,
                    onClick = {
                        updateInventoryEditor(vm, step) { current ->
                            // nextKind se calcula sobre el editor ACTUAL de la
                            // cola: dos toques seguidos nunca se pisan.
                            val kind = current.values[KEY_KIND].orEmpty()
                            val nextKind = if (kind == id) "" else id
                            val typed = withEditorValue(current, KEY_KIND, nextKind)
                            if (nextKind == MACHINE_KIND_GENERIC && kind != MACHINE_KIND_GENERIC) {
                                // El nombre canónico lo aporta el catálogo, no el teclado.
                                withEditorValue(typed, KEY_NAME, "")
                            } else {
                                typed
                            }
                        }
                    },
                )
            }
            if (currentKind == MACHINE_KIND_GENERIC) {
                MachineConfigPicker(
                    identity = "${draft.draftId}|${step.name}|${editor.itemIndex ?: "slot"}|$phase",
                    configurationId = editor.values[KEY_CONFIG].orEmpty(),
                    configurationName = editor.values[KEY_NAME].orEmpty(),
                    onPick = { configId, canonicalName ->
                        updateInventoryEditor(vm, step) { current ->
                            withEditorValue(
                                withEditorValue(current, KEY_CONFIG, configId),
                                KEY_NAME,
                                canonicalName,
                            )
                        }
                    },
                )
            }
        } else {
            keys.forEach { key ->
                InventoryEditField(
                    label = machineFieldLabel(key),
                    draftId = draft.draftId,
                    step = step,
                    itemIndex = editor.itemIndex,
                    phase = phase,
                    fieldKey = key,
                    initialValue = editor.values[key].orEmpty(),
                    onPersist = { text ->
                        updateInventoryEditor(vm, step) { withEditorValue(it, key, text) }
                    },
                    keyboardType = KeyboardType.Decimal,
                )
            }
        }
        if (phase == MACHINE_EDITOR_PHASES - 1 && !valid) {
            TrainingNotice(
                "Completa tipo, nombre, mínimo, máximo, incremento y base; en la genérica elige una configuración del catálogo.",
                TrainingNoticeTone.ERROR,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { cancelInventoryEditor(vm, step) }) {
                Text("Cancelar", color = WizardColors.textMuted)
            }
            TextButton(
                enabled = phase > 0,
                onClick = { updateInventoryEditor(vm, step) { withEditorPhase(it, phase - 1) } },
            ) { Text("Atrás", color = WizardColors.text) }
            if (phase < MACHINE_EDITOR_PHASES - 1) {
                TextButton(onClick = { updateInventoryEditor(vm, step) { withEditorPhase(it, phase + 1) } }) {
                    Text("Siguiente", color = WizardColors.text)
                }
            } else {
                TextButton(
                    enabled = valid,
                    onClick = {
                        saveInventoryEditor(vm, step) { editor, inventory ->
                            val values = editor.values
                            val name = values[KEY_NAME]?.trim().orEmpty()
                            val min = parseLocalizedNumber(values[KEY_MIN].orEmpty())
                            val max = parseLocalizedNumber(values[KEY_MAX].orEmpty())
                            val increment = parseLocalizedNumber(values[KEY_INC].orEmpty())
                            val base = parseLocalizedNumber(values[KEY_BASE].orEmpty())
                            val kind = machineKindOrNull(values[KEY_KIND].orEmpty())
                            if (!machineValuesValid(values) || name.isEmpty() || min == null ||
                                max == null || increment == null || base == null || kind == null
                            ) {
                                null
                            } else {
                                val machine = MachineLoadRange(
                                    name = name,
                                    minLoadKg = min,
                                    maxLoadKg = max,
                                    incrementKg = increment,
                                    baseLoadKg = base,
                                    equipmentKind = kind,
                                    configurationId = values[KEY_CONFIG].orEmpty().trim()
                                        .takeIf { it.isNotBlank() },
                                )
                                val rows = inventory.machines.toMutableList()
                                val index = editor.itemIndex
                                if (index != null && index in rows.indices) rows[index] = machine else rows.add(machine)
                                inventory.copy(machines = rows)
                            }
                        }
                    },
                ) { Text("Guardar", color = WizardColors.text) }
            }
        }
    }
}

/**
 * Selector de configuración real para la máquina genérica: busca en el
 * snapshot que ya tiene el repositorio (`exerciseCatalogSnapshot`, lectura del
 * asset aprobado existente, sin regenerar nada) y conserva id de
 * configuración + nombre canónico del catálogo. Cable y Smith no lo usan:
 * son estación multi con tipo + rango.
 */
@Composable
private fun MachineConfigPicker(
    identity: String,
    configurationId: String,
    configurationName: String,
    onPick: (id: String, name: String) -> Unit,
) {
    var query by rememberSaveable(identity) { mutableStateOf("") }
    val catalog = remember { exerciseCatalogSnapshot() }
    val results = remember(query, catalog) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            emptyList()
        } else {
            catalog.asSequence()
                .filter { info -> info.name.contains(needle, ignoreCase = true) }
                .distinctBy { info -> info.catalogConfigurationId ?: info.id }
                .take(8)
                .toList()
        }
    }

    if (configurationId.isNotBlank()) {
        InventoryRowCard(
            title = configurationName.ifBlank { configurationId },
            subtitle = "Configuración elegida del catálogo.",
            editLabel = "Quitar",
            onClick = { onPick("", "") },
        )
    }
    TrainingNumberField(
        label = "Buscar en el catálogo (ej. prensa, polea)",
        value = query,
        onValueChange = { query = it },
        keyboardType = KeyboardType.Text,
    )
    when {
        catalog.isEmpty() -> TrainingNotice(
            "El catálogo de ejercicios aún no está disponible.",
            TrainingNoticeTone.ERROR,
        )

        query.isNotBlank() && results.isEmpty() -> TrainingNotice(
            "Sin coincidencias en el catálogo para «${query.trim()}».",
        )
    }
    results.forEach { info ->
        InventoryRowCard(
            title = info.name,
            subtitle = info.equipment,
            editLabel = "Elegir",
            onClick = {
                val configId = info.catalogConfigurationId?.takeIf { it.isNotBlank() } ?: info.id
                onPick(configId, catalogConfigurationDisplayName(configId) ?: info.name)
            },
        )
    }
}

private fun machineFieldLabel(key: String): String = when (key) {
    KEY_NAME -> "Nombre de la máquina"
    KEY_MIN -> "Carga mínima (kg)"
    KEY_MAX -> "Carga máxima (kg) · opcional"
    KEY_INC -> "Incremento por paso (kg)"
    else -> "Carga base del carro (kg)"
}

private fun machineSummary(machine: MachineLoadRange): String = buildString {
    append(formatTrainingNumber(machine.minLoadKg))
    append("–")
    append(machine.maxLoadKg?.let(::formatTrainingNumber) ?: "sin tope")
    append(" kg · paso ")
    append(formatTrainingNumber(machine.incrementKg))
    append(" kg · base ")
    append(formatTrainingNumber(machine.baseLoadKg))
    append(" kg")
}
