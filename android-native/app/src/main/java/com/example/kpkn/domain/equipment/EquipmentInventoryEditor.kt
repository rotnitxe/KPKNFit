package com.example.kpkn.domain.equipment

import com.example.kpkn.data.models.DumbbellPairStock
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.KettlebellStock
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.domain.calculations.PlateCalculator
import java.math.BigDecimal
import java.util.Locale

data class PlateDraft(val weightKg: String = "", val countPerSide: String = "1", val unlimited: Boolean = false)
data class DumbbellDraft(val weightKg: String = "", val pairAvailable: Boolean = true)
data class MachineDraft(
    val name: String = "",
    val equipmentKind: String = "machine",
    val minLoadKg: String = "0",
    val maxLoadKg: String = "",
    val incrementKg: String = "",
    val baseLoadKg: String = "0",
    val configurationId: String? = null,
)

/** Conserva también las entradas incompletas: nunca corrige un peso en silencio. */
data class EquipmentInventoryDraft(
    val barbellWeightKg: String = "",
    val plates: List<PlateDraft> = emptyList(),
    val dumbbells: List<DumbbellDraft> = emptyList(),
    val kettlebells: List<String> = emptyList(),
    val machines: List<MachineDraft> = emptyList(),
    val supportEquipment: Set<String> = emptySet(),
)

data class EquipmentValidation(val inventory: EquipmentInventory?, val errors: Map<String, String>) {
    val isValid: Boolean get() = inventory != null
}

data class EquipmentLoadSummary(
    val minKg: Double? = null,
    val maxKg: Double? = null,
    val uniformStepKg: Double? = null,
    val boundedPreview: Boolean = false,
    val warnings: List<String> = emptyList(),
)

data class DumbbellRangeDraft(val fromKg: String = "", val toKg: String = "", val stepKg: String = "")
data class DumbbellRangeResult(val draft: EquipmentInventoryDraft, val error: String? = null)
data class EquipmentMachineConfiguration(val id: String, val name: String, val equipmentKind: String)

enum class EquipmentInventoryTemplate(val label: String) {
    COMMERCIAL("Gimnasio comercial"), HOME("Gimnasio en casa"), BODYWEIGHT("Solo peso corporal y bandas")
}

object EquipmentInventoryEditor {
    // Límites de entrada y de trabajo: cubren material deportivo y acotan rangos accidentales.
    const val MAX_IMPLEMENT_KG = 250.0
    const val MAX_MACHINE_KG = 2000.0
    const val MAX_ROWS = 100
    const val SUMMARY_LIMIT_KG = 400.0
    private const val MAX_SUMMARY_STEPS = 128
    val machineKinds: Map<String, String> = linkedMapOf("machine" to "Máquina", "cable" to "Polea", "smith_machine" to "Multipower")
    val supportKinds: Map<String, String> = linkedMapOf(
        "support" to "Rack o banco", "pull_up_bar" to "Barra de dominadas",
        "band" to "Bandas", "ball" to "Balón", "cardio" to "Equipo de cardio",
    )

    fun number(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    fun format(value: Double): String = if (value.isFinite()) BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',') else value.toString()

    fun fromInventory(inventory: EquipmentInventory) = EquipmentInventoryDraft(
        barbellWeightKg = inventory.barbellWeightKg?.let(::format).orEmpty(),
        plates = inventory.plates.map { PlateDraft(format(it.weightKg), it.countPerSide?.toString() ?: "1", it.countPerSide == null) },
        dumbbells = inventory.dumbbells.map { DumbbellDraft(format(it.weightPerUnitKg), it.pairAvailable) },
        kettlebells = inventory.kettlebells.map { format(it.weightKg) },
        machines = inventory.machines.map {
            MachineDraft(it.name, it.equipmentKind.orEmpty(), format(it.minLoadKg), it.maxLoadKg?.let(::format).orEmpty(),
                format(it.incrementKg), format(it.baseLoadKg), it.configurationId)
        },
        supportEquipment = inventory.supportEquipment,
    )

    fun validate(draft: EquipmentInventoryDraft): EquipmentValidation {
        val errors = linkedMapOf<String, String>()
        fun weight(path: String, text: String, limit: Double = MAX_IMPLEMENT_KG, zero: Boolean = false): Double? {
            val value = number(text)
            if (value == null || value > limit || if (zero) value < 0.0 else value <= 0.0) {
                errors[path] = if (zero) "Introduce un valor entre 0 y ${format(limit)} kg."
                    else "Introduce un peso mayor que 0 y hasta ${format(limit)} kg."
            } else if (BigDecimal.valueOf(value).stripTrailingZeros().scale() > 3) {
                errors[path] = "Usa hasta tres decimales."
            }
            return value
        }
        fun duplicates(path: String, values: List<Double?>) {
            val repeated = values.filterNotNull().groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            values.forEachIndexed { index, value -> if (value in repeated) errors["$path.$index.weight"] = "Este peso ya está en la lista." }
            if (values.size > MAX_ROWS) errors[path] = "El límite es de $MAX_ROWS pesos."
        }

        val bar = draft.barbellWeightKg.takeIf { it.isNotBlank() }?.let { weight("barbell", it, 100.0) }
        val plates = draft.plates.mapIndexed { index, row ->
            val kg = weight("plates.$index.weight", row.weightKg)
            val count = row.countPerSide.trim().toIntOrNull()
            if (!row.unlimited && (count == null || count !in 0..100)) errors["plates.$index.count"] = "Indica de 0 a 100 discos por lado."
            kg?.let { PlateStock(it, if (row.unlimited) null else count) }
        }
        duplicates("plates", plates.map { it?.weightKg })
        val dumbbells = draft.dumbbells.mapIndexed { index, row ->
            weight("dumbbells.$index.weight", row.weightKg)?.let { DumbbellPairStock(it, row.pairAvailable) }
        }
        duplicates("dumbbells", dumbbells.map { it?.weightPerUnitKg })
        val kettlebells = draft.kettlebells.mapIndexed { index, row ->
            weight("kettlebells.$index.weight", row)?.let(::KettlebellStock)
        }
        duplicates("kettlebells", kettlebells.map { it?.weightKg })
        val machineKeys = mutableSetOf<String>()
        val configurationIds = mutableSetOf<String>()
        val machines = draft.machines.mapIndexed { index, row ->
            val path = "machines.$index"
            if (row.name.isBlank() || row.name.trim().length > 80) errors["$path.name"] = "Escribe un nombre de hasta 80 caracteres."
            if (row.equipmentKind !in machineKinds) errors["$path.kind"] = "Elige el tipo de máquina."
            if (!machineKeys.add("${row.equipmentKind}|${row.name.trim().lowercase(Locale.ROOT)}")) errors["$path.name"] = "Esta máquina ya está en la lista."
            val configuration = row.configurationId?.trim()?.takeIf { it.isNotEmpty() }
            if (configuration != null && !configurationIds.add(configuration)) errors["$path.configuration"] = "Esta configuración ya tiene una máquina."
            val min = weight("$path.min", row.minLoadKg, MAX_MACHINE_KG, zero = true)
            val max = weight("$path.max", row.maxLoadKg, MAX_MACHINE_KG)
            val step = weight("$path.increment", row.incrementKg, MAX_MACHINE_KG)
            val base = weight("$path.base", row.baseLoadKg, MAX_MACHINE_KG, zero = true)
            if (min != null && max != null && max < min) errors["$path.max"] = "La máxima debe ser mayor o igual que la mínima."
            if (base != null && max != null && base > max) errors["$path.base"] = "La carga base no puede superar la máxima."
            MachineLoadRange(row.name.trim(), min ?: 0.0, max, step ?: 0.0, base ?: 0.0, row.equipmentKind, configuration)
        }
        if (machines.size > MAX_ROWS) errors["machines"] = "El límite es de $MAX_ROWS máquinas."
        if (draft.supportEquipment.any { it !in supportKinds }) errors["support"] = "Quita el material auxiliar no reconocido."
        return EquipmentValidation(
            inventory = if (errors.isEmpty()) EquipmentInventory(bar, plates.filterNotNull(), dumbbells.filterNotNull(),
                kettlebells.filterNotNull(), machines, draft.supportEquipment) else null,
            errors = errors,
        )
    }

    /** Añade parejas sin duplicar ni cambiar la disponibilidad ya declarada. */
    fun addDumbbellRange(draft: EquipmentInventoryDraft, range: DumbbellRangeDraft): DumbbellRangeResult {
        val from = number(range.fromKg)
        val to = number(range.toKg)
        val step = number(range.stepKg)
        if (from == null || to == null || step == null || from <= 0 || to < from || to > MAX_IMPLEMENT_KG || step <= 0 ||
            listOf(from, to, step).any { BigDecimal.valueOf(it).stripTrailingZeros().scale() > 3 }) {
            return DumbbellRangeResult(draft, "Revisa el inicio, el final y el salto; usa pesos positivos y hasta tres decimales.")
        }
        val first = BigDecimal.valueOf(from)
        val last = BigDecimal.valueOf(to)
        val jump = BigDecimal.valueOf(step)
        val size = last.subtract(first).divideToIntegralValue(jump)
        if (size >= BigDecimal.valueOf(MAX_ROWS.toLong())) return DumbbellRangeResult(draft, "El rango admite hasta $MAX_ROWS pesos.")
        val existing = draft.dumbbells.mapNotNull { number(it.weightKg) }.toSet()
        val added = (0..size.toInt()).map { first.add(jump.multiply(BigDecimal.valueOf(it.toLong()))).toDouble() }
            .filterNot { it in existing }.map { DumbbellDraft(format(it)) }
        if (draft.dumbbells.size + added.size > MAX_ROWS) return DumbbellRangeResult(draft, "El límite es de $MAX_ROWS pesos.")
        return DumbbellRangeResult(draft.copy(dumbbells = (draft.dumbbells + added).sortedBy { number(it.weightKg) ?: Double.MAX_VALUE }))
    }

    fun template(template: EquipmentInventoryTemplate): EquipmentInventoryDraft = fromInventory(when (template) {
        EquipmentInventoryTemplate.COMMERCIAL -> EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0).map { PlateStock(it, 2) },
            dumbbells = (1..20).map { DumbbellPairStock(it * 2.0) },
            kettlebells = listOf(8.0, 12.0, 16.0, 20.0, 24.0).map(::KettlebellStock),
            machines = listOf(
                MachineLoadRange("Polea", 5.0, 100.0, 5.0, 0.0, "cable"),
                MachineLoadRange("Multipower", 20.0, 200.0, 2.5, 20.0, "smith_machine"),
            ),
            supportEquipment = setOf("support", "pull_up_bar", "band", "cardio"),
        )
        EquipmentInventoryTemplate.HOME -> EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(1.25, 2.5, 5.0, 10.0, 20.0).map { PlateStock(it, 1) },
            dumbbells = listOf(6.0, 10.0, 14.0).map { DumbbellPairStock(it) },
            supportEquipment = setOf("support", "band"),
        )
        EquipmentInventoryTemplate.BODYWEIGHT -> EquipmentInventory(supportEquipment = setOf("pull_up_bar", "band"))
    })

    /** Comprueba los pasos con el mismo resolutor que usa el calentamiento, sin otro algoritmo de discos. */
    fun summarize(inventory: EquipmentInventory): EquipmentLoadSummary {
        val warnings = buildList {
            if (inventory.dumbbells.any { !it.pairAvailable }) add("Hay mancuernas sin pareja; no se usarán como par.")
            if (inventory.machines.any { it.equipmentKind == "machine" && it.configurationId == null }) add("Asocia la configuración de cada máquina para resolver su carga.")
        }
        val bar = inventory.barbellWeightKg ?: return EquipmentLoadSummary(warnings = warnings +
            if (inventory.plates.isNotEmpty()) listOf("Los discos no resuelven cargas con barra hasta declarar una barra.") else emptyList())
        val plates = inventory.plates.filter { it.countPerSide == null || it.countPerSide > 0 }
        val unlimited = plates.any { it.countPerSide == null }
        val capacity = if (unlimited) SUMMARY_LIMIT_KG else bar + 2.0 * plates.sumOf { it.weightKg * (it.countPerSide ?: 0) }
        val target = minOf(capacity, SUMMARY_LIMIT_KG)
        val maximum = PlateCalculator.calculatePlates(target, inventory).achievedWeight
        val step = plates.minOfOrNull { it.weightKg * 2.0 }
        val steps = step?.let { (maximum - bar) / it }
        val checked = step != null && steps != null && steps <= MAX_SUMMARY_STEPS &&
            (1..steps.toInt()).all { PlateCalculator.calculatePlates(bar + it * step, inventory).isExact }
        val gaps = if (step != null && !checked && steps != null && steps <= MAX_SUMMARY_STEPS)
            listOf("Hay cargas intermedias inalcanzables; el salto depende de la combinación de discos.") else emptyList()
        return EquipmentLoadSummary(bar, maximum, step.takeIf { checked }, unlimited || capacity > SUMMARY_LIMIT_KG, warnings + gaps)
    }
}
