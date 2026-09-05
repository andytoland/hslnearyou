package com.example.hometravelapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedAssistChip
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.hometravelapp.data.model.AddressSearchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class QuickPreset(
    val name: String,
    val lat: Double,
    val lon: Double
)

private val HELSINKI_PRESETS = listOf(
    QuickPreset("Kamppi", 60.1687, 24.9332),
    QuickPreset("Rautatientori", 60.1700, 24.9414),
    QuickPreset("Pasila", 60.1989, 24.9304),
    QuickPreset("Hakaniemi", 60.1793, 24.9515),
    QuickPreset("Otaniemi", 60.1841, 24.8306),
    QuickPreset("Itis", 60.2117, 25.0818)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartingLocationDialog(
    currentName: String,
    isManual: Boolean,
    onSetManualLocation: (name: String, lat: Double, lon: Double) -> Unit,
    onUseGps: () -> Unit,
    onSearchAddress: suspend (String) -> List<AddressSearchResult>,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AddressSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    // Manual coordinate overrides
    var showCoordinatesInput by remember { mutableStateOf(false) }
    var manualLatText by remember { mutableStateOf("") }
    var manualLonText by remember { mutableStateOf("") }
    var manualNameText by remember { mutableStateOf("") }

    // Debounced address search
    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.length < 2) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(350)
        searchResults = onSearchAddress(trimmed)
        isSearching = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aseta lähtöpaikka")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Valitse paikka, josta lähimmät pysäkit ja aikataulut etsitään:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // GPS Option Button
                Button(
                    onClick = {
                        onUseGps()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (!isManual) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (!isManual) "✓ Käytetään puhelimen GPS:ää" else "Käytä puhelimen GPS-sijaintia"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "VALITSE NOPEASTI KESKUSTASTA:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick presets
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HELSINKI_PRESETS.forEach { preset ->
                        ElevatedAssistChip(
                            onClick = {
                                onSetManualLocation(preset.name, preset.lat, preset.lon)
                                onDismiss()
                            },
                            label = {
                                Text(
                                    text = preset.name,
                                    fontWeight = if (currentName == preset.name && isManual) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "TAI HAE OSOITTEELLA:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Address Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Kirjoita osoite tai paikka...") },
                    placeholder = { Text("Esim. Rautatientori, Kamppi, Oodi...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Haku")
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Tyhjennä")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        if (searchResults.isNotEmpty()) {
                            val top = searchResults.first()
                            onSetManualLocation(top.name, top.latitude, top.longitude)
                            onDismiss()
                        } else if (searchQuery.isNotBlank()) {
                            coroutineScope.launch {
                                isSearching = true
                                val results = onSearchAddress(searchQuery.trim())
                                isSearching = false
                                if (results.isNotEmpty()) {
                                    val top = results.first()
                                    onSetManualLocation(top.name, top.latitude, top.longitude)
                                    onDismiss()
                                }
                            }
                        }
                    }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Top result quick-select button
                if (searchResults.isNotEmpty()) {
                    val top = searchResults.first()
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onSetManualLocation(top.name, top.latitude, top.longitude)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Käytä: ${top.name}", maxLines = 1)
                    }
                }

                // Search Results List
                if (searchResults.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        items(searchResults.drop(1)) { res ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSetManualLocation(res.name, res.latitude, res.longitude)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = res.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = res.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option to type exact coordinates manually
                if (!showCoordinatesInput) {
                    TextButton(
                        onClick = { showCoordinatesInput = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Syötä koordinaatit käsin (Lat / Lon)")
                    }
                } else {
                    OutlinedTextField(
                        value = manualNameText,
                        onValueChange = { manualNameText = it },
                        label = { Text("Paikan nimi (esim. Rautatientori)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = manualLatText,
                            onValueChange = { manualLatText = it },
                            label = { Text("Lat (60.1700)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = manualLonText,
                            onValueChange = { manualLonText = it },
                            label = { Text("Lon (24.9414)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            val lat = manualLatText.toDoubleOrNull() ?: 60.1700
                            val lon = manualLonText.toDoubleOrNull() ?: 24.9414
                            val name = manualNameText.ifBlank { "Manuaalinen paikka" }
                            onSetManualLocation(name, lat, lon)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tallenna ja käytä näitä koordinaatteja")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Sulje")
            }
        }
    )
}
