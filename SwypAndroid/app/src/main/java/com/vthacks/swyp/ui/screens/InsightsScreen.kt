package com.vthacks.swyp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vthacks.swyp.data.MockData
import com.vthacks.swyp.data.money
import com.vthacks.swyp.ui.components.*
import com.vthacks.swyp.ui.theme.SwypColors

@Composable
fun InsightsScreen(onBack: () -> Unit, onExploreOffers: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.clickable(onClick = onBack).padding(end = 16.dp), tint = SwypColors.Teal)
            Column {
                Text("Spending insights", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SwypColors.Ink)
                Pill("Demo data", SwypColors.Mint, SwypColors.Teal, small = true)
            }
        }
        SwypSurface(Modifier.padding(horizontal = 20.dp), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ChevronLeft, null, tint = SwypColors.Ink)
                Text("September 2026", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                Icon(Icons.Filled.ChevronRight, null, tint = SwypColors.Ink)
            }
        }
        SwypSurface(Modifier.padding(horizontal = 20.dp), padding = 20.dp) {
            Text("Total spent", fontSize = 16.sp, color = SwypColors.Muted)
            Text(MockData.monthTotal.money(), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = SwypColors.Ink)
            Text("Across ${MockData.cards.size} cards", fontSize = 16.sp, color = SwypColors.Muted)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MockData.weekly.forEach { (label, frac) ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth(0.55f).fillMaxHeight(frac).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(SwypColors.TealBar))
                        }
                        HorizontalDivider(color = SwypColors.Divider)
                        Text(label, Modifier.padding(top = 8.dp), fontSize = 16.sp, color = SwypColors.Muted)
                    }
                }
            }
        }
        SectionTitle("By category")
        SwypSurface(Modifier.padding(horizontal = 20.dp)) {
            val max = MockData.categories.maxOf { it.amount }
            MockData.categories.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = SwypColors.Divider)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(c.icon)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(c.name, fontSize = 17.sp, color = SwypColors.Ink)
                        Spacer(Modifier.height(4.dp))
                        UsageBar((c.amount / max).toFloat(), SwypColors.TealBar, Modifier.height(10.dp))
                    }
                    Text(c.amount.money(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SwypColors.Ink)
                }
            }
        }
        SwypSurface(Modifier.padding(horizontal = 20.dp), color = SwypColors.Mint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Filled.Sync, 64.dp, bg = Color(0xFFCFEBE7))
                Column(Modifier.padding(start = 16.dp)) {
                    Text("A familiar purchase", style = MaterialTheme.typography.titleMedium)
                    Text("You bought coffee 4 times this month.", fontSize = 15.sp, color = SwypColors.Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.clickable(onClick = onExploreOffers), verticalAlignment = Alignment.CenterVertically) {
                        Text("Explore nearby offers", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = SwypColors.Teal)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = SwypColors.Teal)
                    }
                }
            }
        }
    }
}
