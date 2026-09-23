package com.pheeeew.legacy.domain.usecase

import com.pheeeew.legacy.domain.model.geo.Coordinate
import com.pheeeew.legacy.domain.model.sigh.CreateSighCommand
import com.pheeeew.legacy.domain.model.sigh.Sigh
import com.pheeeew.legacy.domain.repository.SighRepository
import com.pheeeew.legacy.domain.service.SighLocationObfuscator

class CreateSighUseCase(
    private val repository: SighRepository,
    private val locationObfuscator: SighLocationObfuscator,
) {
    fun prepare(
        requestId: String,
        coordinate: Coordinate,
        memo: String? = null,
    ): CreateSighCommand =
        CreateSighCommand(
            requestId = requestId,
            coordinate = locationObfuscator.obfuscate(coordinate),
            memo = memo,
        )

    suspend operator fun invoke(command: CreateSighCommand): Sigh = repository.create(command)
}
