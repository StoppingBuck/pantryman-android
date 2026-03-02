package com.example.pantryman

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryman.databinding.ItemCreateNewIngredientBinding
import com.example.pantryman.databinding.ItemIngredientPickerBinding

/**
 * Adapter for the "Add to pantry" ingredient picker.
 *
 * Always appends a "Create new" row as the last item so the user can create a missing
 * ingredient without dismissing the keyboard or scrolling past the dialog boundary.
 * When the search field is non-empty the row shows "Create '<query>'" so tapping it
 * pre-fills the ingredient name.
 */
class IngredientPickerAdapter(
    private val onItemClick: (Ingredient) -> Unit,
    private val onCreateNew: (nameHint: String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_INGREDIENT = 0
        private const val VIEW_TYPE_CREATE_NEW = 1
    }

    private var items = listOf<Ingredient>()
    private var currentQuery = ""

    fun submitList(newItems: List<Ingredient>, query: String = "") {
        items = newItems
        currentQuery = query
        notifyDataSetChanged()
    }

    // Ingredient rows + 1 permanent "Create new" row at the end
    override fun getItemCount() = items.size + 1

    override fun getItemViewType(position: Int) =
        if (position < items.size) VIEW_TYPE_INGREDIENT else VIEW_TYPE_CREATE_NEW

    class IngredientViewHolder(val binding: ItemIngredientPickerBinding) :
        RecyclerView.ViewHolder(binding.root)

    class CreateNewViewHolder(val binding: ItemCreateNewIngredientBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_INGREDIENT -> IngredientViewHolder(
                ItemIngredientPickerBinding.inflate(inflater, parent, false)
            )
            else -> CreateNewViewHolder(
                ItemCreateNewIngredientBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is IngredientViewHolder -> {
                val ingredient = items[position]
                with(holder.binding) {
                    textName.text = ingredient.name.replaceFirstChar { it.uppercase() }
                    textCategory.text = ingredient.category
                    textInPantry.isVisible = ingredient.isInPantry
                    root.setOnClickListener { onItemClick(ingredient) }
                }
            }
            is CreateNewViewHolder -> {
                val ctx = holder.itemView.context
                holder.binding.textCreate.text = if (currentQuery.isNotEmpty())
                    ctx.getString(R.string.action_create_named, currentQuery)
                else
                    ctx.getString(R.string.action_create_new)
                holder.binding.root.setOnClickListener { onCreateNew(currentQuery) }
            }
        }
    }
}
