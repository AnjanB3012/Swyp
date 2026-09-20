package com.tapchoice.demo.recommendation

import com.tapchoice.demo.data.DemoCardRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class CardRecommendationEngineTest {
    @Test fun recommendsAmexForKrogerExample() {
        val result = CardRecommendationEngine().rank(DemoCardRepository.cards, 200.0)
        assertEquals("cred_amex_01", result.first().card.id)
        assertEquals(0.21, result.first().projectedUtilization, 0.0001)
    }
}
