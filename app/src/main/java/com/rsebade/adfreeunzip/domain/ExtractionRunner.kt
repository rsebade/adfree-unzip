package com.rsebade.adfreeunzip.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The latest extraction of one archive, kept after it ends so the result can be shown later. */
sealed interface ExtractionStatus {
    val archiveName: String
    val folderUri: String

    data class Running(
        override val archiveName: String,
        override val folderUri: String,
        val entriesWritten: Int,
        val totalEntries: Int,
    ) : ExtractionStatus

    data class Finished(
        override val archiveName: String,
        override val folderUri: String,
        val result: ArchiveResult<ExtractionSummary>,
    ) : ExtractionStatus

    data class Cancelled(
        override val archiveName: String,
        override val folderUri: String,
    ) : ExtractionStatus
}

/**
 * Runs extractions in an app-wide [scope], so they keep going when the screen that started them
 * is closed. Several archives may extract at once; [statuses] holds the latest one per archive URI.
 * [scope] must run on the main thread, and so must calls to [start] and [cancel].
 */
class ExtractionRunner(
    private val repository: ArchiveRepository,
    private val scope: CoroutineScope,
) {
    private val _statuses = MutableStateFlow<Map<String, ExtractionStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ExtractionStatus>> = _statuses.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    /** Does nothing if [archiveUri] is already being extracted. */
    fun start(
        archiveUri: String,
        archiveName: String,
        folderUri: String,
        totalEntries: Int,
    ) {
        if (_statuses.value[archiveUri] is ExtractionStatus.Running) return
        setStatus(archiveUri, ExtractionStatus.Running(archiveName, folderUri, 0, totalEntries))
        jobs[archiveUri] =
            scope.launch {
                val result =
                    repository.extract(archiveUri, folderUri) { written ->
                        updateRunning(archiveUri) { it.copy(entriesWritten = written) }
                    }
                updateRunning(archiveUri) { ExtractionStatus.Finished(archiveName, folderUri, result) }
                jobs.remove(archiveUri)
            }
    }

    fun cancel(archiveUri: String) {
        val running = _statuses.value[archiveUri] as? ExtractionStatus.Running ?: return
        jobs.remove(archiveUri)?.cancel()
        setStatus(archiveUri, ExtractionStatus.Cancelled(running.archiveName, running.folderUri))
    }

    private fun setStatus(
        archiveUri: String,
        status: ExtractionStatus,
    ) {
        _statuses.update { it + (archiveUri to status) }
    }

    /** Progress can arrive from a worker thread after [cancel]; only a still-running status changes. */
    private fun updateRunning(
        archiveUri: String,
        transform: (ExtractionStatus.Running) -> ExtractionStatus,
    ) {
        _statuses.update { statuses ->
            val running = statuses[archiveUri] as? ExtractionStatus.Running ?: return@update statuses
            statuses + (archiveUri to transform(running))
        }
    }
}
