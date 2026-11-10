package com.example.maprouter

import android.content.Context
import android.content.pm.PackageManager


class AppManager {
    public var installedApps: MutableList<String> = mutableListOf()
    public fun getApps(context:Context,rawData:String)
    {
        var apps:List<String> = parseEntriesWithDelimiter(rawData)
        if(apps.isNotEmpty()) {
            for (appName in apps) {
                if (isAppInstalled(context, appName))
                    installedApps.add(appName)
                apps = emptyList()
            }
        }
    }

    private fun parseEntriesWithDelimiter(rawData: String?): List<String> {
        if (!rawData.isNullOrEmpty()) {
            return rawData
                .split(";")// 1. Break down entries by the delimiter
                .map { it.trim() }// 2. Remove accidental leading/trailing spaces or line breaks
                .filter { it.isNotEmpty() }// 3. Ignore empty entries (e.g. if the string ends with a trailing ';')
        }
        return emptyList()
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            // Query the package manager framework for the specific target application
            context.packageManager.getPackageInfo(packageName, 0)
            true // If no exception is thrown, the app exists!
        } catch (e: PackageManager.NameNotFoundException) {
            false // App is not installed on this phone
        }
    }

}