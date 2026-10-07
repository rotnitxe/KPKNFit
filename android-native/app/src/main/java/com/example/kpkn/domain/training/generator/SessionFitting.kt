package com.example.kpkn.domain.training.generator

import kotlin.math.abs
import kotlin.math.floor

/** Un ejercicio de la sesión con su prescripción base (antes del ajuste a minutos). */
internal class DraftItem(
    val spec: SlotSpec,
    val candidate: Candidate,
    val rx: Rx,
    /** Orden de inclusión (0 = el más importante). */
    val priority: Int,
    val baseSets: Int,
) {
    val isTimed: Boolean get() = rx.seconds != null
    val repMaxForTime: Int get() = if (isTimed) 0 else rx.repMax
}

/**
 * Unidad de ajuste: un ejercicio suelto o dos en superserie (comparten número de series; el descanso de la pareja es el
 * de después de cada ronda).
 */
internal class Bundle(val items: List<DraftItem>) {
    var included: Boolean = false
    var sets: Int = 0

    /** Descanso del ejercicio (suelto) o descanso tras cada ronda (pareja). */
    var rest: Int = 0

    val isPair: Boolean get() = items.size == 2
    val priority: Int = items.first().priority

    /**
     * ¿Algún hueco del bloque es prioritario (la persona pidió mejorar ese músculo)? Al recortar por tiempo es lo último que se
     * toca y al repartir tiempo sobrante, lo primero que recibe (ver [MinuteFitter.trimOrder] y [MinuteFitter.growOrder]).
     */
    val isPriority: Boolean = items.any { it.spec.boosted }
    val minSets: Int = items.maxOf { it.rx.minSets }
    val maxSets: Int = items.minOf { it.rx.maxSets }.coerceAtLeast(minSets)
    val baseSets: Int = items.maxOf { it.baseSets }.coerceIn(minSets, maxSets)

    /**
     * Suelo de series de un hueco prioritario: +25 % sobre las series que el bloque consigue sin él (redondeado hacia
     * arriba, hasta una serie por encima del máximo habitual). Se fija tras un primer ajuste (ver `fitRepairingAndFilling`):
     * con la sesión llena, un +25 % sobre las series BASE no cambiaría nada porque el ajuste ya las sube hasta el máximo.
     */
    var boostFloor: Int = 0

    /**
     * ¿Admite descansos más largos? Solo lo pesado y los principales con carga externa: descansar más entre flexiones,
     * aislamientos, core o series de una superserie no aporta nada; ese tiempo es mejor para otro ejercicio, movilidad o cardio.
     */
    val restExtendable: Boolean
        get() = !isPair && items.any { item -> item.rx.heavy || (item.spec.role == ItemRole.MAIN && !item.candidate.fromLadder) }

    /** Series con las que el bloque intenta entrar: las base o, si es prioritario, su suelo. */
    val startSets: Int get() = maxOf(baseSets, boostFloor).coerceAtMost(maxSets)

    val between: Int = if (isPair) PAIR_BETWEEN else 0
    val minRest: Int = if (isPair) {
        if (items.any { !it.candidate.kind.isLight() }) Prescriber.MIN_REST_COMPOUND else 60
    } else {
        minRestOf(items.first())
    }
    val maxRest: Int = if (isPair) maxOf(minRest, items.maxOf { it.rx.maxRest }.coerceAtMost(150)) else items.first().rx.maxRest.coerceAtLeast(minRest)
    val baseRest: Int = if (isPair) {
        if (items.any { !it.candidate.kind.isLight() }) 90 else 75
    } else {
        items.first().rx.rest.coerceIn(minRest, maxRest)
    }

    /** Un compuesto pesado (o principal) admite series y descansos extra con más tiempo. */
    val isMajor: Boolean get() = items.any { it.spec.role == ItemRole.MAIN || it.spec.role == ItemRole.SECONDARY }

    private var cachedSets = -1
    private var cachedRest = -1
    private var cachedSeconds = 0

    fun seconds(): Int {
        if (cachedSets == sets && cachedRest == rest) return cachedSeconds
        val value = computeSeconds()
        cachedSets = sets
        cachedRest = rest
        cachedSeconds = value
        return value
    }

    private fun computeSeconds(): Int {
        val first = items.first()
        return if (!isPair) {
            TimeModel.exerciseSeconds(sets, first.repMaxForTime, first.rx.seconds, rest)
        } else {
            val second = items[1]
            TimeModel.pairSeconds(
                sets, first.repMaxForTime, first.rx.seconds,
                sets, second.repMaxForTime, second.rx.seconds,
                between, rest,
            )
        }
    }

    companion object {
        const val PAIR_BETWEEN = 30

        private fun ExKind.isLight(): Boolean = this == ExKind.ISOLATION || this == ExKind.CORE_DYNAMIC || this == ExKind.TIMED

        /** Descanso mínimo por tipo de ejercicio (pesado 150 s, compuesto 90 s, aislamiento 45 s). */
        fun minRestOf(item: DraftItem): Int = when {
            item.rx.heavy -> Prescriber.MIN_REST_HEAVY
            item.candidate.kind.isLight() -> Prescriber.MIN_REST_ISOLATION
            else -> item.rx.minRest.coerceAtLeast(Prescriber.MIN_REST_COMPOUND)
        }
    }
}

/** Margen de series por músculo: cuántas series cabe sumar a un bloque sin pasar el techo semanal ni el de la sesión. */
internal class VolumeRoom(private val ctx: GenContext, private val caps: SessionCaps, private val sessionIndex: Int) {

    private val capMemo = HashMap<Pair<String, Boolean>, Int>()

    /** Tope de la sesión para el músculo (el presupuesto semanal no cambia mientras se arma la sesión). */
    private fun cap(muscle: String, soft: Boolean): Int =
        capMemo.getOrPut(muscle to soft) { caps.cap(sessionIndex, muscle, soft, ctx.ledger) }

    /**
     * ¿El bloque se mide contra el objetivo blando (MAV)? Sí cuando se piden series AÑADIDAS ([soft]) o cuando es un hueco extra
     * (prioridad o relleno), salvo el relleno «duro» que completa el mínimo de ejercicios ([SlotSpec.hard]).
     */
    private fun softLimited(bundle: Bundle, soft: Boolean): Boolean = soft || bundle.items.any { it.spec.extra && !it.spec.hard }

    /** Series que el músculo admite en esta sesión para [bundle]: el menor entre lo que queda de la semana y el tope de la sesión. */
    private fun limit(bundle: Bundle, muscle: String, soft: Boolean): Double {
        val limitSoft = softLimited(bundle, soft)
        val remaining = if (limitSoft) ctx.ledger.remainingTarget(muscle) else ctx.ledger.remaining(muscle)
        return minOf(remaining, cap(muscle, limitSoft).toDouble())
    }

    /**
     * Reparto de series por VOLUMEN antes de ajustar a minutos. Cuando un músculo (el glúteo, que suma en sentadilla,
     * bisagra, zancada y puente) no admite las series base de todos los ejercicios que lo trabajan, repartir por orden de
     * prioridad dejaría al primero con todas y a los últimos con cero: un día de pierna con una sentadilla de 4 series y
     * ninguna zancada ni puente. Aquí:
     * 1. cada bloque parte de sus series mínimas y se descartan, de menor a mayor prioridad, los que pasan el límite de algún
     *    músculo (así los de más prioridad conservan su sitio);
     * 2. de los que quedan solo caben los que entran en el tiempo con sus series mínimas (los demás no reservan volumen);
     * 3. lo que sobra de volumen se reparte por prioridad hasta las series con las que cada bloque quiere entrar.
     * Devuelve las series permitidas por bloque (0 = no entra).
     */
    fun allocate(bundles: List<Bundle>, timeBudgetSeconds: Int, minItems: Int): Map<Bundle, Int> {
        val ordered = bundles.sortedBy { it.priority }
        val perSet = ordered.associateWith { perSet(it) }
        val sets = HashMap<Bundle, Int>()
        ordered.forEach { sets[it] = it.minSets }
        val active = ordered.toMutableList()
        fun used(): HashMap<String, Double> {
            val total = HashMap<String, Double>()
            active.forEach { bundle ->
                perSet.getValue(bundle).forEach { (muscle, c) -> total[muscle] = (total[muscle] ?: 0.0) + (sets[bundle] ?: 0) * c }
            }
            return total
        }
        // 1) Volumen con las series mínimas.
        while (true) {
            val total = used()
            val violator = active.lastOrNull { bundle ->
                perSet.getValue(bundle).keys.any { muscle -> (total[muscle] ?: 0.0) > limit(bundle, muscle, soft = false) + 1e-9 }
            } ?: break
            active.remove(violator)
            sets.remove(violator)
        }
        // 2) Tiempo con las series mínimas.
        var elapsed = 0
        var items = 0
        val kept = ArrayList<Bundle>()
        for (bundle in active) {
            val savedSets = bundle.sets
            val savedRest = bundle.rest
            bundle.sets = bundle.minSets
            bundle.rest = bundle.minRest
            val seconds = bundle.seconds()
            bundle.sets = savedSets
            bundle.rest = savedRest
            if (elapsed + seconds <= timeBudgetSeconds || items < minItems) {
                kept += bundle
                elapsed += seconds
                items += bundle.items.size
            } else {
                sets.remove(bundle)
            }
        }
        active.retainAll(kept.toSet())
        // 3) Lo que sobra, por prioridad, hasta las series con las que el bloque quiere entrar.
        val total = used()
        for (bundle in active) {
            val goal = bundle.startSets
            while ((sets[bundle] ?: 0) < goal) {
                val fits = perSet.getValue(bundle).all { (muscle, c) -> (total[muscle] ?: 0.0) + c <= limit(bundle, muscle, soft = false) + 1e-9 }
                if (!fits) break
                sets[bundle] = (sets[bundle] ?: 0) + 1
                perSet.getValue(bundle).forEach { (muscle, c) -> total[muscle] = (total[muscle] ?: 0.0) + c }
            }
        }
        return bundles.associateWith { sets[it] ?: 0 }
    }

    private fun perSet(bundle: Bundle): Map<String, Double> {
        val total = HashMap<String, Double>()
        bundle.items.forEach { item ->
            item.candidate.entry.contributions.forEach { (muscle, c) ->
                if (c.direct > 0.0) total[muscle] = (total[muscle] ?: 0.0) + c.direct
            }
        }
        return total
    }

    /**
     * Series máximas del bloque dados los demás bloques incluidos (sin contar el propio). Con [soft] el tope es el objetivo
     * blando (MAV) y sirve para AÑADIR series; sin él, el techo duro (MRV) y sirve para decidir si el bloque entra.
     */
    fun maxSets(bundle: Bundle, bundles: List<Bundle>, soft: Boolean = false): Int {
        // Lo que se AÑADE al reparto base (un hueco extra que pide una prioridad o un relleno) solo llega al objetivo blando; el
        // techo duro es para los huecos del plan, cuyos compuestos cuentan como series directas de varios músculos a la vez.
        val limitSoft = softLimited(bundle, soft)
        val own = perSet(bundle)
        if (own.isEmpty()) return bundle.maxSets
        val used = HashMap<String, Double>()
        bundles.forEach { other ->
            if (other === bundle || !other.included) return@forEach
            other.items.forEach { item ->
                item.candidate.entry.contributions.forEach { (muscle, c) ->
                    if (c.direct > 0.0) used[muscle] = (used[muscle] ?: 0.0) + other.sets * c.direct
                }
            }
        }
        var allowed = bundle.maxSets
        own.forEach { (muscle, perSet) ->
            val room = limit(bundle, muscle, limitSoft) - (used[muscle] ?: 0.0)
            val sets = floor(room / perSet + 1e-9).toInt().coerceAtLeast(0)
            allowed = minOf(allowed, sets)
        }
        return allowed
    }
}

internal class FitEnv(
    /** Segundos que la sesión ya lleva fuera de la fuerza (cardio con su preparación, movilidad, aproximaciones). */
    val fixedSeconds: Int,
    val targetSec: Int,
    val loSec: Int,
    val hiSec: Int,
    val minItems: Int,
    val room: VolumeRoom,
)

/**
 * Ajuste a minutos de los ejercicios de fuerza de una sesión. Todas las decisiones miden con [TimeModel] (el estimador
 * común) y respetan los límites de series y descansos de cada ejercicio y los techos de volumen.
 *
 * Orden (el del brief):
 * 0. Reparto de series por volumen (`VolumeRoom.allocate`): ningún ejercicio que cabe en el tiempo se queda sin sus series
 *    mínimas porque otro, de más prioridad, se coma el presupuesto de un músculo compartido (el glúteo).
 * 1. Incluye bloques por prioridad con sus series base (o las que el reparto de volumen les deja) mientras quepan en el
 *    tiempo; el último que no cabe entero entra con menos series si así cabe.
 * 2. Si aun así se pasa de la ventana: descansos al mínimo por tipo, menos series (de los últimos a los primeros) y, por
 *    último, menos ejercicios (nunca por debajo del mínimo de ejercicios).
 * 3. Si sobra tiempo: series a los compuestos pesados, luego a los accesorios (hasta el objetivo blando de volumen); después
 *    descansos más largos, solo en lo pesado y en los principales con carga (`Bundle.restExtendable`).
 *
 * Lo prioritario (`Bundle.isPriority`) se protege del tiempo: un hueco prioritario que no llega a sus series base las consigue
 * quitando series a lo NO prioritario (de atrás hacia delante y sin bajar de sus series mínimas); al recortar solo se toca lo
 * prioritario si no queda otra cosa y con tiempo sobrante lo prioritario recibe las series primero. Sin músculos prioritarios
 * nada de esto cambia el resultado.
 */
internal object MinuteFitter {

    private const val REST_STEP = 15

    private fun included(bundles: List<Bundle>): List<Bundle> = bundles.filter { it.included }.sortedBy { it.priority }

    fun itemCount(bundles: List<Bundle>): Int = bundles.filter { it.included }.sumOf { it.items.size }

    fun total(bundles: List<Bundle>, env: FitEnv): Int {
        val inc = bundles.filter { it.included }
        var t = env.fixedSeconds
        if (inc.isNotEmpty()) t += TimeModel.base
        inc.forEach { t += it.seconds() }
        return t
    }

    /** ¿Aceptar un cambio que lleva el total de [before] a [after]? Cabe en el objetivo o se acerca más a él sin salir de la ventana. */
    private fun accept(before: Int, after: Int, env: FitEnv): Boolean =
        after <= env.targetSec || (after <= env.hiSec && abs(after - env.targetSec) < abs(before - env.targetSec))

    fun fit(bundles: List<Bundle>, env: FitEnv) {
        bundles.forEach { it.included = false; it.sets = 0; it.rest = it.baseRest }
        val volume = env.room.allocate(bundles, env.targetSec - env.fixedSeconds - TimeModel.base, env.minItems)

        // 1) Inclusión por prioridad con las series base (o las que el reparto de volumen deja a cada bloque).
        for (bundle in bundles.sortedBy { it.priority }) {
            val allowed = minOf(bundle.maxSets, volume[bundle] ?: 0)
            if (allowed < bundle.minSets) continue
            bundle.rest = bundle.baseRest
            var placed = false
            for (sets in minOf(bundle.startSets, allowed) downTo bundle.minSets) {
                bundle.sets = sets
                bundle.included = true
                if (total(bundles, env) <= env.targetSec) {
                    placed = true
                    break
                }
            }
            if (!placed) {
                bundle.included = false
                if (itemCount(bundles) < env.minItems) {
                    bundle.sets = bundle.minSets
                    bundle.included = true
                }
            }
        }

        // 1b) Lo prioritario no se queda sin sus series base por el tiempo: se las quita a lo que no lo es.
        protectPriority(bundles, env, volume)

        // 2) Compresión si se pasa de la ventana.
        if (total(bundles, env) > env.hiSec) compress(bundles, env)

        // 3) Expansión hacia el objetivo.
        expand(bundles, env)
    }

    /** Orden en que se recorta: de atrás hacia delante y, al final, lo prioritario (solo se toca si no queda otra cosa). */
    fun trimOrder(bundles: List<Bundle>): List<Bundle> {
        val backwards = included(bundles).asReversed()
        return backwards.filter { !it.isPriority } + backwards.filter { it.isPriority }
    }

    /** Orden en que se reparte el tiempo sobrante: primero lo prioritario y luego el resto, cada grupo por su orden de importancia. */
    fun growOrder(bundles: List<Bundle>): List<Bundle> {
        val forwards = included(bundles)
        return forwards.filter { it.isPriority } + forwards.filter { !it.isPriority }
    }

    /**
     * Un hueco prioritario que el tiempo dejó por debajo de sus series base (o fuera) las recupera quitándoselas a lo NO
     * prioritario: de atrás hacia delante y sin bajar de las series mínimas de nadie. Si ni así cabe, se queda como estaba
     * (lo avisa `SessionAssembler`). No toca nada cuando no hay huecos prioritarios.
     */
    private fun protectPriority(bundles: List<Bundle>, env: FitEnv, volume: Map<Bundle, Int>) {
        for (target in bundles.filter { it.isPriority }.sortedBy { it.priority }) {
            val goal = minOf(target.baseSets, target.maxSets, volume[target] ?: 0)
            if (goal < target.minSets) continue
            if (!target.included && !claim(target, target.minSets, bundles, env)) continue
            while (target.sets < goal) {
                if (!claim(target, target.sets + 1, bundles, env)) break
            }
        }
    }

    /** Lleva [target] a [sets] series (y lo incluye si hace falta) liberando tiempo de lo no prioritario; si no cabe, lo deja como estaba. */
    private fun claim(target: Bundle, sets: Int, bundles: List<Bundle>, env: FitEnv): Boolean {
        val wasIncluded = target.included
        val before = target.sets
        target.included = true
        target.sets = sets
        val taken = ArrayList<Bundle>()
        while (total(bundles, env) > env.targetSec) {
            val donor = included(bundles).asReversed().firstOrNull { !it.isPriority && it.sets > it.minSets }
            if (donor == null) {
                taken.forEach { it.sets++ }
                target.sets = before
                target.included = wasIncluded
                return false
            }
            donor.sets--
            taken += donor
        }
        return true
    }

    private fun compress(bundles: List<Bundle>, env: FitEnv) {
        included(bundles).forEach { it.rest = it.minRest }
        if (total(bundles, env) <= env.hiSec) return
        for (bundle in trimOrder(bundles)) {
            while (bundle.sets > bundle.minSets && total(bundles, env) > env.hiSec) bundle.sets--
            if (total(bundles, env) <= env.hiSec) return
        }
        for (bundle in trimOrder(bundles)) {
            if (total(bundles, env) <= env.hiSec) return
            if (itemCount(bundles) - bundle.items.size >= env.minItems) bundle.included = false
        }
    }

    private fun expand(bundles: List<Bundle>, env: FitEnv) {
        var progressed = true
        var guard = 0
        while (progressed && guard++ < 60) {
            progressed = false
            for (bundle in growOrder(bundles)) {
                val allowed = minOf(bundle.maxSets, env.room.maxSets(bundle, bundles, soft = true))
                if (bundle.sets >= allowed) continue
                val before = total(bundles, env)
                bundle.sets++
                if (accept(before, total(bundles, env), env)) progressed = true else bundle.sets--
            }
        }
        // Descansos más largos solo en lo pesado y en los principales con carga externa (ver `Bundle.restExtendable`).
        val restExtendable = included(bundles).filter { it.restExtendable }
        for (bundle in restExtendable.sortedByDescending { if (it.items.any { item -> item.rx.heavy }) 1 else 0 }) {
            while (bundle.rest + REST_STEP <= bundle.maxRest) {
                val before = total(bundles, env)
                bundle.rest += REST_STEP
                if (!accept(before, total(bundles, env), env)) {
                    bundle.rest -= REST_STEP
                    break
                }
            }
        }
        // Un último reparto de series si el descanso liberó margen.
        var again = true
        guard = 0
        while (again && guard++ < 20) {
            again = false
            for (bundle in growOrder(bundles)) {
                val allowed = minOf(bundle.maxSets, env.room.maxSets(bundle, bundles, soft = true))
                if (bundle.sets >= allowed) continue
                val before = total(bundles, env)
                bundle.sets++
                if (accept(before, total(bundles, env), env)) again = true else bundle.sets--
            }
        }
    }
}
