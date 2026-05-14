package com.plane.cube.features.home.interactors

import com.plane.cube.domain.repository.ItemRepository
import com.plane.cube.features.home.model.HomeItemModel
import com.plane.cube.features.home.model.toHomeItemModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveItemsUseCase @Inject constructor(
    private val itemRepository: ItemRepository,
) {
    operator fun invoke(): Flow<List<HomeItemModel>> =
        itemRepository.observeItems().map { items -> items.map { it.toHomeItemModel() } }
}
