package com.swyp.app

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

object StoreAlerts {
    private fun pending(context: Context) =
        PendingIntent.getBroadcast(
            context,
            7,
            Intent(context, StoreReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    suspend fun enable(context: Context): Int {
        require(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            "Allow precise location first"
        }
        if (android.os.Build.VERSION.SDK_INT >= 30)
            require(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                "In Android settings, allow location all the time, then enable alerts again"
            }
        val stores =
            FirebaseFirestore.getInstance().collection("stores").get().await().documents.take(100)
        val fences =
            stores.mapNotNull { store ->
                val lat = (store.get("latitude") as? Number)?.toDouble()
                val lng = (store.get("longitude") as? Number)?.toDouble()
                if (lat == null || lng == null) null
                else
                    Geofence.Builder()
                        .setRequestId(store.id)
                        .setCircularRegion(
                            lat,
                            lng,
                            ((store.get("radiusMeters") as? Number)?.toDouble() ?: 150.0)
                                .toFloat()
                                .coerceIn(100f, 1000f),
                        )
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                        .setLoiteringDelay(120000)
                        .build()
            }
        require(fences.isNotEmpty()) { "No store locations configured in Firestore yet" }
        LocationServices.getGeofencingClient(context)
            .addGeofences(
                GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
                    .addGeofences(fences)
                    .build(),
                pending(context),
            )
            .await()
        context.getSharedPreferences("swyp-alerts", 0).edit().putBoolean("enabled", true).apply()
        return fences.size
    }

    fun disable(context: Context) {
        LocationServices.getGeofencingClient(context).removeGeofences(pending(context))
        context.getSharedPreferences("swyp-alerts", 0).edit().clear().apply()
    }
}

class StoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (
            event.hasError() ||
                event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_DWELL ||
                FirebaseAuth.getInstance().currentUser == null
        )
            return
        val preferences = context.getSharedPreferences("swyp-alerts", 0)
        if (!preferences.getBoolean("enabled", false)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8000) {
                    val db = FirebaseFirestore.getInstance()
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withTimeout
                    val products =
                        db.collection("users")
                            .document(uid)
                            .collection("cards")
                            .get()
                            .await()
                            .documents
                            .mapNotNull { it.getString("product") }
                            .toSet()
                    if (products.isEmpty()) return@withTimeout
                    for (fence in event.triggeringGeofences.orEmpty()) {
                        if (
                            System.currentTimeMillis() - preferences.getLong(fence.requestId, 0) <
                                6 * 60 * 60 * 1000
                        )
                            continue
                        val store = db.collection("stores").document(fence.requestId).get().await()
                        val merchant = store.getString("merchant") ?: continue
                        val offers =
                            db.collection("deals")
                                .whereEqualTo("merchant", merchant)
                                .get()
                                .await()
                                .toObjects(Offer::class.java)
                                .filter {
                                    it.verified &&
                                        (it.product == "any" || it.product in products) &&
                                        runCatching {
                                                !LocalDate.parse(it.expiresOn)
                                                    .isBefore(LocalDate.now())
                                            }
                                            .getOrDefault(false)
                                }
                        if (offers.isEmpty()) continue
                        val open =
                            PendingIntent.getActivity(
                                context,
                                8,
                                Intent(context, MainActivity::class.java).putExtra("tab", "Nearby"),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            )
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED ||
                                android.os.Build.VERSION.SDK_INT < 33
                        ) {
                            context
                                .getSystemService(NotificationManager::class.java)
                                .notify(
                                    fence.requestId.hashCode(),
                                    NotificationCompat.Builder(context, "deals")
                                        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                                        .setContentTitle("A little extra at $merchant")
                                        .setContentText(
                                            "${offers.size} verified offer(s). Check card eligibility and activate before paying."
                                        )
                                        .setContentIntent(open)
                                        .setAutoCancel(true)
                                        .build(),
                                )
                            preferences
                                .edit()
                                .putLong(fence.requestId, System.currentTimeMillis())
                                .apply()
                        }
                    }
                }
            } catch (_: Exception) {} finally {
                pending.finish()
            }
        }
    }
}
