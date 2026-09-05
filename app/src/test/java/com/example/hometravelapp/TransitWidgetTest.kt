package com.example.hometravelapp

import com.example.hometravelapp.widget.WidgetDestinationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitWidgetTest {

    @Test
    fun testWidgetSummaryProperties() {
        val summary = WidgetDestinationSummary(
            id = "kamppi",
            name = "Kamppi",
            emoji = "🛍️",
            stopName = "Kamppi (M)",
            distanceMeters = 60,
            routeBadge = "M1",
            headsign = "Kivenlahti",
            countdownText = "2 min",
            isRealtime = true
        )

        assertEquals("Kamppi", summary.name)
        assertEquals("🛍️", summary.emoji)
        assertEquals("Kamppi (M)", summary.stopName)
        assertEquals(60, summary.distanceMeters)
        assertEquals("M1", summary.routeBadge)
        assertEquals("Kivenlahti", summary.headsign)
        assertEquals("2 min", summary.countdownText)
        assertTrue(summary.isRealtime)
    }
}
