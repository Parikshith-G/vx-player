/**
 * Role: UI dimension calculations and shape drawable factory.
 * Responsibility: Converts density-independent pixels (dp) to physical pixels and generates rounded drawables.
 * Details: Used across all programmatic UI components for consistent geometry and theming.
 */
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
