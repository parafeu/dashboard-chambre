package fr.alex.chambre.mealie

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

private val FRACTIONS = listOf(0.25 to "¼", 1.0 / 3 to "⅓", 0.5 to "½", 2.0 / 3 to "⅔", 0.75 to "¾")
private val ROUNDED_UNITS = setOf("g", "ml")
private val METRIC_UNITS = setOf("g", "kg", "mg", "ml", "cl", "dl", "l")

/** Readable kitchen quantity: « 1 ½ », « ¾ », grams/ml from 10 rounded to 5, else one decimal with a comma. */
fun formatQuantity(value: Double, unit: String?): String {
    if (unit?.lowercase() in ROUNDED_UNITS && value >= 10) return ((value / 5).roundToLong() * 5).toString()
    val whole = floor(value + 1e-9).toLong()
    val frac = value - whole
    if (frac < 0.03) return whole.toString()
    if (frac > 0.97) return (whole + 1).toString()
    // Metric units stay decimal (« 7,5 ml », not « 7 ½ ml »); spoons, pinches and pieces get fraction signs.
    if (unit?.lowercase() !in METRIC_UNITS) FRACTIONS.firstOrNull { abs(it.first - frac) < 0.03 }?.let { (_, sign) ->
        return if (whole > 0) "$whole $sign" else sign
    }
    val oneDecimal = (value * 10).roundToLong() / 10.0
    return oneDecimal.toString().removeSuffix(".0").replace('.', ',')
}

/** An ingredient as displayed: [quantity] (with its unit) in bold when it scales, then [text] and [note]. */
data class IngredientLine(val quantity: String?, val text: String, val note: String?)

fun ingredientLine(ingredient: Ingredient, factor: Double): IngredientLine {
    if (!ingredient.scalable) {
        return if (ingredient.food != null) IngredientLine(null, ingredient.food, ingredient.note)
        else IngredientLine(null, ingredient.display, null)
    }
    val q = formatQuantity(ingredient.quantity!! * factor, ingredient.unit)
    return IngredientLine(listOfNotNull(q, ingredient.unit).joinToString(" "), ingredient.food!!, ingredient.note)
}
