package com.rsebade.adfreeunzip

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.rsebade.adfreeunzip.data.ContentArchiveRepository
import com.rsebade.adfreeunzip.domain.ArchiveRepository
import com.rsebade.adfreeunzip.domain.ExtractionRunner
import com.rsebade.adfreeunzip.domain.ExtractionStatus
import com.rsebade.adfreeunzip.notifications.ExtractionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Holds the app-wide objects (manual DI) that must outlive any one screen. */
class UnzipApplication : Application() {
    /** Lives as long as the process; extractions run here so leaving the screen doesn't stop them. */
    private val appScope: CoroutineScope = MainScope()
    private var startedActivities = 0

    val archiveRepository: ArchiveRepository by lazy { ContentArchiveRepository(contentResolver) }

    val extractionRunner: ExtractionRunner by lazy { ExtractionRunner(archiveRepository, appScope) }

    /** True while one of the app's screens is on screen. */
    val isAppVisible: Boolean get() = startedActivities > 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        registerActivityLifecycleCallbacks(VisibilityTracker())
        appScope.launch {
            extractionRunner.statuses
                .map { statuses -> statuses.values.any { it is ExtractionStatus.Running } }
                .distinctUntilChanged()
                .collect { anyRunning -> if (anyRunning) startExtractionService() }
        }
    }

    private fun startExtractionService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, ExtractionService::class.java))
        } catch (_: IllegalStateException) {
            // Android refused a foreground start (app already in the background); extraction still runs.
        }
    }

    private fun createNotificationChannels() {
        getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(
                    ExtractionService.CHANNEL_PROGRESS,
                    getString(R.string.notification_channel_progress),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    ExtractionService.CHANNEL_RESULTS,
                    getString(R.string.notification_channel_results),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    private inner class VisibilityTracker : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities--
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
