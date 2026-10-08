/**
 * Role: ViewHolder implementations for media library list items.
 * Responsibility: Wraps the views for header, video, folder, and SAF entries in RecyclerView.
 * Details: Holds typed references to ensure lightweight and zero-overhead view binding.
 */
package com.example.mxoffline.library.adapter

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HeaderHolder(
    view: View,
    val title: TextView,
    val secondaryBtn: TextView,
    val actionBtn: TextView
) : RecyclerView.ViewHolder(view)

class VideoHolder(val views: MediaCardViews) : RecyclerView.ViewHolder(views.root)

class FolderHolder(val views: MediaCardViews) : RecyclerView.ViewHolder(views.root)

class SafHolder(val views: MediaCardViews) : RecyclerView.ViewHolder(views.root)
