package com.example.maprouter

import android.net.Uri

class GeoUrlParser
{

    private val regexList:List<Pair<String,Regex>> = listOf(
        Pair("3d,4d","!3d(-?\\d+\\.\\d+)".toRegex()),
        Pair("3d,4d","!4d(-?\\d+\\.\\d+)".toRegex()),
        Pair("@","@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)".toRegex()),
        Pair("q or query", "[?&](?:q|query)=(-?\\d+\\.\\d+)(?:,|\\+|%20|,\\s*)(-?\\d+\\.\\d+)".toRegex()),
        Pair("dest coordinates","(?:[?&]destination=|/dir/[^/]+/)(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)".toRegex()),
        Pair("label","(-?\\d+\\.\\d+),(?:\\+)?(-?\\d+\\.\\d+)".toRegex()),
        Pair("dir data","!2d(-?\\d+\\.\\d+)!2d(-?\\d+\\.\\d+)".toRegex()),
        Pair("dest text","[?&]destination=([^&]+)".toRegex()),
        Pair("place text","/place/([^/]+)/".toRegex())
    )

    fun getGeoUrl(patternString:String):Pair<String?,Uri?>    {
        var lat:Double? = null
        var lon:Double? = null
        var geoUri:Uri? = null

        try {
            val matchResult1 = regexList[0].second.find(patternString)
            val matchResult2 = regexList[1].second.find(patternString)
            if (matchResult1 != null && matchResult2 != null) {
                lat = matchResult1.groupValues[1].toDouble()
                lon = matchResult2.groupValues[1].toDouble()
                geoUri = Uri.parse("geo:${lat},${lon}?q=${lat},${lon}")
                return Pair(regexList[0].first, geoUri)
            }

            for (i in 2..6) {
                val matchResult = regexList[i].second.find(patternString)
                if (matchResult != null) {
                    lat = matchResult.groupValues[1].toDouble()
                    lon = matchResult.groupValues[2].toDouble()
                    geoUri = Uri.parse("geo:${lat},${lon}?q=${lat},${lon}")
                    return Pair(regexList[i].first, geoUri)
                }
            }

            for (i in 7..8) {
                val matchResult = regexList[i].second.find(patternString)
                if (matchResult != null) {
                    val dest = matchResult.groupValues[1].replace("+", " ")
                    geoUri = Uri.parse("geo:0,0?q=${dest}")
                    return Pair(regexList[i].first, geoUri)
                }
            }
        }
        catch (e: Exception) {
            return Pair(e.localizedMessage,null)
        }

        return Pair(null,null)
    }
}

