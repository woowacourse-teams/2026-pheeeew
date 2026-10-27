package com.pheeeew.core.notification

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidNotificationPermissionRequestTest {
    @Test
    fun rationaleFromAnEarlierAppVersionIsRecordedBeforeTheNextRequest() {
        val history = AndroidNotificationPermissionHistory()
        history.observeRationale(showsRationale = true)
        history.onResult(granted = false, showsRationale = false)
        assertFalse(history.canRequest(showsRationale = false))
    }

    @Test
    fun cancelledWaiterDoesNotLaunchAnotherDialogOrLoseTheOutstandingResult() =
        runTest {
            val requests = AndroidNotificationPermissionRequest()
            var launches = 0
            val first = launch { requests.request { launches++ } }
            runCurrent()
            first.cancelAndJoin()
            val second = launch { requests.request { launches++ } }
            runCurrent()
            assertEquals(1, launches)
            assertTrue(second.isActive)
            requests.complete()
            second.join()
            val third = launch { requests.request { launches++ } }
            runCurrent()
            assertEquals(2, launches)
            assertTrue(third.isActive)
            requests.complete()
            third.join()
        }

    @Test
    fun finalOwnerDisposalCancelsAllWaiters() =
        runTest {
            val requests = AndroidNotificationPermissionRequest()
            val first = launch { requests.request {} }
            val second = launch { requests.request { error("duplicate launch") } }
            runCurrent()
            requests.cancel()
            first.join()
            second.join()
            assertTrue(first.isCancelled)
            assertTrue(second.isCancelled)
        }

    @Test
    fun launchFailureReleasesTheRequestForRetry() =
        runTest {
            val requests = AndroidNotificationPermissionRequest()
            assertFailsWith<IllegalStateException> { requests.request { error("launch unavailable") } }
            val retry = launch { requests.request {} }
            runCurrent()
            assertTrue(retry.isActive)
            requests.complete()
            retry.join()
        }

    @Test
    fun dismissalWithoutRationaleRemainsRequestable() {
        val history = AndroidNotificationPermissionHistory()
        repeat(3) {
            history.onResult(granted = false, showsRationale = false)
            assertTrue(history.canRequest(showsRationale = false))
        }
    }

    @Test
    fun confirmedDenialThenSuppressedRationaleRequiresSettingsAndSurvivesRecreation() {
        val history = AndroidNotificationPermissionHistory()
        history.onResult(granted = false, showsRationale = true)
        assertTrue(history.canRequest(showsRationale = true))
        history.onResult(granted = false, showsRationale = false)
        val restored = AndroidNotificationPermissionHistory(history.hasObservedRationale)
        assertFalse(restored.canRequest(showsRationale = false))
        restored.onResult(granted = true, showsRationale = false)
        assertTrue(restored.canRequest(showsRationale = false))
    }
}
