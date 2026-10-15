package com.rsebade.adfreeunzip

import android.app.Application
import com.rsebade.adfreeunzip.data.ContentArchiveRepository
import com.rsebade.adfreeunzip.domain.ArchiveRepository
import com.rsebade.adfreeunzip.domain.ExtractionRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

/** Holds the app-wide objects (manual DI) that must outlive any one screen. */
class UnzipApplication : Application() {
    /** Lives as long as the process; extractions run here so leaving the screen doesn't stop them. */
    private val appScope: CoroutineScope = MainScope()

    val archiveRepository: ArchiveRepository by lazy { ContentArchiveRepository(contentResolver) }

    val extractionRunner: ExtractionRunner by lazy { ExtractionRunner(archiveRepository, appScope) }
}
