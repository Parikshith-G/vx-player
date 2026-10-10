/**
 * Role: Main entry activity for the VX Player local media library.
 * Responsibility: Coordinates permissions, scans storage, and manages folder/video browsing tabs.
 * Details: Connects navigation, sorting, batch deletion, and playback launchers to modular UI views.
 */
package com.example.mxoffline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsControllerCompat
import com.example.mxoffline.library.MediaScanner
import com.example.mxoffline.library.actions.LibraryBatchActionManager
import com.example.mxoffline.library.adapter.LibraryAdapter
import com.example.mxoffline.library.display.LibraryDisplayCoordinator
import com.example.mxoffline.library.menu.LibraryMenuHelper
import com.example.mxoffline.library.nav.LibraryFolderNavigator
import com.example.mxoffline.library.nav.PlayerLauncher
import com.example.mxoffline.library.permission.LibraryPermissionHelper
import com.example.mxoffline.library.sort.LibrarySortManager
import com.example.mxoffline.library.ui.LibraryUiBuilder
import com.example.mxoffline.library.ui.LibraryUiViews
import com.example.mxoffline.model.FolderItem
import com.example.mxoffline.model.SafEntry
import com.example.mxoffline.model.VideoItem
import com.example.mxoffline.util.AppBackupManager
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), LibraryUiBuilder.Callback {

    private val prefs by lazy { getSharedPreferences("library", MODE_PRIVATE) }
    private val resumePrefs by lazy { getSharedPreferences("player_resume", MODE_PRIVATE) }
    private val seenPrefs by lazy { getSharedPreferences("player_seen", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var ui: LibraryUiViews
    private lateinit var adapter: LibraryAdapter
    private val navigator = LibraryFolderNavigator()
    private lateinit var actionManager: LibraryBatchActionManager
    private lateinit var displayCoordinator: LibraryDisplayCoordinator

    private var currentTab = TAB_FOLDERS
    private var allDeviceVideos = listOf<VideoItem>()
    private var deviceFolders = listOf<FolderItem>()
    private var currentFolderVideos = listOf<VideoItem>()
    private var currentActiveFolderName = ""
    private var safEntries = listOf<SafEntry>()
    private var searchQuery = ""
    private var sortMode = 0

    companion object {
        const val TAB_FOLDERS = 0
        const val TAB_ALL_VIDEOS = 1
        const val TAB_FOLDER_VIDEOS = 2
        const val TAB_SAF = 3
        const val TAB_SEEN = 4
    }

    private val batchDeleteLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest> = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == RESULT_OK) actionManager.onBatchDeleteSuccess(actionManager.pendingBatchDeleteVideos) else actionManager.onBatchDeleteCancelled()
    }

    private val permissionLauncher: androidx.activity.result.ActivityResultLauncher<String> = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            ui.permissionBanner.visibility = View.GONE
            executor.execute { AppBackupManager.autoRestoreIfAvailable(this@MainActivity); mainHandler.post { loadDeviceVideos() } }
        } else {
            Toast.makeText(this, "Storage permission is needed to scan local videos", Toast.LENGTH_LONG).show()
            updatePermissionBanner()
        }
    }

    private val backupExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri)?.use { AppBackupManager.exportToStream(this, it) }
            Toast.makeText(this, "Backup exported successfully", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(this, "Export failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show() }
    }

    private val backupImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val ok = contentResolver.openInputStream(uri)?.use { AppBackupManager.importFromStream(this, it) } ?: false
            if (ok) {
                sortMode = com.example.mxoffline.util.PreferenceHelper.safeGetInt(prefs, "sort_mode", 0)
                loadDeviceVideos()
                Toast.makeText(this, "Preferences & Seen history restored!", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "Failed to parse backup file", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(this, "Import failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show() }
    }

    private val folderPicker: androidx.activity.result.ActivityResultLauncher<Uri?> = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString("tree", uri.toString()).apply()
                openSafTree(uri)
            }.onFailure { LibraryMenuHelper.showAccessError(this, it) { folderPicker.launch(navigator.treeUri) } }
        }
    }

    private val filePicker: androidx.activity.result.ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && col >= 0) c.getString(col) else null
                } ?: "Video"
                PlayerLauncher.start(this, listOf(VideoItem(name = name, uri = uri)), 0)
            }.onFailure { LibraryMenuHelper.showAccessError(this, it) { folderPicker.launch(navigator.treeUri) } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.mxoffline.util.CrashProtection.install(this)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        sortMode = com.example.mxoffline.util.PreferenceHelper.safeGetInt(prefs, "sort_mode", 0)
        ui = LibraryUiBuilder.build(this, this)
        setContentView(ui.root)

        actionManager = LibraryBatchActionManager(this, resumePrefs, seenPrefs, executor, mainHandler, batchDeleteLauncher) { loadDeviceVideos() }
        displayCoordinator = LibraryDisplayCoordinator(resumePrefs, seenPrefs, actionManager)
        adapter = LibraryAdapter({ v, l -> PlayerLauncher.start(this, l, l.indexOf(v).coerceAtLeast(0)) }, { f -> openDeviceFolder(f) }, { e -> if (e.isDirectory) openSafFolder(e) else playSafEntry(e) }, resumePrefs, { v, l -> showVideoActionDialog(v, l) })
        ui.recyclerView.adapter = adapter

        setupBackNavigation()
        ui.updateTabStyles(currentTab, this)
        checkPermissionsAndLoad()
        AppBackupManager.autoRestoreAsync(this) { restored ->
            if (restored) mainHandler.post {
                sortMode = com.example.mxoffline.util.PreferenceHelper.safeGetInt(prefs, "sort_mode", 0)
                refreshCurrentDisplay()
            }
        }
    }

    override fun onPause() { super.onPause(); AppBackupManager.backupToStorageAsync(this) }
    override fun onResume() {
        super.onResume()
        com.example.mxoffline.util.CrashProtection.checkAndNotifyCrash(this)
        if (LibraryPermissionHelper.hasPermission(this)) {
            if (seenPrefs.all.isEmpty() && resumePrefs.all.isEmpty()) executor.execute { if (AppBackupManager.autoRestoreIfAvailable(this@MainActivity)) mainHandler.post { refreshCurrentDisplay() } }
            loadDeviceVideos()
        } else updatePermissionBanner()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (ui.pathRow.visibility == View.VISIBLE) onBackPressed()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
    }

    private fun checkPermissionsAndLoad() {
        if (LibraryPermissionHelper.hasPermission(this)) { ui.permissionBanner.visibility = View.GONE; loadDeviceVideos() }
        else { updatePermissionBanner(); permissionLauncher.launch(LibraryPermissionHelper.getRequiredPermission()) }
    }

    private fun updatePermissionBanner() {
        ui.permissionBanner.visibility = if (LibraryPermissionHelper.hasPermission(this)) View.GONE else View.VISIBLE
    }

    private fun loadDeviceVideos() {
        executor.execute {
            if (seenPrefs.all.isEmpty() && resumePrefs.all.isEmpty()) AppBackupManager.autoRestoreIfAvailable(this@MainActivity)
            val videos = MediaScanner.scanDeviceVideos(contentResolver)
            val folders = MediaScanner.groupIntoFolders(videos)
            mainHandler.post {
                allDeviceVideos = videos; deviceFolders = folders
                if (currentTab == TAB_FOLDER_VIDEOS) currentFolderVideos = folders.find { it.name == currentActiveFolderName }?.videos ?: emptyList()
                refreshCurrentDisplay()
            }
        }
    }

    private fun openDeviceFolder(folder: FolderItem) {
        currentTab = TAB_FOLDER_VIDEOS; currentActiveFolderName = folder.name; currentFolderVideos = folder.videos
        clearSearchQuery(); ui.pathRow.visibility = View.VISIBLE; ui.pathLabel.text = folder.name
        ui.updateTabStyles(currentTab, this); refreshCurrentDisplay()
    }

    private fun openSafTree(uri: Uri) {
        navigator.openSafTree(uri, contentResolver); clearSearchQuery(); currentTab = TAB_SAF; loadCurrentSafFolder()
    }

    private fun loadCurrentSafFolder() {
        val tree = navigator.treeUri ?: return
        val docId = navigator.currentDocumentId ?: return
        runCatching {
            safEntries = MediaScanner.querySafFolder(contentResolver, tree, docId)
            ui.pathRow.visibility = View.VISIBLE; ui.pathLabel.text = navigator.getBreadcrumbText(); refreshCurrentDisplay()
        }.onFailure { LibraryMenuHelper.showAccessError(this, it) { folderPicker.launch(navigator.treeUri) } }
    }

    private fun openSafFolder(entry: SafEntry) {
        navigator.openSafFolder(entry); clearSearchQuery(); loadCurrentSafFolder()
    }

    private fun playSafEntry(entry: SafEntry) {
        val sortedSaf = LibrarySortManager.sortSafEntries(safEntries, sortMode)
        val videos = navigator.buildSafVideoList(sortedSaf)
        val tree = navigator.treeUri ?: return
        val target = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)
        PlayerLauncher.start(this, videos, videos.indexOfFirst { it.uri == target }.coerceAtLeast(0))
    }

    override fun onBackPressed() {
        when (currentTab) {
            TAB_FOLDER_VIDEOS -> onTabSelected(TAB_FOLDERS)
            TAB_SAF -> if (navigator.popSafFolder()) { clearSearchQuery(); loadCurrentSafFolder() } else onTabSelected(TAB_FOLDERS)
            TAB_SEEN -> onTabSelected(TAB_FOLDERS)
        }
    }

    override fun onSearchClicked() {
        if (ui.searchInput.visibility == View.VISIBLE) {
            ui.searchInput.setText(""); ui.searchInput.visibility = View.GONE
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(ui.searchInput.windowToken, 0)
        } else {
            ui.searchInput.visibility = View.VISIBLE; ui.searchInput.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(ui.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onSortClicked() {
        LibrarySortManager.showSortDialog(this, sortMode) { m -> sortMode = m; prefs.edit().putInt("sort_mode", m).apply(); refreshCurrentDisplay() }
    }

    override fun onFolderPickerClicked() = folderPicker.launch(navigator.treeUri)
    override fun onFilePickerClicked() = filePicker.launch(arrayOf("video/*", "*/*"))
    override fun onGrantPermissionClicked() = permissionLauncher.launch(LibraryPermissionHelper.getRequiredPermission())

    override fun onTabSelected(tab: Int) {
        currentTab = tab; clearSearchQuery(); ui.pathRow.visibility = View.GONE; ui.updateTabStyles(currentTab, this); refreshCurrentDisplay()
    }

    override fun onSearchQueryChanged(q: String) { searchQuery = q; refreshCurrentDisplay() }
    private fun clearSearchQuery() { if (searchQuery.isNotEmpty()) ui.searchInput.setText(""); searchQuery = "" }

    private fun refreshCurrentDisplay() {
        val result = when (currentTab) {
            TAB_FOLDERS -> displayCoordinator.buildFoldersTab(allDeviceVideos, deviceFolders, searchQuery, sortMode)
            TAB_ALL_VIDEOS -> displayCoordinator.buildAllVideosTab(allDeviceVideos, searchQuery, sortMode)
            TAB_FOLDER_VIDEOS -> displayCoordinator.buildFolderVideosTab(currentFolderVideos, currentActiveFolderName, searchQuery, sortMode)
            TAB_SAF -> displayCoordinator.buildSafTab(safEntries, searchQuery, sortMode)
            TAB_SEEN -> displayCoordinator.buildSeenTab(allDeviceVideos, searchQuery, sortMode)
            else -> return
        }
        adapter.submitItems(result.items)
        ui.resultCount.text = result.resultText
        ui.emptyView.text = result.emptyText
        ui.emptyView.visibility = if (result.emptyText.isNotBlank()) View.VISIBLE else View.GONE
    }

    override fun onMoreMenuClicked() {
        LibraryMenuHelper.showMoreMenu(
            this,
            onExport = { backupExportLauncher.launch(AppBackupManager.BACKUP_FILENAME) },
            onImport = { backupImportLauncher.launch(arrayOf("application/json", "*/*")) },
            onRestoreSuccess = { sortMode = com.example.mxoffline.util.PreferenceHelper.safeGetInt(prefs, "sort_mode", 0); loadDeviceVideos() }
        )
    }

    private fun showVideoActionDialog(video: VideoItem, playlist: List<VideoItem>) {
        val isSeen = displayCoordinator.isVideoSeen(video)
        val options = arrayOf("Play", if (isSeen) "Mark as Unseen" else "Mark as Seen", "Delete Video")
        android.app.AlertDialog.Builder(this).setTitle(video.name).setItems(options) { _, which ->
            when (which) {
                0 -> PlayerLauncher.start(this, playlist, playlist.indexOf(video).coerceAtLeast(0))
                1 -> toggleSeenStatus(video, isSeen)
                2 -> actionManager.promptDeleteSingleVideo(video)
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun toggleSeenStatus(video: VideoItem, currentlySeen: Boolean) {
        val editor = seenPrefs.edit(); val keys = com.example.mxoffline.util.VideoIdentity.getAllKeys("seen", video)
        if (currentlySeen) keys.forEach { editor.remove(it) } else { val now = System.currentTimeMillis(); keys.forEach { editor.putLong(it, now) } }
        editor.apply(); AppBackupManager.backupToStorageAsync(this); refreshCurrentDisplay()
        Toast.makeText(this, if (currentlySeen) "Marked as Unseen" else "Marked as Seen", Toast.LENGTH_SHORT).show()
    }
}
