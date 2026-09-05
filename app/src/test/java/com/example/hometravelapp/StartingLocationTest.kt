package com.example.hometravelapp

import com.example.hometravelapp.data.model.StartingLocation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartingLocationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun testDefaultStartingLocationIsGps() {
        val defaultLoc = StartingLocation()
        assertEquals("Nykyinen sijainti", defaultLoc.name)
        assertFalse(defaultLoc.isManual)
    }

    @Test
    fun testManualStartingLocationSerialization() {
        val manual = StartingLocation(
            name = "Kamppi",
            latitude = 60.1687,
            longitude = 24.9332,
            isManual = true
        )

        val serialized = json.encodeToString(manual)
        val deserialized = json.decodeFromString<StartingLocation>(serialized)

        assertEquals("Kamppi", deserialized.name)
        assertEquals(60.1687, deserialized.latitude, 0.0001)
        assertEquals(24.9332, deserialized.longitude, 0.0001)
        assertTrue(deserialized.isManual)
    }

    @Test
    fun testStartingLocationCustomCoordinates() {
        val custom = StartingLocation(
            name = "Rautatientori",
            latitude = 60.1700,
            longitude = 24.9414,
            isManual = true
        )
        assertEquals("Rautatientori", custom.name)
        assertTrue(custom.isManual)
        assertEquals(60.1700, custom.latitude, 0.0001)
        assertEquals(24.9414, custom.longitude, 0.0001)
    }
}
