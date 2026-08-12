package com.modjust4real.mixora.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0A0810)
val Panel = Color(0xFF171221)
val Purple = Color(0xFFA875FF)
val PurpleDeep = Color(0xFF6D3BC4)
val Mint = Color(0xFF48E0C2)
val Rose = Color(0xFFFF6F91)
val SoftText = Color(0xFFBDB4C9)

private val MixoraColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color(0xFF18002D),
    secondary = Mint,
    onSecondary = Color(0xFF00251E),
    tertiary = Rose,
    background = Ink,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF25202E),
    onSurfaceVariant = SoftText,
    error = Color(0xFFFF6B6B)
)

@Composable
fun MixoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MixoraColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
