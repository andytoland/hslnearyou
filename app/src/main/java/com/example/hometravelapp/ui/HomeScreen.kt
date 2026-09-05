package com.example.hometravelapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hometravelapp.ui.components.ApiKeyDialog
import com.example.hometravelapp.ui.components.JourneyCard
import com.example.hometravelapp.ui.components.SavedJourneysDialog
import com.example.hometravelapp.ui.components.StopCard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.example.hometravelapp.ui.components.LocationPickerDialog

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import com.example.hometravelapp.data.repository.TransitRepository
import com.example.hometravelapp.ui.components.DestinationsSettingsDialog
import com.example.hometravelapp.ui.components.StartingLocationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TransitViewModel,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLocationPickerDialog by remember { mutableStateOf(false) }
    var showStartingLocationDialog by remember { mutableStateOf(false) }
    var showSavedJourneysDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "HSL Lähellä",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSavedJourneysDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Tallennetut reitit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Päivitä"
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Asetukset"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Predetermined & favorite locations chips row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.savedLocations.forEach { loc ->
                    val isSelected = loc.id == state.selectedLocation.id
                    val chipLabel = if (loc.isGps) {
                        if (state.startingLocation.isManual) "📍 ${state.startingLocation.name}" else "📍 Nykyinen"
                    } else {
                        "${loc.type.iconEmoji} ${loc.name}"
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectLocation(loc) },
                        label = {
                            Text(chipLabel)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                // Add location button
                FilterChip(
                    selected = false,
                    onClick = { showLocationPickerDialog = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Lisää",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Lisää") }
                )
            }

            // Active Starting Location Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (state.selectedLocation.isGps) {
                                if (state.startingLocation.isManual) Icons.Default.Place else Icons.Default.MyLocation
                            } else Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (state.selectedLocation.isGps) {
                                    if (state.startingLocation.isManual) "Lähtöpaikka: ${state.startingLocation.name} (Manuaalinen)"
                                    else "Lähtöpaikka: ${state.activeLocationTitle}"
                                } else "Pysäkit kohteessa: ${state.activeLocationTitle}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Piste: ${"%.4f".format(java.util.Locale.US, state.activeCoordinates.first)}, ${"%.4f".format(java.util.Locale.US, state.activeCoordinates.second)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row {
                        if (state.selectedLocation.isGps) {
                            TextButton(
                                onClick = { showStartingLocationDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("✏️ Aseta", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            TextButton(
                                onClick = { viewModel.selectLocation(TransitRepository.GPS_LOCATION) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Lähtöpaikkaan", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Demo mode notice banner
            if (state.isDemoMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { showSettingsDialog = true }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Demo tila",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Esikatselutila (Helsingin keskusta)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Napauta avataksesi asetukset ja syöttääksesi ilmainen Digitransit-avain.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Location permission request banner if not granted
            if (!state.hasLocationPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Sijainti",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lähimpien pysäkkien haku vaatii sijaintiluvan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Button(
                            onClick = onRequestLocationPermission,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Salli", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isLoading && state.stops.isEmpty() && state.journeys.isEmpty() -> {
                        CircularProgressIndicator()
                    }

                    state.errorMessage != null && state.stops.isEmpty() && state.journeys.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = state.errorMessage ?: "Virhe ladattaessa tietoja.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refresh() }) {
                                Text("Yritä uudelleen")
                            }
                        }
                    }

                    state.selectedLocation.isGps && state.stops.isEmpty() -> {
                        Text(
                            text = "Lähistöltä ei löytynyt pysäkkejä (1,2 km säteellä).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    !state.selectedLocation.isGps && state.journeys.isEmpty() -> {
                        Text(
                            text = "Ei reittivaihtoehtoja kohteeseen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    state.selectedLocation.isGps -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(
                                items = state.stops,
                                key = { it.gtfsId }
                            ) { stop ->
                                StopCard(stop = stop)
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(
                                items = state.journeys
                            ) { journey ->
                                JourneyCard(
                                    journey = journey,
                                    destinationName = state.selectedLocation.name,
                                    onSaveJourney = { j, dest -> viewModel.saveJourney(j, dest) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSavedJourneysDialog) {
        SavedJourneysDialog(
            savedJourneys = state.savedJourneys,
            onDeleteJourney = { id -> viewModel.deleteSavedJourney(id) },
            onDismiss = { showSavedJourneysDialog = false }
        )
    }

    if (showSettingsDialog) {
        DestinationsSettingsDialog(
            currentApiKey = state.apiKey,
            savedLocations = state.savedLocations,
            onDismiss = { showSettingsDialog = false },
            onSaveApiKey = { key ->
                viewModel.saveApiKey(key)
            },
            onSaveLocation = { loc ->
                viewModel.saveLocation(loc)
            },
            onDeleteLocation = { id ->
                viewModel.deleteLocation(id)
            },
            onResetLocations = {
                viewModel.resetLocationsToDefault()
            },
            onSearchAddress = { query ->
                viewModel.searchAddress(query)
            },
            onFetchCurrentGps = {
                viewModel.getCurrentGpsCoordinates()
            }
        )
    }

    if (showLocationPickerDialog) {
        LocationPickerDialog(
            initialLocation = null,
            onDismiss = { showLocationPickerDialog = false },
            onSaveLocation = { newLocation ->
                viewModel.saveLocation(newLocation)
                viewModel.selectLocation(newLocation)
                showLocationPickerDialog = false
            },
            onSearchAddress = { query ->
                viewModel.searchAddress(query)
            },
            onFetchCurrentGps = {
                viewModel.getCurrentGpsCoordinates()
            }
        )
    }

    if (showStartingLocationDialog) {
        StartingLocationDialog(
            currentName = state.startingLocation.name,
            isManual = state.startingLocation.isManual,
            onSetManualLocation = { name, lat, lon ->
                viewModel.setManualStartingLocation(name, lat, lon)
            },
            onUseGps = {
                viewModel.useGpsStartingLocation()
            },
            onSearchAddress = { query ->
                viewModel.searchAddress(query)
            },
            onDismiss = { showStartingLocationDialog = false }
        )
    }
}
