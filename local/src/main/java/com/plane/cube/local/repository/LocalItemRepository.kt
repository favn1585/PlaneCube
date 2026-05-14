package com.plane.cube.local.repository

import com.plane.cube.domain.entity.Item
import com.plane.cube.local.PlaneCubeDatabase
import com.plane.cube.local.model.toItem
import com.plane.cube.local.model.toItemEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalItemRepository @Inject constructor(
    private val database: PlaneCubeDatabase,
) {
    fun observeItems(): Flow<List<Item>> =
        database.itemDao.getAll()
            .flowOn(Dispatchers.IO)
            .map { entities -> entities.map { it.toItem() } }

    suspend fun insertItems(items: List<Item>) {
        withContext(Dispatchers.IO) {
            database.itemDao.insertAll(items.map { it.toItemEntity() })
        }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            database.itemDao.deleteAll()
        }
    }
}
