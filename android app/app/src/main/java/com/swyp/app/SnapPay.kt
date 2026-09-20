package com.swyp.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.util.Base64
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.swyp.app.crypto.CryptoManager
import com.swyp.app.nfc.ArmedPayment
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Home-screen widget: one tap opens the camera, the photo is analyzed by the backend, the best
 * eligible card is armed for tap-to-pay, and the app opens straight to the contactless screen — no
 * card selection and no confirmation anywhere in between.
 */
class SnapPayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val open =
            PendingIntent.getActivity(
                context,
                31,
                Intent(context, SnapPayActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val views =
            RemoteViews(context.packageName, R.layout.widget_snap_pay).apply {
                setOnClickPendingIntent(R.id.snap_pay_root, open)
            }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

/**
 * Quick Settings tile companion to the home-screen widget: one tap from the shade starts the same
 * camera → recommend → tap-to-pay flow.
 */
class SnapPayTile : TileService() {
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent =
            Intent(this, SnapPayActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    33,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }
}

/**
 * Fires the camera, then — while staying in the foreground so it can both show progress and open
 * the app — analyzes the bill, ranks the wallet, arms the best eligible card, and hands off to
 * [MainActivity]'s tap-to-pay screen. No confirmation between the photo and the tap.
 */
class SnapPayActivity : ComponentActivity() {
    private var status by mutableStateOf("Reading your bill…")
    private var error by mutableStateOf<String?>(null)

    private val photoFile
        get() = File(cacheDir, "camera/snap.jpg")

    private fun photoUri(): Uri {
        photoFile.parentFile?.mkdirs()
        return FileProvider.getUriForFile(this, "$packageName.files", photoFile)
    }

    private val camera =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            if (saved) recommendAndOpen() else finishAndRemoveTask()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwypTheme {
                SnapPayProgress(
                    status = status,
                    error = error,
                    onRetry = { error = null; launchCamera() },
                    onClose = { finishAndRemoveTask() },
                )
            }
        }
        if (savedInstanceState == null) launchCamera()
    }

    private fun launchCamera() =
        runCatching { camera.launch(photoUri()) }.onFailure { finishAndRemoveTask() }.let {}

    private fun recommendAndOpen() {
        lifecycleScope.launch {
            try {
                status = "Reading your bill…"
                val user =
                    FirebaseAuth.getInstance().currentUser ?: error("Open Swyp and sign in first")
                val token = user.getIdToken(false).await().token ?: error("Sign in again")
                val checkout =
                    withContext(Dispatchers.IO) {
                        val encoded =
                            Base64.encodeToString(photoFile.readBytes(), Base64.NO_WRAP)
                        parseCheckout(post(token, "checkout", JSONObject().put("image", encoded)))
                    }
                require(checkout.amountCents > 0 && checkout.merchant.isNotBlank()) {
                    "Could not read a merchant and total"
                }
                status = "Choosing your best card…"
                val best =
                    withContext(Dispatchers.IO) { recommend(user.uid, checkout) }
                        ?: error("No card is within your utilization target")
                status = "Getting ${best.name} ready to tap…"
                withContext(Dispatchers.IO) { arm(token, best) }
                startActivity(
                    Intent(this@SnapPayActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("armCardId", best.id)
                        .putExtra("armName", best.name)
                        .putExtra("armLast4", best.last4)
                        .putExtra("armProduct", best.product)
                        .putExtra("armLimit", best.limitCents)
                        .putExtra("armBalance", best.balanceCents)
                        .putExtra("armMerchant", checkout.merchant)
                        .putExtra("armAmount", checkout.amountCents)
                        .putExtra("armCategory", checkout.category)
                )
                finishAndRemoveTask()
            } catch (e: Exception) {
                error = e.message ?: "Snap & Pay failed"
            } finally {
                photoFile.delete()
            }
        }
    }

    /** Loads the wallet from Firestore and returns the best eligible card for [checkout], if any. */
    private suspend fun recommend(uid: String, checkout: Checkout): SwypCard? {
        val db = FirebaseFirestore.getInstance()
        val owner = db.collection("users").document(uid)
        val account = owner.get().await()
        val cards = owner.collection("cards").get().await().toObjects(SwypCard::class.java)
        val history = owner.collection("transactions").get().await().toObjects(Purchase::class.java)
        val offers =
            db.collection("deals").get().await().toObjects(Offer::class.java).filter {
                it.verified &&
                    runCatching { !LocalDate.parse(it.expiresOn).isBefore(LocalDate.now()) }
                        .getOrDefault(false)
            }
        val activated =
            (account.get("activatedOffers") as? List<*>)?.filterIsInstance<String>()?.toSet()
                ?: emptySet()
        val ceiling = account.getDouble("utilizationLimit") ?: 0.30
        return Recommender.rank(cards, history, checkout, offers, activated, ceiling)
            .firstOrNull { it.eligible }
            ?.card
    }

    /** Registers this device's key for [card] with the backend and arms it for the tap. */
    private suspend fun arm(token: String, card: SwypCard) {
        val key = CryptoManager().publicKeyDer(card.id)
        post(
            token,
            "credentials",
            JSONObject()
                .put("cardId", card.id)
                .put("publicKey", Base64.encodeToString(key, Base64.NO_WRAP)),
        )
        ArmedPayment.arm(card)
    }

    private fun post(token: String, path: String, body: JSONObject): JSONObject {
        val connection =
            URL(BuildConfig.BACKEND_URL.trimEnd('/') + "/v1/" + path).openConnection()
                as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 90000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val text =
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
            val result = JSONObject(text.ifBlank { "{}" })
            if (code !in 200..299) error(result.optString("error", "Backend unavailable ($code)"))
            result
        } finally {
            connection.disconnect()
        }
    }
}

/** Full-screen progress (and error) surface shown while Snap & Pay picks and arms a card. */
@androidx.compose.runtime.Composable
private fun SnapPayProgress(status: String, error: String?, onRetry: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.HeroBrush)) {
        FlowLines(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            LogoMark(64.dp, onDark = true)
            if (error == null) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                Text(
                    status,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Snap & Pay is choosing your best card",
                    color = Color.White.copy(alpha = .8f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    error,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PrimaryButton("Retake photo", onRetry)
                    SecondaryButton("Close", onClose)
                }
            }
        }
    }
}
