package com.tapchoice.demo

import android.content.ComponentName
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapchoice.demo.state.TapState
import com.tapchoice.demo.nfc.DemoHostApduService
import com.tapchoice.demo.state.PaymentSessionStore
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private val serviceComponent by lazy { ComponentName(this, DemoHostApduService::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TapChoiceApp() }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter
        val supportsHce = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        val ready = adapter != null && adapter.isEnabled && supportsHce &&
            CardEmulation.getInstance(adapter).setPreferredService(this, serviceComponent)
        PaymentSessionStore.updateHceReady(ready)
        if (!ready) PaymentSessionStore.update(TapState.ERROR)
    }

    override fun onPause() {
        nfcAdapter?.let { CardEmulation.getInstance(it).unsetPreferredService(this) }
        PaymentSessionStore.updateHceReady(false)
        super.onPause()
    }
}

private val Ink = Color(0xFF17231C)
private val Canvas = Color(0xFFF6F7F2)
private val Green = Color(0xFF176B45)
private val Mint = Color(0xFFDDF3E7)

@Composable
fun TapChoiceApp(viewModel: PaymentViewModel = viewModel()) {
    val tapState by viewModel.tapState.collectAsState()
    val hceReady by viewModel.hceReady.collectAsState()
    MaterialTheme {
        Surface(color = Canvas, modifier = Modifier.fillMaxSize()) {
            PaymentScreen(viewModel.uiState, tapState, hceReady, viewModel::reset)
        }
    }
}

@Composable
private fun PaymentScreen(ui: PaymentUiState, tapState: TapState, hceReady: Boolean, onReset: () -> Unit) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("TAPCHOICE", color = Green, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(36.dp))
            Text(ui.merchant, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text(currency.format(ui.amount), color = Ink, fontSize = 46.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(30.dp))
            Text("RECOMMENDED CARD", color = Color(0xFF657168), fontSize = 12.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Ink),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(ui.recommendation.card.network.uppercase(), color = Color(0xFFA9C8B5), fontSize = 12.sp)
                    Spacer(Modifier.height(28.dp))
                    Text(ui.recommendation.card.displayName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("•••• ${ui.recommendation.card.last4}", color = Color(0xFFDCE5DF), fontSize = 17.sp)
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("GROCERY REWARDS", "${ui.recommendation.card.groceryRewardPercent.toInt()}%")
                        Metric("PROJECTED USE", "${(ui.recommendation.projectedUtilization * 100).toInt()}%")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Best value after balancing rewards and projected utilization.",
                color = Color(0xFF657168), fontSize = 14.sp,
            )
        }

        StatusPanel(tapState, hceReady, onReset)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF8EA397), fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusPanel(state: TapState, hceReady: Boolean, onReset: () -> Unit) {
    val (title, subtitle) = when (state) {
        TapState.READY -> if (hceReady) {
            "READY TO PAY" to "NFC active · hold near merchant reader"
        } else {
            "TURN ON NFC" to "NFC card emulation is not available"
        }
        TapState.READING -> "PAYMENT REQUESTED…" to "Reading secure credential"
        TapState.AUTHORIZING -> "AUTHORIZING…" to "Signing transaction challenge"
        TapState.PAID -> "PAID" to "Transaction approved"
        TapState.ERROR -> "TRY AGAIN" to "The NFC exchange was not completed"
    }
    Box(
        modifier = Modifier.fillMaxWidth().background(Mint, RoundedCornerShape(22.dp)).padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(state, label = "payment state") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Green, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, color = Ink, fontSize = 14.sp, textAlign = TextAlign.Center)
                if (it == TapState.PAID || it == TapState.ERROR) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onReset, colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                        Text("Ready for another tap")
                    }
                }
            }
        }
    }
}
