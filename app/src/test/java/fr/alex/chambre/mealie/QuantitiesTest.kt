package fr.alex.chambre.mealie

import org.junit.Assert.assertEquals
import org.junit.Test

class QuantitiesTest {
    @Test
    fun common_fractions_are_shown_as_fraction_signs() {
        assertEquals("¾", formatQuantity(0.75, "c. à soupe"))
        assertEquals("1 ½", formatQuantity(1.5, "c. à café"))
        assertEquals("⅓", formatQuantity(1.0 / 3, null))
        assertEquals("2", formatQuantity(2.0, null))
        assertEquals("2 ¼", formatQuantity(2.25, "pincée"))
    }

    @Test
    fun other_values_keep_one_decimal_with_a_comma() {
        assertEquals("1,2", formatQuantity(1.2, "L"))
        assertEquals("0,1", formatQuantity(0.1, "kg"))
    }

    @Test
    fun grams_and_millilitres_from_ten_are_rounded_to_five() {
        assertEquals("225", formatQuantity(225.0, "ml"))
        assertEquals("115", formatQuantity(112.5, "g"))
        assertEquals("8", formatQuantity(8.0, "g"))
        assertEquals("7,5", formatQuantity(7.5, "ml"))
    }

    @Test
    fun scaled_ingredient_follows_the_servings() {
        val riz = Ingredient(null, 75.0, "g", "riz basmati", "ou autre riz", "75 g riz basmati ou autre riz")
        assertEquals(IngredientLine("150 g", "riz basmati", "ou autre riz"), ingredientLine(riz, factor = 2.0))
        val huile = Ingredient(null, 0.75, "c. à soupe", "huile", null, "¾ c. à soupe huile")
        assertEquals(IngredientLine("1 ½ c. à soupe", "huile", null), ingredientLine(huile, factor = 2.0))
        val oeufs = Ingredient(null, 3.0, null, "œufs", null, "3 œufs")
        assertEquals(IngredientLine("1 ½", "œufs", null), ingredientLine(oeufs, factor = 0.5))
    }

    @Test
    fun unscalable_ingredients_are_shown_as_written() {
        val libre = Ingredient(null, null, null, null, "3 belles échalotes", "3 belles échalotes")
        assertEquals(IngredientLine(null, "3 belles échalotes", null), ingredientLine(libre, factor = 2.0))
        val sel = Ingredient(null, 0.0, null, "sel", "selon le goût", "sel selon le goût")
        assertEquals(IngredientLine(null, "sel", "selon le goût"), ingredientLine(sel, factor = 2.0))
    }
}
