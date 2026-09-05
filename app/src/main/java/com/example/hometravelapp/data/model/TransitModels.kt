package com.example.hometravelapp.data.model

import androidx.compose.ui.graphics.Color
import com.example.hometravelapp.ui.theme.HslBusBlue
import com.example.hometravelapp.ui.theme.HslFerryTeal
import com.example.hometravelapp.ui.theme.HslMetroOrange
import com.example.hometravelapp.ui.theme.HslTrainPurple
import com.example.hometravelapp.ui.theme.HslTramGreen
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class VehicleMode(val displayName: String, val badgeColor: Color) {
    BUS("Bussi", HslBusBlue),
    TRAM("Ratikka", HslTramGreen),
    SUBWAY("Metro", HslMetroOrange),
    RAIL("Juna", HslTrainPurple),
    FERRY("Lautta", HslFerryTeal),
    OTHER("Muu", HslBusBlue);

    companion object {
        fun fromString(mode: String?): VehicleMode {
            return when (mode?.uppercase(Locale.ROOT)) {
                "BUS" -> BUS
                "TRAM" -> TRAM
                "SUBWAY", "METRO" -> SUBWAY
                "RAIL" -> RAIL
                "FERRY" -> FERRY
                else -> OTHER
            }
        }
    }
}

data class Departure(
    val routeShortName: String,
    val headsign: String,
    val departureEpochSeconds: Long,
    val isRealtime: Boolean
) {
    /**
     * Formats departure into relative countdown (e.g. "Nyt", "3 min")
     * or absolute "HH:mm" if more than 45 minutes away.
     */
    fun formattedDepartureTime(currentEpochSeconds: Long = Instant.now().epochSecond): String {
        val diffSeconds = departureEpochSeconds - currentEpochSeconds
        val diffMinutes = (diffSeconds + 30) / 60

        return when {
            diffMinutes <= 0 -> "Nyt"
            diffMinutes < 45 -> "$diffMinutes min"
            else -> {
                val zonedDateTime = Instant.ofEpochSecond(departureEpochSeconds)
                    .atZone(ZoneId.of("Europe/Helsinki"))
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                formatter.format(zonedDateTime)
            }
        }
    }
}

data class NearbyStop(
    val gtfsId: String,
    val name: String,
    val code: String?,
    val distanceMeters: Int,
    val vehicleMode: VehicleMode,
    val departures: List<Departure>
)

@Serializable
enum class LocationType(val defaultLabel: String, val iconEmoji: String) {
    CURRENT_GPS("Nykyinen sijainti", "📍"),
    HOME("Koti", "🏠"),
    WORK("Työpaikka", "💼"),
    MALL("Kauppakeskus", "🛍️"),
    CUSTOM("Suosikki", "⭐")
}

@Serializable
data class SavedLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val type: LocationType
) {
    val isGps: Boolean get() = type == LocationType.CURRENT_GPS
}

data class AddressSearchResult(
    val label: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class StartingLocation(
    val name: String = "Nykyinen sijainti",
    val latitude: Double = 60.1699,
    val longitude: Double = 24.9384,
    val isManual: Boolean = false
)

// Digitransit Geocoding GeoJSON DTOs
@Serializable
data class GeocodingResponse(
    val features: List<GeocodingFeature> = emptyList()
)

@Serializable
data class GeocodingFeature(
    val properties: GeocodingProperties,
    val geometry: GeocodingGeometry
)

@Serializable
data class GeocodingProperties(
    val label: String = "",
    val name: String? = null
)

@Serializable
data class GeocodingGeometry(
    val coordinates: List<Double> = emptyList() // [lon, lat]
)

// GraphQL Response DTOs
@Serializable
data class GraphQLQueryRequest(
    val query: String
)

@Serializable
data class NearestResponseEnvelope(
    val data: NearestData? = null,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class GraphQLError(
    val message: String
)

@Serializable
data class NearestData(
    val nearest: NearestEdges? = null
)

@Serializable
data class NearestEdges(
    val edges: List<NearestEdge> = emptyList()
)

@Serializable
data class NearestEdge(
    val node: NearestNode? = null
)

@Serializable
data class NearestNode(
    val distance: Int = 0,
    val place: StopPlace? = null
)

@Serializable
data class StopPlace(
    val gtfsId: String = "",
    val name: String = "",
    val code: String? = null,
    val vehicleMode: String? = null,
    val stoptimesWithoutPatterns: List<StopTimeDto> = emptyList()
)

@Serializable
data class StopTimeDto(
    val scheduledDeparture: Long = 0,
    val realtimeDeparture: Long = 0,
    val serviceDay: Long = 0,
    val realtime: Boolean = false,
    val headsign: String? = null,
    val trip: TripDto? = null
)

@Serializable
data class TripDto(
    val headsign: String? = null,
    val routeShortName: String? = null,
    val route: RouteDto? = null
)

@Serializable
data class RouteDto(
    val shortName: String? = null,
    val mode: String? = null
)

// GraphQL Plan Response DTOs
@Serializable
data class PlanResponseEnvelope(
    val data: PlanData? = null,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class PlanData(
    val plan: PlanDto? = null
)

@Serializable
data class PlanDto(
    val itineraries: List<ItineraryDto> = emptyList()
)

@Serializable
data class ItineraryDto(
    val duration: Double = 0.0,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val legs: List<LegDto> = emptyList()
)

@Serializable
data class LegDto(
    val mode: String = "",
    val startTime: Long = 0,
    val endTime: Long = 0,
    val duration: Double = 0.0,
    val distance: Double = 0.0,
    val realTime: Boolean = false,
    val headsign: String? = null,
    val route: RouteDto? = null,
    val from: PlaceDto? = null,
    val to: PlaceDto? = null
)

@Serializable
data class PlaceDto(
    val name: String = "",
    val stop: StopDto? = null
)

@Serializable
data class StopDto(
    val code: String? = null,
    val zoneId: String? = null
)

// UI Domain Models for Journey / Itineraries
data class JourneyOption(
    val durationMinutes: Int,
    val startTimeEpochSeconds: Long,
    val endTimeEpochSeconds: Long,
    val primaryRouteBadge: String,
    val primaryVehicleMode: VehicleMode,
    val headsign: String,
    val departureCountdownText: String,
    val isRealtime: Boolean,
    val ticketZones: String = "AB",
    val legs: List<JourneyLeg>
) {
    fun formattedDepartureTime(currentEpochSeconds: Long = Instant.now().epochSecond): String {
        val diffSeconds = startTimeEpochSeconds - currentEpochSeconds
        val diffMinutes = (diffSeconds + 30) / 60

        val zonedDateTime = Instant.ofEpochSecond(startTimeEpochSeconds)
            .atZone(ZoneId.of("Europe/Helsinki"))
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val timeStr = formatter.format(zonedDateTime)

        return when {
            diffMinutes <= 0 -> "Nyt ($timeStr)"
            diffMinutes < 60 -> "$diffMinutes min ($timeStr)"
            else -> timeStr
        }
    }
}

data class JourneyLeg(
    val mode: VehicleMode,
    val routeShortName: String?,
    val headsign: String?,
    val fromName: String,
    val toName: String,
    val durationMinutes: Int,
    val distanceMeters: Int = 0,
    val startTimeEpochSeconds: Long,
    val isRealtime: Boolean
)

@Serializable
data class SavedJourney(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val primaryRouteBadge: String,
    val headsign: String,
    val departureTimeStr: String,
    val durationMinutes: Int,
    val ticketZones: String = "AB",
    val legsSummary: String,
    val savedAtEpochSeconds: Long = Instant.now().epochSecond
)

