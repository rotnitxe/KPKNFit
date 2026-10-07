package com.example.kpkn.screens.onboarding.welcome

import kotlin.math.max

/*
 * Geometría de la escena de Entreno, en las unidades del lienzo lógico (300 × 620).
 *
 * Es la ÚNICA fuente de las posiciones: la línea de tiempo (a dónde apunta el dedo) y el dibujo (dónde está cada
 * control) leen de aquí, así que el dedo no puede apuntar a un sitio distinto del que se dibuja. Las posiciones
 * «locales» (`…L`) se miden desde el borde superior de su hoja; sumando la `y` de la hoja salen las absolutas.
 */

/** Rectángulo del lienzo lógico. */
internal data class EntrenoRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w: Float get() = r - l
    val h: Float get() = b - t
    val cx: Float get() = (l + r) * 0.5f
    val cy: Float get() = (t + b) * 0.5f
    fun contains(x: Float, y: Float): Boolean = x >= l && x <= r && y >= t && y <= b
    fun shifted(dy: Float): EntrenoRect = EntrenoRect(l, t + dy, r, b + dy)
    fun inflated(d: Float): EntrenoRect = EntrenoRect(l - d, t - d, r + d, b + d)
}

/** Un ejercicio del catálogo simulado del selector. */
internal class EntrenoCatalogItem(val name: String, val detail: String)

internal object EntrenoGeo {
    const val W = 300f
    const val H = 620f

    /** Alto de la barra de estado simulada. */
    const val statusH = 28f

    // ------------------------------------------------------------------ Inicio (pestaña Entreno)
    val homeCard = EntrenoRect(16f, 160f, 284f, 226f)
    const val homeTapX = 248f
    const val homeTapY = 194f
    val nav = EntrenoRect(14f, 544f, 286f, 600f)
    const val weekTop = 104f
    val sessionTop = floatArrayOf(262f, 328f, 394f)
    const val sessionH = 58f

    // ------------------------------------------------------------------ Hoja del editor
    /** `y` de la hoja en reposo y fuera de la pantalla. */
    const val sheetRest = 46f
    const val sheetHidden = 640f

    /** Campo del nombre (local a la hoja). */
    val nameFieldL = EntrenoRect(16f, 46f, 284f, 102f)

    /** Tarjeta con los tres contadores (local a la hoja). */
    val statsCardL = EntrenoRect(16f, 114f, 284f, 176f)
    const val rowsTopL = 190f
    const val rowPitch = 74f
    const val rowH = 64f
    const val addH = 44f

    fun rowTopL(i: Int): Float = rowsTopL + rowPitch * i

    /** `y` local del botón «Añadir ejercicio» con [rows] filas (continuo mientras entran). */
    fun addTopL(rows: Float): Float = rowsTopL + rowPitch * max(1f, rows)

    /** El botón principal de abajo (editor y selector comparten sitio: el dedo no se mueve entre los dos). */
    val cta = EntrenoRect(16f, 540f, 284f, 592f)

    /** Botón «Añadir ejercicio» en pantalla, con la hoja en reposo. */
    fun addButtonAbs(rows: Float): EntrenoRect =
        EntrenoRect(16f, sheetRest + addTopL(rows), 284f, sheetRest + addTopL(rows) + addH)

    // ------------------------------------------------------------------ Selector de ejercicios
    const val pickerRest = 112f
    const val pickerListTopL = 138f
    const val catRowH = 56f

    /** Cuánto se desplaza la lista hasta que «Press banca» y «Remo con barra» quedan a la vista. */
    const val pickerScrollEnd = 454f
    const val idxPressBanca = 9
    const val idxRemoConBarra = 12

    val catalog: List<EntrenoCatalogItem> = listOf(
        EntrenoCatalogItem("Aperturas con mancuernas", "Pecho · Mancuernas"),
        EntrenoCatalogItem("Curl con barra", "Bíceps · Barra"),
        EntrenoCatalogItem("Curl martillo", "Bíceps · Mancuernas"),
        EntrenoCatalogItem("Dominadas", "Espalda · Peso corporal"),
        EntrenoCatalogItem("Elevaciones laterales", "Hombro · Mancuernas"),
        EntrenoCatalogItem("Extensión de tríceps", "Tríceps · Polea"),
        EntrenoCatalogItem("Fondos en paralelas", "Tríceps · Peso corporal"),
        EntrenoCatalogItem("Jalón al pecho", "Espalda · Polea"),
        EntrenoCatalogItem("Peso muerto", "Espalda · Barra"),
        EntrenoCatalogItem("Press banca", "Pecho · Barra"),
        EntrenoCatalogItem("Press inclinado", "Pecho · Mancuernas"),
        EntrenoCatalogItem("Press militar", "Hombro · Barra"),
        EntrenoCatalogItem("Remo con barra", "Espalda · Barra"),
        EntrenoCatalogItem("Remo en polea", "Espalda · Polea"),
        EntrenoCatalogItem("Sentadilla", "Pierna · Barra"),
        EntrenoCatalogItem("Zancadas", "Pierna · Mancuernas"),
    )

    /** Borde superior de la zona de la lista (selector en reposo). */
    val listViewTop: Float get() = pickerRest + pickerListTopL

    /** Tope inferior de lo tocable de la lista: por debajo empieza el botón «Añadir». */
    val listTouchBottom: Float get() = cta.t - 4f

    fun catRowTop(i: Int, scroll: Float, pickerTop: Float = pickerRest): Float =
        pickerTop + pickerListTopL + catRowH * i - scroll

    fun catRowRect(i: Int, scroll: Float): EntrenoRect {
        val top = catRowTop(i, scroll)
        return EntrenoRect(16f, top, 284f, top + catRowH)
    }

    // ------------------------------------------------------------------ Sesión en vivo
    val timerPill = EntrenoRect(216f, 38f, 284f, 66f)
    const val liveTitleBase = 61f
    const val progLabelBase = 94f
    const val segLeft = 20f
    const val segRight = 280f
    const val segTop = 101f
    const val segH = 5f
    const val chipsTop = 114f
    const val chipsH = 26f
    val liveCard = EntrenoRect(16f, 152f, 284f, 464f)
    const val liveRowTop0 = 246f
    const val liveRowPitch = 54f
    const val liveRowH = 46f
    const val liveRowL = 24f
    const val liveRowR = 276f
    const val badgeX = 44f
    const val weightX = 94f
    const val repsX = 142f
    const val zoneL = 162f
    const val zoneR = 236f
    const val checkX = 256f
    const val checkR = 16f
    val preview = EntrenoRect(16f, 476f, 284f, 518f)
    val dock = EntrenoRect(16f, 528f, 284f, 588f)
    val saltar = EntrenoRect(210f, 542f, 272f, 574f)

    fun liveRow(i: Int): EntrenoRect {
        val top = liveRowTop0 + liveRowPitch * i
        return EntrenoRect(liveRowL, top, liveRowR, top + liveRowH)
    }

    fun checkRect(i: Int): EntrenoRect {
        val row = liveRow(i)
        return EntrenoRect(checkX - checkR, row.cy - checkR, checkX + checkR, row.cy + checkR)
    }
}
