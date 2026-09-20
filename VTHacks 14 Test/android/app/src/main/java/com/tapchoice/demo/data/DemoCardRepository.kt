package com.tapchoice.demo.data

import android.content.Context
import com.tapchoice.demo.model.DemoCard

object DemoCardRepository {
    val cards = listOf(
        DemoCard("cred_chase_01", "Chase Freedom", "Visa", "1234", 5.0, 1_500.0, 5_000.0),
        DemoCard("cred_amex_01", "Amex Blue Cash", "Amex", "5678", 4.0, 850.0, 5_000.0),
        DemoCard("cred_savor_01", "Capital One Savor", "Mastercard", "9012", 3.0, 400.0, 4_000.0),
    )

    private const val PREFS = "tapchoice_demo"
    private const val SELECTED_CARD = "selected_card_id"

    fun select(context: Context, card: DemoCard) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(SELECTED_CARD, card.id).apply()
    }

    fun selected(context: Context): DemoCard {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SELECTED_CARD, null)
        return cards.firstOrNull { it.id == id } ?: cards.first()
    }
}

