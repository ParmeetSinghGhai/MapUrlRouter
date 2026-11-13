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

data class NetworkQuery (
    var originalUrl:Uri?,
    var unshortenUrl:Uri?,
    var responseCode:Int?,
    var statusMessage:String?,
    var status:Boolean
)

class NetworkManager() {
    var networkQuery = NetworkQuery(
        null,
        null,
        null,
        "",
        false
    )


    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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
        .followRedirects(true) // Keeps the 302 catcher working
        .followSslRedirects(true)
        .dns(secureGoogleDns)    // Forces ALL requests to use Google Secure DNS lookup
        .build()

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
                    if (networkQuery.responseCode == 200) {
                        val finalUrlString  = response.request.url.toString();
                        networkQuery.unshortenUrl= Uri.parse(finalUrlString)
                        networkQuery.status = true
                        networkQuery.statusMessage = "operation completed successfully"
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