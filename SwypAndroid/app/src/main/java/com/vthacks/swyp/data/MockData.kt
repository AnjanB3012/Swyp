package com.vthacks.swyp.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ShoppingCart
import com.vthacks.swyp.ui.theme.SwypColors

data class CreditCard(
    val name: String,
    val last4: String,
    val rewardRate: Double,
    val perk: String,
    val used: Int,
    val limit: Int,
    val color: Color,
) {
    val usedPercent get() = used * 100 / limit
}

data class Transaction(val merchant: String, val category: String, val whenLabel: String, val amount: Double, val icon: ImageVector)

data class Deal(val store: String, val distance: String, val category: String, val headline: String, val cardHint: String, val icon: ImageVector)

data class CategorySpend(val name: String, val amount: Double, val icon: ImageVector)

object MockData {
    const val userName = "Alex"

    val cards = listOf(
        CreditCard("Everyday 2%", "4821", 0.02, "2% on every purchase", 300, 2000, SwypColors.TealDark),
        CreditCard("Grocery 3X", "7306", 0.03, "3X on groceries", 450, 1000, SwypColors.Navy),
        CreditCard("Travel Rewards", "9012", 0.02, "2X on travel", 1050, 7000, SwypColors.CardBlue),
    )

    val totalUsed get() = cards.sumOf { it.used }
    val totalLimit get() = cards.sumOf { it.limit }

    val stores = listOf("Kroger", "Campus Coffee", "Target")

    val transactions = listOf(
        Transaction("Kroger", "Groceries", "Today", 54.20, Icons.Filled.ShoppingCart),
        Transaction("Campus Coffee", "Dining", "Yesterday", 6.50, Icons.Filled.LocalCafe),
    )

    val deals = listOf(
        Deal("Kroger", "0.8 mi", "Groceries", "Save \$1.80 on ketchup", "Use Everyday 2% · 2% back", Icons.Filled.ShoppingCart),
        Deal("Campus Coffee", "0.3 mi", "Dining", "\$1 off a large coffee", "Use Everyday 2% · 2% back", Icons.Filled.LocalCafe),
    )

    val weekly = listOf("W1" to 0.55f, "W2" to 0.72f, "W3" to 0.92f, "W4" to 0.66f)

    val categories = listOf(
        CategorySpend("Groceries", 540.00, Icons.Filled.ShoppingCart),
        CategorySpend("Dining", 210.00, Icons.Filled.LocalCafe),
        CategorySpend("Transport", 180.00, Icons.Filled.DirectionsBus),
        CategorySpend("Other", 318.60, Icons.Filled.MoreHoriz),
    )
    const val monthTotal = 1248.60
}

fun Double.money(): String = "$" + String.format("%,.2f", this)
