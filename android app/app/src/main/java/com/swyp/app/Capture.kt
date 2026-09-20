package com.swyp.app

import android.app.*
import android.content.*
import android.graphics.*
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.service.quicksettings.TileService
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

private const val ACTION_CAPTURE_NOW = "com.swyp.app.CAPTURE_NOW"
private const val ACTION_STOP_CAPTURE = "com.swyp.app.STOP_CAPTURE"
private const val SESSION_NOTIFICATION = 10
private const val ANALYSIS_NOTIFICATION = 12
private const val RESULT_NOTIFICATION = 13

private fun captureFile(context: Context) = File(context.cacheDir, "pending/checkout.jpg")

class SwypTile : TileService() {
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent =
            Intent(this, CaptureActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }
}

class CaptureActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startForegroundService(
                Intent(this, CaptureService::class.java)
                    .setAction(ACTION_CAPTURE_NOW)
                    .putExtra("code", result.resultCode)
                    .putExtra("data", result.data)
            )
        }
        finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        if (CaptureService.isReady) {
            startService(Intent(this, CaptureService::class.java).setAction(ACTION_CAPTURE_NOW))
            finishAndRemoveTask()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        val request =
            if (Build.VERSION.SDK_INT >= 34)
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            else manager.createScreenCaptureIntent()
        launcher.launch(request)
    }
}

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private var captureAfter = 0L

    companion object {
        @Volatile var isReady = false
            private set
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CAPTURE) {
            projection?.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            SESSION_NOTIFICATION,
            NotificationCompat.Builder(this, "scan")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Swyp screen capture is ready")
                .setContentText("Tap the Quick Settings tile to capture the current screen.")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    PendingIntent.getService(
                        this,
                        19,
                        Intent(this, CaptureService::class.java).setAction(ACTION_STOP_CAPTURE),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .build(),
        )
        if (projection != null) {
            requestCapture(700)
            return START_NOT_STICKY
        }
        try {
            @Suppress("DEPRECATION")
            val data = intent?.getParcelableExtra<Intent>("data") ?: error("No capture permission")
            projection = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(intent.getIntExtra("code", -1), data)
            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    isReady = false
                    stopSelf()
                }
            }, handler)
            val bounds = getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds
            reader = ImageReader.newInstance(bounds.width(), bounds.height(), PixelFormat.RGBA_8888, 2)
            reader!!.setOnImageAvailableListener({ source ->
                val frame = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    if (captureAfter == 0L || SystemClock.elapsedRealtime() < captureAfter)
                        return@setOnImageAvailableListener
                    captureAfter = 0L
                    val plane = frame.planes[0]
                    val paddedWidth = plane.rowStride / plane.pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth, frame.height, Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val result = Bitmap.createBitmap(padded, 0, 0, frame.width, frame.height)
                    if (result !== padded) padded.recycle()
                    val file = captureFile(this)
                    file.parentFile?.mkdirs()
                    file.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    result.recycle()
                    startAnalysis()
                } finally { frame.close() }
            }, handler)
            display = projection!!.createVirtualDisplay(
                "Swyp checkout", bounds.width(), bounds.height(), resources.displayMetrics.densityDpi,
                0, reader!!.surface, null, handler,
            )
            isReady = true
            requestCapture(1100)
        } catch (_: Exception) { stopSelf() }
        return START_NOT_STICKY
    }

    private fun requestCapture(delayMillis: Long) {
        captureAfter = SystemClock.elapsedRealtime() + delayMillis
    }

    private fun startAnalysis() {
        startForegroundService(Intent(this, CaptureAnalyzeService::class.java))
    }

    override fun onDestroy() {
        isReady = false
        captureAfter = 0L
        handler.removeCallbacksAndMessages(null)
        display?.release(); reader?.close(); projection?.stop(); projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

class CaptureAnalyzeService : Service() {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            ANALYSIS_NOTIFICATION,
            NotificationCompat.Builder(this, "scan_results")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle("Swyp is checking this purchase")
                .setContentText("Reading every visible item and comparing your cards…")
                .setOngoing(true)
                .build(),
        )
        work.launch {
            val file = captureFile(this@CaptureAnalyzeService)
            try {
                val user = FirebaseAuth.getInstance().currentUser ?: error("Open Swyp and sign in first")
                val token = user.getIdToken(false).await().token ?: error("Sign in again")
                val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                val checkout = parseCheckout(postCheckout(token, encoded))
                require(checkout.amountCents > 0 && checkout.merchant.isNotBlank()) { "Could not read a merchant and total" }
                val db = FirebaseFirestore.getInstance()
                val owner = db.collection("users").document(user.uid)
                val account = owner.get().await()
                val cards = owner.collection("cards").get().await().toObjects(SwypCard::class.java)
                val history = owner.collection("transactions").get().await().toObjects(Purchase::class.java)
                val offers = db.collection("deals").get().await().toObjects(Offer::class.java).filter {
                    it.verified && runCatching { !LocalDate.parse(it.expiresOn).isBefore(LocalDate.now()) }.getOrDefault(false)
                }
                val activated = (account.get("activatedOffers") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                val ceiling = account.getDouble("utilizationLimit") ?: 0.30
                val best = Recommender.rank(cards, history, checkout, offers, activated, ceiling).firstOrNull { it.eligible }
                val deals = checkout.matches(offers)
                showResult(checkout, best?.card, deals)
            } catch (e: Exception) {
                showFailure(e.message ?: "Capture analysis failed")
            } finally {
                file.delete()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun postCheckout(token: String, image: String): JSONObject {
        return withContext(Dispatchers.IO) {
            val connection = URL(BuildConfig.BACKEND_URL.trimEnd('/') + "/v1/checkout").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"; connection.connectTimeout = 15000; connection.readTimeout = 90000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json"); connection.doOutput = true
                connection.outputStream.use { it.write(JSONObject().put("image", image).toString().toByteArray()) }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val result = JSONObject(text.ifBlank { "{}" })
                if (code !in 200..299) error(result.optString("error", "Backend unavailable ($code)"))
                result
            } finally { connection.disconnect() }
        }
    }

    private fun showResult(checkout: Checkout, card: SwypCard?, deals: List<DealMatch>) {
        val open = PendingIntent.getActivity(
            this, 22,
            Intent(this, MainActivity::class.java).putExtra("tab", "Pay").putExtra("cardId", card?.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (card == null) "No card is within your target" else "Use ${card.name} •••• ${card.last4}"
        val detail = buildString {
            append("${checkout.merchant} · ${money(checkout.amountCents)} · ${checkout.items.size} items")
            if (deals.isNotEmpty()) {
                append(" · Deal: ${deals.first().offer.title}")
                if (deals.size > 1) append(" +${deals.size - 1} more")
            }
        }
        getSystemService(NotificationManager::class.java).notify(
            RESULT_NOTIFICATION,
            NotificationCompat.Builder(this, "scan_results")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setContentIntent(open).setAutoCancel(true).build(),
        )
    }

    private fun showFailure(message: String) {
        getSystemService(NotificationManager::class.java).notify(
            RESULT_NOTIFICATION,
            NotificationCompat.Builder(this, "scan_results")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Swyp could not analyze the screen").setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message)).setAutoCancel(true).build(),
        )
    }

    override fun onDestroy() { work.cancel(); super.onDestroy() }
}

internal fun parseCheckout(json: JSONObject): Checkout {
    val rows = json.optJSONArray("items")
    val items = buildList {
        if (rows != null) for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val name = row.optString("name").trim()
            if (name.isNotBlank()) add(
                CheckoutItem(
                    name, row.optDouble("quantity", 1.0).coerceAtLeast(0.0),
                    row.optLong("unitPriceCents").coerceAtLeast(0), row.optLong("totalPriceCents").coerceAtLeast(0),
                    row.optString("category", "other"),
                )
            )
        }
    }
    return Checkout(json.optString("merchant"), json.optLong("amountCents"), json.optString("category", "other"), json.optString("currency", "USD"), items)
}
