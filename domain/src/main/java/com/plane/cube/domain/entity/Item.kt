package com.plane.cube.domain.entity

data class Item(
    val id: String,
    val title: String,
    val description: String,
    val status: ItemStatus,
)
