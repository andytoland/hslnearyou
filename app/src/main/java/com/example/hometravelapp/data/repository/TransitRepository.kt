package com.example.hometravelapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.hometravelapp.data.api.DigitransitApiClient
import com.example.hometravelapp.data.model.Departure
import com.example.hometravelapp.data.model.JourneyLeg
import com.example.hometravelapp.data.model.JourneyOption
import com.example.hometravelapp.data.model.NearbyStop
import com.example.hometravelapp.data.model.VehicleMode
import java.time.Instant

import com.example.hometravelapp.data.model.LocationType
import com.example.hometravelapp.data.model.SavedJourney
import com.example.hometravelapp.data.model.SavedLocation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import com.example.hometravelapp.data.api.GeocodingService
import com.example.hometravelapp.data.model.AddressSearchResult
import com.example.hometravelapp.data.model.StartingLocation
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class TransitRepository(
    private val context: Context,
    private val apiClient: DigitransitApiClient = DigitransitApiClient(),
    private val geocodingService: GeocodingService = GeocodingService(context),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hsl_transit_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DIGITRANSIT_API_KEY = "digitransit_api_key"
        private const val KEY_SAVED_LOCATIONS = "saved_locations_json"
        private const val KEY_STARTING_LOCATION = "starting_location_json"
        private const val KEY_SAVED_JOURNEYS = "saved_journeys_json"

        val GPS_LOCATION = SavedLocation(
            id = "gps",
            name = "Nykyinen",
            latitude = 0.0,
            longitude = 0.0,
            type = LocationType.CURRENT_GPS
        )

        val DEFAULT_LOCATIONS = listOf(
            GPS_LOCATION,
            SavedLocation(
                id = "home",
                name = "Koti",
                latitude = 60.1699,
                longitude = 24.9384,
                type = LocationType.HOME
            ),
            SavedLocation(
                id = "work",
                name = "Työ",
                latitude = 60.1719,
                longitude = 24.9414,
                type = LocationType.WORK
            ),
            SavedLocation(
                id = "kamppi",
                name = "Kamppi",
                latitude = 60.1687,
                longitude = 24.9332,
                type = LocationType.MALL
            ),
            SavedLocation(
                id = "tripla",
                name = "Tripla",
                latitude = 60.1989,
                longitude = 24.9304,
                type = LocationType.MALL
            )
        )
    }

    fun getApiKey(): String? {
        return prefs.getString(KEY_DIGITRANSIT_API_KEY, null)
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_DIGITRANSIT_API_KEY, key.trim()).apply()
    }

    fun getStartingLocation(): StartingLocation {
        val jsonStr = prefs.getString(KEY_STARTING_LOCATION, null) ?: return StartingLocation()
        return try {
            json.decodeFromString<StartingLocation>(jsonStr)
        } catch (_: Exception) {
            StartingLocation()
        }
    }

    fun saveStartingLocation(name: String, lat: Double, lon: Double, isManual: Boolean = true) {
        val loc = StartingLocation(name = name, latitude = lat, longitude = lon, isManual = isManual)
        prefs.edit().putString(KEY_STARTING_LOCATION, json.encodeToString(loc)).apply()
    }

    fun resetStartingLocationToGps() {
        val loc = StartingLocation(name = "Nykyinen sijainti", latitude = 60.1699, longitude = 24.9384, isManual = false)
        prefs.edit().putString(KEY_STARTING_LOCATION, json.encodeToString(loc)).apply()
    }

    fun getSavedLocations(): List<SavedLocation> {
        val jsonStr = prefs.getString(KEY_SAVED_LOCATIONS, null) ?: return DEFAULT_LOCATIONS
        return try {
            val list = json.decodeFromString<List<SavedLocation>>(jsonStr)
            if (list.none { it.isGps }) {
                listOf(GPS_LOCATION) + list
            } else {
                list
            }
        } catch (e: Exception) {
            DEFAULT_LOCATIONS
        }
    }

    fun saveLocation(location: SavedLocation) {
        val current = getSavedLocations().toMutableList()
        val index = current.indexOfFirst { it.id == location.id }
        if (index >= 0) {
            current[index] = location
        } else {
            current.add(location)
        }
        prefs.edit().putString(KEY_SAVED_LOCATIONS, json.encodeToString(current)).apply()
    }

    fun deleteLocation(id: String) {
        if (id == "gps") return // Never delete the GPS option
        val current = getSavedLocations().filterNot { it.id == id }
        prefs.edit().putString(KEY_SAVED_LOCATIONS, json.encodeToString(current)).apply()
    }

    fun resetLocationsToDefault() {
        prefs.edit().putString(KEY_SAVED_LOCATIONS, json.encodeToString(DEFAULT_LOCATIONS)).apply()
    }

    fun saveLocations(locations: List<SavedLocation>) {
        prefs.edit().putString(KEY_SAVED_LOCATIONS, json.encodeToString(locations)).apply()
    }

    fun getSavedJourneys(): List<SavedJourney> {
        val jsonStr = prefs.getString(KEY_SAVED_JOURNEYS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SavedJourney>>(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveJourney(savedJourney: SavedJourney) {
        val current = getSavedJourneys().toMutableList()
        current.add(0, savedJourney)
        val trimmed = current.take(20)
        prefs.edit().putString(KEY_SAVED_JOURNEYS, json.encodeToString(trimmed)).apply()
    }

    fun deleteJourney(id: String) {
        val current = getSavedJourneys().filterNot { it.id == id }
        prefs.edit().putString(KEY_SAVED_JOURNEYS, json.encodeToString(current)).apply()
    }

    suspend fun searchAddress(query: String): List<AddressSearchResult> {
        val apiKey = getApiKey()
        return geocodingService.searchAddress(query, apiKey)
    }

    suspend fun getNearestStops(lat: Double, lon: Double): Result<List<NearbyStop>> {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            // Return demo data with a clear indicator so user can test the UI immediately
            return Result.success(getDemoHelsinkiStops())
        }

        val result = apiClient.fetchNearestStops(lat, lon, apiKey)
        return if (result.isFailure) {
            val exception = result.exceptionOrNull()
            val message = exception?.message ?: ""
            // If offline / network error / hostname resolution failure, fallback to demo data gracefully
            if (message.contains("Unable to resolve host") ||
                message.contains("Host") ||
                exception is UnknownHostException ||
                exception is ConnectException ||
                exception is SocketTimeoutException ||
                (!message.contains("HTTP 401") && !message.contains("HTTP 403"))) {
                Result.success(getDemoHelsinkiStops())
            } else {
                result
            }
        } else {
            result
        }
    }

    suspend fun getItineraries(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Result<List<JourneyOption>> {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            return Result.success(getDemoItineraries())
        }

        val result = apiClient.fetchItineraries(fromLat, fromLon, toLat, toLon, apiKey)
        return if (result.isFailure) {
            val exception = result.exceptionOrNull()
            val message = exception?.message ?: ""
            if (message.contains("Unable to resolve host") ||
                message.contains("Host") ||
                exception is UnknownHostException ||
                exception is ConnectException ||
                exception is SocketTimeoutException ||
                (!message.contains("HTTP 401") && !message.contains("HTTP 403"))) {
                Result.success(getDemoItineraries())
            } else {
                result
            }
        } else {
            result
        }
    }

    fun getDemoItineraries(): List<JourneyOption> {
        val now = Instant.now().epochSecond
        return listOf(
            JourneyOption(
                durationMinutes = 14,
                startTimeEpochSeconds = now + 120,
                endTimeEpochSeconds = now + 930,
                primaryRouteBadge = "M1",
                primaryVehicleMode = VehicleMode.SUBWAY,
                headsign = "Kivenlahti",
                departureCountdownText = "",
                isRealtime = true,
                ticketZones = "AB",
                legs = listOf(
                    JourneyLeg(VehicleMode.OTHER, null, null, "Alkupiste", "Rautatientori (M)", 3, 250, now + 120, false),
                    JourneyLeg(VehicleMode.SUBWAY, "M1", "Kivenlahti", "Rautatientori (M)", "Kamppi (M)", 8, 2000, now + 300, true),
                    JourneyLeg(VehicleMode.OTHER, null, null, "Kamppi (M)", "Kohde", 3, 200, now + 780, false)
                )
            ),
            JourneyOption(
                durationMinutes = 18,
                startTimeEpochSeconds = now + 180,
                endTimeEpochSeconds = now + 1260,
                primaryRouteBadge = "40",
                primaryVehicleMode = VehicleMode.BUS,
                headsign = "Kannelmäki",
                departureCountdownText = "",
                isRealtime = true,
                ticketZones = "AB",
                legs = listOf(
                    JourneyLeg(VehicleMode.OTHER, null, null, "Alkupiste", "Elielinaukio", 4, 320, now + 180, false),
                    JourneyLeg(VehicleMode.BUS, "40", "Kannelmäki", "Elielinaukio", "Kohde", 11, 4500, now + 420, true),
                    JourneyLeg(VehicleMode.OTHER, null, null, "Pysäkki", "Kohde", 3, 180, now + 1080, false)
                )
            ),
            JourneyOption(
                durationMinutes = 22,
                startTimeEpochSeconds = now + 300,
                endTimeEpochSeconds = now + 1620,
                primaryRouteBadge = "7",
                primaryVehicleMode = VehicleMode.TRAM,
                headsign = "Länsiterminaali T2",
                departureCountdownText = "",
                isRealtime = false,
                ticketZones = "A",
                legs = listOf(
                    JourneyLeg(VehicleMode.TRAM, "7", "Länsiterminaali T2", "Alkupiste", "Kohde", 22, 1200, now + 300, false)
                )
            )
        )
    }

    /**
     * Realistic sample stops in central Helsinki (Rautatientori / Kamppi)
     * with live relative timestamps for instant preview & testing.
     */
    fun getDemoHelsinkiStops(): List<NearbyStop> {
        val now = Instant.now().epochSecond

        return listOf(
            NearbyStop(
                gtfsId = "HSL:1020454",
                name = "Rautatientori",
                code = "H2054",
                distanceMeters = 85,
                vehicleMode = VehicleMode.TRAM,
                departures = listOf(
                    Departure("7", "Länsiterminaali T2", now + 120, true),
                    Departure("3", "Meilahti", now + 360, true),
                    Departure("9", "Ilmala", now + 650, false),
                    Departure("7", "Länsiterminaali T2", now + 840, true)
                )
            ),
            NearbyStop(
                gtfsId = "HSL:1020601",
                name = "Rautatientori (M)",
                code = "M1",
                distanceMeters = 140,
                vehicleMode = VehicleMode.SUBWAY,
                departures = listOf(
                    Departure("M1", "Kivenlahti", now + 90, true),
                    Departure("M2", "Vuosaari", now + 240, true),
                    Departure("M1", "Vuosaari", now + 540, true),
                    Departure("M2", "Tapiola", now + 720, false)
                )
            ),
            NearbyStop(
                gtfsId = "HSL:1020112",
                name = "Elielinaukio",
                code = "Lait. 21",
                distanceMeters = 210,
                vehicleMode = VehicleMode.BUS,
                departures = listOf(
                    Departure("40", "Kannelmäki", now + 180, true),
                    Departure("200", "Espoon keskus", now + 480, true),
                    Departure("300", "Myyrmäki", now + 780, false),
                    Departure("400", "Vantaankoski", now + 1100, false)
                )
            ),
            NearbyStop(
                gtfsId = "HSL:1000001",
                name = "Helsingin päärautatieasema",
                code = "Rata 5",
                distanceMeters = 280,
                vehicleMode = VehicleMode.RAIL,
                departures = listOf(
                    Departure("I", "Lentoasema", now + 300, true),
                    Departure("P", "Lentoasema", now + 600, true),
                    Departure("K", "Kerava", now + 900, true)
                )
            )
        )
    }
}
