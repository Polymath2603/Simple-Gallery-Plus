package com.simplemobiletools.gallery.pro.adapters

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.allViews
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.PropertiesDialog
import com.simplemobiletools.commons.dialogs.RenameDialog
import com.simplemobiletools.commons.dialogs.RenameItemDialog
import com.simplemobiletools.commons.dialogs.RenameItemsDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.gallery.pro.R
import com.simplemobiletools.gallery.pro.activities.ViewPagerActivity
import com.simplemobiletools.gallery.pro.activities.MediaActivity
import com.simplemobiletools.gallery.pro.databinding.*
import com.simplemobiletools.gallery.pro.dialogs.DeleteWithRememberDialog
import com.simplemobiletools.gallery.pro.extensions.*
import com.simplemobiletools.gallery.pro.helpers.*
import com.simplemobiletools.gallery.pro.interfaces.MediaOperationsListener
import com.simplemobiletools.gallery.pro.models.Directory
import com.simplemobiletools.gallery.pro.models.Medium
import com.simplemobiletools.gallery.pro.models.SubfolderItem
import com.simplemobiletools.gallery.pro.models.ThumbnailItem
import com.simplemobiletools.gallery.pro.models.ThumbnailSection
import java.io.File

class MediaAdapter(
    activity: BaseSimpleActivity, var media: ArrayList<ThumbnailItem>, val listener: MediaOperationsListener?, val isAGetIntent: Boolean,
    val allowMultiplePicks: Boolean, val path: String, recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) :
    MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private val INSTANT_LOAD_DURATION = 2000L
    private val IMAGE_LOAD_DELAY = 100L
    private val ITEM_SECTION = 0
    private val ITEM_MEDIUM_VIDEO_PORTRAIT = 1
    private val ITEM_MEDIUM_PHOTO = 2
    private val ITEM_SUBFOLDER = 3

    private val config = activity.config
    private val viewType = config.getFolderViewType(if (config.showAll) SHOW_ALL else path)
    private val isListViewType = viewType == VIEW_TYPE_LIST
    private var visibleItemPaths = ArrayList<String>()
    private var rotatedImagePaths = ArrayList<String>()
    private var loadImageInstantly = false
    private var delayHandler = Handler(Looper.getMainLooper())
    private var currentMediaHash = media.hashCode()
    private val hasOTGConnected = activity.hasOTGConnected()

    private var scrollHorizontally = config.scrollHorizontally
    private var animateGifs = config.animateGifs
    private var cropThumbnails = config.cropThumbnails
    private var displayFilenames = config.displayFileNames
    private var showFileTypes = config.showThumbnailFileTypes

    // tree-mode subfolder tiles are rendered with the exact same folder tile state as on the main screen
    private var pinnedFolders = config.pinnedFolders
    private var folderStyle = config.folderStyle
    private var groupDirectSubfolders = config.groupDirectSubfolders
    private var limitFolderTitle = config.limitFolderTitle
    private var showMediaCount = config.showFolderMediaCount

    var sorting = config.getFolderSorting(if (config.showAll) SHOW_ALL else path)
    var dateFormat = config.dateFormat
    var timeFormat = activity.getTimeFormat()

    init {
        setupDragListener(true)
        enableInstantLoad()
    }

    override fun getActionMenuId() = R.menu.cab_media

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = if (viewType == ITEM_SECTION) {
            ThumbnailSectionBinding.inflate(layoutInflater, parent, false)
        } else if (viewType == ITEM_SUBFOLDER) {
            if (isListViewType) {
                DirectoryItemListBinding.inflate(layoutInflater, parent, false)
            } else if (folderStyle == FOLDER_STYLE_SQUARE) {
                DirectoryItemGridSquareBinding.inflate(layoutInflater, parent, false)
            } else {
                DirectoryItemGridRoundedCornersBinding.inflate(layoutInflater, parent, false)
            }
        } else {
            if (isListViewType) {
                if (viewType == ITEM_MEDIUM_PHOTO) {
                    PhotoItemListBinding.inflate(layoutInflater, parent, false)
                } else {
                    VideoItemListBinding.inflate(layoutInflater, parent, false)
                }
            } else {
                if (viewType == ITEM_MEDIUM_PHOTO) {
                    PhotoItemGridBinding.inflate(layoutInflater, parent, false)
                } else {
                    VideoItemGridBinding.inflate(layoutInflater, parent, false)
                }
            }
        }
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        val tmbItem = media.getOrNull(position) ?: return
        if (tmbItem is Medium) {
            visibleItemPaths.add(tmbItem.path)
        }

        // subfolder tiles click and long-press exactly like folder tiles on the main screen:
        // tap opens the folder, long press starts selection
        val isFolderOrMediaItem = tmbItem is Medium || tmbItem is SubfolderItem
        val allowLongPress = (!isAGetIntent || allowMultiplePicks) && isFolderOrMediaItem
        holder.bindView(tmbItem, isFolderOrMediaItem, allowLongPress) { itemView, adapterPosition ->
            when (tmbItem) {
                is Medium -> setupThumbnail(itemView, tmbItem)
                is SubfolderItem -> setupSubfolder(itemView, tmbItem)
                else -> setupSection(itemView, tmbItem as ThumbnailSection)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = media.size

    override fun getItemViewType(position: Int): Int {
        val tmbItem = media[position]
        return when {
            tmbItem is SubfolderItem -> ITEM_SUBFOLDER
            tmbItem is ThumbnailSection -> ITEM_SECTION
            (tmbItem as Medium).isVideo() || tmbItem.isPortrait() -> ITEM_MEDIUM_VIDEO_PORTRAIT
            else -> ITEM_MEDIUM_PHOTO
        }
    }

    override fun prepareActionMode(menu: Menu) {
        val selectedMedia = getSelectedItems()
        val selectedFolders = getSelectedFolderPaths()
        if (selectedMedia.isEmpty() && selectedFolders.isEmpty()) {
            return
        }

        val hasMedia = selectedMedia.isNotEmpty()
        val hasFolders = selectedFolders.isNotEmpty()
        val isOneMediaSelected = selectedMedia.size == 1
        val isInRecycleBin = selectedMedia.firstOrNull()?.getIsInRecycleBin() == true

        menu.apply {
            // media-only operations apply to the selected files only
            findItem(R.id.cab_open_with).isVisible = isOneMediaSelected
            findItem(R.id.cab_edit).isVisible = isOneMediaSelected
            findItem(R.id.cab_set_as).isVisible = isOneMediaSelected
            findItem(R.id.cab_resize).isVisible = hasMedia && canResize(selectedMedia)
            findItem(R.id.cab_rotate).isVisible = hasMedia
            findItem(R.id.cab_share).isVisible = hasMedia
            findItem(R.id.cab_fix_date_taken).isVisible = hasMedia && !isInRecycleBin
            findItem(R.id.cab_restore_recycle_bin_files).isVisible = hasMedia && selectedMedia.all { it.path.startsWith(activity.recycleBinPath) }

            // operations that folders support apply to the selected folders only, in mixed
            // selections each operation touches only the part of the selection it understands
            findItem(R.id.cab_rename).isVisible = (hasMedia && !isInRecycleBin) || hasFolders
            findItem(R.id.cab_move_to).isVisible = (hasMedia && !isInRecycleBin) || hasFolders
            findItem(R.id.cab_copy_to).isVisible = true
            findItem(R.id.cab_delete).isVisible = true
            findItem(R.id.cab_properties).isVisible = true
            findItem(R.id.cab_create_shortcut).isVisible = isOreoPlus() && isOneItemSelected()
            findItem(R.id.cab_select_all).isVisible = true
            findItem(R.id.cab_confirm_selection).isVisible = isAGetIntent && allowMultiplePicks && hasMedia

            checkHideBtnVisibility(this, selectedMedia, selectedFolders)
            checkFavoriteBtnVisibility(this, selectedMedia)
        }

        menu.applyHiddenEntryNames(activity, config.getHiddenSelectedImagesMenuItems())
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_confirm_selection -> confirmSelection()
            R.id.cab_properties -> showProperties()
            R.id.cab_rename -> checkMediaManagementAndRename()
            R.id.cab_edit -> editFile()
            R.id.cab_hide -> toggleSelectionVisibility(true)
            R.id.cab_unhide -> toggleSelectionVisibility(false)
            R.id.cab_add_to_favorites -> toggleFavorites(true)
            R.id.cab_remove_from_favorites -> toggleFavorites(false)
            R.id.cab_restore_recycle_bin_files -> restoreFiles()
            R.id.cab_share -> shareMedia()
            R.id.cab_rotate_right -> rotateSelection(90)
            R.id.cab_rotate_left -> rotateSelection(270)
            R.id.cab_rotate_one_eighty -> rotateSelection(180)
            R.id.cab_copy_to -> checkMediaManagementAndCopy(true)
            R.id.cab_move_to -> moveFilesTo()
            R.id.cab_create_shortcut -> createShortcut()
            R.id.cab_select_all -> selectAll()
            R.id.cab_open_with -> openPath()
            R.id.cab_fix_date_taken -> fixDateTaken()
            R.id.cab_set_as -> setAs()
            R.id.cab_resize -> resize()
            R.id.cab_delete -> checkDeleteConfirmation()
        }
    }

    override fun getSelectableItemCount() = media.count { it is Medium || it is SubfolderItem }

    override fun getIsItemSelectable(position: Int) = !isASectionTitle(position)

    override fun getItemSelectionKey(position: Int) = when (val item = media.getOrNull(position)) {
        is Medium -> item.path.hashCode()
        is SubfolderItem -> item.directory.path.hashCode()
        else -> null
    }

    override fun getItemKeyPosition(key: Int) = media.indexOfFirst {
        (it is Medium && it.path.hashCode() == key) || (it is SubfolderItem && it.directory.path.hashCode() == key)
    }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed) {
            val itemView = holder.itemView
            visibleItemPaths.remove(itemView.allViews.firstOrNull { it.id == R.id.medium_name }?.tag)
            val tmb = itemView.allViews.firstOrNull { it.id == R.id.medium_thumbnail }
            if (tmb != null) {
                Glide.with(activity).clear(tmb)
            }
        }
    }

    fun isASectionTitle(position: Int) = media.getOrNull(position) is ThumbnailSection

    // only section titles take the full row width in grid mode; subfolder tiles are regular grid cells, same as folders on the main screen
    fun isFullSpanItem(position: Int) = isASectionTitle(position)

    private fun checkHideBtnVisibility(menu: Menu, selectedItems: ArrayList<Medium>, selectedFolders: ArrayList<String>) {
        if (isRPlus() && !isExternalStorageManager()) {
            menu.findItem(R.id.cab_hide).isVisible = false
            menu.findItem(R.id.cab_unhide).isVisible = false
            return
        }

        val isInRecycleBin = selectedItems.firstOrNull()?.getIsInRecycleBin() == true
        val canTouchMedia = selectedItems.isNotEmpty() && !isInRecycleBin
        menu.findItem(R.id.cab_hide).isVisible =
            (canTouchMedia && selectedItems.any { !it.isHidden() }) ||
                selectedFolders.any { !it.doesThisOrParentHaveNoMedia(HashMap(), null) }

        menu.findItem(R.id.cab_unhide).isVisible =
            (canTouchMedia && selectedItems.any { it.isHidden() }) ||
                selectedFolders.any { it.doesThisOrParentHaveNoMedia(HashMap(), null) }
    }

    private fun checkFavoriteBtnVisibility(menu: Menu, selectedItems: ArrayList<Medium>) {
        val visible = selectedItems.isNotEmpty() && selectedItems.none { it.getIsInRecycleBin() }
        menu.findItem(R.id.cab_add_to_favorites).isVisible = visible && selectedItems.any { !it.isFavorite }
        menu.findItem(R.id.cab_remove_from_favorites).isVisible = visible && selectedItems.any { it.isFavorite }
    }

    private fun confirmSelection() {
        listener?.selectedPaths(getSelectedPaths())
    }

    private fun showProperties() {
        val paths = ArrayList<String>()
        paths.addAll(getSelectedPaths())
        paths.addAll(getSelectedFolderPaths())
        if (paths.isEmpty()) {
            return
        }

        if (paths.size == 1) {
            PropertiesDialog(activity, paths.first(), config.shouldShowHidden)
        } else {
            PropertiesDialog(activity, paths, config.shouldShowHidden)
        }
    }

    private fun checkMediaManagementAndRename() {
        activity.handleMediaManagementPrompt {
            renameSelection()
        }
    }

    // folders and files are renamed with their own dialogs; in a mixed selection both are applied
    private fun renameSelection() {
        val folderPaths = getSelectedFolderPaths().filter { !config.isFolderProtected(it) } as ArrayList<String>
        if (folderPaths.isNotEmpty()) {
            renameFolders(folderPaths) {
                renameMedia()
            }
        } else {
            renameMedia()
        }
    }

    private fun renameFolders(paths: ArrayList<String>, onDone: () -> Unit) {
        if (paths.size == 1) {
            val sourcePath = paths.first()
            val folder = File(sourcePath)
            if (activity.isAStorageRootFolder(folder.absolutePath)) {
                activity.toast(com.simplemobiletools.commons.R.string.rename_folder_root)
                onDone()
                return
            }

            val tmb = media.filterIsInstance<SubfolderItem>().firstOrNull { it.directory.path == sourcePath }?.directory?.tmb ?: ""
            activity.handleLockedFolderOpening(sourcePath) { success ->
                if (success) {
                    RenameItemDialog(activity, folder.absolutePath) { newPath ->
                        ensureBackgroundThread {
                            try {
                                val newTmb = File(newPath, tmb.getFilenameFromPath()).absolutePath
                                activity.directoryDB.updateDirectoryAfterRename(newTmb, newPath.getFilenameFromPath(), newPath, sourcePath)
                            } catch (e: Exception) {
                                activity.showErrorToast(e)
                            }
                            activity.runOnUiThread {
                                onDone()
                            }
                        }
                    }
                }
            }
        } else {
            RenameItemsDialog(activity, paths) {
                onDone()
            }
        }
    }

    private fun renameMedia() {
        val mediaPaths = getSelectedPaths()
        if (mediaPaths.isEmpty()) {
            finishActMode()
            return
        }

        val firstPath = mediaPaths.first()
        val isSDOrOtgRootFolder = activity.isAStorageRootFolder(firstPath.getParentPath()) && !firstPath.startsWith(activity.internalStoragePath)
        if (isRPlus() && isSDOrOtgRootFolder && !isExternalStorageManager()) {
            activity.toast(com.simplemobiletools.commons.R.string.rename_in_sd_card_system_restriction, Toast.LENGTH_LONG)
            finishActMode()
            return
        }

        if (mediaPaths.size == 1) {
            RenameItemDialog(activity, firstPath) {
                ensureBackgroundThread {
                    activity.updateDBMediaPath(firstPath, it)

                    activity.runOnUiThread {
                        enableInstantLoad()
                        listener?.refreshItems()
                        finishActMode()
                    }
                }
            }
        } else {
            RenameDialog(activity, mediaPaths, true) {
                enableInstantLoad()
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun editFile() {
        val path = getFirstSelectedItemPath() ?: return
        activity.openEditor(path)
    }

    private fun openPath() {
        val path = getFirstSelectedItemPath() ?: return
        activity.openPath(path, true)
    }

    private fun setAs() {
        val path = getFirstSelectedItemPath() ?: return
        activity.setAs(path)
    }

    private fun resize() {
        val paths = getSelectedItems().filter { it.isImage() }.map { it.path }
        if (isOneItemSelected()) {
            val path = paths.first()
            activity.launchResizeImageDialog(path) {
                finishActMode()
                listener?.refreshItems()
            }
        } else {
            activity.launchResizeMultipleImagesDialog(paths) {
                finishActMode()
                listener?.refreshItems()
            }
        }
    }

    private fun canResize(selectedItems: ArrayList<Medium>): Boolean {
        val selectionContainsImages = selectedItems.any { it.isImage() }
        if (!selectionContainsImages) {
            return false
        }

        val parentPath = selectedItems.first { it.isImage() }.parentPath
        val isCommonParent = selectedItems.all { parentPath == it.parentPath }
        val isRestrictedDir = activity.isRestrictedWithSAFSdk30(parentPath)
        return isExternalStorageManager() || (isCommonParent && !isRestrictedDir)
    }

    // folders and files each follow their own hide/unhide flow; both run in a mixed selection
    private fun toggleSelectionVisibility(hide: Boolean) {
        val folderPaths = getSelectedFolderPaths()
        if (getSelectedPaths().isNotEmpty()) {
            toggleFileVisibility(hide)
        }

        if (folderPaths.isNotEmpty()) {
            toggleFoldersVisibility(hide, folderPaths)
        }
    }

    private fun toggleFoldersVisibility(hide: Boolean, paths: ArrayList<String>) {
        if (hide && !config.wasHideFolderTooltipShown) {
            config.wasHideFolderTooltipShown = true
            ConfirmationDialog(activity, activity.getString(R.string.hide_folder_description)) {
                applyFolderVisibility(true, paths)
            }
        } else {
            applyFolderVisibility(hide, paths)
        }
    }

    private fun applyFolderVisibility(hide: Boolean, paths: ArrayList<String>) {
        var remaining = paths.size
        val taskDone = {
            activity.runOnUiThread {
                remaining--
                if (remaining <= 0) {
                    listener?.refreshItems()
                    finishActMode()
                }
            }
        }

        paths.forEach { path ->
            if (hide) {
                activity.addNoMedia(path) { taskDone() }
            } else if (path.containsNoMedia()) {
                activity.removeNoMedia(path) { taskDone() }
            } else {
                taskDone()
            }
        }
    }

    private fun toggleFileVisibility(hide: Boolean) {
        ensureBackgroundThread {
            getSelectedItems().forEach {
                activity.toggleFileVisibility(it.path, hide)
            }
            activity.runOnUiThread {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun toggleFavorites(add: Boolean) {
        ensureBackgroundThread {
            getSelectedItems().forEach {
                it.isFavorite = add
                activity.updateFavorite(it.path, add)
            }
            activity.runOnUiThread {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun restoreFiles() {
        activity.restoreRecycleBinPaths(getSelectedPaths()) {
            listener?.refreshItems()
            finishActMode()
        }
    }

    private fun shareMedia() {
        if (selectedKeys.size == 1 && selectedKeys.first() != -1) {
            activity.shareMediumPath(getSelectedItems().first().path)
        } else if (selectedKeys.size > 1) {
            activity.shareMediaPaths(getSelectedPaths())
        }
    }

    private fun handleRotate(paths: List<String>, degrees: Int) {
        var fileCnt = paths.size
        rotatedImagePaths.clear()
        activity.toast(com.simplemobiletools.commons.R.string.saving)
        ensureBackgroundThread {
            paths.forEach {
                rotatedImagePaths.add(it)
                activity.saveRotatedImageToFile(it, it, degrees, true) {
                    fileCnt--
                    if (fileCnt == 0) {
                        activity.runOnUiThread {
                            listener?.refreshItems()
                            finishActMode()
                        }
                    }
                }
            }
        }
    }

    private fun rotateSelection(degrees: Int) {
        val paths = getSelectedPaths().filter { it.isImageFast() }

        if (paths.any { activity.needsStupidWritePermissions(it) }) {
            activity.handleSAFDialog(paths.first { activity.needsStupidWritePermissions(it) }) {
                if (it) {
                    handleRotate(paths, degrees)
                }
            }
        } else {
            handleRotate(paths, degrees)
        }
    }

    private fun moveFilesTo() {
        activity.handleDeletePasswordProtection {
            checkMediaManagementAndCopy(false)
        }
    }

    private fun checkMediaManagementAndCopy(isCopyOperation: Boolean) {
        activity.handleMediaManagementPrompt {
            copyMoveTo(isCopyOperation)
        }
    }

    private fun copyMoveTo(isCopyOperation: Boolean) {
        val paths = getSelectedCopyMovePaths()

        val recycleBinPath = activity.recycleBinPath
        val fileDirItems = paths.asSequence().filter { isCopyOperation || !it.startsWith(recycleBinPath) }.map {
            FileDirItem(it, it.getFilenameFromPath())
        }.toMutableList() as ArrayList

        if (!isCopyOperation && paths.any { it.startsWith(recycleBinPath) }) {
            activity.toast(com.simplemobiletools.commons.R.string.moving_recycle_bin_items_disabled, Toast.LENGTH_LONG)
        }

        if (fileDirItems.isEmpty()) {
            return
        }

        activity.tryCopyMoveFilesTo(fileDirItems, isCopyOperation) {
            val destinationPath = it
            config.tempFolderPath = ""
            activity.applicationContext.rescanFolderMedia(destinationPath)
            activity.applicationContext.rescanFolderMedia(fileDirItems.first().getParentPath())

            val newPaths = fileDirItems.map { "$destinationPath/${it.name}" }.toMutableList() as ArrayList<String>
            activity.rescanPaths(newPaths) {
                activity.fixDateTaken(newPaths, false)
            }

            if (!isCopyOperation) {
                listener?.refreshItems()
                activity.updateFavoritePaths(fileDirItems, destinationPath)
            }
        }
    }

    private fun createShortcut() {
        if (!isOreoPlus()) {
            return
        }

        val folderItem = media.filterIsInstance<SubfolderItem>().firstOrNull { selectedKeys.contains(it.directory.path.hashCode()) }
        if (folderItem != null) {
            createFolderShortcut(folderItem.directory)
            return
        }

        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (manager.isRequestPinShortcutSupported) {
            val path = getSelectedPaths().first()
            val drawable = resources.getDrawable(R.drawable.shortcut_image).mutate()
            activity.getShortcutImage(path, drawable) {
                val intent = Intent(activity, ViewPagerActivity::class.java).apply {
                    putExtra(PATH, path)
                    putExtra(SHOW_ALL, config.showAll)
                    putExtra(SHOW_FAVORITES, path == FAVORITES)
                    putExtra(SHOW_RECYCLE_BIN, path == RECYCLE_BIN)
                    action = Intent.ACTION_VIEW
                    flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

                val shortcut = ShortcutInfo.Builder(activity, path)
                    .setShortLabel(path.getFilenameFromPath())
                    .setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
                    .setIntent(intent)
                    .build()

                manager.requestPinShortcut(shortcut, null)
            }
        }
    }

    private fun createFolderShortcut(dir: Directory) {
        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (manager.isRequestPinShortcutSupported) {
            val drawable = resources.getDrawable(R.drawable.shortcut_image).mutate()
            activity.getShortcutImage(dir.tmb, drawable) {
                val intent = Intent(activity, MediaActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(DIRECTORY, dir.path)
                }

                val shortcut = ShortcutInfo.Builder(activity, dir.path)
                    .setShortLabel(dir.name)
                    .setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
                    .setIntent(intent)
                    .build()

                manager.requestPinShortcut(shortcut, null)
            }
        }
    }

    private fun fixDateTaken() {
        ensureBackgroundThread {
            activity.fixDateTaken(getSelectedPaths(), true) {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun checkDeleteConfirmation() {
        activity.handleMediaManagementPrompt {
            if (config.isDeletePasswordProtectionOn) {
                activity.handleDeletePasswordProtection {
                    deleteSelection(config.tempSkipRecycleBin)
                }
            } else if (config.tempSkipDeleteConfirmation || config.skipDeleteConfirmation) {
                deleteSelection(config.tempSkipRecycleBin)
            } else {
                askConfirmDelete()
            }
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val selectedMedia = getSelectedItems()
        val folderPaths = getSelectedFolderPaths()
        val isRecycleBin = selectedMedia.isNotEmpty() && selectedMedia.first().path.startsWith(activity.recycleBinPath)

        val itemsAndSize = when {
            itemsCnt == 1 && selectedMedia.size == 1 -> {
                val firstPath = selectedMedia.first().path
                val size = selectedMedia.first().toFileDirItem().getProperSize(activity, countHidden = true).formatSize()
                "\"${firstPath.getFilenameFromPath()}\" ($size)"
            }

            itemsCnt == 1 && folderPaths.isNotEmpty() -> "\"${folderPaths.first().getFilenameFromPath()}\""
            else -> {
                val fileSize = selectedMedia.sumByLong { it.toFileDirItem().getProperSize(activity, countHidden = true) }.formatSize()
                val deleteItemsString = resources.getQuantityString(com.simplemobiletools.commons.R.plurals.delete_items, itemsCnt, itemsCnt)
                if (selectedMedia.isEmpty()) deleteItemsString else "$deleteItemsString ($fileSize)"
            }
        }

        val baseString =
            if (config.useRecycleBin && !config.tempSkipRecycleBin && !isRecycleBin) com.simplemobiletools.commons.R.string.move_to_recycle_bin_confirmation else com.simplemobiletools.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), itemsAndSize)
        val showSkipRecycleBinOption = config.useRecycleBin && !isRecycleBin

        DeleteWithRememberDialog(activity, question, showSkipRecycleBinOption) { remember, skipRecycleBin ->
            config.tempSkipDeleteConfirmation = remember

            if (remember) {
                config.tempSkipRecycleBin = skipRecycleBin
            }

            deleteSelection(skipRecycleBin)
        }
    }

    // files are deleted like before, folders go through the folder deletion flow, both in a mixed selection
    private fun deleteSelection(skipRecycleBin: Boolean) {
        val folderPaths = getSelectedFolderPaths()
        if (folderPaths.isNotEmpty()) {
            deleteSelectedFolders(folderPaths, skipRecycleBin)
        }

        if (getSelectedItems().isNotEmpty()) {
            deleteFiles(skipRecycleBin)
        }
    }

    private fun deleteSelectedFolders(folderPaths: ArrayList<String>, skipRecycleBin: Boolean) {
        val SAFPath = folderPaths.firstOrNull { activity.needsStupidWritePermissions(it) } ?: folderPaths.first()
        activity.handleSAFDialog(SAFPath) {
            if (!it) {
                return@handleSAFDialog
            }

            val sdk30SAFPath = folderPaths.firstOrNull { activity.isAccessibleWithSAFSdk30(it) } ?: folderPaths.first()
            activity.checkManageMediaOrHandleSAFDialogSdk30(sdk30SAFPath) { granted ->
                if (!granted) {
                    return@checkManageMediaOrHandleSAFDialogSdk30
                }

                actuallyDeleteFolders(folderPaths, skipRecycleBin)
            }
        }
    }

    private fun actuallyDeleteFolders(folderPaths: ArrayList<String>, skipRecycleBin: Boolean) {
        ensureBackgroundThread {
            val mediaInside = folderPaths.flatMapTo(ArrayList()) { getMediaFilesInside(it) }
            val finishDelete = {
                // rows of files moved into the recycle bin are rewritten by movePathsInRecycleBin, this is a no-op for them
                mediaInside.forEach { activity.deleteDBPath(it.path) }
                folderPaths.forEach { path ->
                    File(path).deleteRecursively()
                    activity.directoryDB.deleteDirPath(path)
                }

                activity.runOnUiThread {
                    listener?.refreshItems()
                    finishActMode()
                }
            }

            val useRecycleBin = config.useRecycleBin && !skipRecycleBin && folderPaths.none { it.startsWith(activity.recycleBinPath) }
            if (useRecycleBin && mediaInside.isNotEmpty()) {
                val binPaths = mediaInside.map { it.path } as ArrayList<String>
                activity.movePathsInRecycleBin(binPaths) { wasSuccess ->
                    if (wasSuccess) {
                        finishDelete()
                    } else {
                        activity.toast(com.simplemobiletools.commons.R.string.unknown_error_occurred)
                    }
                }
            } else {
                finishDelete()
            }
        }
    }

    private fun deleteFiles(skipRecycleBin: Boolean) {
        if (selectedKeys.isEmpty()) {
            return
        }

        val selectedItems = getSelectedItems()
        val selectedPaths = selectedItems.map { it.path } as ArrayList<String>
        val SAFPath = selectedPaths.firstOrNull { activity.needsStupidWritePermissions(it) } ?: getFirstSelectedItemPath() ?: return
        activity.handleSAFDialog(SAFPath) {
            if (!it) {
                return@handleSAFDialog
            }

            val sdk30SAFPath = selectedPaths.firstOrNull { activity.isAccessibleWithSAFSdk30(it) } ?: getFirstSelectedItemPath() ?: return@handleSAFDialog
            activity.checkManageMediaOrHandleSAFDialogSdk30(sdk30SAFPath) {
                if (!it) {
                    return@checkManageMediaOrHandleSAFDialogSdk30
                }

                val fileDirItems = ArrayList<FileDirItem>(selectedKeys.size)
                val removeMedia = ArrayList<Medium>(selectedKeys.size)
                // folders stay in the list, only removed media rows may be announced as removed
                val positions = getSelectedItemPositions().filter { media.getOrNull(it) is Medium } as ArrayList<Int>

                selectedItems.forEach { medium ->
                    fileDirItems.add(medium.toFileDirItem())
                    removeMedia.add(medium)
                }

                media.removeAll(removeMedia)
                listener?.tryDeleteFiles(fileDirItems, skipRecycleBin)
                listener?.updateMediaGridDecoration(media)
                removeSelectedItems(positions)
                currentMediaHash = media.hashCode()
            }
        }
    }

    private fun getSelectedItems() = selectedKeys.mapNotNull { getItemWithKey(it) } as ArrayList<Medium>

    private fun getSelectedPaths() = getSelectedItems().map { it.path } as ArrayList<String>

    private fun getSelectedFolderPaths() = selectedKeys.mapNotNull { key ->
        media.filterIsInstance<SubfolderItem>().firstOrNull { it.directory.path.hashCode() == key }?.directory?.path
    } as ArrayList<String>

    // folders are copied, moved and deleted by taking the media files inside them, same as on the main screen
    private fun getSelectedCopyMovePaths(): ArrayList<String> {
        val paths = getSelectedPaths()
        getSelectedFolderPaths().forEach { folderPath ->
            getMediaFilesInside(folderPath).mapTo(paths) { it.path }
        }
        return paths
    }

    private fun getMediaFilesInside(folderPath: String): List<FileDirItem> {
        val showHidden = config.shouldShowHidden
        val filter = config.filterMedia
        return File(folderPath).listFiles()
            ?.filter {
                it.isFile && it.absolutePath.isMediaFile() && (showHidden || !it.name.startsWith(".")) &&
                    ((it.isImageFast() && filter and TYPE_IMAGES != 0) ||
                        (it.isVideoFast() && filter and TYPE_VIDEOS != 0) ||
                        (it.isGif() && filter and TYPE_GIFS != 0) ||
                        (it.isRawFast() && filter and TYPE_RAWS != 0) ||
                        (it.isSvg() && filter and TYPE_SVGS != 0))
            }
            ?.map { FileDirItem(it.absolutePath, it.name) }
            ?: emptyList()
    }

    private fun getFirstSelectedItemPath() = getItemWithKey(selectedKeys.first())?.path

    private fun getItemWithKey(key: Int): Medium? = media.firstOrNull { (it as? Medium)?.path?.hashCode() == key } as? Medium

    fun updateMedia(newMedia: ArrayList<ThumbnailItem>) {
        val thumbnailItems = newMedia.clone() as ArrayList<ThumbnailItem>
        if (thumbnailItems.hashCode() != currentMediaHash) {
            currentMediaHash = thumbnailItems.hashCode()
            media = thumbnailItems
            enableInstantLoad()
            notifyDataSetChanged()
            finishActMode()
        }
    }

    fun updateDisplayFilenames(displayFilenames: Boolean) {
        this.displayFilenames = displayFilenames
        enableInstantLoad()
        notifyDataSetChanged()
    }

    fun updateAnimateGifs(animateGifs: Boolean) {
        this.animateGifs = animateGifs
        notifyDataSetChanged()
    }

    fun updateCropThumbnails(cropThumbnails: Boolean) {
        this.cropThumbnails = cropThumbnails
        notifyDataSetChanged()
    }

    fun updateShowFileTypes(showFileTypes: Boolean) {
        this.showFileTypes = showFileTypes
        notifyDataSetChanged()
    }

    private fun enableInstantLoad() {
        loadImageInstantly = true
        delayHandler.postDelayed({
            loadImageInstantly = false
        }, INSTANT_LOAD_DURATION)
    }

    private fun setupThumbnail(view: View, medium: Medium) {
        val isSelected = selectedKeys.contains(medium.path.hashCode())
        bindItem(view, medium).apply {
            val padding = if (config.thumbnailSpacing <= 1) {
                config.thumbnailSpacing
            } else {
                0
            }

            mediaItemHolder.setPadding(padding, padding, padding, padding)

            favorite.beVisibleIf(medium.isFavorite && config.markFavoriteItems)

            playPortraitOutline?.beVisibleIf(medium.isVideo() || medium.isPortrait())
            if (medium.isVideo()) {
                playPortraitOutline?.setImageResource(com.simplemobiletools.commons.R.drawable.ic_play_outline_vector)
                playPortraitOutline?.beVisible()
            } else if (medium.isPortrait()) {
                playPortraitOutline?.setImageResource(R.drawable.ic_portrait_photo_vector)
                playPortraitOutline?.beVisibleIf(showFileTypes)
            }

            if (showFileTypes && (medium.isGIF() || medium.isRaw() || medium.isSVG())) {
                fileType?.setText(
                    when (medium.type) {
                        TYPE_GIFS -> R.string.gif
                        TYPE_RAWS -> R.string.raw
                        else -> R.string.svg
                    }
                )
                fileType?.beVisible()
            } else {
                fileType?.beGone()
            }

            mediumName.beVisibleIf(displayFilenames || isListViewType)
            mediumName.text = medium.name
            mediumName.tag = medium.path

            val showVideoDuration = medium.isVideo() && config.showThumbnailVideoDuration
            if (showVideoDuration) {
                videoDuration?.text = medium.videoDuration.getFormattedDuration()
            }
            videoDuration?.beVisibleIf(showVideoDuration)

            mediumCheck.beVisibleIf(isSelected)
            if (isSelected) {
                mediumCheck.background?.applyColorFilter(properPrimaryColor)
                mediumCheck.applyColorFilter(contrastColor)
            }

            if (isListViewType) {
                mediaItemHolder.isSelected = isSelected
            }

            var path = medium.path
            if (hasOTGConnected && root.context.isPathOnOTG(path)) {
                path = path.getOTGPublicPath(root.context)
            }

            val roundedCorners = when {
                isListViewType -> ROUNDED_CORNERS_SMALL
                config.fileRoundedCorners -> ROUNDED_CORNERS_BIG
                else -> ROUNDED_CORNERS_NONE
            }

            if (loadImageInstantly) {
                activity.loadImage(
                    medium.type, path, mediumThumbnail, scrollHorizontally, animateGifs, cropThumbnails, roundedCorners, medium.getKey(), rotatedImagePaths
                )
            } else {
                mediumThumbnail.setImageDrawable(null)
                mediumThumbnail.isHorizontalScrolling = scrollHorizontally
                delayHandler.postDelayed({
                    val isVisible = visibleItemPaths.contains(medium.path)
                    if (isVisible) {
                        activity.loadImage(
                            medium.type, path, mediumThumbnail, scrollHorizontally, animateGifs, cropThumbnails, roundedCorners,
                            medium.getKey(), rotatedImagePaths
                        )
                    }
                }, IMAGE_LOAD_DELAY)
            }

            if (isListViewType) {
                mediumName.setTextColor(textColor)
                playPortraitOutline?.applyColorFilter(textColor)
            }
        }
    }

    private fun setupSection(view: View, section: ThumbnailSection) {
        ThumbnailSectionBinding.bind(view).apply {
            thumbnailSection.text = section.title
            thumbnailSection.setTextColor(textColor)
        }
    }

    private fun setupSubfolder(view: View, item: SubfolderItem) {
        val dir = item.directory
        val isSelected = selectedKeys.contains(dir.path.hashCode())
        bindDirectoryItem(view).bindDirectoryTile(
            activity = activity,
            directory = dir,
            isSelected = isSelected,
            isListViewType = isListViewType,
            folderStyle = folderStyle,
            scrollHorizontally = scrollHorizontally,
            animateGifs = animateGifs,
            cropThumbnails = cropThumbnails,
            showMediaCount = showMediaCount,
            limitFolderTitle = limitFolderTitle,
            groupDirectSubfolders = groupDirectSubfolders,
            isLocked = config.isFolderProtected(dir.path),
            isPinned = pinnedFolders.contains(dir.path),
            isDragging = false,
            textColor = textColor
        )
    }

    private fun bindDirectoryItem(view: View): DirectoryItemBinding = when {
        isListViewType -> DirectoryItemListBinding.bind(view).toItemBinding()
        folderStyle == FOLDER_STYLE_SQUARE -> DirectoryItemGridSquareBinding.bind(view).toItemBinding()
        else -> DirectoryItemGridRoundedCornersBinding.bind(view).toItemBinding()
    }

    override fun onChange(position: Int): String {
        var realIndex = position
        if (isASectionTitle(position)) {
            realIndex++
        }

        return (media[realIndex] as? Medium)?.getBubbleText(sorting, activity, dateFormat, timeFormat) ?: ""
    }

    private fun bindItem(view: View, medium: Medium): MediaItemBinding {
        return if (isListViewType) {
            if (!medium.isVideo() && !medium.isPortrait()) {
                PhotoItemListBinding.bind(view).toMediaItemBinding()
            } else {
                VideoItemListBinding.bind(view).toMediaItemBinding()
            }
        } else {
            if (!medium.isVideo() && !medium.isPortrait()) {
                PhotoItemGridBinding.bind(view).toMediaItemBinding()
            } else {
                VideoItemGridBinding.bind(view).toMediaItemBinding()
            }
        }
    }
}
