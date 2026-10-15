package com.rsebade.adfreeunzip.ui.unzip

import com.rsebade.adfreeunzip.domain.ArchiveEntry
import com.rsebade.adfreeunzip.domain.ArchiveError
import com.rsebade.adfreeunzip.domain.ArchiveRepository
import com.rsebade.adfreeunzip.domain.ArchiveResult
import com.rsebade.adfreeunzip.domain.ExtractionSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnzipViewModelTest {
    private val entries = listOf(ArchiveEntry(listOf("a.txt"), isDirectory = false, size = 1))
    private val summary = ExtractionSummary(filesWritten = 1, directoriesCreated = 0, skippedUnsafe = emptyList())
    private val repository = FakeRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has no archive`() {
        assertEquals(UnzipUiState.NoArchive, UnzipViewModel(repository).uiState.value)
    }

    @Test
    fun `onArchiveSelected shows the archive contents`() {
        val viewModel = UnzipViewModel(repository)

        viewModel.onArchiveSelected("content://a.zip")

        assertEquals(UnzipUiState.Browsing("content://a.zip", "a.zip", entries), viewModel.uiState.value)
    }

    @Test
    fun `onArchiveSelected reports why an archive can't be opened`() {
        repository.listResult = ArchiveResult.Failure(ArchiveError.Encrypted)
        val viewModel = UnzipViewModel(repository)

        viewModel.onArchiveSelected("content://a.zip")

        assertEquals(UnzipUiState.OpenFailed(ArchiveError.Encrypted), viewModel.uiState.value)
    }

    @Test
    fun `onDestinationSelected extracts and reports the summary`() {
        val viewModel = UnzipViewModel(repository)
        viewModel.onArchiveSelected("content://a.zip")
        val pending = CompletableDeferred<ArchiveResult<ExtractionSummary>>()
        repository.extractResult = pending

        viewModel.onDestinationSelected("content://tree")
        assertEquals(Extraction.InProgress, (viewModel.uiState.value as UnzipUiState.Browsing).extraction)
        pending.complete(ArchiveResult.Success(summary))

        assertEquals(
            Extraction.Done(summary, "content://tree", FolderOpening.Requested),
            (viewModel.uiState.value as UnzipUiState.Browsing).extraction,
        )
        assertEquals(listOf("content://a.zip" to "content://tree"), repository.extractions)
    }

    @Test
    fun `onFolderOpenResult records whether the folder could be shown`() {
        val viewModel = extracted()

        viewModel.onFolderOpenResult(opened = true)
        assertEquals(FolderOpening.Idle, viewModel.folderOpening())

        viewModel.onOpenFolder()
        assertEquals(FolderOpening.Requested, viewModel.folderOpening())

        viewModel.onFolderOpenResult(opened = false)
        assertEquals(FolderOpening.Unavailable, viewModel.folderOpening())
    }

    @Test
    fun `onOpenFolder does nothing before extraction finishes`() {
        val viewModel = UnzipViewModel(repository)
        viewModel.onArchiveSelected("content://a.zip")
        val before = viewModel.uiState.value

        viewModel.onOpenFolder()

        assertEquals(before, viewModel.uiState.value)
    }

    private fun extracted(): UnzipViewModel =
        UnzipViewModel(repository).apply {
            onArchiveSelected("content://a.zip")
            onDestinationSelected("content://tree")
        }

    private fun UnzipViewModel.folderOpening(): FolderOpening =
        ((uiState.value as UnzipUiState.Browsing).extraction as Extraction.Done).folderOpening

    @Test
    fun `extraction finishing after the archive was closed leaves no archive open`() {
        val viewModel = UnzipViewModel(repository)
        viewModel.onArchiveSelected("content://a.zip")
        val pending = CompletableDeferred<ArchiveResult<ExtractionSummary>>()
        repository.extractResult = pending

        viewModel.onDestinationSelected("content://tree")
        viewModel.onCloseArchive()
        pending.complete(ArchiveResult.Success(summary))

        assertEquals(UnzipUiState.NoArchive, viewModel.uiState.value)
    }

    private inner class FakeRepository : ArchiveRepository {
        var listResult: ArchiveResult<List<ArchiveEntry>> = ArchiveResult.Success(entries)
        var extractResult = CompletableDeferred(ArchiveResult.Success(summary) as ArchiveResult<ExtractionSummary>)
        val extractions = mutableListOf<Pair<String, String>>()

        override suspend fun displayName(archiveUri: String) = archiveUri.substringAfterLast('/')

        override suspend fun list(archiveUri: String) = listResult

        override suspend fun extract(
            archiveUri: String,
            destinationTreeUri: String,
        ): ArchiveResult<ExtractionSummary> {
            extractions += archiveUri to destinationTreeUri
            return extractResult.await()
        }
    }
}
