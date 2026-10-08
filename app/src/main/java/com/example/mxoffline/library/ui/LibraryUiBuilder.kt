/**
 * Role: Programmatic layout builder for MainActivity.
 * Responsibility: Constructs and styles the complete view hierarchy for the media library screen.
 * Details: Applies edge-to-edge window insets, typography, dark palette themes, and provides typed view bindings.
 */
package com.example.mxoffline.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mxoffline.util.UiUtils

class LibraryUiViews(
    val root: LinearLayout,
    val recyclerView: RecyclerView,
    val emptyView: TextView,
    val searchInput: EditText,
    val permissionBanner: LinearLayout,
    val grantBtn: TextView,
    val pathRow: LinearLayout,
    val pathLabel: TextView,
    val backButton: TextView,
    val resultCount: TextView,
    val tabFolders: TextView,
    val tabAllVideos: TextView,
    val tabSeen: TextView
) {
    fun updateTabStyles(currentTab: Int, context: android.content.Context) {
        val activeColor = 0xffffc400.toInt()
        val inactiveColor = 0xff7d8392.toInt()
        val activeBg = UiUtils.rounded(0xff222530.toInt(), 10, context)

        val isFolders = currentTab == 0 || currentTab == 2
        tabFolders.setTextColor(if (isFolders) activeColor else inactiveColor)
        tabFolders.background = if (isFolders) activeBg else null

        val isVideos = currentTab == 1
        tabAllVideos.setTextColor(if (isVideos) activeColor else inactiveColor)
        tabAllVideos.background = if (isVideos) activeBg else null

        val isSeen = currentTab == 4
        tabSeen.setTextColor(if (isSeen) activeColor else inactiveColor)
        tabSeen.background = if (isSeen) activeBg else null
    }
}

object LibraryUiBuilder {

    interface Callback {
        fun onSearchClicked()
        fun onSortClicked()
        fun onFolderPickerClicked()
        fun onFilePickerClicked()
        fun onMoreMenuClicked()
        fun onTabSelected(tab: Int)
        fun onSearchQueryChanged(query: String)
        fun onBackPressed()
        fun onGrantPermissionClicked()
    }

    private fun getStatusBarHeight(activity: Activity): Int {
        val resourceId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) activity.resources.getDimensionPixelSize(resourceId) else UiUtils.dp(activity, 28)
    }

    fun build(activity: Activity, callback: Callback): LibraryUiViews {
        val dp = { v: Int -> UiUtils.dp(activity, v) }
        val initialTopPadding = getStatusBarHeight(activity).coerceAtLeast(dp(28)) + dp(14)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff0f1115.toInt())
            setPadding(dp(16), initialTopPadding, dp(16), 0)
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val statusBarInset = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val cutoutInset = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            val navBarInset = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val topInset = maxOf(statusBarInset, cutoutInset, getStatusBarHeight(activity))
            view.setPadding(dp(16), topInset + dp(14), dp(16), navBarInset)
            windowInsets
        }

        // Header
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logoBadge = TextView(activity).apply {
            text = "VX"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xff101114.toInt())
            background = UiUtils.rounded(0xffffc400.toInt(), 13, activity)
        }
        header.addView(logoBadge, LinearLayout.LayoutParams(dp(44), dp(44)))

        val brandLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }
        brandLayout.addView(TextView(activity).apply {
            text = "VX Player"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xfff5f5f7.toInt())
        })
        brandLayout.addView(TextView(activity).apply {
            text = "OFFLINE MEDIA"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(0xff8a909d.toInt())
        })
        header.addView(brandLayout, LinearLayout.LayoutParams(0, -2, 1f))

        fun iconBtn(symbol: String, onClick: () -> Unit) = TextView(activity).apply {
            text = symbol
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xffe6e8ee.toInt())
            setOnClickListener { onClick() }
        }

        header.addView(iconBtn("⌕") { callback.onSearchClicked() }, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(iconBtn("🔀") { callback.onSortClicked() }, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(iconBtn("📁") { callback.onFolderPickerClicked() }, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(iconBtn("＋") { callback.onFilePickerClicked() }, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(iconBtn("⋮") { callback.onMoreMenuClicked() }, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(header)

        // Search Input
        val searchInput = EditText(activity).apply {
            hint = "Search videos & folders..."
            setHintTextColor(0xff777d8a.toInt())
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = UiUtils.rounded(0xff1c1f28.toInt(), 12, activity)
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    callback.onSearchQueryChanged(s?.toString().orEmpty().trim())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        root.addView(searchInput, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })

        // Permission Banner
        val permissionBanner = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = UiUtils.rounded(0xff222633.toInt(), 14, activity)
            visibility = View.GONE
        }
        val bannerText = TextView(activity).apply {
            text = "Grant storage access to scan all local videos automatically"
            textSize = 13f
            setTextColor(0xffe2e4ea.toInt())
        }
        val grantBtn = TextView(activity).apply {
            text = "Grant"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xff111317.toInt())
            background = UiUtils.rounded(0xffffc400.toInt(), 10, activity)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { callback.onGrantPermissionClicked() }
        }
        permissionBanner.addView(bannerText, LinearLayout.LayoutParams(0, -2, 1f))
        permissionBanner.addView(grantBtn)
        root.addView(permissionBanner, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // Tabs
        val tabLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        fun tabItem(label: String, onClick: () -> Unit) = TextView(activity).apply {
            text = label
            textSize = 12f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
        val tabFolders = tabItem("FOLDERS") { callback.onTabSelected(0) }
        val tabAllVideos = tabItem("ALL VIDEOS") { callback.onTabSelected(1) }
        val tabSeen = tabItem("SEEN") { callback.onTabSelected(4) }
        tabLayout.addView(tabFolders, LinearLayout.LayoutParams(0, dp(38), 1f))
        tabLayout.addView(tabAllVideos, LinearLayout.LayoutParams(0, dp(38), 1f))
        tabLayout.addView(tabSeen, LinearLayout.LayoutParams(0, dp(38), 1f))
        root.addView(tabLayout)

        // Path Row
        val pathRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = UiUtils.rounded(0xff181b24.toInt(), 12, activity)
            visibility = View.GONE
        }
        val backButton = TextView(activity).apply {
            text = "‹"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xffffc400.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, dp(10), 0)
            setOnClickListener { callback.onBackPressed() }
        }
        val pathLabel = TextView(activity).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xfff0f1f3.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        pathRow.addView(backButton)
        pathRow.addView(pathLabel, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(pathRow, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        // Result Count
        val listHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(10), dp(2), dp(6))
        }
        val resultCount = TextView(activity).apply {
            textSize = 12f
            setTextColor(0xff8c92a0.toInt())
        }
        listHeader.addView(resultCount, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(listHeader)

        // RecyclerView & Empty
        val recyclerView = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
        }
        val emptyView = TextView(activity).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xff9ea4b2.toInt())
            setPadding(dp(32), dp(40), dp(32), dp(40))
        }
        val contentFrame = FrameLayout(activity)
        contentFrame.addView(recyclerView, FrameLayout.LayoutParams(-1, -1))
        contentFrame.addView(emptyView, FrameLayout.LayoutParams(-1, -1))
        root.addView(contentFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        return LibraryUiViews(
            root, recyclerView, emptyView, searchInput, permissionBanner,
            grantBtn, pathRow, pathLabel, backButton, resultCount,
            tabFolders, tabAllVideos, tabSeen
        )
    }
}
