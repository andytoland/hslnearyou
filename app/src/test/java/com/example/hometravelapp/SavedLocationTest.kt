package com.example.hometravelapp

import com.example.hometravelapp.data.model.LocationType
import com.example.hometravelapp.data.model.SavedLocation
import com.example.hometravelapp.data.repository.TransitRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedLocationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun testDefaultLocationsContainGpsHomeWorkMall() {
        val defaults = TransitRepository.DEFAULT_LOCATIONS
        assertTrue(defaults.any { it.isGps })
        assertTrue(defaults.any { it.type == LocationType.HOME })
        assertTrue(defaults.any { it.type == LocationType.WORK })
        assertTrue(defaults.any { it.type == LocationType.MALL && it.name == "Kamppi" })
        assertTrue(defaults.any { it.type == LocationType.MALL && it.name == "Tripla" })
    }

    @Test
    fun testSavedLocationJsonRoundTrip() {
        val location = SavedLocation(
            id = "test-123",
            name = "Itis Kauppakeskus",
            latitude = 60.2117,
            longitude = 25.0818,
            type = LocationType.MALL
        )

        val serialized = json.encodeToString(location)
        val deserialized = json.decodeFromString<SavedLocation>(serialized)

        assertEquals("test-123", deserialized.id)
        assertEquals("Itis Kauppakeskus", deserialized.name)
        assertEquals(60.2117, deserialized.latitude, 0.0001)
        assertEquals(25.0818, deserialized.longitude, 0.0001)
        assertEquals(LocationType.MALL, deserialized.type)
        assertFalse(deserialized.isGps)
    }

    @Test
    fun testGpsLocationFlag() {
        val gps = TransitRepository.GPS_LOCATION
        assertTrue(gps.isGps)
        assertEquals(LocationType.CURRENT_GPS, gps.type)
    }
}
