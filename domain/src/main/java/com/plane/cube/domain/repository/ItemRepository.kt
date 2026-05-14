package com.plane.cube.domain.repository

import com.plane.cube.domain.entity.Item
import kotlinx.coroutines.flow.Flow

interface ItemRepository {

    fun observeItems(): Flow<List<Item>>

    fun refreshItems(): Flow<List<Item>>
}
