package com.vthacks.swyp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vthacks.swyp.data.CreditCard
import com.vthacks.swyp.data.MockData
import com.vthacks.swyp.data.money
import com.vthacks.swyp.ui.components.*
import com.vthacks.swyp.ui.theme.SwypColors

private fun afterPercent(c: CreditCard, amount: Double) = ((c.used + amount) * 100 / c.limit).toInt()

@Composable
fun PayScreen(onTryNfc: () -> Unit) {
    var store by remember { mutableStateOf(MockData.stores.first()) }
    var storeMenu by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("100.00") }
    var cardIdx by remember { mutableIntStateOf(0) }
    var paid by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val card = MockData.cards[cardIdx]
    val alt = MockData.cards[(cardIdx + 1) % MockData.cards.size]

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SwypHeader("Demo mode") { AvatarCircle(MockData.userName.take(1)) }
        ScreenTitle("Choose & pay", "The right card for this purchase.")
        SwypSurface(Modifier.padding(horizontal = 20.dp), padding = 20.dp) {
            Text("Store", fontSize = 15.sp, color = SwypColors.Muted)
            Box {
                Row(Modifier.fillMaxWidth().clickable { storeMenu = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(store, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SwypColors.Ink)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = SwypColors.Navy)
                }
                DropdownMenu(storeMenu, { storeMenu = false }) {
                    MockData.stores.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { store = s; storeMenu = false }) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = SwypColors.Divider)
            Text("Purchase amount", fontSize = 15.sp, color = SwypColors.Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = SwypColors.Ink)
                BasicAmountField(amountText) { amountText = it; paid = false }
            }
        }
        SectionTitle("Recommended for you")
        SwypSurface(Modifier.padding(horizontal = 20.dp), padding = 20.dp) {
            Pill(if (cardIdx == 0) "Best balance of rewards & usage" else "Your selected card", SwypColors.Mint, SwypColors.Teal)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardThumb(card.color, 84.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(card.name, style = MaterialTheme.typography.titleLarge)
                    Text("•••• ${card.last4}", fontSize = 16.sp, color = SwypColors.Muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Modifier.weight(1f), Icons.Filled.CardGiftcard, "Estimated rewards", (amount * card.rewardRate).money())
                StatTile(Modifier.weight(1f), Icons.Filled.BarChart, "Credit use after", "${afterPercent(card, amount)}%")
            }
            Spacer(Modifier.height(10.dp))
            Text("Earn ${(card.rewardRate * 100).toInt()}% back while keeping more credit available.", fontSize = 15.sp, color = SwypColors.Muted)
        }
        SwypSurface(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardThumb(alt.color, 64.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(alt.name, style = MaterialTheme.typography.titleMedium)
                    Text("•••• ${alt.last4}", fontSize = 15.sp, color = SwypColors.Muted)
                }
                Column(Modifier.weight(1f)) {
                    Text("${afterPercent(alt, amount)}% after purchase", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SwypColors.Orange)
                    Text("Higher rewards, higher credit usage.", fontSize = 12.sp, color = SwypColors.Muted)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = SwypColors.Divider)
            Row(Modifier.fillMaxWidth().clickable { cardIdx = (cardIdx + 1) % MockData.cards.size; paid = false }, verticalAlignment = Alignment.CenterVertically) {
                Text("Choose a different card", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = SwypColors.Teal)
                Icon(Icons.Filled.ChevronRight, null, tint = SwypColors.Teal)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button({ paid = true }, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = SwypColors.Teal)) {
                PrimaryButtonLabel(if (paid) "Paid ${amount.money()} · Demo ✓" else "Pay ${amount.money()} · Demo")
                Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White)
            }
            Text("Simulated payment. No real money moves.", fontSize = 14.sp, color = SwypColors.Muted)
            OutlinedButton(onTryNfc, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(2.dp, SwypColors.Teal)) {
                Icon(Icons.Filled.Contactless, null, tint = SwypColors.Teal)
                Spacer(Modifier.width(12.dp))
                Text("Try NFC demo", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = SwypColors.Teal)
            }
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(SwypColors.MintSoft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconCircle(icon, 44.dp)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, fontSize = 13.sp, color = SwypColors.Ink)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = SwypColors.Ink)
        }
    }
}

@Composable
private fun BasicAmountField(value: String, onChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' } && v.length <= 9) onChange(v) },
        textStyle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = SwypColors.Ink),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

