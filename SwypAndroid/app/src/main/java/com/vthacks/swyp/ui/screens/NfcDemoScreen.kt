package com.vthacks.swyp.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vthacks.swyp.data.MockData
import com.vthacks.swyp.ui.components.*
import com.vthacks.swyp.ui.theme.SwypColors

/** Simulated tap-to-pay screen. No real NFC is used. */
@Composable
fun NfcDemoScreen(onBack: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "t")
    val card = MockData.cards.first()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.clickable(onClick = onBack).padding(end = 16.dp), tint = SwypColors.Teal)
            Column {
                Text("NFC demo", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SwypColors.Ink)
                Pill("Demo mode", SwypColors.Mint, SwypColors.Teal, small = true)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            if (!done) {
                listOf(0f, 0.5f).forEach { off ->
                    val p = (t + off) % 1f
                    Box(Modifier.size(260.dp).scale(0.5f + 0.5f * p).alpha(1f - p).clip(CircleShape).background(SwypColors.Teal.copy(alpha = 0.25f)))
                }
            }
            Box(Modifier.size(130.dp).clip(CircleShape).background(SwypColors.Teal), contentAlignment = Alignment.Center) {
                Icon(if (done) Icons.Filled.Check else Icons.Filled.Contactless, null, Modifier.size(64.dp), tint = Color.White)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(if (done) "Payment complete" else "Hold near reader", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            if (done) "Paid $100.00 with ${card.name} •••• ${card.last4}" else "Simulated tap to pay with ${card.name} •••• ${card.last4}",
            Modifier.padding(top = 8.dp), fontSize = 16.sp, color = SwypColors.Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(
            { if (done) onBack() else done = true },
            Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SwypColors.Teal),
        ) { PrimaryButtonLabel(if (done) "Done" else "Simulate tap") }
        Text("Simulated payment. No real money moves.", Modifier.padding(vertical = 12.dp), fontSize = 14.sp, color = SwypColors.Muted)
    }
}
