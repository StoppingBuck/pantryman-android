package com.example.pantryman

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryman.databinding.ItemPantryItemBinding

class PantryAdapter(
    private val onItemClick: (Ingredient) -> Unit,
) : RecyclerView.Adapter<PantryAdapter.ViewHolder>() {

    private var items = listOf<Ingredient>()

    fun submitList(newItems: List<Ingredient>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemPantryItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPantryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ingredient = items[position]
        with(holder.binding) {
            textName.text = ingredient.name.replaceFirstChar { it.uppercase() }
            textDetails.text = buildDetails(ingredient)
            root.setOnClickListener { onItemClick(ingredient) }
            btnEdit.setOnClickListener { onItemClick(ingredient) }
        }
    }

    override fun getItemCount() = items.size

    private fun buildDetails(ingredient: Ingredient): String {
        val qty = ingredient.quantity?.takeIf { it.isNotEmpty() && it != "0" }
        val unit = ingredient.quantityType?.takeIf { it.isNotEmpty() }
        return when {
            qty != null && unit != null -> "$qty $unit · ${ingredient.category}"
            qty != null -> "$qty · ${ingredient.category}"
            else -> ingredient.category
        }
    }
}
