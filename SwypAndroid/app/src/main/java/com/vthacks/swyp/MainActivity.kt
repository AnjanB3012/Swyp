package com.vthacks.swyp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vthacks.swyp.ui.screens.*
import com.vthacks.swyp.ui.theme.SwypColors
import com.vthacks.swyp.ui.theme.SwypTheme

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Cards("Cards", Icons.Outlined.CreditCard, Icons.Filled.CreditCard),
    Pay("Pay", Icons.Filled.Contactless, Icons.Filled.Contactless),
    Scan("Scan", Icons.Filled.QrCodeScanner, Icons.Filled.QrCodeScanner),
    Nearby("Nearby", Icons.Outlined.LocationOn, Icons.Filled.LocationOn),
}

enum class Overlay { Insights, Nfc }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SwypTheme { SwypApp() } }
    }
}

@Composable
fun SwypApp() {
    var tab by remember { mutableStateOf(Tab.Home) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    BackHandler(enabled = overlay != null) { overlay = null }

    Scaffold(
        containerColor = SwypColors.Background,
        bottomBar = {
            if (overlay != Overlay.Nfc) {
                SwypBottomBar(selected = if (overlay == Overlay.Insights) Tab.Home else tab) { tab = it; overlay = null }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (overlay) {
                Overlay.Insights -> InsightsScreen(onBack = { overlay = null }, onExploreOffers = { overlay = null; tab = Tab.Nearby })
                Overlay.Nfc -> NfcDemoScreen(onBack = { overlay = null })
                null -> when (tab) {
                    Tab.Home -> HomeScreen(onChooseCard = { tab = Tab.Pay }, onViewInsights = { overlay = Overlay.Insights })
                    Tab.Cards -> CardsScreen()
                    Tab.Pay -> PayScreen(onTryNfc = { overlay = Overlay.Nfc })
                    Tab.Scan -> ScanScreen(onUseDeal = { tab = Tab.Pay })
                    Tab.Nearby -> NearbyScreen()
                }
            }
        }
    }
}

@Composable
private fun SwypBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = SwypColors.Surface,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Tab.values().forEach { t ->
                val isSel = t == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                        .background(if (isSel) SwypColors.Mint else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onSelect(t) }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (isSel) t.selectedIcon else t.icon, t.label, tint = if (isSel) SwypColors.Teal else SwypColors.Navy.copy(alpha = 0.7f))
                    Text(t.label, fontSize = 14.sp, fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal, color = if (isSel) SwypColors.Teal else SwypColors.Muted)
                }
            }
        }
    }
}
