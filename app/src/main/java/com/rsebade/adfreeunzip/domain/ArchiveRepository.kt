package com.rsebade.adfreeunzip.domain

/** Archive operations addressed by URI strings, so callers stay free of Android types. */
interface ArchiveRepository {
    suspend fun displayName(archiveUri: String): String

    suspend fun list(archiveUri: String): ArchiveResult<List<ArchiveEntry>>

    suspend fun extract(
        archiveUri: String,
        destinationTreeUri: String,
    ): ArchiveResult<ExtractionSummary>
}
