package com.claw.passvault.data.local

import androidx.room.*
import com.claw.passvault.data.model.PasswordEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Query("SELECT * FROM passwords ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE title LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE category = :category ORDER BY updatedAt DESC")
    fun getByCategory(category: String): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE id = :id")
    suspend fun getById(id: Long): PasswordEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PasswordEntry): Long

    @Update
    suspend fun update(entry: PasswordEntry)

    @Delete
    suspend fun delete(entry: PasswordEntry)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM passwords")
    suspend fun count(): Int
}
