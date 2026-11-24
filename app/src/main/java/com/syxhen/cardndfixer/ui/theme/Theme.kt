package com.syxhen.cardndfixer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FallbackLightColorScheme = lightColorScheme(
    primary = Color(0xFF1A56DB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE6FF),
    onPrimaryContainer = Color(0xFF001A4D),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color.White,
)

private val FallbackDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6F),
    primaryContainer = Color(0xFF00429C),
    onPrimaryContainer = Color(0xFFDBE6FF),
    secondary = Color(0xFFCBD5E1),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF003731),
)

@Composable
fun CarDndTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> FallbackDarkColorScheme
        else -> FallbackLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
