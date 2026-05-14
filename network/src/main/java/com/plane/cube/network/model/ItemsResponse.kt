package com.plane.cube.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemsResponse(
    @SerialName("items") val items: List<ItemResponse>,
)
