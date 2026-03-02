package com.example.pantryman

import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pantryman.databinding.ItemCategoryHeaderBinding

/**
 * Pins the current section's category header to the top of the RecyclerView as the
 * user scrolls through the list (the "sticky header" / "frozen row" pattern).
 *
 * When the next section header enters the top-[headerHeight]dp zone, the stuck header
 * is pushed upward by the same amount so they exchange smoothly.
 *
 * Works with any adapter whose header rows report a specific [headerViewType].
 *
 * @param adapter        The list adapter — used to query item view types.
 * @param headerViewType The view-type value that identifies category header rows.
 * @param getHeaderLabel Returns the label text for the header at the given adapter position.
 */
class StickyHeaderDecoration(
    private val adapter: RecyclerView.Adapter<*>,
    private val headerViewType: Int,
    private val getHeaderLabel: (adapterPosition: Int) -> String,
) : RecyclerView.ItemDecoration() {

    private var stickyView: View? = null
    private var stickyBinding: ItemCategoryHeaderBinding? = null
    private var lastMeasuredWidth = -1

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val lm = parent.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION) return

        // Walk backwards from the first visible row to find its section header.
        val headerPos = (firstVisible downTo 0)
            .firstOrNull { adapter.getItemViewType(it) == headerViewType }
            ?: return

        val header = ensureHeaderView(parent)
        stickyBinding?.textCategory?.text = getHeaderLabel(headerPos)

        // Scan visible children: if any header row has entered the top [header.height] px,
        // shift the sticky header upward by the same amount so it is pushed out smoothly.
        var translateY = 0f
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val childPos = parent.getChildAdapterPosition(child)
            if (childPos != RecyclerView.NO_POSITION &&
                adapter.getItemViewType(childPos) == headerViewType &&
                child.top in 1 until header.height) {
                translateY = (child.top - header.height).toFloat()
                break
            }
        }

        c.save()
        c.translate(0f, translateY)
        header.draw(c)
        c.restore()
    }

    private fun ensureHeaderView(parent: RecyclerView): View {
        if (stickyView == null) {
            val binding = ItemCategoryHeaderBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            stickyBinding = binding
            stickyView = binding.root
        }
        val view = stickyView!!
        // Re-measure only when the RecyclerView width has changed (e.g. rotation).
        if (parent.width != lastMeasuredWidth) {
            lastMeasuredWidth = parent.width
            view.measure(
                View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        return view
    }
}
