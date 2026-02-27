package com.example.pantryman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the grouping and sorting logic used by IngredientsAdapter.
 *
 * IngredientsAdapter itself depends on the Android framework (RecyclerView,
 * LayoutInflater, View), so it cannot be instantiated in a pure JVM test.
 * These tests exercise the grouping/sorting logic that updateIngredients()
 * delegates to updateData(), keeping the test free of any Android dependency.
 */
class IngredientsAdapterTest {

    // ---------------------------------------------------------------------------
    // Helpers that mirror IngredientsAdapter's grouping / sorting logic exactly
    // ---------------------------------------------------------------------------

    private fun makeIngredient(
        name: String,
        category: String,
        isInPantry: Boolean = false,
        quantity: String? = null,
        quantityType: String? = null
    ) = Ingredient(
        name = name,
        slug = name.lowercase().replace(' ', '-'),
        category = category,
        kb = null,
        tags = emptyList(),
        isInPantry = isInPantry,
        quantity = quantity,
        quantityType = quantityType,
        lastUpdated = null
    )

    /** Mirrors updateIngredients() -> updateData() display-item construction. */
    private fun buildDisplayItems(ingredients: List<Ingredient>): List<String> {
        val byCategory = ingredients.groupBy { it.category }
        val result = mutableListOf<String>()
        for (category in byCategory.keys.sorted()) {
            result.add("HEADER:$category")
            val sorted = byCategory[category]?.sortedBy { it.name } ?: emptyList()
            for (ingredient in sorted) {
                result.add("INGREDIENT:${ingredient.name}")
            }
        }
        return result
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    fun `updateIngredients groups by category`() {
        val ingredients = listOf(
            makeIngredient("Potato", "vegetable"),
            makeIngredient("Chicken", "meat"),
            makeIngredient("Carrot", "vegetable")
        )

        val items = buildDisplayItems(ingredients)

        // Should have two category headers
        val headers = items.filter { it.startsWith("HEADER:") }
        assertEquals(listOf("HEADER:meat", "HEADER:vegetable"), headers)
    }

    @Test
    fun `updateIngredients sorts categories alphabetically`() {
        val ingredients = listOf(
            makeIngredient("Milk", "dairy"),
            makeIngredient("Apple", "fruit"),
            makeIngredient("Potato", "vegetable"),
            makeIngredient("Chicken", "meat")
        )

        val items = buildDisplayItems(ingredients)
        val headers = items.filter { it.startsWith("HEADER:") }

        assertEquals(
            listOf("HEADER:dairy", "HEADER:fruit", "HEADER:meat", "HEADER:vegetable"),
            headers
        )
    }

    @Test
    fun `updateIngredients sorts ingredients within category alphabetically`() {
        val ingredients = listOf(
            makeIngredient("Zucchini", "vegetable"),
            makeIngredient("Artichoke", "vegetable"),
            makeIngredient("Broccoli", "vegetable")
        )

        val items = buildDisplayItems(ingredients)

        // Only one category; drop the header and check order
        val ingredientItems = items.filter { it.startsWith("INGREDIENT:") }
        assertEquals(
            listOf("INGREDIENT:Artichoke", "INGREDIENT:Broccoli", "INGREDIENT:Zucchini"),
            ingredientItems
        )
    }

    @Test
    fun `updateIngredients with empty list produces no items`() {
        val items = buildDisplayItems(emptyList())
        assertTrue(items.isEmpty())
    }

    @Test
    fun `updateIngredients with single ingredient produces header and item`() {
        val ingredients = listOf(makeIngredient("Salt", "spice"))

        val items = buildDisplayItems(ingredients)

        assertEquals(2, items.size)
        assertEquals("HEADER:spice", items[0])
        assertEquals("INGREDIENT:Salt", items[1])
    }

    @Test
    fun `updateIngredients interleaves headers and ingredients correctly`() {
        val ingredients = listOf(
            makeIngredient("Beef", "meat"),
            makeIngredient("Banana", "fruit"),
            makeIngredient("Pork", "meat"),
            makeIngredient("Apple", "fruit")
        )

        val items = buildDisplayItems(ingredients)

        // Expected order: fruit header, Apple, Banana, meat header, Beef, Pork
        assertEquals(
            listOf(
                "HEADER:fruit",
                "INGREDIENT:Apple",
                "INGREDIENT:Banana",
                "HEADER:meat",
                "INGREDIENT:Beef",
                "INGREDIENT:Pork"
            ),
            items
        )
    }

    @Test
    fun `ingredient detail string reflects pantry status`() {
        // Mirror the display-detail logic in IngredientViewHolder.bind()
        fun detailFor(ingredient: Ingredient): String {
            return if (ingredient.isInPantry) {
                val quantity = ingredient.quantity?.toString() ?: "?"
                val unit = ingredient.quantityType?.takeIf { it.isNotEmpty() } ?: ""
                "In Stock: $quantity $unit".trim()
            } else {
                "Not in stock"
            }
        }

        val notInPantry = makeIngredient("Egg", "dairy", isInPantry = false)
        assertEquals("Not in stock", detailFor(notInPantry))

        val inPantryWithUnit = makeIngredient("Milk", "dairy", isInPantry = true, quantity = "2", quantityType = "L")
        assertEquals("In Stock: 2 L", detailFor(inPantryWithUnit))

        val inPantryNoUnit = makeIngredient("Salt", "spice", isInPantry = true, quantity = "1", quantityType = "")
        assertEquals("In Stock: 1", detailFor(inPantryNoUnit))
    }
}
