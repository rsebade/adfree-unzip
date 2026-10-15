package com.rsebade.adfreeunzip.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExtractionRunnerTest {
    private val summary = ExtractionSummary(filesWritten = 2, directoriesCreated = 0, skippedUnsafe = emptyList())
    private val repository = FakeRepository()
    private val runner = ExtractionRunner(repository, CoroutineScope(UnconfinedTestDispatcher()))

    @Test
    fun `start reports progress then the result`() {
        runner.start("a.zip", "a", "tree", totalEntries = 2)
        assertEquals(ExtractionStatus.Running("a", "tree", 0, 2), runner.statuses.value["a.zip"])

        repository.onProgress(1)
        assertEquals(ExtractionStatus.Running("a", "tree", 1, 2), runner.statuses.value["a.zip"])

        repository.result.complete(ArchiveResult.Success(summary))
        assertEquals(
            ExtractionStatus.Finished("a", "tree", ArchiveResult.Success(summary)),
            runner.statuses.value["a.zip"],
        )
    }

    @Test
    fun `cancel stops the extraction and ignores late progress`() {
        runner.start("a.zip", "a", "tree", totalEntries = 2)

        runner.cancel("a.zip")
        repository.onProgress(1)

        assertTrue(repository.wasCancelled)
        assertEquals(ExtractionStatus.Cancelled("a", "tree"), runner.statuses.value["a.zip"])
    }

    @Test
    fun `start is ignored while the same archive is already extracting`() {
        runner.start("a.zip", "a", "tree", totalEntries = 2)
        runner.start("a.zip", "a", "other-tree", totalEntries = 2)

        assertEquals(1, repository.calls)
        assertEquals("tree", runner.statuses.value["a.zip"]?.folderUri)
    }

    @Test
    fun `different archives extract independently`() {
        runner.start("a.zip", "a", "tree", totalEntries = 2)
        runner.start("b.zip", "b", "tree", totalEntries = 3)

        runner.cancel("a.zip")

        assertEquals(ExtractionStatus.Cancelled("a", "tree"), runner.statuses.value["a.zip"])
        assertEquals(ExtractionStatus.Running("b", "tree", 0, 3), runner.statuses.value["b.zip"])
    }

    private class FakeRepository : ArchiveRepository {
        val result = CompletableDeferred<ArchiveResult<ExtractionSummary>>()
        var onProgress: (Int) -> Unit = {}
        var wasCancelled = false
        var calls = 0

        override suspend fun displayName(archiveUri: String) = archiveUri

        override suspend fun list(archiveUri: String): ArchiveResult<List<ArchiveEntry>> =
            ArchiveResult.Success(emptyList())

        override suspend fun extract(
            archiveUri: String,
            destinationTreeUri: String,
            onProgress: (entriesWritten: Int) -> Unit,
        ): ArchiveResult<ExtractionSummary> {
            calls++
            this.onProgress = onProgress
            try {
                return result.await()
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            }
        }
    }
}
