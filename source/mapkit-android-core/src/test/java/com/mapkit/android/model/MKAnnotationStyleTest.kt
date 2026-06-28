package com.studiomk.mapkit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MKAnnotationStyleTest {

    @Test
    fun domStyle_defaultsMatchContract() {
        val style = MKAnnotationStyle.Dom(html = "<div>980</div>")

        assertEquals("<div>980</div>", style.html)
        assertEquals(0.5, style.anchorX, 0.0)
        assertEquals(1.0, style.anchorY, 0.0)
        assertTrue(style.allowSelection)
    }
}
