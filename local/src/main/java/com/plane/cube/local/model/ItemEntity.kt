package com.plane.cube.local.model

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.plane.cube.domain.entity.Item
import com.plane.cube.domain.entity.ItemStatus

@Keep
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val status: String,
)

fun ItemEntity.toItem() = Item(
    id = id,
    title = title,
    description = description,
    status = runCatching { ItemStatus.valueOf(status) }.getOrDefault(ItemStatus.NEW),
)

fun Item.toItemEntity() = ItemEntity(
    id = id,
    title = title,
    description = description,
    status = status.name,
)
