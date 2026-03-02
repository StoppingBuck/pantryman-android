package com.example.pantryman

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryman.databinding.ItemCategoryHeaderBinding
import com.example.pantryman.databinding.ItemPantryItemBinding

sealed class PantryListItem {
    data class Header(val category: String) : PantryListItem()
    data class Item(val ingredient: Ingredient) : PantryListItem()
}

private object PantryDiffCallback : DiffUtil.ItemCallback<PantryListItem>() {
    override fun areItemsTheSame(oldItem: PantryListItem, newItem: PantryListItem): Boolean {
        return when {
            oldItem is PantryListItem.Header && newItem is PantryListItem.Header ->
                oldItem.category == newItem.category
            oldItem is PantryListItem.Item && newItem is PantryListItem.Item ->
                oldItem.ingredient.slug == newItem.ingredient.slug
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: PantryListItem, newItem: PantryListItem): Boolean {
        return oldItem == newItem
    }
}

class PantryAdapter(
    private val onItemClick: (Ingredient) -> Unit,
) : ListAdapter<PantryListItem, RecyclerView.ViewHolder>(PantryDiffCallback) {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ITEM = 1
    }

    class HeaderViewHolder(val binding: ItemCategoryHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class ItemViewHolder(val binding: ItemPantryItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is PantryListItem.Header -> VIEW_TYPE_HEADER
        is PantryListItem.Item -> VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                ItemCategoryHeaderBinding.inflate(inflater, parent, false)
            )
            else -> ItemViewHolder(
                ItemPantryItemBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is PantryListItem.Header -> {
                (holder as HeaderViewHolder).binding.textCategory.text = item.category
            }
            is PantryListItem.Item -> {
                val ingredient = item.ingredient
                with((holder as ItemViewHolder).binding) {
                    textName.text = ingredient.name.replaceFirstChar { it.uppercase() }
                    textDetails.text = buildDetails(ingredient)
                    root.setOnClickListener { onItemClick(ingredient) }
                }
            }
        }
    }

    /** Returns the ingredient at [position] if it's an Item row, null if it's a Header. */
    fun getIngredientAt(position: Int): Ingredient? {
        return (getItem(position) as? PantryListItem.Item)?.ingredient
    }

    private fun buildDetails(ingredient: Ingredient): String {
        val qty = ingredient.quantity?.takeIf { it.isNotEmpty() && it != "0" }
        val unit = ingredient.quantityType?.takeIf { it.isNotEmpty() }
        return when {
            qty != null && unit != null -> "$qty $unit"
            qty != null -> qty
            else -> ""
        }
    }
}
