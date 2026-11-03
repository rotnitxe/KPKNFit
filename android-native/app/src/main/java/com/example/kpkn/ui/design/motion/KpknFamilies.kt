package com.example.kpkn.ui.design.motion

/**
 * Familia de `@keyframes` del lienzo: el canónico ([keyframes]), la demostración más usada de la familia ([demo], con su duración,
 * curva, retardo, repetición y relleno), y los `@keyframes` del lienzo que agrupa ([members]). [sheet] = hoja `cm-mov-*` donde se calibra.
 */
class KpknFamily(
    val name: String,
    val description: String,
    val sheet: String,
    val keyframes: KpknKeyframes,
    val demo: KpknAnimation,
    /** Duración de la demostración tal como la escribe el CSS del lienzo (`0.42s`): es la que muestra la hoja de calibración. */
    val demoDurationText: String,
    val demoCurve: KpknCurve,
    val members: List<String>,
)

/**
 * Las 33 familias de `@keyframes` del lienzo (`paridad/CIMIENTOS_GRUPOS.md`), una sola vez y con nombre. Las pantallas usan
 * `KpknFamilies.EntradaSube.demo` (o una copia con `copy(duration = …)`) y el motor [KpknAnimation]/`Modifier.kpknAnimate`; no escriben
 * fotogramas sueltos.
 *
 * ```
 * Modifier.kpknAnimate(KpknFamilies.EntradaSube.demo)                                     // la entrada de 420 ms del lienzo
 * Modifier.kpknAnimate(KpknFamilies.Rebote.demo.copy(duration = KpknDuration(240)))        // el mismo rebote con otra duración
 * val latido = rememberKpknPulse(KpknFamilies.Latido.demo)                                 // bucle con su periodo exacto
 * ```
 */
object KpknFamilies {
    private fun curve(name: String) = KpknMotion.curve(name)

    private fun family(
        name: String,
        description: String,
        sheet: String,
        keyframes: KpknKeyframes,
        durationText: String,
        durationMs: Int,
        curveName: String,
        delayMs: Int = 0,
        iterations: Float = 1f,
        fill: KpknFill = KpknFill.None,
        members: List<String> = emptyList(),
    ): KpknFamily {
        val c = curve(curveName)
        return KpknFamily(
            name = name,
            description = description,
            sheet = sheet,
            keyframes = keyframes,
            demo = KpknAnimation(keyframes, KpknDuration(durationMs), c.easing, KpknDuration(delayMs), iterations, KpknDirection.Normal, fill),
            demoDurationText = durationText,
            demoCurve = c,
            members = members,
        )
    }

    private const val INF = KpknAnimation.INFINITE

    val EntradaSube = family(
        "entrada-sube", "Aparece subiendo", "entradas",
        kpknKeyframes("cv-rise", "@keyframes cv-rise { 0% { opacity: 0; transform: translateY(14px); } 100% { opacity: 1; transform: none; } }") {
            at(0f) { alpha(0f); translateY(14f) }
            at(1f) { alpha(1f); transformNone() }
        },
        "0.42s", 420, "estandar", fill = KpknFill.Both,
        members = listOf("cv-rise", "rp-in", "ce-in", "ct-rise", "nc-up", "aj-sheet", "aj-toast", "asContent", "ed-in", "lv-swap", "lv2-roll", "lv2-su", "tcpop"),
    )

    val DibujoTrazo = family(
        "dibujo-trazo", "Dibujo de línea o anillo", "trazos",
        kpknKeyframes("drawln", "@keyframes drawln { 100% { stroke-dashoffset: 0; } }") {
            base(KpknChannel.DashOffset, 1f)
            at(1f) { dashOffset(0f) }
        },
        "1.6s", 1600, "trazo", fill = KpknFill.Forwards,
        members = listOf("drawln", "lv-ck", "lv2-arc", "lv2-ck", "lxring", "nc-ring"),
    )

    val CreceX = family(
        "crece-x", "Crece en horizontal", "trazos",
        kpknKeyframes("wrbar", "@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }") {
            at(0f) { scaleX(0f) }
            at(1f) { scaleX(1f) }
        },
        "6s", 6000, "lineal", fill = KpknFill.Forwards,
        members = listOf("wrbar", "cv-bar", "asGrow", "lxgrow"),
    )

    val EntradaDerecha = family(
        "entrada-derecha", "Aparece desde la derecha", "entradas",
        kpknKeyframes("asIn", "@keyframes asIn { 0% { opacity: 0; transform: translateX(var(--dx,10px)); } 100% { opacity: 1; transform: none; } }") {
            at(0f) { alpha(0f); translateX(10f) }
            at(1f) { alpha(1f); transformNone() }
        },
        "0.22s", 220, "estandar", fill = KpknFill.Both,
        members = listOf("asIn", "aj-push", "ct-in", "ed-lbl-r", "lv2-sr"),
    )

    val FundidoEntrada = family(
        "fundido-entrada", "Fundido de entrada", "entradas",
        kpknKeyframes("asScrim", "@keyframes asScrim { 0% { opacity: 0; } 100% { opacity: 1; } }") {
            at(0f) { alpha(0f) }
            at(1f) { alpha(1f) }
        },
        "0.2s", 200, "suave", fill = KpknFill.Both,
        members = listOf("asScrim", "cv-fadein", "ed-fadein", "lxfade", "aj-fade"),
    )

    val Giro = family(
        "giro", "Giro continuo", "bucles",
        kpknKeyframes("lxspin", "@keyframes lxspin { 100% { transform: rotate(360deg); } }") {
            at(1f) { rotate(360f) }
        },
        "0.8s", 800, "lineal", iterations = INF,
        members = listOf("lxspin", "aj-spin", "asSpin", "ct-sp", "nc-spin", "rp-rot", "orb"),
    )

    val HojaSube = family(
        "hoja-sube", "Hoja que sube", "hojas",
        kpknKeyframes("asRise", "@keyframes asRise { 0% { transform: translateY(100%); } 100% { transform: none; } }") {
            at(0f) { translateYFraction(1f) }
            at(1f) { transformNone() }
        },
        "0.42s", 420, "estandar", fill = KpknFill.Both,
        members = listOf("asRise", "cv-sheet", "ed-up", "lxsheet", "tcsheet"),
    )

    val Rebote = family(
        "rebote", "Rebote (pulsación con rebasado)", "pulsaciones",
        kpknKeyframes("lv-pop", "@keyframes lv-pop { 0% { transform: scale(0.88); } 60% { transform: scale(1.14); } 100% { transform: scale(1); } }") {
            at(0f) { scale(0.88f) }
            at(0.6f) { scale(1.14f) }
            at(1f) { scale(1f) }
        },
        "0.2s", 200, "estandar",
        members = listOf("lv-pop", "ed-lift", "ed-pop", "lv-snap", "spbump", "sppop", "bump"),
    )

    val Pulso = family(
        "pulso", "Pulso de opacidad o grosor (bucle)", "bucles",
        kpknKeyframes("blink", "@keyframes blink { 50% { opacity: 0; } }") {
            at(0.5f) { alpha(0f) }
        },
        "1s", 1000, "parpadeo", iterations = INF,
        members = listOf("blink", "ce-blink", "lv-breathe", "lxpulse", "nc-pulse", "tcq"),
    )

    val ApareceEscala = family(
        "aparece-escala", "Aparece creciendo", "pulsaciones",
        kpknKeyframes("ed-bump", "@keyframes ed-bump { 0% { transform: scale(0.86); } 100% { transform: none; } }") {
            at(0f) { scale(0.86f) }
            at(1f) { transformNone() }
        },
        "0.24s", 240, "resorte",
        members = listOf("ed-bump", "lv-pin", "rp-pop"),
    )

    val BrilloBarrido = family(
        "brillo-barrido", "Brillo que barre (esqueleto)", "bucles",
        kpknKeyframes("asSh", "@keyframes asSh { 0% { background-position: 120% 0px; } 100% { background-position: -120% 0px; } }") {
            base(KpknChannel.BackgroundShiftPct, 120f)
            at(0f) { backgroundShift(120f) }
            at(1f) { backgroundShift(-120f) }
        },
        "1.4s", 1400, "lineal", iterations = INF,
        members = listOf("asSh", "cv-shim", "nc-sh", "rp-sk", "tcsk", "sk"),
    )

    val Destello = family(
        "destello", "Destello de fondo o color", "trazos",
        kpknKeyframes("asFlash", "@keyframes asFlash { 0% { color: var(--mus); } 100% { color: inherit; } }") {
            at(0f) { colorMix(0f) }
            at(1f) { colorMix(1f) }
        },
        "0.6s", 600, "suave",
        members = listOf("asFlash", "ce-fresh", "cv-swdone", "ed-flash", "nc-flash"),
    )

    val EntradaBaja = family(
        "entrada-baja", "Aparece bajando", "entradas",
        kpknKeyframes("lxrise", "@keyframes lxrise { 0% { opacity: 0; transform: translateY(-8px); } 100% { opacity: 1; transform: none; } }") {
            at(0f) { alpha(0f); translateY(-8f) }
            at(1f) { alpha(1f); transformNone() }
        },
        "0.24s", 240, "estandar", fill = KpknFill.Both,
        members = listOf("lxrise", "ed-popin", "tcfade"),
    )

    val BarridoAncho = family(
        "barrido-ancho", "Barrido de ancho", "trazos",
        kpknKeyframes("lv-wipe", "@keyframes lv-wipe { 0% { width: 0px; opacity: 0.9; } 70% { width: 100%; opacity: 0.9; } 100% { width: 100%; opacity: 0; } }") {
            // El elemento de la hoja parte de `width: 0` y, sin relleno, vuelve a él al terminar.
            base(KpknChannel.WidthFraction, 0f)
            at(0f) { width(0f); alpha(0.9f) }
            at(0.7f) { width(1f); alpha(0.9f) }
            at(1f) { width(1f); alpha(0f) }
        },
        "0.55s", 550, "estandar",
        members = listOf("lv-wipe", "lv2-fill", "lv2-wipe"),
    )

    val CreceY = family(
        "crece-y", "Crece en vertical", "trazos",
        kpknKeyframes("cv-grow", "@keyframes cv-grow { 0% { transform: scaleY(0.05); opacity: 0; } 100% { transform: scaleY(1); } }") {
            at(0f) { scaleY(0.05f); alpha(0f) }
            at(1f) { scaleY(1f) }
        },
        "0.6s", 600, "estandar", fill = KpknFill.Both,
        members = listOf("cv-grow", "nc-bar", "grow", "hgrow"),
    )

    val EntradaImplicita = family(
        "entrada-implicita", "Entrada con el estado inicial en la regla", "entradas",
        kpknKeyframes("lxrise2", "@keyframes lxrise2 { 100% { opacity: 1; transform: none; } }") {
            // El estado inicial lo escribe el elemento (`opacity: 0; transform: translateY(12px)`), no el @keyframes.
            base(KpknChannel.Alpha, 0f)
            base(KpknChannel.TranslateYDp, 12f)
            at(1f) { alpha(1f); transformNone() }
        },
        "0.5s", 500, "estandar", delayMs = 700, fill = KpknFill.Forwards,
        members = listOf("lxrise2", "lv2-chip", "pop", "rise"),
    )

    val EntradaIzquierda = family(
        "entrada-izquierda", "Aparece desde la izquierda", "entradas",
        kpknKeyframes("aj-pop", "@keyframes aj-pop { 0% { transform: translateX(-24px); opacity: 0; } 100% { transform: none; opacity: 1; } }") {
            at(0f) { translateX(-24f); alpha(0f) }
            at(1f) { transformNone(); alpha(1f) }
        },
        "0.3s", 300, "estandar", fill = KpknFill.Both,
        members = listOf("aj-pop", "ed-lbl-l", "lv2-sl"),
    )

    val ReboteAparece = family(
        "rebote-aparece", "Rebote al aparecer", "pulsaciones",
        kpknKeyframes("cv-pop", "@keyframes cv-pop { 0% { opacity: 0; transform: scale(0.86); } 60% { opacity: 1; transform: scale(1.03); } 100% { transform: scale(1); } }") {
            at(0f) { alpha(0f); scale(0.86f) }
            at(0.6f) { alpha(1f); scale(1.03f) }
            at(1f) { scale(1f) }
        },
        "0.35s", 350, "suave", fill = KpknFill.Both,
        members = listOf("cv-pop"),
    )

    val Asentar = family(
        "asentar", "Se asienta (parte de más grande)", "pulsaciones",
        kpknKeyframes("cv-cn", "@keyframes cv-cn { 0% { opacity: 0; transform: scale(1.5); } 30% { opacity: 1; } 100% { opacity: 1; transform: scale(1); } }") {
            at(0f) { alpha(0f); scale(1.5f) }
            at(0.3f) { alpha(1f) }
            at(1f) { alpha(1f); scale(1f) }
        },
        "0.9s", 900, "estandar", fill = KpknFill.Both,
        members = listOf("cv-cn", "lv2-tick"),
    )

    val Latido = family(
        "latido", "Latido", "bucles",
        kpknKeyframes("cv-beat", "@keyframes cv-beat { 0% { opacity: 0.34; transform: scale(1.06); } 45% { opacity: 0.18; transform: scale(1); } 100% { opacity: 0.2; } }") {
            at(0f) { alpha(0.34f); scale(1.06f) }
            at(0.45f) { alpha(0.18f); scale(1f) }
            at(1f) { alpha(0.2f) }
        },
        "1.1s", 1100, "salida", iterations = INF,
        members = listOf("cv-beat", "cv-heart"),
    )

    val Onda = family(
        "onda", "Onda que se expande", "trazos",
        kpknKeyframes("cv-rg", "@keyframes cv-rg { 0% { transform: scale(0.5); opacity: 0.9; } 100% { transform: scale(3.4); opacity: 0; } }") {
            at(0f) { scale(0.5f); alpha(0.9f) }
            at(1f) { scale(3.4f); alpha(0f) }
        },
        "0.9s", 900, "salida", fill = KpknFill.Both,
        members = listOf("cv-rg", "cv-rip"),
    )

    val Aviso = family(
        "aviso", "Aviso: entra, espera y sale", "entradas",
        kpknKeyframes("ed-delta", "@keyframes ed-delta { 0% { opacity: 0; transform: translateY(8px); } 18% { opacity: 1; transform: none; } 75% { opacity: 1; } 100% { opacity: 0; transform: translateY(-8px); } }") {
            at(0f) { alpha(0f); translateY(8f) }
            at(0.18f) { alpha(1f); transformNone() }
            at(0.75f) { alpha(1f) }
            at(1f) { alpha(0f); translateY(-8f) }
        },
        "1.3s", 1300, "estandar", fill = KpknFill.Forwards,
        members = listOf("ed-delta"),
    )

    /** `@keyframes tcsweep`: baja `var(--vfh,640px) + 46px`; [viewportHeightDp] es `--vfh` (la hoja de calibración usa 48). */
    fun barridoLineaKeyframes(viewportHeightDp: Float = 640f): KpknKeyframes = kpknKeyframes(
        "tcsweep",
        "@keyframes tcsweep { 0% { transform: translateY(0px); } 100% { transform: translateY(calc(var(--vfh,640px) + 46px)); } }",
    ) {
        at(0f) { translateY(0f) }
        at(1f) { translateY(viewportHeightDp + 46f) }
    }

    val BarridoLinea = family(
        "barrido-linea", "Línea que barre", "trazos",
        barridoLineaKeyframes(48f),
        "2.6s", 2600, "lineal", iterations = INF,
        members = listOf("tcsweep", "las"),
    )

    val DespliegueFila = family(
        "despliegue-fila", "Despliegue de fila", "trazos",
        kpknKeyframes("ed-fresh", "@keyframes ed-fresh { 0% { background: rgba(242, 238, 230, 0.12); max-height: 0px; opacity: 0; } 30% { max-height: 140px; } 100% { background: var(--module); max-height: 200px; opacity: 1; } }") {
            at(0f) { colorMix(0f); maxHeight(0f); alpha(0f) }
            at(0.3f) { maxHeight(140f) }
            at(1f) { colorMix(1f); maxHeight(200f); alpha(1f) }
        },
        "0.5s", 500, "estandar",
        members = listOf("ed-fresh"),
    )

    val FundidoSalida = family(
        "fundido-salida", "Fundido de salida", "entradas",
        kpknKeyframes("ed-fadeout", "@keyframes ed-fadeout { 100% { opacity: 0; } }") {
            at(1f) { alpha(0f) }
        },
        "0.2s", 200, "suave", fill = KpknFill.Forwards,
        members = listOf("ed-fadeout"),
    )

    val HojaBaja = family(
        "hoja-baja", "Hoja que baja", "hojas",
        kpknKeyframes("ed-down", "@keyframes ed-down { 100% { transform: translateY(100%); } }") {
            at(1f) { translateYFraction(1f) }
        },
        "0.24s", 240, "cae", fill = KpknFill.Forwards,
        members = listOf("ed-down"),
    )

    val Ignicion = family(
        "ignicion", "Ignición (anillo y resplandor)", "trazos",
        kpknKeyframes("cv-ignite", "@keyframes cv-ignite { 0% { box-shadow: transparent 0px 0px 0px 0px; } 40% { box-shadow: 0 0 0 10px color-mix(in srgb,var(--zc) 30%,transparent); } 100% { box-shadow: 0 0 22px color-mix(in srgb,var(--zc) 40%,transparent); } }") {
            at(0f) { shadow(alpha = 0f, spreadDp = 0f, blurDp = 0f) }
            at(0.4f) { shadow(alpha = 0.3f, spreadDp = 10f, blurDp = 0f) }
            at(1f) { shadow(alpha = 0.4f, spreadDp = 0f, blurDp = 22f) }
        },
        "0.8s", 800, "suave", delayMs = 1100, fill = KpknFill.Both,
        members = listOf("cv-ignite"),
    )

    val OndaAnillo = family(
        "onda-anillo", "Onda de anillo", "trazos",
        kpknKeyframes("ed-ring", "@keyframes ed-ring { 0% { box-shadow: rgba(242, 238, 230, 0.467) 0px 0px 0px 0px; } 100% { box-shadow: rgba(242, 238, 230, 0) 0px 0px 0px 14px; } }") {
            at(0f) { shadow(alpha = 0.467f, spreadDp = 0f, blurDp = 0f) }
            at(1f) { shadow(alpha = 0f, spreadDp = 14f, blurDp = 0f) }
        },
        "0.46s", 460, "salida", fill = KpknFill.Forwards,
        members = listOf("ed-ring"),
    )

    val PulsoAnillo = family(
        "pulso-anillo", "Pulso de anillo (bucle)", "bucles",
        kpknKeyframes("tcpulse", "@keyframes tcpulse { 0%, 100% { box-shadow: rgba(242, 238, 230, 0.333) 0px 0px 0px 0px; } 50% { box-shadow: rgba(242, 238, 230, 0) 0px 0px 0px 5px; } }") {
            at(0f) { shadow(alpha = 0.333f, spreadDp = 0f, blurDp = 0f) }
            at(0.5f) { shadow(alpha = 0f, spreadDp = 5f, blurDp = 0f) }
            at(1f) { shadow(alpha = 0.333f, spreadDp = 0f, blurDp = 0f) }
        },
        "1.2s", 1200, "vaiven", iterations = INF,
        members = listOf("tcpulse"),
    )

    val Revelado = family(
        "revelado", "Revelado de vídeo (brillo y desenfoque)", "trazos",
        kpknKeyframes("tcvid", "@keyframes tcvid { 0% { filter: brightness(0.5) blur(6px); transform: translate(var(--vx),var(--vy)) scale(calc(var(--vs)*1.06)); } 100% { filter: none; } }") {
            // La hoja usa --vx 0, --vy 0, --vs 1: el 0 % solo escala 1,06 y el 100 % (sin transform) vuelve al estado base.
            at(0f) { brightness(0.5f); blur(6f); scale(1.06f) }
            at(1f) { brightness(1f); blur(0f) }
        },
        "1.1s", 1100, "estandar", fill = KpknFill.Both,
        members = listOf("tcvid"),
    )

    val Caida = family(
        "caida", "Caída con giro", "trazos",
        kpknKeyframes("fall", "@keyframes fall { 0% { transform: translateY(0px) rotate(0deg); } 100% { transform: translateY(900px) rotate(540deg); opacity: 0; } }") {
            at(0f) { translateY(0f); rotate(0f) }
            at(1f) { translateY(900f); rotate(540f); alpha(0f) }
        },
        "0.4s", 400, "suave", fill = KpknFill.Both,
        members = listOf("fall"),
    )

    val Flotar = family(
        "flotar", "Flota arriba y abajo", "bucles",
        kpknKeyframes("fl", "@keyframes fl { 0%, 100% { transform: translateY(0px); } 50% { transform: translateY(-8px); } }") {
            at(0f) { translateY(0f) }
            at(0.5f) { translateY(-8f) }
            at(1f) { translateY(0f) }
        },
        "0.4s", 400, "suave", fill = KpknFill.Both,
        members = listOf("fl"),
    )

    val GiroEntrada = family(
        "giro-entrada", "Entrada con giro", "trazos",
        kpknKeyframes("popin", "@keyframes popin { 0% { transform: scale(0.2) rotate(-40deg); opacity: 0; } 100% { transform: none; opacity: 1; } }") {
            at(0f) { scale(0.2f); rotate(-40f); alpha(0f) }
            at(1f) { transformNone(); alpha(1f) }
        },
        "0.4s", 400, "suave", fill = KpknFill.Both,
        members = listOf("popin", "spinm"),
    )

    /** Las 33 familias en el orden del catálogo. */
    val all: List<KpknFamily> = listOf(
        EntradaSube, DibujoTrazo, CreceX, EntradaDerecha, FundidoEntrada, Giro, HojaSube, Rebote, Pulso, ApareceEscala,
        BrilloBarrido, Destello, EntradaBaja, BarridoAncho, CreceY, EntradaImplicita, EntradaIzquierda, ReboteAparece, Asentar,
        Latido, Onda, Aviso, BarridoLinea, DespliegueFila, FundidoSalida, HojaBaja, Ignicion, OndaAnillo, PulsoAnillo, Revelado,
        Caida, Flotar, GiroEntrada,
    )

    /** La familia por su nombre del catálogo (`"entrada-sube"`, `"rebote"`, `"latido"`…). */
    fun byName(name: String): KpknFamily = all.first { it.name == name }
}
