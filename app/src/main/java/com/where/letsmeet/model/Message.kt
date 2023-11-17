package com.where.letsmeet.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.ServerValue

data class Message(
    val senderId: String = "",
    val content: String = "",
    val timestamp: Any = ServerValue.TIMESTAMP
) {
    @Exclude
    fun getTimestampLong(): Long {
        if (timestamp is Long) {
            return timestamp
        } else if (timestamp is HashMap<*, *>) {
            return timestamp[".sv"] as Long
        }
        return 0
    }
}
