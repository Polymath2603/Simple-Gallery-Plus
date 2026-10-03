package com.simplemobiletools.gallery.pro.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.getDoesFilePathExist
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.getIsPathDirectory
import com.simplemobiletools.commons.extensions.hideKeyboard
import com.simplemobiletools.commons.extensions.isAStorageRootFolder
import com.simplemobiletools.commons.extensions.isAValidFilename
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.showKeyboard
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.value
import com.simplemobiletools.gallery.pro.R
import com.simplemobiletools.gallery.pro.databinding.DialogEditFolderBinding

// Simple themed dialog for editing an included/excluded folder path.
// The callback receives the new path after validation.
class EditFolderDialog(
    val activity: BaseSimpleActivity,
    val oldPath: String,
    val callback: (newPath: String) -> Unit
) {
    init {
        val binding = DialogEditFolderBinding.inflate(activity.layoutInflater).apply {
            editFolderPathValue.setText(oldPath)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.edit_folder) { alertDialog ->
                    alertDialog.showKeyboard(binding.editFolderPathValue)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newPath = binding.editFolderPathValue.value.trim()
                        when {
                            newPath.isEmpty() -> return@setOnClickListener
                            newPath == oldPath -> alertDialog.dismiss()
                            activity.isAStorageRootFolder(newPath) ->
                                activity.toast(com.simplemobiletools.commons.R.string.rename_folder_root)
                            !newPath.getFilenameFromPath().isAValidFilename() ->
                                activity.toast(com.simplemobiletools.commons.R.string.filename_invalid_characters)
                            activity.getDoesFilePathExist(newPath) && !activity.getIsPathDirectory(newPath) ->
                                // existing path that is a file, not a folder
                                activity.toast(R.string.not_a_folder)
                            !activity.getDoesFilePathExist(newPath) -> {
                                // nonexistent path: warn, but let the user save it anyway
                                activity.hideKeyboard(binding.editFolderPathValue)
                                ConfirmationDialog(activity, activity.getString(R.string.save_path_anyway)) {
                                    callback(newPath)
                                    alertDialog.dismiss()
                                }
                            }
                            else -> {
                                activity.hideKeyboard(binding.editFolderPathValue)
                                callback(newPath)
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
            }
    }
}
