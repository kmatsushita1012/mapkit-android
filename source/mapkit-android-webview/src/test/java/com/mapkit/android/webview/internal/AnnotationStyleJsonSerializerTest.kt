package com.studiomk.mapkit.webview.internal

import com.studiomk.mapkit.model.MKAnnotationStyle
import com.studiomk.mapkit.model.MKImageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationStyleJsonSerializerTest {

    @Test
    fun serialize_domStyle_matchesBridgeContract() {
        val json = AnnotationStyleJsonSerializer.serialize(
            MKAnnotationStyle.Dom(
                html = "<div style=\"white-space:nowrap;\">980</div>",
                anchorX = 0.25,
                anchorY = 0.75,
                allowSelection = false
            )
        )

        assertEquals("MKAnnotationStyleDom", json["kind"])
        assertEquals("<div style=\"white-space:nowrap;\">980</div>", json["html"])
        assertEquals(0.25, json["anchorX"] as Double, 0.0)
        assertEquals(0.75, json["anchorY"] as Double, 0.0)
        assertFalse(json["allowSelection"] as Boolean)
    }

    @Test
    fun serialize_markerAndImageStyles_remainCompatible() {
        val marker = AnnotationStyleJsonSerializer.serialize(
            MKAnnotationStyle.Marker(
                tintHex = "#112233",
                glyphText = "A",
                glyphImageSource = MKImageSource.ResourceName("pin")
            )
        )
        val image = AnnotationStyleJsonSerializer.serialize(
            MKAnnotationStyle.Image(
                source = MKImageSource.Url("https://example.com/pin.png"),
                widthDp = 48,
                heightDp = 64,
                anchorX = 0.4,
                anchorY = 0.9
            )
        )

        assertEquals("MKAnnotationStyleMarker", marker["kind"])
        assertEquals("#112233", marker["tintHex"])
        assertEquals("A", marker["glyphText"])
        val markerImageSource = marker["glyphImageSource"] as Map<*, *>
        assertEquals(
            "MKImageSourceResourceName",
            markerImageSource["kind"]
        )

        assertEquals("MKAnnotationStyleImage", image["kind"])
        val imageSource = image["source"] as Map<*, *>
        assertEquals("https://example.com/pin.png", imageSource["value"])
        assertEquals(48, image["widthDp"])
        assertEquals(64, image["heightDp"])
        assertTrue(image.containsKey("anchorX"))
        assertTrue(image.containsKey("anchorY"))
    }
}
