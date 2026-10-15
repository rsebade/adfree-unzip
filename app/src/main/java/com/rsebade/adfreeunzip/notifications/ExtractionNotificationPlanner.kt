package com.rsebade.adfreeunzip.notifications

import com.rsebade.adfreeunzip.domain.ExtractionStatus

/** What the notification shade should show after one change in extraction statuses. */
data class NotificationPlan(
    /** Extractions to show a progress notification (with Cancel) for, keyed by archive URI. */
    val inProgress: Map<String, ExtractionStatus.Running>,
    /** Extractions that just ended while the app wasn't visible: replace their progress with the result. */
    val toAnnounce: Map<String, ExtractionStatus>,
    /** Extractions that just ended while the app was visible: the screen shows the result, so just remove them. */
    val toDismiss: Set<String>,
) {
    /** Nothing is running any more, so the foreground service can stop. */
    val isIdle: Boolean get() = inProgress.isEmpty()
}

/**
 * Turns the runner's statuses into notification changes. Kept free of Android types so the rules
 * are unit-tested; the service only renders the plan. Only extractions it saw running are
 * announced, so results from before the service started aren't re-posted.
 */
class ExtractionNotificationPlanner {
    private val running = mutableSetOf<String>()

    fun plan(
        statuses: Map<String, ExtractionStatus>,
        appVisible: Boolean,
    ): NotificationPlan {
        val inProgress =
            statuses.mapNotNull { (uri, status) -> (status as? ExtractionStatus.Running)?.let { uri to it } }.toMap()
        val ended = (running - inProgress.keys).mapNotNull { uri -> statuses[uri]?.let { uri to it } }.toMap()
        running.clear()
        running += inProgress.keys
        return NotificationPlan(
            inProgress = inProgress,
            toAnnounce = if (appVisible) emptyMap() else ended,
            toDismiss = if (appVisible) ended.keys else emptySet(),
        )
    }
}
