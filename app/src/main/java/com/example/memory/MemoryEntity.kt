package com.example.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey
    val id: String,
    val category: String, // identity, preference, goal, project, relationship, emotional, behavior
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
