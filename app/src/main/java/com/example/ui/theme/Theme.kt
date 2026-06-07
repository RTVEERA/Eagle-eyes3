package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ViraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColorHex: String = "#FF9933", // Default Saffron
    content: @Composable () -> Unit
) {
    // Attempt to parse dynamic accent color safely
    val primaryColor = try {
        Color(android.graphics.Color.parseColor(accentColorHex))
    } catch (e: Exception) {
        SaffronOrange
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.Black,
            secondary = primaryColor.copy(alpha = 0.7f),
            onSecondary = Color.White,
            tertiary = PeacockBlue,
            background = SlateBackground,
            surface = SlateSurface,
            surfaceVariant = SlateSurfaceVariant,
            onBackground = SlateTextPrimary,
            onSurface = SlateTextPrimary,
            onSurfaceVariant = SlateTextSecondary
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            secondary = primaryColor.copy(alpha = 0.7f),
            onSecondary = Color.Black,
            tertiary = PeacockBlue,
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            onBackground = LightTextPrimary,
            onSurface = LightTextPrimary,
            onSurfaceVariant = LightTextSecondary
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
