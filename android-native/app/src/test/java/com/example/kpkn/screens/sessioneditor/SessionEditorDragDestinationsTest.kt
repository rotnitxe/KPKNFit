package com.example.kpkn.screens.sessioneditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ED-A · A3: el arrastre llega a los cuatro destinos (entre grupos, fuera de un grupo, dentro de un grupo y a una superserie ya formada),
 * cada uno con su plan («qué pasaría al soltar»). La geometría es la de la lista del editor a 100 dp por fila; el movimiento lo aplica el
 * mismo motor que usa la pantalla, así que también se mide que no se pierde ningún ejercicio.
 */
class SessionEditorDragDestinationsTest {

    private fun ex(id: String, group: String? = null) = Exercise(
        id = id,
        name = "Ejercicio $id",
        exerciseDbId = id,
        supersetGroupRef = group,
        supersetId = group,
    )

    /**
     * Sueltos: [loose]. Principal: sentadilla y peso muerto. Accesorios: la superserie «ss» (zancada y curl) y, aparte, un press.
     * Alturas: sueltos 0–100, cabecera de Principal 220–270, sus filas 270–470 y su pie 470–520; cabecera de Accesorios 540–590, la
     * superserie 590–790 y el press 790–890, con su pie 890–940.
     */
    private fun session(loose: List<Exercise> = listOf(ex("loose"))): Session = Session(
        id = "s",
        name = "Pierna A",
        exercises = loose,
        parts = listOf(
            SessionPart(id = "p1", name = "Principal", exercises = listOf(ex("sent"), ex("peso"))),
            SessionPart(
                id = "p2",
                name = "Accesorios",
                exercises = listOf(ex("zan", "ss"), ex("curl", "ss"), ex("press")),
            ),
        ),
        supersetGroups = listOf(SupersetGroup(id = "ss", exerciseOrder = listOf("zan", "curl"))),
    )

    private fun controllerFor(session: Session): SessionEditorDragController {
        val c = SessionEditorDragController()
        if (session.exercises.isNotEmpty()) {
            c.looseContentBounds = Rect(0f, 0f, 300f, 100f)
            c.exerciseBounds["__loose__|loose"] = Rect(0f, 0f, 300f, 100f)
        }
        c.partBounds["p1"] = Rect(0f, 220f, 300f, 270f)
        c.exerciseBounds["p1|sent"] = Rect(0f, 270f, 300f, 370f)
        c.exerciseBounds["p1|peso"] = Rect(0f, 370f, 300f, 470f)
        c.registerPartFooterBounds("p1", Rect(0f, 470f, 300f, 520f))
        c.partBounds["p2"] = Rect(0f, 540f, 300f, 590f)
        c.exerciseBounds["p2|zan"] = Rect(0f, 590f, 300f, 690f)
        c.exerciseBounds["p2|curl"] = Rect(0f, 690f, 300f, 790f)
        c.exerciseBounds["p2|press"] = Rect(0f, 790f, 300f, 890f)
        c.registerPartFooterBounds("p2", Rect(0f, 890f, 300f, 940f))
        return c
    }

    /** Resultado de arrastrar [exerciseId] hasta la altura [pointerY]: la sesión movida y el plan que se enseñaba al soltar. */
    private data class Dropped(val session: Session, val plan: SessionEditorDropPlan, val targetGroupId: String?)

    private fun drag(
        session: Session,
        sourcePartId: String,
        exerciseId: String,
        pointerY: Float,
        scope: ExerciseDragScope = ExerciseDragScope.INDIVIDUAL,
        controller: SessionEditorDragController = controllerFor(session),
    ): Dropped {
        val start = controller.exerciseBounds["$sourcePartId|$exerciseId"]!!
        controller.beginExerciseDrag(sourcePartId, exerciseId, Offset(0f, 50f), session = session, dragScope = scope)
        controller.updateExerciseDrag(Offset(0f, pointerY - (start.top + 50f)), session)
        val plan = planExerciseDrop(
            session = session,
            sourcePartId = controller.draggingExercisePartId,
            exerciseId = controller.draggingExerciseId,
            scope = controller.draggingExerciseScope,
            targetPartId = controller.exerciseDropTargetPartId,
            targetIndex = controller.exerciseDropTargetIndex,
            targetGroupId = controller.exerciseDropTargetGroupId,
            outOfRange = controller.exerciseDropOutOfRange,
        )
        var result = session
        var groupAtDrop: String? = null
        controller.endExerciseDrag(session) { from, id, to, index ->
            groupAtDrop = controller.exerciseDropTargetGroupId
            result = SessionEditorMoveEngine.move(
                session,
                SessionEditorMoveRequest(
                    sourcePartId = from,
                    exerciseId = id,
                    targetPartId = to,
                    targetIndex = index,
                    moveAsGroup = controller.draggingExerciseScope == ExerciseDragScope.BLOCK,
                    targetGroupId = groupAtDrop,
                ),
            )
        }
        return Dropped(result, plan, groupAtDrop)
    }

    private fun ids(session: Session) =
        (session.exercises + session.parts.flatMap { it.exercises }).map { it.id }.sorted()

    private fun Session.part(id: String) = parts.first { it.id == id }.exercises.map { it.id }

    // ───────────────────────────── 1 · entre grupos ─────────────────────────────

    @Test
    fun entreGrupos_unEjercicioPasaDeUnGrupoAOtroSinUnirseALaSuperserie() {
        val before = session()
        // Sobre la cabecera de Accesorios, fuera de la superserie: va a ese grupo, antes de ella.
        val dropped = drag(before, "p1", "peso", pointerY = 560f)

        assertEquals(SessionEditorDropKind.BETWEEN_GROUPS, dropped.plan.kind)
        assertEquals("p2", dropped.plan.targetPartId)
        assertNull(dropped.targetGroupId)
        assertTrue(dropped.plan.hint, dropped.plan.hint.contains("«Principal»") && dropped.plan.hint.contains("«Accesorios»"))
        assertEquals(listOf("sent"), dropped.session.part("p1"))
        assertEquals(listOf("peso", "zan", "curl", "press"), dropped.session.part("p2"))
        assertNull(dropped.session.parts.first { it.id == "p2" }.exercises.first { it.id == "peso" }.supersetGroupRefOrLegacyId())
        assertEquals(ids(before), ids(dropped.session))
    }

    // ───────────────────────────── 2 · fuera de un grupo ─────────────────────────────

    @Test
    fun fueraDeUnGrupo_elEjercicioQuedaSinGrupoYSeAnunciaAntesDeSoltar() {
        val before = session()
        // Sobre los ejercicios sueltos (0–100): sale de Principal y queda sin grupo.
        val dropped = drag(before, "p1", "sent", pointerY = 80f)

        assertEquals(SessionEditorDropKind.OUT_OF_GROUP, dropped.plan.kind)
        assertNull(dropped.plan.targetPartId)
        assertEquals("Al soltar saldrá de «Principal» y quedará sin grupo.", dropped.plan.hint)
        assertEquals(listOf("loose", "sent"), dropped.session.exercises.map { it.id })
        assertEquals(listOf("peso"), dropped.session.part("p1"))
        assertEquals(ids(before), ids(dropped.session))
    }

    @Test
    fun fueraDeUnGrupo_sinSueltosBastaConSubirPorEncimaDelPrimerGrupo() {
        val before = session(loose = emptyList())
        val dropped = drag(before, "p1", "sent", pointerY = 205f)

        assertEquals(SessionEditorDropKind.OUT_OF_GROUP, dropped.plan.kind)
        assertEquals(listOf("sent"), dropped.session.exercises.map { it.id })
        assertEquals(ids(before), ids(dropped.session))
    }

    // ───────────────────────────── 3 · dentro de un grupo ─────────────────────────────

    @Test
    fun dentroDeUnGrupo_cambiaDeSitioYDiceLaPosicion() {
        val before = session()
        // Peso muerto sube sobre la mitad de arriba de la sentadilla: pasa a ser el primero de Principal.
        val dropped = drag(before, "p1", "peso", pointerY = 290f)

        assertEquals(SessionEditorDropKind.REORDER, dropped.plan.kind)
        assertEquals("Al soltar quedará en la posición 1 de «Principal».", dropped.plan.hint)
        assertEquals(listOf("peso", "sent"), dropped.session.part("p1"))
        assertEquals(ids(before), ids(dropped.session))
    }

    @Test
    fun dentroDeUnGrupo_desdeSinGrupoEntraEnElGrupo() {
        val before = session()
        val dropped = drag(before, "__loose__", "loose", pointerY = 290f)

        assertEquals(SessionEditorDropKind.INTO_GROUP, dropped.plan.kind)
        assertEquals("Al soltar entrará en «Principal».", dropped.plan.hint)
        assertEquals(listOf("loose", "sent", "peso"), dropped.session.part("p1"))
        assertTrue(dropped.session.exercises.isEmpty())
        assertEquals(ids(before), ids(dropped.session))
    }

    @Test
    fun soltarDondeEstabaNoCambiaNada() {
        val before = session()
        val dropped = drag(before, "p1", "sent", pointerY = 300f)

        assertEquals(SessionEditorDropKind.NONE, dropped.plan.kind)
        assertEquals(before.part("p1"), dropped.session.part("p1"))
    }

    // ───────────────────────────── 4 · a una superserie ya formada ─────────────────────────────

    @Test
    fun aUnaSuperserie_unEjercicioSueltoEntraComoSuTercerMiembro() {
        val before = session()
        // Sobre la tarjeta de Curl, por debajo de su mitad: entra después de él.
        val dropped = drag(before, "__loose__", "loose", pointerY = 760f)

        assertEquals(SessionEditorDropKind.INTO_SUPERSET, dropped.plan.kind)
        assertEquals("ss", dropped.plan.targetGroupId)
        assertEquals("A3", dropped.plan.supersetLetter)
        assertTrue(dropped.plan.hint, dropped.plan.hint.contains("A3") && dropped.plan.hint.contains("«Ejercicio curl»"))
        assertEquals("ss", dropped.targetGroupId)
        val group = dropped.session.supersetGroups.first { it.id == "ss" }
        assertEquals(listOf("zan", "curl", "loose"), group.exerciseOrder)
        assertEquals("ss", dropped.session.parts.first { it.id == "p2" }.exercises.first { it.id == "loose" }.supersetGroupRefOrLegacyId())
        assertTrue(dropped.session.exercises.isEmpty())
        assertEquals(ids(before), ids(dropped.session))
    }

    @Test
    fun aUnaSuperserie_entreSusMiembrosRespetaLaPosicion() {
        val before = session()
        // Entre las dos mitades de Zancada (640) y Curl (740): el segundo puesto.
        val dropped = drag(before, "p1", "peso", pointerY = 700f)

        assertEquals(SessionEditorDropKind.INTO_SUPERSET, dropped.plan.kind)
        assertEquals("A2", dropped.plan.supersetLetter)
        val group = dropped.session.supersetGroups.first { it.id == "ss" }
        assertEquals(listOf("zan", "peso", "curl"), group.exerciseOrder)
        assertEquals(listOf("sent"), dropped.session.part("p1"))
        assertEquals(ids(before), ids(dropped.session))
    }

    @Test
    fun aUnaSuperserie_valeSoltarEnLaRayaEntreSusMiembros() {
        val before = session()
        val controller = controllerFor(before)
        // Con aire entre las dos tarjetas, el dedo cae en la raya y aun así entra.
        controller.exerciseBounds["p2|zan"] = Rect(0f, 590f, 300f, 680f)
        controller.exerciseBounds["p2|curl"] = Rect(0f, 700f, 300f, 790f)
        val dropped = drag(before, "__loose__", "loose", pointerY = 690f, controller = controller)

        assertEquals(SessionEditorDropKind.INTO_SUPERSET, dropped.plan.kind)
        assertEquals("ss", dropped.targetGroupId)
        assertEquals(ids(before), ids(dropped.session))
    }

    @Test
    fun aUnaSuperserieLlena_noSeUneYSeAvisaSinPerderNada() {
        val full = session().let { base ->
            base.copy(
                parts = base.parts.map { part ->
                    if (part.id == "p2") {
                        part.copy(exercises = listOf(ex("zan", "ss"), ex("curl", "ss"), ex("c3", "ss"), ex("c4", "ss"), ex("press")))
                    } else {
                        part
                    }
                },
                supersetGroups = listOf(SupersetGroup(id = "ss", exerciseOrder = listOf("zan", "curl", "c3", "c4"))),
            )
        }
        val controller = SessionEditorDragController()
        controller.looseContentBounds = Rect(0f, 0f, 300f, 100f)
        controller.exerciseBounds["__loose__|loose"] = Rect(0f, 0f, 300f, 100f)
        controller.partBounds["p2"] = Rect(0f, 540f, 300f, 590f)
        controller.exerciseBounds["p2|zan"] = Rect(0f, 590f, 300f, 690f)
        controller.exerciseBounds["p2|curl"] = Rect(0f, 690f, 300f, 790f)
        controller.exerciseBounds["p2|c3"] = Rect(0f, 790f, 300f, 890f)
        controller.exerciseBounds["p2|c4"] = Rect(0f, 890f, 300f, 990f)
        controller.exerciseBounds["p2|press"] = Rect(0f, 990f, 300f, 1090f)
        controller.registerPartFooterBounds("p2", Rect(0f, 1090f, 300f, 1140f))

        val dropped = drag(full, "__loose__", "loose", pointerY = 740f, controller = controller)

        assertEquals(SessionEditorDropKind.INVALID, dropped.plan.kind)
        assertEquals("La superserie ya tiene 4 ejercicios.", dropped.plan.hint)
        assertEquals(ids(full), ids(dropped.session))
        assertEquals(full.supersetGroups, dropped.session.supersetGroups)
    }

    @Test
    fun unMiembroSacadoDeSuSuperserieQuedaEnElGrupoYSeAnuncia() {
        val before = session()
        // Se arrastra Zancada sola (con su asa) y se suelta sobre el press: sale de la superserie y queda en Accesorios.
        val dropped = drag(before, "p2", "zan", pointerY = 850f)

        assertEquals(SessionEditorDropKind.OUT_OF_SUPERSET, dropped.plan.kind)
        assertTrue(dropped.plan.hint, dropped.plan.hint.contains("saldrá de la superserie"))
        assertEquals(ids(before), ids(dropped.session))
    }

    // ───────────────────────────── el destino desaparece (AUD2-03) y la zona no válida ─────────────────────────────

    @Test
    fun siElDestinoDesapareceAntesDeSoltarNoSePierdeElEjercicio() {
        val before = session()
        val controller = controllerFor(before)
        val start = controller.exerciseBounds["p1|peso"]!!
        controller.beginExerciseDrag("p1", "peso", Offset(0f, 50f), session = before, dragScope = ExerciseDragScope.INDIVIDUAL)
        controller.updateExerciseDrag(Offset(0f, 590f - (start.top + 50f)), before)
        assertEquals("p2", controller.exerciseDropTargetPartId)
        // Mientras se arrastra, Accesorios deja de existir.
        val without = before.copy(parts = before.parts.filterNot { it.id == "p2" })
        var result = without
        controller.endExerciseDrag(without) { from, id, to, index ->
            result = SessionEditorMoveEngine.move(
                without,
                SessionEditorMoveRequest(from, id, to, index, moveAsGroup = false, targetGroupId = controller.exerciseDropTargetGroupId),
            )
        }
        assertTrue("peso" in ids(result))
        assertEquals(ids(without), ids(result))
    }

    @Test
    fun fueraDeLasZonasValidasSeAvisaYNoSeMueveNada() {
        val before = session()
        val controller = controllerFor(before)
        val start = controller.exerciseBounds["p1|peso"]!!
        controller.beginExerciseDrag("p1", "peso", Offset(0f, 50f), session = before, dragScope = ExerciseDragScope.INDIVIDUAL)
        controller.updateExerciseDrag(Offset(0f, 2000f - (start.top + 50f)), before)
        assertTrue(controller.exerciseDropOutOfRange)
        val plan = planExerciseDrop(
            session = before,
            sourcePartId = controller.draggingExercisePartId,
            exerciseId = controller.draggingExerciseId,
            scope = controller.draggingExerciseScope,
            targetPartId = controller.exerciseDropTargetPartId,
            targetIndex = controller.exerciseDropTargetIndex,
            targetGroupId = controller.exerciseDropTargetGroupId,
            outOfRange = controller.exerciseDropOutOfRange,
        )
        assertEquals(SessionEditorDropKind.INVALID, plan.kind)
        assertEquals("Zona no válida", plan.hint)
        var moved = false
        val result = controller.endExerciseDrag(before) { _, _, _, _ -> moved = true }
        assertEquals(ExerciseDragEndResult.OutOfRange, result)
        assertFalse(moved)
    }

    @Test
    fun laSuperserieEnteraSigueMoviendoseComoBloque() {
        val before = session()
        val dropped = drag(before, "p2", "zan", pointerY = 300f, scope = ExerciseDragScope.BLOCK)

        // El bloque (Zancada + Curl) pasa a Principal, junto, sin deshacerse.
        val group = dropped.session.supersetGroups.firstOrNull { it.id == "ss" }
        assertTrue(group != null && group.exerciseOrder.toSet() == setOf("zan", "curl"))
        assertEquals(ids(before), ids(dropped.session))
    }
}
