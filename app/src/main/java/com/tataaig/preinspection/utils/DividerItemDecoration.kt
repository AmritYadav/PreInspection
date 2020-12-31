package com.tataaig.preinspection.utils

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class DividerItemDecoration(private val drawable: Drawable?,private val hasHeader: Boolean = false) : RecyclerView.ItemDecoration() {

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount

        val startIndex = if(hasHeader) 1 else 0

        // Draw divider for items except for 1st and and last Item
        for (i in startIndex..childCount - 2) {
            val child: View = parent.getChildAt(i)
            val dividerLeft = parent.paddingLeft + child.paddingLeft
            val dividerRight = parent.width - (parent.paddingRight + child.paddingRight)

            val params =
                child.layoutParams as RecyclerView.LayoutParams
            val dividerTop: Int = child.bottom + params.bottomMargin
            drawable?.let {
                val dividerBottom: Int = dividerTop + drawable.intrinsicHeight
                drawable.setBounds(dividerLeft, dividerTop, dividerRight, dividerBottom)
                drawable.draw(canvas)
            }
        }
    }
}