package com.studiomk.mapkit.webview.internal

import com.studiomk.mapkit.model.MKAnnotationStyle
import com.studiomk.mapkit.model.MKImageSource

internal object AnnotationStyleJsonSerializer {
    fun serialize(style: MKAnnotationStyle): Map<String, Any?> {
        return when (style) {
            is MKAnnotationStyle.Marker -> mapOf(
                "kind" to "MKAnnotationStyleMarker",
                "tintHex" to style.tintHex,
                "glyphText" to style.glyphText,
                "glyphImageSource" to style.glyphImageSource?.let { serializeImageSource(it) }
            )

            is MKAnnotationStyle.Image -> mapOf(
                "kind" to "MKAnnotationStyleImage",
                "source" to serializeImageSource(style.source),
                "widthDp" to style.widthDp,
                "heightDp" to style.heightDp,
                "anchorX" to style.anchorX,
                "anchorY" to style.anchorY
            )

            is MKAnnotationStyle.Dom -> mapOf(
                "kind" to "MKAnnotationStyleDom",
                "html" to style.html,
                "anchorX" to style.anchorX,
                "anchorY" to style.anchorY,
                "allowSelection" to style.allowSelection
            )
        }
    }

    private fun serializeImageSource(source: MKImageSource): Map<String, Any?> {
        return when (source) {
            is MKImageSource.Url -> mapOf(
                "kind" to "MKImageSourceUrl",
                "value" to source.value
            )

            is MKImageSource.Base64Png -> mapOf(
                "kind" to "MKImageSourceBase64Png",
                "value" to source.value
            )

            is MKImageSource.ResourceName -> mapOf(
                "kind" to "MKImageSourceResourceName",
                "value" to source.value
            )
        }
    }
}
