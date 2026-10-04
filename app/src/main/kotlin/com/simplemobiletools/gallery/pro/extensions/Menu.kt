package com.simplemobiletools.gallery.pro.extensions

import android.content.Context
import android.view.Menu

// hides menu items whose XML entry name (e.g. "sort", "cab_rename") is in [hiddenNames];
// used by the settings menu-customization feature. Submenus are checked too.
fun Menu.applyHiddenEntryNames(context: Context, hiddenNames: Set<String>) {
    if (hiddenNames.isEmpty()) {
        return
    }
    for (index in 0 until size()) {
        val item = getItem(index)
        try {
            val name = context.resources.getResourceEntryName(item.itemId)
            if (hiddenNames.contains(name)) {
                item.isVisible = false
            }
        } catch (ignored: Exception) {
        }
        if (item.hasSubMenu()) {
            item.subMenu?.applyHiddenEntryNames(context, hiddenNames)
        }
    }
}

// re-shows items hidden by an earlier applyHiddenEntryNames call; screens call this before
// their regular visibility logic so unhiding an item in settings takes effect right away
fun Menu.showEntryNames(context: Context, names: Set<String>) {
    if (names.isEmpty()) {
        return
    }
    for (index in 0 until size()) {
        val item = getItem(index)
        try {
            val name = context.resources.getResourceEntryName(item.itemId)
            if (names.contains(name)) {
                item.isVisible = true
            }
        } catch (ignored: Exception) {
        }
        if (item.hasSubMenu()) {
            item.subMenu?.showEntryNames(context, names)
        }
    }
}
