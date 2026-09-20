package com.vthacks.swyp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object SwypColors {
    val Teal = Color(0xFF0B7A75)
    val TealDark = Color(0xFF0B5F5A)
    val TealBar = Color(0xFF1B8F88)
    val Mint = Color(0xFFE3F3F1)
    val MintSoft = Color(0xFFEFF8F7)
    val Navy = Color(0xFF14324B)
    val Ink = Color(0xFF0B1F3A)
    val Muted = Color(0xFF5B6B7B)
    val Background = Color(0xFFF3F8FA)
    val Surface = Color.White
    val Divider = Color(0xFFE4ECEF)
    val Orange = Color(0xFFD9730D)
    val OrangeBar = Color(0xFFEC9A1F)
    val OrangeSoft = Color(0xFFFDEBD5)
    val CardBlue = Color(0xFF3F6C9B)
}

private val scheme = lightColorScheme(
    primary = SwypColors.Teal,
    onPrimary = Color.White,
    background = SwypColors.Background,
    surface = SwypColors.Surface,
    onSurface = SwypColors.Ink,
    onBackground = SwypColors.Ink,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = SwypColors.Ink),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = SwypColors.Ink),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SwypColors.Ink),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SwypColors.Ink),
    bodyLarge = TextStyle(fontSize = 16.sp, color = SwypColors.Ink),
    bodyMedium = TextStyle(fontSize = 14.sp, color = SwypColors.Muted),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun SwypTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
