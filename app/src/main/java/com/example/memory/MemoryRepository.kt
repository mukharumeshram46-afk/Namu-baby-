package com.example.memory

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class MemoryRepository(private val memoryDao: MemoryDao) {

    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>> {
        return memoryDao.getMemoriesByCategory(category)
    }

    suspend fun saveMemory(category: String, text: String): MemoryEntity {
        val entity = MemoryEntity(
            id = UUID.randomUUID().toString().substring(0, 9),
            category = category.lowercase(),
            text = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        memoryDao.insertMemory(entity)
        return entity
    }

    suspend fun deleteMemory(id: String) {
        memoryDao.deleteMemoryById(id)
    }

    suspend fun clearAll() {
        memoryDao.clearAll()
    }

    suspend fun ensureDefaultMemories() {
        if (memoryDao.count() == 0) {
            val defaults = listOf(
                MemoryEntity(
                    id = "ut75a9264",
                    category = "preference",
                    text = "The user prefers to speak in Hindi or Hinglish naturally.",
                    timestamp = System.currentTimeMillis()
                ),
                MemoryEntity(
                    id = "4o9wz4jio",
                    category = "identity",
                    text = "The user's name is Tech GPT, and they have an AI-focused YouTube channel called Tech GPT.",
                    timestamp = System.currentTimeMillis()
                ),
                MemoryEntity(
                    id = "4jswpmydh",
                    category = "goal",
                    text = "The user's goal is to buy an RTX 5090 GPU.",
                    timestamp = System.currentTimeMillis()
                ),
                MemoryEntity(
                    id = "i7aor6g19",
                    category = "identity",
                    text = "The user has an RX 580 8GB GPU and an Intel i3 10th generation CPU.",
                    timestamp = System.currentTimeMillis()
                )
            )
            memoryDao.insertMemories(defaults)
        }
    }

    fun formatMemoriesForSystemPrompt(memories: List<MemoryEntity>): String {
        if (memories.isEmpty()) {
            return "\n=== MYRAA MEMORY CORE ===\nNo prior recollections recorded yet. Listen attentively to learn about your companion.\n=========================\n"
        }

        val grouped = memories.groupBy { it.category }
        val builder = StringBuilder()
        builder.append("\n=== MYRAA PERSISTENT MEMORY CORE (RECOLLECTIONS) ===\n")
        builder.append("You have known this companion for a long duration. Below are your persistent recollections:\n")
        builder.append("- INTEGRATE MEMORIES INSTINCTIVELY: Mention details naturally like a true friend, never say 'According to my database'.\n")
        builder.append("- RECOLLECTIONS KNOWLEDGE CARD:\n")

        val categories = listOf(
            "identity" to "Identity (Name, nick, profession)",
            "preference" to "Preferences & Tastes",
            "goal" to "Active Goals & Aspirations",
            "project" to "Ongoing Projects",
            "relationship" to "Key Relationships",
            "emotional" to "Milestones & Emotional Highlights",
            "behavior" to "Observed Habits & Traits"
        )

        for ((key, label) in categories) {
            val items = grouped[key]
            if (!items.isNullOrEmpty()) {
                builder.append("* $label:\n")
                items.forEach { m ->
                    builder.append("  - ${m.text}\n")
                }
            }
        }
        builder.append("====================================================\n")
        return builder.toString()
    }
}
