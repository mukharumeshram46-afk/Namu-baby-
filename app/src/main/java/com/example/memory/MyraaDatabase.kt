package com.example.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [MemoryEntity::class], version = 1, exportSchema = false)
abstract class MyraaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: MyraaDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): MyraaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MyraaDatabase::class.java,
                    "myraa_assistant_db"
                )
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialMemories(database.memoryDao())
                    }
                }
            }

            suspend fun populateInitialMemories(dao: MemoryDao) {
                val initialMemories = listOf(
                    MemoryEntity(
                        id = "ut75a9264",
                        category = "preference",
                        text = "The user prefers to speak in Hindi or Hinglish naturally.",
                        timestamp = System.currentTimeMillis() - 86400000L * 5
                    ),
                    MemoryEntity(
                        id = "4o9wz4jio",
                        category = "identity",
                        text = "The user's name is Tech GPT, and they have a YouTube channel named Tech GPT focused on AI developments.",
                        timestamp = System.currentTimeMillis() - 86400000L * 4
                    ),
                    MemoryEntity(
                        id = "4jswpmydh",
                        category = "goal",
                        text = "The user's active life goal is to acquire an RTX 5090 graphics card.",
                        timestamp = System.currentTimeMillis() - 86400000L * 3
                    ),
                    MemoryEntity(
                        id = "i7aor6g19",
                        category = "identity",
                        text = "The user currently develops on an RX 580 GPU with 8GB RAM and an Intel i3 10th Gen CPU.",
                        timestamp = System.currentTimeMillis() - 86400000L * 2
                    ),
                    MemoryEntity(
                        id = "proj_myraa_native",
                        category = "project",
                        text = "The user is building MYRAA, a native Android companion on Realme 12 Pro 5G with voice, vision, and system automation.",
                        timestamp = System.currentTimeMillis()
                    )
                )
                dao.insertMemories(initialMemories)
            }
        }
    }
}
