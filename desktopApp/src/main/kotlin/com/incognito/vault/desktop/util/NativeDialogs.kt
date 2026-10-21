package com.incognito.vault.desktop.util

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager

object NativeDialogs {

    init {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            // Fallback to default
        }
    }

    /**
     * Shows native Windows open file dialog allowing selection of one or multiple media files.
     */
    fun pickMediaFiles(parent: Frame? = null): List<File> {
        val dialog = FileDialog(parent, "Select Media to Import (Encrypted Vault)", FileDialog.LOAD)
        dialog.isMultipleMode = true
        dialog.filenameFilter = FilenameFilter { _, name ->
            val lower = name.lowercase()
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                    lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".gif") ||
                    lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov") ||
                    lower.endsWith(".avi") || lower.endsWith(".webm") || lower.endsWith(".icgt")
        }
        dialog.isVisible = true

        val files = dialog.files
        return files?.toList() ?: emptyList()
    }

    /**
     * Shows native Windows folder selection dialog.
     */
    fun pickDirectory(parent: Frame? = null, title: String = "Select Destination Folder"): File? {
        val chooser = JFileChooser()
        chooser.dialogTitle = title
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false

        val result = chooser.showOpenDialog(parent)
        return if (result == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile
        } else {
            null
        }
    }

    /**
     * Shows native Windows save file dialog.
     */
    fun pickSaveFile(parent: Frame? = null, title: String, defaultName: String): File? {
        val dialog = FileDialog(parent, title, FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true

        val directory = dialog.directory
        val file = dialog.file
        return if (directory != null && file != null) {
            File(directory, file)
        } else {
            null
        }
    }
}
