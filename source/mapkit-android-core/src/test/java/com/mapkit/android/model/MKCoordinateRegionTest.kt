package com.studiomk.mapkit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MKCoordinateRegionTest {

    @Test
    fun fromCenter_createsRegionWithGivenCenterAndSpan() {
        val center = MKCoordinate(35.681236, 139.767125)

        val region = MKCoordinateRegion.fromCenter(
            center = center,
            latitudeDelta = 0.05,
            longitudeDelta = 0.08
        )

        assertEquals(35.681236, region.center.latitude, 0.0)
        assertEquals(139.767125, region.center.longitude, 0.0)
        assertEquals(0.05, region.span.latitudeDelta, 0.0)
        assertEquals(0.08, region.span.longitudeDelta, 0.0)
    }

    @Test
    fun fromCoordinates_calculatesCenterAndSpanFromBounds() {
        val points = listOf(
            MKCoordinate(35.0, 139.0),
            MKCoordinate(36.0, 140.0),
            MKCoordinate(34.5, 141.0)
        )

        val region = MKCoordinateRegion.fromCoordinates(points)

        assertEquals(35.25, region.center.latitude, 1e-9)
        assertEquals(140.0, region.center.longitude, 1e-9)
        assertEquals(1.5, region.span.latitudeDelta, 1e-9)
        assertEquals(2.0, region.span.longitudeDelta, 1e-9)
    }

    @Test
    fun fromCoordinates_addsEdgePaddingToSpan() {
        val points = listOf(
            MKCoordinate(35.0, 139.0),
            MKCoordinate(36.0, 140.0)
        )

        val region = MKCoordinateRegion.fromCoordinates(
            coordinates = points,
            edgePadding = MKEdgePadding(
                top = 0.1,
                left = 0.2,
                bottom = 0.3,
                right = 0.4
            )
        )

        assertEquals(1.4, region.span.latitudeDelta, 1e-9)
        assertEquals(1.6, region.span.longitudeDelta, 1e-9)
    }

    @Test
    fun regionAdjustmentRequest_requiresPositiveWidth() {
        try {
            MKRegionAdjustmentRequest(
                region = MKCoordinateRegion.fromCenter(MKCoordinate(35.0, 139.0), 0.1, 0.1),
                widthPx = 0,
                heightPx = 420
            )
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertEquals("widthPx must be > 0: 0", expected.message)
        }
    }

    @Test
    fun regionAdjustmentRequest_requiresPositiveHeight() {
        try {
            MKRegionAdjustmentRequest(
                region = MKCoordinateRegion.fromCenter(MKCoordinate(35.0, 139.0), 0.1, 0.1),
                widthPx = 594,
                heightPx = -1
            )
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertEquals("heightPx must be > 0: -1", expected.message)
        }
    }
}
