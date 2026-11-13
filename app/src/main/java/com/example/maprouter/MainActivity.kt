package com.example.maprouter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay


enum class MTYPE { SUCCESS, ERROR, INFO }


class MainActivity : AppCompatActivity() {

    lateinit var appName:TextView
    lateinit var editText:EditText
    lateinit var btnSave:Button
    lateinit var debugBox:TextView
    lateinit var debugScroll:ScrollView
    var appManager = AppManager()
    var networkManager = NetworkManager()
    var geoUrlParser = GeoUrlParser()





    fun log(msg:String,type:MTYPE) {
        val prefix = when (type) {
            MTYPE.SUCCESS -> "✅ "
            MTYPE.ERROR -> "❌ "
            MTYPE.INFO -> "ℹ️ "
        }
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        debugBox.append("\n${currentTime} \n$prefix ${msg}")
        debugBox.append("\n➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖\n")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //initialize all the UIs
        appName = findViewById<TextView>(R.id.lblAppName)
        editText = findViewById<EditText>(R.id.editText)
        btnSave = findViewById<Button>(R.id.btnSave)
        debugBox = findViewById<TextView>(R.id.debugBox)
        debugScroll = findViewById<ScrollView>(R.id.debugScroll)

        //retrieve saved apps
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedApps = sharedPreferences.getString("saved_apps", "").toString()

        //use app manager to filter out installed apps in the system
        appManager.getApps(this@MainActivity,savedApps)
        if(appManager.installedApps.isEmpty())
        {
            log("no apps saved",MTYPE.ERROR)
        }
        else
        {
            var appsFound = ""
            var count = 1;
            for(appName in appManager.installedApps)
                appsFound += "\n" + count++.toString()+ ". "+ appName
            log("apps saved in settings::"+appsFound,MTYPE.INFO)
        }

        //setup click event for save button
        btnSave.setOnClickListener {
            // This clears all data stored in this preference file
            val textToSave = editText.text.toString()
            sharedPreferences.edit().putString("saved_apps", textToSave).apply()
        }

        //processing incoming intent that started the app
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        log("new intent has been received and is being processed. please wait..", MTYPE.INFO)

        //malformed or corrupt intent
        if (intent == null || intent.action != Intent.ACTION_SEND)
        {
            log("new intent is either null or not of type SEND", MTYPE.ERROR)
            return
        }

        //applications are not saved in the settings
        if(appManager.installedApps.isEmpty())
        {
            log("no apps/package names saved in application settings", MTYPE.ERROR)
            return
        }

        //intent uri is empty or null
        val intentUri: Uri? =  Uri.parse(intent.getStringExtra(Intent.EXTRA_TEXT))
        if (intentUri == null)
        {
            log("received intent uri data is empty or null", MTYPE.ERROR)
            return
        }

        //main background thread
        lifecycleScope.launch {
            var type:MTYPE = MTYPE.ERROR

            //unshorten uri using network manager
            networkManager.networkQuery.originalUrl = intentUri
            networkManager.unshortenUrl()
            if(networkManager.networkQuery.status)
                type = MTYPE.SUCCESS
            val msg ="\nnetwork and parsing summary::\n"+
                    "original url:: ${networkManager.networkQuery.originalUrl}\n"+
                    "unshorten url:: ${networkManager.networkQuery.unshortenUrl}\n"+
                    "geo url:: ${networkManager.networkQuery.geoUrl}\n"+
                    "response code:: ${networkManager.networkQuery.responseCode.toString()}\n"+
                    "parsing pattern:: ${networkManager.networkQuery.parsingPattern}\n"+
                    "status message:: ${networkManager.networkQuery.statusMessage}\n"+
                    "status:: ${networkManager.networkQuery.status.toString()}\n"
            log(msg,type)
            if(!networkManager.networkQuery.status)
                return@launch

            //get geo url from geoUrlParser
            type = MTYPE.ERROR
            if(geoUrlParser.parserQuery.status)
                type = MTYPE.SUCCESS
            geoUrlParser.getGeoUrl(networkManager.networkQuery.unshortenUrl.toString())
            if(!geoUrlParser.parserQuery.status)
            {

            }


            //send outgoing intent to user chosen apps. handle outgoing intent will close the app
            handleOutgoingIntent()
           }
    }



    private fun handleOutgoingIntent()
    {
       if (networkManager.networkQuery.status) {
            for (appName in appManager.installedApps) {
                val outGoingIntent:Intent = Intent(Intent.ACTION_VIEW).apply {
                    data = networkManager.networkQuery.geoUrl
                    setPackage(appName)
                }
                try {
                    startActivity(outGoingIntent)
                    log(
                        "successfully sent geocoordinates uri:: \n${outGoingIntent.data.toString()} \nto app:: " + appName,MTYPE.SUCCESS
                    )

                    //close the app
                    finish()
                } catch (e: Exception) {
                    log(
                        "error while sending geocoordinates uri:: \n${outGoingIntent.data.toString()} \nto app:: " + appName + " \nexception:: ${e.localizedMessage}",MTYPE.ERROR
                    )
                }
            }
        }
    }

}
