package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.domain.training.EquipmentKeys
import com.example.kpkn.domain.training.SymbolEquipmentKeys

/**
 * Traducción pura entre los **símbolos de implemento** del paso de material ([EquipmentSymbolId]) y la disponibilidad
 * que entiende el motor ([EquipmentAvailability]: categorías + presencia de llaves curadas). Es el único sitio que
 * conoce las dos vocabularios: la UI habla en símbolos y el motor, en categorías y llaves.
 *
 * Reglas:
 * - **Un símbolo elegido = presente; uno visible y sin elegir = ausente.** Las llaves de los símbolos no elegidos se
 *   escriben `ABSENT` (nunca `UNKNOWN`): la persona los vio y los dejó fuera, así los planes dicen «te falta el rack» y
 *   no vuelven a preguntar «¿tienes rack?».
 * - **Gimnasio asume todo lo habitual** ([seedFor]): barra, rack, banco, mancuernas, kettlebells, poleas, máquinas,
 *   Smith/Multipower, barra de dominadas, paralelas, bandas, balón, cajón y cardio. Nada exótico. Las llaves
 *   «de gimnasio» que no tienen símbolo propio (barra EZ, hexagonal y T, predicador, banco declinado y de hiperextensión,
 *   doble polea, cuerda de polea, barra baja, GHD y rueda abdominal) quedan presentes mientras haya gimnasio y su símbolo
 *   madre esté elegido. Los discos acompañan a la barra en cualquier lugar donde se ofrezca, y la barra de dominadas de
 *   un parque (espacios públicos entre los lugares) trae también su barra baja.
 * - **«Máquinas» es una sala de máquinas**, no una lista de máquinas: [EquipmentAvailability.machinesAsCategory] se escribe
 *   `true` cuando está elegido, así la categoría basta para todas las variantes de máquina aprobadas (sin el modo de
 *   configuración exacta) mientras las nueve llaves curadas siguen `PRESENT` para las recetas de autor. Sin el símbolo,
 *   las llaves quedan `ABSENT` y la bandera `false`: no hay máquinas.
 * - **Casa** no asume nada y **espacios públicos** asumen la estructura típica de un parque de calistenia.
 * - [BODYWEIGHT_ONLY][EquipmentSymbolId.BODYWEIGHT_ONLY] es exclusivo y equivale a «ningún implemento»: el motor
 *   recibe categorías vacías (solo cuerpo).
 * - Los símbolos que el subpanel curado no pinta (anillas, cajón, cardio) viajan como llaves propias
 *   ([RINGS_KEY], [BOX_KEY], [CARDIO_MACHINE_KEY]) en `supports`. El resolutor acredita las dos primeras
 *   (`SYMBOL_EQUIPMENT_KEYS`: anillas = `trx` y `rings`; cajón = `plyo_box` y, además, `support`, un apoyo elevado); la
 *   del cardio solo hace exacto el ida y vuelta: los símbolos que comparten categoría (todos los de soporte) se
 *   distinguen por su llave, así que `selectedFrom(availabilityOf(S, lugares)) == S` para toda selección S de símbolos
 *   visibles (la selección vacía vuelve como «solo peso corporal»). Los extras de arriba nunca entran en esa lectura inversa.
 * - **La bicicleta al aire libre no es un símbolo ni es de un lugar**: es de la persona
 *   ([SetupApparatusPanel.OUTDOOR_BIKE_KEY], que escribe la casilla «Tengo bicicleta» del paso de cardio). Viaja en
 *   `apparatus`, no cuenta como material para saber si se declaró algo ([selectedFrom]) y [availabilityOf] no la escribe:
 *   quien rehace la disponibilidad desde los símbolos la conserva con [SetupApparatusPanel.withBikeOf].
 * - **La cuerda de saltar salió de la cuadrícula**: ningún ejercicio del catálogo ni tipo de cardio la usa (comba:
 *   pendiente de alta en el catálogo). Su llave interna (`jump_rope`, [RETIRED_JUMP_ROPE_KEY]) sigue en el resolutor y
 *   solo la reconoce la migración de borradores antiguos para limpiarla.
 */
object EquipmentSymbols {

    const val RINGS_KEY = SymbolEquipmentKeys.RINGS
    const val BOX_KEY = SymbolEquipmentKeys.PLYO_BOX

    /** Llave del símbolo retirado «Cuerda de saltar»: nadie la escribe ya; un borrador antiguo puede traerla y se limpia al abrirlo. */
    const val RETIRED_JUMP_ROPE_KEY = SymbolEquipmentKeys.JUMP_ROPE

    /** Cardio de gimnasio o de casa (cinta, bici, elíptica…): abre las máquinas del paso CARDIO_TYPE. */
    const val CARDIO_MACHINE_KEY = "cardio_machine"

    private data class Spec(
        val categories: Set<EquipmentCategory> = emptySet(),
        /** Llaves de `apparatus` (aparatos) que acredita al estar elegido. */
        val apparatus: Set<String> = emptySet(),
        /** Llaves de `supports` (soportes) que acredita al estar elegido. */
        val supports: Set<String> = emptySet(),
        /** Llaves que solo se acreditan con gimnasio en la lista de lugares (extras habituales sin símbolo propio). */
        val gymApparatus: Set<String> = emptySet(),
        val gymSupports: Set<String> = emptySet(),
        /** Llaves de `supports` que acompañan al símbolo elegido en cualquier lugar donde se ofrezca (sin símbolo propio). */
        val companionSupports: Set<String> = emptySet(),
        /** Llaves de `supports` que solo se acreditan con espacios públicos en la lista de lugares (la barra baja del parque). */
        val publicSupports: Set<String> = emptySet(),
        /** Lugares donde el símbolo se ofrece. */
        val places: Set<TrainingPlace>,
        /** Lugares que lo traen puesto de serie. */
        val seededBy: Set<TrainingPlace>,
    )

    private val GYM = TrainingPlace.GYM
    private val HOME = TrainingPlace.HOME
    private val PUBLIC = TrainingPlace.PUBLIC
    private val ANYWHERE = setOf(GYM, HOME, PUBLIC)
    private val INDOORS = setOf(GYM, HOME)

    private val machineKeys: Set<String> = setOf(
        EquipmentKeys.LEG_PRESS, EquipmentKeys.HACK_SQUAT, EquipmentKeys.LEG_EXTENSION,
        EquipmentKeys.LEG_CURL_LYING, EquipmentKeys.LEG_CURL_SEATED,
        EquipmentKeys.CALF_STANDING, EquipmentKeys.CALF_SEATED, EquipmentKeys.CALF_DONKEY,
        EquipmentKeys.CHEST_PRESS_CONVERGING,
    )

    private val specs: Map<EquipmentSymbolId, Spec> = mapOf(
        EquipmentSymbolId.BARBELL to Spec(
            categories = setOf(EquipmentCategory.BARBELL),
            supports = emptySet(),
            // La barra EZ, la hexagonal y la T son extras de gimnasio; los discos van con la barra donde sea.
            gymSupports = setOf(EquipmentKeys.EZ_BAR, SymbolEquipmentKeys.HEX_BAR, SymbolEquipmentKeys.T_BAR),
            companionSupports = setOf(SymbolEquipmentKeys.PLATE),
            places = INDOORS, seededBy = setOf(GYM),
        ),
        EquipmentSymbolId.RACK to Spec(
            categories = setOf(EquipmentCategory.SUPPORT),
            supports = setOf(EquipmentKeys.SQUAT_RACK),
            gymSupports = setOf(EquipmentKeys.LOW_BAR_SUPPORT),
            places = INDOORS, seededBy = setOf(GYM),
        ),
        EquipmentSymbolId.BENCH to Spec(
            categories = setOf(EquipmentCategory.SUPPORT),
            // Un banco «a secas» se asume regulable: acredita plano e inclinado.
            supports = setOf(EquipmentKeys.BENCH_FLAT, EquipmentKeys.BENCH_ADJUSTABLE),
            // El predicador, el banco declinado y el de hiperextensión son otros bancos, propios de un gimnasio: su
            // categoría en el motor es SUPPORT, y solo cuentan si ella está confirmada.
            gymSupports = setOf(
                EquipmentKeys.PREACHER_BENCH, SymbolEquipmentKeys.DECLINE_BENCH, SymbolEquipmentKeys.HYPEREXTENSION_BENCH,
            ),
            places = ANYWHERE, seededBy = setOf(GYM),
        ),
        EquipmentSymbolId.DUMBBELLS to Spec(
            categories = setOf(EquipmentCategory.DUMBBELLS),
            places = ANYWHERE, seededBy = setOf(GYM),
        ),
        EquipmentSymbolId.KETTLEBELL to Spec(
            categories = setOf(EquipmentCategory.KETTLEBELL),
            places = ANYWHERE, seededBy = setOf(GYM),
        ),
        EquipmentSymbolId.CABLE to Spec(
            categories = setOf(EquipmentCategory.CABLE),
            apparatus = setOf(EquipmentKeys.CABLE_HIGH_LOW),
            gymApparatus = setOf(EquipmentKeys.DUAL_CABLE, EquipmentKeys.ROPE_ATTACHMENT),
            places = INDOORS, seededBy = setOf(GYM),
        ),
        EquipmentSymbolId.MACHINES to Spec(
            categories = setOf(EquipmentCategory.MACHINES),
            apparatus = machineKeys,
            // El GHD y la rueda abdominal son extras habituales del gimnasio, sin símbolo propio.
            gymApparatus = setOf(SymbolEquipmentKeys.GHD, SymbolEquipmentKeys.AB_WHEEL),
            places = INDOORS, seededBy = setOf(GYM),
        ),
        EquipmentSymbolId.SMITH to Spec(
            categories = setOf(EquipmentCategory.SMITH_MACHINE),
            places = INDOORS, seededBy = setOf(GYM),
        ),
        EquipmentSymbolId.PULL_UP_BAR to Spec(
            categories = setOf(EquipmentCategory.PULL_UP_BAR),
            supports = setOf(EquipmentKeys.PULLUP_BAR),
            // La barra de dominadas de un parque casi siempre trae una barra baja (remo invertido, rack chin).
            publicSupports = setOf(EquipmentKeys.LOW_BAR_SUPPORT),
            places = ANYWHERE, seededBy = setOf(GYM, PUBLIC),
        ),
        EquipmentSymbolId.PARALLEL_BARS to Spec(
            categories = setOf(EquipmentCategory.SUPPORT),
            supports = setOf(EquipmentKeys.DIP_BARS),
            places = ANYWHERE, seededBy = setOf(GYM, PUBLIC),
        ),
        EquipmentSymbolId.RINGS to Spec(
            categories = setOf(EquipmentCategory.SUPPORT),
            supports = setOf(RINGS_KEY),
            places = ANYWHERE, seededBy = emptySet(),
        ),
        EquipmentSymbolId.BANDS to Spec(
            categories = setOf(EquipmentCategory.BAND),
            places = ANYWHERE, seededBy = setOf(GYM),
        ),
        EquipmentSymbolId.BALL to Spec(
            categories = setOf(EquipmentCategory.BALL),
            places = INDOORS, seededBy = setOf(GYM),
        ),
        EquipmentSymbolId.BOX to Spec(
            categories = setOf(EquipmentCategory.SUPPORT),
            supports = setOf(BOX_KEY),
            places = ANYWHERE, seededBy = setOf(GYM),
        ),
        EquipmentSymbolId.CARDIO to Spec(
            categories = setOf(EquipmentCategory.CARDIO),
            supports = setOf(CARDIO_MACHINE_KEY),
            places = INDOORS, seededBy = setOf(GYM),
        ),
    )

    /** Los símbolos que se pueden elegir (todos menos el exclusivo), en el orden del contrato. */
    val selectable: List<EquipmentSymbolId> = EquipmentSymbolId.entries.filter { it != EquipmentSymbolId.BODYWEIGHT_ONLY }

    /**
     * Símbolos que se muestran para los [places] elegidos: la unión de lo que ofrece cada lugar, en el orden del
     * contrato, y al final «solo peso corporal». Sin lugares no se ofrece nada que elegir.
     */
    fun symbolsFor(places: Set<TrainingPlace>): List<EquipmentSymbolId> {
        if (places.isEmpty()) return emptyList()
        return selectable.filter { symbol -> specs.getValue(symbol).places.any { it in places } } +
            EquipmentSymbolId.BODYWEIGHT_ONLY
    }

    /** Lo que se da por disponible al elegir [places]: gimnasio todo lo habitual, parque su estructura, casa nada. */
    fun seedFor(places: Set<TrainingPlace>): Set<EquipmentSymbolId> =
        selectable.filterTo(linkedSetOf()) { symbol -> specs.getValue(symbol).seededBy.any { it in places } }

    /**
     * Selección tras cambiar los lugares de [before] a [after]: se conserva lo ya elegido que sigue ofreciéndose y se
     * añade la semilla de los lugares NUEVOS. Quitar un lugar retira lo que solo ese lugar ofrecía; nunca borra lo
     * que la persona marcó y sigue siendo visible.
     */
    fun reseed(
        before: Set<TrainingPlace>,
        after: Set<TrainingPlace>,
        current: Set<EquipmentSymbolId>,
    ): Set<EquipmentSymbolId> {
        val visible = symbolsFor(after).toSet()
        val kept = current.filterTo(linkedSetOf()) { it in visible }
        return if (EquipmentSymbolId.BODYWEIGHT_ONLY in kept && (after - before).isNotEmpty()) {
            // Añadir un lugar nuevo reabre el material: «solo peso corporal» deja de ser lo que se declaró.
            (kept - EquipmentSymbolId.BODYWEIGHT_ONLY) + seedFor(after - before)
        } else if (EquipmentSymbolId.BODYWEIGHT_ONLY in kept) {
            kept
        } else {
            kept + seedFor(after - before)
        }
    }

    /**
     * Alterna [symbol] respetando la exclusividad: elegir «solo peso corporal» vacía el resto y elegir cualquier
     * implemento retira «solo peso corporal».
     */
    fun toggle(selected: Set<EquipmentSymbolId>, symbol: EquipmentSymbolId): Set<EquipmentSymbolId> = when {
        symbol == EquipmentSymbolId.BODYWEIGHT_ONLY ->
            if (symbol in selected) emptySet() else setOf(EquipmentSymbolId.BODYWEIGHT_ONLY)
        symbol in selected -> selected - symbol
        else -> (selected - EquipmentSymbolId.BODYWEIGHT_ONLY) + symbol
    }

    /**
     * Disponibilidad del motor para los símbolos [selected] con los [places] declarados. Solo cuerpo (selección vacía o
     * «solo peso corporal») = categorías vacías CONFIRMADAS, que el motor lee como peso corporal.
     *
     * Los extras sin símbolo propio dependen de los lugares: los de gimnasio solo con `GYM` entre ellos, la barra baja de
     * un parque solo con `PUBLIC`; los discos van con la barra donde sea. Ninguno entra en [selectedFrom], así que el ida
     * y vuelta sigue siendo exacto. Con «Máquinas» elegido se escribe también [EquipmentAvailability.machinesAsCategory]
     * (una sala de máquinas: sin modo de configuración exacta); tampoco entra en la lectura inversa.
     */
    fun availabilityOf(selected: Set<EquipmentSymbolId>, places: Set<TrainingPlace>): EquipmentAvailability {
        val chosen = selected.filterTo(linkedSetOf()) { it != EquipmentSymbolId.BODYWEIGHT_ONLY && it in specs }
        if (chosen.isEmpty()) return EquipmentAvailability()
        val withGym = GYM in places
        val withPublic = PUBLIC in places

        val categories = chosen.flatMapTo(linkedSetOf()) { specs.getValue(it).categories }
        val apparatusPresent = chosen.flatMapTo(linkedSetOf()) { symbol ->
            val spec = specs.getValue(symbol)
            spec.apparatus + if (withGym) spec.gymApparatus else emptySet()
        }
        val supportsPresent = chosen.flatMapTo(linkedSetOf()) { symbol ->
            val spec = specs.getValue(symbol)
            spec.supports + spec.companionSupports +
                (if (withGym) spec.gymSupports else emptySet()) +
                (if (withPublic) spec.publicSupports else emptySet())
        }
        // Todo lo que se pudo ver y no se eligió queda explícitamente ausente.
        val visible = symbolsFor(places).filter { it in specs }.toSet().ifEmpty { selectable.toSet() }
        val apparatusAbsent = visible.flatMapTo(linkedSetOf()) { symbol ->
            val spec = specs.getValue(symbol)
            spec.apparatus + spec.gymApparatus
        } - apparatusPresent
        val supportsAbsent = visible.flatMapTo(linkedSetOf()) { symbol ->
            val spec = specs.getValue(symbol)
            spec.supports + spec.companionSupports + spec.gymSupports + spec.publicSupports
        } - supportsPresent

        return EquipmentAvailability(
            categories = categories,
            apparatus = apparatusAbsent.associateWith { ApparatusPresence.ABSENT } +
                apparatusPresent.associateWith { ApparatusPresence.PRESENT },
            supports = supportsAbsent.associateWith { ApparatusPresence.ABSENT } +
                supportsPresent.associateWith { ApparatusPresence.PRESENT },
            machinesAsCategory = EquipmentSymbolId.MACHINES in chosen,
        )
    }

    /**
     * Para cada categoría, el único símbolo que la reclama (null si la comparten varios: soportes y cardio). Una categoría
     * que solo tiene un dueño basta para leer ese símbolo aunque sus llaves no consten.
     */
    private val categoryOwner: Map<EquipmentCategory, EquipmentSymbolId?> = specs.entries
        .flatMap { (symbol, spec) -> spec.categories.map { category -> category to symbol } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, owners) -> owners.singleOrNull() }

    /**
     * Símbolos que representa una disponibilidad (lectura inversa, para pintar la selección). Un símbolo cuenta como
     * elegido cuando todas sus categorías están confirmadas y:
     * - no tiene llaves propias (barra, mancuernas, kettlebell, Smith, bandas, balón): basta su categoría;
     * - alguna de sus llaves está `PRESENT` (el rack con `squat_rack`, el banco con `bench_flat`…): la presencia de una
     *   basta, así una confirmación parcial del asesor («sí, tengo rack y banco plano») ya se lee como rack y banco;
     * - todas sus llaves constan como desconocidas (una disponibilidad antigua de solo categorías) y la categoría es
     *   solo suya (máquinas, poleas, barra de dominadas): la categoría declarada manda. Si la comparte con otros
     *   símbolos (soportes, cardio) no se adivina cuál es: queda sin elegir hasta que la persona lo marque.
     *
     * El ida y vuelta de [availabilityOf] es exacto: lo elegido tiene todas sus llaves `PRESENT` y lo visible sin elegir
     * las tiene `ABSENT`. `null` = nada declarado todavía.
     */
    fun selectedFrom(availability: EquipmentAvailability?): Set<EquipmentSymbolId> {
        if (availability == null) return emptySet()
        // La bicicleta de la persona no es material de ningún símbolo: no cuenta para saber si se declaró algo.
        if (availability.categories.isEmpty()) {
            val material = SetupApparatusPanel.withoutBike(availability)
            return if (material.hasExplicitPresence || material.apparatus.isNotEmpty() || material.supports.isNotEmpty()) {
                emptySet()
            } else {
                setOf(EquipmentSymbolId.BODYWEIGHT_ONLY)
            }
        }
        return selectable.filterTo(linkedSetOf()) { symbol ->
            val spec = specs.getValue(symbol)
            if (!spec.categories.all { it in availability.categories }) return@filterTo false
            val presences = (spec.apparatus + spec.supports).map { key -> availability.presenceOf(key) }
            when {
                presences.isEmpty() -> true
                presences.any { it == ApparatusPresence.PRESENT } -> true
                presences.all { it == ApparatusPresence.UNKNOWN } -> spec.categories.all { categoryOwner[it] == symbol }
                else -> false
            }
        }
    }

    /** ¿La disponibilidad solo permite trabajar con el cuerpo? */
    fun isBodyweightOnly(availability: EquipmentAvailability?): Boolean =
        availability != null && availability.categories.isEmpty()
}
