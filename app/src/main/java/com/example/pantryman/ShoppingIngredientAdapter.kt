package com.example.pantryman

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryman.databinding.ItemCategoryHeaderBinding
import com.example.pantryman.databinding.ItemShoppingIngredientBinding

/**
 * A row in the shopping-mode checklist.
 *
 * [Header] rows separate categories.
 * [Item] rows are tappable to toggle in-stock state.
 */
sealed class ShoppingListItem {
    data class Header(val category: String) : ShoppingListItem()
    data class Item(val ingredient: Ingredient, val checked: Boolean) : ShoppingListItem()
}

private object ShoppingDiffCallback : DiffUtil.ItemCallback<ShoppingListItem>() {
    override fun areItemsTheSame(a: ShoppingListItem, b: ShoppingListItem) = when {
        a is ShoppingListItem.Header && b is ShoppingListItem.Header -> a.category == b.category
        a is ShoppingListItem.Item   && b is ShoppingListItem.Item   -> a.ingredient.slug == b.ingredient.slug
        else -> false
    }
    override fun areContentsTheSame(a: ShoppingListItem, b: ShoppingListItem) = a == b
}

class ShoppingIngredientAdapter(
    private val onToggle: (Ingredient) -> Unit,
    private val onLongPress: (Ingredient) -> Unit,
) : ListAdapter<ShoppingListItem, RecyclerView.ViewHolder>(ShoppingDiffCallback) {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ITEM   = 1
    }

    class HeaderViewHolder(val binding: ItemCategoryHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    class ItemViewHolder(val binding: ItemShoppingIngredientBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is ShoppingListItem.Header -> VIEW_TYPE_HEADER
        is ShoppingListItem.Item   -> VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(ItemCategoryHeaderBinding.inflate(inflater, parent, false))
            else             -> ItemViewHolder(ItemShoppingIngredientBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ShoppingListItem.Header -> {
                (holder as HeaderViewHolder).binding.textCategory.text = item.category
            }
            is ShoppingListItem.Item -> {
                val ingredient = item.ingredient
                with((holder as ItemViewHolder).binding) {
                    textName.text = ingredient.name.replaceFirstChar { it.uppercase() }
                    textCategory.text = ingredient.category
                    checkbox.isChecked = item.checked

                    // Show existing quantity if the ingredient is already in the pantry
                    val qty = ingredient.quantity?.takeIf { it.isNotEmpty() && it != "0" }
                    val unit = ingredient.quantityType?.takeIf { it.isNotEmpty() }
                    if (qty != null || unit != null) {
                        textCurrentQty.text = listOfNotNull(qty, unit).joinToString(" ")
                        textCurrentQty.visibility = android.view.View.VISIBLE
                    } else {
                        textCurrentQty.visibility = android.view.View.GONE
                    }

                    root.setOnClickListener { onToggle(ingredient) }
                    root.setOnLongClickListener { onLongPress(ingredient); true }
                }
            }
        }
    }
}
