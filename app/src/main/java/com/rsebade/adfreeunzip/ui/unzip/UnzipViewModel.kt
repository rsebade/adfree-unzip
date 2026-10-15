package com.rsebade.adfreeunzip.ui.unzip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rsebade.adfreeunzip.UnzipApplication
import com.rsebade.adfreeunzip.domain.ArchiveEntry
import com.rsebade.adfreeunzip.domain.ArchiveError
import com.rsebade.adfreeunzip.domain.ArchiveRepository
import com.rsebade.adfreeunzip.domain.ArchiveResult
import com.rsebade.adfreeunzip.domain.ExtractionRunner
import com.rsebade.adfreeunzip.domain.ExtractionStatus
import com.rsebade.adfreeunzip.domain.ExtractionSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UnzipUiState {
    data object NoArchive : UnzipUiState

    data object Loading : UnzipUiState

    data class Browsing(
        val archiveUri: String,
        val archiveName: String,
        val entries: List<ArchiveEntry>,
        val extraction: Extraction = Extraction.NotStarted,
    ) : UnzipUiState

    data class OpenFailed(
        val error: ArchiveError,
    ) : UnzipUiState
}

sealed interface Extraction {
    data object NotStarted : Extraction

    data class InProgress(
        val entriesWritten: Int,
        val totalEntries: Int,
    ) : Extraction {
        val fraction: Float
            get() = if (totalEntries > 0) (entriesWritten.toFloat() / totalEntries).coerceAtMost(1f) else 0f
    }

    /** Stopped by the user; entries finished before that were kept. */
    data object Cancelled : Extraction

    data class Done(
        val summary: ExtractionSummary,
        val folderUri: String,
        val folderOpening: FolderOpening = FolderOpening.Requested,
    ) : Extraction

    data class Failed(
        val error: ArchiveError,
    ) : Extraction
}

/** Showing the extracted files in the Files app: asked for, handled, or not possible. */
sealed interface FolderOpening {
    data object Requested : FolderOpening

    data object Idle : FolderOpening

    data object Unavailable : FolderOpening
}

class UnzipViewModel(
    private val repository: ArchiveRepository,
    private val runner: ExtractionRunner,
    /** Whether the app is on screen; the folder only opens by itself if the user is here to see it. */
    private val isAppVisible: () -> Boolean = { true },
) : ViewModel() {
    private val _uiState = MutableStateFlow<UnzipUiState>(UnzipUiState.NoArchive)
    val uiState: StateFlow<UnzipUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            runner.statuses.collect { statuses ->
                _uiState.update { state ->
                    if (state !is UnzipUiState.Browsing) return@update state
                    state.copy(extraction = extractionFor(statuses[state.archiveUri], state.extraction))
                }
            }
        }
    }

    fun onArchiveSelected(archiveUri: String) {
        loadJob?.cancel()
        _uiState.value = UnzipUiState.Loading
        loadJob =
            viewModelScope.launch {
                val name = repository.displayName(archiveUri)
                _uiState.value =
                    when (val result = repository.list(archiveUri)) {
                        is ArchiveResult.Success -> {
                            // Shows an extraction of this archive that is still running or ended earlier.
                            val extraction = extractionFor(runner.statuses.value[archiveUri], Extraction.NotStarted)
                            UnzipUiState.Browsing(archiveUri, name, result.value, extraction)
                        }
                        is ArchiveResult.Failure -> UnzipUiState.OpenFailed(result.error)
                    }
            }
    }

    fun onDestinationSelected(destinationTreeUri: String) {
        val browsing = _uiState.value as? UnzipUiState.Browsing ?: return
        runner.start(browsing.archiveUri, browsing.archiveName, destinationTreeUri, browsing.entries.size)
    }

    fun onCancelExtraction() {
        val browsing = _uiState.value as? UnzipUiState.Browsing ?: return
        runner.cancel(browsing.archiveUri)
    }

    /** "Open folder" tapped after extracting; the route performs the request. */
    fun onOpenFolder() = setFolderOpening(FolderOpening.Requested)

    /** The route tried to open the folder; [opened] is false if no app could show it. */
    fun onFolderOpenResult(opened: Boolean) =
        setFolderOpening(if (opened) FolderOpening.Idle else FolderOpening.Unavailable)

    /** Leaves any running extraction to finish in the background; only [onCancelExtraction] stops it. */
    fun onCloseArchive() {
        loadJob?.cancel()
        _uiState.value = UnzipUiState.NoArchive
    }

    private fun setFolderOpening(folderOpening: FolderOpening) {
        _uiState.update { state ->
            val done = (state as? UnzipUiState.Browsing)?.extraction as? Extraction.Done ?: return@update state
            state.copy(extraction = done.copy(folderOpening = folderOpening))
        }
    }

    /**
     * Maps the runner's status to what the screen shows. The folder opens by itself only for an
     * extraction this screen watched finish, not for one that ended while the archive was closed.
     */
    private fun extractionFor(
        status: ExtractionStatus?,
        current: Extraction,
    ): Extraction =
        when (status) {
            null -> Extraction.NotStarted
            is ExtractionStatus.Running -> Extraction.InProgress(status.entriesWritten, status.totalEntries)
            is ExtractionStatus.Cancelled -> Extraction.Cancelled
            is ExtractionStatus.Finished ->
                when (val result = status.result) {
                    is ArchiveResult.Failure -> Extraction.Failed(result.error)
                    is ArchiveResult.Success ->
                        current as? Extraction.Done
                            ?: done(result.value, status.folderUri, current)
                }
        }

    private fun done(
        summary: ExtractionSummary,
        folderUri: String,
        previous: Extraction,
    ): Extraction.Done {
        val watchedItFinish = previous is Extraction.InProgress && isAppVisible()
        return Extraction.Done(summary, folderUri, if (watchedItFinish) FolderOpening.Requested else FolderOpening.Idle)
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) as UnzipApplication
                    UnzipViewModel(app.archiveRepository, app.extractionRunner) { app.isAppVisible }
                }
            }
    }
}
