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
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlin.math.roundToLong


class MainActivity : ComponentActivity() {
    private val vm: SwypViewModel by viewModels()
    private var pendingImage by mutableStateOf<Bitmap?>(null)
    private var currentTab by mutableStateOf("Home")
    private var selectedCardDetail by mutableStateOf<String?>(null)
    private var pendingArm by mutableStateOf<Pair<SwypCard, Checkout>?>(null)
    private var fineLocationGranted by mutableStateOf(false)
    private var userLocation by mutableStateOf<Pair<Double, Double>?>(null)
    private val fusedLocation by lazy { LocationServices.getFusedLocationProviderClient(this) }
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
        refreshDeviceLocation()
        enableLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fineLocationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        refreshDeviceLocation()
        receive(intent)
        if (android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            SwypTheme {
                val s by vm.state.collectAsState()
                // The Snap & Pay widget arms a card headlessly, then opens the app; once the
                // session is signed in, mirror that armed card into the UI so the tap-to-pay
                // screen appears with no further taps.
                LaunchedEffect(s.signedIn, pendingArm) {
                    val arm = pendingArm
                    if (arm != null && s.signedIn) {
                        vm.applyExternalArm(arm.first, arm.second)
                        pendingArm = null
                    }
                }
                Surface(Modifier.fillMaxSize().background(Palette.CanvasBrush), color = Color.Transparent) {
                    when {
                        !s.configured -> SetupScreen()
                        !s.signedIn -> LoginScreen(s, vm)
                        !s.profileLoaded -> LoadingAccountScreen()
                        s.fullName.isBlank() || s.age == 0 || s.phone.isBlank() || s.homeZip.isBlank() ->
                            ProfileSetupScreen(s, vm)
                        s.armed -> TapToPayScreen(s, vm)
                        else -> Scaffold(
                            containerColor = Color.Transparent,
                            bottomBar = {
                                Column {
                                    HorizontalDivider(color = Palette.Border)
                                    NavigationBar(containerColor = Palette.Surface, tonalElevation = 0.dp) {
                                        listOf("Home" to Icons.Home, "Cards" to Icons.Cards, "Pay" to Icons.Pay, "Scan" to Icons.Scan, "Nearby" to Icons.Nearby)
                                            .forEach { (name, icon) ->
                                                NavigationBarItem(
                                                    selected = currentTab == name,
                                                    onClick = { currentTab = name; selectedCardDetail = null },
                                                    icon = { Icon(icon, name, Modifier.size(24.dp)) },
                                                    label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                                                    colors = NavigationBarItemDefaults.colors(
                                                        selectedIconColor = Palette.Primary, selectedTextColor = Palette.Primary,
                                                        unselectedIconColor = Palette.TextMuted, unselectedTextColor = Palette.TextMuted,
                                                        indicatorColor = Palette.Tint,
                                                    ),
                                                )
                                            }
                                    }
                                }
                            },
                        ) { insets ->
                            Column(
                                Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Spacer(Modifier.height(4.dp))
                                AppHeader { vm.signOut(); pendingImage = null }
                                if (s.busy) UtilBar(0.0, color = Palette.Primary)
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
                                        s, vm, fineLocationGranted, userLocation,
                                        {
                                            location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                            if (android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        },
                                        { StoreAlerts.disable(this@MainActivity); vm.message("Nearby alerts turned off.") },
                                        ::refreshDeviceLocation,
                                    )
                                    currentTab == "Insights" -> Insights(s, vm) { currentTab = "Home" }
                                }
                                Spacer(Modifier.height(24.dp))
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
        intent.getStringExtra("armCardId")?.let { id ->
            pendingArm =
                SwypCard(
                    id = id,
                    product = intent.getStringExtra("armProduct").orEmpty(),
                    name = intent.getStringExtra("armName").orEmpty(),
                    last4 = intent.getStringExtra("armLast4").orEmpty(),
                    limitCents = intent.getLongExtra("armLimit", 0),
                    balanceCents = intent.getLongExtra("armBalance", 0),
                ) to
                    Checkout(
                        merchant = intent.getStringExtra("armMerchant").orEmpty(),
                        amountCents = intent.getLongExtra("armAmount", 0),
                        category = intent.getStringExtra("armCategory") ?: "other",
                    )
        }
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

    /** Reads the device's current position so the Nearby map can center on and pin "you are here". */
    private fun refreshDeviceLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return
        lifecycleScope.launch {
            runCatching {
                val fresh = fusedLocation
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                    .await()
                fresh ?: fusedLocation.lastLocation.await()
            }.onSuccess { location ->
                if (location != null) userLocation = location.latitude to location.longitude
            }
        }
    }
}

@Composable private fun LoadingAccountScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LogoMark(56.dp)
            CircularProgressIndicator(color = Palette.Primary, strokeWidth = 3.dp)
            Muted("Loading your Swyp account…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun ProfileSetupScreen(s: UiState, vm: SwypViewModel) {
    var name by remember(s.fullName) { mutableStateOf(s.fullName) }
    var age by remember(s.age) { mutableStateOf(if (s.age > 0) s.age.toString() else "") }
    var phone by remember(s.phone) { mutableStateOf(s.phone) }
    var zip by remember(s.homeZip) { mutableStateOf(s.homeZip) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        LogoMark(52.dp)
        ScreenTitle("Finish your profile", "A few details to set up your wallet. You only need to do this once.")
        Spacer(Modifier.height(4.dp))
        SwypField(name, { name = it }, "Full name")
        SwypField(age, { age = it.filter(Char::isDigit).take(3) }, "Age", keyboard = KeyboardType.Number)
        SwypField(phone, { phone = it.take(20) }, "Phone number", keyboard = KeyboardType.Phone)
        SwypField(zip, { zip = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, "Home ZIP code", keyboard = KeyboardType.Number)
        if (s.busy) UtilBar(0.0, color = Palette.Primary)
        if (s.message.isNotBlank()) Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        PrimaryButton("Save and continue", { vm.saveProfile(name, age, phone, zip) }, enabled = !s.busy)
        TextButton(onClick = vm::signOut, Modifier.align(Alignment.CenterHorizontally)) { Text("Sign out", color = Palette.TextMuted) }
    }
}

@Composable private fun AppHeader(signOut: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LogoMark(40.dp)
            Text("Swyp", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
        }
        TextButton(onClick = signOut) { Text("Sign out", style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp), color = Palette.TextMuted) }
    }
}

@Composable private fun SetupScreen() {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        LogoMark(56.dp)
        Spacer(Modifier.height(24.dp))
        Text("Swyp", style = MaterialTheme.typography.displaySmall)
        Muted("One purchase. A smarter choice.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        InfoCard("Connect Firebase in android app/local.properties, then rebuild. See SETUP.md.")
    }
}

@Composable private fun LoginScreen(s: UiState, vm: SwypViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signup by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)).background(Palette.HeroBrush)) {
            FlowLines(Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LogoMark(52.dp, onDark = true)
                Spacer(Modifier.height(12.dp))
                Text("Make every purchase count.", style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text("Your cards. Their rewards. One thoughtful choice.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .85f))
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (signup) "Create your account" else "Welcome back", style = MaterialTheme.typography.headlineSmall)
            SwypField(email, { email = it }, "Email address", keyboard = KeyboardType.Email)
            SwypField(password, { password = it }, "Password", password = true)
            if (s.busy) UtilBar(0.0, color = Palette.Primary)
            if (s.message.isNotBlank()) Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            PrimaryButton(if (signup) "Create account" else "Sign in", { if (signup) vm.signUp(email, password) else vm.signIn(email, password) }, enabled = !s.busy)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { signup = !signup }) { Text(if (signup) "I have an account" else "Create an account", color = Palette.Primary) }
                TextButton(onClick = { vm.reset(email) }) { Text("Forgot password?", color = Palette.TextMuted) }
            }
        }
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
    HeroPanel {
        Text("Spent this month", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
        Text(money(spent), color = Color.White, style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 44.sp))
        Spacer(Modifier.height(6.dp))
        Text("Across ${s.cards.size} ${if (s.cards.size == 1) "card" else "cards"}", color = Color.White, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.background(Color.White.copy(alpha = .18f), CircleShape).padding(horizontal = 12.dp, vertical = 5.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Rewards earned", money(rewards), Palette.Success, Modifier.weight(1f))
        MetricCard("Credit used", "${(util * 100).toInt()}%", utilColor(util), Modifier.weight(1f))
    }
    SectionHeader("Best card for your next shop")
    val best = s.ranks.firstOrNull { it.eligible }?.card ?: s.cards.minByOrNull { if (it.limitCents > 0) it.balanceCents.toDouble() / it.limitCents else 1.0 }
    if (best != null) {
        SurfaceCard(padding = 18.dp, gap = 16.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniCard(s.cards.indexOfFirst { it.id == best.id }.coerceAtLeast(0))
                Column(Modifier.weight(1f)) {
                    Text(best.name, style = MaterialTheme.typography.titleMedium)
                    Muted("•••• ${best.last4} · ${rewardLabel(best.product)}")
                }
            }
            PrimaryButton("Choose a card", onPay)
        }
    } else SurfaceCard { Muted("Add a card to see your best pick.", style = MaterialTheme.typography.bodyMedium) }
    SectionHeader("Recent activity", "View insights", onInsights)
    ActivityList(s.history.take(5))
}

@Composable private fun MetricCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    SurfaceCard(modifier, padding = 16.dp, gap = 6.dp) {
        Muted(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
    }
}

@Composable private fun Cards(s: UiState, vm: SwypViewModel, open: (String) -> Unit) {
    ScreenTitle("Your cards", "A clear view of your credit.")
    val limit = s.cards.sumOf { it.limitCents }
    val balance = s.cards.sumOf { it.balanceCents }
    val util = if (limit > 0) balance.toDouble() / limit else 0.0
    SurfaceCard(padding = 20.dp, gap = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Muted("Total credit used", style = MaterialTheme.typography.bodyMedium)
                Text("${(util * 100).toInt()}%", style = MaterialTheme.typography.displaySmall, color = utilColor(util))
            }
            Pill(if (util <= .3) "Healthy" else if (util <= .6) "Watch it" else "High", utilTint(util), utilColor(util))
        }
        UtilBar(util)
        Muted("${money(balance)} of ${money(limit)}")
    }
    if (s.cards.isEmpty()) {
        SurfaceCard {
            Text(if (s.status == "generating") "Creating your Nessie wallet and transaction history…" else "Your wallet is empty.", style = MaterialTheme.typography.bodyMedium)
            if (s.status == "ready") PrimaryButton("Add cards", vm::createCards, enabled = !s.busy)
        }
    }
    s.cards.forEachIndexed { index, card -> CardRow(card, index) { open(card.id) } }
}

@Composable private fun CardRow(card: SwypCard, index: Int, open: () -> Unit) {
    val util = if (card.limitCents > 0) card.balanceCents.toDouble() / card.limitCents else 0.0
    SurfaceCard(onClick = open, padding = 16.dp, gap = 14.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniCard(index)
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium)
                Muted("•••• ${card.last4} · ${rewardLabel(card.product)}")
            }
            Pill("${(util * 100).toInt()}%", utilTint(util), utilColor(util))
        }
        UtilBar(util)
        Muted("${money(card.balanceCents)} of ${money(card.limitCents)} used")
    }
}

@Composable private fun CardDetail(card: SwypCard, rows: List<Purchase>, back: () -> Unit) {
    val util = if (card.limitCents > 0) card.balanceCents.toDouble() / card.limitCents else 0.0
    TextButton(onClick = back, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("‹  All cards", color = Palette.Primary, style = MaterialTheme.typography.labelLarge) }
    Box(Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(24.dp)).background(Palette.HeroBrush)) {
        FlowLines(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(card.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("SWYP", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelMedium)
            }
            Text("••••  ••••  ••••  ${card.last4}", color = Color.White, style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 1.sp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(rewardLabel(card.product), color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodyMedium)
                UtilBar(util, color = Color.White, track = Color.White.copy(alpha = .28f))
                Text("${money(card.balanceCents)} of ${money(card.limitCents)} used", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    SectionHeader("Transactions")
    if (rows.isEmpty()) SurfaceCard { Muted("No transactions on this card yet.", style = MaterialTheme.typography.bodyMedium) } else ActivityList(rows)
}

@Composable private fun Pay(s: UiState, vm: SwypViewModel) {
    ScreenTitle("Choose & pay", "Select the card you want to present to the Swyp reader.")
    if (s.checkout.amountCents > 0) {
        InfoCard("${s.checkout.merchant} · ${money(s.checkout.amountCents)}${if (s.checkout.items.isNotEmpty()) " · ${s.checkout.items.size} items" else ""}")
    }
    val ordered = if (s.ranks.isEmpty()) s.cards else s.ranks.map { it.card }
    ordered.forEachIndexed { index, card ->
        val rank = s.ranks.find { it.card.id == card.id }
        val currentUtil = if (card.limitCents > 0) card.balanceCents.toDouble() / card.limitCents else 1.0
        val eligible = rank?.eligible ?: (currentUtil <= s.ceiling)
        val chosen = s.selected == card.id
        SurfaceCard(
            onClick = { vm.select(card.id) }, enabled = eligible,
            container = if (chosen) Palette.Tint else Palette.Surface,
            border = if (chosen) Palette.Primary else Palette.Border, borderWidth = if (chosen) 2.dp else 1.dp,
            padding = 16.dp,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniCard(index)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium, color = if (eligible) Palette.Ink else Palette.TextMuted)
                    Muted("•••• ${card.last4} · ${rewardLabel(card.product)}")
                    if (rank != null) Text("${money(rank.rewardCents)} est. rewards", color = Palette.Success, style = MaterialTheme.typography.labelMedium)
                }
                if (eligible) RadioDot(chosen) else Pill("Over target", Palette.WarningTint, Palette.Warning)
            }
        }
    }
    val selected = s.cards.find { it.id == s.selected }
    val selectedRank = s.ranks.find { it.card.id == s.selected }
    val eligible = selected != null && (selectedRank?.eligible ?: (selected.limitCents > 0 && selected.balanceCents.toDouble() / selected.limitCents <= s.ceiling))
    PrimaryButton("Pay with ${selected?.name ?: "selected card"}", vm::arm, enabled = !s.busy && !s.armed && s.status == "ready" && eligible)
}

/** Full-screen contactless moment shown while a card is armed for tap-to-pay. */
@Composable private fun TapToPayScreen(s: UiState, vm: SwypViewModel) {
    val selected = s.cards.find { it.id == s.selected }
    val transition = rememberInfiniteTransition(label = "tap")
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "pulse",
    )
    Box(Modifier.fillMaxSize().background(Palette.HeroBrush)) {
        FlowLines(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            if (s.checkout.amountCents > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(money(s.checkout.amountCents), color = Color.White, style = MaterialTheme.typography.displaySmall)
                    if (s.checkout.merchant.isNotBlank())
                        Text(s.checkout.merchant, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.titleMedium)
                }
            }
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val maxR = size.minDimension / 2f
                    repeat(3) { i ->
                        val t = (pulse + i / 3f) % 1f
                        drawCircle(
                            Color.White.copy(alpha = (1f - t) * 0.45f),
                            radius = maxR * (0.42f + 0.58f * t),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
                        )
                    }
                }
                Box(Modifier.size(104.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Pay, null, tint = Palette.Primary, modifier = Modifier.size(52.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ready to tap", color = Color.White, style = MaterialTheme.typography.displaySmall)
                Text(
                    "${selected?.name ?: "Selected card"} •••• ${selected?.last4 ?: "••••"}",
                    color = Color.White.copy(alpha = .92f), style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Hold your phone near the iPhone reader within 2 minutes",
                    color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        TextButton(
            onClick = vm::cancelTap,
            modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 16.dp),
        ) { Text("Cancel", color = Color.White, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable private fun Scan(s: UiState, vm: SwypViewModel, image: Bitmap?, clear: () -> Unit, camera: () -> Unit, pick: () -> Unit, capture: () -> Unit, pay: () -> Unit) {
    ScreenTitle("Scan & save", "We read every visible bill item and check for matching offers.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton("Camera", camera, Modifier.weight(1f)); SecondaryButton("Photo", pick, Modifier.weight(1f)); SecondaryButton("Screenshot", capture, Modifier.weight(1.3f))
    }
    Muted("Tip: add “Scan with Swyp” to Quick Settings, or share a screenshot to Swyp.")
    if (image != null) {
        SurfaceCard(padding = 12.dp, gap = 12.dp) {
            Image(image.asImageBitmap(), "Bill preview", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Analyze all items", { vm.scan(image); clear() }, Modifier.weight(1f), enabled = !s.busy)
                TextButton(onClick = clear) { Text("Discard", color = Palette.TextMuted) }
            }
        }
    }
    var merchant by remember(s.checkout.merchant) { mutableStateOf(s.checkout.merchant) }
    var amount by remember(s.checkout.amountCents) { mutableStateOf(if (s.checkout.amountCents > 0) BigDecimal(s.checkout.amountCents).movePointLeft(2).toPlainString() else "") }
    var category by remember(s.checkout.category) { mutableStateOf(s.checkout.category) }
    SectionHeader("Purchase details")
    SwypField(merchant, { merchant = it; vm.cancelTap() }, "Merchant")
    SwypField(amount, { amount = it; vm.cancelTap() }, "Total in USD", keyboard = KeyboardType.Decimal)
    Muted("Category")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("grocery", "dining", "entertainment", "streaming", "travel", "gas", "other").forEach {
            CategoryChip(it.replaceFirstChar(Char::uppercase), category == it) { category = it; vm.cancelTap() }
        }
    }
    PrimaryButton("Compare my cards", {
        val cents = runCatching { BigDecimal(amount).movePointRight(2).longValueExact() }.getOrNull()
        if (cents == null || cents !in 1..100000000 || merchant.isBlank()) vm.message("Enter a merchant and valid USD amount")
        else vm.checkout(Checkout(merchant.trim(), cents, category, "USD", s.checkout.items))
    })
    if (s.checkout.items.isNotEmpty()) {
        SectionHeader("Items found (${s.checkout.items.size})")
        SurfaceCard(padding = 16.dp, gap = 0.dp) {
            s.checkout.items.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${item.quantity.toCleanQuantity()} × ${item.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(money(item.totalPriceCents), style = MaterialTheme.typography.titleMedium)
                }
                if (index < s.checkout.items.lastIndex) HorizontalDivider(color = Palette.Border)
            }
        }
        val matches = s.checkout.matches(s.offers)
        SectionHeader("Deals for this bill")
        if (matches.isEmpty()) SurfaceCard { Muted("No verified item offers match this bill right now.", style = MaterialTheme.typography.bodyMedium) }
        matches.forEach { match -> DealCard(match.offer, match.itemName, s, vm) }
    }
    if (s.ranks.isNotEmpty()) {
        SectionHeader("Recommended card")
        s.ranks.take(3).forEachIndexed { i, rank ->
            val chosen = s.selected == rank.card.id
            SurfaceCard(
                onClick = { vm.select(rank.card.id) }, enabled = rank.eligible,
                container = if (chosen) Palette.Tint else Palette.Surface,
                border = if (chosen) Palette.Primary else Palette.Border, borderWidth = if (chosen) 2.dp else 1.dp, gap = 4.dp,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(rank.card.name, style = MaterialTheme.typography.titleMedium)
                    if (i == 0) Pill("Best match", Palette.SuccessTint, Palette.Success)
                }
                Text("${money(rank.rewardCents)} reward value · ${(rank.projectedUtilization * 100).toInt()}% projected use", style = MaterialTheme.typography.bodyMedium)
                Muted(rank.reason)
            }
        }
        PrimaryButton("Continue to Tap to Pay", pay)
    }
}

@Composable private fun DealCard(offer: Offer, itemName: String?, s: UiState, vm: SwypViewModel) {
    SurfaceCard(container = Palette.Tint, border = Palette.Tint, gap = 6.dp) {
        Text(offer.title, style = MaterialTheme.typography.titleMedium)
        if (itemName != null) Text("Matches $itemName", color = Palette.Primary, style = MaterialTheme.typography.labelMedium)
        Muted("${offer.merchant} · through ${offer.expiresOn}")
        if (offer.requiresActivation) {
            val on = offer.id in s.activated
            if (on) Pill("Activated ✓", Palette.SuccessTint, Palette.Success)
            else SecondaryButton("Activate offer", { vm.activate(offer.id) })
        }
    }
}

@Composable private fun Nearby(
    s: UiState,
    vm: SwypViewModel,
    locationGranted: Boolean,
    userLocation: Pair<Double, Double>?,
    enable: () -> Unit,
    disable: () -> Unit,
    refreshLocation: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // On first open (per session) pull a fresh fix and kick off the daily deal search.
    LaunchedEffect(Unit) {
        refreshLocation()
        vm.refreshDeals()
    }
    ScreenTitle("Nearby deals", "Stores around you and today's deals, closest first.")
    if (!locationGranted) {
        SurfaceCard(container = Palette.Tint, border = Palette.Tint, padding = 18.dp, gap = 12.dp) {
            Text("Turn on location to sort stores by distance and get alerts when you're near a deal.", style = MaterialTheme.typography.bodyMedium)
            SecondaryButton("Allow location", enable)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = refreshLocation, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Nearby, null, tint = Palette.Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Update my location", color = Palette.Primary, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = disable, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Turn off alerts", color = Palette.TextMuted) }
        }
    }

    // Order stores by distance from the user (nearest first) when we have a fix.
    val ordered = remember(s.stores, userLocation) {
        userLocation?.let { (lat, lng) ->
            s.stores.sortedBy { distanceMeters(lat, lng, it.latitude, it.longitude) }
        } ?: s.stores
    }

    if (s.busy && s.offers.isEmpty()) InfoCard("Finding today's deals near you…")

    if (ordered.isNotEmpty()) {
        SectionHeader("Stores near you")
        ordered.forEach { store ->
            val meters = userLocation?.let { distanceMeters(it.first, it.second, store.latitude, store.longitude) }
            val storeOffers = s.offers.filter { merchantMatches(it.merchant, store.merchant.ifBlank { store.name }) }
            SurfaceCard(gap = 10.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(store.name)
                    Column(Modifier.weight(1f)) {
                        Text(store.name, style = MaterialTheme.typography.titleMedium)
                        Muted(store.address)
                    }
                    if (meters != null) Pill(prettyDistance(meters), Palette.Tint, Palette.Primary)
                }
                if (storeOffers.isEmpty()) Muted("No deals here right now.", style = MaterialTheme.typography.bodyMedium)
                else storeOffers.forEach { offer -> OfferRow(offer, context) }
            }
        }
    }

    // Deals whose merchant isn't one of the nearby stores.
    val extra = s.offers.filter { offer -> ordered.none { merchantMatches(offer.merchant, it.merchant.ifBlank { it.name }) } }
    if (extra.isNotEmpty()) {
        SectionHeader("More deals near you")
        extra.forEach { offer ->
            SurfaceCard(padding = 16.dp, gap = 6.dp) {
                Text(offer.merchant, style = MaterialTheme.typography.titleMedium)
                OfferRow(offer, context)
            }
        }
    }

    if (ordered.isEmpty() && s.offers.isEmpty() && !s.busy)
        InfoCard("No stores or deals yet. Pull up this tab again to run today's search.")
}

/** True when a deal's merchant and a store's merchant refer to the same brand. */
private fun merchantMatches(offerMerchant: String, storeMerchant: String): Boolean {
    val a = offerMerchant.trim()
    val b = storeMerchant.trim()
    if (a.isBlank() || b.isBlank()) return false
    return a.equals(b, true) || a.contains(b, true) || b.contains(a, true)
}

/** One deal's title, terms and eligibility link, used inside store and more-deals cards. */
@Composable private fun OfferRow(offer: Offer, context: android.content.Context) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(offer.title, style = MaterialTheme.typography.bodyLarge, color = Palette.Primary, fontWeight = FontWeight.Medium)
        Muted("Use ${offer.product} · through ${offer.expiresOn}")
        if (offer.sourceUrl.startsWith("https://"))
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(offer.sourceUrl))) }) {
                Text("View eligibility ›", color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp))
            }
    }
}

/** Great-circle distance in meters between two lat/lng points. */
private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

private fun prettyDistance(meters: Double): String {
    val miles = meters / 1609.34
    return if (miles < 0.2) "${meters.toInt()} m" else String.format("%.1f mi", miles)
}

@Composable private fun Insights(s: UiState, vm: SwypViewModel, back: () -> Unit) {
    TextButton(onClick = back, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("‹  Home", color = Palette.Primary, style = MaterialTheme.typography.labelLarge) }
    ScreenTitle("Spending insights", "See what's coming and tune your comfort zone.")
    SurfaceCard(padding = 20.dp, gap = 4.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Utilization target", style = MaterialTheme.typography.titleMedium)
            Pill("${(s.ceiling * 100).toInt()}%", Palette.Tint, Palette.Primary)
        }
        Muted("Swyp avoids cards that would push you past this.")
        Slider(
            s.ceiling.toFloat(), { vm.ceiling(it.toDouble()) }, valueRange = .05f..0.80f,
            colors = SliderDefaults.colors(thumbColor = Palette.Primary, activeTrackColor = Palette.Primary, inactiveTrackColor = Palette.Tint),
        )
    }
    val forecast = Recommender.forecast(s.history)
    SectionHeader("Expected before month-end")
    if (forecast.isEmpty()) SurfaceCard { Muted("We need at least three regular charges to learn a pattern.", style = MaterialTheme.typography.bodyMedium) }
    forecast.forEach { f -> InfoCard("${f.merchant} · ${f.due} · ${money(f.cents)} · ${(f.confidence * 100).toInt()}% regularity") }
    SectionHeader("Recent activity")
    ActivityList(s.history.take(30))
}

@Composable private fun ActivityList(rows: List<Purchase>) {
    if (rows.isEmpty()) { SurfaceCard { Muted("No activity yet.", style = MaterialTheme.typography.bodyMedium) }; return }
    SurfaceCard(padding = 4.dp, gap = 0.dp) {
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(row.merchant)
                Column(Modifier.weight(1f)) {
                    Text(row.merchant, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Muted("${row.category.replaceFirstChar(Char::uppercase)} · ${row.date}")
                }
                Text(money(row.amountCents), style = MaterialTheme.typography.titleMedium)
            }
            if (index < rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = Palette.Border)
        }
    }
}

private fun rewardLabel(product: String) = when (product) { "savor" -> "3% on select categories"; "venture" -> "2 miles per dollar"; else -> "1.5% cash back" }
private fun Double.toCleanQuantity(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
