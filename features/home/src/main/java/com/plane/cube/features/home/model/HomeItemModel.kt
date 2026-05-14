package com.plane.cube.features.home.model

import com.plane.cube.domain.entity.Item
import com.plane.cube.domain.entity.ItemStatus

data class HomeItemModel(
    val id: String,
    val title: String,
    val description: String,
    val status: ItemStatus,
)

fun Item.toHomeItemModel() = HomeItemModel(
    id = id,
    title = title,
    description = description,
    status = status,
)
