package com.example.maprouter

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import android.net.Uri
import android.util.Log


data class GeoCoordinates(val latitude: Double?, val longitude: Double?, val destination: String?)
data class NetworkQuery (
    var originalUrl:Uri?,
    var geoUrl:Uri?,
    var responseCode:Int?,
    var locationHeader:String?,
    var parsingPattern:String?,
    var destination:String?,
    var latitude:Double?,
    var longitude:Double?,
    var statusMessage:String?,
    var status:Boolean
)



class NetworkManager() {
    var networkQuery = NetworkQuery(
        null,
        null,
        0,
        null,
        null,
        null,
        0.0,
        0.0,
        null,
        false)

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()


    private val secureGoogleDns = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url("https://dns.google/dns-query".toHttpUrl()) // Forces DNS resolution via HTTPS
        .bootstrapDnsHosts(
            InetAddress.getByName("8.8.8.8"), // Fallback IPv4 addresses to bootstrap the channel
            InetAddress.getByName("8.8.4.4")
        )
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false) // Keeps the 302 catcher working
        .dns(secureGoogleDns)    // Forces ALL requests to use Google Secure DNS lookup
        .build()

    private fun parseGeoUrlFromGoogleUrl() {
        try {
            var url:String? = null
            if(networkQuery.locationHeader.isNullOrEmpty())
                url = networkQuery.originalUrl.toString()
            else
                url = networkQuery.locationHeader.toString()


            // --- 1. GOOGLE MAPS OBFUSCATED DATA PACKET FORMAT (!3d / !4d) ---
            // Example: https://google.com
            // Captures exact structural pin drops when coordinates are stripped from path segments
            val internalDataLatPattern = "!3d(-?\\d+\\.\\d+)".toRegex()
            val internalDataLonPattern = "!4d(-?\\d+\\.\\d+)".toRegex()
            val internalLatMatch = internalDataLatPattern.find(url)
            val internalLonMatch = internalDataLonPattern.find(url)
            if (internalLatMatch != null && internalLonMatch != null) {
                networkQuery.latitude = internalLatMatch.groupValues[1].toDouble()
                networkQuery.longitude = internalLonMatch.groupValues[1].toDouble()
                networkQuery.destination = null
                networkQuery.parsingPattern = "3d/4d"
                networkQuery.geoUrl = Uri.parse("geo:${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}?q=${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}")
                networkQuery.status = true
                networkQuery.statusMessage = "operation completed successfully"
                return
            }

            // --- 2. DIRECT VIEWPORT WINDOW FORMAT ---
            // Example: https://google.com...
            // Captures standard floating point patterns immediately following an '@' symbol
            val directPattern = "@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)".toRegex()
            val directMatch = directPattern.find(url)
            if (directMatch != null) {
                networkQuery.latitude = directMatch.groupValues[1].toDouble()
                networkQuery.longitude  = directMatch.groupValues[2].toDouble()
                networkQuery.destination = null
                networkQuery.parsingPattern = "@"
                networkQuery.geoUrl = Uri.parse("geo:${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}?q=${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}")
                networkQuery.status = true
                networkQuery.statusMessage = "operation completed successfully"
                return
            }

            // --- 3. FLEXIBLE SEARCH QUERY / PARAMETER FORMAT ---
            // Example: https://google.com OR ?query=12.97159+77.59456
            // Captures parameters assigned to 'q' or 'query' keys utilizing space, plus, or comma configurations
            val queryPattern = "[?&](?:q|query)=(-?\\d+\\.\\d+)(?:,|\\+|%20|,\\s*)(-?\\d+\\.\\d+)".toRegex()
            val queryMatch = queryPattern.find(url)
            if (queryMatch != null) {
                networkQuery.latitude = queryMatch.groupValues[1].toDouble()
                networkQuery.longitude = queryMatch.groupValues[2].toDouble()
                networkQuery.destination = null
                networkQuery.parsingPattern = "q / query"
                networkQuery.geoUrl = Uri.parse("geo:${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}?q=${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}")
                networkQuery.status = true
                networkQuery.statusMessage = "operation completed successfully"
                return
            }

            // --- 4. DESTINATION / NAVIGATION ROUTE FORMAT ---
            // Example: https://google.com
            // Captures parameters assigned to navigation paths or 'destination' keys
            val destPattern = "(?:[?&]destination=|/dir/[^/]+/)(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)".toRegex()
            val destMatch = destPattern.find(url)
            if (destMatch != null) {
                networkQuery.latitude = destMatch.groupValues[1].toDouble()
                networkQuery.longitude = destMatch.groupValues[2].toDouble()
                networkQuery.destination = null
                networkQuery.parsingPattern = "destination coordinates"
                networkQuery.geoUrl = Uri.parse("geo:${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}?q=${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}")
                networkQuery.status = true
                networkQuery.statusMessage = "operation completed successfully"
                return
            }

            // --- 5. EMBEDDED INTENT/PLACE FALLBACK ---
            // Fallback to strip raw numbers out of custom location labels if matching characters are found
            val fallbackPattern = "(-?\\d+\\.\\d+),(?:\\+)?(-?\\d+\\.\\d+)".toRegex()
            val fallbackMatch = fallbackPattern.find(url)
            if (fallbackMatch != null) {
                networkQuery.latitude = fallbackMatch.groupValues[1].toDouble()
                networkQuery.longitude = fallbackMatch.groupValues[2].toDouble()
                networkQuery.destination = null
                networkQuery.parsingPattern = "labels"
                networkQuery.geoUrl = Uri.parse("geo:${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}?q=${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}")
                networkQuery.status = true
                networkQuery.statusMessage = "operation completed successfully"
                return
            }

            // --- 6. MULTI-STOP DIRECTION PATH ENCODED DATA BLOCK PATTERN ---
            // Matches the raw sequential coordinate sets generated inside /maps/dir/ links
            // The first sequence is generally the Origin; the final sequence is the Destination
            val directionDataPattern = "!2d(-?\\d+\\.\\d+)!2d(-?\\d+\\.\\d+)".toRegex()
            val directionMatches = directionDataPattern.findAll(url).toList()
            if (directionMatches.isNotEmpty()) {
                // If multiple coordinate sets are discovered, the last set is the final destination pin
                val destinationMatch = directionMatches.last()
                // Note: Google's internal layout format lists Longitude (!2d) first, then Latitude (!2d)
                networkQuery.latitude = destinationMatch.groupValues[1].toDouble()
                networkQuery.latitude = destinationMatch.groupValues[2].toDouble()
                networkQuery.destination = null
                networkQuery.parsingPattern = "dir/data"
                networkQuery.geoUrl = Uri.parse("geo:${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}?q=${networkQuery.latitude.toString()},${networkQuery.longitude.toString()}")
                networkQuery.status = true
                networkQuery.statusMessage = "operation completed successfully"
                return
            }

            // --- 7. GOOGLE MAPS API QUERY DESTINATION FORMAT ---
            // Example: https://www.google.com/maps/dir/?api=1&destination=Times+Square+New+York
            // Captures text-based or alphanumeric names assigned to the 'destination' query key
            val apiDestPattern = "[?&]destination=([^&]+)".toRegex()
            val apiDestMatch = apiDestPattern.find(url)
            if (apiDestMatch != null) {
                val rawDestinationText = apiDestMatch.groupValues[1]
                networkQuery.latitude = 0.0
                networkQuery.longitude = 0.0
                networkQuery.parsingPattern = "destination text in original url"
                networkQuery.destination = android.net.Uri.decode(rawDestinationText).replace("+", " ")
                networkQuery.geoUrl = Uri.parse("geo:0,0?q=" + android.net.Uri.encode(networkQuery.destination))
                networkQuery.status = true
                networkQuery.statusMessage = "operation completed successfully"
                return
            }

        } catch (e: Exception) {
            networkQuery.status = false
            networkQuery.statusMessage = e.localizedMessage
            return
        }

        networkQuery.status = false
        networkQuery.statusMessage = "url did not match any pattern"
        return
    }


    suspend fun unshortenUrl() {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(networkQuery.originalUrl.toString())
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    )
                    .build()

                client.newCall(request).execute().use { response ->
                    networkQuery.responseCode = response.code
                    if (networkQuery.responseCode == 302 || networkQuery.responseCode == 301 || networkQuery.responseCode == 200) {
                        networkQuery.locationHeader = response.header("Location")

                        parseGeoUrlFromGoogleUrl()
                    }
                    else {
                        networkQuery.status = false
                        networkQuery.statusMessage = "http connection error with incorrect response code"
                    }
                }
            } catch (e: Exception) {
                networkQuery.status = false
                networkQuery.statusMessage = "secure dns connection failed:: ${e.localizedMessage}"
            }
        }
    }

}