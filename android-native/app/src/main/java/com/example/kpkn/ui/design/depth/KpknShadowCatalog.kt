// GENERADO por tools/depth-kotlin-gen.cjs desde paridad/cimientos.json (65 sombras y 3 sombras de texto). No se edita a mano: se regenera.
package com.example.kpkn.ui.design.depth

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Las 65 sombras del catálogo del lienzo (caída, anillo, interior, resplandor y la «ninguna»). Cada caso es el valor canónico del
 * preset (el del miembro más usado); la tolerancia del catálogo (±2 dp, ±4 dp de desenfoque, ±0,067 de alfa) solo sirve para agrupar: para dibujar
 * el VALOR EXACTO de una fila de la matriz, parte de [spec] y cámbialo (`preset.spec.copyLayer(0) { it.copy(y = 10.dp) }`).
 * Los casos de color dinámico (`sampleColor != null`) reciben el color por parámetro en [spec].
 */
enum class KpknShadow(
    /** Nombre EXACTO del preset en `paridad/cimientos.json`. */
    val catalogName: String,
    val kind: KpknShadowKind,
    /** Color de muestra de la hoja del lienzo (`--c` o `--zc`); null si el preset no lleva color dinámico. */
    val sampleColor: Color?,
    vararg canonicalLayers: KpknShadowLayer,
) {
    /** `subrayado-musculo` · 10 filas · `rgb(244, 154, 110) 0px -2px inset` · hoja cm-som-interior-1 (24, 72). */
    SubrayadoMusculo(
        "subrayado-musculo", KpknShadowKind.Interior, null,
        KpknShadowLayer(y = (-2).dp, color = Color(0xFFF49A6E), inset = true),
    ),

    /** `hoja` · 11 filas · `rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset` · hoja cm-som-caida-2 (110, 132). */
    Hoja(
        "hoja", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = (-14).dp, blur = 44.dp, color = Color(0xCC000000)),
        KpknShadowLayer(y = 1.dp, color = Color(0x10FFFFFF), inset = true),
    ),

    /** `isla-vidrio` · 15 filas · `rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset` · hoja cm-som-caida-2 (110, 288). */
    IslaVidrio(
        "isla-vidrio", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 8.dp, blur = 28.dp, color = Color(0xAA000000)),
        KpknShadowLayer(y = 1.dp, color = Color(0x14FFFFFF), inset = true),
    ),

    /** `subrayado-hecho` · 6 filas · `rgba(67, 209, 140, 0.533) 0px -2px inset` · hoja cm-som-interior-1 (196, 72). */
    SubrayadoHecho(
        "subrayado-hecho", KpknShadowKind.Interior, null,
        KpknShadowLayer(y = (-2).dp, color = Color(0x8843D18C), inset = true),
    ),

    /** `barra-vidrio` · 5 filas · `rgba(0, 0, 0, 0.467) 0px 8px 24px, rgba(255, 255, 255, 0.11) 0px 1px inset` · hoja cm-som-caida-2 (110, 450). */
    BarraVidrio(
        "barra-vidrio", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 8.dp, blur = 24.dp, color = Color(0x77000000)),
        KpknShadowLayer(y = 1.dp, color = Color(0x1CFFFFFF), inset = true),
    ),

    /** `anillo-actual` · 4 filas · `0 0 0 2px var(--ink)` · hoja cm-som-anillo-1 (24, 72). */
    AnilloActual(
        "anillo-actual", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 2.dp, color = Color(0xFFF2EEE6)),
    ),

    /** `anillo-doble-actual` · 2 filas · `rgb(0, 0, 0) 0px 0px 0px 2px, rgba(242, 238, 230, 0.667) 0px 0px 0px 3px` · hoja cm-som-anillo-1 (196, 72). */
    AnilloDobleActual(
        "anillo-doble-actual", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 2.dp, color = Color(0xFF000000)),
        KpknShadowLayer(spread = 3.dp, color = Color(0xAAF2EEE6)),
    ),

    /** `interior-hecho-1` · 2 filas · `rgba(67, 209, 140, 0.333) 0px 0px 0px 1px inset` · hoja cm-som-interior-1 (24, 222). */
    InteriorHecho1(
        "interior-hecho-1", KpknShadowKind.Interior, null,
        KpknShadowLayer(spread = 1.dp, color = Color(0x5543D18C), inset = true),
    ),

    /** `nota` · 2 filas · `rgba(0, 0, 0, 0.533) 0px 4px 12px` · hoja cm-som-caida-2 (110, 608). */
    Nota(
        "nota", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 4.dp, blur = 12.dp, color = Color(0x88000000)),
    ),

    /** `aureola-foco` · 3 filas · `rgba(242, 238, 230, 0.08) 0px 0px 0px 4px` · hoja cm-som-anillo-1 (24, 222). */
    AureolaFoco(
        "aureola-foco", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 4.dp, color = Color(0x14F2EEE6)),
    ),

    /** `flotante` · 5 filas · `rgba(0, 0, 0, 0.667) 0px 8px 28px` · hoja cm-som-caida-3 (110, 94). */
    Flotante(
        "flotante", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 8.dp, blur = 28.dp, color = Color(0xAA000000)),
    ),

    /** `modal` · 3 filas · `rgba(0, 0, 0, 0.8) 0px 20px 60px` · hoja cm-som-caida-3 (110, 276). */
    Modal(
        "modal", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 20.dp, blur = 60.dp, color = Color(0xCC000000)),
    ),

    /** `borde-interior` · 9 filas · `rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset` · hoja cm-som-interior-1 (196, 222). */
    BordeInterior(
        "borde-interior", KpknShadowKind.Interior, null,
        KpknShadowLayer(spread = 1.dp, color = Color(0x14FFFFFF), inset = true),
    ),

    /** `corte-hoja` · 6 filas · `rgb(27, 27, 29) 0px 0px 0px 2px` · hoja cm-som-anillo-1 (196, 222). */
    CorteHoja(
        "corte-hoja", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 2.dp, color = Color(0xFF1B1B1D)),
    ),

    /** `resplandor-crema-12` · 2 filas · `rgba(242, 238, 230, 0.333) 0px 0px 12px` · hoja cm-som-resplandor-1 (24, 72). */
    ResplandorCrema12(
        "resplandor-crema-12", KpknShadowKind.Resplandor, null,
        KpknShadowLayer(blur = 12.dp, color = Color(0x55F2EEE6)),
    ),

    /** `borde-exterior` · 3 filas · `rgba(255, 255, 255, 0.11) 0px 0px 0px 1px` · hoja cm-som-anillo-1 (24, 372). */
    BordeExterior(
        "borde-exterior", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 1.dp, color = Color(0x1CFFFFFF)),
    ),

    /** `historia` · 2 filas · `rgba(0, 0, 0, 0.8) 0px 20px 50px, rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset` · hoja cm-som-caida-3 (110, 492). */
    Historia(
        "historia", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 20.dp, blur = 50.dp, color = Color(0xCC000000)),
        KpknShadowLayer(spread = 1.dp, color = Color(0x14FFFFFF), inset = true),
    ),

    /** `pie-sube` · 1 filas · `rgba(0, 0, 0, 0.733) 0px -10px 30px` · hoja cm-som-caida-3 (110, 708). */
    PieSube(
        "pie-sube", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = (-10).dp, blur = 30.dp, color = Color(0xBB000000)),
    ),

    /** `menu` · 2 filas · `rgba(0, 0, 0, 0.8) 0px 14px 40px` · hoja cm-som-caida-4 (110, 100). */
    Menu(
        "menu", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 14.dp, blur = 40.dp, color = Color(0xCC000000)),
    ),

    /** `aviso` · 3 filas · `rgba(0, 0, 0, 0.8) 0px 12px 34px` · hoja cm-som-caida-4 (110, 282). */
    Aviso(
        "aviso", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 12.dp, blur = 34.dp, color = Color(0xCC000000)),
    ),

    /** `interior-crema-1` · 1 filas · `inset 0 0 0 1px var(--ink)` · hoja cm-som-interior-1 (24, 372). */
    InteriorCrema1(
        "interior-crema-1", KpknShadowKind.Interior, null,
        KpknShadowLayer(spread = 1.dp, color = Color(0xFFF2EEE6), inset = true),
    ),

    /** `interior-crema-1p5-1` · 1 filas · `rgba(242, 238, 230, 0.533) 0px 0px 0px 1.5px inset` · hoja cm-som-interior-1 (196, 372). */
    InteriorCrema1p51(
        "interior-crema-1p5-1", KpknShadowKind.Interior, null,
        KpknShadowLayer(spread = 1.5.dp, color = Color(0x88F2EEE6), inset = true),
    ),

    /** `interior-blanco-1-tenue` · 3 filas · `rgba(255, 255, 255, 0.165) 0px 0px 0px 1px inset` · hoja cm-som-interior-1 (24, 522). */
    InteriorBlanco1Tenue(
        "interior-blanco-1-tenue", KpknShadowKind.Interior, null,
        KpknShadowLayer(spread = 1.dp, color = Color(0x2AFFFFFF), inset = true),
    ),

    /** `interior-zona-1p5` · 1 filas · `inset 0 0 0 1.5px var(--c)` · hoja cm-som-interior-1 (196, 522). */
    InteriorZona1p5(
        "interior-zona-1p5", KpknShadowKind.Interior, Color(0xFF8FB2FF),
        KpknShadowLayer(spread = 1.5.dp, color = Color.Unspecified, inset = true, dynamicAlpha = 1f),
    ),

    /** `borde-interior-fuerte` · 1 filas · `rgba(255, 255, 255, 0.25) 0px 0px 0px 1px inset` · hoja cm-som-interior-1 (24, 672). */
    BordeInteriorFuerte(
        "borde-interior-fuerte", KpknShadowKind.Interior, null,
        KpknShadowLayer(spread = 1.dp, color = Color(0x40FFFFFF), inset = true),
    ),

    /** `dialogo-1` · 1 filas · `rgba(0, 0, 0, 0.933) 0px 24px 70px, rgba(255, 255, 255, 0.08) 0px 1px inset` · hoja cm-som-caida-4 (110, 480). */
    Dialogo1(
        "dialogo-1", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 24.dp, blur = 70.dp, color = Color(0xEE000000)),
        KpknShadowLayer(y = 1.dp, color = Color(0x14FFFFFF), inset = true),
    ),

    /** `resplandor-zona-10` · 1 filas · `0 0 10px color-mix(in srgb,var(--zc) 75%,transparent)` · hoja cm-som-resplandor-1 (196, 72). */
    ResplandorZona10(
        "resplandor-zona-10", KpknShadowKind.Resplandor, Color(0xFF87C9A5),
        KpknShadowLayer(blur = 10.dp, color = Color.Unspecified, dynamicAlpha = 0.75f),
    ),

    /** `resplandor-zona-12` · 1 filas · `0 0 12px var(--zc)` · hoja cm-som-resplandor-1 (24, 222). */
    ResplandorZona12(
        "resplandor-zona-12", KpknShadowKind.Resplandor, Color(0xFF87C9A5),
        KpknShadowLayer(blur = 12.dp, color = Color.Unspecified, dynamicAlpha = 1f),
    ),

    /** `resplandor-zona-28` · 1 filas · `0 0 28px color-mix(in srgb,var(--wc,var(--zc)) 14%,transparent)` · hoja cm-som-resplandor-2 (110, 102). */
    ResplandorZona28(
        "resplandor-zona-28", KpknShadowKind.Resplandor, Color(0xFF87C9A5),
        KpknShadowLayer(blur = 28.dp, color = Color.Unspecified, dynamicAlpha = 0.14f),
    ),

    /** `anillo-negro-4` · 2 filas · `rgb(0, 0, 0) 0px 0px 0px 4px` · hoja cm-som-anillo-1 (196, 372). */
    AnilloNegro4(
        "anillo-negro-4", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 4.dp, color = Color(0xFF000000)),
    ),

    /** `resplandor-zona-14-capas` · 1 filas · `0 0 0 4px #000,0 0 14px var(--c)` · hoja cm-som-resplandor-1 (196, 222). */
    ResplandorZona14Capas(
        "resplandor-zona-14-capas", KpknShadowKind.Resplandor, Color(0xFF8FB2FF),
        KpknShadowLayer(spread = 4.dp, color = Color(0xFF000000)),
        KpknShadowLayer(blur = 14.dp, color = Color.Unspecified, dynamicAlpha = 1f),
    ),

    /** `caida-4-18` · 1 filas · `rgba(0, 0, 0, 0.533) 0px 4px 18px` · hoja cm-som-caida-4 (110, 700). */
    Caida418(
        "caida-4-18", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 4.dp, blur = 18.dp, color = Color(0x88000000)),
    ),

    /** `levantado` · 1 filas · `rgba(0, 0, 0, 0.867) 0px 24px 48px, rgba(255, 255, 255, 0.15) 0px 0px 0px 1px` · hoja cm-som-caida-5 (110, 98). */
    Levantado(
        "levantado", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 24.dp, blur = 48.dp, color = Color(0xDD000000)),
        KpknShadowLayer(spread = 1.dp, color = Color(0x26FFFFFF)),
    ),

    /** `ninguna` · 2 filas · `rgba(0, 0, 0, 0) 0px 0px 0px 0px` · hoja cm-som-nula-1 (24, 72). */
    Ninguna(
        "ninguna", KpknShadowKind.Nula, null,
        KpknShadowLayer(color = Color(0x00000000)),
    ),

    /** `popover` · 2 filas · `rgba(0, 0, 0, 0.867) 0px 18px 44px` · hoja cm-som-caida-5 (110, 302). */
    Popover(
        "popover", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 18.dp, blur = 44.dp, color = Color(0xDD000000)),
    ),

    /** `anillo-energia-1p5` · 1 filas · `0 0 0 1.5px var(--ene)` · hoja cm-som-anillo-1 (24, 522). */
    AnilloEnergia1p5(
        "anillo-energia-1p5", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 1.5.dp, color = Color(0xFFF7CF73)),
    ),

    /** `asa-deslizador` · 1 filas · `rgba(0, 0, 0, 0.667) 0px 2px 8px` · hoja cm-som-caida-1 (24, 72). */
    AsaDeslizador(
        "asa-deslizador", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 2.dp, blur = 8.dp, color = Color(0xAA000000)),
    ),

    /** `caida-10-22` · 1 filas · `rgba(0, 0, 0, 0.667) 0px 10px 22px` · hoja cm-som-caida-5 (110, 490). */
    Caida1022(
        "caida-10-22", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 10.dp, blur = 22.dp, color = Color(0xAA000000)),
    ),

    /** `anillo-foco` · 2 filas · `rgba(242, 238, 230, 0.333) 0px 0px 0px 2px` · hoja cm-som-anillo-1 (196, 522). */
    AnilloFoco(
        "anillo-foco", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 2.dp, color = Color(0x55F2EEE6)),
    ),

    /** `caida-6-16-capas-1` · 1 filas · `rgba(0, 0, 0, 0.6) 0px 6px 16px, rgba(255, 255, 255, 0.133) 0px 0px 0px 1px` · hoja cm-som-caida-5 (110, 648). */
    Caida616Capas1(
        "caida-6-16-capas-1", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 6.dp, blur = 16.dp, color = Color(0x99000000)),
        KpknShadowLayer(spread = 1.dp, color = Color(0x22FFFFFF)),
    ),

    /** `interior-peligro-1` · 1 filas · `rgba(255, 155, 146, 0.4) 0px 0px 0px 1px inset` · hoja cm-som-interior-1 (196, 672). */
    InteriorPeligro1(
        "interior-peligro-1", KpknShadowKind.Interior, null,
        KpknShadowLayer(spread = 1.dp, color = Color(0x66FF9B92), inset = true),
    ),

    /** `caida-6-16-capas-2` · 1 filas · `rgba(0, 0, 0, 0.6) 0px 6px 16px, rgba(255, 255, 255, 0.133) 0px 0px 0px 1px, rgb(244, 154, 110) 0px 2px 0px` · hoja cm-som-caida-6 (110, 94). */
    Caida616Capas2(
        "caida-6-16-capas-2", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 6.dp, blur = 16.dp, color = Color(0x99000000)),
        KpknShadowLayer(spread = 1.dp, color = Color(0x22FFFFFF)),
        KpknShadowLayer(y = 2.dp, color = Color(0xFFF49A6E)),
    ),

    /** `caida-2-8-capas` · 1 filas · `rgba(0, 0, 0, 0.667) 0px 2px 8px, rgb(17, 17, 17) 0px 0px 0px 4px` · hoja cm-som-caida-1 (196, 72). */
    Caida28Capas(
        "caida-2-8-capas", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 2.dp, blur = 8.dp, color = Color(0xAA000000)),
        KpknShadowLayer(spread = 4.dp, color = Color(0xFF111111)),
    ),

    /** `caida-8-20-capas` · 1 filas · `rgba(0, 0, 0, 0.8) 0px 8px 20px, rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset` · hoja cm-som-caida-6 (110, 242). */
    Caida820Capas(
        "caida-8-20-capas", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 8.dp, blur = 20.dp, color = Color(0xCC000000)),
        KpknShadowLayer(spread = 1.dp, color = Color(0x14FFFFFF), inset = true),
    ),

    /** `interior-blanco-1p5` · 1 filas · `rgb(255, 255, 255) 0px 0px 0px 1.5px inset` · hoja cm-som-interior-2 (24, 72). */
    InteriorBlanco1p5(
        "interior-blanco-1p5", KpknShadowKind.Interior, null,
        KpknShadowLayer(spread = 1.5.dp, color = Color(0xFFFFFFFF), inset = true),
    ),

    /** `interior-musculo-1` · 1 filas · `rgba(244, 154, 110, 0.333) 0px 0px 0px 1px inset` · hoja cm-som-interior-2 (196, 72). */
    InteriorMusculo1(
        "interior-musculo-1", KpknShadowKind.Interior, null,
        KpknShadowLayer(spread = 1.dp, color = Color(0x55F49A6E), inset = true),
    ),

    /** `caida-10-28-capas` · 1 filas · `rgba(0, 0, 0, 0.7) 0px 10px 28px, rgba(0, 0, 0, 0.5) 0px 2px 6px` · hoja cm-som-caida-6 (110, 396). */
    Caida1028Capas(
        "caida-10-28-capas", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 10.dp, blur = 28.dp, color = Color(0xB3000000)),
        KpknShadowLayer(y = 2.dp, blur = 6.dp, color = Color(0x80000000)),
    ),

    /** `caida-4-14` · 1 filas · `rgba(0, 0, 0, 0.35) 0px 4px 14px` · hoja cm-som-caida-6 (110, 560). */
    Caida414(
        "caida-4-14", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 4.dp, blur = 14.dp, color = Color(0x59000000)),
    ),

    /** `resplandor-crema-10` · 1 filas · `rgba(242, 238, 230, 0.22) 0px 0px 10px` · hoja cm-som-resplandor-1 (24, 372). */
    ResplandorCrema10(
        "resplandor-crema-10", KpknShadowKind.Resplandor, null,
        KpknShadowLayer(blur = 10.dp, color = Color(0x38F2EEE6)),
    ),

    /** `resplandor-crema-12-capas` · 1 filas · `rgb(242, 238, 230) 0px 0px 0px 1.5px inset, rgba(242, 238, 230, 0.3) 0px 0px 12px` · hoja cm-som-resplandor-1 (196, 372). */
    ResplandorCrema12Capas(
        "resplandor-crema-12-capas", KpknShadowKind.Resplandor, null,
        KpknShadowLayer(spread = 1.5.dp, color = Color(0xFFF2EEE6), inset = true),
        KpknShadowLayer(blur = 12.dp, color = Color(0x4DF2EEE6)),
    ),

    /** `anillo-blanco-3-suave` · 1 filas · `rgba(255, 255, 255, 0.08) 0px 0px 0px 3px` · hoja cm-som-anillo-1 (24, 672). */
    AnilloBlanco3Suave(
        "anillo-blanco-3-suave", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 3.dp, color = Color(0x14FFFFFF)),
    ),

    /** `caida-16-60` · 1 filas · `rgba(0, 0, 0, 0.867) 0px 16px 60px` · hoja cm-som-caida-7 (110, 118). */
    Caida1660(
        "caida-16-60", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 16.dp, blur = 60.dp, color = Color(0xDD000000)),
    ),

    /** `interior-crema-1p5-2` · 1 filas · `rgba(242, 238, 230, 0.3) 0px 0px 0px 1.5px inset` · hoja cm-som-interior-2 (24, 222). */
    InteriorCrema1p52(
        "interior-crema-1p5-2", KpknShadowKind.Interior, null,
        KpknShadowLayer(spread = 1.5.dp, color = Color(0x4DF2EEE6), inset = true),
    ),

    /** `dialogo-2` · 1 filas · `rgba(0, 0, 0, 0.867) 0px 24px 60px, rgba(255, 255, 255, 0.063) 0px 1px inset` · hoja cm-som-caida-7 (110, 336). */
    Dialogo2(
        "dialogo-2", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 24.dp, blur = 60.dp, color = Color(0xDD000000)),
        KpknShadowLayer(y = 1.dp, color = Color(0x10FFFFFF), inset = true),
    ),

    /** `anillo-aviso` · 2 filas · `rgba(247, 207, 115, 0.15) 0px 0px 0px 3px` · hoja cm-som-anillo-1 (196, 672). */
    AnilloAviso(
        "anillo-aviso", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 3.dp, color = Color(0x26F7CF73)),
    ),

    /** `anillo-hecho` · 1 filas · `rgba(67, 209, 140, 0.15) 0px 0px 0px 3px` · hoja cm-som-anillo-2 (24, 72). */
    AnilloHecho(
        "anillo-hecho", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 3.dp, color = Color(0x2643D18C)),
    ),

    /** `anillo-error` · 1 filas · `rgba(255, 155, 146, 0.15) 0px 0px 0px 3px` · hoja cm-som-anillo-2 (196, 72). */
    AnilloError(
        "anillo-error", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 3.dp, color = Color(0x26FF9B92)),
    ),

    /** `resplandor-crema-8` · 1 filas · `rgba(242, 238, 230, 0.6) 0px 0px 8px` · hoja cm-som-resplandor-1 (24, 522). */
    ResplandorCrema8(
        "resplandor-crema-8", KpknShadowKind.Resplandor, null,
        KpknShadowLayer(blur = 8.dp, color = Color(0x99F2EEE6)),
    ),

    /** `caida-18-50` · 1 filas · `rgba(0, 0, 0, 0.867) 0px 18px 50px` · hoja cm-som-caida-7 (110, 558). */
    Caida1850(
        "caida-18-50", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 18.dp, blur = 50.dp, color = Color(0xDD000000)),
    ),

    /** `dialogo-vidrio` · 1 filas · `rgba(255, 255, 255, 0.11) 0px 0px 0px 1px, rgb(0, 0, 0) 0px 20px 60px` · hoja cm-som-caida-8 (110, 114). */
    DialogoVidrio(
        "dialogo-vidrio", KpknShadowKind.Caida, null,
        KpknShadowLayer(spread = 1.dp, color = Color(0x1CFFFFFF)),
        KpknShadowLayer(y = 20.dp, blur = 60.dp, color = Color(0xFF000000)),
    ),

    /** `resplandor-crema-18` · 1 filas · `rgba(242, 238, 230, 0.333) 0px 0px 18px 2px` · hoja cm-som-resplandor-2 (110, 256). */
    ResplandorCrema18(
        "resplandor-crema-18", KpknShadowKind.Resplandor, null,
        KpknShadowLayer(blur = 18.dp, spread = 2.dp, color = Color(0x55F2EEE6)),
    ),

    /** `anillo-crema-4-suave` · 1 filas · `rgba(242, 238, 230, 0.2) 0px 0px 0px 4px` · hoja cm-som-anillo-2 (24, 222). */
    AnilloCrema4Suave(
        "anillo-crema-4-suave", KpknShadowKind.Anillo, null,
        KpknShadowLayer(spread = 4.dp, color = Color(0x33F2EEE6)),
    ),

    /** `resplandor-energia-22-capas` · 1 filas · `rgba(0, 0, 0, 0.667) 0px 0px 0px 1px, rgba(247, 207, 115, 0.333) 0px 0px 22px, rgba(0, 0, 0, 0.467) 0px 0px 0px 1px inset` · hoja cm-som-resplandor-2 (110, 404). */
    ResplandorEnergia22Capas(
        "resplandor-energia-22-capas", KpknShadowKind.Resplandor, null,
        KpknShadowLayer(spread = 1.dp, color = Color(0xAA000000)),
        KpknShadowLayer(blur = 22.dp, color = Color(0x55F7CF73)),
        KpknShadowLayer(spread = 1.dp, color = Color(0x77000000), inset = true),
    ),

    /** `caida-8-28` · 1 filas · `rgba(0, 0, 0, 0.8) 0px 8px 28px` · hoja cm-som-caida-8 (110, 320). */
    Caida828(
        "caida-8-28", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 8.dp, blur = 28.dp, color = Color(0xCC000000)),
    ),

    /** `caida-24-70` · 1 filas · `rgba(0, 0, 0, 0.933) 0px 24px 70px` · hoja cm-som-caida-8 (110, 508). */
    Caida2470(
        "caida-24-70", KpknShadowKind.Caida, null,
        KpknShadowLayer(y = 24.dp, blur = 70.dp, color = Color(0xEE000000)),
    );

    /** Capas canónicas, en el orden del CSS (la primera se pinta encima). */
    val canonical: List<KpknShadowLayer> = canonicalLayers.toList()

    /** True si el preset lleva un color del módulo (`--c`, `--zc`, `--wc`) que se pasa por parámetro. */
    val isDynamic: Boolean get() = sampleColor != null

    /** La spec canónica; en los dinámicos usa el color de muestra de la hoja. */
    val spec: KpknShadowSpec get() = spec(Color.Unspecified)

    /** La spec con el [color] del módulo en las capas dinámicas (`Color.Unspecified` = el de muestra). Los presets fijos lo ignoran. */
    fun spec(color: Color): KpknShadowSpec = KpknShadowSpec(canonical).withDynamicColor(color.takeIf { it != Color.Unspecified } ?: sampleColor)

    companion object {
        /** Busca un preset por su nombre exacto del catálogo. */
        fun fromCatalogName(name: String): KpknShadow? = entries.firstOrNull { it.catalogName == name }
    }
}

/**
 * Las 3 sombras de texto (`text-shadow`) del catálogo. Se usan con [KpknShadowedText] o con [KpknTextShadow.style].
 */
enum class KpknTextShadow(
    val catalogName: String,
    vararg canonicalLayers: KpknShadowLayer,
) {
    /** `texto-2-26-capas` · `rgb(0, 0, 0) 0px 2px 26px, rgba(0, 0, 0, 0.667) 0px 0px 6px` · hoja cm-som-texto-1 (110, 98). */
    Texto226Capas(
        "texto-2-26-capas",
        KpknShadowLayer(y = 2.dp, blur = 26.dp, color = Color(0xFF000000)),
        KpknShadowLayer(blur = 6.dp, color = Color(0xAA000000)),
    ),

    /** `texto-1-12` · `rgb(0, 0, 0) 0px 1px 12px` · hoja cm-som-texto-1 (110, 252). */
    Texto112(
        "texto-1-12",
        KpknShadowLayer(y = 1.dp, blur = 12.dp, color = Color(0xFF000000)),
    ),

    /** `texto-resplandor-negro-40` · `rgb(0, 0, 0) 0px 0px 40px` · hoja cm-som-texto-1 (110, 411). */
    TextoResplandorNegro40(
        "texto-resplandor-negro-40",
        KpknShadowLayer(blur = 40.dp, color = Color(0xFF000000)),
    );

    /** Capas del CSS (la primera se pinta encima). */
    val layers: List<KpknShadowLayer> = canonicalLayers.toList()
}
