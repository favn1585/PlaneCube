package com.plane.cube.repository

import com.plane.cube.domain.repository.ItemRepository
import com.plane.cube.local.repository.LocalItemRepository
import com.plane.cube.network.repository.RemoteItemRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

class ItemRepositoryImpl @Inject constructor(
    private val localItemRepository: LocalItemRepository,
    private val remoteItemRepository: RemoteItemRepository,
) : ItemRepository {

    override fun observeItems() = merge(
        localItemRepository.observeItems(),
        refreshItems(),
    )

    override fun refreshItems() = remoteItemRepository.observeItems().onEach { items ->
        localItemRepository.insertItems(items)
    }
}
