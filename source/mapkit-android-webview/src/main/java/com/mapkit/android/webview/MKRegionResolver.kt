package com.studiomk.mapkit.webview

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.studiomk.mapkit.api.MKMapKit
import com.studiomk.mapkit.model.MKCoordinateRegion
import com.studiomk.mapkit.model.MKMapErrorCause
import com.studiomk.mapkit.model.MKMapEvent
import com.studiomk.mapkit.model.MKRegionAdjuster
import com.studiomk.mapkit.model.MKRegionAdjustmentRequest
import com.studiomk.mapkit.model.MKRegionResolutionException
import kotlin.coroutines.suspendCoroutine

object MKRegionResolver {
    suspend fun resolve(
        activity: Activity,
        request: MKRegionAdjustmentRequest
    ): MKCoordinateRegion = suspendCoroutine { continuation ->
        RegionResolutionSession(
            activity = activity,
            request = request
        ) { result ->
            continuation.resumeWith(result)
        }.start()
    }
}

internal fun interface RegionResolutionDelegate {
    suspend fun resolve(
        activityProvider: () -> Activity,
        request: MKRegionAdjustmentRequest
    ): MKCoordinateRegion
}

class ActivityBoundMKRegionAdjuster(
    private val activityProvider: () -> Activity
) : MKRegionAdjuster {
    internal constructor(
        activityProvider: () -> Activity,
        delegate: RegionResolutionDelegate
    ) : this(activityProvider) {
        this.delegate = delegate
    }

    private var delegate: RegionResolutionDelegate = RegionResolutionDelegate { provider, request ->
        MKRegionResolver.resolve(provider(), request)
    }

    override suspend fun adjust(request: MKRegionAdjustmentRequest): MKCoordinateRegion {
        return delegate.resolve(activityProvider, request)
    }
}

private class RegionResolutionSession(
    private val activity: Activity,
    private val request: MKRegionAdjustmentRequest,
    private val complete: (Result<MKCoordinateRegion>) -> Unit
) {
    companion object {
        private const val SESSION_TIMEOUT_MS = 6_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isCompleted = false
    private var hostView: FrameLayout? = null
    private var webView: MKBridgeWebView? = null
    private var resolutionStarted = false
    private val timeoutRunnable = Runnable {
        finish(
            Result.failure(
                MKRegionResolutionException.Timeout(
                    phase = if (resolutionStarted) "adjustedRegion" else "mapInitialization",
                    timeoutMs = SESSION_TIMEOUT_MS
                )
            )
        )
    }

    fun start() {
        if (Looper.myLooper() === Looper.getMainLooper()) {
            startOnMainThread()
        } else {
            mainHandler.post { startOnMainThread() }
        }
    }

    private fun startOnMainThread() {
        mainHandler.postDelayed(timeoutRunnable, SESSION_TIMEOUT_MS)
        val token = MKMapKit.currentTokenOrNull()
        if (token.isNullOrBlank()) {
            finish(Result.failure(MKRegionResolutionException.NotInitialized))
            return
        }
        if (request.widthPx <= 0 || request.heightPx <= 0) {
            finish(
                Result.failure(
                    MKRegionResolutionException.InvalidSize(
                        widthPx = request.widthPx,
                        heightPx = request.heightPx
                    )
                )
            )
            return
        }

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        if (contentRoot == null) {
            finish(
                Result.failure(
                    MKRegionResolutionException.BridgeFailure("Activity content root is unavailable")
                )
            )
            return
        }

        val config = MKMapKit.currentConfig()
        val nextHost = FrameLayout(activity).apply {
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            clipChildren = true
            clipToPadding = true
            layoutParams = FrameLayout.LayoutParams(request.widthPx, request.heightPx).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        }
        val nextWebView = MKBridgeWebView(activity, mapKitConfig = config).apply {
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(request.widthPx, request.heightPx)
            setEventListener { event ->
                when (event) {
                    MKMapEvent.MapLoaded -> startResolutionIfNeeded()
                    is MKMapEvent.MapError -> finish(Result.failure(event.cause.toResolutionException()))
                    else -> Unit
                }
            }
        }

        hostView = nextHost
        webView = nextWebView
        nextHost.addView(nextWebView)
        contentRoot.addView(nextHost)
        nextWebView.ensureInitialized(token)
    }

    private fun startResolutionIfNeeded() {
        if (resolutionStarted) return
        resolutionStarted = true
        val currentWebView = webView ?: run {
            finish(Result.failure(MKRegionResolutionException.BridgeFailure("Region resolver WebView is missing")))
            return
        }
        currentWebView.resolveAdjustedRegion(request.region) { result ->
            finish(result)
        }
    }

    private fun finish(result: Result<MKCoordinateRegion>) {
        if (isCompleted) return
        isCompleted = true

        val cleanup = {
            mainHandler.removeCallbacks(timeoutRunnable)
            webView?.setEventListener { }
            hostView?.removeAllViews()
            (hostView?.parent as? ViewGroup)?.removeView(hostView)
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            hostView = null
            complete(result)
        }

        if (Looper.myLooper() === Looper.getMainLooper()) {
            cleanup()
        } else {
            mainHandler.post(cleanup)
        }
    }
}

private fun MKMapErrorCause.toResolutionException(): MKRegionResolutionException {
    return when (this) {
        MKMapErrorCause.NotInitialized,
        MKMapErrorCause.TokenUnavailable -> MKRegionResolutionException.NotInitialized
        is MKMapErrorCause.BridgeFailure -> MKRegionResolutionException.BridgeFailure(message)
    }
}
