package com.pheeeew.core.di.emotion

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.emotion.EmotionApi
import com.pheeeew.data.repository.emotion.EmotionRepositoryImpl
import com.pheeeew.domain.repository.group.GroupStampListRepository
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionViewModel

fun createNearbyEmotionViewModel(
    client: ApiClient,
    groupStampListRepository: GroupStampListRepository,
): NearbyEmotionViewModel =
    NearbyEmotionViewModel(
        EmotionRepositoryImpl(EmotionApi(client.requests)),
        groupStampListRepository,
        client.monitoring,
    )
