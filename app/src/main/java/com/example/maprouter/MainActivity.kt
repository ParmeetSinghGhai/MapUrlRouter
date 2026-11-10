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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Semaphore

private val messageDumpPermit = Semaphore(1)

class MainActivity : AppCompatActivity() {

    lateinit var appName:TextView
    lateinit var editText:EditText
    lateinit var btnSave:Button
    lateinit var debugBox:TextView
    lateinit var debugScroll:ScrollView
    var appManager = AppManager()
    var networkManager = NetworkManager()


    private fun appendMessageToUi(msg: QueueMessage) {
        val prefix = when (msg.type) {
            MTYPE.SUCCESS -> "✅ "
            MTYPE.ERROR -> "❌ "
            MTYPE.INFO -> "ℹ️ "
        }
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        debugBox.append("\n${currentTime} level:: ${
            msg.level.toString()
        } \n$prefix ${msg.text}")
        debugBox.append("\n➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖ ➖\n")
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
        var msg = QueueMessage("",MTYPE.INFO, MLEVEL.SETTINGS)
        if(appManager.installedApps.isEmpty())
        {
            msg = QueueMessage("no apps saved",MTYPE.ERROR,MLEVEL.SETTINGS)
        }
        else
        {
            var appsFound = ""
            var count = 1;
            for(appName in appManager.installedApps)
                appsFound += "\n" + count++.toString()+ ". "+ appName
            msg = QueueMessage("apps saved in settings::"+appsFound,MTYPE.INFO,MLEVEL.SETTINGS)
        }
        appendMessageToUi(msg)

        //setup click event for save button
        btnSave.setOnClickListener {
            // This clears all data stored in this preference file
            val textToSave = editText.text.toString()
            sharedPreferences.edit().putString("saved_apps", textToSave).apply()
        }

        //processing incoming intent that started the app
        handleIncomingIntent(intent)

        lifecycleScope.launch {
            //processing outgoing intent
            while(true)
            {
                if (messageDumpPermit.tryAcquire()) {
                    try {
                        dumpBackgroundMessages()
                        handleOutgoingIntent()
                    } finally {
                        messageDumpPermit.release();
                        break;
                    }
                }
                delay(1000)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Required so 'intent' points to the new event
        // Process intent if the app was already running when the user clicked a link
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        debugBox.setText("");
        appendMessageToUi(QueueMessage("new intent has been received and is being processed. please wait..", MTYPE.INFO,MLEVEL.INTENT_IN))

        //malformed or corrupt intent
        if (intent == null || intent.action != Intent.ACTION_VIEW)
        {
            appendMessageToUi(QueueMessage("new intent is either null or corrupted", MTYPE.ERROR,MLEVEL.INTENT_IN))
            return
        }

        //applications are not saved in the settings
        if(appManager.installedApps.isEmpty())
        {
            appendMessageToUi(QueueMessage("no apps/package names saved in application settings", MTYPE.ERROR,MLEVEL.INTENT_IN))
            return
        }

        //intent uri is empty or null
        val intentUri: Uri? = intent?.data
        if (intentUri == null)
        {
            appendMessageToUi(QueueMessage("received intent uri data is empty or null", MTYPE.ERROR,MLEVEL.INTENT_IN))
            return
        }

        //unshorten uri using network manager
        lifecycleScope.launch {
            try {
                // Assign the network data back to your parent variable
                messageDumpPermit.acquire()
                networkManager.unshortenUrl(intentUri)
            } finally {
                // 3. CRITICAL: Open the gate so latch.await() stops blocking
                messageDumpPermit.release()
            }
        }

    }

    private fun dumpBackgroundMessages()
    {
        //dump all messages here
        for(msg in MessageQueueManager.messageQueue)
            appendMessageToUi(msg)
        MessageQueueManager.messageQueue.clear()
    }

    private fun handleOutgoingIntent()
    {
       if (networkManager.result.first) {
            for (appName in appManager.installedApps) {
                val outGoingIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = networkManager.result.second
                    setPackage(appName)
                }
                try {
                    startActivity(outGoingIntent)
                    appendMessageToUi(
                        QueueMessage("successfully sent geocoordinates uri:: \n${networkManager.result.second} \nto app:: " + appName,
                        MTYPE.SUCCESS,
                        MLEVEL.INTENT_OUT)
                    )
                } catch (e: Exception) {
                    appendMessageToUi(
                        QueueMessage("error while sending geocoordinates uri:: \n${networkManager.result.second} \nto app:: " + appName + " \nexception:: ${e.localizedMessage}",
                        MTYPE.ERROR,
                        MLEVEL.INTENT_OUT)
                    )
                }
            }
        }


    }

}
