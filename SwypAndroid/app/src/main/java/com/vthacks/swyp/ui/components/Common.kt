package com.vthacks.swyp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import com.vthacks.swyp.R
import com.vthacks.swyp.ui.theme.SwypColors

@Composable
fun SwypHeader(badge: String?, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_swyp_logo), null, Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text("Swyp", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SwypColors.Ink)
            if (badge != null) Pill(badge, SwypColors.Mint, SwypColors.Teal, small = true)
        }
        trailing()
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, small: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 2.dp else 6.dp),
        color = fg,
        fontSize = if (small) 13.sp else 15.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun SwypSurface(
    modifier: Modifier = Modifier,
    color: Color = SwypColors.Surface,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = color, shadowElevation = if (color == SwypColors.Surface) 2.dp else 0.dp) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun IconCircle(icon: ImageVector, size: Dp = 48.dp, bg: Color = SwypColors.Mint, tint: Color = SwypColors.Teal) {
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun AvatarCircle(letter: String) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(SwypColors.TealDark), contentAlignment = Alignment.Center) {
        Text(letter, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun UsageBar(fraction: Float, color: Color = SwypColors.Teal, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(SwypColors.Mint)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.03f, 1f)).clip(CircleShape).background(color))
    }
}

@Composable
fun CardThumb(color: Color, width: Dp = 84.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(width, width * 0.65f).clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.78f)), Offset(0f, 0f), Offset(300f, 300f))),
    ) {
        Box(
            Modifier.padding(start = width * 0.1f, top = width * 0.16f)
                .size(width * 0.17f, width * 0.13f).clip(RoundedCornerShape(3.dp)).background(Color(0xFFD9DEE3)),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) Text(subtitle, fontSize = 18.sp, color = SwypColors.Muted)
    }
}

@Composable
fun PrimaryButtonLabel(text: String) = Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
