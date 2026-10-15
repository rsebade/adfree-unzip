package com.rsebade.adfreeunzip.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rsebade.adfreeunzip.R
import com.rsebade.adfreeunzip.UnzipApplication
import com.rsebade.adfreeunzip.domain.ArchiveResult
import com.rsebade.adfreeunzip.domain.ExtractionStatus
import com.rsebade.adfreeunzip.ui.unzip.folderViewIntent
import com.rsebade.adfreeunzip.ui.unzip.messageRes
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app in the foreground while extractions run, so Android doesn't stop them once the
 * user leaves, and shows each one as a notification with progress and Cancel. Started by
 * [UnzipApplication] when an extraction begins; stops itself when none are left running.
 */
class ExtractionService : Service() {
    private val scope = MainScope()
    private val planner = ExtractionNotificationPlanner()
    private val notificationIds = mutableMapOf<String, Int>()
    private val lastProgressPost = mutableMapOf<String, Long>()
    private var foregroundUri: String? = null

    private val app get() = application as UnzipApplication

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch { app.extractionRunner.statuses.collect(::render) }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_ARCHIVE_URI)?.let(app.extractionRunner::cancel)
            return START_NOT_STICKY
        }
        // Render through the planner so it knows about any progress notification posted here and
        // later replaces it; the collector may first see the extraction already finished.
        val statuses = app.extractionRunner.statuses.value
        render(statuses)
        // Started with startForegroundService: Android requires startForeground right away.
        val uri = foregroundUri
        val running = uri?.let { statuses[it] as? ExtractionStatus.Running }
        if (uri != null && running != null) {
            enterForeground(uri, running)
        } else {
            // It already finished; satisfy the requirement, then leave.
            ServiceCompat.startForeground(this, PLACEHOLDER_ID, placeholderNotification(), FOREGROUND_TYPE)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        // Android 15 caps data-sync services at 6 hours a day; extraction continues without the service.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(statuses: Map<String, ExtractionStatus>) {
        val plan = planner.plan(statuses, app.isAppVisible)
        val foreground = foregroundUri
        if (plan.isIdle && foreground != null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            foregroundUri = null
        } else if (!plan.isIdle && (foreground == null || foreground !in plan.inProgress)) {
            val (uri, status) = plan.inProgress.entries.first()
            enterForeground(uri, status)
        }
        plan.inProgress.forEach { (uri, status) -> postProgress(uri, status) }
        val manager = getSystemService(NotificationManager::class.java)
        plan.toDismiss.forEach { manager.cancel(idFor(it)) }
        plan.toAnnounce.forEach { (uri, status) -> post(idFor(uri), resultNotification(uri, status)) }
        if (plan.isIdle && foreground != null) stopSelf()
    }

    private fun enterForeground(
        uri: String,
        status: ExtractionStatus.Running,
    ) {
        ServiceCompat.startForeground(this, idFor(uri), progressNotification(uri, status), FOREGROUND_TYPE)
        foregroundUri = uri
    }

    /** Progress can change many times a second; Android drops notifications posted that fast. */
    private fun postProgress(
        uri: String,
        status: ExtractionStatus.Running,
    ) {
        val now = SystemClock.elapsedRealtime()
        val last = lastProgressPost[uri]
        if (last != null && now - last < PROGRESS_INTERVAL_MS && status.entriesWritten < status.totalEntries) return
        lastProgressPost[uri] = now
        post(idFor(uri), progressNotification(uri, status))
    }

    private fun post(
        id: Int,
        notification: Notification,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private fun idFor(uri: String): Int = notificationIds.getOrPut(uri) { FIRST_EXTRACTION_ID + notificationIds.size }

    private fun progressNotification(
        uri: String,
        status: ExtractionStatus.Running,
    ): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_extracting_title, status.archiveName))
            .setContentText(getString(R.string.unzip_extracting_progress, status.entriesWritten, status.totalEntries))
            .setProgress(status.totalEntries, status.entriesWritten, status.totalEntries == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.unzip_cancel), cancelIntent(uri))
            .build()

    private fun resultNotification(
        uri: String,
        status: ExtractionStatus,
    ): Notification {
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_RESULTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
        val name = status.archiveName
        when (status) {
            is ExtractionStatus.Finished ->
                when (val result = status.result) {
                    is ArchiveResult.Success -> {
                        val files = result.value.filesWritten
                        builder
                            .setContentTitle(getString(R.string.notification_extracted_title, name))
                            .setContentText(
                                resources.getQuantityString(R.plurals.unzip_extracted, files, files) + " " +
                                    getString(R.string.notification_tap_to_open),
                            )
                        folderViewIntent(status.folderUri)?.let { folder ->
                            builder.setContentIntent(
                                PendingIntent.getActivity(this, idFor(uri), folder, PENDING_FLAGS),
                            )
                        }
                    }
                    is ArchiveResult.Failure ->
                        builder
                            .setContentTitle(getString(R.string.notification_failed_title, name))
                            .setContentText(getString(result.error.messageRes()))
                }
            is ExtractionStatus.Cancelled ->
                builder
                    .setContentTitle(getString(R.string.notification_cancelled_title, name))
                    .setContentText(getString(R.string.unzip_cancelled))
            is ExtractionStatus.Running -> Unit
        }
        return builder.build()
    }

    private fun placeholderNotification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setSilent(true)
            .build()

    private fun openAppIntent(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PENDING_FLAGS)
        }

    private fun cancelIntent(uri: String): PendingIntent =
        PendingIntent.getService(
            this,
            idFor(uri),
            Intent(this, ExtractionService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ARCHIVE_URI, uri),
            PENDING_FLAGS,
        )

    companion object {
        const val CHANNEL_PROGRESS = "extraction_progress"
        const val CHANNEL_RESULTS = "extraction_results"
        private const val ACTION_CANCEL = "com.rsebade.adfreeunzip.action.CANCEL_EXTRACTION"
        private const val EXTRA_ARCHIVE_URI = "archive_uri"
        private const val PLACEHOLDER_ID = 1
        private const val FIRST_EXTRACTION_ID = 2
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        private val FOREGROUND_TYPE =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
    }
}
