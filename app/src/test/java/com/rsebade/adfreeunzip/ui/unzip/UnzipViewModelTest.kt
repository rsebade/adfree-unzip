package com.rsebade.adfreeunzip.ui.unzip

import com.rsebade.adfreeunzip.domain.ArchiveEntry
import com.rsebade.adfreeunzip.domain.ArchiveError
import com.rsebade.adfreeunzip.domain.ArchiveRepository
import com.rsebade.adfreeunzip.domain.ArchiveResult
import com.rsebade.adfreeunzip.domain.ExtractionRunner
import com.rsebade.adfreeunzip.domain.ExtractionSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertEquals(UnzipUiState.NoArchive, newViewModel().uiState.value)
    }

    @Test
    fun `onArchiveSelected shows the archive contents`() {
        val viewModel = newViewModel()

        viewModel.onArchiveSelected("content://a.zip")

        assertEquals(UnzipUiState.Browsing("content://a.zip", "a.zip", entries), viewModel.uiState.value)
    }

    @Test
    fun `onArchiveSelected reports why an archive can't be opened`() {
        repository.listResult = ArchiveResult.Failure(ArchiveError.Encrypted)
        val viewModel = newViewModel()

        viewModel.onArchiveSelected("content://a.zip")

        assertEquals(UnzipUiState.OpenFailed(ArchiveError.Encrypted), viewModel.uiState.value)
    }

    @Test
    fun `onDestinationSelected extracts and reports the summary`() {
        val viewModel = newViewModel()
        viewModel.onArchiveSelected("content://a.zip")
        val pending = CompletableDeferred<ArchiveResult<ExtractionSummary>>()
        repository.extractResult = pending

        viewModel.onDestinationSelected("content://tree")
        assertEquals(Extraction.InProgress(0, 1), (viewModel.uiState.value as UnzipUiState.Browsing).extraction)
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
        val viewModel = newViewModel()
        viewModel.onArchiveSelected("content://a.zip")
        val before = viewModel.uiState.value

        viewModel.onOpenFolder()

        assertEquals(before, viewModel.uiState.value)
    }

    @Test
    fun `extraction progress is reported as entries written out of the total`() {
        repository.listResult = ArchiveResult.Success(entries + entries + entries + entries)
        val viewModel = newViewModel()
        viewModel.onArchiveSelected("content://a.zip")
        repository.extractResult = CompletableDeferred()

        viewModel.onDestinationSelected("content://tree")
        repository.onProgress(3)

        val extraction = (viewModel.uiState.value as UnzipUiState.Browsing).extraction
        assertEquals(Extraction.InProgress(3, 4), extraction)
        assertEquals(0.75f, (extraction as Extraction.InProgress).fraction)
    }

    @Test
    fun `onCancelExtraction stops the extraction and says so`() {
        val viewModel = newViewModel()
        viewModel.onArchiveSelected("content://a.zip")
        repository.extractResult = CompletableDeferred()
        viewModel.onDestinationSelected("content://tree")

        viewModel.onCancelExtraction()
        repository.onProgress(1)

        assertTrue(repository.wasCancelled)
        assertEquals(Extraction.Cancelled, (viewModel.uiState.value as UnzipUiState.Browsing).extraction)
    }

    @Test
    fun `closing the archive lets extraction keep running in the background`() {
        val viewModel = newViewModel()
        viewModel.onArchiveSelected("content://a.zip")
        val pending = CompletableDeferred<ArchiveResult<ExtractionSummary>>()
        repository.extractResult = pending
        viewModel.onDestinationSelected("content://tree")

        viewModel.onCloseArchive()
        viewModel.onArchiveSelected("content://b.zip")

        assertFalse(repository.wasCancelled)
        assertTrue(pending.isActive)
        assertEquals("content://b.zip", (viewModel.uiState.value as UnzipUiState.Browsing).archiveUri)
    }

    @Test
    fun `extracting again after cancelling starts over`() {
        val viewModel = newViewModel()
        viewModel.onArchiveSelected("content://a.zip")
        repository.extractResult = CompletableDeferred()
        viewModel.onDestinationSelected("content://tree")
        viewModel.onCancelExtraction()

        repository.extractResult = CompletableDeferred(ArchiveResult.Success(summary))
        viewModel.onDestinationSelected("content://tree")

        assertEquals(
            Extraction.Done(summary, "content://tree"),
            (viewModel.uiState.value as UnzipUiState.Browsing).extraction,
        )
    }

    @Test
    fun `reopening an archive extracted in the background shows the result without opening the folder`() {
        val viewModel = newViewModel()
        viewModel.onArchiveSelected("content://a.zip")
        val pending = CompletableDeferred<ArchiveResult<ExtractionSummary>>()
        repository.extractResult = pending
        viewModel.onDestinationSelected("content://tree")
        viewModel.onCloseArchive()
        pending.complete(ArchiveResult.Success(summary))

        viewModel.onArchiveSelected("content://a.zip")

        assertEquals(
            Extraction.Done(summary, "content://tree", FolderOpening.Idle),
            (viewModel.uiState.value as UnzipUiState.Browsing).extraction,
        )
    }

    @Test
    fun `extraction finishing while the app is in the background does not open the folder by itself`() {
        var visible = true
        val runner = ExtractionRunner(repository, CoroutineScope(Dispatchers.Main))
        val viewModel = UnzipViewModel(repository, runner) { visible }
        viewModel.onArchiveSelected("content://a.zip")
        val pending = CompletableDeferred<ArchiveResult<ExtractionSummary>>()
        repository.extractResult = pending
        viewModel.onDestinationSelected("content://tree")

        visible = false
        pending.complete(ArchiveResult.Success(summary))

        assertEquals(
            Extraction.Done(summary, "content://tree", FolderOpening.Idle),
            (viewModel.uiState.value as UnzipUiState.Browsing).extraction,
        )
    }

    private fun newViewModel(): UnzipViewModel =
        UnzipViewModel(repository, ExtractionRunner(repository, CoroutineScope(Dispatchers.Main)))

    private fun extracted(): UnzipViewModel =
        newViewModel().apply {
            onArchiveSelected("content://a.zip")
            onDestinationSelected("content://tree")
        }

    private fun UnzipViewModel.folderOpening(): FolderOpening =
        ((uiState.value as UnzipUiState.Browsing).extraction as Extraction.Done).folderOpening

    @Test
    fun `extraction finishing after the archive was closed leaves no archive open`() {
        val viewModel = newViewModel()
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

        var onProgress: (Int) -> Unit = {}
        var wasCancelled = false

        override suspend fun extract(
            archiveUri: String,
            destinationTreeUri: String,
            onProgress: (entriesWritten: Int) -> Unit,
        ): ArchiveResult<ExtractionSummary> {
            extractions += archiveUri to destinationTreeUri
            this.onProgress = onProgress
            try {
                return extractResult.await()
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            }
        }
    }
}
