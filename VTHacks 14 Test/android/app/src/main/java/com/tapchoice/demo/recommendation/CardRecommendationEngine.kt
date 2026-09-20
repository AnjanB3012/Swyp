package com.tapchoice.demo.recommendation

import com.tapchoice.demo.model.CardRecommendation
import com.tapchoice.demo.model.DemoCard

/** Deterministic and intentionally explainable; this is not underwriting logic. */
class CardRecommendationEngine(
    private val softUtilizationThreshold: Double = 0.30,
    private val penaltyDollarsPerPoint: Double = 200.0,
) {
    fun rank(cards: List<DemoCard>, transactionAmount: Double): List<CardRecommendation> =
        cards.map { card ->
            val utilization = (card.currentBalance + transactionAmount) / card.creditLimit
            val reward = transactionAmount * card.groceryRewardPercent / 100.0
            val penalty = ((utilization - softUtilizationThreshold).coerceAtLeast(0.0)) *
                penaltyDollarsPerPoint
            CardRecommendation(card, reward, utilization, penalty, reward - penalty)
        }.sortedWith(compareByDescending<CardRecommendation> { it.score }.thenBy { it.card.id })
}

