package com.rsebade.adfreeunzip.domain

/** Archive operations addressed by URI strings, so callers stay free of Android types. */
interface ArchiveRepository {
    suspend fun displayName(archiveUri: String): String

    suspend fun list(archiveUri: String): ArchiveResult<List<ArchiveEntry>>

    /** Cancellable: cancelling the calling coroutine stops extraction promptly. */
    suspend fun extract(
        archiveUri: String,
        destinationTreeUri: String,
        onProgress: (entriesWritten: Int) -> Unit,
    ): ArchiveResult<ExtractionSummary>
}
