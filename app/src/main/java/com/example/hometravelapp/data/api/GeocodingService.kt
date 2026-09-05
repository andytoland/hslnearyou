package com.example.hometravelapp.data.api

import android.content.Context
import android.location.Geocoder
import com.example.hometravelapp.data.model.AddressSearchResult
import com.example.hometravelapp.data.model.GeocodingResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeocodingService(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    companion object {
        private const val GEOCODING_URL = "https://api.digitransit.fi/geocoding/v1/search"
    }

    suspend fun searchAddress(query: String, apiKey: String?): List<AddressSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        // 1. Try Digitransit Geocoding API if key is available
        if (!apiKey.isNullOrBlank()) {
            val urlBuilder = GEOCODING_URL.toHttpUrlOrNull()?.newBuilder()
            if (urlBuilder != null) {
                urlBuilder.addQueryParameter("text", query.trim())
                urlBuilder.addQueryParameter("boundary.circle.lat", "60.1699")
                urlBuilder.addQueryParameter("boundary.circle.lon", "24.9384")
                urlBuilder.addQueryParameter("boundary.circle.radius", "75")
                urlBuilder.addQueryParameter("size", "6")

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .addHeader("digitransit-subscription-key", apiKey.trim())
                    .get()
                    .build()

                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val geoResponse = json.decodeFromString<GeocodingResponse>(body)
                            val results = geoResponse.features.mapNotNull { feature ->
                                val coords = feature.geometry.coordinates
                                if (coords.size >= 2) {
                                    val lon = coords[0]
                                    val lat = coords[1]
                                    AddressSearchResult(
                                        label = feature.properties.label.ifBlank { query },
                                        name = feature.properties.name ?: feature.properties.label,
                                        latitude = lat,
                                        longitude = lon
                                    )
                                } else null
                            }
                            if (results.isNotEmpty()) {
                                return@withContext results
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fall through to system Geocoder
                }
            }
        }

        // 2. Fallback: Android system Geocoder
        try {
            val geocoder = Geocoder(context, Locale("fi", "FI"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName("${query.trim()}, Finland", 5)
            if (!addresses.isNullOrEmpty()) {
                return@withContext addresses.map { addr ->
                    val line = (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }.joinToString(", ")
                    val title = addr.featureName ?: addr.thoroughfare ?: line
                    AddressSearchResult(
                        label = if (line.isNotBlank()) line else title,
                        name = title,
                        latitude = addr.latitude,
                        longitude = addr.longitude
                    )
                }
            }
        } catch (_: Exception) {
            // Geocoder service unavailable
        }

        // 3. Fallback for common Helsinki destinations if offline/demo
        getPresetHelsinkiMatches(query)
    }

    private fun getPresetHelsinkiMatches(query: String): List<AddressSearchResult> {
        val q = query.lowercase(Locale.ROOT)
        val presets = listOf(
            AddressSearchResult("Mannerheimintie 1, Helsinki", "Mannerheimintie 1", 60.1698, 24.9406),
            AddressSearchResult("Kamppi, Urho Kekkosen katu 1, Helsinki", "Kamppi", 60.1687, 24.9332),
            AddressSearchResult("Mall of Tripla, Firdonkatu 2, Helsinki", "Mall of Tripla", 60.1989, 24.9304),
            AddressSearchResult("Kauppakeskus Itis, Itäkatu 1-7, Helsinki", "Itis", 60.2117, 25.0818),
            AddressSearchResult("Kauppakeskus Redi, Hermannin rantatie 5, Helsinki", "Redi", 60.1872, 24.9794),
            AddressSearchResult("Kauppakeskus Jumbo, Vantaanportinkatu 3, Vantaa", "Jumbo", 60.2923, 24.9659),
            AddressSearchResult("Aalto-yliopisto, Otakaari 1, Espoo", "Aalto-yliopisto", 60.1871, 24.8290),
            AddressSearchResult("Helsingin yliopisto, Yliopistonkatu 4, Helsinki", "Helsingin yliopisto", 60.1696, 24.9495),
            AddressSearchResult("Helsinki-Vantaan lentoasema, Vantaa", "Lentoasema", 60.3172, 24.9633)
        )
        return presets.filter { it.label.lowercase(Locale.ROOT).contains(q) || it.name.lowercase(Locale.ROOT).contains(q) }
    }
}
