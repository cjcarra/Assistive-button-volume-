package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

sealed class AppThemePreset(
    val name: String,
    val isDark: Boolean,
    val primary: Color,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val accent: Color,
    val glowColor: Color,
    val shadowColor: Color,
    val glassBg: Color,
    val glassBorder: Color,
    val bgGradients: List<Color>
) {
    object FrostBlue : AppThemePreset(
        name = "Frost Blue",
        isDark = false,
        primary = Color(0xFF3B82F6),      // Vivid brand blue
        background = Color(0xFFEEF2F6),   // Cold sheet
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1E293B),    // Dark slate text
        accent = Color(0xFF6366F1),       // Indigo highlight
        glowColor = Color(0xFF60A5FA),    // Light blue glow
        shadowColor = Color(0xFFD1D5DB),
        glassBg = Color(0xFFFFFFFF).copy(alpha = 0.40f),      // bg-white/40
        glassBorder = Color(0xFFFFFFFF).copy(alpha = 0.60f),  // border-white/60
        bgGradients = listOf(Color(0xFFE0E7FF), Color(0xFFEFF6FF), Color(0xFFFAE8FF)) // indigo-100 via blue-50 to purple-100
    )

    object OnyxDark : AppThemePreset(
        name = "Onyx Dark",
        isDark = true,
        primary = Color(0xFF00FFCC),      // Hyper-bright cyan
        background = Color(0xFF090D16),   // Void black
        surface = Color(0xFF111827),      // Dark gray surface
        onSurface = Color(0xFFF9FAFB),    // Warm off-white
        accent = Color(0xFFFF007F),       // Laser pink accent
        glowColor = Color(0xFF00FFCC),    // Laser cyan glow
        shadowColor = Color(0xFF020104),
        glassBg = Color(0xFF111827).copy(alpha = 0.55f),
        glassBorder = Color(0xFFFFFFFF).copy(alpha = 0.12f),
        bgGradients = listOf(Color(0xFF0F172A), Color(0xFF030712), Color(0xFF1E1B4B))
    )

    object SageGlass : AppThemePreset(
        name = "Sage Glass",
        isDark = false,
        primary = Color(0xFF059669),      // Deep jade emerald
        background = Color(0xFFF0FDF4),   // Sage mint mist
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF065F46),    // Forest dark sage
        accent = Color(0xFF10B981),       // Emerald breeze
        glowColor = Color(0xFFA7F3D0),
        shadowColor = Color(0xFFD1FAE5),
        glassBg = Color(0xFFF0FDF4).copy(alpha = 0.50f),
        glassBorder = Color(0xFFFFFFFF).copy(alpha = 0.65f),
        bgGradients = listOf(Color(0xFFD1FAE5), Color(0xFFECFDF5), Color(0xFFF0FDF4))
    )

    object SakuraBlossom : AppThemePreset(
        name = "Sakura Blossom",
        isDark = false,
        primary = Color(0xFFEC4899),      // Cherry blossom pink
        background = Color(0xFFFFF1F2),   // Rose blush white
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF881337),    // Dark wine plum
        accent = Color(0xFFF43F5E),       // Coral rose accent
        glowColor = Color(0xFFFECDD3),
        shadowColor = Color(0xFFFFE4E6),
        glassBg = Color(0xFFFFF5F5).copy(alpha = 0.45f),
        glassBorder = Color(0xFFFFFFFF).copy(alpha = 0.70f),
        bgGradients = listOf(Color(0xFFFFE4E6), Color(0xFFFFF1F2), Color(0xFFFCE7F3))
    )

    object CosmicSlate : AppThemePreset(
        name = "Cosmic Slate",
        isDark = true,
        primary = Color(0xFF818CF8),      // Aurora indigo
        background = Color(0xFF090D16),   // Deep cosmic dark
        surface = Color(0xFF161F30),      // Space steel
        onSurface = Color(0xFFE2E8F0),    // Nebula white
        accent = Color(0xFFC084FC),       // Amethyst violet
        glowColor = Color(0xFF818CF8),
        shadowColor = Color(0xFF030712),
        glassBg = Color(0xFF1E1B4B).copy(alpha = 0.45f),
        glassBorder = Color(0xFFFFFFFF).copy(alpha = 0.15f),
        bgGradients = listOf(Color(0xFF030712), Color(0xFF0F172A), Color(0xFF1E1B4B))
    )

    fun toColorScheme(): ColorScheme {
        return if (isDark) {
            darkColorScheme(
                primary = primary,
                background = background,
                surface = surface,
                onSurface = onSurface,
                secondary = accent,
                tertiary = glowColor
            )
        } else {
            lightColorScheme(
                primary = primary,
                background = background,
                surface = surface,
                onSurface = onSurface,
                secondary = accent,
                tertiary = glowColor
            )
        }
    }

    companion object {
        val ALL = listOf(FrostBlue, OnyxDark, SageGlass, SakuraBlossom, CosmicSlate)
        fun fromName(name: String): AppThemePreset {
            return ALL.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FrostBlue
        }
    }
}
