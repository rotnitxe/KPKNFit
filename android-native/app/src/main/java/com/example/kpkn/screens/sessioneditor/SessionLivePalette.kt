package com.example.kpkn.screens.sessioneditor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.SessionBackgroundType

/**
 * Curated live-workout colors derived from a session cover preset.
 * Chosen for beauty + readable contrast — not raw max-luminance of the cover stops.
 */
data class SessionLivePalette(
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val glow: Color,
    val surfaceTint: Color,
)

/** Canonical sky blue for ungrouped exercises and superserie mother cards. */
val RoadmapCeleste: Color = Color(0xFF38BDF8)

private val SteelBlueFallback = SessionLivePalette(
    accent = Color(0xFF38BDF8),
    onAccent = Color(0xFF0B1220),
    accentSoft = Color(0xFF38BDF8).copy(alpha = 0.28f),
    onAccentSoft = Color.White.copy(alpha = 0.92f),
    glow = Color(0xFF7DD3FC),
    surfaceTint = Color(0xFF0F172A).copy(alpha = 0.35f),
)

private fun palette(
    accent: Color,
    soft: Color = accent.copy(alpha = 0.28f),
    glow: Color = lerp(accent, Color.White, 0.28f),
    surfaceTint: Color = accent.copy(alpha = 0.14f),
): SessionLivePalette {
    val onAccent = contentOn(accent)
    val onSoft = contentOn(if (soft.alpha < 0.5f) Color(0xFF1A1A1A) else soft)
    return SessionLivePalette(
        accent = accent,
        onAccent = onAccent,
        accentSoft = soft,
        onAccentSoft = onSoft,
        glow = glow,
        surfaceTint = surfaceTint,
    )
}

/** Readable content color for a filled surface (WCAG-ish luminance threshold). */
fun contentOn(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF0B0B0B) else Color.White

private val curatedLivePalettes: Map<String, SessionLivePalette> = mapOf(
    // Gradients
    "gradient://ember" to palette(
        accent = Color(0xFFE08E45),
        soft = Color(0xFFE08E45).copy(alpha = 0.30f),
        glow = Color(0xFFF0B27A),
        surfaceTint = Color(0xFF8D3D2E).copy(alpha = 0.22f),
    ),
    "gradient://lagoon" to palette(
        accent = Color(0xFF5FA8D3),
        soft = Color(0xFF5FA8D3).copy(alpha = 0.30f),
        glow = Color(0xFF9AD0EA),
        surfaceTint = Color(0xFF1B4965).copy(alpha = 0.28f),
    ),
    "gradient://velvet" to palette(
        accent = Color(0xFFC084FC),
        soft = Color(0xFFC084FC).copy(alpha = 0.28f),
        glow = Color(0xFFE9D5FF),
        surfaceTint = Color(0xFF5B2A86).copy(alpha = 0.26f),
    ),
    "gradient://forest" to palette(
        accent = Color(0xFF4ADE80),
        soft = Color(0xFF4ADE80).copy(alpha = 0.28f),
        glow = Color(0xFF86EFAC),
        surfaceTint = Color(0xFF2D6A4F).copy(alpha = 0.26f),
    ),
    "gradient://graphite" to palette(
        accent = Color(0xFFA1A1AA),
        soft = Color(0xFFA1A1AA).copy(alpha = 0.26f),
        glow = Color(0xFFD4D4D8),
        surfaceTint = Color(0xFF27272A).copy(alpha = 0.40f),
    ),
    "gradient://steel-blue" to SteelBlueFallback,
    "gradient://deep-red" to palette(
        accent = Color(0xFFF87171),
        soft = Color(0xFFF87171).copy(alpha = 0.28f),
        glow = Color(0xFFFCA5A5),
        surfaceTint = Color(0xFF7F1D1D).copy(alpha = 0.30f),
    ),
    "gradient://mint-night" to palette(
        accent = Color(0xFF34D399),
        soft = Color(0xFF34D399).copy(alpha = 0.28f),
        glow = Color(0xFF6EE7B7),
        surfaceTint = Color(0xFF14532D).copy(alpha = 0.28f),
    ),
    "gradient://indigo" to palette(
        accent = Color(0xFF818CF8),
        soft = Color(0xFF818CF8).copy(alpha = 0.28f),
        glow = Color(0xFFA5B4FC),
        surfaceTint = Color(0xFF3730A3).copy(alpha = 0.28f),
    ),
    "gradient://bronze" to palette(
        accent = Color(0xFFF59E0B),
        soft = Color(0xFFF59E0B).copy(alpha = 0.28f),
        glow = Color(0xFFFBBF24),
        surfaceTint = Color(0xFF92400E).copy(alpha = 0.26f),
    ),
    // Solids — lift near-black covers into saturated CTAs
    "solid://obsidian" to palette(
        accent = Color(0xFF94A3B8),
        soft = Color(0xFF94A3B8).copy(alpha = 0.26f),
        glow = Color(0xFFCBD5E1),
        surfaceTint = Color(0xFF111318).copy(alpha = 0.50f),
    ),
    "solid://steel" to palette(
        accent = Color(0xFF7DD3FC),
        soft = Color(0xFF7DD3FC).copy(alpha = 0.28f),
        glow = Color(0xFFBAE6FD),
        surfaceTint = Color(0xFF334155).copy(alpha = 0.40f),
    ),
    "solid://ember-red" to palette(
        accent = Color(0xFFF87171),
        soft = Color(0xFFF87171).copy(alpha = 0.28f),
        glow = Color(0xFFFCA5A5),
        surfaceTint = Color(0xFF7F1D1D).copy(alpha = 0.35f),
    ),
    "solid://ocean" to palette(
        accent = Color(0xFF38BDF8),
        soft = Color(0xFF38BDF8).copy(alpha = 0.28f),
        glow = Color(0xFF7DD3FC),
        surfaceTint = Color(0xFF0F3D5E).copy(alpha = 0.40f),
    ),
    "solid://moss" to palette(
        accent = Color(0xFF4ADE80),
        soft = Color(0xFF4ADE80).copy(alpha = 0.28f),
        glow = Color(0xFF86EFAC),
        surfaceTint = Color(0xFF244B3C).copy(alpha = 0.40f),
    ),
    "solid://charcoal" to palette(
        accent = Color(0xFFA8B2C1),
        soft = Color(0xFFA8B2C1).copy(alpha = 0.26f),
        glow = Color(0xFFD1D5DB),
        surfaceTint = Color(0xFF1F2329).copy(alpha = 0.50f),
    ),
    "solid://slate" to palette(
        accent = Color(0xFF7DD3FC),
        soft = Color(0xFF7DD3FC).copy(alpha = 0.26f),
        glow = Color(0xFFBAE6FD),
        surfaceTint = Color(0xFF283241).copy(alpha = 0.45f),
    ),
    "solid://wine" to palette(
        accent = Color(0xFFF472B6),
        soft = Color(0xFFF472B6).copy(alpha = 0.28f),
        glow = Color(0xFFF9A8D4),
        surfaceTint = Color(0xFF581C27).copy(alpha = 0.40f),
    ),
    "solid://pine" to palette(
        accent = Color(0xFF34D399),
        soft = Color(0xFF34D399).copy(alpha = 0.28f),
        glow = Color(0xFF6EE7B7),
        surfaceTint = Color(0xFF12352A).copy(alpha = 0.42f),
    ),
    "solid://navy" to palette(
        accent = Color(0xFF60A5FA),
        soft = Color(0xFF60A5FA).copy(alpha = 0.28f),
        glow = Color(0xFF93C5FD),
        surfaceTint = Color(0xFF10233F).copy(alpha = 0.42f),
    ),
    "solid://aubergine" to palette(
        accent = Color(0xFFC084FC),
        soft = Color(0xFFC084FC).copy(alpha = 0.28f),
        glow = Color(0xFFE9D5FF),
        surfaceTint = Color(0xFF2A1835).copy(alpha = 0.42f),
    ),
)

fun resolveSessionLivePalette(background: SessionBackground?): SessionLivePalette {
    if (background?.type == SessionBackgroundType.IMAGE) return SteelBlueFallback
    val id = background?.value
    if (id != null) {
        curatedLivePalettes[id]?.let { return it }
    }
    // Unknown / null: try brightest cover stop, then ensure readable CTA.
    val raw = sessionCoverColors(background).maxByOrNull { it.luminance() } ?: Color(0xFF38BDF8)
    val accent = if (raw.luminance() < 0.28f) lerp(raw, Color.White, 0.45f) else raw
    return palette(accent = accent)
}

/** @deprecated Prefer [resolveSessionLivePalette]; kept for call-site compatibility. */
fun sessionCoverAccentFromPalette(background: SessionBackground?): Color =
    resolveSessionLivePalette(background).accent
