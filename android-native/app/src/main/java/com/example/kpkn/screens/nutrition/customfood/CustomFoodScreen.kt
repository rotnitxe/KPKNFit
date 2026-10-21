package com.example.kpkn.screens.nutrition.customfood

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.screens.nutrition.scan.FullScreenOverlay
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknDangerPill
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * El formulario de un alimento propio, sin estado propio: alta, edición y borrado. Los errores solo se enseñan cuando se pulsó
 * «Guardar» ([showErrors]); el aviso de kcal que no cuadran se enseña siempre que exista y nunca bloquea.
 */
@Composable
fun CustomFoodFormContent(
    form: CustomFoodForm,
    validation: CustomFoodValidation,
    showErrors: Boolean,
    isEditing: Boolean,
    saving: Boolean,
    saveError: String?,
    confirmingDelete: Boolean,
    onChange: (CustomFoodForm) -> Unit,
    onSave: () -> Unit,
    onAskDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun errorOf(field: CustomFoodField): String? = if (showErrors) validation.errors[field] else null
    val per100 = form.basis == CustomFoodBasis.PER_100G
    Column(modifier.background(KpknSurface.canvas).testTag("custom-food-root")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KpknIconButton(KpknLineIcons.Close, "Cerrar sin guardar", onClose, Modifier.testTag("custom-food-close"))
            Spacer(Modifier.width(8.dp))
            Text(
                if (isEditing) "Editar alimento propio" else "Alimento propio",
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Producto")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpknTextField(
                        value = form.name,
                        onValueChange = { onChange(form.copy(name = it)) },
                        modifier = Modifier.testTag("custom-food-name"),
                        label = "Nombre",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        error = errorOf(CustomFoodField.NAME),
                    )
                    KpknTextField(
                        value = form.brand,
                        onValueChange = { onChange(form.copy(brand = it)) },
                        modifier = Modifier.testTag("custom-food-brand"),
                        label = "Marca (opcional)",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        error = errorOf(CustomFoodField.BRAND),
                    )
                    KpknTextField(
                        value = form.barcode,
                        onValueChange = { onChange(form.copy(barcode = it)) },
                        modifier = Modifier.testTag("custom-food-barcode"),
                        label = "Código de barras (opcional)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        error = errorOf(CustomFoodField.BARCODE),
                    )
                }
            }
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Porción y base")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    KpknTextField(
                        value = form.servingAmount,
                        onValueChange = { onChange(form.copy(servingAmount = it)) },
                        modifier = Modifier.weight(1f).testTag("custom-food-serving"),
                        label = "Porción",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        error = errorOf(CustomFoodField.SERVING),
                    )
                    KpknSegmentedControl(
                        options = listOf("g", "ml"),
                        selectedIndex = if (form.servingUnit == "ml") 1 else 0,
                        onSelect = { onChange(form.copy(servingUnit = if (it == 1) "ml" else "g")) },
                        modifier = Modifier.width(112.dp).padding(top = 22.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                KpknSegmentedControl(
                    options = listOf("Por porción", "Por 100 g"),
                    selectedIndex = if (per100) 1 else 0,
                    onSelect = { onChange(form.copy(basis = if (it == 1) CustomFoodBasis.PER_100G else CustomFoodBasis.PER_SERVING)) },
                    modifier = Modifier.fillMaxWidth().testTag("custom-food-basis"),
                )
                Spacer(Modifier.height(8.dp))
                val amount = form.servingAmount.trim().takeIf { CustomFoodValidator.parseNumber(it) != null } ?: "la"
                Text(
                    if (per100) "Los nutrientes de abajo son los de 100 g de producto."
                    else if (amount == "la") "Los nutrientes de abajo son los de una porción."
                    else "Los nutrientes de abajo son los de una porción de $amount ${form.servingUnit}.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                errorOf(CustomFoodField.BASIS)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = KpknType.caption, color = WizardColors.danger)
                }
            }
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Nutrientes")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Energía", form.calories, { onChange(form.copy(calories = it)) }, errorOf(CustomFoodField.CALORIES), "kcal", "custom-food-kcal")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        NumberField("Proteína", form.protein, { onChange(form.copy(protein = it)) }, errorOf(CustomFoodField.PROTEIN), "g", "custom-food-protein", Modifier.weight(1f))
                        NumberField("Carbohidratos", form.carbs, { onChange(form.copy(carbs = it)) }, errorOf(CustomFoodField.CARBS), "g", "custom-food-carbs", Modifier.weight(1f))
                    }
                    NumberField("Grasas", form.fats, { onChange(form.copy(fats = it)) }, errorOf(CustomFoodField.FATS), "g", "custom-food-fats")
                }
            }
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Opcionales")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        NumberField("Fibra", form.fiber, { onChange(form.copy(fiber = it)) }, errorOf(CustomFoodField.FIBER), "g", "custom-food-fiber", Modifier.weight(1f))
                        NumberField("Azúcar", form.sugar, { onChange(form.copy(sugar = it)) }, errorOf(CustomFoodField.SUGAR), "g", "custom-food-sugar", Modifier.weight(1f))
                    }
                    NumberField("Sodio", form.sodiumMg, { onChange(form.copy(sodiumMg = it)) }, errorOf(CustomFoodField.SODIUM), "mg", "custom-food-sodium", imeLast = true)
                }
            }
            validation.warning?.let {
                KpknModule(Modifier.fillMaxWidth().testTag("custom-food-warning"), accent = SymbolPalette.energia) {
                    Text(it, style = KpknType.bodyStrong, color = WizardColors.text)
                }
            }
            saveError?.let {
                KpknModule(Modifier.fillMaxWidth().testTag("custom-food-save-error"), accent = WizardColors.danger) {
                    Text(it, style = KpknType.bodyStrong, color = WizardColors.text)
                }
            }
            if (isEditing) {
                if (!confirmingDelete) {
                    KpknTextAction("Eliminar alimento", onAskDelete, Modifier.testTag("custom-food-delete"), color = WizardColors.danger, leadingIcon = KpknLineIcons.Trash)
                } else {
                    KpknModule(Modifier.fillMaxWidth().testTag("custom-food-delete-ask"), accent = WizardColors.danger) {
                        Text("¿Eliminar este alimento?", style = KpknType.bodyStrong, color = WizardColors.text)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Se quita de tus alimentos propios. Las comidas que ya registraste con él no cambian.",
                            style = KpknType.body,
                            color = WizardColors.textMuted,
                        )
                        Spacer(Modifier.height(14.dp))
                        KpknDangerPill("Sí, eliminar", onConfirmDelete, Modifier.fillMaxWidth(), tag = "custom-food-delete-confirm")
                        Spacer(Modifier.height(8.dp))
                        KpknSecondaryPill("Cancelar", onCancelDelete, Modifier.fillMaxWidth(), tag = "custom-food-delete-cancel")
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            KpknPrimaryPill(
                text = if (saving) "Guardando…" else "Guardar alimento",
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                tag = "custom-food-save",
                icon = KpknLineIcons.Check,
                enabled = !saving,
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    unit: String,
    tag: String,
    modifier: Modifier = Modifier,
    imeLast: Boolean = false,
) {
    KpknTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.testTag(tag),
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (imeLast) ImeAction.Done else ImeAction.Next),
        error = error,
        trailing = { Text(unit, style = KpknType.unit, color = WizardColors.textMuted) },
    )
}

/**
 * El formulario con su estado, a pantalla completa sobre el registro de comidas. [initial] nulo = alta; con [barcode] el código ya
 * va puesto (alta desde el escáner) y [prefillName]/[prefillBrand] adelantan el nombre y la marca. [save] y [delete] esperan al
 * commit de Room: si fallan, el formulario sigue abierto con el aviso y lo escrito.
 */
@Composable
fun CustomFoodFormOverlay(
    initial: FoodItem?,
    save: suspend (FoodItem) -> Unit,
    onSaved: (FoodItem) -> Unit,
    onDismiss: () -> Unit,
    barcode: String? = null,
    prefillName: String? = null,
    prefillBrand: String? = null,
    delete: (suspend (String) -> Unit)? = null,
    onDeleted: () -> Unit = {},
    /** Solo para la vista previa de depuración: el formulario ya escrito y con los errores a la vista. */
    initialForm: CustomFoodForm? = null,
    showErrorsInitially: Boolean = false,
) {
    val id = remember(initial) { initial?.id ?: "custom_${UUID.randomUUID()}" }
    var form by remember {
        mutableStateOf(
            initialForm ?: initial?.let { CustomFoodValidator.fromFood(it) }
                ?: CustomFoodForm(name = prefillName.orEmpty(), brand = prefillBrand.orEmpty(), barcode = barcode.orEmpty()),
        )
    }
    var showErrors by remember { mutableStateOf(showErrorsInitially) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val validation = remember(form) { CustomFoodValidator.validate(form, id, initial) }
    val scope = rememberCoroutineScope()
    FullScreenOverlay(onBack = onDismiss) {
        CustomFoodFormContent(
            form = form,
            validation = validation,
            showErrors = showErrors,
            isEditing = initial != null,
            saving = saving,
            saveError = saveError,
            confirmingDelete = confirmingDelete,
            onChange = {
                form = it
                saveError = null
            },
            onSave = {
                showErrors = true
                val food = validation.food
                if (food != null && validation.isValid && !saving) {
                    saving = true
                    saveError = null
                    scope.launch {
                        try {
                            save(food)
                            onSaved(food)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            saveError = "No se pudo guardar el alimento. Inténtalo de nuevo."
                        } finally {
                            saving = false
                        }
                    }
                }
            },
            onAskDelete = { confirmingDelete = true },
            onConfirmDelete = {
                val remove = delete
                if (remove != null && initial != null && !saving) {
                    saving = true
                    scope.launch {
                        try {
                            remove(initial.id)
                            onDeleted()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            saveError = "No se pudo eliminar el alimento. Inténtalo de nuevo."
                            confirmingDelete = false
                        } finally {
                            saving = false
                        }
                    }
                }
            },
            onCancelDelete = { confirmingDelete = false },
            onClose = onDismiss,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
