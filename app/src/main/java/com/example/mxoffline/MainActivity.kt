package com.example.mxoffline

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mxoffline.library.LibraryAdapter
import com.example.mxoffline.library.LibraryListItem
import com.example.mxoffline.library.MediaScanner
import com.example.mxoffline.model.FolderItem
import com.example.mxoffline.model.SafEntry
import com.example.mxoffline.model.VideoItem
import com.example.mxoffline.util.UiUtils
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("library", MODE_PRIVATE) }
    private val resumePrefs by lazy { getSharedPreferences("player_resume", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var searchInput: EditText
    private lateinit var permissionBanner: LinearLayout
    private lateinit var pathRow: LinearLayout
    private lateinit var pathLabel: TextView
    private lateinit var backButton: TextView
    private lateinit var resultCount: TextView

    private lateinit var tabFolders: TextView
    private lateinit var tabAllVideos: TextView

    private var currentTab = TAB_FOLDERS
    private var allDeviceVideos = listOf<VideoItem>()
    private var deviceFolders = listOf<FolderItem>()
    private var currentFolderVideos = listOf<VideoItem>()
    private var currentActiveFolderName = ""

    private var treeUri: Uri? = null
    private var safEntries = listOf<SafEntry>()
    private var currentDocumentId: String? = null
    private var currentSafFolderName = ""
    private val safFolderStack = mutableListOf<Pair<String, String>>()

    private var searchQuery = ""
    private var sortMode = 0
    private var isRecentExpanded = false
    private lateinit var adapter: LibraryAdapter

    companion object {
        private const val TAB_FOLDERS = 0
        private const val TAB_ALL_VIDEOS = 1
        private const val TAB_FOLDER_VIDEOS = 2
        private const val TAB_SAF = 3
        private const val RECENT_PREVIEW_LIMIT = 4
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionBanner.visibility = View.GONE
            loadDeviceVideos()
        } else {
            Toast.makeText(this, "Storage permission is needed to scan local videos", Toast.LENGTH_LONG).show()
            updatePermissionBanner()
        }
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString("tree", uri.toString()).apply()
                openSafTree(uri)
            }.onFailure { showAccessError(it) }
        }
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && col >= 0) cursor.getString(col) else null
                } ?: "Video"
                startPlayer(listOf(VideoItem(name = name, uri = uri)), 0)
            }.onFailure { showAccessError(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xff0f1115.toInt()
        window.navigationBarColor = 0xff0f1115.toInt()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        buildScreen()
        setupBackNavigation()
        checkPermissionsAndLoad()
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) {
            loadDeviceVideos()
        } else {
            updatePermissionBanner()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pathRow.visibility == View.VISIBLE) {
                    handleBackNavigation()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissionsAndLoad() {
        if (hasStoragePermission()) {
            permissionBanner.visibility = View.GONE
            loadDeviceVideos()
        } else {
            updatePermissionBanner()
            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            permissionLauncher.launch(perm)
        }
    }

    private fun updatePermissionBanner() {
        permissionBanner.visibility = if (hasStoragePermission()) View.GONE else View.VISIBLE
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else UiUtils.dp(this, 28)
    }

    private fun buildScreen() {
        val dp = { v: Int -> UiUtils.dp(this, v) }
        val initialTopPadding = getStatusBarHeight().coerceAtLeast(dp(28)) + dp(14)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff0f1115.toInt())
            setPadding(dp(16), initialTopPadding, dp(16), 0)
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val statusBarInset = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val cutoutInset = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            val navBarInset = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            val topInset = maxOf(statusBarInset, cutoutInset, getStatusBarHeight())
            view.setPadding(
                dp(16),
                topInset + dp(14),
                dp(16),
                navBarInset
            )
            windowInsets
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logoBadge = TextView(this).apply {
            text = "VX"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xff101114.toInt())
            background = UiUtils.rounded(0xffffc400.toInt(), 13, this@MainActivity)
        }
        header.addView(logoBadge, LinearLayout.LayoutParams(dp(44), dp(44)))

        val brandLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }
        brandLayout.addView(TextView(this).apply {
            text = "VX Player"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xfff5f5f7.toInt())
        })
        brandLayout.addView(TextView(this).apply {
            text = "OFFLINE MEDIA"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(0xff8a909d.toInt())
        })
        header.addView(brandLayout, LinearLayout.LayoutParams(0, -2, 1f))

        header.addView(iconButton("⌕") { toggleSearch() }, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(iconButton("🔀") { showSortMenu() }, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(iconButton("📁") { folderPicker.launch(treeUri) }, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(iconButton("＋") { filePicker.launch(arrayOf("video/*", "*/*")) }, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(header)

        // Search Input
        searchInput = EditText(this).apply {
            hint = "Search videos & folders..."
            setHintTextColor(0xff777d8a.toInt())
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = UiUtils.rounded(0xff1c1f28.toInt(), 12, this@MainActivity)
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString().orEmpty().trim()
                    refreshCurrentDisplay()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        root.addView(searchInput, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })

        // Permission Banner
        permissionBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = UiUtils.rounded(0xff222633.toInt(), 14, this@MainActivity)
            visibility = View.GONE
        }
        val bannerText = TextView(this).apply {
            text = "Grant storage access to scan all local videos automatically"
            textSize = 13f
            setTextColor(0xffe2e4ea.toInt())
        }
        val grantBtn = TextView(this).apply {
            text = "Grant"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xff111317.toInt())
            background = UiUtils.rounded(0xffffc400.toInt(), 10, this@MainActivity)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener {
                val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_VIDEO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                permissionLauncher.launch(perm)
            }
        }
        permissionBanner.addView(bannerText, LinearLayout.LayoutParams(0, -2, 1f))
        permissionBanner.addView(grantBtn)
        root.addView(permissionBanner, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // Tab Bar (Folders & All Videos)
        val tabLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        tabFolders = tabItem("FOLDERS") { selectTab(TAB_FOLDERS) }
        tabAllVideos = tabItem("ALL VIDEOS") { selectTab(TAB_ALL_VIDEOS) }
        tabLayout.addView(tabFolders, LinearLayout.LayoutParams(0, dp(38), 1f))
        tabLayout.addView(tabAllVideos, LinearLayout.LayoutParams(0, dp(38), 1f))
        root.addView(tabLayout)

        // Breadcrumb Path Row
        pathRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = UiUtils.rounded(0xff181b24.toInt(), 12, this@MainActivity)
            visibility = View.GONE
        }
        backButton = TextView(this).apply {
            text = "‹"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xffffc400.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, dp(10), 0)
            setOnClickListener { handleBackNavigation() }
        }
        pathLabel = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xfff0f1f3.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        pathRow.addView(backButton)
        pathRow.addView(pathLabel, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(pathRow, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        // Result Count Header
        val listHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(10), dp(2), dp(6))
        }
        resultCount = TextView(this).apply {
            textSize = 12f
            setTextColor(0xff8c92a0.toInt())
        }
        listHeader.addView(resultCount, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(listHeader)

        // RecyclerView & Empty View
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        adapter = LibraryAdapter(
            onVideoClick = { video, list ->
                val idx = list.indexOf(video).coerceAtLeast(0)
                startPlayer(list, idx)
            },
            onFolderClick = { folder -> openDeviceFolder(folder) },
            onSafClick = { entry ->
                if (entry.isDirectory) openSafFolder(entry) else playSafEntry(entry)
            },
            resumePrefs = resumePrefs
        )
        recyclerView.adapter = adapter

        emptyView = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xff9ea4b2.toInt())
            setPadding(dp(32), dp(40), dp(32), dp(40))
        }

        val contentFrame = FrameLayout(this)
        contentFrame.addView(recyclerView, FrameLayout.LayoutParams(-1, -1))
        contentFrame.addView(emptyView, FrameLayout.LayoutParams(-1, -1))
        root.addView(contentFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        updateTabStyles()
    }

    private fun iconButton(symbol: String, onClick: () -> Unit) = TextView(this).apply {
        text = symbol
        textSize = 18f
        gravity = Gravity.CENTER
        setTextColor(0xffe6e8ee.toInt())
        setOnClickListener { onClick() }
    }

    private fun tabItem(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 12f
        letterSpacing = 0.08f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun updateTabStyles() {
        val activeColor = 0xffffc400.toInt()
        val inactiveColor = 0xff7d8392.toInt()
        val activeBg = UiUtils.rounded(0xff222530.toInt(), 10, this)

        tabFolders.setTextColor(if (currentTab == TAB_FOLDERS || currentTab == TAB_FOLDER_VIDEOS) activeColor else inactiveColor)
        tabFolders.background = if (currentTab == TAB_FOLDERS || currentTab == TAB_FOLDER_VIDEOS) activeBg else null

        tabAllVideos.setTextColor(if (currentTab == TAB_ALL_VIDEOS) activeColor else inactiveColor)
        tabAllVideos.background = if (currentTab == TAB_ALL_VIDEOS) activeBg else null
    }

    private fun selectTab(tab: Int) {
        currentTab = tab
        clearSearchQuery()
        pathRow.visibility = View.GONE
        updateTabStyles()
        refreshCurrentDisplay()
    }

    private fun loadDeviceVideos() {
        executor.execute {
            val videos = MediaScanner.scanDeviceVideos(contentResolver)
            val folders = MediaScanner.groupIntoFolders(videos)
            mainHandler.post {
                allDeviceVideos = videos
                deviceFolders = folders
                refreshCurrentDisplay()
            }
        }
    }

    private fun openDeviceFolder(folder: FolderItem) {
        currentTab = TAB_FOLDER_VIDEOS
        currentActiveFolderName = folder.name
        currentFolderVideos = folder.videos
        clearSearchQuery()
        pathRow.visibility = View.VISIBLE
        pathLabel.text = folder.name
        updateTabStyles()
        refreshCurrentDisplay()
    }

    private fun openSafTree(uri: Uri) {
        treeUri = uri
        safFolderStack.clear()
        clearSearchQuery()
        currentDocumentId = DocumentsContract.getTreeDocumentId(uri)
        currentSafFolderName = queryDisplayName(uri) ?: "Folder"
        currentTab = TAB_SAF
        loadCurrentSafFolder()
    }

    private fun loadCurrentSafFolder() {
        val tree = treeUri ?: return
        val docId = currentDocumentId ?: DocumentsContract.getTreeDocumentId(tree)
        runCatching {
            safEntries = MediaScanner.querySafFolder(contentResolver, tree, docId)
            pathRow.visibility = View.VISIBLE
            pathLabel.text = (safFolderStack.map { it.second } + currentSafFolderName).joinToString("  ›  ")
            refreshCurrentDisplay()
        }.onFailure { showAccessError(it) }
    }

    private fun openSafFolder(entry: SafEntry) {
        safFolderStack.add((currentDocumentId ?: return) to currentSafFolderName)
        currentDocumentId = entry.documentId
        currentSafFolderName = entry.name
        clearSearchQuery()
        loadCurrentSafFolder()
    }

    private fun playSafEntry(entry: SafEntry) {
        val tree = treeUri ?: return
        val videos = safEntries.filter { !it.isDirectory }
            .map {
                VideoItem(
                    name = it.name,
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, it.documentId),
                    sizeBytes = it.sizeBytes ?: 0L,
                    dateModified = it.modified ?: 0L
                )
            }
        val targetUri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)
        val idx = videos.indexOfFirst { it.uri == targetUri }.coerceAtLeast(0)
        startPlayer(videos, idx)
    }

    private fun handleBackNavigation() {
        when (currentTab) {
            TAB_FOLDER_VIDEOS -> selectTab(TAB_FOLDERS)
            TAB_SAF -> {
                if (safFolderStack.isNotEmpty()) {
                    val parent = safFolderStack.removeAt(safFolderStack.lastIndex)
                    currentDocumentId = parent.first
                    currentSafFolderName = parent.second
                    clearSearchQuery()
                    loadCurrentSafFolder()
                } else {
                    selectTab(TAB_FOLDERS)
                }
            }
        }
    }

    private fun getRecentVideos(): List<VideoItem> {
        return allDeviceVideos.filter {
            resumePrefs.getLong("pos_${it.uri}", 0L) > 3000L
        }.sortedByDescending { resumePrefs.getLong("pos_${it.uri}", 0L) }
    }

    private fun promptClearRecent() {
        AlertDialog.Builder(this)
            .setTitle("Clear Recent History")
            .setMessage("Do you want to clear your recently watched playback history?")
            .setPositiveButton("Clear") { _, _ ->
                val editor = resumePrefs.edit()
                for (key in resumePrefs.all.keys) {
                    if (key.startsWith("pos_")) {
                        editor.remove(key)
                    }
                }
                editor.apply()
                refreshCurrentDisplay()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshCurrentDisplay() {
        when (currentTab) {
            TAB_FOLDERS -> {
                val recentVideos = getRecentVideos()
                val filteredRecent = recentVideos.filter {
                    searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                }
                val filteredFolders = deviceFolders.filter {
                    searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                }

                val listItems = mutableListOf<LibraryListItem>()

                // 1. Recent Section inside Folder tab (scrolls vertically)
                if (filteredRecent.isNotEmpty()) {
                    val hasMore = filteredRecent.size > RECENT_PREVIEW_LIMIT
                    val showAll = isRecentExpanded || searchQuery.isNotBlank()

                    val primaryAction = if (hasMore) {
                        if (showAll) "Show Less" else "View All (${filteredRecent.size})"
                    } else {
                        "Clear"
                    }

                    val onPrimaryClick: (() -> Unit) = if (hasMore) {
                        {
                            isRecentExpanded = !isRecentExpanded
                            refreshCurrentDisplay()
                        }
                    } else {
                        { promptClearRecent() }
                    }

                    val secondaryAction = if (hasMore) "Clear" else null
                    val onSecondaryClick: (() -> Unit)? = if (hasMore) { { promptClearRecent() } } else null

                    listItems.add(
                        LibraryListItem.Header(
                            title = "RECENTLY PLAYED",
                            count = filteredRecent.size,
                            actionText = primaryAction,
                            onActionClick = onPrimaryClick,
                            secondaryActionText = secondaryAction,
                            onSecondaryActionClick = onSecondaryClick
                        )
                    )

                    val displayRecent = if (showAll) filteredRecent else filteredRecent.take(RECENT_PREVIEW_LIMIT)
                    displayRecent.forEach { video ->
                        listItems.add(LibraryListItem.Video(video, filteredRecent))
                    }
                }

                // 2. Folders Section (scrolls vertically right below Recent)
                if (filteredFolders.isNotEmpty()) {
                    if (filteredRecent.isNotEmpty()) {
                        listItems.add(
                            LibraryListItem.Header(
                                title = "FOLDERS",
                                count = filteredFolders.size
                            )
                        )
                    }
                    filteredFolders.forEach { folder ->
                        listItems.add(LibraryListItem.Folder(folder))
                    }
                }

                adapter.submitItems(listItems)

                val totalCountText = buildString {
                    if (filteredRecent.isNotEmpty()) append("${filteredRecent.size} recent · ")
                    append("${filteredFolders.size} folders")
                }
                resultCount.text = totalCountText

                val isEmpty = filteredRecent.isEmpty() && filteredFolders.isEmpty()
                emptyView.text = if (isEmpty) {
                    if (searchQuery.isNotBlank()) "No matching folders or videos found"
                    else "No video folders found\n\nGrant storage access or open a folder above."
                } else ""
                emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }

            TAB_ALL_VIDEOS -> {
                val filtered = allDeviceVideos.filter {
                    searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                }
                val sorted = sortVideos(filtered)
                adapter.submitVideos(sorted)
                resultCount.text = "${sorted.size} videos"
                emptyView.text = if (sorted.isEmpty()) "No videos found" else ""
                emptyView.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
            }

            TAB_FOLDER_VIDEOS -> {
                val filtered = currentFolderVideos.filter {
                    searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                }
                val sorted = sortVideos(filtered)
                adapter.submitVideos(sorted)
                resultCount.text = "${sorted.size} videos in $currentActiveFolderName"
                emptyView.text = if (sorted.isEmpty()) "Folder is empty" else ""
                emptyView.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
            }

            TAB_SAF -> {
                val filtered = safEntries.filter {
                    searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                }.sortedWith(compareBy<SafEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })

                adapter.submitSaf(filtered)
                resultCount.text = "${filtered.size} items"
                emptyView.text = if (filtered.isEmpty()) "This folder is empty" else ""
                emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun sortVideos(list: List<VideoItem>): List<VideoItem> {
        return when (sortMode) {
            1 -> list.sortedByDescending { it.dateModified }
            2 -> list.sortedByDescending { it.sizeBytes }
            3 -> list.sortedByDescending { it.durationMs }
            else -> list.sortedBy { it.name.lowercase() }
        }
    }

    private fun showSortMenu() {
        val options = arrayOf("Sort by Name (A-Z)", "Sort by Date (Newest)", "Sort by Size (Largest)", "Sort by Duration (Longest)")
        AlertDialog.Builder(this)
            .setTitle("Sort Videos")
            .setSingleChoiceItems(options, sortMode) { dialog, which ->
                sortMode = which
                refreshCurrentDisplay()
                dialog.dismiss()
            }
            .show()
    }

    private fun toggleSearch() {
        if (searchInput.visibility == View.VISIBLE) {
            searchInput.setText("")
            searchInput.visibility = View.GONE
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchInput.windowToken, 0)
        } else {
            searchInput.visibility = View.VISIBLE
            searchInput.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun clearSearchQuery() {
        if (searchQuery.isNotEmpty()) searchInput.setText("")
        searchQuery = ""
    }

    private fun startPlayer(videos: List<VideoItem>, index: Int) {
        if (videos.isEmpty()) return
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("uris", ArrayList(videos.map { it.uri.toString() }))
                .putExtra("names", ArrayList(videos.map { it.name }))
                .putExtra("index", index)
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            val col = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (cursor.moveToFirst() && col >= 0) cursor.getString(col) else null
        }
    }

    private fun showAccessError(error: Throwable) {
        AlertDialog.Builder(this)
            .setTitle("Cannot open folder")
            .setMessage(error.localizedMessage ?: "Folder access may have been removed.")
            .setPositiveButton("Choose Folder") { _, _ -> folderPicker.launch(treeUri) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
