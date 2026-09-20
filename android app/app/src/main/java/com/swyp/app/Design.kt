package com.swyp.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Swyp design system.
 *
 * Colour: one confident brand hue (electric blue) with a coral accent on a soft, slightly warm-tinted canvas so white
 * cards float naturally. Semantic colours are reserved for meaning only: green = healthy / rewards,
 * amber = caution, red = over limit. Text colours all clear WCAG AA (4.5:1) on their backgrounds.
 *
 * Layout: 4dp grid. Screen gutter 20, section gap 24, card padding 16–20, control height 52–56,
 * touch targets never below 48. Corners: cards 20, controls 16, chips/pills full.
 *
 * Type scale (one step ≈ 1.15×): 34 / 24 / 18 / 16 / 15 / 13. Nothing below 13sp so it stays legible.
 */
internal object Palette {
    val Primary = Color(0xFF047857)
    val PrimaryDeep = Color(0xFF064E3B)
    val Tint = Color(0xFFDDF7EB)
    val Canvas = Color(0xFFF2FAF6)
    val Surface = Color.White
    val Ink = Color(0xFF0B1F1A)
    val TextMuted = Color(0xFF4E6360)
    val Border = Color(0xFFD8EBE2)
    val BorderStrong = Color(0xFFB5D0C5)
    val Accent = Color(0xFFF2B01E)
    val Success = Color(0xFF047857)
    val SuccessTint = Color(0xFFDDF7EB)
    val Warning = Color(0xFFB45309)
    val WarningTint = Color(0xFFFFEFD5)
    val Danger = Color(0xFFD1343A)
    val DangerTint = Color(0xFFFDE8E8)
    val HeroBrush = Brush.linearGradient(listOf(Color(0xFF064E3B), Color(0xFF10B981), Color(0xFFF2C94C)))
    val Cta = Brush.horizontalGradient(listOf(Color(0xFF047857), Color(0xFF0E9F6E)))
    val CanvasBrush = Brush.verticalGradient(listOf(Color(0xFFE0F6EC), Color(0xFFF2FAF6), Color(0xFFFFF7E0)))
}

private val SwypTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable internal fun SwypTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Primary, onPrimary = Color.White,
            primaryContainer = Palette.Tint, onPrimaryContainer = Palette.PrimaryDeep,
            secondary = Palette.PrimaryDeep, secondaryContainer = Palette.Tint, onSecondaryContainer = Palette.Primary,
            background = Palette.Canvas, onBackground = Palette.Ink,
            surface = Palette.Surface, onSurface = Palette.Ink,
            surfaceVariant = Palette.Tint, onSurfaceVariant = Palette.TextMuted,
            outline = Palette.BorderStrong, outlineVariant = Palette.Border,
            error = Palette.Danger,
        ),
        typography = SwypTypography,
        content = content,
    )
}

// ---------- Text helpers ----------

@Composable internal fun Muted(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodySmall, color: Color = Palette.TextMuted) =
    Text(text, modifier, color = color, style = style)

@Composable internal fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Muted(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null) TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(action, style = MaterialTheme.typography.labelMedium, color = Palette.Primary)
        }
    }
}

// ---------- Surfaces ----------

@Composable internal fun SurfaceCard(
    modifier: Modifier = Modifier,
    container: Color = Palette.Surface,
    border: Color = Palette.Border,
    borderWidth: Dp = 1.dp,
    padding: Dp = 16.dp,
    gap: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val colors = CardDefaults.cardColors(containerColor = container, disabledContainerColor = container)
    val stroke = BorderStroke(borderWidth, border)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.fillMaxWidth().padding(padding), verticalArrangement = Arrangement.spacedBy(gap), content = content)
    }
    if (onClick != null) Card(onClick, modifier.fillMaxWidth(), enabled, shape, colors, CardDefaults.cardElevation(0.dp), stroke, content = body)
    else Card(modifier.fillMaxWidth(), shape, colors, CardDefaults.cardElevation(0.dp), stroke, content = body)
}

@Composable internal fun HeroPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.HeroBrush)) {
        FlowLines(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable internal fun InfoCard(text: String) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Tint, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(Palette.Primary, CircleShape))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
    }
}

@Composable internal fun Pill(text: String, background: Color, foreground: Color) {
    Text(
        text, Modifier.background(background, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
        color = foreground, style = MaterialTheme.typography.labelMedium,
    )
}

@Composable internal fun Avatar(label: String, size: Dp = 44.dp) {
    Box(Modifier.size(size).background(Palette.Tint, CircleShape), contentAlignment = Alignment.Center) {
        Text(label.trim().take(1).uppercase().ifEmpty { "•" }, color = Palette.Primary, style = MaterialTheme.typography.titleMedium)
    }
}

/** Swyp mark: a card sending out contactless waves ("tap to pay, smartly"). */
@Composable internal fun LogoMark(size: Dp = 44.dp, onDark: Boolean = false) {
    val card = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M22,44h46a6,6 0 0 1 6,6v20a6,6 0 0 1 -6,6H22a6,6 0 0 1 -6,-6V50a6,6 0 0 1 6,-6z").toPath() }
    val chip = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M25,54h12v9H25z").toPath() }
    val waves = remember { listOf(
        "M64.6,40.3A10,10 0 0 1 71.7,47.4", "M66.7,32.6A18,18 0 0 1 79.4,45.3", "M68.7,24.9A26,26 0 0 1 87.1,43.3",
    ).map { androidx.compose.ui.graphics.vector.PathParser().parsePathString(it).toPath() } }
    androidx.compose.foundation.Canvas(Modifier.size(size).clip(RoundedCornerShape(size * .28f))) {
        val k = this.size.width / 108f
        if (onDark) drawRect(Color.White) else drawRect(Palette.HeroBrush)
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(card, if (onDark) Palette.PrimaryDeep else Color.White)
            drawPath(chip, if (onDark) Color.White else Palette.PrimaryDeep)
            waves.forEach { drawPath(it, if (onDark) Palette.Accent else Color(0xFFFFE08A), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)) }
        }
    }
}

/** A small credit-card artwork used in lists. */
@Composable internal fun MiniCard(index: Int) {
    Box(Modifier.size(56.dp, 38.dp).background(cardBrush(index), RoundedCornerShape(8.dp))) {
        Box(Modifier.padding(start = 7.dp, top = 7.dp).size(11.dp, 8.dp).background(Color.White.copy(alpha = .75f), RoundedCornerShape(2.dp)))
    }
}

internal fun cardBrush(index: Int): Brush = when (index % 3) {
    0 -> Brush.linearGradient(listOf(Color(0xFF047857), Color(0xFF34D399)))
    1 -> Brush.linearGradient(listOf(Color(0xFF052E25), Color(0xFF14705A)))
    else -> Brush.linearGradient(listOf(Color(0xFFE59A0B), Color(0xFFFFD36B)))
}

internal fun utilColor(util: Double): Color = when {
    util > .6 -> Palette.Danger
    util > .3 -> Palette.Warning
    else -> Palette.Success
}

internal fun utilTint(util: Double): Color = when {
    util > .6 -> Palette.DangerTint
    util > .3 -> Palette.WarningTint
    else -> Palette.SuccessTint
}

@Composable internal fun UtilBar(util: Double, modifier: Modifier = Modifier, color: Color = utilColor(util), track: Color = Palette.Tint) {
    LinearProgressIndicator(
        { util.toFloat().coerceIn(0f, 1f) },
        modifier.fillMaxWidth().height(8.dp),
        color = color, trackColor = track, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
    )
}

// ---------- Controls ----------

@Composable internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(16.dp)
    Button(
        onClick, modifier.fillMaxWidth().heightIn(min = 54.dp)
            .then(if (enabled) Modifier.shadow(10.dp, shape, ambientColor = Palette.Primary, spotColor = Palette.Primary) else Modifier),
        enabled = enabled, shape = shape, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White, disabledContainerColor = Palette.Border, disabledContentColor = Palette.TextMuted),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 54.dp).then(if (enabled) Modifier.background(Palette.Cta) else Modifier), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, if (enabled) Palette.Primary.copy(alpha = .35f) else Palette.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Primary),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), maxLines = 1) }
}

@Composable internal fun SwypField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false,
) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Primary, unfocusedBorderColor = Palette.BorderStrong,
            focusedLabelColor = Palette.Primary, unfocusedLabelColor = Palette.TextMuted, cursorColor = Palette.Primary,
            focusedContainerColor = Palette.Surface, unfocusedContainerColor = Palette.Surface,
        ),
    )
}

@Composable internal fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected, onClick, label = { Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp)) },
        shape = CircleShape, modifier = Modifier.heightIn(min = 40.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Palette.Surface, labelColor = Palette.Ink,
            selectedContainerColor = Palette.Primary, selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(true, selected, borderColor = Palette.BorderStrong, selectedBorderColor = Palette.Primary),
    )
}

@Composable internal fun RadioDot(selected: Boolean, enabled: Boolean = true) {
    Box(
        Modifier.size(26.dp).then(
            if (selected) Modifier.background(Palette.Primary, CircleShape)
            else Modifier.border(2.dp, if (enabled) Palette.BorderStrong else Palette.Border, CircleShape),
        ),
        contentAlignment = Alignment.Center,
    ) { if (selected) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

// ---------- Icons (24dp Material path data, no extra dependency) ----------

private fun vec(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

internal object Icons {
    val Home = vec("home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    val Cards = vec("cards", "M20,4H4C2.89,4 2.01,4.89 2.01,6L2,18c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V6c0,-1.11 -0.89,-2 -2,-2zM20,18H4v-6h16v6zM20,8H4V6h16v2z")
    val Pay = vec("pay", "M21,18v1c0,1.1 -0.9,2 -2,2H5c-1.11,0 -2,-0.9 -2,-2V5c0,-1.1 0.89,-2 2,-2h14c1.1,0 2,0.9 2,2v1h-9c-1.11,0 -2,0.9 -2,2v8c0,1.1 0.89,2 2,2h9zM12,16h10V8H12v8zM16,13.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z")
    val Scan = vec("scan", "M9.5,6.5v3h-3v-3H9.5M11,5H5v6h6V5L11,5zM9.5,14.5v3h-3v-3H9.5M11,13H5v6h6V13L11,13zM17.5,6.5v3h-3v-3H17.5M19,5h-6v6h6V5L19,5zM13,13h1.5v1.5H13V13zM14.5,14.5H16V16h-1.5V14.5zM16,13h1.5v1.5H16V13zM13,16h1.5v1.5H13V16zM14.5,17.5H16V19h-1.5V17.5zM16,16h1.5v1.5H16V16zM17.5,14.5H19V16h-1.5V14.5zM17.5,17.5H19V19h-1.5V17.5zM22,7h-2V4h-3V2h5V7zM22,22h-5v-2h3v-3h2V22zM2,22v-5h2v3h3v2H2zM2,2h5v2H4v3H2V2z")
    val Nearby = vec("nearby", "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z")
}

/** Fine flowing contour lines: adds depth and a premium "engraved card" feel without blobs. */
@Composable internal fun FlowLines(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        for (i in 0 until 7) {
            val y = h * (0.30f + i * 0.11f)
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, y)
                cubicTo(w * .30f, y - h * .28f, w * .62f, y + h * .26f, w, y - h * .10f)
            }
            drawPath(p, Color.White.copy(alpha = .05f + i * .012f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
        }
    }
}
