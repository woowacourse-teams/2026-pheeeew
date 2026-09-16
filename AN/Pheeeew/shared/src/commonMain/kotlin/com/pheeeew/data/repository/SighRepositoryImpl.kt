package com.pheeeew.data.repository

import com.pheeeew.data.remote.sigh.api.SighV1Api
import com.pheeeew.data.remote.sigh.api.SighV2Api
import com.pheeeew.data.remote.sigh.dto.SighCreateV2RequestDto
import com.pheeeew.data.remote.sigh.dto.toSigh
import com.pheeeew.data.remote.sigh.dto.toSighPage
import com.pheeeew.data.remote.sigh.dto.toSighPin
import com.pheeeew.domain.model.sigh.CreateSighCommand
import com.pheeeew.domain.model.sigh.Sigh
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPage
import com.pheeeew.domain.model.sigh.SighPin
import com.pheeeew.domain.repository.SighRepository

class SighRepositoryImpl(
    private val sighV1Api: SighV1Api,
    private val sighV2Api: SighV2Api,
) : SighRepository {
    override suspend fun getMapSighs(bounds: SighBounds): List<SighPin> =
        sighV1Api
            .getSighs(bounds)
            .features
            .map { it.toSighPin() }

    override suspend fun getFirstPage(bounds: SighBounds): SighPage =
        sighV2Api
            .getFirstPage(bounds)
            .toSighPage()

    override suspend fun getNextPage(cursor: String): SighPage =
        sighV2Api
            .getNextPage(cursor)
            .toSighPage()

    override suspend fun getById(id: Long): Sigh =
        sighV2Api
            .getById(id)
            .toSigh()

    override suspend fun create(command: CreateSighCommand): Sigh {
        val request =
            SighCreateV2RequestDto(
                requestId = command.requestId,
                latitude = command.coordinate.latitude,
                longitude = command.coordinate.longitude,
                memo = command.memo,
            )

        return sighV2Api
            .create(request)
            .toSigh()
    }
}
