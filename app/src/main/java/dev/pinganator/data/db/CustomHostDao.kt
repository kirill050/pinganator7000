package dev.pinganator.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomHostDao {
    @Query("SELECT * FROM custom_hosts ORDER BY id ASC")
    fun observeAll(): Flow<List<CustomHost>>

    @Query("SELECT * FROM custom_hosts ORDER BY id ASC")
    suspend fun getAll(): List<CustomHost>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(host: CustomHost): Long

    @Delete
    suspend fun delete(host: CustomHost)
}
