package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    secondary = ElectricLime,
    tertiary = CyanBlue,
    background = PitchBlack,
    surface = DarkGrey,
    onPrimary = PitchBlack,
    onSecondary = PitchBlack,
    onTertiary = PitchBlack,
    onBackground = TextGreen,
    onSurface = TextGreen,
    surfaceVariant = CardGrey,
    onSurfaceVariant = GhostGreen,
    outline = BorderGreen
)

private val LightColorScheme = DarkColorScheme // Hacker style is permanently dark

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme by default
  dynamicColor: Boolean = false, // Disable dynamic colors to keep matrix styling intact
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
