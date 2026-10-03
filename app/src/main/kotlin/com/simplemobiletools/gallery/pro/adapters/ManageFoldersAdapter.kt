package com.simplemobiletools.gallery.pro.adapters

import android.view.*
import android.widget.PopupMenu
import androidx.appcompat.view.ContextThemeWrapper
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.getPopupMenuTheme
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.setupViewBackground
import com.simplemobiletools.commons.interfaces.RefreshRecyclerViewListener
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.gallery.pro.R
import com.simplemobiletools.gallery.pro.databinding.ItemManageFolderBinding
import com.simplemobiletools.gallery.pro.dialogs.EditFolderDialog
import com.simplemobiletools.gallery.pro.extensions.config

class ManageFoldersAdapter(
    activity: BaseSimpleActivity, var folders: ArrayList<String>, val isShowingExcludedFolders: Boolean, val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private val config = activity.config

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_manage_folders

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        when (id) {
            com.simplemobiletools.commons.R.id.cab_remove -> removeSelection()
            R.id.cab_edit_folder -> editSelection()
        }
    }

    override fun getSelectableItemCount() = folders.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = folders.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = folders.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemManageFolderBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        holder.bindView(folder, true, true) { itemView, adapterPosition ->
            setupView(itemView, folder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = folders.size

    private fun setupView(view: View, folder: String) {
        ItemManageFolderBinding.bind(view).apply {
            root.setupViewBackground(activity)
            manageFolderHolder.isSelected = selectedKeys.contains(folder.hashCode())
            manageFolderTitle.apply {
                text = folder
                setTextColor(context.getProperTextColor())
            }

            overflowMenuIcon.drawable.apply {
                mutate()
                setTint(activity.getProperTextColor())
            }

            overflowMenuIcon.setOnClickListener {
                showPopupMenu(overflowMenuAnchor, folder)
            }
        }
    }

    private fun showPopupMenu(view: View, folder: String) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(getActionMenuId())
            setOnMenuItemClickListener { item ->
                val eventTypeId = folder.hashCode()
                when (item.itemId) {
                    com.simplemobiletools.commons.R.id.cab_remove -> {
                        executeItemMenuOperation(eventTypeId) {
                            removeSelection()
                        }
                    }
                    R.id.cab_edit_folder -> {
                        executeItemMenuOperation(eventTypeId) {
                            editSelection()
                        }
                    }
                }
                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(eventTypeId: Int, callback: () -> Unit) {
        selectedKeys.clear()
        selectedKeys.add(eventTypeId)
        callback()
    }

    private fun editSelection() {
        val selectedPaths = getSelectedItems()
        if (selectedPaths.size != 1) return
        val oldPath = selectedPaths.firstOrNull() ?: return

        EditFolderDialog(activity, oldPath) { newPath ->
            val index = folders.indexOf(oldPath)
            if (index >= 0) folders[index] = newPath

            if (isShowingExcludedFolders) {
                config.removeExcludedFolder(oldPath)
                config.addExcludedFolder(newPath)
            } else {
                config.removeIncludedFolder(oldPath)
                config.addIncludedFolder(newPath)
            }
            listener?.refreshItems()
        }
    }

    private fun removeSelection() {
        val removeFolders = ArrayList<String>(selectedKeys.size)
        val positions = getSelectedItemPositions()

        getSelectedItems().forEach {
            removeFolders.add(it)
            if (isShowingExcludedFolders) {
                config.removeExcludedFolder(it)
            } else {
                config.removeIncludedFolder(it)
            }
        }

        folders.removeAll(removeFolders)
        removeSelectedItems(positions)
        if (folders.isEmpty()) {
            listener?.refreshItems()
        }
    }

    private fun getSelectedItems(): ArrayList<String> {
        val selectedItems = ArrayList<String>()
        selectedKeys.forEach { key ->
            folders.firstOrNull { it.hashCode() == key }?.let {
                selectedItems.add(it)
            }
        }
        return selectedItems
    }
}
