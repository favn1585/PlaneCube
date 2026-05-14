package com.plane.cube.network.api

import com.plane.cube.domain.entity.Item
import kotlinx.coroutines.flow.Flow

interface ItemApi {

    fun getItems(): Flow<List<Item>>
}
