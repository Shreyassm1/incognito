package com.incognito.vault.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkPrimary = Color(0xFF10B981) // Emerald accent for secure vault
val DarkOnPrimary = Color(0xFF003822)
val DarkPrimaryContainer = Color(0xFF064E3B)
val DarkOnPrimaryContainer = Color(0xFFA7F3D0)

val DarkSecondary = Color(0xFF06B6D4) // Cyan accent
val DarkOnSecondary = Color(0xFF00363F)
val DarkSecondaryContainer = Color(0xFF164E63)
val DarkOnSecondaryContainer = Color(0xFFCFFAFE)

val DarkTertiary = Color(0xFF8B5CF6) // Purple highlight
val DarkOnTertiary = Color(0xFF2E1065)

val DarkBackground = Color(0xFF0F1115) // Deep onyx
val DarkOnBackground = Color(0xFFE2E8F0)

val DarkSurface = Color(0xFF181A20) // Raised surface
val DarkOnSurface = Color(0xFFF1F5F9)
val DarkSurfaceVariant = Color(0xFF242731)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)

val DarkOutline = Color(0xFF333846)
val DarkOutlineVariant = Color(0xFF232630)

val DarkError = Color(0xFFEF4444)
val DarkOnError = Color(0xFF450A0A)
val DarkErrorContainer = Color(0xFF7F1D1D)
val DarkOnErrorContainer = Color(0xFFFECACA)

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer
)

@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
