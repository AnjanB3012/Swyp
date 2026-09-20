package com.vthacks.swyp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vthacks.swyp.data.MockData
import com.vthacks.swyp.ui.components.*
import com.vthacks.swyp.ui.theme.SwypColors

@Composable
fun ScanScreen(onUseDeal: () -> Unit) {
    var scanned by remember { mutableStateOf(true) }
    var flash by remember { mutableStateOf(false) }
    val card = MockData.cards.first()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SwypHeader("Demo data") {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(if (flash) SwypColors.Teal else SwypColors.Mint).clickable { flash = !flash }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Bolt, "Flash", tint = if (flash) Color.White else SwypColors.Ink)
            }
        }
        ScreenTitle("Scan & save", "Find a better deal in seconds")
        Viewfinder(scanned, Modifier.padding(horizontal = 20.dp))
        if (scanned) {
            SwypSurface(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductBottle(Color(0xFFC62828), "HEINZ", Modifier.size(56.dp, 84.dp))
                    Column(Modifier.weight(1f).padding(start = 20.dp)) {
                        Text("Scanned item", fontSize = 15.sp, color = SwypColors.Muted)
                        Text("Heinz Tomato Ketchup", style = MaterialTheme.typography.titleLarge)
                        Text("20 oz", fontSize = 16.sp, color = SwypColors.Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Sample prices", fontSize = 12.sp, color = SwypColors.Muted)
                        Text("$4.29", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SwypColors.Ink)
                    }
                }
            }
            SwypSurface(Modifier.padding(horizontal = 20.dp), color = SwypColors.Mint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductBottle(Color(0xFFB71C1C), "Kroger", Modifier.size(56.dp, 84.dp))
                    Column(Modifier.weight(1f).padding(start = 20.dp)) {
                        Pill("Suggested swap", Color(0xFFCFEBE7), SwypColors.Teal, small = true)
                        Text("Kroger Tomato Ketchup", style = MaterialTheme.typography.titleLarge)
                        Text("20 oz · Same size", fontSize = 16.sp, color = SwypColors.Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$2.49", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SwypColors.Ink)
                        Pill("Save $1.80", SwypColors.TealDark, Color.White)
                    }
                }
            }
            SwypSurface(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardThumb(card.color, 96.dp)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("Pay with ${card.name}", style = MaterialTheme.typography.titleMedium)
                        Text("Earn 2% back on this purchase", fontSize = 15.sp, color = SwypColors.Muted)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = SwypColors.Teal)
                }
            }
            Button(onUseDeal, Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(64.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = SwypColors.Teal)) {
                PrimaryButtonLabel("Use this deal")
            }
            TextButton({ scanned = false }, Modifier.align(Alignment.CenterHorizontally)) {
                Text("Scan another item", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = SwypColors.Teal)
            }
        } else {
            SwypSurface(Modifier.padding(horizontal = 20.dp)) {
                Text("Point your camera at a barcode.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 16.sp, color = SwypColors.Muted)
                Spacer(Modifier.height(12.dp))
                Button({ scanned = true }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = SwypColors.Teal)) {
                    PrimaryButtonLabel("Simulate scan")
                }
            }
        }
        Text("Demo offer · Check store price before buying", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 14.sp, color = SwypColors.Muted)
    }
}

@Composable
private fun Viewfinder(detected: Boolean, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF5B3A36), Color(0xFF8A5A4E), Color(0xFFB9AA96)))),
    ) {
        Row(Modifier.fillMaxSize().padding(top = 28.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(5) { i -> ProductBottle(Color(0xFF9E2A2A).copy(alpha = if (i == 2) 1f else 0.55f), if (i == 2) "HEINZ" else "", Modifier.size(64.dp, 150.dp)) }
        }
        Canvas(Modifier.align(Alignment.Center).size(150.dp, 190.dp)) {
            val s = 6.dp.toPx(); val l = 34.dp.toPx()
            val c = Color(0xFF0B7A75)
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(c, Offset(x, y), Offset(x + dx * l, y), s, StrokeCap.Round)
                drawLine(c, Offset(x, y), Offset(x, y + dy * l), s, StrokeCap.Round)
            }
            corner(0f, 0f, 1f, 1f); corner(size.width, 0f, -1f, 1f)
            corner(0f, size.height, 1f, -1f); corner(size.width, size.height, -1f, -1f)
        }
        if (detected) {
            Pill("Barcode detected", SwypColors.TealDark, Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
        }
    }
}

@Composable
private fun ProductBottle(color: Color, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 22.dp, height = 8.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.9f)))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 8.dp, bottomEnd = 8.dp)).background(color), contentAlignment = Alignment.Center) {
            if (label.isNotEmpty()) Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF3E9D2).copy(alpha = 0.25f)).padding(2.dp))
        }
    }
}
