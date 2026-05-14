package com.plane.cube.network.api

import com.plane.cube.domain.entity.Item
import com.plane.cube.network.model.ItemsResponse
import com.plane.cube.network.model.toItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorItemApi(
    private val client: HttpClient,
) : ItemApi {

    override fun getItems(): Flow<List<Item>> = flow {
        val response: ItemsResponse = client.get("items").body()
        emit(response.items.map { it.toItem() })
    }
}
