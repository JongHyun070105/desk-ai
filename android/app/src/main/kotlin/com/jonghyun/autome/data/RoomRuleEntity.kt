package com.jonghyun.autome.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "room_rules")
data class RoomRuleEntity(
    @PrimaryKey
    val roomId: String,
    val rule: String,
    val isAutoReplyEnabled: Boolean = false
)
