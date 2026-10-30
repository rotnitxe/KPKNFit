package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.split.CatalogNow
import com.example.kpkn.domain.training.split.DayKind
import com.example.kpkn.domain.training.split.DayMark
import com.example.kpkn.domain.training.split.DayRole
import com.example.kpkn.domain.training.split.SplitCatalog
import com.example.kpkn.domain.training.split.SplitCatalogFilter
import com.example.kpkn.domain.training.split.SplitDayMarks
import com.example.kpkn.domain.training.split.StripCell
import com.example.kpkn.screens.programdetail.design.split.RpAppliedActions
import com.example.kpkn.screens.programdetail.design.split.RpAppliedButtons
import com.example.kpkn.screens.programdetail.design.split.RpAppliedContent
import com.example.kpkn.screens.programdetail.design.split.RpAppliedModel
import com.example.kpkn.screens.programdetail.design.split.RpAuthorDialogCard
import com.example.kpkn.screens.programdetail.design.split.RpDay
import com.example.kpkn.screens.programdetail.design.split.RpDecisionActions
import com.example.kpkn.screens.programdetail.design.split.RpDecisionsContent
import com.example.kpkn.screens.programdetail.design.split.RpDecisionsModel
import com.example.kpkn.screens.programdetail.design.split.RpExerciseLine
import com.example.kpkn.screens.programdetail.design.split.RpMapPart
import com.example.kpkn.screens.programdetail.design.split.RpMapRow
import com.example.kpkn.screens.programdetail.design.split.RpMoveTarget
import com.example.kpkn.screens.programdetail.design.split.RpNotice
import com.example.kpkn.screens.programdetail.design.split.RpNoticeAction
import com.example.kpkn.screens.programdetail.design.split.RpPickItem
import com.example.kpkn.screens.programdetail.design.split.RpPlaceDecision
import com.example.kpkn.screens.programdetail.design.split.RpPlaceOption
import com.example.kpkn.screens.programdetail.design.split.RpPreviewActions
import com.example.kpkn.screens.programdetail.design.split.RpPreviewModel
import com.example.kpkn.screens.programdetail.design.split.RpProposal
import com.example.kpkn.screens.programdetail.design.split.RpRefusal
import com.example.kpkn.screens.programdetail.design.split.RpReviewActions
import com.example.kpkn.screens.programdetail.design.split.RpReviewModel
import com.example.kpkn.screens.programdetail.design.split.RpReviewRow
import com.example.kpkn.screens.programdetail.design.split.RpReviewSession
import com.example.kpkn.screens.programdetail.design.split.RpReviewSummary
import com.example.kpkn.screens.programdetail.design.split.RpReviewTab
import com.example.kpkn.screens.programdetail.design.split.RpSession
import com.example.kpkn.screens.programdetail.design.split.RpSheetColumn
import com.example.kpkn.screens.programdetail.design.split.RpSheetSurface
import com.example.kpkn.screens.programdetail.design.split.RpSheetPill
import com.example.kpkn.screens.programdetail.design.split.RpSeverity
import com.example.kpkn.screens.programdetail.design.split.RpSpan
import com.example.kpkn.screens.programdetail.design.split.RpTimeDecision
import com.example.kpkn.screens.programdetail.design.split.RpView
import com.example.kpkn.screens.programdetail.design.split.RpVolumeRow
import com.example.kpkn.screens.programdetail.design.split.SplitCatalogMode
import com.example.kpkn.screens.programdetail.design.split.SplitCatalogScreen
import com.example.kpkn.screens.programdetail.design.split.SplitFlowModels
import com.example.kpkn.screens.programdetail.design.split.SplitPreviewScreen
import com.example.kpkn.screens.programdetail.design.split.SplitReviewScreen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * HOJAS DE RENDER DEL REPARTO (punto 23 de «Pulido v1»): cada escena de la tanda 1 (`rp-catalogo`, `rp-semana`, `rp-mas-dias`, `rp-menos-dias`, `rp-en-curso`,
 * `rp-vacia`, `rp-calculando`, `rp-revision`, `rp-revision-musculo`, `rp-decisiones`, `rp-aplicado`, `rp-deshecho`, `rp-rechazo`, `rp-autor`) con los datos de
 * muestra del lienzo (`src/test/resources/reparto/lienzo.json`, volcado de `scenes-rp.js`) a 360 × 880 dp; las páginas largas también enteras (360 × 2400)
 * y las densas a 360 × 740 con letra al 130 %. Escribe PNG a 1080 px de ancho (`<id>.png`) y a 360 px (`<id>_1x.png`) en `KPKN_RENDER_DIR`; se comparan con
 * `node tools/compare.cjs <lienzo.png> <id>_1x.png`. El lienzo lleva una franja de estado de 38 px arriba: aquí se deja ese hueco vacío. El vidrio con desenfoque
 * (Haze) no se pinta en un lienzo de software: solo se mide la sombra, la geometría y el texto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2500dp-xxhdpi", application = android.app.Application::class)
class RepartoRenderSheetTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        private const val SCALE = 3
        private val SCENES = listOf(
            "rp-catalogo", "rp-semana", "rp-mas-dias", "rp-menos-dias", "rp-en-curso", "rp-vacia", "rp-calculando", "rp-revision",
            "rp-revision-musculo", "rp-decisiones", "rp-aplicado", "rp-deshecho", "rp-rechazo", "rp-autor",
        )
        private val TALL = setOf("rp-catalogo", "rp-semana", "rp-revision", "rp-en-curso")
        private val DENSE = setOf("rp-catalogo", "rp-semana", "rp-mas-dias", "rp-menos-dias", "rp-en-curso", "rp-revision", "rp-decisiones")
    }

    @get:Rule
    val rule = createComposeRule()

    private var sceneId by mutableStateOf<String?>(null)
    private var heightDp by mutableIntStateOf(880)
    private var fontScale by mutableFloatStateOf(1f)
    private var hostView: View? = null

    private val samples: JSONObject by lazy {
        val stream = checkNotNull(javaClass.classLoader!!.getResourceAsStream("reparto/lienzo.json")) { "falta reparto/lienzo.json" }
        JSONObject(stream.bufferedReader().readText())
    }

    // ─── JSON → modelos ─────────────────────────────────────────────────────────────────────────

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONObject.str(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
    private fun role(name: String): DayRole = DayRole.valueOf(name)

    private fun rich(array: JSONArray): List<RpSpan> = (0 until array.length()).map { RpSpan(array.getJSONArray(it).getString(0), array.getJSONArray(it).getBoolean(1)) }

    private fun volume(array: JSONArray): List<RpVolumeRow> = array.objects().map {
        RpVolumeRow(it.getString("label"), it.getDouble("before"), it.getDouble("after"), it.getInt("fb"), it.getInt("fa"))
    }

    private fun session(o: JSONObject) = RpSession(
        id = o.getString("id"),
        weekday = o.getInt("weekday"),
        title = o.getString("title"),
        focus = o.getString("focus"),
        detail = o.getString("detail"),
        role = role(o.getString("role")),
        kind = DayKind.valueOf(o.getString("kind")),
        exercises = o.getJSONArray("exercises").objects().map { e -> RpExerciseLine(e.getString("name"), e.getString("prescription"), e.str("movedFrom"), e.str("supersetId")) },
        moveTargets = o.getJSONArray("moveTargets").objects().map { m -> RpMoveTarget(m.getInt("weekday"), m.getString("letter"), m.getBoolean("here"), m.getBoolean("enabled"), m.getString("description")) },
        template = o.optBoolean("template", false),
        alternatives = o.getJSONArray("alternatives").strings(),
        readOnly = o.optBoolean("readOnly", false),
    )

    private fun preview(o: JSONObject, lead: String? = null): RpPreviewModel = RpPreviewModel(
        eyebrow = o.getString("eyebrow"),
        title = o.getString("title"),
        lead = lead ?: o.getString("lead"),
        chips = o.getJSONArray("chips").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { c -> (if (c.isNull(0)) null else c.getString(0)) to (if (c.isNull(1)) null else c.getString(1)) } } },
        view = RpView.AFTER,
        days = o.getJSONArray("days").objects().map { d ->
            RpDay(d.getInt("weekday"), if (d.isNull("date")) null else d.getInt("date"), d.getJSONArray("sessions").objects().map(::session))
        },
        todayWeekday = if (o.isNull("today")) null else o.getInt("today"),
        scopeText = o.getString("scope"),
        notices = o.getJSONArray("notices").objects().map { n ->
            RpNotice(RpSeverity.valueOf(n.getString("sev")), rich(n.getJSONArray("spans")), if (n.isNull("action")) null else RpNoticeAction.ADJUST_TIME)
        },
        pickerTrailing = o.getString("pickerTrailing"),
        picker = o.getJSONArray("picker").objects().map { p ->
            RpPickItem(
                p.getString("id"), p.getString("name"),
                p.getJSONArray("strip").objects().map { c ->
                    val r = DayRole.valueOf(c.getString("role"))
                    StripCell(0, c.getString("letter"), "", DayMark(r, c.getString("initial")))
                },
                p.getString("caption"), p.getBoolean("on"),
            )
        },
        pickerTotal = 35,
        volume = if (o.isNull("volume")) null else volume(o.getJSONArray("volume")),
        ctaLabel = o.getString("cta"),
        emptyWeek = o.getString("cta") == "Aplicar reparto",
    )

    private fun review(o: JSONObject): RpReviewModel {
        val s = o.getJSONObject("summary")
        return RpReviewModel(
            eyebrow = o.getString("eyebrow"),
            summary = RpReviewSummary(s.getString("verdict"), s.getInt("moved"), s.getInt("exercises"), s.getInt("cardio"), s.getInt("sets"), s.getInt("setsBefore"), s.getInt("lockedCount")),
            lockedNote = if (o.isNull("lockedNote")) null else rich(o.getJSONArray("lockedNote")),
            proposals = o.getJSONArray("proposals").objects().map { RpProposal(it.getString("bold"), it.getString("rest")) },
            timeWarning = o.str("timeWarning"),
            tab = RpReviewTab.valueOf(o.getString("tab")),
            map = o.getJSONArray("map").objects().map { m ->
                RpMapRow(m.getString("name"), m.getInt("total"), m.getBoolean("locked"), m.getJSONArray("parts").objects().map { p -> RpMapPart(p.getString("title"), p.getInt("count"), role(p.getString("role"))) }, m.getString("caption"))
            },
            sessions = o.getJSONArray("sessions").objects().map { s2 ->
                RpReviewSession(
                    s2.getString("title"), role(s2.getString("role")), s2.getString("meta"), s2.getString("from"), s2.getBoolean("locked"),
                    s2.getJSONArray("rows").objects().map { r -> RpReviewRow(r.getString("name"), r.getString("prescription"), r.str("tag"), r.getBoolean("moved"), r.getBoolean("locked"), r.str("supersetId")) },
                )
            },
            volume = volume(o.getJSONArray("volume")),
            muscleNote = o.getString("muscleNote"),
        )
    }

    private fun decisions(o: JSONObject): RpDecisionsModel = RpDecisionsModel(
        places = o.getJSONArray("places").objects().map { p ->
            RpPlaceDecision(p.getString("unitKey"), p.getString("title"), p.getString("explanation"), p.getString("current"), p.getJSONArray("options").objects().map { RpPlaceOption(it.getInt("dayIndex"), it.getString("title"), it.getBoolean("on")) })
        },
        time = if (o.isNull("time")) null else o.getJSONObject("time").let { RpTimeDecision(it.getInt("targetMinutes"), it.getString("explanation"), it.getBoolean("fit"), it.str("boldPrefix")) },
    )

    private fun applied(o: JSONObject): RpAppliedModel {
        val weekdays = o.getJSONArray("weekdays").strings()
        val summary = com.example.kpkn.screens.programdetail.design.split.RpAppliedSummary(
            splitName = o.getString("splitName"), weekLabel = "Semana 5", exercises = o.getInt("exercises"), sessions = o.getInt("sessions"), sets = o.getInt("sets"),
            setsBefore = o.getInt("setsBefore"), moved = o.getInt("moved"), weekdays = emptyList(),
            beforeText = "Personalizado, 4 días (lunes, martes, jueves y viernes)",
        )
        val model = SplitFlowModels.appliedModel(summary, o.getBoolean("undone"))
        // «Días de entreno» con las iniciales del lienzo («lun, mar, jue y vie»): el resumen del motor las calcula, aquí vienen ya escritas.
        return model.copy(stats = model.stats.map { (label, value) -> if (label == "Días de entreno") label to (weekdays.dropLast(1).joinToString(", ") + " y " + weekdays.last()) else label to value })
    }

    // ─── Escenas ────────────────────────────────────────────────────────────────────────────────

    private val noPreviewActions = RpPreviewActions()

    @Composable
    private fun Scene(id: String) {
        val data: JSONObject = samples.optJSONObject(id) ?: JSONObject()
        when (id) {
            "rp-catalogo" -> {
                val pattern = listOf("Pierna A", "Empuje", "Descanso", "Tirón", "Pierna B", "Descanso", "Descanso")
                val now = CatalogNow("Personalizado", 4, SplitDayMarks.strip(pattern, 1))
                val view = SplitCatalog.view(SplitCatalogFilter(), haveDays = 4, now = now)
                SplitCatalogScreen(
                    now = now, nowWeekLabel = "semana 5", haveDays = 4, startDay = 1, query = "", onQueryChange = {}, selectedTag = null, onSelectTag = {},
                    daysFilter = null, onDaysFilter = {}, groups = view.groups, customSplit = null, mode = SplitCatalogMode.BROWSE, selectedIds = emptyList(),
                    onBack = {}, onToggleCompareMode = {}, onToggleMultiMode = {}, onOpen = {}, onToggleSelect = {}, onEditCustom = {}, onApplyCustom = {},
                    onCompare = {}, onApplySelected = {},
                )
            }
            "rp-semana", "rp-mas-dias", "rp-menos-dias", "rp-en-curso", "rp-vacia" -> SplitPreviewScreen(preview(data.getJSONObject("preview")), noPreviewActions, hazeState = null)
            "rp-calculando" -> {
                val base = preview(samples.getJSONObject("rp-semana").getJSONObject("preview"))
                val restDays = setOf(3, 6, 7)
                SplitPreviewScreen(
                    base.copy(chips = emptyList(), calculating = true, days = base.days.map { d -> if (d.weekday in restDays) d.copy(sessions = emptyList()) else d }, volume = null, notices = emptyList()),
                    noPreviewActions, hazeState = null,
                )
            }
            "rp-rechazo" -> {
                val model = preview(data.getJSONObject("preview")).copy(
                    refusal = RpRefusal(
                        "La semana incluye una competición.",
                        "No se pueden repartir los ejercicios de una semana con competición: mover sus sesiones rompería la preparación del día de competencia. El resto de tus semanas sí.",
                        "Aplicar con sesiones vacías",
                        "Cambiar de reparto en esta semana queda desactivado con cualquier reparto, hasta que pase la competición.",
                    ),
                    ctaEnabled = false,
                    chips = emptyList(),
                )
                SplitPreviewScreen(model, noPreviewActions, hazeState = null)
            }
            "rp-autor" -> SplitPreviewScreen(
                preview(samples.getJSONObject("rp-semana").getJSONObject("preview")), noPreviewActions, hazeState = null,
                overlay = {
                    Box(Modifier.fillMaxSize().background(Color(0xA8000000)))
                    Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 152.dp)) { RpAuthorDialogCard(onKeep = {}, onAdapt = {}) }
                },
            )
            "rp-revision", "rp-revision-musculo" -> SplitReviewScreen(review(data.getJSONObject("review")), RpReviewActions(), hazeState = null)
            "rp-decisiones" -> {
                val model = decisions(data.getJSONObject("decisions"))
                SplitReviewScreen(
                    review(data.getJSONObject("review")), RpReviewActions(), hazeState = null,
                    overlay = {
                        Box(Modifier.fillMaxSize().background(Color(0xA8000000)))
                        SheetOver { RpSheetColumn(sticky = { RpSheetPill("Listo", onClick = {}, modifier = Modifier.fillMaxWidth()) }) { RpDecisionsContent(model, RpDecisionActions()) } }
                    },
                )
            }
            "rp-aplicado", "rp-deshecho" -> {
                val model = applied(data.getJSONObject("applied"))
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color(0xA8000000)))
                    SheetOver { RpSheetColumn(sticky = { RpAppliedButtons(model, RpAppliedActions()) }) { RpAppliedContent(model) } }
                }
            }
        }
    }

    /** La hoja `.pl-sheet` pegada abajo: `#1b1b1d`, radio 28 arriba y el asa de 40 × 4. */
    @Composable
    private fun androidx.compose.foundation.layout.BoxScope.SheetOver(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(RpSheetSurface)
                .padding(top = 10.dp),
        ) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF)))
                // 6 dp de margen del asa y 14 dp de separacion (`gap`) con el primer bloque.
                Box(Modifier.height(20.dp))
                content()
            }
        }
    }

    // ─── Captura ────────────────────────────────────────────────────────────────────────────────

    private fun capture(name: String, heightPx: Int) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val cropped = Bitmap.createBitmap(full, 0, 0, 360 * SCALE, (heightPx * SCALE).coerceAtMost(full.height))
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileOutputStream(File(dir, "${name}_1x.png")).use { boxDownscale(cropped).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun boxDownscale(src: Bitmap): Bitmap {
        val w = src.width / SCALE
        val h = src.height / SCALE
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            for (dy in 0 until SCALE) for (dx in 0 until SCALE) {
                val p = px[(y * SCALE + dy) * src.width + x * SCALE + dx]
                r += (p shr 16) and 255
                g += (p shr 8) and 255
                b += p and 255
            }
            val n = SCALE * SCALE
            out[y * w + x] = (255 shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun show(id: String, height: Int, scale: Float, name: String) {
        rule.runOnUiThread { sceneId = null; heightDp = height; fontScale = scale }
        rule.waitForIdle()
        rule.runOnUiThread { sceneId = id }
        rule.waitForIdle()
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
        capture(name, height)
    }

    @Test
    fun renderRepartoScenes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                Box(Modifier.width(360.dp).height(heightDp.dp).background(Color.Black).padding(top = 38.dp)) {
                    sceneId?.let { Scene(it) }
                }
            }
        }
        for (id in SCENES) {
            show(id, 880, 1f, id)
            if (id in TALL) show(id, 2400, 1f, "$id-tall")
            if (id in DENSE) show(id, 740, 1.3f, "$id-740-fs130")
        }
    }
}
