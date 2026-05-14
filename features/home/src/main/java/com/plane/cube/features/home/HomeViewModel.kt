package com.plane.cube.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plane.cube.features.home.interactors.ObserveItemsUseCase
import com.plane.cube.features.home.interactors.RefreshItemsUseCase
import com.plane.cube.features.home.model.HomeUiIntent
import com.plane.cube.features.home.model.HomeViewState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeItemsUseCase: ObserveItemsUseCase,
    private val refreshItemsUseCase: RefreshItemsUseCase,
) : ViewModel() {

    private val _viewState = MutableStateFlow(HomeViewState())
    val viewState = _viewState.asStateFlow()

    init {
        viewModelScope.launch {
            observeItemsUseCase()
                .catch { error ->
                    _viewState.update { it.copy(errorMessage = error.message) }
                }
                .collectLatest { items ->
                    _viewState.update {
                        it.copy(items = items, isRefreshing = false, errorMessage = null)
                    }
                }
        }
    }

    fun onUiIntent(intent: HomeUiIntent) {
        when (intent) {
            HomeUiIntent.OnPullToRefresh -> refresh()
            is HomeUiIntent.OnItemClicked -> Unit
        }
    }

    private fun refresh() {
        _viewState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            refreshItemsUseCase()
                .catch { error ->
                    _viewState.update {
                        it.copy(isRefreshing = false, errorMessage = error.message)
                    }
                }
                .collectLatest { /* state is updated via observeItemsUseCase */ }
        }
    }
}
