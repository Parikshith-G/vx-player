/**
 * Role: Hierarchical folder navigation and SAF directory controller.
 * Responsibility: Tracks document identifiers, breadcrumb path stacks, and tree navigation.
 * Details: Converts SAF document entries to playable VideoItems and formats breadcrumb display text.
 */
package com.example.mxoffline.library.nav

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.example.mxoffline.model.SafEntry
import com.example.mxoffline.model.VideoItem

class LibraryFolderNavigator {

    var treeUri: Uri? = null
    var currentDocumentId: String? = null
    var currentSafFolderName: String = ""
    val safFolderStack = mutableListOf<Pair<String, String>>()

    fun openSafTree(uri: Uri, contentResolver: ContentResolver) {
        treeUri = uri
        safFolderStack.clear()
        currentDocumentId = DocumentsContract.getTreeDocumentId(uri)
        currentSafFolderName = queryDisplayName(contentResolver, uri) ?: "Folder"
    }

    fun openSafFolder(entry: SafEntry) {
        val docId = currentDocumentId ?: return
        safFolderStack.add(docId to currentSafFolderName)
        currentDocumentId = entry.documentId
        currentSafFolderName = entry.name
    }

    fun popSafFolder(): Boolean {
        if (safFolderStack.isNotEmpty()) {
            val parent = safFolderStack.removeAt(safFolderStack.lastIndex)
            currentDocumentId = parent.first
            currentSafFolderName = parent.second
            return true
        }
        return false
    }

    fun getBreadcrumbText(): String {
        return (safFolderStack.map { it.second } + currentSafFolderName).joinToString("  ›  ")
    }

    fun buildSafVideoList(entries: List<SafEntry>): List<VideoItem> {
        val tree = treeUri ?: return emptyList()
        return entries.filter { !it.isDirectory }
            .map {
                VideoItem(
                    name = it.name,
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, it.documentId),
                    sizeBytes = it.sizeBytes ?: 0L,
                    dateModified = it.modified ?: 0L
                )
            }
    }

    fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        return contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            val col = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (cursor.moveToFirst() && col >= 0) cursor.getString(col) else null
        }
    }
}
