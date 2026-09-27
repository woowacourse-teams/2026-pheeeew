package com.pheeeew.core.di.emotion

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.emotion.EmotionApi
import com.pheeeew.data.remote.group.api.GroupListApi
import com.pheeeew.data.repository.emotion.EmotionRepositoryImpl
import com.pheeeew.data.repository.group.GroupListRepositoryImpl
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionViewModel

fun createNearbyEmotionViewModel(client: ApiClient): NearbyEmotionViewModel =
    NearbyEmotionViewModel(
        EmotionRepositoryImpl(EmotionApi(client.requests)),
        GroupListRepositoryImpl(GroupListApi(client.requests)),
    )
