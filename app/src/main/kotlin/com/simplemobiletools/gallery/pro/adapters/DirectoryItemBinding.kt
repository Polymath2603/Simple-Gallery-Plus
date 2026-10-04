package com.simplemobiletools.gallery.pro.adapters

import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.interfaces.StartReorderDragListener
import com.simplemobiletools.commons.views.MySquareImageView
import com.simplemobiletools.gallery.pro.databinding.DirectoryItemGridRoundedCornersBinding
import com.simplemobiletools.gallery.pro.helpers.*
import com.simplemobiletools.gallery.pro.databinding.DirectoryItemGridSquareBinding
import com.simplemobiletools.gallery.pro.databinding.DirectoryItemListBinding
import com.simplemobiletools.gallery.pro.extensions.loadImage
import com.simplemobiletools.gallery.pro.models.Directory

interface DirectoryItemBinding {
    val root: ViewGroup
    val dirThumbnail: MySquareImageView
    val dirPath: TextView?
    val dirCheck: ImageView
    val dirHolder: ViewGroup
    val photoCnt: TextView
    val dirName: TextView
    val dirLock: ImageView
    val dirPin: ImageView
    val dirLocation: ImageView
    val dirDragHandle: ImageView
    val dirDragHandleWrapper: ViewGroup?
}

class ListDirectoryItemBinding(val binding: DirectoryItemListBinding) : DirectoryItemBinding {
    override val root: ViewGroup = binding.root
    override val dirThumbnail: MySquareImageView = binding.dirThumbnail
    override val dirPath: TextView = binding.dirPath
    override val dirCheck: ImageView = binding.dirCheck
    override val dirHolder: ViewGroup = binding.dirHolder
    override val photoCnt: TextView = binding.photoCnt
    override val dirName: TextView = binding.dirName
    override val dirLock: ImageView = binding.dirLock
    override val dirPin: ImageView = binding.dirPin
    override val dirLocation: ImageView = binding.dirLocation
    override val dirDragHandle: ImageView = binding.dirDragHandle
    override val dirDragHandleWrapper: ViewGroup? = null
}

fun DirectoryItemListBinding.toItemBinding() = ListDirectoryItemBinding(this)

class GridDirectoryItemSquareBinding(val binding: DirectoryItemGridSquareBinding) : DirectoryItemBinding {
    override val root: ViewGroup = binding.root
    override val dirThumbnail: MySquareImageView = binding.dirThumbnail
    override val dirPath: TextView? = null
    override val dirCheck: ImageView = binding.dirCheck
    override val dirHolder: ViewGroup = binding.dirHolder
    override val photoCnt: TextView = binding.photoCnt
    override val dirName: TextView = binding.dirName
    override val dirLock: ImageView = binding.dirLock
    override val dirPin: ImageView = binding.dirPin
    override val dirLocation: ImageView = binding.dirLocation
    override val dirDragHandle: ImageView = binding.dirDragHandle
    override val dirDragHandleWrapper: ViewGroup = binding.dirDragHandleWrapper
}

fun DirectoryItemGridSquareBinding.toItemBinding() = GridDirectoryItemSquareBinding(this)

class GridDirectoryItemRoundedCornersBinding(val binding: DirectoryItemGridRoundedCornersBinding) : DirectoryItemBinding {
    override val root: ViewGroup = binding.root
    override val dirThumbnail: MySquareImageView = binding.dirThumbnail
    override val dirPath: TextView? = null
    override val dirCheck: ImageView = binding.dirCheck
    override val dirHolder: ViewGroup = binding.dirHolder
    override val photoCnt: TextView = binding.photoCnt
    override val dirName: TextView = binding.dirName
    override val dirLock: ImageView = binding.dirLock
    override val dirPin: ImageView = binding.dirPin
    override val dirLocation: ImageView = binding.dirLocation
    override val dirDragHandle: ImageView = binding.dirDragHandle
    override val dirDragHandleWrapper: ViewGroup = binding.dirDragHandleWrapper
}

fun DirectoryItemGridRoundedCornersBinding.toItemBinding() = GridDirectoryItemRoundedCornersBinding(this)

/**
 * Single place where a folder tile is rendered, shared by the folders list on the main screen
 * (DirectoryAdapter) and tree-mode subfolder tiles inside a folder (MediaAdapter), so both
 * places always look and behave the same.
 */
fun DirectoryItemBinding.bindDirectoryTile(
    activity: BaseSimpleActivity,
    directory: Directory,
    isSelected: Boolean,
    isListViewType: Boolean,
    folderStyle: Int,
    scrollHorizontally: Boolean,
    animateGifs: Boolean,
    cropThumbnails: Boolean,
    showMediaCount: Int,
    limitFolderTitle: Boolean,
    groupDirectSubfolders: Boolean,
    isLocked: Boolean,
    isPinned: Boolean,
    isDragging: Boolean,
    textColor: Int,
    dragListener: StartReorderDragListener? = null,
    dragHolder: RecyclerView.ViewHolder? = null
) {
    val properPrimaryColor = activity.getProperPrimaryColor()
    val contrastColor = properPrimaryColor.getContrastColor()

    dirPath?.text = "${directory.path.substringBeforeLast("/")}/"
    val thumbnailType = when {
        directory.tmb.isVideoFast() -> TYPE_VIDEOS
        directory.tmb.isGif() -> TYPE_GIFS
        directory.tmb.isRawFast() -> TYPE_RAWS
        directory.tmb.isSvg() -> TYPE_SVGS
        else -> TYPE_IMAGES
    }

    dirCheck.beVisibleIf(isSelected)
    if (isSelected) {
        dirCheck.background?.applyColorFilter(properPrimaryColor)
        dirCheck.applyColorFilter(contrastColor)
    }

    if (isListViewType) {
        dirHolder.isSelected = isSelected
    }

    if (scrollHorizontally && !isListViewType && folderStyle == FOLDER_STYLE_ROUNDED_CORNERS) {
        (dirThumbnail.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.ABOVE, dirName.id)

        val photoCntParams = (photoCnt.layoutParams as RelativeLayout.LayoutParams)
        val nameParams = (dirName.layoutParams as RelativeLayout.LayoutParams)
        nameParams.removeRule(RelativeLayout.BELOW)

        if (showMediaCount == FOLDER_MEDIA_CNT_LINE) {
            nameParams.addRule(RelativeLayout.ABOVE, photoCnt.id)
            nameParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)

            photoCntParams.removeRule(RelativeLayout.BELOW)
            photoCntParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        } else {
            nameParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        }
    }

    if (isLocked) {
        dirLock.beVisible()
        dirLock.background = ColorDrawable(root.context.getProperBackgroundColor())
        dirLock.applyColorFilter(root.context.getProperBackgroundColor().getContrastColor())
    } else {
        dirLock.beGone()
        val roundedCorners = when {
            isListViewType -> ROUNDED_CORNERS_SMALL
            folderStyle == FOLDER_STYLE_SQUARE -> ROUNDED_CORNERS_NONE
            else -> ROUNDED_CORNERS_BIG
        }

        activity.loadImage(
            thumbnailType,
            directory.tmb,
            dirThumbnail,
            scrollHorizontally,
            animateGifs,
            cropThumbnails,
            roundedCorners,
            directory.getKey()
        )
    }

    dirPin.beVisibleIf(isPinned)
    dirLocation.beVisibleIf(directory.location != LOCATION_INTERNAL)
    if (dirLocation.isVisible()) {
        dirLocation.setImageResource(if (directory.location == LOCATION_SD) com.simplemobiletools.commons.R.drawable.ic_sd_card_vector else com.simplemobiletools.commons.R.drawable.ic_usb_vector)
    }

    photoCnt.text = directory.subfoldersMediaCount.toString()
    photoCnt.beVisibleIf(showMediaCount == FOLDER_MEDIA_CNT_LINE)

    if (limitFolderTitle) {
        dirName.setSingleLine()
        dirName.ellipsize = TextUtils.TruncateAt.MIDDLE
    }

    var nameCount = directory.name
    if (showMediaCount == FOLDER_MEDIA_CNT_BRACKETS) {
        nameCount += " (${directory.subfoldersMediaCount})"
    }

    if (groupDirectSubfolders) {
        if (directory.subfoldersCount > 1) {
            nameCount += " [${directory.subfoldersCount}]"
        }
    }

    dirName.text = nameCount

    if (isListViewType || folderStyle == FOLDER_STYLE_ROUNDED_CORNERS) {
        photoCnt.setTextColor(textColor)
        dirName.setTextColor(textColor)
        dirLocation.applyColorFilter(textColor)
    }

    if (isListViewType) {
        dirPath?.setTextColor(textColor)
        dirPin.applyColorFilter(textColor)
        dirLocation.applyColorFilter(textColor)
        dirDragHandle.beVisibleIf(isDragging)
    } else {
        dirDragHandleWrapper?.beVisibleIf(isDragging)
    }

    if (isDragging) {
        dirDragHandle.applyColorFilter(textColor)
        dirDragHandle.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN && dragHolder != null) {
                dragListener?.requestDrag(dragHolder)
            }
            false
        }
    }
}
