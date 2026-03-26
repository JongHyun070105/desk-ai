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

    // ── 채팅방 목록 조회 ──
    @Query("""
        SELECT roomId, 
               sender AS lastSender,
               message AS lastMessage, 
               MAX(timestamp) AS lastTimestamp,
               COUNT(*) AS messageCount
        FROM messages
        GROUP BY roomId
        ORDER BY lastTimestamp DESC
    """)
    fun getDistinctRooms(): List<ChatRoomSummary>

    // ── 특정 채팅방 메시지 전체 조회 (오래된 순) ──
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    fun getMessagesForRoom(roomId: String): List<MessageEntity>

    // ── 특정 채팅방 메시지 페이지네이션 조회 (최신 순으로 가져와서 뒤집어서 표시할 용도) ──
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getMessagesForRoomPaged(roomId: String, limit: Int, offset: Int): List<MessageEntity>

    // ── 특정 채팅방 메시지 수 조회 ──
    @Query("SELECT COUNT(*) FROM messages WHERE roomId = :roomId")
    fun getMessageCountForRoom(roomId: String): Int

    // ── 특정 채팅방 모든 메시지 삭제 ──
    @Query("DELETE FROM messages WHERE roomId = :roomId")
    fun deleteMessagesByRoom(roomId: String)

    // ── 모든 메시지 삭제 ──
    @Query("DELETE FROM messages")
    fun deleteAllMessages()
}
