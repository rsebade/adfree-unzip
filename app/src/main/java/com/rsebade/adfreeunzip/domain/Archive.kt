package com.rsebade.adfreeunzip.domain

import java.io.OutputStream

/**
 * One file or folder inside an archive. [path] is already sanitized: no empty, "." or ".."
 * segments, so it can never point outside the folder it is extracted into.
 */
data class ArchiveEntry(
    val path: List<String>,
    val isDirectory: Boolean,
    /** Uncompressed size in bytes, or null when the archive doesn't record it up front. */
    val size: Long?,
) {
    val name: String get() = path.last()
}

data class ExtractionSummary(
    val filesWritten: Int,
    val directoriesCreated: Int,
    /** Raw names of entries that were skipped because they would escape the target folder. */
    val skippedUnsafe: List<String>,
)

sealed interface ArchiveError {
    /** Not a zip file, or a zip with nothing in it. */
    data object NoEntries : ArchiveError

    /** The zip uses encryption, which isn't supported. */
    data object Encrypted : ArchiveError

    /** The zip is damaged or truncated. */
    data object Corrupted : ArchiveError

    /** Reading the archive or writing to the target failed for an I/O reason. */
    data class Io(
        val message: String?,
    ) : ArchiveError
}

sealed interface ArchiveResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ArchiveResult<T>

    data class Failure(
        val error: ArchiveError,
    ) : ArchiveResult<Nothing>
}

/** Where extracted files go. Implemented over a real folder, a SAF tree, or a fake in tests. */
interface ExtractionTarget {
    fun createDirectory(path: List<String>)

    /** Opens [path] for writing, creating parent folders as needed. The caller closes the stream. */
    fun openFile(path: List<String>): OutputStream
}
