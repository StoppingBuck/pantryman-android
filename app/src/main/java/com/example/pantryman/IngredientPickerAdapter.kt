package com.example.pantryman

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryman.databinding.ItemIngredientPickerBinding

class IngredientPickerAdapter(
    private val onItemClick: (Ingredient) -> Unit,
) : RecyclerView.Adapter<IngredientPickerAdapter.ViewHolder>() {

    private var items = listOf<Ingredient>()

    fun submitList(newItems: List<Ingredient>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemIngredientPickerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIngredientPickerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ingredient = items[position]
        with(holder.binding) {
            textName.text = ingredient.name.replaceFirstChar { it.uppercase() }
            textCategory.text = ingredient.category
            textInPantry.isVisible = ingredient.isInPantry
            root.setOnClickListener { onItemClick(ingredient) }
        }
    }

    override fun getItemCount() = items.size
}
