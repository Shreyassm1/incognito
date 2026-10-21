package com.incognito.vault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VaultBlack = Color(0xFF0A0C10)
val VaultSurface = Color(0xFF12161F)
val VaultSurfaceVariant = Color(0xFF1B2230)
val VaultCard = Color(0xFF1E2536)
val VaultAccent = Color(0xFF00E676)       // Security Green
val VaultAccentDark = Color(0xFF00B0FF)   // Secure Cyan
val VaultError = Color(0xFFFF5252)        // Warning / Shred Red
val VaultTextPrimary = Color(0xFFF0F4F8)
val VaultTextSecondary = Color(0xFF90A4AE)

private val DarkColorScheme = darkColorScheme(
    primary = VaultAccent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Color(0xFFA7FFEB),
    secondary = VaultAccentDark,
    onSecondary = Color.Black,
    background = VaultBlack,
    onBackground = VaultTextPrimary,
    surface = VaultSurface,
    onSurface = VaultTextPrimary,
    surfaceVariant = VaultSurfaceVariant,
    onSurfaceVariant = VaultTextSecondary,
    error = VaultError,
    onError = Color.White
)

@Composable
fun IncognitoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
