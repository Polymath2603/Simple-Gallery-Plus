package com.simplemobiletools.gallery.pro.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.views.MySquareImageView
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.gallery.pro.R
import com.simplemobiletools.gallery.pro.extensions.loadImage
import com.simplemobiletools.gallery.pro.models.Directory

class TreeAdapter(
    activity: BaseSimpleActivity,
    var treeItems: ArrayList<TreeItem>,
    recyclerView: MyRecyclerView,
    private val onFolderClick: (String) -> Unit,
    private val onExpandToggle: (Int) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, {}) {

    data class TreeItem(
        val directory: Directory,
        val depth: Int,
        var isExpanded: Boolean = false,
        val hasMedia: Boolean,
        val childCount: Int
    )

    override fun getActionMenuId() = 0
    override fun prepareActionMode(menu: android.view.Menu) {}
    override fun actionItemPressed(id: Int) {}
    override fun getSelectableItemCount() = 0
    override fun getIsItemSelectable(position: Int) = false
    override fun getItemSelectionKey(position: Int) = null
    override fun getItemKeyPosition(key: Int) = -1
    override fun onActionModeCreated() {}
    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tree_folder, parent, false)
        return createViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = treeItems[position]
        val dir = item.directory
        holder.bindView(item, true, true) { itemView, _ ->
            val thumbnail = itemView.findViewById<MySquareImageView>(R.id.tree_thumbnail)
            val name = itemView.findViewById<com.simplemobiletools.commons.views.MyTextView>(R.id.tree_name)
            val count = itemView.findViewById<com.simplemobiletools.commons.views.MyTextView>(R.id.tree_media_count)
            val arrow = itemView.findViewById<ImageView>(R.id.tree_expand_arrow)
            val indent = itemView.findViewById<View>(R.id.indent_view)

            val indentPx = (item.depth * 24 * itemView.context.resources.displayMetrics.density).toInt()
            indent.layoutParams.width = indentPx

            val thumbTmb = dir.tmb
            val thumbnailType = when {
                thumbTmb.isVideoFast() -> com.simplemobiletools.gallery.pro.helpers.TYPE_VIDEOS
                thumbTmb.isGif() -> com.simplemobiletools.gallery.pro.helpers.TYPE_GIFS
                thumbTmb.isRawFast() -> com.simplemobiletools.gallery.pro.helpers.TYPE_RAWS
                thumbTmb.isSvg() -> com.simplemobiletools.gallery.pro.helpers.TYPE_SVGS
                else -> com.simplemobiletools.gallery.pro.helpers.TYPE_IMAGES
            }
            activity.loadImage(
                thumbnailType, thumbTmb, thumbnail, false, false, true, 20, com.bumptech.glide.signature.ObjectKey(dir.path)
            )

            name.text = dir.name
            count.text = "${dir.mediaCnt}"
            count.beVisibleIf(dir.mediaCnt > 0)

            if (item.childCount > 0) {
                arrow.beVisible()
                arrow.rotation = if (item.isExpanded) 180f else 0f
                arrow.setOnClickListener {
                    onExpandToggle(holder.adapterPosition)
                }
            } else {
                arrow.beGone()
            }

            if (item.hasMedia) {
                itemView.setOnClickListener { onFolderClick(dir.path) }
            } else {
                itemView.setOnClickListener { onExpandToggle(holder.adapterPosition) }
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = treeItems.size

    fun updateItems(newItems: ArrayList<TreeItem>) {
        treeItems = newItems
        notifyDataSetChanged()
    }
}
