package com.plane.cube.features.home.model

sealed class HomeUiIntent {
    data object OnPullToRefresh : HomeUiIntent()
    data class OnItemClicked(val id: String) : HomeUiIntent()
}
