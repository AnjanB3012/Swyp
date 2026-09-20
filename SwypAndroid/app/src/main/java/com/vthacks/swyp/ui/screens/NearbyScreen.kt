package com.vthacks.swyp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vthacks.swyp.data.MockData
import com.vthacks.swyp.ui.components.*
import com.vthacks.swyp.ui.theme.SwypColors

@Composable
fun NearbyScreen() {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    val deals = MockData.deals.filter {
        (filter == "All" || it.category == filter) &&
            (query.isBlank() || it.store.contains(query, true) || it.headline.contains(query, true))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SwypHeader(null)
        ScreenTitle("Nearby deals")
        SwypSurface(Modifier.padding(horizontal = 20.dp), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Filled.LocationOn, 52.dp)
                Text("Blacksburg, VA", Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Filled.ChevronRight, null, tint = SwypColors.Navy)
            }
        }
        Pill("Sample deals", SwypColors.Mint, SwypColors.Teal, modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(56.dp).clip(CircleShape).background(Color.White).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = SwypColors.Ink)
            Box(Modifier.padding(start = 14.dp)) {
                if (query.isEmpty()) Text("Search stores or deals", fontSize = 17.sp, color = SwypColors.Muted)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(fontSize = 17.sp, color = SwypColors.Ink), modifier = Modifier.fillMaxWidth())
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("All", "Groceries", "Dining").forEach { f ->
                val sel = f == filter
                Text(
                    f,
                    Modifier.clip(CircleShape).background(if (sel) SwypColors.Teal else Color.White).clickable { filter = f }.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = if (sel) Color.White else SwypColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
        FakeMap(Modifier.padding(horizontal = 20.dp))
        SectionTitle("Good deals close by")
        if (deals.isEmpty()) Text("No matching deals.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = SwypColors.Muted)
        deals.forEach { d ->
            SwypSurface(Modifier.padding(horizontal = 20.dp)) {
                Row {
                    IconCircle(d.icon, 60.dp)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(d.store, style = MaterialTheme.typography.titleMedium)
                        Text("${d.distance} · ${d.category}", fontSize = 15.sp, color = SwypColors.Muted)
                        Spacer(Modifier.height(6.dp))
                        Text(d.headline, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SwypColors.Teal)
                        Text(d.cardHint, fontSize = 15.sp, color = SwypColors.Muted)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = SwypColors.Ink)
                }
            }
        }
        Text("Offers shown are examples for the demo.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 14.sp, color = SwypColors.Muted)
    }
}

/** Stylised placeholder map; swap for a real map SDK later. */
@Composable
private fun FakeMap(modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE8EEE9))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFFD5E6CC), size.minDimension * 0.55f, Offset(size.width * 0.62f, size.height * 0.4f))
            val road = Color.White
            drawLine(road, Offset(0f, size.height * 0.35f), Offset(size.width, size.height * 0.95f), 16f, StrokeCap.Round)
            drawLine(road, Offset(size.width * 0.75f, 0f), Offset(size.width * 0.62f, size.height), 12f, StrokeCap.Round)
            drawLine(road, Offset(size.width * 0.25f, 0f), Offset(size.width * 0.4f, size.height), 8f, StrokeCap.Round)
            drawLine(road, Offset(0f, size.height * 0.75f), Offset(size.width * 0.5f, size.height * 0.2f), 8f, StrokeCap.Round)
            drawCircle(Color(0x332F80ED), 34f, Offset(size.width * 0.48f, size.height * 0.5f))
            drawCircle(Color.White, 15f, Offset(size.width * 0.48f, size.height * 0.5f))
            drawCircle(Color(0xFF2F80ED), 11f, Offset(size.width * 0.48f, size.height * 0.5f))
        }
        Text("Virginia Tech", Modifier.align(Alignment.TopCenter).padding(top = 40.dp), color = SwypColors.Muted, fontSize = 14.sp)
        Icon(Icons.Filled.LocationOn, null, Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 30.dp).size(40.dp), tint = SwypColors.TealDark)
        Icon(Icons.Filled.LocationOn, null, Modifier.align(Alignment.BottomEnd).padding(end = 60.dp, bottom = 40.dp).size(40.dp), tint = SwypColors.TealDark)
    }
}
