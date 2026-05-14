package com.plane.cube.network.model

import com.plane.cube.domain.entity.Item
import com.plane.cube.domain.entity.ItemStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemResponse(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("status") val status: String,
)

fun ItemResponse.toItem() = Item(
    id = id,
    title = title,
    description = description,
    status = runCatching { ItemStatus.valueOf(status) }.getOrDefault(ItemStatus.NEW),
)
