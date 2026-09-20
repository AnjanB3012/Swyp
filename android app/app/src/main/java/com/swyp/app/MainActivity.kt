package com.swyp.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private val Navy = Color(0xFF061A3D)
private val Teal = Color(0xFF007C75)
private val Mist = Color(0xFFE5F6F3)
private val Paper = Color(0xFFF7F9FA)
private val Orange = Color(0xFFE87800)

class MainActivity : ComponentActivity() {
    private val vm: SwypViewModel by viewModels()
    private var pendingImage by mutableStateOf<Bitmap?>(null)
    private var currentTab by mutableStateOf("Home")
    private var selectedCardDetail by mutableStateOf<String?>(null)
    private var fineLocationGranted by mutableStateOf(false)
    private val photoFile get() = java.io.File(cacheDir, "camera/checkout.jpg")

    private fun photoUri(): Uri {
        photoFile.parentFile?.mkdirs()
        return androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", photoFile)
    }

    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) loadImage(photoUri())
        photoFile.delete()
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { it?.let(::loadImage) }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val location = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        fineLocationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        enableLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fineLocationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        receive(intent)
        if (android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Teal, secondary = Navy, background = Paper, surface = Color.White)
            ) {
                val s by vm.state.collectAsState()
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    when {
                        !s.configured -> SetupScreen()
                        !s.signedIn -> LoginScreen(s, vm)
                        !s.profileLoaded -> LoadingAccountScreen()
                        s.fullName.isBlank() || s.age == 0 || s.phone.isBlank() || s.homeZip.isBlank() ->
                            ProfileSetupScreen(s, vm)
                        else -> Scaffold(
                            containerColor = Paper,
                            bottomBar = {
                                NavigationBar(containerColor = Color.White) {
                                    listOf("Home" to "⌂", "Cards" to "▭", "Pay" to "◖)))", "Scan" to "⌗", "Nearby" to "⌖")
                                        .forEach { (name, icon) ->
                                            NavigationBarItem(
                                                selected = currentTab == name,
                                                onClick = { currentTab = name; selectedCardDetail = null },
                                                icon = { Text(icon, fontSize = 21.sp, fontWeight = FontWeight.Bold) },
                                                label = { Text(name) },
                                            )
                                        }
                                }
                            },
                        ) { insets ->
                            Column(
                                Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Spacer(Modifier.height(5.dp))
                                AppHeader { vm.signOut(); pendingImage = null }
                                if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Teal)
                                if (s.message.isNotBlank()) InfoCard(s.message)
                                val detail = selectedCardDetail?.let { id -> s.cards.find { it.id == id } }
                                when {
                                    detail != null -> CardDetail(detail, s.history.filter { it.cardId == detail.id }) { selectedCardDetail = null }
                                    currentTab == "Home" -> Home(s, { currentTab = "Pay" }, { currentTab = "Insights" })
                                    currentTab == "Cards" -> Cards(s, vm) { selectedCardDetail = it }
                                    currentTab == "Pay" -> Pay(s, vm)
                                    currentTab == "Scan" -> Scan(
                                        s, vm, pendingImage, { pendingImage = null },
                                        { camera.launch(photoUri()) }, { imagePicker.launch("image/*") },
                                        { startActivity(Intent(this@MainActivity, CaptureActivity::class.java)) },
                                        { currentTab = "Pay" },
                                    )
                                    currentTab == "Nearby" -> Nearby(
                                        s, vm, fineLocationGranted,
                                        {
                                            location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                            if (android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        },
                                        { StoreAlerts.disable(this@MainActivity); vm.message("Nearby alerts turned off.") },
                                    )
                                    currentTab == "Insights" -> Insights(s, vm) { currentTab = "Home" }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }

    private fun receive(intent: Intent) {
        intent.getStringExtra("tab")?.let { currentTab = if (it == "Offers") "Nearby" else it }
        intent.getStringExtra("cardId")?.let(vm::select)
        if (intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            uri?.let(::loadImage)
        }
    }

    private fun loadImage(uri: Uri) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                val ratio = minOf(1.0, 1600.0 / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * ratio).toInt(), (info.size.height * ratio).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.onSuccess { pendingImage = it; currentTab = "Scan" }
            .onFailure { vm.message("Could not open this image") }
    }

    private fun enableLocation() {
        lifecycleScope.launch {
            runCatching { StoreAlerts.enable(this@MainActivity) }
                .onSuccess { fineLocationGranted = true; vm.message("Nearby alerts enabled for $it stores.") }
                .onFailure {
                    vm.message(it.message.orEmpty())
                    if (android.os.Build.VERSION.SDK_INT >= 30 && fineLocationGranted)
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
        }
    }
}

@Composable private fun LoadingAccountScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = Teal)
            Text("Loading your Swyp account…", color = Navy)
        }
    }
}

@Composable private fun ProfileSetupScreen(s: UiState, vm: SwypViewModel) {
    var name by remember(s.fullName) { mutableStateOf(s.fullName) }
    var age by remember(s.age) { mutableStateOf(if (s.age > 0) s.age.toString() else "") }
    var phone by remember(s.phone) { mutableStateOf(s.phone) }
    var zip by remember(s.homeZip) { mutableStateOf(s.homeZip) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Swyp", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Navy)
        Text("Finish your profile", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("This information stays connected to your account and is required before using your wallet.", color = Color.Gray)
        OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, label = { Text("Age") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it.take(20) }, label = { Text("Phone number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(zip, { zip = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, label = { Text("Home ZIP code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.saveProfile(name, age, phone, zip) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Save and continue") }
        if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (s.message.isNotBlank()) Text(s.message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = vm::signOut) { Text("Sign out") }
    }
}

@Composable private fun AppHeader(signOut: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(42.dp).background(Teal, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("S", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            Text("Swyp", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
        TextButton(onClick = signOut) { Text("Sign out") }
    }
}

@Composable private fun SetupScreen() {
    Column(Modifier.fillMaxSize().padding(30.dp), verticalArrangement = Arrangement.Center) {
        Text("Swyp", fontSize = 54.sp, fontWeight = FontWeight.Black, color = Navy)
        Text("One purchase. A smarter choice.", fontSize = 28.sp)
        Spacer(Modifier.height(24.dp))
        Text("Connect Firebase in android app/local.properties, then rebuild. See SETUP.md.")
    }
}

@Composable private fun LoginScreen(s: UiState, vm: SwypViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signup by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(50.dp))
        Text("Swyp", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Navy)
        Text("Make every\npurchase count.", fontSize = 40.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Your cards. Their rewards. One thoughtful choice.", color = Color.Gray)
        OutlinedTextField(email, { email = it }, label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (signup) vm.signUp(email, password) else vm.signIn(email, password) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text(if (signup) "Create account" else "Sign in")
        }
        TextButton(onClick = { signup = !signup }) { Text(if (signup) "Already have an account? Sign in" else "New here? Create an account") }
        TextButton(onClick = { vm.reset(email) }) { Text("Forgot password?") }
        if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (s.message.isNotBlank()) Text(s.message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable private fun Home(s: UiState, onPay: () -> Unit, onInsights: () -> Unit) {
    val month = LocalDate.now().toString().take(7)
    val monthRows = s.history.filter { it.date.startsWith(month) }
    val spent = monthRows.sumOf { it.amountCents }
    val limit = s.cards.sumOf { it.limitCents }
    val balance = s.cards.sumOf { it.balanceCents }
    val util = if (limit > 0) balance.toDouble() / limit else 0.0
    val rewards = monthRows.sumOf { row ->
        val card = s.cards.find { it.id == row.cardId }
        if (card == null) 0L else (row.amountCents * Recommender.rate(card, Checkout(row.merchant, row.amountCents, row.category))).roundToLong()
    }
    Text("Your money, in view", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Navy)
    Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This month", color = Color.White, fontSize = 18.sp)
            Text(money(spent), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text("Across ${s.cards.size} cards", color = Mist)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Rewards earned", money(rewards), Modifier.weight(1f))
        MetricCard("Credit used", "${(util * 100).toInt()}%", Modifier.weight(1f))
    }
    Text("Best card for your next shop", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
    val best = s.ranks.firstOrNull { it.eligible }?.card ?: s.cards.minByOrNull { if (it.limitCents > 0) it.balanceCents.toDouble() / it.limitCents else 1.0 }
    if (best != null) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(best.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text("•••• ${best.last4} · ${rewardLabel(best.product)}")
                Button(onClick = onPay, modifier = Modifier.fillMaxWidth()) { Text("Choose a card →") }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Recent activity", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
        TextButton(onClick = onInsights) { Text("View insights ›") }
    }
    ActivityList(s.history.take(5))
}

@Composable private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) { Text(label, color = Color.DarkGray); Text(value, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Navy) }
    }
}

@Composable private fun Cards(s: UiState, vm: SwypViewModel, open: (String) -> Unit) {
    Text("Your cards", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Navy)
    Text("A clear view of your credit.", fontSize = 18.sp, color = Color.Gray)
    val limit = s.cards.sumOf { it.limitCents }
    val balance = s.cards.sumOf { it.balanceCents }
    val util = if (limit > 0) balance.toDouble() / limit else 0.0
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Total credit used")
            Text("${(util * 100).toInt()}%", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text("${money(balance)} of ${money(limit)}")
            LinearProgressIndicator({ util.toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth(), color = Teal, trackColor = Mist)
        }
    }
    if (s.cards.isEmpty()) {
        Text(if (s.status == "generating") "Creating your Nessie wallet and transaction history…" else "Your wallet is empty.")
        if (s.status == "ready") Button(onClick = vm::createCards, enabled = !s.busy) { Text("Add cards") }
    }
    s.cards.forEachIndexed { index, card -> CardRow(card, index, { open(card.id) }) }
}

@Composable private fun CardRow(card: SwypCard, index: Int, open: () -> Unit) {
    val util = if (card.limitCents > 0) card.balanceCents.toDouble() / card.limitCents else 0.0
    Card(Modifier.fillMaxWidth().clickable(onClick = open), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp, 48.dp).background(cardGradient(index), RoundedCornerShape(10.dp)))
                    Column { Text(card.name, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Navy); Text("•••• ${card.last4}"); Text(rewardLabel(card.product), fontSize = 12.sp) }
                }
                Text("${(util * 100).toInt()}% used", color = if (util > .3) Orange else Teal, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator({ util.toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth(), color = if (util > .3) Orange else Teal, trackColor = Mist)
            Text("${money(card.balanceCents)} / ${money(card.limitCents)}", fontSize = 12.sp)
        }
    }
}

@Composable private fun CardDetail(card: SwypCard, rows: List<Purchase>, back: () -> Unit) {
    TextButton(onClick = back) { Text("← All cards") }
    Text(card.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Navy)
    Card(colors = CardDefaults.cardColors(containerColor = Navy), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("•••• ${card.last4}", color = Color.White, fontSize = 22.sp)
            Text(rewardLabel(card.product), color = Mist)
            Text("${money(card.balanceCents)} of ${money(card.limitCents)} used", color = Color.White)
        }
    }
    Text("Transactions", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
    if (rows.isEmpty()) Text("No transactions on this card yet.", color = Color.Gray) else ActivityList(rows)
}

@Composable private fun Pay(s: UiState, vm: SwypViewModel) {
    Text("Choose & pay", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Navy)
    Text("Select the card you want to present to the Swyp reader.", fontSize = 18.sp, color = Color.Gray)
    if (s.checkout.amountCents > 0) {
        InfoCard("${s.checkout.merchant} · ${money(s.checkout.amountCents)}${if (s.checkout.items.isNotEmpty()) " · ${s.checkout.items.size} items" else ""}")
    }
    val ordered = if (s.ranks.isEmpty()) s.cards else s.ranks.map { it.card }
    ordered.forEach { card ->
        val rank = s.ranks.find { it.card.id == card.id }
        val currentUtil = if (card.limitCents > 0) card.balanceCents.toDouble() / card.limitCents else 1.0
        val eligible = rank?.eligible ?: (currentUtil <= s.ceiling)
        Card(
            Modifier.fillMaxWidth().clickable(enabled = eligible) { vm.select(card.id) },
            colors = CardDefaults.cardColors(containerColor = if (s.selected == card.id) Mist else Color.White),
        ) {
            Row(Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text(card.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy); Text("•••• ${card.last4} · ${rewardLabel(card.product)}", fontSize = 12.sp); if (rank != null) Text("${money(rank.rewardCents)} estimated rewards", color = Teal) }
                Text(if (eligible) if (s.selected == card.id) "✓" else "○" else "Over target", color = if (eligible) Teal else Orange)
            }
        }
    }
    val selected = s.cards.find { it.id == s.selected }
    val selectedRank = s.ranks.find { it.card.id == s.selected }
    val eligible = selected != null && (selectedRank?.eligible ?: (selected.limitCents > 0 && selected.balanceCents.toDouble() / selected.limitCents <= s.ceiling))
    Button(onClick = vm::arm, enabled = !s.busy && !s.armed && s.status == "ready" && eligible, modifier = Modifier.fillMaxWidth().height(58.dp)) {
        Text(if (s.armed) "Ready to tap" else "Ready to pay with ${selected?.name ?: "selected card"}")
    }
    if (s.armed) {
        Card(colors = CardDefaults.cardColors(containerColor = Teal), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("◖)))", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Column {
                    Text("Ready to tap", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${selected?.name} •••• ${selected?.last4}", color = Mist)
                    Text("Hold near the iPhone reader within 2 minutes", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        TextButton(onClick = vm::cancelTap) { Text("Cancel") }
    }
}

@Composable private fun Scan(s: UiState, vm: SwypViewModel, image: Bitmap?, clear: () -> Unit, camera: () -> Unit, pick: () -> Unit, capture: () -> Unit, pay: () -> Unit) {
    Text("Scan & save", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Navy)
    Text("We read every visible bill item and check for matching offers.", fontSize = 18.sp, color = Color.Gray)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = camera) { Text("Camera") }; OutlinedButton(onClick = pick) { Text("Photo") }; OutlinedButton(onClick = capture) { Text("Screenshot") }
    }
    Text("Add “Scan with Swyp” in Quick Settings, or share a screenshot to Swyp.", fontSize = 12.sp, color = Color.Gray)
    if (image != null) {
        Image(image.asImageBitmap(), "Bill preview", Modifier.fillMaxWidth().height(210.dp))
        Row { Button(onClick = { vm.scan(image); clear() }, enabled = !s.busy) { Text("Analyze all items") }; TextButton(onClick = clear) { Text("Discard") } }
    }
    var merchant by remember(s.checkout.merchant) { mutableStateOf(s.checkout.merchant) }
    var amount by remember(s.checkout.amountCents) { mutableStateOf(if (s.checkout.amountCents > 0) BigDecimal(s.checkout.amountCents).movePointLeft(2).toPlainString() else "") }
    var category by remember(s.checkout.category) { mutableStateOf(s.checkout.category) }
    OutlinedTextField(merchant, { merchant = it; vm.cancelTap() }, label = { Text("Merchant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(amount, { amount = it; vm.cancelTap() }, label = { Text("Total in USD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("grocery", "dining", "entertainment", "streaming", "travel", "gas", "other").forEach {
            FilterChip(selected = category == it, onClick = { category = it; vm.cancelTap() }, label = { Text(it) })
        }
    }
    Button(onClick = {
        val cents = runCatching { BigDecimal(amount).movePointRight(2).longValueExact() }.getOrNull()
        if (cents == null || cents !in 1..100000000 || merchant.isBlank()) vm.message("Enter a merchant and valid USD amount")
        else vm.checkout(Checkout(merchant.trim(), cents, category, "USD", s.checkout.items))
    }, modifier = Modifier.fillMaxWidth()) { Text("Compare my cards") }
    if (s.checkout.items.isNotEmpty()) {
        Text("Items found (${s.checkout.items.size})", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                s.checkout.items.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.quantity.toCleanQuantity()} × ${item.name}", Modifier.weight(1f)); Text(money(item.totalPriceCents))
                    }
                    if (index < s.checkout.items.lastIndex) HorizontalDivider()
                }
            }
        }
        val matches = s.checkout.matches(s.offers)
        Text("Deals for this bill", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
        if (matches.isEmpty()) Text("No verified item offers match this bill right now.", color = Color.Gray)
        matches.forEach { match -> DealCard(match.offer, match.itemName, s, vm) }
    }
    if (s.ranks.isNotEmpty()) {
        Text("Recommended card", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
        s.ranks.take(3).forEach { rank ->
            Card(Modifier.fillMaxWidth().clickable(enabled = rank.eligible) { vm.select(rank.card.id) }, colors = CardDefaults.cardColors(containerColor = if (s.selected == rank.card.id) Mist else Color.White)) {
                Column(Modifier.padding(16.dp)) { Text(rank.card.name, fontWeight = FontWeight.Bold, color = Navy); Text("${money(rank.rewardCents)} reward value · ${(rank.projectedUtilization * 100).toInt()}% projected use"); Text(rank.reason, fontSize = 12.sp) }
            }
        }
        Button(onClick = pay, modifier = Modifier.fillMaxWidth()) { Text("Continue to Tap to Pay") }
    }
}

@Composable private fun DealCard(offer: Offer, itemName: String?, s: UiState, vm: SwypViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = Mist), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(offer.title, fontWeight = FontWeight.Bold, color = Navy)
            if (itemName != null) Text("Matches $itemName", color = Teal)
            Text("${offer.merchant} · through ${offer.expiresOn}", fontSize = 12.sp)
            if (offer.requiresActivation) OutlinedButton(onClick = { vm.activate(offer.id) }) { Text(if (offer.id in s.activated) "Activated" else "Activate offer") }
        }
    }
}

@Composable private fun Nearby(s: UiState, vm: SwypViewModel, locationGranted: Boolean, enable: () -> Unit, disable: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Text("Nearby deals", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Navy)
    if (!locationGranted) {
        Card(colors = CardDefaults.cardColors(containerColor = Mist)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("See offers after you stay at a participating store for two minutes.")
                OutlinedButton(onClick = enable) { Text("Allow location") }
            }
        }
    } else TextButton(onClick = disable) { Text("Turn off nearby alerts") }
    if (s.stores.isNotEmpty()) {
        NearbyMap(s.stores)
        Text("Participating stores", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
        s.stores.forEach { store ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(store.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Text(store.address, color = Color.Gray)
                }
            }
        }
    }
    if (s.offers.isEmpty()) InfoCard("No verified offers are available right now.")
    s.offers.forEach { offer ->
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(offer.merchant, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text(offer.title, fontSize = 18.sp, color = Teal)
                Text("Use ${offer.product} · through ${offer.expiresOn}", fontSize = 12.sp)
                TextButton(onClick = { if (offer.sourceUrl.startsWith("https://")) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(offer.sourceUrl))) }) { Text("View eligibility ›") }
                if (offer.requiresActivation) OutlinedButton(onClick = { vm.activate(offer.id) }) { Text(if (offer.id in s.activated) "Activated" else "Activate offer") }
            }
        }
    }
}

@Composable private fun NearbyMap(stores: List<StoreLocation>) {
    val markers = remember(stores) {
        stores.joinToString("\n") { store ->
            val title = store.name.replace("'", "\\'")
            "L.marker([${store.latitude},${store.longitude}]).addTo(map).bindPopup('$title');"
        }
    }
    val html = remember(markers) {
        """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
        <style>html,body,#map{height:100%;margin:0} .leaflet-control-attribution{font-size:9px}</style></head>
        <body><div id="map"></div><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <script>const map=L.map('map').setView([37.20,-80.43],11);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);
        $markers
        const group=L.featureGroup(Object.values(map._layers).filter(x=>x instanceof L.Marker)); if(group.getLayers().length) map.fitBounds(group.getBounds().pad(0.25));</script></body></html>
        """.trimIndent()
    }
    AndroidView(
        factory = { context ->
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.WHITE)
            }
        },
        update = { view -> if (view.tag != html) { view.tag = html; view.loadDataWithBaseURL("https://www.openstreetmap.org/", html, "text/html", "UTF-8", null) } },
        modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp)),
    )
}

@Composable private fun Insights(s: UiState, vm: SwypViewModel, back: () -> Unit) {
    TextButton(onClick = back) { Text("← Home") }
    Text("Spending insights", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Navy)
    Text("Utilization target: ${(s.ceiling * 100).toInt()}%", fontWeight = FontWeight.Bold)
    Slider(s.ceiling.toFloat(), { vm.ceiling(it.toDouble()) }, valueRange = .05f..0.80f)
    val forecast = Recommender.forecast(s.history)
    Text("Expected before month-end", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
    if (forecast.isEmpty()) Text("We need at least three regular charges to learn a pattern.", color = Color.Gray)
    forecast.forEach { f -> InfoCard("${f.merchant} · ${f.due} · ${money(f.cents)} · ${(f.confidence * 100).toInt()}% regularity") }
    Text("Recent activity", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
    ActivityList(s.history.take(30))
}

@Composable private fun ActivityList(rows: List<Purchase>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            rows.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(row.merchant, fontWeight = FontWeight.Bold, color = Navy); Text("${row.category} · ${row.date}", fontSize = 12.sp, color = Color.Gray) }
                    Text(money(row.amountCents), fontWeight = FontWeight.Bold)
                }
                if (index < rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable private fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Mist), modifier = Modifier.fillMaxWidth()) { Text(text, Modifier.padding(15.dp), color = Navy) }
}

private fun rewardLabel(product: String) = when (product) { "savor" -> "3% on select categories"; "venture" -> "2 miles per dollar"; else -> "1.5% cash back" }
private fun cardGradient(index: Int) = Brush.linearGradient(if (index % 3 == 0) listOf(Color(0xFF007C75), Color(0xFF04554F)) else listOf(Color(0xFF245781), Color(0xFF11375E)))
private fun Double.toCleanQuantity(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
