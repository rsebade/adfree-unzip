package com.rsebade.adfreeunzip.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import com.rsebade.adfreeunzip.domain.ExtractionTarget
import java.io.IOException
import java.io.OutputStream

/**
 * Writes into a folder the user picked with the system folder picker (a Storage Access
 * Framework tree), so no storage permission is needed. Existing folders are reused; a file
 * whose name is taken gets a numbered name from the provider, e.g. "notes (1).txt".
 */
class SafExtractionTarget(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : ExtractionTarget {
    private val directories = hashMapOf<List<String>, Uri>()

    override fun createDirectory(path: List<String>) {
        rethrowAsIo { directory(path) }
    }

    override fun openFile(path: List<String>): OutputStream =
        rethrowAsIo {
            val name = path.last()
            val parent = directory(path.dropLast(1))
            val file =
                DocumentsContract.createDocument(resolver, parent, mimeTypeOf(name), name)
                    ?: throw IOException("Couldn't create $name")
            resolver.openOutputStream(file) ?: throw IOException("Couldn't write $name")
        }

    private fun directory(path: List<String>): Uri =
        directories.getOrPut(path) {
            if (path.isEmpty()) {
                return@getOrPut DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                )
            }
            val parent = directory(path.dropLast(1))
            val name = path.last()
            findChildDirectory(parent, name)
                ?: DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, name)
                ?: throw IOException("Couldn't create folder $name")
        }

    private fun findChildDirectory(
        parent: Uri,
        name: String,
    ): Uri? {
        val children =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getDocumentId(parent),
            )
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name && cursor.getString(2) == Document.MIME_TYPE_DIR) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                }
            }
        }
        return null
    }

    private fun mimeTypeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    /** Providers also throw SecurityException/IllegalArgumentException; report them as I/O failures. */
    private inline fun <T> rethrowAsIo(block: () -> T): T =
        try {
            block()
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            throw IOException(e.message, e)
        }
}
