package com.example.hometravelapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hometravelapp.data.model.NearbyStop
import com.example.hometravelapp.data.repository.TransitRepository
import com.example.hometravelapp.location.LocationTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.example.hometravelapp.data.model.JourneyOption
import com.example.hometravelapp.data.model.LocationType
import com.example.hometravelapp.data.model.SavedJourney
import com.example.hometravelapp.data.model.SavedLocation
import com.example.hometravelapp.data.model.StartingLocation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TransitUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val stops: List<NearbyStop> = emptyList(),
    val journeys: List<JourneyOption> = emptyList(),
    val savedJourneys: List<SavedJourney> = emptyList(),
    val isDemoMode: Boolean = false,
    val apiKey: String? = null,
    // Starting location (GPS or manual override)
    val startingLocation: StartingLocation = StartingLocation(),
    // Separate GPS state from destination state
    val detectedGpsLat: Double? = null,
    val detectedGpsLon: Double? = null,
    val isGpsFixAcquired: Boolean = false,
    val activeLocationTitle: String = "Nykyinen sijainti",
    val activeCoordinates: Pair<Double, Double> = Pair(60.1699, 24.9384),
    val savedLocations: List<SavedLocation> = emptyList(),
    val selectedLocation: SavedLocation = TransitRepository.GPS_LOCATION,
    val errorMessage: String? = null,
    val hasLocationPermission: Boolean = false
)

class TransitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TransitRepository(application)
    private val locationTracker = LocationTracker(application)

    private val _uiState = MutableStateFlow(
        TransitUiState(
            apiKey = repository.getApiKey(),
            isDemoMode = repository.getApiKey().isNullOrBlank(),
            hasLocationPermission = locationTracker.hasLocationPermission(),
            savedLocations = repository.getSavedLocations(),
            savedJourneys = repository.getSavedJourneys(),
            selectedLocation = repository.getSavedLocations().firstOrNull() ?: TransitRepository.GPS_LOCATION,
            startingLocation = repository.getStartingLocation()
        )
    )
    val uiState: StateFlow<TransitUiState> = _uiState.asStateFlow()

    init {
        loadData()
        startPeriodicCountdownTicker()
    }

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = isGranted) }
        if (_uiState.value.selectedLocation.isGps) {
            loadData()
        }
    }

    fun selectLocation(location: SavedLocation) {
        _uiState.update { it.copy(selectedLocation = location) }
        loadData()
    }

    fun saveLocation(location: SavedLocation) {
        repository.saveLocation(location)
        val updated = repository.getSavedLocations()
        _uiState.update {
            it.copy(
                savedLocations = updated,
                selectedLocation = if (it.selectedLocation.id == location.id) location else it.selectedLocation
            )
        }
    }

    fun deleteLocation(id: String) {
        repository.deleteLocation(id)
        val updated = repository.getSavedLocations()
        _uiState.update {
            it.copy(
                savedLocations = updated,
                selectedLocation = if (it.selectedLocation.id == id) TransitRepository.GPS_LOCATION else it.selectedLocation
            )
        }
        if (_uiState.value.selectedLocation.id == id) {
            loadData()
        }
    }

    fun resetLocationsToDefault() {
        repository.resetLocationsToDefault()
        val updated = repository.getSavedLocations()
        _uiState.update {
            it.copy(
                savedLocations = updated,
                selectedLocation = TransitRepository.GPS_LOCATION
            )
        }
        loadData()
    }

    fun setManualStartingLocation(name: String, lat: Double, lon: Double) {
        repository.saveStartingLocation(name, lat, lon, isManual = true)
        val newStartLoc = StartingLocation(name = name, latitude = lat, longitude = lon, isManual = true)
        _uiState.update {
            it.copy(
                startingLocation = newStartLoc,
                selectedLocation = TransitRepository.GPS_LOCATION
            )
        }
        loadData()
    }

    fun useGpsStartingLocation() {
        repository.resetStartingLocationToGps()
        val newStartLoc = StartingLocation(name = "Nykyinen sijainti", latitude = 60.1699, longitude = 24.9384, isManual = false)
        _uiState.update {
            it.copy(
                startingLocation = newStartLoc,
                selectedLocation = TransitRepository.GPS_LOCATION
            )
        }
        loadData()
    }

    suspend fun searchAddress(query: String): List<com.example.hometravelapp.data.model.AddressSearchResult> {
        return repository.searchAddress(query)
    }

    suspend fun getCurrentGpsCoordinates(): Pair<Double, Double>? {
        if (!locationTracker.hasLocationPermission()) return null
        val loc = locationTracker.getCurrentLocation() ?: return null
        return Pair(loc.latitude, loc.longitude)
    }

    fun saveApiKey(newKey: String) {
        repository.saveApiKey(newKey)
        _uiState.update {
            it.copy(
                apiKey = newKey.ifBlank { null },
                isDemoMode = newKey.isBlank()
            )
        }
        loadData(isRefresh = true)
    }

    fun refresh() {
        loadData(isRefresh = true)
    }

    fun loadData(isRefresh: Boolean = false) {
        viewModelScope.launch {
            val startLoc = repository.getStartingLocation()
            _uiState.update {
                if (isRefresh) it.copy(startingLocation = startLoc, isRefreshing = true, errorMessage = null)
                else it.copy(startingLocation = startLoc, isLoading = true, errorMessage = null)
            }

            val selected = _uiState.value.selectedLocation
            var originLat: Double
            var originLon: Double
            var originName: String
            var gpsAcquired = _uiState.value.isGpsFixAcquired
            var gpsLat = _uiState.value.detectedGpsLat
            var gpsLon = _uiState.value.detectedGpsLon

            if (startLoc.isManual) {
                originLat = startLoc.latitude
                originLon = startLoc.longitude
                originName = startLoc.name
            } else {
                if (locationTracker.hasLocationPermission()) {
                    val loc = locationTracker.getCurrentLocation()
                    if (loc != null) {
                        gpsLat = loc.latitude
                        gpsLon = loc.longitude
                        gpsAcquired = true
                    }
                }

                if (gpsLat != null && gpsLon != null) {
                    originLat = gpsLat
                    originLon = gpsLon
                    val placeName = locationTracker.getPlaceName(gpsLat, gpsLon)
                    originName = if (placeName != null) "Nykyinen sijainti ($placeName)" else "Nykyinen sijainti (GPS)"
                } else {
                    originLat = 60.1699
                    originLon = 24.9384
                    originName = "Nykyinen sijainti (Helsinki)"
                }
            }

            if (selected.isGps) {
                val result = repository.getNearestStops(originLat, originLon)
                result.onSuccess { stops ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            stops = stops,
                            journeys = emptyList(),
                            detectedGpsLat = gpsLat,
                            detectedGpsLon = gpsLon,
                            isGpsFixAcquired = gpsAcquired,
                            activeLocationTitle = originName,
                            activeCoordinates = Pair(originLat, originLon),
                            errorMessage = null,
                            isDemoMode = repository.getApiKey().isNullOrBlank()
                        )
                    }
                }.onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = exception.message ?: "Tuntematon virhe pysäkkien haussa."
                        )
                    }
                }
            } else {
                val result = repository.getItineraries(originLat, originLon, selected.latitude, selected.longitude)
                result.onSuccess { journeys ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            stops = emptyList(),
                            journeys = journeys,
                            detectedGpsLat = gpsLat,
                            detectedGpsLon = gpsLon,
                            isGpsFixAcquired = gpsAcquired,
                            activeLocationTitle = "${selected.type.iconEmoji} ${selected.name} (alkaen: $originName)",
                            activeCoordinates = Pair(selected.latitude, selected.longitude),
                            errorMessage = null,
                            isDemoMode = repository.getApiKey().isNullOrBlank()
                        )
                    }
                }.onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = exception.message ?: "Tuntematon virhe reittien haussa."
                        )
                    }
                }
            }
        }
    }

    /**
     * Ticks every 30 seconds to recalculate relative countdowns like "3 min" -> "2 min"
     */
    private fun startPeriodicCountdownTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                // Trigger state emission so compose recomposes formattedRemainingTime
                _uiState.update { current ->
                    current.copy(stops = current.stops.toList(), journeys = current.journeys.toList())
                }
            }
        }
    }

    fun saveJourney(journey: JourneyOption, destinationName: String) {
        val legsSummary = journey.legs.joinToString(" ➔ ") { leg ->
            if (leg.routeShortName != null) "${leg.mode.displayName} (${leg.routeShortName})" else "Kävely (${leg.durationMinutes} min)"
        }
        val zonedDateTime = Instant.ofEpochSecond(journey.startTimeEpochSeconds)
            .atZone(ZoneId.of("Europe/Helsinki"))
        val timeStr = DateTimeFormatter.ofPattern("HH:mm").format(zonedDateTime)

        val saved = SavedJourney(
            title = destinationName,
            primaryRouteBadge = journey.primaryRouteBadge,
            headsign = journey.headsign,
            departureTimeStr = timeStr,
            durationMinutes = journey.durationMinutes,
            legsSummary = legsSummary
        )
        repository.saveJourney(saved)
        _uiState.update { it.copy(savedJourneys = repository.getSavedJourneys()) }
    }

    fun deleteSavedJourney(id: String) {
        repository.deleteJourney(id)
        _uiState.update { it.copy(savedJourneys = repository.getSavedJourneys()) }
    }
}
