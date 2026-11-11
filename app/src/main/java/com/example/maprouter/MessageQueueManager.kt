package com.example.maprouter

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


enum class MTYPE { SUCCESS, ERROR, INFO }
data class QueueMessage(val text: String, val type: MTYPE)


object MessageQueueManager {
    // 💡 ConcurrentLinkedQueue handles thousands of simultaneous multi-thread writes safely
    val messageQueue = ConcurrentLinkedQueue<QueueMessage>()

    fun push(text: String, type: MTYPE) {
        val msg = QueueMessage(text, type)
        messageQueue.add(msg)
    }
}

