package com.rsebade.adfreeunzip.ui.unzip

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rsebade.adfreeunzip.domain.ArchiveEntry
import com.rsebade.adfreeunzip.domain.ExtractionSummary
import com.rsebade.adfreeunzip.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnzipScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val browsing =
        UnzipUiState.Browsing(
            archiveUri = "content://photos.zip",
            archiveName = "photos.zip",
            entries = listOf(ArchiveEntry(listOf("trip", "beach.jpg"), isDirectory = false, size = null)),
        )

    @Test
    fun noArchiveOffersToChooseOne() {
        var chosen = 0
        setScreen(UnzipUiState.NoArchive, onChooseArchive = { chosen++ })

        composeRule.onNodeWithTag(UnzipTestTags.CHOOSE_ARCHIVE).performClick()

        assertEquals(1, chosen)
    }

    @Test
    fun browsingListsEntriesAndExtractsAll() {
        var extracted = 0
        setScreen(browsing, onExtractAll = { extracted++ })

        composeRule.onNodeWithText("beach.jpg").assertExists()
        composeRule.onNodeWithTag(UnzipTestTags.EXTRACT_ALL).performClick()

        assertEquals(1, extracted)
    }

    @Test
    fun extractAllIsDisabledWhileExtracting() {
        setScreen(browsing.copy(extraction = Extraction.InProgress))

        composeRule.onNodeWithTag(UnzipTestTags.EXTRACT_ALL).assertIsNotEnabled()
    }

    @Test
    fun finishedExtractionShowsFileCount() {
        setScreen(browsing.copy(extraction = Extraction.Done(ExtractionSummary(3, 1, emptyList()), "content://tree")))

        composeRule.onNodeWithTag(UnzipTestTags.RESULT).assertTextEquals("Extracted 3 files.")
    }

    @Test
    fun finishedExtractionOffersToOpenTheFolder() {
        var opened = 0
        val done = Extraction.Done(ExtractionSummary(3, 1, emptyList()), "content://tree", FolderOpening.Idle)
        setScreen(browsing.copy(extraction = done), onOpenFolder = { opened++ })

        composeRule.onNodeWithTag(UnzipTestTags.OPEN_FOLDER_UNAVAILABLE).assertDoesNotExist()
        composeRule.onNodeWithTag(UnzipTestTags.OPEN_FOLDER).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun explainsWhenNoAppCanOpenTheFolder() {
        val done = Extraction.Done(ExtractionSummary(3, 1, emptyList()), "content://tree", FolderOpening.Unavailable)
        setScreen(browsing.copy(extraction = done))

        composeRule.onNodeWithTag(UnzipTestTags.OPEN_FOLDER_UNAVAILABLE).assertExists()
    }

    private fun setScreen(
        state: UnzipUiState,
        onChooseArchive: () -> Unit = {},
        onExtractAll: () -> Unit = {},
        onOpenFolder: () -> Unit = {},
    ) {
        composeRule.setContent {
            AppTheme {
                UnzipScreen(
                    state,
                    onChooseArchive,
                    onExtractAll,
                    onCloseArchive = {},
                    onOpenFolder = onOpenFolder,
                )
            }
        }
    }
}
