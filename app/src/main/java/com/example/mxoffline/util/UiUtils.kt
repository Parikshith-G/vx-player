package com.example.mxoffline.util

import android.content.Context
import android.graphics.drawable.GradientDrawable

object UiUtils {
    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    fun rounded(color: Int, radiusDp: Int, context: Context): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
        }
    }
}
