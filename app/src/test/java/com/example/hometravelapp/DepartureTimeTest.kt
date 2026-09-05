package com.example.hometravelapp

import com.example.hometravelapp.data.model.Departure
import com.example.hometravelapp.data.model.NearestResponseEnvelope
import com.example.hometravelapp.data.model.VehicleMode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepartureTimeTest {

    @Test
    fun testImmediateDepartureShowsNow() {
        val now = 1700000000L
        val departure = Departure(
            routeShortName = "7",
            headsign = "Länsiterminaali",
            departureEpochSeconds = now + 10,
            isRealtime = true
        )
        assertEquals("Nyt", departure.formattedDepartureTime(currentEpochSeconds = now))
    }

    @Test
    fun testFiveMinuteCountdown() {
        val now = 1700000000L
        val departure = Departure(
            routeShortName = "4",
            headsign = "Munkkiniemi",
            departureEpochSeconds = now + 300,
            isRealtime = true
        )
        assertEquals("5 min", departure.formattedDepartureTime(currentEpochSeconds = now))
    }

    @Test
    fun testVehicleModeParsing() {
        assertEquals(VehicleMode.BUS, VehicleMode.fromString("BUS"))
        assertEquals(VehicleMode.TRAM, VehicleMode.fromString("TRAM"))
        assertEquals(VehicleMode.SUBWAY, VehicleMode.fromString("SUBWAY"))
        assertEquals(VehicleMode.SUBWAY, VehicleMode.fromString("METRO"))
        assertEquals(VehicleMode.RAIL, VehicleMode.fromString("RAIL"))
        assertEquals(VehicleMode.FERRY, VehicleMode.fromString("FERRY"))
        assertEquals(VehicleMode.OTHER, VehicleMode.fromString("AIRPLANE"))
    }

    @Test
    fun testJsonParsingForNearestStops() {
        val sampleJson = """
            {
              "data": {
                "nearest": {
                  "edges": [
                    {
                      "node": {
                        "distance": 85,
                        "place": {
                          "gtfsId": "HSL:1020454",
                          "name": "Rautatientori",
                          "code": "H2054",
                          "vehicleMode": "TRAM",
                          "stoptimesWithoutPatterns": [
                            {
                              "scheduledDeparture": 36000,
                              "realtimeDeparture": 36120,
                              "serviceDay": 1700000000,
                              "realtime": true,
                              "headsign": "Länsiterminaali T2",
                              "trip": {
                                "routeShortName": "7"
                              }
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val envelope = json.decodeFromString<NearestResponseEnvelope>(sampleJson)

        assertNotNull(envelope.data)
        val edge = envelope.data!!.nearest!!.edges.first()
        assertEquals(85, edge.node!!.distance)
        assertEquals("Rautatientori", edge.node!!.place!!.name)
        assertEquals("7", edge.node!!.place!!.stoptimesWithoutPatterns.first().trip!!.routeShortName)
        assertTrue(edge.node!!.place!!.stoptimesWithoutPatterns.first().realtime)
    }

    @Test
    fun testJsonParsingWithNestedRouteObject() {
        val sampleJson = """
            {
              "data": {
                "nearest": {
                  "edges": [
                    {
                      "node": {
                        "distance": 120,
                        "place": {
                          "gtfsId": "HSL:1040101",
                          "name": "Kamppi (M)",
                          "code": "M1",
                          "vehicleMode": "SUBWAY",
                          "stoptimesWithoutPatterns": [
                            {
                              "scheduledDeparture": 40000,
                              "realtimeDeparture": 40060,
                              "serviceDay": 1700000000,
                              "realtime": true,
                              "headsign": "Vuosaari",
                              "trip": {
                                "route": {
                                  "shortName": "M1"
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val envelope = json.decodeFromString<NearestResponseEnvelope>(sampleJson)

        assertNotNull(envelope.data)
        val st = envelope.data!!.nearest!!.edges.first().node!!.place!!.stoptimesWithoutPatterns.first()
        assertEquals("M1", st.trip?.route?.shortName)
        assertEquals("Vuosaari", st.headsign)
    }
}
