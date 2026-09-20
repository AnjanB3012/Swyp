package com.swyp.app

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.swyp.app.crypto.CryptoManager
import com.swyp.app.nfc.ArmedPayment
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

data class UiState(
    val configured: Boolean = false,
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val message: String = "",
    val email: String = "",
    val status: String = "",
    val profileLoaded: Boolean = false,
    val fullName: String = "",
    val age: Int = 0,
    val phone: String = "",
    val homeZip: String = "",
    val cards: List<SwypCard> = emptyList(),
    val history: List<Purchase> = emptyList(),
    val offers: List<Offer> = emptyList(),
    val stores: List<StoreLocation> = emptyList(),
    val checkout: Checkout = Checkout(),
    val ranks: List<RankedCard> = emptyList(),
    val selected: String? = null,
    val armed: Boolean = false,
    val ceiling: Double = 0.30,
    val activated: Set<String> = emptySet(),
)

class SwypViewModel(app: Application) : AndroidViewModel(app) {
    private val mutable =
        MutableStateFlow(UiState(configured = FirebaseApp.getApps(app).isNotEmpty()))
    val state = mutable.asStateFlow()
    private val auth: FirebaseAuth? =
        if (mutable.value.configured) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? =
        if (mutable.value.configured) FirebaseFirestore.getInstance() else null
    private val listeners = mutableListOf<ListenerRegistration>()
    private val processing = mutableSetOf<String>()
    private var sessionJob: Job? = null
    private var initJob: Job? = null
    private var settingsJob: Job? = null
    private var dealsRefreshed = false
    private val authListener =
        FirebaseAuth.AuthStateListener { a ->
            listeners.forEach { it.remove() }
            listeners.clear()
            processing.clear()
            ArmedPayment.clear()
            sessionJob?.cancel()
            initJob?.cancel()
            settingsJob?.cancel()
            dealsRefreshed = false
            val user = a.currentUser
            mutable.value =
                UiState(configured = true, signedIn = user != null, email = user?.email.orEmpty())
            if (user != null) {
                observe(user.uid)
                initJob =
                    viewModelScope.launch {
                        runCatching { api("initialize") }
                            .onFailure { message(it.message ?: "Could not initialize account") }
                    }
            }
        }

    init {
        auth?.addAuthStateListener(authListener)
    }

    fun message(text: String) {
        mutable.value = mutable.value.copy(message = text)
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, message = "")
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message(e.message ?: "Operation failed")
            } finally {
                mutable.value = mutable.value.copy(busy = false)
            }
        }
    }

    fun signIn(email: String, password: String) = action {
        require(email.isNotBlank() && password.isNotBlank()) { "Enter email and password" }
        auth!!.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun signUp(email: String, password: String) = action {
        require(password.length >= 8) { "Use at least 8 characters" }
        auth!!.createUserWithEmailAndPassword(email.trim(), password).await()
        auth.currentUser?.sendEmailVerification()?.await()
    }

    fun reset(email: String) = action {
        require(email.isNotBlank()) { "Enter your email first" }
        auth!!.sendPasswordResetEmail(email.trim()).await()
        message("Password reset email requested. Check your inbox.")
    }

    fun signOut() {
        ArmedPayment.clear()
        StoreAlerts.disable(getApplication())
        auth?.signOut()
    }

    fun createCards() = action {
        api("cards")
        message("Creating three cards through Nessie…")
    }

    private fun observe(uid: String) {
        val user = db!!.collection("users").document(uid)
        listeners +=
            user.addSnapshotListener { snap, error ->
                if (error != null) message(error.message.orEmpty())
                if (snap != null) {
                    mutable.value =
                        mutable.value.copy(
                            status = snap.getString("status").orEmpty(),
                            profileLoaded = snap.exists(),
                            fullName = snap.getString("fullName").orEmpty(),
                            age = (snap.getLong("age") ?: 0).toInt(),
                            phone = snap.getString("phone").orEmpty(),
                            homeZip = snap.getString("homeZip").orEmpty(),
                            ceiling = snap.getDouble("utilizationLimit") ?: 0.30,
                            activated =
                                (snap.get("activatedOffers") as? List<*>)
                                    ?.filterIsInstance<String>()
                                    ?.toSet() ?: emptySet(),
                        )
                    recompute()
                    snap.getString("message")?.let(::message)
                }
            }
        listeners +=
            user.collection("cards").addSnapshotListener { snap, error ->
                if (error != null) message(error.message.orEmpty())
                if (snap != null) {
                    mutable.value = mutable.value.copy(cards = snap.toObjects(SwypCard::class.java))
                    recompute()
                }
            }
        listeners +=
            user.collection("transactions").addSnapshotListener { snap, error ->
                if (error != null) message(error.message.orEmpty())
                if (snap != null) {
                    mutable.value =
                        mutable.value.copy(
                            history =
                                snap.toObjects(Purchase::class.java).sortedByDescending { it.date }
                        )
                    recompute()
                }
            }
        listeners +=
            db.collection("deals").addSnapshotListener { snap, error ->
                if (error != null) message(error.message.orEmpty())
                if (snap != null) {
                    mutable.value =
                        mutable.value.copy(
                            offers =
                                snap.toObjects(Offer::class.java).filter {
                                    it.verified &&
                                        runCatching {
                                                !java.time.LocalDate.parse(it.expiresOn)
                                                    .isBefore(java.time.LocalDate.now())
                                            }
                                            .getOrDefault(false)
                                }
                        )
                    recompute()
                }
            }
        listeners +=
            db.collection("stores").addSnapshotListener { snap, error ->
                if (error != null) message(error.message.orEmpty())
                if (snap != null) {
                    mutable.value =
                        mutable.value.copy(
                            stores =
                                snap.documents.mapNotNull { doc ->
                                    val latitude = (doc.get("latitude") as? Number)?.toDouble()
                                    val longitude = (doc.get("longitude") as? Number)?.toDouble()
                                    if (latitude == null || longitude == null) null
                                    else
                                        StoreLocation(
                                            id = doc.id,
                                            merchant = doc.getString("merchant").orEmpty(),
                                            name = doc.getString("name") ?: doc.getString("merchant").orEmpty(),
                                            address = doc.getString("address").orEmpty(),
                                            latitude = latitude,
                                            longitude = longitude,
                                            radiusMeters =
                                                (doc.get("radiusMeters") as? Number)?.toDouble()
                                                    ?: 150.0,
                                        )
                                }
                        )
                }
            }
        listeners +=
            user.collection("paymentRequests").addSnapshotListener { snap, error ->
                if (error != null) message(error.message.orEmpty())
                snap?.documentChanges?.forEach { change ->
                    val doc = change.document
                    if (change.type == DocumentChange.Type.REMOVED) return@forEach
                    when (doc.getString("status")) {
                        "pending" ->
                            if (processing.add(doc.id)) {
                                viewModelScope.launch {
                                    try {
                                        api("payments/process", JSONObject().put("nonce", doc.id))
                                        ArmedPayment.clear()
                                        mutable.value =
                                            mutable.value.copy(
                                                armed = false,
                                                message = "Purchase posted to Nessie.",
                                            )
                                    } catch (e: Exception) {
                                        message(e.message ?: "Payment processing failed")
                                    }
                                }
                            }
                        "posted" -> {
                            if (
                                mutable.value.armed && change.type == DocumentChange.Type.MODIFIED
                            ) {
                                ArmedPayment.clear()
                                mutable.value =
                                    mutable.value.copy(
                                        armed = false,
                                        message = "Purchase posted to Nessie.",
                                    )
                            }
                        }
                        "needs_reconciliation" ->
                            message(
                                "A purchase needs reconciliation. Do not tap again; inspect the backend."
                            )
                    }
                }
            }
    }

    fun retryPending() {
        processing.clear()
        action {
            val uid = auth?.currentUser?.uid ?: return@action
            val pending =
                db!!
                    .collection("users")
                    .document(uid)
                    .collection("paymentRequests")
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()
            for (doc in pending) api("payments/process", JSONObject().put("nonce", doc.id))
            message("Pending requests checked.")
        }
    }

    fun checkout(value: Checkout) {
        ArmedPayment.clear()
        mutable.value = mutable.value.copy(checkout = value, armed = false, selected = null)
        recompute()
    }

    fun ceiling(value: Double) {
        ArmedPayment.clear()
        mutable.value = mutable.value.copy(ceiling = value, armed = false)
        recompute()
        settingsJob?.cancel()
        settingsJob =
            viewModelScope.launch {
                delay(500)
                runCatching { api("settings", JSONObject().put("utilizationLimit", value)) }
                    .onFailure { message("Could not save utilization target") }
            }
    }

    fun activate(id: String) = action {
        api("settings", JSONObject().put("activatedOffer", id))
        mutable.value = mutable.value.copy(activated = mutable.value.activated + id)
        recompute()
    }

    fun saveProfile(fullName: String, age: String, phone: String, homeZip: String) = action {
        val parsedAge = age.toIntOrNull() ?: error("Enter a valid age")
        api(
            "settings",
            JSONObject()
                .put("fullName", fullName.trim())
                .put("age", parsedAge)
                .put("phone", phone.trim())
                .put("homeZip", homeZip.trim()),
        )
        message("Profile saved.")
    }

    fun select(id: String) {
        ArmedPayment.clear()
        mutable.value = mutable.value.copy(selected = id, armed = false)
    }

    private fun recompute() {
        val s = mutable.value
        val ranks =
            if (s.checkout.amountCents > 0 && s.checkout.currency == "USD")
                Recommender.rank(s.cards, s.history, s.checkout, s.offers, s.activated, s.ceiling)
            else emptyList()
        mutable.value =
            s.copy(
                ranks = ranks,
                selected =
                    s.selected?.takeIf { id -> s.cards.any { it.id == id } }
                        ?: ranks.firstOrNull { it.eligible }?.card?.id,
            )
    }

    fun scan(bitmap: Bitmap) = action {
        val result =
            withContext(Dispatchers.Default) {
                val ratio = minOf(1.0, 1600.0 / maxOf(bitmap.width, bitmap.height))
                val scaled =
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * ratio).toInt(),
                        (bitmap.height * ratio).toInt(),
                        true,
                    )
                val output = java.io.ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            }
        val json = api("checkout", JSONObject().put("image", result))
        val rows = json.optJSONArray("items")
        val items =
            buildList {
                if (rows != null) {
                    for (index in 0 until rows.length()) {
                        val row = rows.optJSONObject(index) ?: continue
                        val name = row.optString("name").trim()
                        if (name.isBlank()) continue
                        add(
                            CheckoutItem(
                                name = name,
                                quantity = row.optDouble("quantity", 1.0).coerceAtLeast(0.0),
                                unitPriceCents = row.optLong("unitPriceCents").coerceAtLeast(0),
                                totalPriceCents = row.optLong("totalPriceCents").coerceAtLeast(0),
                                category = row.optString("category", "other"),
                            )
                        )
                    }
                }
            }
        checkout(
            Checkout(
                json.optString("merchant"),
                json.optLong("amountCents"),
                json.optString("category", "other"),
                json.optString("currency", "USD"),
                items,
            )
        )
        message("Review every detected item, the merchant, and total before paying.")
    }

    fun arm() = action {
        val owner = auth?.currentUser?.uid ?: error("Sign in required")
        val s = mutable.value
        val card = s.cards.find { it.id == s.selected } ?: error("Select a card")
        val eligible =
            if (s.ranks.isNotEmpty()) s.ranks.find { it.card.id == card.id }?.eligible == true
            else card.limitCents > 0 && card.balanceCents.toDouble() / card.limitCents <= s.ceiling
        require(eligible) { "This card is over your utilization target. Select another card." }
        val key = CryptoManager().publicKeyDer(card.id)
        api(
            "credentials",
            JSONObject()
                .put("cardId", card.id)
                .put("publicKey", Base64.encodeToString(key, Base64.NO_WRAP)),
        )
        require(
            auth?.currentUser?.uid == owner &&
                mutable.value.selected == card.id
        ) {
            "Card selection changed. Confirm again."
        }
        ArmedPayment.arm(card)
        mutable.value =
            mutable.value.copy(
                armed = true,
                message = "${card.name} is ready to tap for 2 minutes.",
            )
        sessionJob?.cancel()
        sessionJob =
            viewModelScope.launch {
                delay(120000)
                ArmedPayment.clear()
                mutable.value = mutable.value.copy(armed = false)
            }
    }

    /**
     * Asks the backend to run today's deal search if it hasn't already run. The backend caches
     * results for the whole day; published offers arrive through the [db] "deals" listener, so we
     * only need to trigger it once per session. Non-fatal on failure.
     */
    fun refreshDeals() {
        if (dealsRefreshed || auth?.currentUser == null) return
        dealsRefreshed = true
        viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true)
            try {
                api("deals/refresh")
            } catch (e: Exception) {
                dealsRefreshed = false
            } finally {
                mutable.value = mutable.value.copy(busy = false)
            }
        }
    }

    fun cancelTap() {
        sessionJob?.cancel()
        ArmedPayment.clear()
        mutable.value = mutable.value.copy(armed = false)
    }

    /**
     * Enters tap-to-pay for a card chosen and armed outside the UI (the Snap & Pay widget already
     * posted the credential and armed [ArmedPayment]). This only mirrors that state into the UI so
     * the full-screen tap view appears, with no card selection or confirmation step. Resilient to a
     * cold start: [card] is injected into the (possibly still-loading) wallet so the tap view can
     * render before Firestore snapshots arrive; once they do, the matching id keeps it selected.
     */
    fun applyExternalArm(card: SwypCard, checkout: Checkout) {
        ArmedPayment.arm(card)
        val known = mutable.value.cards
        val cards = if (known.any { it.id == card.id }) known else known + card
        mutable.value =
            mutable.value.copy(
                cards = cards,
                checkout = checkout,
                selected = card.id,
                armed = true,
                message = "${card.name} is ready to tap for 2 minutes.",
            )
        recompute()
        sessionJob?.cancel()
        sessionJob =
            viewModelScope.launch {
                delay(120000)
                ArmedPayment.clear()
                mutable.value = mutable.value.copy(armed = false)
            }
    }

    private suspend fun api(path: String, body: JSONObject = JSONObject()): JSONObject {
        val token =
            auth?.currentUser?.getIdToken(false)?.await()?.token ?: error("Sign in required")
        return withContext(Dispatchers.IO) {
            val connection =
                URL(BuildConfig.BACKEND_URL.trimEnd('/') + "/v1/" + path).openConnection()
                    as HttpURLConnection
            try {
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
                if (code !in 200..299)
                    error(result.optString("error", "Backend unavailable ($code)"))
                result
            } finally {
                connection.disconnect()
            }
        }
    }

    override fun onCleared() {
        auth?.removeAuthStateListener(authListener)
        listeners.forEach { it.remove() }
        ArmedPayment.clear()
        super.onCleared()
    }
}
