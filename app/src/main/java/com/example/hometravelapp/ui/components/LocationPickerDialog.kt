package com.example.hometravelapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hometravelapp.data.model.AddressSearchResult
import com.example.hometravelapp.data.model.LocationType
import com.example.hometravelapp.data.model.SavedLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun LocationPickerDialog(
    initialLocation: SavedLocation? = null,
    onDismiss: () -> Unit,
    onSaveLocation: (SavedLocation) -> Unit,
    onSearchAddress: suspend (String) -> List<AddressSearchResult>,
    onFetchCurrentGps: suspend () -> Pair<Double, Double>?
) {
    val isEditMode = initialLocation != null
    var selectedType by remember(initialLocation) {
        mutableStateOf(initialLocation?.type ?: LocationType.CUSTOM)
    }
    var name by remember(initialLocation) {
        mutableStateOf(initialLocation?.name ?: "")
    }
    var latitudeText by remember(initialLocation) {
        mutableStateOf(initialLocation?.latitude?.takeIf { it != 0.0 }?.toString() ?: "")
    }
    var longitudeText by remember(initialLocation) {
        mutableStateOf(initialLocation?.longitude?.takeIf { it != 0.0 }?.toString() ?: "")
    }

    // Address search state
    var addressSearchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AddressSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditMode) "Muokkaa kohdetta" else "Lisää uusi kohde",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Address Search Box
                Text(
                    text = "1. Etsi osoitteella tai paikannimellä:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = addressSearchQuery,
                    onValueChange = { query ->
                        addressSearchQuery = query
                        searchJob?.cancel()
                        if (query.trim().length >= 2) {
                            searchJob = coroutineScope.launch {
                                isSearching = true
                                delay(350) // Debounce typing
                                searchResults = onSearchAddress(query)
                                isSearching = false
                            }
                        } else {
                            searchResults = emptyList()
                            isSearching = false
                        }
                    },
                    label = { Text("Hae osoitetta") },
                    placeholder = { Text("esim. Mannerheimintie 1, Itis, Tripla") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Haku")
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (addressSearchQuery.isNotBlank()) {
                            IconButton(onClick = {
                                addressSearchQuery = ""
                                searchResults = emptyList()
                            }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Tyhjennä")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        coroutineScope.launch {
                            isSearching = true
                            searchResults = onSearchAddress(addressSearchQuery)
                            isSearching = false
                        }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )

                // Search Results Dropdown/List
                if (searchResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Hakutulokset (valitse napauttamalla):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        searchResults.forEach { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        name = result.name.ifBlank { result.label }
                                        latitudeText = result.latitude.toString()
                                        longitudeText = result.longitude.toString()
                                        searchResults = emptyList()
                                        addressSearchQuery = ""
                                        errorMessage = null
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = result.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Or use Current GPS
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val coords = onFetchCurrentGps()
                            if (coords != null) {
                                latitudeText = coords.first.toString()
                                longitudeText = coords.second.toString()
                                if (name.isBlank()) {
                                    name = selectedType.defaultLabel
                                }
                                errorMessage = null
                            } else {
                                errorMessage = "Nykyistä GPS-sijaintia ei saatu. Tarkista sijaintilupa."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Oma sijainti",
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("Käytä nykyistä sijaintia koordinaatteina")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 2. Details & Type Selection
                Text(
                    text = "2. Kohteen tiedot:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                // Type selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        LocationType.HOME,
                        LocationType.WORK,
                        LocationType.MALL,
                        LocationType.CUSTOM
                    ).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                if (name.isBlank() || LocationType.entries.any { it.defaultLabel == name }) {
                                    name = type.defaultLabel
                                }
                            },
                            label = { Text("${type.iconEmoji} ${type.defaultLabel}") }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Kohteen nimi") },
                    placeholder = { Text("esim. Koti, Tripla, Kuntosali") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = latitudeText,
                        onValueChange = { latitudeText = it },
                        label = { Text("Leveysaste (lat)") },
                        placeholder = { Text("60.1699") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = longitudeText,
                        onValueChange = { longitudeText = it },
                        label = { Text("Pituusaste (lon)") },
                        placeholder = { Text("24.9384") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latitudeText.toDoubleOrNull()
                    val lon = longitudeText.toDoubleOrNull()
                    if (name.isBlank()) {
                        errorMessage = "Anna paikalle nimi."
                        return@Button
                    }
                    if (lat == null || lon == null) {
                        errorMessage = "Hae osoitteella tai anna kelvolliset koordinaatit."
                        return@Button
                    }

                    val saved = SavedLocation(
                        id = initialLocation?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        latitude = lat,
                        longitude = lon,
                        type = selectedType
                    )
                    onSaveLocation(saved)
                }
            ) {
                Text(if (isEditMode) "Tallenna muutokset" else "Tallenna")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Peruuta")
            }
        }
    )
}
