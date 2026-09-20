package com.vthacks.swyp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vthacks.swyp.data.MockData
import com.vthacks.swyp.data.money
import com.vthacks.swyp.ui.components.*
import com.vthacks.swyp.ui.theme.SwypColors

@Composable
fun CardsScreen() {
    val pct = MockData.totalUsed * 100 / MockData.totalLimit
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SwypHeader("Demo data") {
            Button({ }, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = SwypColors.TealDark)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Add", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        ScreenTitle("Your cards", "A clear view of your credit.")
        SwypSurface(Modifier.padding(horizontal = 20.dp), padding = 20.dp) {
            Text("Total credit used", fontSize = 16.sp, color = SwypColors.Muted)
            Text("$pct%", fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = SwypColors.Ink)
            Text("${MockData.totalUsed.toDouble().money().dropLast(3)} of ${MockData.totalLimit.toDouble().money().dropLast(3)}", fontSize = 17.sp, color = SwypColors.Ink)
            Spacer(Modifier.height(12.dp))
            UsageBar(MockData.totalUsed.toFloat() / MockData.totalLimit)
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("My cards", style = MaterialTheme.typography.titleLarge)
            Text("${MockData.cards.size} cards", fontSize = 16.sp, color = SwypColors.Muted)
        }
        MockData.cards.forEach { c ->
            val high = c.usedPercent >= 40
            val accent = if (high) SwypColors.Orange else SwypColors.Teal
            SwypSurface(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardThumb(c.color, 84.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text("•••• ${c.last4}", fontSize = 14.sp, color = SwypColors.Muted)
                        Text(c.perk, fontSize = 13.sp, color = SwypColors.Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${c.usedPercent}% used", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = accent)
                        if (high) Pill("Higher usage", SwypColors.OrangeSoft, SwypColors.Orange, small = true)
                        Text("$${c.used} / $${"%,d".format(c.limit)}", fontSize = 14.sp, color = SwypColors.Ink)
                    }
                }
                Spacer(Modifier.height(12.dp))
                UsageBar(c.used.toFloat() / c.limit, if (high) SwypColors.OrangeBar else SwypColors.Teal)
            }
        }
        SwypSurface(Modifier.padding(horizontal = 20.dp), color = SwypColors.Mint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Filled.Lightbulb, 56.dp, bg = Color(0xFFCFEBE7))
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Balance your next purchase", style = MaterialTheme.typography.titleMedium)
                    Text("Everyday 2% has more room for your next grocery trip.", fontSize = 14.sp, color = SwypColors.Muted)
                }
            }
        }
    }
}
