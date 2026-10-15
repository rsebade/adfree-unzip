package com.rsebade.adfreeunzip.domain

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Lists and extracts zip archives. Blocking: call from a background dispatcher.
 *
 * Takes a stream factory rather than a stream so it can re-read the archive with the legacy
 * IBM437 charset when entry names aren't valid UTF-8 (common for zips made on older Windows).
 * Every stream it opens, it also closes.
 */
class ZipArchiveReader {
    /** Lists the safe entries; unsafe ones (see [sanitizePath]) are left out. */
    fun list(openStream: () -> InputStream): ArchiveResult<List<ArchiveEntry>> =
        withCharsetFallback(openStream) { zip ->
            val entries = mutableListOf<ArchiveEntry>()
            var sawAny = false
            forEachEntry(zip) { rawName, isDirectory, size ->
                sawAny = true
                val path = sanitizePath(rawName) ?: return@forEachEntry
                entries += ArchiveEntry(path, isDirectory, size.takeIf { it >= 0 })
            }
            entries.takeIf { sawAny }
        }

    /**
     * Writes every safe entry into [target] and reports what was skipped. If entry names turn
     * out not to be UTF-8 partway through, the archive is extracted again with IBM437, so
     * entries before that point are written twice (same content; names only differ for
     * non-ASCII names without the zip UTF-8 flag, which is rare).
     */
    fun extract(
        openStream: () -> InputStream,
        target: ExtractionTarget,
    ): ArchiveResult<ExtractionSummary> =
        withCharsetFallback(openStream) { zip ->
            var files = 0
            var directories = 0
            val skipped = mutableListOf<String>()
            forEachEntry(zip) { rawName, isDirectory, _ ->
                val path = sanitizePath(rawName)
                when {
                    path == null -> skipped += rawName
                    isDirectory -> {
                        target.createDirectory(path)
                        directories++
                    }
                    else -> {
                        target.openFile(path).use { out -> zip.copyTo(out) }
                        files++
                    }
                }
            }
            ExtractionSummary(files, directories, skipped).takeIf { files + directories + skipped.size > 0 }
        }

    /** Runs [block] over the archive; a null result from it means the archive had no entries. */
    private fun <T : Any> withCharsetFallback(
        openStream: () -> InputStream,
        block: (ZipInputStream) -> T?,
    ): ArchiveResult<T> =
        try {
            read(openStream, Charsets.UTF_8, block)
        } catch (_: IllegalArgumentException) {
            // ZipInputStream throws this for entry names that aren't valid in the charset.
            try {
                read(openStream, IBM437, block)
            } catch (_: IllegalArgumentException) {
                ArchiveResult.Failure(ArchiveError.Corrupted)
            }
        }

    private fun <T : Any> read(
        openStream: () -> InputStream,
        charset: Charset,
        block: (ZipInputStream) -> T?,
    ): ArchiveResult<T> =
        try {
            ZipInputStream(openStream(), charset).use { zip ->
                block(zip)?.let { ArchiveResult.Success(it) } ?: ArchiveResult.Failure(ArchiveError.NoEntries)
            }
        } catch (e: ZipException) {
            val encrypted = e.message?.contains("encrypted", ignoreCase = true) == true
            ArchiveResult.Failure(if (encrypted) ArchiveError.Encrypted else ArchiveError.Corrupted)
        } catch (_: EOFException) {
            ArchiveResult.Failure(ArchiveError.Corrupted)
        } catch (e: IOException) {
            ArchiveResult.Failure(ArchiveError.Io(e.message))
        }

    private inline fun forEachEntry(
        zip: ZipInputStream,
        action: (rawName: String, isDirectory: Boolean, size: Long) -> Unit,
    ) {
        while (true) {
            val entry = zip.nextEntry ?: break
            action(entry.name, entry.isDirectory, entry.size)
            zip.closeEntry()
        }
    }

    companion object {
        private val IBM437: Charset = Charset.forName("IBM437")

        /**
         * Turns a raw entry name into safe path segments, or null if it would escape the
         * target folder (a "zip slip" entry such as `../../evil.sh`). Leading slashes and
         * Windows separators are normalized; "." and empty segments are dropped.
         */
        fun sanitizePath(rawName: String): List<String>? {
            val segments =
                rawName
                    .replace('\\', '/')
                    .split('/')
                    .filter { it.isNotEmpty() && it != "." }
            return segments.takeIf { it.isNotEmpty() && ".." !in segments }
        }
    }
}
