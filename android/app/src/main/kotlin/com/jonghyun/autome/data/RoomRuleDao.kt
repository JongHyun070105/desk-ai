package com.jonghyun.autome.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RoomRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RoomRuleEntity)

    @Query("SELECT * FROM room_rules WHERE roomId = :roomId")
    suspend fun getRuleForRoom(roomId: String): RoomRuleEntity?
    
    @Query("DELETE FROM room_rules WHERE roomId = :roomId")
    suspend fun deleteRule(roomId: String)

    @Query("DELETE FROM room_rules")
    suspend fun deleteAllRules()
}
