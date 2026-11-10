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
enum class MLEVEL { SETTINGS,INTENT_IN, HTTP_CONNECTION, PARSING, INTENT_OUT}
data class QueueMessage(val text: String, val type: MTYPE,val level:MLEVEL)


object MessageQueueManager {
    // 💡 ConcurrentLinkedQueue handles thousands of simultaneous multi-thread writes safely
    val messageQueue = ConcurrentLinkedQueue<QueueMessage>()

    fun push(text: String, type: MTYPE, level: MLEVEL) {
        val msg = QueueMessage(text, type, level)
        messageQueue.add(msg)
    }
}



/*
object MessageQueueManager {
    val messageChannel = Channel<QueueMessage>(Channel.UNLIMITED)
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault()) // 2. Convert raw UTC to your phone's current local timezone
    val loggingScope = CoroutineScope(Dispatchers.IO)
     /**
     * Call this from ANY thread (Main or Background) to add a message to the pile.
     * Example: MessageQueueManager.push("Hello!", MTYPE.INFO)
     */
    fun push(text: String, type: MTYPE, level: MLEVEL) {
         val msg = QueueMessage(formatter.format(Instant.now()),text, type,level)
         //messageChannel.trySend(msg)
         loggingScope.launch {
             // ❌ OLD: messageChannel.trySend(msg)
             //  FIX: 'send' guarantees data delivery across thread boundaries
             messageChannel.send(msg)
         }
    }
}
*/