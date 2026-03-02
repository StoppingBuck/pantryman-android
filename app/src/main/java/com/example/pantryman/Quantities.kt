package com.example.pantryman

/**
 * Parses a free-text quantity string into a (numeric quantity, unit) pair.
 *
 * The numeric part and unit are kept separate so that recipe-to-pantry quantity
 * matching can be added later without changing the storage format.
 *
 * Examples:
 *   "2 cans"     → (2.0, "cans")
 *   "500 g"      → (500.0, "g")
 *   "a handful"  → (null, "a handful")
 *   "3"          → (3.0, null)
 *   ""           → (null, null)
 */
fun parseQuantityString(text: String): Pair<Double?, String?> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Pair(null, null)
    val match = Regex("""^(\d+(?:[.,]\d+)?)\s*(.*)$""").matchEntire(trimmed)
        ?: return Pair(null, trimmed)
    val qty = match.groupValues[1].replace(',', '.').toDoubleOrNull()
    val unit = match.groupValues[2].trim().takeIf { it.isNotEmpty() }
    return Pair(qty, unit)
}

/**
 * Combines a numeric quantity and unit back into a display string for pre-filling the field.
 *
 * (2.0, "cans") → "2 cans"
 * (500.0, "g")  → "500 g"
 * (null, "a handful") → "a handful"
 * (3.0, null)   → "3"
 */
fun formatQuantityString(qty: Double?, unit: String?): String {
    val qtyStr = qty?.let {
        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
    }
    return listOfNotNull(qtyStr, unit?.takeIf { it.isNotEmpty() }).joinToString(" ")
}
