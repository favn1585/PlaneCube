package com.plane.cube.features.home.model

data class HomeViewState(
    val items: List<HomeItemModel>? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)
