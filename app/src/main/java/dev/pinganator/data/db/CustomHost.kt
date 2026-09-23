package dev.pinganator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_hosts")
data class CustomHost(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    /** Full URL (https://...) or bare hostname. */
    val url: String
)
