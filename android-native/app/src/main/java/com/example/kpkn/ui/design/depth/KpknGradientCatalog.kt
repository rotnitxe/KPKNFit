// GENERADO por tools/depth-kotlin-gen.cjs desde paridad/cimientos.json (43 degradados). No se edita a mano: se regenera.
package com.example.kpkn.ui.design.depth

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Los 43 degradados canónicos del catálogo del lienzo (velo-*, relleno-*, resplandor-*, vidrio-relleno-*, brillo-esqueleto-*, rayas-*, rejilla, fondo-compuesto-*, vineta…).
 * Los cinco con color de módulo o variable (`resplandor-ancho-zona`, `resplandor-zona`, `relleno-horizontal-blanco`, `relleno-horizontal-crema-1`,
 * `relleno-horizontal-cardio`) toman sus colores de [KpknGradientArgs].
 */
enum class KpknGradient private constructor(
    /** Nombre EXACTO del preset en `paridad/cimientos.json`. */
    val catalogName: String,
    private val build: (KpknGradientArgs) -> KpknGradientSpec,
) {
    /** `relleno-crema-suave` · 11 filas · hoja cm-deg-1 (24, 72). */
    RellenoCremaSuave(
        "relleno-crema-suave",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0xFFF2EEE6)), KpknGradStop(Color(0xCCF2EEE6)))),
        ),
    ),

    /** `relleno-crema` · 10 filas · hoja cm-deg-1 (196, 72). */
    RellenoCrema(
        "relleno-crema",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0xFFF2EEE6)), KpknGradStop(Color(0xFFCFC9BF)))),
        ),
    ),

    /** `vidrio-relleno-hoja` · 5 filas · hoja cm-deg-3 (196, 522). */
    VidrioRellenoHoja(
        "vidrio-relleno-hoja",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 140.0f, stops = listOf(KpknGradStop(Color(0xB82C2C2C)), KpknGradStop(Color(0xB0161616)))),
        ),
    ),

    /** `vidrio-relleno-barra` · 5 filas · hoja cm-deg-3 (24, 672). */
    VidrioRellenoBarra(
        "vidrio-relleno-barra",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 150.0f, stops = listOf(KpknGradStop(Color(0x663A3A3A)), KpknGradStop(Color(0x59141414)))),
        ),
    ),

    /** `velo-negro-1` · 5 filas · hoja cm-deg-2 (24, 222). */
    VeloNegro1(
        "velo-negro-1",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0x00000000)), KpknGradStop(Color(0xFF000000)))),
        ),
    ),

    /** `tinte-hacia-modulo-1` · 2 filas · hoja cm-deg-1 (24, 222). */
    TinteHaciaModulo1(
        "tinte-hacia-modulo-1",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 160.0f, stops = listOf(KpknGradStop(Color(0x14F49A6E)), KpknGradStop(Color(0xFF111111), position = 0.6f))),
        ),
    ),

    /** `fondo-compuesto-1` · 1 filas · hoja cm-deg-4 (196, 522). */
    FondoCompuesto1(
        "fondo-compuesto-1",
        KpknGradientSpec(
            KpknGradientLayer.Radial(rx = 0.6f, ry = 0.4f, cx = 0.3f, cy = 0.3f, stops = listOf(KpknGradStop(Color(0xFF5A4A3C)), KpknGradStop(Color(0x00000000), position = 0.7f))),
            KpknGradientLayer.Radial(rx = 0.5f, ry = 0.35f, cx = 0.75f, cy = 0.55f, stops = listOf(KpknGradStop(Color(0xFF3A3F47)), KpknGradStop(Color(0x00000000), position = 0.7f))),
            KpknGradientLayer.Radial(rx = 0.7f, ry = 0.5f, cx = 0.5f, cy = 0.85f, stops = listOf(KpknGradStop(Color(0xFF2A2420)), KpknGradStop(Color(0x00000000), position = 0.7f))),
            KpknGradientLayer.Linear(angleDeg = 160.0f, stops = listOf(KpknGradStop(Color(0xFF2B2621)), KpknGradStop(Color(0xFF121110)))),
        ),
    ),

    /** `velo-negro-2` · 1 filas · hoja cm-deg-2 (24, 372). */
    VeloNegro2(
        "velo-negro-2",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0x00000000), position = 0.35f), KpknGradStop(Color(0xD9000000), position = 0.75f))),
        ),
    ),

    /** `velo-negro-3` · 1 filas · hoja cm-deg-2 (196, 372). */
    VeloNegro3(
        "velo-negro-3",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0x00000000), position = 0.3f), KpknGradStop(Color(0xEE000000), position = 0.68f))),
        ),
    ),

    /** `tinte-hacia-modulo-2` · 1 filas · hoja cm-deg-1 (196, 222). */
    TinteHaciaModulo2(
        "tinte-hacia-modulo-2",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 170.0f, stops = listOf(KpknGradStop(Color(0x0DF7CF73)), KpknGradStop(Color(0xFF0F0F0F), position = 0.55f))),
        ),
    ),

    /** `velo-hoja` · 3 filas · hoja cm-deg-2 (196, 222). */
    VeloHoja(
        "velo-hoja",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0x001B1B1D)), KpknGradStop(Color(0xFF1B1B1D), position = 0.3f))),
        ),
    ),

    /** `brillo-esqueleto-1` · 1 filas · hoja cm-deg-4 (196, 72). */
    BrilloEsqueleto1(
        "brillo-esqueleto-1",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 90.0f, stops = listOf(KpknGradStop(Color(0x0AFFFFFF), position = 0.2f), KpknGradStop(Color(0x1CFFFFFF), position = 0.5f), KpknGradStop(Color(0x0AFFFFFF), position = 0.8f))),
        ),
    ),

    /** `vineta` · 1 filas · hoja cm-deg-3 (196, 222). */
    Vineta(
        "vineta",
        KpknGradientSpec(
            KpknGradientLayer.Radial(rx = 1.2f, ry = 0.9f, cx = 0.5f, cy = 0.55f, stops = listOf(KpknGradStop(Color(0xFF1B1B1D), position = 0.0f), KpknGradStop(Color(0xFF0A0A0A), position = 0.62f), KpknGradStop(Color(0xFF000000), position = 1.0f))),
        ),
    ),

    /** `velo-negro-4` · 1 filas · hoja cm-deg-2 (24, 522). */
    VeloNegro4(
        "velo-negro-4",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0x00000000)), KpknGradStop(Color(0xD9000000), position = 0.72f))),
        ),
    ),

    /** `velo-hoja-largo` · 1 filas · hoja cm-deg-2 (196, 522). */
    VeloHojaLargo(
        "velo-hoja-largo",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0x001B1B1D)), KpknGradStop(Color(0xCC1B1B1D), position = 0.42f), KpknGradStop(Color(0xFF1B1B1D), position = 0.82f))),
        ),
    ),

    /** `velo-hoja-sale` · 1 filas · hoja cm-deg-2 (24, 672). */
    VeloHojaSale(
        "velo-hoja-sale",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0xFF1B1B1D), position = 0.62f), KpknGradStop(Color(0x001B1B1D)))),
        ),
    ),

    /** `resplandor-ancho-zona` · 1 filas · hoja cm-deg-3 (24, 372). */
    ResplandorAnchoZona("resplandor-ancho-zona", { a -> KpknGradientSpec(KpknGradientLayer.Radial(rx = 1.2f, ry = 0.52f, cx = 0.5f, cy = 0.24f, stops = listOf(KpknGradStop(a.zone.copy(alpha = a.zone.alpha * 0.14f)), KpknGradStop(a.zone.copy(alpha = 0f), position = 0.66f)))) }),

    /** `resplandor-zona` · 1 filas · hoja cm-deg-3 (196, 372). */
    ResplandorZona("resplandor-zona", { a -> KpknGradientSpec(KpknGradientLayer.Radial(rx = 0.5f, ry = 0.5f, cx = 0.5f, cy = 0.5f, stops = listOf(KpknGradStop(a.zone), KpknGradStop(a.zone.copy(alpha = 0f))))) }),

    /** `relleno-horizontal-blanco` · 1 filas · hoja cm-deg-1 (24, 372). */
    RellenoHorizontalBlanco("relleno-horizontal-blanco", { a -> KpknGradientSpec(KpknGradientLayer.Linear(angleDeg = 90f, stops = listOf(KpknGradStop(Color(0x88FFFFFF)), KpknGradStop(a.zone)))) }),

    /** `brillo-esqueleto-2` · 1 filas · hoja cm-deg-4 (24, 222). */
    BrilloEsqueleto2(
        "brillo-esqueleto-2",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 100.0f, stops = listOf(KpknGradStop(Color(0xA6FFFFFF), px = 0.0f), KpknGradStop(Color(0xA6FFFFFF), position = 0.38f), KpknGradStop(Color(0xFFFFFFFF), position = 0.5f), KpknGradStop(Color(0xA6FFFFFF), position = 0.62f), KpknGradStop(Color(0xA6FFFFFF), position = 1.0f))),
        ),
    ),

    /** `velo-negro-sale-1` · 1 filas · hoja cm-deg-2 (196, 672). */
    VeloNegroSale1(
        "velo-negro-sale-1",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0xFF000000), position = 0.2f), KpknGradStop(Color(0x00000000)))),
        ),
    ),

    /** `relleno-horizontal-crema-1` · 1 filas · hoja cm-deg-1 (196, 372). */
    RellenoHorizontalCrema1("relleno-horizontal-crema-1", { a -> KpknGradientSpec(KpknGradientLayer.Linear(angleDeg = 90f, stops = listOf(KpknGradStop(a.ink, position = 0f), KpknGradStop(a.ink, position = a.progress), KpknGradStop(Color(0x1FFFFFFF), position = a.progress), KpknGradStop(Color(0x1FFFFFFF), position = 1f)))) }),

    /** `relleno-horizontal-crema-2` · 1 filas · hoja cm-deg-1 (24, 522). */
    RellenoHorizontalCrema2(
        "relleno-horizontal-crema-2",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 90.0f, stops = listOf(KpknGradStop(Color(0xC0F2EEE6)), KpknGradStop(Color(0xFFF2EEE6)))),
        ),
    ),

    /** `fondo-compuesto-2` · 1 filas · hoja cm-deg-4 (24, 672). */
    FondoCompuesto2(
        "fondo-compuesto-2",
        KpknGradientSpec(
            KpknGradientLayer.Radial(rx = 1.2f, ry = 0.7f, cx = 0.28f, cy = 0.18f, stops = listOf(KpknGradStop(Color(0xFF30343A)), KpknGradStop(Color(0x00000000), position = 0.62f))),
            KpknGradientLayer.Radial(rx = 0.9f, ry = 0.6f, cx = 0.8f, cy = 0.9f, stops = listOf(KpknGradStop(Color(0xFF23201C)), KpknGradStop(Color(0x00000000), position = 0.7f))),
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0xFF14161A)), KpknGradStop(Color(0xFF0A0B0C)))),
        ),
    ),

    /** `rejilla` · 1 filas · hoja cm-deg-4 (196, 672). */
    Rejilla(
        "rejilla",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0x0DF2EEE6), px = 1.0f), KpknGradStop(Color(0x00000000), px = 1.0f))),
            KpknGradientLayer.Linear(angleDeg = 90.0f, stops = listOf(KpknGradStop(Color(0x0DF2EEE6), px = 1.0f), KpknGradStop(Color(0x00000000), px = 1.0f))),
            tile = 24.dp,
        ),
    ),

    /** `resplandor-crema` · 1 filas · hoja cm-deg-3 (24, 522). */
    ResplandorCrema(
        "resplandor-crema",
        KpknGradientSpec(
            KpknGradientLayer.Radial(rx = 0.5f, ry = 0.5f, cx = 0.5f, cy = 0.5f, stops = listOf(KpknGradStop(Color(0x2AF2EEE6)), KpknGradStop(Color(0x00000000)))),
        ),
    ),

    /** `tinte-crema-35` · 1 filas · hoja cm-deg-1 (196, 522). */
    TinteCrema35(
        "tinte-crema-35",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0x59F2EEE6)), KpknGradStop(Color(0x59F2EEE6)))),
        ),
    ),

    /** `tinte-crema-70` · 1 filas · hoja cm-deg-1 (24, 672). */
    TinteCrema70(
        "tinte-crema-70",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0xB3F2EEE6)), KpknGradStop(Color(0xB3F2EEE6)))),
        ),
    ),

    /** `brillo-esqueleto-3` · 1 filas · hoja cm-deg-4 (196, 222). */
    BrilloEsqueleto3(
        "brillo-esqueleto-3",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 100.0f, stops = listOf(KpknGradStop(Color(0xFF0F0F0F), position = 0.3f), KpknGradStop(Color(0xFF1A1A1A), position = 0.5f), KpknGradStop(Color(0xFF0F0F0F), position = 0.7f))),
        ),
    ),

    /** `velo-negro-sale-2` · 1 filas · hoja cm-deg-3 (24, 72). */
    VeloNegroSale2(
        "velo-negro-sale-2",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0xFF000000), position = 0.52f), KpknGradStop(Color(0x00000000)))),
        ),
    ),

    /** `brillo-esqueleto-4` · 1 filas · hoja cm-deg-4 (24, 372). */
    BrilloEsqueleto4(
        "brillo-esqueleto-4",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 90.0f, stops = listOf(KpknGradStop(Color(0xFF161616)), KpknGradStop(Color(0xFF222222), position = 0.5f), KpknGradStop(Color(0xFF161616)))),
        ),
    ),

    /** `velo-negro-sale-3` · 1 filas · hoja cm-deg-3 (196, 72). */
    VeloNegroSale3(
        "velo-negro-sale-3",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 0.0f, stops = listOf(KpknGradStop(Color(0xFF000000), position = 0.58f), KpknGradStop(Color(0x00000000)))),
        ),
    ),

    /** `relleno-modulo` · 1 filas · hoja cm-deg-1 (196, 672). */
    RellenoModulo(
        "relleno-modulo",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0xFF0C0C0C)), KpknGradStop(Color(0xFF000000)))),
        ),
    ),

    /** `vidrio-relleno-tecnica` · 1 filas · hoja cm-deg-3 (196, 672). */
    VidrioRellenoTecnica(
        "vidrio-relleno-tecnica",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 160.0f, stops = listOf(KpknGradStop(Color(0xC42A2A2D)), KpknGradStop(Color(0xD6141416)))),
        ),
    ),

    /** `rayas-blanco` · 1 filas · hoja cm-deg-5 (24, 72). */
    RayasBlanco(
        "rayas-blanco",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 135.0f, stops = listOf(KpknGradStop(Color(0x30FFFFFF), px = 0.0f), KpknGradStop(Color(0x30FFFFFF), px = 3.0f), KpknGradStop(Color(0x10FFFFFF), px = 3.0f), KpknGradStop(Color(0x10FFFFFF), px = 6.0f)), repeating = true),
        ),
    ),

    /** `rayas-crema` · 1 filas · hoja cm-deg-5 (196, 72). */
    RayasCrema(
        "rayas-crema",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 135.0f, stops = listOf(KpknGradStop(Color(0xFFF2EEE6), px = 0.0f), KpknGradStop(Color(0xFFF2EEE6), px = 3.0f), KpknGradStop(Color(0xAAF2EEE6), px = 3.0f), KpknGradStop(Color(0xAAF2EEE6), px = 6.0f)), repeating = true),
        ),
    ),

    /** `relleno-cardio` · 1 filas · hoja cm-deg-2 (24, 72). */
    RellenoCardio(
        "relleno-cardio",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0xFF8FB2FF)), KpknGradStop(Color(0xFF6E8FD0)))),
        ),
    ),

    /** `rayas-energia` · 1 filas · hoja cm-deg-5 (24, 222). */
    RayasEnergia(
        "rayas-energia",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 135.0f, stops = listOf(KpknGradStop(Color(0xFFF7CF73), px = 0.0f), KpknGradStop(Color(0xFFF7CF73), px = 3.0f), KpknGradStop(Color(0x55F7CF73), px = 3.0f), KpknGradStop(Color(0x55F7CF73), px = 6.0f)), repeating = true),
        ),
    ),

    /** `aviso-relleno-error` · 1 filas · hoja cm-deg-4 (24, 72). */
    AvisoRellenoError(
        "aviso-relleno-error",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 160.0f, stops = listOf(KpknGradStop(Color(0xD83A2423)), KpknGradStop(Color(0xE01C1212)))),
        ),
    ),

    /** `franja-crema` · 1 filas · hoja cm-deg-4 (196, 372). */
    FranjaCrema(
        "franja-crema",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0x00000000)), KpknGradStop(Color(0xFFF2EEE6)), KpknGradStop(Color(0x00000000)))),
        ),
    ),

    /** `relleno-horizontal-cardio` · 1 filas · hoja cm-deg-2 (196, 72). */
    RellenoHorizontalCardio("relleno-horizontal-cardio", { a -> KpknGradientSpec(KpknGradientLayer.Linear(angleDeg = 90f, stops = listOf(KpknGradStop(a.cardio), KpknGradStop(a.energy)))) }),

    /** `velo-crema-largo` · 1 filas · hoja cm-deg-3 (24, 222). */
    VeloCremaLargo(
        "velo-crema-largo",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 180.0f, stops = listOf(KpknGradStop(Color(0x00000000)), KpknGradStop(Color(0x33F2EEE6), position = 0.7f), KpknGradStop(Color(0x80F2EEE6)))),
        ),
    ),

    /** `brillo-esqueleto-5` · 1 filas · hoja cm-deg-4 (24, 522). */
    BrilloEsqueleto5(
        "brillo-esqueleto-5",
        KpknGradientSpec(
            KpknGradientLayer.Linear(angleDeg = 100.0f, stops = listOf(KpknGradStop(Color(0x0DFFFFFF), position = 0.3f), KpknGradStop(Color(0x1CFFFFFF), position = 0.5f), KpknGradStop(Color(0x0DFFFFFF), position = 0.7f))),
        ),
    );

    private constructor(catalogName: String, spec: KpknGradientSpec) : this(catalogName, { _ -> spec })

    /** La spec del degradado; los dinámicos usan [args] (por defecto, los colores de muestra de la hoja del lienzo). */
    fun spec(args: KpknGradientArgs = KpknGradientArgs()): KpknGradientSpec = build(args)

    companion object {
        fun fromCatalogName(name: String): KpknGradient? = entries.firstOrNull { it.catalogName == name }
    }
}
