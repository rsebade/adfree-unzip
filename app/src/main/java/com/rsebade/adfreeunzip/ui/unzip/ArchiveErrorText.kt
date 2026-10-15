package com.rsebade.adfreeunzip.ui.unzip

import androidx.annotation.StringRes
import com.rsebade.adfreeunzip.R
import com.rsebade.adfreeunzip.domain.ArchiveError

/** The message shown to the user for [this] error, on screen or in a notification. */
@StringRes
fun ArchiveError.messageRes(): Int =
    when (this) {
        ArchiveError.NoEntries -> R.string.unzip_error_no_entries
        ArchiveError.Encrypted -> R.string.unzip_error_encrypted
        ArchiveError.Corrupted -> R.string.unzip_error_corrupted
        is ArchiveError.Io -> R.string.unzip_error_io
    }
