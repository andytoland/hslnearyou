package com.example.hometravelapp

import com.example.hometravelapp.data.model.GeocodingResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GeocodingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun testParseDigitransitGeocodingResponse() {
        val sampleGeoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [
                      24.9406,
                      60.1698
                    ]
                  },
                  "properties": {
                    "id": "oa:address:123",
                    "name": "Mannerheimintie 1",
                    "label": "Mannerheimintie 1, Helsinki"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [
                      25.0818,
                      60.2117
                    ]
                  },
                  "properties": {
                    "id": "venue:itis",
                    "name": "Itis",
                    "label": "Kauppakeskus Itis, Helsinki"
                  }
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<GeocodingResponse>(sampleGeoJson)
        assertNotNull(response)
        assertEquals(2, response.features.size)

        val first = response.features[0]
        assertEquals("Mannerheimintie 1, Helsinki", first.properties.label)
        assertEquals("Mannerheimintie 1", first.properties.name)
        // GeoJSON specifies [longitude, latitude]
        assertEquals(24.9406, first.geometry.coordinates[0], 0.0001)
        assertEquals(60.1698, first.geometry.coordinates[1], 0.0001)

        val second = response.features[1]
        assertEquals("Kauppakeskus Itis, Helsinki", second.properties.label)
        assertEquals(25.0818, second.geometry.coordinates[0], 0.0001)
        assertEquals(60.2117, second.geometry.coordinates[1], 0.0001)
    }
}
