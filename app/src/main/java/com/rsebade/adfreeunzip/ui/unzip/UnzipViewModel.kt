package com.rsebade.adfreeunzip.ui.unzip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rsebade.adfreeunzip.data.ContentArchiveRepository
import com.rsebade.adfreeunzip.domain.ArchiveEntry
import com.rsebade.adfreeunzip.domain.ArchiveError
import com.rsebade.adfreeunzip.domain.ArchiveRepository
import com.rsebade.adfreeunzip.domain.ArchiveResult
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

    data object InProgress : Extraction

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
) : ViewModel() {
    private val _uiState = MutableStateFlow<UnzipUiState>(UnzipUiState.NoArchive)
    val uiState: StateFlow<UnzipUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun onArchiveSelected(archiveUri: String) {
        loadJob?.cancel()
        _uiState.value = UnzipUiState.Loading
        loadJob =
            viewModelScope.launch {
                val name = repository.displayName(archiveUri)
                _uiState.value =
                    when (val result = repository.list(archiveUri)) {
                        is ArchiveResult.Success -> UnzipUiState.Browsing(archiveUri, name, result.value)
                        is ArchiveResult.Failure -> UnzipUiState.OpenFailed(result.error)
                    }
            }
    }

    fun onDestinationSelected(destinationTreeUri: String) {
        val browsing = _uiState.value as? UnzipUiState.Browsing ?: return
        if (browsing.extraction == Extraction.InProgress) return
        setExtraction(browsing.archiveUri, Extraction.InProgress)
        viewModelScope.launch {
            val extraction =
                when (val result = repository.extract(browsing.archiveUri, destinationTreeUri)) {
                    is ArchiveResult.Success -> Extraction.Done(result.value, destinationTreeUri)
                    is ArchiveResult.Failure -> Extraction.Failed(result.error)
                }
            setExtraction(browsing.archiveUri, extraction)
        }
    }

    /** "Open folder" tapped after extracting; the route performs the request. */
    fun onOpenFolder() = setFolderOpening(FolderOpening.Requested)

    /** The route tried to open the folder; [opened] is false if no app could show it. */
    fun onFolderOpenResult(opened: Boolean) =
        setFolderOpening(if (opened) FolderOpening.Idle else FolderOpening.Unavailable)

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

    /** Ignored if the user has since closed or switched archives. */
    private fun setExtraction(
        archiveUri: String,
        extraction: Extraction,
    ) {
        _uiState.update { state ->
            if (state is UnzipUiState.Browsing &&
                state.archiveUri == archiveUri
            ) {
                state.copy(extraction = extraction)
            } else {
                state
            }
        }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    UnzipViewModel(
                        ContentArchiveRepository(checkNotNull(this[APPLICATION_KEY]).contentResolver),
                    )
                }
            }
    }
}
