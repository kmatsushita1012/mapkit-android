package com.studiomk.mapkit.webview

import com.studiomk.mapkit.model.MKCoordinate
import com.studiomk.mapkit.model.MKCoordinateRegion
import com.studiomk.mapkit.model.MKRegionAdjustmentRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class ActivityBoundMKRegionAdjusterTest {

    @Test
    fun adjust_delegatesToResolver() {
        val request = MKRegionAdjustmentRequest(
            region = MKCoordinateRegion.fromCenter(
                center = MKCoordinate(35.0, 139.0),
                latitudeDelta = 0.1,
                longitudeDelta = 0.2
            ),
            widthPx = 594,
            heightPx = 420
        )
        val expected = MKCoordinateRegion.fromCenter(
            center = MKCoordinate(35.1, 139.1),
            latitudeDelta = 0.3,
            longitudeDelta = 0.4
        )
        var capturedRequest: MKRegionAdjustmentRequest? = null
        val adjuster = ActivityBoundMKRegionAdjuster(
            activityProvider = { error("activityProvider should not be invoked in this test") },
            delegate = RegionResolutionDelegate { _, nextRequest ->
                capturedRequest = nextRequest
                expected
            }
        )

        val actual = runSuspend { adjuster.adjust(request) }

        assertEquals(request, capturedRequest)
        assertEquals(expected, actual)
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(resumeResult: Result<T>) {
                result = resumeResult
            }
        }
    )
    val finalResult = result ?: error("suspend block did not complete")
    return finalResult.getOrThrow()
}
