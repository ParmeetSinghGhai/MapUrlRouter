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


data class GeoCoordinates(val latitude: Double, val longitude: Double, val destination: String?)

class NetworkManager {
    var result = Pair<Boolean,Uri?>(false,null)

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

    private fun extractCoordinatesFromGoogleUrl(url: String?): GeoCoordinates? {
        if (url.isNullOrEmpty())
        {
            MessageQueueManager.push("parsing of long url failed. the long url is empty or null",MTYPE.ERROR,MLEVEL.PARSING)
            return null
        }

        try {
            var lat:Double=0.0
            var lon:Double=0.0
            var destination:String? = null

            // --- 1. GOOGLE MAPS OBFUSCATED DATA PACKET FORMAT (!3d / !4d) ---
            // Example: https://google.com
            // Captures exact structural pin drops when coordinates are stripped from path segments
            val internalDataLatPattern = "!3d(-?\\d+\\.\\d+)".toRegex()
            val internalDataLonPattern = "!4d(-?\\d+\\.\\d+)".toRegex()
            val internalLatMatch = internalDataLatPattern.find(url)
            val internalLonMatch = internalDataLonPattern.find(url)
            if (internalLatMatch != null && internalLonMatch != null) {
                lat = internalLatMatch.groupValues[1].toDouble()
                lon = internalLonMatch.groupValues[1].toDouble()
                MessageQueueManager.push("parsing of long url was successful. pattern 3d /4d used ",MTYPE.SUCCESS,MLEVEL.PARSING)
                return GeoCoordinates(lat, lon,destination)
            }

            // --- 2. DIRECT VIEWPORT WINDOW FORMAT ---
            // Example: https://google.com...
            // Captures standard floating point patterns immediately following an '@' symbol
            val directPattern = "@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)".toRegex()
            val directMatch = directPattern.find(url)
            if (directMatch != null) {
                lat = directMatch.groupValues[1].toDouble()
                lon = directMatch.groupValues[2].toDouble()
                MessageQueueManager.push("parsing of long url was successful. pattern @ used ",MTYPE.SUCCESS,MLEVEL.PARSING)
                return GeoCoordinates(lat, lon,destination)
            }

            // --- 3. FLEXIBLE SEARCH QUERY / PARAMETER FORMAT ---
            // Example: https://google.com OR ?query=12.97159+77.59456
            // Captures parameters assigned to 'q' or 'query' keys utilizing space, plus, or comma configurations
            val queryPattern = "[?&](?:q|query)=(-?\\d+\\.\\d+)(?:,|\\+|%20|,\\s*)(-?\\d+\\.\\d+)".toRegex()
            val queryMatch = queryPattern.find(url)
            if (queryMatch != null) {
                lat = queryMatch.groupValues[1].toDouble()
                lon = queryMatch.groupValues[2].toDouble()
                MessageQueueManager.push("parsing of long url was successful. pattern q /query used ",MTYPE.SUCCESS,MLEVEL.PARSING)
                return GeoCoordinates(lat, lon,destination)
            }

            // --- 4. DESTINATION / NAVIGATION ROUTE FORMAT ---
            // Example: https://google.com
            // Captures parameters assigned to navigation paths or 'destination' keys
            val destPattern = "(?:[?&]destination=|/dir/[^/]+/)(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)".toRegex()
            val destMatch = destPattern.find(url)
            if (destMatch != null) {
                lat = destMatch.groupValues[1].toDouble()
                lon = destMatch.groupValues[2].toDouble()
                MessageQueueManager.push("parsing of long url was successful. pattern destination used ",MTYPE.SUCCESS,MLEVEL.PARSING)
                return GeoCoordinates(lat, lon,destination)
            }

            // --- 5. EMBEDDED INTENT/PLACE FALLBACK ---
            // Fallback to strip raw numbers out of custom location labels if matching characters are found
            val fallbackPattern = "(-?\\d+\\.\\d+),(?:\\+)?(-?\\d+\\.\\d+)".toRegex()
            val fallbackMatch = fallbackPattern.find(url)
            if (fallbackMatch != null) {
                lat = fallbackMatch.groupValues[1].toDouble()
                lon = fallbackMatch.groupValues[2].toDouble()
                MessageQueueManager.push("parsing of long url was successful. pattern labels used ",MTYPE.SUCCESS,MLEVEL.PARSING)
                return GeoCoordinates(lat, lon,destination)
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
                lon = destinationMatch.groupValues[1].toDouble()
                lat = destinationMatch.groupValues[2].toDouble()
                MessageQueueManager.push("parsing of long url successful. pattern dir/data used", MTYPE.SUCCESS, MLEVEL.PARSING)
                return GeoCoordinates(lat, lon,destination)
            }

            // --- 7. GOOGLE MAPS API QUERY DESTINATION FORMAT ---
            // Example: https://www.google.com/maps/dir/?api=1&destination=Times+Square+New+York
            // Captures text-based or alphanumeric names assigned to the 'destination' query key
            val apiDestPattern = "[?&]destination=([^&]+)".toRegex()
            val apiDestMatch = apiDestPattern.find(url)
            if (apiDestMatch != null) {
                val rawDestinationText = apiDestMatch.groupValues[1]
                destination = android.net.Uri.decode(rawDestinationText).replace("+", " ")
                MessageQueueManager.push("parsing of long url successful. pattern 'destination=' used", MTYPE.SUCCESS, MLEVEL.PARSING)
                return GeoCoordinates(lat, lon,destination)
            }

        } catch (e: Exception) {
            MessageQueueManager.push("parsing of long url failed::  ${e.localizedMessage} ",MTYPE.ERROR,MLEVEL.PARSING)
        }

        MessageQueueManager.push("parsing of long url failed:: url did not match any patterns ",MTYPE.ERROR,MLEVEL.PARSING)
        return null
    }


    suspend fun unshortenUrl(url:Uri?):Pair<Boolean,Uri?> {
        MessageQueueManager.push("unshorting url:: \n"+url.toString()+ " \nby establishing a http connection to google server. please wait..", MTYPE.INFO,MLEVEL.HTTP_CONNECTION)

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url.toString())
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    )
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseCode = response.code
                    if (responseCode == 302 || responseCode == 301 || responseCode == 200) {
                        MessageQueueManager.push("http connection passed with response code: $responseCode", MTYPE.SUCCESS,MLEVEL.HTTP_CONNECTION)
                        val longUrl = response.header("Location")
                        var coords: GeoCoordinates? = null

                        //if location header is not present then use the actual url
                        if(!longUrl.isNullOrEmpty())
                            coords = extractCoordinatesFromGoogleUrl(longUrl)
                        else
                            coords = extractCoordinatesFromGoogleUrl(url.toString())

                        //check if parser worked or not
                        if (coords != null) {
                            // Successfully extracted double variables!
                            if(coords.destination != null)
                            {
                                val encodedPlace = android.net.Uri.encode(coords.destination)
                                result = Pair(true,Uri.parse("geo:0,0?q=$encodedPlace"))
                            }
                            else {
                                val lat = coords.latitude
                                val lon = coords.longitude
                                result = Pair(true, Uri.parse("geo:$lat,$lon?q=$lat,$lon"))
                            }
                        }
                    }
                    else {
                        MessageQueueManager.push("failed to unshorten url. http connection error with response code: $responseCode",MTYPE.ERROR,MLEVEL.HTTP_CONNECTION)
                    }
                }
            } catch (e: Exception) {
                MessageQueueManager.push("failed to unshorten url. secure dns connection failed:: ${e.localizedMessage}",MTYPE.ERROR,MLEVEL.HTTP_CONNECTION)
            }
            result
        }
    }

}