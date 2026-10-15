package com.rsebade.adfreeunzip.notifications

import com.rsebade.adfreeunzip.domain.ArchiveResult
import com.rsebade.adfreeunzip.domain.ExtractionStatus
import com.rsebade.adfreeunzip.domain.ExtractionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionNotificationPlannerTest {
    private val planner = ExtractionNotificationPlanner()
    private val running = ExtractionStatus.Running("a.zip", "tree", 1, 4)
    private val finished =
        ExtractionStatus.Finished("a.zip", "tree", ArchiveResult.Success(ExtractionSummary(4, 0, emptyList())))

    @Test
    fun `running extractions get progress notifications`() {
        val plan = planner.plan(mapOf("a" to running), appVisible = true)

        assertEquals(mapOf("a" to running), plan.inProgress)
        assertFalse(plan.isIdle)
    }

    @Test
    fun `an extraction ending in the background is announced`() {
        planner.plan(mapOf("a" to running), appVisible = false)

        val plan = planner.plan(mapOf("a" to finished), appVisible = false)

        assertEquals(mapOf("a" to finished), plan.toAnnounce)
        assertEquals(emptySet<String>(), plan.toDismiss)
        assertTrue(plan.isIdle)
    }

    @Test
    fun `an extraction ending while the app is visible is only dismissed`() {
        planner.plan(mapOf("a" to running), appVisible = true)

        val plan = planner.plan(mapOf("a" to finished), appVisible = true)

        assertEquals(emptyMap<String, ExtractionStatus>(), plan.toAnnounce)
        assertEquals(setOf("a"), plan.toDismiss)
    }

    @Test
    fun `results from before it started watching are not announced again`() {
        val plan = planner.plan(mapOf("a" to finished), appVisible = false)

        assertEquals(emptyMap<String, ExtractionStatus>(), plan.toAnnounce)
        assertTrue(plan.isIdle)
    }

    @Test
    fun `one extraction ending leaves the others in progress`() {
        val other = ExtractionStatus.Running("b.zip", "tree", 0, 2)
        planner.plan(mapOf("a" to running, "b" to other), appVisible = false)

        val plan = planner.plan(mapOf("a" to finished, "b" to other), appVisible = false)

        assertEquals(mapOf("b" to other), plan.inProgress)
        assertEquals(mapOf("a" to finished), plan.toAnnounce)
        assertFalse(plan.isIdle)
    }
}
