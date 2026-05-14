package com.plane.cube.network.repository

import com.plane.cube.network.api.ItemApi
import javax.inject.Inject

class RemoteItemRepository @Inject constructor(
    private val itemApi: ItemApi,
) {
    fun observeItems() = itemApi.getItems()
}
