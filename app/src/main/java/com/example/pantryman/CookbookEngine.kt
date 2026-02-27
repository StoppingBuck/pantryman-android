package com.example.pantryman

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Data class representing an ingredient with pantry status
 */
data class Ingredient(
    val name: String,
    val slug: String,
    val category: String,
    val kb: String?,
    val tags: List<String>,
    val isInPantry: Boolean = false,
    val quantity: String?,
    val quantityType: String?,
    val lastUpdated: String?
)

/**
 * Kotlin wrapper for the Rust cookbook-engine
 */
class CookbookEngine(dataDir: String) {
    
    private var nativePtr: Long = 0
    private val gson = Gson()
    
    init {
        try {
            Log.d("CookbookEngine", "Loading native library: pantryman_bridge")
            System.loadLibrary("pantryman_bridge")
            Log.d("CookbookEngine", "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("CookbookEngine", "Failed to load native library: ${e.message}")
            throw RuntimeException("Failed to load native library pantryman_bridge", e)
        } catch (e: Exception) {
            Log.e("CookbookEngine", "Unexpected error loading native library: ${e.message}")
            throw e
        }
        Log.d("CookbookEngine", "Calling createDataManager with dataDir: $dataDir")
        nativePtr = createDataManager(dataDir)
        Log.d("CookbookEngine", "createDataManager returned nativePtr: $nativePtr")
        if (nativePtr == 0L) {
            Log.e("CookbookEngine", "Failed to initialize cookbook engine with data directory: $dataDir")
            throw RuntimeException("Failed to initialize cookbook engine with data directory: $dataDir")
        }
    }
    
    /**
     * Get all ingredients with their pantry status
     */
    fun getAllIngredients(): List<Ingredient> {
        Log.d("CookbookEngine", "=== getAllIngredients() called ===")
        val json = getAllIngredientsJson(nativePtr)
        Log.d("CookbookEngine", "=== getAllIngredientsJson returned: ${json.take(100)}... ===")
        val type = object : TypeToken<List<Ingredient>>() {}.type
        val ingredients = gson.fromJson<List<Ingredient>>(json, type) ?: emptyList()
        
        Log.d("CookbookEngine", "=== Parsed ${ingredients.size} ingredients ===")
        for (ingredient in ingredients.take(3)) {
            Log.d("CookbookEngine", "Ingredient: ${ingredient.name}, isInPantry: ${ingredient.isInPantry}, quantity: ${ingredient.quantity}")
        }
        
        return ingredients
    }
    
    /**
     * Get ingredients grouped by category
     */
    fun getIngredientsByCategory(): Map<String, List<Ingredient>> {
        val json = getIngredientsByCategoryJson(nativePtr)
        val type = object : TypeToken<Map<String, List<Ingredient>>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }
    
    /**
     * Update the pantry status of an ingredient
     * @param ingredientName Name of the ingredient
     * @param addToPantry true to add to pantry, false to remove
     * @param quantity Optional quantity (ignored if addToPantry is false)
     * @param quantityType Optional quantity type (ignored if addToPantry is false)
     */
    fun updatePantryStatus(
        ingredientName: String, 
        addToPantry: Boolean, 
        quantity: Double? = null, 
        quantityType: String? = null
    ): Boolean {
        val qty = quantity?.toLong() ?: 0L
        val qtyType = quantityType ?: ""
        return updatePantryStatus(nativePtr, ingredientName, addToPantry, qty, qtyType)
    }
    
    /**
     * Create a new ingredient
     */
    fun createIngredient(
        name: String,
        category: String,
        kbSlug: String? = null,
        tags: List<String>? = null
    ): Boolean {
        val tagsJson = gson.toJson(tags ?: emptyList<String>())
        return createIngredient(nativePtr, name, category, kbSlug ?: "", tagsJson)
    }
    
    /**
     * Update an existing ingredient
     */
    fun updateIngredient(
        originalName: String,
        newName: String,
        category: String,
        kbSlug: String? = null,
        tags: List<String>? = null
    ): Boolean {
        val tagsJson = gson.toJson(tags ?: emptyList<String>())
        return updateIngredient(nativePtr, originalName, newName, category, kbSlug ?: "", tagsJson)
    }
    
    /**
     * Delete an ingredient
     */
    fun deleteIngredient(ingredientName: String): Boolean {
        return deleteIngredient(nativePtr, ingredientName)
    }
    
    /**
     * Get all available categories
     */
    fun getAllCategories(): List<String> {
        val json = getAllCategories(nativePtr)
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    /**
     * Clean up native resources
     */
    fun cleanup() {
        if (nativePtr != 0L) {
            destroyDataManager(nativePtr)
            nativePtr = 0L
        }
    }
    
    protected fun finalize() {
        cleanup()
    }
    
    // Native method declarations
    private external fun createDataManager(dataDir: String): Long
    private external fun destroyDataManager(nativePtr: Long)
    private external fun getAllIngredientsJson(nativePtr: Long): String
    private external fun getIngredientsByCategoryJson(nativePtr: Long): String
    private external fun updatePantryStatus(nativePtr: Long, ingredientName: String, addToPantry: Boolean, quantity: Long, quantityType: String): Boolean
    private external fun createIngredient(nativePtr: Long, name: String, category: String, kbSlug: String, tagsJson: String): Boolean
    private external fun updateIngredient(nativePtr: Long, originalName: String, newName: String, category: String, kbSlug: String, tagsJson: String): Boolean
    private external fun deleteIngredient(nativePtr: Long, ingredientName: String): Boolean
    private external fun getAllCategories(nativePtr: Long): String
}
