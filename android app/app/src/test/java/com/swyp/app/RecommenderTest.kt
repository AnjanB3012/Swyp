package com.swyp.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RecommenderTest {
    private val today = LocalDate.of(2026, 9, 19)
    private val savor = SwypCard("a", "savor", "Savor", "1234", 100000, 1000)
    private val quick = SwypCard("b", "quicksilver", "Quicksilver", "5678", 100000, 1000)

    @Test
    fun superstoresDoNotEarnGroceryBonus() {
        assertEquals(0.01, Recommender.rate(savor, Checkout("Walmart", 5000, "grocery")), 0.0)
        assertEquals(0.03, Recommender.rate(savor, Checkout("Kroger", 5000, "grocery")), 0.0)
    }

    @Test
    fun reservesRoomForMonthlyBill() {
        val history =
            listOf("2026-06-25", "2026-07-25", "2026-08-25").mapIndexed { i, date ->
                Purchase("$i", "a", "Internet", "bills", 10000, date)
            }
        val cards = listOf(savor.copy(balanceCents = 19000), quick)
        val ranked =
            Recommender.rank(cards, history, Checkout("Kroger", 5000, "grocery"), today = today)
        assertEquals("b", ranked.first().card.id)
        assertFalse(ranked.last().eligible)
        assertEquals(10000L, ranked.last().forecastCents)
    }

    @Test
    fun overallUtilizationCanBlockEveryCard() {
        val rows =
            Recommender.rank(
                listOf(savor.copy(balanceCents = 29000), quick.copy(balanceCents = 29000)),
                emptyList(),
                Checkout("Cafe", 5000, "dining"),
                today = today,
            )
        assertTrue(rows.none { it.eligible })
    }

    @Test
    fun expiredOrInactiveOffersCannotWin() {
        val deal =
            Offer(
                "x",
                "Bonus",
                "Cafe",
                "quicksilver",
                50.0,
                0,
                100000,
                "2026-09-18",
                "",
                true,
                false,
            )
        val result =
            Recommender.rank(
                listOf(savor, quick),
                emptyList(),
                Checkout("Cafe", 10000, "dining"),
                listOf(deal),
                today = today,
            )
        assertEquals("a", result.first().card.id)
        val active = deal.copy(expiresOn = "2026-09-30", requiresActivation = true)
        assertEquals(
            "a",
            Recommender.rank(
                    listOf(savor, quick),
                    emptyList(),
                    Checkout("Cafe", 10000, "dining"),
                    listOf(active),
                    today = today,
                )
                .first()
                .card
                .id,
        )
        assertEquals(
            "b",
            Recommender.rank(
                    listOf(savor, quick),
                    emptyList(),
                    Checkout("Cafe", 10000, "dining"),
                    listOf(active),
                    setOf("x"),
                    today = today,
                )
                .first()
                .card
                .id,
        )
    }

    @Test
    fun irregularOrStaleHistoryDoesNotForecast() {
        val rows =
            listOf("2026-01-01", "2026-02-01", "2026-03-01").map {
                Purchase("", "a", "Old membership", "bills", 5000, it)
            }
        assertTrue(Recommender.forecast(rows, today).isEmpty())
    }

    @Test
    fun coldStartAndZeroLimits() {
        assertTrue(Recommender.forecast(emptyList(), today).isEmpty())
        assertFalse(
            Recommender.rank(
                    listOf(savor.copy(limitCents = 0)),
                    emptyList(),
                    Checkout("Cafe", 500, "dining"),
                    today = today,
                )
                .first()
                .eligible
        )
    }

    @Test
    fun rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException::class.java) {
            Recommender.rank(listOf(savor), emptyList(), Checkout("Cafe", -5), today = today)
        }
    }
}
