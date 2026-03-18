package com.jonghyun.autome.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {
    @Insert
    fun insertMessage(message: MessageEntity)

    @Insert
    fun insertMessages(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(roomId: String, limit: Int = 20): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    fun getAllRecentMessages(limit: Int = 50): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages")
    fun getMessageCount(): Int
}
