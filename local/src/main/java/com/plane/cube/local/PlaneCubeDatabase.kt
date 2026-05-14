package com.plane.cube.local

import androidx.annotation.Keep
import androidx.room.Database
import androidx.room.RoomDatabase
import com.plane.cube.local.dao.ItemDao
import com.plane.cube.local.model.ItemEntity

@Keep
@Database(
    entities = [ItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PlaneCubeDatabase : RoomDatabase() {
    abstract val itemDao: ItemDao
}
