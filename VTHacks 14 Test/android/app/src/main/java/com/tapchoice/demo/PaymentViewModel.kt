package com.tapchoice.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.tapchoice.demo.data.DemoCardRepository
import com.tapchoice.demo.model.CardRecommendation
import com.tapchoice.demo.recommendation.CardRecommendationEngine
import com.tapchoice.demo.state.PaymentSessionStore
import com.tapchoice.demo.state.TapState
import kotlinx.coroutines.flow.StateFlow

data class PaymentUiState(
    val merchant: String,
    val amount: Double,
    val recommendation: CardRecommendation,
    val alternatives: List<CardRecommendation>,
)

class PaymentViewModel(application: Application) : AndroidViewModel(application) {
    private val amount = 200.0
    private val ranking = CardRecommendationEngine().rank(DemoCardRepository.cards, amount)

    val uiState = PaymentUiState(
        merchant = "Kroger",
        amount = amount,
        recommendation = ranking.first(),
        alternatives = ranking.drop(1),
    )
    val tapState: StateFlow<TapState> = PaymentSessionStore.state
    val hceReady: StateFlow<Boolean> = PaymentSessionStore.hceReady

    init {
        // The recommendation immediately becomes the HCE credential; no extra user action.
        DemoCardRepository.select(application, uiState.recommendation.card)
        PaymentSessionStore.update(TapState.READY)
    }

    fun reset() = PaymentSessionStore.update(TapState.READY)
}
