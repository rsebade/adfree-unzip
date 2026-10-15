package com.rsebade.adfreeunzip.data

import android.content.ContentResolver
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.rsebade.adfreeunzip.domain.ArchiveEntry
import com.rsebade.adfreeunzip.domain.ArchiveRepository
import com.rsebade.adfreeunzip.domain.ArchiveResult
import com.rsebade.adfreeunzip.domain.ExtractionSummary
import com.rsebade.adfreeunzip.domain.ZipArchiveReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/** Reads archives from content URIs (file picker, "Open with") off the main thread. */
class ContentArchiveRepository(
    private val resolver: ContentResolver,
    private val reader: ZipArchiveReader = ZipArchiveReader(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ArchiveRepository {
    override suspend fun displayName(archiveUri: String): String =
        withContext(ioDispatcher) {
            val uri = archiveUri.toUri()
            val queried =
                runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull()
            queried ?: uri.lastPathSegment ?: archiveUri
        }

    override suspend fun list(archiveUri: String): ArchiveResult<List<ArchiveEntry>> =
        withContext(ioDispatcher) { reader.list { open(archiveUri) } }

    override suspend fun extract(
        archiveUri: String,
        destinationTreeUri: String,
        onProgress: (entriesWritten: Int) -> Unit,
    ): ArchiveResult<ExtractionSummary> =
        withContext(ioDispatcher) {
            val target = SafExtractionTarget(resolver, destinationTreeUri.toUri())
            reader.extract({ open(archiveUri) }, target) { written ->
                ensureActive()
                onProgress(written)
            }
        }

    private fun open(archiveUri: String): InputStream =
        try {
            resolver.openInputStream(archiveUri.toUri()) ?: throw IOException("Couldn't open $archiveUri")
        } catch (e: SecurityException) {
            throw IOException(e.message, e)
        }
}
