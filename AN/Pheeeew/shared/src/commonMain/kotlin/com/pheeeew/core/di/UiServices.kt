package com.pheeeew.core.di

import com.pheeeew.data.repository.EmotionMapRepositoryImpl
import com.pheeeew.data.repository.EmotionRegionMapRepositoryImpl
import com.pheeeew.data.repository.GroupDetailRepositoryImpl
import com.pheeeew.data.repository.GroupListRepositoryImpl
import com.pheeeew.data.repository.GroupStampListRepositoryImpl
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator

/** Shared caches and operation identity for one root UI, never for the whole process. */
class UiServices(
    val map: EmotionMapRepositoryImpl,
    val regions: EmotionRegionMapRepositoryImpl,
    val groupStamps: GroupStampListRepositoryImpl,
    val groupList: GroupListRepositoryImpl,
    val groupDetail: GroupDetailRepositoryImpl,
    val operationKeys: GroupOperationKeyAllocator,
)
