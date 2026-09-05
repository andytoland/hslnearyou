package com.example.hometravelapp.data.api

import com.example.hometravelapp.data.model.Departure
import com.example.hometravelapp.data.model.GraphQLQueryRequest
import com.example.hometravelapp.data.model.JourneyLeg
import com.example.hometravelapp.data.model.JourneyOption
import com.example.hometravelapp.data.model.NearbyStop
import com.example.hometravelapp.data.model.NearestResponseEnvelope
import com.example.hometravelapp.data.model.PlanResponseEnvelope
import com.example.hometravelapp.data.model.VehicleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

class DigitransitApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    companion object {
        const val ENDPOINT = "https://api.digitransit.fi/routing/v2/hsl/gtfs/v1"
        const val HEADER_API_KEY = "digitransit-subscription-key"
    }

    suspend fun fetchNearestStops(
        lat: Double,
        lon: Double,
        apiKey: String?,
        maxResults: Int = 4,
        maxDistanceMeters: Int = 1200
    ): Result<List<NearbyStop>> = withContext(Dispatchers.IO) {
        val nowSec = (System.currentTimeMillis() / 1000) - 30 // Allow departures from 30s ago
        val query = """
            {
              nearest(
                lat: $lat,
                lon: $lon,
                maxDistance: $maxDistanceMeters,
                maxResults: $maxResults,
                filterByPlaceTypes: STOP
              ) {
                edges {
                  node {
                    distance
                    place {
                      ... on Stop {
                        gtfsId
                        name
                        code
                        vehicleMode
                        stoptimesWithoutPatterns(startTime: $nowSec, numberOfDepartures: 5, omitNonPickups: true) {
                          scheduledDeparture
                          realtimeDeparture
                          serviceDay
                          realtime
                          headsign
                          trip {
                            route {
                              shortName
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val requestPayload = json.encodeToString(GraphQLQueryRequest(query))
        val body = requestPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        val requestBuilder = Request.Builder()
            .url(ENDPOINT)
            .post(body)

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.addHeader(HEADER_API_KEY, apiKey.trim())
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    401, 403 -> "Digitransit API -avain puuttuu tai on virheellinen (HTTP ${response.code})."
                    429 -> "Pyyntöjen määräraja ylittyi (HTTP 429). Yritä hetken kuluttua uudelleen."
                    else -> "HSL API virhe: HTTP ${response.code} ${response.message}"
                }
                return@withContext Result.failure(IOException(errorMsg))
            }

            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(IOException("Tyhjä vastaus HSL-palvelimelta"))
            }

            val envelope = json.decodeFromString<NearestResponseEnvelope>(responseBody)
            if (!envelope.errors.isNullOrEmpty()) {
                val firstError = envelope.errors.first().message
                return@withContext Result.failure(IOException("GraphQL-virhe: $firstError"))
            }

            val edges = envelope.data?.nearest?.edges ?: emptyList()
            val stops = edges.mapNotNull { edge ->
                val node = edge.node ?: return@mapNotNull null
                val place = node.place ?: return@mapNotNull null
                if (place.gtfsId.isEmpty()) return@mapNotNull null

                val departures = place.stoptimesWithoutPatterns.map { st ->
                    val depSec = if (st.realtime && st.realtimeDeparture > 0) {
                        st.realtimeDeparture
                    } else {
                        st.scheduledDeparture
                    }
                    val totalEpoch = st.serviceDay + depSec

                    val routeName = st.trip?.route?.shortName
                        ?: st.trip?.routeShortName
                        ?: "?"
                    val headsignText = st.headsign
                        ?: st.trip?.headsign
                        ?: "Määränpää ei tiedossa"

                    Departure(
                        routeShortName = routeName,
                        headsign = headsignText,
                        departureEpochSeconds = totalEpoch,
                        isRealtime = st.realtime
                    )
                }

                NearbyStop(
                    gtfsId = place.gtfsId,
                    name = place.name.ifBlank { "Pysäkki" },
                    code = place.code,
                    distanceMeters = node.distance,
                    vehicleMode = VehicleMode.fromString(place.vehicleMode),
                    departures = departures
                )
            }

            Result.success(stops)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchItineraries(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        apiKey: String?
    ): Result<List<JourneyOption>> = withContext(Dispatchers.IO) {
        val query = """
            {
              plan(
                from: {lat: $fromLat, lon: $fromLon},
                to: {lat: $toLat, lon: $toLon},
                numItineraries: 4,
                transportModes: [{mode: BUS}, {mode: TRAM}, {mode: SUBWAY}, {mode: RAIL}, {mode: FERRY}]
              ) {
                itineraries {
                  duration
                  startTime
                  endTime
                  legs {
                    mode
                    startTime
                    endTime
                    duration
                    distance
                    realTime
                    headsign
                    route {
                      shortName
                      mode
                    }
                    from {
                      name
                      stop {
                        code
                        zoneId
                      }
                    }
                    to {
                      name
                      stop {
                        code
                        zoneId
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val requestPayload = json.encodeToString(GraphQLQueryRequest(query))
        val body = requestPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        val requestBuilder = Request.Builder()
            .url(ENDPOINT)
            .post(body)

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.addHeader(HEADER_API_KEY, apiKey.trim())
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    401, 403 -> "Digitransit API -avain puuttuu tai on virheellinen (HTTP ${response.code})."
                    429 -> "Pyyntöjen määräraja ylittyi (HTTP 429). Yritä hetken kuluttua uudelleen."
                    else -> "HSL API virhe: HTTP ${response.code} ${response.message}"
                }
                return@withContext Result.failure(IOException(errorMsg))
            }

            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(IOException("Tyhjä vastaus HSL-palvelimelta"))
            }

            val envelope = json.decodeFromString<PlanResponseEnvelope>(responseBody)
            if (!envelope.errors.isNullOrEmpty()) {
                val firstError = envelope.errors.first().message
                return@withContext Result.failure(IOException("GraphQL-virhe: $firstError"))
            }

            val itinerariesDto = envelope.data?.plan?.itineraries ?: emptyList()
            val now = Instant.now().epochSecond
            val validItineraries = itinerariesDto.filter { itin ->
                val walkLeg = itin.legs.firstOrNull { it.route?.shortName.isNullOrBlank() }
                val transitLeg = itin.legs.firstOrNull { it.route?.shortName != null }
                if (walkLeg != null && transitLeg != null) {
                    val walkMin = (walkLeg.duration / 60.0).toInt()
                    val transitStartSec = if (transitLeg.startTime > 10_000_000_000L) transitLeg.startTime / 1000 else transitLeg.startTime
                    val timeUntilTransitMin = (transitStartSec - now) / 60
                    walkMin <= timeUntilTransitMin + 3
                } else {
                    true
                }
            }

            val journeys = validItineraries.map { itin ->
                val durationMin = ((itin.duration / 60.0) + 0.5).toInt()
                val startEpochSec = if (itin.startTime > 10_000_000_000L) itin.startTime / 1000 else itin.startTime
                val endEpochSec = if (itin.endTime > 10_000_000_000L) itin.endTime / 1000 else itin.endTime

                val allZones = mutableSetOf<String>()
                itin.legs.forEach { leg ->
                    leg.from?.stop?.zoneId?.let { allZones.add(it) }
                    leg.to?.stop?.zoneId?.let { allZones.add(it) }
                }
                val sortedZones = allZones.filter { it.isNotBlank() }.sorted()
                val ticketZonesText = if (sortedZones.isNotEmpty()) sortedZones.joinToString("") else "AB"

                val legs = itin.legs.map { leg ->
                    val legStartSec = if (leg.startTime > 10_000_000_000L) leg.startTime / 1000 else leg.startTime
                    val legDurationMin = ((leg.duration / 60.0) + 0.5).toInt()
                    val distMeters = leg.distance.toInt()
                    JourneyLeg(
                        mode = VehicleMode.fromString(leg.route?.mode ?: leg.mode),
                        routeShortName = leg.route?.shortName,
                        headsign = leg.headsign,
                        fromName = leg.from?.name ?: "Lähtö",
                        toName = leg.to?.name ?: "Määränpää",
                        durationMinutes = legDurationMin,
                        distanceMeters = distMeters,
                        startTimeEpochSeconds = legStartSec,
                        isRealtime = leg.realTime
                    )
                }

                val transitLeg = legs.firstOrNull { it.routeShortName != null }
                    ?: legs.firstOrNull()

                val primaryBadge = transitLeg?.routeShortName ?: "Matka"
                val primaryMode = transitLeg?.mode ?: VehicleMode.BUS
                val headsignText = transitLeg?.headsign ?: legs.lastOrNull()?.toName ?: "Määränpää"
                val isRealtime = transitLeg?.isRealtime ?: false
                val transitStartSec = transitLeg?.startTimeEpochSeconds ?: startEpochSec

                JourneyOption(
                    durationMinutes = durationMin,
                    startTimeEpochSeconds = transitStartSec,
                    endTimeEpochSeconds = endEpochSec,
                    primaryRouteBadge = primaryBadge,
                    primaryVehicleMode = primaryMode,
                    headsign = headsignText,
                    departureCountdownText = "",
                    isRealtime = isRealtime,
                    ticketZones = ticketZonesText,
                    legs = legs
                )
            }

            Result.success(journeys)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
