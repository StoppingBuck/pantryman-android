package com.example.pantryman

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Swipe gesture handler for the main pantry list.
 *
 * - Swipe LEFT  → remove from pantry (red background, trash icon hint)
 * - Swipe RIGHT → "still have this" / refresh timestamp (green background, checkmark hint)
 *
 * Header rows are not swipeable — the callback guards on VIEW_TYPE_ITEM.
 */
class PantrySwipeCallback(
    private val context: Context,
    private val onSwipeLeft: (Ingredient) -> Unit,
    private val onSwipeRight: (Ingredient) -> Unit,
    private val getIngredientAt: (Int) -> Ingredient?,
) : ItemTouchHelper.SimpleCallback(
    0,
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
) {

    private val removeBackground = ColorDrawable(Color.parseColor("#B71C1C"))  // deep red
    private val touchBackground  = ColorDrawable(Color.parseColor("#1B5E20"))  // deep green

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = context.resources.displayMetrics.density * 14  // 14sp
    }

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        // Only swipe ingredient rows, not category headers
        return if (viewHolder is PantryAdapter.ItemViewHolder) {
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        } else {
            0
        }
    }

    override fun onMove(
        rv: RecyclerView,
        vh: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ) = false  // no drag-and-drop

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val pos = viewHolder.bindingAdapterPosition
        val ingredient = getIngredientAt(pos) ?: return
        when (direction) {
            ItemTouchHelper.LEFT  -> onSwipeLeft(ingredient)
            ItemTouchHelper.RIGHT -> {
                // Swipe-right is non-destructive: the item stays in the list.
                // Notify the adapter to snap the view back to its resting position.
                // onSwipeRight updates the engine and calls loadData() which submits a
                // new list via DiffUtil — but we also need an immediate notifyItemChanged
                // so ItemTouchHelper releases its hold on the view before DiffUtil runs.
                onSwipeRight(ingredient)
                // The adapter position may have shifted by the time the callback returns,
                // but bindingAdapterPosition was captured before any list change, so it
                // is still valid for this single notifyItemChanged call.
                viewHolder.bindingAdapter?.notifyItemChanged(pos)
            }
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top

        if (dX < 0) {
            // Swipe left — red background
            removeBackground.setBounds(
                itemView.right + dX.toInt(), itemView.top,
                itemView.right, itemView.bottom
            )
            removeBackground.draw(c)

            // Label
            val label = context.getString(R.string.btn_remove_from_pantry)
            val textWidth = textPaint.measureText(label)
            val textX = itemView.right - textWidth - context.resources.displayMetrics.density * 16
            val textY = itemView.top + itemHeight / 2f + textPaint.textSize / 2f - 2f
            if (textX > itemView.right + dX) {  // only draw if background is wide enough
                c.drawText(label, textX, textY, textPaint)
            }
        } else if (dX > 0) {
            // Swipe right — green background
            touchBackground.setBounds(
                itemView.left, itemView.top,
                itemView.left + dX.toInt(), itemView.bottom
            )
            touchBackground.draw(c)

            // Label
            val label = context.getString(R.string.label_still_have_it)
            val textX = itemView.left + context.resources.displayMetrics.density * 16
            val textY = itemView.top + itemHeight / 2f + textPaint.textSize / 2f - 2f
            if (dX > textPaint.measureText(label) + context.resources.displayMetrics.density * 32) {
                c.drawText(label, textX, textY, textPaint)
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
