package com.example.pantryman

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.pantryman.databinding.ActivityShoppingModeBinding
import com.example.pantryman.databinding.DialogPantryItemBinding

/**
 * Shopping Mode — a full-screen checklist of every known ingredient, grouped by category.
 *
 * Usage:
 *   - Tap a row to toggle it in-stock (checked) / out-of-stock (unchecked).
 *   - Long-press a row to set a specific quantity for that item.
 *   - Tap Done (FAB) to write all changes to the pantry in one batch and return.
 *
 * Items that are already in the pantry are pre-checked. Their existing quantity is shown
 * as a small label so the user can see what was previously recorded.
 *
 * The activity operates on a local copy of ingredient state and only commits on Done,
 * so the user can change their mind freely without triggering writes.
 */
class ShoppingModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShoppingModeBinding
    private lateinit var engine: JanusEngine
    private lateinit var adapter: ShoppingIngredientAdapter

    /** All ingredients as loaded from the engine. */
    private var allIngredients = listOf<Ingredient>()

    /** The set of ingredient slugs the user has checked in this session. */
    private val checkedSlugs = mutableSetOf<String>()

    /**
     * Per-item quantity overrides set via long-press.
     * Only contains items the user explicitly set a quantity for in this session.
     */
    private val quantityOverrides = mutableMapOf<String, Pair<Double?, String?>>()

    private var searchQuery = ""

    companion object {
        private const val TAG = "ShoppingModeActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShoppingModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val dataDir = intent.getStringExtra(MainActivity.EXTRA_DATA_DIR) ?: run {
            Toast.makeText(this, "Missing data directory", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val deviceId = intent.getStringExtra(MainActivity.EXTRA_DEVICE_ID) ?: run {
            Toast.makeText(this, "Missing device ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            engine = JanusEngine(dataDir, deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engine", e)
            Toast.makeText(this, "Failed to load pantry data", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupAdapter()
        setupSearch()
        setupFab()
        loadData()
    }

    private fun setupAdapter() {
        adapter = ShoppingIngredientAdapter(
            onToggle = { ingredient -> toggleItem(ingredient) },
            onLongPress = { ingredient -> showQuantityDialog(ingredient) }
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(StickyHeaderDecoration(
            adapter = adapter,
            headerViewType = ShoppingIngredientAdapter.VIEW_TYPE_HEADER,
            getHeaderLabel = { pos ->
                (adapter.currentList.getOrNull(pos) as? ShoppingListItem.Header)?.category ?: ""
            },
        ))
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener { text ->
            searchQuery = text?.toString() ?: ""
            updateDisplay()
        }
    }

    private fun setupFab() {
        binding.fabDone.setOnClickListener { applyAndFinish() }
    }

    private fun loadData() {
        allIngredients = engine.getAllIngredients()

        // Pre-check items that are currently in the pantry
        checkedSlugs.clear()
        allIngredients.filter { it.isInPantry }.forEach { checkedSlugs.add(it.slug) }

        updateDisplay()
    }

    private fun toggleItem(ingredient: Ingredient) {
        if (checkedSlugs.contains(ingredient.slug)) {
            checkedSlugs.remove(ingredient.slug)
        } else {
            checkedSlugs.add(ingredient.slug)
        }
        updateCheckedCount()
        updateDisplay()
    }

    /**
     * Long-press handler: shows the quantity dialog so the user can specify
     * how much of this item they have. Implicitly also marks the item checked.
     */
    private fun showQuantityDialog(ingredient: Ingredient) {
        val db = DialogPantryItemBinding.inflate(layoutInflater)

        val override = quantityOverrides[ingredient.slug]
        val existingQty = override?.first ?: ingredient.quantity?.toDoubleOrNull()
        val existingUnit = override?.second ?: ingredient.quantityType
        val prefill = formatQuantityString(existingQty, existingUnit)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(ingredient.name.replaceFirstChar { it.uppercase() })
            .setView(db.root)
            .setPositiveButton(R.string.btn_update) { _, _ ->
                val (qty, unit) = parseQuantityString(db.editQuantity.text?.toString() ?: "")
                quantityOverrides[ingredient.slug] = Pair(qty, unit)
                // Checking the item is implicit when you set a quantity
                checkedSlugs.add(ingredient.slug)
                updateCheckedCount()
                updateDisplay()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .create()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener {
            db.editQuantity.setText(prefill)
            db.editQuantity.setSelection(prefill.length)
            db.editQuantity.requestFocus()
        }
        dialog.show()
    }

    private fun updateDisplay() {
        val query = searchQuery.trim()
        val filtered = allIngredients
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true)
                                      || it.category.contains(query, ignoreCase = true) }
            .sortedWith(compareBy({ it.category.ifEmpty { "\uFFFF" } }, { it.name }))

        adapter.submitList(buildSectionedList(filtered))
        updateCheckedCount()
    }

    private fun buildSectionedList(ingredients: List<Ingredient>): List<ShoppingListItem> {
        val result = mutableListOf<ShoppingListItem>()
        var currentCat: String? = null
        for (ing in ingredients) {
            val cat = ing.category.ifEmpty { getString(R.string.category_uncategorised) }
            if (cat != currentCat) {
                result.add(ShoppingListItem.Header(cat))
                currentCat = cat
            }
            result.add(ShoppingListItem.Item(ing, checkedSlugs.contains(ing.slug)))
        }
        return result
    }

    private fun updateCheckedCount() {
        val count = checkedSlugs.size
        if (count > 0) {
            binding.textCheckedCount.text = getString(R.string.shopping_items_checked, count)
            binding.textCheckedCount.visibility = View.VISIBLE
        } else {
            binding.textCheckedCount.visibility = View.GONE
        }
    }

    /**
     * Write all changes to the pantry in one batch and close the activity.
     *
     * - Checked items with no quantity override keep their existing quantity (or none).
     * - Checked items with a quantity override use the new quantity.
     * - Unchecked items that were previously in the pantry are tombstoned (removed).
     * - Unchecked items that were not in the pantry are left untouched.
     */
    private fun applyAndFinish() {
        var changes = 0

        for (ingredient in allIngredients) {
            val isChecked = checkedSlugs.contains(ingredient.slug)
            val wasInPantry = ingredient.isInPantry

            when {
                isChecked -> {
                    // Determine the quantity to write
                    val override = quantityOverrides[ingredient.slug]
                    val qty = override?.first ?: ingredient.quantity?.toDoubleOrNull()
                    val unit = override?.second ?: ingredient.quantityType?.takeIf { it.isNotEmpty() }

                    val success = engine.updatePantryStatus(ingredient.name, true, qty, unit)
                    if (success) changes++
                    else Log.w(TAG, "Failed to update pantry for ${ingredient.name}")
                }
                !isChecked && wasInPantry -> {
                    // User unchecked something that was in the pantry → tombstone it
                    val success = engine.updatePantryStatus(ingredient.name, false)
                    if (success) changes++
                    else Log.w(TAG, "Failed to remove ${ingredient.name} from pantry")
                }
                // !isChecked && !wasInPantry → no-op
            }
        }

        Log.d(TAG, "Shopping mode applied $changes pantry changes")
        Toast.makeText(this, R.string.msg_shopping_mode_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.cleanup()
    }
}
