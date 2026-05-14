package com.plane.cube.local.dao

import androidx.annotation.Keep
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.plane.cube.local.model.ItemEntity
import kotlinx.coroutines.flow.Flow

@Keep
@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ItemEntity>)

    @Query("SELECT * FROM items")
    fun getAll(): Flow<List<ItemEntity>>

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}
