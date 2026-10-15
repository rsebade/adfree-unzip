package com.rsebade.adfreeunzip.ui.unzip

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.core.net.toUri

/**
 * Shows the folder picked with the system folder picker in the Files app. Returns false when no
 * installed app can open a folder, so the caller can say so instead of failing silently.
 */
fun Context.openFolderInFilesApp(treeUri: String): Boolean =
    try {
        val tree = treeUri.toUri()
        val folder = DocumentsContract.buildDocumentUri(tree.authority, DocumentsContract.getTreeDocumentId(tree))
        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: SecurityException) {
        false
    }
