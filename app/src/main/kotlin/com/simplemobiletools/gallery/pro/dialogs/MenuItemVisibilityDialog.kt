package com.simplemobiletools.gallery.pro.dialogs

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.gallery.pro.R
import com.simplemobiletools.gallery.pro.databinding.DialogMenuItemCheckboxBinding
import com.simplemobiletools.gallery.pro.databinding.DialogMenuItemVisibilityBinding

// shows one checkbox per menu item; hidden items are stored in the given config string-set
// under a per-menu pref key. The caller applies visibility on its own refreshMenuItems() runs.
class MenuItemVisibilityDialog(
    private val activity: Activity,
    private val items: List<Item>,
    private val hiddenKeys: Set<String>,
    private val onApply: (hiddenKeys: Set<String>) -> Unit
) {
    class Item(val key: String, val titleRes: Int)

    private val checkedItems = HashMap<String, Boolean>()

    init {
        val binding = DialogMenuItemVisibilityBinding.inflate(LayoutInflater.from(activity))

        items.forEach { checkedItems[it.key] = !hiddenKeys.contains(it.key) }

        binding.menuItemVisibilityList.adapter = ItemsAdapter()
        binding.menuItemVisibilityList.layoutManager = LinearLayoutManager(activity)
        binding.menuItemVisibilityEmpty.beVisibleIf(items.isEmpty())

        val dialogBuilder = AlertDialog.Builder(activity)
            .setTitle(R.string.shown_menu_items)
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ ->
                val hidden = items.filter { checkedItems[it.key] == false }.map { it.key }.toSet()
                onApply(hidden)
            }
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)

        activity.setupDialogStuff(binding.root, dialogBuilder)
    }

    private inner class ItemsAdapter : RecyclerView.Adapter<ItemsAdapter.ItemHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            val itemBinding = DialogMenuItemCheckboxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ItemHolder(itemBinding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            val item = items[position]
            holder.binding.menuItemCheckbox.apply {
                text = activity.getString(item.titleRes)
                isChecked = checkedItems[item.key] == true
                setOnClickListener {
                    checkedItems[item.key] = isChecked
                }
            }
        }

        inner class ItemHolder(val binding: DialogMenuItemCheckboxBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
