package com.rsebade.adfreeunzip.ui.unzip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    fun extractingShowsProgressAndCanBeCancelled() {
        var cancelled = 0
        setScreen(browsing.copy(extraction = Extraction.InProgress(3, 4)), onCancelExtraction = { cancelled++ })

        composeRule.onNodeWithTag(UnzipTestTags.PROGRESS).assertExists()
        composeRule.onNodeWithTag(UnzipTestTags.RESULT).assertTextEquals("Extracting… 3 of 4")
        composeRule.onNodeWithTag(UnzipTestTags.EXTRACT_ALL).assertDoesNotExist()
        composeRule.onNodeWithTag(UnzipTestTags.CANCEL).performClick()

        assertEquals(1, cancelled)
    }

    @Test
    fun cancelledExtractionSaysSoAndOffersExtractAgain() {
        setScreen(browsing.copy(extraction = Extraction.Cancelled))

        composeRule
            .onNodeWithTag(UnzipTestTags.RESULT)
            .assertTextEquals("Extraction cancelled. Files finished before that were kept.")
        composeRule.onNodeWithTag(UnzipTestTags.EXTRACT_ALL).assertExists()
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

    @Test
    fun onAWideScreenTheContentWidthIsCapped() {
        setScreenOfWidth(1000.dp)

        composeRule.onNodeWithTag(UnzipTestTags.ENTRIES).assertWidthIsEqualTo(MaxContentWidth)
    }

    @Test
    fun onAPhoneTheContentFillsTheWidth() {
        setScreenOfWidth(360.dp)

        composeRule.onNodeWithTag(UnzipTestTags.ENTRIES).assertWidthIsEqualTo(360.dp)
    }

    private fun setScreenOfWidth(width: Dp) {
        composeRule.setContent {
            AppTheme {
                Box(Modifier.requiredWidth(width)) {
                    UnzipScreen(browsing, {}, {}, onCloseArchive = {}, onOpenFolder = {}, onCancelExtraction = {})
                }
            }
        }
    }

    private fun setScreen(
        state: UnzipUiState,
        onChooseArchive: () -> Unit = {},
        onExtractAll: () -> Unit = {},
        onOpenFolder: () -> Unit = {},
        onCancelExtraction: () -> Unit = {},
    ) {
        composeRule.setContent {
            AppTheme {
                UnzipScreen(
                    state,
                    onChooseArchive,
                    onExtractAll,
                    onCloseArchive = {},
                    onOpenFolder = onOpenFolder,
                    onCancelExtraction = onCancelExtraction,
                )
            }
        }
    }
}
