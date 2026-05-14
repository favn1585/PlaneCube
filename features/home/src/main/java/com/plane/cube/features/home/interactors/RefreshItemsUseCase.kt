package com.plane.cube.features.home.interactors

import com.plane.cube.domain.repository.ItemRepository
import javax.inject.Inject

class RefreshItemsUseCase @Inject constructor(
    private val itemRepository: ItemRepository,
) {
    operator fun invoke() = itemRepository.refreshItems()
}
