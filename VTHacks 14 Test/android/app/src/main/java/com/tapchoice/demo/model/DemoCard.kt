package com.tapchoice.demo.model

data class DemoCard(
    val id: String,
    val displayName: String,
    val network: String,
    val last4: String,
    val groceryRewardPercent: Double,
    val currentBalance: Double,
    val creditLimit: Double,
)

data class CardRecommendation(
    val card: DemoCard,
    val rewardValue: Double,
    val projectedUtilization: Double,
    val utilizationPenalty: Double,
    val score: Double,
)

