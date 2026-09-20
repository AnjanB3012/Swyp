package com.swyp.app

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.*

data class SwypCard(
    val id: String = "",
    val product: String = "",
    val name: String = "",
    val last4: String = "",
    val limitCents: Long = 0,
    val balanceCents: Long = 0,
)

data class Purchase(
    val id: String = "",
    val cardId: String = "",
    val merchant: String = "",
    val category: String = "other",
    val amountCents: Long = 0,
    val date: String = "",
)

data class Offer(
    val id: String = "",
    val title: String = "",
    val merchant: String = "",
    val product: String = "any",
    val bonusPercent: Double = 0.0,
    val minimumCents: Long = 0,
    val capCents: Long = 0,
    val expiresOn: String = "",
    val sourceUrl: String = "",
    val verified: Boolean = false,
    val requiresActivation: Boolean = true,
    val itemKeywords: List<String> = emptyList(),
)

data class CheckoutItem(
    val name: String = "",
    val quantity: Double = 1.0,
    val unitPriceCents: Long = 0,
    val totalPriceCents: Long = 0,
    val category: String = "other",
)

data class Checkout(
    val merchant: String = "",
    val amountCents: Long = 0,
    val category: String = "other",
    val currency: String = "USD",
    val items: List<CheckoutItem> = emptyList(),
)

data class DealMatch(val offer: Offer, val itemName: String?)

data class StoreLocation(
    val id: String = "",
    val merchant: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 150.0,
)

fun Checkout.matches(offers: List<Offer>): List<DealMatch> {
    val merchantName = merchant.trim()
    return offers.mapNotNull { offer ->
        val merchantMatches =
            offer.merchant.isBlank() ||
                offer.merchant.equals("any", true) ||
                merchantName.contains(offer.merchant, true) ||
                offer.merchant.contains(merchantName, true)
        if (!merchantMatches) return@mapNotNull null
        val keywords =
            (offer.itemKeywords + offer.title.split(Regex("[^A-Za-z0-9]+")))
                .map { it.lowercase().trim() }
                .filter { it.length >= 3 && it !in setOf("with", "save", "offer", "card") }
        val item =
            items.firstOrNull { row ->
                val name = row.name.lowercase()
                keywords.any(name::contains)
            }
        if (offer.itemKeywords.isNotEmpty() && item == null) null
        else DealMatch(offer, item?.name)
    }
}

data class Forecast(
    val cardId: String,
    val merchant: String,
    val cents: Long,
    val due: LocalDate,
    val confidence: Double,
)

data class RankedCard(
    val card: SwypCard,
    val rewardCents: Long,
    val forecastCents: Long,
    val projectedUtilization: Double,
    val overallUtilization: Double,
    val opportunityCostCents: Long,
    val eligible: Boolean,
    val reason: String,
)

object Recommender {
    // Robust interval learning: median inter-arrival time + median absolute deviation.
    fun forecast(history: List<Purchase>, today: LocalDate = LocalDate.now()): List<Forecast> =
        history
            .filter {
                it.amountCents > 0 &&
                    runCatching { !LocalDate.parse(it.date).isAfter(today) }.getOrDefault(false)
            }
            .groupBy { it.cardId to it.merchant.lowercase().trim() }
            .flatMap { (_, rows) ->
                val sorted = rows.distinctBy { it.date }.sortedBy { it.date }.takeLast(12)
                if (sorted.size < 3) return@flatMap emptyList()
                val intervals =
                    sorted.zipWithNext { a, b ->
                        ChronoUnit.DAYS.between(LocalDate.parse(a.date), LocalDate.parse(b.date))
                            .toDouble()
                    }
                val interval = median(intervals)
                val deviation = median(intervals.map { abs(it - interval) })
                if (interval < 5 || interval > 95 || deviation > interval * 0.25)
                    return@flatMap emptyList()
                val last = LocalDate.parse(sorted.last().date)
                if (ChronoUnit.DAYS.between(last, today) > interval * 2) return@flatMap emptyList()
                val confidence = (1 - deviation / interval).coerceIn(0.0, 1.0)
                val cents = median(sorted.map { it.amountCents.toDouble() }).roundToLong()
                var next =
                    if (interval in 27.0..32.0) last.plusMonths(1)
                    else last.plusDays(interval.roundToLong())
                val results = mutableListOf<Forecast>()
                while (!next.isAfter(today.withDayOfMonth(today.lengthOfMonth()))) {
                    if (next.isAfter(today))
                        results +=
                            Forecast(
                                sorted.last().cardId,
                                sorted.last().merchant,
                                cents,
                                next,
                                confidence,
                            )
                    next =
                        if (interval in 27.0..32.0) next.plusMonths(1)
                        else next.plusDays(interval.roundToLong())
                }
                results
            }

    private fun median(values: List<Double>): Double {
        val a = values.sorted()
        return if (a.size % 2 == 1) a[a.size / 2] else (a[a.size / 2 - 1] + a[a.size / 2]) / 2
    }

    fun rate(card: SwypCard, checkout: Checkout): Double =
        when (card.product) {
            "savor" ->
                if (
                    checkout.category in setOf("grocery", "dining", "entertainment", "streaming") &&
                        !Regex("walmart|target", RegexOption.IGNORE_CASE)
                            .containsMatchIn(checkout.merchant)
                )
                    0.03
                else 0.01
            "quicksilver" -> 0.015
            "venture" -> 0.02 // 2 miles/$ at an explicit assumed travel value of 1 cent/mile.
            else -> 0.0
        }

    fun rank(
        cards: List<SwypCard>,
        history: List<Purchase>,
        checkout: Checkout,
        offers: List<Offer> = emptyList(),
        activated: Set<String> = emptySet(),
        ceiling: Double = 0.30,
        today: LocalDate = LocalDate.now(),
    ): List<RankedCard> {
        require(checkout.amountCents in 1..100_000_000 && checkout.currency == "USD")
        require(ceiling in 0.01..1.0)
        val forecast = forecast(history, today)
        val totalLimit = cards.sumOf { it.limitCents }.toDouble()
        val totalProjected =
            cards.sumOf { it.balanceCents } + forecast.sumOf { it.cents } + checkout.amountCents
        val overall = if (totalLimit > 0) totalProjected / totalLimit else Double.POSITIVE_INFINITY
        return cards
            .map { card ->
                val upcoming = forecast.filter { it.cardId == card.id }.sumOf { it.cents }
                val projected =
                    if (card.limitCents > 0)
                        (card.balanceCents + upcoming + checkout.amountCents).toDouble() /
                            card.limitCents
                    else Double.POSITIVE_INFINITY
                val base = (checkout.amountCents * rate(card, checkout)).roundToLong()
                val bonus =
                    offers
                        .filter { offer ->
                            offer.verified &&
                                (!offer.requiresActivation || offer.id in activated) &&
                                offer.product in setOf("any", card.product) &&
                                offer.merchant.equals(checkout.merchant, true) &&
                                checkout.amountCents >= offer.minimumCents &&
                                offer.bonusPercent in 0.0..100.0 &&
                                offer.capCents > 0 &&
                                runCatching { !LocalDate.parse(offer.expiresOn).isBefore(today) }
                                    .getOrDefault(false)
                        }
                        .maxOfOrNull {
                            min(
                                it.capCents,
                                (checkout.amountCents * it.bonusPercent / 100).roundToLong(),
                            )
                        } ?: 0
                val headroom =
                    (card.limitCents * ceiling - card.balanceCents - upcoming).coerceAtLeast(0.0)
                val displaced = (checkout.amountCents - headroom).coerceAtLeast(0.0)
                val alternative =
                    cards.filter { it.id != card.id }.maxOfOrNull { rate(it, checkout) } ?: 0.0
                val opportunity =
                    (displaced * (rate(card, checkout) - alternative).coerceAtLeast(0.0))
                        .roundToLong()
                val eligible = projected <= ceiling && overall <= ceiling && card.limitCents > 0
                RankedCard(
                    card,
                    base + bonus,
                    upcoming,
                    projected,
                    overall,
                    opportunity,
                    eligible,
                    when {
                        overall > ceiling ->
                            "Wallet forecast exceeds your ${(ceiling*100).toInt()}% target. Consider paying down a balance or another payment method."
                        projected > ceiling ->
                            "Upcoming charges leave too little room on this card."
                        upcoming > 0 ->
                            "Reserves ${money(upcoming)} for expected charges this month."
                        else -> "Best available rewards within your utilization target."
                    },
                )
            }
            .sortedWith(
                compareByDescending<RankedCard> { it.eligible }
                    .thenByDescending { it.rewardCents - it.opportunityCostCents }
                    .thenBy { it.projectedUtilization }
            )
    }
}

fun money(cents: Long): String =
    java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(cents / 100.0)
