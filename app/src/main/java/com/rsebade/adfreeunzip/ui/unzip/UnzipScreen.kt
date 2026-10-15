package com.rsebade.adfreeunzip.ui.unzip

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsebade.adfreeunzip.R
import com.rsebade.adfreeunzip.domain.ArchiveEntry
import com.rsebade.adfreeunzip.domain.ArchiveError
import com.rsebade.adfreeunzip.domain.ExtractionSummary
import com.rsebade.adfreeunzip.ui.theme.AppTheme

private val ZIP_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/x-zip")

/**
 * Stateful entry point: owns the system file/folder pickers and the ViewModel.
 * [initialArchiveUri] is a zip handed to the app via "Open with".
 */
@Composable
fun UnzipRoute(
    initialArchiveUri: String? = null,
    viewModel: UnzipViewModel = viewModel(factory = UnzipViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pickArchive =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.onArchiveSelected(it.toString()) }
        }
    val pickDestination =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { viewModel.onDestinationSelected(it.toString()) }
        }
    LaunchedEffect(initialArchiveUri) {
        initialArchiveUri?.let(viewModel::onArchiveSelected)
    }
    UnzipScreen(
        uiState = uiState,
        onChooseArchive = { pickArchive.launch(ZIP_MIME_TYPES) },
        onExtractAll = { pickDestination.launch(null) },
        onCloseArchive = viewModel::onCloseArchive,
    )
}

@Composable
fun UnzipScreen(
    uiState: UnzipUiState,
    onChooseArchive: () -> Unit,
    onExtractAll: () -> Unit,
    onCloseArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        val content = Modifier.fillMaxSize().padding(innerPadding)
        when (uiState) {
            UnzipUiState.NoArchive -> Message(stringResource(R.string.unzip_empty), onChooseArchive, content)
            UnzipUiState.Loading ->
                Column(content, Arrangement.Center, Alignment.CenterHorizontally) { CircularProgressIndicator() }
            is UnzipUiState.OpenFailed -> Message(errorText(uiState.error), onChooseArchive, content)
            is UnzipUiState.Browsing -> Browsing(uiState, onExtractAll, onCloseArchive, content)
        }
    }
}

@Composable
private fun Message(
    text: String,
    onChooseArchive: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(UnzipTestTags.MESSAGE))
        Button(onClick = onChooseArchive, modifier = Modifier.testTag(UnzipTestTags.CHOOSE_ARCHIVE)) {
            Text(stringResource(R.string.unzip_choose_archive))
        }
    }
}

@Composable
private fun Browsing(
    state: UnzipUiState.Browsing,
    onExtractAll: () -> Unit,
    onCloseArchive: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                state.archiveName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.unzip_item_count, state.entries.size, state.entries.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).testTag(UnzipTestTags.ENTRIES)) {
            items(state.entries) { entry -> EntryRow(entry) }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val extraction = state.extraction) {
                Extraction.NotStarted -> Unit
                Extraction.InProgress -> Text(stringResource(R.string.unzip_extracting))
                is Extraction.Done -> Text(summaryText(extraction.summary), Modifier.testTag(UnzipTestTags.RESULT))
                is Extraction.Failed -> Text(errorText(extraction.error), Modifier.testTag(UnzipTestTags.RESULT))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCloseArchive) { Text(stringResource(R.string.unzip_close)) }
                Button(
                    onClick = onExtractAll,
                    enabled = state.extraction != Extraction.InProgress,
                    modifier = Modifier.testTag(UnzipTestTags.EXTRACT_ALL),
                ) {
                    Text(stringResource(R.string.unzip_extract_all))
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: ArchiveEntry) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val folder = entry.path.dropLast(1).joinToString("/")
            val detail =
                when {
                    entry.isDirectory -> stringResource(R.string.unzip_folder)
                    entry.size != null -> Formatter.formatShortFileSize(context, entry.size)
                    else -> null
                }
            val line = listOfNotNull(folder.ifEmpty { null }, detail).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
}

@Composable
private fun summaryText(summary: ExtractionSummary): String {
    val done = pluralStringResource(R.plurals.unzip_extracted, summary.filesWritten, summary.filesWritten)
    if (summary.skippedUnsafe.isEmpty()) return done
    val skipped = summary.skippedUnsafe.size
    return done + "\n" + pluralStringResource(R.plurals.unzip_skipped_unsafe, skipped, skipped)
}

@Composable
private fun errorText(error: ArchiveError): String =
    when (error) {
        ArchiveError.NoEntries -> stringResource(R.string.unzip_error_no_entries)
        ArchiveError.Encrypted -> stringResource(R.string.unzip_error_encrypted)
        ArchiveError.Corrupted -> stringResource(R.string.unzip_error_corrupted)
        is ArchiveError.Io -> stringResource(R.string.unzip_error_io)
    }

object UnzipTestTags {
    const val MESSAGE = "unzip_message"
    const val CHOOSE_ARCHIVE = "unzip_choose_archive"
    const val ENTRIES = "unzip_entries"
    const val EXTRACT_ALL = "unzip_extract_all"
    const val RESULT = "unzip_result"
}

@Preview(showBackground = true)
@Composable
private fun UnzipScreenPreview() {
    AppTheme {
        UnzipScreen(
            uiState =
                UnzipUiState.Browsing(
                    archiveUri = "content://preview/photos.zip",
                    archiveName = "photos.zip",
                    entries =
                        listOf(
                            ArchiveEntry(listOf("trip"), isDirectory = true, size = null),
                            ArchiveEntry(listOf("trip", "beach.jpg"), isDirectory = false, size = 2_400_000),
                            ArchiveEntry(listOf("notes.txt"), isDirectory = false, size = 512),
                        ),
                ),
            onChooseArchive = {},
            onExtractAll = {},
            onCloseArchive = {},
        )
    }
}
