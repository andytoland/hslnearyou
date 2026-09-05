package com.example.hometravelapp.widget

import android.content.Context
import com.example.hometravelapp.data.repository.TransitRepository
import com.example.hometravelapp.location.LocationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WidgetDestinationSummary(
    val id: String,
    val name: String,
    val emoji: String,
    val stopName: String,
    val distanceMeters: Int,
    val routeBadge: String,
    val headsign: String,
    val countdownText: String,
    val isRealtime: Boolean
)

object TransitWidgetData {

    suspend fun loadSummaries(context: Context): List<WidgetDestinationSummary> = withContext(Dispatchers.IO) {
        val repository = TransitRepository(context)
        val destinations = repository.getSavedLocations().filterNot { it.isGps }
        val startingLocation = repository.getStartingLocation()

        val originLat = if (startingLocation.isManual) {
            startingLocation.latitude
        } else {
            val loc = LocationTracker(context).getCurrentLocation()
            loc?.latitude ?: 60.1699
        }

        val originLon = if (startingLocation.isManual) {
            startingLocation.longitude
        } else {
            val loc = LocationTracker(context).getCurrentLocation()
            loc?.longitude ?: 24.9384
        }

        val summaries = mutableListOf<WidgetDestinationSummary>()

        for (dest in destinations) {
            val itinerariesResult = repository.getItineraries(originLat, originLon, dest.latitude, dest.longitude)
            val itineraries = itinerariesResult.getOrNull() ?: emptyList()

            if (itineraries.isNotEmpty()) {
                val best = itineraries.first()
                summaries.add(
                    WidgetDestinationSummary(
                        id = dest.id,
                        name = dest.name,
                        emoji = dest.type.iconEmoji,
                        stopName = best.headsign,
                        distanceMeters = best.durationMinutes,
                        routeBadge = best.primaryRouteBadge,
                        headsign = best.headsign,
                        countdownText = best.formattedDepartureTime(),
                        isRealtime = best.isRealtime
                    )
                )
            } else {
                summaries.add(
                    WidgetDestinationSummary(
                        id = dest.id,
                        name = dest.name,
                        emoji = dest.type.iconEmoji,
                        stopName = "Ei reittejä",
                        distanceMeters = 0,
                        routeBadge = "-",
                        headsign = "-",
                        countdownText = "-",
                        isRealtime = false
                    )
                )
            }
        }

        summaries
    }
}
