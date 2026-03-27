package com.jonghyun.autome.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["roomId", "timestamp"]),
        Index(value = ["roomId"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: String,
    val sender: String,
    val message: String,
    val timestamp: Long,
    val isSentByMe: Boolean
)
