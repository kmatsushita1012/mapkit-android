package com.studiomk.mapkit.api

import android.content.Context
import android.view.ViewGroup
import com.studiomk.mapkit.model.MKMapController
import com.studiomk.mapkit.webview.MKBridgeWebView

/**
 * Keeps one MapKit WebView alive while different MKMapView composables take turns displaying it.
 * Create this with an Activity context and close it from that Activity's onDestroy.
 */
class MKMapSession(context: Context) : AutoCloseable {
    private val webView = MKBridgeWebView(context, mapKitConfig = MKMapKit.currentConfig())
    private var owner: Any? = null
    private var controller: MKMapController? = null
    private var isClosed = false

    internal fun acquire(nextOwner: Any): MKBridgeWebView {
        check(!isClosed) { "MKMapSession is closed" }
        if (owner !== nextOwner) {
            controller?.clearCommandDispatcher()
            controller = null
            webView.setEventListener { }
            owner = nextOwner
        }
        (webView.parent as? ViewGroup)?.removeView(webView)
        return webView
    }

    internal fun owns(candidate: Any): Boolean = !isClosed && owner === candidate

    internal fun bind(candidate: Any, nextController: MKMapController) {
        if (!owns(candidate)) return
        if (controller !== nextController) {
            controller?.clearCommandDispatcher()
            controller = nextController
        }
        nextController.bindCommandDispatcher { command -> webView.applyCommand(command) }
    }

    internal fun release(candidate: Any) {
        if (!owns(candidate)) return
        controller?.clearCommandDispatcher()
        controller = null
        webView.setEventListener { }
        owner = null
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        controller?.clearCommandDispatcher()
        controller = null
        owner = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}
