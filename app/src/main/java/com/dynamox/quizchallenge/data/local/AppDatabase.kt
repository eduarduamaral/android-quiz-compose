package com.dynamox.quizchallenge.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// Schema export is off; a long-lived app would check schemas in to support migration tests.
@Database(entities = [PlayerScoreEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerScoreDao(): PlayerScoreDao
}
