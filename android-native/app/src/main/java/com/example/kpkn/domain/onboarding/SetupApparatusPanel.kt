package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.domain.training.EFFECTIVE_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.EquipmentKeyGroups

/**
 * T-005 / §13.2 — subpanel «¿Qué tienes disponible?» del paso de material.
 *
 * El panel es una SUBPANEL de EQUIPMENT (paso AVAILABILITY): presencia
 * `Sí / No / No sé` sobre las claves curadas de [EFFECTIVE_EQUIPMENT_KEYS],
 * agrupada por grupo humano (bancos/rack, tirón/apoyos, piernas, torso) y
 * SOLO con los ítems relevantes a las categorías elegidas. Omitir deja
 * `UNKNOWN` (nunca `PRESENT`) y «No tengo otros» marca ausentes los ítems
 * visibles todavía desconocidos. Nunca hay kilos ni cantidades.
 *
 * Este módulo es puro Kotlin: la UI pinta; la escritura vive en
 * `SetupStepAnswers.withApparatusPresence`.
 */
data class SetupApparatusItem(
    /** Clave estable (§13.2). */
    val key: String,
    /** Etiqueta humana para la UI (nunca el token). */
    val label: String,
    /** Grupo resumido del panel. */
    val group: String,
    /** true → se persiste en `supports`; false → en `apparatus`. */
    val isSupport: Boolean,
    /** Categoría que delimita el ítem; null = solo con categorías confirmadas. */
    val category: EquipmentCategory?,
)

object SetupApparatusPanel {

    /**
     * Clave de presencia de bicicleta al aire libre (§15.1: BIKE_OUTDOOR
     * exige confirmar acceso si no consta; no se hereda por marcar «Cardio»).
     * No es una clave curada del panel de gimnasio: se declara en el propio
     * paso de cardio, con presencia — nunca con kilos ni cantidades.
     *
     * La bicicleta es de la persona, no de un lugar ni de un símbolo de material: al rehacer la
     * disponibilidad desde los símbolos (cambiar de material o de lugares) se conserva con [withBikeOf].
     */
    const val OUTDOOR_BIKE_KEY = "outdoor_bike"

    /** Orden estable de los grupos del resumen (§13.2). */
    val groupOrder: List<String> = listOf(
        EquipmentKeyGroups.BENCH_RACK,
        EquipmentKeyGroups.PULL_SUPPORT,
        EquipmentKeyGroups.LEGS,
        EquipmentKeyGroups.TORSO,
    )

    /** Soportes: banco/rack/apoyos y barra de dominadas. */
    private val supportCategories = setOf(EquipmentCategory.SUPPORT, EquipmentCategory.PULL_UP_BAR)

    fun isSupport(category: EquipmentCategory?): Boolean =
        category == null || category in supportCategories

    /**
     * Paquete A · B1: llave curada del panel que confirma un token del vocabulario de material
     * (`rack` → `squat_rack`, `bench` → `bench_flat`, `pull_up_bar` → `pullup_bar`…). Es la primera
     * llave de [EFFECTIVE_EQUIPMENT_KEYS] cuyos `attestedTokens` contienen el token o cuyas
     * `machineConfigurations` contienen el id de la configuración (con o sin el prefijo
     * `machine_config:`). Devuelve `null` para `machine` y para los tokens de categoría (`barbell`,
     * `dumbbells`, `kettlebell`…): una categoría no tiene una llave que confirmar, solo se marca o no.
     */
    fun keyForToken(token: String): String? {
        val configurationId = token.removePrefix("machine_config:")
        return EFFECTIVE_EQUIPMENT_KEYS.firstOrNull { key ->
            token in key.attestedTokens || configurationId in key.machineConfigurations
        }?.key
    }

    /**
     * Paquete A · B1: categorías que hay que tener confirmadas para que las llaves [keys] se
     * muestren y cuenten (§13.1 regla 2). Una llave sin categoría propia (la barra EZ) cuenta como
     * [EquipmentCategory.SUPPORT]; las llaves desconocidas se ignoran.
     */
    fun categoriesFor(keys: Collection<String>): Set<EquipmentCategory> =
        keys.mapNotNullTo(LinkedHashSet<EquipmentCategory>()) { key ->
            EFFECTIVE_EQUIPMENT_KEYS.firstOrNull { it.key == key }
                ?.let { it.category ?: EquipmentCategory.SUPPORT }
        }

    /**
     * Ítems relevantes para [categories] elegidas. Una clave sin categoría
     * propia solo aparece cuando hay al menos una categoría confirmada
     * (§13.1 regla 2); con `bodyweight_only` (conjunto vacío) no se muestra
     * ningún aparato.
     */
    fun itemsFor(categories: Set<EquipmentCategory>): List<SetupApparatusItem> {
        if (categories.isEmpty()) return emptyList()
        fun groupIndex(group: String): Int = groupOrder.indexOf(group).let { index ->
            if (index < 0) Int.MAX_VALUE else index
        }
        return EFFECTIVE_EQUIPMENT_KEYS
            .filter { key -> key.category == null || key.category in categories }
            .map { key ->
                SetupApparatusItem(
                    key = key.key,
                    label = key.label,
                    group = key.group,
                    isSupport = isSupport(key.category),
                    category = key.category,
                )
            }
            .sortedWith(compareBy({ groupIndex(it.group) }, { it.label }))
    }

    /** Presencia de una clave; ausente = UNKNOWN (nunca PRESENT implícito). */
    fun presenceOf(availability: EquipmentAvailability?, key: String): ApparatusPresence =
        availability?.presenceOf(key) ?: ApparatusPresence.UNKNOWN

    /** ¿La persona declaró que tiene bicicleta al aire libre? Solo [ApparatusPresence.PRESENT] lo afirma. */
    fun hasBike(availability: EquipmentAvailability?): Boolean =
        presenceOf(availability, OUTDOOR_BIKE_KEY) == ApparatusPresence.PRESENT

    /**
     * Declara ([has] = true) o retira la bicicleta al aire libre. Retirarla borra la llave (no deja un «ausente» suelto: la
     * persona no dijo «no tengo» a una pregunta, simplemente no la tiene). Sin disponibilidad declarada no se fabrica
     * ninguna (null).
     */
    fun withBike(current: EquipmentAvailability?, has: Boolean): EquipmentAvailability? {
        if (current == null) return null
        val without = withoutBike(current)
        return if (has) without.copy(apparatus = without.apparatus + (OUTDOOR_BIKE_KEY to ApparatusPresence.PRESENT)) else without
    }

    /** La misma disponibilidad sin la bicicleta: lo que queda es el material de los símbolos y nada más. */
    fun withoutBike(availability: EquipmentAvailability): EquipmentAvailability = availability.copy(
        apparatus = availability.apparatus - OUTDOOR_BIKE_KEY,
        supports = availability.supports - OUTDOOR_BIKE_KEY,
    )

    /**
     * La disponibilidad [rebuilt] (rehecha desde los símbolos de material) con la bicicleta que [previous] declaró: la
     * bicicleta no es material de ningún símbolo, así que rehacer el material no la borra. Sin declaración previa no
     * se escribe nada.
     */
    fun withBikeOf(previous: EquipmentAvailability?, rebuilt: EquipmentAvailability): EquipmentAvailability {
        val presence = previous?.presenceOf(OUTDOOR_BIKE_KEY)?.takeIf { it != ApparatusPresence.UNKNOWN } ?: return rebuilt
        return rebuilt.copy(apparatus = rebuilt.apparatus + (OUTDOOR_BIKE_KEY to presence))
    }

    /**
     * Escritura pura de UNA presencia sobre la disponibilidad confirmada.
     * Sin disponibilidad declarada no hay panel que escribir (devuelve
     * [current] intacta: el subpanel no fabrica categorías).
     */
    fun withPresence(
        current: EquipmentAvailability?,
        key: String,
        presence: ApparatusPresence,
        isSupport: Boolean,
    ): EquipmentAvailability? {
        if (current == null) return null
        return if (isSupport) {
            current.copy(supports = current.supports + (key to presence))
        } else {
            current.copy(apparatus = current.apparatus + (key to presence))
        }
    }

    /**
     * «No tengo otros» (§13.2): marca `ABSENT` solo los ítems VISIBLES que
     * todavía están `UNKNOWN`; una confirmación previa (Sí/No) se conserva.
     */
    fun markVisibleAbsent(
        current: EquipmentAvailability?,
        visible: List<SetupApparatusItem>,
    ): EquipmentAvailability? {
        if (current == null) return null
        val apparatus = current.apparatus.toMutableMap()
        val supports = current.supports.toMutableMap()
        visible.forEach { item ->
            val target = if (item.isSupport) supports else apparatus
            if (target[item.key] == null || target[item.key] == ApparatusPresence.UNKNOWN) {
                target[item.key] = ApparatusPresence.ABSENT
            }
        }
        return current.copy(apparatus = apparatus, supports = supports)
    }
}
