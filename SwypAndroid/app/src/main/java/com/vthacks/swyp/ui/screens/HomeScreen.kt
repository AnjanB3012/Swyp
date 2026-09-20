package com.vthacks.swyp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vthacks.swyp.data.MockData
import com.vthacks.swyp.data.money
import com.vthacks.swyp.ui.components.*
import com.vthacks.swyp.ui.theme.SwypColors

@Composable
fun HomeScreen(onChooseCard: () -> Unit, onViewInsights: () -> Unit) {
    val best = MockData.cards.first()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SwypHeader("Demo data") { AvatarCircle(MockData.userName.take(1)) }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Good morning, ${MockData.userName}", fontSize = 18.sp, color = SwypColors.Muted)
            Text("Your money, in view", style = MaterialTheme.typography.headlineLarge)
        }
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SwypColors.Navy).padding(24.dp)) {
            Text("September spending", color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp)
            Text(MockData.monthTotal.money(), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Text("Across ${MockData.cards.size} cards", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SwypSurface(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Filled.CardGiftcard, 44.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("Rewards earned", fontSize = 13.sp, color = SwypColors.Muted)
                        Text("$24.97", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SwypColors.Ink)
                    }
                }
            }
            SwypSurface(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Filled.BarChart, 44.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("Credit used", fontSize = 13.sp, color = SwypColors.Muted)
                        Text("${MockData.totalUsed * 100 / MockData.totalLimit}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SwypColors.Ink)
                    }
                }
                Spacer(Modifier.height(10.dp))
                UsageBar(MockData.totalUsed.toFloat() / MockData.totalLimit)
            }
        }
        SectionTitle("Best card for your next shop")
        SwypSurface(Modifier.padding(horizontal = 20.dp)) {
            Pill("Recommended at Kroger", SwypColors.Mint, SwypColors.Teal)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardThumb(best.color, 84.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(best.name, style = MaterialTheme.typography.titleLarge)
                    Text("•••• ${best.last4}", fontSize = 16.sp, color = SwypColors.Muted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Earn 2% back. Estimated credit use: 20%.", fontSize = 15.sp, color = SwypColors.Muted)
            Spacer(Modifier.height(14.dp))
            Button(onChooseCard, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = SwypColors.Teal)) {
                PrimaryButtonLabel("Choose a card")
                Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White)
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent activity", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.clickable(onClick = onViewInsights), verticalAlignment = Alignment.CenterVertically) {
                Text("View insights", color = SwypColors.Teal, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Filled.ChevronRight, null, tint = SwypColors.Teal)
            }
        }
        SwypSurface(Modifier.padding(horizontal = 20.dp)) {
            MockData.transactions.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = SwypColors.Divider)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(t.icon)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(t.merchant, style = MaterialTheme.typography.titleMedium)
                        Text("${t.category} · ${t.whenLabel}", fontSize = 14.sp, color = SwypColors.Muted)
                    }
                    Text(t.amount.money(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = SwypColors.Ink)
                }
            }
        }
    }
}
